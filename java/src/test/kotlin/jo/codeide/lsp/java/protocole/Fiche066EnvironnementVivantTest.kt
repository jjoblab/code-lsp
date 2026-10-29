package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

/**
 * Fiche 066 — EnvironnementVivantServeurTest : le RÉGIME VIVANT du serveur
 * (INTEGRATION-CODEIDE §3.3, OPTION B — routeur multi-modules AU PROTOCOLE),
 * la façade que `:lsp:host` de CodeIDE consommera :
 * `ServeurLangageJava(environnement) { rappel }`.
 *
 * Le montage reproduit l'orchestration RÉELLE de l'hôte (OrchestrateurLsp) :
 * UN environnement détenu par le « propriétaire » (le test joue l'hôte), UN
 * serveur sur cet environnement, des syncs qui NOURRISSENT les contextes
 * AVANT et APRÈS l'initialize — jamais de reconstruction du serveur.
 *
 * Invariants éprouvés (critères d'acceptation §5 de l'intégration) :
 *  - **routage fichier → module** (fiche 047 au protocole) : le document
 *    est analysé par le moteur de SON module (racines propres), le
 *    document hors arbre par le moteur DÉFAUT (contexte vide) ;
 *  - **reconfiguration À CHAUD** (sémantiques replaceAll fiche 034) : un
 *    remplacement effectif (racine AJOUTÉE) invalide le moteur du module
 *    SEUL, la requête suivante voit la NOUVELLE racine SANS re-initialize ;
 *  - **invariant ADR 0211** (« nourrissage moteur unique par sync », un
 *    motif direct du critère 6 d'acceptation) : N syncs (redondants et
 *    effectifs) traversent la MÊME session — l'état du cycle reste
 *    INITIALIZED, un second initialize est REJETÉ (-32600), les requêtes
 *    répondent après CHAQUE sync ;
 *  - **diagnostics routés par module** : le publicateur résout le moteur
 *    du module AU TIR (fenêtre du debounce) — le document erroné du
 *    module publie SES squiggles ;
 *  - **decompiledSource sonde les moteurs VIVANTS** (motif ADR 0215 du
 *    vendored) : défaut d'abord, puis modules — la première source gagne.
 *
 * La suite est ORDONNÉE (@Order) : c'est un scénario d'orchestration
 * séquentiel (sync → requêtes → re-sync → requêtes), l'état MUTABLE de
 * l'environnement étant le SUJET même de la fiche.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation::class)
class Fiche066EnvironnementVivantTest {

    /** La racine des fixtures (créée MANUELLEMENT — motif Fiche026 : les
     *  suites à état long ne peuvent pas compter sur le cycle de vie du
     *  @TempDir de méthode, dont le contenu disparaît en cours de suite). */
    private lateinit var racine: Path

    /** Les sources du module « :app » (déclarées à la sync). */
    private val sourcesApp: Path get() = racine.resolve("appmodule/src")

    /** Les sources du module « :core » (le frère). */
    private val sourcesCore: Path get() = racine.resolve("coremodule/src")

    /** Les sources ajoutées par la SECONDE sync (reconfiguration à chaud). */
    private val sourcesNeuves: Path get() = racine.resolve("neufmodule/src")

    /** L'environnement détenu par le « hôte » (le test). */
    private var environnement: jo.codeide.lsp.java.EnvironnementCompilation? = null

    /** Le serveur en régime vivant (l'état du cycle y est observable). */
    private var serveur: ServeurLangageJava? = null

    /** La boucle JSON-RPC du serveur en régime vivant. */
    private var boucle: BoucleLocale? = null

    /** Coeur : le frère déclaré en entrée MODULE (résolution en sources). */
    private val sourceCoeur = """
        package coeur;

        public class Coeur {
            public int battre() {
                return 42;
            }
        }
    """.trimIndent() + "\n"

    /** Alpha : consomme le frère (c.battre()) — routé vers « :app ». */
    private val sourceAlpha = """
        package app;

        import coeur.Coeur;

        public class Alpha {
            int total = 0;
            void pomper(Coeur c) {
                total += c.battre();
            }
        }
    """.trimIndent() + "\n"

    /** Neuf : la classe de la racine AJOUTÉE par la re-sync. */
    private val sourceNeuf = """
        package neuf;

        public class Neuf {
            int valeur = 9;
        }
    """.trimIndent() + "\n"

    /** Buggy : le document erroné du module (publication de diagnostics). */
    private val sourceBuggy = """
        package app;

        public class Buggy {
            int x = ;
        }
    """.trimIndent() + "\n"

    @BeforeAll
    fun avantTout() {
        // Racine MANUELLE (motif Fiche026) — les fixtures doivent vivre
        // jusqu'au DERNIER test de la suite ordonnée. Noms CANONIQUES
        // paquet ↔ chemin (racine/paquet/Type.java) : la résolution du
        // projet entier en dépend.
        racine = Files.createTempDirectory("envvivant-066")
        Files.createDirectories(sourcesApp.resolve("app"))
        Files.createDirectories(sourcesCore.resolve("coeur"))
        Files.writeString(sourcesCore.resolve("coeur/Coeur.java"), sourceCoeur)
        Files.writeString(sourcesApp.resolve("app/Alpha.java"), sourceAlpha)
        Files.writeString(sourcesApp.resolve("app/Buggy.java"), sourceBuggy)

        // L'HÔTE (le test) détient l'environnement et le NOURRIT — motif
        // OrchestrateurLsp : racines de « :app » = les siennes + celles du
        // frère « :core » (résolution en sources du frère). Le JDK de test
        // (JO_TEST_JDK_HOME ou runtime) porte l'image jrt — la résolution
        // ECJ des types de projet l'exige (motif Fiche026).
        val jdk = jo.codeide.lsp.java.navigation.JdkDeTest.home()
        val env = jo.codeide.lsp.java.EnvironnementCompilation(jdkHome = jdk)
        env.routeurFichierModule = { chemin ->
            when {
                chemin.contains("appmodule") -> ":app"
                chemin.contains("coremodule") -> ":core"
                chemin.contains("neufmodule") -> ":neuf"
                else -> null
            }
        }
        env.remplacerContexteModule(":app", listOf(sourcesApp, sourcesCore))
        env.remplacerContexteModule(":core", listOf(sourcesCore))

        // Hypothèse d'environnement VISIBLE (motif Fiche026) : sans image
        // jrt, la résolution ECJ ne monte pas — la suite est ignorée.
        assumeTrue(env.moteur(":app").isLikelyUsable(), "image jrt indisponible — fiche 066 ignorée (gate visible)")

        environnement = env

        // La façade de l'intégration : constructeur ENVIRONNEMENT + rappel
        // d'arrêt (le registre des serveurs vivants côté hôte).
        val serveurVivant = ServeurLangageJava(env) { }
        serveur = serveurVivant
        boucle = BoucleLocale(serveurVivant).also { b ->
            b.demarrer()
            b.client.initialize(
                InitializeParams().apply { rootUri = racine.toUri().toString() },
            ).get()
            b.client.initialized(InitializedParams())
        }
    }

    /** Ouvre (ou ré-ouvre) un document par son chemin et son texte. */
    private fun ouvrir(chemin: Path, texte: String, version: Int = 0): TextDocumentIdentifier {
        val b = boucle ?: fail("boucle absente")
        val uri = chemin.toUri().toString()
        b.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", version, texte)),
        )
        return TextDocumentIdentifier(uri)
    }

    /** La position juste après un extrait cherché dans une source. */
    private fun positionApres(source: String, cherche: String): Position {
        val indice = source.indexOf(cherche)
        if (indice < 0) fail("« $cherche » introuvable dans la source")
        val ligne = source.substring(0, indice).count { it == '\n' }
        val debutLigne = source.lastIndexOf('\n', indice - 1) + 1
        return Position(ligne, indice - debutLigne + cherche.length)
    }

    /** Les labels de complétion au fil pour un document et une position. */
    private fun completer(document: TextDocumentIdentifier, position: Position): List<String> {
        val params = CompletionParams()
        params.textDocument = document
        params.position = position
        val reponse = boucle!!.client.textDocumentService.completion(params).get()
        return reponse.right?.items?.mapNotNull { it.label } ?: emptyList()
    }

    /** ---------------------------------------------------------------- */

    /** 1 — le document est analysé par le moteur de SON module : la complétion des membres du FRÈRE (racines de « :app ») résout. */
    @Test
    @Order(1)
    fun leDocumentEstAnalyseParLeMoteurDeSonModule() {
        val document = ouvrir(sourcesApp.resolve("app/Alpha.java"), sourceAlpha)
        // Juste après « c. » : les membres du frère coeur.Coeur.
        val labels = completer(document, positionApres(sourceAlpha, "c."))
        assertTrue(
            labels.contains("battre"),
            "« battre » (membre du FRÈRE Coeur, racines de « :app ») est proposé — trouvé : ${labels.take(12)}",
        )
    }

    /** 2 — le document HORS ARBRE tombe sur le moteur DÉFAUT (contexte VIDE) : le frère ne résout PAS. */
    @Test
    @Order(2)
    fun leDocumentHorsArbreTombeSurLeMoteurDefaut() {
        val horsArbre = racine.resolve("horsarbre")
        Files.createDirectories(horsArbre)
        val document = ouvrir(horsArbre.resolve("Perdu.java"), sourceAlpha)
        val labels = completer(document, positionApres(sourceAlpha, "c."))
        assertTrue(
            labels.none { it == "battre" },
            "« battre » n'est PAS proposé hors arbre — le moteur DÉFAUT (contexte vide) n'a pas les racines des modules — trouvé : ${labels.take(12)}",
        )
    }

    /** 3 — diagnostics ROUTÉS : le publicateur résout le moteur du module au TIR — l'erreur de Buggy.java est publiée. */
    @Test
    @Order(3)
    fun lesDiagnosticsSontRoutesVersLeMoteurDuModule() {
        val document = ouvrir(sourcesApp.resolve("app/Buggy.java"), sourceBuggy)
        val publication = boucle!!.attendrePublication(document.uri)
        assertNotNull(publication, "une publication est arrivée pour Buggy.java")
        assertTrue(
            publication.diagnostics.isNotEmpty(),
            "la publication porte les squiggles du document erroné (moteur du module résolu AU TIR)",
        )
        assertTrue(
            publication.diagnostics.any { it.severity == org.eclipse.lsp4j.DiagnosticSeverity.Error },
            "au moins une ERREUR de syntaxe (« int x = ; ») — trouvé : ${publication.diagnostics.map { it.message }}",
        )
    }

    /** 4 — reconfiguration À CHAUD (replaceAll fiche 034) : la racine AJOUTÉE par la seconde sync est vue SANS re-initialize. */
    @Test
    @Order(4)
    fun laReconfigurationAChaudEstVueSansReinitialisation() {
        val env = environnement ?: fail("environnement absent")
        val serveur = serveur ?: fail("serveur absent")

        // La SECONDE sync : le module « :app » gagne la racine de
        // « neufmodule » (remplacement EFFECTIF — l'ancien contexte de
        // « :app » ne la contenait pas : moteur invalidé, fiche 034).
        Files.createDirectories(sourcesNeuves.resolve("neuf"))
        Files.writeString(sourcesNeuves.resolve("neuf/Neuf.java"), sourceNeuf)
        env.remplacerContexteModule(":app", listOf(sourcesApp, sourcesCore, sourcesNeuves))

        // La session n'a JAMAIS été re-construite : l'état du cycle est
        // resté INITIALIZED et un second initialize est REJETÉ (-32600).
        assertEquals(
            jo.codeide.lsp.java.protocole.CycleVie.Etat.INITIALIZED,
            serveur.cycle.etat(),
            "le cycle est resté INITIALIZED à travers la re-sync (invariant ADR 0211)",
        )
        val rejet = runCatching {
            serveur.initialize(InitializeParams().apply { rootUri = racine.toUri().toString() }).get()
        }
        assertTrue(rejet.isFailure, "un second initialize est rejeté (re-initialize interdit)")

        // La requête suivante voit la NOUVELLE racine : le type « Neuf »
        // IMPORTÉ résout dans le moteur RECONFIGURÉ — ses MEMBRES sont
        // proposés (preuve que la source de neufmodule est LUE).
        val corps = sourceAlpha
            .replace("package app;", "package app;\n\nimport neuf.Neuf;")
            .replace("total += c.battre();", "total += c.battre(); Neuf n = new Neuf(); n.")
        val document = ouvrir(sourcesApp.resolve("app/Alpha.java"), corps, version = 1)
        val labels = completer(document, positionApres(corps, "n."))
        assertTrue(
            labels.contains("valeur"),
            "« valeur » (membre de Neuf, racine AJOUTÉE par la re-sync) est proposé — trouvé : ${labels.take(12)}",
        )
        // Drain du TIR retardé du publicateur (diagnostics du corps v1) :
        // la suite (test 5) sync l'environnement — le tir doit être TERMINÉ
        // pour que les fils ne se marchent pas dessus (bisection B).
        boucle!!.attendrePublication(document.uri)
    }

    /** 5 — invariant ADR 0211 : N syncs (redondants et effectifs) traversent la MÊME session, les requêtes répondent après CHACUNE. */
    @Test
    @Order(5)
    fun lesSyncsRedondantsEtEffectifsTraversentLaMemeSession() {
        val env = environnement ?: fail("environnement absent")
        val serveur = serveur ?: fail("serveur absent")

        // Cinq syncs mêlées : redondantes (no-op système — mêmes listes)
        // et effectives (ordre des racines changé = contexte différent).
        repeat(3) {
            env.remplacerContexteModule(":app", listOf(sourcesApp, sourcesCore, sourcesNeuves))
        }
        env.remplacerContexteModule(":app", listOf(sourcesNeuves, sourcesCore, sourcesApp))
        env.remplacerContexteModule(":app", listOf(sourcesApp, sourcesCore, sourcesNeuves))

        // La session est TOUJOURS la même et répond : la résolution du
        // frère (test 1) SURVIT aux syncs redondantes et effectives.
        assertEquals(
            jo.codeide.lsp.java.protocole.CycleVie.Etat.INITIALIZED,
            serveur.cycle.etat(),
            "le cycle est resté INITIALIZED à travers les cinq syncs",
        )
        val document = ouvrir(sourcesApp.resolve("app/Alpha.java"), sourceAlpha, version = 3)
        val labels = completer(document, positionApres(sourceAlpha, "c."))
        assertTrue(
            labels.contains("battre"),
            "la résolution du frère SURVIT aux syncs — « battre » toujours proposé (trouvé : ${labels.take(12)})",
        )
    }

    /** 6 — decompiledSource sonde les moteurs VIVANTS (motif ADR 0215 du vendored) : défaut d'abord, puis modules. */
    @Test
    @Order(6)
    fun decompiledSourceSondeLesMoteursVivants() {
        val b = boucle ?: fail("boucle absente")
        // Un moteur vivant existe (les requêtes précédentes ont construit
        // ceux de « :app » et « :core ») ; les stubs intrinsèques du régime
        // instantané servent java.lang.String SANS JDK.
        val reponse = b.experimental.decompiledSource(
            ParamsFqcnRpc("java.lang.String"),
        ).get()
        assertNotNull(reponse.source, "la source (stub intrinsèque) est servie par un moteur VIVANT")
        assertTrue("String" in reponse.source!!, "la source contient « String »")

        // Le FQCN inconnu reste HONNÊTE : null (contrat §9.2).
        val introuvable = b.experimental.decompiledSource(
            ParamsFqcnRpc("naquette.inexistante.Classe"),
        ).get()
        assertNull(introuvable.source, "FQCN inconnu → source null (honnêteté §9.2)")
    }

    /** 7 — LIVE-REPUBLISH : la re-sync du module REPUBLIE les documents ouverts (auditeur de contexte, motif vendord). */
    @Test
    @Order(7)
    fun laResyncRepublishLesDocumentsOuvertsDuModule() {
        val env = environnement ?: fail("environnement absent")
        val b = boucle ?: fail("boucle absente")

        // Util.java dans neufmodule — le contexte « :neuf » est VIDE au
        // départ : le type Neuf (pourtant sur disque) NE RÉSOUT PAS.
        val sourceUtil = """
            package neuf;

            public class Util {
                int x = new Neuf().valeur;
            }
        """.trimIndent() + "\n"
        Files.writeString(sourcesNeuves.resolve("neuf/Util.java"), sourceUtil)
        val document = ouvrir(sourcesNeuves.resolve("neuf/Util.java"), sourceUtil)

        // Première publication : l'ERREUR « Neuf cannot be resolved ».
        val premiere = b.attendrePublication(document.uri)
        assertNotNull(premiere, "une première publication arrive pour Util.java")
        assertTrue(
            premiere.diagnostics.isNotEmpty(),
            "le document du module au contexte VIDE porte des erreurs — trouvé : ${premiere.diagnostics.map { it.message }}",
        )

        // LA SYNC : le contexte « :neuf » reçoit ses racines (remplacement
        // EFFECTIF) — l'AUDITEUR republie le document ouvert SANS requête
        // client : l'erreur s'efface (live-republish du vendord).
        env.remplacerContexteModule(":neuf", listOf(sourcesNeuves))
        val republiee = attendreSansErreur(b, document.uri)
        assertTrue(
            republiee.diagnostics.none { it.severity == org.eclipse.lsp4j.DiagnosticSeverity.Error },
            "la REPUBLICATION automatique efface l'erreur dès la sync (live-republish) — trouvées : ${republiee.diagnostics.map { it.message }}",
        )
    }

    /** Attend (patiemment) la publication SANS ERREUR d'une URI. */
    private fun attendreSansErreur(b: BoucleLocale, uri: String, delaiMs: Long = 15_000): org.eclipse.lsp4j.PublishDiagnosticsParams {
        val fin = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(delaiMs)
        while (System.nanoTime() < fin) {
            val candidate = b.espion.publications.lastOrNull {
                it.uri == uri && it.diagnostics.none { d -> d.severity == org.eclipse.lsp4j.DiagnosticSeverity.Error }
            }
            if (candidate != null) return candidate
            Thread.sleep(100)
        }
        fail("aucune publication sans erreur pour $uri après ${delaiMs}ms (reçues : ${b.espion.publications.filter { it.uri == uri }.map { it.diagnostics.size }})")
    }
}

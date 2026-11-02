package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * REPRODUCTION des retours appareil du 30/10/2026 (CodeIDE 0.204.2,
 * moteur v1.1.2, logs codeide-logs-2026-10-30-025629.zip) : la chaîne
 * complète est FRANCHIE mais deux familles de fonctionnalités ne se
 * montrent pas à l'utilisateur —
 *
 *  1. **Complétion d'import** : « j'ai tapé "import ", il n'y a pas de
 *     suggestions de package ; j'ai même essayé "import com.", j'aurais
 *     dû avoir "exemple" » ;
 *  2. **Diagnostics analytiques** : « le fichier des imports non
 *     utilisés, variables non utilisées et autres, il ne les affiche
 *     pas » ;
 *  3. **superDefinition** : le journal appareil porte
 *     `Unsupported request method: textDocument/superDefinition` — le
 *     client cel-lsp appelle la méthode par son nom COMPLET
 *     (`textDocument/superDefinition`, cf. CodeIdeLanguageServer), pas
 *     par le nom nu enregistré par le serveur.
 *
 * Le montage reproduit le CHEMIN DE PRODUCTION : serveur réel sur un
 * dossier temporaire (racine source `com/exemple/monip` — le paquet du
 * projet de l'utilisateur), client LSP4J réel via [BoucleLocale]. Le
 * client de superDefinition utilise une interface MIRROR DE CEL-LSP
 * ([ClientCelLsp] — @JsonRequest explicite « textDocument/… ») : c'est
 * le seul moyen d'éprouver le NOM SUR LE FIL, pas le nom du harnais.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Repro20261030AppareilTest {

    /** La racine des fixtures (MANUELLE — motif Fiche026/066/054). */
    private lateinit var dossier: Path

    /** La boucle locale de la suite. */
    private var boucle: BoucleLocale? = null

    /** La boucle mirror cel-lsp (noms de méthodes COMPLETS sur le fil). */
    private var distantCel: ClientCelLsp? = null

    // ---------------------------------------------------------------- //
    // Sources — le paquet du projet utilisateur est com.exemple.monip
    // ---------------------------------------------------------------- //

    /** Fichier valide PORTANT des signaux « unused » (import, champ, locale). */
    private val sourceUnused = """
        package com.exemple.monip;

        import java.util.ArrayList;

        public class Main {
            private int jamaisUtilise = 3;

            public int travail() {
                int x = 42;
                return 0;
            }
        }
    """.trimIndent() + "\n"

    /** Fichier valide pour la complétion/surbrillance/inlay (une vraie méthode utilisée). */
    private val sourceTravail = """
        package com.exemple.monip;

        import java.util.List;

        public class Travail {
            private int total = 0;

            public int cumule(List<Integer> valeurs) {
                for (int v : valeurs) {
                    total += v;
                }
                return total;
            }
        }
    """.trimIndent() + "\n"

    /** « import » sans préfixe au TOP (position valide — ce que l'utilisateur a tapé). */
    private val sourceImportVide = """
        package com.exemple.monip;

        import ${' '}

        public class Main {
        }
    """.trimIndent() + "\n"

    /** « import com. » au TOP (position valide). */
    private val sourceImportCom = """
        package com.exemple.monip;

        import com.

        public class Main {
        }
    """.trimIndent() + "\n"

    /** « import com. » DANS le corps de la classe (le contexte CASSÉ des logs appareil). */
    private val sourceImportDansCorps = """
        package com.exemple.monip;

        public class Main {
            void essai() {
                import com.
            }
        }
    """.trimIndent() + "\n"

    // ---------------------------------------------------------------- //
    // Montage
    // ---------------------------------------------------------------- //

    @BeforeAll
    fun avantTout() {
        dossier = Files.createTempDirectory("repro-20261030")
        val paquet = dossier.resolve("com/exemple/monip")
        Files.createDirectories(paquet)
        Files.writeString(paquet.resolve("Main.java"), "package com.exemple.monip;\n\npublic class Main {\n}\n")

        val jdk = jo.codeide.lsp.java.navigation.JdkDeTest.home()
        org.junit.jupiter.api.Assumptions.assumeTrue(
            jdk != null,
            "JDK de test indisponible (lib/modules) — repro ignorée (gate visible)",
        )

        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine ->
                racine?.let {
                    jo.codeide.lsp.java.MoteurJava(
                        racines = listOf(it),
                        jars = emptyList(),
                        jdkHome = jdk,
                    )
                }
            },
        )
        boucle = BoucleLocale(serveur).also { b ->
            b.demarrer()
            b.client.initialize(InitializeParams().apply { rootUri = dossier.toUri().toString() }).get()
            b.client.initialized(InitializedParams())
        }

        // La boucle MIRROR CEL-LSP : un SECOND serveur (le protocole
        // n'autorise qu'un initialize par session), un client dont
        // l'interface porte les noms COMPLETS (@JsonRequest explicites)
        // — le chemin exact de CodeIdeLanguageServer côté production.
        val serveurCel = ServeurLangageJava(
            fabriqueMoteur = { racine ->
                racine?.let {
                    jo.codeide.lsp.java.MoteurJava(
                        racines = listOf(it),
                        jars = emptyList(),
                        jdkHome = jdk,
                    )
                }
            },
        )
        val serveurVersClient = java.io.PipedOutputStream()
        val clientVersServeur = java.io.PipedOutputStream()
        val entreeClient = java.io.PipedInputStream(serveurVersClient)
        val entreeServeur = java.io.PipedInputStream(clientVersServeur)
        val lanceurServeur = org.eclipse.lsp4j.jsonrpc.Launcher.Builder<org.eclipse.lsp4j.services.LanguageClient>()
            .setLocalService(serveurCel)
            .setRemoteInterface(org.eclipse.lsp4j.services.LanguageClient::class.java)
            .setInput(entreeServeur)
            .setOutput(serveurVersClient)
            .create()
        serveurCel.connect(lanceurServeur.remoteProxy)
        lanceurServeur.startListening()
        val lanceurClient = org.eclipse.lsp4j.jsonrpc.Launcher.Builder<ClientCelLsp>()
            .setLocalService(ClientEspion())
            .setRemoteInterface(ClientCelLsp::class.java)
            .setInput(entreeClient)
            .setOutput(clientVersServeur)
            .create()
        lanceurClient.startListening()
        distantCel = lanceurClient.remoteProxy
        distantCel!!.initialize(InitializeParams().apply { rootUri = dossier.toUri().toString() }).get()
        distantCel!!.initialized(InitializedParams())
    }

    /** Ouvre un document de test et rend son identifiant. */
    private fun ouvrir(nom: String, texte: String, version: Int = 0): TextDocumentIdentifier {
        val b = boucle ?: fail("boucle absente")
        val uri = dossier.resolve("com/exemple/monip/$nom").toUri().toString()
        b.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", version, texte)),
        )
        return TextDocumentIdentifier(uri)
    }

    /** La position (ligne, colonne) d'un texte cherché dans une source. */
    private fun positionDe(source: String, cherche: String, occurrence: Int = 0): Position {
        var indice = -1
        repeat(occurrence + 1) { indice = source.indexOf(cherche, indice + 1) }
        val ligne = source.substring(0, indice).count { it == '\n' }
        val debutLigne = source.lastIndexOf('\n', indice - 1) + 1
        return Position(ligne, indice - debutLigne)
    }

    /** La position (ligne, colonne) d'un texte cherché dans une source, décalée d'un suffixe. */
    private fun positionApres(source: String, cherche: String): Position {
        val base = positionDe(source, cherche)
        return Position(base.line, base.character + cherche.length)
    }

    /** Une complétion au caret après un texte cherché. */
    private fun completion(source: String, apres: String): List<org.eclipse.lsp4j.CompletionItem> {
        val document = ouvrir("Completion.java", source)
        val params = CompletionParams()
        params.textDocument = document
        params.position = positionApres(source, apres)
        val reponse = boucle!!.client.textDocumentService.completion(params).get()
        return if (reponse.isLeft) reponse.left else reponse.right.items
    }

    /** L'attente bornée d'une valeur (polling 50 ms). */
    private fun <T> attendre(delaiMs: Long = 5_000, fournisseur: () -> T?): T? {
        val fin = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delaiMs)
        var dernier: T? = null
        while (System.nanoTime() < fin) {
            dernier = fournisseur()
            if (dernier != null) return dernier
            Thread.sleep(50)
        }
        return dernier
    }

    // ---------------------------------------------------------------- //
    // 1. Complétion d'import — « import » / « import com. » au TOP
    // ---------------------------------------------------------------- //

    @Test
    fun completion_import_vide_propose_les_paquets_de_premier_niveau() {
        val items = completion(sourceImportVide, "import ")
        val libelles = items.map { it.label }
        println("[REPRO] import (vide) → ${libelles.size} items : ${libelles.take(20)}")
        assertTrue(libelles.isNotEmpty(), "la complétion d'import vide rend des items — reçu : $libelles")
        assertTrue("com" in libelles, "« com » (paquet projet) est proposé — reçu : ${libelles.take(12)}")
        assertTrue("java" in libelles, "« java » (surface publique jrt) est proposé — reçu : ${libelles.take(12)}")
    }

    @Test
    fun completion_import_com_propose_exemple() {
        val items = completion(sourceImportCom, "import com.")
        val libelles = items.map { it.label }
        println("[REPRO] import com. → ${libelles.size} items : ${libelles.take(20)}")
        assertTrue("exemple" in libelles, "« exemple » (paquet du projet) est proposé — reçu : ${libelles.take(20)}")
    }

    @Test
    fun completion_import_com_exemple_propose_monip() {
        val source = sourceImportCom.replace("import com.", "import com.exemple.")
        val items = completion(source, "import com.exemple.")
        val libelles = items.map { it.label }
        println("[REPRO] import com.exemple. → ${libelles.size} items : $libelles.take(20)}")
        assertTrue("monip" in libelles, "« monip » est proposé — reçu : ${libelles.take(20)}")
    }

    @Test
    fun completion_import_com_dans_le_corps_de_classe_ne_ment_pas() {
        // Le contexte des LOGS APPAREIL : l'utilisateur a tapé son import
        // DANS le corps de la classe (erreur de syntaxe). La réponse doit
        // rester HONNÊTE (pas de crash, pas de mots-clés parasites
        // massifs) — on consigne ce que le moteur rend dans ce cas.
        val items = completion(sourceImportDansCorps, "import com.")
        val libelles = items.map { it.label }
        println("[REPRO] import com. DANS le corps → ${libelles.size} items : ${libelles.take(20)}")
        // Aucun contrat strict ici : la position est invalide, la réponse
        // peut être vide — le test CONSIGNE le comportement pour décider.
    }

    // ---------------------------------------------------------------- //
    // 2. Diagnostics analytiques — unused import / locale / champ
    // ---------------------------------------------------------------- //

    @Test
    fun diagnostics_import_inutilise_publie() {
        val uri = dossier.resolve("com/exemple/monip/Main.java").toUri().toString()
        ouvrir("Main.java", sourceUnused)
        val publication = attendre {
            boucle!!.espion.publications.lastOrNull { it.uri == uri && it.diagnostics.isNotEmpty() }
        } ?: fail("aucune publication de diagnostics pour Main.java")
        val messages = publication.diagnostics.map { "${it.severity}:${it.message}" }
        println("[REPRO] diagnostics full → $messages")
        assertTrue(
            messages.any { it.contains("The import java.util.ArrayList is never used") },
            "l'import inutilisé est publié — reçu : $messages",
        )
    }

    @Test
    fun diagnostics_variable_locale_inutilisee_publie() {
        val uri = dossier.resolve("com/exemple/monip/Main.java").toUri().toString()
        ouvrir("Main.java", sourceUnused)
        val publication = attendre {
            boucle!!.espion.publications.lastOrNull { it.uri == uri && it.diagnostics.isNotEmpty() }
        } ?: fail("aucune publication de diagnostics pour Main.java")
        val messages = publication.diagnostics.map { it.message }
        println("[REPRO] diagnostics full → $messages")
        assertTrue(
            messages.any { it.contains("The value of the local variable x is not used") },
            "la locale inutilisée est publiée — reçu : $messages",
        )
    }

    @Test
    fun diagnostics_champ_prive_inutilise_publie() {
        val uri = dossier.resolve("com/exemple/monip/Main.java").toUri().toString()
        ouvrir("Main.java", sourceUnused)
        val publication = attendre {
            boucle!!.espion.publications.lastOrNull { it.uri == uri && it.diagnostics.isNotEmpty() }
        } ?: fail("aucune publication de diagnostics pour Main.java")
        val messages = publication.diagnostics.map { it.message }
        println("[REPRO] diagnostics full → $messages")
        assertTrue(
            messages.any { it.contains("jamaisUtilise") && it.contains("not used") },
            "le champ privé inutilisé est publié — reçu : $messages",
        )
    }

    @Test
    fun diagnostics_frappe_dans_un_corps_l_import_inutilise_survit() {
        val uri = dossier.resolve("com/exemple/monip/Main.java").toUri().toString()
        ouvrir("Main.java", sourceUnused, version = 0)
        attendre {
            boucle!!.espion.publications.lastOrNull { it.uri == uri && it.diagnostics.isNotEmpty() }
        } ?: fail("aucune publication initiale")

        // didChange : une frappe CONFINÉE dans le corps de travail() —
        // le cas de production (l'utilisateur tape dans une méthode).
        val frappee = sourceUnused.replace("return 0;", "return 0; ")
        val identifiant = VersionedTextDocumentIdentifier(uri, 1)
        boucle!!.client.textDocumentService.didChange(
            DidChangeTextDocumentParams(identifiant, listOf(TextDocumentContentChangeEvent(frappee))),
        )

        val apres = attendre {
            val derniere = boucle!!.espion.publications.lastOrNull { it.uri == uri }
            derniere?.takeIf { (it.version ?: 0) >= 1 }
        } ?: fail("aucune publication après la frappe dans le corps")
        val messages = apres.diagnostics.map { it.message }
        println("[REPRO] diagnostics après frappe dans le corps → $messages")
        assertTrue(
            messages.any { it.contains("The import java.util.ArrayList is never used") },
            "l'import inutilisé SURVIT à la frappe dans un corps — reçu : $messages",
        )
    }

    // ---------------------------------------------------------------- //
    // 3. superDefinition — le nom COMPLET sur le fil (mirror cel-lsp)
    // ---------------------------------------------------------------- //

    /**
     * L'interface MIRROR du client de production (cel-lsp
     * CodeIdeLanguageServer) : les extensions maison voyagent par leur
     * nom COMPLET `textDocument/superDefinition` — pas le nom nu du
     * harnais du dépôt. C'est le chemin exact qui a produit
     * `Unsupported request method` dans les logs appareil.
     */
    public interface ClientCelLsp : LanguageServer {

        @JsonRequest("textDocument/superDefinition")
        public fun superDefinition(params: TextDocumentPositionParams): CompletableFuture<List<CibleSuperRpc>>
    }

    @Test
    fun superDefinition_par_son_nom_complet_est_servi() {
        val distant = distantCel ?: fail("client mirror cel-lsp absent")
        val uri = dossier.resolve("com/exemple/monip/Travail.java").toUri().toString()
        distant.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, sourceTravail)),
        )
        val params = TextDocumentPositionParams(TextDocumentIdentifier(uri), positionDe(sourceTravail, "cumule"))
        try {
            val cibles = distant.superDefinition(params).get(5, TimeUnit.SECONDS)
            println("[REPRO] textDocument/superDefinition → ${cibles.size} cible(s)")
            // Le contrat honnête : pas d'exception — la méthode EST servie.
        } catch (e: java.util.concurrent.ExecutionException) {
            val cause = e.cause
            if (cause is ResponseErrorException) {
                fail(
                    "textDocument/superDefinition est RECONNU par le serveur — " +
                        "reçu : ${cause.responseError.message}",
                )
            }
            throw e
        }
    }

    // ---------------------------------------------------------------- //
    // 4. Surbrillance et inlay — les « no results » des logs appareil
    // ---------------------------------------------------------------- //

    @Test
    fun documentHighlight_sur_un_identifiant_rend_ses_occurrences() {
        val uri = dossier.resolve("com/exemple/monip/Travail.java").toUri().toString()
        ouvrir("Travail.java", sourceTravail)
        val params = DocumentHighlightParams()
        params.textDocument = TextDocumentIdentifier(uri)
        params.position = positionDe(sourceTravail, "total")
        val surbrillances = boucle!!.client.textDocumentService.documentHighlight(params).get()
        println("[REPRO] documentHighlight(total) → ${surbrillances?.size ?: 0} occurrence(s)")
        assertTrue(
            !surbrillances.isNullOrEmpty(),
            "les occurrences de « total » sont rendues (2 dans la source)",
        )
    }

    @Test
    fun inlayHint_sur_un_code_sain_rend_ses_indices() {
        val uri = dossier.resolve("com/exemple/monip/Travail.java").toUri().toString()
        ouvrir("Travail.java", sourceTravail)
        val params = InlayHintParams()
        params.textDocument = TextDocumentIdentifier(uri)
        params.range = org.eclipse.lsp4j.Range(Position(0, 0), Position(20, 0))
        val indices = boucle!!.client.textDocumentService.inlayHint(params).get()
        println("[REPRO] inlayHint → ${indices?.size ?: 0} indice(s)")
        // Consigne : les indices (lambda/paramètres) dépendent du code —
        // on exige au minimum une liste NON NULLE (jamais d'exception).
        org.junit.jupiter.api.Assertions.assertNotNull(indices)
    }
}

package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.completion.ServiceCompletion
import jo.codeide.lsp.java.concurrence.EpoqueRetiree
import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.interne.PontEcj
import jo.codeide.lsp.java.navigation.IndexMotsSources
import jo.codeide.lsp.java.navigation.ServiceUsages
import jo.codeide.lsp.java.protocole.ClientEspion
import jo.codeide.lsp.java.protocole.ServeurLangageJava
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Vague V-F1 — DOUBLE-TAMPON et ÉPOQUES de l'environnement de compilation
 * (ADR 0029 phase 1, écarts G-030/H2) : l'extension aux ENVIRONNEMENTS du
 * design copy-on-write des documents (N8).
 *
 * Invariants éprouvés (critères de sortie de la vague, plan 04) :
 *  - **0 figage** : pendant la reconstruction du moteur neuf (réchauffement
 *    ARTIFICIELLEMENT BLOQUÉ — le banc tient le fil de reconstruction), les
 *    requêtes continuent d'être servies par l'ancien moteur SANS attendre —
 *    la rafale ENTIÈRE se solde avant la libération du réchauffeur ;
 *  - **0 réponse périmée** : une requête COUPÉE par le swap (départ sur
 *    l'ancienne époque, retour après le swap) est REJOUÉE sur l'époque
 *    courante — le résultat livré vient du moteur NEUF ; après le swap,
 *    chaque réponse reflète le classpath de l'époque vivante ;
 *  - **annulation fine de base** : les points de contrôle coupent la
 *    collecte de complétion (l'exception TRAVERSE la frontière « jamais
 *    null » du service) et le balayage d'usages (coupe ENTRE deux fichiers,
 *    déterministe par résolveur intercepté) ;
 *  - **syncs rapides successives** : seules les reconstructions NON
 *    dépassées swappent (une notification d'époque par swap effectif) ;
 *  - **repli documenté** : la surcharge par instance rétablit le
 *    comportement d'avant V-F1 (dispose immédiat, zéro reconstruction) ;
 *  - **serveur** : au swap d'époque, les documents OUVERTS sont republiés
 *    sur l'époque neuve (les faux « cannot be resolved » s'effacent SANS
 *    attendre la frappe suivante).
 */
class DoubleTamponTest {

    /** Le source du banc : une classe Y du NOUVEAU classpath (invisible à l'ancienne époque). */
    private val sourceAvecY = """
        package demo;
        public class Main {
            public void go() {
                Y y = new Y();
            }
        }
    """.trimIndent()

    private fun dossier(nom: String): Path = Files.createTempDirectory("vf1-$nom")

    /** Racine ne contenant QUE X (l'époque ancienne). */
    private fun srcAvant(): Path = dossier("avant").also {
        val demo = Files.createDirectories(it.resolve("demo"))
        demo.resolve("X.java").writeText("package demo;\npublic class X { }\n")
    }

    /** Racine contenant X ET Y (l'époque neuve). */
    private fun srcApres(): Path = dossier("apres").also {
        val demo = Files.createDirectories(it.resolve("demo"))
        demo.resolve("X.java").writeText("package demo;\npublic class X { }\n")
        demo.resolve("Y.java").writeText("package demo;\npublic class Y { }\n")
    }

    /** Le nombre d'erreurs « cannot be resolved » d'un diagnose (via le moteur donné). */
    private fun erreursDe(moteur: MoteurJava, chemin: String): Int =
        moteur.diagnose(chemin, sourceAvecY).count { it.severite == Severite.ERREUR }

    // ------------------------------------------------------------------ //
    // 0 figage — la rafale se solde PENDANT la reconstruction bloquée
    // ------------------------------------------------------------------ //

    @Test
    fun zeroFigageLaRafaleSertPendantLaReconstructionBloquee() {
        val environnement = EnvironnementCompilation()
        // TOUT document de ce banc vit dans le module « :app » (le routage
        // du protocole frais passe par le routeur — fiche 047).
        environnement.routeurFichierModule = { _: String -> ":app" }
        environnement.remplacerContexteModule(":app", listOf(srcAvant()))
        val chemin = "/demo/Main.java"
        val moteur1 = environnement.moteur(":app")
        // Chauffe de l'ancien : mémo de diagnose rempli (frappe nominale).
        assertTrue(erreursDe(moteur1, chemin) >= 1, "l'ancienne époque ne connaît pas Y : ≥ 1 erreur")

        // Réchauffeur BLOQUÉ : le fil de reconstruction tient la fenêtre
        // ouverte (l'appareil y paie 11–12 s — ici un loquet, pas un sommeil).
        val liberer = CountDownLatch(1)
        environnement.rechauffeur = { liberer.await(10, TimeUnit.SECONDS) }

        val servies = AtomicInteger(0)
        val latences = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val rafale = Thread {
            repeat(8) {
                val debut = System.nanoTime()
                val erreurs = environnement.calculerFraiche(chemin) { it.diagnose(chemin, sourceAvecY) }
                    .count { d -> d.severite == Severite.ERREUR }
                latences.add((System.nanoTime() - debut) / 1_000_000)
                if (erreurs >= 1) servies.incrementAndGet() // ancienne époque : honnête
            }
        }
        rafale.start()

        // La sync : remplacement effectif — la reconstruction démarre et BLOQUE.
        environnement.remplacerContexteModule(":app", listOf(srcApres()))
        assertTrue(environnement.reconstructionsEnCours() >= 1, "une reconstruction est en cours (bloquée)")

        // LA PREUVE du « 0 figage » : la rafale ENTIERE se solde alors que
        // le réchauffeur tient encore le fil — aucune requête n'attend le neuf.
        rafale.join(10_000)
        assertTrue(!rafale.isAlive, "la rafale ne doit JAMAIS attendre la reconstruction")
        assertEquals(8, latences.size, "les 8 requêtes ont toutes été servies")
        assertTrue(servies.get() >= 1, "les réponses de la fenêtre viennent de l'ancienne époque (légitime : ≥ 1 erreur)")
        // Bornes généreuses (CI partagée) : le figage d'avant V-F1 se
        // manifesterait par un join qui EXPIRE, pas par quelques ms.
        assertTrue(latences.max() < 5_000, "aucune requête ne s'est figée (max ${latences.max()} ms)")

        // Libération : le swap a lieu, l'époque neuve sert Y sans erreur.
        liberer.countDown()
        assertTrue(environnement.attendreReconstructions(), "la reconstruction draine")
        val moteur2 = environnement.moteur(":app")
        assertNotSame(moteur1, moteur2, "le moteur neuf est en place")
        assertEquals(0, erreursDe(moteur2, chemin), "l'époque neuve connaît Y : 0 erreur")
    }

    // ------------------------------------------------------------------ //
    // 0 réponse périmée — le rejeu des requêtes coupées par le swap
    // ------------------------------------------------------------------ //

    @Test
    fun zeroReponsePerimeeLaRequeteCoupeeEstRejoueeSurLEpoqueNeuve() {
        val environnement = EnvironnementCompilation()
        environnement.routeurFichierModule = { _: String -> ":app" }
        environnement.remplacerContexteModule(":app", listOf(srcAvant()))
        val chemin = "/demo/Main.java"
        val moteur1 = environnement.moteur(":app")
        assertTrue(erreursDe(moteur1, chemin) >= 1, "chauffe de l'ancienne époque")

        val demarree = CountDownLatch(1)
        val liberer = CountDownLatch(1)
        val moteursVus = java.util.Collections.synchronizedList(mutableListOf<MoteurJava>())

        val executeur = Executors.newSingleThreadExecutor()
        val future = executeur.submit<Int> {
            environnement.calculerFraiche(chemin) { moteur ->
                moteursVus.add(moteur)
                if (moteursVus.size == 1) {
                    // La requête COUPÉE : partie sur l'époque ancienne, elle
                    // s'attarde PENDANT la sync (une vraie frappe en vol).
                    demarree.countDown()
                    liberer.await(10, TimeUnit.SECONDS)
                }
                erreursDe(moteur, chemin)
            }
        }

        demarree.await(10, TimeUnit.SECONDS)
        // La sync se joue PENDANT la requête : époque ancienne capturée,
        // swap effectif avant le retour du calcul.
        environnement.remplacerContexteModule(":app", listOf(srcApres()))
        assertTrue(environnement.attendreReconstructions(), "le swap est effectif avant le retour")
        val moteur2 = environnement.moteur(":app")
        assertNotSame(moteur1, moteur2)

        liberer.countDown()
        val erreursLivrees = future.get(10, TimeUnit.SECONDS)
        executeur.shutdown()

        // Le calcul ancien s'est SOLDE (1 erreur sur l'époque révolue) mais
        // n'a PAS été livré : le protocole l'a REJOUÉ sur l'époque courante.
        assertEquals(0, erreursLivrees, "la réponse livrée vient de l'époque NEUVE (0 erreur)")
        assertEquals(listOf(moteur1, moteur2), moteursVus, "exactement deux passes : l'ancienne (coupée), la neuve (livrée)")
    }

    @Test
    fun apresLeSwapTouteRequeteRefleteLEpoqueVivante() {
        val environnement = EnvironnementCompilation()
        environnement.routeurFichierModule = { _: String -> ":app" }
        environnement.remplacerContexteModule(":app", listOf(srcAvant()))
        val chemin = "/demo/Main.java"
        assertTrue(erreursDe(environnement.moteur(":app"), chemin) >= 1)

        environnement.remplacerContexteModule(":app", listOf(srcApres()))
        assertTrue(environnement.attendreReconstructions(), "la reconstruction draine")

        // Trois requêtes CONSÉCUTIVES par le protocole frais : toutes neuves.
        repeat(3) {
            val erreurs = environnement.calculerFraiche(chemin) { it.diagnose(chemin, sourceAvecY) }
                .count { d -> d.severite == Severite.ERREUR }
            assertEquals(0, erreurs, "chaque réponse d'après le swap reflète le classpath neuf")
        }
    }

    // ------------------------------------------------------------------ //
    // Annulation fine — complétion : la coupure TRAVERSE la frontière
    // ------------------------------------------------------------------ //

    @Test
    fun laCoupureFineTraverseLaFrontiereDeLaCompletion() {
        val environnement = EnvironnementNoms(emptyList(), emptyList(), null)
        val pont = PontEcj(environnement, "17")
        val source = "package demo;\npublic class M {\n    void go() {\n        Str\n    }\n}\n"
        val offset = source.indexOf("Str") + 3

        // Moteur RETIRÉ (estCoupe toujours vrai) : la collecte est coupée au
        // premier point de contrôle (types visibles / java.lang) et
        // l'exception TRAVERSE le repli « liste vide honnête » de la fiche
        // 035 — l'avaler livrerait une complétion VIDE, pire qu'un rejeu.
        val serviceCoupe = ServiceCompletion(
            pontFournisseur = { pont },
            environnementFournisseur = { environnement },
            arbreOriginal = { _, _ -> null },
            estCoupe = { true },
        )
        assertFailsWith<EpoqueRetiree> { serviceCoupe.completer("/demo/M.java", source, offset) }

        // Moteur VIVANT (jamais coupé) : la même complétion sert normalement
        // — le point de contrôle est INVISIBLE sans retrait (non-régression).
        val serviceVivant = ServiceCompletion(
            pontFournisseur = { pont },
            environnementFournisseur = { environnement },
            arbreOriginal = { _, _ -> null },
        )
        val reponse = serviceVivant.completer("/demo/M.java", source, offset)
        assertTrue(
            reponse.items.any { it.libelle == "String" },
            "la complétion vivante propose String (java.lang) — reçu : ${reponse.items.map { it.libelle }.take(10)}",
        )
    }

    // ------------------------------------------------------------------ //
    // Annulation fine — balayage d'usages : coupe DÉTERMINISTE entre fichiers
    // ------------------------------------------------------------------ //

    @Test
    fun laCoupureFineCoupeLeBalayageDUsagesEntreDeuxFichiers() {
        val corpus = dossier("usages")
        val demoCorpus = Files.createDirectories(corpus.resolve("demo"))
        demoCorpus.resolve("T.java").writeText("package demo;\npublic class T { public int a; }\n")
        for (i in 1..3) {
            // Récepteur NOMMÉ (la forme canonique des fixtures d'usages :
            // `c.memoire` — le collecteur matche la référence qualifiée).
            demoCorpus.resolve("U$i.java").writeText("package demo;\npublic class U$i { T t = new T(); int b = t.a; }\n")
        }
        val cheminT = demoCorpus.resolve("T.java").toString()
        val sourceT = demoCorpus.resolve("T.java").readText()
        val offsetA = sourceT.indexOf("a;")
        val intercepte = CountDownLatch(1)
        val liberer = CountDownLatch(1)
        val phaseCoupee = AtomicBoolean(false)
        val premierFichierIntercepte = AtomicBoolean(false)
        val estCoupe = AtomicBoolean(false)

        // Le résolveur INTERCEPTÉ : la PREMIÈRE source balayée de la phase
        // COUPÉE tient le balayage (le temps de retirer le moteur), les
        // suivantes servent la preuve de la coupure. Le moteur réel
        // résout pour de vrai (le balayage vivant traverse le même chemin).
        val moteur = MoteurJava(racines = listOf(corpus))
        val service = ServiceUsages(
            racines = listOf(corpus),
            indexMots = IndexMotsSources(listOf(corpus)),
            resoudre = { chemin, texte ->
                if (phaseCoupee.get() && chemin != cheminT && premierFichierIntercepte.compareAndSet(false, true)) {
                    intercepte.countDown()
                    liberer.await(10, TimeUnit.SECONDS)
                }
                moteur.resolveUnit(chemin, texte, genererCode = false)?.arbre
            },
            estCoupe = { estCoupe.get() },
        )

        // Moteur VIVANT : le balayage est EXHAUSTIF (4 usages de « a » :
        // la déclaration + 3 lectures — non-régression de l'annulation fine).
        val usagesVivants = service.references(cheminT, sourceT, offsetA)
        assertEquals(4, usagesVivants.size, "le balayage vivant reste exhaustif (déclaration + 3 usages)")

        // Moteur RETIRÉ PENDANT le balayage : le point de contrôle du
        // FICHIER SUIVANT coupe — jamais un résultat partiel livré.
        val levée = AtomicReference<Throwable?>(null)
        val executeur = Executors.newSingleThreadExecutor()
        phaseCoupee.set(true)
        val future = executeur.submit {
            try {
                service.references(cheminT, sourceT, offsetA)
            } catch (t: Throwable) {
                levée.set(t)
            }
        }
        assertTrue(intercepte.await(10, TimeUnit.SECONDS), "le balayage est entré dans la source interceptée")
        estCoupe.set(true) // le retrait : le jeton d'époque s'annule ICI
        liberer.countDown()
        future.get(10, TimeUnit.SECONDS)
        executeur.shutdown()
        assertTrue(levée.get() is EpoqueRetiree, "le balayage coupé lève EpoqueRetiree (reçu : ${levée.get()})")
    }

    // ------------------------------------------------------------------ //
    // Syncs rapides successives — seule la dernière reconstruction swappe
    // ------------------------------------------------------------------ //

    @Test
    fun syncsRapidesSuccessivesUneSeuleEpoqueSwitchee() {
        val environnement = EnvironnementCompilation()
        val avant = srcAvant()
        val apres = srcApres()
        val apresBis = dossier("apres-bis").also {
            val demo = Files.createDirectories(it.resolve("demo"))
            demo.resolve("X.java").writeText("package demo;\npublic class X { }\n")
            demo.resolve("Y.java").writeText("package demo;\npublic class Y { }\n")
            demo.resolve("Z.java").writeText("package demo;\npublic class Z { }\n")
        }
        environnement.remplacerContexteModule(":app", listOf(avant))
        val chemin = "/demo/Main.java"
        val moteur1 = environnement.moteur(":app")
        assertTrue(erreursDe(moteur1, chemin) >= 1)

        // Les deux syncs tombent PENDANT la première reconstruction (le fil
        // unique les sérialise) : la première build sera dépassée, jetée.
        val liberer = CountDownLatch(1)
        environnement.rechauffeur = { liberer.await(10, TimeUnit.SECONDS) }
        val notificationsEpoque = AtomicInteger(0)
        environnement.ajouterAuditeurEpoque { notificationsEpoque.incrementAndGet() }

        environnement.remplacerContexteModule(":app", listOf(apres))
        environnement.remplacerContexteModule(":app", listOf(apresBis))
        // Génération : 1 (pose initiale) + 2 remplacements effectifs.
        assertEquals(3L, environnement.generationDe(":app"), "pose initiale + deux remplacements effectifs : génération 3")

        liberer.countDown()
        assertTrue(environnement.attendreReconstructions(), "les reconstructions drainent")

        assertEquals(1, notificationsEpoque.get(), "UNE seule notification d'époque : la build dépassée ne swappe pas")
        val moteur2 = environnement.moteur(":app")
        assertNotSame(moteur1, moteur2)
        assertEquals(0, erreursDe(moteur2, chemin), "l'époque vivante est la DERNIÈRE sync (Y et Z résolvent)")
    }

    // ------------------------------------------------------------------ //
    // Repli documenté — la surcharge par instance rétablit l'avant V-F1
    // ------------------------------------------------------------------ //

    @Test
    fun repliDocumenteDisposeImmediatementSansReconstruction() {
        val environnement = EnvironnementCompilation()
        environnement.doubleTamponForceDesactive = true
        environnement.remplacerContexteModule(":app", listOf(srcAvant()))
        val chemin = "/demo/Main.java"
        val moteur1 = environnement.moteur(":app")
        assertTrue(erreursDe(moteur1, chemin) >= 1)
        assertTrue(moteur1.parseCacheSize() >= 1, "chauffe : cache de parse rempli")

        val notificationsEpoque = AtomicInteger(0)
        environnement.ajouterAuditeurEpoque { notificationsEpoque.incrementAndGet() }

        environnement.remplacerContexteModule(":app", listOf(srcApres()))

        // Avant V-F1 exactement : dispose IMMÉDIAT et SYNCHRONE — l'ancien
        // cache est vidé, le prochain accès construit le neuf, aucun swap.
        assertEquals(0, moteur1.parseCacheSize(), "l'ancien moteur est disposé sur le champ")
        assertEquals(0, environnement.reconstructionsEnCours(), "aucune reconstruction en vol")
        assertEquals(0, notificationsEpoque.get(), "aucune notification d'époque (pas de swap)")
        val moteur2 = environnement.moteur(":app")
        assertNotSame(moteur1, moteur2, "le prochain accès construit le moteur neuf")
        assertEquals(0, erreursDe(moteur2, chemin))
        // Pose initiale + replacement effectif : deux tours d'époque.
        assertEquals(2L, environnement.generationDe(":app"), "le protocole frais reste actif en repli (l'époque a tourné)")
    }

    // ------------------------------------------------------------------ //
    // Serveur — la republication au swap d'époque
    // ------------------------------------------------------------------ //

    @Test
    fun leServeurRepublieLesDocumentsOuvertsAuSwapDEpoque() {
        val racine = dossier("serveur")
        // Les racines des modules sont les répertoires SRC — le paquet
        // « demo » s'y projette en sous-répertoire (fiche 010).
        val srcAvantServeur = Files.createDirectories(racine.resolve("avant/src/demo"))
        val srcApresServeur = Files.createDirectories(racine.resolve("apres/src/demo"))
        srcAvantServeur.resolve("X.java").writeText("package demo;\npublic class X { }\n")
        srcApresServeur.resolve("X.java").writeText("package demo;\npublic class X { }\n")
        srcApresServeur.resolve("Y.java").writeText("package demo;\npublic class Y { }\n")

        val environnement = EnvironnementCompilation()
        val cheminRacine = racine.toString()
        environnement.routeurFichierModule = { chemin -> if (chemin.startsWith(cheminRacine)) ":app" else null }
        environnement.remplacerContexteModule(":app", listOf(racine.resolve("avant/src")))

        val serveur = ServeurLangageJava(environnement) { }
        val espion = ClientEspion()
        serveur.connect(espion)
        serveur.initialize(
            InitializeParams().apply { rootUri = racine.toUri().toString() },
        ).get(10, TimeUnit.SECONDS)
        // La transition finale (fiche 055) : sans elle, didOpen reste hors
        // fenêtre (requêtes permises à partir de INITIALIZED seulement).
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())

        val uri = racine.resolve("avant/Main.java").toUri().toString()
        serveur.textDocumentService.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(uri, "java", 0, sourceAvecY),
            ),
        )

        // Époque ancienne : la publication initiale porte le squiggle honnête.
        val premiere = attendrePublication(espion, uri) ?: kotlin.test.fail("aucune publication initiale")
        assertTrue(
            premiere.diagnostics.any { it.message.contains("cannot be resolved") },
            "l'ancienne époque publie « Y cannot be resolved » (honnête)",
        )

        // La sync : double-tampon — l'ancien sert pendant la construction,
        // puis le SWAP republie les documents ouverts sur l'époque neuve
        // SANS didChange ni re-didOpen (c'est le critère « les faux
        // squiggles s'effacent dès la sync »).
        environnement.remplacerContexteModule(":app", listOf(racine.resolve("apres/src")))
        assertTrue(environnement.attendreReconstructions(), "le swap draine")

        val propre = attendrePublication(espion, uri) { publication ->
            publication.diagnostics.none { it.message.contains("cannot be resolved") }
        }
        assertTrue(propre != null, "après le swap, la republication est PROPRE (reçu : ${dernierePublication(espion, uri)?.diagnostics?.map { it.message }}")
        serveur.shutdown().get(5, TimeUnit.SECONDS)
        serveur.exit()
    }

    /** Attend (polling 50 ms) une publication d'URI satisfaisant le prédicat. */
    private fun attendrePublication(
        espion: ClientEspion,
        uri: String,
        delaiMs: Long = 15_000,
        predicat: (org.eclipse.lsp4j.PublishDiagnosticsParams) -> Boolean = { true },
    ): org.eclipse.lsp4j.PublishDiagnosticsParams? {
        val fin = System.nanoTime() + delaiMs * 1_000_000
        while (System.nanoTime() < fin) {
            val trouvee = espion.publications.lastOrNull { it.uri == uri && predicat(it) }
            if (trouvee != null) return trouvee
            Thread.sleep(50)
        }
        return espion.publications.lastOrNull { it.uri == uri && predicat(it) }
    }

    private fun dernierePublication(espion: ClientEspion, uri: String): org.eclipse.lsp4j.PublishDiagnosticsParams? =
        espion.publications.lastOrNull { it.uri == uri }
}

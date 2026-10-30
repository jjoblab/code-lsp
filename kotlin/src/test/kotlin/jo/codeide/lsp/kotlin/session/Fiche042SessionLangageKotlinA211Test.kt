package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 042 — SessionLangageKotlinA211Test : tests ADR 0211/0212 (demandes
 * D1/D2 de CodeIDE) — BRANCHEMENT et SOUS-LECTURE de la session.
 *
 * Le câblage est éprouvé sur les coutures INTERNES du module (le moteur
 * nourricier et son guichet d'index — la fiche nomme `nourrissageParMoteur`
 * et le snapshot publié) : le contrat DE SESSION de la fiche 049 se teste
 * par le SPI, le MÉCANISME de branchement se teste ici, câblé main — c'est
 * l'usage direct documenté par l'ADR 0211 (« tests, packs anciens »).
 *
 * - quand le MOTEUR possède le nourrissage (`nourrissageParMoteur`),
 *   `definirClasspath` NE RE-LECT RIEN : l'index reste tel quel (le snapshot
 *   publié, par identité) et les requêtes qui prouvent par l'ABSENCE se
 *   TAISENT tant que l'index n'est pas couvert (aucun faux négatif) ;
 * - hors moteur nourricier, le contrat SYNCHRONE historique tient : la
 *   définition nourrit ICI (couverture immédiate, vue locale) ;
 * - le verrou lecture-écriture (ADR 0212) sert les requêtes EN PARALLÈLE —
 *   écritures : mutations et définitions ; jamais d'interblocage.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier). Les requêtes qui prouvent par l'absence à
 * DiagnosticKotlin proprement dit (`kt.unresolved`) naissent à l'étage 2 :
 * à la vague 1b, la sous-lecture s'atteste par l'état du guichet d'index
 * (`indexSiPret` — null tant que rien n'est publié) et la guérison par le
 * rendu du snapshot À LA REQUÊTE SUIVANTE, sans nouvel appel de définition.
 */
class Fiche042SessionLangageKotlinA211Test {

    /** La racine temporaire des sources (preuve comportementale du scan L1). */
    @TempDir
    lateinit var racine: Path

    /**
     * La déclaration externe de preuve : résolvable par le survol après
     * rafraichirSources — les racines posées par la définition servent.
     */
    private fun ecrireDeclarationExterne() {
        Files.writeString(
            racine.resolve("Fabrique.kt"),
            "/** Fabrique des instances. */\nclass Fabrique {\n    fun creer(): Int = 1\n}\n",
        )
    }

    @Test
    fun `contrat-synchrone---hors-moteur-nourricier,-definirClasspath-nourrit-ici`() {
        ecrireDeclarationExterne()
        // Usage DIRECT : session sans moteur nourricier (tests, packs
        // anciens — ADR 0211) — la définition nourrit ICI, synchronement.
        val session = SessionLangageKotlinDocuments(moteur = null)
        session.definirClasspath(
            ClasspathKotlin(entrees = emptyList(), racinesSources = listOf(racine.toAbsolutePath().toString())),
        )
        assertNotNull(session.indexSiPret(), "hors moteur nourricier : la vue est couverte IMMÉDIATEMENT (nourrie ici)")

        // Comportement : les racines posées servent le scan local.
        val uri = "file:///principal.kt"
        val texte = "val usine = Fabrique()\n"
        session.ouvrir(uri, texte)
        session.rafraichirSources()
        assertNotNull(
            session.survol(uri, texte.indexOf("Fabrique")),
            "la session autonome résout la déclaration externe depuis SES racines",
        )
    }

    @Test
    fun `branchement---moteur-nourricier,-definirClasspath-ne-re-lect-rien`() {
        ecrireDeclarationExterne()
        val moteur = MoteurLangageKotlinNoyau()
        val classpath = ClasspathKotlin(
            entrees = listOf("bibliotheque.jar"),
            racinesSources = listOf(racine.toAbsolutePath().toString()),
        )
        assertTrue(moteur.definirClasspath(classpath), "le moteur noyau est nourricier : la passe rend true")

        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        assertSame(
            moteur.snapshotCourant,
            session.indexSiPret(),
            "branchement : la session porte le snapshot PUBLIÉ tel quel — aucune re-lecture locale",
        )

        // Comportement : les racines posées par le branchement servent le scan.
        val uri = "file:///principal.kt"
        val texte = "val usine = Fabrique()\n"
        session.ouvrir(uri, texte)
        session.rafraichirSources()
        assertNotNull(session.survol(uri, texte.indexOf("Fabrique")), "le branchement ne casse ni racines ni scan")
    }

    @Test
    fun `sous-lecture-guerit---le-snapshot-publie-rend-l-index-a-la-requete-suivante`() {
        ecrireDeclarationExterne()
        val moteur = MoteurLangageKotlinNoyau()
        val classpath = ClasspathKotlin(
            entrees = listOf("bibliotheque.jar"),
            racinesSources = listOf(racine.toAbsolutePath().toString()),
        )
        val session = moteur.creerSession() as SessionLangageKotlinDocuments

        // Branchement AVANT toute passe moteur : rien n'est couvert — la
        // session ne nourrit PAS à la place du moteur (aucune fausse vue).
        session.definirClasspath(classpath)
        assertNull(session.indexSiPret(), "sous-lecture : index non couvert — les requêtes par absence se taisent")

        // La passe moteur PUBLIE — SANS retoucher la session.
        moteur.definirClasspath(classpath)
        assertSame(
            moteur.snapshotCourant,
            session.indexSiPret(),
            "guérison : le snapshot publié rend l'index À LA REQUÊTE SUIVANTE, sans nouvel appel de définition",
        )
    }

    @Test
    fun `verrou-lecture-ecriture---requetes-paralleles-sans-interblocage`() {
        ecrireDeclarationExterne()
        val moteur = MoteurLangageKotlinNoyau()
        val session = moteur.creerSession()
        session.definirClasspath(
            ClasspathKotlin(entrees = emptyList(), racinesSources = listOf(racine.toAbsolutePath().toString())),
        )

        val uris = (1..3).map { "file:///doc$it.kt" }
        val executeur = Executors.newFixedThreadPool(6)
        try {
            val taches = (1..6).map { numero ->
                executeur.submit(
                    Callable {
                        repeat(150) { iteration ->
                            when ((numero + iteration) % 6) {
                                0 -> session.ouvrir(uris[iteration % 3], "val x$iteration = ${if (iteration % 2 == 0) "1" else ""}\n")
                                1 -> session.diagnostics(uris[iteration % 3])
                                2 -> session.plagesPliables(uris[iteration % 3])
                                3 -> session.survol(uris[iteration % 3], 0)
                                4 -> session.fermer(uris[iteration % 3])
                                else -> if (iteration % 25 == 0) session.rafraichirSources()
                            }
                        }
                    },
                )
            }
            // Aucune tâche n'a levé (les lectures vont EN PARALLÈLE, les
            // écritures attendent leur tour — jamais l'inverse).
            executeur.shutdown()
            assertTrue(
                executeur.awaitTermination(60, TimeUnit.SECONDS),
                "aucun interblocage : toutes les requêtes parallèles s'achèvent",
            )
            taches.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executeur.shutdownNow()
        }

        // Le verrou reste sain après la tempête : cohérence finale.
        session.ouvrir("file:///final.kt", "val x = ")
        assertEquals(1, session.diagnostics("file:///final.kt").size, "cohérence post-tempête : les diagnostics vivent")
        session.close()
        assertEquals(0, session.diagnostics("file:///final.kt").size, "fermeture saine après tempête")
    }
}

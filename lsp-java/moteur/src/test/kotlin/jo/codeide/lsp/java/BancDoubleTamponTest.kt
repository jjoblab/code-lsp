package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Vague V-F1 — BANC DE MESURE du double-tampon (ADR 0029 phase 1, style
 * `Fiche033BancLatenceTest` : AUCUN seuil de latence asserté — le banc
 * PRODUIT des nombres à comparer entre versions et à consigner au PV ;
 * la seule assertion est STRUCTURELLE : pendant une reconstruction
 * ARTIFICIELLEMENT TENUE OUVERTE, la rafale de frappe se solde ENTIERE —
 * le figage d'avant V-F1 (requêtes empilées derrière la reconstruction,
 * 11–12 s côté appareil, G-030/H2) ferait EXPIRER l'attente).
 *
 * Mesures imprimées (R5 — aucune valeur fabriquée) :
 *  - latence NOMINALE de frappe (diagnose mémo chaud, medians/p95 sur la
 *    rafale) ;
 *  - latence PENDANT la fenêtre de reconstruction (mêmes requêtes, le fil
 *    de reconstruction tenu par un loquet — la durée du loquet est une
 *    CONDITION du banc, pas une mesure) ;
 *  - durée RÉELLE de reconstruction à réchauffeur par défaut (jrt du
 *    runtime — le coût que l'appareil paie, ici borné JVM) ;
 *  - coût du REJEU : une requête coupée par le swap est exécutée DEUX fois
 *    (l'ancienne passe solde, la neuve livre) — le banc compte les passes.
 */
class BancDoubleTamponTest {

    private val source = """
        package demo;
        public class Main {
            public void go() {
                Y y = new Y();
            }
        }
    """.trimIndent()

    private fun racine(avecY: Boolean): Path {
        val dossier = Files.createTempDirectory("vf1-banc-${if (avecY) "apres" else "avant"}")
        val demo = Files.createDirectories(dossier.resolve("demo"))
        demo.resolve("X.java").writeText("package demo;\npublic class X { }\n")
        if (avecY) demo.resolve("Y.java").writeText("package demo;\npublic class Y { }\n")
        return dossier
    }

    /** Les p50/p95 d'un échantillon de latences MICROSECONDES (calcul par tri — jamais une valeur fabriquée). */
    private fun percentiles(echantillon: List<Long>): Pair<Long, Long> {
        val trie = echantillon.sorted()
        val p50 = trie[trie.size / 2]
        val p95 = trie[(trie.size * 95 / 100).coerceAtMost(trie.size - 1)]
        return p50 to p95
    }

    @Test
    fun bancDoubleTamponLatencePendantReconstruction() {
        val environnement = EnvironnementCompilation()
        environnement.routeurFichierModule = { _: String -> ":app" }
        environnement.remplacerContexteModule(":app", listOf(racine(avecY = false)))
        val chemin = "/demo/Main.java"
        val moteur1 = environnement.moteur(":app")

        // --- Mesure 0 : la reconstruction RÉELLE (réchauffeur par défaut,
        // jrt du runtime) — le coût que la vague déplace hors du fil de
        // frappe. Deux syncs pour deux époques mesurées.
        var dureeReelleMs = -1L
        var dureeReelle2Ms = -1L
        run {
            environnement.remplacerContexteModule(":app", listOf(racine(avecY = true)))
            val debut = System.nanoTime()
            assertTrue(environnement.attendreReconstructions(), "reconstruction réelle 1")
            dureeReelleMs = (System.nanoTime() - debut) / 1_000_000
            val apres = environnement.moteur(":app")
            assertNotSame(moteur1, apres, "sync réelle : moteur neuf en place")
            // Chauffe du neuf (mémo) puis re-sync pour la seconde mesure.
            assertEquals(0, apres.diagnose(chemin, source).count { it.severite == Severite.ERREUR })
            environnement.remplacerContexteModule(":app", listOf(racine(avecY = true)))
            val debut2 = System.nanoTime()
            assertTrue(environnement.attendreReconstructions(), "reconstruction réelle 2")
            dureeReelle2Ms = (System.nanoTime() - debut2) / 1_000_000
        }

        // --- Nominal : la frappe sur l'époque vivante (mémo chaud).
        val moteur2 = environnement.moteur(":app")
        moteur2.diagnose(chemin, source) // chauffe du mémo
        val nominales = ArrayList<Long>(64)
        repeat(48) {
            val debut = System.nanoTime()
            moteur2.diagnose(chemin, source)
            nominales.add((System.nanoTime() - debut) / 1_000) // µs
        }
        val (nominalP50, nominalP95) = percentiles(nominales)

        // --- Fenêtre : reconstruction TENUE OUVERTE (loquet) ; la rafale
        //     ne démarre qu'une fois la reconstruction SOUMISE — TOUTE la
        //     rafale vit donc dans la fenêtre, servie par l'ancien moteur.
        val liberer = CountDownLatch(1)
        environnement.rechauffeur = { liberer.await(30, TimeUnit.SECONDS) }
        environnement.remplacerContexteModule(":app", listOf(racine(avecY = true)))
        val echeanceSoumission = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10_000)
        while (environnement.reconstructionsEnCours() == 0) {
            assertTrue(System.nanoTime() < echeanceSoumission, "la reconstruction est soumise")
            Thread.sleep(2)
        }
        val latencesFenetre = ArrayList<Long>(64)
        val rafale = Thread {
            repeat(48) {
                val debut = System.nanoTime()
                environnement.calculerFraiche(chemin) { m -> m.diagnose(chemin, source) }
                latencesFenetre.add((System.nanoTime() - debut) / 1_000) // µs
            }
        }
        rafale.start()

        // Assertion STRUCTURELLE (anti-figage) : la rafale se solde
        // TOUTE ENTIERE pendant que le réchauffeur tient le fil —
        // avant V-F1, chaque requête aurait attendu la reconstruction.
        rafale.join(20_000)
        assertTrue(!rafale.isAlive, "0 figage : la rafale entière est servie pendant la reconstruction tenue")
        assertEquals(48, latencesFenetre.size, "48 requêtes servies DANS la fenêtre")
        liberer.countDown()
        assertTrue(environnement.attendreReconstructions(), "drainage final")
        val (fenetreP50, fenetreP95) = percentiles(latencesFenetre)

        // --- Rejeu : une requête coupée par le swap compte DEUX passes,
        //     seule la neuve livre.
        val passees = AtomicInteger(0)
        val demarree = CountDownLatch(1)
        val libererCoupee = CountDownLatch(1)
        val executeur = java.util.concurrent.Executors.newSingleThreadExecutor()
        val future = executeur.submit<Int> {
            environnement.calculerFraiche(chemin) { m ->
                val numero = passees.incrementAndGet()
                if (numero == 1) {
                    demarree.countDown()
                    libererCoupee.await(20, TimeUnit.SECONDS)
                }
                m.diagnose(chemin, source).count { it.severite == Severite.ERREUR }
            }
        }
        demarree.await(10, TimeUnit.SECONDS)
        environnement.remplacerContexteModule(":app", listOf(racine(avecY = true)))
        assertTrue(environnement.attendreReconstructions(), "swap pendant la requête coupée")
        libererCoupee.countDown()
        val erreursLivrees = future.get(20, TimeUnit.SECONDS)
        executeur.shutdown()

        val ratioP50 = if (nominalP50 == 0L) "n/a" else "%.2f".format(fenetreP50.toDouble() / nominalP50)
        val ratioP95 = if (nominalP95 == 0L) "n/a" else "%.2f".format(fenetreP95.toDouble() / nominalP95)
        println("[BANC V-F1] reconstruction réelle (jrt runtime, 2 mesures) : $dureeReelleMs ms / $dureeReelle2Ms ms")
        println("[BANC V-F1] frappe nominale (48 requêtes, mémo chaud) : p50 ${nominalP50} µs, p95 ${nominalP95} µs")
        println(
            "[BANC V-F1] frappe PENDANT reconstruction tenue (48 requêtes) : p50 ${fenetreP50} µs (×$ratioP50), " +
                "p95 ${fenetreP95} µs (×$ratioP95) — budget ADR : p95 ≤ 2× nominal",
        )
        println("[BANC V-F1] requête coupée par le swap : ${passees.get()} passes, livrée avec $erreursLivrees erreur(s) (0 attendu — rejeu sur l'époque neuve)")

        assertEquals(0, erreursLivrees, "la requête coupée livre le verdict de l'époque NEUVE")
        assertEquals(2, passees.get(), "exactement deux passes : solde de l'ancienne, livraison de la neuve")
    }
}

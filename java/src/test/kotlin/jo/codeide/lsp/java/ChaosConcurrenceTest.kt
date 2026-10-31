package jo.codeide.lsp.java

import jo.codeide.lsp.java.concurrence.EpoqueRetiree
import jo.codeide.lsp.java.diagnostics.Severite
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vague V-F2 — CHAOS TEST multi-fils (ADR 0029 phase 2 / ADR 0010) :
 * entrelacements délibérés de TOUTES les familles d'opérations sur un
 * environnement à DEUX modules, avec syncs (double-tampon + époques),
 * rafraîchissements XML, invalidations et libérations.
 *
 * Critères (ADR 0029 « Tests de non-régression ») :
 *  - **zéro interblocement** : chaque fil termine endéans la borne (un
 *    dépassement VIDE les piles de TOUS les fils dans le rapport avant
 *    l'échec — dump de diagnostic) ;
 *  - **zéro exception non attendue** : seules les [EpoqueRetiree] sont
 *    tolérées (coupure honnête d'un moteur retiré par un swap — le
 *    protocole d'époque les rejoue) ;
 *  - **le verdict survit au chaos** (fiche 012) : après la tempête et la
 *    drainage des reconstructions, MAIN_OK diagnostique 0 erreur et
 *    MAIN_ERR au moins une — quel que soit l'entrelacement subi.
 */
class ChaosConcurrenceTest {

    private val sourceOk = "package demo;\npublic class Main {\n    public void go() {\n        int x = 1 + 2;\n    }\n}\n"
    private val sourceErr = "package demo;\npublic class Main {\n    public void go() {\n        Inconnu i = null;\n    }\n}\n"

    private fun racine(nom: String, avecInconnu: Boolean): Path {
        val racine = Files.createTempDirectory("vf2-chaos-$nom")
        val demo = Files.createDirectories(racine.resolve("demo"))
        demo.resolve("Main.java").writeText(sourceOk)
        demo.resolve("Aide.java").writeText("package demo;\npublic class Aide { public void aider() { } }\n")
        if (avecInconnu) {
            demo.resolve("Extra.java").writeText("package demo;\npublic class Extra { }\n")
        }
        return racine
    }

    @Test
    fun `chaos multi-fils sans interblocage ni exception non attendue`() {
        val environnement = EnvironnementCompilation()
        environnement.routeurFichierModule = { chemin ->
            if (chemin.startsWith("/b/")) ":b" else ":a"
        }
        environnement.remplacerContexteModule(":a", listOf(racine("a", avecInconnu = false)))
        environnement.remplacerContexteModule(":b", listOf(racine("b", avecInconnu = true)))
        val moteurA = environnement.moteur(":a")
        // Chauffe initiale : environnement + mémo (régime plein, jrt runtime).
        // Les chemins logiques portent le paquet « demo » (paquet = dossier
        // du chemin logique — le routeur distingue les modules par préfixe).
        assertTrue(
            moteurA.diagnose("/a/demo/Main.java", sourceOk).none { it.severite == Severite.ERREUR },
            "fixture :a saine",
        )
        assertTrue(
            environnement.moteur(":b").diagnose("/b/demo/Main.java", sourceOk).none { it.severite == Severite.ERREUR },
            "fixture :b saine",
        )

        val iterations = 90
        val fils = 6
        val attendu = CountDownLatch(fils)
        val exceptions = CopyOnWriteArrayList<Throwable>()
        val coupuresEpoque = AtomicInteger(0)
        val xml = Files.createTempFile("vf2-chaos", ".xml")

        for (fil in 0 until fils) {
            Thread({
                try {
                    repeat(iterations) { i ->
                        try {
                            when ((fil * 7 + i) % 9) {
                                0 -> { // Diagnose alternée (travail réel, mémo impossible).
                                    val source = if (i % 2 == 0) sourceOk.replace("1 + 2", "1 + $i") else sourceErr
                                    environnement.calculerFraiche("/a/demo/Main.java") { it.diagnose("/a/demo/Main.java", source) }
                                }
                                1 -> environnement.calculerFraiche("/a/demo/Main.java") { it.hoverAt("/a/demo/Main.java", sourceOk, 30) }
                                2 -> environnement.calculerFraiche("/a/demo/Main.java") {
                                    it.completer("/a/demo/Main.java", sourceOk, sourceOk.indexOf("int x"))
                                }
                                3 -> environnement.calculerFraiche("/a/demo/Main.java") {
                                    it.references("/a/demo/Main.java", sourceOk, sourceOk.indexOf("go"), true)
                                }
                                4 -> if (i % 5 == 0) {
                                    // Sync : remplacement effectif → époque + double-tampon.
                                    environnement.remplacerContexteModule(":a", listOf(racine("a2-$fil$i", avecInconnu = true)))
                                } else {
                                    environnement.calculerFraiche("/b/demo/Main.java") { it.diagnose("/b/demo/Main.java", sourceOk) }
                                }
                                5 -> environnement.moteurPourFichier("/a/demo/Main.java").rafraichirFichierXml(xml)
                                6 -> {
                                    val moteur = environnement.moteurPourFichier("/a/demo/Main.java")
                                    moteur.invalidateParseCache("/a/demo/Main.java")
                                    moteur.invalidateSourcePresence()
                                }
                                7 -> environnement.calculerFraiche("/a/demo/Main.java") {
                                    it.surbrillances("/a/demo/Main.java", sourceOk, sourceOk.indexOf("go"))
                                }
                                else -> environnement.calculerFraiche("/a/demo/Main.java") {
                                    it.chainesSelection("/a/demo/Main.java", sourceOk, listOf(sourceOk.indexOf("go")))
                                }
                            }
                        } catch (e: EpoqueRetiree) {
                            // Coupure honnête : le moteur servi a été retiré par
                            // un swap pendant le calcul — tolérée (rejouée par le
                            // protocole en production ; le chaos l'observe brute).
                            coupuresEpoque.incrementAndGet()
                        }
                    }
                } catch (t: Throwable) {
                    exceptions.add(t)
                } finally {
                    attendu.countDown()
                }
            }, "vf2-chaos-$fil").apply { isDaemon = true }.start()
        }

        // Borne d'interblocement : 120 s (le chaos paie des résolutions réelles).
        val solde = attendu.await(120, TimeUnit.SECONDS)
        if (!solde) {
            // DUMP DE DIAGNOSTIC (ADR 0010 : « timeout + dump en échec »).
            System.err.println("=== CHAOS : fils non soldés — piles de TOUS les fils ===")
            for ((fil, pile) in Thread.getAllStackTraces()) {
                System.err.println("--- ${fil.name} (${fil.state}) ---")
                for (element in pile) {
                    System.err.println("    $element")
                }
            }
        }
        assertTrue(solde, "aucun interblocement : tous les fils se sont soldés en 120 s")

        // Drainage des reconstructions de double-tampon en vol.
        assertTrue(
            environnement.attendreReconstructions(60_000),
            "les reconstructions de double-tampon se sont drainées",
        )
        Thread.sleep(200) // lasse finir les retraits en vol

        assertEquals(0, exceptions.size, "zéro exception non attendue (reçues : ${exceptions.take(3)})")
        println("Chaos : $fils fils × $iterations itérations ; coupures d'époque honnêtes observées : ${coupuresEpoque.get()}")

        // Le verdict survit (fiche 012) : sur l'époque courante, MAIN_OK est
        // propre et MAIN_ERR est fautif — quel que soit l'entrelacement subi.
        assertTrue(
            environnement.calculerFraiche("/a/demo/Main.java") { it.diagnose("/a/demo/Main.java", sourceOk) }
                .none { it.severite == Severite.ERREUR },
            "après le chaos, MAIN_OK diagnostique 0 erreur",
        )
        assertTrue(
            environnement.calculerFraiche("/a/demo/Main.java") { it.diagnose("/a/demo/Main.java", sourceErr) }
                .any { it.severite == Severite.ERREUR },
            "après le chaos, MAIN_ERR diagnostique au moins une erreur",
        )
        environnement.dispose()
    }
}

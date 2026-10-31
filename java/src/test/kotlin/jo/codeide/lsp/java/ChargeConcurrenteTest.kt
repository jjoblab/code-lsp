package jo.codeide.lsp.java

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import jo.codeide.lsp.java.diagnostics.Severite
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vague V-F2 — BANC DE CHARGE CONCURRENT (ADR 0010/0029, critère du test
 * de charge déterministe) : **complétion du fichier A + diagnose du
 * fichier B concurrents** — le p95 de chacun reste ≤ p95 séquentiel +
 * 10 % (marge absolue de 5 ms pour le bruit de CI).
 *
 * Le gain mesuré (écart G-029 « une complétion attend un diagnose d'un
 * autre fichier ») : la complétion CHAUDE (sonde à chaud sur l'arbre du
 * cache de parse + annuaire) ne prend PAS le verrou de compilation —
 * avant V-F2, elle sérialisait derrière le moniteur du moteur et son p95
 * concurrent valait p95(completion) + p95(diagnose).
 */
class ChargeConcurrenteTest {

    /** Le fichier A (complétion) : assez de membres pour un contexte MEMBRE. */
    private val sourceA = buildString {
        append("package demo;\n")
        append("public class A {\n")
        append("    private final String nom = \"a\";\n")
        for (i in 0 until 12) {
            append("    public int methode$i(int v) { return v + $i; }\n")
        }
        append("    public void go() {\n")
        append("        int total = 0;\n")
        append("        total = total + nom.length();\n")
        append("        // ancre\n")
        append("    }\n")
        append("}\n")
    }

    /** Deux variantes du fichier B (diagnose complet à chaque appel : le mémo ne peut jamais servir). */
    private fun sourceB(variante: Int): String = buildString {
        append("package demo;\n")
        append("public class B {\n")
        append("    public void travail() {\n")
        for (i in 0 until 30) {
            append("        int v$i = $i * ${variante + 1};\n")
        }
        append("        String s = \"x\";\n")
        append("    }\n")
        append("}\n")
    }

    private fun racine(): Path {
        val racine = Files.createTempDirectory("vf2-charge")
        Files.createDirectories(racine.resolve("demo")).apply {
            resolve("A.java").writeText(sourceA)
            resolve("B.java").writeText(sourceB(0))
        }
        return racine
    }

    /** Le p95 d'une liste de durées (ms), majoré par excès. */
    private fun p95(durees: List<Long>): Long {
        val triees = durees.sorted()
        val indice = ((triees.size * 95 + 99) / 100).coerceAtMost(triees.size - 1)
        return triees[indice]
    }

    @Test
    fun `completion fichier A et diagnose fichier B concurrents sans degradation de p95`() {
        val moteur = MoteurJava(racines = listOf(racine()))
        val cheminA = "/demo/A.java"
        val cheminB = "/demo/B.java"
        val ancre = sourceA.indexOf("// ancre")

        // Chauffe : environnement + cache de parse de A (le chemin chaud de
        // la complétion vit sur l'arbre du cache) + un premier diagnose de B.
        // (Assertions sur les ERREURS : les locales de travail des fixtures
        // portent des avertissements « unused local » — le tableau analytique.)
        assertTrue(
            moteur.diagnose(cheminA, sourceA).none { it.severite == Severite.ERREUR },
            "fixture A saine",
        )
        assertTrue(
            moteur.diagnose(cheminB, sourceB(0)).none { it.severite == Severite.ERREUR },
            "fixture B saine",
        )
        repeat(10) {
            assertTrue(moteur.completer(cheminA, sourceA, ancre).items.isNotEmpty(), "la complétion de A travaille")
        }

        val iterations = 40

        // Phase SÉQUENTIELLE (base de comparaison) : les mêmes charges, un
        // seul fil, alternées comme le pire cas du régime interactif.
        val seqCompletion = mutableListOf<Long>()
        val seqDiagnose = mutableListOf<Long>()
        repeat(iterations) { i ->
            var debut = System.nanoTime()
            moteur.completer(cheminA, sourceA, ancre)
            seqCompletion.add((System.nanoTime() - debut) / 1_000_000)
            debut = System.nanoTime()
            moteur.diagnose(cheminB, sourceB(i % 2))
            seqDiagnose.add((System.nanoTime() - debut) / 1_000_000)
        }

        // Phase CONCURRENTE : deux fils, la complétion de A en rafale PENDANT
        // les diagnoses complets de B — l'entrelacement de l'écart G-029.
        val concCompletion = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val concDiagnose = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val pret = CountDownLatch(2)
        val fini = CountDownLatch(2)
        val erreurs = AtomicInteger(0)
        Thread {
            pret.countDown(); pret.await(5, TimeUnit.SECONDS)
            repeat(iterations) {
                val debut = System.nanoTime()
                try {
                    moteur.completer(cheminA, sourceA, ancre)
                } catch (_: Throwable) {
                    erreurs.incrementAndGet()
                }
                concCompletion.add((System.nanoTime() - debut) / 1_000_000)
            }
            fini.countDown()
        }.apply { isDaemon = true }.start()
        Thread {
            pret.countDown(); pret.await(5, TimeUnit.SECONDS)
            repeat(iterations) { i ->
                val debut = System.nanoTime()
                try {
                    moteur.diagnose(cheminB, sourceB(i % 2))
                } catch (_: Throwable) {
                    erreurs.incrementAndGet()
                }
                concDiagnose.add((System.nanoTime() - debut) / 1_000_000)
            }
            fini.countDown()
        }.apply { isDaemon = true }.start()
        assertTrue(fini.await(120, TimeUnit.SECONDS), "aucun interblocement au banc de charge")
        assertEquals(0, erreurs.get(), "zéro erreur sous charge concurrente")

        val p95SeqCompletion = p95(seqCompletion)
        val p95ConcCompletion = p95(concCompletion)
        val p95SeqDiagnose = p95(seqDiagnose)
        val p95ConcDiagnose = p95(concDiagnose)
        println(
            "Banc de charge V-F2 : complétion A p95 séquentiel ${p95SeqCompletion} ms / concurrent $p95ConcCompletion ms ; " +
                "diagnose B p95 séquentiel $p95SeqDiagnose ms / concurrent $p95ConcDiagnose ms",
        )

        // Critère ADR 0010 : p95 de chacun ≤ p95 séquentiel + 10 % (marge
        // absolue 5 ms pour le bruit de machine). L'ANCIEN régime (moniteur
        // d'instance) échouait ici : p95 concurrent de la complétion ≈
        // p95(completion) + p95(diagnose).
        assertTrue(
            p95ConcCompletion <= p95SeqCompletion * 1.10 + 5,
            "complétion A : p95 concurrent $p95ConcCompletion ms > séquentiel $p95SeqCompletion ms + 10 % + 5 ms",
        )
        assertTrue(
            p95ConcDiagnose <= p95SeqDiagnose * 1.10 + 5,
            "diagnose B : p95 concurrent $p95ConcDiagnose ms > séquentiel $p95SeqDiagnose ms + 10 % + 5 ms",
        )
    }
}

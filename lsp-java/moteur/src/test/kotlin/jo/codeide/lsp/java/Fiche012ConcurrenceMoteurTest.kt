package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 012 — EcjCompilerEngineConcurrencyTest : concurrence du moteur
 * in-process — mix concurrent diagnostics + resolveUnit + hover sur un
 * moteur PARTAGÉ (cohérence des résultats, aucune exception), et stress de
 * resolveUnit sur deux sources à clés de cache distinctes.
 */
class Fiche012ConcurrenceMoteurTest {

    @Test
    fun concurrentMixOfOperationsDoesNotCorruptCache() {
        val moteur = MoteurJava()
        val depart = CountDownLatch(1)
        val premiereDefaillance = AtomicReference<Throwable?>(null)
        val erreursCache = AtomicInteger(0)
        val executur = Executors.newFixedThreadPool(8)
        val futures = (0 until 8).map { thread ->
            executur.submit {
                depart.await()
                repeat(12) { iteration ->
                    try {
                        val source = if ((thread + iteration) % 2 == 0) CorpusCommun.MAIN_OK else CorpusCommun.MAIN_ERR
                        val diagnostics = moteur.diagnose("/demo/Main.java", source)
                        val nbErreurs = diagnostics.count { it.severite == Severite.ERREUR }
                        if (source == CorpusCommun.MAIN_OK && nbErreurs != 0) {
                            erreursCache.incrementAndGet()
                            throw AssertionError("MAIN_OK doit rendre 0 erreur — le cache a rendu le résultat d'une autre frappe")
                        }
                        if (source == CorpusCommun.MAIN_ERR && nbErreurs < 1) {
                            erreursCache.incrementAndGet()
                            throw AssertionError("MAIN_ERR doit rendre au moins 1 erreur")
                        }
                        assertNotNull(moteur.resolveUnit("/demo/Main.java", source, false), "resolveUnit jamais null")
                        moteur.hoverAt("/demo/Main.java", source, 60) // ne doit pas crasher
                    } catch (t: Throwable) {
                        premiereDefaillance.compareAndSet(null, t)
                    }
                }
            }
        }
        depart.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executur.shutdown()
        assertTrue(executur.awaitTermination(5, TimeUnit.SECONDS), "le pool se termine proprement")
        assertEquals(null, premiereDefaillance.get(), "0 exception levée : ${premiereDefaillance.get()}")
        assertEquals(0, erreursCache.get(), "cohérence du cache sous 8 threads")
    }

    @Test
    fun twoThreadsResolveDifferentSourcesConcurrentlyWithoutCrash() {
        val moteur = MoteurJava()
        val sourceA = """
            public class A {
                public static void main(String[] args) {
                    String message = "Hello, " + args.length;
                }
            }
        """.trimIndent()
        val sourceB = """
            public class B {
                public static void main(String[] args) {
                    String s = 42;
                }
            }
        """.trimIndent()
        val nonNullA = AtomicInteger(0)
        val nonNullB = AtomicInteger(0)
        val executur = Executors.newFixedThreadPool(2)
        val futures = listOf(
            executur.submit {
                repeat(20) {
                    if (moteur.resolveUnit("/demo/A.java", sourceA, false) != null) nonNullA.incrementAndGet()
                }
            },
            executur.submit {
                repeat(20) {
                    if (moteur.resolveUnit("/demo/B.java", sourceB, false) != null) nonNullB.incrementAndGet()
                }
            },
        )
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executur.shutdown()
        assertTrue(executur.awaitTermination(5, TimeUnit.SECONDS), "le pool se termine proprement")
        assertTrue(nonNullA.get() > 0, "le thread A obtient au moins une résolution non nulle")
        assertTrue(nonNullB.get() > 0, "le thread B obtient au moins une résolution non nulle")
    }
}

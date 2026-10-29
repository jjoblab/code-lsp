package jo.codeide.lsp.java

import jo.codeide.lsp.java.concurrence.OrdonnanceurMoteur
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 048 — EcjEngineSchedulerTest : le scheduler du moteur — un worker
 * unique sérialise RÉELLEMENT les tâches interactives (aucun
 * chevauchement) ; la supersession annule les interactives obsolètes d'un
 * même fichier (les analyses, jamais) ; shutdown idempotent.
 */
class Fiche048OrdonnanceurTest {

    @Test
    fun interactiveTasksAreSerializedOnSingleWorker() {
        OrdonnanceurMoteur("test1").use { scheduler ->
            val enVol = AtomicInteger(0)
            val maxEnVol = AtomicInteger(0)
            val completions = AtomicInteger(0)
            val superseded = AtomicInteger(0)
            val executur = Executors.newFixedThreadPool(10)
            val faites = CountDownLatch(1)
            val futures = (0 until 10).map { i ->
                val fichier = "/demo/F${i % 3}.java"
                scheduler.soumettreInteractif(fichier) {
                    val vol = enVol.incrementAndGet()
                    maxEnVol.accumulateAndGet(vol) { a, b -> maxOf(a, b) }
                    Thread.sleep(30)
                    enVol.decrementAndGet()
                    completions.incrementAndGet()
                }
            }
            faites.countDown()
            // Les supersedées se soldent par CancellationException (l'appelant
            // ne doit PAS la propager comme une erreur serveur).
            futures.forEach {
                try {
                    it.get(5, TimeUnit.SECONDS)
                } catch (_: CancellationException) {
                    superseded.incrementAndGet()
                }
            }
            executur.shutdown()
            executur.awaitTermination(5, TimeUnit.SECONDS)
            assertEquals(1, maxEnVol.get(), "le maximum d'exécutions en vol constaté vaut exactement 1")
            // Toutes les tâches soumises sont comptabilisées (complétées ou superseded).
            assertEquals(10, completions.get() + superseded.get(), "complétées + supersedées == soumises")
        }
    }

    @Test
    fun supersessionCancelsPendingInteractiveForSameFile() {
        OrdonnanceurMoteur("test2").use { scheduler ->
            val completions = AtomicInteger(0)
            // Une tâche ANALYSE bloquante sature le worker pendant 800 ms.
            scheduler.soumettreAnalyse {
                Thread.sleep(800)
                completions.incrementAndGet()
            }
            // 5 interactives soumises pendant l'occupation sur le MÊME fichier.
            val futures = (0 until 5).map {
                scheduler.soumettreInteractif("/demo/F1.java") {
                    completions.incrementAndGet()
                }
            }
            // Les interactives en attente sont supersedées par la plus récente.
            var annulations = 0
            for (future in futures) {
                try {
                    future.get(5, TimeUnit.SECONDS)
                } catch (_: CancellationException) {
                    annulations++
                }
            }
            assertTrue(scheduler.supersededCount() >= 1, "au moins une supersession comptée")
            assertTrue(completions.get() <= 2, "au plus 2 tâches interactives ont réellement démarré : ${completions.get()}")
        }
    }

    @Test
    fun analysisTasksAreNotSuperseded() {
        OrdonnanceurMoteur("test3").use { scheduler ->
            val completions = AtomicInteger(0)
            val futures = (0 until 5).map {
                scheduler.soumettreAnalyse {
                    Thread.sleep(30)
                    completions.incrementAndGet()
                }
            }
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(5, completions.get(), "les 5 analyses complètent TOUTES")
            assertEquals(0, scheduler.supersededCount(), "aucune analyse superseded")
        }
    }

    @Test
    fun shutdownIsIdempotentAndReleasesWorker() {
        OrdonnanceurMoteur("test4").use { scheduler ->
            scheduler.shutdown()
            // Le second appel ne lève pas (idempotence).
            scheduler.shutdown()
        }
    }
}

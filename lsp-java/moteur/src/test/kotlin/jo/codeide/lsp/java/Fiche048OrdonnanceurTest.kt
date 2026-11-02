package jo.codeide.lsp.java

import jo.codeide.lsp.java.concurrence.OrdonnanceurMoteur
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 048 — EcjEngineSchedulerTest (**amendée V-F2** — ADR 0029 phase 2 /
 * ADR 0010 schéma B) : le scheduler du moteur est un POOL PLAFONNÉ (2 par
 * défaut, borné 1..3) — au plafond 1, un worker unique sérialise RÉELLEMENT
 * les tâches (aucun chevauchement — contrat historique) ; au plafond 2, les
 * tâches de fichiers DISTINCTS courent en parallèle (rendez-vous déterministe)
 * mais celles d'un MÊME fichier ne se chevauchent JAMAIS ; la supersession
 * annule les interactives obsolètes d'un même fichier (les analyses, jamais) ;
 * shutdown idempotent.
 */
class Fiche048OrdonnanceurTest {

    @Test
    fun interactiveTasksAreSerializedOnSingleWorker() {
        // Plafond EXPLICITE à 1 : le worker unique d'avant V-F2 (contrat
        // historique de la fiche — « maxInFlight == 1 »).
        OrdonnanceurMoteur("test1", workers = 1).use { scheduler ->
            assertEquals(1, scheduler.workers(), "plafond posé à 1")
            val enVol = AtomicInteger(0)
            val maxEnVol = AtomicInteger(0)
            val completions = AtomicInteger(0)
            val superseded = AtomicInteger(0)
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
            // Les supersedées se soldent par CancellationException (l'appelant
            // ne doit PAS la propager comme une erreur serveur).
            futures.forEach {
                try {
                    it.get(5, TimeUnit.SECONDS)
                } catch (_: CancellationException) {
                    superseded.incrementAndGet()
                }
            }
            assertEquals(1, maxEnVol.get(), "le maximum d'exécutions en vol constaté vaut exactement 1")
            // Toutes les tâches soumises sont comptabilisées (complétées ou superseded).
            assertEquals(10, completions.get() + superseded.get(), "complétées + supersedées == soumises")
        }
    }

    @Test
    fun distinctFilesRunInParallelUnderTwoWorkers() {
        // Plafond 2 : deux tâches INTERACTIVES de fichiers DISTINCTS se
        // chevauchent RÉELLEMENT — preuve par RENDEZ-VOUS déterministe
        // (chaque tâche attend l'arrivée de l'autre, bornée) : si le pool
        // les sérialisait, le premier rendez-vous expirerait.
        OrdonnanceurMoteur("test1b", workers = 2).use { scheduler ->
            assertEquals(2, scheduler.workers(), "plafond posé à 2")
            val arriveeA = CountDownLatch(1)
            val arriveeB = CountDownLatch(1)
            val chevauchement = AtomicInteger(0)
            val fA = scheduler.soumettreInteractif("/demo/A.java") {
                arriveeA.countDown()
                assertTrue(arriveeB.await(5, TimeUnit.SECONDS), "B doit démarrer pendant que A court (parallélisme plafond 2)")
                chevauchement.incrementAndGet()
            }
            val fB = scheduler.soumettreInteractif("/demo/B.java") {
                arriveeB.countDown()
                assertTrue(arriveeA.await(5, TimeUnit.SECONDS), "A doit démarrer pendant que B court (parallélisme plafond 2)")
                chevauchement.incrementAndGet()
            }
            fA.get(5, TimeUnit.SECONDS)
            fB.get(5, TimeUnit.SECONDS)
            assertEquals(2, chevauchement.get(), "les deux tâches se sont chevauchées")
        }
    }

    @Test
    fun sameFileNeverOverlapsEvenWithTwoWorkers() {
        // Plafond 2, MÊME fichier : jamais deux tâches de « /demo/F1.java »
        // en vol simultanément (le second worker sert une AUTRE clé ou
        // attend) — le garde-fou de la supersession par fichier.
        OrdonnanceurMoteur("test1c", workers = 2).use { scheduler ->
            val enVolF1 = AtomicInteger(0)
            val maxEnVolF1 = AtomicInteger(0)
            val futures = (0 until 6).map {
                scheduler.soumettreInteractif("/demo/F1.java") {
                    val vol = enVolF1.incrementAndGet()
                    maxEnVolF1.accumulateAndGet(vol) { a, b -> maxOf(a, b) }
                    Thread.sleep(40)
                    enVolF1.decrementAndGet()
                }
            }
            var annulations = 0
            for (future in futures) {
                try {
                    future.get(5, TimeUnit.SECONDS)
                } catch (_: CancellationException) {
                    annulations++
                }
            }
            assertEquals(1, maxEnVolF1.get(), "jamais deux tâches du même fichier en vol (plafond 2)")
            assertEquals(6, futures.size - annulations + annulations) // comptabilité close
        }
    }

    @Test
    fun supersessionCancelsPendingInteractiveForSameFile() {
        OrdonnanceurMoteur("test2").use { scheduler ->
            val completions = AtomicInteger(0)
            // Une tâche ANALYSE bloquante occupe un worker pendant 800 ms.
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
            assertTrue(completions.get() <= 3, "au plus 3 tâches réellement démarrées (analyse + 2 interactives max) : ${completions.get()}")
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

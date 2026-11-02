package jo.codeide.lsp.java

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import jo.codeide.lsp.java.concurrence.OrdonnanceurMoteur
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V-H — BRANCHEMENT DU PROTOCOLE (ADR 0010 : « le scheduler 3 lanes est
 * conservé ») : la soumission PRIORISÉE typée
 * ([OrdonnanceurMoteur.soumettrePrioritaire]) qui porte désormais la
 * couche protocole. La tempête d'appareil mesurée
 * (`codeide-logs-2026-11-01-221628.zip`, moto g06, CodeIDE 0.205.2) :
 * complétion servie en 29–87 s derrière la file FIFO unique du fil de
 * lecture lsp4j, timeout client 5 s — l'utilisateur ne voyait RIEN.
 *
 * Ce verrou éprouve les SÉMANTIQUES du répartiteur (la couche protocole
 * les consomme telles quelles) :
 *
 *  1. la voie FRAPPE (0) DEVANCE la file de confort en attente ;
 *  2. la supersession SOLDE PAR LA VALEUR DE REMPLACEMENT (réponse vide
 *     honnête — jamais d'annulation : le client attend une réponse pour
 *     l'identifiant de sa requête) ;
 *  3. une soumission de FRAPPE rend caduques les tâches EN ATTENTE des
 *     voies inférieures du même fichier ;
 *  4. jamais deux tâches du MÊME fichier en vol, toutes voies confondues ;
 *  5. l'arrêt solde les en attente par le remplacement.
 */
class RepartitionPrioriteeVhTest {

    @Test
    fun `la voie superieure devance la file de confort en attente`() {
        OrdonnanceurMoteur("vh1", workers = 1).use { scheduler ->
            // Une tâche DURABLE (sans clé de fichier — jamais supersedée)
            // occupe le worker unique : la file s'accumule derrière elle,
            // comme derrière le premier inlay de la tempête d'appareil.
            val occupation = CountDownLatch(1)
            val ordre = java.util.Collections.synchronizedList(ArrayList<String>())
            val bloqueuse = scheduler.soumettrePrioritaire(null, 2, {
                occupation.await(10, TimeUnit.SECONDS)
                Unit
            }, { Unit })
            val avenir = ArrayList<CompletableFuture<String>>()
            for (i in 1..5) {
                avenir.add(
                    scheduler.soumettrePrioritaire("/demo/F.java", 2, {
                        ordre.add("confort-$i")
                        "confort-$i"
                    }, { "vide" }),
                )
            }
            // La requête de NAVIGATION arrive en DERNIÈRE : elle ne
            // supersede RIEN (seule la FRAPPE le fait) — elle DEVANCE la
            // file de confort par sa voie.
            val navigation = scheduler.soumettrePrioritaire("/demo/F.java", 1, {
                ordre.add("NAVIGATION")
                "navigation"
            }, { "vide" })
            occupation.countDown()
            assertEquals("navigation", navigation.get(10, TimeUnit.SECONDS))
            // Toutes les tâches se soldent AVANT de lire l'ordre.
            avenir.forEach { it.get(10, TimeUnit.SECONDS) }
            assertTrue(
                ordre.indexOf("NAVIGATION") in 0..(ordre.indexOf("confort-5") - 1),
                "la navigation doit devancer la file de confort : $ordre",
            )
        }
    }

    @Test
    fun `la supersession solde par la valeur de remplacement sans courir`() {
        OrdonnanceurMoteur("vh2", workers = 1).use { scheduler ->
            val occupation = CountDownLatch(1)
            val demarree = CountDownLatch(1)
            val courues = AtomicInteger(0)
            // Le worker est occupé ET DÉMARRÉ (rendez-vous) : les deux
            // soumissions patientent — DÉTERMINISTE.
            scheduler.soumettrePrioritaire("/demo/A.java", 1, {
                demarree.countDown()
                occupation.await(10, TimeUnit.SECONDS)
                Unit
            }, { Unit })
            assertTrue(demarree.await(10, TimeUnit.SECONDS), "la bloquante doit démarrer")
            val ancienne = scheduler.soumettrePrioritaire("/demo/A.java", 2, {
                courues.incrementAndGet()
                listOf("resultat-ancien")
            }, { emptyList<String>() })
            // La plus récente MÊME VOIE remplace l'ancienne.
            val recente = scheduler.soumettrePrioritaire("/demo/A.java", 2, {
                courues.incrementAndGet()
                listOf("resultat-recent")
            }, { emptyList() })
            occupation.countDown()
            // L'ANCIENNE se solde par le REMPLACEMENT (la réponse vide
            // honnête) — JAMAIS par une annulation, JAMAIS par son calcul.
            assertEquals(emptyList<String>(), ancienne.get(10, TimeUnit.SECONDS))
            assertEquals(listOf("resultat-recent"), recente.get(10, TimeUnit.SECONDS))
            assertEquals(1, courues.get(), "seule la plus récente a couru")
        }
    }

    @Test
    fun `la frappe rend caduques les voies inferieures en attente`() {
        OrdonnanceurMoteur("vh3", workers = 1).use { scheduler ->
            val occupation = CountDownLatch(1)
            val demarree = CountDownLatch(1)
            val courues = AtomicInteger(0)
            scheduler.soumettrePrioritaire("/demo/B.java", 1, {
                demarree.countDown()
                occupation.await(10, TimeUnit.SECONDS)
                Unit
            }, { Unit })
            assertTrue(demarree.await(10, TimeUnit.SECONDS), "la bloquante doit démarrer")
            // Navigation et confort patientent pour le même fichier.
            val navigation = scheduler.soumettrePrioritaire("/demo/B.java", 1, {
                courues.incrementAndGet()
                "navigation"
            }, { "vide-nav" })
            val confort = scheduler.soumettrePrioritaire("/demo/B.java", 2, {
                courues.incrementAndGet()
                "confort"
            }, { "vide-confort" })
            // LA FRAPPE : le texte a bougé — leurs coordonnées sont périmées.
            val frappe = scheduler.soumettrePrioritaire("/demo/B.java", 0, {
                courues.incrementAndGet()
                "frappe"
            }, { "vide-frappe" })
            occupation.countDown()
            // Les voies inférieures EN ATTENTE se soldent par leur
            // remplacement SANS courir ; la frappe seule travaille.
            assertEquals("vide-nav", navigation.get(10, TimeUnit.SECONDS))
            assertEquals("vide-confort", confort.get(10, TimeUnit.SECONDS))
            assertEquals("frappe", frappe.get(10, TimeUnit.SECONDS))
            assertEquals(1, courues.get(), "seule la frappe a couru")
        }
    }

    @Test
    fun `jamais deux taches du meme fichier en vol toutes voies confondues`() {
        OrdonnanceurMoteur("vh4", workers = 2).use { scheduler ->
            val enVol = AtomicInteger(0)
            val maxEnVol = AtomicInteger(0)
            val futures = ArrayList<CompletableFuture<String>>()
            // Deux voies DISTINCTES, le MÊME fichier : l'exclusion par
            // fichier domine la priorité (contrat V-F2 étendu aux voies).
            for (i in 0 until 8) {
                futures.add(
                    scheduler.soumettrePrioritaire("/demo/C.java", i % 3, {
                        val vol = enVol.incrementAndGet()
                        maxEnVol.accumulateAndGet(vol) { a, b -> maxOf(a, b) }
                        Thread.sleep(20)
                        enVol.decrementAndGet()
                        "tache-$i"
                    }, { "vide" }),
                )
            }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, maxEnVol.get(), "jamais deux tâches du même fichier en vol")
        }
    }

    @Test
    fun `l arret solde les taches en attente par le remplacement`() {
        val scheduler = OrdonnanceurMoteur("vh5", workers = 1)
        val occupation = CountDownLatch(1)
        val demarree = CountDownLatch(1)
        val tenue = scheduler.soumettrePrioritaire("/demo/D.java", 2, {
            demarree.countDown()
            occupation.await(10, TimeUnit.SECONDS)
            Unit
        }, { Unit })
        assertTrue(demarree.await(10, TimeUnit.SECONDS), "la bloquante doit démarrer")
        val enAttente = scheduler.soumettrePrioritaire("/demo/D.java", 2, {
            "jamais-couru"
        }, { "solde-arret" })
        // Arrêt pendant l'attente : la tâche pendante se solde honnêtement.
        scheduler.shutdown()
        assertEquals("solde-arret", enAttente.get(10, TimeUnit.SECONDS))
        occupation.countDown()
        tenue.get(10, TimeUnit.SECONDS)
    }
}

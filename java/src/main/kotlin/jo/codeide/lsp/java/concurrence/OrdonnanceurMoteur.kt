package jo.codeide.lsp.java.concurrence

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Ordonnanceur du moteur (étage N1, cahier fiche 048 — **amendé V-F2**).
 *
 * **Pool plafonné (V-F2 — ADR 0029 phase 2, ADR 0010 schéma B « workers
 * plafonnés 2–3 »)** : l'ordonnanceur n'est plus un worker unique mais un
 * PETIT pool (2 par défaut, propriété
 * `jo.codeide.lsp.java.ordonnanceur.workers`, borné 1..3 — un appareil
 * monocœur peut revenir au comportement d'avant V-F2 avec `=1`). Deux
 * files traversent les mêmes workers :
 *
 *  - **tâches interactives** (complétion, hover, diagnostics de frappe) :
 *    soumises avec une clé de FICHIER ; la plus récente **supersède** les
 *    interactives encore en attente du même fichier — leur `Future` se
 *    solde par une `CancellationException` (l'appelant ne doit PAS la
 *    propager comme une erreur serveur) ;
 *  - **tâches d'analyse** (indexation, diagnostics de fond) : durables,
 *    jamais supersedées par une plus récente.
 *
 * **Contrat (fiche 048 amendée)** : les tâches interactives d'un MÊME
 * fichier ne se chevauchent JAMAIS (une clé en cours d'exécution bloque
 * la prise de la suivante — le worker la laisse en file et sert une autre
 * tâche) ; les tâches de fichiers DISTINCTS (et les analyses) PEUVENT
 * courir en parallèle, dans la limite du plafond de workers — le maximum
 * d'exécutions en vol est au plus [workers]. L'ancienne garantie
 * « exactement 1 en vol » reste obtenue en posant le plafond à 1.
 *
 * @param nom nom de l'ordonnanceur (préfixe des threads workers).
 * @param workers le plafond de workers (borné 1..3 ; défaut : propriété
 *   `jo.codeide.lsp.java.ordonnanceur.workers`, sinon 2).
 */
public class OrdonnanceurMoteur(
    nom: String,
    workers: Int = WORKERS_PAR_PROPRIETE,
) : AutoCloseable {

    /** Verrou de la file interne + condition de réveil des workers. */
    private val verrou = ReentrantLock()

    /** Condition de réveil (nouvelle tâche, clé libérée, arrêt). */
    private val aTravailler = verrou.newCondition()

    /** Interactives en attente par fichier : seule la plus récente survit. */
    private val interactivesEnAttente = HashMap<String, Tache>()

    /** File FIFO des tâches prêtes à courir (ordres de soumission respectés). */
    private val pretes = ArrayList<Tache>()

    /** Clés de fichiers dont une tâche EST en cours d'exécution (jamais deux). */
    private val clesEnCours = HashSet<String>()

    /** Compteur des supersessions effectives (surface observable, fiche 048). */
    private val supersessions = AtomicInteger(0)

    /** Compteur des complétions (tâches réellement exécutées jusqu'au bout). */
    private val completions = AtomicInteger(0)

    /** Première défaillance rapportée (pattern de diagnostic du cahier). */
    private val premiereDefaillance = AtomicReference<Throwable?>(null)

    /** Exécutions en vol (garde du contrat : max constaté == plafond). */
    private val enVol = AtomicInteger(0)

    /** Le plafond de workers effectif (borné 1..3). */
    private val plafond: Int = workers.coerceIn(1, 3)

    @Volatile
    private var arrete: Boolean = false

    /** Les workers (threads démons, démarrent à la naissance). */
    private val workersThreads: List<Thread> = (1..plafond).map { indice ->
        Thread({ boucleWorker() }, "ordonnanceur-$nom-$indice").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Soumet une tâche INTERACTIVE liée à un fichier.
     *
     * La soumission supersede immédiatement toute interactive du même
     * fichier **encore en attente** (sa future se solde par
     * `CancellationException`) ; la tâche en cours d'exécution sur un
     * worker n'est jamais interrompue — et la suivante du même fichier
     * attend qu'elle soit terminée (contrat « jamais deux du même fichier
     * en vol », fiche 048 amendée V-F2).
     *
     * @param fichier clé de supersession (chemin du document).
     * @param fournisseur le calcul à exécuter sur un worker.
     * @return la future de la tâche (complétée, supersedée ou en échec).
     */
    public fun soumettreInteractif(fichier: String, fournisseur: Callable<Unit>): Future<Unit> {
        val tache = Tache(fichier, fournisseur)
        verrou.withLock {
            if (arrete) {
                tache.future.cancel(false)
            } else {
                interactivesEnAttente.remove(fichier)?.let { ancienne ->
                    if (ancienne.superseder()) {
                        supersessions.incrementAndGet()
                    }
                }
                interactivesEnAttente[fichier] = tache
                pretes.add(tache)
                aTravailler.signalAll()
            }
        }
        return tache.future
    }

    /**
     * Soumet une tâche d'ANALYSE (durable, jamais supersedée).
     *
     * @param fournisseur le calcul à exécuter sur un worker.
     * @return la future de la tâche.
     */
    public fun soumettreAnalyse(fournisseur: Callable<Unit>): Future<Unit> {
        val tache = Tache(null, fournisseur)
        verrou.withLock {
            if (arrete) {
                tache.future.cancel(false)
            } else {
                pretes.add(tache)
                aTravailler.signalAll()
            }
        }
        return tache.future
    }

    /**
     * Le PLAFOND de workers de cet ordonnanceur (sonde — V-F2).
     *
     * @return le nombre de workers démarrés (1..3).
     */
    public fun workers(): Int = plafond

    /**
     * Nombre de supersessions effectives depuis la naissance.
     *
     * Surface observable exigée par le cahier (fiche 048) : à conserver
     * pour le banc de parité.
     */
    public fun supersededCount(): Int = supersessions.get()

    /**
     * Arrête l'ordonnanceur : idempotent (le second appel ne lève pas),
     * les workers se terminent, les tâches en attente sont annulées.
     */
    public fun shutdown() {
        verrou.withLock {
            if (arrete) return
            arrete = true
            for ((_, tache) in interactivesEnAttente) {
                tache.superseder()
            }
            interactivesEnAttente.clear()
            for (tache in pretes) {
                tache.superseder()
            }
            pretes.clear()
            aTravailler.signalAll()
        }
    }

    /** Alias de [shutdown] pour l'usage auto-fermable. */
    override fun close() {
        shutdown()
    }

    /**
     * Boucle d'un worker : prend la plus ancienne tâche PRENABLE (dont la
     * clé de fichier n'est pas déjà en cours sur un autre worker — jamais
     * deux tâches du même fichier en vol), l'exécute, recommence.
     */
    private fun boucleWorker() {
        while (true) {
            var tache: Tache? = null
            verrou.withLock {
                while (tache == null && !arrete) {
                    val prenable = pretes.withIndex().firstOrNull { (_, t) ->
                        t.cle == null || t.cle !in clesEnCours
                    }
                    if (prenable != null) {
                        val (indice, t) = prenable
                        pretes.removeAt(indice)
                        if (t.cle != null && interactivesEnAttente[t.cle] === t) {
                            interactivesEnAttente.remove(t.cle)
                        }
                        if (t.cle != null) {
                            clesEnCours.add(t.cle)
                        }
                        tache = t
                    } else {
                        aTravailler.await()
                    }
                }
            }
            val prise = tache ?: return // arrêté
            if (prise.estAnnulee()) {
                // Supersedée entre la prise et l'exécution : rien à courir,
                // mais la clé doit être libérée pour les suivantes.
                libererCle(prise)
                continue
            }
            enVol.incrementAndGet()
            try {
                prise.future.run()
                if (!prise.future.isCancelled) {
                    completions.incrementAndGet()
                }
            } finally {
                enVol.decrementAndGet()
                libererCle(prise)
            }
        }
    }

    /** Libère la clé de fichier d'une tâche terminée et réveille les workers. */
    private fun libererCle(tache: Tache) {
        if (tache.cle == null) return
        verrou.withLock {
            clesEnCours.remove(tache.cle)
            aTravailler.signalAll()
        }
    }

    /**
     * Représentation interne d'une tâche : future + clé de supersession
     * (null pour une analyse — jamais supersedée).
     */
    private inner class Tache(
        val cle: String?,
        fournisseur: Callable<Unit>,
    ) {
        val future: FutureTask<Unit> = FutureTask(
            Callable {
                try {
                    fournisseur.call()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    premiereDefaillance.compareAndSet(null, t)
                    throw t
                }
            },
        )

        fun estAnnulee(): Boolean = future.isCancelled

        /** Marque la tâche annulée ; vrai ssi l'annulation a pris (pas encore courue). */
        fun superseder(): Boolean = future.cancel(false)
    }

    public companion object {
        /**
         * Plafond lu à l'initialisation : propriété système
         * `jo.codeide.lsp.java.ordonnanceur.workers` (bornée 1..3), défaut 2
         * — le « workers plafonnés 2–3 » de l'ADR 0010 schéma B, exécuté par
         * V-F2. `1` rétablit le worker unique d'avant V-F2.
         */
        private val WORKERS_PAR_PROPRIETE: Int =
            System.getProperty("jo.codeide.lsp.java.ordonnanceur.workers")?.toIntOrNull()?.coerceIn(1, 3) ?: 2
    }
}

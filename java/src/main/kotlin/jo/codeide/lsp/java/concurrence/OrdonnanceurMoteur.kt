package jo.codeide.lsp.java.concurrence

import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Ordonnanceur du moteur (étage N1, cahier fiche 048).
 *
 * Un **worker unique** sérialise réellement les tâches soumises : le moteur
 * ecj et ses caches ne sont pas sûrs en exécution simultanée, et le contrat
 * du cahier exige qu'aucune paire de tâches ne se chevauche (le maximum
 * d'exécutions en vol constaté vaut exactement 1). Deux files distinctes
 * traversent ce même worker :
 *
 *  - **tâches interactives** (complétion, hover, diagnostics de frappe) :
 *    soumises avec une clé de FICHIER ; la plus récente **supersède** les
 *    interactives encore en attente du même fichier — leur `Future` se
 *    solde par une `CancellationException` (l'appelant ne doit PAS la
 *    propager comme une erreur serveur) ;
 *  - **tâches d'analyse** (indexation, diagnostics de fond) : durables,
 *    jamais supersedées par une plus récente.
 *
 * Sur un appareil monocœur, le worker unique est aussi le plafond de charge
 * raisonnable (ADR 0010, schéma B — workers plafonnés).
 *
 * @param nom nom de l'ordonnanceur (nom du thread worker).
 */
public class OrdonnanceurMoteur(
    nom: String,
) : AutoCloseable {

    /** Verrou de la file interne + condition de réveil du worker. */
    private val verrou = Object()

    /** Interactives en attente par fichier : seule la plus récente survit. */
    private val interactivesEnAttente = HashMap<String, Tache>()

    /** File FIFO des tâtes prêtes à courir (ordres de soumission respectés). */
    private val pretes = ArrayList<Tache>()

    /** Compteur des supersessions effectives (surface observable, fiche 048). */
    private val supersessions = AtomicInteger(0)

    /** Compteur des complétions (tâches réellement exécutées jusqu'au bout). */
    private val completions = AtomicInteger(0)

    /** Première défaillance rapportée (pattern de diagnostic du cahier). */
    private val premiereDefaillance = AtomicReference<Throwable?>(null)

    /** Exécutions en vol (garde du contrat : max constaté == 1). */
    private val enVol = AtomicInteger(0)

    @Volatile
    private var arrete: Boolean = false

    /** Le worker unique (thread démon, démarre à la naissance). */
    private val worker: Thread = Thread(::boucleWorker, "ordonnanceur-$nom").apply {
        isDaemon = true
        start()
    }

    /**
     * Soumet une tâche INTERACTIVE liée à un fichier.
     *
     * La soumission supersède immédiatement toute interactive du même
     * fichier **encore en attente** (sa future se solde par
     * `CancellationException`) ; la tâche en cours d'exécution sur le worker
     * n'est jamais interrompue.
     *
     * @param fichier clé de supersession (chemin du document).
     * @param fournisseur le calcul à exécuter sur le worker.
     * @return la future de la tâche (complétée, supersedée ou en échec).
     */
    public fun soumettreInteractif(fichier: String, fournisseur: Callable<Unit>): Future<Unit> {
        val tache = Tache(fichier, fournisseur)
        synchronized(verrou) {
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
                verrou.notifyAll()
            }
        }
        return tache.future
    }

    /**
     * Soumet une tâche d'ANALYSE (durable, jamais supersedée).
     *
     * @param fournisseur le calcul à exécuter sur le worker.
     * @return la future de la tâche.
     */
    public fun soumettreAnalyse(fournisseur: Callable<Unit>): Future<Unit> {
        val tache = Tache(null, fournisseur)
        synchronized(verrou) {
            if (arrete) {
                tache.future.cancel(false)
            } else {
                pretes.add(tache)
                verrou.notifyAll()
            }
        }
        return tache.future
    }

    /**
     * Nombre de supersessions effectives depuis la naissance.
     *
     * Surface observable exigée par le cahier (fiche 048) : à conserver
     * pour le banc de parité.
     */
    public fun supersededCount(): Int = supersessions.get()

    /**
     * Arrête l'ordonnanceur : idempotent (le second appel ne lève pas), le
     * worker se termine, les tâches en attente sont annulées.
     */
    public fun shutdown() {
        synchronized(verrou) {
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
            verrou.notifyAll()
        }
    }

    /** Alias de [shutdown] pour l'usage auto-fermable. */
    override fun close() {
        shutdown()
    }

    /** Boucle du worker : prend la plus ancienne tâche prête, l'exécute, recommence. */
    private fun boucleWorker() {
        while (true) {
            val tache = synchronized(verrou) {
                while (pretes.isEmpty() && !arrete) {
                    verrou.wait()
                }
                if (pretes.isEmpty()) null else pretes.removeAt(0)
            } ?: return
            if (tache.cle != null) {
                synchronized(verrou) {
                    if (interactivesEnAttente[tache.cle] === tache) {
                        interactivesEnAttente.remove(tache.cle)
                    }
                }
            }
            if (tache.estAnnulee()) {
                // Supersedée entre la prise et l'exécution : rien à courir.
                continue
            }
            enVol.incrementAndGet()
            try {
                tache.future.run()
                if (!tache.future.isCancelled) {
                    completions.incrementAndGet()
                }
            } finally {
                enVol.decrementAndGet()
            }
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
}

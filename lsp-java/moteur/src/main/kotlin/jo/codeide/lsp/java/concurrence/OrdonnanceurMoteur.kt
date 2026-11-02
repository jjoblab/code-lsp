package jo.codeide.lsp.java.concurrence

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
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
 * **Voies priorisées (V-H — branchement du protocole, ADR 0010 : « le
 * scheduler 3 lanes est conservé »)** : [soumettrePrioritaire] est la
 * soumission GÉNÉRIQUE typée consommée par la couche protocole — trois
 * voies (0 = FRAPPE : complétion et aide de signature, 1 = NAVIGATION,
 * 2 = CONFORT : inlay, actions…), la prise d'un worker privilégie la voie
 * la plus BASSE puis l'ordre de soumission ; la supersession d'une tâche
 * priorisée ne l'annule pas mais la SOLDE par la VALEUR DE REMPLACEMENT
 * fournie (une réponse VIDE honnête — le client attend une réponse pour
 * l'identifiant de sa requête) ; une soumission de voie FRAPPE rend
 * caduques les tâches EN ATTENTE des voies inférieures du MÊME fichier
 * (la frappe a déplacé le texte). L'exclusion par fichier s'applique à
 * TOUTES les voies (jamais deux tâches du même fichier en vol).
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

    /**
     * Les tâches PRIORISÉES en attente (V-H — branchement protocole) :
     * servies par voie croissante puis ordre de soumission ; l'exclusion
     * par fichier est PARTAGÉE avec les tâches historiques ([clesEnCours]).
     */
    private val prioriseesEnAttente = ArrayList<TachePrioritaire<*>>()

    /** Le séquenceur des soumissions priorisées (ordre stable par voie). */
    private var sequencePriorisees: Long = 0

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
     * les workers se terminent, les tâches en attente sont annulées — les
     * PRIORISÉES se soldent par leur valeur de REMPLACEMENT (V-H : le
     * client attend une réponse, la réponse vide honnête lui parvient).
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
            val enAttente = ArrayList(prioriseesEnAttente)
            prioriseesEnAttente.clear()
            enAttente.forEach { it.soldeParRemplacement() }
            aTravailler.signalAll()
        }
    }

    /** Alias de [shutdown] pour l'usage auto-fermable. */
    override fun close() {
        shutdown()
    }

    /**
     * Soumet une tâche PRIORISÉE typée (V-H — branchement du protocole).
     *
     * Sémantique (miroir des lanes du scheduler vendord, transposée à la
     * couche protocole) :
     *
     *  - **Priorité** : 0 = FRAPPE (complétion, aide de signature),
     *    1 = NAVIGATION, 2 = CONFORT. La prise d'un worker privilégie la
     *    voie la plus BASSE, puis l'ordre de soumission — une complétion
     *    ne patiente JAMAIS derrière une file d'inlay/actions.
     *  - **Supersession à VALEUR** : une tâche EN ATTENTE remplacée (même
     *    fichier, même voie — ou voie inférieure lors d'une soumission
     *    FRAPPE) se solde par [remplacement] — typiquement la réponse
     *    VIDE du type de la requête : le client attend une réponse pour
     *    son identifiant, une liste vide est honnête, une exception
     *    serait du bruit.
     *  - **Exclusion par fichier** : jamais deux tâches du MÊME fichier en
     *    vol, toutes voies confondues (contrat V-F2 inchangé).
     *  - **Durabilité** : `fichier` null = tâche DURABLE (jamais
     *    supersedée, pas d'exclusion de clé — requêtes d'espace de travail).
     *  - **Arrêt** : après [shutdown], la soumission se solde
     *    immédiatement par [remplacement] (aucun calcul).
     *
     * @param fichier la clé de supersession/exclusion (chemin ou URI du
     *   document ; null = durable).
     * @param priorite la voie (0 frappe, 1 navigation, 2 confort).
     * @param calcul le calcul à exécuter sur un worker.
     * @param remplacement la valeur qui solde la tâche supersedée ou
     *   refusée (la réponse vide honnête du type).
     * @return la future de la requête (complétée, remplacée ou en échec).
     */
    public fun <T> soumettrePrioritaire(
        fichier: String?,
        priorite: Int,
        calcul: () -> T,
        remplacement: () -> T,
    ): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        verrou.withLock {
            if (arrete) {
                future.complete(remplacement())
                return future
            }
            val sequence = sequencePriorisees++
            if (fichier != null) {
                // (a) Même fichier, MÊME voie : la plus récente remplace.
                // (b) Une soumission de FRAPPE rend caduques les tâches EN
                // ATTENTE des voies inférieures du même fichier : le texte
                // a bougé, leurs coordonnées sont périmées.
                val caduques = prioriseesEnAttente.filter { tache ->
                    tache.cle == fichier &&
                        (tache.priorite == priorite || (priorite <= PRIORITE_FRAPPE && tache.priorite > priorite))
                }
                if (caduques.isNotEmpty()) {
                    prioriseesEnAttente.removeAll(caduques.toSet())
                    caduques.forEach { it.soldeParRemplacement() }
                }
            }
            prioriseesEnAttente.add(
                TachePrioritaire(fichier, priorite, sequence, calcul, remplacement, future),
            )
            aTravailler.signalAll()
        }
        return future
    }

    /**
     * Boucle d'un worker : prend la tâche PRIORISÉE prenable de voie la
     * plus basse (puis ordre de soumission), sinon la plus ancienne tâche
     * historique PRENABLE (dont la clé de fichier n'est pas déjà en cours
     * sur un autre worker — jamais deux tâches du même fichier en vol),
     * l'exécute, recommence.
     */
    private fun boucleWorker() {
        while (true) {
            var historique: Tache? = null
            var prioritaire: TachePrioritaire<*>? = null
            verrou.withLock {
                while (historique == null && prioritaire == null && !arrete) {
                    val prenablePriorisee = prioriseesEnAttente
                        .filter { it.cle == null || it.cle !in clesEnCours }
                        .minByOrNull { it.priorite * ORDRE_MAGNITUDE + it.sequence }
                    if (prenablePriorisee != null) {
                        prioriseesEnAttente.remove(prenablePriorisee)
                        if (prenablePriorisee.cle != null) {
                            clesEnCours.add(prenablePriorisee.cle)
                        }
                        prioritaire = prenablePriorisee
                    } else {
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
                            historique = t
                        } else {
                            aTravailler.await()
                        }
                    }
                }
            }
            val priseHistorique = historique
            val prisePrioritaire = prioritaire
            when {
                priseHistorique != null -> {
                    if (priseHistorique.estAnnulee()) {
                        // Supersedée entre la prise et l'exécution : rien à courir,
                        // mais la clé doit être libérée pour les suivantes.
                        libererCle(priseHistorique)
                    } else {
                        enVol.incrementAndGet()
                        try {
                            priseHistorique.future.run()
                            if (!priseHistorique.future.isCancelled) {
                                completions.incrementAndGet()
                            }
                        } finally {
                            enVol.decrementAndGet()
                            libererCle(priseHistorique)
                        }
                    }
                }
                prisePrioritaire != null -> {
                    enVol.incrementAndGet()
                    try {
                        prisePrioritaire.courir()
                        completions.incrementAndGet()
                    } finally {
                        enVol.decrementAndGet()
                        libererCle(prisePrioritaire)
                    }
                }
                else -> return // arrêté
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

    /** Libère la clé de fichier d'une tâche priorisée terminée (V-H). */
    private fun libererCle(tache: TachePrioritaire<*>) {
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

    /**
     * Une tâche PRIORISÉE (V-H — branchement protocole) : future typée à
     * VALEUR DE REMPLACEMENT (jamais d'annulation — le client attend une
     * réponse pour l'identifiant de sa requête), voie et séquence pour la
     * prise des workers.
     */
    private inner class TachePrioritaire<T>(
        val cle: String?,
        val priorite: Int,
        val sequence: Long,
        val calcul: () -> T,
        val remplacement: () -> T,
        val future: CompletableFuture<T>,
    ) {
        /**
         * Exécute le calcul et solde la future ; une tâche déjà soldée
         * (supersession entre la prise et l'exécution) ne court PAS — voie
         * économique : aucun travail moteur pour une requête obsolète.
         */
        fun courir() {
            if (future.isDone) return
            try {
                val resultat = calcul()
                future.complete(resultat)
            } catch (t: Throwable) {
                premiereDefaillance.compareAndSet(null, t)
                future.completeExceptionally(t)
            }
        }

        /** Solde la tâche par la valeur de remplacement (supersession/arrêt). */
        fun soldeParRemplacement() {
            try {
                future.complete(remplacement())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
    }

    public companion object {
        /** La voie FRAPPE : complétion, aide de signature (V-H — protocole). */
        public const val PRIORITE_FRAPPE: Int = 0

        /** L'écart d'ordre de grandeur entre voies (la voie domine le séquençage). */
        private const val ORDRE_MAGNITUDE: Long = 1_000_000_000L

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

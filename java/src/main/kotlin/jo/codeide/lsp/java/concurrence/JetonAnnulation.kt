package jo.codeide.lsp.java.concurrence

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Jeton d'annulation coopératif (étage N1, cahier fiche 046).
 *
 * L'annulation dans le moteur est **coopérative** : les points de contrôle
 * consultent [isCancelled], et l'annulation diffuse une **interruption** aux
 * threads qui se sont attachés au jeton — jamais un kill. Trois variantes de
 * naissance couvrent les usages du serveur :
 *  - [NEVER] : singleton jamais annulable (tâches durables sans échéance) ;
 *  - [JetonsAnnulation.manual] : annulation déclenchée à la demande, idempotente ;
 *  - [JetonsAnnulation.timeout] : annulation automatique à l'échéance (garde latence).
 *
 * Contrats verrouillés par le cahier :
 *  - `annuler()` interrompt TOUS les threads attachés (un appelant attaché
 *    reçoit aussi l'interruption — comportement diffus assumé) ;
 *  - [close] ≠ `annuler()` : fermer libère les références aux threads sans
 *    jamais marquer le jeton annulé ;
 *  - toutes les variantes sont auto-fermables.
 */
public interface JetonAnnulation : AutoCloseable {

    /** Singleton et fabriques du contrat de naissance. */
    public companion object {

        /** Instance unique du jeton inerte — jamais annulable, sans ressources. */
        public val NEVER: JetonAnnulation = JetonInerte()
    }

    /**
     * Indique si le porteur doit abandonner son travail.
     *
     * @return vrai ssi `annuler()` a été appelé (ou l'échéance atteinte pour
     *   la variante timeout) ; toujours faux pour [NEVER].
     */
    public fun isCancelled(): Boolean

    /**
     * Demande l'annulation et interrompt tous les threads attachés.
     *
     * L'opération est **idempotente** : les contrôles ultérieurs restent
     * vrais, les interruptions ne sont émises qu'à la première demande.
     */
    public fun annuler()

    /**
     * Attache le thread courant au jeton.
     *
     * Un thread attaché recevra l'interruption au `annuler()` — c'est le
     * canal qui coupe les opérations bloquantes (lecture disque, attente).
     * Le détachement se fait par [close].
     *
     * @return ce jeton (chaînage).
     */
    public fun attacherAuThreadCourant(): JetonAnnulation

    /** Implémentation inerte du singleton [NEVER]. */
    private class JetonInerte : JetonAnnulation {
        override fun isCancelled(): Boolean = false
        override fun annuler() {
            // Jamais annulable par contrat.
        }

        override fun attacherAuThreadCourant(): JetonAnnulation = this
        override fun close() {
            // Aucune ressource.
        }
    }
}

/**
 * Fabrique des jetons annulables (le contrat de naissance du cahier, fiche 046).
 */
public object JetonsAnnulation {

    /**
     * Crée un jeton manuel : non annulé à la naissance, annulable à la demande.
     *
     * @return un jeton auto-fermable, initialement non annulé.
     */
    @JvmStatic
    public fun manual(): JetonAnnulation = JetonManuel()

    /**
     * Crée un jeton à échéance : annulé automatiquement après [duree] [unite].
     *
     * Le minuteur est un thread démon léger : il ne retarde jamais l'arrêt de
     * la JVM et s'efface à [JetonAnnulation.close] sans déclencher
     * l'annulation (contrat : fermer n'annule jamais).
     *
     * @param duree durée avant annulation (strictement positive).
     * @param unite unité de temps.
     * @return un jeton auto-fermable, non annulé à la naissance.
     * @throws IllegalArgumentException si [duree] `<= 0`.
     */
    @JvmStatic
    public fun timeout(duree: Long, unite: TimeUnit): JetonAnnulation {
        require(duree > 0) { "duree doit être positive : $duree $unite" }
        val jeton = JetonManuel()
        val minuteur = Thread({
            try {
                Thread.sleep(unite.toMillis(duree))
                jeton.annuler()
            } catch (_: InterruptedException) {
                // close() avant l'échéance : le minuteur s'efface sans annuler.
            }
        }, "jeton-annulation-timeout")
        minuteur.isDaemon = true
        jeton.attacherMinuteur(minuteur)
        minuteur.start()
        return jeton
    }
}

/**
 * Implémentation annulable partagée par les variantes manual et timeout.
 */
private class JetonManuel : JetonAnnulation {

    private val annule = AtomicBoolean(false)
    private val threads = CopyOnWriteArrayList<Thread>()

    @Volatile
    private var minuteur: Thread? = null

    override fun isCancelled(): Boolean = annule.get()

    override fun annuler() {
        if (annule.compareAndSet(false, true)) {
            // Le minuteur éventuel devient inutile : on le coupe pour ne pas
            // laisser dormir un thread déjà dépassé.
            minuteur?.interrupt()
            for (thread in threads) {
                thread.interrupt()
            }
        }
    }

    override fun attacherAuThreadCourant(): JetonAnnulation {
        threads.add(Thread.currentThread())
        return this
    }

    override fun close() {
        // Fermer libère les références SANS annuler (contrat : close ≠ cancel).
        threads.clear()
        minuteur?.interrupt()
        minuteur = null
    }

    /** Attache le minuteur de la variante timeout (réservé à [JetonsAnnulation]). */
    fun attacherMinuteur(thread: Thread) {
        minuteur = thread
    }
}

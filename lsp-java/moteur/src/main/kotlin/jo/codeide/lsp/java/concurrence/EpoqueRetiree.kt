package jo.codeide.lsp.java.concurrence

/**
 * Signal de coupure d'époque (vague V-F1 — ADR 0029 phase 1, « annulation
 * fine de base »).
 *
 * Levée par les **points de contrôle** des boucles longue durée du moteur
 * (collecte de complétion, balayage d'usages) quand le jeton d'époque du
 * moteur est annulé — c'est-à-dire quand le moteur vient d'être RETIRÉ du
 * service par un swap de double-tampon : le résultat en cours de calcul ne
 * sera JAMAIS livré (il serait périmé), inutile de le finir.
 *
 * La coupure est **coopérative** (jamais une interruption de thread — le
 * jeton fiche 046 n'attache AUCUN thread ici) et **contrôlée** : elle ne
 * franchit la frontière du service de complétion QUE pour remonter jusqu'au
 * protocole d'époque ([jo.codeide.lsp.java.EnvironnementCompilation.
 * calculerFraiche]) qui REJOUE le calcul sur l'époque courante (« rejeu des
 * requêtes coupées » — ADR 0029 (b)). Les appelants directs du moteur (tests,
 * hôte in-process) ne la voient jamais : un moteur qui n'a pas été retiré a
 * son jeton sur [JetonAnnulation.NEVER], les points de contrôle restent
 * muets.
 *
 * L'exception est VOLONTAIREMENT légère : pas de pile remplie (le rejeu la
 * relaie, la pile ne servirait qu'un diagnostic que la cause porte déjà).
 */
public class EpoqueRetiree(
    message: String = "époque du moteur retirée — le calcul doit être rejoué sur l'époque courante",
) : RuntimeException(message) {
    override fun fillInStackTrace(): Throwable = this
}

package jo.codeide.lsp.java

/**
 * Port SPI de l'index de noms (étage N1) — la surface minimale que le
 * moteur consomme, définie par les fiches 032 et 034 du cahier.
 *
 * L'implémentation de production est l'index disque CIDX (`:index`, ADR
 * 0008) branché par le module d'intégration `:testing` (ADR 0016) ; les
 * tests du cahier posent des index FACTICES qui rendent EXPRÈS des entrées
 * brutes (comportement pré-correctif) — c'est le MOTEUR qui filtre en aval
 * (fiche 032 : « invariant à conserver même avec un index imparfait »).
 *
 * Contrat des membres (fiche 034 — surface observée du faux index) :
 *  - [estPret] : l'index a fini sa construction ;
 *  - [ajouterObservateurPret] : observateur de la transition PRÊT (les
 *    index factices l'exécutent immédiatement) ;
 *  - [jarDe] : jar porteur d'un FQCN ;
 *  - [nomsParPrefixe] : noms simples par préfixe (bornés) ;
 *  - [fqnsForSimpleName] : FQCN portant un nom simple (BRUTS — le moteur filtre `$`) ;
 *  - [estPaquet] / [sousPaquetsDirects] / [typesDansPaquet] : arborescence
 *    de paquets (les entrées peuvent être brutes, avec `$`) ;
 *  - [membresStatiques] : membres statiques d'un FQCN.
 */
public interface IndexNoms {

    /** L'index a-t-il fini sa construction ? */
    public fun estPret(): Boolean

    /**
     * Enregistre un observateur de la transition PRÊT.
     *
     * Un index DÉJÀ prêt exécute l'observateur immédiatement (contrat des
     * index factices du cahier).
     *
     * @param observateur callback sans argument, appelé au plus une fois.
     */
    public fun ajouterObservateurPret(observateur: () -> Unit)

    /**
     * Jar porteur d'un FQCN (le `classpath` de l'entrée d'index).
     *
     * @param fqcn nom entièrement qualifié en points.
     * @return le chemin du jar, ou null si inconnu de l'index.
     */
    public fun jarDe(fqcn: String): String?

    /**
     * Noms simples commençant par un préfixe.
     *
     * @param prefixe le préfixe cherché.
     * @param limite borne du nombre de noms rendus.
     * @return les noms simples correspondants.
     */
    public fun nomsParPrefixe(prefixe: String, limite: Int): List<String>

    /**
     * FQCN portant EXACTEMENT un nom simple — rendus BRUTS (un FQCN peut
     * contenir `$` : le filtre appartient au moteur).
     *
     * @param nom le nom simple.
     * @return les FQCN déclarant ce nom.
     */
    public fun fqnsForSimpleName(nom: String): List<String>

    /** Le paquet existe-t-il dans l'index ? */
    public fun estPaquet(paquet: String): Boolean

    /** Sous-paquets DIRECTS d'un paquet (noms simples de paquets). */
    public fun sousPaquetsDirects(paquet: String): List<String>

    /**
     * Types d'un paquet — rendus BRUTS (FQCN, éventuellement avec `$`).
     *
     * @param paquet le paquet en points.
     * @return les FQCN du paquet.
     */
    public fun typesDansPaquet(paquet: String): List<String>

    /**
     * Membres statiques d'un type (complétion statique).
     *
     * @param fqcn le type porteur.
     * @return les noms de membres statiques.
     */
    public fun membresStatiques(fqcn: String): List<String>
}

package jo.codeide.lsp.java.completion

/**
 * Frontière RPC de la complétion (design D8 « en naissance », ADR 0012
 * vague (b)) : les structures transportées au client pour le re-filtrage
 * LOCAL par frappe — sans aller-retour serveur. La capability LSP
 * `x-codeide.completionRanking` et le câblage protocole arrivent à l'étage
 * N8 (cycle de vie & protocole riche) ; l'étage N2 pose les structures et
 * verrouille leur contrat :
 *
 *  - [PoidsRpc] : le poids de classement PACKÉ en un entier transportable ;
 *  - [CorrespondanceRpc] : le matcher sérialisé (préfixe + règles graduées)
 *    que le client rejoue localement à chaque frappe.
 */
public object FrontiereRpc {

    /**
     * Poids RPC d'un item : les dimensions NUMÉRIQUES de la chaîne, packées
     * en un entier unique — la dimension DOMINANTE (correspondance) occupe
     * les bits de poids fort.
     *
     * Contrat verrouillé (ADR 0012, risque R1 — piège documenté
     * « lower = higher priority ») : **plus petit = plus prioritaire**. Un
     * client qui trierait par poids croissant reproduit l'ordre du serveur.
     *
     * @property correspondance le bonus de grade (−200 préfixe / 0 autre).
     * @property nature la priorité de nature dans le contexte.
     * @property longueur la longueur du libellé (liftShorter).
     */
    public class PoidsRpc(
        public val correspondance: Int,
        public val nature: Int,
        public val longueur: Int,
    ) {

        /**
         * Le poids packé : correspondance en poids fort, puis nature, puis
         * longueur — le départage alphabétique reste au client (le libellé
         * voyage avec l'item).
         *
         * Les poids négatifs sont décalés par [DECALAGE] pour rester dans
         * les bornes positives du transport : l'ORDRE est préservé bit à
         * bit, la convention « plus petit = plus prioritaire » aussi.
         */
        public val packe: Long
            get() = (correspondance + DECALAGE).toLong() shl 32 or
                ((nature + DECALAGE).toLong() shl 16) or
                (longueur + DECALAGE).toLong()

        public companion object {
            /** Décalage de positivité des dimensions (bornes : −200..1000). */
            public const val DECALAGE: Int = 1_000
        }
    }

    /**
     * Matcher de correspondance sérialisé (miroir transportable du
     * `RpcPrefixMatcher` du projet tiers de référence) : le client re-filtre
     * la liste reçue par préfixe SANS nouveau aller-retour serveur — la
     * réponse structurelle au facteur ART 3–5× (ADR 0012).
     *
     * @property prefixe le préfixe tapé au moment de l'envoi.
     * @property longueurMinSousChaine la règle « substring ≥ 3 » (fiche 008).
     */
    public class CorrespondanceRpc(
        public val prefixe: String,
        public val longueurMinSousChaine: Int = CorrespondanceGraduee.LONGUEUR_MIN_SOUS_CHAINE,
    ) {
        /**
         * Grade local d'un nom pour le préfixe transporté — le client
         * rejoue les règles graduées du serveur.
         *
         * @param nom le nom candidat.
         * @return le grade de correspondance.
         */
        public fun grade(nom: String): GradeCorrespondance =
            CorrespondanceGraduee.grade(prefixe, nom)
    }
}

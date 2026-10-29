package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.interne.ItemCompletion

/**
 * Réponse de l'arrangement (étage N2) : la liste ordonnée, plafonnée à
 * l'affichage, AVEC la publicité honnête du total disponible (le trim ne
 * mente jamais sur la taille de l'espace de candidats — fiche 043 : le
 * dump est plafonné à 120 items, le total réel reste consultable).
 *
 * @property items les items ordonnés (≤ [LIMITE_AFFICHAGE]).
 * @property totalDisponible le nombre RÉEL de candidats correspondants
 *   (avant trim — l'honnêteté du plafond).
 */
public class ReponseArrangement(
    public val items: List<ItemCompletion>,
    public val totalDisponible: Int,
) {
    /** Vrai ssi la liste a été raccourcie par le plafond d'affichage. */
    public val tronquee: Boolean
        get() = totalDisponible > items.size
}

/**
 * Arrangement de liste de complétion (design D8 « en naissance », ADR 0012) :
 * le miroir Kotlin de l'arrangement IntelliJ — **gel** des items affichés en
 * tête (les items déjà montrés ne sautent pas quand le préfixe s'affine) et
 * **trim honnête** (plafond d'affichage + total réel communiqué).
 *
 * Le gel (`freezeTopItems`) : après chaque arrangement sous une même clé de
 * contexte (même document, même genre, même ancre d'expression — le
 * qualificatif/receveur tapé), les [GEL_MAX] premiers libellés sont
 * mémorisés ; à l'arrangement SUIVANT sous la même clé, chaque survivant du
 * gel OCCUPE EXACTEMENT son rang précédent — un nouveau venu ne déplace
 * JAMAIS un item déjà affiché en tête (contrat « zéro saut de liste » du
 * banc de qualité).
 *
 * Le trim : la liste rendue est plafonnée à [LIMITE_AFFICHAGE] items
 * (fiche 043 : « affichage plafonné à 120 items ») ; le total disponible
 * voyage avec la réponse — le client SAIT que la liste est tronquée.
 *
 * @param chaine la chaîne de classement (peseurs ordonnés).
 */
public class ArrangementListe(
    private val chaine: ChaineClassement,
) {

    /** Items gelés par clé de contexte (libellé → rang). */
    private val gels = HashMap<String, List<String>>()

    /**
     * Arrange les candidats : classement par la chaîne, gel des têtes,
     * plafond honnête.
     *
     * @param cleContexte la clé de stabilité (document + genre + ancre) —
     *   deux arrangements successifs de frappes successives partagent la clé.
     * @param candidats les items collectés, non ordonnés.
     * @param contexte le contexte de classement.
     * @return la réponse arrangée (items ≤ limite + total réel).
     */
    public fun arranger(
        cleContexte: String,
        candidats: List<ItemCompletion>,
        contexte: ContexteClassement,
    ): ReponseArrangement {
        val total = candidats.size
        val poids = chaine.preparer(candidats)
        val ordonnee = poids.sortedWith(chaine.comparateur(contexte)).map { it.porte }
        val gelees = appliquerGel(cleContexte, ordonnee)
        memoriserGel(cleContexte, gelees)
        val items = gelees.take(LIMITE_AFFICHAGE)
        return ReponseArrangement(items, total)
    }

    /**
     * Applique le gel aux items ordonnés : les survivants de la tête gelée
     * précédente reprennent LEUR rang exact, les autres comblent les trous
     * dans l'ordre de la chaîne.
     *
     * Cas de rétrécissement (découvert par la fiche 020) : quand le préfixe
     * s'affine, la liste ordonnée peut compter MOINS d'items que la tête
     * gelée précédente — les places non comblables restent alors vides,
     * JAMAIS d'exception (le contrat « jamais null » de la fiche 035 ne
     * doit pas servir de masque à un crash du gel : la levée avalée se
     * manifestait comme une liste VIDE silencieuse).
     */
    private fun appliquerGel(cleContexte: String, ordonnee: List<ItemCompletion>): List<ItemCompletion> {
        val gelees = gels[cleContexte] ?: return ordonnee
        if (gelees.isEmpty()) return ordonnee
        val rangs = HashMap<String, Int>()
        gelees.forEachIndexed { rang, libelle -> rangs[libelle] = rang }
        val places = arrayOfNulls<ItemCompletion>(ordonnee.size.coerceAtLeast(gelees.size))
        val epargnes = mutableListOf<ItemCompletion>()
        // 1. Les survivants du gel reprennent leur rang.
        for (item in ordonnee) {
            val rang = rangs[item.libelle]
            if (rang != null && rang < places.size && places[rang] == null) {
                places[rang] = item
            } else {
                epargnes.add(item)
            }
        }
        // 2. Les autres comblent les trous, dans l'ordre de la chaîne ; si
        // la liste a rétréci, les places restantes restent vides (honnête).
        var curseur = 0
        val resultat = mutableListOf<ItemCompletion>()
        for (place in places) {
            if (place != null) {
                resultat.add(place)
                continue
            }
            val epargne = epargnes.getOrNull(curseur)
            if (epargne != null) {
                resultat.add(epargne)
                curseur++
            }
        }
        // 3. Le reliquat (au-delà des places gelées) suit.
        while (curseur < epargnes.size) resultat.add(epargnes[curseur++])
        return resultat.filterNotNull()
    }

    /** Mémorise la nouvelle tête gelée (les [GEL_MAX] premiers libellés). */
    private fun memoriserGel(cleContexte: String, ordonnee: List<ItemCompletion>) {
        gels[cleContexte] = ordonnee.take(GEL_MAX).map { it.libelle }
    }

    /** Oublie tout gel (test/nouvelle session). */
    public fun reinitialiser() {
        gels.clear()
    }

    public companion object {
        /**
         * Plafond d'affichage de la liste rendue (fiche 043 : « affichage
         * plafonné à 120 items par dump » ; fiche 044 : l'avalanche du
         * préfixe vide reste listée).
         */
        public const val LIMITE_AFFICHAGE: Int = 120

        /** Nombre d'items gelés en tête par clé de contexte. */
        public const val GEL_MAX: Int = 8
    }
}

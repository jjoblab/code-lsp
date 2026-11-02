package jo.codeide.lsp.java.gabarits

import jo.codeide.lsp.java.completion.ContexteCompletion
import jo.codeide.lsp.java.completion.GenreContexte
import jo.codeide.lsp.java.interne.ItemCompletion
import jo.codeide.lsp.java.interne.NatureItem

/**
 * Intégration des gabarits dans le pipeline de complétion (étage N7 —
 * cahier fiche 029, niveau « intégration moteur »).
 *
 * Les gabarits s'injectent APRÈS l'arrangement D8 (la chaîne de peseurs et
 * le gel des têtes restent intacts — aucune régression de classement) :
 *  - VIVANTS : en genre NOM et position d'INSTRUCTION uniquement — la clé
 *    exacte (pertinence 2500) passe EN TÊTE de la liste, les clés
 *    partielles (100) restent DISCRÈTES en queue ;
 *  - POSTFIXES : en genre MEMBRE — l'item remplace le récepteur ET la clé
 *    (édition additionnelle de suppression), la conscience de type vient
 *    du binding du récepteur diagnostiqué par l'analyseur ;
 *  - ANTI-POLLUTION : préfixe vide, contexte d'import, position
 *    d'expression — AUCUN gabarit ne sort.
 */
public object ServiceGabarits {

    /**
     * Enrichit la liste ordonnée des items avec les gabarits du caret.
     *
     * @param items les items ordinaires, déjà arrangés (ordre D8, gel,
     *   plafond honnête).
     * @param contexte le contexte diagnostiqué au caret.
     * @param source la source ORIGINALE (les éditions additionnelles s'y
     *   expriment — jamais dans le système de la source marquée).
     * @param offset la position du caret dans la source originale.
     * @return la liste enrichie (tête : postfixe ou vivante exacte ;
     *   queue : vivantes partielles).
     */
    public fun enrichir(
        items: List<ItemCompletion>,
        contexte: ContexteCompletion,
        source: String,
        offset: Int,
    ): List<ItemCompletion> {
        val prefixe = contexte.prefixe
        if (prefixe.isEmpty()) return items
        val debutCle = (offset - prefixe.length).coerceAtLeast(0)

        // Postfixes : `recepteur.cle` — genres de MEMBRE uniquement.
        if (contexte.genre == GenreContexte.MEMBRE || contexte.genre == GenreContexte.MEMBRE_STATIQUE) {
            val typeResolu = contexte.typeRecepteur?.readableName()?.let { String(it) }
            val postfixes = GabaritsPostfixes.itemsPour(source, debutCle, prefixe, typeResolu)
            if (postfixes.isNotEmpty()) {
                val converties = postfixes.map { item ->
                    ItemCompletion(
                        libelle = item.cle,
                        nature = NatureItem.EXTRAIT,
                        texteInsertion = item.extrait,
                        extrait = true,
                        pertinence = item.pertinence,
                        editionsAdditionnelles = item.editions,
                    )
                }
                return converties + items
            }
            return items
        }

        // Vivants : genre NOM + position d'INSTRUCTION (garde textuelle).
        if (contexte.genre == GenreContexte.NOM && GabaritsVivants.estPositionInstruction(source, debutCle)) {
            val vivants = GabaritsVivants.itemsPour(prefixe)
            if (vivants.isEmpty()) return items
            val tete = vivants.filter { it.pertinence == GabaritsVivants.PERTINENCE_EXACTE }.map { vivant ->
                ItemCompletion(
                    libelle = vivant.gabarit.cle,
                    nature = NatureItem.EXTRAIT,
                    texteInsertion = vivant.gabarit.extrait,
                    extrait = true,
                    pertinence = vivant.pertinence,
                )
            }
            val queue = vivants.filter { it.pertinence == GabaritsVivants.PERTINENCE_PARTIELLE }.map { vivant ->
                ItemCompletion(
                    libelle = vivant.gabarit.cle,
                    nature = NatureItem.EXTRAIT,
                    texteInsertion = vivant.gabarit.extrait,
                    extrait = true,
                    pertinence = vivant.pertinence,
                )
            }
            return tete + items + queue
        }

        return items
    }
}

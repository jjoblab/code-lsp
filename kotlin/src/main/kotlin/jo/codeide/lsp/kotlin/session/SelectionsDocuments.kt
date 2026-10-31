// CodeIDE — chaînes de sélection d'un document Kotlin (module :kotlin,
// étage 6 vague 6b). Code NEUF jjoblab (contrat : fiche 015 du cahier
// docs/cahier-specifications-kotlin/ — `textDocument/selectionRange` : la
// chaîne des maillons englobants, du PLUS PROFOND au FICHIER ENTIER,
// strictement croissante ; hors arbre = fichier SEUL, rien n'est inventé).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ChaineSelectionKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Calcul des CHAÎNES DE SÉLECTION (pur — aucun état, appelé sous verrou de
 * lecture).
 *
 * ## La chaîne (fiche 015)
 *
 * Pour une position : les nœuds de l'arbre qui la CONTIENNENT, du plus
 * profond au fichier entier. Chaque maillon CONTIENT le précédent (la
 * chaîne est strictement croissante en étendue — les planges ÉGALES d'un
 * parent et de son enfant unique se dédupliquent). Une position HORS
 * arbre (au-delà de la fin, dans le vide) porte le FICHIER SEUL — aucun
 * maillon n'est inventé (D3).
 */
internal object SelectionsDocuments {

    /**
     * Rend les chaînes des positions demandées, dans leur ordre.
     *
     * @param arbre l'arbre du document ouvert ;
     * @param deplacements les offsets des positions ;
     * @return une chaîne par position (du profond au fichier) ; le fichier
     * seul pour une position hors arbre.
     */
    internal fun selections(arbre: KtFichier, deplacements: List<Int>): List<ChaineSelectionKotlin> {
        return deplacements.map { position -> chaine(arbre.noeud, position) }
    }

    /**
     * La chaîne d'UNE position.
     *
     * @param racine le nœud racine du document (le fichier) ;
     * @param position l'offset de la position ;
     * @return les maillons du PLUS PROFOND au FICHIER (toujours présent en
     * dernier — fiche 015 : « le fichier ENTIER est le DERNIER maillon »).
     */
    private fun chaine(racine: NoeudArbre, position: Int): ChaineSelectionKotlin {
        // La descente de la racine au plus profond contenant : chaque
        // étape ajoute la plage du nœud visité (racine d'abord) ; la
        // chaîne se rend INVERSÉE (du profond au fichier).
        val descente = mutableListOf<PlageTexte>()
        var courant: NoeudArbre = racine
        while (true) {
            val plage = PlageTexte(courant.debut, courant.fin)
            // La déduplication : un parent de plage ÉGALE à son enfant
            // (le wrapper mono-enfant) n'ouvre pas de maillon neuf.
            if (descente.isEmpty() || descente.last() != plage) descente.add(plage)
            if (position < courant.debut || position >= courant.fin) break
            val enfant = courant.enfants.firstOrNull {
                it.debut <= position && position < it.fin
            } ?: break
            courant = enfant
        }
        return ChaineSelectionKotlin(plages = descente.asReversed())
    }
}

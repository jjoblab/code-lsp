// CodeIDE — complétion MEMBRE (module :kotlin, étage 2 vague 2a — le GERME
// requis par la fiche 022 du cahier ; vague 2b — fiche 046 : les récepteurs
// de BIBLIOTHÈQUE (vue JVM), les SMART CASTS positionnels et les ATTENTES
// de lambda type le récepteur ; étage 3 vague 3a — ADR 0113 : le régime
// MEMBRE du pipeline, propositions DÉTAILLÉES — natures et types de
// présentation). Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.Couches
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.PileThis
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le régime MEMBRE du pipeline (étage 3 — ADR 0113) : les membres d'un
 * récepteur TYPÉ après le point (fiche 022 — `copy` proposé après `p.` ;
 * fiche 046 — récepteurs de bibliothèque par la vue JVM, smart cast
 * positionnel, attente de lambda ; fiches 044/040 — les membres HÉRITÉS de
 * la clôture, `this.` dans une Activity propose `layoutInflater` ; fiche
 * 045 — champ et méthode le long de la chaîne de supertypes, avec nature
 * et type de présentation).
 *
 * Le régime est CONSERVATEUR : un point dont le récepteur n'est pas typable
 * (classpath illisible, littéral hors famille, non typable) rend une liste
 * VIDE honnête — jamais une invention (D3). La liste n'est PAS filtrée par
 * préfixe : le re-filtrage local par frappe est la frontière RPC de l'hôte
 * (ADR 0113/0118 — le serveur envoie la liste et son classement).
 */
internal object CompletionMembres {

    /**
     * Collecte les propositions du régime MEMBRE au point.
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison de la région porteuse ;
     * @param deplacement l'offset du caret (juste après le point) ;
     * @return les propositions détaillées du récepteur typé ; liste vide
     * sans récepteur typable — jamais d'exception.
     */
    internal fun collecter(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        deplacement: Int,
    ): List<PropositionKotlin> {
        // Le récepteur : la PLUS PROFONDE expression qualifiée couvrant le
        // point (le caret vit juste après le point du sélecteur).
        val qualifiee = recepteurQualifie(arbre.noeud, deplacement) ?: return emptyList()
        val recepteur = qualifiee.enfants.firstOrNull() ?: return emptyList()

        // La PILE DES THIS au caret (vague 2c — fiches 028/040 : `this.`
        // type la couche profonde, les membres implicites hérités
        // `layoutInflater` type le récepteur, les lambdas d'application
        // portent le leur).
        val pile = Couches.aPosition(arbre, univers, liaison, deplacement)
        val typeRecepteur = TypageBorne.typeDeLexpression(recepteur, univers, liaison, pile)
            ?: return emptyList()
        return univers.propositionsMembresDetaillees(typeRecepteur).map {
            PropositionKotlin(libelle = it.nom, nature = it.nature, detail = it.detail)
        }
    }

    /**
     * La plus profonde expression qualifiée (point ou accès sûr) dont la
     * plage couvre le point AVANT le caret (le caret vit juste après le
     * point du sélecteur).
     *
     * @param racine la racine de l'arbre du document ;
     * @param deplacement l'offset du caret ;
     * @return le nœud qualifié portant le récepteur ; `null` si le point
     * n'est pas dans un sélecteur.
     */
    private fun recepteurQualifie(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement - 1 < noeud.debut || deplacement - 1 >= noeud.fin) return
            if (noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION ||
                noeud.genre == KtNodeTypes.SAFE_ACCESS_EXPRESSION
            ) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }
}

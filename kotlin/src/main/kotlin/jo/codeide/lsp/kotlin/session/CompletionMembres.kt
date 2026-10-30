// CodeIDE — complétion MEMBRE (module :kotlin, étage 2 vague 2a — le GERME
// requis par la fiche 022 du cahier : « data class : copy résolu et proposé
// (le modificateur data enfin lu du MODIFIER_LIST) » ; le pipeline complet —
// contextuelle, classement, multi-langue — est l'étage 3, ADR 0113). Code
// NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.index.VueAppelables
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.ContributionFichier
import jo.codeide.lsp.kotlin.resolve.EtatScan
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le germe de complétion MEMBRE : les membres d'un récepteur typé du PROJET
 * après le point (fiche 022 — `copy` proposé après `p.`).
 *
 * Le germe est CONSERVATEUR : un point dont le récepteur n'est pas un type
 * du PROJET (classpath, littéral, non typable) rend une liste VIDE honnête
 * — les récepteurs classpath et le classement relèvent de l'étage 3 (le
 * germe ne devine jamais, D3).
 */
internal object CompletionMembres {

    /**
     * Propose les membres du récepteur typé du projet au point demandé.
     *
     * @param document le document ouvert (texte + arbre) ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret (juste après le point) ;
     * @param contributionsScan les contributions du scan (document exclu) ;
     * @param etatScan l'état du scan ;
     * @param index la vue des appelables du classpath (`null` = guichet
     * muet : les extensions classpath manquent, jamais de propositions
     * fausses).
     * @return les propositions en ordre de déclaration du récepteur ; liste
     * vide sans récepteur typé du projet — jamais d'exception.
     */
    internal fun proposer(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        contributionsScan: List<ContributionFichier>,
        etatScan: EtatScan,
        index: VueAppelables?,
    ): List<PropositionKotlin> {
        val arbre = document.arbre

        // Le récepteur : la PLUS PROFONDE expression qualifiée couvrant le
        // point (le caret vit juste après le point du sélecteur).
        val qualifiee = recepteurQualifie(arbre.noeud, deplacement) ?: return emptyList()
        val recepteur = qualifiee.enfants.firstOrNull() ?: return emptyList()

        // L'univers, la liaison de la RÉGION contenant le point (les
        // paramètres et locales de la fonction porteuse — miroir de la
        // passe de diagnostics).
        val batisseur = ModeleProjet.Batisseur()
        batisseur.ajouter(ModeleProjet.contributionKotlin(arbre, uri = ""))
        contributionsScan.forEach(batisseur::ajouter)
        val univers = UniversPreuves(
            arbre = arbre,
            paquet = arbre.directivePackage()?.nomQualifie().orEmpty(),
            modele = batisseur.figer(),
            index = index,
            etatScan = etatScan,
            script = uri.endsWith(".kts"),
        )
        val region = regionContenant(arbre.noeud, deplacement)
        val liaison = MarcheTypee.Liaison(arbre = arbre, univers = univers, region = region)
        val typeRecepteur = MarcheTypee.typeProjetDeLexpression(recepteur, univers, liaison)
            ?: return emptyList()
        return univers.propositionsMembres(typeRecepteur).map(::PropositionKotlin)
    }

    /**
     * La RÉGION contenant le point : la plus profonde déclaration de
     * fonction ou de propriété (les paramètres et locales y vivent — le
     * miroir des régions propres de la passe de diagnostics).
     *
     * @param racine la racine de l'arbre du document ;
     * @param deplacement l'offset du caret ;
     * @return la région porteuse ; `null` au niveau du document.
     */
    private fun regionContenant(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.FUN || noeud.genre == KtNodeTypes.PROPERTY) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
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

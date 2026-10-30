// CodeIDE — complétion MEMBRE (module :kotlin, étage 2 vague 2a — le GERME
// requis par la fiche 022 du cahier ; vague 2b — fiche 046 : les récepteurs
// de BIBLIOTHÈQUE (vue JVM), les SMART CASTS positionnels et les ATTENTES
// de lambda type le récepteur). Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.index.VueAppelables
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.ContributionFichier
import jo.codeide.lsp.kotlin.resolve.Couches
import jo.codeide.lsp.kotlin.resolve.EtatScan
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import jo.codeide.lsp.kotlin.resolve.VueJvmMembres
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le germe de complétion MEMBRE : les membres d'un récepteur TYPAÉ après
 * le point (fiche 022 — `copy` proposé après `p.` ; fiche 046 — récepteur
 * de bibliothèque par la vue JVM, smart cast positionnel, attente de
 * lambda : `s.` dans `repeter(2) { s -> … }` type `s`).
 *
 * Le germe est CONSERVATEUR : un point dont le récepteur n'est pas typable
 * (classpath illisible, littéral hors famille, non typable) rend une liste
 * VIDE honnête — le classement relève de l'étage 3 (le germe ne devine
 * jamais, D3).
 */
internal object CompletionMembres {

    /**
     * Propose les membres du récepteur typé au point demandé.
     *
     * @param document le document ouvert (texte + arbre) ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret (juste après le point) ;
     * @param contributionsScan les contributions du scan (document exclu) ;
     * @param etatScan l'état du scan ;
     * @param index la vue des appelables du classpath (`null` = guichet
     * muet : les extensions classpath manquent, jamais de propositions
     * fausses) ;
     * @param vueJvm la vue JVM des membres du classpath (`null` = les
     * récepteurs de bibliothèque ne proposent rien — D3).
     * @return les propositions en ordre de rencontre du récepteur ; liste
     * vide sans récepteur typé — jamais d'exception.
     */
    internal fun proposer(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        contributionsScan: List<ContributionFichier>,
        etatScan: EtatScan,
        index: VueAppelables?,
        vueJvm: VueJvmMembres? = null,
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
            vueJvm = vueJvm,
        )
        val region = regionContenant(arbre.noeud, deplacement)
        val liaison = MarcheTypee.Liaison(arbre = arbre, univers = univers, region = region)
        // La PILE DES THIS au caret (vague 2c — fiches 028/040 : `this.` type
        // la couche profonde, les membres implicites hérités `layoutInflater`
        // type le récepteur, les lambdas d'application portent le leur).
        val pile = Couches.aPosition(arbre, univers, liaison, deplacement)
        val typeRecepteur = TypageBorne.typeDeLexpression(recepteur, univers, liaison, pile)
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

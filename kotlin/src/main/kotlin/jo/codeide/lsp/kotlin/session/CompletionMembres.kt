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

    /** La limite des propositions de l'index par préfixe (germe 2e). */
    private const val LIMITE_INDEX: Int = 24

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
        val qualifiee = recepteurQualifie(arbre.noeud, deplacement) ?: return nomsAuPoint(
            document, uri, deplacement, contributionsScan, index,
        )
        val recepteur = qualifiee.enfants.firstOrNull() ?: return nomsAuPoint(
            document, uri, deplacement, contributionsScan, index,
        )

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
     * La complétion de NOM au point (vague 2e — fiche 025 : « complétion
     * de nom dans un corps ») : le caret vit dans un identifiant SANS
     * récepteur qualifié — les noms DÉCLARÉS du document (garde
     * documentaire), puis les TYPES du catalogue KP8, puis les APPELABLES
     * de premier niveau de l'index par PRÉFIXE (recherche binaire), tous
     * filtrés par le préfixe déjà frappé. Le germe est CONSERVATEUR : le
     * classement contextuel relève de l'étage 3 (ADR 0113).
     *
     * @param document le document ouvert ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret (dans ou juste après
     * l'identifiant) ;
     * @param contributionsScan les contributions du scan ;
     * @param index la vue des appelables du classpath (`null` = guichet
     * muet : seuls les noms déclarés et le catalogue se proposent) ;
     * @return les propositions par ordre déclaré → catalogue → index.
     */
    private fun nomsAuPoint(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        contributionsScan: List<ContributionFichier>,
        index: VueAppelables?,
    ): List<PropositionKotlin> {
        val prefixe = prefixeAuPoint(document.arbre.noeud, deplacement)

        // Les noms DÉCLARÉS du document (garde documentaire — portées
        // locales comprises).
        val propositions = LinkedHashSet<String>()
        if (prefixe.isEmpty()) {
            return emptyList()
        }
        for (nom in nomsDeclares(document.arbre)) {
            if (nom.startsWith(prefixe) && nom != prefixe) propositions.add(nom)
        }

        // Les contributions du scan : les types du projet par nom simple.
        val batisseur = ModeleProjet.Batisseur()
        batisseur.ajouter(ModeleProjet.contributionKotlin(document.arbre, uri = ""))
        contributionsScan.forEach(batisseur::ajouter)
        val modele = batisseur.figer()
        for (type in modele.typesParNomSimple()) {
            if (type.startsWith(prefixe) && type != prefixe) propositions.add(type)
        }

        // Le catalogue KP8 (les types bien connus des paquets par défaut).
        for (nom in UniversPreuves.CATALOGUE_TYPES_DEFAUT) {
            if (nom.startsWith(prefixe) && nom != prefixe) propositions.add(nom)
        }

        // Les appelables de premier niveau de l'index (par préfixe).
        if (index != null) {
            for (nom in index.nomsTopParPrefixe(prefixe, LIMITE_INDEX)) {
                if (nom != prefixe) propositions.add(nom)
            }
        }
        return propositions.map(::PropositionKotlin)
    }

    /**
     * Le PRÉFIXE frappé au point : l'identifiant sous le caret (fin
     * comprise — le caret vit juste après les caractères frappés), tronqué
     * à la position du caret ; vide si le caret ne désigne pas un
     * identifiant en cours de frappe.
     */
    private fun prefixeAuPoint(racine: NoeudArbre, deplacement: Int): String {
        var trouve: String? = null
        fun marcher(noeud: NoeudArbre) {
            for (jeton in noeud.jetonsPropres) {
                if (jeton.genre == org.jetbrains.kotlin.kmp.lexer.KtTokens.IDENTIFIER &&
                    jeton.debut <= deplacement && deplacement <= jeton.fin
                ) {
                    val coupe = (deplacement - jeton.debut).coerceIn(0, jeton.texte.length)
                    trouve = jeton.texte.substring(0, coupe)
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve.orEmpty()
    }

    /** Les noms DÉCLARÉS du document (la garde documentaire, fiche 017). */
    private fun nomsDeclares(arbre: KtFichier): Set<String> {
        val noms = mutableSetOf<String>()
        fun collecter(noeud: NoeudArbre) {
            if (noeud.genre in UniversPreuves.GENRES_DECLARATION) {
                noeud.jetonsPropres
                    .firstOrNull { it.genre == org.jetbrains.kotlin.kmp.lexer.KtTokens.IDENTIFIER }?.texte
                    ?.let { noms.add(it) }
            }
            noeud.enfants.forEach(::collecter)
        }
        collecter(arbre.noeud)
        return noms
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

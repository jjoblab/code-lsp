// CodeIDE — PIPELINE de complétion (module :kotlin, étage 3 vague 3a —
// ADR 0113 : « la chaîne de classement de l'ADR 0012 est la spécification
// du pipeline de complétion Kotlin neuf » ; le pipeline CLASSIFIE le caret,
// collecte par régime, puis ARRANGE — classement en chaîne, gel des têtes,
// trim honnête). Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ArrangementListeKotlin
import jo.codeide.lsp.kotlin.api.ContexteClassementKotlin
import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.api.ReponseCompletionKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.resolve.ContributionFichier
import jo.codeide.lsp.kotlin.resolve.EtatScan
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import jo.codeide.lsp.kotlin.resolve.VueJvmMembres
import jo.codeide.lsp.kotlin.index.VueAppelables
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le pipeline de complétion (étage 3 — ADR 0113) : le germe de la vague 2a
 * (`CompletionMembres.proposer` monolithique) est devenu une CHAÎNE de
 * régimes pilotée par la classification du caret :
 *
 *  1. **CLASSIFIER** — [CompletionContextes.auPoint] décide du genre sur
 *     l'arbre seul (import, membre, nom) ;
 *  2. **SONDER LES CONTEXTES** — les contextuels v1 (fiche 043) se
 *     prouvent sur l'arbre ET la résolution, avant le régime membre ;
 *  3. **COLLECTER** — le régime ouvert rassemble ses candidates (chacun
 *     conservateur : un contexte non prouvable rend une liste vide,
 *     jamais une invention — D3) ;
 *  4. **ARRANGER** — classement par la CHAÎNE (peseurs dimension par
 *     dimension paresseusement), GEL des têtes (contrat « zéro saut de
 *     liste »), PLAFOND d'affichage honnête avec total réel.
 *
 * L'arrangement est À SESSION (les gels ne fuient pas entre sessions) ;
 * la chaîne est INJECTABLE (le banc de qualité compare la chaîne livrée à
 * la chaîne naïve alphabétique — ADR 0113/0119).
 */
internal object CompletionPipeline {

    /**
     * Propose la complétion ARRANGÉE au point demandé.
     *
     * @param document le document ouvert (texte + arbre) ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret ;
     * @param contributionsScan les contributions du scan (document exclu) ;
     * @param etatScan l'état du scan ;
     * @param index la vue des appelables du classpath (`null` = guichet
     * muet : les extensions classpath manquent, jamais de propositions
     * fausses) ;
     * @param vueJvm la vue JVM des membres du classpath (`null` = les
     * récepteurs de bibliothèque ne proposent rien — D3) ;
     * @param arrangement l'arrangement de la session (chaîne + gels).
     * @return la réponse arrangée (genre + propositions ≤ plafond + total
     * réel) ; jamais `null`, jamais d'exception.
     */
    internal fun proposer(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        contributionsScan: List<ContributionFichier>,
        etatScan: EtatScan,
        index: VueAppelables?,
        vueJvm: VueJvmMembres?,
        arrangement: ArrangementListeKotlin,
    ): ReponseCompletionKotlin {
        val arbre = document.arbre
        val contexte = CompletionContextes.auPoint(arbre, deplacement)

        // L'univers, la liaison de la RÉGION contenant le point et la pile
        // des this — les régimes les partagent (une seule construction).
        fun univers(): UniversPreuves {
            val batisseur = ModeleProjet.Batisseur()
            batisseur.ajouter(ModeleProjet.contributionKotlin(arbre, uri = ""))
            contributionsScan.forEach(batisseur::ajouter)
            return UniversPreuves(
                arbre = arbre,
                paquet = arbre.directivePackage()?.nomQualifie().orEmpty(),
                modele = batisseur.figer(),
                index = index,
                etatScan = etatScan,
                script = uri.endsWith(".kts"),
                vueJvm = vueJvm,
            )
        }

        // La collecte : (genre effectif, candidates) — les contextuels
        // courent AVANT le régime membre (un caret dans la liste
        // d'arguments d'un appel membre est une position d'ARGUMENT).
        val genreEffectif: GenreContexteKotlin
        val candidats: List<PropositionKotlin>
        if (contexte.genre == GenreContexteKotlin.IMPORT) {
            genreEffectif = GenreContexteKotlin.IMPORT
            candidats = CompletionImports.collecter(contexte.cheminImport.orEmpty(), vueJvm)
        } else {
            val univers = univers()
            val region = regionContenant(arbre.noeud, deplacement)
            val liaison = MarcheTypee.Liaison(arbre = arbre, univers = univers, region = region)
            val pile = jo.codeide.lsp.kotlin.resolve.Couches.aPosition(arbre, univers, liaison, deplacement)
            val contextuel = CompletionContextuelle.argumentsNommes(arbre, univers, liaison, pile, deplacement)
                ?: CompletionContextuelle.constantesEnum(arbre, univers, liaison, pile, deplacement)
                ?: CompletionContextuelle.attributsAnnotation(arbre, univers, deplacement)
            if (contextuel != null) {
                genreEffectif = contextuel.genre
                candidats = contextuel.propositions
            } else if (contexte.genre == GenreContexteKotlin.MEMBRE) {
                genreEffectif = GenreContexteKotlin.MEMBRE
                candidats = CompletionMembres.collecter(arbre, univers, liaison, deplacement)
            } else {
                genreEffectif = GenreContexteKotlin.NOM
                candidats = CompletionNoms.collecter(
                    arbre = arbre,
                    univers = univers,
                    liaison = liaison,
                    pile = pile,
                    deplacement = deplacement,
                    index = index,
                    vueJvm = vueJvm,
                )
            }
        }

        // L'ARRANGEMENT : classement en chaîne + gel des têtes + plafond
        // honnête. Le plafond suit le RÉGIME : l'espace de membre est BORNÉ
        // par la chaîne du récepteur (le re-filtrage local du client,
        // frontière RPC, y travaille sur l'espace complet) ; les espaces
        // non bornés (noms, imports) plafonnent à l'affichage.
        val limite = if (genreEffectif == GenreContexteKotlin.MEMBRE) {
            ArrangementListeKotlin.LIMITE_MEMBRE
        } else {
            ArrangementListeKotlin.LIMITE_AFFICHAGE
        }
        val arrangees = arrangement.arranger(
            cleContexte = cleContexte(uri, genreEffectif, arbre.noeud, deplacement),
            candidates = candidats,
            contexte = ContexteClassementKotlin(genre = genreEffectif, prefixe = contexte.prefixe),
            limite = limite,
        )
        return ReponseCompletionKotlin(
            genre = genreEffectif,
            propositions = arrangees.propositions,
            totalDisponible = arrangees.totalDisponible,
        )
    }

    /**
     * La CLÉ DE STABILITÉ du gel : document + genre + ANCRE — le début
     * (offset STABLE pendant la frappe) de la plus profonde production
     * ANCRANTE couvrant le caret (sélecteur qualifié, appel, `when`,
     * annotation, déclaration porteuse, directive d'import). Deux frappes
     * successives de la MÊME expression partagent la clé : le gel y
     * maintient les têtes à leur rang.
     *
     * @param uri l'identifiant du document ;
     * @param genre le genre effectif du contexte ;
     * @param racine la racine de l'arbre ;
     * @param deplacement l'offset du caret ;
     * @return la clé de contexte.
     */
    private fun cleContexte(uri: String, genre: GenreContexteKotlin, racine: NoeudArbre, deplacement: Int): String {
        var ancre = 0
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre in PRODUCTIONS_ANCRANTES) {
                ancre = noeud.debut
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return "$uri|${genre.name}|$ancre"
    }

    /** Les productions ANCRANTES du gel (leurs DÉBUTS sont stables pendant la frappe). */
    private val PRODUCTIONS_ANCRANTES: Set<com.intellij.platform.syntax.SyntaxElementType> = setOf(
        KtNodeTypes.DOT_QUALIFIED_EXPRESSION,
        KtNodeTypes.SAFE_ACCESS_EXPRESSION,
        KtNodeTypes.CALL_EXPRESSION,
        KtNodeTypes.WHEN,
        KtNodeTypes.ANNOTATION_ENTRY,
        KtNodeTypes.FUN,
        KtNodeTypes.PROPERTY,
        KtNodeTypes.IMPORT_DIRECTIVE,
    )

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
}

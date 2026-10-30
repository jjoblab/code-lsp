// CodeIDE — PIPELINE de complétion (module :kotlin, étage 3 vague 3a —
// ADR 0113 : « la chaîne de classement de l'ADR 0012 est la spécification
// du pipeline de complétion Kotlin neuf » ; le pipeline CLASSIFIE le caret,
// collecte par régime, puis ARRANGE honnêtement — plafond d'affichage avec
// total réel). Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.api.ReponseCompletionKotlin
import jo.codeide.lsp.kotlin.resolve.ContributionFichier
import jo.codeide.lsp.kotlin.resolve.EtatScan
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import jo.codeide.lsp.kotlin.resolve.VueJvmMembres
import jo.codeide.lsp.kotlin.index.VueAppelables
import jo.codeide.lsp.kotlin.parse.kt.KtFichier

/**
 * Le pipeline de complétion (étage 3 — ADR 0113) : le germe de la vague 2a
 * (`CompletionMembres.proposer` monolithique) devient une CHAÎNE de
 * régimes pilotée par la classification du caret :
 *
 *  1. **CLASSIFIER** — [CompletionContextes.auPoint] décide du genre sur
 *     l'arbre seul (import, membre, nom) ;
 *  2. **COLLECTER** — le régime ouvert rassemble ses candidats (chacun
 *     conservateur : un contexte non prouvable rend une liste vide,
 *     jamais une invention — D3) ;
 *  3. **ARRANGER** — ordre de rencontre puis PLAFOND D'AFFICHAGE honnête :
 *     la liste rendue est bornée à [LIMITE_AFFICHAGE] items, le TOTAL
 *     RÉEL voyage avec la réponse (le plafond ne ment jamais — miroir du
 *     `trimToHalfLimit` d'IntelliJ, ADR 0012).
 *
 * La chaîne de CLASSEMENT (peseurs dimension par dimension, gel des têtes)
 * se greffe à l'étape 3 en vague 3c — SANS toucher à la collecte.
 */
internal object CompletionPipeline {

    /**
     * Le plafond d'affichage de la liste rendue (miroir de la fiche 043
     * Java : « affichage plafonné à 120 items par dump »).
     */
    internal const val LIMITE_AFFICHAGE: Int = 120

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
    ): ReponseCompletionKotlin {
        val arbre = document.arbre
        val contexte = CompletionContextes.auPoint(arbre, deplacement)

        // L'univers, la liaison de la RÉGION contenant le point et la pile
        // des this — les régimes MEMBRE et NOM les partagent (miroir du
        // germe 2a : jamais deux constructions par requête).
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

        val candidats: List<PropositionKotlin> = when (contexte.genre) {
            GenreContexteKotlin.IMPORT ->
                CompletionImports.collecter(contexte.cheminImport.orEmpty(), vueJvm)

            else -> {
                // Les CONTEXTES CONTEXTUELS v1 (fiche 043 — vague 3b) se
                // sondent AVANT le régime membre : un caret dans la LISTE
                // D'ARGUMENTS d'un appel membre (`m.copier(|`) est une
                // position d'ARGUMENT, pas de sélecteur. Chaque contexte
                // est PROUVÉ sur l'arbre ET la résolution (appel résolu,
                // sujet enum typé, annotation résolue) — un contexte non
                // prouvable rend `null` et la suite reprend son cours.
                val univers = univers()
                val region = regionContenant(arbre.noeud, deplacement)
                val liaison = MarcheTypee.Liaison(arbre = arbre, univers = univers, region = region)
                val pile = jo.codeide.lsp.kotlin.resolve.Couches.aPosition(arbre, univers, liaison, deplacement)
                val contextuel = CompletionContextuelle.argumentsNommes(arbre, univers, liaison, pile, deplacement)
                    ?: CompletionContextuelle.constantesEnum(arbre, univers, liaison, pile, deplacement)
                    ?: CompletionContextuelle.attributsAnnotation(arbre, univers, deplacement)
                if (contextuel != null) {
                    return ReponseCompletionKotlin(
                        genre = contextuel.genre,
                        propositions = contextuel.propositions,
                        totalDisponible = contextuel.propositions.size,
                    )
                }
                if (contexte.genre == GenreContexteKotlin.MEMBRE) {
                    CompletionMembres.collecter(arbre, univers, liaison, deplacement)
                } else {
                    CompletionNoms.collecter(
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
        }

        // L'ARRANGEMENT (3a : ordre de rencontre, total réel honnête — le
        // PLAFOND d'affichage s'appliquera AVEC le classement en 3c, sur
        // la liste ORDONNÉE, jamais sur l'ordre de rencontre qui
        // tronquerait arbitrairement : miroir du `trimToHalfLimit`
        // d'IntelliJ, ADR 0012/0113).
        return ReponseCompletionKotlin(
            genre = contexte.genre,
            propositions = candidats,
            totalDisponible = candidats.size,
        )
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
    private fun regionContenant(racine: jo.codeide.lsp.kotlin.parse.NoeudArbre, deplacement: Int): jo.codeide.lsp.kotlin.parse.NoeudArbre? {
        var trouve: jo.codeide.lsp.kotlin.parse.NoeudArbre? = null
        fun marcher(noeud: jo.codeide.lsp.kotlin.parse.NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == org.jetbrains.kotlin.kmp.parser.KtNodeTypes.FUN ||
                noeud.genre == org.jetbrains.kotlin.kmp.parser.KtNodeTypes.PROPERTY
            ) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }
}

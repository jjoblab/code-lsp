// CodeIDE — previews Compose d'un document (module :kotlin, zone preview
// — REPRISE VOIE C, manifeste docs/PROVENANCE-REPRISE-KOTLIN.md §3 R3).
// Origine : codeide-lsp
// lsp/kotlin/src/main/kotlin/jo/lspkotlin/preview/PreviewsDocumentKotlin.kt
// @ bc11253 (auteur jjoblab, dépôt propriétaire) — contrôle R9.4 PASSÉ
// (en-têtes sans mention tierce ; re-contrôle au moment de la copie,
// vague 5c). Adaptations de câblage SEULEMENT, listées ici : paquet
// jo.codeide.lsp.kotlin.preview ; le SPI est le modèle NEUF
// `api/PreviewsKotlin.kt` (l'ancien `api/` est zone NOYAU en lecture
// interdite — réécrit cahier en main, fiche 050) ; la façade est la
// neuve `parse.kt.*` (KtFichier/KtClasse/KtFonction/KtAnnotationEntree
// — nom(), arguments(), parametres(), membres()) ; les extensions
// d'expression de l'ancienne façade (nomAppele, genreLitteral,
// recepteurQualifie, selecteurQualifie — zone noyau jamais lue) sont
// réécrites ici en helpers PRIVÉS depuis le contrat de la fiche 050 ;
// la preuve FQCN d'un nom d'annotation (fqcnDuNomEcrit — l'ancienne
// vivait dans le resolveur noyau, jamais lu) est réécrite ici depuis
// sa sémantique documentée (imports exacts, jokers de paquets PROJET,
// unicité projet) sur le modèle neuf `resolve.ModeleProjet` ; les
// déclarations du projet sont les `AppelableProjet` du modèle neuf
// (étendus de estFonction/debutNom pour la reprise — frontière
// consignée au manifeste). Le cœur (preuve FQCN des annotations,
// réduction du corps en arbre d'appels, inlining borné cycles/budgets,
// paramètres tranchés, lignes d'annotation) est repris TEL QUEL.
package jo.codeide.lsp.kotlin.preview

import jo.codeide.lsp.kotlin.api.AppelFonctionKotlin
import jo.codeide.lsp.kotlin.api.ArgumentAppelKotlin
import jo.codeide.lsp.kotlin.api.ExpressionNonInterpreteeKotlin
import jo.codeide.lsp.kotlin.api.GenreArgumentAppelKotlin
import jo.codeide.lsp.kotlin.api.GenreInlineKotlin
import jo.codeide.lsp.kotlin.api.NoeudAppelKotlin
import jo.codeide.lsp.kotlin.api.ParametrePreviewKotlin
import jo.codeide.lsp.kotlin.api.PreviewKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtClasse
import jo.codeide.lsp.kotlin.parse.kt.KtDeclaration
import jo.codeide.lsp.kotlin.parse.kt.KtDirectiveImport
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.KtFonction
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.AppelableProjet
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.resolve.nomEffectifImport
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Collecte des fonctions `@Preview @Composable` d'un document (preview
 * Compose C1) — la session pioche le document courant et délègue TOUTE
 * la marche ici : preuve FQCN des annotations, lecture des paramètres
 * `@Preview`, réduction du corps en ARBRE D'APPELS NEUTRE, inlining
 * des composables du projet (cycles + budgets).
 *
 * Garde-fous (PC4 côté moteur) : budget TOTAL de nœuds [BUDGET_NOEUDS]
 * et profondeur d'inline [PROFONDEUR_INLINE] — une marche qui touche un
 * plafond est TRONQUÉE et signalée ([PreviewKotlin.coupureBudget]),
 * jamais silencieuse (PC1 : jamais un faux rendu).
 *
 * Conservateur (D3) : annotation non résolue par les imports = pas de
 * preview ; composable du projet non prouvé = pas d'inline (le rendu
 * en tire une erreur typée) ; corps absent = preview SANS corps.
 */
internal object PreviewsDocumentKotlin {
    /** FQCN de l'annotation `@Preview` (ui-tooling-preview). */
    private const val FQCN_PREVIEW: String = "androidx.compose.ui.tooling.preview.Preview"

    /** FQCN de l'annotation `@Composable` (runtime). */
    private const val FQCN_COMPOSABLE: String = "androidx.compose.runtime.Composable"

    /** Budget TOTAL de nœuds rendus par document (garde-fou PC4). */
    internal const val BUDGET_NOEUDS: Int = 500

    /** Profondeur MAXIMALE d'inline composable-projet (A → B → C…). */
    internal const val PROFONDEUR_INLINE: Int = 6

    /** Troncature du texte d'une expression non interprétée. */
    private const val LONGUEUR_EXPRESSION: Int = 120

    /** Troncature du nom de repli d'un appel en édition. */
    private const val LONGUEUR_NOM_REPLI: Int = 32

    /**
     * Le contexte de nommage d'UN fichier (adaptation de câblage :
     * l'ancien `ResolutionKotlin.ContexteNom` vivait dans le noyau en
     * lecture interdite — la sémantique conservée est celle documentée
     * par la fiche 050 : le package courant et les imports du fichier).
     *
     * @property paquet le package du fichier (`""` si aucun) ;
     * @property imports les directives d'import du fichier, en ordre
     * d'écriture.
     */
    internal class ContexteNom internal constructor(
        internal val paquet: String,
        internal val imports: List<KtDirectiveImport>,
    )

    /**
     * Accès de la collecte au RESTE du projet : arbre + texte d'un
     * fichier QUELCONQUE (document ouvert d'abord, fichier du scan
     * ensuite) et contexte de nommage d'un arbre — implémenté par la
     * session, qui possède les documents et le scan partagé.
     */
    internal interface Port {
        /** Arbre + texte du fichier [uri], ou null (illisible/inconnu). */
        fun fichierDe(uri: String): Pair<KtFichier, CharSequence>?

        /** Contexte de nommage (imports, paquet) du fichier donné. */
        fun contexteNomDe(fichier: KtFichier): ContexteNom
    }

    /**
     * Le document à parcourir : son identité, son texte, son contexte
     * de nommage, le modèle de symboles du projet, les appelables du
     * PROJET (document + scan) et le port vers les autres fichiers.
     */
    internal class Contexte(
        internal val uri: String,
        internal val texte: CharSequence,
        internal val contexteNom: ContexteNom,
        internal val modele: ModeleProjet,
        internal val declarationsProjet: List<AppelableProjet>,
        internal val port: Port,
    )

    /**
     * Collecte les previews du document, dans l'ordre du document
     * (fonctions top-level puis membres de classes, profondeur
     * d'abord). Le budget est PARTAGÉ par toutes les previews du
     * fichier ; une fonction SANS preview n'en consomme AUCUN (la
     * marche du corps ne démarre que pour une preview retenue). La
     * fonction RETENUE est SEMÉE dans la pile d'inline AVANT la marche
     * de son corps — une auto-récursion (`Apercu()` s'appelant) est un
     * CYCLE typé, jamais une boucle infinie (garde-fou PC4).
     */
    internal fun collecter(
        contexte: Contexte,
        racine: NoeudArbre,
        paquet: String?,
    ): List<PreviewKotlin> {
        val etat = Etat(BUDGET_NOEUDS)
        val previews = mutableListOf<PreviewKotlin>()
        val fonctions = fonctionsDe(KtFichier(racine, contexte.texte))
        var indice = 0
        while (indice < fonctions.size && etat.budget > 0) {
            Marche(contexte, etat)
                .previewDe(fonctions[indice], paquet)
                ?.let { previews.add(it) }
            indice++
        }
        return previews
    }

    /** Fonctions du fichier : top-level, puis membres de chaque classe. */
    private fun fonctionsDe(fichier: KtFichier): List<KtFonction> =
        fichier.declarations().flatMap { declaration ->
            when (declaration) {
                is KtFonction -> listOf(declaration)
                is KtClasse -> declaration.membres().filterIsInstance<KtFonction>()
                else -> emptyList()
            }
        }

    /**
     * FQCN d'un nom d'annotation ÉCRIT (adaptation de câblage :
     * l'ancien `fqcnDuNomEcrit` vivait dans le résolveur noyau, jamais
     * lu — réécrit depuis sa sémantique documentée) : un nom DÉJÀ
     * QUALIFIÉ (`@androidx.compose.runtime.Composable`) EST le FQCN tel
     * qu'écrit — comparaison directe ; un nom COURT passe par les
     * imports EXACTS du fichier (alias compris), puis les imports
     * JOKERS (le nom doit être un TYPE DU PROJET sous le paquet importé
     * — un joker de paquet hors projet ne prouve RIEN), puis l'unicité
     * projet (paquet courant d'abord). Conservateur, null si non résolu
     * (D3).
     */
    private fun fqcnAnnotation(
        nomEcrit: String?,
        contexteNom: ContexteNom,
        modele: ModeleProjet,
    ): String? {
        if (nomEcrit == null) return null
        if ('.' in nomEcrit) return nomEcrit
        for (directive in contexteNom.imports) {
            if (directive.estJoker()) continue
            if (directive.nomEffectifImport() == nomEcrit) return directive.nomQualifie()
        }
        for (directive in contexteNom.imports) {
            if (!directive.estJoker()) continue
            val paquetJoker = directive.nomQualifie() ?: continue
            modele.type("$paquetJoker.$nomEcrit")?.let { return it.fqcn }
        }
        modele.typeParNomSimple(nomEcrit, contexteNom.paquet)?.let { return it.fqcn }
        return null
    }

    /** Le BLOCK d'un lambda final (LAMBDA_ARGUMENT → … → BLOCK). */
    private fun blocDeLambda(lambdaArgument: NoeudArbre): NoeudArbre? {
        val portee =
            lambdaArgument.enfants.firstOrNull { it.genre == KtNodeTypes.LAMBDA_EXPRESSION }
                ?: lambdaArgument.enfants.firstOrNull { it.genre == KtNodeTypes.FUNCTION_LITERAL }
        val litteral =
            portee?.enfants?.firstOrNull { it.genre == KtNodeTypes.FUNCTION_LITERAL } ?: portee
        val conteneur = litteral ?: lambdaArgument
        return conteneur.enfants.firstOrNull { it.genre == KtNodeTypes.BLOCK }
    }

    /** La fonction du fichier dont le NOM commence à [debutNom]. */
    private fun fonctionANom(
        racine: NoeudArbre,
        texte: CharSequence,
        debutNom: Int,
    ): KtFonction? =
        fonctionsDe(KtFichier(racine, texte)).firstOrNull { fonction ->
            jetonNom(fonction)?.debut == debutNom
        }

    /** Le jeton NOM d'une déclaration (premier identifiant propre). */
    private fun jetonNom(declaration: KtDeclaration): jo.codeide.lsp.kotlin.parse.JetonBrut? =
        declaration.noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }

    /** État MUTABLE partagé par les marches du document (budget + cycles). */
    private class Etat(
        var budget: Int,
        /** Chaîne d'inline en cours : « uri#nom » du composable visité. */
        val inlineEnCours: LinkedHashSet<String> = LinkedHashSet(),
    )

    /**
     * Marche d'UN fichier (le document, ou le fichier d'un composable
     * inliné) : tranche son texte, résout SES annotations, déduit du
     * budget PARTAGÉ [etat] et déclare ses cycles dans la pile commune.
     *
     * Exemption detekt ciblée (règle 16) : TooManyFunctions — la marche
     * épouse les PRODUCTIONS du parseur (preview, bloc, instruction,
     * appel, argument, valeur, inline ×3) : le compte épouse le
     * CONTRAT C1, pas une dérive.
     */
    @Suppress("TooManyFunctions")
    private class Marche(
        private val contexte: Contexte,
        private val etat: Etat,
    ) {
        /**
         * La preview d'une fonction, ou null (pas `@Preview @Composable`).
         *
         * Exemption detekt ciblée (règle 16) : ReturnCount — nom absent,
         * preuve d'annotation absente, rendu final : TROIS issues
         * distinctes du même contrat.
         */
        @Suppress("ReturnCount")
        fun previewDe(
            fonction: KtFonction,
            paquet: String?,
        ): PreviewKotlin? {
            val nom = fonction.nom() ?: return null
            val annotationPreview = annotationPreviewDe(fonction) ?: return null
            val nomJeton = jetonNom(fonction)
            val blocCorps = blocCorps(fonction)
            // Ligne 1-indexée de l'entrée d'annotation `@Preview` (le
            // marqueur de gouttière vit SUR cette ligne — écho picker de
            // la référence ; 0 si la plage est inconnue, le consommateur
            // renonce honnêtement à placer un marqueur).
            val ligneAnnotation = ligneDe(annotationPreview.noeud.debut)
            // La fonction racine est SEMÉE dans la pile d'inline : son
            // propre rappel (direct ou via un composable inliné) est un
            // CYCLE typé — jamais une récursion infinie (PC4).
            etat.inlineEnCours.add("${contexte.uri}#$nom")
            val corps = blocCorps?.let { marcherBloc(it) } ?: emptyList()
            etat.inlineEnCours.remove("${contexte.uri}#$nom")
            return PreviewKotlin(
                nom = nom,
                paquet = paquet,
                plageNom = PlageTexte(
                    debut = nomJeton?.debut ?: fonction.noeud.debut,
                    fin = nomJeton?.fin ?: fonction.noeud.debut,
                ),
                parametres =
                    annotationPreview.arguments().map { argument ->
                        ParametrePreviewKotlin(
                            nom = argument.nom(),
                            valeurTexte = argument.plageValeur()?.let { tranche(it.debut, it.fin) },
                        )
                    },
                corps = corps,
                coupureBudget = etat.budget <= 0,
                ligneAnnotation = ligneAnnotation,
            )
        }

        /**
         * L'annotation `@Preview` de la fonction, À CONDITION qu'elle
         * soit aussi `@Composable` (les DEUX preuves par FQCN, imports
         * du document) — sinon null (conservateur D3).
         */
        private fun annotationPreviewDe(fonction: KtFonction): jo.codeide.lsp.kotlin.parse.kt.KtAnnotationEntree? {
            val annotations = fonction.annotations()
            val composable =
                annotations.any { fqcnDe(it.nom()) == FQCN_COMPOSABLE }
            val preview =
                annotations.firstOrNull { fqcnDe(it.nom()) == FQCN_PREVIEW }
            return if (composable) preview else null
        }

        /**
         * FQCN d'un nom d'annotation ÉCRIT du fichier COURANT — noms
         * qualifiés comparés tels quels, noms courts par les imports
         * ([fqcnAnnotation]) ; conservateur, null si non résolu (D3).
         */
        private fun fqcnDe(nomEcrit: String?): String? =
            fqcnAnnotation(nomEcrit, contexte.contexteNom, contexte.modele)

        /** Texte tranché dans la source (null si plage vide/inverse). */
        private fun tranche(
            debut: Int,
            fin: Int,
        ): String? {
            if (debut < 0 || fin > contexte.texte.length || debut >= fin) return null
            return contexte.texte.substring(debut, fin)
        }

        /** Ligne 1-indexée d'un décalage. */
        private fun ligneDe(decalage: Int): Int {
            var ligne = 1
            for (i in 0 until decalage.coerceAtMost(contexte.texte.length)) {
                if (contexte.texte[i] == '\n') ligne++
            }
            return ligne
        }

        /**
         * Marche d'un BLOC (corps, lambda final) : chaque instruction
         * directe devient appel interprétable ou expression non
         * interprétée honnête. Le budget épuisé coupe la marche.
         *
         * Exemption detekt ciblée (règle 16) :
         * LoopWithTooManyJumpStatements — la coupe de budget (break) et
         * l'instruction ignorée (continue) sont les DEUX bornes du
         * parcours.
         */
        @Suppress("LoopWithTooManyJumpStatements")
        fun marcherBloc(bloc: NoeudArbre): List<NoeudAppelKotlin> {
            val noeuds = mutableListOf<NoeudAppelKotlin>()
            for (enfant in bloc.enfants) {
                if (etat.budget <= 0) break
                val noeud = instructionDe(enfant) ?: continue
                noeuds.add(noeud)
            }
            return noeuds
        }

        /** L'instruction directe d'un bloc, ou null si à ignorer. */
        private fun instructionDe(enfant: NoeudArbre): NoeudAppelKotlin? =
            when (enfant.genre) {
                KtNodeTypes.CALL_EXPRESSION -> marcherAppel(enfant)
                KtNodeTypes.PROPERTY,
                KtNodeTypes.FUNCTION_LITERAL, KtNodeTypes.IF, KtNodeTypes.WHEN,
                KtNodeTypes.FOR, KtNodeTypes.WHILE, KtNodeTypes.DO_WHILE,
                KtNodeTypes.TRY,
                -> expressionNonInterpretee(enfant)

                else ->
                    if (estExpressionSimple(enfant)) {
                        expressionNonInterpretee(enfant)
                    } else {
                        null
                    }
            }

        /** Une expression non appel, rendue honnêtement (erreur typée). */
        private fun expressionNonInterpretee(noeud: NoeudArbre): NoeudAppelKotlin {
            etat.budget--
            val brut = tranche(noeud.debut, noeud.fin) ?: ""
            return ExpressionNonInterpreteeKotlin(
                texte = brut.replace('\n', ' ').take(LONGUEUR_EXPRESSION),
                ligne = ligneDe(noeud.debut),
            )
        }

        /** L'expression est-elle du TEXTE interprétable à trancher ? */
        private fun estExpressionSimple(noeud: NoeudArbre): Boolean =
            noeud.genre in TYPES_EXPRESSION_NON_APPEL

        /**
         * Marche d'un APPEL : arguments explicites (nommés/positionnels,
         * littéraux distingués), contenu du LAMBDA FINAL, inlining du
         * composable du PROJET le cas échéant (le genre final reflète le
         * résultat de la tentative — jamais de faux inline).
         */
        private fun marcherAppel(appel: NoeudArbre): NoeudAppelKotlin {
            etat.budget--
            val nom = nomAppele(appel)
            val callee = appel.enfants.firstOrNull()
            val recepteur =
                if (callee != null &&
                    (callee.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION ||
                        callee.genre == KtNodeTypes.SAFE_ACCESS_EXPRESSION)
                ) {
                    recepteurQualifie(callee)?.let { tranche(it.debut, it.fin) }
                } else {
                    null
                }
            val arguments = marcherArguments(appel)
            val blocFinal =
                appel.enfants
                    .firstOrNull { it.genre == KtNodeTypes.LAMBDA_ARGUMENT }
                    ?.let { blocDeLambda(it) }
            val contenuLambda = blocFinal?.let { marcherBloc(it) } ?: emptyList()
            val (genreInline, contenu) = inlineProjet(nom, recepteur, contenuLambda)
            return AppelFonctionKotlin(
                nom = nom ?: tranche(appel.debut, appel.fin)?.take(LONGUEUR_NOM_REPLI) ?: "",
                recepteur = recepteur,
                ligne = ligneDe(appel.debut),
                arguments = arguments,
                contenu = contenu,
                genreInline = genreInline,
            )
        }

        /** Arguments explicites de l'appel (la liste entre parenthèses). */
        private fun marcherArguments(appel: NoeudArbre): List<ArgumentAppelKotlin> {
            val liste =
                appel.enfants.firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST }
                    ?: return emptyList()
            return liste.enfants
                .filter { it.genre == KtNodeTypes.VALUE_ARGUMENT }
                .mapNotNull(::argumentDe)
        }

        /** Un argument de l'appel, ou null (valeur absente — édition). */
        private fun argumentDe(argument: NoeudArbre): ArgumentAppelKotlin? {
            val nom =
                argument.enfants
                    .firstOrNull { enfant -> enfant.genre == KtNodeTypes.VALUE_ARGUMENT_NAME }
                    ?.let { jetonsDescendants(it) }
                    ?.firstOrNull { it.genre == KtTokens.IDENTIFIER }
                    ?.texte
            val valeur =
                argument.enfants.firstOrNull { enfant ->
                    enfant.genre != KtNodeTypes.VALUE_ARGUMENT_NAME
                } ?: return null
            return ArgumentAppelKotlin(
                nom = nom,
                genre = genreDeValeur(valeur),
                texte = tranche(valeur.debut, valeur.fin),
            )
        }

        /** Le genre d'interprétation de la VALEUR d'un argument. */
        private fun genreDeValeur(valeur: NoeudArbre): GenreArgumentAppelKotlin =
            when {
                genreLitteral(valeur) != null -> GenreArgumentAppelKotlin.LITTERAL
                valeur.genre == KtNodeTypes.REFERENCE_EXPRESSION -> GenreArgumentAppelKotlin.REFERENCE
                (valeur.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION ||
                    valeur.genre == KtNodeTypes.SAFE_ACCESS_EXPRESSION) ->
                    // a.b : référence ; a.b() : APPEL qualifié — AUTRE.
                    if (selecteurQualifie(valeur)?.genre == KtNodeTypes.CALL_EXPRESSION) {
                        GenreArgumentAppelKotlin.AUTRE
                    } else {
                        GenreArgumentAppelKotlin.REFERENCE
                    }

                else -> GenreArgumentAppelKotlin.AUTRE
            }

        /**
         * Tentative d'inline d'un appel vers un composable du PROJET :
         * candidate top-level homonyme SANS récepteur, prouvée
         * `@Composable` par les imports de SON PROPRE fichier, SANS
         * paramètres (C1 : les arguments ne traversent pas l'inline).
         *
         * Exemption detekt ciblée (règle 16) : ReturnCount — les gardes
         * successives (appel non éligible, candidate absente, CYCLE,
         * BUDGET, délégation) sont des ISSUES DISTINCTES du même
         * garde-fou, pas une dérive.
         */
        @Suppress("ReturnCount")
        private fun inlineProjet(
            nom: String?,
            recepteur: String?,
            contenuLambda: List<NoeudAppelKotlin>,
        ): Pair<GenreInlineKotlin, List<NoeudAppelKotlin>> {
            if (nom == null || recepteur != null) return GenreInlineKotlin.AUCUN to contenuLambda
            val candidate = candidateProjet(nom) ?: return GenreInlineKotlin.AUCUN to contenuLambda
            val cle = "${candidate.uri}#${candidate.nom}"
            if (cle in etat.inlineEnCours) return GenreInlineKotlin.PROJET_CYCLE to contenuLambda
            if (etat.inlineEnCours.size >= PROFONDEUR_INLINE || etat.budget <= 0) {
                return GenreInlineKotlin.PROJET_BUDGET to contenuLambda
            }
            return ouvrirCandidate(candidate, contenuLambda)
        }

        /** La candidate top-level du projet pour un appel simple, ou null. */
        private fun candidateProjet(nom: String): AppelableProjet? =
            contexte.declarationsProjet.firstOrNull { declaration ->
                declaration.estFonction &&
                    declaration.recepteurNomme == null &&
                    declaration.nom == nom
            }

        /**
         * Ouverture + preuve + marche du corps de la candidate — le
         * genre rendu reflète le résultat : [GenreInlineKotlin.PROJET_INLINE] avec le
         * corps marché en contenu, ou un genre d'échec TYPIQUE. Une
         * candidate qui n'est PAS composable retombe à [GenreInlineKotlin.AUCUN] (appel
         * inconnu du rendu — l'inline ne concerne que les COMPOSABLES).
         *
         * Exemption detekt ciblée (règle 16) : ReturnCount — les issues
         * (illisible ×3, non-composable, paramétré) sont DISTINCTES.
         */
        @Suppress("ReturnCount")
        private fun ouvrirCandidate(
            candidate: AppelableProjet,
            contenuLambda: List<NoeudAppelKotlin>,
        ): Pair<GenreInlineKotlin, List<NoeudAppelKotlin>> {
            val paire = contexte.port.fichierDe(candidate.uri)
                ?: return GenreInlineKotlin.PROJET_ILLISIBLE to contenuLambda
            val (arbreCible, texteCible) = paire
            val fonctionCible = fonctionANom(arbreCible.noeud, texteCible, candidate.debutNom)
                ?: return GenreInlineKotlin.PROJET_ILLISIBLE to contenuLambda
            val contexteCible = contexte.port.contexteNomDe(arbreCible)
            val composable =
                fonctionCible.annotations().any { annotation ->
                    fqcnAnnotation(
                        annotation.nom(),
                        contexteCible,
                        contexte.modele,
                    ) == FQCN_COMPOSABLE
                }
            if (!composable) return GenreInlineKotlin.AUCUN to contenuLambda
            if (fonctionCible.parametres().isNotEmpty()) {
                return GenreInlineKotlin.PROJET_PARAMETRES to contenuLambda
            }
            val blocCible = blocCorps(fonctionCible)
                ?: return GenreInlineKotlin.PROJET_ILLISIBLE to contenuLambda
            val cle = "${candidate.uri}#${candidate.nom}"
            etat.inlineEnCours.add(cle)
            val marcheCible =
                Marche(
                    Contexte(
                        uri = candidate.uri,
                        texte = texteCible,
                        contexteNom = contexteCible,
                        modele = contexte.modele,
                        declarationsProjet = contexte.declarationsProjet,
                        port = contexte.port,
                    ),
                    etat,
                )
            val noeuds = marcheCible.marcherBloc(blocCible)
            etat.inlineEnCours.remove(cle)
            return GenreInlineKotlin.PROJET_INLINE to noeuds
        }
    }

    // ------------------------------------------------------------------
    // Helpers d'expression (adaptation de câblage : les extensions de
    // l'ancienne façade parse.kt — nomAppele, genreLitteral,
    // recepteurQualifie, selecteurQualifie — vivaient dans le NOYAU en
    // lecture interdite ; réécrites ici depuis le contrat de la fiche
    // 050, mêmes sémantiques).
    // ------------------------------------------------------------------

    /** Le BLOCK corps d'une fonction (corps de bloc `{ … }`), ou null. */
    private fun blocCorps(fonction: KtFonction): NoeudArbre? =
        fonction.noeud.enfants.firstOrNull { it.genre == KtNodeTypes.BLOCK }

    /**
     * Le NOM APPELÉ d'un `CALL_EXPRESSION` : la référence simple de son
     * callee (`Text(…)` → `Text`) ou le SÉLECTEUR d'un callee qualifié
     * (`Modifier.fillMaxWidth()` → `fillMaxWidth`).
     */
    private fun nomAppele(appel: NoeudArbre): String? {
        val callee = appel.enfants.firstOrNull() ?: return null
        return when (callee.genre) {
            KtNodeTypes.REFERENCE_EXPRESSION ->
                callee.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte

            KtNodeTypes.DOT_QUALIFIED_EXPRESSION,
            KtNodeTypes.SAFE_ACCESS_EXPRESSION,
            -> selecteurQualifie(callee)?.let { jetonsDescendants(it) }
                .orEmpty()
                .lastOrNull { it.genre == KtTokens.IDENTIFIER }
                ?.texte

            else -> null
        }
    }

    /** Le RÉCEPTEUR d'une expression qualifiée (son premier enfant). */
    private fun recepteurQualifie(qualifiee: NoeudArbre): NoeudArbre? =
        qualifiee.enfants.firstOrNull()

    /** Le SÉLECTEUR d'une expression qualifiée (son second enfant). */
    private fun selecteurQualifie(qualifiee: NoeudArbre): NoeudArbre? =
        qualifiee.enfants.drop(1).firstOrNull()

    /**
     * Le genre LITTÉRAL d'une valeur (chaîne, nombre, booléen,
     * caractère) — `null` si la valeur n'est pas un littéral.
     */
    private fun genreLitteral(valeur: NoeudArbre): String? =
        when (valeur.genre) {
            KtNodeTypes.STRING_TEMPLATE,
            KtNodeTypes.INTEGER_CONSTANT,
            KtNodeTypes.FLOAT_CONSTANT,
            KtNodeTypes.BOOLEAN_CONSTANT,
            KtNodeTypes.CHARACTER_CONSTANT,
            -> valeur.genre.toString()

            else -> null
        }

    /**
     * Les jetons SIGNIFICATIFS du sous-arbre en ordre du document (les
     * jetons propres du nœud vivent AUTOUR de ceux de ses enfants — la
     * fusion se trie par position).
     */
    private fun jetonsDescendants(noeud: NoeudArbre): List<jo.codeide.lsp.kotlin.parse.JetonBrut> {
        val jetons = mutableListOf<jo.codeide.lsp.kotlin.parse.JetonBrut>()
        jetons.addAll(noeud.jetonsPropres)
        for (enfant in noeud.enfants) {
            jetons.addAll(jetonsDescendants(enfant))
        }
        return jetons.sortedBy { it.debut }
    }

    /** Les expressions non-appel qui se tranchent honnêtement (C1). */
    private val TYPES_EXPRESSION_NON_APPEL: Set<com.intellij.platform.syntax.SyntaxElementType> =
        setOf(
            KtNodeTypes.REFERENCE_EXPRESSION,
            KtNodeTypes.DOT_QUALIFIED_EXPRESSION,
            KtNodeTypes.SAFE_ACCESS_EXPRESSION,
            KtNodeTypes.BINARY_EXPRESSION,
            KtNodeTypes.BINARY_WITH_TYPE,
            KtNodeTypes.PREFIX_EXPRESSION,
            KtNodeTypes.POSTFIX_EXPRESSION,
            KtNodeTypes.STRING_TEMPLATE,
            KtNodeTypes.INTEGER_CONSTANT,
            KtNodeTypes.FLOAT_CONSTANT,
            KtNodeTypes.BOOLEAN_CONSTANT,
            KtNodeTypes.RETURN,
            KtNodeTypes.OBJECT_LITERAL,
            KtNodeTypes.ANNOTATED_EXPRESSION,
            KtNodeTypes.DESTRUCTURING_DECLARATION,
            KtNodeTypes.IS_EXPRESSION,
            KtNodeTypes.LAMBDA_EXPRESSION,
        )
}

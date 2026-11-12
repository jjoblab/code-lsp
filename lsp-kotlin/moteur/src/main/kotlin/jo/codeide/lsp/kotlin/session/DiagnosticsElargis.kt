// CodeIDE — diagnostics ÉLARGIS par décidabilité (module :kotlin, vague
// V-C — ADR 0125 : les familles SANS inférence d'abord — `when` non
// exhaustif sur enum, appel `suspend` hors contexte — puis la contrainte
// simple de types projet). Code NEUF jjoblab (clean-room — bornologie :
// chaque preuve se décide sur l'arbre + le modèle ; `@Suppress` est SERVI
// (GK-021) : une annotation `@Suppress(code)` sur une déclaration
// englobante éteint le code dans SA région).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.DiagnosticKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.GenreTypeProjet
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens

/**
 * Les familles ÉLARGIES (V-C) de la passe des diagnostics — appelée sous
 * la garde par déclaration (miroir de [DiagnosticsTypage]) :
 *
 * - `kt.whenNonExhaustif` : un `when` utilisé en EXPRESSION (initialiseur
 *   de propriété, `return`, argument) sur un sujet ENUM TYPÉ (projet ou
 *   classpath binaire — superclasse `java.lang.Enum`) dont les entrées ne
 *   sont pas toutes couvertes et sans branche `else` ;
 * - `kt.suspendHorsContexte` : l'appel nu d'une fonction `suspend`
 *   PROUVÉE du projet (le modificateur est écrit) hors d'une fonction
 *   `suspend` englobante.
 */
internal object DiagnosticsElargis {

    /**
     * Calcule les diagnostics élargis d'une région propre.
     *
     * @param region la déclaration propre visitée ;
     * @param univers l'univers des preuves ;
     * @param suspendEnglobant la déclaration englobante est-elle `suspend` ;
     * @param diagnostics l'accumulateur des émissions.
     */
    internal fun verifier(
        region: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        suspendEnglobant: Boolean,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        val suspendLocal = suspendEnglobant || porteurSuspend(region)
        marcher(region, univers, liaison, suspendLocal, diagnostics)
    }

    /** La marche des deux familles (mutuellement indépendantes). */
    private fun marcher(
        noeud: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        suspendEnglobant: Boolean,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        when (noeud.genre) {
            KtNodeTypes.WHEN -> verifierWhen(noeud, univers, liaison, diagnostics)
            KtNodeTypes.CALL_EXPRESSION -> verifierSuspend(noeud, univers, suspendEnglobant, diagnostics)
            else -> Unit
        }
        for (enfant in noeud.enfants) {
            // Une fonction LOCALE `suspend` rouvre le contexte dans son corps.
            val enfantSuspend = if (enfant.genre == KtNodeTypes.FUN) {
                suspendEnglobant || porteurSuspend(enfant)
            } else {
                suspendEnglobant
            }
            marcher(enfant, univers, liaison, enfantSuspend, diagnostics)
        }
    }

    // ── kt.whenNonExhaustif ─────────────────────────────────────────────

    /**
     * La preuve de NON-EXHAUSTIVITÉ : un `when` EN EXPRESSION (son parent
     * est un initialisateur, un `return` ou un argument — un `when`
     * INSTRUCTION n'a pas à l'être) sur un sujet ENUM TYPÉ dont les
     * entrées ne sont pas toutes couvertes, sans `else`.
     */
    private fun verifierWhen(
        quand: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        // Le sujet : premier enfant hors entrées — typé ENUM (projet :
        // genre ENUM ; classpath : superclasse binaire java.lang.Enum).
        val sujet = quand.enfants.firstOrNull { it.genre != KtNodeTypes.WHEN_ENTRY } ?: return
        val typeSujet = TypageBorne.typeDeLexpression(sujet, univers, liaison) ?: return
        val entrees = entreesEnum(typeSujet, univers) ?: return

        // Les conditions couvertes : les références aux ENTRÉES dans les
        // conditions des branches ; une branche `else` couvre tout.
        val couvertes = mutableSetOf<String>()
        var aElse = false
        for (entree in quand.enfants.filter { it.genre == KtNodeTypes.WHEN_ENTRY }) {
            // La branche `else` : l'entrée porte le JETON else (sa
            // condition n'est pas une WHEN_CONDITION_* — le parseur la
            // rend en jeton).
            if (entree.jetonsPropres.any { it.genre == KtTokens.ELSE_KEYWORD } ||
                entree.texteCanonique.startsWith("else")
            ) {
                aElse = true
            }
            for (condition in entree.enfants) {
                if (condition.genre != KtNodeTypes.WHEN_CONDITION_EXPRESSION &&
                    condition.genre != KtNodeTypes.WHEN_CONDITION_IS_PATTERN
                ) {
                    continue
                }
                val texte = condition.texteCanonique
                // La condition `Couleur.ROUGE` ou `ROUGE` : le DERNIER
                // segment dénomme l'entrée.
                entrees.filter { texte.endsWith(it) }.forEach { couvertes.add(it) }
            }
        }
        if (aElse || couvertes.containsAll(entrees)) return

        // Le `when` doit être en POSITION D'EXPRESSION : son parent est un
        // initialisateur de propriété, un `return` ou un argument — un
        // `when` INSTRUCTION n'exige pas l'exhaustivité (K2 pareil).
        if (!enPositionExpression(quand, univers)) return

        val manquantes = entrees.filter { it !in couvertes }
        val motCle = quand.jetonsPropres.firstOrNull { it.genre == KtTokens.WHEN_KEYWORD }
            ?: return
        diagnostics.add(
            DiagnosticKotlin(
                code = CodeDiagnosticKotlin.WHEN_NON_EXHAUSTIF,
                severite = SeveriteKotlin.ERREUR,
                plage = PlageTexte(motCle.debut, motCle.fin),
                message = "« when » non exhaustif : ${manquantes.joinToString(", ")} " +
                    "n'est pas couvert (ajoutez la branche ou « else »)",
                tags = emptyList(),
                informationsLiees = emptyList(),
            ),
        )
    }

    /** Les ENTRÉES d'un type enum (projet : modèle ; classpath : champs statiques). */
    private fun entreesEnum(type: TypeValeur, univers: UniversPreuves): List<String>? {
        return when (type) {
            is TypeValeur.Projet ->
                if (type.type.genre == GenreTypeProjet.ENUM && type.type.entreesEnum.isNotEmpty()) {
                    type.type.entreesEnum
                } else {
                    null
                }
            is TypeValeur.Jvm -> {
                val declares = univers.vueJvm?.membresDeclares(univers.mapeJvm(type.fqcn)) ?: return null
                if (declares.superFqcn != "java.lang.Enum") return null
                val descripteur = "L${type.fqcn.replace('.', '/')};"
                declares.membres
                    .filter { it.estChamp && it.descripteur == descripteur }
                    .map { it.nom }
                    .sorted()
                    .ifEmpty { null }
            }
        }
    }

    /** Le `when` est-il en POSITION D'EXPRESSION (initialisateur, return, argument) ? */
    private fun enPositionExpression(quand: NoeudArbre, univers: UniversPreuves): Boolean {
        val chaine = parentsDe(quand, univers)
        val parent = chaine.firstOrNull() ?: return false
        return when (parent.genre) {
            // PROPERTY (initialiseur), RETURN, argument — et le CORPS
            // D'EXPRESSION d'une fonction (`fun f(e: E): Int = when (e)`) :
            // le when est la VALEUR, l'exhaustivité s'exige.
            KtNodeTypes.PROPERTY, KtNodeTypes.RETURN, KtNodeTypes.VALUE_ARGUMENT, KtNodeTypes.FUN -> true
            else -> false
        }
    }

    /** Les parents immédiats (remonte les enveloppes — aide locale). */
    private fun parentsDe(noeud: NoeudArbre, univers: UniversPreuves): List<NoeudArbre> {
        val parents = mutableListOf<NoeudArbre>()
        fun marcherRacine(courant: NoeudArbre) {
            for (enfant in courant.enfants) {
                if (enfant === noeud) {
                    parents.add(courant)
                    return
                }
                marcherRacine(enfant)
            }
        }
        marcherRacine(univers.arbre.noeud)
        return parents
    }

    // ── kt.suspendHorsContexte ──────────────────────────────────────────

    /**
     * La preuve d'appel SUSPEND hors contexte : la callee est une fonction
     * `suspend` du PROJET (le modificateur est ÉCRIT dans SA DÉCLARATION —
     * jamais une inférence) et la fonction englobante n'est PAS `suspend`.
     * Une surcharge NON suspend du même nom rend la preuve muette
     * (conservateur — le résolveur d'appel n'est pas de ce palier).
     */
    private fun verifierSuspend(
        appel: NoeudArbre,
        univers: UniversPreuves,
        suspendEnglobant: Boolean,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        if (suspendEnglobant) return
        val nom = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?: return
        val texte = nom.texteCanonique
        if (texte.isEmpty()) return
        val candidates = univers.modele.appelables(texte, univers.paquet)
        if (candidates.isEmpty()) return
        if (candidates.any { !it.estSuspend }) return
        diagnostics.add(
            DiagnosticKotlin(
                code = CodeDiagnosticKotlin.SUSPEND_HORS_CONTEXTE,
                severite = SeveriteKotlin.ERREUR,
                plage = PlageTexte(nom.debut, nom.fin),
                message = "Appel suspendu hors contexte : « $texte » est « suspend » — " +
                    "l'appelant doit être « suspend » ou lancer une coroutine",
                tags = emptyList(),
                informationsLiees = emptyList(),
            ),
        )
    }

    /** La déclaration porte-t-elle le modificateur `suspend` ? */
    private fun porteurSuspend(declaration: NoeudArbre): Boolean {
        return declaration.enfants
            .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
            .any { liste -> liste.enfants.any { it.genre == KtTokens.SUSPEND_MODIFIER } }
    }

    // ── @Suppress servi (GK-021) ────────────────────────────────────────

    /**
     * Les CODES supprimés à la position : les annotations `@Suppress(...)`
     * des DÉCLARATIONS englobantes (fonction, classe, propriété — la
     * suppression couvre toute la région de la déclaration) servent leur
     * liste de codes (ou le joker `"*"` / `"all"` — miroir kotlinc).
     *
     * @param univers l'univers des preuves ;
     * @param position l'offset du diagnostic candidat ;
     * @param code le code `kt.*` à tester ;
     * @return `true` si une déclaration englobante supprime CE code.
     */
    internal fun supprimeA(univers: UniversPreuves, position: Int, code: String): Boolean {
        return supprimeDans(univers.arbre.noeud, position, code)
    }

    /** La marche récursive de la suppression (une déclaration couvre sa région). */
    private fun supprimeDans(noeud: NoeudArbre, position: Int, code: String): Boolean {
        for (enfant in noeud.enfants) {
            if (enfant.debut <= position && position <= enfant.fin) {
                if (codesSupprimesDe(noeud)?.let { code in it || "*" in it || "all" in it } == true) {
                    return true
                }
                return supprimeDans(enfant, position, code)
            }
        }
        // La position est SUR ce nœud (feuille) : le parent a déjà été
        // consulté à la récursion précédente.
        return codesSupprimesDe(noeud)?.let { code in it || "*" in it || "all" in it } ?: false
    }

    /** Les codes d'un `@Suppress(...)` porté par le nœud (ou `null`). */
    private fun codesSupprimesDe(noeud: NoeudArbre): Set<String>? {
        val modificateurs = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.MODIFIER_LIST }
            ?: return null
        var codes: Set<String>? = null
        for (annotation in modificateurs.enfants.filter { it.genre == KtNodeTypes.ANNOTATION_ENTRY }) {
            // La construction : @ Suppress ( "code" , … ) — les chaînes des
            // ARGUMENTS sont les codes (ils vivent sous la liste
            // d'arguments de l'annotation ; les autres formes restent muettes).
            val chaines = annotation.enfants
                .filter { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST }
                .flatMap { liste -> liste.enfants }
                .filter { it.genre == KtNodeTypes.VALUE_ARGUMENT }
                .flatMap { argument -> argument.enfants }
                .filter { it.genre == KtNodeTypes.STRING_TEMPLATE }
                .mapNotNull { gabarit ->
                    // Le texte du gabarit SANS les guillemets (les jetons
                    // de la partie littérale vivent imbriqués).
                    gabarit.texteCanonique.removePrefix("\"").removeSuffix("\"").ifEmpty { null }
                }
            if (chaines.isEmpty()) continue
            // L'annotation doit ÊTRE Suppress (l'identifiant du type, nu
            // ou qualifié — `kotlin.Suppress` légal).
            if (!annotation.texteCanonique.substringBefore('(').endsWith("Suppress")) continue
            codes = (codes ?: emptySet()) + chaines
        }
        return codes
    }
}

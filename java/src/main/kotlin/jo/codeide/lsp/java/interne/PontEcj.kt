package jo.codeide.lsp.java.interne

import org.eclipse.jdt.internal.compiler.CompilationResult
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies
import org.eclipse.jdt.internal.compiler.ICompilerRequestor
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import org.eclipse.jdt.internal.compiler.env.INameEnvironment
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter
import java.util.Locale

/**
 * Pont vers le compilateur ecj patché (étage N1) — adaptateur DIRECT des
 * classes internes d'`org.eclipse.jdt.internal.compiler` (le préfixe `Ecj`
 * du nom est donc justifié par la convention de nommage du dépôt : cette
 * classe encapsule réellement la bibliothèque).
 *
 * Deux régimes de pilotage :
 *  - **résolution complète** ([resoudre]) : `Compiler.resolve(...)` — pipeline
 *    parse → bindings → résolution → analyse de flux → (codegen optionnel) ;
 *    rend l'arbre `CompilationUnitDeclaration` VIVANT (scope et bindings
 *    lisibles, jamais nettoyés — contrairement au pipeline batch qui
 *    `cleanUp()` les unités après acceptation) ;
 *  - **parse pur** ([parserComplet], [parserDiet]) : `Parser` seul, sans
 *    résolution — le socle du repli syntaxe instantané (fiche 028) et de la
 *    complétion instantanée.
 *
 * Les options verrouillées : conformité/source/cible paramétrables, politique
 * `proceedWithAllProblems`, `Locale.ROOT` pour des messages reproductibles
 * (les assertions du cahier portent des sous-chaînes ANGLAISES — fiche 013),
 * et la famille des avertissements « unused » désactivée : le diagnostic de
 * frappe n'est pas une analytique IDE (conséquence structurelle : le
 * diagnose rapide peut remplacer les corps masqués par des synthétiques sans
 * fabriquer de faux « never used » — fiche 014, comportement corpusMasques).
 *
 * @param environnement le routage des noms (sources → jars → jrt → stubs).
 * @param compliance niveau Java (« 8 », « 17 », « 21 »…).
 */
public class PontEcj(
    private val environnement: INameEnvironment,
    compliance: String,
) {

    /**
     * Options du compilateur (construites une fois, réutilisées par passe).
     *
     * Deux tables :
     *  - [options] — la table STRICTE (familles « unused » éteintes) :
     *    le domaine des passes à corps MASQUÉS (diagnose rapide, fiche
     *    014 : un import utilisé par un corps masqué ne doit JAMAIS
     *    devenir fallacieusement « unused ») ;
     *  - [optionsAnalytiques] — la table du diagnose COMPLET (étage N8,
     *    cahier fiche 056 : l'import inutilisé est un VRAI signal —
     *    avertissement + tag Unnecessary côté protocole ; les corps
     *    entiers sont analysés, aucun fallacieux).
     */
    private val options: CompilerOptions = CompilerOptions(optionsEcj(compliance, analytique = false))

    /** Options analytiques du diagnose complet (fiche 056 — import inutilisé). */
    private val optionsAnalytiques: CompilerOptions = CompilerOptions(optionsEcj(compliance, analytique = true))

    /** Usine de problèmes en Locale.ROOT — messages reproductibles (fiche 013). */
    private val usineProblemes = DefaultProblemFactory(Locale.ROOT)

    /**
     * Résout une unité de compilation complète : parse, construction des
     * bindings, résolution, analyse de flux, finalisation des problèmes —
     * codegen mémoire optionnel.
     *
     * @param unite l'unité source à résoudre.
     * @param genererCode si vrai, la génération de classe mémoire est jouée
     *   (les octets restent dans le `CompilationResult`, jamais écrits) —
     *   c'est l'attribut d'entrée du cache de parse (fiche 025).
     * @param analytique vrai pour la table ANALYTIQUE (diagnose complet —
     *   l'import inutilisé est publié, fiche 056) ; faux (défaut) pour la
     *   table stricte des passes à corps masqués (fiche 014).
     * @return la réponse (arbre vivant + problèmes + résultat).
     */
    public fun resoudre(unite: ICompilationUnit, genererCode: Boolean, analytique: Boolean = false): ReponseEcj {
        val reponse = ReponseEcj()
        val compilateur = org.eclipse.jdt.internal.compiler.Compiler(
            environnement,
            DefaultErrorHandlingPolicies.proceedWithAllProblems(),
            if (analytique) optionsAnalytiques else options,
            reponse,
            usineProblemes,
        )
        // Le requestor (reponse) reçoit le CompilationResult pendant la passe ;
        // l'arbre vivant n'est rendu que par la valeur de retour.
        reponse.arbre = compilateur.resolve(null, unite, true, true, genererCode)
        return reponse
    }

    /**
     * Parse une unité SANS résolution (corps des méthodes inclus).
     *
     * Le parse pur ne consulte jamais l'environnement de noms : aucune
     * plateforme n'est requise — c'est le socle du repli syntaxe (fiche 028,
     * garanties ① et ②) et de la complétion instantanée.
     *
     * @param unite l'unité source.
     * @return la réponse (arbre non résolu + problèmes de SYNTAXE seulement).
     */
    public fun parserComplet(unite: ICompilationUnit): ReponseEcj {
        val reponse = ReponseEcj()
        val parser = nouveauParser()
        val resultat = CompilationResult(unite, 0, 1, options.maxProblemsPerUnit)
        val arbre = parser.parse(unite, resultat)
        reponse.resultats.add(resultat)
        reponse.arbre = arbre
        return reponse
    }

    /**
     * Parse une unité en mode DIET (en-têtes seulement, corps éludés).
     *
     * Utile pour énumérer rapidement les déclarations d'un fichier (types,
     * méthodes, champs) sans payer le parse des corps.
     *
     * @param unite l'unité source.
     * @return la réponse (arbre diet + problèmes de syntaxe d'en-tête).
     */
    public fun parserDiet(unite: ICompilationUnit): ReponseEcj {
        val reponse = ReponseEcj()
        val parser = nouveauParser()
        val resultat = CompilationResult(unite, 0, 1, options.maxProblemsPerUnit)
        val arbre = parser.dietParse(unite, resultat)
        reponse.resultats.add(resultat)
        reponse.arbre = arbre
        return reponse
    }

    /** Crée un parser frais (le parser d'ecj n'est pas réutilisable entre sources). */
    private fun nouveauParser(): org.eclipse.jdt.internal.compiler.parser.Parser =
        org.eclipse.jdt.internal.compiler.parser.Parser(
            ProblemReporter(
                DefaultErrorHandlingPolicies.proceedWithAllProblems(),
                options,
                usineProblemes,
            ),
            false,
        )

    /**
     * Réponse d'une passe ecj : résultats (problèmes) + arbre éventuel.
     *
     * @property resultats les `CompilationResult` produits (au moins celui de
     *   l'unité principale ; les unités additionnelles tirées du classpath
     *   source ne publient pas leurs propres problèmes).
     * @property arbre l'arbre de l'unité principale (null si la passe a avorté
     *   prématurément).
     */
    public class ReponseEcj : ICompilerRequestor {
        public val resultats: MutableList<CompilationResult> = mutableListOf()

        public var arbre: CompilationUnitDeclaration? = null

        override fun acceptResult(result: CompilationResult) {
            resultats.add(result)
        }

        /** Tous les problèmes non nuls de tous les résultats (le tableau ecj peut contenir des trous). */
        public fun problemes(): List<org.eclipse.jdt.core.compiler.IProblem> =
            resultats.flatMap { resultat ->
                resultat.problems?.filterNotNull()?.toList() ?: emptyList()
            }
    }

    /**
     * Construit la table d'options ecj du moteur.
     *
     * @param compliance le niveau de conformité.
     * @param analytique vrai = familles « unused » ALLUMÉES pour le signal
     *   réel (fiche 056 : import inutilisé) ; faux = éteintes (fiche 014 :
     *   les corps masqués du diagnose rapide ne fabriquent pas de faux
     *   « unused »).
     */
    private fun optionsEcj(compliance: String, analytique: Boolean): Map<String, String> = mapOf(
        "org.eclipse.jdt.core.compiler.compliance" to compliance,
        "org.eclipse.jdt.core.compiler.source" to compliance,
        "org.eclipse.jdt.core.compiler.codegen.targetPlatform" to compliance,
        // Familles « unused » : ÉTEINTES sur les passes à corps masqués
        // (fiche 014, corpsMasques — la valeur du moteur est l'erreur de
        // compilation fidèle, pas l'analytique IDE) ; l'import inutilisé
        // est ALLUMÉ sur le diagnose COMPLET (étage N8, fiche 056 : vrai
        // signal, tag Unnecessary côté protocole — les corps entiers sont
        // analysés, aucun fallacieux). Les autres familles restent
        // éteintes : le cahier ne verrouille qu'un seul signal unused.
        "org.eclipse.jdt.core.compiler.problem.unusedImport" to if (analytique) "warning" else "ignore",
        "org.eclipse.jdt.core.compiler.problem.unusedLocal" to "ignore",
        "org.eclipse.jdt.core.compiler.problem.unusedPrivateMember" to "ignore",
        "org.eclipse.jdt.core.compiler.problem.undocumentedEmptyBlock" to "ignore",
        "org.eclipse.jdt.core.compiler.problem.rawTypeReference" to "ignore",
        "org.eclipse.jdt.core.compiler.problem.uncheckedTypeOperation" to "ignore",
        "org.eclipse.jdt.core.compiler.problem.missingSerialVersion" to "ignore",
    )

    public companion object {
        /**
         * Enveloppe une source en mémoire en unité de compilation ecj.
         *
         * @param chemin nom de fichier logique (p. ex. `/demo/Main.java`).
         * @param source le texte Java.
         * @return l'unité prête pour [resoudre] ou [parserComplet].
         */
        @JvmStatic
        public fun uniteEnMemoire(chemin: String, source: String): ICompilationUnit =
            CompilationUnit(source.toCharArray(), chemin, "UTF-8")
    }
}

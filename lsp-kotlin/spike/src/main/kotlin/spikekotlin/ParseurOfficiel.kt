package spikekotlin

import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.parser.prepareProduction
import com.intellij.platform.syntax.util.lexer.LexerBase
import org.jetbrains.kotlin.kmp.lexer.KotlinLexer
import org.jetbrains.kotlin.kmp.parser.AbstractParser
import org.jetbrains.kotlin.kmp.parser.KotlinParser

/**
 * Pilote du PARSEUR OFFICIEL seul (variante S2 du spike).
 *
 * Conduit la grammaire vendorée (obtenue de l'amont JetBrains au tag
 * v2.3.0, `src/main/vendor`) exactement comme les tests amont conduisent
 * leur parseur : un `KotlinParser` (façade publique de la grammaire) branché
 * sur un `SyntaxTreeBuilder` produit par `SyntaxTreeBuilderFactory`, le
 * lexing étant fait par `KotlinLexer` + `performLexing`.
 *
 * Le patron d'assemblage est adapté de
 * `compiler/multiplatform-parsing/jvm/test/.../NewTestParser.kt` (Apache-2.0,
 * JetBrains — crédit consigné, miroir de l'adaptation de l'ancien moteur,
 * ADR 0115.8 ancien) : c'est l'ASSEMBLAGE qui est adapté, pas copié.
 *
 * Ce pilote ne connaît RIEN de la sémantique : il mesure le coût
 * d'entrée de la couche syntaxique officielle (lexing + arbre de
 * production), base de la variante « modèle de symboles neuf ».
 */
object ParseurOfficiel {

    /** Parse [texte] et rend l'arbre léger prêt (productions + jetons).
     *
     * @param texte le source Kotlin ;
     * @param script vrai pour le mode script (`.kts`) ;
     * @param paresseux vrai pour le mode lazy de la grammaire amont ;
     * @return le résultat de parse (production racine prête).
     */
    fun parse(texte: CharSequence, script: Boolean = false, paresseux: Boolean = false): ResultatParse {
        val parseur: AbstractParser = KotlinParser(isScript = script, isLazy = paresseux)
        val lexeur: LexerBase = KotlinLexer()
        val constructeur: SyntaxTreeBuilder = SyntaxTreeBuilderFactory.builder(
            texte,
            performLexing(texte, lexeur, cancellationProvider = null, logger = null),
            whitespaces = parseur.whitespaces,
            comments = parseur.comments,
        ).withStartOffset(0)
            .withWhitespaceOrCommentBindingPolicy(parseur.whitespaceOrCommentBindingPolicy)
            .build()
        parseur.parse(constructeur)
        return ResultatParse(constructeur)
    }

    /** Arbre léger rendu par le constructeur, interrogable. */
    class ResultatParse(val constructeur: SyntaxTreeBuilder) {
        /** Nombre de productions (nœuds) de l'arbre, après préparation. */
        fun nombreProductions(): Int {
            val pret = prepareProduction(constructeur)
            return pret.productionMarkers.size
        }

        /** Nombre de jetons de l'arbre (liste de jetons du constructeur). */
        fun nombreJetons(): Int = constructeur.tokens.tokenCount
    }
}

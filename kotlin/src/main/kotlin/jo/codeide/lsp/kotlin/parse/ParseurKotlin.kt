// CodeIDE — harnais de parse de la grammaire Kotlin officielle (module
// :kotlin, étage 0 — amorçage). Ce fichier est du code NEUF jjoblab écrit
// pour code-lsp (clean-room : le code de l'ancien moteur codeide-lsp n'est
// pas consulté — le cahier docs/cahier-specifications-kotlin/ est le contrat).
package jo.codeide.lsp.kotlin.parse

import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.parser.prepareProduction
import com.intellij.platform.syntax.util.lexer.LexerBase
import java.security.MessageDigest
import org.jetbrains.kotlin.kmp.lexer.KotlinLexer
import org.jetbrains.kotlin.kmp.parser.AbstractParser
import org.jetbrains.kotlin.kmp.parser.KotlinParser

/**
 * Harnais minimal de la grammaire Kotlin officielle vendorée
 * (`org.jetbrains.kotlin.kmp.*`, `src/main/vendor` — R9.2, tag amont
 * épinglé v2.3.0, voir `VENDOR.md`).
 *
 * Il conduit la grammaire exactement comme les tests amont conduisent leur
 * parseur : un [KotlinParser] (façade publique de la grammaire) branché sur
 * un [SyntaxTreeBuilder] produit par [SyntaxTreeBuilderFactory], le lexing
 * étant fait par [KotlinLexer] + [performLexing]. Le patron d'assemblage est
 * adapté de `compiler/multiplatform-parsing/jvm/test/.../NewTestParser.kt`
 * amont (Apache-2.0, JetBrains — crédit consigné, miroir de l'adaptation de
 * l'ancien moteur et du spike `spike-kotlin/src/main/kotlin/spikekotlin/
 * ParseurOfficiel.kt`) : c'est l'ASSEMBLAGE qui est adapté, pas copié.
 *
 * Contrat de tolérance (cahier, fiches 011/012 — D3 amont) : l'éditeur
 * appelle le parseur à CHAQUE frappe, y compris entre deux lettres, au
 * milieu d'une chaîne, d'un commentaire ou d'un KDoc — [analyse] ne lève
 * JAMAIS d'exception sur du texte hostile (tronqué, injecté, corrompu) et
 * rend toujours un arbre racine complet ; les fautes se lisent comme des
 * marqueurs d'erreur DANS l'arbre, jamais comme des échecs.
 *
 * Ce harnais est volontairement MINIMAL (étage 0) : il expose le vocabulaire
 * strict nécessaire à la suite de parité « grammaire » (fiches 010–012).
 * La façade `Kt*` conservative (fiche 013 — package, imports, déclarations,
 * genres) est le périmètre de l'étage 1 (noyau parse/session) : elle naîtra
 * du cahier, au-dessus de ce harnais.
 */
public class ParseurKotlin {

    /**
     * Analyse [texte] avec la grammaire officielle vendorée.
     *
     * @param texte le source Kotlin (jamais nul ; peut être vide, tronqué ou
     * hostile — voir le contrat de tolérance de la classe) ;
     * @param script `true` pour le mode script (`.kts`) — le mode script de
     * la grammaire autorise les directives et instructions de premier niveau
     * (fiche 012, comportement `script-kts---directives-autorisees-en-racine`) ;
     * @return un instantané IMMUTABLE du résultat (genre racine, marqueurs
     * d'erreur, jetons non blancs, digeste structurel) — l'arbre de
     * construction sous-jacent n'est pas retenu : l'instantané se copie et
     * se compare (fiches 010/012) sans porter la mémoire de l'arbre.
     */
    public fun analyse(texte: CharSequence, script: Boolean = false): ResultatAnalyse {
        val parseur: AbstractParser = KotlinParser(isScript = script, isLazy = false)
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

        val productions = prepareProduction(constructeur).productionMarkers
        val jetons = constructeur.tokens

        // --- Marqueurs d'erreur + production racine (plus grande envergure) ---
        var marqueursErreur = 0
        var racine: SyntaxTreeBuilder.Production? = null
        var envergureRacine = -1
        for (index in 0 until productions.size) {
            val marqueur = productions.getMarker(index)
            if (productions.isDoneMarker(index)) {
                val envergure = marqueur.getEndOffset() - marqueur.getStartOffset()
                if (envergure >= envergureRacine) {
                    envergureRacine = envergure
                    racine = marqueur
                }
            } else if (marqueur.isErrorMarker()) {
                marqueursErreur++
            }
        }

        // --- Jetons non blancs (la fiche 012 épinglage texte + positions) ---
        val jetonsNonBlancs = buildList {
            for (index in 0 until jetons.tokenCount) {
                val genre = checkNotNull(jetons.getTokenType(index))
                if (parseur.whitespaces.contains(genre)) continue
                if (parseur.comments.contains(genre)) continue
                add(
                    JetonAnalyse(
                        genre = genre.toString(),
                        texte = jetons.getTokenText(index)!!.toString(),
                        debut = jetons.getTokenStart(index),
                        fin = jetons.getTokenEnd(index),
                    ),
                )
            }
        }

        // --- Digeste structurel (déterminisme + baseline de parité, fiche 010) ---
        val canonique = buildString {
            for (index in 0 until productions.size) {
                val marqueur = productions.getMarker(index)
                if (productions.isDoneMarker(index)) {
                    append('D').append(':').append(marqueur.getNodeType())
                        .append(':').append(if (marqueur.isCollapsed()) 1 else 0).append('\n')
                } else if (marqueur.isErrorMarker()) {
                    append("E\n")
                }
            }
            for (index in 0 until jetons.tokenCount) {
                append('T').append(':').append(jetons.getTokenType(index)).append('\n')
            }
        }
        val digeste = MessageDigest.getInstance("SHA-256")
            .digest(canonique.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        return ResultatAnalyse(
            genreRacine = racine?.getNodeType()?.toString() ?: "(aucune)",
            nombreMarqueursErreur = marqueursErreur,
            nombreProductions = productions.size,
            nombreJetons = jetons.tokenCount,
            jetonsNonBlancs = jetonsNonBlancs,
            digesteStructurel = digeste,
        )
    }
}

/**
 * Instantané immutable d'une analyse (voir [ParseurKotlin.analyse]).
 *
 * @property genreRacine le genre de la production racine de l'arbre
 * (vocabulaire du compilateur : `KT_FILE` pour un fichier, `KT_SCRIPT` pour
 * un script) — conservateur : `(aucune)` si l'arbre n'a aucune production
 * achevée (jamais observé, la grammaire rend toujours une racine).
 * @property nombreMarqueursErreur le nombre de marqueurs d'erreur posés par
 * la grammaire dans l'arbre (une faute de syntaxe se lit, ne lève pas).
 * @property nombreProductions le nombre total de marqueurs de production
 * (nœuds + marqueurs d'erreur) de l'arbre préparé.
 * @property nombreJetons le nombre total de jetons (blancs compris) produits
 * par le lexeur.
 * @property jetonsNonBlancs les jetons significatifs (blancs et commentaires
 * exclus) avec leurs positions exactes dans le texte source.
 * @property digesteStructurel le SHA-256 de la sérialisation canonique de
 * la FORME de l'arbre (productions + genres de jetons) — deux analyses du
 * même texte donnent le même digeste (fiche 012) ; toute dérive de grammaire
 * (re-sync amont, régression locale) déplace les digests du corpus (fiche
 * 010, baseline versionnée).
 */
public class ResultatAnalyse(
    public val genreRacine: String,
    public val nombreMarqueursErreur: Int,
    public val nombreProductions: Int,
    public val nombreJetons: Int,
    public val jetonsNonBlancs: List<JetonAnalyse>,
    public val digesteStructurel: String,
)

/**
 * Un jeton significatif de l'analyse (blancs et commentaires exclus).
 *
 * @property genre le genre du jeton dans le vocabulaire du compilateur
 * (par ex. `IDENTIFIER`, `fun_keyword`, `INTEGER_LITERAL`) ;
 * @property texte le texte EXACT du jeton dans le source ;
 * @property debut l'offset (inclus) du début du jeton dans le texte source ;
 * @property fin l'offset (exclu) de la fin du jeton dans le texte source.
 */
public class JetonAnalyse(
    public val genre: String,
    public val texte: String,
    public val debut: Int,
    public val fin: Int,
)

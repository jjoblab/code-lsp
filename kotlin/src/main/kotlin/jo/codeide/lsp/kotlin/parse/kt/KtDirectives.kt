// CodeIDE — façade `Kt*` : directives de package et d'import (module
// :kotlin, étage 1 vague 1a). Code NEUF jjoblab (clean-room — contrat :
// fiche 013 du cahier, comportements `directive-package---nom-qualifie`,
// `fichier-sans-package---nom-qualifie-null-(directive-vide-presente)`,
// `imports-simple-alias-et-wildcard`,
// `alias-sans-identifiant-derriere-as---null-conservateur`).
package jo.codeide.lsp.kotlin.parse.kt

import jo.codeide.lsp.kotlin.parse.NoeudArbre
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * La directive `package` d'un document Kotlin (toujours présente dans
 * l'arbre de la grammaire — de plage nulle lorsqu'aucun package n'est
 * écrit).
 *
 * Le nom qualifié se lit comme la forme écrite sans trivia de la référence
 * de la directive (fiche 013) : `package demo.parse` rend `"demo.parse"`.
 */
public class KtDirectivePackage internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtNoeud(noeud, source) {

    /**
     * Rend le nom qualifié du package TEL QU'ÉCRIT (sans trivia).
     *
     * @return le nom qualifié (par ex. `"demo.parse"`) ; `null`
     * (conservateur, fiche 013) si la directive est vide — la directive est
     * alors PRESENTE dans l'arbre mais de plage nulle, sans référence
     * enfant.
     */
    public fun nomQualifie(): String? {
        val reference = premierEnfantDeGenre(KtNodeTypes.DOT_QUALIFIED_EXPRESSION)
            ?: premierEnfantDeGenre(KtNodeTypes.REFERENCE_EXPRESSION)
            ?: return null
        return reference.texteCanonique.ifEmpty { null }
    }
}

/**
 * Une directive `import` d'un document Kotlin.
 *
 * Les trois formes écrites sont couvertes (fiche 013) : l'import simple
 * (`import java.util.ArrayList`), l'import aliasé (`import kotlin.math.PI as
 * constante`) et l'import joker (`import java.util.*`). Les accès restent
 * conservateurs : ce qui n'est pas écrit se lit `null` ou `false`.
 */
public class KtDirectiveImport internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtNoeud(noeud, source) {

    /**
     * Rend le chemin importé TEL QU'ÉCRIT (sans trivia), alias exclu : la
     * forme canonique de l'expression qualifiée de la directive.
     *
     * Pour un import joker, le chemin rendu est la partie qualifiée écrite
     * (par ex. `"java.util"` pour `import java.util.*`) — le joker se
     * interroge séparément par [estJoker].
     *
     * @return le chemin importé (par ex. `"java.util.ArrayList"`) ; `null`
     * (conservateur) si la directive n'a pas d'expression de chemin (forme
     * tronquée).
     */
    public fun nomQualifie(): String? {
        val chemin = premierEnfantDeGenre(KtNodeTypes.DOT_QUALIFIED_EXPRESSION)
            ?: premierEnfantDeGenre(KtNodeTypes.REFERENCE_EXPRESSION)
            ?: return null
        return chemin.texteCanonique.ifEmpty { null }
    }

    /**
     * Rend l'identifiant de l'alias écrit derrière `as`.
     *
     * @return le nom de l'alias (par ex. `"constante"` pour
     * `import kotlin.math.PI as constante`) ; `null` (conservateur, fiche
     * 013 — comportement `alias-sans-identifiant-derriere-as`) si la
     * directive n'est pas aliasée ou si l'alias est tronqué sans
     * identifiant.
     */
    public fun alias(): String? {
        val noeudAlias = premierEnfantDeGenre(KtNodeTypes.IMPORT_ALIAS) ?: return null
        // L'identifiant de l'alias est le premier jeton IDENTIFIER propre du
        // nœud alias — le mot-clé `as` est un jeton de genre AS_KEYWORD, il
        // n'entre pas en concurrence.
        return noeudAlias.jetonsPropres
            .firstOrNull { it.genre == KtTokens.IDENTIFIER }
            ?.texte
    }

    /**
     * Interroge si l'import est un JOKER (`import java.util.*`).
     *
     * @return `true` si la directive porte le jeton `*` (genre `MUL`) hors
     * de toute production enfant ; `false` sinon (jamais d'exception).
     */
    public fun estJoker(): Boolean {
        return noeud.jetonsPropres.any { it.genre == KtTokens.MUL }
    }
}

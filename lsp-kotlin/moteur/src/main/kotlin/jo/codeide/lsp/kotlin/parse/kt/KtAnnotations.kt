// CodeIDE — façade `Kt*` : entrées d'annotation et arguments (module
// :kotlin, étage 1 vague 1a). Code NEUF jjoblab (clean-room — contrat :
// fiche 013 du cahier, comportements
// `annotations---noms-ecrits-et-ordre-du-document`,
// `annotations---arguments-nommes-avec-plages-de-valeurs`).
package jo.codeide.lsp.kotlin.parse.kt

import jo.codeide.lsp.kotlin.parse.NoeudArbre
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Une entrée d'annotation (`@Deprecated(...)`, `@Suppress("…")`…).
 *
 * Le nom se lit TEL QU'ÉCRIT (forme canonique sans trivia de la référence du
 * constructeur appelé) : `@Deprecated` rend `"Deprecated"`,
 * `@kotlin.Deprecated` rend `"kotlin.Deprecated"` (fiche 013, comportement
 * `annotations---noms-ecrits-et-ordre-du-document`). Les cibles d'usage
 * (`@get:Deprecated`) ne font pas partie du nom — elles sont une production
 * sœur de la référence et sont ignorées par l'accès au nom.
 */
public class KtAnnotationEntree internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtNoeud(noeud, source) {

    /**
     * Rend le nom de l'annotation TEL QU'ÉCRIT, sans la cible d'usage.
     *
     * La grammaire encode la référence qualifiée en `USER_TYPE` imbriqués
     * (qualificateurs en cascade — sondé à l'étage 1) : la forme écrite
     * fidèle est donc le texte canonique de la RÉFÉRENCE DE TYPE du
     * constructeur appelé, qui reconstitue les segments et les points.
     *
     * @return la forme écrite (par ex. `"Deprecated"`, `"kotlin.Beta"`,
     * `"Foo.Bar"`) ; `null` (conservateur) si l'entrée est tronquée avant sa
     * référence.
     */
    public fun nom(): String? {
        val callee = premierEnfantDeGenre(KtNodeTypes.CONSTRUCTOR_CALLEE) ?: return null
        val typeRef = callee.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
            ?: return null
        return typeRef.texteCanonique.ifEmpty { null }
    }

    /**
     * Rend les arguments de l'annotation, en ordre d'écriture.
     *
     * @return les arguments de la liste d'arguments ; liste vide si
     * l'annotation n'a pas de liste ni d'arguments (jamais d'exception).
     */
    public fun arguments(): List<KtArgumentAnnotation> {
        val liste = premierEnfantDeGenre(KtNodeTypes.VALUE_ARGUMENT_LIST) ?: return emptyList()
        return liste.enfants
            .filter { it.genre == KtNodeTypes.VALUE_ARGUMENT }
            .map { KtArgumentAnnotation(it, source) }
    }
}

/**
 * Un argument d'annotation — nommé (`message = "…"` ou `level = X`) ou
 * positionnel (`"…"`).
 */
public class KtArgumentAnnotation internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtNoeud(noeud, source) {

    /**
     * Rend le NOM de l'argument, tel qu'écrit avant `=`.
     *
     * @return le nom de l'argument nommé (par ex. `"message"`) ; `null`
     * (conservateur) si l'argument est positionnel — sans nom écrit.
     */
    public fun nom(): String? {
        val noeudNom = premierEnfantDeGenre(KtNodeTypes.VALUE_ARGUMENT_NAME) ?: return null
        return noeudNom.texteCanonique.ifEmpty { null }
    }

    /**
     * Rend la PLAGE de la VALEUR de l'argument dans le texte source.
     *
     * La valeur est la dernière production enfant qui n'est pas le nom : la
     * plage couvre l'expression écrite après `=` (par exemple le gabarit de
     * chaîne `"obsolète"` ou la référence qualifiée `DeprecationLevel.WARNING`).
     *
     * @return la plage (offsets inclus/exclu) du nœud valeur ; `null`
     * (conservateur) si l'argument est tronqué avant sa valeur (nom sans
     * `=` ni expression).
     */
    public fun plageValeur(): PlageTexte? {
        val valeur = noeud.enfants
            .lastOrNull { it.genre != KtNodeTypes.VALUE_ARGUMENT_NAME }
            ?: return null
        return PlageTexte(debut = valeur.debut, fin = valeur.fin)
    }
}

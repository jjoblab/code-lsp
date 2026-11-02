// CodeIDE — façade `Kt*` : socle générique des nœuds (module :kotlin, étage 1
// vague 1a). Code NEUF jjoblab écrit pour code-lsp (clean-room — le contrat
// est la fiche 013 du cahier docs/cahier-specifications-kotlin/ : « les
// accesseurs portent le vocabulaire du compilateur et RESTENT conservateurs
// (D3) — introuvable = null/vide »).
package jo.codeide.lsp.kotlin.parse.kt

import com.intellij.platform.syntax.SyntaxElementType
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.NoeudArbre

/**
 * Socle générique de la façade `Kt*` : l'enveloppe publique, conservatrice
 * et immuable, d'un [NoeudArbre] reconstruit depuis la grammaire officielle
 * vendorée.
 *
 * ## Contrat conservateur (D3, fiche 013)
 *
 * Tout accès se fait dans le vocabulaire du compilateur (le nom du genre est
 * celui de la grammaire amont, par ex. `CLASS`, `FUN`, `IMPORT_DIRECTIVE`) ;
 * ce qui est introuvable se lit comme `null` ou une liste vide — JAMAIS comme
 * une exception : l'éditeur interroge l'arbre à chaque frappe, y compris sur
 * du texte tronqué ou hostile, et la façade répond honnêtement « absent »
 * plutôt que de lever.
 *
 * ## Navigation générique
 *
 * [enfants] permet la navigation par type au genre près (fiche 013,
 * comportement `navigation-generique---enfants-par-type`) : elle rend des
 * enveloppes génériques [KtNoeud], sans typage déclaratif — les accesseurs
 * typés (`KtClasse`, `KtFonction`…) sont les chemins recommandés, la
 * navigation générique est le filet pour les formes non typées.
 *
 * @property noeud le nœud interne enveloppé (interne au module) ;
 * @property source le texte source complet analysé (lecture seule).
 */
public open class KtNoeud internal constructor(
    internal val noeud: NoeudArbre,
    internal val source: CharSequence,
) {

    /**
     * Le genre du nœud dans le vocabulaire du compilateur (nom de la
     * production de la grammaire amont — par ex. `CLASS`, `FUN`,
     * `IMPORT_DIRECTIVE`, `kotlin.FILE` pour la racine fichier).
     */
    public val genre: String
        get() = noeud.genre.toString()

    /**
     * La plage du nœud dans le texte source : [PlageTexte.debut] inclus,
     * [PlageTexte.fin] exclu.
     */
    public val plage: PlageTexte
        get() = PlageTexte(debut = noeud.debut, fin = noeud.fin)

    /**
     * Le message d'erreur porté par un nœud `ERROR_ELEMENT` (faute de
     * syntaxe lisible, jamais levée) ; `null` pour un nœud sain. C'est la
     * matière première des diagnostics de syntaxe (fiche 049, étage 1).
     */
    public val messageErreur: String?
        get() = noeud.messageErreur

    /**
     * Rend le texte EXACT du nœud dans le source (trivia compris).
     *
     * @return la tranche de [source] couverte par le nœud.
     */
    public fun texte(): CharSequence = source.subSequence(noeud.debut, noeud.fin)

    /**
     * Rend la forme ÉCRITE du nœud sans trivia : tous les jetons de la plage
     * (productions enfants comprises), blancs et commentaires exclus, dans
     * l'ordre du document. C'est la forme des noms qualifiés
     * (`java.util.ArrayList`) et des noms d'annotations tels qu'écrits.
     *
     * @return le texte canonique du nœud (vide pour un nœud de plage nulle).
     */
    public fun texteCanonique(): String = noeud.texteCanonique

    /**
     * Rend les enfants directs du nœud dont le genre est exactement [genre]
     * (comparaison au nom du vocabulaire du compilateur).
     *
     * @param genre le nom du genre recherché (par ex. `"CLASS"`,
     * `"VALUE_PARAMETER"`) ;
     * @return les enveloppes génériques des enfants de ce genre, en ordre du
     * document ; liste vide si aucun (jamais d'exception).
     */
    public fun enfants(genre: String): List<KtNoeud> {
        return noeud.enfants
            .filter { it.genre.toString() == genre }
            .map { KtNoeud(it, source) }
    }

    /**
     * Rend le premier enfant direct dont le genre est exactement [genre].
     *
     * @param genre le nom du genre recherché ;
     * @return l'enveloppe générique du premier enfant de ce genre, ou `null`
     * (conservateur) s'il n'existe pas.
     */
    public fun premierEnfant(genre: String): KtNoeud? {
        return noeud.enfants
            .firstOrNull { it.genre.toString() == genre }
            ?.let { KtNoeud(it, source) }
    }

    // ------------------------------------------------------------------
    // Aides internes typées (comparaison par IDENTITÉ de SyntaxElementType —
    // les constantes vendored sont des singletons enregistrés).
    // ------------------------------------------------------------------

    /**
     * Rend les enfants directs de genre exactement [genre] (identité
     * d'élément syntaxique).
     */
    internal fun enfantsDeGenre(genre: SyntaxElementType): List<NoeudArbre> {
        return noeud.enfants.filter { it.genre == genre }
    }

    /**
     * Rend le premier enfant direct de genre exactement [genre].
     */
    internal fun premierEnfantDeGenre(genre: SyntaxElementType): NoeudArbre? {
        return noeud.enfants.firstOrNull { it.genre == genre }
    }

    /**
     * Rend le premier jeton PROPRE du nœud (hors productions enfants, blancs
     * et commentaires exclus) dont le genre est exactement [genre].
     */
    internal fun premierJetonPropre(genre: SyntaxElementType): JetonBrut? {
        return noeud.jetonsPropres.firstOrNull { it.genre == genre }
    }
}

/**
 * Une plage du texte source : [debut] inclus, [fin] exclu (convention LSP).
 *
 * @property debut l'offset (inclus) de début dans le texte source ;
 * @property fin l'offset (exclu) de fin dans le texte source.
 */
public data class PlageTexte(
    public val debut: Int,
    public val fin: Int,
)

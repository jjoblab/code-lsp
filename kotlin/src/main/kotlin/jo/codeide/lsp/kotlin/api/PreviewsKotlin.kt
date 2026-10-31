// CodeIDE — SPI du moteur Kotlin LSP : modèle neutre des previews
// Compose (module :kotlin, étage 5 vague 5c — fiche 050 du cahier, C1 —
// PREVIEW.md §4.4). Code NEUF jjoblab (clean-room — l'ancien `api/` est
// zone NOYAU en lecture interdite R9.1 : ce modèle est écrit cahier en
// main, la reprise R3 `preview/PreviewsDocumentKotlin.kt` s'y câble).
// Frontière D6 : l'hôte traduit en marqueurs de gouttière et rendu
// « goutte d'eau » — le moteur n'expose jamais LSP4J.
package jo.codeide.lsp.kotlin.api

import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Le genre d'INLINING d'un appel de composable du projet (C1 — la
 * réduction du corps d'une preview inlinée, avec ses échecs TYPIES :
 * jamais un faux rendu).
 */
public enum class GenreInlineKotlin {

    /** L'appel n'est PAS inliné (appel inconnu du rendu, ou candidate NON
     * composable — un appel de fonction ordinaire reste un appel). */
    AUCUN,

    /** Le corps du composable du PROJET est INLINÉ (rendu imbriqué). */
    PROJET_INLINE,

    /** La chaîne d'inline boucle (le composable se rappelle, direct ou
     * indirect) — coupé, jamais une récursion infinie. */
    PROJET_CYCLE,

    /** La chaîne d'inline dépasse la profondeur ou le budget de nœuds —
     * coupé. */
    PROJET_BUDGET,

    /** Le composable candidate porte des PARAMÈTRES : C1 n'inlinie pas
     * (les arguments ne traversent pas l'inline). */
    PROJET_PARAMETRES,

    /** Le fichier du composable candidate est ILLISIBLE (disque absent,
     * parse hostile) — échec typé. */
    PROJET_ILLISIBLE,
}

/** Le genre d'interprétation de la VALEUR d'un argument d'appel. */
public enum class GenreArgumentAppelKotlin {

    /** Un LITTÉRAL (chaîne, nombre, booléen) — rendu tel quel. */
    LITTERAL,

    /** Une RÉFÉRENCE simple ou qualifiée sans appel (`Color.Blue`, `8.dp`). */
    REFERENCE,

    /** Tout le reste (appel imbriqué, lambda, expression composée). */
    AUTRE,
}

/**
 * Un NŒUD de l'arbre d'appels d'une preview (C1) : un appel interprétable
 * ou une expression honnête non interprétée.
 */
public sealed class NoeudAppelKotlin

/**
 * Un APPEL de fonction/composable dans le corps d'une preview : nom,
 * récepteur éventuel, arguments explicites (littéraux distingués),
 * contenu du lambda final et genre d'inline.
 *
 * @property nom le nom appelé (`Text`, `Column`) — un repli tronqué en
 * édition ;
 * @property recepteur le texte du récepteur qualifié (`Modifier`), `null`
 * pour un appel nu ;
 * @property ligne la ligne 1-indexée de l'appel (le rendu place les
 * marqueurs) ;
 * @property arguments les arguments EXPLICITES entre parenthèses ;
 * @property contenu les nœuds du corps du LAMBDA FINAL (le contenu
 * imbriqué) — ou le corps INLINÉ du composable du projet ;
 * @property genreInline le genre d'inline (jamais un faux rendu).
 */
public class AppelFonctionKotlin(
    public val nom: String,
    public val recepteur: String?,
    public val ligne: Int,
    public val arguments: List<ArgumentAppelKotlin>,
    public val contenu: List<NoeudAppelKotlin>,
    public val genreInline: GenreInlineKotlin,
) : NoeudAppelKotlin()

/**
 * Une EXPRESSION NON INTERPRÉTÉE du corps d'une preview (une locale, un
 * `if`, un `when` hors appel) — rendue honnêtement en texte tronqué, une
 * erreur typée côté consommateur (D3 : jamais un faux rendu).
 *
 * @property texte le texte de l'expression, retours à la ligne aplatis,
 * tronqué ;
 * @property ligne la ligne 1-indexée de l'expression.
 */
public class ExpressionNonInterpreteeKotlin(
    public val texte: String,
    public val ligne: Int,
) : NoeudAppelKotlin()

/**
 * Un argument EXPLICITE d'un appel de la preview.
 *
 * @property nom le nom de l'argument nommé (`texte =`), `null` si
 * positionnel ;
 * @property genre le genre d'interprétation de la valeur ;
 * @property texte le texte de la valeur TEL QUEL dans la source.
 */
public class ArgumentAppelKotlin(
    public val nom: String?,
    public val genre: GenreArgumentAppelKotlin,
    public val texte: String?,
)

/**
 * Un paramètre d'annotation `@Preview`, TRANCHÉ dans la source (C1 : pas
 * de schéma figé — l'évolution amont de l'annotation ne casse pas le
 * moteur, ADR 0116).
 *
 * @property nom le nom de l'argument (`widthDp`) ;
 * @property valeurTexte le texte de la valeur tel que écrit (`320`,
 * `"spec:shape=Normal"`).
 */
public class ParametrePreviewKotlin(
    public val nom: String?,
    public val valeurTexte: String?,
)

/**
 * Une PREVIEW Compose du document (C1 — `@Preview @Composable`), la
 * matière de la carte « goutte d'eau » de CodeIDE.
 *
 * @property nom le nom de la fonction preview ;
 * @property paquet le package du document (`null` si aucun) ;
 * @property plageNom la plage du NOM de la fonction (la navigation du
 * marqueur) ;
 * @property parametres les paramètres `@Preview` tranchés dans la source ;
 * @property corps le corps réduit en ARBRE D'APPELS (littéraux
 * distingués, lambda final = contenu, expressions honnêtes, inlining
 * borné) ;
 * @property coupureBudget la marche a touché le budget de nœuds (PC4 :
 * troncature SIGNALÉE, jamais silencieuse) ;
 * @property ligneAnnotation la ligne 1-indexée de l'entrée d'annotation
 * `@Preview` (le marqueur de gouttière vit sur cette ligne — PAS la
 * ligne du `fun`).
 */
public class PreviewKotlin(
    public val nom: String,
    public val paquet: String?,
    public val plageNom: PlageTexte,
    public val parametres: List<ParametrePreviewKotlin>,
    public val corps: List<NoeudAppelKotlin>,
    public val coupureBudget: Boolean,
    public val ligneAnnotation: Int,
)

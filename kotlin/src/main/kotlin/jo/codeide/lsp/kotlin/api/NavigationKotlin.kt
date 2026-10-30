// CodeIDE — SPI du moteur Kotlin LSP : navigation et symboles (module
// :kotlin, étage 2 vague 2e — fiches 031/025 du cahier : go-to-definition
// de l'usage vers l'expect KMP, symboles de document, références d'une
// fonction projet). Code NEUF jjoblab (clean-room — frontière D6 : les
// genres sont NEUTRES, l'hôte les traduit en SymbolKind/Location LSP).
package jo.codeide.lsp.kotlin.api

import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Un EMPLACEMENT neutre de déclaration ou d'usage (la matière du
 * go-to-definition et des références — l'hôte convertit en `Location`
 * LSP : uri + plage).
 *
 * @property uri l'identifiant du document porteur (URI de fichier du scan
 * ou URI de session du document ouvert) ;
 * @property plage l'emplacement en OFFSETS du texte ([debut] inclus,
 * [fin] exclu) — le NOM de la déclaration pour un site de déclaration, la
 * référence pour un usage.
 */
public class EmplacementKotlin(
    public val uri: String,
    public val plage: PlageTexte,
)

/**
 * Le genre neutre d'un SYMBOLE de document (l'hôte le traduit en
 * `SymbolKind` LSP).
 */
public enum class GenreSymboleKotlin {

    /** `class` (le genre par défaut d'une déclaration de type). */
    CLASSE,

    /** `interface`. */
    INTERFACE,

    /** `enum class`. */
    ENUM,

    /** `object` (compagnon compris). */
    OBJET,

    /** `fun` de premier niveau ou membre. */
    FONCTION,

    /** `val`/`var` de premier niveau ou membre. */
    PROPRIETE,

    /** `typealias`. */
    TYPEALIAS,
}

/**
 * Un SYMBOLE de document (le plan du fichier — fiche 025 : « symboles de
 * document d'un fichier Compose-like ») : chaque déclaration nommée du
 * document, son genre et ses plages. La liste est PLATE en ordre du
 * document (la hiérarchie relève du classement de l'étage 3).
 *
 * @property nom le nom ÉCRIT de la déclaration ;
 * @property genre le genre neutre ;
 * @property plage l'emplacement de la DÉCLARATION entière (offsets) ;
 * @property plageNom l'emplacement du NOM seul (le site de sélection).
 */
public class SymboleDocumentKotlin(
    public val nom: String,
    public val genre: GenreSymboleKotlin,
    public val plage: PlageTexte,
    public val plageNom: PlageTexte,
)

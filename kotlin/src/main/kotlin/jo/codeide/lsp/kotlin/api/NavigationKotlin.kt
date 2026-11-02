// CodeIDE — SPI du moteur Kotlin LSP : navigation et symboles (module
// :kotlin, étage 2 vague 2e — fiches 031/025 du cahier : go-to-definition
// de l'usage vers l'expect KMP, symboles de document, références d'une
// fonction projet ; étage 4 vague 4a — fiche 048 : le surlignage de
// document ECRITURE/LECTURE et la plage renommable). Code NEUF jjoblab
// (clean-room — frontière D6 : les genres sont NEUTRES, l'hôte les
// traduit en SymbolKind/Location/DocumentHighlightKind LSP).
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

/**
 * Le genre neutre d'un SURLIGNAGE de document (documentHighlight LSP —
 * étage 4 vague 4a, fiche 048 : la déclaration se surligne en ÉCRITURE,
 * chaque référence en LECTURE ; l'hôte traduit en `DocumentHighlightKind`
 * LSP `Write`/`Read`).
 */
public enum class GenreSurlignageKotlin {

    /** Le site de DÉCLARATION du symbole surligné (`Write` LSP). */
    ECRITURE,

    /** Une RÉFÉRENCE liée au symbole surligné (`Read` LSP). */
    LECTURE,
}

/**
 * Un SURLIGNAGE de document (fiche 048, comportement
 * `documentHighlight---déclaration-en-écriture,-références-en-lecture,-ombrage-exclu`)
 * : l'emplacement d'une occurrence LIÉE dans le document courant, avec son
 * genre — les surlignages vivent EN ORDRE DU DOCUMENT, la déclaration (si
 * le document la porte) en tête.
 *
 * @property plage l'emplacement de l'occurrence (offsets du texte du
 * document) ;
 * @property genre le genre du surlignage (ECRITURE pour le site de
 * déclaration, LECTURE pour une référence).
 */
public class SurlignageDocumentKotlin(
    public val plage: PlageTexte,
    public val genre: GenreSurlignageKotlin,
)

/**
 * La plage RENOMMABLE sous le point (prepareRename LSP — fiche 048,
 * comportement `plageRenommable---placeholder-exact-sur-le-nom-déclaré`)
 * : l'emplacement exact du nom désigné et le texte qui y vit — la matière
 * du dialogue de renommage de l'hôte.
 *
 * @property plage l'emplacement du nom (offsets) ;
 * @property placeholder le nom ÉCRIT sous le point (la valeur initiale
 * du champ de saisie de l'hôte).
 */
public class PlageRenommableKotlin(
    public val plage: PlageTexte,
    public val placeholder: String,
)

/**
 * Une CIBLE du GO-TO SUPER (étage 4 vague 4c — fiche 052 : ADR 0210,
 * `textDocument/superDefinition`) : l'emplacement du même-nommé dans un
 * SUPERTYPE (chemin membre : `« run  ·  Base »`) ou du supertype DIRECT
 * du type en contexte (chemin type : `« Base  ·  demo »`), avec son
 * LIBELLÉ de picker — l'hôte l'affiche tel quel (miroir des libellés de
 * l'éditeur hôte, jamais interprétés par le moteur).
 *
 * @property uri l'identifiant du fichier portant la cible ;
 * @property libelle le libellé picker (`« nom  ·  déclarant »` pour un
 * membre, `« nomSimple  ·  paquet »` pour un type) ;
 * @property plage l'emplacement du NOM de la cible (offsets).
 */
public class CibleSuperKotlin(
    public val uri: String,
    public val libelle: String,
    public val plage: PlageTexte,
)

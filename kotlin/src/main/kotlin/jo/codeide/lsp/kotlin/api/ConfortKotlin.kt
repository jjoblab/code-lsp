// CodeIDE — SPI du moteur Kotlin LSP : modèles de confort neutres (module
// :kotlin, étage 1 vague 1b). Code NEUF jjoblab (clean-room — contrat :
// fiche 049 du cahier : plages pliables et survol S2 ; les genres du pliage
// sont NEUTRES, l'hôte les traduit en `FoldingRangeKind` LSP — frontière D6).
package jo.codeide.lsp.kotlin.api

import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Le genre d'une plage pliable d'un document Kotlin (vocabulaire neutre —
 * l'hôte le traduit en `FoldingRangeKind` LSP : [IMPORTS] vers `imports`,
 * [KDOC] vers `comment`, [CLASSE] et [FONCTION] sans genre LSP).
 */
public enum class GenrePlagePliable {
    /** Le bloc des directives d'import du document. */
    IMPORTS,

    /** Un bloc de documentation KDoc (`/** … */`). */
    KDOC,

    /** Le corps d'une déclaration de type (classe, interface, enum, objet). */
    CLASSE,

    /** Le corps de bloc d'une fonction. */
    FONCTION,
}

/**
 * Une plage pliable d'un document Kotlin (fiche 049 : classe, fonctions,
 * imports et KDoc).
 *
 * @property plage l'emplacement pliable en OFFSETS du texte ([debut] inclus,
 * [fin] exclu) — l'hôte convertit en lignes/colonnes LSP ;
 * @property genre le genre neutre de la plage.
 */
public class PlagePliableKotlin(
    public val plage: PlageTexte,
    public val genre: GenrePlagePliable,
)

/**
 * Le survol d'un point du document (S2, fiche 049) : la signature de la
 * déclaration visée, accompagnée de sa documentation KDoc lorsque le
 * document (ou la source externe scannée) en porte une.
 *
 * @property signature la signature rendue de la déclaration (forme écrite
 * conservatrice — par ex. `fun saluer(destinataire: String): String`) ;
 * @property documentation la documentation KDoc qui précède la déclaration,
 * nettoyée de ses marqueurs ; `null` (conservateur) si la déclaration n'a
 * pas de KDoc — la signature voyage alors seule.
 */
public class SurvolKotlin(
    public val signature: String,
    public val documentation: String?,
)

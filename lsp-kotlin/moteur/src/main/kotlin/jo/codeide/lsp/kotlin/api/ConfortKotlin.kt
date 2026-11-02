// CodeIDE — SPI du moteur Kotlin LSP : modèles de confort neutres (module
// :kotlin, étage 1 vague 1b ; étage 5 vague 5a — genre BLOC et édition de
// texte du formateur borné, fiches 014/019). Code NEUF jjoblab (clean-room —
// contrat : fiches 049/014/019 du cahier ; les genres du pliage sont
// NEUTRES, l'hôte les traduit en `FoldingRangeKind` LSP — frontière D6).
package jo.codeide.lsp.kotlin.api

import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Le genre d'une plage pliable d'un document Kotlin (vocabulaire neutre —
 * l'hôte le traduit en `FoldingRangeKind` LSP : [IMPORTS] vers `imports`,
 * [KDOC] vers `comment`, [CLASSE]/[FONCTION]/[BLOC] sans genre LSP).
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

    /** Un corps à accolades SANS genre dédié (étage 5 vague 5a — fiche 014 :
     * branche de structure de contrôle, lambda, bloc d'un `when`, commentaire
     * de bloc multi-ligne). */
    BLOC,
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

/**
 * Une ÉDITION DE TEXTE neutre du formateur borné (étage 5 vague 5a —
 * fiche 019, `textDocument/formatting` et `rangeFormatting` L2) : la plage
 * remplacée et le nouveau texte — l'hôte l'applique comme un `TextEdit`
 * LSP (plage en offsets du document, une plage vide est une INSERTION à
 * [PlageTexte.debut]).
 *
 * Le contrat est celui du formateur BORNÉ (ADR 0202 miroir) : le contenu
 * des lignes n'est JAMAIS réécrit — seule la plénitude de tête d'une ligne
 * est remplacée ; une édition à l'identique n'est JAMAIS émise
 * (idempotence).
 *
 * @property plage la plage remplacée (offsets, [PlageTexte.debut] inclus
 * [PlageTexte.fin] exclu) ;
 * @property texte le texte de remplacement (le blanc de tête canonique).
 */
public class EditionTexteKotlin(
    public val plage: PlageTexte,
    public val texte: String,
)

// CodeIDE — SPI du moteur Kotlin LSP : capacités L2 (module :kotlin, étage
// 6 vague 6b). Code NEUF jjoblab (contrat : fiche 015 du cahier
// docs/cahier-specifications-kotlin/ — les trois capacités L2 restantes :
// indices en ligne, symboles d'espace, chaînes de sélection — par le SPI
// (chemin de production D6), moteur réel). Types NEUTRES : aucune
// dépendance LSP4J ni Android — frontière D6.
package jo.codeide.lsp.kotlin.api

import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Le genre d'un INDICE EN LIGNE (inlay hint — fiche 015) : ce que l'indice
 * décore, dans le vocabulaire neutre du moteur (l'hôte traduit en
 * `InlayHintKind` LSP).
 */
public enum class GenreIndiceKotlin {

    /** Le NOM d'un paramètre devant un argument positionnel (`a =` avant `1`). */
    PARAMETRE,

    /** Le TYPE d'une déclaration sans type écrit (`: Int` après `entier`). */
    TYPE,

    /** Le `it` IMPLICITE d'un littéral lambda (libellé complet `it: Type`). */
    LAMBDA,
}

/**
 * Un INDICE EN LIGNE (fiche 015) : un libellé décoratif ancré sur une
 * plage du document — la preuve seule s'affiche (D3 : un appel sans
 * candidate prouvée ne rend AUCUN indice).
 *
 * @property libelle le texte de l'indice (un nom de paramètre, un type,
 * `it: Type` pour le récepteur implicite d'une lambda) ;
 * @property genre le genre neutre de l'indice ;
 * @property debut l'offset de DÉBUT de l'ancre (l'argument positionnel, le
 * nom de déclaration, l'accolade ouvrante du littéral) ;
 * @property fin l'offset de FIN de l'ancre (le plus souvent `debut + 1`,
 * l'indice vit sur la tête de l'ancre).
 */
public class IndiceKotlin(
    public val libelle: String,
    public val genre: GenreIndiceKotlin,
    public val debut: Int,
    public val fin: Int,
)

/**
 * Un SYMBOLE D'ESPACE (workspace/symbol — fiche 015) : une déclaration de
 * PREMIER NIVEAU d'un document OUVERT ou d'un fichier du SCAN, dont le nom
 * contient la requête. Les LOCALES ne remontent JAMAIS (la palette
 * d'espace parcourt les déclarations de premier niveau seulement).
 *
 * @property nom le nom ÉCRIT de la déclaration ;
 * @property genre le genre neutre (le vocabulaire des symboles de
 * document) ;
 * @property uri l'identifiant du fichier porteur (l'URI du document ouvert,
 * le chemin du fichier scanné) ;
 * @property plageNom l'emplacement du NOM dans son fichier (`null` si le
 * scan n'en a pas retenu — la sélection relève alors du client).
 */
public class SymboleEspaceKotlin(
    public val nom: String,
    public val genre: GenreSymboleKotlin,
    public val uri: String,
    public val plageNom: PlageTexte?,
)

/**
 * Une CHAÎNE DE SÉLECTION (selectionRange — fiche 015) : les maillons
 * englobants d'une position, du PLUS PROFOND au FICHIER ENTIER — chaque
 * maillon contient le précédent, le dernier est toujours le fichier.
 *
 * @property plages les maillons en ordre CROISSANT d'étendue (le premier
 * est le nœud le plus profond contenant la position, le dernier est la
 * plage du fichier entier) ; une position hors arbre porte le FICHIER
 * SEUL (rien n'est inventé).
 */
public class ChaineSelectionKotlin(
    public val plages: List<PlageTexte>,
)

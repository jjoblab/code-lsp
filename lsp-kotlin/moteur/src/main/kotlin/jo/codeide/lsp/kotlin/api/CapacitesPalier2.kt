// CodeIDE — SPI du moteur Kotlin LSP : modèles des CAPACITÉS PALIER 2
// (module :kotlin, vague V-I2 — ADR 0136 étape B.3 : semanticTokens borné
// KH1, hiérarchies de types et d'appels, codeLens paresseux). Code NEUF
// jjoblab (clean-room — miroir des modèles CapacitesArbre.kt de V-I1 :
// le moteur rend des modèles NEUTRES, l'hôte les traduit en LSP).
package jo.codeide.lsp.kotlin.api

import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Un JETON SÉMANTIQUE Kotlin (V-I2 — ADR 0136 B.3, borné KH1/ADR 0123) :
 * un identifiant PORTANT du document, son genre PROUVÉ et ses
 * modificateurs ÉCRITS. Le jeton ne porte JAMAIS la variance ni la
 * nullabilité inférées (risque R2 de l'ADR 0136 : la borne écrite).
 *
 * @property offset le début du jeton (inclusif) — le NOM seul, jamais le
 *   `.` ni les arguments ;
 * @property longueur la longueur du jeton ;
 * @property genre la catégorie (voir [GenreJetonSemantiqueKotlin]) ;
 * @property declaration vrai ssi ce jeton EST la déclaration ;
 * @property statique le modificateur de portée `companion` ;
 * @property final le modificateur `val` (le miroir Kotlin de `final`) ;
 * @property abstrait le modificateur d'un membre SANS corps écrit.
 */
public class JetonSemantiqueKotlin(
    public val offset: Int,
    public val longueur: Int,
    public val genre: GenreJetonSemantiqueKotlin,
    public val declaration: Boolean,
    public val statique: Boolean,
    public val final: Boolean,
    public val abstrait: Boolean,
)

/**
 * Le genre d'un jeton sémantique Kotlin — les catégories PROUVÉES au
 * palier 2 (la légende LSP est l'index de cet enum, ordre contractuel :
 * `class`, `interface`, `enum`, `decorator`, `method`, `property`,
 * `parameter`, `variable` — le miroir de la légende Java V-G).
 */
public enum class GenreJetonSemantiqueKotlin {

    /** Un nom de CLASSE (déclaration ou référence résolue). */
    CLASSE,

    /** Un nom d'INTERFACE (déclaration ou référence résolue). */
    INTERFACE,

    /** Un nom d'ENUM (déclaration ou référence résolue). */
    ENUM,

    /** Un nom d'ANNOTATION (l'usage `@Anno`). */
    ANNOTATION,

    /** Un nom de FONCTION (déclaration ou appel résolu). */
    METHODE,

    /** Un nom de PROPRIÉTÉ (membre, déclaration ou sélecteur résolu). */
    PROPRIETE,

    /** Un PARAMÈTRE de fonction. */
    PARAMETRE,

    /** Une LOCALE (`val`/`var` de corps). */
    VARIABLE,
}

/**
 * Une ITEM de hiérarchie de TYPES (V-I2 — miroir LSP `TypeHierarchyItem`,
 * modèle neutre) : un type avec sa plage de DÉCLARATION. Une item de
 * maillon CLASSPATH porte `uri` VIDE (l'affichage borné honnête : le
 * nom FQCN sans navigation — le décompilateur relève de l'hôte).
 *
 * @property nom le nom SIMPLE du type ;
 * @property uri l'uri du fichier déclarant (VIDE pour un maillon
 * classpath) ;
 * @property plage la plage du NOM de la déclaration ;
 * @property genre le genre (CLASSE/INTERFACE/ENUM/OBJET/JAVA — le kind).
 */
public class ItemHierarchieKotlinApi(
    public val nom: String,
    public val uri: String,
    public val plage: PlageTexte,
    public val genre: String,
)

/**
 * Une ITEM de hiérarchie d'APPELS (V-I2 — miroir LSP `CallHierarchyItem`)
 * : un APPELABLE (fonction de premier niveau du projet) avec sa plage.
 *
 * @property nom le nom de l'appelable ;
 * @property uri l'uri du fichier déclarant ;
 * @property plage la plage du NOM de la déclaration ;
 * @property detail la signature écrite (types écrits, sans variance).
 */
public class ItemAppelKotlinApi(
    public val nom: String,
    public val uri: String,
    public val plage: PlageTexte,
    public val detail: String,
)

/**
 * Une arête de hiérarchie d'appels (V-I2 — miroir LSP
 * `CallHierarchyIncomingCall`/`OutgoingCall`) : l'autre bout de l'appel
 * et les plages des APPELS dans le fichier de l'item courante.
 *
 * @property item l'appelable de l'autre bout ;
 * @property plagesAppel les plages des appels dans le fichier de CETTE
 * item (les flèches FROM/TO de LSP).
 */
public class AreteAppelKotlinApi(
    public val item: ItemAppelKotlinApi,
    public val plagesAppel: List<PlageTexte>,
)

/**
 * Une LENTILLE de document (V-I2 — codeLens par occurrences paresseuses,
 * périmètre BORNÉ au document) : une déclaration de premier niveau et le
 * COMPTE de ses usages dans le document.
 *
 * @property plage la plage du NOM de la déclaration ;
 * @property nom le nom déclaré ;
 * @property usages le compte d'usages dans le document (0 pour une
 * déclaration non référencée — la lentille honnête).
 */
public class LentilleDocumentKotlin(
    public val plage: PlageTexte,
    public val nom: String,
    public val usages: Int,
)

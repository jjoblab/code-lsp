package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.diagnostics.Plage
import java.nio.file.Path

/**
 * Genre d'une cible de navigation (étage N4 — cahier fiches 049/060/062) :
 * le RÉGIME qui a servi la cible — la discipline des trois chemins de
 * l'audit navigation (fiche 049 : « une cible PROJET n'atterrit JAMAIS
 * dans le stub décompilé ; une cible BINAIRE n'atterrit jamais dans les
 * sources »).
 *
 * L'ordre des constantes reflète l'ordre de routage du
 * [ServiceNavigation] : intra-fichier d'abord, projet ensuite, binaire en
 * dernier recours — jamais l'inverse.
 */
public enum class GenreCible {
    /** Déclaration dans le document COURANT (locale, champ, méthode ou type de l'unité ouverte). */
    SOURCE_COURANTE,

    /** Déclaration dans un AUTRE fichier source du projet (croix-fichiers par annuaire de noms). */
    SOURCE_PROJET,

    /** Cible binaire décompilée — URI `jdt://decompiled/…`, plage vide. */
    BINAIRE_DECOMPILE,
}

/**
 * Une cible de navigation (étage N4) : le miroir moteur d'une `Location`
 * LSP servie par `definition` / `typeDefinition` / `implementation`.
 *
 * Invariants de construction (fiches 049/060/062) :
 *  - [genre] [SOURCE_COURANTE] ou [SOURCE_PROJET] : [chemin] non null,
 *    [uri] en forme `file://` et [plage] désigne le JETON nom déclaré ;
 *  - [genre] [BINAIRE_DECOMPILE] : [chemin] null, [uri] exactement
 *    `jdt://decompiled/<chemin-fqcn-en-slashes>.java` et [plage] VIDE
 *    (`0:0–0:0` — le câble « GO TO sur cible binaire » : le client appelle
 *    ensuite `decompiledSource(fqcn)` pour ouvrir le tampon lecture-seule).
 *
 * @property genre le régime ayant servi la cible.
 * @property chemin le fichier source porteur (null pour un binaire).
 * @property uri l'URI rendue au client (`file://` ou `jdt://decompiled/`).
 * @property plage la plage du NOM déclaré (vide pour un binaire).
 * @property nomSimple le nom simple du symbole visé (libellé picker).
 */
public data class CibleNavigation(
    public val genre: GenreCible,
    public val chemin: Path?,
    public val uri: String,
    public val plage: Plage,
    public val nomSimple: String,
)

/**
 * Une cible « GO TO Super » (étage N4 — cahier fiches 049/060) : le miroir
 * moteur d'une `NavigationTarget` interne — chemin + plage + libellé
 * picker. Le libellé est EXACTEMENT « nom  ·  conteneur » (DEUX espaces de
 * part et d'autre du séparateur « · ») : la méthode outrépassée porte le
 * nom simple de la classe déclarante en conteneur (« run  ·  Base »), la
 * retombée sur un supertype direct porte son PAQUET (« Base  ·  demo »).
 *
 * @property nom le nom du membre ou du type visé.
 * @property conteneur le conteneur affiché (classe déclarante ou paquet).
 * @property chemin le fichier source du supertype (null si binaire).
 * @property plage la plage du NOM dans la source du supertype (null si binaire).
 */
public data class CibleSuper(
    public val nom: String,
    public val conteneur: String,
    public val chemin: Path?,
    public val plage: Plage?,
) {
    /** Libellé picker exact (deux espaces autour du séparateur « · »). */
    public val libelle: String get() = "$nom  ·  $conteneur"
}

/**
 * Un usage trouvé par le [ServiceUsages] (étage N4 — QueryExecutor sur
 * index de mots) : une occurrence d'un symbole, dans un fichier du projet.
 *
 * @property chemin le chemin du fichier porteur de l'occurrence.
 * @property plage la plage du JETON de l'occurrence (référence ou nom déclaré).
 * @property estDeclaration vrai ssi l'occurrence EST la déclaration du
 *   symbole (filtrable par le paramètre `inclureDeclaration`).
 */
public data class Usage(
    public val chemin: String,
    public val plage: Plage,
    public val estDeclaration: Boolean,
)

/**
 * Genre de surbrillance documentaire (miroir moteur des
 * `DocumentHighlightKind` du protocole LSP — l'étage N8 recopiera les
 * valeurs numériques) : le moteur définit le contrat, la couche LSP4J le
 * traduira.
 *
 * @property codeLsp la valeur `kind` du protocole LSP 3.17.
 */
public enum class GenreSurbrillance(public val codeLsp: Int) {
    /** Occurrence textuelle (méthode, type — ni lecture ni écriture). */
    TEXTE(1),

    /** Lecture d'une variable. */
    LECTURE(2),

    /** Écriture d'une variable (déclaration ou cible d'affectation). */
    ECRITURE(3),
}

/**
 * Une surbrillance du document courant (miroir moteur d'un
 * `DocumentHighlight` LSP) : la plage du jeton et son genre.
 *
 * @property plage la plage du JETON de l'occurrence.
 * @property genre le genre de surbrillance.
 */
public data class Surbrillance(
    public val plage: Plage,
    public val genre: GenreSurbrillance,
)

/**
 * Un maillon d'une chaîne de sélection (étage N4 — cahier fiche 027) :
 * une plage de la chaîne STRICTEMENT croissante (contrat LSP 3.17 :
 * chaque niveau parent CONTIENT STRICTEMENT le précédent — croissance
 * stricte de la longueur ET inclusion).
 */
public data class MaillonSelection(public val plage: Plage)

/**
 * Une chaîne de sélection (fiche 027) : l'escalade complète depuis le
 * jeton jusqu'à l'unité — jeton → expression → instruction → bloc →
 * déclaration → type → unité. Le SOMMET couvre toute la source
 * (`[0, longueur]`) ; chaque maillon contient strictement le précédent.
 *
 * @property maillons les plages par ordre croissant strict.
 */
public class ChaineSelection(public val maillons: List<MaillonSelection>) : Iterable<MaillonSelection> {
    /** Le nombre de niveaux de la chaîne. */
    public val taille: Int get() = maillons.size

    /** Le maillon sommet (toute la source) — null si chaîne vide. */
    public val sommet: MaillonSelection? get() = maillons.lastOrNull()

    /** Itère les maillons du plus petit (jeton) au plus grand (unité). */
    override fun iterator(): Iterator<MaillonSelection> = maillons.iterator()
}

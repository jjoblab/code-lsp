package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.outils.EditionTexte

/**
 * Genre d'une cible de renommage (étage N5 — cahier fiches 026 et 061) :
 * la famille du symbole visé, rendue au client sous forme de libellé
 * court (« method », « field », « type », « local ») — le vocabulaire de
 * la clé structurelle qui isole les homonymes.
 *
 * @property libelle le libellé du genre servi au client (fiche 026 :
 *   kind « method » / « field » / « type »).
 */
public enum class GenreCibleRenommage(public val libelle: String) {
    /** Une méthode (déclaration ou envoi de message). */
    METHODE("method"),

    /** Un champ (déclaration, référence simple ou qualifiée). */
    CHAMP("field"),

    /** Un type (classe, interface, énumération — constructeur compris). */
    TYPE("type"),

    /** Une variable LOCALE ou un paramètre (périmètre du fichier). */
    LOCALE("local"),
}

/**
 * La cible d'un renommage (étage N5 — cahier fiche 026) : le symbole
 * identifié au caret par BINDINGS, décrit pour le client — nom actuel,
 * genre, clé structurelle et périmètre.
 *
 * La clé structurelle est celle de la fiche 026 (notes de réécriture) :
 * « method:paquet.Classe.nom( », « field:paquet.Classe.nom »,
 * « type:paquet.Nom » — c'est elle qui isole les HOMONYMES (le champ
 * `name` de `demo.User` n'est jamais le champ `name` de `demo.Order`).
 * Les LOCALES n'ont pas de clé croix-fichiers : leur renommage est
 * confiné au fichier courant ([fileLocal] vrai).
 *
 * @property nomActuel le nom du symbole (sélecteur, nom de champ, nom
 *   simple du type, nom de la locale).
 * @property genre la famille du symbole.
 * @property cleStructurelle la clé structurelle (null pour une locale).
 * @property fileLocal vrai ssi le renommage est confiné au fichier
 *   courant (une locale ne sort JAMAIS de son unité — court-circuit du
 *   balayage des racines sources).
 * @property jeton la plage du JETON visé dans la source de l'arbre.
 */
public data class CibleRenommage(
    public val nomActuel: String,
    public val genre: GenreCibleRenommage,
    public val cleStructurelle: String?,
    public val fileLocal: Boolean,
    public val jeton: Plage,
) {
    /** Vrai ssi la cible est un TYPE (constructeur compris — fiche 026). */
    public val estType: Boolean get() = genre == GenreCibleRenommage.TYPE

    /**
     * Le libellé du genre servi au client (« method », « field »,
     * « type », « local »).
     */
    public val kind: String get() = genre.libelle
}

/**
 * Une occurrence à retoucher (étage N5 — cahier fiche 026) : la plage
 * EXACTE du jeton dans son fichier — la déclaration, les usages simples
 * et qualifiés, et pour un type les IMPORTS et CONSTRUCTEURS compris.
 *
 * @property plage la plage du JETON (le texte couvert est exactement le
 *   nom du symbole — contrat de la fiche 026).
 * @property estDeclaration vrai ssi l'occurrence EST la déclaration.
 */
public data class OccurrenceRenommage(
    public val plage: Plage,
    public val estDeclaration: Boolean,
)

/**
 * Genre d'une COLLISION typée (étage N5 — ADR 0014 : « liste typée de
 * collisions », les quatre familles de l'étude D10 traduites au
 * moteur). Le renommage « sûr » les détecte AVANT écriture ; la
 * projection LSP des conflits (dialogue de décision utilisateur) est
 * un choix du client, le moteur ne décide JAMAIS à sa place.
 *
 * @property libelle le libellé machine du genre de collision.
 */
public enum class GenreConflit(public val libelle: String) {
    /** Le nouveau nom n'est pas un identifiant Java valide (mot-clé compris). */
    IDENTIFIANT_INVALIDE("identifiant.invalide"),

    /** Le nouveau nom est déjà porté par un membre du type déclarant (tout genre confondu) ou par une locale du périmètre. */
    NOM_PRIS("nom.pris"),

    /**
     * Le renommé masquerait (ou serait masqué par) une entité visible du
     * périmètre — Java l'autorise, le moteur le SIGNALE (famille
     * « member-hides-outer » de l'étude D10).
     */
    MASQUAGE("masquage"),

    /**
     * Une méthode outrépassée dans un sous-type ne sera PAS suivie (opt-in
     * explicite — comportement volontaire documenté) : sa déclaration
     * garde l'ancien nom et son `@Override` devient faux.
     */
    OVERRIDE_NON_SUIVI("override.nonSuivi"),

    /** Après renommage, une référence ne résout plus (ou re-résout ailleurs) — diagnostic NOUVEAU sur l'aperçu. */
    REFERENCE_BRISE("reference.brisee"),

    /** Collision résiduelle détectée par le diagnoseur sur l'aperçu (déclaration dupliquée, Override cassé…). */
    CASSURE("cassure"),
}

/**
 * Une collision typée (étage N5 — ADR 0014) : le résultat de la phase
 * CONFLITS du protocole de renommage. Le critère de sortie de l'étage
 * — « 0 conflit manqué (oracle) » — s'entend sur le corpus doré.
 *
 * @property genre la famille typée de la collision.
 * @property message le message FR décrivant la collision.
 * @property chemin le fichier où vit la collision (null si hors source).
 * @property plage la plage de la collision dans ce fichier (null si
 *   hors source).
 */
public data class ConflitRenommage(
    public val genre: GenreConflit,
    public val message: String,
    public val chemin: String? = null,
    public val plage: Plage? = null,
)

/**
 * La réponse de `prepareRename` (étage N5 — cahier fiche 061) : le
 * miroir moteur de la forme LSP `{ range, placeholder }`.
 *
 * Contrat de la fiche 061 : le placeholder est le jeton EXACT, et la
 * plage couvre TOUT le jeton même si le caret vit AU MILIEU du nom —
 * jamais `[caret, caret + longueur)` comme le faisait l'ancien code.
 *
 * @property plage la plage du jeton ENTIER.
 * @property placeholder le texte EXACT du jeton.
 */
public data class PreparationRenommage(
    public val plage: Plage,
    public val placeholder: String,
)

/**
 * Les capacités d'édition d'espace de travail annoncées par le CLIENT
 * (étage N5 — cahier fiche 061) : le miroir moteur du bloc
 * `workspace.workspaceEdit` de `InitializeParams`. Le client réel
 * (cel-lsp) n'annonce PAS `documentChanges` et reçoit donc la forme
 * héritée `changes`.
 *
 * @property documentChanges vrai ssi le client annonce
 *   `workspace.workspaceEdit.documentChanges` (forme versionnée
 *   négociée).
 */
public data class CapsEspaceDeTravailClient(
    public val documentChanges: Boolean = false,
)

/**
 * Une édition de document VERSIONNÉE (étage N5 — cahier fiche 061) :
 * le miroir moteur d'un `TextDocumentEdit` LSP — les éditions d'UN
 * document, portant la version COURANTE posée par le didOpen (null si
 * le document n'est pas ouvert — les fichiers balayés non ouverts).
 *
 * @property uri l'URI du document édité.
 * @property version la version courante du document ouvert (null si
 *   inconnue — document non ouvert).
 * @property editions les éditions ordonnées (offsets croissants,
 *   plages disjointes).
 */
public data class EditionDocumentVersionnee(
    public val uri: String,
    public val version: Long?,
    public val editions: List<EditionTexte>,
)

/**
 * Le miroir moteur d'un `WorkspaceEdit` LSP (étage N5 — cahier
 * fiche 061) : les éditions d'un renommage sous L'UNE des DEUX formes
 * négociées — JAMAIS les deux.
 *
 *  - forme `changes` (héritée) : une map URI → éditions, servie quand
 *    le client n'annonce pas `documentChanges` ;
 *  - forme `documentChanges` (versionnée) : une liste d'éditions de
 *    documents versionnés, servie quand il l'annonce.
 *
 * L'exclusivité est STRUCTURELLE : les fabriques [formeChanges] et
 * [formeDocumentChanges] sont les seules portes de construction, la
 * forme non servie rend null.
 *
 * @property changes la forme héritée (null ssi la forme versionnée est
 *   servie).
 * @property documentChanges la forme versionnée (null ssi la forme
 *   héritée est servie).
 */
public class EditionEspaceDeTravail private constructor(
    public val changes: Map<String, List<EditionTexte>>?,
    public val documentChanges: List<EditionDocumentVersionnee>?,
) {
    /** Le nombre TOTAL d'éditions toutes formes confondues. */
    public fun nombreEditions(): Int = changes?.values?.sumOf { it.size }
        ?: documentChanges?.sumOf { it.editions.size }
        ?: 0

    /** Représentation lisible (les deux formes ne coexistent jamais). */
    override fun toString(): String =
        if (changes != null) "EditionEspaceDeTravail(changes=${changes.size} uri)" else
            "EditionEspaceDeTravail(documentChanges=${documentChanges?.size ?: 0} documents)"

    public companion object {
        /** Construit la forme HÉRITÉE `changes` (URI → éditions). */
        @JvmStatic
        public fun formeChanges(changes: Map<String, List<EditionTexte>>): EditionEspaceDeTravail =
            EditionEspaceDeTravail(changes, null)

        /** Construit la forme VERSIONNÉE `documentChanges` (jamais les deux). */
        @JvmStatic
        public fun formeDocumentChanges(editions: List<EditionDocumentVersionnee>): EditionEspaceDeTravail =
            EditionEspaceDeTravail(null, editions)
    }
}

/**
 * Position d'une erreur de renommage (étage N5 — cahier fiche 061) :
 * le miroir moteur d'une `Position` LSP, en unités UTF-16, PLUS l'offset
 * absolu du moteur (la couche LSP de N8 recopiera ligne/caractère).
 *
 * @property ligne la ligne (base 0) en unités UTF-16.
 * @property caractere le caractère dans la ligne (base 0, UTF-16).
 * @property offset l'offset absolu dans la source.
 */
public data class PositionSource(
    public val ligne: Int,
    public val caractere: Int,
    public val offset: Int,
)

/**
 * Les données structurées d'une erreur de renommage (étage N5 — cahier
 * fiche 061) : le miroir moteur du `data` de la `ResponseErrorException`
 * du domaine — code machine + position.
 *
 * @property code le code machine (« renommage.sansCible »).
 * @property position la position du caret fautif (null si hors source).
 */
public data class DonneesErreurRenommage(
    public val code: String,
    public val position: PositionSource?,
)

/**
 * Erreur typée « sans cible renommable » (étage N5 — cahier fiche 061,
 * comportement I8) : le miroir moteur de la `ResponseErrorException`
 * RequestFailed — le renommage SANS cible ne rend JAMAIS une édition
 * vide silencieuse, il REJETTE avec un code, un message FR et des
 * données structurées.
 *
 * @property position la position du caret fautif.
 */
public class RenommageSansCible(public val position: PositionSource?) :
    RuntimeException(
        position?.let { "Aucun symbole renommable à la position ${it.ligne}:${it.caractere}" }
            ?: "Aucun symbole renommable",
    ) {
    /** Le code d'erreur LSP exact : RequestFailed (-32803). */
    public val codeLsp: Int = CODE_REQUEST_FAILED

    /** Les données structurées (code machine « renommage.sansCible » + position). */
    public val donnees: DonneesErreurRenommage = DonneesErreurRenommage(CODE_MACHINE_SANS_CIBLE, position)

    public companion object {
        /** Le code d'erreur protocole `RequestFailed` (fiche 061 : exact). */
        public const val CODE_REQUEST_FAILED: Int = -32803

        /** Le code machine de l'erreur « sans cible » (fiche 061 : exact). */
        public const val CODE_MACHINE_SANS_CIBLE: String = "renommage.sansCible"
    }
}

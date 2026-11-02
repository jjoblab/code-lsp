package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.interne.NatureItem

/**
 * Kind LSP d'un item de complétion (étage N2 — cahier fiche 050).
 *
 * Miroir des valeurs numériques du protocole LSP 3.17 (`CompletionItemKind`)
 * — spécification publique du protocole, cotée côté client par le badge de
 * type du popup. Le serveur LSP complet arrive à l'étage N8 (cycle de vie &
 * protocole riche) ; l'étage N2 verrouille DÈS MAINTENANT la chaîne de
 * mapping complète `nature moteur → kind LSP (+ champ data)`, c'est le
 * contrat visible par le client.
 */
public enum class KindLsp(
    /** Valeur numérique du kind dans le protocole LSP. */
    public val code: Int,
) {
    /** Texte brut. */
    TEXT(1),

    /** Méthode. */
    METHOD(2),

    /** Fonction. */
    FUNCTION(3),

    /** Constructeur. */
    CONSTRUCTOR(4),

    /** Champ. */
    FIELD(5),

    /** Variable. */
    VARIABLE(6),

    /** Classe. */
    CLASS(7),

    /** Interface. */
    INTERFACE(8),

    /** Module (les sous-paquets — fiche 050/053). */
    MODULE(9),

    /** Propriété. */
    PROPERTY(10),

    /** Unité. */
    UNIT(11),

    /** Valeur. */
    VALUE(12),

    /** Enum (les constantes — fiche 007). */
    ENUM(13),

    /** Mot-clé. */
    KEYWORD(14),

    /** Extrait de code (snippet — stubs d'override, fiche 007). */
    SNIPPET(15),

    /** Couleur. */
    COLOR(16),

    /** Fichier. */
    FILE(17),

    /** Référence. */
    REFERENCE(18),

    /** Dossier. */
    FOLDER(19),

    /** Membre d'enum. */
    ENUM_MEMBER(20),

    /** Constante. */
    CONSTANT(21),

    /** Structure. */
    STRUCT(22),

    /** Événement. */
    EVENT(23),

    /** Opérateur. */
    OPERATOR(24),

    /** Paramètre de type. */
    TYPE_PARAMETER(25),
}

/**
 * Correspondance des natures du moteur vers les kinds LSP (étage N2 —
 * fiche 050 : vérification BOUT-EN-BOUT côté moteur de la chaîne
 * `nature → CompletionItemKind (+ champ data)`).
 *
 * Le contrat de la fiche : Method/Field pour les membres, Module pour les
 * sous-paquets, Class pour les types — et Class + `data = "annotation"`
 * pour les types annotation (seule distinction possible en LSP).
 */
public object CorrespondanceKindsLsp {

    /**
     * Valeur du champ `data` portée par les items de type annotation
     * (fiche 050 : `data = "annotation"` exactement — badge « @ » côté
     * client).
     */
    public const val MARQUEUR_ANNOTATION: String = "annotation"

    /**
     * Kind LSP d'une nature du moteur.
     *
     * @param nature la nature de l'item.
     * @return le kind LSP correspondant (jamais null — les natures du moteur
     *   ont toutes un kind).
     */
    public fun kindDe(nature: NatureItem): KindLsp = when (nature) {
        NatureItem.METHODE -> KindLsp.METHOD
        NatureItem.CHAMP -> KindLsp.FIELD
        NatureItem.VARIABLE -> KindLsp.VARIABLE
        NatureItem.PARAMETRE -> KindLsp.VARIABLE
        NatureItem.CLASSE -> KindLsp.CLASS
        NatureItem.INTERFACE -> KindLsp.INTERFACE
        NatureItem.PAQUET -> KindLsp.MODULE
        NatureItem.MOT_CLE -> KindLsp.KEYWORD
        NatureItem.CONSTANTE_ENUM -> KindLsp.ENUM
        NatureItem.EXTRAIT -> KindLsp.SNIPPET
        NatureItem.ANNOTATION -> KindLsp.CLASS
    }

    /**
     * Marqueur `data` LSP d'une nature (fiche 050) : les types annotation
     * voyagent avec `data = "annotation"` (le protocole n'a pas de kind
     * dédié pour un type annotation) ; les autres natures ne portent rien.
     *
     * @param nature la nature de l'item.
     * @return le marqueur `data`, ou null.
     */
    public fun marqueurDe(nature: NatureItem): String? = when (nature) {
        NatureItem.ANNOTATION -> MARQUEUR_ANNOTATION
        else -> null
    }
}

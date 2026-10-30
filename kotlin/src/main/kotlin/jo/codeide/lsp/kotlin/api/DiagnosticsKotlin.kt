// CodeIDE — SPI du moteur Kotlin LSP : diagnostics neutres (module :kotlin,
// étage 1 vague 1b). Code NEUF jjoblab (clean-room — contrat : fiches 049 du
// cahier + cadrage LSP_KOTLIN.md §5 K2 « diagnostics NEUTRES, le moteur ne
// voit pas LSP4J » ; les tags et informations liées reprennent la demande
// KP15 du plan de stabilisation ancien — l'hôte les traduit en
// DiagnosticTag/RelatedInformation LSP).
package jo.codeide.lsp.kotlin.api

import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * La sévérité d'un diagnostic Kotlin, en vocabulaire neutre (l'hôte la
 * traduit en `DiagnosticSeverity` LSP).
 */
public enum class SeveriteKotlin {
    /** Une faute certaine (erreur de syntaxe du parseur). */
    ERREUR,

    /** Un signalement utile mais non bloquant (imports). */
    AVERTISSEMENT,

    /** Une information (aucun usage à cet étage). */
    INFORMATION,
}

/**
 * Le code d'un diagnostic Kotlin — le vocabulaire `kt.*` conservateur du
 * catalogue (fiche 049 : `kt.syntax`, `kt.unusedImport`,
 * `kt.conflictingImport` ; étage 2 : `kt.unresolved`, `kt.missingReturn` —
 * fiches 017/022/006).
 *
 * @property code la forme LSP du code (voyage tel quel vers l'hôte).
 */
public enum class CodeDiagnosticKotlin(public val code: String) {
    /** Erreur de syntaxe : marqueur d'erreur du parseur, message amont repris. */
    SYNTAXE("kt.syntax"),

    /** Import inutilisé : nom effectif absent des identifiants du fichier. */
    IMPORT_INUTILISE("kt.unusedImport"),

    /** Imports conflictuels : même nom effectif, chemins qualifiés distincts. */
    IMPORT_CONFLICTUEL("kt.conflictingImport"),

    /**
     * Référence non résolue (étage 2, fiches 017/022/006/046) : preuve
     * d'ABSENCE — un nom introuvable de TOUTES les portées (noms nus) ou un
     * sélecteur absent du jeu de membres COMPLET d'un récepteur typé du
     * projet (ADR 0223 : la sélection par types complets). Émise seulement
     * quand l'univers des preuves est établi (classpath posé et couvert,
     * scan non partiel — le régime dégradé ne prouve rien).
     */
    REFERENCE_NON_RESOLUE("kt.unresolved"),

    /**
     * Retour manquant (étage 2, fiches 017/046) : un corps de BLOC sans
     * `return` ni `throw` pour un type de retour écrit autre que `Unit`.
     */
    RETOUR_MANQUANT("kt.missingReturn"),

    /**
     * Nombre d'arguments (étage 2, fiches 017/046 — famille VAGUE 2b : le
     * code est RÉSERVÉ au vocabulaire, aucune émission à la vague 2a ; les
     * témoins d'arité y seront les seules déclarations PROPRES — une
     * surcharge en cours de frappe est écartée, fiche 017).
     */
    NOMBRE_ARGUMENTS("kt.argumentCount"),
}

/**
 * Un tag de diagnostic Kotlin (demande KP15) — l'hôte le traduit en
 * `DiagnosticTag` LSP.
 */
public enum class TagDiagnosticKotlin {
    /**
     * Le diagnostic porte sur du code INUTILE (import jamais référencé) —
     * l'hôte le rend en `DiagnosticTag.Unnecessary` : l'élément se grise.
     */
    INUTILE,
}

/**
 * Une information liée à un diagnostic (demande KP15) : l'emplacement d'un
 * AUTRE point du document qui participe au problème — l'hôte la traduit en
 * `DiagnosticRelatedInformation` LSP.
 *
 * @property plage l'emplacement lié dans le document diagnostiqué ;
 * @property message le message rendu à cet emplacement.
 */
public class InformationLieeKotlin(
    public val plage: PlageTexte,
    public val message: String,
)

/**
 * Un diagnostic de session Kotlin, en modèle neutre (D6 : aucune forme LSP
 * ici — l'hôte convertit les offsets en positions UTF-16 et élargit les
 * plages vides).
 *
 * Les plages sont en OFFSETS du texte du document ([PlageTexte.debut] inclus,
 * [PlageTexte.fin] exclu) ; une plage VIDE est fidèle au marqueur du parseur
 * (l'élargissement est un soin d'hôte).
 *
 * @property code le code du diagnostic (vocabulaire `kt.*`) ;
 * @property severite la sévérité neutre ;
 * @property plage l'emplacement du problème dans le texte du document ;
 * @property message le message lisible (français de la maison, ou message
 * amont repris tel quel pour `kt.syntax`) ;
 * @property tags les tags portés (KP15) — liste vide si aucun ;
 * @property informationsLiees les informations liées (KP15) — liste vide si
 * aucune (un import inutilisé n'en porte JAMAIS, un import conflictuel porte
 * la PREMIÈRE directive).
 */
public class DiagnosticKotlin(
    public val code: CodeDiagnosticKotlin,
    public val severite: SeveriteKotlin,
    public val plage: PlageTexte,
    public val message: String,
    public val tags: List<TagDiagnosticKotlin>,
    public val informationsLiees: List<InformationLieeKotlin>,
)

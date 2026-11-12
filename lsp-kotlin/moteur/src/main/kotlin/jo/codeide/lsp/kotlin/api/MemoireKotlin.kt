// CodeIDE — SPI mémoire neutre du moteur Kotlin LSP (module :kotlin, vague
// V-A — ADR 0117/0134 : budgets octets + observateur `onTrimMemory`). Code
// NEUF jjoblab (clean-room — l'hôte Android reçoit les rappels
// `onTrimMemory`/`onLowMemory` de la plateforme et les TRADUIT en niveaux
// neutres : le moteur reste LIBRE de toute dépendance Android, le pack se
// branche sur ce SPI et uniquement lui).
package jo.codeide.lsp.kotlin.api

/**
 * Le niveau de PRESSION mémoire signalé par l'hôte (traduction NEUTRE des
 * rappels plateforme — `onTrimMemory` côté Android : le moteur ne connaît
 * ni Android ni ses constantes).
 *
 * La réponse du moteur est TOUJOURS sûre : les caches évacués se
 * RECONSTRUISENT à la demande (coût de relecture, jamais de perte de
 * justesse — les documents ouverts, l'époque et les index persistants ne
 * sont JAMAIS évacués).
 *
 * ## Correspondance conseillée (hôte Android)
 *
 * | Niveau neutre | Rappels `ComponentCallbacks2` |
 * |---|---|
 * | [UI_MASQUEE] | `TRIM_MEMORY_UI_HIDDEN` (20) |
 * | [FAIBLE] | `TRIM_MEMORY_RUNNING_MODERATE` (5), `TRIM_MEMORY_RUNNING_LOW` (10), `TRIM_MEMORY_BACKGROUND` (40) |
 * | [CRITIQUE] | `TRIM_MEMORY_RUNNING_CRITICAL` (15), `TRIM_MEMORY_MODERATE` (60) |
 * | [URGENT] | `TRIM_MEMORY_COMPLETE` (80), `onLowMemory` |
 */
public enum class NiveauMemoireKotlin {
    /** L'interface est masquée : la mémoire des caches « visibles » peut partir. */
    UI_MASQUEE,

    /** Pression modérée : les caches de lecture paresseuse se vidangent. */
    FAIBLE,

    /** Pression critique : caches ET annuaires de complétion se vidangent. */
    CRITIQUE,

    /** Pression urgente : TOUT ce qui se reconstruit se vidange. */
    URGENT,
}

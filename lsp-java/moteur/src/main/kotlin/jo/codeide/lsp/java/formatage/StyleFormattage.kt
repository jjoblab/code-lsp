package jo.codeide.lsp.java.formatage

/**
 * Style de formatage (étage N7 — cahier fiches 016/017) : les deux styles
 * minimaux exigés par le cahier.
 *
 * @property indentation l'unité d'indentation (espaces par niveau).
 * @property largeurLigne la largeur de repli cible (le cahier vérifie
 *   chaque ligne ≤ largeur + 10 pour Eclipse).
 * @property indentationContinuation le surcroît d'indentation des lignes
 *   repliées.
 */
public enum class StyleFormattage(
    public val indentation: Int,
    public val largeurLigne: Int,
    public val indentationContinuation: Int,
) {
    /** Style Eclipse par défaut : 4 espaces, repli à 120 colonnes. */
    ECLIPSE(4, 120, 8),

    /** Style Google Java Style : 2 espaces. */
    GOOGLE(2, 100, 4),
}

package jo.codeide.lsp.kotlin.format

/**
 * Style de formatage Kotlin (session 5 — 15/11/2026) : le formateur
 * borné (fiche 019 / ADR 0202) RÉ-INDENTE — le style se réduit à
 * l'UNITÉ d'indentation (aucune largeur de ligne dans un
 * ré-indenteur ; le contenu des lignes n'est jamais réécrit).
 *
 * - OFFICIEL (kotlinlang / ktlint par défaut) : 4 espaces par niveau ;
 * - ANDROID (convention Compose / AndroidX-Kotlin) : 2 espaces ;
 * - CUSTOM : mesure libre validée (1..16).
 */
public enum class StyleFormattageKotlin(
    public val indentation: Int,
) {
    /** Le style officiel kotlinlang (ktlint défaut) : 4 espaces. */
    OFFICIEL(4),

    /** La convention Android/Compose (AndroidX) : 2 espaces. */
    ANDROID(2),
    ;

    public companion object {
        /**
         * Le style des FormattingOptions LSP (session 5) : la clé
         * « style » (« officiel »/« kotlin », « android », « custom »)
         * et la mesure « indentSize ». Priorité HONNÊTE : un style NOMMÉ
         * gagne sur tabSize (le tabSize générique du client n'est pas
         * une intention de style) ; la mesure `indentSize` surcharge
         * tout ; les bornes 1..16 — hors bornes : ignorée.
         *
         * @param nom la valeur de la clé « style » (null/blank = officiel
         *   SI aucune mesure, sinon la mesure).
         * @param indentCustom l'indentation demandée (custom/surcharge).
         * @param tabSize le tabSize LSP (repli SEULEMENT sans style ni
         *   mesure — le contrat fiche 019 « tabSize du client » reste).
         */
        @JvmStatic
        public fun depuisOptions(nom: String?, indentCustom: Int?, tabSize: Int? = null): Int {
            val mesure = indentCustom?.takeIf { it in 1..16 }
            return when {
                mesure != null -> mesure
                nom?.trim()?.lowercase() == "android" -> ANDROID.indentation
                nom?.trim()?.lowercase() in setOf("officiel", "kotlin") -> OFFICIEL.indentation
                // Sans style ni mesure : le tabSize du client (contrat
                // fiche 019 inchangé), sinon le style officiel.
                else -> tabSize?.takeIf { it in 1..16 } ?: OFFICIEL.indentation
            }
        }
    }
}

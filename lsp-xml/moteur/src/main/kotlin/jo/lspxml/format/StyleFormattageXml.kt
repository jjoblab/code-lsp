package jo.lspxml.format

/**
 * Style de formatage XML (session 5 — 15/11/2026) : le formateur borné
 * (S3) RÉ-INDENTE — le style se réduit à l'UNITÉ d'indentation.
 *
 * - ANDROID (la convention Android Studio des fichiers XML du projet :
 *   layouts, manifeste, values) : 4 espaces ;
 * - CUSTOM : mesure libre validée (1..16).
 */
public enum class StyleFormattageXml(
    public val indentation: Int,
) {
    /** La convention Android Studio (layouts, manifeste) : 4 espaces. */
    ANDROID(4),
    ;

    public companion object {
        /**
         * L'indentation des FormattingOptions LSP (session 5) : la clé
         * « style » (« android », « custom ») et la mesure
         * « indentSize ». Priorité HONNÊTE : la mesure `indentSize`
         * surcharge tout ; un style NOMMÉ gagne sur le tabSize générique
         * ; le tabSize reste le repli (le contrat S3 « options du
         * client » est inchangé) ; les bornes 1..16 — hors bornes :
         * ignorée, jamais d'indentation absurde.
         *
         * @param nom la valeur de la clé « style » (null = tabSize/4).
         * @param indentCustom l'indentation demandée (custom/surcharge).
         * @param tabSize le tabSize LSP (repli).
         */
        @JvmStatic
        public fun depuisOptions(nom: String?, indentCustom: Int?, tabSize: Int? = null): Int {
            val mesure = indentCustom?.takeIf { it in 1..16 }
            return when {
                mesure != null -> mesure
                nom?.trim()?.lowercase() == "android" -> ANDROID.indentation
                nom?.trim()?.lowercase() == "custom" -> tabSize?.takeIf { it in 1..16 } ?: ANDROID.indentation
                // Sans style ni mesure : le tabSize du client (contrat S3
                // inchangé), sinon la convention Android (4).
                else -> tabSize?.takeIf { it in 1..16 } ?: ANDROID.indentation
            }
        }
    }
}

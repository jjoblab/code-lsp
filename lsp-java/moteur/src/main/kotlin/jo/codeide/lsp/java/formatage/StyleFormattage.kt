package jo.codeide.lsp.java.formatage

/**
 * Style de formatage (étage N7 — cahier fiches 016/017 ; catalogue
 * étendu session 5, 15/11/2026) : une INTERFACE — les styles NOMMÉS
 * sont des objets (ECLIPSE, GOOGLE, ANDROID) et le style CUSTOM est
 * une data class aux mesures libres validées.
 *
 * @property indentation l'unité d'indentation (espaces par niveau).
 * @property largeurLigne la largeur de repli cible (le cahier vérifie
 *   chaque ligne ≤ largeur + 10 pour Eclipse).
 * @property indentationContinuation le surcroît d'indentation des lignes
 *   repliées.
 */
public interface StyleFormattage {
    public val indentation: Int
    public val largeurLigne: Int
    public val indentationContinuation: Int

    /** Style Eclipse par défaut : 4 espaces, repli à 120 colonnes. */
    public object ECLIPSE : StyleFormattage {
        override val indentation: Int = 4
        override val largeurLigne: Int = 120
        override val indentationContinuation: Int = 8
    }

    /** Style Google Java Style : 2 espaces, repli à 100 colonnes. */
    public object GOOGLE : StyleFormattage {
        override val indentation: Int = 2
        override val largeurLigne: Int = 100
        override val indentationContinuation: Int = 4
    }

    /**
     * Style Android (AOSP — la convention des sources de la plateforme
     * et des bibliothèques AndroidX) : 8 espaces par niveau, repli à
     * 100 colonnes. Session 5 (15/11/2026).
     */
    public object ANDROID : StyleFormattage {
        override val indentation: Int = 8
        override val largeurLigne: Int = 100
        override val indentationContinuation: Int = 8
    }

    public companion object {
        /**
         * Le style des FormattingOptions LSP (session 5) : la clé
         * `style` (« google », « android », « eclipse » — insensible à
         * la casse) ; « custom » (ou toute valeur inconnue ACCOMPAGNÉE
         * de mesures) bâtit [CUSTOM] avec `indentSize` et `lineWidth`
         * (bornes honnêtes : indent 1..16, largeur 20..400 — une
         * valeur hors bornes est IGNORÉE, jamais de style absurde).
         *
         * @param nom la valeur de la clé `style` (null/blank = Eclipse).
         * @param indentCustom l'indentation demandée (custom).
         * @param largeurCustom la largeur demandée (custom).
         */
        @JvmStatic
        public fun depuisOptions(
            nom: String?,
            indentCustom: Int?,
            largeurCustom: Int?,
        ): StyleFormattage {
            val mesureIndent = indentCustom?.takeIf { it in 1..16 }
            val mesureLargeur = largeurCustom?.takeIf { it in 20..400 }
            return when (nom?.trim()?.lowercase()) {
                "google" -> GOOGLE.avecMesures(mesureIndent, mesureLargeur)
                "android", "aosp" -> ANDROID.avecMesures(mesureIndent, mesureLargeur)
                "eclipse" -> ECLIPSE.avecMesures(mesureIndent, mesureLargeur)
                // CUSTOM — et tout nom inconnu SANS mesure reste Eclipse
                // (le défaut du cahier, jamais une erreur).
                else -> CUSTOM.de(mesureIndent, mesureLargeur) ?: ECLIPSE
            }
        }

        /** Le style aux mesures remplacées (les invalides restent). */
        private fun StyleFormattage.avecMesures(
            indent: Int?,
            largeur: Int?,
        ): StyleFormattage =
            if (indent != null || largeur != null) {
                CUSTOM.de(indent ?: indentation, largeur ?: largeurLigne) ?: this
            } else {
                this
            }
    }
}

/**
 * Le style CUSTOM (session 5) : des mesures LIBRES validées —
 * l'indentation 1..16, la largeur 20..400, la continuation
 * proportionnelle (le double de l'unité, plafonnée à 12 — les mesures
 * extrêmes produisent des replis illisibles).
 */
public data class CUSTOM(
    override val indentation: Int,
    override val largeurLigne: Int,
) : StyleFormattage {
    override val indentationContinuation: Int = (indentation * 2).coerceAtMost(12)

    public companion object {
        /**
         * La fabrique validée : toute mesure hors bornes (ou les DEUX
         * absentes) rend null — l'appelant garde alors son style.
         */
        @JvmStatic
        public fun de(indent: Int?, largeur: Int?): CUSTOM? {
            if (indent != null && indent !in 1..16) return null
            if (largeur != null && largeur !in 20..400) return null
            if (indent == null && largeur == null) return null
            return CUSTOM(indent ?: 4, largeur ?: 100)
        }
    }
}

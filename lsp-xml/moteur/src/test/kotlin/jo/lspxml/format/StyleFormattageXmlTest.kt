package jo.lspxml.format

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Session 5 (15/11/2026) — le style de formatage XML : l'unité
 * d'indentation vient des FormattingOptions LSP — ANDROID (4 espaces,
 * la convention Android Studio, le défaut historique S3), CUSTOM
 * (indentSize libre borné 1..16), la mesure surcharge tout, le tabSize
 * reste le repli (contrat S3 inchangé).
 */
class StyleFormattageXmlTest {

    @Test
    fun depuisOptionsLesPrioritesHonnetes() {
        // La mesure SURCHAUFFE tout (même un style nommé).
        assertEquals("mesure > style nommé", 3L, StyleFormattageXml.depuisOptions("android", 3, 8).toLong())
        assertEquals("mesure > tabSize", 6L, StyleFormattageXml.depuisOptions(null, 6, 4).toLong())
        // Le style NOMMÉ gagne sur le tabSize générique.
        assertEquals("android (4) > tabSize 8", 4L, StyleFormattageXml.depuisOptions("android", null, 8).toLong())
        // Sans style ni mesure : le tabSize du client (contrat S3).
        assertEquals("tabSize du client", 2L, StyleFormattageXml.depuisOptions(null, null, 2).toLong())
        assertEquals("défaut 4 (Android)", 4L, StyleFormattageXml.depuisOptions(null, null, null).toLong())
        // Hors bornes : la mesure est IGNORÉE (jamais d'absurde).
        assertEquals("indent 0 ignorée", 4L, StyleFormattageXml.depuisOptions("custom", 0, null).toLong())
        assertEquals("indent 99 ignorée", 4L, StyleFormattageXml.depuisOptions("custom", 99, null).toLong())
        assertEquals("ignorée puis tabSize 8", 8L, StyleFormattageXml.depuisOptions("custom", 0, 8).toLong())
    }
}

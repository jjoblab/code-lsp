package jo.codeide.lsp.java.formatage

import jo.codeide.lsp.java.outils.appliquerEditions
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 016 — EcjFormatterRangeTest : les éditions de formatage de PLAGE
 * (`editionsRange`), substrat du handler LSP `textDocument/rangeFormatting`.
 *
 * Sémantique imposée par le cahier : les éditions ne touchent QUE la plage
 * demandée (l'unité entière est néanmoins parsée pour le contexte) ; elles
 * voyagent en ordre CROISSANT d'offset et sont disjointes ; source
 * imparsable → null ou no-op ; plage vide → liste vide ; plage pleine page
 * équivaut au formatage intégral.
 *
 * Le harnais du domaine éprouvait `EcjFormatter` (JDT patché) ; la
 * réécriture verrouille le miroir moteur [FormateurSource] (formateur
 * Kotlin clean-room — voir la fiche 017 pour le motif).
 */
class Fiche016FormateurPlagesTest {

    private companion object {

        /** Le formateur « 17 » du cahier, style Eclipse. */
        val FORMATEUR = FormateurSource("17", StyleFormattage.ECLIPSE)

        /** Classe Ex à deux méthodes volontairement mal formatées. */
        val SOURCE = """
            public class Ex {
            void a() {
            int x=1;
            }
            void b() {
            int y=2;
            }
            }
        """.trimIndent()

        /** La plage = la méthode `b` entière, de « void b() » à son accolade fermante incluse. */
        fun plageMethodeB(): Pair<Int, Int> {
            val debut = SOURCE.indexOf("void b()")
            assertNotNull(debut)
            val fin = SOURCE.indexOf('}', SOURCE.indexOf("int y=2;")) + 1
            return debut to fin
        }
    }

    /** editionsConfineesALaPlage — les éditions restent dans la plage demandée. */
    @org.junit.jupiter.api.Test
    fun editionsConfineesALaPlage() {
        val (debut, fin) = plageMethodeB()
        val editions = FORMATEUR.editionsRange(SOURCE, debut, fin - debut)
        assertNotNull(editions, "une source saine rend des éditions (jamais null)")
        assertTrue(editions.isNotEmpty(), "la méthode b mal formatée produit des éditions")
        for (edition in editions) {
            assertTrue(
                edition.offset >= debut && edition.offset + edition.longueur <= fin,
                "l'édition $edition doit être entièrement contenue dans [$debut, $fin]",
            )
        }
        val applique = appliquerEditions(SOURCE, editions)
        assertTrue("int x=1;" in applique, "le code hors plage contient toujours « int x=1; » inchangé")
        assertTrue("int y = 2;" in applique, "le code dans la plage contient « int y = 2; » formaté")
    }

    /** editionsTrieesCroissantesEtDisjointes — offsets strictement croissants. */
    @org.junit.jupiter.api.Test
    fun editionsTrieesCroissantesEtDisjointes() {
        val editions = FORMATEUR.editionsRange(SOURCE, 0, SOURCE.length)
        assertNotNull(editions)
        var borne = -1
        for (edition in editions) {
            assertTrue(edition.offset > borne, "offsets strictement croissants — éditions : $editions")
            assertTrue(edition.longueur >= 0, "longueur ≥ 0")
            assertTrue(edition.offset <= SOURCE.length, "offset ≤ longueur de la source")
            borne = edition.offset + edition.longueur
        }
    }

    /** plageVideListeVide — plage vide → liste vide (jamais null). */
    @org.junit.jupiter.api.Test
    fun plageVideListeVide() {
        val editions = FORMATEUR.editionsRange(SOURCE, 10, 0)
        assertNotNull(editions, "une plage vide rend une liste, jamais null")
        assertTrue(editions.isEmpty(), "une plage vide ne produit aucune édition")
    }

    /** sourceImparsableNeMutilleJamais — imparsable → null ou aucune édition. */
    @org.junit.jupiter.api.Test
    fun sourceImparsableNeMutilleJamais() {
        val source = "class {{{"
        val editions = FORMATEUR.editionsRange(source, 0, source.length)
        assertTrue(editions == null || editions.isEmpty(), "editionsRange : null ou liste vide")
        val plage = FORMATEUR.formatRange(source, 0, source.length)
        assertTrue(plage == null || plage == source, "formatRange : null ou source strictement inchangée")
    }

    /** pleinePageEquivautAuFormatDocument — pleine page ≡ format intégral. */
    @org.junit.jupiter.api.Test
    fun pleinePageEquivautAuFormatDocument() {
        val editions = FORMATEUR.editionsRange(SOURCE, 0, SOURCE.length)
        assertNotNull(editions)
        val applique = appliquerEditions(SOURCE, editions)
        assertEquals(FORMATEUR.format(SOURCE), applique, "pleine page ≡ format intégral")
    }

    /** formatRangeResteCoherentAvecLesEditions — formatRange ≡ éditions appliquées. */
    @org.junit.jupiter.api.Test
    fun formatRangeResteCoherentAvecLesEditions() {
        val (debut, fin) = plageMethodeB()
        val editions = FORMATEUR.editionsRange(SOURCE, debut, fin - debut)
        assertNotNull(editions)
        val attendu = appliquerEditions(SOURCE, editions)
        val rendu = FORMATEUR.formatRange(SOURCE, debut, fin - debut)
        assertEquals(attendu, rendu, "formatRange rend le texte des éditions appliquées")
        assertTrue("int x=1;" in rendu!!, "hors plage intact")
        assertTrue("int y = 2;" in rendu, "plage formatée")
    }
}

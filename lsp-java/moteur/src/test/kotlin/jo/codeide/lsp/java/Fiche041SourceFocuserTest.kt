package jo.codeide.lsp.java

import jo.codeide.lsp.java.outils.Focused
import jo.codeide.lsp.java.outils.SourceFocuser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fiche 041 — SourceFocuserTest : focalisation du source (réduction à la
 * java-language-server) — corps hors portée effacés, signatures préservées,
 * appariement d'accolades insensible aux strings/commentaires, traduction
 * inverse mapBack.
 */
class Fiche041SourceFocuserTest {

    /** Classe de 3 méthodes de [lignesParMethode] lignes chacune. */
    private fun classeTroisMethodes(lignesParMethode: Int): String {
        fun corps(prefixe: String): String = (1..lignesParMethode).joinToString(" ") { "int ${prefixe}$it = $it;" }
        return """
            public class Gen {
                void m1() { ${corps("a")} }
                void m2() { ${corps("b")} int cible = ${lignesParMethode + 1}; String here = "here"; }
                void m3() { ${corps("c")} }
            }
        """.trimIndent()
    }

    @Test
    fun shortSourceIsReturnedUnchanged() {
        val court = "class C { void m() { int x = 1; } }\n"
        val focused = SourceFocuser.focusAndMap(court, 20)
        assertEquals(court, focused.source(), "source court rendu INCHANGÉ (aucune réduction sous le seuil)")
    }

    @Test
    fun methodBodiesOutsideOffsetAreErased() {
        val source = classeTroisMethodes(200)
        assertTrue(source.length > SourceFocuser.SEUIL_REDUCTION, "source > 2 000 caractères")
        val offset = source.indexOf("\"here\"")
        val focused = SourceFocuser.focusAndMap(source, offset)
        val reduit = focused.source()
        assertTrue(reduit.contains("\"here\""), "la méthode focalisée (m2) reste INTÉGRALE")
        assertFalse(reduit.contains("a150 = 150"), "le corps de m1 est effacé")
        assertFalse(reduit.contains("c150 = 150"), "le corps de m3 est effacé")
        for (signature in listOf("void m1()", "void m2()", "void m3()")) {
            assertTrue(reduit.contains(signature), "la signature $signature reste présente")
        }
    }

    @Test
    fun stringsAndCommentsDoNotConfuseMatcher() {
        val source = classeTroisMethodes(300)
        // m1 reçoit un string avec une accolade échappée et un commentaire
        // contenant une accolade : l'appariement doit les ignorer.
        val piege = source.replace(
            "void m1() {",
            "void m1() { String s = \"a{b\"; /* un commentaire avec { */",
        )
        val offset = piege.indexOf("\"target\"")
        val avecCible = piege.replace("\"here\"", "\"target\"")
        val focused = SourceFocuser.focusAndMap(avecCible, avecCible.indexOf("\"target\""))
        val reduit = focused.source()
        assertTrue(reduit.contains("\"target\""), "m2 (focalisée) préservée")
        assertFalse(reduit.contains("a150 = 150"), "m1 effacée malgré les accolades parasites")
    }

    @Test
    fun offsetOutsideMethodDoesNotReduce() {
        val source = classeTroisMethodes(400)
        val offsetChamp = source.indexOf("class Gen") // niveau CLASSE, hors méthode
        val focused = SourceFocuser.focusAndMap(source, offsetChamp)
        assertEquals(source, focused.source(), "offset hors méthode : source rendue INCHANGÉE")
    }

    @Test
    fun mapBackUnreducedSourceIsIdentity() {
        val court = "class C { void m() { int x = 1; } }\n"
        val focused = SourceFocuser.focusAndMap(court, 10)
        assertEquals(10, focused.mapBack(10), "position 10 → 10 (identité sans réduction)")
        assertEquals(court.length, focused.mapBack(court.length), "fin de source → fin de source")
    }

    @Test
    fun mapBackTranslatesPositionsAfterErasedBodies() {
        val source = classeTroisMethodes(200)
        val offset = source.indexOf("\"here\"")
        val focused = SourceFocuser.focusAndMap(source, offset)
        val reduit = focused.source()
        // Une position AVANT la première coupe est identique.
        assertEquals(source.indexOf("void m1()"), focused.mapBack(reduit.indexOf("void m1()")), "avant la première coupe : identique")
        // La signature de m3, décalée dans la réduite, restituée à SA position originale.
        val positionM3Reduite = reduit.indexOf("void m3()")
        assertEquals(source.indexOf("void m3()"), focused.mapBack(positionM3Reduite), "m3 restituée à sa position ORIGINALE")
        // La fin de la source réduite correspond à la fin originale.
        assertEquals(source.length, focused.mapBack(reduit.length), "fin réduite → fin originale")
    }

    @Test
    fun mapBackInsideInsertedTokenMapsToResumePosition() {
        val source = classeTroisMethodes(200)
        val offset = source.indexOf("\"here\"")
        val focused = SourceFocuser.focusAndMap(source, offset)
        val reduit = focused.source()
        // Position du `}` du jeton `{}` inséré à la place du corps de m1 :
        // elle mappe sur la REPRISE (juste après le `}` du corps original de m1).
        val jetonM1 = reduit.indexOf("void m1()") + "void m1()".length + 1 // position du `{` du jeton
        val fermetureJeton = jetonM1 + 1 // position du `}` du jeton
        val corpsOriginalM1Fin = source.indexOf("void m2()") - 2 // juste avant « } m2 »… recalcul ci-dessous
        val finCorpsM1 = source.indexOf("}", source.indexOf("void m1()")) // premier } après l'ouverture de m1
        assertEquals(finCorpsM1 + 1, focused.mapBack(fermetureJeton), "position dans le jeton inséré → reprise (fermeture + 1)")
    }

    @Test
    fun mapBackRoundTripsTheCaretOffset() {
        val source = classeTroisMethodes(200)
        val offset = source.indexOf("\"here\"")
        val focused = SourceFocuser.focusAndMap(source, offset)
        assertEquals(offset, focused.mapBack(focused.offset()), "l'aller-retour du caret restitue EXACTEMENT l'offset original")
    }
}

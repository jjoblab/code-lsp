package jo.codeide.lsp.java.formatage

import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.outils.appliquerEditions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 017 — EcjFormatterTest : le formateur in-process, style Eclipse
 * par défaut (indentation 4 espaces, repli à 120 colonnes) et style Google
 * (2 espaces), idempotence, syntaxe Java 17 (record, bloc-texte), formatage
 * de plage, reformatage des commentaires/javadoc, et contrats de sûreté
 * (imparsable, source vide).
 *
 * Le harnais du domaine éprouvait le formateur Eclipse JDT au travers des
 * jars patchés (tâche `patchEclipseForArt`) — la réécriture verrouille le
 * MIROIR MOTEUR : [FormateurSource], formateur Kotlin clean-room (le jar
 * ecj 3.40.0 patché n'embarque pas les classes du formateur JDT — note de
 * réécriture de la fiche : « la réécriture Kotlin devra offrir un
 * équivalent exécutable sur Android/ART »).
 *
 * Les assertions sont par sous-chaînes (le cahier ne fixe PAS le texte
 * intégral de sortie) : invariants d'espacement/indentation seulement.
 */
class Fiche017FormateurTest {

    private companion object {

        /** Le formateur « 17 » du cahier, style Eclipse par défaut. */
        val FORMATEUR = FormateurSource("17", StyleFormattage.ECLIPSE)

        /** Classe publique tassée, affectations sans espaces (fixture MESSY). */
        val MESSY = """
            package demo;public class Main{private int counter=0;
            public String greet(String name){String message="Hello, "+name;
            return message;}}
        """.trimIndent()

        /** Record Java 17 compact. */
        val RECORD = "public record Point(int x,int y){int doubler(){return x*2;}}"
    }

    /** formatsIndentationAndSpacing — style Eclipse : espacement et indentation 4. */
    @org.junit.jupiter.api.Test
    fun formatsIndentationAndSpacing() {
        val resultat = FORMATEUR.format(MESSY)
        assertNotNull(resultat, "une source saine doit se formater")
        assertTrue("public class Main {" in resultat, "accolade de classe espacée — obtenu : $resultat")
        assertTrue("private int counter = 0;" in resultat, "espaces autour de = — obtenu : $resultat")
        assertTrue("public String greet(String name) {" in resultat, "signature espacée — obtenu : $resultat")
        assertTrue("\"Hello, \" + name" in resultat, "espaces autour de + — obtenu : $resultat")
        assertTrue("\n    private int counter" in resultat, "corps de classe indenté de 4 — obtenu : $resultat")
        assertTrue("\n        String message" in resultat, "corps de méthode indenté de 8 — obtenu : $resultat")
    }

    /** googleStyleUsesTwoSpaces — style Google : indentation 2. */
    @org.junit.jupiter.api.Test
    fun googleStyleUsesTwoSpaces() {
        val google = FormateurSource("17", StyleFormattage.GOOGLE)
        val resultat = google.format(MESSY)
        assertNotNull(resultat)
        assertTrue("\n  private int counter" in resultat, "corps de classe indenté de 2 — obtenu : $resultat")
        assertTrue("\n    String message" in resultat, "corps de méthode indenté de 4 — obtenu : $resultat")
    }

    /** formattedOutputIsStable — idempotence du formatage. */
    @org.junit.jupiter.api.Test
    fun formattedOutputIsStable() {
        val uneFois = FORMATEUR.format(MESSY)
        assertNotNull(uneFois)
        val deuxFois = FORMATEUR.format(uneFois)
        assertEquals(uneFois, deuxFois, "format(format(s)) == format(s)")
    }

    /** formatsModernSyntax — records Java 17 formatables. */
    @org.junit.jupiter.api.Test
    fun formatsModernSyntax() {
        val resultat = FORMATEUR.format(RECORD)
        assertNotNull(resultat)
        assertTrue(
            "public record Point(int x, int y) {" in resultat,
            "en-tête du record espacé — obtenu : $resultat",
        )
    }

    /** formatRangeOnlyTouchesRange — le formatage de plage n'atteint que la plage. */
    @org.junit.jupiter.api.Test
    fun formatRangeOnlyTouchesRange() {
        val source = "class T {\n    void a() {\n        int a = 1;\n    }\n\n    void b() {\nint b = 2;\n    }\n}\n"
        // Plage = la ligne « int b = 2; » uniquement.
        val debut = source.indexOf("int b = 2;")
        val resultat = FORMATEUR.formatRange(source, debut, "int b = 2;".length)
        assertNotNull(resultat)
        assertTrue("        int a = 1;" in resultat, "ligne hors plage intacte — obtenu : $resultat")
        assertTrue("        int b = 2;" in resultat, "ligne dans la plage indentée — obtenu : $resultat")
    }

    /** unparseableSourceIsNeverMangled — imparsable : null ou inchangé. */
    @org.junit.jupiter.api.Test
    fun unparseableSourceIsNeverMangled() {
        val source = "class {{{{ nope"
        val resultat = FORMATEUR.format(source)
        assertTrue(resultat == null || resultat == source, "jamais une version déformée — obtenu : $resultat")
    }

    /** emptySourceIsReturnedAsIs — source vide retournée telle quelle. */
    @org.junit.jupiter.api.Test
    fun emptySourceIsReturnedAsIs() {
        assertEquals("", FORMATEUR.format(""))
    }

    /** formatsCommentsAndJavadoc — la javadoc est reformatée. */
    @org.junit.jupiter.api.Test
    fun formatsCommentsAndJavadoc() {
        val source = "class C {\n/**Javadoc    mal   espacée.*/\nint x = 1;\n/*bloc    aéré   */\nint y = 2;\n}\n"
        val resultat = FORMATEUR.format(source)
        assertNotNull(resultat)
        assertFalse("/**Javadoc    mal   espacée.*/" in resultat, "la javadoc mal espacée doit être reformatée — obtenu : $resultat")
        assertTrue("/** Javadoc mal espacée. */" in resultat, "contenu resserré — obtenu : $resultat")
        assertFalse("/*bloc    aéré   */" in resultat, "le bloc de commentaire est ré-espacé — obtenu : $resultat")
    }

    /** lineWrappingRespectsWidth — repli de ligne à la largeur configurée. */
    @org.junit.jupiter.api.Test
    fun lineWrappingRespectsWidth() {
        val chaines = (1..14).joinToString(", ") { "\"chaine$it\"" }
        val source = "import java.util.Arrays;\nclass W {\n    java.util.List<String> toutes = Arrays.asList($chaines);\n}\n"
        val resultat = FORMATEUR.format(source)
        assertNotNull(resultat)
        val depassement = resultat.lines().filter { it.length > 130 }
        assertTrue(depassement.isEmpty(), "aucune ligne ne dépasse 130 caractères — fautives : $depassement")
        // Idempotence du repli : les lignes repliées restent en place.
        assertEquals(resultat, FORMATEUR.format(resultat), "le repli est stable")
    }
}

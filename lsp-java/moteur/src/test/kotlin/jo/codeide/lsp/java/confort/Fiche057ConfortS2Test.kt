package jo.codeide.lsp.java.confort

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.outils.appliquerEditions
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Fiche 057 — JavaS2ConfortTest : lot S2-a « confort structurel » —
 * formatage de SÉLECTION (`rangeFormatting` : capacité déclarée, éditions
 * bornées à la plage, code hors plage jamais touché, garde lifecycle) et
 * survol riche (`hover` : signature seule → PLAINTEXT ; signature +
 * javadoc → MARKDOWN ; la forme MarkedString-string dépréciée est
 * proscrite).
 *
 * Le harnais du domaine éprouvait la FAÇADE LSP réelle (`JavaTextDocumentService`,
 * didOpen + requête asynchrone) sur un serveur SANS classpath ; la
 * réécriture verrouille le MIROIR MOTEUR : [ServiceConfort] — capacités,
 * garde lifecycle I2, formatage de plage et contenus de survol marqués.
 * La couche LSP4J (MarkupContent, Either) arrive à l'étage N8 ; ses kinds
 * et son préfixe « ```java\n » sont déjà fidèles au protocole.
 *
 * Fixtures du cahier : classe `T` à deux méthodes mal indentées (`a()`
 * hors sélection, `b()` ciblée) et classe `H` avec javadoc (`somme`
 * documentée « Additionne deux valeurs. », appelée depuis `doubleSomme`,
 * `sansDoc` sans doc).
 */
class Fiche057ConfortS2Test {

    private companion object {

        lateinit var MOTEUR: MoteurJava

        /** Classe T : deux méthodes mal indentées. */
        val SOURCE_T = """
            public class T {
            void a() {
            int x=1;
            }
            void b() {
            int y=2;
            }
            }
        """.trimIndent()

        /** Classe H : somme documentée, doubleSomme qui l'appelle, sansDoc sans doc. */
        val SOURCE_H = """
            package demo;

            public class H {
                /** Additionne deux valeurs. */
                public int somme(int a, int b) {
                    return a + b;
                }

                public int doubleSomme(int a, int b) {
                    return somme(a, b) * 2;
                }

                public int sansDoc(int a) {
                    return a;
                }
            }
        """.trimIndent()

        /** La sélection couvre « void b() » jusqu'à l'accolade fermante incluse. */
        fun selectionB(): Pair<Int, Int> {
            val debut = SOURCE_T.indexOf("void b()")
            val fin = SOURCE_T.indexOf('}', SOURCE_T.indexOf("int y=2;")) + 1
            return debut to fin
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            MOTEUR = MoteurJava(racines = emptyList(), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — confort S2 ignoré")
        }
    }

    /** capaciteRangeFormattingDeclaree — capacité rangeFormatting annoncée. */
    @Test
    fun capaciteRangeFormattingDeclaree() {
        val service = ServiceConfort(MOTEUR)
        val capacites = service.capacites()
        // Annoncée en forme BOOLÉENNE TRUE (côté gauche du Either côté LSP4J).
        assertEquals(true, capacites.formatagePlageDocument, "documentRangeFormattingProvider = true (booléen)")
    }

    /** formatageDeSelectionBornesALaPlage — formatage de sélection borné à la plage. */
    @Test
    fun formatageDeSelectionBornesALaPlage() {
        val service = ServiceConfort(MOTEUR)
        service.initialiser()
        val (debut, fin) = selectionB()
        val editions = service.formatagePlage("/T.java", SOURCE_T, debut, fin - debut)
        assertNotNull(editions, "éditions non-null (source saine)")
        assertTrue(editions.isNotEmpty(), "éditions non vides (méthode b mal indentée)")
        val ligneB = SOURCE_T.substring(0, debut).count { it == '\n' }
        for (edition in editions) {
            val ligneEdition = SOURCE_T.substring(0, edition.offset).count { it == '\n' }
            assertTrue(ligneEdition >= ligneB, "toutes les éditions commencent à une ligne ≥ ligne de b()")
        }
        val applique = appliquerEditions(SOURCE_T, editions)
        assertTrue("int y = 2;" in applique, "« int y = 2; » (dans la plage) est formatée — obtenu : $applique")
        assertTrue("int x=1;" in applique, "« int x=1; » (méthode a, hors plage) reste inchangée")
    }

    /** rangeFormattingAvantInitialisationRejetee — garde lifecycle sur rangeFormatting. */
    @Test
    fun rangeFormattingAvantInitialisationRejetee() {
        val service = ServiceConfort(MOTEUR)
        // Serveur NON initialisé (jamais d'initialized) : rejet I2.
        try {
            service.formatagePlage("/fictif.java", "class A {\n}\n", 0, 5)
            fail("la requête doit être rejetée avant initialization (garde I2)")
        } catch (attendu: RequeteRefusee) {
            // Rejet par l'équivalent moteur de la ResponseErrorException.
            assertTrue(attendu.message!!.contains("rangeFormatting"))
        }
    }

    /** hoverSignatureSeuleEnPlaintext — hover signature seule en PLAINTEXT. */
    @Test
    fun hoverSignatureSeuleEnPlaintext() {
        val service = ServiceConfort(MOTEUR)
        service.initialiser()
        val caret = SOURCE_H.indexOf("sansDoc")
        val contenu = service.contenuHover("/demo/H.java", SOURCE_H, caret)
        assertNotNull(contenu, "hover non-null sur « sansDoc » (méthode sans javadoc)")
        assertEquals(GenreMarquage.TEXTE_BRUT, contenu.genre, "kind PLAINTEXT (signature seule)")
        assertTrue("sansDoc" in contenu.valeur, "la signature nomme la méthode")
        assertTrue("```" !in contenu.valeur, "AUCUNE clôture de code en PLAINTEXT — obtenu : ${contenu.valeur}")
    }

    /** hoverAvecJavadocEnMarkdown — hover signature + javadoc en MARKDOWN. */
    @Test
    fun hoverAvecJavadocEnMarkdown() {
        val service = ServiceConfort(MOTEUR)
        service.initialiser()
        // Hover sur l'APPEL « somme(a, b) » dans doubleSomme — la déclaration
        // porte sa javadoc ailleurs dans le fichier.
        val caret = SOURCE_H.indexOf("somme(a, b)")
        val contenu = service.contenuHover("/demo/H.java", SOURCE_H, caret)
        assertNotNull(contenu, "hover non-null sur l'appel « somme(a, b) »")
        assertEquals(GenreMarquage.MARKDOWN, contenu.genre, "kind MARKDOWN (signature + javadoc)")
        assertTrue(contenu.valeur.startsWith("```java\n"), "préfixe exact « ```java\\n » — obtenu : ${contenu.valeur.take(40)}")
        assertTrue("Additionne deux valeurs" in contenu.valeur, "le texte de la javadoc est rendu")
    }
}

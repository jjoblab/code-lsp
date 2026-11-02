package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.MoteurJava
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fiche 062 — TypeDefinitionFacadeTest : `textDocument/typeDefinition`
 * (le type du symbole au caret) et les offsets de `textDocument/definition`
 * sur les gros fichiers réduits par SourceFocuser (> 2000 caractères) —
 * les plages rendues doivent être en coordonnées de la source ORIGINALE.
 *
 * Fixtures du cahier : `Holder` (classe interne `Item` et variable
 * locale), `Box` (référence de type sur soi), `UsesString` (type binaire
 * java.lang.String), `Counts` (type de base int), `BigFile` (générée :
 * 120 lignes de remplissage dans `big()`, au-delà du seuil de réduction).
 */
class Fiche062FacadeTypeDefinitionTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        lateinit var PROJET: Path
        lateinit var DOSSIER: Path

        val SOURCE_HOLDER = """
            package demo;

            public class Holder {
                class Item {
                    String name;
                }

                public void garder() {
                    Item it = new Item();
                    it.name = "x";
                }
            }
        """.trimIndent()

        val SOURCE_BOX = """
            package demo;

            public class Box {
                public void emballer() {
                    Box b = this;
                }
            }
        """.trimIndent()

        val SOURCE_USES_STRING = """
            package demo;

            public class UsesString {
                public String transformer() {
                    String s = "x";
                    return s;
                }
            }
        """.trimIndent()

        val SOURCE_COUNTS = """
            package demo;

            public class Counts {
                public int compter() {
                    int n = 3;
                    return n;
                }
            }
        """.trimIndent()

        /** La source RÉDUISIBLE : remplissage de 120 lignes dans big() (> seuil 2 000). */
        val SOURCE_BIG_FILE = construireBigFile()

        fun construireBigFile(): String {
            val remplissage = (1..120).joinToString("\n") { "        int filler$it = $it;" }
            return """
                package demo;

                public class BigFile {
                    public void caller() {
                        big();
                    }

                    public void big() {
                $remplissage
                    }
                }
            """.trimIndent()
        }

        fun texte(nom: String): String = DOSSIER.resolve(nom).toFile().readText()

        fun offsetDe(source: String, mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = source.indexOf(mot, position + 1) }
            return position
        }

        /** La position ligne/caractère (0-indexées) d'un offset — le miroir LSP du test. */
        fun ligneCaractere(source: String, offset: Int): Pair<Int, Int> {
            var ligne = 0
            var debutLigne = 0
            for (i in 0 until offset) {
                if (source[i] == '\n') {
                    ligne++
                    debutLigne = i + 1
                }
            }
            return ligne to (offset - debutLigne)
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            PROJET = Files.createTempDirectory("fiche062")
            DOSSIER = Files.createDirectories(PROJET.resolve("demo"))
            DOSSIER.resolve("Holder.java").writeText(SOURCE_HOLDER)
            DOSSIER.resolve("Box.java").writeText(SOURCE_BOX)
            DOSSIER.resolve("UsesString.java").writeText(SOURCE_USES_STRING)
            DOSSIER.resolve("Counts.java").writeText(SOURCE_COUNTS)
            DOSSIER.resolve("BigFile.java").writeText(SOURCE_BIG_FILE)
            MOTEUR = MoteurJava(racines = listOf(PROJET), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — fiche ignorée")
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            MOTEUR.dispose()
        }
    }

    /** typeDefinition_localVariableResolvesItsDeclaredType — locale → déclaration de son type. */
    @Test
    fun typeDefinitionLocalVariableResolvesItsDeclaredType() {
        val source = texte("Holder.java")
        val caret = offsetDe(source, "it.name")
        val cibles = MOTEUR.typeDefinition("demo/Holder.java", source, caret)
        assertEquals(1, cibles.size, "1 cible exactement")
        val cible = cibles.first()
        assertEquals(GenreCible.SOURCE_COURANTE, cible.genre, "Item vit dans le fichier courant")
        assertEquals("Item", source.substring(cible.plage.debutOffset, cible.plage.finOffset), "la plage vise le nom Item")
        // Ligne et colonne EXACTES du nom « Item » de la classe interne.
        val attendu = ligneCaractere(source, offsetDe(source, "class Item") + "class ".length)
        assertEquals(attendu, ligneCaractere(source, cible.plage.debutOffset), "position ligne + colonne exacte du nom")
    }

    /** typeDeclaration_onTypeReferenceResolvesItself — référence de type → sa propre déclaration. */
    @Test
    fun typeDefinitionOnTypeReferenceResolvesItself() {
        val source = texte("Box.java")
        val caret = offsetDe(source, "Box b")
        val cibles = MOTEUR.typeDefinition("demo/Box.java", source, caret)
        assertEquals(1, cibles.size, "1 cible exactement")
        val cible = cibles.first()
        assertEquals("Box", source.substring(cible.plage.debutOffset, cible.plage.finOffset), "la plage vise le nom Box")
        val attendu = ligneCaractere(source, offsetDe(source, "class Box") + "class ".length)
        assertEquals(attendu, ligneCaractere(source, cible.plage.debutOffset), "position exacte du nom")
    }

    /** typeDeclaration_binaryTypeYieldsNoTarget — type binaire → URI synthétique décompilée, plage vide. */
    @Test
    fun typeDefinitionBinaryTypeYieldsSyntheticUri() {
        val source = texte("UsesString.java")
        val caret = offsetDe(source, "String s = \"x\"")
        val cibles = MOTEUR.typeDefinition("demo/UsesString.java", source, caret)
        assertEquals(1, cibles.size, "1 cible exactement")
        val cible = cibles.first()
        assertEquals("jdt://decompiled/java/lang/String.java", cible.uri, "URI synthétique EXACTE")
        assertEquals(0, cible.plage.debutOffset, "plage VIDE 0:0")
        assertEquals(0, cible.plage.finOffset, "plage VIDE 0:0")
    }

    /** typeDeclaration_intLiteralYieldsNoTarget — type de base → aucune cible. */
    @Test
    fun typeDefinitionIntYieldsNoTarget() {
        val source = texte("Counts.java")
        val caret = offsetDe(source, "int n = 3")
        assertTrue(MOTEUR.typeDefinition("demo/Counts.java", source, caret).isEmpty(), "un type de base n'a pas de déclaration")
    }

    /** definition_afterFocusedMethod_landsOnExactOriginalOffset — offsets sur source réduite. */
    @Test
    fun definitionAfterFocusedMethodLandsOnExactOriginalOffset() {
        val source = texte("BigFile.java")
        assertTrue(source.length > 2000, "fixture au-delà du seuil de réduction (${source.length} caractères)")
        val caret = offsetDe(source, "big();")
        val cibles = MOTEUR.definition("demo/BigFile.java", source, caret)
        assertTrue(cibles.isNotEmpty(), "au moins 1 cible")
        val cible = cibles.first()
        assertEquals("big", source.substring(cible.plage.debutOffset, cible.plage.finOffset), "la première cible désigne le NOM « big »")
        // Position EXACTE dans la source ORIGINALE — pas en coordonnées réduites.
        val attendu = ligneCaractere(source, offsetDe(source, "public void big()") + "public void ".length)
        assertEquals(attendu, ligneCaractere(source, cible.plage.debutOffset), "ligne et colonne exactes de la source ORIGINALE")
        assertNotNull(cible.plage)
    }
}

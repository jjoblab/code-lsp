package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.MoteurJava
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fiche 026 — EcjRenameEngineTest : le moteur de renommage sûr
 * STRUCTUREL — identification de cible par BINDINGS (méthode, champ,
 * locale, type — clé structurelle « kind:paquet.Classe.membre »),
 * collecte des références (déclaration + usages simples et qualifiés +
 * imports + constructeurs pour un type), non-retouchage des homonymes
 * d'une autre classe, balayage multi-fichiers des racines sources et
 * court-circuit file-local des locales.
 *
 * Fixtures du cahier : classes `demo.Main`, `demo.Other`, `demo.Config`
 * (un fichier) ; un projet avec `demo/Helper.java` (source réelle) et
 * `app/Main.java` (import + extends) ; un projet multi-fichiers
 * `demo` avec `User` (champ `name`), `Printer` (usage qualifié
 * `user.name`), `Order` (champ homonyme `name`) et `Unrelated`
 * (méthode `name()` sans le champ).
 */
class Fiche026MoteurRenommageTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        lateinit var PROJET: Path
        lateinit var RACINE_A: Path
        lateinit var RACINE_B: Path
        lateinit var RACINE_C: Path

        val SOURCE_MAIN = """
            package demo;

            public class Main {
                int counter;

                public Main() {
                }

                public void greet() {
                }

                public int get() {
                    return 0;
                }

                public int next() {
                    int total = counter + 1;
                    counter = total;
                    counter++;
                    return 0;
                }

                public int appelHomonyme(Other other) {
                    get();
                    return other.get();
                }

                public int lireDelai() {
                    Config config = new Config();
                    return config.timeout;
                }
            }

            class Other {
                public int get() {
                    return 1;
                }
            }

            class Config {
                int timeout;
            }
        """.trimIndent()

        val SOURCE_HELPER = """
            package demo;

            public class Helper {
                public void secourir() {
                }
            }
        """.trimIndent()

        val SOURCE_MAIN_ETENDU = """
            package app;

            import demo.Helper;

            public class Main extends Helper {
                public Main() {
                }
            }
        """.trimIndent()

        val SOURCE_USER = """
            package demo;

            public class User {
                String name;

                public String getName() {
                    return name;
                }
            }
        """.trimIndent()

        val SOURCE_PRINTER = """
            package demo;

            public class Printer {
                public String rendre(User user) {
                    return user.name;
                }
            }
        """.trimIndent()

        val SOURCE_ORDER = """
            package demo;

            public class Order {
                String name;
            }
        """.trimIndent()

        val SOURCE_UNRELATED = """
            package demo;

            public class Unrelated {
                public int name() {
                    return 1;
                }
            }
        """.trimIndent()

        fun texte(racine: Path, nom: String): String = racine.resolve(nom).toFile().readText()

        fun offsetDe(source: String, mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = source.indexOf(mot, position + 1) }
            return position
        }

        /** La ligne contenant l'offset (vérification « ligne import »). */
        fun ligneA(source: String, offset: Int): String {
            var debut = 0
            for (i in 0 until offset) {
                if (source[i] == '\n') debut = i + 1
            }
            var fin = source.indexOf('\n', offset)
            if (fin < 0) fin = source.length
            return source.substring(debut, fin)
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            PROJET = Files.createTempDirectory("renommage-026")
            RACINE_A = Files.createDirectories(PROJET.resolve("a/demo")).parent!!
            RACINE_B = Files.createDirectories(PROJET.resolve("b/demo")).parent!!
            RACINE_C = Files.createDirectories(PROJET.resolve("c/demo")).parent!!
            Files.createDirectories(PROJET.resolve("b/app"))
            RACINE_A.resolve("demo/Main.java").writeText(SOURCE_MAIN)
            RACINE_B.resolve("demo/Helper.java").writeText(SOURCE_HELPER)
            RACINE_B.resolve("app/Main.java").writeText(SOURCE_MAIN_ETENDU)
            RACINE_C.resolve("demo/User.java").writeText(SOURCE_USER)
            RACINE_C.resolve("demo/Printer.java").writeText(SOURCE_PRINTER)
            RACINE_C.resolve("demo/Order.java").writeText(SOURCE_ORDER)
            RACINE_C.resolve("demo/Unrelated.java").writeText(SOURCE_UNRELATED)
            MOTEUR = MoteurJava(
                racines = listOf(RACINE_A, RACINE_B, RACINE_C),
                jars = emptyList(),
                compliance = "17",
            )
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — renommage ignoré")
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            MOTEUR.dispose()
        }
    }

    /** targetAtOnMethodDeclaration — cible méthode à la déclaration. */
    @Test
    fun targetAtOnMethodDeclaration() {
        val source = texte(RACINE_A, "demo/Main.java")
        val caret = offsetDe(source, "void greet()") + "void ".length
        val cible = MOTEUR.cibleRenommageA("demo/Main.java", source, caret)
        assertNotNull(cible, "la cible est servie")
        assertEquals("greet", cible.nomActuel, "oldName « greet »")
        assertEquals("method", cible.kind, "kind « method »")
        assertTrue(cible.cleStructurelle!!.startsWith("method:demo.Main.greet("), "clé structurelle : ${cible.cleStructurelle}")
    }

    /** targetAtOnField — cible champ, clé exacte. */
    @Test
    fun targetAtOnField() {
        val source = texte(RACINE_A, "demo/Main.java")
        val caret = offsetDe(source, "int counter;") + "int ".length
        val cible = MOTEUR.cibleRenommageA("demo/Main.java", source, caret)
        assertNotNull(cible, "la cible est servie")
        assertEquals("field", cible.kind, "kind « field »")
        assertEquals("field:demo.Main.counter", cible.cleStructurelle, "clé exacte")
    }

    /** targetAtOnLocalIsFileLocal — une locale est file-local. */
    @Test
    fun targetAtOnLocalIsFileLocal() {
        val source = texte(RACINE_A, "demo/Main.java")
        val caret = offsetDe(source, "int total =") + "int ".length
        val cible = MOTEUR.cibleRenommageA("demo/Main.java", source, caret)
        assertNotNull(cible, "la cible est servie")
        assertTrue(cible.fileLocal, "le renommage ne sortira pas du fichier")
    }

    /** targetAtOnType — cible type, clé exacte. */
    @Test
    fun targetAtOnType() {
        val source = texte(RACINE_A, "demo/Main.java")
        val caret = offsetDe(source, "class Main {") + "class ".length
        val cible = MOTEUR.cibleRenommageA("demo/Main.java", source, caret)
        assertNotNull(cible, "la cible est servie")
        assertTrue(cible.estType, "isType")
        assertEquals("type:demo.Main", cible.cleStructurelle, "clé exacte")
    }

    /** targetAtOutsideNameIsNull — hors nom : null. */
    @Test
    fun targetAtOutsideNameIsNull() {
        val source = texte(RACINE_A, "demo/Main.java")
        assertNull(MOTEUR.cibleRenommageA("demo/Main.java", source, 0), "offset 0 : aucune cible")
    }

    /** referencesIncludeDeclarationAndUses — 4 occurrences couvrant exactement « counter ». */
    @Test
    fun referencesIncludeDeclarationAndUses() {
        val source = texte(RACINE_A, "demo/Main.java")
        val caret = offsetDe(source, "int counter;") + "int ".length
        val occurrences = MOTEUR.renommageProjet("demo/Main.java", source, caret)
            .filterKeys { it.endsWith("demo/Main.java") }
            .values.flatten()
        assertEquals(4, occurrences.size, "1 déclaration + 3 usages : ${occurrences.size}")
        for (occurrence in occurrences) {
            assertEquals(
                "counter",
                source.substring(occurrence.plage.debutOffset, occurrence.plage.finOffset),
                "CHAQUE occurrence couvre exactement « counter »",
            )
        }
    }

    /** homonymMethodOfDifferentClassIsNotRenamed — l'appel other.get() n'est PAS retouché. */
    @Test
    fun homonymMethodOfDifferentClassIsNotRenamed() {
        val source = texte(RACINE_A, "demo/Main.java")
        val caret = offsetDe(source, "public int get()") + "public int ".length
        val occurrences = MOTEUR.renommageProjet("demo/Main.java", source, caret)
            .filterKeys { it.endsWith("demo/Main.java") }
            .values.flatten()
        assertEquals(2, occurrences.size, "déclaration + appel nu (l'homonyme Other.get est exclu)")
        val debutQualifie = offsetDe(source, "other.get();") + "other.".length
        for (occurrence in occurrences) {
            assertTrue(
                occurrence.plage.debutOffset != debutQualifie,
                "l'appel qualifié other.get() n'est PAS retouché",
            )
            assertEquals("get", source.substring(occurrence.plage.debutOffset, occurrence.plage.finOffset))
        }
    }

    /** qualifiedFieldReferenceIsCaught — usage qualifié de champ capté. */
    @Test
    fun qualifiedFieldReferenceIsCaught() {
        val source = texte(RACINE_A, "demo/Main.java")
        val caret = offsetDe(source, "int timeout;") + "int ".length
        val cible = MOTEUR.cibleRenommageA("demo/Main.java", source, caret)
        assertNotNull(cible, "la cible est servie")
        assertEquals("field:demo.Config.timeout", cible.cleStructurelle, "clé exacte")
        val occurrences = MOTEUR.renommageProjet("demo/Main.java", source, caret)
            .filterKeys { it.endsWith("demo/Main.java") }
            .values.flatten()
        assertEquals(2, occurrences.size, "déclaration + usage qualifié (même fichier, autre classe)")
    }

    /** typeRenameIncludesDeclarationConstructorAndImports — l'import est retouché. */
    @Test
    fun typeRenameIncludesDeclarationConstructorAndImports() {
        val source = texte(RACINE_B, "app/Main.java")
        val caret = offsetDe(source, "import demo.Helper;") + "import demo.".length
        val cible = MOTEUR.cibleRenommageA("app/Main.java", source, caret)
        assertNotNull(cible, "cible sur le segment d'import")
        assertEquals("type:demo.Helper", cible.cleStructurelle, "le segment importé vise le type")
        val parFichier = MOTEUR.renommageProjet("app/Main.java", source, caret)
        val total = parFichier.values.sumOf { it.size }
        assertTrue(total >= 2, "≥ 2 occurrences (import + extends + déclaration) : $total")
        val occurrencesMain = parFichier.filterKeys { it.endsWith("app/Main.java") }.values.flatten()
        val surImport = occurrencesMain.any { ligneA(source, it.plage.debutOffset).trim().startsWith("import") }
        assertTrue(surImport, "l'IMPORT est retouché (une occurrence vit sur une ligne « import »)")
    }

    /** constructorDeclarationCountsForTypeRename — le constructeur compte pour le type. */
    @Test
    fun constructorDeclarationCountsForTypeRename() {
        val source = texte(RACINE_A, "demo/Main.java")
        val caret = offsetDe(source, "public Main()") + "public ".length
        val cible = MOTEUR.cibleRenommageA("demo/Main.java", source, caret)
        assertNotNull(cible, "la cible est servie")
        assertTrue(cible.estType, "la cible prise sur le constructeur est le TYPE")
        val occurrences = MOTEUR.renommageProjet("demo/Main.java", source, caret)
            .filterKeys { it.endsWith("demo/Main.java") }
            .values.flatten()
        assertEquals(2, occurrences.size, "« class Main » + « Main() » : ${occurrences.size}")
        for (occurrence in occurrences) {
            assertEquals("Main", source.substring(occurrence.plage.debutOffset, occurrence.plage.finOffset))
        }
    }

    /** projectRenameSweepsSourceRoots — balayage projet multi-fichiers, homonymes exclus. */
    @Test
    fun projectRenameSweepsSourceRoots() {
        val source = texte(RACINE_C, "demo/User.java")
        val caret = offsetDe(source, "String name;") + "String ".length
        val parFichier = MOTEUR.renommageProjet("demo/User.java", source, caret)
        val user = parFichier.entries.firstOrNull { it.key.endsWith("demo/User.java") }
        val printer = parFichier.entries.firstOrNull { it.key.endsWith("demo/Printer.java") }
        assertNotNull(user, "User.java est dans la map")
        assertNotNull(printer, "Printer.java est dans la map")
        assertEquals(2, user.value.size, "User : déclaration + return name")
        assertEquals(1, printer.value.size, "Printer : user.name")
        assertTrue(parFichier.keys.none { it.endsWith("demo/Order.java") }, "Order.java est ABSENT (homonyme non retouché)")
        assertTrue(parFichier.keys.none { it.endsWith("demo/Unrelated.java") }, "Unrelated non retouché")
    }

    /** fileLocalRenameSkipsProjectSweep — file-local : pas de balayage projet. */
    @Test
    fun fileLocalRenameSkipsProjectSweep() {
        val source = texte(RACINE_A, "demo/Main.java")
        val caret = offsetDe(source, "int total =") + "int ".length
        val parFichier = MOTEUR.renommageProjet("demo/Main.java", source, caret)
        assertEquals(1, parFichier.size, "UNE seule entrée (le fichier courant) : ${parFichier.keys}")
        val occurrences = parFichier.values.first()
        assertEquals(2, occurrences.size, "déclaration + usage")
        for (occurrence in occurrences) {
            assertEquals("total", source.substring(occurrence.plage.debutOffset, occurrence.plage.finOffset))
        }
    }
}

package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.MoteurJava
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fiche 060 — NavigationFacadeTest : `textDocument/implementation` (GO TO
 * « Implementations » — les héritiers DIRECTS du type en contexte,
 * multi-fichiers par balayage des racines sources) et
 * `textDocument/superDefinition` (GO TO « Super » — le membre outrépassé
 * dans chaque supertype, sinon les supertypes DIRECTS du type en
 * contexte).
 *
 * Fixtures du cahier : `Base`/`ImplA`/`ImplB` (héritage de classe),
 * `Greeter`/`EnGreeter` (implémentation d'interface), `Nest` (sous-classe
 * imbriquée dans le même fichier), `Solo` (sans descendance), `MyThread`
 * (supertype BINAIRE java.lang.Thread — aucune source navigable).
 */
class Fiche060FacadeNavigationTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        lateinit var PROJET: Path
        lateinit var DOSSIER: Path

        val SOURCE_BASE = """
            package demo;

            public class Base {
                int counter;

                public void run() {
                }
            }
        """.trimIndent()

        val SOURCE_IMPL_A = """
            package demo;

            public class ImplA extends Base {
                String tag = "a";

                @Override
                public void run() {
                }
            }
        """.trimIndent()

        val SOURCE_IMPL_B = """
            package demo;

            public class ImplB extends Base {
            }
        """.trimIndent()

        val SOURCE_MAIN = """
            package demo;

            public class Main {
                public void use(Base b) {
                    b.run();
                }
            }
        """.trimIndent()

        val SOURCE_GREETER = """
            package demo;

            public interface Greeter {
                String greet();
            }
        """.trimIndent()

        val SOURCE_EN_GREETER = """
            package demo;

            public class EnGreeter implements Greeter {
                public String greet() {
                    return "hello";
                }
            }
        """.trimIndent()

        val SOURCE_NEST = """
            package demo;

            public class Nest {
                int tag;

                class Inner extends Nest {
                }
            }
        """.trimIndent()

        val SOURCE_SOLO = """
            package demo;

            public class Solo {
                int tag;
            }
        """.trimIndent()

        val SOURCE_MY_THREAD = """
            package demo;

            public class MyThread extends Thread {
                int tag;
            }
        """.trimIndent()

        fun texte(nom: String): String = DOSSIER.resolve(nom).toFile().readText()

        fun offsetDe(source: String, mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = source.indexOf(mot, position + 1) }
            return position
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            PROJET = Files.createTempDirectory("fiche060")
            DOSSIER = Files.createDirectories(PROJET.resolve("demo"))
            DOSSIER.resolve("Base.java").writeText(SOURCE_BASE)
            DOSSIER.resolve("ImplA.java").writeText(SOURCE_IMPL_A)
            DOSSIER.resolve("ImplB.java").writeText(SOURCE_IMPL_B)
            DOSSIER.resolve("Main.java").writeText(SOURCE_MAIN)
            DOSSIER.resolve("Greeter.java").writeText(SOURCE_GREETER)
            DOSSIER.resolve("EnGreeter.java").writeText(SOURCE_EN_GREETER)
            DOSSIER.resolve("Nest.java").writeText(SOURCE_NEST)
            DOSSIER.resolve("Solo.java").writeText(SOURCE_SOLO)
            DOSSIER.resolve("MyThread.java").writeText(SOURCE_MY_THREAD)
            MOTEUR = MoteurJava(racines = listOf(PROJET), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — fiche ignorée")
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            MOTEUR.dispose()
        }
    }

    /** implementation_typeReference_findsCrossFileSubtypes — référence de type → héritiers inter-fichiers. */
    @Test
    fun implementationTypeReferenceFindsCrossFileSubtypes() {
        val source = texte("Main.java")
        val caret = offsetDe(source, "use(Base b") + "use(".length
        val cibles = MOTEUR.implementation("demo/Main.java", source, caret)
        assertEquals(2, cibles.size, "2 cibles exactement (ImplA et ImplB)")
        val parNom = cibles.associateBy { it.nomSimple }
        assertTrue("ImplA" in parNom && "ImplB" in parNom, "les deux héritiers directs : ${cibles.map { it.nomSimple }}")
        for ((nom, cible) in parNom) {
            assertTrue(cible.chemin!!.endsWith("demo/$nom.java"), "$nom vit dans SON fichier")
            val texteFichier = cible.chemin.toFile().readText()
            val attendu = ligneCaractere(texteFichier, offsetDe(texteFichier, "class $nom") + "class ".length)
            assertEquals(
                attendu,
                ligneCaractere(texteFichier, cible.plage.debutOffset),
                "la cible est positionnée sur le NOM de classe $nom (ligne + colonne exactes)",
            )
            assertEquals("$nom", texteFichier.substring(cible.plage.debutOffset, cible.plage.finOffset), "le jeton visé est le nom")
        }
    }

    /** implementation_insideClass_findsSubtypes — caret dans la classe → classe englobante. */
    @Test
    fun implementationInsideClassFindsSubtypes() {
        val source = texte("Base.java")
        val caret = offsetDe(source, "counter")
        val cibles = MOTEUR.implementation("demo/Base.java", source, caret)
        assertEquals(2, cibles.size, "le contexte retombe sur la classe ENGLOBANTE (Base) → 2 cibles")
    }

    /** implementation_noSubclass_yieldsEmpty — sans descendance → vide. */
    @Test
    fun implementationNoSubclassYieldsEmpty() {
        val source = texte("Solo.java")
        val caret = offsetDe(source, "tag")
        assertTrue(MOTEUR.implementation("demo/Solo.java", source, caret).isEmpty(), "aucune sous-classe → liste VIDE")
    }

    /** implementation_interfaceImplementors — implémenteurs d'une interface. */
    @Test
    fun implementationInterfaceImplementors() {
        val source = texte("EnGreeter.java")
        val caret = offsetDe(source, "implements Greeter") + "implements ".length
        val cibles = MOTEUR.implementation("demo/EnGreeter.java", source, caret)
        assertEquals(1, cibles.size, "1 cible exactement")
        val cible = cibles.first()
        assertTrue(cible.chemin!!.endsWith("demo/EnGreeter.java"), "l'implémentante est EnGreeter")
        val texteFichier = cible.chemin.toFile().readText()
        val attendu = ligneCaractere(texteFichier, offsetDe(texteFichier, "class EnGreeter") + "class ".length)
        assertEquals(attendu, ligneCaractere(texteFichier, cible.plage.debutOffset), "colonne exacte du nom de classe")
        assertEquals("EnGreeter", texteFichier.substring(cible.plage.debutOffset, cible.plage.finOffset))
    }

    /** implementation_nestedSubclass_sameFile — sous-classe imbriquée même fichier. */
    @Test
    fun implementationNestedSubclassSameFile() {
        val source = texte("Nest.java")
        val caret = offsetDe(source, "int tag")
        val cibles = MOTEUR.implementation("demo/Nest.java", source, caret)
        assertEquals(1, cibles.size, "1 cible exactement")
        val cible = cibles.first()
        assertEquals("Inner", cible.nomSimple, "la sous-classe imbriquée Inner")
        assertTrue(cible.chemin!!.endsWith("demo/Nest.java"), "dans le MÊME fichier")
        val attendu = ligneCaractere(source, offsetDe(source, "class Inner") + "class ".length)
        assertEquals(attendu, ligneCaractere(source, cible.plage.debutOffset), "colonne exacte de « class Inner »")
        assertEquals("Inner", source.substring(cible.plage.debutOffset, cible.plage.finOffset))
    }

    /** super_onOverridingMethod_findsBaseMethod — super sur une redéfinition → méthode de base. */
    @Test
    fun superOnOverridingMethodFindsBaseMethod() {
        val source = texte("ImplA.java")
        val caret = offsetDe(source, "public void run()")
        val cibles = MOTEUR.superDefinition("demo/ImplA.java", source, caret)
        assertEquals(1, cibles.size, "1 cible exactement")
        val cible = cibles.first()
        assertTrue(cible.chemin!!.endsWith("demo/Base.java"), "la méthode de base vit dans Base.java")
        val texteBase = cible.chemin.toFile().readText()
        assertEquals("run", texteBase.substring(cible.plage!!.debutOffset, cible.plage!!.finOffset), "la plage désigne le NOM « run » dans la source de Base")
        assertEquals("run  ·  Base", cible.libelle, "libellé exact du picker")
    }

    /** super_onTypeInsideImpl_findsBaseClass — super hors membre outrépassant → supertype direct. */
    @Test
    fun superOnTypeInsideImplFindsBaseClass() {
        val source = texte("ImplA.java")
        val caret = offsetDe(source, "tag = \"a\"")
        val cibles = MOTEUR.superDefinition("demo/ImplA.java", source, caret)
        assertEquals(1, cibles.size, "1 cible exactement")
        val cible = cibles.first()
        assertTrue(cible.chemin!!.endsWith("demo/Base.java"), "le supertype DIRECT de ImplA")
        val texteBase = cible.chemin.toFile().readText()
        assertEquals("Base", texteBase.substring(cible.plage!!.debutOffset, cible.plage!!.finOffset), "plage sur le NOM « Base »")
        assertEquals("Base  ·  demo", cible.libelle, "libellé exact du picker")
    }

    /** super_binarySupertype_yieldsEmpty — supertype binaire → vide. */
    @Test
    fun superBinarySupertypeYieldsEmpty() {
        val source = texte("MyThread.java")
        val caret = offsetDe(source, "int tag")
        assertTrue(
            MOTEUR.superDefinition("demo/MyThread.java", source, caret).isEmpty(),
            "un supertype binaire (java.lang.Thread) n'a pas de source projet",
        )
    }

    /** super_overriddenViaInvocation_findsBaseMethod — super via une invocation → vide. */
    @Test
    fun superOverriddenViaInvocationYieldsEmpty() {
        val source = texte("Main.java")
        val caret = offsetDe(source, "b.run()") + 2
        assertTrue(
            MOTEUR.superDefinition("demo/Main.java", source, caret).isEmpty(),
            "rien au-dessus de la méthode liée (Base.run), et la retombée (Main → Object, binaire) ne donne aucune cible source",
        )
    }

    /** implementation_interfaceMethodReference_findsImplementor — sur la propre déclaration de l'interface. */
    @Test
    fun implementationInterfaceDeclarationFindsImplementor() {
        val source = texte("Greeter.java")
        val caret = offsetDe(source, "interface Greeter") + "interface ".length
        val cibles = MOTEUR.implementation("demo/Greeter.java", source, caret)
        assertEquals(1, cibles.size, "1 cible exactement")
        assertTrue(cibles.first().chemin!!.endsWith("demo/EnGreeter.java"), "les implémentations de l'interface")
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

        /** La ligne contenant l'offset (vérification ligne du cahier). */
        fun ligneDe(source: String, offset: Int): String {
            val debut = source.lastIndexOf('\n', offset - 1).let { if (it < 0) 0 else it + 1 }
            val fin = source.indexOf('\n', offset).let { if (it < 0) source.length else it }
            return source.substring(debut, fin)
        }
}

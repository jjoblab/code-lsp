package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 013 — EcjCompilerEngineTest : le moteur ecj in-process sur JVM
 * desktop (jar ecj PATCHÉ via :ecj-art — les shims satisfont les références
 * réécrites ; la plateforme vient de l'image jrt du JDK courant).
 */
class Fiche013MoteurDiagnosticsTest {

    @Test
    fun engineIsUsableOnDesktopViaJrt() {
        val moteur = MoteurJava()
        assertTrue(moteur.isLikelyUsable(), "l'image jrt du JDK courant rend le moteur utilisable")
    }

    @Test
    fun validCodeProducesNoErrors() {
        val moteur = MoteurJava()
        val erreurs = moteur.diagnose("/demo/Main.java", CorpusCommun.MAIN_OK)
            .filter { it.severite == Severite.ERREUR }
        assertEquals(0, erreurs.size, "code valide : 0 erreur — reçues : ${erreurs.map { it.message }}")
    }

    @Test
    fun undefinedVariableIsReported() {
        val moteur = MoteurJava()
        val source = """
            public class Main {
                public void m() {
                    int x = undefinedVariable;
                }
            }
        """.trimIndent()
        val erreurs = moteur.diagnose("/demo/Main.java", source).filter { it.severite == Severite.ERREUR }
        assertTrue(erreurs.isNotEmpty(), "au moins une erreur attendue")
        assertTrue(
            erreurs.any { it.message.contains("undefinedVariable") },
            "le message nomme undefinedVariable : ${erreurs.map { it.message }}",
        )
    }

    @Test
    fun typeMismatchIsReported() {
        val moteur = MoteurJava()
        val erreurs = moteur.diagnose("/demo/Main.java", CorpusCommun.MAIN_ERR)
            .filter { it.severite == Severite.ERREUR }
        assertTrue(erreurs.isNotEmpty())
        assertTrue(
            erreurs.any { it.message.contains("mismatch", ignoreCase = true) },
            "le message porte « mismatch » : ${erreurs.map { it.message }}",
        )
    }

    @Test
    fun voidMethodReturnIsReported() {
        val moteur = MoteurJava()
        val source = """
            public class Main {
                public void m() {
                    return 42;
                }
            }
        """.trimIndent()
        val erreurs = moteur.diagnose("/demo/Main.java", source).filter { it.severite == Severite.ERREUR }
        assertTrue(erreurs.isNotEmpty())
        assertTrue(
            erreurs.any { it.message.contains("void", ignoreCase = true) },
            "« Void methods cannot return a value » attendu : ${erreurs.map { it.message }}",
        )
    }

    @Test
    fun brokenCodeStillYieldsLocatedDiagnostics() {
        val moteur = MoteurJava()
        val source = """
            public class Main {
                void m() { if (x.
            """.trimIndent()
        val diagnostics = moteur.diagnose("/demo/Main.java", source)
        assertTrue(diagnostics.isNotEmpty(), "code cassé : diagnostics non vides")
        for (diagnostic in diagnostics) {
            assertTrue(
                diagnostic.plage.finOffset > diagnostic.plage.debutOffset ||
                    diagnostic.plage.finOffset == diagnostic.plage.debutOffset + 1,
                "plage localisée (au plus dégénérée de 1) : ${diagnostic.plage} — ${diagnostic.message}",
            )
        }
    }

    @Test
    fun crossFileResolutionFromSourceRoot() {
        val racine = Files.createTempDirectory("fiche013-racine")
        val paquet = racine.resolve("demo")
        Files.createDirectories(paquet)
        paquet.resolve("Helper.java").writeText(
            """
            package demo;
            public class Helper {
                public static final int ANSWER = 42;
            }
            """.trimIndent(),
        )
        val source = """
            public class Main {
                public int reponse() {
                    return demo.Helper.ANSWER;
                }
            }
        """.trimIndent()
        val moteur = MoteurJava(racines = listOf(racine))
        val erreurs = moteur.diagnose("/demo/Main.java", source).filter { it.severite == Severite.ERREUR }
        assertEquals(0, erreurs.size, "résolution inter-fichiers sans build — reçues : ${erreurs.map { it.message }}")
    }

    @Test
    fun jarClasspathResolvesTypes() {
        // Le jar de classpath est localisé dynamiquement : le jar JUnit du
        // runtime de test (fiche 013 : « jar JUnit du classpath de test »).
        val jarJupiter = localiserJarJupiter()
        assertNotNull(jarJupiter, "jar JUnit Jupiter localisé dans le runtime de test")
        val source = """
            public class Main {
                @org.junit.jupiter.api.Test
                public void essai() {
                }
            }
        """.trimIndent()
        val moteur = MoteurJava(jars = listOf(jarJupiter!!))
        val erreurs = moteur.diagnose("/demo/Main.java", source).filter { it.severite == Severite.ERREUR }
        assertEquals(0, erreurs.size, "résolution binaire depuis le jar — reçues : ${erreurs.map { it.message }}")
    }

    /** Localise le jar junit-jupiter-api du classpath de test courant. */
    private fun localiserJarJupiter(): Path? {
        val classe = org.junit.jupiter.api.Test::class.java
        val url = classe.protectionDomain.codeSource.location
        return try {
            Path.of(url.toURI())
        } catch (_: Exception) {
            null
        }
    }
}

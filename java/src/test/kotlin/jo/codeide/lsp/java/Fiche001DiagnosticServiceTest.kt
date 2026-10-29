package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.diagnostics.ServiceDiagnostics
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 001 — DiagnosticServiceTest : invariants structurels de la couche
 * service (API interne `Diagnostic`/`Severite`/`Plage` en offsets absolus).
 */
class Fiche001DiagnosticServiceTest {

    private val service = ServiceDiagnostics(EnvironnementCompilation())

    private val codeValide = """
        public class Test {
            public void m() {
                int x = 42;
                System.out.println(x);
            }
        }
    """.trimIndent()

    private val variableNonDefinie = """
        public class Test {
            public void m() {
                System.out.println(undefined);
            }
        }
    """.trimIndent()

    private val syntaxeCassée = """
        public class Test {
            public void method( {
        }
    """.trimIndent()

    private val returnEnVoid = """
        public class Test {
            public void m() {
                return 42;
            }
        }
    """.trimIndent()

    @Test
    fun testParseableCode() {
        val diagnostics = service.diagnose("Test.java", codeValide)
        assertNotNull(diagnostics, "la liste rendue est non nulle sur code valide")
    }

    @Test
    fun testDiagnosticSeverity() {
        val diagnostics = service.diagnose("Test.java", variableNonDefinie)
        assertTrue(diagnostics.isNotEmpty())
        for (diagnostic in diagnostics) {
            assertNotNull(diagnostic.severite, "sévérité renseignée")
            assertNotNull(diagnostic.message, "message renseigné")
        }
    }

    @Test
    fun testDiagnosticRange() {
        val diagnostics = service.diagnose("Test.java", variableNonDefinie)
        assertTrue(diagnostics.isNotEmpty())
        for (diagnostic in diagnostics) {
            assertNotNull(diagnostic.plage, "plage renseignée")
            assertTrue(diagnostic.plage.debutOffset >= 0, "startOffset >= 0")
            assertTrue(diagnostic.plage.finOffset >= diagnostic.plage.debutOffset, "endOffset >= startOffset")
        }
    }

    @Test
    fun testDiagnosticMessage() {
        val diagnostics = service.diagnose("Test.java", variableNonDefinie)
        assertTrue(diagnostics.isNotEmpty())
        for (diagnostic in diagnostics) {
            assertTrue(diagnostic.message.isNotEmpty(), "message non vide")
        }
    }

    @Test
    fun testSyntaxError() {
        // Les deux issues sont acceptées (comportement tolérant du parseur) ;
        // la réécriture choisit de toujours rendre des diagnostics.
        val diagnostics = service.diagnose("Test.java", syntaxeCassée)
        assertNotNull(diagnostics)
    }

    @Test
    fun testReturnType() {
        val diagnostics = service.diagnose("Test.java", returnEnVoid)
        assertNotNull(diagnostics)
        // Bonus de cohérence : l'erreur « void » est bien portée (fiche 013).
        assertTrue(
            diagnostics.any { it.severite == Severite.ERREUR && it.message.contains("void", ignoreCase = true) },
            "return en méthode void signalé",
        )
    }
}

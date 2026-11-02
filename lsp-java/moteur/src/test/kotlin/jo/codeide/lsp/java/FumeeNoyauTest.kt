package jo.codeide.lsp.java

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fumée du noyau N1 — vérification d'intégrité du pont ecj avant la suite
 * du cahier (usage interne de mise au point, conservé comme banc minimal).
 */
class FumeeNoyauTest {

    private val mainOk = """
        public class Main {
            public static void main(String[] args) {
                String message = "Hello, " + args.length;
                System.out.println(message);
            }
        }
    """.trimIndent()

    private val mainErr = """
        public class Main {
            public static void main(String[] args) {
                String s = 42;
            }
        }
    """.trimIndent()

    @Test
    fun diagnoseCodeValideZeroErreur() {
        val moteur = MoteurJava()
        assertTrue(moteur.isLikelyUsable(), "l'image jrt du runtime doit rendre le moteur utilisable")
        val diagnostics = moteur.diagnose("/demo/Main.java", mainOk)
        val erreurs = diagnostics.filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
        assertEquals(0, erreurs.size, "code valide : 0 erreur — reçues : ${erreurs.map { it.message }}")
    }

    @Test
    fun diagnoseMismatchSignale() {
        val moteur = MoteurJava()
        val diagnostics = moteur.diagnose("/demo/Main.java", mainErr)
        val erreurs = diagnostics.filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
        assertTrue(erreurs.isNotEmpty(), "au moins une erreur attendue")
        assertTrue(erreurs.any { it.message.contains("mismatch", ignoreCase = true) }, "message mismatch attendu : ${erreurs.map { it.message }}")
    }
}

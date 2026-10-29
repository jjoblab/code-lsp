package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.interne.ItemCompletion
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 031 — ExternalJrtTest : le jrt EXTERNE — résolution des classes
 * java.* depuis un JDK autre que le runtime courant via son `lib/jrt-fs.jar`
 * (URLClassLoader + ServiceLoader du fournisseur jrt).
 */
class Fiche031JrtExterneTest {

    /** Le JDK externe à chemin fixe (contrat du cahier). */
    private val jdkExterne: Path = Path.of("/usr/lib/jvm/java-21-openjdk-amd64")

    /** Le JDK externe est présent (motif visible `@EnabledIf`). */
    private fun aJdkExterne(): Boolean = Files.isRegularFile(jdkExterne.resolve("lib/modules"))

    @Test
    fun resolvesJavaLangFromExternalJdkViaJrtFsJar() {
        assumeTrue(aJdkExterne(), "JDK 21 externe absent — test ignoré (skip visible)")
        val moteur = MoteurJava(jdkHome = jdkExterne)
        assertTrue(moteur.isLikelyUsable(), "le jrt externe rend le moteur utilisable")
        val source = """
            public class Essai {
                public String salut() {
                    return new java.lang.String("bonjour");
                }
            }
        """.trimIndent()
        val diagnostics = moteur.diagnose("/Essai.java", source)
        assertTrue(
            diagnostics.none { it.severite == Severite.ERREUR && it.message.contains("cannot be resolved") },
            "aucun message « cannot be resolved » : ${diagnostics.map { it.message }}",
        )
    }

    @Test
    fun externalJdkCompletesStringMembers() {
        assumeTrue(aJdkExterne(), "JDK 21 externe absent — test ignoré (skip visible)")
        val moteur = MoteurJava(jdkHome = jdkExterne)
        val source = """
            public class Essai {
                public int taille(String s) {
                    return s.le;
                }
            }
        """.trimIndent()
        val items: List<ItemCompletion> = moteur.complete("/Essai.java", source, source.indexOf("s.le") + 4)
        assertTrue(items.isNotEmpty(), "complétion non null")
        assertTrue(
            items.any { it.libelle == "length" },
            "« length » proposé — les membres de String résolvent depuis l'image externe : ${items.map { it.libelle }}",
        )
    }

    @Test
    fun nullJdkHomeFallsBackToRuntime() {
        val moteur = MoteurJava()
        assertTrue(moteur.isLikelyUsable(), "jdkHome null : le jrt du runtime courant est utilisé par défaut")
    }
}

package jo.codeide.lsp.java

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 039 — RealClasspathDeviceReproTest : variante HAUTE FIDÉLITÉ — le
 * VRAI classpath complet (export `model.json` réel du projet CodeIDE — 91
 * jars + boot + sourceSets) avec les conditions EXACTES du device (ordre
 * des racines : racine projet EN PREMIER).
 *
 * Le test dépend de chemins machine absolus (model.json exporté) : absent
 * de la machine → repro ignoré (hypothèse visible — comme le cahier).
 */
class Fiche039ClasspathReelTest {

    private val source = """
        package com.example.r;

        import android.os.Bundle;
        import androidx.appcompat.app.AppCompatActivity;

        public class MainActivity extends AppCompatActivity {
            @Override
            protected void onCreate(Bundle b) {
                super.onCreate(b);
                setContentView(R.layout.activity_main);
            }
        }
    """.trimIndent()

    /** Parsing minimal du model.json (regex, org.json indisponible — miroir du cahier). */
    private fun analyserModele(modelJson: Path): Triple<List<Path>, List<Path>, List<Path>> {
        val texte = Files.readString(modelJson)
        val jarsCompile = extraireJars(texte, "compileClasspath")
        val jarsBoot = extraireJars(texte, "bootClasspath")
        val javaDirs = extraireChemins(texte, "javaDirs")
        val jarsExistant = (jarsCompile + jarsBoot).map(Path::of).filter { Files.isRegularFile(it) }.distinct()
        val dirsExistants = javaDirs.map(Path::of).filter { Files.isDirectory(it) }
        return Triple(jarsExistant, dirsExistants, dirsExistants)
    }

    /** Section chaîne ou tableau d'un model.json (parsing tolérant). */
    private fun section(texte: String, nom: String): String {
        val regex = Regex("""\"$nom\"\s*:\s*\"([^\"]*)\"""")
        val liste = Regex("""\"$nom\"\s*:\s*\[([^\]]*)\]""")
        return regex.find(texte)?.groupValues?.get(1)
            ?: liste.find(texte)?.groupValues?.get(1)
            ?: ""
    }

    /** Jars cités dans une section. */
    private fun extraireJars(texte: String, nom: String): List<String> =
        Regex("""[^"]+\.jar""").findAll(section(texte, nom)).map { it.value }.toList()

    /** Chemins cités dans une section tableau. */
    private fun extraireChemins(texte: String, nom: String): List<String> =
        Regex("""[^",]+""").findAll(section(texte, nom))
            .map { it.value.trim().trim('"') }
            .filter { it.isNotEmpty() }
            .toList()

    @Test
    fun diagnoseWithRealFullClasspathDeviceConditions() {
        val modelJson = Path.of("/home/z/my-project/upload/model.json")
        assumeTrue(Files.isRegularFile(modelJson), "model.json réel absent — repro ignoré (hypothèse visible)")
        val (jars, javaDirs, _) = analyserModele(modelJson)
        assumeTrue(jars.size >= 50, "classpath parsé trop petit (attendu ~93) — repro ignoré")

        // Racines : racine projet d'abord (conditions EXACTES du device).
        val racineProjet = modelJson.toAbsolutePath().parent.parent
        val racines = listOf(racineProjet) + javaDirs
        val jdkHome = Path.of("/home/z/my-project/opt/jdk-17")
        val moteur = MoteurJava(
            racines = racines,
            jars = jars,
            jdkHome = if (Files.isDirectory(jdkHome)) jdkHome else null,
        )
        // (2) Sondes de résolution non nulles (épreuve off-by-one incluse).
        assertNotNull(moteur.typeIndex().find("android.os.Bundle"), "Bundle résout (épreuve off-by-one)")
        assertNotNull(moteur.typeIndex().find("androidx.appcompat.app.AppCompatActivity"), "AppCompatActivity résout (appcompat indexé)")
        assertNotNull(moteur.typeIndex().find("java.lang.Object"), "java.lang.Object résout (image jrt)")
        // (3) Diagnostics : aucun « cannot be resolved » sur android.*/appcompat,
        // aucun « does not match the expected package » ; le bruit s'effondre.
        val diagnostics = moteur.diagnose("/com/example/r/MainActivity.java", source)
        assertTrue(
            diagnostics.none { it.message.contains("android.os.Bundle cannot be resolved") },
            "régression dure : Bundle doit résoudre",
        )
        assertTrue(
            diagnostics.none { it.message.contains("androidx.appcompat.app.AppCompatActivity cannot be resolved") },
            "régression dure : AppCompatActivity doit résoudre",
        )
        assertTrue(
            diagnostics.none { it.message.contains("does not match the expected package") },
            "régression FQCN avec racine projet en premier",
        )
        // (4) Le reliquat autorisé : le R d'avant premier build seulement (≤ 2).
        assertTrue(
            diagnostics.size <= 2,
            "le bruit total s'effondre (≤ 2 — le symptôme du bug était ~31) : ${diagnostics.map { it.message }}",
        )
    }
}

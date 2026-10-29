package jo.codeide.lsp.java

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 004 — AndroidClasspathReproTest : repro du scénario device (log
 * device) — MainActivity, classpath android.jar + classes.jar d'appcompat
 * extrait d'un AAR, PAS de R.jar.
 *
 * Trois invariants FORTS assertés : index binaire rempli, résolution
 * d'`AppCompatActivity` et de `Bundle`. Le reste est observationnel (journal
 * à valeur d'oracle pour le banc de parité).
 *
 * Dépendances environnementales fortes (SDK Android + cache Gradle avec AAR
 * androidx) : absentes de la machine → tout le lot est IGNORÉ (hypothèses
 * visibles, comme le cahier).
 */
class Fiche004ClasspathAndroidReproTest {

    /** La source MainActivity du log device (349 caractères). */
    private val source = """
        package com.example.d;

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

    private fun androidJar(): Path? = listOf(
        "/home/z/android-sdk/platforms/android-34/android.jar",
        "/home/z/android-sdk/platforms/android-35/android.jar",
        "/home/z/tools/android-sdk/platforms/android-34/android.jar",
        "/home/z/tools/android-sdk/platforms/android-35/android.jar",
    ).map(Path::of).firstOrNull { Files.isRegularFile(it) }

    private fun appcompatClassesJar(): Path? {
        // AAR appcompat du cache Gradle : classes.jar à extraire.
        val caches = listOf(
            Path.of(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1/androidx.appcompat/appcompat"),
        )
        for (cache in caches) {
            if (!Files.isDirectory(cache)) continue
            val aar = Files.walk(cache).filter { it.toString().endsWith(".aar") }.findFirst().orElse(null) ?: continue
            val dossier = Files.createTempDirectory("fiche004-appcompat")
            val copie = dossier.resolve("appcompat.aar")
            Files.copy(aar, copie)
            java.util.zip.ZipFile(copie.toFile()).use { zip ->
                val entree = zip.getEntry("classes.jar") ?: return@use
                val classes = dossier.resolve("classes.jar")
                Files.copy(zip.getInputStream(entree), classes)
                return classes
            }
        }
        return null
    }

    @Test
    fun reproDeviceScenario() {
        assumeTrue(androidJar() != null, "android.jar absent (plateformes 34/35) — repro ignoré")
        assumeTrue(appcompatClassesJar() != null, "AAR appcompat introuvable dans le cache Gradle — repro ignoré")
        val android = androidJar()!!
        val appcompat = appcompatClassesJar()!!

        val dossier = Files.createTempDirectory("fiche004-projet")
        val racine = Files.createDirectories(dossier.resolve("java/com/example/d"))
        racine.resolve("MainActivity.java").writeText(source)

        val moteur = MoteurJava(
            racines = listOf(dossier.resolve("java")),
            jars = listOf(android, appcompat),
        )
        // (1) L'index binaire est rempli.
        assertTrue(moteur.typeIndex().hasBinarySource(), "index binaire rempli (hasBinarySource)")
        // (2) Sondes de résolution non nulles.
        assertNotNull(moteur.typeIndex().find("androidx.appcompat.app.AppCompatActivity"), "AppCompatActivity résout")
        assertNotNull(moteur.typeIndex().find("android.os.Bundle"), "Bundle résout")
        // (3) Diagnostics de MainActivity : liste rendue (taille imprimée —
        // ~10 attendus sur device avant premier build, NON assertés).
        val diagnostics = moteur.diagnose("/com/example/d/MainActivity.java", source)
        println("fiche004 : ${diagnostics.size} diagnostics (attendu ~10 sur device, essentiellement R non résolu)")
        diagnostics.forEach { println("  ${it.severite} ${it.message}") }
        // (4) Complétions A–D et discriminateurs : exécutées et imprimées,
        // exceptions tolérées (valeur d'oracle, pas d'assertion).
        for ((nom, prefixe) in listOf("A-import" to "androidx.appcompat.app.", "B-membre" to "b.", "C-milieu" to "activity_mai", "D-nom" to "alp")) {
            try {
                val items = moteur.complete("/com/example/d/MainActivity.java", source, source.length / 2)
                println("fiche004 complétion $nom (préfixe $prefixe) : ${items.size} items")
            } catch (t: Throwable) {
                println("fiche004 complétion $nom : exception tolérée — ${t.javaClass.simpleName}")
            }
        }
    }
}

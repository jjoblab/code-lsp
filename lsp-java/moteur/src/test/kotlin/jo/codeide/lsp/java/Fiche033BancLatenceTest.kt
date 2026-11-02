package jo.codeide.lsp.java

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertTrue

/**
 * Fiche 033 — LatenceCompletionBenchmark : BANC de mesure (pas un test
 * d'assertion) — latence de complétion À CHAUD dans le scénario « les
 * suggestions sont devenues lentes ». AUCUN seuil asserté : le banc produit
 * des nombres à comparer entre versions.
 *
 * Corpus réaliste : deux racines sources, 12 Helpers, un record, un binding
 * généré, MainActivity, classpath android.jar + R.jar. Sans android.jar le
 * banc est ignoré (skip visible).
 */
class Fiche033BancLatenceTest {

    private fun androidJar(): Path? = listOf(
        "/home/z/android-sdk/platforms/android-37.2/android.jar",
        "/home/z/android-sdk/platforms/android-34/android.jar",
        "/home/z/tools/android-sdk/platforms/android-37.2/android.jar",
    ).map(Path::of).firstOrNull { Files.isRegularFile(it) }

    /** Construit le corpus complet du banc (retourne les deux racines). */
    private fun corpus(): Pair<Path, Path> {
        val dossier = Files.createTempDirectory("fiche033-monip")
        val src = Files.createDirectories(dossier.resolve("app/src/main/java/com/example/monip"))
        val generated = Files.createDirectories(dossier.resolve("app/src/generated/java/com/example/monip"))
        for (i in 0 until 12) {
            src.resolve("Helper$i.java").writeText(
                """
                package com.example.monip;
                public class Helper$i {
                    public int valeur$i() { return $i; }
                    public String nom$i() { return "h$i"; }
                }
                """.trimIndent(),
            )
        }
        generated.resolve("Modele.java").writeText(
            """
            package com.example.monip;
            public record Modele(String titre, int poids) { }
            """.trimIndent(),
        )
        generated.resolve("ActivityMainBinding.java").writeText(
            """
            package com.example.monip;
            public class ActivityMainBinding {
                public android.widget.TextView titre;
                public static ActivityMainBinding inflate(Object inflater) { return new ActivityMainBinding(); }
            }
            """.trimIndent(),
        )
        src.resolve("MainActivity.java").writeText(sourceMain(src))
        return src to generated
    }

    private fun sourceMain(src: Path): String {
        val helpers = (0 until 12).joinToString(" + ") { "h$it.valeur$it()" }
        return """
            package com.example.monip;

            import android.os.Bundle;
            import android.widget.TextView;

            public class MainActivity extends android.app.Activity {
                private Modele modele = new Modele("t", 1);
                private ActivityMainBinding binding;
                private Helper0 h0 = new Helper0();
                private Helper1 h1 = new Helper1();

                protected void onCreate(Bundle b) {
                    binding.titre.setText(modele.titre());
                    int somme = $helpers;
                }
            }
        """.trimIndent()
    }

    @Test
    fun latenceCompletionMembre() {
        assumeTrue(androidJar() != null, "android.jar absent — banc ignoré (skip visible)")
        val (src, generated) = corpus()
        val moteur = MoteurJava(racines = listOf(src, generated), jars = listOf(androidJar()!!))
        val source = Files.readString(src.resolve("MainActivity.java"))
        val caret = source.indexOf("binding.titre.") + "binding.titre.".length
        moteur.complete("/com/example/monip/MainActivity.java", source, caret) // 1 échauffement
        val latences = (1..10).map {
            val debut = System.nanoTime()
            moteur.complete("/com/example/monip/MainActivity.java", source, caret)
            (System.nanoTime() - debut) / 1_000_000.0
        }
        println("fiche033 membre : ${latences.joinToString(" / ")} ms")
        assertTrue(true, "le banc documente la latence, il ne la valide pas")
    }

    @Test
    fun latenceCompletionType() {
        assumeTrue(androidJar() != null, "android.jar absent — banc ignoré (skip visible)")
        val (src, generated) = corpus()
        val moteur = MoteurJava(racines = listOf(src, generated), jars = listOf(androidJar()!!))
        val source = "package com.example.monip;\npublic class Essai { void m() { int x = TextVi; } }\n"
        val caret = source.indexOf("TextVi") + "TextVi".length
        moteur.complete("/com/example/monip/Essai.java", source, caret)
        val latences = (1..10).map {
            val debut = System.nanoTime()
            moteur.complete("/com/example/monip/Essai.java", source, caret)
            (System.nanoTime() - debut) / 1_000_000.0
        }
        println("fiche033 type : ${latences.joinToString(" / ")} ms")
        assertTrue(true)
    }

    @Test
    fun latenceCompletionVide() {
        assumeTrue(androidJar() != null, "android.jar absent — banc ignoré (skip visible)")
        val (src, generated) = corpus()
        val moteur = MoteurJava(racines = listOf(src, generated), jars = listOf(androidJar()!!))
        val source = "package com.example.monip;\npublic class Essai { void m() { int x = 5;  } }\n"
        val caret = source.indexOf("int x = 5; ") + "int x = 5; ".length
        moteur.complete("/com/example/monip/Essai.java", source, caret)
        val latences = (1..10).map {
            val debut = System.nanoTime()
            moteur.complete("/com/example/monip/Essai.java", source, caret)
            (System.nanoTime() - debut) / 1_000_000.0
        }
        println("fiche033 vide (pire cas popup spontané) : ${latences.joinToString(" / ")} ms")
        assertTrue(true)
    }
}

package jo.codeide.lsp.java

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * REPRODUCTION du faux positif appareil du 01/11/2026 : « I cannot be
 * resolved to a type » / « O cannot be resolved to a type » + « Name
 * clash … has the same erasure » sur CHAQUE usage de
 * `registerForActivityResult(ActivityResultContract<I,O>,
 * ActivityResultCallback<O>)` — les PARAMÈTRES DE TYPES de la méthode
 * générique HÉRITÉE (déclarés sur la méthode de ComponentActivity) ne
 * résolvent pas dans la sous-classe, alors que javac et Android Studio
 * compilent ce fichier SANS erreur.
 */
class ReproGeneriquesHeritesTest {

    @TempDir
    lateinit var dossier: Path

    @Test
    fun `les parametres de type de la methode heritee ne sont pas des faux positifs`() {
        val racine = Files.createDirectories(dossier.resolve("racine/com/exemple/monip"))
        val classes = OutilsFixture.compilerEnMemoire(
            dossier,
            mapOf(
                "/android/app/Activity.java" to """
                    package android.app;
                    public class Activity { }
                """.trimIndent(),
                "/androidx/activity/result/ActivityResultContract.java" to """
                    package androidx.activity.result;
                    public abstract class ActivityResultContract<I, O> {
                        public abstract O creer(I entree);
                    }
                """.trimIndent(),
                "/androidx/activity/result/ActivityResultCallback.java" to """
                    package androidx.activity.result;
                    public interface ActivityResultCallback<O> {
                        void onResult(O resultat);
                    }
                """.trimIndent(),
                "/androidx/activity/ComponentActivity.java" to """
                    package androidx.activity;
                    public class ComponentActivity extends android.app.Activity {
                        public final <I, O> void registerForActivityResult(
                                androidx.activity.result.ActivityResultContract<I, O> contrat,
                                androidx.activity.result.ActivityResultCallback<O> rappel) { }
                    }
                """.trimIndent(),
                "/androidx/appcompat/app/AppCompatActivity.java" to """
                    package androidx.appcompat.app;
                    public class AppCompatActivity extends androidx.activity.ComponentActivity { }
                """.trimIndent(),
            ),
        )
        val jar = dossier.resolve("fake.jar")
        OutilsFixture.ecrireJar(jar, classes.entries.associate { (nom, octets) -> nom to octets })

        val source = """
            package com.example.monip;

            import androidx.appcompat.app.AppCompatActivity;
            import androidx.activity.result.ActivityResultContract;

            public class MainActivity extends AppCompatActivity {
                @Override
                protected void onCreate(android.os.Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    ActivityResultContract<String, Boolean> contrat = null;
                    registerForActivityResult(contrat, null);
                }
            }
        """.trimIndent() + "\n"
        Files.writeString(racine.resolve("MainActivity.java"), source)

        val jdk = jo.codeide.lsp.java.navigation.JdkDeTest.home()
        org.junit.jupiter.api.Assumptions.assumeTrue(jdk != null, "JDK de test indisponible")

        // SONDE de montage : le jar et sa lisibilité binaire.
        val entrees = jo.codeide.lsp.java.classpath.LecteurZipBrut.scanNames(jar)
        println("[REPRO] jar → ${entrees.size} entrées : ${entrees.take(12)}")

        val moteur = MoteurJava(
            racines = listOf(racine.parent),
            jars = listOf(jar),
            jdkHome = jdk,
        )
        println("[REPRO] isBinaryType(AppCompatActivity) = ${moteur.isBinaryType("androidx.appcompat.app.AppCompatActivity")}")
        val diagnostics = moteur.diagnose(racine.resolve("MainActivity.java").toString(), source)
        val messages = diagnostics.map { it.message }
        println("[REPRO] diagnostics → $messages")
        // Garde de montage : la classe DU JAR doit résoudre (sinon le test
        // passe à vide en régime instantané).
        assertTrue(
            messages.none { it.contains("AppCompatActivity cannot be resolved") },
            "garde de montage : AppCompatActivity (du jar) résout — reçu : $messages",
        )
        val fauxPositifs = messages.filter {
            it.contains("cannot be resolved to a type") &&
                (it.startsWith("I ") || it.startsWith("O ") || it.startsWith("I,") || it.startsWith("O,"))
        }
        assertTrue(
            fauxPositifs.isEmpty(),
            "les paramètres de types I/O de la méthode héritée ne sont pas des erreurs — " +
                "reçu : $fauxPositifs",
        )
        assertTrue(
            messages.none { it.contains("Name clash") },
            "pas de name clash fantôme sur la méthode héritée — reçu : $messages",
        )
        moteur.dispose()
    }
}

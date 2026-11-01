package jo.codeide.lsp.java

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * REPRODUCTION UNITAIRE du figage appareil du 01/11/2026 (logs
 * codeide-logs-2026-11-01-144401.zip, CodeIDE 0.205.0 / moteur 1.12.0) :
 * les complétions TIMEOUT en cascade après une SECONDE invocation à la
 * MÊME ancre (logs : caret=190 demandé à 448,61 s PUIS 450,06 s — la
 * 2e invocation V-C3 « inaccessibles marqués + statiques importables »)
 * et le moteur ne répond plus (plus de publishDiagnostics non plus — le
 * verrou de compilation est retenu par la requête figée).
 *
 * Le différenciateur appareil vs banc : le classpath RÉEL — le JDK
 * bootstrap (jrt) + android.jar + les jars du modèle, avec les indices
 * de DOUBLONS (diagnostics « Name clash … has the same erasure » sur
 * registerForActivityResult : la même classe androidx vue DEUX fois).
 * Ce banc reconstruit ce classpath : un jar androidx factice PRÉSENT
 * DEUX FOIS dans la liste des jars, jdkHome = le JDK du banc (jrt).
 *
 * Le contrat : la 2e invocation à la même ancre doit revenir dans la
 * borne — un dépassement vide les piles de tous les fils dans le
 * rapport (motif ChaosConcurrenceTest) : le point de blocage exact
 * doit être lisible.
 */
class ReproFreezeInvocation2Test {

    @TempDir
    lateinit var dossier: Path

    /** Les sources du jar androidx factice (la hiérarchie des logs). */
    private val sourcesAndroidx = mapOf(
        "/android/app/Activity.java" to """
            package android.app;
            public class Activity {
                public void setContentView(int id) { }
                public <T extends android.view.View> T findViewById(int id) { return null; }
            }
        """.trimIndent(),
        "/android/view/View.java" to """
            package android.view;
            public class View {
                public void setTag(Object t) { }
            }
        """.trimIndent(),
        "/android/widget/TextView.java" to """
            package android.widget;
            public class TextView extends android.view.View {
                public void setText(CharSequence t) { }
                public CharSequence getText() { return null; }
            }
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
            public class AppCompatActivity extends androidx.activity.ComponentActivity {
                public void setSupportActionBar(Object bar) { }
                public Object getSupportActionBar() { return null; }
            }
        """.trimIndent(),
    )

    /** Le MainActivity des logs (package déclaré ≠ dossier, usage générique). */
    private val sourceMain = """
        package com.example.monip;

        import android.os.Bundle;
        import android.widget.TextView;
        import androidx.appcompat.app.AppCompatActivity;
        import androidx.activity.result.ActivityResultContract;
        import androidx.activity.result.ActivityResultCallback;

        public class MainActivity extends AppCompatActivity {

            private int eieueie;

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(0);
                ActivityResultContract<String, Boolean> contrat = null;
                registerForActivityResult(contrat, null);
                TextView textView = findViewById(0);
                textView.
            }
        }
    """.trimIndent() + "\n"

    /** Le montage complet : racine + jar androidx ×2 (le DOUBLON des logs). */
    private fun monter(): Triple<MoteurJava, Path, Int> {
        val racine = Files.createDirectories(dossier.resolve("racine/com/exemple/monip"))
        Files.writeString(racine.resolve("MainActivity.java"), sourceMain)

        val classes = OutilsFixture.compilerEnMemoire(dossier, sourcesAndroidx)
        val jar = dossier.resolve("fake-androidx.jar")
        OutilsFixture.ecrireJar(
            jar,
            classes.entries.associate { (nom, octets) -> nom to octets },
        )

        val jdk = jo.codeide.lsp.java.navigation.JdkDeTest.home()
        org.junit.jupiter.api.Assumptions.assumeTrue(jdk != null, "JDK de test indisponible")

        val moteur = MoteurJava(
            racines = listOf(racine.parent),
            jars = listOf(jar, jar), // LE DOUBLON : la même classe vue deux fois
            jdkHome = jdk,
        )
        val offsetMembre = sourceMain.indexOf("textView.") + "textView.".length
        return Triple(moteur, racine.resolve("MainActivity.java"), offsetMembre)
    }

    /** Vidage des piles de tous les fils (motif ChaosConcurrenceTest). */
    private fun dumpDesPiles(contexte: String): String = buildString {
        appendLine("=== FIGAGE : $contexte — piles de tous les fils ===")
        for ((fil, elements) in Thread.getAllStackTraces().entries.sortedBy { it.key.name }) {
            appendLine("--- FIL ${fil.name} (état ${fil.state}) ---")
            elements.take(30).forEach { appendLine("    $it") }
        }
    }

    /** Exécute borné ; au dépassement, DUMP + échec franc. */
    private fun borne(contexte: String, calcul: () -> Unit) {
        val fil = Thread {
            try {
                calcul()
            } catch (t: Throwable) {
                println("[REPRO] $contexte → EXCEPTION ${t.javaClass.simpleName}: ${t.message?.take(120)}")
            }
        }
        fil.isDaemon = true
        fil.start()
        fil.join(TimeUnit.SECONDS.toMillis(20))
        if (fil.isAlive) {
            fail(dumpDesPiles(contexte))
        }
    }

    @Test
    fun `deuxieme invocation membre a la meme ancre avec jar duplique ne figure pas`() {
        val (moteur, chemin, offset) = monter()
        // 1re invocation (membre : textView.| )
        borne("1re invocation membre") {
            val r1 = moteur.completer(chemin.toString(), sourceMain, offset)
            println("[REPRO] 1re invocation membre → ${r1.items.size} item(s)")
        }
        // 2e invocation : MÊME ancre, MÊME préfixe — la clé C-08 D4 est
        // identique, l'invocation monte à 2 (inaccessibles marqués).
        borne("2e invocation membre (l'ancre du figage appareil)") {
            val r2 = moteur.completer(chemin.toString(), sourceMain, offset)
            println("[REPRO] 2e invocation membre → ${r2.items.size} item(s)")
        }
        // 3e invocation (projet) — la borne suivante du contrat C-08.
        borne("3e invocation membre") {
            val r3 = moteur.completer(chemin.toString(), sourceMain, offset)
            println("[REPRO] 3e invocation membre → ${r3.items.size} item(s)")
        }
        moteur.dispose()
    }

    @Test
    fun `deuxieme invocation nom avec prefixe ne figure pas`() {
        val (moteur, chemin, _) = monter()
        // Contexte NOM : un identifiant nu avec préfixe (l'ancre des
        // STATIQUES IMPORTABLES — fiche C-08 D2, le 2e appel).
        val sourceNom = sourceMain.replace("textView.\n", "tex\n")
        val offsetNom = sourceNom.indexOf("tex") + 3
        borne("1re invocation nom (tex|)") {
            val r1 = moteur.completer(chemin.toString(), sourceNom, offsetNom)
            println("[REPRO] 1re invocation nom → ${r1.items.size} item(s)")
        }
        borne("2re invocation nom (tex| — statiques importables)") {
            val r2 = moteur.completer(chemin.toString(), sourceNom, offsetNom)
            println("[REPRO] 2e invocation nom → ${r2.items.size} item(s)")
        }
        borne("3e invocation nom (tex| — projet)") {
            val r3 = moteur.completer(chemin.toString(), sourceNom, offsetNom)
            println("[REPRO] 3e invocation nom → ${r3.items.size} item(s)")
        }
        moteur.dispose()
    }
}

package jo.codeide.lsp.java

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SONDE DE PERFORMANCE — figage appareil du 01/11/2026 : mesure le coût
 * des invocations SUCCESSIVES de complétion à la MÊME ancre (V-C3 : la
 * 2e élargit aux inaccessibles + STATIQUES IMPORTABLES, la 3e au projet)
 * sur un classpath RÉEL — le android.jar de la plateforme 37 (43 Mo,
 * ~40 000 classes), plateforme portée PAR LE JAR (jdkHome = null : le
 * miroir exact de l'appareil, où le fournisseur jrt ne s'instancie pas
 * sur ART et où android.jar porte java.* ET android.*).
 *
 * Hypothèse éprouvée : la 2e invocation est le PREMIER chemin qui touche
 * l'ANNUAIRE du classpath (importsStatiques → typesDirects du paquet
 * courant + java.lang) — sa construction paresseuse par paquet balaye
 * les tables centrales des jars ; avec le android.jar réel, le coût
 * dépasse la borne client de 5 s et tout appel annuaire-mate (la
 * diagnose qui suit un didChange) se met DERRIÈRE le même verrou feuille
 * → plus de publishDiagnostics, tout timeout (les logs appareil).
 */
class SondeInvocation2AndroidJarTest {

    @TempDir
    lateinit var dossier: Path

    /** Le android.jar réel (fixtures du dépôt de banc). */
    private fun androidJar(): Path? {
        val candidat = Path.of("/home/z/my-project/fixtures/android.jar")
        org.junit.jupiter.api.Assumptions.assumeTrue(
            Files.isRegularFile(candidat),
            "android.jar réel absent des fixtures — sonde ignorée",
        )
        return candidat
    }

    /** Le MainActivity des logs. */
    private val sourceMain = """
        package com.example.monip;

        import android.os.Bundle;
        import android.widget.TextView;
        import androidx.appcompat.app.AppCompatActivity;

        public class MainActivity extends AppCompatActivity {

            private int eieueie;

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(0);
                TextView textView = findViewById(0);
                textView.
            }
        }
    """.trimIndent() + "\n"

    @Test
    fun `le cout des invocations successives reste sous la borne client`() {
        val jar = androidJar()
        val racine = Files.createDirectories(dossier.resolve("racine/com/exemple/monip"))
        Files.writeString(racine.resolve("MainActivity.java"), sourceMain)

        val moteur = MoteurJava(
            racines = listOf(racine.parent),
            jars = listOf(jar!!),
            jdkHome = null, // plateforme PORTÉE PAR LE JAR — le miroir ART
        )
        val chemin = racine.resolve("MainActivity.java").toString()
        val offset = sourceMain.indexOf("textView.") + "textView.".length

        // La diagnose initiale (l'équivalent du didOpen + premier tir).
        var t = System.nanoTime()
        moteur.diagnose(chemin, sourceMain)
        println("[SONDE] diagnose initiale : ${(System.nanoTime() - t) / 1_000_000} ms")

        // Les invocations successives à la MÊME ancre (l'ancre des logs).
        for (invocation in 1..3) {
            t = System.nanoTime()
            val reponse = moteur.completer(chemin, sourceMain, offset)
            val ms = (System.nanoTime() - t) / 1_000_000
            println("[SONDE] complétion invocation $invocation → ${reponse.items.size} item(s) : $ms ms")
            assertTrue(ms < 5_000, "invocation $invocation à ${ms} ms — au-delà de la borne client de 5 s")
        }

        // Le contexte NOM (l'ancre des STATIQUES IMPORTABLES).
        val sourceNom = sourceMain.replace("textView.\n", "tex\n")
        val offsetNom = sourceNom.indexOf("tex") + 3
        for (invocation in 1..3) {
            t = System.nanoTime()
            val reponse = moteur.completer(chemin, sourceNom, offsetNom)
            val ms = (System.nanoTime() - t) / 1_000_000
            println("[SONDE] complétion NOM (tex|) invocation $invocation → ${reponse.items.size} item(s) : $ms ms")
            assertTrue(ms < 5_000, "invocation NOM $invocation à ${ms} ms — au-delà de la borne client")
        }

        // La diagnose APRÈS (l'équivalent du didChange + tir — sur
        // l'appareil, elle ne revenait plus).
        t = System.nanoTime()
        moteur.diagnose(chemin, sourceMain)
        println("[SONDE] diagnose après les complétions : ${(System.nanoTime() - t) / 1_000_000} ms")

        moteur.dispose()
    }
}

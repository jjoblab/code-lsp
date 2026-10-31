package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.projetandroid.ModeleAndroidDerive

/**
 * BANC PROJET ANDROID LIGHT (vague V-B — ADR 0021) : le montage RÉEL du
 * banc (`bancs/projet-android` — vraie plateforme android.jar 34-ext12,
 * vrais AARs androidx/material) SANS la racine des sources générées —
 * le régime « projet jamais buildé » du propriétaire (§0.1-2).
 *
 * Critère de sortie de la vague, mesuré ici : zéro « cannot be resolved »
 * sur `R.*`/`BuildConfig` AVANT tout build (P3 étendu, ADR 0021) — et la
 * dérivation DISQUE du contrat suffit (aucune sync, aucun Gradle).
 *
 * Gate environnementale (motif Fiche004) : sans les bibliothèques réelles,
 * la suite s'ignore avec un message VISIBLE — jamais un vert silencieux.
 */
class BancProjetAndroidLightTest {

    /** La racine des bibliothèques réelles (téléchargées hors dépôt). */
    private val libs: Path? = sequenceOf(
        System.getenv("BANC_ANDROID_LIBS")?.let { Path.of(it) },
        Path.of("../bancs/projet-android-libs"),
        Path.of("../../bancs/projet-android-libs"),
    ).firstOrNull { p -> p != null && Files.isDirectory(p) && Files.isRegularFile(p.resolve("android-34-ext12.jar")) }

    /** La racine des sources :app du banc (SANS les générées). */
    private val monip: Path? = sequenceOf(
        Path.of("../bancs/projet-android/app/src/main/java"),
        Path.of("../../bancs/projet-android/app/src/main/java"),
    ).firstOrNull { Files.isDirectory(it) }

    @Test
    fun `r et buildconfig resolus avant build - derivation disque sans sync ni gradle`() {
        assumeTrue(libs != null && monip != null, "bancs/projet-android-libs absent — BANC_ANDROID_LIBS")

        // Le contrat vient UNIQUEMENT du disque (build.gradle.kts + res/).
        val contrat = ModeleAndroidDerive.deriver(listOf(monip!!))!!
        val moteur = MoteurJava(
            listOf(monip),
            listOfNotNull(
                libs!!.resolve("android-34-ext12.jar"),
                libs.resolve("appcompat-classes.jar"),
                libs.resolve("core-classes.jar"),
                libs.resolve("annotation-1.8.0.jar"),
                libs.resolve("material-classes.jar"),
                libs.resolve("constraintlayout-classes.jar"),
                libs.resolve("lifecycle-runtime-classes.jar"),
                libs.resolve("drawerlayout-classes.jar"),
                libs.resolve("fragment-classes.jar"),
                libs.resolve("activity-classes.jar"),
                libs.resolve("lifecycle-viewmodel-classes.jar"),
                libs.resolve("lifecycle-viewmodel-savedstate-classes.jar"),
                libs.resolve("savedstate-classes.jar"),
                libs.resolve("tracing-classes.jar"),
                libs.resolve("emoji2-classes.jar"),
                libs.resolve("core-viewtree-classes.jar"),
                libs.resolve("lifecycle-common-2.6.2.jar"),
            ),
            null,
            "17",
            null,
            null,
            contrat,
        )
        assertTrue(moteur.aClassesLegereAndroid(), "contrat actif (namespace + res/)")

        // UNE activité réelle du banc, SANS les sources générées : seule la
        // référence au DataBinding reste (V-B3 — la fabrique binding).
        val source = """
            package com.example.monip;

            import android.os.Bundle;
            import android.widget.TextView;
            import androidx.appcompat.app.AppCompatActivity;

            public class EcranR extends AppCompatActivity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.activity_main);
                    TextView titre = findViewById(R.id.text_titre);
                    titre.setText(R.string.app_nom);
                }
            }
        """.trimIndent()
        val diagnostics = moteur.diagnose("/com/example/monip/EcranR.java", source)
        val erreurs = diagnostics.filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
        assertTrue(
            erreurs.none { erreur -> erreur.message.contains("R.") || erreur.message.contains("BuildConfig") },
            "zéro « cannot be resolved » sur R.* — erreurs réelles : $erreurs",
        )

        // L'anti-doublon dans l'AUTRE sens : AVEC la racine générée (le
        // simulacre commis = la sortie AGP), c'est la SOURCE qui répond.
        val genere = monip.parent.resolve("../../../projet-android-genere").normalize()
        if (Files.isDirectory(genere)) {
            val moteurPostBuild = MoteurJava(
                listOf(monip, genere),
                moteur.javaClass.let { emptyList() },
                null,
                "17",
                null,
                null,
                contrat,
            )
            val localisation = moteurPostBuild.typeIndex().find("com.example.monip.R")
            assertTrue(
                localisation is jo.codeide.lsp.java.classpath.EnvironnementNoms.Localisation.Source,
                "post-build : la source générée réelle PRIME",
            )
        }
    }
}

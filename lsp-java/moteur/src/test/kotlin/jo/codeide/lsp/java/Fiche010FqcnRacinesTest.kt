package jo.codeide.lsp.java

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 010 — DeviceFqcnReproTest : ordre des racines sources et FQCN
 * longest-match — le calcul du FQCN depuis les racines doit matcher le PLUS
 * LONG préfixe de paquet (le vrai source set), JAMAIS la première racine
 * par ordre d'itération ; l'ordre des racines ne doit plus être signifiant.
 *
 * L'INVARIANT (longest-match + aucun « does not match the expected
 * package ») est éprouvé SANS android.jar (classpaths vides) ; le repro
 * intégral (diagnostics d'écart, complétions) exige le SDK — ignoré sinon
 * (hypothèses visibles).
 */
class Fiche010FqcnRacinesTest {

    /** La source MainActivity du log device (349 caractères, modèle device). */
    private val source = """
        package com.example.r;

        public class MainActivity extends android.app.Activity {
            protected void onCreate(android.os.Bundle b) {
                super.onCreate(b);
                setContentView(0);
            }
        }
    """.trimIndent()

    /** Projet factice Rifk : settings.gradle à la racine (la racine « ressemble à un projet »). */
    private fun projetFactice(): Path {
        val dossier = Files.createTempDirectory("Rifk")
        dossier.resolve("settings.gradle").writeText("rootProject.name = 'rifk'\n")
        val src = Files.createDirectories(dossier.resolve("app/src/main/java/com/example/r"))
        src.resolve("MainActivity.java").writeText(source)
        return dossier
    }

    @Test
    fun fqcnLongestMatchIndependantDeLOrdre() {
        val dossier = projetFactice()
        val racineProjet = dossier
        val srcMain = dossier.resolve("app/src/main/java")
        val fichier = srcMain.resolve("com/example/r/MainActivity.java")

        // Ordre DEVICE (racine projet EN PREMIER) — l'ordre historique fautif.
        val moteurDevice = MoteurJava(racines = listOf(racineProjet, srcMain))
        // Ordre corrigé (source set seul).
        val moteurCorrige = MoteurJava(racines = listOf(srcMain))

        assertEquals(
            "com.example.r.MainActivity",
            moteurDevice.fqcnPour(fichier),
            "le FQCN matche le PLUS LONG préfixe (ordre device : racine projet d'abord)",
        )
        assertEquals(
            "com.example.r.MainActivity",
            moteurCorrige.fqcnPour(fichier),
            "le FQCN est identique dans l'ordre corrigé",
        )
        // Le FQCN segmenté par les slashes (le bug historique) ne doit JAMAIS apparaître.
        assertTrue(
            moteurDevice.fqcnPour(fichier) != "app.src.main.java.com.example.r.MainActivity",
            "jamais le FQCN segmenté par les slashes",
        )
    }

    @Test
    fun ordreDeviceAucunDiagnosticDePaquetAttendu() {
        // L'invariant fort du cahier : AUCUN diagnostic du moteur device ne
        // contient « does not match the expected package », même dans
        // l'ordre racine-projet-d'abord. Le repro intégral exige android.jar
        // (ignoré ici) — l'invariant est éprouvé avec le jrt du runtime.
        val dossier = projetFactice()
        val racineProjet = dossier
        val srcMain = dossier.resolve("app/src/main/java")

        val moteurDevice = MoteurJava(racines = listOf(racineProjet, srcMain))
        val moteurCorrige = MoteurJava(racines = listOf(srcMain))
        val chemin = "/app/src/main/java/com/example/r/MainActivity.java"

        val diagsDevice = moteurDevice.diagnose(chemin, source)
        val diagsCorrige = moteurCorrige.diagnose(chemin, source)
        assertTrue(
            diagsDevice.none { it.message.contains("does not match the expected package") },
            "aucun « does not match the expected package » dans l'ordre device : ${diagsDevice.map { it.message }}",
        )
        assertTrue(
            diagsCorrige.size <= diagsDevice.size,
            "l'ordre corrigé produit strictement moins ou autant de bruit (${diagsCorrige.size} vs ${diagsDevice.size})",
        )
    }

    @Test
    fun reproIntegraleOrdreDeviceContreOrdreCorrige() {
        // Repro intégral : mêmes moteurs AVEC android.jar + appcompat —
        // diagnostics d'écart et complétions imprimés (valeur d'oracle).
        assumeTrue(
            listOf("/home/z/android-sdk/platforms/android-34/android.jar", "/home/z/android-sdk/platforms/android-35/android.jar")
                .any { Files.isRegularFile(Path.of(it)) },
            "android.jar absent — repro intégral ignoré (skip visible)",
        )
        val dossier = projetFactice()
        val android = listOf("/home/z/android-sdk/platforms/android-34/android.jar", "/home/z/android-sdk/platforms/android-35/android.jar")
            .map(Path::of).first { Files.isRegularFile(it) }
        val moteurDevice = MoteurJava(racines = listOf(dossier, dossier.resolve("app/src/main/java")), jars = listOf(android))
        val moteurCorrige = MoteurJava(racines = listOf(dossier.resolve("app/src/main/java")), jars = listOf(android))
        val chemin = "/app/src/main/java/com/example/r/MainActivity.java"
        val diagsDevice = moteurDevice.diagnose(chemin, source)
        val diagsCorrige = moteurCorrige.diagnose(chemin, source)
        println("fiche010 : device=${diagsDevice.size} correctes=${diagsCorrige.size} diagnostics")
        assertTrue(diagsCorrige.size <= diagsDevice.size, "l'ordre corrigé produit moins ou autant de bruit")
        assertTrue(
            diagsDevice.none { it.message.contains("does not match the expected package") },
            "le calcul FQCN longest-match ne doit plus régresser, même dans l'ordre historique",
        )
    }
}

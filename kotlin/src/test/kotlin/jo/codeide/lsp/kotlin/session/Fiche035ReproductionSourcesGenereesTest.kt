package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 035 — ReproductionSourcesGenereesTest : REPRODUCTION H2 (mission
 * fiabilisation, étape A.2 — hypothèse « sources générées invisibles » du
 * prompt) — 2 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * La MARCHE du scan EXCLUT le dossier `build` (exclusions ADR 0212 :
 * sorties, caches — le scan est le SOURCE). Or les sources GÉNÉRÉES d'un
 * projet Android réel vivent précisément dans l'arbre `build/generated` :
 * KSP/Hilt (`Hilt_` préfixé), Room (`_Impl` suffixé), DataBinding
 * (`BindingImpl` suffixé). Leurs noms sont MUETS par convention documentée
 * — leurs sources sont invisibles PAR CONCEPTION.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche035ReproductionSourcesGenereesTest {

    /** La racine temporaire du projet. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Sème un fichier dans un dossier donné. */
    private fun semer(dossier: Path, nomFichier: String, contenu: String) {
        Files.createDirectories(dossier)
        Files.writeString(dossier.resolve(nomFichier), contenu)
    }

    /** Le projet : `src` (scanné) et `build/generated` (exclu par convention). */
    private fun racineSources(): Path {
        semer(
            racine.resolve("app/src/com/exemple/genere"),
            "App.kt",
            """
            package com.exemple.genere

            class App
            """.trimIndent(),
        )
        semer(
            racine.resolve("app/src/com/exemple/genere"),
            "MainActivity.kt",
            """
            package com.exemple.genere

            class MainActivity
            """.trimIndent(),
        )
        // Les sources GÉNÉRÉES : Hilt (préfixe), Room (suffixe), DataBinding
        // (suffixe) — elles vivent dans build/generated, EXCLU du scan.
        semer(
            racine.resolve("app/build/generated/x/com/exemple/genere"),
            "Hilt_App.kt",
            """
            package com.exemple.genere

            class Hilt_App : App()
            """.trimIndent(),
        )
        semer(
            racine.resolve("app/build/generated/x/com/exemple/genere"),
            "BaseDonnees_Impl.kt",
            """
            package com.exemple.genere

            class BaseDonnees_Impl
            """.trimIndent(),
        )
        return racine.resolve("app/src")
    }

    /** Session branchée sur la racine src + stdlib (le build est exclu). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val copieStdlib = Files.createTempFile(racine, "f035-stdlib", ".jar").toFile()
        copieStdlib.writeBytes(jarStdlib().readBytes())
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copieStdlib.path, "f035:stdlib")),
            racinesSources = listOf(racineSources().toAbsolutePath().toString()),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 90) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /** Les messages « non résolue » du document. */
    private fun nonResolues(session: SessionLangageKotlinDocuments, uri: String): List<String> =
        session.diagnostics(uri)
            .filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
            .map { it.message }

    @Test
    fun `source-generee-dans-build---le-parent-Hilt-est-muet-(convention-generee)`() {
        val session = session()
        val source =
            """
            package com.exemple.genere

            class Hilt_App : App()

            fun demo(a: App): String = a.toString() + Hilt_App().toString()
            """.trimIndent()
        val uri = "file:///generees-hilt.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // La classe GÉNÉRÉE (convention Hilt_) vit dans build/generated,
        // INVISIBLE du scan par conception — ses références restent MUETTES
        // (silence conservateur D3, convention documentée).
        assertTrue(recus.none { it.contains("Hilt_App") },
            "Hilt_App (convention générée) muet : $recus",
        )
        session.close()
    }

    @Test
    fun `controle---la-meme-classe-dans-une-racine-non-exclue-est-muette`() {
        val session = session()
        val source =
            """
            package com.exemple.genere

            fun demo(m: MainActivity, a: App): String = m.toString() + a.toString()
            """.trimIndent()
        val uri = "file:///generees-controle.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le contrôle : la MÊME espèce de classe dans une racine NON
        // exclue (src, scannée) résout — muette aussi, mais par PREUVE de
        // présence (le harnais ne confond pas silence et convention).
        assertTrue(recus.isEmpty(), "les classes des racines scannées sont muettes : $recus")
        session.close()
    }
}

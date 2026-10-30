package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 027 — ReproductionGradleKtsTest : REPRODUCTION des fichiers
 * GRADLE KOTLIN DSL (.gradle.kts) — le build.gradle.kts et
 * settings.gradle.kts du template « Empty Views Activity » d'Android
 * Studio (version-catalog `libs`, blocs plugins/android/dependencies,
 * récepteurs implicites injectés par le DSL Gradle) — 5 comportements du
 * cahier docs/cahier-specifications-kotlin/.
 *
 * Miroir Android Studio : le DSL Gradle est INJECTÉ par le modèle Gradle
 * — sans lui (pas de classpath Gradle), IntelliJ ne signale PAS les noms
 * du DSL. Le régime SCRIPT du moteur rend les noms nus muets ; le Kotlin
 * STANDARD du script (stdlib, locales) résout quand même ; la garde D3
 * garde le segment QUALIFIÉ inconnu signalé.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche027ReproductionGradleKtsTest {

    /** La racine temporaire de la copie du jar. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une copie isolée du jar (le classpath de test reste intact). */
    private fun copieStdlib(): java.io.File {
        val copie = Files.createTempFile(racine, "f027-stdlib", ".jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        return copie
    }

    /** Session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copieStdlib().path, "f027:stdlib")),
            racinesSources = emptyList(),
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
    fun `gradle-kts---build-gradle-kts-du-template,-aucun-faux-positif`() {
        val session = session()
        val source =
            """
            plugins {
                alias(libs.plugins.android.application)
            }

            android {
                namespace = "com.exemple.app"
                compileSdk = 35

                defaultConfig {
                    applicationId = "com.exemple.app"
                    minSdk = 24
                    targetSdk = 35
                }
            }

            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.appcompat)
            }
            """.trimIndent()
        val uri = "file:///build.gradle.kts"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le régime SCRIPT : les récepteurs implicites du DSL Gradle sont
        // inconnus (aucun modèle Gradle posé) — les noms nus et sélecteurs
        // sur récepteurs non typables restent muets (miroir IntelliJ sans
        // modèle Gradle).
        assertTrue(recus.isEmpty(), "le build.gradle.kts du template sans faux positif : $recus")
        session.close()
    }

    @Test
    fun `gradle-kts---settings-gradle-kts-du-template,-aucun-faux-positif`() {
        val session = session()
        val source =
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "MonApp"

            include(":app")
            """.trimIndent()
        val uri = "file:///settings.gradle.kts"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.isEmpty(), "le settings.gradle.kts du template sans faux positif : $recus")
        session.close()
    }

    @Test
    fun `gradle-kts---le-Kotlin-du-script-resout-quand-meme-(stdlib,-locales)`() {
        val session = session()
        val source =
            """
            val sortie = "build/rapport"
            val nom = "rapport"

            println(nom + " vers " + sortie)
            """.trimIndent()
        val uri = "file:///tache.gradle.kts"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le Kotlin STANDARD du script : les LOCALES et la STDLIB
        // (println) ne voient aucun faux positif — le régime script ne
        // coupe que les preuves, jamais les résolutions.
        assertTrue(recus.isEmpty(), "le Kotlin standard du script résout quand même : $recus")
        // Et le SURVOL de la locale rend sa signature (val nom: String).
        val survol = session.survol(uri, source.indexOf("nom"))
        assertTrue(
            survol != null && survol.signature.contains("nom"),
            "le survol du Kotlin standard du script rend la signature : ${survol?.signature}",
        )
        session.close()
    }

    @Test
    fun `gradle-kts---garde-D3---le-segment-qualifie-inconnu-reste-signale`() {
        val session = session()
        val source =
            """
            val moteur: kotlin.io.TypeInconnuDuScript? = null
            """.trimIndent()
        val uri = "file:///garde.gradle.kts"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // La garde D3 : le régime script ne coupe pas les PREUVES de TYPES
        // — le dernier segment d'un chemin qualifié inconnu reste SIGNALÉ
        // (l'émission est attendue).
        assertEquals(1, recus.size, "le segment qualifié inconnu reste signalé : $recus")
        assertTrue(recus.single().contains("TypeInconnuDuScript"))
        session.close()
    }

    @Test
    fun `gradle-kts---survol-du-Kotlin-standard-du-script`() {
        val session = session()
        val source =
            """
            val repertoire = "build"

            fun tacherie(): Int = repertoire.length
            """.trimIndent()
        val uri = "file:///survol.gradle.kts"
        session.ouvrir(uri, source)
        // Le SURVOL du Kotlin standard : la fonction du script rend sa
        // signature écrite.
        val survol = session.survol(uri, source.indexOf("tacherie"))
        assertTrue(
            survol != null && survol.signature.contains("tacherie") && survol.signature.contains("Int"),
            "le survol du Kotlin standard du script rend la signature : ${survol?.signature}",
        )
        session.close()
    }
}

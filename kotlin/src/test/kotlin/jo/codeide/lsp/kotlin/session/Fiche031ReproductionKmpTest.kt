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
 * Fiche 031 — ReproductionKmpTest : REPRODUCTION du template Kotlin
 * Multiplatform (KMP) d'Android Studio au niveau MOTEUR — 5 comportements
 * du cahier docs/cahier-specifications-kotlin/.
 *
 * Le module `shared` a DEUX racines sources : commonMain (les `expect`) et
 * androidMain (les `actual`) — le paquet y vit DEUX fois, le scan des
 * racines nourrit le modèle des DEUX côtés, les usages de chaque côté ne
 * voient AUCUN faux positif. Le go-to-definition de l'usage commonMain
 * va vers l'`expect` (fiche 031 : URI de fichier + plage du nom du scan).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche031ReproductionKmpTest {

    /** La racine temporaire du module shared. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Sème un fichier dans un dossier donné et rend le dossier. */
    private fun semer(dossier: Path, nomFichier: String, contenu: String): Path {
        Files.createDirectories(dossier)
        Files.writeString(dossier.resolve(nomFichier), contenu)
        return dossier
    }

    /** Les DEUX racines du module shared : commonMain et androidMain. */
    private fun racinesKmp(): List<Path> {
        val common = semer(
            racine.resolve("shared/src/commonMain/kotlin/com/exemple/kmp"),
            "Platform.kt",
            """
            package com.exemple.kmp

            expect fun platformName(): String

            expect class Platform
            """.trimIndent(),
        )
        semer(
            racine.resolve("shared/src/commonMain/kotlin/com/exemple/kmp"),
            "Greeting.kt",
            """
            package com.exemple.kmp

            class Greeting {
              fun saluer(): String = "Bonjour " + platformName()
            }
            """.trimIndent(),
        )
        val android = semer(
            racine.resolve("shared/src/androidMain/kotlin/com/exemple/kmp"),
            "Platform.android.kt",
            """
            package com.exemple.kmp

            actual fun platformName(): String = "Android"

            class AndroidPlatform

            actual typealias Platform = AndroidPlatform
            """.trimIndent(),
        )
        return listOf(common, android)
    }

    /** Session branchée sur les deux racines + stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val copieStdlib = Files.createTempFile(racine, "f031-stdlib", ".jar").toFile()
        copieStdlib.writeBytes(jarStdlib().readBytes())
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copieStdlib.path, "f031:stdlib")),
            racinesSources = racinesKmp().map { it.toAbsolutePath().toString() },
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        session.rafraichirSources()
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
    fun `kmp---commonMain,-expect-et-usages,-zero-faux-positif`() {
        val session = session()
        val source =
            """
            package com.exemple.kmp

            fun demo(p: Platform): String = platformName() + Greeting().saluer()
            """.trimIndent()
        val uri = "file:///kmp-common-usages.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // commonMain : les usages de l'expect (fonction, classe) et de la
        // classe voisine ne voient AUCUN faux positif — le scan des deux
        // racines nourrit le modèle.
        assertTrue(recus.isEmpty(), "usages commonMain sans faux positif : $recus")
        session.close()
    }

    @Test
    fun `kmp---expect-declasse-et-fonction,-fichier-meme,-zero-faux-positif`() {
        val session = session()
        val source =
            """
            package com.exemple.kmp

            expect fun calculer(): Int

            expect class Conteneur

            fun demo(c: Conteneur): Int = calculer() + c.toString().length
            """.trimIndent()
        val uri = "file:///kmp-expect-meme-fichier.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // L'expect de classe et de fonction dans le MÊME fichier que les
        // usages : aucun faux positif.
        assertTrue(recus.isEmpty(), "expect et usages du même fichier sans faux positif : $recus")
        session.close()
    }

    @Test
    fun `kmp---androidMain,-actual-et-usages,-zero-faux-positif`() {
        val session = session()
        val source =
            """
            package com.exemple.kmp

            actual fun versionAndroid(): String = "15"

            fun demo(): String = platformName() + versionAndroid() + AndroidPlatform().toString()
            """.trimIndent()
        val uri = "file:///kmp-android-usages.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // androidMain : les actual (la fonction actual platformName vit
        // dans la racine) et les usages croisés ne voient AUCUN faux
        // positif.
        assertTrue(recus.isEmpty(), "usages androidMain sans faux positif : $recus")
        session.close()
    }

    @Test
    fun `kmp---survol-de-l-expect-platformName-depuis-commonMain`() {
        val session = session()
        val source =
            """
            package com.exemple.kmp

            fun demo(): String = platformName()
            """.trimIndent()
        val uri = "file:///kmp-survol-expect.kt"
        session.ouvrir(uri, source)
        // Le SURVOL de l'usage rend la signature de l'EXPECT (le scan des
        // racines porte la déclaration externe).
        val survol = session.survol(uri, source.indexOf("platformName"))
        assertTrue(
            survol != null && survol.signature.contains("platformName") && survol.signature.contains("String"),
            "le survol de platformName rend la signature de l'expect : ${survol?.signature}",
        )
        session.close()
    }

    @Test
    fun `kmp---definition-go-to-depuis-l-usage-common-vers-l-expect`() {
        val session = session()
        val source =
            """
            package com.exemple.kmp

            fun demo(): String = platformName()
            """.trimIndent()
        val uri = "file:///kmp-definition-expect.kt"
        session.ouvrir(uri, source)
        // Le GO-TO-DEFINITION de l'usage commonMain va vers l'EXPECT :
        // l'URI de FICHIER du scan (Platform.kt de commonMain) et la plage
        // du nom de la déclaration.
        val emplacements = session.definition(uri, source.indexOf("platformName"))
        assertEquals(1, emplacements.size, "un emplacement de définition : $emplacements")
        val cible = emplacements.single()
        assertTrue(
            cible.uri.endsWith("Platform.kt"),
            "la définition vit dans Platform.kt de commonMain : ${cible.uri}",
        )
        assertTrue(cible.plage.debut >= 0 && cible.plage.fin > cible.plage.debut, "la plage du nom est rendue")
        session.close()
    }
}

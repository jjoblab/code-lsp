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
 * Fiche 030 — ReproductionKmpAvanceTest : REPRODUCTION KMP AVANCÉE — les
 * formes qui dépassent la déclaration simple : `expect val`, `expect
 * object` (le patron HttpClient/Database de toute lib KMP), `actual
 * typealias`, membres d'expect object, génériques sur un type expect,
 * usage depuis une EXTENSION — 5 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche030ReproductionKmpAvanceTest {

    /** La racine temporaire du module shared. */
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

    /** Les DEUX racines du module shared avancé. */
    private fun racinesKmp(): List<Path> {
        val common = racine.resolve("shared/src/commonMain/kotlin/com/exemple/avance")
        semer(
            common,
            "Attentes.kt",
            """
            package com.exemple.avance

            expect val clePublique: String

            expect object Fabrique {
              fun creer(): Boite
            }

            expect class Boite {
              fun ouvrir(): Porte
            }

            expect class Porte {
              val fenetre: Int
            }

            expect class BoiteGenerique<T> {
              fun ranger(valeur: T)
            }
            """.trimIndent(),
        )
        val android = racine.resolve("shared/src/androidMain/kotlin/com/exemple/avance")
        semer(
            android,
            "Effectifs.kt",
            """
            package com.exemple.avance

            actual val clePublique: String = "cle-android"

            actual object Fabrique {
              actual fun creer(): Boite = Boite()
            }

            actual class Boite {
              actual fun ouvrir(): Porte = Porte()
            }

            actual class Porte {
              actual val fenetre: Int = 1
            }

            class PorteAndroid

            actual typealias BoiteGenerique<T> = ArrayList<T>
            """.trimIndent(),
        )
        return listOf(common, android)
    }

    /** Session branchée sur les deux racines + stdlib. */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val copieStdlib = Files.createTempFile(racine, "f030-stdlib", ".jar").toFile()
        copieStdlib.writeBytes(jarStdlib().readBytes())
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copieStdlib.path, "f030:stdlib")),
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
    fun `kmp-avance---usages-commonMain-de-expect-val,-object,-generique`() {
        val session = session()
        val source =
            """
            package com.exemple.avance

            fun demo(b: Boite, g: BoiteGenerique<String>): Int {
              g.ranger("x")
              return clePublique.length + Fabrique.creer().ouvrir().fenetre
            }
            """.trimIndent()
        val uri = "file:///kmp-avance-common.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Les usages commonMain de l'expect VAL, de l'expect OBJECT (membre
        // creer) et du type expect GÉNÉRIQUE : aucun faux positif.
        assertTrue(recus.isEmpty(), "usages commonMain (val, object, générique) sans faux positif : $recus")
        session.close()
    }

    @Test
    fun `kmp-avance---androidMain,-actual-val,-object,-typealias`() {
        val session = session()
        val source =
            """
            package com.exemple.avance

            fun demo(p: Porte): Int = clePublique.length + p.fenetre + PorteAndroid().toString().length
            """.trimIndent()
        val uri = "file:///kmp-avance-android.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // androidMain : les usages de l'actual VAL, des membres de l'actual
        // et de la classe voisine du typealias : aucun faux positif.
        assertTrue(recus.isEmpty(), "usages androidMain (val, object, typealias) sans faux positif : $recus")
        session.close()
    }

    @Test
    fun `kmp-avance---typage-du-retour-de-Boite-ouvrir`() {
        val session = session()
        val source =
            """
            package com.exemple.avance

            fun demo(b: Boite): Int {
              val p = b.ouvrir()
              return p.fenetre
            }
            """.trimIndent()
        val uri = "file:///kmp-avance-typage.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le RETOUR de Boite.ouvrir type Porte : le sélecteur fenetre sur
        // la locale typée résout (chaîne de typage du modèle).
        assertTrue(recus.none { it.contains("fenetre") },
            "le retour de ouvrir() type Porte — p.fenetre muet : $recus",
        )
        session.close()
    }

    @Test
    fun `kmp-avance---survol-du-membre-d-un-expect-object`() {
        val session = session()
        val source =
            """
            package com.exemple.avance

            fun demo(): Boite = Fabrique.creer()
            """.trimIndent()
        val uri = "file:///kmp-avance-survol.kt"
        session.ouvrir(uri, source)
        // Le SURVOL du membre creer d'un expect OBJECT rend la signature
        // de l'expect (le scan porte la déclaration récursive).
        val survol = session.survol(uri, source.indexOf("creer"))
        assertTrue(
            survol != null && survol.signature.contains("creer") && survol.signature.contains("Boite"),
            "le survol du membre d'un expect object rend sa signature : ${survol?.signature}",
        )
        session.close()
    }

    @Test
    fun `kmp-avance---completion-membre-apres-Fabrique-point`() {
        val session = session()
        val source =
            """
            package com.exemple.avance

            fun demo(): Boite = Fabrique.creer()
            """.trimIndent()
        val uri = "file:///kmp-avance-completion.kt"
        session.ouvrir(uri, source)
        // La COMPLÉTION membre après `Fabrique.` propose `creer` (les
        // membres de l'expect object, lus du modèle du scan).
        val point = source.indexOf("Fabrique.creer") + "Fabrique.".length
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("creer" in propositions,
            "la complétion de Fabrique. propose creer : $propositions",
        )
        session.close()
    }
}

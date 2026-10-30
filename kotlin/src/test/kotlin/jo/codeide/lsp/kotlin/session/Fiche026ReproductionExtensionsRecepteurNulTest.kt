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
 * Fiche 026 — ReproductionExtensionsRecepteurNulTest : EXTENSIONS À
 * RÉCEPTEUR PARAMÈTRE DE TYPE (mission fiabilisation, étape A.3 — mesure
 * née du différentiel [jo.lspkotlin.classpath.LecteurClassBrutDifferentielTest])
 * — 2 comportements du cahier docs/cahier-specifications-kotlin/.
 *
 * `let`/`also`/`apply`/`run`/`takeIf`/`takeUnless` sont déclarées
 * `fun <T> T.…(…)` : leur récepteur n'est PAS une classe, l'index des
 * appelables les porte SANS récepteur — elles atteignent TOUT récepteur,
 * leur présence MUET la preuve d'absence du sélecteur. Le récepteur de la
 * démonstration est un type ANDROID LISIBLE du classpath (android.jar
 * AOSP + kotlin-stdlib réels posés en entrées) : le harnais ne valide pas
 * un « tout muet » — un membre réellement ABSENT du même récepteur reste
 * SIGNALÉ.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche026ReproductionExtensionsRecepteurNulTest {

    /** La racine temporaire des copies de jars. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Le jar android.jar AOSP du classpath de test (Maven Central, API 16). */
    private fun jarAndroid(): java.io.File =
        java.io.File(
            android.util.Log::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une copie isolée d'un jar (le classpath de test reste intact). */
    private fun copieDe(jar: java.io.File, suffixe: String): java.io.File {
        val copie = Files.createTempFile(racine, "f026-$suffixe", ".jar").toFile()
        copie.writeBytes(jar.readBytes())
        return copie
    }

    /** Session branchée sur les jars donnés (guichet attendu par sondage borné). */
    private fun session(jars: List<java.io.File>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f026:$i:${jar.name}")
            },
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
    fun `let-also-apply-run-takeIf-takeUnless---muets-malgre-un-recepteur-classpath-lisible`() {
        val session = session(listOf(copieDe(jarAndroid(), "android"), copieDe(jarStdlib(), "stdlib")))
        val source =
            """
            import android.widget.TextView

            fun demo(tv: TextView) {
              val a = tv.let { it.text }
              val b = tv.also { }
              val c = tv.apply { }
              val d = tv.run { text }
              val e = tv.takeIf { true }
              val f = tv.takeUnless { false }
            }
            """.trimIndent()
        val uri = "file:///extensions-recepteur-nul.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Les six extensions a recepteur parametre de type : MUETTES sur le
        // recepteur classpath lisible — leur presence dans l'index (sans
        // recepteur de classe) muet la preuve (D3).
        assertTrue(
            recus.none {
                it.contains("let") || it.contains("also") || it.contains("apply") ||
                    it.contains("run") || it.contains("takeIf") || it.contains("takeUnless")
            },
            "let/also/apply/run/takeIf/takeUnless muets : $recus",
        )
        session.close()
    }

    @Test
    fun `controle---un-membre-reellement-absent-du-recepteur-lisible-reste-signale`() {
        val session = session(listOf(copieDe(jarAndroid(), "android"), copieDe(jarStdlib(), "stdlib")))
        val source =
            """
            import android.widget.TextView

            fun demo(tv: TextView) {
              val a = tv.membreVraimentAbsentDuBytecode
            }
            """.trimIndent()
        val uri = "file:///extensions-recepteur-nul-controle.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le vrai negatif de controle : la chaine TextView se ferme, aucune
        // extension de ce nom n'existe — l'absence se PROUVE (le harnais ne
        // valide pas un « tout muet »).
        assertEquals(1, recus.size, "un membre réellement absent reste signalé : $recus")
        assertTrue(recus.single().contains("membreVraimentAbsentDuBytecode"))
        session.close()
    }
}

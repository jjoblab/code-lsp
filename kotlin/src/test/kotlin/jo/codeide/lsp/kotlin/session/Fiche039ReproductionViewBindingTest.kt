package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 039 — ReproductionViewBindingTest : REPRODUCTION de la forme
 * ViewBinding au niveau MOTEUR (ADR 0219) — le récepteur est une PROPRIÉTÉ
 * TYPAÉE (`private lateinit var binding: ActivityMainBinding`) : la
 * complétion membre doit offrir root/textView/inflate/bind du groupe
 * synthétique K5, TOUJOURS dans le sous-paquet `databinding` (convention
 * AGP réelle — le template Android Studio « Empty Views Activity »
 * importe com.ex.app.databinding.ActivityMainBinding) — 1 comportement du
 * cahier docs/cahier-specifications-kotlin/.
 *
 * Un PROJET ANDROID FACTICE sur disque (manifeste + res/layout) nourrit
 * les synthétiques du moteur ; la FUSION AVEC COUPURE les ajoute au
 * modèle des preuves tant qu'aucune classe réelle du même FQCN ne les
 * efface.
 */
class Fiche039ReproductionViewBindingTest {

    /** La racine temporaire du projet Android factice. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Sème src/main/java + res/layout + manifeste du paquet com.ex.app. */
    private fun semer(): Path {
        val srcMain = racine.resolve("src/main")
        Files.createDirectories(srcMain.resolve("java"))
        Files.createDirectories(srcMain.resolve("res/layout"))
        Files.writeString(
            srcMain.resolve("AndroidManifest.xml"),
            """<?xml version="1.0"?><manifest package="com.ex.app" />""",
        )
        Files.writeString(
            srcMain.resolve("res/layout/activity_main.xml"),
            """
            <?xml version="1.0"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:id="@+id/textView"/>
            </LinearLayout>
            """.trimIndent(),
        )
        return srcMain.resolve("java")
    }

    /** Session branchée sur le projet factice + stdlib (guichet attendu). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(jarStdlib()).mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f039:$i:${jar.name}")
            },
            racinesSources = listOf(semer().toAbsolutePath().toString()),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 120) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    @Test
    fun `viewbinding---propriete-typée,-completion-membre-du-groupe-K5`() {
        val session = session()
        val source =
            """
            package com.ex.app

            import com.ex.app.databinding.ActivityMainBinding

            class MainActivity {
              private lateinit var binding: ActivityMainBinding

              fun demo(): Int = binding.textView.id + binding.root.width + binding.inflate(0).textView.id
            }
            """.trimIndent()
        val uri = "file:///com/ex/app/MainActivity.kt"
        session.ouvrir(uri, source)

        // La complétion membre du récepteur TYPAÉ (propriété du groupe
        // synthétique K5) offre root/textView/inflate/bind.
        val apresBinding = source.indexOf("binding.textView") + "binding.".length
        val propositions = session.completion(uri, apresBinding).map { it.libelle }
        assertTrue(
            "root" in propositions,
            "root doit s'offrir — obtenu : ${propositions.take(16)}",
        )
        assertTrue("textView" in propositions, "textView doit s'offrir (champ de l'@+id du layout)")
        assertTrue("inflate" in propositions, "inflate doit s'offrir (membre du groupe K5)")
        assertTrue("bind" in propositions, "bind doit s'offrir (membre du groupe K5)")
        session.close()
    }
}

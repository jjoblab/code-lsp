package jo.codeide.lsp.kotlin.session

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin

/**
 * Fiche 051 — SessionLangageKotlinSignatureTest : le contrat de session —
 * AIDE À LA SIGNATURE (S1, plan de stabilisation LSP §3.2) : par le SPI
 * (chemin de production D6), moteur réel, candidates du PROJET (fonctions,
 * constructeurs, membres vifs), candidates du CLASSPATH (index Kotlin —
 * jar stdlib RÉEL ; méthode JVM du récepteur qualifié — même montage que
 * le survol qualifié K3) (12 comportements du cahier
 * docs/cahier-specifications-kotlin/).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche051SessionLangageKotlinSignatureTest {

    /** Le dossier des fixtures (racines et jar du récepteur qualifié). */
    @TempDir
    lateinit var dossier: File

    /** Une session sans classpath (les candidates du projet seul). */
    private fun session(): SessionLangageKotlinDocuments =
        ServiceMoteurLangageKotlin.unique.creerSession() as SessionLangageKotlinDocuments

    /** Le jar kotlin-stdlib du classpath de test (index RÉEL). */
    private fun entreeStdlib(): EntreeClasspathKotlin =
        EntreeClasspathKotlin(
            KindEntreeKotlin.JAR,
            File(
                kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                    .codeSource.location.toURI(),
            ).path,
            "f051:stdlib",
        )

    /**
     * Une session branchée sur le jar du RÉCEPTEUR QUALIFIÉ (TextView avec
     * sa méthode setText — même montage que le survol qualifié K3 : la vue
     * JVM lit les ENTRÉES, jamais une vue factice).
     */
    private fun sessionQualifiee(): SessionLangageKotlinDocuments {
        val jar = OutilsJarFixture.Batisseur()
            .classe(
                "android.widget.TextView", "android.view.View",
                OutilsJarFixture.methode("setText", "(Ljava/lang/CharSequence;)V"),
            )
            .ecrire(dossier.toPath(), "f051-qualifie.jar")
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f051:qualifie")),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend que l'index de la session soit couvert (polling, miroir fiche 032). */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 90) {
        val fin = System.nanoTime() + secondes * 1_000_000_000
        while (System.nanoTime() < fin) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
    }

    // ── Candidates du projet ───────────────────────────────────────────

    @Test
    fun `signature---fonction-projet,-curseur-après-parenthèse`() {
        val s = session()
        try {
            val source =
                """
                fun creer(nom: String, taille: Int): Int = 1
                fun demo() { creer() }
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///fonction.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("creer()") + "creer(".length))
            assertEquals(1, aide.signatures.size)
            assertEquals("creer(nom: String, taille: Int): Int", aide.signatures.single().etiquette)
            assertEquals(listOf("nom: String", "taille: Int"), aide.signatures.single().parametres)
            assertEquals(0, aide.signatureActive)
            assertEquals(0, aide.parametreActif)
        } finally {
            s.close()
        }
    }

    @Test
    fun `signature---paramètre-actif-après-la-virgule`() {
        val s = session()
        try {
            val source =
                """
                fun creer(nom: String, taille: Int): Int = 1
                fun demo() { creer("a", ) }
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///virgule.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("\"a\", ") + 4))
            assertEquals(1, aide.parametreActif)
        } finally {
            s.close()
        }
    }

    @Test
    fun `signature---surcharges,-signature-active-=-arité-compatible`() {
        val s = session()
        try {
            val source =
                """
                fun f(x: Int): Int = 1
                fun f(x: Int, y: Int): Int = 1
                fun demo() { f(1, 2) }
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///surcharge.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("f(1, 2)") + 5))
            assertEquals(2, aide.signatures.size)
            assertEquals("f(x: Int, y: Int): Int", aide.signatures[aide.signatureActive].etiquette)
            assertEquals(1, aide.parametreActif)
        } finally {
            s.close()
        }
    }

    @Test
    fun `signature---paramètres-par-défaut-élargissent-l'arité-légale`() {
        val s = session()
        try {
            val source =
                """
                fun saluer(nom: String, ponctuation: String = "!"): String = nom
                fun demo() { saluer("a") }
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///defaut.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("saluer(\"a\")") + "saluer(".length))
            assertEquals(0, aide.signatureActive)
        } finally {
            s.close()
        }
    }

    @Test
    fun `signature---constructeur-primaire-de-classe-projet`() {
        val s = session()
        try {
            val source =
                """
                class Point(x: Int, y: Int)
                fun demo() { Point(1, ) }
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///constructeur.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("Point(1, )") + "Point(1, ".length))
            assertEquals("Point(x: Int, y: Int)", aide.signatures.single().etiquette)
            assertEquals(1, aide.parametreActif)
        } finally {
            s.close()
        }
    }

    @Test
    fun `signature---membre-vif-de-la-classe-englobante`() {
        val s = session()
        try {
            val source =
                """
                class Boite {
                    fun ranger(contenu: String): Boolean = true
                    fun demo() { ranger() }
                }
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///membre.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("ranger()") + "ranger(".length))
            assertEquals("ranger(contenu: String): Boolean", aide.signatures.single().etiquette)
        } finally {
            s.close()
        }
    }

    // ── Lambda final ───────────────────────────────────────────────────

    @Test
    fun `signature---lambda-final-compte-pour-l'argument-suivant`() {
        val s = session()
        try {
            val source =
                """
                fun repeter(times: Int, action: () -> Unit): Unit = Unit
                fun demo() { repeter(2) { } }
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///lambda.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("{ }") + 1))
            assertEquals(1, aide.parametreActif)
            assertEquals(0, aide.signatureActive)
        } finally {
            s.close()
        }
    }

    // ── Candidates du classpath ────────────────────────────────────────

    @Test
    fun `signature---appelable-de-premier-niveau-du-stdlib-(index-réel)`() {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(entreeStdlib()),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val s = moteur.creerSession() as SessionLangageKotlinDocuments
        try {
            s.definirClasspath(classpath)
            attendreCouvert(s)
            val source = "fun demo() { listOf(1, ) }\n"
            val uri = "file:///stdlib.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("listOf(1, ") + "listOf(1, ".length))
            assertTrue(aide.signatures.isNotEmpty())
            assertTrue(aide.signatures.all { it.etiquette.startsWith("listOf(") })
            assertTrue(aide.parametreActif >= 1)
        } finally {
            s.close()
        }
    }

    @Test
    fun `signature---méthode-JVM-du-récepteur-qualifié-(vue-factice)`() {
        val s = sessionQualifiee()
        try {
            val source =
                """
                import android.widget.TextView
                fun demo() {
                    val v: TextView = creer()
                    v.setText()
                }
                fun creer(): TextView = creer()
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///qualifie.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("v.setText()") + "v.setText(".length))
            assertEquals("setText(CharSequence): Unit", aide.signatures.single().etiquette)
            assertEquals(listOf("CharSequence"), aide.signatures.single().parametres)
        } finally {
            s.close()
        }
    }

    // ── Imbrication et silence ─────────────────────────────────────────

    @Test
    fun `signature---appel-niché,-le-plus-profond-gagne`() {
        val s = session()
        try {
            val source =
                """
                fun exterieur(a: Int): Int = a
                fun interieur(b: String): String = b
                fun demo() { exterieur(interieur()) }
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///niche.kt"
            s.ouvrir(uri, source)
            val aide = assertNotNull(s.signature(uri, source.indexOf("interieur()") + "interieur(".length))
            assertEquals("interieur(b: String): String", aide.signatures.single().etiquette)
        } finally {
            s.close()
        }
    }

    @Test
    fun `signature---curseur-après-l'appel-fermé,-silence`() {
        val s = session()
        try {
            val source =
                """
                fun creer(nom: String): Int = 1
                fun demo() { creer("a") }
                """.trimIndent().replace("    ", "  ")
            val uri = "file:///ferme.kt"
            s.ouvrir(uri, source)
            assertNull(s.signature(uri, source.indexOf("creer(\"a\")") + "creer(\"a\")".length))
        } finally {
            s.close()
        }
    }

    @Test
    fun `signature---hors-appel,-document-inconnu,-session-fermée`() {
        val s = session()
        val source = "val x = 1\n"
        val uri = "file:///hors.kt"
        s.ouvrir(uri, source)
        assertNull(s.signature(uri, source.indexOf("x") + 1))
        assertNull(s.signature("file:///jamais-vu.kt", 0))
        s.close()
        assertNull(s.signature(uri, 0))
    }
}

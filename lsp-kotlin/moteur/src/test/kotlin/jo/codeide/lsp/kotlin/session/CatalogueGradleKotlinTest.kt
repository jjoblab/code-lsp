// CodeIDE — tests du CATALOGUE de versions Gradle (module :kotlin, vague
// V-G — ADR 0130 : le DISQUE est la source de vérité, jamais la sync —
// KC-12 G4/G5/G6/G7). Code NEUF jjoblab (clean-room — comportements :
// lecture TOML directe (PAS les accesseurs générés), alias exacts,
// imbrication par groupe, navigation kts→toml, clé inconnue = preuve
// d'absence, catalogue absent = silence — jamais un appel réseau, jamais
// un diagnostic de version (indémontrable sans sync)).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * La vague V-G — le catalogue de versions Gradle (ADR 0130 Option 1 :
 * lecture disque sans sync) : accesseurs `libs.…` par lecture TOML
 * directe, navigation vers l'entrée, diagnostics bornés.
 */
class CatalogueGradleKotlinTest {

    /** La racine temporaire du projet Gradle factice. */
    @org.junit.jupiter.api.io.TempDir
    lateinit var racineProjet: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "vg:stdlib"),
            ),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        val limite = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return session
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /** Écrit le catalogue standard du projet factice. */
    private fun ecrireCatalogue() {
        Files.createDirectories(racineProjet.resolve("gradle"))
        Files.writeString(
            racineProjet.resolve("gradle").resolve("libs.versions.toml"),
            """
            [versions]
            core = "1.15.0"

            [libraries]
            androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core" }
            androidx-appcompat = { group = "androidx.appcompat", name = "appcompat" }
            junit = { group = "junit", name = "junit" }

            [plugins]
            android-application = { id = "com.android.application", version = "8.7.0" }
            """.trimIndent() + "\n",
        )
    }

    // ── G4 — accesseurs libs.… par lecture TOML directe ─────────────

    @Test
    fun `g4---segments-suivants-par-groupe,-alias-exacts-du-toml`() {
        ecrireCatalogue()
        val session = session()
        val source = "dependencies { implementation(libs.) }\n"
        val uri = racineProjet.resolve("build.gradle.kts").toUri().toString()
        Files.writeString(racineProjet.resolve("build.gradle.kts"), source)
        session.ouvrir(uri, source)
        // `libs.|` : les groupes et alias de premier niveau.
        val reponse = session.completionArrangee(uri, source.indexOf("libs.") + "libs.".length)
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue("androidx" in libelles, "le groupe androidx devait s'offrir : $libelles")
        assertTrue("junit" in libelles, "le segment junit devait s'offrir : $libelles")
        assertTrue("plugins" in libelles, "le groupe plugins devait s'offrir : $libelles")
        session.close()
    }

    @Test
    fun `g4---imbrication-par-groupe-libs-androidx-point`() {
        ecrireCatalogue()
        val session = session()
        val source = "dependencies { implementation(libs.androidx.) }\n"
        val uri = racineProjet.resolve("build.gradle.kts").toUri().toString()
        Files.writeString(racineProjet.resolve("build.gradle.kts"), source)
        session.ouvrir(uri, source)
        val reponse = session.completionArrangee(uri, source.indexOf("androidx.") + "androidx.".length)
        val libelles = reponse.propositions.map { it.libelle }
        // Les ACCESSEURS Gradle : l'alias `androidx-core-ktx` se frappe
        // `libs.androidx.core.ktx` — `libs.androidx.|` propose `core`
        // (le segment suivant du chemin normalisé).
        assertTrue("core" in libelles, "le segment core devait s'offrir : $libelles")
        assertTrue("appcompat" in libelles, "le segment appcompat devait s'offrir : $libelles")
        assertTrue("junit" !in libelles, "junit n'est pas dans le groupe androidx")
        session.close()
    }

    @Test
    fun `g4-bornes---catalogue-absent---libs-point-non-complété-(-silence-)`() {
        val session = session()
        // PAS de catalogue dans la racine temporaire.
        val source = "dependencies { implementation(libs.) }\n"
        val uri = racineProjet.resolve("build.gradle.kts").toUri().toString()
        Files.writeString(racineProjet.resolve("build.gradle.kts"), source)
        session.ouvrir(uri, source)
        val reponse = session.completionArrangee(uri, source.indexOf("libs.") + "libs.".length)
        assertTrue(
            reponse.propositions.none { it.detail == "catalogue de versions" },
            "catalogue absent → libs. NON complété (silence, G4)",
        )
        session.close()
    }

    // ── G5 — navigation kts → entrée du catalogue ───────────────────

    @Test
    fun `g5---definition-libs-androidx-core-ktx---ligne-de-l'entrée-toml`() {
        ecrireCatalogue()
        val session = session()
        val source = "dependencies { implementation(libs.androidx.core.ktx) }\n"
        val uri = racineProjet.resolve("build.gradle.kts").toUri().toString()
        Files.writeString(racineProjet.resolve("build.gradle.kts"), source)
        session.ouvrir(uri, source)
        val definitions = session.definition(uri, source.indexOf("core") + 1)
        assertEquals(1, definitions.size, "une définition : l'entrée du catalogue")
        val cible = definitions.single()
        assertTrue(
            cible.uri.endsWith("libs.versions.toml"),
            "la cible est le TOML : ${cible.uri}",
        )
        // G5 : l'entrée androidx-core-ktx porte version.ref = "core" —
        // la navigation saute à l'entrée [versions] pointée (core, ligne
        // 1 du TOML : [versions] ligne 0, core ligne 1).
        assertEquals(1, cible.plage.debut, "version.ref → saut à l'entrée [versions] pointée (G5)")
        session.close()
    }

    // ── G6 — diagnostics bornés : clé inconnue = preuve d'absence ──

    @Test
    fun `g6---clé-inconnue-signalée,-clés-résolues-muettes`() {
        ecrireCatalogue()
        val session = session()
        val source =
            "dependencies {\n" +
                "    implementation(libs.androidx.core.ktx)\n" +
                "    implementation(libs.pas.dans.le.catalogue)\n" +
                "}\n"
        val uri = racineProjet.resolve("build.gradle.kts").toUri().toString()
        Files.writeString(racineProjet.resolve("build.gradle.kts"), source)
        session.ouvrir(uri, source)
        val cleInconnue = session.diagnostics(uri)
            .filter { it.code == CodeDiagnosticKotlin.CLE_CATALOGUE_INCONNUE }
        assertEquals(1, cleInconnue.size, "UNE clé inconnue signalée (preuve par parse)")
        assertTrue(
            "pas.dans.le.catalogue" in cleInconnue.single().message,
            "le message porte la clé absente",
        )
        session.close()
    }

    @Test
    fun `g6g7---catalogue-absent---aucun-diagnostic,-jamais-de-diagnostic-de-version`() {
        val session = session()
        val source = "dependencies { implementation(libs.nimporte.quoi) }\n"
        val uri = racineProjet.resolve("build.gradle.kts").toUri().toString()
        Files.writeString(racineProjet.resolve("build.gradle.kts"), source)
        session.ouvrir(uri, source)
        assertTrue(
            session.diagnostics(uri).none { it.code == CodeDiagnosticKotlin.CLE_CATALOGUE_INCONNUE },
            "catalogue absent → AUCUN diagnostic de clé (le silence n'est jamais une preuve)",
        )
        session.close()
    }

    // ── Non-régression : le template Gradle sans faux positif ───────

    @Test
    fun `non-régression---build-gradle-kts-du-template,-aucun-faux-positif`() {
        ecrireCatalogue()
        val session = session()
        val source =
            """
            plugins {
                alias(libs.plugins.android.application)
            }

            android {
                namespace = "com.exemple.app"
            }

            dependencies {
                implementation(libs.androidx.core.ktx)
            }
            """.trimIndent() + "\n"
        val uri = racineProjet.resolve("build.gradle.kts").toUri().toString()
        Files.writeString(racineProjet.resolve("build.gradle.kts"), source)
        session.ouvrir(uri, source)
        val cleInconnue = session.diagnostics(uri)
            .filter { it.code == CodeDiagnosticKotlin.CLE_CATALOGUE_INCONNUE }
        assertTrue(
            cleInconnue.isEmpty(),
            "le template résolu par le catalogue ne porte AUCUNE clé inconnue : $cleInconnue",
        )
        session.close()
    }

    // ── Le parseur TOML tolérant ────────────────────────────────────

    @Test
    fun `toml-tolérant---entrées-hostiles-ignorées,-jamais-de-cascade`() {
        Files.createDirectories(racineProjet.resolve("gradle"))
        Files.writeString(
            racineProjet.resolve("gradle").resolve("libs.versions.toml"),
            "[libraries]\n" +
                "valide = { group = \"g\", name = \"n\" }\n" +
                "ligne hostile sans egale\n" +
                "= pas d alias\n" +
                "[section inconnue]\n" +
                "autre = \"x\"\n" +
                "encore-valide = \"y\"\n",
        )
        val entrees = CatalogueGradle.lire(racineProjet)
        val libelles = entrees.map { it.alias }
        assertTrue("valide" in libelles, "l'entrée saine survit")
        assertTrue(
            entrees.none { it.alias == "autre" },
            "les entrées des sections inconnues ne comptent pas : $libelles",
        )
        assertTrue(
            entrees.none { it.alias == "encore-valide" },
            "la section inconnue ABSORBE ses entrées — le parseur reprend à la section CONNUE suivante : $libelles",
        )
    }
}

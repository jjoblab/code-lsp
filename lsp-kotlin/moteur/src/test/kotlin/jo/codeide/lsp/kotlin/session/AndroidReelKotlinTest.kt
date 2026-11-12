// CodeIDE — tests de la vague ANDROID RÉEL (module :kotlin, vague V-E —
// ADR 0127 Option 1 : consommer le socle partagé + lever H2 ; ADR 0128
// palier 1 : Compose prouvé par annotation ÉCRITE). Code NEUF jjoblab
// (clean-room — comportements : le scan DÉDIÉ lit `build/generated`
// post-build (priorité généré réel > light, miroir Java 0021 R1), la
// convention muette `estNomGenere` est BORNÉE PAR PREUVE, le supertype
// `Parcelable` est INJECTÉ pour `@Parcelize`, le namespace du build
// PRIME le paquet du manifest).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La vague V-E — Android réel (ADR 0127/0128/0129) : le scan des racines
 * GÉNÉRÉES, la coupure des synthétiques, les plugins déclaratifs
 * (Compose palier 1, Parcelize) et le namespace du build.
 */
class AndroidReelKotlinTest {

    /** La racine temporaire du projet Android factice. */
    @org.junit.jupiter.api.io.TempDir
    lateinit var racineProjet: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une session branchée sur le stdlib, racines = le module factice. */
    private fun session(vararg racines: Path): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val listeRacines = racines.map { it.toString() }
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "ve:stdlib"),
            ),
            racinesSources = listeRacines,
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        session.rafraichirSources()
        val limite = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return session
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /** Un module Android factice : src/main/{kotlin,res} + manifest + build.gradle.kts. */
    private fun moduleFactice(namespace: String? = "com.exemple.app", manifestPaquet: String? = "com.exemple.app"): Path {
        val module = racineProjet.resolve("app")
        val srcMain = module.resolve("src/main")
        Files.createDirectories(srcMain.resolve("kotlin"))
        Files.createDirectories(srcMain.resolve("res/layout"))
        if (manifestPaquet != null) {
            Files.writeString(
                srcMain.resolve("AndroidManifest.xml"),
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$manifestPaquet\" />",
            )
        }
        Files.writeString(srcMain.resolve("res/layout/activity_main.xml"), "<LinearLayout />")
        if (namespace != null) {
            Files.writeString(module.resolve("build.gradle.kts"), "android { namespace = \"$namespace\" }\n")
        }
        return module.resolve("src/main/kotlin")
    }

    // ── V-E2 : le scan dédié des racines générées ────────────────────

    @Test
    fun `ve2---le-nom-généré-lu-du-disque-résout-(-room-impl-,-hilt-)`() {
        val module = moduleFactice()
        // Le code GÉNÉRÉ post-build : Room `MonDao_Impl` dans build/generated.
        val genere = racineProjet.resolve("app/build/generated/ksp/debug/java")
        Files.createDirectories(genere)
        Files.writeString(
            genere.resolve("MonDao_Impl.kt"),
            "package com.exemple.app\n" +
                "\n" +
                "class MonDao_Impl : MonDao {\n" +
                "    override fun lire(): Int = 1\n" +
                "}\n",
        )
        // Le code SOURCE : un usage du nom généré.
        val source =
            "import com.exemple.app.MonDao\n" +
                "\n" +
                "fun demo(dao: MonDao): Int {\n" +
                "    val impl = MonDao_Impl(dao)\n" +
                "    return impl.lire()\n" +
                "}\n"
        val uri = module.resolve("Demo.kt").toUri().toString()
        Files.writeString(module.resolve("Demo.kt"), source)
        val session = session(module)
        session.ouvrir(uri, source)
        // Le nom `MonDao_Impl` RÉSOUT (sa source générée est LUE par le
        // scan dédié) : AUCUN diagnostic d'introuvable sur ce nom.
        val erreurs = session.diagnostics(uri)
            .filter { it.severite == SeveriteKotlin.ERREUR }
            .filter { "MonDao_Impl" in it.message || "impl" in it.message }
        assertTrue(
            erreurs.none { "MonDao_Impl" in it.message },
            "le nom généré MonDao_Impl (source LUE post-build) résout : $erreurs",
        )
        session.close()
    }

    @Test
    fun `ve2---priorité-généré-réel---le-groupe-synthétique-homonyme-effacé`() {
        val module = moduleFactice(namespace = "com.exemple.app")
        // Une classe R GÉNÉRÉE réelle (post-build) efface le groupe
        // synthétique R homonyme (coupure 0021 R1).
        val genere = racineProjet.resolve("app/build/generated/debug/java")
        Files.createDirectories(genere)
        Files.writeString(
            genere.resolve("R.kt"),
            "package com.exemple.app\n" +
                "\n" +
                "class R {\n" +
                "    class layout\n" +
                "}\n",
        )
        val session = session(module)
        // Le groupe synthétique R est EFFACÉ : la contribution générée
        // porte le FQCN com.exemple.app.R (la coupure s'applique).
        val contributions = session.contributionsAuConsult("file:///aucun.kt")
        val fqcnR = contributions.flatMap { it.types }.filter { it.fqcn == "com.exemple.app.R" }
        assertEquals(1, fqcnR.size, "UNE seule classe R : la générée réelle (le synthétique est effacé)")
        // Le membre `layout` de la générée EST servi (pas les familles
        // synthétiques éventuelles du même nom).
        assertTrue(fqcnR.single().membres.any { it.nom == "layout" }, "le membre de la classe GÉNÉRÉE est servi")
        session.close()
    }

    @Test
    fun `ve2---sans-build---le-synthétique-survit-(-atout-avant-build-)`() {
        val module = moduleFactice()
        // AUCUN build : le groupe synthétique R survit (l'atout « avant
        // build » — la coupure ne s'applique JAMAIS sans preuve réelle).
        val session = session(module)
        val contributions = session.contributionsAuConsult("file:///aucun.kt")
        val fqcnR = contributions.flatMap { it.types }.filter { it.fqcn == "com.exemple.app.R" }
        assertTrue(fqcnR.isNotEmpty(), "sans build, le synthétique R survit (avant-build préservé)")
        session.close()
    }

    // ── V-E3 : les plugins de compilateur DÉCLARATIFS ────────────────

    @Test
    fun `ve3---parcelize---le-supertype-parcelable-est-injecté`() {
        val session = session()
        // DEUX documents OUVERTS : l'interface au paquet canonique
        // `android.os` (la cible de l'injection) et la classe @Parcelize
        // au paquet `com.exemple` (les documents ouverts nourrissent le
        // modèle de preuves — miroir des contributions du scan).
        val sourceInterface =
            "package android.os\n" +
                "\n" +
                "interface Parcelable\n"
        session.ouvrir("file:///parcelable.kt", sourceInterface)
        val source =
            "package com.exemple\n" +
                "\n" +
                "@Parcelize\n" +
                "class Donnee(val nom: String)\n"
        val uri = "file:///donnee.kt"
        session.ouvrir(uri, source)
        // La preuve : la hiérarchie de Donnee porte le supertype INJECTÉ
        // (résolu vers l'interface du document frère).
        val hierarchie = session.preparerHierarchieTypes(uri, source.indexOf("Donnee") + 1)
        assertNotNull(hierarchie, "la préparation sur Donnee devait rendre l'item")
        val supertypes = session.supertypesHierarchie(uri, hierarchie!!)
        assertTrue(
            supertypes.any { it.nom == "Parcelable" },
            "le supertype Parcelable est INJECTÉ pour @Parcelize (modélisation déclarative) : ${supertypes.map { it.nom }}",
        )
        session.close()
    }

    @Test
    fun `ve3---composable-et-suspend-dans-le-survol-(-annotation-écrite-)`() {
        val session = session()
        val source =
            "import androidx.compose.runtime.Composable\n" +
                "\n" +
                "@Composable\n" +
                "fun Ecran() { }\n" +
                "\n" +
                "suspend fun charger() { }\n"
        val uri = "file:///compose.kt"
        session.ouvrir(uri, source)
        // Le survol d'Ecran porte @Composable (l'annotation ÉCRITE).
        val survolEcran = session.survol(uri, source.indexOf("Ecran") + 1)
        assertNotNull(survolEcran, "le survol d'Ecran devait exister")
        assertTrue(
            "@Composable" in survolEcran!!.signature,
            "la signature porte @Composable (contexte du plugin prouvé) : ${survolEcran.signature}",
        )
        // Le survol de charger porte suspend.
        val survolCharger = session.survol(uri, source.indexOf("charger") + 1)
        assertNotNull(survolCharger, "le survol de charger devait exister")
        assertTrue(
            "suspend" in survolCharger!!.signature,
            "la signature porte suspend : ${survolCharger.signature}",
        )
        session.close()
    }

    // ── V-E1 : le namespace du build PRIME le paquet du manifest ────

    @Test
    fun `ve1---le-namespace-du-build-prime-le-paquet-du-manifest`() {
        // Le manifest porte l'ANCIEN paquet, le build porte le namespace
        // NOUVEAU (AGP ≥ 8) : le groupe synthétique vit au namespace.
        val module = moduleFactice(namespace = "com.exemple.nouveau", manifestPaquet = "com.exemple.ancien")
        val session = session(module)
        val contributions = session.contributionsAuConsult("file:///aucun.kt")
        val fqcnR = contributions.flatMap { it.types }.filter { it.fqcn.endsWith(".R") }
        assertTrue(
            fqcnR.any { it.fqcn == "com.exemple.nouveau.R" },
            "le groupe R vit au NAMESPACE du build (AGP ≥ 8) : ${fqcnR.map { it.fqcn }}",
        )
        assertTrue(
            fqcnR.none { it.fqcn == "com.exemple.ancien.R" },
            "le paquet du manifest NE PRIME PAS le namespace",
        )
        session.close()
    }

    @Test
    fun `ve1---sans-namespace---le-paquet-du-manifest-reprend`() {
        val module = moduleFactice(namespace = null, manifestPaquet = "com.exemple.manifest")
        val session = session(module)
        val contributions = session.contributionsAuConsult("file:///aucun.kt")
        val fqcnR = contributions.flatMap { it.types }.filter { it.fqcn.endsWith(".R") }
        assertTrue(
            fqcnR.any { it.fqcn == "com.exemple.manifest.R" },
            "sans namespace écrit, le paquet du MANIFEST reprend (AGP < 8) : ${fqcnR.map { it.fqcn }}",
        )
        session.close()
    }
}

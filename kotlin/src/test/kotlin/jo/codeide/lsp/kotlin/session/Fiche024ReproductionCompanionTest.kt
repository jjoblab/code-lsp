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
 * Fiche 024 — ReproductionCompanionTest : REPRODUCTION des COMPANION
 * OBJECTS (ADR 0222) — le quatrième monde Kotlin pour la parité Android
 * Studio — 7 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * Les compagnons PROJET (anonymes et nommés) vivent dans le document :
 * `Boite.creer()` (membres du compagnon accessibles par le NOM de la
 * classe — portée STATIQUE du conteneur), `Boite.Fabrique.creer()` (la
 * marche imbriquée), le `this` implicite du corps. Les compagnons
 * CLASSPATH (`Regex.escape`, `UInt.MAX_VALUE`) se lisent du jar
 * kotlin-stdlib RÉEL posé en entrée : `kotlin.text.Regex$Companion` porte
 * `escape`, le champ `MAX_VALUE` est typé par son descripteur.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche024ReproductionCompanionTest {

    /** La racine temporaire de la copie du jar. */
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
        val copie = Files.createTempFile(racine, "f024-$suffixe", ".jar").toFile()
        copie.writeBytes(jar.readBytes())
        return copie
    }

    /** Session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments = sessionSur(listOf(copieDe(jarStdlib(), "stdlib")))

    /**
     * Le jar de fixture du compagnon AUTOCONTENU : `exo.lib.Boussole` et
     * son compagnon `exo.lib.Boussole$Companion` (une méthode d'instance
     * `echappe`, un champ STATIQUE `SEUIL` — la structure exacte d'un
     * compagnon compilé), SANS supertype externe — la chaîne se ferme sur
     * le terminal connu Object, la preuve de la garde D3 s'exerce VRAIMENT
     * (le stdlib réel ne s'y prête pas : `Regex` implémente
     * `java.io.Serializable`, `UInt` implémente `java.lang.Comparable` —
     * des maillons absents des jars de test, la chaîne resterait ouverte).
     */
    private fun jarCompagnonAutocontenu(): java.io.File =
        OutilsJarFixture.Batisseur()
            .classe(
                "exo.lib.Boussole", null,
                OutilsJarFixture.champStatique("Companion", "Lexo/lib/Boussole\$Companion;"),
            )
            .classe(
                "exo.lib.Boussole\$Companion", null,
                OutilsJarFixture.methode("echappe", "(Ljava/lang/String;)Ljava/lang/String;"),
                OutilsJarFixture.champStatique("SEUIL", "I"),
            )
            .ecrire(racine, "fixture-compagnon.jar")

    /** Session branchée sur les jars donnés (guichet attendu par sondage borné). */
    private fun sessionSur(jars: List<java.io.File>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f024:$i:${jar.name}")
            },
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Session SANS classpath (les compagnons projet seuls — guichet muet attendu). */
    private fun sessionSansClasspath(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(entrees = emptyList(), racinesSources = emptyList())
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
    fun `companion-anonyme---Boite-point-creer(),-zero-faux-positif-et-capacites`() {
        val session = sessionSansClasspath()
        val source =
            """
            class Boite(val contenu: String) {
              companion object {
                fun creer(): Boite = Boite("neuve")
              }
            }

            fun demo(): Boite = Boite.creer()
            """.trimIndent()
        val uri = "file:///companion-anonyme.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("creer") },
            "Boite.creer() (compagnon anonyme) muet : $recus",
        )
        // Les CAPACITÉS : la complétion après `Boite.` propose `creer`
        // (portée STATIQUE du conteneur — miroir Android Studio).
        val point = source.indexOf("Boite.creer()") + "Boite.".length
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("creer" in propositions,
            "la complétion de Boite. propose creer : $propositions",
        )
        session.close()
    }

    @Test
    fun `companion-nomme---membres-sous-la-classe-ET-sous-l-objet`() {
        val session = sessionSansClasspath()
        val source =
            """
            class Boite(val contenu: String) {
              companion object Fabrique {
                fun creer(): Boite = Boite("neuve")
              }
            }

            fun demo(): Boite {
              val a = Boite.creer()
              val b = Boite.Fabrique.creer()
              return a
            }
            """.trimIndent()
        val uri = "file:///companion-nomme.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Les membres du compagnon nommé sont atteints sous la CLASSE
        // (`Boite.creer()`) ET sous l'OBJET (`Boite.Fabrique.creer()`).
        assertTrue(recus.none { it.contains("creer") },
            "Boite.creer() ET Boite.Fabrique.creer() muets : $recus",
        )
        session.close()
    }

    @Test
    fun `corps-du-compagnon---this-implicite-des-membres`() {
        val session = sessionSansClasspath()
        val source =
            """
            class Boite(val contenu: String) {
              companion object {
                const val CLE = "boite"

                fun creer(): Boite = Boite(nom())

                fun nom(): String = CLE
              }
            }
            """.trimIndent()
        val uri = "file:///corps-compagnon.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le CORPS du compagnon : `nom()` et `CLE` nus (le this implicite
        // du compagnon) — jamais signalés.
        assertTrue(recus.none { it.contains("nom") || it.contains("CLE") },
            "this implicite du compagnon muet : $recus",
        )
        session.close()
    }

    @Test
    fun `objet-simple-non-regression---ADR-0220-inchangée`() {
        val session = sessionSansClasspath()
        val source =
            """
            object Util {
              fun aide(): Int = 1
            }

            fun demo(): Int = Util.aide()
            """.trimIndent()
        val uri = "file:///objet-simple.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("aide") },
            "Util.aide() (objet simple, ADR 0220) muet : $recus",
        )
        session.close()
    }

    @Test
    fun `companion-du-classpath---Regex-point-escape-resolve-et-s-offre`() {
        val session = session()
        val source =
            """
            fun demo(): String = Regex.escape("a.b")
            """.trimIndent()
        val uri = "file:///regex-escape.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // `escape` vit sur kotlin.text.Regex$Companion (méthode d'instance
        // du compagnon) : le sélecteur sur la CLASSE résout.
        assertTrue(recus.none { it.contains("escape") },
            "Regex.escape (compagnon du classpath) muet : $recus",
        )
        // ET S'OFFRE : la complétion après `Regex.` propose `escape`.
        val point = source.indexOf("Regex.escape") + "Regex.".length
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("escape" in propositions,
            "la complétion de Regex. propose escape : $propositions",
        )
        session.close()
    }

    @Test
    fun `companion-du-classpath---UInt-point-MAX_VALUE-champ-typé`() {
        val session = session()
        val source =
            """
            import kotlin.UInt

            fun demo(): Int = UInt.MAX_VALUE
            """.trimIndent()
        val uri = "file:///uint-maxvalue.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("MAX_VALUE") },
            "UInt.MAX_VALUE (champ du classpath) muet : $recus",
        )
        // Le champ est TYPÉ : le survol rend la signature dépouillée du
        // descripteur (`MAX_VALUE: Int` — le champ statique I de
        // kotlin.UInt) avec son propriétaire déclarant.
        val survol = session.survol(uri, source.indexOf("MAX_VALUE"))
        assertTrue(survol != null && survol.signature.contains("MAX_VALUE") && survol.signature.contains("Int"),
            "le survol de UInt.MAX_VALUE rend le champ typé : ${survol?.signature}",
        )
        session.close()
    }

    @Test
    fun `garde-D3---membre-introuvable-du-compagnon-reste-signale`() {
        val session = sessionSur(listOf(jarCompagnonAutocontenu()))
        val source =
            """
            import exo.lib.Boussole

            fun demo(): String = Boussole.echappe("x") + Boussole.MEMBRE_INTROUVABLE
            """.trimIndent()
        val uri = "file:///garde-compagnon.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le compagnon est CLOSE et énumérable : `echappe` (membre du
        // compagnon) est muet, un nom qui n'y vit PAS reste SIGNALÉ (la
        // garde D3 de la fiche — l'émission est attendue, le harnais ne
        // valide pas un « tout muet »).
        assertTrue(recus.none { it.contains("echappe") },
            "Boussole.echappe (membre du compagnon) muet : $recus",
        )
        assertEquals(1, recus.size, "un membre introuvable du compagnon reste signalé : $recus")
        assertTrue(recus.single().contains("MEMBRE_INTROUVABLE"))
        session.close()
    }
}

package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 052 — SuperKotlinTest : le GO-TO « Super » Kotlin (ADR 0210 —
 * `textDocument/superDefinition`, miroir des scénarios
 * `NavigationFacadeTest` du LSP Java) — 10 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * Le montage éprouve le CHEMIN DE PRODUCTION D6 (moteur nourricier réel
 * + session branchée + guichet attendu) : un projet multi-fichiers sur
 * DISQUE (racines sources du scan, même montage que les types L2) — les
 * documents sont ouverts sous leur URI disque RÉELLE (comme la
 * production : le scan exclut les documents ouverts PAR URI).
 *
 * Le cœur éprouvé : les DEUX chemins du picker Super — (1) MEMBRE au
 * caret (déclaration outrepassante, ou usage RÉSOLU par la cible
 * occurrences — un homonyme AMBIGU n'est jamais présumé, borne O3) → le
 * même-nommé dans chaque supertype TRANSITIF projet (`« run  ·  Base »`) ;
 * (2) sinon le type en contexte (référence de type, à défaut la classe
 * englobante) → ses supertypes DIRECTS (`« Simple  ·  pkg »`). Chaque
 * supertype est prouvé PAR FQCN (les imports de SON fichier) — jamais
 * par homonymie ; un supertype sans source projet est ABSENT.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche052SuperKotlinTest {

    /** La racine temporaire des sources (projet multi-fichiers du scan). */
    @TempDir
    lateinit var dossier: Path

    /**
     * Session avec racines sources (scan multi-fichiers actif) — rend la
     * session et les URI DISQUE RÉELLES des fichiers écrits.
     */
    private fun sessionAvecRacines(
        vararg fichiers: Pair<String, String>,
    ): Pair<SessionLangageKotlinDocuments, Map<String, String>> {
        val racine = Files.createDirectories(dossier.resolve("src"))
        val uris = mutableMapOf<String, String>()
        for ((chemin, contenu) in fichiers) {
            val fichier = racine.resolve(chemin)
            Files.createDirectories(fichier.parent)
            Files.writeString(fichier, contenu)
            uris[chemin] = fichier.toUri().toString()
        }
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = emptyList(),
            racinesSources = listOf(racine.toAbsolutePath().toString()),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session to uris
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

    /** Décalage du PREMIER caractère de [occurrence] dans [texte]. */
    private fun decalageDe(texte: String, occurrence: String, numero: Int = 0): Int {
        var index = -1
        repeat(numero + 1) {
            index = texte.indexOf(occurrence, index + 1)
        }
        return index
    }

    // ── Fixtures (miroir NavigationFacadeTest du LSP Java) ──────────

    private val baseKt =
        """
        package demo

        open class Base {
            val compteur: Int = 0

            open fun run() {
            }
        }
        """.trimIndent()

    private val implAKt =
        """
        package demo

        class ImplA : Base() {
            val tag: Int = 1

            override fun run() {
                compteur + 1
            }
        }
        """.trimIndent()

    private val implBKt =
        """
        package demo

        class ImplB : ImplA() {
            override fun run() {
            }
        }
        """.trimIndent()

    private val greeterKt =
        """
        package demo

        interface Greeter {
            fun greet()
        }
        """.trimIndent()

    private val enGreeterKt =
        """
        package demo

        class EnGreeter : Greeter {
            override fun greet() {
            }
        }
        """.trimIndent()

    private val mainKt =
        """
        package demo

        class Main {
            fun use(a: ImplA) {
                a.run()
            }
        }
        """.trimIndent()

    // ── Chemin MEMBRE ────────────────────────────────────────────────

    @Test
    fun `super-sur-déclaration-outrepassante---le-même-nommé-du-supertype-direct`() {
        val (session, uris) =
            sessionAvecRacines(
                "demo/Base.kt" to baseKt,
                "demo/ImplA.kt" to implAKt,
            )
        val uriImpl = uris.getValue("demo/ImplA.kt")
        session.ouvrir(uriImpl, implAKt)
        // Caret sur « run » — la déclaration OUTREPASSANTE dans ImplA.
        val cibles = session.superDefinition(uriImpl, decalageDe(implAKt, "run()"))
        assertEquals(1, cibles.size, "run() de Base : $cibles")
        val cible = cibles.single()
        assertEquals(uris.getValue("demo/Base.kt"), cible.uri)
        assertEquals("run  ·  Base", cible.libelle, "libellé picker (miroir ecj)")
        assertEquals(
            baseKt.indexOf("fun run()") + "fun ".length,
            cible.plage.debut,
            "plage du NOM run dans Base.kt",
        )
        assertEquals(baseKt.indexOf("run()") + "run".length, cible.plage.fin, "fin du nom")
        session.close()
    }

    @Test
    fun `super-sur-déclaration-outrepassante---clôture-TRANSITIVE-(deux-niveaux)`() {
        val (session, uris) =
            sessionAvecRacines(
                "demo/Base.kt" to baseKt,
                "demo/ImplA.kt" to implAKt,
                "demo/ImplB.kt" to implBKt,
            )
        val uriImplB = uris.getValue("demo/ImplB.kt")
        session.ouvrir(uriImplB, implBKt)
        // ImplB.run outrepasse ImplA.run qui outrepasse Base.run — la
        // clôture transitive rend les DEUX (miroir allSupertypes ecj).
        val cibles = session.superDefinition(uriImplB, decalageDe(implBKt, "run()"))
        assertEquals(2, cibles.size, "ImplA puis Base : $cibles")
        assertEquals("run  ·  Base", cibles[0].libelle, "tri par libellé")
        assertEquals("run  ·  ImplA", cibles[1].libelle)
        assertEquals(uris.getValue("demo/Base.kt"), cibles[0].uri)
        assertEquals(uris.getValue("demo/ImplA.kt"), cibles[1].uri)
        session.close()
    }

    @Test
    fun `super-sur-déclaration-d'une-IMPLÉMENTATION-d'interface`() {
        val (session, uris) =
            sessionAvecRacines(
                "demo/Greeter.kt" to greeterKt,
                "demo/EnGreeter.kt" to enGreeterKt,
            )
        val uriEn = uris.getValue("demo/EnGreeter.kt")
        session.ouvrir(uriEn, enGreeterKt)
        val cibles = session.superDefinition(uriEn, decalageDe(enGreeterKt, "greet()"))
        assertEquals(1, cibles.size, "greet() de l'interface : $cibles")
        val cible = cibles.single()
        assertEquals(uris.getValue("demo/Greeter.kt"), cible.uri)
        assertEquals("greet  ·  Greeter", cible.libelle)
        session.close()
    }

    @Test
    fun `super-sur-usage-AMBIGU-d'un-override---vide-honnête-(borne-O3)`() {
        val (session, uris) =
            sessionAvecRacines(
                "demo/Base.kt" to baseKt,
                "demo/ImplA.kt" to implAKt,
                "demo/Main.kt" to mainKt,
            )
        val uriMain = uris.getValue("demo/Main.kt")
        session.ouvrir(uriMain, mainKt)
        // Caret sur l'INVOCATION a.run() : DEUX homonymes projet
        // (Base.run + ImplA.run) — la cible occurrences refuse
        // l'ambiguïté (miroir du renommage) : le moteur ne présage
        // JAMAIS le membre d'un récepteur de type projet (borne O3 de
        // l'audit — membres hérités non résolus pour récepteurs
        // projet). L'option Super n'apparaît pas : vide, jamais un
        // faux jalon. Le chemin usuel (curseur sur la DÉCLARATION
        // outrepassante) est couvert par les tests ci-dessus.
        val cibles = session.superDefinition(uriMain, decalageDe(mainKt, "run()"))
        assertTrue(cibles.isEmpty(), "usage ambigu : vide (borne O3), pas un faux jalon : $cibles")
        session.close()
    }

    @Test
    fun `super-sur-usage-UNIQUE-d'un-membre---les-supertypes-du-déclarant`() {
        val usageKt =
            """
            package demo

            class Usage {
                val hote: ImplA = ImplA()

                fun appel(): Int = hote.tag
            }
            """.trimIndent()
        val (session, uris) =
            sessionAvecRacines(
                "demo/Base.kt" to baseKt,
                "demo/ImplA.kt" to implAKt,
                "demo/Usage.kt" to usageKt,
            )
        val uriUsage = uris.getValue("demo/Usage.kt")
        session.ouvrir(uriUsage, usageKt)
        // `tag` n'existe que dans ImplA (cible UNIQUE → prouvée) : le
        // chemin membre éprouve les supertypes du déclarant ImplA,
        // AUCUN homonyme `tag` n'y vit → retombée TYPE : la classe
        // englobante Usage n'a pas de supertype écrit → vide.
        val cibles = session.superDefinition(uriUsage, decalageDe(usageKt, "tag"))
        assertTrue(cibles.isEmpty(), "usage unique sans super vivant : $cibles")
        session.close()
    }

    @Test
    fun `super-sur-propriété-NON-outrepassante---retombée-classe-englobante`() {
        val (session, uris) =
            sessionAvecRacines(
                "demo/Base.kt" to baseKt,
                "demo/ImplA.kt" to implAKt,
            )
        val uriImpl = uris.getValue("demo/ImplA.kt")
        session.ouvrir(uriImpl, implAKt)
        // Caret sur le champ « tag » (aucun homonyme dans Base) — le
        // chemin membre ne trouve rien, la retombée TYPE montre les
        // supertypes DIRECTS de la classe ENGLOBANTE ImplA.
        val cibles = session.superDefinition(uriImpl, decalageDe(implAKt, "tag"))
        assertEquals(1, cibles.size, "le supertype direct : $cibles")
        val cible = cibles.single()
        assertEquals(uris.getValue("demo/Base.kt"), cible.uri)
        assertEquals("Base  ·  demo", cible.libelle, "libellé picker « Simple  ·  pkg »")
        assertEquals(
            baseKt.indexOf("class Base") + "class ".length,
            cible.plage.debut,
            "plage du NOM Base",
        )
        session.close()
    }

    // ── Chemin TYPE ─────────────────────────────────────────────────

    @Test
    fun `super-sur-référence-de-type---les-supertypes-du-type-visé`() {
        val (session, uris) =
            sessionAvecRacines(
                "demo/Base.kt" to baseKt,
                "demo/ImplA.kt" to implAKt,
                "demo/Main.kt" to mainKt,
            )
        val uriMain = uris.getValue("demo/Main.kt")
        session.ouvrir(uriMain, mainKt)
        // Caret sur « ImplA » (référence de type dans la signature) —
        // le type en contexte EST ImplA (miroir contextType ecj), ses
        // supertypes DIRECTS : Base.
        val cibles = session.superDefinition(uriMain, decalageDe(mainKt, "ImplA"))
        assertEquals(1, cibles.size, "supertype direct de ImplA : $cibles")
        assertEquals("Base  ·  demo", cibles.single().libelle)
        assertEquals(uris.getValue("demo/Base.kt"), cibles.single().uri)
        session.close()
    }

    @Test
    fun `super-classe-racine-sans-clause---aucune-cible-(garde-D3)`() {
        val (session, uris) =
            sessionAvecRacines(
                "demo/Base.kt" to baseKt,
                "demo/ImplA.kt" to implAKt,
                "demo/Main.kt" to mainKt,
            )
        val uriBase = uris.getValue("demo/Base.kt")
        session.ouvrir(uriBase, baseKt)
        // Caret sur le champ « compteur » DANS Base : le chemin membre
        // ne trouve pas d'homonyme (Any est IMPLICITE — jamais écrit),
        // la retombée type ne montre que les supertypes ÉCRITS de
        // Base : aucun → vide honnête.
        val cibles = session.superDefinition(uriBase, decalageDe(baseKt, "compteur"))
        assertTrue(cibles.isEmpty(), "aucun supertype écrit : $cibles")
        session.close()
    }

    @Test
    fun `supertype-classpath-SANS-source-projet---aucune-cible-(garde-D3)`() {
        val androidViewKt =
            """
            package demo

            import android.view.View

            class MaVue : View {
                val tag: Int = 1

                override fun toString(): String = "x"
            }
            """.trimIndent()
        // Vue NULLE (classpath absent) : le supertype écrit ne résout
        // vers AUCUNE déclaration projet — vide, jamais un faux jalon.
        val (session, uris) =
            sessionAvecRacines(
                "demo/MaVue.kt" to androidViewKt,
            )
        val uriVue = uris.getValue("demo/MaVue.kt")
        session.ouvrir(uriVue, androidViewKt)
        val cibles = session.superDefinition(uriVue, decalageDe(androidViewKt, "tag"))
        assertTrue(cibles.isEmpty(), "supertype classpath sans source : $cibles")
        session.close()
    }

    @Test
    fun `document-inconnu-ou-fermé---liste-vide,-jamais-d'exception`() {
        val (session, _) =
            sessionAvecRacines(
                "demo/Base.kt" to baseKt,
            )
        assertTrue(session.superDefinition("file:///introuvable.kt", 3).isEmpty(), "document inconnu : vide")
        session.close()
        assertTrue(
            session.superDefinition("file:///quelconque.kt", 3).isEmpty(),
            "session fermée : vide",
        )
    }
}

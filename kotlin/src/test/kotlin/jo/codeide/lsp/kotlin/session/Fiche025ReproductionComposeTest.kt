package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreSymboleKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 025 — ReproductionComposeTest : REPRODUCTION des faux positifs
 * `kt.unresolved` signalés par le propriétaire sur un projet Android
 * Compose (25/10/2026) — 12 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * Classpath : kotlin-stdlib RÉEL (voie 6 d'ADR 0203) — les motifs ne
 * dépendent d'aucune AAR Compose, ils isolent les FORMES.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche025ReproductionComposeTest {

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
        val copie = Files.createTempFile(racine, "f025-stdlib", ".jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        return copie
    }

    /** Session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copieStdlib().path, "f025:stdlib")),
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
    fun `repro---lambda-finale-a-parametre-nomme-(Scaffold-innerPadding)`() {
        val session = session()
        val source =
            """
            class PaddingValues

            fun Scaffold(contenu: (PaddingValues) -> Unit) {}

            fun Ecran() {
              Scaffold { innerPadding ->
                val p: PaddingValues = innerPadding
              }
            }
            """.trimIndent()
        val uri = "file:///compose-scaffold.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("innerPadding") },
            "la lambda finale à paramètre nommé type son paramètre : $recus",
        )
        session.close()
    }

    @Test
    fun `repro---lambda-imbriquee-parametres-nommes`() {
        val session = session()
        val source =
            """
            fun Conteneur(contenu: (Int) -> Unit) {}

            fun Ecran() {
              Conteneur { a ->
                Conteneur { b ->
                  val s = a + b
                }
              }
            }
            """.trimIndent()
        val uri = "file:///compose-imbriquee.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("a +") || it.contains("b") },
            "les lambdas imbriquées à paramètres nommés typent leurs paramètres : $recus",
        )
        session.close()
    }

    @Test
    fun `repro---appel-complet-qualifie-premier-segment-(paquet)`() {
        val session = session()
        val source =
            """
            fun demo() {
              kotlin.io.println("x")
            }
            """.trimIndent()
        val uri = "file:///compose-qualifie-paquet.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le PREMIER segment d'un appel complet qualifié est un PAQUET
        // (`kotlin.io`) : il n'est JAMAIS signalé — la feuille seule est
        // faillible comme appelable du package.
        assertTrue(recus.none { it.contains("kotlin") || it.contains("io") || it.contains("println") },
            "l'appel complet qualifié par paquet est muet : $recus",
        )
        session.close()
    }

    @Test
    fun `repro---annotation-qualifiee`() {
        val session = session()
        val source =
            """
            @kotlin.jvm.JvmName("nomAlternatif")
            fun originale() {}
            """.trimIndent()
        val uri = "file:///compose-annotation.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("JvmName") || it.contains("jvm") },
            "l'annotation qualifiée résout par son chemin de classe : $recus",
        )
        session.close()
    }

    @Test
    fun `repro---delegation-by-lazy`() {
        val session = session()
        val source =
            """
            class Ecran {
              val titre: String by lazy { "chargé" }
            }
            """.trimIndent()
        val uri = "file:///compose-by-lazy.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("lazy") },
            "la délégation by lazy résout (facade LazyKt du stdlib) : $recus",
        )
        session.close()
    }

    @Test
    fun `repro---import-alias-puis-usage`() {
        val session = session()
        val source =
            """
            import kotlin.text.Regex as Motif

            fun demo(): Boolean = Motif("a.b").matches("a.b")
            """.trimIndent()
        val uri = "file:///compose-import-alias.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("Motif") || it.contains("matches") },
            "l'import alias puis l'usage résolvent : $recus",
        )
        session.close()
    }

    @Test
    fun `repro---import-joker-puis-usage`() {
        val session = session()
        val source =
            """
            import kotlin.text.*

            fun demo(): Boolean = Regex("a").matches("a")
            """.trimIndent()
        val uri = "file:///compose-import-joker.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("Regex") || it.contains("matches") },
            "l'import joker puis l'usage restent muets : $recus",
        )
        session.close()
    }

    @Test
    fun `repro---appel-de-fonction-importee-prefixee-par-paquet`() {
        val session = session()
        val source =
            """
            import kotlin.io.println

            fun demo() {
              println("x")
            }
            """.trimIndent()
        val uri = "file:///compose-import-fonction.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("println") },
            "l'appel de la fonction importée (chemin préfixé par paquet) est muet : $recus",
        )
        session.close()
    }

    @Test
    fun `sondage---survol-d'un-appelable-top-level-du-classpath-(println)`() {
        val session = session()
        val source =
            """
            fun demo() {
              println("x")
            }
            """.trimIndent()
        val uri = "file:///compose-survol-println.kt"
        session.ouvrir(uri, source)
        // Le SURVOL d'un appelable top-level du classpath : la signature
        // dépouillée de l'index + le package déclarant.
        val survol = session.survol(uri, source.indexOf("println"))
        assertTrue(
            survol != null && survol.signature.contains("println") && survol.signature.contains("kotlin.io"),
            "le survol de println rend la signature de l'index : ${survol?.signature}",
        )
        session.close()
    }

    @Test
    fun `sondage---symboles-de-document-d'un-fichier-Compose-like`() {
        val session = session()
        val source =
            """
            class Ecran(val titre: String) {
              fun rafraichir() {}
            }

            object Routes {
              val accueil = "accueil"
            }

            fun chargerDonnees(): List<String> = emptyList()
            """.trimIndent()
        val uri = "file:///compose-symboles.kt"
        session.ouvrir(uri, source)
        val symboles = session.symboles(uri)
        val noms = symboles.map { it.nom }
        // Le PLAN du fichier : les déclarations nommées avec leurs genres
        // neutres (Ecran CLASSE, rafraichir FONCTION, Routes OBJET,
        // accueil PROPRIETE, chargerDonnees FONCTION).
        assertTrue(
            "Ecran" in noms && "rafraichir" in noms && "Routes" in noms &&
                "accueil" in noms && "chargerDonnees" in noms,
            "les symboles du document couvrent les déclarations : $noms",
        )
        assertEquals(GenreSymboleKotlin.CLASSE, symboles.first { it.nom == "Ecran" }.genre)
        assertEquals(GenreSymboleKotlin.FONCTION, symboles.first { it.nom == "rafraichir" }.genre)
        assertEquals(GenreSymboleKotlin.OBJET, symboles.first { it.nom == "Routes" }.genre)
        assertEquals(GenreSymboleKotlin.PROPRIETE, symboles.first { it.nom == "accueil" }.genre)
        session.close()
    }

    @Test
    fun `sondage---references-d'une-fonction-projet`() {
        val session = session()
        val source =
            """
            fun saluer(destinataire: String): String = "Bonjour " + destinataire

            fun demo(): String {
              val a = saluer("Léa")
              val b = saluer("Marc")
              return a + b
            }
            """.trimIndent()
        val uri = "file:///compose-references.kt"
        session.ouvrir(uri, source)
        // Les RÉFÉRENCES d'une fonction projet : le site de DÉCLARATION
        // puis les deux USAGES, en ordre du document.
        val occurrences = session.occurrences(uri, source.indexOf("saluer"))
        assertEquals(3, occurrences.size, "une déclaration + deux usages : $occurrences")
        val plages = occurrences.map { it.plage.debut }
        assertEquals(plages, plages.sorted(), "les occurrences sont en ordre du document")
        session.close()
    }

    @Test
    fun `sondage---completion-de-nom-dans-un-corps`() {
        val session = session()
        val source =
            """
            fun chargerDonnees(): List<String> = emptyList()

            fun demo() {
              charg
            }
            """.trimIndent()
        val uri = "file:///compose-completion-nom.kt"
        session.ouvrir(uri, source)
        // La COMPLÉTION de nom dans un corps : le préfixe frappé `charg`
        // propose la fonction déclarée `chargerDonnees`.
        val point = source.indexOf("charg") + "charg".length
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("chargerDonnees" in propositions,
            "la complétion de nom propose les déclarés par préfixe : $propositions",
        )
        session.close()
    }
}

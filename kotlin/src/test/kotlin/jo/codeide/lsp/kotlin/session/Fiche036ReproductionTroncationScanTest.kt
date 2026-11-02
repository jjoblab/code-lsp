package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 036 — ReproductionTroncationScanTest : H1 — TRONCATION DU SCAN
 * (mission fiabilisation, étape A.2 → CORRECTIF étape B) — 3
 * comportements du cahier docs/cahier-specifications-kotlin/.
 *
 * La MARCHE du scan est BORNÉE ([ScanProjetParesseux.BORN_FICHIERS]) ;
 * au-delà de la borne, les fichiers sont IGNORÉS du parse mais COMPTÉS
 * (statistiques honnêtes : total réel). La passe tronquée rend un snapshot
 * PARTIEL — l'univers des preuves est OUVERT, la référence au type du
 * fichier non scanné est MUETTE (absence non prouvable, D3).
 *
 * La couture de constructeur (`bornFichiersScan`) sert la fiche SANS écrire
 * 2 001 fichiers — la production garde le défaut 2 000.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche036ReproductionTroncationScanTest {

    /** La racine temporaire du projet. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /**
     * Sème `nombre` fichiers dans la racine (chacun déclare une classe
     * Distincte) et rend la racine.
     */
    private fun semerRacine(nombre: Int): Path {
        val dossier = Files.createDirectories(racine.resolve("src"))
        for (i in 1..nombre) {
            Files.writeString(
                dossier.resolve("Fichier%03d.kt".format(i)),
                "class Distincte%03d".format(i),
            )
        }
        return dossier
    }

    /**
     * Session DIRECTE (sans moteur nourricier) avec la borne de collecte
     * donnée — la couture de test de [ScanProjetParesseux].
     */
    private fun sessionBornee(borne: Int): SessionLangageKotlinDocuments {
        val copieStdlib = Files.createTempFile(racine, "f036-stdlib", ".jar").toFile()
        copieStdlib.writeBytes(jarStdlib().readBytes())
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copieStdlib.path, "f036:stdlib")),
            racinesSources = listOf(racineResolveSources().toAbsolutePath().toString()),
        )
        val session = SessionLangageKotlinDocuments(moteur = null, bornFichiersScan = borne)
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** La racine sources posée par le test courant (mise avant la session). */
    private var racinePosee: Path? = null

    /** Pose puis rend la racine sources du test courant. */
    private fun racineResolveSources(): Path = racinePosee!!

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
    fun `marche-bornee---le-fichier-au-dela-de-la-borne-est-compte-et-estUniversConnu-rend-faux`() {
        racinePosee = semerRacine(3)
        val session = sessionBornee(borne = 2)
        // Le fichier AU-DELÀ de la borne est COMPTÉ (total réel 3, faits 2)
        // et la passe rend un état PARTIEL — l'univers n'est PAS connu.
        val stats = session.statsDernierPassageScan()
        assertEquals(3, stats.total, "le total compte les fichiers éligibles VUS (au-delà de la borne)")
        assertEquals(2, stats.faits, "la collecte s'arrête à la borne")
        assertEquals(
            jo.codeide.lsp.kotlin.resolve.EtatScan.PARTIEL,
            session.etatScanAuConsult(),
            "estUniversConnu rend faux : la passe tronquée est PARTIELLE",
        )
        session.close()
    }

    @Test
    fun `correction---la-reference-au-type-du-fichier-non-scanne-est-muette-(absence-non-prouvable)`() {
        racinePosee = semerRacine(3)
        val session = sessionBornee(borne = 2)
        val source =
            """
            fun demo(d: Distincte003): Int = d.toString().length
            """.trimIndent()
        val uri = "file:///troncation-correction.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le type du fichier NON SCANNÉ (au-delà de la borne) : MUET — la
        // passe PARTIELLE ouvre l'univers, l'absence n'est pas prouvable.
        assertTrue(recus.none { it.contains("Distincte003") },
            "la référence au type du fichier non scanné est muette : $recus",
        )
        session.close()
    }

    @Test
    fun `controle---sans-troncation,-la-meme-reference-est-muette`() {
        racinePosee = semerRacine(2)
        val session = sessionBornee(borne = 2)
        val source =
            """
            fun demo(d: Distincte002): Int = d.toString().length
            """.trimIndent()
        val uri = "file:///troncation-controle.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Sans troncation (2 fichiers ≤ borne 2) : la passe est COMPLÈTE,
        // le type du frère scanné RÉSOUT — la même référence est muette
        // par PREUVE de présence (le harnais ne valide pas un « tout muet »).
        assertEquals(
            jo.codeide.lsp.kotlin.resolve.EtatScan.COMPLET,
            session.etatScanAuConsult(),
            "sans troncation la passe est complète",
        )
        assertTrue(recus.isEmpty(), "la référence au type scanné est muette : $recus")
        session.close()
    }
}

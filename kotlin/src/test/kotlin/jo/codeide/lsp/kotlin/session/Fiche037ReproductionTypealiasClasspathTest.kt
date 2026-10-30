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
 * Fiche 037 — ReproductionTypealiasClasspathTest : REPRODUCTION (mission
 * fiabilisation, étape B — rejeu du non-concluant §4 du rapport faux
 * positifs) : un TYPEALIAS PUBLIC d'une bibliothèque Kotlin — 5
 * comportements du cahier docs/cahier-specifications-kotlin/.
 *
 * Aucun miroir bytecode : la sonde [jo.lspkotlin.classpath.SondeTypealiasKotlinxTest]
 * a établi que le corpus kotlinx-datetime 0.6.1 en porte trois
 * (`DayOfWeek`, `Month`, `ZoneOffset`) — la facade `*Kt` est leur seule
 * existence. Le jar RÉEL du classpath de test est posé en entrée : le
 * décodeur @Metadata du moteur en tire les alias publics, la résolution
 * des types qualifiés les consulte (chemin d-bis, vague 2d).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche037ReproductionTypealiasClasspathTest {

    /** La racine temporaire de la copie du jar. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlinx-datetime du classpath de test (jar physique réel). */
    private fun jarDatetime(): java.io.File =
        java.io.File(
            kotlinx.datetime.Clock::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une copie isolée du jar (le classpath de test reste intact). */
    private fun copieDatetime(): java.io.File {
        val copie = Files.createTempFile(racine, "f037-datetime", ".jar").toFile()
        copie.writeBytes(jarDatetime().readBytes())
        return copie
    }

    /** Session branchée sur kotlinx-datetime (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val jar = copieDatetime()
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f037:datetime")),
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
    fun `typealias-importe-explicitement---type-nu-muet`() {
        val session = session()
        val source =
            """
            import kotlinx.datetime.DayOfWeek

            fun demo(j: DayOfWeek): Int = 1
            """.trimIndent()
        val uri = "file:///typealias-importe.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le type NU derrière l'import EXPLICITE : muet (l'import exact
        // résout par son nom effectif, l'alias n'a pas de miroir bytecode).
        assertTrue(recus.none { it.contains("DayOfWeek") },
            "DayOfWeek importé explicitement : type nu muet : $recus",
        )
        session.close()
    }

    @Test
    fun `typealias-qualifie-complet---dernier-segment-muet`() {
        val session = session()
        val source =
            """
            fun demo(j: kotlinx.datetime.DayOfWeek): Int = 1
            """.trimIndent()
        val uri = "file:///typealias-qualifie.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le chemin QUALIFIÉ COMPLET : le dernier segment reste muet —
        // l'alias public du classpath se consulte par son FQCN (chemin
        // d-bis, vague 2d) alors qu'aucune classe ne le porte.
        assertTrue(recus.none { it.contains("DayOfWeek") },
            "kotlinx.datetime.DayOfWeek qualifié : dernier segment muet : $recus",
        )
        session.close()
    }

    @Test
    fun `typealias-derriere-import-joker---type-nu-muet`() {
        val session = session()
        val source =
            """
            import kotlinx.datetime.*

            fun demo(j: Month): Int = 1
            """.trimIndent()
        val uri = "file:///typealias-joker.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le type NU derrière un import JOKER d'un package non énumérable :
        // muet (l'univers est ouvert, D3 — un joker classpath ne prouve
        // jamais qu'un nom n'y vit pas).
        assertTrue(recus.none { it.contains("Month") },
            "Month derrière import joker : type nu muet : $recus",
        )
        session.close()
    }

    @Test
    fun `controles---classe-reelle-du-corpus-muette-en-import-et-en-qualifie`() {
        val session = session()
        val source =
            """
            import kotlinx.datetime.Clock

            fun demo(a: Clock, b: kotlinx.datetime.Instant): Int = 1
            """.trimIndent()
        val uri = "file:///controles-classe-reelle.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Les classes RÉELLES du corpus (Clock, Instant — class-files
        // présents dans le jar) : muettes en IMPORT (nom effectif) et en
        // QUALIFIÉ (chemin de classe classpath, voie d).
        assertTrue(
            recus.none { it.contains("Clock") || it.contains("Instant") },
            "les classes réelles du corpus sont muettes en import et en qualifié : $recus",
        )
        session.close()
    }

    @Test
    fun `controle-d3---nom-inconnu-en-qualifie-reste-signale`() {
        val session = session()
        val source =
            """
            fun demo(j: kotlinx.datetime.NomQuiNExistePas): Int = 1
            """.trimIndent()
        val uri = "file:///controle-d3-qualifie.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le vrai négatif de contrôle : ni classe ni alias ne porte ce nom
        // dans le package — le DERNIER segment reste SIGNALÉ.
        assertEquals(1, recus.size, "un nom inconnu en qualifié reste signalé : $recus")
        assertTrue(recus.single().contains("NomQuiNExistePas"))
        session.close()
    }
}

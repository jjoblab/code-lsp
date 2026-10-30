package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 023 — ReproductionAppelablesBuiltinTest : REPRODUCTION (mission
 * fiabilisation, étape A.3 — découverte du test différentiel
 * [jo.lspkotlin.classpath.LecteurClassBrutDifferentielTest]) : les
 * FONCTIONS BUILTIN `arrayOf` et `intArrayOf` du stdlib — 2 comportements
 * du cahier docs/cahier-specifications-kotlin/.
 *
 * Le moteur prouve l'absence d'un nom nu par la voie 6 (appelables de
 * premier niveau du classpath — ADR 0203) : le jar kotlin-stdlib RÉEL du
 * classpath de test est posé comme ENTRÉE, ses facades `*Kt` décodées par
 * le moteur — AUCUNE vue factice. Les intrinsèques réputées restent MUETTES
 * (silence conservateur D3 : aucune émission hors preuve), les contrôles
 * (`listOf`, `emptyArray`, `println`) pareillement.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche023ReproductionAppelablesBuiltinTest {

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
        val copie = Files.createTempFile(racine, "f023-stdlib", ".jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        return copie
    }

    /** Session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val jar = copieStdlib()
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f023:stdlib")),
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
    fun `builtins-arrayOf-et-intArrayOf---intrinseques-réputées,-muets`() {
        val session = session()
        val source =
            """
            fun demo() {
              val a = arrayOf(1, 2, 3)
              val b = intArrayOf(1, 2)
              val c = a.size + b.size
            }
            """.trimIndent()
        val uri = "file:///builtins.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Les intrinseques reputees : MUETS — le stdlib reel est pose en
        // entree, la preuve par absence ne les atteint JAMAIS (D3).
        assertTrue(recus.none { it.contains("arrayOf") || it.contains("intArrayOf") },
            "arrayOf et intArrayOf muets : $recus",
        )
        session.close()
    }

    @Test
    fun `controles---listOf,-emptyArray-et-println-restent-muets`() {
        val session = session()
        val source =
            """
            fun demo() {
              val l = listOf(1, 2)
              val v = emptyArray<String>()
              println(l.size + v.size)
            }
            """.trimIndent()
        val uri = "file:///builtins-controles.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(
            recus.none { it.contains("listOf") || it.contains("emptyArray") || it.contains("println") },
            "listOf, emptyArray et println muets : $recus",
        )
        session.close()
    }
}

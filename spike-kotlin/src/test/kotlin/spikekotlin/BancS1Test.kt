package spikekotlin

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Banc S1 — FRONTEND K2 EMBARQUÉ : `kotlin-compiler-embeddable 2.2.10`.
 *
 * Contexte de l'étude : l'Analysis API standalone N'EST PAS publiée sur
 * Maven Central (constat de l'étude, 01-architectures-analyse-kotlin.md
 * §K2) — le seul vaisseau K2 disponible est le compilateur embarqué
 * (53,6 Mo / 25 019 classes / 64 507 méthodes déclarées, scan ART
 * `resultats/scan-art-s1.txt` : ~2 170 références sensibles dont
 * MethodHandles ×2 084, swing, awt, `sun.misc.Unsafe` ×2).
 *
 * Mesure retenue (robuste, reproductible) : le compilateur K2 exécuté en
 * **sous-processus** sur le corpus — `K2JVMCompiler` en classe main du
 * jar. C'est le coût intégral de la « profondeur K2 » : démarrage JVM +
 * chargement du frontend + analyse FIR complète + génération. Toute
 * variante « K2 à la demande » paie au minimum ce coût d'amorçage une
 * fois ; l'écart chaud/froid du spike Java (V2/V3) suggère un facteur
 * 3–7 entre ce coût froid et un accès chaud in-process [estimé].
 *
 * Toutes les mesures sont des LIMITES BASSES JVM de bureau (R5).
 */
class BancS1Test {

    companion object {
        @JvmStatic
        @BeforeClass
        fun jarPresent() {
            assumeTrue(
                "kotlin-compiler-embeddable absent — lancer spike-kotlin/scripts/telecharger-jars.sh",
                CheminJars.jar("kotlin-compiler-embeddable-2.2.10.jar") != null,
            )
        }
    }

    private val jarCompilateur = CheminJars.jar("kotlin-compiler-embeddable-2.2.10.jar")!!
    private val jarStdlib = CheminJars.jar("kotlin-stdlib-2.2.10.jar")!!
    private val jarCoroutines = CheminJars.jar("kotlinx-coroutines-core-jvm-1.8.0.jar")!!
    private val jarAnnotations = CheminJars.jar("annotations-26.0.2.jar")!!
    private val java = File(System.getProperty("java.home"), "bin/java")

    /** Mesure le temps de [bloc] en ms. */
    private inline fun chronometreMs(bloc: () -> Unit): Double {
        val debut = System.nanoTime()
        bloc()
        return (System.nanoTime() - debut) / 1_000_000.0
    }

    /** Démarrage à froid : compilation d'un fichier trivial (1 classe). */
    @Test
    fun `demarrage a froid - K2 sur fichier trivial`() {
        val trivial = File.createTempFile("trivial", ".kt")
        trivial.writeText("package demo\n\nfun main() { println(\"spike\") }\n")

        val froide = chronometreMs { compilerK2Assez(trivial) }
        println("S1;demarrage;k2_fichier_trivial=${"%.0f".format(froide)}ms")

        // Chaud (deuxième sous-processus : mesure la variance, pas un cache).
        val seconde = chronometreMs { compilerK2Assez(trivial) }
        println("S1;demarrage;k2_fichier_trivial_passe2=${"%.0f".format(seconde)}ms")
        trivial.delete()
        assertTrue(true)
    }

    /** Compilation K2 complète du corpus 500 lignes. */
    @Test
    fun `compilation K2 complete - 500 lignes`() {
        val fichier = File.createTempFile("corpus500k2", ".kt")
        fichier.writeText(GenerateurCorpus.fichier(500))
        val latence = chronometreMs { compilerK2Assez(fichier) }
        println("S1;compilation;k2_500l=${"%.0f".format(latence)}ms")
        fichier.delete()
        assertTrue(true)
    }

    /** Compilation K2 complète du corpus 3 000 lignes (échelle §4 du prompt). */
    @Test
    fun `compilation K2 complete - 3000 lignes`() {
        val fichier = File.createTempFile("corpus3000k2", ".kt")
        fichier.writeText(GenerateurCorpus.fichier(3000))
        val latence = chronometreMs { compilerK2Assez(fichier) }
        println("S1;compilation;k2_3000l=${"%.0f".format(latence)}ms")
        fichier.delete()
        assertTrue(true)
    }

    /** Exécution K2 mesurée : compile [sources] en sous-processus (sortie consommée). */
    private fun compilerK2Assez(vararg sources: File) {
        compilerK2Silencieux(*sources)
    }

    private fun compilerK2Silencieux(vararg sources: File) {
        val sortie = File.createTempFile("spike-s1-out", "").apply { delete() }
        val process = ProcessBuilder(
            java.absolutePath,
            "-Xmx2g",
            "-cp",
            "${jarCompilateur.absolutePath}:${jarStdlib.absolutePath}:${jarCoroutines.absolutePath}:${jarAnnotations.absolutePath}",
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            *sources.map { it.absolutePath }.toTypedArray(),
            "-cp",
            jarStdlib.absolutePath,
            "-d",
            sortie.absolutePath,
        ).redirectErrorStream(true).start()

        val flux = process.inputStream
        val tampon = ByteArray(8192)
        while (flux.read(tampon) != -1) { /* consomme */ }
        val code = process.waitFor()
        flux.close()
        sortie.delete()
        assertTrue("le compilateur K2 doit réussir (code=$code)", code == 0)
    }
}

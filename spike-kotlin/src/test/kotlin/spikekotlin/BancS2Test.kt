package spikekotlin

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Banc S2 — PARSEUR OFFICIEL SEUL : la grammaire vendorée DEPUIS L'AMONT
 * (tag v2.3.0) conduite par [ParseurOfficiel], SANS modèle de symboles.
 *
 * Mesures (limites basses JVM de bureau, R5) :
 * - parse complet (lexing + arbre de production) à froid puis à chaud
 *   (min de N passes) sur corpus 500 et 3 000 lignes ;
 * - volumétrie d'arbre (productions/jetons) pour le budget mémoire ;
 * - tolérance : aucun lever sur texte tronqué (D3 amont).
 */
class BancS2Test {

    companion object {
        @JvmStatic
        @BeforeClass
        fun jarsPresents() {
            assumeTrue(
                "syntax-api absent — lancer spike-kotlin/scripts/telecharger-jars.sh",
                CheminJars.jar("syntax-api-jvm-0.3.340.jar") != null,
            )
        }
    }

    private fun ms(nanos: Long): String = "%.1f".format(nanos / 1_000_000.0)

    @Test
    fun `corpus 500 lignes - parse officiel, froid puis chaud, p50-p95`() {
        val texte = GenerateurCorpus.fichier(500)

        // Froid (premier chargement des tables de la grammaire).
        val froid = mesure { ParseurOfficiel.parse(texte) }

        // Chaud : 50 passes → p50/p95.
        val passes = (1..50).map { mesure { ParseurOfficiel.parse(texte) }.toDouble() }
        val triees = passes.sorted()
        val p50 = triees[triees.size / 2]
        val p95 = triees[(triees.size * 95) / 100]
        val arbre = ParseurOfficiel.parse(texte)

        println(
            "S2;corpus=500;parse_froid=${ms(froid.toLong())}ms;parse_chaud_p50=${ms(p50.toLong())}ms;" +
                "parse_chaud_p95=${ms(p95.toLong())}ms;passes=50;" +
                "productions=${arbre.nombreProductions()};jetons=${arbre.nombreJetons()}",
        )
        assertTrue(arbre.nombreProductions() > 0)
    }

    @Test
    fun `corpus 3000 lignes - parse officiel, froid puis chaud, p50-p95`() {
        val texte = GenerateurCorpus.fichier(3000)

        val froid = mesure { ParseurOfficiel.parse(texte) }
        val passes = (1..20).map { mesure { ParseurOfficiel.parse(texte) }.toDouble() }
        val triees = passes.sorted()
        val p50 = triees[triees.size / 2]
        val p95 = triees[(triees.size * 95) / 100]
        val arbre = ParseurOfficiel.parse(texte)

        println(
            "S2;corpus=3000;parse_froid=${ms(froid.toLong())}ms;parse_chaud_p50=${ms(p50.toLong())}ms;" +
                "parse_chaud_p95=${ms(p95.toLong())}ms;passes=20;" +
                "productions=${arbre.nombreProductions()};jetons=${arbre.nombreJetons()}",
        )
        assertTrue(arbre.nombreProductions() > 0)
    }

    /** Tolérance D3 de la grammaire amont : la troncature ne lève jamais. */
    @Test
    fun `tolerance - troncature hostile, aucun lever`() {
        val texte = GenerateurCorpus.fichier(500)
        var levers = 0
        // Troncature à chaque position d'un échantillon régulier (100 pas).
        for (position in texte.indices step (texte.length / 100).coerceAtLeast(1)) {
            try {
                ParseurOfficiel.parse(texte.subSequence(0, position))
            } catch (e: Throwable) {
                levers += 1
            }
        }
        println("S2;tolerance;troncatures=${texte.length / (texte.length / 100).coerceAtLeast(1)};levers=$levers")
        assertTrue("la grammaire officielle ne doit JAMAIS lever (D3 amont)", levers == 0)
    }

    private inline fun mesure(bloc: () -> Unit): Long {
        val debut = System.nanoTime()
        bloc()
        return System.nanoTime() - debut
    }
}

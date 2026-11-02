// CodeIDE — banc de cohérence P9 : résistance à la frappe (module :kotlin,
// vague 0). Code NEUF jjoblab (banc de MESURE).
package jo.codeide.lsp.kotlin.coherence

import java.nio.file.Files
import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * P9 — Résistance à la frappe (banc de cohérence, vague 0 — miroir de la
 * P1 Java : rejeu d'historiques de frappe réels — ici troncatures hostiles
 * du banc de la grammaire, étendues aux requêtes du moteur).
 *
 * ## Ce que la propriété mesure
 *
 * Le code en cours de frappe est CASSÉ par construction : un document
 * tronqué à un offset arbitraire (miroir du banc de tolérance, 100
 * troncatures hostiles) doit pouvoir être (1) diagnostiqué et (2)
 * complété SANS LEVER — et la complétion mesure son taux d'échec de
 * classification (positions membres devenues INCONNU).
 *
 * ## Assertions vs mesures (R5)
 *
 * - **ASSERTÉ** (toujours) : zéro exception sur toutes les troncatures
 *   (le contrat « l'éditeur appelle le parseur à CHAQUE frappe » de la
 *   fiche 049, étendu au moteur entier) ;
 * - **mesuré** (rapport) : taux de classification INCONNU sur les
 *   positions membres des documents tronqués.
 */
class P9ResistanceFrappeTest {

    @Test
    fun `des-documents-tronqués-ne-cassent-jamais-diagnostic-ni-complétion`() {
        val racine = Files.createTempDirectory("p9-frappe")
        val source = SocleCoherence.racineDepot.resolve("kotlin/src/test/resources/fidelite")
        assertTrue(Files.isDirectory(source), "le répertoire des mondes doit exister")
        val documents = Files.list(source).use { flux ->
            flux.filter { it.fileName.toString().endsWith(".kt") }
                .sorted()
                .map { it.fileName.toString() to Files.readString(it) }
                .toList()
        }
        assertTrue(documents.isNotEmpty(), "les mondes doivent exister")
        val jars = SocleCoherence.assurerPlancherStdlib(SocleCoherence.jarsAutoCorpus())
        val session = SocleCoherence.monterSession(listOf(racine), jars)
        try {
            session.rafraichirSources()
            var troncatures = 0
            var exceptions = 0
            var positionsMembres = 0
            var membresClassés = 0
            var inconnus = 0
            for ((nom, texte) in documents) {
                val uri = racine.resolve("frappe-$nom").toUri().toString()
                for (pas in listOf(3, 7, 11, 17, 29, 43, 71, 113)) {
                    val offset = (texte.length * pas) / 120
                    if (offset <= 0 || offset >= texte.length) continue
                    val tronqué = texte.substring(0, offset)
                    troncatures++
                    try {
                        session.ouvrir(uri, tronqué)
                        session.diagnostics(uri)
                        // Dernière position membre du texte tronqué (recepteur.|).
                        val dernierPoint = tronqué.lastIndexOf('.')
                        if (dernierPoint > 0 && dernierPoint + 1 < tronqué.length) {
                            positionsMembres++
                            val reponse = session.completionArrangee(uri, dernierPoint + 1)
                            when (reponse.genre) {
                                GenreContexteKotlin.INCONNU -> inconnus++
                                else -> membresClassés++
                            }
                        }
                        session.fermer(uri)
                    } catch (e: Exception) {
                        exceptions++
                        println("P9 EXCEPTION $nom@$offset : ${e::class.simpleName} : ${e.message}")
                    }
                }
            }
            val tauxInconnu = if (positionsMembres == 0) 0.0 else inconnus * 100.0 / positionsMembres
            println(
                "P9 BILAN: documents=${documents.size}, troncatures=$troncatures, exceptions=$exceptions, " +
                    "positionsMembres=$positionsMembres, classées=$membresClassés, INCONNU=$inconnus " +
                    "(taux d'échec de classification=${String.format("%.1f", tauxInconnu)} %)",
            )
            assertTrue(
                exceptions == 0,
                "P9 : $exceptions exception(s) sur $troncatures troncatures — le moteur ne lève JAMAIS sur du texte hostile",
            )
        } finally {
            session.close()
        }
    }
}

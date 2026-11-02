// CodeIDE — banc de cohérence P6 : complétion résoluble et couverture
// typée (module :kotlin, vague 0). Code NEUF jjoblab (banc de MESURE).
package jo.codeide.lsp.kotlin.coherence

import java.nio.file.Files
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * P6 — Complétion « résoluble » et couverture typée (banc de cohérence,
 * vague 0 — miroir de la P6 Java + mesure KH1-proxy du prompt).
 *
 * ## Ce que la propriété mesure
 *
 * Sur des positions RÉELLES d'un document sonde :
 *
 * - **taux de classification** : la position reçoit un genre de contexte
 *   déterminé (≠ INCONNU) — la classification import→membre→nom est un
 *   contrat du cahier (fiches 043/045) ;
 * - **résolubilité (proxy)** : la PREMIÈRE proposition insérée au caret ne
 *   crée AUCUNE nouvelle erreur sur sa ligne — une suggestion insérable
 *   qui casse le document n'est pas une suggestion ;
 * - **couverture typée (KH1-proxy)** : part des positions MEMBRE dont la
 *   liste est NON VIDE — proxy honnête de « le récepteur est typé ».
 *
 * ## R5 — rapport par défaut, classification certaine assertée
 *
 * Le genre MEMBRE sur une position `recepteur.` est un invariant
 * syntaxique (cahier) : il est ASSERTÉ. La résolubilité et la couverture
 * sont des MESURES (le niveau dépend du palier de typage — ADR 0123) :
 * imprimées, affirmées nulle part à ce stade.
 */
class P6CompletionResolubleTest {

    @Test
    fun `les-suggestions-insérées-ne-cassent-pas-le-document-et-la-classification-tient`() {
        val racine = Files.createTempDirectory("p6-completion")
        val jars = SocleCoherence.assurerPlancherStdlib(SocleCoherence.jarsAutoCorpus())
        val document = racine.resolve("positions.kt")
        val texte = listOf(
            "package p6",
            "",
            "import java.util.ArrayList",
            "",
            "val nombres = listOf(1, 2, 3)",
            "",
            "class ZzMoteur {",
            "    val etiquettes = ArrayList<String>()",
            "",
            "    fun zzTotal(): Int {",
            "        etiquettes.add(\"x\")",
            "        return nombres.size",
            "    }",
            "}",
        ).joinToString("\n") + "\n"
        Files.writeString(document, texte)
        val uri = document.toUri().toString()

        val session = SocleCoherence.monterSession(listOf(racine), jars)
        try {
            session.rafraichirSources()
            session.ouvrir(uri, texte)

            // Position MEMBRE certaine : après « nombres. » (miroir du
            // cahier : recepteur.prefixe| → MEMBRE, classification
            // syntaxique).
            val pointMembre = texte.indexOf("nombres.") + "nombres.".length
            val reponseMembre = session.completionArrangee(uri, pointMembre)
            assertTrue(
                reponseMembre.genre == jo.codeide.lsp.kotlin.api.GenreContexteKotlin.MEMBRE,
                "la position recepteur.| doit se classifier MEMBRE (contrat du cahier) — reçu : ${reponseMembre.genre}",
            )
            println(
                "P6 position membre: genre=${reponseMembre.genre}, propositions=${reponseMembre.propositions.size}, " +
                    "totalDisponible=${reponseMembre.totalDisponible}, tronquee=${reponseMembre.tronquee}",
            )

            // Positions de mesure : membre (nombres.), membre (etiquettes.),
            // nom (après « val s: String = »), import (après java.util.).
            val positions = listOf(
                "membre-nombres" to pointMembre,
                "membre-etiquettes" to texte.indexOf("etiquettes.add") + "etiquettes.".length,
                "nom-attendu" to texte.indexOf("return nombres.size"),
                "import-java-util" to texte.indexOf("java.util.ArrayList") + "java.util.".length,
            )
            var classees = 0
            var nonVides = 0
            var membresNonVides = 0
            var membres = 0
            var insertionsPropres = 0
            var insertions = 0
            for ((nom, point) in positions) {
                val reponse = session.completionArrangee(uri, point)
                if (reponse.genre != jo.codeide.lsp.kotlin.api.GenreContexteKotlin.INCONNU) classees++
                if (reponse.propositions.isNotEmpty()) nonVides++
                if (reponse.genre == jo.codeide.lsp.kotlin.api.GenreContexteKotlin.MEMBRE) {
                    membres++
                    if (reponse.propositions.isNotEmpty()) membresNonVides++
                }
                println(
                    "P6 [$nom]: genre=${reponse.genre}, propositions=${reponse.propositions.size}, " +
                        "total=${reponse.totalDisponible}",
                )
                // Résolubilité : insertion de la première proposition au caret,
                // le document ne doit pas gagner d'ERREUR sur la ligne du caret.
                val premiere = reponse.propositions.firstOrNull() ?: continue
                insertions++
                val insertion = premiere.texteInsertion ?: premiere.libelle
                val muté = texte.substring(0, point) + insertion + texte.substring(point)
                session.modifier(uri, muté)
                val ligneCaret = 1 + muté.substring(0, point).count { it == '\n' }
                val nouvellesErreurs = session.diagnostics(uri)
                    .filter { it.severite == SeveriteKotlin.ERREUR }
                    .filter {
                        1 + muté.substring(0, it.plage.debut.coerceAtMost(muté.length)).count { c -> c == '\n' } == ligneCaret
                    }
                if (nouvellesErreurs.isEmpty()) insertionsPropres++
                else println("P6 [$nom]: insertion « $insertion » → ${nouvellesErreurs.size} erreur(s) sur la ligne")
                session.modifier(uri, texte)
            }
            val tauxClassification = classees * 100.0 / positions.size
            val couvertureTypée = if (membres == 0) 0.0 else membresNonVides * 100.0 / membres
            val tauxResoluble = if (insertions == 0) 0.0 else insertionsPropres * 100.0 / insertions
            println(
                "P6 BILAN: classification=${String.format("%.0f", tauxClassification)} %, " +
                    "couvertureTypéeMembre(H1-proxy)=${String.format("%.0f", couvertureTypée)} % ($membresNonVides/$membres), " +
                    "résoluble=${String.format("%.0f", tauxResoluble)} % ($insertionsPropres/$insertions)",
            )
            if (SocleCoherence.STRICT) {
                assertTrue(classees == positions.size, "P6 strict : classification ${classees}/${positions.size}")
                assertTrue(insertionsPropres == insertions, "P6 strict : résolubilité ${insertionsPropres}/${insertions}")
            }
        } finally {
            session.close()
        }
    }
}

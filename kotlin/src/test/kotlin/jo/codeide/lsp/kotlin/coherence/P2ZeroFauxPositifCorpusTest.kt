// CodeIDE — banc de cohérence P2 : zéro faux positif sur corpus Android
// réel (module :kotlin, vague 0). Code NEUF jjoblab (banc de MESURE).
package jo.codeide.lsp.kotlin.coherence

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * P2 — Zéro faux positif sur code qui COMPILE (banc de cohérence, vague 0 —
 * miroir de la P3 Java).
 *
 * ## Ce que la propriété mesure
 *
 * Chaque projet du corpus externe (`bancs/corpus-android-kotlin`) est
 * réputé COMPILER à son commit épinglé (ce sont des applications livrées) :
 * TOUT diagnostic `kt.*` de gravité ERREUR émis par le moteur sur leurs
 * sources est donc un FAUX POSITIF POTENTIEL — à la nuance près, tracée
 * au LISEZMOI du corpus, que la propriété exige les MANIFESTES (racines +
 * classpath compilé) : sans classpath réel, les `kt.unresolved` sur
 * `androidx.*` seraient des artefacts de montage, pas des faux positifs
 * du moteur.
 *
 * ## Gates (jamais un vert silencieux)
 *
 * La gate s'ignore AVEC LA RAISON AFFICHÉE tant que `CORPUS_KOTLIN` (ou
 * la propriété `corpus.kotlin`) ne pointe pas un corpus portant des
 * manifestes `*.manifeste` dans son sous-répertoire `prepares`.
 *
 * ## R5 — rapport par défaut, strict à la demande
 *
 * Mode RAPPORT (défaut) : violations, FP/1 000 lignes et répartition par
 * code IMPRIMÉS. Mode STRICT (`BANC_COHERENCE_STRICT`) : 0 violation
 * attendu — c'est la cible de la vague V-A (seuil D5 de la mission Java :
 * FP/1 000 < 0,5).
 */
class P2ZeroFauxPositifCorpusTest {

    @Test
    fun `zéro-faux-positif-sur-corpus-Android-réel-(manifestes-requis)`() {
        val projets = SocleCoherence.projetsCorpus()
        assumeTrue(
            projets.isNotEmpty(),
            "CORPUS_KOTLIN non posé ou manifestes absents — gate ignorée, jamais un vert silencieux",
        )
        for (projet in projets) {
            val fichiers = Files.walk(projet.racines.first()).use { flux ->
                flux.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                    .map { it.toAbsolutePath().normalize() }
                    .toList()
                    .sorted()
            }
            assertTrue(fichiers.isNotEmpty(), "le projet ${projet.nom} doit porter des fichiers .kt")
            val session = SocleCoherence.monterSession(projet.racines, SocleCoherence.assurerPlancherStdlib(projet.jars))
            try {
                session.rafraichirSources()
                var lignes = 0
                var violations = 0
                val parCode = mutableMapOf<String, Int>()
                val detail = mutableListOf<String>()
                for (fichier in fichiers) {
                    val texte = Files.readString(fichier)
                    lignes += texte.lines().size
                    val uri = fichier.toUri().toString()
                    session.ouvrir(uri, texte)
                    for (d in session.diagnostics(uri).filter { it.severite == SeveriteKotlin.ERREUR }) {
                        violations++
                        parCode.merge(d.code.code, 1, Int::plus)
                        if (detail.size < 50) {
                            detail += "${fichier.fileName}:${1 + texte.substring(0, d.plage.debut.coerceAtMost(texte.length)).count { it == '\n' }} [${d.code.code}] ${d.message}"
                        }
                    }
                    session.fermer(uri)
                }
                val pourMille = if (lignes == 0) 0.0 else violations * 1000.0 / lignes
                println(
                    "P2 ${projet.nom}: fichiers=${fichiers.size}, lignes=$lignes, " +
                        "violations=$violations (par code: $parCode), " +
                        "FP/1000l=${String.format(Locale.ROOT, "%.3f", pourMille)}",
                )
                detail.forEach { println("P2 ${projet.nom}: $it") }
                if (SocleCoherence.STRICT) {
                    assertTrue(
                        violations == 0,
                        "P2 strict ${projet.nom} : $violations violation(s) (cible 0) :\n${detail.joinToString("\n")}",
                    )
                }
            } finally {
                session.close()
            }
        }
    }
}

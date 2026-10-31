// CodeIDE — banc de cohérence P3 : vrais positifs par mutation (module
// :kotlin, vague 0). Code NEUF jjoblab (banc de MESURE).
package jo.codeide.lsp.kotlin.coherence

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * P3 — Rappel de vrais positifs par MUTATIONS (banc de cohérence, vague 0 —
 * miroir de la P4 Java : mutants à la bonne plage).
 *
 * ## Ce que la propriété mesure
 *
 * Le corpus (les quatre « mondes » de la fidélité + l'auto-corpus en
 * échantillon borné) est copié dans un répertoire temporaire, puis des
 * mutations CIBLÉES y cassent délibérément le code :
 *
 * - **M1 (import)** : suppression de la première directive d'import —
 *   attendu : `kt.unusedImport` disparaît, les usages deviennent
 *   non résolus (si l'absence est prouvable) ;
 * - **M2 (type)** : insertion de `val zzType: Int = "texte"` — attendu
 *   `kt.typeMismatch` ;
 * - **M3 (nom)** : insertion de `val zzNom = zzInconnu` — attendu
 *   `kt.unresolved` (nom prouvablement absent d'un univers fermé).
 *
 * Chaque mutant est confronté à l'oracle K2 (le MÊME contenu muté) :
 * DÉTECTÉ = le moteur émet une ERREUR nouvelle sur une ligne d'erreur
 * nouvelle de l'oracle ; SILENCIEUX sinon. Le **taux de silence par
 * famille** est le résultat — la couverture se mesure, elle ne se
 * déclare pas.
 *
 * ## R5 — rapport uniquement (baseline future)
 *
 * Aucune cible n'est affirmée à ce stade : les taux mesurés ici deviennent
 * la baseline de la propriété (vague V-A, ADR 0135). Sanité toujours
 * active : jamais d'exception, l'oracle répond toujours.
 */
class P3VraisPositifsParMutationTest {

    /** Une mutation : libellé, famille attendue, texte final du document. */
    private data class Mutant(val famille: String, val uri: String, val texte: String, val original: String)

    /** Les fichiers du corpus de mutation : les 4 mondes + 10 fichiers auto-corpus. */
    private fun corpusMutation(): List<Pair<String, String>> {
        val racineFidelite = SocleCoherence.racineDepot.resolve("kotlin/src/test/resources/fidelite")
        val fichiers = mutableListOf<Pair<String, String>>()
        if (Files.isDirectory(racineFidelite)) {
            Files.list(racineFidelite).use { flux ->
                flux.filter { it.fileName.toString().endsWith(".kt") && it.fileName.toString() != "temoin-positif.kt" }
                    .sorted()
                    .forEach { fichiers += it.fileName.toString() to Files.readString(it) }
            }
        }
        val auto = SocleCoherence.racinesAutoCorpus().firstOrNull()
            ?.let { racine ->
                Files.walk(racine).use { flux ->
                    flux.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                        .sorted()
                        .limit(10)
                        .toList()
                }
            } ?: emptyList()
        for (fichier in auto) {
            fichiers += "auto-${fichier.fileName}" to Files.readString(fichier)
        }
        assertTrue(fichiers.isNotEmpty(), "le corpus de mutation (mondes + auto-corpus) doit exister")
        return fichiers
    }

    @Test
    fun `rappel-de-vrais-positifs-par-mutations-à-la-bonne-plage`() {
        val racine = Files.createTempDirectory("p3-mutations")
        val sources = corpusMutation()
        // Copie du corpus dans le répertoire temporaire (univers FERMÉ).
        val fichiersCopies = sources.mapIndexed { indice, (nom, texte) ->
            val fichier = racine.resolve("f$indice-${nom.replace('/', '_')}")
            Files.writeString(fichier, texte)
            fichier
        }
        val jars = SocleCoherence.assurerPlancherStdlib(SocleCoherence.jarsAutoCorpus())
        val session = SocleCoherence.monterSession(listOf(racine), jars)
        try {
            session.rafraichirSources()
            val detectes = mutableMapOf<String, Int>()
            val silencieux = mutableMapOf<String, Int>()
            for ((indice, paire) in sources.withIndex()) {
                val (nom, texte) = paire
                val fichier = fichiersCopies[indice]
                val uri = fichier.toUri().toString()
                val mutants = mutableListOf<Mutant>()
                texte.lines().indexOfFirst { it.trimStart().startsWith("import ") }
                    .takeIf { it >= 0 }?.let { ligneImport ->
                        val remplace = texte.lines().toMutableList().apply { removeAt(ligneImport) }
                        mutants += Mutant("import-supprimé", uri, remplace.joinToString("\n"), texte)
                    }
                mutants += Mutant("type-erroné", uri, texte + "\nprivate fun zzZone() { val zzType: Int = \"texte\" }\n", texte)
                mutants += Mutant("nom-inconnu", uri, texte + "\nprivate fun zzZone() { val zzNom = zzInconnu }\n", texte)
                for (mutant in mutants) {
                    val oracleAvant = SocleCoherence.oracleK2(listOf(fichier), jars)[fichier].orEmpty().map { it.ligne }.toSet()
                    Files.writeString(fichier, mutant.texte)
                    session.ouvrir(uri, mutant.texte)
                    val lignesMoteur = session.diagnostics(uri)
                        .filter { it.severite == SeveriteKotlin.ERREUR }
                        .map { 1 + mutant.texte.substring(0, it.plage.debut.coerceAtMost(mutant.texte.length)).count { c -> c == '\n' } }
                        .toSet()
                    session.fermer(uri)
                    val oracleApres = SocleCoherence.oracleK2(listOf(fichier), jars)[fichier].orEmpty().map { it.ligne }.toSet()
                    val nouvellesLignesOracle = oracleApres - oracleAvant
                    val nouvellesLignesMoteur = lignesMoteur - oracleAvant
                    val detecte = nouvellesLignesOracle.isNotEmpty() && nouvellesLignesMoteur.isNotEmpty()
                    if (detecte) detectes.merge(mutant.famille, 1, Int::plus) else silencieux.merge(mutant.famille, 1, Int::plus)
                    println(
                        "P3 $nom [${mutant.famille}]: ${if (detecte) "DÉTECTÉ" else "silencieux"} " +
                            "(nouvelles lignes oracle=${nouvellesLignesOracle.size}, moteur=${nouvellesLignesMoteur.size})",
                    )
                    Files.writeString(fichier, mutant.original)
                }
            }
            for (famille in (detectes.keys + silencieux.keys).distinct().sorted()) {
                val total = (detectes[famille] ?: 0) + (silencieux[famille] ?: 0)
                val taux = if (total == 0) 0.0 else (silencieux[famille] ?: 0) * 100.0 / total
                println("P3 TAUX DE SILENCE [$famille]: ${String.format("%.1f", taux)} % ($total mutants)")
            }
        } finally {
            session.close()
        }
    }
}

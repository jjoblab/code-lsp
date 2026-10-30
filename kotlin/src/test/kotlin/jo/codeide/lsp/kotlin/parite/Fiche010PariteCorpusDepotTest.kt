package jo.codeide.lsp.kotlin.parite

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import jo.codeide.lsp.kotlin.parse.ParseurKotlin
import org.junit.jupiter.api.Test

/**
 * Fiche 010 — PariteCorpusDepotTest : suite de parité « dépôt » (D2 amont).
 *
 * TOUT fichier `.kt` du dépôt code-lsp est analysé par la grammaire
 * vendorée du compilateur — chaque fichier doit produire un arbre SANS
 * marqueur d'erreur (le dépôt compile en CI, la grammaire du compilateur
 * doit l'accepter telle quelle), et la FORME globale des arbres est épinglée
 * par un digeste de base : toute dérive de grammaire — re-sync amont
 * (`scripts/vendor-grammaire-kotlin.sh`, ADR 0121) ou régression locale —
 * déplace les digests et se voit immédiatement.
 *
 * Le digeste porte la FORME de l'arbre (productions + genres de jetons, pas
 * les offsets) : un renommage ou un reformatage sans changement de forme ne
 * déplace PAS la baseline ; un changement de grammaire la déplace. La
 * baseline (`src/test/resources/parite/digeste-corpus.txt`) se met à jour
 * DÉLIBÉRÉMENT :
 *
 * `./gradlew :kotlin:test --tests '*Fiche010PariteCorpusDepotTest*' -Dparite.majBase=true --rerun-tasks`
 *
 * **`--rerun-tasks` OBLIGATOIRE** (leçon de l'incident v1.22.0 de l'ancien
 * dépôt, reprise du cahier) : la régénération est un EFFET DE BORD d'une
 * tâche de test — sans `--rerun-tasks`, Gradle peut déclarer la tâche
 * UP-TO-DATE et NE PAS l'exécuter : la baseline resterait alors PÉRIMÉE en
 * silence, la CI suivante échouerait sur un digeste qui ne correspond plus
 * à rien. TOUJOURS vérifier que le fichier a bien changé — le diff fait
 * partie de la revue du re-sync.
 *
 * Racine du corpus : le dépôt (remontée depuis le répertoire de travail du
 * test jusqu'à `settings.gradle.kts`) ; `-Dparite.racineCorpus=<chemin>` la
 * force (utile pour un sweep externe, ex. un checkout du compilateur).
 * Répertoires exclus : `.git`, `build`, `.gradle`, `.kotlin`, `.oracle`,
 * artefacts de bancs (`jarres`, `bascule`, `kit-bascule`).
 */
class Fiche010PariteCorpusDepotTest {

    private companion object {
        private val EXCLUSIONS = setOf(
            ".git", "build", ".gradle", ".kotlin", ".oracle",
            "jarres", "bascule", "kit-bascule",
        )

        /** Le parseur est sans état : une instance partagée suffit. */
        private val PARSEUR = ParseurKotlin()

        /** Racine du corpus : le dépôt (ou la racine forcée par propriété). */
        private val RACINE: Path = run {
            val forcee = System.getProperty("parite.racineCorpus", "").trim()
            if (forcee.isNotEmpty()) {
                return@run Path.of(forcee).toAbsolutePath().normalize()
            }
            var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
            while (dir != null && !Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
                dir = dir.parent
            }
            checkNotNull(dir) {
                "settings.gradle.kts introuvable en remontant depuis " +
                    Path.of(System.getProperty("user.dir")).toAbsolutePath() +
                    " — forcer la racine avec -Dparite.racineCorpus=<chemin>"
            }
        }

        /** Les fichiers du corpus, triés (ordre stable → baseline stable). */
        private val FICHIERS: List<Path> =
            Files.walk(RACINE).use { flux ->
                flux.filter { chemin -> Files.isRegularFile(chemin) }
                    .filter { chemin -> chemin.toString().endsWith(".kt") }
                    .filter { chemin -> chemin.none { partie -> EXCLUSIONS.contains(partie.toString()) } }
                    .sorted()
                    .toList()
            }

        /** Chaque fichier du corpus analysé UNE fois (partagé par les deux tests) :
         * chemin, nombre de marqueurs d'erreur, digeste structurel. */
        private val ANALYSES: List<Triple<Path, Int, String>> = FICHIERS.map { fichier ->
            val resultat = PARSEUR.analyse(Files.readString(fichier))
            Triple(fichier, resultat.nombreMarqueursErreur, resultat.digesteStructurel)
        }

        /** La baseline digeste, une ligne par fichier : `<sha256>  <chemin relatif>`. */
        private val LIGNES_BASELINE: List<String> = ANALYSES
            .map { (fichier, _, digeste) -> "$digeste  ${RACINE.relativize(fichier)}" }
            .sorted()

        /** Emplacement de la baseline dans les sources du module. */
        private val FICHIER_BASELINE: Path = run {
            var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
            while (dir != null && !Files.isDirectory(dir.resolve("src/test/resources/parite"))) {
                dir = dir.parent
            }
            checkNotNull(dir) { "src/test/resources/parite introuvable en remontant depuis l'emplacement de test" }
                .resolve("src/test/resources/parite/digeste-corpus.txt")
        }
    }

    @Test
    fun `tout-le-depôt-se-parse-sans-marqueur-d'erreur`() {
        val fautifs = ANALYSES.mapNotNull { (fichier, erreurs, _) ->
            if (erreurs > 0) "${RACINE.relativize(fichier)} : $erreurs marqueur(s) d'erreur" else null
        }
        assertTrue(
            fautifs.isEmpty(),
            "le dépôt compile en CI : la grammaire du compilateur doit l'accepter tel quel — " +
                "${fautifs.size} fichier(s) avec marqueurs d'erreur :\n${fautifs.joinToString("\n")}",
        )
    }

    @Test
    fun `le-digeste-structurel-du-corpus-correspond-a-la-baseline`() {
        val majBase = System.getProperty("parite.majBase", "false").toBoolean()
        if (majBase) {
            FICHIER_BASELINE.writeText(LIGNES_BASELINE.joinToString("\n") + "\n")
            println("Baseline digeste RÉGÉNÉRÉE (${LIGNES_BASELINE.size} fichiers) : $FICHIER_BASELINE")
            println("  → le diff de baseline fait partie de la revue (re-sync amont ?) — cf. KDoc.")
            return
        }
        assertTrue(
            Files.isRegularFile(FICHIER_BASELINE),
            "baseline absente ($FICHIER_BASELINE) — première signature : " +
                "./gradlew :kotlin:test --tests '*Fiche010PariteCorpusDepotTest*' " +
                "-Dparite.majBase=true --rerun-tasks",
        )
        val attendue = Files.readAllLines(FICHIER_BASELINE).filter { it.isNotBlank() }
        assertEquals(
            attendue.size,
            LIGNES_BASELINE.size,
            "nombre de fichiers du corpus ≠ baseline (corpus : ${LIGNES_BASELINE.size}, " +
                "baseline : ${attendue.size}) — fichiers nouveaux/supprimés ? " +
                "Re-signer : -Dparite.majBase=true --rerun-tasks",
        )
        val attendueMap = attendue.associateBy(
            { it.substringAfter("  ") },
            { it.substringBefore("  ") },
        )
        val divergences = LIGNES_BASELINE.mapNotNull { ligne ->
            val chemin = ligne.substringAfter("  ")
            val digeste = ligne.substringBefore("  ")
            when (val attendu = attendueMap[chemin]) {
                null -> "$chemin : absent de la baseline (nouveau fichier)"
                digeste -> null
                else -> "$chemin : forme d'arbre divergente (re-sync amont ? régression locale ?)"
            }
        }
        assertEquals(
            emptyList(),
            divergences,
            "digeste structurel du corpus ≠ baseline — toute divergence doit être EXPLIQUÉE " +
                "(changement de grammaire amont vérifié, ou régression) avant re-signature " +
                "(-Dparite.majBase=true --rerun-tasks) :\n${divergences.joinToString("\n")}",
        )
    }
}

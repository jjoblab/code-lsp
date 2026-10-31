// CodeIDE — banc des RESSOURCES de la vague V-B (ADR 0020/0021 — critères
// de sortie : « scan res/ p95 < 150 ms JVM », « MAJ ciblée < 5 ms »,
// « budget CIDX < 2 Mo/projet moyen »).
//
// Usage : ./gradlew :java:benchRessources
//
// Mesures (R5 : chiffres réels, médianes calculées par le banc, jamais
// simulés) :
//  - scan COMPLET d'un `res/` : p50/p95 sur 9 passes (1 échauffement) ;
//  - MAJ CIBLÉE d'un fichier (rafraichirFichier) : p50/p95 sur 50 passes ;
//  - PESON compact du référentiel (octetsCompacts — futur segment CIDX).
//
// Cibles mesurées, de la plus légère à la plus lourde :
//  1. la fixture mini (java/src/test/resources/projetandroid/mini) ;
//  2. le banc réel monip (bancs/projet-android — si présent) ;
//  3. les projets du corpus réel (bancs/corpus-android/<projet> — chaque
//     module :app trouvé par convention src/main/res, si le corpus est
//     téléchargé ; absent = mesuré sur ce qui existe, raison affichée).
//
// GATES (échec du banc) : p95 scan > 150 ms sur le res/ LE PLUS LOURD
// mesuré ; MAJ ciblée p95 > 5 ms ; peson > 2 Mo par projet. Les seuils
// JVM sont les seuils ADR (ART ≈ ×5 — budget appareil documenté gabarit 07).
package jo.codeide.lsp.java.banc

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.java.projetandroid.ModeleAndroidDerive
import jo.codeide.lsp.java.projetandroid.ReferentielRessources
import kotlin.math.roundToInt
import kotlin.system.measureNanoTime

fun main() {
    val cible = Path.of("../bancs").takeIf { Files.isDirectory(it) } ?: Path.of("../../bancs")
    val modules = mutableListOf<Pair<String, Path>>()

    // 1. Fixture mini.
    val mini = Path.of("src/test/resources/projetandroid/mini/app/src/main/java")
    if (Files.isDirectory(mini)) {
        ModeleAndroidDerive.deriver(listOf(mini))?.dossiersRes?.firstOrNull()?.let {
            modules += "mini (fixture)" to it
        }
    }

    // 2. Banc monip.
    val monip = cible.resolve("projet-android/app/src/main/java")
    if (Files.isDirectory(monip)) {
        ModeleAndroidDerive.deriver(listOf(monip))?.dossiersRes?.firstOrNull()?.let {
            modules += "monip (banc)" to it
        }
    }

    // 3. Corpus réel — chaque module portant un res/.
    val corpus = cible.resolve("corpus-android")
    if (Files.isDirectory(corpus)) {
        Files.walk(corpus).use { flux ->
            flux.filter { dossier -> dossier.fileName?.toString() == "res" && Files.isDirectory(dossier) }
                .filter { res -> res.parent?.fileName?.toString() == "main" }
                .sorted()
                .forEach { res ->
                    val projet = corpus.relativize(res).first().toString()
                    modules += "corpus/$projet" to res
                }
        }
    }
    if (modules.isEmpty()) {
        println("BANC RESSOURCES : AUCUNE cible mesurable (fixtures absentes) — ignoré.")
        return
    }

    var echec = false
    var p95MaxGlobal = 0.0
    var pesonMaxGlobal = 0L
    val compactMaxParProjet = mutableMapOf<String, Long>()

    println("BANC RESSOURCES V-B — ${modules.size} res/ mesurés")
    println("%-28s %8s %9s %9s %10s %10s".format("cible", "fichiers", "scan p50", "scan p95", "MAJ p95", "peson"))
    for ((nom, res) in modules) {
        val nbFichiers = Files.walk(res).use { flux -> flux.filter { Files.isRegularFile(it) && it.toString().endsWith(".xml") }.count() }
        if (nbFichiers == 0L) continue

        // Scan complet : 1 échauffement + 9 passes mesurées.
        ReferentielRessources(listOf(res))
        val scans = (0 until 9).map {
            measureNanoTime { ReferentielRessources(listOf(res)) } / 1_000_000.0
        }
        val p50 = mediane(scans)
        val p95 = scans.sorted()[((scans.size - 1) * 0.95).roundToInt().coerceAtMost(scans.size - 1)]

        // MAJ ciblée : re-scan du PLUS GROS XML du res/.
        val plusGros = Files.walk(res).use { flux ->
            flux.filter { fichier: Path -> Files.isRegularFile(fichier) && fichier.toString().endsWith(".xml") }
                .max(java.util.Comparator.comparingLong { fichier: Path -> Files.size(fichier) })
                .orElse(null)
        }
        val referentiel = ReferentielRessources(listOf(res))
        val majs = (0 until 50).map {
            measureNanoTime { referentiel.rafraichirFichier(plusGros!!) } / 1_000_000.0
        }
        val majP95 = majs.sorted()[((majs.size - 1) * 0.95).roundToInt().coerceAtMost(majs.size - 1)]

        // Peson compact.
        val peson = referentiel.octetsCompacts().size.toLong()
        val projet = nom.substringBefore('/')
        compactMaxParProjet[projet] = maxOf(compactMaxParProjet[projet] ?: 0L, peson)

        println(
            "%-28s %8d %8.1fms %8.1fms %9.2fms %8d o".format(nom, nbFichiers, p50, p95, majP95, peson),
        )
        p95MaxGlobal = maxOf(p95MaxGlobal, p95)
        pesonMaxGlobal = maxOf(pesonMaxGlobal, peson)
        if (p95 > 150.0) {
            println("  ÉCHEC : scan p95 ${p95}ms > 150 ms (ADR 0020)")
            echec = true
        }
        if (majP95 > 5.0) {
            println("  ÉCHEC : MAJ ciblée p95 ${majP95}ms > 5 ms (ADR 0020)")
            echec = true
        }
    }
    for ((projet, peson) in compactMaxParProjet) {
        if (peson > 2L * 1024 * 1024) {
            println("ÉCHEC : peson $projet = $peson o > 2 Mo (budget CIDX)")
            echec = true
        }
    }
    println(
        "— p95 scan max %.1f ms (budget 150) · peson max %d o (budget 2 Mo/projet) · %d projets".format(
            p95MaxGlobal,
            pesonMaxGlobal,
            compactMaxParProjet.size,
        ),
    )
    if (echec) {
        kotlin.system.exitProcess(1)
    }
    println("BANC RESSOURCES : OK (budgets ADR tenus).")
}

/** Médiane d'un échantillon (R5 : calculée, jamais estimée). */
private fun mediane(valeurs: List<Double>): Double {
    val triees = valeurs.sorted()
    return triees[triees.size / 2]
}

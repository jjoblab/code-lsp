// CodeIDE — banc de passe BASCULE SUR APPAREIL (étage T — §8.5 du plan de
// réécriture : « bascule uniquement à parité : 0 comportement perdu contre
// l'oracle (banc croisé ADR 0016), latences §4 tenues sur appareil »).
//
// RÔLE : mesure PURE pour l'appareil (moto g06 / Termux — le protocole du
// gabarit `bancs/GABARIT-BASCULE-APPAREIL.md`, R5 : jamais simulée). Ce banc
// ne compare AUCUNE baseline (les bornes de non-régression sont l'affaire
// de la CI JVM — benchQualite/benchParite) : il rejoue le corpus de parité
// sur l'appareil et rend (a) la COUVERTURE DU CAHIER position par position
// (la parité de comportement constatée sur l'appareil), (b) les latences
// p50/p95 par genre de contexte et globales, (c) le verdict zéro-saut de la
// succession de frappe.
//
// Usage (assemblé par scripts/kit-bascule.sh — jamais à la main) :
//   java -Xmx2g -cp <kit> jo.codeide.lsp.java.banc.BancBasculeAppareilKt
package jo.codeide.lsp.java.banc

import jo.codeide.lsp.java.MoteurJava
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** Marqueur de caret du corpus (convention du cahier). */
private const val MARQUEUR = '¦'

/** Échauffements avant mesure (R2 — le gel se constitue comme en session). */
private const val ECHAUFFEMENTS = 2

/** Passes mesurées (R2 — médiane et p95 sur un échantillon borné). */
private const val PASSES = 5

fun main() {
    val dossier = Files.createTempDirectory("bascule-appareil")
    val racine = Files.createDirectories(dossier.resolve("src/demo"))
    racine.resolve("Main.java").writeText(CorpusQualiteCompletion.SOURCE_MAIN)
    racine.resolve("Widget.java").writeText(CorpusQualiteCompletion.SOURCE_WIDGET)
    val moteur = MoteurJava(racines = listOf(racine.parent))

    fun completer(source: String) = run {
        val marque = source.indexOf(MARQUEUR)
        moteur.completer("/demo/Main.java", source.replace(MARQUEUR.toString(), ""), marque)
    }

    // --- Échauffement (caches, index, gel de la succession comme en session) ---
    repeat(ECHAUFFEMENTS) {
        CorpusQualiteCompletion.positions.forEach { completer(it.source) }
        CorpusQualiteCompletion.successionFrappe.forEach { completer(it) }
    }

    // --- Couverture du cahier sur l'appareil (la parité de comportement) ---
    println("== couverture du cahier (corpus ${CorpusQualiteCompletion.positions.size} positions)")
    var couverts = 0
    for (position in CorpusQualiteCompletion.positions) {
        val libelles = completer(position.source).items.map { it.libelle }
        val manquants = position.attendus.filter { it !in libelles }
        if (manquants.isEmpty()) couverts++
        println(
            "  %-24s %s%s".format(
                position.nom,
                if (manquants.isEmpty()) "✓" else "✗ manquants=",
                manquants.joinToString(","),
            ),
        )
    }
    println("== couverture cahier : %d/%d".format(couverts, CorpusQualiteCompletion.positions.size))

    // --- Latences par genre de contexte (p50/p95 sur PASSES passes mesurées) ---
    println("== latences de complétion par genre ($PASSES passes mesurées après $ECHAUFFEMENTS échauffements)")
    val echantillons = mutableMapOf<String, MutableList<Long>>()
    val tous = mutableListOf<Long>()
    repeat(PASSES) {
        for (position in CorpusQualiteCompletion.positions) {
            val debut = System.nanoTime()
            completer(position.source)
            val micros = (System.nanoTime() - debut) / 1_000
            echantillons.getOrPut(position.genreAttendu.toString()) { mutableListOf() }.add(micros)
            tous.add(micros)
        }
    }
    for ((genre, valeurs) in echantillons.toSortedMap()) {
        println("  %-16s p50=%7.2f ms  p95=%7.2f ms  max=%7.2f ms (n=%d)".format(genre, p50(valeurs), p95(valeurs), valeurs.max() / 1000.0, valeurs.size))
    }
    println("  %-16s p50=%7.2f ms  p95=%7.2f ms  max=%7.2f ms (n=%d)".format("GLOBAL", p50(tous), p95(tous), tous.max() / 1000.0, tous.size))

    // --- Verdict « zéro saut de liste » (contrat gel D8, cf. benchQualite) ---
    var sauts = 0
    var tetePrecedente: List<String> = emptyList()
    for (source in CorpusQualiteCompletion.successionFrappe) {
        val tete = completer(source).items.map { it.libelle }.take(8)
        for ((rang, libelle) in tetePrecedente.withIndex()) {
            if (libelle !in tete) continue
            if (tete.indexOf(libelle) > rang) sauts++
        }
        tetePrecedente = tete
    }
    println("== sauts de liste : $sauts (contrat gel : 0 exigé)")
}

/** Percentile par interpolation discrète (même convention que benchQualite). */
private fun p50(valeurs: List<Long>): Double {
    val t = valeurs.sorted()
    return t[(t.size / 2).coerceAtMost(t.size - 1)] / 1000.0
}

private fun p95(valeurs: List<Long>): Double {
    val t = valeurs.sorted()
    return t[((0.95 * t.size).toInt()).coerceAtMost(t.size - 1)] / 1000.0
}

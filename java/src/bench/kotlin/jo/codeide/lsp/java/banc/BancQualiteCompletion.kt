// CodeIDE — banc de QUALITÉ de complétion (étage N2, critère de sortie —
// PLAN-REECRITURE §4 : « banc qualité : recall ≥ baseline, top-1/MRR +5 pts
// vs baseline, p95 ≤ baseline, zéro saut de liste » ; ADR 0016 option 1 :
// corpus fixe versionné + baseline JSON signée + CI qui ÉCHOUE en régression).
//
// Usage : ./gradlew :java:benchQualite          (comparaison vs baseline)
//         ./gradlew :java:benchQualite -Presigner   (re-signature, PR dédiée)
//
// Deux arrangements sur le MÊME corpus :
//  - « d8 »    : la chaîne standard de l'étage (Correspondance → Nature →
//                Longueur → Alphabetique) — le moteur livré ;
//  - « naïf »  : la chaîne alphabétique SEULE — le statu quo sans design
//                (la baseline de première signature : le design D8 doit la
//                battre de ≥ 5 points top-1/MRR pour valider ADR 0012).
//
// Métriques (corpus [CorpusQualiteCompletion], 18 positions étiquetées) :
//  - recall  : fraction des positions dont TOUS les libellés attendus
//              figurent dans la liste rendue ;
//  - top-1   : fraction des positions dont le PREMIER item est le gagnant
//              désigné ;
//  - MRR     : moyenne de l'inverse du rang du gagnant désigné ;
//  - p95     : latence de complétion d8 (5 passes mesurées après 2 passes
//              d'échauffement — R2 bruit de mesure : la borne comparative
//              intègre l'incertitude) ;
//  - sauts   : contrat « zéro saut de liste » — sur la succession de
//              frappe (même clé de gel, préfixe s'affinant), une tête
//              gelée qui survit doit garder SON rang exact.
//
// Le corpus est DÉTERMINISTE (jrt seul, pas d'android.jar) : recall/top-1/
// MRR sont reproductibles au bit près — tolérance de comparaison NULLE ;
// seule la latence porte une tolérance documentée.
package jo.codeide.lsp.java.banc

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.completion.ChaineClassement
import jo.codeide.lsp.java.completion.GenreContexte
import jo.codeide.lsp.java.completion.PeseurAlphabetique
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

/** Les métriques d'un arrangement sur le corpus. */
class Mesures(
    val recall: Double,
    val top1: Double,
    val mrr: Double,
)

/** Entrée de diagnostic d'une position (imprimée pour chaque run). */
private data class LignePosition(
    val nom: String,
    val genre: GenreContexte,
    val genreAttendu: GenreContexte,
    val manquants: List<String>,
    val rangGagnant: Int,
    val premier: String,
)

fun main(args: Array<String>) {
    val resigner = "--resigner" in args
    val cheminBaseline = System.getProperty("banc.baseline")
        ?: "/home/z/my-project/bancs/baselines/completion.json"
    val echecs = mutableListOf<String>()

    // --- Montage du projet corpus (racines sources réelles — Widget est
    // résolu depuis la racine, comme une vraie frappe) ---
    val dossier = Files.createTempDirectory("banc-qualite")
    val racine = Files.createDirectories(dossier.resolve("src/demo"))
    racine.resolve("Main.java").writeText(CorpusQualiteCompletion.SOURCE_MAIN)
    racine.resolve("Widget.java").writeText(CorpusQualiteCompletion.SOURCE_WIDGET)

    val moteurD8 = MoteurJava(racines = listOf(dossier.resolve("src")))
    val moteurNaif = MoteurJava(
        racines = listOf(dossier.resolve("src")),
        chaineCompletion = ChaineClassement(listOf(PeseurAlphabetique())),
    )

    // --- Passage de mesure (les deux moteurs partagent le corpus) ---
    fun mesurer(moteur: MoteurJava, etiquette: String): Pair<Mesures, List<LignePosition>> {
        val lignes = mutableListOf<LignePosition>()
        var rappels = 0
        var premiers = 0
        var sommeMrr = 0.0
        for (position in CorpusQualiteCompletion.positions) {
            val marque = position.source.indexOf('¦')
            val texte = position.source.replace("¦", "")
            val reponse = moteur.completer("/demo/Main.java", texte, marque)
            val libelles = reponse.items.map { it.libelle }
            val manquants = position.attendus.filter { it !in libelles }
            if (manquants.isEmpty()) rappels++
            val rang = libelles.indexOf(position.gagnant)
            if (rang == 0) premiers++
            if (rang >= 0) sommeMrr += 1.0 / (rang + 1)
            lignes.add(LignePosition(position.nom, reponse.genre, position.genreAttendu, manquants, rang, libelles.firstOrNull() ?: "∅"))
        }
        val total = CorpusQualiteCompletion.positions.size
        val mesures = Mesures(rappels.toDouble() / total, premiers.toDouble() / total, sommeMrr / total)
        println("== arrangement « $etiquette » : recall=%.3f top1=%.3f mrr=%.3f".format(mesures.recall, mesures.top1, mesures.mrr))
        for (ligne in lignes) {
            val alerte = if (ligne.manquants.isNotEmpty() || ligne.rangGagnant != 0 || ligne.genre != ligne.genreAttendu) " ←" else ""
            println(
                "  %-22s genre=%-14s (attendu %-14s) gagnant rang=%-3d premier=%s%s".format(
                    ligne.nom, ligne.genre, ligne.genreAttendu, ligne.rangGagnant, ligne.premier, alerte,
                ),
            )
        }
        return mesures to lignes
    }

    val (d8, lignesD8) = mesurer(moteurD8, "d8")
    val (naif, _) = mesurer(moteurNaif, "naïf")

    // --- Contrôle de santé : le verdict de genre est DÉTERMINISTE ---
    for (ligne in lignesD8) {
        if (ligne.genre != ligne.genreAttendu) {
            echecs.add("verdict non déterministe sur ${ligne.nom} : ${ligne.genre} ≠ ${ligne.genreAttendu} attendu")
        }
    }

    // --- Latence p95 (d8, 2 passes d'échauffement + 5 passes mesurées) ---
    repeat(2) { chauffe ->
        for (position in CorpusQualiteCompletion.positions) {
            val marque = position.source.indexOf('¦')
            moteurD8.completer("/demo/Main.java", position.source.replace("¦", ""), marque)
        }
        if (chauffe == 1) {
            // la succession de frappe est JOUÉE pendant l'échauffement : le
            // gel se constitue, l'état est celui d'une vraie session.
            for (source in CorpusQualiteCompletion.successionFrappe) {
                val marque = source.indexOf('¦')
                moteurD8.completer("/demo/Main.java", source.replace("¦", ""), marque)
            }
        }
    }
    val echantillons = mutableListOf<Long>()
    repeat(5) {
        for (position in CorpusQualiteCompletion.positions) {
            val marque = position.source.indexOf('¦')
            val texte = position.source.replace("¦", "")
            val debut = System.nanoTime()
            moteurD8.completer("/demo/Main.java", texte, marque)
            echantillons.add((System.nanoTime() - debut) / 1_000)
        }
    }
    val tries = echantillons.sorted()
    val p95Us = tries[((0.95 * tries.size).toInt()).coerceAtMost(tries.size - 1)]
    val p95Ms = p95Us / 1000.0
    println("== latence d8 : p95=%.2f ms (min=%.2f, max=%.2f, %d échantillons)".format(p95Ms, tries.first() / 1000.0, tries.last() / 1000.0, tries.size))

    // --- Contrat « zéro saut de liste » : une tête gelée qui SURVIT à
    // l'affinement du préfixe ne peut ni être DÉPASSÉE par un nouveau venu,
    // ni descendre — elle peut seulement MONTER par disparition des items
    // au-dessus d'elle (le propre filtre de l'utilisateur les retire). Un
    // saut = un rang qui AUGMENTE.
    var sauts = 0
    var tetePrecedente: List<String> = emptyList()
    for (source in CorpusQualiteCompletion.successionFrappe) {
        val marque = source.indexOf('¦')
        val reponse = moteurD8.completer("/demo/Main.java", source.replace("¦", ""), marque)
        val libelles = reponse.items.map { it.libelle }
        val tete = libelles.take(8)
        for ((rang, libelle) in tetePrecedente.withIndex()) {
            if (libelle !in tete) continue // ne survit pas au filtre : légal
            val nouveauRang = tete.indexOf(libelle)
            if (nouveauRang > rang) {
                sauts++
                println("  SAUT : « $libelle » rang $rang → $nouveauRang (dépassée)")
            }
        }
        tetePrecedente = tete
    }
    println("== sauts de liste : $sauts (contrat gel : 0 exigé)")

    // --- Preuve de valeur du design D8 (vs la baseline naïve) ---
    if (d8.recall < naif.recall) echecs.add("recall d8 (%.3f) < naïf (%.3f)".format(d8.recall, naif.recall))
    if (d8.top1 < naif.top1 + 0.05) {
        echecs.add("top-1 d8 (%.3f) < naïf + 5 pts (%.3f) — ADR 0012 non validé".format(d8.top1, naif.top1 + 0.05))
    }
    if (d8.mrr < naif.mrr + 0.05) {
        echecs.add("MRR d8 (%.3f) < naïf + 5 pts (%.3f) — ADR 0012 non validé".format(d8.mrr, naif.mrr + 0.05))
    }
    if (sauts != 0) echecs.add("$sauts saut(s) de liste — contrat gel cassé")
    if (p95Ms > SEUIL_P95_MS_ABSOLU) echecs.add("p95 %.2f ms > borne absolue %.2f ms".format(p95Ms, SEUIL_P95_MS_ABSOLU))

    // --- Comparaison à la baseline signée (régression = échec CI) ---
    val baseline = lireBaseline(cheminBaseline)
    if (baseline == null) {
        println("== baseline absente ($cheminBaseline) : première signature")
        if (!resigner) echecs.add("baseline absente — signer par PR dédiée : ./gradlew :java:benchQualite -Presigner")
    } else {
        val d8Base = baseline.getValue("d8")
        if (d8.recall < d8Base.getValue("recall")) echecs.add("recall régressé : %.3f < baseline %.3f".format(d8.recall, d8Base.getValue("recall")))
        if (d8.top1 < d8Base.getValue("top1")) echecs.add("top-1 régressé : %.3f < baseline %.3f".format(d8.top1, d8Base.getValue("top1")))
        if (d8.mrr < d8Base.getValue("mrr")) echecs.add("MRR régressé : %.3f < baseline %.3f".format(d8.mrr, d8Base.getValue("mrr")))
        val p95Base = d8Base.getValue("p95_ms")
        val borne = maxOf(SEUIL_P95_MS_ABSOLU, p95Base * 1.5)
        if (p95Ms > borne) echecs.add("p95 régressé : %.2f ms > borne %.2f ms (baseline %.2f × 1,5)".format(p95Ms, borne, p95Base))
    }

    if (resigner) {
        ecrireBaseline(cheminBaseline, d8, naif, p95Ms)
        println("== baseline SIGNÉE : $cheminBaseline (commit = signature, re-baseline par PR dédiée)")
    }

    if (echecs.isNotEmpty()) {
        println("== ÉCHEC DU BANC DE QUALITÉ (${echecs.size}) :")
        echecs.forEach { println("  ✗ $it") }
        exitProcess(1)
    }
    println("== BANC DE QUALITÉ VERT (corpus ${CorpusQualiteCompletion.positions.size} positions)")
}

/** Borne absolue de latence p95 par complétion (R2 : large, documentée). */
private const val SEUIL_P95_MS_ABSOLU = 50.0

/** Écrit la baseline JSON (une métrique par ligne — diff lisible en PR). */
private fun ecrireBaseline(chemin: String, d8: Mesures, naif: Mesures, p95Ms: Double) {
    val fichier = Path.of(chemin)
    Files.createDirectories(fichier.parent)
    val contenu = """
        {
          "note": "Baseline du banc de qualité de complétion (ADR 0016 — signée par le commit qui la porte ; re-baseline par PR dédiée uniquement). 'naif' = chaîne alphabétique seule (statu quo sans design D8) ; 'd8' = chaîne livrée. Tolérance latence : p95 ≤ max(50 ms, baseline × 1,5).",
          "corpus": "completion-n2 v1 (18 positions)",
          "signee": "${LocalDate.now()}",
          "d8": {
            "recall": ${"%.3f".format(d8.recall)},
            "top1": ${"%.3f".format(d8.top1)},
            "mrr": ${"%.3f".format(d8.mrr)},
            "p95_ms": ${"%.2f".format(p95Ms)}
          },
          "naif": {
            "recall": ${"%.3f".format(naif.recall)},
            "top1": ${"%.3f".format(naif.top1)},
            "mrr": ${"%.3f".format(naif.mrr)}
          }
        }
    """.trimIndent()
    fichier.writeText(contenu + "\n")
}

/**
 * Lit la baseline JSON (schéma plat à deux niveaux — analyseur minimal
 * dédié, sans dépendance).
 *
 * @return la map « section → (métrique → valeur) », ou null si absente.
 */
private fun lireBaseline(chemin: String): Map<String, Map<String, Double>>? {
    val fichier = Path.of(chemin)
    if (!Files.isRegularFile(fichier)) return null
    val texte = fichier.readText()
    val resultats = mutableMapOf<String, MutableMap<String, Double>>()
    var section: String? = null
    for (ligneBrute in texte.lines()) {
        val ligne = ligneBrute.trim()
        when {
            ligne.startsWith("\"d8\"") || ligne.startsWith("\"naif\"") ->
                section = ligne.substringBefore(':').trim('"')
            ligne.contains("\":") && section != null -> {
                val cle = ligne.substringBefore(':').trim().trim('"')
                val valeur = ligne.substringAfter(':').substringBefore(',').substringBefore('}').trim()
                valeur.toDoubleOrNull()?.let { valeurD ->
                    resultats.getOrPut(section!!) { mutableMapOf() }[cle] = valeurD
                }
            }
        }
    }
    return if (resultats.isEmpty()) null else resultats
}

// CodeIDE — banc de QUALITÉ de complétion Kotlin (étage 3, critère de
// sortie — synthèse §4 : « banc : recall ≥ baseline, top-1/MRR +5 pts,
// p95 ≤ baseline » ; ADR 0119 option 1, ADR 0113 : miroir du banc Java
// :java:benchQualite — corpus fixe versionné + baseline JSON signée + CI
// qui ÉCHOUE en régression).
//
// Usage : ./gradlew :kotlin:benchQualite          (comparaison vs baseline)
//         ./gradlew :kotlin:benchQualite -Presigner   (re-signature, PR dédiée)
//
// Deux arrangements sur le MÊME corpus :
//  - « livree » : la chaîne standard de l'étage (Correspondance → Nature
//                 par contexte → Longueur → Alphabetique) — le moteur livré ;
//  - « naïf »   : la chaîne alphabétique SEULE — le statu quo sans design
//                 (la baseline de première signature : le design 3c doit la
//                 battre de ≥ 5 points top-1/MRR pour valider ADR 0113).
//
// Métriques (corpus [CorpusQualiteCompletion], positions étiquetées) :
//  - recall  : fraction des positions dont TOUS les libellés attendus
//              figurent dans la liste rendue ;
//  - top-1   : fraction des positions dont le PREMIER item est le gagnant
//              désigné ;
//  - MRR     : moyenne de l'inverse du rang du gagnant désigné ;
//  - p95     : latence de complétion livrée (ouverture + arrangement, 5
//              passes mesurées après 2 passes d'échauffement — R2 bruit de
//              mesure : la borne comparative intègre l'incertitude) ;
//  - sauts   : contrat « zéro saut de liste » — sur la succession de
//              frappe (même clé de gel, préfixe s'affinant), une tête
//              gelée qui survit doit garder SON rang exact.
//
// Le corpus est DÉTERMINISTE (stdlib du runtime seule, projet corpus sur
// disque) : recall/top-1/MRR sont reproductibles au bit près — tolérance
// de comparaison NULLE ; seule la latence porte une tolérance documentée.
// La Passe de STABILITÉ SOUS GEL re-mesure la chaîne livrée une deuxième
// fois sur la MÊME session (gels constitués) : un rang qui bouge entre les
// deux passes dénonce une CONTAMINATION de gel entre positions (clés
// d'ancre partagées) — c'est un bug de corpus ou de moteur, jamais une
// mesure.
package jo.codeide.lsp.kotlin.banc

import java.nio.file.Files
import java.time.LocalDate
import jo.codeide.lsp.kotlin.api.ChaineClassementKotlin
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.PeseurAlphabetiqueKotlin
import jo.codeide.lsp.kotlin.session.MoteurLangageKotlinNoyau
import jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments
import kotlin.io.path.writeText

/** Les métriques d'un arrangement sur le corpus. */
class Mesures(
    val recall: Double,
    val top1: Double,
    val mrr: Double,
)

/** Entrée de diagnostic d'une position (imprimée pour chaque run). */
private data class LignePosition(
    val nom: String,
    val genre: GenreContexteKotlin,
    val genreAttendu: GenreContexteKotlin,
    val manquants: List<String>,
    val rangGagnant: Int,
    val premier: String,
)

fun main(args: Array<String>) {
    val resigner = "--resigner" in args
    val cheminBaseline = System.getProperty("banc.baseline")
        ?: "../bancs/baselines/completion-kotlin.json"
    val echecs = mutableListOf<String>()

    // --- Montage du projet corpus : Widget/Outil sur DISQUE (racines
    // sources réelles — résolus par le scan, comme une vraie frappe), la
    // stdlib du runtime comme UNIQUE entrée de bibliothèque (ancre de
    // classe : la chaîne d'outils épingle la version). ---
    val dossier = Files.createTempDirectory("banc-qualite-kotlin")
    val racine = Files.createDirectories(dossier.resolve("src"))
    racine.resolve("Widget.kt").writeText(CorpusQualiteCompletion.SOURCE_WIDGET)
    val jarStdlib = java.io.File(
        kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
            .codeSource.location.toURI(),
    )
    val classpath = ClasspathKotlin(
        entrees = listOf(
            EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib.path, "banc:0:${jarStdlib.name}"),
        ),
        racinesSources = listOf(racine.toAbsolutePath().toString()),
    )

    /** Monte une session chaude : moteur nourricier (passe + publication), session branchée, guichet attendu par sondage borné, scan des racines RAFRAÎCHI (miroir du monteur de la fiche 045 — le chemin de production D6). */
    fun sessionPrete(chaine: ChaineClassementKotlin?): SessionLangageKotlinDocuments {
        val moteur = if (chaine == null) {
            MoteurLangageKotlinNoyau()
        } else {
            MoteurLangageKotlinNoyau(chaineCompletion = chaine)
        }
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        val limite = System.currentTimeMillis() + 120_000
        while (session.indexSiPret() == null) {
            if (System.currentTimeMillis() > limite) {
                println("== ÉCHEC : la passe du moteur n'a pas publié (guichet toujours muet après 120 s)")
                kotlin.system.exitProcess(2)
            }
            Thread.sleep(10)
        }
        session.rafraichirSources()
        return session
    }

    // --- Passage de mesure (chaque moteur sur le MÊME corpus ; UNE session
    // par arrangement — les gels y vivent, comme une vraie séance) ---
    fun mesurer(session: SessionLangageKotlinDocuments, etiquette: String): Pair<Mesures, List<LignePosition>> {
        val lignes = mutableListOf<LignePosition>()
        var rappels = 0
        var premiers = 0
        var sommeMrr = 0.0
        for (position in CorpusQualiteCompletion.positions) {
            val marque = position.source.indexOf('¦')
            val texte = position.source.replace("¦", "")
            session.ouvrir(position.uri, texte)
            val reponse = session.completionArrangee(position.uri, marque)
            val libelles = reponse.propositions.map { it.libelle }
            val manquants = position.attendus.filter { it !in libelles }
            if (manquants.isEmpty()) rappels++
            val rang = libelles.indexOf(position.gagnant)
            if (rang == 0) premiers++
            if (rang >= 0) sommeMrr += 1.0 / (rang + 1)
            lignes.add(
                LignePosition(
                    position.nom,
                    reponse.genre,
                    position.genreAttendu,
                    manquants,
                    rang,
                    libelles.firstOrNull() ?: "∅",
                ),
            )
        }
        val total = CorpusQualiteCompletion.positions.size
        val mesures = Mesures(rappels.toDouble() / total, premiers.toDouble() / total, sommeMrr / total)
        println(
            "== arrangement « $etiquette » : recall=%.3f top1=%.3f mrr=%.3f".format(
                mesures.recall,
                mesures.top1,
                mesures.mrr,
            ),
        )
        for (ligne in lignes) {
            val alerte = if (ligne.manquants.isNotEmpty() || ligne.rangGagnant != 0 || ligne.genre != ligne.genreAttendu) " ←" else ""
            println(
                "  %-26s genre=%-20s (attendu %-20s) gagnant rang=%-3d premier=%s%s".format(
                    ligne.nom,
                    ligne.genre,
                    ligne.genreAttendu,
                    ligne.rangGagnant,
                    ligne.premier,
                    alerte,
                ),
            )
        }
        return mesures to lignes
    }

    val sessionLivree = sessionPrete(null)
    val (livree, lignesLivree) = mesurer(sessionLivree, "livree")
    val sessionNaive = sessionPrete(ChaineClassementKotlin(listOf(PeseurAlphabetiqueKotlin())))
    val (naif, _) = mesurer(sessionNaive, "naïf")

    // --- Contrôle de santé 1 : le verdict de genre est DÉTERMINISTE ---
    for (ligne in lignesLivree) {
        if (ligne.genre != ligne.genreAttendu) {
            echecs.add("verdict non déterministe sur ${ligne.nom} : ${ligne.genre} ≠ ${ligne.genreAttendu} attendu")
        }
    }

    // --- Contrôle de santé 2 : STABILITÉ SOUS GEL — la chaîne livrée
    // re-mesurée sur la MÊME session (gels constitués par la première
    // passe) doit rendre des rangs IDENTIQUES : un rang qui bouge dénonce
    // une contamination de gel entre positions (clés d'ancre partagées —
    // le corpus les tient distinctes par construction). ---
    val (_, lignesSeconde) = mesurer(sessionLivree, "livree (stabilité sous gel)")
    for ((premiere, seconde) in lignesLivree.zip(lignesSeconde)) {
        if (premiere.rangGagnant != seconde.rangGagnant || premiere.genre != seconde.genre) {
            echecs.add(
                "contamination de gel sur ${premiere.nom} : rang ${premiere.rangGagnant} → ${seconde.rangGagnant} " +
                    "entre la passe à froid et la passe sous gel",
            )
        }
    }

    // --- Latence p95 (livrée, 2 passes d'échauffement + 5 passes mesurées ;
    // la MESURE couvre l'ouverture du document ET l'arrangement — le coût
    // d'une frappe complète) ---
    repeat(2) { chauffe ->
        for (position in CorpusQualiteCompletion.positions) {
            val marque = position.source.indexOf('¦')
            sessionLivree.ouvrir(position.uri, position.source.replace("¦", ""))
            sessionLivree.completionArrangee(position.uri, marque)
        }
        if (chauffe == 1) {
            // la succession de frappe est JOUÉE pendant l'échauffement : le
            // gel se constitue, l'état est celui d'une vraie session.
            for (source in CorpusQualiteCompletion.successionFrappe) {
                val marque = source.indexOf('¦')
                sessionLivree.ouvrir(CorpusQualiteCompletion.URI_SUCCESSION, source.replace("¦", ""))
                sessionLivree.completionArrangee(CorpusQualiteCompletion.URI_SUCCESSION, marque)
            }
        }
    }
    val echantillons = mutableListOf<Long>()
    repeat(5) {
        for (position in CorpusQualiteCompletion.positions) {
            val marque = position.source.indexOf('¦')
            val texte = position.source.replace("¦", "")
            val debut = System.nanoTime()
            sessionLivree.ouvrir(position.uri, texte)
            sessionLivree.completionArrangee(position.uri, marque)
            echantillons.add((System.nanoTime() - debut) / 1_000)
        }
    }
    val tries = echantillons.sorted()
    val p95Us = tries[((0.95 * tries.size).toInt()).coerceAtMost(tries.size - 1)]
    val p95Ms = p95Us / 1000.0
    println(
        "== latence livrée : p95=%.2f ms (min=%.2f, max=%.2f, %d échantillons)".format(
            p95Ms,
            tries.first() / 1000.0,
            tries.last() / 1000.0,
            tries.size,
        ),
    )

    // --- Contrat « zéro saut de liste » : une tête gelée qui SURVIT à
    // l'affinement du préfixe ne peut ni être DÉPASSÉE par un nouveau venu,
    // ni descendre — elle peut seulement MONTER par disparition des items
    // au-dessus d'elle. Un saut = un rang qui AUGMENTE. ---
    var sauts = 0
    var tetePrecedente: List<String> = emptyList()
    for (source in CorpusQualiteCompletion.successionFrappe) {
        val marque = source.indexOf('¦')
        sessionLivree.ouvrir(CorpusQualiteCompletion.URI_SUCCESSION, source.replace("¦", ""))
        val reponse = sessionLivree.completionArrangee(CorpusQualiteCompletion.URI_SUCCESSION, marque)
        val libelles = reponse.propositions.map { it.libelle }
        val tete = libelles.take(jo.codeide.lsp.kotlin.api.ArrangementListeKotlin.GEL_MAX)
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

    // --- Preuve de valeur du design 3c (vs la baseline naïve) ---
    if (livree.recall < naif.recall) {
        echecs.add("recall livrée (%.3f) < naïf (%.3f)".format(livree.recall, naif.recall))
    }
    if (livree.top1 < naif.top1 + 0.05) {
        echecs.add(
            "top-1 livrée (%.3f) < naïf + 5 pts (%.3f) — ADR 0113 non validé".format(livree.top1, naif.top1 + 0.05),
        )
    }
    if (livree.mrr < naif.mrr + 0.05) {
        echecs.add(
            "MRR livrée (%.3f) < naïf + 5 pts (%.3f) — ADR 0113 non validé".format(livree.mrr, naif.mrr + 0.05),
        )
    }
    if (sauts != 0) echecs.add("$sauts saut(s) de liste — contrat gel cassé")
    if (p95Ms > SEUIL_P95_MS_ABSOLU) {
        echecs.add("p95 %.2f ms > borne absolue %.2f ms".format(p95Ms, SEUIL_P95_MS_ABSOLU))
    }

    // --- Comparaison à la baseline signée (régression = échec CI) ---
    val baseline = lireBaseline(cheminBaseline)
    if (baseline == null) {
        println("== baseline absente ($cheminBaseline) : première signature")
        if (!resigner) echecs.add("baseline absente — signer par PR dédiée : ./gradlew :kotlin:benchQualite -Presigner")
    } else {
        val baseLivree = baseline.getValue("livree")
        if (livree.recall < baseLivree.getValue("recall")) {
            echecs.add("recall régressé : %.3f < baseline %.3f".format(livree.recall, baseLivree.getValue("recall")))
        }
        if (livree.top1 < baseLivree.getValue("top1")) {
            echecs.add("top-1 régressé : %.3f < baseline %.3f".format(livree.top1, baseLivree.getValue("top1")))
        }
        if (livree.mrr < baseLivree.getValue("mrr")) {
            echecs.add("MRR régressé : %.3f < baseline %.3f".format(livree.mrr, baseLivree.getValue("mrr")))
        }
        val p95Base = baseLivree.getValue("p95_ms")
        val borne = maxOf(SEUIL_P95_MS_ABSOLU, p95Base * 1.5)
        if (p95Ms > borne) {
            echecs.add(
                "p95 régressé : %.2f ms > borne %.2f ms (baseline %.2f × 1,5)".format(p95Ms, borne, p95Base),
            )
        }
    }

    if (resigner) {
        ecrireBaseline(cheminBaseline, livree, naif, p95Ms)
        println("== baseline SIGNÉE : $cheminBaseline (commit = signature, re-baseline par PR dédiée)")
    }

    sessionLivree.close()
    sessionNaive.close()

    if (echecs.isNotEmpty()) {
        println("== ÉCHEC DU BANC DE QUALITÉ (${echecs.size}) :")
        echecs.forEach { println("  ✗ $it") }
        kotlin.system.exitProcess(1)
    }
    println("== BANC DE QUALITÉ KOTLIN VERT (corpus ${CorpusQualiteCompletion.positions.size} positions)")
}

/**
 * Borne absolue de latence p95 par frappe (R2 : large, documentée — la
 * cible appareil < 100 ms de la synthèse §4 garde la JVM comme LIMITE
 * BASSE ; 50 ms est le miroir exact de la borne du banc Java).
 */
private const val SEUIL_P95_MS_ABSOLU = 50.0

/** Écrit la baseline JSON (une métrique par ligne — diff lisible en PR). */
private fun ecrireBaseline(chemin: String, livree: Mesures, naif: Mesures, p95Ms: Double) {
    val fichier = java.nio.file.Path.of(chemin)
    Files.createDirectories(fichier.parent)
    val contenu = """
        {
          "note": "Baseline du banc de qualité de complétion KOTLIN (étage 3 — ADR 0119 option 1 / ADR 0113, miroir ADR 0016 Java — signée par le commit qui la porte ; re-baseline par PR dédiée uniquement). 'naif' = chaîne alphabétique seule (statu quo sans design 3c) ; 'livree' = chaîne standard (correspondance graduée → nature par contexte → liftShorter → alphabétique). Le régime membre ne filtre pas par préfixe côté serveur (frontière RPC ADR 0118) : la naïve y rend l'espace entier en ordre alphabétique. Tolérance latence : p95 ≤ max(50 ms, baseline × 1,5).",
          "corpus": "completion-kotlin v1 (${CorpusQualiteCompletion.positions.size} positions)",
          "signee": "${LocalDate.now()}",
          "livree": {
            "recall": ${"%.3f".format(livree.recall)},
            "top1": ${"%.3f".format(livree.top1)},
            "mrr": ${"%.3f".format(livree.mrr)},
            "p95_ms": ${"%.2f".format(p95Ms)}
          },
          "naif": {
            "recall": ${"%.3f".format(naif.recall)},
            "top1": ${"%.3f".format(naif.top1)},
            "mrr": ${"%.3f".format(naif.mrr)}
          }
        }
    """.trimIndent()
    fichier.toFile().writeText(contenu + "\n")
}

/**
 * Lit la baseline JSON (schéma plat à deux niveaux — analyseur minimal
 * dédié, sans dépendance ; miroir du lecteur du banc Java).
 *
 * @return la map « section → (métrique → valeur) », ou null si absente.
 */
private fun lireBaseline(chemin: String): Map<String, Map<String, Double>>? {
    val fichier = java.nio.file.Path.of(chemin)
    if (!Files.isRegularFile(fichier)) return null
    val texte = fichier.toFile().readText()
    val resultats = mutableMapOf<String, MutableMap<String, Double>>()
    var section: String? = null
    for (ligneBrute in texte.lines()) {
        val ligne = ligneBrute.trim()
        when {
            ligne.startsWith("\"livree\"") || ligne.startsWith("\"naif\"") ->
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

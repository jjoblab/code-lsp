// CodeIDE — banc de PARITÉ CROISÉE (étage T, cœur historique — PLAN-REECRITURE
// §4 : « le même corpus rejoué contre le vendored (oracle en boîte noire,
// exécuté dans codeide-lsp) et le moteur neuf ; baselines JSON signées, CI
// bloquante ; corpus fixe versionné » ; ADR 0016 option 1 ; critère de
// bascule §8.5 : « 0 comportement perdu contre l'oracle »).
//
// Usage : ./gradlew :java:benchParite            (comparaison vs baseline)
//         ./gradlew :java:benchParite -Presigner (re-signature, PR dédiée)
//
// Prérequis (gate, motif ANDROID_JAR) : l'oracle vendored construit au
// commit épinglé par scripts/preparer-oracle.sh → .oracle/ (jamais committé,
// R6.4). Oracle absent = banc IGNORÉ avec message (exit 0) — la CI le
// construit d'abord, le banc y est donc BLOQUANT.
//
// MÉTHODE : le MÊME corpus (completion-n2 v1 — 18 positions + 4 frappes,
// [CorpusQualiteCompletion], unique source de vérité) est rejoué par :
//  - l'ORACLE : sous-processus JVM distinct (le vendored embarque son ecj
//    patché, jamais dans le classpath du moteur neuf — pilote
//    [DriverOracle], résultats JSON) ;
//  - le MOTEUR NEUF : in-process ([MoteurJava], chaîne D8 standard).
//
// PARITÉ MESURÉE (l'ORDRE n'est pas le contrat du croisé — le classement
// relève du banc de qualité N2 et du design D8 ; ici : les COMPORTEMENTS
// rendus, i.e. les libellés présents/absents) :
//  - couverture du CAHIER (attendus ⊆ rendu) pour chaque moteur ;
//  - PERTES : libellés rendus par l'oracle et absents du neuf — la métrique
//    de bascule (§8.5 : elle doit atteindre 0) ;
//  - GAINS : libellés rendus par le neuf et absents de l'oracle —
//    informationnels (améliorations D8/Gabarits, re-signées à la prochaine
//    PR de re-baseline).
//
// RÉGRESSION = ÉCHEC CI (ADR 0016) :
//  1. couverture cahier du neuf en baisse vs baseline ;
//  2. pertes vs oracle en hausse vs baseline (approche du mur de bascule) ;
//  3. libellé PERDU par le neuf sur une position signée en baseline ;
//  4. DÉRIVE DE L'ORACLE : toute différence de listes oracle vs baseline
//     (le commit épinglé a changé de comportement → re-pin + re-signature).
//
// ARBITRAGES (ADR 0030 : « toute divergence oracle↔neuf arbitrée : FP corrigé,
// FN documenté, ou quirk consigné ») : les libellés d'[ARBITRAGES_G004] sont
// retirés DÉLIBÉRÉMENT par la vague V-A (écart G-004/P6 — types NON PUBLICS
// du JDK que l'oracle propose aussi, avec référence insoluble depuis un autre
// paquet). Ils ne comptent NI comme pertes (2) NI comme libellés perdus (3) ;
// la re-baseline suivante les absorbe dans les listes signées.
package jo.codeide.lsp.java.banc

import jo.codeide.lsp.java.MoteurJava
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Marqueur de caret du corpus (convention du cahier). */
private const val MARQUEUR = '¦'

/** Le nombre d'items de la succession enregistrés par frappe (cap documenté). */
private const val CAP_SUCCESSION = 12

/** Délai du sous-processus oracle (garde anti-blocage, en secondes). */
private const val DELAI_ORACLE_S = 600L

/**
 * Les libellés retirés DÉLIBÉRÉMENT par la vague V-A (arbitrage ADR 0030,
 * écart G-004/P6, consigné noir sur blanc) : des types NON PUBLICS du JDK
 * que l'ANNEXE du classpath énumérait dans les paquets de surface et que le
 * filtre d'accessibilité `estProposablePourAutoImport` ne propose plus — la
 * référence étant INSOLUBLE depuis un autre paquet (mesuré P6 : 4 insertions
 * sur 12 non résolubles avant correctif ; l'ORACLE vendored porte le même
 * défaut et propose ces libellés, d'où l'écart de listes).
 *
 * Établie par diff exhaustif du corpus rejoué contre la baseline signée le
 * 31/10/2026 (30 libellés, 3 positions : nom-locale-domine, type-Integer,
 * type-String) — java.lang/java.util.stream/javax.swing.plaf.metal internes
 * (`StringUTF16`, `StringLatin1`, `AbstractPipeline`, `Striped64`,
 * `InternalFrameTitlePane*`…). La re-baseline suivante (PR dédiée) absorbe
 * cette liste dans les listes signées et la retire d'ici.
 */
private val ARBITRAGES_G004: Set<String> = setOf(
    // nom-locale-domine (java.util.stream + java.lang internes)
    "AbstractConstantGroup", "AbstractFilter", "AbstractPipeline",
    "AbstractShortCircuitTask", "AbstractSpinedBuffer", "AbstractStringBuilder",
    "AbstractTask", "AbstractValidatingLambdaMetafactory",
    // type-Integer (javax.swing.plaf.metal + java.lang internes)
    "InternalFramePainter",
    "InternalFrameTitlePaneCloseButtonPainter",
    "InternalFrameTitlePaneCloseButtonWindowNotFocusedState",
    "InternalFrameTitlePaneIconifyButtonPainter",
    "InternalFrameTitlePaneIconifyButtonWindowNotFocusedState",
    "InternalFrameTitlePaneMaximizeButtonPainter",
    "InternalFrameTitlePaneMaximizeButtonWindowMaximizedState",
    "InternalFrameTitlePaneMaximizeButtonWindowNotFocusedState",
    "InternalFrameTitlePaneMenuButtonPainter",
    "InternalFrameTitlePaneMenuButtonWindowNotFocusedState",
    "InternalFrameTitlePaneWindowFocusedState",
    "InternalFrameWindowFocusedState",
    "PinnedThreadPrinter",
    // type-String (java.lang/java.nio internes + Striped64)
    "StringCharBuffer", "StringCoding", "StringConcatHelper",
    "StringLatin1", "StringTemplateImpl", "StringTemplateImplFactory",
    "StringUTF16", "Striped64",
)

/** Les mesures agrégées du banc croisé (comparées à la baseline signée). */
private class MesuresParite(
    val couvertureCahierOracle: Double,
    val couvertureCahierNeuf: Double,
    val pertesVsOracle: Int,
    val positionsSansPerte: Double,
)

/** Une ligne de diagnostic par position (le rapport imprimé). */
private data class LigneParite(
    val nom: String,
    val nbOracle: Int,
    val nbNeuf: Int,
    val manquantsCahierOracle: List<String>,
    val manquantsCahierNeuf: List<String>,
    val pertes: List<String>,
    val arbitrages: List<String>,
    val gains: List<String>,
)

fun main(args: Array<String>) {
    val resigner = "--resigner" in args
    val cheminBaseline = Path.of(
        System.getProperty("banc.baseline") ?: "bancs/baselines/parite-completion.json",
    )
    val dossierOracle = Path.of(System.getProperty("banc.oracle") ?: ".oracle")
    val classesPilote = System.getProperty("banc.piloteClasses")
        ?: error("propriété système banc.piloteClasses absente (câblée par la tâche :java:benchParite)")
    val echecs = mutableListOf<String>()

    // --- Gate : l'oracle épinglé doit être construit (jamais committé) ---
    val jarOracle = dossierOracle.resolve("java-1.16.0.jar")
    val jarLsp4j = dossierOracle.resolve("lsp4j-0.21.2.jar")
    val jarGson = dossierOracle.resolve("gson-2.10.1.jar")
    if (!jarOracle.isRegularFile() || !jarLsp4j.isRegularFile() || !jarGson.isRegularFile()) {
        println(
            "== BANC CROISÉ IGNORÉ — oracle vendored absent de $dossierOracle " +
                "(construire : bash scripts/preparer-oracle.sh — jamais committé, R6.4).",
        )
        return
    }

    // --- Espace de travail : corpus versionné + sources de base posées ---
    val dossier = Files.createTempDirectory("banc-parite")
    val racine = Files.createDirectories(dossier.resolve("src/demo"))
    racine.resolve("Main.java").writeText(CorpusQualiteCompletion.SOURCE_MAIN, StandardCharsets.UTF_8)
    racine.resolve("Widget.java").writeText(CorpusQualiteCompletion.SOURCE_WIDGET, StandardCharsets.UTF_8)
    val corpus = CorpusBrut(
        CorpusQualiteCompletion.positions.map { PositionBrute(it.nom, it.source) },
        CorpusQualiteCompletion.successionFrappe.mapIndexed { i, s -> PositionBrute("frappe-$i", s) },
    )
    val cheminCorpus = dossier.resolve("corpus.json")
    cheminCorpus.writeText(FormatEchange.ecrireCorpus(corpus), StandardCharsets.UTF_8)

    // --- L'ORACLE (vendored, boîte noire, sous-processus isolé) ---
    val cheminResultatsOracle = dossier.resolve("resultats-oracle.json")
    lancerOracle(jarOracle, jarLsp4j, jarGson, classesPilote, cheminCorpus, cheminResultatsOracle, racine.parent)
        ?.let { echecs.add(it) }
    val resultatsOracle: Map<String, List<String>> =
        if (cheminResultatsOracle.isRegularFile()) {
            FormatEchange.lireResultats(cheminResultatsOracle.readText(StandardCharsets.UTF_8))
                .associate { it.nom to it.libelles }
        } else {
            emptyMap()
        }

    // --- Le MOTEUR NEUF (in-process, chaîne D8 standard) ---
    val moteur = MoteurJava(racines = listOf(racine.parent))
    fun rejouerNeuf(position: PositionBrute): ResultatPosition {
        val marque = position.source.indexOf(MARQUEUR)
        val texte = position.source.replace(MARQUEUR.toString(), "")
        val reponse = moteur.completer("/demo/Main.java", texte, marque)
        return ResultatPosition(position.nom, reponse.items.map { it.libelle })
    }
    val resultatsNeuf: Map<String, List<String>> =
        (corpus.positions.map(::rejouerNeuf) + corpus.succession.map(::rejouerNeuf))
            .associate { it.nom to it.libelles }

    if (echecs.isNotEmpty()) {
        println("== ÉCHEC DU BANC CROISÉ (${echecs.size}) :")
        echecs.forEach { println("  ✗ $it") }
        kotlin.system.exitProcess(1)
    }

    // --- Parité position par position (le cahier reste le contrat) ---
    // Pertes/gains comptent les LIBELLÉS DISTINCTS (l'oracle rend des
    // doublons d'overloads ; un doublon n'est pas un comportement de plus).
    val lignes = mutableListOf<LigneParite>()
    for (position in CorpusQualiteCompletion.positions) {
        val oracle = resultatsOracle[position.nom] ?: emptyList()
        val neuf = resultatsNeuf[position.nom] ?: emptyList()
        lignes.add(
            LigneParite(
                nom = position.nom,
                nbOracle = oracle.size,
                nbNeuf = neuf.size,
                manquantsCahierOracle = position.attendus.filter { it !in oracle },
                manquantsCahierNeuf = position.attendus.filter { it !in neuf },
                pertes = oracle.distinct().filter { it !in neuf && it !in ARBITRAGES_G004 },
                arbitrages = oracle.distinct().filter { it in ARBITRAGES_G004 && it !in neuf },
                gains = neuf.distinct().filter { it !in oracle },
            ),
        )
    }
    val total = CorpusQualiteCompletion.positions.size
    val mesures = MesuresParite(
        couvertureCahierOracle = lignes.count { it.manquantsCahierOracle.isEmpty() }.toDouble() / total,
        couvertureCahierNeuf = lignes.count { it.manquantsCahierNeuf.isEmpty() }.toDouble() / total,
        pertesVsOracle = lignes.sumOf { it.pertes.size },
        positionsSansPerte = lignes.count { it.pertes.isEmpty() }.toDouble() / total,
    )

    println(
        "== parité : couverture cahier oracle=%.3f neuf=%.3f — pertes vs oracle=%d (%.0f%% positions sans perte)".format(
            mesures.couvertureCahierOracle,
            mesures.couvertureCahierNeuf,
            mesures.pertesVsOracle,
            100.0 * mesures.positionsSansPerte,
        ),
    )
    for (ligne in lignes) {
        val alerte = if (ligne.manquantsCahierNeuf.isNotEmpty() || ligne.pertes.isNotEmpty()) " ←" else ""
        println(
            "  %-24s oracle=%-3d neuf=%-3d cahier=%s pertes=%d%s".format(
                ligne.nom,
                ligne.nbOracle,
                ligne.nbNeuf,
                if (ligne.manquantsCahierNeuf.isEmpty()) "✓" else "✗" + ligne.manquantsCahierNeuf.joinToString("/"),
                ligne.pertes.size,
                alerte,
            ),
        )
        if (ligne.pertes.isNotEmpty()) println("      pertes : ${ligne.pertes.joinToString(", ")}")
        if (ligne.arbitrages.isNotEmpty()) {
            println("      arbitrés (G-004, V-A — retirés délibérément) : ${ligne.arbitrages.joinToString(", ")}")
        }
        if (ligne.gains.isNotEmpty()) println("      gains  : ${ligne.gains.joinToString(", ")}")
    }

    // --- Baseline signée : comparaison (régression = échec CI) ---
    val baseline = lireBaseline(cheminBaseline)
    if (baseline == null) {
        println("== baseline absente ($cheminBaseline) : première signature")
        if (!resigner) echecs.add("baseline absente — signer par PR dédiée : ./gradlew :java:benchParite -Presigner")
    } else {
        val metriquesBase = baseline.metriques
        val plancherNeuf = metriquesBase["couverture_cahier_neuf"] ?: 1.0
        if (mesures.couvertureCahierNeuf < plancherNeuf) {
            echecs.add(
                "couverture cahier du neuf régressée : %.3f < baseline %.3f".format(
                    mesures.couvertureCahierNeuf,
                    plancherNeuf,
                ),
            )
        }
        val pertesBase = metriquesBase["pertes_vs_oracle"]?.toInt() ?: 0
        if (mesures.pertesVsOracle > pertesBase) {
            echecs.add("pertes vs oracle en hausse : ${mesures.pertesVsOracle} > baseline $pertesBase — critère de bascule §8.5 qui s'éloigne")
        }
        // Dérive de l'ORACLE : listes immuables par construction (commit épinglé).
        for ((nom, attendues) in baseline.positions) {
            val actuelles = resultatsOracle[nom] ?: emptyList()
            if (attendues != actuelles) {
                echecs.add("DÉRIVE ORACLE sur « $nom » : listes ≠ baseline (commit épinglé à re-vérifier, re-signature par PR)")
            }
        }
        // Libellés PERDUS par le neuf sur une position signée (le plancher de
        // parité) — hors arbitrages V-A (G-004 : retirés délibérément, la
        // re-baseline suivante les absorbe).
        for ((nom, signees) in baseline.neuf) {
            val actuelles = resultatsNeuf[nom] ?: emptyList()
            val perdues = signees.filter { it !in actuelles && it !in ARBITRAGES_G004 }
            if (perdues.isNotEmpty()) {
                echecs.add("libellé(s) perdu(s) par le neuf sur « $nom » : ${perdues.joinToString(", ")}")
            }
        }
        // Dérive de l'ORACLE sur la succession (listes plafonnées au CAP).
        for ((nom, frappe) in baseline.succession) {
            val attendues = frappe["oracle"] ?: emptyList()
            val actuelles = (resultatsOracle[nom] ?: emptyList()).take(CAP_SUCCESSION)
            if (attendues != actuelles) {
                echecs.add("DÉRIVE ORACLE sur la frappe « $nom » : listes ≠ baseline")
            }
        }
    }

    if (resigner) {
        ecrireBaseline(
            cheminBaseline,
            mesures,
            resultatsOracle,
            resultatsNeuf,
            corpus,
        )
        println("== baseline SIGNÉE : $cheminBaseline (commit = signature, re-baseline par PR dédiée)")
    }

    if (echecs.isNotEmpty()) {
        println("== ÉCHEC DU BANC DE PARITÉ (${echecs.size}) :")
        echecs.forEach { println("  ✗ $it") }
        kotlin.system.exitProcess(1)
    }
    println(
        "== BANC DE PARITÉ CROISÉE VERT (corpus ${CorpusQualiteCompletion.positions.size} positions + " +
            "${corpus.succession.size} frappes, oracle ${jarOracle.fileName})",
    )
}

/**
 * Lance le pilote oracle en sous-processus JVM ISOLÉ (classpath : jar
 * vendored en tête, puis lsp4j/gson du vendored, classes du pilote,
 * kotlin-stdlib de l'orchestrateur).
 *
 * @return null si le pilote a rendu ses résultats, sinon le message
 *   d'échec (code de sortie, stdout/stderr conservés pour le diagnostic).
 */
private fun lancerOracle(
    jarOracle: Path,
    jarLsp4j: Path,
    jarGson: Path,
    classesPilote: String,
    cheminCorpus: Path,
    cheminResultats: Path,
    racineSources: Path,
): String? {
    val stdlib = chercherKotlinStdlib()
        ?: return "kotlin-stdlib introuvable sur le classpath de l'orchestrateur (assemblage du sous-processus impossible)"
    val classpath = listOf(jarOracle.toString(), jarLsp4j.toString(), jarGson.toString()) +
        classesPilote.split(File.pathSeparator).filter { it.isNotBlank() } +
        listOf(stdlib)
    val javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString()
    val processus = ProcessBuilder(
        javaBin,
        "-Xmx2g",
        "-cp",
        classpath.joinToString(File.pathSeparator),
        "jo.codeide.lsp.java.banc.DriverOracleKt",
        cheminCorpus.toString(),
        cheminResultats.toString(),
        racineSources.toString(),
    ).start()
    // Flux lus sur des threads distincts (garde anti-interblocage : un stderr
    // plein pendant la lecture du stdout bloquerait le pilote autrement).
    val sortie = StringBuilder()
    val lecteurs = listOf(processus.inputStream, processus.errorStream).map { flux ->
        Thread {
            val texte = flux.readBytes().toString(StandardCharsets.UTF_8)
            synchronized(sortie) { sortie.append(texte) }
        }.apply { isDaemon = true; start() }
    }
    val termine = processus.waitFor(DELAI_ORACLE_S, java.util.concurrent.TimeUnit.SECONDS)
    lecteurs.forEach { it.join(5_000) }
    if (!termine) {
        processus.destroyForcibly()
        return "pilote oracle : délai dépassé (${DELAI_ORACLE_S} s)"
    }
    if (processus.exitValue() != 0) {
        return "pilote oracle : exit ${processus.exitValue()}\n$sortie".trim()
    }
    print(sortie)
    return null
}

/** Localise kotlin-stdlib sur le classpath de l'orchestrateur (JVM Gradle). */
private fun chercherKotlinStdlib(): String? =
    System.getProperty("java.class.path")
        ?.split(File.pathSeparator)
        ?.firstOrNull { File(it).name.startsWith("kotlin-stdlib") }

/** La baseline signée du banc croisé (métriques + listes de parité). */
private class BaselineParite(
    val metriques: Map<String, Double>,
    /** Position → listes ORACLE signées (contrat anti-dérive du commit épinglé). */
    val positions: Map<String, List<String>>,
    /** Position → listes NEUF signées (le plancher de parité du moteur neuf). */
    val neuf: Map<String, List<String>>,
    /** Frappe → listes plafonnées (oracle et neuf) de la succession. */
    val succession: Map<String, Map<String, List<String>>>,
)

/**
 * Lit la baseline JSON signée (schéma plat documenté — analyseur JSON
 * minimal dédié, sans dépendance).
 */
private fun lireBaseline(chemin: Path): BaselineParite? {
    if (!chemin.isRegularFile()) return null
    val racine = LecteurJsonMinimal.lire(chemin.readText(StandardCharsets.UTF_8)) as? Map<*, *> ?: return null
    val metriques = (racine["metriques"] as? Map<*, *>)?.mapKeys { it.key.toString() }
        ?.mapValues { (it.value as Number).toDouble() } ?: emptyMap()

    /** Une section « { oracle: [...], neuf: [...] } » d'une position/frappe. */
    fun listesSection(valeur: Any?): Map<String, List<String>> =
        (valeur as? Map<*, *>)?.entries?.associate { (cle, liste) ->
            cle.toString() to (liste as? List<*> ?: emptyList<Any>()).map { it.toString() }
        } ?: emptyMap()

    /** Le bloc positions : rend les map « nom → oracle » et « nom → neuf ». */
    fun blocPositions(section: Any?): Pair<Map<String, List<String>>, Map<String, List<String>>> {
        val oracle = mutableMapOf<String, List<String>>()
        val neuf = mutableMapOf<String, List<String>>()
        for ((nom, valeur) in (section as? Map<*, *> ?: emptyMap<Any, Any>())) {
            val listes = listesSection(valeur)
            oracle[nom.toString()] = listes["oracle"] ?: emptyList()
            neuf[nom.toString()] = listes["neuf"] ?: emptyList()
        }
        return oracle to neuf
    }

    val (positionsOracle, positionsNeuf) = blocPositions(racine["positions"])
    val succession = (racine["succession"] as? Map<*, *> ?: emptyMap<Any, Any>())
        .entries.associate { (nom, valeur) -> nom.toString() to listesSection(valeur) }
    return BaselineParite(
        metriques = metriques,
        positions = positionsOracle,
        neuf = positionsNeuf,
        succession = succession,
    )
}

/**
 * Écrit la baseline JSON signée (une métrique par ligne, une position par
 * bloc — diff lisible en PR, ADR 0016).
 */
private fun ecrireBaseline(
    chemin: Path,
    mesures: MesuresParite,
    oracle: Map<String, List<String>>,
    neuf: Map<String, List<String>>,
    corpus: CorpusBrut,
) {
    Files.createDirectories(chemin.parent)
    val sb = StringBuilder()
    sb.append("{\n")
    sb.append("  \"note\": \"Baseline du banc de PARITÉ CROISÉE (étage T — ADR 0016) : même corpus rejoué contre l'oracle vendored (boîte noire, sous-processus) et le moteur neuf. Signée par le commit qui la porte ; re-baseline par PR dédiée uniquement. L'ORDRE des libellés n'est PAS le contrat du croisé (classement = banc de qualité N2 / design D8) : seules les PRÉSENCES comptent, comptées en libellés DISTINCTS. Les GAINS du neuf sont informationnels — re-signés à la PR de re-baseline suivante. La succession est plafonnée aux $CAP_SUCCESSION premiers items par frappe. Lecture de la bascule (§8.5) : pertes_vs_oracle est le DELTA BRUT de surface (bruit de l'oracle inclus — mots-clés hors cahier, types environnants) ; le CONTRAT est couverture_cahier_neuf ; l'arbitrage de chaque classe de delta appartient à la décision de bascule.\\n\",\n")
    sb.append("  \"corpus\": \"completion-n2 v1 (18 positions + 4 frappes)\",\n")
    sb.append("  \"oracle\": \"codeide-lsp@cb809e1412fec429c9672da087244b3bd3176545 (java-1.16.0.jar)\",\n")
    sb.append("  \"signee\": \"${java.time.LocalDate.now()}\",\n")
    sb.append("  \"metriques\": {\n")
    sb.append("    \"couverture_cahier_oracle\": %.3f,\n".format(mesures.couvertureCahierOracle))
    sb.append("    \"couverture_cahier_neuf\": %.3f,\n".format(mesures.couvertureCahierNeuf))
    sb.append("    \"pertes_vs_oracle\": ${mesures.pertesVsOracle},\n")
    sb.append("    \"positions_sans_perte\": %.3f\n".format(mesures.positionsSansPerte))
    sb.append("  },\n")
    sb.append("  \"positions\": {\n")
    corpus.positions.forEachIndexed { i, position ->
        sb.append("    ${JsonMini.chaine(position.nom)}: {\n")
        sb.append("      \"oracle\": ${JsonMini.liste(oracle[position.nom] ?: emptyList())},\n")
        sb.append("      \"neuf\": ${JsonMini.liste(neuf[position.nom] ?: emptyList())}\n")
        sb.append("    }%s\n".format(if (i < corpus.positions.size - 1) "," else ""))
    }
    sb.append("  },\n")
    sb.append("  \"succession\": {\n")
    corpus.succession.forEachIndexed { i, frappe ->
        sb.append("    ${JsonMini.chaine(frappe.nom)}: {\n")
        sb.append("      \"oracle\": ${JsonMini.liste((oracle[frappe.nom] ?: emptyList()).take(CAP_SUCCESSION))},\n")
        sb.append("      \"neuf\": ${JsonMini.liste((neuf[frappe.nom] ?: emptyList()).take(CAP_SUCCESSION))}\n")
        sb.append("    }%s\n".format(if (i < corpus.succession.size - 1) "," else ""))
    }
    sb.append("  }\n")
    sb.append("}\n")
    chemin.writeText(sb.toString(), StandardCharsets.UTF_8)
}

/** Écriture JSON minimale (chaînes/entiers échappés, listes sur une ligne). */
private object JsonMini {
    fun chaine(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20 || c.code > 0x7E) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    fun liste(valeurs: List<String>): String =
        "[" + valeurs.joinToString(", ") { chaine(it) } + "]"
}

/** Analyseur JSON minimal (objets, tableaux, chaînes, nombres) — baseline only. */
private object LecteurJsonMinimal {
    fun lire(texte: String): Any? {
        val l = Curseur(texte)
        val valeur = l.valeur()
        l.fin()
        return valeur
    }

    private class Curseur(val texte: String) {
        var i = 0

        fun valeur(): Any? = when (courant()) {
            '{' -> objet()
            '[' -> tableau()
            '"' -> chaine()
            else -> nombreOuMot()
        }

        fun objet(): Map<String, Any?> {
            i++
            val resultat = mutableMapOf<String, Any?>()
            sauter()
            if (courant() == '}') {
                i++
                return resultat
            }
            while (true) {
                sauter()
                val cle = chaine()
                sauter()
                if (courant() != ':') erreur("« : » attendu")
                i++
                sauter()
                resultat[cle] = valeur()
                sauter()
                when (courant()) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return resultat
                    }
                    else -> erreur("« , » ou « } » attendu")
                }
            }
        }

        fun tableau(): List<Any?> {
            i++
            sauter()
            val resultat = mutableListOf<Any?>()
            if (courant() == ']') {
                i++
                return resultat
            }
            while (true) {
                sauter()
                resultat.add(valeur())
                sauter()
                when (courant()) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return resultat
                    }
                    else -> erreur("« , » ou « ] » attendu")
                }
            }
        }

        fun chaine(): String {
            if (courant() != '"') erreur("chaîne attendue")
            i++
            val sb = StringBuilder()
            while (true) {
                val c = texte[i]
                when {
                    c == '"' -> {
                        i++
                        return sb.toString()
                    }
                    c == '\\' -> {
                        i++
                        when (val e = texte[i]) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                sb.append(texte.substring(i + 1, i + 5).toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(e)
                        }
                        i++
                    }
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            }
        }

        fun nombreOuMot(): Any? {
            val debut = i
            while (i < texte.length && texte[i] !in ",}] \n\r\t") i++
            val brut = texte.substring(debut, i)
            return when (brut) {
                "true" -> true
                "false" -> false
                "null" -> null
                else -> brut.toDoubleOrNull() ?: brut
            }
        }

        fun courant(): Char {
            if (i >= texte.length) erreur("fin inattendue")
            return texte[i]
        }

        fun fin() {
            sauter()
            if (i != texte.length) erreur("contenu résiduel après la racine")
        }

        fun sauter() {
            while (i < texte.length && texte[i] in " \n\r\t") i++
        }

        fun erreur(message: String): Nothing =
            throw IllegalArgumentException("baseline JSON invalide — $message (position ~$i)")
    }
}

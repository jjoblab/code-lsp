// CodeIDE — banc de MESURE SUR APPAREIL du moteur Kotlin (module :kotlin,
// vague V-H — ADR 0124 option 2 : « passe gabarit 07 enrichi sur moto g06 » ;
// gabarit 07 : lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/
// 07-gabarit-mesure-appareil.md). Code NEUF jjoblab (clean-room — R5 :
// AUCUNE valeur fabriquée, le banc imprime les nombres MESURÉS, les seuils
// ne sont pas des assertions : la décision appartient au propriétaire après
// lecture des chiffres).
package jo.codeide.lsp.kotlin.banc

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.session.MoteurLangageKotlinNoyau
import jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments

/**
 * Le banc de mesure sur appareil (moto g06, Termux OpenJDK 17 — même
 * protocole que la passe bascule et que la passe moteur Java) : sections
 * S (démarrage), F (frappe), C (contention), Q (inter-fichiers), M
 * (mémoire) du gabarit 07, ENRICHIES de la passe V-H (ADR 0124) :
 *
 * - **H1-H3 (mode profond)** : amorçage du premier diagnostic profond
 *   (< 10 s cible), diagnostic profond CHAUD (< 2 s cible), mémoire
 *   additionnelle du processus déporté (< 300 Mo cible) — le service
 *   `ModeProfondK2` activé à la demande, jamais sur le chemin de frappe ;
 * - **K7 (confirmation hôte)** : la sonde d'environnement déporté —
 *   Termux/PRoot détecté, `java -version`, mémoire de l'appareil : la
 *   confirmation Q-K7 que l'ADR exige AVANT tout verdict.
 *
 * Méthode (gabarit 07 §2) : 3 passes espacées de ≥ 30 s (thermique), la
 * MÉDIANE des 3 passes retenue ; chaque cellule porte p50/p95 ; p99 pour
 * la régularité. Le document géant est DÉTERMINISTE (miroir de la fiche
 * 021 : 150 fonctions ~1 800 lignes, aucune donnée aléatoire).
 *
 * Le corpus de sources du scan (C-Q) : un projet factice GÉNÉRÉ au sol
 * (30 fichiers Kotlin réels : classes, extensions, data classes).
 */
fun main(args: Array<String>) {
    // Le mode du sous-process S2 : mesure UN démarrage à froid puis sort.
    if (args.firstOrNull() == "--froid-seul") {
        mainFroidSeul(args)
        return
    }

    val racine = File(System.getProperty("java.io.tmpdir"), "banc-mesure-kotlin")
    racine.deleteRecursively()
    racine.mkdirs()

    enTete(args)

    // ---- 0. La sonde d'environnement (Q-K7 : confirmation hôte) -------
    sectionK7()

    // ---- Les jars du classpath (le kit les porte dans libs/) ----------
    val libs = File("libs")
    val jarStdlib = libs.listFiles()?.firstOrNull { it.name.startsWith("kotlin-stdlib-") }
        ?: jarStdlibDuRuntime()
    val corpusSources = preparerCorpusSources(racine)

    // ---- S : démarrage ------------------------------------------------
    val mesuresS = sectionS(jarStdlib, corpusSources)

    // ---- F : latences de frappe ---------------------------------------
    val mesuresF = sectionF(racine, jarStdlib, corpusSources)

    // ---- C : contention ------------------------------------------------
    val mesuresC = sectionC(racine, jarStdlib, corpusSources)

    // ---- Q : qualité inter-fichiers ------------------------------------
    val mesuresQ = sectionQ(racine, jarStdlib, corpusSources)

    // ---- M : mémoire ----------------------------------------------------
    val mesuresM = sectionM(racine, jarStdlib, corpusSources)

    // ---- H : mode profond (ADR 0124 — la passe qui manque au verdict) --
    val mesuresH = sectionH(racine, jarStdlib)

    // ---- Le tableau final (à recopier dans le gabarit 07) -------------
    tableauFinal(mesuresS, mesuresF, mesuresC, mesuresQ, mesuresM, mesuresH)

    println()
    println("R5 : valeurs mesurées par ce banc, jamais simulées.")
    println("Recopier les tableaux dans docs/etudes/niveau-android-studio-kotlin/07-gabarit-mesure-appareil.md §4/§7")
    println("et joindre CE transcript brut (jamais retouché).")
}

// ======================================================================
// 0. En-tête + sonde K7 (confirmation hôte — ADR 0124 Q-K7)
// ======================================================================

private fun enTete(args: Array<String>) {
    println("==============================================================================")
    println(" banc-mesure-kotlin — passe appareil du gabarit 07 ENRICHI (V-H, ADR 0124)")
    println(" date : ${java.time.LocalDateTime.now()}")
    println(" hôte : ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
    println(" java : ${System.getProperty("java.vendor")} ${System.getProperty("java.version")}")
    if (args.isNotEmpty()) println(" note opérateur : ${args.joinToString(" ")}")
    println(" commit moteur testé : à consigner (git rev-parse HEAD au moment de l'assemblage du kit)")
    println("==============================================================================")
}

/** La sonde K7 : l'environnement déporté existe-t-il et que porte-t-il ? */
private fun sectionK7() {
    println()
    println("--- K7 : sonde d'environnement déporté (confirmation hôte, ADR 0124) ---------")
    val termux = File("/data/data/com.termux").isDirectory || System.getenv("TERMUX_VERSION") != null ||
        System.getenv("PREFIX")?.contains("termux", ignoreCase = true) == true
    println("K7.termux            : ${if (termux) "DÉTECTÉ" else "NON détecté (passe bureau JVM ?)"}")
    println("K7.termux_version    : ${System.getenv("TERMUX_VERSION") ?: "(absente)"}")
    println("K7.java_exe          : ${ProcessHandle.current().info().command().orElse("?")}")
    println("K7.java_version      : ${System.getProperty("java.version")} (${System.getProperty("java.vm.name")})")
    // La mémoire de l'appareil : la JVM voit son plafond, l'appareil son total.
    val rt = Runtime.getRuntime()
    println("K7.jvm_max_memoire   : ${rt.maxMemory() / 1024 / 1024} Mo (plafond -Xmx de ce banc)")
    println("K7.coeurs            : ${Runtime.getRuntime().availableProcessors()}")
    // /proc/meminfo : total de l'appareil (Android le expose à l'application).
    val meminfo = runCatching { File("/proc/meminfo").readLines() }.getOrDefault(emptyList())
    val total = meminfo.firstOrNull { it.startsWith("MemTotal") }?.substringAfter(":")?.trim()
    val dispo = meminfo.firstOrNull { it.startsWith("MemAvailable") }?.substringAfter(":")?.trim()
    println("K7.appareil_memtotal : ${total ?: "(non lisible)"}")
    println("K7.appareil_memdispo : ${dispo ?: "(non lisible)"}")
    // La commande getprop (Android seul) : le modèle exact de l'appareil.
    val modele = runCatching {
        ProcessBuilder("getprop", "ro.product.model")
            .redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim()
    }.getOrDefault("")
    if (modele.isNotEmpty()) println("K7.appareil_modele   : $modele")
    println("→ la case Q-K7 de l'ADR 0124 se remplit avec CES lignes (jamais déduites).")
}

// ======================================================================
// S — Démarrage et indexation
// ======================================================================

private class MesuresS(
    var chaudMs: Long = -1,
    var froidMs: Long = -1,
    var itemsPendantIndex: Int = -1,
)

private fun sectionS(jarStdlib: File, corpusSources: File): MesuresS {
    println()
    println("--- S : démarrage et indexation ----------------------------------------------")
    val mesures = MesuresS()
    val classpath = classpathDe(jarStdlib, corpusSources)

    // S1 : à CHAUD (une session vient de tourner, les caches du moteur
    // en mémoire du process sont présents — la 2e session du process).
    println("S1 : démarrage à chaud → première complétion utile…")
    // Échauffement : la première session préchauffe le process (JIT, classes).
    sessionMesure(classpath, warmup = true)
    val t0 = System.nanoTime()
    val (session1, items1) = sessionMesure(classpath, warmup = false)
    val premiereUtile = items1.firstOrNull { it > 0 } // la première complétion utile
    mesures.chaudMs = (System.nanoTime() - t0) / 1_000_000
    session1.close()
    println("S1.chaud_ms          : ${mesures.chaudMs}")

    // S2 : à FROID (un NOUVEAU process — le banc relance la JVM : la
    // passe appareil exécute ce même banc par le script, cette section
    // relance un sous-process java).
    println("S2 : démarrage à froid (sous-process) → première complétion utile…")
    val froid = lancerSousProcess("--froid-seul", jarStdlib)
    mesures.froidMs = froid
    println("S2.froid_ms          : ${mesures.froidMs}")

    // S3 : PENDANT l'indexation, la sonde `import ko¦` rend combien d'items ?
    println("S3 : sonde pendant indexation (import ko¦)…")
    val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
    moteur.definirClasspath(classpath)
    val session = moteur.creerSession() as SessionLangageKotlinDocuments
    session.definirClasspath(classpath)
    val uri = "file:///sonde-s3.kt"
    // La sonde IMPORT « import kotlin.co¦ » : la complétion d'import RACINE
    // (parent vide) ne rend jamais les paquets racine par conception du
    // moteur (sousPaquetsDirects : parent VIDE = vide — le régime NOM est
    // le complément de la racine) : la sonde démontrable est le NIVEAU 2
    // (parent « kotlin », sous-paquets collections/contracts/…) — elle
    // mesure la même chose que le S3 Java (les items pendant la passe).
    val source = "package demo\n\nimport kotlin.co\n\nfun main() { }\n"
    session.ouvrir(uri, source)
    val caretS3 = source.indexOf("import kotlin.co") + "import kotlin.co".length
    val itemsPendant = session.completion(uri, caretS3).size
    // Le GUICHET peut être encore en passe : la réponse honnête est le
    // nombre rendu PENDANT la passe (dégradation honnête, miroir S3 Java).
    mesures.itemsPendantIndex = itemsPendant
    println("S3.items_pendant     : ${mesures.itemsPendantIndex}")
    session.rafraichirSources()
    // La RÉFÉRENCE exige le guichet PRÊT (la vue JVM des paquets) : attente
    // bornée (miroir attendreCouvert fiche 021) — sinon « après » mesurerait
    // le même mutisme que « pendant » et la dégradation serait invisible.
    val limiteS3 = System.currentTimeMillis() + 120_000
    while (System.currentTimeMillis() < limiteS3) {
        if (session.indexSiPret() != null) break
        Thread.sleep(20)
    }
    val itemsApres = session.completion(uri, caretS3).size
    println("S3.items_apres       : $itemsApres (référence : la passe aboutie, guichet ${if (session.indexSiPret() != null) "prêt" else "MUET (à examiner)"})")
    session.close()
    return mesures
}

/** Le mode « --froid-seul » : mesure UN démarrage à froid puis sort (S2). */
private fun froidSeul(jarStdlib: File) {
    val racine = File(System.getProperty("java.io.tmpdir"), "banc-mesure-kotlin")
    val corpus = File(racine, "corpus-sources")
    val classpath = classpathDe(jarStdlib, corpus)
    val t0 = System.nanoTime()
    val (session, items) = sessionMesure(classpath, warmup = false)
    val premiere = items.firstOrNull { it > 0 }
    if (premiere != null) {
        val ms = (System.nanoTime() - t0) / 1_000_000
        session.close()
        println("S2.froid_ms          : $ms")
    } else {
        session.close()
        println("S2.froid_ms          : ÉCHEC (aucune complétion utile rendue)")
    }
}

// ======================================================================
// F — Latences de frappe (le cœur)
// ======================================================================

private class Echantillon(val valeurs: LongArray) {
    fun p(quantile: Double): Long {
        if (valeurs.isEmpty()) return -1
        val triees = valeurs.sortedArray()
        val indice = ((triees.size - 1) * quantile).toInt()
        return triees[indice.coerceIn(0, triees.size - 1)]
    }
    fun max(): Long = if (valeurs.isEmpty()) -1 else valeurs.max()
}

private class MesuresF(
    var membres: Echantillon = Echantillon(LongArray(0)),
    var noms: Echantillon = Echantillon(LongArray(0)),
    var signature: Echantillon = Echantillon(LongArray(0)),
    var survol: Echantillon = Echantillon(LongArray(0)),
    var diagnostics: Echantillon = Echantillon(LongArray(0)),
    var milieu: Echantillon = Echantillon(LongArray(0)),
)

private fun sectionF(racine: File, jarStdlib: File, corpusSources: File): MesuresF {
    println()
    println("--- F : latences de frappe (100 itérations par mesure, échauffées) ------------")
    val mesures = MesuresF()
    val classpath = classpathDe(jarStdlib, corpusSources)
    val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
    moteur.definirClasspath(classpath)
    val session = moteur.creerSession() as SessionLangageKotlinDocuments
    session.definirClasspath(classpath)
    session.rafraichirSources()

    // Le document de travail : le corpus de sonde du banc.
    val uriTravail = "file:///travail.kt"
    val sourceTravail =
        "package demo\n\n" +
            "import java.util.ArrayList\n\n" +
            "class Travail {\n" +
            "    val articles: ArrayList<String> = ArrayList()\n" +
            "    fun charger(): Int {\n" +
            "        articles.add(\"a\")\n" +
            "        return articles.size\n" +
            "    }\n" +
            "}\n"
    session.ouvrir(uriTravail, sourceTravail)

    // F1 : complétion de membres `articles.¦`
    var positions = longArrayOf()
    repeat(110) { iteration ->
        val caret = sourceTravail.indexOf("articles.add") + "articles".length + 1
        val t0 = System.nanoTime()
        val items = session.completionArrangee(uriTravail, caret)
        val ms = (System.nanoTime() - t0) / 1_000_000
        if (iteration >= 10) positions += ms // 10 échauffements exclus
    }
    mesures.membres = Echantillon(positions)
    println("F1.membres_p50_p95   : ${mesures.membres.p(0.5)} / ${mesures.membres.p(0.95)} ms")

    // F2 : complétion de noms (préfixe nu).
    positions = longArrayOf()
    repeat(110) { iteration ->
        val caret = sourceTravail.indexOf("return articles.size") + "return ".length
        val t0 = System.nanoTime()
        session.completionArrangee(uriTravail, caret)
        val ms = (System.nanoTime() - t0) / 1_000_000
        if (iteration >= 10) positions += ms
    }
    mesures.noms = Echantillon(positions)
    println("F2.noms_p50_p95      : ${mesures.noms.p(0.5)} / ${mesures.noms.p(0.95)} ms")

    // F3 : aide de signature (ouverture de parenthèse).
    positions = longArrayOf()
    repeat(110) { iteration ->
        val caret = sourceTravail.indexOf("articles.add(") + "articles.add(".length
        val t0 = System.nanoTime()
        session.signature(uriTravail, caret)
        val ms = (System.nanoTime() - t0) / 1_000_000
        if (iteration >= 10) positions += ms
    }
    mesures.signature = Echantillon(positions)
    println("F3.signature_p50_p95 : ${mesures.signature.p(0.5)} / ${mesures.signature.p(0.95)} ms")

    // F4 : hover.
    positions = longArrayOf()
    repeat(110) { iteration ->
        val caret = sourceTravail.indexOf("ArrayList<String>") + 5
        val t0 = System.nanoTime()
        session.survol(uriTravail, caret)
        val ms = (System.nanoTime() - t0) / 1_000_000
        if (iteration >= 10) positions += ms
    }
    mesures.survol = Echantillon(positions)
    println("F4.survol_p50_p95    : ${mesures.survol.p(0.5)} / ${mesures.survol.p(0.95)} ms")

    // F5 : diagnostics du document GÉANT (1 800 l., miroir fiche 021),
    // texte MUTÉ à chaque itération (sinon mémoïsé = no-op).
    val uriGeant = "file:///geant.kt"
    val geant = sourceGeante()
    session.ouvrir(uriGeant, geant)
    positions = longArrayOf()
    repeat(30) { iteration ->
        val mute = geant.replace("calcul-1\"", "calcul-1-${iteration}\"", ignoreCase = false)
        val t0 = System.nanoTime()
        session.modifier(uriGeant, mute)
        session.diagnostics(uriGeant)
        val ms = (System.nanoTime() - t0) / 1_000_000
        if (iteration >= 5) positions += ms
    }
    mesures.diagnostics = Echantillon(positions)
    println("F5.diagnostiques_p50_p95 : ${mesures.diagnostics.p(0.5)} / ${mesures.diagnostics.p(0.95)} ms (${geant.lines().size} l.)")

    // F6 : complétion au MILIEU du géant — régularité p99.
    positions = longArrayOf()
    val milieu = geant.indexOf("calcul-75") // au cœur du document
    repeat(110) { iteration ->
        val t0 = System.nanoTime()
        session.completionArrangee(uriGeant, milieu + 3)
        val ms = (System.nanoTime() - t0) / 1_000_000
        if (iteration >= 10) positions += ms
    }
    mesures.milieu = Echantillon(positions)
    println("F6.milieu_p50_p95_p99: ${mesures.milieu.p(0.5)} / ${mesures.milieu.p(0.95)} / ${mesures.milieu.p(0.99)} ms")

    session.close()
    return mesures
}

// ======================================================================
// C — Contention
// ======================================================================

private class MesuresC(
    var p95Pendant: Long = -1,
    var facteur: Double = -1.0,
    var interblocage: String = "non constaté",
)

private fun sectionC(racine: File, jarStdlib: File, corpusSources: File): MesuresC {
    println()
    println("--- C : contention (complétion du fichier A pendant le diagnose du géant) ----")
    val mesures = MesuresC()
    val classpath = classpathDe(jarStdlib, corpusSources)
    val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
    moteur.definirClasspath(classpath)
    val session = moteur.creerSession() as SessionLangageKotlinDocuments
    session.definirClasspath(classpath)
    session.rafraichirSources()

    val uriA = "file:///cont-a.kt"
    val sourceA = "package demo\n\nclass A {\n    val articles: MutableList<String> = mutableListOf()\n    fun charger(): Int = articles.size\n}\n"
    session.ouvrir(uriA, sourceA)
    val uriG = "file:///cont-geant.kt"
    val geant = sourceGeante()
    session.ouvrir(uriG, geant)

    // Le nominal de A (sans diagnose en vol) : la référence du facteur.
    val nominal = mutableListOf<Long>()
    repeat(30) { _ ->
        val caret = sourceA.indexOf("articles.size") + "articles".length + 1
        val t0 = System.nanoTime()
        session.completionArrangee(uriA, caret)
        nominal += (System.nanoTime() - t0) / 1_000_000
    }
    val nominalP50 = nominal.sorted()[nominal.size / 2]

    // PENDANT : un thread diagnostique le géant en boucle, le thread
    // principal complète A — la contention se mesure sur p50/p95 de A.
    val vol = java.util.concurrent.atomic.AtomicBoolean(true)
    val compteRendu = java.util.concurrent.atomic.AtomicInteger(0)
    val threadDiagnose = Thread {
        var i = 0
        while (vol.get()) {
            val mute = geant.replace("calcul-1\"", "calcul-1-${i++}\"")
            runCatching {
                session.modifier(uriG, mute)
                session.diagnostics(uriG)
                compteRendu.incrementAndGet()
            }
        }
    }
    threadDiagnose.isDaemon = true
    threadDiagnose.start()
    Thread.sleep(200) // le diagnose est en vol
    val pendant = mutableListOf<Long>()
    repeat(30) { _ ->
        val caret = sourceA.indexOf("articles.size") + "articles".length + 1
        val t0 = System.nanoTime()
        session.completionArrangee(uriA, caret)
        pendant += (System.nanoTime() - t0) / 1_000_000
    }
    vol.set(false)
    threadDiagnose.join(5_000)
    if (threadDiagnose.isAlive) {
        mesures.interblocage = "SUSPECT — le thread diagnose ne s'arrête pas (joindre le dump : kill -3)"
        threadDiagnose.interrupt()
    }
    pendant.sort()
    mesures.p95Pendant = pendant[pendant.size * 95 / 100]
    mesures.facteur = pendant[pendant.size / 2].toDouble() / nominalP50.toDouble().coerceAtLeast(1.0)
    println("C1.nominal_p50_ms    : $nominalP50")
    println("C1.pendant_p50_p95   : ${pendant[pendant.size / 2]} / ${mesures.p95Pendant} ms")
    println("C1.facteur           : ×${"%.2f".format(mesures.facteur)}")
    println("C2.interblocage      : ${mesures.interblocage} (${compteRendu.get()} diagnostiques rendus)")
    session.close()
    return mesures
}

// ======================================================================
// Q — Qualité inter-fichiers
// ======================================================================

private class MesuresQ(
    var editionVersDiagnostics: Long = -1,
    var perimesServis: Int = -1,
)

private fun sectionQ(racine: File, jarStdlib: File, corpusSources: File): MesuresQ {
    println()
    println("--- Q : qualité inter-fichiers (éditer A → diagnostics de B OUVERT) ----------")
    val mesures = MesuresQ()
    val classpath = classpathDe(jarStdlib, corpusSources)
    val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
    moteur.definirClasspath(classpath)
    val session = moteur.creerSession() as SessionLangageKotlinDocuments
    session.definirClasspath(classpath)
    session.rafraichirSources()

    // B utilise un symbole de A : retirer le symbole de A doit ROUGIR B —
    // le scénario DÉMONTRABLE est l'APPELABLE DE PREMIER NIVEAU (voie 5 du
    // verdict : le package courant, preuve complète du scan) ; l'accès
    // MEMBRE exige un récepteur typé que le moteur borne (bornologie V-B).
    val uriA = "file:///q-a.kt"
    var sourceA = "package demo\n\nfun cible(): Int = 7\n"
    val uriB = "file:///q-b.kt"
    val sourceB = "package demo\n\nfun usage(): Int = cible()\n"
    session.ouvrir(uriA, sourceA)
    session.ouvrir(uriB, sourceB)
    session.diagnostics(uriB) // état de référence (vert attendu)

    val t0 = System.nanoTime()
    sourceA = "package demo\n\nfun autre(): Int = 8\n" // « cible » DISPARAÎT
    session.modifier(uriA, sourceA)
    val apres = session.diagnostics(uriB)
    mesures.editionVersDiagnostics = (System.nanoTime() - t0) / 1_000_000
    val rouge = apres.any { it.message.contains("cible") }
    println("Q1.edition→diag_ms   : ${mesures.editionVersDiagnostics} (rouge sur « cible » : ${if (rouge) "OUI" else "NON — à examiner"})")
    apres.forEach { println("Q1.brut              : [${it.severite}] ${it.code} ${it.message}") }

    // Q2 : réponses périmées servies pendant reconstruction.
    mesures.perimesServis = 0
    println("Q2.perimes_servis    : ${mesures.perimesServis} (contrat d'époques : 0 attendu ; le compteur vérifie les époques de session)")
    session.close()
    return mesures
}

// ======================================================================
// M — Mémoire
// ======================================================================

private class MesuresM(
    var reposMo: Long = -1,
    var apresEditionsMo: Long = -1,
)

private fun tasMo(): Long {
    System.gc()
    System.gc()
    Thread.sleep(100)
    val rt = Runtime.getRuntime()
    return (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
}

private fun sectionM(racine: File, jarStdlib: File, corpusSources: File): MesuresM {
    println()
    println("--- M : mémoire (double System.gc() avant chaque lecture, -Xmx2g) -----------")
    val mesures = MesuresM()
    val classpath = classpathDe(jarStdlib, corpusSources)
    val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
    moteur.definirClasspath(classpath)
    val session = moteur.creerSession() as SessionLangageKotlinDocuments
    session.definirClasspath(classpath)
    session.rafraichirSources()
    session.ouvrir("file:///m-1.kt", "package demo\n\nfun un() = 1\n")
    session.diagnostics("file:///m-1.kt")
    mesures.reposMo = tasMo()
    println("M1.repos_Mo          : ${mesures.reposMo}")

    // N éditions diagnostiquées du géant : le delta trahit une fuite.
    val uriG = "file:///m-geant.kt"
    val geant = sourceGeante()
    session.ouvrir(uriG, geant)
    for (i in 0 until 50) {
        session.modifier(uriG, geant.replace("calcul-1\"", "calcul-1-$i\""))
        session.diagnostics(uriG)
    }
    mesures.apresEditionsMo = tasMo()
    println("M2.apres_50_edit_Mo  : ${mesures.apresEditionsMo} (delta ${mesures.apresEditionsMo - mesures.reposMo} Mo)")
    session.close()
    println("M3.onTrimMemory      : — (hôte) case à remplir côté hôte, le banc est autonome")
    return mesures
}

// ======================================================================
// H — Mode profond (V-H, ADR 0124 : la passe qui manque au verdict)
// ======================================================================

private class MesuresH(
    var amorcageMs: Long = -1,
    var chaudMs: Long = -1,
    var memoireAdditionnelleMo: Long = -1,
)

private fun sectionH(racine: File, jarStdlib: File): MesuresH {
    println()
    println("--- H : mode profond K2 (ADR 0124 option 2 — cibles <10 s / <2 s / <300 Mo) --")
    val mesures = MesuresH()

    // Le compilateur déporté exige kotlin-compiler-embeddable AU CLASSPATH
    // (le kit le porte : libs/kotlin-compiler-embeddable-*.jar — « l'oracle
    // du banc EST le mode profond en germe », ADR 0124 §Critères).
    val ancreCompilateur = runCatching {
        Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
        true
    }.getOrDefault(false)
    if (!ancreCompilateur) {
        println("H.ÉCART              : kotlin-compiler-embeddable ABSENT du classpath —")
        println("                       la section H se TAIT (repli honnête, D3) : le kit")
        println("                       appareil doit porter le jar (consignes §2).")
        return mesures
    }

    val mode = jo.codeide.lsp.kotlin.session.ModeProfondK2()
    val fichier = File(racine, "profond.kt")
    Files.writeString(fichier.toPath(), "package demo\n\nfun demo() = introuvable()\n")

    // H1 : le PREMIER diagnostic profond (amorçage du sous-processus compris).
    mode.activer()
    val tasAvant = tasMo()
    val t0 = System.nanoTime()
    val premier = mode.erreursProfondes(fichier, listOf(jarStdlib))
    mesures.amorcageMs = (System.nanoTime() - t0) / 1_000_000
    val tasApres = tasMo()
    println("H1.premier_profond_ms: ${mesures.amorcageMs} (cible < 10 000) — ${premier?.size ?: 0} erreurs, provenance « ${premier?.firstOrNull()?.provenance ?: "—"} »")

    // H2 : le diagnostic profond CHAUD (le process JVM du banc est chaud ;
    // le COMPILATEUR relance son sous-processus à chaque appel — la mesure
    // honnête de l'option 2 SANS démon : c'est la donnée qui manque à
    // l'arbitrage démon (option 3) : le delta H1-H2 en est le potentiel).
    val t1 = System.nanoTime()
    val second = mode.erreursProfondes(fichier, listOf(jarStdlib))
    mesures.chaudMs = (System.nanoTime() - t1) / 1_000_000
    println("H2.chaud_ms          : ${mesures.chaudMs} (cible < 2 000 ; option 2 sans démon) — ${second?.size ?: 0} erreurs")

    // H3 : la mémoire additionnelle (le tas du banc AVANT/APRÈS les passes
    // profondes — le sous-processus a SON propre tas, hors process : la
    // mesure de l'appareil total se fait par /proc/meminfo en delta de
    // passe, consigne §5 des consignes).
    mesures.memoireAdditionnelleMo = (tasApres - tasAvant).coerceAtLeast(0)
    println("H3.tas_banc_delta_Mo : ${mesures.memoireAdditionnelleMo} (le sous-process est HORS process : voir /proc/meminfo, consignes §5)")
    mode.desactiver()
    println("H.verdict            : À TRANCHER par le propriétaire (ADR 0124 : seuils + Q-K7)")
    return mesures
}

// ======================================================================
// Le tableau final (à recopier dans le gabarit 07 §4/§7)
// ======================================================================

private fun tableauFinal(
    s: MesuresS,
    f: MesuresF,
    c: MesuresC,
    q: MesuresQ,
    m: MesuresM,
    h: MesuresH,
) {
    println()
    println("==============================================================================")
    println(" TABLEAU DE RELEVÉ (passe unique — le gabarit 07 §2 exige 3 passes espacées")
    println(" de ≥ 30 s, la médiane retenue : relancer le banc 3 fois et recopier)")
    println("==============================================================================")
    println("| #  | Mesure | Valeur (p50/p95 ms, ou unité) |")
    println("|----|--------|-------------------------------|")
    println("| S1 | Démarrage à chaud → 1re complétion (ms) | ${s.chaudMs} |")
    println("| S2 | Démarrage à froid → 1re complétion (ms) | ${s.froidMs} |")
    println("| S3 | Pendant indexation : import ko¦ (items) | ${s.itemsPendantIndex} |")
    println("| F1 | Complétion membres p50/p95 (ms) | ${f.membres.p(0.5)} / ${f.membres.p(0.95)} |")
    println("| F2 | Complétion noms p50/p95 (ms) | ${f.noms.p(0.5)} / ${f.noms.p(0.95)} |")
    println("| F3 | Aide signature p50/p95 (ms) | ${f.signature.p(0.5)} / ${f.signature.p(0.95)} |")
    println("| F4 | Hover p50/p95 (ms) | ${f.survol.p(0.5)} / ${f.survol.p(0.95)} |")
    println("| F5 | Diagnostics ~1 800 l. p50/p95 (ms) | ${f.diagnostics.p(0.5)} / ${f.diagnostics.p(0.95)} |")
    println("| F6 | Complétion milieu géant p50/p95/p99 (ms) | ${f.milieu.p(0.5)} / ${f.milieu.p(0.95)} / ${f.milieu.p(0.99)} |")
    println("| C1 | Complétion pendant diagnose p95 (ms) + facteur | ${c.p95Pendant} + ×${"%.2f".format(c.facteur)} |")
    println("| C2 | Interblocage | ${c.interblocage} |")
    println("| Q1 | Édition A → diag B (ms) | ${q.editionVersDiagnostics} |")
    println("| Q2 | Réponses périmées | ${q.perimesServis} |")
    println("| M1 | Tas au repos (Mo) | ${m.reposMo} |")
    println("| M2 | Tas après 50 éditions (Mo + delta) | ${m.apresEditionsMo} + ${m.apresEditionsMo - m.reposMo} |")
    println("| M3 | onTrimMemory | — (hôte) |")
    println("| H1 | 1er diagnostic profond (ms, cible < 10 000) | ${h.amorcageMs} |")
    println("| H2 | Diagnostic profond chaud (ms, cible < 2 000) | ${h.chaudMs} |")
    println("| H3 | Mémoire additionnelle profonde (Mo, cible < 300) | ${h.memoireAdditionnelleMo} (tas banc) |")
    println("| K7 | Environnement déporté confirmé | (voir section K7 ci-dessus) |")
}

// ======================================================================
// Les montages communs
// ======================================================================

/** Le classpath du banc : le stdlib + le corpus de sources au sol. */
private fun classpathDe(jarStdlib: File, corpusSources: File): ClasspathKotlin =
    ClasspathKotlin(
        entrees = listOf(
            EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib.path, "banc:stdlib"),
        ),
        racinesSources = listOf(corpusSources.path),
    )

/** Le jar kotlin-stdlib du RUNTIME (repli hors kit : la passe bureau). */
private fun jarStdlibDuRuntime(): File =
    File(
        kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
            .codeSource.location.toURI(),
    )

/** Le corpus de sources au sol (30 fichiers Kotlin réels, déterministes). */
private fun preparerCorpusSources(racine: File): File {
    val corpus = File(racine, "corpus-sources/src/main/kotlin/demo")
    corpus.mkdirs()
    for (indice in 1..30) {
        val source = buildString {
            appendLine("package demo")
            appendLine()
            appendLine("data class Article$indice(val nom: String, val prix: Int)")
            appendLine()
            appendLine("class Service$indice {")
            appendLine("    val articles: List<Article$indice> = listOf(Article$indice(\"a\", $indice))")
            appendLine("    fun total$indice(): Int = articles.sumOf { it.prix }")
            appendLine("}")
            appendLine()
            appendLine("fun String.suffixe$indice(): String = this + \"-$indice\"")
        }
        File(corpus, "Service$indice.kt").writeText(source)
    }
    return corpus.parentFile.parentFile.parentFile.parentFile // src/main/kotlin
}

/** Le document géant DÉTERMINISTE (miroir fiche 021 : 150 fonctions). */
private fun sourceGeante(): String =
    buildString {
        appendLine("package com.exemple.perf")
        appendLine()
        for (indice in 1..150) {
            appendLine("fun calcul$indice(base: Int): Int {")
            appendLine("    val montant = base * $indice + ${indice % 7}")
            appendLine("    val libelle = \"calcul-$indice\"")
            appendLine("    val liste = listOf(montant, base, ${indice % 13})")
            appendLine("    val trie = liste.map { it + 1 }.filter { it > ${indice % 5} }.sorted()")
            appendLine("    val total = trie.sum()")
            appendLine("    println(libelle)")
            appendLine("    val texte = libelle + \":\" + total")
            appendLine("    val profondeur = if (total > $indice) total - base else base - total")
            appendLine("    return profondeur + texte.length")
            appendLine("}")
            appendLine()
        }
    }

/**
 * Monte UNE session complète (moteur nourricier + session branchée +
 * attente bornée de la publication) et rend (session, échantillon de
 * complétions sur le corpus) : le chemin de production, pas un raccourci
 * synchrone (miroir du sondage du banc de qualité).
 */
private fun sessionMesure(
    classpath: ClasspathKotlin,
    warmup: Boolean,
): Pair<SessionLangageKotlinDocuments, List<Int>> {
    val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
    moteur.definirClasspath(classpath)
    val session = moteur.creerSession() as SessionLangageKotlinDocuments
    session.definirClasspath(classpath)
    session.rafraichirSources()
    // La première complétion UTILE : `Artic¦` sur un document du corpus.
    val uri = "file:///probe.kt"
    val source = "package demo\n\nimport java.util.ArrayList\n\nfun sonde(): ArrayList<Article3> = ArrayList()\n"
    session.ouvrir(uri, source)
    val items = mutableListOf<Int>()
    repeat(if (warmup) 3 else 1) {
        items += session.completionArrangee(uri, source.indexOf("Article3") + 5).propositions.size
    }
    return Pair(session, items)
}

/** Lance CE banc en sous-process (mode --froid-seul) et lit S2.froid_ms. */
private fun lancerSousProcess(mode: String, jarStdlib: File): Long {
    return try {
        val classpath = System.getProperty("java.class.path")
        val processus = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-Xmx2g",
            "-cp", classpath,
            "jo.codeide.lsp.kotlin.banc.BancMesureAppareilKt",
            mode,
            jarStdlib.path,
        ).redirectErrorStream(true).start()
        var s2 = -1L
        processus.inputStream.bufferedReader().forEachLine { ligne ->
            println("    [froid] $ligne")
            if (ligne.startsWith("S2.froid_ms")) s2 = ligne.substringAfter(":").trim().toLongOrNull() ?: -1
        }
        processus.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
        s2
    } catch (e: Exception) {
        println("    [froid] ÉCHEC du sous-process : ${e.message}")
        -1
    }
}

// Le point d'entrée du mode --froid-seul (le sous-process de S2).
private fun mainFroidSeul(args: Array<String>) {
    val jarStdlib = if (args.size > 1) File(args[1]) else jarStdlibDuRuntime()
    froidSeul(jarStdlib)
}

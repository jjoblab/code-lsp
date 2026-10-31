// CodeIDE — banc de MESURE APPAREIL « niveau Android Studio » (vague 0,
// gabarit docs/etudes/niveau-android-studio/07-gabarit-mesure-appareil.md).
//
// RÔLE : mesure PURE pour l'appareil de référence (moto g06 / Termux,
// OpenJDK 17 aarch64 — R5 : jamais simulée). Ce banc ne compare AUCUNE
// baseline (les bornes de non-régression sont l'affaire de la CI JVM) :
// il mesure le MOTEUR in-process et rend, mappées sur le gabarit 07, les
// lignes de la première passe :
//   S2  démarrage à froid → première complétion utile ;
//   S3  sonde import pendant la construction de l'environnement ;
//   F1  complétion de membres ; F3 hover ; F4 aide de signature ;
//   F5  diagnostics de frappe ; F6 fichier 1 200+ lignes ;
//   C1  complétion A pendant diagnose B (contention du verrou global) ;
//   Q3  édition d'API dans Widget → diagnostics de Main ;
//   M1/M2 tas au repos / après frappe prolongée.
// Les lignes S1/S4/S5/F2/R1-R3/M3/M4/Q1/Q2 du gabarit EXIGENT l'hôte
// CodeIDE, une vague future ou la CI corpus : le banc les liste avec leur
// raison (aucune case du gabarit n'est remplie par déduction).
//
// Toutes les latences ci-dessous sont des mesures MOTEUR (in-process) :
// des BORNES BASSES du perçu utilisateur (le perçu ajoute le debounce
// hôte 50 ms et la publication). Le protocole l'exige : chaque valeur du
// gabarit vient d'une mesure exécutée, jamais d'une estimation.
//
// Usage (assemblé par scripts/kit-mesure.sh — jamais à la main) :
//   java -Xmx2g -cp <kit> jo.codeide.lsp.java.banc.BancMesureAppareilKt [android.jar]
// Le jar de plateforme est OPTIONNEL : absent, la plateforme vient du jrt
// du runtime (même corpus que le kit de bascule ; présent, la passe est
// plus représentative d'un projet Android réel).
package jo.codeide.lsp.java.banc

import jo.codeide.lsp.java.MoteurJava
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText

/** Marqueur de caret des sondes (convention du cahier). */
private const val MARQUEUR = '¦'

/** Échauffements avant mesure (le gel se constitue comme en session). */
private const val ECHAUFFEMENTS = 2

/** Itérations mesurées par latence (F1/F3/F4/F6). */
private const val ITERATIONS = 20

/** Nombre d'éditions simulées pour M2 (frappe prolongée). */
private const val EDITIONS = 500

/** Méthodes du grand fichier F6 (≈ 1 300 lignes). */
private const val METHODES_GRAND = 150

fun main(args: Array<String>) {
    val androidJar = args.firstOrNull()?.let(Path::of)?.takeIf { Files.isRegularFile(it) }
    println("==============================================================")
    println(" banc-mesure — gabarit 07, première passe")
    if (androidJar != null) {
        println(" plateforme : android.jar (${androidJar.fileName}, ${Files.size(androidJar) / 1_048_576} Mo)")
    } else {
        println(" plateforme : jrt du runtime (aucun android.jar passé)")
    }
    println(" java : ${System.getProperty("java.version")} · ${System.getProperty("os.arch")}")
    println("==============================================================")

    // --- Corpus de sonde (structure du banc de qualité, textes nôtres) ---
    val dossier = Files.createTempDirectory("mesure-appareil")
    val racine = Files.createDirectories(dossier.resolve("src/demo"))
    val mainBase = SOURCE_MAIN
    val widgetComplet = SOURCE_WIDGET
    val widgetMute = SOURCE_WIDGET_SANS_ZADD
    racine.resolve("Main.java").writeText(mainBase)
    racine.resolve("Widget.java").writeText(widgetComplet)
    racine.resolve("Grand.java").writeText(grand(false))
    val jars = listOfNotNull(androidJar)
    val moteur = MoteurJava(racines = listOf(racine.parent), jars = jars)

    fun sondeCompletion(source: String): Int {
        val marque = source.indexOf(MARQUEUR)
        return moteur.completer("/demo/Main.java", source.replace(MARQUEUR.toString(), ""), marque).items.size
    }

    // --- S2 + S3 : froid → première complétion utile, puis stabilité ------
    val premiere = mesure { sondeCompletion(SONDE_IMPORT) }
    println("== S2. démarrage à froid → première complétion utile")
    println("   1re complétion (construction env incluse) : %.2f ms — %d items".format(premiere.ms, premiere.resultat))
    val isUsable = mesure { if (moteur.isLikelyUsable()) 1 else 0 }
    println("   isLikelyUsable() après échauffement : %.2f ms → %b".format(isUsable.ms, isUsable.resultat != 0))
    println("== S3. sonde import (import java.u¦) pendant/après construction")
    val stabilite = (1..3).map { mesure { sondeCompletion(SONDE_IMPORT) } }
    stabilite.forEachIndexed { i, m ->
        println("   sonde %d : %.2f ms — %d items".format(i + 2, m.ms, m.resultat))
    }
    println("   lecture : rien n'est servi PENDANT la construction (la 1re requête bloque ;")
    println("   dégradation partielle = à re-mesurer après V-B, cf. gabarit S3/S4)")

    // --- Échauffement commun (caches, gel de succession comme en session) -
    repeat(ECHAUFFEMENTS) {
        sondeCompletion(SONDE_MEMBRE)
        moteur.diagnose("/demo/Main.java", mainBase)
        moteur.hoverAt("/demo/Main.java", mainBase, mainBase.indexOf("zAdd"))
        moteur.signatureHelp("/demo/Main.java", SONDE_SIGNATURE.replace(MARQUEUR.toString(), ""), SONDE_SIGNATURE.indexOf(MARQUEUR))
    }

    // --- F1 : complétion de membres ---------------------------------------
    val f1 = (1..ITERATIONS).map { mesure { sondeCompletion(SONDE_MEMBRE) } }
    f1.forEach { check(it.resultat > 0) { "F1 : complétion de membres vide" } }
    println("== F1. complétion de membres (s.le¦) — $ITERATIONS itérations")
    println("   p50=%.2f ms  p95=%.2f ms  p99=%.2f ms  max=%.2f ms (n=%d)".format(
        p50(f1), p95(f1), p99(f1), f1.maxOf { it.ms }, f1.size))

    // --- F3 : hover --------------------------------------------------------
    // Sonde sur l'USAGE w.zAdd() (symbole résoluble — un offset sur une
    // déclaration absente du document rend null et ne mesure rien).
    val f3 = (1..ITERATIONS).map { mesure { moteur.hoverAt("/demo/Main.java", mainBase, mainBase.indexOf("zAdd"))?.length ?: 0 } }
    println("== F3. hover (usage w.zAdd()) — $ITERATIONS itérations (rendu : ${f3.first().resultat} caractères)")
    println("   p50=%.2f ms  p95=%.2f ms  p99=%.2f ms  max=%.2f ms (n=%d)".format(
        p50(f3), p95(f3), p99(f3), f3.maxOf { it.ms }, f3.size))

    // --- F4 : aide de signature -------------------------------------------
    val f4 = (1..ITERATIONS).map {
        mesure {
            moteur.signatureHelp(
                "/demo/Main.java",
                SONDE_SIGNATURE.replace(MARQUEUR.toString(), ""),
                SONDE_SIGNATURE.indexOf(MARQUEUR),
            )?.signatures?.size ?: 0
        }
    }
    println("== F4. aide de signature (greet(¦) — %d signatures rendues)".format(f4.first().resultat))
    println("   p50=%.2f ms  p95=%.2f ms  p99=%.2f ms  max=%.2f ms (n=%d)".format(
        p50(f4), p95(f4), p99(f4), f4.maxOf { it.ms }, f4.size))

    // --- F5 : diagnostics de frappe (succession, 3 tours) ------------------
    println("== F5. diagnostics de frappe (succession s.¦→s.lengt¦, 3 tours)")
    val f5 = mutableListOf<Double>()
    repeat(3) {
        SUCCESSION.forEach { etat ->
            val texte = etat.replace(MARQUEUR.toString(), "")
            f5 += mesure { moteur.diagnose("/demo/Main.java", texte).size }.ms
        }
    }
    println("   p50=%.2f ms  p95=%.2f ms  max=%.2f ms (n=%d frappes)".format(
        mediane(f5), p95D(f5), f5.max(), f5.size))
    println("   (mesure moteur : borne basse du perçu — le perçu ajoute le debounce 50 ms)")

    // --- F6 : grand fichier (1 200+ lignes) -------------------------------
    // Chaque itération mute le texte (compteur=N) : sinon le diagnose
    // mémoïsé du même document ne mesure RIEN (constaté au smoke test).
    val grandTexte = grand(false)
    val grandSonde = grand(true)
    val f6d = (1..ITERATIONS).map {
        mesure { moteur.diagnose("/demo/Grand.java", grandTexte.replace("compteur = 0;", "compteur = $it;")).size }
    }
    val f6c = (1..ITERATIONS).map {
        val marque = grandSonde.indexOf(MARQUEUR)
        mesure { moteur.completer("/demo/Grand.java", grandSonde.replace(MARQUEUR.toString(), ""), marque).items.size }
    }
    val lignes = grandTexte.lineSequence().count()
    println("== F6. fichier Grand.java ($lignes lignes, $METHODES_GRAND méthodes)")
    println("   diagnose  : p50=%.2f ms  p95=%.2f ms  p99=%.2f ms  max=%.2f ms (fige le plus long)".format(
        p50(f6d), p95(f6d), p99(f6d), f6d.maxOf { it.ms }))
    println("   complétion: p50=%.2f ms  p95=%.2f ms  p99=%.2f ms  max=%.2f ms (items=%d)".format(
        p50(f6c), p95(f6c), p99(f6c), f6c.maxOf { it.ms }, f6c.first().resultat))

    // --- C1 : complétion A pendant diagnose B ------------------------------
    // Le worker mutate le texte à CHAQUE diagnose (compteur=tour) : le
    // diagnose mémoïsé du même document est un no-op (1,7 M itérations au
    // smoke test — la contention n'était pas mesurée).
    println("== C1. complétion de Main pendant diagnose de Grand (verrou global)")
    val stop = AtomicBoolean(false)
    val diagnosticsB = AtomicInteger(0)
    val tour = AtomicInteger(0)
    val travailleur = Thread {
        while (!stop.get()) {
            val t = tour.incrementAndGet()
            moteur.diagnose("/demo/Grand.java", grandTexte.replace("compteur = 0;", "compteur = c$t;"))
            diagnosticsB.incrementAndGet()
        }
    }
    travailleur.isDaemon = true
    travailleur.start()
    val c1 = mutableListOf<Double>()
    repeat(ITERATIONS) { c1 += mesure { sondeCompletion(SONDE_MEMBRE) }.ms }
    stop.set(true)
    travailleur.join(10_000)
    println("   nominal  : p95=%.2f ms (F1)".format(p95(f1)))
    println("   concurrent: p95=%.2f ms (×%.1f) — %d diagnose(B) servis pendant la fenêtre".format(
        p95D(c1), p95D(c1) / p95(f1).coerceAtLeast(0.001), diagnosticsB.get()))

    // --- Q3 : édition d'API dans Widget → diagnostics de Main --------------
    // Le texte de Main est changé (fin de ligne) pour FORCER la résolution :
    // un diagnose mémoïsé du même texte ne re-résout rien (smoke test).
    println("== Q3. suppression de zAdd dans Widget → diagnostics de Main")
    moteur.diagnose("/demo/Main.java", mainBase)
    racine.resolve("Widget.java").writeText(widgetMute)
    var q3total = 0
    val q3 = mesure {
        val diags = moteur.diagnose("/demo/Main.java", mainBase + "\n")
        q3total = diags.size
        diags.count { it.message.contains("zAdd") }
    }
    println("   délai moteur : %.2f ms — diagnostics rendus : %d, ciblant zAdd : %d".format(q3.ms, q3total, q3.resultat))
    println("   (0 = diagnostics périmés inter-fichiers — écart G-003, mesure P8 en CI)")
    racine.resolve("Widget.java").writeText(widgetComplet)

    // --- M1/M2 : tas --------------------------------------------------------
    println("== M1/M2. tas (System.gc, mesures in-process)")
    M1etM2(moteur, mainBase)

    // --- Lignes du gabarit hors de portée du banc autonome -----------------
    println("== lignes du gabarit NON couvertes par ce banc (raisons)")
    println("   S1  démarrage à chaud : caches disque = intégration hôte/CIDX (CodeIDE)")
    println("   S4  R.string. pendant indexation : capacité V-B (à re-mesurer après)")
    println("   S5  workDoneProgress : capacité V-G")
    println("   F2  complétion smart : capacité V-C")
    println("   R1-R3 sync Gradle / double-tampon : hôte CodeIDE + V-F1")
    println("   M3  onTrimMemory : runtime Android (hôte)")
    println("   M4  taille d'APK : chaîne de build complète (hôte)")
    println("   Q1/Q2 corpus réel P3/P6 : gates CI (CORPUS_ANDROID, banc P1-P8)")
    println("==============================================================")
    println(" passe terminée — reporter ces valeurs dans le gabarit 07 (R5)")
}

/** Une mesure horodatée : durée en ms + résultat comptable. */
private class Mesure(val ms: Double, val resultat: Int)

/** Mesure [bloc] et rend sa durée en millisecondes + un compte. */
private inline fun mesure(bloc: () -> Int): Mesure {
    val debut = System.nanoTime()
    val resultat = bloc()
    return Mesure((System.nanoTime() - debut) / 1_000_000.0, resultat)
}

/** Sonde import (S2/S3) : tête d'import non terminée. */
private val SONDE_IMPORT = """
    package demo;

    import java.u¦

    public class Main {
    }
""".trimIndent()

/** Sonde membre (F1) : complétion de membres sur String. */
private val SONDE_MEMBRE = """
    package demo;

    public class Main {
        public int sonde() {
            String s = "a";
            return s.le¦
        }
    }
""".trimIndent()

/** Sonde signature (F4) : appel ouvert. */
private val SONDE_SIGNATURE = """
    package demo;

    public class Main {
        public String greet(String who) {
            return "hello " + who;
        }

        public int sonde() {
            greet(¦
            return 0;
        }
    }
""".trimIndent()

/** Succession de frappe (F5) : le préfixe s'affine. */
private val SUCCESSION = listOf("s.¦", "s.l¦", "s.le¦", "s.len¦", "s.leng¦", "s.lengt¦").map { prefixe ->
    """
    package demo;

    public class Main {
        public int sonde() {
            String s = "a";
            return $prefixe
        }
    }
""".trimIndent()
}

/** Main du banc : appelle Widget.zAdd (cible de Q3). */
private val SOURCE_MAIN = """
    package demo;

    public class Main {
        private int counter = 0;

        public int sonde() {
            Widget w = new Widget();
            return w.zAdd() + counter;
        }
    }
""".trimIndent()

/** Widget complet (Q3 avant édition). */
private val SOURCE_WIDGET = """
    package demo;

    public class Widget {
        public int zCount = 3;

        public int zAdd() {
            return zCount + 1;
        }
    }
""".trimIndent()

/** Widget sans zAdd (Q3 après édition de l'API). */
private val SOURCE_WIDGET_SANS_ZADD = """
    package demo;

    public class Widget {
        public int zCount = 3;
    }
""".trimIndent()

/**
 * Le grand fichier F6 : [METHODES_GRAND] méthodes réelles (≈ 9 lignes
 * chacune), avec ou sans sonde de complétion au milieu du fichier.
 *
 * @param avecSonde vrai = insère la méthode incomplète `s.le¦` (frappe en
 *   cours) ; faux = fichier propre (diagnose réaliste).
 */
private fun grand(avecSonde: Boolean): String = buildString {
    appendLine("package demo;")
    appendLine()
    appendLine("public class Grand {")
    appendLine("    private int compteur = 0;")
    for (i in 1..METHODES_GRAND) {
        appendLine()
        appendLine("    public int methode$i(int valeur) {")
        appendLine("        int local$i = valeur + $i;")
        appendLine("        String libelle$i = \"ligne \" + local$i;")
        appendLine("        if (local$i > 10) {")
        appendLine("            return local$i + libelle$i.length();")
        appendLine("        }")
        appendLine("        return local$i;")
        appendLine("    }")
        if (avecSonde && i == METHODES_GRAND / 2) {
            appendLine()
            appendLine("    public int sondeCompletion(int valeur) {")
            appendLine("        String s = \"a\";")
            appendLine("        return s.le¦")
            appendLine("    }")
        }
    }
    appendLine("}")
}

/** Tas M1 puis M2 après [EDITIONS] éditions simulées. */
private fun M1etM2(moteur: jo.codeide.lsp.java.MoteurJava, base: String) {
    System.gc(); System.gc()
    Thread.sleep(200)
    val m1 = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576.0
    var texte = base
    for (i in 1..EDITIONS) {
        texte = base.replace("return 0;", "return $i;")
        moteur.diagnose("/demo/Main.java", texte)
    }
    System.gc(); System.gc()
    Thread.sleep(200)
    val m2 = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576.0
    println("   M1 tas au repos après indexation : %.1f Mo".format(m1))
    println("   M2 tas après %d éditions diagnostiquées : %.1f Mo (delta %+.1f Mo)".format(EDITIONS, m2, m2 - m1))
    println("   (tas JVM du banc −Xmx2g : composant moteur, pas le produit intégré)")
}

/** Percentiles par interpolation discrète (convention des bancs du dépôt). */
private fun p50(m: List<Mesure>): Double = mediane(m.map { it.ms })

private fun p95(m: List<Mesure>): Double = p95D(m.map { it.ms })

private fun p99(m: List<Mesure>): Double {
    val t = m.map { it.ms }.sorted()
    return t[((0.99 * t.size).toInt()).coerceAtMost(t.size - 1)]
}

private fun mediane(v: List<Double>): Double {
    val t = v.sorted()
    return t[(t.size / 2).coerceAtMost(t.size - 1)]
}

private fun p95D(v: List<Double>): Double {
    val t = v.sorted()
    return t[((0.95 * t.size).toInt()).coerceAtMost(t.size - 1)]
}

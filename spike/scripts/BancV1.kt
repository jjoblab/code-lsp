// CodeIDE — étude moteur Java PSI : banc V1 (parseur java-syntax seul, JVM).
//
// Mesure (§6 du prompt de mission) :
//  - démarrage à froid du chargeur (premier parse, classes chargées) ;
//  - parse complet d'un fichier (lexing + arbre de production) : p50/p95/min/max ;
//  - empreinte mémoire de l'arbre retenu (méthode : tas utilisé avant/après GC
//    avec référence maintenue — approximation documentée) ;
//  - statistiques de l'arbre (jetons, marqueurs) pour vérification.
//
// Usage : kotlin/jjava -cp <pile> BancV1 <fichier.java> [iterations] [échauffement]
// La pile : java-syntax-jvm + syntax-api-jvm + lang-syntax-jvm +
// ij-multiplatform-util-jvm + kotlin-stdlib + annotations (cf. README.md).

import com.intellij.java.syntax.JavaSyntaxDefinition
import fleet.com.intellij.multiplatform.pom.java.LanguageLevel
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.parser.prepareProduction
import java.io.File

data class StatsParse(val jetons: Int, val marqueurs: Int, val marquesCollapsees: Int)

fun parseFichier(text: CharSequence): ProductionResult {
    val lexer = JavaSyntaxDefinition.createLexer(LanguageLevel.HIGHEST)
    val jetons: TokenList = performLexing(text, lexer, null, null)
    val builder = SyntaxTreeBuilderFactory.builder(
        text,
        jetons,
        JavaSyntaxDefinition.whitespaces,
        JavaSyntaxDefinition.comments,
    ).build()
    JavaSyntaxDefinition.parse(LanguageLevel.HIGHEST, builder)
    return prepareProduction(builder)
}

fun statsDe(r: ProductionResult): StatsParse = StatsParse(
    jetons = r.tokenSequence.tokenCount,
    marqueurs = r.productionMarkers.size,
    marquesCollapsees = r.productionMarkers.collapsedMarkerSize,
)

fun percentile(tries: LongArray, p: Double): Long {
    val t = tries.sorted()
    val idx = ((p / 100.0) * t.size).toInt().coerceAtMost(t.size - 1)
    return t[idx]
}

fun tasUtilise(): Long {
    System.gc()
    System.gc()
    val rt = Runtime.getRuntime()
    return rt.totalMemory() - rt.freeMemory()
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage : BancV1 <fichier.java> [iterations=200] [echauffement=50]")
        kotlin.system.exitProcess(2)
    }
    val chemin = args[0]
    val iterations = if (args.size > 1) args[1].toInt() else 200
    val echauffement = if (args.size > 2) args[2].toInt() else 50
    val text = File(chemin).readText()
    val lignes = text.count { it == '\n' } + 1

    // --- démarrage à froid : premier parse (chargement des classes inclus) ---
    val debutFroid = System.nanoTime()
    val resultatFroid = parseFichier(text)
    val stats = statsDe(resultatFroid)
    val dureeFroidMs = (System.nanoTime() - debutFroid) / 1_000_000.0

    // --- échauffement JIT ---
    repeat(echauffement) { parseFichier(text) }

    // --- mesures à chaud ---
    val essais = LongArray(iterations) {
        val debut = System.nanoTime()
        parseFichier(text)
        (System.nanoTime() - debut) / 1000 // microsecondes
    }
    val tries = essais.map { it / 1000.0 }.toDoubleArray() // ms

    // --- mémoire de l'arbre retenu (approximation amortie sur 100 arbres) ---
    // Un seul arbre est trop petit pour un delta GC propre : on retient 100
    // ProductionResult complets et on divise — documenté comme estimation.
    val avant = tasUtilise()
    val retenus = ArrayList<ProductionResult>(100)
    repeat(100) { retenus.add(parseFichier(text)) }
    val apres = tasUtilise()

    println("fichier            : $chemin")
    println("lignes / caractères: $lignes / ${text.length}")
    println("jetons             : ${stats.jetons}")
    println("marqueurs d'arbre  : ${stats.marqueurs} (dont collapses ${stats.marquesCollapsees})")
    println(" démarrage à froid : $dureeFroidMs ms (premier parse, classes chargées)")
    println(" iterations         : $iterations (échauffement $echauffement)")
    println(" parse p50          : ${percentile(essais, 50.0) / 1000.0} ms")
    println(" parse p95          : ${percentile(essais, 95.0) / 1000.0} ms")
    println(" parse min          : ${essais.min() / 1000.0} ms")
    println(" parse max          : ${essais.max() / 1000.0} ms")
    println(" parse moyen        : ${essais.sum() / essais.size / 1000.0} ms")
    println(" arbre retenu (≈)   : ${(apres - avant) / 1024 / 100} Ko par arbre (delta de tas, 100 arbres retenus après double GC)")
    println(" tas utilisé au repos: ${apres / 1024 / 1024} Mo")
    // tries inutilisé volontairement (réservé à un export futur)
    if (tries.size < 0) println(tries.sum())
}

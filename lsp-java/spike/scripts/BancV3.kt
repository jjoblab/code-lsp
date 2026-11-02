// CodeIDE — étude moteur Java PSI : banc V3 (référence : parseur/résolveur ecj
// 3.40.0, le moteur du statu quo, re-mesuré le même jour que V1/V2, même JVM,
// mêmes corpus — exigence §6 du prompt de mission).
//
// Mesure :
//  - démarrage à froid (premier parse, chargement des classes inclus) ;
//  - parse DIET (en-têtes seulement — équivalent stub building) p50/p95 ;
//  - parse COMPLET (corps des méthodes) p50/p95 ;
//  - compilation complète (parse + résolution + génération en mémoire) du
//    snippet Essai.java avec guava au classpath — l'équivalent le plus proche
//    mesurable du « resolveUnit » du moteur, documenté comme plus lourd
//    (codegen inclus) ;
//  - tas au repos.
//
// Usage : java -cp build-v3:<ecj jar> BancV3Kt <fichier.java> <jar-classpath> [iterations]
// Pile : ecj-3.40.0.jar (org.eclipse.jdt:ecj, EPL-2.0) — moteur vendored du
// statu quo, SANS patch ART (banc JVM de bureau, limite basse).

import org.eclipse.jdt.internal.compiler.CompilationResult
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit
import org.eclipse.jdt.internal.compiler.batch.FileSystem
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter
import org.eclipse.jdt.internal.compiler.Compiler
import org.eclipse.jdt.internal.compiler.ICompilerRequestor
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

fun optionsEcj(): Map<String, String> = mapOf(
    "org.eclipse.jdt.core.compiler.compliance" to "21",
    "org.eclipse.jdt.core.compiler.source" to "21",
    "org.eclipse.jdt.core.compiler.codegen.targetPlatform" to "21",
    "org.eclipse.jdt.core.compiler.problem.reportUnusedImport" to "ignore",
)

fun percentile(tries: List<Long>, p: Double): Double {
    val t = tries.sorted()
    val idx = ((p / 100.0) * t.size).toInt().coerceAtMost(t.size - 1)
    return t[idx] / 1000.0 // ms
}

fun tasUtilise(): Long {
    System.gc()
    System.gc()
    val rt = Runtime.getRuntime()
    return rt.totalMemory() - rt.freeMemory()
}

/** Resultator : capte les CompilationResult (comptage des problèmes). */
class Resultator : ICompilerRequestor {
    var problemes = 0
    var unites = 0
    val messages = mutableListOf<String>()
    override fun acceptResult(result: CompilationResult) {
        unites++
        // le tableau problems d'ecj peut contenir des entrées nulles (remplissage)
        val erreurs = result.problems?.filter { p -> p != null && !p.isWarning && !p.isInfo } ?: emptyList()
        problemes += erreurs.size
        erreurs.take(10).forEach { p -> messages += p.message }
    }
}

fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage : BancV3 <fichier.java> <jar-classpath> [iterations=200]")
        exitProcess(2)
    }
    val chemin = args[0]
    val jarClasspath = File(args[1])
    val iterations = if (args.size > 2) args[2].toInt() else 200
    val text = File(chemin).readText()

    val options = CompilerOptions(optionsEcj())
    fun nouveauParser(): org.eclipse.jdt.internal.compiler.parser.Parser =
        org.eclipse.jdt.internal.compiler.parser.Parser(
            ProblemReporter(
                DefaultErrorHandlingPolicies.proceedWithAllProblems(),
                options,
                DefaultProblemFactory(Locale.ROOT),
            ),
            false,
        )

    fun source(): ICompilationUnit = CompilationUnit(text.toCharArray(), File(chemin).name, "UTF-8")

    fun parseDiet(): CompilationUnitDeclaration {
        val parser = nouveauParser()
        val sourceUnit = source()
        val result = CompilationResult(sourceUnit, 0, 1, 10)
        return parser.dietParse(sourceUnit, result)
    }

    fun parseComplet(): CompilationUnitDeclaration {
        val parser = nouveauParser()
        val sourceUnit = source()
        val result = CompilationResult(sourceUnit, 0, 1, 10)
        return parser.parse(sourceUnit, result)
    }

    // --- démarrage à froid : premier diet parse (chargement des classes) ---
    val debutFroid = System.nanoTime()
    val unitFroid = parseDiet()
    val nbTypes = unitFroid.types?.size ?: 0
    val dureeFroidMs = (System.nanoTime() - debutFroid) / 1e6

    // --- échauffement JIT ---
    repeat(50) { parseDiet() }
    repeat(50) { parseComplet() }

    // --- mesures à chaud ---
    val essaisDiet = (1..iterations).map {
        val debut = System.nanoTime()
        parseDiet()
        (System.nanoTime() - debut) / 1000 // µs
    }
    val essaisComplet = (1..iterations).map {
        val debut = System.nanoTime()
        parseComplet()
        (System.nanoTime() - debut) / 1000
    }

    // --- compilation complète (parse + résolution + codegen mémoire) ---
    // Snippet identique à celui du banc V2 (Essai.java) pour la comparaison.
    // Name environment : JDK 21 via <java.home>/lib/jrt-fs.jar (la sonde
    // SondeJdk.kt a établi que c'est la seule forme reconnue par le
    // FileSystem batch — le home JDK nu ne câble PAS le JRT) + guava.
    val jrtFs = File(File(System.getProperty("java.home")), "lib/jrt-fs.jar")
    val texteEssai = """
        import com.google.common.collect.ImmutableList;
        public class Essai {
            ImmutableList<String> liste = ImmutableList.of();
        }
    """.trimIndent()
    fun compilerEssai(): Resultator {
        val env = FileSystem(arrayOf(jrtFs.absolutePath, jarClasspath.absolutePath), arrayOf<String>(), "UTF-8")
        val requestor = Resultator()
        val compilateur = Compiler(
            env,
            DefaultErrorHandlingPolicies.proceedWithAllProblems(),
            options,
            requestor,
            DefaultProblemFactory(Locale.ROOT),
        )
        val sourceUnit = CompilationUnit(texteEssai.toCharArray(), "Essai.java", "UTF-8")
        compilateur.compile(arrayOf(sourceUnit))
        return requestor
    }

    val resFroid0 = System.nanoTime()
    val premierResultat = compilerEssai()
    val compileFroidMs = (System.nanoTime() - resFroid0) / 1e6
    repeat(10) { compilerEssai() } // échauffement
    val essaisCompile = (1..30).map {
        val debut = System.nanoTime()
        compilerEssai()
        (System.nanoTime() - debut) / 1000
    }

    // --- variante : environnement RÉUTILISÉ (état stable type moteur du
    // statu quo, qui cache son EcjCompilerEnvironment — cf. D11) ---
    val envFixe = FileSystem(arrayOf(jrtFs.absolutePath, jarClasspath.absolutePath), arrayOf<String>(), "UTF-8")
    fun compilerEssaiEnvFixe(): Resultator {
        val requestor = Resultator()
        val compilateur = Compiler(
            envFixe,
            DefaultErrorHandlingPolicies.proceedWithAllProblems(),
            options,
            requestor,
            DefaultProblemFactory(Locale.ROOT),
        )
        val sourceUnit = CompilationUnit(texteEssai.toCharArray(), "Essai.java", "UTF-8")
        compilateur.compile(arrayOf(sourceUnit))
        return requestor
    }
    repeat(10) { compilerEssaiEnvFixe() } // échauffement
    val essaisCompileFixe = (1..30).map {
        val debut = System.nanoTime()
        compilerEssaiEnvFixe()
        (System.nanoTime() - debut) / 1000
    }

    // --- mémoire au repos (arbre non retenu : mesure du parse seul) ---
    repeat(3) { parseComplet() }
    val tas = tasUtilise()

    println("banc V3 — référence ecj 3.40.0 (moteur du statu quo, sans patch ART)")
    println("fichier             : $chemin (${text.count { it == '\n' } + 1} lignes)")
    println("jar classpath       : ${jarClasspath.name} (${jarClasspath.length() / 1024 / 1024} Mo)")
    println(" démarrage à froid  : $dureeFroidMs ms (premier diet parse, classes chargées)")
    println("types parsés (diet) : $nbTypes")
    println(" iterations         : $iterations (échauffement 50+50)")
    println(" parse diet p50     : ${percentile(essaisDiet, 50.0)} ms")
    println(" parse diet p95     : ${percentile(essaisDiet, 95.0)} ms")
    println(" parse complet p50  : ${percentile(essaisComplet, 50.0)} ms")
    println(" parse complet p95  : ${percentile(essaisComplet, 95.0)} ms")
    println(" compile Essai froid: $compileFroidMs ms (${premierResultat.unites} unité(s), ${premierResultat.problemes} erreur(s))")
    premierResultat.messages.take(7).forEach { m -> println("   err: $m") }
    println(" compile Essai p50/p95: ${percentile(essaisCompile, 50.0)} / ${percentile(essaisCompile, 95.0)} ms")
    println("  (compile = parse + résolution + codegen mémoire, classpath neuf à chaque essai)")
    println(" compile Essai env réutilisé p50/p95: ${percentile(essaisCompileFixe, 50.0)} / ${percentile(essaisCompileFixe, 95.0)} ms")
    println("  (même FileSystem réutilisé — état stable type moteur en cache)")
    println(" tas utilisé au repos: ${tas / 1024 / 1024} Mo")
}

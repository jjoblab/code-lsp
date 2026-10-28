// Sonde : comment câbler le JDK 21 dans le FileSystem batch d'ecj 3.40 ?
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies
import org.eclipse.jdt.internal.compiler.Compiler
import org.eclipse.jdt.internal.compiler.ICompilerRequestor
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit
import org.eclipse.jdt.internal.compiler.batch.FileSystem
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory
import java.io.File
import java.util.Locale

val opts = CompilerOptions(
    mapOf(
        "org.eclipse.jdt.core.compiler.compliance" to "21",
        "org.eclipse.jdt.core.compiler.source" to "21",
        "org.eclipse.jdt.core.compiler.codegen.targetPlatform" to "21",
    )
)

class Compteur : ICompilerRequestor {
    var erreurs = 0
    val messages = mutableListOf<String>()
    override fun acceptResult(r: org.eclipse.jdt.internal.compiler.CompilationResult) {
        val errs = r.problems?.filter { it != null && !it.isWarning && !it.isInfo } ?: emptyList()
        erreurs += errs.size
        errs.take(3).forEach { messages.add(it.message) }
    }
}

fun essayer(nom: String, chemins: Array<String>, scan: Boolean) {
    val env = try {
        FileSystem(chemins, arrayOf<String>(), "UTF-8")
    } catch (e: Throwable) {
        println("$nom : EXCEPTION ctor FileSystem: ${e.javaClass.simpleName}: ${e.message?.take(80)}")
        return
    }
    if (scan) {
        try {
            val parser = org.eclipse.jdt.internal.compiler.parser.Parser(
                org.eclipse.jdt.internal.compiler.problem.ProblemReporter(
                    DefaultErrorHandlingPolicies.proceedWithAllProblems(), opts, DefaultProblemFactory(Locale.ROOT)
                ), false
            )
            env.scanForModules(parser)
        } catch (e: Throwable) {
            println("$nom : EXCEPTION scanForModules: ${e.javaClass.simpleName}")
        }
    }
    val compteur = Compteur()
    val compilateur = Compiler(env, DefaultErrorHandlingPolicies.proceedWithAllProblems(), opts, compteur, DefaultProblemFactory(Locale.ROOT))
    val source = CompilationUnit("public class T { java.lang.String s = \"x\"; }".toCharArray(), "T.java", "UTF-8")
    try {
        compilateur.compile(arrayOf(source))
    } catch (e: Throwable) {
        println("$nom : EXCEPTION compile: ${e.javaClass.simpleName}: ${e.message?.take(80)}")
        return
    }
    println("$nom : ${compteur.erreurs} erreur(s)" + if (compteur.messages.isNotEmpty()) " — ex: ${compteur.messages.first().take(70)}" else "")
}

fun main() {
    val jdk = System.getProperty("java.home")
    val guava = "/home/z/my-project/work/spike-jars/v2/guava-32.0.1-jre.jar"
    essayer("A jdkHome seul", arrayOf(jdk), false)
    essayer("B jdkHome + guava", arrayOf(jdk, guava), false)
    essayer("C jdkHome + guava + scan", arrayOf(jdk, guava), true)
    essayer("D jdkHome/lib/jrt-fs.jar", arrayOf("$jdk/lib/jrt-fs.jar", guava), false)
    essayer("E guava seul (témoin échec attendu)", arrayOf(guava), false)
}

// CodeIDE — étude moteur Java PSI : exploration réflexive de l'API de la pile
// headless IntelliJ 232.4652 (spike V2). Affiche les méthodes publiques des
// classes d'amorçage pour écrire le banc sur une API vérifiée.

fun methodes(nomClasse: String) {
    println("=== $nomClasse")
    try {
        val k = Class.forName(nomClasse)
        k.constructors.forEach { c ->
            println("  <init>(${c.parameterTypes.joinToString(", ") { it.simpleName }})")
        }
        k.methods.filter { it.declaringClass == k || k.interfaces.any { i -> it.declaringClass == i } }
            .sortedBy { it.name }
            .forEach { m ->
                val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                println("  ${m.returnType.simpleName} ${m.name}($params)")
            }
    } catch (e: Throwable) {
        println("  ABSENT: ${e.javaClass.simpleName}: ${e.message?.take(90)}")
    }
}

fun main() {
    methodes("com.intellij.core.JavaCoreApplicationEnvironment")
    methodes("com.intellij.core.JavaCoreProjectEnvironment")
    methodes("com.intellij.core.CoreJavaFileManager")
    methodes("com.intellij.psi.JavaPsiFacade")
    // presence de classes utilitaires
    for (c in listOf(
        "com.intellij.psi.impl.PsiFileFactoryImpl",
        "com.intellij.psi.PsiFileFactory",
        "com.intellij.lang.java.JavaLanguage",
        "com.intellij.psi.JavaPsiFacade\$FindClassCache",
    )) {
        try {
            Class.forName(c)
            println("PRÉSENT : $c")
        } catch (e: Throwable) {
            println("ABSENT  : $c (${e.javaClass.simpleName})")
        }
    }
}

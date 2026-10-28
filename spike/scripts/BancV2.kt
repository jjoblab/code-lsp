// CodeIDE — étude moteur Java PSI : banc V2 (environnement PSI Java headless
// complet — artefacts IntelliJ 232.4652, résolution native par CoreJavaFileManager).
//
// Mesure (§6 du prompt de mission) :
//  - démarrage à froid de l'ENVIRONNEMENT (construction des 2 env + services) ;
//  - parse PSI complet d'un fichier (PsiFileFactory.createFileFromText) p50/p95 ;
//  - résolution : findClass sur un jar réel ajouté au classpath (guava) ;
//  - résolution de référence intra-fichier (resolve() d'une PsiReference) ;
//  - tas après environnement + parse.
//
// Usage : java -cp <pile V2 + build-v2> BancV2Kt <fichier.java> <jar-classpath> [iterations]
// Pile : cf. spike/README.md (artefacts 232.4652 + dépendances).

import com.intellij.core.JavaCoreApplicationEnvironment
import com.intellij.core.JavaCoreProjectEnvironment
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil
import java.io.File
import kotlin.system.exitProcess

/** Adaptateur headless : expose le JavaFileManager service comme PsiElementFinder
 * (l'amorçage 232 enregistre le service mais pas l'EP — taxe d'assemblage). */
class AdaptateurFinder(private val gestionnaire: com.intellij.psi.impl.file.impl.JavaFileManager) : PsiElementFinder() {
    override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? =
        gestionnaire.findClass(qualifiedName, scope)

    override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> =
        gestionnaire.findClasses(qualifiedName, scope)
}

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

fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage : BancV2 <fichier.java> <jar-classpath> [iterations=100]")
        exitProcess(2)
    }
    val chemin = args[0]
    val jarClasspath = File(args[1])
    val iterations = if (args.size > 2) args[2].toInt() else 100
    val text = File(chemin).readText()

    // --- 1. démarrage à froid de l'environnement headless ---
    val parent: Disposable = Disposer.newDisposable()
    val t0 = System.nanoTime()
    val appEnv = JavaCoreApplicationEnvironment(parent)
    val t1 = System.nanoTime()
    // EP non enregistrés par l'amorçage headless mais requis par la
    // résolution Java (taxe d'assemblage headless — constat d'étude D1/D9).
    // Chaque ajout = un EP manquant découvert à l'exécution (itération de spike).
    var epManuels = 0
    com.intellij.openapi.extensions.Extensions.getRootArea().let { zone ->
        com.intellij.core.CoreApplicationEnvironment.registerExtensionPoint(
            zone, "com.intellij.lang.psiAugmentProvider",
            com.intellij.psi.augment.PsiAugmentProvider::class.java,
        )
        epManuels++
        // requis par PsiResolveHelperImpl.isAccessible (232.4652) — à HEAD ce
        // mécanisme est devenu le service JavaModuleGraphHelper (constat D1)
        com.intellij.core.CoreApplicationEnvironment.registerExtensionPoint(
            zone, "com.intellij.javaModuleSystem",
            com.intellij.psi.JavaModuleSystem::class.java,
        )
        epManuels++
    }
    val projEnv = JavaCoreProjectEnvironment(parent, appEnv)
    val t2 = System.nanoTime()
    val envFroidAppMs = (t1 - t0) / 1e6
    val envFroidProjetMs = (t2 - t1) / 1e6
    val project: Project = projEnv.project
    // EP projet non enregistré par l'amorçage headless mais requis par
    // JavaPsiFacadeImpl.filteredFinders (constat d'étude : taxe d'assemblage)
    com.intellij.core.CoreApplicationEnvironment.registerExtensionPoint(
        (project as com.intellij.mock.MockComponentManager).extensionArea,
        "com.intellij.java.elementFinder",
        PsiElementFinder::class.java,
    )
    @Suppress("UNCHECKED_CAST")
    PsiElementFinder.EP.getPoint(project)
        .registerExtension(AdaptateurFinder(com.intellij.psi.impl.file.impl.JavaFileManager.getInstance(project)), parent)
    // niveau de langage (recette du JavaParsingTestCase 232)
    LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.HIGHEST

    // --- 2. classpath : un jar réel (résolution par CoreJavaFileManager, sans index) ---
    projEnv.addJarToClassPath(jarClasspath)

    // --- 3. parse PSI complet ---
    val factory = PsiFileFactory.getInstance(project)
    val parseUne: () -> PsiJavaFile = {
        val f = factory.createFileFromText("Banc.java", JavaFileType.INSTANCE, text) as PsiJavaFile
        f.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST)
        f
    }
    val premier = parseUne()
    val classes = premier.classes
    val nomPremiereClasse = classes.firstOrNull()?.name ?: "?"
    val nbMethodes = classes.firstOrNull()?.methods?.size ?: 0

    repeat(30) { parseUne() } // échauffement JIT
    val essais = (1..iterations).map {
        val debut = System.nanoTime()
        parseUne()
        (System.nanoTime() - debut) / 1000 // µs
    }

    // --- 4. résolution findClass (jar réel, CoreJavaFileManager) ---
    val facade = JavaPsiFacade.getInstance(project)
    val portee = GlobalSearchScope.allScope(project)
    val fqcn = "com.google.common.collect.ImmutableList"
    val rFroid0 = System.nanoTime()
    val classeResolue = facade.findClass(fqcn, portee)
    val resolveFroidMs = (System.nanoTime() - rFroid0) / 1e6
    val essaisResolve = (1..20).map {
        val debut = System.nanoTime()
        facade.findClass(fqcn, portee)
        (System.nanoTime() - debut) / 1000
    }

    // --- 5. résolution de référence intra-fichier ---
    val texteRef = """
        import com.google.common.collect.ImmutableList;
        public class Essai {
            ImmutableList<String> liste = ImmutableList.of();
        }
    """.trimIndent()
    val fichierRef = factory.createFileFromText("Essai.java", JavaFileType.INSTANCE, texteRef) as PsiJavaFile
    fichierRef.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST)
    val tpsRefs = mutableListOf<Long>()
    repeat(20) {
        val f = factory.createFileFromText("Essai.java", JavaFileType.INSTANCE, texteRef) as PsiJavaFile
        f.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST)
        val debut = System.nanoTime()
        val champ = f.classes.firstOrNull()?.fields?.firstOrNull()
        val typeResolu = (champ?.type as? PsiClassType)?.resolve()
        tpsRefs += (System.nanoTime() - debut) / 1000
        if (typeResolu == null) System.err.println("ATTENTION : type non résolu")
    }

    // --- 6. mémoire ---
    repeat(5) { parseUne() }
    val tas = tasUtilise()

    println("banc V2 — pile headless IntelliJ 232.4652 (artefacts publiés)")
    println("taxe d'assemblage    : $epManuels EP application + 1 EP projet enregistrés à la main" +
        " (psiAugmentProvider, javaModuleSystem, java.elementFinder)")
    println("fichier             : $chemin (${text.count { it == '\n' } + 1} lignes)")
    println("jar classpath       : ${jarClasspath.name} (${jarClasspath.length() / 1024 / 1024} Mo)")
    println("env app froid       : $envFroidAppMs ms (JavaCoreApplicationEnvironment)")
    println("env projet froid    : $envFroidProjetMs ms (JavaCoreProjectEnvironment)")
    println("parse PSI p50       : ${percentile(essais, 50.0)} ms")
    println("parse PSI p95       : ${percentile(essais, 95.0)} ms")
    println("parse PSI min/max   : ${essais.min() / 1000.0} / ${essais.max() / 1000.0} ms")
    println("première classe     : $nomPremiereClasse ($nbMethodes méthodes)")
    println("findClass froid     : $resolveFroidMs ms ($fqcn → ${classeResolue != null})")
    println("findClass p50/p95   : ${percentile(essaisResolve, 50.0)} / ${percentile(essaisResolve, 95.0)} ms")
    println("resolve() ref p50/p95: ${percentile(tpsRefs, 50.0)} / ${percentile(tpsRefs, 95.0)} ms")
    println("tas (env + parse)   : ${tas / 1024 / 1024} Mo")

    Disposer.dispose(parent)
}

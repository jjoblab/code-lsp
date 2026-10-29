package jo.codeide.lsp.java.navigation

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Fixture du JDK de test (étage N4 — fiches 005/009) : le pattern de
 * détection du cahier (`JO_TEST_JDK_HOME`, sinon les candidats outils,
 * sinon `java.home` — préférence à un home portant `lib/src.zip`, à
 * défaut `lib/modules`).
 */
internal object JdkDeTest {

    /** Les candidats du cahier, dans l'ordre. */
    private fun candidats(): List<Path> {
        val liste = ArrayList<Path>()
        System.getenv("JO_TEST_JDK_HOME")?.let { liste.add(Paths.get(it)) }
        liste.add(Paths.get("/home/z/tools/jdk-17.0.20.1+1"))
        liste.add(Paths.get("/home/z/my-project/tools/jdk-17.0.20.1+1"))
        liste.add(Paths.get("/usr/lib/jvm/java-17-openjdk-amd64"))
        liste.add(Paths.get("/usr/lib/jvm/java-21-openjdk-amd64"))
        runCatching { liste.add(Paths.get(System.getProperty("java.home"))) }
        return liste
    }

    /**
     * Le home JDK de test (préférence `lib/src.zip`, sinon `lib/modules`),
     * ou null (la classe appelante IGNORE ses tests par `assumeTrue`).
     */
    fun home(): Path? {
        val avecSrcZip = candidats().firstOrNull { estHomeAvecSrcZip(it) }
        if (avecSrcZip != null) return avecSrcZip
        return candidats().firstOrNull { estHomeAvecModules(it) }
    }

    /** Vrai ssi le home porte `lib/src.zip` (source attachée). */
    fun aSrcZip(home: Path): Boolean = Files.isRegularFile(home.resolve("lib").resolve("src.zip"))

    private fun estHomeAvecSrcZip(home: Path): Boolean = runCatching { aSrcZip(home) }.getOrDefault(false)

    private fun estHomeAvecModules(home: Path): Boolean =
        runCatching { Files.isRegularFile(home.resolve("lib").resolve("modules")) }.getOrDefault(false)
}

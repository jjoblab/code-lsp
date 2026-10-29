package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.FabriqueClasseBinaire
import jo.codeide.lsp.java.classpath.StubsIntrinseques
import jo.codeide.lsp.java.diagnostics.Severite
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 042 — StringConcatFactoryReproTest : repro du faux positif
 * « The type java.lang.invoke.StringConcatFactory cannot be resolved » —
 * un `.class` binaire qui DÉCLARE `extends StringConcatFactory`, sans jrt.
 *
 * Correctif verrouillé : stubs EMBARQUÉS des intrinsèques du compilateur,
 * repli APRÈS sources/jars/jrt, uniquement pour ces FQCN. La fixture
 * `batisse.Biblio` est fabriquée par [FabriqueClasseBinaire] (octets
 * Java 8 équivalents à l'ASM du cahier — constructeur public sans argument
 * pour éviter le bruit « Implicit super constructor »).
 */
class Fiche042StubsIntrinsequesTest {

    /** Fabrique le jar biblio.jar : batisse.Biblio étend StringConcatFactory. */
    private fun fabriquerBiblioJar(): Path {
        val dossier = Files.createTempDirectory("fiche042")
        val jar = dossier.resolve("biblio.jar")
        val octets = FabriqueClasseBinaire.octets(
            "batisse.Biblio",
            superFqcn = "java.lang.invoke.StringConcatFactory",
            constructeurPublic = true,
        )
        ZipOutputStream(Files.newOutputStream(jar)).use { sortie ->
            sortie.putNextEntry(ZipEntry("batisse/Biblio.class"))
            sortie.write(octets)
            sortie.closeEntry()
        }
        return jar
    }

    /** Home factice SANS lib/modules (jrt neutralisé). */
    private fun fauxJdk(): Path = Files.createTempDirectory("fiche042-fauxjdk")

    private val sourceA = "package p;\npublic class A extends batisse.Biblio { }\n"

    @Test
    fun sansJrtLeFauxPositifNeReapparaitPas() {
        val moteur = MoteurJava(jars = listOf(fabriquerBiblioJar()), jdkHome = fauxJdk())
        val diagnostics = moteur.diagnose("/p/A.java", sourceA)
        assertTrue(
            diagnostics.none { it.message.contains("StringConcatFactory") },
            "le faux positif ne doit JAMAIS réapparaître sans jrt : ${diagnostics.map { it.message }}",
        )
    }

    @Test
    fun contratDesStubsIntrinseques() {
        // FQCN sondés : types de plateforme vs intrinsèques.
        assertFalse(StubsIntrinseques.estIntrinseque("java.lang.String"), "java.lang.String n'est PAS un intrinsèque")
        assertFalse(StubsIntrinseques.estIntrinseque("java.util.List"), "java.util.List n'est PAS un intrinsèque")
        for (fqcn in listOf(
            "java.lang.invoke.StringConcatFactory",
            "java.lang.runtime.ObjectMethods",
            "java.lang.runtime.SwitchBootstraps",
        )) {
            assertTrue(StubsIntrinseques.estIntrinseque(fqcn), "$fqcn est un intrinsèque")
        }
        val octets = StubsIntrinseques.octets("java.lang.invoke.StringConcatFactory")
        assertNotNull(octets, "octets du stub non nuls")
        // Magic exact CA FE BA BE (octets 0-3).
        assertContentEquals(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()), octets.copyOfRange(0, 4), "magic CA FE BA BE")
        // Versions mineure et majeure : poids fort à 0, majeure 52 (Java 8).
        assertEquals(0, octets[4].toInt() and 0xFF, "version mineure (haut) = 0")
        assertEquals(0, octets[5].toInt() and 0xFF, "version mineure (bas) = 0")
        assertEquals(0, octets[6].toInt() and 0xFF, "version majeure (haut) = 0")
        assertEquals(52, octets[7].toInt() and 0xFF, "version majeure = 52 (Java 8)")
        // Jamais de stub d'un type de plateforme.
        assertNull(StubsIntrinseques.octets("java.lang.String"), "octets(java.lang.String) rend null")
    }

    @Test
    fun temoinAvecJrtLeDiagnosticDisparait() {
        // MÊME classpath mais jdkHome = JDK réel avec image jrt complète :
        // la classe résout via le jrt, la hiérarchie se referme naturellement.
        val jdkReel = Path.of(System.getProperty("java.home"))
        assumeTrue(Files.isRegularFile(jdkReel.resolve("lib/modules")), "JDK runtime sans image jrt — ignoré")
        val moteur = MoteurJava(jars = listOf(fabriquerBiblioJar()), jdkHome = jdkReel)
        val diagnostics = moteur.diagnose("/p/A.java", sourceA)
        assertTrue(
            diagnostics.none { it.message.contains("StringConcatFactory") },
            "témoin jrt sain : aucun diagnostic StringConcatFactory : ${diagnostics.map { it.message }}",
        )
    }

    @Test
    fun stubEmbarqueLeFauxPositifDisparait() {
        // Exactement le contexte du repro : biblio.jar + jdkHome factice sans jrt.
        val moteur = MoteurJava(jars = listOf(fabriquerBiblioJar()), jdkHome = fauxJdk())
        val diagnostics = moteur.diagnose("/p/A.java", sourceA)
        assertTrue(
            diagnostics.none { it.severite == Severite.ERREUR && it.message.contains("StringConcatFactory") },
            "le stub embarqué referme la hiérarchie binaire",
        )
    }

    @Test
    fun api36NonPlusSansJrt() {
        // Plateforme récente aussi : sans jrt, même sans android.jar réel, le
        // mécanisme du stub est STRUCTUREL (la classe n'existe dans AUCUNE
        // plateforme Android) — le moteur avec classpath biblio.jar seul doit
        // rester silencieux sur l'intrinsèque.
        val moteur = MoteurJava(jars = listOf(fabriquerBiblioJar()), jdkHome = fauxJdk())
        val diagnostics = moteur.diagnose("/p/A.java", sourceA)
        assertTrue(
            diagnostics.none { it.message.contains("StringConcatFactory") },
            "le correctif ferme le binding même sans plateforme : ${diagnostics.map { it.message }}",
        )
    }
}

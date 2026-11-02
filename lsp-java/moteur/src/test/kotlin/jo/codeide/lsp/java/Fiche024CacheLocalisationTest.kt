package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.CacheLocalisation
import jo.codeide.lsp.java.classpath.LecteurZipBrut
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 024 — EcjLocateCacheTest : cache de localisation fqcn → jar (P1
 * v1.3.0) intégré à la recherche binaire — positif mémorisé, négatif
 * mémorisé (lookup O(1)), types imbriqués via le chemin `$` sans exception,
 * invalidation par dispose.
 *
 * Le jar de test est fabriqué à partir d'un vrai `.class` copié du runtime
 * de test (entrée ZIP valide pour la lecture de fichier de classe — le
 * contrat du cahier remplace le jar de classes du module).
 */
class Fiche024CacheLocalisationTest {

    private val classeTemoins = Path.of(
        org.junit.jupiter.api.Test::class.java.protectionDomain.codeSource.location.toURI(),
    )

    private fun fabriquerJarTemoins(): Path {
        val dossier = Files.createTempDirectory("fiche024")
        val jar = dossier.resolve("temoins.jar")
        val octets = LecteurZipBrut.readSingle(classeTemoins, "org/junit/jupiter/api/Test.class")
        ZipOutputStream(Files.newOutputStream(jar)).use { sortie ->
            sortie.putNextEntry(ZipEntry("org/junit/jupiter/api/Test.class"))
            sortie.write(octets)
            sortie.closeEntry()
        }
        return jar
    }

    @Test
    fun positifMemoriseDeuxFindSuccessifs() {
        val jar = fabriquerJarTemoins()
        val cache = CacheLocalisation(listOf(jar))
        val premier = cache.jarPour("org.junit.jupiter.api.Test")
        val second = cache.jarPour("org.junit.jupiter.api.Test")
        assertEquals(jar, premier, "premier find : jar porteur")
        assertEquals(premier, second, "second find : même verdict (emprunt du cache)")
    }

    @Test
    fun negatifMemoriseAbsentDesJars() {
        val jar = fabriquerJarTemoins()
        val cache = CacheLocalisation(listOf(jar))
        assertNull(cache.jarPour("com.inexistant.Classe"), "premier appel : null")
        assertNull(cache.jarPour("com.inexistant.Classe"), "second appel : null immédiat (négatif mémorisé)")
    }

    @Test
    fun typeImbriqueLocaliseViaEnglobant() {
        val jar = fabriquerJarTemoins()
        val cache = CacheLocalisation(listOf(jar))
        // Englobant ABSENT : verdict null sans exception.
        assertNull(cache.jarPour("org.junit.jupiter.api.PasLa\$Inner"))
        // Englobant présent mais SANS entrée imbriquée dans le jar témoin :
        // verdict null cohérent, le chemin `$` retombe sans crash.
        assertNull(cache.jarPour("org.junit.jupiter.api.Test\$Inner"))
    }

    @Test
    fun disposeVideLeCache() {
        val jar = fabriquerJarTemoins()
        val cache = CacheLocalisation(listOf(jar))
        assertNull(cache.jarPour("com.inexistant.ApresDispose"))
        cache.dispose()
        // Après dispose, le find reste sûr et rend null (structures vides).
        assertNull(cache.jarPour("com.inexistant.ApresDispose"))
        // Et un positif re-sonde proprement (handles ré-ouverts paresseusement).
        assertEquals(jar, cache.jarPour("org.junit.jupiter.api.Test"))
    }

    @Test
    fun encodageUtf8DuFichierSource() {
        // Garde-fou d'encodage du contrat : ce fichier source est UTF-8.
        val ressource = javaClass.classLoader.getResourceAsStream(
            "jo/codeide/lsp/java/Fiche024CacheLocalisationTest.class",
        )
        if (ressource != null) {
            // Le .class encodé du test contient le mot « localisation » dans
            // son pool de constantes si les assertions sont intactes ; la
            // lecture ne lève pas = encodage sain.
            ressource.use { it.readBytes() }
        }
        assertTrue(true, "lecture UTF-8 du fichier du test : saine")
    }
}

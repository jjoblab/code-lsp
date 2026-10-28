package jo.codeide.lsp.ecjart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * Tests de chargement et de validation de la liste de shims versionnée
 * (ADR 0018) — le fichier de configuration qui pilote le patcheur.
 */
class ListeShimsTest {

    @Test
    fun `compiler accepte directives valides et saute commentaires et lignes vides`() {
        val liste = ListeShims.compiler(
            listOf(
                "# en-tête de commentaire",
                "",
                "   # commentaire indenté",
                "remap-classe java/lang/Runtime\$Version jo/codeide/lsp/ecjart/compat/VersionJava",
                "rediriger-methode java/io/InputStream readAllBytes ()[B jo/x/LectureFlux lireTout (Ljava/io/InputStream;)[B",
            ),
            "0.0-test",
        )
        assertEquals(2, liste.directives.size)
        assertEquals(1, liste.remaps().size)
        assertEquals(1, liste.redirections().size)
        assertEquals("0.0-test", liste.versionEcj)
    }

    @Test
    fun `compiler rejette une directive inconnue`() {
        val erreur = assertThrows(IllegalArgumentException::class.java) {
            ListeShims.compiler(listOf("renommer-classe a b"), "0.0-test")
        }
        assertTrue(erreur.message!!.contains("directive inconnue"))
    }

    @Test
    fun `compiler rejette une arité incorrecte`() {
        assertThrows(IllegalArgumentException::class.java) {
            ListeShims.compiler(listOf("remap-classe java/lang/X"), "0.0-test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ListeShims.compiler(
                listOf("rediriger-methode a b c d e"),
                "0.0-test",
            )
        }
    }

    @Test
    fun `compiler rejette les directives dupliquées`() {
        assertThrows(IllegalArgumentException::class.java) {
            ListeShims.compiler(
                listOf(
                    "remap-classe a/b c/d",
                    "remap-classe a/b c/d",
                ),
                "0.0-test",
            )
        }
    }

    @Test
    fun `compiler rejette une liste vide`() {
        assertThrows(IllegalArgumentException::class.java) {
            ListeShims.compiler(listOf("# rien", ""), "0.0-test")
        }
    }

    @Test
    fun `directive remap rejette les noms internes mal formés`() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectiveRemap("java/lang/", "a/b")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DirectiveRemap("[Ljava/lang/String;", "a/b")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DirectiveRemap("a/b", "")
        }
    }

    /**
     * La VRAIE liste versionnée ecj 3.40.0 (ressource du module) : 3
     * directives actives — le contrat exact constaté au scan du jar
     * (Runtime$Version 1x, readAllBytes 3x, readNBytes 1x).
     */
    @Test
    fun `la liste versionnee 3_40_0 charge avec exactement 3 directives`() {
        val chemin = localiserRessourceListe()
        val liste = ListeShims.charger(chemin, "3.40.0")
        assertEquals(3, liste.directives.size)

        val remap = liste.remaps().single()
        assertEquals("java/lang/Runtime\$Version", remap.classeAmont)
        assertEquals("jo/codeide/lsp/ecjart/compat/VersionJava", remap.classeShim)

        val redirections = liste.redirections()
        assertEquals(2, redirections.size)
        assertEquals(
            DirectiveRedirection(
                "java/io/InputStream", "readAllBytes", "()[B",
                "jo/codeide/lsp/ecjart/compat/LectureFlux", "lireTout", "(Ljava/io/InputStream;)[B",
            ),
            redirections[0],
        )
        assertEquals(
            DirectiveRedirection(
                "java/io/InputStream", "readNBytes", "(I)[B",
                "jo/codeide/lsp/ecjart/compat/LectureFlux", "lireN", "(Ljava/io/InputStream;I)[B",
            ),
            redirections[1],
        )
    }

    /** Localise le fichier de liste versionné sur le classpath de test. */
    internal companion object {
        fun localiserRessourceListe(): Path {
            val url = ListeShimsTest::class.java.classLoader
                .getResource("jo/codeide/lsp/ecjart/liste-shims-3.40.0.txt")
            checkNotNull(url) { "ressource liste-shims-3.40.0.txt introuvable sur le classpath" }
            return Path.of(url.toURI())
        }
    }
}

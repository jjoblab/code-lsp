package jo.codeide.lsp.ecjart.compat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Tests du shim [LectureFlux] — sémantique de la javadoc JDK d'`InputStream`
 * (Java 9+) pour `readAllBytes()` et `readNBytes(int)`, redirigées par le
 * patcheur car API 33+ sur Android (minSdk 26 visé).
 */
class LectureFluxTest {

    /** Flux de test servant ses données PAR PETITS BLOCS (simule les read partiels). */
    private class FluxParBlocs(
        private val donnees: ByteArray,
        private val maxParLecture: Int,
    ) : InputStream() {
        var appelsDeLecture = 0

        override fun read(): Int {
            appelsDeLecture++
            return if (position >= donnees.size) -1 else donnees[position++].toInt() and 0xFF
        }

        override fun read(tampon: ByteArray, decalage: Int, longueur: Int): Int {
            appelsDeLecture++
            if (position >= donnees.size) return -1
            val n = minOf(longueur, maxParLecture, donnees.size - position)
            System.arraycopy(donnees, position, tampon, decalage, n)
            position += n
            return n
        }

        private var position = 0
    }

    /** Flux qui échoue au premier read (propagation des IOException). */
    private class FluxFaux : InputStream() {
        override fun read(): Int = throw IOException("échouée exprès")
    }

    // ------------------------------------------------------------------
    // lireTout — équivalent de readAllBytes()
    // ------------------------------------------------------------------

    @Test
    fun `lireTout sur un flux vide retourne un tableau vide`() {
        assertEquals(0, LectureFlux.lireTout(ByteArrayInputStream(ByteArray(0))).size)
    }

    @Test
    fun `lireTout assemble les lectures partielles`() {
        val donnees = ByteArray(10_000) { (it % 251).toByte() }
        val flux = FluxParBlocs(donnees, maxParLecture = 7) // 10 000 / 7 lectures
        assertArrayEquals(donnees, LectureFlux.lireTout(flux))
        assertTrue("le flux doit avoir été lu par blocs", flux.appelsDeLecture > 1000)
    }

    @Test
    fun `lireTout propage les IOException`() {
        assertThrows(IOException::class.java) { LectureFlux.lireTout(FluxFaux()) }
    }

    @Test
    fun `lireTout parité avec readAllBytes de la JVM`() {
        val donnees = ByteArray(50_000) { (it * 31 % 199).toByte() }
        val jdk = ByteArrayInputStream(donnees).readAllBytes()
        val shim = LectureFlux.lireTout(ByteArrayInputStream(donnees))
        assertArrayEquals(jdk, shim)
    }

    // ------------------------------------------------------------------
    // lireN — équivalent de readNBytes(int)
    // ------------------------------------------------------------------

    @Test
    fun `lireN avec nombre négatif lève IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            LectureFlux.lireN(ByteArrayInputStream(byteArrayOf(1)), -1)
        }
    }

    @Test
    fun `lireN avec zéro retourne un tableau vide`() {
        assertEquals(0, LectureFlux.lireN(ByteArrayInputStream(byteArrayOf(1, 2)), 0).size)
    }

    @Test
    fun `lireN lit exactement le nombre demandé quand il est disponible`() {
        val donnees = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), LectureFlux.lireN(ByteArrayInputStream(donnees), 4))
    }

    @Test
    fun `lireN s'arrête à la fin de flux avant le nombre demandé`() {
        val donnees = byteArrayOf(7, 8, 9)
        assertArrayEquals(donnees, LectureFlux.lireN(ByteArrayInputStream(donnees), 100))
    }

    @Test
    fun `lireN bloque jusqu'à atteindre le nombre malgré les lectures partielles`() {
        val donnees = ByteArray(1000) { (it % 7).toByte() }
        val flux = FluxParBlocs(donnees, maxParLecture = 3)
        assertArrayEquals(donnees, LectureFlux.lireN(flux, 1000))
        // 1000 octets par blocs de 3 : 333 lectures + 1 retour partielle + 1 fin.
        assertTrue(flux.appelsDeLecture > 300)
    }

    @Test
    fun `lireN sur flux épuisé retourne un tableau vide`() {
        assertEquals(0, LectureFlux.lireN(ByteArrayInputStream(ByteArray(0)), 5).size)
    }

    @Test
    fun `lireN parité avec readNBytes de la JVM`() {
        val donnees = ByteArray(999) { (it * 13 % 101).toByte() }
        for (n in listOf(0, 1, 42, 998, 999, 5000)) {
            val jdk = ByteArrayInputStream(donnees).readNBytes(n)
            val shim = LectureFlux.lireN(ByteArrayInputStream(donnees), n)
            assertArrayEquals("n=$n", jdk, shim)
        }
    }
}

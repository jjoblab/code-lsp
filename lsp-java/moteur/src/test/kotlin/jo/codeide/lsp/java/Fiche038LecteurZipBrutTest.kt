package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.LecteurZipBrut
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fiche 038 — RawZipReaderTest : le lecteur ZIP minimaliste, chaque
 * propriété vérifiée PAR RAPPORT à `java.util.zip.ZipFile` (vérité terrain
 * JDK). Corpus : DEFLATED, imbriqué `$`, STORED (CRC/taille), UTF-8
 * accentué, grande entrée multi-tampons.
 */
class Fiche038LecteurZipBrutTest {

    /** Fabrique le zip de synthèse du corpus (5 entrées couvrant les cas réels). */
    private fun fabriquerZipSynthetique(): Path {
        val dossier = Files.createTempDirectory("fiche038")
        val zip = dossier.resolve("synthetique.zip")
        val grandeEntree = ByteArray(100_000) { i -> 'x'.code.toByte() } // motif « x<i mod 10> »
        // Contenu conforme au motif du contrat : « x0x1x2…x8x9 » répété.
        val motif = (0..9).joinToString("") { "x$it" }
        val grande = motif.repeat(5_000).toByteArray(Charsets.UTF_8)
        ZipOutputStream(Files.newOutputStream(zip)).use { sortie ->
            sortie.setLevel(Deflater.BEST_COMPRESSION)
            sortie.putNextEntry(ZipEntry("com/example/Alpha.class"))
            sortie.write("classeAlpha".toByteArray())
            sortie.closeEntry()
            sortie.putNextEntry(ZipEntry("com/example/R\$string.class"))
            sortie.write("classeRstring".toByteArray())
            sortie.closeEntry()
            val manifeste = ZipEntry("META-INF/MANIFEST.MF")
            manifeste.method = ZipEntry.STORED
            val contenuManifeste = "Manifest-Version: 1.0\n".toByteArray()
            manifeste.size = contenuManifeste.size.toLong()
            manifeste.crc = CRC32().apply { update(contenuManifeste) }.value
            sortie.putNextEntry(manifeste)
            sortie.write(contenuManifeste)
            sortie.closeEntry()
            sortie.putNextEntry(ZipEntry("déjà/ClassÉ.class"))
            sortie.write("utf8".toByteArray(Charsets.UTF_8))
            sortie.closeEntry()
            sortie.putNextEntry(ZipEntry("big/Data.class"))
            sortie.write(grande)
            sortie.closeEntry()
        }
        return zip
    }

    @Test
    fun scanNamesMatchesZipFileGroundTruth() {
        val zip = fabriquerZipSynthetique()
        val attendu = ZipFile(zip.toFile()).use { zf ->
            zf.entries().asSequence().map { it.name }.toSet()
        }
        assertEquals(attendu, LecteurZipBrut.scanNames(zip), "inventaire IDENTIQUE au JDK")
    }

    @Test
    fun readSingleDeflatedEqualsZipFile() {
        val zip = fabriquerZipSynthetique()
        val attendu = ZipFile(zip.toFile()).use { zf ->
            zf.getInputStream(zf.getEntry("com/example/Alpha.class")).readBytes()
        }
        assertContentEquals(attendu, LecteurZipBrut.readSingle(zip, "com/example/Alpha.class"), "octets identiques (DEFLATED)")
    }

    @Test
    fun readSingleStoredEqualsZipFile() {
        val zip = fabriquerZipSynthetique()
        val attendu = ZipFile(zip.toFile()).use { zf ->
            zf.getInputStream(zf.getEntry("META-INF/MANIFEST.MF")).readBytes()
        }
        assertContentEquals(attendu, LecteurZipBrut.readSingle(zip, "META-INF/MANIFEST.MF"), "octets identiques (STORED)")
    }

    @Test
    fun readLargeMultiBufferEntry() {
        val zip = fabriquerZipSynthetique()
        val donnees = LecteurZipBrut.readSingle(zip, "big/Data.class")
        assertEquals(100_000, donnees.size, "longueur exacte de l'entrée multi-tampons")
        val texte = donnees.toString(Charsets.UTF_8)
        assertTrue(texte.startsWith("x0x1x2"), "préfixe conforme au motif")
        assertTrue(texte.endsWith("x8x9"), "suffixe conforme au motif (inflation complète)")
    }

    @Test
    fun utf8EntriesReadable() {
        val zip = fabriquerZipSynthetique()
        val noms = LecteurZipBrut.scanNames(zip)
        assertTrue("déjà/ClassÉ.class" in noms, "le nom accentué EXACT est inventorié")
        assertEquals(
            "utf8",
            LecteurZipBrut.readSingle(zip, "déjà/ClassÉ.class").toString(Charsets.UTF_8),
            "le contenu décodé est « utf8 »",
        )
    }

    @Test
    fun persistentHandleLifecycle() {
        val zip = fabriquerZipSynthetique()
        LecteurZipBrut.open(zip).use { handle ->
            assertTrue(handle.contains("com/example/Alpha.class"), "contains : vrai pour une entrée existante")
            assertFalse(handle.contains("absent/Entrée.class"), "contains : faux pour une absente")
            val premiere = handle.lire("com/example/Alpha.class")
            val seconde = handle.lire("com/example/Alpha.class")
            assertContentEquals(premiere, seconde, "deux lectures successives par le MÊME handle")
            assertContentEquals(
                LecteurZipBrut.readSingle(zip, "com/example/Alpha.class"),
                premiere,
                "lecture par handle identique à la lecture en un coup",
            )
        }
    }

    @Test
    fun missingOrTruncatedFilesAreRejectedCleanly() {
        val zip = fabriquerZipSynthetique()
        // Chemin inexistant.
        assertFailsWith<IOException> { LecteurZipBrut.open(Path.of("/inexistant/fiche038.zip")) }
        // Zip valide amputé de sa seconde moitié (EOCD supprimé).
        val octets = Files.readAllBytes(zip)
        val tronque = Files.createTempFile("fiche038-tronque", ".zip")
        Files.write(tronque, octets.copyOfRange(0, octets.size / 2))
        assertFailsWith<IOException> { LecteurZipBrut.open(tronque) }
    }
}

package jo.codeide.lsp.ecjart

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.ByteArrayInputStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Test end-to-end du [PatcheurJar] sur une classe CIBLE SYNTHÉTIQUE —
 * générée par ASM à l'image des usages constatés dans ecj 3.40.0 :
 *  - `Runtime.Version.parse(a).compareTo(Runtime.Version.parse(b))` (Parser) ;
 *  - `InputStream.readAllBytes()` (ClasspathJsr199, ArchiveFileObject, Util) ;
 *  - `InputStream.readNBytes(int)` (Util).
 *
 * La preuve est COMPORTEMENTALE : la classe patchée est chargée par un
 * chargeur dédié et EXÉCUTÉE — les invocations doivent passer par les shims
 * ([jo.codeide.lsp.ecjart.compat.VersionJava], [jo.codeide.lsp.ecjart.compat.LectureFlux])
 * et produire les résultats attendus, preuve que le remap de type et la
 * redirection d'invocations produisent du bytecode résolvable et juste.
 */
class PatcheurJarTest {

    private val nomCible = "jo/codeide/lsp/ecjart/essai/CibleEssai"

    // ------------------------------------------------------------------
    // Génération de la classe cible (usages sensibles, image d'ecj)
    // ------------------------------------------------------------------

    /**
     * Génère les octets de la classe cible : trois méthodes statiques
     * utilisant exactement les motifs constatés dans ecj 3.40.0.
     */
    private fun genererCible(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
            nomCible,
            null,
            "java/lang/Object",
            null,
        )
        cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0) // recalculées (COMPUTE_MAXS)
            visitEnd()
        }
        // int comparer(String a, String b) — image du diagnoseParse du Parser :
        // Runtime.Version.parse(a).compareTo(Runtime.Version.parse(b))
        cw.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "comparer",
            "(Ljava/lang/String;Ljava/lang/String;)I",
            null,
            null,
        ).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(
                Opcodes.INVOKESTATIC, "java/lang/Runtime\$Version", "parse",
                "(Ljava/lang/String;)Ljava/lang/Runtime\$Version;", false,
            )
            visitVarInsn(Opcodes.ALOAD, 1)
            visitMethodInsn(
                Opcodes.INVOKESTATIC, "java/lang/Runtime\$Version", "parse",
                "(Ljava/lang/String;)Ljava/lang/Runtime\$Version;", false,
            )
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL, "java/lang/Runtime\$Version", "compareTo",
                "(Ljava/lang/Runtime\$Version;)I", false,
            )
            visitInsn(Opcodes.IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        // byte[] tout(InputStream) — InputStream.readAllBytes()
        cw.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "tout",
            "(Ljava/io/InputStream;)[B",
            null,
            null,
        ).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "readAllBytes", "()[B", false)
            visitInsn(Opcodes.ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        // byte[] n(InputStream, int) — InputStream.readNBytes(int)
        cw.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "n",
            "(Ljava/io/InputStream;I)[B",
            null,
            null,
        ).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitVarInsn(Opcodes.ILOAD, 1)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "readNBytes", "(I)[B", false)
            visitInsn(Opcodes.ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        cw.visitEnd()
        return cw.toByteArray()
    }

    // ------------------------------------------------------------------
    // Le test complet : patcher puis EXÉCUTER
    // ------------------------------------------------------------------

    @Test
    fun `patcher une classe cible puis l'exécuter via les shims`() {
        val dossier = Files.createTempDirectory("ecj-art-synthese")

        // 1. Jar entrant : uniquement la classe cible.
        val jarEntree = dossier.resolve("cible.jar")
        ecrireJar(jarEntree, mapOf("$nomCible.class" to genererCible()))

        // 2. Le patcheur avec la vraie liste versionnée et les vrais shims.
        val racineShims = localiserRacineClassesShims()
        val liste = ListeShims.charger(ListeShimsTest.localiserRessourceListe(), "3.40.0")
        val jarSortie = dossier.resolve("cible-patche.jar")
        val rapport = PatcheurJar(jarEntree, racineShims, jarSortie, liste).patcher()

        // 3. Rapport : une classe patchée, un remap, deux redirections
        //    (un site readAllBytes + un site readNBytes — le rapport compte
        //    les SITES d'appel redirigés, pas les entrées de pool).
        assertEquals(listOf("$nomCible.class"), rapport.classesPatchees.map { it.chemin })
        assertTrue(rapport.classesPatchees.single().remappee)
        assertEquals(2, rapport.classesPatchees.single().redirections)
        assertTrue("shims embarqués", rapport.shimsEmbarques.any { it.endsWith("VersionJava") })
        assertTrue(rapport.shimsEmbarques.any { it.endsWith("LectureFlux") })

        // 4. Octets : plus aucune référence à Runtime$Version, des références aux shims.
        val octetsPatches = lireEntree(jarSortie, "$nomCible.class")
        val texte = String(octetsPatches, Charsets.ISO_8859_1)
        assertFalse("Runtime\$Version doit avoir disparu", texte.contains("Runtime\$Version"))
        assertTrue(texte.contains("VersionJava"))
        assertTrue(texte.contains("LectureFlux"))

        // 5. EXÉCUTION de la classe patchée (chargeur dédié, parent = classpath de test).
        URLClassLoader(arrayOf(jarSortie.toUri().toURL()), javaClass.classLoader).use { chargeur ->
            val classe = chargeur.loadClass("jo.codeide.lsp.ecjart.essai.CibleEssai")

            // comparer — remap de Runtime$Version vers VersionJava
            fun comparer(a: String, b: String): Int =
                classe.getMethod("comparer", String::class.java, String::class.java)
                    .invoke(null, a, b) as Int
            assertTrue("'21' > '9'", comparer("21", "9") > 0)
            assertTrue("'9-ea' < '9'", comparer("9-ea", "9") < 0)
            assertTrue("'1.8' < '9'", comparer("1.8", "9") < 0)
            assertEquals("'21' == '21'", 0, comparer("21", "21"))
            assertTrue("'23' > '21'", comparer("23", "21") > 0)

            // tout — redirection de InputStream.readAllBytes vers LectureFlux.lireTout
            val donnees = ByteArray(3000) { (it % 211).toByte() }
            val tout = classe.getMethod("tout", java.io.InputStream::class.java)
                .invoke(null, ByteArrayInputStream(donnees)) as ByteArray
            assertArrayEquals(donnees, tout)

            // n — redirection de InputStream.readNBytes vers LectureFlux.lireN
            val n = classe.getMethod("n", java.io.InputStream::class.java, Int::class.javaPrimitiveType)
                .invoke(null, ByteArrayInputStream(donnees), 17) as ByteArray
            assertArrayEquals(donnees.copyOf(17), n)
        }

        // 6. Nettoyage du répertoire temporaire.
        Files.walk(dossier).sorted(Comparator.reverseOrder()).forEach(Files::delete)
    }

    @Test
    fun `le jar entrant refuse un motif non couvert par la liste`() {
        val dossier = Files.createTempDirectory("ecg-art-refus")
        // Classe cible + référence supplémentaire NON couverte : java/awt/Toolkit.
        val octets = genererCibleAvecAwt()
        val jarEntree = dossier.resolve("cible-awt.jar")
        ecrireJar(jarEntree, mapOf("$nomCible.class" to octets))
        val liste = ListeShims.charger(ListeShimsTest.localiserRessourceListe(), "3.40.0")

        val erreur = try {
            PatcheurJar(jarEntree, localiserRacineClassesShims(), dossier.resolve("x.jar"), liste).patcher()
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertNotNull("le patchage doit être refusé", erreur)
        assertTrue(erreur!!.message!!.contains("non couverts"))
        assertTrue(erreur.message!!.contains("java/awt"))

        Files.walk(dossier).sorted(Comparator.reverseOrder()).forEach(Files::delete)
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    /** Variante de la cible référençant AUSSI java/awt/Toolkit (motif non couvert). */
    private fun genererCibleAvecAwt(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "$nomCible\$_Awt", null, "java/lang/Object", null)
        cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "awt", "()Ljava/awt/Toolkit;", null, null).apply {
            visitCode()
            visitMethodInsn(Opcodes.INVOKESTATIC, "java/awt/Toolkit", "getDefaultToolkit", "()Ljava/awt/Toolkit;", false)
            visitInsn(Opcodes.ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** Écrit un jar minimal (entrées fournies). */
    private fun ecrireJar(fichier: Path, entrees: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(fichier)).use { sortie ->
            for ((nom, octets) in entrees) {
                sortie.putNextEntry(ZipEntry(nom))
                sortie.write(octets)
                sortie.closeEntry()
            }
        }
    }

    /** Lit une entrée d'un jar. */
    private fun lireEntree(jar: Path, nom: String): ByteArray =
        ZipFile(jar.toFile()).use { z -> z.getInputStream(z.getEntry(nom)).readBytes() }

    /** Racine des classes compilées du module (racine du paquet compat). */
    internal companion object {
        fun localiserRacineClassesShims(): Path =
            Path.of(
                jo.codeide.lsp.ecjart.compat.VersionJava::class.java
                    .protectionDomain.codeSource.location.toURI(),
            )
    }
}

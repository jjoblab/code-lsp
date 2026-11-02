package jo.codeide.lsp.ecjart

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.PrintWriter
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.writeText

/**
 * Test d'intégration du [PatcheurJar] sur le VRAI jar ecj 3.40.0 (résolu du
 * classpath de test — même artefact que la configuration Gradle `ecjOriginal`).
 *
 * C'est le garant factuel du contrat ADR 0018 : les motifs sensibles constatés
 * au scan du jar ENTRANT correspondent exactement à la liste versionnée, le
 * jar SORTANT ne contient plus AUCUN motif bloquant, seules les classes
 * ciblées sont réécrites (les autres entrées sont octet pour octet
 * identiques), et le jar patché COMPILE réellement du code Java (fumée).
 */
class PatcheurEcjTest {

    companion object {
        private lateinit var jarEcj: Path
        private lateinit var jarSortie: Path
        private lateinit var rapport: RapportPatch
        private lateinit var dossierTravail: Path

        @BeforeClass
        @JvmStatic
        fun patcherLeVraiJar() {
            jarEcj = localiserJarEcj()
            dossierTravail = Files.createTempDirectory("ecj-art-integration")
            val racineShims = Path.of(
                jo.codeide.lsp.ecjart.compat.VersionJava::class.java
                    .protectionDomain.codeSource.location.toURI(),
            )
            val liste = ListeShims.charger(ListeShimsTest.localiserRessourceListe(), "3.40.0")
            jarSortie = dossierTravail.resolve("ecj-3.40.0-art.jar")
            rapport = PatcheurJar(jarEcj, racineShims, jarSortie, liste).patcher()
        }

        /** Localise le jar ecj du classpath de test via la classe Parser elle-même. */
        private fun localiserJarEcj(): Path {
            val url = org.eclipse.jdt.internal.compiler.parser.Parser::class.java
                .protectionDomain.codeSource.location
            assertNotNull("jar ecj introuvable sur le classpath de test", url)
            return Path.of(url.toURI())
        }
    }

    // ------------------------------------------------------------------
    // Contrat de la liste versionnée (ADR 0018)
    // ------------------------------------------------------------------

    @Test
    fun `les motifs entrants correspondent exactement a la liste 3_40_0`() {
        // Chiffres du spike (resultats/scan-art-v3-ecj.txt) — verrou d'équivalence.
        val motifs = rapport.motifsEntrants.associate { it.cle to it.occurrences }
        assertEquals(1, motifs["java/lang/Runtime\$Version"])
        assertEquals(3, motifs["java/io/InputStream.readAllBytes"])
        assertEquals(1, motifs["java/io/InputStream.readNBytes"])
        // Motifs surveillés, présents mais tolérés (présents sur ART) :
        assertTrue(motifs.containsKey("java/lang/invoke/MethodHandles"))
        assertTrue(motifs.containsKey("java/util/ServiceLoader"))
    }

    @Test
    fun `le jar sortant est re-scanne sans aucun motif bloquant`() {
        val residuels = ScanneurArt().scanner(jarSortie).motifs()
            .filter { it.cle !in setOf("java/lang/invoke/MethodHandles", "java/util/ServiceLoader") }
        assertEquals("motifs bloquants résiduels : $residuels", 0, residuels.size)
    }

    // ------------------------------------------------------------------
    // Classes effectivement patchées
    // ------------------------------------------------------------------

    @Test
    fun `exactement 4 classes patchees - Parser en remap, 3 appelants en redirection`() {
        val patchees = rapport.classesPatchees.associate { it.chemin to it }
        assertEquals(4, patchees.size)

        val parser = patchees["org/eclipse/jdt/internal/compiler/parser/Parser.class"]
        assertNotNull("Parser doit être patché", parser)
        assertTrue(parser!!.remappee)

        val util = patchees["org/eclipse/jdt/internal/compiler/util/Util.class"]
        assertNotNull("Util doit être patché", util)
        // 3 SITES d'appel constatés au désassemblage d'ecj 3.40.0 :
        // readAllBytes() ×2 + readNBytes(I) ×1 — le scanner compte les ENTRÉES
        // de pool (2 pour Util), le rapport compte les SITES redirigés (3).
        assertEquals(3, util!!.redirections)

        val jsr199 = patchees["org/eclipse/jdt/internal/compiler/batch/ClasspathJsr199.class"]
        assertEquals(1, jsr199!!.redirections)
        val archive = patchees["org/eclipse/jdt/internal/compiler/tool/ArchiveFileObject.class"]
        assertEquals(1, archive!!.redirections)
    }

    @Test
    fun `le Parser pathe reference VersionJava et plus jamais Runtime_Version`() {
        val octets = lireEntree("org/eclipse/jdt/internal/compiler/parser/Parser.class")
        val texte = String(octets, Charsets.ISO_8859_1)
        assertTrue("VersionJava attendu dans le Parser patché", texte.contains("VersionJava"))
        assertTrue("plus aucune mention de Runtime\$Version", !texte.contains("Runtime\$Version"))
    }

    @Test
    fun `les trois appelants de flux referencent LectureFlux`() {
        for (chemin in listOf(
            "org/eclipse/jdt/internal/compiler/util/Util.class",
            "org/eclipse/jdt/internal/compiler/batch/ClasspathJsr199.class",
            "org/eclipse/jdt/internal/compiler/tool/ArchiveFileObject.class",
        )) {
            val octets = lireEntree(chemin)
            assertTrue("$chemin doit référencer LectureFlux", String(octets, Charsets.ISO_8859_1).contains("LectureFlux"))
            // Vérification négative SÉMANTIQUE (ASM), pas textuelle : la
            // chaîne « readAllBytes » subsiste légitimement dans Util pour
            // java.nio.file.Files.readAllBytes(Path) — présent sur ART
            // (API 26+), hors périmètre des shims.
            val invoques = methodesInvoquees(octets)
            assertTrue(
                "$chemin ne doit plus invoquer InputStream.readAllBytes ni readNBytes",
                ("java/io/InputStream" to "readAllBytes") !in invoques &&
                    ("java/io/InputStream" to "readNBytes") !in invoques,
            )
        }
    }

    @Test
    fun `une classe non ciblee est recopiee octet pour octet`() {
        val chemin = "org/eclipse/jdt/internal/compiler/ast/ASTNode.class"
        assertArrayEquals(lireEntreeJar(jarEcj, chemin), lireEntree(chemin))
    }

    // ------------------------------------------------------------------
    // Emballage du jar patché
    // ------------------------------------------------------------------

    @Test
    fun `signatures amont retirees, manifeste et services conserves, shims et notice presents`() {
        assertEquals(
            listOf("META-INF/ECLIPSE_.RSA", "META-INF/ECLIPSE_.SF"),
            rapport.signaturesRetirees.sorted(),
        )
        val noms = nomsEntrees(jarSortie)
        assertTrue("manifeste conservé", noms.contains("META-INF/MANIFEST.MF"))
        assertTrue("service conservé", noms.contains("META-INF/services/javax.tools.JavaCompiler"))
        assertTrue("shim VersionJava embarqué", noms.contains("jo/codeide/lsp/ecjart/compat/VersionJava.class"))
        assertTrue("shim LectureFlux embarqué", noms.contains("jo/codeide/lsp/ecjart/compat/LectureFlux.class"))
        assertTrue("registre EPL présent", noms.contains("META-INF/ECJ-ART-MODIFICATIONS.txt"))
        assertTrue("aucune signature résiduelle", noms.none { it.startsWith("META-INF/") && it.endsWith(".SF", true) })
    }

    @Test
    fun `le registre des modifications documente chaque transformation (EPL-2_0)`() {
        val registre = String(lireEntree("META-INF/ECJ-ART-MODIFICATIONS.txt"), Charsets.UTF_8)
        assertTrue(registre.contains("MODIFIÉ"))
        assertTrue(registre.contains("EPL-2.0"))
        assertTrue(registre.contains("Parser.class : remap de type java/lang/Runtime\$Version"))
        assertTrue(registre.contains("readAllBytes"))
        assertTrue(registre.contains("ECLIPSE_.SF"))
    }

    // ------------------------------------------------------------------
    // Fumée : le jar patché compile du code Java
    // ------------------------------------------------------------------

    @Test
    fun `compilation de fumee avec le jar ecj pathe`() {
        val source = dossierTravail.resolve("EssaiFumee.java").apply {
            writeText("public class EssaiFumee { public static int carre(int n) { return n * n; } }\n")
        }
        val sortieCompilation = Files.createDirectories(dossierTravail.resolve("classes-fumee"))

        // Chargeur isolé : jar patché (ecj patché + shims embarqués) puis
        // kotlin-stdlib (les shims Kotlin appellent Intrinsics — déjà présent
        // dans l'APK CodeIDE, fourni ici pour la machine de test).
        val stdlib = Path.of(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain.codeSource.location.toURI(),
        )
        val chargeur = URLClassLoader(
            arrayOf(jarSortie.toUri().toURL(), stdlib.toUri().toURL()),
            ClassLoader.getPlatformClassLoader(),
        )
        chargeur.use { c ->
            val classeMain = c.loadClass("org.eclipse.jdt.internal.compiler.batch.Main")
            val constructeur = classeMain.getConstructor(
                PrintWriter::class.java, PrintWriter::class.java, java.lang.Boolean.TYPE,
            )
            val compilateur = constructeur.newInstance(PrintWriter(System.out, true), PrintWriter(System.err, true), false)
            val ok = classeMain.getMethod("compile", Array<String>::class.java).invoke(
                compilateur,
                arrayOf("-d", sortieCompilation.toString(), "-nowarn", source.toString()),
            )
            assertEquals("la compilation doit réussir", java.lang.Boolean.TRUE, ok)
            assertTrue("la classe compilée doit exister", Files.exists(sortieCompilation.resolve("EssaiFumee.class")))
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private fun lireEntree(chemin: String): ByteArray = lireEntreeJar(jarSortie, chemin)

    /**
     * Méthodes invoquées par une classe (paires propriétaire/nom), lues par
     * ASM — sert à la vérification sémantique « ne plus invoquer X ».
     */
    private fun methodesInvoquees(octets: ByteArray): Set<Pair<String, String>> {
        val invoques = mutableSetOf<Pair<String, String>>()
        ClassReader(octets).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    acces: Int,
                    nom: String?,
                    descripteur: String?,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? = object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        proprietaire: String?,
                        nomMethode: String?,
                        descripteurMethode: String?,
                        estInterface: Boolean,
                    ) {
                        invoques.add((proprietaire ?: "") to (nomMethode ?: ""))
                    }
                }
            },
            ClassReader.SKIP_FRAMES,
        )
        return invoques
    }

    private fun lireEntreeJar(jar: Path, chemin: String): ByteArray =
        ZipFile(jar.toFile()).use { z -> z.getInputStream(z.getEntry(chemin)).readBytes() }

    private fun nomsEntrees(jar: Path): List<String> =
        ZipFile(jar.toFile()).use { z -> z.entries().asSequence().map { it.name }.toList() }
}

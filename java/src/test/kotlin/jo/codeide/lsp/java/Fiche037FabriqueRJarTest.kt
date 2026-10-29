package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.LecteurZipBrut
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 037 — PopupSpontaneReproTestHelper : fabrique du R.jar « réaliste »
 * (aapt2-like) — source `R.java` compilée, 4 entrées exactement.
 *
 * La compilation passe par ecj EN MÉMOIRE ([OutilsFixture.compilerEnMemoire])
 * — l'équivalent Kotlin du javac du cahier, sans dépendance environnementale ;
 * tout échec de compilation est fatal (la fixture est supposée toujours
 * compiler).
 */
class Fiche037FabriqueRJarTest {

    /** La source R.java du cahier : classe finale, classes internes statiques vides. */
    private val SOURCE_R = """
        package com.example.monip;

        public final class R {
            public static final class style {
            }

            public static final class id {
            }

            public static final class string {
            }
        }
    """.trimIndent()

    /**
     * buildRJar : rend le chemin d'un jar contenant exactement 4 entrées —
     * R, R$style, R$id, R$string — compilées depuis la source embarquée.
     */
    private fun buildRJar(dossier: Path): Path {
        val classes = OutilsFixture.compilerEnMemoire(
            dossier,
            mapOf("/com/example/monip/R.java" to SOURCE_R),
        )
        assertEquals(4, classes.size, "4 classes compilées : ${classes.keys}")
        val jar = dossier.resolve("r-bench.jar")
        val entrees = LinkedHashMap<String, ByteArray>()
        entrees["com/example/monip/R.class"] = classes.getValue("com/example/monip/R.class")
        entrees["com/example/monip/R\$style.class"] = classes.getValue("com/example/monip/R\$style.class")
        entrees["com/example/monip/R\$id.class"] = classes.getValue("com/example/monip/R\$id.class")
        entrees["com/example/monip/R\$string.class"] = classes.getValue("com/example/monip/R\$string.class")
        OutilsFixture.ecrireJar(jar, entrees)
        return jar
    }

    @Test
    fun buildRJarQuatreEntrees() {
        val dossier = Files.createTempDirectory("fiche037")
        val jar = buildRJar(dossier)
        assertTrue(Files.isRegularFile(jar), "le jar existe")
        val noms = LecteurZipBrut.scanNames(jar)
        assertEquals(
            setOf(
                "com/example/monip/R.class",
                "com/example/monip/R\$style.class",
                "com/example/monip/R\$id.class",
                "com/example/monip/R\$string.class",
            ),
            noms,
            "exactement 4 entrées",
        )
        // Les octets sont des fichiers de classe valides (magic).
        for (nom in noms) {
            val octets = LecteurZipBrut.readSingle(jar, nom)
            assertEquals(0xCA, octets[0].toInt() and 0xFF, "magic CA pour $nom")
        }
    }
}

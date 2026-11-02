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
 * La compilation passe par ecj EN MÉMOIRE ([OutilsFixture.fabriquerRJar] —
 * l'équivalent Kotlin du javac du cahier, sans dépendance environnementale) ;
 * tout échec de compilation est fatal (la fixture est supposée toujours
 * compiler). La fabrique est PARTAGÉE avec la fiche 036 (le cahier : la
 * version dupliquée du test de repro, « la version partagée est celle du
 * helper » — la réécriture la concentre dans [OutilsFixture]).
 */
class Fiche037FabriqueRJarTest {

    /**
     * buildRJar : rend le chemin d'un jar contenant exactement 4 entrées —
     * R, R$style, R$id, R$string — compilées depuis la source embarquée.
     */
    private fun buildRJar(dossier: Path): Path = OutilsFixture.fabriquerRJar(dossier)

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

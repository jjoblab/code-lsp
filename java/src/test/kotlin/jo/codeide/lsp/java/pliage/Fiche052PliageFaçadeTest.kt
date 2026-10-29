package jo.codeide.lsp.java.pliage

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.inlay.GenreIndice
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Fiche 052 — FoldingRangeFacadeTest : les handlers `foldingRange` (imports
 * repliés par défaut avec texte replié, javadoc/commentaires, corps des
 * accolades — avec précision CARACTÈRE du dépliage) et `inlayHint` (hint de
 * type `var` inféré par bindings).
 *
 * Le harnais du domaine éprouvait la FAÇADE LSP réelle (`JavaTextDocumentService`,
 * didOpen + requête asynchrone) sur android.jar ; la réécriture verrouille
 * le MIROIR MOTEUR : [ServicePliage.plisLsp] (coordonnées LSP lignes et
 * caractères — la couche LSP4J arrive à l'étage N8) et [MoteurJava.inlayHints]
 * pour le hint `var`. La gate environnementale du cahier (android.jar pour
 * le classpath de la façade) devient la gate jrt du moteur : `String.valueOf`
 * résout par l'image modules du JDK — la fixture « documents posés sur
 * disque » est conservée (racine source réelle).
 */
class Fiche052PliageFaçadeTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava

        /** Source avec 2 imports consécutifs, une javadoc de classe, une classe et une méthode. */
        val SOURCE_PLIAGE = listOf(
            "// entête du document de test.",
            "package p;",
            "import java.util.List;",
            "import java.util.Map;",
            "",
            "/**",
            " * Javadoc de la classe.",
            " */",
            "public class Run {",
            "    void run() {",
            "        int x = 1;",
            "    }",
            "}",
        ).joinToString("\n") + "\n"

        /** Source avec un `var` inféré par bindings dans un corps de méthode. */
        val SOURCE_INLAY = listOf(
            "package p;",
            "",
            "public class H {",
            "    void f() {",
            "        var s = String.valueOf(1);",
            "    }",
            "}",
        ).joinToString("\n") + "\n"

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            MOTEUR = MoteurJava(racines = emptyList(), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — miroir moteur ignoré")
        }
    }

    /** foldingRange_importsBlockCommentAndBodies — folds imports/commentaires/corps. */
    @Test
    fun foldingRangeImportsBlockCommentAndBodies() {
        val plis = ServicePliage.plisLsp(SOURCE_PLIAGE)
        assertTrue(plis.size >= 4, "au moins 4 folds rendues — obtenu : $plis")

        // Fold des imports : kind « imports », lignes 2 → 3, texte replié « import ... ».
        val imports = plis.filter { it.genre == GenrePli.IMPORTS }
        assertEquals(1, imports.size)
        assertEquals(2, imports[0].ligneDebut, "imports : ligne de départ 2 (0-based)")
        assertEquals(3, imports[0].ligneFin, "imports : ligne de fin 3")
        assertEquals("import ...", imports[0].texteReplie)
        assertTrue(imports[0].replieParDefaut)

        // Fold de la javadoc : kind « comment », ligne de départ 5.
        val javadoc = plis.filter { it.genre == GenrePli.COMMENTAIRE }
        assertEquals(1, javadoc.size)
        assertEquals(5, javadoc[0].ligneDebut, "javadoc : ligne de départ 5 (0-based)")

        // Fold du corps de run() : kind « block », lignes 9 → 11, DÉMARRANT juste
        // après le « { » (colonne 16) et se terminant SUR la « } » (colonne 4).
        val ligneRun = SOURCE_PLIAGE.lines()[9]
        val ouvrante = ligneRun.indexOf('{')
        assertEquals(15, ouvrante, "l'accolade ouvrante de run() vit en colonne 15 — ligne : « $ligneRun »")
        val corps = plis.first { it.genre == GenrePli.BLOC && it.ligneDebut == 9 }
        assertEquals(16, corps.colonneDebut, "dépliage juste APRÈS le « { » (colonne 16)")
        assertEquals(11, corps.ligneFin, "fin sur la ligne de la « } » de run()")
        assertEquals(4, corps.colonneFin, "fin SUR la « } » (colonne 4)")
    }

    /** inlayHint_varTypeFromBindings — hint de type var par bindings. */
    @Test
    fun inlayHintVarTypeFromBindings() {
        val indices = MOTEUR.inlayHints("/p/H.java", SOURCE_INLAY)
        val cible = indices.firstOrNull { it.genre == GenreIndice.TYPE }
        assertNotNull(cible, "un hint de type est rendu pour le var — obtenu : $indices")
        assertEquals(": String", cible.libelle, "libellé « : String » (inférence par bindings)")
        assertEquals(GenreIndice.TYPE, cible.genre)
        // Position : ligne 4, caractère 13 — fin du nom `s`, juste avant le « = ».
        val ligneVar = SOURCE_INLAY.lines()[4]
        assertEquals(12, ligneVar.indexOf(" s =").let { ligneVar.indexOf('s', it) }, "le nom `s` vit en colonne 12")
        val position = cible.offset
        val ligne = SOURCE_INLAY.substring(0, position).count { it == '\n' }
        val colonne = position - SOURCE_INLAY.substring(0, position).lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        assertEquals(4, ligne, "hint en ligne 4 (0-based)")
        assertEquals(13, colonne, "hint en caractère 13 — fin du nom + 1")
    }
}

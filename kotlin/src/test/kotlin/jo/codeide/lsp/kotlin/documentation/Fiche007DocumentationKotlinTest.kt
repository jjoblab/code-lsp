package jo.codeide.lsp.kotlin.documentation

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.ParseurKotlin
import jo.codeide.lsp.kotlin.parse.kt.KtClasse
import jo.codeide.lsp.kotlin.parse.kt.KtDeclaration

/**
 * Fiche 007 — DocumentationKotlinTest : le moteur de KDoc du cahier
 * (hover-inlay — S2, survol riche, KDocParser vendoré) — 9
 * comportements du cahier docs/cahier-specifications-kotlin/.
 *
 * Le moteur est la REPRISE voie C R8 (manifeste
 * docs/PROVENANCE-REPRISE-KOTLIN.md §3) : extraction du DOC_COMMENT
 * porté par la déclaration, rendu en texte neutre (sections libres +
 * tags). Tolérance D3 : rien ne lève.
 *
 * Chaque comportement porte le nom exact du test source (identifiant
 * de traçabilité du cahier).
 */
class Fiche007DocumentationKotlinTest {

    /** Le parseur du test (sans état). */
    private val parseur = ParseurKotlin()

    /** La première déclaration du fichier analysé, avec les commentaires du document. */
    private fun premiereDeclaration(texte: String): Pair<KtDeclaration, List<JetonBrut>> {
        val fichier = parseur.arbre(texte)
        return Pair(fichier.declarations().single(), fichier.noeud.commentaires)
    }

    @Test
    fun `kdoc-simple---texte-libre-rendu,-asteriskes-et-blancs-detaches`() {
        val (declaration, commentaires) =
            premiereDeclaration(
                "/**\n" +
                    " * La vue de rendu.\n" +
                    " * Un paragraphe de plus.\n" +
                    " */\n" +
                    "class Rendu\n",
            )
        assertEquals(
            "La vue de rendu.\nUn paragraphe de plus.",
            DocumentationKotlin.kdocDe(declaration, commentaires),
        )
    }

    @Test
    fun `kdoc-avec-tags---param-et-return-rendus-en-lignes`() {
        val (declaration, commentaires) =
            premiereDeclaration(
                "/**\n" +
                    " * Calcule la taille.\n" +
                    " *\n" +
                    " * @param x le nombre de colonnes\n" +
                    " * @param y la hauteur\n" +
                    " * @return le total\n" +
                    " */\n" +
                    "class Rendu(val x: Int, val y: Int) {\n" +
                    "    fun total(): Int = x + y\n" +
                    "}\n",
            )
        assertEquals(
            "Calcule la taille.\n" +
                "@param x — le nombre de colonnes\n" +
                "@param y — la hauteur\n" +
                "@return le total",
            DocumentationKotlin.kdocDe(declaration, commentaires),
        )
    }

    @Test
    fun `kdoc-d-un-membre---fonction-documentée`() {
        val fichier = parseur.arbre(
            "class A {\n" +
                "    /** Rend la taille. */\n" +
                "    fun taille(): Int = 1\n" +
                "}\n",
        )
        val classe = fichier.declarations().single() as KtClasse
        val membre = classe.membres().single()
        assertEquals("Rend la taille.", DocumentationKotlin.kdocDe(membre, fichier.noeud.commentaires))
    }

    @Test
    fun `kdoc-avant-annotations---porte-quand-meme-par-la-declaration`() {
        val (declaration, commentaires) =
            premiereDeclaration(
                "/** La doc malgré l'annotation. */\n" +
                    "@Deprecated\n" +
                    "class Vieux\n",
            )
        assertEquals("La doc malgré l'annotation.", DocumentationKotlin.kdocDe(declaration, commentaires))
    }

    @Test
    fun `sans-kdoc---null`() {
        val (declaration, commentaires) = premiereDeclaration("class Nue\n")
        assertNull(DocumentationKotlin.kdocDe(declaration, commentaires))
    }

    @Test
    fun `block-comment-n-est-pas-un-kdoc---null`() {
        val (declaration, commentaires) =
            premiereDeclaration(
                "/* un bloc, pas une doc */\n" +
                    "class A\n",
            )
        assertNull(DocumentationKotlin.kdocDe(declaration, commentaires))
    }

    @Test
    fun `kdoc-vide---etoiles-seules,-null`() {
        val (declaration, commentaires) =
            premiereDeclaration(
                "/**\n" +
                    " *\n" +
                    " */\n" +
                    "class A\n",
            )
        assertNull(DocumentationKotlin.kdocDe(declaration, commentaires))
    }

    @Test
    fun `kdoc-une-ligne---rendu-sans-entourage`() {
        val (declaration, commentaires) = premiereDeclaration("/** Doc tenue courte. */\nclass A\n")
        assertEquals("Doc tenue courte.", DocumentationKotlin.kdocDe(declaration, commentaires))
    }

    @Test
    fun `tag-sans-contenu---rendu-degage-sans-exception`() {
        val (declaration, commentaires) =
            premiereDeclaration(
                "/**\n * Rend.\n * @return\n */\nclass A\n",
            )
        val rendu = DocumentationKotlin.kdocDe(declaration, commentaires)
        assertTrue(rendu!!.contains("@return"), "le tag nu est rendu")
        assertTrue(rendu.contains("Rend."), "le texte libre est rendu")
    }
}

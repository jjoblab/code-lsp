package jo.codeide.lsp.kotlin.plis

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import jo.codeide.lsp.kotlin.parse.ParseurKotlin

/**
 * Fiche 014 — PlisDocumentKotlinTest : le moteur de pliage complet du
 * cahier (folding, S2 — plan de stabilisation LSP §3.2) — 7
 * comportements du cahier docs/cahier-specifications-kotlin/.
 *
 * Le moteur est la REPRISE voie C R7 (manifeste
 * docs/PROVENANCE-REPRISE-KOTLIN.md §3) : chaque corps à accolades est
 * UNE plage (de la première accolade ouvrante à la dernière fermante —
 * JAMAIS du début du nœud), le bloc d'imports entier en est un, les
 * commentaires multi-lignes aussi ; déduplication BODY/BLOCK ;
 * tolérance D3 (jamais d'exception).
 *
 * Chaque comportement porte le nom exact du test source (identifiant
 * de traçabilité du cahier).
 */
class Fiche014PlisDocumentKotlinTest {

    /** Le parseur du test (sans état). */
    private val parseur = ParseurKotlin()

    /** Les plages pliables du texte (moteur R7, chemin direct). */
    private fun plagesDe(texte: String) = PlisDocumentKotlin.depuis(parseur.arbre(texte).noeud)

    @Test
    fun `corps-de-classe-et-de-fonctions---une-plage-par-paire-d-accolades`() {
        val texte =
            "class Rendu {\n" +
                "    fun taille(): Int {\n" +
                "        return 1\n" +
                "    }\n" +
                "\n" +
                "    fun vide() {}\n" +
                "}\n"
        val plages = plagesDe(texte)
        // Le corps de `vide()` est MONO-LIGNE : il reste ÉMIS par le
        // moteur (l'hôte filtre) — deux blocs de fonction + la classe.
        assertEquals(3, plages.size)
        val classe = plages.first()
        assertEquals(texte.indexOf('{'), classe.plage.debut, "le pli CLASSE commence à l'accolade")
        assertEquals(texte.lastIndexOf('}') + 1, classe.plage.fin, "…et finit à la fermante")
        val taille = plages[1]
        assertEquals(texte.indexOf('{', texte.indexOf("taille")), taille.plage.debut)
        assertEquals(texte.indexOf('}', texte.indexOf("return")) + 1, taille.plage.fin)
    }

    @Test
    fun `liste-d-imports---du-premier-au-dernier`() {
        val texte =
            "package jo.essai\n" +
                "\n" +
                "import android.view.View\n" +
                "import android.view.ViewGroup\n" +
                "import android.widget.TextView\n" +
                "\n" +
                "class A\n"
        val plages = plagesDe(texte)
        val imports = plages.first()
        assertEquals(texte.indexOf("import"), imports.plage.debut, "début du PREMIER import")
        assertEquals(
            texte.indexOf("TextView") + "TextView".length,
            imports.plage.fin,
            "fin du DERNIER import",
        )
    }

    @Test
    fun `commentaire-multi-ligne---KDoc-et-bloc-pliables`() {
        val texte =
            "/**\n" +
                " * La doc.\n" +
                " */\n" +
                "class A {\n" +
                "    /* bloc\n" +
                "     * multi */\n" +
                "    fun f() {}\n" +
                "}\n"
        val plages = plagesDe(texte)
        val kdoc = plages.first()
        assertEquals(0, kdoc.plage.debut, "le KDoc commence à 0")
        assertEquals(texte.indexOf("*/") + 2, kdoc.plage.fin, "…et finit à son `*/`")
        // Ordre du document : le KDoc, la classe, le bloc, la fonction.
        val bloc = plages.first { it.plage.debut == texte.indexOf("/*", kdoc.plage.fin) }
        assertEquals(
            texte.indexOf("multi */") + "multi */".length,
            bloc.plage.fin,
            "fin du bloc multi-ligne",
        )
    }

    @Test
    fun `lambda-et-when---pliés-par-accolades,-pas-de-doublon`() {
        val texte =
            "fun f(v: Int): String {\n" +
                "    val g = { x: Int -> x + 1 }\n" +
                "    return when (v) {\n" +
                "        0 -> \"zero\"\n" +
                "        else -> \"autre\"\n" +
                "    }\n" +
                "}\n"
        val plages = plagesDe(texte)
        // AUCUN doublon : les paires (debut, fin) sont dédupliquées.
        assertEquals(plages.size, plages.distinctBy { it.plage.debut to it.plage.fin }.size)
        // Le when est plié à ses accolades (pas au mot-clé) :
        val pliWhen = plages.last { it.plage.debut == texte.indexOf('{', texte.indexOf("when")) }
        assertEquals(
            texte.indexOf('}', texte.indexOf("zero")) + 1,
            pliWhen.plage.fin,
            "fin du when (accolades, pas le mot-clé)",
        )
        // Le lambda mono-ligne reste émis (filtre hôte) :
        assertTrue(
            plages.any { it.plage.debut == texte.indexOf('{', texte.indexOf("val g")) },
            "le lambda est pliable",
        )
    }

    @Test
    fun `structures-de-controle---if-for-while-sans-doublon-BODY-BLOCK`() {
        val texte =
            "fun f(xs: List<Int>) {\n" +
                "    if (xs.isNotEmpty()) {\n" +
                "        for (x in xs) {\n" +
                "            while (x > 0) {\n" +
                "                println(x)\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n"
        val plages = plagesDe(texte)
        assertEquals(plages.size, plages.distinctBy { it.plage.debut to it.plage.fin }.size, "aucun doublon")
        // Quatre blocs distincts : f, if, for, while.
        assertEquals(4, plages.size)
    }

    @Test
    fun `edition-en-cours---accolade-fermante-absente,-sous-rapport-sans-exception`() {
        val texte = "fun f() {\n    val x = 1\n"
        val plages = plagesDe(texte)
        assertTrue(plages.isEmpty(), "sans paire d'accolades, aucun pli")
    }

    @Test
    fun `document-vide-ou-mono-ligne---rendu-honnete,-filtre-hote-ensuite`() {
        assertTrue(plagesDe("").isEmpty())
        // `fun f() {}` tout sur une ligne : la plage est émise (le
        // filtre mono-ligne appartient à l'hôte, qui voit les lignes).
        val plages = plagesDe("fun f() {}")
        assertEquals(1, plages.size)
        assertEquals(8, plages.single().plage.debut, "accolade ouvrante")
        assertEquals(10, plages.single().plage.fin, "après la fermante")
    }
}

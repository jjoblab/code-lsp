package jo.codeide.lsp.java.banc

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * FormatEchange — tests du format d'échange du banc de parité croisé
 * (étage T, ADR 0016) : aller-retour corpus/résultats (marqueur « ¦ »,
 * échappements, non-ASCII), grammaire REFUSIVE (clé inconnue, contenu
 * résiduel, entrée tronquée = échec bruyant, jamais de silence).
 */
class FormatEchangeTest {

    /** Aller-retour corpus : le marqueur de caret et les sauts de ligne survivent. */
    @Test
    fun `aller retour corpus avec marqueur et retours ligne`() {
        val corpus = CorpusBrut(
            positions = listOf(
                PositionBrute("membre-longueur", "return s.le¦"),
                PositionBrute(
                    "source-multiple-lignes",
                    "package demo;\n\npublic class Main {\n    void m() {\n        return \"a\\\"b\".le¦\n    }\n}\n",
                ),
            ),
            succession = listOf(PositionBrute("frappe-0", "return s.¦")),
        )
        val relu = FormatEchange.lireCorpus(FormatEchange.ecrireCorpus(corpus))
        assertEquals(corpus, relu)
    }

    /** Aller-retour résultats : l'ORDRE des libellés est l'information de parité. */
    @Test
    fun `aller retour resultats conserve l ordre`() {
        val resultats = listOf(
            ResultatPosition("membre-z-champ", listOf("zAdd", "zCount")),
            ResultatPosition("nom-locale-domine", listOf("abd", "abc", "abstract")),
            ResultatPosition("vide", emptyList()),
        )
        val relu = FormatEchange.lireResultats(FormatEchange.ecrireResultats(resultats))
        assertEquals(resultats, relu)
    }

    /** La sortie est ASCII PURE (le marqueur « ¦ » part en \u00a6) : transportable par n'importe quel charset. */
    @Test
    fun `sortie ascii pure par echappement unicode`() {
        val corpus = CorpusBrut(
            listOf(PositionBrute("case-ROUGE", "case RO¦")),
            emptyList(),
        )
        val texte = FormatEchange.ecrireCorpus(corpus)
        assertTrue(texte.chars().allMatch { it in 0x20..0x7E || it == '\n'.code || it == '\t'.code })
        assertEquals(corpus, FormatEchange.lireCorpus(texte))
    }

    /** Clé inconnue = rejet (dérive de format = échec bruyant). */
    @Test
    fun `cle inconnue rejetee`() {
        assertFailsWith<IllegalArgumentException> {
            FormatEchange.lireCorpus("{ \"positions\": [], \"succession\": [], \"extra\": 1 }")
        }
    }

    /** Contenu résiduel après la racine = rejet. */
    @Test
    fun `contenu residuel rejete`() {
        val texte = FormatEchange.ecrireResultats(listOf(ResultatPosition("a", listOf("b"))))
        assertFailsWith<IllegalArgumentException> {
            FormatEchange.lireResultats(texte + "débris")
        }
    }

    /** Chaîne non fermée = rejet avec position (fichier corrompu). */
    @Test
    fun `chaine tronquee rejetee`() {
        assertFailsWith<IllegalArgumentException> {
            FormatEchange.lireCorpus("{ \"positions\": [ { \"nom\": \"a")
        }
    }

    /** Les échappements JSON usuels passent à l'aller-retour. */
    @Test
    fun `echappements usuels preserves`() {
        val source = "a\\\\b \"c\" d\te\nf"
        val corpus = CorpusBrut(listOf(PositionBrute("x", source)), emptyList())
        assertEquals(source, FormatEchange.lireCorpus(FormatEchange.ecrireCorpus(corpus)).positions[0].source)
    }
}

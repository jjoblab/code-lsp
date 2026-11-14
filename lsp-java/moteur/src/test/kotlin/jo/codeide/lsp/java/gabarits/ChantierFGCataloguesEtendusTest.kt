package jo.codeide.lsp.java.gabarits

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Chantiers F+G (14/11/2026) — catalogues étendus :
 *  - F : postfixes IntelliJ (while, notnull, null, syserr, return, assert,
 *    throw, for) avec gardes typées ;
 *  - G : gabarits vivants IntelliJ (psvm, serr, ifn, inn, St, inst, iter,
 *    itar, thr, main) en position d'instruction.
 */
class ChantierFGCataloguesEtendusTest {

    /** La fixture porte CHAQUE clé testée sur un récepteur « nom. ». */
    private val SOURCE = """
        package demo;

        public class Main {
            void go(String nom) {
                nom.while;
                nom.notnull;
                nom.null;
                nom.throw;
                nom.syserr;
                nom.return;
                nom.assert;
                nom.for;
            }
        }
    """.trimIndent()

    /** Appel direct : le récepteur « nom. » porte la clé (offset sur la clé). */
    private fun items(cle: String, type: String?): List<ItemPostfixe> {
        val corps = "nom.$cle"
        val debutCle = SOURCE.indexOf(corps) + 4 // après « nom. »
        assertTrue(debutCle > 4, "la fixture doit contenir « $corps »")
        return GabaritsPostfixes.itemsPour(SOURCE, debutCle, cle, type)
    }

    @Test
    fun `postfixe while - garde booleen`() {
        assertTrue(items("while", "boolean").isNotEmpty(), "while sur booléen proposé")
        assertTrue(items("while", "java.lang.String").isEmpty(), "while exige un booléen")
    }

    @Test
    fun `postfixes null notnull - garde reference`() {
        assertTrue(items("notnull", "java.lang.String").isNotEmpty(), "notnull sur référence proposé")
        assertTrue(items("null", "java.lang.String").isNotEmpty(), "null sur référence proposé")
        assertTrue(items("null", "int").isEmpty(), "null exige une référence")
    }

    @Test
    fun `postfixe throw - garde throwable`() {
        assertTrue(items("throw", "java.lang.Exception").isNotEmpty(), "throw sur Throwable proposé")
        assertTrue(items("throw", "java.lang.String").isEmpty(), "throw exige un Throwable")
    }

    @Test
    fun `postfixes syserr return assert for - proposes`() {
        assertTrue(items("syserr", "java.lang.String").isNotEmpty(), "syserr proposé")
        assertTrue(items("return", "int").isNotEmpty(), "return proposé")
        assertTrue(items("assert", "boolean").isNotEmpty(), "assert proposé")
        assertTrue(items("for", "java.util.List").isNotEmpty(), "for sur référence proposé")
    }

    @Test
    fun `gabarits vivants - cles etendues proposees`() {
        for (cle in listOf("psvm", "main", "serr", "ifn", "inn", "St", "inst", "iter", "itar", "thr")) {
            val items = GabaritsVivants.itemsPour(cle)
            assertTrue(items.isNotEmpty(), "« $cle » doit être proposé (clé exacte)")
            assertEquals(cle, items.first().gabarit.cle, "la clé exacte domine la liste")
        }
    }

    @Test
    fun `gabarits vivants - position instruction reconnue`() {
        // La clé « nom.while » suit « { » de méthode : position légitime.
        val position = SOURCE.indexOf("nom.while")
        assertTrue(GabaritsVivants.estPositionInstruction(SOURCE, position), "après « { » = position d'instruction")
    }
}

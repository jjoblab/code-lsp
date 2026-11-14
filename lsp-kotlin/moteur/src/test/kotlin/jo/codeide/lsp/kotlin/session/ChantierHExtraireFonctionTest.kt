// CodeIDE — CHANTIER H (parité Android Studio « Extract Function »,
// 14/11/2026) : l'expression englobante du caret devient le CORPS d'une
// nouvelle fonction privée INSÉRÉE après la fonction porteuse,
// l'expression est REMPLACÉE par l'APPEL — kind refactor.extract, le
// complément fonction de l'extraction de variable KC-11.
package jo.codeide.lsp.kotlin.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le chantier H : l'extraction de fonction bornée.
 *
 * **Les cas verrouillés** :
 *  - une expression SANS CAPTURE dans une instruction : la fonction
 *    privée est insérée après la porteuse, l'appel remplace
 *    l'expression ;
 *  - une expression CAPTURANT une locale (paramètre ou val locale)
 *    REFUSE le plan — les paramètres de la fonction extraite exigent
 *    le typage complet ;
 *  - le caret hors fonction ne rend RIEN (intra-fonction Q5) ;
 *  - l'expression-instruction ENTIÈRE refuse (miroir KC-11).
 */
class ChantierHExtraireFonctionTest {

    /** Une session de documents SANS classpath (l'extraction vit de l'arbre seul). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur =
            (jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin.unique)
                as MoteurLangageKotlinNoyau
        return moteur.creerSession() as SessionLangageKotlinDocuments
    }

    @Test
    fun `chantierH---expression-sans-capture-devient-fonction-appelee`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    val total = 1 + tailleMax()\n" +
                "    return total\n" +
                "}\n" +
                "fun tailleMax(): Int = 10\n"
        val uri = "file:///ch1.kt"
        session.ouvrir(uri, source)
        // Le caret dans l'expression `tailleMax()` (une référence de
        // premier niveau — pas une capture).
        val caret = source.indexOf("tailleMax")
        val plans = session.actions(uri, caret)
        val plan = plans.firstOrNull { it.titre.startsWith("Extraire") && it.titre.contains("fonction") }
        assertNotNull(plan, "le plan d'extraction de fonction devait exister ; rendu : ${plans.map { it.titre }}")
        val editions = plan.editions.single().editions
        assertEquals(2, editions.size, "le remplacement de l'appel + l'insertion de la fonction : $editions")
        // Le REMPLACEMENT : l'expression devient l'appel.
        assertEquals("extraitFonction()", editions[0].texte)
        assertEquals(
            "tailleMax()",
            source.substring(editions[0].plage.debut, editions[0].plage.fin),
            "la plage remplacée est l'expression extraite",
        )
        // L'INSERTION : après la fermeture de la porteuse, la fonction
        // privée au même niveau.
        assertTrue(
            editions[1].texte.contains("private fun extraitFonction() = tailleMax()"),
            "l'insertion porte la fonction privée : <<${editions[1].texte}>>",
        )
        assertEquals(
            "}",
            source.substring(editions[1].plage.debut - 1, editions[1].plage.debut),
            "l'insertion se fait juste après la fermeture de la porteuse",
        )
        session.close()
    }

    @Test
    fun `chantierH---capture-de-locale-refuse`() {
        val session = session()
        // L'expression capture `seuil` (paramètre) : la fonction
        // extraite exigerait un paramètre — refus.
        val source =
            "package demo\n" +
                "fun demo(seuil: Int): Int {\n" +
                "    val total = 1 + seuil\n" +
                "    return total\n" +
                "}\n"
        val uri = "file:///ch2.kt"
        session.ouvrir(uri, source)
        val caret = source.indexOf("seuil")
        val plans = session.actions(uri, caret)
        assertNull(
            plans.firstOrNull { it.titre.startsWith("Extraire") && it.titre.contains("fonction") },
            "une capture de locale devait refuser ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }

    @Test
    fun `chantierH---hors-fonction-ne-rend-rien`() {
        val session = session()
        val source =
            "package demo\n" +
                "val global = 1 + tailleMax()\n" +
                "fun tailleMax(): Int = 10\n"
        val uri = "file:///ch3.kt"
        session.ouvrir(uri, source)
        val caret = source.indexOf("tailleMax")
        val plans = session.actions(uri, caret)
        assertNull(
            plans.firstOrNull { it.titre.startsWith("Extraire") && it.titre.contains("fonction") },
            "hors fonction, pas d'extraction de fonction ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }

    @Test
    fun `chantierH---expression-instruction-entiere-refuse`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    return tailleMax()\n" +
                "}\n" +
                "fun tailleMax(): Int = 10\n"
        val uri = "file:///ch4.kt"
        session.ouvrir(uri, source)
        // Le caret sur l'appel — mais l'instruction est `return …` :
        // l'appel n'est pas l'instruction ENTIÈRE, l'extraction est
        // LÉGALE (l'appel devient le corps, `return extraitFonction()`).
        // La vraie borne : le caret sur `tailleMax` d'une instruction
        // ENTIERE-expression (`tailleMax()` seul dans le corps).
        val source2 =
            "package demo\n" +
                "fun demo(): Int = tailleMax() + 1\n" +
                "fun tailleMax(): Int = 10\n"
        val uri2 = "file:///ch5.kt"
        session.ouvrir(uri2, source2)
        val caret2 = source2.indexOf("tailleMax")
        val plans2 = session.actions(uri2, caret2)
        // Une expression-body n'a pas de BLOCK : pas d'instruction
        // porteuse — refus par construction.
        assertNull(
            plans2.firstOrNull { it.titre.startsWith("Extraire") && it.titre.contains("fonction") },
            "une expression-body sans bloc ne peut pas porter l'extraction ; rendu : ${plans2.map { it.titre }}",
        )
        session.close()
    }
}

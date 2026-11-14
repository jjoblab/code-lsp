// CodeIDE — CHANTIER D (parité Android Studio — intention de mutabilité,
// 14/11/2026) : au caret sur une déclaration `val`, l'action « Convertir
// en var » se propose (kind refactor.rewrite) ; une `var` ne reçoit PAS
// la conversion inverse — la preuve de non-réécriture (analyse des flux)
// est hors périmètre, le moteur ne ment pas (D3).
package jo.codeide.lsp.kotlin.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le chantier D : la mutabilité val → var en action de code.
 *
 * **Les cas verrouillés** :
 *  - un `val` LOCAL au caret rend le plan « Convertir 'nom' en var »
 *    dont l'édition remplace EXACTEMENT le jeton `val` ;
 *  - un `var` ne rend AUCUN plan de mutabilité (la preuve de
 *    non-réécriture est un chantier d'analyse des flux) ;
 *  - le caret sur l'USAGE d'une variable (pas sa déclaration) ne rend
 *    rien — la déclaration seule est porteuse.
 */
class ChantierDMutabiliteTest {

    /** Une session de documents SANS classpath (les actions vivent de l'arbre seul). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur =
            (jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin.unique)
                as MoteurLangageKotlinNoyau
        return moteur.creerSession() as SessionLangageKotlinDocuments
    }

    @Test
    fun `chantierD---val-au-caret-rend-le-plan-convertir-en-var`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    val compteur = 0\n" +
                "    return compteur + 1\n" +
                "}\n"
        val uri = "file:///cd1.kt"
        session.ouvrir(uri, source)
        // Le caret sur le NOM de la déclaration.
        val plans = session.actions(uri, source.indexOf("compteur"))
        val plan = plans.firstOrNull { it.titre.startsWith("Convertir") }
        assertNotNull(plan, "le plan de conversion devait exister ; rendu : ${plans.map { it.titre }}")
        assertEquals("Convertir 'compteur' en var", plan.titre)
        // L'édition remplace EXACTEMENT le jeton `val` de la déclaration —
        // la preuve sémantique : le texte de la plage REMPLACÉE est `val`.
        val edition = plan.editions.single().editions.single()
        assertEquals("var", edition.texte)
        assertEquals(
            "val",
            source.substring(edition.plage.debut, edition.plage.fin),
            "la plage remplacée doit être le jeton val de la déclaration",
        )
        session.close()
    }

    @Test
    fun `chantierD---var-ne-rend-aucun-plan-de-mutabilite`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    var compteur = 0\n" +
                "    compteur += 1\n" +
                "    return compteur\n" +
                "}\n"
        val uri = "file:///cd2.kt"
        session.ouvrir(uri, source)
        val plans = session.actions(uri, source.indexOf("compteur"))
        assertNull(
            plans.firstOrNull { it.titre.startsWith("Convertir") },
            "une var ne devait PAS recevoir de conversion (preuve de non-réécriture indémontrable) ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }

    @Test
    fun `chantierD---usage-hors-declaration-ne-rend-rien`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    val compteur = 0\n" +
                "    return compteur + 1\n" +
                "}\n"
        val uri = "file:///cd3.kt"
        session.ouvrir(uri, source)
        // Le caret sur l'USAGE de retour (pas la déclaration).
        val usage = source.indexOf("compteur", source.indexOf("return"))
        val plans = session.actions(uri, usage)
        assertTrue(
            plans.none { it.titre.startsWith("Convertir") },
            "un usage hors déclaration ne devait rien rendre ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }
}

// CodeIDE — CHANTIER E (parité Android Studio — Inline Variable,
// 14/11/2026) : au caret sur une `val` locale à initialisateur SANS
// EFFET DE BORD DÉMONTRABLE (littéral ou référence simple), l'action
// « Inline » remplace chaque usage par l'initialisateur et RETIRE la
// déclaration — kind refactor.inline, l'inverse de l'extraction KC-11.
package jo.codeide.lsp.kotlin.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le chantier E : l'inline de variable locale.
 *
 * **Les cas verrouillés** :
 *  - une `val` locale à littéral, utilisée DEUX fois : le plan retire
 *    la ligne de déclaration et remplace les DEUX usages par le
 *    littéral ;
 *  - une `var` ne reçoit RIEN (l'inline changerait la sémantique de
 *    réécriture) ;
 *  - un initialisateur À EFFET DE BORD (appel) refuse le plan — le
 *    moteur ne répète pas un calcul sous silence ;
 *  - une propriété de CLASSE refuse (l'usage peut vivre ailleurs) ;
 *  - une seconde déclaration du même nom refuse (ambiguïté d'ombre).
 */
class ChantierEInlineVariableTest {

    /** Une session de documents SANS classpath (les actions vivent de l'arbre seul). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur =
            (jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin.unique)
                as MoteurLangageKotlinNoyau
        return moteur.creerSession() as SessionLangageKotlinDocuments
    }

    @Test
    fun `chantierE---val-litterale-utilisee-deux-fois-s-inline-entierement`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    val taille = 3\n" +
                "    return taille + taille\n" +
                "}\n"
        val uri = "file:///ce1.kt"
        session.ouvrir(uri, source)
        val plans = session.actions(uri, source.indexOf("taille"))
        val plan = plans.firstOrNull { it.titre.startsWith("Inline") }
        assertNotNull(plan, "le plan d'inline devait exister ; rendu : ${plans.map { it.titre }}")
        assertEquals("Inline 'taille' (2 usages)", plan.titre)
        val editions = plan.editions.single().editions
        // 3 éditions : le retrait de la ligne + les 2 usages.
        assertEquals(3, editions.size, "retrait de la déclaration + 2 remplacements : $editions")
        // Le retrait : la ligne entière de la déclaration.
        val retrait = editions[0]
        assertEquals("", retrait.texte)
        assertEquals(
            "    val taille = 3\n",
            source.substring(retrait.plage.debut, retrait.plage.fin),
            "le retrait emporte la ligne de déclaration complète",
        )
        // Les remplacements : le littéral aux DEUX usages.
        assertEquals("3", editions[1].texte)
        assertEquals("3", editions[2].texte)
        assertEquals("taille", source.substring(editions[1].plage.debut, editions[1].plage.fin))
        session.close()
    }

    @Test
    fun `chantierE---var-ne-s-inline-pas`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    var taille = 3\n" +
                "    taille += 1\n" +
                "    return taille\n" +
                "}\n"
        val uri = "file:///ce2.kt"
        session.ouvrir(uri, source)
        val plans = session.actions(uri, source.indexOf("taille"))
        assertNull(
            plans.firstOrNull { it.titre.startsWith("Inline") },
            "une var ne devait PAS s'inliner ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }

    @Test
    fun `chantierE---initialisateur-a-effet-de-bord-refuse`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    val taille = calculer()\n" +
                "    return taille + taille\n" +
                "}\n" +
                "fun calculer(): Int = 3\n"
        val uri = "file:///ce3.kt"
        session.ouvrir(uri, source)
        val plans = session.actions(uri, source.indexOf("taille"))
        assertNull(
            plans.firstOrNull { it.titre.startsWith("Inline") },
            "un appel ne devait PAS s'inliner (effet de bord possible) ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }

    @Test
    fun `chantierE---propriete-de-classe-refuse`() {
        val session = session()
        val source =
            "package demo\n" +
                "class Panier {\n" +
                "    val taille = 3\n" +
                "    fun total(): Int = taille * 2\n" +
                "}\n"
        val uri = "file:///ce4.kt"
        session.ouvrir(uri, source)
        val plans = session.actions(uri, source.indexOf("taille"))
        assertNull(
            plans.firstOrNull { it.titre.startsWith("Inline") },
            "une propriété de classe ne devait PAS s'inliner (usages inter-fichiers) ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }

    @Test
    fun `chantierE---seconde-declaration-du-meme-nom-refuse`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    val taille = 3\n" +
                "    if (taille > 1) {\n" +
                "        val taille = 5\n" +
                "        return taille\n" +
                "    }\n" +
                "    return taille\n" +
                "}\n"
        val uri = "file:///ce5.kt"
        session.ouvrir(uri, source)
        val plans = session.actions(uri, source.indexOf("taille"))
        assertNull(
            plans.firstOrNull { it.titre.startsWith("Inline") },
            "l'ombre d'une seconde déclaration devait refuser l'inline ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }

    @Test
    fun `chantierE---reference-simple-s-inline-aussi`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(base: Int): Int {\n" +
                "    val copie = base\n" +
                "    return copie + copie\n" +
                "}\n"
        val uri = "file:///ce6.kt"
        session.ouvrir(uri, source)
        val plans = session.actions(uri, source.indexOf("copie"))
        val plan = plans.firstOrNull { it.titre.startsWith("Inline") }
        assertNotNull(plan, "une référence simple devait s'inliner ; rendu : ${plans.map { it.titre }}")
        // Le remplacement : le NOM de la référence, pas sa valeur.
        val remplacements = plan.editions.single().editions.filter { it.texte.isNotEmpty() }
        assertTrue(
            remplacements.all { it.texte == "base" },
            "les usages deviennent la référence base : $remplacements",
        )
        session.close()
    }
}

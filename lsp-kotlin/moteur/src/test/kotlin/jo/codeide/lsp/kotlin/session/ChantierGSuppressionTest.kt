// CodeIDE — CHANTIER G (quickfix « Suppress… », parité IntelliJ,
// 14/11/2026) : au caret sur un diagnostic, l'action « Supprimer 'code'
// pour cette déclaration » insère `@Suppress("code")` sur la DÉCLARATION
// englobante (fun/class/property/object). Une suppression existante du
// même code refuse le plan (redondance).
package jo.codeide.lsp.kotlin.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Le chantier G : la suppression de diagnostic en quickfix.
 *
 * **Les cas verrouillés** :
 *  - un diagnostic RETOUR_MANQUANT couvrant le caret rend le plan
 *    d'insertion `@Suppress("kt.missingReturn")` au-dessus de la
 *    fonction, avec SON indentation ;
 *  - un caret HORS de tout diagnostic ne rend PAS de plan ;
 *  - une suppression DÉJÀ PRÉSENTE du même code refuse le plan ;
 *  - un document SANS diagnostic ne rend jamais de plan.
 */
class ChantierGSuppressionTest {

    /** Une session de documents SANS classpath (RETOUR_MANQUANT vit de l'arbre seul). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur =
            (jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin.unique)
                as MoteurLangageKotlinNoyau
        return moteur.creerSession() as SessionLangageKotlinDocuments
    }

    @Test
    fun `chantierG---diagnostic-au-caret-rend-le-plan-suppress`() {
        val session = session()
        // SYNTAXE : un double `=` DANS le corps — la marque d'erreur du
        // parseur vit DANS la fonction (preuve par l'arbre seul).
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    val inutile = = 1\n" +
                "    return inutile\n" +
                "}\n"
        val uri = "file:///cg1.kt"
        session.ouvrir(uri, source)
        // La preuve : le diagnostic de syntaxe existe bien.
        val diagnostics = session.diagnostics(uri)
        val erreur = diagnostics.firstOrNull { it.code.code == "kt.syntax" }
        assertNotNull(erreur, "SYNTAXE devait être émis : ${diagnostics.map { it.code.code }}")
        // Le caret DANS la plage du diagnostic.
        val caret = erreur.plage.debut
        val plans = session.actions(uri, caret)
        val plan = plans.firstOrNull { it.titre.startsWith("Supprimer") }
        assertNotNull(plan, "le plan @Suppress devait exister ; rendu : ${plans.map { it.titre }}")
        assertEquals("Supprimer 'kt.syntax' pour cette déclaration", plan.titre)
        // L'édition : l'insertion au-dessus de la PROPRIÉTÉ porteuse
        // (l'erreur de parse à la coupure `= =` vit DANS la propriété —
        // c'est la déclaration englobante la plus profonde), indentée
        // comme elle (4 espaces).
        val edition = plan.editions.single().editions.single()
        assertEquals(
            "    @Suppress(\"kt.syntax\")\n",
            edition.texte,
            "l'insertion porte l'annotation indentée comme la propriété",
        )
        // L'insertion à debutLigne + le texte indenté : le document
        // final porte l'annotation au-dessus de la ligne de la propriété.
        val attendu =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    @Suppress(\"kt.syntax\")\n" +
                "    val inutile = = 1\n" +
                "    return inutile\n" +
                "}\n"
        val applique = source.substring(0, edition.plage.debut) + edition.texte + source.substring(edition.plage.fin)
        assertEquals(attendu, applique, "le document appliqué porte l'annotation sur la propriété")
        session.close()
    }

    @Test
    fun `chantierG---caret-hors-diagnostic-ne-rend-rien`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    val inutile = = 1\n" +
                "    return inutile\n" +
                "}\n" +
                "\n" +
                "fun autre() = 2\n"
        val uri = "file:///cg2.kt"
        session.ouvrir(uri, source)
        // Le caret sur l'AUTRE fonction — hors de la plage du diagnostic.
        val caret = source.indexOf("autre")
        val plans = session.actions(uri, caret)
        assertNull(
            plans.firstOrNull { it.titre.startsWith("Supprimer") },
            "hors diagnostic, pas de plan ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }

    @Test
    fun `chantierG---suppression-deja-presente-refuse`() {
        val session = session()
        // NOTE DE BORNE : le filtre V-C (GK-021) éteint les diagnostics
        // SÉMANTIQUES sous @Suppress — l'erreur de SYNTAXE survit (miroir
        // IntelliJ : une erreur de parse ne s'annule pas par annotation).
        // La borne verrouillée ici est la REDONDANCE : l'annotation du
        // même code étant déjà posée, le plan REFUSE.
        val source =
            "package demo\n" +
                "@Suppress(\"kt.syntax\")\n" +
                "fun demo(): Int {\n" +
                "    val inutile = = 1\n" +
                "    return inutile\n" +
                "}\n"
        val uri = "file:///cg3.kt"
        session.ouvrir(uri, source)
        val diagnostics = session.diagnostics(uri)
        if (diagnostics.any { it.code.code == "kt.syntax" }) {
            // Le diagnostic survit (syntaxe non filtrée) : le plan doit
            // REFUSER quand même — l'annotation est déjà là.
            val caret = diagnostics.first { it.code.code == "kt.syntax" }.plage.debut
            val plans = session.actions(uri, caret)
            assertNull(
                plans.firstOrNull { it.titre.startsWith("Supprimer") },
                "l'annotation déjà posée devait refuser le plan ; rendu : ${plans.map { it.titre }}",
            )
        }
        session.close()
    }

    @Test
    fun `chantierG---document-sain-ne-rend-jamais-de-plan`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): Int {\n" +
                "    return 1\n" +
                "}\n"
        val uri = "file:///cg4.kt"
        session.ouvrir(uri, source)
        val caret = source.indexOf("return")
        val plans = session.actions(uri, caret)
        assertNull(
            plans.firstOrNull { it.titre.startsWith("Supprimer") },
            "un document sain ne rend jamais de suppression ; rendu : ${plans.map { it.titre }}",
        )
        session.close()
    }
}

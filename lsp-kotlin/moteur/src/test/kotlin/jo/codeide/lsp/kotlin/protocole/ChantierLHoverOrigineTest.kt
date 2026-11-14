// CodeIDE — CHANTIER L (parité Android Studio — « lien vers source » du
// hover, 14/11/2026) : le survol d'un symbole déclaré dans un AUTRE
// fichier porte la ligne « Défini dans `chemin` » — la signature et la
// KDoc inchangées ; un symbole du document COURANT n'a pas d'origine
// (le saut reste textDocument/definition, la ligne INFORME).
package jo.codeide.lsp.kotlin.protocole

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem

/**
 * Le chantier L : l'origine de la déclaration dans le hover.
 *
 * **Les cas verrouillés** :
 *  - le survol d'un symbole déclaré dans un AUTRE document ouvert rend
 *    le hover markdown avec « **Défini dans** `Autre.kt` » en tête ;
 *  - le survol d'un symbole du document COURANT ne porte PAS la ligne
 *    (pas d'origine — la déclaration est SOUS le caret) ;
 *  - la signature reste le bloc ```kotlin (contrat chantier B intact).
 */
class ChantierLHoverOrigineTest {

    /** Un serveur avec la session PAR DÉFAUT (deux documents ouverts). */
    private fun monte(): ServeurLangageKotlin {
        val serveur = ServeurLangageKotlin()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        serveur.initialize(InitializeParams()).get(15, TimeUnit.SECONDS)
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())
        return serveur
    }

    @Test
    fun `chantierL---survol-d-un-symbole-d-autre-document-rend-l-origine`() {
        val serveur = monte()
        val uriAutre = "file:///src/Autre.kt"
        serveur.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(
                    uriAutre,
                    "kotlin",
                    1,
                    "package demo\n" +
                        "\n" +
                        "/** Calcule le total.\n" +
                        " */\n" +
                        "fun total(): Int = 42\n",
                ),
            ),
        )
        val uriCourant = "file:///src/Ecran.kt"
        val source =
            "package demo\n" +
                "\n" +
                "fun ecran(): Int {\n" +
                "    return total()\n" +
                "}\n"
        serveur.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uriCourant, "kotlin", 2, source)))
        // Le caret sur l'USAGE de `total` (déclaré dans Autre.kt).
        val ligne = 3
        val colonne = source.split("\n")[ligne].indexOf("total")
        val hover = serveur.hover(
            HoverParams(TextDocumentIdentifier(uriCourant), org.eclipse.lsp4j.Position(ligne, colonne)),
        ).get(15, TimeUnit.SECONDS)
        assertNotNull(hover, "le survol devait rendre total() ; reçu : $hover")
        val contenu = hover.contents.right
        assertNotNull(contenu, "le hover est en MarkupContent")
        assertTrue(
            contenu.value.startsWith("**Défini dans** `Autre.kt`"),
            "la ligne d'origine en tête ; reçu : <<${contenu.value}>>",
        )
        assertTrue(
            "fun total(): Int" in contenu.value,
            "la signature du chantier B reste dans le bloc kotlin",
        )
    }

    @Test
    fun `chantierL---symbole-du-document-courant-sans-origine`() {
        val serveur = monte()
        val uri = "file:///local.kt"
        val source =
            "package demo\n" +
                "\n" +
                "/** Doc locale.\n" +
                " */\n" +
                "fun calcul(): Int = 1\n" +
                "\n" +
                "fun ecran(): Int {\n" +
                "    return calcul()\n" +
                "}\n"
        serveur.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "kotlin", 1, source)))
        // Le caret sur l'USAGE de `calcul` (déclaré DANS ce document).
        val ligne = 7
        val colonne = source.split("\n")[ligne].indexOf("calcul")
        val hover = serveur.hover(
            HoverParams(TextDocumentIdentifier(uri), org.eclipse.lsp4j.Position(ligne, colonne)),
        ).get(15, TimeUnit.SECONDS)
        assertNotNull(hover, "le survol devait rendre calcul()")
        val contenu = hover.contents.right
        assertNotNull(contenu, "le hover est en MarkupContent")
        assertTrue(
            "Défini dans" !in contenu.value,
            "un symbole du document courant n'a PAS de ligne d'origine ; reçu : <<${contenu.value}>>",
        )
        assertTrue(
            "Doc locale" in contenu.value,
            "la KDoc du chantier B reste rendue",
        )
    }
}

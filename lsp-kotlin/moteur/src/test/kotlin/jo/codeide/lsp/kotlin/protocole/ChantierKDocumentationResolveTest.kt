// CodeIDE — CHANTIER K (parité Android Studio — documentation différée à
// la complétion, 14/11/2026) : l'item d'une déclaration TOP-LEVEL du
// document porte son nom en `data` ; completionItem/resolve rend la KDoc
// en MarkupContent markdown — la charge ne porte QUE les items consultés.
package jo.codeide.lsp.kotlin.protocole

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextDocumentPositionParams

/**
 * Le chantier K : la KDoc au completionItem/resolve.
 *
 * **Les cas verrouillés** :
 *  - la complétion d'un nom déclaré avec KDoc porte `data.symbole` ;
 *  - le resolve rend la KDoc en markdown ;
 *  - une déclaration SANS KDoc ne porte PAS `data.symbole` (pas de doc
 *    à résoudre) ;
 *  - la capacité annonce completionProvider.resolveProvider = true.
 */
class ChantierKDocumentationResolveTest {

    /** Un serveur avec la session PAR DÉFAUT (les déclarations vivent dans le document ouvert). */
    private fun monte(): ServeurLangageKotlin {
        val serveur = ServeurLangageKotlin()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        serveur.initialize(InitializeParams()).get(15, TimeUnit.SECONDS)
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())
        return serveur
    }

    @Test
    fun `chantierK---le-resolve-rend-la-kdoc-de-la-declaration`() {
        val serveur = monte()
        val uri = "file:///ck1.kt"
        val source =
            "package demo\n" +
                "\n" +
                "/** Additionne deux entiers.\n" +
                " * @param a le premier\n" +
                " */\n" +
                "fun additionner(a: Int, b: Int): Int = a + b\n" +
                "\n" +
                "fun demo(): Int {\n" +
                "    return addi\n" +
                "}\n"
        serveur.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "kotlin", 1, source)))
        val position = org.eclipse.lsp4j.Position(8, 15)
        val reponse = serveur.completion(
            CompletionParams(TextDocumentIdentifier(uri), position),
        ).get(15, TimeUnit.SECONDS).right
        val item = reponse.items.firstOrNull { it.label == "additionner" }
        assertNotNull(item, "la complétion devait proposer additionner ; rendu : ${reponse.items.map { it.label }}")
        assertNotNull(item.data, "l'item déclaré avec KDoc porte data (symbole)")
        // Le RESOLVE : la KDoc rendue en markdown.
        item.data = mapOf("symbole" to "additionner", "uri" to uri)
        val resolu = serveur.resolveCompletionItem(item).get(15, TimeUnit.SECONDS)
        val doc = resolu.documentation?.right
        assertNotNull(doc, "le resolve devait rendre la KDoc")
        assertEquals("markdown", doc.kind)
        assertTrue(
            doc.value.contains("Additionne deux entiers"),
            "la KDoc rendue porte le texte ; reçu : <<${doc.value}>>",
        )
        serveur.textDocumentService.didClose(
            org.eclipse.lsp4j.DidCloseTextDocumentParams(TextDocumentIdentifier(uri)),
        )
    }

    @Test
    fun `chantierK---declaration-sans-kdoc-ne-porte-pas-symbole`() {
        val serveur = monte()
        val uri = "file:///ck2.kt"
        val source =
            "package demo\n" +
                "\n" +
                "fun calculer(x: Int): Int = x * 2\n" +
                "\n" +
                "fun demo(): Int {\n" +
                "    return calcu\n" +
                "}\n"
        serveur.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "kotlin", 1, source)))
        val position = org.eclipse.lsp4j.Position(5, 15)
        val reponse = serveur.completion(
            CompletionParams(TextDocumentIdentifier(uri), position),
        ).get(15, TimeUnit.SECONDS).right
        val item = reponse.items.firstOrNull { it.label == "calculer" }
        assertNotNull(item, "la complétion devait proposer calculer ; rendu : ${reponse.items.map { it.label }}")
        // SANS KDoc : data.symbole ne doit PAS exister (rien à résoudre).
        val donnees = item.data as? Map<*, *>
        assertNull(
            donnees?.get("symbole"),
            "une déclaration sans KDoc ne porte pas data.symbole ; data=$donnees",
        )
        serveur.textDocumentService.didClose(
            org.eclipse.lsp4j.DidCloseTextDocumentParams(TextDocumentIdentifier(uri)),
        )
    }

    @Test
    fun `chantierK---la-capacite-annonce-le-resolve-de-completion`() {
        val serveur = monte()
        val init = ServeurLangageKotlin().initialize(InitializeParams()).get(15, TimeUnit.SECONDS)
        // (le serveur du montage a déjà initialisé — un NEUF porte la capacité)
        assertTrue(
            init.capabilities.completionProvider.resolveProvider == true,
            "completionProvider.resolveProvider devait être annoncé (contrat LSP du resolve)",
        )
    }
}

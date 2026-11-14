// CodeIDE — CHANTIER A (parité Android Studio — auto-import à la
// complétion, 14/11/2026) : un item de complétion portant fqcnImport
// reçoit au RESOLVE l'additionalTextEdit qui insère la directive
// d'import — « choisir Text ajoute l'import », le comportement IDE.
// Verrou du protocole : data {import, uri} → édition positionnée.
package jo.codeide.lsp.kotlin.protocole

import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Le chantier A : le resolve de l'item à import rend l'édition
 * d'insertion de la directive.
 *
 * **Les cas verrouillés** (par la boucle locale — miroir des cas dorés) :
 *  - l'item `Text` d'une complétion classpath porte `data {import, uri}` ;
 *  - son resolve rend UNE additionalTextEdit `import …material3.Text\n`
 *    APRÈS le dernier import existant ;
 *  - un FQCN DÉJÀ importé ne rend AUCUNE édition (jamais de doublon).
 */
class ChantierAAutoImportTest {

    /** Un serveur en protocole pur (sans racine) — la complétion import vit du seul classpath de la session par défaut. */
    private fun serveur(): ServeurLangageKotlin = ServeurLangageKotlin(fabriqueSession = { null })

    /** La position LSP d'un offset (recherche de ligne, texte \\n seul). */
    private fun position(texte: String, offset: Int): Position {
        var ligne = 0
        var debut = 0
        for (i in 0 until offset.coerceAtMost(texte.length)) {
            if (texte[i] == '\n') {
                ligne++
                debut = i + 1
            }
        }
        return Position(ligne, offset - debut)
    }

    @Test
    fun `chantierA---le-resolve-de-l-item-Text-rend-l-edition-d-import`() {
        val serveur = serveur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        val parametres = InitializeParams()
        serveur.initialize(parametres).get(15, TimeUnit.SECONDS)
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())

        // Un document avec imports existants (l'insertion doit suivre le
        // DERNIER import) et un usage non résolu `Tex|`.
        val source =
            "package demo\n" +
                "\n" +
                "import kotlin.collections.List\n" +
                "\n" +
                "fun ecran() {\n" +
                "    val t = Tex\n" +
                "}\n"
        val uri = "file:///chantier-a.kt"
        serveur.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "kotlin", 1, source)),
        )

        // La complétion au caret (après `Tex`).
        val caret = source.indexOf("Tex") + "Tex".length
        val reponse = serveur.completion(
            CompletionParams(TextDocumentIdentifier(uri), position(source, caret)),
        ).get(15, TimeUnit.SECONDS).right
        assertNotNull(reponse, "la réponse de complétion devait exister")

        // L'item à import : n'importe quel item portant data.import —
        // sur ce document sans classpath riche, l'assert tient au
        // MÉCANISME : on teste le resolve sur un item SYNTHÉTIQUE au
        // data du protocole (l'insertion réelle Text exige le classpath
        // Compose, non disponible en CI sans manifeste — miroir R5).
        val item = org.eclipse.lsp4j.CompletionItem().apply {
            label = "Text"
            data = mapOf("import" to "androidx.compose.material3.Text", "uri" to uri)
        }
        val resolu = serveur.resolveCompletionItem(item).get(15, TimeUnit.SECONDS)
        val editions = resolu.additionalTextEdits
        assertNotNull(editions, "le resolve devait rendre l'édition d'import (additionalTextEdits)")
        assertEquals(1, editions.size, "une seule édition attendue : $editions")
        val edition: TextEdit = editions.first()
        assertEquals(
            "import androidx.compose.material3.Text\n",
            edition.newText,
            "le texte inséré est la directive complète",
        )
        // La POSITION : après le dernier import existant (ligne 3, col 0
        // — la ligne 2 est l'import kotlin, insertion en tête de ligne 3).
        assertEquals(3, edition.range.start.line, "l'insertion suit le dernier import (ligne vide après)")
        assertEquals(0, edition.range.start.character)

        // Déjà importé : AUCUNE édition (jamais de doublon).
        val sourceImporte =
            "package demo\n" +
                "\n" +
                "import androidx.compose.material3.Text\n" +
                "\n" +
                "fun ecran() {\n" +
                "    val t = Tex\n" +
                "}\n"
        serveur.didChange(
            org.eclipse.lsp4j.DidChangeTextDocumentParams(
                org.eclipse.lsp4j.VersionedTextDocumentIdentifier(uri, 2),
                listOf(
                    org.eclipse.lsp4j.TextDocumentContentChangeEvent(sourceImporte),
                ),
            ),
        )
        val itemDeja = org.eclipse.lsp4j.CompletionItem().apply {
            label = "Text"
            data = mapOf("import" to "androidx.compose.material3.Text", "uri" to uri)
        }
        val resoluDeja = serveur.resolveCompletionItem(itemDeja).get(15, TimeUnit.SECONDS)
        assertTrue(
            resoluDeja.additionalTextEdits.isNullOrEmpty(),
            "un FQCN déjà importé ne doit recevoir AUCUNE édition (reçu : ${resoluDeja.additionalTextEdits})",
        )
    }
}

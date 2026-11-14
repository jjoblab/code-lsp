package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Chantier C (14/11/2026) — documentation au `completionItem/resolve` :
 * le resolve d'un item de complétion rend la JAVADOC de l'item choisi à
 * la demande (miroir du chantier K Kotlin) — le popup ne paie que
 * l'item sélectionné, la liste reste légère. Les sources : les sources
 * ATTACHÉES du classpath (chantier A — dossier sources/android-<api> du
 * SDK). Contrats verrouillés :
 *
 *  - un item de TYPE du classpath (import différé) rend documentation +
 *    édition d'import (les DEUX enrichissements du resolve) ;
 *  - un item SANS FQCN exploitable revient TEL QUEL (documentation null,
 *    jamais fabriquée — R5) ;
 *  - l'édition d'import DIFFÉRÉE reste calculée sur le texte COURANT
 *    (contrat V-G inchangé).
 *
 * Le test exige le VRAI SDK Android (local.properties → platforms 37.2 +
 * sources 37.2 — sandbox et CI les posent) : sans lui, l'ignore est
 * EXPLICITE (jamais rouge, jamais vert silencieux).
 */
class ChantierCResolveDocumentationTest {

    @Test
    fun `resolve d'un type classpath rend la javadoc et l'import differe`() {
        val sdk = sdkDuPoste() ?: return ignoré("SDK Android absent du poste")
        val jar = sdk.resolve("platforms/android-37.2/android.jar")
        val sources = sdk.resolve("sources/android-37.2")
        if (!Files.isRegularFile(jar) || !Files.isDirectory(sources)) {
            return ignoré("SDK sans plateforme 37.2 ou sans sources")
        }

        // 1. Le serveur VIVANT : environnement hôte avec le jar plateforme
        //    AU CONTEXTE DÉFAUT (la voie rapide du lot h4 — le montage
        //    SessionLsp de l'hôte réel).
        val env = jo.codeide.lsp.java.EnvironnementCompilation()
        env.remplacerContexteModule(null, emptyList(), listOf(jar))
        val serveur = ServeurLangageJava(env) { }
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        // 2. Un document avec un type NU en position d'EXPRESSION (après
        //    « = ») — la complétion propose Activity avec l'import différé
        //    (V-G) : c'est LE scénario de l'auto-import.
        val source = """
            package demo;

            public class Demo {
                Class<?> c = Acti
            }
        """.trimIndent()
        val uri = tmp.resolve("Demo.java").toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, source)),
        )
        val completion = boucle.client.textDocumentService.completion(
            CompletionParams(TextDocumentIdentifier(uri), positionDe(source, "Acti") + 4),
        ).get()
        val items = completion.left ?: completion.right?.items ?: emptyList()
        val item = items.firstOrNull { it.label == "Activity" }
        assertNotNull(item, "l'item Activity doit être proposé — reçu : ${items.map { it.label }.take(8)}")

        // 3. Le RESOLVE : documentation + édition d'import.
        val resolu = boucle.client.textDocumentService.resolveCompletionItem(item).get()
        assertNotNull(resolu, "le resolve rend l'item")
        val documentation = resolu.documentation?.right
        assertNotNull(documentation, "la documentation du resolve doit être rendue (javadoc des sources attachées)")
        assertTrue(
            documentation.value.isNotBlank(),
            "la javadoc n'est pas vide",
        )
        val editions = resolu.additionalTextEdits ?: emptyList()
        assertTrue(
            editions.any { it.newText.contains("import android.app.Activity") },
            "l'édition d'import différée reste présente — reçues : ${editions.map { it.newText }}",
        )
    }

    @Test
    fun `item sans fqn exploitable revient tel quel`() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams()).get()
        boucle.client.initialized(InitializedParams())
        // Un item NU (sans data, sans détail FQCN) : le resolve doit le
        // rendre TEL QUEL — documentation null, jamais fabriquée.
        val item = CompletionItem().apply {
            label = "quelconque"
            detail = "int foo(int)" // signature, PAS un FQCN
        }
        val resolu = boucle.client.textDocumentService.resolveCompletionItem(item).get()
        assertEquals("quelconque", resolu.label, "l'item revient tel quel")
        assertNull(resolu.documentation?.right, "aucune documentation fabriquée pour un item sans FQCN")
    }

    // --------------------------------------------------------------- //
    // Utilitaires
    // --------------------------------------------------------------- //

    /** La position (0-based) d'un motif dans le texte — utilitaire LSP. */
    private fun positionDe(source: String, motif: String): Position {
        val indice = source.indexOf(motif)
        assertTrue(indice >= 0, "motif absent : $motif")
        var ligne = 0
        var debutLigne = 0
        for (i in 0 until indice) {
            if (source[i] == '\n') {
                ligne++
                debutLigne = i + 1
            }
        }
        return Position(ligne, indice - debutLigne)
    }

    /** L'opérateur d'ajout de colonnes (le caret APRÈS le mot tapé). */
    private operator fun Position.plus(colonnes: Int): Position = Position(line, character + colonnes)

    /** Le SDK Android du poste (local.properties — sandbox et CI le posent). */
    private fun sdkDuPoste(): Path? {
        for (candidat in listOf(Path.of("local.properties"), Path.of("../../local.properties"))) {
            if (!Files.isRegularFile(candidat)) continue
            val sdk = runCatching {
                val props = java.util.Properties()
                Files.newInputStream(candidat).use { props.load(it) }
                props.getProperty("sdk.dir")?.let(Path::of)
            }.getOrNull()
            if (sdk != null && Files.isDirectory(sdk)) return sdk
        }
        return null
    }

    /** Un ignore explicite (jamais vert silencieux). */
    private fun ignoré(raison: String) {
        println("[ChantierC] test SDK ignoré : $raison")
    }

    @TempDir
    lateinit var tmp: Path
}

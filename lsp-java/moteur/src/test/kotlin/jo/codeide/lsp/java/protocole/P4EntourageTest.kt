package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P4 (15/11/2026) — l'ENTOURAGE (surround with, Ctrl+Alt+T d'IntelliJ) :
 * le caret dans une instruction TERMINÉE (`… ;`) propose les quatre
 * formes de contrôle — if / else, try / catch, for, while — et l'édition
 * appliquée rend un code INDENTÉ et COMPILABLE (l'instruction devient le
 * corps, en-tête avant, fermeture après, un cran d'indentation).
 *
 * Le sous-ensemble SENSÉ : une SEULE instruction (la sélection
 * multi-instructions exige un analyseur de blocs complet — hors cadre,
 * l'honnêteté d'abord).
 */
class P4EntourageTest {

    @Test
    fun `entourer de try catch - intention servie et code compilable`() {
        val dossier = tmp.resolve("src").also(Files::createDirectories)
        val source = """
            package demo;

            public class Coffre {
                int ouvrir() {
                    int code = 1234;
                    return code;
                }
            }
        """.trimIndent()
        val fichier = dossier.resolve("Coffre.java")
        Files.writeString(fichier, source)
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = dossier.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())
        val uri = fichier.toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, source)),
        )

        // Le caret DANS l'instruction « int code = 1234; ».
        val idx = source.indexOf("1234")
        val ligne = source.substring(0, idx).count { it == '\n' }
        val colonne = idx - source.lastIndexOf('\n', idx) - 1
        val actions = boucle.client.textDocumentService.codeAction(
            CodeActionParams(
                TextDocumentIdentifier(uri),
                Range(Position(ligne, colonne), Position(ligne, colonne + 1)),
                CodeActionContext(),
            ),
        ).get().orEmpty().mapNotNull { it.right }
        val titres = actions.map { it.title }
        assertTrue("Entourer de try / catch" in titres, "l'entourage try/catch est proposé — reçus : $titres")
        assertTrue("Entourer de if / else" in titres, "l'entourage if/else est proposé")
        assertTrue("Entourer de for" in titres, "l'entourage for est proposé")
        assertTrue("Entourer de while" in titres, "l'entourage while est proposé")

        // L'édition du try/catch : appliquée, le code est INDENTÉ et
        // l'instruction devient le corps.
        val action = actions.first { it.title == "Entourer de try / catch" }
        val editions = action.edit?.changes?.get(uri) ?: emptyList()
        assertTrue(editions.isNotEmpty(), "l'action EAGER porte ses éditions")
        var apres = source
        for (edition in editions.sortedByDescending { it.range.start.line * 100000 + it.range.start.character }) {
            val nouveau = edition.newText
            val plage = edition.range
            val lignes = apres.split("\n")
            val debut = lignes.take(plage.start.line).joinToString("\n").length +
                (if (plage.start.line > 0) 1 else 0) + plage.start.character
            val fin = lignes.take(plage.end.line).joinToString("\n").length +
                (if (plage.end.line > 0) 1 else 0) + plage.end.character
            apres = apres.substring(0, debut) + nouveau + apres.substring(fin)
        }
        assertTrue(
            "try {\n            int code = 1234;\n        } catch (Exception e) {" in apres,
            "l'instruction devient le corps INDENTÉ du try — reçu :\n$apres",
        )
        // La ligne du return reste INTACTE (seule l'instruction visée).
        assertEquals(1, Regex("return code;").findAll(apres).count(), "le return n'est pas dupliqué ni touché")
    }

    @Test
    fun `caret hors instruction - aucune intention d entourage`() {
        val dossier = tmp.resolve("src").also(Files::createDirectories)
        val source = """
            package demo;

            public class Vide {
                void go() {
                }
            }
        """.trimIndent()
        val fichier = dossier.resolve("Vide.java")
        Files.writeString(fichier, source)
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = dossier.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())
        val uri = fichier.toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, source)),
        )
        // Le caret sur le NOM du type (aucune instruction autour).
        val idx = source.indexOf("Vide")
        val ligne = source.substring(0, idx).count { it == '\n' }
        val colonne = idx - source.lastIndexOf('\n', idx) - 1
        val actions = boucle.client.textDocumentService.codeAction(
            CodeActionParams(
                TextDocumentIdentifier(uri),
                Range(Position(ligne, colonne), Position(ligne, colonne + 1)),
                CodeActionContext(),
            ),
        ).get().orEmpty().mapNotNull { it.right }
        // Le nom du type porte des GÉNÉRATEURS mais PAS d'entourage — et
        // surtout PAS de crash : la méthode vide n'a pas d'instruction au
        // caret. (Le nom peut porter equalsHashcode/toString : on ne
        // vérifie que l'ABSENCE d'entourage.)
        assertTrue(
            actions.none { it.title.startsWith("Entourer de") },
            "aucune intention d'entourage hors instruction — reçus : ${actions.map { it.title }}",
        )
    }

    @TempDir
    lateinit var tmp: Path
}

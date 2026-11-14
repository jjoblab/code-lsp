package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P6 (15/11/2026) — le RENOMMAGE DE PARAMÈTRE, le troisième geste du
 * Change Signature (supprimer et déplacer sont livrés) : le paramètre
 * est déjà collecté comme LOCALE par le moteur de renommage, mais
 * AUCUN test ne verrouille le contrat — un renommage de paramètre qui
 * fuiterait hors de sa méthode (un appelant touché, une homonymie
 * d'une AUTRE méthode renommée) passerait inaperçu.
 *
 * Contrats verrouillés :
 *  - prepareRename résout le paramètre (plage du nom) ;
 *  - le renommage touche la DÉCLARATION et ses LECTURES dans la
 *    méthode, RIEN d'autre (pas l'appelant, pas l'homonyme d'une
 *    autre méthode — `largeur` de aire() reste intact quand on
 *    renomme `largeur` de perimetre()) ;
 *  - les éditions sont dans le FICHIER de la méthode (file-local).
 */
class P6RenommageParametreTest {

    @Test
    fun `renommage d un parametre - declaration et lectures, rien d autre`() {
        val dossier = tmp.resolve("src").also(Files::createDirectories)
        val fichier = dossier.resolve("Calcul.java")
        val source = """
            package demo;

            public class Calcul {
                int perimetre(int largeur, int hauteur) {
                    return 2 * (largeur + hauteur);
                }
                int aire(int largeur) {
                    return largeur * largeur;
                }
            }
        """.trimIndent()
        Files.writeString(fichier, source)
        // L'APPELANT : un AUTRE fichier — il ne doit PAS bouger.
        val appelant = dossier.resolve("Main.java")
        Files.writeString(
            appelant,
            """
            package demo;

            public class Main {
                int go() {
                    return new Calcul().perimetre(3, 4) + new Calcul().aire(5);
                }
            }
            """.trimIndent(),
        )
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

        // Le caret sur « largeur » de perimetre (PREMIÈRE occurrence :
        // la déclaration — « int largeur » avant « return 2 * (largeur »).
        val offset = source.indexOf("largeur")
        val ligne = source.substring(0, offset).count { it == '\n' }
        val colonne = offset - source.lastIndexOf('\n', offset) - 1

        // 1. prepareRename résout le paramètre.
        val plage = boucle.client.textDocumentService.prepareRename(
            PrepareRenameParams(TextDocumentIdentifier(uri), org.eclipse.lsp4j.Position(ligne, colonne)),
        ).get()
        assertNotNull(plage, "prepareRename résout le paramètre")

        // 2. Le renommage : largeur → cote.
        val edit = boucle.client.textDocumentService.rename(
            RenameParams(
                VersionedTextDocumentIdentifier(uri, 1),
                org.eclipse.lsp4j.Position(ligne, colonne),
                "cote",
            ),
        ).get()
        assertNotNull(edit, "le renommage rend un WorkspaceEdit")

        // Les éditions ne touchent QUE le fichier de la méthode.
        val urisTouches = (edit.documentChanges ?: emptyList()).mapNotNull { it.left?.textDocument?.uri } +
            (edit.changes ?: emptyMap()).keys
        assertEquals(setOf(uri), urisTouches.toSet(), "file-local : seul Calcul.java bouge — reçus : $urisTouches")

        // Le texte renommé : déclaration + lecture de perimetre touchées,
        // l'homonyme de aire() INTACT, l'appelant INTACT.
        val texteApres = appliquer(source, edit, uri)
        assertTrue("int cote, int hauteur" in texteApres, "la déclaration est renommée")
        assertTrue("2 * (cote + hauteur)" in texteApres, "la lecture dans perimetre est renommée")
        assertTrue("int aire(int largeur)" in texteApres, "l'homonyme de aire() reste INTACT")
        assertTrue("largeur * largeur" in texteApres, "les lectures de aire() restent INTACTES")
        // L'appelant Main.java reste INTACT octet pour octet.
        val texteMain = Files.readString(appelant)
        assertTrue(
            "perimetre(3, 4)" in texteMain && "aire(5)" in texteMain && "cote" !in texteMain,
            "l'appelant Main.java n'est PAS retouché — reçu : $texteMain",
        )
    }

    /** Applique les éditions du WorkspaceEdit (documentChanges d'abord). */
    private fun appliquer(texte: String, edit: WorkspaceEdit, uri: String): String {
        val editions: List<Pair<String, org.eclipse.lsp4j.Range>> =
            edit.documentChanges
                ?.flatMap { c ->
                    c.left?.takeIf { it.textDocument.uri == uri }?.edits?.map { it.newText to it.range } ?: emptyList()
                }
                ?: edit.changes?.get(uri)?.map { it.newText to it.range }
                ?: emptyList()
        var resultat = texte
        // De la fin au début : les offsets ne bougent pas.
        for ((nouveau, plage) in editions.sortedByDescending { it.second.start.line * 100000 + it.second.start.character }) {
            val lignes = resultat.split("\n")
            val debut = lignes.dropLast(lignes.size - plage.start.line).joinToString("\n").length +
                (if (plage.start.line > 0) 1 else 0) + plage.start.character
            val fin = lignes.dropLast(lignes.size - plage.end.line).joinToString("\n").length +
                (if (plage.end.line > 0) 1 else 0) + plage.end.character
            resultat = resultat.substring(0, debut) + nouveau + resultat.substring(fin)
        }
        return resultat
    }

    @TempDir
    lateinit var tmp: Path
}

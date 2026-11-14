package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextEdit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P5 (15/11/2026) — les GÉNÉRATEURS IJ (constructor/equals/hashCode/
 * toString) par la VOIE PROTOCOLE COMPLÈTE : codeAction les propose
 * (kinds source.generate.*) quand le caret porte le nom du type, la
 * COMMANDE DIFFÉRÉE les résout (les éditions arrivent par executeCommand
 * — ADR 0027 (f)), et la nouvelle GARDE constructeur (un type qui a
 * déjà un constructeur ne voit plus l'action — la générer produirait un
 * doublon) tient.
 *
 * Les générateurs existaient (IntentionsEtGenerationTest les verrouille
 * au niveau MOTEUR) — ce test verrouille le CHÂSSIS complet : ce que le
 * CLIENT voit, ce qu'il reçoit, et ce qu'il ne voit PLUS.
 */
class P5GenerateursProtocoleTest {

    @Test
    fun `codeAction propose et executeCommand resout les generateurs`() {
        val dossier = tmp.resolve("src").also(Files::createDirectories)
        val fichier = dossier.resolve("Point.java")
        val source = """
            package demo;

            public class Point {
                int x;
                int y;
            }
        """.trimIndent()
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

        // Le caret sur le NOM du type (ligne « public class Point »).
        val idx = source.indexOf("Point")
        val ligne = source.substring(0, idx).count { it == '\n' }
        val colonne = idx - source.lastIndexOf('\n', idx) - 1
        val plage = Range(org.eclipse.lsp4j.Position(ligne, colonne), org.eclipse.lsp4j.Position(ligne, colonne + 1))

        // 1. codeAction PROPOSE les trois familles source.generate.*.
        val actions = boucle.client.textDocumentService.codeAction(
            CodeActionParams(TextDocumentIdentifier(uri), plage, org.eclipse.lsp4j.CodeActionContext()),
        ).get().orEmpty().mapNotNull { it.right }
        val kinds = actions.map { it.kind ?: "" }
        assertTrue("source.generate.constructor" in kinds, "le constructeur est proposé — reçus : $kinds")
        assertTrue("source.generate.equalsHashcode" in kinds, "equals/hashCode proposé")
        assertTrue("source.generate.toString" in kinds, "toString proposé")

        // 2. La résolution DIFFÉRÉE (V-D, ADR 0027 (f)) : l'action porte
        //    son `data` (cle/arguments/uri), `codeAction/resolve` recalcule
        //    les éditions côté serveur — la voie du châssis réel.
        val actionConstructeur = actions.first { it.kind == "source.generate.constructor" }
        assertNotNull(actionConstructeur.data, "l'action différée porte son data")
        val resolue = boucle.client.textDocumentService.resolveCodeAction(actionConstructeur).get()
        val espace = resolue.edit
        assertNotNull(espace, "la résolution rend les éditions")
        val editions = espace.changes?.get(uri) ?: emptyList()
        assertTrue(editions.isNotEmpty(), "les éditions du constructeur sont servies")
        val texte = editions.joinToString("") { it.newText }
        assertTrue("public Point(int x, int y)" in texte, "la signature générée — reçu : ${texte.take(200)}")
        assertTrue("this.x = x" in texte && "this.y = y" in texte, "les affectations this")

        // 3. La GARDE (P5) : un type AVEC constructeur ne propose PLUS
        //    l'action constructeur.
        val sourceAvec = """
            package demo;

            public class Avec {
                int x;
                int y;
                Avec(int x, int y) {
                    this.x = x;
                    this.y = y;
                }
            }
        """.trimIndent()
        // Un NOUVEAU document pour la garde (le constructeur est le seul
        // différenciateur — le reste est identique à Point).
        val uri2 = dossier.resolve("Avec.java").toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri2, "java", 1, sourceAvec)),
        )
        val idx2 = sourceAvec.indexOf("Avec")
        val ligne2 = sourceAvec.substring(0, idx2).count { it == '\n' }
        val colonne2 = idx2 - sourceAvec.lastIndexOf('\n', idx2) - 1
        val actions2 = boucle.client.textDocumentService.codeAction(
            CodeActionParams(
                TextDocumentIdentifier(uri2),
                Range(org.eclipse.lsp4j.Position(ligne2, colonne2), org.eclipse.lsp4j.Position(ligne2, colonne2 + 1)),
                org.eclipse.lsp4j.CodeActionContext(),
            ),
        ).get().orEmpty().mapNotNull { it.right }
        val kinds2 = actions2.map { it.kind ?: "" }
        assertEquals(
            emptyList<String>(),
            kinds2.filter { it == "source.generate.constructor" },
            "la GARDE : un type avec constructeur ne voit PLUS l'action — reçus : $kinds2",
        )
        assertTrue("source.generate.toString" in kinds2, "toString reste proposé (pas de toString)")
    }

    @TempDir
    lateinit var tmp: Path
}

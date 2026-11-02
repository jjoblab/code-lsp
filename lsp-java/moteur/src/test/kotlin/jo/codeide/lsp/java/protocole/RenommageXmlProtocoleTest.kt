package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * COMPLÉMENT DE CATALOGUE — le RENAME DEPUIS UN XML au niveau PROTOCOLE
 * (ADR 0032) : `textDocument/prepareRename` et `textDocument/rename` sur
 * un document XML du module servent la ressource au caret ; un document
 * XML OUVERT ne publie JAMAIS de diagnostics Java (garde du publicateur —
 * l'intention documentée de V-B devient une garde posée).
 */
class RenommageXmlProtocoleTest {

    /** Un dossier de module Android minimal : sources + res/. */
    private fun dossierModule(): Path {
        val racine = Files.createTempDirectory("cat-xml-proto")
        Files.createDirectories(racine.resolve("demo"))
        racine.resolve("demo").resolve("Main.java").writeText(
            """
            package demo;
            public class Main {
                int texte() { return R.string.bonjour; }
            }
            """.trimIndent(),
        )
        Files.createDirectories(racine.resolve("res/values"))
        racine.resolve("res/values").resolve("strings.xml").writeText(
            "<resources><string name=\"bonjour\">Salut</string></resources>",
        )
        Files.createDirectories(racine.resolve("res/layout"))
        racine.resolve("res/layout").resolve("activity.xml").writeText(
            "<LinearLayout><TextView android:text=\"@string/bonjour\"/></LinearLayout>",
        )
        return racine
    }

    private fun serveurSur(racine: Path): ServeurLangageJava = ServeurLangageJava(
        fabriqueMoteur = { r ->
            r?.let {
                jo.codeide.lsp.java.MoteurJava(
                    racines = listOf(it),
                    jars = emptyList(),
                    jdkHome = null,
                    compliance = "17",
                    niveauDemande = null,
                    indexNoms = null,
                    projetAndroid = jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur(
                        namespace = "demo",
                        dossiersRes = listOf(it.resolve("res")),
                    ),
                )
            }
        },
    )

    @Test
    fun `prepareRename et rename servent la ressource d un document xml (protocole)`() {
        val racine = dossierModule()
        val boucle = BoucleLocale(serveurSur(racine))
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = racine.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        val uriXml = racine.resolve("res/layout/activity.xml").toUri().toString()
        val texteXml = racine.resolve("res/layout/activity.xml").toFile().readText()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uriXml, "xml", 0, texteXml)),
        )

        // AUCUN diagnostic Java publié pour le document XML (garde ADR 0032).
        Thread.sleep(200) // le debounce du publicateur (50 ms) a le temps de tirer
        assertNull(
            boucle.espion.publications.firstOrNull { it.uri == uriXml },
            "un document XML ne publie JAMAIS de diagnostics Java",
        )

        // prepareRename : le placeholder EXACT et la plage EXACTE du nom.
        val offset = texteXml.indexOf("bonjour")
        val ligne = texteXml.substring(0, offset).count { it == '\n' }
        val retour = texteXml.lastIndexOf('\n', offset - 1)
        val colonne = offset - (if (retour < 0) 0 else retour + 1)
        val preparation = boucle.client.textDocumentService
            .prepareRename(PrepareRenameParams(TextDocumentIdentifier(uriXml), Position(ligne, colonne)))
            .get()
        val resultat = preparation?.second
        assertNotNull(resultat, "prepareRename sert la ressource XML")
        assertEquals("bonjour", resultat!!.placeholder)

        // rename : la propagation multi-fichiers (Java + XML).
        val edition = boucle.client.textDocumentService
            .rename(RenameParams(TextDocumentIdentifier(uriXml), Position(ligne, colonne), "salutations"))
            .get()
        assertNotNull(edition)
        val fichiers = (edition.changes?.keys ?: emptyList())
        assertTrue(fichiers.any { it.endsWith("Main.java") }, "le code Java est retouché : $fichiers")
        assertTrue(fichiers.any { it.endsWith("values/strings.xml") }, "la déclaration est retouchée : $fichiers")
        assertTrue(fichiers.contains(uriXml), "le document XML courant voyage sous SON URI : $fichiers")

        boucle.client.shutdown().get()
        boucle.client.exit()
    }
}

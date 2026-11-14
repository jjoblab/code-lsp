package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WillSaveTextDocumentParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Chantier E (14/11/2026) — organisation des imports À LA SAUVEGARDE :
 * `textDocument/willSaveWaitUntil` rend les éditions d'organisation des
 * imports AVANT l'écriture disque (« Optimize imports on the fly »
 * d'IntelliJ — miroir du chantier F Kotlin). Le MÊME organisateur que
 * l'action de code `source.organizeImports` : tri stable, inutilisés
 * retirés, doublons fusionnés.
 *
 * Contrats verrouillés :
 *  - les capacités annoncent willSaveWaitUntil + save.includeText
 *    (la négociation cliente en dépend) ;
 *  - un document avec imports NON TRIÉS reçoit les éditions de tri à la
 *    sauvegarde ;
 *  - un bloc d'imports PROPRE ne reçoit RIEN (idempotence — une
 *    sauvegarde ne réécrit jamais un document propre) ;
 *  - un document jamais ouvert reçoit une liste VIDE (honnête, I9).
 */
class ChantierEWillSaveWaitUntilTest {

    @Test
    fun `capacites - willSaveWaitUntil et save includeText annonces`() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val capacites = serveur.initialize(
            InitializeParams().apply { rootUri = tmp.toUri().toString() },
        ).get().capabilities
        val sync = capacites.textDocumentSync
        assertNotNull(sync, "la synchronisation est annoncée")
        assertTrue(sync.isRight, "la forme OPTIONS est posée (chantier E) — reçue : ${sync.left}")
        val options = sync.right
        assertEquals(
            org.eclipse.lsp4j.TextDocumentSyncKind.Full,
            options.change,
            "le kind reste FULL (fiche 059)",
        )
        assertTrue(options.willSaveWaitUntil, "willSaveWaitUntil annoncé")
        assertEquals(true, options.save?.right?.includeText, "save.includeText annoncé")
    }

    @Test
    fun `imports non tries - editions de tri a la sauvegarde`() {
        val dossier = tmp.resolve("src").also(Files::createDirectories)
        val fichier = dossier.resolve("Main.java")
        // Bloc NON TRIÉ : java.util après java.io (l'ordre alphabétique
        // exige io < util), un import DOUBLÉ, un INUTILISÉ.
        val source = """
            package demo;

            import java.util.List;
            import java.io.File;
            import java.util.List;
            import java.text.SimpleDateFormat;

            public class Main {
                File f;
                List<String> l;
            }
        """.trimIndent()
        fichier.writeText(source)
        val boucle = bouclePrete(dossier)
        val uri = fichier.toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, source)),
        )
        val editions = boucle.client.textDocumentService.willSaveWaitUntil(
            WillSaveTextDocumentParams(TextDocumentIdentifier(uri), org.eclipse.lsp4j.TextDocumentSaveReason.Manual),
        ).get()
        assertTrue(editions.isNotEmpty(), "un bloc non trié reçoit des éditions — reçues : $editions")
        val texte = editions.joinToString("") { it.newText }
        assertTrue(
            texte.contains("import java.io.File"),
            "l'édition réorganisée contient l'import File — reçue : ${editions.map { it.newText }}",
        )
        assertTrue(
            texte.indexOf("java.io.File") < texte.indexOf("java.util.List"),
            "le tri alphabétique io < util est appliqué",
        )
        assertTrue(
            !texte.contains("SimpleDateFormat"),
            "l'import INUTILISÉ est retiré du bloc",
        )
        // Le DOUBLON : une seule occurrence de java.util.List dans le texte.
        assertEquals(
            1,
            texte.split("java.util.List").size - 1,
            "le doublon java.util.List est fusionné — texte : $texte",
        )
    }

    @Test
    fun `imports propres - aucune edition (idempotence)`() {
        val dossier = tmp.resolve("src2").also(Files::createDirectories)
        val fichier = dossier.resolve("Main.java")
        val source = """
            package demo;

            import java.io.File;
            import java.util.List;

            public class Main {
                File f;
                List<String> l;
            }
        """.trimIndent()
        fichier.writeText(source)
        val boucle = bouclePrete(dossier)
        val uri = fichier.toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, source)),
        )
        val editions = boucle.client.textDocumentService.willSaveWaitUntil(
            WillSaveTextDocumentParams(TextDocumentIdentifier(uri), org.eclipse.lsp4j.TextDocumentSaveReason.Manual),
        ).get()
        assertTrue(editions.isEmpty(), "un bloc PROPRE ne reçoit RIEN — reçues : $editions")
    }

    @Test
    fun `document jamais ouvert - liste vide honnete`() {
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams()).get()
        boucle.client.initialized(InitializedParams())
        val editions = boucle.client.textDocumentService.willSaveWaitUntil(
            WillSaveTextDocumentParams(
                TextDocumentIdentifier(tmp.resolve("Inconnu.java").toUri().toString()),
                org.eclipse.lsp4j.TextDocumentSaveReason.Manual,
            ),
        ).get()
        assertTrue(editions.isEmpty(), "un document jamais ouvert rend une liste VIDE — reçues : $editions")
    }

    // --------------------------------------------------------------- //
    // Utilitaires
    // --------------------------------------------------------------- //

    /** Monte la boucle + initialise le serveur sur le dossier de sources. */
    private fun bouclePrete(dossier: Path): BoucleLocale {
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = dossier.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())
        return boucle
    }

    @TempDir
    lateinit var tmp: Path
}

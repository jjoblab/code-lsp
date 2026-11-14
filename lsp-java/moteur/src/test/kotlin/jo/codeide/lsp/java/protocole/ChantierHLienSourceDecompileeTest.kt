package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Chantier H (14/11/2026) — lien « source décompilée » depuis le hover :
 * le survol d'un symbole BINAIRE (jar plateforme, jrt) porte la ligne
 * d'accès `x-codeide.decompile://<fqcn>` (le client CodeIDE la rend en
 * bouton ; un client standard l'affiche comme texte informatif), et la
 * COMMANDE `codeide.decompiledSource` (executeCommand) sert la MÊME
 * source que l'extension decompiledSource (fiche 054) — les deux bouts
 * du lien, enfin rejoints.
 *
 * Contrats verrouillés :
 *  - le hover d'un type binaire porte la ligne decompile avec le FQCN ;
 *  - le hover d'un symbole du PROJET n'en porte JAMAIS (rien à décompiler) ;
 *  - executeCommand codeide.decompiledSource rend la source (contient
 *    le nom simple du type) ;
 *  - une commande inconnue reste REFUSÉE (contrat inchangé).
 */
class ChantierHLienSourceDecompileeTest {

    @Test
    fun `hover d'un type binaire - ligne decompile avec fqcn`() {
        val sdk = sdkDuPoste() ?: return ignoré("SDK Android absent du poste")
        val jar = sdk.resolve("platforms/android-37.2/android.jar")
        if (!Files.isRegularFile(jar)) return ignoré("SDK sans plateforme 37.2")
        val env = jo.codeide.lsp.java.EnvironnementCompilation()
        env.remplacerContexteModule(null, emptyList(), listOf(jar))
        val serveur = ServeurLangageJava(env) { }
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        val source = """
            package demo;

            import android.app.Activity;

            public class Demo extends Activity {
            }
        """.trimIndent()
        val uri = tmp.resolve("Demo.java").toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, source)),
        )
        val ligne = source.substring(0, source.indexOf("extends Activity")).count { it == '\n' }
        val colonne = source.indexOf("Activity", source.indexOf("extends")) - source.lastIndexOf('\n', source.indexOf("Activity", source.indexOf("extends"))) - 1
        val hover = boucle.client.textDocumentService.hover(
            HoverParams(TextDocumentIdentifier(uri), org.eclipse.lsp4j.Position(ligne, colonne)),
        ).get()
        assertNotNull(hover, "le hover du type binaire doit résoudre")
        val texte = hover.contents.right?.value ?: ""
        assertTrue(
            texte.contains("x-codeide.decompile://android.app.Activity"),
            "la ligne decompile porte le FQCN — reçu : $texte",
        )
    }

    @Test
    fun `executeCommand decompiledSource rend la source`() {
        val sdk = sdkDuPoste() ?: return ignoré("SDK Android absent du poste")
        val jar = sdk.resolve("platforms/android-37.2/android.jar")
        if (!Files.isRegularFile(jar)) return ignoré("SDK sans plateforme 37.2")
        val env = jo.codeide.lsp.java.EnvironnementCompilation()
        env.remplacerContexteModule(null, emptyList(), listOf(jar))
        val serveur = ServeurLangageJava(env) { }
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        val reponse = boucle.client.workspaceService.executeCommand(
            ExecuteCommandParams("codeide.decompiledSource", listOf("android.app.Activity")),
        ).get()
        assertNotNull(reponse, "la commande rend une réponse")
        val carte = reponse as? Map<*, *>
        assertNotNull(carte, "la réponse est une carte fqcn+source")
        assertEquals("android.app.Activity", carte["fqcn"], "le FQCN voyagent")
        val sourceRendue = carte["source"]?.toString()
        assertNotNull(sourceRendue, "la source est servie")
        assertTrue(
            sourceRendue.contains("Activity"),
            "la source décompilée contient le nom du type",
        )
    }

    @Test
    fun `commande inconnue - refusee (contrat inchange)`() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams()).get()
        boucle.client.initialized(InitializedParams())
        runCatching {
            boucle.client.workspaceService.executeCommand(
                ExecuteCommandParams("commande.bidon", emptyList()),
            ).get()
        }.onFailure {
            assertTrue(it is Exception, "la commande inconnue est refusée par erreur RPC")
            return
        }
        throw AssertionError("la commande inconnue devait être REFUSÉE")
    }

    // --------------------------------------------------------------- //
    // Utilitaires
    // --------------------------------------------------------------- //

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
        println("[ChantierH] test SDK ignoré : $raison")
    }

    @TempDir
    lateinit var tmp: Path
}

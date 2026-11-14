package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
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
 * Chantier J (14/11/2026) — pull diagnostics (`textDocument/documentDiagnostic`,
 * LSP 3.17) : le client moderne TIRE les diagnostics au lieu d'attendre la
 * publication push (qui reste le chemin par défaut — fiche 059 inchangée).
 * La MÊME diagnose moteur que le publicateur (`moteur.diagnose`), calculée
 * à la demande sur le texte VIVANT ; le rapport voyage en
 * RelatedFullDocumentDiagnosticReport (wrapper lsp4j).
 *
 * Contrats verrouillés :
 *  - un document avec ERREUR rend ses diagnostics au pull (le message
 *    « cannot be resolved » du banc réel) ;
 *  - un document SAIN rend une liste vide ;
 *  - un document INCONNU rend un rapport vide honnête (I9, jamais
 *    d'exception) ;
 *  - la capability diagnosticProvider est annoncée (forme complète,
 *    sans dépendances inter-fichiers).
 */
class ChantierJPullDiagnosticsTest {

    @Test
    fun `document en erreur - diagnostics tires a la demande`() {
        val dossier = tmp.resolve("demo").also(Files::createDirectories)
        val fichier = dossier.resolve("Main.java")
        // La faute : Inexistant n'est résolu nulle part (le moteur nu ne
        // connaît que le jrt — le « cannot be resolved » honnête).
        Files.writeString(fichier, "package demo;\n\npublic class Main {\n    Inexistant champ;\n}\n")
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        // La RACINE du paquet : src2 (demo/Ok.java relatif à src2 = demo).
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())
        val uri = fichier.toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, Files.readString(fichier))),
        )
        val rapport = boucle.client.textDocumentService.diagnostic(
            DocumentDiagnosticParams(TextDocumentIdentifier(uri)),
        ).get()
        assertNotNull(rapport, "le rapport existe (jamais null)")
        val complet = rapport.relatedFullDocumentDiagnosticReport
        assertNotNull(complet, "la forme COMPLETE est rendue (pas unchanged)")
        assertTrue(
            complet.items.isNotEmpty(),
            "l'erreur « Inexistant cannot be resolved » est tirée — reçu : ${complet.items.map { it.message }}",
        )
        assertTrue(
            complet.items.any { it.message.contains("Inexistant") },
            "le diagnostic porte le nom de la faute — reçu : ${complet.items.map { it.message }}",
        )
    }

    @Test
    fun `document sain - liste vide au pull`() {
        val dossier = tmp.resolve("demo2").also(Files::createDirectories)
        val fichier = dossier.resolve("Ok.java")
        Files.writeString(fichier, "package demo2;\n\npublic class Ok {\n    int x = 1;\n}\n")
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        // La RACINE du paquet : src2 (demo/Ok.java relatif à src2 = demo).
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())
        val uri = fichier.toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, Files.readString(fichier))),
        )
        val rapport = boucle.client.textDocumentService.diagnostic(
            DocumentDiagnosticParams(TextDocumentIdentifier(uri)),
        ).get()
        val complet = rapport.relatedFullDocumentDiagnosticReport
        assertNotNull(complet, "la forme complète")
        // Le document SAIN ne rend AUCUN diagnostic d'ERREUR — l'inspection
        // « paquet déclaré » (sévérité FAIBLE, chemin logique vs arborescence
        // du temp) est hors de ce contrat : elle signale l'arborescence du
        // harnais, pas la santé du document (le même comportement que le
        // banc Repro20261101 qui l'assume explicitement).
        val erreurs = complet.items.filter {
            it.severity == null || it.severity == org.eclipse.lsp4j.DiagnosticSeverity.Error
        }
        assertTrue(erreurs.isEmpty(), "un document sain ne rend aucune ERREUR — reçues : ${erreurs.map { it.message }}")
    }

    @Test
    fun `document inconnu - rapport vide honnete`() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams()).get()
        boucle.client.initialized(InitializedParams())
        val rapport = boucle.client.textDocumentService.diagnostic(
            DocumentDiagnosticParams(TextDocumentIdentifier(tmp.resolve("Inconnu.java").toUri().toString())),
        ).get()
        assertNotNull(rapport, "jamais d'exception — rapport vide honnête (I9)")
        val complet = rapport.relatedFullDocumentDiagnosticReport
        assertNotNull(complet, "la forme complète")
        assertEquals(0, complet.items.size, "aucun diagnostic pour un document inconnu")
    }

    @Test
    fun `capacites - diagnosticProvider annonce`() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val capacites = serveur.initialize(InitializeParams()).get().capabilities
        val options = capacites.diagnosticProvider
        assertNotNull(options, "diagnosticProvider annoncé (chantier J)")
        assertEquals(false, options.isInterFileDependencies, "pas de dépendances inter-fichiers")
        assertEquals(false, options.isWorkspaceDiagnostics, "pas de diagnostics d'espace de travail")
    }

    @TempDir
    lateinit var tmp: Path
}

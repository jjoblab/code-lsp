package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P2 (15/11/2026) — `workspace/diagnostic` : le rapport d'ESPACE (LSP
 * 3.17, suite du chantier J). Le serveur ne pose pas d'identifiant de
 * rapport : chaque tir est COMPLET — chaque document OUVERT rend sa
 * forme PLEINE (WorkspaceFullDocumentDiagnosticReport : la même diagnose
 * moteur que documentDiagnostic, avec uri et version), les documents
 * fermés n'apparaissent pas, les XML sont sautés (ADR 0032).
 *
 * Contrats verrouillés :
 *  - chaque document ouvert rend UN rapport plein (uri + version) ;
 *  - le diagnostic d'erreur du document en faute y figure ;
 *  - aucun document fermé n'apparaît ;
 *  - la capability diagnosticProvider annonce workspaceDiagnostics=true
 *    (P2) et executeCommandProvider annonce la commande du chantier H.
 */
class P2WorkspaceDiagnosticTest {

    @Test
    fun `espace - un rapport plein par document ouvert`() {
        val dossier = tmp.resolve("demo").also(Files::createDirectories)
        val fichierBon = dossier.resolve("Bon.java")
        Files.writeString(fichierBon, "package demo;\n\npublic class Bon {\n    int x = 1;\n}\n")
        val fichierFaux = dossier.resolve("Faux.java")
        Files.writeString(fichierFaux, "package demo;\n\npublic class Faux {\n    Inexistant champ;\n}\n")
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        // DEUX documents ouverts (le bon + le faux).
        for (fichier in listOf(fichierBon, fichierFaux)) {
            boucle.client.textDocumentService.didOpen(
                DidOpenTextDocumentParams(
                    TextDocumentItem(fichier.toUri().toString(), "java", 1, Files.readString(fichier)),
                ),
            )
        }
        // Un TROISIÈME sur disque mais JAMAIS ouvert : il ne doit PAS
        // apparaître dans le rapport d'espace.
        Files.writeString(dossier.resolve("Ferme.java"), "package demo;\n\npublic class Ferme {\n}\n")

        val rapport = boucle.client.workspaceService.diagnostic(WorkspaceDiagnosticParams()).get()
        assertNotNull(rapport, "le rapport existe (jamais null)")
        // items est une liste de WRAPPERS (Full | Unchanged) — on extrait
        // la forme pleine de chaque (le serveur ne rend jamais unchanged).
        val items = rapport.items.mapNotNull { it.left }
        assertEquals(2, items.size, "DEUX rapports (les documents OUVERTS) — reçus : ${items.map { it.uri }}")

        // Les URI des documents ouverts, chacun avec sa version.
        val uris = items.map { it.uri }.toSet()
        assertTrue(fichierBon.toUri().toString() in uris, "Bon.java ouvert → rapport")
        assertTrue(fichierFaux.toUri().toString() in uris, "Faux.java ouvert → rapport")
        assertTrue(
            dossier.resolve("Ferme.java").toUri().toString() !in uris,
            "Ferme.java jamais ouvert → ABSENT du rapport",
        )
        items.forEach { assertEquals(1, it.version, "la version du document ouvert voyage") }

        // Le document en faute porte son ERREUR dans le rapport d'espace.
        val rapportFaux = items.first { it.uri == fichierFaux.toUri().toString() }
        val plein = rapportFaux as? org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport
        assertNotNull(plein, "la forme PLEINE (pas unchanged)")
        assertTrue(
            plein.items.any { it.message.contains("Inexistant") },
            "l'erreur du Faux.java figure dans le rapport d'espace — reçus : ${plein.items.map { it.message }}",
        )
        // Le document sain ne rend aucune ERREUR (l'inspection paquet FAIBLE
        // du harnais assumée, miroir ChantierJ).
        val rapportBon = items.first { it.uri == fichierBon.toUri().toString() } as org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport
        assertTrue(
            rapportBon.items.none { it.severity == org.eclipse.lsp4j.DiagnosticSeverity.Error },
            "Bon.java sain : aucune erreur — reçues : ${rapportBon.items.map { it.message }}",
        )
    }

    @Test
    fun `espace vide - rapport honnete sans documents`() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams()).get()
        boucle.client.initialized(InitializedParams())
        val rapport = boucle.client.workspaceService.diagnostic(WorkspaceDiagnosticParams()).get()
        assertNotNull(rapport, "jamais d'exception — rapport vide honnête (I9)")
        assertTrue(rapport.items.isEmpty(), "aucun document ouvert ⇒ liste vide")
    }

    @Test
    fun `capacites - workspaceDiagnostics et commande H annoncées`() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val capacites = serveur.initialize(InitializeParams()).get().capabilities
        val options = capacites.diagnosticProvider
        assertNotNull(options, "diagnosticProvider annoncé (chantier J)")
        assertTrue(options.isWorkspaceDiagnostics, "workspaceDiagnostics=true (P2)")
        val commandes = capacites.executeCommandProvider
        assertNotNull(commandes, "executeCommandProvider annoncé (chantier H)")
        assertTrue(
            "codeide.decompiledSource" in (commandes.commands ?: emptyList()),
            "la commande de la source décompilée est annoncée",
        )
    }

    @TempDir
    lateinit var tmp: Path
}

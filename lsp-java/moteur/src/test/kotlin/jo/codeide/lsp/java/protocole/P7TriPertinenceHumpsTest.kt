package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P7 (15/11/2026) — tri par pertinence des humps : après le préfixe
 * (inchangé, ordre du balayage), les résultats camel-humps sont
 * ORDONNÉS par longueur de nom croissante — « AM » doit présenter
 * ActionMode (10 lettres) AVANT ActivityManager (15), miroir du
 * rangement IntelliJ (l'ordre du balayage est un artefact du système
 * de fichiers, pas un choix de pertinence).
 */
class P7TriPertinenceHumpsTest {

    @Test
    fun `humps sources - ordonnes du plus court au plus long`() {
        val dossier = tmp.resolve("src/demo").also(Files::createDirectories)
        // Trois matchs humps de « AM », longueurs 15 / 10 / 21 : le tri
        // doit rendre 10 < 15 < 21, quel que soit l'ordre du balayage.
        Files.writeString(dossier.resolve("ActivityManager.java"), "package demo;\npublic class ActivityManager {}\n")
        Files.writeString(dossier.resolve("ActionMode.java"), "package demo;\npublic class ActionMode {}\n")
        Files.writeString(dossier.resolve("ApplicationModeManager.java"), "package demo;\npublic class ApplicationModeManager {}\n")
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.resolve("src").toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        val reponse = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("AM")).get()
        val noms = (reponse.left ?: emptyList()).map { it.name } + (reponse.right ?: emptyList()).map { it.name }
        val am = noms.filter { it in setOf("ActionMode", "ActivityManager", "ApplicationModeManager") }
        assertEquals(
            listOf("ActionMode", "ActivityManager", "ApplicationModeManager"),
            am,
            "les humps « AM » ordonnés du plus court au plus long — reçus : $noms",
        )
    }

    @Test
    fun `prefixe d'abord - inchange`() {
        val dossier = tmp.resolve("src/demo").also(Files::createDirectories)
        // Un PRÉFIXE (AMachin) et un humps (ActionMode) : le préfixe
        // garde la priorité ABSOLUE, l'ordre du balayage pour lui.
        Files.writeString(dossier.resolve("ActionMode.java"), "package demo;\npublic class ActionMode {}\n")
        Files.writeString(dossier.resolve("AMachin.java"), "package demo;\npublic class AMachin {}\n")
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.resolve("src").toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        val reponse = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("AM")).get()
        val noms = (reponse.left ?: emptyList()).map { it.name } + (reponse.right ?: emptyList()).map { it.name }
        assertTrue("AMachin" in noms && "ActionMode" in noms, "les deux matchs figurent — reçus : $noms")
        assertEquals(
            0,
            noms.indexOf("AMachin"),
            "le PRÉFIXE reste en tête (priorité absolue sur le humps le plus court)",
        )
    }

    @TempDir
    lateinit var tmp: Path
}

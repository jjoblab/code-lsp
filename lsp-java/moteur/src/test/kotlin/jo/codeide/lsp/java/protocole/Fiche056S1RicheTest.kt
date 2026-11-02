package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.eclipse.lsp4j.DiagnosticTag
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 056 — JavaS1RicheTest : lot S1 « parité utile JDT.LS » (plan de
 * stabilisation §3.1) : tags de diagnostics (S1-b — `Unnecessary` sur un
 * import inutilisé), `workspace/symbol` (S1-c — recherche de types par
 * préfixe sur les sources du projet, requête vide et moteur non prêt →
 * liste vide), registre des serveurs vivants (S1-d — rappel d'arrêt à la
 * façade) et événements de fichiers surveillés.
 *
 * Le montage du cahier : serveur réel + client espion (enregistre les
 * `publishDiagnostics`), SANS classpath pour les cas « moteur non prêt ».
 * Fixtures : classe avec import inutilisé (`T1.java`), affectation erronée
 * (`T2.java`), `com/ex/MaVue.java`, `com/ex/Vue.java`.
 */
class Fiche056S1RicheTest {

    @TempDir
    lateinit var dossier: Path

    /** L'ID ecj de l'import inutilisé (IProblem.UnusedImport). */
    private companion object {
        private const val ID_UNUSED_IMPORT: Long = 268435844L
    }

    /**
     * La liste de symboles d'une réponse `workspace/symbol`.
     *
     * Le SERVEUR répond une LISTE (forme « gauche » du cahier — le fil
     * porte un tableau NU, mesuré sur la boucle locale). La relecture
     * lsp4j 0.21.2 d'un tableau VIDE est AMBIGUË : l'adaptateur Either ne
     * peut pas deviner la nature des éléments et le pose à droite — la
     * liste (vide) est néanmoins là, sans exception : c'est le CONTRAT
     * de la fiche (« liste vide, jamais d'exception ») qui est
     * verrouillé, la lecture retenue par le harnais est documentée ici.
     */
    private fun listeDe(reponse: org.eclipse.lsp4j.jsonrpc.messages.Either<List<org.eclipse.lsp4j.SymbolInformation>, List<org.eclipse.lsp4j.WorkspaceSymbol>>): List<org.eclipse.lsp4j.SymbolInformation> {
        val gauche = reponse.left
        if (gauche != null) return gauche
        val droite = reponse.right ?: return emptyList()
        return droite.map { ws ->
            org.eclipse.lsp4j.SymbolInformation().apply {
                name = ws.name
                kind = ws.kind
                location = ws.location.left
                containerName = ws.containerName
            }
        }
    }

    /** Un serveur réel sur le dossier (moteur nu — sans classpath Android). */
    private fun serveurSurDossier(): ServeurLangageJava = ServeurLangageJava(
        fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
    )

    /** Les paramètres d'initialize sur le dossier temporaire. */
    private fun parametres(): InitializeParams = InitializeParams().apply {
        rootUri = dossier.toUri().toString()
    }

    // ----------------------------------------------------------------- //
    // Diagnostics — tags (S1-b)
    // ----------------------------------------------------------------- //

    /** importInutiliseVoyageAvecTagUnnecessary — import inutilisé taggé Unnecessary. */
    @Test
    fun importInutiliseVoyageAvecTagUnnecessary() {
        val boucle = BoucleLocale(serveurSurDossier())
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())
        val uri = dossier.resolve("T1.java").toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(uri, "java", 0, "import java.util.ArrayList;\n\nclass T1 {\n}\n"),
            ),
        )
        // Publication attendue sous 15 s (polling de 100 ms).
        val publication = boucle.attendrePublication(uri, delaiMs = 15_000)
        assertNotNull(publication, "une publication de diagnostics arrive (asynchrone, sous 15 s)")
        assertTrue(
            publication.diagnostics.any { d -> d.tags?.contains(DiagnosticTag.Unnecessary) == true },
            "au moins un diagnostic porte le tag Unnecessary — trouvé : ${publication.diagnostics}",
        )
        val tagge = publication.diagnostics.first { d -> d.tags?.contains(DiagnosticTag.Unnecessary) == true }
        val codeLsp = tagge.code
        val codeTexte = when (codeLsp) {
            is org.eclipse.lsp4j.jsonrpc.messages.Either<*, *> -> codeLsp.left?.toString()
            else -> codeLsp?.toString()
        }
        assertEquals(
            "ecj:$ID_UNUSED_IMPORT",
            codeTexte,
            "le code est exactement « ecj:<IProblem.UnusedImport> »",
        )
    }

    /** diagnosticSansCodeEcnConnuSansTag — pas de tag inventé hors famille. */
    @Test
    fun diagnosticSansCodeEcnConnuSansTag() {
        val boucle = BoucleLocale(serveurSurDossier())
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())
        val uri = dossier.resolve("T2.java").toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(uri, "java", 0, "class T2 {\n    int x = \"texte\";\n}\n"),
            ),
        )
        val publication = boucle.attendrePublication(uri, delaiMs = 15_000)
        assertNotNull(publication, "une publication arrive (erreur de type)")
        assertTrue(publication.diagnostics.isNotEmpty(), "l'erreur de type est publiée")
        assertTrue(
            publication.diagnostics.any { d -> d.tags.isNullOrEmpty() },
            "au moins un diagnostic (l'erreur de type) a des tags null OU vides — aucun tag inventé",
        )
    }

    // ----------------------------------------------------------------- //
    // workspace/symbol (S1-c)
    // ----------------------------------------------------------------- //

    /** capaciteWorkspaceSymbolDeclaree — capacité workspaceSymbolProvider déclarée. */
    @Test
    fun capaciteWorkspaceSymbolDeclaree() {
        val boucle = BoucleLocale(serveurSurDossier())
        boucle.demarrer()
        val resultat = boucle.client.initialize(parametres()).get()
        assertEquals(
            true,
            resultat.capabilities.workspaceSymbolProvider.left,
            "workspaceSymbolProvider annoncé en forme booléenne TRUE (côté gauche du Either)",
        )
    }

    /** requeteVideListeVideConventionI9 — requête vide → liste vide (I9). */
    @Test
    fun requeteVideListeVideConventionI9() {
        val boucle = BoucleLocale(serveurSurDossier())
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())
        val reponse = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("")).get()
        assertNotNull(reponse, "la réponse existe (jamais d'exception — convention I9)")
        assertTrue(listeDe(reponse).isEmpty(), "la réponse est une liste VIDE (le fil porte le tableau nu)")
    }

    /** rechercheTypeSourceLocalisationReelleEtPlage — type source trouvé, localisation réelle. */
    @Test
    fun rechercheTypeSourceLocalisationReelleEtPlage() {
        // Le fichier AVANT le serveur (sur disque sous la racine source).
        val paquet = dossier.resolve("com/ex")
        Files.createDirectories(paquet)
        Files.writeString(paquet.resolve("MaVue.java"), "package com.ex;\n\npublic class MaVue {\n}\n")

        val boucle = BoucleLocale(serveurSurDossier())
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())

        // didChangeWatchedFiles avec liste VIDE (force la construction de
        // l'index).
        boucle.client.workspaceService.didChangeWatchedFiles(
            DidChangeWatchedFilesParams(emptyList()),
        )

        val reponse = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("MaVue")).get()
        assertNotNull(reponse, "la réponse existe")
        val entrees = listeDe(reponse)
        assertTrue(entrees.isNotEmpty(), "liste non vide — MaVue trouvée")
        val entree = entrees.first { it.name == "MaVue" }
        assertEquals("com.ex", entree.containerName, "containerName = « com.ex »")
        assertEquals(
            paquet.resolve("MaVue.java").toUri().toString(),
            entree.location.uri,
            "URI égale à la forme Path.toUri() du fichier",
        )
        assertEquals(
            Position(2, 13),
            entree.location.range.start,
            "plage de début sur le NOM déclarant : ligne 2, caractère 13",
        )
    }

    /** moteurNonPretListeVideSansException — moteur non prêt → liste vide sans exception. */
    @Test
    fun moteurNonPretListeVideSansException() {
        // Serveur construit SANS environnement (aucune racine source).
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams()).get()
        boucle.client.initialized(InitializedParams())
        val reponse = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("Rien")).get()
        assertNotNull(reponse, "liste vide ; aucune exception (jamais d'erreur, toujours une réponse)")
        assertTrue(listeDe(reponse).isEmpty(), "moteur non prêt → liste VIDE")
    }

    // ----------------------------------------------------------------- //
    // Rappel d'arrêt (S1-d) et fichiers surveillés
    // ----------------------------------------------------------------- //

    /** aLArretEstAppeleAuShutdownEtALexit — rappel d'arrêt : shutdown puis exit. */
    @Test
    fun aLArretEstAppeleAuShutdownEtALexit() {
        var appels = 0
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { null },
            rappelArret = { appels++ },
        )
        serveur.initialize(parametres()).get()
        assertEquals(0, appels, "0 appel du rappel après initialize")
        serveur.initialized(InitializedParams())
        assertEquals(0, appels, "0 appel après initialized (le rappel ne touche que l'arrêt)")
        serveur.shutdown().get()
        assertEquals(1, appels, "1 appel après shutdown (la façade est prévenue)")
        serveur.exit()
        assertEquals(2, appels, "2 appels après exit (confirmation — le rappel est idempotent côté façade)")
    }

    /** evenementsFichiersInvalidentLeCacheSansErreur — didChangeWatchedFiles tolérant. */
    @Test
    fun evenementsFichiersInvalidentLeCacheSansErreur() {
        val vue = dossier.resolve("com/ex/Vue.java")
        Files.createDirectories(vue.parent)
        Files.writeString(vue, "package com.ex;\n\npublic class Vue {\n}\n")

        val serveur = serveurSurDossier()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())

        // Trois notifications successives : .java EXISTANT (Changed),
        // .java ABSENT (Deleted — suppression externe), NON-.java
        // (notes.txt, Changed). AUCUNE exception dans les trois cas.
        val evenements = listOf(
            FileEvent(vue.toUri().toString(), FileChangeType.Changed),
            FileEvent(dossier.resolve("com/ex/Absente.java").toUri().toString(), FileChangeType.Deleted),
            FileEvent(dossier.resolve("notes.txt").toUri().toString(), FileChangeType.Changed),
        )
        // Appel DIRECT du service espace (notification sans réponse : la
        // tolérance s'observe côté serveur — l'appel ne doit pas lever).
        serveur.didChangeWatchedFiles(DidChangeWatchedFilesParams(evenements))
        serveur.didChangeWatchedFiles(DidChangeWatchedFilesParams(emptyList()))
    }
}

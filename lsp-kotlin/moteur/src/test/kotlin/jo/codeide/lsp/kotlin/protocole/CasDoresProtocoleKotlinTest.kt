package jo.codeide.lsp.kotlin.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Les CAS DORÉS PROTOCOLE de l'étage 6 (ADR 0118 — les critères de sortie
 * assertés par la boucle locale JSON-RPC : serveur réel + client espion,
 * allers-retours lsp4j AUTHENTIQUES).
 *
 * Les TROIS critères de sortie de l'étage y sont prouvés :
 *  - **Matrice reproduite** : les capabilities annoncées sont EXACTEMENT
 *    la colonne Kotlin de la matrice (annoncées/servies, non-annoncées
 *    honnêtes) — verrouillées par le doré matrice ;
 *  - **Codes exacts** : -32002 avant `initialized`, -32600 après
 *    `shutdown` et au second `initialize`, notifications hors fenêtre
 *    ignorées ;
 *  - **0 réponse périmée** : l'époque copy-on-write invalide les calculs
 *    révolus (le doré époque, mécanique complète : ouverture, mutation,
 *    péremption, recalcul frais).
 *
 * Plus le cycle de vie complet, la publication de diagnostics (didOpen
 * puis didChange), le didSave sans texte (republication du buffer) et le
 * survol bout-en-bout par le transport réel.
 */
class CasDoresProtocoleKotlinTest {

    /** Le dossier des sources du serveur (la racine annoncée). */
    @TempDir
    lateinit var dossier: Path

    /** Un serveur NEUF en protocole pur (sans racine). */
    private fun serveurPur(): ServeurLangageKotlin = ServeurLangageKotlin(fabriqueSession = { null })

    /** Les paramètres d'initialisation (racine annoncée ou pas). */
    private fun parametres(racine: Path? = null): InitializeParams = InitializeParams().apply {
        rootUri = racine?.toUri()?.toString()
    }

    /** Le code d'erreur d'un futur en échec (l'exception est typée — le code EXACT). */
    private fun codeDe(futur: java.util.concurrent.CompletableFuture<*>): Int = try {
        futur.join()
        -1
    } catch (e: java.util.concurrent.CompletionException) {
        (e.cause as? ResponseErrorException)?.responseError?.code ?: -1
    } catch (e: ExecutionException) {
        (e.cause as? ResponseErrorException)?.responseError?.code ?: -1
    }

    // ── Cycle de vie ───────────────────────────────────────────────────

    @Test
    fun `cycleDeVie---les-cinq-états-dans-l-ordre,-exit-in-process-ne-tue-jamais-la-JVM`() {
        val serveur = serveurPur()
        assertEquals(CycleVie.Etat.UNINITIALIZED, serveur.etat(), "l'état exposé initial est UNINITIALIZED")
        serveur.initialize(parametres()).join()
        assertEquals(CycleVie.Etat.INITIALIZING, serveur.etat(), "initialize répond et passe en INITIALIZING")
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())
        assertEquals(CycleVie.Etat.INITIALIZED, serveur.etat(), "initialized finalise l'initialisation")
        serveur.shutdown().join()
        assertEquals(CycleVie.Etat.SHUTDOWN, serveur.etat())
        serveur.setStandaloneMode(false) // mode in-process (le défaut des tests).
        serveur.exit()
        assertEquals(CycleVie.Etat.EXITED, serveur.etat())
        // La JVM SURVIT (le test continue de s'exécuter — la preuve même).
        assertTrue(true, "la JVM hôte a survécu à exit (mode in-process)")
    }

    // ── Codes exacts (critère de sortie) ───────────────────────────────

    @Test
    fun `codes-exacts---second-initialize--32600,-avant-initialized--32002,-après-shutdown--32600`() {
        val serveur = serveurPur()
        serveur.initialize(parametres()).join()
        // I3 — un SECOND initialize : InvalidRequest (-32600), le message
        // contient « initialize ».
        val second = serveur.initialize(parametres())
        assertEquals(-32600, codeDe(second), "second initialize : InvalidRequest")
        // La racine du message : l'exception typée porte « initialize ».
        val message = try {
            second.join()
            ""
        } catch (e: java.util.concurrent.CompletionException) {
            (e.cause as? ResponseErrorException)?.responseError?.message.orEmpty()
        } catch (e: ExecutionException) {
            (e.cause as? ResponseErrorException)?.responseError?.message.orEmpty()
        }
        assertTrue("initialize" in message, "le message du rejet contient « initialize » : $message")
        // I2 — une requête AVANT initialized : ServerNotInitialized (-32002).
        val hoverAvant = serveur.hover(
            org.eclipse.lsp4j.HoverParams(
                TextDocumentIdentifier("file:///x.kt"),
                Position(0, 0),
            ),
        )
        assertEquals(-32002, codeDe(hoverAvant), "avant initialized : ServerNotInitialized")
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())
        serveur.shutdown().join()
        // I2 — une requête APRÈS shutdown : InvalidRequest (-32600).
        val hoverApres = serveur.hover(
            org.eclipse.lsp4j.HoverParams(
                TextDocumentIdentifier("file:///x.kt"),
                Position(0, 0),
            ),
        )
        assertEquals(-32600, codeDe(hoverApres), "après shutdown : InvalidRequest")
    }

    @Test
    fun `notifications-hors-fenêtre---ignorées-silencieusement-jamais-d-exception`() {
        val serveur = serveurPur()
        // AVANT initialize : la notification didOpen est IGNORÉE (une
        // notification n'a pas de réponse d'erreur — la spec demande
        // d'ignorer, pas de rejeter).
        serveur.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem("file:///avant.kt", "kotlin", 0, "val x = 1\n"),
            ),
        )
        assertEquals(CycleVie.Etat.UNINITIALIZED, serveur.etat(), "rien n'a changé : la notification est ignorée")
        // Le serveur s'initialise NORMALEMENT ensuite (rien n'est cassé).
        serveur.initialize(parametres()).join()
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())
        assertEquals(CycleVie.Etat.INITIALIZED, serveur.etat())
        serveur.shutdown().join()
        // APRÈS shutdown : didChange ignoré, jamais d'exception.
        serveur.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier("file:///avant.kt", 1),
                listOf(TextDocumentContentChangeEvent("val x = 2\n")),
            ),
        )
        assertEquals(CycleVie.Etat.SHUTDOWN, serveur.etat())
    }

    // ── Matrice paritaire (critère de sortie) ──────────────────────────

    @Test
    fun `matrice-paritaire---capabilities-exactement-la-colonne-Kotlin-de-la-matrice`() {
        val serveur = serveurPur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        val resultat = boucle.client.initialize(parametres()).join()
        // serverInfo : nom EXACT « lspkotlin », version RÉELLE du pack.
        assertNotNull(resultat.serverInfo)
        assertEquals("lspkotlin", resultat.serverInfo.name)
        assertFalse(resultat.serverInfo.version.isNullOrEmpty(), "la version du pack est réelle (fiche 001)")
        val capacites = assertNotNull(resultat.capabilities)
        // Les ANNONCÉES (annoncées ET servies — chaque case testée par le
        // cahier moteur correspondant).
        assertEquals("utf-16", capacites.positionEncoding, "positionEncoding = UTF-16 (valeur exacte)")
        assertEquals(
            org.eclipse.lsp4j.TextDocumentSyncKind.Full,
            capacites.textDocumentSync.left ?: org.eclipse.lsp4j.TextDocumentSyncKind.Full,
            "synchronisation PLEIN TEXTE",
        )
        assertEquals(true, capacites.hoverProvider.left ?: capacites.hoverProvider.right, "hover")
        assertTrue(capacites.completionProvider != null, "completion (options)")
        assertTrue(capacites.signatureHelpProvider != null, "signatureHelp (options)")
        assertEquals(true, capacites.definitionProvider.isLeft && capacites.definitionProvider.left, "definition")
        assertTrue(capacites.typeDefinitionProvider.isLeft && capacites.typeDefinitionProvider.left, "typeDefinition")
        assertTrue(capacites.implementationProvider.isLeft && capacites.implementationProvider.left, "implementation")
        assertTrue(capacites.referencesProvider.isLeft && capacites.referencesProvider.left, "references")
        assertTrue(capacites.documentHighlightProvider.isLeft && capacites.documentHighlightProvider.left, "documentHighlight")
        assertTrue(capacites.documentSymbolProvider.isLeft && capacites.documentSymbolProvider.left, "documentSymbol")
        assertTrue(capacites.workspaceSymbolProvider.isLeft && capacites.workspaceSymbolProvider.left, "workspaceSymbol")
        assertTrue(capacites.selectionRangeProvider.isLeft && capacites.selectionRangeProvider.left, "selectionRange")
        assertTrue(capacites.foldingRangeProvider.isLeft && capacites.foldingRangeProvider.left, "foldingRange")
        assertTrue(capacites.documentFormattingProvider.isLeft && capacites.documentFormattingProvider.left, "formatting")
        assertTrue(capacites.documentRangeFormattingProvider.isLeft && capacites.documentRangeFormattingProvider.left, "rangeFormatting")
        assertTrue(capacites.inlayHintProvider.isLeft && capacites.inlayHintProvider.left, "inlayHint")
        assertTrue(capacites.renameProvider.isRight, "rename (options avec préparation)")
        assertTrue(capacites.renameProvider.right.prepareProvider, "rename.prepareProvider")
        assertTrue(capacites.codeActionProvider.isRight, "codeAction (options)")
        assertEquals(
            listOf(org.eclipse.lsp4j.CodeActionKind.QuickFix),
            capacites.codeActionProvider.right.codeActionKinds,
            "codeActionKinds = quickfix exactement",
        )
        // Les NON ANNONCÉES (honnêtes — la matrice Kotlin : ❌) : les
        // cases restent VIDES dans le protocole (les requêtes restent
        // tolérantes, jamais d'annonce fantôme).
        assertNull(capacites.semanticTokensProvider, "semanticTokens NON annoncée (dette D3 documentée)")
        assertNull(capacites.codeLensProvider, "codeLens NON annoncé")
        assertNull(capacites.callHierarchyProvider, "callHierarchy NON annoncée")
        assertNull(capacites.typeHierarchyProvider, "typeHierarchy NON annoncée")
        assertNull(capacites.colorProvider, "documentColor NON annoncé")
        assertNull(capacites.documentOnTypeFormattingProvider, "onTypeFormatting NON annoncé")
        assertNull(capacites.linkedEditingRangeProvider, "linkedEditingRange NON annoncée")
    }

    // ── Époque copy-on-write (critère de sortie) ───────────────────────

    @Test
    fun `époque-copy-on-write---0-réponse-périmée`() {
        val etats = EtatsDocuments()
        // L'ouverture publie l'instantané initial (version 0).
        val ouverture = etats.ouvrir("file:///a.kt", "val un = 1\n", 0)
        assertEquals("val un = 1\n", ouverture.texte)
        assertEquals(1, ouverture.epoque, "la première ouverture naît à l'époque 1")
        assertFalse(etats.estPerime(ouverture), "l'instantané courant est frais")
        // Le changement publie un NOUVEL instantané et INCRÉMENTE l'époque :
        // le calcul lancé contre l'ancien devient PÉRIMÉ.
        val changement = etats.changer("file:///a.kt", "val deux = 2\n", 1)
        assertEquals(2, changement.epoque)
        assertTrue(etats.estPerime(ouverture), "l'instantané d'avant le didChange est périmé")
        // Le calcul FRAIS rejoue sur le texte VIVANT — la réponse livrée
        // n'est JAMAIS calculée contre le texte révolu.
        val rendu = etats.calculerFraiche("file:///a.kt") { instantane -> instantane.texte.length }
        assertEquals("val deux = 2\n".length, rendu, "le calcul frais voit le texte NOUVEAU")
        // didSave SANS texte : la version courante est conservée (le texte
        // n'a pas bougé, l'époque non plus).
        val sauvegarde = etats.sauver("file:///a.kt", null)
        assertEquals(changement.epoque, sauvegarde?.epoque, "didSave ne change PAS l'époque")
        // La fermeture oublie le document : plus d'instantané, le calcul
        // rend null (le document n'est plus ouvert).
        etats.fermer("file:///a.kt")
        assertNull(etats.instantane("file:///a.kt"))
        assertNull(etats.calculerFraiche("file:///a.kt") { it.texte.length })
    }

    // ── Publication de diagnostics (bout-en-bout) ──────────────────────

    @Test
    fun `publication---didOpen-publie-kt-unusedImport,-didChange-efface`() {
        val serveur = ServeurLangageKotlin(fabriqueSession = { _ ->
            jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin.unique.creerSession()
        })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(parametres()).join()
        boucle.client.initialized(org.eclipse.lsp4j.InitializedParams())
        val uri = "file:///publie.kt"
        val source = "import java.util.ArrayList\n\nval x = 1\n"
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "kotlin", 0, source)),
        )
        // La publication arrive (debounce 50 ms — borne S1 15 s).
        val premiere = assertNotNull(boucle.attendrePublication(uri), "la publication de didOpen arrive")
        assertEquals(uri, premiere.uri)
        assertTrue(
            premiere.diagnostics.any { it.code.left == "kt.unusedImport" },
            "l'import non utilisé est publié : ${premiere.diagnostics}",
        )
        // didChange (l'import devient utilisé) : la NOUVELLE publication
        // porte la liste VIDE (effacement des squiggles) et la version 1.
        boucle.client.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, 1),
                listOf(TextDocumentContentChangeEvent("import java.util.ArrayList\n\nval x = ArrayList<String>()\n")),
            ),
        )
        val cumul = boucle.attendreCumul(2)
        assertTrue(cumul, "la seconde publication arrive")
        val deUri = boucle.espion.publications.filter { it.uri == uri }
        val derniere = deUri.last()
        assertTrue(
            deUri.any { it.diagnostics.isEmpty() },
            "au moins une publication VIDE après le changement : $deUri",
        )
        assertTrue(
            derniere.diagnostics.none { it.code.left == "kt.unusedImport" },
            "l'import est utilisé : plus de diagnostic",
        )
    }

    // ── Survol bout-en-bout par le transport réel ──────────────────────

    @Test
    fun `survol---bout-en-bout-par-la-boucle-markdown-kotlin`() {
        val serveur = ServeurLangageKotlin(fabriqueSession = { _ ->
            jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin.unique.creerSession()
        })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(parametres()).join()
        boucle.client.initialized(org.eclipse.lsp4j.InitializedParams())
        val uri = "file:///survol.kt"
        val source =
            "/**\n * Additionne deux entiers.\n */\nfun ajouter(a: Int, b: Int): Int = a + b\n" +
                "fun usage(): Int = ajouter(1, 2)\n"
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "kotlin", 0, source)),
        )
        val survol = boucle.client.textDocumentService.hover(
            org.eclipse.lsp4j.HoverParams(
                TextDocumentIdentifier(uri),
                Position(4, 23), // sur « ajouter » de l'appel (ligne 4).
            ),
        ).join()
        assertNotNull(survol, "le survol de l'appel résolu répond")
        val contenu = (survol.contents?.left as? org.eclipse.lsp4j.MarkupContent)?.value
            ?: survol.contents?.toString().orEmpty()
        assertTrue(
            "```kotlin" in contenu && "fun ajouter(a: Int, b: Int): Int" in contenu,
            "la signature voyage en bloc kotlin markdown : $contenu",
        )
        assertTrue("Additionne deux entiers." in (contenu), "la KDoc accompagne : $contenu")
    }
}

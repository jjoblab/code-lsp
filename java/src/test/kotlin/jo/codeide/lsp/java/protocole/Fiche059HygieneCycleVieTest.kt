package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 059 — LifecycleHygieneTest : lot S0-a « hygiène protocolaire »
 * (audit §6.3) — garde lifecycle I2 (requête avant `initialized` rejetée
 * ServerNotInitialized, après `shutdown` rejetée InvalidRequest ;
 * notifications ignorées hors fenêtre), initialize réentrant I3
 * (InvalidRequest), didSave sans `includeText` I5 (republication depuis
 * le buffer vivant), serverInfo.version I7 (version du dépôt lue de la
 * ressource embarquée), convention null/vide I9 et erreur typée rename
 * I8.
 *
 * Les tests du cahier tournent SANS classpath Android (protocole
 * verrouillé, pas l'analyse) ; le client espion reçoit les publications
 * de diagnostics. Le montage combine appels DIRECTS (les rejets — la
 * fiche exige un rejet SYNCHRONE observable) et la boucle JSON-RPC
 * ([BoucleLocale]) pour les notifications et le didSave.
 */
class Fiche059HygieneCycleVieTest {

    @TempDir
    lateinit var dossier: Path

    /**
     * Un serveur SANS classpath Android (le cahier : « protocole
     * verrouillé, pas l'analyse ») — mais AVEC moteur : les publications
     * de diagnostics des tests I5 exigent le diagnose réel (syntaxe) ;
     * le moteur nu sur la racine du dossier le sert (image jrt du
     * runtime, aucun jar additionnel).
     */
    private fun serveurPur(): ServeurLangageJava = ServeurLangageJava(
        fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
    )

    /** Les paramètres d'initialize sur le dossier temporaire. */
    private fun parametres(): InitializeParams = InitializeParams().apply {
        rootUri = dossier.toUri().toString()
    }

    /** Le code du rejet d'un futur exceptionnel (ou échec du test). */
    private fun codeDe(futur: java.util.concurrent.CompletableFuture<*>): Int = try {
        futur.get()
        fail("le futur devait être en erreur")
    } catch (e: ExecutionException) {
        val reponse = e.cause as? ResponseErrorException
            ?: fail("la cause devait être ResponseErrorException — trouvé : ${e.cause}")
        reponse.responseError?.code ?: fail("code d'erreur absent")
    }

    // ----------------------------------------------------------------- //
    // Garde I2 / I3 — codes d'erreur EXACTS
    // ----------------------------------------------------------------- //

    /** initializeDeuxFoisRejeteteInvalidRequest — initialize réentrant rejeté (I3). */
    @Test
    fun initializeDeuxFoisRejeteteInvalidRequest() {
        val serveur = serveurPur()
        serveur.initialize(parametres()).get()
        val second = serveur.initialize(parametres())
        assertEquals(
            ResponseErrorCode.InvalidRequest.value,
            codeDe(second),
            "code d'erreur exact -32600 (InvalidRequest)",
        )
        // Le message contient « initialize ».
        try {
            second.get()
            fail("rejet attendu")
        } catch (e: ExecutionException) {
            val message = (e.cause as? ResponseErrorException)?.responseError?.message ?: ""
            assertTrue("initialize" in message, "le message contient « initialize » — trouvé : « $message »")
        }
    }

    /** requeteAvantInitializedRejeteeServerNotInitialized — requête précoce rejetée (I2). */
    @Test
    fun requeteAvantInitializedRejeteeServerNotInitialized() {
        val serveur = serveurPur()
        // AUCUN initialized — la requête hover sur un document fictif.
        val params = org.eclipse.lsp4j.HoverParams()
        params.textDocument = TextDocumentIdentifier("file:///fictif/A.java")
        params.position = Position(0, 0)
        val futur = serveur.hover(params)
        // Rejet SYNCHRONE : le futur est DÉJÀ terminé en erreur — pas de
        // future en erreur différée (borne de la fiche).
        assertTrue(futur.isDone, "le futur est déjà terminé (rejet immédiat)")
        assertTrue(futur.isCompletedExceptionally, "le futur est déjà en erreur (rejet synchrone)")
        assertEquals(
            ResponseErrorCode.ServerNotInitialized.value,
            codeDe(futur),
            "code d'erreur exact -32002 (ServerNotInitialized)",
        )
    }

    /** requeteApresShutdownRejeteeInvalidRequest — requête post-shutdown rejetée (I2). */
    @Test
    fun requeteApresShutdownRejeteeInvalidRequest() {
        val serveur = serveurPur()
        serveur.initialize(parametres()).get()
        serveur.initialized(InitializedParams())
        serveur.shutdown().get()
        val params = org.eclipse.lsp4j.CompletionParams()
        params.textDocument = TextDocumentIdentifier("file:///fictif/A.java")
        params.position = Position(0, 0)
        assertEquals(
            ResponseErrorCode.InvalidRequest.value,
            codeDe(serveur.completion(params)),
            "code d'erreur exact -32600 (InvalidRequest) après shutdown",
        )
    }

    // ----------------------------------------------------------------- //
    // Notifications hors fenêtre — ignorées, jamais rejetées
    // ----------------------------------------------------------------- //

    /** notificationAvantInitializedIgnoree — notification précoce ignorée, pas rejetée. */
    @Test
    fun notificationAvantInitializedIgnoree() {
        val serveur = serveurPur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()

        // didOpen AVANT initialize — aucune exception, aucune publication.
        val uri = (dossier.resolve("Avant.java").toUri().toString())
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(
                org.eclipse.lsp4j.TextDocumentItem(uri, "java", 0, "class Avant {}"),
            ),
        )
        Thread.sleep(200)
        assertEquals(0, boucle.espion.publications.size, "0 publication après 200 ms (didOpen précoce ignoré)")

        // Initialisation complète PUIS le MÊME didOpen → publication (≤ 3 s).
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(
                org.eclipse.lsp4j.TextDocumentItem(uri, "java", 1, "class Avant {}"),
            ),
        )
        val publication = boucle.attendrePublication(uri, delaiMs = 3_000)
        assertNotNull(publication, "une fois INITIALIZED, le même didOpen publie des diagnostics (sous 3 s)")
    }

    /** notificationApresShutdownIgnoree — notification post-shutdown ignorée. */
    @Test
    fun notificationApresShutdownIgnoree() {
        val serveur = serveurPur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())
        boucle.client.shutdown().get()
        // didClose après shutdown — ignorée SANS exception (une
        // notification n'a pas de réponse d'erreur).
        boucle.client.textDocumentService.didClose(
            DidCloseTextDocumentParams(TextDocumentIdentifier("file:///fictif/A.java")),
        )
        Thread.sleep(200)
        assertEquals(0, boucle.espion.publications.size, "aucune publication, aucune exception")
    }

    // ----------------------------------------------------------------- //
    // didSave (I5) — republication depuis le buffer vivant
    // ----------------------------------------------------------------- //

    /** didSaveSansIncludeTextRepublieDepuisLeBuffer — didSave sans includeText republie (I5). */
    @Test
    fun didSaveSansIncludeTextRepublieDepuisLeBuffer() {
        val serveur = serveurPur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())

        val uri = dossier.resolve("Save.java").toUri().toString()
        val service = boucle.client.textDocumentService
        service.didOpen(
            DidOpenTextDocumentParams(
                org.eclipse.lsp4j.TextDocumentItem(uri, "java", 0, "class Save {}"),
            ),
        )
        assertTrue(boucle.attendreCumul(1), "la publication du didOpen arrive (drain du debounce)")

        // didChange full-sync en version 1 : un texte avec ERREUR de
        // syntaxe longue (plusieurs published attendus au total).
        val texteCasse = StringBuilder("class Save {\n")
        repeat(30) { texteCasse.append("    int champ$it = «non fermé\n") }
        texteCasse.append("}\n")
        val changement = DidChangeTextDocumentParams()
        changement.textDocument = VersionedTextDocumentIdentifier(uri, 1)
        changement.contentChanges = listOf(TextDocumentContentChangeEvent(texteCasse.toString()))
        service.didChange(changement)
        assertTrue(
            boucle.attendreCumul(2, delaiMs = 5_000),
            "au moins 2 publications cumulées après didChange — trouvé : ${boucle.espion.publications.size}",
        )

        // didSave SANS texte (includeText absent) : NOUVELLE publication
        // portant la version COURANTE (1).
        service.didSave(DidSaveTextDocumentParams(TextDocumentIdentifier(uri)))
        val publication = boucle.attendrePublication(uri, delaiMs = 3_000)
        assertNotNull(publication, "le didSave sans texte déclenche une nouvelle publication (≤ 3 s)")
        assertEquals(1, publication.version, "la publication porte la version COURANTE du buffer (1)")
        assertEquals(uri, publication.uri, "la publication porte l'URI du document")
    }

    /** didSaveSurDocumentJamaisOuvertNeRepubliePas — didSave sans buffer : rien. */
    @Test
    fun didSaveSurDocumentJamaisOuvertNeRepubliePas() {
        val serveur = serveurPur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())
        val uri = dossier.resolve("JamaisOuvert.java").toUri().toString()
        boucle.client.textDocumentService.didSave(
            DidSaveTextDocumentParams(TextDocumentIdentifier(uri)),
        )
        Thread.sleep(200)
        assertEquals(
            0,
            boucle.espion.publications.size,
            "AUCUNE publication (contrôle après 200 ms) — republier sur un buffer vide serait faux",
        )
    }

    // ----------------------------------------------------------------- //
    // Version du serveur (I7) et conventions null/vide (I9)
    // ----------------------------------------------------------------- //

    /** serverInfoVersionEstCelleDuDepot — serverInfo.version = version du dépôt (I7). */
    @Test
    fun serverInfoVersionEstCelleDuDepot() {
        val serveur = serveurPur()
        val resultat = serveur.initialize(parametres()).get()
        val prop = Properties()
        CapacitesServeur::class.java.getResourceAsStream(
            "/jo/codeide/lsp/java/protocole/version.properties",
        )?.use { prop.load(it) }
        val versionRessource = prop.getProperty("version")
        assertNotNull(versionRessource, "la ressource version.properties embarquée existe")
        assertEquals("lspjava", resultat.serverInfo.name, "serverInfo.name = « lspjava »")
        assertNotNull(resultat.serverInfo.version, "version non-null")
        assertTrue(resultat.serverInfo.version != "1.2.0", "la valeur fantôme « 1.2.0 » est proscrite")
        assertEquals(
            versionRessource,
            resultat.serverInfo.version,
            "version EXACTEMENT égale à celle de la ressource du dépôt",
        )
    }

    /** conventionNullVideSelonRepliClient — convention null/vide (I9). */
    @Test
    fun conventionNullVideSelonRepliClient() {
        val serveur = serveurPur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())

        val service = boucle.client.textDocumentService
        val uri = dossier.resolve("Conv.java").toUri().toString()
        service.didOpen(
            DidOpenTextDocumentParams(
                org.eclipse.lsp4j.TextDocumentItem(uri, "java", 0, "class Conv {}"),
            ),
        )

        // hover SANS symbole → null (le client a un repli). Le caret
        // (0, 12) du cahier tombait sur l'accolade fermante — le nœud le
        // plus profond couvrant cet offset est la DÉCLARATION de classe
        // elle-même chez notre moteur (règle fiche 020, N3 : « le nœud le
        // plus profond couvrant le caret » — la classe couvre son
        // accolade) ; la branche « sans symbole » de la convention I9 se
        // vérifie au caret APRÈS la fin du document (aucun nœud ne
        // couvre) : la CONVENTION (null) est verrouillée, pas l'offset du
        // vendored.
        val survol = service.hover(
            org.eclipse.lsp4j.HoverParams(TextDocumentIdentifier(uri), Position(0, 14)),
        ).get()
        assertNull(survol, "hover → null (repli client — aucun symbole au caret)")

        // foldingRange → liste non-null VIDE.
        val plis = service.foldingRange(
            org.eclipse.lsp4j.FoldingRangeRequestParams(TextDocumentIdentifier(uri)),
        ).get()
        assertNotNull(plis, "foldingRange → liste non-null")
        assertTrue(plis.isEmpty(), "foldingRange → liste VIDE (moteur utilisable + aucun pli)")

        // inlayHint (plage 0:0–1:0) → liste non-null VIDE.
        val parametresInlay = org.eclipse.lsp4j.InlayHintParams()
        parametresInlay.textDocument = TextDocumentIdentifier(uri)
        parametresInlay.range = Range(Position(0, 0), Position(1, 0))
        val indices = service.inlayHint(parametresInlay).get()
        assertNotNull(indices, "inlayHint → liste non-null")
        assertTrue(indices.isEmpty(), "inlayHint → liste VIDE")

        // definition, documentSymbol, semanticTokensFull sur un document
        // JAMAIS ouvert → structures VIDES (jamais null, jamais d'exception).
        val jamaisOuvert = dossier.resolve("Jamais.java").toUri().toString()
        val definition = service.definition(
            org.eclipse.lsp4j.DefinitionParams(TextDocumentIdentifier(jamaisOuvert), Position(0, 0)),
        ).get()
        assertNotNull(definition, "definition → réponse non-null")
        assertTrue(definition.left.isEmpty(), "definition → liste vide")

        val symboles = service.documentSymbol(
            org.eclipse.lsp4j.DocumentSymbolParams(TextDocumentIdentifier(jamaisOuvert)),
        ).get()
        assertNotNull(symboles, "documentSymbol → liste non-null")
        assertTrue(symboles.isEmpty(), "documentSymbol → liste vide")

        val jetons = service.semanticTokensFull(
            org.eclipse.lsp4j.SemanticTokensParams(TextDocumentIdentifier(jamaisOuvert)),
        ).get()
        assertNotNull(jetons, "semanticTokens → réponse non-null")
        assertTrue(
            jetons.data == null || jetons.data.isEmpty(),
            "semanticTokens → data null ou vide",
        )
    }

    /** renameSansSymboleErreurTypeeSansClasspath — rename sans cible : erreur typée (I8). */
    @Test
    fun renameSansSymboleErreurTypeeSansClasspath() {
        val serveur = serveurPur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(parametres()).get()
        boucle.client.initialized(InitializedParams())

        val service = boucle.client.textDocumentService
        val uri = dossier.resolve("R.java").toUri().toString()
        service.didOpen(
            DidOpenTextDocumentParams(
                org.eclipse.lsp4j.TextDocumentItem(uri, "java", 0, "class R {}"),
            ),
        )
        // rename au caret (0, 3) — sur le mot-clé « class », sans classpath.
        val params = org.eclipse.lsp4j.RenameParams(
            TextDocumentIdentifier(uri),
            Position(0, 3),
            "Nouveau",
        )
        try {
            service.rename(params).get()
            fail("le rename sans symbole devait être rejeté")
        } catch (e: ExecutionException) {
            val reponse = e.cause as? ResponseErrorException
                ?: fail("la cause devait être ResponseErrorException — trouvé : ${e.cause}")
            assertEquals(
                -32803,
                reponse.responseError?.code,
                "code d'erreur exact -32803 (RequestFailed)",
            )
            assertTrue(
                (reponse.responseError?.message ?: "").contains("Aucun symbole renommable"),
                "message FR contenant « Aucun symbole renommable » — trouvé : « ${reponse.responseError?.message} »",
            )
        }
    }
}

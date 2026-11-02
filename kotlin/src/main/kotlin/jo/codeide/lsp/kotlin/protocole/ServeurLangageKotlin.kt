// CodeIDE — le serveur de langage Kotlin (module :kotlin, étage 6 vague 6e
// — ADR 0118 option 1, miroir :java N8 : le MODÈLE architectural Java
// transposé, jamais son code). Code NEUF jjoblab.
package jo.codeide.lsp.kotlin.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreActionKotlin
import jo.codeide.lsp.kotlin.api.GenreIndiceKotlin
import jo.codeide.lsp.kotlin.api.GenrePlagePliable
import jo.codeide.lsp.kotlin.api.GenreSurlignageKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.api.SessionLangageKotlin
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintKind
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService

/**
 * Le serveur de langage Kotlin (étage 6 — ADR 0118 option 1) :
 * l'implémentation des trois interfaces lsp4j ([LanguageServer],
 * [TextDocumentService], [WorkspaceService]) au-dessus d'une SESSION du
 * moteur (le chemin de production D6 — le SPI, jamais une classe interne).
 *
 * **Cycle de vie** : UNINITIALIZED → INITIALIZING (après la RÉPONSE de
 * `initialize`) → INITIALIZED (notification `initialized`) → SHUTDOWN →
 * EXITED. Le mode in-process (défaut) ne tue JAMAIS la JVM sur `exit` —
 * le mode autonome l'arrête.
 *
 * **Gardes protocolaires** (miroir N8) :
 *  - I2 — toute requête AVANT `initialized` est rejetée SYNCHRONOUSMENT
 *    par `ServerNotInitialized` (-32002) ;
 *  - I2 — toute requête APRÈS `shutdown` est rejetée par `InvalidRequest`
 *    (-32600) ;
 *  - I3 — un second `initialize` est rejeté par `InvalidRequest` (-32600),
 *    le message contient « initialize » ;
 *  - les NOTIFICATIONS hors fenêtre (avant initialize comme après
 *    shutdown) sont IGNORÉES silencieusement — une notification n'a pas
 *    de réponse d'erreur, la spec demande d'ignorer.
 *
 * **Synchronisation** : plein texte — `didOpen`/`didChange`/`didSave`/
 * `didClose` maintiennent le magasin copy-on-write ([EtatsDocuments]) ET
 * la session moteur (le miroir exact : chaque mutation de buffer devient
 * une mutation de document de session) ; les publications de diagnostics
 * partent en asynchrone débouncées ([PublicateurDiagnostics]).
 *
 * **Époque copy-on-write** (« 0 réponse périmée ») : les requêtes de
 * document calculent sur l'instantané VIVANT et VÉRIFIENT la fraîcheur
 * avant de répondre — une réponse calculée contre un texte révolu est
 * RECALCULÉE sur le nouveau (borné, [EtatsDocuments.calculerFraiche]).
 *
 * **Racines sources** : la racine annoncée à `initialize` nourrit le scan
 * paresseux de la session (le classpath des sources du projet) ; le
 * rafraîchissement des fichiers surveillés repart sur un fil dédié (le
 * fil JSON-RPC n'attend jamais un scan).
 *
 * @param fabriqueSession la fabrique de la session pour la racine annoncée
 *   à `initialize` (null = protocole pur, sans racine) ;
 * @param rappelArret le rappel d'arrêt (shutdown puis exit), ou null.
 */
public class ServeurLangageKotlin(
    private val fabriqueSession: (racine: Path?) -> SessionLangageKotlin? = { racine ->
        val session = ServiceMoteurLangageKotlin.unique.creerSession()
        racine?.let {
            session.definirClasspath(
                ClasspathKotlin(entrees = emptyList(), racinesSources = listOf(it.toString())),
            )
        }
        session
    },
    private val rappelArret: Runnable? = null,
) : LanguageServer, TextDocumentService, WorkspaceService {

    /** La machine à états du cycle de vie (observable par l'hôte/tests). */
    public val cycle: CycleVie = CycleVie()

    /** Le magasin des documents ouverts (copy-on-write + époques). */
    private val etats: EtatsDocuments = EtatsDocuments()

    /** Le client LSP (branché par le lanceur — `connect`). */
    @Volatile
    private var client: LanguageClient? = null

    /** La session du moteur (construite à initialize). */
    @Volatile
    private var session: SessionLangageKotlin? = null

    /** Le publicateur asynchrone des diagnostics. */
    private var publicateur: PublicateurDiagnostics? = null

    /** Le fil dédié des rafraîchissements du scan (fichiers surveillés). */
    private val filRafraichissement = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lspkotlin-rafraichissement").apply { isDaemon = true }
    }

    /** Mode autonome : exit() tue le processus (vrai) ou pas (faux, défaut). */
    @Volatile
    private var modeAutonome: Boolean = false

    /**
     * L'état exposé du cycle de vie — raccourci de [cycle.etat].
     *
     * @return l'état courant.
     */
    public fun etat(): CycleVie.Etat = cycle.etat()

    /**
     * Le mode autonome (mode in-process, défaut des tests : `exit` ne tue
     * pas la JVM hôte).
     *
     * @param valeur vrai pour le mode processus autonome.
     */
    public fun setStandaloneMode(valeur: Boolean) {
        modeAutonome = valeur
    }

    /**
     * Branche le client LSP (appelé par le lanceur après construction).
     *
     * @param client le client à prévenir (publications, messages).
     */
    public fun connect(client: LanguageClient) {
        this.client = client
    }

    // ----------------------------------------------------------------- //
    // Cycle de vie : initialize / initialized / shutdown / exit
    // ----------------------------------------------------------------- //

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        // I3 — initialize RÉENTRANT : rejet InvalidRequest, message
        // contenant « initialize » (code exact -32600).
        if (cycle.etat() != CycleVie.Etat.UNINITIALIZED) {
            return refuse(
                ResponseErrorCode.InvalidRequest,
                "initialize a déjà été appelé — le protocole LSP n'autorise qu'un seul initialize par session",
            )
        }
        // La session naît de la racine annoncée (null → protocole pur).
        val racine = params.rootUri?.let { ConvertisseurLsp.cheminDeUri(it) }
            ?.let { Path.of(it) }
            ?.takeIf { Files.isDirectory(it) }
        session = fabriqueSession(racine)
        session?.let { s ->
            publicateur = PublicateurDiagnostics(clientOuMuet(), s, etats)
            // L'amorce du scan part sur le fil dédié (le fil JSON-RPC
            // n'attend JAMAIS un scan — la palette d'espace s'enrichit à
            // la publication).
            filRafraichissement.execute { s.rafraichirSources() }
        }
        cycle.surInitialize()
        return CompletableFuture.completedFuture(CapacitesServeur.reponseInitialize())
    }

    override fun initialized(params: org.eclipse.lsp4j.InitializedParams) {
        // La transition finale ; les notifications didOpen deviennent
        // efficaces à partir d'ici (garde ci-dessous).
        cycle.surInitialized()
    }

    override fun shutdown(): CompletableFuture<Any> {
        cycle.surShutdown()
        rappelArret?.run()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        cycle.surExit()
        publicateur?.fermer()
        filRafraichissement.shutdownNow()
        session?.close()
        // Second appel du rappel (confirmation).
        rappelArret?.run()
        // Mode autonome uniquement : le processus s'arrête (en mode
        // in-process, JAMAIS de System.exit).
        if (modeAutonome) {
            Runtime.getRuntime().halt(0)
        }
    }

    override fun getTextDocumentService(): TextDocumentService = this

    override fun getWorkspaceService(): WorkspaceService = this

    // ----------------------------------------------------------------- //
    // Gardes I2/I3 et refus typés
    // ----------------------------------------------------------------- //

    /**
     * La garde de requête (I2) : avant `initialized` → rejet SYNCHRONE
     * ServerNotInitialized (-32002) ; après `shutdown` → rejet
     * InvalidRequest (-32600). Rend null si la requête est permise.
     */
    private fun <T> garde(): CompletableFuture<T>? {
        return when (cycle.etat()) {
            CycleVie.Etat.INITIALIZED -> null
            CycleVie.Etat.UNINITIALIZED, CycleVie.Etat.INITIALIZING -> refuse(
                ResponseErrorCode.ServerNotInitialized,
                "requête refusée : le serveur n'est pas encore initialisé (notification initialized attendue)",
            )
            CycleVie.Etat.SHUTDOWN, CycleVie.Etat.EXITED -> refuse(
                ResponseErrorCode.InvalidRequest,
                "requête refusée : le serveur est arrêté (shutdown reçu)",
            )
        }
    }

    /** Un futur IMMÉDIATEMENT en erreur typée (rejet synchrone — I2). */
    private fun <T> refuse(code: ResponseErrorCode, message: String): CompletableFuture<T> {
        val futur = CompletableFuture<T>()
        futur.completeExceptionally(ResponseErrorException(ResponseError(code, message, null)))
        return futur
    }

    /** Le client branché (ou un client muet si le lanceur n'a pas connecté). */
    private fun clientOuMuet(): LanguageClient = client ?: ClientMuet

    /** Un client muet (aucun lanceur — les publications se perdent honnêtement). */
    private object ClientMuet : LanguageClient {
        override fun telemetryEvent(object_: Any?) = Unit
        override fun publishDiagnostics(diagnostics: org.eclipse.lsp4j.PublishDiagnosticsParams?) = Unit
        override fun showMessage(messageParams: org.eclipse.lsp4j.MessageParams?) = Unit
        override fun showMessageRequest(
            requestParams: org.eclipse.lsp4j.ShowMessageRequestParams?,
        ): CompletableFuture<org.eclipse.lsp4j.MessageActionItem> {
            @Suppress("UNCHECKED_CAST")
            return CompletableFuture.completedFuture(null) as CompletableFuture<org.eclipse.lsp4j.MessageActionItem>
        }
        override fun logMessage(message: org.eclipse.lsp4j.MessageParams?) = Unit
        override fun logTrace(params: org.eclipse.lsp4j.LogTraceParams?) = Unit
    }

    // ----------------------------------------------------------------- //
    // Synchronisation des documents (plein texte — fiche 059 Java)
    // ----------------------------------------------------------------- //

    override fun didOpen(params: DidOpenTextDocumentParams) {
        if (!cycle.requetesPermises()) return // notification hors fenêtre : ignorée
        val document = params.textDocument
        etats.ouvrir(document.uri, document.text, document.version.toLong())
        session?.ouvrir(document.uri, document.text)
        publicateur?.demander(document.uri)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        if (!cycle.requetesPermises()) return
        val identifiant = params.textDocument
        // Sync PLEINE : le dernier changement porte le texte intégral.
        val texte = params.contentChanges.lastOrNull()?.text ?: return
        etats.changer(identifiant.uri, texte, identifiant.version.toLong())
        session?.modifier(identifiant.uri, texte)
        publicateur?.demander(identifiant.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        if (!cycle.requetesPermises()) return
        // didSave SANS includeText : republier depuis le buffer VIVANT (la
        // publication porte la version COURANTE). Un document JAMAIS ouvert
        // ne republie pas.
        if (etats.instantane(params.textDocument.uri) == null) return
        etats.sauver(params.textDocument.uri, null)
        params.text?.let { session?.modifier(params.textDocument.uri, it) }
        publicateur?.demander(params.textDocument.uri)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        if (!cycle.requetesPermises()) return
        etats.fermer(params.textDocument.uri)
        session?.fermer(params.textDocument.uri)
    }

    // ----------------------------------------------------------------- //
    // Espace de travail
    // ----------------------------------------------------------------- //

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        // Aucune configuration dynamique servie — tolérée, ignorée.
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        if (!cycle.requetesPermises()) return
        // Les fichiers surveillés ont changé sur le DISQUE : le scan des
        // racines se rafraîchit sur le FIL DÉDIÉ (le fil JSON-RPC
        // n'attend jamais un scan ; la session ne sert le nouveau snapshot
        // qu'à l'issue — jamais faux en chemin).
        session?.let { s -> filRafraichissement.execute { s.rafraichirSources() } }
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        garde<Any>()?.let { return it }
        return refuse(ResponseErrorCode.MethodNotFound, "commande inconnue : ${params.command}")
    }

    override fun symbol(
        params: WorkspaceSymbolParams,
    ): CompletableFuture<Either<List<SymbolInformation>, List<org.eclipse.lsp4j.WorkspaceSymbol>>> {
        garde<Either<List<SymbolInformation>, List<org.eclipse.lsp4j.WorkspaceSymbol>>>()?.let { return it }
        // Requête vide → tout ; sans correspondance → liste VIDE (I9) ;
        // la réponse prend la forme GAUCHE (liste) — jamais null.
        val symboles = session?.symbolesEspace(params.query ?: "").orEmpty()
        val rendus = symboles.map { symbole ->
            // La plage du nom si le document est OUVERT (le repère y
            // vit), la position d'origine sinon (le client n'ouvre pas).
            val plage = symbole.plageNom?.let { plage ->
                etats.instantane(symbole.uri)?.let { instantane ->
                    ConvertisseurLsp.repere(instantane.texte).plage(plage)
                }
            } ?: Range(Position(0, 0), Position(0, 0))
            SymbolInformation(
                symbole.nom,
                ConvertisseurLsp.kindSymbole(symbole.genre),
                Location(symbole.uri, plage),
                symbole.uri,
            )
        }
        return CompletableFuture.completedFuture(Either.forLeft(rendus))
    }

    // ----------------------------------------------------------------- //
    // Les aides de requête (instantané frais + repère + offset)
    // ----------------------------------------------------------------- //

    /**
     * Le calcul FRAIS d'un document (« 0 réponse périmée ») : le calcul
     * part de l'instantané VIVANT, se rejoue si le document a muté
     * PENDANT (borné à 3 relances).
     */
    private fun <T> calculFraiche(uri: String, calcul: (String) -> T): T? =
        etats.calculerFraiche(uri) { instantane -> calcul(instantane.texte) }

    /** L'offset d'une position LSP dans le texte VIVANT du document. */
    private fun offsetDe(texte: String, position: Position): Int =
        ConvertisseurLsp.repere(texte).offset(position)

    // ----------------------------------------------------------------- //
    // Survol, complétion, signature
    // ----------------------------------------------------------------- //

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        garde<Hover>()?.let { return it }
        val uri = params.textDocument.uri
        // Convention I9 : hover → null quand il n'y a rien (le client a un
        // repli) — jamais une structure vide.
        val survol = calculFraiche(uri) { texte ->
            session?.survol(uri, offsetDe(texte, params.position))
        }
        return CompletableFuture.completedFuture(survol?.let(::survolLsp))
    }

    /** Le Hover LSP d'un survol moteur (markdown ```kotlin + KDoc sinon plaintext). */
    private fun survolLsp(survol: jo.codeide.lsp.kotlin.api.SurvolKotlin): Hover {
        val valeur = if (survol.documentation != null) {
            MarkupContent("markdown", "```kotlin\n${survol.signature}\n```\n${survol.documentation}")
        } else {
            MarkupContent("plaintext", survol.signature)
        }
        return Hover(valeur)
    }

    override fun completion(
        params: CompletionParams,
    ): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        garde<Either<List<CompletionItem>, CompletionList>>()?.let { return it }
        val uri = params.textDocument.uri
        val reponse = calculFraiche(uri) { texte ->
            session?.completionArrangee(uri, offsetDe(texte, params.position))
        }
        val liste = CompletionList()
        liste.setIsIncomplete(false)
        liste.items = reponse?.propositions.orEmpty().map { proposition ->
            val item = CompletionItem()
            item.label = proposition.libelle
            item.kind = ConvertisseurLsp.kindCompletion(proposition.nature)
            proposition.detail?.let { item.detail = it }
            // filterText STRICTEMENT ÉGAL au label (miroir I12 Java).
            item.filterText = proposition.libelle
            item.insertText = proposition.texteInsertion ?: proposition.libelle
            if (proposition.texteInsertion != null && proposition.texteInsertion.contains("$")) {
                item.insertTextFormat = InsertTextFormat.Snippet
            }
            proposition.fqcnImport?.let { fqcn -> item.data = mapOf("import" to fqcn) }
            item
        }
        return CompletableFuture.completedFuture(Either.forRight(liste))
    }

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        garde<CompletionItem>()?.let { return it }
        // Aucune résolution différée : l'item est complet tel quel.
        return CompletableFuture.completedFuture(unresolved)
    }

    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp> {
        garde<SignatureHelp>()?.let { return it }
        val uri = params.textDocument.uri
        val aide = calculFraiche(uri) { texte ->
            session?.signature(uri, offsetDe(texte, params.position))
        }
        val rendu = SignatureHelp()
        if (aide != null) {
            rendu.signatures = aide.signatures.map { candidate ->
                val information = SignatureInformation()
                information.label = candidate.etiquette
                information.parameters = candidate.parametres.map { ParameterInformation(it) }
                candidate.documentation?.let { documentation ->
                    information.documentation = Either.forLeft<String, MarkupContent>(documentation)
                }
                information
            }
            rendu.activeSignature = aide.signatureActive
            rendu.activeParameter = aide.parametreActif
        }
        return CompletableFuture.completedFuture(rendu)
    }

    // ----------------------------------------------------------------- //
    // Navigation
    // ----------------------------------------------------------------- //

    override fun definition(
        params: org.eclipse.lsp4j.DefinitionParams,
    ): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        garde<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()?.let { return it }
        val uri = params.textDocument.uri
        val cibles = calculFraiche(uri) { texte ->
            session?.definition(uri, offsetDe(texte, params.position)).orEmpty()
        }.orEmpty()
        return CompletableFuture.completedFuture(Either.forLeft(locations(cibles, uri)))
    }

    override fun typeDefinition(
        params: TypeDefinitionParams,
    ): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        garde<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()?.let { return it }
        val uri = params.textDocument.uri
        val cibles = calculFraiche(uri) { texte ->
            session?.typeDefinition(uri, offsetDe(texte, params.position)).orEmpty()
        }.orEmpty()
        return CompletableFuture.completedFuture(Either.forLeft(locations(cibles, uri)))
    }

    override fun implementation(
        params: org.eclipse.lsp4j.ImplementationParams,
    ): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        garde<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()?.let { return it }
        val uri = params.textDocument.uri
        val cibles = calculFraiche(uri) { texte ->
            session?.implementations(uri, offsetDe(texte, params.position)).orEmpty()
        }.orEmpty()
        return CompletableFuture.completedFuture(Either.forLeft(locations(cibles, uri)))
    }

    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
        garde<List<Location>>()?.let { return it }
        val uri = params.textDocument.uri
        val inclureDeclaration = params.context?.isIncludeDeclaration ?: false
        val cibles = calculFraiche(uri) { texte ->
            session?.references(uri, offsetDe(texte, params.position), inclureDeclaration).orEmpty()
        }.orEmpty()
        return CompletableFuture.completedFuture(locations(cibles, uri))
    }

    override fun documentHighlight(params: org.eclipse.lsp4j.DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> {
        garde<List<DocumentHighlight>>()?.let { return it }
        val uri = params.textDocument.uri
        val surlignages = calculFraiche(uri) { texte ->
            session?.surlignagesDocument(uri, offsetDe(texte, params.position)).orEmpty()
        }.orEmpty()
        val uriCourante = uri
        val rendus = surlignages.mapNotNull { surlignage ->
            val instantane = etats.instantane(uriCourante) ?: return@mapNotNull null
            val repere = ConvertisseurLsp.repere(instantane.texte)
            DocumentHighlight(
                repere.plage(surlignage.plage),
                if (surlignage.genre == GenreSurlignageKotlin.ECRITURE) {
                    DocumentHighlightKind.Write
                } else {
                    DocumentHighlightKind.Read
                },
            )
        }
        return CompletableFuture.completedFuture(rendus)
    }

    override fun documentSymbol(
        params: DocumentSymbolParams,
    ): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        garde<List<Either<SymbolInformation, DocumentSymbol>>>()?.let { return it }
        val uri = params.textDocument.uri
        val symboles = calculFraiche(uri) { _ ->
            session?.symboles(uri).orEmpty()
        }.orEmpty()
        val instantane = etats.instantane(uri)
        val repere = instantane?.let { ConvertisseurLsp.repere(it.texte) }
        val rendus = symboles.map { symbole ->
            val document = DocumentSymbol(
                symbole.nom,
                ConvertisseurLsp.kindSymbole(symbole.genre),
                repere?.plage(symbole.plage) ?: Range(Position(0, 0), Position(0, 0)),
                repere?.plage(symbole.plageNom) ?: Range(Position(0, 0), Position(0, 0)),
            )
            Either.forRight<SymbolInformation, DocumentSymbol>(document)
        }
        return CompletableFuture.completedFuture(rendus)
    }

    override fun selectionRange(params: SelectionRangeParams): CompletableFuture<List<SelectionRange>> {
        garde<List<SelectionRange>>()?.let { return it }
        val uri = params.textDocument.uri
        val chaines = calculFraiche(uri) { texte ->
            val offsets = params.positions.map { position -> offsetDe(texte, position) }
            session?.selections(uri, offsets).orEmpty()
        }.orEmpty()
        val instantane = etats.instantane(uri)
        val repere = instantane?.let { ConvertisseurLsp.repere(it.texte) }
        val rendues = chaines.map { chaine ->
            // Les maillons du PROFOND au fichier : la chaîne se construit
            // de l'EXTERNAL (le fichier, sans parent) vers le PROFOND —
            // chaque maillon porte le précédent en PARENT.
            var construite: SelectionRange? = null
            for (plage in chaine.plages.asReversed()) {
                val rendue = SelectionRange(
                    repere?.plage(plage) ?: Range(Position(0, 0), Position(0, 0)),
                    construite,
                )
                construite = rendue
            }
            construite ?: SelectionRange(Range(Position(0, 0), Position(0, 0)), null)
        }
        return CompletableFuture.completedFuture(rendues)
    }

    /** Les localisations LSP d'emplacements moteur (le repère du document courant). */
    private fun locations(cibles: List<jo.codeide.lsp.kotlin.api.EmplacementKotlin>, uriCourante: String): List<Location> {
        return cibles.map { cible ->
            val instantane = etats.instantane(cible.uri)
                ?: etats.instantane(uriCourante)
            val repere = instantane?.let { ConvertisseurLsp.repere(it.texte) }
            Location(
                cible.uri,
                repere?.plage(cible.plage) ?: Range(Position(0, 0), Position(0, 0)),
            )
        }
    }

    // ----------------------------------------------------------------- //
    // Édition assistée : formatage, pliage, actions, indices
    // ----------------------------------------------------------------- //

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        garde<List<TextEdit>>()?.let { return it }
        val uri = params.textDocument.uri
        val taille = params.options?.tabSize ?: 4
        val editions = calculFraiche(uri) { _ ->
            session?.formatage(uri, taille).orEmpty()
        }.orEmpty()
        return CompletableFuture.completedFuture(editionsLsp(editions, uri))
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> {
        garde<List<TextEdit>>()?.let { return it }
        val uri = params.textDocument.uri
        val taille = params.options?.tabSize ?: 4
        val editions = calculFraiche(uri) { texte ->
            val repere = ConvertisseurLsp.repere(texte)
            val debut = repere.offset(params.range.start)
            val fin = repere.offset(params.range.end)
            session?.formatagePlage(uri, debut, fin, taille).orEmpty()
        }.orEmpty()
        return CompletableFuture.completedFuture(editionsLsp(editions, uri))
    }

    /** Les éditions LSP d'une liste moteur (le repère du document courant). */
    private fun editionsLsp(editions: List<jo.codeide.lsp.kotlin.api.EditionTexteKotlin>, uri: String): List<TextEdit> {
        val instantane = etats.instantane(uri) ?: return emptyList()
        val repere = ConvertisseurLsp.repere(instantane.texte)
        return ConvertisseurLsp.editions(editions, repere)
    }

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> {
        garde<List<TextEdit>>()?.let { return it }
        // NON annoncée (matrice Kotlin : ❌) — la requête reste TOLÉRANTE
        // (liste vide, jamais d'exception).
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
        garde<List<FoldingRange>>()?.let { return it }
        val uri = params.textDocument.uri
        val plages = calculFraiche(uri) { _ ->
            session?.plagesPliables(uri).orEmpty()
        }.orEmpty()
        val instantane = etats.instantane(uri)
        val repere = instantane?.let { ConvertisseurLsp.repere(it.texte) }
        val rendues = plages.map { plage ->
            val debut = repere?.position(plage.plage.debut) ?: Position(0, 0)
            val fin = repere?.position(plage.plage.fin) ?: Position(0, 0)
            FoldingRange(debut.line, fin.line).apply {
                // Le genre neutre du moteur : les commentaires et imports
                // portent leur kind LSP, le reste reste SANS kind.
                kind = when (plage.genre) {
                    GenrePlagePliable.KDOC -> "comment"
                    GenrePlagePliable.IMPORTS -> "imports"
                    else -> null
                }
            }
        }
        return CompletableFuture.completedFuture(rendues)
    }

    override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> {
        garde<List<InlayHint>>()?.let { return it }
        val uri = params.textDocument.uri
        val indices = calculFraiche(uri) { texte ->
            val repere = ConvertisseurLsp.repere(texte)
            val debut = params.range?.let { repere.offset(it.start) } ?: 0
            val fin = params.range?.let { repere.offset(it.end) } ?: texte.length
            session?.indices(uri, debut, fin).orEmpty()
        }.orEmpty()
        val instantane = etats.instantane(uri)
        val repere = instantane?.let { ConvertisseurLsp.repere(it.texte) }
        val rendus = indices.map { indice ->
            val rendu = InlayHint()
            rendu.position = repere?.position(indice.debut) ?: Position(0, 0)
            rendu.label = Either.forLeft<String, List<org.eclipse.lsp4j.InlayHintLabelPart>>(indice.libelle)
            rendu.kind = when (indice.genre) {
                GenreIndiceKotlin.PARAMETRE -> InlayHintKind.Parameter
                GenreIndiceKotlin.TYPE, GenreIndiceKotlin.LAMBDA -> InlayHintKind.Type
            }
            rendu
        }
        return CompletableFuture.completedFuture(rendus)
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        garde<SemanticTokens>()?.let { return it }
        // NON annoncée (matrice Kotlin : ❌ — distinction non fiable au
        // niveau parseur, D3) — la requête reste TOLÉRANTE (data vide).
        return CompletableFuture.completedFuture(SemanticTokens())
    }

    // ----------------------------------------------------------------- //
    // Renommage
    // ----------------------------------------------------------------- //

    override fun prepareRename(
        params: PrepareRenameParams,
    ): CompletableFuture<Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior>> {
        garde<Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior>>()?.let { return it }
        val uri = params.textDocument.uri
        val preparation = calculFraiche(uri) { texte ->
            session?.plageRenommable(uri, offsetDe(texte, params.position))
        }
        // Null = pas renommable à cette position (le client désactive le
        // rename) — la forme RENDUE est le couple plage + placeholder.
        return CompletableFuture.completedFuture(
            preparation?.let {
                val instantane = etats.instantane(uri)
                val repere = instantane?.let { texte -> ConvertisseurLsp.repere(texte.texte) }
                Either3.forSecond(
                    PrepareRenameResult(
                        repere?.plage(it.plage) ?: Range(Position(0, 0), Position(0, 0)),
                        it.placeholder,
                    ),
                )
            },
        )
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
        garde<WorkspaceEdit>()?.let { return it }
        val uri = params.textDocument.uri
        val emplacements = calculFraiche(uri) { texte ->
            session?.renommage(uri, offsetDe(texte, params.position)).orEmpty()
        }.orEmpty()
        val edit = WorkspaceEdit()
        if (emplacements.isEmpty()) {
            // Sans cible prouvée : un renommage VIDE (l'hôte en fait une
            // erreur typée) — jamais un renommage partiel silencieux.
            return CompletableFuture.completedFuture(edit)
        }
        val parUri = HashMap<String, MutableList<TextEdit>>()
        for (emplacement in emplacements) {
            val instantane = etats.instantane(emplacement.uri)
            val repere = instantane?.let { ConvertisseurLsp.repere(it.texte) } ?: continue
            parUri.getOrPut(emplacement.uri) { mutableListOf() }.add(
                TextEdit(repere.plage(emplacement.plage), params.newName),
            )
        }
        edit.changes = parUri
        return CompletableFuture.completedFuture(edit)
    }

    // ----------------------------------------------------------------- //
    // Actions de code
    // ----------------------------------------------------------------- //

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
        garde<List<Either<Command, CodeAction>>>()?.let { return it }
        val uri = params.textDocument.uri
        val plans = calculFraiche(uri) { texte ->
            val repere = ConvertisseurLsp.repere(texte)
            val offset = repere.offset(params.range.start)
            session?.actions(uri, offset).orEmpty()
        }.orEmpty()
        val rendus = plans.map { plan ->
            val action = CodeAction()
            action.title = plan.titre
            action.kind = if (plan.genre == GenreActionKotlin.RAPIDE) CodeActionKind.QuickFix else CodeActionKind.Source
            val edit = WorkspaceEdit()
            val parUri = HashMap<String, MutableList<TextEdit>>()
            for (fichier in plan.editions) {
                val instantane = etats.instantane(fichier.uri)
                val repere = instantane?.let { ConvertisseurLsp.repere(it.texte) } ?: continue
                parUri.getOrPut(fichier.uri) { mutableListOf() }
                    .addAll(ConvertisseurLsp.editions(fichier.editions, repere))
            }
            edit.changes = parUri
            action.edit = edit
            Either.forRight<Command, CodeAction>(action)
        }
        return CompletableFuture.completedFuture(rendus)
    }

    // ----------------------------------------------------------------- //
    // Les requêtes NON annoncées (tolérantes — matrice honnête)
    // ----------------------------------------------------------------- //

    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> {
        garde<List<CodeLens>>()?.let { return it }
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
        garde<CodeLens>()?.let { return it }
        return CompletableFuture.completedFuture(unresolved)
    }

    override fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): CompletableFuture<List<TypeHierarchyItem>> {
        garde<List<TypeHierarchyItem>>()?.let { return it }
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        garde<List<TypeHierarchyItem>>()?.let { return it }
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        garde<List<TypeHierarchyItem>>()?.let { return it }
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<List<CallHierarchyItem>> {
        garde<List<CallHierarchyItem>>()?.let { return it }
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture<List<CallHierarchyIncomingCall>> {
        garde<List<CallHierarchyIncomingCall>>()?.let { return it }
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture<List<CallHierarchyOutgoingCall>> {
        garde<List<CallHierarchyOutgoingCall>>()?.let { return it }
        return CompletableFuture.completedFuture(emptyList())
    }
}

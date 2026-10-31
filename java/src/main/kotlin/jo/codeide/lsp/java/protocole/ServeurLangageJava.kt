package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import jo.codeide.lsp.java.EnvironnementCompilation
import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.completion.ChaineClassement
import jo.codeide.lsp.java.completion.ContexteClassement
import jo.codeide.lsp.java.completion.CorrespondanceGraduee
import jo.codeide.lsp.java.completion.FrontiereRpc
import jo.codeide.lsp.java.completion.PeseurCorrespondance
import jo.codeide.lsp.java.completion.PeseurLongueur
import jo.codeide.lsp.java.completion.PeseurNature
import jo.codeide.lsp.java.renommage.CapsEspaceDeTravailClient
import jo.codeide.lsp.java.renommage.RenommageSansCible
import jo.codeide.lsp.java.renommage.ServiceRenommage
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
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
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService

/**
 * Le serveur de langage Java (étage N8 — cycle de vie LSP, protocole
 * riche & intégration) : l'implémentation complète des trois interfaces
 * lsp4j ([LanguageServer], [TextDocumentService], [WorkspaceService])
 * PLUS les méthodes expérimentales ([ServiceExperimental]).
 *
 * **Cycle de vie** (fiche 055) : UNINITIALIZED → INITIALIZING
 * (après la RÉPONSE de `initialize`) → INITIALIZED (notification
 * `initialized`) → SHUTDOWN → EXITED. Le mode in-process
 * ([modeAutonome] faux, défaut) ne tue JAMAIS la JVM sur `exit` — le
 * mode autonome (vrai) appelle `System.exit` après le changement
 * d'état (l'intégration Android et les tests exigent le premier).
 *
 * **Gardes protocolaires** (fiche 059) :
 *  - I2 — toute requête AVANT `initialized` est rejetée
 *    **SYNCHRONOUSLY** par `ServerNotInitialized` (-32002) : le futur
 *    est déjà en erreur à l'entrée de la méthode, jamais différé ;
 *  - I2 — toute requête APRÈS `shutdown` est rejetée par
 *    `InvalidRequest` (-32600) ;
 *  - I3 — un second `initialize` est rejeté par `InvalidRequest`
 *    (-32600), le message contient « initialize » ;
 *  - les NOTIFICATIONS hors fenêtre (avant initialize comme après
 *    shutdown) sont IGNORÉES silencieusement — une notification n'a pas
 *    de réponse d'erreur, la spec demande d'ignorer, pas de rejeter.
 *
 * **Synchronisation** (fiche 059) : plein texte — `didOpen`/`didChange`
 * (full sync)/`didSave`/`didClose` maintiennent le magasin
 * copy-on-write ([EtatsDocuments]) ; les publications de diagnostics
 * partent en asynchrone débouncées ([PublicateurDiagnostics]) ; le
 * renommage reçoit le miroir des versions ([ServiceRenommage]).
 *
 * **Époque copy-on-write** (design intégré, plan §4 N8 : « 0 réponse
 * périmée ») : les requêtes de document calculent sur l'instantané
 * VIVANT et VÉRIFIENT la fraîcheur avant de répondre — une réponse
 * calculée contre un texte révolu est RECALCULÉE sur le nouveau (borné,
 * cf. [EtatsDocuments.calculerFraiche]).
 *
 * **Rappel d'arrêt** (fiche 056 S1-d) : le registre des serveurs vivants
 * repose sur un rappel déclenché à `shutdown` PUIS confirmé à `exit` —
 * 0 appel après initialize, 1 après shutdown, 2 après le cycle complet.
 *
 * **Régimes du moteur** (INTEGRATION-CODEIDE §3.3, option B — routeur
 * multi-modules AU PROTOCOLE) : DEUX constructeurs, DEUX régimes.
 *
 *  - **régime FIGÉ** (constructeur principal, fabrique) : le moteur
 *    naît de la racine annoncée à `initialize` et ne bouge plus — le
 *    régime des tests du cahier et du lanceur stdio autonome ;
 *  - **régime VIVANT** (constructeur `environnement`) : le serveur vit
 *    sur un [EnvironnementCompilation] possédé par l'HÔTE — UNE session
 *    LSP Java par projet, le moteur résolu PAR REQUÊTE par le ROUTAGE
 *    fichier → module (fiche 047, relu à CHAQUE requête), les
 *    reconfigurations de l'hôte remplacent les CONTEXTES à chaud
 *    (sémantiques replaceAll fiche 034 : remplacement effectif = moteur
 *    du module reconstruit, redondant = no-op) — JAMAIS de
 *    reconstruction du serveur ni re-initialize. L'invariant ADR 0211
 *    (« nourrissage moteur unique par sync ») tient par construction :
 *    l'hôte nourrit l'environnement, le serveur route. Le serveur NE
 *    DISPOSE PAS l'environnement (il ne le possède pas — l'hôte le
 *    libère à la fermeture de la session, miroir exact du vendored
 *    `JavaLanguageServer`).
 *
 * @param fabriqueMoteur la fabrique du moteur pour la racine annoncée à
 *   `initialize` (null = serveur SANS environnement de compilation —
 *   protocole pur, fiche 055 ; le défaut : un moteur sur la racine,
 *   sans classpath additionnel).
 * @param rappelArret le rappel d'arrêt (shutdown puis exit), ou null.
 */
public class ServeurLangageJava(
    private val fabriqueMoteur: (racine: Path?) -> MoteurJava? = { racine ->
        racine?.let { MoteurJava(racines = listOf(it)) }
    },
    private val rappelArret: Runnable? = null,
) : LanguageServer, TextDocumentService, WorkspaceService, ServiceExperimental {

    /**
     * Le régime VIVANT (INTEGRATION-CODEIDE §3.3, option B) : le serveur
     * sur l'environnement de compilation de l'HÔTE — moteur résolu PAR
     * REQUÊTE par le routeur fichier → module, contextes remplacés À
     * CHAUD par l'orchestrateur, index partagé de l'environnement.
     *
     * @param environnement l'environnement possédé par l'hôte (le
     *   serveur ne le libère JAMAIS — miroir du vendored).
     * @param rappelArret le rappel d'arrêt (shutdown puis exit), ou null.
     */
    public constructor(
        environnement: EnvironnementCompilation,
        rappelArret: Runnable? = null,
    ) : this(fabriqueMoteur = { _ -> null }, rappelArret = rappelArret) {
        envVivant = environnement
    }

    /** L'environnement vivant branché (null = régime figé). */
    private var envVivant: EnvironnementCompilation? = null

    /**
     * L'environnement VIVANT branché (sonde publique — l'HÔTE et ses tests
     * d'orchestration y vérifient les moteurs par module, les niveaux et
     * les diagnostics ; null en régime figé, l'instance POSÉDÉE PAR L'HÔTE
     * en régime vivant — le serveur ne le libère jamais).
     *
     * @return l'environnement du régime vivant, ou null.
     */
    public fun environnement(): EnvironnementCompilation? = envVivant

    /** La machine à états du cycle de vie (observable par l'hôte/tests). */
    public val cycle: CycleVie = CycleVie()

    /** Le magasin des documents ouverts (copy-on-write + époques). */
    private val etats: EtatsDocuments = EtatsDocuments()

    /** Le client LSP (branché par le lanceur — `connect`). */
    @Volatile
    private var client: LanguageClient? = null

    /** Le moteur (construit à initialize, null = protocole pur). */
    @Volatile
    private var moteur: MoteurJava? = null

    /** Le publicateur asynchrone des diagnostics. */
    private var publicateur: PublicateurDiagnostics? = null

    /** Le miroir des versions pour le renommage (forme versionnée). */
    private var serviceRenommage: ServiceRenommage? = null

    /** Les capacités d'édition du client (négociation de forme du rename). */
    @Volatile
    private var capsEspaceDeTravail: CapsEspaceDeTravailClient = CapsEspaceDeTravailClient()

    /** Le service des symboles d'espace de travail (null = moteur absent). */
    private var symbolesEspace: SymbolesEspace? = null

    /** La racine serveur captée à initialize (noms canoniques paquet ↔ chemin). */
    @Volatile
    private var racinesServeur: List<Path> = emptyList()

    /** Mode autonome : exit() tue le processus (vrai) ou pas (faux, défaut). */
    @Volatile
    private var modeAutonome: Boolean = false

    /**
     * L'état exposé du cycle de vie (fiche 055 : « l'état exposé est
     * UNINITIALIZED ») — raccourci de [cycle.etat].
     *
     * @return l'état courant.
     */
    public fun etat(): CycleVie.Etat = cycle.etat()

    /**
     * Le mode autonome (fiche 055 : `setStandaloneMode(false)` — mode
     * in-process, défaut des tests : `exit` ne tue pas la JVM hôte).
     *
     * @param valeur vrai pour le mode processus autonome.
     */
    public fun setStandaloneMode(valeur: Boolean) {
        modeAutonome = valeur
    }

    /**
     * Branche le client LSP (appelé par le lanceur après construction —
     * le client espion des tests passe par ici comme le client réel).
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
        // contenant « initialize » (fiche 059, code exact -32600).
        if (cycle.etat() != CycleVie.Etat.UNINITIALIZED) {
            return refuse(
                ResponseErrorCode.InvalidRequest,
                "initialize a déjà été appelé — le protocole LSP n'autorise qu'un seul initialize par session",
            )
        }

        // Capacités client : la forme du WorkspaceEdit du renommage se
        // négocie ICI (documentChanges versionné ou changes héritée).
        capsEspaceDeTravail = CapsEspaceDeTravailClient(
            documentChanges = params.capabilities?.workspace?.workspaceEdit?.documentChanges ?: false,
        )

        // Le moteur naît de la racine annoncée (null → protocole pur,
        // fiche 055 ; racine inexistante → protocole pur aussi — moteur
        // non prêt, fiche 056 : liste vide, jamais d'exception).
        val racine = params.rootUri?.let { ConvertisseurLsp.cheminDeUri(it) }?.let { Path.of(it) }
        racinesServeur = if (racine != null && Files.isDirectory(racine)) listOf(racine) else emptyList()

        // Régime VIVANT (option B) : le moteur ne naît PAS ici — il naît
        // PAR REQUÊTE, par le routeur fichier → module de l'environnement
        // (fiche 047). Les services dérivés consomment l'ENVIRONNEMENT :
        // publicateur à fournisseur (résolution au TIR, dans la fenêtre
        // du debounce), renommage à fournisseur (résolution par document),
        // symboles d'espace aux RACINES DES CONTEXTES (indépendantes des
        // moteurs construits). Une reconfiguration de l'hôte (sync
        // Gradle) traverse le serveur SANS re-initialize.
        val env = envVivant
        if (env != null) {
            publicateur = PublicateurDiagnostics(clientOuRefus(), etats, env::moteurPourFichier)
            serviceRenommage = ServiceRenommage(env::moteurPourFichier, capsEspaceDeTravail)
            symbolesEspace = SymbolesEspace(racines = { env.racinesSources() })
            moteur = null

            // LIVE-REPUBLISH (motif ContextListener du vendord — le
            // scénario E2E de l'hôte : les faux « cannot be resolved »
            // s'effacent dès que la sync nourrit le moteur du module) :
            // chaque remplacement EFFECTIF de contexte REPUBLIE les
            // documents OUVERTS du module remplacé — le publicateur
            // résoudra le NOUVEAU moteur au tir (fenêtre du debounce).
            // L'auditeur vit sous le verrou de l'ENV : demander() ne fait
            // que PLANIFIER (jamais de compilation ici, jamais de
            // re-prise du verrou — aucun interblocage possible).
            env.ajouterAuditeurContexte { cle ->
                for (uri in etats.urisOuverts) {
                    val chemin = ConvertisseurLsp.cheminDeUri(uri) ?: uri
                    val module = try {
                        env.routeurFichierModule?.invoke(chemin)
                    } catch (_: Exception) {
                        null
                    }
                    if (module == cle) {
                        publicateur?.demander(uri)
                    }
                }
            }

            // V-F1 — REPUBLICATION AU SWAP D'ÉPOQUE (ADR 0029 phase 1) : le
            // double-tampon fait servir l'ANCIEN moteur pendant toute la
            // fenêtre de reconstruction (légitime — c'est l'époque vivante),
            // et l'auditeur de contexte ci-dessus ne republie qu'à
            // l'ANNONCE de la sync (ancien encore vivant). Ce SECOND
            // auditeur attend le SWAP EFFECTIF — le moteur neuf est déjà
            // vivant et sert — puis redemande la publication des documents
            // OUVERTS du module : les diagnostics des tampons suivent
            // l'époque au lieu d'attendre la frappe suivante. Demander()
            // ne fait que PLANIFIER (debounce) — jamais de compilation ici.
            env.ajouterAuditeurEpoque { cle ->
                for (uri in etats.urisOuverts) {
                    val chemin = ConvertisseurLsp.cheminDeUri(uri) ?: uri
                    val module = try {
                        env.routeurFichierModule?.invoke(chemin)
                    } catch (_: Exception) {
                        null
                    }
                    if (module == cle) {
                        publicateur?.demander(uri)
                    }
                }
            }
        } else {
            moteur = if (racine != null && Files.isDirectory(racine)) fabriqueMoteur(racine) else fabriqueMoteur(null)

            // Les services dérivés du moteur (régime figé).
            moteur?.let { m ->
                publicateur = PublicateurDiagnostics(clientOuRefus(), m, etats)
                serviceRenommage = ServiceRenommage(m, capsEspaceDeTravail)
                symbolesEspace = SymbolesEspace(racines = { listOfNotNull(racine.takeIf { Files.isDirectory(it) }) })
            }
        }

        cycle.surInitialize()
        return CompletableFuture.completedFuture(CapacitesServeur.reponseInitialize())
    }

    override fun initialized(params: org.eclipse.lsp4j.InitializedParams) {
        // La transition finale (fiche 055) ; les notifications didOpen
        // deviennent efficaces à partir d'ici (garde ci-dessous).
        cycle.surInitialized()
    }

    override fun shutdown(): CompletableFuture<Any> {
        // Le rappel d'arrêt : PREMIER appel (fiche 056 : compteur 1 après
        // shutdown, 2 après exit — la façade est prévenue puis confirmée).
        cycle.surShutdown()
        rappelArret?.run()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        cycle.surExit()
        publicateur?.fermer()
        // Second appel du rappel (confirmation — fiche 056).
        rappelArret?.run()
        // Mode autonome uniquement : le processus s'arrête (fiche 055 :
        // en mode in-process, JAMAIS de System.exit).
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
     * La garde de requête (I2) : avant `initialized` → rejet
     * SYNCHRONE ServerNotInitialized (-32002) ; après `shutdown` → rejet
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
    private fun clientOuRefus(): LanguageClient = client ?: ClientMuet

    /**
     * Le moteur DE LA REQUÊTE : le ROUTAGE fichier → module de
     * l'environnement vivant (fiche 047 — le routeur est RELU à chaque
     * requête : un modèle remplacé par la sync suivante route aussitôt,
     * un routeur fautif replie sur le contexte défaut) ; à défaut, le
     * moteur figé du régime fabrique (construit à initialize). Null =
     * protocole pur (liste vide, jamais d'exception — convention I9).
     *
     * @param chemin le chemin logique du document interrogé.
     * @return le moteur du module du document (ou le moteur figé).
     */
    private fun moteurPour(chemin: String): MoteurJava? =
        envVivant?.moteurPourFichier(chemin) ?: moteur

    /**
     * Le protocole de calcul FRAIS D'ÉPOQUE (vague V-F1 — ADR 0029 phase 1,
     * « 0 réponse périmée ») : l'extension aux ENVIRONNEMENTS du design
     * copy-on-write des documents (N8). Le calcul est exécuté sur le moteur
     * de l'époque capturée ; si une sync a fait tourner l'époque PENDANT le
     * calcul (swap de double-tampon), le résultat n'est PAS livré — le
     * calcul est REJOUÉ sur l'époque courante (bornes et honnêtetés de
     * [EnvironnementCompilation.calculerFraiche]). Un calcul coupé par
     * l'annulation fine est lui aussi rejoué.
     *
     * Régime VIVANT : délègue à l'environnement (capture atomique du couple
     * moteur/génération). Régime FIGÉ : le moteur unique ne change jamais
     * d'époque — le calcul y passe tel quel.
     *
     * @param chemin le chemin logique du document interrogé (routé).
     * @param calcul le calcul pur (moteur → résultat) — rejouable.
     * @return le résultat FRAIS de l'époque courante.
     */
    private fun <T> avecMoteurFrais(chemin: String, calcul: (MoteurJava?) -> T): T {
        val env = envVivant ?: return calcul(moteur)
        return env.calculerFraiche(chemin) { moteur -> calcul(moteur) }
    }

    // ----------------------------------------------------------------- //
    // Synchronisation des documents (fiche 059 — plein texte)
    // ----------------------------------------------------------------- //

    override fun didOpen(params: DidOpenTextDocumentParams) {
        if (!cycle.requetesPermises()) return // notification hors fenêtre : ignorée
        val document = params.textDocument
        etats.ouvrir(document.uri, document.text, document.version.toLong())
        serviceRenommage?.ouvrirDocument(document.uri, document.version.toLong())
        publicateur?.demander(document.uri)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        if (!cycle.requetesPermises()) return
        val identifiant = params.textDocument
        // Sync PLEINE : le dernier changement porte le texte intégral.
        val texte = params.contentChanges.lastOrNull()?.text ?: return
        // V-B — MAJ ciblée des ressources : un didChange XML (le client
        // édite un layout/menu/values) rafraîchit le référentiel du module
        // SANS rescan complet et invalide le mémo de diagnose (les champs
        // de R changent). Le document XML n'entre PAS dans les tampons
        // Java (filtré par etats.changer — aucun diagnostic Java publié).
        if (identifiant.uri.endsWith(".xml")) {
            val cheminXml = ConvertisseurLsp.cheminDeUri(identifiant.uri)
            if (cheminXml != null) {
                moteurPour(cheminXml)?.rafraichirFichierXml(java.nio.file.Path.of(cheminXml))
            }
        }
        etats.changer(identifiant.uri, texte, identifiant.version.toLong())
        serviceRenommage?.ouvrirDocument(identifiant.uri, identifiant.version.toLong())
        publicateur?.demander(identifiant.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        if (!cycle.requetesPermises()) return
        // I5 — didSave SANS includeText : republier depuis le buffer
        // VIVANT (la publication porte la version COURANTE — fiche 059).
        // Un document JAMAIS ouvert ne republie pas (buffer vide : faux).
        if (etats.instantane(params.textDocument.uri) == null) return
        etats.sauver(params.textDocument.uri, null)
        publicateur?.demander(params.textDocument.uri)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        if (!cycle.requetesPermises()) return
        etats.fermer(params.textDocument.uri)
        serviceRenommage?.fermerDocument(params.textDocument.uri)
    }

    // ----------------------------------------------------------------- //
    // Espace de travail (fiche 056 : symboles, fichiers surveillés)
    // ----------------------------------------------------------------- //

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        // Aucune configuration dynamique servie — tolérée, ignorée.
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        if (!cycle.requetesPermises()) return
        // Fiche 056 : les trois cas tolérés SANS exception — .java
        // EXISTANT (Changed : le cache s'invalide), .java ABSENT
        // (Deleted : suppression externe, rien à lire), NON-.java
        // (filtré). L'invalidation est CONSERVATRICE : tout événement
        // révoque l'index des symboles (reconstruction au prochain usage).
        symbolesEspace?.invalider()
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        garde<Any>()?.let { return it }
        return refuse(ResponseErrorCode.MethodNotFound, "commande inconnue : ${params.command}")
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<SymbolInformation>, List<org.eclipse.lsp4j.WorkspaceSymbol>>> {
        garde<Either<List<SymbolInformation>, List<org.eclipse.lsp4j.WorkspaceSymbol>>>()?.let { return it }
        // Fiche 056 : requête vide → liste VIDE (I9) ; moteur non prêt →
        // liste VIDE sans exception ; la réponse prend la forme GAUCHE
        // (liste) — jamais null.
        val resultat = symbolesEspace?.rechercher(params.query ?: "") ?: emptyList()
        return CompletableFuture.completedFuture(Either.forLeft(resultat))
    }

    // ----------------------------------------------------------------- //
    // Lectures de document (overlay VIVANT puis disque — convention I9)
    // ----------------------------------------------------------------- //

    /** Le couple (chemin logique, texte) d'un document — overlay puis disque. */
    private fun documentDe(uri: String): Pair<String, String>? {
        etats.instantane(uri)?.let { return (ConvertisseurLsp.cheminDeUri(uri) ?: uri) to it.texte }
        val chemin = ConvertisseurLsp.cheminDeUri(uri) ?: return null
        val texte = runCatching { Files.readString(Path.of(chemin)) }.getOrNull() ?: return null
        return chemin to texte
    }

    /** L'offset d'une position LSP dans le texte d'un document. */
    private fun offsetDe(texte: String, position: Position): Int =
        ConvertisseurLsp.repere(texte).offset(position)

    // ----------------------------------------------------------------- //
    // Survol, complétion, signatures (fiches 054/058/059)
    // ----------------------------------------------------------------- //

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        garde<Hover>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri)
        // Convention I9 : hover → null quand il n'y a rien (le client a
        // un repli) — jamais une structure vide.
        val survol = document?.let { (chemin, texte) ->
            avecMoteurFrais(chemin) { moteur ->
                moteur?.survolRiche(chemin, texte, offsetDe(texte, params.position))
            }
        }
        return CompletableFuture.completedFuture(survol?.let(::survolLsp))
    }

    /** Le Hover LSP d'un survol riche (conventions fiche 057 : PLAINTEXT
     *  seul, MARKDOWN « ```java\n » + javadoc sinon). */
    private fun survolLsp(survol: jo.codeide.lsp.java.survol.SurvolRiche): Hover {
        val valeur = if (survol.documentation != null) {
            MarkupContent("markdown", "```java\n${survol.signature}\n```\n${survol.documentation}")
        } else {
            MarkupContent("plaintext", survol.signature)
        }
        return Hover(valeur)
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        garde<Either<List<CompletionItem>, CompletionList>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri)
        val reponse = document?.let { (chemin, texte) ->
            etats.calculerFraiche(uri) { instantane ->
                avecMoteurFrais(chemin) { moteur ->
                    moteur?.completer(chemin, instantane.texte, offsetDe(instantane.texte, params.position))
                }
            }
        }
        val liste = reponse ?: jo.codeide.lsp.java.completion.ReponseCompletion(emptyList(), 0, jo.codeide.lsp.java.completion.GenreContexte.INCONNU)
        return CompletableFuture.completedFuture(Either.forRight(listeCompletion(uri, liste, params.position)))
    }

    /** La CompletionList LSP d'une réponse moteur (I12 : filterText = label
     *  sur CHAQUE item ; data = payload de classement x-codeide). */
    private fun listeCompletion(
        uri: String,
        reponse: jo.codeide.lsp.java.completion.ReponseCompletion,
        position: Position,
    ): CompletionList {
        val document = documentDe(uri)
        val texte = document?.second ?: ""
        val repere = ConvertisseurLsp.repere(texte)
        val prefixe = prefixeAuCaret(texte, repere.offset(position))
        val contexte = ContexteClassement(reponse.genre, prefixe)
        val peseurs = listOf(PeseurCorrespondance(), PeseurNature(), PeseurLongueur())
        val items = reponse.items.map { item ->
            val lsp = CompletionItem()
            lsp.label = item.libelle
            lsp.kind = CompletionItemKind.forValue(
                jo.codeide.lsp.java.completion.CorrespondanceKindsLsp.kindDe(item.nature).code,
            )
            item.detail?.let { lsp.detail = it }
            // I12 — filterText STRICTEMENT ÉGAL au label (fiche 058).
            lsp.filterText = item.libelle
            lsp.insertText = item.texteInsertion ?: item.libelle
            if (item.extrait) lsp.insertTextFormat = InsertTextFormat.Snippet
            if (item.editionsAdditionnelles.isNotEmpty()) {
                lsp.additionalTextEdits = ConvertisseurLsp.editions(item.editionsAdditionnelles, repere)
            }
            item.marqueur?.let { lsp.data = mapOf("marqueur" to it) }
            // Le payload de classement (capability x-codeide.completionRanking) :
            // poids packé + matcher sérialisé — le re-filtrage local du
            // client (ADR 0012). Posé APRÈS le marqueur : le payload
            // complet voyage sous les clés maison.
            val poids = FrontiereRpc.PoidsRpc(
                correspondance = (peseurs[0].poids(item, contexte) as Number).toInt(),
                nature = (peseurs[1].poids(item, contexte) as Number).toInt(),
                longueur = (peseurs[2].poids(item, contexte) as Number).toInt(),
            )
            val donnees = CapacitesServeur.donneesItemRanking(
                poids.packe,
                prefixe,
                CorrespondanceGraduee.LONGUEUR_MIN_SOUS_CHAINE,
            )
            item.marqueur?.let { donnees.addProperty("marqueur", it) }
            lsp.data = donnees
            lsp
        }
        val liste = CompletionList(false, items)
        return liste
    }

    /** Le mot tapé avant le caret (le préfixe du matcher sérialisé). */
    private fun prefixeAuCaret(texte: String, offset: Int): String {
        var debut = offset
        while (debut > 0 && texte[debut - 1].isJavaIdentifierPart()) debut--
        return texte.substring(debut, offset)
    }

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        garde<CompletionItem>()?.let { return it }
        return CompletableFuture.completedFuture(unresolved)
    }

    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp> {
        garde<SignatureHelp>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri)
        val aide = document?.let { (chemin, texte) ->
            avecMoteurFrais(chemin) { moteur ->
                moteur?.signatureHelp(chemin, texte, offsetDe(texte, params.position))
            }
        }
        val reponse = SignatureHelp()
        reponse.signatures = aide?.signatures?.map { candidat ->
            SignatureInformation(candidat.libelle).apply {
                parameters = candidat.parametres.map { ParameterInformation(it) }
            }
        } ?: emptyList()
        reponse.activeSignature = aide?.signatureActive
        reponse.activeParameter = aide?.parametreActif
        return CompletableFuture.completedFuture(reponse)
    }

    // ----------------------------------------------------------------- //
    // Navigation (fiches 054/059 — définitions, références, surlignage)
    // ----------------------------------------------------------------- //

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        garde<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()?.let { return it }
        return CompletableFuture.completedFuture(Either.forLeft(ciblesNavigation(params)))
    }

    override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        garde<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()?.let { return it }
        return CompletableFuture.completedFuture(Either.forLeft(ciblesNavigation(params)))
    }

    override fun implementation(params: ImplementationParams): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        garde<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()?.let { return it }
        return CompletableFuture.completedFuture(Either.forLeft(ciblesNavigation(params)))
    }

    /** Les localisations LSP d'une requête de navigation (I9 : liste vide). */
    private fun ciblesNavigation(params: TextDocumentPositionParams): List<Location> {
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return emptyList()
        val (chemin, texte) = document
        val cibles = avecMoteurFrais(chemin) { moteur ->
            val m = moteur ?: return@avecMoteurFrais emptyList()
            when (params) {
                is TypeDefinitionParams -> m.typeDefinition(chemin, texte, offsetDe(texte, params.position))
                is ImplementationParams -> m.implementation(chemin, texte, offsetDe(texte, params.position))
                else -> m.definition(chemin, texte, offsetDe(texte, params.position))
            }
        }
        return cibles.mapNotNull { cible -> localisationDe(cible) }
    }

    /** La Location d'une cible moteur (le repère du FICHIER PORTeur). */
    private fun localisationDe(cible: jo.codeide.lsp.java.navigation.CibleNavigation): Location? {
        val cheminPorteur = cible.chemin?.toString() ?: ConvertisseurLsp.cheminDeUri(cible.uri)
        val texte = runCatching { cheminPorteur?.let { Files.readString(Path.of(it)) } }.getOrNull()
            ?: etats.instantane(cible.uri)?.texte
            ?: return Location(cible.uri, Range(Position(0, 0), Position(0, 0)))
        return ConvertisseurLsp.localisation(cible, ConvertisseurLsp.repere(texte))
    }

    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
        garde<List<Location>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        // LEVÉE DU CONFINEMENT (vague V-E — décision D1 du propriétaire,
        // 31/10/2026 ; ADR 0028). La parité stricte §10.4 avec le vendored
        // est FORMELLEMENT abandonnée pour `references` : le programme de
        // réécriture est terminé, la bascule est signée (v1.0.0) et le
        // propriétaire a amendé la décision du 28/10. La réponse rend les
        // usages MULTI-FICHIERS réels du moteur (exhaustifs par clé de
        // binding — rappel = oracle au banc :java:benchUsages), la gate
        // « taille attendue = 1 » de la fiche 054 est INVERSÉE (borne
        // « 2 exactement » : la déclaration T.java ET l'appel U.java).
        // Un client ancien qui s'appuyait sur le confinement consomme ce
        // qu'il veut de la liste (ADR 0028 : « refus de complexifier ») ;
        // la capability maison versionnée `x-codeide.usages` (champ
        // `experimental`) annonce le comportement multi-fichiers, et le
        // RPC maison `textDocument/usagesGroupes` sert la vue groupée.
        // V-F1 : le balayage passe par le protocole d'époque (rejeu au
        // swap, coupure fine par fichier — jamais d'usages périmés).
        val usages = avecMoteurFrais(chemin) { moteur ->
            moteur?.references(chemin, texte, offsetDe(texte, params.position), params.context.isIncludeDeclaration)
        } ?: return CompletableFuture.completedFuture(emptyList())
        // Conversion offset → ligne/colonne : le repère du FICHIER
        // PORTEUR de chaque usage (l'ancien confinement convertissait
        // toujours avec le repère du document interrogé — cela ne restait
        // vrai que pour lui). Les usages des AUTRES fichiers vivent sur
        // leur texte DISQUE (celui que le moteur a analysé), l'overlay
        // ouvert ne servant que de repli si le disque est illisible.
        val repereCourant = ConvertisseurLsp.repere(texte)
        val reperesPorteurs = HashMap<String, ConvertisseurLsp.Repere>()
        val localisations = ArrayList<Location>(usages.size)
        for (usage in usages) {
            val repere = if (usage.chemin == chemin) repereCourant
            else reperesPorteurs.getOrPut(usage.chemin) { repereDuPorteur(usage.chemin) }
            localisations.add(Location(ConvertisseurLsp.uriDeFichier(usage.chemin), repere.plage(usage.plage)))
        }
        return CompletableFuture.completedFuture(localisations)
    }

    /**
     * Le repère du FICHIER PORTEUR d'un usage inter-fichiers : le texte
     * DISQUE d'abord (le moteur analyse les voisins sur disque — les plages
     * rendues s'y réfèrent), l'overlay ouvert en repli, puis un repère VIDE
     * (chaque position se borne à 0:0 — même repli que [localisationDe],
     * jamais d'échec de requête pour un fichier disparu).
     *
     * @param cheminPorteur le chemin du fichier porteur de l'usage.
     * @return le repère indexé du texte porteur.
     */
    private fun repereDuPorteur(cheminPorteur: String): ConvertisseurLsp.Repere {
        val textePorteur = runCatching { Files.readString(Path.of(cheminPorteur)) }.getOrNull()
            ?: etats.instantane(ConvertisseurLsp.uriDeFichier(cheminPorteur))?.texte
            ?: return ConvertisseurLsp.repere("")
        return ConvertisseurLsp.repere(textePorteur)
    }

    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> {
        garde<List<DocumentHighlight>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        val surbrillances = avecMoteurFrais(chemin) { moteur ->
            moteur?.surbrillances(chemin, texte, offsetDe(texte, params.position))
        } ?: return CompletableFuture.completedFuture(emptyList())
        val repere = ConvertisseurLsp.repere(texte)
        return CompletableFuture.completedFuture(surbrillances.map { ConvertisseurLsp.surlignage(it, repere) })
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        garde<List<Either<SymbolInformation, DocumentSymbol>>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        val symboles = avecMoteurFrais(chemin) { moteur ->
            moteur?.let { SymbolesDocument(it).symboles(chemin, texte) }
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(symboles.map { Either.forRight(it) })
    }

    // ----------------------------------------------------------------- //
    // Formatage (fiches 054/058 — plein document, plage, à la frappe)
    // ----------------------------------------------------------------- //

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        garde<List<TextEdit>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        val formate = avecMoteurFrais(chemin) { moteur -> moteur?.formater(texte) }
        if (formate == null || formate == texte) return CompletableFuture.completedFuture(emptyList())
        // Une édition pleine page : le texte reconstruit EST le texte
        // formaté (fiche 054 : « le texte reconstruit ... contient
        // “int x = 1;” précédé de 8 espaces »).
        val repere = ConvertisseurLsp.repere(texte)
        val plage = Range(Position(0, 0), repere.position(texte.length))
        return CompletableFuture.completedFuture(listOf(TextEdit(plage, formate)))
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> {
        garde<List<TextEdit>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        val repere = ConvertisseurLsp.repere(texte)
        val debut = repere.offset(params.range.start)
        val fin = repere.offset(params.range.end)
        val editions = avecMoteurFrais(chemin) { moteur ->
            moteur?.editionsFormatagePlage(texte, debut, fin - debut)
        }
        return CompletableFuture.completedFuture(editions?.let { ConvertisseurLsp.editions(it, repere) } ?: emptyList())
    }

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> {
        garde<List<TextEdit>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        val repere = ConvertisseurLsp.repere(texte)
        val offset = repere.offset(params.position)
        // Fiche 058 : no-op (liste VIDE) si le caractère tapé ne
        // correspond pas au caractère à la position.
        val plage = avecMoteurFrais(chemin) { moteur ->
            moteur?.plageFormattable(chemin, texte, offset, params.ch.singleOrNull() ?: ' ')
        } ?: return CompletableFuture.completedFuture(emptyList())
        val editions = avecMoteurFrais(chemin) { moteur ->
            moteur?.editionsFormatagePlage(texte, plage.debutOffset, plage.finOffset - plage.debutOffset)
        }
        return CompletableFuture.completedFuture(editions?.let { ConvertisseurLsp.editions(it, repere) } ?: emptyList())
    }

    // ----------------------------------------------------------------- //
    // Sélection, pliage, inlay, jetons sémantiques (fiches 058/059)
    // ----------------------------------------------------------------- //

    override fun selectionRange(params: SelectionRangeParams): CompletableFuture<List<SelectionRange>> {
        garde<List<SelectionRange>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        val repere = ConvertisseurLsp.repere(texte)
        val chaines = avecMoteurFrais(chemin) { moteur ->
            moteur?.chainesSelection(chemin, texte, params.positions.map { repere.offset(it) })
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(
            chaines.mapNotNull { chaine ->
                chaine?.let { SelectionRangeLsp.deChaine(it, repere) }
            },
        )
    }

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
        garde<List<FoldingRange>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        // I9 : liste non-null VIDE (moteur utilisable + aucun pli).
        val texte = document.second
        val repere = ConvertisseurLsp.repere(texte)
        val plis = avecMoteurFrais(document.first) { moteur -> moteur?.plis(texte) }
            ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(
            plis.map { pli ->
                val debut = repere.position(pli.debut)
                val fin = repere.position(pli.fin)
                FoldingRange(debut.line, fin.line).apply {
                    startCharacter = debut.character
                    endCharacter = fin.character
                    kind = pli.genre.libelleLsp
                    collapsedText = pli.texteReplie
                }
            },
        )
    }

    override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> {
        garde<List<InlayHint>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        val indices = avecMoteurFrais(chemin) { moteur -> moteur?.inlayHints(chemin, texte) }
            ?: return CompletableFuture.completedFuture(emptyList())
        val repere = ConvertisseurLsp.repere(texte)
        return CompletableFuture.completedFuture(
            indices.map { indice ->
                InlayHint(repere.position(indice.offset), Either.forLeft(indice.libelle)).apply {
                    kind = InlayHintKind.forValue(indice.genre.codeLsp)
                    paddingLeft = indice.paddingGauche
                    paddingRight = indice.paddingDroit
                }
            },
        )
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        garde<SemanticTokens>()?.let { return it }
        // Non annoncé (règle matrice) mais TOLÉRANT : data vide (I9 —
        // jamais null, jamais d'exception).
        return CompletableFuture.completedFuture(SemanticTokens(ArrayList()))
    }

    // ----------------------------------------------------------------- //
    // Renommage (fiches 054/059 — prepareRename, rename, erreur typée)
    // ----------------------------------------------------------------- //

    override fun prepareRename(params: PrepareRenameParams): CompletableFuture<Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior>> {
        garde<Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri)
        val preparation = document?.let { (chemin, texte) ->
            avecMoteurFrais(chemin) { _ ->
                serviceRenommage?.preparerRenommage(chemin, texte, offsetDe(texte, params.position))
            }
        }
        // Null = pas renommable à cette position (le client désactive le
        // rename) — la fiche 054 teste le cas NON-null (le champ, forme
        // plage).
        return CompletableFuture.completedFuture(
            preparation?.let {
                Either3.forSecond(
                    PrepareRenameResult(
                        ConvertisseurLsp.repere(document.second).plage(it.plage),
                        it.placeholder,
                    ),
                )
            },
        )
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
        garde<WorkspaceEdit>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri)
        val service = serviceRenommage
        // Régime vivant : la garde moteur passe par le ROUTAGE du document
        // renommé (fiche 047) — le champ figé resterait null en mode
        // environnement et rejetterait le rename à tort.
        if (document == null || service == null || moteurPour(document.first) == null) {
            // I8 — erreur TYPIE RequestFailed (-32803), message FR contenant
            // « Aucun symbole renommable » (fiche 059) — jamais un
            // WorkspaceEdit vide silencieux. Le rejet passe par un FUTUR
            // EN ERREUR (le contrat d'lsp4j : un throw synchrone serait
            // reformé en InternalError par la couche réflexive).
            return refuse(ResponseErrorCode.RequestFailed, "Aucun symbole renommable")
        }
        val (chemin, texte) = document
        return try {
            // V-F1 : le renommage entier vit sous le protocole d'époque du
            // document CIBLE — un swap pendant les phases rejeue le calcul
            // (jamais d'éditions mixtes de deux époques).
            val edition = avecMoteurFrais(chemin) { _ ->
                service.renommage(uri, chemin, texte, offsetDe(texte, params.position), params.newName)
            }
            CompletableFuture.completedFuture(espaceDeTravailLsp(edition))
        } catch (e: RenommageSansCible) {
            refuse(ResponseErrorCode.RequestFailed, e.message ?: "Aucun symbole renommable")
        }
    }

    /** Le WorkspaceEdit LSP d'une édition moteur (forme négociée). */
    private fun espaceDeTravailLsp(edition: jo.codeide.lsp.java.renommage.EditionEspaceDeTravail): WorkspaceEdit {
        val espace = WorkspaceEdit()
        if (edition.changes != null) {
            val changes = LinkedHashMap<String, List<TextEdit>>()
            for ((uriFichier, editions) in edition.changes) {
                val texte = documentDe(uriFichier)?.second
                    ?: runCatching { Files.readString(Path.of(uriFichier.removePrefix("file://"))) }.getOrNull()
                    ?: ""
                changes[uriFichier] = ConvertisseurLsp.editions(editions, ConvertisseurLsp.repere(texte))
            }
            espace.changes = changes
        }
        edition.documentChanges?.let { documents ->
            espace.documentChanges = documents.map { doc ->
                val texte = documentDe(doc.uri)?.second
                    ?: runCatching { Files.readString(Path.of(doc.uri.removePrefix("file://"))) }.getOrNull()
                    ?: ""
                val identifiant = org.eclipse.lsp4j.VersionedTextDocumentIdentifier()
                identifiant.uri = doc.uri
                identifiant.version = doc.version?.toInt()
                val element = org.eclipse.lsp4j.TextDocumentEdit()
                element.textDocument = identifiant
                element.edits = ConvertisseurLsp.editions(doc.editions, ConvertisseurLsp.repere(texte))
                Either.forLeft<org.eclipse.lsp4j.TextDocumentEdit, org.eclipse.lsp4j.ResourceOperation>(element)
            }
        }
        return espace
    }

    // ----------------------------------------------------------------- //
    // Actions de code et lentilles (fiches 055/058/063)
    // ----------------------------------------------------------------- //

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
        garde<List<Either<Command, CodeAction>>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        // Les diagnostics du contexte voyagent en forme moteur (le
        // service N6 consomme les diagnostics moteur).
        val diagnosticsMoteur = params.context.diagnostics.mapNotNull { diagnostic ->
            diagnosticVersMoteur(diagnostic)
        }
        // V-F1 : les actions vivent sous le protocole d'époque du document
        // (rejeu au swap, jamais de correcteurs périmés).
        val actions = avecMoteurFrais(chemin) { moteur ->
            val m = moteur ?: return@avecMoteurFrais emptyList()
            jo.codeide.lsp.java.actionscode.ServiceActionsCode(m).actions(chemin, texte, diagnosticsMoteur)
        } ?: return CompletableFuture.completedFuture(emptyList())
        val repere = ConvertisseurLsp.repere(texte)
        return CompletableFuture.completedFuture(
            actions.map { action -> Either.forRight(ConvertisseurLsp.actionCode(action, uri, repere)) },
        )
    }

    /** Le diagnostic moteur d'un diagnostic LSP (aller-retour codeAction). */
    private fun diagnosticVersMoteur(diagnostic: Diagnostic): jo.codeide.lsp.java.diagnostics.Diagnostic? {
        val severite = when (diagnostic.severity) {
            org.eclipse.lsp4j.DiagnosticSeverity.Error -> jo.codeide.lsp.java.diagnostics.Severite.ERREUR
            org.eclipse.lsp4j.DiagnosticSeverity.Warning -> jo.codeide.lsp.java.diagnostics.Severite.AVERTISSEMENT
            else -> jo.codeide.lsp.java.diagnostics.Severite.INFO
        }
        val document = documentDe(diagnostic.source ?: "")?.second ?: ""
        val repere = ConvertisseurLsp.repere(document)
        val code = (diagnostic.code as? String)?.removePrefix(ConvertisseurLsp.PREFIXE_CODE_ECJ)
        // La plage LSP → offsets absolus.
        val debut = repere.offset(diagnostic.range.start)
        val fin = repere.offset(diagnostic.range.end)
        return jo.codeide.lsp.java.diagnostics.Diagnostic(
            severite = severite,
            message = diagnostic.message,
            plage = jo.codeide.lsp.java.diagnostics.Plage(debut, fin),
            code = code,
        )
    }

    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> {
        garde<List<CodeLens>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        // V-F1 : les lentilles vivent sous le protocole d'époque du document.
        val lentilles = avecMoteurFrais(document.first) { moteur ->
            val m = moteur ?: return@avecMoteurFrais null
            LentillesCode(m, etats).lentilles(uri, document.first, document.second)
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(lentilles)
    }

    override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
        garde<CodeLens>()?.let { return it }
        // L'ancre (uri) transportée par la lentille route le moteur du
        // document PORTEUR (fiche 047) — le resolve sans ancre retombe
        // sur le moteur défaut, et [LentillesCode.resoudre] rend la
        // lentille TELLE QUELLE faute d'uri/offset complets.
        val uriLentille = champDataChaine(unresolved.data, "uri")
        val cheminLentille = uriLentille?.let { ConvertisseurLsp.cheminDeUri(it) } ?: uriLentille ?: ""
        // V-F1 : le resolve vit sous le protocole d'époque de l'ancre.
        val resolue = avecMoteurFrais(cheminLentille) { moteur ->
            val m = moteur ?: return@avecMoteurFrais unresolved
            LentillesCode(m, etats).resoudre(unresolved)
        } ?: return CompletableFuture.completedFuture(unresolved)
        return CompletableFuture.completedFuture(resolue)
    }

    // ----------------------------------------------------------------- //
    // Hiérarchies de types et d'appels (fiche 058/018)
    // ----------------------------------------------------------------- //

    override fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): CompletableFuture<List<TypeHierarchyItem>> {
        garde<List<TypeHierarchyItem>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        // V-F1 : la préparation vit sous le protocole d'époque du document.
        val cible = avecMoteurFrais(chemin) { moteur ->
            val m = moteur ?: return@avecMoteurFrais null
            val offset = offsetDe(texte, params.position)
            m.typeAuCaret(chemin, texte, offset)
                // Convention contextType (fiche 058) : un caret SANS référence
                // dans le corps d'un type rend le type ENGLOBANT — le moteur
                // sert les références et déclarations (fiche 018), la couche
                // protocole complète l'englobant ; avant toute déclaration
                // (« package ») → liste VIDE.
                ?: typeEnglobant(m, chemin, texte, offset, uri)
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(
            listOfNotNull(cible?.let { itemTypeHierarchie(it) }),
        )
    }

    override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        garde<List<TypeHierarchyItem>>()?.let { return it }
        val element = params.item
        val (uri, offset) = ancreDe(element.data)
        if (uri == null || offset == null) return CompletableFuture.completedFuture(emptyList())
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        // V-F1 : la hiérarchie vit sous le protocole d'époque du document.
        val supertypes = avecMoteurFrais(chemin) { moteur ->
            moteur?.supertypesDirects(chemin, texte, offset)
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(supertypes.map { itemTypeHierarchie(it) })
    }

    override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        garde<List<TypeHierarchyItem>>()?.let { return it }
        val element = params.item
        val (uri, offset) = ancreDe(element.data)
        if (uri == null || offset == null) return CompletableFuture.completedFuture(emptyList())
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        // V-F1 : la hiérarchie vit sous le protocole d'époque du document.
        val sousTypes = avecMoteurFrais(chemin) { moteur ->
            moteur?.sousTypes(chemin, texte, offset)
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(sousTypes.map { itemTypeHierarchie(it) })
    }

    override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<List<CallHierarchyItem>> {
        garde<List<CallHierarchyItem>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        // V-F1 : la préparation vit sous le protocole d'époque du document.
        val items = avecMoteurFrais(chemin) { moteur ->
            val m = moteur ?: return@avecMoteurFrais emptyList()
            val offset = offsetDe(texte, params.position)
            // Fiche 058 : le prepare sur un APPEL rend la DÉCLARATION de la
            // MÉTHODE APPELÉE (utilise dans Base.java) ; le prepare sur le NOM
            // D'UNE DÉCLARATION rend cette déclaration. La DÉFINITION N4 sert
            // les DEUX cas (un caret sur un identifiant de méthode — appel ou
            // déclaration — se résout vers le fichier de déclaration) ; la
            // méthode ENGLOBANTE (fiche 018, methodeAuCaret) sert le caret
            // ordinaire du corps.
            val definitions = m.definition(chemin, texte, offset)
            val itemAppel = definitions.firstOrNull()?.let { definition ->
                val cheminDeclaration = definition.chemin?.toString() ?: return@let null
                val texteDeclaration = runCatching { Files.readString(Path.of(cheminDeclaration)) }.getOrNull()
                    ?: return@let null
                val repere = ConvertisseurLsp.repere(texteDeclaration)
                CallHierarchyItem(
                    definition.nomSimple,
                    org.eclipse.lsp4j.SymbolKind.Method,
                    definition.uri,
                    repere.plage(definition.plage),
                    repere.plage(definition.plage),
                ).apply {
                    data = mapOf(
                        "uri" to definition.uri,
                        "offset" to definition.plage.debutOffset,
                    )
                }
            }
            if (itemAppel != null) return@avecMoteurFrais listOf(itemAppel)
            val cible = m.methodeAuCaret(chemin, texte, offset)
            listOfNotNull(cible?.let { itemMethodeHierarchie(it) })
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(items)
    }

    override fun callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture<List<CallHierarchyIncomingCall>> {
        garde<List<CallHierarchyIncomingCall>>()?.let { return it }
        val element = params.item
        val (uri, offset) = ancreDe(element.data)
        if (uri == null || offset == null) return CompletableFuture.completedFuture(emptyList())
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        // V-F1 : la hiérarchie vit sous le protocole d'époque du document.
        val entrants = avecMoteurFrais(chemin) { moteur ->
            moteur?.appelsEntrants(chemin, texte, offset)
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(
            entrants.map { appelant ->
                val texteAppelant = runCatching { Files.readString(appelant.chemin) }.getOrNull() ?: texte
                val repere = ConvertisseurLsp.repere(texteAppelant)
                val plage = plageJetonDans(texteAppelant, appelant.nomMethode)
                val item = CallHierarchyItem(
                    appelant.nomMethode,
                    org.eclipse.lsp4j.SymbolKind.Method,
                    appelant.chemin.toUri().toString(),
                    Range(repere.position(0), repere.position(texteAppelant.length)),
                    plage ?: Range(Position(0, 0), Position(0, 0)),
                )
                CallHierarchyIncomingCall(item, listOfNotNull(plage))
            },
        )
    }

    override fun callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture<List<CallHierarchyOutgoingCall>> {
        garde<List<CallHierarchyOutgoingCall>>()?.let { return it }
        val element = params.item
        val (uri, offset) = ancreDe(element.data)
        if (uri == null || offset == null) return CompletableFuture.completedFuture(emptyList())
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        // V-F1 : la hiérarchie vit sous le protocole d'époque du document.
        val sortants = avecMoteurFrais(chemin) { moteur ->
            moteur?.appelsSortants(chemin, texte, offset)
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(
            sortants.mapNotNull { cible ->
                // Localisation de la DÉCLARATION cible : fichier du FQN
                // propriétaire, jeton du sélecteur (noms canoniques).
                val fichierCible = fichierDeClasse(cible.classeProprietaire) ?: return@mapNotNull null
                val texteCible = runCatching { Files.readString(fichierCible) }.getOrNull() ?: return@mapNotNull null
                val repere = ConvertisseurLsp.repere(texteCible)
                val plageNom = plageJetonDans(texteCible, cible.nom)
                val item = CallHierarchyItem(
                    cible.nom,
                    org.eclipse.lsp4j.SymbolKind.Method,
                    fichierCible.toUri().toString(),
                    plageNom ?: Range(Position(0, 0), Position(0, 0)),
                    plageNom ?: Range(Position(0, 0), Position(0, 0)),
                )
                // Les sites d'appel vivent dans le document SOURCE.
                val sites = cible.sites.mapNotNull { site -> plageJetonDans(texte, site) }
                CallHierarchyOutgoingCall(item, sites)
            },
        )
    }

    /** L'ancre (uri, offset) transportée dans le champ data d'un élément
     *  (JsonObject au fil JSON-RPC, Map en appel direct — helpers partagés). */
    private fun ancreDe(donnees: Any?): Pair<String?, Int?> =
        champDataChaine(donnees, "uri") to champDataEntier(donnees, "offset")

    /**
     * Le type ENGLOBANT d'un offset (convention contextType, fiche 058) :
     * la plus PETITE déclaration de type dont la plage couvre l'offset —
     * servie comme cible quand le caret ne porte ni référence ni
     * déclaration (corps vide de la classe). Null si l'offset est hors de
     * tout type (avant « package »…) → liste VIDE.
     */
    private fun typeEnglobant(
        moteur: MoteurJava,
        chemin: String,
        texte: String,
        offset: Int,
        uri: String,
    ): jo.codeide.lsp.java.hierarchie.CibleTypeHierarchie? {
        val arbre = moteur.resolveUnit(chemin, texte, genererCode = false)?.arbre ?: return null
        var meilleur: org.eclipse.jdt.internal.compiler.ast.TypeDeclaration? = null
        var meilleurSpan = Int.MAX_VALUE

        fun considerer(type: org.eclipse.jdt.internal.compiler.ast.TypeDeclaration) {
            val debut = type.declarationSourceStart
            val fin = type.declarationSourceEnd
            if (offset >= debut && offset <= fin && fin - debut < meilleurSpan) {
                meilleur = type
                meilleurSpan = fin - debut
            }
            type.memberTypes?.forEach { considerer(it) }
        }
        arbre.types?.forEach { considerer(it) }
        val declaration = meilleur ?: return null
        val nom = String(declaration.name)
        val plageNom = plageJetonDe(nom, declaration.declarationSourceStart, declaration.declarationSourceEnd, texte)
            ?: return null
        val debutLigne = texte.lastIndexOf('\n', declaration.declarationSourceStart).let { if (it < 0) 0 else it + 1 }
        return jo.codeide.lsp.java.hierarchie.CibleTypeHierarchie(
            nomSimple = nom,
            chemin = Path.of(chemin),
            plageNom = plageNom,
            plageDeclaration = jo.codeide.lsp.java.diagnostics.Plage(debutLigne, declaration.declarationSourceEnd + 1),
        )
    }

    /** La plage moteur du JETON nom (premier identifiant égal dans la fenêtre). */
    private fun plageJetonDe(nom: String, debutFenetre: Int, finFenetre: Int, source: String): jo.codeide.lsp.java.diagnostics.Plage? {
        val debut = debutFenetre.coerceIn(0, source.length)
        val fin = finFenetre.coerceIn(debut, source.length)
        val fenetre = source.substring(debut, fin)
        var indice = 0
        while (indice < fenetre.length) {
            val c = fenetre[indice]
            if (c.isJavaIdentifierStart()) {
                var j = indice + 1
                while (j < fenetre.length && fenetre[j].isJavaIdentifierPart()) j++
                if (fenetre.substring(indice, j) == nom) {
                    return jo.codeide.lsp.java.diagnostics.Plage(debut + indice, debut + j)
                }
                indice = j
            } else {
                indice++
            }
        }
        return null
    }

    /** L'item LSP d'une cible de hiérarchie de types (localisée dans SA source). */
    private fun itemTypeHierarchie(cible: jo.codeide.lsp.java.hierarchie.CibleTypeHierarchie): TypeHierarchyItem {
        val texte = runCatching { Files.readString(cible.chemin) }.getOrNull() ?: ""
        val repere = ConvertisseurLsp.repere(texte)
        val item = TypeHierarchyItem(
            cible.nomSimple,
            org.eclipse.lsp4j.SymbolKind.Class,
            cible.chemin.toUri().toString(),
            repere.plage(cible.plageDeclaration),
            repere.plage(cible.plageNom),
        )
        // Ancre de résolution (supertypes/subtypes sans re-parse).
        item.data = mapOf("uri" to cible.chemin.toUri().toString(), "offset" to cible.plageNom.debutOffset)
        return item
    }

    /** L'item LSP d'une cible de hiérarchie d'appels. */
    private fun itemMethodeHierarchie(cible: jo.codeide.lsp.java.hierarchie.CibleMethodeHierarchie): CallHierarchyItem {
        val texte = runCatching { Files.readString(cible.chemin) }.getOrNull() ?: ""
        val repere = ConvertisseurLsp.repere(texte)
        val item = CallHierarchyItem(
            cible.nom,
            org.eclipse.lsp4j.SymbolKind.Method,
            cible.chemin.toUri().toString(),
            repere.plage(cible.plageNom),
            repere.plage(cible.plageNom),
        )
        item.detail = cible.classeProprietaire
        item.data = mapOf("uri" to cible.chemin.toUri().toString(), "offset" to cible.plageNom.debutOffset)
        return item
    }

    /** La plage LSP d'un extrait de texte TROUVÉ dans une source. */
    private fun plageJetonDans(texte: String, extrait: String): Range? {
        val indice = texte.indexOf(extrait)
        if (indice < 0) return null
        val repere = ConvertisseurLsp.repere(texte)
        return repere.plage(jo.codeide.lsp.java.diagnostics.Plage(indice, indice + extrait.length))
    }

    /** Le fichier d'une classe par son FQN (noms canoniques paquet ↔ chemin).
     *  Régime vivant : les racines DES CONTEXTES de l'environnement
     *  (défaut puis modules — fiche 047) ; régime figé : la racine
     *  serveur captée à initialize. */
    private fun fichierDeClasse(fqn: String): Path? {
        val racines = envVivant?.racinesSources() ?: racinesServeur
        for (racine in racines) {
            val fichier = racine.resolve(fqn.replace('.', '/') + ".java")
            if (Files.isRegularFile(fichier)) return fichier
        }
        return null
    }

    // ----------------------------------------------------------------- //
    // Méthodes expérimentales (fiche 054)
    // ----------------------------------------------------------------- //

    override fun superDefinition(params: TextDocumentPositionParams): CompletableFuture<List<CibleSuperRpc>> {
        garde<List<CibleSuperRpc>>()?.let { return it }
        val uri = params.textDocument.uri
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (chemin, texte) = document
        // V-F1 : la navigation vit sous le protocole d'époque du document.
        val cibles = avecMoteurFrais(chemin) { moteur ->
            moteur?.superDefinition(chemin, texte, offsetDe(texte, params.position))
        } ?: return CompletableFuture.completedFuture(emptyList())
        return CompletableFuture.completedFuture(
            cibles.mapNotNull { cible ->
                // Contrat de sérialisation du vendored (`NavigationTarget`)
                // — miroir exact du client cel-lsp : path/start/end/label,
                // la cible BINAIRE (chemin null) n'est pas servie (le
                // picker super est une navigation SOURCE).
                cible.chemin?.let {
                    CibleSuperRpc(
                        path = it.toString(),
                        start = cible.plage?.debutOffset ?: 0,
                        end = cible.plage?.finOffset ?: 0,
                        label = cible.libelle,
                    )
                }
            },
        )
    }

    override fun decompiledSource(params: ParamsFqcnRpc): CompletableFuture<SourceDecompileeRpc> {
        garde<SourceDecompileeRpc>()?.let { return it }
        // Régime VIVANT — ADR 0215 (motif exact du vendored
        // `JavaTextDocumentService.decompiledSource`) : le classpath
        // pertinent est celui du module APPELANT (perdu à cette frontière)
        // — on sonde les moteurs VIVANTS sans en construire : DÉFAUT
        // d'abord, puis modules ([EnvironnementCompilation.moteursVivants]
        // rend déjà cet ordre) ; la première source trouvée gagne.
        val env = envVivant
        if (env != null) {
            for (moteur in env.moteursVivants()) {
                val source = moteur.decompiledSource(params.fqcn)
                if (source != null) {
                    return CompletableFuture.completedFuture(SourceDecompileeRpc(source))
                }
            }
            return CompletableFuture.completedFuture(SourceDecompileeRpc(null))
        }
        val moteur = moteur
            ?: return CompletableFuture.completedFuture(SourceDecompileeRpc(null))
        return CompletableFuture.completedFuture(SourceDecompileeRpc(moteur.decompiledSource(params.fqcn)))
    }

    override fun usagesGroupes(params: TextDocumentPositionParams): CompletableFuture<ReponseUsagesGroupesRpc> {
        garde<ReponseUsagesGroupesRpc>()?.let { return it }
        val uri = params.textDocument.uri
        // Vue VIDE honnête (convention I9) : symbole absent, document
        // disparu ou moteur manquant → groupes vides, total 0.
        val vide = ReponseUsagesGroupesRpc(0, 0, PLAFOND_USAGES_RENDUS, false, emptyList())
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(vide)
        val (chemin, texte) = document
        // V-F1 : même requête moteur que `references` sous le protocole
        // d'époque (rejeu au swap, coupure fine par fichier balayé).
        val usages = avecMoteurFrais(chemin) { moteur ->
            moteur?.references(chemin, texte, offsetDe(texte, params.position), true)
        } ?: return CompletableFuture.completedFuture(vide)
        return CompletableFuture.completedFuture(grouperUsages(usages, PLAFOND_USAGES_RENDUS))
    }
}


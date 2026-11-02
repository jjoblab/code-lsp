package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressReport
import jo.codeide.lsp.java.EnvironnementCompilation
import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.completion.ChaineClassement
import jo.codeide.lsp.java.completion.ContexteClassement
import jo.codeide.lsp.java.completion.CorrespondanceGraduee
import jo.codeide.lsp.java.completion.FrontiereRpc
import jo.codeide.lsp.java.completion.PeseurCompatibilite
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

    /** Le registre des plages visibles (V-F4 — zone visible d'abord). */
    private val plagesVisibles: PlagesVisibles = PlagesVisibles()

    /** Le miroir des versions pour le renommage (forme versionnée). */
    private var serviceRenommage: ServiceRenommage? = null

    /** Les capacités d'édition du client (négociation de forme du rename). */
    @Volatile
    private var capsEspaceDeTravail: CapsEspaceDeTravailClient = CapsEspaceDeTravailClient()

    /** Vrai ssi le client sait rendre un `workDoneProgress` (V-G — négociation). */
    @Volatile
    private var capsProgressionClient: Boolean = false

    /** Vrai ssi le client SAIT RÉSOUDRE un item de complétion (V-G — ADR 0031 (f)). */
    @Volatile
    private var capsResolveCompletion: Boolean = false

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

        // V-G (ADR 0031 (c)) : la capability client `workDoneProgress` —
        // sans elle, le préchauffage d'indexation reste SILENCIEUX (la
        // valeur propre du préchauffage subsiste, aucune notification
        // n'est envoyée — un client qui ne sait pas rendre une progression
        // ne reçoit jamais de bruit).
        capsProgressionClient = params.capabilities?.window?.workDoneProgress ?: false

        // V-G (ADR 0031 (f)) : la capability client `resolveSupport` —
        // sans elle, les éditions d'AUTO-IMPORT restent calculées pour
        // CHAQUE item au moment de la liste (le comportement d'avant V-G,
        // AUCUNE perte pour un client qui ne résout pas) ; avec elle, le
        // calcul est DIFFÉRÉ à l'item CHOISI (`completionItem/resolve`).
        capsResolveCompletion = params.capabilities?.textDocument?.completion?.completionItem?.resolveSupport != null

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
            publicateur = PublicateurDiagnostics(clientOuRefus(), etats, env::moteurPourFichier, plagesVisibles)
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
                publicateur = PublicateurDiagnostics(clientOuRefus(), m, etats, plagesVisibles)
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
        // V-G (ADR 0031 (c), G-032) : le PRÉCHAUFFAGE d'indexation en fil
        // de démon — la première analyse paie la construction froide de
        // l'index de mots ICI (l'utilisateur n'attend rien), et le client
        // qui sait rendre une progression voit « Indexation des sources…
        // n/m » au lieu du silence.
        demarrerPrechauffageIndexation()
    }

    /**
     * Le PRÉCHAUFFAGE D'INDEXATION (V-G — ADR 0031 (c), écart G-032) :
     * un fil de DÉMON force la construction froide de l'index de mots
     * du moteur DÉFAUT juste après `initialized` — le moment le plus
     * creux de la session (aucune requête en vol).
     *
     * **Progression honnête** : le jeton `window/workDoneProgress` n'est
     * créé QUE si le client l'a annoncé ([capsProgressionClient]) ; les
     * `report` suivent les fichiers RÉELLEMENT lexés (jamais de
     * pourcentage fabriqué — l'auditeur moteur appelle après CHAQUE
     * fichier) et sont THROTTLÉS (un report tous les [PAS_REPORT] fichiers
     * minimum, jamais plus de ~50 reports) pour ne pas noyer le fil
     * JSON-RPC ; `end` part TOUJOURS (le jeton ne fuit jamais — même sur
     * exception, le finally le ferme).
     *
     * **Régimes** : figé — le moteur de la racine (aucun moteur = aucun
     * préchauffage, silencieux) ; vivant — le moteur DÉFAUT de
     * l'environnement (les modules suivants se préchauffent à leur
     * première requête, documenté).
     *
     * Kill-switch : `jo.codeide.lsp.java.progression.disabled=true` coupe
     * le préchauffage ENTIER (repli documenté — comportement d'avant V-G).
     */
    private fun demarrerPrechauffageIndexation() {
        if (PROGRESSION_DESACTIVEE) return
        val clientLSP = client ?: return
        val moteurDefaut = moteurPour("") ?: return
        val avecProgression = capsProgressionClient
        val fil = Thread(
            {
                val jeton = "codeide-indexation-${System.nanoTime()}"
                var ouvert = false
                try {
                    if (avecProgression) {
                        // La création du jeton est une REQUÊTE serveur→client
                        // (le client peut refuser — la progression devient
                        // silencieuse, le préchauffage continue).
                        runCatching { clientLSP.createProgress(WorkDoneProgressCreateParams(Either.forLeft(jeton))).get(2, TimeUnit.SECONDS) }
                        ouvert = true
                        val debut = WorkDoneProgressBegin()
                        debut.title = "Indexation des sources"
                        debut.cancellable = false
                        clientLSP.notifyProgress(ProgressParams(Either.forLeft(jeton), Either.forLeft(debut)))
                    }
                    moteurDefaut.prechaufferIndexMots { construits, total ->
                        if (avecProgression && ouvert && total > 0) {
                            val pas = maxOf(1, total / NB_REPORTS_MAX)
                            if (construits == total || construits % pas == 0) {
                                val report = WorkDoneProgressReport()
                                report.percentage = construits * 100 / total
                                runCatching {
                                    clientLSP.notifyProgress(ProgressParams(Either.forLeft(jeton), Either.forLeft(report)))
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Le préchauffage est un GAIN, jamais un devoir : une
                    // panne ici n'affecte aucune requête (l'index se
                    // reconstruit paresseusement au besoin).
                } finally {
                    if (ouvert) {
                        runCatching {
                            clientLSP.notifyProgress(
                                ProgressParams(Either.forLeft(jeton), Either.forLeft(WorkDoneProgressEnd())),
                            )
                        }
                    }
                }
            },
            "lspjava-indexation",
        )
        fil.isDaemon = true
        fil.start()
    }

    private companion object {
        /**
         * Kill-switch du préchauffage/progression (V-G — repli documenté) :
         * `jo.codeide.lsp.java.progression.disabled=true` coupe le fil
         * d'indexation et son `workDoneProgress` (comportement d'avant V-G).
         */
        private val PROGRESSION_DESACTIVEE: Boolean =
            System.getProperty("jo.codeide.lsp.java.progression.disabled") == "true"

        /**
         * Seuil du chien de garde des requêtes lentes (V-A3) : 2 s par
         * défaut, propriété `jo.codeide.lsp.java.lenteur.seuilms`
         * (`0` le désactive, borne haute 60 s).
         */
        private val SEUIL_LENTEUR_MS: Long =
            System.getProperty("jo.codeide.lsp.java.lenteur.seuilms")?.toLongOrNull()
                ?.coerceIn(0L, 60_000L) ?: 2_000L

        /** Le nombre maximal de `report` par préchauffage (throttle du fil). */
        private const val NB_REPORTS_MAX: Int = 50

        /** Le nom LSP d'un genre de jeton (son INDEX dans la légende voyage). */
        private fun nomTypeLsp(genre: jo.codeide.lsp.java.coloration.GenreJeton): String = when (genre) {
            jo.codeide.lsp.java.coloration.GenreJeton.CLASSE -> "class"
            jo.codeide.lsp.java.coloration.GenreJeton.INTERFACE -> "interface"
            jo.codeide.lsp.java.coloration.GenreJeton.ENUM -> "enum"
            jo.codeide.lsp.java.coloration.GenreJeton.ANNOTATION -> "decorator"
            jo.codeide.lsp.java.coloration.GenreJeton.METHODE -> "method"
            jo.codeide.lsp.java.coloration.GenreJeton.PROPRIETE -> "property"
            jo.codeide.lsp.java.coloration.GenreJeton.PARAMETRE -> "parameter"
            jo.codeide.lsp.java.coloration.GenreJeton.VARIABLE -> "variable"
        }

        /** Le bitfield des modificateurs d'un jeton (l'ordre de la légende). */
        private fun modificateursLsp(jeton: jo.codeide.lsp.java.coloration.JetonSemantique): Int {
            var bits = 0
            if (jeton.declaration) bits = bits or (1 shl 0)
            if (jeton.statique) bits = bits or (1 shl 1)
            if (jeton.final) bits = bits or (1 shl 2)
            if (jeton.abstrait) bits = bits or (1 shl 3)
            return bits
        }
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
        // V-H : le répartiteur s'arrête — les requêtes en attente se
        // soldent par leur réponse vide honnête (jamais d'exception au
        // client pour un arrêt de serveur).
        repartiteur.shutdown()
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
     * Le CHIEN DE GARDE des requêtes lentes (V-A3 — retours appareil du
     * 01/11/2026) : chronomètre un calcul serveur et, au-delà du seuil
     * (2 s par défaut, propriété `jo.codeide.lsp.java.lenteur.seuilms`,
     * 0 = désactivé), publie UNE ligne `window/logMessage` (Warning) vers
     * le client — cel-lsp la relaie dans le journal applicatif
     * (« server/logMessage: … ») : le point de lenteur devient VISIBLE
     * sur l'appareil, la prochaine passe ne nécessite plus de deviner.
     *
     * Jamais bloquant, jamais levé : l'observation ne change PAS le
     * comportement du calcul observé.
     */
    private fun <T> guetterLenteur(operation: String, calcul: () -> T): T {
        val seuil = SEUIL_LENTEUR_MS
        if (seuil <= 0) return calcul()
        val debut = System.nanoTime()
        try {
            return calcul()
        } finally {
            val ms = (System.nanoTime() - debut) / 1_000_000L
            if (ms >= seuil) {
                runCatching {
                    clientOuRefus().logMessage(
                        org.eclipse.lsp4j.MessageParams(
                            org.eclipse.lsp4j.MessageType.Warning,
                            "lspjava lenteur : $operation a pris $ms ms (seuil $seuil ms)",
                        ),
                    )
                }
            }
        }
    }

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
        // V-F4 : la zone visible d'un tampon mort ne priorise rien.
        plagesVisibles.oublier(params.textDocument.uri)
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

    override fun didRenameFiles(params: org.eclipse.lsp4j.RenameFilesParams) {
        if (!cycle.requetesPermises()) return
        // V-G : le renommage EFFECTIF — la présence source et l'index de
        // mots doivent réapprendre l'arborescence (le FQCN a changé).
        for (renommage in params.files) {
            val chemin = ConvertisseurLsp.cheminDeUri(renommage.oldUri) ?: continue
            moteurPour(chemin)?.invalidateSourcePresence()
        }
    }

    /**
     * `workspace/willRenameFiles` (V-G — ADR 0031 (d)) : les éditions qui
     * gardent le projet COHÉRENT pendant le geste « renommer / déplacer
     * un `.java` » de l'arbre éditeur — la déclaration de PAQUET du
     * fichier déplacé et les IMPORTS de ses porteurs suivent le NOUVEAU
     * FQCN. Cas doré de la décision : « déplacer une classe publique →
     * package + imports ».
     *
     * La source FRAÎCHE de chaque porteur : le TAMPON OUVERT si le
     * document l'est (l'utilisateur peut éditer pendant le geste), le
     * DISQUE sinon ; un fichier illisible est écarté honnêtement. La
     * réponse est TOUJOURS un WorkspaceEdit (vide si rien à ajuster —
     * jamais null, jamais d'exception).
     */
    override fun willRenameFiles(params: org.eclipse.lsp4j.RenameFilesParams): CompletableFuture<WorkspaceEdit> {
        garde<WorkspaceEdit>()?.let { return it }
        val changes = LinkedHashMap<String, List<org.eclipse.lsp4j.TextEdit>>()
        for (renommage in params.files) {
            val ancienChemin = ConvertisseurLsp.cheminDeUri(renommage.oldUri) ?: continue
            val nouveauChemin = ConvertisseurLsp.cheminDeUri(renommage.newUri) ?: continue
            val ancien = Path.of(ancienChemin)
            val nouveau = Path.of(nouveauChemin)
            val moteur = moteurPour(ancienChemin) ?: continue
            // Le lecteur de source fraîche : tampon ouvert d'abord, disque ensuite.
            val texteDe: (Path) -> String? = { fichier ->
                val uri = ConvertisseurLsp.uriDeFichier(fichier.toString())
                etats.instantane(uri)?.texte
                    ?: runCatching { java.nio.file.Files.readString(fichier) }.getOrNull()
            }
            val editions = runCatching {
                moteur.editionsRenommageFichier(ancien, nouveau, texteDe)
            }.getOrNull() ?: continue
            for ((cheminPorteur, editionsPorteur) in editions.editionsParFichier) {
                val uri = ConvertisseurLsp.uriDeFichier(cheminPorteur)
                val texte = texteDe(Path.of(cheminPorteur)) ?: continue
                val repere = ConvertisseurLsp.repere(texte)
                val textEdits = editionsPorteur.map { edition ->
                    org.eclipse.lsp4j.TextEdit(
                        repere.plage(jo.codeide.lsp.java.diagnostics.Plage(edition.offset, edition.offset + edition.longueur)),
                        edition.texte,
                    )
                }
                // Fusion honnête : un porteur touché par plusieurs renommages
                // cumule ses éditions (elles restent disjointes par construction).
                val existantes = changes[uri] ?: emptyList()
                changes[uri] = existantes + textEdits
            }
        }
        return CompletableFuture.completedFuture(WorkspaceEdit(changes))
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        garde<Any>()?.let { return it }
        return refuse(ResponseErrorCode.MethodNotFound, "commande inconnue : ${params.command}")
    }

    /**
     * La notification `$/codeide/plageVisible` (V-F4 — ADR 0029 phase 4) :
     * enregistre la zone affichée du document — le publicateur s'en sert
     * pour la double publication « visible d'abord » sur les grands
     * fichiers. Hors fenêtre de vie : ignorée silencieusement (la règle
     * maison des notifications — aucune réponse d'erreur possible).
     */
    override fun plageVisible(params: ParamsPlageVisibleRpc) {
        if (!cycle.requetesPermises()) return
        plagesVisibles.notifier(params.uri, params.start, params.end)
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
    // Répartition priorisée des requêtes (V-H — branchement ADR 0010)   //
    // ----------------------------------------------------------------- //

    /**
     * Le répartiteur des requêtes de document (V-H — ADR 0010 : « le
     * scheduler 3 lanes est conservé », chaînon manquant du branchement
     * protocole). Avant V-H, CHAQUE requête était calculée SYNCHRONEMENT
     * sur le fil de lecture lsp4j : sous la tempête d'appareil (inlay +
     * surbrillances + actions + complétion à CHAQUE frappe, mesuré
     * `codeide-logs-2026-11-01-221628.zip` : complétion servie en 29–87 s
     * derrière la file, timeout client 5 s), la complétion patientait
     * derrière TOUT. Le fil de lecture ne porte plus que les magasins
     * (didOpen/didChange : stockage copy-on-write bon marché) et les
     * soumissions ; les calculs courent sur le pool plafonné, la voie
     * FRAPPE devant les voies NAVIGATION et CONFORT.
     */
    private val repartiteur = jo.codeide.lsp.java.concurrence.OrdonnanceurMoteur("requetes")

    /** La voie FRAPPE : complétion, aide de signature (V-H). */
    private val voieFrappe: Int = jo.codeide.lsp.java.concurrence.OrdonnanceurMoteur.PRIORITE_FRAPPE

    /** La voie NAVIGATION : survol, définitions, usages, surbrillances (V-H). */
    private val voieNavigation: Int = 1

    /** La voie CONFORT : inlay, actions, lentilles, pliage, formatage (V-H). */
    private val voieConfort: Int = 2

    /**
     * La capture d'un document À LA SOUMISSION (V-H) : le couple
     * (chemin, texte) + l'instantané D'ÉPOQUE si le document est ouvert.
     *
     * Pourquoi capturer : la requête porte des coordonnées (position LSP)
     * relatives au texte AU MOMENT de son émission ; si le calcul court
     * plus tard sur un texte ayant muté entre-temps, les offsets seraient
     * FAUX (silencieusement). La capture fige le couple exact ; la
     * vérification d'époque à la livraison préserve l'invariant
     * « 0 réponse périmée » : un texte ayant muté rend la réponse VIDE
     * honnête — jamais une réponse calculée contre un texte révolu.
     *
     * @property instantane l'instantané ouvert (null = lecture disque).
     * @property chemin le chemin logique du document.
     * @property texte le texte intégral capturé.
     */
    private class CaptureDocument(
        val instantane: EtatsDocuments.Instantane?,
        val chemin: String,
        val texte: String,
    )

    /** Capture le document (overlay vivant puis disque — convention I9). */
    private fun capturer(uri: String): CaptureDocument? {
        etats.instantane(uri)?.let {
            return CaptureDocument(it, ConvertisseurLsp.cheminDeUri(uri) ?: uri, it.texte)
        }
        val chemin = ConvertisseurLsp.cheminDeUri(uri) ?: return null
        val texte = runCatching { Files.readString(Path.of(chemin)) }.getOrNull() ?: return null
        return CaptureDocument(null, chemin, texte)
    }

    /**
     * Répartit une requête de DOCUMENT sur le pool priorisé (V-H).
     *
     * La capture se fait sur le FIL D'APPEL (cohérence coordonnées ↔
     * texte) ; le calcul court sur un worker — la voie commande l'ordre
     * de service ; la supersession solde par [vide] (réponse VIDE
     * honnête — ou NULL pour les requêtes qui l'admettent, convention
     * I9 du survol) ; la péremption d'époque À LA LIVRAISON solde aussi
     * par [vide] (invariant « 0 réponse périmée »). Un document
     * introuvable répond [vide] immédiatement (miroir des corps
     * historiques).
     *
     * @param uri l'URI du document (clé de supersession/exclusion).
     * @param voie la voie (0 frappe, 1 navigation, 2 confort).
     * @param vide la réponse vide honnête du type de la requête (null
     *   admis — l'effacement Java de [CompletableFuture] tolère la
     *   valeur nulle EXACTEMENT comme l'historique
     *   `completedFuture(survol?.let(::survolLsp))`).
     * @param calcul le corps historique (chemin, texte) → réponse.
     * @return la future de la requête.
     */
    private fun <T : Any> repartirDocument(
        uri: String,
        voie: Int,
        vide: () -> T?,
        calcul: (chemin: String, texte: String) -> T?,
    ): CompletableFuture<T> {
        val capture = capturer(uri)
            ?: return CompletableFuture.completedFuture(vide())
        return repartirCapturee(capture, uri, voie, vide, calcul)
    }

    /**
     * La variante À CAPTURE IMPOSÉE (V-H) : le corps du renommage refuse
     * un document introuvable par une erreur TYPIÉE (I8) au lieu de la
     * réponse vide — il capture LUI-MÊME puis répartit.
     */
    private fun <T : Any> repartirCapturee(
        capture: CaptureDocument,
        uri: String,
        voie: Int,
        vide: () -> T?,
        calcul: (chemin: String, texte: String) -> T?,
    ): CompletableFuture<T> {
        @Suppress("UNCHECKED_CAST")
        val avenir: CompletableFuture<T> =
            repartiteur.soumettrePrioritaire<T?>(uri, voie, {
                val resultat = calcul(capture.chemin, capture.texte)
                // Invariant « 0 réponse périmée » : le document a muté pendant
                // le calcul — la réponse calculée ne part JAMAIS, la requête
                // suivante (déjà soumise par le client) sert l'état neuf.
                if (capture.instantane != null && etats.estPerime(capture.instantane)) vide() else resultat
            }, vide) as CompletableFuture<T>
        return avenir
    }

    // ----------------------------------------------------------------- //
    // Survol, complétion, signatures (fiches 054/058/059)
    // ----------------------------------------------------------------- //

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        garde<Hover>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie NAVIGATION ; convention I9 : hover → null quand il n'y
        // a rien (le client a un repli) — jamais une structure vide.
        // Chien de garde V-A3 : le chronomètre court SUR LE WORKER du
        // répartiteur (le fil de lecture ne porte plus que la soumission).
        return repartirDocument<Hover>(uri, voieNavigation, { null }) { chemin, texte ->
            guetterLenteur("hover") {
                avecMoteurFrais(chemin) { moteur ->
                    moteur?.survolRiche(chemin, texte, offsetDe(texte, params.position))
                }?.let(::survolLsp)
            }
        }
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
        // V-H : voie FRAPPE — la complétion ne patiente jamais derrière la
        // file de confort (inlay/actions) ; capture à la soumission (elle
        // remplace calculerFraiche : la position de la requête est relative
        // au texte À L'ÉMISSION — recalculer contre un texte muté y
        // appliquerait une position révolue ; l'époque à la livraison rend
        // la réponse vide honnête, la requête suivante sert l'état neuf).
        // Chien de garde V-A3 : le chronomètre court sur le worker.
        return repartirDocument(
            uri,
            voieFrappe,
            { Either.forRight(CompletionList(false, emptyList())) },
        ) { chemin, texte ->
            val reponse = guetterLenteur("completion") {
                avecMoteurFrais(chemin) { moteur ->
                    moteur?.completer(chemin, texte, offsetDe(texte, params.position))
                }
            }
            val liste = reponse
                ?: jo.codeide.lsp.java.completion.ReponseCompletion(
                    emptyList(),
                    0,
                    jo.codeide.lsp.java.completion.GenreContexte.INCONNU,
                )
            listeCompletion(uri, texte, liste, params.position).let { Either.forRight(it) }
        }
    }

    /** La CompletionList LSP d'une réponse moteur (I12 : filterText = label
     *  sur CHAQUE item ; data = payload de classement x-codeide). Le texte
     *  est CELUI DE LA CAPTURE (V-H — jamais l'état vivant : le préfixe et
     *  le repère doivent correspondre aux coordonnées de la requête). */
    private fun listeCompletion(
        uri: String,
        texte: String,
        reponse: jo.codeide.lsp.java.completion.ReponseCompletion,
        position: Position,
    ): CompletionList {
        val repere = ConvertisseurLsp.repere(texte)
        val prefixe = prefixeAuCaret(texte, repere.offset(position))
        val contexte = ContexteClassement(reponse.genre, prefixe)
        val peseurs = listOf(PeseurCorrespondance(), PeseurCompatibilite(), PeseurNature(), PeseurLongueur())
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
                compatibilite = (peseurs[1].poids(item, contexte) as Number).toInt(),
                nature = (peseurs[2].poids(item, contexte) as Number).toInt(),
                longueur = (peseurs[3].poids(item, contexte) as Number).toInt(),
            )
            val donnees = CapacitesServeur.donneesItemRanking(
                poids.packe,
                prefixe,
                CorrespondanceGraduee.LONGUEUR_MIN_SOUS_CHAINE,
            )
            item.marqueur?.let { donnees.addProperty("marqueur", it) }
            // V-G : l'URI du document voyage dans data (le resolve en a
            // besoin pour relire le texte courant).
            donnees.addProperty("uri", uri)
            // V-G (ADR 0031 (f)) — l'import DIFFÉRÉ : quand le client sait
            // résoudre, l'édition d'auto-import est RETIRÉE de la liste (les
            // AUTRES éditions additionnelles — snippets — restent) et le
            // FQCN voyage dans data : le resolve de l'item CHOISI la
            // recalcule sur le texte courant (un seul calcul au lieu de N).
            val fqnImport = item.autoImportFqn ?: item.importStatiqueFqn
            if (capsResolveCompletion && fqnImport != null && lsp.additionalTextEdits != null) {
                lsp.additionalTextEdits = lsp.additionalTextEdits
                    ?.filterNot { it.newText.startsWith("import ") } ?: emptyList()
                donnees.addProperty("importFqn", fqnImport)
                donnees.addProperty("importStatique", item.importStatiqueFqn != null)
            }
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
        // V-G (ADR 0031 (f)) — le resolve RÉEL : l'édition d'AUTO-IMPORT de
        // l'item choisi est calculée ICI, sur le texte COURANT du document
        // (miroir exact des règles du calcul à la liste — [ImportsDifferes]).
        // Sans FQCN différé dans data : l'item revient TEL QUEL (jamais
        // d'édition fabriquée, jamais d'exception). Sur le fil, `data`
        // revient comme JsonObject (Gson) — lu par SON API, jamais par un
        // cast de Map.
        val donnees = unresolved.data as? com.google.gson.JsonObject
            ?: return CompletableFuture.completedFuture(unresolved)
        val fqn = donnees.get("importFqn")?.takeIf { it.isJsonPrimitive }?.asString
            ?: return CompletableFuture.completedFuture(unresolved)
        val statique = donnees.get("importStatique")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        val uri = donnees.get("uri")?.takeIf { it.isJsonPrimitive }?.asString
            ?: return CompletableFuture.completedFuture(unresolved)
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(unresolved)
        val (chemin, texte) = document
        val edition = jo.codeide.lsp.java.imports.ImportsDifferes.edition(texte, fqn, statique)
            ?: return CompletableFuture.completedFuture(unresolved)
        val repere = ConvertisseurLsp.repere(texte)
        val editionImport = org.eclipse.lsp4j.TextEdit(
            repere.plage(jo.codeide.lsp.java.diagnostics.Plage(edition.offset, edition.offset + edition.longueur)),
            edition.texte,
        )
        val enrichi = CompletionItem()
        enrichi.label = unresolved.label
        enrichi.kind = unresolved.kind
        enrichi.detail = unresolved.detail
        enrichi.filterText = unresolved.filterText
        enrichi.insertText = unresolved.insertText
        enrichi.insertTextFormat = unresolved.insertTextFormat
        enrichi.data = unresolved.data
        enrichi.additionalTextEdits = (unresolved.additionalTextEdits ?: emptyList()) + editionImport
        return CompletableFuture.completedFuture(enrichi)
    }

    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp> {
        garde<SignatureHelp>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie FRAPPE — l'aide de signature est le popup de frappe.
        // Chien de garde V-A3 : le chronomètre court sur le worker.
        return repartirDocument(uri, voieFrappe, {
            SignatureHelp().apply { signatures = emptyList() }
        }) { chemin, texte ->
            val aide = guetterLenteur("signatureHelp") {
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
            reponse
        }
    }

    // ----------------------------------------------------------------- //
    // Navigation (fiches 054/059 — définitions, références, surlignage)
    // ----------------------------------------------------------------- //

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        garde<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()?.let { return it }
        // V-H : voie NAVIGATION.
        return repartirDocument(
            params.textDocument.uri,
            voieNavigation,
            { Either.forLeft(emptyList()) },
        ) { chemin, texte ->
            Either.forLeft(ciblesNavigation(chemin, texte, params))
        }
    }

    override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        garde<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()?.let { return it }
        // V-H : voie NAVIGATION.
        return repartirDocument(
            params.textDocument.uri,
            voieNavigation,
            { Either.forLeft(emptyList()) },
        ) { chemin, texte ->
            Either.forLeft(ciblesNavigation(chemin, texte, params))
        }
    }

    override fun implementation(params: ImplementationParams): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        garde<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()?.let { return it }
        // V-H : voie NAVIGATION.
        return repartirDocument(
            params.textDocument.uri,
            voieNavigation,
            { Either.forLeft(emptyList()) },
        ) { chemin, texte ->
            Either.forLeft(ciblesNavigation(chemin, texte, params))
        }
    }

    /** Les localisations LSP d'une requête de navigation (I9 : liste vide).
     *  Le couple (chemin, texte) est CELUI DE LA CAPTURE (V-H). */
    private fun ciblesNavigation(chemin: String, texte: String, params: TextDocumentPositionParams): List<Location> {
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
        // V-H : voie NAVIGATION.
        //
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
        return repartirDocument(uri, voieNavigation, { emptyList() }) { chemin, texte ->
            val usages = avecMoteurFrais(chemin) { moteur ->
                moteur?.references(chemin, texte, offsetDe(texte, params.position), params.context.isIncludeDeclaration)
            } ?: return@repartirDocument emptyList()
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
            localisations
        }
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
        // V-H : voie NAVIGATION (la requête la plus fréquente de la
        // tempête d'appareil — un déplacement de caret) ; supersession par
        // voie : seule la plus récente patiente. Chien de garde V-A3 : le
        // chronomètre court sur le worker.
        return repartirDocument(uri, voieNavigation, { emptyList() }) { chemin, texte ->
            val surbrillances = guetterLenteur("documentHighlight") {
                avecMoteurFrais(chemin) { moteur ->
                    moteur?.surbrillances(chemin, texte, offsetDe(texte, params.position))
                }
            } ?: return@repartirDocument emptyList()
            val repere = ConvertisseurLsp.repere(texte)
            surbrillances.map { ConvertisseurLsp.surlignage(it, repere) }
        }
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        garde<List<Either<SymbolInformation, DocumentSymbol>>>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie CONFORT.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val symboles = avecMoteurFrais(chemin) { moteur ->
                moteur?.let { SymbolesDocument(it).symboles(chemin, texte) }
            } ?: return@repartirDocument emptyList()
            symboles.map { Either.forRight(it) }
        }
    }

    // ----------------------------------------------------------------- //
    // Formatage (fiches 054/058 — plein document, plage, à la frappe)
    // ----------------------------------------------------------------- //

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        garde<List<TextEdit>>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie CONFORT.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val formate = avecMoteurFrais(chemin) { moteur -> moteur?.formater(texte) }
            if (formate == null || formate == texte) return@repartirDocument emptyList()
            // Une édition pleine page : le texte reconstruit EST le texte
            // formaté (fiche 054 : « le texte reconstruit ... contient
            // “int x = 1;” précédé de 8 espaces »).
            val repere = ConvertisseurLsp.repere(texte)
            val plage = Range(Position(0, 0), repere.position(texte.length))
            listOf(TextEdit(plage, formate))
        }
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> {
        garde<List<TextEdit>>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie CONFORT.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val repere = ConvertisseurLsp.repere(texte)
            val debut = repere.offset(params.range.start)
            val fin = repere.offset(params.range.end)
            val editions = avecMoteurFrais(chemin) { moteur ->
                moteur?.editionsFormatagePlage(texte, debut, fin - debut)
            }
            editions?.let { ConvertisseurLsp.editions(it, repere) } ?: emptyList()
        }
    }

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> {
        garde<List<TextEdit>>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie CONFORT.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val repere = ConvertisseurLsp.repere(texte)
            val offset = repere.offset(params.position)
            // Fiche 058 : no-op (liste VIDE) si le caractère tapé ne
            // correspond pas au caractère à la position.
            val plage = avecMoteurFrais(chemin) { moteur ->
                moteur?.plageFormattable(chemin, texte, offset, params.ch.singleOrNull() ?: ' ')
            } ?: return@repartirDocument emptyList()
            val editions = avecMoteurFrais(chemin) { moteur ->
                moteur?.editionsFormatagePlage(texte, plage.debutOffset, plage.finOffset - plage.debutOffset)
            }
            editions?.let { ConvertisseurLsp.editions(it, repere) } ?: emptyList()
        }
    }

    // ----------------------------------------------------------------- //
    // Sélection, pliage, inlay, jetons sémantiques (fiches 058/059)
    // ----------------------------------------------------------------- //

    override fun selectionRange(params: SelectionRangeParams): CompletableFuture<List<SelectionRange>> {
        garde<List<SelectionRange>>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie CONFORT.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val repere = ConvertisseurLsp.repere(texte)
            val chaines = avecMoteurFrais(chemin) { moteur ->
                moteur?.chainesSelection(chemin, texte, params.positions.map { repere.offset(it) })
            } ?: return@repartirDocument emptyList()
            chaines.mapNotNull { chaine ->
                chaine?.let { SelectionRangeLsp.deChaine(it, repere) }
            }
        }
    }

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
        garde<List<FoldingRange>>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie CONFORT.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            // I9 : liste non-null VIDE (moteur utilisable + aucun pli).
            val repere = ConvertisseurLsp.repere(texte)
            val plis = avecMoteurFrais(chemin) { moteur -> moteur?.plis(texte) }
                ?: return@repartirDocument emptyList()
            plis.map { pli ->
                val debut = repere.position(pli.debut)
                val fin = repere.position(pli.fin)
                FoldingRange(debut.line, fin.line).apply {
                    startCharacter = debut.character
                    endCharacter = fin.character
                    kind = pli.genre.libelleLsp
                    collapsedText = pli.texteReplie
                }
            }
        }
    }

    override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> {
        garde<List<InlayHint>>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie CONFORT — l'inlay est la requête la plus coûteuse de
        // la tempête d'appareil ; supersession par voie : seule la plus
        // récente patiente, la frappe la rend caduque. Chien de garde
        // V-A3 : le chronomètre court sur le worker.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val indices = guetterLenteur("inlayHint") {
                avecMoteurFrais(chemin) { moteur -> moteur?.inlayHints(chemin, texte) }
            } ?: return@repartirDocument emptyList()
            val repere = ConvertisseurLsp.repere(texte)
            indices.map { indice ->
                InlayHint(repere.position(indice.offset), Either.forLeft(indice.libelle)).apply {
                    kind = InlayHintKind.forValue(indice.genre.codeLsp)
                    paddingLeft = indice.paddingGauche
                    paddingRight = indice.paddingDroit
                }
            }
        }
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        garde<SemanticTokens>()?.let { return it }
        // V-G (ADR 0031 (a)) : la coloration SÉMANTIQUE sur bindings — la
        // réponse est le VECTEUR ENCODÉ EN DELTAS RELATIFS (5 entiers par
        // jeton : deltaLigne, deltaColonne, longueur, type, modificateurs).
        // Document absent ou moteur introuvable : data VIDE (I9 — jamais
        // null, jamais d'exception).
        val uri = params.textDocument.uri
        // V-H : voie CONFORT.
        return repartirDocument(uri, voieConfort, { SemanticTokens(ArrayList()) }) { chemin, texte ->
            val jetons = avecMoteurFrais(chemin) { moteur ->
                moteur?.jetonsSemantiques(chemin, texte)
            } ?: return@repartirDocument SemanticTokens(ArrayList())
            val repere = ConvertisseurLsp.repere(texte)
            val donnees = ArrayList<Int>(jetons.size * 5)
            var lignePrecedente = 0
            var colonnePrecedente = 0
            for (jeton in jetons) {
                val plage = repere.plage(jo.codeide.lsp.java.diagnostics.Plage(jeton.offset, jeton.offset + jeton.longueur))
                val ligne = plage.start.line
                val colonne = plage.start.character
                donnees.add(ligne - lignePrecedente)
                donnees.add(if (ligne == lignePrecedente) colonne - colonnePrecedente else colonne)
                donnees.add(jeton.longueur)
                donnees.add(CapacitesServeur.TYPES_LEGENDE_SEMANTIQUES.indexOf(nomTypeLsp(jeton.genre)).let { if (it < 0) 0 else it })
                donnees.add(modificateursLsp(jeton))
                lignePrecedente = ligne
                colonnePrecedente = colonne
            }
            SemanticTokens(donnees)
        }
    }

    // ----------------------------------------------------------------- //
    // Renommage (fiches 054/059 — prepareRename, rename, erreur typée)
    // ----------------------------------------------------------------- //

    override fun prepareRename(params: PrepareRenameParams): CompletableFuture<Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior>> {
        garde<Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior>>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie NAVIGATION.
        //
        // Complément de catalogue (ADR 0032) — rename DEPUIS UN XML : la
        // ressource au caret d'un document XML du module (déclaration
        // name="…" des values ou référence @type/…) se prépare comme
        // depuis le code Java.
        return repartirDocument<Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior>>(
            uri,
            voieNavigation,
            { null },
        ) { chemin, texte ->
            if (uri.endsWith(".xml")) {
                val preparationXml = avecMoteurFrais(chemin) { _ ->
                    serviceRenommage?.preparerRenommageXml(chemin, texte, offsetDe(texte, params.position))
                }
                return@repartirDocument preparationXml?.let {
                    Either3.forSecond(
                        PrepareRenameResult(
                            ConvertisseurLsp.repere(texte).plage(it.plage),
                            it.placeholder,
                        ),
                    )
                }
            }
            val preparation = avecMoteurFrais(chemin) { _ ->
                serviceRenommage?.preparerRenommage(chemin, texte, offsetDe(texte, params.position))
            }
            // Null = pas renommable à cette position (le client désactive le
            // rename) — la fiche 054 teste le cas NON-null (le champ, forme
            // plage).
            preparation?.let {
                Either3.forSecond(
                    PrepareRenameResult(
                        ConvertisseurLsp.repere(texte).plage(it.plage),
                        it.placeholder,
                    ),
                )
            }
        }
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
        garde<WorkspaceEdit>()?.let { return it }
        val uri = params.textDocument.uri
        val service = serviceRenommage
        // V-H : voie NAVIGATION — le geste explicite du renommage. Le
        // document introuvable garde son refus TYPIÉ (I8 — jamais un
        // WorkspaceEdit vide silencieux), la capture est faite AVANT la
        // répartition.
        //
        // Complément de catalogue (ADR 0032) — rename DEPUIS UN XML : la
        // MÊME propagation multi-fichiers (code Java + XML), le document
        // XML courant servi depuis son tampon OUVERT.
        val capture = capturer(uri)
            ?: return refuse(ResponseErrorCode.RequestFailed, "Aucun symbole renommable")
        return repartirCapturee(capture, uri, voieNavigation, {
            WorkspaceEdit()
        }) { chemin, texte ->
            if (uri.endsWith(".xml")) {
                if (service == null || moteurPour(chemin) == null) {
                    throw ResponseErrorException(
                        ResponseError(ResponseErrorCode.RequestFailed, "Aucun symbole renommable", null),
                    )
                }
                return@repartirCapturee try {
                    val edition = avecMoteurFrais(chemin) { _ ->
                        service.renommageXml(uri, chemin, texte, offsetDe(texte, params.position), params.newName)
                    }
                    espaceDeTravailLsp(edition)
                } catch (e: RenommageSansCible) {
                    throw ResponseErrorException(
                        ResponseError(ResponseErrorCode.RequestFailed, e.message ?: "Aucun symbole renommable", null),
                    )
                }
            }
            // Régime vivant : la garde moteur passe par le ROUTAGE du document
            // renommé (fiche 047) — le champ figé resterait null en mode
            // environnement et rejetterait le rename à tort.
            if (service == null || moteurPour(chemin) == null) {
                // I8 — erreur TYPIE RequestFailed (-32803), message FR contenant
                // « Aucun symbole renommable » (fiche 059) — jamais un
                // WorkspaceEdit vide silencieux. Le rejet passe par un FUTUR
                // EN ERREUR (le contrat d'lsp4j : un throw synchrone serait
                // reformé en InternalError par la couche réflexive).
                throw ResponseErrorException(
                    ResponseError(ResponseErrorCode.RequestFailed, "Aucun symbole renommable", null),
                )
            }
            // V-F1 : le renommage entier vit sous le protocole d'époque du
            // document CIBLE — un swap pendant les phases rejeue le calcul
            // (jamais d'éditions mixtes de deux époques).
            try {
                val edition = avecMoteurFrais(chemin) { _ ->
                    service.renommage(uri, chemin, texte, offsetDe(texte, params.position), params.newName)
                }
                espaceDeTravailLsp(edition)
            } catch (e: RenommageSansCible) {
                throw ResponseErrorException(
                    ResponseError(ResponseErrorCode.RequestFailed, e.message ?: "Aucun symbole renommable", null),
                )
            }
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
        // V-H : voie CONFORT — la tempête d'appareil en fait une requête
        // fréquente (chaque frappe/déplacement de caret).
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            // V-D (ADR 0027) : le filtre `only` de LSP 3.17 — un kind est
            // servi s'il ÉGALE une valeur demandée ou la PROLONGE (préfixe
            // hiérarchique) ; sans `only`, tout est servi (quickfixes +
            // intentions + générateurs).
            val only = params.context.only
            fun demande(kind: String): Boolean =
                only == null || only.isEmpty() || correspondKind(kind, only)
            // Les diagnostics du contexte voyagent en forme moteur (le
            // service N6 consomme les diagnostics moteur).
            val diagnosticsMoteur = params.context.diagnostics.mapNotNull { diagnostic ->
                diagnosticVersMoteur(diagnostic)
            }
            // V-F1 : les actions vivent sous le protocole d'époque du document
            // (rejeu au swap, jamais de correcteurs périmés).
            val actions = avecMoteurFrais(chemin) { moteur ->
                val m = moteur ?: return@avecMoteurFrais emptyList()
                val resultat = mutableListOf<jo.codeide.lsp.java.actionscode.ActionCode>()
                if (demande("quickfix")) {
                    resultat.addAll(jo.codeide.lsp.java.actionscode.ServiceActionsCode(m).actions(chemin, texte, diagnosticsMoteur))
                }
                if (demande("source.organizeImports")) {
                    m.organiserImportsEditions(chemin, texte)?.let { editions ->
                        resultat.add(
                            jo.codeide.lsp.java.actionscode.ActionCode(
                                "source.organizeImports",
                                "Organiser les imports",
                                jo.codeide.lsp.java.actionscode.ModCommande.EditsTexte(editions),
                            ),
                        )
                    }
                }
                // Intentions + générateurs (V-D) : sans `only` tout est servi ;
                // avec `only`, seules les actions dont le KIND correspond.
                if (only == null || only.isEmpty()) {
                    val debut = offsetDe(texte, params.range.start)
                    val fin = offsetDe(texte, params.range.end)
                    resultat.addAll(m.intentions(chemin, texte, debut, fin))
                } else {
                    val debut = offsetDe(texte, params.range.start)
                    val fin = offsetDe(texte, params.range.end)
                    resultat.addAll(m.intentions(chemin, texte, debut, fin).filter { correspondKind(it.kind, only) })
                }
                resultat
            } ?: return@repartirDocument emptyList()
            val repere = ConvertisseurLsp.repere(texte)
            actions.map { action -> Either.forRight(ConvertisseurLsp.actionCode(action, uri, repere)) }
        }
    }

    override fun resolveCodeAction(unresolved: CodeAction): CompletableFuture<CodeAction> {
        garde<CodeAction>()?.let { return it }
        // V-D (ADR 0027 (f)) : la résolution d'une commande DIFFÉRÉE — la
        // liste n'a transporté que (clé, arguments, uri) ; les éditions se
        // recalculent DÉTERMINISTEMENT depuis la source courante. Une
        // action EAGER (édits déjà portés) revient TELLE QUELLE.
        val donnees = (unresolved.data as? com.google.gson.JsonObject)
        val cle = donnees?.get("cle")?.takeIf { it.isJsonPrimitive }?.takeIf { p -> p.asJsonPrimitive.isString }?.asString
        val uri = donnees?.get("uri")?.takeIf { it.isJsonPrimitive }?.takeIf { p -> p.asJsonPrimitive.isString }?.asString
        val arguments = (donnees?.get("arguments") as? com.google.gson.JsonArray)
            ?.mapNotNull { element -> (element as? com.google.gson.JsonPrimitive)?.takeIf { p -> p.isString }?.asString }
            .orEmpty()
        if (cle == null || uri == null) {
            return CompletableFuture.completedFuture(unresolved)
        }
        val document = documentDe(uri) ?: return CompletableFuture.completedFuture(unresolved)
        val (chemin, texte) = document
        // V-D3 : la résolution MULTI-FICHIERS d'abord (change signature —
        // les appelants, suppression hiérarchique — chaque maillon, inline
        // de constante PROJET, création de champ — le type déclarant peut
        // vivre ailleurs) : les éditions par fichier deviennent une map
        // URI → éditions (le document courant sous SON URI, les autres
        // sous `file://<chemin>`, convention du renommage N5).
        val projet = avecMoteurFrais(chemin) { moteur ->
            moteur?.resoudreActionProjet(chemin, texte, cle, arguments)
        }
        if (projet != null && projet.isNotEmpty()) {
            val editionsParUri = java.util.LinkedHashMap<String, List<org.eclipse.lsp4j.TextEdit>>()
            for ((fichier, editions) in projet) {
                if (editions.isEmpty()) continue
                val uriFichier = if (fichier == chemin) uri else "file://$fichier"
                val repereFichier = if (fichier == chemin) {
                    ConvertisseurLsp.repere(texte)
                } else {
                    val texteFichier = try {
                        java.nio.file.Path.of(fichier).toFile().readText()
                    } catch (_: Exception) {
                        null
                    }
                    if (texteFichier != null) ConvertisseurLsp.repere(texteFichier) else continue
                }
                editionsParUri[uriFichier] = ConvertisseurLsp.editions(editions, repereFichier)
            }
            if (editionsParUri.isNotEmpty()) {
                unresolved.edit = org.eclipse.lsp4j.WorkspaceEdit().apply { changes = editionsParUri }
            }
            return CompletableFuture.completedFuture(unresolved)
        }
        val editions = avecMoteurFrais(chemin) { moteur ->
            moteur?.resoudreAction(chemin, texte, cle, arguments)
        } ?: return CompletableFuture.completedFuture(unresolved)
        if (editions == null || editions.isEmpty()) {
            return CompletableFuture.completedFuture(unresolved)
        }
        val repere = ConvertisseurLsp.repere(texte)
        unresolved.edit = org.eclipse.lsp4j.WorkspaceEdit().apply {
            changes = java.util.Collections.singletonMap(uri, ConvertisseurLsp.editions(editions, repere))
        }
        return CompletableFuture.completedFuture(unresolved)
    }

    /**
     * Vrai ssi le kind répond à une demande `only` de LSP 3.17 : égalité
     * OU prolongement hiérarchique (« source » demande
     * « source.organizeImports » ; « source.organizeImports » ne demande
     * PAS « source.generate »).
     */
    private fun correspondKind(kind: String, only: List<String>): Boolean =
        only.any { valeur -> kind == valeur || kind.startsWith("$valeur.") }

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
        // V-H : voie CONFORT. V-F1 : les lentilles vivent sous le protocole
        // d'époque du document.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val lentilles = avecMoteurFrais(chemin) { moteur ->
                val m = moteur ?: return@avecMoteurFrais null
                LentillesCode(m, etats).lentilles(uri, chemin, texte)
            } ?: return@repartirDocument emptyList()
            lentilles
        }
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
        // V-H : voie CONFORT. V-F1 : la préparation vit sous le protocole
        // d'époque du document.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
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
            } ?: return@repartirDocument emptyList()
            listOfNotNull(cible?.let { itemTypeHierarchie(it) })
        }
    }

    override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        garde<List<TypeHierarchyItem>>()?.let { return it }
        val element = params.item
        val (uri, offset) = ancreDe(element.data)
        if (uri == null || offset == null) return CompletableFuture.completedFuture(emptyList())
        // V-H : voie CONFORT. V-F1 : la hiérarchie vit sous le protocole
        // d'époque du document.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val supertypes = avecMoteurFrais(chemin) { moteur ->
                moteur?.supertypesDirects(chemin, texte, offset)
            } ?: return@repartirDocument emptyList()
            supertypes.map { itemTypeHierarchie(it) }
        }
    }

    override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        garde<List<TypeHierarchyItem>>()?.let { return it }
        val element = params.item
        val (uri, offset) = ancreDe(element.data)
        if (uri == null || offset == null) return CompletableFuture.completedFuture(emptyList())
        // V-H : voie CONFORT. V-F1 : la hiérarchie vit sous le protocole
        // d'époque du document.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val sousTypes = avecMoteurFrais(chemin) { moteur ->
                moteur?.sousTypes(chemin, texte, offset)
            } ?: return@repartirDocument emptyList()
            sousTypes.map { itemTypeHierarchie(it) }
        }
    }

    override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<List<CallHierarchyItem>> {
        garde<List<CallHierarchyItem>>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie CONFORT. V-F1 : la préparation vit sous le protocole
        // d'époque du document.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
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
            } ?: return@repartirDocument emptyList()
            items
        }
    }

    override fun callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture<List<CallHierarchyIncomingCall>> {
        garde<List<CallHierarchyIncomingCall>>()?.let { return it }
        val element = params.item
        val (uri, offset) = ancreDe(element.data)
        if (uri == null || offset == null) return CompletableFuture.completedFuture(emptyList())
        // V-H : voie CONFORT. V-F1 : la hiérarchie vit sous le protocole
        // d'époque du document.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val entrants = avecMoteurFrais(chemin) { moteur ->
                moteur?.appelsEntrants(chemin, texte, offset)
            } ?: return@repartirDocument emptyList()
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
            }
        }
    }

    override fun callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture<List<CallHierarchyOutgoingCall>> {
        garde<List<CallHierarchyOutgoingCall>>()?.let { return it }
        val element = params.item
        val (uri, offset) = ancreDe(element.data)
        if (uri == null || offset == null) return CompletableFuture.completedFuture(emptyList())
        // V-H : voie CONFORT. V-F1 : la hiérarchie vit sous le protocole
        // d'époque du document.
        return repartirDocument(uri, voieConfort, { emptyList() }) { chemin, texte ->
            val sortants = avecMoteurFrais(chemin) { moteur ->
                moteur?.appelsSortants(chemin, texte, offset)
            } ?: return@repartirDocument emptyList()
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
            }
        }
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
        // V-H : voie NAVIGATION. V-F1 : la navigation vit sous le protocole
        // d'époque du document.
        return repartirDocument(uri, voieNavigation, { emptyList() }) { chemin, texte ->
            val cibles = avecMoteurFrais(chemin) { moteur ->
                moteur?.superDefinition(chemin, texte, offsetDe(texte, params.position))
            } ?: return@repartirDocument emptyList()
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
            }
        }
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
        // V-H : voie NAVIGATION. V-F1 : même requête moteur que
        // `references` sous le protocole d'époque (rejeu au swap,
        // coupure fine par fichier balayé).
        val vide = { ReponseUsagesGroupesRpc(0, 0, PLAFOND_USAGES_RENDUS, false, emptyList()) }
        return repartirDocument(uri, voieNavigation, vide) { chemin, texte ->
            val usages = avecMoteurFrais(chemin) { moteur ->
                moteur?.references(chemin, texte, offsetDe(texte, params.position), true)
            } ?: return@repartirDocument vide()
            grouperUsages(usages, PLAFOND_USAGES_RENDUS)
        }
    }

    override fun statementCompletion(params: TextDocumentPositionParams): CompletableFuture<ReponseInstructionRpc> {
        garde<ReponseInstructionRpc>()?.let { return it }
        val uri = params.textDocument.uri
        // V-H : voie FRAPPE (la fin d'instruction est un geste de frappe).
        //
        // V-C3 (ADR 0026 (c)) : le calcul est PUREMENT LEXICAL — aucun
        // verrou, aucun arbre, servi dans tous les régimes. Le protocole
        // d'époque ne s'y raccroche pas (aucune résolution sous-jacente).
        return repartirDocument(uri, voieFrappe, {
            ReponseInstructionRpc(null, null, null, null, false)
        }) { chemin, texte ->
            val edition = avecMoteurFrais(chemin) { moteur ->
                moteur?.completerInstruction(chemin, texte, offsetDe(texte, params.position))
            }
            if (edition != null) {
                ReponseInstructionRpc(edition.offset, edition.longueur, edition.texte, edition.caret, true)
            } else {
                ReponseInstructionRpc(null, null, null, null, false)
            }
        }
    }
}


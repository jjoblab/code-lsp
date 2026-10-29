package jo.codeide.lsp.java.protocole

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSerializer
import java.util.Properties
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.CodeLensOptions
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.PositionEncodingKind
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * Les capabilities du serveur + la réponse `initialize` (étage N8 — cahier
 * fiches 055/056/058/059).
 *
 * **Règle matrice (ADR 0015)** : « capacité testée = annoncée ; sinon
 * absente ». Chaque capability ci-dessous renvoie à un comportement du
 * cahier VERROUILLÉ PAR TEST — aucune annonce de `semanticTokens` (aucun
 * service moteur ne le sert ; la requête reste TOLÉRANTE et rend une data
 * vide, fiche 059 I9, mais l'annonce est absente).
 *
 * **Capability maison** (design intégré de l'ancienne vague 4, plan §4 N8 ;
 * ADR 0012 vague (b) ; ADR 0015 : préfixe `x-codeide.`, versionné) :
 * `x-codeide.completionRanking` annonce le protocole riche de classement —
 * chaque item de complétion porte dans son champ `data` un payload de
 * CLASSEMENT (poids packé + matcher sérialisé,
 * [jo.codeide.lsp.java.completion.FrontiereRpc]) qui permet au client le
 * RE-FILTRAGE LOCAL par frappe sans aller-retour serveur (la réponse
 * structurelle au facteur ART 3–5×, ADR 0012).
 *
 * **Sérialisation du champ maison** [mesuré sur le fil — sonde du 29/10] :
 * Gson sérialise les champs du type DÉCLARÉ — une sous-classe de
 * [ServerCapabilities] perd ses champs propres au passage. La capability
 * voyage donc via un [JsonSerializer] dédié enregistré pour
 * `ServerCapabilities` (crochet `configureGson` du `Launcher.Builder`) :
 * la BASE est sérialisée par le Gson lsp4j standard (champs `Either`
 * correctement dépliés), le champ maison est ADDITIONNÉ à l'arbre si la
 * valeur est du type [CapacitesEtendues]. L'absence de la capability chez
 * un client qui ne connaît pas le champ = repli automatique (ADR 0015).
 *
 * **serverInfo** (fiche 055/059) : nom EXACT « lspjava » ; version lue de
 * la ressource `version.properties` EMBARQUÉE (clé `version`) — générée à
 * la construction depuis la version du projet (jamais codée en dur ; la
 * valeur fantôme « 1.2.0 » du vendored est proscrite par la fiche 059 I7).
 */
public object CapacitesServeur {

    /** Le nom du serveur (fiche 055 : serverInfo.name exact « lspjava »). */
    public const val NOM_SERVEUR: String = "lspjava"

    /** Le champ capability maison du protocole riche (ADR 0012/0015). */
    public const val CHAMP_COMPLETION_RANKING: String = "x-codeide.completionRanking"

    /** La version du protocole maison (évolution = nouvelle version, jamais de mutation silencieuse). */
    public const val VERSION_PROTOCOLE_RANKING: Int = 1

    /** Le chemin de la ressource de version embarquée (fiche 059 I7). */
    private const val RESSOURCE_VERSION: String = "/jo/codeide/lsp/java/protocole/version.properties"

    /**
     * Les capabilities étendues : la base standard [ServerCapabilities] PLUS
     * le champ maison [CHAMP_COMPLETION_RANKING].
     *
     * Le champ maison est un payload VERSIONNÉ — un futur protocole v2 se
     * portera en valeur `version: 2` (ou en capability nouvelle), jamais par
     * mutation silencieuse de la sémantique (ADR 0015, risque R1).
     */
    public class CapacitesEtendues : ServerCapabilities() {

        /**
         * Le payload de la capability maison : version du protocole et
         * adresses des champs transportés dans chaque item (`data`).
         *
         * Sérialisé par [serialisateurCapacites] — jamais par réflexion
         * directe (le type déclaré du champ `capabilities` commande Gson).
         */
        internal val payloadRanking: Map<String, Any> = mapOf(
            "version" to VERSION_PROTOCOLE_RANKING,
            "poidsItem" to "x-codeide.poids",
            "correspondanceItem" to "x-codeide.correspondance",
        )
    }

    /**
     * Le sérialiseur des capabilities : base standard via le Gson lsp4j
     * NU + champ maison additionné pour [CapacitesEtendues].
     *
     * À enregistrer sur CHAQUE extrémité qui doit émettre les capabilities
     * étendues (le serveur ; le harnais de test côté client pour lecture
     * brute éventuelle) :
     *
     * ```kotlin
     * Launcher.Builder<...>()
     *     .configureGson { it.registerTypeAdapter(ServerCapabilities::class.java, serialisateurCapacites()) }
     * ```
     *
     * La DÉSÉRIALISATION reste réflexive standard (un client qui ignore le
     * champ maison le perd silencieusement — comportement spec LSP pour
     * les champs inconnus ; repli automatique ADR 0015).
     *
     * @return le sérialiseur à enregistrer pour `ServerCapabilities`.
     */
    public fun serialisateurCapacites(): JsonSerializer<ServerCapabilities> {
        val gsonBase = MessageJsonHandler(
            emptyMap<String, org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod>(),
            { _: GsonBuilder -> },
        ).defaultGsonBuilder.create()
        return JsonSerializer { source, _, _ ->
            val arbre = gsonBase.toJsonTree(source, ServerCapabilities::class.java).asJsonObject
            if (source is CapacitesEtendues) {
                arbre.add(CHAMP_COMPLETION_RANKING, gsonBase.toJsonTree(source.payloadRanking))
            }
            arbre
        }
    }

    /**
     * La valeur `data` à transporter sur CHAQUE item de complétion (le
     * câblage protocole du design ADR 0012 — « frontière RPC en naissance »
     * posée par l'étage N2 dans [jo.codeide.lsp.java.completion.FrontiereRpc]).
     *
     * Le client qui a négocié la capability relit ces champs pour :
     *  - RE-FILTRER localement par préfixe (matcher sérialisé —
     *    `correspondance.prefixe` + `longueurMinSousChaine`) ;
     *  - RE-TRIER localement par poids packé (`poids` : plus petit = plus
     *    prioritaire, contrat verrouillé ADR 0012 R1).
     *
     * La forme est un [JsonObject] (le champ `data` LSP est libre) — les
     * clés sont stables et versionnées par [CHAMP_COMPLETION_RANKING].
     *
     * @param poidsPacke le poids packé de l'item (FrontiereRpc.PoidsRpc.packe).
     * @param prefixe le préfixe tapé au moment de l'envoi.
     * @param longueurMinSousChaine la règle « substring ≥ 3 » (fiche 008).
     * @return l'objet JSON transporté dans `CompletionItem.data`.
     */
    public fun donneesItemRanking(poidsPacke: Long, prefixe: String, longueurMinSousChaine: Int): JsonObject {
        val racine = JsonObject()
        racine.addProperty("x-codeide.poids", poidsPacke)
        val correspondance = JsonObject()
        correspondance.addProperty("prefixe", prefixe)
        correspondance.addProperty("longueurMinSousChaine", longueurMinSousChaine)
        racine.add("x-codeide.correspondance", correspondance)
        return racine
    }

    /**
     * Construit la réponse `initialize` complète (fiche 055 : « InitializeResult
     * non-null avec capabilities non-null et serverInfo non-null »).
     *
     * Les formes verrouillées par le cahier :
     *  - `positionEncoding` = UTF-16 (fiche 055, valeur exacte) ;
     *  - `codeActionProvider` = forme DÉTAILLÉE avec `codeActionKinds`
     *    contenant « quickfix » (fiche 055) ;
     *  - `selectionRangeProvider`/`callHierarchyProvider`/`typeHierarchyProvider`/
     *    `workspaceSymbolProvider`/`foldingRangeProvider` = booléens VRAIS
     *    (côté GAUCHE du Either — fiches 056/058) ;
     *  - `documentOnTypeFormattingProvider` : premier déclencheur « } »,
     *    second « ; » (fiche 058 — déclencheurs exacts) ;
     *  - `codeLensProvider` : `resolveProvider` VRAI (fiche 058 — resolve
     *    paresseux) ;
     *  - `renameProvider` : forme options avec `prepareProvider` (fiche 054 :
     *    prepareRename servi).
     *
     * @return la réponse initialize (capabilities + serverInfo).
     */
    public fun reponseInitialize(): InitializeResult {
        val capacites = CapacitesEtendues()

        // Synchronisation plein texte (fiche 059 : didChange full-sync,
        // didSave sans texte → republier depuis le buffer vivant).
        capacites.setTextDocumentSync(TextDocumentSyncKind.Full)

        // Encodage des positions (fiche 055 : valeur exacte UTF-16).
        capacites.setPositionEncoding(PositionEncodingKind.UTF16)

        // Complétion (étage N2 — fiches 007/008/036/043/044/050/053).
        capacites.setCompletionProvider(
            CompletionOptions(false, listOf(".")),
        )

        // Survol (étage N3 — fiches 020/021), aide de signature (fiche 023).
        capacites.setHoverProvider(true)
        capacites.setSignatureHelpProvider(SignatureHelpOptions(listOf("(", ",")))

        // Navigation (étage N4 — fiches 005/009/049/060/062 ; fiche 054
        // pour les références et le surlignage).
        capacites.setDefinitionProvider(true)
        capacites.setTypeDefinitionProvider(true)
        capacites.setImplementationProvider(true)
        capacites.setReferencesProvider(true)
        capacites.setDocumentHighlightProvider(true)
        capacites.setDocumentSymbolProvider(true)

        // Formatage (étage N4/N7 — fiches 016/017/057 ; fiche 054 pour le
        // document ENTIER ; fiche 058 pour la frappe).
        capacites.setDocumentFormattingProvider(true)
        capacites.setDocumentRangeFormattingProvider(true)
        capacites.setDocumentOnTypeFormattingProvider(
            DocumentOnTypeFormattingOptions("}", listOf(";")),
        )

        // Sélection (fiche 027/058 : chaîne strictement croissante).
        capacites.setSelectionRangeProvider(true)

        // Pliage (étage N7 — fiches 011/052 ; forme booléenne gauche).
        capacites.setFoldingRangeProvider(true)

        // Actions de code (étage N6 — fiches 063/064/065/051 ; fiche 055 :
        // forme DÉTAILLÉE avec quickfix).
        val optionsActions = CodeActionOptions()
        optionsActions.codeActionKinds = listOf(CodeActionKind.QuickFix)
        capacites.setCodeActionProvider(optionsActions)

        // Lentilles (fiche 058 : resolve paresseux).
        capacites.setCodeLensProvider(CodeLensOptions(true))

        // Renommage (étage N5 — fiches 026/061 ; prepareRename servi, fiche 054).
        val optionsRenommage = RenameOptions()
        optionsRenommage.prepareProvider = true
        capacites.setRenameProvider(optionsRenommage)

        // Hiérarchies (étage N7 — fiche 018 ; formes booléennes gauches,
        // fiche 058).
        capacites.setCallHierarchyProvider(true)
        capacites.setTypeHierarchyProvider(true)

        // Symboles d'espace de travail (fiche 056 : forme booléenne TRUE).
        capacites.setWorkspaceSymbolProvider(true)

        // Indices inlay (étage N3 — fiches 002/003/030 ; fiche 059 : liste
        // vide honnête).
        capacites.setInlayHintProvider(true)

        // PAS de semanticTokens : aucun service moteur — règle matrice
        // (ADR 0015 : capacité non testée = non annoncée).

        return InitializeResult(capacites, ServerInfo(NOM_SERVEUR, versionDuDepot()))
    }

    /**
     * La version du serveur lue de la ressource EMBARQUÉE (fiche 059 I7) :
     * `jo/codeide/lsp/java/protocole/version.properties`, clé `version` —
     * générée à la construction depuis la version Gradle du projet.
     *
     * @return la version lue, ou « inconnue » si la ressource manque (cas
     *   de banc sans ressources — JAMAIS la fantôme « 1.2.0 »).
     */
    public fun versionDuDepot(): String = runCatching {
        val prop = Properties()
        CapacitesServeur::class.java.getResourceAsStream(RESSOURCE_VERSION)?.use { flux ->
            prop.load(flux)
        }
        prop.getProperty("version")?.takeIf { it.isNotBlank() } ?: VERSION_INCONNUE
    }.getOrDefault(VERSION_INCONNUE)

    /** La version de repli (jamais la fantôme « 1.2.0 » proscrite). */
    public const val VERSION_INCONNUE: String = "inconnue"
}

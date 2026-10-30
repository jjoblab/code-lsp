package jo.codeide.lsp.java.protocole

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer

/**
 * Les méthodes EXPÉRIMENTALES du serveur (étage N8 — cahier fiche 054,
 * notes de réécriture : « superDefinition et decompiledSource sont des
 * méthodes EXPÉRIMENTALES (extensions hors protocole LSP standard) »).
 *
 * Ces extensions sont héritées du protocole maison du vendored : le
 * client CodeIDE les appelle par leur nom JSON-RPC exact
 * (`superDefinition`, `decompiledSource`) — un client standard qui ne
 * les connaît pas ne les appelle jamais (aucune interférence avec le
 * protocole 3.17).
 *
 *  - `superDefinition` : le « GO TO Super » — la méthode de BASE d'une
 *    redéfinition au caret. La réponse est une liste de cibles à champ
 *    `path` (la forme du vendored, verrouillée par la fiche 054 : « son
 *    chemin se termine par Base.java ») ; depuis l'intégration
 *    CodeIDE (INTEGRATION-CODEIDE §2), les champs suivent le CONTRAT
 *    DE SÉRIALISATION exact du vendored (`NavigationTarget` : `path`,
 *    `start`, `end`, `label`) — le client cel-lsp consomme son miroir
 *    TEL QUEL, aucune adaptation client n'est requise ;
 *  - `decompiledSource` : la source d'un FQCN BINAIRE (la définition a
 *    répondu `jdt://decompiled/<fqcn>` — le client demande ici le
 *    tampon lecture-seule). La réponse porte un champ `source`.
 *
 * L'interface est consommée par [ServeurLangageJava] (implémentation
 * locale) et par le harnais de test (proxy distant via
 * `ServiceEndpoints.toServiceObject` — lsp4j découvre les méthodes
 * `@JsonRequest` sur TOUTE interface du service local).
 *
 * **NOMS SUR LE FIL (incident appareil du 30/10/2026)** : le client
 * cel-lsp (`CodeIdeLanguageServer`) appelle ces extensions par leur nom
 * COMPLET `textDocument/superDefinition` /
 * `textDocument/decompiledSource` — le préfixe `textDocument/` hérite du
 * `@JsonSegment("textDocument")` de `TextDocumentService` chez le
 * VENDORED (méthode déclarée dans `NavTextDocumentService`, sous-interface
 * du service de documents). Déclarer `@JsonRequest` NU sur une interface
 * HORS hiérarchie `TextDocumentService` (le cas de cette interface)
 * enregistrait la méthode sous son nom NU (`superDefinition`) : le serveur
 * rejetait la requête réelle du client par
 * « Unsupported request method: textDocument/superDefinition » (journal
 * appareil 025629, 09:34). Les noms EXPLICITES ci-dessous alignent le fil
 * sur le contrat du client SANS toucher à la hiérarchie des interfaces.
 */
public interface ServiceExperimental {

    /**
     * La méthode de BASE d'une redéfinition (GO TO Super).
     *
     * @param params la position du caret (URI + position) sur le NOM de
     *   la redéfinition.
     * @return les cibles (liste VIDE honnête si le caret ne porte pas de
     *   redéfinition — convention I9).
     */
    @JsonRequest("textDocument/superDefinition")
    public fun superDefinition(params: TextDocumentPositionParams): CompletableFuture<List<CibleSuperRpc>>

    /**
     * La source d'un FQCN binaire (attachée ou décompilée).
     *
     * @param params le FQCN demandé (scénario de production : la
     *   définition a répondu `jdt://decompiled/<fqcn>`).
     * @return la réponse (champ `source` null si le FQCN est introuvable).
     */
    @JsonRequest("textDocument/decompiledSource")
    public fun decompiledSource(params: ParamsFqcnRpc): CompletableFuture<SourceDecompileeRpc>
}

/**
 * L'interface DISTANTE complète du serveur Java (étage N8) :
 * [LanguageServer] PLUS les [ServiceExperimental] — le client qui
 * négocie les extensions maison construit son proxy sur CETTE interface
 * (le lanceur n'enregistre les types de retour que des méthodes de
 * l'interface distante déclarée — un client standard utilise
 * [LanguageServer] et ignore simplement les méthodes inconnues).
 */
public interface LangageServeurDistant : LanguageServer, ServiceExperimental

/**
 * Une cible « GO TO Super » sur le fil (fiche 054 : « superDefinition
 * rend une liste de cibles à champ path ») — champs ALIGNÉS sur le
 * contrat de sérialisation du vendored (`NavigationTarget`), miroir
 * exact du client cel-lsp : Gson mappe les propriétés une à une, le
 * client lit `path`/`start`/`end`/`label` sans modification.
 *
 * @property path le chemin de la source de la méthode de BASE (sans
 *   préfixe `file://`).
 * @property start l'offset du NOM déclaré dans cette source.
 * @property end la fin (exclusive) du nom.
 * @property label le libellé picker (« nom  ·  conteneur » — deux
 *   espaces autour du séparateur, contrat N4).
 */
public data class CibleSuperRpc(
    public val path: String,
    public val start: Int,
    public val end: Int,
    public val label: String,
)

/**
 * Le paramètre de `decompiledSource` : le FQCN binaire demandé.
 *
 * @property fqcn le nom canonique (p. ex. « java.lang.String »).
 */
public data class ParamsFqcnRpc(
    public val fqcn: String,
)

/**
 * La réponse de `decompiledSource` (fiche 054 : « champ source non-null,
 * non vide (blank), contenant “String” »).
 *
 * @property source la source servie (attachée ou décompilée), ou null.
 */
public data class SourceDecompileeRpc(
    public val source: String?,
)

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
 *    chemin se termine par Base.java ») ;
 *  - `decompiledSource` : la source d'un FQCN BINAIRE (la définition a
 *    répondu `jdt://decompiled/<fqcn>` — le client demande ici le
 *    tampon lecture-seule). La réponse porte un champ `source`.
 *
 * L'interface est consommée par [ServeurLangageJava] (implémentation
 * locale) et par le harnais de test (proxy distant via
 * `ServiceEndpoints.toServiceObject` — lsp4j découvre les méthodes
 * `@JsonRequest` sur TOUTE interface du service local).
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
    @JsonRequest
    public fun superDefinition(params: TextDocumentPositionParams): CompletableFuture<List<CibleSuperRpc>>

    /**
     * La source d'un FQCN binaire (attachée ou décompilée).
     *
     * @param params le FQCN demandé (scénario de production : la
     *   définition a répondu `jdt://decompiled/<fqcn>`).
     * @return la réponse (champ `source` null si le FQCN est introuvable).
     */
    @JsonRequest
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
 * rend une liste de cibles à champ path »).
 *
 * @property path le chemin de la source de la méthode de BASE.
 * @property libelle le libellé picker (« nom  ·  conteneur » — deux
 *   espaces autour du séparateur, contrat N4).
 */
public data class CibleSuperRpc(
    public val path: String,
    public val libelle: String,
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

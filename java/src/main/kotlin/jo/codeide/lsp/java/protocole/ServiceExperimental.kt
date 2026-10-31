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

    /**
     * Les usages du symbole au caret, GROUPÉS PAR FICHIER (vague V-E —
     * ADR 0028 (e) : « métadonnées de regroupement dans la réponse, le
     * rendu arborescent revient au client »).
     *
     * Même requête moteur que `textDocument/references` (usages
     * multi-fichiers exhaustifs par clé de binding — la DÉCLARATION est
     * TOUJOURS incluse : la vue arborescente « Find Usages » l'affiche
     * comme racine), servie dans la forme groupée que la référence
     * appelle « groupés par fichiers » : un groupe par fichier porteur,
     * ordre de première apparition de la liste triée (chemin, offset).
     *
     * Le PLAFOND [PLAFOND_USAGES_RENDUS] borne le volume RENDU (risque R1
     * de l'ADR 0028 : jamais de troncature silencieuse) : la réponse
     * annonce TOUJOURS le `total` exact, le `plafond` appliqué et le
     * drapeau `tronque` — un client peut affiner sa recherche sur cette
     * information. Le plafond est annoncé dans la capability
     * `x-codeide.usages` (champ `experimental` de `initialize`).
     *
     * @param params la position du caret (URI + position) sur le symbole.
     * @return les groupes d'usages avec le total exact et l'état de
     *   troncature (groupes vides honnêtes si le caret ne porte pas de
     *   symbole — convention I9).
     */
    @JsonRequest("textDocument/usagesGroupes")
    public fun usagesGroupes(params: TextDocumentPositionParams): CompletableFuture<ReponseUsagesGroupesRpc>
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

/**
 * Le plafond d'usages RENDUS par `textDocument/usagesGroupes` (ADR 0028,
 * risque R1 : « ordre + plafond honnête documenté — jamais de troncature
 * silencieuse : annoncer le total »). Au-delà, la réponse est tronquée
 * DANS L'ORDRE TRIÉ (les premiers fichiers) et le porte l'annonce
 * (`total`, `plafond`, `tronque`) ; la liste plate `references` standard
 * reste EXHAUSTIVE (aucune troncature — un client standard consomme ce
 * qu'il veut de la liste).
 */
public const val PLAFOND_USAGES_RENDUS: Int = 2000

/**
 * Un usage sur le fil de `usagesGroupes` — offsets ABSOLUS dans le texte
 * du fichier porteur (même convention que `CibleSuperRpc` : jamais de
 * conversion ligne/colonne, le client convertit s'il en a besoin).
 *
 * @property start l'offset du JETON de l'occurrence.
 * @property end la fin (exclusive) du jeton.
 * @property estDeclaration vrai ssi l'occurrence EST la déclaration (le
 *   client l'épingle en tête de son groupe — racine de la vue).
 */
public data class UsageRpc(
    public val start: Int,
    public val end: Int,
    public val estDeclaration: Boolean,
)

/**
 * Un groupe d'usages : TOUTES les occurrences portées par UN fichier,
 * dans l'ordre des offsets.
 *
 * @property path le chemin du fichier porteur (sans préfixe `file://` —
 *   même convention que `CibleSuperRpc`).
 * @property usages les occurrences triées par offset croissant.
 */
public data class GroupeUsagesRpc(
    public val path: String,
    public val usages: List<UsageRpc>,
)

/**
 * La réponse de `usagesGroupes` : les groupes par fichier PLUS les
 * métadonnées du plafond honnête (ADR 0028 R1).
 *
 * @property total le nombre EXACT d'usages trouvés (jamais plafonné).
 * @property rendus le nombre d'usages effectivement rendus (≤ plafond).
 * @property plafond le plafond appliqué (miroir de [PLAFOND_USAGES_RENDUS]).
 * @property tronque vrai ssi des usages ont été écartés par le plafond.
 * @property groupes un groupe par fichier porteur (ordre de première
 *   apparition dans la liste triée chemin, offset).
 */
public data class ReponseUsagesGroupesRpc(
    public val total: Int,
    public val rendus: Int,
    public val plafond: Int,
    public val tronque: Boolean,
    public val groupes: List<GroupeUsagesRpc>,
)

/**
 * Regroupe les usages par fichier porteur et applique le plafond honnête
 * (ADR 0028 R1) : les [plafond] PREMIERS usages de l'ordre rendu sont
 * groupés, le total EXACT et le drapeau `tronque` annoncent le reste —
 * jamais de troncature silencieuse.
 *
 * L'entrée est attendue TRIÉE (chemin, offset) — l'ordre du moteur — et
 * l'ordre des groupes suit la première apparition (fichiers
 * alphabétiques pour l'ordre du moteur).
 *
 * @param usages les usages triés du moteur (déclaration incluse ou non
 *   — décision de l'appelant).
 * @param plafond le nombre maximal d'usages rendus (≤ 0 : réponse vide
 *   mais le total reste exact).
 * @return la réponse groupée avec ses métadonnées de plafond.
 */
public fun grouperUsages(usages: List<jo.codeide.lsp.java.navigation.Usage>, plafond: Int): ReponseUsagesGroupesRpc {
    val total = usages.size
    val rendus = if (plafond > 0) usages.take(plafond) else emptyList()
    val parFichier = LinkedHashMap<String, MutableList<UsageRpc>>()
    for (usage in rendus) {
        parFichier.getOrPut(usage.chemin) { ArrayList() }.add(
            UsageRpc(usage.plage.debutOffset, usage.plage.finOffset, usage.estDeclaration),
        )
    }
    return ReponseUsagesGroupesRpc(
        total = total,
        rendus = rendus.size,
        plafond = plafond,
        tronque = total > rendus.size,
        groupes = parFichier.map { (chemin, occurrences) -> GroupeUsagesRpc(chemin, occurrences) },
    )
}

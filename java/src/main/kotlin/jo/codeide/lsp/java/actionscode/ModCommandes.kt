package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.outils.appliquerEditions

/**
 * Le plan déclaratif d'un correcteur (étage N6 — ADR 0013, modèle
 * ModCommand d'IntelliJ étudié dans `platform/analysis-api`, greffé nulle
 * part) : une hiérarchie scellée de commandes **sérialisables** — la
 * javadoc du modèle d'origine : « every ModCommand can be easily
 * serialized and sent e.g., over the network ».
 *
 * Le contrat du modèle (ADR 0013, option 1) : le quick-fix est **calculé en
 * lecture comme un plan déclaratif** (textes ancien/nouveau par fichier),
 * prévisualisable sur copie ([ActionCode.apercu]), et son **exécution est
 * déléguée au client LSP** — le moteur n'écrit JAMAIS sur le disque,
 * l'annulation (undo) appartient au client. Les éditions voyagent en ordre
 * croissant d'offset, plages DISJOINTES (contrat transverse de
 * [EditionTexte], fiche 016).
 *
 * N6 ne couvre que la commande de texte ; les variantes navigate / rename /
 * conflict de la grille ADR 0013 sont des points d'extension DOCUMENTÉS —
 * le renommage et les conflits relèvent du protocole dédié N5
 * ([jo.codeide.lsp.java.renommage], ADR 0014 : « refactoring multi-fichiers
 * complexe → protocoles rename/usages, pas des édits impératifs »), la
 * navigation de la couche LSP N8. La sérialisation elle-même (JSON) est le
 * travail de la couche hôte : la forme data class — champs stables, types
 * simples — EST le contrat de sérialisation.
 */
public sealed interface ModCommande {

    /**
     * Éditions de texte dans le FICHIER du diagnostic (fiche 063 : les
     * quick-fixes de l'étage sont mono-fichier — le multi-fichiers passe
     * par les protocoles N5/N8, jamais par ces édits).
     *
     * @property editions les éditions, en ordre croissant d'offset, plages
     *   disjointes ; JAMAIS vide (fiche 063 : « aucune action sans édits »
     *   — une action coquille vide ne sort pas du service).
     */
    public data class EditsTexte(public val editions: List<EditionTexte>) : ModCommande
}

/**
 * Une action de code servie par [ServiceActionsCode] (étage N6 — cahier
 * fiche 063) : le miroir moteur d'un `CodeAction` LSP — un kind verrouillé,
 * un titre humain, un plan déclaratif [ModCommande] et le diagnostic source
 * (le `context.diagnostics` de la requête LSP : le client rafraîchit la
 * squiggle une fois l'action appliquée).
 *
 * Kinds verrouillés par le cahier (fiche 063, notes de réécriture) :
 * `quickfix.remove.import`, `quickfix.semicolon`, `quickfix.remove.unused`,
 * `quickfix.surround.trycatch`, `quickfix.declare.throws`, `quickfix.cast`,
 * `quickfix.import.fqn`, `quickfix.create.method`.
 *
 * @property kind le kind machine (famille de correcteur).
 * @property titre le titre humain (« Supprimer l'import inutilisé »…).
 * @property commande le plan déclaratif sérialisable (ADR 0013).
 * @property diagnostic le diagnostic corrigé par cette action (null pour
 *   les actions synthétiques de banc).
 */
public data class ActionCode(
    public val kind: String,
    public val titre: String,
    public val commande: ModCommande,
    public val diagnostic: Diagnostic? = null,
) {

    /** Les éditions du plan (vide si la commande n'est pas textuelle). */
    public val editions: List<EditionTexte>
        get() = (commande as? ModCommande.EditsTexte)?.editions ?: emptyList()

    /**
     * L'aperçu sur COPIE (ADR 0013 : « preview sur copie ») : le texte
     * résultat de l'application du plan à la source — l'équivalent moteur
     * de l'aperçu N5 ([jo.codeide.lsp.java.renommage] : serveur-calcule /
     * client-applique).
     *
     * @param source le texte Java complet du fichier du diagnostic.
     * @return le texte après application des éditions (la source intacte si
     *   la commande n'est pas textuelle).
     */
    public fun apercu(source: String): String = appliquerEditions(source, editions)
}

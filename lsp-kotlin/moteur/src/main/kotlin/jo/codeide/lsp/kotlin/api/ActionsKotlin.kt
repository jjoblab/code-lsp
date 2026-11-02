// CodeIDE — SPI du moteur Kotlin LSP : plans d'action DÉCLARATIFS (module
// :kotlin, étage 5 vague 5d — ADR 0115 option 1 : les quick-fixes
// migrent vers un modèle déclaratif sérialisable, miroir ModCommand —
// ADR 0013 Java : LE MODÈLE, PAS LE CODE). Code NEUF jjoblab (clean-room
// — frontière D6 : l'hôte traduit en CodeAction/WorkspaceEdit LSP).
package jo.codeide.lsp.kotlin.api

/**
 * Le genre d'un plan d'action (vocabulaire neutre — l'hôte le traduit en
 * `CodeActionKind` LSP : [RAPIDE] vers `quickfix`, [SOURCE] vers
 * `source`).
 */
public enum class GenreActionKotlin {

    /** Un correctif rapide proposé SUR un diagnostic (quick-fix). */
    RAPIDE,

    /** Une action de source proposée sur le document (organiser, générer). */
    SOURCE,
}

/**
 * Les éditions d'UN fichier d'un plan d'action — le morceau d'un
 * WorkspaceEdit (l'hôte regroupe [PlanActionKotlin.editions] par uri).
 *
 * @property uri l'identifiant du fichier édité ;
 * @property editions les éditions du fichier, en ordre du document.
 */
public class EditionFichierKotlin(
    public val uri: String,
    public val editions: List<EditionTexteKotlin>,
)

/**
 * Un PLAN D'ACTION déclaratif (ADR 0115 — miroir ModCommand) : le titre
 * présenté à l'usager, le genre, et les ÉDITIONS par fichier qui le
 * composent — un plan SÉRIALISABLE, calculé en LECTURE (le moteur
 * RE-PROUVE le document à la requête : un plan périmé n'existe pas),
 * prévisualisable (l'hôte applique les éditions sur une copie), et dont
 * l'EXÉCUTION est déléguée au client (l'hôte applique le WorkspaceEdit
 * — le moteur n'écrit jamais).
 *
 * Les plans sont ALIGNÉS SUR LES PREUVES COURANTES par construction :
 * un plan d'import n'existe que si le nom est non résolu avec
 * candidats ; un plan de correctif n'existe que si le diagnostic
 * correspondant existe (fiche 016) ; sans preuve, la liste est VIDE —
 * jamais une action inventée (D3).
 *
 * @property titre le titre lisible (`« Importer android.widget.TextView »`) ;
 * @property genre le genre neutre de l'action ;
 * @property editions les éditions PAR FICHIER (une par fichier touché —
 * le plus souvent une seule : le document courant).
 */
public class PlanActionKotlin(
    public val titre: String,
    public val genre: GenreActionKotlin,
    public val editions: List<EditionFichierKotlin>,
)

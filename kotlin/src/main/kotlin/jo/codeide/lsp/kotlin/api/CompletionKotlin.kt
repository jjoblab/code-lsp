// CodeIDE — SPI du moteur Kotlin LSP : complétion (module :kotlin, étage 2
// vague 2a — le GERME membre requis par la fiche 022 du cahier : « data
// class : copy résolu ET PROPOSÉ » ; le pipeline complet — contextuelle,
// chaîne de classement, multi-langue — est l'étage 3, ADR 0113). Code NEUF
// jjoblab (clean-room — le modèle croîtra par vagues, comme ClasspathKotlin
// à la vague 1b).
package jo.codeide.lsp.kotlin.api

/**
 * Une proposition de complétion Kotlin (modèle neutre — l'hôte la traduit
 * en `CompletionItem` LSP).
 *
 * Étage 2 (vague 2a) : le libellé NU du membre proposé (le germe membre de
 * la fiche 022 — `copy` proposé après `p.`) ; l'étage 3 (ADR 0113) étend le
 * modèle (détail, genre, import auto-éditable `fqnImport`, classement).
 *
 * @property libelle le libellé rendu (`copy`, `somme`) — préfixe du nom
 * appelé.
 */
public class PropositionKotlin(
    public val libelle: String,
)

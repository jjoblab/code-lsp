// CodeIDE — SPI des capacités ARBRE du moteur Kotlin LSP (module :kotlin,
// vague V-I1 — ADR 0136 étape B.2 : les capacités décidables sur l'arbre
// SEULE, avant le palier 2). Code NEUF jjoblab (clean-room — les trois
// familles : plages liées des délimiteurs appariés, couleurs littérales,
// ré-indentation à la frappe).
package jo.codeide.lsp.kotlin.api

import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Une PAIRE de plages LIÉES (vague V-I1 — `textDocument/linkedEditingRange`)
 * : les deux délimiteurs APPARIÉS d'une construction — `(`…`)`, `{`…`}`,
 * `[`…`]` ou les `<`…`>` d'une liste d'arguments de type. Éditer l'un
 * renvoie l'autre (le client lie les deux pour la suppression en miroir).
 *
 * @property plageOuvrante la plage du délimiteur OUVRANT ;
 * @property plageFermante la plage du délimiteur FERMANT apparié.
 */
public class PlagesLieesKotlin(
    public val plageOuvrante: PlageTexte,
    public val plageFermante: PlageTexte,
)

/**
 * Une COULEUR littérale du document (vague V-I1 —
 * `textDocument/documentColor`) : un littéral entier HEXADÉCIMAL Android
 * (`0xFF1A73E8`, `0x1A73E8` — 8 chiffres = alpha compris, 6 = opaque),
 * rendu avec ses canaux normalisés [0, 1] pour le color picker du client.
 *
 * @property plage la plage du littéral dans le document ;
 * @property rouge le canal rouge normalisé [0, 1] ;
 * @property vert le canal vert normalisé [0, 1] ;
 * @property bleu le canal bleu normalisé [0, 1] ;
 * @property alpha le canal alpha normalisé [0, 1].
 */
public class CouleurDocumentKotlin(
    public val plage: PlageTexte,
    public val rouge: Double,
    public val vert: Double,
    public val bleu: Double,
    public val alpha: Double,
)

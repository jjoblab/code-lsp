// CodeIDE — SPI du moteur Kotlin LSP : correctifs de texte (module :kotlin,
// étage 2 vague 2f — fiches 016/020 du cahier : accès non sûr, conversions,
// membres manquants P1-C ADR 0229). Code NEUF jjoblab (clean-room —
// frontière D6 : l'hôte traduit en TextEdit/CodeAction LSP).
package jo.codeide.lsp.kotlin.api

import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Un CORRECTIF DE TEXTE (une action d'édition) : le TITRE présenté à
 * l'usager, la PLAGE remplacée et le NOUVEAU TEXTE — l'hôte l'applique
 * comme un `TextEdit` LSP (plage en offsets du document).
 *
 * Les correctifs sont ALIGNÉS SUR LES DIAGNOSTICS COURANTS par
 * construction (le moteur re-prouve le document à la requête — un
 * diagnostic périmé ne produit RIEN, fiche 016).
 *
 * @property titre le titre lisible (`« Accès sûr (?.) »`) ;
 * @property plage la plage remplacée (offsets, [debut] inclus [fin] exclu ;
 * une plage vide est une INSERTION à [debut]) ;
 * @property texte le texte de remplacement.
 */
public class CorrectifTexteKotlin(
    public val titre: String,
    public val plage: PlageTexte,
    public val texte: String,
)

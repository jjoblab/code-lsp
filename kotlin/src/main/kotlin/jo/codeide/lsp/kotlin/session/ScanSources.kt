// CodeIDE — le snapshot immuable du scan des racines sources (L1, module
// :kotlin, étage 1). Code NEUF jjoblab (clean-room — contrat : fiches 049/041
// du cahier). Étage 1 vague 1c : le scan minimal SYNCHRONE de la vague 1b est
// REMPLACÉ par le scanneur paresseux borné annulable ([ScanProjetParesseux],
// fiche 041) — ce type demeure l'UNITÉ DE PUBLICATION (snapshot immuable, hors
// verrou de session : « moins riche, jamais faux »).
// Étage 2 vague 2a : le snapshot porte AUSSI les CONTRIBUTIONS du modèle de
// symboles du projet (fichiers Kotlin ET Java — fiche 022) et l'ÉTAT de la
// passe (complète ou partielle — l'univers des preuves, fiche 006/036).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.resolve.ContributionFichier

/**
 * Le résultat IMMUABLE d'un scan des racines sources (L1) : les déclarations
 * nommées des fichiers Kotlin des racines, indexées par nom — la matière du
 * survol externe — et les contributions au modèle de symboles du projet
 * (Kotlin ET Java), la matière de la résolution de l'étage 2.
 *
 * Le scan est conservateur : seuls les fichiers `.kt`/`.kts` LISIBLES
 * contributeurs sont retenus pour le survol (un fichier illisible est
 * ignoré, jamais une exception — D3) ; les fichiers `.java` ne contribuent
 * QU'AU MODÈLE (la matière du survol reste Kotlin) ; pour chaque nom, la
 * PREMIÈRE déclaration en ordre de marche gagne (racines dans l'ordre posé,
 * fichiers triés par chemin — les surcharges relèvent de la résolution,
 * étage 2).
 *
 * @property parNom les déclarations externes indexées par nom (ordre de
 * marche — déterministe) ;
 * @property contributions les contributions au modèle de symboles (une par
 * fichier Kotlin ou Java contribuant, en ordre de marche) ;
 * @property complet la passe a-t-elle couvert tous les fichiers éligibles
 * VUS (une passe annulée ou tronquée rend `false` — l'univers n'est pas
 * fiable, les preuves d'absence se taisent).
 */
internal class ScanSources internal constructor(
    private val parNom: Map<String, DeclarationExterne>,
    internal val contributions: List<ContributionFichier> = emptyList(),
    internal val complet: Boolean = true,
) {

    /**
     * Une déclaration externe rendue par le scan.
     *
     * @property nom le nom de la déclaration (clé du scan — la première
     * déclaration du nom en ordre de marche gagne) ;
     * @property uri l'identifiant (chemin) du fichier la portant ;
     * @property signature la signature écrite conservatrice ;
     * @property documentation la documentation KDoc qui la précède (`null`
     * si aucune).
     */
    internal class DeclarationExterne(
        internal val nom: String,
        internal val uri: String,
        internal val signature: String,
        internal val documentation: String?,
    )

    /**
     * Rend la déclaration externe portant ce nom.
     *
     * @param nom le nom cherché ;
     * @return la première déclaration du scan portant ce nom ; `null` si le
     * scan n'en connaît aucune.
     */
    internal fun declaration(nom: String): DeclarationExterne? {
        return parNom[nom]
    }

    /**
     * Rend les déclarations du snapshot, l'URI du document courant exclue.
     *
     * @param uriExclue l'URI à exclure (le document courant vit son arbre
     * frais côté session : son doublon du scan serait périmé) ; `null` pour
     * tout rendre ;
     * @return les déclarations en ordre de marche (déterministe) — jamais
     * `null`.
     */
    internal fun declarations(uriExclue: String? = null): List<DeclarationExterne> {
        return if (uriExclue == null) {
            parNom.values.toList()
        } else {
            parNom.values.filter { it.uri != uriExclue }
        }
    }

    /**
     * Rend les contributions au modèle de symboles, l'URI du document
     * courant exclue (son arbre frais PRIME sur son doublon périmé du scan
     * — fiche 022 : le modèle du document est assemblé à part).
     *
     * @param uriExclue l'URI à exclure ; `null` pour tout rendre ;
     * @return les contributions en ordre de marche — jamais `null`.
     */
    internal fun contributionsHors(uriExclue: String?): List<ContributionFichier> {
        return if (uriExclue == null) contributions else contributions.filter { it.uri != uriExclue }
    }
}

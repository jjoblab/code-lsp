// CodeIDE — complétion d'IMPORT au point (module :kotlin, étage 3 vague 3a
// — ADR 0113 ; fiche 045 : « complétion import : sous-paquets du paquet
// tapé » — les sous-paquets DOMINENT les types directs du paquet, miroir
// de la fiche 008 Java). Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.resolve.VueJvmMembres

/**
 * Le régime d'IMPORT (étage 3 — fiche 045) : le caret vit dans une
 * directive d'import, un chemin en cours de frappe (`import android.wid|`).
 *
 * Le paquet TAPÉ se coupe au dernier point : le PARENT (`android`) ouvre
 * la consultation, le PRÉFIXE (`wid`) filtre. Les sources, dans l'ordre
 * de dominance :
 *
 *  1. les SOUS-PAQUETS DIRECTS du parent (nature PAQUET, le FQCN en
 *     détail — `widget` détail `android.widget`) : dominants, la
 *     navigation par paquet précède le choix de type ;
 *  2. les TYPES DIRECTS du parent (nature CLASSE, FQCN en détail et en
 *     import éditable — l'import se termine ici).
 *
 * Un parent INCONNU des entrées rend une liste VIDE honnête (D3 — jamais
 * une invention de paquet) ; le préfixe VIDE propose tout le niveau (la
 * liste du niveau est finie).
 */
internal object CompletionImports {

    /**
     * Collecte les propositions du régime d'IMPORT.
     *
     * @param cheminTape le chemin tapé à l'import, coupé au caret
     * (`android.wid` — vide si rien n'est tapé) ;
     * @param vueJvm la vue JVM des entrées (`null` = pas d'annuaire :
     * liste vide honnête) ;
     * @return les propositions (sous-paquets puis types du paquet).
     */
    internal fun collecter(cheminTape: String, vueJvm: VueJvmMembres?): List<PropositionKotlin> {
        if (vueJvm == null) return emptyList()
        // Le parent et le préfixe : sans point, TOUT le chemin tapé est le
        // préfixe du niveau racine (parent vide).
        val parent = if (cheminTape.contains('.')) cheminTape.substringBeforeLast('.') else ""
        val prefixe = cheminTape.substringAfterLast('.')

        val propositions = LinkedHashMap<String, PropositionKotlin>()

        // 1. Les SOUS-PAQUETS DIRECTS du parent — dominants (fiche 008
        // Java : « dominants sur les seuls types directs »).
        for (sousPaquet in vueJvm.sousPaquetsDirects(parent)) {
            val nomSimple = sousPaquet.substringAfterLast('.')
            if (prefixe.isNotEmpty() && !nomSimple.startsWith(prefixe)) continue
            propositions[nomSimple] = PropositionKotlin(
                libelle = nomSimple,
                nature = NaturePropositionKotlin.PAQUET,
                detail = sousPaquet,
            )
        }

        // 2. Les TYPES DIRECTS du parent (l'import se termine ici : FQCN
        // en détail ET en import éditable).
        for (fqcn in vueJvm.typesDansPaquet(parent)) {
            val nomSimple = fqcn.substringAfterLast('.')
            if (prefixe.isNotEmpty() && !nomSimple.startsWith(prefixe)) continue
            if (nomSimple in propositions) continue
            propositions[nomSimple] = PropositionKotlin(
                libelle = nomSimple,
                nature = NaturePropositionKotlin.CLASSE,
                detail = fqcn,
                fqcnImport = fqcn,
            )
        }
        return propositions.values.toList()
    }
}

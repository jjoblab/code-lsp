// CodeIDE — complétion MEMBRE (module :kotlin, étage 2 vague 2a — le GERME
// requis par la fiche 022 du cahier ; vague 2b — fiche 046 : les récepteurs
// de BIBLIOTHÈQUE (vue JVM), les SMART CASTS positionnels et les ATTENTES
// de lambda type le récepteur ; étage 3 vague 3a — ADR 0113 : le régime
// MEMBRE du pipeline, propositions DÉTAILLÉES — natures et types de
// présentation). Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.Couches
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.PileThis
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le régime MEMBRE du pipeline (étage 3 — ADR 0113) : les membres d'un
 * récepteur TYPÉ après le point (fiche 022 — `copy` proposé après `p.` ;
 * fiche 046 — récepteurs de bibliothèque par la vue JVM, smart cast
 * positionnel, attente de lambda ; fiches 044/040 — les membres HÉRITÉS de
 * la clôture, `this.` dans une Activity propose `layoutInflater` ; fiche
 * 045 — champ et méthode le long de la chaîne de supertypes, avec nature
 * et type de présentation).
 *
 * Le régime est CONSERVATEUR : un point dont le récepteur n'est pas typable
 * (classpath illisible, littéral hors famille, non typable) rend une liste
 * VIDE honnête — jamais une invention (D3). La liste n'est PAS filtrée par
 * préfixe : le re-filtrage local par frappe est la frontière RPC de l'hôte
 * (ADR 0113/0118 — le serveur envoie la liste et son classement).
 */
internal object CompletionMembres {

    /**
     * Collecte les propositions du régime MEMBRE au point.
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison de la région porteuse ;
     * @param deplacement l'offset du caret (juste après le point) ;
     * @return les propositions détaillées du récepteur typé ; liste vide
     * sans récepteur typable — jamais d'exception.
     */
    internal fun collecter(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        deplacement: Int,
    ): List<PropositionKotlin> {
        // Le récepteur : la PLUS PROFONDE expression qualifiée couvrant le
        // point (le caret vit juste après le point du sélecteur).
        val qualifiee = recepteurQualifie(arbre.noeud, deplacement) ?: return emptyList()
        val recepteur = qualifiee.enfants.firstOrNull() ?: return emptyList()

        // La PILE DES THIS au caret (vague 2c — fiches 028/040 : `this.`
        // type la couche profonde, les membres implicites hérités
        // `layoutInflater` type le récepteur, les lambdas d'application
        // portent le leur).
        val pile = Couches.aPosition(arbre, univers, liaison, deplacement)
        val typeRecepteur = TypageBorne.typeDeLexpression(recepteur, univers, liaison, pile)
            ?: return emptyList()
        val propositions = univers.propositionsMembresDetaillees(typeRecepteur).map {
            PropositionKotlin(libelle = it.nom, nature = it.nature, detail = it.detail)
        }.toMutableList()

        // CHANTIER C (parité Android Studio — complétion postfixe) : les
        // POSTFIXES usuels se proposent avec leur SNIPPET de
        // transformation (`x.le|` → item `let` insérant `let { $0 }`) —
        // le point est déjà tapé, l'item complète le sélecteur. Les
        // mots-clés de contrôle (`if`, `for`) ne sont pas des membres :
        // ils se proposent UNIQUEMENT en postfixe ici.
        val prefixe = prefixeAuPoint(arbre.noeud, deplacement)
        for ((nom, snippet, detail) in POSTFIXES) {
            if (prefixe.isNotEmpty() && !nom.startsWith(prefixe)) continue
            if (propositions.any { it.libelle == nom }) continue
            propositions.add(
                PropositionKotlin(
                    libelle = nom,
                    nature = NaturePropositionKotlin.FONCTION,
                    detail = detail,
                    texteInsertion = snippet,
                ),
            )
        }
        // CHANTIER I — les postfixes DE CONTRÔLE : le snippet réécrit
        // toute l'expression (`valeur.if|` → `if (valeur) { $0 }`), la
        // plage de remplacement couvre l'expression qualifiée ENTIÈRE
        // (le récepteur + le point + le préfixe tapé — l'hôte rend un
        // TextEdit LSP unique). Le texte du récepteur est repris TEL
        // QUEL dans le snippet.
        val texteRecepteur = arbre.source.substring(qualifiee.debut, recepteur.fin)
        for ((nom, snippet, detail) in POSTFIXES_CONTROLE) {
            if (prefixe.isNotEmpty() && !nom.startsWith(prefixe)) continue
            if (propositions.any { it.libelle == nom }) continue
            propositions.add(
                PropositionKotlin(
                    libelle = nom,
                    nature = NaturePropositionKotlin.MOT_CLE,
                    detail = detail,
                    texteInsertion = snippet.replace("recepteur", texteRecepteur),
                    plageRemplacement = PlageTexte(debut = qualifiee.debut, fin = deplacement),
                ),
            )
        }
        return propositions
    }

    /** Le préfixe FRAPPÉ au caret (l'identifiant en cours, tronqué au caret). */
    private fun prefixeAuPoint(racine: NoeudArbre, deplacement: Int): String {
        var trouve: String = ""
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.REFERENCE_EXPRESSION) {
                val coupe = (deplacement - noeud.debut).coerceIn(0, noeud.texteCanonique.length)
                if (coupe > trouve.length) trouve = noeud.texteCanonique.take(coupe)
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /**
     * Les POSTFIXES usuels (chantier C — miroir des postfix templates
     * Kotlin de l'IDE, périmètre borné aux quatre familles quotidiennes)
     * : label, snippet inséré (position LSP — `$0` le caret final), détail.
     */
    private val POSTFIXES: List<Triple<String, String, String>> = listOf(
        Triple("let", "let { ${'$'}0 }", "postfix — scope let { it }"),
        Triple("also", "also { ${'$'}0 }", "postfix — scope also { it }"),
        Triple("apply", "apply { ${'$'}0 }", "postfix — scope apply { this }"),
        Triple("run", "run { ${'$'}0 }", "postfix — scope run { this }"),
    )

    /**
     * Les POSTFIXES DE CONTRÔLE (chantier I — miroir des postfix
     * templates `if`/`for`/`while` de l'IDE) : ils RÉÉCRIVENT toute
     * l'expression réceptrice (`valeur.if|` → `if (valeur) { }`) — le
     * snippet est le CORPS complet, la plage de remplacement couvre
     * l'expression qualifiée ENTIÈRE (du début du récepteur au caret).
     */
    private val POSTFIXES_CONTROLE: List<Triple<String, String, String>> = listOf(
        Triple("if", "if (recepteur) { ${'$'}0 }", "postfix — if (recepteur) { }"),
        Triple("for", "for (element in recepteur) { ${'$'}0 }", "postfix — for (element in recepteur) { }"),
        Triple("while", "while (recepteur) { ${'$'}0 }", "postfix — while (recepteur) { }"),
    )

    /**
     * La plus profonde expression qualifiée (point ou accès sûr) dont la
     * plage couvre le point AVANT le caret (le caret vit juste après le
     * point du sélecteur).
     *
     * @param racine la racine de l'arbre du document ;
     * @param deplacement l'offset du caret ;
     * @return le nœud qualifié portant le récepteur ; `null` si le point
     * n'est pas dans un sélecteur.
     */
    private fun recepteurQualifie(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement - 1 < noeud.debut || deplacement - 1 >= noeud.fin) return
            if (noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION ||
                noeud.genre == KtNodeTypes.SAFE_ACCESS_EXPRESSION
            ) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }
}

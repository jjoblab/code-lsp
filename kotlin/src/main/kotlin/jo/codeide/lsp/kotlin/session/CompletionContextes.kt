// CodeIDE — classification du CONTEXTE de complétion (module :kotlin,
// étage 3 vague 3a — ADR 0113 : le pipeline classe le caret AVANT la
// collecte ; les contextes contextuels v1 — arguments nommés, constantes
// d'enum, attributs d'annotation — arrivent en vague 3b). Code NEUF
// jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le contexte de complétion AU POINT : le genre classifié et les données
 * du régime (le préfixe frappé, le chemin d'import en cours).
 *
 * @property genre le genre du contexte (le régime de collecte) ;
 * @property prefixe le préfixe FRAPPÉ au caret (l'identifiant en cours,
 * tronqué au caret — vide sans identifiant) ;
 * @property cheminImport le chemin TAPÉ d'une directive d'import en
 * édition (`android.wid` — genre [GenreContexteKotlin.IMPORT] seul).
 */
internal class ContexteCompletion internal constructor(
    internal val genre: GenreContexteKotlin,
    internal val prefixe: String,
    internal val cheminImport: String? = null,
)

/**
 * Le classificateur du caret (étage 3 — ADR 0113) : le genre de contexte
 * se décide sur l'ARBRE SEUL, AVANT toute collecte — chaque genre ouvre
 * son régime et ses bornes conservatrices (un contexte non prouvable
 * retombe sur le régime de NOM, jamais une invention).
 *
 * L'ordre de décision :
 *  1. **IMPORT** — le caret vit dans une directive d'import (le chemin
 *     tapé se coupe au caret) ;
 *  2. **MEMBRE** — le caret vit juste après le point d'un sélecteur
 *     qualifié (le récepteur se typera à la collecte — un récepteur non
 *     typable rend une liste VIDE, D3) ;
 *  3. **NOM** — le reste des positions d'identifiant (le régime de
 *     repli honnête : locales, membres implicites, types, mots-clés).
 *
 * Les genres contextuels v1 (arguments nommés, constantes d'enum des
 * branches d'un `when`, attributs d'annotation) sont classifiés en vague
 * 3b — ils s'insèrent entre MEMBRE et NOM.
 */
internal object CompletionContextes {

    /** La longueur du mot-clé `import` (le préfixe du chemin tapé). */
    private const val LONGUEUR_IMPORT: Int = "import".length

    /**
     * Classe le caret du document.
     *
     * @param arbre l'arbre du document ;
     * @param deplacement l'offset du caret ;
     * @return le contexte classifié (jamais `null` — le repli est le
     * régime de NOM à préfixe vide).
     */
    internal fun auPoint(arbre: KtFichier, deplacement: Int): ContexteCompletion {
        // 1. IMPORT : le caret vit dans une directive d'import.
        for (directive in arbre.imports()) {
            if (deplacement < directive.plage.debut || deplacement > directive.plage.fin) continue
            val chemin = cheminImportTape(arbre, directive.plage.debut, deplacement)
            // Sans point, TOUT le chemin tapé est le préfixe du niveau
            // racine (parent vide) — `import andr|` filtre le niveau 1.
            val prefixe = chemin.substringAfterLast('.')
            return ContexteCompletion(
                genre = GenreContexteKotlin.IMPORT,
                prefixe = prefixe,
                cheminImport = chemin,
            )
        }

        // 2. MEMBRE : le caret vit après le point du SÉLECTEUR d'une
        // expression qualifiée (la plus profonde couvrant le caret MOINS
        // un — le caret vit juste après le point).
        val prefixe = prefixeAuPoint(arbre.noeud, deplacement)
        if (estPositionMembre(arbre.noeud, deplacement)) {
            return ContexteCompletion(genre = GenreContexteKotlin.MEMBRE, prefixe = prefixe)
        }

        // 3. NOM : le repli honnête.
        return ContexteCompletion(genre = GenreContexteKotlin.NOM, prefixe = prefixe)
    }

    /**
     * Le chemin TAPÉ d'une directive d'import en édition : le texte brut
     * du début de la directive au caret, moins le mot-clé `import`, tronqué
     * au caret — la forme honnête d'une frappe en cours (`android.wi`).
     *
     * @param arbre l'arbre porteur (la source) ;
     * @param debut le début de la directive ;
     * @param deplacement l'offset du caret ;
     * @return le chemin tapé (`android.wid`) ; vide si rien n'est tapé.
     */
    private fun cheminImportTape(arbre: KtFichier, debut: Int, deplacement: Int): String {
        val fin = deplacement.coerceAtMost(arbre.source.length).coerceAtLeast(debut)
        if (fin <= debut) return ""
        val brut = arbre.source.subSequence(debut, fin).toString()
        val sansMotCle = brut.removePrefix("import")
        return sansMotCle.trim().substringBeforeLast(' ').trim(' ', '*')
    }

    /**
     * Le caret est-il en position de MEMBRE : la plus profonde expression
     * qualifiée (point ou accès sûr) couvrant le caret MOINS UN existe et
     * porte un récepteur — la règle du GERME 2a à l'identique (fiche 047 :
     * le caret posé juste après le `R` de `R.layout` complète le
     * récepteur `R` — le classement fin des positions relève de la
     * chaîne, pas du classificateur).
     *
     * @param racine la racine de l'arbre ;
     * @param deplacement l'offset du caret ;
     * @return `true` en position de sélecteur.
     */
    private fun estPositionMembre(racine: NoeudArbre, deplacement: Int): Boolean {
        return recepteurQualifie(racine, deplacement) != null
    }

    /**
     * La plus profonde expression qualifiée (point ou accès sûr) dont la
     * plage couvre le point AVANT le caret (le caret vit juste après le
     * point du sélecteur).
     *
     * @param racine la racine de l'arbre ;
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

    /**
     * Le PRÉFIXE frappé au point : l'identifiant sous le caret (fin
     * comprise — le caret vit juste après les caractères frappés), tronqué
     * à la position du caret ; vide si le caret ne désigne pas un
     * identifiant en cours de frappe.
     */
    internal fun prefixeAuPoint(racine: NoeudArbre, deplacement: Int): String {
        var trouve: String? = null
        fun marcher(noeud: NoeudArbre) {
            for (jeton in noeud.jetonsPropres) {
                if (jeton.genre == KtTokens.IDENTIFIER &&
                    jeton.debut <= deplacement && deplacement <= jeton.fin
                ) {
                    val coupe = (deplacement - jeton.debut).coerceIn(0, jeton.texte.length)
                    trouve = jeton.texte.substring(0, coupe)
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve.orEmpty()
    }
}

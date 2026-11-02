// CodeIDE — plages pliables d'un document Kotlin (module :kotlin, zone
// plis — REPRISE VOIE C, manifeste docs/PROVENANCE-REPRISE-KOTLIN.md
// §3 R7). Origine : codeide-lsp
// lsp/kotlin/src/main/kotlin/jo/lspkotlin/plis/PlisDocumentKotlin.kt
// @ bc11253 (auteur jjoblab, dépôt propriétaire) — contrôle R9.4 PASSÉ
// (en-têtes sans mention tierce ; re-contrôle au moment de la copie,
// vague 5a). Adaptations de câblage SEULEMENT, listées ici : paquet
// jo.codeide.lsp.kotlin.plis ; modèle neuf `parse.NoeudArbre` (les
// commentaires du document vivent à la RACINE de l'arbre neuf — la
// marche les y lit au lieu de rencontrer des jetons enfants) ; le genre
// neutre `api.GenrePlagePliable` porté par chaque plage (le SPI neuf
// distingue IMPORTS/KDOC/CLASSE/FONCTION/BLOC — un BLOC de fonction
// rend FONCTION, les autres corps à accolades rendent BLOC) ; le rendu
// final trié par début de plage (l'ordre du document, miroir du calcul
// neuf vague 1b qu'il remplace). Le cœur (règle d'accolades,
// déduplication, pliage des imports et des commentaires) est repris
// TEL QUEL.
package jo.codeide.lsp.kotlin.plis

import jo.codeide.lsp.kotlin.api.GenrePlagePliable
import jo.codeide.lsp.kotlin.api.PlagePliableKotlin
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Génération de la liste `textDocument/foldingRange` pour Kotlin (S2 —
 * plan de stabilisation LSP §3.2) : les régions REPLIABLES du document.
 *
 * Règle unique pour les productions : un nœud PLIABLE est plié de sa
 * PREMIÈRE accolade ouvrante à sa DERNIÈRE accolade fermante (jamais
 * du début du nœud — un pli qui masquerait l'en-tête `fun f()` ou
 * `when (x)` serait du bruit). Types pliés :
 * - [KtNodeTypes.CLASS_BODY] — corps de classe/objet/compagnon/enum.
 * - [KtNodeTypes.BLOCK] — bloc d'instructions (corps de fonction,
 *   corps d'initialiseur, branche de contrôle).
 * - [KtNodeTypes.BODY] — corps d'une structure de contrôle (if/else/
 *   for/while/try). Le nœud imbriqué BLOCK couvre les MÊMES accolades :
 *   la déduplication par plage fait l'un des deux (le premier émis).
 * - [KtNodeTypes.LAMBDA_EXPRESSION] — lambda (`FUNCTION_LITERAL`
 *   couvre les mêmes accolades, dédupliqué).
 * - [KtNodeTypes.WHEN] — le bloc d'un `when` (accolades seules).
 * - [KtNodeTypes.IMPORT_LIST] — le bloc d'imports ENTIER (du premier
 *   `import` au dernier, patron des serveurs tiers de référence).
 * Règle pour les jetons : un commentaire multi-ligne ([KtTokens.DOC_COMMENT]
 * KDoc ou [KtTokens.BLOCK_COMMENT]) est pliable — les EOL isolées et
 * les commentaires mono-ligne ne le sont PAS (l'hôte filtre les plis
 * mono-ligne, le moteur ne connaît pas les lignes).
 *
 * Tolérance (D3) : un document vide/malformé rend une liste VIDE
 * (jamais d'exception) — un nœud sans PAIRE d'accolades (édition en
 * cours) ne produit RIEN ; les nœuds d'erreur ne sont pas exclus (le
 * pliage suit la structure écrite, pas la validité).
 */
internal object PlisDocumentKotlin {

    /**
     * Plages pliables du document, en ORDRE DU DOCUMENT (tri par début
     * de plage — le conteneur précède ses membres), DÉDUPLIQUÉES (BODY
     * et son BLOCK imbriqué portent les mêmes accolades : une seule
     * plage, du premier genre émis).
     *
     * @param racine la racine de l'arbre du document (les jetons de
     * commentaire du document y vivent — modèle neuf) ;
     * @return les plages pliables avec leur genre neutre.
     */
    internal fun depuis(racine: NoeudArbre): List<PlagePliableKotlin> {
        val vues = LinkedHashMap<Pair<Int, Int>, GenrePlagePliable>()
        collecter(racine, genreParent = null, vues)
        for (jeton in racine.commentaires) commentaire(jeton, vues)
        return vues.entries
            .sortedBy { it.key.first }
            .map { (plage, genre) -> PlagePliableKotlin(PlageTexte(plage.first, plage.second), genre) }
    }

    /** Marche récursive : le nœud, puis ses enfants. */
    private fun collecter(
        noeud: NoeudArbre,
        genreParent: com.intellij.platform.syntax.SyntaxElementType?,
        vues: LinkedHashMap<Pair<Int, Int>, GenrePlagePliable>,
    ) {
        quand(noeud, genreParent, vues)
        for (enfant in noeud.enfants) {
            collecter(enfant, genreParent = noeud.genre, vues)
        }
    }

    /**
     * Émet la plage d'un nœud pliable (accolades ou imports) avec son
     * genre neutre, sinon rien.
     */
    private fun quand(
        noeud: NoeudArbre,
        genreParent: com.intellij.platform.syntax.SyntaxElementType?,
        vues: LinkedHashMap<Pair<Int, Int>, GenrePlagePliable>,
    ) {
        when (noeud.genre) {
            KtNodeTypes.IMPORT_LIST -> plageImports(noeud)?.let { vues.putIfAbsent(it, GenrePlagePliable.IMPORTS) }

            KtNodeTypes.CLASS_BODY ->
                plageAccolades(noeud)?.let { vues.putIfAbsent(it, GenrePlagePliable.CLASSE) }

            KtNodeTypes.BLOCK ->
                plageAccolades(noeud)?.let {
                    vues.putIfAbsent(
                        it,
                        if (genreParent == KtNodeTypes.FUN) GenrePlagePliable.FONCTION else GenrePlagePliable.BLOC,
                    )
                }

            KtNodeTypes.BODY,
            KtNodeTypes.LAMBDA_EXPRESSION,
            KtNodeTypes.WHEN,
            -> plageAccolades(noeud)?.let { vues.putIfAbsent(it, GenrePlagePliable.BLOC) }

            else -> {
                // Production non pliable (expression, déclaration,
                // liste de paramètres…) : rien à replier.
            }
        }
    }

    /** Émet la plage d'un commentaire multi-ligne (KDoc / bloc). */
    private fun commentaire(
        jeton: JetonBrut,
        vues: LinkedHashMap<Pair<Int, Int>, GenrePlagePliable>,
    ) {
        when (jeton.genre) {
            KtTokens.DOC_COMMENT -> vues.putIfAbsent(Pair(jeton.debut, jeton.fin), GenrePlagePliable.KDOC)

            KtTokens.BLOCK_COMMENT -> vues.putIfAbsent(Pair(jeton.debut, jeton.fin), GenrePlagePliable.BLOC)

            else -> {
                // EOL/SHEBANG : jamais pliables (une ligne).
            }
        }
    }

    /**
     * Plage d'un bloc d'imports : du DÉBUT de la première directive à
     * la FIN de la dernière (l'IMPORT_LIST peut être vide ou en
     * édition : sans directive, rien).
     */
    private fun plageImports(noeud: NoeudArbre): Pair<Int, Int>? {
        val directives = noeud.enfants.filter { it.genre == KtNodeTypes.IMPORT_DIRECTIVE }
        val premiere = directives.firstOrNull() ?: return null
        val derniere = directives.last()
        return Pair(premiere.debut, derniere.fin)
    }

    /**
     * Plage d'un bloc à accolades : première accolade ouvrante du
     * sous-arbre → dernière fermante. Sans PAIRE (édition en cours,
     * nœud dégénéré) : rien — un pli doit replier QUELQUE CHOSE.
     */
    private fun plageAccolades(noeud: NoeudArbre): Pair<Int, Int>? {
        val jetons = jetonsDescendants(noeud)
        val ouvrante = jetons.firstOrNull { it.genre == KtTokens.LBRACE }
        val fermante = jetons.lastOrNull { it.genre == KtTokens.RBRACE }
        val plage =
            if (ouvrante != null && fermante != null && fermante.fin > ouvrante.fin) {
                Pair(ouvrante.debut, fermante.fin)
            } else {
                null
            }
        return plage
    }

    /**
     * Les jetons SIGNIFICATIFS du sous-arbre (blancs et commentaires
     * exclus — modèle neuf : chaque nœud ne porte que ses jetons
     * propres), EN ORDRE DU DOCUMENT (les jetons propres du nœud
     * vivent AUTOUR de ceux de ses enfants — l'ouvrante avant, la
     * fermante après : la fusion se trie par position, miroir de la
     * suite contiguë de l'ancien modèle).
     */
    private fun jetonsDescendants(noeud: NoeudArbre): List<JetonBrut> {
        val jetons = mutableListOf<JetonBrut>()
        jetons.addAll(noeud.jetonsPropres)
        for (enfant in noeud.enfants) {
            jetons.addAll(jetonsDescendants(enfant))
        }
        return jetons.sortedBy { it.debut }
    }
}

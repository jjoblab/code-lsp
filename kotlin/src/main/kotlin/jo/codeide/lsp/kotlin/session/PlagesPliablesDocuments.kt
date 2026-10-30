// CodeIDE — plages pliables d'un document Kotlin (module :kotlin, étage 1
// vague 1b). Code NEUF jjoblab (clean-room — contrat : fiche 049 du cahier :
// « plages pliables : classe, fonctions, imports et KDoc » ; la spécification
// complète du pliage est la fiche 014, étage 5 — la présente vague couvre le
// périmètre du survol session, conservateur).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.GenrePlagePliable
import jo.codeide.lsp.kotlin.api.PlagePliableKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Calcul des plages pliables d'un document ouvert (pur — aucun état, appelé
 * sous verrou de lecture).
 *
 * Le périmètre conservateur de la vague 1b (exactement la fiche 049) : le
 * BLOC des imports (une plage pour la liste entière lorsqu'elle porte au
 * moins une directive), chaque BLOC DE DOCUMENTATION KDoc (jeton `KDoc` du
 * lexeur), le CORPS de chaque déclaration de type (classes, interfaces,
 * enums, objets — production `CLASS_BODY`) et le CORPS DE BLOC de chaque
 * fonction (production `BLOCK` fille d'un `FUN`). Les autres formes
 * (initialiseurs de propriété, branches `when`…) relèvent de la fiche 014 —
 * sous-rapport honnête (D3).
 */
internal object PlagesPliablesDocuments {

    /**
     * Calcule les plages pliables du document.
     *
     * @param document le document ouvert (texte + arbre + commentaires) ;
     * @return les plages en ordre du document ; liste vide si le document
     * n'a rien de pliable (jamais d'exception).
     */
    internal fun calculer(document: DocumentOuvert): List<PlagePliableKotlin> {
        val plages = mutableListOf<PlagePliableKotlin>()

        fun walk(n: NoeudArbre, parentEstFonction: Boolean) {
            if (n.genre == KtNodeTypes.IMPORT_LIST &&
                n.enfants.any { it.genre == KtNodeTypes.IMPORT_DIRECTIVE }
            ) {
                plages.add(PlagePliableKotlin(PlageTexte(n.debut, n.fin), GenrePlagePliable.IMPORTS))
            }
            if (n.genre == KtNodeTypes.CLASS_BODY) {
                plages.add(PlagePliableKotlin(PlageTexte(n.debut, n.fin), GenrePlagePliable.CLASSE))
            }
            if (n.genre == KtNodeTypes.BLOCK && parentEstFonction) {
                plages.add(PlagePliableKotlin(PlageTexte(n.debut, n.fin), GenrePlagePliable.FONCTION))
            }
            val estFonction = n.genre == KtNodeTypes.FUN
            n.enfants.forEach { walk(it, parentEstFonction = estFonction) }
        }
        walk(document.arbre.noeud, parentEstFonction = false)

        // Blocs de documentation : les jetons de commentaire KDoc du document
        // (trivia portés par la racine — forme « /** … */ »).
        for (commentaire in document.arbre.noeud.commentaires) {
            if (commentaire.texte.startsWith("/**") && commentaire.texte.length > 4) {
                plages.add(
                    PlagePliableKotlin(
                        PlageTexte(commentaire.debut, commentaire.fin),
                        GenrePlagePliable.KDOC,
                    ),
                )
            }
        }

        plages.sortBy { it.plage.debut }
        return plages
    }
}

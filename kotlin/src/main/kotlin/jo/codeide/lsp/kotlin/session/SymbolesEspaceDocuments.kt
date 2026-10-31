// CodeIDE — symboles d'espace d'une session Kotlin (module :kotlin, étage 6
// vague 6b). Code NEUF jjoblab (contrat : fiche 015 du cahier
// docs/cahier-specifications-kotlin/ — la palette `workspace/symbol` : les
// déclarations de PREMIER NIVEAU des documents OUVERTS et du SCAN, requête
// CONTIENT insensible à la casse, ordre par nom ; les LOCALES ne remontent
// JAMAIS).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.GenreSymboleKotlin
import jo.codeide.lsp.kotlin.api.SymboleEspaceKotlin
import jo.codeide.lsp.kotlin.parse.kt.KtClasse
import jo.codeide.lsp.kotlin.parse.kt.KtDeclaration
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.KtFonction
import jo.codeide.lsp.kotlin.parse.kt.KtPropriete
import jo.codeide.lsp.kotlin.parse.kt.KtTypealias
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.ContributionFichier
import jo.codeide.lsp.kotlin.resolve.GenreTypeProjet
import org.jetbrains.kotlin.kmp.lexer.KtTokens

/**
 * Calcul des SYMBOLES D'ESPACE (pur — consulte les documents ouverts et le
 * snapshot du scan, appelé hors verrou d'écriture).
 *
 * ## Le périmètre de la palette (fiche 015)
 *
 * Les déclarations de PREMIER NIVEAU : celles des documents OUVERTS (leur
 * arbre VIVANT — jamais leur doublon périmé du disque) et celles du SCAN
 * des racines (fichiers fermés). Les LOCALES (valeurs de corps, paramètres)
 * ne remontent JAMAIS — la palette parcourt le premier niveau seulement.
 *
 * Une requête VIDE rend TOUT ; une requête sans correspondance rend une
 * liste VIDE (honnête D3).
 */
internal object SymbolesEspaceDocuments {

    /**
     * Rend les symboles d'espace de la requête.
     *
     * @param documents les documents ouverts (URI → arbre VIVANT, en ordre
     * d'ouverture) ;
     * @param scan les contributions du snapshot du scan publié (fichiers
     * fermés — leurs URI d'ouvertes sont déjà exclues par l'appelant) ;
     * @param requete le texte cherché (CONTIENT, insensible à la casse ;
     * vide = tout) ;
     * @return les symboles triés par nom (puis URI — ordre stable) ; liste
     * VIDE sans correspondance.
     */
    internal fun symboles(
        documents: List<Pair<String, KtFichier>>,
        scan: List<ContributionFichier>,
        requete: String,
    ): List<SymboleEspaceKotlin> {
        val cherche = requete.lowercase()
        val resultat = mutableListOf<SymboleEspaceKotlin>()
        for ((uri, arbre) in documents) {
            for (declaration in arbre.declarations()) {
                val nom = declaration.nom() ?: continue
                if (cherche.isNotEmpty() && !nom.lowercase().contains(cherche)) continue
                val jeton = declaration.noeud.jetonsPropres
                    .firstOrNull { it.genre == KtTokens.IDENTIFIER } ?: continue
                resultat.add(
                    SymboleEspaceKotlin(
                        nom = nom,
                        genre = genreDe(declaration),
                        uri = uri,
                        plageNom = PlageTexte(jeton.debut, jeton.fin),
                    ),
                )
            }
        }
        for (contribution in scan) {
            for (type in contribution.types) {
                if (cherche.isNotEmpty() && !type.nomSimple.lowercase().contains(cherche)) continue
                resultat.add(
                    SymboleEspaceKotlin(
                        nom = type.nomSimple,
                        genre = genreDeType(type.genre),
                        uri = contribution.uri,
                        plageNom = null,
                    ),
                )
            }
            for (appelable in contribution.appelables + contribution.extensions) {
                val nom = appelable.nom
                if (cherche.isNotEmpty() && !nom.lowercase().contains(cherche)) continue
                resultat.add(
                    SymboleEspaceKotlin(
                        nom = nom,
                        genre = if (appelable.estFonction) GenreSymboleKotlin.FONCTION else GenreSymboleKotlin.PROPRIETE,
                        uri = contribution.uri,
                        plageNom = appelable.debutNom.takeIf { it >= 0 }
                            ?.let { PlageTexte(it, it + nom.length) },
                    ),
                )
            }
        }
        return resultat.sortedWith(compareBy({ it.nom }, { it.uri }))
    }

    /** Le genre neutre d'une déclaration typée du document. */
    private fun genreDe(declaration: KtDeclaration): GenreSymboleKotlin = when (declaration) {
        is KtClasse -> when (declaration.genreClasse) {
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.CLASSE -> GenreSymboleKotlin.CLASSE
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.INTERFACE -> GenreSymboleKotlin.INTERFACE
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.ENUM -> GenreSymboleKotlin.ENUM
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.OBJET -> GenreSymboleKotlin.OBJET
        }
        is jo.codeide.lsp.kotlin.parse.kt.KtObjet -> GenreSymboleKotlin.OBJET
        is KtFonction -> GenreSymboleKotlin.FONCTION
        is KtPropriete -> GenreSymboleKotlin.PROPRIETE
        is KtTypealias -> GenreSymboleKotlin.TYPEALIAS
        else -> GenreSymboleKotlin.CLASSE
    }

    /** Le genre neutre d'un type du scan (les classes Java → CLASSE). */
    private fun genreDeType(genre: GenreTypeProjet): GenreSymboleKotlin = when (genre) {
        GenreTypeProjet.CLASSE, GenreTypeProjet.JAVA -> GenreSymboleKotlin.CLASSE
        GenreTypeProjet.INTERFACE -> GenreSymboleKotlin.INTERFACE
        GenreTypeProjet.ENUM -> GenreSymboleKotlin.ENUM
        GenreTypeProjet.OBJET -> GenreSymboleKotlin.OBJET
    }
}

// CodeIDE — navigation d'un document Kotlin (module :kotlin, étage 2 vague
// 2e). Code NEUF jjoblab (clean-room — contrat : fiches 031/025 du cahier :
// go-to-definition de l'usage commonMain vers l'`expect` KMP, symboles de
// document d'un fichier Compose-like, références d'une fonction projet).
// Chaîne conservatrice miroir du survol : document courant → documents
// ouverts → scan des racines (URI de fichier + plage du nom).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.EmplacementKotlin
import jo.codeide.lsp.kotlin.api.GenreSymboleKotlin
import jo.codeide.lsp.kotlin.api.SymboleDocumentKotlin
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.FabriqueKt
import jo.codeide.lsp.kotlin.parse.kt.KtClasse
import jo.codeide.lsp.kotlin.parse.kt.KtDeclaration
import jo.codeide.lsp.kotlin.parse.kt.KtFonction
import jo.codeide.lsp.kotlin.parse.kt.KtPropriete
import jo.codeide.lsp.kotlin.parse.kt.KtTypealias
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * La navigation d'un document ouvert (pur — aucun état, appelé sous verrou
 * de lecture) : DÉFINITION du nom sous le point, SYMBOLES du document et
 * RÉFÉRENCES du nom sous le point.
 *
 * ## Les chaînes conservatrices (miroir du survol, fiche 049)
 *
 * - **Définition** : le caret désigne un IDENTIFIANT ; la déclaration se
 *   cherche dans le document courant (le SITE désigné d'abord, puis la
 *   première déclaration du même nom), puis les autres documents ouverts,
 *   puis le SCAN des racines (l'URI de fichier du scanner + la plage du
 *   nom — fiche 031 : l'usage commonMain va vers l'`expect`).
 * - **Symboles** : les déclarations NOMMÉES du document en ordre du
 *   document, récursivement (premier niveau + membres) — liste PLATE (la
 *   hiérarchie relève du classement de l'étage 3).
 * - **Références** : le site de déclaration (s'il vit dans le document)
 *   puis chaque RÉFÉRENCE du même nom dans le document — périmètre
 *   DOCUMENT, honnête (les usages des sources externes relèvent de
 *   l'étage 3).
 *
 * Introuvable ⇒ liste vide (silence honnête D3 — jamais d'exception).
 */
internal object NavigationDocuments {

    /**
     * Rend les emplacements de DÉFINITION du nom sous le point.
     *
     * @param cible le document ouvert visé ;
     * @param uriCible l'identifiant du document visé ;
     * @param autres les autres documents ouverts (URI + document, ordre
     * d'ouverture) ;
     * @param scan le scan des racines publié (L1) — `null` tant qu'aucun
     * rafraichirSources n'a abouti ;
     * @param deplacement l'offset du caret DANS l'identifiant usage ;
     * @return les emplacements (le premier est le meilleur candidat) ;
     * liste vide sans identifiant ou sans déclaration trouvable.
     */
    internal fun definition(
        cible: DocumentOuvert,
        uriCible: String,
        autres: List<Pair<String, DocumentOuvert>>,
        scan: ScanSources?,
        deplacement: Int,
        externes: List<Pair<String, String>> = emptyList(),
    ): List<EmplacementKotlin> {
        val identifiant = jetonIdentifierA(cible.arbre.noeud, deplacement) ?: return emptyList()
        val nom = identifiant.texte

        // 1. Le document courant : le SITE de déclaration désigné d'abord
        // (le caret SUR le nom d'une déclaration), puis la première
        // déclaration du même nom.
        val declarations = SurvolDocuments.declarationsRecursives(cible.arbre)
        val auSite = declarations.firstOrNull { declaration ->
            jetonNom(declaration)?.let { it.debut == identifiant.debut && it.fin == identifiant.fin } == true
        }
        if (auSite != null) {
            return emplacementsDe(auSite, uriCible)
        }
        declarations.firstOrNull { it.nom() == nom }?.let { retour ->
            return emplacementsDe(retour, uriCible)
        }

        // 2. Les autres documents ouverts (ordre d'ouverture).
        for ((uriAutre, document) in autres) {
            SurvolDocuments.declarationsRecursives(document.arbre)
                .firstOrNull { it.nom() == nom }
                ?.let { return emplacementsDe(it, uriAutre) }
        }

        // 3. Les déclarations externes du scan (L1) — l'URI de fichier du
        // scanner et la plage du nom (fiche 031 : l'usage va vers
        // l'`expect` de commonMain).
        val externe = scan?.declaration(nom)
        if (externe != null) {
            val plage = externe.plageNom ?: return emptyList()
            return listOf(EmplacementKotlin(uri = externe.uri, plage = plage))
        }

        // 4. Les types du MODÈLE externe (vague 2f — fiche 047 : la
        // définition d'une référence Kotlin OUVRE LE JAVA du scan ; la
        // classe ViewBinding ouvre le FICHIER DE LAYOUT — l'URI portée par
        // la contribution, la plage nulle d'une source non lue).
        for ((nomSimple, uriExterne) in externes) {
            if (nomSimple == nom && uriExterne.isNotEmpty()) {
                return listOf(EmplacementKotlin(uri = uriExterne, plage = PlageTexte(0, 0)))
            }
        }
        return emptyList()
    }

    /**
     * Rend les SYMBOLES du document (le plan du fichier).
     *
     * @param cible le document ouvert visé ;
     * @return les symboles en ordre du document ; liste vide sans
     * déclaration nommée.
     */
    internal fun symboles(cible: DocumentOuvert): List<SymboleDocumentKotlin> {
        val resultat = mutableListOf<SymboleDocumentKotlin>()
        for (declaration in SurvolDocuments.declarationsRecursives(cible.arbre)) {
            val nom = declaration.nom() ?: continue
            val jeton = jetonNom(declaration) ?: continue
            resultat.add(
                SymboleDocumentKotlin(
                    nom = nom,
                    genre = genreDe(declaration),
                    plage = PlageTexte(declaration.noeud.debut, declaration.noeud.fin),
                    plageNom = PlageTexte(jeton.debut, jeton.fin),
                ),
            )
        }
        return resultat
    }

    /**
     * Rend les RÉFÉRENCES du nom sous le point : le site de déclaration
     * (s'il vit dans le document) puis chaque référence du même nom, en
     * ordre du document.
     *
     * @param cible le document ouvert visé ;
     * @param uriCible l'identifiant du document visé ;
     * @param deplacement l'offset du caret DANS le nom (usage ou
     * déclaration) ;
     * @return les emplacements ; liste vide sans identifiant ou sans
     * occurrence.
     */
    internal fun occurrences(
        cible: DocumentOuvert,
        uriCible: String,
        deplacement: Int,
    ): List<EmplacementKotlin> {
        val identifiant = jetonIdentifierA(cible.arbre.noeud, deplacement) ?: return emptyList()
        val nom = identifiant.texte
        val resultat = mutableListOf<EmplacementKotlin>()

        // Les SITES de déclaration du document portant ce nom.
        for (declaration in SurvolDocuments.declarationsRecursives(cible.arbre)) {
            if (declaration.nom() != nom) continue
            jetonNom(declaration)?.let { jeton ->
                resultat.add(EmplacementKotlin(uri = uriCible, plage = PlageTexte(jeton.debut, jeton.fin)))
            }
        }

        // Les RÉFÉRENCES du même nom (expressions de référence — les
        // callee d'appels comprises), en ordre du document.
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.REFERENCE_EXPRESSION && noeud.texteCanonique == nom) {
                resultat.add(EmplacementKotlin(uri = uriCible, plage = PlageTexte(noeud.debut, noeud.fin)))
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(cible.arbre.noeud)
        return resultat
    }

    /** Les emplacements d'une déclaration du DOCUMENT (plage du nom seul). */
    private fun emplacementsDe(declaration: KtDeclaration, uri: String): List<EmplacementKotlin> {
        val jeton = jetonNom(declaration) ?: return emptyList()
        return listOf(EmplacementKotlin(uri = uri, plage = PlageTexte(jeton.debut, jeton.fin)))
    }

    /** Le genre NEUTRE d'une déclaration typée. */
    private fun genreDe(declaration: KtDeclaration): GenreSymboleKotlin = when (declaration) {
        is KtFonction -> GenreSymboleKotlin.FONCTION
        is KtPropriete -> GenreSymboleKotlin.PROPRIETE
        is KtTypealias -> GenreSymboleKotlin.TYPEALIAS
        is jo.codeide.lsp.kotlin.parse.kt.KtObjet -> GenreSymboleKotlin.OBJET
        is KtClasse -> when (declaration.genreClasse) {
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.CLASSE -> GenreSymboleKotlin.CLASSE
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.INTERFACE -> GenreSymboleKotlin.INTERFACE
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.ENUM -> GenreSymboleKotlin.ENUM
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.OBJET -> GenreSymboleKotlin.OBJET
        }
        else -> GenreSymboleKotlin.CLASSE
    }

    /** Le jeton NOM d'une déclaration (premier identifiant propre). */
    private fun jetonNom(declaration: KtDeclaration): JetonBrut? {
        return declaration.noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
    }

    /** Le jeton IDENTIFIER qui contient l'offset du caret. */
    private fun jetonIdentifierA(racine: NoeudArbre, deplacement: Int): JetonBrut? {
        var trouve: JetonBrut? = null
        fun walk(n: NoeudArbre) {
            for (jeton in n.jetonsPropres) {
                if (jeton.genre == KtTokens.IDENTIFIER && jeton.debut <= deplacement && deplacement < jeton.fin) {
                    trouve = jeton
                }
            }
            n.enfants.forEach { walk(it) }
        }
        walk(racine)
        return trouve
    }
}

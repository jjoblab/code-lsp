// CodeIDE — façade `Kt*` : racine du document (module :kotlin, étage 1
// vague 1a). Code NEUF jjoblab (clean-room — contrat : fiche 013 du cahier,
// comportements `directive-package---nom-qualifie`,
// `fichier-sans-package---nom-qualifie-null-(directive-vide-presente)`,
// `imports-simple-alias-et-wildcard`,
// `declarations-de-premier-niveau---noms-et-genres`).
package jo.codeide.lsp.kotlin.parse.kt

import jo.codeide.lsp.kotlin.parse.NoeudArbre
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * La racine de la façade `Kt*` : un document Kotlin entier.
 *
 * Un document est la racine de l'arbre de la grammaire officielle vendorée —
 * `kotlin.FILE` pour un fichier `.kt`, `SCRIPT` pour un script `.kts`
 * ([estScript] fait la distinction, miroir du mode script du parseur). Les
 * trois sections du préambule se lisent par accesseurs : la directive de
 * package ([directivePackage] — toujours présente dans l'arbre, de plage
 * nulle si aucun package n'est écrit), les imports ([imports]) et les
 * déclarations de premier niveau ([declarations]).
 *
 * Toutes les méthodes sont conservatrices (D3) : ce qui n'est pas écrit se
 * lit `null` ou vide, jamais comme une exception — le document peut être
 * tronqué, hostile ou vide.
 */
public class KtFichier internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtNoeud(noeud, source) {

    /**
     * Interroge si le document est un SCRIPT (`.kts` — racine `SCRIPT` de la
     * grammaire, où les instructions de premier niveau sont autorisées).
     *
     * @return `true` pour un script, `false` pour un fichier (`kotlin.FILE`).
     */
    public fun estScript(): Boolean = noeud.genre == KtNodeTypes.SCRIPT

    /**
     * Rend la directive de package du document.
     *
     * La grammaire rend la directive TOUJOURS PRÉSENTE dans l'arbre — de
     * plage nulle, sans référence enfant, lorsqu'aucun package n'est écrit :
     * le rendu est alors non nul mais [KtDirectivePackage.nomQualifie] rend
     * `null` (fiche 013, comportement
     * `fichier-sans-package---nom-qualifie-null-(directive-vide-presente)`).
     *
     * @return la directive de package ; `null` (conservateur, jamais observé
     * — chemin défensif) si l'arbre n'a pas de production `PACKAGE_DIRECTIVE`.
     */
    public fun directivePackage(): KtDirectivePackage? {
        val directive = premierEnfantDeGenre(KtNodeTypes.PACKAGE_DIRECTIVE) ?: return null
        return KtDirectivePackage(directive, source)
    }

    /**
     * Rend les directives d'import du document, en ordre d'écriture.
     *
     * @return les imports (simples, aliasés et jokers confondus — les formes
     * se distinguent sur [KtDirectiveImport.alias] et
     * [KtDirectiveImport.estJoker]) ; liste vide si le document n'a aucun
     * import (jamais d'exception).
     */
    public fun imports(): List<KtDirectiveImport> {
        val liste = premierEnfantDeGenre(KtNodeTypes.IMPORT_LIST) ?: return emptyList()
        return liste.enfants
            .filter { it.genre == KtNodeTypes.IMPORT_DIRECTIVE }
            .map { KtDirectiveImport(it, source) }
    }

    /**
     * Rend les déclarations de premier niveau du document, en ordre du
     * document (fiche 013, comportement
     * `declarations-de-premier-niveau---noms-et-genres`).
     *
     * Les genres typés à cet étage : classes/interfaces/enums ([KtClasse]),
     * objets nommés ([KtObjet]), fonctions ([KtFonction]), propriétés
     * ([KtPropriete]), alias de type ([KtTypealias]). Les instructions d'un
     * script ne sont PAS des déclarations — elles restent joignables par la
     * navigation générique [enfants] (sous-rapport D3 honnête).
     *
     * @return les déclarations typées ; liste vide si le document n'en a pas
     * (jamais d'exception).
     */
    public fun declarations(): List<KtDeclaration> {
        return noeud.enfants.mapNotNull { FabriqueKt.declaration(it, source) }
    }
}

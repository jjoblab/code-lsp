// CodeIDE — façade `Kt*` : déclarations typées (module :kotlin, étage 1
// vague 1a). Code NEUF jjoblab (clean-room — contrat : fiche 013 du cahier,
// comportements `declarations-de-premier-niveau---noms-et-genres`,
// `classe---membres,-interface,-enum`, `fonction---parametres-nommes`,
// `propriete-avec-objet-anonyme---nom-de-propriete-conservateur`,
// `declaration-tronquee-sans-identifiant---nom-null-sans-lever`).
package jo.codeide.lsp.kotlin.parse.kt

import com.intellij.platform.syntax.SyntaxElementType
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Hiérarchie fermée des déclarations typées de la façade `Kt*`.
 *
 * Les genres reconnus (vocabulaire du compilateur) : `CLASS` ([KtClasse],
 * interfaces et enums compris — le genre fin se lit sur
 * [KtClasse.genreClasse]), `OBJECT_DECLARATION` ([KtObjet]), `FUN`
 * ([KtFonction]), `PROPERTY` ([KtPropriete]), `TYPEALIAS` ([KtTypealias]),
 * `ENUM_ENTRY` ([KtEntreeEnum]).
 *
 * Les autres formes de membres (`SECONDARY_CONSTRUCTOR`, `CLASS_INITIALIZER`)
 * ne sont PAS typées à cet étage : elles restent joignables par la navigation
 * générique [KtNoeud.enfants] (sous-rapport D3 honnête — jamais de typage
 * approximatif), et seront typées quand leur fiche le demandera.
 */
public sealed class KtDeclaration protected constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtNoeud(noeud, source) {

    /**
     * Rend le NOM de la déclaration.
     *
     * Le nom est le premier jeton `IDENTIFIER` directement rattaché à la
     * déclaration (hors productions enfants et hors trivia) : les
     * modificateurs et annotations sont des productions ou des mots-clés, ils
     * n'entrent pas en concurrence.
     *
     * @return le nom écrit (par ex. `"Exemple"`, `"calculer"`) ; `null`
     * (conservateur, fiche 013 — comportement
     * `declaration-tronquee-sans-identifiant---nom-null-sans-lever`) si la
     * déclaration est tronquée avant son identifiant — AUCUNE exception
     * n'est levée, quelle que soit la forme du texte.
     */
    public fun nom(): String? {
        return noeud.jetonsPropres
            .firstOrNull { it.genre == KtTokens.IDENTIFIER }
            ?.texte
    }

    /**
     * Rend les ANNOTATIONS de la déclaration, en ordre du document.
     *
     * Les annotations se portent par la liste de modificateurs
     * (`MODIFIER_LIST` → `ANNOTATION_ENTRY`) ; une déclaration sans
     * annotations rend une liste VIDE (fiche 013, comportement
     * `annotations---fonction-sans-annotations-et-fonction-avec-corps`),
     * jamais `null` ni une exception.
     *
     * @return les entrées d'annotation dans l'ordre où elles sont écrites.
     */
    public fun annotations(): List<KtAnnotationEntree> {
        val modificateurs = premierEnfantDeGenre(KtNodeTypes.MODIFIER_LIST) ?: return emptyList()
        return modificateurs.enfants
            .filter { it.genre == KtNodeTypes.ANNOTATION_ENTRY }
            .map { KtAnnotationEntree(it, source) }
    }
}

/**
 * Genre fin d'une déclaration de type : classe (`class`), interface
 * (`interface`), enum (`enum class`) ou objet nommé (`object`).
 */
public enum class KtGenreClasse {
    /** Une classe écrite `class` (les classes d'annotation comprises). */
    CLASSE,

    /** Une interface écrite `interface` (les interfaces `fun interface` comprises). */
    INTERFACE,

    /** Un enum écrit `enum class`. */
    ENUM,

    /** Un objet nommé écrit `object` (les `companion object` compris). */
    OBJET,
}

/**
 * Une déclaration de type : classe, interface, enum ou objet nommé.
 *
 * Le genre fin se lit sur [genreClasse] (les quatre formes partagent le
 * vocabulaire `CLASS`/`OBJECT_DECLARATION` de la grammaire) ; les membres se
 * lisent sur [membres] (fiche 013, comportement
 * `classe---membres,-interface,-enum`).
 */
public class KtClasse internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtDeclaration(noeud, source) {

    /**
     * Le genre fin de la déclaration de type (CLASSE, INTERFACE, ENUM,
     * OBJET).
     *
     * Dénombrement conservateur : `OBJECT_DECLARATION` rend OBJET ; `CLASS`
     * portant le modificateur doux `enum` rend ENUM ; `CLASS` portant le
     * mot-clé `interface` rend INTERFACE ; tout autre `CLASS` rend CLASSE.
     */
    public val genreClasse: KtGenreClasse
        get() {
            if (noeud.genre == KtNodeTypes.OBJECT_DECLARATION) return KtGenreClasse.OBJET
            val modificateurs = premierEnfantDeGenre(KtNodeTypes.MODIFIER_LIST)
            val estEnum = modificateurs?.enfants?.any { it.genre == KtTokens.ENUM_MODIFIER } == true
            if (estEnum) return KtGenreClasse.ENUM
            if (noeud.jetonsPropres.any { it.genre == KtTokens.INTERFACE_KEYWORD }) {
                return KtGenreClasse.INTERFACE
            }
            return KtGenreClasse.CLASSE
        }

    /**
     * Rend les déclarations typées du CORPS de la classe, en ordre du
     * document.
     *
     * Les entrées d'enum sont rendues comme membres (des [KtEntreeEnum]) ;
     * les formes non typées à cet étage (constructeurs secondaires,
     * initialisateurs) sont omises du résultat — elles restent joignables
     * par la navigation générique [KtNoeud.enfants] (sous-rapport D3).
     *
     * @return les membres typés (fonctions, propriétés, types imbriqués,
     * objets, alias, entrées d'enum) ; liste vide si le corps est vide ou
     * absent (jamais d'exception).
     */
    public fun membres(): List<KtDeclaration> {
        val corps = premierEnfantDeGenre(KtNodeTypes.CLASS_BODY) ?: return emptyList()
        return corps.enfants.mapNotNull { FabriqueKt.declaration(it, source) }
    }
}

/**
 * Une déclaration de fonction (`fun`).
 */
public class KtFonction internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtDeclaration(noeud, source) {

    /**
     * Rend les paramètres NOMMÉS de la fonction, en ordre d'écriture (fiche
     * 013, comportement `fonction---parametres-nommes`).
     *
     * @return les paramètres de valeur de la liste de paramètres ; liste
     * vide si la fonction n'a pas de liste (jamais d'exception).
     */
    public fun parametres(): List<KtParametreValeur> {
        val liste = premierEnfantDeGenre(KtNodeTypes.VALUE_PARAMETER_LIST) ?: return emptyList()
        return liste.enfants
            .filter { it.genre == KtNodeTypes.VALUE_PARAMETER }
            .map { KtParametreValeur(it, source) }
    }

    /**
     * Interroge si la fonction porte un CORPS DE BLOC (`{ … }`).
     *
     * Les corps d'expression (`fun f() = 1`) ne portent pas de bloc — le
     * rendu reste `false`, distinction honnête (fiche 013, comportement
     * `annotations---fonction-sans-annotations-et-fonction-avec-corps`).
     *
     * @return `true` si la fonction a une production `BLOCK` enfant ;
     * `false` sinon (corps d'expression ou corps tronqué/absent).
     */
    public fun possedeCorpsBloc(): Boolean {
        return premierEnfantDeGenre(KtNodeTypes.BLOCK) != null
    }
}

/**
 * Une déclaration de propriété (`val`/`var`), initialiseur compris.
 *
 * Le nom reste celui de la PROPRIÉTÉ même si l'initialisateur est un objet
 * anonyme (fiche 013, comportement
 * `propriete-avec-objet-anonyme---nom-de-propriete-conservateur`) : le
 * littéral d'objet est une production enfant, il ne concurrence jamais le
 * nom.
 */
public class KtPropriete internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtDeclaration(noeud, source)

/**
 * Une déclaration d'alias de type (`typealias`).
 */
public class KtTypealias internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtDeclaration(noeud, source)

/**
 * Une déclaration d'objet nommé (`object`, `companion object` compris).
 */
public class KtObjet internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtDeclaration(noeud, source)

/**
 * Une entrée d'enum (`UN`, `DEU(1)`…).
 */
public class KtEntreeEnum internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtDeclaration(noeud, source)

/**
 * Un paramètre de valeur d'une liste de paramètres.
 */
public class KtParametreValeur internal constructor(
    noeud: NoeudArbre,
    source: CharSequence,
) : KtNoeud(noeud, source) {

    /**
     * Rend le NOM du paramètre.
     *
     * @return le premier jeton `IDENTIFIER` propre du paramètre (le type et
     * la valeur par défaut sont des productions enfants) ; `null`
     * (conservateur) si le paramètre est tronqué avant son identifiant.
     */
    public fun nom(): String? {
        return noeud.jetonsPropres
            .firstOrNull { it.genre == KtTokens.IDENTIFIER }
            ?.texte
    }
}

/**
 * Fabrique interne des déclarations typées depuis les nœuds de l'arbre.
 */
internal object FabriqueKt {

    /**
     * Rend la déclaration typée correspondant à [noeud].
     *
     * @param noeud le nœud candidat ;
     * @param source le texte source complet ;
     * @return l'enveloppe typée, ou `null` (sous-rapport D3) si le genre du
     * nœud n'est pas une déclaration typée à cet étage.
     */
    internal fun declaration(noeud: NoeudArbre, source: CharSequence): KtDeclaration? {
        return when (noeud.genre) {
            KtNodeTypes.CLASS -> KtClasse(noeud, source)
            KtNodeTypes.OBJECT_DECLARATION -> KtObjet(noeud, source)
            KtNodeTypes.FUN -> KtFonction(noeud, source)
            KtNodeTypes.PROPERTY -> KtPropriete(noeud, source)
            KtNodeTypes.TYPEALIAS -> KtTypealias(noeud, source)
            KtNodeTypes.ENUM_ENTRY -> KtEntreeEnum(noeud, source)
            else -> null
        }
    }

    /**
     * Les genres de nœuds qui sont des déclarations de premier niveau (ou de
     * membres) typées à cet étage.
     */
    internal val genresDeclaration: Set<SyntaxElementType> = setOf(
        KtNodeTypes.CLASS,
        KtNodeTypes.OBJECT_DECLARATION,
        KtNodeTypes.FUN,
        KtNodeTypes.PROPERTY,
        KtNodeTypes.TYPEALIAS,
        KtNodeTypes.ENUM_ENTRY,
    )
}

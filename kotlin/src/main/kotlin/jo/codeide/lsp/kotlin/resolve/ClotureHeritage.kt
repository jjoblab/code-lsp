// CodeIDE — clôture des SUPERTYPES et PILE DES THIS (module :kotlin, étage
// 2 vague 2c — fiches 044/028/040 du cahier : la voie 2-ter « membres
// HÉRITÉS » d'ADR 0216, la clôture MIXTE projet/classpath du plan de
// migration ADR 0106, la chaîne profondeur 7 du monde Views ADR 0219 et la
// pile des this des objets anonymes). Code NEUF jjoblab (clean-room —
// l'ancien `resolve/` est en lecture interdite R9.1 ; cette clôture est
// écrite cahier en main).
package jo.codeide.lsp.kotlin.resolve

import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtDirectiveImport
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le genre d'un membre de la CLÔTURE (hérité ou déclaré).
 */
internal enum class GenreMembreHerite {

    /** `fun` membre (méthode JVM comprise). */
    FONCTION,

    /** Propriété `val`/`var` (champ JVM et dérivée ADR 0219 comprises). */
    PROPRIETE,

    /** Un type IMBRIQUÉ du conteneur ou d'un supertype (fiche 029/028). */
    TYPE_IMBRIQUE,
}

/**
 * Un membre de la CLÔTURE d'un type : déclaré par le type lui-même ou par
 * un maillon de sa chaîne de supertypes (PROJET ou CLASSPATH — la clôture
 * mixte, vague 2c).
 *
 * @property nom le nom appelé ;
 * @property genre le genre du membre ;
 * @property typeEcrit le nom ÉCRIT du type (membre PROJET — résolu à la
 * consultation par l'univers) ;
 * @property lienJvm le FQCN du maillon JVM qui porte le membre (le typage
 * et le survol consultent la vue JVM PAR CE LIEN — les descripteurs n'entrent
 * pas dans la clôture, ils se lisent à la demande) ;
 * @property typeImbrique l'imbriquée TYPÉE quand le membre est un type
 * imbriqué (PROJET du modèle, ou JVM `Conteneur$Nom`) ;
 * @property proprietaireFqcn le FQCN du DÉCLARANT (le survol l'accompagne,
 * fiche 040 : `val layoutInflater: LayoutInflater` sur `android.app.Activity`).
 */
internal class MembreHerite(
    internal val nom: String,
    internal val genre: GenreMembreHerite,
    internal val typeEcrit: String?,
    internal val lienJvm: String?,
    internal val typeImbrique: TypeValeur?,
    internal val proprietaireFqcn: String,
)

/**
 * La CLÔTURE D'HÉRITAGE d'un type du projet (vague 2c — ADR 0216/0106) :
 * le jeu de membres COMPLET du type et de toute sa chaîne de supertypes,
 * clôture MIXTE — les maillons PROJET se relisent récursivement (le frère
 * fermé du scan ou le frère OUVERT VIVANT, ses membres déclarés compris),
 * les maillons CLASSPATH se lisent par la vue JVM (jeu complet de la
 * chaîne binaire + IMBRIQUÉES `$` — `Resultats` nu hérité de `Base`).
 *
 * La clôture est NULLE quand la chaîne ne SE FERME PAS (un supertype écrit
 * introuvable du projet, des imports et du catalogue KP8 ; un enum ; un
 * maillon JVM illisible dans les entrées) : l'univers des membres est
 * OUVERT, les preuves d'absence s'y taisent (D3 — jamais un faux positif).
 *
 * @property membres les membres par nom (le DÉCLARÉ/le plus dérivé gagne).
 */
internal class ClotureHeritage internal constructor(
    internal val membres: Map<String, MembreHerite>,
) {

    /** Le nom est-il porté par la clôture (déclaré ou hérité) ? */
    internal fun contient(nom: String): Boolean = nom in membres

    /** Le membre de la clôture portant ce nom (`null` sinon). */
    internal fun membre(nom: String): MembreHerite? = membres[nom]
}

/**
 * Une COUCHE de la pile des this (vague 2c — fiche 028 : l'objet anonyme
 * d'abord, puis les classes englobantes ; fiche 040 : le récepteur implicite
 * d'un `apply`).
 *
 * @property typeInterne le type de la couche (projet ou bibliothèque) —
 * c'est lui qui type `this` ;
 * @property cloture la clôture de la couche (`null` = OUVERTE : les noms nus
 * y sont muets, D3) ;
 * @property opaque la couche est ILLISIBLE (déclaration introuvable du
 * modèle — en-tête brisée) : elle BLOQUE la preuve des noms nus des couches
 * profondes (jamais un faux positif sur des membres inconnus).
 */
internal class CoucheThis(
    internal val typeInterne: TypeValeur?,
    internal val cloture: ClotureHeritage?,
    internal val opaque: Boolean,
)

/**
 * La PILE DES THIS d'une position (vague 2c — ADR 0106 : « récepteurs
 * implicites, pile des this des objets anonymes ») : les couches de la plus
 * PROFONDE à la plus EXTERNE — la consultation va de la profonde vers
 * l'externe (l'objet anonyme d'abord, puis les classes englobantes).
 */
internal class PileThis private constructor(private val couches: List<CoucheThis>) {

    /** Empile une couche (la pile est IMMUAble — une nouvelle instance). */
    internal fun empiler(couche: CoucheThis): PileThis = PileThis(couches + couche)

    /**
     * Une couche bloque-t-elle la preuve des NOMS NUS (ouvert ou opaque) ?
     * La pile est consultée de la profonde vers l'externe : dès qu'une
     * couche englobante est ouverte, l'univers des membres ne se clôt pas —
     * l'absence d'un nom nu ne se prouve plus (D3).
     */
    internal val bloqueNomsNus: Boolean
        get() = couches.any { it.opaque || it.cloture == null }

    /** Le nom est-il un MEMBRE d'une couche (la profonde gagne) ? */
    internal fun contientMembre(nom: String): Boolean =
        couches.any { it.cloture?.contient(nom) == true }

    /** Le membre d'une couche (la couche la plus PROFONDE qui le porte). */
    internal fun membre(nom: String): MembreHerite? =
        couches.firstNotNullOfOrNull { it.cloture?.membre(nom) }

    /**
     * Le TYPE d'un membre de la pile (vague 2c — le typage des récepteurs
     * implicites : `layoutInflater` type `LayoutInflater`, `compte` type
     * `Int`, l'imbriquée `Resultats` type `Base$Resultats`).
     *
     * @param nom le nom du membre ;
     * @param univers l'univers des preuves (résolution des types écrits) ;
     * @return le type du membre ; `null` si absent ou non typable.
     */
    internal fun typeDuMembre(nom: String, univers: UniversPreuves): TypeValeur? {
        val membre = membre(nom) ?: return null
        membre.typeImbrique?.let { return it }
        val ecrit = membre.typeEcrit ?: return null
        return univers.resoudreTypeEcrit(ecrit)
    }

    /** Le type de la couche la plus PROFONDE (celui de `this`). */
    internal val typeInterne: TypeValeur?
        get() = couches.lastOrNull()?.typeInterne

    /** Le companion de la pile vide (le niveau du document). */
    internal companion object {
        internal val VIDE: PileThis = PileThis(emptyList())
    }
}

/**
 * Le bâtisseur des CLÔTURES (vague 2c) : la fermeture transitive des
 * supertypes d'un type du projet — clauses ÉCRITES relues maillon par
 * maillon, les maillons projet RECURSIFS (garde de cycle, borne de
 * profondeur), les maillons classpath par la VUE JVM (chaîne binaire
 * complète + IMBRIQUÉES `$`), les implicites de Any/Object en socle.
 */
internal object Clotures {

    /** La borne de profondeur de la fermeture transitive. */
    private const val PROFONDEUR_MAX: Int = 24

    /**
     * La clôture d'un TYPE DU PROJET : ses membres déclarés (les imbriquées
     * TYPE_IMBRIQUE liées au modèle) puis la fermeture transitive de ses
     * clauses écrites.
     *
     * @param univers l'univers des preuves ;
     * @param type le type du projet à clore ;
     * @return la clôture ; `null` si la chaîne ne se ferme pas (ouverte).
     */
    internal fun clotureDuType(univers: UniversPreuves, type: TypeProjet): ClotureHeritage? {
        // Un enum porte des membres IMPLICITES invérifiables : ouvert. Les
        // MARQUEURS du lecteur Java (`Enum`/`Record` — leurs membres
        // implicites values/valueOf/name/ordinal/accesseurs de composants
        // ne vivent dans AUCUNE source) ouvrent pareillement (fiche 029 :
        // `CouleurJava.ROUGE` muet, jamais un faux positif).
        if (type.genre == GenreTypeProjet.ENUM) return null
        if (type.genre == GenreTypeProjet.JAVA &&
            type.supertypesEcrits.any { it == "Enum" || it == "Record" }
        ) {
            return null
        }
        val membres = LinkedHashMap<String, MembreHerite>()
        val visites = HashSet<String>()
        if (!fermerType(univers, type, membres, visites, 0)) return null
        return ClotureHeritage(membres)
    }

    /**
     * La clôture d'un OBJET ANONYME (vague 2c — fiche 028 : la pile des
     * this) : les membres DÉCLARÉS du corps de l'objet anonyme puis la
     * fermeture de ses supertypes RÉSOLUS.
     *
     * @param univers l'univers des preuves ;
     * @param supertypes les supertypes résolus de l'objet anonyme ;
     * @param membresDeclares les membres déclarés du corps de l'objet ;
     * @return la clôture ; `null` si la chaîne ne se ferme pas.
     */
    internal fun clotureDeSupertypes(
        univers: UniversPreuves,
        supertypes: List<TypeValeur>,
        membresDeclares: List<MembreProjet>,
    ): ClotureHeritage? {
        val membres = LinkedHashMap<String, MembreHerite>()
        for (membre in membresDeclares) {
            membres[membre.nom] = MembreHerite(
                nom = membre.nom,
                genre = if (membre.genre == GenreMembreProjet.FONCTION) GenreMembreHerite.FONCTION else GenreMembreHerite.PROPRIETE,
                typeEcrit = membre.typeRetourNomme,
                lienJvm = null,
                typeImbrique = null,
                proprietaireFqcn = "(objet anonyme)",
            )
        }
        val visites = HashSet<String>()
        for (supertype in supertypes) {
            when (supertype) {
                is TypeValeur.Projet -> {
                    if (supertype.type.genre == GenreTypeProjet.ENUM) return null
                    if (!fermerType(univers, supertype.type, membres, visites, 0)) return null
                }
                is TypeValeur.Jvm -> {
                    if (!fermerLienJvm(univers, supertype.fqcn, membres, visites, 0)) return null
                }
            }
        }
        return ClotureHeritage(membres)
    }

    /**
     * Ferme un maillon PROJET : membres déclarés (+ data synthétisés déjà
     * membres, + imbriquées liées), puis récursion sur ses clauses écrites.
     *
     * @return `false` si la chaîne ne se ferme pas (ouverte).
     */
    private fun fermerType(
        univers: UniversPreuves,
        type: TypeProjet,
        membres: LinkedHashMap<String, MembreHerite>,
        visites: HashSet<String>,
        profondeur: Int,
    ): Boolean {
        if (profondeur > PROFONDEUR_MAX) return false
        if (!visites.add(type.fqcn)) return true
        val imbriques = univers.modele.imbriquesDu(type.fqcn)
        for (membre in type.membres) {
            if (membre.nom in membres) continue // le plus dérivé gagne.
            membres[membre.nom] = when (membre.genre) {
                GenreMembreProjet.TYPE_IMBRIQUE -> {
                    val imbriquee = imbriques.firstOrNull { it.nomSimple == membre.nom }
                    MembreHerite(
                        nom = membre.nom,
                        genre = GenreMembreHerite.TYPE_IMBRIQUE,
                        typeEcrit = null,
                        lienJvm = null,
                        typeImbrique = imbriquee?.let { TypeValeur.Projet(it) },
                        proprietaireFqcn = type.fqcn,
                    )
                }
                GenreMembreProjet.FONCTION -> MembreHerite(
                    nom = membre.nom,
                    genre = GenreMembreHerite.FONCTION,
                    typeEcrit = membre.typeRetourNomme,
                    lienJvm = null,
                    typeImbrique = null,
                    proprietaireFqcn = type.fqcn,
                )
                GenreMembreProjet.PROPRIETE -> MembreHerite(
                    nom = membre.nom,
                    genre = GenreMembreHerite.PROPRIETE,
                    typeEcrit = membre.typeRetourNomme,
                    lienJvm = null,
                    typeImbrique = null,
                    proprietaireFqcn = type.fqcn,
                )
            }
        }
        // Une classe SANS clause écrite est close par construction (le jeu
        // déclaré + les implicites Any/Object) ; une clause se ferme
        // maillon par maillon.
        if (type.supertypesEcrits.isEmpty()) return true
        for (nomEcrit in type.supertypesEcrits) {
            val maillon = univers.supertypeResolu(nomEcrit) ?: return false
            when (maillon) {
                is TypeValeur.Projet -> {
                    if (maillon.type.genre == GenreTypeProjet.ENUM) return false
                    if (!fermerType(univers, maillon.type, membres, visites, profondeur + 1)) return false
                }
                is TypeValeur.Jvm -> {
                    if (!fermerLienJvm(univers, maillon.fqcn, membres, visites, profondeur + 1)) return false
                }
            }
        }
        return true
    }

    /**
     * Ferme un maillon CLASSPATH : le jeu COMPLET de la chaîne binaire (vue
     * JVM — un maillon illisible des entrées ouvre la clôture) et les
     * IMBRIQUÉES `$` du maillon (fiche 028 : `Resultats` nu hérité de
     * `Base$Resultats`).
     *
     * @return `false` si la chaîne ne se ferme pas (ouverte).
     */
    private fun fermerLienJvm(
        univers: UniversPreuves,
        fqcn: String,
        membres: LinkedHashMap<String, MembreHerite>,
        visites: HashSet<String>,
        profondeur: Int,
    ): Boolean {
        if (profondeur > PROFONDEUR_MAX) return false
        if (!visites.add("jvm:$fqcn")) return true
        val vue = univers.vueJvm ?: return false
        val jeu = vue.jeuComplet(univers.mapeJvm(fqcn)) ?: return false
        for (nom in jeu.noms) {
            if (nom in membres) continue
            membres[nom] = MembreHerite(
                nom = nom,
                genre = GenreMembreHerite.FONCTION,
                typeEcrit = null,
                lienJvm = fqcn,
                typeImbrique = null,
                proprietaireFqcn = fqcn,
            )
        }
        for (simple in vue.imbriquesDe(fqcn)) {
            if (simple in membres) continue
            membres[simple] = MembreHerite(
                nom = simple,
                genre = GenreMembreHerite.TYPE_IMBRIQUE,
                typeEcrit = null,
                lienJvm = null,
                typeImbrique = TypeValeur.Jvm("$fqcn\$$simple"),
                proprietaireFqcn = fqcn,
            )
        }
        return true
    }
}

/** Le nom EFFECTIF d'une directive d'import (alias, sinon dernier segment). */
internal fun KtDirectiveImport.nomEffectifImport(): String? {
    alias()?.let { return it }
    val chemin = nomQualifie() ?: return null
    return chemin.substringAfterLast('.').ifEmpty { null }
}

/**
 * Les COUCHES de la pile des this (vague 2c) : construction par la marche
 * sémantique (déclarations) et PAR POSITION (complétion, survol, quick-fix
 * d'import — le caret désigne la couche profonde).
 */
internal object Couches {

    /** Les fonctions d'application dont la lambda traînante porte le récepteur. */
    internal val FONCTIONS_APPLIQUANTES: Set<String> = setOf("apply", "also", "run")

    /**
     * La couche d'une classe NOMMÉE : le type du MODÈLE portant le FQCN
     * pointé (package + chemin + nom — le document courant PRIME son
     * doublon du scan) et sa clôture. Une classe introuvable du modèle rend
     * une couche OPAQUE (elle bloque la preuve des noms nus de ses membres).
     */
    internal fun nommee(univers: UniversPreuves, chemin: List<String>, nom: String): CoucheThis {
        val fqcn = (listOf(univers.paquet) + chemin + nom)
            .filter { it.isNotEmpty() }
            .joinToString(".")
        val type = univers.modele.type(fqcn)
            ?: return CoucheThis(typeInterne = null, cloture = null, opaque = true)
        return CoucheThis(
            typeInterne = TypeValeur.Projet(type),
            cloture = univers.clotureDe(type),
            opaque = false,
        )
    }

    /**
     * La couche d'un OBJET ANONYME : ses supertypes ÉCRITS résolus (le
     * premier type `this`) et sa clôture — membres DÉCLARÉS du corps puis
     * fermeture des supertypes.
     */
    internal fun anonyme(univers: UniversPreuves, noeud: NoeudArbre): CoucheThis {
        val supertypes = ModeleProjet.supertypesEcrits(noeud)
            .mapNotNull { univers.supertypeResolu(it) }
        val membresDeclares = membresDuCorps(noeud)
        return CoucheThis(
            typeInterne = supertypes.firstOrNull(),
            cloture = Clotures.clotureDeSupertypes(univers, supertypes, membresDeclares),
            opaque = false,
        )
    }

    /**
     * La couche d'un récepteur TYPAÉ (le this implicite d'un `apply`/
     * `also`/`run` — fiche 028) : projet → sa clôture ; bibliothèque → le
     * jeu JVM de la chaîne en clôture (`null` = chaîne illisible, couche
     * ouverte).
     */
    internal fun deType(univers: UniversPreuves, type: TypeValeur): CoucheThis {
        return when (type) {
            is TypeValeur.Projet -> CoucheThis(
                typeInterne = type,
                cloture = univers.clotureDe(type.type),
                opaque = false,
            )
            is TypeValeur.Jvm -> {
                val jeu = univers.vueJvm?.jeuComplet(univers.mapeJvm(type.fqcn))
                val cloture = jeu?.let {
                    ClotureHeritage(
                        it.noms.associateWith { nom ->
                            MembreHerite(nom, GenreMembreHerite.FONCTION, null, type.fqcn, null, type.fqcn)
                        },
                    )
                }
                CoucheThis(typeInterne = type, cloture = cloture, opaque = false)
            }
        }
    }

    /**
     * La pile des this D'UNE POSITION (vague 2c — complétion, survol,
     * quick-fix) : les couches couvrant le caret en ordre d'imbrication —
     * classes nommées (chemin du modèle), objets anonymes, lambdas
     * traînantes d'application sur récepteur typé.
     *
     * @param arbre le document porteur ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison de la région contenant la position ;
     * @param position l'offset du caret ;
     * @return la pile (vide au niveau du document).
     */
    internal fun aPosition(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        position: Int,
    ): PileThis {
        var pile = PileThis.VIDE
        fun nomDe(noeud: NoeudArbre): String? =
            noeud.jetonsPropres.firstOrNull {
                it.genre == KtTokens.IDENTIFIER
            }?.texte

        fun marcher(noeud: NoeudArbre, chemin: List<String>) {
            if (position < noeud.debut || position > noeud.fin) return
            when (noeud.genre) {
                KtNodeTypes.CLASS,
                KtNodeTypes.OBJECT_DECLARATION,
                -> {
                    val nom = nomDe(noeud)
                    if (nom != null) {
                        pile = pile.empiler(nommee(univers, chemin, nom))
                        for (enfant in noeud.enfants) marcher(enfant, chemin + nom)
                    } else {
                        pile = pile.empiler(anonyme(univers, noeud))
                        for (enfant in noeud.enfants) marcher(enfant, chemin)
                    }
                }
                KtNodeTypes.DOT_QUALIFIED_EXPRESSION,
                KtNodeTypes.SAFE_ACCESS_EXPRESSION,
                -> {
                    val recepteur = noeud.enfants.firstOrNull()
                    val selecteur = noeud.enfants.lastOrNull()
                    if (recepteur != null && selecteur != null && recepteur !== selecteur) {
                        marcher(recepteur, chemin)
                        if (selecteur.genre == KtNodeTypes.CALL_EXPRESSION) {
                            val nomSel = selecteur.enfants
                                .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                                ?.texteCanonique.orEmpty()
                            val couche = if (nomSel in FONCTIONS_APPLIQUANTES) {
                                TypageBorne.typeDeLexpression(recepteur, univers, liaison, pile)
                                    ?.let { deType(univers, it) }
                            } else {
                                null
                            }
                            for (enfant in selecteur.enfants) {
                                if (enfant.genre == KtNodeTypes.LAMBDA_ARGUMENT && couche != null) {
                                    pile = pile.empiler(couche)
                                }
                                marcher(enfant, chemin)
                            }
                        } else {
                            marcher(selecteur, chemin)
                        }
                    } else {
                        noeud.enfants.forEach { marcher(it, chemin) }
                    }
                }
                else -> noeud.enfants.forEach { marcher(it, chemin) }
            }
        }
        marcher(arbre.noeud, emptyList())
        return pile
    }

    /** Les membres DÉCLARÉS du corps d'un objet anonyme (fun/propriétés). */
    private fun membresDuCorps(noeud: NoeudArbre): List<MembreProjet> {
        val corps = noeud.enfants.firstOrNull {
            it.genre == KtNodeTypes.CLASS_BODY
        } ?: return emptyList()
        val membres = mutableListOf<MembreProjet>()
        for (membre in corps.enfants) {
            when (membre.genre) {
                KtNodeTypes.FUN -> {
                    val n = membre.jetonsPropres.firstOrNull {
                        it.genre == KtTokens.IDENTIFIER
                    }?.texte ?: continue
                    membres.add(MembreProjet(n, GenreMembreProjet.FONCTION, typeRetourEcrit(membre)))
                }
                KtNodeTypes.PROPERTY -> {
                    val n = membre.jetonsPropres.firstOrNull {
                        it.genre == KtTokens.IDENTIFIER
                    }?.texte ?: continue
                    membres.add(MembreProjet(n, GenreMembreProjet.PROPRIETE, null))
                }
                else -> Unit
            }
        }
        return membres
    }

    /** Le type de retour ÉCRIT d'une fonction (après la liste de paramètres). */
    private fun typeRetourEcrit(noeud: NoeudArbre): String? {
        val enfants = noeud.enfants
        val listeParams = enfants.indexOfFirst {
            it.genre == KtNodeTypes.VALUE_PARAMETER_LIST
        }
        if (listeParams < 0) return null
        for (i in (listeParams + 1) until enfants.size) {
            if (enfants[i].genre != KtNodeTypes.TYPE_REFERENCE) continue
            return enfants[i].texteCanonique.substringBefore('<').ifEmpty { null }
        }
        return null
    }
}

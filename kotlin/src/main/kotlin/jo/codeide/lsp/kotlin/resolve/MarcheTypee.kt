// CodeIDE — la marche TYPÉE des expressions (module :kotlin, étage 2 vague
// 2a — socle de résolution). Code NEUF jjoblab (clean-room — contrat : fiche
// 022 du cahier : le typage BORNÉ par types écrits et initialiseurs, la
// « sélection par types complets » ADR 0223 ; l'ancien `resolve/` est en
// lecture interdite R9.1).
package jo.codeide.lsp.kotlin.resolve

import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le typage BORNÉ des expressions du document (ADR 0107 — périmètre assumé :
 * ce qui se DÉCIDE sur l'arbre + la résolution bornée) :
 *
 * - un NOM LIÉ par les portées (paramètre, locale, membre) : son type ÉCRIT,
 *   ou le type de son INITIALISATEUR (fiche 022 : `val q = p.copy(x = 1)` —
 *   q est un `Point` par le retour écrit de `copy`) ;
 * - un NOM DE TYPE en position d'expression (objet, accès statique) ;
 * - un appel NU de constructeur (le type du construct) ou de fonction au
 *   RETOUR ÉCRIT ;
 * - une CHAÎNE qualifiée : le type de retour ÉCRIT du membre typé.
 *
 * Tout le reste (littéraux, appels classpath, `this`, lambdas) rend `null` :
 * le récepteur n'est pas typable à cet étage — le sélecteur reste muet (D3).
 */
internal object MarcheTypee {

    /**
     * La liaison nom → type écrit des portées d'une région (PARESSEUSE et
     * mémoïsée : le typage d'un initialiseur requiert la marche typée, qui
     * requiert la liaison — la récursion est gardée contre les cycles).
     *
     * @param arbre le document porteur ;
     * @param univers l'univers des preuves (modèle du projet) ;
     * @param region la région consultée (fonction ou propriété propre) ;
     * `null` pour la liaison du document (hors régions de fonction).
     */
    internal class Liaison(
        private val arbre: KtFichier,
        private val univers: UniversPreuves,
        private val region: NoeudArbre?,
    ) {
        private val cache = HashMap<String, String?>()
        private val enCours = HashSet<String>()

        /** Le type écrit du nom lié (`null` si aucune liaison ne le porte). */
        operator fun invoke(nom: String): String? {
            if (cache.containsKey(nom)) return cache[nom]
            if (!enCours.add(nom)) return null
            val type = resoudre(nom)
            enCours.remove(nom)
            cache[nom] = type
            return type
        }

        /** Résout le nom : portée locale de la région, puis document. */
        private fun resoudre(nom: String): String? {
            if (region != null) {
                chercher(region, nom, locaux = true)?.let { return it }
            }
            return chercher(arbre.noeud, nom, locaux = false, hors = region)
        }

        /**
         * Cherche la liaison du nom dans un sous-arbre : paramètre de valeur
         * ou propriété (type écrit, sinon initialiseur typé par la marche).
         * Quand [locaux] est faux, les sous-arbres de FONCTION ne sont pas
         * descendus (les locales d'une fonction n'existent que dans sa
         * région).
         */
        private fun chercher(
            noeud: NoeudArbre,
            nom: String,
            locaux: Boolean,
            hors: NoeudArbre? = null,
        ): String? {
            if (noeud === hors) return null
            when (noeud.genre) {
                KtNodeTypes.VALUE_PARAMETER -> {
                    val n = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                    if (n == nom) {
                        noeud.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                            ?.texteCanonique?.substringBefore('<')?.ifEmpty { null }
                            ?.let { return it }
                    }
                }
                KtNodeTypes.PROPERTY -> {
                    val n = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                    if (n == nom) {
                        noeud.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                            ?.texteCanonique?.substringBefore('<')?.ifEmpty { null }
                            ?.let { return it }
                        // Pas de type écrit : l'INITIALISEUR type la propriété
                        // (marche typée — `val q = p.copy(…)`).
                        val initialiseur = initialiseurDe(propriete = noeud)
                        if (initialiseur != null) {
                            typeProjetDeLexpression(initialiseur, univers, this)
                                ?.let { return it.nomSimple }
                        }
                    }
                }
                else -> Unit
            }
            if (noeud.genre != KtNodeTypes.FUN || locaux) {
                for (enfant in noeud.enfants) {
                    chercher(enfant, nom, locaux, hors)?.let { return it }
                }
            }
            return null
        }

        /** L'initialisateur d'une propriété (le dernier enfant expression). */
        private fun initialiseurDe(propriete: NoeudArbre): NoeudArbre? {
            return propriete.enfants.lastOrNull {
                it.genre != KtNodeTypes.TYPE_REFERENCE &&
                    it.genre != KtNodeTypes.MODIFIER_LIST &&
                    it.genre != KtNodeTypes.PROPERTY_DELEGATE &&
                    it.genre != KtNodeTypes.PROPERTY_ACCESSOR
            }
        }
    }

    /**
     * Le type du PROJET d'une expression (le typage borné de la vague) ;
     * `null` quand l'expression n'est pas typable à cet étage.
     *
     * @param noeud l'expression à typer ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison des portées de la région consultée.
     */
    internal fun typeProjetDeLexpression(
        noeud: NoeudArbre,
        univers: UniversPreuves,
        liaison: Liaison,
    ): TypeProjet? {
        return when (noeud.genre) {
            KtNodeTypes.REFERENCE_EXPRESSION -> {
                val nom = noeud.texteCanonique
                if (nom.isEmpty()) return null
                univers.typeProjetDuNomEcrit(liaison(nom)) ?: univers.typeProjetEcrit(nom)
            }
            KtNodeTypes.CALL_EXPRESSION -> {
                val nom = noeud.enfants
                    .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                    ?.texteCanonique.orEmpty()
                if (nom.isEmpty()) return null
                univers.typeProjetEcrit(nom)
                    ?: univers.modele.appelables(nom, univers.paquet)
                        .firstNotNullOfOrNull { it.typeRetourNomme }
                        ?.let { univers.typeProjetDuNomEcrit(it) }
            }
            KtNodeTypes.DOT_QUALIFIED_EXPRESSION, KtNodeTypes.SAFE_ACCESS_EXPRESSION -> {
                val recepteur = noeud.enfants.firstOrNull() ?: return null
                val selecteur = noeud.enfants.lastOrNull() ?: return null
                val typeRecepteur = typeProjetDeLexpression(recepteur, univers, liaison) ?: return null
                val nomMembre = when (selecteur.genre) {
                    KtNodeTypes.REFERENCE_EXPRESSION -> selecteur.texteCanonique
                    KtNodeTypes.CALL_EXPRESSION ->
                        selecteur.enfants.firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                            ?.texteCanonique.orEmpty()
                    else -> ""
                }
                if (nomMembre.isEmpty()) return null
                univers.typeProjetDuNomEcrit(typeRecepteur.membre(nomMembre)?.typeRetourNomme)
            }
            else -> null
        }
    }
}

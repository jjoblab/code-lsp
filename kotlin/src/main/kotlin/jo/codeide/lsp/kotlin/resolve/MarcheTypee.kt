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
     * ## La sélection POSITIONNELLE (vague 2b — fiche 046)
     *
     * Les candidats d'un nom (paramètres, locales, propriétés du document)
     * sont collectés en ordre de SOURCE ; la consultation À POSITION rend
     * le DERNIER déclaré AVANT la position — la locale ombreuse gagne
     * (fiche 046 : « le cast ne fuit pas »), le membre déclaré avant la
     * méthode reste atteignable. Sans position (ou sans candidat antérieur)
     * : le PREMIER candidat — la garde documentaire P1 de la fiche 017
     * (sur-résolution assumée, jamais faux).
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
        /** Un candidat de liaison (la fin de sa déclaration, son type écrit). */
        private class Candidat(internal val fin: Int, internal val type: String?)

        private val cache = HashMap<String, List<Candidat>>()
        private val enCours = HashSet<String>()

        /**
         * Le type écrit du nom lié (`null` si aucune liaison ne le porte).
         *
         * @param nom le nom consulté ;
         * @param position la position de l'USAGE (offset du récepteur) —
         * `-1` pour la consultation sans position (garde documentaire).
         */
        operator fun invoke(nom: String, position: Int = -1): String? {
            val candidats = candidatsDe(nom) ?: return null
            if (candidats.isEmpty()) return null
            if (position >= 0) {
                candidats.lastOrNull { it.fin <= position }?.let { return it.type }
            }
            return candidats.first().type
        }

        /** Les candidats du nom (collectés une fois, mémoïsés ; `null` en cycle). */
        private fun candidatsDe(nom: String): List<Candidat>? {
            if (cache.containsKey(nom)) return cache[nom]
            if (!enCours.add(nom)) return null
            val collectes = mutableListOf<Candidat>()
            if (region != null) {
                chercher(region, nom, locaux = true, collectes = collectes)
            }
            chercher(arbre.noeud, nom, locaux = false, collectes = collectes, hors = region)
            enCours.remove(nom)
            // Ordre de SOURCE : le dernier candidat avant la position est le
            // plus récent — l'ombre interne gagne (fiche 046).
            val tries = collectes.sortedBy { it.fin }
            cache[nom] = tries
            return tries
        }

        /**
         * Cherche les liaisons du nom dans un sous-arbre : paramètre de
         * valeur ou propriété (type écrit, sinon initialiseur typé par la
         * marche). Quand [locaux] est faux, les sous-arbres de FONCTION ne
         * sont pas descendus (les locales d'une fonction n'existent que dans
         * sa région). Les paramètres NUS du constructeur PRIMAIRE (sans
         * `val`/`var` — vague 2c, fiche 028) ne LIENT PAS : ils ne vivent
         * que dans la zone de construction, leur typage relève du modèle.
         */
        private fun chercher(
            noeud: NoeudArbre,
            nom: String,
            locaux: Boolean,
            collectes: MutableList<Candidat>,
            hors: NoeudArbre? = null,
            dansPrimaire: Boolean = false,
        ) {
            if (noeud === hors) return
            when (noeud.genre) {
                KtNodeTypes.VALUE_PARAMETER -> {
                    val n = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                    val nuDuPrimaire = dansPrimaire && noeud.jetonsPropres.none {
                        it.genre == KtTokens.VAL_KEYWORD || it.genre == KtTokens.VAR_KEYWORD
                    }
                    if (n == nom && !nuDuPrimaire) {
                        noeud.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                            ?.let { reference ->
                                nomTypeEcritDeReference(reference)?.let { collectes.add(Candidat(noeud.fin, it)) }
                            }
                    }
                }
                KtNodeTypes.PROPERTY -> {
                    val n = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                    if (n == nom) {
                        var typeNomme: String? = null
                        noeud.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                            ?.let { reference -> nomTypeEcritDeReference(reference)?.let { typeNomme = it } }
                        if (typeNomme == null) {
                            // Pas de type écrit : l'INITIALISEUR type la propriété
                            // (marche typée — `val q = p.copy(…)` en 2a, `val x =
                            // "une chaîne"` par LITTÉRAL en 2b — fiche 046).
                            val initialiseur = initialiseurDe(propriete = noeud)
                            if (initialiseur != null) {
                                when (val type = TypageBorne.typeDeLexpression(initialiseur, univers, this)) {
                                    is TypeValeur.Projet -> typeNomme = type.type.nomSimple
                                    is TypeValeur.Jvm -> typeNomme = type.fqcn
                                    null -> Unit
                                }
                            }
                        }
                        typeNomme?.let { collectes.add(Candidat(noeud.fin, it)) }
                    }
                }
                else -> Unit
            }
            if (noeud.genre != KtNodeTypes.FUN || locaux) {
                for (enfant in noeud.enfants) {
                    chercher(
                        enfant, nom, locaux, collectes, hors,
                        dansPrimaire || enfant.genre == KtNodeTypes.PRIMARY_CONSTRUCTOR,
                    )
                }
            }
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

        /**
         * Le nom ÉCRIT du type d'une référence — la NULLABILITÉ EST
         * CONSERVÉE (`String?` rend `String?` : c'est elle qui prouve
         * `kt.unsafeNullable`, fiche 046) et les génériques sont DÉPOUILLÉS
         * (`List<Int>?` rend `List?`) — les consommateurs tranchent
         * ([UniversPreuves.resoudreTypeEcrit] dépouille, la passe typée
         * regarde le `?`).
         */
        private fun nomTypeEcritDeReference(reference: NoeudArbre): String? {
            val nullable = reference.enfants.any { it.genre == KtNodeTypes.NULLABLE_TYPE }
            var porteur: NoeudArbre =
                reference.enfants.firstOrNull { it.genre == KtNodeTypes.NULLABLE_TYPE } ?: reference
            if (porteur.genre == KtNodeTypes.NULLABLE_TYPE) {
                porteur = porteur.enfants.firstOrNull { it.genre == KtNodeTypes.USER_TYPE } ?: return null
            }
            val utilisateur = porteur.enfants.firstOrNull { it.genre == KtNodeTypes.USER_TYPE }
                ?: porteur.takeIf { it.genre == KtNodeTypes.USER_TYPE }
                ?: return null
            val nom = utilisateur.texteCanonique.substringBefore('<')
            if (nom.isEmpty()) return null
            return if (nullable) "$nom?" else nom
        }
    }
}

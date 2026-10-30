// CodeIDE — reconstruction de l'arbre syntaxique depuis les marqueurs de la
// grammaire Kotlin officielle (module :kotlin, étage 1 vague 1a — noyau
// parse/session). Code NEUF jjoblab écrit pour code-lsp (clean-room : le code
// de l'ancien moteur codeide-lsp n'est pas consulté — le cahier
// docs/cahier-specifications-kotlin/ est le contrat, fiches 012/013).
package jo.codeide.lsp.kotlin.parse

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.prepareProduction
import org.jetbrains.kotlin.kmp.parser.AbstractParser
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Reconstruction de l'arbre syntaxique immuable depuis la liste plate des
 * marqueurs de production rendue par la grammaire vendorée (interne au
 * module — la surface publique est la façade `Kt*` du package
 * `jo.codeide.lsp.kotlin.parse.kt`).
 *
 * ## Encodage de la liste des marqueurs (sondé à l'étage 1, fiches 012/013)
 *
 * La liste `productionMarkers` de [prepareProduction] suit un encodage
 * équilibré en ordre du document, vérifié par sonde sur des sources valides
 * ET hostiles (troncatures) :
 *
 *  - chaque marqueur de production apparaît DEUX fois : une entrée
 *    d'OUVERTURE (`isDoneMarker == false`, `isErrorMarker == false`) puis une
 *    entrée de FERMETURE (`isDoneMarker == true`), les deux référençant le
 *    MÊME objet marqueur (identité vérifiée) — la fermeture suit toutes les
 *    entrées des enfants ;
 *  - les marqueurs d'erreur posés par `builder.error(message)` apparaissent
 *    en UNE seule entrée (`isDoneMarker == false`, `isErrorMarker == true`),
 *    de plage nulle : ce sont des FEUILLES rattachées au nœud ouvert courant ;
 *  - les nœuds `ERROR_ELEMENT` fermés proprement (paires ouverture/fermeture)
 *    portent leur message via `getErrorMessage()` — les fautes se lisent,
 *    jamais ne lèvent (contrat D3).
 *
 * La reconstruction suit donc une discipline de pile : ouverture → empilement,
 * fermeture → dépilation et rattachement au parent. Des chemins défensifs
 * (jamais observés — la grammaire rend toujours un arbre complet) garantissent
 * que [construire] ne lève JAMAIS : tout déséquilibre éventuel est résorbé
 * sans exception (tolérance d'édition, fiches 011/012).
 */
internal object ArbreSyntaxique {

    /**
     * Reconstruit l'arbre complet du source analysé par [constructeur].
     *
     * @param constructeur le constructeur d'arbre déjà parcouru par la
     * grammaire vendorée (le parseur a déjà fait `parse(constructeur)`) ;
     * @param parseur le parseur amont dont on lit les ensembles de blancs et
     * de commentaires (pour le calcul des jetons propres et du texte
     * canonique de chaque nœud) ;
     * @return la racine IMMUABLE de l'arbre reconstruit — jamais nulle : si
     * la liste des marqueurs était vide (jamais observé), une racine fichier
     * vide est fabriquée pour honorer le contrat de tolérance.
     */
    internal fun construire(constructeur: SyntaxTreeBuilder, parseur: AbstractParser): NoeudArbre {
        val productions = prepareProduction(constructeur).productionMarkers
        val jetons = constructeur.tokens

        val pile = ArrayDeque<NoeudEnConstruction>()
        var racine: NoeudEnConstruction? = null

        for (index in 0 until productions.size) {
            val marqueur = productions.getMarker(index)
            when {
                productions.isDoneMarker(index) -> {
                    // FERMETURE d'un marqueur ouvert : apparier au sommet de
                    // pile (par identité d'objet — l'encodage est équilibré).
                    val ferme = depilerJusqua(pile, marqueur)
                    if (ferme == null) continue
                    val parent = pile.lastOrNull()
                    if (parent == null) {
                        racine = ferme
                    } else {
                        parent.enfants.add(ferme)
                    }
                }
                marqueur.isErrorMarker() -> {
                    // Feuille d'erreur MONO-ENTRÉE (error(message) du
                    // constructeur) : rattachée au nœud ouvert courant.
                    val feuille = NoeudEnConstruction(
                        genre = marqueur.getNodeType(),
                        debut = marqueur.getStartOffset(),
                        fin = marqueur.getEndOffset(),
                        debutJetons = marqueur.getStartTokenIndex(),
                        finJetons = marqueur.getEndTokenIndex(),
                        messageErreur = marqueur.getErrorMessage(),
                    )
                    val parent = pile.lastOrNull()
                    if (parent == null) {
                        if (racine == null) racine = feuille
                    } else {
                        parent.enfants.add(feuille)
                    }
                }
                else -> {
                    // OUVERTURE d'un marqueur : empilement.
                    pile.addLast(
                        NoeudEnConstruction(
                            genre = marqueur.getNodeType(),
                            debut = marqueur.getStartOffset(),
                            fin = marqueur.getEndOffset(),
                            debutJetons = marqueur.getStartTokenIndex(),
                            finJetons = marqueur.getEndTokenIndex(),
                            messageErreur = marqueur.getErrorMessage(),
                        ),
                    )
                }
            }
        }

        // Chemin défensif (jamais observé) : marqueurs jamais fermés —
        // rattachés à la racine plutôt que perdus.
        var racineSure = racine
        while (pile.isNotEmpty()) {
            val orphelin = pile.removeLast()
            if (racineSure == null) {
                racineSure = orphelin
            } else {
                racineSure.enfants.add(orphelin)
            }
        }

        // Chemin défensif (jamais observé) : liste vide — racine fichier vide.
        if (racineSure == null) {
            racineSure = NoeudEnConstruction(
                genre = KtNodeTypes.KT_FILE,
                debut = 0,
                fin = 0,
                debutJetons = 0,
                finJetons = 0,
                messageErreur = null,
            )
        }

        val racineFige = racineSure.figer(
            jetons = jetons,
            blancs = parseur.whitespaces,
            commentaires = parseur.comments,
        )

        // Vague 1b (fiche 049) : les jetons de COMMENTAIRES du document sont
        // des trivia hors productions — l'arbre ne les porte pas. La racine
        // les collecte pour les services qui en ont besoin (KDoc du survol
        // S2, pliage des blocs de documentation).
        val commentairesDoc = mutableListOf<JetonBrut>()
        for (indice in 0 until jetons.tokenCount) {
            val genre = jetons.getTokenType(indice) ?: continue
            if (!parseur.comments.contains(genre)) continue
            val texte = jetons.getTokenText(indice) ?: continue
            commentairesDoc.add(
                JetonBrut(
                    genre = genre,
                    texte = texte.toString(),
                    debut = jetons.getTokenStart(indice),
                    fin = jetons.getTokenEnd(indice),
                ),
            )
        }
        return racineFige.avecCommentaires(commentairesDoc)
    }

    /**
     * Re-construit la racine en lui attachant la liste des commentaires du
     * document (copie immuable — seul le niveau racine les porte).
     *
     * @param commentaires les jetons de commentaire du document, en ordre du
     * document ;
     * @return la racine portant [NoeudArbre.commentaires].
     */
    private fun NoeudArbre.avecCommentaires(commentaires: List<JetonBrut>): NoeudArbre {
        return NoeudArbre(
            genre = genre,
            debut = debut,
            fin = fin,
            debutJetons = debutJetons,
            finJetons = finJetons,
            messageErreur = messageErreur,
            enfants = enfants,
            jetonsPropres = jetonsPropres,
            texteCanonique = texteCanonique,
            commentaires = commentaires,
        )
    }

    /**
     * Dépile jusqu'au marqueur fermé (identité d'objet). Les éventuels nœuds
     * ouverts intermédiaires (déséquilibre — jamais observé) sont rattachés
     * au nœud fermé plutôt que perdus.
     *
     * @param pile la pile des nœuds ouverts ;
     * @param ferme le marqueur dont on traite la fermeture ;
     * @return le nœud dépilé correspondant, ou `null` si aucun nœud ouvert ne
     * correspond (fermeture orpheline — ignorée, jamais observé).
     */
    private fun depilerJusqua(
        pile: ArrayDeque<NoeudEnConstruction>,
        ferme: SyntaxTreeBuilder.Production,
    ): NoeudEnConstruction? {
        val reponses = ArrayDeque<NoeudEnConstruction>()
        var trouve: NoeudEnConstruction? = null
        while (pile.isNotEmpty()) {
            val sommet = pile.removeLast()
            if (sommet.genre == ferme.getNodeType() &&
                sommet.debut == ferme.getStartOffset() &&
                sommet.debutJetons == ferme.getStartTokenIndex()
            ) {
                trouve = sommet
                break
            }
            reponses.addLast(sommet)
        }
        // Les intermédiaires (jamais observé) deviennent enfants du fermé.
        while (reponses.isNotEmpty()) {
            trouve?.enfants?.add(reponses.removeLast())
        }
        return trouve
    }
}

/**
 * Nœud en cours de reconstruction (phase mutable, interne à [ArbreSyntaxique]).
 *
 * @property genre le genre du nœud dans le vocabulaire du compilateur
 * (`KtNodeTypes`, vendored) ;
 * @property debut l'offset (inclus) de début du nœud dans le source ;
 * @property fin l'offset (exclu) de fin du nœud dans le source ;
 * @property debutJetons l'indice (inclus) du premier jeton du nœud ;
 * @property finJetons l'indice (exclu) du dernier jeton du nœud ;
 * @property messageErreur le message d'erreur porté par un nœud
 * `ERROR_ELEMENT` (null pour un nœud sain).
 */
private class NoeudEnConstruction(
    val genre: SyntaxElementType,
    val debut: Int,
    val fin: Int,
    val debutJetons: Int,
    val finJetons: Int,
    val messageErreur: String?,
) {
    val enfants: MutableList<NoeudEnConstruction> = mutableListOf()

    /**
     * Fige ce nœud et sa descendance en [NoeudArbre] immuable, en calculant
     * au passage les jetons propres et le texte canonique.
     *
     * @param jetons la séquence de jetons du lexeur (source de vérité des
     * positions) ;
     * @param blancs les genres de jetons considérés comme blancs ;
     * @param commentaires les genres de jetons considérés comme commentaires ;
     * @return le nœud immuable figé, avec ses enfants figés (ordre du
     * document).
     */
    fun figer(
        jetons: TokenList,
        blancs: Set<SyntaxElementType>,
        commentaires: Set<SyntaxElementType>,
    ): NoeudArbre {
        val enfantsFiges = enfants.map { it.figer(jetons, blancs, commentaires) }

        // Jetons PROPRES au nœud : indices de [debutJetons, finJetons) hors
        // des plages couvertes par les enfants (les enfants sont en ordre du
        // document), blancs et commentaires exclus.
        val jetonsPropres = mutableListOf<JetonBrut>()
        var indiceEnfant = 0
        for (i in debutJetons until finJetons) {
            while (indiceEnfant < enfantsFiges.size && enfantsFiges[indiceEnfant].finJetons <= i) {
                indiceEnfant++
            }
            val couvert = indiceEnfant < enfantsFiges.size && enfantsFiges[indiceEnfant].debutJetons <= i
            if (couvert) continue
            ajouterJetonSiSignificatif(jetons, i, blancs, commentaires, jetonsPropres)
        }

        // Texte canonique : TOUS les jetons de la plage (enfants compris),
        // blancs et commentaires exclus, dans l'ordre — c'est la forme écrite
        // sans trivia (noms qualifiés, noms d'annotations).
        val canonique = StringBuilder()
        for (i in debutJetons until finJetons) {
            val genre = jetons.getTokenType(i) ?: continue
            if (blancs.contains(genre) || commentaires.contains(genre)) continue
            canonique.append(jetons.getTokenText(i))
        }

        return NoeudArbre(
            genre = genre,
            debut = debut,
            fin = fin,
            debutJetons = debutJetons,
            finJetons = finJetons,
            messageErreur = messageErreur,
            enfants = enfantsFiges,
            jetonsPropres = jetonsPropres,
            texteCanonique = canonique.toString(),
        )
    }

    /**
     * Ajoute à [cible] le jeton d'indice [i] s'il est significatif (ni blanc
     * ni commentaire).
     */
    private fun ajouterJetonSiSignificatif(
        jetons: TokenList,
        i: Int,
        blancs: Set<SyntaxElementType>,
        commentaires: Set<SyntaxElementType>,
        cible: MutableList<JetonBrut>,
    ) {
        val genre = jetons.getTokenType(i) ?: return
        if (blancs.contains(genre) || commentaires.contains(genre)) return
        val texte = jetons.getTokenText(i) ?: return
        cible.add(
            JetonBrut(
                genre = genre,
                texte = texte.toString(),
                debut = jetons.getTokenStart(i),
                fin = jetons.getTokenEnd(i),
            ),
        )
    }
}

/**
 * Nœud immuable de l'arbre syntaxique reconstruit (interne au module — la
 * façade publique `Kt*` l'enveloppe).
 *
 * @property genre le genre du nœud dans le vocabulaire du compilateur
 * (identité d'objet avec les constantes `KtNodeTypes`/`KtTokens` vendored) ;
 * @property debut l'offset (inclus) de début du nœud dans le source ;
 * @property fin l'offset (exclu) de fin du nœud dans le source ;
 * @property debutJetons l'indice (inclus) du premier jeton du nœud ;
 * @property finJetons l'indice (exclu) du dernier jeton du nœud ;
 * @property messageErreur le message d'erreur porté par un nœud
 * `ERROR_ELEMENT` (null pour un nœud sain) — c'est la matière première des
 * diagnostics de syntaxe (fiche 049, étage 1) ;
 * @property enfants les enfants directs du nœud, en ordre du document ;
 * @property jetonsPropres les jetons significatifs situés DIRECTEMENT dans
 * la plage du nœud (hors plages des enfants), blancs et commentaires exclus ;
 * @property texteCanonique le texte écrit du nœud sans trivia (tous les
 * jetons de la plage, enfants compris, blancs et commentaires exclus) ;
 * @property commentaires les jetons de COMMENTAIRE du document — portés par
 * la RACINE uniquement (vague 1b : KDoc du survol, pliage), vide pour tout
 * autre nœud ; les commentaires sont des trivia hors productions.
 */
internal class NoeudArbre(
    internal val genre: SyntaxElementType,
    internal val debut: Int,
    internal val fin: Int,
    internal val debutJetons: Int,
    internal val finJetons: Int,
    internal val messageErreur: String?,
    internal val enfants: List<NoeudArbre>,
    internal val jetonsPropres: List<JetonBrut>,
    internal val texteCanonique: String,
    internal val commentaires: List<JetonBrut> = emptyList(),
)

/**
 * Un jeton significatif directement rattaché à un nœud (blancs et
 * commentaires exclus).
 *
 * @property genre le genre du jeton (identité d'objet avec les constantes
 * `KtTokens` vendored — par ex. `KtTokens.IDENTIFIER`) ;
 * @property texte le texte exact du jeton dans le source ;
 * @property debut l'offset (inclus) du début du jeton dans le source ;
 * @property fin l'offset (exclu) de la fin du jeton dans le source.
 */
internal class JetonBrut(
    internal val genre: SyntaxElementType,
    internal val texte: String,
    internal val debut: Int,
    internal val fin: Int,
)

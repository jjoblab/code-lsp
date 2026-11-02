// CodeIDE — complétion CONTEXTUELLE v1 (module :kotlin, étage 3 vague 3b —
// ADR 0113 : reprise en parité des trois contextes du moteur Java 1.16.0,
// ADR 0228 ancien) : arguments NOMMÉS d'un appel résolu, CONSTANTES d'enum
// des branches d'un `when` à sujet typé, ATTRIBUTS d'une annotation
// résolue — chaque contexte porte sa borne conservatrice D3 (position de
// valeur muette, candidat inconnu : repli honnête sur le régime de nom).
// Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import com.intellij.platform.syntax.SyntaxElementType
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.GenreTypeProjet
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.PileThis
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le résultat d'un contexte contextuel : le genre classifié et les
 * propositions EXCLUSIVES du contexte (aucun mot-clé ne suit — le contexte
 * est prouvé, il remplace le régime de nom).
 *
 * @property genre le genre du contexte ;
 * @property propositions les propositions du contexte.
 */
internal class ResultatContextuel internal constructor(
    internal val genre: GenreContexteKotlin,
    internal val propositions: List<PropositionKotlin>,
)

/**
 * La complétion contextuelle v1 (fiche 043 — ADR 0113/0228) : trois
 * contextes, chacun PROUVÉ avant d'être servi — la classification se fait
 * sur l'arbre ET la résolution (l'appel se résout, le sujet du `when` se
 * type, l'annotation se résout) ; un contexte non prouvable rend `null` et
 * le pipeline retombe sur le régime de NOM (repli honnête, jamais une
 * invention).
 *
 * Les positions de VALEUR (après `=` d'un argument nommé, dans un littéral
 * en cours de frappe) ne sont PAS des positions de nom : elles rendent
 * `null` (le régime de nom reprend — mots-clés visibles).
 */
internal object CompletionContextuelle {

    /**
     * Les ARGUMENTS NOMMÉS d'un appel résolu (fiche 043) : les paramètres
     * des surcharges du projet non encore fournis en forme nommée, à
     * insertion avec séparateur (`nom = `), le détail portant la signature
     * écrite de l'appelable.
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison de la région porteuse ;
     * @param pile la pile des this au caret ;
     * @param deplacement l'offset du caret ;
     * @return le contexte et ses propositions ; `null` hors d'une position
     * de nom d'argument d'un appel résolu.
     */
    internal fun argumentsNommes(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
        deplacement: Int,
    ): ResultatContextuel? {
        // La liste d'arguments d'APPEL couvrant le caret (la plus profonde).
        val (liste, appel) = listeArgumentsCouvrante(arbre.noeud, deplacement, KtNodeTypes.CALL_EXPRESSION)
            ?: return null
        // Position de NOM : le caret n'est pas dans le slot de VALEUR d'un
        // argument nommé (après son `=`).
        if (!estPositionDeNom(liste, deplacement)) return null

        val fournis = nomsFournis(liste)
        val prefixe = CompletionContextes.prefixeAuPoint(arbre.noeud, deplacement)

        // L'appel NU : les surcharges du projet du même nom (le document
        // courant et le scan du même package — l'union des paramètres).
        val nomNu = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique.orEmpty()
        val propositions = mutableListOf<PropositionKotlin>()
        if (nomNu.isNotEmpty()) {
            for (appelable in univers.modele.appelables(nomNu, univers.paquet)) {
                val detail = signatureEcrit(
                    nomNu,
                    appelable.parametres.mapNotNull { parametre ->
                        parametre.nom?.let { it to parametre.typeEcrit }
                    },
                )
                for (parametre in appelable.parametres) {
                    val nom = parametre.nom ?: continue
                    if (nom in fournis) continue
                    if (prefixe.isNotEmpty() && !nom.startsWith(prefixe)) continue
                    propositions.add(
                        PropositionKotlin(
                            libelle = "$nom =",
                            nature = NaturePropositionKotlin.PARAMETRE,
                            detail = detail,
                            texteInsertion = "$nom = ",
                        ),
                    )
                }
            }
            if (propositions.isNotEmpty()) {
                return ResultatContextuel(GenreContexteKotlin.ARGUMENTS_NOMMES, propositions)
            }
            return null
        }

        // L'appel MEMBRE QUALIFIÉ : le récepteur se type, le membre se lit
        // dans SES déclarations (les paramètres du MEMBRE, pas de la
        // clôture entière).
        val membre = membreAppele(arbre, appel, univers, liaison, pile, deplacement) ?: return null
        val detail = signatureEcrit(
            membre.first,
            membre.second.mapNotNull { parametre ->
                parametre.nom?.let { it to parametre.typeEcrit }
            },
        )
        for (parametre in membre.second) {
            val nom = parametre.nom ?: continue
            if (nom in fournis) continue
            if (prefixe.isNotEmpty() && !nom.startsWith(prefixe)) continue
            propositions.add(
                PropositionKotlin(
                    libelle = "$nom =",
                    nature = NaturePropositionKotlin.PARAMETRE,
                    detail = detail,
                    texteInsertion = "$nom = ",
                ),
            )
        }
        if (propositions.isEmpty()) return null
        return ResultatContextuel(GenreContexteKotlin.ARGUMENTS_NOMMES, propositions)
    }

    /**
     * Les CONSTANTES d'enum des branches d'un `when` à sujet ENUM TYPÉ
     * (fiche 043) : le contexte est EXCLUSIF — les entrées de l'enum et
     * RIEN d'autre (aucun mot-clé) ; un sujet non enum rend `null` (repli
     * honnête sur le régime de nom).
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison de la région porteuse ;
     * @param pile la pile des this au caret ;
     * @param deplacement l'offset du caret ;
     * @return le contexte et ses propositions ; `null` hors d'une position
     * de condition d'un `when` à sujet enum.
     */
    internal fun constantesEnum(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
        deplacement: Int,
    ): ResultatContextuel? {
        // Le WHEN couvrant le caret, en ZONE DE CONDITION (après le sujet,
        // hors des CORPS de branches).
        val quand = quandCouvrant(arbre.noeud, deplacement) ?: return null
        val sujet = sujetDuQuand(quand) ?: return null
        val typeSujet = TypageBorne.typeDeLexpression(sujet, univers, liaison, pile) ?: return null

        val prefixe = CompletionContextes.prefixeAuPoint(arbre.noeud, deplacement)
        val propositions = mutableListOf<PropositionKotlin>()
        when (typeSujet) {
            is TypeValeur.Projet -> {
                if (typeSujet.type.genre != GenreTypeProjet.ENUM) return null
                for (entree in typeSujet.type.entreesEnum) {
                    if (prefixe.isNotEmpty() && !entree.startsWith(prefixe)) continue
                    propositions.add(
                        PropositionKotlin(
                            libelle = entree,
                            nature = NaturePropositionKotlin.ENUM,
                            detail = typeSujet.type.nomSimple,
                        ),
                    )
                }
            }
            is TypeValeur.Jvm -> {
                // Les constantes d'enum BINAIRES : la superclasse du type
                // est `java.lang.Enum` (le marqueur binaire d'un enum —
                // l'INSTANCE d'un singleton typé par lui-même n'y entre
                // pas) ; les constantes sont les champs STATIQUES typés
                // par l'enum ELLE-MÊME (`Ldemo/Couleur;`) — `name`/
                // `ordinal` (String/Int) n'y entrent pas.
                val declares = univers.vueJvm?.membresDeclares(univers.mapeJvm(typeSujet.fqcn))
                    ?: return null
                if (declares.superFqcn != "java.lang.Enum") return null
                val descripteurEnum = "L${typeSujet.fqcn.replace('.', '/')};"
                for (membre in declares.membres) {
                    if (!membre.estChamp) continue
                    if (membre.descripteur != descripteurEnum) continue
                    if (prefixe.isNotEmpty() && !membre.nom.startsWith(prefixe)) continue
                    propositions.add(
                        PropositionKotlin(
                            libelle = membre.nom,
                            nature = NaturePropositionKotlin.ENUM,
                            detail = typeSujet.fqcn.substringAfterLast('.'),
                        ),
                    )
                }
            }
        }
        if (propositions.isEmpty()) return null
        return ResultatContextuel(GenreContexteKotlin.CONSTANTES_ENUM, propositions)
    }

    /**
     * Les ATTRIBUTS d'une annotation RÉSOLUE (fiche 043) : les attributs
     * non encore fournis (forme nommée), à insertion avec séparateur
     * (`level = `), le détail portant le FQCN de l'annotation — le contexte
     * est EXCLUSIF.
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param deplacement l'offset du caret ;
     * @return le contexte et ses propositions ; `null` hors d'une position
     * de nom d'argument d'une annotation résolue.
     */
    internal fun attributsAnnotation(
        arbre: KtFichier,
        univers: UniversPreuves,
        deplacement: Int,
    ): ResultatContextuel? {
        val (liste, entree) = listeArgumentsCouvrante(arbre.noeud, deplacement, KtNodeTypes.ANNOTATION_ENTRY)
            ?: return null
        if (!estPositionDeNom(liste, deplacement)) return null

        // Le nom ÉCRIT de l'annotation (CONSTRUCTOR_CALLEE → référence de
        // type) se résout en classe du classpath.
        val nomEcrit = entree.enfants
            .firstOrNull { it.genre == KtNodeTypes.CONSTRUCTOR_CALLEE }
            ?.enfants?.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
            ?.texteCanonique.orEmpty()
        if (nomEcrit.isEmpty()) return null
        val type = univers.resoudreTypeEcrit(nomEcrit) as? TypeValeur.Jvm ?: return null
        val declares = univers.vueJvm?.membresDeclares(univers.mapeJvm(type.fqcn)) ?: return null

        // Les attributs d'annotation BINAIRES : les méthodes ABSTRAITES de
        // la classe (0x0401 — les accesseurs des attributs).
        val fournis = nomsFournis(liste)
        val prefixe = CompletionContextes.prefixeAuPoint(arbre.noeud, deplacement)
        val propositions = mutableListOf<PropositionKotlin>()
        for (membre in declares.membres) {
            if (membre.estChamp || !membre.estAbstrait) continue
            if (membre.nom in fournis) continue
            if (prefixe.isNotEmpty() && !membre.nom.startsWith(prefixe)) continue
            propositions.add(
                PropositionKotlin(
                    libelle = "${membre.nom} =",
                    nature = NaturePropositionKotlin.PROPRIETE,
                    detail = type.fqcn,
                    texteInsertion = "${membre.nom} = ",
                ),
            )
        }
        if (propositions.isEmpty()) return null
        return ResultatContextuel(GenreContexteKotlin.ATTRIBUTS_ANNOTATION, propositions)
    }

    // ------------------------------------------------------------------
    // Structures (les formes de la grammaire officielle).
    // ------------------------------------------------------------------

    /**
     * La plus profonde LISTE D'ARGUMENTS couvrant le caret dont le parent
     * porte le genre attendu (appel ou entrée d'annotation) — le PORTEUR
     * voyage avec la liste (l'arbre ne porte pas de pointeur parent).
     *
     * @param racine la racine de l'arbre ;
     * @param deplacement l'offset du caret ;
     * @param genreParent le genre du porteur (CALL_EXPRESSION ou
     * ANNOTATION_ENTRY) ;
     * @return la liste d'arguments et son porteur ; `null` si aucune ne
     * couvre.
     */
    private fun listeArgumentsCouvrante(
        racine: NoeudArbre,
        deplacement: Int,
        genreParent: SyntaxElementType,
    ): Pair<NoeudArbre, NoeudArbre>? {
        var trouve: Pair<NoeudArbre, NoeudArbre>? = null
        fun marcher(noeud: NoeudArbre, parent: NoeudArbre?) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.VALUE_ARGUMENT_LIST && parent?.genre == genreParent) {
                trouve = noeud to parent
            }
            noeud.enfants.forEach { marcher(it, noeud) }
        }
        marcher(racine, null)
        return trouve
    }

    /**
     * Le caret est-il en POSITION DE NOM dans la liste d'arguments : il ne
     * vit ni dans le SLOT DE VALEUR d'un argument nommé (après son `=` —
     * la valeur s'y frappe, pas un nom), ni DANS une expression de valeur
     * (un littéral en cours de frappe couvre le caret), ni dans le slot de
     * valeur d'un argument nommé TRONQUÉ (nom écrit, valeur absente — le
     * caret le suit immédiatement).
     *
     * @param liste la liste d'arguments ;
     * @param deplacement l'offset du caret ;
     * @return `true` en position de nom.
     */
    private fun estPositionDeNom(liste: NoeudArbre, deplacement: Int): Boolean {
        for (argument in liste.enfants) {
            if (argument.genre != KtNodeTypes.VALUE_ARGUMENT) continue
            if (deplacement < argument.debut || deplacement > argument.fin) continue
            // L'argument COUVRANT le caret.
            val nomEcrit = argument.enfants.firstOrNull {
                it.genre == KtNodeTypes.VALUE_ARGUMENT_NAME
            }
            if (nomEcrit != null && deplacement > nomEcrit.fin) return false
            val valeur = argument.enfants.lastOrNull {
                it.genre != KtNodeTypes.VALUE_ARGUMENT_NAME
            }
            if (valeur != null && valeur.genre != KtNodeTypes.REFERENCE_EXPRESSION &&
                deplacement > valeur.debut && deplacement <= valeur.fin
            ) {
                return false
            }
        }
        // Le caret ENTRE les arguments : s'il suit IMMÉDIATEMENT un
        // argument nommé TRONQUÉ (nom écrit sans valeur — les ERROR_ELEMENT
        // de plage nulle de la récupération d'erreur ne comptent pas comme
        // valeur), il vit dans son slot de valeur.
        val precedent = liste.enfants.lastOrNull {
            it.genre == KtNodeTypes.VALUE_ARGUMENT && it.fin <= deplacement
        }
        if (precedent != null) {
            val nomEcrit = precedent.enfants.firstOrNull {
                it.genre == KtNodeTypes.VALUE_ARGUMENT_NAME
            }
            val valeur = precedent.enfants.lastOrNull {
                it.genre != KtNodeTypes.VALUE_ARGUMENT_NAME && it.fin > it.debut
            }
            if (nomEcrit != null && valeur == null) return false
        }
        return true
    }

    /** Les noms DÉJÀ FOURNIS en forme nommée dans la liste. */
    private fun nomsFournis(liste: NoeudArbre): Set<String> {
        val fournis = mutableSetOf<String>()
        for (argument in liste.enfants) {
            if (argument.genre != KtNodeTypes.VALUE_ARGUMENT) continue
            // Le nom écrit vit dans la production enfant VALUE_ARGUMENT_NAME
            // (sa forme canonique — l'identifiant s'y porte par référence).
            argument.enfants
                .firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_NAME }
                ?.texteCanonique
                ?.takeIf { it.isNotEmpty() }
                ?.let { fournis.add(it) }
        }
        return fournis
    }

    /**
     * Le MEMBRE APPELÉ d'un appel QUALIFIÉ (`m.copier(`) : le récepteur se
     * type, le membre se lit dans les DÉCLARATIONS du type (ses paramètres
     * y vivent — un membre hérité du scan sans paramètres relus rend
     * `null`, D3).
     *
     * @return le nom du membre et ses paramètres.
     */
    private fun membreAppele(
        arbre: KtFichier,
        appel: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
        deplacement: Int,
    ): Pair<String, List<jo.codeide.lsp.kotlin.resolve.ParametreProjet>>? {
        val nomMembre = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique.orEmpty()
        if (nomMembre.isEmpty()) return null
        // Le récepteur : le DOT PORTEUR de l'appel (le nœud JUSTE AVANT
        // l'appel dans la chaîne des ancêtres — la chaîne descend de la
        // racine au plus profond, le parent précède).
        val chaine = chaineAncetres(arbre.noeud, appel.debut)
        val dot = chaine.getOrNull((chaine.indexOf(appel) - 1).coerceAtLeast(0)) ?: return null
        if (dot.genre != KtNodeTypes.DOT_QUALIFIED_EXPRESSION &&
            dot.genre != KtNodeTypes.SAFE_ACCESS_EXPRESSION
        ) {
            return null
        }
        val recepteur = dot.enfants.firstOrNull() ?: return null
        val typeRecepteur = TypageBorne.typeDeLexpression(recepteur, univers, liaison, pile)
            ?: return null
        if (typeRecepteur !is TypeValeur.Projet) return null
        val membre = typeRecepteur.type.membres.firstOrNull { it.nom == nomMembre } ?: return null
        return Pair(membre.nom, membre.parametres)
    }

    /**
     * Le `when` couvrant le caret, en ZONE DE CONDITION : après la fin du
     * SUJET (hors des parenthèses du sujet) et hors des CORPS de branches
     * (après la flèche `->`).
     *
     * @param racine la racine de l'arbre ;
     * @param deplacement l'offset du caret ;
     * @return le nœud `when` ; `null` hors de sa zone de condition.
     */
    private fun quandCouvrant(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.WHEN) {
                val sujet = sujetDuQuand(noeud)
                if (sujet == null || deplacement > sujet.fin) trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        val quand = trouve ?: return null
        // Le caret vit-il dans le CORPS d'une branche (après la flèche) ?
        for (entree in quand.enfants) {
            if (entree.genre != KtNodeTypes.WHEN_ENTRY) continue
            if (deplacement < entree.debut || deplacement > entree.fin) continue
            val fleche = entree.jetonsPropres.firstOrNull { it.genre == KtTokens.ARROW }
            if (fleche != null && deplacement > fleche.debut) return null
        }
        return quand
    }

    /**
     * Le SUJET d'un `when` : la première production enfant qui n'est ni une
     * entrée ni un jeton (l'expression entre les parenthèses).
     */
    private fun sujetDuQuand(quand: NoeudArbre): NoeudArbre? =
        quand.enfants.firstOrNull {
            it.genre != KtNodeTypes.WHEN_ENTRY
        }

    /** La signature ÉCRITE d'un appelable (`creer(nom: String, age: Int)`) — les paramètres SANS nom lus sont écartés (signatures de sources décompilées). */
    private fun signatureEcrit(nom: String, parametres: List<Pair<String, String?>>): String {
        val rendus = parametres.map { (nomParametre, type) ->
            if (type == null) nomParametre else "$nomParametre: $type"
        }
        return "$nom(${rendus.joinToString(", ")})"
    }

    /** La chaîne des ANCÊTRES d'un nœud (de la racine vers le nœud). */
    private fun chaineAncetres(racine: NoeudArbre, position: Int): List<NoeudArbre> {
        val chaine = mutableListOf<NoeudArbre>()
        var courant: NoeudArbre? = racine
        while (courant != null && position >= courant.debut && position <= courant.fin) {
            chaine.add(courant)
            courant = courant.enfants.firstOrNull { position >= it.debut && position <= it.fin }
        }
        return chaine
    }
}

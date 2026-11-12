// CodeIDE — complétion SMART (module :kotlin, vague V-D — KC-01/KC-02/
// KC-04/KC-05, ADR 0126) : le TYPE ATTENDU démontable au caret ordonne le
// régime de NOM (le tri ne cache jamais — KC-01 T6), les membres OUVRABLES
// des supertypes s'offrent après `override fun|val|var ` (KC-05), les
// EXTENSIONS APPLICABLES du récepteur typé atteignant le type attendu se
// proposent en chaînes de profondeur ≤ 2 (KC-02), et les constantes DÉJÀ
// CITÉES d'un `when` se déclassent (KC-04 W1). Code NEUF jjoblab
// (clean-room — conservateur par construction : chaque preuve se décide
// sur l'arbre + la résolution, une position sans preuve ne réordonne RIEN).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.GenreMembreHerite
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.PileThis
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le résultat du contexte OVERRIDE (KC-05) : les propositions et les
 * libellés DÉCLASSÉS (les membres OUVERTS derrière les ABSTRAITS, O1).
 *
 * @property genre le genre du contexte (OVERRIDE) ;
 * @property propositions les membres ouvrables (abstraits + ouverts) ;
 * @property ouverts les libellés des membres OUVERTS (déclassés en fin
 * de liste par le contexte de classement — jamais cachés).
 */
internal class ResultatOverride internal constructor(
    internal val genre: GenreContexteKotlin,
    internal val propositions: List<PropositionKotlin>,
    internal val ouverts: Set<String>,
)

/**
 * La complétion smart V-D (ADR 0126) — trois preuves indépendantes, chacune
 * CONSERVATRICE (D3 : ce qui ne se décide pas rend `null`/vide, jamais une
 * invention) :
 *
 * - **le TYPE ATTENDU au caret** (KC-01) : argument d'appel typé, `return`
 *   de fonction typée, initialisateur de propriété typée, branche
 *   d'`if`/`when` expression typée par ses branches complètes — rendu au
 *   pipeline pour le contexte de CLASSEMENT (le peseur `typeAttendu`
 *   ordonne, T6 : sans preuve, la liste reste dans l'ordre standard) ;
 * - **les membres OUVRABLES** (KC-05) : après `override fun|val|var ` dans
 *   le corps d'une classe qui DÉCLARE des supertypes — les membres
 *   ABSTRAITS ou `open` des supertypes PROJET et CLASSPATH non déjà
 *   surchargés, à insertion de la SIGNATURE COMPLÈTE (corps `= TODO(nom)`,
 *   miroir de [MembresManquants]) ;
 * - **les CHAÎNES D'EXTENSIONS** (KC-02) : les extensions applicables du
 *   récepteur TYPÉ d'un sélecteur, retournant le TYPE ATTENDU, chaînes de
 *   profondeur ≤ 2 (chaque maillon démontrable — un maillon manquant
 *   n'offre AUCUNE chaîne) ;
 * - **le DÉCLASSEMENT des constantes citées** (KC-04 W1) : les entrées
 *   d'un `when` déjà couvertes descendent en fin de liste — elles
 *   restent proposées.
 */
internal object CompletionSmart {

    /**
     * Les types ATTENDUS démontables au caret (KC-01 T1–T4) : les noms
     * ÉCRITS (simples ou FQCN) du type que la position exige.
     *
     * Les preuves, dans l'ordre de la fiche :
     *  - **T1** argument d'appel : le type du paramètre à la place de
     *    l'argument couvrant le caret (appelable du PROJET lu du modèle —
     *    surcharges comprises) ;
     *  - **T2** `return` : le type de retour ÉCRIT de la fonction
     *    englobante ;
     *  - **T3** initialisateur : le type ÉCRIT de la propriété dont le
     *    jeton `=` s'achève juste avant le caret (l'initialisateur vide —
     *    le nœud PROPERTY s'arrête après le `=` ; le caret suit le `=`) ;
     *  - **T4** branche d'`if`/`when` expression : le type de la PREMIÈRE
     *    branche complète (les branches complètes lisibles typent
     *    l'attente — l'inférence complète relève du compilateur, la
     *    première lisible est la preuve honnête du palier).
     *
     * La preuve T5 (argument de lambda typé) vit déjà dans l'attente des
     * lambdas (fiche 046) : le paramètre `it`/explicite y est TYPÉ, ses
     * propositions portent le type en détail — le peseur y lit l'attente
     * par le même champ.
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison de la région porteuse ;
     * @param deplacement l'offset du caret ;
     * @return les noms écrits des types attendus ; VIDE sans preuve
     * (le tri ne s'applique alors jamais, T6).
     */
    internal fun typesAttendus(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        deplacement: Int,
    ): Set<String> {
        val rendus = mutableSetOf<String>()

        // T1 — l'argument d'appel typé : la liste d'arguments couvrant le
        // caret, la place de l'argument, le paramètre correspondant des
        // surcharges du projet du même nom.
        attenteArgument(arbre, univers, liaison, deplacement)?.let { rendus.addAll(it) }

        // T2 — le `return` de la fonction typée : la fonction englobante
        // la plus profonde porte un type de retour écrit.
        attenteRetour(arbre, deplacement)?.let { rendus.add(it) }

        // T3 — l'initialisateur de propriété typée : la propriété dont le
        // jeton `=` s'achève au caret (l'initialiseur vide suit le `=`).
        attentePropriete(arbre, deplacement)?.let { rendus.add(it) }

        // T4 — la branche d'`if`/`when` expression : la première branche
        // complète LISIBLE type l'attente.
        attenteBranche(arbre, univers, liaison, deplacement)?.let { rendus.add(it) }

        return rendus.filter { it.isNotEmpty() }.toSet()
    }

    /**
     * Les libellés DÉCLASSÉS (KC-04 W1) : les constantes d'enum DÉJÀ
     * CITÉES dans les conditions des branches du `when` couvrant le caret
     * — elles restent proposées (le contexte CONSTANTES_ENUM est exclusif)
     * mais descendent derrière les non citées.
     *
     * @param arbre l'arbre du document ;
     * @param deplacement l'offset du caret (zone de condition du `when`) ;
     * @return les noms cités ; vide hors d'un `when` à sujet enum.
     */
    internal fun citeesDuQuand(arbre: KtFichier, deplacement: Int): Set<String> {
        val quand = quandCouvrant(arbre.noeud, deplacement) ?: return emptySet()
        val citees = mutableSetOf<String>()
        for (entree in quand.enfants.filter { it.genre == KtNodeTypes.WHEN_ENTRY }) {
            if (entree.jetonsPropres.any { it.genre == KtTokens.ELSE_KEYWORD }) continue
            for (condition in entree.enfants) {
                if (condition.genre != KtNodeTypes.WHEN_CONDITION_EXPRESSION) continue
                // Le DERNIER segment dénomme l'entrée (miroir V-C).
                citees.add(condition.texteCanonique.substringAfterLast('.').trim())
            }
        }
        citees.remove("")
        return citees
    }

    // ------------------------------------------------------------------
    // KC-05 — les membres ouvrables après `override fun|val|var `.
    // ------------------------------------------------------------------

    /**
     * Les membres OUVRABLES des supertypes de la classe porteuse (KC-05
     * O1) : le caret vit juste après `override fun `/`override val `/`
     * `override var ` dans le CORPS d'une classe qui DÉCLARE des
     * supertypes.
     *
     * Les ouvrables : les membres ABSTRAITS d'abord, les membres `open`
     * ensuite — les membres `final` (concrets sans `open`) et les membres
     * DÉJÀ SURCHARGÉS (par nom) n'entrent jamais (O1) ; un membre non
     * LISIBLE du supertype classpath n'entre pas non plus (O5 : preuve ou
     * silence).
     *
     * L'INSERTION (O2/O3) porte la signature COMPLÈTE (`override fun
     * mesurer(): Boolean = TODO("mesurer")` — miroir de
     * [MembresManquants.stubProjet]) : le libellé reste le nom NU (le
     * re-filtrage local par frappe est la frontière RPC), le texte
     * d'insertion porte la signature.
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison de la région porteuse ;
     * @param deplacement l'offset du caret ;
     * @return le contexte OVERRIDE (propositions + libellés déclassés des
     * OUVERTS) ; `null` hors d'une position `override` prouvée.
     */
    internal fun overrideAuPoint(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        deplacement: Int,
    ): ResultatOverride? {
        // La déclaration `override` EN COURS : le modificateur est écrit
        // dans la fenêtre du caret — soit la DÉCLARATION couvre le caret
        // (le parseur rend `override fun x|` en FUN complet), soit la
        // déclaration S'ARRÊTE juste avant (le nœud de récupération
        // `override fun |` s'arrête après le mot-clé — le caret suit aux
        // blancs près).
        val declaration = declarationOverrideCouvrante(arbre.noeud, deplacement) ?: return null
        val classe = classePorteuse(arbre.noeud, declaration.debut) ?: return null
        val clauses = clausesSupertypes(classe)
        if (clauses.isEmpty()) return null

        // Les membres déjà surchargés (par nom) dans le corps de la classe.
        val deja = membresDeclares(classe).toSet()

        val abstraits = mutableListOf<PropositionKotlin>()
        val ouverts = mutableSetOf<String>()
        val propositionsOuvertes = mutableListOf<PropositionKotlin>()
        for (clause in clauses) {
            val supertype = univers.supertypeResolu(clause) ?: continue
            when (supertype) {
                is TypeValeur.Projet -> {
                    for (membre in supertype.type.membres) {
                        if (membre.genre == jo.codeide.lsp.kotlin.resolve.GenreMembreProjet.TYPE_IMBRIQUE) continue
                        if (membre.nom in deja) continue
                        // V-D — l'ABSTRAIT se lit sur l'ARBRE (un `fun` SANS
                        // bloc ; le prédicat modèle `estAbstrait` blanchit
                        // BLOCK par erreur latente — jamais compté ici) ;
                        // un membre CONCRET d'INTERFACE est ouvrable par
                        // nature, un membre de CLASSE ne l'est que si SA
                        // déclaration porte `open` (O1).
                        if (estAbstraitDeclaration(univers, supertype.type, membre.nom)) {
                            abstraits.add(propositionOverrideProjet(membre, supertype.type.nomSimple))
                        } else if (estOuvert(univers, supertype.type, membre.nom)) {
                            ouverts.add(membre.nom)
                            propositionsOuvertes.add(propositionOverrideProjet(membre, supertype.type.nomSimple))
                        }
                    }
                }
                is TypeValeur.Jvm -> {
                    val vue = univers.vueJvm ?: continue
                    val declares = vue.membresDeclares(univers.mapeJvm(supertype.fqcn)) ?: continue
                    for (membre in declares.membres) {
                        if (membre.estChamp) continue
                        if (membre.nom in deja) continue
                        if (membre.estAbstrait) {
                            abstraits.add(propositionOverrideJvm(membre, supertype.fqcn.substringAfterLast('.')))
                        } else if (estOuvertJvm(declares.accesClasse, membre)) {
                            ouverts.add(membre.nom)
                            propositionsOuvertes.add(propositionOverrideJvm(membre, supertype.fqcn.substringAfterLast('.')))
                        }
                    }
                }
            }
        }
        if (abstraits.isEmpty() && propositionsOuvertes.isEmpty()) return null
        // E5 — les candidats du projet AVANT le classpath, puis ordre
        // alphabétique stable ; le peseur déclasse les OUVERTS derrière
        // les ABSTRAITS (O1 — le pipeline porte `ouverts` au contexte de
        // classement).
        val propositions = (abstraits + propositionsOuvertes).sortedBy { it.libelle }
        return ResultatOverride(GenreContexteKotlin.OVERRIDE, propositions, ouverts)
    }

    // ------------------------------------------------------------------
    // KC-02 — les chaînes d'extensions applicables.
    // ------------------------------------------------------------------

    /**
     * Les CHAÎNES D'EXTENSIONS applicables (KC-02 E1/E2) : au point d'un
     * sélecteur `recepteur.|` dont le récepteur est TYPÉ, les extensions
     * (projet + classpath) applicables au récepteur qui atteignent le
     * TYPE ATTENDU de la position — les chaînes directes (profondeur 1)
     * d'abord, puis les chaînes de profondeur 2 (deux maillons
     * démontrables, proposées comme UN SEUL item).
     *
     * La passe se TAIT sans preuve (E4) : récepteur non typable, position
     * sans type attendu, ou aucun maillon — aucune spéculation.
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison de la région porteuse ;
     * @param pile la pile des this au caret ;
     * @param deplacement l'offset du caret (juste après le point) ;
     * @param typesAttendus les types attendus démontables de la position ;
     * @return les propositions de chaînes ; VIDE sans preuve complète.
     */
    internal fun chainesExtensions(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
        deplacement: Int,
        typesAttendus: Set<String>,
    ): List<PropositionKotlin> {
        if (typesAttendus.isEmpty()) return emptyList()
        val attendus = typesAttendus.map { it.substringAfterLast('.') }.toSet()

        // Le récepteur du sélecteur couvrant le caret, TYPÉ.
        val qualifiee = recepteurQualifie(arbre.noeud, deplacement) ?: return emptyList()
        val recepteur = qualifiee.enfants.firstOrNull() ?: return emptyList()
        val typeRecepteur = TypageBorne.typeDeLexpression(recepteur, univers, liaison, pile)
            ?: return emptyList()
        val fqcnRecepteur = when (typeRecepteur) {
            is TypeValeur.Projet -> typeRecepteur.type.fqcn
            is TypeValeur.Jvm -> typeRecepteur.fqcn
        }

        val propositions = mutableListOf<PropositionKotlin>()
        // E5 — le projet avant le classpath, puis l'ordre alphabétique
        // stable (les sources parcourent projet puis classpath ; le tri
        // final départage).
        val extensions = extensionsApplicables(univers, fqcnRecepteur)

        // Profondeur 1 — l'extension retourne le type attendu.
        for (extension in extensions) {
            val retour = extension.second ?: continue
            if (retour.substringAfterLast('.') in attendus) {
                propositions.add(
                    PropositionKotlin(
                        libelle = extension.first,
                        nature = NaturePropositionKotlin.FONCTION,
                        detail = retour,
                    ),
                )
            }
        }

        // Profondeur 2 — un maillon vers un type intermédiaire, puis un
        // maillon de l'intermédiaire vers l'attendu (chaîne UN SEUL item).
        val dejaDirectes = propositions.map { it.libelle }.toSet()
        for (premier in extensions) {
            val intermediaire = premier.second ?: continue
            if (intermediaire.substringAfterLast('.') in attendus) continue
            val secondes = extensionsApplicables(univers, intermediaire)
            for (seconde in secondes) {
                if (seconde.first in dejaDirectes && seconde.second == intermediaire) continue
                val retour = seconde.second ?: continue
                if (retour.substringAfterLast('.') !in attendus) continue
                propositions.add(
                    PropositionKotlin(
                        libelle = "${premier.first}().${seconde.first}()",
                        nature = NaturePropositionKotlin.FONCTION,
                        detail = "chaîne : ${premier.first}(): $intermediaire → ${retour.substringAfterLast('.')}",
                    ),
                )
            }
        }

        // E3 — le plafond s'applique au pipeline (le total réel voyage
        // avec la réponse) ; ici la passe se borne elle-même : les
        // chaînes directes dominent, les profondeurs 2 ferment la marche.
        return propositions.sortedBy { it.libelle }
    }

    // ------------------------------------------------------------------
    // Structures et aides (miroirs des passes voisines).
    // ------------------------------------------------------------------

    /** T1 — l'attente de la place d'argument de l'appel couvrant le caret. */
    private fun attenteArgument(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        deplacement: Int,
    ): Set<String>? {
        val (liste, appel) = listeArgumentsCouvrante(arbre.noeud, deplacement) ?: return null
        if (!estPositionDArgument(liste, deplacement)) return null
        val nomNu = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique.orEmpty()
        if (nomNu.isEmpty()) return null

        // La place de l'argument : les arguments AVANT le caret, hors
        // nommés (un argument nommé ne décale pas la place ordinale).
        var place = 0
        for (argument in liste.enfants.filter { it.genre == KtNodeTypes.VALUE_ARGUMENT }) {
            if (argument.fin <= deplacement && argument.enfants.none {
                    it.genre == KtNodeTypes.VALUE_ARGUMENT_NAME
                }
            ) {
                place++
            }
        }

        // Les types des paramètres à la place, sur les surcharges du
        // PROJET du même nom (le document courant et le scan).
        val rendus = mutableSetOf<String>()
        for (appelable in univers.modele.appelables(nomNu, univers.paquet)) {
            val parametre = appelable.parametres.getOrNull(place) ?: continue
            parametre.typeEcrit?.let { rendus.add(it) }
        }
        if (rendus.isEmpty()) return null
        return rendus
    }

    /** T2 — le type de retour écrit de la fonction englobante la plus profonde. */
    private fun attenteRetour(arbre: KtFichier, deplacement: Int): String? {
        val chaine = chaineAncetres(arbre.noeud, deplacement)
        val fonction = chaine.lastOrNull { it.genre == KtNodeTypes.FUN } ?: return null
        // Le type de retour : la première référence de type HORS liste de
        // paramètres (une fonction `f(a: Int): Ecran` porte `Ecran`).
        for (enfant in fonction.enfants) {
            if (enfant.genre != KtNodeTypes.TYPE_REFERENCE) continue
            if (enfant.fin <= (fonction.enfants
                    .firstOrNull { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }?.fin ?: 0)
            ) {
                continue
            }
            val ecrit = enfant.texteCanonique.trim()
            if (ecrit.isNotEmpty()) return ecrit
        }
        return null
    }

    /**
     * T3 — le type écrit de la propriété dont l'INITIALISATEUR couvre le
     * caret : le jeton `=` de la propriété précède le caret et le caret
     * vit dans la fenêtre de l'initialisateur (jusqu'à la fin du nœud —
     * l'initialisateur vide rend la fin ÉGALE au `=` ; deux blancs de
     * marge couvrent la frappe non commencée).
     */
    private fun attentePropriete(arbre: KtFichier, deplacement: Int): String? {
        var candidat: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.PROPERTY) {
                val egale = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.EQ }
                if (egale != null) {
                    val borne = maxOf(noeud.fin, egale.fin)
                    if (deplacement >= egale.fin && deplacement <= borne + 2) candidat = noeud
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(arbre.noeud)
        val propriete = candidat ?: return null
        return propriete.enfants
            .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
            ?.texteCanonique?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * T4 — la première branche COMPLETE lisible d'un `if`/`when` expression
     * dont une branche INCOMPLÈTE vit dans la ZONE du caret : la branche
     * vide a une plage NULLE — le caret vit entre la fin de la CONDITION
     * (ou du jeton `)` de l'en-tête) et le début de la branche suivante ;
     * l'attente vient des branches SŒURS complètes (THEN vide → l'ELSE
     * type ; ELSE vide → le THEN type ; branches de `when` sœurs
     * complètes).
     */
    private fun attenteBranche(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        deplacement: Int,
    ): String? {
        val chaine = chaineAncetres(arbre.noeud, deplacement)
        for (ancetre in chaine.asReversed()) {
            when (ancetre.genre) {
                KtNodeTypes.IF -> {
                    val alors = ancetre.enfants.firstOrNull { it.genre == KtNodeTypes.THEN }
                    val sinon = ancetre.enfants.firstOrNull { it.genre == KtNodeTypes.ELSE }
                    val zoneAlors = zoneDeBranche(ancetre, alors, suivant = sinon)
                    val zoneSinon = zoneDeBranche(ancetre, sinon, suivant = null)
                    val complet = when {
                        zoneAlors != null && deplacement in zoneAlors -> sinon
                        zoneSinon != null && deplacement in zoneSinon -> alors
                        else -> null
                    } ?: continue
                    val corps = complet.enfants.lastOrNull() ?: continue
                    return typeEcritDe(corps, arbre, univers, liaison)
                }
                KtNodeTypes.WHEN -> {
                    val flecheCourante = ancetre.enfants
                        .filter { it.genre == KtNodeTypes.WHEN_ENTRY }
                        .firstOrNull { couvre(it, deplacement) }
                        ?.jetonsPropres?.firstOrNull { it.genre == KtTokens.ARROW }
                    if (flecheCourante == null || deplacement <= flecheCourante.fin) continue
                    for (entree in ancetre.enfants.filter { it.genre == KtNodeTypes.WHEN_ENTRY }) {
                        if (couvre(entree, deplacement)) continue
                        val fleche = entree.jetonsPropres.firstOrNull { it.genre == KtTokens.ARROW }
                            ?: continue
                        val corps = entree.enfants.lastOrNull { it.fin > fleche.fin } ?: continue
                        return typeEcritDe(corps, arbre, univers, liaison)
                    }
                }
                else -> Unit
            }
        }
        return null
    }

    /**
     * La ZONE du caret d'une branche : la plage de la branche ÉTENDUE au
     * segment qui la PRÉCÈDE (depuis la fin de la condition / du jeton
     * précédent de l'`if` — une branche VIDE y absorbe le caret qui suit
     * la condition) jusqu'au début de la branche SŒUR suivante (ou la fin
     * de l'`if`).
     *
     * @param si le nœud IF porteur ;
     * @param branche la branche (THEN ou ELSE) ;
     * @param suivant la branche SŒUR suivante (`null` pour le ELSE) ;
     * @return la plage de la zone ; `null` sans branche.
     */
    private fun zoneDeBranche(si: NoeudArbre, branche: NoeudArbre?, suivant: NoeudArbre?): IntRange? {
        if (branche == null) return null
        // Le début de la zone : la fin du jeton `)` de l'en-tête de l'`if`
        // (la branche THEN vide absorbe le caret juste après la condition).
        val finEnTete = si.jetonsPropres
            .filter { it.debut < branche.debut }
            .lastOrNull { it.genre == KtTokens.RPAR }
            ?.fin ?: branche.debut
        val debut = minOf(branche.debut, finEnTete)
        val fin = when {
            suivant != null && suivant.debut > debut -> suivant.debut
            else -> si.fin
        }
        return debut..fin
    }

    /**
     * Le type ÉCRIT d'une expression complète : le type borné de
     * l'expression, rendu en nom SIMPLE pour le classement (le peseur
     * compare les derniers segments). La liaison se refonde sur la
     * FONCTION porteuse de l'expression (la région du caret peut être la
     * PROPRIÉTÉ locale dont l'initialiseur porte l'`if` — les paramètres
     * de la fonction restent visibles, KC-01 T3/T4).
     */
    private fun typeEcritDe(
        corps: NoeudArbre,
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
    ): String? {
        // La plus profonde FUN couvrant l'expression (sa fonction porteuse).
        var porteuse: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (corps.debut >= noeud.debut && corps.debut <= noeud.fin && noeud.genre == KtNodeTypes.FUN) {
                porteuse = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(arbre.noeud)
        val liaisonEffective = if (porteuse != null) {
            MarcheTypee.Liaison(arbre = arbre, univers = univers, region = porteuse)
        } else {
            liaison
        }
        val type = TypageBorne.typeDeLexpression(corps, univers, liaisonEffective) ?: return null
        return when (type) {
            is TypeValeur.Projet -> type.type.nomSimple
            is TypeValeur.Jvm -> type.fqcn
        }
    }

    /** La plus profonde liste d'arguments d'APPEL couvrant le caret (et son appel). */
    private fun listeArgumentsCouvrante(
        racine: NoeudArbre,
        deplacement: Int,
    ): Pair<NoeudArbre, NoeudArbre>? {
        var trouve: Pair<NoeudArbre, NoeudArbre>? = null
        fun marcher(noeud: NoeudArbre, parent: NoeudArbre?) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.VALUE_ARGUMENT_LIST && parent?.genre == KtNodeTypes.CALL_EXPRESSION) {
                trouve = noeud to parent
            }
            noeud.enfants.forEach { marcher(it, noeud) }
        }
        marcher(racine, null)
        return trouve
    }

    /**
     * Le caret est-il à une PLACE D'ARGUMENT (pas dans le slot de valeur
     * d'un argument nommé — miroir de `estPositionDeNom` inversé pour
     * l'attente ordinale).
     */
    private fun estPositionDArgument(liste: NoeudArbre, deplacement: Int): Boolean {
        for (argument in liste.enfants) {
            if (argument.genre != KtNodeTypes.VALUE_ARGUMENT) continue
            if (deplacement < argument.debut || deplacement > argument.fin) continue
            val nomEcrit = argument.enfants.firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_NAME }
            if (nomEcrit != null && deplacement > nomEcrit.fin) return false
            if (nomEcrit != null) return false
        }
        return true
    }

    /**
     * La déclaration FUN/PROPERTY `override` EN COURS d'écriture : le
     * modificateur est écrit, la déclaration n'a pas de corps BLOCK, et
     * le caret vit DANS la déclaration ou la SUIT immédiatement (aux
     * blancs près — le nœud de récupération `override fun |` s'arrête
     * après le mot-clé `fun`, le caret vit juste après sa fin).
     */
    private fun declarationOverrideCouvrante(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if ((noeud.genre == KtNodeTypes.FUN || noeud.genre == KtNodeTypes.PROPERTY) &&
                porteOverride(noeud) &&
                noeud.enfants.none { it.genre == KtNodeTypes.BLOCK } &&
                deplacement >= noeud.debut && deplacement <= noeud.fin + 2
            ) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /** La déclaration porte-t-elle le modificateur `override` ? */
    private fun porteOverride(declaration: NoeudArbre): Boolean =
        declaration.enfants
            .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
            .any { liste -> liste.enfants.any { it.genre == KtTokens.OVERRIDE_MODIFIER } }

    /** La CLASSE (ou objet) dont le corps porte la déclaration. */
    private fun classePorteuse(racine: NoeudArbre, position: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (position < noeud.debut || position > noeud.fin) return
            if (noeud.genre == KtNodeTypes.CLASS || noeud.genre == KtNodeTypes.OBJECT_DECLARATION) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /** Les noms de supertypes ÉCRITS de la classe (noms simples, arguments exclus). */
    private fun clausesSupertypes(classe: NoeudArbre): List<String> {
        val clause = classe.enfants.firstOrNull { it.genre == KtNodeTypes.SUPER_TYPE_LIST } ?: return emptyList()
        val noms = mutableListOf<String>()
        for (entree in clause.enfants) {
            if (entree.genre != KtNodeTypes.SUPER_TYPE_ENTRY &&
                entree.genre != KtNodeTypes.SUPER_TYPE_CALL_ENTRY
            ) {
                continue
            }
            val utilisateur = entree.enfants
                .firstOrNull { it.genre == KtNodeTypes.CONSTRUCTOR_CALLEE }
                ?.enfants?.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                ?: entree.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                ?: continue
            val nom = utilisateur.texteCanonique.substringBefore('<').trim()
            if (nom.isNotEmpty()) noms.add(nom)
        }
        return noms
    }

    /** Les noms des membres FUN/PROPERTY déclarés dans le corps de la classe. */
    private fun membresDeclares(classe: NoeudArbre): List<String> {
        val corps = classe.enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY } ?: return emptyList()
        return corps.enfants.mapNotNull { membre ->
            when (membre.genre) {
                KtNodeTypes.FUN, KtNodeTypes.PROPERTY ->
                    membre.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                else -> null
            }
        }
    }

    /**
     * Un membre du type projet est-il ABSTRAIT : sa déclaration SOURCE
     * (relue dans l'arbre du document) est un `fun` SANS bloc ou une
     * propriété SANS initialiseur — le prédicat du modèle
     * (`estAbstrait` à liste blanche) est FAUVE pour les fonctions à
     * corps de bloc (bug latent V-D : `fermer() { }` y était abstraite).
     */
    private fun estAbstraitDeclaration(univers: UniversPreuves, type: jo.codeide.lsp.kotlin.resolve.TypeProjet, nomMembre: String): Boolean {
        val noeud = noeudDeclarationDuMembre(univers, type.nomSimple, nomMembre) ?: return false
        return when (noeud.genre) {
            KtNodeTypes.FUN -> noeud.enfants.none { it.genre == KtNodeTypes.BLOCK }
            KtNodeTypes.PROPERTY -> noeud.jetonsPropres.none { it.genre == KtTokens.EQ }
            else -> false
        }
    }

    /**
     * Un membre CONCRET du type projet est-il OUVERT : le modificateur
     * `open` se lit sur SA déclaration — la déclaration SOURCE se relit
     * dans l'arbre du DOCUMENT (une déclaration hors du document n'est
     * PAS prouvée ouverte, O5 : preuve ou silence) ; une interface est
     * OUVERTE par nature (tout membre concret d'une interface est
     * ouvrable).
     */
    private fun estOuvert(univers: UniversPreuves, type: jo.codeide.lsp.kotlin.resolve.TypeProjet, nomMembre: String): Boolean {
        if (type.genre == jo.codeide.lsp.kotlin.resolve.GenreTypeProjet.INTERFACE) return true
        val noeud = noeudDeclarationDuMembre(univers, type.nomSimple, nomMembre) ?: return false
        return noeud.enfants
            .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
            .any { liste -> liste.enfants.any { it.genre == KtTokens.OPEN_MODIFIER } }
    }

    /** Le nœud de déclaration du membre du type projet dans l'arbre du document. */
    private fun noeudDeclarationDuMembre(univers: UniversPreuves, nomSimple: String, nomMembre: String): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcherClasse(noeud: NoeudArbre, dansCible: Boolean) {
            if (noeud.genre == KtNodeTypes.CLASS || noeud.genre == KtNodeTypes.OBJECT_DECLARATION) {
                val nom = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                val cible = dansCible || nom == nomSimple
                val corps = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY }
                if (cible && corps != null) {
                    for (membre in corps.enfants) {
                        if (membre.genre == KtNodeTypes.FUN || membre.genre == KtNodeTypes.PROPERTY) {
                            if (membre.jetonsPropres.any {
                                    it.genre == KtTokens.IDENTIFIER && it.texte == nomMembre
                                }
                            ) {
                                trouve = membre
                            }
                        }
                    }
                }
                noeud.enfants.forEach { marcherClasse(it, cible) }
                return
            }
            noeud.enfants.forEach { marcherClasse(it, dansCible) }
        }
        marcherClasse(univers.arbre.noeud, false)
        return trouve
    }

    /** Un membre JVM CONCRET est-il ouvrable : classe non finale, méthode non finale. */
    private fun estOuvertJvm(accesClasse: Int, membre: jo.codeide.lsp.kotlin.resolve.MembreJvm): Boolean {
        val ACC_FINAL = 0x0010
        if (accesClasse and ACC_FINAL != 0) return false
        return membre.acces and ACC_FINAL == 0
    }

    /** La proposition OVERRIDE d'un membre du projet (signature complète, miroir MembresManquants). */
    private fun propositionOverrideProjet(
        membre: jo.codeide.lsp.kotlin.resolve.MembreProjet,
        nomSupertype: String,
    ): PropositionKotlin {
        val substitution = { nom: String? -> nom }
        return if (membre.genre == jo.codeide.lsp.kotlin.resolve.GenreMembreProjet.FONCTION) {
            val parametres = membre.parametres.joinToString(", ") { parametre ->
                val type = substitution(parametre.typeEcrit) ?: "?"
                if (parametre.nom == null) type else "${parametre.nom}: $type"
            }
            val retour = substitution(membre.typeRetourNomme)
            val signature = if (retour == null || retour == "Unit") {
                "override fun ${membre.nom}($parametres) = TODO(\"${membre.nom}\")"
            } else {
                "override fun ${membre.nom}($parametres): $retour = TODO(\"${membre.nom}\")"
            }
            PropositionKotlin(
                libelle = membre.nom,
                nature = NaturePropositionKotlin.FONCTION,
                detail = "$nomSupertype.${membre.nom}",
                texteInsertion = signature,
            )
        } else {
            PropositionKotlin(
                libelle = membre.nom,
                nature = NaturePropositionKotlin.PROPRIETE,
                detail = "$nomSupertype.${membre.nom}",
                texteInsertion = "override val ${membre.nom}: ${membre.typeRetourNomme ?: "?"} " +
                    "get() = TODO(\"${membre.nom}\")",
            )
        }
    }

    /** La proposition OVERRIDE d'un membre JVM du classpath. */
    private fun propositionOverrideJvm(
        membre: jo.codeide.lsp.kotlin.resolve.MembreJvm,
        nomSupertype: String,
    ): PropositionKotlin {
        val parametres = membre.parametres()?.joinToString(", ") { it.substringAfterLast('.') }.orEmpty()
        val retour = membre.retour()
        val typeRetour = retour?.let { ": " + it.substringAfterLast('.') }.orEmpty()
        val corps = if (retour == null || retour == "kotlin.Unit") "" else typeRetour
        return PropositionKotlin(
            libelle = membre.nom,
            nature = NaturePropositionKotlin.FONCTION,
            detail = "$nomSupertype.${membre.nom}",
            texteInsertion = "override fun ${membre.nom}($parametres)$corps = TODO(\"${membre.nom}\")",
        )
    }

    /**
     * Les extensions APPLICABLES à un récepteur FQCN : le PROJET d'abord
     * ([ModeleProjet.extensionsSur] sur le FQCN et sa chaîne de
     * supertypes LISIBLE), puis le CLASSPATH ([VueAppelables] par les
     * récepteurs de la chaîne — miroir de `recepteursExtension`).
     *
     * @return les paires (nom, type de retour écrit) — le type de retour
     * `null` (illisible) n'entre PAS (un maillon non démontrable
     * n'offre aucune chaîne, E2).
     */
    private fun extensionsApplicables(univers: UniversPreuves, fqcnRecepteur: String): List<Pair<String, String>> {
        val rendues = LinkedHashMap<String, String>()

        // Le projet : les extensions déclarées sur le récepteur exact.
        for (extension in univers.modele.extensionsSur(fqcnRecepteur)) {
            extension.typeRetourNomme?.let { rendues[extension.nom] = it }
        }

        // Le classpath : les extensions de l'index sur le récepteur et sa
        // chaîne de supertypes (le miroir Kotlin des types mappés inclus).
        if (univers.index != null) {
            for (recepteur in recepteursExtension(univers, fqcnRecepteur)) {
                for (extension in univers.index.extensionsSur(recepteur)) {
                    if (extension.genre != jo.codeide.lsp.kotlin.index.GenreAppelable.FONCTION) continue
                    extension.typeRetour?.let { rendues.putIfAbsent(extension.nom, it) }
                }
            }
        }
        return rendues.map { it.key to it.value }
    }

    /**
     * Les récepteurs d'extension à consulter pour un FQCN (miroir de
     * `UniversPreuves.recepteursExtension` — récepteur exact, chaîne de
     * supertypes JVM, miroir Kotlin des types mappés, hiérarchie des
     * intégrées).
     */
    private fun recepteursExtension(univers: UniversPreuves, fqcn: String): List<String> {
        val recepteurs = LinkedHashSet<String>()
        recepteurs.add(fqcn)
        val mappes = jo.codeide.lsp.kotlin.resolve.UniversPreuves.MAPPES_JVM[fqcn] ?: fqcn
        val maillons = univers.vueJvm?.chainesDe(mappes).orEmpty() +
            univers.vueJvm?.chainesDe(fqcn).orEmpty() +
            jo.codeide.lsp.kotlin.resolve.UniversPreuves.HIERARCHIE_BUILTINS[fqcn].orEmpty()
        for (maillon in maillons) {
            recepteurs.add(maillon)
            jo.codeide.lsp.kotlin.resolve.UniversPreuves.MAPPES_JVM_INVERSE[maillon]?.let { recepteurs.add(it) }
        }
        return recepteurs.toList()
    }

    /** Le `when` couvrant le caret en zone de condition (miroir V-C). */
    private fun quandCouvrant(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.WHEN) {
                val sujet = noeud.enfants.firstOrNull { it.genre != KtNodeTypes.WHEN_ENTRY }
                if (sujet == null || deplacement > sujet.fin) trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        val quand = trouve ?: return null
        for (entree in quand.enfants) {
            if (entree.genre != KtNodeTypes.WHEN_ENTRY) continue
            if (deplacement < entree.debut || deplacement > entree.fin) continue
            val fleche = entree.jetonsPropres.firstOrNull { it.genre == KtTokens.ARROW }
            if (fleche != null && deplacement > fleche.debut) return null
        }
        return quand
    }

    /** La plus profonde expression qualifiée couvrant le point AVANT le caret (miroir CompletionMembres). */
    private fun recepteurQualifie(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement - 1 < noeud.debut || deplacement - 1 >= noeud.fin) return
            if (noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION ||
                noeud.genre == KtNodeTypes.SAFE_ACCESS_EXPRESSION
            ) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /** La chaîne des ancêtres couvrant la position (de la racine au plus profond). */
    private fun chaineAncetres(racine: NoeudArbre, position: Int): List<NoeudArbre> {
        val chaine = mutableListOf<NoeudArbre>()
        var courant: NoeudArbre? = racine
        while (courant != null && position >= courant.debut && position <= courant.fin) {
            chaine.add(courant)
            courant = courant.enfants.firstOrNull { position >= it.debut && position <= it.fin }
        }
        return chaine
    }

    /** La plage du nœud couvre-t-elle la position ? */
    private fun couvre(noeud: NoeudArbre, position: Int): Boolean =
        position >= noeud.debut && position <= noeud.fin
}

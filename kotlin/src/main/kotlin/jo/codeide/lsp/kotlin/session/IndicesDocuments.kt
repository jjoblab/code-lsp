// CodeIDE — indices en ligne d'un document Kotlin (module :kotlin, étage 6
// vague 6b). Code NEUF jjoblab (contrat : fiche 015 du cahier
// docs/cahier-specifications-kotlin/ — les trois familles d'indices :
// PARAMETRE aux appels positionnels prouvés, TYPE des déclarations sans
// type écrit, LAMBDA des `it` implicites). L'honnêteté D3 est le cœur :
// sans preuve, AUCUN indice (un appel non résolu, une initialisation
// composée, un suffixe non modélisé, une arité divergente).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.GenreIndiceKotlin
import jo.codeide.lsp.kotlin.api.IndiceKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.PileThis
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Calcul des INDICES EN LIGNE d'un document (pur — aucun état, appelé sous
 * verrou de lecture).
 *
 * ## Les trois familles (fiche 015)
 *
 *  - **PARAMETRE** : à un appel dont la candidate est PROUVÉE (fonction du
 *    projet — modèle de symboles), chaque argument POSITIONNEL porte le
 *    nom de son paramètre (ancré sur l'argument, libellé AVANT). Un
 *    argument NOMMÉ (`b = 2`) porte déjà son nom : JAMAIS d'indice. Quand
 *    plusieurs candidates existent, l'indice n'est émis que si elles
 *    s'ACCORDENT sur le nom du paramètre à cet index (surcharges
 *    homonymes divergentes : silence).
 *  - **TYPE** : une déclaration (`val`/`var`) sans type écrit dont
 *    l'initialiseur est d'un type CERTAIN (littéral modélisé,
 *    constructeur du projet) porte son type (ancré sur le NOM). Les
 *    initialiseurs COMPOSÉS (somme d'appels), les RÉFÉRENCES nues et
 *    les suffixes non modélisés (`1u`) restent MUETS.
 *  - **LAMBDA** : un littéral lambda SANS paramètre écrit (`{ println(it) }`)
 *    dont l'attente se prouve à UN paramètre porte le libellé COMPLET
 *    `it: Type` (ancré sur l'accolade ouvrante) ; un littéral À
 *    paramètres écrits sans type (`{ x, y -> … }`) porte un TYPE par nom
 *    — à condition que l'arité ÉGALE l'attente (divergente : AUCUN
 *    indice, un nom déjà typé n'en reçoit pas).
 */
internal object IndicesDocuments {

    /**
     * Rend les indices de la plage demandée.
     *
     * @param document le document ouvert visé ;
     * @param univers l'univers des preuves (modèle + index + vue JVM) ;
     * @param debutPlage l'offset de début de la plage ;
     * @param finPlage l'offset de fin de la plage ;
     * @return les indices en ordre du document, ancres dans la plage ;
     * liste VIDE sans preuve (D3).
     */
    internal fun indices(
        document: DocumentOuvert,
        univers: UniversPreuves?,
        debutPlage: Int,
        finPlage: Int,
    ): List<IndiceKotlin> {
        val resultat = mutableListOf<IndiceKotlin>()
        val liaison = univers?.let {
            MarcheTypee.Liaison(arbre = it.arbre, univers = it, region = null)
        }
        marcher(document.arbre.noeud, univers, liaison, debutPlage, finPlage, resultat)
        return resultat.sortedWith(compareBy({ it.debut }, { it.genre.ordinal }))
    }

    /**
     * La marche récursive : collecte les indices des trois familles en
     * ordre du document (les nœuds se visitent en profondeur, l'ordre de
     * collecte suit l'ordre d'écriture).
     */
    private fun marcher(
        noeud: NoeudArbre,
        univers: UniversPreuves?,
        liaison: MarcheTypee.Liaison?,
        debutPlage: Int,
        finPlage: Int,
        resultat: MutableList<IndiceKotlin>,
    ) {
        // Le nœud doit INTERSECTER la plage pour porter des indices (la
        // borne de la fiche : les indices ne sortent JAMAIS de la plage).
        if (noeud.fin <= debutPlage || noeud.debut >= finPlage) return
        when (noeud.genre) {
            KtNodeTypes.PROPERTY -> {
                indicesPropriete(noeud, univers, liaison, debutPlage, finPlage, resultat)
                noeud.enfants.forEach { marcher(it, univers, liaison, debutPlage, finPlage, resultat) }
            }
            KtNodeTypes.CALL_EXPRESSION -> {
                indicesAppel(noeud, univers, debutPlage, finPlage, resultat)
                noeud.enfants.forEach { marcher(it, univers, liaison, debutPlage, finPlage, resultat) }
            }
            KtNodeTypes.FUNCTION_LITERAL -> {
                indicesLitteral(litteral = noeud, univers, liaison, debutPlage, finPlage, resultat)
                noeud.enfants.forEach { marcher(it, univers, liaison, debutPlage, finPlage, resultat) }
            }
            else -> noeud.enfants.forEach { marcher(it, univers, liaison, debutPlage, finPlage, resultat) }
        }
    }

    // ------------------------------------------------------------------
    // Famille TYPE : les déclarations sans type écrit.
    // ------------------------------------------------------------------

    /**
     * Les indices TYPE d'une propriété sans type écrit : l'initialiseur
     * d'un type CERTAIN type la déclaration (ancré sur le NOM).
     */
    private fun indicesPropriete(
        propriete: NoeudArbre,
        univers: UniversPreuves?,
        liaison: MarcheTypee.Liaison?,
        debutPlage: Int,
        finPlage: Int,
        resultat: MutableList<IndiceKotlin>,
    ) {
        if (univers == null || liaison == null) return
        // Un type ÉCRIT porte déjà l'information : aucun indice.
        if (propriete.enfants.any { it.genre == KtNodeTypes.TYPE_REFERENCE }) return
        val nom = propriete.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER } ?: return
        if (nom.debut < debutPlage || nom.debut >= finPlage) return
        val initialiseur = initialiseurDe(propriete) ?: return
        val type = typeCertainDe(initialiseur, univers, liaison) ?: return
        resultat.add(
            IndiceKotlin(libelle = type, genre = GenreIndiceKotlin.TYPE, debut = nom.debut, fin = nom.debut + 1),
        )
    }

    /**
     * L'initialiseur d'une propriété (le dernier enfant expression —
     * miroir de [MarcheTypee] : type écrit, délégue et accesseurs exclus).
     */
    private fun initialiseurDe(propriete: NoeudArbre): NoeudArbre? {
        return propriete.enfants.lastOrNull {
            it.genre != KtNodeTypes.TYPE_REFERENCE &&
                it.genre != KtNodeTypes.MODIFIER_LIST &&
                it.genre != KtNodeTypes.PROPERTY_DELEGATE &&
                it.genre != KtNodeTypes.PROPERTY_ACCESSOR
        }
    }

    /**
     * Le type CERTAIN d'un initialiseur (fiche 015 : « types certains ») :
     * les LITTÉRAUX modélisés (suffixes non modélisés `u`/`U` exclus — le
     * type ne se décide pas), sinon le typage borné (constructeurs du
     * projet). Une expression COMPOSÉE ou une RÉFÉRENCE nue reste `null`
     * (le typage borné ne les décide pas — conservateur par construction).
     *
     * @return le nom SIMPLE du type (`Int`, `Vue`) ; `null` si incertain.
     */
    private fun typeCertainDe(
        initialiseur: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
    ): String? {
        // Les suffixes non modélisés : l'entier non signé (`1u`) n'a pas de
        // type décidable à cet étage (fiche 015 : « suffixe non modélisé »).
        if (initialiseur.genre == KtNodeTypes.INTEGER_CONSTANT ||
            initialiseur.genre == KtNodeTypes.FLOAT_CONSTANT
        ) {
            val texte = initialiseur.texteCanonique
            if (texte.endsWith("u") || texte.endsWith("U")) return null
        }
        return when (val type = TypageBorne.typeDeLexpression(initialiseur, univers, liaison)) {
            null -> null
            is TypeValeur.Projet -> type.type.nomSimple
            is TypeValeur.Jvm -> nomSimple(type.fqcn)
        }
    }

    /** Le nom SIMPLE d'un FQCN (`kotlin.String` → `String`, `A$B` → `B`). */
    private fun nomSimple(fqcn: String): String =
        fqcn.substringAfterLast('.').substringAfterLast('$')

    // ------------------------------------------------------------------
    // Famille PARAMETRE : les appels positionnels prouvés.
    // ------------------------------------------------------------------

    /**
     * Les indices PARAMETRE d'un appel : chaque argument POSITIONNEL dont
     * le nom du paramètre est PROUVÉ porte son nom (ancré sur l'argument).
     */
    private fun indicesAppel(
        appel: NoeudArbre,
        univers: UniversPreuves?,
        debutPlage: Int,
        finPlage: Int,
        resultat: MutableList<IndiceKotlin>,
    ) {
        if (univers == null) return
        val nom = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique.orEmpty()
        if (nom.isEmpty()) return
        val liste = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST } ?: return
        val arguments = liste.enfants.filter { it.genre == KtNodeTypes.VALUE_ARGUMENT }
        if (arguments.isEmpty()) return
        // Les candidates du projet (modèle de symboles — document courant,
        // scan des racines) : les paramètres par nom.
        val candidates = univers.modele.appelables(nom, univers.paquet)
        if (candidates.isEmpty()) return
        for ((indice, argument) in arguments.withIndex()) {
            // Un argument NOMMÉ porte déjà son nom : JAMAIS d'indice.
            if (argument.enfants.any { it.genre == KtNodeTypes.VALUE_ARGUMENT_NAME }) continue
            if (argument.debut < debutPlage || argument.debut >= finPlage) continue
            // Les noms proposés par les candidates à CET index : l'indice
            // n'est émis que si elles s'ACCORDENT (divergence : silence).
            val noms = candidates
                .mapNotNull { it.parametres.getOrNull(indice)?.nom }
                .filter { it.isNotEmpty() }
                .distinct()
            if (noms.size != 1) continue
            resultat.add(
                IndiceKotlin(
                    libelle = noms.single(),
                    genre = GenreIndiceKotlin.PARAMETRE,
                    debut = argument.debut,
                    fin = argument.debut + 1,
                ),
            )
        }
    }

    // ------------------------------------------------------------------
    // Famille LAMBDA : les `it` implicites et les paramètres non typés.
    // ------------------------------------------------------------------

    /**
     * Les indices d'un littéral lambda : le `it` IMPLICITE (libellé complet
     * `it: Type`, ancre sur l'accolade ouvrante) quand le littéral est SANS
     * paramètre écrit et l'attente à UN paramètre ; sinon un TYPE par
     * paramètre écrit SANS type, à arité ÉGALE seulement.
     */
    private fun indicesLitteral(
        litteral: NoeudArbre,
        univers: UniversPreuves?,
        liaison: MarcheTypee.Liaison?,
        debutPlage: Int,
        finPlage: Int,
        resultat: MutableList<IndiceKotlin>,
    ) {
        if (univers == null) return
        val arbre = univers.arbre
        val listeParams = litteral.enfants
            .firstOrNull { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
        if (listeParams == null) {
            // `it` implicite : l'attente à UN paramètre seul le prouve
            // (une locale nommée `it` prime — garde TypageBorne).
            if (litteral.debut < debutPlage || litteral.debut >= finPlage) return
            val attente = TypageBorne.attenteLambdaAuPoint(
                arbre, litteral.debut, univers, liaison, PileThis.VIDE,
            ) ?: return
            if (attente.size != 1) return
            resultat.add(
                IndiceKotlin(
                    libelle = "it: ${attente.single()}",
                    genre = GenreIndiceKotlin.LAMBDA,
                    debut = litteral.debut,
                    fin = litteral.debut + 1,
                ),
            )
            return
        }
        // Paramètres EXPLICITES : un TYPE par nom sans type écrit, à arité
        // ÉGALE de l'attente (divergente : AUCUN indice du littéral).
        val params = listeParams.enfants.filter { it.genre == KtNodeTypes.VALUE_PARAMETER }
        if (params.isEmpty()) return
        val attente = TypageBorne.attenteLambdaAuPoint(
            arbre, litteral.debut, univers, liaison, PileThis.VIDE,
        ) ?: return
        if (attente.size != params.size) return
        for ((indice, parametre) in params.withIndex()) {
            if (parametre.enfants.any { it.genre == KtNodeTypes.TYPE_REFERENCE }) continue
            val nom = parametre.jetonsPropres
                .firstOrNull { it.genre == KtTokens.IDENTIFIER } ?: continue
            if (nom.debut < debutPlage || nom.debut >= finPlage) continue
            val type = attente.getOrNull(indice) ?: continue
            resultat.add(
                IndiceKotlin(
                    libelle = type,
                    genre = GenreIndiceKotlin.TYPE,
                    debut = nom.debut,
                    fin = nom.debut + 1,
                ),
            )
        }
    }
}

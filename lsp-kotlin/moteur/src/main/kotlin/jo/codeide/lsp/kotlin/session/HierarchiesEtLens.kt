// CodeIDE — HIÉRARCHIES et CODE LENS (module :kotlin, vague V-I2 — ADR
// 0136 étape B.3 : callHierarchy/typeHierarchy fondés palier 2 « types
// Projet/Jvm sans variance » et codeLens par occurrences PARESSEUSES
// « périmètre borné »). Code NEUF jjoblab (clean-room — bornologie : le
// type d'une item est le type ÉCRIT résolu, JAMAIS la variance ; la
// hiérarchie d'appels se prouve sur les appelables de premier niveau
// du projet dont les arguments portent le type attendu).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.EmplacementKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Une ITEM de hiérarchie de TYPES (miroir LSP `TypeHierarchyItem`, modèle
 * neutre — l'hôte le traduit) : un type avec sa plage de DÉCLARATION.
 *
 * @property nom le nom SIMPLE du type ;
 * @property uri l'uri du fichier déclarant ;
 * @property plage la plage du NOM de la déclaration ;
 * @property genre le genre (CLASSE/INTERFACE/ENUM/OBJET — le kind LSP).
 */
internal class ItemHierarchieKotlin internal constructor(
    internal val nom: String,
    internal val uri: String,
    internal val plage: PlageTexte,
    internal val genre: String,
)

/**
 * Une ITEM de hiérarchie d'APPELS (miroir LSP `CallHierarchyItem`) : un
 * APPELABLE (fonction de premier niveau du projet) avec sa plage.
 *
 * @property nom le nom de l'appelable ;
 * @property uri l'uri du fichier déclarant ;
 * @property plage la plage du NOM de la déclaration ;
 * @property detail la signature écrite (types écrits, sans variance).
 */
internal class ItemAppelKotlin internal constructor(
    internal val nom: String,
    internal val uri: String,
    internal val plage: PlageTexte,
    internal val detail: String,
)

/**
 * Un appel ENTRANT/SORTANT : l'appelant/l'appelé avec la plage de
 * l'APPEL dans son fichier (miroir LSP `CallHierarchyIncomingCall`).
 *
 * @property item l'appelable de l'autre bout ;
 * @property plagesAppel les plages des APPELS dans le fichier de CETTE
 * item (les flèches FROM/TO de LSP).
 */
internal class AreteAppelKotlin internal constructor(
    internal val item: ItemAppelKotlin,
    internal val plagesAppel: List<PlageTexte>,
)

/**
 * Les hiérarchies et le codeLens V-I2 (ADR 0136 B.3) —
 *
 * - **typeHierarchy** : les SUPERTYPES/TYPES DIRECTS ÉCRITS du type sous
 *   le point (preuve : la clause de la déclaration du type projet ; les
 *   maillons du classpath restent des items au nom FQCN SANS navigation
 *   — l'affichage est borné honnête) ;
 * - **callHierarchy** : les APPELS ENTRANTS d'un appelable de premier
 *   niveau (les appelants du projet dont les appels NU portent le nom)
 *   et SORTANTS (les appelés : les appels NU du CORPS de la déclaration) ;
 * - **codeLens** : les OCCURRENCES paresseuses du document — chaque
 *   DÉCLARATION de premier niveau (classe, fonction, propriété) porte
 *   une lentille du compte de ses RÉFÉRENCES prouvées dans le document
 *   (miroir Find Usages — périmètre BORNÉ au document + fichiers du
 *   scan par le montage des occurrences).
 */
internal object HierarchiesEtLens {

    // ------------------------------------------------------------------
    // typeHierarchy — les items de la préparation et des sous/supertypes.
    // ------------------------------------------------------------------

    /**
     * La PRÉPARATION : l'item du TYPE sous le point (la déclaration du
     * type nommé — référence de type, nom de déclaration).
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param uri l'uri du document ;
     * @param deplacement l'offset du caret ;
     * @return l'item de départ ; `null` sans type prouvé (D3).
     */
    internal fun preparerType(
        arbre: KtFichier,
        univers: UniversPreuves,
        uri: String,
        deplacement: Int,
    ): ItemHierarchieKotlin? {
        // Le NOM sous le caret : une RÉFERENCE (l'usage) OU la DÉCLARATION
        // (le nom introduit — le caret vit DANS le nom de la déclaration) ;
        // son nom résout vers un type du projet — la DÉCLARATION du type
        // est l'item.
        val nom = nomSousLePoint(arbre, deplacement) ?: return null
        if (nom.isEmpty() || nom.contains(' ')) return null
        val type = univers.modele.typeParNomSimple(nom, univers.paquet) ?: return null
        val declaration = declarationDuType(arbre, univers, type.nomSimple) ?: return null
        return ItemHierarchieKotlin(
            nom = type.nomSimple,
            uri = declaration.first,
            plage = declaration.second,
            genre = type.genre.toString(),
        )
    }

    /**
     * Le NOM sous le caret : la référence couvrante OU l'IDENTIFIANT de la
     * déclaration couvrante (le caret vit dans le nom de `class Cercle` —
     * le nœud CLASS porte l'IDENTIFIER).
     */
    private fun nomSousLePoint(arbre: KtFichier, deplacement: Int): String? {
        referenceCouvrante(arbre.noeud, deplacement)?.let { return it.texteCanonique.substringAfterLast('.') }
        var trouve: String? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.CLASS || noeud.genre == KtNodeTypes.OBJECT_DECLARATION ||
                noeud.genre == KtNodeTypes.FUN || noeud.genre == KtNodeTypes.PROPERTY
            ) {
                noeud.jetonsPropres.firstOrNull { jeton ->
                    jeton.genre == KtTokens.IDENTIFIER &&
                        deplacement >= jeton.debut && deplacement <= jeton.fin
                }?.let { trouve = it.texte }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(arbre.noeud)
        return trouve
    }

    /**
     * Les SUPERTYPES DIRECTS du type item (la clause ÉCRITE de sa
     * déclaration — résolus en items projet ; un maillon classpath rend
     * une item au FQCN SANS uri de navigation).
     *
     * @param arbre l'arbre du document de départ ;
     * @param univers l'univers des preuves ;
     * @param item l'item préparée ;
     * @return les items supertypes ; VIDE sans clause prouvable.
     */
    internal fun supertypes(
        arbre: KtFichier,
        univers: UniversPreuves,
        item: ItemHierarchieKotlin,
    ): List<ItemHierarchieKotlin> {
        val type = univers.modele.typeParNomSimple(item.nom, univers.paquet) ?: return emptyList()
        val items = mutableListOf<ItemHierarchieKotlin>()
        for (nomEcrit in type.supertypesEcrits) {
            val resolu = univers.supertypeResolu(nomEcrit) ?: continue
            when (resolu) {
                is TypeValeur.Projet -> {
                    val declaration = declarationDuType(arbre, univers, resolu.type.nomSimple)
                    if (declaration != null) {
                        items.add(
                            ItemHierarchieKotlin(
                                nom = resolu.type.nomSimple,
                                uri = declaration.first,
                                plage = declaration.second,
                                genre = resolu.type.genre.toString(),
                            ),
                        )
                    }
                }
                is TypeValeur.Jvm -> {
                    // Un maillon du CLASSPATH : item au FQCN — l'affichage
                    // borné honnête (la navigation vers les sources du jar
                    // relève du décompilateur, hors périmètre).
                    items.add(
                        ItemHierarchieKotlin(
                            nom = resolu.fqcn.substringAfterLast('.'),
                            uri = "",
                            plage = PlageTexte(0, 0),
                            genre = "CLASSE",
                        ),
                    )
                }
            }
        }
        return items
    }

    /**
     * Les SOUS-TYPES DIRECTS du type item (les déclarations du document +
     * du scan dont la clause ÉCRIT résout vers ce type — preuve par
     * FQCN, miroir de `implementations`).
     *
     * @param arbre l'arbre du document de départ ;
     * @param univers l'univers des preuves ;
     * @param item l'item préparée ;
     * @return les items sous-types ; VIDE sans preuve.
     */
    internal fun sousTypes(
        arbre: KtFichier,
        univers: UniversPreuves,
        item: ItemHierarchieKotlin,
    ): List<ItemHierarchieKotlin> {
        val cible = univers.modele.typeParNomSimple(item.nom, univers.paquet) ?: return emptyList()
        val items = mutableListOf<ItemHierarchieKotlin>()
        // Les types du MODÈLE dont un supertype écrit résout vers la cible.
        for (nom in univers.modele.typesParNomSimple()) {
            val candidat = univers.modele.typeParNomSimple(nom, univers.paquet) ?: continue
            if (candidat.fqcn == cible.fqcn) continue
            val herite = candidat.supertypesEcrits.any { ecrit ->
                val resolu = univers.supertypeResolu(ecrit)
                when (resolu) {
                    is TypeValeur.Projet -> resolu.type.fqcn == cible.fqcn
                    else -> false
                }
            }
            if (herite) {
                val declaration = declarationDuType(arbre, univers, candidat.nomSimple)
                if (declaration != null) {
                    items.add(
                        ItemHierarchieKotlin(
                            nom = candidat.nomSimple,
                            uri = declaration.first,
                            plage = declaration.second,
                            genre = candidat.genre.toString(),
                        ),
                    )
                }
            }
        }
        return items.sortedBy { it.nom }
    }

    // ------------------------------------------------------------------
    // callHierarchy — la préparation, les entrants, les sortants.
    // ------------------------------------------------------------------

    /**
     * La PRÉPARATION : l'item de l'APPELABLE sous le point (fonction de
     * premier niveau du projet — le nom de la déclaration ou l'usage).
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @param uri l'uri du document ;
     * @param deplacement l'offset du caret ;
     * @return l'item de départ ; `null` sans appelable prouvé.
     */
    internal fun preparerAppel(
        arbre: KtFichier,
        univers: UniversPreuves,
        uri: String,
        deplacement: Int,
    ): ItemAppelKotlin? {
        // Le NOM sous le caret : la référence d'appel OU l'IDENTIFIANT de
        // la déclaration FUN (la préparation se fait aussi BIEN sur
        // l'appel que sur la déclaration).
        val nom = nomSousLePoint(arbre, deplacement)?.substringAfterLast('.') ?: return null
        if (nom.isEmpty()) return null
        val appelables = univers.modele.appelables(nom, univers.paquet)
        val appelable = appelables.firstOrNull() ?: return null
        val declaration = declarationDeLappelable(arbre, univers, nom) ?: return null
        return ItemAppelKotlin(
            nom = nom,
            uri = declaration.first,
            plage = declaration.second,
            detail = signatureEcrite(appelable.typeRetourNomme),
        )
    }

    /**
     * Les APPELS ENTRANTS de l'appelable item : les appelables du projet
     * dont le CORPS appelle le nom NU — chaque arête porte l'item de
     * l'APPELANT et les plages des appels dans SON fichier.
     *
     * @param arbre l'arbre du document de départ ;
     * @param univers l'univers des preuves ;
     * @param item l'item préparée ;
     * @return les arêtes entrantes ; VIDE sans appelant prouvé.
     */
    internal fun appelsEntrants(
        arbre: KtFichier,
        univers: UniversPreuves,
        item: ItemAppelKotlin,
    ): List<AreteAppelKotlin> {
        return appelsDuNom(arbre, univers, item.nom, hors = item.nom)
    }

    /**
     * Les APPELS SORTANTS de l'appelable item : les appelables du projet
     * appelés NU dans le CORPS de la déclaration.
     *
     * @param arbre l'arbre du document de départ ;
     * @param univers l'univers des preuves ;
     * @param item l'item préparée ;
     * @return les arêtes sortantes ; VIDE sans appelé prouvé.
     */
    internal fun appelsSortants(
        arbre: KtFichier,
        univers: UniversPreuves,
        item: ItemAppelKotlin,
    ): List<AreteAppelKotlin> {
        // La déclaration FUN ENTIÈRE de l'appelable item dans l'arbre du
        // document (le CORPS vit dans la FUN — la plage du NOM ne suffit
        // pas : les appels du corps couvrent la déclaration complète).
        val funDeclarante = funDeclarante(arbre, item.nom) ?: return emptyList()
        val aretes = mutableListOf<AreteAppelKotlin>()
        // Les appels NU du corps de la déclaration : chaque nom appelé
        // prouvé appelable du projet rend une arête vers SA déclaration.
        val appelNus = mutableMapOf<String, MutableList<PlageTexte>>()
        marcher(arbre.noeud) { noeud ->
            if (noeud.genre == KtNodeTypes.CALL_EXPRESSION &&
                noeud.debut >= funDeclarante.debut && noeud.fin <= funDeclarante.fin
            ) {
                val nom = noeud.enfants
                    .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                    ?.texteCanonique?.substringAfterLast('.')
                if (!nom.isNullOrEmpty() && nom != item.nom) {
                    appelNus.getOrPut(nom) { mutableListOf() }.add(
                        PlageTexte(noeud.debut, noeud.debut + nom.length),
                    )
                }
            }
        }
        for ((nom, plages) in appelNus) {
            val cible = declarationDeLappelable(arbre, univers, nom) ?: continue
            val appelable = univers.modele.appelables(nom, univers.paquet).firstOrNull() ?: continue
            aretes.add(
                AreteAppelKotlin(
                    item = ItemAppelKotlin(nom, cible.first, cible.second, signatureEcrite(appelable.typeRetourNomme)),
                    plagesAppel = plages,
                ),
            )
        }
        return aretes
    }

    // ------------------------------------------------------------------
    // codeLens — les occurrences paresseuses des déclarations top-level.
    // ------------------------------------------------------------------

    /**
     * Les LENTILLES du document (ADR 0136 : occurrences par re-parse
     * paresseux, périmètre borné) : chaque DÉCLARATION de premier niveau
     * (classe, fonction, propriété) porte le COMPTE de ses usages dans le
     * DOCUMENT (la preuve locale : les références du même nom dans le
     * texte — la preuve de LIAISON complète relève du moteur
     * d'occurrences, appelé pour le compte exact quand la position
     * l'exige ; ici le compte documentaire BORNÉ est la lentille
     * honnête).
     *
     * @param arbre l'arbre du document ;
     * @param uri l'uri du document ;
     * @return les lentilles (plage de déclaration, nom, compte) ; VIDE
     * sans déclaration top-level.
     */
    internal fun lentilles(arbre: KtFichier, uri: String): List<Triple<PlageTexte, String, Int>> {
        val rendues = mutableListOf<Triple<PlageTexte, String, Int>>()
        val noms = mutableListOf<Pair<PlageTexte, String>>()

        // Les déclarations de premier niveau : enfants directs du FILE.
        for (enfant in arbre.noeud.enfants) {
            when (enfant.genre) {
                KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION, KtNodeTypes.FUN, KtNodeTypes.PROPERTY -> {
                    val nom = enfant.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                    if (nom != null) {
                        val identifiant = enfant.jetonsPropres.firstOrNull {
                            it.genre == KtTokens.IDENTIFIER && it.texte == nom
                        }
                        if (identifiant != null) {
                            noms.add(Pair(PlageTexte(identifiant.debut, identifiant.fin), nom))
                        }
                    }
                }
                else -> Unit
            }
        }
        // Le compte documentaire de chaque nom : les références du CORPS
        // du document (excluant la déclaration elle-même).
        val texte = arbre.source
        for ((plage, nom) in noms) {
            val comptes = compterOccurrences(texte, nom)
            rendues.add(Triple(plage, nom, comptes))
        }
        return rendues
    }

    /** Le compte des occurrences d'un nom dans le texte (mot complet). */
    private fun compterOccurrences(texte: CharSequence, nom: String): Int {
        var compte = 0
        var indice = texte.indexOf(nom)
        while (indice >= 0) {
            val avant = if (indice == 0) ' ' else texte[indice - 1]
            val apres = if (indice + nom.length >= texte.length) ' ' else texte[indice + nom.length]
            val avantOk = !avant.isLetterOrDigit() && avant != '_'
            val apresOk = !apres.isLetterOrDigit() && apres != '_'
            if (avantOk && apresOk) compte++
            indice = texte.indexOf(nom, indice + nom.length)
        }
        return compte
    }

    // ------------------------------------------------------------------
    // Aides de preuve.
    // ------------------------------------------------------------------

    /** La référence la plus profonde couvrant la position. */
    private fun referenceCouvrante(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.REFERENCE_EXPRESSION) trouve = noeud
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /** La déclaration du type du nom simple : (uri, plage du NOM). */
    private fun declarationDuType(arbre: KtFichier, univers: UniversPreuves, nomSimple: String): Pair<String, PlageTexte>? {
        var declaration: Pair<String, PlageTexte>? = null
        fun marcher(noeud: NoeudArbre) {
            if ((noeud.genre == KtNodeTypes.CLASS || noeud.genre == KtNodeTypes.OBJECT_DECLARATION) &&
                noeud.jetonsPropres.any { it.genre == KtTokens.IDENTIFIER && it.texte == nomSimple }
            ) {
                val identifiant = noeud.jetonsPropres.firstOrNull {
                    it.genre == KtTokens.IDENTIFIER && it.texte == nomSimple
                }
                if (identifiant != null) {
                    declaration = Pair("", PlageTexte(identifiant.debut, identifiant.fin))
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(arbre.noeud)
        return declaration
    }

    /** La déclaration de l'appelable du nom : (uri, plage du NOM). */
    private fun declarationDeLappelable(arbre: KtFichier, univers: UniversPreuves, nom: String): Pair<String, PlageTexte>? {
        var declaration: Pair<String, PlageTexte>? = null
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.FUN &&
                noeud.jetonsPropres.any { it.genre == KtTokens.IDENTIFIER && it.texte == nom }
            ) {
                val identifiant = noeud.jetonsPropres.firstOrNull {
                    it.genre == KtTokens.IDENTIFIER && it.texte == nom
                }
                if (identifiant != null) {
                    declaration = Pair("", PlageTexte(identifiant.debut, identifiant.fin))
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(arbre.noeud)
        return declaration
    }

    /** La signature écrite d'un appelable (types écrits, sans variance). */
    private fun signatureEcrite(retour: String?): String = retour?.let { ": $it" }.orEmpty()

    /** Le nœud FUN déclarant l'appelable du nom (la déclaration ENTIÈRE). */
    private fun funDeclarante(arbre: KtFichier, nom: String): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.FUN &&
                noeud.jetonsPropres.any { it.genre == KtTokens.IDENTIFIER && it.texte == nom }
            ) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(arbre.noeud)
        return trouve
    }

    /**
     * Les arêtes d'appel du nom dans le document : les APPELANTS (les
     * déclarations de fonction dont le corps appelle le nom NU) — chaque
     * arête porte l'item de l'appelant et les plages des appels.
     */
    private fun appelsDuNom(
        arbre: KtFichier,
        univers: UniversPreuves,
        nom: String,
        hors: String?,
    ): List<AreteAppelKotlin> {
        val aretes = mutableListOf<AreteAppelKotlin>()
        // Les fonctions de premier niveau du document : leurs corps
        // portent-ils un appel NU du nom ?
        for (enfant in arbre.noeud.enfants) {
            if (enfant.genre != KtNodeTypes.FUN) continue
            val nomFonction = enfant.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
            if (nomFonction == null || nomFonction == hors) continue
            val plages = mutableListOf<PlageTexte>()
            marcher(enfant) { noeud ->
                if (noeud.genre == KtNodeTypes.CALL_EXPRESSION) {
                    val appelNom = noeud.enfants
                        .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                        ?.texteCanonique?.substringAfterLast('.')
                    if (appelNom == nom) {
                        plages.add(PlageTexte(noeud.debut, noeud.debut + nom.length))
                    }
                }
            }
            if (plages.isNotEmpty() && nomFonction.isNotEmpty()) {
                val identifiant = enfant.jetonsPropres.firstOrNull {
                    it.genre == KtTokens.IDENTIFIER && it.texte == nomFonction
                }
                if (identifiant != null) {
                    val appelable = univers.modele.appelables(nomFonction, univers.paquet).firstOrNull()
                    aretes.add(
                        AreteAppelKotlin(
                            item = ItemAppelKotlin(
                                nom = nomFonction,
                                uri = "",
                                plage = PlageTexte(identifiant.debut, identifiant.fin),
                                detail = signatureEcrite(appelable?.typeRetourNomme),
                            ),
                            plagesAppel = plages,
                        ),
                    )
                }
            }
        }
        return aretes
    }

    /** La marche avec visiteur sur un NŒUD donné. */
    private fun marcher(noeud: NoeudArbre, visiteur: (NoeudArbre) -> Unit) {
        visiteur(noeud)
        noeud.enfants.forEach { marcher(it, visiteur) }
    }
}

// CodeIDE — le GO-TO SUPER (module :kotlin, étage 4 vague 4c). Code NEUF
// jjoblab (clean-room — contrat : fiche 052 du cahier
// docs/cahier-specifications-kotlin/ : ADR 0210,
// `textDocument/superDefinition`, miroir des scénarios NavigationFacadeTest
// du LSP Java). Les DEUX chemins du picker : (1) MEMBRE au caret
// (déclaration outrepassante, ou usage RÉSOLU non ambigu — un homonyme
// ambigu n'est jamais présumé, borne O3) → le même-nommé dans chaque
// supertype TRANSITIF projet ; (2) sinon le type en contexte (référence
// de type, à défaut la classe englobante) → ses supertypes DIRECTS.
// Chaque supertype est prouvé PAR FQCN (les imports de SON fichier) —
// jamais par homonymie ; un supertype sans source projet est ABSENT.
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.CibleSuperKotlin
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.Couches
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.resolve.TypeProjet
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le moteur du GO-TO SUPER (étage 4 — fiche 052, ADR 0210) : les cibles
 * super du symbole sous le point.
 *
 * ## Le chemin MEMBRE
 *
 * Le caret désigne un membre — la DÉCLARATION OUTREPASSANTE (le site de
 * déclaration dans une classe) ou un USAGE RÉSOLU NON AMBIGU (un sélecteur
 * qualifié dont le nom vit dans EXACTEMENT UN type de la chaîne du
 * récepteur — deux homonymes dans la chaîne rendent le silence, borne O3 :
 * le moteur ne présage JAMAIS le membre d'un récepteur de type projet).
 * La cible est alors (le nom, le type déclarant) et le moteur rend le
 * MÊME-NOMMÉ dans chaque supertype TRANSITIF projet du déclarant —
 * `« run  ·  Base »`, triés par libellé.
 *
 * ## Le chemin TYPE (la retombée)
 *
 * Sans membre désigné — ou sans même-nommé dans les supertypes — le type
 * EN CONTEXTE : la référence de type sous le caret, à défaut la classe
 * ENGLOBANTE. Le moteur rend ses supertypes DIRECTS ÉCRITS, prouvés par
 * FQCN dans le fichier du type — `« Base  ·  demo »`. Un supertype
 * classpath sans source projet est ABSENT (garde D3 : jamais un faux
 * jalon).
 */
internal object NavigationSuper {

    // ------------------------------------------------------------------
    // L'entrée (appelée par la session, HORS verrou).
    // ------------------------------------------------------------------

    /**
     * Rend les CIBLES SUPER du symbole sous le point (fiche 052).
     *
     * @param document le document ouvert consulté ;
     * @param uri l'URI du document consulté ;
     * @param deplacement l'offset du caret ;
     * @param montage la matière de la preuve ;
     * @return les cibles (chemin membre : les même-nommés des supertypes
     * transitifs, triés par libellé ; retombée type : les supertypes
     * directs du type en contexte) ; liste vide sans preuve.
     */
    internal fun superDefinition(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        montage: OccurrencesProuvees.Montage,
    ): List<CibleSuperKotlin> {
        // 1. Le chemin MEMBRE : la déclaration outrepassante ou l'usage
        // résolu NON AMBIGU → le même-nommé dans chaque supertype transitif.
        val ciblesMembre = cheminMembre(document, uri, deplacement, montage)
        if (ciblesMembre.isNotEmpty()) return ciblesMembre

        // 2. La retombée TYPE : le type en contexte (référence de type, à
        // défaut la classe englobante) → ses supertypes DIRECTS écrits.
        return cheminType(document, uri, deplacement, montage)
    }

    // ------------------------------------------------------------------
    // Le chemin MEMBRE.
    // ------------------------------------------------------------------

    /**
     * Le chemin MEMBRE : la cible (nom, type déclarant) puis le
     * même-nommé dans chaque supertype TRANSITIF projet du déclarant.
     */
    private fun cheminMembre(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        montage: OccurrencesProuvees.Montage,
    ): List<CibleSuperKotlin> {
        val arbre = document.arbre
        val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()
        val jeton = jetonIdentifierA(arbre.noeud, deplacement) ?: return emptyList()

        // La DÉCLARATION au site : un MEMBRE (corps de classe ou propriété
        // de constructeur) — la déclaration OUTREPASSANTE désignée.
        val site = declarationMembreAuSite(arbre, jeton)
        if (site != null) {
            return ciblesMemeNomme(site.nom, site.fqcnDeclarant, montage)
        }

        // L'USAGE RÉSOLU NON AMBIGU : un sélecteur QUALIFIÉ dont le nom vit
        // dans EXACTEMENT UN type de la chaîne du récepteur (deux homonymes
        // = ambiguïté, borne O3 : le moteur ne présage jamais), ou un nom
        // NU dont la pile des this porte le membre avec la même unicité.
        val usage = usageMembreA(arbre, uri, jeton, montage) ?: return emptyList()
        return ciblesMemeNomme(usage.nom, usage.fqcnDeclarant, montage)
    }

    /** Un site de déclaration MEMBRE (nom + FQCN de la classe porteuse). */
    private class SiteMembre(
        internal val nom: String,
        internal val fqcnDeclarant: String,
    )

    /** La déclaration MEMBRE dont le NOM est sous le caret (ou `null`). */
    private fun declarationMembreAuSite(arbre: KtFichier, jeton: JetonBrut): SiteMembre? {
        var resultat: SiteMembre? = null
        var profondeur = Int.MAX_VALUE
        val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()

        fun marcher(noeud: NoeudArbre, chemin: List<String>, dansMembre: Boolean) {
            val jetonNom = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
            val estMembre = noeud.genre == KtNodeTypes.FUN ||
                noeud.genre == KtNodeTypes.PROPERTY ||
                noeud.genre == KtNodeTypes.ENUM_ENTRY
            if (estMembre && dansMembre && jetonNom != null &&
                jetonNom.debut == jeton.debut && jetonNom.fin == jeton.fin
            ) {
                val taille = noeud.fin - noeud.debut
                if (taille < profondeur) {
                    profondeur = taille
                    val fqcn = (listOf(paquet) + chemin).filter { it.isNotEmpty() }.joinToString(".")
                    if (fqcn.isNotEmpty()) {
                        resultat = SiteMembre(nom = jetonNom.texte, fqcnDeclarant = fqcn)
                    }
                }
            }
            when (noeud.genre) {
                KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION -> {
                    val nom = jetonNom?.texte ?: return
                    for (enfant in noeud.enfants) {
                        marcher(
                            enfant,
                            chemin + nom,
                            enfant.genre == KtNodeTypes.CLASS_BODY ||
                                enfant.genre == KtNodeTypes.PRIMARY_CONSTRUCTOR ||
                                enfant.genre == KtNodeTypes.VALUE_PARAMETER_LIST,
                        )
                    }
                }
                else -> {
                    for (enfant in noeud.enfants) {
                        // Un BLOC de fonction N'EST PAS un corps de classe :
                        // les locales d'une méthode ne sont pas des membres.
                        marcher(enfant, chemin, dansMembre && enfant.genre != KtNodeTypes.BLOCK)
                    }
                }
            }
        }
        for (enfant in arbre.noeud.enfants) marcher(enfant, emptyList(), false)
        return resultat
    }

    /** Un usage de membre résolu non ambigu (nom + FQCN du déclarant). */
    private class UsageMembre(
        internal val nom: String,
        internal val fqcnDeclarant: String,
    )

    /**
     * L'USAGE DE MEMBRE sous le caret, résolu NON AMBIGU : un sélecteur
     * QUALIFIÉ (`a.run()`) par le typage du récepteur, un nom NU par la
     * pile des this — le nom doit vivre dans EXACTEMENT UN type de la
     * chaîne du récepteur (deux homonymes = ambiguïté, borne O3).
     */
    private fun usageMembreA(
        arbre: KtFichier,
        uri: String,
        jeton: JetonBrut,
        montage: OccurrencesProuvees.Montage,
    ): UsageMembre? {
        val univers = montage.universDe(uri) ?: return null
        val nom = nomReferenceA(arbre.noeud, jeton) ?: return null

        // Le récepteur du sélecteur qualifié couvrant le jeton (ou `null`
        // pour un nom nu).
        val recepteur = recepteurDuSelecteur(arbre.noeud, jeton)
        val typeRecepteur: TypeProjet = when {
            recepteur != null -> {
                val liaison = montage.liaisonA(uri, recepteur.debut) ?: return null
                when (val type = TypageBorne.typeDeLexpression(recepteur, univers, liaison)) {
                    is TypeValeur.Projet -> type.type
                    else -> return null
                }
            }
            else -> {
                val liaison = montage.liaisonA(uri, jeton.debut) ?: return null
                val pile = Couches.aPosition(arbre, univers, liaison, jeton.debut)
                val proprietaire = pile.membre(nom)?.proprietaireFqcn ?: return null
                montage.modele.type(proprietaire) ?: return null
            }
        }

        // La garde d'AMBIGUÏTÉ (borne O3) : le nom doit vivre dans
        // EXACTEMENT UN type de la chaîne du récepteur — deux homonymes
        // (un override ET sa déclaration) rendent le silence : le moteur
        // ne présage JAMAIS le membre d'un récepteur de type projet.
        val chaine = chaineTransitive(typeRecepteur, montage)
        val porteurs = chaine.filter { type -> type.membres.any { it.nom == nom } }
        if (porteurs.size != 1) return null
        return UsageMembre(nom = nom, fqcnDeclarant = porteurs.single().fqcn)
    }

    /** Le RÉCEPTEUR du sélecteur qualifié couvrant le jeton (ou `null`). */
    private fun recepteurDuSelecteur(racine: NoeudArbre, jeton: JetonBrut): NoeudArbre? {
        var recepteur: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION ||
                noeud.genre == KtNodeTypes.SAFE_ACCESS_EXPRESSION
            ) {
                val rec = noeud.enfants.firstOrNull()
                val sel = noeud.enfants.lastOrNull()
                if (rec != null && sel != null && rec !== sel && sel.debut <= jeton.debut && jeton.fin <= sel.fin) {
                    recepteur = rec
                }
            }
            for (enfant in noeud.enfants) marcher(enfant)
        }
        marcher(racine)
        return recepteur
    }

    /**
     * Les cibles du MÊME-NOMMÉ : chaque supertype TRANSITIF projet du
     * déclarant qui DÉCLARE un membre du même nom — `« nom  ·  Simple »`,
     * triées par libellé (l'ordre du picker).
     */
    private fun ciblesMemeNomme(
        nom: String,
        fqcnDeclarant: String,
        montage: OccurrencesProuvees.Montage,
    ): List<CibleSuperKotlin> {
        val declarant = montage.modele.type(fqcnDeclarant) ?: return emptyList()
        val resultat = mutableListOf<CibleSuperKotlin>()
        for (superType in chaineTransitive(declarant, montage).drop(1)) {
            if (superType.membres.none { it.nom == nom }) continue
            val uriSuper = superType.uri ?: continue
            val arbreSuper = montage.arbreDe(uriSuper) ?: continue
            val paquetSuper = arbreSuper.directivePackage()?.nomQualifie().orEmpty()
            val plage = OccurrencesProuvees.siteMembreDe(arbreSuper, paquetSuper, superType.fqcn, nom)
                ?: continue
            resultat.add(
                CibleSuperKotlin(
                    uri = uriSuper,
                    libelle = "$nom  ·  ${superType.nomSimple}",
                    plage = plage,
                ),
            )
        }
        return resultat.sortedBy { it.libelle }
    }

    // ------------------------------------------------------------------
    // Le chemin TYPE (la retombée).
    // ------------------------------------------------------------------

    /**
     * Le chemin TYPE : le type EN CONTEXTE (la référence de type sous le
     * caret, à défaut la classe ENGLOBANTE) → ses supertypes DIRECTS
     * ÉCRITS, prouvés par FQCN dans le fichier du type — `« Simple  ·
     * paquet »`.
     */
    private fun cheminType(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        montage: OccurrencesProuvees.Montage,
    ): List<CibleSuperKotlin> {
        val arbre = document.arbre
        val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()
        val jeton = jetonIdentifierA(arbre.noeud, deplacement) ?: return emptyList()

        // Le type en contexte : la référence de TYPE sous le caret (ou le
        // nom d'une DÉCLARATION DE TYPE — le type lui-même), à défaut la
        // classe ENGLOBANTE du caret.
        val typeContexte = typeEnContexte(arbre, paquet, jeton, montage)
            ?: classeEnglobante(arbre, paquet, deplacement, montage)
            ?: return emptyList()

        // Ses supertypes DIRECTS ÉCRITS, résolus PAR FQCN dans le fichier
        // DU TYPE (les imports de SON fichier — jamais par homonymie).
        val uriType = typeContexte.uri ?: return emptyList()
        val arbreType = montage.arbreDe(uriType) ?: return emptyList()
        val paquetType = arbreType.directivePackage()?.nomQualifie().orEmpty()
        val noeudType = typeNoeudDe(arbreType, typeContexte, paquetType) ?: return emptyList()
        val resultat = mutableListOf<CibleSuperKotlin>()
        for (ecrit in ModeleProjet.supertypesEcrits(noeudType)) {
            val superType = NavigationTypes.typeDuNomEcrit(ecrit, arbreType, paquetType, montage)
                ?: continue
            val uriSuper = superType.uri ?: continue
            val arbreSuper = montage.arbreDe(uriSuper) ?: continue
            val paquetSuper = arbreSuper.directivePackage()?.nomQualifie().orEmpty()
            val plage = NavigationTypes.plageNomDuType(arbreSuper, paquetSuper, superType.fqcn)
                ?: continue
            resultat.add(
                CibleSuperKotlin(
                    uri = uriSuper,
                    libelle = "${superType.nomSimple}  ·  $paquetSuper",
                    plage = plage,
                ),
            )
        }
        return resultat
    }

    /**
     * Le TYPE EN CONTEXTE sous le caret : une référence de TYPE (le jeton
     * vit dans une TYPE_REFERENCE) ou le nom d'une déclaration de type —
     * résolu par les imports du fichier du curseur ; `null` sinon.
     */
    private fun typeEnContexte(
        arbre: KtFichier,
        paquet: String,
        jeton: JetonBrut,
        montage: OccurrencesProuvees.Montage,
    ): TypeProjet? {
        val dansTypeReference = referenceDeTypeA(arbre.noeud, jeton)
        if (dansTypeReference) {
            val nom = nomReferenceA(arbre.noeud, jeton) ?: return null
            return NavigationTypes.typeDuNomEcrit(nom, arbre, paquet, montage)
        }
        // Le nom d'une DÉCLARATION DE TYPE : le type lui-même.
        val declaration = declarationTypeAuSite(arbre, jeton)
        if (declaration != null) {
            return NavigationTypes.typeDuNomEcrit(declaration, arbre, paquet, montage)
        }
        return null
    }

    /** La référence sous le jeton vit-elle dans une TYPE_REFERENCE ? */
    private fun referenceDeTypeA(racine: NoeudArbre, jeton: JetonBrut): Boolean {
        var trouve = false
        fun marcher(noeud: NoeudArbre, dansType: Boolean) {
            if (jeton.debut < noeud.debut || jeton.fin > noeud.fin) return
            if (dansType && noeud.genre == KtNodeTypes.REFERENCE_EXPRESSION &&
                noeud.debut == jeton.debut && noeud.fin == jeton.fin
            ) {
                trouve = true
            }
            for (enfant in noeud.enfants) {
                marcher(enfant, dansType || enfant.genre == KtNodeTypes.TYPE_REFERENCE)
            }
        }
        marcher(racine, false)
        return trouve
    }

    /** Le nom d'une DÉCLARATION DE TYPE sous le jeton (ou `null`). */
    private fun declarationTypeAuSite(arbre: KtFichier, jeton: JetonBrut): String? {
        var nom: String? = null
        var profondeur = Int.MAX_VALUE
        fun marcher(noeud: NoeudArbre) {
            when (noeud.genre) {
                KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION, KtNodeTypes.TYPEALIAS -> {
                    val jetonNom = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
                    if (jetonNom != null && jetonNom.debut == jeton.debut && jetonNom.fin == jeton.fin) {
                        val taille = noeud.fin - noeud.debut
                        if (taille < profondeur) {
                            profondeur = taille
                            nom = jetonNom.texte
                        }
                    }
                }
                else -> Unit
            }
            for (enfant in noeud.enfants) marcher(enfant)
        }
        marcher(arbre.noeud)
        return nom
    }

    /**
     * La CLASSE ENGLOBANTE du caret : le type déclaré le plus profond
     * couvrant la position, résolu dans le modèle par son FQCN.
     */
    private fun classeEnglobante(
        arbre: KtFichier,
        paquet: String,
        deplacement: Int,
        montage: OccurrencesProuvees.Montage,
    ): TypeProjet? {
        var fqcn: String? = null
        fun marcher(noeud: NoeudArbre, chemin: List<String>) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            when (noeud.genre) {
                KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION -> {
                    val nom = noeud.jetonsPropres
                        .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte ?: return
                    fqcn = (listOf(paquet) + chemin + nom)
                        .filter { it.isNotEmpty() }
                        .joinToString(".")
                    for (enfant in noeud.enfants) {
                        if (enfant.genre == KtNodeTypes.CLASS_BODY) marcher(enfant, chemin + nom)
                    }
                }
                else -> {
                    for (enfant in noeud.enfants) marcher(enfant, chemin)
                }
            }
        }
        marcher(arbre.noeud, emptyList())
        return fqcn?.let { montage.modele.type(it) }
    }

    /**
     * Le NŒUD de déclaration du type dans SON arbre (pour lire ses
     * supertypes ÉCRITS) — recherché par FQCN.
     */
    private fun typeNoeudDe(arbre: KtFichier, type: TypeProjet, paquet: String): NoeudArbre? {
        val suffixe = if (paquet.isEmpty()) type.fqcn else type.fqcn.removePrefix("$paquet.")
        val segments = suffixe.split('.').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        var resultat: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre, cheminCourant: List<String>) {
            when (noeud.genre) {
                KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION -> {
                    val nomDecl = noeud.jetonsPropres
                        .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte ?: return
                    if (cheminCourant + nomDecl == segments) {
                        resultat = noeud
                        return
                    }
                    for (enfant in noeud.enfants) {
                        if (enfant.genre == KtNodeTypes.CLASS_BODY) marcher(enfant, cheminCourant + nomDecl)
                    }
                }
                else -> {
                    for (enfant in noeud.enfants) marcher(enfant, cheminCourant)
                }
            }
        }
        marcher(arbre.noeud, emptyList())
        return resultat
    }

    // ------------------------------------------------------------------
    // Les aides.
    // ------------------------------------------------------------------

    /**
     * La CHAÎNE TRANSITIVE des supertypes PROJET d'un type : le type
     * lui-même puis chaque supertype ÉCRIT résolu PAR FQCN dans le
     * fichier de SON sous-type (garde de cycle, ordre de fermeture —
     * les maillons classpath sont ABSENTS, jamais devinés).
     */
    private fun chaineTransitive(type: TypeProjet, montage: OccurrencesProuvees.Montage): List<TypeProjet> {
        val chaine = mutableListOf<TypeProjet>()
        val visites = HashSet<String>()
        fun fermer(courant: TypeProjet) {
            if (!visites.add(courant.fqcn)) return
            chaine.add(courant)
            val uriCourant = courant.uri ?: return
            val arbreCourant = montage.arbreDe(uriCourant) ?: return
            val paquetCourant = arbreCourant.directivePackage()?.nomQualifie().orEmpty()
            val noeud = typeNoeudDe(arbreCourant, courant, paquetCourant) ?: return
            for (ecrit in ModeleProjet.supertypesEcrits(noeud)) {
                val superType = NavigationTypes.typeDuNomEcrit(ecrit, arbreCourant, paquetCourant, montage)
                if (superType != null) fermer(superType)
            }
        }
        fermer(type)
        return chaine
    }

    /**
     * Le NOM de la référence qui couvre le jeton (le texte canonique de
     * la production de référence couvrante).
     */
    private fun nomReferenceA(racine: NoeudArbre, jeton: JetonBrut): String? {
        var nom: String? = null
        fun marcher(noeud: NoeudArbre) {
            if (jeton.debut < noeud.debut || jeton.fin > noeud.fin) return
            if (noeud.genre == KtNodeTypes.REFERENCE_EXPRESSION &&
                noeud.debut == jeton.debut && noeud.fin == jeton.fin
            ) {
                nom = noeud.texteCanonique.ifEmpty { null }
            }
            for (enfant in noeud.enfants) marcher(enfant)
        }
        marcher(racine)
        return nom
    }

    /** Le jeton IDENTIFIER qui contient l'offset du caret (ou `null`). */
    private fun jetonIdentifierA(racine: NoeudArbre, deplacement: Int): JetonBrut? {
        var trouve: JetonBrut? = null
        fun marcher(noeud: NoeudArbre) {
            for (jeton in noeud.jetonsPropres) {
                if (jeton.genre == KtTokens.IDENTIFIER && jeton.debut <= deplacement && deplacement < jeton.fin) {
                    trouve = jeton
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }
}

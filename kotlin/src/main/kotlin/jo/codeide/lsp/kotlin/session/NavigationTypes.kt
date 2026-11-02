// CodeIDE — navigation de TYPES (module :kotlin, étage 4 vague 4b). Code
// NEUF jjoblab (clean-room — contrat : fiche 053 du cahier
// docs/cahier-specifications-kotlin/ : `textDocument/typeDefinition` et
// `textDocument/implementation` L2, plan 1.3.0 — la PREUVE PAR FQCN : une
// cible n'est servie que si son nom résout par les IMPORTS du fichier du
// curseur (ou par le fichier lui-même, ou par unicité projet) ; un
// héritier n'est listé que si son supertype ÉCRIT résout par les imports
// de SON PROPRE fichier — jamais par homonymie de nom simple).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.EmplacementKotlin
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.resolve.TypeProjet
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le moteur de NAVIGATION DE TYPES (étage 4 — fiche 053) : la définition
 * du type sous le point et ses implémentations — les héritiers PROUVÉS du
 * projet, multi-fichiers (document courant en tête, fichiers fermés du
 * scan ensuite).
 *
 * ## typeDefinition — la déclaration du type désigné
 *
 * - le caret sur une RÉFÉRENCE DE TYPE (`param: Base`, la clause
 *   `class D : Base()`, le type de retour) : le nom écrit résout vers la
 *   DÉCLARATION du type du projet ;
 * - le caret sur le NOM d'une PROPRIÉTÉ ou d'un PARAMÈTRE typé : son type
 *   ÉCRIT résout pareillement (le type inféré d'un initialiseur n'est pas
 *   de cette vague — conservateur, D3) ;
 * - le caret sur un APPEL DE CONSTRUCTEUR (`Base()`) : la classe
 *   construite.
 *
 * ## implementations — la preuve par FQCN
 *
 * Un type déclaré H est un héritier de la cible si un supertype ÉCRIT de
 * H résout — par les IMPORTS du fichier de H d'abord, puis son paquet
 * courant, puis l'unicité projet — vers le FQCN de la cible. Deux `Base`
 * de paquets distincts ne se confondent JAMAIS ; l'incertain rend VIDE.
 *
 * Le montage (documents ouverts + fichiers du scan re-parsés
 * paresseusement) est celui des occurrences prouvées —
 * [OccurrencesProuvees.Montage].
 */
internal object NavigationTypes {

    // ------------------------------------------------------------------
    // Les entrées (appelées par la session, HORS verrou).
    // ------------------------------------------------------------------

    /**
     * Rend les DÉFINITIONS DE TYPE du symbole sous le point (fiche 053).
     *
     * @param document le document ouvert consulté ;
     * @param uri l'URI du document consulté ;
     * @param deplacement l'offset du caret ;
     * @param montage la matière de la preuve ;
     * @return l'emplacement de la déclaration du type visé ; liste vide
     * sans type projet prouvable (classpath, littéral, position vide).
     */
    internal fun typeDefinition(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        montage: OccurrencesProuvees.Montage,
    ): List<EmplacementKotlin> {
        val type = typeProjetSousPoint(document, uri, deplacement, montage) ?: return emptyList()
        val arbreDeclarant = montage.arbreDe(type.uri ?: return emptyList()) ?: return emptyList()
        val paquetDeclarant = arbreDeclarant.directivePackage()?.nomQualifie().orEmpty()
        val plage = plageNomDuType(arbreDeclarant, paquetDeclarant, type.fqcn) ?: return emptyList()
        return listOf(EmplacementKotlin(uri = type.uri ?: return emptyList(), plage = plage))
    }

    /**
     * Rend les IMPLÉMENTATIONS du type sous le point (fiche 053) : les
     * héritiers PROUVÉS — document courant en tête (ordre du document),
     * puis les fichiers fermés du scan (ordre de marche).
     *
     * @param document le document ouvert consulté ;
     * @param uri l'URI du document consulté ;
     * @param deplacement l'offset du caret (sur le supertype écrit ou le
     * nom du type) ;
     * @param montage la matière de la preuve ;
     * @return les emplacements des déclarations héritières ; liste vide
     * sans type prouvé.
     */
    internal fun implementations(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        montage: OccurrencesProuvees.Montage,
    ): List<EmplacementKotlin> {
        val cible = typeProjetSousPoint(document, uri, deplacement, montage) ?: return emptyList()
        val resultat = mutableListOf<EmplacementKotlin>()
        for (uriFichier in montage.fichiersParContenu(cible.nomSimple, uri)) {
            val arbre = montage.arbreDe(uriFichier) ?: continue
            val paquetF = arbre.directivePackage()?.nomQualifie().orEmpty()
            for (typeDecl in typesDeclares(arbre)) {
                val estHeritier = typeDecl.supertypesEcrits.any { ecrit ->
                    val resolu = typeDuNomEcrit(ecrit, arbre, paquetF, montage)
                    resolu != null && resolu.fqcn == cible.fqcn
                }
                if (estHeritier) {
                    resultat.add(EmplacementKotlin(uri = uriFichier, plage = typeDecl.plageNom))
                }
            }
        }
        return resultat
    }

    // ------------------------------------------------------------------
    // Le type projet sous le point.
    // ------------------------------------------------------------------

    /**
     * Le TYPE DU PROJET désigné sous le point : la référence de type
     * visée, le type ÉCRIT de la déclaration nommée (propriété,
     * paramètre), la classe CONSTRUITE d'un appel — résolu par les
     * imports du fichier, le fichier lui-même, puis l'unicité projet.
     *
     * @param document le document consulté ;
     * @param uri l'URI du document ;
     * @param deplacement l'offset du caret ;
     * @param montage la matière de la preuve ;
     * @return le type du projet ; `null` sans preuve (classpath, littéral,
     * position vide — jamais deviné, D3).
     */
    private fun typeProjetSousPoint(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        montage: OccurrencesProuvees.Montage,
    ): TypeProjet? {
        val arbre = document.arbre
        val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()
        val jeton = jetonIdentifierA(arbre.noeud, deplacement) ?: return null

        // 1. Le NOM D'UNE DÉCLARATION typée : son type ÉCRIT (une
        // propriété, un paramètre — le type inféré d'un initialiseur
        // n'est pas de cette vague, D3) ; le nom d'un TYPE désigne
        // lui-même.
        val declaration = declarationTyppeeAuSite(arbre, jeton)
        if (declaration != null) {
            return when {
                declaration.estType -> typeDuNomEcrit(declaration.nom, arbre, paquet, montage)
                declaration.nomEcritType != null ->
                    typeDuNomEcrit(declaration.nomEcritType!!, arbre, paquet, montage)
                else -> null
            }
        }

        // 2. Une RÉFÉRENCE sous le caret : le nom écrit (référence de
        // type, callee de constructeur — le nom d'une expression ne
        // désigne un type que s'il RÉSOUT en type du projet, ce que la
        // résolution par nom simple tranche honnêtement).
        val nom = nomReferenceA(arbre.noeud, jeton) ?: return null
        return typeDuNomEcrit(nom, arbre, paquet, montage)
    }

    /**
     * La déclaration TYPAÉE dont le NOM est sous le caret (propriété avec
     * type écrit, paramètre avec type écrit, type déclaré) — la plus
     * profonde dont le nom couvre exactement le jeton.
     */
    private class DeclarationTyppee(
        internal val estType: Boolean,
        internal val nom: String,
        internal val nomEcritType: String?,
    )

    /** La déclaration typée au site du jeton (ou `null`). */
    private fun declarationTyppeeAuSite(arbre: KtFichier, jeton: JetonBrut): DeclarationTyppee? {
        var resultat: DeclarationTyppee? = null
        var profondeur = Int.MAX_VALUE

        fun nomDe(noeud: NoeudArbre): String? =
            noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte

        fun typeEcritDe(noeud: NoeudArbre): String? =
            noeud.enfants
                .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                ?.texteCanonique?.takeIf { it.isNotEmpty() }

        fun marcher(noeud: NoeudArbre) {
            when (noeud.genre) {
                KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION, KtNodeTypes.TYPEALIAS -> {
                    val jetonNom = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
                    if (jetonNom != null && jetonNom.debut == jeton.debut && jetonNom.fin == jeton.fin) {
                        val taille = noeud.fin - noeud.debut
                        if (taille < profondeur) {
                            profondeur = taille
                            resultat = DeclarationTyppee(estType = true, nom = jetonNom.texte, nomEcritType = null)
                        }
                    }
                }
                KtNodeTypes.PROPERTY, KtNodeTypes.VALUE_PARAMETER -> {
                    val jetonNom = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
                    if (jetonNom != null && jetonNom.debut == jeton.debut && jetonNom.fin == jeton.fin) {
                        val taille = noeud.fin - noeud.debut
                        if (taille < profondeur) {
                            profondeur = taille
                            resultat = DeclarationTyppee(
                                estType = false,
                                nom = jetonNom.texte,
                                nomEcritType = typeEcritDe(noeud),
                            )
                        }
                    }
                }
                else -> Unit
            }
            for (enfant in noeud.enfants) marcher(enfant)
        }
        marcher(arbre.noeud)
        return resultat
    }

    /**
     * Le NOM de la référence qui couvre le jeton (référence de type dans
     * une TYPE_REFERENCE, callee d'un appel de constructeur, référence
     * d'expression) — le texte canonique de la plus profonde production
     * de référence couvrante.
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

    // ------------------------------------------------------------------
    // La résolution d'un nom de type ÉCRIT dans un fichier.
    // ------------------------------------------------------------------

    /**
     * Le TYPE DU PROJET désigné par un nom ÉCRIT dans un fichier : les
     * imports EXACTS du fichier D'ABORD (l'import prime le paquet courant
     * — sémantique du compilateur), puis le fichier LUI-MÊME (un type
     * déclaré du même nom y prime), puis le paquet courant, puis
     * l'unicité projet. Un nom qualifié (`o.Base`) se résout par FQCN
     * direct du modèle.
     *
     * @param nomEcrit le nom écrit (`Base`, `o.Base`) ;
     * @param arbreF l'arbre du fichier de l'écrit ;
     * @param paquetF le paquet du fichier de l'écrit ;
     * @param montage la matière (le modèle du projet) ;
     * @return le type du projet ; `null` si le nom désigne le classpath
     * ou rien (jamais deviné, D3).
     */
    internal fun typeDuNomEcrit(
        nomEcrit: String,
        arbreF: KtFichier,
        paquetF: String,
        montage: OccurrencesProuvees.Montage,
    ): TypeProjet? {
        val nom = nomEcrit.trim()
        if (nom.isEmpty()) return null

        // Un nom QUALIFIÉ : le FQCN direct du modèle.
        if ('.' in nom) return montage.modele.type(nom)

        // 1. Les imports EXACTS du fichier (l'import PRIME le paquet —
        // `package z ; import o.Base` y désigne o.Base).
        for (directive in arbreF.imports()) {
            if (directive.estJoker()) continue
            val chemin = directive.nomQualifie() ?: continue
            if (chemin.substringAfterLast('.') != nom && directive.alias() != nom) continue
            montage.modele.type(chemin)?.let { return it }
        }

        // 2. Le fichier LUI-MÊME : un type déclaré du même nom y prime.
        for (typeDecl in typesDeclares(arbreF)) {
            if (typeDecl.nomSimple != nom) continue
            val fqcn = (listOf(paquetF) + typeDecl.chemin + nom)
                .filter { it.isNotEmpty() }
                .joinToString(".")
            montage.modele.type(fqcn)?.let { return it }
        }

        // 3. Le paquet courant, puis l'unicité projet (le modèle tranche).
        return montage.modele.typeParNomSimple(nom, paquetF)
    }

    // ------------------------------------------------------------------
    // Les types déclarés d'un document.
    // ------------------------------------------------------------------

    /** Un type déclaré : sa plage de nom, son nom, son chemin, ses clauses. */
    private class TypeDecl(
        internal val plageNom: PlageTexte,
        internal val nomSimple: String,
        internal val chemin: List<String>,
        internal val supertypesEcrits: List<String>,
    )

    /**
     * Les TYPES DÉCLARÉS d'un document (top-level et imbriqués), en ordre
     * du document, avec leurs clauses de supertypes ÉCRITES.
     */
    private fun typesDeclares(arbre: KtFichier): List<TypeDecl> {
        val resultat = mutableListOf<TypeDecl>()

        fun nomDe(noeud: NoeudArbre): String? =
            noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte

        fun marcher(noeud: NoeudArbre, chemin: List<String>) {
            when (noeud.genre) {
                KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION -> {
                    val nom = nomDe(noeud) ?: return
                    val jeton = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
                    if (jeton != null) {
                        resultat.add(
                            TypeDecl(
                                plageNom = PlageTexte(jeton.debut, jeton.fin),
                                nomSimple = nom,
                                chemin = chemin,
                                supertypesEcrits = ModeleProjet.supertypesEcrits(noeud),
                            ),
                        )
                    }
                    for (enfant in noeud.enfants) {
                        if (enfant.genre == KtNodeTypes.CLASS_BODY) marcher(enfant, chemin + nom)
                    }
                }
                else -> {
                    for (enfant in noeud.enfants) marcher(enfant, chemin)
                }
            }
        }
        for (enfant in arbre.noeud.enfants) marcher(enfant, emptyList())
        return resultat
    }

    /**
     * La PLAGE du NOM du type déclaré par FQCN dans SON document (le
     * paquet du document est CONNU — le suffixe du FQCN se décompose en
     * chemin des englobants + nom, miroir du modèle).
     */
    internal fun plageNomDuType(arbre: KtFichier, paquet: String, fqcn: String): PlageTexte? {
        val suffixe = if (paquet.isEmpty()) fqcn else fqcn.removePrefix("$paquet.")
        if (suffixe == fqcn && paquet.isNotEmpty() && fqcn != paquet) return null
        val segments = suffixe.split('.').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val nom = segments.last()
        val chemin = segments.dropLast(1)
        for (typeDecl in typesDeclares(arbre)) {
            if (typeDecl.nomSimple == nom && typeDecl.chemin == chemin) return typeDecl.plageNom
        }
        return null
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

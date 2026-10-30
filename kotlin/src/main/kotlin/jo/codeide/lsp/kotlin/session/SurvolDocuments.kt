// CodeIDE — survol S2 d'un document Kotlin (module :kotlin, étage 1 vague
// 1b). Code NEUF jjoblab (clean-room — contrat : fiche 049 du cahier : « la
// KDoc du document courant accompagne la signature (S2) », « survol sans
// KDoc : documentation absente, signature seule », « rafraichirSources : une
// déclaration externe devient resolvable (L1) »). Chaîne conservatrice :
// document courant → documents ouverts → scan des racines.
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.SurvolKotlin
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.FabriqueKt
import jo.codeide.lsp.kotlin.parse.kt.KtClasse
import jo.codeide.lsp.kotlin.parse.kt.KtDeclaration
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.KtFonction
import jo.codeide.lsp.kotlin.parse.kt.KtPropriete
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Calcul du survol d'un point du document (pur — aucun état, appelé sous
 * verrou de lecture).
 *
 * ## Chaîne conservatrice (fiche 049)
 *
 * Le caret désigne un IDENTIFIANT (site de déclaration ou référence) ; la
 * déclaration visée se cherche par :
 * 1. le document COURANT — la déclaration dont le jeton nom est désigné,
 *    puis la première déclaration portant ce nom (ord re du document) ;
 * 2. les AUTRES documents ouverts de la session (ordre d'ouverture) ;
 * 3. les déclarations EXTERNES du scan des racines (après
 *    [SessionLangageKotlin.rafraichirSources]).
 *
 * Introuvable ⇒ `null` (silence honnête D3). La signature rendue est la
 * forme écrite conservatrice de la déclaration ; la documentation est le
 * KDoc qui la précède IMMÉDIATEMENT (un commentaire simple ne compte pas).
 */
internal object SurvolDocuments {

    /**
     * Rend le survol au point demandé.
     *
     * @param cible le document ouvert visé par la requête ;
     * @param autres les autres documents ouverts de la session (ordre
     * d'ouverture — les documents ouverts dominent le scan) ;
     * @param scan le scan des racines publié (L1) — `null` tant qu'aucun
     * rafraichirSources n'a abouti ;
     * @param deplacement l'offset du caret dans le texte du document ;
     * @return le survol (signature + KDoc éventuelle) ; `null` si le caret
     * ne désigne aucun identifiant résolvable.
     */
    internal fun survol(
        cible: DocumentOuvert,
        autres: List<DocumentOuvert>,
        scan: ScanSources?,
        deplacement: Int,
    ): SurvolKotlin? {
        val identifiant = jetonIdentifierA(cible.arbre.noeud, deplacement) ?: return null
        val nom = identifiant.texte

        // 1. Document courant : site de déclaration, puis première
        // déclaration du même nom (ordre du document, récursif) — une
        // déclaration tronquée ne se rend pas, la chaîne CONTINUE.
        val declarationsCourantes = declarationsRecursives(cible.arbre)
        val auSite = declarationsCourantes.firstOrNull { declaration ->
            jetonNom(declaration)?.let { it.debut == identifiant.debut && it.fin == identifiant.fin } == true
        }
        rendu(auSite, cible.arbre.noeud.commentaires)?.let { return it }
        val courante = declarationsCourantes.firstOrNull { it.nom() == nom }
        rendu(courante, cible.arbre.noeud.commentaires)?.let { return it }

        // 2. Autres documents ouverts (ordre d'ouverture).
        for (document in autres) {
            val declaration = declarationsRecursives(document.arbre).firstOrNull { it.nom() == nom }
            rendu(declaration, document.arbre.noeud.commentaires)?.let { return it }
        }

        // 3. Déclarations externes du scan (L1).
        val externe = scan?.declaration(nom) ?: return null
        return SurvolKotlin(signature = externe.signature, documentation = externe.documentation)
    }

    // ------------------------------------------------------------------
    // Rendu : signature et documentation.
    // ------------------------------------------------------------------

    /**
     * Rend le survol d'une déclaration IN-VIVO (document ouvert) : signature
     * + KDoc qui précède.
     *
     * @param declaration la déclaration visée (`null` admis — rend `null`) ;
     * @param commentaires les jetons de commentaire du document la portant ;
     * @return le survol ; `null` si la déclaration est absente ou tronquée
     * sans nom (conservateur — elle ne se rend pas).
     */
    private fun rendu(declaration: KtDeclaration?, commentaires: List<JetonBrut>): SurvolKotlin? {
        val signature = renduSignature(declaration ?: return null) ?: return null
        val documentation = kdocPrecedente(declaration, commentaires)
        return SurvolKotlin(signature = signature, documentation = documentation)
    }

    /**
     * Rend la SIGNATURE écrite conservatrice d'une déclaration : la forme
     * lisible de son en-tête (corps et initialiseurs exclus).
     *
     * @param declaration la déclaration à rendre ;
     * @return la signature (par ex. `fun saluer(destinataire: String):
     * String`, `class Serviteur`, `val nom: String`) ; `null` si la
     * déclaration est tronquée sans nom.
     */
    internal fun renduSignature(declaration: KtDeclaration): String? {
        val nom = declaration.nom() ?: return null
        return when (declaration) {
            is KtFonction -> renduFonction(declaration, nom)
            is KtPropriete -> renduPropriete(declaration, nom)
            is KtClasse -> renduClasse(declaration, nom)
            else -> nom
        }
    }

    /**
     * Rend la signature d'une fonction : récepteur éventuel, paramètres et
     * type de retour écrits.
     *
     * @param fonction la fonction à rendre ;
     * @param nom le nom déjà lu de la fonction ;
     * @return par ex. `fun Int.double(): Int`.
     */
    private fun renduFonction(fonction: KtFonction, nom: String): String {
        val enfants = fonction.noeud.enfants
        val listeParametres = enfants.firstOrNull { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
        val recepteur = enfants
            .takeWhile { it.genre != KtNodeTypes.VALUE_PARAMETER_LIST }
            .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
        val retour = enfants
            .dropWhile { it.genre != KtNodeTypes.VALUE_PARAMETER_LIST }
            .drop(1)
            .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }

        val parametres = listeParametres?.enfants
            ?.filter { it.genre == KtNodeTypes.VALUE_PARAMETER }
            ?.joinToString(", ") { parametre ->
                val nomParametre = parametre.jetonsPropres
                    .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte.orEmpty()
                val type = parametre.enfants
                    .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }?.texteCanonique
                if (type == null) nomParametre else "$nomParametre: $type"
            }.orEmpty()

        val construction = StringBuilder("fun ")
        if (recepteur != null) construction.append(recepteur.texteCanonique).append('.')
        construction.append(nom).append('(').append(parametres).append(')')
        if (retour != null) construction.append(": ").append(retour.texteCanonique)
        return construction.toString()
    }

    /**
     * Rend la signature d'une propriété : mot-clé, nom et type écrit.
     *
     * @param propriete la propriété à rendre ;
     * @param nom le nom déjà lu de la propriété ;
     * @return par ex. `val nom: String` (type absent si non écrit).
     */
    private fun renduPropriete(propriete: KtPropriete, nom: String): String {
        val motCle = propriete.noeud.jetonsPropres
            .firstOrNull { it.genre == KtTokens.VAL_KEYWORD || it.genre == KtTokens.VAR_KEYWORD }
            ?.texte ?: "val"
        val type = propriete.noeud.enfants
            .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }?.texteCanonique
        return if (type == null) "$motCle $nom" else "$motCle $nom: $type"
    }

    /**
     * Rend la signature d'une déclaration de type : genre fin, nom et
     * supertypes écrits.
     *
     * @param classe la déclaration de type à rendre ;
     * @param nom le nom déjà lu de la déclaration ;
     * @return par ex. `class A : B`, `interface Service`, `object Unique`.
     */
    private fun renduClasse(classe: KtClasse, nom: String): String {
        val genre = when (classe.genreClasse) {
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.CLASSE -> "class"
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.INTERFACE -> "interface"
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.ENUM -> "enum class"
            jo.codeide.lsp.kotlin.parse.kt.KtGenreClasse.OBJET -> "object"
        }
        val supertypes = classe.noeud.enfants
            .firstOrNull { it.genre == KtNodeTypes.SUPER_TYPE_LIST }?.texteCanonique
        return if (supertypes == null || supertypes.isEmpty()) "$genre $nom" else "$genre $nom : $supertypes"
    }

    /**
     * Extrait la documentation KDoc qui précède IMMÉDIATEMENT la déclaration
     * (des blancs seuls séparent) — un commentaire simple ou un commentaire
     * bloc non-KDoc ne compte pas.
     *
     * Le point de départ est le PREMIER JETON PROPRE de la déclaration : la
     * politique de liaison de la grammaire attache les trivia de marge aux
     * plages des productions (sonde : le KDoc précédent fait partie de la
     * plage de la déclaration) — chercher avant la plage inclurait le KDoc
     * dans la déclaration elle-même et ne le trouverait jamais.
     *
     * @param declaration la déclaration visée ;
     * @param commentaires les jetons de commentaire du document (ordre du
     * document) ;
     * @return le corps du KDoc, marqueurs et astérisques de marge retirés ;
     * `null` si aucun KDoc ne précède immédiatement la déclaration.
     */
    internal fun kdocPrecedente(declaration: KtDeclaration, commentaires: List<JetonBrut>): String? {
        // Début SIGNIFICATIF de la déclaration : le premier jeton propre
        // (commentaires/blancs de tête exclus de la recherche).
        val debut = declaration.noeud.jetonsPropres.firstOrNull()?.debut
            ?: declaration.noeud.debut
        // Le commentaire le plus proche qui FINIT avant la déclaration.
        val candidat = commentaires.lastOrNull { it.fin <= debut } ?: return null
        // Séparation : uniquement des blancs entre la fin du commentaire et
        // le début de la déclaration (testé sur le SOURCE du document).
        for (indice in candidat.fin until debut) {
            if (indice < declaration.source.length &&
                !declaration.source[indice].isWhitespace()
            ) {
                return null
            }
        }
        if (!candidat.texte.startsWith("/**") || candidat.texte.length <= 4) return null
        return corpsKDoc(candidat.texte)
    }

    /**
     * Rend le CORPS nettoyé d'un bloc KDoc : marqueurs `/**`/`*/` retirés,
     * astérisques de marge retirés ligne à ligne, bords taillés.
     *
     * @param texte le texte intégral du jeton KDoc ;
     * @return le corps lisible ; `null` si le corps est vide ou blanc.
     */
    private fun corpsKDoc(texte: String): String? {
        val corps = texte.removePrefix("/**").removeSuffix("*/")
        val lignes = corps.split('\n').map { ligne ->
            ligne.trim().removePrefix("*").trim()
        }
        val rendu = lignes.joinToString("\n").trim()
        return rendu.ifEmpty { null }
    }

    // ------------------------------------------------------------------
    // Navigation : identifiant visé et déclarations.
    // ------------------------------------------------------------------

    /**
     * Rend le jeton IDENTIFIER qui contient l'offset du caret.
     *
     * @param racine la racine de l'arbre du document ;
     * @param deplacement l'offset du caret ;
     * @return le jeton identifiant visé ; `null` si le caret ne désigne pas
     * un identifiant.
     */
    private fun jetonIdentifierA(racine: NoeudArbre, deplacement: Int): JetonBrut? {
        var trouve: JetonBrut? = null
        fun walk(n: NoeudArbre) {
            for (jeton in n.jetonsPropres) {
                if (jeton.genre == KtTokens.IDENTIFIER && jeton.debut <= deplacement && deplacement < jeton.fin) {
                    trouve = jeton
                }
            }
            n.enfants.forEach { walk(it) }
        }
        walk(racine)
        return trouve
    }

    /**
     * Rend le jeton NOM d'une déclaration (premier identifiant propre).
     *
     * @param declaration la déclaration ;
     * @return son jeton nom ; `null` si tronquée sans identifiant.
     */
    private fun jetonNom(declaration: KtDeclaration): JetonBrut? {
        return declaration.noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
    }

    /**
     * Rend TOUTES les déclarations nommées d'un document, en ordre du
     * document, récursivement (premier niveau + membres des corps de types).
     *
     * @param fichier la racine de la façade du document ;
     * @return les déclarations (typées) en ordre du document.
     */
    internal fun declarationsRecursives(fichier: KtFichier): List<KtDeclaration> {
        val resultat = mutableListOf<KtDeclaration>()
        fun collecter(enfants: List<NoeudArbre>) {
            for (enfant in enfants) {
                val declaration = FabriqueKt.declaration(enfant, fichier.source) ?: continue
                resultat.add(declaration)
                val corps = enfant.enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY }
                if (corps != null) collecter(corps.enfants)
            }
        }
        collecter(fichier.noeud.enfants)
        return resultat
    }
}

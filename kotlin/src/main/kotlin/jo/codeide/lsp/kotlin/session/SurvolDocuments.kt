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
import jo.codeide.lsp.kotlin.resolve.Couches
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.MembreChaineJvm
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
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
        univers: UniversPreuves? = null,
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
        val externe = scan?.declaration(nom)
        if (externe != null) {
            // Garde D3 (ADR 0208, fiche 045) : la KDoc tierce voyage avec
            // le SNAPSHOT du scan — un fichier déclarant DISPARU depuis ne
            // la sert plus (signature seule, jamais d'exception) ; une URI
            // non-fichier (fixture) ne se vérifie pas : la KDoc reste.
            val documentation = externe.documentation?.takeIf { fichierDeclarantLisible(externe.uri) }
            return SurvolKotlin(signature = externe.signature, documentation = documentation)
        }

        // 3-bis. Les APPELABLES de premier niveau du CLASSPATH (vague 2e —
        // fiche 025 : « survol d'un appelable top-level du classpath
        // (println) ») : l'index porte nom, paramètres et retour — la
        // signature dépouillée + le package déclarant entre parenthèses
        // (miroir du rendu des membres JVM, propriétaire déclarant).
        univers?.index?.let { index ->
            val visible = index.topParNom(nom)
                .firstOrNull { it.paquet == univers.paquet || it.paquet in UniversPreuves.PAQUETS_DEFAUT }
            if (visible != null) {
                val parametres = visible.parametres.joinToString(", ") { parametre ->
                    if (parametre.type == null) {
                        parametre.nom.orEmpty()
                    } else {
                        "${parametre.nom.orEmpty()}: ${parametre.type}"
                    }
                }
                val genre = if (visible.genre == jo.codeide.lsp.kotlin.index.GenreAppelable.PROPRIETE) "val" else "fun"
                val retour = visible.typeRetour?.let { ": $it" }.orEmpty()
                return SurvolKotlin(
                    signature = "$genre ${visible.nom}($parametres)$retour (${visible.paquet})",
                    documentation = null,
                )
            }
        }

        // 4. Résolution des membres HÉRITÉS de bibliothèque (vague 2c —
        // fiche 040 : le survol IMPLICITE `layoutInflater` rend `val
        // layoutInflater: LayoutInflater` avec son propriétaire déclarant,
        // le survol QUALIFIÉ `this.layoutInflater` rend le même — miroir
        // Android Studio).
        return univers?.let { survolMembreHerite(cible, it, identifiant, nom) }
    }

    /**
     * Le survol d'un membre HÉRITÉ de bibliothèque (vague 2c — fiche 040)
     * : l'identifiant désigné est un SÉLECTEUR sur récepteur typé
     * (`this.layoutInflater`, `layoutInflater.inflate`) ou un NOM NU de
     * membre implicite (`layoutInflater` dans une Activity) — la vue JVM
     * rend la signature dépouillée du descripteur et le PROPRIÉTAIRE
     * déclarant.
     *
     * @param cible le document ouvert visé ;
     * @param univers l'univers des preuves (modèle + vue JVM) ;
     * @param identifiant le jeton désigné ;
     * @param nom le nom du membre.
     * @return le survol ; `null` si la référence ne résout pas à un membre
     * de bibliothèque (silence honnête D3).
     */
    private fun survolMembreHerite(
        cible: DocumentOuvert,
        univers: UniversPreuves,
        identifiant: JetonBrut,
        nom: String,
    ): SurvolKotlin? {
        val vue = univers.vueJvm ?: return null
        val region = regionContenant(cible.arbre.noeud, identifiant.debut)
        val liaison = MarcheTypee.Liaison(arbre = cible.arbre, univers = univers, region = region)
        val pile = Couches.aPosition(cible.arbre, univers, liaison, identifiant.debut)

        // La référence désignée : SÉLECTEUR d'une expression qualifiée (le
        // récepteur AVANT elle) ou NOM NU implicite.
        val qualifiee = qualifieeCouvrante(cible.arbre.noeud, identifiant.debut)
        val recepteur: NoeudArbre? = qualifiee?.enfants?.firstOrNull()
        val typeRecepteur: TypeValeur? = if (
            recepteur != null && recepteur.fin <= identifiant.debut
        ) {
            TypageBorne.typeDeLexpression(recepteur, univers, liaison, pile)
        } else {
            // Nom NU : le récepteur implicite — la pile rend le membre.
            val membre = pile.membre(nom) ?: return null
            val lien = membre.lienJvm ?: return null
            val chaine = vue.membreChaine(univers.mapeJvm(lien), nom) ?: return null
            return renduMembreJvm(chaine.proprietaire, chaine, nom)
        }

        val fqcn = when (typeRecepteur) {
            is TypeValeur.Jvm -> typeRecepteur.fqcn
            is TypeValeur.Projet -> {
                // Récepteur PROJET : le membre peut vivre sur un maillon JVM
                // de la clôture (`this.layoutInflater` dans une Activity
                // projet — le lien porte le propriétaire déclarant).
                val membre = univers.clotureDe(typeRecepteur.type)?.membre(nom) ?: return null
                val lien = membre.lienJvm ?: return null
                val chaine = vue.membreChaine(univers.mapeJvm(lien), nom) ?: return null
                return renduMembreJvm(chaine.proprietaire, chaine, nom)
            }
            null -> return null
        }
        val chaine = vue.membreChaine(univers.mapeJvm(fqcn), nom)
            // Le COMPAGNON du classpath (vague 2d — fiche 024 : le survol
            // de `UInt.MAX_VALUE` lit le champ STATIQUE du compagnon —
            // `fqcn$Companion`, la classe manquante rend `null` d'elle-même).
            ?: vue.membreChaine(univers.mapeJvm(fqcn) + "\$Companion", nom)
            ?: return null
        return renduMembreJvm(chaine.proprietaire, chaine, nom)
    }

    /**
     * Le rendu d'un membre JVM (vague 2c — fiche 040) : `val x: T`,
     * `var x: T` (getter+setter/setter seul) ou `fun x(A, B): R`, suivi du
     * PROPRIÉTAIRE déclarant entre parenthèses.
     */
    private fun renduMembreJvm(proprietaire: String, chaine: MembreChaineJvm, nom: String): SurvolKotlin? {
        val signature = chaine.proprieteDerivee?.let { derivee ->
            val genre = if (derivee.estVar) "var" else "val"
            "$genre $nom: ${typeSimple(derivee.typeFqcn)}"
        } ?: chaine.signatures.firstOrNull()?.let { signature ->
            if (signature.estChamp) {
                "var $nom: ${typeSimple(signature.retour)}"
            } else {
                val parametres = signature.parametres?.joinToString(", ") { typeSimple(it) }.orEmpty()
                val retour = signature.retour?.let { typeSimple(it) } ?: "Unit"
                "fun $nom($parametres): $retour"
            }
        } ?: return null
        return SurvolKotlin(signature = "$signature ($proprietaire)", documentation = null)
    }

    /** Le nom SIMPLE d'affichage d'un FQCN (`android.view.LayoutInflater` → `LayoutInflater`). */
    private fun typeSimple(fqcn: String?): String =
        fqcn?.substringAfterLast('.')?.substringAfterLast('$')?.ifEmpty { fqcn } ?: "?"

    /** La plus profonde expression qualifiée couvrant la position (ou `null`). */
    private fun qualifieeCouvrante(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
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

    /** La plus profonde déclaration FUN/PROPERTY couvrant la position (ou `null`). */
    private fun regionContenant(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.FUN || noeud.genre == KtNodeTypes.PROPERTY) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    // ------------------------------------------------------------------
    // Rendu : signature et documentation.
    // ------------------------------------------------------------------

    /**
     * Le fichier déclarant d'une déclaration du scan est-il encore LISIBLE
     * (garde D3 — ADR 0208) : une URI `file:` se vérifie par existence ;
     * toute autre forme (fixture, schéma inconnu) ne se vérifie PAS — la
     * documentation reste servie (conservateur honnête).
     *
     * @param uri l'URI de la déclaration externe ;
     * @return `true` si le fichier existe (ou si l'URI n'est pas vérifiable).
     */
    private fun fichierDeclarantLisible(uri: String): Boolean {
        if (!uri.startsWith("file:")) return true
        return try {
            java.io.File(java.net.URI(uri)).isFile
        } catch (_: Exception) {
            true
        }
    }

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
     * La PLAGE du nom d'une déclaration (vague 2e — fiche 031 : le
     * go-to-definition externe rend l'URI de fichier ET la plage du nom).
     *
     * @param declaration la déclaration ;
     * @return la plage du nom ; `null` si tronquée sans identifiant.
     */
    internal fun plageNomDe(declaration: KtDeclaration): jo.codeide.lsp.kotlin.parse.kt.PlageTexte? {
        val jeton = jetonNom(declaration) ?: return null
        return jo.codeide.lsp.kotlin.parse.kt.PlageTexte(jeton.debut, jeton.fin)
    }

    /**
     * Rend TOUTES les déclarations nommées d'un document, en ordre du
     * document, récursivement (premier niveau + membres des corps de types).
     *
     * Vague 2e (fiche 027) : les instructions d'un SCRIPT (`.kts`) sont
     * des déclarations pour la navigation — le premier niveau d'un script
     * vit dans le BLOC de son nœud SCRIPT (le survol, les symboles et la
     * définition du Kotlin standard d'un `.kts` les desservent).
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
                // Les corps porteurs de déclarations : le CORPS DE CLASSE
                // (membres) et le BLOC d'une fonction (LOCALES — étage 3
                // vague 3a, fiche 045 : « survol : locale avec type écrit »,
                // « définition : locale du document courant » — une locale
                // se survole et se définit comme toute déclaration).
                for (corps in enfant.enfants) {
                    if (corps.genre == KtNodeTypes.CLASS_BODY || corps.genre == KtNodeTypes.BLOCK) {
                        collecter(corps.enfants)
                    }
                }
            }
        }
        val enfants = fichier.noeud.enfants
        val blocScript = enfants
            .firstOrNull { it.genre == KtNodeTypes.SCRIPT }
            ?.enfants?.firstOrNull { it.genre == KtNodeTypes.BLOCK }
        val porteurs = blocScript?.enfants ?: enfants
        collecter(porteurs)
        return resultat
    }
}

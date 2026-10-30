// CodeIDE — diagnostics SÉMANTIQUES d'un document (module :kotlin, étage 2
// vague 2a — fiches 017/022/006 ; vague 2b — fiche 046 : sélecteurs sur
// récepteurs CLASSPATH typés par la vue JVM). Code NEUF jjoblab (clean-room
// — l'ancien `diagnostics/` est en lecture interdite R9.1 ; cette passe est
// écrite cahier en main : garde par déclaration P1-A, preuve d'absence sur
// les types du projet ET de la bibliothèque, journal explain, familles K4
// de la passe typée [DiagnosticsTypage]).
package jo.codeide.lsp.kotlin.session

import com.intellij.platform.syntax.SyntaxElementType
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.DiagnosticKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.index.VueAppelables
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.ClotureHeritage
import jo.codeide.lsp.kotlin.resolve.Clotures
import jo.codeide.lsp.kotlin.resolve.ContributionFichier
import jo.codeide.lsp.kotlin.resolve.Couches
import jo.codeide.lsp.kotlin.resolve.CoucheThis
import jo.codeide.lsp.kotlin.resolve.EtatScan
import jo.codeide.lsp.kotlin.resolve.ExplicationsNonResolus
import jo.codeide.lsp.kotlin.resolve.FormeReference
import jo.codeide.lsp.kotlin.resolve.GenreMembreHerite
import jo.codeide.lsp.kotlin.resolve.GenreMembreProjet
import jo.codeide.lsp.kotlin.resolve.IssueSelection
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.MembreHerite
import jo.codeide.lsp.kotlin.resolve.MembreProjet
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.resolve.PileThis
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.TypeProjet
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import jo.codeide.lsp.kotlin.resolve.VueJvmMembres
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * La passe SÉMANTIQUE des diagnostics d'un document (pure — aucun état,
 * appelée sous verrou de lecture) : `kt.unresolved` (preuve d'absence) et
 * `kt.missingReturn` (preuve locale), selon la garde PAR DÉCLARATION du
 * chantier P1-A (fiche 017).
 *
 * ## La garde par déclaration (fiche 017)
 *
 * - un document portant des marqueurs d'erreur n'est plus écarté EN BLOC :
 *   seules les DÉCLARATIONS dont le sous-arbre parse proprement sont
 *   diagnostiquées (« régions propres ») ;
 * - un nom DÉCLARÉ n'importe où dans le document (déclaration en cours de
 *   frappe ou avalie par la récupération d'erreur) n'est JAMAIS signalé ;
 * - une ZONE package/import brisée coupe la preuve d'absence pour TOUT le
 *   document (les noms importés ne sont plus fiables) ;
 * - un EN-TÊTE de classe brisée (supertypes, paramètres de type) rend les
 *   membres hérités illisibles : les membres propres restent diagnostiqués
 *   (preuves locales) mais PAS absence-fiables.
 *
 * ## Les univers ouverts (fiche 022 — jamais de faux positif)
 *
 * Un héritage ÉCRIT, un enum, une data class ouvrent l'univers des membres :
 * noms nus muets à l'intérieur de ces classes, sélecteurs muets sur ces
 * récepteurs (le jeu de membres complet ne peut pas être constitué).
 *
 * ## Le régime dégradé (fiche 046, garde « sans classpath jamais signalé »)
 *
 * La passe ne prouve RIEN tant que le guichet d'index est muet (classpath
 * non posé ou passe non publiée — fiche 042) ou que le scan est PARTIEL.
 */
internal object DiagnosticsSemantiques {

    /**
     * Calcule les diagnostics sémantiques du document.
     *
     * @param document le document ouvert (texte + arbre) ;
     * @param uri l'identifiant du document (le régime script se déduit du
     * suffixe `.kts`) ;
     * @param contributionsScan les contributions du scan des racines (le
     * document COURANT exclu — la session filtre son doublon périmé) ;
     * @param etatScan l'état du scan des racines ;
     * @param index la vue des appelables du classpath (guichet de la fiche
     * 042) ; `null` = régime dégradé, TOUTE la passe se tait ;
     * @param vueJvm la vue JVM des membres du classpath (vague 2b — ADR
     * 0223) ; `null` = les sélecteurs sur récepteurs de bibliothèque
     * restent muets.
     * @return les diagnostics sémantiques en ordre du document ; liste vide
     * en régime dégradé (jamais d'exception).
     */
    internal fun calculer(
        document: DocumentOuvert,
        uri: String,
        contributionsScan: List<ContributionFichier>,
        etatScan: EtatScan,
        index: VueAppelables?,
        vueJvm: VueJvmMembres? = null,
    ): List<DiagnosticKotlin> {
        // Régime dégradé : le guichet est muet — AUCUNE preuve (fiche 046,
        // comportement « kt-unresolved---sans-classpath,-jamais-signale »).
        if (index == null) return emptyList()

        val arbre = document.arbre
        val script = uri.endsWith(".kts")
        val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()

        // Le modèle du document PRIME (premier ajouté : ses FQCN gagnent).
        val batisseur = ModeleProjet.Batisseur()
        batisseur.ajouter(ModeleProjet.contributionKotlin(arbre, uri = ""))
        contributionsScan.forEach(batisseur::ajouter)
        val modele = batisseur.figer()

        val univers = UniversPreuves(
            arbre = arbre,
            paquet = paquet,
            modele = modele,
            index = index,
            etatScan = etatScan,
            script = script,
            vueJvm = vueJvm,
        )

        val contextes = Contextes(
            document = document,
            univers = univers,
            nomsDeclares = collecterNomsDeclares(arbre),
            directivesBruisees = directivesPortentErreur(arbre.noeud),
        )

        val diagnostics = mutableListOf<DiagnosticKotlin>()
        for (declaration in declarationsHautNiveau(arbre)) {
            visiterDeclaration(
                declaration, contextes, diagnostics,
                pile = PileThis.VIDE,
                chemin = emptyList(),
                nomsZone = emptySet(),
                enteteBrisee = false,
            )
        }
        return diagnostics
    }

    /**
     * Les déclarations de PREMIER NIVEAU du document (vague 2e — fiche
     * 027) : les enfants DIRECTS de la racine pour un fichier, les enfants
     * du BLOC du SCRIPT pour un `.kts` — les instructions d'un script sont
     * des déclarations POUR LA PASSE SÉMANTIQUE (le Kotlin standard du
     * script résout, la garde D3 s'y exerce) ; la facade
     * `KtFichier.declarations()` reste sur sa frontière étage 1 (les
     * instructions ne sont pas des déclarations DOCUMENTAIRES).
     */
    private fun declarationsHautNiveau(arbre: KtFichier): List<NoeudArbre> {
        val enfants = arbre.noeud.enfants
        val blocScript = enfants
            .firstOrNull { it.genre == KtNodeTypes.SCRIPT }
            ?.enfants?.firstOrNull { it.genre == KtNodeTypes.BLOCK }
        val porteurs = blocScript?.enfants ?: enfants
        return porteurs.filter { it.genre in jo.codeide.lsp.kotlin.parse.kt.FabriqueKt.genresDeclaration }
    }

    // ------------------------------------------------------------------
    // Régions (la garde par déclaration — fiche 017).
    // ------------------------------------------------------------------

    /**
     * Visite une déclaration : région propre si son sous-arbre est sain ;
     * une déclaration de type visite son EN-TÊTE (types de la clause et du
     * constructeur primaire) puis ses MEMBRES récursivement.
     *
     * La vague 2c y construit la PILE DES THIS (fiches 044/028/040) : une
     * classe NOMMÉE empile sa couche (le type du modèle + sa CLÔTURE
     * d'héritage — mixte projet/classpath), un OBJET ANONYME empile la
     * sienne (supertypes résolus + membres du corps) ; la ZONE DE
     * CONSTRUCTION porte les paramètres NUS du primaire (initialisateurs,
     * init — PAS les méthodes, fiche 028 : `contexte` hors zone est
     * SIGNALÉ, miroir kotlinc).
     *
     * @param noeud la déclaration visitée ;
     * @param contextes les contextes de la passe ;
     * @param diagnostics l'accumulateur des émissions ;
     * @param pile la pile des this des couches ENGLOBANTES ;
     * @param chemin les noms des types englobants (FQCN pointé du modèle) ;
     * @param nomsZone les noms en portée dans la ZONE DE CONSTRUCTION
     * courante (paramètres nus du primaire de la classe englobante) ;
     * @param enteteBrisee l'en-tête de la classe englobante est-elle brisée
     * (absence muette dans les membres — fiche 017).
     */
    private fun visiterDeclaration(
        noeud: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        pile: PileThis,
        chemin: List<String>,
        nomsZone: Set<String>,
        enteteBrisee: Boolean,
    ) {
        when (noeud.genre) {
            KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION -> {
                val nom = nomDeDeclaration(noeud)
                val corps = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY }
                val enteteSaine = entetePropre(noeud, corps)
                if (enteteSaine) {
                    visiterZone(noeud, corps, contextes, diagnostics, pile, nomsZone)
                }

                // La COUCHE de la déclaration : nommée (modèle + clôture)
                // ou anonyme (supertypes résolus + membres du corps) — les
                // constructeurs PARTAGÉS de [Couches] (miroir de la pile par
                // position de la complétion/survol).
                val couche = if (nom != null) {
                    Couches.nommee(contextes.univers, chemin, nom)
                } else {
                    Couches.anonyme(contextes.univers, noeud)
                }
                val pileInterne = if (nom != null || noeud.genre == KtNodeTypes.OBJECT_DECLARATION) {
                    pile.empiler(couche)
                } else {
                    pile
                }

                // La ZONE DE CONSTRUCTION de la classe : les paramètres NUS
                // du primaire (sans val/var — les val/var sont des MEMBRES,
                // gardés par la déclaration documentaire) y sont en portée
                // (initialisateurs, init, entrées d'enum) — PAS dans les
                // méthodes (fiche 028).
                val zone = if (nom != null) parametresPrimaireNus(noeud) else emptySet()

                if (corps != null) {
                    for (membre in corps.enfants) {
                        val zoneMembre = when (membre.genre) {
                            KtNodeTypes.FUN -> emptySet()
                            else -> zone
                        }
                        visiterDeclaration(
                            membre, contextes, diagnostics,
                            pileInterne,
                            if (nom != null) chemin + nom else chemin,
                            zoneMembre,
                            !enteteSaine,
                        )
                    }
                }
            }
            else -> {
                // Fonction, propriété, typealias, entrée d'enum : une région
                // si le sous-arbre est propre (les déclarations cassées ne
                // diagnostiquent RIEN — leurs noms restent des gardes).
                if (sousArbrePropre(noeud)) {
                    visiterRegion(noeud, contextes, diagnostics, pile, nomsZone, enteteBrisee)
                }
            }
        }
    }

    /**
     * Les noms des paramètres NUS du constructeur PRIMAIRE (sans `val`/
     * `var` — fiche 028 : ils ne vivent que dans la ZONE DE CONSTRUCTION).
     */
    private fun parametresPrimaireNus(noeud: NoeudArbre): Set<String> {
        val primaire = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.PRIMARY_CONSTRUCTOR }
        val liste = primaire?.enfants?.firstOrNull { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
            ?: noeud.enfants.firstOrNull { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
            ?: return emptySet()
        val noms = mutableSetOf<String>()
        for (parametre in liste.enfants.filter { it.genre == KtNodeTypes.VALUE_PARAMETER }) {
            val estMembre = parametre.jetonsPropres.any {
                it.genre == KtTokens.VAL_KEYWORD || it.genre == KtTokens.VAR_KEYWORD
            }
            if (estMembre) continue
            nomDeDeclaration(parametre)?.let { noms.add(it) }
        }
        return noms
    }

    /**
     * Visite l'EN-TÊTE d'une déclaration de type saine : le nœud SANS son
     * corps (les types de la clause de supertypes et du constructeur primaire
     * sont vérifiés — ce sont des positions de types à part entière ; la
     * clause d'appel du super voit la ZONE DE CONSTRUCTION, fiche 028).
     */
    private fun visiterZone(
        noeud: NoeudArbre,
        corps: NoeudArbre?,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        pile: PileThis,
        nomsZone: Set<String>,
    ) {
        val liaison = contextes.liaisonDuDocument()
        for (enfant in noeud.enfants) {
            if (enfant === corps) continue
            marcher(enfant, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee = false)
        }
    }

    /** Visite une RÉGION PROPRE (fonction, propriété, membre sain). */
    private fun visiterRegion(
        noeud: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        pile: PileThis,
        nomsZone: Set<String>,
        enteteBrisee: Boolean,
    ) {
        if (noeud.genre == KtNodeTypes.FUN) {
            verifierRetourManquant(noeud, diagnostics)
        }
        val liaison = contextes.liaisonLocale(noeud)
        marcher(noeud, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee)
        // La passe TYPÉE de la famille K4 (fiche 046 — vague 2b) : les cinq
        // familles sémantiques sur la région propre.
        DiagnosticsTypage.verifier(noeud, contextes.univers, liaison, diagnostics)
    }

    // ------------------------------------------------------------------
    // Preuve locale : le retour manquant (fiche 017 ; gardes 046 en 2b).
    // ------------------------------------------------------------------

    /**
     * La preuve locale du RETOUR MANQUANT (fiche 017, comportement « region
     * propre - preuve locale (retour manquant) pendant l'edition ailleurs » ;
     * les gardes complètes de la fiche 046 arrivent en vague 2b) : un corps
     * de BLOC, un type de retour ÉCRIT autre que `Unit`, et AUCUN `return`
     * NI `throw` dans le bloc (sous-rapport assumé : un `return` imbriqué
     * dans un lambda éteint la preuve — jamais faux).
     */
    private fun verifierRetourManquant(noeud: NoeudArbre, diagnostics: MutableList<DiagnosticKotlin>) {
        val bloc = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.BLOCK } ?: return
        val typeRetour = typeRetourEcritDeFonction(noeud) ?: return
        if (typeRetour == "Unit") return
        if (contientGenre(bloc, KtNodeTypes.RETURN) || contientGenre(bloc, KtNodeTypes.THROW)) return
        val nom = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER } ?: return
        diagnostics.add(
            DiagnosticKotlin(
                code = CodeDiagnosticKotlin.RETOUR_MANQUANT,
                severite = SeveriteKotlin.ERREUR,
                plage = PlageTexte(nom.debut, nom.fin),
                message = "Retour manquant : la fonction « ${nom.texte} » déclare un type de retour " +
                    "« $typeRetour » mais son corps de bloc ne retourne rien",
                tags = emptyList(),
                informationsLiees = emptyList(),
            ),
        )
    }

    /** Le type de retour ÉCRIT d'une fonction (après la liste de paramètres). */
    private fun typeRetourEcritDeFonction(noeud: NoeudArbre): String? {
        val enfants = noeud.enfants
        val listeParams = enfants.indexOfFirst { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
        if (listeParams < 0) return null
        for (i in (listeParams + 1) until enfants.size) {
            if (enfants[i].genre != KtNodeTypes.TYPE_REFERENCE) continue
            return enfants[i].texteCanonique.substringBefore('<').ifEmpty { null }
        }
        return null
    }

    // ------------------------------------------------------------------
    // Saineté des zones (les gardes de la fiche 017).
    // ------------------------------------------------------------------

    /** Le sous-arbre porte-t-il un nœud d'erreur (la région est-elle propre) ? */
    private fun sousArbrePropre(noeud: NoeudArbre): Boolean {
        if (noeud.messageErreur != null) return false
        return noeud.enfants.all { sousArbrePropre(it) }
    }

    /**
     * L'EN-TÊTE d'une déclaration de type (le nœud SANS son corps) est-elle
     * propre ? Les erreurs du CORPS ne contaminent pas l'en-tête (fiche 017 :
     * le membre propre d'une classe au membre cassé reste diagnostiqué).
     */
    private fun entetePropre(noeud: NoeudArbre, corps: NoeudArbre?): Boolean {
        if (corps == null) return sousArbrePropre(noeud)
        fun propreHorsCorps(n: NoeudArbre): Boolean {
            if (n === corps) return true
            if (n.messageErreur != null) return false
            return n.enfants.all(::propreHorsCorps)
        }
        return propreHorsCorps(noeud)
    }

    /** Le nœud porte-t-il ce modificateur doux (MODIFIER_LIST comprise) ? */
    private fun porteurModificateur(noeud: NoeudArbre, modificateur: SyntaxElementType): Boolean {
        return noeud.enfants
            .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
            .any { liste -> liste.enfants.any { it.genre == modificateur } }
    }

    /** Le nom d'un nœud déclaration (premier IDENTIFIER propre — aide 2c). */
    private fun nomDeDeclaration(noeud: NoeudArbre): String? {
        return noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
    }

    /** Les directives package/import portent-elles une erreur de syntaxe ? */
    private fun directivesPortentErreur(racine: NoeudArbre): Boolean {
        return portentErreur(racine, KtNodeTypes.PACKAGE_DIRECTIVE) ||
            portentErreur(racine, KtNodeTypes.IMPORT_DIRECTIVE)
    }

    /** Une directive du genre donné porte-t-elle une erreur ? */
    private fun portentErreur(racine: NoeudArbre, genre: SyntaxElementType): Boolean {
        fun porter(n: NoeudArbre): Boolean {
            if (n.genre == genre) return !sousArbrePropre(n)
            return n.enfants.any(::porter)
        }
        return porter(racine)
    }

    /** Collecte les noms DÉCLARÉS du document (gardes : data synthétisés compris). */
    private fun collecterNomsDeclares(arbre: KtFichier): Set<String> {
        val noms = mutableSetOf<String>()
        fun collecter(noeud: NoeudArbre, dansPrimaire: Boolean) {
            // Un PARAMÈTRE NU du constructeur PRIMAIRE (sans `val`/`var`)
            // n'est PAS un nom déclaré du document : il ne vit que dans la
            // ZONE DE CONSTRUCTION (fiche 028 — `contexte` hors zone est
            // SIGNALÉ, miroir kotlinc) ; les `val`/`var` restent des
            // MEMBRES (garde documentaire inchangée).
            val parametreNuDuPrimaire = dansPrimaire && noeud.genre == KtNodeTypes.VALUE_PARAMETER &&
                noeud.jetonsPropres.none {
                    it.genre == KtTokens.VAL_KEYWORD || it.genre == KtTokens.VAR_KEYWORD
                }
            if (noeud.genre in UniversPreuves.GENRES_DECLARATION && !parametreNuDuPrimaire) {
                noeud.jetonsPropres
                    .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                    ?.let { noms.add(it) }
            }
            // Data class : les membres SYNTHÉTISÉS (copy, componentN) sont
            // accessibles sans sélecteur dans la classe — jamais signalés.
            if (noeud.genre == KtNodeTypes.CLASS && porteurModificateur(noeud, KtTokens.DATA_MODIFIER)) {
                noms.add("copy")
                val primaire = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.PRIMARY_CONSTRUCTOR }
                val liste = primaire?.enfants?.firstOrNull { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
                var proprietes = 0
                if (liste != null) {
                    for (parametre in liste.enfants.filter { it.genre == KtNodeTypes.VALUE_PARAMETER }) {
                        if (parametre.jetonsPropres.any {
                                it.genre == KtTokens.VAL_KEYWORD || it.genre == KtTokens.VAR_KEYWORD
                            }
                        ) {
                            proprietes++
                        }
                    }
                }
                for (rang in 1..proprietes) noms.add("component$rang")
            }
            for (enfant in noeud.enfants) {
                collecter(enfant, dansPrimaire || enfant.genre == KtNodeTypes.PRIMARY_CONSTRUCTOR)
            }
        }
        collecter(arbre.noeud, dansPrimaire = false)
        return noms
    }

    /** Le sous-arbre contient-il un nœud de ce genre ? */
    private fun contientGenre(noeud: NoeudArbre, genre: SyntaxElementType): Boolean {
        if (noeud.genre == genre) return true
        return noeud.enfants.any { contientGenre(it, genre) }
    }

    // ------------------------------------------------------------------
    // La marche des références.
    // ------------------------------------------------------------------

    /**
     * La MARCHE des références d'une zone : vérifie chaque référence (nom
     * nu, sélecteur qualifié, type écrit, CHEMIN FQN) et rend les arguments,
     * lambdas et expressions imbriquées à la marche.
     *
     * La vague 2c y tisse la PILE DES THIS : les OBJETS ANONYMES empilent
     * leur couche (le this implicite le plus profond d'abord), les lambdas
     * traînantes d'un `apply`/`also`/`run` sur récepteur TYPÉ portent le
     * récepteur en this implicite (fiche 028), et la ZONE DE CONSTRUCTION
     * (`field` des accesseurs, paramètres nus du primaire) garde ses noms.
     *
     * @param noeud le nœud visité ;
     * @param liaison la liaison nom → type écrit des portées locales ;
     * @param pile la pile des this de la position ;
     * @param nomsZone les noms de la ZONE DE CONSTRUCTION courante
     * (paramètres nus du primaire, `field` des accesseurs) ;
     * @param enteteBrisee l'en-tête de la classe englobante est brisée
     * (absence muette — fiche 017).
     */
    private fun marcher(
        noeud: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
        nomsZone: Set<String>,
        enteteBrisee: Boolean,
    ) {
        when (noeud.genre) {
            KtNodeTypes.USER_TYPE -> {
                if (noeud.enfants.none { it.genre == KtNodeTypes.USER_TYPE }) {
                    // Le type SIMPLE porte la vérification ; ses ARGUMENTS
                    // marchent — la RÉFÉRENCE du nom ne re-marche PAS en nom
                    // nu (elle est une position de TYPE, jamais un usage :
                    // l'émission y serait DOUBLÉE).
                    noeud.enfants
                        .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                        ?.let { verifierNomType(it, contextes, diagnostics, pile, enteteBrisee) }
                    noeud.enfants
                        .filter { it.genre != KtNodeTypes.REFERENCE_EXPRESSION }
                        .forEach { marcher(it, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee) }
                } else {
                    // Un type QUALIFIÉ (`android.os.Bundle`, `Aquarium.Bac`,
                    // `Conteneur.Fabrique` — vague 2c, fiches 044/028/029) :
                    // le CHEMIN COMPLET décide. S'il résout (projet imbriqué,
                    // import + `$` classpath, classe de packages), tous les
                    // segments restent MUETS ; sinon UN SEUL segment est
                    // faillible — le DERNIER (le nom de classe) — les
                    // segments amont sont des PAQUETS potentiels, jamais
                    // signalés (E4/E4bis).
                    verifierTypeQualifie(noeud, contextes, diagnostics, pile, enteteBrisee)
                    noeud.enfants
                        .filter { it.genre == KtNodeTypes.TYPE_ARGUMENT_LIST }
                        .forEach { marcher(it, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee) }
                }
            }
            KtNodeTypes.DOT_QUALIFIED_EXPRESSION, KtNodeTypes.SAFE_ACCESS_EXPRESSION -> {
                val recepteur = noeud.enfants.firstOrNull()
                val selecteur = noeud.enfants.lastOrNull()
                if (recepteur != null && selecteur != null && recepteur !== selecteur) {
                    // Le CHEMIN FQN d'identifiants nus (vague 2c — fiche 028 :
                    // `exo.paq.ClassePublique()` sans import, `Aquarium.Bac()`)
                    // : si la RACINE ne résout PAS comme valeur et que le
                    // chemin complet RÉSOUT comme classe, TOUT est muet ; s'il
                    // ne résout pas, la racine ET la feuille appelée sont
                    // signalées, le MILIEU (paquet) muet — miroir kotlinc.
                    if (noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION &&
                        marcherCheminFqn(noeud, recepteur, selecteur, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee)
                    ) {
                        return
                    }
                    // Le RÉCEPTEUR marche d'abord (nom nu candidat), le
                    // sélecteur est vérifié par le type complet du récepteur.
                    marcher(recepteur, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee)
                    verifierSelecteur(recepteur, selecteur, contextes, diagnostics, liaison, pile, enteteBrisee)
                    // Les ARGUMENTS et lambdas de l'appel sélecteur marchent
                    // (la callee elle-même ne re-marche pas) ; la lambda
                    // traînante d'un `apply`/`also`/`run` sur récepteur typé
                    // porte le RÉCEPTEUR en this implicite (fiche 028).
                    if (selecteur.genre == KtNodeTypes.CALL_EXPRESSION) {
                        val nomSelecteur = selecteur.enfants
                            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                            ?.texteCanonique.orEmpty()
                        val pileLambda = if (nomSelecteur in Couches.FONCTIONS_APPLIQUANTES) {
                            TypageBorne.typeDeLexpression(recepteur, contextes.univers, liaison, pile)
                                ?.let { pile.empiler(Couches.deType(contextes.univers, it)) }
                        } else {
                            null
                        }
                        selecteur.enfants
                            .filter { it.genre != KtNodeTypes.REFERENCE_EXPRESSION }
                            .forEach {
                                marcher(
                                    it, contextes, diagnostics, liaison,
                                    pileLambda ?: pile, nomsZone, enteteBrisee,
                                )
                            }
                    }
                }
            }
            KtNodeTypes.OBJECT_LITERAL, KtNodeTypes.OBJECT_DECLARATION -> {
                // La PILE DES THIS (vague 2c — fiche 028) : un objet ANONYME
                // en position d'EXPRESSION empile sa couche (ses supertypes
                // résolus + les membres de son corps) pour la marche de son
                // corps ; sa clause de supertypes marche dans la pile
                // EXTERNE (les arguments du super y sont évalués).
                if (noeud.genre == KtNodeTypes.OBJECT_LITERAL || nomDeDeclaration(noeud) == null) {
                    val couche = Couches.anonyme(contextes.univers, noeud)
                    val pileInterne = pile.empiler(couche)
                    for (enfant in noeud.enfants) {
                        if (enfant.genre == KtNodeTypes.SUPER_TYPE_LIST) {
                            marcher(enfant, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee)
                        } else {
                            marcher(enfant, contextes, diagnostics, liaison, pileInterne, nomsZone, enteteBrisee)
                        }
                    }
                } else {
                    noeud.enfants.forEach { marcher(it, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee) }
                }
            }
            KtNodeTypes.PROPERTY_ACCESSOR -> {
                // Un accesseur CUSTOM : `field` y est en portée (fiche 028 —
                // `set(valeur) { field = valeur + 1 }`) ; son paramètre est
                // une DÉCLARATION (garde documentaire).
                val zoneAccesseur = nomsZone + "field"
                noeud.enfants.forEach { marcher(it, contextes, diagnostics, liaison, pile, zoneAccesseur, enteteBrisee) }
            }
            KtNodeTypes.CALL_EXPRESSION -> {
                // Un appel NU : la callee est un nom nu candidat, les
                // arguments et lambdas marchent. Vague 2f (fiche 047) : la
                // lambda traînante d'un `with(récepteur) { … }` porte le
                // PREMIER ARGUMENT en this implicite (miroir de
                // `apply`/`also`/`run` — le récepteur vit dans les
                // ARGUMENTS, pas avant le point).
                noeud.enfants
                    .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                    ?.let { verifierNomNu(it, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee) }
                val pileWith = pileImpliciteWith(noeud, contextes, liaison, pile)
                for (enfant in noeud.enfants) {
                    // La callee ne re-marche PAS (verifierNomNu l'a déjà
                    // couverte — l'émission y serait DOUBLÉE).
                    if (enfant.genre == KtNodeTypes.REFERENCE_EXPRESSION) continue
                    if (enfant.genre == KtNodeTypes.LAMBDA_ARGUMENT && pileWith != null) {
                        marcher(enfant, contextes, diagnostics, liaison, pileWith, nomsZone, enteteBrisee)
                    } else {
                        marcher(enfant, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee)
                    }
                }
            }
            KtNodeTypes.REFERENCE_EXPRESSION -> {
                verifierNomNu(noeud, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee)
            }
            KtNodeTypes.LAMBDA_EXPRESSION -> {
                // Étage 3 vague 3d (fiche 038) : une lambda EN POSITION
                // dont l'ATTENTE porte un RÉCEPTEUR prête ce récepteur
                // comme this implicite du CORPS — `Etage { compter() }`
                // (PorteeMaison.() -> Unit, projet), `Ecran { bricoler() }`
                // (classpath), `buildList { add(…) }` (Function1 rendu par
                // l'index). Miroir de la pile des this de Couches.aPosition.
                val couchePortee = TypageBorne.recepteurAttenduDeLambda(
                    contextes.univers.arbre,
                    noeud.debut,
                    contextes.univers,
                )?.let { Couches.deType(contextes.univers, it) }
                val pilePortee = if (couchePortee != null) pile.empiler(couchePortee) else pile
                noeud.enfants.forEach {
                    marcher(it, contextes, diagnostics, liaison, pilePortee, nomsZone, enteteBrisee)
                }
            }
            KtNodeTypes.VALUE_ARGUMENT -> {
                // Un argument NOMMÉ (`copy(x = 1)`) : le nom de l'argument
                // (production `VALUE_ARGUMENT_NAME`) n'est JAMAIS un usage —
                // la VALEUR marche seule.
                noeud.enfants
                    .filter { it.genre != KtNodeTypes.VALUE_ARGUMENT_NAME }
                    .forEach { marcher(it, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee) }
            }
            else -> {
                noeud.enfants.forEach { marcher(it, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee) }
            }
        }
    }

    /**
     * Marche un CHEMIN FQN d'identifiants nus (vague 2c — fiche 028) :
     * `exo.paq.ClassePublique()`, `Aquarium.Bac()`. La racine ne résout
     * pas comme VALEUR (nom nu SIGNALABLE) : si le chemin complet résout
     * comme CLASSE, tous les segments sont muets (seuls les ARGUMENTS de
     * l'appel feuille marchent) ; sinon la RACINE et la FEUILLE APPELÉE
     * sont émis, le MILIEU (paquet potentiel) muet.
     *
     * @return `true` si la marche est CONSOMMÉE par le chemin (l'appelant
     * ne re-marche PAS le récepteur) ; `false` pour la marche standard.
     */
    private fun marcherCheminFqn(
        noeud: NoeudArbre,
        recepteur: NoeudArbre,
        selecteur: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
        nomsZone: Set<String>,
        enteteBrisee: Boolean,
    ): Boolean {
        val segments = chaineIdentifiants(noeud) ?: return false
        if (segments.size < 2) return false

        // La RACINE doit être un nom nu NON résolu (SIGNALABLE) : une racine
        // valeur (locale, paramètre, classe, import) ou un nom de la ZONE DE
        // CONSTRUCTION (paramètre nu du primaire — fiche 028 : le sélecteur
        // d'une valeur en zone relève de la marche standard) relève de la
        // marche standard par sélecteur.
        val racine = segments.first()
        if (racine in nomsZone) return false
        if (contextes.univers.resoudreNomNu(racine, contextes.nomsDeclares, liaison, pile) !=
            UniversPreuves.Verdict.SIGNALABLE
        ) {
            return false
        }

        // Les ARGUMENTS de l'appel feuille marchent dans tous les cas.
        val arguments: List<NoeudArbre> = if (selecteur.genre == KtNodeTypes.CALL_EXPRESSION) {
            selecteur.enfants.filter { it.genre != KtNodeTypes.REFERENCE_EXPRESSION }
        } else {
            emptyList()
        }

        // Le chemin complet RÉSOUT : tout est muet (fiche 028 — « expression
        // complétée sans import »).
        if (contextes.univers.cheminClasse(segments) != null) {
            arguments.forEach { marcher(it, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee) }
            return true
        }

        // Le chemin de PACKAGE (vague 2e — fiche 025 : « appel complet
        // qualifié, premier segment (paquet) ») : le PRÉFIXE du chemin est
        // un package CONNU de l'index (`kotlin.io.println(…)` — la racine
        // et le milieu sont des PAQUETS, jamais signalés) ; la FEUILLE
        // seule est faillible, comme APPELABLE de ce package (voie 6).
        if (segments.size >= 2) {
            val prefixe = segments.dropLast(1).joinToString(".")
            val index = contextes.univers.index
            if (index != null && index.paquetConnu(prefixe)) {
                val feuilleNom = segments.last()
                val appelee = selecteur.genre == KtNodeTypes.CALL_EXPRESSION
                if (appelee && contextes.univers.nomsNusProuvables && !contextes.univers.script) {
                    if (index.topParNom(feuilleNom).none { it.paquet == prefixe }) {
                        selecteur.enfants
                            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                            ?.let { feuille ->
                                emettreNonResolu(
                                    reference = feuille,
                                    nom = feuilleNom,
                                    forme = FormeReference.NOM_NU,
                                    issue = IssueSelection.INCONNU,
                                    recepteur = null,
                                    contextes = contextes,
                                    diagnostics = diagnostics,
                                )
                            }
                    }
                }
                arguments.forEach { marcher(it, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee) }
                return true
            }
        }

        // Le chemin ne résout pas : la RACINE (récepteur inconnu — miroir
        // kotlinc « unresolved: exo ») est émise ; la FEUILLE APPELÉE ne
        // l'est que pour un chemin de CLASSE QUALIFIÉE (≥ 3 segments :
        // paquet.paquet.Classe — fiche 028) — un sélecteur de MÉTHODE sur
        // récepteur inconnel (2 segments : `contexte.toString()`) reste au
        // récepteur seul ; le MILIEU (paquet) reste toujours muet.
        if (contextes.univers.nomsNusProuvables && !contextes.univers.script) {
            if (segments.size >= 3 && selecteur.genre == KtNodeTypes.CALL_EXPRESSION) {
                selecteur.enfants
                    .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                    ?.let { feuille ->
                        emettreNonResolu(
                            reference = feuille,
                            nom = feuille.texteCanonique,
                            forme = FormeReference.NOM_NU,
                            issue = IssueSelection.INCONNU,
                            recepteur = null,
                            contextes = contextes,
                            diagnostics = diagnostics,
                        )
                    }
            }
            noeudReferenceRacine(noeud)?.let { racineNode ->
                emettreNonResolu(
                    reference = racineNode,
                    nom = racine,
                    forme = FormeReference.NOM_NU,
                    issue = IssueSelection.INCONNU,
                    recepteur = null,
                    contextes = contextes,
                    diagnostics = diagnostics,
                )
            }
        }
        arguments.forEach { marcher(it, contextes, diagnostics, liaison, pile, nomsZone, enteteBrisee) }
        return true
    }

    /**
     * Les SEGMENTS d'une chaîne qualifiée d'IDENTIFIANTS NUS (`exo.paq.X()`)
     * — `null` si la chaîne contient autre chose qu'une référence nue (un
     * appel au MILIEU, un littéral, un `this`) : ce n'est pas un chemin FQN.
     */
    private fun chaineIdentifiants(noeud: NoeudArbre): List<String>? {
        val segments = mutableListOf<String>()
        var courant: NoeudArbre = noeud
        while (courant.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION) {
            val recepteur = courant.enfants.firstOrNull() ?: return null
            val selecteur = courant.enfants.lastOrNull() ?: return null
            if (recepteur === selecteur) return null
            when (selecteur.genre) {
                KtNodeTypes.REFERENCE_EXPRESSION -> segments.add(0, selecteur.texteCanonique)
                KtNodeTypes.CALL_EXPRESSION -> {
                    // La feuille APPELÉE : sa callee doit être une référence
                    // nue ; le reste de la chaîne continue à gauche.
                    val callee = selecteur.enfants.firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                        ?: return null
                    if (selecteur.enfants.count { it.genre == KtNodeTypes.REFERENCE_EXPRESSION } > 1) return null
                    segments.add(0, callee.texteCanonique)
                }
                else -> return null
            }
            if (recepteur.genre == KtNodeTypes.REFERENCE_EXPRESSION) {
                segments.add(0, recepteur.texteCanonique)
                return segments
            }
            courant = recepteur
        }
        return null
    }

    /** Le nœud RÉFÉRENCE racine d'une chaîne qualifiée (le plus à gauche). */
    private fun noeudReferenceRacine(noeud: NoeudArbre): NoeudArbre? {
        var courant: NoeudArbre = noeud
        while (courant.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION) {
            val recepteur = courant.enfants.firstOrNull() ?: return null
            if (recepteur.genre == KtNodeTypes.REFERENCE_EXPRESSION) return recepteur
            if (recepteur.genre != KtNodeTypes.DOT_QUALIFIED_EXPRESSION) return null
            courant = recepteur
        }
        return null
    }

    /**
     * Vérifie un TYPE QUALIFIÉ (vague 2c — fiches 044 E4/E4bis, 028/029) :
     * le CHEMIN COMPLET des segments (`android.os.Bundle`, `Aquarium.Bac`,
     * `Conteneur.Fabrique`) résout (projet, import + `$` classpath, classe
     * de packages) → MUET ; sinon UN SEUL segment faillible : le DERNIER.
     */
    private fun verifierTypeQualifie(
        noeud: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        pile: PileThis,
        enteteBrisee: Boolean,
    ) {
        if (contextes.directivesBruisees || enteteBrisee) return
        val segments = segmentsTypeQualifie(noeud)
        if (segments.isEmpty()) return
        if (contextes.univers.cheminClasse(segments) != null) return
        if (!contextes.univers.nomsNusProuvables) return
        // Le DERNIER segment seul est faillible (le nom de classe) : les
        // segments amont sont des PAQUETS potentiels (E4bis).
        val feuille = noeud.enfants.lastOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?: return
        val nom = feuille.texteCanonique
        if (nom.isEmpty()) return
        emettreNonResolu(
            reference = feuille,
            nom = nom,
            forme = FormeReference.NOM_NU,
            issue = IssueSelection.INCONNU,
            recepteur = null,
            contextes = contextes,
            diagnostics = diagnostics,
        )
    }

    /** Les SEGMENTS d'un USER_TYPE qualifié (gauche → droite). */
    private fun segmentsTypeQualifie(noeud: NoeudArbre): List<String> {
        val segments = mutableListOf<String>()
        var courant: NoeudArbre = noeud
        while (true) {
            val nom = courant.enfants
                .lastOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                ?.texteCanonique
            if (nom.isNullOrEmpty()) return emptyList()
            segments.add(0, nom)
            val interne = courant.enfants.firstOrNull { it.genre == KtNodeTypes.USER_TYPE } ?: break
            courant = interne
        }
        return segments
    }


    /**
     * La pile du RÉCEPTEUR IMPLICITE d'un `with(récepteur) { … }` (vague
     * 2f — fiche 047) : le PREMIER ARGUMENT typé devient le this implicite
     * de la lambda traînante ; `null` pour tout autre appel.
     */
    private fun pileImpliciteWith(
        appel: NoeudArbre,
        contextes: Contextes,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
    ): PileThis? {
        val nom = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique
        if (nom != "with") return null
        val premierArgument = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST }
            ?.enfants?.firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT }
            ?.enfants?.firstOrNull() ?: return null
        val type = TypageBorne.typeDeLexpression(premierArgument, contextes.univers, liaison, pile)
            ?: return null
        return pile.empiler(Couches.deType(contextes.univers, type))
    }

    /** Vérifie un NOM NU (référence d'expression ou callee d'appel nu). */
    private fun verifierNomNu(
        reference: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
        nomsZone: Set<String>,
        enteteBrisee: Boolean,
    ) {
        val nom = reference.texteCanonique
        if (nom.isEmpty()) return
        // Le mot-clé `super` (le parseur l'enveloppe d'un REFERENCE_EXPRESSION
        // dans SUPER_EXPRESSION — vague 2c, fiche 044 E4) : jamais un nom nu.
        if (nom == "super") return
        if (contextes.directivesBruisees || enteteBrisee) return
        // Une couche ENGLOBANTE ouverte ou opaque bloque la preuve des noms
        // nus (vague 2c — la clôture ne se clôt pas : chaîne non fermée,
        // enum, couche illisible — jamais un faux positif).
        if (pile.bloqueNomsNus) return
        // La ZONE DE CONSTRUCTION (paramètres nus du primaire, `field` des
        // accesseurs — fiche 028).
        if (nom in nomsZone) return
        val verdict = contextes.univers.resoudreNomNu(nom, contextes.nomsDeclares, liaison, pile)
        if (verdict != UniversPreuves.Verdict.SIGNALABLE) return
        emettreNonResolu(
            reference = reference,
            nom = nom,
            forme = FormeReference.NOM_NU,
            issue = IssueSelection.INCONNU,
            recepteur = null,
            contextes = contextes,
            diagnostics = diagnostics,
        )
    }

    /** Vérifie un NOM DE TYPE (position `USER_TYPE`). */
    private fun verifierNomType(
        reference: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        pile: PileThis,
        enteteBrisee: Boolean,
    ) {
        val nom = reference.texteCanonique
        if (nom.isEmpty()) return
        if (contextes.directivesBruisees || enteteBrisee) return
        val verdict = contextes.univers.resoudreNomType(nom, pile)
        if (verdict != UniversPreuves.Verdict.SIGNALABLE) return
        emettreNonResolu(
            reference = reference,
            nom = nom,
            forme = FormeReference.NOM_NU,
            issue = IssueSelection.INCONNU,
            recepteur = null,
            contextes = contextes,
            diagnostics = diagnostics,
        )
    }

    /** Vérifie un SÉLECTEUR qualifié (callee ou propriété du récepteur). */
    private fun verifierSelecteur(
        recepteur: NoeudArbre,
        selecteur: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
        enteteBrisee: Boolean,
    ) {
        if (contextes.directivesBruisees || enteteBrisee) return
        val noeudNom = when (selecteur.genre) {
            KtNodeTypes.REFERENCE_EXPRESSION -> selecteur
            KtNodeTypes.CALL_EXPRESSION ->
                selecteur.enfants.firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            else -> null
        } ?: return
        val nom = noeudNom.texteCanonique
        if (nom.isEmpty()) return

        // Le type du récepteur (ADR 0223 : la sélection par types complets
        // — un récepteur PROJET ou de BIBLIOTHÈQUE lisible dans la vue JVM
        // (vague 2b), sinon le sélecteur reste muet). La vague 2c y passe
        // la PILE DES THIS (`this.layoutInflater`, membres implicites).
        val typeRecepteur = TypageBorne.typeDeLexpression(recepteur, contextes.univers, liaison, pile)
            ?: return
        val verdict = contextes.univers.verifierSelecteur(typeRecepteur, nom, pile)
        if (verdict != UniversPreuves.Verdict.SIGNALABLE) return
        emettreNonResolu(
            reference = noeudNom,
            nom = nom,
            forme = FormeReference.SELECTEUR_QUALIFIE_ABSENCE_PROUVEE,
            issue = IssueSelection.ABSENCE_PROUVEE,
            recepteur = recepteurFqcn(typeRecepteur),
            contextes = contextes,
            diagnostics = diagnostics,
        )
    }

    /** Le FQCN d'affichage d'un type de récepteur (journal explain). */
    private fun recepteurFqcn(type: TypeValeur): String = when (type) {
        is TypeValeur.Projet -> type.type.fqcn
        is TypeValeur.Jvm -> type.fqcn
    }

    /** Émet un `kt.unresolved` et consigne le journal explain (fiche 006). */
    private fun emettreNonResolu(
        reference: NoeudArbre,
        nom: String,
        forme: FormeReference,
        issue: IssueSelection,
        recepteur: String?,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        val (ligne, colonne) = contextes.position(reference.debut)
        ExplicationsNonResolus.consigner(
            reference = nom,
            forme = forme,
            issue = issue,
            recepteurTypé = recepteur,
            etatScan = contextes.univers.etatScan,
            indexPortant = contextes.univers.index != null,
            script = contextes.univers.script,
            ligne = ligne,
            colonne = colonne,
        )
        diagnostics.add(
            DiagnosticKotlin(
                code = CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE,
                severite = SeveriteKotlin.ERREUR,
                plage = PlageTexte(reference.debut, reference.fin),
                message = "Référence non résolue : « $nom »",
                tags = emptyList(),
                informationsLiees = emptyList(),
            ),
        )
    }

    /**
     * Les contextes partagés de la passe : l'univers des preuves, la garde
     * documentaire (noms déclarés) et la garde des directives brisées.
     */
    private class Contextes(
        private val document: DocumentOuvert,
        internal val univers: UniversPreuves,
        internal val nomsDeclares: Set<String>,
        internal val directivesBruisees: Boolean,
    ) {

        /** La ligne et colonne (1-based) d'un offset du document. */
        fun position(decalage: Int): Pair<Int, Int> {
            var ligne = 1
            var debutLigne = 0
            val borne = decalage.coerceAtMost(document.texte.length)
            for (i in 0 until borne) {
                if (document.texte[i] == '\n') {
                    ligne++
                    debutLigne = i + 1
                }
            }
            return Pair(ligne, decalage - debutLigne + 1)
        }

        /** La liaison DU DOCUMENT (en-têtes de classes, zones hors fonction). */
        fun liaisonDuDocument(): MarcheTypee.Liaison =
            MarcheTypee.Liaison(arbre = univers.arbre, univers = univers, region = null)

        /**
         * La liaison LOCALE d'une région : les paramètres et locales de la
         * région d'abord (typés par leur type ÉCRIT ou leur initialiseur —
         * fiche 022 : `val q = p.copy(x = 1)`), puis la liaison du document
         * hors de la région.
         */
        fun liaisonLocale(region: NoeudArbre): MarcheTypee.Liaison =
            MarcheTypee.Liaison(arbre = univers.arbre, univers = univers, region = region)

    }
}

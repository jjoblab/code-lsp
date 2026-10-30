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
import jo.codeide.lsp.kotlin.resolve.ContributionFichier
import jo.codeide.lsp.kotlin.resolve.EtatScan
import jo.codeide.lsp.kotlin.resolve.ExplicationsNonResolus
import jo.codeide.lsp.kotlin.resolve.FormeReference
import jo.codeide.lsp.kotlin.resolve.IssueSelection
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
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
        for (declaration in arbre.declarations()) {
            visiterDeclaration(declaration.noeud, contextes, diagnostics, heritageOuvert = false)
        }
        return diagnostics
    }

    // ------------------------------------------------------------------
    // Régions (la garde par déclaration — fiche 017).
    // ------------------------------------------------------------------

    /**
     * Visite une déclaration : région propre si son sous-arbre est sain ;
     * une déclaration de type visite son EN-TÊTE (types de la clause et du
     * constructeur primaire) puis ses MEMBRES récursivement.
     *
     * @param noeud la déclaration visitée ;
     * @param contextes les contextes de la passe ;
     * @param diagnostics l'accumulateur des émissions ;
     * @param heritageOuvert une déclaration englobante porte-t-elle une
     * clause de supertype écrite ou est-elle un enum (noms nus muets) ;
     * @param enteteBrisee l'en-tête de la classe englobante est-elle brisée
     * (absence muette dans les membres — fiche 017).
     */
    private fun visiterDeclaration(
        noeud: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        heritageOuvert: Boolean,
        enteteBrisee: Boolean = false,
    ) {
        when (noeud.genre) {
            KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION -> {
                val corps = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY }
                val enteteSaine = entetePropre(noeud, corps)
                if (enteteSaine) {
                    visiterZone(noeud, corps, contextes, diagnostics)
                }
                val clauseEcrit = noeud.enfants.any { it.genre == KtNodeTypes.SUPER_TYPE_LIST }
                val estEnum = porteurModificateur(noeud, KtTokens.ENUM_MODIFIER)
                val ouvert = heritageOuvert || clauseEcrit || estEnum
                if (corps != null) {
                    for (membre in corps.enfants) {
                        visiterDeclaration(membre, contextes, diagnostics, ouvert, !enteteSaine)
                    }
                }
            }
            else -> {
                // Fonction, propriété, typealias, entrée d'enum : une région
                // si le sous-arbre est propre (les déclarations cassées ne
                // diagnostiquent RIEN — leurs noms restent des gardes).
                if (sousArbrePropre(noeud)) {
                    visiterRegion(noeud, contextes, diagnostics, heritageOuvert, enteteBrisee)
                }
            }
        }
    }

    /**
     * Visite l'EN-TÊTE d'une déclaration de type saine : le nœud SANS son
     * corps (les types de la clause de supertypes et du constructeur primaire
     * sont vérifiés — ce sont des positions de types à part entière).
     */
    private fun visiterZone(
        noeud: NoeudArbre,
        corps: NoeudArbre?,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        val liaison = contextes.liaisonDuDocument()
        for (enfant in noeud.enfants) {
            if (enfant === corps) continue
            marcher(enfant, contextes, diagnostics, liaison, heritageOuvert = false, enteteBrisee = false)
        }
    }

    /** Visite une RÉGION PROPRE (fonction, propriété, membre sain). */
    private fun visiterRegion(
        noeud: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        heritageOuvert: Boolean,
        enteteBrisee: Boolean,
    ) {
        if (noeud.genre == KtNodeTypes.FUN) {
            verifierRetourManquant(noeud, diagnostics)
        }
        val liaison = contextes.liaisonLocale(noeud)
        marcher(noeud, contextes, diagnostics, liaison, heritageOuvert, enteteBrisee)
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
        fun collecter(noeud: NoeudArbre) {
            if (noeud.genre in UniversPreuves.GENRES_DECLARATION) {
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
            noeud.enfants.forEach(::collecter)
        }
        collecter(arbre.noeud)
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
     * nu, sélecteur qualifié, type écrit) et rend les arguments, lambdas et
     * expressions imbriquées à la marche.
     *
     * @param noeud le nœud visité ;
     * @param liaison la liaison nom → type écrit des portées locales ;
     * @param heritageOuvert l'univers des membres de la classe englobante
     * est ouvert (noms nus muets — fiche 022) ;
     * @param enteteBrisee l'en-tête de la classe englobante est brisée
     * (absence muette — fiche 017).
     */
    private fun marcher(
        noeud: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        liaison: MarcheTypee.Liaison,
        heritageOuvert: Boolean,
        enteteBrisee: Boolean,
    ) {
        when (noeud.genre) {
            KtNodeTypes.USER_TYPE -> {
                // Un type QUALIFIÉ (`a.b.C`) : les segments restent muets
                // (un seul segment faillible — fiche 044, vague 2c). Le type
                // SIMPLE porte la vérification ; ses ARGUMENTS marchent —
                // la RÉFÉRENCE du nom ne re-marche PAS en nom nu (elle est
                // une position de TYPE, jamais un usage : l'émission y
                // serait DOUBLÉE).
                if (noeud.enfants.none { it.genre == KtNodeTypes.USER_TYPE }) {
                    noeud.enfants
                        .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                        ?.let { verifierNomType(it, contextes, diagnostics, enteteBrisee) }
                }
                noeud.enfants
                    .filter { it.genre != KtNodeTypes.REFERENCE_EXPRESSION }
                    .forEach { marcher(it, contextes, diagnostics, liaison, heritageOuvert, enteteBrisee) }
            }
            KtNodeTypes.DOT_QUALIFIED_EXPRESSION, KtNodeTypes.SAFE_ACCESS_EXPRESSION -> {
                val recepteur = noeud.enfants.firstOrNull()
                val selecteur = noeud.enfants.lastOrNull()
                if (recepteur != null && selecteur != null && recepteur !== selecteur) {
                    // Le RÉCEPTEUR marche d'abord (nom nu candidat), le
                    // sélecteur est vérifié par le type complet du récepteur.
                    marcher(recepteur, contextes, diagnostics, liaison, heritageOuvert, enteteBrisee)
                    verifierSelecteur(recepteur, selecteur, contextes, diagnostics, liaison, enteteBrisee)
                    // Les ARGUMENTS et lambdas de l'appel sélecteur marchent
                    // (la callee elle-même ne re-marche pas).
                    if (selecteur.genre == KtNodeTypes.CALL_EXPRESSION) {
                        selecteur.enfants
                            .filter { it.genre != KtNodeTypes.REFERENCE_EXPRESSION }
                            .forEach { marcher(it, contextes, diagnostics, liaison, heritageOuvert, enteteBrisee) }
                    }
                }
            }
            KtNodeTypes.CALL_EXPRESSION -> {
                // Un appel NU : la callee est un nom nu candidat, les
                // arguments et lambdas marchent.
                noeud.enfants
                    .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                    ?.let { verifierNomNu(it, contextes, diagnostics, liaison, heritageOuvert, enteteBrisee) }
                noeud.enfants
                    .filter { it.genre != KtNodeTypes.REFERENCE_EXPRESSION }
                    .forEach { marcher(it, contextes, diagnostics, liaison, heritageOuvert, enteteBrisee) }
            }
            KtNodeTypes.REFERENCE_EXPRESSION -> {
                verifierNomNu(noeud, contextes, diagnostics, liaison, heritageOuvert, enteteBrisee)
            }
            KtNodeTypes.VALUE_ARGUMENT -> {
                // Un argument NOMMÉ (`copy(x = 1)`) : le nom de l'argument
                // (production `VALUE_ARGUMENT_NAME`) n'est JAMAIS un usage —
                // la VALEUR marche seule.
                noeud.enfants
                    .filter { it.genre != KtNodeTypes.VALUE_ARGUMENT_NAME }
                    .forEach { marcher(it, contextes, diagnostics, liaison, heritageOuvert, enteteBrisee) }
            }
            else -> {
                noeud.enfants.forEach { marcher(it, contextes, diagnostics, liaison, heritageOuvert, enteteBrisee) }
            }
        }
    }

    /** Vérifie un NOM NU (référence d'expression ou callee d'appel nu). */
    private fun verifierNomNu(
        reference: NoeudArbre,
        contextes: Contextes,
        diagnostics: MutableList<DiagnosticKotlin>,
        liaison: MarcheTypee.Liaison,
        heritageOuvert: Boolean,
        enteteBrisee: Boolean,
    ) {
        val nom = reference.texteCanonique
        if (nom.isEmpty()) return
        if (contextes.directivesBruisees || enteteBrisee || heritageOuvert) return
        val verdict = contextes.univers.resoudreNomNu(nom, contextes.nomsDeclares, liaison)
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
        enteteBrisee: Boolean,
    ) {
        val nom = reference.texteCanonique
        if (nom.isEmpty()) return
        if (contextes.directivesBruisees || enteteBrisee) return
        val verdict = contextes.univers.resoudreNomType(nom)
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
        // (vague 2b), sinon le sélecteur reste muet).
        val typeRecepteur = TypageBorne.typeDeLexpression(recepteur, contextes.univers, liaison)
            ?: return
        val verdict = contextes.univers.verifierSelecteur(typeRecepteur, nom)
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

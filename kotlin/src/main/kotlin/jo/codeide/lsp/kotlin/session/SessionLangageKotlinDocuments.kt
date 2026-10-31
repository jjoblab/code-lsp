// CodeIDE — session de langage Kotlin « documents » (module :kotlin, étage 1
// vague 1b — session documents ; vague 1c — scan paresseux fiche 041 ;
// vague 2b — quick-fix d'import `importsCandidats`, fiche 046). Code NEUF
// jjoblab (clean-room — contrat : fiches 049/042/041/046 du
// cahier docs/cahier-specifications-kotlin/ ; concurrence ADR 0117/ADR 0212
// ancien : verrou lecture-écriture, écritures = mutations de documents et
// définitions, lectures parallèles = requêtes, JAMAIS de scan sous verrou).
package jo.codeide.lsp.kotlin.session

import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.DiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EmplacementKotlin
import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.PlagePliableKotlin
import jo.codeide.lsp.kotlin.api.PlageRenommableKotlin
import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.api.ReponseCompletionKotlin
import jo.codeide.lsp.kotlin.api.SessionLangageKotlin
import jo.codeide.lsp.kotlin.api.SurlignageDocumentKotlin
import jo.codeide.lsp.kotlin.api.SurvolKotlin
import jo.codeide.lsp.kotlin.index.IndexAppelablesKotlin
import jo.codeide.lsp.kotlin.parse.ParseurKotlin
import jo.codeide.lsp.kotlin.parse.kt.KtDirectiveImport
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.EtatScan
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Un document ouvert d'une session (interne) : son texte complet et la
 * façade d'arbre analysée (immuable — chaque mutation ré-analyse).
 *
 * @property texte le contenu complet courant du document ;
 * @property arbre la racine de la façade `Kt*` du texte courant.
 */
internal class DocumentOuvert(
    internal val texte: String,
    internal val arbre: KtFichier,
)

/**
 * La session de langage Kotlin du noyau — implémentation du contrat
 * [SessionLangageKotlin] (interne au module : l'hôte ne voit que le SPI
 * `api/`, D6).
 *
 * ## Concurrence (ADR 0117 — schéma par périmètre ; ADR 0212)
 *
 * UN verrou lecture-écriture pour cette session :
 * - ÉCRIVENT : [ouvrir], [modifier], [fermer], [definirClasspath], [close]
 *   et la PUBLICATION du scan ([rafraichirSources] — la passe du scan court
 *   sur le fil dédié du scanneur, HORS verrou : jamais de scan sous verrou,
 *   l'attente de la passe est elle aussi hors verrou) ;
 * - LISENT EN PARALLÈLE : [diagnostics], [plagesPliables], [survol] et le
 *   guichet d'index [indexSiPret] — aucune requête ne mute l'état, aucune
 *   ré-entrance lecture→écriture n'existe.
 *
 * L'analyse (parse complet) se fait SOUS le verrou d'écriture, au moment de
 * la mutation : les requêtes ne relisent que des arbres immuables en cache.
 *
 * ## Scan des racines (L1 — vague 1c, fiche 041)
 *
 * Le scan des racines sources vit dans le scanneur paresseux
 * ([ScanProjetParesseux]) : [definirClasspath] y pose les racines (l'amorce
 * WARM part à la définition — le coût n'est pas à la première frappe), mais
 * la session ne SERT un snapshot qu'après [rafraichirSources] (contrat de la
 * vague 1b préservé : avant le rafraîchissement, une déclaration externe
 * reste hors de portée ; la définition reste un branchement qui ne re-lect
 * rien). [rafraichirSources] rafraîchit puis attend l'aboutissement de la
 * passe (HORS verrou) et publie le snapshot immuable — publication hors du
 * chemin des requêtes.
 *
 * ## Classpath (fiche 042 — ADR 0211 ; vague 1d — nourrissage réel)
 *
 * Câblée à un moteur nourricier ([NourrissageMoteur] non nul), la session
 * BRANCHE : [definirClasspath] pose classpath et racines sans rien relire,
 * et chaque requête consulte l'état publié par le moteur via [indexSiPret]
 * — sous-lecture honnête tant que rien n'est couvert, guérison à la requête
 * suivante dès publication. Sans moteur (usage direct — tests, packs
 * anciens), la définition nourrit ICI, SYNCHRONE : l'index LOCAL de la
 * session décode les entrées et la vue n'est publiée que si la couverture
 * tient (une entrée écartée = un guichet muet — jamais de faux négatif).
 *
 * @property moteur la couture de nourrissage du moteur créateur (`null` =
 * usage direct : contrat synchrone) — ne change jamais après construction.
 */
internal class SessionLangageKotlinDocuments(
    private val moteur: NourrissageMoteur?,
    bornFichiersScan: Int = ScanProjetParesseux.BORN_FICHIERS,
    chaineCompletion: jo.codeide.lsp.kotlin.api.ChaineClassementKotlin =
        jo.codeide.lsp.kotlin.api.ChaineClassementKotlin(),
) : SessionLangageKotlin {

    /** Le verrou de la session (écritures mutent, lectures parallèles). */
    private val verrou = ReentrantReadWriteLock()

    /** Le parseur de la session (sans état — sert chaque mutation). */
    private val parseur = ParseurKotlin()

    /**
     * L'ARRANGEMENT de complétion de la session (étage 3 vague 3c — ADR
     * 0113 : chaîne de classement + GELS par clé de contexte — les gels
     * sont À SESSION, états étanches) ; la chaîne est INJECTABLE (le banc
     * de qualité compare la chaîne livrée à la naïve alphabétique).
     */
    private val arrangementCompletion =
        jo.codeide.lsp.kotlin.api.ArrangementListeKotlin(chaineCompletion)

    /** Le drapeau de fermeture : après close(), tout est no-op honnête. */
    private var fermee: Boolean = false

    /** Les documents ouverts, en ordre d'ouverture (LinkedHashMap). */
    private val documents = LinkedHashMap<String, DocumentOuvert>()

    /** Le classpath posé par la dernière définition (entrées + racines). */
    private var classpathPose: ClasspathKotlin? = null

    /**
     * L'index LOCAL de la session (usage direct — vague 1d) : nourri ICI,
     * SYNCHRONE à chaque définition hors moteur nourricier (contrat
     * historique ADR 0211). Interne : les fiches 034/008 l'inspectent
     * (couverture, segments). Un moteur nourricier ne l'utilise jamais —
     * la session branchée sert le snapshot PUBLIÉ.
     */
    internal val indexLocal = IndexAppelablesKotlin()

    /** La vue locale (usage direct uniquement — nourrie ICI, synchrone). */
    private var vueLocale: SnapshotClasspath? = null

    /** Le dernier scan des racines publié (L1 — `null` tant qu'inexistant). */
    private var scanPublie: ScanSources? = null

    /**
     * Le scanneur paresseux des racines sources (L1 — vague 1c, fiche 041 :
     * passe bornée annulable sur fil dédié, cache mtime, progression). Ses
     * résultats ne sont SERVIS par la session qu'à la publication de
     * [rafraichirSources] (contrat vague 1b inchangé).
     */
    private val scanneur = ScanProjetParesseux(bornFichiersScan)

    // ------------------------------------------------------------------
    // Cycle de vie des documents (écritures).
    // ------------------------------------------------------------------

    /**
     * Ouvre un document : le texte devient l'état courant, analysé sous le
     * verrou d'écriture. Une session fermée ignore silencieusement l'appel.
     */
    override fun ouvrir(uri: String, texte: CharSequence) {
        verrou.write {
            if (fermee) return@write
            documents[uri] = analyser(uri, texte)
        }
    }

    /**
     * Modifie un document (synchronisation intégrale) : vaut une ouverture
     * si le document est inconnu. Une session fermée ignore l'appel.
     */
    override fun modifier(uri: String, texte: CharSequence) {
        verrou.write {
            if (fermee) return@write
            documents[uri] = analyser(uri, texte)
        }
    }

    /**
     * Ferme (oublie) un document ; une URI inconnue est ignorée sans
     * exception.
     */
    override fun fermer(uri: String) {
        verrou.write {
            if (fermee) return@write
            documents.remove(uri)
        }
    }

    // ------------------------------------------------------------------
    // Requêtes (lectures parallèles).
    // ------------------------------------------------------------------

    /**
     * Rend les diagnostics courants du document : erreurs de syntaxe
     * (`kt.syntax` — plage et message du parseur), imports inutilisés
     * (`kt.unusedImport` — joker et directives en édition JAMAIS signalés),
     * imports conflictuels (`kt.conflictingImport` — signalés sur le plus
     * tardif, information liée sur la première directive, KP15) et — depuis
     * l'étage 2 — la passe SÉMANTIQUE (`kt.unresolved` par preuve d'absence
     * avec la garde par déclaration, `kt.missingReturn` — fiches 017/022).
     *
     * La passe sémantique consulte l'UNIVERS des preuves HORS verrou
     * d'écriture : elle REJOINT la passe de scan en vol (jamais de scan sur
     * le fil appelant — fiche 041) et consulte le guichet d'index À CHAQUE
     * requête (fiche 042) ; le régime dégradé (classpath non posé ou passe
     * non publiée) ne prouve RIEN (sous-lecture honnête).
     *
     * @param uri l'identifiant du document ;
     * @return les diagnostics, en ordre du document ; liste VIDE pour un
     * document inconnu, fermé ou sain — jamais `null`, jamais d'exception.
     */
    override fun diagnostics(uri: String): List<DiagnosticKotlin> {
        val document =
            verrou.read {
                if (fermee) return@read null
                documents[uri]
            } ?: return emptyList()
        // L'univers des preuves se consulte HORS verrou de lecture : la
        // jointure de la passe de scan peut ATTENDRE (fiche 041) et le
        // guichet d'index lit l'état publié du moteur — aucune de ces
        // lectures ne mutile le document, le verrou n'a rien à garder.
        val syntaxeEtImports = verrou.read { DiagnosticsImports.calculer(document) }
        val guichet = indexSiPret()
        val semantiques = DiagnosticsSemantiques.calculer(
            document = document,
            uri = uri,
            contributionsScan = contributionsAuConsult(uri),
            etatScan = etatScanAuConsult(),
            index = guichet?.vue,
            vueJvm = guichet?.vueJvm,
        )
        return syntaxeEtImports + semantiques
    }

    /**
     * Rend les propositions de complétion au point demandé (étage 3 —
     * ADR 0113 : le pipeline complet — classification du caret, régimes de
     * collecte, arrangement honnête ; le germe membre vague 2a reste couvert
     * par le régime MEMBRE).
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret dans le texte du document ;
     * @return les propositions ARRANGÉES (≤ plafond d'affichage) ; liste
     * VIDE pour un document inconnu/fermé ou une position sans contexte —
     * jamais `null`, jamais d'exception.
     */
    override fun completion(uri: String, deplacement: Int): List<PropositionKotlin> {
        return completionArrangee(uri, deplacement).propositions
    }

    /**
     * Rend la réponse ARRANGÉE de complétion au point demandé (étage 3 —
     * ADR 0113 : le genre du contexte et le TOTAL RÉEL de l'espace de
     * candidats voyagent avec la liste plafonnée — le plafond ne ment
     * jamais).
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret dans le texte du document ;
     * @return la réponse arrangée ; propositions VIDE et genre INCONNU pour
     * un document inconnu/fermé — jamais `null`, jamais d'exception.
     */
    override fun completionArrangee(uri: String, deplacement: Int): ReponseCompletionKotlin {
        val document =
            verrou.read {
                if (fermee) return@read null
                documents[uri]
            } ?: return ReponseCompletionKotlin(GenreContexteKotlin.INCONNU, emptyList(), 0)
        val guichet = indexSiPret()
        return CompletionPipeline.proposer(
            document = document,
            uri = uri,
            deplacement = deplacement,
            contributionsScan = contributionsAuConsult(uri),
            etatScan = etatScanAuConsult(),
            index = guichet?.vue,
            vueJvm = guichet?.vueJvm,
            arrangement = arrangementCompletion,
        )
    }

    /**
     * Rend les FQCN candidats à l'IMPORT pour le nom sous le point (fiche
     * 046 — vague 2b) : les classes des ENTRÉES du classpath portant
     * EXACTEMENT ce nom simple, MOINS celles déjà résolues (import exact de
     * même nom effectif, ou joker couvrant le package). Document inconnu,
     * position sans identifiant, guichet muet : liste vide honnête.
     */
    override fun importsCandidats(uri: String, deplacement: Int): List<String> {
        val document =
            verrou.read {
                if (fermee) return@read null
                documents[uri]
            } ?: return emptyList()
        val guichet = indexSiPret() ?: return emptyList()

        // L'IDENTIFIANT sous le point : la plus profonde référence couvrant
        // la position (nom de type ou d'expression).
        val identifiant = referenceCouvrante(document.arbre.noeud, deplacement) ?: return emptyList()
        val nom = identifiant.texteCanonique
        if (nom.isEmpty() || nom.contains('.')) return emptyList()

        // Déjà résolu par un import EXACT (même nom effectif) : rien à
        // proposer ; les candidats couverts par un JOKER sont écartés.
        val imports = document.arbre.imports()
        if (imports.any { nomEffectifImport(it) == nom }) return emptyList()
        val jokers = imports.filter { it.estJoker() }.mapNotNull { it.nomQualifie() }.toSet()

        // Déjà résolu par la RÉSOLUTION elle-même (vague 2c — fiche 044 :
        // un membre HÉRITÉ ne propose AUCUN import — même prédicat que le
        // diagnostic ADR 0216, sinon l'ampoule proposerai un import absurde
        // pour un appel déjà résolu).
        val univers = construireUnivers(document, uri, guichet.vue, guichet.vueJvm)
        if (univers != null) {
            val region = regionContenantDocument(document.arbre.noeud, deplacement)
            val liaison = jo.codeide.lsp.kotlin.resolve.MarcheTypee.Liaison(
                arbre = document.arbre, univers = univers, region = region,
            )
            val pile = jo.codeide.lsp.kotlin.resolve.Couches.aPosition(document.arbre, univers, liaison, deplacement)
            val declares = nomsDeclaresDuDocument(document)
            if (univers.resoudreNomNu(nom, declares, liaison, pile) != jo.codeide.lsp.kotlin.resolve.UniversPreuves.Verdict.SIGNALABLE &&
                univers.resoudreNomType(nom, pile) != jo.codeide.lsp.kotlin.resolve.UniversPreuves.Verdict.SIGNALABLE
            ) {
                return emptyList()
            }
        }

        return guichet.vueJvm.fqcnParNomSimple(nom)
            .filterNot { fqcn ->
                val paquet = fqcn.substringBeforeLast('.', "")
                paquet in jokers
            }
    }

    /** La plus profonde déclaration FUN/PROPERTY couvrant la position (ou `null`). */
    private fun regionContenantDocument(
        racine: jo.codeide.lsp.kotlin.parse.NoeudArbre,
        deplacement: Int,
    ): jo.codeide.lsp.kotlin.parse.NoeudArbre? {
        var trouve: jo.codeide.lsp.kotlin.parse.NoeudArbre? = null
        fun marcher(noeud: jo.codeide.lsp.kotlin.parse.NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == org.jetbrains.kotlin.kmp.parser.KtNodeTypes.FUN ||
                noeud.genre == org.jetbrains.kotlin.kmp.parser.KtNodeTypes.PROPERTY
            ) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /** Les noms DÉCLARÉS du document (la garde documentaire, fiche 017). */
    private fun nomsDeclaresDuDocument(document: DocumentOuvert): Set<String> {
        val noms = mutableSetOf<String>()
        fun collecter(noeud: jo.codeide.lsp.kotlin.parse.NoeudArbre, dansPrimaire: Boolean) {
            val parametreNuDuPrimaire = dansPrimaire &&
                noeud.genre == org.jetbrains.kotlin.kmp.parser.KtNodeTypes.VALUE_PARAMETER &&
                noeud.jetonsPropres.none {
                    it.genre == org.jetbrains.kotlin.kmp.lexer.KtTokens.VAL_KEYWORD ||
                        it.genre == org.jetbrains.kotlin.kmp.lexer.KtTokens.VAR_KEYWORD
                }
            if (noeud.genre in jo.codeide.lsp.kotlin.resolve.UniversPreuves.GENRES_DECLARATION && !parametreNuDuPrimaire) {
                noeud.jetonsPropres
                    .firstOrNull { it.genre == org.jetbrains.kotlin.kmp.lexer.KtTokens.IDENTIFIER }?.texte
                    ?.let { noms.add(it) }
            }
            for (enfant in noeud.enfants) {
                collecter(enfant, dansPrimaire || enfant.genre == org.jetbrains.kotlin.kmp.parser.KtNodeTypes.PRIMARY_CONSTRUCTOR)
            }
        }
        collecter(document.arbre.noeud, dansPrimaire = false)
        return noms
    }

    /** La plus profonde REFERENCE_EXPRESSION couvrant la position (ou `null`). */
    private fun referenceCouvrante(
        racine: jo.codeide.lsp.kotlin.parse.NoeudArbre,
        deplacement: Int,
    ): jo.codeide.lsp.kotlin.parse.NoeudArbre? {
        var trouve: jo.codeide.lsp.kotlin.parse.NoeudArbre? = null
        fun marcher(noeud: jo.codeide.lsp.kotlin.parse.NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == org.jetbrains.kotlin.kmp.parser.KtNodeTypes.REFERENCE_EXPRESSION) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /** Le nom EFFECTIF d'un import (alias, sinon dernier segment). */
    private fun nomEffectifImport(directive: KtDirectiveImport): String? {
        directive.alias()?.let { return it }
        val chemin = directive.nomQualifie() ?: return null
        return chemin.substringAfterLast('.').ifEmpty { null }
    }

    /**
     * Rend les plages pliables du document (classe, fonctions, imports,
     * KDoc — fiche 049). Document inconnu, fermé ou vide : liste vide.
     */
    override fun plagesPliables(uri: String): List<PlagePliableKotlin> {
        return verrou.read {
            if (fermee) return@read emptyList()
            val document = documents[uri] ?: return@read emptyList()
            PlagesPliablesDocuments.calculer(document)
        }
    }

    /**
     * Rend le survol (S2) au point demandé — chaîne conservatrice : document
     * courant, autres documents ouverts, scan des racines (fiche 049) ;
     * vague 2c (fiche 040) : à défaut, la résolution des membres HÉRITÉS de
     * bibliothèque (vue JVM — `val layoutInflater: LayoutInflater` avec
     * propriétaire). L'univers se construit HORS verrou de lecture (miroir
     * de la passe de diagnostics).
     */
    override fun survol(uri: String, deplacement: Int): SurvolKotlin? {
        val document =
            verrou.read {
                if (fermee) return@read null
                documents[uri]
            } ?: return null
        val autres = verrou.read {
            if (fermee) return@read emptyList()
            documents.entries.filter { it.key != uri }.map { it.value }
        }
        val guichet = indexSiPret()
        val univers = construireUnivers(document, uri, guichet?.vue, guichet?.vueJvm)
        return verrou.read {
            if (fermee) return@read null
            SurvolDocuments.survol(document, autres, scanPublie, deplacement, univers)
        }
    }

    /**
     * Construit l'univers des preuves d'un document (vague 2c — survol et
     * quick-fix d'import consultent la même matière que la passe de
     * diagnostics : modèle du document + contributions, index, vue JVM).
     */
    private fun construireUnivers(
        document: DocumentOuvert,
        uri: String,
        index: jo.codeide.lsp.kotlin.index.VueAppelables?,
        vueJvm: jo.codeide.lsp.kotlin.resolve.VueJvmMembres?,
    ): jo.codeide.lsp.kotlin.resolve.UniversPreuves? {
        if (index == null) return null
        val batisseur = jo.codeide.lsp.kotlin.resolve.ModeleProjet.Batisseur()
        batisseur.ajouter(jo.codeide.lsp.kotlin.resolve.ModeleProjet.contributionKotlin(document.arbre, uri = ""))
        contributionsAuConsult(uri).forEach(batisseur::ajouter)
        return jo.codeide.lsp.kotlin.resolve.UniversPreuves(
            arbre = document.arbre,
            paquet = document.arbre.directivePackage()?.nomQualifie().orEmpty(),
            modele = batisseur.figer(),
            index = index,
            etatScan = etatScanAuConsult(),
            script = uri.endsWith(".kts"),
            vueJvm = vueJvm,
        )
    }

    // ------------------------------------------------------------------
    // Classpath et scan (fiches 042/049).
    // ------------------------------------------------------------------

    /**
     * Pose le classpath : BRANCHEMENT si un moteur nourricier est câblé
     * (aucune re-lecture — les requêtes consultent l'état publié), nourrissage
     * ICI synchrone sinon (usage direct — le décodage des jars vit HORS
     * verrou, ADR 0212 : jamais d'I/O sous le verrou de session). Les racines
     * sont posées au scanneur paresseux — l'amorce WARM y part à la
     * définition (fiche 041) mais la session n'en SERT rien avant
     * [rafraichirSources] : le scan précédent reste abandonné (moins riche
     * mais jamais faux).
     */
    override fun definirClasspath(classpath: ClasspathKotlin) {
        val nourrirLocalement: Boolean? =
            verrou.write {
                if (fermee) return
                classpathPose = classpath
                vueLocale = null
                scanPublie = null
                scanneur.definirRacines(classpath.racinesSources)
                if (moteur == null || !moteur.nourrissageParMoteur) true else null
            }
        if (nourrirLocalement != true) return
        // Nourrissage SYNCHRONE local (usage direct) — HORS verrou : le
        // décodage des entrées peut durer des secondes, il ne tient jamais
        // le verrou d'écriture (miroir de rafraichirSources / ADR 0212).
        indexLocal.definir(classpath.entrees, classpath.dossierIndex?.let(::File))
        verrou.write {
            if (!fermee && classpathPose === classpath && indexLocal.couvre(classpath.entrees)) {
                vueLocale = SnapshotClasspath(classpath, indexLocal)
            }
        }
    }

    /**
     * Re-scanne les racines sources (L1) : rafraîchit la passe du scanneur
     * paresseux (delta-only — cache mtime, fiche 041), ATTEND son
     * aboutissement HORS verrou (jamais de scan sous verrou — ADR 0212), puis
     * publie le snapshot immuable : une déclaration externe devient
     * résolvable pour le survol à partir de la requête suivante. Une session
     * fermée l'ignore silencieusement ; une publication dont les racines
     * auraient changé entre-temps n'est pas servie (jamais faux).
     */
    override fun rafraichirSources() {
        val racines = verrou.read {
            if (fermee) return
            classpathPose?.racinesSources ?: return
        }
        scanneur.rafraichir()
        scanneur.attendrePassageCourant()
        val frais = scanneur.snapshotCourant()
        verrou.write {
            if (!fermee && classpathPose?.racinesSources == racines) {
                scanPublie = frais
            }
        }
    }

    // ------------------------------------------------------------------
    // Navigation (étage 2 vague 2e — fiches 031/025).
    // ------------------------------------------------------------------

    /**
     * Rend les CORRECTIFS DE TEXTE du document (fiche 016) : le moteur
     * re-prouve le document courant — les familles « accès non sûr » et
     * « conversion de littéral » dérivent leurs correctifs des MÊMES
     * prédicats que la passe typée (alignement par construction).
     */
    override fun correctifsTexte(uri: String): List<jo.codeide.lsp.kotlin.api.CorrectifTexteKotlin> {
        val document = verrou.read {
            if (fermee) return@read null
            documents[uri]
        } ?: return emptyList()
        val guichet = indexSiPret()
        val univers = construireUnivers(document, uri, guichet?.vue, guichet?.vueJvm) ?: return emptyList()
        return verrou.read {
            if (fermee) return@read emptyList()
            CorrectifsTexte.calculer(document, uri, univers)
        }
    }

    /**
     * Rend le correctif « IMPLÉMENTER LES MEMBRES MANQUANTS » de la
     * déclaration sous le point (fiche 020, chantier P1-C ADR 0229).
     */
    override fun membresManquants(uri: String, deplacement: Int): List<jo.codeide.lsp.kotlin.api.CorrectifTexteKotlin> {
        val document = verrou.read {
            if (fermee) return@read null
            documents[uri]
        } ?: return emptyList()
        val guichet = indexSiPret()
        val univers = construireUnivers(document, uri, guichet?.vue, guichet?.vueJvm) ?: return emptyList()
        return verrou.read {
            if (fermee) return@read emptyList()
            MembresManquants.calculer(document, univers, deplacement)
        }
    }

    /**
     * Rend les emplacements de DÉFINITION du nom sous le point (fiche 031 :
     * go-to-definition de l'usage commonMain vers l'`expect` KMP) — la
     * chaîne conservatrice du survol : document courant (site désigné puis
     * même nom), autres documents ouverts, scan des racines (URI de fichier
     * + plage du nom).
     */
    override fun definition(uri: String, deplacement: Int): List<jo.codeide.lsp.kotlin.api.EmplacementKotlin> {
        return verrou.read {
            if (fermee) return@read emptyList()
            val document = documents[uri] ?: return@read emptyList()
            val autres = documents.entries.filter { it.key != uri }.map { it.key to it.value }
            // Les types EXTERNES du modèle (vague 2f — fiche 047 : le java
            // du scan, le fichier de layout d'un binding synthétique) —
            // l'URI portée par la contribution, jamais re-parsée.
            val externes = contributionsAuConsult(uri).flatMap { contribution ->
                contribution.types.map { it.nomSimple to (it.uri ?: contribution.uri) }
            }
            NavigationDocuments.definition(document, uri, autres, scanPublie, deplacement, externes)
        }
    }

    /**
     * Rend les SYMBOLES du document (fiche 025 : le plan d'un fichier
     * Compose-like) : chaque déclaration nommée avec son genre neutre et
     * ses plages, PLATE en ordre du document.
     */
    override fun symboles(uri: String): List<jo.codeide.lsp.kotlin.api.SymboleDocumentKotlin> {
        return verrou.read {
            if (fermee) return@read emptyList()
            val document = documents[uri] ?: return@read emptyList()
            NavigationDocuments.symboles(document)
        }
    }

    /**
     * Rend les RÉFÉRENCES du nom sous le point (fiche 025 : références
     * d'une fonction projet) : le site de déclaration puis chaque référence
     * du même nom dans le document, en ordre du document.
     */
    override fun occurrences(uri: String, deplacement: Int): List<EmplacementKotlin> {
        return verrou.read {
            if (fermee) return@read emptyList()
            val document = documents[uri] ?: return@read emptyList()
            NavigationDocuments.occurrences(document, uri, deplacement)
        }
    }

    // ------------------------------------------------------------------
    // Occurrences prouvées (étage 4 vague 4a — fiche 048).
    // ------------------------------------------------------------------

    /**
     * Le MONTAGE des occurrences prouvées (étage 4 — fiche 048) : les
     * documents OUVERTS (arbres vifs, l'ordre d'ouverture), les fichiers
     * Kotlin du scan NON OUVERTS (re-parsés paresseusement à la demande),
     * les contributions au modèle (ouvertes vives PRIMANT leurs doublons
     * scannés) et le guichet de classpath. Assemblé HORS verrou : la
     * lecture des fichiers fermés et leur parse ne tiennent JAMAIS le
     * verrou de session (ADR 0212) — les instantanés sont immuables.
     */
    private fun montageOccurrences(): OccurrencesProuvees.Montage {
        val ouverts = verrou.read {
            if (fermee) return@read emptyList()
            documents.entries.map { it.key to it.value }
        }
        val contributions = contributionsAuConsult("")
        val guichet = indexSiPret()
        val urisScan = scanneur.snapshotAuConsult()?.contributions
            ?.map { it.uri }
            ?.filter { it.endsWith(".kt") || it.endsWith(".kts") }
            ?.distinct()
            .orEmpty()
        return OccurrencesProuvees.Montage(
            documents = ouverts.toMap(),
            ordreDocuments = ouverts.map { it.first },
            urisScan = urisScan,
            contributions = contributions,
            index = guichet?.vue,
            vueJvm = guichet?.vueJvm,
            etatScan = etatScanAuConsult(),
        )
    }

    /**
     * Rend les RÉFÉRENCES PROUVÉES du symbole sous le point (étage 4 —
     * fiche 048 : le contrat S2 multi-fichiers par la preuve de liaison —
     * import exact, même paquet sans homonyme, même fichier déclarant,
     * typage du récepteur ; repli mot-clé ADR 0218).
     */
    override fun references(
        uri: String,
        deplacement: Int,
        inclureDeclaration: Boolean,
    ): List<EmplacementKotlin> {
        val document = verrou.read {
            if (fermee) return@read null
            documents[uri]
        } ?: return emptyList()
        return OccurrencesProuvees.references(
            document = document,
            uri = uri,
            deplacement = deplacement,
            inclureDeclaration = inclureDeclaration,
            montage = montageOccurrences(),
        )
    }

    /**
     * Rend les SURLIGNAGES de document au point (étage 4 — fiche 048 :
     * documentHighlight — déclaration ECRITURE, usages LECTURE, ombrage
     * exclu, en ordre du document).
     */
    override fun surlignagesDocument(uri: String, deplacement: Int): List<SurlignageDocumentKotlin> {
        val document = verrou.read {
            if (fermee) return@read null
            documents[uri]
        } ?: return emptyList()
        return OccurrencesProuvees.surlignages(
            document = document,
            uri = uri,
            deplacement = deplacement,
            montage = montageOccurrences(),
        )
    }

    /**
     * Rend la PLAGE RENOMMABLE sous le point (étage 4 — fiche 048 :
     * prepareRename — placeholder exact, `null` hors identifiant, jamais
     * deviné).
     */
    override fun plageRenommable(uri: String, deplacement: Int): PlageRenommableKotlin? {
        val document = verrou.read {
            if (fermee) return@read null
            documents[uri]
        } ?: return null
        return OccurrencesProuvees.plageRenommable(
            document = document,
            deplacement = deplacement,
            montage = montageOccurrences(),
        )
    }

    /**
     * Rend les occurrences à ÉDITER du RENOMMAGE BORNÉ (étage 4 —
     * fiche 048 : déclaration + usages + segments d'import, multi-fichiers —
     * liste VIDE sans cible prouvée, l'hôte en fait une erreur typée).
     */
    override fun renommage(uri: String, deplacement: Int): List<EmplacementKotlin> {
        val document = verrou.read {
            if (fermee) return@read null
            documents[uri]
        } ?: return emptyList()
        return OccurrencesProuvees.renommage(
            document = document,
            uri = uri,
            deplacement = deplacement,
            montage = montageOccurrences(),
        )
    }

    /**
     * Les statistiques du DERNIER passage du scanneur (vague 2e — fiche
     * 036 : le fichier au-delà de la borne est COMPTÉ — total réel des
     * fichiers éligibles vus par la marche, `faits < total` signale la
     * troncation). INTERNE : le bilan d'inspection, pas un contrat d'hôte.
     */
    internal fun statsDernierPassageScan(): ScanProjetParesseux.StatsPassage =
        scanneur.statsDernierPassage()

    // ------------------------------------------------------------------
    // Fermeture (fiche 049 : tout abandonné, no-ops postérieurs).
    // ------------------------------------------------------------------

    /**
     * Abandonne TOUT : documents, classpath, vue et scan (le fil dédié du
     * scanneur est interrompu). Les appels postérieurs sont des no-ops
     * silencieux ; la fermeture est idempotente.
     */
    override fun close() {
        verrou.write {
            fermee = true
            documents.clear()
            classpathPose = null
            vueLocale = null
            scanPublie = null
        }
        scanneur.fermer()
    }

    // ------------------------------------------------------------------
    // Guichet d'index (fiche 042 — interne).
    // ------------------------------------------------------------------

    /**
     * Les contributions du modèle de symboles au CONSULT (étage 2) : la
     * passe de scan est REJOINTE si elle est en vol (fiche 041 — jamais de
     * scan sur le fil appelant), le document COURANT est exclu de la
     * matière (son arbre frais prime sur son doublon du scan).
     *
     * @param uriExclue l'URI du document consulté ;
     * @return les contributions du scan hors document ; liste vide sans
     * racines posées (projet vide — connaissance complète).
     */
    internal fun contributionsAuConsult(uriExclue: String): List<jo.codeide.lsp.kotlin.resolve.ContributionFichier> {
        // Les documents OUVERTS priment leurs doublons du scan (vague 2c —
        // fiche 044 : le frère OUVERT VIVANT, l'arbre frais JAMAIS le disque
        // périmé) : leurs contributions vives d'abord, le scan filtré de
        // leurs URI ensuite (le document COURANT est ajouté séparément par
        // la passe, uri `""`).
        val ouvertes = verrou.read {
            if (fermee) return@read emptyList()
            documents.entries
                .filter { it.key != uriExclue }
                .map { (uri, document) ->
                    jo.codeide.lsp.kotlin.resolve.ModeleProjet.contributionKotlin(document.arbre, uri)
                }
        }
        val scan = scanneur.snapshotAuConsult()?.contributionsHors(uriExclue).orEmpty()
        val urisOuvertes = ouvertes.map { it.uri }.toSet()
        val reelles = ouvertes + scan.filter { it.uri !in urisOuvertes }

        // La fusion des groupes SYNTHÉTIQUES AVEC COUPURE (vague 2f —
        // fiche 047 : R, BuildConfig, ViewBinding/DataBinding) : chaque
        // groupe entre au modèle de preuves TANT qu'aucune classe RÉELLE
        // du même FQCN (document ouvert ou scan) ne l'EFFACE — une classe
        // R réelle du scan efface le synthétique homonyme.
        val fqcnsReels = reelles.flatMap { contribution -> contribution.types.map { it.fqcn } }.toSet()
        val synthetiques = moteur?.groupesSynthetiques().orEmpty()
            .filter { it.fqcn !in fqcnsReels }
            .mapNotNull(::contributionSynthetique)
        return reelles + synthetiques
    }

    /**
     * La CONTRIBUTION au modèle d'un groupe synthétique (vague 2f) : la
     * classe de tête (`R`, `ActivityMainBinding`, `BuildConfig`) avec ses
     * membres, ses sous-objets (`R.layout`) en types IMBRIQUÉS pointés —
     * l'URI synthétique `synthetique://` n'entre jamais en collision avec
     * les documents ouverts (uri `""`) ni les fichiers scannés.
     */
    private fun contributionSynthetique(
        groupe: jo.codeide.lsp.kotlin.synthetics.ClassesSynthetiques.GroupeSynthetique,
    ): jo.codeide.lsp.kotlin.resolve.ContributionFichier? {
        val tete = groupe.declarations.firstOrNull {
            it.genre == jo.codeide.lsp.kotlin.synthetics.GenreDeclaration.CLASSE
        } ?: return null
        val paquet = groupe.fqcn.substringBeforeLast('.', "")
        val membresTete = mutableListOf<jo.codeide.lsp.kotlin.resolve.MembreProjet>()
        val imbriques = mutableListOf<jo.codeide.lsp.kotlin.resolve.TypeProjet>()
        for (declaration in groupe.declarations) {
            when (declaration.genre) {
                jo.codeide.lsp.kotlin.synthetics.GenreDeclaration.OBJET -> {
                    if (declaration.conteneur != groupe.fqcn) continue
                    membresTete.add(
                        jo.codeide.lsp.kotlin.resolve.MembreProjet(
                            declaration.nom,
                            jo.codeide.lsp.kotlin.resolve.GenreMembreProjet.TYPE_IMBRIQUE,
                            null,
                        ),
                    )
                    imbriques.add(
                        jo.codeide.lsp.kotlin.resolve.TypeProjet(
                            uri = declaration.uri.ifEmpty { null },
                            fqcn = declaration.conteneur + "." + declaration.nom,
                            nomSimple = declaration.nom,
                            genre = jo.codeide.lsp.kotlin.resolve.GenreTypeProjet.OBJET,
                            supertypesEcrits = emptyList(),
                            membres = groupe.declarations
                                .filter { it.conteneur == declaration.conteneur + "." + declaration.nom }
                                .map { membre ->
                                    jo.codeide.lsp.kotlin.resolve.MembreProjet(
                                        membre.nom,
                                        jo.codeide.lsp.kotlin.resolve.GenreMembreProjet.PROPRIETE,
                                        membre.typeRetour,
                                    )
                                },
                            estData = false,
                            entreesEnum = emptyList(),
                        ),
                    )
                }
                jo.codeide.lsp.kotlin.synthetics.GenreDeclaration.PROPRIETE -> {
                    if (declaration.conteneur != groupe.fqcn) continue
                    membresTete.add(
                        jo.codeide.lsp.kotlin.resolve.MembreProjet(
                            declaration.nom,
                            jo.codeide.lsp.kotlin.resolve.GenreMembreProjet.PROPRIETE,
                            declaration.typeRetour,
                        ),
                    )
                }
                jo.codeide.lsp.kotlin.synthetics.GenreDeclaration.FONCTION -> {
                    if (declaration.conteneur != groupe.fqcn) continue
                    membresTete.add(
                        jo.codeide.lsp.kotlin.resolve.MembreProjet(
                            declaration.nom,
                            jo.codeide.lsp.kotlin.resolve.GenreMembreProjet.FONCTION,
                            declaration.typeRetour,
                        ),
                    )
                }
                else -> Unit
            }
        }
        val typeTete = jo.codeide.lsp.kotlin.resolve.TypeProjet(
            uri = tete.uri.ifEmpty { null },
            fqcn = groupe.fqcn,
            nomSimple = tete.nom,
            genre = jo.codeide.lsp.kotlin.resolve.GenreTypeProjet.CLASSE,
            supertypesEcrits = emptyList(),
            membres = membresTete,
            estData = false,
            entreesEnum = emptyList(),
            // BuildConfig : la classe SEULE est synthétisée — ses champs
            // réels vivent dans la génération AGP, jamais lus (fiche 047 :
            // membres sous-reportés, l'absence d'un sélecteur ne se prouve
            // jamais, D3).
            membresSousRapportes = tete.nom == "BuildConfig",
        )
        return jo.codeide.lsp.kotlin.resolve.ContributionFichier(
            uri = "synthetique://" + groupe.fqcn,
            paquet = paquet,
            types = listOf(typeTete) + imbriques,
            appelables = emptyList(),
            extensions = emptyList(),
        )
    }

    /**
     * L'état du scan au CONSULT (étage 2 — l'univers des preuves, fiche
     * 006) : ABSENT sans racines posées, COMPLET si la dernière passe a
     * couvert tous les fichiers éligibles vus, PARTIEL sinon (annulée ou
     * tronquée — les preuves d'absence s'y taisent, fiche 036 à venir).
     */
    internal fun etatScanAuConsult(): EtatScan {
        val snapshot = scanneur.snapshotAuConsult() ?: return EtatScan.ABSENT
        return if (snapshot.complet) EtatScan.COMPLET else EtatScan.PARTIEL
    }

    /**
     * Le guichet d'index de la session (interne — fiche 042) : l'état de
     * couverture CONSULTÉ À CHAQUE REQUÊTE.
     *
     * - usage direct : la vue locale (nourrie ICI à la définition, si la
     *   couverture tient — une entrée écartée garde le guichet MUET) ;
     * - branchement : le snapshot PUBLIÉ par le moteur s'il couvre les
     *   entrées posées (garde de liste ordonnée) — `null` tant qu'aucune
     *   passe n'a abouti (sous-lecture honnête : les requêtes qui prouvent
     *   par l'absence restent muettes, aucun faux négatif) ;
     * - session fermée : `null`.
     *
     * Depuis la vague 1d le snapshot porte la VUE des appelables
     * (`vue.topParNom`, `extensionsSur`…) : les diagnostics `kt.unresolved`
     * de l'étage 2 s'y brancheront — le même guichet, sans nouvelle
     * couture.
     *
     * @return la vue d'index couvrant le classpath posé ; `null` si rien
     * n'est couvert.
     */
    internal fun indexSiPret(): SnapshotClasspath? {
        return verrou.read {
            if (fermee) return@read null
            val classpath = classpathPose ?: return@read null
            val locale = vueLocale
            if (locale != null) return@read locale
            return@read moteur?.indexSiPret(classpath.entrees)
        }
    }

    // ------------------------------------------------------------------
    // Aides.
    // ------------------------------------------------------------------

    /**
     * Analyse un texte de document (parse complet sous verrou d'écriture) —
     * le mode script se déduit du suffixe `.kts` de l'URI.
     *
     * @param uri l'identifiant du document ;
     * @param texte le contenu complet à analyser ;
     * @return le document ouvert prêt à servir les requêtes.
     */
    private fun analyser(uri: String, texte: CharSequence): DocumentOuvert {
        val script = uri.endsWith(".kts")
        return DocumentOuvert(texte = texte.toString(), arbre = parseur.arbre(texte, script))
    }
}

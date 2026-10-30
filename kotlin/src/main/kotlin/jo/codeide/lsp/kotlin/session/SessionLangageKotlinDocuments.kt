// CodeIDE — session de langage Kotlin « documents » (module :kotlin, étage 1
// vague 1b — session documents ; vague 1c — scan paresseux fiche 041). Code
// NEUF jjoblab (clean-room — contrat : fiches 049/042/041 du
// cahier docs/cahier-specifications-kotlin/ ; concurrence ADR 0117/ADR 0212
// ancien : verrou lecture-écriture, écritures = mutations de documents et
// définitions, lectures parallèles = requêtes, JAMAIS de scan sous verrou).
package jo.codeide.lsp.kotlin.session

import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.DiagnosticKotlin
import jo.codeide.lsp.kotlin.api.PlagePliableKotlin
import jo.codeide.lsp.kotlin.api.SessionLangageKotlin
import jo.codeide.lsp.kotlin.api.SurvolKotlin
import jo.codeide.lsp.kotlin.index.IndexAppelablesKotlin
import jo.codeide.lsp.kotlin.parse.ParseurKotlin
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
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
) : SessionLangageKotlin {

    /** Le verrou de la session (écritures mutent, lectures parallèles). */
    private val verrou = ReentrantReadWriteLock()

    /** Le parseur de la session (sans état — sert chaque mutation). */
    private val parseur = ParseurKotlin()

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
    private val scanneur = ScanProjetParesseux()

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
     * Rend les diagnostics courants du document : syntaxe, imports
     * inutilisés, imports conflictuels (fiche 049). Document inconnu ou
     * fermé : liste vide, jamais d'exception.
     */
    override fun diagnostics(uri: String): List<DiagnosticKotlin> {
        return verrou.read {
            if (fermee) return@read emptyList()
            val document = documents[uri] ?: return@read emptyList()
            DiagnosticsImports.calculer(document)
        }
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
     * courant, autres documents ouverts, scan des racines (fiche 049).
     */
    override fun survol(uri: String, deplacement: Int): SurvolKotlin? {
        return verrou.read {
            if (fermee) return@read null
            val document = documents[uri] ?: return@read null
            val autres = documents.entries
                .filter { it.key != uri }
                .map { it.value }
            SurvolDocuments.survol(document, autres, scanPublie, deplacement)
        }
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

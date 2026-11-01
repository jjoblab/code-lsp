package jo.codeide.lsp.java.protocole

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import jo.codeide.lsp.java.MoteurJava
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient

/**
 * Le publicateur asynchrone des diagnostics (étage N8 — cahier fiches
 * 056 et 059) : le pont entre le moteur ([MoteurJava.diagnose]) et la
 * notification `textDocument/publishDiagnostics` du client.
 *
 * **Contrats verrouillés par le cahier** :
 *  - publication ASYNCHRONE (fiche 056 : « les diagnostics voyagent en
 *    publication asynchrone (debounce + scheduler) — la réécriture doit
 *    publier sans réponse bloquante côté client ») : aucun fil JSON-RPC
 *    ne paie le compilateur ;
 *  - DEBOUNCE de 50 ms par document (fiche 059 : « debounce de 50 ms,
 *    drain attendu 400 ms ») : une salve de didChange se fond en UNE
 *    publication — la dernière fait foi ;
 *  - la publication porte l'URI et la version COURANTE du buffer
 *    (fiche 059 I5 : « la publication didSave transportant la version
 *    est elle-même verrouillée ») — le contenu est calculé à l'INSTANT
 *    DU TIR sur l'instantané VIVANT, jamais sur un instantané capturé à
 *    la demande (un didChange pendant la fenêtre de debounce publie le
 *    texte NOUVEAU) ;
 *  - un document FERMÉ pendant la fenêtre publie la liste VIDE (la
 *    convention LSP d'effacement des squiggles) ;
 *  - une demande sur un document JAMAIS ouvert ne planifie RIEN
 *    (fiche 059 : « didSave sur document jamais ouvert ne republie
 *    pas — republier sur un buffer vide serait faux ») ;
 *  - la publication arrive TOUJOURS sous les bornes généreuses du cahier
 *    (15 s — fiche 056 ; 3 s — fiche 059) : 50 ms de debounce +
 *    compilation asynchrone les tient par construction.
 *
 * **PLAGE PRIORITAIRE (vague V-F4 — ADR 0029 phase 4, « zone visible
 * d'abord »)** : quand le client a déclaré une zone visible
 * (`$/codeide/plageVisible`) ET que la passe à venir serait COMPLÈTE
 * (sonde [MoteurJava.serviraitPasseComplete] — ouverture de grand
 * fichier, édition structurelle) ET que le moteur accepte la priorité
 * ([MoteurJava.diagnosePrioritaire]), le tir publie DEUX fois : la passe
 * PRIORITAIRE d'abord (le corps visible + tout le hors-corps — partielle
 * annoncée), la passe COMPLÈTE ensuite (elle fait foi). Le fil unique
 * conserve l'ordre ; le client rend la dernière reçue. Un client sans
 * notification de plage garde le comportement d'avant V-F4, À L'IDENTIQUE
 * (les contrats du cahier — dont « une salve = une publication » — ne
 * sont touchés que dans ce scénario opt-in). Kill-switch :
 * `jo.codeide.lsp.java.plageprioritaire.disabled=true` coupe la double
 * publication (repli documenté).
 *
 * **Séquentialité** : un exécuteur À FIL UNIQUE sert toutes les
 * publications — l'ordre d'arrivée au client est l'ordre de demande, la
 * version la plus fraîche gagne toujours (le client rend les publications
 * par URI : la dernière efface la précédente).
 *
 * **Régimes du moteur** (INTEGRATION-CODEIDE §3.3, option B) : le
 * publicateur consomme un FOURNISSEUR `(chemin) -> MoteurJava?` — la
 * forme FIGÉE (constructeur de compatibilité) sert un moteur unique
 * capturé à initialize ; la forme VIVANTE résout le moteur AU TIR,
 * dans la fenêtre du debounce, par le ROUTAGE fichier → module de
 * l'environnement (fiche 047 : le routeur est relu à chaque requête,
 * une reconfiguration à chaud route le document changé vers SON
 * nouveau moteur sans reconstruction du publicateur). Un fournisseur
 * qui rend null NE PUBLIE PAS : l'absence de publication est le
 * comportement exact de l'absence de publicateur (jamais une liste
 * vide mensongère, qui effacerait des squiggles sans diagnostic).
 *
 * @param client le client LSP (bout publishDiagnostics).
 * @param etats le magasin des documents ouverts (instantanés vivants).
 * @param fournisseur le résolveur du moteur par chemin de document.
 * @param plagesVisibles le registre des zones visibles (V-F4 — défaut :
 *   un registre vide, la double publication n'engage jamais).
 */
public class PublicateurDiagnostics(
    private val client: LanguageClient,
    private val etats: EtatsDocuments,
    private val fournisseur: (chemin: String) -> MoteurJava?,
    private val plagesVisibles: PlagesVisibles = PlagesVisibles(),
) {

    /**
     * La forme FIGÉE (compatibilité des tests du cahier) : le moteur
     * unique construit à initialize sert toutes les publications.
     *
     * @param client le client LSP (bout publishDiagnostics).
     * @param moteur le moteur hôte (diagnose complet, replis internes).
     * @param etats le magasin des documents ouverts (instantanés vivants).
     */
    public constructor(
        client: LanguageClient,
        moteur: MoteurJava,
        etats: EtatsDocuments,
    ) : this(client, etats, { _ -> moteur })

    /**
     * La forme FIGÉE avec registre de plages visibles (V-F4 — régime figé
     * du serveur : le publicateur partage le registre des notifications).
     *
     * @param client le client LSP (bout publishDiagnostics).
     * @param moteur le moteur hôte (diagnose complet, replis internes).
     * @param etats le magasin des documents ouverts (instantanés vivants).
     * @param plagesVisibles le registre des zones visibles (V-F4).
     */
    public constructor(
        client: LanguageClient,
        moteur: MoteurJava,
        etats: EtatsDocuments,
        plagesVisibles: PlagesVisibles,
    ) : this(client, etats, { _ -> moteur }, plagesVisibles)

    /** Le fil unique des publications (ordre + jamais de fil RPC bloqué). */
    private val ordonnanceur = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "lspjava-publications").apply { isDaemon = true }
    }

    /** Les tirs en attente, par URI (le debounce annule et remplace). */
    private val enAttente = ConcurrentHashMap<String, ScheduledFuture<*>>()

    /** Vrai tant que le serveur n'a pas été arrêté (shutdown/exit). */
    @Volatile
    private var ouvert: Boolean = true

    /**
     * Demande une publication pour un document (didOpen, didChange,
     * didSave — les trois déclenchent, fiche 059 I5) : annule le tir en
     * attente éventuel et plananche à +50 ms (debounce). Le document doit
     * être OUVERT — la demande sur un buffer absent ne planifie rien.
     *
     * Complément de catalogue (ADR 0032) : un document **XML** ne publie
     * JAMAIS de diagnostics Java — l'intention documentée de V-B
     * (didChange XML : « aucun diagnostic Java publié ») devient une
     * garde POSÉE ici (le magasin ne filtre pas l'extension — sans cette
     * garde, un XML ouvert serait compilé comme Java et publierait du
     * bruit de parse).
     *
     * @param uri l'URI du document.
     */
    public fun demander(uri: String) {
        if (!ouvert) return
        if (uri.endsWith(".xml")) return // jamais de diagnostics Java pour un XML (ADR 0032)
        if (etats.instantane(uri) == null) return
        enAttente.remove(uri)?.cancel(false)
        enAttente[uri] = ordonnanceur.schedule({ tirer(uri) }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Le tir : lit l'instantané VIVANT à cet instant (la version et le
     * texte font foi — fiche 059 I5), résout le moteur par le ROUTAGE du
     * jour (fiche 047 — relu à CHAQUE tir, une reconfiguration à chaud
     * route sans reconstruction), compile, convertit, publie. Document
     * fermé entre-temps → liste VIDE (effacement des squiggles) ; moteur
     * introuvable → AUCUNE publication (l'absence de publicateur, jamais
     * une liste vide mensongère).
     *
     * V-F4 : si une zone visible est déclarée, que la passe serait
     * COMPLÈTE et que le moteur accepte la priorité, la passe PRIORITAIRE
     * (partielle, annoncée) part D'ABORD — la passe complète suit dans le
     * MÊME tir (fil unique : l'ordre d'arrivée au client est garanti).
     *
     * @param uri l'URI du document à publier.
     */
    private fun tirer(uri: String) {
        enAttente.remove(uri)
        val instantane = etats.instantane(uri) ?: run {
            client.publishDiagnostics(PublishDiagnosticsParams(uri, emptyList()))
            return
        }
        val chemin = ConvertisseurLsp.cheminDeUri(uri) ?: uri
        val moteur = fournisseur(chemin) ?: return
        // V-F4 — la PLAGE PRIORITAIRE : visible d'abord, complète ensuite.
        if (!PLAGE_PRIORITAIRE_DESACTIVEE) {
            val plage = plagesVisibles.plageDe(uri)
            if (plage != null && moteur.serviraitPasseComplete(chemin, instantane.texte)) {
                val prioritaire = moteur.diagnosePrioritaire(
                    chemin,
                    instantane.texte,
                    (plage.debut + plage.fin) / 2,
                )
                if (prioritaire != null) {
                    publier(uri, instantane, prioritaire)
                }
            }
        }
        publier(uri, instantane, moteur.diagnose(chemin, instantane.texte))
    }

    /** Publie une liste de diagnostics pour l'instantané donné (version fait foi). */
    private fun publier(uri: String, instantane: EtatsDocuments.Instantane, diagnostics: List<jo.codeide.lsp.java.diagnostics.Diagnostic>) {
        val repere = ConvertisseurLsp.repere(instantane.texte)
        val params = PublishDiagnosticsParams(
            uri,
            ConvertisseurLsp.diagnostics(diagnostics, repere, uri),
        )
        params.version = instantane.version.toInt()
        client.publishDiagnostics(params)
    }

    /**
     * Arrête le publicateur (fin de vie du serveur) : plus AUCUNE
     * publication ne part — les tirs en attente sont abandonnés. Les
     * notifications post-shutdown sont ignorées de toute façon (garde du
     * serveur) ; cette fermeture garantit qu'aucune publication en cours
     * de compilation ne survit à l'exit.
     */
    public fun fermer() {
        ouvert = false
        enAttente.values.forEach { it.cancel(false) }
        enAttente.clear()
        ordonnanceur.shutdownNow()
    }

    private companion object {
        /** Le debounce du cahier (fiche 059 : « debounce de 50 ms »). */
        private const val DEBOUNCE_MS: Long = 50

        /**
         * Kill-switch de la plage prioritaire (V-F4 — repli documenté) :
         * `jo.codeide.lsp.java.plageprioritaire.disabled=true` coupe la
         * double publication (comportement d'avant V-F4, à l'identique).
         */
        private val PLAGE_PRIORITAIRE_DESACTIVEE: Boolean =
            System.getProperty("jo.codeide.lsp.java.plageprioritaire.disabled") == "true"
    }
}

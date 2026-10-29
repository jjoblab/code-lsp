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
 * **Séquentialité** : un exécuteur À FIL UNIQUE sert toutes les
 * publications — l'ordre d'arrivée au client est l'ordre de demande, la
 * version la plus fraîche gagne toujours (le client rend les publications
 * par URI : la dernière efface la précédente).
 *
 * @param client le client LSP (bout publishDiagnostics).
 * @param moteur le moteur hôte (diagnose complet, replis internes).
 * @param etats le magasin des documents ouverts (instantanés vivants).
 */
public class PublicateurDiagnostics(
    private val client: LanguageClient,
    private val moteur: MoteurJava,
    private val etats: EtatsDocuments,
) {

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
     * @param uri l'URI du document.
     */
    public fun demander(uri: String) {
        if (!ouvert) return
        if (etats.instantane(uri) == null) return
        enAttente.remove(uri)?.cancel(false)
        enAttente[uri] = ordonnanceur.schedule({ tirer(uri) }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Le tir : lit l'instantané VIVANT à cet instant (la version et le
     * texte font foi — fiche 059 I5), compile, convertit, publie. Document
     * fermé entre-temps → liste VIDE (effacement des squiggles).
     *
     * @param uri l'URI du document à publier.
     */
    private fun tirer(uri: String) {
        enAttente.remove(uri)
        val instantane = etats.instantane(uri)
        val params = if (instantane == null) {
            PublishDiagnosticsParams(uri, emptyList())
        } else {
            val chemin = ConvertisseurLsp.cheminDeUri(uri) ?: uri
            val diagnostics = moteur.diagnose(chemin, instantane.texte)
            val repere = ConvertisseurLsp.repere(instantane.texte)
            PublishDiagnosticsParams(
                uri,
                ConvertisseurLsp.diagnostics(diagnostics, repere),
            )
        }
        params.version = instantane?.version?.toInt()
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
    }
}

// CodeIDE — publicateur asynchrone des diagnostics du serveur Kotlin
// (module :kotlin, étage 6 vague 6e — miroir :java N8). Code NEUF jjoblab :
// le debounce par URI, la publication sur fil dédié, la version COURANTE
// du buffer (jamais le fil RPC bloqué).
package jo.codeide.lsp.kotlin.protocole

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import jo.codeide.lsp.kotlin.api.SessionLangageKotlin
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient

/**
 * Le publicateur asynchrone des diagnostics : les trois notifications de
 * synchronisation (`didOpen`, `didChange`, `didSave`) déclenchent une
 * publication DÉBOUNCÉE (50 ms par URI — les frappes rapprochées ne
 * publient qu'une fois) sur un fil DÉDIÉ (le fil JSON-RPC n'est jamais
 * bloqué par un calcul de diagnostics).
 *
 * Le tir lit l'instantané VIVANT à cet instant (la version et le texte
 * font foi) et publie la version COURANTE du buffer. Un document fermé
 * entre-temps publie une liste VIDE (l'effacement des squiggles côté
 * client — la spec 3.17 demande la publication vide à la fermeture).
 *
 * **Publication sous 15 s par construction** (borne S1 du plan Java
 * transposée) : le debounce est de 50 ms et le calcul part IMMÉDIATEMENT
 * après — la seule latence restante est celle du moteur (les cibles du
 * cahier : premier diagnostic < 250 ms après parse).
 */
public class PublicateurDiagnostics(
    private val client: LanguageClient,
    private val session: SessionLangageKotlin,
    private val etats: EtatsDocuments,
) {

    /** Le fil unique des publications (ordre + jamais de fil RPC bloqué). */
    private val ordonnanceur = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "lspkotlin-publications").apply { isDaemon = true }
    }

    /** Les tirs en attente, par URI (le debounce annule et remplace). */
    private val enAttente = ConcurrentHashMap<String, ScheduledFuture<*>>()

    /** Vrai tant que le serveur n'a pas été arrêté (shutdown/exit). */
    @Volatile
    private var ouvert: Boolean = true

    /**
     * Demande une publication pour un document (didOpen, didChange,
     * didSave — les trois déclenchent) : annule le tir en attente
     * éventuel et planifie à +50 ms (debounce). Le document doit être
     * OUVERT — la demande sur un buffer absent ne planifie rien.
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
     * texte font foi), calcule les diagnostics de la SESSION (le chemin
     * de production D6), convertit, publie. Document fermé entre-temps →
     * liste VIDE (effacement des squiggles).
     *
     * @param uri l'URI du document à publier.
     */
    private fun tirer(uri: String) {
        enAttente.remove(uri)
        if (!ouvert) return
        val instantane = etats.instantane(uri)
        val version = instantane?.version ?: 0L
        val diagnostics: List<Diagnostic> = if (instantane == null) {
            emptyList()
        } else {
            try {
                val brutes = session.diagnostics(uri)
                val repere = ConvertisseurLsp.repere(instantane.texte)
                brutes.map { ConvertisseurLsp.diagnostic(it, uri, repere) }
            } catch (_: Exception) {
                // La session ne lève JAMAIS (D3) — la garde reste pour
                // l'honnêteté du fil de publication (un tir ne tue pas le
                // publicateur).
                emptyList()
            }
        }
        client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics, version.toInt()))
    }

    /**
     * Ferme le publicateur (à `exit`) : plus AUCUN tir ne part, les tirs
     * en attente sont annulés, le fil dédié s'éteint.
     */
    public fun fermer() {
        ouvert = false
        enAttente.values.forEach { it.cancel(false) }
        enAttente.clear()
        ordonnanceur.shutdownNow()
    }

    private companion object {
        /** Le debounce par URI (ms) — les frappes rapprochées publient une fois. */
        private const val DEBOUNCE_MS: Long = 50
    }
}

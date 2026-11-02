package jo.codeide.lsp.kotlin.protocole

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer

/**
 * La boucle locale JSON-RPC (harnais des cas dorés protocole de l'étage 6)
 * : un serveur réel et un client réel reliés par deux tuyaux en mémoire —
 * les ALLER-RETOURS LSP4J AUTHENTIQUES (les requêtes et publications
 * traversent la sérialisation Gson, les en-têtes Content-Length, les fils
 * d'écoute — exactement le chemin de production, sans boucle
 * d'orchestration humaine).
 *
 * Le montage (miroir du harnais Java N8) : « client espion (enregistre
 * les publishDiagnostics) » — un client réel dont la SEULE observation est
 * l'enregistrement des publications, le reste silencieux.
 *
 * @param serveur le serveur à éprouver.
 */
public class BoucleLocale(private val serveur: ServeurLangageKotlin) {

    /** Le client espion : enregistre TOUTES les publications reçues. */
    public val espion: ClientEspion = ClientEspion()

    /** Le proxy du serveur distant (l'interface standard LanguageServer). */
    public lateinit var client: LanguageServer

    /**
     * Démarre la boucle : crée les deux lanceurs (serveur, client), connecte
     * l'espion au serveur, démarre l'écoute.
     */
    public fun demarrer() {
        val serveurVersClient = PipedOutputStream()
        val clientVersServeur = PipedOutputStream()
        val entreeClient = PipedInputStream(serveurVersClient)
        val entreeServeur = PipedInputStream(clientVersServeur)

        val lanceurServeur = Launcher.Builder<LanguageClient>()
            .setLocalService(serveur)
            .setRemoteInterface(LanguageClient::class.java)
            .setInput(entreeServeur)
            .setOutput(serveurVersClient)
            .create()
        serveur.connect(lanceurServeur.remoteProxy)
        lanceurServeur.startListening()

        val lanceurClient = Launcher.Builder<LanguageServer>()
            .setLocalService(espion)
            .setRemoteInterface(LanguageServer::class.java)
            .setInput(entreeClient)
            .setOutput(clientVersServeur)
            .create()
        lanceurClient.startListening()
        client = lanceurClient.remoteProxy
    }

    /**
     * Attends qu'une publication de diagnostics arrive pour une URI
     * (polling de 100 ms — la convention du cahier Java, borne S1 de 15 s).
     *
     * @param uri l'URI attendue (null = n'importe laquelle).
     * @param delaiMs le délai maximal (défaut 15 000 — la borne S1).
     * @return la dernière publication reçue, ou null au délai.
     */
    public fun attendrePublication(uri: String? = null, delaiMs: Long = 15_000): PublishDiagnosticsParams? {
        val fin = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delaiMs)
        while (System.nanoTime() < fin) {
            val trouvee = espion.publications.lastOrNull { uri == null || it.uri == uri }
            if (trouvee != null) return trouvee
            Thread.sleep(100)
        }
        return espion.publications.lastOrNull { uri == null || it.uri == uri }
    }

    /**
     * Attends que le nombre CUMULÉ de publications atteigne un seuil.
     *
     * @param seuil le nombre de publications attendu.
     * @param delaiMs le délai maximal.
     * @return vrai ssi le seuil est atteint dans le délai.
     */
    public fun attendreCumul(seuil: Int, delaiMs: Long = 3_000): Boolean {
        val fin = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delaiMs)
        while (System.nanoTime() < fin) {
            if (espion.publications.size >= seuil) return true
            Thread.sleep(50)
        }
        return espion.publications.size >= seuil
    }
}

/**
 * Le client espion : un client réel dont la SEULE observation est
 * l'enregistrement des publications — le reste est silencieux (le cahier
 * n'attend aucune interaction client au-delà des publications).
 */
public class ClientEspion : LanguageClient {

    /** Les publications reçues, dans l'ordre d'arrivée (file thread-safe). */
    public val publications: MutableList<PublishDiagnosticsParams> =
        java.util.concurrent.CopyOnWriteArrayList()

    override fun telemetryEvent(object_: Any?) = Unit

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        diagnostics?.let { publications.add(it) }
    }

    override fun showMessage(messageParams: MessageParams?) = Unit

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
        @Suppress("UNCHECKED_CAST")
        return CompletableFuture.completedFuture(null) as CompletableFuture<MessageActionItem>
    }

    override fun logMessage(message: MessageParams?) = Unit

    override fun logTrace(params: LogTraceParams?) = Unit
}

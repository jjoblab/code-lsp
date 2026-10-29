package jo.codeide.lsp.java.protocole

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import java.util.concurrent.CompletableFuture

/**
 * La boucle locale JSON-RPC (harnais des fiches 054/056/058/059) : un
 * serveur réel et un client réel reliés par deux tuyaux en mémoire —
 * les ALLER-RETOURS LSP4J AUTHENTIQUES (les requêtes et publications
 * traversent la sérialisation Gson, les en-têtes Content-Length, les
 * fils d'écoute — exactement le chemin de production, sans boucle
 * d'orchestration humaine).
 *
 * Le montage du cahier : « serveur réel sur un dossier @TempDir » (fiche
 * 054), « client espion (enregistre les publishDiagnostics) » (fiche
 * 056), « c'est cette exécution via le cycle de vie réel du serveur qui
 * motive le domaine » (fiche 058).
 *
 * Le ROBINET d'octets bruts ([brutServeurVersClient]) copie le fil
 * serveur→client : les tests de la capability maison
 * (`x-codeide.completionRanking`) y vérifient la PRÉSENCE SUR LE FIL
 * (le JSON désérialisé perd les champs inconnus par conception — spec
 * LSP — la preuve de l'annonce se fait sur les octets).
 *
 * @param serveur le serveur à éprouver.
 */
public class BoucleLocale(private val serveur: ServeurLangageJava) {

    /** Le client espion : enregistre TOUTES les publications reçues. */
    public val espion: ClientEspion = ClientEspion()

    /** Les octets bruts du fil serveur→client (la capability maison s'y lit). */
    public val brutServeurVersClient: ByteArrayOutputStream = ByteArrayOutputStream()

    /** Le proxy client du serveur distant. */
    public lateinit var client: LanguageServer

    /** Le proxy du service expérimental distant (superDefinition…). */
    public lateinit var experimental: ServiceExperimental

    /** Le robinet : copie les octets vers le tampon brut de CETTE boucle. */
    private class Robinet(
        private val cible: OutputStream,
        private val brut: ByteArrayOutputStream,
    ) : OutputStream() {
        override fun write(b: Int) {
            brut.write(b)
            cible.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            brut.write(b, off, len)
            cible.write(b, off, len)
        }

        override fun flush() = cible.flush()
    }

    /**
     * Démarre la boucle : crée les deux lanceurs (serveur avec le
     * sérialiseur des capabilities étendues, client standard), connecte
     * l'espion au serveur, démarre l'écoute.
     */
    public fun demarrer() {
        val serveurVersClient = PipedOutputStream()
        val clientVersServeur = PipedOutputStream()
        val robinet = Robinet(serveurVersClient, brutServeurVersClient)
        val entreeClient = PipedInputStream(serveurVersClient)
        val entreeServeur = PipedInputStream(clientVersServeur)

        val lanceurServeur = Launcher.Builder<LanguageClient>()
            .setLocalService(serveur)
            .setRemoteInterface(LanguageClient::class.java)
            .setInput(entreeServeur)
            .setOutput(robinet)
            .configureGson { gson ->
                gson.registerTypeAdapter(
                    org.eclipse.lsp4j.ServerCapabilities::class.java,
                    CapacitesServeur.serialisateurCapacites(),
                )
            }
            .create()
        serveur.connect(lanceurServeur.remoteProxy)
        lanceurServeur.startListening()

        // L'interface DISTANTE combinée : les méthodes expérimentales
        // (superDefinition, decompiledSource) ne sont désérialisables
        // que si le lanceur client les CONNAÎT — un client standard
        // (LanguageServer seul) les ignore, le harnais les éprouve.
        val lanceurClient = Launcher.Builder<LangageServeurDistant>()
            .setLocalService(espion)
            .setRemoteInterface(LangageServeurDistant::class.java)
            .setInput(entreeClient)
            .setOutput(clientVersServeur)
            .create()
        lanceurClient.startListening()
        val distant: LangageServeurDistant = lanceurClient.remoteProxy
        client = distant
        experimental = distant
    }

    /**
     * Attends qu'une publication de diagnostics arrive pour une URI
     * (polling de 100 ms — la convention du cahier, fiche 056).
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

    /**
     * Le fil brut capté (la chaîne des octets serveur→client).
     *
     * @return le contenu capté, en chaîne.
     */
    public fun filBrut(): String = brutServeurVersClient.toString()
}

/**
 * Le client espion (fiche 056 : « client espion (enregistre les
 * publishDiagnostics) ») : un client réel dont la SEULE observation est
 * l'enregistrement des publications — le reste est silencieux (le cahier
 * n'attend aucune interaction client au-delà des publications).
 */
public class ClientEspion : LanguageClient {

    /** Les publications reçues, dans l'ordre d'arrivée (file thread-safe). */
    public val publications: MutableList<PublishDiagnosticsParams> =
        java.util.concurrent.CopyOnWriteArrayList()

    override fun telemetryEvent(object_: Any?): Unit = Unit

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        diagnostics?.let { publications.add(it) }
    }

    override fun showMessage(messageParams: MessageParams?): Unit = Unit

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
        @Suppress("UNCHECKED_CAST")
        return CompletableFuture.completedFuture(null) as CompletableFuture<MessageActionItem>
    }

    override fun logMessage(message: MessageParams?): Unit = Unit

    override fun logTrace(params: LogTraceParams?): Unit = Unit
}

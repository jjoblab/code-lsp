package jo.codeide.lsp.java.protocole

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.PositionEncodingKind
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * Fiche 055 — JavaLanguageServerLifecycleTest : la machine à états du
 * cycle de vie LSP (UNINITIALIZED → INITIALIZING → INITIALIZED →
 * SHUTDOWN → EXITED) et le contenu de la réponse `initialize`
 * (capabilities, serverInfo).
 *
 * Le harnais du domaine construisait le serveur IN-PROCESS sans
 * environnement de compilation — « protocole pur, aucune dépendance à
 * android.jar ou au JDK du projet ». La réécriture garde EXACTEMENT ce
 * montage : appels DIRECTS sur [ServeurLangageJava], aucune boucle
 * JSON-RPC — la fabrique du moteur rend null (racine fictive
 * `file:///test`).
 *
 * Corpus minimal du cahier : paramètres d'initialize avec rootUri
 * `file:///test` et processId 12345.
 */
class Fiche055CycleVieServeurTest {

    /** Les paramètres du cahier : rootUri file:///test, processId 12345. */
    private fun parametres(): InitializeParams = InitializeParams().apply {
        processId = 12345
        rootUri = "file:///test"
    }

    /** initialStateIsUninitialized — état initial. */
    @Test
    fun initialStateIsUninitialized() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        assertEquals(CycleVie.Etat.UNINITIALIZED, serveur.etat(), "l'état exposé est UNINITIALIZED")
    }

    /** initializeTransitionsToInitializing — initialize répond et passe en INITIALIZING. */
    @Test
    fun initializeTransitionsToInitializing() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val resultat = serveur.initialize(parametres()).get()
        assertNotNull(resultat, "InitializeResult non-null")
        assertNotNull(resultat.capabilities, "capabilities non-null")
        assertNotNull(resultat.serverInfo, "serverInfo non-null")
        assertEquals("lspjava", resultat.serverInfo.name, "serverInfo.name exact « lspjava »")
        assertNotNull(resultat.serverInfo.version, "version non-null")
        assertEquals(
            CycleVie.Etat.INITIALIZING,
            serveur.etat(),
            "après la requête, l'état est INITIALIZING (et non INITIALIZED — la transition finale attend initialized)",
        )
    }

    /** initializedTransitionsToInitialized — initialized finalise l'initialisation. */
    @Test
    fun initializedTransitionsToInitialized() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        serveur.initialize(parametres()).get()
        serveur.initialized(InitializedParams())
        assertEquals(CycleVie.Etat.INITIALIZED, serveur.etat(), "initialized finalise l'initialisation")
    }

    /** shutdownTransitionsToShutdown — shutdown. */
    @Test
    fun shutdownTransitionsToShutdown() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        serveur.initialize(parametres()).get()
        serveur.initialized(InitializedParams())
        serveur.shutdown().get()
        assertEquals(CycleVie.Etat.SHUTDOWN, serveur.etat(), "shutdown passe à SHUTDOWN")
    }

    /** exitTransitionsToExited — exit. */
    @Test
    fun exitTransitionsToExited() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        serveur.initialize(parametres()).get()
        serveur.initialized(InitializedParams())
        serveur.shutdown().get()
        serveur.exit()
        assertEquals(CycleVie.Etat.EXITED, serveur.etat(), "exit passe à EXITED (après shutdown)")
    }

    /** standaloneModeDoesNotExitProcess — mode in-process : exit ne tue pas le processus. */
    @Test
    fun standaloneModeDoesNotExitProcess() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        serveur.setStandaloneMode(false)
        serveur.initialize(parametres()).get()
        serveur.initialized(InitializedParams())
        serveur.shutdown().get()
        // En mode in-process, exit ne JAMAIS appeler System.exit — le
        // test observe simplement que l'état est atteint SANS crash de la
        // JVM hôte (si exit tuait le processus, ce test ne passerait pas).
        serveur.exit()
        assertEquals(CycleVie.Etat.EXITED, serveur.etat(), "l'état atteint EXITED sans arrêter la JVM hôte")
    }

    /** capabilitiesIncludeCodeActionKinds — codeActionProvider annoncé avec quickfix. */
    @Test
    fun capabilitiesIncludeCodeActionKinds() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val resultat = serveur.initialize(parametres()).get()
        val fournisseur = resultat.capabilities.codeActionProvider
        assertNotNull(fournisseur, "codeActionProvider annoncé")
        val detail = fournisseur.right
        if (detail is CodeActionOptions) {
            assertNotNull(detail.codeActionKinds, "codeActionKinds non-null en forme détaillée")
            assertTrue(detail.codeActionKinds.isNotEmpty(), "codeActionKinds non vide")
            assertTrue(
                detail.codeActionKinds.contains("quickfix"),
                "codeActionKinds contient « quickfix » — trouvé : ${detail.codeActionKinds}",
            )
        } else {
            assertTrue(fournisseur.left == true, "à défaut de la forme détaillée, la forme booléenne TRUE")
        }
    }

    /** capabilitiesIncludePositionEncoding — positionEncoding UTF-16. */
    @Test
    fun capabilitiesIncludePositionEncoding() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val resultat = serveur.initialize(parametres()).get()
        assertEquals(
            PositionEncodingKind.UTF16,
            resultat.capabilities.positionEncoding,
            "positionEncoding = UTF16 (valeur exacte)",
        )
    }
}

// CodeIDE — capabilities du serveur Kotlin (module :kotlin, étage 6 vague
// 6e — ADR 0118). Code NEUF jjoblab : la réponse `initialize` porte la
// MATRICE des capacités de la colonne Kotlin (docs/LSP_MATRICE_CAPACITES.md
// de codeide-lsp — l'inventaire de parité à reproduire).
package jo.codeide.lsp.kotlin.protocole

import java.util.Properties
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.PositionEncodingKind
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.TextDocumentSyncKind

/**
 * Les capabilities du serveur + la réponse `initialize` — la règle matrice
 * (ADR 0015 Java transposée par ADR 0118) : **« capacité testée =
 * annoncée ; sinon absente »**. Chaque capability ci-dessous correspond à
 * une capacité SERVIE par le SPI du moteur et VÉROUILLÉE par les tests du
 * cahier — aucune capability fantôme.
 *
 * ## La colonne Kotlin de la matrice (l'inventaire de parité)
 *
 * **Annoncées et servies** : synchronisation PLEIN TEXTE, survol (fiche
 * classpath comprise — vague 6c), complétion (contextuelle — étage 3),
 * aide à la signature (vague 6d), définition, définition de type,
 * implémentations, références, surlignage d'occurrences, symboles de
 * document, symboles d'espace (vague 6b), plage de sélection (vague 6b),
 * pliage, formatage borné, formatage de plage, actions de code
 * (quick-fix), indices en ligne (vague 6b), renommage + préparation,
 * diagnostics POUSSÉS (publication didOpen/didChange/didSave) et
 * didChangeWatchedFiles (rafraîchissement du scan).
 *
 * **Non annoncées (honnêtes)** : jetons sémantiques (distinction non
 * fiable au niveau parseur — dette documentée D3 de la matrice),
 * lentilles de code, hiérarchies d'appels et de types, couleurs de
 * document, formatage à la frappe, édition liée, super-définition
 * expérimentale et source décompilée (extensions propres Java). Les
 * requêtes NON annoncées restent TOLÉRANTES (réponse vide, jamais
 * d'exception — convention I9) mais ABSENTES des annonces.
 *
 * **serverInfo** : nom EXACT « lspkotlin » (miroir « lspjava ») ; version
 * lue du DESCRIPTEUR DE PACK embarqué (clé `version` — générée depuis la
 * version du projet, jamais codée en dur ; la fiche 001 verrouille les
 * valeurs réelles).
 */
public object CapacitesServeur {

    /** Le nom du serveur (serverInfo.name exact « lspkotlin »). */
    public const val NOM_SERVEUR: String = "lspkotlin"

    /** Le chemin de la ressource de version embarquée (le descripteur de pack). */
    private const val RESSOURCE_VERSION: String = "/jo/codeide/lsp/kotlin/pack/version.properties"

    /**
     * La réponse `initialize` : les capabilities de la colonne Kotlin +
     * le serverInfo (nom et version du pack).
     *
     * @return la réponse complète (jamais nulle).
     */
    public fun reponseInitialize(): InitializeResult {
        val resultat = InitializeResult(capabilities())
        resultat.serverInfo = ServerInfo(NOM_SERVEUR, versionDuPack())
        return resultat
    }

    /**
     * Les capabilities du serveur — la table de parité VERROUILLÉE par le
     * test du protocole (miroir W4 : la forme exacte ne change pas sans
     * faire échouer le test).
     *
     * @return les capabilities annoncées.
     */
    public fun capabilities(): ServerCapabilities {
        val capacites = ServerCapabilities()
        // Encodage des positions : UTF-16 (valeur exacte annoncée à
        // initialize — un String JVM EST une suite d'unités UTF-16).
        capacites.setPositionEncoding(PositionEncodingKind.UTF16)
        // Synchronisation PLEIN TEXTE (matrice Kotlin : « complète »).
        capacites.setTextDocumentSync(TextDocumentSyncKind.Full)
        // Navigation.
        capacites.setHoverProvider(true)
        capacites.setDefinitionProvider(true)
        capacites.setTypeDefinitionProvider(true)
        capacites.setImplementationProvider(true)
        capacites.setReferencesProvider(true)
        capacites.setDocumentHighlightProvider(true)
        capacites.setDocumentSymbolProvider(true)
        capacites.setWorkspaceSymbolProvider(true)
        capacites.setSelectionRangeProvider(true)
        // Édition assistée.
        capacites.setCompletionProvider(CompletionOptions(false, listOf(".", "(")))
        capacites.setSignatureHelpProvider(SignatureHelpOptions(listOf("(", ",")))
        capacites.setCodeActionProvider(optionsActions())
        capacites.setDocumentFormattingProvider(true)
        capacites.setDocumentRangeFormattingProvider(true)
        capacites.setFoldingRangeProvider(true)
        capacites.setInlayHintProvider(true)
        capacites.setRenameProvider(optionsRenommage())
        return capacites
    }

    /** Les options d'actions de code (le kind quickfix annoncé). */
    private fun optionsActions(): CodeActionOptions {
        val options = CodeActionOptions()
        options.codeActionKinds = listOf(CodeActionKind.QuickFix)
        return options
    }

    /** Les options de renommage (la PRÉPARATION est servie — étage 4). */
    private fun optionsRenommage(): RenameOptions {
        val options = RenameOptions()
        options.prepareProvider = true
        return options
    }

    /**
     * La version du PACK (serverInfo.version) : lue du descripteur
     * `version.properties` embarqué (clé `version` — générée du projet).
     *
     * @return la version réelle du pack, ou « inconnue » en repli (le test
     * du protocole dénonce le repli — fiche 001).
     */
    private fun versionDuPack(): String {
        return try {
            val flux = CapacitesServeur::class.java.getResourceAsStream(RESSOURCE_VERSION)
                ?: return REPLI_VERSION
            flux.use { Properties().apply { load(it) }.getProperty("version") }
                ?.takeIf { it.isNotBlank() } ?: REPLI_VERSION
        } catch (_: Exception) {
            REPLI_VERSION
        }
    }

    /** Le repli de version (DÉNONCÉ par le test — jamais silencieux). */
    private const val REPLI_VERSION: String = "inconnue"
}

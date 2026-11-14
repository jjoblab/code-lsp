// CodeIDE — capabilities du serveur Kotlin (module :kotlin, étage 6 vague
// 6e — ADR 0118). Code NEUF jjoblab : la réponse `initialize` porte la
// MATRICE des capacités de la colonne Kotlin (docs/LSP_MATRICE_CAPACITES.md
// de codeide-lsp — l'inventaire de parité à reproduire).
package jo.codeide.lsp.kotlin.protocole

import java.util.Properties
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.PositionEncodingKind
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.SaveOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions

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
        // Synchronisation PLEIN TEXTE (matrice Kotlin : « complète ») —
        // chantier F : la SAUVEGARDE porte le TEXTE (SaveOptions) et le
        // serveur PARTICIPE à la sauvegarde (willSaveWaitUntil : les
        // éditions d'organisation des imports sont rendues AVANT
        // l'écriture disque — « Optimize imports on the fly » IJ).
        val sauvegarde = SaveOptions()
        sauvegarde.includeText = true
        val synchronisation = TextDocumentSyncOptions()
        synchronisation.openClose = true
        synchronisation.change = TextDocumentSyncKind.Full
        synchronisation.save = Either.forRight(sauvegarde)
        synchronisation.willSave = false
        synchronisation.willSaveWaitUntil = true
        // L'appel EXPLICITE : la propriété synthétique Kotlin voit le
        // setter Either seul (les surcharges polymorphes Java se
        // résolvent à l'appel de méthode, pas à l'affectation).
        capacites.setTextDocumentSync(synchronisation)
        // Navigation.
        capacites.setHoverProvider(true)
        capacites.setDefinitionProvider(true)
        capacites.setTypeDefinitionProvider(true)
        capacites.setImplementationProvider(true)
        capacites.setReferencesProvider(true)
        capacites.setDocumentHighlightProvider(true)
        capacites.setDocumentSymbolProvider(true)
        // CHANTIER J — le resolve DIFFÉRÉ des symboles bibliothèque
        // (workspaceSymbol/resolve remplit la location à la demande).
        capacites.setWorkspaceSymbolProvider(
            org.eclipse.lsp4j.WorkspaceSymbolOptions(true),
        )
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
        // V-I1 — les CAPACITÉS ARBRE (ADR 0136 étape B.2 : décidables sur
        // l'arbre SEUL, avant le palier 2) : les délimiteurs appariés, les
        // couleurs littérales, la ré-indentation à la frappe.
        capacites.setLinkedEditingRangeProvider(true)
        capacites.setColorProvider(true)
        capacites.setDocumentOnTypeFormattingProvider(optionsFormatageFrappé())
        // V-I2 — les CAPACITÉS PALIER 2 (ADR 0136 étape B.3 : fondées sur
        // la résolution bornée ADR 0123) : la coloration sémantique (légende
        // contractuelle — le jeton ne porte JAMAIS la variance, KH1), les
        // hiérarchies de types et d'appels, les lentilles d'occurrences.
        // Chaque capacité est ANNONCÉE parce que SERVIE et TESTÉE (règle
        // « testée = annoncée », USAGE §2.2 — les 4 absentes passent
        // « annoncée+servie » une à une, miroir du critère 0136).
        capacites.setSemanticTokensProvider(optionsJetonsSemantiques())
        capacites.setCodeLensProvider(optionsLentilles())
        capacites.setCallHierarchyProvider(true)
        capacites.setTypeHierarchyProvider(true)
        return capacites
    }

    /**
     * V-I2 — la LÉGENDE des jetons sémantiques : l'ordre contractuel des
     * types (le miroir de la légende Java V-G) et des modificateurs —
     * l'index de la valeur entière d'un jeton dans le vecteur encodé.
     */
    public val LEGENDE_TYPES_SEMANTIQUES: List<String> = listOf(
        "class", "interface", "enum", "decorator", "method", "property", "parameter", "variable",
    )

    /** La LÉGENDE des modificateurs sémantiques (l'ordre du bitfield). */
    public val LEGENDE_MODIFICATEURS_SEMANTIQUES: List<String> = listOf(
        "declaration", "static", "final", "abstract",
    )

    /**
     * V-I2 — les options de jetons sémantiques : la légende + le mode
     * PLEIN document SANS delta (la coloration Kotlin se calcule entière —
     * le delta relève d'une mesure de latence au gabarit 07, pas d'un
     * survendre).
     */
    private fun optionsJetonsSemantiques(): org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions {
        val legende = org.eclipse.lsp4j.SemanticTokensLegend(
            LEGENDE_TYPES_SEMANTIQUES,
            LEGENDE_MODIFICATEURS_SEMANTIQUES,
        )
        return org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions(legende).apply {
            full = org.eclipse.lsp4j.jsonrpc.messages.Either.forRight(
                org.eclipse.lsp4j.SemanticTokensServerFull(false),
            )
        }
    }

    /** V-I2 — les options de lentilles : PAS de résolveur (la lentille est complète). */
    private fun optionsLentilles(): org.eclipse.lsp4j.CodeLensOptions =
        org.eclipse.lsp4j.CodeLensOptions().apply { resolveProvider = false }

    /**
     * Les options d'actions de code : les kinds ANNONCÉS — quickfix (les
     * correctifs sur diagnostics), source.organizeImports (V-F — KC-10),
     * refactor.extract (V-F — KC-11) — et le RESOLVE annoncé : l'action
     * rendue est complète, le resolve est l'identité (stable par
     * contrat).
     */
    private fun optionsActions(): CodeActionOptions {
        val options = CodeActionOptions()
        options.codeActionKinds = listOf(
            CodeActionKind.QuickFix,
            CodeActionKind.SourceOrganizeImports,
            CodeActionKind.RefactorExtract,
            // Chantier D — la mutabilité (intention « Convertir en var »).
            CodeActionKind.RefactorRewrite,
            // Chantier E — l'inline de variable.
            CodeActionKind.RefactorInline,
        )
        options.resolveProvider = true
        return options
    }

    /**
     * V-I1 — les options de formatage À LA FRAPPE : les déclencheurs
     * `\n` (l'indent de la nouvelle ligne) et `}` (l'alignement sur
     * l'ouvrant) — le périmètre borné du miroir ADR 0115.
     */
    private fun optionsFormatageFrappé(): DocumentOnTypeFormattingOptions {
        val options = DocumentOnTypeFormattingOptions()
        options.firstTriggerCharacter = "\n"
        options.moreTriggerCharacter = listOf("}")
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

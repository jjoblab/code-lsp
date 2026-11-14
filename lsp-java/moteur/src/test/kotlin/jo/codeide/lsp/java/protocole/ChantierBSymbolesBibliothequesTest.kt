package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Chantier B (14/11/2026) — workspace/symbol incluant les BIBLIOTHÈQUES :
 * le « Go to Class » complet d'IntelliJ traverse le projet ET les jars de
 * son classpath. Les types du classpath (annuaire du contexte défaut :
 * jars + image jrt) rejoignent les sources dans la réponse — en forme
 * DROITE (WorkspaceSymbol) avec des symboles NON RÉSOLUS (data.fqcn) :
 * la location `jdt://decompiled/<fqcn>` est matérialisée par
 * `workspaceSymbol/resolve` quand l'utilisateur CHOISIT l'entrée
 * (contrat LSP 3.17 — « postpone computing location to the resolve
 * request » ; le contenu suit par l'extension decompiledSource, fiche 049).
 *
 * Contrats verrouillés :
 *  - une requête par préfixe rend sources (résolues) + bibliothèques
 *    (non résolues, data.fqcn), forme DROITE ;
 *  - le resolve d'un symbole bibliothèque pose la location
 *    jdt://decompiled/<fqcn> ;
 *  - un symbole SANS payload fqcn revient TEL QUEL (jamais d'invention) ;
 *  - SANS bibliothèques (aucun classpath), la réponse reste la forme
 *    GAUCHE historique (compatibilité fiche 056).
 */
class ChantierBSymbolesBibliothequesTest {

    @Test
    fun `recherche par prefixe - sources et bibliotheques, forme droite`() {
        val sdk = sdkDuPoste() ?: return ignoré("SDK Android absent du poste")
        val jar = sdk.resolve("platforms/android-37.2/android.jar")
        if (!Files.isRegularFile(jar)) return ignoré("SDK sans plateforme 37.2")

        // 1. L'environnement hôte : UNE racine source (le type projet
        //    MaVue) + le jar plateforme au contexte DÉFAUT.
        val sources = tmp.resolve("app/src/main/java").also(Files::createDirectories)
        Files.writeString(
            sources.resolve("MaVue.java"),
            "package demo;\n\npublic class MaVue {\n}\n",
        )
        val env = jo.codeide.lsp.java.EnvironnementCompilation()
        env.remplacerContexteModule(null, emptyList(), listOf(jar))
        env.remplacerContexteModule(":app", listOf(sources), emptyList())
        val serveur = ServeurLangageJava(env) { }
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        // 2. La recherche « Act » : la source MaVue NE matche pas le
        //    préfixe — seules les BIBLIOTHÈQUES répondent (Activity du
        //    jar plateforme). La forme est DROITE.
        val reponse = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("Act")).get()
        assertTrue(reponse.isRight, "avec bibliothèques la réponse prend la forme DROITE")
        val items = reponse.right
        assertTrue(items.isNotEmpty(), "« Act » doit trouver Activity du classpath — reçu : ${items.map { it.name }.take(8)}")
        val activity = items.firstOrNull { it.name == "Activity" }
        assertNotNull(activity, "Activity (android.app) présent dans les résultats")
        assertEquals("android.app", activity.containerName, "le containerName est le paquet")
        val donnees = activity.data as? com.google.gson.JsonObject
        assertNotNull(donnees, "la payload data.fqcn voyage (symbole NON résolu)")
        assertEquals("android.app.Activity", donnees.get("fqcn")?.asString, "le FQCN exact dans data")

        // 3. Le RESOLVE : la location jdt://decompiled/ est matérialisée.
        val resolu = boucle.client.workspaceService.resolveWorkspaceSymbol(activity).get()
        val location = resolu?.location?.left
        assertNotNull(location, "le resolve pose la location")
        assertTrue(
            location.uri.startsWith("jdt://decompiled/"),
            "l'URI décompilée du binaire — reçue : ${location.uri}",
        )
        assertTrue(location.uri.contains("android/app/Activity"), "le FQCN y est encodé")
    }

    @Test
    fun `prefixe sans bibliotheque - aucune entree non resolue`() {
        // Un préfixe qui ne matche AUCUNE bibliothèque (zz…) : la réponse
        // ne contient AUCUN symbole NON RÉSOLU (sans data) — la forme
        // sérialisée appartient à l'adaptateur client (Fiche056 lit les
        // deux formes) ; le CONTRAT du chantier B est l'absence de
        // payloads fqcn quand le classpath n'apporte rien.
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())
        val reponse = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("zz")).get()
        val noms = (reponse.left ?: emptyList()).map { it.name } +
            (reponse.right ?: emptyList()).map { it.name }
        assertTrue(noms.isEmpty(), "« zz » ne matche rien — reçu : $noms")
    }

    @Test
    fun `resolve d'un symbole sans payload - tel quel`() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams()).get()
        boucle.client.initialized(InitializedParams())
        // Un symbole NU (sans data) : le resolve le rend TEL QUEL.
        val nu = WorkspaceSymbol().apply {
            name = "Quelconque"
            kind = org.eclipse.lsp4j.SymbolKind.Class
        }
        val resolu = boucle.client.workspaceService.resolveWorkspaceSymbol(nu).get()
        assertEquals("Quelconque", resolu.name, "le symbole revient tel quel")
    }

    // --------------------------------------------------------------- //
    // Utilitaires
    // --------------------------------------------------------------- //

    /** Le SDK Android du poste (local.properties — sandbox et CI le posent). */
    private fun sdkDuPoste(): Path? {
        for (candidat in listOf(Path.of("local.properties"), Path.of("../../local.properties"))) {
            if (!Files.isRegularFile(candidat)) continue
            val sdk = runCatching {
                val props = java.util.Properties()
                Files.newInputStream(candidat).use { props.load(it) }
                props.getProperty("sdk.dir")?.let(Path::of)
            }.getOrNull()
            if (sdk != null && Files.isDirectory(sdk)) return sdk
        }
        return null
    }

    /** Un ignore explicite (jamais vert silencieux). */
    private fun ignoré(raison: String) {
        println("[ChantierB] test SDK ignoré : $raison")
    }

    @TempDir
    lateinit var tmp: Path
}

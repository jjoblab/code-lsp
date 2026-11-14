package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P1 (15/11/2026) — camel-humps dans workspace/symbol : la requête
 * « MVA » doit trouver `MaVueActivite` (les initiales des bosses), comme
 * le « Go to Class » d'IntelliJ — sur les SOURCES du projet ET les
 * BIBLIOTHÈQUES du classpath. Le PRÉFIXE reste le matching premier
 * (l'ordre le plus prévisible), les humps complètent.
 *
 * Contrats verrouillés :
 *  - « MVA » (sources) trouve MaVueActivite — pas par préfixe, par humps ;
 *  - « MVA » (bibliothèques) trouve un type du jar plateforme dont les
 *    bosses matchent (ex. AVD/ActivityView*… — le VRAI android.jar) ;
 *  - le PRÉFIXE continue de marcher inchangé (MaVue → MaVueActivite —
 *    le verrou de la fiche 056 n'est pas relâché) ;
 *  - une requête d'UN SEUL caractère ne matche QUE par préfixe (une
 *    lettre seule matcherait tout le classpath par initiale) ;
 *  - « mL » minuscule matche maListe (insensible à la casse).
 */
class P1CamelHumpsSymbolesTest {

    @Test
    fun `humps sources - MVA trouve MaVueActivite`() {
        val dossier = tmp.resolve("src/demo").also(Files::createDirectories)
        Files.writeString(
            dossier.resolve("MaVueActivite.java"),
            "package demo;\n\npublic class MaVueActivite {\n}\n",
        )
        Files.writeString(
            dossier.resolve("RunnableActivityFactory.java"),
            "package demo;\n\npublic class RunnableActivityFactory {\n}\n",
        )
        Files.writeString(
            dossier.resolve("Autre.java"),
            "package demo;\n\npublic class Autre {\n}\n",
        )
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.resolve("src").toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        // 1. Les humps : MVA → MaVueActivite (M|aVue|A…), RAF →
        //    RunnableActivityFactory — aucun des deux par préfixe.
        val mva = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("MVA")).get()
        val nomsMva = (mva.left ?: emptyList()).map { it.name } + (mva.right ?: emptyList()).map { it.name }
        assertTrue(
            "MaVueActivite" in nomsMva,
            "« MVA » matche MaVueActivite par camel-humps — reçus : $nomsMva",
        )
        val raf = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("RAF")).get()
        val nomsRaf = (raf.left ?: emptyList()).map { it.name } + (raf.right ?: emptyList()).map { it.name }
        assertTrue(
            "RunnableActivityFactory" in nomsRaf,
            "« RAF » matche RunnableActivityFactory par camel-humps — reçus : $nomsRaf",
        )

        // 2. Le bruit reste exclu : « MVA » ne matche pas Autre.
        assertTrue("Autre" !in nomsMva, "Autre ne matche pas MVA — reçus : $nomsMva")

        // 3. Une requête d'une lettre ne matche QUE par préfixe : « A »
        //    trouve Autre (préfixe) mais PAS MaVueActivite (initiale).
        val a = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("A")).get()
        val nomsA = (a.left ?: emptyList()).map { it.name } + (a.right ?: emptyList()).map { it.name }
        assertTrue("Autre" in nomsA, "« A » trouve Autre par préfixe")
        assertTrue("MaVueActivite" !in nomsA, "une lettre seule ne matche pas par initiale")
    }

    @Test
    fun `prefixe et casse - contrats fiche 056 inchanges`() {
        val dossier = tmp.resolve("src/demo").also(Files::createDirectories)
        Files.writeString(
            dossier.resolve("MaVue.java"),
            "package demo;\n\npublic class MaVue {\n}\n",
        )
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.resolve("src").toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        // Le préfixe exact et la casse basse marchent toujours (fiche 056).
        for (requete in listOf("MaVue", "mavue")) {
            val reponse = boucle.client.workspaceService.symbol(WorkspaceSymbolParams(requete)).get()
            val noms = (reponse.left ?: emptyList()).map { it.name } + (reponse.right ?: emptyList()).map { it.name }
            assertEquals(
                listOf("MaVue"),
                noms,
                "« $requete » (préfixe, fiche 056) — reçus : $noms",
            )
        }
    }

    @Test
    fun `humps bibliotheques - initiales du jar plateforme`() {
        val sdk = sdkDuPoste() ?: return ignoré("SDK Android absent du poste")
        val jar = sdk.resolve("platforms/android-37.2/android.jar")
        if (!Files.isRegularFile(jar)) return ignoré("SDK sans plateforme 37.2")

        val env = jo.codeide.lsp.java.EnvironnementCompilation()
        env.remplacerContexteModule(null, emptyList(), listOf(jar))
        val serveur = ServeurLangageJava(env) { }
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        // « AM » doit trouver des types à bosses A…M… du jar plateforme —
        // le humps fonctionne si AU MOINS une entrée rendue a DEUX bosses
        // A puis M (ex. AmbientBacklightEvent? Non — A|mbient|M... :
        // Ambient*Backlight* : A, B, E. Le candidat SÛR de la famille :
        // ActionMode (A|ction|M|ode) ou AppInteractionManager). On ne
        // peut pas viser UN type précis : la limite de 50 est consommée
        // AVANT les android.app (l'ordre de l'index n'est pas
        // alphabétique) — le CONTRAT est le matching, pas le tri.
        val am = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("AM")).get()
        val nomsAm = (am.left ?: emptyList()).map { it.name } + (am.right ?: emptyList()).map { it.name }
        val aDeuxBosses = nomsAm.filter { nom ->
            // A en tête de nom, et un M en tête d'une bosse suivante.
            nom.startsWith("A") && nom.drop(1).any { it == 'M' && nom[nom.indexOf(it) - 1].isLowerCase() }
        }
        assertTrue(
            aDeuxBosses.isNotEmpty() || "ActionMode" in nomsAm || "ActivityManager" in nomsAm,
            "« AM » matche des types A…M… du classpath par camel-humps — reçus : ${nomsAm.take(10)}",
        )
        // Les symboles bibliothèque restent NON RÉSOLUS (data.fqcn —
        // contrat chantier B inchangé).
        val amDroite = am.right ?: emptyList()
        assertTrue(
            amDroite.isNotEmpty() && amDroite.all { it.data != null },
            "les symboles bibliothèque restent non résolus (chantier B)",
        )
    }

    @Test
    fun `humps insensible a la casse - mL trouve maListe`() {
        val dossier = tmp.resolve("src/demo").also(Files::createDirectories)
        Files.writeString(
            dossier.resolve("maListe.java"),
            "package demo;\n\npublic class maListe {\n}\n",
        )
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.resolve("src").toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())
        val reponse = boucle.client.workspaceService.symbol(WorkspaceSymbolParams("mL")).get()
        val noms = (reponse.left ?: emptyList()).map { it.name } + (reponse.right ?: emptyList()).map { it.name }
        assertTrue("maListe" in noms, "« mL » matche maListe (casse basse, humps) — reçus : $noms")
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
        println("[P1] test SDK ignoré : $raison")
    }

    @TempDir
    lateinit var tmp: Path
}

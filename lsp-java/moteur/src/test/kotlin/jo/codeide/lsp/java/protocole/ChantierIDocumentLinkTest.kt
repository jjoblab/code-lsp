package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Chantier I (14/11/2026) — documentLink des `{@link}` javadoc : les
 * références `{@link Foo}` / `{@link Foo#bar()}` de la javadoc du
 * document ouvert deviennent des LIENS (DocumentLink LSP) vers la
 * définition — le fichier source du projet (URI fichier) quand le type
 * y vit, la forme `jdt://decompiled/<fqcn>` pour un binaire (le client
 * appelle decompiledSource — fiche 049). Un lien NON résolu est OMIS
 * (jamais fabriqué — R5).
 *
 * La résolution du nom simple passe par les IMPORTS de l'unité, puis
 * l'annuaire du classpath par nom simple.
 */
class ChantierIDocumentLinkTest {

    @Test
    fun `liens d'une javadoc - source projet, binaire importe, membre`() {
        val sdk = sdkDuPoste() ?: return ignoré("SDK Android absent du poste")
        val jar = sdk.resolve("platforms/android-37.2/android.jar")
        if (!Files.isRegularFile(jar)) return ignoré("SDK sans plateforme 37.2")

        // 1. Une racine source avec le type Aide (la cible PROJET) —
        //    l'arborescence du PAQUET (src/demo/Aide.java) : l'annuaire
        //    indexe les sources par dossier de paquet.
        val sources = tmp.resolve("src").also(Files::createDirectories)
        Files.createDirectories(sources.resolve("demo"))
        Files.writeString(sources.resolve("demo/Aide.java"), "package demo;\n\npublic class Aide {\n}\n")
        Files.createDirectories(sources.resolve("demo"))
        Files.writeString(
            sources.resolve("demo/Main.java"),
            "package demo;\n\npublic class Main {\n}\n",
        )

        // 2. Le moteur : racines + jar plateforme (la cible BINAIRE).
        val moteur = jo.codeide.lsp.java.MoteurJava(racines = listOf(sources), jars = listOf(jar))
        val serveur = ServeurLangageJava(fabriqueMoteur = { _ -> moteur })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        val source = """
            package demo;

            import android.app.Activity;

            /**
             * Voir {@link Aide} et {@link Activity#onCreate} et {@link InconnuAuMonde}.
             */
            public class Main {
            }
        """.trimIndent()
        val uri = sources.resolve("demo/Main.java").toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, source)),
        )
        val liens = boucle.client.textDocumentService.documentLink(
            DocumentLinkParams(TextDocumentIdentifier(uri)),
        ).get()
        assertNotNull(liens, "la réponse existe (jamais null)")
        assertEquals(2, liens.size, "DEUX liens (Aide + Activity), l'inconnu OMIS — reçus : ${liens.map { it.target }}")

        // Le lien PROJET : URI fichier vers Aide.java.
        val lienAide = liens.first { it.target.contains("Aide.java") }
        assertTrue(lienAide.target.startsWith("file:"), "la cible projet est une URI fichier — ${lienAide.target}")

        // Le lien BINAIRE : jdt://decompiled/android/app/Activity.
        val lienActivity = liens.first { it.target.startsWith("jdt://") }
        assertEquals(
            "jdt://decompiled/android/app/Activity",
            lienActivity.target,
            "la cible binaire est la forme décompilée",
        )

        // La PLAGE du lien couvre le NOM dans le tag (pas le tag entier).
        assertTrue(lienAide.range.start.character > 0, "la plage est calée sur le nom")
    }

    @Test
    fun `document sans javadoc - liste vide honnete`() {
        val serveur = ServeurLangageJava(fabriqueMoteur = { null })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams()).get()
        boucle.client.initialized(InitializedParams())
        val uri = tmp.resolve("Nu.java").toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, "package demo;\npublic class Nu {\n}\n")),
        )
        val liens = boucle.client.textDocumentService.documentLink(
            DocumentLinkParams(TextDocumentIdentifier(uri)),
        ).get()
        assertTrue(liens.isEmpty(), "aucun {@link} ⇒ liste VIDE — reçus : ${liens.map { it.target }}")
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
        println("[ChantierI] test SDK ignoré : $raison")
    }

    @TempDir
    lateinit var tmp: Path
}

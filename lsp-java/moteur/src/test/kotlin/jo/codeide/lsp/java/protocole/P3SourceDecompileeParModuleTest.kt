package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P3 (15/11/2026) — la source décompilée servie par le BON module : quand
 * le même FQCN vit dans plusieurs classpaths (une plateforme par module —
 * le cas réel : une app multi-variante avec des compileSdk différents),
 * le hover d'un document du module `:app` doit proposer le lien du MODULE,
 * et la commande doit sonder le moteur du module AVANT le défaut.
 *
 * Deux jars fabriqués pour l'occasion portent le MÊME FQCN
 * (`demo.Plateforme`) avec une MÉTHODE DISTINCTE (`marqueurApp()` /
 * `marqueurDefaut()` — le stub décompilé rend les méthodes publiques, pas les champs) : la source servie révèle quel moteur a répondu.
 *
 * Contrats verrouillés :
 *  - hover d'un document du module : le lien embarque le module
 *    (`x-codeide.decompile://:app/demo.Plateforme`) ;
 *  - la commande avec module=`:app` sert la version DU MODULE (le
 *    marqueur `MARQUEUR_APP` figure dans la source) ;
 *  - la commande sans module sert la version du DÉFAUT (repli
 *    inchangé — chantier H intact) ;
 *  - la commande avec un module INCONNU replie sur le défaut (jamais
 *    d'exception, jamais de source perdue).
 */
class P3SourceDecompileeParModuleTest {

    @Test
    fun `le module d'abord, le defaut en repli`() {
        // Deux jars : même FQCN, champ distinct — le marqueur trahit le
        // moteur qui a servi la source.
        val jarApp = fabriquerJar("app", "APP")
        val jarDefaut = fabriquerJar("defaut", "DEFAUT")

        val env = jo.codeide.lsp.java.EnvironnementCompilation()
        env.remplacerContexteModule(null, emptyList(), listOf(jarDefaut))
        env.remplacerContexteModule(":app", emptyList(), listOf(jarApp))
        // Le routeur : les documents sous app/ appartiennent à :app.
        env.routeurFichierModule = { chemin -> if (chemin.contains("app/")) ":app" else null }

        val serveur = ServeurLangageJava(env) { }
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = tmp.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())

        // Un document du module :app utilisant le type du classpath.
        val dossier = tmp.resolve("app").also(Files::createDirectories)
        val source = """
            package demo;

            import demo.Plateforme;

            public class Ecran {
                Plateforme p;
            }
        """.trimIndent()
        val uri = dossier.resolve("Ecran.java").toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, source)),
        )

        // 1. Le hover d'un document du module porte le lien AVEC module
        //    (survol de l'USAGE du type — la déclaration du champ, comme
        //    le chantier H survole « extends Activity »).
        val idx = source.indexOf("Plateforme", source.indexOf("class Ecran"))
        val ligne = source.substring(0, idx).count { it == '\n' }
        val colonne = idx - source.lastIndexOf('\n', idx) - 1
        val hover = boucle.client.textDocumentService.hover(
            HoverParams(TextDocumentIdentifier(uri), org.eclipse.lsp4j.Position(ligne, colonne)),
        ).get()
        assertNotNull(hover, "le hover du type binaire doit résoudre")
        val texte = hover.contents.right?.value ?: ""
        assertTrue(
            texte.contains("x-codeide.decompile://:app/demo.Plateforme"),
            "le lien embarque le module :app — reçu : $texte",
        )

        // 2. La commande AVEC module sert la version DU MODULE.
        val reponseModule = boucle.client.workspaceService.executeCommand(
            ExecuteCommandParams("codeide.decompiledSource", listOf("demo.Plateforme", ":app")),
        ).get() as Map<*, *>
        val sourceModule = reponseModule["source"]?.toString() ?: ""
        assertTrue(
            "marqueurApp" in sourceModule,
            "la commande avec module=:app sert la version du MODULE (marqueurApp) — reçue : ${sourceModule.take(200)}",
        )

        // 3. La commande SANS module sert la version du DÉFAUT (chantier H
        //    intact).
        val reponseDefaut = boucle.client.workspaceService.executeCommand(
            ExecuteCommandParams("codeide.decompiledSource", listOf("demo.Plateforme")),
        ).get() as Map<*, *>
        val sourceDefaut = reponseDefaut["source"]?.toString() ?: ""
        assertTrue(
            "marqueurDefaut" in sourceDefaut,
            "la commande sans module sert la version du DÉFAUT (marqueurDefaut) — reçue : ${sourceDefaut.take(200)}",
        )

        // 4. Un module INCONNU replie sur le défaut (jamais de source
        //    perdue, jamais d'exception).
        val reponseInconnu = boucle.client.workspaceService.executeCommand(
            ExecuteCommandParams("codeide.decompiledSource", listOf("demo.Plateforme", ":biblio")),
        ).get() as Map<*, *>
        val sourceInconnu = reponseInconnu["source"]?.toString() ?: ""
        assertTrue(
            "marqueurDefaut" in sourceInconnu,
            "un module inconnu replie sur le DÉFAUT — reçue : ${sourceInconnu.take(200)}",
        )
    }

    // --------------------------------------------------------------- //
    // Utilitaires
    // --------------------------------------------------------------- //

    /**
     * Le binaire `javac` du poste : le JVM de test est parfois un JRE
     * (sandbox — `java.home` sans bin/javac). Ordre de recherche :
     * `java.home` d'abord, puis les JDK du cache Gradle (toolchain),
     * puis le PATH. Introuvable → le test s'ignore EXPLICITEMENT (jamais
     * vert silencieux, jamais rouge pour l'environnement).
     */
    private fun javacDuPoste(): String? {
        val candidats = mutableListOf(
            Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
            Path.of(System.getProperty("user.home"), ".gradle", "jdks").toString(),
        )
        for (base in candidats) {
            val chemin = Path.of(base)
            if (Files.isRegularFile(chemin)) return chemin.toString()
            if (Files.isDirectory(chemin)) {
                // Le cache Gradle : premier JDK extrait qui porte bin/javac.
                val trouve = chemin.toFile().walkTopDown()
                    .filter { it.isFile && it.name == "javac" }
                    .firstOrNull()
                if (trouve != null) return trouve.absolutePath
            }
        }
        return runCatching { ProcessBuilder("which", "javac").start().inputStream.bufferedReader().readText().trim() }
            .getOrNull()?.takeIf { it.isNotEmpty() && Files.isRegularFile(Path.of(it)) }
    }

    /**
     * Un jar d'un seul type `demo.Plateforme` avec un champ `String
     * MARQUEUR_<nom>` distinct — compilé par le javac du poste (ci-dessus).
     */
    /** Le nom de méthode marqueur du jar `nom` (`marqueurApp`…). */
    private fun methodeMarqueur(nom: String): String = "marqueur" + nom.replaceFirstChar { it.uppercase() }

    private fun fabriquerJar(nom: String, marqueur: String): Path {
        val javacBin = javacDuPoste()
            ?: throw org.opentest4j.TestAbortedException("aucun javac sur le poste (JRE seul) — test sauté explicitement")
        val dossier = tmp.resolve("jar-$nom").also(Files::createDirectories)
        val sources = dossier.resolve("src/demo").also(Files::createDirectories)
        Files.writeString(
            sources.resolve("Plateforme.java"),
            """
            package demo;

            public class Plateforme {
                public String ${methodeMarqueur(nom)}() { return "$marqueur"; }
            }
            """.trimIndent(),
        )
        val classes = dossier.resolve("classes").also(Files::createDirectories)
        val javac = ProcessBuilder(
            javacBin,
            sources.resolve("Plateforme.java").toAbsolutePath().toString(),
            "-d",
            classes.toAbsolutePath().toString(),
        ).apply { redirectErrorStream(true) }.start().apply { waitFor() }
        val sortie = javac.inputStream.bufferedReader().readText()
        assertTrue(javac.exitValue() == 0, "javac doit réussir (jar $nom) : $sortie")
        val jar = dossier.resolve("plateforme-$nom.jar")
        // jar vit à côté de javac (même JDK).
        val jarBin = Path.of(javacBin).resolveSibling("jar").toString()
        val jarTool = ProcessBuilder(
            jarBin,
            "--create",
            "--file",
            jar.toAbsolutePath().toString(),
            "-C",
            classes.toAbsolutePath().toString(),
            "demo",
        ).apply { redirectErrorStream(true) }.start().apply { waitFor() }
        assertTrue(jarTool.exitValue() == 0, "jar doit réussir (jar $nom)")
        return jar
    }

    @TempDir
    lateinit var tmp: Path
}

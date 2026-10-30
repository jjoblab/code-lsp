package jo.codeide.lsp.java.protocole

import java.net.URI
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * RÉGRESSION ART (retours appareil du 30/10 10:59, logs
 * `codeide-logs-2026-10-30-105910.zip`, CodeIDE 0.204.3 / moteur v1.1.3 —
 * correctif v1.1.4) : sur ART, le fournisseur `jrt` du `lib/jrt-fs.jar` du
 * JDK Termux externe NE PEUT PAS s'instancier ( internes JDK absents du
 * runtime — incident contenu en v1.1.2 ) : l'image jrt est donc ABSENTE
 * sur l'appareil alors que le classpath porte `android.jar`, qui contient
 * `java.*` ( boot classpath Android ).
 *
 * AVANT v1.1.4, la sonde du régime plein était `jrtDisponible()` SEULE :
 * l'appareil vivait en régime instantané ALORS QUE sa plateforme était
 * résoluble — les familles entiéres se taisaient ( diagnostics
 * analytiques muets — import inutilisé, locale inutilisée, champ privé
 * inutilisé —, go-to définition/implémentations/super vide, survol vide,
 * inlay vides, complétion réduite aux mots-clés + fichier courant :
 * `import ` → 6 items mots-clés, `import com.` → 58 items mots-clés,
 * documentHighlight → « no results » 50 fois, inlayHint → « no results »
 * 28 fois — cf. INTEGRATION-CODEIDE §12 ).
 *
 * Le montage SIMULE l'appareil fidélement :
 *  - un home JDK factice SANS `lib/modules` ( pas de jrt — sur JVM le
 *    fournisseur système masque toute panne de `jrt-fs.jar`, le seul
 *    réglage déterministe est l'absence d'image, cf. Fiche031 ) ;
 *  - un JAR DE PLATEFORME construit depuis l'image jrt du runtime (
 *    `java.lang.Object`, `java.lang.String`, `java.lang.Iterable`,
 *    `java.lang.Integer`, `java.util.ArrayList` ) — le rôle d'`android.jar`
 *    sur l'appareil ;
 *  - un projet `com/exemple/monip` — le paquet de l'utilisateur.
 *
 * Les assertions parcourent la CHAÎNE demandée par le propriétaire (
 * « une fonctionnalité à la fois depuis le LSP jusqu'à l'éditeur » ) :
 * moteur ( sonde, diagnostics, complétion, navigation ) PUIS protocole (
 * aller-retour LSP4J authentique par [BoucleLocale] — définition servie,
 * publishDiagnostics portant l'import inutilisé ).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlateformeClasspathARTTest {

    /** La racine des fixtures (MANUELLE — motif Fiche026/066/054/Repro). */
    private lateinit var dossier: Path

    /** Le home JDK factice SANS lib/modules (aucune image jrt). */
    private lateinit var jdkFactice: Path

    /** Le jar de plateforme extrait du jrt du runtime (rôle d'android.jar). */
    private lateinit var jarPlateforme: Path

    /** La boucle locale LSP4J (serveur réel + client espion). */
    private var boucle: BoucleLocale? = null

    // ---------------------------------------------------------------- //
    // Sources — le paquet du projet utilisateur est com.exemple.monip
    // ---------------------------------------------------------------- //

    /** Fichier PORTANT les trois signaux « unused » (import, champ, locale). */
    private val sourceUnused = """
        package com.exemple.monip;

        import java.util.ArrayList;

        public class Main {
            private int jamaisUtilise = 3;

            public int travail() {
                int x = 42;
                return 0;
            }
        }
    """.trimIndent() + "\n"

    /** Fichier sain pour la résolution/navigation (liaison champ↔usage). */
    private val sourceTravail = """
        package com.exemple.monip;

        public class Travail {
            private int total = 0;

            public int cumule() {
                return total + 1;
            }
        }
    """.trimIndent() + "\n"

    /** « import » sans préfixe au TOP — ce que l'utilisateur a tapé. */
    private val sourceImportVide = """
        package com.exemple.monip;

        import ${' '}

        public class Main {
        }
    """.trimIndent() + "\n"

    // ---------------------------------------------------------------- //
    // Montage
    // ---------------------------------------------------------------- //

    @BeforeAll
    fun avantTout() {
        dossier = Files.createTempDirectory("art-plateforme")
        val paquet = dossier.resolve("com/exemple/monip")
        Files.createDirectories(paquet)
        Files.writeString(paquet.resolve("Main.java"), "package com.exemple.monip;\n\npublic class Main {\n}\n")

        jdkFactice = Files.createTempDirectory("art-fauxjdk")

        jarPlateforme = construireJarPlateforme() ?: run {
            Assumptions.assumeTrue(false, "image jrt du runtime illisible — ART simulée ignorée")
            return
        }

        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine ->
                racine?.let {
                    jo.codeide.lsp.java.MoteurJava(
                        racines = listOf(it),
                        jars = listOf(jarPlateforme),
                        jdkHome = jdkFactice,
                    )
                }
            },
        )
        boucle = BoucleLocale(serveur).also { b ->
            b.demarrer()
            b.client.initialize(InitializeParams().apply { rootUri = dossier.toUri().toString() }).get()
            b.client.initialized(InitializedParams())
        }
    }

    /**
     * Construit le jar de plateforme depuis l'image jrt du RUNTIME —
     * le remplaçant déterministe d'`android.jar` (boot classpath Android :
     * `java.*` + `android.*`).
     *
     * @return le jar, ou null si l'image jrt du runtime est illisible (
     *   la suite est ignorée par hypothèse visible).
     */
    private fun construireJarPlateforme(): Path? {
        val fs = try {
            FileSystems.getFileSystem(URI("jrt:/"))
        } catch (_: FileSystemNotFoundException) {
            return null
        }
        val classes = listOf(
            "java/lang/Object",
            "java/lang/String",
            "java/lang/Iterable",
            "java/lang/Integer",
            "java/util/ArrayList",
        )
        for (chemin in classes) {
            if (!Files.isRegularFile(fs.getPath("/modules/java.base/$chemin.class"))) return null
        }
        val jar = Files.createTempFile("plateforme-art", ".jar")
        java.util.zip.ZipOutputStream(Files.newOutputStream(jar)).use { zip ->
            for (chemin in classes) {
                zip.putNextEntry(java.util.zip.ZipEntry("$chemin.class"))
                zip.write(Files.readAllBytes(fs.getPath("/modules/java.base/$chemin.class")))
                zip.closeEntry()
            }
        }
        return jar
    }

    /** Le moteur ART : jdkHome factice + plateforme du classpath. */
    private fun moteurArt(): jo.codeide.lsp.java.MoteurJava =
        jo.codeide.lsp.java.MoteurJava(
            racines = listOf(dossier),
            jars = listOf(jarPlateforme),
            jdkHome = jdkFactice,
        )

    /** Ouvre un document sur la boucle et rend son identifiant. */
    private fun ouvrir(nom: String, texte: String): TextDocumentIdentifier {
        val b = boucle ?: fail("boucle absente")
        val uri = dossier.resolve("com/exemple/monip/$nom").toUri().toString()
        b.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, texte)),
        )
        return TextDocumentIdentifier(uri)
    }

    /** La position (ligne, colonne) d'un texte cherché dans une source. */
    private fun positionDe(source: String, cherche: String): Position {
        val indice = source.indexOf(cherche)
        if (indice < 0) fail("« $cherche » introuvable dans la source")
        val ligne = source.substring(0, indice).count { it == '\n' }
        val debutLigne = source.lastIndexOf('\n', indice - 1) + 1
        return Position(ligne, indice - debutLigne)
    }

    /** La position (ligne, colonne) juste APRÈS un texte cherché. */
    private fun positionApres(source: String, cherche: String): Position {
        val base = positionDe(source, cherche)
        return Position(base.line, base.character + cherche.length)
    }

    /** L'attente bornée d'une valeur (polling 50 ms — motif Repro). */
    private fun <T> attendre(delaiMs: Long = 5_000, fournisseur: () -> T?): T? {
        val fin = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delaiMs)
        while (System.nanoTime() < fin) {
            fournisseur()?.let { return it }
            Thread.sleep(50)
        }
        return null
    }

    // ---------------------------------------------------------------- //
    // 1. La sonde du régime plein — plateforme résoluble par le classpath
    // ---------------------------------------------------------------- //

    @Test
    fun laPlateformeDuClasspathRendLeMoteurUtilisableSansJrt() {
        val moteur = moteurArt()
        assertTrue(
            moteur.isLikelyUsable(),
            "jdkHome sans lib/modules MAIS jar portant java.lang.Object : régime PLEIN (v1.1.4)",
        )
    }

    @Test
    fun sansJrtNiJarPorteurLeRepliInstantaneSurvit() {
        val moteur = jo.codeide.lsp.java.MoteurJava(
            racines = listOf(dossier),
            jars = emptyList(),
            jdkHome = jdkFactice,
        )
        assertFalse(
            moteur.isLikelyUsable(),
            "ni jrt ni jar porteur de la plateforme : régime instantané (fiche 028) inchangé",
        )
    }

    // ---------------------------------------------------------------- //
    // 2. Diagnostics analytiques — les « familles muettes » de l'appareil
    // ---------------------------------------------------------------- //

    @Test
    fun lImportInutiliseEstPublieDepuisLeClasspath() {
        val moteur = moteurArt()
        val diagnostics = moteur.diagnose("/com/exemple/monip/Main.java", sourceUnused)
        val messages = diagnostics.map { "${it.severite}:${it.message}" }
        println("[ART] diagnostics unused → $messages")
        assertTrue(
            messages.any { it.contains("The import java.util.ArrayList is never used") },
            "l'import inutilisé est rendu — reçu : $messages",
        )
        assertTrue(
            diagnostics.none { it.message.contains("cannot be resolved") },
            "aucun « cannot be resolved » fabriqué — reçu : $messages",
        )
    }

    @Test
    fun laResolutionJavaLangPasseParLeJarDePlateforme() {
        val moteur = moteurArt()
        val diagnostics = moteur.diagnose("/com/exemple/monip/Travail.java", sourceTravail)
        val messages = diagnostics.map { it.message }
        println("[ART] diagnostics Travail → $messages")
        assertTrue(
            diagnostics.none { it.message.contains("cannot be resolved") },
            "java.lang.Object résout depuis le jar de plateforme — reçu : $messages",
        )
    }

    // ---------------------------------------------------------------- //
    // 3. Complétion — les paquets du classpath, pas les mots-clés seuls
    // ---------------------------------------------------------------- //

    @Test
    fun laCompletionDImportProposeLesPaquetsDuClasspath() {
        val moteur = moteurArt()
        val items = moteur.complete(
            "/com/exemple/monip/Main.java",
            sourceImportVide,
            sourceImportVide.indexOf("import ") + "import ".length,
        )
        val libelles = items.map { it.libelle }
        println("[ART] complétion import (vide) → ${libelles.size} items : ${libelles.take(20)}")
        assertTrue("com" in libelles, "« com » (paquet projet, racine source) est proposé — reçu : ${libelles.take(12)}")
        assertTrue("java" in libelles, "« java » (paquet du jar de plateforme) est proposé — reçu : ${libelles.take(12)}")
    }

    // ---------------------------------------------------------------- //
    // 4. Navigation — définition et surbrillance du document
    // ---------------------------------------------------------------- //

    @Test
    fun laDefinitionDuChampResoutDepuisLeClasspath() {
        val moteur = moteurArt()
        val cibles = moteur.definition(
            "/com/exemple/monip/Travail.java",
            sourceTravail,
            sourceTravail.indexOf("total + 1"),
        )
        println("[ART] definition(total) → ${cibles.size} cible(s)")
        assertTrue(cibles.isNotEmpty(), "le go-to du champ « total » rend sa déclaration — reçu : ${cibles.size}")
    }

    @Test
    fun laSurbrillanceDeDocumentRendLesOccurrences() {
        val moteur = moteurArt()
        val surbrillances = moteur.surbrillances(
            "/com/exemple/monip/Travail.java",
            sourceTravail,
            sourceTravail.indexOf("total + 1"),
        )
        println("[ART] documentHighlight(total) → ${surbrillances.size} occurrence(s)")
        assertTrue(
            surbrillances.size >= 2,
            "les occurrences de « total » (déclaration + usage) sont rendues — reçu : ${surbrillances.size}",
        )
    }

    // ---------------------------------------------------------------- //
    // 5. Le protocole — aller-retour LSP4J authentique en régime ART
    // ---------------------------------------------------------------- //

    @Test
    fun surLeFilLaDefinitionEstServieEtDiagnosticsPublies() {
        val b = boucle ?: fail("boucle absente")

        // 5a. publishDiagnostics porte l'import inutilisé sur le FIL.
        val uriMain = ouvrir("Main.java", sourceUnused)
        val publication = attendre {
            b.espion.publications.lastOrNull { it.uri == uriMain.uri && it.diagnostics.isNotEmpty() }
        } ?: fail("aucune publication de diagnostics pour Main.java en régime ART")
        val messages = publication.diagnostics.map { it.message }
        println("[ART] publishDiagnostics sur le fil → $messages")
        assertTrue(
            messages.any { it.contains("The import java.util.ArrayList is never used") },
            "l'import inutilisé est PUBLIÉ sur le fil — reçu : $messages",
        )

        // 5b. textDocument/definition répond NON VIDE sur le fil.
        val uriTravail = ouvrir("Travail.java", sourceTravail)
        val params = DefinitionParams()
        params.textDocument = uriTravail
        params.position = positionDe(sourceTravail, "total + 1")
        val reponse = b.client.textDocumentService.definition(params).get()
        val cibles = reponse.left ?: emptyList()
        println("[ART] textDocument/definition sur le fil → ${cibles.size} cible(s)")
        assertTrue(cibles.isNotEmpty(), "la définition est SERVIE sur le fil — reçu : ${cibles.size}")

        // 5c. textDocument/completion répond des PAQUETS sur le fil.
        val uriCompletion = ouvrir("Completion.java", sourceImportVide)
        val completion = CompletionParams()
        completion.textDocument = uriCompletion
        completion.position = positionApres(sourceImportVide, "import ")
        val liste = b.client.textDocumentService.completion(completion).get()
        val libelles = (liste.right?.items ?: liste.left ?: emptyList()).map { it.label }
        println("[ART] complétion sur le fil → ${libelles.size} items : ${libelles.take(20)}")
        assertTrue(
            "com" in libelles && "java" in libelles,
            "les paquets du classpath sont proposés sur le fil — reçu : ${libelles.take(12)}",
        )
    }
}

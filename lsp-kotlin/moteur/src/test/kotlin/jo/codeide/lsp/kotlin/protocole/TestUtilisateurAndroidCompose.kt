// CodeIDE — TEST UTILISATEUR du serveur Kotlin LSP sur un VRAI projet
// Android Kotlin + Compose (mission « test utilisateur dun vrai projet
// android kotlin puis compose », 13/11/2026). Le montage miroir du harnais
// BoucleLocale des cas dorés protocole : serveur réel + client espion
// reliés par les tuyaux lsp4j (allers-retours JSON-RPC AUTHENTIQUES), le
// classpath Android COMPOSE RÉEL (jars androidx téléchargés) nourrit le
// moteur — le chemin de production complet, comme Android Studio ouvre un
// projet et sert les complétions.
package jo.codeide.lsp.kotlin.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.session.MoteurLangageKotlinNoyau
import jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionTriggerKind
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le test utilisateur Android Compose : UNE session LSP complète sur le
 * projet réel `projet-test-android` — didOpen des fichiers, complétions à
 * tous les niveaux (membre, racine, import, paramètre nommé), aide à la
 * signature, survol, diagnostics, navigation, renommage, formatage.
 *
 * Le classpath est nourri par les VRAIS jars androidx (runtime, ui,
 * material3, foundation, lifecycle, activity-compose, stdlib) téléchargés
 * de Maven Google — l'objectif : mesurer que le serveur répond EXACTEMENT
 * comme Android Studio le ferait (complétion `Text(` avec aide signature,
 * membres Compose sur `Modifier.`, etc.).
 */
class TestUtilisateurAndroidCompose {

    companion object {

        /** Le montage complet exige le projet réel ET ses jars (gate honnête). */
        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun exigenceMontage() {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isDirectory(Path.of(cheminProjet()).resolve("app/src/main/java")) &&
                    Files.isDirectory(Path.of(cheminProjet()).resolve("classpath-jars")),
                "projet Android de test absent (${cheminProjet()}) — gate ignorée, jamais un vert silencieux (R5)",
            )
        }

        private fun cheminProjet(): String =
            System.getenv("PROJET_ANDROID_TEST") ?: "/home/z/my-project/projet-test-android"
    }

    /** La racine du projet Android de test (sources réelles). */
    private val projet: Path = Path.of(
        System.getenv("PROJET_ANDROID_TEST")
            ?: "/home/z/my-project/projet-test-android",
    )



    /** Le dossier des jars du classpath Android Compose. */
    private val jarsClasspath: Path = projet.resolve("classpath-jars")

    /** La fabrique de session avec le classpath Android réel. */
    private fun fabriqueClasspath(): ClasspathKotlin {
        val jars = jarsClasspath.toFile().listFiles()
            ?.filter { it.name.endsWith(".jar") }
            ?.sortedBy { it.name }
            ?: emptyList()
        assertTrue(jars.isNotEmpty(), "les jars du classpath Android sont absents : $jarsClasspath")
        return ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "test-android:$i:${jar.name}")
            },
            racinesSources = listOf(projet.resolve("app/src/main/java").toString()),
        )
    }

    /** Le test principal : session complète sur le projet Android. */
    @Test
    fun `session-utilisateur-complete-sur-projet-android-compose`() {
        // ── Montage : moteur nourricier + session branchée, comme l'hôte.
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = fabriqueClasspath()
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)

        // Le serveur réel sur cette session.
        val serveur = ServeurLangageKotlin(fabriqueSession = { session })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()

        // initialize + initialized (le protocole complet).
        val parametres = InitializeParams().apply { rootUri = projet.toUri().toString() }
        val resultat = serveur.initialize(parametres).get(30, TimeUnit.SECONDS)
        assertEquals("lspkotlin", resultat.serverInfo.name, "le serveur s'annonce")
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())

        // Attente de la couverture de l'index (guichet prêt — miroir
        // SocleCoherence.attendreCouvert, borne large : gros classpath).
        attendreCouvert(session, 300)

        // ── didOpen des deux fichiers réels du projet.
        val uriNote = projet.resolve("app/src/main/java/com/demo/noteapp/Note.kt").toUri().toString()
        val uriMain = projet.resolve("app/src/main/java/com/demo/noteapp/MainActivity.kt").toUri().toString()
        val texteNote = Files.readString(Path.of(java.net.URI(uriNote)))
        val texteMain = Files.readString(Path.of(java.net.URI(uriMain)))
        serveur.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(uriNote, "kotlin", 1, texteNote),
            ),
        )
        serveur.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(uriMain, "kotlin", 1, texteMain),
            ),
        )

        var echecs = mutableListOf<String>()
        fun verif(nom: String, condition: Boolean, detail: String = "") {
            if (condition) {
                println("PASS  $nom")
            } else {
                println("FAIL  $nom  $detail")
                echecs.add("$nom : $detail")
            }
        }

        // ── 1. Complétions sur le projet réel (tous les niveaux) ──────
        // 1a. Complétion MEMBRE : `it.` dans NoteRepository.chercher
        //     (membres data class : id, titre, contenu, dateCreation) —
        //     le caret vit JUSTE APRÈS le point du sélecteur.
        val offsetTitre = texteNote.indexOf("it.titre.contains")
        val completionsMembre = serveur.completion(
            org.eclipse.lsp4j.CompletionParams(
                TextDocumentIdentifier(uriNote),
                position(texteNote, offsetTitre + 3),
            ),
        ).get(15, TimeUnit.SECONDS).right
        verif(
            "completion-membre-data-class (it.)",
            completionsMembre != null && completionsMembre.items.any { it.label.startsWith("titre") },
            "items=${completionsMembre?.items?.size ?: 0}, extraits=${completionsMembre?.items?.take(10)?.map { it.label }}",
        )

        // 1b. Complétion MEMBRE Compose : `modifier.` (padding, fillMaxWidth…)
        val offsetModifier = texteNote.indexOf("modifier.padding")
        val completionsModifier = serveur.completion(
            org.eclipse.lsp4j.CompletionParams(
                TextDocumentIdentifier(uriNote),
                position(texteNote, offsetModifier + 9),
            ),
        ).get(15, TimeUnit.SECONDS).right
        verif(
            "completion-membre-compose (modifier.)",
            completionsModifier != null && completionsModifier.items.any { it.label.startsWith("padding") },
            "items=${completionsModifier?.items?.size ?: 0}, extraits=${completionsModifier?.items?.take(10)?.map { it.label }}",
        )

        // 1c. Complétion RACINE dans un corps de fonction (noms simples).
        val offsetCorps = texteNote.indexOf("val notes = repository.toutes()")
        val completionsRacine = serveur.completion(
            org.eclipse.lsp4j.CompletionParams(
                TextDocumentIdentifier(uriNote),
                position(texteNote, offsetCorps + 4),
            ),
        ).get(15, TimeUnit.SECONDS).right
        verif(
            "completion-racine-corps-fonction",
            completionsRacine != null && completionsRacine.items.isNotEmpty(),
            "items=${completionsRacine?.items?.size ?: 0}",
        )

        // 1d. Complétion IMPORT : `import androidx.compose.material3.Te|`
        //     (le caret APRÈS le préfixe frappé — l'insertion utilisateur).
        val offsetImport = texteNote.indexOf("import androidx.compose.material3.Text")
        val completionsImport = serveur.completion(
            org.eclipse.lsp4j.CompletionParams(
                TextDocumentIdentifier(uriNote),
                position(texteNote, offsetImport + "import androidx.compose.material3.Te".length),
            ),
        ).get(15, TimeUnit.SECONDS).right
        verif(
            "completion-import-material3 (Text, Button…)",
            completionsImport != null && completionsImport.items.any { it.label == "Text" || it.label.startsWith("Text") },
            "items=${completionsImport?.items?.size ?: 0}, extraits=${completionsImport?.items?.take(10)?.map { it.label }}",
        )

        // ── 2. Aide à la signature : `Text(` ───────────────────────────
        val offsetTexte = texteNote.indexOf("Text(", offsetTitre) // Text( dans EcranNoteRepository
        val signatureText = serveur.signatureHelp(
            org.eclipse.lsp4j.SignatureHelpParams(
                TextDocumentIdentifier(uriNote),
                position(texteNote, offsetTexte + 5),
            ),
        ).get(15, TimeUnit.SECONDS)
        verif(
            "signature-help Text(",
            signatureText != null && signatureText.signatures.isNotEmpty(),
            "signatures=${signatureText?.signatures?.size ?: 0}, label=${signatureText?.signatures?.firstOrNull()?.label}",
        )

        // ── 3. Survol : `NoteRepository` (KDoc « Répertoire de notes ») ─
        val offsetSurvol = texteNote.indexOf("class NoteRepository")
        val survolRepo = serveur.hover(
            org.eclipse.lsp4j.HoverParams(
                TextDocumentIdentifier(uriNote),
                position(texteNote, offsetSurvol + 7),
            ),
        ).get(15, TimeUnit.SECONDS)
        verif(
            "hover NoteRepository (KDoc)",
            survolRepo != null && (survolRepo.contents?.toString()?.contains("Répertoire") ?: false),
            "contenu=${survolRepo?.contents?.toString()?.take(80)}",
        )

        // 3b. Survol CLASSPATH : `Text` importé de material3 (la signature
        //     de la fonction top-level du classpath, paquet déclarant).
        val offsetTexteSurvol = texteNote.indexOf("Text(")
        val survolText = serveur.hover(
            org.eclipse.lsp4j.HoverParams(
                TextDocumentIdentifier(uriNote),
                position(texteNote, offsetTexteSurvol + 2),
            ),
        ).get(15, TimeUnit.SECONDS)
        verif(
            "hover Text (classpath material3)",
            survolText != null && (survolText.contents?.toString()?.contains("androidx.compose.material3") ?: false),
            "contenu=${survolText?.contents?.toString()?.take(120)}",
        )

        // ── 4. Diagnostics : EcranAvecErreurs contient Inexistante ────
        val pub = boucle.attendrePublication(uriNote, 30_000)
        verif(
            "diagnostics-publiés-sur-erreurs-volontaires",
            pub != null && pub.diagnostics.isNotEmpty(),
            "nb=${pub?.diagnostics?.size}, codes=${pub?.diagnostics?.map { it.code }}",
        )

        // ── 5. Définitions : projet (repository) et CLASSPATH (padding) ─
        val offsetDef = texteNote.indexOf("repository.toutes()")
        val definition = serveur.definition(
            org.eclipse.lsp4j.DefinitionParams(
                TextDocumentIdentifier(uriNote),
                position(texteNote, offsetDef + 3),
            ),
        ).get(15, TimeUnit.SECONDS)
        val locDef = definition?.left
        verif(
            "definition repository→NoteRepository",
            locDef != null && locDef.isNotEmpty() && locDef.first().uri.endsWith("Note.kt"),
            "locs=${locDef?.size}, uri=${locDef?.firstOrNull()?.uri}",
        )

        // 5b. Définition CLASSPATH : `padding` (extension importée de
        //     foundation-layout) → le JAR PORTEUR (comportement IDE :
        //     ouvrir la bibliothèque).
        val offsetPadding = texteNote.indexOf("modifier.padding")
        val definitionPadding = serveur.definition(
            org.eclipse.lsp4j.DefinitionParams(
                TextDocumentIdentifier(uriNote),
                position(texteNote, offsetPadding + 11),
            ),
        ).get(15, TimeUnit.SECONDS)
        val locPadding = definitionPadding?.left
        verif(
            "definition padding→jar classpath",
            locPadding != null && locPadding.isNotEmpty() && locPadding.first().uri.startsWith("jar:file:"),
            "locs=${locPadding?.size}, uri=${locPadding?.firstOrNull()?.uri}",
        )

        // ── 6. Références : Note (usages dans le projet) ───────────────
        val offsetRefs = texteNote.indexOf("data class Note(")
        val references = serveur.references(
            org.eclipse.lsp4j.ReferenceParams(
                org.eclipse.lsp4j.ReferenceContext(true),
            ).apply {
                textDocument = TextDocumentIdentifier(uriNote)
                position = position(texteNote, offsetRefs + 13)
            },
        ).get(15, TimeUnit.SECONDS)
        verif(
            "references Note",
            references != null && references.isNotEmpty(),
            "nb=${references?.size}",
        )

        // ── 7. Symboles de document ────────────────────────────────────
        val symboles = serveur.documentSymbol(
            org.eclipse.lsp4j.DocumentSymbolParams(TextDocumentIdentifier(uriNote)),
        ).get(15, TimeUnit.SECONDS)
        val listeSymboles = symboles?.mapNotNull { it.right }
        verif(
            "documentSymbol Note.kt (Note, Priorite, NoteRepository…)",
            listeSymboles != null && listeSymboles.size >= 4,
            "nb=${listeSymboles?.size}, noms=${listeSymboles?.map { it.name }}",
        )

        // ── 8. Formatage ───────────────────────────────────────────────
        val formatage = serveur.formatting(
            org.eclipse.lsp4j.DocumentFormattingParams(
                TextDocumentIdentifier(uriNote),
                org.eclipse.lsp4j.FormattingOptions(),
            ),
        ).get(30, TimeUnit.SECONDS)
        verif(
            "formatting Note.kt",
            formatage != null,
            "nb éditions=${formatage?.size}",
        )

        // ── 9. didChange + republication ───────────────────────────────
        val texteNoteV2 = texteNote.replace(
            "val inconnu: Inexistante = fabrique()",
            "val inconnu: Inexistante = fabrique()\n    val autre: Int = \"x\"",
        )
        serveur.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uriNote, 2),
                listOf(TextDocumentContentChangeEvent(texteNoteV2)),
            ),
        )
        val pubV2 = boucle.attendrePublication(uriNote, 30_000)
        verif(
            "didChange→republication diagnostics",
            pubV2 != null,
            "nb=${pubV2?.diagnostics?.size}",
        )

        // ── 10. workspace/symbol ───────────────────────────────────────
        val symbolesEspace = serveur.symbol(
            org.eclipse.lsp4j.WorkspaceSymbolParams("NoteRepository"),
        ).get(15, TimeUnit.SECONDS)?.left
        verif(
            "workspaceSymbol NoteRepository",
            symbolesEspace != null && symbolesEspace.isNotEmpty(),
            "nb=${symbolesEspace?.size}",
        )

        // ── Verdict final ──────────────────────────────────────────────
        println()
        println("════════ VERDICT : ${echecs.size} échec(s) / 14 vérifications ════════")
        echecs.forEach { println("  ÉCHEC : $it") }
        assertTrue(echecs.isEmpty(), "les vérifications utilisateur ont échoué :\n${echecs.joinToString("\n")}")
    }

    /** La position LSP d'un offset dans le texte (recherche de ligne). */
    private fun position(texte: String, offset: Int): Position {
        var ligne = 0
        var debutLigne = 0
        for (i in 0 until offset.coerceAtMost(texte.length)) {
            if (texte[i] == '\n') {
                ligne++
                debutLigne = i + 1
            }
        }
        return Position(ligne, offset - debutLigne)
    }

    /** L'attente bornée de couverture d'index (miroir SocleCoherence). */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(50)
        }
        println("AVERTISSEMENT : guichet d'index muet après ${secondes}s — les complétions classpath peuvent être dégradées")
    }
}

package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.test.fail
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * REPRODUCTION des retours appareil du 01/11/2026 (CodeIDE 0.205.0,
 * moteur v1.12.0, logs codeide-logs-2026-11-01-144401.zip) : la
 * complétion « ne fonctionne pas correctement » — le journal montre un
 * SERVEUR FIGÉ : rafale de didChange (v1..v8, frappe dans
 * MainActivity.java), deux complétions demandées (caret 1099 puis 1094),
 * puis PLUS AUCUNE sortie moteur — plus de publishDiagnostics (v4..v8
 * jamais diagnostiqués), 38 timeouts de complétion, 47 de
 * documentHighlight, 29 d'inlayHint, pendant 4 minutes — le serveur ne
 * récupère JAMAIS (il est tué et reconnecté par le client à 376 s).
 *
 * L'état du document au moment du freeze (diagnostics reçus à 134,7 s) :
 * superclass NON résolue (AppCompatActivity sans jars androidx sur le
 * classpath), `textView cannot be resolved`, `Duplicate local variable
 * liaison`, `liason cannot be resolved`, `Syntax error on token "."` —
 * l'utilisateur tape un ACCÈS MEMBRE sur un récepteur non résolu, dans
 * un fichier dont la hiérarchie est cassée.
 *
 * Le montage reproduit le CHEMIN DE PRODUCTION (serveur réel + boucle
 * LSP4J authentique via [BoucleLocale], motif Repro20261030) : rafale
 * didChange/complétion/highlight/inlay entrelacées, chaque requête
 * bornée ; au premier dépassement, les piles de TOUS les fils sont
 * vidées dans le rapport (motif ChaosConcurrenceTest) — le point de
 * blocage exact doit être lisible.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Repro20261101AppareilTest {

    /** La racine des fixtures (MANUELLE — motif Fiche026/066/054). */
    private lateinit var dossier: Path

    /** La boucle locale de la suite. */
    private var boucle: BoucleLocale? = null

    /** La borne de chaque requête individuelle (le client appareil : 5 s). */
    private val borneRequeteMs = 5_000L

    // ---------------------------------------------------------------- //
    // Source — la forme du MainActivity.java des logs (≈1210 caractères)
    // ---------------------------------------------------------------- //

    /** Le source reconstruit des diagnostics appareil (sans Greeter.java,
     *  sans jars androidx — l'état du classpath au moment du freeze). */
    private val sourceInitial = """
        package com.example.monip;

        import android.app.Activity;
        import android.os.Bundle;
        import android.widget.TextView;
        import androidx.appcompat.app.AppCompatActivity;
        import androidx.fragment.app.FragmentActivity;
        import java.io.File;

        public class MainActivity extends AppCompatActivity {

            private int eieueie;

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                ActivityResultContract<I, O> contrat = null;
                registerForActivityResult(contrat, null);
                TextView textView = null;
                textView.setText("bonjour");
                TextView liaison = null;
                liaison.setText("monde");
            }
        }
    """.trimIndent() + "\n"

    // ---------------------------------------------------------------- //
    // Montage
    // ---------------------------------------------------------------- //

    /** Le jar androidx factice DUPLIQUÉ (le « same erasure » des logs).
     * Reconstruit en mémoire par OutilsFixture (reproductible CI). */
    private fun jarAndroidxDuplique(): List<Path> {
        val classes = jo.codeide.lsp.java.OutilsFixture.compilerEnMemoire(
            dossier,
            mapOf(
                "/android/app/Activity.java" to """
                    package android.app;
                    public class Activity {
                        public void setContentView(int id) { }
                        public <T extends android.view.View> T findViewById(int id) { return null; }
                    }
                """.trimIndent(),
                "/android/view/View.java" to """
                    package android.view;
                    public class View {
                        public void setTag(Object t) { }
                    }
                """.trimIndent(),
                "/android/widget/TextView.java" to """
                    package android.widget;
                    public class TextView extends android.view.View {
                        public void setText(CharSequence t) { }
                        public CharSequence getText() { return null; }
                    }
                """.trimIndent(),
                "/androidx/activity/result/ActivityResultContract.java" to """
                    package androidx.activity.result;
                    public abstract class ActivityResultContract<I, O> {
                        public abstract O creer(I entree);
                    }
                """.trimIndent(),
                "/androidx/activity/result/ActivityResultCallback.java" to """
                    package androidx.activity.result;
                    public interface ActivityResultCallback<O> {
                        void onResult(O resultat);
                    }
                """.trimIndent(),
                "/androidx/activity/ComponentActivity.java" to """
                    package androidx.activity;
                    public class ComponentActivity extends android.app.Activity {
                        public final <I, O> void registerForActivityResult(
                                androidx.activity.result.ActivityResultContract<I, O> contrat,
                                androidx.activity.result.ActivityResultCallback<O> rappel) { }
                    }
                """.trimIndent(),
                "/androidx/appcompat/app/AppCompatActivity.java" to """
                    package androidx.appcompat.app;
                    public class AppCompatActivity extends androidx.activity.ComponentActivity {
                        public void setSupportActionBar(Object bar) { }
                        public Object getSupportActionBar() { return null; }
                    }
                """.trimIndent(),
            ),
        )
        val jar = dossier.resolve("fake-androidx.jar")
        jo.codeide.lsp.java.OutilsFixture.ecrireJar(
            jar,
            classes.entries.associate { (nom, octets) -> nom to octets },
        )
        return listOf(jar, jar) // DOUBLON : la même classe vue deux fois
    }

    @BeforeAll
    fun avantTout() {
        dossier = Files.createTempDirectory("repro-20261101")
        // Le paquet DU DOSSIER est com/exemple/monip (orthographe des
        // logs : « ne correspond pas au dossier ») — déclaré com.example.
        val paquet = dossier.resolve("com/exemple/monip")
        Files.createDirectories(paquet)
        Files.writeString(paquet.resolve("MainActivity.java"), sourceInitial)

        val jdk = jo.codeide.lsp.java.navigation.JdkDeTest.home()
        org.junit.jupiter.api.Assumptions.assumeTrue(
            jdk != null,
            "JDK de test indisponible (lib/modules) — repro ignorée (gate visible)",
        )

        // Le classpath RÉEL de l'appareil : jrt (JDK bootstrap) + le jar
        // androidx DUPLIQUÉ (les diagnostics « Name clash … same erasure »
        // des logs trahissent la double présence des classes androidx).
        val jars = jarAndroidxDuplique()
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine ->
                racine?.let {
                    jo.codeide.lsp.java.MoteurJava(
                        racines = listOf(it),
                        jars = jars,
                        jdkHome = jdk,
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

    /** La position (ligne, colonne) d'un texte cherché dans une source. */
    private fun positionDe(source: String, cherche: String): Position {
        val indice = source.indexOf(cherche)
        assertTrue(indice >= 0, "« $cherche » introuvable dans la source")
        val ligne = source.substring(0, indice).count { it == '\n' }
        val debutLigne = source.lastIndexOf('\n', indice - 1) + 1
        return Position(ligne, indice - debutLigne)
    }

    /** La position JUSTE APRÈS un texte cherché (le caret de frappe). */
    private fun positionApres(source: String, cherche: String): Position {
        val base = positionDe(source, cherche)
        return Position(base.line, base.character + cherche.length)
    }

    /** Vidage des piles de tous les fils (motif ChaosConcurrenceTest). */
    private fun dumpDesPiles(contexte: String): String = buildString {
        appendLine("=== BLOCAGE : $contexte — piles de tous les fils ===")
        val piles = Thread.getAllStackTraces()
        for ((fil, elements) in piles.entries.sortedBy { it.key.name }) {
            appendLine("--- FIL ${fil.name} (état ${fil.state}) ---")
            elements.take(25).forEach { appendLine("    $it") }
        }
    }

    /** Exécute borné et rend le résultat ; au dépassement, DUMP + échec. */
    private fun <T> borne(contexte: String, future: CompletableFuture<T>): T {
        try {
            return future.get(borneRequeteMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            fail(dumpDesPiles(contexte) + "\ncause : $e")
        }
    }

    /** Variante paresseuse : la requête part à l'appel, la borne la cadre. */
    private fun <T> borne(contexte: String, calcul: () -> CompletableFuture<T>): T =
        borne(contexte, calcul())

    // ---------------------------------------------------------------- //
    // 1. Le cœur du bug : rafale de frappe + complétion membre — le
    //    serveur doit rester VIVANT (diagnostics ET réponses)
    // ---------------------------------------------------------------- //

    @Test
    fun `rafale de frappe avec completion membre ne fige pas le serveur`() {
        val b = boucle ?: fail("boucle absente")
        val uri = dossier.resolve("com/exemple/monip/MainActivity.java").toUri().toString()

        // didOpen → la première vague de diagnostics (20 sur l'appareil).
        b.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, sourceInitial)),
        )
        val premiere = b.attendrePublication(uri, 15_000) ?: fail("aucun diagnostic initial")

        // Le compteur de publications AVANT la rafale.
        val publicationsAvant = b.espion.publications.size

        // La RAFALE des logs : 8 didChange (frappe caractère par caractère
        // autour d'un accès membre sur un récepteur NON résolu), chaque
        // étape entrelacée de complétion/highlight/inlayHint — les requêtes
        // du client appareil pendant la frappe.
        val frappes = mutableListOf<String>()
        var source = sourceInitial
        // La frappe des logs : correction « liaison » → « liason » PUIS
        // troncature de l'instruction (il ne reste que « liason. » — le
        // « Syntax error on token "." » des diagnostics), puis caractère
        // par caractère : s → se → set → setT → setTe → setTex.
        frappes += source.replace("liaison.setText(\"monde\")", "liason.")
        frappes += frappes.last().replace("liason.", "liason.s")
        frappes += frappes.last().replace("liason.s", "liason.se")
        frappes += frappes.last().replace("liason.se", "liason.set")
        frappes += frappes.last().replace("liason.set", "liason.setT")
        frappes += frappes.last().replace("liason.setT", "liason.setTe")
        frappes += frappes.last().replace("liason.setTe", "liason.setTex")

        var version = 0
        for ((indice, frappee) in frappes.withIndex()) {
            version += 1
            source = frappee
            b.client.textDocumentService.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, version),
                    listOf(TextDocumentContentChangeEvent(frappee)),
                ),
            )
            // Les requêtes concurrentes de l'appareil, à CHAQUE étape de la
            // rafale (complétion au caret membre + highlight + inlay). Le
            // caret suit la frappe : juste après le mot en cours.
            val ancreMembre = "liason."
            val suite = frappee.substring(frappee.indexOf(ancreMembre) + ancreMembre.length)
            val extra = suite.takeWhile { it.isJavaIdentifierPart() }.length
            val base = positionDe(frappee, ancreMembre)
            val caret = Position(base.line, base.character + ancreMembre.length + extra)
            val completion = CompletionParams()
            completion.textDocument = TextDocumentIdentifier(uri)
            completion.position = caret
            val futureCompletion = b.client.textDocumentService.completion(completion)

            val highlight = DocumentHighlightParams()
            highlight.textDocument = TextDocumentIdentifier(uri)
            highlight.position = positionDe(frappee, "textView")
            val futureHighlight = b.client.textDocumentService.documentHighlight(highlight)

            val inlay = InlayHintParams()
            inlay.textDocument = TextDocumentIdentifier(uri)
            inlay.range = Range(Position(0, 0), Position(30, 0))
            val futureInlay = b.client.textDocumentService.inlayHint(inlay)

            // Chaque requête doit revenir dans la borne — sinon DUMP.
            borne("complétion (rafale v$version, étape $indice)", futureCompletion)
            borne("documentHighlight (rafale v$version, étape $indice)", futureHighlight)
            borne("inlayHint (rafale v$version, étape $indice)", futureInlay)
        }

        // Le VIVANT : après la rafale, les diagnostics CONTINUENT de
        // couler (sur l'appareil : plus RIEN après v3 — c'est le bug).
        Thread.sleep(1_000) // laisse le debounce tirer
        b.client.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version + 1),
                listOf(TextDocumentContentChangeEvent(source + "\n")),
            ),
        )
        val diagnosticApres = run {
            val fin = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(15_000)
            while (System.nanoTime() < fin) {
                val derniere = b.espion.publications.lastOrNull { it.uri == uri }
                if (derniere != null && (derniere.version ?: 0) >= version + 1) return@run derniere
                Thread.sleep(100)
            }
            null
        }
        assertTrue(
            diagnosticApres != null,
            "les diagnostics coulent encore après la rafale (v${version + 1}) — " +
                "sur l'appareil, plus AUCUN publishDiagnostics après la frappe",
        )
    }

    // ---------------------------------------------------------------- //
    // 2. La séquence EXACTE du figage session 2 : complétion ×2 à la
    //    MÊME ancre (invocation 2 V-C3) avec didChange + diagnose
    //    concurrente entre les deux (logs 448.61 → 450.06)
    // ---------------------------------------------------------------- //

    @Test
    fun `deuxieme invocation a la meme ancre avec diagnose concurrente ne figure pas`() {
        val b = boucle ?: fail("boucle absente")
        val uri = dossier.resolve("com/exemple/monip/Rafale.java").toUri().toString()

        // Le fichier de la session 2 (~760 ch, l'utilisateur tape dans
        // une méthode, caret=190 sur un accès membre en cours).
        val source = """
            package com.exemple.monip;

            import android.widget.TextView;
            import androidx.appcompat.app.AppCompatActivity;

            public class Rafale extends AppCompatActivity {

                private int compteur = 0;

                public void go() {
                    TextView textView = findViewById(0);
                    textView.
                    compteur++;
                }
            }
        """.trimIndent() + "\n"
        b.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, source)),
        )
        b.attendrePublication(uri, 15_000) ?: fail("aucun diagnostic initial")

        val ancre = "textView."
        // 1re invocation (448.61 dans les logs).
        val r1 = borne("1re invocation (Rafale)") {
            b.client.textDocumentService.completion(paramsCompletion(uri, source, ancre))
        }
        println("[REPRO] Rafale 1re invocation → ${r1.right?.items?.size ?: r1.left?.size ?: 0} item(s)")

        // didChange v21/v22 : la frappe AILLEURS (compteur++ → compteur+=1)
        // pendant que la diagnose du publicateur tire (~150 ms après).
        val sourceV2 = source.replace("compteur++;", "compteur += 1;")
        b.client.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, 1),
                listOf(TextDocumentContentChangeEvent(sourceV2)),
            ),
        )
        // LAISSE la diagnose tirer (debounce 50 ms + compilation) — la
        // course des logs : le tir de v21 croise la 2e complétion.
        Thread.sleep(120)

        // 2e invocation à la MÊME ancre, MÊME préfixe (450.06) — la clé
        // C-08 D4 est identique : invocation 2, le chemin ÉLARGI.
        val r2 = borne("2e invocation MÊME ancre (le figage des logs)") {
            b.client.textDocumentService.completion(paramsCompletion(uri, sourceV2, ancre))
        }
        println("[REPRO] Rafale 2e invocation → ${r2.right?.items?.size ?: r2.left?.size ?: 0} item(s)")

        // 3e invocation (le projet, borne suivante du contrat).
        val r3 = borne("3e invocation (Rafale)") {
            b.client.textDocumentService.completion(paramsCompletion(uri, sourceV2, ancre))
        }
        println("[REPRO] Rafale 3e invocation → ${r3.right?.items?.size ?: r3.left?.size ?: 0} item(s)")

        // Le VIVANT : la diagnose de v1 DOIT avoir publié (sur l'appareil,
        // plus aucun publishDiagnostics après ce point — c'est le bug).
        val diagnostiqueV1 = run {
            val fin = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10_000)
            while (System.nanoTime() < fin) {
                val derniere = b.espion.publications.lastOrNull { it.uri == uri }
                if (derniere != null && (derniere.version ?: 0) >= 1) return@run derniere
                Thread.sleep(100)
            }
            null
        }
        assertTrue(
            diagnostiqueV1 != null,
            "la diagnose de v1 a publié (v1) — sur l'appareil, le publicateur " +
                "était figé derrière la complétion : plus AUCUN diagnostic",
        )
    }

    /** Les paramètres d'une complétion juste après une ancre. */
    private fun paramsCompletion(uri: String, source: String, ancre: String): CompletionParams {
        val params = CompletionParams()
        params.textDocument = TextDocumentIdentifier(uri)
        params.position = positionApres(source, ancre)
        return params
    }

    // ---------------------------------------------------------------- //
    // 3. La complétion membre SEULE sur récepteur non résolu — isolée
    // ---------------------------------------------------------------- //

    @Test
    fun `completion membre sur recepteur non resolu repond honnetement`() {
        val b = boucle ?: fail("boucle absente")
        val uri = dossier.resolve("com/exemple/monip/Recepteur.java").toUri().toString()
        val source = """
            package com.exemple.monip;

            public class Recepteur extends Inconnue {
                void go() {
                    textView.
                }
            }
        """.trimIndent() + "\n"
        b.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, source)),
        )
        val params = CompletionParams()
        params.textDocument = TextDocumentIdentifier(uri)
        params.position = positionApres(source, "textView.")
        val reponse = borne("complétion membre isolée", b.client.textDocumentService.completion(params))
        // Le contrat honnête : une liste (possiblement vide) — JAMAIS un
        // figage. On consigne la taille.
        println("[REPRO] complétion membre sur récepteur non résolu → ${reponse.right?.items?.size ?: reponse.left?.size ?: 0} item(s)")
    }
}

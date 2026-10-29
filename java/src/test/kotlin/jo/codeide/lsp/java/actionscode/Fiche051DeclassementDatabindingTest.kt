package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Severite
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fiche 051 — DatabindingImportDowngradeTest : repro du constat production
 * v0.107.0 — « The import com.example.monip.databinding cannot be
 * resolved » en ERROR sec sur un projet Data Binding sans build préalable.
 *
 * Les classes `XxxBinding` ne sont produites par AGP qu'AU BUILD (la sync
 * ne les génère pas) : avant tout build, l'import est STRUCTURELLEMENT non
 * résoluble — l'ERROR est exacte mais incurable côté utilisateur. Le
 * correctif verrouillé : au package Data Binding, le MÊME downgrade honnête
 * que la classe R — ERROR → HINT avec remède (« lance un Build ») — avec
 * COUPURE honnête : dès que la première classe générée existe, une faute
 * de frappe redevient une ERROR intégrale.
 *
 * Miroir moteur de la publication LSP : le downgrade vit dans la
 * PUBLICATION ([MoteurJava.declasserDatabinding] — racines sources + jars
 * du moteur), PAS dans le diagnose (le moteur publie sans compromis). Le
 * cycle de vie LSP4J complet (serveur in-process + espion
 * `publishDiagnostics`, polling 15 s) relève de la couche hôte N8 — la
 * réécriture requalifie le montage « serveur complet » en transformation
 * pure de la liste publiée : même contrat, déterministe, exécutable
 * partout.
 *
 * Gate requalifiée : l'hypothèse android.jar du montage vendored était un
 * artefact du serveur — le moteur n'a besoin que de l'image jrt. Chaque
 * comportement vit sur SON projet (l'état « peuplé » du paquet
 * databinding est LE paramètre du banc — jamais partagé entre tests).
 */
class Fiche051DeclassementDatabindingTest {

    private companion object {

        const val ID_IMPORT_NOT_FOUND = 268435846L

        /** Un module monté pour le banc : projet + MainActivity important [import]. */
        class Module(val projet: Path, val moteur: MoteurJava) {
            fun publier(): List<Diagnostic> {
                val chemin = "/com/example/e/MainActivity.java"
                val source = projet.resolve("com/example/e/MainActivity.java").toFile().readText()
                val diagnostics = moteur.diagnose(chemin, source)
                return moteur.declasserDatabinding(diagnostics)
            }

            fun fermer() {
                moteur.dispose()
            }
        }

        /** Monte le module : com.example.e peuplé, databinding peuplé ou non. */
        fun monter(import: String, databindingPeuple: Boolean): Module {
            val projet = Files.createTempDirectory("databinding-051")
            if (databindingPeuple) {
                Files.createDirectories(projet.resolve("com/example/e/databinding"))
                    .resolve("MainActivityBinding.java")
                    .writeText(
                        """
                        package com.example.e.databinding;

                        public final class MainActivityBinding {
                        }
                        """.trimIndent(),
                    )
            }
            Files.createDirectories(projet.resolve("com/example/e"))
                .resolve("MainActivity.java")
                .writeText(
                    """
                    package com.example.e;

                    import $import;

                    public class MainActivity {
                        public void voir() {
                            Object b = null;
                            System.out.println(b);
                        }
                    }
                    """.trimIndent(),
                )
            val moteur = MoteurJava(racines = listOf(projet), jars = emptyList(), compliance = "17")
            assumeTrue(moteur.isLikelyUsable(), "image jrt indisponible — déclassement ignoré")
            return Module(projet, moteur)
        }

        /** Le diagnostic d'import non résolu (le seul qui porte le downgrade). */
        fun diagnosticImport(diagnostics: List<Diagnostic>): Diagnostic? =
            diagnostics.firstOrNull { it.code == ID_IMPORT_NOT_FOUND.toString() }
    }

    // ---- 1. importDatabindingSansBuildEstHintAvecRemede --------------- //

    @Test
    fun `importDatabindingSansBuildEstHintAvecRemede`() {
        val module = monter("com.example.e.databinding.MainActivityBinding", databindingPeuple = false)
        try {
            val import = assertNotNull(diagnosticImport(module.publier()), "le diagnostic d'import non résolu est publié")
            assertEquals(
                Severite.INFO,
                import.severite,
                "sans build : le diagnostic de l'import est déclassé en HINT (INFO moteur)",
            )
            assertTrue(
                import.message.contains("Data Binding"),
                "le message EXPLIQUE le remède (contient « Data Binding »)",
            )
            assertTrue(
                import.message.contains("Build"),
                "le message nomme l'action (contient « Build » — classes générées par AGP à la construction)",
            )
        } finally {
            module.fermer()
        }
    }

    // ---- 2. fauteDeFrappeDatabindingAvecGeneresResteError -------------- //

    @Test
    fun `fauteDeFrappeDatabindingAvecGeneresResteError`() {
        // Situation POST-BUILD : le paquet databinding est PEUPLÉ, la classe
        // importée est une FAUTE DE FRAPPE (inexistante).
        val module = monter("com.example.e.databinding.PasLaBinding", databindingPeuple = true)
        try {
            val import = assertNotNull(diagnosticImport(module.publier()), "le diagnostic d'import non résolu est publié")
            assertEquals(
                Severite.ERREUR,
                import.severite,
                "coupure honnête : le downgrade ne masque JAMAIS une vraie erreur post-build",
            )
        } finally {
            module.fermer()
        }
    }

    // ---- 3. paquetHomonymeNeDeclenchePasLeDowngrade ------------------- //

    @Test
    fun `paquetHomonymeNeDeclenchePasLeDowngrade`() {
        // Paquet homonyme SANS être un segment « .databinding » (garde du
        // segment : des points des deux côtés).
        val module = monter("com.example.e.mydatabinding.Machin", databindingPeuple = false)
        try {
            val import = assertNotNull(diagnosticImport(module.publier()), "le diagnostic d'import non résolu est publié")
            assertEquals(
                Severite.ERREUR,
                import.severite,
                "garde du segment : seul un VRAI segment « .databinding » déclenche le downgrade",
            )
        } finally {
            module.fermer()
        }
    }
}

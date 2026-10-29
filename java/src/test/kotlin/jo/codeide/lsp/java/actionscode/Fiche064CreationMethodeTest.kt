package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.MoteurJava
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fiche 064 — CreateMethodFixTest : le quick-fix « Créer la méthode »
 * (famille UNDEFINED_METHOD) — stub `private [static]` inséré APRÈS la
 * méthode englobante, types des paramètres déduits des ARGUMENTS
 * STRUCTURÉS du diagnostic ecj, retour `void` (appel-instruction) ou
 * `Object` (appel-expression), corps en `UnsupportedOperationException`.
 *
 * Les diagnostics viennent du VRAI moteur (diagnose) — plage et arguments
 * réels, pas synthétiques ; le premier comportement RE-DIAGNOSE le texte
 * corrigé et exige la disparition de l'erreur (« 0 UndefinedMethod après
 * correction » — critère de sortie de l'étage N6).
 *
 * Gate requalifiée (note de réécriture fiche 063 appliquée au montage de
 * la fiche 064) : l'hypothèse android.jar de `@BeforeAll` était un artefact
 * du montage vendored — la réécriture n'a besoin que de l'image jrt du JDK
 * (UndefinedMethod ne consulte pas android.jar) ; le banc s'exécute partout
 * où le moteur est utilisable.
 */
class Fiche064CreationMethodeTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        lateinit var PROJET: Path

        /** L'ID ecj d'UndefinedMethod — le seul qui porte le correcteur. */
        const val ID_UNDEFINED_METHOD = 67108964L

        val SOURCE_MAIN = """
            package demo;

            public class Main {
                void run() {
                    compute(1, "x");
                }
            }
        """.trimIndent()

        val SOURCE_ASSIGN = """
            package demo;

            public class Assign {
                void run() {
                    int r = compute(1);
                    System.out.println(r);
                }
            }
        """.trimIndent()

        val SOURCE_STAT = """
            package demo;

            public class Stat {
                static void s() {
                    compute(1);
                }
            }
        """.trimIndent()

        val SOURCE_OTHER = """
            package demo;

            public class Other {
                void run() {
                    "abc".noSuchMethod(1);
                }
            }
        """.trimIndent()

        val SOURCE_FQN = """
            package demo;

            public class Fqn {
                void run(java.util.List<String> items) {
                    process(items);
                }
            }
        """.trimIndent()

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            PROJET = Files.createTempDirectory("creation-methode-064")
            val dossier = Files.createDirectories(PROJET.resolve("demo"))
            dossier.resolve("Main.java").writeText(SOURCE_MAIN)
            dossier.resolve("Assign.java").writeText(SOURCE_ASSIGN)
            dossier.resolve("Stat.java").writeText(SOURCE_STAT)
            dossier.resolve("Other.java").writeText(SOURCE_OTHER)
            dossier.resolve("Fqn.java").writeText(SOURCE_FQN)
            MOTEUR = MoteurJava(racines = listOf(PROJET), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — création de méthode ignorée")
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            if (this::MOTEUR.isInitialized) MOTEUR.dispose()
        }

        /** Le diagnostic UndefinedMethod RÉEL du moteur sur une source. */
        fun undefinedMethod(chemin: String, source: String) =
            MOTEUR.diagnose(chemin, source).firstOrNull { it.code == ID_UNDEFINED_METHOD.toString() }

        /** Les actions du service pour TOUS les UndefinedMethod de la source. */
        fun creations(chemin: String, source: String): List<ActionCode> {
            val service = ServiceActionsCode(MOTEUR)
            return MOTEUR.diagnose(chemin, source)
                .filter { it.code == ID_UNDEFINED_METHOD.toString() }
                .flatMap { service.actions(chemin, source, it) }
                .filter { it.kind == "quickfix.create.method" }
        }
    }

    // ---- 1. statementCallCreatesVoidPrivateMethod ----------------------- //

    @Test
    fun `statementCallCreatesVoidPrivateMethod`() {
        val chemin = "/demo/Main.java"
        val diagnostic = undefinedMethod(chemin, SOURCE_MAIN)
        assertNotNull(diagnostic, "le diagnostic UndefinedMethod réel est présent")
        val creations = creations(chemin, SOURCE_MAIN)
        assertEquals(1, creations.size, "une action quickfix.create.method")
        val corrige = creations[0].apercu(SOURCE_MAIN)

        // Le stub : private void, paramètres arg0/arg1 typés depuis les
        // arguments structurés du diagnostic.
        assertTrue(
            corrige.contains("private void compute(int arg0, String arg1) {"),
            "le stub inséré est « private void compute(int arg0, String arg1) { »",
        )
        assertTrue(
            corrige.contains("throw new UnsupportedOperationException(\"TODO: compute\");"),
            "le corps imposé est « throw new UnsupportedOperationException(\"TODO: compute\"); »",
        )
        // L'insertion se fait APRÈS la méthode englobante run().
        val fermetureRun = corrige.indexOf("}", corrige.indexOf("void run()"))
        assertTrue(
            corrige.indexOf("private void compute") > fermetureRun,
            "le stub est inséré APRÈS la méthode englobante run()",
        )
        // La boucle est bouclée : le texte corrigé re-diagnostiqué ne
        // contient PLUS AUCUN UndefinedMethod (compte exact).
        val apres = MOTEUR.diagnose(chemin, corrige)
        assertEquals(
            0,
            apres.count { it.code == ID_UNDEFINED_METHOD.toString() },
            "0 UndefinedMethod après correction (borne de la fiche)",
        )
    }

    // ---- 2. assignmentCallCreatesObjectReturningMethod ------------------- //

    @Test
    fun `assignmentCallCreatesObjectReturningMethod`() {
        val chemin = "/demo/Assign.java"
        val diagnostic = undefinedMethod(chemin, SOURCE_ASSIGN)
        assertNotNull(diagnostic, "le diagnostic UndefinedMethod réel est présent")
        val creations = creations(chemin, SOURCE_ASSIGN)
        assertEquals(1, creations.size, "une action quickfix.create.method")
        val corrige = creations[0].apercu(SOURCE_ASSIGN)
        assertTrue(
            corrige.contains("private Object compute(int arg0) {"),
            "retour Object quand la valeur de l'appel est utilisée (affectation)",
        )
    }

    // ---- 3. staticCallSiteCreatesStaticMethod ---------------------------- //

    @Test
    fun `staticCallSiteCreatesStaticMethod`() {
        val chemin = "/demo/Stat.java"
        val diagnostic = undefinedMethod(chemin, SOURCE_STAT)
        assertNotNull(diagnostic, "le diagnostic UndefinedMethod réel est présent")
        val creations = creations(chemin, SOURCE_STAT)
        assertEquals(1, creations.size, "une action quickfix.create.method")
        val corrige = creations[0].apercu(SOURCE_STAT)
        assertTrue(
            corrige.contains("private static void compute(int arg0) {"),
            "le « static » suit le contexte du site d'appel",
        )
    }

    // ---- 4. qualifiedCallOnOtherTypeNotFixed ----------------------------- //

    @Test
    fun `qualifiedCallOnOtherTypeNotFixed`() {
        val chemin = "/demo/Other.java"
        // Le test ne vérifie le fix que si le diagnostic est présent
        // (vérification conditionnelle explicite de la fiche).
        val diagnostic = undefinedMethod(chemin, SOURCE_OTHER) ?: return
        val service = ServiceActionsCode(MOTEUR)
        val actions = service.actions(chemin, SOURCE_OTHER, diagnostic)
        assertEquals(
            0,
            actions.count { it.kind == "quickfix.create.method" },
            "jamais de création de méthode pour un appel qualifié sur un autre type",
        )
    }

    // ---- 5. fqnParamsShortenedWithImportEdit ----------------------------- //

    @Test
    fun `fqnParamsShortenedWithImportEdit`() {
        val chemin = "/demo/Fqn.java"
        val diagnostic = undefinedMethod(chemin, SOURCE_FQN)
        assertNotNull(diagnostic, "le diagnostic UndefinedMethod réel est présent")
        val creations = creations(chemin, SOURCE_FQN)
        assertEquals(1, creations.size, "une action quickfix.create.method")
        val corrige = creations[0].apercu(SOURCE_FQN)
        // Le stub utilise la forme RACCOURCIE.
        assertTrue(
            corrige.contains("process(List<String> arg0)"),
            "le stub utilise la forme raccourcie « process(List<String> arg0) »",
        )
        // Une édition SÉPARÉE ajoute l'import du type raccourci.
        assertTrue(
            creations[0].editions.any { it.texte == "import java.util.List;\n" },
            "une édition séparée ajoute « import java.util.List; »",
        )
        assertTrue(corrige.contains("import java.util.List;"), "l'import est bien posé dans le texte corrigé")
    }
}

package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.outils.appliquerEditions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vague V-D — FAMILLES ECJ ÉTENDUES (ADR 0027 (a) : « étendre la table
 * ID→famille aux IDs ecj à fix réel ») : stubs des méthodes abstraites
 * manquantes, corps de méthode manquant, retour de valeur en void,
 * import en conflit, cast redondant (signal de l'inspection V-C1).
 *
 * Critères (ADR 0027 / P5) : chaque fix appliqué → le diagnostic CIBLE
 * disparaît, zéro NOUVELLE erreur, texte COMPILABLE (fixtures par
 * ailleurs valides).
 */
class FamillesEtenduesTest {

    /** Le moteur et le chemin d'une fixture écrite sur disque. */
    private fun moteurAvec(source: String): Pair<MoteurJava, String> {
        val racine = Files.createTempDirectory("vd-familles")
        Files.createDirectories(racine.resolve("demo"))
        racine.resolve("demo").resolve("Main.java").writeText(source)
        return MoteurJava(racines = listOf(racine)) to "/demo/Main.java"
    }

    /** Le cycle P5 complet d'une fixture : diagnostiquer → actions → appliquer → re-diagnostiquer. */
    private fun cycle(source: String): Triple<String, List<jo.codeide.lsp.java.diagnostics.Diagnostic>, List<String>> {
        val (moteur, chemin) = moteurAvec(source)
        val avant = moteur.diagnose(chemin, source)
        val actions = moteur.actionsCode(chemin, source, avant)
        val rapports = mutableListOf<String>()
        for (action in actions) {
            val apres = appliquerEditions(source, action.editions)
            val temoin = Files.createTempDirectory("vd-verifie")
            Files.createDirectories(temoin.resolve("demo"))
            temoin.resolve("demo").resolve("Main.java").writeText(apres)
            val erreursApres = MoteurJava(racines = listOf(temoin)).diagnose("/demo/Main.java", apres)
                .filter { it.severite == Severite.ERREUR }
            val cible = action.diagnostic
            val cibleDisparue = cible == null || erreursApres.none {
                it.code == cible.code && it.message == cible.message
            } && (cible == null || run {
                // la cible peut être une INSPECTION (INFO) : re-vérifier par code+message
                val temoinToutes = MoteurJava(racines = listOf(temoin)).diagnose("/demo/Main.java", apres)
                temoinToutes.none { it.code == cible.code && it.message == cible.message }
            })
            if (!cibleDisparue) rapports.add("« ${action.titre} » : la cible survit")
            if (erreursApres.isNotEmpty()) rapports.add("« ${action.titre} » : ${erreursApres.size} erreur(s) : ${erreursApres.map { it.message }}")
        }
        return Triple(source, avant, rapports)
    }

    @Test
    fun `les stubs implémentent la methode abstraite manquante (cycle P5)`() {
        val (_, avant, rapports) = cycle(
            """
            package demo;
            public class Main implements Runnable {
            }
            """.trimIndent(),
        )
        assertTrue(avant.any { it.code == "67109264" }, "AbstractMethodMustBeImplemented émis : ${avant.map { it.code }}")
        assertEquals(0, rapports.size, "cycle propre : $rapports")
    }

    @Test
    fun `le corps manquant s'insere et compile (cycle P5)`() {
        val (_, avant, rapports) = cycle(
            """
            package demo;
            public class Main {
                void go();
            }
            """.trimIndent(),
        )
        assertTrue(avant.any { it.code == "603979883" }, "MethodRequiresBody émis")
        assertEquals(0, rapports.size, "cycle propre : $rapports")
    }

    @Test
    fun `le retour de valeur en void disparait (cycle P5)`() {
        val (_, avant, rapports) = cycle(
            """
            package demo;
            public class Main {
                void go() { return 5; }
            }
            """.trimIndent(),
        )
        assertTrue(avant.any { it.code == "67108969" }, "VoidMethodReturnsValue émis")
        assertEquals(0, rapports.size, "cycle propre : $rapports")
    }

    @Test
    fun `l'import en conflit se supprime (cycle P5)`() {
        val (_, avant, rapports) = cycle(
            """
            package demo;
            import java.util.List;
            import java.awt.List;
            public class Main {
                List<String> noms;
            }
            """.trimIndent(),
        )
        assertTrue(avant.any { it.code == "268435842" }, "DuplicateImport (collides) émis : ${avant.map { it.code }}")
        assertEquals(0, rapports.size, "cycle propre : $rapports")
    }

    @Test
    fun `le cast redondant de l'inspection se retire (cycle P5)`() {
        val (_, avant, rapports) = cycle(
            """
            package demo;
            public class Main {
                String go() { return (String) "x"; }
            }
            """.trimIndent(),
        )
        assertTrue(avant.any { it.code == "RedundantCast" }, "l'inspection V-C1 signale le cast : ${avant.map { it.code }}")
        assertEquals(0, rapports.size, "cycle propre : $rapports")
    }

    @Test
    fun `la table etendue mappe les nouveaux IDs (fiche 065 etendue)`() {
        // Extraits du jar ecj 3.40.0 par lecture du format de classe (javap) —
        // jamais codés « de mémoire » (règle du dépôt).
        assertEquals(jo.codeide.lsp.java.actionscode.FamilleProbleme.METHODES_ABSTRAITES, jo.codeide.lsp.java.actionscode.FamilleProbleme.fromId(67109264L))
        assertEquals(jo.codeide.lsp.java.actionscode.FamilleProbleme.METHODES_ABSTRAITES, jo.codeide.lsp.java.actionscode.FamilleProbleme.fromId(67109282L))
        assertEquals(jo.codeide.lsp.java.actionscode.FamilleProbleme.METHODES_ABSTRAITES, jo.codeide.lsp.java.actionscode.FamilleProbleme.fromId(67109622L))
        assertEquals(jo.codeide.lsp.java.actionscode.FamilleProbleme.CORPS_MANQUANT, jo.codeide.lsp.java.actionscode.FamilleProbleme.fromId(603979883L))
        assertEquals(jo.codeide.lsp.java.actionscode.FamilleProbleme.RETOUR_VOID, jo.codeide.lsp.java.actionscode.FamilleProbleme.fromId(67108969L))
        assertEquals(jo.codeide.lsp.java.actionscode.FamilleProbleme.IMPORT_CONFLIT, jo.codeide.lsp.java.actionscode.FamilleProbleme.fromId(268435841L))
        assertEquals(jo.codeide.lsp.java.actionscode.FamilleProbleme.IMPORT_CONFLIT, jo.codeide.lsp.java.actionscode.FamilleProbleme.fromId(268435842L))
        // L'alias NOMMÉ de l'inspection V-C1 (code non numérique).
        assertEquals(jo.codeide.lsp.java.actionscode.FamilleProbleme.CAST_INUTILE, jo.codeide.lsp.java.actionscode.FamilleProbleme.fromCode("RedundantCast"))
        // La priorité de l'ID numérique reste INTACTE (fiche 065).
        assertEquals(jo.codeide.lsp.java.actionscode.FamilleProbleme.UNRESOLVED_REFERENCE, jo.codeide.lsp.java.actionscode.FamilleProbleme.fromCode("16777218"))
    }
}

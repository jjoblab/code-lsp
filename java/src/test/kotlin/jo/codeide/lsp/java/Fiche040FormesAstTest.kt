package jo.codeide.lsp.java

import jo.codeide.lsp.java.interne.LectureAst
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 040 — ScratchAstShapesTest : banc d'étude (dump empirique, AUCUNE
 * assertion sur le contenu) des formes AST consommées par le moteur —
 * lambdas, chaînages d'appels, allocations. La traversée RÉSILIENTE ne doit
 * jamais planter sur un AST en recovery.
 */
class Fiche040FormesAstTest {

    private val source = """
        import java.util.function.BiConsumer;
        import java.util.List;

        public class Main {
            private BiConsumer<Integer, Integer> fn = (x, y) -> { int z = x + y; };
            private List<String> items;

            public void run(List<String> items, BiConsumer<String, Integer> f) {
                items.forEach(s -> f.accept(s, 1));
            }

            public StringBuilder make() {
                return new StringBuilder();
            }

            public void demo() {
                BiConsumer<String, Integer> typer = (String a, Integer b) -> { };
                String chaine = make().append("a").append("b").toString();
                new Main().run(items, fn);
                new Object(1, "x");
            }
        }
    """.trimIndent()

    @Test
    fun dump() {
        val moteur = MoteurJava(compliance = "17")
        val unite = moteur.resolveUnit("/demo/Main.java", source, false)
        assertNotNull(unite, "resolveUnit rend une unité compilée (avec scope)")
        val lignes = LectureAst.dumpFormes(unite.arbre)
        // Impressions empiriques (aucune assertion) — journal du banc.
        for (ligne in lignes) {
            println(ligne)
        }
        // Propriété conservée : la traversée résiliente ne lève JAMAIS, même
        // avec l'allocation volontairement invalide « new Object(1, "x") ».
        assertTrue(lignes.any { it.startsWith("[LAMBDA]") }, "des lambdas sont observées")
        assertTrue(lignes.any { it.startsWith("[SEND]") }, "des envois sont observés")
        assertTrue(lignes.any { it.startsWith("[ALLOC]") }, "des allocations sont observées")
    }
}

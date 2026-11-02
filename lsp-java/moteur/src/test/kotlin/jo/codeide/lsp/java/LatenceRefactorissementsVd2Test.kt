package jo.codeide.lsp.java

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vague V-D TRANCHE 2 — TRANSCRIPT DE LATENCE de la liste `codeAction`
 * AVEC les refactorings lourds (critère de sortie du plan 04 : liste
 * p95 < 30 ms JVM — la CI reste la référence ; R5 : aucune valeur
 * fabriquée, les chiffres ci-dessous sont MESURÉS au banc du bac à
 * sable).
 *
 * Le profil mesuré : la requête intentions COMPLÈTE (tranche 1 :
 * intentions légères + générateurs différés ; V-D2 : extractions sur
 * SÉLECTION + inline + safe delete au caret — la liste ne porte que des
 * clés + positions, la garde des usages du safe delete vit au resolve,
 * ADR 0027 (f)) sur les positions de déclenchement ciblées des deux
 * régimes (sélection et caret).
 */
class LatenceRefactorissementsVd2Test {

    @Test
    fun `transcript de latence de la liste codeAction avec refactorings (p95 cible sous 30 ms JVM)`() {
        val source = """
            package demo;
            public class Main {
                private static final int MAX = 100;
                private int base = 1;

                public int plafond(int valeur) {
                    int total = valeur + base;
                    total = total * 2;
                    return Math.min(total, MAX);
                }

                private int obsolete() {
                    return base;
                }

                void saluer() {
                    String salut = "bonjour";
                    int compte = salut.length();
                    System.out.println(salut + compte);
                }
            }
        """.trimIndent()
        val racine = Files.createTempDirectory("vd2-latence")
        Files.createDirectories(racine.resolve("demo"))
        racine.resolve("demo").resolve("Main.java").writeText(source)
        val moteur = MoteurJava(racines = listOf(racine))
        val chemin = "demo/Main.java"

        // Les positions CIBLÉES des deux régimes : SÉLECTIONS (extractions :
        // expression d'initialisateur, expression d'affectation, plage
        // d'instructions, littéral de constante) et CARETS (inline locale,
        // inline constante, safe delete méthode/champ).
        val selectionExpression = source.indexOf("valeur + base")
        val finSelectionExpression = selectionExpression + "valeur + base".length
        val selectionAffectation = source.indexOf("total * 2")
        val finSelectionAffectation = selectionAffectation + "total * 2".length
        val selectionInstructions = source.indexOf("total = valeur + base;")
        val finSelectionInstructions = source.indexOf("total * 2;") + "total * 2;".length
        val selectionLitteral = source.indexOf("100;")
        val finSelectionLitteral = selectionLitteral + "100".length
        val positions: List<Pair<Int, Int>> = listOf(
            selectionExpression to finSelectionExpression,
            selectionAffectation to finSelectionAffectation,
            selectionInstructions to finSelectionInstructions,
            selectionLitteral to finSelectionLitteral,
            source.indexOf("salut =") to source.indexOf("salut ="),
            source.indexOf("MAX =") to source.indexOf("MAX ="),
            source.indexOf("obsolete()") to source.indexOf("obsolete()"),
            source.indexOf("base =") to source.indexOf("base ="),
        )

        // Échauffement (le cache de parse et le mémo se remplissent) — et
        // preuve que les actions sont bien SERVIES sur les positions visées.
        var extractions = 0
        var inlines = 0
        var suppressions = 0
        for ((debut, fin) in positions) {
            val liste = moteur.intentions(chemin, source, debut, fin)
            extractions += liste.count { it.kind.startsWith("refactor.extract") }
            inlines += liste.count { it.kind.startsWith("refactor.inline") }
            suppressions += liste.count { it.kind == "refactor.rewrite.delete" }
        }
        assertTrue(extractions > 0, "les extractions sont servies sur sélection ($extractions)")
        assertTrue(inlines > 0, "les inlines sont servis au caret ($inlines)")
        assertTrue(suppressions > 0, "les safe deletes sont servis au caret ($suppressions)")

        // La série mesurée : 8 positions × 6 tours = 48 requêtes (miroir du
        // banc tranche 1).
        val durees = mutableListOf<Long>()
        repeat(6) {
            for ((debut, fin) in positions) {
                val debutNanos = System.nanoTime()
                moteur.intentions(chemin, source, debut, fin)
                durees.add((System.nanoTime() - debutNanos) / 1_000)
            }
        }
        val triees = durees.sorted()
        val p50 = triees[triees.size / 2]
        val p95 = triees[(triees.size * 95 / 100).coerceAtMost(triees.size - 1)]
        val max = triees.last()
        println("TRANSCRIPT_VD2_LISTE: requetes=${durees.size} p50=${p50} p95=${p95} max=${max} (microsecondes)")
        assertEquals(48, durees.size, "48 requêtes mesurées")
        assertTrue(p95 < 30_000, "la liste codeAction tient le budget p95 < 30 ms (mesuré $p95 µs)")
    }
}

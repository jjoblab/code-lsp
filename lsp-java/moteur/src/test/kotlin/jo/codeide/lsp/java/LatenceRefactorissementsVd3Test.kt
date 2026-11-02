package jo.codeide.lsp.java

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vague V-D TRANCHE 3 — TRANSCRIPT DE LATENCE de la liste `codeAction`
 * AVEC les refactorings lourds étendus (critère de sortie du plan 04 :
 * liste p95 < 30 ms JVM — la CI reste la référence ; R5 : aucune valeur
 * fabriquée, les chiffres ci-dessous sont MESURÉS au banc du bac à
 * sable).
 *
 * Le profil mesuré : la requête intentions COMPLÈTE (tranches 1–2 :
 * intentions légères, générateurs, extractions, inline, safe delete ;
 * V-D3 : change signature au caret d'un paramètre, suppression
 * hiérarchique au caret d'un override — la liste ne porte que des clés +
 * positions, les gardes multi-fichiers vivent au resolve, ADR 0027 (f)).
 */
class LatenceRefactorissementsVd3Test {

    @Test
    fun `transcript de latence de la liste codeAction avec refactorings etendus (p95 cible sous 30 ms JVM)`() {
        val source = """
            package demo;
            public class Main {
                private static final int MAX = 100;
                private int base = 1;

                public int plafond(int valeur, int silence) {
                    return Math.min(valeur * base, MAX);
                }

                @Override
                public String toString() {
                    return "Main";
                }

                private int obsolete() {
                    return base;
                }
            }
        """.trimIndent()
        val racine = Files.createTempDirectory("vd3-latence")
        Files.createDirectories(racine.resolve("demo"))
        racine.resolve("demo").resolve("Main.java").writeText(source)
        val moteur = MoteurJava(racines = listOf(racine))
        val chemin = "demo/Main.java"

        // Les positions CIBLÉES des régimes V-D3 : CARET sur le paramètre
        // inutilisé (change signature), sur la méthode toString
        // (outrepasse Object.toString — suppression hiérarchique), sur la
        // constante (inline PROJET), sur une méthode morte (safe delete).
        val positions: List<Pair<Int, Int>> = listOf(
            source.indexOf("silence") to source.indexOf("silence"),
            source.indexOf("MAX =") to source.indexOf("MAX ="),
            source.indexOf("obsolete()") to source.indexOf("obsolete()"),
            source.indexOf("toString()") to source.indexOf("toString()"),
            source.indexOf("valeur * base") to source.indexOf("valeur * base") + "valeur * base".length,
        )

        // Échauffement (le cache de parse et le mémo se remplissent) — et
        // preuve que les actions V-D3 sont bien SERVIES sur les positions.
        var changeSignature = 0
        var hierarchiques = 0
        var inlines = 0
        var suppressions = 0
        for ((debut, fin) in positions) {
            val liste = moteur.intentions(chemin, source, debut, fin)
            changeSignature += liste.count { it.kind == "refactor.rewrite.signature" }
            hierarchiques += liste.count { it.kind == "refactor.rewrite.delete" && it.titre.contains("hiérarchie") }
            inlines += liste.count { it.kind == "refactor.inline.constant" }
            suppressions += liste.count { it.kind == "refactor.rewrite.delete" && !it.titre.contains("hiérarchie") }
        }
        assertTrue(changeSignature > 0, "le change signature est servi au caret du paramètre ($changeSignature)")
        assertTrue(hierarchiques > 0, "la suppression hiérarchique est servie au caret de l'override ($hierarchiques)")
        assertTrue(inlines > 0, "l'inline de constante PROJET est servi au caret ($inlines)")
        assertTrue(suppressions > 0, "le safe delete simple reste servi ($suppressions)")

        // La série mesurée : 5 positions × 6 tours = 30 requêtes.
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
        println("TRANSCRIPT_VD3_LISTE: requetes=${durees.size} p50=${p50} p95=${p95} max=${max} (microsecondes)")
        assertEquals(30, durees.size, "30 requêtes mesurées")
        assertTrue(p95 < 30_000, "la liste codeAction tient le budget p95 < 30 ms (mesuré $p95 µs)")
    }
}

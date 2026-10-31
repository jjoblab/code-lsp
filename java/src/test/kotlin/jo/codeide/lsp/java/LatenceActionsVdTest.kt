package jo.codeide.lsp.java

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Vague V-D — TRANSCRIPT DE LATENCE de la liste `codeAction` (critère de
 * sortie du plan 04 : liste p95 < 30 ms JVM — la CI reste la référence,
 * R5 : aucune valeur fabriquée, les chiffres ci-dessous sont MESURÉS au
 * banc du bac à sable).
 *
 * Le profil mesuré : la requête codeAction COMPLÈTE sans `only` —
 * quickfixes (contexte vide ici) + intentions EAGER + générateurs
 * DIFFÉRÉS (la liste ne porte que clé + positions, ADR 0027 (f)) — sur
 * les positions de déclenchement ciblées (champ, nom de type, littéral,
 * if/else, lambda, var).
 */
class LatenceActionsVdTest {

    @Test
    fun `transcript de latence de la liste codeAction (p95 cible sous 30 ms JVM)`() {
        val source = """
            package demo;
            import java.util.function.Function;
            public class Main {
                private String nom = "Ada";
                private int age = 36;
                private boolean actif = true;

                public Main() { }

                String etiquette(boolean majeur) {
                    if (majeur) {
                        return "adulte";
                    } else {
                        return "enfant";
                    }
                }

                int plafond() {
                    return 42;
                }

                Function<String, Integer> longueurs() {
                    return s -> s.length();
                }

                void saluer() {
                    String salut = "bonjour";
                    var compte = 3;
                    System.out.println(salut + compte);
                }
            }
        """.trimIndent()
        val racine = Files.createTempDirectory("vd-latence")
        Files.createDirectories(racine.resolve("demo"))
        racine.resolve("demo").resolve("Main.java").writeText(source)
        val moteur = MoteurJava(racines = listOf(racine))
        val chemin = "/demo/Main.java"

        // Les positions de déclenchement ciblées (champ, nom de type,
        // littéral, if, lambda, var, corps vide).
        val positions = listOf(
            source.indexOf("nom ="),
            source.indexOf("Main()"),
            source.indexOf("42;"),
            source.indexOf("if (majeur)"),
            source.indexOf("s ->"),
            source.indexOf("String salut"),
            source.indexOf("var compte"),
            source.indexOf("etiquette"),
        )
        val diagnostics = moteur.diagnose(chemin, source)

        // Échauffement (le cache de parse et le mémo se remplissent).
        for (position in positions) {
            moteur.intentions(chemin, source, position, position)
            moteur.actionsCode(chemin, source, diagnostics)
        }

        // La série mesurée : 8 positions × 6 tours = 48 requêtes (miroir du
        // banc double-tampon V-F1).
        val durees = mutableListOf<Long>()
        repeat(6) {
            for (position in positions) {
                val debut = System.nanoTime()
                val liste = moteur.intentions(chemin, source, position, position)
                val quickfixes = moteur.actionsCode(chemin, source, diagnostics)
                durees.add((System.nanoTime() - debut) / 1_000)
                assertTrue(liste.size + quickfixes.size >= 0, "les listes vivent")
            }
        }
        val triees = durees.sorted()
        val p50 = triees[triees.size / 2]
        val p95 = triees[(triees.size * 95 / 100).coerceAtMost(triees.size - 1)]
        val max = triees.last()
        println("TRANSCRIPT_VD_LISTE: requetes=${durees.size} p50=${p50} p95=${p95} max=${max} (microsecondes)")
        assertTrue(p95 < 30_000, "la liste codeAction tient le budget p95 < 30 ms (mesuré $p95 µs)")
    }
}

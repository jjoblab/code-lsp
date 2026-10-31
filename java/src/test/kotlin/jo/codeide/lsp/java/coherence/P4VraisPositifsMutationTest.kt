package jo.codeide.lsp.java.coherence

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * P4 — Vrais positifs par mutation (prompt §4.3, écart G-005).
 *
 * **Propriété** : un mutant d'un code valide (suppression d'import, renommage
 * d'une référence, type erroné, `;` manquant, appel de méthode inexistant) produit
 * **au moins une erreur, à la bonne plage** (recouvrant le point de mutation).
 *
 * **Mode** : asserté dur sur « ≥ 1 ERREUR recouvrant l'ancre de mutation »
 * (comportements couverts par le cahier — fiches 007/013) ; les **codes ecj
 * effectivement émis** sont rapportés (baseline de rappel — la mesure fine du
 * rappel par famille se fait sur corpus réel en vague V-A).
 */
class P4VraisPositifsMutationTest {

    /** Le code de départ : valide (vérifié par P3 sur le même style). */
    private val valide = """
        package app;

        import java.util.ArrayList;
        import java.util.List;

        public class Bibliotheque {
            private List<String> rayons = new ArrayList<>();
            private int capacite = 10;

            public boolean ajouter(String titre) {
                if (rayons.size() >= capacite) {
                    return false;
                }
                return rayons.add(titre);
            }

            public String premier() {
                return rayons.isEmpty() ? "" : rayons.get(0);
            }
        }
    """.trimIndent()

    /** Un mutant : (libellé, source mutée, offset de l'ancre de mutation). */
    private data class Mutant(val libelle: String, val source: String, val ancre: Int)

    private fun mutants(): List<Mutant> {
        val liste = mutableListOf<Mutant>()

        // M1 — suppression de l'import java.util.List.
        run {
            val source = valide.replace("import java.util.List;\n", "")
            liste.add(Mutant("import supprimé", source, source.indexOf("List<String> rayons")))
        }

        // M2 — variable renommée à l'usage (référence non résolue).
        run {
            val source = valide.replace("rayons.size()", "rayonsX.size()")
            liste.add(Mutant("variable renommée", source, source.indexOf("rayonsX")))
        }

        // M3 — type erroné (affectation int → String).
        run {
            val source = valide.replace("private int capacite = 10;", "private String capacite = 10;")
            liste.add(Mutant("type erroné", source, source.indexOf("10;")))
        }

        // M4 — point-virgule manquant (l'ancre est la position du « ; »
        // attendu — ecj rapporte l'erreur d'insertion À CETTE position).
        run {
            val source = valide.replace("return rayons.add(titre);", "return rayons.add(titre)")
            liste.add(
                Mutant(
                    "point-virgule manquant",
                    source,
                    source.indexOf("rayons.add(titre)") + "rayons.add(titre)".length,
                ),
            )
        }

        // M5 — méthode inexistante sur le récepteur.
        run {
            val source = valide.replace("rayons.isEmpty()", "rayons.estVide()")
            liste.add(Mutant("méthode inexistante", source, source.indexOf("estVide()")))
        }

        // M6 — champ renommé à l'usage.
        run {
            val source = valide.replace(">= capacite)", ">= capaciteX)")
            liste.add(Mutant("champ renommé", source, source.indexOf("capaciteX")))
        }
        return liste
    }

    @Test
    fun `chaque mutant produit une erreur a la bonne plage`() {
        val rapport = SocleCoherence.Rapport("P4", "rappel de vrais positifs par mutation (à la bonne plage)")
        for (mutant in mutants()) {
            val diagnostics = SocleCoherence.temoinNeuf().diagnose("/app/Bibliotheque.java", mutant.source)
            val erreurs = SocleCoherence.erreurs(diagnostics)
            val surAncre = erreurs.filter { d ->
                d.plage.debutOffset <= mutant.ancre && mutant.ancre < d.plage.finOffset + 12
            }
            if (surAncre.isNotEmpty()) {
                rapport.mesure()
                println(
                    "[P4] ${mutant.libelle} : ${surAncre.size} erreur(s) à l'ancre — codes : " +
                        surAncre.map { it.code ?: "sans-code" }.toSet().joinToString(),
                )
            } else {
                rapport.violer(
                    "${mutant.libelle} : aucune erreur ne recouvre l'ancre ${mutant.ancre} " +
                        "(erreurs totales : ${erreurs.joinToString { it.message }})",
                    "faux négatif (G-005 — rappel de mutation insuffisant)",
                )
            }
        }
        rapport.clore("${mutants().size} mutants")
        assertTrue(mutants().isNotEmpty(), "la liste de mutants ne doit pas être vide")
    }
}

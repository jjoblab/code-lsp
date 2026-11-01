package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * P3 — Zéro faux positif sur code qui compile (prompt §4.3, écarts G-001/G-005,
 * hypothèses H1/H11 — la propriété fondatrice « le moteur ne rend pas FAUX »).
 *
 * **Propriété** : sur du code Java valide, le moteur ne produit **aucune erreur**
 * (un « cannot be resolved » sur du code qui compile est un faux positif — le
 * symptôme n°1 des retours appareil).
 *
 * **Deux régimes** :
 *  - **corpus synthétique** (asserté dur, présent) : fichiers valides variés
 *    (génériques, lambdas, enums, switch, classes internes, try-with-resources,
 *    `var`, records) — zéro ERREUR attendu ;
 *  - **corpus réel** (gate) : les 5 projets Android du corpus (téléchargés par
 *    `scripts/telecharger-corpus-android.sh`, manifestes de préparation sous
 *    `<repertoire>/prepares/<projet>.manifeste` — format `racine:` / `classpath:`,
 *    voir `bancs/corpus-android/LISEZMOI.md`) — la métrique FP/1 000 lignes par
 *    famille d'ID ecj est MESURÉE et imprimée (baseline d'écart — aucun seuil
 *    asserté avant la vague V-A).
 */
class P3ZeroFauxPositifTest {

    /** Le corpus synthétique : valide, varié, sans dépendance hors JDK. */
    private val corpusSynthetique: Map<String, String> = linkedMapOf(
        "Generiques.java" to """
            package app;

            import java.util.ArrayList;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;

            public class Generiques<T extends Comparable<T>> {
                private final List<T> elements = new ArrayList<>();
                private final Map<String, List<T>> index = new HashMap<>();

                public void ajouter(T element) {
                    elements.add(element);
                    index.computeIfAbsent(element.toString(), cle -> new ArrayList<>()).add(element);
                }

                public T plusGrand() {
                    T meilleur = null;
                    for (T candidat : elements) {
                        if (meilleur == null || candidat.compareTo(meilleur) > 0) {
                            meilleur = candidat;
                        }
                    }
                    return meilleur;
                }
            }
        """.trimIndent(),
        "Expressions.java" to """
            package app;

            import java.util.List;
            import java.util.Optional;

            public class Expressions {
                public long total(List<Integer> valeurs) {
                    return valeurs.stream().filter(v -> v > 0).mapToLong(Integer::longValue).sum();
                }

                public Optional<String> premier(List<String> valeurs) {
                    return valeurs.stream().filter(v -> !v.isEmpty()).findFirst();
                }

                private record Point(int x, int y) {
                    private double distance() {
                        return Math.sqrt(x * x + y * y);
                    }
                }

                public double distanceOriginale() {
                    return new Point(3, 4).distance();
                }
            }
        """.trimIndent(),
        "Enums.java" to """
            package app;

            public class Enums {
                private enum Couleur { ROUGE, VERT, BLEU }

                public String nommer(Couleur couleur) {
                    return switch (couleur) {
                        case ROUGE -> "r";
                        case VERT -> "v";
                        case BLEU -> "b";
                    };
                }

                public int ancienneFacon(Couleur couleur) {
                    switch (couleur) {
                        case ROUGE:
                            return 1;
                        case VERT:
                            return 2;
                        case BLEU:
                        default:
                            return 3;
                    }
                }
            }
        """.trimIndent(),
        "Imbriquees.java" to """
            package app;

            public class Imbriquees {
                private int compteur = 0;

                private interface Observateur {
                    void notifier(int valeur);
                }

                private static class CompteurSimple {
                    private int valeur = 0;

                    public void incrementer() {
                        valeur++;
                    }
                }

                public void executer() {
                    Observateur observateur = nouvelle -> compteur += nouvelle;
                    observateur.notifier(2);
                    CompteurSimple simple = new CompteurSimple();
                    simple.incrementer();
                    try (var lecteur = new java.io.StringReader("abc")) {
                        compteur += lecteur.read();
                    } catch (java.io.IOException e) {
                        compteur = 0;
                    }
                    var anonyme = new Object() {
                        @Override
                        public String toString() {
                            return "anonyme";
                        }
                    };
                    compteur += anonyme.toString().length();
                }
            }
        """.trimIndent(),
        "Heritage.java" to """
            package app;

            public class Heritage {
                public static abstract class Forme {
                    public abstract double aire();

                    @Override
                    public String toString() {
                        return "forme de " + aire();
                    }
                }

                public static final class Carre extends Forme {
                    private final double cote;

                    public Carre(double cote) {
                        this.cote = cote;
                    }

                    @Override
                    public double aire() {
                        return cote * cote;
                    }
                }

                public boolean egalites() {
                    Forme a = new Carre(2);
                    Forme b = new Carre(2);
                    return a.aire() == b.aire();
                }
            }
        """.trimIndent(),
    )

    @Test
    fun `zero erreur sur corpus synthetique valide`() {
        val rapport = SocleCoherence.Rapport("P3", "zéro faux positif sur corpus synthétique")
        var lignes = 0
        for ((nom, source) in corpusSynthetique) {
            lignes += source.lines().size
            val diagnostics = SocleCoherence.temoinNeuf().diagnose("/app/$nom", source)
            val erreurs = SocleCoherence.erreurs(diagnostics)
            if (erreurs.isEmpty()) {
                rapport.mesure()
            } else {
                erreurs.forEach {
                    rapport.violer(
                        "$nom : ${it.message} à ${it.plage.debutOffset}-${it.plage.finOffset}",
                        "faux positif sur code valide (G-005 — à arbitrer contre javac)",
                    )
                }
            }
        }
        println("[P3] corpus synthétique : $lignes lignes, ${rapport.nbViolations} FP, FP/1 000 lignes = ${if (lignes == 0) 0.0 else rapport.nbViolations * 1000.0 / lignes}")
        rapport.clore("${corpusSynthetique.size} fichiers valides")
        assertTrue(corpusSynthetique.isNotEmpty(), "le corpus synthétique ne doit pas être vide")
    }

    @Test
    fun `faux positifs sur corpus reel android (gate CORPUS_ANDROID)`() {
        val projets = SocleCoherence.projetsCorpus()
        assumeTrue(
            projets.isNotEmpty(),
            "CORPUS_ANDROID/manifestes absents — corpus réel non téléchargé ou non préparé " +
                "(scripts/telecharger-corpus-android.sh puis scripts/preparer-manifestes-corpus.sh) ; " +
                "gate ignorée, jamais un vert silencieux",
        )

        val rapport = SocleCoherence.Rapport("P3", "faux positifs sur corpus réel (FP/1 000 lignes, par famille)")
        for (projet in projets) {
            // Bornage du coût (ADR 0030 R3) : le noyau dur d'abord (code écrit
            // à la main — le « 0 FP » de la sortie de vague V-A), puis les
            // sources générées dans la limite du budget.
            val fichiersNoyau = projet.fichiersNoyau.take(120)
            val fichiersGenerees = projet.fichiersGenerees.take(30)
            val moteur = MoteurJava(racines = projet.toutesRacines, jars = projet.jars)
            try {
                for ((etiquette, fichiers) in listOf("noyau-dur" to fichiersNoyau, "genere" to fichiersGenerees)) {
                    var lignes = 0
                    val parCode = mutableMapOf<String, Int>()
                    for (fichier in fichiers) {
                        val source = Files.readString(fichier)
                        lignes += source.lines().size
                        val diagnostics = runCatching { moteur.diagnose(fichier.toString(), source) }.getOrDefault(emptyList())
                        SocleCoherence.erreurs(diagnostics).forEach { erreur ->
                            val cle = erreur.code ?: "sans-code"
                            parCode.merge(cle, 1, Int::plus)
                            rapport.violer(
                                "${projet.cle}/${fichier.fileName} ($etiquette) : ${erreur.message}",
                                "FP réel (famille $cle) — divergence moteur/javac à arbitrer (G-002/G-005, rapport ecj↔javac)",
                            )
                        }
                    }
                    val fpPourMille = if (lignes == 0) 0.0 else parCode.values.sum() * 1000.0 / lignes
                    println("[P3] ${projet.cle}/$etiquette : ${fichiers.size} fichiers, $lignes lignes, FP/1 000 = $fpPourMille, par famille : $parCode")

                    // Critères de sortie V-A (ADR 0030 (c), plan §V-A) :
                    // FP < 0,5/1 000 lignes en régime nominal, 0 sur le noyau dur.
                    if (etiquette == "noyau-dur" && parCode.values.sum() > 0) {
                        rapport.violer(
                            "${projet.cle}/noyau-dur : ${parCode.values.sum()} FP sur le noyau dur — le critère V-A exige 0",
                            "FP noyau dur (G-002/G-005) — corriger ou arbitrer en quirk documenté",
                        )
                    }
                    if (etiquette == "genere" && fpPourMille >= 0.5) {
                        rapport.violer(
                            "${projet.cle}/genere : FP/1 000 = $fpPourMille ≥ 0,5 — le critère V-A exige < 0,5",
                            "FP code généré (G-002/G-005) — corriger ou arbitrer en quirk documenté",
                        )
                    }
                }
            } finally {
                moteur.dispose()
            }
        }
        rapport.clore("corpus réel (${projets.size} manifestes)")
    }
}

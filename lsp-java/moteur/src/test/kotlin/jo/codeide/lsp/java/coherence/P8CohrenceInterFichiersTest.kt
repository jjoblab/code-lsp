package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * P8 — Cohérence inter-fichiers des diagnostics (prompt §4.3, écart G-003,
 * hypothèse H4 — l'incohérence structurelle la plus visible).
 *
 * **Propriété** : éditer l'API d'un fichier `A` (ici : renommage d'une méthode
 * publique sur le disque) doit se refléter dans les diagnostics de tout fichier
 * `B` **ouvert** qui dépend de cette API — sans attendre un remplacement de
 * contexte complet (sync Gradle).
 *
 * **Mode** : la propriété est aujourd'hui **NON garantie** (H4 : le mémo par
 * fichier de `diagnose` sert les diagnostics périmés tant que la source de B
 * ne change pas, et rien ne re-demande B) — elle est donc **mesurée et
 * rapportée** comme baseline d'écart, avec deux garde-fous **assertés durs** :
 * (a) le projet initial compile (0 erreur) ; (b) un moteur NEUF voit bien
 * l'erreur après l'édition de A (la résolution, elle, est correcte — c'est la
 * **fraîcheur** qui manque, pas la capacité).
 */
class P8CohrenceInterFichiersTest {

    private val sourceA = """
        package demo;

        public class A {
            public int taille() {
                return 3;
            }
        }
    """.trimIndent()

    private val sourceB = """
        package demo;

        public class B {
            public int lire(A porteur) {
                return porteur.taille();
            }
        }
    """.trimIndent()

    @Test
    fun `edition de l API de A se reflete dans les diagnostics de B`() {
        val rapport = SocleCoherence.Rapport("P8", "diagnostics de B à jour après édition de l'API de A")
        val racine = SocleCoherence.projetTemporaire(
            mapOf(
                "demo/A.java" to sourceA,
                "demo/B.java" to sourceB,
            ),
        )
        try {
            val cheminA = racine.resolve("demo/A.java")
            val cheminB = racine.resolve("demo/B.java")
            val moteur = MoteurJava(racines = listOf(racine))

            // (a) Dur : le projet initial compile.
            val initial = SocleCoherence.erreurs(moteur.diagnose(cheminB.toString(), sourceB))
            assertTrue(initial.isEmpty(), "le projet initial doit compiler (erreurs : ${initial.joinToString { it.message }})")
            rapport.mesure()

            // Édition de l'API de A : la méthode publique change de nom.
            Files.writeString(cheminA, sourceA.replace("taille", "grosseur"))

            // La propriété : B (ouvert, source inchangée) doit devenir faux.
            val apres = moteur.diagnose(cheminB.toString(), sourceB)
            val erreursApres = SocleCoherence.erreurs(apres)
            if (erreursApres.isNotEmpty()) {
                println("[P8] le moteur sous test voit bien l'erreur après édition de A : ${erreursApres.joinToString { it.message }}")
                rapport.mesure()
            } else {
                rapport.violer(
                    "l'API de A a changé (taille → grosseur) mais B (ouvert, inchangé) n'a toujours aucune erreur",
                    "mémo par fichier sans invalidation par dépendances (H4/G-003 — la re-publication n'existe qu'au remplacement de contexte)",
                )
            }

            // (b) Dur : un moteur NEUF voit l'erreur — preuve que la résolution
            // est correcte et que la divergence est une fraîcheur, pas une cécité.
            val temoinNeuf = MoteurJava(racines = listOf(racine))
            val erreursTemoin = SocleCoherence.erreurs(temoinNeuf.diagnose(cheminB.toString(), sourceB))
            assertTrue(
                erreursTemoin.isNotEmpty(),
                "un moteur neuf DOIT voir l'erreur de B après l'édition de A (sinon la résolution multi-fichiers est cassée)",
            )
            println("[P8] témoin neuf : ${erreursTemoin.size} erreur(s) attendue(s) — ${erreursTemoin.joinToString { it.message }}")
        } finally {
            racine.toFile().deleteRecursively()
        }
        rapport.clore("édition d'API inter-fichiers (1 scénario, garde-fous durs)")
    }
}

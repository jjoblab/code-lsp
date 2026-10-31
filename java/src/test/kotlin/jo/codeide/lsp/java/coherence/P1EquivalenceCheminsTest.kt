package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P1 — Équivalence des chemins rapide/complet sur historiques de frappe
 * (prompt §4.3, écart G-002, hypothèse H6).
 *
 * **Propriété** : pour toute séquence d'éditions CONFINÉES dans un corps de
 * méthode (le domaine du diagnose rapide, fiche 014), les diagnostics du moteur
 * sous test (chemin rapide + report des corps masqués) sont STRICTEMENT égaux à
 * ceux d'un moteur témoin NEUF qui diagnostique la même source en complet.
 *
 * **Mode** : le premier tour (diagnose complet, sans mémo) est asserté dur
 * (précédent fiche 014) ; les tours suivants (chemin rapide) sont MESURÉS :
 * toute divergence est rapportée avec la cause racine pressentie — le report
 * des corps masqués n'est pas re-validé (H6). Le mode strict
 * (`banc.coherence.strict`) les escalade en échecs pour la vague V-A.
 */
class P1EquivalenceCheminsTest {

    /** Fixture riche : champ privé inutilisé au départ, corps édités ensuite. */
    private val base = """
        package app;

        public class Panier {
            private int stockInutilise = 7;

            void ajouter() { int x = 1; }

            void consulter() { System.out.println("ok"); }
        }
    """.trimIndent()

    /** Remplace le corps d'`ajouter` (édition confinée dans CE corps). */
    private fun corpsAjouter(source: String, corps: String): String =
        source.replace("void ajouter() { int x = 1; }", "void ajouter() { $corps }")

    @Test
    fun `equivalence sur historique confine simple`() {
        val rapport = SocleCoherence.Rapport("P1", "équivalence rapide/complet sur éditions confinées")
        val moteur = MoteurJava()

        // Tour 0 — complet (dur) : le témoin et le moteur sous test voient la
        // même chose au premier diagnostic (précédent fiche 014).
        val d0SousTest = moteur.diagnose("/app/Panier.java", base)
        val d0Temoin = SocleCoherence.temoinNeuf().diagnose("/app/Panier.java", base)
        assertEquals(
            d0Temoin.map(SocleCoherence::empreinte),
            d0SousTest.map(SocleCoherence::empreinte),
            "le premier diagnose (complet) doit être identique au témoin neuf",
        )

        // Historique : trois éditions confinées dans le corps d'ajouter().
        val editions = listOf(
            "int x = 1;",
            "int x = 1; int y = 2;",
            "int x = 1; int y = 2; System.out.println(x + y);",
        )
        for (corps in editions) {
            val source = corpsAjouter(base, corps)
            val sousTest = moteur.diagnose("/app/Panier.java", source)
            val temoin = SocleCoherence.temoinNeuf().diagnose("/app/Panier.java", source)
            val differences = SocleCoherence.differences(sousTest, temoin)
            if (differences.isEmpty()) {
                rapport.mesure()
            } else {
                differences.forEach { rapport.violer(it, "report du chemin rapide non re-validé (H6, G-002)") }
            }
        }
        rapport.clore("historique confiné simple (3 éditions)")
    }

    @Test
    fun `equivalence quand un corps se met a utiliser un champ prive`() {
        val rapport = SocleCoherence.Rapport("P1", "équivalence rapide/complet — usage tardif d'un champ")
        val moteur = MoteurJava()

        // Tour 0 : le champ privé est inutilisé (avertissement analytique attendu).
        moteur.diagnose("/app/Panier.java", base)

        // Édition confinée : le corps d'ajouter() se met à UTILISER le champ —
        // l'avertissement « champ privé inutilisé » doit disparaître. Le chemin
        // rapide masque les autres corps et PORTE les signaux précédents : si le
        // portage n'est pas re-validé, l'avertissement périmé survit.
        val source = corpsAjouter(base, "stockInutilise++;")
        val sousTest = moteur.diagnose("/app/Panier.java", source)
        val temoin = SocleCoherence.temoinNeuf().diagnose("/app/Panier.java", source)
        val differences = SocleCoherence.differences(sousTest, temoin)
        if (differences.isEmpty()) {
            rapport.mesure()
        } else {
            differences.forEach {
                rapport.violer(
                    it,
                    "problème périmé porté par le chemin rapide (H6 — le report des corps masqués n'est pas re-validé ; G-002)",
                )
            }
        }
        rapport.clore("usage tardif d'un champ privé (1 édition)")
    }

    @Test
    fun `equivalence sur frappe caractere par caractere`() {
        val rapport = SocleCoherence.Rapport("P1", "équivalence rapide/complet sur frappe lettre à lettre")
        val moteur = MoteurJava()
        val instruction = "        int total = 40 + 2;"

        // On tape l'instruction caractère par caractère dans consulter().
        val historique = (1..instruction.length).map { n ->
            base.replace(
                "void consulter() { System.out.println(\"ok\"); }",
                "void consulter() { System.out.println(\"ok\"); ${instruction.substring(0, n)} }",
            )
        }
        for (source in historique) {
            val sousTest = moteur.diagnose("/app/Panier.java", source)
            val temoin = SocleCoherence.temoinNeuf().diagnose("/app/Panier.java", source)
            val differences = SocleCoherence.differences(sousTest, temoin)
            if (differences.isEmpty()) {
                rapport.mesure()
            } else {
                differences.forEach { rapport.violer(it, "divergence rapide/complet en cours de frappe (H6, G-002)") }
            }
        }
        rapport.clore("frappe lettre à lettre (${historique.size} éditions)")
        assertTrue(historique.isNotEmpty(), "l'historique ne doit pas être vide")
    }

    @Test
    fun `repli explicite quand le texte hors corps change`() {
        val rapport = SocleCoherence.Rapport("P1", "repli complet sur édition hors corps (garde fiche 014)")
        val moteur = MoteurJava()
        moteur.diagnose("/app/Panier.java", base)

        // Édition HORS corps : la signature de consulter() change — la garde de
        // confinement DOIT forcer le diagnose complet (équivalence attendue).
        val source = base.replace("void consulter()", "void consulter(boolean silencieux)")
        val sousTest = moteur.diagnose("/app/Panier.java", source)
        val temoin = SocleCoherence.temoinNeuf().diagnose("/app/Panier.java", source)
        val differences = SocleCoherence.differences(sousTest, temoin)
        if (differences.isEmpty()) {
            rapport.mesure()
        } else {
            differences.forEach { rapport.violer(it, "garde de repli hors-corps non tenue (fiche 014, G-002)") }
        }
        rapport.clore("édition hors corps (signature)")
    }
}

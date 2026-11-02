package jo.codeide.lsp.kotlin.parite

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import jo.codeide.lsp.kotlin.parse.ParseurKotlin
import org.junit.jupiter.api.Test

/**
 * Fiche 011 — ToleranceErreursTest : tolérance aux fautes (D3 amont).
 *
 * Un texte en cours d'édition doit TOUJOURS produire un arbre : l'éditeur
 * appelle le parseur à chaque frappe, y compris entre deux lettres, au
 * milieu d'une chaîne, d'un commentaire ou d'un KDoc. L'oracle est simple :
 * AUCUNE exception levée, un arbre racine complet à chaque appel — les
 * fautes éventuelles se lisent comme des marqueurs d'erreur DANS l'arbre,
 * jamais comme des échecs.
 *
 * Les mutations sont DÉTERMINISTES (positions calculées, germe fixe pour
 * les délétions) : la suite est rejouable à l'identique en CI.
 */
class Fiche011ToleranceErreursTest {

    private val parseur = ParseurKotlin()

    /** Fixture représentative : KDoc, imports, classe, chaîne interpolée,
     * commentaires de ligne et de bloc, lambda, identifiants longs. */
    private val fixture: String = """
        package demo.tolerance

        import java.util.ArrayList

        /**
         * Documentation KDoc avec [lien] et balise.
         */
        class Demonstration(val nom: String) {
            private val liste = ArrayList<String>()

            fun salue(fois: Int): String {
                val texte = "bonjour ${'$'}nom"
                // commentaire de ligne
                /* commentaire de bloc */
                return texte.repeat(fois)
            }

            fun appliquer(action: (String) -> Unit) {
                liste.forEach { ligne -> action(ligne.trim()) }
            }
        }
    """.trimIndent() + "\n"

    @Test
    fun `troncature-a-chaque-position---aucun-lever`() {
        var arbrePresent = 0
        // Troncature à CHAQUE position (0 inclus : fichier vide).
        for (position in 0..fixture.length) {
            val resultat = parseur.analyse(fixture.take(position))
            assertTrue(resultat.genreRacine.isNotEmpty(), "position $position : arbre racine attendu")
            if (resultat.genreRacine != "(aucune)") arbrePresent++
        }
        assertEquals(fixture.length + 1, arbrePresent, "chaque troncature rend un arbre racine complet")
    }

    @Test
    fun `insertions-en-positions-hostiles---aucun-lever`() {
        // Positions hostiles calculées sur la fixture : milieu de chaîne
        // interpolée, de commentaire de ligne, de commentaire de bloc, de
        // KDoc, d'identifiant, de mot-clé.
        val positions = listOf(
            "milieu de chaîne interpolée" to fixture.indexOf("bonjour") + 3,
            "milieu de commentaire de ligne" to fixture.indexOf("commentaire de ligne") + 8,
            "milieu de commentaire de bloc" to fixture.indexOf("commentaire de bloc") + 8,
            "milieu de KDoc" to fixture.indexOf("Documentation KDoc") + 8,
            "milieu d'identifiant" to fixture.indexOf("Demonstration") + 6,
            "milieu de mot-clé" to fixture.indexOf("return") + 2,
        ).filter { (_, offset) -> offset > 0 }
        val charges = listOf("{", "}", "/*", "*/", "\"", "'", "(", ")", "\n", "\$", "\\", "fun", "é", "@")
        var essais = 0
        for ((nom, position) in positions) {
            for (charge in charges) {
                val mutée = fixture.substring(0, position) + charge + fixture.substring(position)
                val resultat = parseur.analyse(mutée)
                assertTrue(
                    resultat.genreRacine.isNotEmpty(),
                    "$nom + « $charge » (position $position) : arbre racine attendu",
                )
                essais++
            }
        }
        assertTrue(essais >= 60, "échantillon d'insertions hostiles trop faible : $essais")
    }

    @Test
    fun `deletions-aleatoires-bornees---aucun-lever`() {
        // Délétions pseudo-aléatoires BORNÉES (1 à 8 caractères), germe FIXE :
        // la suite est déterministe et rejouable à l'identique.
        val alea = java.util.Random(42)
        val bornes = 400
        for (essai in 0 until bornes) {
            val debut = alea.nextInt(fixture.length)
            val fin = (debut + 1 + alea.nextInt(8)).coerceAtMost(fixture.length)
            val mutée = fixture.substring(0, debut) + fixture.substring(fin)
            val resultat = parseur.analyse(mutée)
            assertTrue(
                resultat.genreRacine.isNotEmpty(),
                "délétion [$debut, $fin) (essai $essai) : arbre racine attendu",
            )
        }
    }
}

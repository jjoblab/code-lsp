package jo.codeide.lsp.kotlin.parse

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fiche 012 — ParseurKotlinTest : tests du harnais sur des sources
 * représentatives (l'autre moitié de la suite de parité K1 : la fiche 010
 * balaye le dépôt entier, ces fixtures épinglent des formes précises,
 * jeton par jeton).
 */
class Fiche012ParseurKotlinTest {

    private val parseur = ParseurKotlin()

    /** Fixture valide complète : la plupart des formes de premier niveau. */
    private val fixtureValide: String = """
        package demo.parse

        import java.util.ArrayList
        import kotlin.math.PI as constante

        /**
         * Documentation de la classe.
         */
        class Exemple(
            val identifiant: Int,
            var libelle: String = "défaut",
        ) {
            private val cache = ArrayList<String>()

            fun calculer(facteur: Int): Double {
                val rayon = identifiant * facteur
                return 2.0 * constante * rayon
            }

            val descriptif: String
                get() = "Exemple ${'$'}identifiant: ${'$'}libelle"
        }

        fun utilitaire(exemple: Exemple): String = exemple.descriptif.trim()
    """.trimIndent() + "\n"

    @Test
    fun `fichier-vide---arbre-racine-KT_FILE-sans-erreur`() {
        val resultat = parseur.analyse("")
        // Vocabulaire amont : le nœud racine d'un fichier est enregistré
        // « kotlin.FILE » (KtNodeTypes.KT_FILE du KMP — la fiche porte le
        // nom historique KT_FILE du monde PSI ; même nœud, nom fidèle amont).
        assertEquals("kotlin.FILE", resultat.genreRacine, "la racine d'un fichier vide est le nœud fichier")
        assertEquals(0, resultat.nombreMarqueursErreur, "un fichier vide n'a aucun marqueur d'erreur")
    }

    @Test
    fun `accesseurs-non-whitespace---texte-du-jeton-exact`() {
        val resultat = parseur.analyse("val compteur = 42\n")
        val textes = resultat.jetonsNonBlancs.map { it.texte }
        assertEquals(
            listOf("val", "compteur", "=", "42"),
            textes,
            "le texte de chaque jeton non blanc est exact (blancs et commentaires exclus)",
        )
        assertEquals(
            "compteur",
            resultat.jetonsNonBlancs.first { it.genre == "IDENTIFIER" }.texte,
            "le genre IDENTIFIER porte le texte exact de l'identifiant",
        )
    }

    @Test
    fun `fichier-valide-complet---aucun-marqueur-d'erreur`() {
        val resultat = parseur.analyse(fixtureValide)
        assertEquals(
            0,
            resultat.nombreMarqueursErreur,
            "une source valide et complète ne produit aucun marqueur d'erreur",
        )
        assertEquals("kotlin.FILE", resultat.genreRacine)
        assertTrue(resultat.nombreProductions > 40, "l'arbre d'une fixture complète est riche")
    }

    @Test
    fun `accolade-manquante---arbre-construit-et-erreur-marquee`() {
        val source = """
            class Demo {
                fun f() {
                    val x = 1
            }
        """.trimIndent() + "\n"
        val resultat = parseur.analyse(source)
        assertEquals("kotlin.FILE", resultat.genreRacine, "l'arbre est construit malgré l'accolade manquante")
        assertTrue(
            resultat.nombreMarqueursErreur > 0,
            "l'accolade manquante est MARQUÉE en erreur dans l'arbre (elle ne lève pas)",
        )
    }

    @Test
    fun `texte-tronque-en-plein-identifiant---parse-sans-lever`() {
        val complet = "fun calculInteressant(x: Int): Int = x + 1\n"
        val tronque = complet.substring(0, "fun calculInteres".length)
        val resultat = parseur.analyse(tronque)
        assertEquals("kotlin.FILE", resultat.genreRacine, "le texte tronqué en plein identifiant se parse")
    }

    @Test
    fun `chaine-non-fermee---parse-sans-lever`() {
        // La fiche n'exige QUE le sans-lever (tolérance d'édition) : la
        // grammaire amont est tolérante sur la chaîne non fermée — le jeton
        // est émis, l'arbre est construit, RIEN ne lève.
        val resultat = parseur.analyse("val message = \"jamais fermé\n")
        assertEquals("kotlin.FILE", resultat.genreRacine, "la chaîne non fermée se parse sans lever")
    }

    @Test
    fun `script-kts---directives-autorisees-en-racine`() {
        val script = """
            @file:Suppress("unused")

            import java.io.File

            println(File(".").absolutePath)

            val configuration = mapOf("cle" to 1)
        """.trimIndent() + "\n"
        // En mode script, la grammaire enveloppe le tout d'un nœud SCRIPT
        // sous la racine fichier (amont : parseScript marque file puis
        // script) — les directives et instructions de premier niveau y
        // sont acceptées SANS marqueur d'erreur. Constat honnête : la
        // grammaire KMP est tolérante — ces mêmes instructions ne sont pas
        // marquées non plus en mode fichier (le rejet des instructions en
        // racine de fichier relève de la sémantique du compilateur, pas de
        // la couche grammaticale) : le présent test épingle ce que le cahier
        // exige — l'acceptation SILENCIEUSE en mode script.
        val resultat = parseur.analyse(script, script = true)
        assertEquals("kotlin.FILE", resultat.genreRacine, "la racine reste le nœud fichier (SCRIPT est un enfant)")
        assertEquals(
            0,
            resultat.nombreMarqueursErreur,
            "les directives et instructions de premier niveau sont autorisées en racine d'un script",
        )
        assertTrue(
            resultat.nombreProductions > 10,
            "l'arbre du script est construit (directives et instructions analysées)",
        )
    }

    @Test
    fun `determinisme---deux-analyses-du-meme-texte-donnent-le-meme-digeste`() {
        val premiere = parseur.analyse(fixtureValide)
        val seconde = parseur.analyse(fixtureValide)
        assertEquals(
            premiere.digesteStructurel,
            seconde.digesteStructurel,
            "deux analyses du même texte donnent le même digeste structurel",
        )
        assertNotEquals(
            premiere.digesteStructurel,
            parseur.analyse(fixtureValide.replace("val cache", "var cache")).digesteStructurel,
            "un changement de FORME (val → var change le genre du jeton) déplace le digeste",
        )
    }

    @Test
    fun `positions---offsets-de-jetons-dans-le-texte-source`() {
        val source = "val aire = 3.14 * rayon\n"
        val resultat = parseur.analyse(source)
        for (jeton in resultat.jetonsNonBlancs) {
            assertTrue(
                jeton.debut >= 0 && jeton.fin <= source.length && jeton.debut < jeton.fin,
                "offsets hors bornes : [$jeton.debut, $jeton.fin) pour « ${jeton.texte} »",
            )
            assertEquals(
                jeton.texte,
                source.substring(jeton.debut, jeton.fin),
                "l'offset du jeton « ${jeton.genre} » pointe son texte exact dans le source",
            )
        }
        val identifiant = resultat.jetonsNonBlancs.first { it.texte == "rayon" }
        assertEquals(source.indexOf("rayon"), identifiant.debut, "l'offset de « rayon » est sa position réelle")
    }
}

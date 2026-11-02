package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.MoteurJava
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Fiche 027 — EcjSelectionRangesTest (lot S2-b, fiche mixte) : la chaîne
 * de sélection (`selectionRange`) et la cible de formatage à la frappe
 * (`onTypeFormatting`). La chaîne doit être STRICTEMENT croissante en
 * longueur (chaque niveau contient strictement le précédent — contrat LSP
 * 3.17 pour `parent`) et couvrir l'escalade complète jeton → expression →
 * instruction → bloc → déclaration → type → unité. La cible de formatage
 * doit tomber sur la construction qui SE TERMINE au caractère tapé
 * (`}` → méthode, `;` → instruction).
 */
class Fiche027ChainesSelectionTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava

        val SOURCE = """
            package demo;

            public class Main {
                private int total = 0;

                public int somme(int a, int b) {
                    return a + b;
                }

                public void malIndente() {
                    int y=2;
                    return;
                }
            }
        """.trimIndent()

        fun offsetDe(mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = SOURCE.indexOf(mot, position + 1) }
            return position
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            MOTEUR = MoteurJava(racines = emptyList(), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — fiche ignorée")
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            MOTEUR.dispose()
        }
    }

    /** chaineStrictementCroissanteDepuisUneExpression — croissance stricte, sommet = unité. */
    @Test
    fun chaineStrictementCroissanteDepuisUneExpression() {
        // Caret au milieu du « + » de « a + b ».
        val caret = offsetDe("a + b") + 2
        val chaine = MOTEUR.chainesSelection("demo/Main.java", SOURCE, listOf(caret)).firstOrNull()
        assertNotNull(chaine, "chaîne non vide")
        assertTrue(chaine.taille >= 2, "au moins deux niveaux : ${chaine.taille}")
        var precedent: jo.codeide.lsp.java.diagnostics.Plage? = null
        for (maillon in chaine) {
            if (precedent != null) {
                assertTrue(
                    maillon.plage.debutOffset <= precedent.debutOffset &&
                        maillon.plage.finOffset >= precedent.finOffset &&
                        maillon.plage.finOffset - maillon.plage.debutOffset > precedent.finOffset - precedent.debutOffset,
                    "chaque niveau est STRICTEMENT plus long ET contient le précédent",
                )
            }
            precedent = maillon.plage
        }
        val sommet = chaine.sommet!!.plage
        assertEquals(0, sommet.debutOffset, "le sommet démarre à 0")
        assertEquals(SOURCE.length, sommet.finOffset, "le sommet couvre toute la source")
    }

    /** escaladeCompleteJusquaLaDeclaration — l'escalade traverse les niveaux attendus. */
    @Test
    fun escaladeCompleteJusquaLaDeclaration() {
        val caret = offsetDe("a + b") + 2
        val chaine = MOTEUR.chainesSelection("demo/Main.java", SOURCE, listOf(caret)).firstOrNull()
        assertNotNull(chaine)
        val textes = chaine.maillons.map { SOURCE.substring(it.plage.debutOffset, it.plage.finOffset) }
        assertTrue(
            textes.any { it.trimStart().startsWith("return") },
            "un niveau INSTRUCTION commençant par « return » : $textes",
        )
        assertTrue(textes.any { it.contains("somme") }, "un niveau DÉCLARATION contenant « somme »")
        assertTrue(textes.any { it.contains("class Main") }, "un niveau TYPE contenant « class Main »")
    }

    /** jetonNomDuChampEtDeclarationComplete — jeton puis déclaration complète. */
    @Test
    fun jetonNomDuChampEtDeclarationComplete() {
        val caret = offsetDe("total")
        val chaine = MOTEUR.chainesSelection("demo/Main.java", SOURCE, listOf(caret)).firstOrNull()
        assertNotNull(chaine)
        assertTrue(chaine.taille >= 2, "au moins deux niveaux : ${chaine.taille}")
        // Le premier niveau est le jeton « total » EXACTEMENT.
        assertEquals("total", SOURCE.substring(chaine.maillons[0].plage.debutOffset, chaine.maillons[0].plage.finOffset))
        // Le second niveau est la déclaration complète du champ.
        val second = SOURCE.substring(chaine.maillons[1].plage.debutOffset, chaine.maillons[1].plage.finOffset)
        assertTrue(second.contains("private int total"), "le second niveau est la déclaration complète : « $second »")
    }

    /** offsetHorsBornesOuSourceVideChaineVide — entrées invalides : chaîne vide. */
    @Test
    fun offsetHorsBornesOuSourceVideChaineVide() {
        val resultats = MOTEUR.chainesSelection(
            "demo/Main.java",
            SOURCE,
            listOf(-1, SOURCE.length),
        )
        assertNull(resultats[0], "offset −1 → chaîne ABSENTE")
        assertNull(resultats[1], "offset = longueur → chaîne ABSENTE")
        assertNull(MOTEUR.chainesSelection("demo/Vide.java", "", listOf(0)).firstOrNull(), "source vide → chaîne ABSENTE")
    }

    /** sourceImparsableNeProvoquePasDException — imparsable : plages bornées, pas de crash. */
    @Test
    fun sourceImparsableNeProvoquePasDException() {
        val source = "class {{{{{"
        assertEquals(11, source.length, "la source d'imparsabilité du cahier fait 11 caractères")
        val chaine = MOTEUR.chainesSelection("demo/Pane.java", source, listOf(3)).firstOrNull()
        if (chaine != null) {
            for (maillon in chaine) {
                assertTrue(maillon.plage.debutOffset >= 0, "début ≥ 0")
                assertTrue(maillon.plage.finOffset <= 11, "fin ≤ 11")
                assertTrue(maillon.plage.debutOffset < maillon.plage.finOffset, "début < fin")
            }
        }
    }

    /** accoladeFermanteCibleLeBlocDeLaMethode — `}` cible le bloc de la méthode. */
    @Test
    fun accoladeFermanteCibleLeBlocDeLaMethode() {
        // L'accolade fermante de malIndente() — la MÉTHODE entière est la cible
        // (l'en-tête porte le nom, le corps porte les instructions).
        val corps = offsetDe("int y=2;")
        val finMethode = SOURCE.indexOf('}', corps)
        val cible = MOTEUR.plageFormattable("demo/Main.java", SOURCE, finMethode, '}')
        assertNotNull(cible, "cible servie pour « } »")
        val texte = SOURCE.substring(cible.debutOffset, cible.finOffset)
        assertTrue(texte.contains("int y=2;"), "la cible contient le corps mal indenté")
        assertTrue(texte.contains("malIndente"), "la cible contient la méthode entière")
    }

    /** pointVirguleCibleLinstruction — `;` cible l'instruction. */
    @Test
    fun pointVirguleCibleLinstruction() {
        val point = offsetDe("int y=2;") + "int y=2".length
        val cible = MOTEUR.plageFormattable("demo/Main.java", SOURCE, point, ';')
        assertNotNull(cible, "cible servie pour « ; »")
        val texte = SOURCE.substring(cible.debutOffset, cible.finOffset)
        assertTrue(texte.contains("int y=2;"), "la cible contient « int y=2; »")
        assertTrue(!texte.contains("return"), "la cible ne déborde PAS sur l'instruction suivante")
    }

    /** caractereInattenduAucuneCible — caractère inattendu : null. */
    @Test
    fun caractereInattenduAucuneCible() {
        val point = offsetDe("int y=2;") + "int y=2".length
        // Un « ; » à l'offset avec déclencheur demandé « } ».
        assertNull(MOTEUR.plageFormattable("demo/Main.java", SOURCE, point, '}'), "le caractère à l'offset doit ÊTRE le déclencheur")
        // Déclencheur hors liste.
        assertNull(MOTEUR.plageFormattable("demo/Main.java", SOURCE, point, 'x'), "déclencheur non supporté")
    }
}

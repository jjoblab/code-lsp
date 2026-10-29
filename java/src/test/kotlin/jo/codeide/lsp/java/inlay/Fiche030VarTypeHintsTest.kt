package jo.codeide.lsp.java.inlay

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 030 — VarTypeHintsTest (étage N3) : les inlay hints de TYPE des
 * déclarations `var` — le type RÉEL de l'initialiseur (appel de méthode,
 * référence générique, enhanced-for) affiché et ancré juste après le NOM de
 * la déclaration, AVANT le « = ». Les déclarations typées explicitement ne
 * sont pas annotées ; un « var » dans une chaîne littérale n'est pas une
 * déclaration (l'analyse est structurelle, pas textuelle).
 *
 * Corpus du cahier : classe `demo.Main` dont le corps est injecté (avec
 * `import java.util.List`).
 *
 * Anomalie de nommage du domaine consignée au cahier (fichier
 * `EcjVarTypeHintsTest.java`, classe interne `JdtVarTypeHintsTest`) : la
 * réécriture NOMME la classe d'après le CONTRAT, sans préfixe Ecj/Jdt.
 */
class Fiche030VarTypeHintsTest {

    private companion object {

        /** Moteur partagé (jrt paresseux, racines/jars vides — conformité 17). */
        val MOTEUR: MoteurJava by lazy { MoteurJava(racines = emptyList(), jars = emptyList()) }

        /** Chemin logique de l'unité de la fiche. */
        const val CHEMIN = "/demo/Main.java"

        /** Squelette du corpus : corps de classe injecté, import List. */
        val SQUELETTE = """
            package demo;

            import java.util.List;

            public class Main {
                void run(List<String> items) {
                    BLOC
                }
            }
        """.trimIndent()

        /** Source avec le corps injecté (remplace « BLOC »). */
        fun sourceAvec(corps: String): String = SQUELETTE.replace("BLOC", corps.trimIndent())

        /** Les indices de l'unité (moteur). */
        fun indices(source: String): List<IndiceInlay> = MOTEUR.inlayHints(CHEMIN, source)

        /** Offset de fin du NOM déclaré qui suit le motif « var <nom> ». */
        fun finNom(source: String, nom: String): Int {
            val i = source.indexOf("var $nom")
            assertTrue(i >= 0, "déclaration « var $nom » absente")
            return i + "var ".length + nom.length
        }
    }

    @Test
    fun complexInitializerGetsRealType() {
        // « var s = String.valueOf(42); » — le type vient du BINDING de
        // l'appel, pas du texte.
        val source = sourceAvec("var s = String.valueOf(42);")
        val indices = indices(source)
        assertEquals(1, indices.size, "exactement un hint : $indices")
        val hint = indices[0]
        assertEquals(": String", hint.libelle, "type réel de l'appel")
        assertEquals(finNom(source, "s"), hint.offset, "ancre en fin du nom « s »")
        assertEquals(GenreIndice.TYPE, hint.genre)
    }

    @Test
    fun genericsPreserved() {
        // « var list = items; » où items est List<String> — généricité
        // préservée, pas d'effacement.
        val source = sourceAvec("var list = items;")
        val indices = indices(source)
        assertEquals(1, indices.size, "exactement un hint : $indices")
        assertEquals(": List<String>", indices[0].libelle, "le type générique complet")
        assertEquals(finNom(source, "list"), indices[0].offset)
    }

    @Test
    fun enhancedForVarHinted() {
        // « for (var it : items) » — le var de boucle est annoté du type
        // d'ÉLÉMENT.
        val source = sourceAvec("for (var it : items) { }")
        val indices = indices(source)
        assertEquals(1, indices.size, "exactement un hint : $indices")
        assertEquals(": String", indices[0].libelle, "type d'élément de List<String>")
        val finIt = source.indexOf("var it") + "var it".length
        assertEquals(finIt, indices[0].offset, "ancre en fin du nom « it »")
    }

    @Test
    fun typedDeclarationNotHinted() {
        // Déclarations typées explicitement : AUCUN hint (le type est déjà
        // visible dans la source — un hint serait redondant).
        val source = sourceAvec(
            """
            int plain = 1;
            String s = "x";
            """,
        )
        assertEquals(0, indices(source).size, "0 hint pour les déclarations typées")
    }

    @Test
    fun varInStringLiteralNotHinted() {
        // « String s = "var x = 1;"; » — le mot var vit dans un LITTÉRAL :
        // l'analyse est structurelle (AST), pas textuelle.
        val source = sourceAvec("String s = \"var x = 1;\";")
        assertEquals(0, indices(source).size, "0 hint — le var textuel n'est pas une déclaration")
    }

    @Test
    fun anchorIsAtNameEnd() {
        // « var greeting = "hi"; » — l'offset du hint est JUSTE après
        // « greeting », AVANT le « = » : les 2 caractères à l'offset sont
        // «  = ».
        val source = sourceAvec("var greeting = \"hi\";")
        val indices = indices(source)
        assertEquals(1, indices.size, "exactement un hint : $indices")
        val hint = indices[0]
        val finNom = finNom(source, "greeting")
        assertEquals(finNom, hint.offset, "ancre en fin de nom, avant le « = »")
        assertEquals(" =", source.substring(hint.offset, hint.offset + 2), "les 2 caractères à l'offset sont «  = »")
        assertEquals(": String", hint.libelle)
    }
}

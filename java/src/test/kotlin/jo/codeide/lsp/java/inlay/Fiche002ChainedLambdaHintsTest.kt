package jo.codeide.lsp.java.inlay

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 002 — ChainedLambdaHintsTest (étage N3) : deux familles d'inlay
 * hints (parité `JdtInlayHintService` du projet tiers de référence) :
 *  - les CHAÎNING hints — sur un chaînage fluent multi-ligne, le type du
 *    récepteur (l'appel de la ligne précédente) est affiché à la fin de SA
 *    ligne, libellé = nom court SANS deux-points, padding gauche ;
 *    AUCUN hint pour le premier maillon (récepteur constructeur) ni sur
 *    une seule ligne ;
 *  - les hints de TYPE des paramètres lambda IMPLICITES — « : Type »
 *    collé à la fin du nom, SANS padding ; rien pour un paramètre typé.
 *
 * Le cycle LSP complet du domaine (initialize/initialized + didOpen) est
 * porté par le serveur de l'étage N8 ; le harnais verrouille le contrat du
 * MOTEUR (libellés, genres, paddings, ancres en ligne/colonne par le
 * repère du harnais).
 *
 * Anomalie de nommage du domaine consignée au cahier (fichier
 * `EcjChainedLambdaHintsTest.java`, classe interne
 * `JdtChainedLambdaHintsTest`) : la réécriture NOMME la classe d'après le
 * CONTRAT, sans préfixe Ecj/Jdt.
 */
class Fiche002ChainedLambdaHintsTest {

    private companion object {

        /** Moteur partagé (jrt paresseux, racines/jars vides — conformité 17). */
        val MOTEUR: MoteurJava by lazy { MoteurJava(racines = emptyList(), jars = emptyList()) }

        /** Chemin logique du document du domaine (file:///Demo.java). */
        const val CHEMIN = "/Demo.java"

        /** Ligne (0-based) et colonne (0-based) d'un offset. */
        fun position(source: String, offset: Int): Pair<Int, Int> {
            var retours = 0
            for (i in 0 until offset) {
                if (source[i] == '\n') retours++
            }
            val debutLigne = source.lastIndexOf('\n', offset - 1) + 1
            return retours to (offset - debutLigne)
        }
    }

    @Test
    fun multiLineChain_emitsReceiverTypeAtEachBoundary() {
        // Chaînage multi-ligne : StringBuilder("a") puis .append("b") /
        // .append("c") / .toString() chacun sur sa propre ligne. Exactement
        // 2 hints « StringBuilder » aux frontières .append("c")←.append("b")
        // et .toString()←.append("c") — ancres (3,24) et (4,24), fins de
        // ligne du récepteur. Le maillon .append("b") (récepteur
        // constructeur) ne reçoit PAS de hint.
        val source = """
            class Demo {
                void run() {
                    String r = new StringBuilder("a")
                        .append("b")
                        .append("c")
                        .toString();
                }
            }
        """.trimIndent()
        val indices = MOTEUR.inlayHints(CHEMIN, source)
        val chainages = indices.filter { it.libelle == "StringBuilder" }
        assertEquals(2, chainages.size, "exactement 2 hints de chaînage : $indices")
        val ancres = chainages.map { position(source, it.offset) }.toSet()
        assertEquals(setOf(3 to 24, 4 to 24), ancres, "ancres en fin de ligne du récepteur : $ancres")
        assertTrue(chainages.all { it.genre == GenreIndice.TYPE }, "kind Type")
        assertTrue(chainages.all { it.paddingGauche }, "paddingLeft = TRUE")
        assertTrue(chainages.none { it.paddingDroit }, "paddingRight ≠ TRUE")
        // Aucun autre indice ne pollue la fixture (aucun var, aucun appel
        // de méthode source à arguments — StringBuilder est binaire).
        assertEquals(2, indices.size, "la fixture ne porte QUE les 2 hints de chaînage : $indices")
    }

    @Test
    fun sameLineChain_emitsNoChainingHint() {
        // Même chaînage sur UNE seule ligne : aucun hint de chaînage (un
        // hint monoligne ne serait que du bruit visuel).
        val source = """
            class Demo {
                void run() {
                    String r = new StringBuilder("a").append("b").append("c").toString();
                }
            }
        """.trimIndent()
        val indices = MOTEUR.inlayHints(CHEMIN, source)
        assertEquals(0, indices.count { it.libelle == "StringBuilder" }, "0 hint de chaînage monoligne")
    }

    @Test
    fun implicitLambdaParam_emitsTypeHint() {
        // « list.forEach(x -> …) » sur une List<String> : exactement 1 hint
        // « : String », kind Type, SANS padding gauche NI droit (collé au
        // nom), ancré (4,22) — fin du paramètre x.
        val source = """
            import java.util.List;

            class Demo {
                void run(List<String> list) {
                    list.forEach(x -> System.out.println(x));
                }
            }
        """.trimIndent()
        val indices = MOTEUR.inlayHints(CHEMIN, source)
        assertEquals(1, indices.size, "exactement 1 hint : $indices")
        val hint = indices[0]
        assertEquals(": String", hint.libelle)
        assertEquals(GenreIndice.TYPE, hint.genre)
        assertEquals(4 to 22, position(source, hint.offset), "ancre (4,22) — fin du paramètre x")
        assertTrue(!hint.paddingGauche && !hint.paddingDroit, "collé au nom, sans padding")
    }

    @Test
    fun typedLambdaParam_emitsNoHint() {
        // « list.forEach((String x) -> …) » — paramètre typé
        // explicitement : aucun hint (le type est déjà visible).
        val source = """
            import java.util.List;

            class Demo {
                void run(List<String> list) {
                    list.forEach((String x) -> System.out.println(x));
                }
            }
        """.trimIndent()
        assertEquals(0, MOTEUR.inlayHints(CHEMIN, source).size, "0 hint pour un paramètre typé")
    }

    @Test
    fun twoImplicitLambdaParams_bothHinted() {
        // « BiConsumer<String, Integer> c = (a, b) -> … » — un hint « : String »
        // (paramètre a) ET un hint « : Integer » (paramètre b).
        val source = """
            import java.util.function.BiConsumer;

            class Demo {
                void run() {
                    BiConsumer<String, Integer> c = (a, b) -> System.out.println(a + b);
                }
            }
        """.trimIndent()
        val indices = MOTEUR.inlayHints(CHEMIN, source)
        val libelles = indices.map { it.libelle }.toSet()
        assertEquals(setOf(": String", ": Integer"), libelles, "un hint par paramètre implicite : $indices")
        assertEquals(2, indices.size, "exactement 2 hints")
    }
}

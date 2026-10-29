package jo.codeide.lsp.java.inlay

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 003 — InlayHintTest (étage N3) : le fournisseur d'inlay hints.
 * Verrouille : hints de type « : Type » pour les déclarations `var` dont
 * l'initialiseur est inférable (littéraux, création d'objet, retour de
 * méthode), RIEN pour les déclarations non-`var` ; hints de noms de
 * paramètres « nom: » aux sites d'appel de méthodes déclarées dans l'unité
 * (kind Parameter, libellé terminé par « : ») ; RIEN pour les appels sans
 * argument ou externes à l'unité ; PAS de filtrage par range côté serveur
 * (responsabilité de l'éditeur) ; liste volontairement NON triée (contrat
 * documenté — le client trie par colonne avant le tissage).
 *
 * Le cycle LSP complet du test du domaine (initialize/initialized +
 * didOpen sur `file:///Demo.java`, requête `inlayHint` avec range) est
 * porté par le serveur de l'étage N8 ; le présent harnais verrouille le
 * CONTRAT DU MOTEUR : contenu, genres, libellés, ancres (converties en
 * ligne/colonne par le repère du harnais — le serveur LSP convertira par
 * le repère du CLIENT, seule autorité).
 */
class Fiche003InlayHintTest {

    private companion object {

        /** Moteur partagé (jrt paresseux, racines/jars vides — conformité 17). */
        val MOTEUR: MoteurJava by lazy { MoteurJava(racines = emptyList(), jars = emptyList()) }

        /** Chemin logique du document du domaine (file:///Demo.java). */
        const val CHEMIN = "/Demo.java"

        /**
         * Fixture du domaine : déclarations var en ligne 2 (0-based), la
         * méthode n() déclarée dans l'unité, setName/doStuff aussi.
         */
        val SOURCE = """
            public class Demo {
                void run() {
                    var s = "hello";
                    var x = 42;
                    var b = true;
                    var l = 42L;
                    var list = new ArrayList<String>();
                    String t = "hello";
                    int n2 = 42;
                    var m = n();
                    setName("John", "Doe");
                    doStuff();
                    System.out.println("hello");
                }

                String n() { return ""; }

                void setName(String first, String last) {
                }

                public void doStuff() {
                }
            }
        """.trimIndent()

        /** Ligne (0-based) et colonne (0-based) d'un offset, par le repère du harnais. */
        fun position(source: String, offset: Int): Pair<Int, Int> {
            val ligne = source.count { ch -> ch == '\n' && source.indexOf(ch) < offset }
            // Comptage exact : nombre de sauts de ligne STRICTEMENT avant l'offset.
            var retours = 0
            for (i in 0 until offset) {
                if (source[i] == '\n') retours++
            }
            val debutLigne = source.lastIndexOf('\n', offset - 1) + 1
            return retours to (offset - debutLigne)
        }

        /** Offset de la première occurrence d'un motif. */
        fun offsetDe(source: String, motif: String): Int {
            val i = source.indexOf(motif)
            assertTrue(i >= 0, "motif absent : « $motif »")
            return i
        }
    }

    @Test
    fun inlayHint_stringLiteral_var_emitsStringTypeHint() {
        // « var s = "hello"; » en ligne 2 — exactement 1 hint « : String »
        // sur la ligne 2 (la colonne n'est contractuelle que « dans la
        // ligne et non négative »).
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE)
        val varS = indices.filter { it.libelle == ": String" && position(SOURCE, it.offset).first == 2 }
        assertEquals(1, varS.size, "un seul hint : String en ligne 2 — reçu : $indices")
        val (ligne, colonne) = position(SOURCE, varS[0].offset)
        assertEquals(2, ligne, "le hint vit sur la ligne du var")
        assertTrue(colonne >= 0, "colonne non négative")
        assertEquals(GenreIndice.TYPE, varS[0].genre, "kind Type")
    }

    @Test
    fun inlayHint_integerLiteral_emitsIntTypeHint() {
        // « var x = 42; » — exactement 1 hint « : int ».
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE).filter { it.libelle == ": int" }
        assertEquals(1, indices.size, "exactement un hint « : int » : ${MOTEUR.inlayHints(CHEMIN, SOURCE)}")
    }

    @Test
    fun inlayHint_booleanLiteral_emitsBooleanTypeHint() {
        // « var b = true; » — exactement 1 hint « : boolean ».
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE).filter { it.libelle == ": boolean" }
        assertEquals(1, indices.size, "exactement un hint « : boolean »")
    }

    @Test
    fun inlayHint_longLiteral_emitsLongTypeHint() {
        // « var l = 42L; » — exactement 1 hint « : long ».
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE).filter { it.libelle == ": long" }
        assertEquals(1, indices.size, "exactement un hint « : long »")
    }

    @Test
    fun inlayHint_objectCreation_emitsTypeNameHint() {
        // « var list = new ArrayList<String>(); » — 1 hint dont le libellé
        // contient « ArrayList » (type complet avec generics).
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE).filter { it.libelle.contains("ArrayList") }
        assertEquals(1, indices.size, "exactement un hint ArrayList : ${MOTEUR.inlayHints(CHEMIN, SOURCE)}")
    }

    @Test
    fun inlayHint_nonVarDeclaration_emitsNoHint() {
        // Source dédiée avec SEULEMENT des déclarations fortement typées
        // (la fixture du domaine) : 0 hint — le type est déjà visible.
        val source = """
            public class Demo {
                void run() {
                    String s = "hello";
                    int x = 42;
                }
            }
        """.trimIndent()
        assertEquals(0, MOTEUR.inlayHints(CHEMIN, source).size, "0 hint — types déjà visibles dans la source")
    }

    @Test
    fun inlayHint_methodCallInitializer_emitsBindingTypeHint() {
        // « var m = n(); » — 1 hint « : String » : le type de RETOUR réel de
        // l'appel, résolu par bindings (n() déclarée dans l'unité).
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE)
        val hints = indices.filter { it.genre == GenreIndice.TYPE && it.libelle == ": String" }
        // Deux var String au total (s et m) — le hint de m est ancré sur SA ligne.
        val ligneM = position(SOURCE, offsetDe(SOURCE, "var m = n();")).first
        val hintM = hints.filter { position(SOURCE, it.offset).first == ligneM }
        assertEquals(1, hintM.size, "le hint « : String » de m sur sa ligne : $indices")
    }

    @Test
    fun inlayHint_methodCall_emitsParameterNameHints() {
        // « setName("John", "Doe") » — exactement 2 hints Parameter, tous
        // deux terminés par « : » — résolution de la surcharge exacte.
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE)
        val params = indices.filter { it.genre == GenreIndice.PARAMETRE }
        assertEquals(2, params.size, "exactement 2 hints de paramètres : $indices")
        assertTrue(params.all { it.libelle.endsWith(":") }, "libellés terminés par « : » : ${params.map { it.libelle }}")
        val libelles = params.map { it.libelle }.toSet()
        assertEquals(setOf("first:", "last:"), libelles, "noms de la DÉCLARATION : ${params.map { it.libelle }}")
    }

    @Test
    fun inlayHint_noArgMethodCall_emitsNoHint() {
        // « doStuff() » — 0 argument : 0 hint.
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE)
        assertEquals(0, indices.count { it.genre == GenreIndice.PARAMETRE && it.libelle == "doStuff" }, "aucun hint pour doStuff()")
        // Plus précisément : aucun indice de paramètre ancré dans l'appel.
        val appel = offsetDe(SOURCE, "doStuff();")
        assertEquals(0, indices.count { it.genre == GenreIndice.PARAMETRE && it.offset in appel..appel + 10 }, "0 hint dans l'appel sans argument")
    }

    @Test
    fun inlayHint_externalMethodCall_emitsNoHint() {
        // « System.out.println("hello") » — méthode EXTERNE à l'unité
        // (PrintStream) : 0 hint (jamais de noms devinés).
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE)
        val appel = offsetDe(SOURCE, "System.out.println(\"hello\")")
        assertEquals(0, indices.count { it.genre == GenreIndice.PARAMETRE && it.offset in appel..appel + 30 }, "0 hint pour l'appel externe")
    }

    @Test
    fun inlayHint_emptyRange_returnsEmptyList() {
        // La requête du domaine porte une range qui n'inclut PAS la ligne 2 ;
        // le serveur ne filtre PAS par range : TOUS les hints de l'unité
        // reviennent (le filtrage est la responsabilité de l'éditeur). La
        // réponse est non nulle, sans crash — le harnais le documente par
        // l'absence totale de paramètre range dans l'API moteur.
        val indices = MOTEUR.inlayHints(CHEMIN, SOURCE)
        assertNotNull(indices, "réponse non nulle, sans crash")
        assertTrue(indices.isNotEmpty(), "les hints de TOUTE l'unité sont rendus (pas de filtrage serveur)")
        assertTrue(indices.any { position(SOURCE, it.offset).first == 2 }, "le hint de la ligne 2 est rendu même hors « range »")
    }

    @Test
    fun inlayHint_varAndCallOnSameLine_emitsBothHintsUnsorted() {
        // Ligne 3 = « var s = "hi"; greet(s); » — 2 hints dans cet ordre
        // exact : index 0 = Parameter « name: » ancré (3,28) (sur
        // l'argument s) ; index 1 = Type « : String » ancré (3,13) (fin du
        // nom s du var). Le caractère du premier est STRICTEMENT supérieur
        // à celui du second : la liste n'est PAS triée par position.
        val source = """
            package demo;
            class Demo {
                void run() {
                    var s = "hi"; greet(s);
                }
                void greet(String name) {
                }
            }
        """.trimIndent()
        val indices = MOTEUR.inlayHints(CHEMIN, source)
        assertEquals(2, indices.size, "2 hints exactement : $indices")
        val (genre0, libelle0, pos0) = Triple(indices[0].genre, indices[0].libelle, position(source, indices[0].offset))
        val (genre1, libelle1, pos1) = Triple(indices[1].genre, indices[1].libelle, position(source, indices[1].offset))
        assertEquals(GenreIndice.PARAMETRE, genre0, "index 0 = hint de paramètre")
        assertEquals("name:", libelle0, "libellé « name: »")
        assertEquals(3 to 28, pos0, "ancre (3,28) — sur l'argument s : $pos0")
        assertEquals(GenreIndice.TYPE, genre1, "index 1 = hint de type")
        assertEquals(": String", libelle1)
        assertEquals(3 to 13, pos1, "ancre (3,13) — fin du nom s du var : $pos1")
        assertTrue(pos0.second > pos1.second, "la liste n'est PAS triée par position — le client trie")
    }
}

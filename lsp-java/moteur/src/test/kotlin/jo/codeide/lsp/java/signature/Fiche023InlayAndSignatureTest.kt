package jo.codeide.lsp.java.signature

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.inlay.GenreIndice
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 023 — InlayAndSignatureTest (étage N3) : les inlay hints de
 * PARAMÈTRES et l'aide de signature sur bindings ecj.
 *
 * Inlays : un hint par argument (« nom: ») ancré avant l'argument, noms
 * issus de la DÉCLARATION — même fichier ou AUTRE fichier du projet
 * (racine source déclarée au moteur), résolution d'overload, et silence
 * total pour les méthodes binaires (jamais de faux noms).
 *
 * Signature help : énumération des surcharges (binaires comprises),
 * paramètre actif compté (virgules hors littéraux), noms de paramètres
 * source, et « niveau 4 » — le texte vivant CASSÉ (parenthèse ouverte,
 * virgule de fin, argument à moitié tapé, chaîne non refermée,
 * constructeur ouvert, booléen) est quand même servi via variantes
 * réparées ; hors appel, la réponse reste null.
 *
 * Les cas « niveau 4 » reproduisent le TEXTE VIVANT du domaine : la
 * frappe interrompt l'unité — le squelette OUVERT n'a plus ses
 * accolades fermantes, le caret vit à la FIN du texte, et la variante
 * réparée referme tout (listes d'arguments, instruction, corps).
 *
 * Anomalie de nommage du domaine consignée au cahier (fichier
 * `EcjInlayAndSignatureTest.java`, classe interne
 * `JdtInlayAndSignatureTest`) : la réécriture NOMME la classe d'après le
 * CONTRAT, sans préfixe Ecj/Jdt.
 */
class Fiche023InlayAndSignatureTest {

    private companion object {

        /** Chemin logique de l'unité principale. */
        const val CHEMIN = "/demo/Main.java"

        /** Helper du cahier — déclaré dans UN AUTRE FICHIER du projet. */
        val SOURCE_HELPER = """
            package demo;

            public class Helper {
                public int add(int first, int second) {
                    return first + second;
                }
            }
        """.trimIndent()

        /** Main du cahier : greet, area surchargée, toggle, corps injecté. */
        val SQUELETTE = """
            package demo;

            public class Main {
                void greet(String name, int times) {
                }

                int area(int size) {
                    return size * size;
                }

                int area(int width, int height) {
                    return width * height;
                }

                void toggle(boolean flag) {
                }

                void run() {
                    BLOC
                }
            }
        """.trimIndent()

        /**
         * Squelette OUVERT pour les cas « niveau 4 » : la frappe interrompt
         * l'unité — le corps de run() et la classe ne sont plus fermés,
         * le texte vivant S'ARRÊTE au caret.
         */
        val SQUELETTE_OUVERT = """
            package demo;

            public class Main {
                void toggle(boolean flag) {
                }

                void run() {
                    BLOC
        """.trimIndent()

        /** Source complète (squelette fermé) avec le corps injecté. */
        fun sourceAvec(bloc: String): String = SQUELETTE.replace("BLOC", bloc.trimIndent())

        /** Source vivante tronquée au caret (squelette ouvert). */
        fun sourceOuverte(bloc: String): String = SQUELETTE_OUVERT.replace("BLOC", bloc.trimIndent())

        /** Racine source temporaire avec demo/Helper.java écrit sur disque. */
        fun racineAvecHelper(): java.nio.file.Path {
            val racine = Files.createTempDirectory("fiche023")
            Files.createDirectories(racine.resolve("demo"))
            Files.writeString(racine.resolve("demo/Helper.java"), SOURCE_HELPER)
            return racine
        }

        /** Moteur avec la racine source déclarée (Helper visible), amorcé sur la source. */
        fun moteurAvec(source: String, racine: java.nio.file.Path? = null): MoteurJava {
            val moteur = MoteurJava(racines = listOf(racine ?: racineAvecHelper()), jars = emptyList())
            // Amorce l'environnement pour que la première requête ne paie
            // pas seule l'ouverture jrt (convention des harnais voisins).
            moteur.diagnose(CHEMIN, source)
            return moteur
        }

        /** Offset de la première occurrence d'un motif. */
        fun offsetDe(source: String, motif: String): Int {
            val i = source.indexOf(motif)
            assertTrue(i >= 0, "motif absent : « $motif »")
            return i
        }
    }

    // ------------------------------------------------------------------ //
    // Inlay hints de paramètres
    // ------------------------------------------------------------------ //

    @Test
    fun hintsForLocalMethodCall() {
        // greet("world", 3) — 2 hints « name: » et « times: », ancrés juste
        // avant CHAQUE argument.
        val bloc = "greet(\"world\", 3);"
        val source = sourceAvec(bloc)
        val indices = moteurAvec(source).inlayHints(CHEMIN, source)
        val params = indices.filter { it.genre == GenreIndice.PARAMETRE }
        assertEquals(2, params.size, "2 hints exactement : $indices")
        assertEquals(listOf("name:", "times:"), params.map { it.libelle }, "noms de la déclaration")
        val monde = offsetDe(source, "\"world\"")
        val trois = offsetDe(source, "3);")
        assertEquals(monde, params[0].offset, "« name: » ancré juste avant l'argument « world »")
        assertEquals(trois, params[1].offset, "« times: » ancré juste avant l'argument 3")
    }

    @Test
    fun hintsForMethodDeclaredInAnotherFile() {
        // h.add(10, 20) dans Main — les noms viennent de la déclaration de
        // demo/Helper.java, l'AUTRE fichier du projet.
        val source = sourceAvec(
            """
            Helper h = new Helper();
            h.add(10, 20);
            """,
        )
        val indices = moteurAvec(source).inlayHints(CHEMIN, source)
        val params = indices.filter { it.genre == GenreIndice.PARAMETRE }
        assertEquals(2, params.size, "2 hints exactement : $indices")
        assertEquals(listOf("first:", "second:"), params.map { it.libelle }, "noms depuis l'AUTRE fichier")
    }

    @Test
    fun noHintsForBinaryMethod() {
        // s.substring(1, 4) — méthode du jrt, BINAIRE : aucun hint (pas de
        // noms de paramètres disponibles → jamais de faux noms).
        val source = sourceAvec(
            """
            String s = "abcdef";
            s.substring(1, 4);
            """,
        )
        val indices = moteurAvec(source).inlayHints(CHEMIN, source)
        assertEquals(0, indices.count { it.genre == GenreIndice.PARAMETRE }, "AUCUN hint pour la méthode binaire : $indices")
    }

    @Test
    fun overloadResolutionPicksRightParameterNames() {
        // area(2, 3) — l'appel à 2 arguments résout vers area(int, int) :
        // hints « width: » et « height: » (pas ceux d'area(int)).
        val source = sourceAvec("area(2, 3);")
        val indices = moteurAvec(source).inlayHints(CHEMIN, source)
        val params = indices.filter { it.genre == GenreIndice.PARAMETRE }
        assertEquals(2, params.size, "2 hints exactement : $indices")
        assertEquals(listOf("width:", "height:"), params.map { it.libelle }, "résolution d'overload")
    }

    // ------------------------------------------------------------------ //
    // Signature help — énumération et paramètre actif
    // ------------------------------------------------------------------ //

    @Test
    fun signatureHelpListsBinaryOverloads() {
        // sb.append("hello") — ≥ 5 signatures (les surcharges de
        // StringBuilder.append), labels contenant « append( », paramètre
        // actif = 0 (caret dans le premier argument).
        val source = sourceAvec(
            """
            StringBuilder sb = new StringBuilder();
            sb.append("hello");
            """,
        )
        val moteur = moteurAvec(source)
        val caret = offsetDe(source, "\"hello\"") + 2
        val aide = moteur.signatureHelp(CHEMIN, source, caret)
        assertNotNull(aide, "aide servie sur l'appel binaire")
        assertTrue(aide.signatures.size >= 5, "≥ 5 surcharges énumérées : ${aide.signatures.size}")
        assertTrue(aide.signatures.all { it.libelle.contains("append(") }, "labels contenant « append( »")
        assertEquals(0, aide.parametreActif, "paramètre actif = 0")
    }

    @Test
    fun signatureHelpCountsActiveParameter() {
        // sb.append("a").append('b') — caret AVANT l'argument « 'b' » du
        // second appel : paramètre actif = 0.
        val source = sourceAvec(
            """
            StringBuilder sb = new StringBuilder();
            sb.append("a").append('b');
            """,
        )
        val moteur = moteurAvec(source)
        val caret = offsetDe(source, "'b'")
        val aide = moteur.signatureHelp(CHEMIN, source, caret)
        assertNotNull(aide, "aide servie sur le second appel")
        assertEquals(0, aide.parametreActif, "paramètre actif = 0 dans l'appel chaîné")
    }

    @Test
    fun signatureHelpSourceParameterNames() {
        // h.add(1, 2) — 1 signature exactement ; le label contient « first »
        // et « second » ; la liste des noms est EXACTEMENT [first, second].
        val source = sourceAvec(
            """
            Helper h = new Helper();
            h.add(1, 2);
            """,
        )
        val moteur = moteurAvec(source)
        val caret = offsetDe(source, "1, 2") + 1
        val aide = moteur.signatureHelp(CHEMIN, source, caret)
        assertNotNull(aide)
        assertEquals(1, aide.signatures.size, "1 signature exactement : ${aide.signatures.map { it.libelle }}")
        val signature = aide.signatures[0]
        assertTrue(signature.libelle.contains("first") && signature.libelle.contains("second"), "label : ${signature.libelle}")
        assertEquals(listOf("first", "second"), signature.parametres, "noms de paramètres source")
    }

    @Test
    fun signatureHelpNullOutsideCall() {
        // Caret sur « int x » (déclaration locale, pas dans un appel) : null.
        val source = sourceAvec("int x = 1;")
        val moteur = moteurAvec(source)
        val caret = offsetDe(source, "int x")
        assertNull(moteur.signatureHelp(CHEMIN, source, caret), "null hors appel")
    }

    @Test
    fun activeParameterCountsCommasNotInStrings() {
        // « run(a, "x,y", c); » — caret après « "x,y" » : la virgule DANS la
        // chaîne n'est pas un séparateur — index d'argument 1.
        val texte = "run(a, \"x,y\", c);"
        val debutListe = texte.indexOf('(') + 1
        val caret = texte.indexOf("\"x,y\"") + "\"x,y\"".length
        assertEquals(1, AideSignatures.compterParametreActif(texte, debutListe, caret), "virgules dans les chaînes ignorées")
    }

    // ------------------------------------------------------------------ //
    // Signature help niveau 4 — texte vivant cassé (squelette ouvert)
    // ------------------------------------------------------------------ //

    @Test
    fun signatureHelpSurvivesOpenParen() {
        // « sb.append( » sans fermeture, caret juste après la parenthèse
        // ouverte : la variante réparée referme l'appel — ≥ 5 signatures,
        // paramètre actif = 0.
        val source = sourceOuverte(
            """
            StringBuilder sb = new StringBuilder();
            sb.append(
            """,
        )
        val moteur = moteurAvec(source)
        val caret = source.length
        val aide = moteur.signatureHelp(CHEMIN, source, caret)
        assertNotNull(aide, "l'appel non refermé est quand même servi")
        assertTrue(aide.signatures.size >= 5, "≥ 5 signatures : ${aide.signatures.size}")
        assertEquals(0, aide.parametreActif, "paramètre actif = 0")
    }

    @Test
    fun signatureHelpSurvivesTrailingComma() {
        // « h.add(1, » non refermé (virgule traînante), caret après la
        // virgule : apposition d'un littéral de secours — paramètre actif
        // = 1 ; au moins une signature portant le sélecteur « add( ».
        val source = sourceOuverte(
            """
            Helper h = new Helper();
            h.add(1,
            """,
        )
        val moteur = moteurAvec(source)
        val caret = source.length
        val aide = moteur.signatureHelp(CHEMIN, source, caret)
        assertNotNull(aide, "la virgule traînante est servie")
        assertEquals(1, aide.parametreActif, "paramètre actif = 1")
        assertTrue(aide.signatures.any { it.libelle.contains("add(") }, "sélecteur « add( » présent : ${aide.signatures.map { it.libelle }}")
    }

    @Test
    fun signatureHelpSurvivesMidWordArgument() {
        // « sb.append("a").append(fi » — identifiant en cours de frappe :
        // substitution de l'argument — paramètre actif = 0, sélecteur
        // « append( » présent.
        val source = sourceOuverte(
            """
            StringBuilder sb = new StringBuilder();
            sb.append("a").append(fi
            """,
        )
        val moteur = moteurAvec(source)
        val caret = source.length
        val aide = moteur.signatureHelp(CHEMIN, source, caret)
        assertNotNull(aide, "l'argument à moitié tapé est servi")
        assertEquals(0, aide.parametreActif, "paramètre actif = 0")
        assertTrue(aide.signatures.any { it.libelle.contains("append(") }, "sélecteur « append( » : ${aide.signatures.map { it.libelle }}")
    }

    @Test
    fun signatureHelpSurvivesUnterminatedString() {
        // « sb.append("abc » — littéral ouvert : refermeture du littéral
        // puis de l'appel — paramètre actif = 0.
        val source = sourceOuverte(
            """
            StringBuilder sb = new StringBuilder();
            sb.append("abc
            """,
        )
        val moteur = moteurAvec(source)
        val caret = source.length
        val aide = moteur.signatureHelp(CHEMIN, source, caret)
        assertNotNull(aide, "la chaîne non refermée est servie")
        assertEquals(0, aide.parametreActif, "paramètre actif = 0")
        assertTrue(aide.signatures.size >= 5, "≥ 5 signatures : ${aide.signatures.size}")
    }

    @Test
    fun signatureHelpSurvivesOpenConstructor() {
        // « new StringBuilder( » sans fermeture : ≥ 3 constructeurs de
        // StringBuilder énumérés.
        val source = sourceOuverte("new StringBuilder(")
        val moteur = moteurAvec(source)
        val caret = source.length
        val aide = moteur.signatureHelp(CHEMIN, source, caret)
        assertNotNull(aide, "le constructeur non refermé est servi")
        assertTrue(aide.signatures.size >= 3, "≥ 3 constructeurs : ${aide.signatures.map { it.libelle }}")
    }

    @Test
    fun signatureHelpSurvivesBooleanParameter() {
        // « toggle( » ouvert, toggle(boolean flag) étant l'unique surcharge
        // (0 argument impossible) : substitution true — 1 signature
        // contenant « boolean ».
        val source = sourceOuverte("toggle(")
        val moteur = moteurAvec(source)
        val caret = source.length
        val aide = moteur.signatureHelp(CHEMIN, source, caret)
        assertNotNull(aide, "le booléen est servi par substitution")
        assertEquals(1, aide.signatures.size, "1 signature exactement : ${aide.signatures.map { it.libelle }}")
        assertTrue(aide.signatures[0].libelle.contains("boolean"), "la signature porte « boolean » : ${aide.signatures[0].libelle}")
    }

    @Test
    fun signatureHelpStillNullOutsideCall() {
        // « int x = 1 + » — expression incomplète, AUCUNE parenthèse d'appel
        // ouverte : null — le niveau 4 ne doit RIEN inventer hors d'une
        // liste d'arguments.
        val source = sourceOuverte("int x = 1 +")
        val moteur = moteurAvec(source)
        val caret = source.length
        assertNull(moteur.signatureHelp(CHEMIN, source, caret), "null — garde absolue hors appel")
    }
}

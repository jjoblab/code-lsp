package jo.codeide.lsp.java.survol

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 020 — HoverDefinitionTest, PARTIE HOVER & DÉFINITIONS (7
 * comportements sur 15 — étage N3) : hover sur déclarations et références
 * (texte contenant nom + types), et définition (plage de la DÉCLARATION
 * visée par une référence : locale, champ, méthode).
 *
 * La partie COMPLÉTION de la fiche (8 comportements) a été livrée à
 * l'étage N2 (Fiche020BindingCompletionTest) ; le présent harnais ferme la
 * fiche mixte.
 *
 * Moteur construit avec racines sources et classpath de jars VIDES,
 * conformité « 17 » — la plateforme (java.lang, java.util…) vient de
 * l'image jrt du JVM de test (convention du cahier).
 *
 * Anomalie de nommage du domaine consignée au cahier (fichier
 * `EcjHoverDefinitionCompletionTest.java`, classe interne
 * `JdtHoverDefinitionCompletionTest`) : la réécriture NOMME les classes
 * d'après le CONTRAT, sans préfixe Ecj/Jdt — convention du dépôt.
 */
class Fiche020HoverDefinitionTest {

    private companion object {

        /** Moteur partagé (jrt paresseux, racines/jars vides — conformité 17). */
        val MOTEUR: MoteurJava by lazy { MoteurJava(racines = emptyList(), jars = emptyList()) }

        /** Chemin logique de l'unité de la fiche. */
        const val CHEMIN = "/demo/Main.java"

        /** Fixture principale du cahier : champ counter, greet, getCounter. */
        val SOURCE = """
            package demo;

            public class Main {
                private int counter = 0;

                public String greet(String who) {
                    String message = "hello " + who;
                    return message;
                }

                public int getCounter() {
                    int total = counter + 1;
                    return total;
                }
            }
        """.trimIndent()

        /** Offset de la première occurrence d'un motif. */
        fun offsetDe(source: String, motif: String): Int {
            val i = source.indexOf(motif)
            assertTrue(i >= 0, "motif absent : « $motif »")
            return i
        }
    }

    // ------------------------------------------------------------------ //
    // Hover — nom + types, sur déclarations ET références
    // ------------------------------------------------------------------ //

    @Test
    fun hoverOnMethodDeclaration() {
        // Caret sur « greet » de la DÉCLARATION (pas un appel).
        val offset = offsetDe(SOURCE, "greet(String who)")
        val hover = MOTEUR.hoverAt(CHEMIN, SOURCE, offset)
        assertNotNull(hover, "hover servi sur une déclaration de méthode")
        assertTrue(hover.contains("greet"), "contient le nom : $hover")
        assertTrue(hover.contains("String"), "contient le type de retour : $hover")
        // Le survol riche sert la même position (signature + documentation
        // null ici — aucune javadoc dans la fixture).
        val riche = MOTEUR.survolRiche(CHEMIN, SOURCE, offset)
        assertNotNull(riche)
        assertTrue(riche.signature.contains("greet") && riche.signature.contains("String"), riche.signature)
    }

    @Test
    fun hoverOnFieldReference() {
        // Caret sur « counter » dans « counter + 1 » — RÉFÉRENCE de champ.
        val offset = offsetDe(SOURCE, "counter + 1")
        val hover = MOTEUR.hoverAt(CHEMIN, SOURCE, offset)
        assertNotNull(hover)
        assertTrue(hover.contains("int"), "contient le type : $hover")
        assertTrue(hover.contains("counter"), "contient le nom : $hover")
    }

    @Test
    fun hoverOnStringMethodCall() {
        // Source avec « return s.length(); » — caret sur « length » (binaire).
        val source = SOURCE.replace(
            "String message = \"hello \" + who;",
            "String message = \"hello \" + who;\n                    int n = message.length();",
        )
        val offset = offsetDe(source, "message.length()") + "message.".length
        val hover = MOTEUR.hoverAt(CHEMIN, source, offset)
        assertNotNull(hover, "hover servi sur un appel binaire")
        assertTrue(hover.contains("length"), "contient le nom : $hover")
        assertTrue(hover.contains("int"), "contient le type de retour : $hover")
    }

    @Test
    fun hoverOutsideAnyNode() {
        // Offset 0 : début du « package » — aucun nœud hoverable → null.
        assertNull(MOTEUR.hoverAt(CHEMIN, SOURCE, 0), "hover NULL hors de tout nœud résolu")
    }

    // ------------------------------------------------------------------ //
    // Définitions — plage de la DÉCLARATION visée
    // ------------------------------------------------------------------ //

    @Test
    fun definitionOfLocalVariable() {
        // Caret sur « message » dans « return message » — la plage vise la
        // DÉCLARATION « String message ».
        val offset = offsetDe(SOURCE, "return message") + "return ".length
        val plage = MOTEUR.definition(CHEMIN, SOURCE, offset)
        assertNotNull(plage, "plage de définition servie")
        val debutDeclaration = offsetDe(SOURCE, "String message")
        assertTrue(
            plage.debutOffset >= debutDeclaration && plage.debutOffset < debutDeclaration + 50,
            "start dans [début de « String message », début+50) : ${plage.debutOffset} vs $debutDeclaration",
        )
    }

    @Test
    fun definitionOfField() {
        // Caret sur « counter » dans « counter + 1 » — la plage vise le
        // champ déclaré « private int counter ».
        val offset = offsetDe(SOURCE, "counter + 1")
        val plage = MOTEUR.definition(CHEMIN, SOURCE, offset)
        assertNotNull(plage)
        val debutDeclaration = offsetDe(SOURCE, "private int counter")
        assertTrue(
            plage.debutOffset >= debutDeclaration && plage.debutOffset < debutDeclaration + 25,
            "start dans [début de « private int counter », début+25) : ${plage.debutOffset} vs $debutDeclaration",
        )
    }

    @Test
    fun definitionOfMethodCall() {
        // Source avec « return decorate(name); » — la plage vise la méthode
        // déclarée « private String decorate ».
        val source = SOURCE.replace(
            "int total = counter + 1;",
            "int total = counter + 1;\n                    String d = decorate(\"x\");",
        ).replace(
            "public int getCounter() {",
            "private String decorate(String value) {\n                    return \"[\" + value + \"]\";\n                }\n\n                public int getCounter() {",
        )
        val offset = offsetDe(source, "decorate(\"x\")")
        val plage = MOTEUR.definition(CHEMIN, source, offset)
        assertNotNull(plage, "plage de définition de la méthode appelée")
        val debutDeclaration = offsetDe(source, "private String decorate")
        assertTrue(
            plage.debutOffset >= debutDeclaration && plage.debutOffset < debutDeclaration + 30,
            "start dans [début de « private String decorate », début+30) : ${plage.debutOffset} vs $debutDeclaration",
        )
        assertEquals("decorate", source.substring(plage.debutOffset, plage.finOffset), "la plage vise le sélecteur déclaré")
    }
}

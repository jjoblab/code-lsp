package jo.codeide.lsp.java.gabarits

import jo.codeide.lsp.java.MoteurJava
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Fiche 029 — EcjTemplatesTest (classe interne `JdtTemplatesTest` — portage
 * de Live/PostfixTemplates v1.3.0) : gabarits de complétion, trois niveaux —
 * unitaire (tiering de pertinence, scan du récepteur, prédicats sans
 * binding = conscience de type), intégration moteur (item SNIPPET avec
 * éditions additionnelles en offsets de la source originale) et API
 * (isSnippet, insertText, relevance).
 *
 * Sémantique clé (cahier) : les gabarits vivants sortent en position
 * d'INSTRUCTION et jamais en position d'expression ; les postfixes
 * respectent le type résolu du récepteur (`opt` référence, `if` booléen) ;
 * les éditions additionnelles ne chevauchent JAMAIS la plage principale.
 *
 * Anomalie de nommage du domaine consignée au cahier (fichier
 * `EcjTemplatesTest.java`, classe `JdtTemplatesTest`) : la réécriture
 * NOMME les classes d'après le CONTRAT, sans préfixe Ecj/Jdt.
 */
class Fiche029GabaritsTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            MOTEUR = MoteurJava(racines = emptyList(), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — gabarits ignorés")
        }

        /** Squelette : un corps de méthode où la LIGNE est insérée telle quelle. */
        fun sourceAvec(ligne: String): String = """
            package demo;

            public class Main {
                void f() {
                    String name = "x";
                    $ligne
                }
            }
        """.trimIndent() + "\n"

        /** Complétion à la fin de la ligne insérée. */
        fun completerFinDeLigne(ligne: String) = MOTEUR.completer(
            "/demo/Main.java",
            sourceAvec(ligne),
            sourceAvec(ligne).indexOf(ligne) + ligne.length,
        )
    }

    // ------------------------------------------------------------------ //
    // Niveau unitaire — gabarits vivants
    // ------------------------------------------------------------------ //

    /** liveTemplatesVidesAuCaretNu — caret nu : aucune live template. */
    @Test
    fun liveTemplatesVidesAuCaretNu() {
        assertEquals(emptyList(), GabaritsVivants.itemsPour(""))
        assertEquals(emptyList(), GabaritsVivants.itemsPour(null))
    }

    /** liveTemplatesTiering — tiering de pertinence exact/partiel. */
    @Test
    fun liveTemplatesTiering() {
        val items = GabaritsVivants.itemsPour("sout")
        assertEquals(2, items.size, "clé exacte + clé partielle — obtenu : $items")
        assertEquals("sout", items[0].gabarit.cle)
        assertEquals(GabaritsVivants.PERTINENCE_EXACTE, items[0].pertinence, "relevance exacte = 2500")
        assertEquals("soutv", items[1].gabarit.cle)
        assertEquals(GabaritsVivants.PERTINENCE_PARTIELLE, items[1].pertinence, "relevance partielle = 100")
        assertEquals("System.out.println(\$0);", items[0].gabarit.extrait, "snippet de « sout »")
    }

    /** liveTemplatesTabStopsLies — tab stops liés et placeholders. */
    @Test
    fun liveTemplatesTabStopsLies() {
        val fori = GabaritsVivants.itemsPour("fori").first().gabarit.extrait
        assertTrue("\${1:i}" in fori, "tab stop lié nommé — obtenu : $fori")
        val essai = GabaritsVivants.itemsPour("try").first().gabarit.extrait
        assertTrue("catch (\${1:Exception}" in essai, "placeholder dans le catch — obtenu : $essai")
    }

    /** positionInstruction — garde textuelle position instruction/expression. */
    @Test
    fun positionInstruction() {
        fun position(texte: String): Boolean {
            val debutCle = texte.indexOf("sout")
            return GabaritsVivants.estPositionInstruction(texte, debutCle)
        }
        assertTrue(position("void f() {\n    sout"), "tête de ligne après « { »")
        assertTrue(position("a(); sout"), "après « ; »")
        assertTrue(position("if (x) { sout"), "après « { »")
        assertTrue(position("sout"), "début de fichier")
        assertTrue(!position("x = sout"), "après « = » : expression")
        assertTrue(!position("foo(sout"), "dans des arguments")
        assertTrue(!position("x.sout"), "après un point : accès membre")
    }

    /** ancreImport — ancre d'insertion d'import. */
    @Test
    fun ancreImport() {
        val avecImport = "package a.b;\nimport java.util.Map;\n\npublic class C {\n}\n"
        assertEquals(35, AncreImport.ancre(avecImport), "juste après le DERNIER import (13 + 22)")
        val sansImport = "package a.b;\n\nclass C {\n}\n"
        assertEquals(13, AncreImport.ancre(sansImport), "juste après le package")
        val sansPaquet = "class C {\n}\n"
        assertEquals(0, AncreImport.ancre(sansPaquet), "début de fichier")
    }

    // ------------------------------------------------------------------ //
    // Niveau unitaire — postfixes
    // ------------------------------------------------------------------ //

    /** postfixScanDuRecepteur — scan du récepteur avant la clé. */
    @Test
    fun postfixScanDuRecepteur() {
        fun recepteur(texte: String): String? {
            val cle = texte.substringAfterLast('.')
            val debutCle = texte.length - cle.length
            return GabaritsPostfixes.scannerRecepteur(texte, debutCle)?.let { texte.substring(it.first, it.last + 1) }
        }
        assertEquals("list", recepteur("list.sout"))
        assertEquals("foo()", recepteur("foo().var"))
        assertEquals("arr[i]", recepteur("arr[i].for"))
        assertEquals("a.b().c", recepteur("a.b().c.sout"), "avant le DERNIER point")
        assertEquals("\"texte\"", recepteur("\"texte\".len"), "littéral de chaîne, guillemets compris")
    }

    /** postfixSansBindingConscienceDeType — postfixes sans binding : conscience de type. */
    @Test
    fun postfixSansBindingConscienceDeType() {
        val sout = GabaritsPostfixes.itemsPour("value.sout", 6, "sout", null)
        assertEquals(1, sout.size, "« value.sout » → exactement 1 item")
        assertEquals("System.out.println(value);\$0", sout[0].extrait)
        assertEquals(1, sout[0].editions.size)
        assertEquals(0, sout[0].editions[0].offset, "deleteStart = 0")
        assertEquals(6, sout[0].editions[0].longueur, "deleteEnd = 6 (récepteur jusqu'avant la clé)")

        val variable = GabaritsPostfixes.itemsPour("value.var", 6, "var", null)
        assertEquals(1, variable.size)
        assertTrue("\${1:" in variable[0].extrait, "snippet avec tab stop nommé — obtenu : ${variable[0].extrait}")
        assertTrue("= value;" in variable[0].extrait, "récepteur réinséré — obtenu : ${variable[0].extrait}")

        assertTrue(GabaritsPostfixes.itemsPour("value.opt", 6, "opt", null).isEmpty(), "opt SANS type résolu → NON proposée (isReference)")
        assertEquals(1, GabaritsPostfixes.itemsPour("value.cast", 6, "cast", null).size, "cast (ANY_VALUE) → proposée")
        assertTrue(GabaritsPostfixes.itemsPour("sout", 4, "sout", null).isEmpty(), "sans point → aucune postfixe")
    }

    // ------------------------------------------------------------------ //
    // Niveau intégration moteur
    // ------------------------------------------------------------------ //

    /** liveTemplateEnPositionInstruction — live template servie en position d'instruction. */
    @Test
    fun liveTemplateEnPositionInstruction() {
        val reponse = completerFinDeLigne("sout")
        val items = reponse.items
        assertTrue(items.isNotEmpty(), "la complétion rend des items")
        val tete = items[0]
        assertEquals("sout", tete.libelle, "l'item « sout » est en TÊTE de la liste — ordre : ${items.take(3)}")
        assertTrue(tete.extrait, "isSnippet() vrai — kind SNIPPET")
        assertEquals("System.out.println(\$0);", tete.texteInsertion, "insertText")
        assertEquals(2500, tete.pertinence, "relevance 2500")
    }

    /** liveTemplateAbsenteEnPositionExpression — aucune template en position d'expression. */
    @Test
    fun liveTemplateAbsenteEnPositionExpression() {
        val reponse = completerFinDeLigne("int x = sout")
        assertTrue(
            reponse.items.none { it.extrait && it.libelle == "sout" },
            "aucune template d'instruction après « = » — obtenu : ${reponse.items.filter { it.extrait }}",
        )
    }

    /** postfixVarSurRecepteurResolu — `.var` conscient du type résolu. */
    @Test
    fun postfixVarSurRecepteurResolu() {
        val reponse = completerFinDeLigne("name.var")
        val item = reponse.items.firstOrNull { it.extrait && it.libelle == "var" }
        assertNotNull(item, "item « var » SNIPPET — obtenu : ${reponse.items}")
        assertEquals("String \${1:string} = name;\$0", item.texteInsertion, "type du binding, nom dérivé du type")
        assertEquals(1, item.editionsAdditionnelles.size, "1 édition additionnelle")
        val suppression = item.editionsAdditionnelles[0]
        assertEquals("", suppression.texte, "texte de remplacement vide")
        val debutNomVar = sourceAvec("name.var").indexOf("name.var")
        assertEquals(debutNomVar, suppression.offset, "suppression au début de « name.var »")
        assertEquals(5, suppression.longueur, "suppression de « name. » (5 caractères)")
        // La plage principale (la clé « var ») ne chevauche jamais l'édition.
        val debutCle = debutNomVar + 5
        assertTrue(
            suppression.offset + suppression.longueur <= debutCle,
            "l'édition additionnelle ne chevauche jamais la plage principale",
        )
    }

    /** postfixOptImporteOptionalToutSeul — `.opt` importe Optional. */
    @Test
    fun postfixOptImporteOptionalToutSeul() {
        val reponse = completerFinDeLigne("name.opt")
        val item = reponse.items.firstOrNull { it.extrait && it.libelle == "opt" }
        assertNotNull(item, "item « opt » — obtenu : ${reponse.items}")
        assertEquals("Optional.ofNullable(name)\$0", item.texteInsertion)
        assertEquals(2, item.editionsAdditionnelles.size, "2 éditions additionnelles : suppression + import")
        val import = item.editionsAdditionnelles.first { it.longueur == 0 }
        assertEquals("import java.util.Optional;\n", import.texte)
        val source = sourceAvec("name.opt")
        assertEquals(source.indexOf('\n') + 1, import.offset, "insertion zéro-largeur juste après la ligne package")
    }

    /** postfixBoolFiltreesParType — postfixes booléennes filtrées par type. */
    @Test
    fun postfixBoolFiltreesParType() {
        val booleen = MOTEUR.completer(
            "/demo/Main.java",
            sourceAvecBooleen(),
            sourceAvecBooleen().indexOf("ok.if") + 5,
        )
        val itemIf = booleen.items.firstOrNull { it.extrait && it.libelle == "if" }
        assertNotNull(itemIf, "sur booléen : item « if » — obtenu : ${booleen.items}")
        assertEquals("if (ok) {\n    \$0\n}", itemIf.texteInsertion)

        val chaine = MOTEUR.completer(
            "/demo/Main.java",
            sourceAvecChaine(),
            sourceAvecChaine().indexOf("ok.if") + 5,
        )
        assertTrue(
            chaine.items.none { it.extrait },
            "sur String : AUCUNE template postfixe proposée — obtenu : ${chaine.items.filter { it.extrait }}",
        )
    }

    /** lesTemplatesNePolluentPasLesContextesNonPrevus — anti-pollution. */
    @Test
    fun lesTemplatesNePolluentPasLesContextesNonPrevus() {
        // Caret nu (préfixe vide) en position d'instruction.
        val source = sourceAvec("")
        val nu = MOTEUR.completer("/demo/Main.java", source, source.indexOf("String name") - 4)
        assertTrue(nu.items.none { it.extrait }, "caret nu : aucun item isSnippet — obtenu : ${nu.items.filter { it.extrait }}")

        // Complétion dans « import java.util. » (contexte IMPORT).
        val sourceImport = "package demo;\n\nimport java.util.¦\n\nclass C {\n}\n".replace("¦", "")
        val reponse = MOTEUR.completer("/demo/Main.java", sourceImport, sourceImport.indexOf("import java.util.") + "import java.util.".length)
        assertTrue(
            reponse.items.none { it.extrait },
            "contexte import : aucun item isSnippet — obtenu : ${reponse.items.filter { it.extrait }}",
        )
        assertNull(reponse.items.firstOrNull { it.libelle == "sout" })
    }

    /** Source avec `boolean ok = true;` puis `ok.if`. */
    private fun sourceAvecBooleen(): String = """
        package demo;

        public class Main {
            void f() {
                boolean ok = true;
                ok.if
            }
        }
    """.trimIndent() + "\n"

    /** Source avec `String ok = "x";` puis `ok.if`. */
    private fun sourceAvecChaine(): String = """
        package demo;

        public class Main {
            void f() {
                String ok = "x";
                ok.if
            }
        }
    """.trimIndent() + "\n"
}

package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.diagnostics.ServiceDiagnostics
import jo.codeide.lsp.java.interne.NatureItem
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 028 — EcjSyntaxInstantTest : lot h4 « LSP instantané » — le régime
 * de repli quand la plateforme manque (classpath vide AVANT la première
 * sync, jdkHome sans `lib/modules`).
 */
class Fiche028RepliSyntaxeTest {

    /** Home factice SANS lib/modules : image jrt neutralisée. */
    private val fauxJdk: Path = Files.createTempDirectory("fiche028-fauxjdk")

    private fun moteurRepli(): MoteurJava = MoteurJava(jdkHome = fauxJdk)

    private val syntaxeCassee = """
        public class Test {
            public void m() {
                int x = 42
                System.out.println(x;
            }
        }
    """.trimIndent()

    private val saineNonResolue = """
        public class Test {
            public void m() {
                String s = "x";
                System.out.println(s);
            }
        }
    """.trimIndent()

    private val complet = """
        public class Main {
            private int compteurTotal = 0;

            public void premiere() {
                int localePremiere = 1;
                compteurTotal = compteurTotal + localePremiere;
            }

            public void seconde(String parametre) {
                int localeSeconde = 2;
                compteurTotal = compteurTotal + localeSeconde;
            }
        }
    """.trimIndent()

    @Test
    fun preconditionLeMoteurSansPlateformeEstInutilisable() {
        val moteur = moteurRepli()
        assertFalse(moteur.isLikelyUsable(), "classpath vide + jdkHome sans lib/modules : régime instantané")
    }

    @Test
    fun laSyntaxeCasseeRendSesErreursSansPlateforme() {
        val moteur = moteurRepli()
        val diagnostics = moteur.diagnoseSyntax("/Test.java", syntaxeCassee)
        assertTrue(
            diagnostics.any { it.severite == Severite.ERREUR },
            "au moins un diagnostic ERROR (garantie ①)",
        )
        for (diagnostic in diagnostics) {
            assertTrue(diagnostic.plage.debutOffset >= 0, "startOffset >= 0")
            assertTrue(diagnostic.plage.finOffset > diagnostic.plage.debutOffset, "endOffset > startOffset")
        }
    }

    @Test
    fun laSourceSaineNonResolueResteSilencieuse() {
        val moteur = moteurRepli()
        val diagnostics = moteur.diagnoseSyntax("/Test.java", saineNonResolue)
        assertEquals(0, diagnostics.size, "garantie ② : zéro « cannot be resolved » fabriqué par le repli")
    }

    @Test
    fun diagnoseSyntaxTriParOffset() {
        val moteur = moteurRepli()
        val diagnostics = moteur.diagnoseSyntax("/Test.java", syntaxeCassee)
        assertTrue(diagnostics.size >= 2, "les deux fautes sont signalées")
        assertEquals(diagnostics.map { it.plage.debutOffset }, diagnostics.map { it.plage.debutOffset }.sorted(), "tri par offset croissant")
    }

    @Test
    fun leServiceRendLaSyntaxeQuandLaPlateformeManque() {
        val environnement = EnvironnementCompilation(jdkHome = fauxJdk)
        val service = ServiceDiagnostics(environnement)
        val diagnostics = service.diagnose("Test.java", syntaxeCassee)
        assertTrue(
            diagnostics.any { it.severite == Severite.ERREUR },
            "le service sert la syntaxe en repli (≥ 1 ERROR)",
        )
    }

    @Test
    fun leServiceResteSilencieuxSurDuSainNonResolu() {
        val environnement = EnvironnementCompilation(jdkHome = fauxJdk)
        val service = ServiceDiagnostics(environnement)
        val diagnostics = service.diagnose("Test.java", saineNonResolue)
        assertEquals(0, diagnostics.size, "liste VIDE : le repli du service ne fabrique rien")
    }

    @Test
    fun laCompletionInstantaneeProposeLeChampFiltreParPrefixe() {
        val moteur = moteurRepli()
        val items = moteur.complete("/Main.java", complet, positionDe(complet, "compteurTotal = compteurTotal + localePremiere;") + 3)
        assertTrue(items.any { it.libelle == "compteurTotal" && it.nature == NatureItem.CHAMP }, "champ compteurTotal proposé (FIELD)")
        assertFalse(items.any { it.libelle == "premiere" && it.nature == NatureItem.METHODE }, "« premiere » non proposé : le préfixe filtre")
    }

    @Test
    fun laCompletionInstantaneeProposeLesLocalesDeLaMethodeEnglobante() {
        val moteur = moteurRepli()
        val items = moteur.complete("/Main.java", complet, positionDe(complet, "localeSeconde;") + 7)
        assertTrue(items.any { it.libelle == "localeSeconde" && it.nature == NatureItem.VARIABLE }, "localeSeconde proposée")
        assertFalse(items.any { it.libelle == "localePremiere" && it.nature == NatureItem.VARIABLE }, "localePremiere de l'AUTRE méthode non proposée")
    }

    @Test
    fun laCompletionInstantaneeProposeLesMotsClesDuLangage() {
        val moteur = moteurRepli()
        // « wh » tapé dans le corps de seconde() : le préfixe filtre les mots-clés.
        val avecWh = complet.replace("int localeSeconde = 2;", "int localeSeconde = 2; int wh")
        val position = avecWh.indexOf("int wh") + "int wh".length
        val items = moteur.complete("/Main.java", avecWh, position)
        assertTrue(items.any { it.libelle == "while" && it.nature == NatureItem.MOT_CLE }, "while (KEYWORD) proposé pour le préfixe « wh »")
    }

    @Test
    fun laCompletionInstantaneeNeDevineAucunTypeDuJdk() {
        val moteur = moteurRepli()
        // « Str » tapé dans un corps : le repli ne connaît que le fichier courant.
        val avecStr = complet.replace("int localeSeconde = 2;", "int localeSeconde = 2; int Str")
        val position = avecStr.indexOf("int Str") + "int Str".length
        val items = moteur.complete("/Main.java", avecStr, position)
        assertFalse(items.any { it.libelle == "String" }, "NI String proposé")
        assertFalse(items.any { it.libelle == "StringBuilder" }, "NI StringBuilder proposé — zéro devinette de type JDK")
    }

    @Test
    fun laCompletionInstantaneeProposeTypesEtMethodesDuFichier() {
        val moteur = moteurRepli()
        // Préfixe VIDE : caret à blanc juste avant « int localeSeconde » dans seconde().
        val position = complet.indexOf("int localeSeconde") - 1
        val items = moteur.complete("/Main.java", complet, position)
        assertTrue(items.any { it.libelle == "Main" && it.nature == NatureItem.CLASSE }, "Main (CLASS) présent")
        assertTrue(items.any { it.libelle == "seconde" && it.nature == NatureItem.METHODE }, "seconde (METHOD) présent")
        assertTrue(items.any { it.libelle == "premiere" && it.nature == NatureItem.METHODE }, "premiere (METHOD) présent")
        assertTrue(items.any { it.libelle == "compteurTotal" && it.nature == NatureItem.CHAMP }, "compteurTotal (FIELD) présent")
        assertTrue(items.any { it.libelle == "parametre" && it.nature == NatureItem.PARAMETRE }, "parametre (PARAMETER) présent")
    }

    @Test
    fun laCompletionInstantaneeNeRendJamaisNull() {
        val moteur = moteurRepli()
        assertNotNull(moteur.complete("/Main.java", "", 0), "source vide : une liste, jamais null")
        assertNotNull(moteur.complete("/Main.java", null, 0), "source null : une liste, jamais null")
        assertEquals(0, moteur.complete("/Main.java", "", 0).size, "liste éventuellement vide")
    }

    /** Offset du caret pour un préfixe donné : début de l'occurrence + longueur du préfixe. */
    private fun positionDe(source: String, morceau: String): Int = source.indexOf(morceau).coerceAtLeast(0)
}

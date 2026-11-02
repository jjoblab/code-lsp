package jo.codeide.lsp.java

import jo.codeide.lsp.java.actionscode.ModCommande
import jo.codeide.lsp.java.outils.appliquerEditions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vague V-D — INTENTIONS et GÉNÉRATION DE CODE (ADR 0027, écarts
 * G-014/G-015/G-020) : les ampoules SANS diagnostic au caret.
 *
 * Critères (ADR 0027 / plan 04) :
 *  - chaque capacité : WorkspaceEdit DORÉ (texte exact après application) ;
 *  - le résultat est COMPILABLE (zéro ERREUR au re-diagnose — esprit P5,
 *    sans diagnostic source : la propriété (c) seule s'applique) ;
 *  - les générateurs voyagent DIFFÉRÉS (clé + positions, éditions au
 *    resolve — ADR 0027 (f)) : la liste ne porte JAMAIS les gros textes ;
 *  - `organizeImports` exposé (M3 — G-020) : l'édition du bloc trié.
 */
class IntentionsEtGenerationTest {

    private fun moteurAvec(source: String): Pair<MoteurJava, String> {
        val racine = Files.createTempDirectory("vd-actions")
        Files.createDirectories(racine.resolve("demo"))
        val chemin = "/demo/Main.java"
        val fichier = racine.resolve("demo").resolve("Main.java")
        fichier.writeText(source)
        return MoteurJava(racines = listOf(racine)) to chemin
    }

    /** Le caret (offset) d'un marqueur § dans la source. */
    private fun caret(source: String): Int = source.indexOf("§")

    /** La source sans le marqueur. */
    private fun propre(source: String): String = source.replace("§", "")

    @Test
    fun `le getter et le setter se generent au caret d'un champ (dore + compilable)`() {
        val source = """
            package demo;
            public class Main {
                private String §nom = "Ada";
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val getter = actions.firstOrNull { it.kind == "source.generate.getter" }
        val setteur = actions.firstOrNull { it.kind == "source.generate.setter" }
        val couple = actions.firstOrNull { it.kind == "source.generate.getterSetteur" }
        assertTrue(getter != null && setteur != null && couple != null, "les trois actions sont servies : ${actions.map { it.kind }}")
        // DIFFÉRÉES : la liste ne porte PAS les éditions (ADR 0027 (f)).
        assertTrue(getter!!.editions.isEmpty() && getter.commande is ModCommande.Differee, "le générateur voyage différé")

        val editions = moteur.resoudreAction(chemin, propre(source), "generer.getter", argumentsDe(getter))
        assertTrue(editions != null && editions.isNotEmpty(), "le resolve rend les éditions du getter")
        val apres = appliquerEditions(propre(source), editions)
        assertEquals(
            """
            package demo;
            public class Main {
                private String nom = "Ada";
                public String getNom() {
                    return nom;
                }
            }
        """.trimIndent(),
            apres,
            "WorkspaceEdit doré du getter",
        )
        assertEquals(0, erreursDe(apres).size, "le getter généré compile")

        val editionsSetteur = moteur.resoudreAction(chemin, propre(source), "generer.getterSetteur", argumentsDe(couple!!))
        val apresDeux = appliquerEditions(propre(source), editionsSetteur!!)
        assertTrue(apresDeux.contains("public void setNom(String nom)"), "le couple porte le setter : $apresDeux")
        assertTrue(apresDeux.contains("this.nom = nom;"), "le setter assigne le champ")
        assertEquals(0, erreursDe(apresDeux).size, "getter+setter générés compilent")
    }

    @Test
    fun `le constructeur assigne les champs d'instance (dore + compilable)`() {
        val source = """
            package demo;
            public class §Main {
                private final String nom;
                private final int age;
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val constructeur = actions.firstOrNull { it.kind == "source.generate.constructor" }
        assertTrue(constructeur != null, "le constructeur est servi au nom du type : ${actions.map { it.kind }}")
        val editions = moteur.resoudreAction(chemin, propre(source), "generer.constructeur", argumentsDe(constructeur!!))
        val apres = appliquerEditions(propre(source), editions!!)
        assertTrue(apres.contains("public Main(String nom, int age) {"), "signature dorée : $apres")
        assertTrue(apres.contains("this.nom = nom;") && apres.contains("this.age = age;"), "assignations des champs")
        assertEquals(0, erreursDe(apres).size, "le constructeur généré compile (final assignés)")
    }

    @Test
    fun `equals hashCode et toString se generent (dore + compilable)`() {
        val source = """
            package demo;
            public class §Main {
                private String nom = "Ada";
                private int age = 36;
                private boolean actif = true;
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val egaliseur = actions.firstOrNull { it.kind == "source.generate.equalsHashcode" }
        val texte = actions.firstOrNull { it.kind == "source.generate.toString" }
        assertTrue(egaliseur != null && texte != null, "equals/hashCode et toString sont servis : ${actions.map { it.kind }}")

        val apresEgal = appliquerEditions(propre(source), moteur.resoudreAction(chemin, propre(source), "generer.equalsHashcode", argumentsDe(egaliseur!!))!!)
        assertTrue(apresEgal.contains("public boolean equals(Object autre)"), "signature equals")
        assertTrue(apresEgal.contains("Main cela = (Main) autre;"), "cast doré")
        assertTrue(apresEgal.contains("nom == cela.nom") || apresEgal.contains("java.util.Objects.equals(nom, cela.nom)"), "comparaison des champs")
        assertTrue(apresEgal.contains("java.util.Objects.hash(nom, age, actif)"), "hashCode par Objects.hash")
        assertEquals(0, erreursDe(apresEgal).size, "equals/hashCode générés compilent")

        val apresTexte = appliquerEditions(propre(source), moteur.resoudreAction(chemin, propre(source), "generer.toString", argumentsDe(texte!!))!!)
        assertTrue(apresTexte.contains("public String toString()"), "signature toString")
        assertTrue(apresTexte.contains("Main{nom=\" + nom + \", age=\" + age + \", actif=\" + actif + \"}\""), "corps doré : $apresTexte")
        assertEquals(0, erreursDe(apresTexte).size, "toString généré compile")
    }

    @Test
    fun `les stubs d'outrepassement implémentent les abstraites (compilable)`() {
        val source = """
            package demo;
            interface Forme {
                double aire();
                String nom();
            }
            public class §Main implements Forme {
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val stubs = actions.filter { it.kind == "source.generate.override" }
        assertEquals(2, stubs.size, "aire() et nom() manquantes : ${stubs.map { it.titre }}")
        val editions = moteur.resoudreAction(chemin, propre(source), "generer.outrepasser", argumentsDe(stubs[0]))
        val apres = appliquerEditions(propre(source), editions!!)
        assertTrue(apres.contains("@Override"), "le stub porte @Override")
        assertTrue(apres.contains("public double aire() {") || apres.contains("public String nom() {"), "signature du stub : $apres")
        assertTrue(apres.contains("// TODO : implémenter"), "le stub porte le marqueur TODO")
        // Avant l'édition : les DEUX abstraites manquent ; après UNE : la
        // couverte disparaît, l'autre reste (préexistante, pas nouvelle),
        // et AUCUNE erreur de syntaxe n'apparaît.
        val apresErreurs = erreursDe(apres)
        assertTrue(apresErreurs.none { it.message.contains("Syntax") || it.message.contains("record") }, "aucune erreur de syntaxe : ${apresErreurs.map { it.message }}")
        assertTrue(apresErreurs.none { it.message.contains("aire()") }, "aire() est couverte par le stub")
        assertTrue(apresErreurs.any { it.message.contains("nom()") }, "nom() reste à couvrir (l'autre stub)")
    }

    @Test
    fun `l'intention ternaire remplace un if else symetrique de retours`() {
        val source = """
            package demo;
            public class Main {
                String etiquette(boolean majeur) {
                    if (majeur) {
                        return "adulte";
                    } else {
                        return "enfant";
                    }
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source)
        val position = source.indexOf("if (majeur)")
        val actions = moteur.intentions(chemin, source, position, position)
        val ternaire = actions.firstOrNull { it.titre.contains("ternaire") }
        assertTrue(ternaire != null, "l'intention est servie : ${actions.map { it.titre }}")
        assertTrue(ternaire!!.editions.isNotEmpty(), "l'intention est EAGER (édits portés)")
        val apres = appliquerEditions(source, ternaire.editions)
        assertTrue(apres.contains("""return majeur ? "adulte" : "enfant";"""), "ternaire doré : $apres")
        assertEquals(0, erreursDe(apres).size, "le ternaire compile")
    }

    @Test
    fun `l'intention constante extrait un litteral au caret`() {
        val source = """
            package demo;
            public class Main {
                int plafond() {
                    return §42;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val constante = actions.firstOrNull { it.kind == "refactor.extract" && it.titre.contains("CONSTANTE_42") }
        assertTrue(constante != null, "l'extraction est servie : ${actions.map { it.titre }}")
        val apres = appliquerEditions(propre(source), constante!!.editions)
        assertTrue(apres.contains("private static final int CONSTANTE_42 = 42;"), "déclaration dorée : $apres")
        assertTrue(apres.contains("return CONSTANTE_42;"), "le littéral est remplacé")
        assertEquals(0, erreursDe(apres).size, "la constante compile")
    }

    @Test
    fun `l'intention reference de methode remplace la lambda a un parametre`() {
        val source = """
            package demo;
            import java.util.function.Function;
            public class Main {
                Function<String, Integer> longueurs() {
                    return s§ -> s.length();
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val reference = actions.firstOrNull { it.titre.contains("lambda") }
        assertTrue(reference != null, "l'intention est servie : ${actions.map { it.titre }}")
        val apres = appliquerEditions(propre(source), reference!!.editions)
        assertTrue(apres.contains("return String::length;"), "référence dorée : $apres")
        assertEquals(0, erreursDe(apres).size, "la référence de méthode compile")
    }

    @Test
    fun `l'intention var remplace le type exact et l'inverse le retape`() {
        val source = """
            package demo;
            public class Main {
                void go() {
                    String §salut = "bonjour";
                    var compte = 3;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val versVar = actions.firstOrNull { it.titre.contains("var") }
        assertTrue(versVar != null, "l'intention var est servie : ${actions.map { it.titre }}")
        val apres = appliquerEditions(propre(source), versVar!!.editions)
        assertTrue(apres.contains("var salut = \"bonjour\";"), "var doré : $apres")
        assertEquals(0, erreursDe(apres).size, "var compile (type exact String)")

        // L'inverse : le caret sur « var compte » retape explicitement.
        val positionVar = propre(source).indexOf("var compte")
        val actionsInverse = moteur.intentions(chemin, propre(source), positionVar, positionVar)
        val versExplicite = actionsInverse.firstOrNull { it.titre.contains("explicitement") }
        assertTrue(versExplicite != null, "l'intention inverse est servie : ${actionsInverse.map { it.titre }}")
        val apresInverse = appliquerEditions(propre(source), versExplicite!!.editions)
        assertTrue(apresInverse.contains("int compte = 3;"), "retypage doré : $apresInverse")
        assertEquals(0, erreursDe(apresInverse).size, "le type explicite compile")
    }

    @Test
    fun `organizeImports rend l'edition du bloc trie (M3 - G-020)`() {
        val source = """
            package demo;
            import java.util.Map;
            import java.util.List;
            public class Main {
                private List<String> noms;
                private Map<String, List<String>> index;
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source)
        val editions = moteur.organiserImportsEditions(chemin, source)
        assertTrue(editions != null && editions.size == 1, "UNE édition de bloc")
        val apres = appliquerEditions(source, editions!!)
        assertTrue(apres.indexOf("import java.util.List;") < apres.indexOf("import java.util.Map;"), "bloc trié : $apres")
        assertEquals(0, erreursDe(apres).size, "les imports organisés compilent")
    }

    @Test
    fun `sans applicable au caret la liste d'intentions est honnetement vide`() {
        val source = """
            package demo;
            public class Main {
                void go(int dynamique) {
                    int total = dynamique + 1;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source)
        val position = source.indexOf("void")
        assertEquals(0, moteur.intentions(chemin, source, position, position).size, "rien d'applicable : silence honnête")
    }

    // ------------------------------------------------------------------ //

    /** Les arguments sérialisés d'une commande différée. */
    private fun argumentsDe(action: jo.codeide.lsp.java.actionscode.ActionCode): List<String> =
        (action.commande as? ModCommande.Differee)?.arguments ?: emptyList()

    /** Les ERREUR ecj d'une source re-diagnostiquée (témoin neuf). */
    private fun erreursDe(source: String): List<jo.codeide.lsp.java.diagnostics.Diagnostic> {
        val racine = Files.createTempDirectory("vd-verifie")
        Files.createDirectories(racine.resolve("demo"))
        racine.resolve("demo").resolve("Main.java").writeText(source)
        val temoin = MoteurJava(racines = listOf(racine))
        return temoin.diagnose("/demo/Main.java", source).filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
    }
}

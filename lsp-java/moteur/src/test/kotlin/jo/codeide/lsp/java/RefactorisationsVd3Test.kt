package jo.codeide.lsp.java

import jo.codeide.lsp.java.actionscode.ModCommande
import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.outils.appliquerEditions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Vague V-D TRANCHE 3 (ADR 0027 (e) — « reste la tranche 3 ») : change
 * signature (suppression de paramètre + graphe des appelants
 * MULTI-FICHIERS), suppression HIÉRARCHIQUE (chaîne d'overrides),
 * inline de constante MULTI-FICHIERS, création de champ sur UndefinedField.
 *
 * Critères (plan 04 §V-D) :
 *  - chaque capacité : WorkspaceEdit DORÉ (texte exact après application,
 *    TOUS les fichiers touchés) ;
 *  - le résultat est COMPILABLE (zéro ERREUR au re-diagnose — esprit P5,
 *    chaque fichier re-diagnostiqué) ;
 *  - les refactorings voyagent DIFFÉRÉS (clé + positions — ADR 0027 (f)) ;
 *  - refus honnêtes (R1) : paramètre utilisé, varargs, hiérarchie,
 *    référence de méthode, usage bloquant la chaîne ;
 *  - l'oracle `changementSignatureSure` / `suppressionHierarchiqueSure`
 *    porte ses motifs.
 *
 * Marqueurs : `§` le caret simple.
 */
class RefactorisationsVd3Test {

    /** Le moteur sur une racine temporaire : Main.java + fichiers annexes. */
    private fun moteurAvec(source: String, vararg autres: Pair<String, String>): Pair<MoteurJava, String> {
        val racine = Files.createTempDirectory("vd3-refactorings")
        Files.createDirectories(racine.resolve("demo"))
        val chemin = "demo/Main.java"
        racine.resolve("demo").resolve("Main.java").writeText(source)
        for ((nom, texte) in autres) {
            racine.resolve("demo").resolve(nom).writeText(texte)
        }
        return MoteurJava(racines = listOf(racine)) to chemin
    }

    /** Le moteur sur des fichiers NOMMÉS (le nom PUBLIC doit suivre le fichier — mesuré). */
    private fun moteurFichiers(vararg fichiers: Pair<String, String>): MoteurJava {
        val racine = Files.createTempDirectory("vd3-fichiers")
        Files.createDirectories(racine.resolve("demo"))
        for ((nom, texte) in fichiers) {
            racine.resolve("demo").resolve(nom).writeText(texte)
        }
        return MoteurJava(racines = listOf(racine))
    }

    /** Le caret (offset) du marqueur § dans la source. */
    private fun caret(source: String): Int = source.indexOf("§")

    /** La source sans marqueur. */
    private fun propre(source: String): String = source.replace("§", "")

    /** Les arguments sérialisés d'une commande différée. */
    private fun argumentsDe(action: jo.codeide.lsp.java.actionscode.ActionCode): List<String> =
        (action.commande as? ModCommande.Differee)?.arguments ?: emptyList()

    /**
     * Applique une map d'éditions MULTI-FICHIERS à la map des sources
     * (chemins RELATIFS à la racine — `demo/X.java`).
     */
    private fun appliquerProjet(sources: Map<String, String>, editions: Map<String, List<EditionTexte>>): Map<String, String> {
        val resultat = HashMap(sources)
        for ((fichier, liste) in editions) {
            val relatif = fichier.substringAfter("/demo/")
            val ancien = resultat[relatif] ?: continue
            resultat[relatif] = appliquerEditions(ancien, liste)
        }
        return resultat
    }

    /** Les ERREURS de CHAQUE fichier, re-diagnostiqués sur un témoin NEUF. */
    private fun erreursDe(sources: Map<String, String>): Map<String, Int> {
        val racine = Files.createTempDirectory("vd3-verifie")
        Files.createDirectories(racine.resolve("demo"))
        for ((nom, texte) in sources) {
            racine.resolve("demo").resolve(nom).writeText(texte)
        }
        val temoin = MoteurJava(racines = listOf(racine))
        return sources.mapValues { (nom, texte) ->
            temoin.diagnose("demo/$nom", texte).count { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
        }
    }

    // ------------------------------------------------------------------ //
    // Change signature — suppression de paramètre
    // ------------------------------------------------------------------ //

    @Test
    fun `supprimer un parametre dore la signature et tous les appelants (multi-fichiers)`() {
        val source = """
            package demo;
            public class Main {
                int calcul(int a, int §inutilise) { return a * 2; }
                int appel() { return calcul(3, 0); }
            }
        """.trimIndent()
        val client = """
            package demo;
            public class Client {
                int appelDistant(Main m) { return m.calcul(4, 5); }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source), "Client.java" to client)
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val changement = actions.firstOrNull { it.kind == "refactor.rewrite.signature" }
        assertNotNull(changement, "le change signature est servi au caret du paramètre : ${actions.map { it.kind }}")
        val editions = moteur.resoudreActionProjet(chemin, propre(source), "refactor.supprimerParametre", argumentsDe(changement!!))
        assertNotNull(editions, "le resolve rend les éditions multi-fichiers")
        assertEquals(setOf("Main.java", "Client.java"), editions!!.keys.map { it.substringAfter("/demo/") }.toSet(), "la déclaration ET l'appelant distant sont retouchés")
        val apres = appliquerProjet(
            mapOf("Main.java" to propre(source), "Client.java" to client),
            editions,
        )
        assertEquals(
            """
            package demo;
            public class Main {
                int calcul(int a) { return a * 2; }
                int appel() { return calcul(3); }
            }
        """.trimIndent(),
            apres["Main.java"],
            "WorkspaceEdit doré : la signature ET l'appel local perdent le paramètre",
        )
        assertEquals(
            """
            package demo;
            public class Client {
                int appelDistant(Main m) { return m.calcul(4); }
            }
        """.trimIndent(),
            apres["Client.java"],
            "WorkspaceEdit doré : l'appelant DISTANT perd son argument",
        )
        assertEquals(mapOf("Main.java" to 0, "Client.java" to 0), erreursDe(apres), "le résultat compile partout")
    }

    @Test
    fun `supprimer le premier parametre dore les appels unaires restants`() {
        val source = """
            package demo;
            public class Main {
                int calcul(int §bruit, int utile) { return utile; }
                int appel() { return calcul(9, 8); }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val editions = moteur.resoudreActionProjet(chemin, propre(source), "refactor.supprimerParametre", listOf(position.toString()))
        assertNotNull(editions)
        val apres = appliquerProjet(mapOf("Main.java" to propre(source)), editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                int calcul(int utile) { return utile; }
                int appel() { return calcul(8); }
            }
        """.trimIndent(),
            apres["Main.java"],
            "le PREMIER argument emmène sa virgule suivante",
        )
        assertEquals(0, erreursDe(apres)["Main.java"], "le résultat compile")
    }

    @Test
    fun `supprimer le parametre unique dore un appel sans arguments`() {
        val source = """
            package demo;
            public class Main {
                int compteur(int §silence) { return 0; }
                int appel() { return compteur(1); }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val editions = moteur.resoudreActionProjet(chemin, propre(source), "refactor.supprimerParametre", listOf(position.toString()))
        assertNotNull(editions)
        val apres = appliquerProjet(mapOf("Main.java" to propre(source)), editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                int compteur() { return 0; }
                int appel() { return compteur(); }
            }
        """.trimIndent(),
            apres["Main.java"],
            "un argument SEUL part sans virgule (les parenthèses restent)",
        )
        assertEquals(0, erreursDe(apres)["Main.java"])
    }

    @Test
    fun `supprimer un parametre de constructeur retouche les sites new et this`() {
        val source = """
            package demo;
            public class Main {
                Main(int a, int §bruit) { }
                Main() { this(1, 2); }
                Main creer() { return new Main(3, 4); }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val editions = moteur.resoudreActionProjet(chemin, propre(source), "refactor.supprimerParametre", listOf(position.toString()))
        assertNotNull(editions, "les sites new et this(…) comptent comme appelants")
        val apres = appliquerProjet(mapOf("Main.java" to propre(source)), editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                Main(int a) { }
                Main() { this(1); }
                Main creer() { return new Main(3); }
            }
        """.trimIndent(),
            apres["Main.java"],
            "le constructeur, le this(…) et le new X(…) perdent l'argument",
        )
        assertEquals(0, erreursDe(apres)["Main.java"])
    }

    @Test
    fun `refuse le parametre utilise dans le corps (oracle motive)`() {
        val source = """
            package demo;
            public class Main {
                int calcul(int a, int §b) { return a + b; }
                int appel() { return calcul(3, 4); }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        // NOTE DE CONTRAT (complément de catalogue — ADR 0032) : le kind
        // refactor.rewrite.signature porte désormais AUSSI le
        // RÉORDONNANCEMENT (un paramètre LU se DÉPLACE avec ses lectures —
        // c'est la SUPPRESSION qui refuse) : la garde R1 se prouve sur le
        // TITRE de suppression, plus sur le kind seul.
        assertTrue(
            moteur.intentions(chemin, propre(source), position, position).none { it.kind == "refactor.rewrite.signature" && it.titre.startsWith("Supprimer") },
            "un paramètre LU dans le corps ne se SUPPRIME pas (R1)",
        )
        val verdict = moteur.changementSignatureSure(chemin, propre(source), position)
        assertNotNull(verdict)
        assertEquals("utilise", verdict!!.motifRefus, "l'oracle porte le motif")
        assertNull(verdict.editions)
    }

    @Test
    fun `refuse le parametre varargs et la methode d une hierarchie (oracle motive)`() {
        val source = """
            package demo;
            public class Main {
                void ecrire(String... §parties) { }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val verdict = moteur.changementSignatureSure(chemin, propre(source), position)
        assertNotNull(verdict)
        assertEquals("varargs", verdict!!.motifRefus, "le rang d'un argument varargs ne se sait pas à l'appel")

        val hierarchie = """
            package demo;
            public class Main extends Base {
                @Override int calc(int a, int §b) { return a; }
            }
        """.trimIndent()
        val base = """
            package demo;
            public abstract class Base {
                abstract int calc(int a, int b);
            }
        """.trimIndent()
        val moteur2 = moteurFichiers("Main.java" to propre(hierarchie), "Base.java" to base)
        val position2 = caret(hierarchie)
        val verdict2 = moteur2.changementSignatureSure("demo/Main.java", propre(hierarchie), position2)
        assertNotNull(verdict2)
        assertEquals("hierarchie", verdict2!!.motifRefus, "changer une méthode qui outrepasse casserait le contrat du supertype")
    }

    @Test
    fun `refuse la reference de methode qui fixe la forme (oracle motive)`() {
        val source = """
            package demo;
            public class Main {
                static int calcul(int a, int §bruit) { return a; }
                java.util.function.IntBinaryOperator ref() { return Main::calcul; }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val verdict = moteur.changementSignatureSure(chemin, propre(source), position)
        assertNotNull(verdict)
        assertEquals("referenceMethode", verdict!!.motifRefus, "un X::m fixe la forme de la méthode")
        assertNull(verdict.editions)
    }

    // ------------------------------------------------------------------ //
    // Suppression hiérarchique
    // ------------------------------------------------------------------ //

    @Test
    fun `supprimer une methode et sa hierarchie dore les deux maillons`() {
        val sub = """
            package demo;
            public class Sub extends Base {
                @Override
                void §obsolete() { }
            }
        """.trimIndent()
        val base = """
            package demo;
            public class Base {
                void obsolete() { }
            }
        """.trimIndent()
        val moteur = moteurFichiers("Sub.java" to propre(sub), "Base.java" to base)
        val chemin = "demo/Sub.java"
        val position = caret(sub)
        val actions = moteur.intentions(chemin, propre(sub), position, position)
        val hierarchique = actions.firstOrNull { it.kind == "refactor.rewrite.delete" }
        assertNotNull(hierarchique, "la suppression hiérarchique est servie au caret d'une méthode qui outrepasse : ${actions.map { it.kind }}")
        assertEquals("Supprimer « obsolete » et sa hiérarchie", hierarchique!!.titre)

        val verdict = moteur.suppressionHierarchiqueSure(chemin, propre(sub), position)
        assertNotNull(verdict)
        assertEquals(2, verdict!!.membres.size, "la chaîne porte la cible ET sa outrépassée")
        assertTrue(verdict.bloquants.isEmpty(), "aucun usage hors chaîne")
        val apres = appliquerProjet(
            mapOf("Sub.java" to propre(sub), "Base.java" to base),
            verdict.editions!!,
        )
        assertEquals(
            """
            package demo;
            public class Sub extends Base {
            }
        """.trimIndent(),
            apres["Sub.java"],
            "l'override quitte le sous-type",
        )
        assertEquals(
            """
            package demo;
            public class Base {
            }
        """.trimIndent(),
            apres["Base.java"],
            "la outrépassée quitte le supertype",
        )
        assertEquals(mapOf("Sub.java" to 0, "Base.java" to 0), erreursDe(apres), "le résultat compile (aucun @Override orphelin)")
    }

    @Test
    fun `la chaine d overrides refuse sur un usage hors chaine (oracle)`() {
        val sub = """
            package demo;
            public class Sub extends Base {
                @Override
                void §obsolete() { }
            }
        """.trimIndent()
        val base = """
            package demo;
            public class Base {
                void obsolete() { }
            }
        """.trimIndent()
        val client = """
            package demo;
            public class Client {
                void travail(Base b) { b.obsolete(); }
            }
        """.trimIndent()
        val moteur = moteurFichiers("Sub.java" to propre(sub), "Base.java" to base, "Client.java" to client)
        val chemin = "demo/Sub.java"
        val position = caret(sub)
        val verdict = moteur.suppressionHierarchiqueSure(chemin, propre(sub), position)
        assertNotNull(verdict)
        assertTrue(verdict!!.bloquants.isNotEmpty(), "l'appel via le supertype FERME la porte")
        assertNull(verdict.editions, "aucune édition servie sur blocage")
        assertNull(
            moteur.resoudreActionProjet(chemin, propre(sub), "refactor.supprimerHierarchique", listOf(position.toString())),
            "le resolve refuse honnêtement",
        )
    }

    @Test
    fun `le safe delete simple refuse la methode outrepassee par un sous-type (garde V-D3)`() {
        val base = """
            package demo;
            public class Base {
                void §obsolete() { }
            }
        """.trimIndent()
        val sub = """
            package demo;
            public class Sub extends Base {
                @Override
                void obsolete() { }
            }
        """.trimIndent()
        val moteur = moteurFichiers("Base.java" to propre(base), "Sub.java" to sub)
        val chemin = "demo/Base.java"
        val position = caret(base)
        val verdict = moteur.suppressionSure(chemin, propre(base), position)
        assertNotNull(verdict)
        assertEquals(
            "hierarchie",
            verdict!!.motifRefus,
            "la clé d'usages ne suit PAS la hiérarchie descendante : un maillon orphelin ne compile pas — refus motivé (garde V-D3)",
        )
        assertNull(verdict.editions)
    }

    // ------------------------------------------------------------------ //
    // Inline de constante MULTI-FICHIERS
    // ------------------------------------------------------------------ //

    @Test
    fun `mettre une constante en ligne a travers tout le projet`() {
        val source = """
            package demo;
            public class Main {
                public static final int §MAX = 100;
                int plafond() { return MAX; }
            }
        """.trimIndent()
        val client = """
            package demo;
            public class Client {
                int plafondDistant() { return Main.MAX; }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source), "Client.java" to client)
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val inline = actions.firstOrNull { it.kind == "refactor.inline.constant" }
        assertNotNull(inline, "l'inline de constante est servi : ${actions.map { it.kind }}")
        assertEquals("refactor.mettreEnLigneConstanteProjet", (inline!!.commande as ModCommande.Differee).cle, "la clé PROJET (multi-fichiers, V-D3)")

        val editions = moteur.resoudreActionProjet(chemin, propre(source), "refactor.mettreEnLigneConstanteProjet", argumentsDe(inline))
        assertNotNull(editions)
        val apres = appliquerProjet(mapOf("Main.java" to propre(source), "Client.java" to client), editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                int plafond() { return 100; }
            }
        """.trimIndent(),
            apres["Main.java"],
            "la déclaration quitte son fichier, l'usage local reçoit la valeur",
        )
        assertEquals(
            """
            package demo;
            public class Client {
                int plafondDistant() { return 100; }
            }
        """.trimIndent(),
            apres["Client.java"],
            "la référence QUALIFIÉE est remplacée ENTIÈRE (Main.MAX devient 100)",
        )
        assertEquals(mapOf("Main.java" to 0, "Client.java" to 0), erreursDe(apres))
    }

    @Test
    fun `une constante negative se met entre parentheses dans tous les fichiers`() {
        val source = """
            package demo;
            public class Main {
                public static final int §SEUIL = -42;
                int bas() { return SEUIL; }
            }
        """.trimIndent()
        val client = """
            package demo;
            public class Client {
                int produit() { return Main.SEUIL * 2; }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source), "Client.java" to client)
        val position = caret(source)
        val editions = moteur.resoudreActionProjet(chemin, propre(source), "refactor.mettreEnLigneConstanteProjet", listOf(position.toString()))
        assertNotNull(editions)
        val apres = appliquerProjet(mapOf("Main.java" to propre(source), "Client.java" to client), editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                int bas() { return (-42); }
            }
        """.trimIndent(),
            apres["Main.java"],
            "la valeur négative se met entre parenthèses (miroir tranche 2)",
        )
        assertEquals(
            """
            package demo;
            public class Client {
                int produit() { return (-42) * 2; }
            }
        """.trimIndent(),
            apres["Client.java"],
        )
        assertEquals(0, erreursDe(apres)["Client.java"])
    }

    // ------------------------------------------------------------------ //
    // Création de champ sur UndefinedField
    // ------------------------------------------------------------------ //

    @Test
    fun `creer le champ this pointe dans le fichier courant (type infere)`() {
        val source = """
            package demo;
            public class Main {
                void travail() { this.compteur = 3; }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source)
        val diagnostics = moteur.diagnose(chemin, source).filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
        val undefinedField = diagnostics.firstOrNull {
            it.code?.removePrefix("ecj:")?.toLongOrNull() == 33554502L
        }
        assertNotNull(undefinedField, "le diagnostic UndefinedField est servi : ${diagnostics.map { it.code }}")

        val actions = moteur.actionsCode(chemin, source, diagnostics)
        val creation = actions.firstOrNull { it.kind == "quickfix.create.field" }
        assertNotNull(creation, "la création de champ est servie sur UndefinedField : ${actions.map { it.kind }}")

        val editions = moteur.resoudreActionProjet(
            chemin, source,
            jo.codeide.lsp.java.actionscode.CorrecteurCreationChamp.CLE_CREER_CHAMP,
            argumentsDe(creation!!),
        )
        assertNotNull(editions)
        val apres = appliquerProjet(mapOf("Main.java" to source), editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                private int compteur = 0;
                void travail() { this.compteur = 3; }
            }
        """.trimIndent(),
            apres["Main.java"],
            "le champ naît en tête des membres, typé par la valeur affectée",
        )
        assertEquals(0, erreursDe(apres)["Main.java"], "le résultat compile (esprit P5)")
    }

    @Test
    fun `creer le champ d un recepteur distant ecrit dans le fichier du type declarant`() {
        val source = """
            package demo;
            public class Main {
                void travail(Util u) { u.total = 5; }
            }
        """.trimIndent()
        val util = """
            package demo;
            public class Util {
                int connu = 1;
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source, "Util.java" to util)
        val diagnostics = moteur.diagnose(chemin, source).filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
        val undefinedField = diagnostics.firstOrNull {
            it.code?.removePrefix("ecj:")?.toLongOrNull() == 33554502L
        }
        assertNotNull(undefinedField, "UndefinedField sur u.total : ${diagnostics.map { it.code }}")

        val actions = moteur.actionsCode(chemin, source, diagnostics)
        val creation = actions.firstOrNull { it.kind == "quickfix.create.field" }
        assertNotNull(creation)

        val editions = moteur.resoudreActionProjet(
            chemin, source,
            jo.codeide.lsp.java.actionscode.CorrecteurCreationChamp.CLE_CREER_CHAMP,
            argumentsDe(creation!!),
        )
        assertNotNull(editions, "l'édition vit dans le fichier du type DÉCLARANT")
        assertEquals(setOf("Util.java"), editions!!.keys.map { it.substringAfter("/demo/") }.toSet(), "SEUL le fichier déclarant est retouché")
        val apres = appliquerProjet(mapOf("Main.java" to source, "Util.java" to util), editions)
        assertEquals(
            """
            package demo;
            public class Util {
                int total = 0;
                int connu = 1;
            }
        """.trimIndent(),
            apres["Util.java"],
            "le champ naît dans Util — en tête des membres réels, de VISIBILITÉ PACKAGE (le récepteur vit hors du type)",
        )
        assertEquals(mapOf("Main.java" to 0, "Util.java" to 0), erreursDe(apres), "le résultat compile partout")
    }

    @Test
    fun `creer le champ refuse un recepteur binaire (refus honnete)`() {
        val source = """
            package demo;
            public class Main {
                void travail(String s) { s.longueurInconnue = 3; }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source)
        val diagnostics = moteur.diagnose(chemin, source).filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
        val undefinedField = diagnostics.firstOrNull {
            it.code?.removePrefix("ecj:")?.toLongOrNull() == 33554502L
        }
        assertNotNull(undefinedField, "UndefinedField sur un champ de java.lang.String : ${diagnostics.map { it.code }}")
        val actions = moteur.actionsCode(chemin, source, diagnostics)
        val creation = actions.firstOrNull { it.kind == "quickfix.create.field" }
        assertNotNull(creation, "l'action est servie (différée) — le refus se paie au resolve")
        assertNull(
            moteur.resoudreActionProjet(
                chemin, source,
                jo.codeide.lsp.java.actionscode.CorrecteurCreationChamp.CLE_CREER_CHAMP,
                argumentsDe(creation!!),
            ),
            "le type déclarant est BINAIRE : refus honnête (R1) — jamais de champ dans java.lang",
        )
    }
}

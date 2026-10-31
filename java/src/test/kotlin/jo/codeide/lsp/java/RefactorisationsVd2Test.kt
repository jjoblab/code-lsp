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
 * Vague V-D TRANCHE 2 — REFACTORINGS LOURDS (ADR 0027 (e) — écarts G-016
 * restants) : extract variable/constant/méthode sur SÉLECTION, inline
 * variable/constant, safe delete (usages + conflits, socle ADR 0028).
 *
 * Critères (ADR 0027 / plan 04, tranches 2) :
 *  - chaque capacité : WorkspaceEdit DORÉ (texte exact après application) ;
 *  - le résultat est COMPILABLE (zéro ERREUR au re-diagnose — esprit P5) ;
 *  - les refactorings voyagent DIFFÉRÉS (clé + positions — ADR 0027 (f)) ;
 *  - safe delete : conflits 0 MANQUÉ (oracle — les usages ferment la porte,
 *    multi-fichiers compris ; `new X(…)` et `X::m` comptent désormais) ;
 *  - refus honnêtes (R1) : expression paresseuse, deux sorties, variable
 *    réaffectée, override — l'action n'est PAS servie.
 *
 * Marqueurs : `§` le caret simple ; `⟦…⟧` la SÉLECTION (bornes incluses).
 */
class RefactorisationsVd2Test {

    private fun moteurAvec(source: String, vararg autres: Pair<String, String>): Pair<MoteurJava, String> {
        val racine = Files.createTempDirectory("vd2-refactorings")
        Files.createDirectories(racine.resolve("demo"))
        val chemin = "demo/Main.java"
        racine.resolve("demo").resolve("Main.java").writeText(source)
        for ((nom, texte) in autres) {
            val cheminRelatif = if (nom.contains('/')) nom.substringBeforeLast('/') else ""
            if (cheminRelatif.isNotEmpty()) {
                Files.createDirectories(racine.resolve("demo").resolve(cheminRelatif))
            }
            racine.resolve("demo").resolve(nom).writeText(texte)
        }
        return MoteurJava(racines = listOf(racine)) to chemin
    }

    /** Le caret (offset) d'un marqueur § dans la source. */
    private fun caret(source: String): Int = source.indexOf("§")

    /** La SÉLECTION (début, fin) des marqueurs ⟦…⟧ dans la source. */
    private fun selection(source: String): Pair<Int, Int> {
        val debut = source.indexOf("⟦")
        val fin = source.indexOf("⟧")
        return debut to fin - 1 // fin EXCLUE du marqueur, incluse dans la sélection
    }

    /** La source sans marqueurs. */
    private fun propre(source: String): String = source.replace("§", "").replace("⟦", "").replace("⟧", "")

    /** Les arguments sérialisés d'une commande différée. */
    private fun argumentsDe(action: jo.codeide.lsp.java.actionscode.ActionCode): List<String> =
        (action.commande as? ModCommande.Differee)?.arguments ?: emptyList()

    /** Les ERREURS ecj d'une source re-diagnostiquée (témoin neuf). */
    private fun erreursDe(source: String): List<jo.codeide.lsp.java.diagnostics.Diagnostic> {
        val racine = Files.createTempDirectory("vd2-verifie")
        Files.createDirectories(racine.resolve("demo"))
        racine.resolve("demo").resolve("Main.java").writeText(source)
        val temoin = MoteurJava(racines = listOf(racine))
        return temoin.diagnose("demo/Main.java", source).filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
    }

    // ------------------------------------------------------------------ //
    // Extract variable
    // ------------------------------------------------------------------ //

    @Test
    fun `extraire la variable dore et compile (selection d expression)`() {
        val source = """
            package demo;
            public class Main {
                public int travail(int a, int b) {
                    int y = ⟦a + b⟧;
                    return y;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val (debut, fin) = selection(source)
        val actions = moteur.intentions(chemin, propre(source), debut, fin)
        val extraction = actions.firstOrNull { it.kind == "refactor.extract.variable" }
        assertTrue(extraction != null, "l'extraction est servie sur sélection : ${actions.map { it.kind }}")
        val editions = moteur.resoudreAction(chemin, propre(source), "refactor.extraireVariable", argumentsDe(extraction!!))
        assertTrue(editions != null && editions.isNotEmpty(), "le resolve rend les éditions")
        val apres = appliquerEditions(propre(source), editions)
        assertEquals(
            """
            package demo;
            public class Main {
                public int travail(int a, int b) {
                    int nombre = a + b;
                    int y = nombre;
                    return y;
                }
            }
        """.trimIndent(),
            apres,
            "WorkspaceEdit doré de l'extraction de variable",
        )
        assertEquals(0, erreursDe(apres).size, "le résultat compile")
    }

    @Test
    fun `extraire la variable refuse l expression paresseuse (operande de et logique)`() {
        val source = """
            package demo;
            public class Main {
                public boolean valide(String s) {
                    return s != null && ⟦s.length() > 2⟧;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val (debut, fin) = selection(source)
        val actions = moteur.intentions(chemin, propre(source), debut, fin)
        assertTrue(
            actions.none { it.kind == "refactor.extract.variable" },
            "un opérande de && est en position paresseuse : refus honnête (R1) — ${actions.map { it.kind }}",
        )
    }

    @Test
    fun `extraire la variable refuse la condition de boucle (reevaluation)`() {
        val source = """
            package demo;
            public class Main {
                public void compte(java.util.List<String> xs) {
                    int n = 0;
                    while (⟦n < xs.size()⟧) {
                        n++;
                    }
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val (debut, fin) = selection(source)
        val actions = moteur.intentions(chemin, propre(source), debut, fin)
        assertTrue(
            actions.none { it.kind == "refactor.extract.variable" },
            "une condition de while ré-évaluée ne s'extrait pas en variable",
        )
    }

    // ------------------------------------------------------------------ //
    // Extract constante
    // ------------------------------------------------------------------ //

    @Test
    fun `extraire la constante dore et compile (literal selectionne)`() {
        val source = """
            package demo;
            public class Main {
                public int plafond() {
                    return ⟦100⟧;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val (debut, fin) = selection(source)
        val actions = moteur.intentions(chemin, propre(source), debut, fin)
        val extraction = actions.firstOrNull { it.kind == "refactor.extract.constant" }
        assertTrue(extraction != null, "l'extraction de constante est servie : ${actions.map { it.kind }}")
        val editions = moteur.resoudreAction(chemin, propre(source), "refactor.extraireConstante", argumentsDe(extraction!!))
        val apres = appliquerEditions(propre(source), editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                private static final int CONSTANTE_100 = 100;
                public int plafond() {
                    return CONSTANTE_100;
                }
            }
        """.trimIndent(),
            apres,
            "WorkspaceEdit doré de l'extraction de constante",
        )
        assertEquals(0, erreursDe(apres).size, "le résultat compile")
    }

    // ------------------------------------------------------------------ //
    // Extract méthode
    // ------------------------------------------------------------------ //

    @Test
    fun `extraire la methode depuis une expression dore et compile (entrees en parametres)`() {
        val source = """
            package demo;
            public class Main {
                public int aire(int largeur, int hauteur) {
                    int resultat = ⟦largeur * hauteur⟧;
                    return resultat;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val (debut, fin) = selection(source)
        val actions = moteur.intentions(chemin, propre(source), debut, fin)
        val extraction = actions.firstOrNull { it.kind == "refactor.extract.function" }
        assertTrue(extraction != null, "l'extraction de méthode (expression) est servie : ${actions.map { it.kind }}")
        val editions = moteur.resoudreAction(chemin, propre(source), "refactor.extraireMethode", argumentsDe(extraction!!))
        val apres = appliquerEditions(propre(source), editions!!)
        assertTrue(apres.contains("private int calculer(int largeur, int hauteur) {"), "signature dorée : $apres")
        assertTrue(apres.contains("return largeur * hauteur;"), "le corps rend l'expression")
        assertTrue(apres.contains("int resultat = calculer(largeur, hauteur);"), "l'appel remplace l'expression SUR PLACE")
        assertEquals(0, erreursDe(apres).size, "le résultat compile")
    }

    @Test
    fun `extraire la methode depuis des instructions dore et compile (sortie unique)`() {
        val source = """
            package demo;
            public class Main {
                public int total(int a, int b) {
                    int somme = 0;
                    ⟦somme = a + b;
                    somme = somme + 1;⟧
                    return somme;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val (debut, fin) = selection(source)
        val actions = moteur.intentions(chemin, propre(source), debut, fin)
        val extraction = actions.firstOrNull { it.kind == "refactor.extract.function" }
        assertTrue(extraction != null, "l'extraction de méthode (instructions) est servie : ${actions.map { it.kind }}")
        val editions = moteur.resoudreAction(chemin, propre(source), "refactor.extraireMethode", argumentsDe(extraction!!))
        val apres = appliquerEditions(propre(source), editions!!)
        assertTrue(apres.contains("private int calculer(int a, int b)"), "signature dorée (somme est ÉCRITE avant toute lecture : déclarée dedans, pas un paramètre) : $apres")
        assertTrue(apres.contains("int somme;"), "la locale écrite-d'abord est déclarée dans la méthode extraite : $apres")
        assertTrue(apres.contains("somme = calculer(a, b);"), "l'appel assigne la sortie (la locale déclarée DEHORS reste au site) : $apres")
        assertTrue(apres.contains("somme = a + b;") && apres.contains("somme = somme + 1;"), "les instructions extraites vivent dans la nouvelle méthode")
        assertTrue(apres.contains("return somme;"), "la sortie est retournée")
        assertEquals(0, erreursDe(apres).size, "le résultat compile")
    }

    @Test
    fun `extraire la methode refuse le controle de flux non local (return)`() {
        val source = """
            package demo;
            public class Main {
                public int garde(int x) {
                    ⟦if (x < 0) {
                        return 0;
                    }⟧
                    return x;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val (debut, fin) = selection(source)
        val actions = moteur.intentions(chemin, propre(source), debut, fin)
        assertTrue(
            actions.none { it.kind == "refactor.extract.function" },
            "un return dans la sélection est un contrôle non local : refus honnête (R1)",
        )
    }

    @Test
    fun `extraire la methode refuse deux sorties`() {
        val source = """
            package demo;
            public class Main {
                public int travail(int a, int b) {
                    int x = 0;
                    int y = 0;
                    ⟦x = a + 1;
                    y = b + 1;⟧
                    return x + y;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val (debut, fin) = selection(source)
        val actions = moteur.intentions(chemin, propre(source), debut, fin)
        assertTrue(
            actions.none { it.kind == "refactor.extract.function" },
            "deux sorties (x et y lues après) : refus honnête (heuristique §A5)",
        )
    }

    // ------------------------------------------------------------------ //
    // Inline variable
    // ------------------------------------------------------------------ //

    @Test
    fun `mettre en ligne une locale dore et compile (usage unique)`() {
        val source = """
            package demo;
            public class Main {
                public int calcul() {
                    int §deux = 2;
                    return 3 + deux;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val inline = actions.firstOrNull { it.kind == "refactor.inline.variable" }
        assertTrue(inline != null, "l'inline est servi au caret d'une locale : ${actions.map { it.kind }}")
        val editions = moteur.resoudreAction(chemin, propre(source), "refactor.mettreEnLigne", argumentsDe(inline!!))
        val apres = appliquerEditions(propre(source), editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                public int calcul() {
                    return 3 + 2;
                }
            }
        """.trimIndent(),
            apres,
            "WorkspaceEdit doré de l'inline (déclaration supprimée, usage remplacé)",
        )
        assertEquals(0, erreursDe(apres).size, "le résultat compile")
    }

    @Test
    fun `mettre en ligne refuse une locale reaffectee`() {
        val source = """
            package demo;
            public class Main {
                public int calcul() {
                    int §n = 2;
                    n = n + 1;
                    return n;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        assertTrue(
            actions.none { it.kind == "refactor.inline.variable" },
            "une locale réaffectée ne se met pas en ligne (sa valeur n'est pas l'initialisateur)",
        )
    }

    @Test
    fun `mettre en ligne refuse l initialisateur a effets en multi-usage`() {
        val source = """
            package demo;
            public class Main {
                int base = 1;
                public int calcul() {
                    int §n = suivant();
                    return n + n;
                }
                int suivant() { base++; return base; }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        assertTrue(
            actions.none { it.kind == "refactor.inline.variable" },
            "ré-évaluer un appel à effet deux fois changerait la sémantique : refus honnête",
        )
    }

    // ------------------------------------------------------------------ //
    // Inline constante
    // ------------------------------------------------------------------ //

    @Test
    fun `mettre en ligne une constante dore et compile (usages qualifies compris)`() {
        val source = """
            package demo;
            public class Main {
                public static final int §MAX = 100;
                public int plafond() {
                    return Main.MAX;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val inline = actions.firstOrNull { it.kind == "refactor.inline.constant" }
        assertTrue(inline != null, "l'inline de constante est servi : ${actions.map { it.kind }}")
        val editions = moteur.resoudreAction(chemin, propre(source), "refactor.mettreEnLigneConstante", argumentsDe(inline!!))
        val apres = appliquerEditions(propre(source), editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                public int plafond() {
                    return 100;
                }
            }
        """.trimIndent(),
            apres,
            "WorkspaceEdit doré de l'inline de constante (référence ENTIÈRE remplacée)",
        )
        assertEquals(0, erreursDe(apres).size, "le résultat compile")
    }

    @Test
    fun `mettre en ligne une constante refuse l usage hors du fichier courant`() {
        val autre = """
            package demo;
            public class Autre {
                public int plafond() {
                    return Main.MAX;
                }
            }
        """.trimIndent()
        val source = """
            package demo;
            public class Main {
                public static final int §MAX = 100;
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source), "Autre.java" to autre)
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        assertTrue(
            actions.any { it.kind == "refactor.inline.constant" },
            "la liste sert l'inline (la garde se paie au resolve)",
        )
        val editions = moteur.resoudreAction(chemin, propre(source), "refactor.mettreEnLigneConstante", listOf(position.toString()))
        assertTrue(editions == null, "un usage hors du fichier courant ferme la porte (mono-fichier en tranche 2)")
    }

    // ------------------------------------------------------------------ //
    // Safe delete — l'oracle des conflits
    // ------------------------------------------------------------------ //

    @Test
    fun `supprimer sans danger une methode inutilisee dore et compile`() {
        val source = """
            package demo;
            public class Main {
                public int travail() {
                    return 1;
                }
                private void §obsolete() {
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val verdict = moteur.suppressionSure(chemin, propre(source), position)
        assertTrue(verdict != null, "le verdict est rendu")
        assertTrue(verdict!!.usagesBloquants.isEmpty(), "aucun usage : rien ne bloque")
        val editions = verdict.editions
        assertTrue(editions != null && editions.isNotEmpty(), "les éditions de suppression sont servies")
        val apres = appliquerEditions(propre(source), editions)
        assertEquals(
            """
            package demo;
            public class Main {
                public int travail() {
                    return 1;
                }
            }
        """.trimIndent(),
            apres,
            "WorkspaceEdit doré de la suppression",
        )
        assertEquals(0, erreursDe(apres).size, "le résultat compile")
    }

    @Test
    fun `supprimer sans danger sert l action differree et le resolve bloque sur les usages`() {
        val source = """
            package demo;
            public class Main {
                public int travail() {
                    returnobsolete();
                }
                private int §obsolete() {
                    return 2;
                }
            }
        """.trimIndent().replace("returnobsolete", "return obsolete")
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val actions = moteur.intentions(chemin, propre(source), position, position)
        val suppression = actions.firstOrNull { it.kind == "refactor.rewrite.delete" }
        assertTrue(suppression != null, "l'action safe delete est servie au caret : ${actions.map { it.kind }}")
        // Le RESOLVE paie la garde : l'usage dans le fichier FERME la porte.
        val editions = moteur.resoudreAction(chemin, propre(source), "refactor.supprimerSure", argumentsDe(suppression!!))
        assertTrue(editions == null, "un usage ferme la porte au resolve (refus honnête)")
        // L'ORACLE : le verdict porte les usages bloquants.
        val verdict = moteur.suppressionSure(chemin, propre(source), position)!!
        assertEquals(1, verdict.usagesBloquants.size, "l'appel dans travail() bloque")
        assertTrue(verdict.editions == null, "pas d'éditions quand les usages ferment la porte")
    }

    @Test
    fun `supprimer sans danger bloque sur un usage dans un AUTRE fichier (multi-fichiers)`() {
        val autre = """
            package demo;
            public class Autre {
                public int appel() {
                    Main m = new Main();
                    return m.obsolete();
                }
            }
        """.trimIndent()
        val source = """
            package demo;
            public class Main {
                int §obsolete() {
                    return 3;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source), "Autre.java" to autre)
        val position = caret(source)
        val verdict = moteur.suppressionSure(chemin, propre(source), position)!!
        assertEquals(1, verdict.usagesBloquants.size, "l'usage multi-fichiers est détecté (0 manqué)")
        assertTrue(verdict.usagesBloquants[0].chemin.endsWith("Autre.java"), "l'usage bloquant vit dans l'autre fichier")
        assertTrue(verdict.editions == null, "la porte est fermée")
    }

    @Test
    fun `supprimer un constructeur bloque sur les sites new (extension V-D2 du socle usages)`() {
        val source = """
            package demo;
            public class Main {
                public §Main() {
                }
            }
        """.trimIndent()
        val autre = """
            package demo;
            public class Autre {
                public Main fabriquer() {
                    return new Main();
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source), "Autre.java" to autre)
        val position = caret(source)
        val verdict = moteur.suppressionSure(chemin, propre(source), position)!!
        assertTrue(verdict.usagesBloquants.isNotEmpty(), "le site new Main() compte pour le constructeur (0 manqué)")
        assertTrue(verdict.editions == null, "la porte est fermée")
    }

    @Test
    fun `supprimer une methode bloque sur une reference de methode (extension V-D2)`() {
        val source = """
            package demo;
            public class Main {
                int §creer() {
                    return 4;
                }
                public java.util.function.IntSupplier fournisseur() {
                    return this::creer;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val verdict = moteur.suppressionSure(chemin, propre(source), position)!!
        assertTrue(verdict.usagesBloquants.isNotEmpty(), "la référence de méthode this::creer compte (0 manqué)")
        assertTrue(verdict.editions == null, "la porte est fermée")
    }

    @Test
    fun `supprimer une methode qui outrepasse refuse honnetement (motif override)`() {
        val source = """
            package demo;
            interface Base {
                int taille();
            }
            public class Main implements Base {
                public int §taille() {
                    return 5;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val verdict = moteur.suppressionSure(chemin, propre(source), position)!!
        assertEquals("override", verdict.motifRefus, "le refus honnête porte son motif")
        assertTrue(verdict.editions == null && verdict.usagesBloquants.isEmpty(), "pas d'éditions, pas de fausse garde")
        // L'action n'est PAS servie (le resolve mourrait) — miroir liste.
        val actions = moteur.intentions(chemin, propre(source), position, position)
        assertTrue(actions.none { it.kind == "refactor.rewrite.delete" }, "pas d'action safe delete sur un override")
    }

    @Test
    fun `supprimer un parametre refuse honnetement (motif parametre)`() {
        val source = """
            package demo;
            public class Main {
                public int travail(int §inutilise) {
                    return 6;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val verdict = moteur.suppressionSure(chemin, propre(source), position)!!
        assertEquals("parametre", verdict.motifRefus, "le refus honnête porte son motif (change signature = tranche 3)")
    }

    @Test
    fun `supprimer un champ inutilise dore et compile`() {
        val source = """
            package demo;
            public class Main {
                private int §morte = 7;
                public int travail() {
                    return 8;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val verdict = moteur.suppressionSure(chemin, propre(source), position)!!
        assertTrue(verdict.usagesBloquants.isEmpty(), "aucun usage")
        val apres = appliquerEditions(propre(source), verdict.editions!!)
        assertEquals(
            """
            package demo;
            public class Main {
                public int travail() {
                    return 8;
                }
            }
        """.trimIndent(),
            apres,
            "WorkspaceEdit doré de la suppression du champ",
        )
        assertEquals(0, erreursDe(apres).size, "le résultat compile")
    }

    @Test
    fun `supprimer une locale inutilisee dore et compile`() {
        val source = """
            package demo;
            public class Main {
                public int travail() {
                    int §morte = 9;
                    return 10;
                }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(propre(source))
        val position = caret(source)
        val verdict = moteur.suppressionSure(chemin, propre(source), position)!!
        assertTrue(verdict.usagesBloquants.isEmpty(), "aucun usage")
        val apres = appliquerEditions(propre(source), verdict.editions!!)
        assertTrue(!apres.contains("morte"), "la locale est partie : $apres")
        assertEquals(0, erreursDe(apres).size, "le résultat compile")
    }
}

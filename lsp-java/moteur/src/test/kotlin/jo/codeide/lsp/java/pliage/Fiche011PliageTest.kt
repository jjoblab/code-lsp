package jo.codeide.lsp.java.pliage

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 011 — EcjCodeFolderTest : calcul des régions de pliage, parité du
 * plieur du projet de référence — groupe d'imports replié par défaut,
 * régions couvrant l'INTÉRIEUR des accolades (les `{}`/`()` restent
 * visibles), commentaires de bloc et javadoc multi-lignes, RIEN pour les
 * paires monolignes ni pour les accolades dans chaînes/commentaires/
 * blocs-texte, blocs de contrôle et corps de lambda pliables.
 *
 * Le pliage est une fonction PURE du texte (aucune racine, aucun
 * classpath — la fiche le verrouille : « le folding ne dépend d'aucune
 * résolution de type »).
 */
class Fiche011PliageTest {

    /** importGroupFoldedCollapsedByDefault — groupe d'imports replié par défaut. */
    @org.junit.jupiter.api.Test
    fun importGroupFoldedCollapsedByDefault() {
        val source = "import java.util.List;\nimport java.util.Map;\n\nclass A {\n}\n"
        val plis = ServicePliage.plis(source)
        val imports = plis.filter { it.genre == GenrePli.IMPORTS }
        assertEquals(1, imports.size, "exactement une fold imports")
        val pli = imports[0]
        assertEquals("import ...", pli.texteReplie, "placeholder contractuel")
        assertEquals(source.indexOf("import"), pli.debut, "start au « import » du PREMIER")
        val dernierImport = source.indexOf("import java.util.Map")
        assertEquals(source.indexOf(";", dernierImport) + 1, pli.fin, "end JUSTE APRÈS le « ; » du DERNIER import")
        assertTrue(pli.replieParDefaut, "le groupe d'imports est replié par défaut")
    }

    /** noImportFoldWithoutImports — pas d'imports, pas de fold. */
    @org.junit.jupiter.api.Test
    fun noImportFoldWithoutImports() {
        val source = "class A {\n    void run() {\n    }\n}\n"
        val plis = ServicePliage.plis(source)
        assertTrue(plis.none { it.genre == GenrePli.IMPORTS }, "aucune fold imports sans import")
    }

    /** bracePairsFoldInteriorOnly — accolades : intérieur seulement. */
    @org.junit.jupiter.api.Test
    fun bracePairsFoldInteriorOnly() {
        val source = "class A {\n    void run() {\n        int x = 1;\n    }\n}\n"
        val plis = ServicePliage.plis(source)
        val blocs = plis.filter { it.genre == GenrePli.BLOC }
        assertEquals(2, blocs.size, "le corps du type et celui de la méthode — obtenu : $plis")
        val ouvranteClasse = source.indexOf('{')
        val fermanteClasse = source.lastIndexOf('}')
        val ouvranteMethode = source.indexOf('{', ouvranteClasse + 1)
        val fermanteMethode = source.lastIndexOf('}', fermanteClasse - 1)
        for (bloc in blocs) {
            assertEquals(false, bloc.replieParDefaut, "un bloc n'est jamais replié par défaut")
            assertNull(bloc.texteReplie, "un bloc n'a pas de texte replié")
        }
        assertTrue(
            blocs.any { it.debut == ouvranteClasse + 1 && it.fin == fermanteClasse },
            "fold du type : start = ouvrante + 1, end = fermante (exclue)",
        )
        assertTrue(
            blocs.any { it.debut == ouvranteMethode + 1 && it.fin == fermanteMethode },
            "fold de la méthode : start = ouvrante + 1, end = fermante (exclue)",
        )
    }

    /** singleLineBracesNotFolded — paires monolignes ignorées. */
    @org.junit.jupiter.api.Test
    fun singleLineBracesNotFolded() {
        val source = "public class Main { void run() { int x = 1; } }"
        val plis = ServicePliage.plis(source)
        assertTrue(plis.isEmpty(), "liste de folds VIDE en monoligne — obtenu : $plis")
    }

    /** bracesInStringsAndCommentsIgnored — accolades dans chaînes / commentaires / blocs-texte. */
    @org.junit.jupiter.api.Test
    fun bracesInStringsAndCommentsIgnored() {
        val blocTexte = "\"\"\"\n" + "text { block } content\n" + "\"\"\""
        val source = "class A {\n    String s = \"{ not a fold\";\n    /* bloc\n       multi { lignes } */\n    String t = $blocTexte;\n    void run() {\n    }\n}\n"
        val plis = ServicePliage.plis(source)
        // La fold « comment » placeholder « slash-étoile…étoile-slash » existe.
        assertTrue(
            plis.any { it.genre == GenrePli.COMMENTAIRE && it.texteReplie == ServicePliage.TEXTE_COMMENTAIRE },
            "la fold comment existe — obtenu : $plis",
        )
        // Aucune fold ne démarre juste après l'accolade de la chaîne.
        val accoladeChaine = source.indexOf("{ not a fold")
        assertTrue(
            plis.none { it.debut == accoladeChaine + 1 },
            "aucune fold ne démarre juste après l'accolade de la chaîne",
        )
        // Aucune fold « block » ne se ferme sur l'accolade interne du bloc-texte.
        val accoladeInterne = source.indexOf("{ block }")
        assertTrue(
            plis.none { it.genre == GenrePli.BLOC && it.fin == accoladeInterne },
            "aucune fold block fermée sur l'accolade interne du bloc-texte",
        )
    }

    /** controlFlowAndLambdaBlocksFold — blocs if et lambda pliables. */
    @org.junit.jupiter.api.Test
    fun controlFlowAndLambdaBlocksFold() {
        val source = """
            import java.util.function.Consumer;

            class A {
                void run(int n) {
                    if (n > 0) {
                        System.out.println(n);
                    }
                    Consumer<Integer> c = valeur -> {
                        System.out.println(valeur);
                    };
                }
            }
        """.trimIndent() + "\n"
        val plis = ServicePliage.plis(source)
        val ouvranteIf = source.indexOf('{', source.indexOf("if ("))
        val ouvranteLambda = source.indexOf('{', source.indexOf("->"))
        assertTrue(
            plis.any { it.genre == GenrePli.BLOC && it.debut == ouvranteIf + 1 },
            "une fold block contient la position juste après l'accolade du if — obtenu : $plis",
        )
        assertTrue(
            plis.any { it.genre == GenrePli.BLOC && it.debut == ouvranteLambda + 1 },
            "une fold block contient la position juste après l'accolade du lambda",
        )
    }

    /** blockAndJavadocCommentsFold — javadoc et commentaires de bloc. */
    @org.junit.jupiter.api.Test
    fun blockAndJavadocCommentsFold() {
        val source = """
            /**
             * Javadoc de la classe.
             */
            class A {
                /* bloc
                   multi-lignes */
                void run() {
                }
            }
        """.trimIndent() + "\n"
        val plis = ServicePliage.plis(source)
        val javadoc = plis.first { it.genre == GenrePli.COMMENTAIRE && it.texteReplie == ServicePliage.TEXTE_JAVADOC }
        assertEquals(source.indexOf("/**"), javadoc.debut, "start au double-slash-étoile")
        assertEquals(source.indexOf("*/") + 2, javadoc.fin, "end = index de l'étoile-slash + 2 (borne incluse)")
        val bloc = plis.first { it.genre == GenrePli.COMMENTAIRE && it.texteReplie == ServicePliage.TEXTE_COMMENTAIRE }
        assertEquals(source.indexOf("/*", source.indexOf("class A")), bloc.debut, "start au slash-étoile du bloc")
    }

    /** lineCommentsNeverFold — commentaires de ligne jamais pliés. */
    @org.junit.jupiter.api.Test
    fun lineCommentsNeverFold() {
        val source = "class A {\n    // un commentaire\n    // un autre\n    void run() {\n    }\n}\n"
        val plis = ServicePliage.plis(source)
        assertTrue(plis.none { it.genre == GenrePli.COMMENTAIRE }, "aucune fold comment pour des lignes // — obtenu : $plis")
    }
}

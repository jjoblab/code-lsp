package jo.codeide.lsp.java.imports

import jo.codeide.lsp.java.MoteurJava
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Fiche 022 — EcjImportOrganizerTest : l'organisateur d'imports —
 * identification des FQN à importer depuis les bindings (types qualifiés
 * utilisés, superclasse), règles d'exemption (java.lang, même paquet,
 * on-demand existant), remplacement du bloc d'imports existant par un
 * bloc trié, insertion après le `package` quand il n'y a pas d'imports,
 * et robustesse au code cassé (aucun faux import).
 *
 * Moteur à racines/classpath VIDES (« 17 ») — les types java.* résolvent
 * via le jrt du JVM de test (fixture du cahier).
 */
class Fiche022OrganisateurImportsTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            MOTEUR = MoteurJava(racines = emptyList(), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — organisateur ignoré")
        }

        /** Classe demo.Main n'utilisant que java.lang et le même paquet. */
        val SOURCE_LANG_ET_PAQUET = """
            package demo;

            public class Main {
                String salut() {
                    return "x";
                }
            }
        """.trimIndent()

        /** Méthode retournant java.util.List<String> (usage QUALIFIÉ). */
        val SOURCE_QUALIFIE = """
            package demo;

            public class Main {
                public java.util.List<String> tous() {
                    return null;
                }
            }
        """.trimIndent()

        /** Source avec on-demand et usages de List/ArrayList non qualifiés. */
        val SOURCE_ON_DEMAND = """
            package demo;

            import java.util.*;

            public class Main {
                public List<String> tous() {
                    return new ArrayList<>();
                }
            }
        """.trimIndent()
    }

    /** javaLangAndSamePackageNeedNoImport — java.lang et même paquet : aucun import. */
    @Test
    fun javaLangAndSamePackageNeedNoImport() {
        val proposition = MOTEUR.organiserImports("/demo/Main.java", SOURCE_LANG_ET_PAQUET)
        assertNotNull(proposition, "résultat non null")
        assertEquals(0, proposition.fqcn.size, "liste d'imports VIDE — obtenu : ${proposition.fqcn}")
    }

    /** javaUtilTypeNeedsImport — type java.util identifié (usage qualifié). */
    @Test
    fun javaUtilTypeNeedsImport() {
        val proposition = MOTEUR.organiserImports("/demo/Main.java", SOURCE_QUALIFIE)
        assertNotNull(proposition)
        assertTrue(
            "java.util.List" in proposition.fqcn,
            "« java.util.List » figure dans les imports identifiés (dé-qualification possible) — obtenu : ${proposition.fqcn}",
        )
    }

    /** onDemandImportCoversPackage — l'on-demand couvre le paquet et est préservé. */
    @Test
    fun onDemandImportCoversPackage() {
        val proposition = MOTEUR.organiserImports("/demo/Main.java", SOURCE_ON_DEMAND)
        assertNotNull(proposition)
        assertEquals(listOf("java.util.*"), proposition.fqcn, "la liste est EXACTEMENT « java.util.* »")
        assertEquals("import java.util.*;\n", proposition.bloc, "le bloc d'imports est « import java.util.*;\\n »")
    }

    /** unqualifiedTypeFromOtherPackageIsImported — type d'un autre paquet importé. */
    @Test
    fun unqualifiedTypeFromOtherPackageIsImported() {
        val source = """
            package demo;

            public class Main {
                java.math.BigDecimal total = java.math.BigDecimal.ONE;
            }
        """.trimIndent()
        val proposition = MOTEUR.organiserImports("/demo/Main.java", source)
        assertNotNull(proposition)
        assertTrue("java.math.BigDecimal" in proposition.fqcn, "« java.math.BigDecimal » présent — obtenu : ${proposition.fqcn}")
    }

    /** replacesExistingBlockSorted — remplacement du bloc existant, trié. */
    @Test
    fun replacesExistingBlockSorted() {
        val source = """
            package demo;

            import java.util.List;
            import java.math.BigDecimal;

            public class Main {
                java.util.List<String> tous() { return null; }
                java.math.BigDecimal total = java.math.BigDecimal.ONE;
            }
        """.trimIndent()
        val proposition = MOTEUR.organiserImports("/demo/Main.java", source)
        assertNotNull(proposition)
        assertEquals(2, proposition.fqcn.size, "2 imports identifiés — obtenu : ${proposition.fqcn}")
        assertTrue(
            proposition.bloc.startsWith("import java.math.BigDecimal;\nimport java.util.List;"),
            "bloc trié alphabétiquement — obtenu : ${proposition.bloc}",
        )
        val premierImport = source.indexOf("import java.util.List")
        val dernierSemi = source.indexOf(';', source.indexOf("java.math.BigDecimal;"))
        assertTrue(
            proposition.debutRemplacement <= premierImport && proposition.finRemplacement >= dernierSemi,
            "la plage de remplacement couvre les imports existants : [${proposition.debutRemplacement}, ${proposition.finRemplacement})",
        )
    }

    /** insertionAfterPackageWhenNoImports — insertion juste après le package. */
    @Test
    fun insertionAfterPackageWhenNoImports() {
        val proposition = MOTEUR.organiserImports("/demo/Main.java", SOURCE_QUALIFIE)
        assertNotNull(proposition)
        val apresPackage = SOURCE_QUALIFIE.indexOf("package demo;") + "package demo;".length
        val avantClasse = SOURCE_QUALIFIE.indexOf("public class")
        assertTrue(
            proposition.debutRemplacement > apresPackage && proposition.debutRemplacement < avantClasse,
            "le point d'insertion vit après « package demo; » et avant « public class » : ${proposition.debutRemplacement}",
        )
        assertEquals(proposition.debutRemplacement, proposition.finRemplacement, "insertion zéro-largeur")
    }

    /** brokenCodeYieldsNoFalseImports — code cassé : aucun faux import. */
    @Test
    fun brokenCodeYieldsNoFalseImports() {
        val source = """
            package demo;

            public class Main {
                UndefinedThing champ = null;
                int x = inconnu + 1;
            }
        """.trimIndent()
        val proposition = MOTEUR.organiserImports("/demo/Main.java", source)
        if (proposition != null) {
            assertTrue(
                proposition.fqcn.none { "Undefined" in it || "inconnu" in it },
                "aucun FQN fabriqué sur un binding non résolu — obtenu : ${proposition.fqcn}",
            )
        }
    }

    /** superclassFromOtherPackageIsImported — la superclasse est importée. */
    @Test
    fun superclassFromOtherPackageIsImported() {
        val source = """
            package demo;

            public class Main extends java.util.TimerTask {
                public void run() {
                }
            }
        """.trimIndent()
        val proposition = MOTEUR.organiserImports("/demo/Main.java", source)
        assertNotNull(proposition)
        assertTrue("java.util.TimerTask" in proposition.fqcn, "« java.util.TimerTask » présent — obtenu : ${proposition.fqcn}")
    }
}

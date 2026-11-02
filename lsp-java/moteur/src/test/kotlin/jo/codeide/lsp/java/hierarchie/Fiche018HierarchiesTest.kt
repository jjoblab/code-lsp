package jo.codeide.lsp.java.hierarchie

import jo.codeide.lsp.java.MoteurJava
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fiche 018 — EcjHierarchiesTest : hiérarchies de TYPES et d'APPELS sur
 * un projet multi-fichiers réel écrit dans un répertoire temporaire
 * (racines sources = ce répertoire, conformité « 17 »).
 *
 * Fixture du cahier : `demo.Base` (méthodes `utilise(int)`, `log(int)`,
 * `fournis()`), `demo.Contrat` (interface `fournis()`), `demo.Enfant
 * extends Base implements Contrat`, `demo.Appelant` (appelle `b.utilise(3)`
 * et `b.utilise(4)` depuis `declenche()`).
 *
 * Invariants verrouillés : les éléments vivent dans les SOURCES du projet
 * (jamais d'URI factice pour un binaire — règle D3), les appels entrants
 * sont groupés par méthode appelante, les sortants résolus vers des
 * déclarations source (cibles binaires écartées).
 */
class Fiche018HierarchiesTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        lateinit var PROJET: Path

        val SOURCE_BASE = """
            package demo;

            public class Base {
                public int utilise(int n) {
                    log(n);
                    return n;
                }

                public void log(int n) {
                }

                public String fournis() {
                    return "x";
                }
            }
        """.trimIndent()

        val SOURCE_CONTRAT = """
            package demo;

            public interface Contrat {
                String fournis();
            }
        """.trimIndent()

        val SOURCE_ENFANT = """
            package demo;

            public class Enfant extends Base implements Contrat {
                @Override
                public String fournis() {
                    return "y";
                }
            }
        """.trimIndent()

        val SOURCE_APPELANT = """
            package demo;

            public class Appelant {
                public int declenche(Base b) {
                    b.utilise(3);
                    return b.utilise(4);
                }
            }
        """.trimIndent()

        fun texte(chemin: String): String = PROJET.resolve(chemin).toFile().readText()

        fun offsetDe(source: String, mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = source.indexOf(mot, position + 1) }
            return position
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            PROJET = Files.createTempDirectory("fiche018")
            val dossier = Files.createDirectories(PROJET.resolve("demo"))
            dossier.resolve("Base.java").writeText(SOURCE_BASE)
            dossier.resolve("Contrat.java").writeText(SOURCE_CONTRAT)
            dossier.resolve("Enfant.java").writeText(SOURCE_ENFANT)
            dossier.resolve("Appelant.java").writeText(SOURCE_APPELANT)
            MOTEUR = MoteurJava(racines = listOf(PROJET), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — hiérarchies ignorées")
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            MOTEUR.dispose()
        }
    }

    /** elementTypeAuCaretSurUneReference — type au caret sur une référence. */
    @Test
    fun elementTypeAuCaretSurUneReference() {
        val source = texte("demo/Enfant.java")
        val caret = offsetDe(source, "Base")
        val cible = MOTEUR.typeAuCaret("demo/Enfant.java", source, caret)
        assertNotNull(cible, "une cible de hiérarchie est retournée (caret sur « Base » de extends)")
        assertEquals("Base", cible.nomSimple, "nom simple « Base »")
        assertTrue(cible.chemin.toString().endsWith("demo/Base.java"), "SA source, pas d'URI factice — ${cible.chemin}")
        val texteBase = texte("demo/Base.java")
        assertEquals("Base", texteBase.substring(cible.plageNom.debutOffset, cible.plageNom.finOffset), "la plage nom vise le jeton « Base »")
        val declaration = texteBase.substring(cible.plageDeclaration.debutOffset, cible.plageDeclaration.finOffset)
        assertTrue("class Base" in declaration, "la plage déclaration couvre la classe")
    }

    /** supertypesDirectsClasseEtInterface — superclasse + interface directs. */
    @Test
    fun supertypesDirectsClasseEtInterface() {
        val source = texte("demo/Enfant.java")
        val caret = offsetDe(source, "Enfant")
        val supertypes = MOTEUR.supertypesDirects("demo/Enfant.java", source, caret)
        assertEquals(2, supertypes.size, "exactement 2 supertypes directs — obtenu : ${supertypes.map { it.nomSimple }}")
        val noms = supertypes.map { it.nomSimple }.toSet()
        assertTrue("Base" in noms, "superclasse « Base »")
        assertTrue("Contrat" in noms, "interface « Contrat »")
        for (superType in supertypes) {
            assertTrue("demo" in superType.chemin.toString(), "localisé dans les sources du projet — ${superType.chemin}")
        }
    }

    /** sousTypesViaBalayageProjet — sous-types par balayage des racines. */
    @Test
    fun sousTypesViaBalayageProjet() {
        val source = texte("demo/Base.java")
        val caret = offsetDe(source, "Base")
        val sousTypes = MOTEUR.sousTypes("demo/Base.java", source, caret)
        assertEquals(1, sousTypes.size, "exactement 1 sous-type — obtenu : ${sousTypes.map { it.nomSimple }}")
        assertEquals("Enfant", sousTypes[0].nomSimple)
        assertTrue(sousTypes[0].chemin.toString().endsWith("Enfant.java"), "balayage des racines sources")
    }

    /** typeBinaireAuCaretNullPasDUriFactice — pas de type projet au caret → null. */
    @Test
    fun typeBinaireAuCaretNullPasDUriFactice() {
        val source = texte("demo/Enfant.java")
        val caret = offsetDe(source, "Override")
        assertNull(
            MOTEUR.typeAuCaret("demo/Enfant.java", source, caret),
            "la cible de hiérarchie est NULL — jamais une URI factice pour un binaire",
        )
    }

    /** elementMethodeAuCaretSurUnAppel — méthode au caret sur un appel. */
    @Test
    fun elementMethodeAuCaretSurUnAppel() {
        val source = texte("demo/Appelant.java")
        val caret = offsetDe(source, "utilise")
        val cible = MOTEUR.methodeAuCaret("demo/Appelant.java", source, caret)
        assertNotNull(cible, "cible résolue et localisée")
        assertEquals("utilise", cible.nom)
        assertTrue(cible.chemin.toString().endsWith("Base.java"), "la déclaration vit dans Base.java")
        val texteBase = texte("demo/Base.java")
        assertEquals("utilise", texteBase.substring(cible.plageNom.debutOffset, cible.plageNom.finOffset), "plage nom sur le jeton dans Base")
    }

    /** appelsEntrantsGroupesParAppelant — appelants entrants groupés avec leurs sites. */
    @Test
    fun appelsEntrantsGroupesParAppelant() {
        val source = texte("demo/Base.java")
        val caret = offsetDe(source, "utilise")
        val entrants = MOTEUR.appelsEntrants("demo/Base.java", source, caret)
        assertEquals(1, entrants.size, "exactement 1 appelant — obtenu : ${entrants.map { it.nomMethode }}")
        val appelant = entrants[0]
        assertEquals("declenche", appelant.nomMethode, "méthode appelante « declenche »")
        assertTrue(appelant.chemin.toString().endsWith("Appelant.java"), "l'appelant vit dans Appelant.java")
        assertEquals(2, appelant.sites.size, "2 sites d'appel")
        for (site in appelant.sites) {
            assertTrue(site.startsWith("b.utilise"), "chaque site extrait commence par « b.utilise » — obtenu : $site")
        }
    }

    /** appelsSortantsResolusVersSourcesDuProjet — sortants résolus en source (D3). */
    @Test
    fun appelsSortantsResolusVersSourcesDuProjet() {
        val source = texte("demo/Appelant.java")
        val caret = offsetDe(source, "declenche")
        val sortants = MOTEUR.appelsSortants("demo/Appelant.java", source, caret)
        assertEquals(1, sortants.size, "exactement 1 cible sortante — obtenu : ${sortants.map { it.nom }}")
        val cible = sortants[0]
        assertEquals("utilise", cible.nom)
        assertEquals("demo.Base", cible.classeProprietaire, "classe propriétaire « demo.Base »")
        assertEquals(2, cible.sites.size, "2 sites d'appel")
        assertTrue(cible.sites.all { it.startsWith("b.utilise") }, "les sites vivent dans declenche")
    }

    /** pasDeMethodeAuCaretListesVides — pas de méthode au caret → liste vide. */
    @Test
    fun pasDeMethodeAuCaretListesVides() {
        val source = texte("demo/Base.java")
        val caret = offsetDe(source, "tu") // littéral « return n; » — sur « tu » de return
        val entrants = MOTEUR.appelsEntrants("demo/Base.java", source, caret)
        assertEquals(emptyList(), entrants, "la liste des appels entrants est VIDE (pas de méthode au caret)")
    }
}

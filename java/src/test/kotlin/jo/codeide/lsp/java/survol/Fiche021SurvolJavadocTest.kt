package jo.codeide.lsp.java.survol

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 021 — SurvolJavadocTest (étage N3) : le survol RICHE (lot S2-a) —
 * la signature du symbole au caret PLUS la javadoc de sa déclaration quand
 * elle vit dans la MÊME unité ; les symboles binaires n'apportent pas de
 * javadoc (périmètre « honnête » — signature seule). Verrouille aussi le
 * nettoyeur javadoc→markdown en unitaire, et le contrat historique du hover
 * texte (clôtures markdown, jamais de documentation).
 *
 * Le cycle LSP complet du test du domaine (initialize/initialized/didOpen)
 * est porté par le serveur de l'étage N8 ; le présent harnais verrouille
 * le CONTRAT du moteur (survol riche + nettoyeur), conformément au plan.
 *
 * Anomalie de nommage du domaine consignée au cahier (fichier
 * `EcjHoverJavadocTest.java`) : la réécriture NOMME la classe d'après le
 * CONTRAT, sans préfixe Ecj/Jdt — convention de nommage du dépôt (AGENTS.md).
 */
class Fiche021SurvolJavadocTest {

    private companion object {

        /** Moteur partagé (jrt paresseux, racines/jars vides — conformité 17). */
        val MOTEUR: MoteurJava by lazy { MoteurJava(racines = emptyList(), jars = emptyList()) }

        /** Chemin logique de l'unité de la fiche. */
        const val CHEMIN = "/demo/Main.java"

        /** Fixture du cahier : javadoc sur la classe, le champ, la méthode. */
        val SOURCE = """
            package demo;

            /**
             * Un compteur d'essais.
             */
            public class Main {

                /**
                 * Le total courant.
                 */
                private int total = 0;

                /**
                 * Additionne deux valeurs {@code a} et {@code b}.<p>valeurs.
                 *
                 * Les deux.
                 *
                 * @param a la première valeur
                 * @return la somme
                 */
                int somme(int a, int b) {
                    return a + b;
                }

                void utilise() {
                    int r = somme(total, total);
                    String s = "abc";
                    int n = s.length();
                }

                void pasDeDoc() {
                }
            }
        """.trimIndent()

        /** Offset de la PREMIÈRE occurrence d'un motif (assert explicite si absent). */
        fun offsetDe(motif: String): Int {
            val i = SOURCE.indexOf(motif)
            assertTrue(i >= 0, "motif absent de la fixture : « $motif »")
            return i
        }
    }

    // ------------------------------------------------------------------ //
    // Survol riche — références et déclarations retrouvent la javadoc
    // ------------------------------------------------------------------ //

    @Test
    fun referenceMethodeAvantArriereRetrouveLeJavadoc() {
        // Caret sur « somme( » — l'appel vit dans utilise(), la déclaration
        // PLUS HAUT dans le fichier : le registre des déclarations rend
        // l'ordre indifférent (référence avant/après déclaration).
        val survol = MOTEUR.survolRiche(CHEMIN, SOURCE, offsetDe("somme(total"))
        assertNotNull(survol, "survol riche servi sur l'appel")
        assertTrue(survol.signature.contains("somme"), "signature contient « somme » : ${survol.signature}")
        val doc = survol.documentation
        assertNotNull(doc, "la documentation de la déclaration est servie")
        assertTrue(doc.contains("Additionne deux valeurs"), "première ligne : $doc")
        assertTrue(doc.contains("`a` et `b`"), "{@code} converti en code span : $doc")
        assertTrue(doc.contains("@param a la première valeur"), "balises de bloc conservées verbatim : $doc")
        assertTrue(doc.contains("valeurs.\n\nLes deux"), "<p> devient un saut de paragraphe : ${doc.replace("\n", "\\n")}")
    }

    @Test
    fun referenceChampRetrouveSonJavadoc() {
        // Caret sur le PREMIER « total » argument — référence de champ.
        val survol = MOTEUR.survolRiche(CHEMIN, SOURCE, offsetDe("total, total"))
        assertNotNull(survol)
        assertTrue(survol.signature.contains("total"), "signature contient « total » : ${survol.signature}")
        assertEquals("Le total courant.", survol.documentation, "javadoc du champ servie")
    }

    @Test
    fun declarationClasseRetrouveSonJavadoc() {
        // Caret sur le nom « Main » de la déclaration de classe.
        val survol = MOTEUR.survolRiche(CHEMIN, SOURCE, offsetDe("Main {"))
        assertNotNull(survol)
        assertTrue(survol.signature.contains("Main"), "signature contient « Main » : ${survol.signature}")
        assertEquals("Un compteur d'essais.", survol.documentation, "javadoc de la classe servie")
    }

    @Test
    fun methodeSansJavadocDocumentationNulle() {
        // Caret sur « pasDeDoc » — déclaration SANS javadoc : la
        // documentation est NULL (distincte d'une chaîne vide).
        val survol = MOTEUR.survolRiche(CHEMIN, SOURCE, offsetDe("pasDeDoc"))
        assertNotNull(survol, "le survol riche est servi (signature seule)")
        assertTrue(survol.signature.contains("pasDeDoc"), "signature : ${survol.signature}")
        assertNull(survol.documentation, "documentation NULL sans javadoc — pas une chaîne vide")
    }

    @Test
    fun referenceBinaireSansDocumentation() {
        // Caret sur « length » de « s.length() » — symbole BINAIRE (jrt) :
        // signature seule, jamais de javadoc devinée.
        val survol = MOTEUR.survolRiche(CHEMIN, SOURCE, offsetDe("length"))
        assertNotNull(survol)
        assertTrue(survol.signature.contains("length"), "signature : ${survol.signature}")
        assertTrue(survol.signature.contains("int"), "type de retour dans la signature : ${survol.signature}")
        assertNull(survol.documentation, "périmètre limité au même fichier — le binaire n'apporte rien")
    }

    @Test
    fun hoverAtResteSousCloturesSansDocumentation() {
        // API historique : clôtures markdown, JAMAIS la documentation.
        val texte = MOTEUR.hoverAt(CHEMIN, SOURCE, offsetDe("somme(total"))
        assertNotNull(texte, "hoverAt servi")
        assertTrue(texte.startsWith("```java\n"), "commence par la clôture ouvrante : ${texte.take(20)}")
        assertTrue(texte.endsWith("\n```"), "termine par la clôture fermante : ${texte.takeLast(20)}")
        assertFalse(texte.contains("Additionne"), "hoverAt ne porte JAMAIS la javadoc")
    }

    // ------------------------------------------------------------------ //
    // Nettoyeur javadoc → markdown — unitaire
    // ------------------------------------------------------------------ //

    @Test
    fun nettoyeurCasLimites() {
        assertNull(NettoyeurJavadocMarkdown.nettoyer(null), "null en entrée")
        assertNull(NettoyeurJavadocMarkdown.nettoyer("   "), "blancs en entrée")
        assertNull(NettoyeurJavadocMarkdown.nettoyer("/***/"), "commentaire vide")
        assertEquals("x", NettoyeurJavadocMarkdown.nettoyer("/**x*/"), "contenu collé")
        assertEquals("x", NettoyeurJavadocMarkdown.nettoyer("/** x */"), "contenu espacé")
    }

    @Test
    fun nettoyeurAsterisquesDeMarge() {
        assertEquals(
            "Ligne une.\nLigne deux.",
            NettoyeurJavadocMarkdown.nettoyer("/**\n * Ligne une.\n * Ligne deux.\n */"),
            "astérisques de marge et marqueurs disparus",
        )
    }

    @Test
    fun nettoyeurBalisesInline() {
        assertEquals("Voir `Foo`.", NettoyeurJavadocMarkdown.nettoyer("/** Voir {@code Foo}. */"), "{@code} → code span")
        assertEquals("Voir `Foo.bar()`.", NettoyeurJavadocMarkdown.nettoyer("/** Voir {@link Foo#bar()}. */"), "{@link} → code span, dièse→point")
        assertEquals("Voir `Foo`.", NettoyeurJavadocMarkdown.nettoyer("/** Voir {@linkplain Foo}. */"), "{@linkplain} → code span")
        assertEquals("abc", NettoyeurJavadocMarkdown.nettoyer("/**{@literal abc}*/"), "{@literal} → verbatim")
        assertEquals("A\n\nB", NettoyeurJavadocMarkdown.nettoyer("/** A<p>B */"), "<p> → saut de paragraphe")
        assertEquals("A\nB", NettoyeurJavadocMarkdown.nettoyer("/** A<br>B */"), "<br> → saut de ligne")
        assertEquals("A\nB", NettoyeurJavadocMarkdown.nettoyer("/** A<br/>B */"), "<br/> → saut de ligne")
    }
}

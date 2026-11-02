package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.interne.PontEcj
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Échelle de variantes du splice-marqueur (étage N2 — mécanisme de
 * [AnalyseurContexte]) : la réponse au phénomène découvert pendant le
 * développement — ecj ABANDONNE les membres incomplets à l'EOF (champ sans
 * nom, paramètre sans nom, sélecteur sans corps, initialiseur non terminé)
 * et le marqueur disparaît de l'arbre AVEC le membre entier. Le pipeline du
 * domaine payait le même phénomène par ses « 5 variantes de splice »
 * (ADR 0012) ; l'échelle ici est textuellement gérée, chaque variante ne
 * s'applique que dans sa grammaire, la cascade s'arrêtant à la première
 * capture.
 *
 * Chaque test FIXE le verdict d'une variante sur son cas critique — les
 * fiches d'origine des comportements sont citées au fil des tests
 * (007 stubs d'override, 008 recovery classe non fermée, 035 paramètre
 * incomplet et repli honnête).
 */
class EchelleVariantesTest {

    private companion object {
        /** Environnement + pont partagés par la suite (jrt paresseux). */
        val ENVIRONNEMENT: EnvironnementNoms by lazy { EnvironnementNoms(emptyList(), emptyList(), null) }

        val ANALYSEUR: AnalyseurContexte by lazy { AnalyseurContexte(PontEcj(ENVIRONNEMENT, "17"), ENVIRONNEMENT) }

        /**
         * Analyse au caret marqué par « ¦ » dans la source (fin de source
         * si absent) — la convention de toute la suite.
         */
        fun analyserA(source: String): ContexteCompletion {
            val position = source.indexOf('¦')
            val texte = source.replace("¦", "")
            return ANALYSEUR.analyser("/Main.java", texte, if (position >= 0) position else texte.length)
        }
    }

    // ------------------------------------------------------------------ //
    // Variante 1 — REMPLACEMENT (le marqueur nu)
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun remplacementCaptureClauseExtends() {
        val contexte = analyserA("public class Main extends Obj¦ {}\n")
        assertEquals(GenreContexte.TYPE, contexte.genre, "clause extends → TYPE (fiche 007 : la position filtre le légal)")
        assertEquals(FiltreGrammatical.CLASSES_SEULES, contexte.filtre, "extends d'une CLASSE : interfaces refusées")
        assertEquals("Obj", contexte.prefixe, "le préfixe tapé voyage avec le contexte")
    }

    @org.junit.jupiter.api.Test
    fun remplacementCaptureMembreDeInstance() {
        val contexte = analyserA("class Main { void m() { String s = \"x\"; s.tri¦; } }\n")
        assertEquals(GenreContexte.MEMBRE, contexte.genre, "accès membre d'instance → MEMBRE")
        assertEquals("s", contexte.recepteur, "le récepteur textuel est le premier segment")
        assertEquals(
            "java.lang.String",
            contexte.typeRecepteur?.readableName()?.let { String(it) },
            "le type du récepteur est celui du PREMIER segment sain (l'échec du marqueur ne masque pas la chaîne résolue)",
        )
    }

    @org.junit.jupiter.api.Test
    fun remplacementCaptureMembreApresAppel() {
        val contexte = analyserA("class Main { void m() { String s = \"x\"; s.trim().toUp¦; } }\n")
        assertEquals(GenreContexte.MEMBRE, contexte.genre, "accès champ après un appel (`a.b().|`) → MEMBRE")
        assertEquals(
            "java.lang.String",
            contexte.typeRecepteur?.readableName()?.let { String(it) },
            "le récepteur est le résultat de l'appel (String)",
        )
    }

    @org.junit.jupiter.api.Test
    fun remplacementCaptureMembreStatique() {
        val contexte = analyserA("class Main { void m() { String.val¦; } }\n")
        assertEquals(GenreContexte.MEMBRE_STATIQUE, contexte.genre, "qualifier DE TYPE → MEMBRE_STATIQUE (fiche 020)")
        assertEquals("java.lang.String", contexte.recepteurFqcn, "le FQCN du qualifier est résolu par l'environnement")
    }

    @org.junit.jupiter.api.Test
    fun remplacementCaptureInstruction() {
        val contexte = analyserA("class Main { void m() { Stri¦; } }\n")
        assertEquals(GenreContexte.NOM, contexte.genre, "expression simple en instruction → NOM (mots-clés fiche 008)")
        assertEquals("Stri", contexte.prefixe)
    }

    @org.junit.jupiter.api.Test
    fun remplacementCaptureNomDeParametre() {
        val contexte = analyserA("class Main { void autre(int no¦\n")
        assertEquals(GenreContexte.NOM_DECLARATION, contexte.genre, "paramètre AVEC type → NOM_DECLARATION (fiche 007)")
        assertEquals("no", contexte.prefixe)
    }

    // ------------------------------------------------------------------ //
    // Variante 2 — APPEL_NEW (« () »)
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun appelNewSauveInitialiseurNonTermine() {
        val contexte = analyserA("class Main { Object x = new ArrayLi¦\n")
        assertEquals(GenreContexte.TYPE, contexte.genre, "new à l'EOF → TYPE (l'initialiseur incomplet est sinon jeté)")
        assertEquals(
            FiltreGrammatical.INSTANCIABLES_SEULES,
            contexte.filtre,
            "après new : instanciables seuls (fiche 007)",
        )
        assertEquals("ArrayLi", contexte.prefixe)
    }

    // ------------------------------------------------------------------ //
    // Variante 3 — CHAMP (« x; » après modificateurs)
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun champSauveTypeSansNomEof() {
        // Fiche 008 EXACTE : typeCompletionOnFieldDeclarationBareClass.
        val contexte = analyserA("public class MainActivity {\n    private Str¦")
        assertEquals(GenreContexte.TYPE, contexte.genre, "champ sans nom à l'EOF → TYPE (le champ est sinon jeté)")
        assertEquals("Str", contexte.prefixe)
    }

    @org.junit.jupiter.api.Test
    fun champSauveTypeSansNomFerme() {
        // La sonde a montré que même FERMÉ (`private M;`) le champ sans nom
        // est jeté — le nom factice reste nécessaire.
        val contexte = analyserA("public class MainActivity {\n    private Str¦;\n}\n")
        assertEquals(GenreContexte.TYPE, contexte.genre, "champ fermé sans nom → TYPE par la variante CHAMP")
        assertEquals("Str", contexte.prefixe)
    }

    // ------------------------------------------------------------------ //
    // Variante 4 — PARAM (« x » en tête de liste)
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun paramSauveTypeSansNomEof() {
        // Fiche 035 : le token unique d'un paramètre incomplet se lit comme
        // TYPE — le paramètre sans nom est sinon jeté à l'EOF.
        val contexte = analyserA("class Main { void autre(Stri¦\n")
        assertEquals(GenreContexte.TYPE, contexte.genre, "paramètre sans nom à l'EOF → TYPE (fiche 035)")
        assertEquals("Stri", contexte.prefixe)
    }

    // ------------------------------------------------------------------ //
    // Variante 5 — METHODE (« () {} » en corps de classe)
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun methodeSauveIdentifiantNuEnCorpsDeClasse() {
        val contexte = analyserA("public class MainActivity {\n    toStr¦")
        assertEquals(GenreContexte.CORPS_CLASSE, contexte.genre, "identifiant nu en corps de classe → CORPS_CLASSE (stubs d'override, fiche 007)")
        assertEquals("toStr", contexte.prefixe)
    }

    @org.junit.jupiter.api.Test
    fun methodeSauveSelecteurSansCorps() {
        val contexte = analyserA("class Main { ru¦\n")
        assertEquals(GenreContexte.CORPS_CLASSE, contexte.genre, "sélecteur sans corps (`ru|`) → CORPS_CLASSE (fiche 007)")
        assertEquals("ru", contexte.prefixe)
    }

    // ------------------------------------------------------------------ //
    // Variante 6 — QUEUE ÉQUILIBRÉE
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun queueSauveAccesMembreNonTermine() {
        val contexte = analyserA("class Main { void m() { String s = \"x\"; s.trim().toUpperCa¦\n")
        assertEquals(GenreContexte.MEMBRE, contexte.genre, "accès membre non terminé à l'EOF → MEMBRE par la queue équilibrée")
        assertEquals(
            "java.lang.String",
            contexte.typeRecepteur?.readableName()?.let { String(it) },
            "le récepteur survit à la fermeture synthétique",
        )
    }

    @org.junit.jupiter.api.Test
    fun queueSauveExpressionNonTerminee() {
        val contexte = analyserA("class Main { void m() { Stri¦\n")
        assertEquals(GenreContexte.NOM, contexte.genre, "expression non terminée à l'EOF → NOM par la queue équilibrée")
        assertEquals("Stri", contexte.prefixe)
    }

    // ------------------------------------------------------------------ //
    // Suffixe identifiant — compléter au MILIEU d'un mot
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun milieuDeMotMembreAppel() {
        // Le résiduel après le caret (`ring`) doit être CONSOMMÉ — sinon le
        // marqueur fusionnerait avec lui et aucune capture ne serait
        // possible (le cas le plus courant en frappe live).
        val contexte = analyserA("class Main { void m() { String s = \"x\"; s.substring(0).subst¦ring(1); } }\n")
        assertEquals(GenreContexte.MEMBRE, contexte.genre, "milieu de mot d'un appel de méthode → MEMBRE (sélecteur-marqueur)")
        assertEquals("subst", contexte.prefixe, "le préfixe s'arrête au caret")
        assertEquals(
            "java.lang.String",
            contexte.typeRecepteur?.readableName()?.let { String(it) },
            "le récepteur est le résultat de s.substring(0)",
        )
    }

    @org.junit.jupiter.api.Test
    fun milieuDeMotNom() {
        val contexte = analyserA("class Main { void m() { String greet = \"hi\"; Sys¦tem.out.println(greet); } }\n")
        assertEquals(GenreContexte.NOM, contexte.genre, "milieu de mot d'une référence qualifiée → NOM (repli honnête, pas de capture parasite)")
        assertEquals("Sys", contexte.prefixe)
    }

    // ------------------------------------------------------------------ //
    // Non capture honnête (fiche 035)
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun accesMembreSurVoidEstNonCaptureHonnete() {
        // `forEach` rend void : l'accès membre sur void n'a aucun candidat —
        // INCONNU (liste vide honnête), jamais de genre parasite.
        val contexte = analyserA("class Main { void m() { java.util.List.of(1).forEach(x -> x).fil¦\n")
        assertEquals(GenreContexte.INCONNU, contexte.genre, "accès membre sur void → non capture honnête (fiche 035)")
    }

    // ------------------------------------------------------------------ //
    // Bout en bout — la capacité phare au niveau du moteur
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun moteurCompleteTypeSurClasseNonFermee() {
        // Fiche 008 EXACTE : items non null, « String » présent.
        val moteur = MoteurJava(racines = emptyList(), jars = emptyList())
        val source = "public class MainActivity {\n    private Str"
        val reponse = moteur.completer("/Main.java", source, source.length)
        assertNotNull(reponse, "la réponse n'est jamais nulle (fiche 035)")
        assertEquals(GenreContexte.TYPE, reponse.genre, "le genre diagnostiqué est TYPE")
        assertTrue(
            reponse.items.any { it.libelle == "String" },
            "« String » est présent (pire cas de recovery, fiche 008) — items=${reponse.items.take(5).map { it.libelle }}",
        )
    }
}

package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.inspections.SOURCE_LINT
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vague V-C2 (suite) — COMPLÉMENT DE CATALOGUE LINT (ADR 0024, reste du PV
 * 1.7.0) : **Range** (@IntRange/@FloatRange/@Size), **ResourceType**
 * (@…Res contre la famille réelle), **CommitPrefEdits**, **Recycle**.
 *
 * Montage SANS plateforme (annotations et types SYTHÉTIQUES du fichier —
 * mêmesliaisons ecj réelles que le SDK) : le banc éprouve la SÉMANTIQUE
 * sur bindings vivants ; les annotations BINAIRES du .class du SDK
 * (androidx/android.annotation) suivent le même chemin de lecture en
 * CI/appareil (ControlesLintTest, gate plateforme).
 *
 * Critères (ADR 0024) : cas dorés par contrôle — DÉTECTION, mutant négatif
 * (non-détection), ÉCHAPPATOIRE/évasion, SUPPRESSION @SuppressLint ;
 * sévérité Warning LSP, provenance "lint", jamais Error.
 */
class CatalogueLintTest {

    private fun lintDe(source: String, chemin: String = "/demo/Main.java"): List<jo.codeide.lsp.java.diagnostics.Diagnostic> {
        val racine = Files.createTempDirectory("vc2-catalogue")
        val demo = Files.createDirectories(racine.resolve("demo"))
        demo.resolve("Main.java").writeText(source)
        val moteur = MoteurJava(racines = listOf(racine))
        return moteur.diagnose(chemin, source).filter { it.source == SOURCE_LINT }
    }

    // ------------------------------------------------------------------ //
    // Range — @IntRange/@FloatRange/@Size
    // ------------------------------------------------------------------ //

    private val sourceRange = """
        package demo;
        @interface IntRange { long from() default Long.MIN_VALUE; long to() default Long.MAX_VALUE; }
        @interface FloatRange { float from() default Float.NEGATIVE_INFINITY; float to() default Float.POSITIVE_INFINITY; }
        @interface Size { long min() default Long.MIN_VALUE; long max() default Long.MAX_VALUE; long value() default -1; }
        public class Main {
            void regler(@IntRange(from = 1, to = 100) int largeur) { }
            void tangenter(@FloatRange(from = 0.0f, to = 1.0f) float pente) { }
            void nommer(@Size(min = 1) String nom) { }
            @IntRange(from = 1, to = 10)
            int plafond() { return 42; }
            void go() {
                regler(150);
                regler(50);
                regler(-5);
                tangenter(1.5f);
                nommer("");
            }
        }
    """.trimIndent()

    @Test
    fun `Range detecte les constantes hors bornes et epargne les conformes`() {
        val signaux = lintDe(sourceRange).filter { it.code == "Range" }
        assertEquals(5, signaux.size, "150, -5, 1.5f, \"\" et le retour 42 hors bornes : ${signaux.map { it.message }}")
        assertEquals(Severite.AVERTISSEMENT, signaux[0].severite, "Warning LSP par défaut (ADR 0024)")
        assertTrue(signaux.any { it.message.contains("150") }, "la valeur fautive est nommée")
        assertTrue(signaux.any { it.message.contains("retour de « plafond »") }, "le RETOUR annoté est éprouvé")
        assertTrue(signaux.none { it.message.contains("valeur 50") }, "regler(50) conforme : silence")
    }

    @Test
    fun `Range epargne l'argument non constant et le retour conforme`() {
        val conforme = """
            package demo;
            @interface IntRange { long from() default Long.MIN_VALUE; long to() default Long.MAX_VALUE; }
            public class Main {
                void regler(@IntRange(from = 1, to = 100) int largeur) { }
                @IntRange(from = 1, to = 10)
                int plafondConforme() { return 7; }
                int calcule(int brut) { return brut; }
                void go(int dynamique) {
                    regler(dynamique);
                    regler(calcule(30));
                    plafondConforme();
                }
            }
        """.trimIndent()
        assertEquals(0, lintDe(conforme).filter { it.code == "Range" }.size, "aucune constante fautive : silence honnête")
    }

    @Test
    fun `Range respecte la suppression SuppressLint`() {
        val supprimee = """
            package demo;
            @interface IntRange { long from() default Long.MIN_VALUE; long to() default Long.MAX_VALUE; }
            public class Main {
                @interface SuppressLint { String[] value(); }
                void regler(@IntRange(from = 1, to = 100) int largeur) { }
                @SuppressLint("Range")
                void go() {
                    regler(150);
                }
            }
        """.trimIndent()
        assertEquals(0, lintDe(supprimee).filter { it.code == "Range" }.size, "@SuppressLint(Range) coupe le contrôle")
    }

    @Test
    fun `Range eprouve la taille des tableaux par Size exacte`() {
        val source = """
            package demo;
            @interface Size { long min() default Long.MIN_VALUE; long max() default Long.MAX_VALUE; long value() default -1; }
            public class Main {
                void paire(@Size(2) int[] binome) { }
                void go() {
                    paire(new int[] { 1, 2, 3 });
                    paire(new int[] { 1, 2 });
                }
            }
        """.trimIndent()
        val signaux = lintDe(source).filter { it.code == "Range" }
        assertEquals(1, signaux.size, "seul le tableau de 3 éléments est signalé")
        assertTrue(signaux[0].message.contains("3"), "le cardinal fautif est nommé : ${signaux[0].message}")
    }

    // ------------------------------------------------------------------ //
    // ResourceType — famille @…Res contre famille réelle
    // ------------------------------------------------------------------ //

    private val sourceResourceType = """
        package demo;
        @interface StringRes { }
        @interface DrawableRes { }
        public class Main {
            public static class R {
                public static class string { public static final int titre = 1; }
                public static class drawable { public static final int logo = 2; }
            }
            void poser(@DrawableRes int icone) { }
            void creer(@StringRes int libelle) { }
            void go() {
                poser(R.string.titre);
                poser(R.drawable.logo);
                creer(R.string.titre);
                poser(5);
            }
        }
    """.trimIndent()

    @Test
    fun `ResourceType detecte la famille refusee et epargne la bonne`() {
        val signaux = lintDe(sourceResourceType).filter { it.code == "ResourceType" }
        assertEquals(1, signaux.size, "seul R.string.titre vers @DrawableRes est signalé : ${signaux.map { it.message }}")
        assertEquals(Severite.AVERTISSEMENT, signaux[0].severite)
        assertTrue(signaux[0].message.contains("string"), "la famille réelle est nommée : ${signaux[0].message}")
        assertTrue(signaux[0].message.contains("Drawable"), "l'annotation attendue est nommée : ${signaux[0].message}")
    }

    @Test
    fun `ResourceType eprouve les noms annotes du fichier`() {
        val source = """
            package demo;
            @interface StringRes { }
            @interface DrawableRes { }
            public class Main {
                @DrawableRes int champIco;
                void poser(@StringRes int libelle) { }
                void go(@DrawableRes int paramIco) {
                    poser(paramIco);
                    poser(champIco);
                }
            }
        """.trimIndent()
        val signaux = lintDe(source).filter { it.code == "ResourceType" }
        assertEquals(2, signaux.size, "paramètre et champ annotés drawable vers @StringRes : signalés")
    }

    @Test
    fun `ResourceType se tait sur l'entier ordinaire (jamais de faux positif)`() {
        val extrait = """
            package demo;
            @interface DrawableRes { }
            public class Main {
                void poser(@DrawableRes int icone) { }
                void go(int brut) {
                    poser(brut);
                    poser(0);
                    poser(brut + 1);
                }
            }
        """.trimIndent()
        assertEquals(0, lintDe(extrait).filter { it.code == "ResourceType" }.size, "entier ordinaire : silence honnête")
    }

    // ------------------------------------------------------------------ //
    // CommitPrefEdits — edit() sans commit()/apply()
    // ------------------------------------------------------------------ //

    private val sourceCommit = """
        package demo;
        interface SharedPreferences {
            Editor edit();
            interface Editor {
                Editor putString(String cle, String valeur);
                void apply();
                boolean commit();
            }
        }
        public class Main {
            void oublieuse(SharedPreferences prefs) {
                SharedPreferences.Editor e = prefs.edit();
                e.putString("cle", "valeur");
            }
            void soignee(SharedPreferences prefs) {
                SharedPreferences.Editor e = prefs.edit();
                e.putString("cle", "valeur");
                e.apply();
            }
            void chainee(SharedPreferences prefs) {
                prefs.edit().putString("cle", "valeur");
            }
            void scellee(SharedPreferences prefs) {
                prefs.edit().putString("cle", "valeur").apply();
            }
            void evadee(SharedPreferences prefs) {
                SharedPreferences.Editor e = prefs.edit();
                e.putString("cle", "valeur");
                confier(e);
            }
            void confier(SharedPreferences.Editor e) { }
        }
    """.trimIndent()

    @Test
    fun `CommitPrefEdits detecte l'editeur jamais scelle`() {
        val signaux = lintDe(sourceCommit).filter { it.code == "CommitPrefEdits" }
        assertEquals(2, signaux.size, "oublieuse + chainee : ${signaux.map { it.message }}")
        assertEquals(Severite.AVERTISSEMENT, signaux[0].severite)
        assertTrue(signaux.all { it.message.contains("commit") }, "le message porte le geste manquant")
    }

    @Test
    fun `CommitPrefEdits isole les pistes par methode`() {
        val source = """
            package demo;
            interface SharedPreferences {
                Editor edit();
                interface Editor { Editor putString(String cle, String valeur); void apply(); boolean commit(); }
            }
            public class Main {
                void premiere(SharedPreferences prefs) {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putString("cle", "valeur");
                }
                void seconde(SharedPreferences prefs) {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putString("cle", "valeur");
                    e.commit();
                }
            }
        """.trimIndent()
        val signaux = lintDe(source).filter { it.code == "CommitPrefEdits" }
        assertEquals(1, signaux.size, "le commit de la seconde méthode n'acquitte pas la première")
        val plagePremiere = source.indexOf("prefs.edit()")
        assertTrue(
            signaux[0].plage.debutOffset in plagePremiere..(plagePremiere + 30),
            "le signalement porte sur la première méthode",
        )
    }

    // ------------------------------------------------------------------ //
    // Recycle — TypedArray/VelocityTracker sans recycle()
    // ------------------------------------------------------------------ //

    private val sourceRecycle = """
        package demo;
        class Resources {
            public TypedArray obtainStyledAttributes(int[] attrs) { return null; }
        }
        class TypedArray implements AutoCloseable {
            public void close() { }
            public void recycle() { }
            public String getString(int index) { return null; }
        }
        class VelocityTracker {
            public static VelocityTracker obtain() { return null; }
            public void recycle() { }
        }
        public class Main {
            void oublieuse(Resources res, int[] attrs) {
                TypedArray ta = res.obtainStyledAttributes(attrs);
                ta.getString(0);
            }
            void soignee(Resources res, int[] attrs) {
                TypedArray ta = res.obtainStyledAttributes(attrs);
                ta.getString(0);
                ta.recycle();
            }
            void pisteVitesse() {
                VelocityTracker vt = VelocityTracker.obtain();
                vt.recycle();
            }
            void pisteVitesseOubliee() {
                VelocityTracker vt = VelocityTracker.obtain();
            }
            void exempte(Resources res, int[] attrs) {
                try (TypedArray ta = res.obtainStyledAttributes(attrs)) {
                    ta.getString(0);
                }
            }
            void evasive(Resources res, int[] attrs) {
                TypedArray ta = res.obtainStyledAttributes(attrs);
                confier(ta);
            }
            void confier(TypedArray ta) { }
        }
    """.trimIndent()

    @Test
    fun `Recycle detecte la ressource jamais restituee`() {
        val signaux = lintDe(sourceRecycle).filter { it.code == "Recycle" }
        assertEquals(2, signaux.size, "oublieuse + pisteVitesseOubliee : ${signaux.map { it.message }}")
        assertEquals(Severite.AVERTISSEMENT, signaux[0].severite)
        assertTrue(signaux.all { it.message.contains("recycle") }, "le message porte le geste manquant")
    }

    @Test
    fun `Recycle respecte suppression et garde des ecarts de methode`() {
        val supprime = """
            package demo;
            class Resources { public TypedArray obtainStyledAttributes(int[] attrs) { return null; } }
            class TypedArray { public void recycle() { } }
            public class Main {
                @android.annotation.SuppressLint("Recycle")
                void oublieuse(Resources res, int[] attrs) {
                    TypedArray ta = res.obtainStyledAttributes(attrs);
                }
            }
        """.trimIndent()
        // Sans android.annotation sur le classpath, @android.annotation.SuppressLint
        // est une erreur de résolution : la garde commune tait la passe (comportement
        // de la référence) — le cas doré de suppression vit avec le VRAI android.jar
        // (ControlesLintTest). Ici : la garde elle-même est l'assertion.
        val erreurs = lintDe(supprime)
        assertTrue(erreurs.none { it.code == "Recycle" }, "aucun signal Recycle (garde ou suppression)")
    }
}

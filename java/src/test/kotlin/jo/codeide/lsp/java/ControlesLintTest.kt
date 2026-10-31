package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.inspections.BaseApiAndroid
import jo.codeide.lsp.java.inspections.SOURCE_LINT
import jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vague V-C2 — CONTRÔLES LINT ANDROID à valeur Java (ADR 0024, écart
 * G-011) : NewApi (+ InlinedApi + ObsoleteSdkInt), MissingSuperCall,
 * SetTextI18n — sur une BASE API SYNTHÉTIQUE (jamais la vraie 5,5 Mo —
 * R6.4/R5 : le banc éprouve la SÉMANTIQUE, le script
 * `scripts/preparer-base-api.sh` branche la vraie en CI/appareil) et le
 * VRAI android.jar du banc (plateforme résoluble, annotations réelles
 * @RequiresApi/@SuppressLint/@CallSuper — gate environnementale assumée,
 * même discipline que le banc projet réel).
 *
 * Critères (ADR 0024) : cas dorés par contrôle (détection, ÉCHAPPATOIRE,
 * suppression, garde « pas d'erreurs bloquantes » du circuit annotator de
 * la référence) ; sévérité Warning LSP par défaut, provenance "lint",
 * jamais Error.
 */
class ControlesLintTest {

    /** La racine des bibliothèques réelles du banc (android.jar & co). */
    private val libs: Path? = sequenceOf(
        System.getenv("BANC_ANDROID_LIBS")?.let { Path.of(it) },
        Path.of("../bancs/projet-android-libs"),
        Path.of("bancs/projet-android-libs"),
    ).firstOrNull { p -> p != null && Files.isRegularFile(p.resolve("android-34-ext12.jar")) }

    /** Vrai ssi la plateforme Android du banc est disponible. */
    private val plateformeDisponible: Boolean get() = libs != null

    /** Une base synthétique : TextView#setTextAlignment requiert 17 ; un champ inliné depuis 26. */
    private fun poserBase(): Path {
        val base = Files.createTempDirectory("vc2-base")
        val xml = base.resolve("api-versions.xml")
        xml.writeText(
            """
            <api version="3">
              <class name="android/widget/TextView" since="1">
                <method name="setText(CharSequence)" since="1"/>
              </class>
              <class name="android/view/View" since="1">
                <method name="setTextAlignment(int)" since="17"/>
                <field name="TEXT_ALIGNMENT_CENTER" since="17" inlined="26"/>
              </class>
              <class name="android/os/Build" since="1">
                <field name="VERSION_CODES" since="1"/>
              </class>
            </api>
            """.trimIndent(),
        )
        System.setProperty("jo.codeide.lsp.java.lint.baseapi", xml.toString())
        return xml
    }

    /** Un moteur Android du banc (contrat + minSdk + plateforme réelle). */
    private fun moteur(minSdk: Int): MoteurJava {
        val racine = Files.createTempDirectory("vc2-lint")
        Files.createDirectories(racine.resolve("demo").resolve("res").resolve("values"))
        val contrat = ContratAndroidMoteur(
            namespace = "demo.exemple",
            minSdk = minSdk,
            compileSdk = 34,
            dossiersRes = listOf(racine.resolve("demo").resolve("res")),
        )
        return MoteurJava(
            racines = listOf(racine),
            jars = listOfNotNull(libs?.resolve("android-34-ext12.jar")),
            jdkHome = null,
            compliance = "17",
            niveauDemande = null,
            indexNoms = null,
            projetAndroid = contrat,
        )
    }

    private fun lintDe(source: String, minSdk: Int = 21): List<jo.codeide.lsp.java.diagnostics.Diagnostic> {
        poserBase()
        val moteur = moteur(minSdk)
        return moteur.diagnose("/demo/Main.java", source).filter { it.source == SOURCE_LINT }
    }

    @Test
    fun `NewApi detecte l'appel au-dela du minSdk et respecte les echappatoires`() {
        assumeTrue(plateformeDisponible, "android.jar du banc absent — gate environnementale")
        val base = """
            package demo;
            public class Main {
                public void go(android.widget.TextView t) {
                    t.setTextAlignment(1);
                }
            }
        """.trimIndent()
        // setTextAlignment(int) depuis 17, minSdk 21 : RAS.
        assertTrue(lintDe(base, minSdk = 21).none { it.code == "NewApi" }, "since 17 ≤ minSdk 21 : silence")
        // minSdk 14 : signalé (Warning LSP, provenance lint).
        val signaux = lintDe(base, minSdk = 14).filter { it.code == "NewApi" }
        assertEquals(1, signaux.size, "setTextAlignment requiert 17 > minSdk 14")
        assertEquals(Severite.AVERTISSEMENT, signaux[0].severite, "Warning LSP par défaut (ADR 0024)")
        assertTrue(signaux[0].message.contains("17"), "le message porte le niveau requis : ${signaux[0].message}")
        // Échappatoire 1 : garde SDK_INT.
        val avecGarde = """
            package demo;
            public class Main {
                public void go(android.widget.TextView t) {
                    if (android.os.Build.VERSION.SDK_INT >= 17) {
                        t.setTextAlignment(1);
                    }
                }
            }
        """.trimIndent()
        assertTrue(lintDe(avecGarde, minSdk = 14).none { it.code == "NewApi" }, "garde SDK_INT >= 17 : échappatoire")
        // Échappatoire 2 : @RequiresApi(17) sur la méthode englobante
        // (annotation LOCALE : la lecture se fait par nom simple — le
        // framework n'expose pas RequiresApi dans le jar du banc).
        val avecAnnotation = """
            package demo;
            public class Main {
                @interface RequiresApi { int value(); }
                @RequiresApi(17)
                public void go(android.widget.TextView t) {
                    t.setTextAlignment(1);
                }
            }
        """.trimIndent()
        assertTrue(lintDe(avecAnnotation, minSdk = 14).none { it.code == "NewApi" }, "@RequiresApi(17) : échappatoire")
        // Échappatoire 3 : @SuppressLint("NewApi").
        val avecSuppress = """
            package demo;
            public class Main {
                @android.annotation.SuppressLint("NewApi")
                public void go(android.widget.TextView t) {
                    t.setTextAlignment(1);
                }
            }
        """.trimIndent()
        assertTrue(lintDe(avecSuppress, minSdk = 14).none { it.code == "NewApi" }, "@SuppressLint(NewApi) coupe")
    }

    @Test
    fun `InlinedApi signale la constante inlinee au-dela du minSdk`() {
        assumeTrue(plateformeDisponible, "android.jar du banc absent — gate environnementale")
        val base = """
            package demo;
            public class Main {
                public int go() {
                    return android.widget.TextView.TEXT_ALIGNMENT_CENTER;
                }
            }
        """.trimIndent()
        // Champ TEXT_ALIGNMENT_CENTER : since 17, inlined 26.
        val signaux = lintDe(base, minSdk = 21).filter { it.code == "NewApi" }
        assertEquals(1, signaux.size, "inlined 26 > minSdk 21 : signal InlinedApi")
        assertTrue(
            signaux[0].message.contains("inlin"),
            "le message porte la sémantique InlinedApi : ${signaux[0].message}",
        )
        assertTrue(lintDe(base, minSdk = 28).isEmpty(), "inlined 26 ≤ minSdk 28 : silence")
    }

    @Test
    fun `ObsoleteSdkInt detecte la comparaison morte sous le minSdk`() {
        assumeTrue(plateformeDisponible, "android.jar du banc absent — gate environnementale")
        val base = """
            package demo;
            public class Main {
                public String go() {
                    if (android.os.Build.VERSION.SDK_INT < 21) {
                        return "vieux";
                    }
                    return "moderne";
                }
            }
        """.trimIndent()
        val signaux = lintDe(base, minSdk = 24).filter { it.message.contains("obsolète") }
        assertEquals(1, signaux.size, "SDK_INT < 21 sous minSdk 24 : branche morte signalée")
        assertTrue(
            lintDe(base, minSdk = 19).none { it.message.contains("obsolète") },
            "sous minSdk 19 la branche vit : silence",
        )
    }

    @Test
    fun `MissingSuperCall detecte l'outrepassement sans super`() {
        assumeTrue(plateformeDisponible, "android.jar du banc absent — gate environnementale")
        poserBase()
        val moteur = moteur(21)
        val base = """
            package demo;
            public class Main {
                public static class Base {
                    @interface CallSuper { }
                    @CallSuper
                    public void onCreate() { }
                }
                public static class Sous extends Base {
                    public void onCreate() { System.out.println("x"); }
                }
            }
        """.trimIndent()
        val signaux = moteur.diagnose("/demo/Main.java", base).filter { it.code == "MissingSuperCall" }
        assertEquals(1, signaux.size, "l'outrepassement sans super est signalé")
        assertTrue(signaux[0].message.contains("super.onCreate"), "le message porte le fix : ${signaux[0].message}")
        // Avec l'appel super : silence (le mutant négatif).
        val avecSuper = base.replace(
            "public void onCreate() { System.out.println(\"x\"); }",
            "public void onCreate() { super.onCreate(); System.out.println(\"x\"); }",
        )
        assertEquals(
            0,
            moteur.diagnose("/demo/Main.java", avecSuper).filter { it.code == "MissingSuperCall" }.size,
            "avec super.onCreate() : silence",
        )
    }

    @Test
    fun `SetTextI18n signale le litteral et non l'appel dynamique`() {
        assumeTrue(plateformeDisponible, "android.jar du banc absent — gate environnementale")
        val base = """
            package demo;
            public class Main {
                public void go(android.widget.TextView t, String s) {
                    t.setText("Bonjour");
                    t.setText(s);
                }
            }
        """.trimIndent()
        val signaux = lintDe(base).filter { it.code == "SetTextI18n" }
        assertEquals(1, signaux.size, "seul le littéral est signalé")
        assertEquals(Severite.INFO, signaux[0].severite, "signal faible (localisation)")
    }

    @Test
    fun `sans contrat Android ni base, les controles API restent muets (repli honnete)`() {
        System.clearProperty("jo.codeide.lsp.java.lint.baseapi")
        val racine = Files.createTempDirectory("vc2-sans")
        Files.createDirectories(racine.resolve("demo"))
        val moteur = MoteurJava(
            racines = listOf(racine),
            jars = listOfNotNull(libs?.resolve("android-34-ext12.jar")),
            jdkHome = null,
            compliance = "17",
            niveauDemande = null,
            indexNoms = null,
            projetAndroid = null, // SANS contrat Android
        )
        val base = """
            package demo;
            public class Main {
                public void go(android.widget.TextView t) {
                    t.setTextAlignment(1);
                }
            }
        """.trimIndent()
        assertEquals(
            0,
            moteur.diagnose("/demo/Main.java", base).filter { it.source == SOURCE_LINT && it.code == "NewApi" }.size,
            "sans contrat (minSdk inconnu) : silence honnête, jamais un faux NewApi",
        )
    }

    @Test
    fun `une erreur de resolution tait la passe lint (garde de la reference)`() {
        assumeTrue(plateformeDisponible, "android.jar du banc absent — gate environnementale")
        val base = """
            package demo;
            public class Main {
                public void go(android.widget.TextView t) {
                    Inconnu.fait(t);
                    t.setTextAlignment(1);
                }
            }
        """.trimIndent()
        assertTrue(lintDe(base, minSdk = 14).isEmpty(), "sur unité fautive : la passe lint se tait")
    }

    @Test
    fun `la base synthetique se charge et repond`() {
        poserBase()
        assertTrue(BaseApiAndroid.sinceClasse("android/widget/TextView") != null, "la base synthétique se charge")
        assertEquals(17, BaseApiAndroid.sinceMethode("android/view/View", "setTextAlignment", listOf("int")))
        assertEquals(1, BaseApiAndroid.sinceMethode("android/widget/TextView", "setText", listOf("CharSequence")))
        assertEquals(
            null,
            BaseApiAndroid.sinceMethode("android/widget/TextView", "setText", listOf("Inconnu")),
            "signature inconnue : null (silence honnête)",
        )
        assertEquals(26, BaseApiAndroid.niveauChamp("android/view/View", "TEXT_ALIGNMENT_CENTER")?.inlined)
    }
}

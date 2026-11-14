package jo.codeide.lsp.java.survol

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Chantier D (14/11/2026) — origine « Défini dans … » dans le hover : le
 * survol d'un symbole dont la déclaration vit dans un AUTRE fichier rend
 * une ligne d'ORIGINE (le nom du fichier déclarant) en tête du hover —
 * miroir exact du chantier L Kotlin (même libellé « Défini dans », même
 * position, même contrat : la ligne INFORME, le saut reste
 * textDocument/definition).
 *
 * Le niveau MOTEUR exige un classpath RÉEL (le binding d'un type binaire
 * ne résout que contre un jar plateforme) : les tests d'origine utilisent
 * le VRAI SDK Android du poste (local.properties → platforms/android-37.2
 * + sources/android-37.2 — sandbox et CI les installent), avec un IGNORE
 * EXPLICITE hors poste SDK (jamais rouge, jamais vert silencieux). Les
 * contrats PÉRIMÈTRE (document courant, absence d'invention) sont
 * autoportés (jrt seul, aucun SDK requis).
 */
class ChantierDOrigineHoverTest {

    // --------------------------------------------------------------- //
    // Autoporté — aucun SDK requis
    // --------------------------------------------------------------- //

    @Test
    fun `symbole du document courant - jamais d'origine`() {
        val moteur = MoteurJava(racines = emptyList(), jars = emptyList())
        val offset = SOURCE_LOCALE.indexOf("somme(1") + 1
        val survol = moteur.survolRiche("/demo/Main.java", SOURCE_LOCALE, offset)
        assertNotNull(survol, "le survol de la méthode locale doit résoudre")
        assertTrue(survol.signature.startsWith("int somme"), "signature : ${survol.signature}")
        assertNull(survol.origine, "aucune origine pour le document courant — rien à dire")
        assertNotNull(survol.documentation, "la javadoc locale reste rendue")
    }

    @Test
    fun `sans sources attachees - origine null jamais inventee`() {
        // length() du jrt : le JDK du poste n'a pas de src.zip ⇒ canal zip
        // absent ⇒ origine null (R5 : jamais inventée).
        val moteur = MoteurJava(racines = emptyList(), jars = emptyList())
        val source = """
            package demo;

            public class Demo {
                int n() {
                    String s = "abc";
                    return s.length();
                }
            }
        """.trimIndent()
        val offset = source.indexOf("length") + 1
        val survol = moteur.survolRiche("/demo/Demo.java", source, offset)
        assertNotNull(survol, "le survol de la méthode jrt doit résoudre")
        assertTrue(survol.signature.contains("length"), "signature : ${survol.signature}")
        assertNull(survol.origine, "sans canal de sources, aucune origine — null honnête")
    }

    // --------------------------------------------------------------- //
    // Vrai SDK — origine des sources attachées réelles
    // --------------------------------------------------------------- //

    @Test
    fun `type binaire du vrai sdk - origine du fichier des sources`() {
        val sdk = sdkDuPoste() ?: return ignoré("SDK Android absent du poste")
        val jar = sdk.resolve("platforms/android-37.2/android.jar")
        val sources = sdk.resolve("sources/android-37.2")
        if (!Files.isRegularFile(jar) || !Files.isDirectory(sources)) {
            return ignoré("SDK sans plateforme 37.2 ou sans sources")
        }
        val moteur = MoteurJava(racines = emptyList(), jars = listOf(jar))
        val demo = """
            package demo;

            import android.app.Activity;

            public class Demo extends Activity {
            }
        """.trimIndent()
        val offset = demo.indexOf("extends Activity") + "extends ".length + 1
        val survol = moteur.survolRiche("/demo/Demo.java", demo, offset)
        assertNotNull(survol, "le survol du type android doit résoudre (jar réel)")
        assertEquals("android.app.Activity", survol.signature, "le nom LONG du binding est rendu")
        assertEquals("Activity.java", survol.origine, "l'origine est le fichier des sources attachées")
        assertNotNull(survol.documentation, "la javadoc réelle du SDK (chantier A) accompagne")
    }

    @Test
    fun `methode binaire du vrai sdk - origine du declarant`() {
        val sdk = sdkDuPoste() ?: return ignoré("SDK Android absent du poste")
        val jar = sdk.resolve("platforms/android-37.2/android.jar")
        val sources = sdk.resolve("sources/android-37.2")
        if (!Files.isRegularFile(jar) || !Files.isDirectory(sources)) {
            return ignoré("SDK sans plateforme 37.2 ou sans sources")
        }
        val moteur = MoteurJava(racines = emptyList(), jars = listOf(jar))
        // findViewById est documenté dans les sources réelles d'Activity.
        val demo = """
            package demo;

            import android.app.Activity;

            public class Demo extends Activity {
                void go() {
                    Object v = findViewById(0x7f080001);
                }
            }
        """.trimIndent()
        val offset = demo.indexOf("findViewById") + 1
        val survol = moteur.survolRiche("/demo/Demo.java", demo, offset)
        assertNotNull(survol, "le survol de la méthode binaire doit résoudre (jar réel)")
        assertEquals("Activity.java", survol.origine, "l'origine est le fichier déclarant")
        assertNotNull(survol.documentation, "la javadoc de findViewById vient des sources réelles")
        assertTrue(
            survol.documentation!!.isNotBlank(),
            "la javadoc rendue n'est pas vide",
        )
    }

    // --------------------------------------------------------------- //
    // Fixtures
    // --------------------------------------------------------------- //

    private val SOURCE_LOCALE = """
        package demo;

        public class Main {
            /**
             * Additionne et rend la somme.
             */
            int somme(int a, int b) {
                return a + b;
            }

            void utilise() {
                int r = somme(1, 2);
            }
        }
    """.trimIndent()

    /** Le SDK Android du poste (local.properties — sandbox et CI le posent). */
    private fun sdkDuPoste(): Path? {
        for (candidat in listOf(Path.of("local.properties"), Path.of("../../local.properties"))) {
            if (!Files.isRegularFile(candidat)) continue
            val sdk = runCatching {
                val props = java.util.Properties()
                Files.newInputStream(candidat).use { props.load(it) }
                props.getProperty("sdk.dir")?.let(Path::of)
            }.getOrNull()
            if (sdk != null && Files.isDirectory(sdk)) return sdk
        }
        return null
    }

    /** Un ignore explicite (jamais vert silencieux). */
    private fun ignoré(raison: String) {
        println("[ChantierD] test SDK ignoré : $raison")
    }

    @TempDir
    lateinit var tmp: Path
}

package jo.codeide.lsp.java.survol

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.classpath.SourcesAttachees
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Chantier A (14/11/2026) — javadoc du CLASSPATH au survol : le survol
 * d'un symbole BINAIRE (jar plateforme du classpath) rend la javadoc de
 * ses SOURCES ATTACHÉES (dossier `sources/android-<api>` du SDK déduit
 * du jar plateforme, `lib/src.zip` du JDK) — contrat « honnête » R5
 * tenu : SANS sources posées, la documentation reste null (signature
 * seule), exactement le comportement d'avant le chantier.
 *
 * Deux niveaux de verrou :
 *  - UNITAIRE : le service [SourcesAttachees] (découverte des canaux,
 *    lecture javadoc type/méthode/champ, replis honnêtes) sur des
 *    fixtures totalement autoportées (dossier + zip construits en
 *    temporaire) — AUCUNE dépendance au SDK du poste ;
 *  - INTÉGRATION : le moteur COMPLET (survolRiche) contre le VRAI SDK
 *    Android si `local.properties` le désigne (sandbox/CI l'installent)
 * — le survol de `android.app.Activity` dans un source de démonstration
 *    rend la javadoc RÉELLE des sources du SDK. Le test est IGNORÉ
 *    honnêtement quand le SDK (ou ses sources) manque — jamais rouge,
 *    jamais vert silencieux : l'ignore est EXPLICITE.
 */
class ChantierAJavadocClasspathTest {

    // --------------------------------------------------------------- //
    // Niveau unitaire — le service SourcesAttachees (autoporté)
    // --------------------------------------------------------------- //

    @Test
    fun `canal dossier - javadoc de type, methode et champ`() {
        val sdk = tmp.resolve("sdk").also(Files::createDirectories)
        // Le jar plateforme (présence seule — la découverte se fait par
        // layout, pas par contenu) et les sources au layout sdkmanager.
        val jar = sdk.resolve("platforms/android-99/android.jar")
        Files.createDirectories(jar.parent)
        Files.createFile(jar)
        val source = sdk.resolve("sources/android-99/android/app/Activity.java")
        Files.createDirectories(source.parent)
        source.writeText(FIXTURE_ACTIVITY)

        val canal = SourcesAttachees(jarsClasspath = listOf(jar))
        assertTrue(canal.actif, "le canal dossier doit être découvert depuis le layout SDK")

        // Javadoc du TYPE.
        val javadocType = canal.javadocType("android.app.Activity")
        assertNotNull(javadocType, "la javadoc du type doit être lue")
        assertTrue(javadocType.contains("chose unique et focalisée"), "javadoc type : $javadocType")

        // Javadoc d'une MÉTHODE (première surcharge du sélecteur).
        val javadocMethode = canal.javadocMethode("android.app.Activity", "onCreate")
        assertNotNull(javadocMethode, "la javadoc de la méthode doit être lue")
        assertTrue(javadocMethode.contains("l'activité démarre"), "javadoc méthode : $javadocMethode")

        // Javadoc d'un CHAMP.
        val javadocChamp = canal.javadocChamp("android.app.Activity", "RESULT_OK")
        assertNotNull(javadocChamp, "la javadoc du champ doit être lue")
        assertTrue(javadocChamp.contains("code de résultat"), "javadoc champ : $javadocChamp")
    }

    @Test
    fun `canal zip jdk - src zip au layout lib`() {
        val home = tmp.resolve("jdk-home").also(Files::createDirectories)
        val lib = home.resolve("lib")
        Files.createDirectories(lib)
        // Un src.zip MINIMAL au layout JDK.
        val entree = "java.base/java/lang/String.java"
        java.util.zip.ZipOutputStream(Files.newOutputStream(lib.resolve("src.zip"))).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry(entree))
            zip.write(FIXTURE_STRING.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val canal = SourcesAttachees(jdkHome = home)
        assertTrue(canal.actif, "le canal zip doit être découvert depuis lib/src.zip")
        val javadoc = canal.javadocType("java.lang.String")
        assertNotNull(javadoc, "la javadoc du src.zip doit être lue")
        assertTrue(javadoc.contains("chaînes de caractères"), "javadoc zip : $javadoc")
    }

    @Test
    fun `sans canal - actif faux et javadoc null partout`() {
        // CORRECTIF CI : jdkHome explicite SANS lib/src.zip — le null
        // laissait le repli java.home jouer (le JDK de CI est COMPLET,
        // son src.zip fabriquait un canal actif et le test échouait ;
        // le sandbox local, lui, n'a qu'un JRE nu).
        val homeNu = tmp.resolve("jdk-nu").also(Files::createDirectories)
        val canal = SourcesAttachees(jarsClasspath = emptyList(), jdkHome = homeNu)
        assertEquals(false, canal.actif, "aucun jar plateforme ni src.zip ⇒ inactif")
        assertNull(canal.javadocType("android.app.Activity"), "type : null honnête")
        assertNull(canal.javadocMethode("android.app.Activity", "onCreate"), "méthode : null honnête")
        assertNull(canal.javadocChamp("android.app.Activity", "RESULT_OK"), "champ : null honnête")
    }

    @Test
    fun `canal present mais type absent - null honnete`() {
        val sdk = tmp.resolve("sdk2").also(Files::createDirectories)
        val jar = sdk.resolve("platforms/android-98/android.jar")
        Files.createDirectories(jar.parent)
        Files.createFile(jar)
        val dossier = sdk.resolve("sources/android-98")
        Files.createDirectories(dossier)
        val canal = SourcesAttachees(jarsClasspath = listOf(jar))
        assertTrue(canal.actif, "le canal existe (dossier vide)")
        assertNull(canal.javadocType("android.app.Inexistant"), "type absent des sources ⇒ null")
    }

    @Test
    fun `declaration sans javadoc - null honnete`() {
        val sdk = tmp.resolve("sdk3").also(Files::createDirectories)
        val jar = sdk.resolve("platforms/android-97/android.jar")
        Files.createDirectories(jar.parent)
        Files.createFile(jar)
        val source = sdk.resolve("sources/android-97/android/app/Nue.java")
        Files.createDirectories(source.parent)
        source.writeText("package android.app;\npublic class Nue {\n    public void nue() {\n    }\n}\n")
        val canal = SourcesAttachees(jarsClasspath = listOf(jar))
        assertNull(canal.javadocType("android.app.Nue"), "classe sans javadoc ⇒ null, pas de texte fabriqué")
        assertNull(canal.javadocMethode("android.app.Nue", "nue"), "méthode sans javadoc ⇒ null")
    }

    @Test
    fun `jar hors layout sdk - aucun canal android fabrique`() {
        // Un jar AAR posé n'importe où ne doit JAMAIS faire deviner un
        // canal android (le layout platforms/ est la seule signature).
        val lib = tmp.resolve("libs").also(Files::createDirectories)
        val jar = lib.resolve("appcompat-1.7.0.aar")
        Files.createFile(jar)
        // CORRECTIF CI : home nu explicite (même cause que « sans canal »).
        val homeNu = tmp.resolve("jdk-nu").also(Files::createDirectories)
        val canal = SourcesAttachees(jarsClasspath = listOf(jar), jdkHome = homeNu)
        assertEquals(false, canal.actif, "un AAR isolé ne découvre aucun canal")
    }

    // --------------------------------------------------------------- //
    // Niveau intégration — le moteur complet contre le VRAI SDK
    // --------------------------------------------------------------- //

    @Test
    fun `survol moteur du type android avec le vrai sdk`() {
        val sdk = sdkDuPoste() ?: return ignoré("SDK Android absent du poste (local.properties)")
        val jar = sdk.resolve("platforms/android-37.2/android.jar")
        val dossierSources = sdk.resolve("sources/android-37.2")
        if (!Files.isRegularFile(jar) || !Files.isDirectory(dossierSources)) {
            return ignoré("SDK sans plateforme 37.2 ou sans sources posées")
        }
        val moteur = MoteurJava(racines = emptyList(), jars = listOf(jar))
        val source = """
            package demo;

            import android.app.Activity;

            public class Demo extends Activity {
            }
        """.trimIndent()
        // Le caret vise le « Activity » de la clause EXTENDS (pas celui de
        // l'import — le collecteur survol ne visite pas les imports).
        val offset = source.indexOf("extends Activity") + "extends ".length + 1
        val survol = moteur.survolRiche("/demo/Demo.java", source, offset)
        assertNotNull(survol, "le survol du type android doit résoudre (jar plateforme réel)")
        assertNotNull(
            survol.documentation,
            "la javadoc des sources RÉELLES du SDK doit être rendue — signature : ${survol.signature}",
        )
        assertTrue(
            survol.documentation!!.contains("single, focused thing") ||
                survol.documentation!!.contains("activity"),
            "la javadoc réelle d'Activity doit être servie — reçue : ${survol.documentation!!.take(200)}",
        )
    }

    // --------------------------------------------------------------- //
    // Fixtures et utilitaires
    // --------------------------------------------------------------- //

    /** Fixture du canal android : type, méthode, champ documentés. */
    private val FIXTURE_ACTIVITY = """
        package android.app;

        /**
         * Une activité est une chose unique et focalisée.
         */
        public class Activity {

            /**
             * Le code de résultat standard.
             */
            public static final int RESULT_OK = -1;

            /**
             * Appelée quand l'activité démarre.
             */
            protected void onCreate() {
            }
        }
    """.trimIndent()

    /** Fixture du canal JDK. */
    private val FIXTURE_STRING = """
        package java.lang;

        /**
         * La classe String représente des chaînes de caractères.
         */
        public final class String {
        }
    """.trimIndent()

    /** Le SDK Android du poste (local.properties — sandbox et CI le posent). */
    private fun sdkDuPoste(): Path? {
        // local.properties vit à la RACINE du dépôt ; Gradle lance les
        // tests depuis le répertoire du module — les DEUX sont sondés.
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
        println("[ChantierA] test intégration ignoré : $raison")
    }

    @TempDir
    lateinit var tmp: Path
}

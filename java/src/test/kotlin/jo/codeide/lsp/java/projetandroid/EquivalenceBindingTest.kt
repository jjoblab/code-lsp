package jo.codeide.lsp.java.projetandroid

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.diagnostics.Severite

/**
 * Équivalence light ↔ généré du BINDING (vague V-B3 — critère de sortie de
 * l'ADR 0021 : « 0 écart sur cas dorés ») : la classe `ActivityMainBinding`
 * fabriquée par la fabrique light sur le banc réel monip (layout `<layout>`
 * + variable) contre le SIMULACRE COMMIS de la sortie AGP
 * (`bancs/projet-android-genere/`).
 *
 * L'équivalence mesurée : les CHAMPS publics (noms + types) et les
 * MÉTHODES statiques (sélecteurs + descripteurs) — l'API que la
 * résolution Java consomme. Les constructeurs protégés et corps réels ne
 * sont pas dans le périmètre light (API-seule, ADR 0021).
 */
class EquivalenceBindingTest {

    private companion object {
        val MONIP: Path? =
            sequenceOf(
                Path.of("../bancs/projet-android/app/src/main/java"),
                Path.of("../../bancs/projet-android/app/src/main/java"),
            ).firstOrNull { Files.isDirectory(it) }
    }

    @Test
    fun `activitymainbinding light equivaut au genere sur les champs et methodes`() {
        assumeTrue(MONIP != null, "banc projet-android absent du checkout")
        val contrat = ModeleAndroidDerive.deriver(listOf(MONIP!!))!!
        val fabrique = ClassesLegereAndroid(contrat, ReferentielRessources(contrat.dossiersRes), resolvabilite = { _ -> true })

        val octets = fabrique.octetsPour("com.example.monip.databinding.ActivityMainBinding")!!
        val lecteur = ClassFileReader.read(octets, "com/example/monip/databinding/ActivityMainBinding.class")

        // Super : ViewDataBinding (le simulacre l'étend — les artefacts
        // runtime sont des dépendances réelles du projet data binding).
        assertEquals(
            "androidx/databinding/ViewDataBinding",
            String(lecteur.superclassName ?: CharArray(0)),
            "le binding data étend ViewDataBinding",
        )

        // Champs publics du simulacre : main, textTitre, liaison.
        val champsLight = lecteur.fields!!
            .filter { champ -> (champ.modifiers and 0x0001) != 0 }
            .associate { champ -> String(champ.name) to String(champ.typeName) }
        val attendus = mapOf(
            "textTitre" to "Landroid/widget/TextView;",
            "liaison" to "Lcom/example/monip/Greeter;",
        )
        for ((nom, type) in attendus) {
            assertEquals(type, champsLight[nom], "champ $nom — même type que la génération AGP")
        }

        // Méthodes statiques : inflate(LayoutInflater, ViewGroup, boolean),
        // inflate(LayoutInflater), bind(View).
        val methodes = lecteur.methods!!.map { String(it.selector) to String(it.methodDescriptor) }
        assertTrue(
            methodes.any { it.first == "inflate" && it.second == "(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Z)Lcom/example/monip/databinding/ActivityMainBinding;" },
            "inflate 3 arguments",
        )
        assertTrue(
            methodes.any { it.first == "inflate" && it.second == "(Landroid/view/LayoutInflater;)Lcom/example/monip/databinding/ActivityMainBinding;" },
            "inflate 1 argument",
        )
        assertTrue(
            methodes.any { it.first == "bind" && it.second == "(Landroid/view/View;)Lcom/example/monip/databinding/ActivityMainBinding;" },
            "bind(View)",
        )
    }

    @Test
    fun `le scenario complet du banc monip resolve sans les sources generees`() {
        assumeTrue(MONIP != null, "banc projet-android absent du checkout")
        val libs =
            sequenceOf(
                System.getenv("BANC_ANDROID_LIBS")?.let { Path.of(it) },
                Path.of("../bancs/projet-android-libs"),
                Path.of("../../bancs/projet-android-libs"),
            ).firstOrNull { p -> p != null && Files.isRegularFile(p.resolve("android-34-ext12.jar")) }
        assumeTrue(libs != null, "BANC_ANDROID_LIBS absent")

        // RACINES SANS les générées : MainActivity tel qu'écrit AVANT tout
        // build — seule ActivityMainBinding est résolue par la fabrique.
        val contrat = ModeleAndroidDerive.deriver(listOf(MONIP!!))!!
        val moteur = MoteurJava(
            listOf(MONIP),
            listOf(
                libs!!.resolve("android-34-ext12.jar"),
                libs.resolve("appcompat-classes.jar"),
                libs.resolve("core-classes.jar"),
                libs.resolve("annotation-1.8.0.jar"),
                libs.resolve("material-classes.jar"),
                libs.resolve("constraintlayout-classes.jar"),
                libs.resolve("lifecycle-runtime-classes.jar"),
                libs.resolve("drawerlayout-classes.jar"),
                libs.resolve("fragment-classes.jar"),
                libs.resolve("activity-classes.jar"),
                libs.resolve("lifecycle-viewmodel-classes.jar"),
                libs.resolve("lifecycle-viewmodel-savedstate-classes.jar"),
                libs.resolve("savedstate-classes.jar"),
                libs.resolve("tracing-classes.jar"),
                libs.resolve("emoji2-classes.jar"),
                libs.resolve("core-viewtree-classes.jar"),
                libs.resolve("lifecycle-common-2.6.2.jar"),
                libs.resolve("databinding-runtime-classes.jar"),
                libs.resolve("databinding-common-8.4.2.jar"),
                libs.resolve("viewbinding-8.4.2.jar"),
            ),
            null,
            "17",
            null,
            null,
            contrat,
        )
        val mainActivity = Files.readString(MONIP.resolve("com/example/monip/MainActivity.java"))
        val erreurs = moteur.diagnose("/com/example/monip/MainActivity.java", mainActivity)
            .filter { it.severite == Severite.ERREUR }
        assertTrue(
            erreurs.isEmpty(),
            "MainActivity du banc, SANS racine générée : ZÉRO erreur (binding + R light) — erreurs réelles : $erreurs",
        )
    }
}

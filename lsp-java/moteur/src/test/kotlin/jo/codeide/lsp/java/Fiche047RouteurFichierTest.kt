package jo.codeide.lsp.java

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Fiche 047 — CompilationEnvironmentRouteurTest : le ROUTAGE fichier →
 * module (ADR 0215) — le serveur doit analyser un fichier avec le moteur de
 * SON module, pas avec le contexte défaut.
 */
class Fiche047RouteurFichierTest {

    private fun dossier(nom: String): Path =
        Files.createTempDirectory("fiche047-$nom").also { it.resolve("X.java").writeText("class X { }\n") }

    @Test
    fun sansRouteurComportementHistorique() {
        val environnement = EnvironnementCompilation()
        environnement.routeurFichierModule = null
        assertSame(
            environnement.moteurPourFichier("/quelconque/App.java"),
            environnement.moteur(),
            "sans routeur : le moteur du contexte DÉFAUT",
        )
    }

    @Test
    fun routeurAssocieLeMoteurDuModule() {
        val environnement = EnvironnementCompilation()
        val srcApp = dossier("src-app")
        environnement.remplacerContexteModule(":app", listOf(srcApp))
        environnement.routeurFichierModule = { chemin -> if (chemin.endsWith("App.java")) ":app" else null }
        val moteurRouté = environnement.moteurPourFichier("/projet/src/App.java")
        assertSame(environnement.moteur(":app"), moteurRouté, "routage nominal vers :app")
        assertNotSame(environnement.moteur(), moteurRouté, "distinct du défaut")
        // Un chemin non associé (routeur rendant null) retombe sur le défaut.
        assertSame(environnement.moteur(), environnement.moteurPourFichier("/projet/src/Autre.java"), "repli défaut")
    }

    @Test
    fun routeurVuAChaqueRequeteSansRePose() {
        val environnement = EnvironnementCompilation()
        val srcApp = dossier("src-app")
        environnement.remplacerContexteModule(":app", listOf(srcApp))
        // Routeur lisant un état MUABLE — initialement null.
        var moduleCourant: String? = null
        environnement.routeurFichierModule = { moduleCourant }
        val chemin = "/projet/src/App.java"
        assertSame(environnement.moteur(), environnement.moteurPourFichier(chemin), "première requête : défaut")
        // L'état change SANS re-pose du routeur.
        moduleCourant = ":app"
        assertSame(environnement.moteur(":app"), environnement.moteurPourFichier(chemin), "le routage suit l'INSTANTANÉ COURANT du modèle")
    }

    @Test
    fun routeurQuiJetteRepliSurDefaut() {
        val environnement = EnvironnementCompilation()
        environnement.routeurFichierModule = { throw IllegalStateException("dépôt illisible") }
        assertSame(
            environnement.moteur(),
            environnement.moteurPourFichier("/projet/src/App.java"),
            "un routeur fautif ne casse JAMAIS une requête",
        )
    }

    @Test
    fun fichiersDeModulesDistinctsMoteursDistincts() {
        val environnement = EnvironnementCompilation()
        val srcApp = dossier("src-app")
        val srcLib = dossier("src-lib")
        environnement.remplacerContexteModule(":app", listOf(srcApp))
        environnement.remplacerContexteModule(":lib", listOf(srcLib))
        environnement.routeurFichierModule = { chemin -> if (chemin.contains("/lib/")) ":lib" else ":app" }
        val moteurApp = environnement.moteurPourFichier("/projet/app/src/App.java")
        val moteurLib = environnement.moteurPourFichier("/projet/lib/src/Lib.java")
        assertNotSame(moteurApp, moteurLib, "isolation par module")
        assertSame(environnement.moteur(":app"), moteurApp)
        assertSame(environnement.moteur(":lib"), moteurLib)
    }

    @Test
    fun gardeParesseuseParModuleSansConstruction() {
        val environnement = EnvironnementCompilation()
        val srcApp = dossier("src-app-garde")
        environnement.remplacerContexteModule(":app", listOf(srcApp))
        environnement.routeurFichierModule = { ":app" }
        val chemin = "/projet/src/App.java"
        assertFalse(environnement.moteurInitialise(":app"), "avant toute requête : pas de moteur")
        environnement.moteurPourFichier(chemin)
        assertTrue(environnement.moteurInitialise(":app"), "après UN jdtPourFichier : moteur construit")
        assertTrue(environnement.moteurInitialisePour(chemin), "garde par chemin équivalente")
        assertFalse(environnement.moteurInitialise(null), "le contexte DÉFAUT reste NON construit")
    }

    @Test
    fun moteursVivantsDefautPuisModules() {
        val environnement = EnvironnementCompilation()
        val srcLib = dossier("src-lib-vivants")
        environnement.remplacerContexteModule(":lib", listOf(srcLib))
        environnement.routeurFichierModule = { chemin -> if (chemin.endsWith("Lib.java")) ":lib" else null }
        assertTrue(environnement.moteursVivants().isEmpty(), "avant tout : vide")
        // Construction du défaut PUIS d'un fichier :lib.
        environnement.moteur()
        environnement.moteurPourFichier("/projet/lib/src/Lib.java")
        val vivants = environnement.moteursVivants()
        assertEquals(2, vivants.size, "exactement 2 moteurs vivants")
        assertSame(environnement.moteur(), vivants[0], "le DÉFAUT en PREMIÈRE position")
    }

    @Test
    fun remplacementDeContexteInvalideLeMoteurDuSeulModule() {
        val environnement = EnvironnementCompilation()
        val srcAppAvant = dossier("src-app-avant")
        val srcAppApres = dossier("src-app-apres")
        val srcLib = dossier("src-lib-inval")
        environnement.remplacerContexteModule(":app", listOf(srcAppAvant))
        environnement.remplacerContexteModule(":lib", listOf(srcLib))
        environnement.routeurFichierModule = { chemin -> if (chemin.contains("App")) ":app" else ":lib" }
        val cheminApp = "/projet/src/App.java"
        val cheminLib = "/projet/src/Lib.java"
        val moteurApp1 = environnement.moteurPourFichier(cheminApp)
        val moteurLib1 = environnement.moteurPourFichier(cheminLib)
        // Remplacement EFFECTIF du contexte de :app.
        environnement.remplacerContexteModule(":app", listOf(srcAppApres))
        // V-F1 (amendement) : la reconstruction est asynchrone (double-tampon)
        // — drainage avant d'observer le moteur reconstruit au prochain routage.
        assertTrue(environnement.attendreReconstructions(), "la reconstruction de :app draine")
        assertNotSame(moteurApp1, environnement.moteurPourFichier(cheminApp), "le moteur de :app est reconstruit au prochain routage")
        assertSame(moteurLib1, environnement.moteurPourFichier(cheminLib), "le moteur de :lib VIT — jamais invalidé par la sync de :app")
    }
}

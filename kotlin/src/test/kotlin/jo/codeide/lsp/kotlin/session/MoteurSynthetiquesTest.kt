// CodeIDE — tests du câblage moteur des classes synthétiques (module
// :kotlin, étage 1 vague 1e). Tests NEUFS jjoblab : le moteur noyau pose
// les racines sources au synthétiseur AU MOMENT de la synchronisation du
// classpath (ADR 0211 — une sync par classement côté hôte), les groupes
// sont consultables en interne (la consommation par les requêtes — fusion
// avec coupure — est l'étage 2).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import jo.codeide.lsp.kotlin.api.ClasspathKotlin

/**
 * Tests du câblage [MoteurLangageKotlinNoyau] → [groupesSynthetiques] :
 * la définition du classpath moteur nourrit les classes synthétiques des
 * racines sources (invalidation immédiate au changement, calcul paresseux
 * au premier consult) ; sans racines Android conventionnelles, aucun
 * groupe (sous-rapport honnête D3).
 */
class MoteurSynthetiquesTest {

    /** La racine temporaire (projets factices isolés). */
    @TempDir
    lateinit var dossier: Path

    /** Écrit un fichier (parents créés). */
    private fun ecrire(chemin: Path, contenu: String): Path {
        Files.createDirectories(chemin.parent)
        Files.writeString(chemin, contenu)
        return chemin
    }

    /** Pose un projet Android factice ; rend sa racine source. */
    private fun projetFactice(): Path {
        ecrire(dossier.resolve("src/main/AndroidManifest.xml"), """<manifest package="com.ex.app"/>""")
        ecrire(
            dossier.resolve("src/main/res/layout/activity_main.xml"),
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:id="@+id/textView"/>
            </LinearLayout>
            """.trimIndent(),
        )
        ecrire(
            dossier.resolve("src/main/res/values/strings.xml"),
            """<resources><string name="app_nom">App</string></resources>""",
        )
        return Files.createDirectories(dossier.resolve("src/main/java"))
    }

    /** La définition du classpath moteur nourrit les groupes synthétiques. */
    @Test
    fun moteurNourritLesSynthetiquesALaDefinition() {
        val racine = projetFactice()
        val moteur = MoteurLangageKotlinNoyau()
        moteur.definirClasspath(
            ClasspathKotlin(entrees = emptyList(), racinesSources = listOf(racine.toString())),
        )
        val fqcns = moteur.groupesSynthetiques().map { it.fqcn }.toSet()
        assertEquals(
            setOf(
                "com.ex.app.R",
                "com.ex.app.BuildConfig",
                "com.ex.app.databinding.ActivityMainBinding",
            ),
            fqcns,
        )
    }

    /** Mêmes racines re-posées : le cache tient ; changement : re-calcul. */
    @Test
    fun redefinitionRacinesCachePuisInvalidation() {
        val racine = projetFactice()
        val moteur = MoteurLangageKotlinNoyau()
        val classpath = ClasspathKotlin(entrees = emptyList(), racinesSources = listOf(racine.toString()))
        moteur.definirClasspath(classpath)
        val avant = moteur.groupesSynthetiques().map { it.fqcn }.toSet()

        // Mutation disque SANS changement de racines : le cache tient.
        ecrire(
            dossier.resolve("src/main/res/layout/autre.xml"),
            """<FrameLayout/>""",
        )
        moteur.definirClasspath(classpath)
        assertEquals(avant, moteur.groupesSynthetiques().map { it.fqcn }.toSet())

        // Changement de racines : la pose suivante re-calculera.
        val auxiliaire = Files.createDirectories(dossier.resolve("ailleurs"))
        moteur.definirClasspath(
            ClasspathKotlin(
                entrees = emptyList(),
                racinesSources = listOf(racine.toString(), auxiliaire.toString()),
            ),
        )
        val apres = moteur.groupesSynthetiques().map { it.fqcn }
        assertTrue("com.ex.app.databinding.AutreBinding" in apres)
    }

    /** Sans racines (ou sans convention Android) : aucun groupe. */
    @Test
    fun sansRacinesAucunGroupe() {
        val moteur = MoteurLangageKotlinNoyau()
        moteur.definirClasspath(ClasspathKotlin(entrees = emptyList(), racinesSources = emptyList()))
        assertEquals(emptyList(), moteur.groupesSynthetiques())
    }
}

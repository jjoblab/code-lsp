// CodeIDE — tests des groupes synthétiques Android (module :kotlin, étage
// 1 vague 1e). Tests NEUFS jjoblab : AUCUN test source dédié à la zone
// synthetics/ n'existe dans le cahier (la capacité y est éprouvée via les
// fiches 039/040/045/047 des étages 2–3) ; le présent contrat est spécifié
// par le KDoc du module repris (R2 — sources DÉMONTRABLES seules, coupure
// à la fusion, paresseux invalidé au changement de racines, bornes D3).
package jo.codeide.lsp.kotlin.synthetics

import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tests de [ClassesSynthetiques] — les groupes synthétiques Android : `R`,
 * `BuildConfig`, ViewBinding/DataBinding AVANT TOUT BUILD. Chaque groupe
 * est un FQCN de tête plus ses déclarations ; les sources sont
 * DÉMONTRABLES seules (convention AGP : racine `src/main/java|kotlin`,
 * manifeste avec attribut `package`, répertoire frère `res`) ; le calcul
 * est PARESSEUX et invalidé au SEUL changement de racines ; les familles
 * sont BORNÉES (troncation honnête D3).
 */
class ClassesSynthetiquesTest {

    /** La racine temporaire (projets factices isolés). */
    @TempDir
    lateinit var dossier: Path

    /** Écrit un fichier (parents créés). */
    private fun ecrire(chemin: Path, contenu: String): Path {
        Files.createDirectories(chemin.parent)
        Files.writeString(chemin, contenu)
        return chemin
    }

    /**
     * Pose un projet Android factice COMPLET sous [base] : manifeste
     * `com.ex.app`, un layout ViewBinding (`activity_main`), un layout
     * DataBinding (`ecran_compte`), un `<merge>` (`item_liste`), des
     * valeurs, un drawable, un mipmap.
     *
     * @return la racine SOURCE (`src/main/java`) à poser au synthétiseur.
     */
    private fun projetComplet(base: Path): Path {
        ecrire(base.resolve("src/main/AndroidManifest.xml"), """<manifest package="com.ex.app"/>""")
        ecrire(
            base.resolve("src/main/res/layout/activity_main.xml"),
            """
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:id="@+id/textView"/>
            </ScrollView>
            """.trimIndent(),
        )
        ecrire(
            base.resolve("src/main/res/layout/ecran_compte.xml"),
            """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable name="utilisateur" type="com.ex.app.Utilisateur"/>
                </data>
                <LinearLayout>
                    <EditText android:id="@+id/champ"/>
                </LinearLayout>
            </layout>
            """.trimIndent(),
        )
        ecrire(
            base.resolve("src/main/res/layout/item_liste.xml"),
            """<merge xmlns:android="http://schemas.android.com/apk/res/android"><TextView android:id="@+id/titre"/></merge>""",
        )
        ecrire(
            base.resolve("src/main/res/values/strings.xml"),
            """<resources><string name="app_nom">App</string><string name="autre">Autre</string></resources>""",
        )
        ecrire(
            base.resolve("src/main/res/values/colors.xml"),
            """<resources><color name="accent">#FF0000</color></resources>""",
        )
        ecrire(base.resolve("src/main/res/drawable/ic_logo.xml"), "<vector/>")
        ecrire(base.resolve("src/main/res/mipmap/ic_launcher.png"), "")
        return Files.createDirectories(base.resolve("src/main/java"))
    }

    /** Les déclarations d'un groupe, indexées par nom. */
    private fun parNom(groupe: ClassesSynthetiques.GroupeSynthetique): Map<String, DeclarationProjet> =
        groupe.declarations.associateBy { it.nom }

    // ------------------------------------------------------------------
    // Catalogue des groupes.
    // ------------------------------------------------------------------

    /** Projet complet : R, BuildConfig, deux bindings — `<merge>` exclu. */
    @Test
    fun groupesDuProjetAndroidComplet() {
        val racine = projetComplet(dossier)
        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        val fqcns = classes.groupes().map { it.fqcn }.toSet()
        assertEquals(
            setOf(
                "com.ex.app.R",
                "com.ex.app.BuildConfig",
                "com.ex.app.databinding.ActivityMainBinding",
                "com.ex.app.databinding.EcranCompteBinding",
            ),
            fqcns,
        )
    }

    /** Groupe `R` : classe de tête, sous-objets par famille, entrées `Int`. */
    @Test
    fun groupeRFamillesEtMembres() {
        val racine = projetComplet(dossier)
        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        val groupeR = classes.groupes().first { it.fqcn == "com.ex.app.R" }
        val parNom = parNom(groupeR)

        val classeR = parNom.getValue("R")
        assertEquals(GenreDeclaration.CLASSE, classeR.genre)
        assertEquals("com.ex.app", classeR.paquet)
        assertNull(classeR.conteneur)
        assertNull(classeR.typeRetour)

        val familleLayout = parNom.getValue("layout")
        assertEquals(GenreDeclaration.OBJET, familleLayout.genre)
        assertEquals("com.ex.app.R", familleLayout.conteneur)
        assertEquals("com.ex.app.R.layout", familleLayout.typeRetour)

        val entree = parNom.getValue("app_nom")
        assertEquals(GenreDeclaration.PROPRIETE, entree.genre)
        assertEquals("Int", entree.typeRetour)
        assertEquals("com.ex.app.R.string", entree.conteneur)
        assertEquals("com.ex.app", entree.paquet)

        // Familles démontrées : values (string, color), layouts, ids,
        // fichiers (drawable, mipmap).
        assertEquals(
            setOf("layout", "string", "color", "id", "drawable", "mipmap"),
            groupeR.declarations.filter { it.genre == GenreDeclaration.OBJET }.map { it.nom }.toSet(),
        )
        assertEquals("com.ex.app.R.layout", parNom.getValue("activity_main").conteneur)
        assertEquals("com.ex.app.R.id", parNom.getValue("textView").conteneur)
        assertEquals("com.ex.app.R.drawable", parNom.getValue("ic_logo").conteneur)
        assertEquals("com.ex.app.R.mipmap", parNom.getValue("ic_launcher").conteneur)
        // Les identifiants des TROIS layouts nourrissent la même famille.
        assertEquals(
            setOf("textView", "champ", "titre"),
            groupeR.declarations.filter { it.conteneur == "com.ex.app.R.id" }.map { it.nom }.toSet(),
        )
    }

    /** Groupe `BuildConfig` : la CLASSE seule (les champs appartiennent au build). */
    @Test
    fun groupeBuildConfigClasseSeule() {
        val racine = projetComplet(dossier)
        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        val groupe = classes.groupes().first { it.fqcn == "com.ex.app.BuildConfig" }
        assertEquals(1, groupe.declarations.size)
        val classe = groupe.declarations.single()
        assertEquals(GenreDeclaration.CLASSE, classe.genre)
        assertEquals("BuildConfig", classe.nom)
        assertEquals("com.ex.app", classe.paquet)
        assertNull(classe.conteneur)
    }

    /** ViewBinding : sous-paquet `databinding` (ADR 0219), ids + root + inflate/bind. */
    @Test
    fun bindingViewBindingSousPaquetDatabinding() {
        val racine = projetComplet(dossier)
        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        val groupe = classes.groupes().first { it.fqcn == "com.ex.app.databinding.ActivityMainBinding" }
        val parNom = parNom(groupe)

        val classe = parNom.getValue("ActivityMainBinding")
        assertEquals(GenreDeclaration.CLASSE, classe.genre)
        assertEquals("com.ex.app.databinding", classe.paquet)
        assertTrue(classe.aVararg)

        val champ = parNom.getValue("textView")
        assertEquals(GenreDeclaration.PROPRIETE, champ.genre)
        assertEquals("android.widget.TextView", champ.typeRetour)
        assertEquals("com.ex.app.databinding.ActivityMainBinding", champ.conteneur)
        assertTrue(champ.uri.endsWith("activity_main.xml"))

        val root = parNom.getValue("root")
        assertEquals("android.widget.ScrollView", root.typeRetour)

        for (methode in listOf("inflate", "bind")) {
            val declaration = parNom.getValue(methode)
            assertEquals(GenreDeclaration.FONCTION, declaration.genre)
            assertEquals("ActivityMainBinding", declaration.typeRetour)
            assertEquals("com.ex.app.databinding.ActivityMainBinding", declaration.conteneur)
            assertTrue(declaration.aVararg)
            assertTrue(declaration.parametres.isEmpty())
        }
    }

    /** DataBinding : variables `<data>` comme propriétés, PAS de `root`. */
    @Test
    fun bindingDataBindingVariablesSansRoot() {
        val racine = projetComplet(dossier)
        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        val groupe = classes.groupes().first { it.fqcn == "com.ex.app.databinding.EcranCompteBinding" }
        val parNom = parNom(groupe)
        val variable = parNom.getValue("utilisateur")
        assertEquals(GenreDeclaration.PROPRIETE, variable.genre)
        assertEquals("com.ex.app.Utilisateur", variable.typeRetour)
        assertEquals("android.widget.EditText", parNom.getValue("champ").typeRetour)
        assertNull(parNom["root"])
    }

    // ------------------------------------------------------------------
    // Paresse et invalidation.
    // ------------------------------------------------------------------

    /** No-op chaud aux MÊMES racines ; re-calcul au CHANGEMENT seul. */
    @Test
    fun paresseuxNoOpChaudEtInvalidationAuChangement() {
        val racine = projetComplet(dossier)
        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        val avant = classes.groupes().map { it.fqcn }.toSet()

        // Mutation disque SANS changement de racines : le cache tient
        // (le partiel servi reste celui du calcul courant).
        ecrire(
            racine.parent.resolve("res/layout/autre_ecran.xml"),
            """<FrameLayout/>""",
        )
        classes.definirRacines(listOf(racine.toFile()))
        assertEquals(avant, classes.groupes().map { it.fqcn }.toSet())

        // Changement de racines (une racine saine de plus) : re-calcul.
        val auxiliaire = Files.createDirectories(dossier.resolve("ailleurs"))
        classes.definirRacines(listOf(racine.toFile(), auxiliaire.toFile()))
        val apres = classes.groupes().map { it.fqcn }
        assertTrue("com.ex.app.databinding.AutreEcranBinding" in apres)
        assertEquals(avant + "com.ex.app.databinding.AutreEcranBinding", apres.toSet())
    }

    // ------------------------------------------------------------------
    // Garde-fous D3.
    // ------------------------------------------------------------------

    /** Une racine hors convention AGP ne produit RIEN (sous-rapport). */
    @Test
    fun racineHorsConventionNeProduitRien() {
        val ailleurs = Files.createDirectories(dossier.resolve("lib"))
        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(ailleurs.toFile()))
        assertEquals(emptyList(), classes.groupes())
    }

    /** Un manifeste sans attribut `package` ne produit RIEN. */
    @Test
    fun manifesteSansPaquetNeProduitRien() {
        val base = dossier.resolve("nu")
        ecrire(base.resolve("src/main/AndroidManifest.xml"), """<manifest/>""")
        ecrire(base.resolve("src/main/res/layout/main.xml"), """<FrameLayout/>""")
        val racine = Files.createDirectories(base.resolve("src/main/java"))
        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        assertEquals(emptyList(), classes.groupes())
    }

    /** Deux racines de MÊME paquet : la première gagne, la seconde est muette. */
    @Test
    fun paquetsDedupliquesPremiereRacineGagne() {
        val moduleA = dossier.resolve("a")
        val moduleB = dossier.resolve("b")
        ecrire(moduleA.resolve("src/main/AndroidManifest.xml"), """<manifest package="com.ex.app"/>""")
        ecrire(moduleA.resolve("src/main/res/layout/un.xml"), """<FrameLayout/>""")
        ecrire(moduleB.resolve("src/main/AndroidManifest.xml"), """<manifest package="com.ex.app"/>""")
        ecrire(moduleB.resolve("src/main/res/layout/deux.xml"), """<FrameLayout/>""")
        val racineA = Files.createDirectories(moduleA.resolve("src/main/java"))
        val racineB = Files.createDirectories(moduleB.resolve("src/main/java"))

        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racineA.toFile(), racineB.toFile()))
        val groupeR = classes.groupes().first { it.fqcn == "com.ex.app.R" }
        val layouts = groupeR.declarations.filter { it.conteneur == "com.ex.app.R.layout" }.map { it.nom }
        assertEquals(listOf("un"), layouts)
        assertEquals(1, classes.groupes().count { it.fqcn == "com.ex.app.R" })
    }

    /** Troncation honnête : 301 entrées `string` → 300 propriétés servies. */
    @Test
    fun borneTroisCentEntreesParFamille() {
        val base = dossier.resolve("born")
        ecrire(base.resolve("src/main/AndroidManifest.xml"), """<manifest package="com.ex.app"/>""")
        val morceaux = StringBuilder("<resources>")
        repeat(301) { indice -> morceaux.append("""<string name="s$indice">V</string>""") }
        morceaux.append("</resources>")
        ecrire(base.resolve("src/main/res/values/strings.xml"), morceaux.toString())
        val racine = Files.createDirectories(base.resolve("src/main/java"))

        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        val groupeR = classes.groupes().first { it.fqcn == "com.ex.app.R" }
        assertEquals(
            300,
            groupeR.declarations.count { it.conteneur == "com.ex.app.R.string" },
        )
    }

    /** Famille `color` : les noms de FICHIERS `res/color*` priment sur `values*`. */
    @Test
    fun familleColorFichiersPrimentSurValeurs() {
        val base = dossier.resolve("c")
        ecrire(base.resolve("src/main/AndroidManifest.xml"), """<manifest package="com.ex.app"/>""")
        ecrire(
            base.resolve("src/main/res/values/colors.xml"),
            """<resources><color name="accent">#FF0000</color></resources>""",
        )
        ecrire(base.resolve("src/main/res/color/selecteur.xml"), "<selector/>")
        val racine = Files.createDirectories(base.resolve("src/main/java"))

        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        val groupeR = classes.groupes().first { it.fqcn == "com.ex.app.R" }
        assertEquals(
            listOf("selecteur"),
            groupeR.declarations.filter { it.conteneur == "com.ex.app.R.color" }.map { it.nom },
        )
    }

    /** Lectures parallèles : une seule construction, un résultat cohérent. */
    @Test
    fun lecturesParallelesCoherentes() {
        val racine = projetComplet(dossier)
        val classes = ClassesSynthetiques()
        classes.definirRacines(listOf(racine.toFile()))
        val resultats = Collections.synchronizedList(mutableListOf<List<String>>())
        val fils =
            (1..2).map {
                Thread { resultats.add(classes.groupes().map { groupe -> groupe.fqcn }) }
                    .also(Thread::start)
            }
        fils.forEach(Thread::join)
        assertEquals(resultats[0], resultats[1])
        assertEquals(4, resultats[0].size)
    }
}

// CodeIDE — tests du versant « entrées » des classes synthétiques (module
// :kotlin, étage 1 vague 1e). Tests NEUFS jjoblab : AUCUN test source
// dédié à la zone synthetics/ n'existe dans le cahier (l'ancien dépôt
// n'en avait pas — la capacité y est éprouvée via les fiches 039/040/045/
// 047 des étages 2–3) ; le présent contrat est spécifié par le KDoc du
// module repris (R1 — partiel honnête D3, DOCTYPE refusé, bornes).
package jo.codeide.lsp.kotlin.synthetics

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tests de [AnalyseRessourcesAndroid] — la lecture CONSERVATRICE des
 * ressources Android : manifeste, layouts, valeurs, noms de fichiers,
 * types de vues. Toute entrée illisible est ABSENTE (partiel honnête D3 —
 * jamais d'exception vers l'appelant) ; les dossiers qualifiés
 * (`layout-land`, `values-night`) sont parcourus ; les parcours sont
 * BORNÉS (troncation honnête au-delà).
 */
class AnalyseRessourcesAndroidTest {

    /** La racine temporaire (projets factices isolés). */
    @TempDir
    lateinit var dossier: Path

    /** Écrit un fichier (parents créés). */
    private fun ecrire(chemin: Path, contenu: String): Path {
        Files.createDirectories(chemin.parent)
        Files.writeString(chemin, contenu)
        return chemin
    }

    // ------------------------------------------------------------------
    // Manifeste.
    // ------------------------------------------------------------------

    /** Le paquet déclaré du manifeste est lu tel quel. */
    @Test
    fun paquetDuManifestLu() {
        val manifest =
            ecrire(
                dossier.resolve("src/main/AndroidManifest.xml"),
                """<manifest package="com.ex.app"/>""",
            )
        assertEquals("com.ex.app", AnalyseRessourcesAndroid.paquetDuManifest(manifest.toFile()))
    }

    /** Manifeste sans attribut `package` (ou blanc), ou absent : `null`. */
    @Test
    fun manifesteSansPaquetRendNull() {
        val sansAttribut =
            ecrire(dossier.resolve("a/src/main/AndroidManifest.xml"), """<manifest/>""")
        assertNull(AnalyseRessourcesAndroid.paquetDuManifest(sansAttribut.toFile()))
        val blanc =
            ecrire(dossier.resolve("b/src/main/AndroidManifest.xml"), """<manifest package="  "/>""")
        assertNull(AnalyseRessourcesAndroid.paquetDuManifest(blanc.toFile()))
        assertNull(
            AnalyseRessourcesAndroid.paquetDuManifest(dossier.resolve("inexistant.xml").toFile()),
        )
    }

    /** Un XML cassé est ABSENT (partiel honnête D3 — jamais d'exception). */
    @Test
    fun manifesteIllisibleRendNull() {
        val casse =
            ecrire(dossier.resolve("src/main/AndroidManifest.xml"), "<manifest package=")
        assertNull(AnalyseRessourcesAndroid.paquetDuManifest(casse.toFile()))
    }

    /** Un DOCTYPE est REFUSÉ (entrée externe = rejet du fichier). */
    @Test
    fun doctypeRefuseRendNull() {
        val avecDoctype =
            ecrire(
                dossier.resolve("src/main/AndroidManifest.xml"),
                """<!DOCTYPE manifest SYSTEM "http://externe.example/entite"><manifest/>""",
            )
        assertNull(AnalyseRessourcesAndroid.paquetDuManifest(avecDoctype.toFile()))
    }

    // ------------------------------------------------------------------
    // Layouts.
    // ------------------------------------------------------------------

    /** Deux layouts avec identifiants, racines et ordre déterministe. */
    @Test
    fun layoutsLusAvecIdsRacineEtOrdreDeterministe() {
        val res = dossier.resolve("res")
        ecrire(
            res.resolve("layout/activity_main.xml"),
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:id="@+id/textView"/>
                <Button android:id="@+id/action"/>
            </LinearLayout>
            """.trimIndent(),
        )
        ecrire(
            res.resolve("layout/ecran_compte.xml"),
            """<FrameLayout/>""",
        )
        ecrire(
            res.resolve("layout-land/vue_paysage.xml"),
            """<ScrollView/>""",
        )
        val layouts = AnalyseRessourcesAndroid.layouts(res.toFile())
        assertEquals(
            listOf("activity_main.xml", "ecran_compte.xml", "vue_paysage.xml"),
            layouts.map { it.fichier.name },
        )
        val activityMain = layouts.first { it.fichier.name == "activity_main.xml" }
        assertEquals("LinearLayout", activityMain.racine)
        assertEquals("android.widget.TextView", activityMain.ids["textView"])
        assertEquals("android.widget.Button", activityMain.ids["action"])
        assertTrue(!activityMain.dataBinding)
        assertEquals(emptyList(), activityMain.variables)
    }

    /** Le PREMIER porteur d'un identifiant gagne (doublon = premier). */
    @Test
    fun idsPremierPorteurGagne() {
        val res = dossier.resolve("res")
        ecrire(
            res.resolve("layout/main.xml"),
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:id="@+id/meme"/>
                <Button android:id="@+id/meme"/>
            </LinearLayout>
            """.trimIndent(),
        )
        val ids = AnalyseRessourcesAndroid.layouts(res.toFile()).single().ids
        assertEquals("android.widget.TextView", ids["meme"])
    }

    /** Un layout `<merge>` nourrit les identifiants, racine `merge`. */
    @Test
    fun layoutMergeNourritLesIds() {
        val res = dossier.resolve("res")
        ecrire(
            res.resolve("layout/item.xml"),
            """<merge xmlns:android="http://schemas.android.com/apk/res/android"><TextView android:id="@+id/titre"/></merge>""",
        )
        val analyse = AnalyseRessourcesAndroid.layouts(res.toFile()).single()
        assertEquals("merge", analyse.racine)
        assertEquals("android.widget.TextView", analyse.ids["titre"])
    }

    /** Un layout DataBinding : racine `<layout>`, variables `<data>`. */
    @Test
    fun layoutDatabindingVariablesEtRacineLayout() {
        val res = dossier.resolve("res")
        ecrire(
            res.resolve("layout/ecran.xml"),
            """
            <layout>
                <data>
                    <variable name="utilisateur" type="com.ex.app.Utilisateur"/>
                    <variable name=" " type="com.ex.app.Vide"/>
                </data>
                <LinearLayout/>
            </layout>
            """.trimIndent(),
        )
        val analyse = AnalyseRessourcesAndroid.layouts(res.toFile()).single()
        assertTrue(analyse.dataBinding)
        assertEquals("layout", analyse.racine)
        assertEquals(listOf("utilisateur" to "com.ex.app.Utilisateur"), analyse.variables)
    }

    // ------------------------------------------------------------------
    // Valeurs.
    // ------------------------------------------------------------------

    /** Familles de `values*` fusionnées, `string-array` → `array`, styles exclus. */
    @Test
    fun entreesValeursParFamilleFusionnees() {
        val res = dossier.resolve("res")
        ecrire(
            res.resolve("values/strings.xml"),
            """
            <resources>
                <string name="app_nom">App</string>
                <string name="autre">Autre</string>
                <string-array name="roles">
                    <item>admin</item>
                </string-array>
                <style name="MonStyle"/>
                <string name=" "/>
            </resources>
            """.trimIndent(),
        )
        ecrire(
            res.resolve("values-night/colors.xml"),
            """<resources><color name="nuit">#000000</color></resources>""",
        )
        ecrire(
            res.resolve("values/dimens.xml"),
            """<resources><dimen name="marge">8dp</dimen></resources>""",
        )
        val familles = AnalyseRessourcesAndroid.entreesValeurs(res.toFile())
        assertEquals(listOf("app_nom", "autre"), familles["string"])
        assertEquals(listOf("nuit"), familles["color"])
        assertEquals(listOf("marge"), familles["dimen"])
        assertEquals(listOf("roles"), familles["array"])
        assertNull(familles["style"])
    }

    // ------------------------------------------------------------------
    // Noms de fichiers et types de vues.
    // ------------------------------------------------------------------

    /** Noms de fichiers sans extension, dossiers qualifiés compris. */
    @Test
    fun nomsFichiersSansExtension() {
        val res = dossier.resolve("res")
        ecrire(res.resolve("drawable/ic_logo.xml"), "<vector/>")
        ecrire(res.resolve("mipmap/ic_launcher.png"), "")
        ecrire(res.resolve("raw/donnees.dat"), "")
        assertEquals(listOf("ic_logo"), AnalyseRessourcesAndroid.nomsFichiers(res.toFile(), "drawable"))
        assertEquals(listOf("ic_launcher"), AnalyseRessourcesAndroid.nomsFichiers(res.toFile(), "mipmap"))
        assertEquals(listOf("donnees"), AnalyseRessourcesAndroid.nomsFichiers(res.toFile(), "raw"))
        assertEquals(emptyList(), AnalyseRessourcesAndroid.nomsFichiers(res.toFile(), "anim"))
    }

    /** Un dossier QUALIFIÉ seul (sans dossier de base) ne produit rien. */
    @Test
    fun dossierQualifieSeulMuet() {
        val res = dossier.resolve("res")
        ecrire(res.resolve("mipmap-hdpi/ic_launcher.png"), "")
        assertEquals(emptyList(), AnalyseRessourcesAndroid.nomsFichiers(res.toFile(), "mipmap"))
    }

    /** Types de vues : connus mappés, qualifiés tels quels, inconnu nu → View. */
    @Test
    fun typeVueConnuQualifieInconnu() {
        assertEquals("android.widget.TextView", AnalyseRessourcesAndroid.typeVue("TextView"))
        assertEquals("android.view.View", AnalyseRessourcesAndroid.typeVue("InconnuNu"))
        assertEquals("com.ex.vue.MaVue", AnalyseRessourcesAndroid.typeVue("com.ex.vue.MaVue"))
    }

    /** Troncation honnête : au-delà de la borne, les fichiers sont coupés. */
    @Test
    fun borneFichiersTroncatureHonnete() {
        val drawable = dossier.resolve("res/drawable")
        Files.createDirectories(drawable)
        repeat(401) { indice -> Files.writeString(drawable.resolve("f$indice.xml"), "<vector/>") }
        assertEquals(
            400,
            AnalyseRessourcesAndroid.nomsFichiers(dossier.resolve("res").toFile(), "drawable").size,
        )
    }
}

package jo.codeide.lsp.java.projetandroid

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Dérivateur disque + lecteur XML tolérant + référentiel de ressources
 * (vague V-B1 — ADR 0020, décision D2) : le moteur connaît le projet
 * Android SANS Gradle. Cas dorés versionnés sur la fixture `mini`
 * (module complet miniature) et le banc réel `bancs/projet-android`.
 *
 * Oracle des attentes : les règles AGP 8 documentées (référence §B10 pour
 * le contrat, §B8 pour le référentiel) et les fichiers de la fixture —
 * jamais de sortie AGP exécutée (aucun build dans ce banc).
 */
class ModeleAndroidDeriveTest {

    private companion object {
        /** La fixture mini (chemin relatif au module :java). */
        val MINI: Path = Path.of("src/test/resources/projetandroid/mini/app/src/main/java")

        /** Le banc projet Android réel (racine des sources :app). */
        val MONIP: Path =
            sequenceOf(
                Path.of("../bancs/projet-android/app/src/main/java"),
                Path.of("../../bancs/projet-android/app/src/main/java"),
            ).firstOrNull { Files.isDirectory(it) } ?: Path.of("inexistant")

        /** Vrai ssi le banc réel est présent (checkout complet). */
        val MONIP_PRESENT: Boolean = Files.isDirectory(MONIP)
    }

    // ------------------------------------------------------------------ //
    // Lecteur XML tolérant
    // ------------------------------------------------------------------ //

    @Test
    fun `le lecteur xml rend elements attributs et positions de valeurs`() {
        val xml = """<LinearLayout a="x" b='y'><TextView android:id="@+id/t"/></LinearLayout>"""
        val lecteur = LecteurXmlTolerant(xml)
        val evenements = mutableListOf<LecteurXmlTolerant.EvenementXml>()
        while (true) {
            evenements.add(lecteur.suivant() ?: break)
        }
        assertEquals(4, evenements.size, "début(LinearLayout), début(TextView), fin, fin")
        val debut = evenements[0] as LecteurXmlTolerant.EvenementXml.Debut
        assertEquals("LinearLayout", debut.nom)
        assertEquals("x", debut.attributs["a"])
        assertEquals("y", debut.attributs["b"])
        // La position de la valeur de a désigne le 'x' dans la source.
        assertEquals(xml.indexOf('x'), debut.positionsValeurs["a"])
        val vue = evenements[1] as LecteurXmlTolerant.EvenementXml.Debut
        assertEquals("TextView", vue.nom)
        assertEquals("@+id/t", vue.attributs["id"])
        assertEquals(xml.indexOf("@+id/t"), vue.positionsValeurs["id"])
    }

    @Test
    fun `le lecteur xml decode entites cdata et supporte le tronque`() {
        // Entités prédéfinies + numérique + CDATA + commentaire + PI.
        val xml = "<r t=\"a &amp; b &#65;\"> <![CDATA[ <brut> ]]> <?pi ignore?> <!-- c --> </r>"
        val lecteur = LecteurXmlTolerant(xml)
        val evenements = mutableListOf<LecteurXmlTolerant.EvenementXml>()
        while (true) {
            evenements.add(lecteur.suivant() ?: break)
        }
        val debut = evenements[0] as LecteurXmlTolerant.EvenementXml.Debut
        assertEquals("a & b A", debut.attributs["t"])
        assertTrue(evenements.any { it is LecteurXmlTolerant.EvenementXml.Texte && it.contenu.contains("<brut>") }, "CDATA rendu comme texte")

        // Document tronqué : pas d'exception, ce qui est lu est rendu.
        val tronque = LecteurXmlTolerant("<a><b att=\"v")
        var vus = 0
        while (true) {
            tronque.suivant() ?: break
            vus++
        }
        assertTrue(vus >= 1, "au moins l'élément <a> est rendu")
    }

    // ------------------------------------------------------------------ //
    // Dérivateur disque
    // ------------------------------------------------------------------ //

    @Test
    fun `le derivateur lit le contrat complet du module mini`() {
        val contrat = ModeleAndroidDerive.deriver(listOf(MINI))

        assertNotNull(contrat, "mini est un module Android (plugin + manifest + res/)")
        assertEquals("com.exemple.mini", contrat.namespace)
        assertEquals("com.exemple.mini", contrat.applicationId)
        assertEquals(21, contrat.minSdk)
        assertEquals(34, contrat.targetSdk)
        assertEquals(34, contrat.compileSdk)
        assertTrue(contrat.viewBinding, "viewBinding = true (KTS)")
        assertTrue(contrat.buildConfig, "buildConfig = true (KTS)")
        assertFalse(contrat.dataBinding, "dataBinding absent = défaut AGP 8 (faux)")
        assertTrue(contrat.nonTransitiveRClass, "défaut AGP 8")
        assertTrue(contrat.actif, "namespace + res/ ⇒ classes light actives")
        assertTrue(
            contrat.dossiersRes.any { it.endsWith("src/main/res") },
            "res/ par convention src/main/res",
        )
        assertEquals(1, contrat.dossiersRes.size, "aucun srcDirs additionnel déclaré")
        assertNotNull(contrat.manifest)
    }

    @Test
    fun `le derivateur rend null sur un module non android`() {
        val dossier = Files.createTempDirectory("derive-non-android")
        try {
            Files.createDirectories(dossier.resolve("src/main/java"))
            assertNull(ModeleAndroidDerive.deriver(listOf(dossier.resolve("src/main/java"))))
        } finally {
            dossier.toFile().deleteRecursively()
        }
    }

    @Test
    fun `le derivateur lit le banc reel monip - dataBinding et viewBinding tous deux actifs`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(MONIP_PRESENT, "banc projet-android absent du checkout")
        val contrat = ModeleAndroidDerive.deriver(listOf(MONIP))

        assertNotNull(contrat)
        assertEquals("com.example.monip", contrat.namespace)
        assertEquals(24, contrat.minSdk)
        assertEquals(34, contrat.targetSdk)
        assertEquals(34, contrat.compileSdk)
        assertTrue(contrat.dataBinding)
        assertTrue(contrat.viewBinding)
        assertFalse(contrat.buildConfig, "défaut AGP 8 : pas de BuildConfig sans buildFeatures")
    }

    // ------------------------------------------------------------------ //
    // Référentiel de ressources (fixture mini — oracle : la fixture elle-même)
    // ------------------------------------------------------------------ //

    @Test
    fun `le referentiel enumere les ressources par type - regles aapt`() {
        val contrat = ModeleAndroidDerive.deriver(listOf(MINI))!!
        val referentiel = ReferentielRessources(contrat.dossiersRes)

        assertEquals(setOf("ecran_donnees", "ecran_principal"), referentiel.noms("layout"))
        assertEquals(
            setOf("app_nom", "salut", "pluriel_zero"),
            referentiel.noms("string"),
        )
        assertEquals(setOf("fond", "texte"), referentiel.noms("color"))
        assertEquals(setOf("marge", "taille_titre"), referentiel.noms("dimen"))
        assertEquals(setOf("mode_nuit"), referentiel.noms("bool"))
        assertEquals(setOf("nb_colonnes"), referentiel.noms("integer"))
        assertEquals(setOf("jours", "poids"), referentiel.noms("array"))
        assertEquals(setOf("nb_messages"), referentiel.noms("plurals"))
        assertEquals(setOf("Theme_Mini"), referentiel.noms("style"), "règle aapt : le point devient un underscore")
        assertEquals(setOf("Exemple", "Exemple_libelle", "Exemple_poidsMax"), referentiel.noms("styleable"), "trois familles de champs")
        assertEquals(setOf("ic_logo"), referentiel.noms("drawable"))
        assertEquals(setOf("menu_principal"), referentiel.noms("menu"))
        // ids : layouts (2 configs) + menu + values/ids.xml — dédoublonnés.
        assertEquals(
            setOf("text_titre", "bouton_valider", "vue_perso", "vue_anonyme", "action_parametres"),
            referentiel.noms("id"),
        )
    }

    @Test
    fun `le referentiel extrait les metadonnees de binding - types variables et vue par configuration`() {
        val contrat = ModeleAndroidDerive.deriver(listOf(MINI))!!
        val referentiel = ReferentielRessources(contrat.dossiersRes)
        val binding = referentiel.binding("ecran_principal")

        assertNotNull(binding)
        assertFalse(binding.estDataBinding, "racine <LinearLayout> : view binding")
        assertEquals("android.widget.TextView", binding.vues["text_titre"])
        assertEquals("android.widget.Button", binding.vues["bouton_valider"])
        // tools:viewBindingType PRIME sur la balise (règle B9).
        assertEquals("com.exemple.mini.VueLiee", binding.vues["vue_perso"])
        // Même type dans la configuration land : le champ reste TextView.
        assertEquals(2, binding.fichiers.size, "layout + layout-land")
        assertTrue(binding.variables.isEmpty(), "pas de <data> en view binding")
    }

    @Test
    fun `le referentiel vise les cibles exactes - offsets des declarations`() {
        val contrat = ModeleAndroidDerive.deriver(listOf(MINI))!!
        val referentiel = ReferentielRessources(contrat.dossiersRes)

        val (fichierLayout, offsetLayout) = referentiel.cible("layout", "ecran_principal")!!
        assertTrue(fichierLayout.endsWith("res/layout/ecran_principal.xml"), "le PRINCIPAL d'abord")

        val (fichierId, offsetId) = referentiel.cible("id", "text_titre")!!
        val texte = Files.readString(fichierId)
        assertTrue(
            texte.regionMatches(offsetId, "text_titre", 0, "text_titre".length),
            "l'offset désigne le NOM dans la valeur de android:id (@+id/…)",
        )

        val (fichierString, offsetString) = referentiel.cible("string", "app_nom")!!
        val texteValues = Files.readString(fichierString)
        assertTrue(texteValues.regionMatches(offsetString, "app_nom", 0, 6), "l'offset désigne la valeur de name")

        val styleable = referentiel.cibleStyleable("Exemple", "libelle")!!
        assertTrue(styleable.first.endsWith("ressources.xml"))
    }

    @Test
    fun `la maj ciblee rafraichit un fichier sans rescan - et reste idempotente`() {
        val dossier = Files.createTempDirectory("referentiel-maj")
        try {
            val res = Files.createDirectories(dossier.resolve("res/layout"))
            val layout = res.resolve("ecran.xml")
            Files.writeString(layout, "<LinearLayout><TextView android:id=\"@+id/a\"/></LinearLayout>")
            val referentiel = ReferentielRessources(listOf(res))
            assertEquals(setOf("a"), referentiel.noms("id"))

            // didChange : l'id a change de nom.
            Files.writeString(layout, "<LinearLayout><TextView android:id=\"@+id/b\"/><Button android:id=\"@+id/c\"/></LinearLayout>")
            referentiel.rafraichirFichier(layout)
            assertEquals(setOf("b", "c"), referentiel.noms("id"), "l'ancien id a disparu, les nouveaux sont là")
            assertEquals(setOf("ecran"), referentiel.noms("layout"))

            // Idempotence (P2 étendu aux XML) : re-rafrachir le même contenu.
            referentiel.rafraichirFichier(layout)
            assertEquals(setOf("b", "c"), referentiel.noms("id"))
        } finally {
            dossier.toFile().deleteRecursively()
        }
    }

    @Test
    fun `le referentiel du banc monip equivoque le R genere du banc - zero ecart`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(MONIP_PRESENT, "banc projet-android absent du checkout")
        val contrat = ModeleAndroidDerive.deriver(listOf(MONIP))!!
        val referentiel = ReferentielRessources(contrat.dossiersRes)

        // Oracle : bancs/projet-android-genere/com/example/monip/R.java —
        // le simulacre commis de la sortie AGP (layout + id, uniquement).
        assertEquals(setOf("activity_main"), referentiel.noms("layout"))
        assertEquals(setOf("text_titre"), referentiel.noms("id"))
        val binding = referentiel.binding("activity_main")!!
        assertTrue(binding.estDataBinding, "racine <layout> : data binding")
        assertEquals("android.widget.TextView", binding.vues["text_titre"])
        assertEquals(listOf("liaison" to "com.example.monip.Greeter"), binding.variables)
    }
}

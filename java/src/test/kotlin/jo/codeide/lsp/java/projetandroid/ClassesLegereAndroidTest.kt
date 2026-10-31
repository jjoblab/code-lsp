package jo.codeide.lsp.java.projetandroid

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader
import org.junit.jupiter.api.Test
import jo.codeide.lsp.java.MoteurJava

/**
 * Classes light R + BuildConfig (vague V-B1 — ADR 0021, décision D2) : le
 * moteur résout `R.*` et `BuildConfig.*` SANS build, par octets de classe
 * synthétiques servis entre sources et jars.
 *
 * Vérifications (critères de sortie de la vague) :
 *  - les OCTETS sont des fichiers de classe valides pour le lecteur ecj
 *    (champs, drapeaux, ConstantValue) ;
 *  - la RÉSOLUTION moteur rend zéro « cannot be resolved » sur `R.*` avant
 *    build — et reste HONNÊTE (une ressource inconnue reste une erreur) ;
 *  - l'ANTI-DOUBLON : la source générée réelle PRIME sur le synthétique ;
 *  - le contrat `:model` v2 se convertit (miroir moteur) sans perte.
 */
class ClassesLegereAndroidTest {

    private companion object {
        val MINI: Path = Path.of("src/test/resources/projetandroid/mini/app/src/main/java")
        val RESSOURCES: Path = Path.of("src/test/resources/projetandroid/mini/app/src/main/res")
    }

    /** Le contrat mini dérivé + référentiel + fabrique. */
    private fun fabriqueMini(): ClassesLegereAndroid {
        val contrat = ModeleAndroidDerive.deriver(listOf(MINI))!!
        return ClassesLegereAndroid(contrat, ReferentielRessources(contrat.dossiersRes))
    }

    // ------------------------------------------------------------------ //
    // Octets : validité du format pour le lecteur ecj
    // ------------------------------------------------------------------ //

    @Test
    fun `les octets de R sont des fichiers de classe valides - champs drapeaux constantes`() {
        val fabrique = fabriqueMini()

        val octetsR = fabrique.octetsPour("com.exemple.mini.R")!!
        val lecteurR = ClassFileReader.read(octetsR, "com/exemple/mini/R.class")
        assertTrue((lecteurR.modifiers and ClassFileConstants.AccFinal) != 0, "R est final")
        assertTrue((lecteurR.modifiers and ClassFileConstants.AccPublic) != 0, "R est public")
        assertEquals(0, lecteurR.fields?.size ?: 0, "R porte les types dans ses imbriquées, pas de champs propres")

        val octetsLayout = fabrique.octetsPour("com.exemple.mini.R\$layout")!!
        val lecteurLayout = ClassFileReader.read(octetsLayout, "com/exemple/mini/R\$layout.class")
        val champs = lecteurLayout.fields!!.map { String(it.name) to it.modifiers }
        assertEquals(listOf("ecran_principal"), champs.map { it.first }, "champ par ressource layout")
        val (nom, drapeaux) = champs.single()
        assertEquals("ecran_principal", nom)
        assertTrue((drapeaux and ClassFileConstants.AccPublic) != 0)
        assertTrue((drapeaux and ClassFileConstants.AccStatic) != 0)
        assertTrue((drapeaux and ClassFileConstants.AccFinal) != 0, "final par défaut (nonFinalResIds = faux)")

        // ConstantValue lisible : la valeur est un int stable.
        val constante = lecteurLayout.fields!!.single().constant
        assertNotNull(constante, "ConstantValue émis pour le switch sur R.layout.x")

        // Les styleables portent leurs trois familles.
        val octetsStyleable = fabrique.octetsPour("com.exemple.mini.R\$styleable")!!
        val nomsStyleable = ClassFileReader.read(octetsStyleable, "x.class").fields!!.map { champ -> String(champ.name) }
        assertEquals(setOf("Exemple", "Exemple_libelle", "Exemple_poidsMax"), nomsStyleable.toSet())
    }

    @Test
    fun `buildconfig n existe que si active - et porte les champs agp`() {
        val fabrique = fabriqueMini()
        assertNull(fabrique.octetsPour("com.exemple.mini.BuildConfigAA"), "FQCN inconnu : null")

        val octets = fabrique.octetsPour("com.exemple.mini.BuildConfig")!!
        val champs = ClassFileReader.read(octets, "x.class").fields!!.associate { champ -> String(champ.name) to champ.constant }
        assertEquals(setOf("DEBUG", "APPLICATION_ID", "BUILD_TYPE", "FLAVOR", "VERSION_CODE", "VERSION_NAME"), champs.keys)
        assertTrue(champs.getValue("DEBUG").booleanValue(), "DEBUG = true")
        assertEquals("com.exemple.mini", champs.getValue("APPLICATION_ID").stringValue())
        assertEquals(1, champs.getValue("VERSION_CODE").intValue())
        assertEquals("debug", champs.getValue("BUILD_TYPE").stringValue())
    }

    // ------------------------------------------------------------------ //
    // Résolution moteur : zéro « cannot be resolved » avant build
    // ------------------------------------------------------------------ //

    @Test
    fun `le moteur resout R et BuildConfig sans build - et reste honnete sur l inconnu`() {
        val contrat = ModeleAndroidDerive.deriver(listOf(MINI))!!
        val moteur = MoteurJava(listOf(MINI), emptyList(), null, "17", null, null, contrat)
        assertTrue(moteur.aClassesLegereAndroid())

        // Références valides : AUCUNE erreur (la plateforme jrt du JDK de
        // test suffit — R et BuildConfig ne consultent pas android.jar).
        val source = """
            package com.exemple.mini;

            public class Ecran {
                static final int LAYOUT = R.layout.ecran_principal;
                static final int TITRE = R.id.text_titre;
                static final int NOM = R.string.app_nom;
                static final int STYLABELE = R.styleable.Exemple_libelle;
                static final int MENU = R.menu.menu_principal;
                static final boolean DEBUG = BuildConfig.DEBUG;
                private int champ = R.id.vue_anonyme;

                int switchSurLayout(int code) {
                    switch (code) {
                        case R.layout.ecran_principal: return 1;
                        default: return 0;
                    }
                }
            }
        """.trimIndent()
        val diagnostics = moteur.diagnose("/com/exemple/mini/Ecran.java", source)
        assertTrue(
            diagnostics.none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR },
            "aucune erreur sur R.*/BuildConfig.* avant build — erreurs réelles : ${diagnostics.filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }}",
        )

        // Honnêteté : une ressource INCONNUE reste une erreur (règle
        // conservatrice — la fabrique n'invente rien).
        val fautif = """
            package com.exemple.mini;

            public class EcranFautif {
                static final int X = R.layout.inexistant;
            }
        """.trimIndent()
        val diagnosticsFautif = moteur.diagnose("/com/exemple/mini/EcranFautif.java", fautif)
        assertTrue(
            diagnosticsFautif.any { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR },
            "R.layout.inexistant reste une erreur (honnêteté)",
        )
    }

    @Test
    fun `anti-doublon - la source generee reelle prime sur le synthetique`() {
        val dossier = Files.createTempDirectory("light-anti-doublon")
        try {
            // Un VRAI R.java dans les sources du projet (build passé).
            val racine = Files.createDirectories(dossier.resolve("com/exemple/mini"))
            Files.writeString(
                racine.resolve("R.java"),
                """
                package com.exemple.mini;
                public final class R {
                    public static final class layout {
                        public static final int ecran_principal = 0x7f0e0000;
                        private layout() {}
                    }
                }
                """.trimIndent(),
            )
            val contrat = ContratAndroidMoteur(
                namespace = "com.exemple.mini",
                dossiersRes = listOf(RESSOURCES),
                buildConfig = true,
            )
            val moteur = MoteurJava(listOf(dossier), emptyList(), null, "17", null, null, contrat)
            val localisation = moteur.typeIndex().find("com.exemple.mini.R")
            assertTrue(localisation is jo.codeide.lsp.java.classpath.EnvironnementNoms.Localisation.Source, "le R du build passé gagne")

            // Sans la source : le synthétique répond.
            val moteurVide = MoteurJava(listOf(dossier.resolve("vide-inexistante")), emptyList(), null, "17", null, null, contrat)
            val localisationSynthetique = moteurVide.typeIndex().find("com.exemple.mini.R")
            assertTrue(localisationSynthetique is jo.codeide.lsp.java.classpath.EnvironnementNoms.Localisation.Synthetique)
        } finally {
            dossier.toFile().deleteRecursively()
        }
    }

    @Test
    fun `l annuaire propose R en completion et le paquet namespace est un paquet`() {
        val contrat = ContratAndroidMoteur(namespace = "com.exemple.mini", dossiersRes = listOf(RESSOURCES), buildConfig = true)
        val fabrique = ClassesLegereAndroid(contrat, ReferentielRessources(listOf(RESSOURCES)))
        val moteur = MoteurJava(listOf(MINI), emptyList(), null, "17", null, null, contrat)

        val env = jo.codeide.lsp.java.classpath.EnvironnementNoms(listOf(MINI), emptyList(), null, listOf(fabrique))
        assertTrue(env.isPackage(arrayOf("com".toCharArray(), "exemple".toCharArray()), "mini".toCharArray()), "le namespace est un paquet")
        val types = env.annuaire.typesDirects("com.exemple.mini")
        val noms = types.map { it.nomSimple }.toSet()
        assertTrue("R" in noms, "R proposé en complétion")
        assertTrue("BuildConfig" in noms, "BuildConfig proposé")
        val origineR = types.single { it.nomSimple == "R" }.origine
        assertEquals(jo.codeide.lsp.java.classpath.Origine.SYNTHETIQUE, origineR)
        assertTrue(env.annuaire.estProposablePourAutoImport(types.single { it.nomSimple == "R" }, "autre.paquet"))
        assertEquals(0, moteur.diagnose("/x/A.java", "package x; class A { int i = com.exemple.mini.R.layout.ecran_principal; }").count { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR })
    }

    @Test
    fun `la maj xml invalide les octets light et le memo - generation croissante`() {
        val dossier = Files.createTempDirectory("light-maj")
        try {
            val res = Files.createDirectories(dossier.resolve("res/layout"))
            val layout = res.resolve("ecran.xml")
            Files.writeString(layout, "<LinearLayout><TextView android:id=\"@+id/a\"/></LinearLayout>")
            val contrat = ContratAndroidMoteur(namespace = "com.exemple.mini", dossiersRes = listOf(res))
            val fabrique = ClassesLegereAndroid(contrat, ReferentielRessources(listOf(res)))
            val moteur = MoteurJava(listOf(MINI), emptyList(), null, "17", null, null, contrat)
            assertTrue(moteur.aClassesLegereAndroid())

            val avant = moteur.generationRessources()
            Files.writeString(layout, "<LinearLayout><TextView android:id=\"@+id/b\"/></LinearLayout>")
            moteur.rafraichirFichierXml(layout)
            assertTrue(moteur.generationRessources() > avant, "la génération a crû")

            // Le NOUVEAU champ est là, l'ancien a disparu — via la fabrique
            // VIVANTE DU MOTEUR (la même que la résolution).
            val fabriqueDuMoteur = moteur.classesLegereAndroid()!!
            val octets = fabriqueDuMoteur.octetsPour("com.exemple.mini.R\$id")!!
            val noms = ClassFileReader.read(octets, "x.class").fields!!.map { String(it.name) }
            assertEquals(listOf("b"), noms)
        } finally {
            dossier.toFile().deleteRecursively()
        }
    }
}

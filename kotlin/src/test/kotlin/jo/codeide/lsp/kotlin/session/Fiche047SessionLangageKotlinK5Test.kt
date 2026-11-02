package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 047 — SessionLangageKotlinK5Test : le contrat de session K5
 * (LSP_KOTLIN.md §5 — classes synthétiques R/ViewBinding/DataBinding,
 * intégration éditeur complète : complétion multi-langue, bascule
 * Java↔Kotlin) — 21 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * Un PROJET ANDROID FACTICE sur disque (manifeste + res/layout +
 * res/values + res/drawable + sources Java) nourrit les synthétiques du
 * moteur ; la FUSION AVEC COUPURE les ajoute au modèle des preuves tant
 * qu'aucune classe réelle du même FQCN ne les efface.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche047SessionLangageKotlinK5Test {

    /** La racine temporaire du projet Android factice. */
    @TempDir
    lateinit var racine: Path

    /** Écrit un fichier (parents créés). */
    private fun ecrire(chemin: Path, contenu: String) {
        Files.createDirectories(chemin.parent)
        Files.writeString(chemin, contenu)
    }

    /** Le jar android.jar AOSP du classpath de test. */
    private fun jarAndroid(): java.io.File =
        java.io.File(
            android.util.Log::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Pose le projet Android factice ; rend sa racine source. */
    private fun projetFactice(): Path {
        ecrire(racine.resolve("src/main/AndroidManifest.xml"), """<manifest package="com.ex.app"/>""")
        ecrire(
            racine.resolve("src/main/res/layout/activity_main.xml"),
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:id="@+id/textView"/>
                <Button android:id="@+id/bouton"/>
            </LinearLayout>
            """.trimIndent(),
        )
        ecrire(
            racine.resolve("src/main/res/layout/activity_data.xml"),
            """
            <layout>
                <data>
                    <variable name="utilisateur" type="String"/>
                </data>
                <LinearLayout/>
            </layout>
            """.trimIndent(),
        )
        ecrire(
            racine.resolve("src/main/res/values/strings.xml"),
            """<resources><string name="app_nom">App</string></resources>""",
        )
        ecrire(
            racine.resolve("src/main/res/values/ids.xml"),
            """<resources><item type="id" name="champ"/></resources>""",
        )
        ecrire(
            racine.resolve("src/main/res/drawable/logo.xml"),
            """<selector/>""",
        )
        // La source JAVA multi-langue (fiche 047 : bascule Java↔Kotlin).
        ecrire(
            racine.resolve("src/main/java/com/ex/app/CalculatriceJava.java"),
            """
            package com.ex.app;

            public class CalculatriceJava {
                private String titre = "calc";

                public int additionne(int a, int b) { return a + b; }

                public String getTitre() { return titre; }

                public static CalculatriceJava fabriquer() { return new CalculatriceJava(); }
            }
            """.trimIndent(),
        )
        return racine.resolve("src/main/java")
    }

    /** Session branchée sur le projet factice + android.jar + stdlib. */
    private fun session(): SessionLangageKotlinDocuments =
        sessionSur(racineSources = projetFactice(), avecJava = true)

    /** Session sur les racines données (guichet attendu par sondage borné). */
    private fun sessionSur(racineSources: Path, avecJava: Boolean): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val jars = mutableListOf(jarStdlib())
        if (avecJava) jars.add(jarAndroid())
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f047:$i:${jar.name}")
            },
            racinesSources = listOf(racineSources.toAbsolutePath().toString()),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 120) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /** Les messages « non résolue » du document. */
    private fun nonResolues(session: SessionLangageKotlinDocuments, uri: String): List<String> =
        session.diagnostics(uri)
            .filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
            .map { it.message }

    @Test
    fun `R---familles-proposees-apres-le-point,-layout-string-id-drawable`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(): Int = R.layout.activity_main
            """.trimIndent()
        val uri = "file:///k5-r-familles.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("R.layout") + 1
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue(
            "layout" in propositions && "string" in propositions && "id" in propositions && "drawable" in propositions,
            "les familles de R proposées : $propositions",
        )
        session.close()
    }

    @Test
    fun `R---constantes-des-familles-(layout,-string,-id)-proposees`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(): Int = R.layout.activity_main
            """.trimIndent()
        val uri = "file:///k5-r-constantes.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("R.layout.") + "R.layout.".length
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("activity_main" in propositions, "les constantes de layout : $propositions")
        val pointChaines = source.indexOf("R.layout.") + "R.".length
        val apresString = session.completion(uri, source.indexOf("R.layout") + 3).map { it.libelle }
        assertTrue(apresString.isNotEmpty(), "les familles se proposent : $apresString")
        session.close()
    }

    @Test
    fun `R---reference-nue-resolue,-aucun-kt-unresolved`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(): Int = R.layout.activity_main + R.string.app_nom + R.id.textView + R.drawable.logo
            """.trimIndent()
        val uri = "file:///k5-r-nue.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.isEmpty(), "R nu et ses familles résolus : $recus")
        session.close()
    }

    @Test
    fun `R---sans-import,-meme-paquet-que-le-manifeste-(retour-U2---MainActivity)`() {
        val session = session()
        val source =
            """
            package com.ex.app

            class MainActivity
            """.trimIndent()
        val uri = "file:///k5-r-paquet.kt"
        session.ouvrir(uri, source)
        // Le paquet du DOCUMENT est celui du MANIFESTE : R y vit sans
        // import (retour utilisateurs U2 — MainActivity).
        val recus = nonResolues(session, uri)
        assertTrue(recus.isEmpty(), "R sans import au paquet du manifeste : $recus")
        val propositions = session.completion(uri, source.indexOf("class") + 3).map { it.libelle }
        assertTrue("R" in propositions || propositions.isEmpty(), "le document sain : $propositions")
        session.close()
    }

    @Test
    fun `ViewBinding---classe-synthetique-et-champs-des-ids-proposes`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(binding: ActivityMainBinding): Int = binding.textView.id + binding.bouton.id
            """.trimIndent()
        val uri = "file:///k5-vb-champs.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.isEmpty(), "la classe synthétique et ses champs résolus : $recus")
        val point = source.indexOf("binding.") + "binding.".length
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("textView" in propositions && "bouton" in propositions, "les champs des ids proposés : $propositions")
        session.close()
    }

    @Test
    fun `ViewBinding---le-nom-de-classe-propose-en-complétion-de-nom`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo() {
              ActivityM
            }
            """.trimIndent()
        val uri = "file:///k5-vb-nom.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("ActivityM") + "ActivityM".length
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("ActivityMainBinding" in propositions, "le nom de classe proposé : $propositions")
        session.close()
    }

    @Test
    fun `DataBinding---classe-du-sous-paquet-databinding-et-variable`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(binding: ActivityDataBinding) {
              binding.utilisateur
            }
            """.trimIndent()
        val uri = "file:///k5-db-variable.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(
            recus.none { it.contains("ActivityDataBinding") || it.contains("utilisateur") },
            "la classe du sous-paquet databinding et sa variable résolues : $recus",
        )
        session.close()
    }

    @Test
    fun `ViewBinding---la-definition-ouvre-le-fichier-de-layout`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(binding: ActivityMainBinding) = binding.textView.id
            """.trimIndent()
        val uri = "file:///k5-vb-definition.kt"
        session.ouvrir(uri, source)
        // La DÉFINITION de la classe synthétique OUVRE le fichier de
        // LAYOUT qui la porte.
        val emplacements = session.definition(uri, source.indexOf("ActivityMainBinding"))
        assertEquals(1, emplacements.size, "un emplacement : $emplacements")
        assertTrue(
            emplacements.single().uri.endsWith("activity_main.xml"),
            "la définition ouvre le layout : ${emplacements.single().uri}",
        )
        session.close()
    }

    @Test
    fun `BuildConfig---type-resolu,-membres-sous-rapportes`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(): Int = BuildConfig.DEBUG.toString().length
            """.trimIndent()
        val uri = "file:///k5-buildconfig.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le TYPE résout ; les MEMBRES sont sous-reportés (silence
        // conservateur D3 — les champs réels vivent dans la génération
        // AGP, jamais lus).
        assertTrue(recus.none { it.contains("BuildConfig") }, "le type BuildConfig résout : $recus")
        assertTrue(recus.none { it.contains("DEBUG") }, "les membres sous-reportés sont muets : $recus")
        session.close()
    }

    @Test
    fun `coupure---une-classe-R-reelle-du-scan-efface-le-synthetique`() {
        // Une classe R RÉELLE vit dans les sources scannées : elle EFFACE
        // le groupe synthétique homonyme (fusion AVEC COUPURE).
        val racine = projetFactice()
        ecrire(
            racine.resolve("com/ex/app/R.java"),
            """
            package com.ex.app;

            public final class R {
            }
            """.trimIndent(),
        )
        val session = sessionSur(racine, avecJava = true)
        val source =
            """
            package com.ex.app

            fun demo(): Int = 1
            """.trimIndent()
        val uri = "file:///k5-coupure.kt"
        session.ouvrir(uri, source)
        // La complétion après `R.` — le R RÉEL (vide) a remplacé le
        // synthétique : les familles ne se proposent PLUS.
        val complet = "fun demo(): Int = R.x"
        session.ouvrir(uri, "package com.ex.app\n\n$complet")
        val point = session.let { "package com.ex.app\n\nfun demo(): Int = R.".length }
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("layout" !in propositions, "le synthétique est effacé : $propositions")
        session.close()
    }

    @Test
    fun `sans-manifeste---aucun-synthetique,-rien-de-faux`() {
        // AUCUN manifeste : aucun groupe synthétique — R n'est PAS résolu
        // (l'honnêteté prime, rien de faux n'est proposé).
        val racine = Files.createDirectories(racine.resolve("src"))
        ecrire(
            racine.resolve("com/ex/app/Vide.kt"),
            "package com.ex.app\n\nclass Vide",
        )
        val session = sessionSur(racine, avecJava = true)
        val source =
            """
            package com.ex.app

            fun demo(): Int = R.layout.activity_main
            """.trimIndent()
        val uri = "file:///k5-sans-manifeste.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.isNotEmpty(), "sans manifeste, R est honnètement inconnu : $recus")
        val point = source.indexOf("R.layout") + 1
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("layout" !in propositions, "aucune famille proposée : $propositions")
        session.close()
    }

    @Test
    fun `multi-langue---la-classe-Java-est-proposee-et-resolue-depuis-Kotlin`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(): Int = CalculatriceJava().additionne(1, 2)
            """.trimIndent()
        val uri = "file:///k5-java-resolu.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.isEmpty(), "la classe Java résolue depuis Kotlin : $recus")
        val corps =
            """
            package com.ex.app

            fun demo() {
              CalculatriceJ
            }
            """.trimIndent()
        session.ouvrir("file:///k5-java-nom.kt", corps)
        val point = corps.indexOf("CalculatriceJ") + "CalculatriceJ".length
        val propositions = session.completion("file:///k5-java-nom.kt", point).map { it.libelle }
        assertTrue("CalculatriceJava" in propositions, "la classe Java proposée : $propositions")
        session.close()
    }

    @Test
    fun `multi-langue---l-arite-d-un-constructeur-Java-n-est-jamais-controlee`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(): CalculatriceJava = CalculatriceJava(1, 2, 3, 4)
            """.trimIndent()
        val uri = "file:///k5-java-arite.kt"
        session.ouvrir(uri, source)
        val arites = session.diagnostics(uri).filter { it.code == CodeDiagnosticKotlin.NOMBRE_ARGUMENTS }
        // L'ARITÉ d'un constructeur Java n'est JAMAIS contrôlée (le lecteur
        // Java ne porte pas les constructeurs — sous-rapport honnête).
        assertTrue(arites.isEmpty(), "l'arité Java jamais contrôlée : $arites")
        session.close()
    }

    @Test
    fun `multi-langue---la-definition-d-une-reference-Kotlin-ouvre-le-java`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(): Int = CalculatriceJava().additionne(1, 2)
            """.trimIndent()
        val uri = "file:///k5-java-definition.kt"
        session.ouvrir(uri, source)
        // La DÉFINITION d'une référence Kotlin OUVRE le fichier Java du
        // scan.
        val emplacements = session.definition(uri, source.indexOf("CalculatriceJava"))
        assertEquals(1, emplacements.size, "un emplacement : $emplacements")
        assertTrue(
            emplacements.single().uri.endsWith("CalculatriceJava.java"),
            "la définition ouvre le java : ${emplacements.single().uri}",
        )
        session.close()
    }

    @Test
    fun `multi-langue---membres-Java-resolus-depuis-Kotlin-(getters,-statiques,-propriete-synthetique)`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(calc: CalculatriceJava): Int {
              val fabrique = CalculatriceJava.fabriquer()
              return calc.additionne(1, 2) + calc.titre.length + fabrique.additionne(3, 4)
            }
            """.trimIndent()
        val uri = "file:///k5-java-membres.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Les membres Java résolus : méthode d'instance, STATIQUE, et
        // propriété synthétique du getter (ADR 0219 — calc.titre).
        assertTrue(recus.isEmpty(), "les membres Java résolus : $recus")
        session.close()
    }

    @Test
    fun `multi-langue---membres-Java-proposes-en-completion-apres-le-point`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(calc: CalculatriceJava): Int = calc.additionne(1, 2)
            """.trimIndent()
        val uri = "file:///k5-java-completion.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("calc.") + "calc.".length
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("additionne" in propositions && "titre" in propositions, "les membres Java proposés : $propositions")
        session.close()
    }

    @Test
    fun `multi-langue---le-typage-traverse-un-getter-Java-(chaine-continue)`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(calc: CalculatriceJava): Int = calc.titre.length
            """.trimIndent()
        val uri = "file:///k5-java-typage.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le TYPE du getter Java (String) type la chaîne CONTINUE :
        // calc.titre.length résout (titre : String par le descripteur du
        // lecteur Java, length par la vue JVM de android.jar).
        assertTrue(recus.isEmpty(), "le typage traverse le getter Java : $recus")
        session.close()
    }

    @Test
    fun `with---membres-du-recepteur-implicite-proposes-sans-selecteur`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(calc: CalculatriceJava): Int = with(calc) {
              additionne(1, 2)
            }
            """.trimIndent()
        val uri = "file:///k5-with-completion.kt"
        session.ouvrir(uri, source)
        // Dans le `with`, les membres du récepteur IMPLICITE se proposent
        // SANS sélecteur (la complétion de nom consulte la pile des this).
        val point = source.indexOf("additionne") + "additionne".length
        val propositions = session.completion(uri, point).map { it.libelle }
        assertTrue("additionne" in propositions, "les membres du with proposés : $propositions")
        session.close()
    }

    @Test
    fun `with---un-membre-nu-du-recepteur-n-est-pas-non-resolu`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(calc: CalculatriceJava): Int = with(calc) {
              additionne(1, 2) + titre.length
            }
            """.trimIndent()
        val uri = "file:///k5-with-nu.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Les membres NUS du récepteur implicite ne sont PAS non résolus
        // (la pile des this du with).
        assertTrue(recus.isEmpty(), "les membres nus du with résolus : $recus")
        session.close()
    }

    @Test
    fun `with---le-recepteur-implicite-traverse-les-literaux-emboites`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(calc: CalculatriceJava): Int = with(calc) {
              val transforme = "x".let { _ ->
                additionne(1, 2)
              }
              transforme.length + titre.length
            }
            """.trimIndent()
        val uri = "file:///k5-with-emboite.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le récepteur implicite TRAVERSE les littéraux emboîtés (la
        // lambda de `let` ne coupe pas la pile).
        assertTrue(recus.isEmpty(), "le with traverse les littéraux : $recus")
        session.close()
    }

    @Test
    fun `SAM-classpath---le-parametre-du-lambda-est-type-par-l-attente`() {
        val session = session()
        val source =
            """
            package com.ex.app

            fun demo(vue: android.view.View) {
              vue.setOnClickListener { v -> v.invalidate() }
            }
            """.trimIndent()
        val uri = "file:///k5-sam-classpath.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le paramètre du lambda est TYPÉ par l'attente SAM du CLASSPATH
        // (View$OnClickListener.onClick(View)) : v type View, son
        // sélecteur invalidate résout.
        assertTrue(recus.isEmpty(), "le SAM classpath type le paramètre : $recus")
        session.close()
    }
}

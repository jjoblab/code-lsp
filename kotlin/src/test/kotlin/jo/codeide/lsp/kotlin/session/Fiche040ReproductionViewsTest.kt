package jo.codeide.lsp.kotlin.session

import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 040 — ReproductionViewsTest : le monde NON-Compose (vues XML /
 * AppCompat) au niveau MOTEUR — ADR 0219 (vague 2c) — 11 comportements du
 * cahier docs/cahier-specifications-kotlin/.
 *
 * Deux découvertes du template « Empty Views Activity » (rapport d'audit
 * §14) :
 *
 *  - **chaîne COUPÉE** : la hiérarchie Android réelle (AppCompatActivity →
 *    … → ContextWrapper → Context) exige PROFONDEUR 7+ — `getString` vit
 *    au maillon Context et restait un faux `kt.unresolved` alors que le
 *    classpath portait tout ;
 *  - **propriétés SYNTHÉTIQUES Java** : l'interop cœur du langage (getter
 *    `getFoo()` lisible `foo`) — `layoutInflater` nu dans une Activity,
 *    `title = …` via setTitle, `packageManager`/`packageName` var.
 *
 * La chaîne BINAIRE complète d'Android Studio (8 maillons jusqu'à Object)
 * vit sur un JAR DE FIXATURE — des class-files minimaux mais RÉELS avec
 * leurs descripteurs, posés comme ENTRÉES du classpath (aucune vue
 * factice) : `getLayoutInflater`/`setTitle` sur Activity (profondeur 4 de
 * la chaîne — prouve la synthèse SANS dépendre de la borne),
 * `getString`/`getPackageName`/`getDrawable(I)` sur Context (le maillon
 * profond — prouve la borne corrigée).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche040ReproductionViewsTest {

    /** La racine temporaire du jar de fixture. */
    @TempDir
    lateinit var racine: Path

    /**
     * Le jar de fixture du monde Views : la chaîne RÉELLE d'Android Studio
     * jusqu'à `java.lang.Object` (8 maillons) — AppCompatActivity →
     * FragmentActivity → activity.ComponentActivity →
     * core.ComponentActivity → Activity → ContextThemeWrapper →
     * ContextWrapper → Context.
     */
    private fun jarViews(): java.io.File =
        OutilsJarFixture.Batisseur()
            .classe(
                "androidx.appcompat.app.AppCompatActivity",
                "androidx.fragment.app.FragmentActivity",
                OutilsJarFixture.methode("setSupportActionBar", "(Landroidx/appcompat/widget/Toolbar;)V"),
            )
            .classe("androidx.fragment.app.FragmentActivity", "androidx.activity.ComponentActivity")
            .classe("androidx.activity.ComponentActivity", "androidx.core.app.ComponentActivity")
            .classe("androidx.core.app.ComponentActivity", "android.app.Activity")
            .classe(
                "android.app.Activity", "android.view.ContextThemeWrapper",
                OutilsJarFixture.champ("mFenetre", "Landroid/view/View;"),
                OutilsJarFixture.methode("getLayoutInflater", "()Landroid/view/LayoutInflater;"),
                OutilsJarFixture.methode("setTitle", "(Ljava/lang/CharSequence;)V"),
                OutilsJarFixture.methode("findViewById", "(I)Landroid/view/View;"),
            )
            .classe("android.view.ContextThemeWrapper", "android.content.ContextWrapper")
            .classe("android.content.ContextWrapper", "android.content.Context")
            .classe(
                "android.content.Context", "java.lang.Object",
                OutilsJarFixture.methode("getString", "(I)Ljava/lang/String;"),
                OutilsJarFixture.methode("getPackageName", "()Ljava/lang/String;"),
                OutilsJarFixture.methode("getDrawable", "(I)Landroid/graphics/drawable/Drawable;"),
            )
            .classe(
                "android.view.LayoutInflater", "java.lang.Object",
                OutilsJarFixture.methode("inflate", "(ILandroid/view/View;Landroid/view/ViewGroup;)Landroid/view/View;"),
            )
            .classe("android.view.View", "java.lang.Object")
            .classe("androidx.appcompat.widget.Toolbar", "android.view.View")
            .classe("android.graphics.drawable.Drawable", "java.lang.Object")
            .ecrire(racine, "fixture-views.jar")

    /** Session branchée sur le jar de fixture (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val jar = jarViews()
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f040:views")),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 90) {
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

    // ── Découverte 1 : chaîne coupée avant Context (borne ADR 0219) ──

    @Test
    fun `getString-herite-du-maillon-7---Context---non-signale`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo(): String = getString(1)
            }
            """.trimIndent()
        val uri = "file:///views-getString.kt"
        session.ouvrir(uri, source)
        // AVANT ADR 0219 : « Référence non résolue : getString » — la
        // chaîne s'arrêtait avant Context, le porteur réel vit au maillon
        // profond (la clôture 2c ferme les 8 maillons).
        val nonResolus = nonResolues(session, uri)
        assertEquals(0,
            nonResolus.size,
            "le membre du maillon Context doit résoudre : $nonResolus",
        )
        session.close()
    }

    // ── Découverte 2 : propriétés synthétiques Java (ADR 0219) ────────

    @Test
    fun `layoutInflater-propriete-synthetique---non-signale`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo(): android.view.LayoutInflater = layoutInflater
            }
            """.trimIndent()
        val uri = "file:///views-layoutinflater.kt"
        session.ouvrir(uri, source)
        // AVANT ADR 0219 : « Référence non résolue : layoutInflater » —
        // le getter getLayoutInflater() (Activity) existe dans les entrées
        // mais aucune synthèse n'était faite (la dérivation 2c lit le
        // descripteur : aucun paramètre → propriété).
        val nonResolus = nonResolues(session, uri)
        assertEquals(0,
            nonResolus.size,
            "la propriété synthétique doit résoudre : $nonResolus",
        )
        session.close()
    }

    @Test
    fun `title-affectation-nue-via-setter-synthetique---non-signale`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo() {
                title = "Démonstration"
              }
            }
            """.trimIndent()
        val uri = "file:///views-title.kt"
        session.ouvrir(uri, source)
        // setTitle(CharSequence) sur Activity — l'AFFECTATION nue prouve
        // sa résolution par l'existence du setter (la dérivation 2c :
        // un paramètre, retour void → propriété var).
        val nonResolus = nonResolues(session, uri)
        assertEquals(0,
            nonResolus.size,
            "le setter synthétique doit résoudre : $nonResolus",
        )
        session.close()
    }

    @Test
    fun `packageManager-var---getter-ET-setter---non-signale`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo(): String = packageName
            }
            """.trimIndent()
        val uri = "file:///views-packagename.kt"
        session.ouvrir(uri, source)
        val nonResolus = nonResolues(session, uri)
        assertEquals(0,
            nonResolus.size,
            "la propriété de Context (maillon profond) doit résoudre : $nonResolus",
        )
        session.close()
    }

    // ── Honnêteté D3 : les vrais négatifs restent signalés ────────────

    @Test
    fun `typo-d-une-propriete-synthetique-reste-signalee`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo() {
                val faux = layoutInfleurer
              }
            }
            """.trimIndent()
        val uri = "file:///views-typo.kt"
        session.ouvrir(uri, source)
        val nonResolus = nonResolues(session, uri)
        assertEquals(1,
            nonResolus.size,
            "la typo doit rester signalée : $nonResolus",
        )
        assertTrue(nonResolus.single().contains("layoutInfleurer"))
        session.close()
    }

    @Test
    fun `getter-A-PARAMETRE-ne-synthetise-pas-de-propriete`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo() {
                val d = drawable
              }
            }
            """.trimIndent()
        val uri = "file:///views-getdrawable.kt"
        session.ouvrir(uri, source)
        // getDrawable(I) porte UN paramètre — Kotlin n'en fait JAMAIS une
        // propriété : l'écriture `drawable` nue reste signalée (la
        // dérivation 2c lit le descripteur — la borne est honnête).
        val nonResolus = nonResolues(session, uri)
        assertEquals(1,
            nonResolus.size,
            "le getter paramétré ne doit PAS synthétiser : $nonResolus",
        )
        assertTrue(nonResolus.single().contains("drawable"))
        session.close()
    }

    // ── Parité Android Studio : survol, typage, complétion ────────────

    @Test
    fun `survol-implicite-de-layoutInflater---val-avec-proprietaire`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo(): android.view.LayoutInflater = layoutInflater
            }
            """.trimIndent()
        val uri = "file:///views-survol-implicite.kt"
        session.ouvrir(uri, source)
        val survol = session.survol(uri, source.indexOf("layoutInflater"))
        assertNotNull(survol,
            "le survol implicite doit répondre")
        assertTrue(survol!!.signature.startsWith("val layoutInflater: LayoutInflater"),
            "rendu miroir Android Studio — obtenu : ${survol?.signature}",
        )
        assertTrue(survol.signature.contains("android.app.Activity"),
            "le propriétaire (Activity) accompagne — obtenu : ${survol.signature}",
        )
        session.close()
    }

    @Test
    fun `survol-implicite-de-getString---fun-avec-proprietaire-Context`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo(): String = getString(1)
            }
            """.trimIndent()
        val uri = "file:///views-survol-getstring.kt"
        session.ouvrir(uri, source)
        val survol = session.survol(uri, source.indexOf("getString"))
        assertNotNull(survol,
            "le survol de la méthode implicite doit répondre")
        assertTrue(survol!!.signature.startsWith("fun getString(Int): String") &&
                survol.signature.contains("android.content.Context"),
            "rendu fun + propriétaire — obtenu : ${survol?.signature}",
        )
        session.close()
    }

    @Test
    fun `survol-qualifie-d-une-propriete-synthetique---this-layoutInflater`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo(): android.view.LayoutInflater = this.layoutInflater
            }
            """.trimIndent()
        val uri = "file:///views-survol-qualifie.kt"
        session.ouvrir(uri, source)
        // La forme QUALIFIÉE `this.layoutInflater` — même rendu que
        // l'implicite (miroir Android Studio).
        val survol = session.survol(uri, source.indexOf("layoutInflater"))
        assertNotNull(survol,
            "le survol qualifié doit répondre")
        assertTrue(survol!!.signature.contains("val layoutInflater: LayoutInflater"),
            "rendu val + propriétaire — obtenu : ${survol?.signature}",
        )
        session.close()
    }

    @Test
    fun `typage---layoutInflater-inflate-chaine-membre-apres-propriete-synthetique`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo(): android.view.View = layoutInflater.inflate(1, null, null)
            }
            """.trimIndent()
        val uri = "file:///views-typage.kt"
        session.ouvrir(uri, source)
        // La propriété synthétique DOIT typer (LayoutInflater) — sinon la
        // CHAÎNE `layoutInflater.inflate(…)` reste muette : le retour
        // prouvé (View) se voit par la complétion membre du sélecteur.
        val completions = session.completion(uri, source.indexOf("inflate") + "inflate".length)
        assertTrue(completions.any { it.libelle.startsWith("inflate") },
            "inflate doit s'offrir (le récepteur a typé) — obtenu : " +
                completions.map { it.libelle },
        )
        session.close()
    }

    @Test
    fun `completion-membre-apres-this-point---proprietes-synthetiques-offertes`() {
        val session = session()
        val source =
            """
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
              fun demo() {
                this.
              }
            }
            """.trimIndent()
        val uri = "file:///views-completion-this.kt"
        session.ouvrir(uri, source)
        val apresPoint = source.indexOf("this.") + "this.".length
        val completions = session.completion(uri, apresPoint)
        val libelles = completions.map { it.libelle }
        // Les propriétés synthétiques dominent les listes du monde
        // non-Compose (miroir Android Studio).
        assertTrue("layoutInflater" in libelles,
            "layoutInflater doit s'offrir — obtenu : $libelles",
        )
        assertTrue("packageName" in libelles,
            "packageName doit s'offrir (maillon profond) — obtenu : $libelles",
        )
        session.close()
    }
}

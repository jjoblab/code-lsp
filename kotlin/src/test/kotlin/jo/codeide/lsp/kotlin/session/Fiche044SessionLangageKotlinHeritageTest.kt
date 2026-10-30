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
 * Fiche 044 — SessionLangageKotlinHeritageTest : la voie 2-ter « membres
 * HÉRITÉS » d'ADR 0216 (vague 2c — la CLÔTURE des supertypes, mixte
 * projet/classpath) — 13 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * Le câblage suit le chemin de production (D6 — miroir du patron fiche
 * 046) : moteur nourricier + session branchée + guichet attendu par sondage
 * borné. Les supertypes BINARIES vivent sur un JAR DE FIXTURE — des
 * class-files minimaux mais RÉELS (champs et méthodes avec descripteurs)
 * posés comme ENTRÉES du classpath : AUCUNE vue factice, la vue JVM du
 * moteur lit ces entrées comme celles d'une bibliothèque véritable
 * (miroir honnête du bug rapporté : supertype binaire, pas source projet).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche044SessionLangageKotlinHeritageTest {

    /** La racine temporaire des jars de fixture et des racines sources. */
    @TempDir
    lateinit var racine: Path

    /**
     * Le jar de fixture de l'héritage : `android.app.Activity`
     * (setContentView/findViewById/moveTaskToBack + champ protégé mFenetre),
     * `androidx.fragment.app.FragmentFactory` (instantiate), les feuilles
     * Fragment/Bundle/View.
     */
    private fun jarHeritage(): java.io.File =
        OutilsJarFixture.Batisseur()
            .classe(
                "android.app.Activity", "java.lang.Object",
                OutilsJarFixture.champ("mFenetre", "Landroid/view/View;"),
                OutilsJarFixture.methode("setContentView", "(Landroid/view/View;)V"),
                OutilsJarFixture.methode("findViewById", "(I)Landroid/view/View;"),
                OutilsJarFixture.methode("moveTaskToBack", "(Z)Z"),
            )
            .classe(
                "androidx.fragment.app.FragmentFactory", "java.lang.Object",
                OutilsJarFixture.methode(
                    "instantiate",
                    "(Ljava/lang/ClassLoader;Ljava/lang/String;)Landroidx/fragment/app/Fragment;",
                ),
            )
            .classe("androidx.fragment.app.Fragment", "java.lang.Object")
            .classe("android.os.Bundle", "java.lang.Object")
            .classe("android.view.View", "java.lang.Object")
            .ecrire(racine, "fixture-heritage.jar")

    /** Session branchée sur les jars et racines donnés (guichet attendu par sondage borné). */
    private fun session(
        jars: List<java.io.File>,
        racines: List<Path> = emptyList(),
    ): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f044:$i:${jar.name}")
            },
            racinesSources = racines.map { it.toAbsolutePath().toString() },
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

    /** Les messages « non résolue » du document (code + texte). */
    private fun nonResolues(session: SessionLangageKotlinDocuments, uri: String): List<String> =
        session.diagnostics(uri)
            .filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
            .map { it.message }

    /** Sème un paquet `com.ex.app` dans une racine sources et rend la racine. */
    private fun semerPaquet(vararg classes: Pair<String, String>): Path {
        val racineSources = Files.createDirectories(racine.resolve("src").resolve("com/ex/app"))
        for ((nomFichier, contenu) in classes) {
            Files.writeString(racineSources.resolve(nomFichier), contenu)
        }
        return racine.resolve("src")
    }

    // ── Héritage DIRECT classpath ──────────────────────────────────────

    @Test
    fun `methode-heritee-directe---setContentView-non-signalee`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            import android.app.Activity

            class MonActivite : Activity() {
              fun demo() {
                setContentView(null)
              }
            }
            """.trimIndent()
        val uri = "file:///mon-activite.kt"
        session.ouvrir(uri, source)
        val nonResolus = nonResolues(session, uri)
        // AVANT ADR 0216 : « Référence non résolue : setContentView » — la
        // voie 2 (membres DÉCLARÉS) ne voit pas l'héritage ; la clôture 2c
        // traverse la chaîne binaire des entrées.
        assertEquals(0,
            nonResolus.size,
            "le membre hérité doit résoudre : $nonResolus",
        )
        session.close()
    }

    @Test
    fun `methode-heritee-transitives---findViewById-et-moveTaskToBack-non-signales`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            import android.app.Activity

            class MonActivite : Activity() {
              fun demo(): Boolean {
                findViewById(1)
                return moveTaskToBack(true)
              }
            }
            """.trimIndent()
        val uri = "file:///transitive.kt"
        session.ouvrir(uri, source)
        assertEquals(0, nonResolues(session, uri).size)
        session.close()
    }

    @Test
    fun `champ-herite---mFenetre-non-signale`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            import android.app.Activity

            class MonActivite : Activity() {
              fun lit(): Int = mFenetre
            }
            """.trimIndent()
        val uri = "file:///champ.kt"
        session.ouvrir(uri, source)
        assertEquals(0, nonResolues(session, uri).size)
        session.close()
    }

    @Test
    fun `instantiate-herite---FragmentFactory-non-signale`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            import androidx.fragment.app.FragmentFactory

            class MaFabrique : FragmentFactory() {
              fun creer() {
                instantiate(null, "com.ex.Zzz")
              }
            }
            """.trimIndent()
        val uri = "file:///fabrique.kt"
        session.ouvrir(uri, source)
        assertEquals(0, nonResolues(session, uri).size)
        session.close()
    }

    // ── Chaîne PROJET → classpath (le port du GO TO Super) ────────────

    private val baseActiviteSource =
        """
        package com.ex.app

        import android.app.Activity

        open class BaseActivite : Activity() {
          fun aCote() = 1
        }
        """.trimIndent() + "\n"

    @Test
    fun `frere-projet-entre-l-appel-et-le-classpath---setContentView-non-signalee`() {
        val racineSources = semerPaquet("BaseActivite.kt" to baseActiviteSource)
        val session = session(listOf(jarHeritage()), listOf(racineSources))
        val source =
            """
            package com.ex.app

            class Enfant : BaseActivite() {
              fun go() {
                setContentView(null)
              }
            }
            """.trimIndent()
        val uri = "file:///com/ex/app/Enfant.kt"
        session.ouvrir(uri, source)
        // La clôture traverse Enfant → BaseActivite (FRÈRE projet, lu du
        // scan) → Activity (classpath des entrées) → setContentView.
        assertEquals(0, nonResolues(session, uri).size)
        session.close()
    }

    @Test
    fun `membre-declare-du-frere-projet-herite---aCote-non-signale`() {
        val racineSources = semerPaquet("BaseActivite.kt" to baseActiviteSource)
        val session = session(listOf(jarHeritage()), listOf(racineSources))
        val source =
            """
            package com.ex.app

            class Enfant : BaseActivite() {
              fun go(): Int = aCote()
            }
            """.trimIndent()
        val uri = "file:///com/ex/app/Enfant.kt"
        session.ouvrir(uri, source)
        assertEquals(0, nonResolues(session, uri).size)
        session.close()
    }

    @Test
    fun `frere-ouvert-vivant---setContentView-non-signalee-(arbre-vif)`() {
        // Le disque porte un frère SANS le membre vivant ; le document
        // OUVERT en ajoute un — la clôture doit lire l'ARBRE VIF (jamais le
        // disque périmé) : `vivant()` ne résout QUE par le frère ouvert.
        val racineSources = semerPaquet(
            "BaseActivite.kt" to baseActiviteSource.replace("  fun aCote() = 1\n", ""),
        )
        val session = session(listOf(jarHeritage()), listOf(racineSources))
        val uriBase = "file:///com/ex/app/BaseActivite.kt"
        session.ouvrir(uriBase, baseActiviteSource)
        val source =
            """
            package com.ex.app

            class Enfant : BaseActivite() {
              fun go() {
                setContentView(null)
                vivant()
              }
            }
            """.trimIndent()
        val uri = "file:///com/ex/app/Enfant.kt"
        session.ouvrir(uri, source)
        val nonResolus = nonResolues(session, uri)
        assertTrue(nonResolus.isEmpty(),
            "setContentView ET vivant (membre de l'arbre VIF du frère ouvert) résolvent : $nonResolus",
        )
        session.close()
    }

    // ── Types qualifiés (E4/E4bis) ─────────────────────────────────────

    @Test
    fun `E4---type-qualifie-existant---segments-muets-(android-os-Bundle)`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            import android.app.Activity

            class MonActivite : Activity() {
              override fun onCreate(savedInstanceState: android.os.Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(null)
              }
            }
            """.trimIndent()
        val uri = "file:///type-qualifie.kt"
        session.ouvrir(uri, source)
        // AVANT E4 : TROIS faux « Référence non résolue » — android, os,
        // Bundle — alors que le type COMPLET vit dans les entrées du
        // classpath (le chemin `android.os.Bundle` résout).
        assertEquals(0, nonResolues(session, uri).size)
        session.close()
    }

    @Test
    fun `E4bis---type-qualifie-inexistant---un-seul-segment-faillible`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            fun demo(param: zz.qqq.Inconnu) {
            }
            """.trimIndent()
        val uri = "file:///type-inconnu.kt"
        session.ouvrir(uri, source)
        val nonResolus = nonResolues(session, uri)
        // kotlinc signale UN segment faillible, jamais les PAQUETS zz/qqq :
        // seul le DERNIER segment (nom de classe) est émis.
        assertEquals(1,
            nonResolus.size,
            "seul le DERNIER segment (nom de classe) : $nonResolus",
        )
        assertTrue(nonResolus.single().contains("Inconnu"))
        session.close()
    }

    // ── Régressions : le diagnostic reste UN ALTER ego honnête ────────

    @Test
    fun `regression---nom-reellement-inconnu-reste-signale`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            import android.app.Activity

            class MonActivite : Activity() {
              fun demo() {
                zzInconnuNu()
              }
            }
            """.trimIndent()
        val uri = "file:///regression.kt"
        session.ouvrir(uri, source)
        val nonResolus = nonResolues(session, uri)
        assertEquals(1,
            nonResolus.size,
            "un vrai inconnu DOIT rester signalé : $nonResolus",
        )
        assertTrue(nonResolus.single().contains("zzInconnuNu"))
        session.close()
    }

    @Test
    fun `regression---methode-absente-du-supertype-reste-signalee`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            import android.app.Activity

            class MonActivite : Activity() {
              fun demo() {
                methodeQuiNexistePas()
              }
            }
            """.trimIndent()
        val uri = "file:///regression-absente.kt"
        session.ouvrir(uri, source)
        assertEquals(1, nonResolues(session, uri).size)
        session.close()
    }

    @Test
    fun `regression---sans-supertype,-pas-de-voie-heritee-magique`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            class TouteSeule {
              fun demo() {
                setContentView(null)
              }
            }
            """.trimIndent()
        val uri = "file:///sans-supertype.kt"
        session.ouvrir(uri, source)
        // Pas de clause de supertype : la voie 2-ter ne peut RIEN prouver —
        // le diagnostic reste (Activity n'est PAS un supertype de TouteSeule).
        assertEquals(1, nonResolues(session, uri).size)
        session.close()
    }

    @Test
    fun `quick-fix-import---membre-herite-ne-propose-aucun-import`() {
        val session = session(listOf(jarHeritage()))
        val source =
            """
            import android.app.Activity

            class MonActivite : Activity() {
              fun demo() {
                setContentView(null)
              }
            }
            """.trimIndent()
        val uri = "file:///quickfix.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("setContentView")
        // MÊME prédicat que le diagnostic (ADR 0216) : résolu par
        // héritage ⇒ AUCUN candidat d'import — sinon l'ampoule
        // proposerait un import absurde pour un appel déjà résolu.
        assertEquals(0, session.importsCandidats(uri, decalage).size)
        session.close()
    }
}

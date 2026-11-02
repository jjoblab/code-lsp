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
 * Fiche 033 — ReproductionObjetsClasspathTest : REPRODUCTION des OBJETS DU
 * CLASSPATH (retour utilisateurs 25/10/2026, familles A du programme
 * U1-U4) — 9 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * Un objet Kotlin compilé est une classe au champ STATIQUE `INSTANCE` dont
 * les membres sont des membres d'INSTANCE : le jar de fixture calque la
 * structure RÉELLE de Compose (`exo.theme.MaterialTheme` — champ statique
 * INSTANCE + propriétés membres ; `exo.layout.Arrangement` — fonction
 * membre), lu par la vue JVM du moteur comme une bibliothèque véritable.
 * Les coroutines sont RÉELLES (kotlinx-coroutines-core 1.9.0 du classpath
 * de test) : `collect` est une extension suspend sur `Flow`, atteinte par
 * le MAILLON DISTANCIÉ `SharedFlow`/`StateFlow` de la chaîne.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche033ReproductionObjetsClasspathTest {

    /** La racine temporaire des jars. */
    @TempDir
    lateinit var racine: Path

    /**
     * Le jar de fixture des objets Compose-like : `MaterialTheme` (champ
     * statique INSTANCE + deux propriétés membres), `Arrangement` (fonction
     * membre `spacedBy`), leurs classes support vides — TOUT se lit par la
     * vue JVM comme un classpath véritable.
     */
    private fun jarObjets(): java.io.File =
        OutilsJarFixture.Batisseur()
            .classe(
                "exo.theme.MaterialTheme", null,
                OutilsJarFixture.champStatique("INSTANCE", "Lexo/theme/MaterialTheme;"),
                OutilsJarFixture.champ("typography", "Lexo/theme/Typography;"),
                OutilsJarFixture.champ("colorant", "Lexo/theme/ColorScheme;"),
            )
            .classe(
                "exo.layout.Arrangement", null,
                OutilsJarFixture.champStatique("INSTANCE", "Lexo/layout/Arrangement;"),
                OutilsJarFixture.methode("spacedBy", "(I)Lexo/layout/Dp;"),
            )
            .classe("exo.theme.Typography", null)
            .classe("exo.theme.ColorScheme", null)
            .classe("exo.layout.Dp", null)
            .ecrire(racine, "fixture-objets-compose.jar")

    /** Le jar kotlinx-coroutines-core du classpath de test (jar physique réel). */
    private fun jarCoroutines(): java.io.File =
        java.io.File(
            kotlinx.coroutines.flow.Flow::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Session branchée sur les jars donnés (guichet attendu par sondage borné). */
    private fun session(jars: List<java.io.File>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f033:$i:${jar.name}")
            },
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Une copie isolée d'un jar (le classpath de test reste intact). */
    private fun copieDe(jar: java.io.File, suffixe: String): java.io.File {
        val copie = Files.createTempFile(racine, "f033-$suffixe", ".jar").toFile()
        copie.writeBytes(jar.readBytes())
        return copie
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
    fun `objet-classpath---propriete-membre,-zero-faux-positif`() {
        val session = session(listOf(jarObjets()))
        val source =
            """
            import exo.theme.MaterialTheme

            fun demo() {
              val t = MaterialTheme.typography
            }
            """.trimIndent()
        val uri = "file:///objet-classpath-propriete.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("typography") },
            "MaterialTheme.typography (propriété membre de l'objet) muet : $recus",
        )
        session.close()
    }

    @Test
    fun `objet-classpath---second-membre,-zero-faux-positif`() {
        val session = session(listOf(jarObjets()))
        val source =
            """
            import exo.theme.MaterialTheme

            fun demo() {
              val c = MaterialTheme.colorant
            }
            """.trimIndent()
        val uri = "file:///objet-classpath-second-membre.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("colorant") },
            "MaterialTheme.colorant (second membre de l'objet) muet : $recus",
        )
        session.close()
    }

    @Test
    fun `objet-classpath---fonction-membre-appelee,-zero-faux-positif`() {
        val session = session(listOf(jarObjets()))
        val source =
            """
            import exo.layout.Arrangement

            fun demo() {
              val e = Arrangement.spacedBy(8)
            }
            """.trimIndent()
        val uri = "file:///objet-classpath-fonction.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("spacedBy") },
            "Arrangement.spacedBy(8) (fonction membre de l'objet) muet : $recus",
        )
        session.close()
    }

    @Test
    fun `objet-classpath---membre-inexistant,-toujours-signale`() {
        val session = session(listOf(jarObjets()))
        val source =
            """
            import exo.theme.MaterialTheme

            fun demo() {
              val t = MaterialTheme.membreInexistantDuBytecode
            }
            """.trimIndent()
        val uri = "file:///objet-classpath-inexistant.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le vrai négatif de contrôle : la classe objet est close (super
        // Object terminal connu), le champ n'y vit pas — l'absence se PROUVE.
        assertEquals(1, recus.size, "un membre inexistant d'un objet reste signalé : $recus")
        assertTrue(recus.single().contains("membreInexistantDuBytecode"))
        session.close()
    }

    @Test
    fun `objet-classpath---survol-du-membre`() {
        val session = session(listOf(jarObjets()))
        val source =
            """
            import exo.theme.MaterialTheme

            fun demo() {
              val t = MaterialTheme.typography
            }
            """.trimIndent()
        val uri = "file:///objet-classpath-survol.kt"
        session.ouvrir(uri, source)
        // Le SURVOL du membre : la signature dépouillée du descripteur du
        // champ + le PROPRIÉTAIRE déclarant (miroir Android Studio).
        val survol = session.survol(uri, source.indexOf("typography"))
        assertTrue(
            survol != null && survol.signature.contains("typography") &&
                survol.signature.contains("Typography") &&
                survol.signature.contains("MaterialTheme"),
            "le survol de MaterialTheme.typography rend la signature typée : ${survol?.signature}",
        )
        session.close()
    }

    @Test
    fun `coroutines-reel---collect-suspend-extension,-zero-faux-positif`() {
        val session = session(listOf(copieDe(jarCoroutines(), "coroutines")))
        val source =
            """
            import kotlinx.coroutines.flow.Flow

            fun demo(flux: Flow<Int>) {
              flux.collect { valeur -> valeur + 1 }
            }
            """.trimIndent()
        val uri = "file:///coroutines-collect.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // `collect` est une extension suspend declaree sur Flow : le
        // recepteur EXACT la porte dans l'index — zero faux positif.
        assertTrue(recus.none { it.contains("collect") },
            "flux.collect (extension suspend sur Flow) muet : $recus",
        )
        session.close()
    }

    @Test
    fun `coroutines-reel---collect-sur-membre-distancie-SharedFlow,-zero-faux-positif`() {
        val session = session(listOf(copieDe(jarCoroutines(), "coroutines")))
        val source =
            """
            import kotlinx.coroutines.flow.SharedFlow

            class ModeleEcran {
              val etat: SharedFlow<Int>? = null
            }

            fun demo(m: ModeleEcran) {
              m.etat?.collect { valeur -> valeur + 1 }
            }
            """.trimIndent()
        val uri = "file:///coroutines-collect-distancie.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // `collect` vit sur le MAILLON DISTANCIÉ Flow de la chaîne
        // SharedFlow → Flow : l'extension s'y atteint, zero faux positif.
        assertTrue(recus.none { it.contains("collect") },
            "m.etat?.collect (membre distancié SharedFlow) muet : $recus",
        )
        session.close()
    }

    @Test
    fun `coroutines-reel---collect-sur-membre-dont-l-import-vit-ailleurs,-zero-faux-positif`() {
        val session = session(listOf(copieDe(jarCoroutines(), "coroutines")))
        val source =
            """
            import kotlinx.coroutines.flow.StateFlow

            class Depot {
              val etat: StateFlow<Int>? = null
            }

            fun demo(depot: Depot) {
              depot.etat?.collect { valeur -> valeur + 1 }
            }
            """.trimIndent()
        val uri = "file:///coroutines-collect-import-ailleurs.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le type du membre (StateFlow) est importé d'ailleurs que la
        // déclaration de `collect` : la résolution par le RÉCEPTEUR (chaîne
        // StateFlow → SharedFlow → Flow) n'en dépend PAS — zero faux positif.
        assertTrue(recus.none { it.contains("collect") },
            "depot.etat?.collect (import du membre ailleurs) muet : $recus",
        )
        session.close()
    }

    @Test
    fun `objectimporte---le-membre-inexistant-d-un-object-du-projet-est-signale`() {
        val session = session(listOf(jarObjets()))
        val source =
            """
            object Reglages {
              const val cle: String = "x"
            }

            fun demo(): String = Reglages.cle + Reglages.membreInconnuDuProjet
            """.trimIndent()
        val uri = "file:///object-importe.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le vrai négatif de contrôle côté PROJET : l'objet Reglages est
        // close (membres énumérés), le membre inconnu reste SIGNALÉ.
        assertEquals(1, recus.size, "le membre inexistant d'un object du projet est signalé : $recus")
        assertTrue(recus.single().contains("membreInconnuDuProjet"))
        session.close()
    }
}

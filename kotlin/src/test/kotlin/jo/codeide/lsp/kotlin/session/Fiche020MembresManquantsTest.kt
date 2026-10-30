package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 020 — MembresManquantsTest : le quick-fix « IMPLÉMENTER LES
 * MEMBRES MANQUANTS » (chantier P1-C, ADR 0229) — 10 comportements du
 * cahier docs/cahier-specifications-kotlin/ : la clôture des supertypes
 * (PROJET via le modèle, CLASSPATH via la vue JVM), la substitution des
 * paramètres de type, l'appariement getters/setters en propriétés et les
 * exclusions du corps — chaque test éprouve AUSSI la borne.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche020MembresManquantsTest {

    /** La racine temporaire. */
    @TempDir
    lateinit var racine: Path

    /** Le jar android.jar AOSP du classpath de test (interface binaire). */
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

    /** Session branchée sur les jars donnés (guichet attendu par sondage borné). */
    private fun session(jars: List<java.io.File>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f020:$i:${jar.name}")
            },
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

    /** Le caret dans le nom de la classe déclarée. */
    private fun caret(source: String, nom: String): Int = source.indexOf(nom)

    @Test
    fun `membres-manquants---interface-du-projet,-corps-vide-remplace`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            interface Depot {
              fun lire(): Int
              val taille: Int
            }

            class Memoire : Depot {}
            """.trimIndent()
        val uri = "file:///mm-vide.kt"
        session.ouvrir(uri, source)
        val correctifs = session.membresManquants(uri, caret(source, "Memoire"))
        assertEquals(1, correctifs.size, "un correctif : $correctifs")
        val correctif = correctifs.single()
        // Le corps VIDE est REMPLACÉ par les membres manquants.
        assertTrue(correctif.texte.contains("override fun lire(): Int"), "lire généré : ${correctif.texte}")
        assertTrue(correctif.texte.contains("override val taille: Int"), "taille générée : ${correctif.texte}")
        assertTrue(correctif.plage.debut == source.indexOf("{}"), "le corps vide est la plage")
        session.close()
    }

    @Test
    fun `membres-manquants---tout-implemente,-correctif-absent-(D3)`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            interface Depot {
              fun lire(): Int
            }

            class Memoire : Depot {
              override fun lire(): Int = 1
            }
            """.trimIndent()
        val uri = "file:///mm-complete.kt"
        session.ouvrir(uri, source)
        // Tout est IMPLÉMENTÉ : le correctif est ABSENT (D3 — la borne
        // ne propose jamais un travail nul).
        assertTrue(session.membresManquants(uri, caret(source, "Memoire")).isEmpty(), "rien à implémenter")
        session.close()
    }

    @Test
    fun `membres-manquants---implementation-partielle,-insertion-apres-le-dernier-membre`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            interface Depot {
              fun lire(): Int
              fun ecrire(valeur: Int)
            }

            class Memoire : Depot {
              override fun lire(): Int = 1
            }
            """.trimIndent()
        val uri = "file:///mm-partielle.kt"
        session.ouvrir(uri, source)
        val correctifs = session.membresManquants(uri, caret(source, "Memoire"))
        assertEquals(1, correctifs.size, "un correctif : $correctifs")
        val correctif = correctifs.single()
        assertTrue(correctif.texte.contains("override fun ecrire(valeur: Int)"), "ecrire généré : ${correctif.texte}")
        // L'INSERTION se fait APRÈS le DERNIER MEMBRE (plage vide à la
        // fin du membre lire).
        val finDernier = source.indexOf("override fun lire(): Int = 1") + "override fun lire(): Int = 1".length
        assertEquals(finDernier, correctif.plage.debut, "insertion après le dernier membre")
        session.close()
    }

    @Test
    fun `membres-manquants---parametres-de-type-substitues-par-la-clause-ecrite`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            interface Depot<T> {
              fun lire(): T
              fun ranger(valeur: T)
            }

            class Textes : Depot<String>
            """.trimIndent()
        val uri = "file:///mm-generique.kt"
        session.ouvrir(uri, source)
        val correctifs = session.membresManquants(uri, caret(source, "Textes"))
        assertEquals(1, correctifs.size, "un correctif : $correctifs")
        // Les paramètres de TYPE sont SUBSTITUÉS par la clause écrite
        // (`T` devient `String`).
        assertTrue(correctifs.single().texte.contains("override fun lire(): String"), "T → String : ${correctifs.single().texte}")
        assertTrue(correctifs.single().texte.contains("override fun ranger(valeur: String)"))
        session.close()
    }

    @Test
    fun `membres-manquants---clause-nue-sur-type-generique,-membre-muet-(D3)`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            interface Depot<T> {
              fun lire(): T
            }

            class Archive : Depot
            """.trimIndent()
        val uri = "file:///mm-clause-nue.kt"
        session.ouvrir(uri, source)
        // La clause NUE sur un type GÉNÉRIQUE : la substitution est
        // impossible, le membre reste MUET (D3).
        assertTrue(session.membresManquants(uri, caret(source, "Archive")).isEmpty(), "clause nue : muet")
        session.close()
    }

    @Test
    fun `membres-manquants---corps-absent,-corps-cree-en-fin-d'en-tete`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            interface Service {
              fun servir()
            }

            class Garcon : Service
            """.trimIndent()
        val uri = "file:///mm-sanscorps.kt"
        session.ouvrir(uri, source)
        val correctifs = session.membresManquants(uri, caret(source, "Garcon"))
        assertEquals(1, correctifs.size, "un correctif : $correctifs")
        val correctif = correctifs.single()
        // Le corps ABSENT : le CORPS est CRÉÉ en fin d'en-tête (insertion
        // à la fin de la déclaration).
        assertEquals(source.length, correctif.plage.debut, "en fin d'en-tête")
        assertTrue(correctif.texte.startsWith(" {\n"), "le corps créé : ${correctif.texte}")
        assertTrue(correctif.texte.contains("override fun servir()"))
        session.close()
    }

    @Test
    fun `membres-manquants---objet-implementant-l'interface`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            interface Horloge {
              fun maintenant(): Int
            }

            object HorlogeFixe : Horloge {}
            """.trimIndent()
        val uri = "file:///mm-objet.kt"
        session.ouvrir(uri, source)
        val correctifs = session.membresManquants(uri, caret(source, "HorlogeFixe"))
        // Un OBJET implémentant l'interface reçoit le correctif aussi.
        assertEquals(1, correctifs.size, "un correctif : $correctifs")
        assertTrue(correctifs.single().texte.contains("override fun maintenant(): Int"))
        session.close()
    }

    @Test
    fun `membres-manquants---cible-interface-ou-classe-abstraite,-action-absente-(D3)`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            interface Depot {
              fun lire(): Int
            }

            abstract class Base : Depot

            class Autre
            """.trimIndent()
        val uri = "file:///mm-cible.kt"
        session.ouvrir(uri, source)
        // La cible INTERFACE (Depot) ou CLASSE ABSTRAITE (Base)
        // ELLE-MÊME : l'action est ABSENTE (le correctif vise
        // l'implémenteur, jamais le contrat).
        assertTrue(session.membresManquants(uri, caret(source, "Depot")).isEmpty(), "interface : absent")
        assertTrue(session.membresManquants(uri, caret(source, "Base")).isEmpty(), "abstraite : absent")
        // Une classe SANS supertype : rien non plus.
        assertTrue(session.membresManquants(uri, caret(source, "Autre")).isEmpty(), "sans supertype : absent")
        session.close()
    }

    @Test
    fun `membres-manquants---membre-concret-du-supertype,-jamais-collecte`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            open class Base {
              fun concret(): Int = 1
              abstract fun abstrait(): Int
            }

            class Feuille : Base()
            """.trimIndent()
        val uri = "file:///mm-concret.kt"
        session.ouvrir(uri, source)
        val correctifs = session.membresManquants(uri, caret(source, "Feuille"))
        assertEquals(1, correctifs.size, "un correctif : $correctifs")
        // Le membre CONCRET du supertype n'est JAMAIS collecté — seul
        // l'abstrait l'est.
        assertTrue(correctifs.single().texte.contains("override fun abstrait(): Int"), "l'abstrait : ${correctifs.single().texte}")
        assertTrue(!correctifs.single().texte.contains("concret"), "le concret exclu : ${correctifs.single().texte}")
        session.close()
    }

    @Test
    fun `membres-manquants---interface-du-classpath,-methode-et-proprietes-appariees`() {
        // L'interface du CLASSPATH par FIXTURE binaire : méthodes ABSTRAITES
        // (ACC_ABSTRACT) dont un couple getter/setter APPARIÉ en propriété
        // VAR et un getter seul en VAL.
        val jar = OutilsJarFixture.Batisseur()
            .classe(
                "exo.service.Capteur", null,
                OutilsJarFixture.methodeAbstraite("lireValeur", "()I"),
                OutilsJarFixture.methodeAbstraite("getUnite", "()Ljava/lang/String;"),
                OutilsJarFixture.methodeAbstraite("setUnite", "(Ljava/lang/String;)V"),
                OutilsJarFixture.methodeAbstraite("getNom", "()Ljava/lang/String;"),
            )
            .ecrire(racine, "fixture-interface.jar")
        val session = session(listOf(jar, jarStdlib()))
        val source =
            """
            import exo.service.Capteur

            class CapteurSec : Capteur {}
            """.trimIndent()
        val uri = "file:///mm-classpath.kt"
        session.ouvrir(uri, source)
        val correctifs = session.membresManquants(uri, caret(source, "CapteurSec"))
        assertEquals(1, correctifs.size, "un correctif : $correctifs")
        val texte = correctifs.single().texte
        assertTrue(texte.contains("override fun lireValeur(): Int"), "la méthode générée : $texte")
        assertTrue(texte.contains("override var unite: String"), "le couple apparié en var : $texte")
        assertTrue(texte.contains("override val nom: String"), "le getter seul en val : $texte")
        session.close()
    }
}

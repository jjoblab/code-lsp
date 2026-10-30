package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 046 — SessionLangageKotlinK4Test : les FAMILLES SÉMANTIQUES K4 du
 * cahier (typage borné, smart casts POSITIONNELS, lambdas par ATTENTE, les
 * codes `kt.typeMismatch`/`kt.argumentCount`/`kt.unsafeNullable`/
 * `kt.unreachable`/`kt.valReassign`, les gardes KP8/ADR 0203 et le
 * quick-fix `importsCandidats`) — 39 comportements.
 *
 * Le câblage suit le chemin de production (D6 — miroir du patron fiche
 * 042) : moteur nourricier + session branchée + guichet attendu par sondage
 * borné. Les récepteurs de bibliothèque vivent sur des JARS RÉELS du
 * classpath de test — kotlin-stdlib (les extensions de `kotlin.String`
 * prouvent le typage par l'attente) et android.jar AOSP 4.1.1.4 (les
 * membres de `android.widget.TextView` et la chaîne `View` prouvent la vue
 * JVM des membres du classpath, ADR 0223) — AUCUNE vue factice : la vue du
 * moteur est pilotée par les ENTRÉES (discipline miroir de l'index 1d).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/).
 */
@Suppress("LargeClass")
class Fiche046SessionLangageKotlinK4Test {

    /** La racine temporaire des copies de jars. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Le jar android.jar AOSP du classpath de test (Maven Central, API 16). */
    private fun jarAndroid(): java.io.File =
        java.io.File(
            android.util.Log::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une copie isolée du jar (le classpath de test reste intact). */
    private fun copieDe(jar: java.io.File, suffixe: String): java.io.File {
        val copie = Files.createTempFile(racine, "k4-$suffixe", ".jar").toFile()
        copie.writeBytes(jar.readBytes())
        return copie
    }

    /** Session branchée sur les jars donnés (guichet attendu par sondage borné). */
    private fun session(jars: List<java.io.File>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "k4:$i:${jar.name}")
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
    private fun attendreCouvert(
        session: SessionLangageKotlinDocuments,
        secondes: Long = 90,
    ) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /** Les libellés proposés au point demandé. */
    private fun libelles(
        session: SessionLangageKotlinDocuments,
        uri: String,
        decalage: Int,
    ): List<String> = session.completion(uri, decalage).map { it.libelle }

    /** Les diagnostics du code donné. */
    private fun diagnostics(
        session: SessionLangageKotlinDocuments,
        uri: String,
        source: String,
    ): List<jo.codeide.lsp.kotlin.api.DiagnosticKotlin> {
        session.ouvrir(uri, source)
        return session.diagnostics(uri)
    }

    // ── Smart casts positionnels (la complétion membre voit le cast) ───

    @Test
    fun `smart-cast---branche-THEN-d'un-if,-membres-du-type-casté`() {
        val session = session(listOf(jarAndroid()))
        val source =
            """
            import android.widget.TextView
            fun demo(x: Any) {
              if (x is TextView) {
                x.
              }
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///cast-if.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.indexOf("x.") + 2)
        // Méthode DÉCLARÉE de TextView et méthode HÉRITÉE de View : le cast
        // a raffiné le type de x ET la chaîne de supertypes est résolue.
        assertNotNull(libelles.firstOrNull { it == "append" }, "le cast propose un membre déclaré de TextView")
        assertNotNull(libelles.firstOrNull { it == "requestLayout" }, "le cast propose un membre hérité de View")
        session.close()
    }

    @Test
    fun `smart-cast---garde-!is-a-sortie-precoce,-membres-apres-le-garde`() {
        val session = session(listOf(jarAndroid()))
        val source =
            """
            import android.widget.TextView
            fun demo(x: Any) {
              if (x !is TextView) return
              x.
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///cast-garde.kt"
        session.ouvrir(uri, source)
        val apres = source.indexOf("x.", startIndex = source.indexOf("return"))
        val libelles = libelles(session, uri, apres + 2)
        assertNotNull(libelles.firstOrNull { it == "append" }, "la garde !is caste la suite du bloc")
        session.close()
    }

    @Test
    fun `smart-cast---operande-droit-d'un-&&`() {
        val session = session(listOf(jarAndroid()))
        val source =
            """
            import android.widget.TextView
            fun demo(x: Any, apres: Boolean): Boolean {
              return x is TextView && x.
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///cast-et.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.indexOf("x.", source.indexOf("&&")) + 2)
        assertNotNull(libelles.firstOrNull { it == "append" }, "l'opérande droit du && voit le cast")
        session.close()
    }

    @Test
    fun `smart-cast---entree-d'un-when-sans-sujet`() {
        val session = session(listOf(jarAndroid()))
        val source =
            """
            import android.widget.TextView
            fun demo(x: Any): Int {
              when {
                x is TextView -> x.
                else -> 0
              }
              return 0
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///cast-when.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.indexOf("x.", source.indexOf("->")) + 2)
        assertNotNull(libelles.firstOrNull { it == "append" }, "le corps de l'entrée voit le cast")
        session.close()
    }

    @Test
    fun `smart-cast---locale-OMBREE-apres-la-condition,-le-cast-ne-fuit-pas`() {
        val session = session(listOf(jarAndroid(), jarStdlib()))
        val source =
            """
            import android.widget.TextView
            fun demo(x: Any) {
              if (x is TextView) {
                val x = "une chaine"
                x.
              }
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///cast-ombre.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.lastIndexOf("x.") + 2)
        // La locale String OMBRE le sujet du cast : les membres de TextView
        // ne sont PAS proposés, les EXTENSIONS de String le sont.
        assertEquals(null, libelles.firstOrNull { it == "append" }, "le cast ne fuit pas sur la locale")
        assertNotNull(libelles.firstOrNull { it == "trim" }, "la locale ombreuse est typée par son littéral")
        session.close()
    }

    @Test
    fun `smart-cast---garde-sans-sortie-precoce,-aucun-cast-(sous-rapport)`() {
        val session = session(listOf(jarAndroid()))
        val source =
            """
            import android.widget.TextView
            fun demo(x: Any) {
              if (x !is TextView) {
                val b = 1
              }
              x.
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///cast-sans-garde.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.lastIndexOf("x.") + 2)
        assertEquals(
            null,
            libelles.firstOrNull { it == "append" },
            "la garde ne SORT pas : aucun cast ne s'applique",
        )
        session.close()
    }

    // ── Inférence bornée des paramètres de lambda ──────────────────────

    @Test
    fun `lambda---parametre-explicite-type-par-l'attente-de-la-fonction-projet`() {
        val session = session(listOf(jarAndroid(), jarStdlib()))
        val source =
            """
            fun repeter(n: Int, action: (String) -> Unit) {}
            fun demo() {
              repeter(2) { s -> s. }
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///lambda.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.indexOf("s.") + 2)
        assertNotNull(
            "s est typé String par l'attente (String) -> Unit",
            libelles.firstOrNull { it == "trim" },
        )
        session.close()
    }

    @Test
    fun `lambda---it-implicite-type-par-l'attente-a-parametre-unique`() {
        val session = session(listOf(jarAndroid(), jarStdlib()))
        val source =
            """
            fun repeter(n: Int, action: (String) -> Unit) {}
            fun demo() {
              repeter(2) { it. }
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///lambda-it.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.indexOf("it.") + 3)
        assertNotNull(libelles.firstOrNull { it == "trim" }, "it est typé String par l'attente")
        session.close()
    }

    @Test
    fun `lambda---SAM-d'une-interface-projet-a-methode-unique`() {
        val session = session(listOf(jarAndroid(), jarStdlib()))
        val source =
            """
            interface Ecrivain {
              fun ecrire(ligne: String)
            }
            fun demo() {
              val e = Ecrivain { l -> l. }
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///sam.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.indexOf("l.") + 2)
        assertNotNull(
            "l est typé String par la méthode unique du SAM",
            libelles.firstOrNull { it == "trim" },
        )
        session.close()
    }

    @Test
    fun `lambda---initialiseur-de-propriete-au-type-de-fonction-ecrit`() {
        val session = session(listOf(jarAndroid(), jarStdlib()))
        val source =
            """
            fun demo() {
              val cb: (String) -> Unit = { s -> s. }
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///lambda-propriete.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.indexOf("s.") + 2)
        assertNotNull(libelles.firstOrNull { it == "trim" }, "s est typé String par le type écrit")
        session.close()
    }

    @Test
    fun `lambda---sans-attente-demontrable,-aucun-membre-(sous-rapport)`() {
        val session = session(listOf(jarAndroid(), jarStdlib()))
        val source =
            """
            fun demo() {
              val f = { s -> s. }
            }
            """.trimIndent().replace("    ", "  ")
        val uri = "file:///lambda-inconnu.kt"
        session.ouvrir(uri, source)
        val libelles = libelles(session, uri, source.indexOf("s.") + 2)
        assertEquals(
            null,
            libelles.firstOrNull { it == "trim" },
            "aucune attente : s n'est pas typé (D3)",
        )
        session.close()
    }

    // ── Diagnostics sémantiques : kt.unresolved ─────────────────────────

    @Test
    fun `kt-unresolved---reference-simple-introuvable,-signalee`() {
        val session = session(listOf(jarStdlib()))
        val diagnostics = diagnostics(session, "file:///nu-resolu.kt", "fun demo() {\n  introuvable\n}\n")
        val nonResolus = diagnostics.filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        assertEquals(1, nonResolus.size, "la référence introuvable est signalée")
        session.close()
    }

    @Test
    fun `kt-unresolved---sans-classpath,-jamais-signale-(sous-rapport)`() {
        val nue = (ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau).creerSession()
        nue.ouvrir("file:///nu.kt", "fun demo() {\n  introuvable\n}\n")
        assertTrue(
            nue.diagnostics("file:///nu.kt").none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "régime dégradé : aucune preuve sans classpath",
        )
        nue.close()
    }

    @Test
    fun `kt-unresolved---selecteur-sur-recepteur-TYPO,-signale-(ADR-0223)`() {
        val session = session(listOf(jarAndroid()))
        val source =
            """
            import android.widget.TextView
            fun demo(v: TextView) {
              v.methodeInexistante
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///typo-selecteur.kt", source)
        val nonResolus = diagnostics.filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        assertEquals(1, nonResolus.size, "le sélecteur introuvable sur récepteur de bibliothèque est signalé")
        assertEquals(source.indexOf("methodeInexistante"), nonResolus.single().plage.debut)
        session.close()
    }

    @Test
    fun `kt-unresolved---selecteur-MEMBRE-sur-recepteur-typo,-signale-(ADR-0223)`() {
        val session = session(listOf(jarAndroid()))
        val source =
            """
            import android.widget.TextView
            fun demo(v: TextView) {
              v.methodeInexistante()
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///typo-membre.kt", source)
        val nonResolus = diagnostics.filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        assertEquals(1, nonResolus.size, "la méthode appelée introuvable est signalée")
        session.close()
    }

    @Test
    fun `kt-unresolved---callee-MEMBRE-d-un-recepteur-typé,-resolu-(ADR-0223)`() {
        val session = session(listOf(jarAndroid()))
        val source =
            """
            import android.widget.TextView
            fun demo(v: TextView) {
              v.append("x")
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///membre-resolu.kt", source)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "la méthode déclarée du récepteur de bibliothèque RÉSOUT (pas de faux positif)",
        )
        session.close()
    }

    @Test
    fun `kt-unresolved---recepteur-NON-TYPO,-selecteur-muet-(D3-inchangé)`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun demo(inconnu: ChoseAbsenteDuClasspath) {\n  inconnu.nimporte\n}\n"
        val diagnostics = diagnostics(session, "file:///recepteur-inconnu.kt", source)
        val nonResolus = diagnostics.filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        // Le type du récepteur est introuvable : le sélecteur reste muet
        // (le TYPE lui-même est signalé, pas le membre).
        assertEquals(1, nonResolus.size, "seul le type introuvable est signalé")
        assertEquals(source.indexOf("ChoseAbsenteDuClasspath"), nonResolus.single().plage.debut)
        session.close()
    }

    @Test
    fun `kt-unresolved---imports-par-defaut-kotlin-et-jvm,-jamais-signales-(KP8)`() {
        val session = session(listOf(jarStdlib()))
        val source =
            "fun demo(n: Number, cs: CharSequence): ArrayList<String> {\n  val z = n\n}\n"
        val diagnostics = diagnostics(session, "file:///kp8.kt", source)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "les types des paquets par défaut ne sont jamais signalés",
        )
        session.close()
    }

    @Test
    fun `kt-unresolved---type-hors-imports-par-defaut,-toujours-signale-(garde-KP8)`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun demo(x: ChoseIntrouvable) {\n}\n"
        val diagnostics = diagnostics(session, "file:///garde-kp8.kt", source)
        val nonResolus = diagnostics.filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        assertEquals(1, nonResolus.size, "un type hors catalogue et hors classpath est signalé")
        session.close()
    }

    @Test
    fun `kt-unresolved---appelable-du-classpath-sans-import,-signale-(ADR-0203)`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun f(): Double {\n  return PI\n}\n"
        val diagnostics = diagnostics(session, "file:///pi.kt", source)
        val nonResolus = diagnostics.filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        assertEquals(1, nonResolus.size, "kotlin.math.PI sans import est signalé (voie 6 filtrée)")
        session.close()
    }

    @Test
    fun `kt-unresolved---appelable-du-classpath-importe,-jamais-signale-(garde-ADR-0203)`() {
        val session = session(listOf(jarStdlib()))
        val source = "import kotlin.math.PI\n\nfun f(): Double {\n  return PI\n}\n"
        val diagnostics = diagnostics(session, "file:///pi-importe.kt", source)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "l'import exact rétablit la résolution",
        )
        session.close()
    }

    @Test
    fun `kt-unresolved---appelables-des-paquets-par-defaut,-jamais-signales-(garde-ADR-0203)`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun demo() {\n  println(\"x\")\n  val l = emptyList<String>()\n}\n"
        val diagnostics = diagnostics(session, "file:///defauts.kt", source)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "println et emptyList (paquets par défaut) résolvent par l'index",
        )
        session.close()
    }

    // ── Diagnostics sémantiques : kt.typeMismatch ───────────────────────

    @Test
    fun `kt-typeMismatch---litteral-chaine-affecte-a-Int,-signale`() {
        val session = session(listOf(jarStdlib()))
        val source = "val x: Int = \"s\"\n"
        val diagnostics = diagnostics(session, "file:///type.kt", source)
        val incompatibles = diagnostics.filter { it.code == CodeDiagnosticKotlin.TYPE_INCOMPATIBLE }
        assertEquals(1, incompatibles.size)
        assertEquals(SeveriteKotlin.ERREUR, incompatibles.single().severite)
        assertEquals("\"s\"".length, incompatibles.single().plage.fin - incompatibles.single().plage.debut)
        session.close()
    }

    @Test
    fun `kt-typeMismatch---conversions-legitales,-jamais-signalees-(sous-rapport)`() {
        val session = session(listOf(jarStdlib()))
        val source = "val a: Long = 1\nval b: Double = 2\nval c: String? = \"s\"\nval d: Boolean = true\n"
        val diagnostics = diagnostics(session, "file:///type-ok.kt", source)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.TYPE_INCOMPATIBLE },
            "élargissements numériques et nullabilité ne sont jamais signalés",
        )
        session.close()
    }

    // ── Diagnostics sémantiques : kt.argumentCount ──────────────────────

    @Test
    fun `kt-argumentCount---appel-d'une-fonction-du-projet,-arite-fausse`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            fun f(a: Int, b: Int) {}
            fun demo() {
              f(1)
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///arite.kt", source)
        val arites = diagnostics.filter { it.code == CodeDiagnosticKotlin.NOMBRE_ARGUMENTS }
        assertEquals(1, arites.size)
        val diagnostic = arites.single()
        assertEquals(SeveriteKotlin.ERREUR, diagnostic.severite)
        assertEquals(source.indexOf("f(1)"), diagnostic.plage.debut)
        assertTrue(diagnostic.message.contains("1"), "le message porte le compte de l'appel")
        assertTrue(diagnostic.message.contains("2"), "le message porte l'arité attendue")
        session.close()
    }

    @Test
    fun `kt-argumentCount---constructeur-du-projet,-arite-fausse`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            class Point(val x: Int, val y: Int)
            fun demo() {
              val p = Point(1)
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///ctor.kt", source)
        assertEquals(1, diagnostics.filter { it.code == CodeDiagnosticKotlin.NOMBRE_ARGUMENTS }.size)
        session.close()
    }

    @Test
    fun `kt-argumentCount---defauts-et-varargs,-jamais-signales-(sous-rapport)`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            fun g(a: Int, b: Int = 2) {}
            fun h(vararg xs: Int) {}
            fun demo() {
              g(1)
              h()
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///arite-ok.kt", source)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.NOMBRE_ARGUMENTS },
            "les défauts et varargs éteignent le témoin d'arité",
        )
        session.close()
    }

    // ── Diagnostics sémantiques : kt.unsafeNullable ─────────────────────

    @Test
    fun `kt-unsafeNullable---point-sur-un-recepteur-nullable,-signale`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            fun demo(s: String?) {
              s.length
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///sur.kt", source)
        val nonsurs = diagnostics.filter { it.code == CodeDiagnosticKotlin.ACCES_NON_SUR }
        assertEquals(1, nonsurs.size)
        assertEquals(SeveriteKotlin.ERREUR, nonsurs.single().severite)
        assertTrue(nonsurs.single().message.contains("?."), "le message suggère l'accès sûr")
        session.close()
    }

    @Test
    fun `kt-unsafeNullable---garde-de-nullite-et-acces-sur,-jamais-signales-(sous-rapport)`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            fun demo(s: String?) {
              val n = s?.length
              if (s != null) {
                s.length
              }
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///sur-ok.kt", source)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.ACCES_NON_SUR },
            "l'accès sûr et la garde de nullité éteignent la preuve",
        )
        session.close()
    }

    // ── Diagnostics sémantiques : kt.missingReturn (gardes complètes) ───

    @Test
    fun `kt-missingReturn---corps-de-bloc-sans-return,-signale`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun f(): Int {\n  val a = 1\n}\n"
        val diagnostics = diagnostics(session, "file:///retour.kt", source)
        val manquants = diagnostics.filter { it.code == CodeDiagnosticKotlin.RETOUR_MANQUANT }
        assertEquals(1, manquants.size)
        assertTrue(manquants.single().message.contains("Int"), "le message porte le type attendu")
        session.close()
    }

    @Test
    fun `kt-missingReturn---Unit,-corps-d'expression-et-throw,-jamais-signales-(sous-rapport)`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            fun u() {
              val a = 1
            }
            fun e(): Int = 1
            fun t(): Int {
              throw IllegalStateException("x")
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///retour-ok.kt", source)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.RETOUR_MANQUANT },
            "Unit, corps d'expression et throw ne sont jamais signalés",
        )
        session.close()
    }

    // ── Diagnostics sémantiques : kt.unreachable ────────────────────────

    @Test
    fun `kt-unreachable---declaration-apres-un-return,-signalee`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun f(): Int {\n  return 1\n  val a = 2\n}\n"
        val diagnostics = diagnostics(session, "file:///mort.kt", source)
        val morts = diagnostics.filter { it.code == CodeDiagnosticKotlin.CODE_INATTEIGNABLE }
        assertEquals(1, morts.size)
        assertEquals(SeveriteKotlin.AVERTISSEMENT, morts.single().severite)
        assertEquals(source.indexOf("val a"), morts.single().plage.debut)
        session.close()
    }

    @Test
    fun `kt-unreachable---apres-throw-aussi,-return-imbrique-non-frere-ignore-(sous-rapport)`() {
        val session = session(listOf(jarStdlib()))
        val source =
            """
            fun f(): Int {
              if (true) {
                return 1
              }
              return 2
            }
            fun g(): Int {
              throw IllegalStateException("x")
              val a = 1
            }
            """.trimIndent().replace("    ", "  ")
        val diagnostics = diagnostics(session, "file:///mort2.kt", source)
        val morts = diagnostics.filter { it.code == CodeDiagnosticKotlin.CODE_INATTEIGNABLE }
        // Seul le `val a` de g suit DIRECTEMENT un terminal.
        assertEquals(1, morts.size)
        assertTrue(morts.single().plage.debut >= source.lastIndexOf("val a"))
        session.close()
    }

    // ── Diagnostics sémantiques : kt.valReassign ────────────────────────

    @Test
    fun `kt-valReassign---affectation-a-un-val,-signalee`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun demo() {\n  val v = 1\n  v = 2\n}\n"
        val diagnostics = diagnostics(session, "file:///val.kt", source)
        val reaffectations = diagnostics.filter { it.code == CodeDiagnosticKotlin.REAFFECTATION_VAL }
        assertEquals(1, reaffectations.size)
        val diagnostic = reaffectations.single()
        assertEquals(SeveriteKotlin.ERREUR, diagnostic.severite)
        assertEquals(source.lastIndexOf("v"), diagnostic.plage.debut)
        session.close()
    }

    @Test
    fun `kt-valReassign---var-et-cibles-introuvables,-jamais-signales-(sous-rapport)`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun demo() {\n  var w = 1\n  w = 2\n}\n"
        val diagnostics = diagnostics(session, "file:///var.kt", source)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REAFFECTATION_VAL },
            "var et cibles introuvables ne sont jamais signalés",
        )
        session.close()
    }

    @Test
    fun `diagnostics-semantiques---arbre-en-edition,-tout-le-semantique-ecarte`() {
        val session = session(listOf(jarStdlib()))
        // L'erreur de parse coupe la preuve de la DÉCLARATION brisée (garde
        // par déclaration, fiche 017) : le valReassign ci-dessous n'est PAS
        // signalé — tout le sémantique de la région est écarté.
        val source = "fun demo() {\n  val v = 1\n  v = (\n}\n"
        val diagnostics = diagnostics(session, "file:///edition.kt", source)
        assertTrue(diagnostics.any { it.code == CodeDiagnosticKotlin.SYNTAXE }, "le marqueur de syntaxe reste rendu")
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REAFFECTATION_VAL },
            "aucune preuve typée sur une déclaration brisée",
        )
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "aucune preuve d'absence sur une déclaration brisée",
        )
        session.close()
    }

    // ── Quick-fixes d'import ────────────────────────────────────────────

    @Test
    fun `importsCandidats---FQCN-du-classpath-portant-exactement-le-nom`() {
        val session = session(listOf(jarAndroid()))
        val source = "val v: TextView\n"
        val uri = "file:///fix.kt"
        session.ouvrir(uri, source)
        val candidats = session.importsCandidats(uri, source.indexOf("TextView") + 1)
        assertEquals(listOf("android.widget.TextView"), candidats)
        session.close()
    }

    @Test
    fun `importsCandidats---deja-importe,-aucun-candidat`() {
        val session = session(listOf(jarAndroid()))
        val source = "import android.widget.TextView\nval v: TextView\n"
        val uri = "file:///fix-importe.kt"
        session.ouvrir(uri, source)
        val candidats = session.importsCandidats(uri, source.lastIndexOf("TextView") + 1)
        assertTrue(candidats.isEmpty(), "résolu par l'import : rien à proposer")
        session.close()
    }

    @Test
    fun `importsCandidats---document-inconnu-et-sans-classpath,-vide`() {
        val session = session(listOf(jarAndroid()))
        assertTrue(session.importsCandidats("file:///jamais-ouvert.kt", 0).isEmpty())
        session.close()
        val nue = (ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau).creerSession()
        nue.ouvrir("file:///nu.kt", "val v: TextView\n")
        assertTrue(
            nue.importsCandidats("file:///nu.kt", 7).isEmpty(),
            "régime dégradé : pas de candidats",
        )
        nue.close()
    }
}

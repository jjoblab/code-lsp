package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.resolve.ExplicationsNonResolus
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 006 — ExplicationNonResoluTest : JOURNAL « EXPLAIN » des
 * `kt.unresolved` ÉMIS (mission fiabilisation, étape A.1 — instrumentation) :
 * chaque émission est consignée en niveau FINE — forme syntaxique de la
 * référence, issue de la sélection par types complets (ADR 0223, la « voie
 * de preuve » qui a conclu à l'absence), récepteur TYPÉ et état de
 * l'univers des preuves (scan projet, index portant, régime script).
 *
 * Ce test EST la « commande qui rend le journal » : il branche un
 * [Handler] JUL sur [ExplicationsNonResolus.journal], joue un document
 * porteur d'un vrai négatif NU et d'un vrai négatif QUALIFIÉ, et vérifie le
 * rendu greppable (`clé=valeur`) ligne par ligne. Les références RÉSOLUES ne
 * journalisent RIEN (le journal ne parle que des émissions), et le niveau
 * INFO de production ne rend rien du tout.
 *
 * Le classpath porte le VRAI jar stdlib (copie isolée — l'index doit
 * CONNAÎTRE des entrées pour prouver l'absence d'EXTENSION, ADR 0223) et
 * les racines sources nourrissent le scan projet, miroir du patron de la
 * fiche 022 (câblage de production D6 : moteur nourricier + session
 * branchée + guichet attendu par sondage borné).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/).
 */
class Fiche006ExplicationNonResoluTest {

    /** La racine temporaire des sources du projet. */
    @TempDir
    lateinit var racine: Path

    // ── Capture du journal FINE de l'instrumentation ──────────────────

    private val journal: Logger = Logger.getLogger(ExplicationsNonResolus::class.java.name)
    private val lignesJournal: MutableList<String> = mutableListOf()
    private var ancienNiveau: Level? = null
    private val ecouteur: Handler =
        object : Handler() {
            override fun publish(enregistrement: LogRecord) {
                lignesJournal += enregistrement.message
            }

            override fun flush() {
                // Rien à vider — la capture vit en mémoire du test.
            }

            override fun close() {
                // Rien à fermer — le test retire l'écouteur lui-même.
            }
        }

    @BeforeEach
    fun ecouter() {
        ancienNiveau = journal.level
        journal.level = Level.FINE
        journal.addHandler(ecouteur)
        lignesJournal.clear()
    }

    @AfterEach
    fun taire() {
        journal.removeHandler(ecouteur)
        journal.level = ancienNiveau
        lignesJournal.clear()
    }

    // ── Session branchée : stdlib réel (copie) + racines du scan ──────

    /** Sème une classe projet SANS héritage écrit (preuve d'absence fondée). */
    private fun semerSources(): Path {
        val racineSources = Files.createDirectories(racine.resolve("src-explain").resolve("com/ex/app"))
        Files.writeString(
            racineSources.resolve("Boite.kt"),
            """
            package com.ex.app

            class Boite(var contenu: String) {
                fun ouvrir(): Int = contenu.length
            }
            """.trimIndent(),
        )
        return racine.resolve("src-explain")
    }

    private fun session(racines: List<Path>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val copie = Files.createTempFile(racine, "stdlib-explain", ".jar").toFile()
        copie.writeBytes(
            java.io.File(
                kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                    .codeSource.location.toURI(),
            ).readBytes(),
        )
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, "explain:stdlib")),
            racinesSources = racines.map { it.toAbsolutePath().toString() },
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

    private fun nonResolues(
        session: SessionLangageKotlinDocuments,
        uri: String,
    ): List<String> = session.diagnostics(uri)
        .filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        .map { it.message }

    private fun ligneAttendue(
        source: String,
        marqueur: String,
    ): Int = 1 + source.substring(0, source.indexOf(marqueur)).count { it == '\n' }

    // ── Le journal rend chaque émission ────────────────────────────────

    @Test
    fun `nom-nu---le-journal-rend-la-forme,-l-issue-INCONNU-et-l-etat-des-preuves`() {
        val session = session(listOf(semerSources()))
        val source =
            """
            package com.ex.app

            fun demo() {
                val precedent = grincher(1)
            }
            """.trimIndent()
        val uri = "file:///explain-nom-nu.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(recus.any { it.contains("grincher") }, "grincher (vrai négatif NU) doit être signalé : $recus")
        val ligne = lignesJournal.filter { it.contains("« grincher »") }
        assertTrue(
            ligne.size == 1,
            "le journal doit contenir UNE ligne pour « grincher » : $lignesJournal",
        )
        assertTrue(ligne.single().contains("forme=nom-nu"), "forme du nom nu : $ligne")
        assertTrue(
            ligne.single().contains("selection=INCONNU"),
            "issue de la sélection (voie normale — pas de récepteur) : $ligne",
        )
        assertTrue(
            ligne.single().contains("recepteur=aucun"),
            "récepteur absent d'un nom nu : $ligne",
        )
        assertTrue(
            ligne.single().contains("scan=complet") &&
                ligne.single().contains("index=portant") &&
                ligne.single().contains("script=non"),
            "l'univers des preuves doit être rendu (scan complet, index portant, pas un script) : $ligne",
        )
        assertTrue(
            ligne.single().contains("${ligneAttendue(source, "grincher")}:"),
            "position ligne attendue : $ligne",
        )
    }

    @Test
    fun `selecteur-qualifie---le-journal-rend-l-ABSENCE_PROUVEE-et-le-recepteur-type`() {
        val session = session(listOf(semerSources()))
        val source =
            """
            package com.ex.app

            fun demo(b: Boite) {
                val x = b.contenu
                val u = b.champInconnu
            }
            """.trimIndent()
        val uri = "file:///explain-selecteur.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(recus.none { it.contains("« contenu »") }, "contenu (déclaré) muet : $recus")
        assertTrue(
            recus.any { it.contains("champInconnu") },
            "champInconnu (membre inexistant d'une classe sans héritage, scan complet) doit être signalé : $recus",
        )
        val ligne = lignesJournal.filter { it.contains("« champInconnu »") }
        assertTrue(
            ligne.size == 1,
            "le journal doit contenir UNE ligne pour « champInconnu » : $lignesJournal",
        )
        assertTrue(
            ligne.single().contains("selection=ABSENCE_PROUVEE"),
            "voie de preuve = absence prouvée par types complets : $ligne",
        )
        assertTrue(
            ligne.single().contains("forme=selecteur-qualifie-absence-prouvee"),
            "forme du sélecteur à absence prouvée : $ligne",
        )
        assertTrue(
            ligne.single().contains("recepteur=com.ex.app.Boite"),
            "le récepteur TYPÉ doit être rendu : $ligne",
        )
        assertTrue(
            ligne.single().contains("scan=complet") && ligne.single().contains("index=portant"),
            "l'univers des preuves doit être rendu : $ligne",
        )
    }

    @Test
    fun `references-resolues---le-journal-ne-dit-rien`() {
        val session = session(listOf(semerSources()))
        val source =
            """
            package com.ex.app

            fun demo(b: Boite) {
                val x = b.contenu
                val y = b.ouvrir()
                val t = b.toString()
            }
            """.trimIndent()
        val uri = "file:///explain-silence.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(recus.isEmpty(), "document sain : aucun non résolu, $recus")
        assertTrue(
            lignesJournal.isEmpty(),
            "aucune référence résolue ne journalise (le journal ne parle que des émissions) : $lignesJournal",
        )
    }

    @Test
    fun `journal-eteint-par-defaut---aucun-rendu-sans-ecouteur-FINE`() {
        // La production ne branche AUCUN handler et le niveau par défaut
        // (INFO) filtre FINE : l'enregistrement paresseux ne construit
        // RIEN. Prouvé en baissant le niveau sous FINE pendant l'émission.
        journal.level = Level.INFO
        val session = session(listOf(semerSources()))
        val source =
            """
            package com.ex.app

            fun demo() {
                val precedent = grincher(1)
            }
            """.trimIndent()
        val uri = "file:///explain-eteint.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(recus.any { it.contains("grincher") })
        assertTrue(
            lignesJournal.isEmpty(),
            "niveau INFO (production) : le journal ne rend RIEN : $lignesJournal",
        )
    }
}

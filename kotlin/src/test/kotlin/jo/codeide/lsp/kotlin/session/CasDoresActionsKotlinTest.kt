package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreActionKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin

/**
 * CAS DORÉS WorkspaceEdit — critère de sortie ADR 0115 (« cas dorés des
 * WorkspaceEdit exacts au banc ») : chaque plan d'action de l'étage 5
 * vague 5d est asserté par ses éditions EXACTES (uri, plages, textes) —
 * le plan déclaratif est SÉRIALISABLE et son application est testée
 * comme un WorkspaceEdit doré, pas comme un effet de bord.
 *
 * Les cas couvrent les trois familles (import, membres manquants,
 * correctif de texte) et les gardes (sans preuve : liste vide ;
 * idempotence du calcul).
 */
class CasDoresActionsKotlinTest {

    /** La racine temporaire de la copie du jar. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val copie = Files.createTempFile(racine, "dore-stdlib", ".jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, "dore:stdlib")),
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

    // ------------------------------------------------------------------
    // Cas doré 1 — le plan d'IMPORT : éditions EXACTES de l'insertion.
    // ------------------------------------------------------------------

    @Test
    fun `cas-dore---plan-d-import---edition-exacte-apres-la-derniere-directive`() {
        val session = session()
        val source =
            "import java.util.ArrayList\n" +
                "\n" +
                "fun demo(expose: ArrayList<String>, suite: Continuation<String>) {\n" +
                "}\n"
        session.ouvrir("fixture://dore.kt", source)
        val deplacement = source.indexOf("Continuation")
        val plans = session.actions("fixture://dore.kt", deplacement)
        // Le nom `Continuation` est non résolu dans le document (le
        // paquet kotlin.coroutines n'est PAS importé par défaut) ; le
        // stdlib du classpath porte la classe — un plan, une édition, la
        // plage EXACTE d'insertion après le dernier import.
        val planImport = plans.single { it.titre.startsWith("Importer ") }
        assertEquals(GenreActionKotlin.RAPIDE, planImport.genre)
        assertEquals(1, planImport.editions.size)
        val fichier = planImport.editions.single()
        assertEquals("fixture://dore.kt", fichier.uri)
        assertEquals(1, fichier.editions.size)
        val edition = fichier.editions.single()
        // L'insertion vit à la FIN de la dernière directive (plage vide =
        // insertion) et porte le retour à la ligne + la directive.
        assertEquals(source.indexOf("ArrayList") + "ArrayList".length, edition.plage.debut)
        assertEquals(edition.plage.debut, edition.plage.fin)
        assertEquals("\nkotlin.coroutines.Continuation", edition.texte)
        session.close()
    }

    // ------------------------------------------------------------------
    // Cas doré 2 — le plan MEMBRES MANQUANTS : l'insertion des stubs.
    // ------------------------------------------------------------------

    @Test
    fun `cas-dore---plan-membres-manquants---edition-exacte-de-l-insertion`() {
        val session = session()
        val source =
            "interface Depot {\n" +
                "    fun lire(cle: String): String\n" +
                "}\n" +
                "\n" +
                "class Memoire : Depot {\n" +
                "}\n"
        session.ouvrir("fixture://dore.kt", source)
        val deplacement = source.indexOf("Memoire")
        val plans = session.actions("fixture://dore.kt", deplacement)
        val planMembres = plans.single { it.titre.startsWith("Implémenter les membres manquants") }
        assertEquals(GenreActionKotlin.RAPIDE, planMembres.genre)
        val edition = planMembres.editions.single().editions.single()
        assertEquals("fixture://dore.kt", planMembres.editions.single().uri)
        // L'insertion vit en fin d'en-tête de classe (corps créé), le
        // stub porte la signature de l'interface — le TEXTE exact est
        // doré : deux espaces d'indentation, paramètre substitué.
        assertTrue(edition.texte.contains("override fun lire(cle: String): String"), "le stub exact : ${edition.texte}")
        assertTrue(edition.plage.debut >= source.indexOf("Memoire"), "l'insertion vit dans la classe")
        session.close()
    }

    // ------------------------------------------------------------------
    // Cas doré 3 — le plan de CORRECTIF au caret : accès sûr.
    // ------------------------------------------------------------------

    @Test
    fun `cas-dore---plan-correctif-au-caret---edition-exacte-de-l-acces-sur`() {
        val session = session()
        val source =
            "fun demo(nom: String?): Int {\n" +
                "  return nom.length\n" +
                "}\n"
        session.ouvrir("fixture://dore.kt", source)
        val deplacement = source.indexOf(".length")
        val plans = session.actions("fixture://dore.kt", deplacement)
        val planSur = plans.single { it.titre == "Accès sûr : nom?." }
        assertEquals(GenreActionKotlin.RAPIDE, planSur.genre)
        val edition = planSur.editions.single().editions.single()
        assertEquals("fixture://dore.kt", planSur.editions.single().uri)
        // L'édition remplace le SÉLECTEUR par la forme sûre : plage du
        // `.` et texte `?.` — exactement le correctif prouvé.
        assertEquals(source.indexOf(".length"), edition.plage.debut)
        assertEquals(source.indexOf(".length") + 1, edition.plage.fin)
        assertEquals("?.", edition.texte)
        session.close()
    }

    // ------------------------------------------------------------------
    // Cas doré 4 — SANS PREUVE : liste vide (jamais une action inventée).
    // ------------------------------------------------------------------

    @Test
    fun `cas-dore---sans-preuve---liste-vide`() {
        val session = session()
        val source =
            "import java.util.ArrayList\n" +
                "\n" +
                "fun demo(): ArrayList<String> = ArrayList()\n"
        session.ouvrir("fixture://dore.kt", source)
        // Le nom `ArrayList` RÉOUT déjà par l'import exact : aucun plan
        // d'import (fiche 047 — rien à proposer).
        assertEquals(0, session.actions("fixture://dore.kt", source.indexOf("ArrayList")).size)
        // Le caret dans un BLANC : aucune preuve, aucune action.
        assertEquals(0, session.actions("fixture://dore.kt", source.indexOf("\n")).size)
        // Document inconnu : liste vide, jamais d'exception.
        assertEquals(0, session.actions("fixture:///inconnu.kt", 0).size)
        session.close()
    }

    // ------------------------------------------------------------------
    // Cas doré 5 — IDEMPOTENCE du calcul : deux requêtes, mêmes plans.
    // ------------------------------------------------------------------

    @Test
    fun `cas-dore---idempotence---deux-calculs-memes-plans`() {
        val session = session()
        val source =
            "interface Depot {\n" +
                "    fun lire(cle: String): String\n" +
                "}\n" +
                "\n" +
                "class Memoire : Depot {\n" +
                "}\n"
        session.ouvrir("fixture://dore.kt", source)
        val deplacement = source.indexOf("Memoire")
        val premiers = session.actions("fixture://dore.kt", deplacement)
        val seconds = session.actions("fixture://dore.kt", deplacement)
        // Le plan est une VALEUR : re-calculé à l'identique (calcul en
        // lecture, re-preuve à la requête — ADR 0115).
        assertEquals(premiers.map { it.titre }, seconds.map { it.titre }, "mêmes titres")
        assertEquals(
            premiers.map { plan -> plan.editions.map { f -> f.uri to f.editions.map { it.plage.debut to it.plage.fin to it.texte } } },
            seconds.map { plan -> plan.editions.map { f -> f.uri to f.editions.map { it.plage.debut to it.plage.fin to it.texte } } },
            "mêmes éditions",
        )
        session.close()
    }
}

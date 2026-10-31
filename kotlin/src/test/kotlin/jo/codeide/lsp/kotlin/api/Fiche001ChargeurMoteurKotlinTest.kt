package jo.codeide.lsp.kotlin.api

import java.util.Properties
import java.util.ServiceLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fiche 001 — ChargeurMoteurKotlinTest : tests du SPI — résolution
 * ServiceLoader du moteur (le contrat de pack LSP_KOTLIN §6 vérifié
 * MÉCANIQUEMENT, pas seulement revu) et intégrité du descripteur de pack
 * (3 comportements du cahier docs/cahier-specifications-kotlin/).
 *
 * Le cœur éprouvé : le descripteur `version.properties` GÉNÉRÉ porte des
 * VALEURS RÉELLES — les replis « inconnue » du moteur sont des files de
 * secours ; si le descripteur est absent du classpath, c'est le CONTRAT
 * de pack qui est cassé (D6) — le test le DIT, il ne l'absorbe pas.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche001ChargeurMoteurKotlinTest {

    /** Le motif d'une version semver (x.y.z — la forme attendue de minApp). */
    private fun motifVersion(texte: String): Boolean = Regex("""\d+\.\d+\.\d+""").matches(texte)

    @Test
    fun `serviceLoader-resolve-le-moteur-unique`() {
        // Le descripteur META-INF/services embarqué porte UN fournisseur
        // exactement — le chargement du pack est mécanique, pas revu.
        val fournisseurs = ServiceLoader.load(ServiceMoteurLangageKotlin::class.java).toList()
        assertEquals(1, fournisseurs.size, "un FOURNISSEUR exactement dans le descripteur du pack")
        // L'entrée UNIQUE du SPI est le fournisseur résolu (même classe —
        // le repli noyau ne se substitue JAMAIS à un fournisseur enregistré).
        assertEquals(
            fournisseurs.single()::class.java,
            ServiceMoteurLangageKotlin.unique::class.java,
            "unique est le fournisseur du ServiceLoader",
        )
    }

    @Test
    fun `descripteur-de-pack-complet---valeurs-reelles-pas-des-replis`() {
        val moteur = ServiceMoteurLangageKotlin.unique
        assertEquals("jo-codeide-lsp-kotlin", moteur.identifiant, "l'identifiant exact du pack (clé id)")
        assertNotEquals("inconnue", moteur.version, "la version est RÉELLE (générée du projet)")
        assertNotEquals("inconnue", moteur.versionGrammaire, "la version de grammaire est RÉELLE (épinglée au build)")
        assertTrue(motifVersion(moteur.versionMinimaleApp), "minApp lisible : ${moteur.versionMinimaleApp}")
        assertTrue(motifVersion(moteur.version), "la version suit le motif x.y.z : ${moteur.version}")
        // Les CLÉS du descripteur sont VERROUILLÉES (contrat D6 — la
        // génération :kotlin:genererDescripteurPack ne change pas de forme
        // sans faire échouer CE test).
        val flux = assertNotNull(
            ServiceMoteurLangageKotlin::class.java.getResourceAsStream("/jo/codeide/lsp/kotlin/pack/version.properties"),
            "la ressource version.properties est DANS le pack (contrat D6)",
        )
        val cles = Properties().apply { flux.use { load(it) } }.stringPropertyNames().toSet()
        assertEquals(setOf("id", "version", "minApp", "grammaire"), cles, "les quatre clés exactement")
    }

    @Test
    fun `le-moteur-analyse-via-le-spi---arbre-non-vide`() {
        val session = ServiceMoteurLangageKotlin.unique.creerSession()
        val uri = "fixture://essai.kt"
        try {
            session.ouvrir(uri, "val essai = 1\n")
            // L'analyse par le SPI (chemin de production D6) : l'arbre est
            // NON VIDE (la déclaration remonte au plan du document) et
            // SANS ERREUR (aucun diagnostic — l'arbre racine kotlin.FILE
            // du test source, vu par la frontière du SPI).
            val symboles = session.symboles(uri)
            assertEquals(listOf("essai"), symboles.map { it.nom }, "l'arbre analysé porte la déclaration")
            assertTrue(session.diagnostics(uri).isEmpty(), "zéro erreur d'analyse")
        } finally {
            session.close()
        }
    }
}

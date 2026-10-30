package jo.codeide.lsp.kotlin.classpath

import java.io.File
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fiche 002 — DecodeurMetadonneesTest : tests du décodeur de métadonnées
 * (K3) sur des jars RÉELS du classpath de test :
 *  - kotlin-stdlib (Kotlin → appelables de premier niveau + extensions) ;
 *  - junit (Java → PORTE ABSENTE, zéro classe décodée — le saut de §4.2
 *    est le contrat même du décodeur).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/). Les jars sont
 * localisés au runtime par leurs ANCRES de classe, jamais résolus en code.
 */
class Fiche002DecodeurMetadonneesTest {

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): File =
        File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /**
     * Le jar junit du classpath de test (bibliothèque Java — opentest4j,
     * la bibliothèque d'assertions de JUnit 5 : AUCUN module Kotlin ; les
     * jars jupiter en portent un depuis JUnit 5.11, ils ne peuvent pas
     * servir ce témoin).
     */
    private fun jarJunit(): File =
        File(org.opentest4j.AssertionFailedError::class.java.protectionDomain.codeSource.location.toURI())

    @Test
    fun `stdlib---des-appelables-de-premier-niveau-et-des-extensions`() {
        val appelables = DecodeurMetadonnees.decoder(jarStdlib(), KindEntreeKotlin.JAR)
        assertTrue(appelables.size > 100, "aucun appelable décodé du stdlib")
        // Fonctions de premier niveau : p.ex. kotlin.text.trim (String).
        assertTrue(appelables.any { it.recepteur == "kotlin.String" })
        // Paquets réels du stdlib.
        assertTrue(appelables.any { it.paquet == "kotlin.text" })
        // Chaque appelable porte son conteneur (facade du fichier).
        assertTrue(appelables.all { it.conteneur.isNotEmpty() })
    }

    @Test
    fun `jar-Java-(junit)---porte-absente,-ZERO-appelable`() {
        val appelables = DecodeurMetadonnees.decoder(jarJunit(), KindEntreeKotlin.JAR)
        assertTrue(appelables.isEmpty(), "un jar sans module Kotlin ne doit rien rendre")
    }

    @Test
    fun `fichier-absent---liste-vide,-jamais-d'exception-(D3)`() {
        assertTrue(DecodeurMetadonnees.decoder(File("/inexistant/rien.jar"), KindEntreeKotlin.JAR).isEmpty())
        assertTrue(DecodeurMetadonnees.decoder(File("/inexistant/rien.aar"), KindEntreeKotlin.AAR).isEmpty())
    }
}

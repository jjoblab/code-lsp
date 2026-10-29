package spikekotlin

import jo.lspkotlin.api.ClasspathKotlin
import jo.lspkotlin.api.ServiceMoteurLangageKotlin
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Banc S0 — ORACLE : l'ancien moteur Kotlin PUBLIÉ, exécuté en boîte noire.
 *
 * Artéfact : `com.github.jjoblab.codeide-lsp:kotlin:1.22.1` (GitHub
 * Packages, jar local `../spike-jars/lsp-kotlin-1.22.1.jar`). Le moteur
 * est conduit UNIQUEMENT par son SPI public (`ServiceMoteurLangageKotlin`,
 * surface de l'artefact) — aucune source de l'ancien dépôt n'est lue
 * (R9.1) : c'est l'usage d'un consommateur standard de l'artefact.
 *
 * Mesures (limites basses JVM de bureau, R5 — cf. 02-spike-mesures.md) :
 * - reparse complet (ouvrirDocument / modifierDocument) à froid puis à
 *   chaud (min de N passes) sur corpus 500 et 3 000 lignes ;
 * - diagnostics complets à froid puis à chaud ;
 * - toutes SANS classpath réel (vue vide) : le banc borne le plan
 *   « édition » du moteur ; les chiffres publiés de l'ancien dépôt
 *   (31/21 ms reparse, 469/147 ms diagnostics, stdlib nourrie) restent
 *   la référence du plan complet.
 */
class BancS0Test {

    /** Le jar oracle doit être présent (téléchargé par scripts/telecharger-jars.sh). */
    companion object {
        @JvmStatic
        @BeforeClass
        fun jarPresent() {
            assumeTrue(
                "jar oracle absent — lancer spike-kotlin/scripts/telecharger-jars.sh",
                CheminJars.jar("lsp-kotlin-1.22.1.jar") != null,
            )
        }
    }

    /** Ouvre une session neuve avec la vue vide (plan édition seul). */
    private fun sessionEdition() =
        ServiceMoteurLangageKotlin.unique().creerSession().also { session ->
            session.definirClasspath(
                ClasspathKotlin(
                    entrees = emptyList(),
                    vue = VueVide(),
                    racinesSources = emptyList(),
                    dossierIndex = null,
                ),
            )
        }

    /** Format d'impression des mesures (une ligne greppable par opération). */
    private fun ms(nanos: Long): String = "%.1f".format(nanos / 1_000_000.0)

    @Test
    fun `corpus 500 lignes - reparse et diagnostics, froid puis chaud`() {
        val texte = GenerateurCorpus.fichier(500)
        val uri = "file:///corpus500.kt"
        val session = sessionEdition()

        // Froid : première ouverture = parse + diagnostics complets.
        val tOuvrirFroid = mesure { session.ouvrirDocument(uri, texte) }
        val tDiagFroid = mesure { session.diagnostics(uri) }

        // Chaud : modification complète (reparse) × passes, min retenu.
        var meilleurReparse = Long.MAX_VALUE
        var meilleurDiag = Long.MAX_VALUE
        for (passe in 1..5) {
            val tRep = mesure { session.modifierDocument(uri, texte) }
            val tDiag = mesure { session.diagnostics(uri) }
            if (tRep < meilleurReparse) meilleurReparse = tRep
            if (tDiag < meilleurDiag) meilleurDiag = tDiag
        }
        session.close()

        println("S0;corpus=500;ouvrir_froid=${ms(tOuvrirFroid)}ms;diagnostics_froid=${ms(tDiagFroid)}ms;reparse_chaud_min=${ms(meilleurReparse)}ms;diagnostics_chaud_min=${ms(meilleurDiag)}ms;passes=5")
        assertTrue(true)
    }

    @Test
    fun `corpus 3000 lignes - reparse et diagnostics, froid puis chaud`() {
        val texte = GenerateurCorpus.fichier(3000)
        val uri = "file:///corpus3000.kt"
        val session = sessionEdition()

        val tOuvrirFroid = mesure { session.ouvrirDocument(uri, texte) }
        val tDiagFroid = mesure { session.diagnostics(uri) }

        var meilleurReparse = Long.MAX_VALUE
        var meilleurDiag = Long.MAX_VALUE
        for (passe in 1..5) {
            val tRep = mesure { session.modifierDocument(uri, texte) }
            val tDiag = mesure { session.diagnostics(uri) }
            if (tRep < meilleurReparse) meilleurReparse = tRep
            if (tDiag < meilleurDiag) meilleurDiag = tDiag
        }
        session.close()

        println("S0;corpus=3000;ouvrir_froid=${ms(tOuvrirFroid)}ms;diagnostics_froid=${ms(tDiagFroid)}ms;reparse_chaud_min=${ms(meilleurReparse)}ms;diagnostics_chaud_min=${ms(meilleurDiag)}ms;passes=5")
        assertTrue(true)
    }

    /** Mesure le temps d'exécution de [bloc] en nanosecondes (System.nanoTime). */
    private inline fun mesure(bloc: () -> Unit): Long {
        val debut = System.nanoTime()
        bloc()
        return System.nanoTime() - debut
    }
}

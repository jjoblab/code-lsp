// CodeIDE — tests du SPI mémoire de la session (module :kotlin, vague V-A —
// ADR 0117/0134 : budgets octets + observateur onTrimMemory). Code NEUF
// jjoblab (clean-room — comportements : la vidange est HONNÊTE, tout se
// reconstruit ; la session fermée ignore silencieusement ; le budget
// d'octets vidange au plafond).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.NiveauMemoireKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.resolve.CacheBorneOctets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * La vague V-A — le SPI MÉMOIRE : [SessionLangageKotlin.reduireMemoire]
 * (l'hôte traduit ses rappels plateforme en niveaux neutres) et les
 * BUDGETS D'OCTETS des caches de la vue JVM (vidange au plafond,
 * reconstruction à la demande — jamais une perte de justesse).
 */
class MemoireSessionKotlinTest {

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
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "memoire:stdlib"),
            ),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        val limite = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return session
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /**
     * Le BUDGET D'OCTETS vidange au plafond : un cache dont le budget est
     * dépassé se vide ENTIÈREMENT avant d'accueillir la nouvelle entrée
     * (jamais de croissance silencieuse, jamais de perte de justesse — la
     * relecture reconstruit).
     */
    @Test
    fun `budget-octets---vidange-entiere-au-plafond`() {
        val cache = CacheBorneOctets<String, String>(budgetOctets = 200L) { cle, valeur ->
            40L + 2L * (cle.length + valeur.length)
        }
        // Trois entrées d'environ 100 octets : la troisième dépasse 200.
        cache["aaaa"] = "valeur-aaaa"
        cache["bbbb"] = "valeur-bbbb"
        assertEquals(2 * (40L + 2L * 15), cache.octetsEstimes, "deux entrées budgétées")
        cache["cccc"] = "valeur-cccc"
        assertEquals(40L + 2L * 15, cache.octetsEstimes, "la vidange au plafond ne garde que la nouvelle entrée")
        assertEquals(null, cache["aaaa"], "l'entrée d'avant plafond est partie")
        assertEquals("valeur-cccc", cache["cccc"], "la nouvelle entrée vit")
        cache.vider()
        assertEquals(0L, cache.octetsEstimes, "la vidange explicite remet le compteur à zéro")
    }

    /**
     * Le budget d'octets compte AUSSI les témoins `null` (l'introuvable
     * caché est une entrée qui occupe — jamais de table infinie de
     * négatifs).
     */
    @Test
    fun `budget-octets---temoin-null-compte-et-sert`() {
        val cache = CacheBorneOctets<String, Nothing?>(budgetOctets = 1L) { cle, _ -> 40L + 2L * cle.length }
        cache["introuvable"] = null
        assertTrue(cache.contient("introuvable"), "le témoin null est servi")
        assertEquals(null, cache["introuvable"], "la valeur null est retournée telle quelle")
        assertTrue(cache.octetsEstimes > 0L, "le témoin null occupe son budget")
    }

    /**
     * reduireMemoire(FAIBLE) vidange les caches de lecture paresseuse de la
     * vue JVM — et la REQUÊTE suivante reconstruit : le diagnostic d'avant
     * et d'après la vidange est IDENTIQUE (jamais une perte de justesse).
     */
    @Test
    fun `reduire-memoire---vidange-puis-reconstruit-identique`() {
        val session = session()
        try {
            val source = "fun demo() { Intrinsics introuvable = 1 }\n"
            session.ouvrir("file:///memoire.kt", source)
            val avant = session.diagnostics("file:///memoire.kt")
            assertTrue(avant.isNotEmpty(), "le document porte des diagnostics (syntaxe/typage)")
            val occupation = session.indexSiPret()?.vueJvm?.occupationOctetsEstimee() ?: 0L
            session.reduireMemoire(NiveauMemoireKotlin.FAIBLE)
            val apresVidange = session.indexSiPret()?.vueJvm?.occupationOctetsEstimee() ?: 0L
            assertEquals(0L, apresVidange, "la vidange FAIBLE remet l'occupation à zéro (était $occupation)")
            val apres = session.diagnostics("file:///memoire.kt")
            assertEquals(avant.map { it.code to it.plage.debut }, apres.map { it.code to it.plage.debut },
                "la reconstruction rend le diagnostic identique")
        } finally {
            session.close()
        }
    }

    /** Une session FERMÉE ignore silencieusement la réduction (no-op honnête). */
    @Test
    fun `reduire-memoire---session-fermee-no-op-silencieux`() {
        val session = session()
        session.close()
        session.reduireMemoire(NiveauMemoireKotlin.URGENT)
        assertEquals(emptyList(), session.diagnostics("file:///quelconque.kt"), "fermée = vide, jamais d'exception")
    }

    /** Les niveaux forment une échelle croissante (l'hôte traduit sa plateforme dessus). */
    @Test
    fun `niveaux---echelle-croissante-documentee`() {
        assertTrue(NiveauMemoireKotlin.UI_MASQUEE < NiveauMemoireKotlin.FAIBLE)
        assertTrue(NiveauMemoireKotlin.FAIBLE < NiveauMemoireKotlin.CRITIQUE)
        assertTrue(NiveauMemoireKotlin.CRITIQUE < NiveauMemoireKotlin.URGENT)
    }

    /** Le cache borné ne s'utilise qu'en interne : l'estimation négative est impossible (défensif). */
    @Test
    fun `budget-octets---reecriture-d-une-cle-ne-fuite-pas`() {
        val cache = CacheBorneOctets<String, String>(budgetOctets = 10_000L) { cle, valeur ->
            40L + 2L * (cle.length + valeur.length)
        }
        cache["cle"] = "première-valeur-longue-pour-le-budget"
        val premiere = cache.octetsEstimes
        cache["cle"] = "x"
        assertTrue(cache.octetsEstimes < premiere, "la réécriture remplace le coût, elle ne l'accumule pas")
        assertEquals("x", cache["cle"], "la valeur remplacée est servie")
    }
}

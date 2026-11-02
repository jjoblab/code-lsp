package jo.codeide.lsp.kotlin.session

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 034 — ReproductionSegmentsPersistantsTest : REPRODUCTION H4
 * (hypothèse « segment d'index partiel / périmé » du prompt) : l'index
 * des appelables persiste ses segments sous `dossierIndex`
 * (`kotlin-appelables/<sha256>.kidx`), relus PAR EMPREINTE. Les scénarios
 * suspectés — segment CORROMPU, segment de VERSION périmée (le format
 * porte magie + version), jar DISPARU — sont mesurés ici en session
 * complète (usage direct, nourrissage synchrone — ADR 0211).
 *
 * Comportement attendu : le segment n'est JAMAIS une autorité — corrompu
 * ou périmé, il est REJETÉ puis re-décodé depuis le jar (auto-guérison) ;
 * un jar ABSENT rend l'entrée ÉCARTÉE, le snapshot ne couvre PLUS les
 * empreintes et la garde ADR 0211 (`indexSiPret` ⇒ `null`) TAIT tout
 * futur `kt.unresolved` — jamais de faux positif sur un index qui ne
 * prouve rien.
 *
 * À la vague 1d, le diagnostic `kt.unresolved` n'existe pas encore (il
 * naît à l'étage 2) : la garde s'atteste par le GUICHET d'index que ce
 * diagnostic consultera (miroir du choix de la fiche 042 à la vague 1b) —
 * et par le service réel de la vue d'appelables quand l'index est
 * portant (`println` servi, extensions vivantes).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/).
 */
class Fiche034ReproductionSegmentsPersistantsTest {

    /** La racine temporaire (dossiers d'index isolés). */
    @TempDir
    lateinit var dossier: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): File =
        File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Le classpath témoin : le stdlib RÉEL (jamais modifié), empreinte de test. */
    private fun classpathStdlib(dossierIndex: String?): ClasspathKotlin =
        ClasspathKotlin(
            entrees =
                listOf(
                    EntreeClasspathKotlin(
                        KindEntreeKotlin.JAR,
                        jarStdlib().path,
                        EMPREINTE,
                    ),
                ),
            racinesSources = emptyList(),
            dossierIndex = dossierIndex,
        )

    /** Une session DIRECTE (miroir A211) — le dossierIndex vit dans le CLASSPATH. */
    private fun session(): SessionLangageKotlinDocuments = SessionLangageKotlinDocuments(moteur = null)

    /** Le segment persisté de l'empreinte de test sous [dossierIndex], s'il existe. */
    private fun segmentDe(dossierIndex: Path): File? =
        Files.walk(dossierIndex)
            .filter { fichier -> fichier.fileName.toString() == session().indexLocal.nomSegment(EMPREINTE) }
            .findFirst()
            .orElse(null)
            ?.toFile()

    @Test
    fun `segment-corrompu---auto-guerison,-l-index-reste-portant`() {
        val dossierIndex = Files.createDirectories(dossier.resolve("index-corrompu"))
        // 1. Premier passage : le segment est écrit sur disque, l'index
        //    est PORTANT (le guichet sert la vue d'appelables).
        val premiere = session()
        premiere.definirClasspath(classpathStdlib(dossierIndex.toString()))
        val vue1 = assertNotNull(premiere.indexSiPret(), "témoin initial : l'index est portant")
        assertFalse(
            vue1.vue.topParNom("println").isEmpty(),
            "témoin : println est servi (top-level du stdlib)",
        )
        premiere.close()
        val segment = segmentDe(dossierIndex)
        assertTrue(segment != null && segment.isFile, "le segment doit exister après le premier passage")
        // 2. CORRUPTION : des octets quelconques remplacent le segment.
        segment!!.writeBytes("CORROMPU-PAS-UN-SEGMENT".toByteArray())
        // 3. Session NEUVE, même empreinte : le segment est rejeté puis
        //    re-décodé depuis le jar — l'index reste PORTANT.
        val seconde = session()
        seconde.definirClasspath(classpathStdlib(dossierIndex.toString()))
        val vue2 =
            assertNotNull(
                seconde.indexSiPret(),
                "auto-guérison : l'entrée est re-décodée, le guichet reste ouvert",
            )
        assertFalse(
            vue2.vue.topParNom("println").isEmpty(),
            "auto-guérison : println reste servi (l'index a été re-décodé, pas muet)",
        )
        assertFalse(
            vue2.vue.extensionsSur("kotlin.String").isEmpty(),
            "auto-guérison : les extensions de String restent vivantes",
        )
        seconde.close()
    }

    @Test
    fun `segment-de-version-perimee---rejete-puis-re-decode,-pas-de-faux-positif`() {
        val dossierIndex = Files.createDirectories(dossier.resolve("index-perime"))
        val premiere = session()
        premiere.definirClasspath(classpathStdlib(dossierIndex.toString()))
        val vueTemoin = assertNotNull(premiere.indexSiPret()).vue
        val attendu = vueTemoin.topParNom("println")
        assertFalse(attendu.isEmpty(), "témoin : println est servi")
        premiere.close()
        val segment = segmentDe(dossierIndex)
        assertTrue(segment != null && segment.isFile)
        // Version = 2e entier du format (magie, VERSION, empreinte…) :
        // on la FORCE à 0 — un segment d'une AUTRE ère du format, par
        // ailleurs cohérent, exactement la montée de version.
        val octets = segment!!.readBytes()
        octets[4] = 0
        octets[5] = 0
        octets[6] = 0
        octets[7] = 0
        segment.writeBytes(octets)
        val seconde = session()
        seconde.definirClasspath(classpathStdlib(dossierIndex.toString()))
        val vue2 =
            assertNotNull(
                seconde.indexSiPret(),
                "segment périmé rejeté puis re-décodé : le guichet reste ouvert",
            )
        // PAS DE FAUX POSITIF : le service re-décodé est IDENTIQUE au
        // témoin (mêmes appelables servis — rien de fantôme, rien de perdu).
        assertEquals(
            attendu,
            vue2.vue.topParNom("println"),
            "segment périmé : le re-décodage sert exactement le témoin",
        )
        seconde.close()
    }

    @Test
    fun `jar-absent---l-entree-est-ecartee-et-la-garde-tait-tout-kt-unresolved`() {
        val classpathCasse =
            ClasspathKotlin(
                entrees =
                    listOf(
                        EntreeClasspathKotlin(
                            KindEntreeKotlin.JAR,
                            "/chemin/inexistant.jar",
                            "h4:absent",
                        ),
                    ),
                racinesSources = emptyList(),
                dossierIndex = null,
            )
        val session = session()
        session.definirClasspath(classpathCasse)
        // L'entrée ÉCARTÉE ne couvre PAS les empreintes : la garde ADR
        // 0211 rend le guichet MUET — le futur `kt.unresolved` (étage 2)
        // qui s'y branche se TAIT, même pour un vrai négatif. C'est le
        // contrat « index froid = silence ».
        assertFalse(
            session.indexLocal.couvre(classpathCasse.entrees),
            "l'entrée est écartée : l'index local ne couvre pas le classpath",
        )
        assertNull(
            session.indexSiPret(),
            "jar absent : la garde tient TOUT — le guichet d'index est muet",
        )
        session.close()
    }

    private companion object {
        private const val EMPREINTE: String = "h4:stdlib"
    }
}

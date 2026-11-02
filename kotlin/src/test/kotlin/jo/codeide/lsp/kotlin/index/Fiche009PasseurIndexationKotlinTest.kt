package jo.codeide.lsp.kotlin.index

import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.EtatEntreeTravailKotlin
import jo.codeide.lsp.kotlin.api.EvenementIndexationKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.PhaseDetailleeIndexationKotlin
import jo.codeide.lsp.kotlin.api.PhaseIndexationKotlin
import jo.codeide.lsp.kotlin.session.ScanProjetParesseux
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 009 — PasseurIndexationKotlinTest : tests du PILOTE de passages
 * d'indexation (ADR 0211/0213/0214 — demandes D1/D3/D4/D5 de CodeIDE) :
 * SÉQUENCE d'événements (bibliothèques puis sources puis PRET/PARTIEL),
 * ANNULATION coopérative (PARTIEL « annulé par l'utilisateur », l'acquis
 * est publié), REPRISE ciblée (les entrées saines deviennent REPRIS du
 * cache, seuls les échecs sont re-payés) et no-op chaud des syncs
 * redondantes.
 *
 * Le stdlib RÉEL du classpath de test sert d'entrée froide (copie sous
 * empreinte de test — le jar original n'est JAMAIS touché) ; les
 * attentes sont des SONDAGES bornés (le passeur n'expose volontairement
 * aucune primitive d'attente publique).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/).
 */
class Fiche009PasseurIndexationKotlinTest {

    /** La racine temporaire (copies isolées et dossiers d'index). */
    @TempDir
    lateinit var dossier: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): File =
        File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une COPIE ISOLÉE du stdlib sous [empreinte] (jamais l'original). */
    private fun copieStdlib(empreinte: String): EntreeClasspathKotlin {
        val copie = dossier.resolve("stdlib-$empreinte.jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        return EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, empreinte)
    }

    /** Une entrée ABSENTE (écart honnête — PARTIEL). */
    private fun entreeAbsente(empreinte: String): EntreeClasspathKotlin =
        EntreeClasspathKotlin(KindEntreeKotlin.JAR, "/inexistant-$empreinte.jar", empreinte)

    /** Un classpath de test (dossier d'index isolé par appel). */
    private fun classpath(
        entrees: List<EntreeClasspathKotlin>,
        racines: List<String> = emptyList(),
    ): ClasspathKotlin =
        ClasspathKotlin(
            entrees = entrees,
            racinesSources = racines,
            dossierIndex = dossier.resolve("idx-${System.nanoTime()}").toFile().apply { mkdirs() }.path,
        )

    /** Un passeur sur index + scan NEUFS (chaque test vit sa propre vie). */
    private fun passeurNeuf(): PasseurIndexationKotlin =
        PasseurIndexationKotlin(IndexAppelablesKotlin(), ScanProjetParesseux())

    /**
     * Le collecteur d'événements : accumulation thread-safe + attente
     * BORNÉE de l'événement final (PRET ou PARTIEL).
     */
    private class Collecteur {
        val evenements = CopyOnWriteArrayList<EvenementIndexationKotlin>()

        fun reinitialiser() = evenements.clear()

        fun attendreFinal(
            secondes: Long = 90,
        ): EvenementIndexationKotlin {
            val limite = System.currentTimeMillis() + secondes * 1000
            while (System.currentTimeMillis() < limite) {
                evenements
                    .lastOrNull {
                        it.phase == PhaseIndexationKotlin.PRET || it.phase == PhaseIndexationKotlin.PARTIEL
                    }?.let { return it }
                Thread.sleep(10)
            }
            throw AssertionError("aucun événement final (${evenements.size} événements)")
        }
    }

    @Test
    fun `sequence---bibliotheques-puis-sources-puis-PARTIEL-avec-ecart`() {
        val passeur = passeurNeuf()
        val collecteur = Collecteur()
        val poignee = passeur.enregistrer { collecteur.evenements.add(it) }
        try {
            val sain = copieStdlib("test:seq-sain")
            val absent = entreeAbsente("test:seq-absent")
            passeur.definirClasspath(classpath(listOf(sain, absent)))

            val final = collecteur.attendreFinal()
            assertEquals(PhaseIndexationKotlin.PARTIEL, final.phase)
            assertEquals(2, final.total)
            assertEquals(2, final.faits)
            // L'écart est ECARTE et signé dans les avertissements.
            assertEquals(
                EtatEntreeTravailKotlin.ECARTE,
                final.listeTravail.first { it.nom == absent.chemin }.etat,
            )
            assertTrue(final.avertissements.any { it.contains(absent.chemin) })
            assertTrue(final.settled, "l'écart n'est pas une annulation : le passage est settled")
        } finally {
            poignee.close()
        }
    }

    @Test
    fun `sequence-complete---liste-de-travail,-breakdown-et-bilan-paritaires`() {
        val passeur = passeurNeuf()
        val collecteur = Collecteur()
        val poignee = passeur.enregistrer { collecteur.evenements.add(it) }
        try {
            val sain = copieStdlib("test:riche-sain")
            passeur.definirClasspath(classpath(listOf(sain)))
            val final = collecteur.attendreFinal()

            assertEquals(PhaseIndexationKotlin.PRET, final.phase)
            assertTrue(final.settled, "le passage abouti est settled")
            assertEquals(1, final.total)
            assertEquals(1, final.faits)
            assertEquals(
                EtatEntreeTravailKotlin.TERMINE,
                final.listeTravail.single().etat,
            )
            // ADR 0213 : breakdown à DEUX indexeurs (segments, scan).
            assertEquals(2, final.breakdownIndexeurs.size)
            assertTrue(final.breakdownIndexeurs.any { it.identifiant == "kidx-segments" })
            assertTrue(final.breakdownIndexeurs.any { it.identifiant == "scan-warm" })
            assertNotNull(final.dureesPhases)
            assertNotNull(final.bilanDernierPassage)
            assertEquals(1, final.bilanDernierPassage!!.artefactsTotal)
            // La SÉQUENCE : au moins un événement BIBLIOTHEQUES puis un
            // SOURCES avant le final.
            val phases = collecteur.evenements.map { it.phaseDetaillee }
            assertTrue(
                phases.indexOf(PhaseDetailleeIndexationKotlin.BIBLIOTHEQUES) >= 0 &&
                    phases.indexOf(PhaseDetailleeIndexationKotlin.SOURCES) >
                    phases.indexOf(PhaseDetailleeIndexationKotlin.BIBLIOTHEQUES),
                "BIBLIOTHEQUES précède SOURCES",
            )
        } finally {
            poignee.close()
        }
    }

    @Test
    fun `sync-redondante---tout-est-REPRIS,-aucune-relecture`() {
        val passeur = passeurNeuf()
        val collecteur = Collecteur()
        val poignee = passeur.enregistrer { collecteur.evenements.add(it) }
        try {
            val entrees = listOf(copieStdlib("test:redondant"))
            val cp = classpath(entrees)
            passeur.definirClasspath(cp)
            val premier = collecteur.attendreFinal()
            assertEquals(PhaseIndexationKotlin.PRET, premier.phase)
            assertEquals(
                EtatEntreeTravailKotlin.TERMINE,
                premier.listeTravail.single().etat,
                "premier passage : décodage à froid",
            )
            // Sync REDONDANTE (classpath identique) : le snapshot couvre,
            // tout est REPRIS — le cache mémoire rend la reprise gratuite.
            collecteur.reinitialiser()
            passeur.definirClasspath(cp)
            val second = collecteur.attendreFinal()
            assertEquals(PhaseIndexationKotlin.PRET, second.phase)
            assertEquals(
                EtatEntreeTravailKotlin.REPRIS,
                second.listeTravail.single().etat,
            )
            assertEquals(1, second.reprises)
        } finally {
            poignee.close()
        }
    }

    @Test
    fun `annulation---PARTIEL-annule-par-l-utilisateur,-l-acquis-est-publie`() {
        val passeur = passeurNeuf()
        val collecteur = Collecteur()
        var annuleDemande = false
        val poignee =
            passeur.enregistrer { evenement ->
                collecteur.evenements.add(evenement)
                // Annulation déclenchée DEPUIS l'observateur dès la
                // première entrée traitée : déterministe (le drapeau est
                // posé avant le point coopératif suivant).
                val enBibliotheques =
                    evenement.phase == PhaseIndexationKotlin.CONSTRUCTION &&
                        evenement.phaseDetaillee == PhaseDetailleeIndexationKotlin.BIBLIOTHEQUES
                if (!annuleDemande && enBibliotheques && evenement.faits >= 1) {
                    annuleDemande = true
                    passeur.annulerPassage()
                }
            }
        try {
            passeur.definirClasspath(
                classpath(
                    listOf(
                        copieStdlib("test:annul-1"),
                        copieStdlib("test:annul-2"),
                        copieStdlib("test:annul-3"),
                    ),
                ),
            )
            val final = collecteur.attendreFinal()
            assertEquals(PhaseIndexationKotlin.PARTIEL, final.phase)
            assertFalse(final.settled, "un passage annulé n'est pas settled")
            assertTrue(
                final.avertissements.any { it.contains("annulé par l'utilisateur") },
                "l'avertissement d'annulation est publié",
            )
            // L'ACQUIS est publié : la première entrée est traitée, les
            // suivantes restent EN_ATTENTE (pendances de la reprise).
            assertEquals(
                EtatEntreeTravailKotlin.TERMINE,
                final.listeTravail.first { it.nom.contains("annul-1") }.etat,
            )
            assertTrue(
                final.listeTravail
                    .filter { it.nom.contains("annul-2") || it.nom.contains("annul-3") }
                    .all { it.etat == EtatEntreeTravailKotlin.EN_ATTENTE },
                "les suivantes pendent",
            )
            // La REPRISE complète le passage : l'acquis est REPRIS du
            // cache, les pendances aboutissent.
            collecteur.reinitialiser()
            assertTrue(passeur.reesayerEchecs(), "des pendances existent")
            val reprise = collecteur.attendreFinal()
            assertEquals(PhaseIndexationKotlin.PRET, reprise.phase)
            assertTrue(reprise.settled)
            assertEquals(
                EtatEntreeTravailKotlin.REPRIS,
                reprise.listeTravail.first { it.nom.contains("annul-1") }.etat,
            )
            assertTrue(
                reprise.listeTravail.all { it.etat != EtatEntreeTravailKotlin.EN_ATTENTE },
            )
        } finally {
            poignee.close()
        }
    }

    @Test
    fun `echec-isole---reesayerEchecs-retente-l-ecart-et-le-guerit`() {
        val passeur = passeurNeuf()
        val collecteur = Collecteur()
        val poignee = passeur.enregistrer { collecteur.evenements.add(it) }
        try {
            val sain = copieStdlib("test:echec-sain")
            // L'entrée échouée : un jar PUIS un jar SAIN (le disque est
            // réparé entre les deux passages — la reprise le voit).
            val fichierEnPanne = dossier.resolve("en-panne.jar").toFile()
            fichierEnPanne.writeBytes(byteArrayOf(1, 2, 3)) // pas un jar
            val enPanne =
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, fichierEnPanne.path, "test:echec-panne")
            passeur.definirClasspath(classpath(listOf(sain, enPanne)))
            val premier = collecteur.attendreFinal()
            assertEquals(PhaseIndexationKotlin.PARTIEL, premier.phase)
            assertEquals(
                EtatEntreeTravailKotlin.ECARTE,
                premier.listeTravail.first { it.nom == enPanne.chemin }.etat,
            )

            // RÉPARATION du disque + reprise ciblée : l'écart seul est
            // re-payé (le sain devient REPRIS du cache).
            fichierEnPanne.writeBytes(jarStdlib().readBytes())
            collecteur.reinitialiser()
            assertTrue(passeur.reesayerEchecs(), "un écart existe")
            val reprise = collecteur.attendreFinal()
            assertEquals(PhaseIndexationKotlin.PRET, reprise.phase)
            assertEquals(
                EtatEntreeTravailKotlin.REPRIS,
                reprise.listeTravail.first { it.nom == sain.chemin }.etat,
            )
            assertEquals(
                EtatEntreeTravailKotlin.TERMINE,
                reprise.listeTravail.first { it.nom == enPanne.chemin }.etat,
            )
            assertTrue(reprise.avertissements.none { it.contains(enPanne.chemin) })

            // Plus RIEN à retenter : la demande suivante refuse.
            assertFalse(passeur.reesayerEchecs())
        } finally {
            poignee.close()
        }
    }

    @Test
    fun `hors-vol---annulerPassage-et-reesayerEchecs-refusent-honnetement`() {
        val passeur = passeurNeuf()
        assertFalse(passeur.annulerPassage(), "rien en vol")
        assertFalse(passeur.reesayerEchecs(), "aucun passage passé")
        // Après un passage PRET sans écart : toujours rien à retenter.
        val collecteur = Collecteur()
        val poignee = passeur.enregistrer { collecteur.evenements.add(it) }
        try {
            passeur.definirClasspath(classpath(listOf(copieStdlib("test:hors-vol"))))
            collecteur.attendreFinal()
            assertFalse(passeur.reesayerEchecs())
            assertFalse(passeur.annulerPassage())
        } finally {
            poignee.close()
        }
    }
}

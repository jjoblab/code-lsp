package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 041 — ScanProjetTest : tests du scan des RACINES SOURCES (L1 —
 * KP6 du plan de stabilisation) sur la couture interne du module (le
 * scanneur paresseux de la vague 1c, câblé main — miroir de la méthode de
 * la fiche 042 : le contrat de SESSION se teste par le SPI, le MÉCANISME
 * de scan se teste ici).
 *
 * Comportements éprouvés : le contrat fonctionnel (scan paresseux des
 * `.kt`, exclusion du document courant, invalidation au changement de
 * racines, amorce warm à la définition, incrémental mtime, rafraîchissement
 * externe L1) et les accès CONCURRENTS — plusieurs lecteurs simultanés
 * doivent voir un état cohérent : la paire (racines, snapshot servi) est
 * mutée sous le verrou du scan, la visibilité mémoire est garantie par les
 * champs volatils. La MARCHE précède le PARSE (progression à total connu),
 * l'annulation est COOPÉRATIVE (l'acquis publié, le passage partiel abouti,
 * la reprise le complète).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier). Les fixtures forcent le mtime (+60 s) quand un
 * contenu change : la granularité du système de fichiers masquerait un
 * mtime trop proche (l'échec du forçage serait un refus du FS, pas un
 * silence du scan — l'assertion l'éprouve).
 */
class Fiche041ScanProjetParesseuxTest {

    /** La racine temporaire des sources du scan. */
    @TempDir
    lateinit var dossier: Path

    /** Le scanneur éprouvé (un NEUF par test — états indépendants). */
    private val scan = ScanProjetParesseux()

    /**
     * Une racine de N fichiers d'une déclaration chacun (noms distincts).
     *
     * @param nombre le nombre de fichiers à semer ;
     * @param nom le nom du dossier racine ;
     * @return la racine créée (chemin absolu).
     */
    private fun racineAvecFichiers(nombre: Int, nom: String = "src"): Path {
        val racine = dossier.resolve(nom)
        Files.createDirectories(racine)
        (1..nombre).forEach { indice ->
            Files.writeString(racine.resolve("F$indice.kt"), "val f$indice = $indice\n")
        }
        return racine
    }

    /**
     * Réécrit un fichier puis FORCE son mtime dans le futur (le contenu
     * change ET la date — le cache incrémental ne peut pas les confondre).
     *
     * @param racine la racine du fichier ;
     * @param nom le nom du fichier ;
     * @param texte le nouveau contenu complet.
     */
    private fun reecrireEnForcantMtime(racine: Path, nom: String, texte: String) {
        val fichier = racine.resolve(nom)
        Files.writeString(fichier, texte)
        val accepte = fichier.toFile().setLastModified(System.currentTimeMillis() + 60_000L)
        assertTrue(accepte, "mtime forcé accepté par le FS (refus = échec du FS, pas du scan)")
    }

    // ------------------------------------------------------------------
    // Contrat fonctionnel du scan paresseux.
    // ------------------------------------------------------------------

    @Test
    fun `sans-racines---aucune-declaration`() {
        assertTrue(scan.declarations().isEmpty(), "aucune racine posée : aucune déclaration")
    }

    @Test
    fun `scan-paresseux---les-kt-des-racines-nourrissent-le-scan`() {
        val racine = racineAvecFichiers(2)
        scan.definirRacines(listOf(racine.toString()))
        val declarations = scan.declarations()
        assertEquals(2, declarations.size, "les deux fichiers nourrissent le scan")
        // Le contrat d'URI est le SCHÉMA `file:` (le chemin suit).
        assertTrue(declarations.all { it.uri.startsWith("file:") }, "URI de fichier")
    }

    @Test
    fun `document-courant-exclu---son-doublon-du-scan-ne-revient-pas`() {
        val racine = racineAvecFichiers(2)
        scan.definirRacines(listOf(racine.toString()))
        val uriExclue = scan.declarations().first().uri
        val sansDoublon = scan.declarations(uriExclue)
        assertEquals(1, sansDoublon.size, "le document courant exclu ne revient pas")
        assertTrue(sansDoublon.none { it.uri == uriExclue }, "aucun doublon du document courant")
    }

    @Test
    fun `changement-de-racines---invalidation-puis-re-scan`() {
        val premiere = racineAvecFichiers(1, nom = "src1")
        scan.definirRacines(listOf(premiere.toString()))
        assertEquals(1, scan.declarations().size, "première racine : une déclaration")
        val seconde = racineAvecFichiers(2, nom = "src2")
        scan.definirRacines(listOf(seconde.toString()))
        assertEquals(2, scan.declarations().size, "changement : invalidation puis re-scan complet")
    }

    @Test
    fun `racines-identiques---no-op-chaud,-meme-resultat`() {
        val racine = racineAvecFichiers(2)
        scan.definirRacines(listOf(racine.toString()))
        assertEquals(2, scan.declarations().size)
        // Synchronisation redondante : le contrat reste le même (pas d'état vide).
        scan.definirRacines(listOf(racine.toString()))
        assertEquals(2, scan.declarations().size, "no-op chaud : le même résultat demeure servi")
    }

    @Test
    fun `amorce-warm---declarations-joint-le-scan-lancé-à-la-définition`() {
        // La définition AMORCE la passe sur le fil dédié : une lecture
        // immédiate S'Y JOINT et voit le résultat COMPLET (jamais de double
        // scan) — le coût part à la définition, pas à la première frappe.
        val racine = racineAvecFichiers(2)
        scan.definirRacines(listOf(racine.toString()))
        assertEquals(2, scan.declarations().size, "la lecture rejoint la passe amorcée à la définition")
        // Re-définition CHANGÉE puis lecture immédiate : le même chemin.
        val autre = racineAvecFichiers(1, nom = "autre")
        scan.definirRacines(listOf(autre.toString()))
        assertEquals(1, scan.declarations().size, "changement + lecture immédiate : jointure encore")
    }

    @Test
    fun `incremental-mtime---le-fichier-changé-re-parse,-les-autres-survivent`() {
        val racine = racineAvecFichiers(2)
        scan.definirRacines(listOf(racine.toString()))
        assertEquals(2, scan.declarations().size)
        // F2.kt change de contenu ET de mtime ; une racine RÉELLEMENT
        // différente déclenche la passe incrémentale (le même contenu serait
        // un no-op chaud par contrat).
        reecrireEnForcantMtime(racine, "F2.kt", "val fDeux = 22\nval fTrois = 23\n")
        val racineAjoutee = racineAvecFichiers(0, nom = "racine-ajoutee")
        scan.definirRacines(listOf(racine.toString(), racineAjoutee.toString()))
        val noms = scan.declarations().map { it.nom }
        assertTrue("fDeux" in noms, "les nouvelles déclarations du fichier changé apparaissent")
        assertTrue("f1" in noms, "les déclarations du fichier inchangé survivent (cache mtime)")
        assertTrue("f2" !in noms, "l'ancienne déclaration du fichier changé part")
    }

    // ------------------------------------------------------------------
    // Rafraîchissement externe (lot L1 — événement de fichier hors éditeur).
    // ------------------------------------------------------------------

    @Test
    fun `rafraichissement-externe---un-fichier-cree-devient-visible-(L1)`() {
        val racine = racineAvecFichiers(2)
        scan.definirRacines(listOf(racine.toString()))
        assertEquals(2, scan.declarations().size)
        Files.writeString(racine.resolve("F3.kt"), "val f3 = 3\n")
        scan.rafraichir()
        val noms = scan.declarations().map { it.nom }
        assertTrue("f3" in noms, "le fichier créé devient visible après rafraîchissement externe")
        assertTrue("f1" in noms && "f2" in noms, "les déclarations antérieures survivent (cache mtime)")
    }

    @Test
    fun `rafraichissement-externe---un-fichier-modifie-remplace-ses-declarations-(L1)`() {
        val racine = racineAvecFichiers(2)
        scan.definirRacines(listOf(racine.toString()))
        assertEquals(2, scan.declarations().size)
        reecrireEnForcantMtime(racine, "F2.kt", "val fDeux = 22\n")
        scan.rafraichir()
        val noms = scan.declarations().map { it.nom }
        assertTrue("fDeux" in noms, "la nouvelle déclaration du fichier modifié est visible")
        assertTrue("f2" !in noms, "l'ancienne déclaration du fichier modifié part")
    }

    @Test
    fun `sans-racines---rafraichir-est-un-no-op-(L1)`() {
        scan.rafraichir()
        assertTrue(scan.declarations().isEmpty(), "sans racines : rafraîchir ne fait rien")
    }

    // ------------------------------------------------------------------
    // Accès concurrents (KP6).
    // ------------------------------------------------------------------

    @Test
    fun `acces-concurrents---chaque-session-voit-un-etat-coherent-(KP6)`() {
        val racine = racineAvecFichiers(2)
        scan.definirRacines(listOf(racine.toString()))
        val depart = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(NB_FILS)
        val erreurs = mutableListOf<Throwable>()
        try {
            repeat(NB_FILS) { index ->
                pool.submit {
                    depart.await()
                    repeat(ITERATIONS) {
                        try {
                            // Le fil 0 re-pose les MÊMES racines (no-op chaud)
                            // pendant que les autres lisent : le verrou du scan
                            // sérialise, le résultat reste total.
                            if (index == 0) scan.definirRacines(listOf(racine.toString()))
                            assertEquals(2, scan.declarations().size, "chaque lecteur voit l'état total")
                        } catch (lancee: Throwable) {
                            synchronized(erreurs) { erreurs.add(lancee) }
                        }
                    }
                }
            }
            depart.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(DELAI_SECONDES, TimeUnit.SECONDS), "aucun fil ne se bloque")
            assertEquals(emptyList<Throwable>(), erreurs, "aucun fil ne doit lever")
        } finally {
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------
    // Progression, annulation coopérative, reprise (demandes D1/D3).
    // ------------------------------------------------------------------

    @Test
    fun `progression---chaque-fichier-notifie,-le-total-est-connu-(marche-d-abord)`() {
        val racine = racineAvecFichiers(5, nom = "src-progression")
        val notifications = mutableListOf<Pair<Int, Int>>()
        scan.definirObservateur { faits, total, _ ->
            synchronized(notifications) { notifications.add(faits to total) }
            true
        }
        scan.definirRacines(listOf(racine.toString()))
        scan.attendrePassageCourant()
        scan.definirObservateur(null)
        assertTrue(notifications.size >= 5, "chaque fichier traité notifie")
        // La MARCHE précède le PARSE : le total est connu dès le PREMIER tick
        // (vraie fraction faits/total, pas un total inconnu).
        assertEquals(5, notifications.first().second, "total connu dès le premier tick")
        assertEquals(5, notifications.last().first, "le dernier tick porte les cinq fichiers faits")
        assertEquals(5, scan.declarations().size, "le passage complet publie les cinq déclarations")
    }

    @Test
    fun `annulation---l-acquis-est-publie,-le-passage-est-abouti-meme-en-partiel`() {
        val racine = racineAvecFichiers(4, nom = "src-annulation")
        scan.definirObservateur { _, _, _ -> false } // annulation après le premier fichier
        scan.definirRacines(listOf(racine.toString()))
        scan.attendrePassageCourant()
        scan.definirObservateur(null)
        // L'ACQUIS (le premier fichier traité) est publié ; le PARTIEL annulé
        // est un état LÉGITIME abouti (pas de ré-amorce en boucle).
        assertEquals(1, scan.declarations().size, "l'acquis du passage annulé est publié")
        assertEquals(1, scan.statsDernierPassage().faits, "un seul fichier traité avant l'annulation")
        assertEquals(4, scan.statsDernierPassage().total, "le total réel demeure honnête")
    }

    @Test
    fun `reprise-apres-annulation---rafraichir-complete-le-passage`() {
        val racine = racineAvecFichiers(3, nom = "src-reprise")
        scan.definirObservateur { _, _, _ -> false }
        scan.definirRacines(listOf(racine.toString()))
        scan.attendrePassageCourant()
        scan.definirObservateur(null)
        assertEquals(1, scan.declarations().size, "l'acquis partiel avant la reprise")
        // La REPRISE : le cache mtime épargne l'acquis, le rafraîchissement
        // complète le reste du passage.
        scan.rafraichir()
        scan.attendrePassageCourant()
        assertEquals(3, scan.declarations().size, "le rafraîchissement complète le passage")
    }

    @Test
    fun `projet-sans-sources---passage-abouti,-pas-de-boucle-d-amorcage`() {
        val vide = dossier.resolve("src-vide")
        Files.createDirectories(vide)
        scan.definirRacines(listOf(vide.toString()))
        scan.attendrePassageCourant()
        assertTrue(scan.declarations().isEmpty(), "un projet sans .kt est un état légitime")
        // Le drapeau d'aboutissement ferme la boucle : attendre retourne
        // immédiatement, aucune ré-amorce perpétuelle.
        scan.attendrePassageCourant()
        assertTrue(scan.declarations().isEmpty(), "le passage abouti (vide) ne ré-amorce pas")
    }

    private companion object {

        /** Fils concurrents (transitions d'onglets simulées). */
        private const val NB_FILS: Int = 4

        /** Itérations par fil (martèlement court, déterministe). */
        private const val ITERATIONS: Int = 50

        /** Budget d'attente du pool, en secondes. */
        private const val DELAI_SECONDES: Long = 30L
    }
}

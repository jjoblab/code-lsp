package jo.codeide.lsp.java

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Vague V-F2 — VERROUS PAR PÉRIMÈTRE (ADR 0029 phase 2, exécution de l'ADR
 * 0010 schéma B, écart G-029) : le moniteur d'instance du moteur est
 * remplacé par des verrous nommés (compilation / services / feuilles),
 * avec un ordre global documenté.
 *
 * Invariants éprouvés ici :
 *  - **métrique CI** : le comptage `synchronized(` dans `java/src/main`
 *    est < 6 (critère ADR 0010 — il vaut 0 depuis V-F2) ;
 *  - **les chemins sans ecj ne prennent pas le verrou de compilation** :
 *    mémo de diagnose, sondes et compteurs répondent PENDANT qu'un fil
 *    détient délibérément le verrou (test boîte blanche — le banc de
 *    charge prouve le même gain en comportement) ;
 *  - **le verrou de compilation est bien réel** : une diagnose de source
 *    fraîche (mémo manquant) BLOQUE tant que le verrou est détenu, puis
 *    se solde après libération — la sémantique de drainage est préservée.
 */
class VerrousPerimetreTest {

    /** Localise la racine des sources du module java (métrique CI). */
    private fun racineSources(): Path? {
        val candidats = listOf(Path.of("src/main"), Path.of("java/src/main"), Path.of("../java/src/main"))
        return candidats.firstOrNull { Files.isDirectory(it) }
    }

    @Test
    fun `le comptage synchronized du module java est inferieur a six`() {
        val racine = racineSources()
        assertNotNull(racine, "racine src/main du module java introuvable (CWD = ${Path.of("").toAbsolutePath()})")
        var total = 0
        Files.walk(racine).use { flux ->
            flux.filter { it.toString().endsWith(".kt") }.forEach { fichier ->
                val occurrences = fichier.toFile().readText().split("synchronized(").size - 1
                if (occurrences > 0) {
                    println("  ${racine.relativize(fichier)} : $occurrences")
                }
                total += occurrences
            }
        }
        println("Comptage synchronized( dans src/main : $total (cible ADR 0010 : < 6)")
        assertTrue(total < 6, "comptage synchronized( = $total — la cible ADR 0010 est < 6")
    }

    @Test
    fun `memo, sondes et actions texte repondent pendant que le verrou de compilation est detenu`() {
        val racine = Files.createTempDirectory("vf2-sondes")
        Files.createDirectories(racine.resolve("demo")).resolve("A.java")
            .writeText("package demo;\npublic class A { public int unChamp = 1; }\n")
        val moteur = MoteurJava(racines = listOf(racine))
        val chemin = "/demo/A.java"
        val source = "package demo;\npublic class A { public int unChamp = 1; }\n"

        // Chauffe : la première diagnose construit l'environnement et remplit
        // le mémo (elle prend le verrou elle-même — aucun fil ne le détient) ;
        // la complétion est chauffée de la même façon (sonde à chaud).
        assertTrue(
            moteur.diagnose(chemin, source).none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR },
            "fixture saine : 0 erreur",
        )
        moteur.completer(chemin, source, source.indexOf("unChamp"))

        // Le verrou de compilation, saisi PAR LE TEST (boîte blanche) : un
        // fil hostile détient le périmètre MODULE comme le ferait une longue
        // résolution d'un AUTRE fichier.
        val champ = MoteurJava::class.java.getDeclaredField("verrouCompilation")
        champ.isAccessible = true
        val verrou = champ.get(moteur) as ReentrantLock
        verrou.lock()
        try {
            // (1) Les chemins SANS périmètre compilation répondent (bornés).
            val fait = CountDownLatch(4)
            val reussites = java.util.concurrent.atomic.AtomicInteger(0)
            Thread {
                // Mémo de diagnose (frappe identique — le chemin chaud).
                if (moteur.diagnose(chemin, source).isEmpty()) reussites.incrementAndGet()
                fait.countDown()
            }.start()
            Thread {
                // Sondes observables.
                if (moteur.parseCacheSize() >= 0 && moteur.fullDiagnoseCount >= 0 &&
                    moteur.fastDiagnoseHits >= 1 && moteur.fqcnPour(racine.resolve("demo/A.java")) == "demo.A"
                ) {
                    reussites.incrementAndGet()
                }
                fait.countDown()
            }.start()
            Thread {
                // Actions de code (service TEXTE pur — sans moteur).
                val actions = moteur.actionsCode(chemin, source, emptyList())
                if (actions.isEmpty()) reussites.incrementAndGet() // liste vide honnête, sans bloquer
                fait.countDown()
            }.start()
            Thread {
                // FQCN + complétion de tête (prologue bref, corps sans verrou
                // sur chemin chaud — la réponse est attendue endéans la borne).
                val reponse = moteur.completer(chemin, source, source.indexOf("unChamp"))
                if (reponse.totalDisponible >= 0) reussites.incrementAndGet()
                fait.countDown()
            }.start()
            assertTrue(
                fait.await(5, TimeUnit.SECONDS),
                "les chemins sans ecj doivent répondre pendant que le verrou de compilation est détenu",
            )
            assertEquals(4, reussites.get(), "les 4 familles de chemins sans ecj ont répondu")

            // (2) Le verrou est BIEN réel : une diagnose de source FRAÎCHE
            // (mémo manquant → diagnose complet → ecj) bloque jusqu'à
            // libération — la sémantique « une résolution à la fois » et la
            // drainage de dispose sont préservées.
            val sourceFraiche = source.replace("unChamp = 1", "unChamp = 2")
            val terminee = java.util.concurrent.atomic.AtomicBoolean(false)
            val bloquante = Thread { moteur.diagnose(chemin, sourceFraiche); terminee.set(true) }
            bloquante.isDaemon = true
            bloquante.start()
            Thread.sleep(400)
            assertTrue(!terminee.get(), "la diagnose fraîche doit attendre le verrou de compilation")
        } finally {
            verrou.unlock()
        }
        // Libéré : la diagnose bloquée se solde (drainage).
        val echeance = System.nanoTime() + 10_000_000_000L
        while (moteur.fullDiagnoseCount < 2 && System.nanoTime() < echeance) {
            Thread.sleep(20)
        }
        assertTrue(moteur.fullDiagnoseCount >= 2, "la diagnose fraîche s'est soldée après libération")
    }
}

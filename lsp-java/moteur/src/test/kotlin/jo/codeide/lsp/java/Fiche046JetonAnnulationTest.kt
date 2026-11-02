package jo.codeide.lsp.java

import jo.codeide.lsp.java.concurrence.JetonsAnnulation
import jo.codeide.lsp.java.concurrence.JetonAnnulation
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fiche 046 — CancelTokenTest : jeton d'annulation coopératif — singleton
 * NEVER, token manuel (idempotent), token à échéance, interruption des
 * threads attachés, close sans annulation.
 */
class Fiche046JetonAnnulationTest {

    @Test
    fun neverTokenIsNeverCancelled() {
        assertFalse(JetonAnnulation.NEVER.isCancelled(), "NEVER : jamais annulable")
        JetonAnnulation.NEVER.annuler()
        assertFalse(JetonAnnulation.NEVER.isCancelled(), "NEVER : même après annuler()")
    }

    @Test
    fun manualTokenStartsNotCancelled() {
        val jeton = JetonsAnnulation.manual()
        assertFalse(jeton.isCancelled(), "frais : pas annulé")
        jeton.annuler()
        assertTrue(jeton.isCancelled(), "après annuler() : annulé")
        assertTrue(jeton.isCancelled(), "second contrôle : idempotent")
    }

    @Test
    fun timeoutTokenFiresAfterDeadline() {
        JetonsAnnulation.timeout(50, TimeUnit.MILLISECONDS).use { jeton ->
            assertFalse(jeton.isCancelled(), "frais : pas annulé")
            Thread.sleep(120)
            assertTrue(jeton.isCancelled(), "après un sommeil supérieur au délai : annulé")
        }
    }

    @Test
    fun cancelInterruptsAttachedThread() {
        val jeton = JetonsAnnulation.manual()
        val travaille = arrayOf(true)
        val worker = Thread {
            // L'attachement précède le signal de prêt : l'annulation ne peut
            // plus passer AVANT l'attachement (sinon l'interruption serait
            // perdue et le worker dormirait 5 s entières).
            jeton.attacherAuThreadCourant()
            travaille[0] = true
            try {
                Thread.sleep(5_000)
            } catch (_: InterruptedException) {
                // Le sommeil du worker est INTERROMPU par l'annulation.
            }
        }
        worker.start()
        while (!travaille[0]) {
            Thread.sleep(10)
        }
        Thread.sleep(100)
        // Attache AUSSI le thread principal : l'interruption est diffuse.
        jeton.attacherAuThreadCourant()
        jeton.annuler()
        // Le flag d'interruption DU THREAD PRINCIPAL (attaché) fait échouer
        // join immédiatement : on nettoie et on RETENTE jusqu'à épuisement
        // du délai (le worker a besoin d'un instant pour s'arrêter).
        val echeance = System.currentTimeMillis() + 2_000
        while (worker.isAlive && System.currentTimeMillis() < echeance) {
            try {
                worker.join(200)
            } catch (_: InterruptedException) {
                Thread.interrupted() // nettoie le flag du principal
            }
        }
        assertFalse(worker.isAlive, "join réussi en 500 ms (worker non vivant)")
        assertTrue(jeton.isCancelled())
    }

    @Test
    fun closeClearsWorkerReference() {
        val jeton = JetonsAnnulation.manual()
        jeton.attacherAuThreadCourant()
        jeton.close()
        assertFalse(jeton.isCancelled(), "close ne déclenche PAS l'annulation")
    }
}

// CodeIDE — banc de cohérence P8 : cohérence inter-fichiers (module :kotlin,
// vague 0). Code NEUF jjoblab (banc de MESURE).
package jo.codeide.lsp.kotlin.coherence

import java.nio.file.Files
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * P8 — Cohérence inter-fichiers (banc de cohérence, vague 0 — miroir de la
 * P8 Java : éditer l'API de A, les diagnostics de B doivent suivre).
 *
 * ## Ce que la propriété mesure
 *
 * Deux fichiers d'un univers FERMÉ (répertoire temporaire, stdlib seule) :
 * A déclare `saluer()`, B l'appelle. Séquence :
 *
 * 1. B est diagnostiqué SAIN (l'usage résout — A est présent) ;
 * 2. A est MODIFIÉ (la fonction disparaît), les sources sont rafraîchies,
 *    B est re-touché (re-diagnostic forcé) ;
 * 3. les diagnostics de B doivent ÊTRE RECALCULÉS : dans un univers fermé
 *    l'absence de `saluer` est PROUVÉE — l'usage doit devenir
 *    `kt.unresolved` (SIGNALABLE). S'il reste silencieux, c'est un faux
 *    négatif de fraîcheur inter-fichiers — mesuré, pas nié.
 *
 * ## Assertions vs mesures (R5)
 *
 * - **ASSERTÉ** (toujours) : jamais d'exception ; l'étape 1 rend B sain
 *   (sinon le montage est faux) ;
 * - **mesuré** (rapport ; strict à la demande) : l'étape 3 rapporte le
 *   verdict (recalculé SIGNALABLE vs silencieux périmé).
 */
class P8CohrenceInterFichiersTest {

    @Test
    fun `éditer-l'API-de-A-recalcule-les-diagnostics-de-B`() {
        val racine = Files.createTempDirectory("p8-interfichiers")
        val fichierA = racine.resolve("Api.kt")
        val fichierB = racine.resolve("Client.kt")
        val texteAInitial = "package p8\n\nfun saluer(): String = \"bonjour\"\n"
        val texteAMuté = "package p8\n\n// saluer a été retirée (sonde P8)\n"
        val texteB = "package p8\n\nfun accueil(): String = saluer() + \" !\"\n"
        Files.writeString(fichierA, texteAInitial)
        Files.writeString(fichierB, texteB)
        val jars = SocleCoherence.assurerPlancherStdlib(SocleCoherence.jarsAutoCorpus())
        val session = SocleCoherence.monterSession(listOf(racine), jars)
        try {
            session.rafraichirSources()
            val uriA = fichierA.toUri().toString()
            val uriB = fichierB.toUri().toString()

            // 1. B sain (A présent) — sanité du montage.
            session.ouvrir(uriB, texteB)
            val initial = session.diagnostics(uriB).filter { it.severite == SeveriteKotlin.ERREUR }
            assertTrue(
                initial.none { it.message.contains("saluer") },
                "le montage doit résoudre saluer() tant que A la déclare (reçu : $initial)",
            )
            println("P8 étape 1 (A présent): erreurs de B=${initial.size}")

            // 2. A mute (fonction retirée) + rafraîchissement + re-touch de B.
            Files.writeString(fichierA, texteAMuté)
            session.ouvrir(uriA, texteAMuté)
            session.rafraichirSources()
            session.modifier(uriB, texteB + " ")

            // 3. Verdict : recalculé SIGNALABLE vs silencieux périmé.
            val recalculés = session.diagnostics(uriB).filter { it.severite == SeveriteKotlin.ERREUR }
            val surSaluer = recalculés.filter { it.message.contains("saluer") }
            println(
                "P8 étape 3 (A muté): erreurs de B=${recalculés.size}, sur saluer=${surSaluer.size} " +
                    "→ ${if (surSaluer.isNotEmpty()) "RECALCULÉ (SIGNALABLE)" else "SILENCIEUX (faux négatif de fraîcheur — mesuré)"}",
            )
            if (SocleCoherence.STRICT) {
                assertTrue(
                    surSaluer.isNotEmpty(),
                    "P8 strict : l'absence de saluer est PROUVÉE dans un univers fermé — l'usage doit devenir kt.unresolved",
                )
            }
            session.fermer(uriA)
            session.fermer(uriB)
        } finally {
            session.close()
        }
    }
}

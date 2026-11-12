// CodeIDE — tests du MODE PROFOND K2 optionnel (module :kotlin, vague
// V-H — ADR 0124 option 2 : le processus déporté en germe ; ADR 0122 :
// la provenance `palier 3`). Code NEUF jjoblab (clean-room — le service
// est DÉSACTIVÉ par défaut : jamais sur le chemin de frappe, le repli
// palier 1/2 est immédiat ; l'oracle du banc (kotlin-compiler-embeddable
// en classpath de TEST SEULEMENT) sert la vérification).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La vague V-H — le mode profond K2 optionnel (ADR 0124 option 2) :
 * désactivé par défaut, activation par l'utilisateur, provenance
 * `palier 3`, repli immédiat, l'oracle du banc en service.
 */
class ModeProfondK2Test {

    /** La racine temporaire des fichiers de test. */
    @org.junit.jupiter.api.io.TempDir
    lateinit var racine: Path

    // ── Le drapeau : désactivé par défaut, repli immédiat ────────────

    @Test
    fun `drapeau---désactivé-par-défaut,-repli-palier-1-2-immédiat`() {
        val mode = ModeProfondK2()
        assertEquals(false, mode.estActif(), "désactivé par défaut (jamais sur le chemin de frappe)")
        // Éteint : erreursProfondes rend `null` (le palier 1/2 reprend).
        val fichier = racine.resolve("demo.kt").toFile()
        Files.writeString(fichier.toPath(), "fun demo() = inconnu()\n")
        assertNull(
            mode.erreursProfondes(fichier, emptyList()),
            "service éteint : `null` (repli palier 1/2 immédiat, CI inchangée)",
        )
    }

    @Test
    fun `drapeau---activation-par-l'utilisateur-puis-désactivation`() {
        val mode = ModeProfondK2()
        mode.activer()
        assertEquals(true, mode.estActif(), "l'activation est l'acte de l'utilisateur")
        mode.desactiver()
        assertEquals(false, mode.estActif(), "la désactivation est immédiate (repli)")
    }

    // ── L'oracle du banc en service (provenance palier 3) ────────────

    @Test
    fun `oracle---les-erreurs-du-compilateur-véritable-portent-la-provenance`() {
        // L'oracle exige kotlin-compiler-embeddable au classpath de TEST
        // (miroir du banc BancFideliteK2Test) : le sous-processus l'emprunte.
        val ancre = try {
            Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        if (!ancre) {
            // Le compilateur n'est pas au classpath (CI sans la dépendance
            // de test) : le service rend VIDE en repli honnête (D3).
            return
        }
        val mode = ModeProfondK2()
        mode.activer()
        // Un fichier avec une référence NON RÉSOLUE : l'oracle K2 la voit.
        val fichier = racine.resolve("profond.kt").toFile()
        Files.writeString(fichier.toPath(), "fun demo() = introuvable()\n")
        val erreurs = mode.erreursProfondes(fichier, emptyList())
        assertNotNull(erreurs, "service actif : la réponse existe (palier 3)")
        assertTrue(
            erreurs.any { it.ligne == 1 && "introuvable" in it.message },
            "l'oracle voit la référence introuvable : $erreurs",
        )
        // La PROVENANCE : chaque erreur porte « palier 3 » (ADR 0122 —
        // jamais une réponse du palier 3 ne se fait passer pour 1/2).
        assertTrue(
            erreurs.all { it.provenance == "palier 3" },
            "la provenance est étiquetée palier 3 : ${erreurs.map { it.provenance }}",
        )
        mode.desactiver()
    }

    @Test
    fun `oracle---un-fichier-sain-ne-produit-aucune-erreur`() {
        val ancre = try {
            Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        if (!ancre) return
        val mode = ModeProfondK2()
        mode.activer()
        val fichier = racine.resolve("sain.kt").toFile()
        Files.writeString(fichier.toPath(), "fun demo(): Int = 1\n")
        val erreurs = mode.erreursProfondes(fichier, emptyList())
        assertNotNull(erreurs, "service actif : la réponse existe")
        assertTrue(
            erreurs.none { it.ligne == 1 },
            "un fichier sain ne produit aucune erreur ligne 1 : $erreurs",
        )
        mode.desactiver()
    }

    // ── L'échec du processus déporté n'est jamais une erreur moteur ──

    @Test
    fun `tolérance---un-échec-du-processus-est-un-repli-silencieux`() {
        val ancre = try {
            Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        if (!ancre) return
        val mode = ModeProfondK2()
        mode.activer()
        // Un fichier ILLISIBLE (qui n'existe pas) : le processus déporté
        // échoue — le service rend VIDE (jamais d'exception vers
        // l'appelant, D3 : le palier 1/2 reprend).
        val erreurs = mode.erreursProfondes(racine.resolve("absent.kt").toFile(), emptyList())
        assertNotNull(erreurs, "la réponse existe (vide — repli honnête)")
        assertTrue(erreurs.isEmpty(), "un échec du processus déporté est un repli silencieux")
        mode.desactiver()
    }
}

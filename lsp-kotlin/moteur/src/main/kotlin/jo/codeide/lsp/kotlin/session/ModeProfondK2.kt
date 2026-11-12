// CodeIDE — le MODE PROFOND K2 optionnel, palier 3 (module :kotlin,
// vague V-H — ADR 0124 option 2 « processus déporté » + ADR 0122 : la
// provenance étiquetée `palier 3`). Code NEUF jjoblab (clean-room — le
// germe livrable de l'option 2 : l'ORACLE du banc de fidélité
// (BancFideliteK2Test — le compilateur K2 VÉRITABLE en sous-processus)
// devient un SERVICE OPTIONNEL derrière drapeau : DÉSACTIVÉ par défaut
// (le palier 2 reste le plafond de fidélité tant que le propriétaire
// n'arbitre pas après les mesures appareil moto g06 + la confirmation
// hôte Q-K7 — ADR 0124 §Décision). Jamais sur le chemin de frappe :
// le service ne se sollicite que par activation EXPLICITE de
// l'utilisateur, le délai est annoncé par la provenance, le repli
// palier 1/2 est immédiat (le service éteint rend `null`).
package jo.codeide.lsp.kotlin.session

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Un diagnostic PROFOND du palier 3 : une erreur du compilateur K2
 * VÉRITABLE, portée par sa ligne et son message — la provenance
 * `palier 3` voyage avec (ADR 0122 : jamais une réponse du palier 3
 * ne se fait passer pour une réponse du palier 1/2).
 *
 * @property ligne la ligne (1-based) de l'erreur ;
 * @property message le message du compilateur ;
 * @property provenance TOUJOURS "palier 3" (l'étiquette contractuelle).
 */
public class DiagnosticProfondKotlin internal constructor(
    public val ligne: Int,
    public val message: String,
    public val provenance: String = "palier 3",
)

/**
 * Le MODE PROFOND K2 optionnel (V-H — ADR 0124 option 2 : le
 * compilateur VÉRITABLE en PROCESSUS DÉPORTÉ, sollicité À LA DEMANDE).
 *
 * **Désactivé par défaut** — l'activation relève de l'UTILISATEUR
 * (jamais sur le chemin de frappe, ADR 0124 §Critères) ; le verdict
 * final (retenir/rejeter l'option 2/3) appartient au PROPRIÉTAIRE
 * après les mesures appareil (gabarit 07 sur moto g06) et la
 * confirmation hôte de l'environnement déporté (Q-K7) — ce service est
 * le GERME livrable : la même frontière que l'oracle du banc
 * (BancFideliteK2Test.oracleK2), transformée en service borné.
 *
 * Le protocole (miroir de l'oracle du banc) : `java -cp <classpath
 * test> org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp
 * <classpath oracle> -nowarn <fichier>` — le compilateur tourne HORS
 * processus (l'amorçage mesuré à ~3,5 s [S1, ADR 0124] : le délai est
 * annoncé, la réponse porte sa provenance).
 */
internal class ModeProfondK2 internal constructor() {

    /** Le drapeau d'activation (POSÉ PAR L'UTILISATEUR, jamais par le moteur). */
    @Volatile
    private var active: Boolean = false

    /**
     * ACTIVE le mode profond (l'acte de l'utilisateur — le moteur ne
     * l'active JAMAIS de lui-même : le chemin de frappe reste palier 1/2).
     */
    internal fun activer() {
        active = true
    }

    /**
     * DÉSACTIVE le mode profond (repli immédiat palier 1/2 — le service
     * éteint rend `null`, la CI est inchangée).
     */
    internal fun desactiver() {
        active = false
    }

    /** Le mode profond est-il ACTIF ? */
    internal fun estActif(): Boolean = active

    /**
     * Les ERREURS profondes d'un document : le compilateur K2 lancé en
     * SOUS-PROCESSUS sur le fichier écrit au disque.
     *
     * @param fichier le fichier Kotlin à compiler (le document doit être
     * matérialisé au disque — le compilateur lit un FICHIER) ;
     * @param jarsOracle le classpath de l'oracle (les jars dont le
     * document dépend) ;
     * @return les erreurs (ligne, message) — provenance `palier 3` ;
     * `null` si le mode est ÉTEINT (repli palier 1/2) ; liste VIDE si
     * le compilateur ne trouve rien (jamais d'exception vers l'appelant).
     */
    internal fun erreursProfondes(
        fichier: File,
        jarsOracle: List<File>,
    ): List<DiagnosticProfondKotlin>? {
        if (!active) return null
        return try {
            oracleK2(fichier, jarsOracle)
        } catch (_: Exception) {
            // Un échec du processus déporté N'EST JAMAIS une erreur du
            // moteur : repli silencieux (le palier 1/2 reprend, D3).
            emptyList()
        }
    }

    /**
     * L'oracle K2 (miroir exact du banc de fidélité
     * `BancFideliteK2Test.oracleK2` — la MÊME frontière, en service) :
     * le compilateur lancé en sous-processus, les erreurs
     * `unresolved reference` relevées.
     */
    private fun oracleK2(
        fichier: File,
        jars: List<File>,
    ): List<DiagnosticProfondKotlin> {
        val classpathTest = System.getProperty("java.class.path")
        val ancreStdlib = File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )
        val classpathOracle = (jars + ancreStdlib)
            .distinctBy { it.absolutePath }
            .joinToString(File.pathSeparator) { it.absolutePath }
        val processus = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-cp", classpathTest,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib",
            "-cp", classpathOracle,
            "-nowarn",
            fichier.absolutePath,
        ).redirectErrorStream(true).start()
        val sortie = processus.inputStream.bufferedReader().readText()
        processus.waitFor(120, TimeUnit.SECONDS)
        val erreurs = mutableListOf<DiagnosticProfondKotlin>()
        for (ligne in sortie.lineSequence()) {
            val correspondance =
                Regex(":(\\d+):\\d+: error: (.+)$").find(ligne) ?: continue
            val numero = correspondance.groupValues[1].toIntOrNull() ?: continue
            erreurs.add(DiagnosticProfondKotlin(numero, correspondance.groupValues[2]))
        }
        return erreurs
    }
}

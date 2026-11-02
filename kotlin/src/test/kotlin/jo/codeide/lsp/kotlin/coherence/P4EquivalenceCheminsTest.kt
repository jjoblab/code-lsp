// CodeIDE — banc de cohérence P4 : équivalence des chemins (module :kotlin,
// vague 0). Code NEUF jjoblab (banc de MESURE).
package jo.codeide.lsp.kotlin.coherence

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * P4 — Équivalence des chemins : froid/chaud, index disque/mémoire,
 * ouvert/fermé-rouvert, neutralité des blancs et commentaires (banc de
 * cohérence, vague 0 — miroir de la P2 Java).
 *
 * ## Ce que la propriété mesure
 *
 * Le MÊME corpus (les mondes de la fidélité, copiés dans un répertoire
 * temporaire) est diagnostiqué par des chemins DIFFÉRENTS et les
 * résultats doivent être IDENTIQUES (ensemble des couples code+message
 * par fichier) :
 *
 * - **C1 froid** : session neuve, `dossierIndex = null` (mémoire seule) ;
 * - **C2 chaud** : mêmes documents FERMÉS puis ROUVERTS dans la session ;
 * - **C3 index disque** : session neuve, `dossierIndex` = répertoire
 *   temporaire dédié (segments persistés) ;
 * - **C4 blancs** : insertion de lignes vides et d'un commentaire en tête
 *   — mêmes diagnostics, décalés du nombre de lignes insérées.
 *
 * ## R5 — rapport par défaut, strict à la demande
 *
 * Mode RAPPORT : les différences éventuelles sont IMPRIMÉES (chaque
 * différence est un résultat — la fraîcheur des chemins est une
 * propriété à mesurer, pas à présupposer). Mode STRICT
 * (`BANC_COHERENCE_STRICT`) : identité exigée sur C1–C3 et neutralité
 * des blancs exigée sur C4. Sanité toujours active : jamais d'exception.
 */
class P4EquivalenceCheminsTest {

    /** L'empreinte d'un diagnostic : code + message (indépendante des offsets). */
    private data class Empreinte(val code: String, val message: String)

    /** Les fichiers du corpus de P4 (les mondes, sans le témoin positif). */
    private fun copierMondes(racine: Path): List<Path> {
        val source = SocleCoherence.racineDepot.resolve("kotlin/src/test/resources/fidelite")
        assertTrue(Files.isDirectory(source), "le répertoire des mondes doit exister")
        val copies = Files.list(source).use { flux ->
            flux.filter { it.fileName.toString().endsWith(".kt") && it.fileName.toString() != "temoin-positif.kt" }
                .sorted()
                .map { original ->
                    val copie = racine.resolve(original.fileName.toString())
                    Files.writeString(copie, Files.readString(original))
                    copie
                }
                .toList()
        }
        assertTrue(copies.isNotEmpty(), "les mondes doivent être copiés")
        return copies
    }

    /** L'ensemble des empreintes d'ERREURS par fichier, pour une session donnée. */
    private fun empreintes(
        session: jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments,
        fichiers: List<Path>,
    ): Map<Path, List<Empreinte>> {
        val resultat = LinkedHashMap<Path, List<Empreinte>>()
        for (fichier in fichiers) {
            val uri = fichier.toUri().toString()
            val texte = Files.readString(fichier)
            session.ouvrir(uri, texte)
            resultat[fichier] = session.diagnostics(uri)
                .filter { it.severite == SeveriteKotlin.ERREUR }
                .map { Empreinte(it.code.code, it.message) }
            session.fermer(uri)
        }
        return resultat
    }

    @Test
    fun `froid-chaud-index-disque-et-blancs-rendent-les-mêmes-diagnostics`() {
        val racine = Files.createTempDirectory("p4-chemins")
        val fichiers = copierMondes(racine)
        val jars = SocleCoherence.assurerPlancherStdlib(SocleCoherence.jarsAutoCorpus())

        // C1 — froid, mémoire seule.
        val sessionFroide = SocleCoherence.monterSession(listOf(racine), jars, dossierIndex = null)
        val c1: Map<Path, List<Empreinte>>
        val c2: Map<Path, List<Empreinte>>
        try {
            sessionFroide.rafraichirSources()
            c1 = empreintes(sessionFroide, fichiers)
            // C2 — chaud : documents fermés puis rouverts dans la MÊME session.
            c2 = empreintes(sessionFroide, fichiers)
        } finally {
            sessionFroide.close()
        }

        // C3 — index disque : session NEUVE avec segments persistés.
        val dossierIndex = Files.createTempDirectory("p4-index")
        val sessionIndex = SocleCoherence.monterSession(listOf(racine), jars, dossierIndex = dossierIndex)
        val c3: Map<Path, List<Empreinte>>
        try {
            sessionIndex.rafraichirSources()
            c3 = empreintes(sessionIndex, fichiers)
        } finally {
            sessionIndex.close()
        }

        var differences = mutableListOf<String>()
        for (fichier in fichiers) {
            val nom = fichier.fileName.toString()
            if (c1[fichier] != c2[fichier]) differences += "C1≠C2 sur $nom : ${c1[fichier]} vs ${c2[fichier]}"
            if (c1[fichier] != c3[fichier]) differences += "C1≠C3 sur $nom : ${c1[fichier]} vs ${c3[fichier]}"
        }
        println("P4 C1-C3: fichiers=${fichiers.size}, diagnostics_par_fichier=${c1.values.sumOf { it.size }}, différences=${differences.size}")
        differences.forEach { println("P4 ÉCART $it") }

        // C4 — neutralité des blancs et commentaires : mêmes empreintes,
        // lignes décalées du nombre de lignes insérées.
        val lignesInserees = 3
        for (fichier in fichiers) {
            val texte = Files.readString(fichier)
            Files.writeString(fichier, "\n\n// sonde P4 — commentaire neutre\n" + texte)
        }
        val sessionBlancs = SocleCoherence.monterSession(listOf(racine), jars, dossierIndex = null)
        try {
            sessionBlancs.rafraichirSources()
            val c4 = empreintes(sessionBlancs, fichiers)
            for (fichier in fichiers) {
                if (c1[fichier] != c4[fichier]) {
                    differences += "C1≠C4 (blancs) sur ${fichier.fileName} : ${c1[fichier]} vs ${c4[fichier]}"
                }
            }
            println("P4 C4 (blancs/commentaires, +$lignesInserees lignes): différences cumulées=${differences.size}")
        } finally {
            sessionBlancs.close()
        }
        differences.forEach { println("P4 ÉCART $it") }
        if (SocleCoherence.STRICT) {
            assertTrue(differences.isEmpty(), "P4 strict : ${differences.size} différence(s) de chemin :\n${differences.joinToString("\n")}")
        }
    }
}

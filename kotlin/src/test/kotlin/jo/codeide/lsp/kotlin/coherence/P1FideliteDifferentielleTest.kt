// CodeIDE — banc de cohérence P1 : fidélité différentielle (module :kotlin,
// vague 0). Code NEUF jjoblab (banc de MESURE — ADR 0119 pour l'oracle K2).
package jo.codeide.lsp.kotlin.coherence

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * P1 — Fidélité différentielle moteur vs oracle K2 à GRANDE ÉCHELLE (banc de
 * cohérence, vague 0 — miroir de la P3 Java « zéro faux positif », étendue à
 * la comparaison DIFFÉRENTIELLE avec le compilateur véritable).
 *
 * ## Ce que la propriété mesure
 *
 * Sur du code Kotlin RÉEL (l'auto-corpus = le module `:kotlin` lui-même, et
 * le corpus Android externe quand ses manifestes existent), chaque diagnostic
 * d'ERREUR du moteur est confronté à l'oracle K2 en sous-processus, **par
 * fichier et par LIGNE** :
 *
 * - **FAUX POSITIF (FP)** : un diagnostic moteur ERREUR sur une ligne où
 *   l'oracle n'a AUCUNE erreur — le symptôme n°1 des retours appareil (le
 *   moteur rend FAUX là où le code compile) ;
 * - **FAUX NÉGATIF (FN)** : une erreur de l'oracle sur une ligne sans
 *   diagnostic moteur ERREUR — la couverture se mesure, elle ne se déclare
 *   pas.
 *
 * ## R5 — les chiffres sont IMPRIMÉS, jamais inventés
 *
 * Chaque gate imprime ses nombres (fichiers, lignes, diagnostics moteur par
 * code `kt.*`, erreurs oracle, FP, FN, FP/1 000 lignes, détail de chaque FP,
 * temps). **Mode RAPPORT par défaut** : les écarts sont des RÉSULTATS à
 * rapporter. **Mode STRICT** (`banc.coherence.strict=true` ou
 * `BANC_COHERENCE_STRICT`) : la cible FP = 0 devient une assertion — mode
 * d'escalade des vagues de correctifs à venir.
 *
 * ## Gates corpus (jamais un vert silencieux)
 *
 * Les gates corpus externe exigent les manifestes `*.manifeste` du
 * répertoire `prepares` du corpus (variable `CORPUS_KOTLIN` ou propriété
 * `corpus.kotlin`) — sans eux elles s'ignorent
 * AVEC LA RAISON AFFICHÉE (`assumeTrue`). La gate P1(c) sans classpath
 * (propriété `corpus.kotlin.sansClasspath=true`) mesure la DIVERGENCE de
 * comportement D3 moteur/oracle sur univers vide : rapport uniquement.
 *
 * ## Sanités TOUJOURS actives (quel que soit le mode)
 *
 * L'oracle ne lève pas (timeout, crash compilateur et chemins imprimés hors
 * corpus = échec explicite) et le moteur ne lève AUCUNE exception sur le
 * corpus — une mesure muette n'est pas une mesure.
 */
class P1FideliteDifferentielleTest {

    /** Un diagnostic moteur ramené à sa LIGNE (la granularité de P1). */
    private data class DiagLigne(val ligne: Int, val code: String, val message: String)

    /** La passe moteur d'un corpus : ERREURS par fichier + sanité + volume. */
    private class PasseMoteur(
        val diags: Map<Path, List<DiagLigne>>,
        val exceptions: List<String>,
        val lignes: Int,
    ) {
        /** Le total de diagnostics ERREUR. */
        val total: Int get() = diags.values.sumOf { it.size }

        /** La répartition par code `kt.*` (chiffres imprimés, jamais inventés). */
        val parCode: Map<String, Int> get() = diags.values.flatten().groupingBy { it.code }.eachCount()
    }

    /** Les fichiers `.kt` des racines, tri stable (ordre de mesure stable). */
    private fun fichiersKotlin(racines: List<Path>): List<Path> =
        racines.flatMap { racine ->
            Files.walk(racine).use { flux ->
                flux.filter { chemin -> Files.isRegularFile(chemin) && chemin.fileName.toString().endsWith(".kt") }
                    .map { chemin -> chemin.toAbsolutePath().normalize() }
                    .toList()
            }
        }.sorted()

    /**
     * La passe moteur : pour chaque fichier, OUVRIR → diagnostics (ERREURS
     * seules) → FERMER ; les exceptions sont COLLECTÉES (la sanité
     * « jamais d'exception » s'assertée après, avec le détail).
     */
    private fun passerMoteur(session: SessionLangageKotlinDocuments, fichiers: List<Path>): PasseMoteur {
        val diags = LinkedHashMap<Path, List<DiagLigne>>()
        val exceptions = mutableListOf<String>()
        var lignes = 0
        for (fichier in fichiers) {
            try {
                val texte = Files.readString(fichier)
                lignes += texte.lines().size
                val uri = fichier.toUri().toString()
                session.ouvrir(uri, texte)
                val erreurs = session.diagnostics(uri)
                    .filter { it.severite == SeveriteKotlin.ERREUR }
                    .map { diagnostic ->
                        DiagLigne(
                            ligne = 1 + texte
                                .substring(0, diagnostic.plage.debut.coerceAtMost(texte.length))
                                .count { it == '\n' },
                            code = diagnostic.code.code,
                            message = diagnostic.message,
                        )
                    }
                session.fermer(uri)
                diags[fichier] = erreurs
            } catch (e: Exception) {
                exceptions += "${fichier.fileName} : ${e::class.simpleName} : ${e.message}"
            }
        }
        return PasseMoteur(diags, exceptions, lignes)
    }

    /**
     * Le verdict différentiel : le DÉTAIL de chaque faux positif (fichier,
     * ligne, code, message) et le compte des faux négatifs (erreurs oracle
     * sur des lignes sans diagnostic moteur ERREUR).
     */
    private fun comparer(
        fichiers: List<Path>,
        passe: PasseMoteur,
        oracle: Map<Path, List<SocleCoherence.ErreurOracle>>,
    ): Pair<List<String>, Int> {
        val fauxPositifs = mutableListOf<String>()
        var fauxNegatifs = 0
        for (fichier in fichiers) {
            val lignesMoteur = passe.diags[fichier].orEmpty()
            val erreursOracle = oracle[fichier].orEmpty()
            val lignesOracle = erreursOracle.map { it.ligne }.toSet()
            for (diagnostic in lignesMoteur) {
                if (diagnostic.ligne !in lignesOracle) {
                    fauxPositifs += "${fichier.fileName}:${diagnostic.ligne} [${diagnostic.code}] ${diagnostic.message}"
                }
            }
            val lignesMoteurSet = lignesMoteur.map { it.ligne }.toSet()
            for (erreur in erreursOracle) {
                if (erreur.ligne !in lignesMoteurSet) fauxNegatifs++
            }
        }
        return fauxPositifs to fauxNegatifs
    }

    /** La sanité oracle : les chemins imprimés par K2 sont ceux du corpus. */
    private fun saniteOracle(oracle: Map<Path, List<SocleCoherence.ErreurOracle>>, corpus: List<Path>, contexte: String) {
        val attendus = corpus.toHashSet()
        val inattendus = oracle.keys.filter { it !in attendus }
        assertTrue(inattendus.isEmpty(), "l'oracle a imprimé des chemins hors corpus ($contexte) : $inattendus")
    }

    // ── P1 : auto-corpus (TOUJOURS actif) ───────────────────────────────

    /**
     * P1 auto-corpus : zéro faux positif du moteur sur SON PROPRE code
     * compilable (les 99 fichiers `.kt` de `kotlin/src/main/kotlin` + la
     * grammaire vendorée — du vrai Kotlin, K2-compilable, TOUJOURS en CI).
     *
     * L'oracle compile le corpus ENTIERS en UNE invocation (mesuré en bac à
     * sable : des passes séparées de 40 fichiers fabriquent 462 « unresolved
     * reference » CROISÉS — le module de compilation est l'univers de la
     * mesure) ; le moteur voit les mêmes racines et les jars du classpath de
     * test.
     */
    @Test
    fun `auto-corpus---zéro-faux-positif-du-moteur-sur-son-propre-code-compilable`() {
        val debut = System.nanoTime()
        val racines = SocleCoherence.racinesAutoCorpus()
        assertTrue(racines.isNotEmpty(), "l'auto-corpus (kotlin/src/main/kotlin + vendor) doit exister")
        val jars = SocleCoherence.assurerPlancherStdlib(SocleCoherence.jarsAutoCorpus())
        assertTrue(jars.isNotEmpty(), "le classpath de test doit porter des jars")
        val fichiers = fichiersKotlin(racines)
        assertTrue(fichiers.isNotEmpty(), "l'auto-corpus doit porter des fichiers .kt")
        val session = SocleCoherence.monterSession(racines, jars, attenteSecondes = 600)
        val finMontage = System.nanoTime()
        try {
            // Déterminisme de mesure : le scan des racines rejoint la passe
            // moteur AVANT la mesure (miroir du cycle hôte — un hôte appelle
            // rafraichirSources après le branchement des racines).
            session.rafraichirSources()
            val passe = passerMoteur(session, fichiers)
            val finMoteur = System.nanoTime()
            val oracle = SocleCoherence.oracleK2(fichiers, jars)
            val finOracle = System.nanoTime()

            // SANITÉS toujours actives : l'oracle a répondu pour le corpus
            // et le moteur n'a levé AUCUNE exception.
            saniteOracle(oracle, fichiers, "auto-corpus")
            assertTrue(
                passe.exceptions.isEmpty(),
                "le moteur a levé sur ${passe.exceptions.size} fichier(s) : ${passe.exceptions.joinToString(" ; ")}",
            )

            val (detailFp, fn) = comparer(fichiers, passe, oracle)
            val fp = detailFp.size
            val fpPourMille = if (passe.lignes == 0) 0.0 else fp * 1000.0 / passe.lignes
            println(
                "P1 auto-corpus: fichiers=${fichiers.size}, lignes=${passe.lignes}, " +
                    "diags_moteur=${passe.total} (par code: ${passe.parCode}), " +
                    "erreurs_oracle=${oracle.values.sumOf { it.size }}, FP=$fp, FN=$fn, " +
                    "FP/1000l=${String.format(Locale.ROOT, "%.3f", fpPourMille)}",
            )
            println(
                "P1 auto-corpus: montage=${(finMontage - debut) / 1_000_000} ms, " +
                    "moteur=${(finMoteur - finMontage) / 1_000_000} ms, " +
                    "oracle=${(finOracle - finMoteur) / 1_000_000} ms, " +
                    "total=${(finOracle - debut) / 1_000_000} ms",
            )
            detailFp.forEach { println("P1 auto-corpus: FP $it") }

            // Mode strict : la CIBLE est FP = 0 (l'écart mesuré est imprimé
            // en mode rapport — jamais un silence, jamais un inventé).
            if (SocleCoherence.STRICT) {
                assertTrue(fp == 0, "P1 strict : FP=$fp (cible 0) :\n${detailFp.joinToString("\n")}")
            }
        } finally {
            session.close()
        }
    }

    // ── P1(b) : corpus externe (manifestes requis) ──────────────────────

    /**
     * P1(b) corpus externe : fidélité sur code Android RÉEL (les projets du
     * corpus `bancs/corpus-android-kotlin`, racines et jars de leurs
     * manifestes). Le montage REMONTE la session par projet (le moteur
     * singleton accepte les redéfinitions de classpath — voir
     * [SocleCoherence.monterSession]) ; impression par projet (FP, FN,
     * FP/1 000 lignes, par code). Mode strict : FP = 0 PAR PROJET.
     */
    @Test
    fun `corpus-externe---fidélité-sur-code-Android-réel-(manifestes-requis)`() {
        val projets = SocleCoherence.projetsCorpus()
        assumeTrue(
            projets.isNotEmpty(),
            "CORPUS_KOTLIN non posé ou manifestes absents — gate ignorée, jamais un vert silencieux",
        )
        for (projet in projets) {
            val fichiers = fichiersKotlin(projet.racines)
            assertTrue(fichiers.isNotEmpty(), "le projet ${projet.nom} doit porter des fichiers .kt")
            val jars = SocleCoherence.assurerPlancherStdlib(projet.jars)
            val session = SocleCoherence.monterSession(projet.racines, jars, attenteSecondes = 600)
            try {
                session.rafraichirSources()
                val passe = passerMoteur(session, fichiers)
                val oracle = SocleCoherence.oracleK2(fichiers, jars)

                saniteOracle(oracle, fichiers, "projet ${projet.nom}")
                assertTrue(
                    passe.exceptions.isEmpty(),
                    "le moteur a levé sur ${projet.nom} : ${passe.exceptions.joinToString(" ; ")}",
                )

                val (detailFp, fn) = comparer(fichiers, passe, oracle)
                val fp = detailFp.size
                val fpPourMille = if (passe.lignes == 0) 0.0 else fp * 1000.0 / passe.lignes
                println(
                    "P1(b) ${projet.nom}: fichiers=${fichiers.size}, lignes=${passe.lignes}, " +
                        "diags_moteur=${passe.total} (par code: ${passe.parCode}), " +
                        "erreurs_oracle=${oracle.values.sumOf { it.size }}, FP=$fp, FN=$fn, " +
                        "FP/1000l=${String.format(Locale.ROOT, "%.3f", fpPourMille)}",
                )
                detailFp.forEach { println("P1(b) ${projet.nom}: FP $it") }
                if (SocleCoherence.STRICT) {
                    assertTrue(
                        fp == 0,
                        "P1(b) strict ${projet.nom} : FP=$fp (cible 0) :\n${detailFp.joinToString("\n")}",
                    )
                }
            } finally {
                session.close()
            }
        }
    }

    // ── P1(c) : corpus externe SANS classpath (D3, rapport seul) ────────

    /**
     * P1(c) corpus sans classpath : divergence de comportement D3 moteur vs
     * oracle sur code Android réel avec un univers VIDE (racines du projet,
     * AUCUN jar des deux côtés). Mesure : erreurs oracle, diagnostics
     * moteur, FN (lignes oracle sans diagnostic moteur), taux de silence
     * global — RAPPORT UNIQUEMENT (aucune cible FP : sans classpath le code
     * ne compile pas, la mesure est celle de la DIVERGENCE de silence).
     */
    @Test
    fun `corpus-externe-sans-classpath---divergence-D3-sur-code-Android-réel`() {
        val projets = SocleCoherence.projetsCorpus()
        assumeTrue(
            SocleCoherence.SANS_CLASSPATH && projets.isNotEmpty(),
            "corpus.kotlin.sansClasspath=true ET corpus présent requis — gate ignorée, jamais un vert silencieux",
        )
        val projet = projets.firstOrNull { it.nom == "sunflower" } ?: projets.first()
        val fichiers = fichiersKotlin(projet.racines)
        assertTrue(fichiers.isNotEmpty(), "le projet ${projet.nom} doit porter des fichiers .kt")
        val session = SocleCoherence.monterSession(projet.racines, emptyList(), attenteSecondes = 120)
        try {
            session.rafraichirSources()
            val passe = passerMoteur(session, fichiers)
            val oracle = SocleCoherence.oracleK2(fichiers, emptyList())

            saniteOracle(oracle, fichiers, "sans-classpath ${projet.nom}")
            var lignesOracle = 0
            var lignesSilencieuses = 0
            for (fichier in fichiers) {
                val lignesMoteur = passe.diags[fichier].orEmpty().map { it.ligne }.toSet()
                val oracleFichier = oracle[fichier].orEmpty().map { it.ligne }.toSet()
                lignesOracle += oracleFichier.size
                lignesSilencieuses += oracleFichier.count { it !in lignesMoteur }
            }
            val tauxSilence = if (lignesOracle == 0) 0.0 else lignesSilencieuses * 100.0 / lignesOracle
            println(
                "P1(c) sans-classpath ${projet.nom}: fichiers=${fichiers.size}, lignes=${passe.lignes}, " +
                    "erreurs_oracle=${oracle.values.sumOf { it.size }}, " +
                    "diags_moteur=${passe.total} (par code: ${passe.parCode}), FN=$lignesSilencieuses, " +
                    "taux_silence=${String.format(Locale.ROOT, "%.1f", tauxSilence)}%",
            )
            println(
                "P1(c) ATTENTION : mesure de divergence de comportement D3 (univers vide), " +
                    "PAS une mesure de FP sur code compilant — les classpath réels exigent les manifestes",
            )
            assertTrue(
                passe.exceptions.isEmpty(),
                "le moteur ne lève pas (le silence D3 est muet, jamais cassé) : " +
                    "${passe.exceptions.joinToString(" ; ")}",
            )
        } finally {
            session.close()
        }
    }
}

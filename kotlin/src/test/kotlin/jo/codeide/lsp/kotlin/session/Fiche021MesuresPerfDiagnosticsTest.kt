package jo.codeide.lsp.kotlin.session

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 021 — MesuresPerfDiagnosticsTest : MESURES DE PERFORMANCE sur un
 * GROS fichier (≥ 1 500 lignes) — reparse complet et diagnostics, à froid
 * puis à chaud (le comportement « caches-perf » du cahier
 * docs/cahier-specifications-kotlin/ : la RELÈVE des chiffres, pas une
 * capacité du moteur).
 *
 * Le document est GÉNÉRÉ DÉTERMINISTEMENT (~1 650 lignes : 150 fonctions
 * réelles — locales, appels stdlib, littéraux, chaînes) ; la session porte
 * le VRAI stdlib en entrée (index nourri, AUCUNE racine de scan : l'univers
 * des documents OUVERTS est complet par contrat — les diagnostics
 * `kt.unresolved` y sont donc ÉMIS, l'univers des preuves étant couvert).
 *
 * Le montage est le CHEMIN DE PRODUCTION D6 (miroir du banc qualité de la
 * vague 3e) : moteur nourricier NEUF + session BRANCHÉE + guichet attendu
 * par sondage borné — jamais le raccourci synchrone qui n'existerait pas
 * côté hôte (la publication de l'index est asynchrone, ADR 0211). Le
 * nourrissage du stdlib est HORS mesure : ce que la fiche chronomètre est
 * le CYCLE DOCUMENT (reparse intégral — synchronisation Full — puis
 * diagnostics recalculés intégralement).
 *
 * Protocole : un passage À FROID (premier parse + premiers diagnostics)
 * puis CINQ passages À CHAUD (mutation intégrale du texte — un nom de
 * fonction changé par passe, tout cache par contenu est invalidé — puis
 * diagnostics complets). Les chiffres sont IMPRIMÉS pour le journal
 * d'exécution (étiquette [mesuré], environnement bac à sable JVM 17) ; les
 * bornes de SANITÉ sont volontairement LARGES (machine CI 2 cœurs / 4 Go :
 * le test chasse la régression pathologique, pas la latence — les cibles
 * de latence vivent dans les bancs et la synthèse, pas ici).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche021MesuresPerfDiagnosticsTest {

    /** La racine temporaire (copie du stdlib, jamais l'original touché). */
    @TempDir
    lateinit var racine: Path

    /**
     * La COPIE isolée du jar kotlin-stdlib du runtime de test (miroir des
     * montages fiche 042 : le jar original n'est jamais lu directement).
     */
    private fun copieStdlib(): File {
        val original =
            File(
                kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                    .codeSource.location.toURI(),
            )
        val copie = Files.createTempFile(racine, "stdlib-t", ".jar").toFile()
        copie.writeBytes(original.readBytes())
        return copie
    }

    /**
     * Le document géant DÉTERMINISTE : 150 fonctions réelles de ~12 lignes
     * (~1 800 lignes) — locales, appels stdlib (`listOf`, chaîne
     * `map`/`filter`/`sorted`, `sum`, `println`), littéraux, chaînes et
     * gabarits. Aucune donnée aléatoire : deux exécutions produisent le
     * même texte au octet près (les chiffres restent reproductibles).
     */
    private fun sourceGeante(): String =
        buildString {
            appendLine("package com.exemple.perf")
            appendLine()
            for (indice in 1..NOMBRE_FONCTIONS) {
                appendLine("fun calcul$indice(base: Int): Int {")
                appendLine("    val montant = base * $indice + ${indice % 7}")
                appendLine("    val libelle = \"calcul-$indice\"")
                appendLine("    val liste = listOf(montant, base, ${indice % 13})")
                appendLine("    val trie = liste.map { it + 1 }.filter { it > ${indice % 5} }.sorted()")
                appendLine("    val total = trie.sum()")
                appendLine("    println(libelle)")
                appendLine("    val texte = libelle + \":\" + total")
                appendLine("    val profondeur = if (total > $indice) total - base else base - total")
                appendLine("    return profondeur + texte.length")
                appendLine("}")
                appendLine()
            }
        }

    /**
     * Attend (sondage borné) que le guichet de la session serve le
     * snapshot publié par la passe du moteur — l'univers des preuves doit
     * être COUVERT avant les mesures, sinon les `kt.unresolved` se
     * tairaient (régime dégradé) et la preuve de santé serait creuse.
     */
    private fun attendreCouvert(
        session: SessionLangageKotlinDocuments,
        secondes: Long = 90,
    ) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    @Test
    fun `gros fichier - reparse complet et diagnostics mesures (froid puis 5 passes chaudes)`() {
        val source = sourceGeante()
        val lignes = source.count { it == '\n' } + 1
        assertTrue(lignes >= 1500, "document géant exigé (>= 1500 lignes) : $lignes")

        // Montage chemin de production (D6) : moteur nourricier neuf,
        // session branchée, stdlib réel en COPIE, AUCUNE racine de scan.
        val moteur = MoteurLangageKotlinNoyau()
        val classpath =
            ClasspathKotlin(
                entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copieStdlib().path, "test:t-stdlib")),
                racinesSources = emptyList(),
                dossierIndex = null,
            )
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        assertTrue(moteur.definirClasspath(classpath), "le moteur noyau est nourricier : la passe rend true")
        attendreCouvert(session)

        val uri = "file:///perf-gros.kt"

        // À FROID : premier parse intégral + premiers diagnostics.
        var debut = System.nanoTime()
        session.ouvrir(uri, source)
        val reparseFroidMs = (System.nanoTime() - debut) / 1_000_000
        debut = System.nanoTime()
        val diagnostiques = session.diagnostics(uri)
        val diagnosticsFroidsMs = (System.nanoTime() - debut) / 1_000_000
        assertTrue(
            diagnostiques.none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "document sain attendu (aucun non résolu) : " +
                diagnostiques.map { "${it.code.code}@${it.plage.debut}: ${it.message}" }.joinToString(limit = 12),
        )

        // À CHAUD : 5 passes — mutation intégrale (un nom changé par
        // passe : aucun cache par contenu ne peut survivre) puis
        // diagnostics recalculés intégralement.
        val reprisesChaudes = mutableListOf<Long>()
        val diagnosticsChauds = mutableListOf<Long>()
        for (passe in 1..NOMBRE_PASSES_CHAUDES) {
            val texte = source.replaceFirst("calcul2(", "calcul2Passage$passe(")
            debut = System.nanoTime()
            session.modifier(uri, texte)
            reprisesChaudes += (System.nanoTime() - debut) / 1_000_000
            debut = System.nanoTime()
            session.diagnostics(uri)
            diagnosticsChauds += (System.nanoTime() - debut) / 1_000_000
        }
        session.close()

        // La relève des chiffres (journal d'exécution du transverse T).
        println(
            "=== MESURES PERF T: lignes=$lignes fonctions=$NOMBRE_FONCTIONS " +
                "reparseFroid=${reparseFroidMs}ms repriseChaudeMin=${reprisesChaudes.min()}ms " +
                "reprisesChaudes=${reprisesChaudes}ms " +
                "diagnosticsFroid=${diagnosticsFroidsMs}ms " +
                "diagnosticsChaudMin=${diagnosticsChauds.min()}ms " +
                "diagnosticsChauds=${diagnosticsChauds}ms",
        )

        // Bornes de SANITÉ (larges — machine CI 2 cœurs) : la régression
        // pathologique seulement ; les chiffres réels vivent au journal.
        assertTrue(reparseFroidMs < 20_000, "reparse à froid borné (< 20 s) : ${reparseFroidMs}ms")
        assertTrue(reprisesChaudes.min() < 10_000, "reprise à chaud bornée (< 10 s) : ${reprisesChaudes.min()}ms")
        assertTrue(diagnosticsChauds.min() < 10_000, "diagnostics à chaud bornés (< 10 s) : ${diagnosticsChauds.min()}ms")
    }

    private companion object {
        /** Le nombre de fonctions du document géant (~11 lignes chacune). */
        private const val NOMBRE_FONCTIONS: Int = 150

        /** Le nombre de passes chaudes (reparse intégral + diagnostics). */
        private const val NOMBRE_PASSES_CHAUDES: Int = 5
    }
}

package jo.codeide.lsp.kotlin.index

import java.io.File
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 008 — IndexAppelablesKotlinTest : tests de l'index des appelables
 * (K3) — le CONTRAT DISQUE est le cœur du test : un segment écrit puis
 * relu SANS le jar d'origine (supprimé) prouve la persistance ; un
 * segment CORROMPU se re-décoderait (jar absent → partiel honnête, jamais
 * d'exception).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/). Le stdlib
 * RÉEL du classpath de test sert d'entrée froide (COPIE sous empreinte de
 * test — le jar original n'est JAMAIS touché).
 */
class Fiche008IndexAppelablesKotlinTest {

    /** La racine temporaire (segments et copies isolées). */
    @TempDir
    lateinit var dossier: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): File =
        File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une entrée pointant le stdlib du classpath, sous empreinte de test. */
    private fun entreeStdlib(empreinte: String): EntreeClasspathKotlin =
        EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, empreinte)

    /** Une COPIE isolée du stdlib sous [empreinte] (jamais l'original). */
    private fun copieStdlib(empreinte: String): Pair<EntreeClasspathKotlin, File> {
        val copie = dossier.resolve("stdlib-$empreinte.jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        return EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, empreinte) to copie
    }

    @Test
    fun `segment-persiste---le-jar-supprime-est-encore-servi-par-le-cache`() {
        val stockage = dossier.resolve("index").toFile().apply { mkdirs() }
        val (entree, copie) = copieStdlib("test:stdlib-1")

        val index = IndexAppelablesKotlin()
        index.definir(listOf(entree), stockage)
        assertFalse(index.extensionsSur("kotlin.String").isEmpty(), "le stdlib doit offrir des extensions de String")

        // Le jar DISPARAÎT : la redéfinition doit être servie par le
        // SEGMENT (l'index est un cache — le contrat de persistance).
        assertTrue(copie.delete(), "la copie de travail doit exister pour ce test")
        val indexRelu = IndexAppelablesKotlin()
        indexRelu.definir(listOf(entree), stockage)
        assertFalse(indexRelu.extensionsSur("kotlin.String").isEmpty(), "segment attendu : les extensions doivent survivre au jar")
    }

    @Test
    fun `memoire-seule---index-fonctionnel-sans-dossier`() {
        val index = IndexAppelablesKotlin()
        index.definir(listOf(entreeStdlib("test:mem")), null)
        assertFalse(index.extensionsSur("kotlin.String").isEmpty())
        assertTrue(index.nomsTopParPrefixe("tri", 10).isNotEmpty())
        assertFalse(index.topParNom("trim").isEmpty())
    }

    @Test
    fun `entree-inexistante---partiel-honnete,-jamais-d'exception`() {
        val index = IndexAppelablesKotlin()
        index.definir(
            listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, "/inexistant.jar", "test:absent")),
            dossier.resolve("index2").toFile().apply { mkdirs() },
        )
        assertTrue(index.extensionsSur("kotlin.String").isEmpty())
        assertTrue(index.topParNom("trim").isEmpty())
    }

    /**
     * Rejeu §4 (étape B) : les TYPEALIAS publics du corpus réel
     * kotlinx-datetime vivent dans une carte DÉDIÉE — les appelables et
     * la complétion de noms n'en sont PAS pollués, les gardes les sondent
     * par nom et par FQCN complet.
     */
    @Test
    fun `typealias---carte-dediee,-appelables-et-completion-non-pollues`() {
        val entreeDatetime =
            EntreeClasspathKotlin(
                KindEntreeKotlin.JAR,
                File(
                    kotlinx.datetime.DateTimePeriod::class.java.protectionDomain
                        .codeSource
                        .location
                        .toURI(),
                ).path,
                "test:alias-1",
            )
        val index = IndexAppelablesKotlin()
        index.definir(listOf(entreeDatetime), null)
        val zone = index.aliasTopParNom("ZoneOffset")
        assertTrue(
            zone.isNotEmpty() && zone.all { it.genre == GenreAppelable.ALIAS },
            "l'alias ZoneOffset est indexé en genre ALIAS (corpus kotlinx-datetime)",
        )
        assertEquals("kotlinx.datetime", zone.first().paquet)
        // La carte des APPELABLES reste pure : complétion, survol et
        // signatures d'appel ne voient JAMAIS les alias.
        assertTrue(
            index.topParNom("ZoneOffset").none { it.genre == GenreAppelable.ALIAS },
            "topParNom ne rend pas les alias",
        )
        assertTrue(
            index.nomsTopParPrefixe("Zone", 10).none { it == "ZoneOffset" },
            "la complétion de noms top ne propose pas les alias",
        )
        // Gardes : le FQCN complet d'un alias est CONNU...
        assertTrue(index.estTypeAliasConnu("kotlinx.datetime.ZoneOffset"))
        assertTrue(index.estTypeAliasConnu("kotlinx.datetime.Month"))
        assertTrue(index.estTypeAliasConnu("kotlinx.datetime.DayOfWeek"))
        // ... l'inconnu et le nom SANS paquet ne le sont pas (D3).
        assertFalse(index.estTypeAliasConnu("kotlinx.datetime.Inexistant"))
        assertFalse(index.estTypeAliasConnu("ZoneOffset"))
    }

    @Test
    fun `recherche-par-prefixe---binaire-bornee-et-limite-respectee`() {
        val index = IndexAppelablesKotlin()
        index.definir(listOf(entreeStdlib("test:prefixe")), null)
        val cinq = index.nomsTopParPrefixe("tr", 5)
        assertTrue(cinq.size <= 5)
        assertTrue(cinq.all { it.startsWith("tr") })
        // Préfixe introuvable : liste vide (pas de scan complet).
        assertTrue(index.nomsTopParPrefixe("zzzzzzzz", 5).isEmpty())
    }

    // ── ADR 0211/0214 (demandes D1/D4 de CodeIDE) ──────────────────────

    @Test
    fun `garde-d-empreintes---definir-deux-fois-est-un-no-op-chaud`() {
        val index = IndexAppelablesKotlin()
        val saine = entreeStdlib("test:garde-1")
        val autre = entreeStdlib("test:garde-2")
        val entrees = listOf(saine, autre)
        assertEquals(
            IndexAppelablesKotlin.ResultatDefin.RECONSTRUIT,
            index.definir(entrees, null),
        )
        // Deuxième appel : le snapshot COUVRE — aucune relecture (l'issue
        // d'observation ne se déclenche JAMAIS).
        var issues = 0
        val resultat =
            index.definir(entrees, null) { _, issue ->
                if (issue != null) issues++
                true
            }
        assertEquals(IndexAppelablesKotlin.ResultatDefin.DEJA_COUVERT, resultat)
        assertEquals(0, issues)
        assertTrue(index.couvre(entrees))
        assertFalse(index.couvre(entrees.reversed()), "un ordre différent n'est pas couvert")
        assertFalse(index.couvre(listOf(saine)), "une entrée différente n'est pas couverte")
    }

    @Test
    fun `cache-memoire---le-dossier-efface-ne-re-relit-rien`() {
        val stockage = dossier.resolve("index-cache").toFile().apply { mkdirs() }
        val (entreeA, copie) = copieStdlib("test:cache-a")
        val entreeB = EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, "test:cache-b")
        val index = IndexAppelablesKotlin()
        // Les DEUX empreintes vivent en cache mémoire (chacune a été lue
        // une fois, à des ordres différents — la garde ne bloque que la
        // liste EXACTE).
        index.definir(listOf(entreeA), stockage)
        index.definir(listOf(entreeB, entreeA), stockage)
        assertFalse(index.extensionsSur("kotlin.String").isEmpty())

        // Le disque DISPARAÎT tout entier (segments ET jar) : une
        // redéfinition à ordre DIFFÉRENT échappe à la garde mais est
        // servie par le CACHE MÉMOIRE — tout est REPRIS, aucune lecture.
        stockage.deleteRecursively()
        assertTrue(copie.delete(), "la copie de travail doit exister pour ce test")
        var reprises = 0
        val resultat =
            index.definir(listOf(entreeA, entreeB), stockage) { _, issue ->
                if (issue == IndexAppelablesKotlin.IssueSegment.REPRIS) reprises++
                true
            }
        assertEquals(IndexAppelablesKotlin.ResultatDefin.RECONSTRUIT, resultat)
        assertEquals(2, reprises, "les deux entrées sont reprises du cache mémoire")
        assertFalse(index.extensionsSur("kotlin.String").isEmpty(), "les extensions survivent au disque effacé")
        assertTrue(index.couvre(listOf(entreeA, entreeB)))
    }

    @Test
    fun `echec-jamais-en-cache---la-reprise-retente-toujours-l-illisible`() {
        val stockage = dossier.resolve("index-echec").toFile().apply { mkdirs() }
        val saine = entreeStdlib("test:echec-saine")
        val absente = EntreeClasspathKotlin(KindEntreeKotlin.JAR, "/inexistant-reprise.jar", "test:echec-absente")
        val index = IndexAppelablesKotlin()
        val issuesPremier = mutableListOf<IndexAppelablesKotlin.IssueSegment>()
        index.definir(listOf(saine, absente), stockage) { _, issue ->
            if (issue != null) issuesPremier.add(issue)
            true
        }
        assertEquals(
            listOf(IndexAppelablesKotlin.IssueSegment.TERMINE, IndexAppelablesKotlin.IssueSegment.ECARTE),
            issuesPremier,
        )
        // La REPRISE ciblée : la saine est REPRIS du cache, l'absente est
        // RETENTÉE (un échec n'est jamais mis en cache).
        val issuesReprise = mutableListOf<IndexAppelablesKotlin.IssueSegment>()
        index.definir(listOf(saine, absente), stockage) { _, issue ->
            if (issue != null) issuesReprise.add(issue)
            true
        }
        assertEquals(
            listOf(IndexAppelablesKotlin.IssueSegment.REPRIS, IndexAppelablesKotlin.IssueSegment.ECARTE),
            issuesReprise,
        )
        // Le snapshot couvre les entrées SAINES seules (l'absente n'y
        // entre pas) — la couverture exige la liste EXACTE.
        assertFalse(index.couvre(listOf(saine, absente)))
    }

    @Test
    fun `annulation---le-snapshot-partiel-est-la-verite`() {
        val stockage = dossier.resolve("index-annul").toFile().apply { mkdirs() }
        val (premiere, copie) = copieStdlib("test:annul-1")
        val seconde = EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, "test:annul-2")
        val index = IndexAppelablesKotlin()
        val resultat =
            index.definir(listOf(premiere, seconde), stockage) { _, _ -> false } // annule après la 1re
        assertEquals(IndexAppelablesKotlin.ResultatDefin.PARTIEL_ANNULE, resultat)
        // L'ACQUIS de la première entrée est publié — la seconde pend.
        assertFalse(index.extensionsSur("kotlin.String").isEmpty())
        assertFalse(index.couvre(listOf(premiere, seconde)))
        assertTrue(index.couvre(listOf(premiere)), "la première entrée seule est couverte")
    }
}

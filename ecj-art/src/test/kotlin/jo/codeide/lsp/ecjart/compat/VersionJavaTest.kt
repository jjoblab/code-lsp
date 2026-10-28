package jo.codeide.lsp.ecjart.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.Runtime.Version

/**
 * Tests du shim [VersionJava] — sémantique JEP 322.
 *
 * La fidélité est éprouvée en **différentiel contre l'oracle JVM** : les
 * mêmes chaînes sont analysées et comparées par la vraie
 * `java.lang.Runtime.Version` (disponible sur la machine de build) et par le
 * shim — acceptations, rejets et signes de comparaison doivent coïncider.
 * Les cas figés ici proviennent des sondes du 28/10/2026 (comportements
 * constatés, pas déduits) et des chaînes réellement alimentées par ecj
 * (niveaux de conformité de `CompilerOptions` : « 1.1 » … « 23 »).
 */
class VersionJavaTest {

    /**
     * Signe d'un entier : -1, 0 ou 1 (`kotlin.math.sign` n'existe qu'en
     * variantes Double/Float — les comparaisons du JEP 322 sont entières).
     */
    private fun signeEntier(v: Int): Int = when {
        v < 0 -> -1
        v > 0 -> 1
        else -> 0
    }

    // ------------------------------------------------------------------
    // parse — acceptations et rejets (grammaire stricte du JEP 322)
    // ------------------------------------------------------------------

    /** Formes valides constatées acceptées par l'oracle JVM. */
    private val formesValides = listOf(
        "21", "1.8", "9-ea", "9+3", "9+-ea", "10.0.2", "10.0.2.1", "21.0.1",
        "21.0.1.2.3", "9.0.1", "9.0.2.1", "9.1.2+3-LTS", "9-ea-b19", "9-ea+3",
        "9+0", "9-123", "9-45", "9-9", "9-EA", "9+3-a", "9+3-a.b", "9+3-LTS.1",
        "9.0.0.1", "9.0.0.1-ea",
    )

    /** Formes invalides constatées rejetées (IllegalArgumentException) par l'oracle JVM. */
    private val formesInvalides = listOf(
        "21.0", "9.0.0", "1.8.0_30", "9.1.2-ea.1", "0", "09", "9.01", "-ea",
        "9-", "9+", "1.8.0", "1.9.0-ea-b19", "9+007", "9-ea.1", "9+3-",
        "10.00.1", "", "9.x", "9.1..2", "9--ea", "9++3",
    )

    @Test
    fun `parse accepte les formes valides`() {
        for (forme in formesValides) {
            VersionJava.parse(forme) // ne doit pas lever
        }
    }

    @Test
    fun `parse rejette les formes invalides`() {
        for (forme in formesInvalides) {
            assertThrows(
                "« $forme » doit être rejetée",
                IllegalArgumentException::class.java,
            ) { VersionJava.parse(forme) }
        }
    }

    @Test
    fun `parse en différentiel contre l'oracle JVM`() {
        for (forme in formesValides + formesInvalides) {
            val oracle = try {
                Version.parse(forme).toString()
            } catch (e: IllegalArgumentException) {
                "IAE"
            }
            val shim = try {
                VersionJava.parse(forme).toString()
            } catch (e: IllegalArgumentException) {
                "IAE"
            }
            assertEquals("forme « $forme »", oracle, shim)
        }
    }

    @Test
    fun `toString restitue la chaîne d'origine`() {
        for (forme in listOf("21", "9.0.1", "9-ea+3-LTS")) {
            assertEquals(forme, VersionJava.parse(forme).toString())
        }
    }

    // ------------------------------------------------------------------
    // compareTo — ordre du JEP 322
    // ------------------------------------------------------------------

    @Test
    fun `ordre de comparaison - cas figés par les sondes`() {
        fun signe(a: String, b: String): Int =
            signeEntier(VersionJava.parse(a).compareTo(VersionJava.parse(b)))

        // numéros de version : point à point, suite courte inférieure
        assertEquals(-1, signe("10.0.2", "10.0.2.1"))
        assertEquals(-1, signe("9", "9.0.1"))
        assertEquals(1, signe("21", "9"))
        assertEquals(0, signe("9.0.1", "9.0.1"))
        // pré-version : absente > présente ; numérique < non numérique ; en nombre si numériques
        assertEquals(-1, signe("9-ea", "9"))
        assertEquals(1, signe("9-123", "9-45"))
        assertEquals(-1, signe("9-123", "9-ea"))
        assertEquals(-1, signe("9-45", "9-ea"))
        assertEquals(-1, signe("9-EA", "9-ea")) // lexicographique sensible à la casse
        assertEquals(-1, signe("9-9", "9-10")) // numérique : 9 < 10
        // build : absent < présent, sinon numérique
        assertEquals(-1, signe("9", "9+3"))
        assertEquals(1, signe("9+3", "9"))
        // précédence : pré-version avant build
        assertEquals(-1, signe("9.1.2-ea", "9.1.2+5"))
        assertEquals(-1, signe("9.1.2-ea", "9.1.2"))
        // information optionnelle : absente < présente, lexicographique
        assertEquals(1, signe("9+3-a", "9+3"))
        assertEquals(-1, signe("9+3-a", "9+3-b"))
        // précédence vnum : 9+5 < 9.1.2+5
        assertEquals(-1, signe("9+5", "9.1.2+5"))
    }

    @Test
    fun `ordre en différentiel intégral contre l'oracle JVM`() {
        val formes = formesValides.filter { !it.contains('_') }
        for (a in formes) {
            for (b in formes) {
                val oracle = signeEntier(
                    Version.parse(a).compareTo(Version.parse(b)),
                )
                val shim = signeEntier(
                    VersionJava.parse(a).compareTo(VersionJava.parse(b)),
                )
                assertEquals("« $a » vs « $b »", oracle, shim)
            }
        }
    }

    // ------------------------------------------------------------------
    // cas d'usage d'ecj (diagnoseParse du Parser)
    // ------------------------------------------------------------------

    @Test
    fun `niveaux de conformité alimentés par ecj - ordre exact`() {
        // Chaînes constatées dans CompilerOptions d'ecj 3.40.0 — le Parser
        // compare requestedSourceVersion au dernier niveau supporté.
        val niveaux = listOf("1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8",
            "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23")
        for (i in niveaux.indices) {
            for (j in niveaux.indices) {
                val attendu = signeEntier(i.compareTo(j))
                val obtenu = signeEntier(
                    VersionJava.parse(niveaux[i]).compareTo(VersionJava.parse(niveaux[j])),
                )
                assertEquals("${niveaux[i]} vs ${niveaux[j]}", attendu, obtenu)
            }
        }
    }
}

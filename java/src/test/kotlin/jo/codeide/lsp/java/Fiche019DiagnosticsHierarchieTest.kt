package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 019 — EcjHierarchyDiagnosticsTest : enrichissement du diagnostic
 * « La hiérarchie du type X est incohérente » (P5) — quand l'ancêtre
 * manquant est le super d'un supertype BINAIRE, le message est replié en
 * « … — absent du classpath : lib.A (requis par lib.B) ».
 *
 * Fixture : `lib.A` et `lib.B` compilés par ecj EN MÉMOIRE (remplace le
 * javac système du cahier — même effet, aucune dépendance environnementale),
 * puis seuls les `.class` choisis sont placés dans le jar du classpath.
 */
class Fiche019DiagnosticsHierarchieTest {

    private val sourceC = "package app;\nclass C extends lib.B { }\n"

    /**
     * Compile lib.A et lib.B : rend les octets des deux classes (la racine
     * de compilation est ÉPHÉMÈRE — les sources A/B ne doivent PAS vivre
     * dans les racines du moteur, sinon elles résoudraient avant le jar).
     */
    private fun preparerRacineEtClasses(): Triple<Path, ByteArray, ByteArray> {
        val racineCompilation = Files.createTempDirectory("fiche019-compil")
        val classes = OutilsFixture.compilerEnMemoire(
            racineCompilation,
            mapOf(
                "/lib/A.java" to "package lib;\npublic class A { }\n",
                "/lib/B.java" to "package lib;\npublic class B extends A { }\n",
            ),
        )
        val classeA = classes.getValue("lib/A.class")
        val classeB = classes.getValue("lib/B.class")
        return Triple(racineCompilation, classeA, classeB)
    }

    /** Racine source du moteur : porte UNIQUEMENT la source C (paquet app). */
    private fun racineSourceC(parent: Path): Path {
        val racine = Files.createTempDirectory("fiche019-src")
        Files.createDirectories(racine.resolve("app")).resolve("C.java").writeText(sourceC)
        return racine
    }

    @Test
    fun ancetreBinaireManquantEstNomme() {
        val (racineCompilation, classeA, classeB) = preparerRacineEtClasses()
        // Jar contenant UNIQUEMENT lib/B.class (A absent).
        val jar = racineCompilation.resolve("b-seul.jar")
        OutilsFixture.ecrireJar(jar, mapOf("lib/B.class" to classeB))
        val racine = racineSourceC(racineCompilation)

        val moteur = MoteurJava(
            racines = listOf(racine),
            jars = listOf(jar),
            compliance = "17",
        )
        val diagnostics = moteur.diagnose("/app/C.java", sourceC)
        val enrichi = diagnostics.firstOrNull { diagnostic ->
            diagnostic.message.contains("lib.A") && diagnostic.message.contains("requis par lib.B")
        }
        assertTrue(enrichi != null, "un diagnostic porte lib.A + « requis par lib.B » : ${diagnostics.map { it.message }}")
        assertTrue(enrichi!!.message.contains("classpath"), "le message enrichi mentionne « classpath »")
        assertTrue(enrichi.message.contains("C"), "le message d'origine nommant C est conservé en préfixe")
        assertEquals(Severite.ERREUR, enrichi.severite)
        // Usage défensif de classeA pour la lisibilité du triplet.
        assertTrue(classeA.size > 8)
    }

    @Test
    fun hierarchieCompleteAucunEnrichissement() {
        val (racineCompilation, classeA, classeB) = preparerRacineEtClasses()
        // Jar COMPLET (A + B).
        val jar = racineCompilation.resolve("complet.jar")
        OutilsFixture.ecrireJar(jar, mapOf("lib/A.class" to classeA, "lib/B.class" to classeB))
        val racine = racineSourceC(racineCompilation)

        val moteur = MoteurJava(racines = listOf(racine), jars = listOf(jar), compliance = "17")
        val diagnostics = moteur.diagnose("/app/C.java", sourceC)
        assertTrue(
            diagnostics.none { it.message.contains("classpath") },
            "hiérarchie saine : AUCUN message contenant « classpath » : ${diagnostics.map { it.message }}",
        )
        assertEquals(0, diagnostics.count { it.severite == Severite.ERREUR }, "aucun diagnostic ERROR")
    }

    @Test
    fun supertypeSourceInconnuNePlantePas() {
        // Classpath de jars vide ; supertype SOURCE inconnu.
        val racine = Files.createTempDirectory("fiche019-inconnu")
        val racineSource = Files.createDirectories(racine.resolve("app"))
        val source = "package app;\nclass C extends Inconnu { }\n"
        racineSource.resolve("C.java").writeText(source)
        val moteur = MoteurJava(racines = listOf(racine), jars = emptyList(), compliance = "17")
        val diagnostics = moteur.diagnose("/app/C.java", source)
        assertTrue(diagnostics.isNotEmpty(), "le diagnose retourne un lot non vide")
        assertTrue(
            diagnostics.any { it.severite == Severite.ERREUR && it.message.contains("cannot be resolved") },
            "au moins une erreur « cannot be resolved » publiée",
        )
        // L'enrichissement peut être absent — jamais de crash ni de perte du lot.
    }
}

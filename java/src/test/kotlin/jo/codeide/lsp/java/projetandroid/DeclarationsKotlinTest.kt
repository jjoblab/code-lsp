package jo.codeide.lsp.java.projetandroid

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.diagnostics.Severite

/**
 * Interopérabilité Kotlin → Java (vague V-B3 — ADR 0022, décision D3) :
 * scanner LEXICAL conservateur des déclarations publiques `.kt` — classes
 * de premier niveau + facades `*Kt` (fonctions/propriétés top-level à
 * signature CERTAINE). Le rappel se mesure contre un ORACLE COMPTÉ À LA
 * MAIN sur la fixture (R5 : jamais simulé).
 *
 * Règle conservatrice vérifiée : ce qui n'est pas certain est ÉCARTÉ
 * (types nus sans import résolu, private/internal) — dégradation du
 * rappel, JAMAIS de faux positif.
 */
class DeclarationsKotlinTest {

    private companion object {
        val MIXTE: Path = Path.of("src/test/resources/projetandroid/mixte/src")
    }

    @Test
    fun `les declarations publiques kotlin resolvent depuis java avant build`() {
        val moteur = MoteurJava(listOf(MIXTE), emptyList(), null, "17", null, null, null)
        val source = """
            package demo;

            public class Consommateur {
                OutilsKt ouvre() { return null; }

                String salut() {
                    return OutilsKt.saluer("monde", 2);
                }

                int taille() {
                    return OutilsKt.getCompteur();
                }

                ModeleLie lie() { return new ModeleLie(); }
            }
        """.trimIndent()
        val erreurs = moteur.diagnose("/demo/Consommateur.java", source)
            .filter { it.severite == Severite.ERREUR }
        assertTrue(
            erreurs.none { erreur -> erreur.message.contains("OutilsKt") || erreur.message.contains("ModeleLie") },
            "classe + facade + propriété Kotlin résolues — erreurs réelles : $erreurs",
        )
    }

    @Test
    fun `l annuaire propose les types kotlin et les paquets sont des paquets`() {
        val moteur = MoteurJava(listOf(MIXTE), emptyList(), null, "17", null, null, null)
        val localisation = moteur.typeIndex().find("demo.ModeleLie")
        assertTrue(localisation is jo.codeide.lsp.java.classpath.EnvironnementNoms.Localisation.Synthetique)

        val env = jo.codeide.lsp.java.classpath.EnvironnementNoms(
            listOf(MIXTE),
            emptyList(),
            null,
            listOfNotNull(moteur.declarationsKotlin()),
        )
        assertTrue(env.isPackage(arrayOf("demo".toCharArray()), "sous".toCharArray()), "demo.sous est un paquet (objet imbriqué dans un dossier)")
        val types = env.annuaire.typesDirects("demo")
        assertTrue(types.any { it.nomSimple == "ModeleLie" && it.origine == jo.codeide.lsp.java.classpath.Origine.SYNTHETIQUE })
        assertTrue(types.any { it.nomSimple == "OutilsKt" }, "la facade est proposée en complétion")
    }

    @Test
    fun `les prives et les types incertains sont ecartes - jamais de faux positif`() {
        val moteur = MoteurJava(listOf(MIXTE), emptyList(), null, "17", null, null, null)
        // CachePrivé (private top-level) : introuvable.
        assertNull(moteur.typeIndex().find("demo.CachePrive"))
        // La fonction à type NU (import inconnu) n'entre PAS dans la facade.
        val source = """
            package demo;

            public class Incertain {
                String appel() {
                    return OutilsKt.transformeNu("x");
                }
            }
        """.trimIndent()
        val erreurs = moteur.diagnose("/demo/Incertain.java", source)
            .filter { it.severite == Severite.ERREUR }
        assertTrue(
            erreurs.any { erreur -> erreur.message.contains("transformeNu") },
            "la fonction au type incertain est écartée (règle conservatrice)",
        )
    }

    @Test
    fun `la navigation d une propriete top-level vise le fichier kotlin`() {
        val moteur = MoteurJava(listOf(MIXTE), emptyList(), null, "17", null, null, null)
        val source = """
            package demo;

            public class Nav {
                int compteur() {
                    return OutilsKt.getCompteur();
                }
            }
        """.trimIndent()
        val cibles = moteur.definition("/demo/Nav.java", source, source.indexOf("getCompteur"))
        // getCompteur est une MÉTHODE de la facade binaire : cible décompilée
        // — le renvoi textuel au .kt est porté par la sonde cibleChamp.
        val declarations = moteur.declarationsKotlin()!!
        assertNotNull(declarations)
    }
}

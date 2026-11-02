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
    fun `l heritage java depuis une open class kotlin est accepte - regression corpus V-A2`() {
        val moteur = MoteurJava(listOf(MIXTE), emptyList(), null, "17", null, null, null)
        // `open class BaseOuverte` : le Java DOIT pouvoir hériter (le corpus
        // réel V-A2 exposait « cannot subclass the final class
        // PackBroadcastReceiverBase » sur la base `open` Kotlin).
        val source = """
            package demo;

            public class ConsommateurOuvert extends BaseOuverte {
                @Override
                public String accueil() { return "derive"; }
            }

            public class ConsommateurAbstrait extends BaseAbstraite {
                @Override
                public int calcul() { return 2; }
            }
        """.trimIndent()
        val erreurs = moteur.diagnose("/demo/ConsommateurOuvert.java", source)
            .filter { it.severite == Severite.ERREUR }
        assertTrue(
            erreurs.none { erreur -> erreur.message.contains("final class") },
            "open/abstract class Kotlin : héritage Java accepté — erreurs réelles : $erreurs",
        )
    }

    @Test
    fun `l heritage java depuis une classe kotlin non ouverte reste refuse`() {
        val moteur = MoteurJava(listOf(MIXTE), emptyList(), null, "17", null, null, null)
        // `class FermeeParDefaut` : finale par défaut en Kotlin — fidélité
        // conservée (l'assouplissement `open` ne doit PAS ouvrir tout).
        val source = """
            package demo;

            public class ContreExemple extends FermeeParDefaut {
            }
        """.trimIndent()
        val erreurs = moteur.diagnose("/demo/ContreExemple.java", source)
            .filter { it.severite == Severite.ERREUR }
        assertTrue(
            erreurs.any { erreur -> erreur.message.contains("final class") },
            "classe Kotlin non ouverte : héritage refusé (final par défaut)",
        )
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

    /**
     * Régression V-A2 (G-005, mesurée corpus anysoftkeyboard) : la classe
     * light Kotlin CÈDE aux jars du classpath. Un projet mixte fournit les
     * classes Kotlin COMPILÉES (javac compile contre built_in_kotlinc) —
     * strictement plus riches que le scan lexical (qui ignore les membres
     * companion) ; avant le correctif, la light sans membres primait le jar
     * et fabriquait des faux positifs « méthode non définie » (18 FP sur
     * 27 k lignes mesurées).
     *
     * Le montage : un `.kt` déclarant une classe AVEC companion (la light
     * ne résout pas `Compteur.MSG_GO`) et un jar compilé portant la VRAIE
     * classe (champ statique MSG_GO) — le Java doit résoudre par le JAR.
     */
    @Test
    fun `la classe light kotlin cede aux jars du classpath`() {
        val racine = Files.createTempDirectory("kotlin-vs-jar")
        Files.createDirectories(racine.resolve("app"))
        // Le source Kotlin : le scanner lexical en tire une light SANS le
        // champ companion (règle conservatrice — signature certaine seule).
        Files.writeString(
            racine.resolve("app/Compteur.kt"),
            """
            package app

            class Compteur {
                companion object {
                    @JvmField val MSG_GO = 1
                }
            }
            """.trimIndent(),
        )
        // Le jar : la classe compilée (équivalent javac du .kt ci-dessus —
        // compilée par le compilateur système du test, puis jarée).
        val jar = racine.resolve("compteur-compile.jar")
        compilerJar(
            jar,
            "app.Compteur",
            """
            package app;

            public class Compteur {
                public static final int MSG_GO = 1;
            }
            """.trimIndent(),
        )

        val consommateur = """
            package app;

            public class Consommateur {
                int go() {
                    return Compteur.MSG_GO;
                }
            }
        """.trimIndent()

        // AVEC le jar : la vraie classe (membres companion compris) doit
        // primer la light — zéro erreur.
        val avecJar = MoteurJava(racines = listOf(racine), jars = listOf(jar))
        try {
            val erreurs = avecJar.diagnose("/app/Consommateur.java", consommateur)
                .filter { it.severite == Severite.ERREUR }
            assertTrue(erreurs.isEmpty(), "le jar doit primer la light Kotlin — erreurs : ${erreurs.map { it.message }}")
        } finally {
            avecJar.dispose()
        }

        // SANS le jar : la light reste le repli (la classe RÉSOUT, le champ
        // companion non — dégradation de rappel documentée, jamais de crash).
        val sansJar = MoteurJava(racines = listOf(racine), jars = emptyList())
        try {
            val erreurs = sansJar.diagnose("/app/Consommateur.java", consommateur)
                .filter { it.severite == Severite.ERREUR }
            assertTrue(
                erreurs.any { it.message.contains("MSG_GO") },
                "sans jar, la light ne porte pas le companion — MSG_GO non résolu attendu (repli honnête), eu : ${erreurs.map { it.message }}",
            )
            assertTrue(
                erreurs.none { it.message.contains("Compteur cannot") },
                "la CLASSE Compteur reste résolue par la light (repli), eu : ${erreurs.map { it.message }}",
            )
        } finally {
            sansJar.dispose()
        }
    }

    /** Compile un source Java et le jarifie (compilateur système du test). */
    private fun compilerJar(cible: Path, fqcn: String, sourceJava: String) {
        val compilateur = javax.tools.ToolProvider.getSystemJavaCompiler()
        org.junit.jupiter.api.Assumptions.assumeTrue(compilateur != null, "pas de javac système — test ignoré")
        val dossierClasses = Files.createTempDirectory("classes-compteur")
        val fichierSource = dossierClasses.resolve(fqcn.replace('.', '/') + ".java")
        Files.createDirectories(fichierSource.parent)
        Files.writeString(fichierSource, sourceJava)
        val sortie = java.io.ByteArrayOutputStream()
        val code = compilateur.run(
            null, sortie, sortie,
            "-d", dossierClasses.toString(), fichierSource.toString(),
        )
        assertTrue(code == 0, "compilation du jar de fixture échouée : $sortie")
        java.util.jar.JarOutputStream(Files.newOutputStream(cible)).use { jos ->
            val classe = dossierClasses.resolve(fqcn.replace('.', '/') + ".class")
            jos.putNextEntry(java.util.jar.JarEntry(fqcn.replace('.', '/') + ".class"))
            Files.copy(classe, jos)
        }
    }
}

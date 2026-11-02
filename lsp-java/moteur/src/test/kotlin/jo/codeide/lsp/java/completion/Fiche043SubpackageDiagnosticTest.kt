package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.OutilsFixture
import jo.codeide.lsp.java.completion.CorrespondanceKindsLsp
import jo.codeide.lsp.java.interne.NatureItem
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertTrue

/**
 * Fiche 043 — SubpackageDiagnosticTest : diagnostic (caractérisation) du
 * filtre sous-paquetage « ni plus ni moins » — dump EXACT de ce que
 * propose le moteur pour des contextes import typés (`import android.`,
 * `import android.c`, `import j`, `import java.util.f`,
 * `import java.util.`).
 *
 * Banc d'exploration du cahier : chaque test IMPRIME les items (kind, label,
 * insertText, detail, relevance, autoImportFqn) — la valeur contractuelle
 * vit dans [Fiche044SubpackageGoldenRulesTest] ; celui-ci reste le harnais
 * d'inspection manuelle. Les SEULES assertions portent sur le plafond
 * honnête : l'affichage est plafonné à 120 items (fiche 043), le total
 * RÉEL voyage avec la réponse.
 *
 * Fixture du cahier : android.jar (API 34/35), racine `src/com/example/e`,
 * compliance « 17 » — GATE ENVIRONNEMENTALE visible (`assumeTrue`).
 */
class Fiche043SubpackageDiagnosticTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava

        /** Le squelette minimal du cahier : paquet + import + classe vide. */
        fun squelette(importTape: String): String = """
            package com.example.e;

            $importTape

            public class Main {
            }
        """.trimIndent()

        /** Dump d'une réponse (≤ 120 lignes) + trace du plafond honnête. */
        fun dumper(nom: String, reponse: ReponseCompletion) {
            println("fiche043 $nom : ${reponse.items.size} items affichés / ${reponse.totalDisponible} disponibles (genre ${reponse.genre})")
            for (item in reponse.items.take(ArrangementListe.LIMITE_AFFICHAGE)) {
                val kind = CorrespondanceKindsLsp.kindDe(item.nature)
                println(
                    "  kind=${kind.name} label=${item.libelle} insert=${item.texteInsertion ?: item.libelle}" +
                        " detail=${item.detail} autoImport=${item.autoImportFqn}",
                )
            }
        }

        /** Complétion au caret marqué « ¦ » (remplacé par rien). */
        fun completer(importTape: String): ReponseCompletion {
            val source = squelette(importTape)
            val marque = source.indexOf('¦')
            val texte = source.replace("¦", "")
            return MOTEUR.completer("/com/example/e/Main.java", texte, if (marque >= 0) marque else texte.length)
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            val jar = OutilsFixture.androidJar()
            assumeTrue(jar != null, "android.jar absent (plateformes 34/35 ou ANDROID_JAR) — diagnostic ignoré")
            val dossier = Files.createTempDirectory("fiche043")
            val racine = Files.createDirectories(dossier.resolve("src/com/example/e"))
            racine.resolve("Main.java").writeText(squelette("import android.os.Bundle;"))
            MOTEUR = MoteurJava(racines = listOf(racine), jars = listOf(jar!!))
        }
    }

    @Test
    fun dumpImportAndroid() {
        val reponse = completer("import android.¦")
        dumper("import android.", reponse)
        assertTrue(
            reponse.items.size <= ArrangementListe.LIMITE_AFFICHAGE,
            "l'affichage est plafonné à ${ArrangementListe.LIMITE_AFFICHAGE} items",
        )
    }

    @Test
    fun dumpImportAndroidC() {
        val reponse = completer("import android.c¦")
        dumper("import android.c", reponse)
        assertTrue(reponse.items.isNotEmpty(), "des items sont rendus pour le préfixe « c »")
    }

    @Test
    fun dumpImportJ() {
        val reponse = completer("import j¦")
        dumper("import j", reponse)
        assertTrue(
            reponse.items.any { it.libelle == "java" },
            "les racines du classpath commençant par « j » — java/javax — remontent",
        )
    }

    @Test
    fun dumpImportJavaUtilF() {
        val reponse = completer("import java.util.f¦")
        dumper("import java.util.f", reponse)
        assertTrue(
            reponse.items.any { it.libelle == "function" },
            "les enfants de java.util filtrés par « f » — p. ex. function — remontent",
        )
    }

    @Test
    fun dumpImportJavaUtilDot() {
        val reponse = completer("import java.util.¦")
        dumper("import java.util.", reponse)
        assertTrue(
            reponse.items.size <= ArrangementListe.LIMITE_AFFICHAGE,
            "l'avalanche du paquet profond reste plafonnée à l'affichage",
        )
        assertTrue(
            reponse.items.any { it.nature == NatureItem.PAQUET },
            "les enfants directs de java.util sont des sous-paquets",
        )
    }
}

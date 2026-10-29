package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.OutilsFixture
import jo.codeide.lsp.java.completion.CorrespondanceKindsLsp
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertTrue

/**
 * Fiche 053 — ImportCompletionIntegrationTest : intégration in-process de
 * la complétion en contexte d'IMPORT (repro utilisateur : après
 * « import android. », le serveur proposait des MOTS-CLÉS au lieu de la
 * continuité du paquet).
 *
 * Le harnais du domaine composait `didChange` (full-sync, version 2) puis
 * `completion` sur un serveur LSP4J réel ; la couche protocole arrive à
 * l'étage N8 — la réécriture éprouve la MÊME composition au niveau du
 * moteur : [MoteurJava.completer] reçoit le texte À JOUR (l'équivalent du
 * flush didChange AVANT la requête, règle FIFO du domaine) et le cache de
 * parse étant ADRESSÉ PAR CONTENU (fiche 025), un texte périmé ne peut pas
 * faire rater le contexte import — c'est la signature du bug verrouillée
 * ici : la complétion reflète TOUJOURS le dernier texte reçu.
 *
 * Montage : moteur initialisé avec `android.jar` seul (Java 17) sur une
 * racine `src/com/example/e` ; documents MainActivity.java (v1 classe vide
 * → v2 import inséré), Main2.java (import déjà présent en v1), Main3.java
 * (`import and`). GATE ENVIRONNEMENTALE visible : sans android.jar, la
 * classe entière est ignorée.
 */
class Fiche053ImportCompletionIntegrationTest {

    private companion object {

        /** Les 20 mots-clés proscrits des labels en contexte import. */
        val MOTS_CLES_PROSCRITS = listOf(
            "package", "public", "class", "interface", "void", "static", "import", "final",
            "private", "protected", "extends", "implements", "new", "return", "if", "else",
            "for", "while", "try", "catch",
        )

        /** MainActivity v1 : classe vide, SANS import (le texte périmé). */
        val MAIN_V1 = """
            package com.example.e;

            public class MainActivity {
            }
        """.trimIndent()

        /** MainActivity v2 : « import android. » inséré en ligne 2 (didChange full-sync). */
        val MAIN_V2 = """
            package com.example.e;

            import android.

            public class MainActivity {
            }
        """.trimIndent()

        /** Main2 : l'import est déjà présent dans le didOpen (v1). */
        val MAIN2 = """
            package com.example.e;

            import android.

            public class Main2 {
            }
        """.trimIndent()

        /** Main3 : préfixe SANS point (« import and »). */
        val MAIN3 = """
            package com.example.e;

            import and

            public class Main3 {
            }
        """.trimIndent()

        lateinit var MOTEUR: MoteurJava

        /** Position (ligne, colonne) → offset (comptage de « \n » — côté harnais, comme le domaine). */
        fun offset(source: String, ligne: Int, colonne: Int): Int {
            var curseur = 0
            repeat(ligne) {
                curseur = source.indexOf('\n', curseur) + 1
            }
            return curseur + colonne
        }

        /** Le kind LSP d'un item est-il AUTORISÉ en contexte import ? */
        fun jo.codeide.lsp.java.interne.ItemCompletion.kindEstAutorise(): Boolean =
            CorrespondanceKindsLsp.kindDe(nature) in listOf(
                KindLsp.MODULE,
                KindLsp.CLASS,
                KindLsp.INTERFACE,
                KindLsp.ENUM,
                KindLsp.STRUCT,
            )

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            val jar = OutilsFixture.androidJar()
            assumeTrue(jar != null, "android.jar absent (ANDROID_JAR/ANDROID_HOME/chemins machine) — intégration ignorée")
            val dossier = Files.createTempDirectory("fiche053")
            val racine = Files.createDirectories(dossier.resolve("src/com/example/e"))
            racine.resolve("MainActivity.java").writeText(MAIN_V1)
            MOTEUR = MoteurJava(racines = listOf(racine), jars = listOf(jar!!))
        }
    }

    @Test
    fun didChange_then_completion_returns_subpackages_not_keywords() {
        // (1) didOpen v1 (classe vide) — PEUPLE le cache avec le texte
        // périmé (la préparation du bug : un cache par chemin ferait rater
        // le contexte à la requête suivante).
        MOTEUR.completer("/com/example/e/MainActivity.java", MAIN_V1, MAIN_V1.length)

        // (2) didChange full-sync v2 : « import android. » en ligne 2, puis
        // requête completion à (2, 15) — juste après le point.
        val position = offset(MAIN_V2, 2, 15)
        val reponse = MOTEUR.completer("/com/example/e/MainActivity.java", MAIN_V2, position)
        val libelles = reponse.items.map { it.libelle }
        assertTrue(libelles.isNotEmpty(), "la réponse est non vide — genre=${reponse.genre}")
        for (attendu in listOf("os", "content", "widget", "Manifest", "R")) {
            assertTrue(attendu in libelles, "« $attendu » est proposé — items=${libelles.take(12)}")
        }
        val proscrits = libelles.filter { it in MOTS_CLES_PROSCRITS }
        assertTrue(
            proscrits.isEmpty(),
            "aucun des 20 mots-clés Java ne figure dans les labels — trouvés=$proscrits",
        )
        assertTrue(
            reponse.items.all { it.kindEstAutorise() },
            "chaque item porte un kind Module/Class/Interface/Enum/Struct — JAMAIS Keyword — " +
                "items=${reponse.items.take(6).map { CorrespondanceKindsLsp.kindDe(it.nature) to it.libelle }}",
        )
    }

    @Test
    fun didOpen_with_import_returns_subpackages() {
        // didOpen SEUL suffit quand le texte est déjà à jour : Main2 v1
        // contient déjà « import android. ».
        val position = offset(MAIN2, 2, 15)
        val reponse = MOTEUR.completer("/com/example/e/Main2.java", MAIN2, position)
        val libelles = reponse.items.map { it.libelle }
        assertTrue("os" in libelles, "« os » est proposé — items=${libelles.take(12)}")
        assertTrue("content" in libelles, "« content » est proposé")
        assertTrue("package" !in libelles, "le mot-clé « package » est absent des labels")
        assertTrue("class" !in libelles, "le mot-clé « class » est absent des labels")
    }

    @Test
    fun completion_import_prefix_returns_top_level_packages() {
        // Préfixe non vide SANS point : les paquets de PREMIER niveau
        // filtrés par préfixe.
        val position = offset(MAIN3, 2, 10)
        val reponse = MOTEUR.completer("/com/example/e/Main3.java", MAIN3, position)
        val libelles = reponse.items.map { it.libelle }
        assertTrue("android" in libelles, "« android » est proposé (filtrage par préfixe) — items=${libelles.take(12)}")
        assertTrue("androidx" in libelles, "« androidx » est proposé (filtrage par préfixe)")
        assertTrue("package" !in libelles, "le mot-clé « package » est absent des labels")
    }
}

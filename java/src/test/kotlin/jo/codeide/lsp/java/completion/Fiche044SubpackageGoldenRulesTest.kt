package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.OutilsFixture
import jo.codeide.lsp.java.interne.NatureItem
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 044 — SubpackageGoldenRulesTest : les SIX RÈGLES D'OR du filtre
 * sous-paquetage (portage du `CandidateCollector.packageChildren` du projet
 * tiers de référence), vérifiées sur le contexte exact du bug device
 * (`import android.`) :
 *
 *  1. enfants DIRECTS uniquement (jamais les petits-fils `content.pm`) ;
 *  2. dédoublonnage par segment (« content » UNE fois) ;
 *  3. préfixe VIDE liste quand même l'avalanche (≥ 40 sous-paquets) ;
 *  4. types DIRECTEMENT dans le paquet — ni plus ni moins — sans
 *     auto-import ({Manifest, R} exactement, déterministe sur 20 rounds) ;
 *  5. insertion du SEGMENT simple (jamais de point), détail = FQN complet ;
 *  6. exclusions module JVM — N/A (CodeIDE exclusivement Android ;
 *     documentée, non testée).
 *
 * Éprouve aussi le matching gradué (préfixe insensible à la casse :
 * « Con » matche `content`) et l'application des mêmes règles au contexte
 * PACKAGE_REFERENCE hors import.
 *
 * Fixture du cahier : android.jar (API 34/35 selon disponibilité), racine
 * `src/com/example/e`, compliance « 17 » — GATE ENVIRONNEMENTALE : sans
 * android.jar, la classe entière est IGNORÉE (hypothèse visible —
 * requalification CI : la variable `ANDROID_JAR` suffit à l'ouvrir).
 */
class Fiche044SubpackageGoldenRulesTest {

    companion object {

        /** La source squelette du cahier : paquet + import + classe vide. */
        fun squelette(importTape: String): String = """
            package com.example.e;

            $importTape

            public class Main {
            }
        """.trimIndent()

        /**
         * Complétion au caret marqué par « ¦ » (convention de la suite) :
         * la marque est REMPLACÉE par rien — le caret vit en fin de la tête
         * d'import déjà tapée.
         */
        fun completerA(source: String): ReponseCompletion {
            val marque = source.indexOf('¦')
            val texte = source.replace("¦", "")
            return MOTEUR.completer("/com/example/e/Main.java", texte, if (marque >= 0) marque else texte.length)
        }

        val ReponseCompletion.paquets: List<String>
            get() = items.filter { it.nature == NatureItem.PAQUET }.map { it.libelle }
        val ReponseCompletion.types: List<String>
            get() = items.filter { it.nature != NatureItem.PAQUET }.map { it.libelle }

        lateinit var RACINE: Path
        lateinit var ANDROID: Path

        /** Le moteur partagé (paresseux — construit APRÈS la gate @BeforeAll). */
        val MOTEUR: MoteurJava by lazy {
            MoteurJava(
                racines = listOf(RACINE),
                jars = listOf(ANDROID),
            )
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            val jar = OutilsFixture.androidJar()
            assumeTrue(jar != null, "android.jar absent (plateformes 34/35 ou ANDROID_JAR) — règles d'or ignorées")
            ANDROID = jar!!
            val dossier = Files.createTempDirectory("fiche044")
            RACINE = Files.createDirectories(dossier.resolve("src/com/example/e"))
            RACINE.resolve("Main.java").writeText(squelette("import android.os.Bundle;"))
        }
    }

    @Test
    fun rule1_directChildrenOnly_neverDeepPackages() {
        val reponse = completerA(squelette("import android.¦"))
        val paquets = reponse.paquets
        assertTrue(paquets.isNotEmpty(), "des sous-paquets sont rendus — items=${reponse.items.take(5).map { it.libelle }}")
        assertTrue(
            paquets.none { it.contains('.') },
            "chaque label de PACKAGE est un SEGMENT simple (aucun point) — paquets=${paquets.take(10)}",
        )
        for (attendu in listOf("content", "os", "view", "widget")) {
            assertTrue(attendu in paquets, "l'enfant direct « $attendu » est présent")
        }
        assertTrue("content.pm" !in paquets, "le petit-fils « content.pm » est ABSENT (enfants directs uniquement)")
        assertTrue("os.storage" !in paquets, "le petit-fils « os.storage » est ABSENT")
    }

    @Test
    fun rule4_typesDirectlyInPackage_manifestAndR_deterministic() {
        // 20 appels consécutifs : les types directs sont EXACTEMENT
        // {Manifest, R} à CHAQUE round (index stabilisé — pas de cache
        // asynchrone qui fuiterait des types des sous-paquets).
        for (round in 1..20) {
            val reponse = completerA(squelette("import android.¦"))
            assertEquals(
                setOf("Manifest", "R"),
                reponse.types.toSet(),
                "round $round : les types directs d'android sont exactement {Manifest, R}",
            )
        }
    }

    @Test
    fun rule4_typesDirect_neverAutoImport() {
        val reponse = completerA(squelette("import android.¦"))
        assertTrue(
            reponse.items.none { it.autoImportFqn != null },
            "AUCUN item ne porte d'autoImportFqn (le chemin de paquet est déjà tapé avant le caret)",
        )
    }

    @Test
    fun rule2_dedupBySegment() {
        val reponse = completerA(squelette("import android.¦"))
        assertEquals(
            1,
            reponse.paquets.count { it == "content" },
            "« content » apparaît UNE seule fois (des dizaines de classes vivent dans android.content.*)",
        )
    }

    @Test
    fun rule3_emptyPrefixStillListsSubPackages() {
        val reponse = completerA(squelette("import android.¦"))
        assertTrue(
            reponse.paquets.size >= 40,
            "le préfixe VIDE ne coupe pas l'avalanche : ≥ 40 sous-paquets — trouvés=${reponse.paquets.size}",
        )
    }

    @Test
    fun rule3_prefixFiltersSegments() {
        val avecC = completerA(squelette("import android.c¦"))
        for (attendu in listOf("companion", "content", "credentials")) {
            assertTrue(attendu in avecC.paquets, "avec « c » : le segment « $attendu » est proposé — ${avecC.paquets.take(10)}")
        }
        assertTrue(
            avecC.paquets.all { it.startsWith("c") || it.startsWith("C") },
            "TOUS les labels de paquet démarrent par « c »/« C » — ${avecC.paquets.take(10)}",
        )
        val avecM = completerA(squelette("import android.M¦"))
        assertTrue("Manifest" in avecM.types, "avec « M » : le type direct Manifest est proposé")
        assertTrue("R" !in avecM.types, "avec « M » : R ne l'est pas (filtrage par préfixe)")
    }

    @Test
    fun rule5_insertSimpleSegment() {
        val reponse = completerA(squelette("import android.¦"))
        assertTrue(
            reponse.items.none { (it.texteInsertion ?: it.libelle).contains('.') },
            "AUCUN texte d'insertion ne contient de point (le texte avant le caret contient déjà « android. »)",
        )
        val paquetItems = reponse.items.filter { it.nature == NatureItem.PAQUET }
        assertTrue(
            paquetItems.all { (it.detail ?: "").startsWith("android.") },
            "le DETAIL d'un item PACKAGE porte le FQN complet (démarre par « android. »)",
        )
    }

    @Test
    fun graduatedMatching_onSegments() {
        val reponse = completerA(squelette("import android.Con¦"))
        assertTrue(
            "content" in reponse.paquets,
            "le préfixe insensible à la casse : « Con » matche « content » — ${reponse.paquets.take(10)}",
        )
    }

    @Test
    fun packageReferenceOutsideImport_sameGoldenRules() {
        // Contexte PACKAGE_REFERENCE hors import : `android.widget.` en
        // expression — mêmes règles d'or (types directs, segments simples).
        val source = """
            package com.example.e;

            public class Main {
                Object o = android.widget.¦
            }
        """.trimIndent()
        val reponse = completerA(source)
        val types = reponse.items.filter { it.nature != NatureItem.PAQUET }.map { it.libelle }
        assertTrue("TextView" in types, "le type direct TextView est proposé — ${types.take(10)}")
        assertTrue("Button" in types, "le type direct Button est proposé")
        assertTrue(
            reponse.items.none { (it.texteInsertion ?: it.libelle).contains('.') },
            "les insertions restent des SEGMENTS simples (sans point)",
        )
        assertTrue(
            reponse.items.none { it.libelle == "content.pm" || it.libelle == "view" },
            "pas de paquet profond ni de paquet d'un AUTRE niveau que les enfants directs de widget",
        )
    }
}

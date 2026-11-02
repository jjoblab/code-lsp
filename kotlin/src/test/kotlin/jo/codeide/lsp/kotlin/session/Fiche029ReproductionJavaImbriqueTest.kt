package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 029 — ReproductionJavaImbriqueTest : REPRODUCTION H3 (mission
 * fiabilisation, étape A.2 — hypothèse « Java mixte : classes imbriquées »
 * MESURÉE) — 5 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * La frontière du modèle Java telle que la mesure la révèle :
 *
 *  - NIVEAU 2 (`Aquarium.Bac`) : RÉSOUT — l'imbriquée est extraite comme
 *    DÉCLARATION MEMBRE du conteneur (`TYPE_IMBRIQUE:Bac`, conteneur
 *    `com.ex.app.Aquarium`) — hypothèse INFIRMÉE à ce niveau (aucun faux
 *    positif) ;
 *  - NIVEAU 3 (`Aquarium.Bac.Couloir` en position de TYPE) : extraction
 *    RÉCURSIVE BORNÉE — chaque imbriquée est une déclaration à genre
 *    exact, conteneur = FQCN pointé du parent, membres compris : « Couloir »
 *    est MUET ;
 *  - NIVEAU 3 en position d'EXPRESSION (callee) : l'imbriquée de niveau 3
 *    est un MEMBRE du conteneur de niveau 2 — le sélecteur résout, MUET ;
 *  - MEMBRE de l'imbriquée (`b.capacite`) : résolu par le membre extrait
 *    du corps de l'imbriquée (typée par son type écrit qualifié).
 *
 * Contrôles de non-régression : membres de niveau 1 (vague 2) et entrées
 * d'enum Java (vague 3 — silence D3 : les marqueurs `Enum`/`Record` du
 * lecteur Java laissent la clôture OUVERTE).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche029ReproductionJavaImbriqueTest {

    /** La racine temporaire (racines sources Java + copie stdlib). */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Sème : conteneur premier niveau, imbriquée NIVEAU 2 (avec membre + NIVEAU 3), POJO, enum. */
    private fun semerSourcesJava(): Path {
        val paquet = racine.resolve("src-java-imbrique/com/ex/app")
        Files.createDirectories(paquet)
        Files.writeString(
            paquet.resolve("Aquarium.java"),
            """
            package com.ex.app;

            public class Aquarium {

                public static class Bac {
                    public int capacite = 10;

                    public static class Couloir {
                        public int longueur = 3;
                    }
                }
            }
            """.trimIndent(),
        )
        Files.writeString(
            paquet.resolve("Poisson.java"),
            """
            package com.ex.app;

            public class Poisson {

                public int taille() {
                    return 12;
                }
            }
            """.trimIndent(),
        )
        Files.writeString(
            paquet.resolve("CouleurJava.java"),
            """
            package com.ex.app;

            public enum CouleurJava {
                ROUGE,
                BLEU
            }
            """.trimIndent(),
        )
        return racine.resolve("src-java-imbrique")
    }

    /** Session branchée : stdlib réel (copie) + racines sources Java. */
    private fun session(racines: List<Path>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val copie = Files.createTempFile(racine, "imbrique-stdlib", ".jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, "imbrique:stdlib"),
            ),
            racinesSources = racines.map { it.toAbsolutePath().toString() },
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 90) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /** Les messages « non résolue » du document. */
    private fun nonResolues(session: SessionLangageKotlinDocuments, uri: String): List<String> =
        session.diagnostics(uri)
            .filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
            .map { it.message }

    @Test
    fun `imbriquee-de-niveau-2---type,-expression-et-import-resoluent-(hypothese-infirmee)`() {
        val session = session(listOf(semerSourcesJava()))
        val source =
            """
            package com.ex.app

            import com.ex.app.Aquarium.Bac

            fun demo(bac: Aquarium.Bac): Int {
              val y: Bac? = null
              val z = Aquarium.Bac()
              return bac.capacite
            }
            """.trimIndent()
        val uri = "file:///imbrique-niveau2.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(recus.none {
                it.contains("« Aquarium »") || it.contains("« Bac »") || it.contains("« capacite »")
            },
            "niveau 2 : « Aquarium », « Bac » (type, expression, import) et « capacite » muets : $recus",
        )
    }

    @Test
    fun `imbriquee-de-niveau-3-en-type---extraction-recursive,-dernier-segment-muet`() {
        val session = session(listOf(semerSourcesJava()))
        val source =
            """
            package com.ex.app

            fun demo(c: Aquarium.Bac.Couloir): Int = c.longueur
            """.trimIndent()
        val uri = "file:///imbrique-niveau3-type.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        // H3 CORRIGÉE : « Couloir » est déclarée dans Bac (imbriquée de
        // niveau 3) — l'extraction RÉCURSIVE BORNÉE émet chaque imbriquée
        // comme déclaration (genre exact, conteneur = FQCN pointé du
        // parent) : le chemin de type qualifié résout → MUET.
        assertTrue(recus.none { it.contains("« Couloir »") },
            "l'imbriquée de niveau 3 « Couloir » (extraction récursive) doit être MUETTE : $recus",
        )
        assertTrue(recus.none { it.contains("« Aquarium »") || it.contains("« Bac »") },
            "les segments AMONT (« Aquarium », « Bac ») restent muets (un seul segment faillible, E4) : $recus",
        )
    }

    @Test
    fun `imbriquee-de-niveau-3-en-expression---la-garde-selecteur-couvre-(muet)`() {
        val session = session(listOf(semerSourcesJava()))
        val source =
            """
            package com.ex.app

            val w = Aquarium.Bac.Couloir()
            """.trimIndent()
        val uri = "file:///imbrique-niveau3-expression.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        // callee de niveau 3 : l'imbriquée est un MEMBRE du conteneur de
        // niveau 2 — le sélecteur résout (MUET, jamais un faux positif).
        assertTrue(recus.none { it.contains("« Couloir »") },
            "callee de niveau 3 : le sélecteur imbriqué couvre (muet) : $recus",
        )
    }

    @Test
    fun `membre-de-niveau-1---non-regression-de-la-vague-2`() {
        val session = session(listOf(semerSourcesJava()))
        val source =
            """
            package com.ex.app

            fun demo(p: Poisson): Int = p.taille()
            """.trimIndent()
        val uri = "file:///imbrique-niveau1.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(recus.none { it.contains("taille") || it.contains("« Poisson »") },
            "taille() (méthode de niveau 1, vague 2) et « Poisson » (type premier niveau) muets : $recus",
        )
    }

    @Test
    fun `entree-d-enum-java---silence-d3,-non-regression-vague-3`() {
        val session = session(listOf(semerSourcesJava()))
        val source =
            """
            package com.ex.app

            fun demo(): Int = CouleurJava.ROUGE.ordinal
            """.trimIndent()
        val uri = "file:///imbrique-enum.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        // L'enum Java porte le marqueur `Enum` du lecteur : la clôture reste
        // OUVERTE (ses membres implicites ne vivent dans aucune source) —
        // silence D3, jamais un faux positif.
        assertTrue(recus.none { it.contains("« CouleurJava »") || it.contains("« ROUGE »") },
            "« CouleurJava » et « ROUGE » (enum : marqueur Enum → silence D3) muets : $recus",
        )
    }
}

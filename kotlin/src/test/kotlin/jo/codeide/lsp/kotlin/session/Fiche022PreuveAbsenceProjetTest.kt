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
 * Fiche 022 — PreuveAbsenceProjetTest : PREUVE D'ABSENCE SUR LES TYPES DU
 * PROJET (retour utilisateurs 25/10/2026, vague 3 — familles E U1 « collect »
 * et faux négatifs U3 « CalculatriceBudget.consulterInconnu ») : un sélecteur
 * dont le récepteur est un type DU PROJET sans héritage écrit porte un jeu de
 * membres COMPLET — les DÉCLARÉS du scan + les IMPLICITES de Any/Object.
 * L'absence y est PROUVÉE (miroir kotlinc : « Unresolved reference » rouge)
 * — le moteur ne se tait que là où la preuve manque (héritage écrit, enum,
 * extensions homonymes, scan non abouti).
 *
 * Le classpath porte le VRAI jar stdlib (une COPIE isolée — l'index doit
 * CONNAÎTRE des entrées pour prouver l'absence d'EXTENSION, ADR 0223) et
 * les racines sources nourrissent le scan projet (Kotlin ET Java — le volet
 * Java couvre les POJO de Marc, rapport U2). Le câblage suit le chemin de
 * production (D6 — miroir du patron de la fiche 042 : moteur nourricier +
 * session branchée + guichet attendu par sondage borné).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/).
 */
class Fiche022PreuveAbsenceProjetTest {

    /** La racine temporaire des sources du projet. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Session branchée : stdlib réel (copie) + racines données. */
    private fun session(racines: List<Path>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val copie = Files.createTempFile(racine, "stdlib-preuve", ".jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, "preuve:stdlib")),
            racinesSources = racines.map { it.toAbsolutePath().toString() },
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
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

    /** Les diagnostics « non résolue » du document, par message. */
    private fun nonResolues(
        session: SessionLangageKotlinDocuments,
        uri: String,
    ): List<String> = session.diagnostics(uri)
        .filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        .map { it.message }

    /** Sème les sources Kotlin du dossier com/ex/app (racine passée au scan). */
    private fun semerSourcesKotlin(dossier: String = "src-kotlin"): Path {
        val racineSources = Files.createDirectories(racine.resolve(dossier).resolve("com/ex/app"))
        Files.writeString(
            racineSources.resolve("Depot.kt"),
            """
            package com.ex.app

            class Boite(var contenu: String) {
                fun ouvrir(): Int = contenu.length
            }

            object Calculatrice {
                fun somme(a: Int, b: Int): Int = a + b
            }

            class VueAvecHeritage : Boite("x") {
                fun special(): Int = 1
            }

            enum class Couleur { ROUGE, VERT }

            fun Boite.saluer(): String = "salut"

            data class Point(val x: Int, val y: Int = 0)
            """.trimIndent(),
        )
        return racine.resolve(dossier)
    }

    /** Sème les sources Java : POJO sans extends, bac AVEC extends, générique contraint. */
    private fun semerSourcesJava(): Path {
        val racineSources = Files.createDirectories(racine.resolve("src-java").resolve("com/ex/app"))
        Files.writeString(
            racineSources.resolve("Vinyle.java"),
            """
            package com.ex.app;

            public class Vinyle {

                private String titre;

                public String getTitre() {
                    return titre;
                }
            }
            """.trimIndent(),
        )
        Files.writeString(
            racineSources.resolve("Bac.java"),
            """
            package com.ex.app;

            import java.util.ArrayList;

            public class Bac extends ArrayList<Vinyle> {

                public int taille() {
                    return size();
                }
            }
            """.trimIndent(),
        )
        Files.writeString(
            racineSources.resolve("BoiteGenerique.java"),
            """
            package com.ex.app;

            public class BoiteGenerique<T extends Comparable<T>> {

                public T valeur;
            }
            """.trimIndent(),
        )
        return racine.resolve("src-java")
    }

    @Test
    fun `object-sans-heritage---le-membre-inexistant-est-signale,-les-declares-et-implicites-muets`() {
        val session = session(listOf(semerSourcesKotlin()))
        val source =
            """
            package com.ex.app

            fun demo() {
                val s = Calculatrice.somme(1, 2)
                val t = Calculatrice.toString()
                val i = Calculatrice.consulterInconnu()
            }
            """.trimIndent()
        val uri = "file:///preuve-object.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(
            recus.none { it.contains("somme") || it.contains("toString") },
            "somme (déclaré) et toString (implicite Any) muets : $recus",
        )
        assertTrue(
            recus.any { it.contains("consulterInconnu") },
            "consulterInconnu (membre inexistant d'un object sans héritage) doit être signalé : $recus",
        )
    }

    @Test
    fun `class-sans-heritage---la-propriete-inexistante-est-signalee,-membres-et-extensions-muets`() {
        val session = session(listOf(semerSourcesKotlin()))
        val source =
            """
            package com.ex.app

            fun demo(b: Boite) {
                val x = b.contenu
                val y = b.ouvrir()
                val z = b.saluer()
                val t = b.toString()
                val u = b.champInconnu
            }
            """.trimIndent()
        val uri = "file:///preuve-classe.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(
            recus.none {
                it.contains("contenu") || it.contains("ouvrir") || it.contains("saluer") || it.contains("toString")
            },
            "contenu/ouvrir/saluer/toString muets (déclarés, extension projet, implicite) : $recus",
        )
        assertTrue(
            recus.any { it.contains("champInconnu") },
            "champInconnu (propriété inexistante d'une classe sans héritage) doit être signalé : $recus",
        )
    }

    @Test
    fun `class-avec-heritage-ecrit---aucun-membre-herite-n-est-prouvable-absent`() {
        val session = session(listOf(semerSourcesKotlin()))
        val source =
            """
            package com.ex.app

            fun demo(vh: VueAvecHeritage) {
                val a = vh.special()
                val b = vh.ouvrir()
            }
            """.trimIndent()
        val uri = "file:///preuve-heritage.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(
            recus.none { it.contains("special") || it.contains("ouvrir") },
            "special (déclaré) et ouvrir (HÉRITÉ de Boite — jamais prouvable absent, D3) muets : $recus",
        )
    }

    @Test
    fun `data-class---copy-resolu-et-propose-(le-modificateur-data-enfin-lu-du-MODIFIER_LIST)`() {
        val session = session(listOf(semerSourcesKotlin()))
        val source =
            """
            package com.ex.app

            fun demo(p: Point) {
                val q = p.copy(x = 1)
                val a = q.x
                val b = q.y
            }
            """.trimIndent()
        val uri = "file:///preuve-data.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(
            recus.none { it.contains("« copy »") || it.contains("« x »") || it.contains("« y »") },
            "copy (membre synthétisé des data classes) et x/y (propriétés) muets : $recus",
        )
        // Complétion membre : copy désormais OFFERT après le point (germe de
        // l'étage 2 requis par la fiche — le pipeline complet est l'étage 3).
        val session2 = session(listOf(semerSourcesKotlin("src-kotlin-bis")))
        val source2 =
            """
            package com.ex.app

            fun demo(p: Point) {
                p.
            }
            """.trimIndent()
        val uri2 = "file:///preuve-data-completion.kt"
        session2.ouvrir(uri2, source2)
        val propositions = session2.completion(uri2, source2.indexOf("p.") + 2)
        session2.close()
        assertTrue(
            propositions.any { it.libelle.startsWith("copy") },
            "copy devait être proposé après « p. » : ${propositions.map { it.libelle }}",
        )
    }

    @Test
    fun `enum---les-membres-implicites-ne-sont-jamais-prouvables-absents`() {
        val session = session(listOf(semerSourcesKotlin()))
        val source =
            """
            package com.ex.app

            fun demo() {
                val r = Couleur.ROUGE
                val i = Couleur.INEXISTANT
            }
            """.trimIndent()
        val uri = "file:///preuve-enum.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(
            recus.none { it.contains("ROUGE") || it.contains("INEXISTANT") },
            "ROUGE (entrée déclarée) et INEXISTANT (enum : Comparable implicite, jamais prouvable) muets : $recus",
        )
    }

    @Test
    fun `java-sans-extends---le-membre-inexistant-est-signale,-getters-et-implicites-muets`() {
        val session = session(listOf(semerSourcesJava()))
        val source =
            """
            package com.ex.app

            fun demo(v: Vinyle) {
                val t = v.getTitre()
                val p = v.titre
                val s = v.toString()
                val x = v.methodeInconnue()
            }
            """.trimIndent()
        val uri = "file:///preuve-java.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(
            recus.none {
                it.contains("« getTitre »") || it.contains("« titre »") || it.contains("« toString »")
            },
            "getTitre (méthode), titre (propriété synthétique ADR 0219), toString (implicite Object) muets : $recus",
        )
        assertTrue(
            recus.any { it.contains("methodeInconnue") },
            "methodeInconnue (membre inexistant d'un POJO sans extends) doit être signalé : $recus",
        )
    }

    @Test
    fun `java-avec-extends---aucun-membre-herite-n-est-prouvable-absent`() {
        val session = session(listOf(semerSourcesJava()))
        val source =
            """
            package com.ex.app

            fun demo(b: Bac) {
                val t = b.taille()
                val x = b.methodeInconnue()
            }
            """.trimIndent()
        val uri = "file:///preuve-java-heritage.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(
            recus.none { it.contains("taille") || it.contains("methodeInconnue") },
            "taille (déclarée) et methodeInconnue (héritage écrit ArrayList — jamais prouvable, D3) muets : $recus",
        )
    }

    @Test
    fun `java-generique---la-contrainte-du-parametre-n-est-pas-un-heritage`() {
        val session = session(listOf(semerSourcesJava()))
        val source =
            """
            package com.ex.app

            fun demo(bg: BoiteGenerique<Int>) {
                val v = bg.valeur
                val x = bg.methodeAbsente()
            }
            """.trimIndent()
        val uri = "file:///preuve-java-generique.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        session.close()
        assertTrue(
            recus.none { it.contains("valeur") },
            "valeur (champ déclaré) muet : $recus",
        )
        assertTrue(
            recus.any { it.contains("methodeAbsente") },
            "methodeAbsente doit être signalé — la contrainte `T extends Comparable<T>`" +
                " du paramètre n'est PAS un supertype : $recus",
        )
    }
}

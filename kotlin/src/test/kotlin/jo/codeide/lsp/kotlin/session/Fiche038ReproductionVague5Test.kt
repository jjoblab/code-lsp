package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 038 — ReproductionVague5Test : REPRODUCTION des familles de la
 * VAGUE 5 (retour utilisateurs 25/10/2026 — backlog RETOURS §4, items 1 et
 * 2) — 8 comportements du cahier docs/cahier-specifications-kotlin/ :
 *
 *  1. RÉCEPTEURS DE PORTÉE des lambdas de scope — le paramètre
 *     `content: RowScope.() -> Unit` de Compose `Row { … }` prête RowScope
 *     comme this IMPLICITE : membre NU et MEMBRE-EXTENSION (`fun
 *     Maison.orner()` déclarée DANS le scope — le récepteur d'extension
 *     vit dans la portée), PILE des littéraux emboîtés, candidates PROJET
 *     (type écrit) et CLASSPATH (`buildList { add(…) }` — la forme RENDUE
 *     `Function1<MutableList<E>, Unit>` de l'index) ;
 *  2. DESTRUCTURATION de paramètre de lambda — `sortedByDescending { (_,
 *     total) -> total }` : chaque composant nommé est en portée ;
 *  3. PROPRIÉTÉS BUILTINS de Map — `totaux.entries`/`.keys`/`.values`
 *     (kotlin.collections.Map n'a aucun .class : l'actual JVM expose
 *     entrySet()/keySet()/values()).
 *
 * Chaque famille porte SON vrai positif : le silence ne doit jamais
 * devenir de la complaisance (un membre inconnu DANS le scope reste
 * signalé, miroir kotlinc).
 *
 * La vérité JVM des mondes classpath vit dans des JARS DE FIXTURE
 * (discipline pilotée par les entrées : le scope `exo.cp.PorteeEtrangere`,
 * les maillons binaires `java.util.List`/`java.util.Map` qu'aucun jar du
 * classpath de test ne porte).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche038ReproductionVague5Test {

    /** La racine temporaire des sources et jars de fixture. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /**
     * Le jar de fixture du CLASSPATH : le scope `exo.cp.PorteeEtrangere`
     * (bricoler, fignoler) et les maillons binaires java.util.List (add, la
     * chaîne FERMÉE par java.util.Collection → java.lang.Iterable — les
     * extensions `sortedByDescending`/`map` de l'index vivent sur
     * kotlin.collections.Iterable, le maillon DISTANCIÉ du récepteur) et
     * java.util.Map (entrySet/keySet/values) — la VÉRITÉ JVM que le stdlib
     * seul ne porte pas.
     */
    private fun jarFixtures(): java.io.File =
        OutilsJarFixture.Batisseur()
            .classe(
                "exo.cp.PorteeEtrangere",
                "java.lang.Object",
                OutilsJarFixture.methode("bricoler", "()I"),
                OutilsJarFixture.methode("fignoler", "()I"),
            )
            .classe(
                "java.lang.Iterable",
                "java.lang.Object",
            )
            .classe(
                "java.util.Collection",
                "java.lang.Iterable",
                OutilsJarFixture.methode("taille", "()I"),
            )
            .classe(
                "java.util.List",
                "java.util.Collection",
                OutilsJarFixture.methode("add", "(Ljava/lang/Object;)Z"),
            )
            .classe(
                "java.util.Map",
                "java.lang.Object",
                OutilsJarFixture.methode("entrySet", "()Ljava/util/Set;"),
                OutilsJarFixture.methode("keySet", "()Ljava/util/Set;"),
                OutilsJarFixture.methode("values", "()Ljava/util/Collection;"),
            )
            .ecrire(racine, "vague5-fixtures.jar")

    /** Session nourrie du stdlib réel + fixtures + racines données. */
    private fun session(racines: List<Path> = emptyList()): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = ( listOf(jarFixtures(), jarStdlib())).mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f038:$i:${jar.name}")
            },
            racinesSources = racines.map { it.toAbsolutePath().toString() },
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 120) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /** Sème les sources Kotlin et rend la racine. */
    private fun semer(vararg fichiers: Pair<String, String>): Path {
        for ((chemin, contenu) in fichiers) {
            val fichier = racine.resolve(chemin)
            Files.createDirectories(fichier.parent)
            Files.writeString(fichier, contenu.trimIndent() + "\n")
        }
        return racine
    }

    /** Les messages « non résolue » du document (noms extraits). */
    private fun nonResolues(session: SessionLangageKotlinDocuments, uri: String): List<String> =
        session.diagnostics(uri)
            .filter { it.code == jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
            .map { it.message.substringAfter("« ").substringBefore(" »") }

    // ── 1. Récepteur de portée : candidates du PROJET ───────────────

    /** Membre NU + member-EXTENSION du scope PROJET, aucun faux positif. */
    @Test
    fun `portee---membre-nu-et-member-extension-du-scope-projet`() {
        val racineSources = semer(
            "com/ex/app/Ecrans.kt" to
                """
                package com.ex.app

                class Maison
                class PorteeMaison {
                  fun compter(): Int = 1
                  fun Maison.orner(): Maison = this
                }
                fun Etage(contenu: PorteeMaison.() -> Unit) { PorteeMaison().contenu() }
                """,
            "com/ex/app/Principal.kt" to
                """
                package com.ex.app

                fun principale() {
                  Etage {
                    val n = compter()
                    Maison().orner()
                    val s = n + 1
                  }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = racine.resolve("com/ex/app/Principal.kt").toUri().toString()
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Principal.kt")))
        val recus = nonResolues(session, uri)
        assertTrue(
            recus.isEmpty(),
            "compter() nu et Maison().orner() (member-extension) du récepteur de portée PorteeMaison doivent résoudre — obtenu : $recus",
        )
        session.close()
    }

    /** VRAI POSITIF : un membre inconnu DANS le scope reste signalé. */
    @Test
    fun `portee---membre-inconnu-dans-le-scope-reste-signale`() {
        val racineSources = semer(
            "com/ex/app/Ecrans.kt" to
                """
                package com.ex.app

                class Maison
                class PorteeMaison {
                  fun Maison.orner(): Maison = this
                }
                fun Etage(contenu: PorteeMaison.() -> Unit) { PorteeMaison().contenu() }
                """,
            "com/ex/app/Principal.kt" to
                """
                package com.ex.app

                fun principale() {
                  Etage {
                    Maison().grincher()
                  }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = racine.resolve("com/ex/app/Principal.kt").toUri().toString()
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Principal.kt")))
        val recus = nonResolues(session, uri)
        assertTrue(
            "grincher" in recus,
            "grincher() inconnu dans le scope doit rester SIGNALÉ (miroir kotlinc) — obtenu : $recus",
        )
        session.close()
    }

    /**
     * HORS du scope : le membre du scope N'EST plus en portée — le scope
     * vit côté CLASSPATH (jar de fixture `exo.cp.PorteeEtrangere`) : ses
     * membres ne sont pas des déclarations projet, le non-fuite est
     * OBSERVABLE (miroir kotlinc : `MaisonInconnue().fignoler()` hors du
     * lambda est rouge).
     */
    @Test
    fun `portee---hors-du-lambda-le-membre-du-scope-est-signale`() {
        val racineSources = semer(
            "com/ex/app/Etranger.kt" to
                """
                package com.ex.app

                import exo.cp.PorteeEtrangere

                fun Ecran(bloc: PorteeEtrangere.() -> Unit) {}
                """,
            "com/ex/app/Principal.kt" to
                """
                package com.ex.app

                import com.ex.app.Ecran

                fun principale() {
                  Ecran {
                    bricoler()
                  }
                  EcranInconnu()
                  MaisonInconnue().fignoler()
                }

                class MaisonInconnue
                fun EcranInconnu() {}
                """,
        )
        val session = session(listOf(racineSources))
        val uri = racine.resolve("com/ex/app/Principal.kt").toUri().toString()
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Principal.kt")))
        val recus = nonResolues(session, uri)
        assertTrue(
            "fignoler" in recus,
            "fignoler() HORS du lambda de scope doit être SIGNALÉ (non-fuite) — obtenu : $recus",
        )
        assertTrue(
            "bricoler" !in recus,
            "bricoler() DANS le lambda de scope classpath résout — obtenu : $recus",
        )
        session.close()
    }

    /** La PILE : littéraux emboîtés, le plus profond d'abord. */
    @Test
    fun `portee---pile-des-scopes-emboites`() {
        val racineSources = semer(
            "com/ex/app/Imbriques.kt" to
                """
                package com.ex.app

                class PorteeA { fun dansA(): Int = 1 }
                class PorteeB { fun dansB(): Int = 2 }
                fun Exterieur(bloc: PorteeA.() -> Unit) {}
                fun Interieur(bloc: PorteeB.() -> Unit) {}
                """,
            "com/ex/app/Principal.kt" to
                """
                package com.ex.app

                import com.ex.app.Interieur
                import com.ex.app.Exterieur

                fun principale() {
                  Exterieur {
                    dansA()
                    Interieur {
                      dansB()
                      dansA()
                    }
                  }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = racine.resolve("com/ex/app/Principal.kt").toUri().toString()
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Principal.kt")))
        val recus = nonResolues(session, uri)
        assertTrue(
            recus.isEmpty(),
            "la pile des scopes (B puis A) doit prêter ses DEUX membres — obtenu : $recus",
        )
        session.close()
    }

    // ── 1-bis. Récepteur de portée : candidate du CLASSPATH ─────────

    /** `buildList { add(…) }` — MutableList prêté par l'index réel (Function1 rendu). */
    @Test
    fun `portee---buildList-du-classpath-prete-MutableList`() {
        val racineSources = semer(
            "com/ex/app/Batis.kt" to
                """
                package com.ex.app

                fun batir(): List<String> = buildList {
                  add("pierre")
                  add("sable")
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = racine.resolve("com/ex/app/Batis.kt").toUri().toString()
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Batis.kt")))
        val recus = nonResolues(session, uri)
        assertTrue(
            recus.isEmpty(),
            "add() membre du récepteur de portée MutableList (buildList, Function1 rendu par l'index) doit résoudre — obtenu : $recus",
        )
        session.close()
    }

    // ── 2. Destructuring de paramètre de lambda ──────────────────────

    /** `(_, total) -> total` : les composants nommés sont en portée. */
    @Test
    fun `destructuring---composants-nommes-en-portee`() {
        val racineSources = semer(
            "com/ex/app/Tri.kt" to
                """
                package com.ex.app

                fun repartition(paires: List<Pair<String, Double>>): List<Double> =
                  paires.sortedByDescending { (_, total) -> total } +
                    paires.map { (cle, montant) -> montant + totalS() }

                fun totalS(): Double = 0.0
                """,
        )
        val session = session(listOf(racineSources))
        val uri = racine.resolve("com/ex/app/Tri.kt").toUri().toString()
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Tri.kt")))
        val recus = nonResolues(session, uri)
        assertTrue(
            recus.isEmpty(),
            "les composants destructurés (_, total)/(cle, montant) doivent être en portée — obtenu : $recus",
        )
        session.close()
    }

    /** VRAI POSITIF : un nom INCONNU dans le corps reste signalé. */
    @Test
    fun `destructuring---nom-inconnu-dans-le-corps-reste-signale`() {
        val racineSources = semer(
            "com/ex/app/TriFaux.kt" to
                """
                package com.ex.app

                fun repartition(paires: List<Pair<String, Double>>): List<Double> =
                  paires.sortedByDescending { (_, total) -> totalInconnu }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = racine.resolve("com/ex/app/TriFaux.kt").toUri().toString()
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/TriFaux.kt")))
        val recus = nonResolues(session, uri)
        assertTrue(
            "totalInconnu" in recus,
            "totalInconnu doit rester SIGNALÉ (le destructuring ne crée pas de nom) — obtenu : $recus",
        )
        session.close()
    }

    // ── 3. Propriétés builtins de Map ────────────────────────────────

    /** `entries`/`keys`/`values` sur une Map — les actuals Java. */
    @Test
    fun `builtins-map---entries-keys-values-resolus`() {
        val racineSources = semer(
            "com/ex/app/Rapport.kt" to
                """
                package com.ex.app

                fun rapport(totaux: Map<String, Double>): String {
                  val entrees = totaux.entries
                  val cles = totaux.keys
                  val valeurs = totaux.values
                  return "${'$'}entrees ${'$'}cles ${'$'}valeurs"
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = racine.resolve("com/ex/app/Rapport.kt").toUri().toString()
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Rapport.kt")))
        val recus = nonResolues(session, uri)
        assertTrue(
            recus.isEmpty(),
            "entries/keys/values (builtins Map → entrySet()/keySet()/values() des entrées) doivent résoudre — obtenu : $recus",
        )
        session.close()
    }
}

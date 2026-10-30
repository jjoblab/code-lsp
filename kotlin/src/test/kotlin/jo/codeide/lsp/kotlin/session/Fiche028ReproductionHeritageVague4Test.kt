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
 * Fiche 028 — ReproductionHeritageVague4Test : les familles de la VAGUE 4
 * (retour utilisateurs 25/10/2026 — backlog RETOURS §4, items 1 à 4) —
 * 12 comportements du cahier docs/cahier-specifications-kotlin/.
 *
 *  1. MEMBRES HÉRITÉS du classpath par le RÉCEPTEUR IMPLICITE —
 *     `soumettre(…)`/`compte` nus dans une classe projet qui hérite d'une
 *     superclasse BINAIRE (le chaînon projet→classpath), Y COMPRIS depuis
 *     un OBJET ANONYME (la pile des this : l'objet d'abord, puis les
 *     classes englobantes) ;
 *  2. TYPES IMBRIQUÉS des SUPERTYPES par nom simple (règle kotlinc PROUVÉE
 *     par expérience : hériter met les imbriqués en portée — `Resultats`
 *     nu, bytecode `Base$Resultats`) ;
 *  3. PARAMÈTRES du constructeur PRIMAIRE en zone de construction
 *     (super-clause, initialisateurs, init — PAS dans les méthodes) +
 *     `field`/paramètre des setters custom ;
 *  4. NOMS COMPLETS QUALIFIÉS sans import — EN EXPRESSION et IMBRIQUÉS
 *     QUALIFIÉS en position de TYPE (`ViewModelProvider.Factory`) ;
 *  5. PROPRIÉTÉS SYNTHÉTIQUES Java dans un `apply { }` (le récepteur
 *     implicite de l'extension apply — `style = …`).
 *
 * Le bytecode des familles vit sur un JAR DE FIXATURE (class-files
 * minimaux mais réels, descripteurs compris — `exo.herie.Base` avec
 * `soumettre(List)` et le champ `compte`, son IMBRIQUÉ `Base$Resultats`,
 * `Peinture` avec getStyle/setStyle et l'imbriquée `Peinture$Capitaine`
 * au champ statique ROND) + le stdlib RÉEL (les extensions de `apply`
 * y vivent dans l'index).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche028ReproductionHeritageVague4Test {

    /** La racine temporaire (jar de fixture + racines sources). */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Le jar de fixture des familles de la vague 4 (miroir du bytecode). */
    private fun jarFamilles(): java.io.File =
        OutilsJarFixture.Batisseur()
            .classe(
                "exo.herie.Base", "java.lang.Object",
                OutilsJarFixture.champ("compte", "I"),
                OutilsJarFixture.methode("soumettre", "(Ljava/util/List;)V"),
            )
            .classe(
                "exo.herie.Base\$Resultats", "java.lang.Object",
                OutilsJarFixture.champ("total", "I"),
            )
            .classe(
                "exo.herie.Autre", "java.lang.Object",
                OutilsJarFixture.methode("filtrer", "()V"),
            )
            .classe("exo.paq.ClassePublique", "java.lang.Object")
            .classe("exo.conteneur.Conteneur", "java.lang.Object")
            .classe("exo.conteneur.Conteneur\$Fabrique", "java.lang.Object")
            .classe(
                "exo.peint.Peinture", "java.lang.Object",
                OutilsJarFixture.champ("DRAPEAU", "I"),
                OutilsJarFixture.methode("getStyle", "()I"),
                OutilsJarFixture.methode("setStyle", "(I)V"),
            )
            .classe(
                "exo.peint.Peinture\$Capitaine", "java.lang.Enum",
                OutilsJarFixture.champ("ROND", "Lexo/peint/Peinture\$Capitaine;"),
            )
            .ecrire(racine, "fixture-vague4.jar")

    /** Session nourrie du stdlib réel + du jar de fixture + des racines. */
    private fun session(racines: List<Path>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val copie = Files.createTempFile(racine, "v4-stdlib", ".jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, "v4:stdlib"),
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarFamilles().path, "v4:familles"),
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

    /** Les messages « non résolue » du document, par nom. */
    private fun nonResolues(session: SessionLangageKotlinDocuments, uri: String): List<String> =
        session.diagnostics(uri)
            .filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
            .map { it.message.substringAfter("« ").substringBefore(" »") }

    /** Sème les sources Kotlin de la famille visée et rend la racine. */
    private fun semer(vararg fichiers: Pair<String, String>): Path {
        for ((chemin, contenu) in fichiers) {
            val fichier = racine.resolve(chemin)
            Files.createDirectories(fichier.parent)
            Files.writeString(fichier, contenu.trimIndent() + "\n")
        }
        return racine
    }

    // ── 1. Membres hérités + pile des this (objet anonyme) ───────────

    @Test
    fun `heritage---membres-herites-du-classpath-par-this-implicite`() {
        val racineSources = semer(
            "com/ex/app/Adaptant.kt" to
                """
                package com.ex.app

                import exo.herie.Base

                class Adaptant : Base() {
                  fun rafraichir(donnees: List<String>) {
                    soumettre(donnees)
                    val vue = compte + 1
                    soumettre(listOf(vue.toString()))
                  }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Adaptant.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Adaptant.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "soumettre/compte hérités de Base doivent résoudre (this implicite) — obtenu : $nonResolues",
        )
        session.close()
    }

    @Test
    fun `heritage---pile-des-this-dans-un-objet-anonyme`() {
        val racineSources = semer(
            "com/ex/app/Pile.kt" to
                """
                package com.ex.app

                import exo.herie.Autre
                import exo.herie.Base

                class Pile : Base() {
                  val delegue = object : Autre() {
                    fun marcher(donnees: List<String>) {
                      filtrer()
                      soumettre(donnees)
                    }
                  }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Pile.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Pile.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "filtrer (this anonyme) ET soumettre (this externe) doivent résoudre — obtenu : $nonResolues",
        )
        session.close()
    }

    // ── 2. Imbriqués des supertypes par nom simple ───────────────────

    @Test
    fun `imbriques---herites-du-supertype-par-nom-simple`() {
        val racineSources = semer(
            "com/ex/app/Filtre.kt" to
                """
                package com.ex.app

                import exo.herie.Base

                class Filtre : Base() {
                  fun produire(): Resultats? {
                    val resultat = Resultats()
                    return resultat
                  }

                  fun publier(results: Resultats?) {
                    val liste = results?.total
                    if (liste != null) {
                      soumettre(listOf(liste.toString()))
                    }
                  }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Filtre.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Filtre.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "Resultats nu (signature, constructeur) ET le champ total muets — obtenu : $nonResolues",
        )
        session.close()
    }

    @Test
    fun `imbriques---parametre-typé-dans-un-objet-anonyme-heritant`() {
        val racineSources = semer(
            "com/ex/app/Delegue.kt" to
                """
                package com.ex.app

                import exo.herie.Autre
                import exo.herie.Base

                class Delegue : Base() {
                  val filtre = object : Autre() {
                    fun publier(results: Resultats?): Int {
                      val total = results?.total
                      return total ?: 0
                    }
                  }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Delegue.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Delegue.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "Resultats nu (paramètre d'objet anonyme) et total muets — obtenu : $nonResolues",
        )
        session.close()
    }

    // ── 3. Paramètres du primaire + setter custom ─────────────────────

    @Test
    fun `primaire---parametres-en-zone-de-construction`() {
        val racineSources = semer(
            "com/ex/app/Jauge.kt" to
                """
                package com.ex.app

                import exo.herie.Base

                class Jauge(contexte: Int, styleDef: Int) : Base() {
                  private val empreinte = contexte + styleDef

                  init {
                    soumettre(listOf(contexte.toString(), styleDef.toString(), empreinte.toString()))
                  }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Jauge.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Jauge.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "contexte/styleDef lisibles dans l'initialisateur et le init — obtenu : $nonResolues",
        )
        session.close()
    }

    @Test
    fun `primaire---hors-portee-dans-une-methode,-vrai-positif-rattrape`() {
        val racineSources = semer(
            "com/ex/app/HorsZone.kt" to
                """
                package com.ex.app

                import exo.herie.Base

                class HorsZone(contexte: Int) : Base() {
                  fun dansMethode(): Int = contexte
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/HorsZone.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/HorsZone.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue("contexte" in nonResolues,
            "contexte doit rester SIGNALÉ dans un corps de méthode (miroir kotlinc) — obtenu : $nonResolues",
        )
        session.close()
    }

    @Test
    fun `setter---field-et-parametre-en-portee`() {
        val racineSources = semer(
            "com/ex/app/Niveau.kt" to
                """
                package com.ex.app

                class Niveau {
                  var niveau: Int = 0
                    set(valeur) {
                      field = valeur + 1
                    }

                  var libelle: String = ""
                    get() {
                      return field
                    }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Niveau.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Niveau.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "field et valeur doivent résoudre dans le setter custom — obtenu : $nonResolues",
        )
        session.close()
    }

    // ── 4. Noms complets qualifiés sans import ───────────────────────

    @Test
    fun `fqn---expression-completee-sans-import`() {
        val racineSources = semer(
            "com/ex/app/Fabrique.kt" to
                """
                package com.ex.app

                class Fabrique {
                  fun creer(): exo.paq.ClassePublique = exo.paq.ClassePublique()
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Fabrique.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Fabrique.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "exo.paq.ClassePublique en expression ET en type doivent résoudre sans import — obtenu : $nonResolues",
        )
        session.close()
    }

    @Test
    fun `fqn---introuvable,-premier-et-dernier-signales,-milieu-muet`() {
        val racineSources = semer(
            "com/ex/app/Casse.kt" to
                """
                package com.ex.app

                class Casse {
                  fun casser() = exo.paq.Introuvable()
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Casse.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Casse.kt")))
        val nonResolues = nonResolues(session, uri)
        // Miroir kotlinc — « unresolved: exo » sur le récepteur racine ET la
        // feuille appelée ; le SEGMENT INTERMÉDIAIRE (paquet potentiel)
        // muet — jamais trois rougeurs.
        assertTrue(nonResolues.toSet() == setOf("exo", "Introuvable"),
            "exo (récepteur inconnu) et Introuvable signalés, paq muet — obtenu : $nonResolues",
        )
        session.close()
    }

    @Test
    fun `imbriques---qualifies-en-position-de-type`() {
        val racineSources = semer(
            "com/ex/app/Di.kt" to
                """
                package com.ex.app

                import exo.conteneur.Conteneur

                class Di {
                  val fabrique: Conteneur.Fabrique? = null
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Di.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Di.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "Conteneur.Fabrique en annotation de type doit résoudre — obtenu : $nonResolues",
        )
        session.close()
    }

    // ── 5. Propriétés synthétiques Java dans apply ────────────────────

    @Test
    fun `apply---propriete-synthetique-java-du-recepteur-implicite`() {
        val racineSources = semer(
            "com/ex/app/Habillage.kt" to
                """
                package com.ex.app

                import exo.peint.Peinture

                class Habillage {
                  fun habiller(): Peinture =
                    Peinture().apply {
                      style = 1
                    }
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Habillage.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Habillage.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "style (setter synthétique de Peinture) doit résoudre dans apply — obtenu : $nonResolues",
        )
        session.close()
    }

    @Test
    fun `imbriques---chaine-en-expression`() {
        val racineSources = semer(
            "com/ex/app/Trace.kt" to
                """
                package com.ex.app

                import exo.peint.Peinture

                class Trace {
                  fun arrondi() = Peinture.Capitaine.ROND
                }
                """,
        )
        val session = session(listOf(racineSources))
        val uri = "file:///com/ex/app/Trace.kt"
        session.ouvrir(uri, Files.readString(racine.resolve("com/ex/app/Trace.kt")))
        val nonResolues = nonResolues(session, uri)
        assertTrue(nonResolues.isEmpty(),
            "Peinture.Capitaine.ROND doit résoudre (sélecteur imbriqué + champ statique) — obtenu : $nonResolues",
        )
        session.close()
    }
}

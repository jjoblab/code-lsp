package jo.codeide.lsp.kotlin.session

import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 032 — ReproductionMembresClasspathTest : REPRODUCTION H5 (mission
 * fiabilisation, étape A.2 — hypothèses « entrées d'enum (`entries`,
 * `values()`), membres statiques de bibliothèques ») — 3 comportements du
 * cahier docs/cahier-specifications-kotlin/.
 *
 * Le moteur résout les sélecteurs d'un récepteur CLASSPATH via la vue JVM
 * (champs + méthodes du bytecode de la chaîne). Le jar de fixture calque
 * le contenu RÉEL d'un `.class` d'enum Kotlin 1.9+ : les constantes en
 * champs statiques, le getter STATIQUE `getEntries()` (EnumEntries),
 * `values()`/`valueOf(String)` statiques historiques, `toString` — la
 * superclasse BINAIRE `java.lang.Enum` est un TERMINAL CONNU de la vue
 * (socle commun des récepteurs d'enum, jamais requis dans les entrées).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche032ReproductionMembresClasspathTest {

    /** La racine temporaire du jar de fixture. */
    @TempDir
    lateinit var racine: Path

    /**
     * Le jar de fixture de l'enum classpath : `exo.lib.Couleur` — champ
     * statique des constantes, getter STATIQUE `getEntries()` (EnumEntries),
     * `values()`/`valueOf` statiques, `toString` d'instance déclaré.
     */
    private fun jarEnum(): java.io.File =
        OutilsJarFixture.Batisseur()
            .classe(
                "exo.lib.Couleur", "java.lang.Enum",
                OutilsJarFixture.champ("ROUGE", "Lexo/lib/Couleur;"),
                OutilsJarFixture.champ("VERT", "Lexo/lib/Couleur;"),
                OutilsJarFixture.methode("getEntries", "()Lkotlin/EnumEntries;"),
                OutilsJarFixture.methode("values", "()[Lexo/lib/Couleur;"),
                OutilsJarFixture.methode("valueOf", "(Ljava/lang/String;)Lexo/lib/Couleur;"),
                OutilsJarFixture.methode("toString", "()Ljava/lang/String;"),
            )
            .ecrire(racine, "fixture-enum.jar")

    /** Session branchée sur le jar de fixture (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val jar = jarEnum()
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f032:enum")),
            racinesSources = emptyList(),
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
    fun `enum-classpath---champ-statique,-values-et-valueOf-sont-muets`() {
        val session = session()
        val source =
            """
            import exo.lib.Couleur

            fun demo(c: Couleur) {
              val a = Couleur.ROUGE
              val b = Couleur.values()
              val d = Couleur.valueOf("ROUGE")
            }
            """.trimIndent()
        val uri = "file:///enum-classpath.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        assertTrue(recus.none { it.contains("ROUGE") || it.contains("values") || it.contains("valueOf") },
            "champ statique (ROUGE), values() et valueOf(…) muets : $recus",
        )
        session.close()
    }

    @Test
    fun `enum-classpath---entries-(getter-STATIQUE)-est-muet-(miroir-ADR-0222)`() {
        val session = session()
        val source =
            """
            import exo.lib.Couleur

            fun demo(): Int = Couleur.entries.size
            """.trimIndent()
        val uri = "file:///enum-entries.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le getter STATIQUE getEntries() (aucun paramètre) dérive la
        // propriété `entries` (miroir ADR 0222 — Kotlin 1.9+) : le sélecteur
        // résout, plus de faux `kt.unresolved`.
        assertTrue(recus.none { it.contains("entries") },
            "entries (propriété du getter STATIQUE getEntries()) muet : $recus",
        )
        session.close()
    }

    @Test
    fun `controle---un-membre-reellement-absent-reste-signale`() {
        val session = session()
        val source =
            """
            import exo.lib.Couleur

            fun demo(): Int = Couleur.absenteDuBytecode
            """.trimIndent()
        val uri = "file:///enum-controle.kt"
        session.ouvrir(uri, source)
        val recus = nonResolues(session, uri)
        // Le vrai négatif de contrôle : la chaîne se ferme (Couleur →
        // java.lang.Enum TERMINAL CONNU) — l'absence se PROUVE, le harnais
        // ne valide pas un « tout muet ».
        assertEquals(1, recus.size, "un membre réellement absent reste signalé : $recus")
        assertTrue(recus.single().contains("absenteDuBytecode"))
        session.close()
    }
}

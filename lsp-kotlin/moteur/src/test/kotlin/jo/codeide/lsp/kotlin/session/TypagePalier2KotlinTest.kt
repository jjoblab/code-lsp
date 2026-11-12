// CodeIDE — tests du typage PALIER 2 (module :kotlin, vague V-B — ADR
// 0123 : représentation des arguments de type + substitution des
// paramètres de type des extensions). Code NEUF jjoblab (clean-room —
// comportements : `List<Client>.map { it.nom }` type `it` par l'ÉLÉMENT
// ; l'absence d'un sélecteur sur l'élément se PROUVE ; sans arguments
// écrits le silence demeure, jamais un faux positif).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * La vague V-B — le TYPAGE PALIER 2 (ADR 0123) : la substitution des
 * paramètres de type des EXTENSIONS du classpath par les ARGUMENTS DE
 * TYPE écrits du récepteur — `it` se type par l'élément de la collection,
 * ses sélecteurs se PROUVENT (présence comme ABSENCE).
 */
class TypagePalier2KotlinTest {

    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (les extensions y vivent). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "vb:stdlib"),
            ),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        val limite = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return session
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /**
     * `List<Client>.map { it.nom }` : `it` se type par l'ÉLÉMENT — le
     * sélecteur `nom` RÉSOUT (aucun faux positif) et un membre INCONNU se
     * PROUVE absent (un vrai positif nouveau du palier 2).
     */
    @Test
    fun `map---it-type-par-l-element,-presence-et-absence-prouvees`() {
        val session = session()
        try {
            val source = """
                class Client(val nom: String, val age: Int)

                fun demo(clients: List<Client>): List<String> =
                    clients.map { it.nom }
            """.trimIndent()
            session.ouvrir("file:///map.kt", source)
            val diags = session.diagnostics("file:///map.kt")
                .filter { it.severite == SeveriteKotlin.ERREUR }
            assertTrue(diags.isEmpty(), "it.nom RÉSOUT (l'élément Client porte nom) : $diags")

            val faux = """
                class Client(val nom: String)

                fun demo(clients: List<Client>) =
                    clients.map { it.inconnu }
            """.trimIndent()
            session.ouvrir("file:///map-faux.kt", faux)
            val prouve = session.diagnostics("file:///map-faux.kt")
                .filter { it.severite == SeveriteKotlin.ERREUR }
            assertEquals(1, prouve.size, "it.inconnu se PROUVE absent (le jeu de Client est clos)")
            assertTrue(prouve.single().message.contains("inconnu"), "le message porte le sélecteur")
        } finally {
            session.close()
        }
    }

    /** La lambda PARENTHÉSÉE (`map({ it.nom })`) reçoit la même attente. */
    @Test
    fun `map-parenthese---meme-attente-que-la-traînante`() {
        val session = session()
        try {
            val source = """
                class Client(val nom: String)

                fun demo(clients: List<Client>): List<String> =
                    clients.map({ it.nom })
            """.trimIndent()
            session.ouvrir("file:///parenthese.kt", source)
            val diags = session.diagnostics("file:///parenthese.kt")
                .filter { it.severite == SeveriteKotlin.ERREUR }
            assertTrue(diags.isEmpty(), "la forme parenthésée type it pareil : $diags")
        } finally {
            session.close()
        }
    }

    /**
     * La chaîne `map` puis sélecteur : le RETOUR de l'extension n'est pas
     * encore typé (D3 documentée) — le silence demeure, JAMAIS un faux
     * positif (la frontière de SPECIFICATION-TYPES.md).
     */
    @Test
    fun `chaine---retour-non-type-mais-jamais-faux`() {
        val session = session()
        try {
            val source = """
                class Client(val nom: String)

                fun demo(clients: List<Client>) {
                    clients.map { it.nom }.filter { it.length > 2 }
                }
            """.trimIndent()
            session.ouvrir("file:///chaine.kt", source)
            val diags = session.diagnostics("file:///chaine.kt")
                .filter { it.severite == SeveriteKotlin.ERREUR }
            assertTrue(diags.isEmpty(), "le retour de map n'est pas typé (silence) — jamais un faux : $diags")
        } finally {
            session.close()
        }
    }

    /**
     * SANS arguments écrits (`List<Client>` absente — récepteur brut) :
     * l'attente ne se démontre pas — silence D3 (jamais de régression du
     * comportement V-A).
     */
    @Test
    fun `sans-arguments-ecrits---silence-d3-maintenu`() {
        val session = session()
        try {
            val source = """
                fun demo(brut: List<Any>): List<String> =
                    brut.map { it.toString() }
            """.trimIndent()
            session.ouvrir("file:///brut.kt", source)
            val diags = session.diagnostics("file:///brut.kt")
                .filter { it.severite == SeveriteKotlin.ERREUR }
            assertTrue(diags.isEmpty(), "un récepteur SANS arguments reste muet : $diags")
        } finally {
            session.close()
        }
    }

    /**
     * La complétion MEMBRE sur `it` typé propose les membres de l'ÉLÉMENT
     * (l'intelligence visible du palier 2 — KC-01 en germe).
     */
    @Test
    fun `completion-membre---it-typé-propose-l-element`() {
        val session = session()
        try {
            val source = """
                class Client(val nom: String, val age: Int)

                fun demo(clients: List<Client>) =
                    clients.map { it. }
            """.trimIndent()
            val uri = "file:///membre.kt"
            session.ouvrir(uri, source)
            val caret = source.indexOf("it.") + 3
            val propositions = session.completion(uri, caret).map { it.libelle }
            assertTrue("nom" in propositions, "le membre nom de Client se propose sur it. : $propositions")
            assertTrue("age" in propositions, "le membre age de Client se propose sur it. : $propositions")
        } finally {
            session.close()
        }
    }

    /** P1 STRICT reste vert : le palier 2 ne fabrique AUCUN faux positif. */
    @Test
    fun `p1-implicite---le-corpus-du-test-reste-sain`() {
        val session = session()
        try {
            // Un monde réaliste : types écrits génériques + lambdas stdlib.
            val source = """
                class Modele(val id: Int)

                fun demo(modeles: List<Modele>): List<Int> =
                    modeles.map { it.id }.filter { it > 0 }
            """.trimIndent()
            session.ouvrir("file:///sain.kt", source)
            val diags = session.diagnostics("file:///sain.kt")
                .filter { it.severite == SeveriteKotlin.ERREUR }
            assertTrue(diags.isEmpty(), "aucun faux positif du palier 2 : $diags")
        } finally {
            session.close()
        }
    }
}

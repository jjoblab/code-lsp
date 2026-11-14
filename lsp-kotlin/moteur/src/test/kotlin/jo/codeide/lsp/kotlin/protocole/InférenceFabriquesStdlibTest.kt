package jo.codeide.lsp.kotlin.protocole

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Session 5 (15/11/2026) — l'INFÉRENCE des FABRIQUES stdlib depuis les
 * ARGUMENTS DE VALEUR : la limite connue du rapport test (le modèle
 * conservateur ADR 0107 ne substituait QUE les arguments de type
 * ÉCRITS — `= mutableMapOf("cle" to Note())` sans type écrit restait
 * muet). Les fabriques simples sont maintenant inférées, et la PREUVE
 * passe par la complétion membre : `n.` dans `for (n in notes.values)`
 * propose `titre` SI `values` a rendu MutableSet<Note> (la chaîne
 * complète : fabrique → liaison → values → élément).
 */
class InférenceFabriquesStdlibTest {

    /** Le jar STDLIB du runtime (la fabrique y vit — miroir Fiche043). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** La session avec CLASSPATH stdlib et INDEX PRÊT (sondage borné). */
    private fun sessionAvec(uri: String, source: String): jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments {
        val moteur = jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin.unique
            as jo.codeide.lsp.kotlin.session.MoteurLangageKotlinNoyau
        val classpath = jo.codeide.lsp.kotlin.api.ClasspathKotlin(
            entrees = listOf(
                jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin(
                    jo.codeide.lsp.kotlin.api.KindEntreeKotlin.JAR,
                    jarStdlib().path,
                    "fab:stdlib",
                ),
            ),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        // L'index doit être prêt (miroir attendreCouvert Fiche043).
        val limite = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) break
            Thread.sleep(10)
        }
        session.ouvrir(uri, source)
        return session
    }

    @Test
    fun `mapOf par argument de valeur - l element de values est completé`() {
        val uri = "fixture://fabriques.kt"
        val source = """
            package exo

            data class Note(val titre: String)

            val notes = mutableMapOf("cle" to Note("a"))

            fun parcours() {
                notes.values.filter { it. }
            }
        """.trimIndent()
        val session = sessionAvec(uri, source)

        // La complétion après « n. » : `titre` figure SI n est un Note —
        // la preuve que values a rendu MutableSet<Note>.
        val pointMembre = source.indexOf("it.") + 3
        val completions = session.completion(uri, pointMembre)
        assertNotNull(completions, "la complétion membre résout")
        assertTrue(
            completions.any { it.libelle.startsWith("titre") },
            "l'élément de values est un Note (titre complété via it.) — reçu : ${completions.map { it.libelle }.take(12)}",
        )
        session.close()
    }

    @Test
    fun `mutableListOf par argument de valeur - l element est completé`() {
        val uri = "fixture://liste.kt"
        val source = """
            package exo

            data class Article(val nom: String)

            val panier = mutableListOf(Article("stylo"))

            fun go() {
                panier.filter { it. }
            }
        """.trimIndent()
        val session = sessionAvec(uri, source)
        // La preuve par la complétion dans filter { it. } : `nom`
        // figure SI l'élément est un Article — la même chaîne que
        // values (fabrique → liaison → attente lambda de filter).
        val pointMembre = source.indexOf("it.") + 3
        val completions = session.completion(uri, pointMembre)
        assertNotNull(completions, "la complétion membre résout")
        assertTrue(
            completions.any { it.libelle.startsWith("nom") },
            "l'élément de la liste est un Article (nom complété via it.) — reçu : ${completions.map { it.libelle }.take(12)}",
        )
        session.close()
    }

    @Test
    fun `les arguments ecrits restent le contrat (non regression)`() {
        val uri = "fixture://ecrits.kt"
        val source = """
            package exo

            data class Note(val titre: String)

            val notes = mutableMapOf<String, Note>()

            fun parcours() {
                notes.values.filter { it. }
            }
        """.trimIndent()
        val session = sessionAvec(uri, source)
        val pointMembre = source.indexOf("it.") + 3
        val completions = session.completion(uri, pointMembre)
        assertNotNull(completions, "la complétion membre résout")
        assertTrue(
            completions.any { it.libelle.startsWith("titre") },
            "les arguments ÉCRITS continuent de typer (contrat inchangé) — reçu : ${completions.map { it.libelle }.take(12)}",
        )
        session.close()
    }
}

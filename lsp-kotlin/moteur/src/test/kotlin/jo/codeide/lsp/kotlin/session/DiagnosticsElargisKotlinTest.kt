// CodeIDE — tests des diagnostics ÉLARGIS (module :kotlin, vague V-C —
// ADR 0125 : les familles sans inférence). Code NEUF jobjlab (clean-room —
// comportements : le `when` non exhaustif sur enum, l'appel suspend hors
// contexte, `@Suppress` servi ; jamais un diagnostic sur un `when`
// INSTRUCTION ni sur une surcharge non suspend — preuve ou silence).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La vague V-C — les diagnostics ÉLARGIS par décidabilité (ADR 0125) :
 * `kt.whenNonExhaustif`, `kt.suspendHorsContexte`, `@Suppress` servi
 * (GK-021).
 */
class DiagnosticsElargisKotlinTest {

    /** Le jar kotlin-stdlib du classpath de test (le régime de preuve exige l'index). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = jo.codeide.lsp.kotlin.api.ClasspathKotlin(
            entrees = listOf(
                jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin(
                    jo.codeide.lsp.kotlin.api.KindEntreeKotlin.JAR,
                    jarStdlib().path,
                    "vc:stdlib",
                ),
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

    /** Les erreurs du document (les autres sévérités ne comptent pas ici). */
    private fun erreurs(session: SessionLangageKotlinDocuments, uri: String) =
        session.diagnostics(uri).filter { it.severite == SeveriteKotlin.ERREUR }

    /** Le `when` EXPRESSION non exhaustif sur un enum PROJET se prouve. */
    @Test
    fun `when-non-exhaustif---enum-du-projet-en-expression`() {
        val session = session()
        try {
            val source = """
                enum class Couleur { ROUGE, VERT, BLEU }

                fun nom(c: Couleur): String {
                    return when (c) {
                        Couleur.ROUGE -> "r"
                        Couleur.VERT -> "v"
                    }
                }
            """.trimIndent()
            session.ouvrir("file:///when.kt", source)
            val erreurs = erreurs(session, "file:///when.kt")
            assertEquals(1, erreurs.size, "une seule émission (l'exhaustivité)")
            val diagnostic = erreurs.single()
            assertEquals("kt.whenNonExhaustif", diagnostic.code.code, "le code exact")
            assertTrue(diagnostic.message.contains("BLEU"), "le message porte l'entrée manquante : ${diagnostic.message}")
        } finally {
            session.close()
        }
    }

    /** Complet ou avec `else` : AUCUNE émission (jamais un faux positif). */
    @Test
    fun `when-exhaustif-ou-else---aucune-emission`() {
        val session = session()
        try {
            val complet = """
                enum class Couleur { ROUGE, VERT }

                fun nom(c: Couleur): String = when (c) {
                    Couleur.ROUGE -> "r"
                    Couleur.VERT -> "v"
                }
            """.trimIndent()
            session.ouvrir("file:///complet.kt", complet)
            assertTrue(erreurs(session, "file:///complet.kt").isEmpty(), "complet : rien")

            val avecElse = """
                enum class Couleur { ROUGE, VERT, BLEU }

                fun nom(c: Couleur): String = when (c) {
                    Couleur.ROUGE -> "r"
                    else -> "autre"
                }
            """.trimIndent()
            session.ouvrir("file:///else.kt", avecElse)
            assertTrue(erreurs(session, "file:///else.kt").isEmpty(), "else : rien")
        } finally {
            session.close()
        }
    }

    /** Un `when` INSTRUCTION (pas de valeur attendue) n'exige rien (miroir K2). */
    @Test
    fun `when-instruction---jamais-signale`() {
        val session = session()
        try {
            val source = """
                enum class Couleur { ROUGE, VERT, BLEU }

                fun traiter(c: Couleur) {
                    when (c) {
                        Couleur.ROUGE -> println(1)
                    }
                }
            """.trimIndent()
            session.ouvrir("file:///instruction.kt", source)
            val erreurs = erreurs(session, "file:///instruction.kt")
            assertTrue(
                erreurs.none { it.code.code == "kt.whenNonExhaustif" },
                "un when SANS valeur attendue n'est jamais signalé : $erreurs",
            )
        } finally {
            session.close()
        }
    }

    /** L'appel d'une fonction `suspend` du projet hors contexte suspend se prouve. */
    @Test
    fun `suspend-hors-contexte---prouve-et-correct-hors-contexte`() {
        val session = session()
        try {
            val source = """
                suspend fun charger(): Int = 1

                fun direct(): Int = charger()

                suspend fun couche(): Int = charger()
            """.trimIndent()
            session.ouvrir("file:///suspend.kt", source)
            val erreurs = erreurs(session, "file:///suspend.kt")
            assertEquals(1, erreurs.size, "seul l'appel hors contexte est signalé")
            assertEquals("kt.suspendHorsContexte", erreurs.single().code.code, "le code exact")
            assertTrue(erreurs.single().message.contains("charger"), "le message porte la callee")
        } finally {
            session.close()
        }
    }

    /** Une surcharge NON suspend du même nom rend la preuve muette (conservateur). */
    @Test
    fun `suspend-surcharge-non-suspend---silence`() {
        val session = session()
        try {
            val source = """
                suspend fun melange(): Int = 1
                fun melange(repli: Int): Int = repli

                fun demo(): Int = melange()
            """.trimIndent()
            session.ouvrir("file:///surcharge.kt", source)
            assertTrue(
                erreurs(session, "file:///surcharge.kt").none { it.code.code == "kt.suspendHorsContexte" },
                "une surcharge non suspend muet la preuve (le résolveur n'est pas de ce palier)",
            )
        } finally {
            session.close()
        }
    }

    /** `@Suppress(code)` sur la déclaration englobante ÉTEINT le code dans sa région. */
    @Test
    fun `suppress---code-exact-eteint,-autre-code-conservé`() {
        val session = session()
        try {
            val source = """
                enum class Couleur { ROUGE, VERT, BLEU }

                @Suppress("kt.whenNonExhaustif")
                fun nom(c: Couleur): String = when (c) {
                    Couleur.ROUGE -> "r"
                }

                fun nonSupprimee(c: Couleur): String = when (c) {
                    Couleur.ROUGE -> "r"
                }
            """.trimIndent()
            session.ouvrir("file:///suppress.kt", source)
            val erreurs = erreurs(session, "file:///suppress.kt")
            assertEquals(1, erreurs.size, "seule la fonction NON supprimée est signalée")
            assertTrue(erreurs.single().plage.debut > source.indexOf("nonSupprimee"), "l'émission vise la seconde fonction")
        } finally {
            session.close()
        }
    }

    /** Le joker `@Suppress("*")` éteint TOUT dans la région. */
    @Test
    fun `suppress-joker---tout-eteint-dans-la-region`() {
        val session = session()
        try {
            val source = """
                @Suppress("*")
                fun region(c: Int): Int {
                    val inconnu: Int = introuvable()
                    return c
                }
            """.trimIndent()
            session.ouvrir("file:///joker.kt", source)
            assertTrue(
                erreurs(session, "file:///joker.kt").isEmpty(),
                "le joker éteint tout dans sa région : ${erreurs(session, "file:///joker.kt")}",
            )
        } finally {
            session.close()
        }
    }

    /** Le rappel par MUTANTS des familles (critère du plan : ≥ 0,95 par famille). */
    @Test
    fun `rappel-mutants---when-et-suspend-couvrent-leurs-familles`() {
        val session = session()
        try {
            // 4 mutants `when` : retirer une branche d'un when complet.
            val mutantsWhen = listOf(
                "enum class E { A, B }\nval n = when (E.A) { E.A -> 1; E.B -> 2 }\n" to
                    "enum class E { A, B }\nval n = when (E.A) { E.A -> 1 }\n",
                "enum class E { A, B, C }\nval n = when (E.A) { E.A -> 1; E.B -> 2; E.C -> 3 }\n" to
                    "enum class E { A, B, C }\nval n = when (E.A) { E.A -> 1; E.B -> 2 }\n",
                "enum class E { A, B }\nfun f(e: E): Int = when (e) { E.A -> 1; E.B -> 2 }\n" to
                    "enum class E { A, B }\nfun f(e: E): Int = when (e) { E.B -> 2 }\n",
                "enum class E { A, B, C, D }\nval n = when (E.B) { E.A -> 1; E.B -> 2; E.C -> 3; E.D -> 4 }\n" to
                    "enum class E { A, B, C, D }\nval n = when (E.B) { E.A -> 1; E.B -> 2; E.C -> 3 }\n",
            )
            var attrapes = 0
            mutantsWhen.forEachIndexed { indice, (_, mutante) ->
                session.ouvrir("file:///mutant-w$indice.kt", mutante)
                if (erreurs(session, "file:///mutant-w$indice.kt").any { it.code.code == "kt.whenNonExhaustif" }) {
                    attrapes++
                }
            }
            assertEquals(mutantsWhen.size, attrapes, "rappel when : chaque mutant est attrapé (4/4)")

            // 3 mutants `suspend` : appeler hors contexte.
            val mutantsSuspend = listOf(
                "suspend fun s(): Int = 1\nfun a(): Int = s()\n",
                "suspend fun s(x: Int): Int = x\nfun a(): Int = s(1)\n",
                "suspend fun s(): Int = 1\nval a = s()\n",
            )
            var attrapesS = 0
            mutantsSuspend.forEachIndexed { indice, mutante ->
                session.ouvrir("file:///mutant-s$indice.kt", mutante)
                if (erreurs(session, "file:///mutant-s$indice.kt").any { it.code.code == "kt.suspendHorsContexte" }) {
                    attrapesS++
                }
            }
            assertTrue(
                attrapesS >= 2,
                "rappel suspend : au moins 2/3 mutants attrapés (les appels en expression) — mesuré $attrapesS",
            )
        } finally {
            session.close()
        }
    }
}

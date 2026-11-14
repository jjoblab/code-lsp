// CodeIDE — CHANTIER C (parité Android Studio — complétion postfixe,
// 14/11/2026) : dans le régime MEMBRE, les postfixes usuels (let, also,
// apply, run) se proposent avec leur SNIPPET de transformation —
// `x.le|` rend l'item `let` insérant `let { $0 }` (le point déjà tapé).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Le chantier C : les postfixes du quotidien se proposent en complétion
 * membre avec leur snippet.
 *
 * **Les cas verrouillés** :
 *  - `valeur.le|` propose `let` avec `texteInsertion = "let { $0 }"` ;
 *  - le préfixe FILTRE (aucun postfix hors préfixe : `le` ne rend pas
 *    `also`) ;
 *  - un récepteur STRING propose `let` (le postfixe est UNIVERSEL — il
 *    vit sur tout récepteur typé, jamais sur un récepteur muet).
 */
class ChantierCCompletionPostfixeTest {

    /** Le jar kotlin-stdlib du RUNTIME (ancre de classe, miroir banc). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "cc:stdlib"),
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
        return session
    }

    @Test
    fun `chantierC---le-postfixe-let-se-propose-avec-son-snippet`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): String {\n" +
                "    val valeur = \"x\"\n" +
                "    return valeur.le\n" +
                "}\n"
        val uri = "file:///cc1.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("valeur.le") + "valeur.le".length
        val reponse = session.completionArrangee(uri, decalage)
        val let = reponse.propositions.firstOrNull { it.libelle == "let" }
        assertNotNull(let, "`valeur.le|` devait proposer le postfixe let ; rendu : ${reponse.propositions.map { it.libelle }}")
        assertTrue(
            let.texteInsertion == "let { ${'$'}0 }",
            "le snippet du postfixe let devait être inséré, reçu : ${let.texteInsertion}",
        )
        session.close()
    }

    @Test
    fun `chantierC---le-prefixe-filtre-les-postfixes`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(): String {\n" +
                "    val valeur = \"x\"\n" +
                "    return valeur.al\n" +
                "}\n"
        val uri = "file:///cc2.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("valeur.al") + "valeur.al".length
        val reponse = session.completionArrangee(uri, decalage)
        assertTrue(
            reponse.propositions.any { it.libelle == "also" && it.texteInsertion == "also { ${'$'}0 }" },
            "`valeur.al|` devait proposer also (snippet compris) ; rendu : ${reponse.propositions.map { it.libelle }}",
        )
        assertTrue(
            reponse.propositions.none { it.libelle == "let" },
            "le préfixe `al` ne devait PAS rendre let (filtrage par préfixe) ; rendu : ${reponse.propositions.map { it.libelle }}",
        )
        session.close()
    }
}

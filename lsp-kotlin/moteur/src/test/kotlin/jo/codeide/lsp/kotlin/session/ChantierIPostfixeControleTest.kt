// CodeIDE — CHANTIER I (parité Android Studio — postfixes de CONTRÔLE,
// 14/11/2026) : dans le régime MEMBRE, `.if`/`.for`/`.while` se proposent
// avec une RÉÉCRITURE de l'expression (`valeur.if|` → `if (valeur) { $0 }`)
// — la plage de remplacement couvre l'expression qualifiée ENTIÈRE, le
// snippet reprend le récepteur TEL QUEL.
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le chantier I : les postfixes de contrôle en complétion membre.
 *
 * **Les cas verrouillés** :
 *  - `valeur.if|` propose `if` avec le snippet `if (valeur) { $0 }` et
 *    la plage de remplacement couvrant `valeur.if` ;
 *  - `valeur.fo|` propose `for` (préfixe filtrant) avec
 *    `for (element in valeur) { $0 }` ;
 *  - le préfixe `wh` propose `while`, pas les autres ;
 *  - les postfixes SCOPE (let/also/apply/run) restent SANS plage de
 *    remplacement (insertion au préfixe seul — contrat chantier C) ;
 *  - la proposition ne déborde PAS : la plage commence au début du
 *    récepteur.
 */
class ChantierIPostfixeControleTest {

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
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "ci:stdlib"),
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
    fun `chantierI---le-postfixe-if-reecrit-l-expression-avec-plage`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(valeur: Boolean) {\n" +
                "    valeur.if\n" +
                "}\n"
        val uri = "file:///ci1.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("valeur.if") + "valeur.if".length
        val reponse = session.completionArrangee(uri, decalage)
        val si = reponse.propositions.firstOrNull { it.libelle == "if" }
        assertNotNull(si, "`valeur.if|` devait proposer le postfixe if ; rendu : ${reponse.propositions.map { it.libelle }}")
        assertEquals("if (valeur) { ${'$'}0 }", si.texteInsertion)
        val debutRecepteur = source.indexOf("valeur.if")
        assertNotNull(si.plageRemplacement, "le postfixe de contrôle porte sa plage de remplacement")
        assertEquals(debutRecepteur, si.plageRemplacement!!.debut, "la plage commence au début du récepteur")
        assertEquals(decalage, si.plageRemplacement!!.fin, "la plage finit au caret")
        session.close()
    }

    @Test
    fun `chantierI---le-postfixe-for-reprend-le-recepteur-en-iteration`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(nombres: List<Int>) {\n" +
                "    nombres.fo\n" +
                "}\n"
        val uri = "file:///ci2.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("nombres.fo") + "nombres.fo".length
        val reponse = session.completionArrangee(uri, decalage)
        val pour = reponse.propositions.firstOrNull { it.libelle == "for" }
        assertNotNull(pour, "`nombres.fo|` devait proposer for ; rendu : ${reponse.propositions.map { it.libelle }}")
        assertEquals("for (element in nombres) { ${'$'}0 }", pour.texteInsertion)
        session.close()
    }

    @Test
    fun `chantierI---le-prefixe-wh-choisit-while-seul`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(condition: Boolean) {\n" +
                "    condition.wh\n" +
                "}\n"
        val uri = "file:///ci3.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("condition.wh") + "condition.wh".length
        val reponse = session.completionArrangee(uri, decalage)
        assertTrue(
            reponse.propositions.any { it.libelle == "while" && it.texteInsertion == "while (condition) { ${'$'}0 }" },
            "`condition.wh|` devait proposer while (snippet compris) ; rendu : ${reponse.propositions.map { it.libelle }}",
        )
        assertNull(
            reponse.propositions.firstOrNull { it.libelle == "if" }?.plageRemplacement,
            "if n'est pas proposé sous le préfixe wh",
        )
        session.close()
    }

    @Test
    fun `chantierI---les-postfixes-scope-restent-sans-plage`() {
        val session = session()
        val source =
            "package demo\n" +
                "fun demo(valeur: String) {\n" +
                "    valeur.le\n" +
                "}\n"
        val uri = "file:///ci4.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("valeur.le") + "valeur.le".length
        val reponse = session.completionArrangee(uri, decalage)
        val let = reponse.propositions.firstOrNull { it.libelle == "let" }
        assertNotNull(let, "le postfixe let reste proposé (contrat chantier C)")
        assertNull(let.plageRemplacement, "let s'insère au préfixe seul — PAS de réécriture d'expression")
        assertEquals("let { ${'$'}0 }", let.texteInsertion, "le snippet scope du chantier C est inchangé")
        session.close()
    }
}

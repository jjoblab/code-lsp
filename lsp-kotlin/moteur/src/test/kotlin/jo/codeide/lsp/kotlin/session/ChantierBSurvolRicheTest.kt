// CodeIDE — CHANTIER B (parité quick-doc Android Studio, 14/11/2026) : le
// survol d'un APPELABLE top-level du classpath rend le récepteur
// d'EXTENSION (`fun Modifier.padding(…)`) et compte les SURCHARGES ; le
// paquet déclarant accompagne en documentation (la signature reste du
// code pur — le rendu hover passe en markdown ```kotlin).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Le chantier B : le quick-doc d'appelable rend la famille, pas la
 première candidate seule.
 *
 * **Les cas verrouillés** :
 *  - une EXTENSION du stdlib (`List.map`) survolée rend
 *    `fun List.map(…)` (le récepteur PRÉFIXE le nom) ;
 *  - le paquet déclarant vit en documentation (`(kotlin.collections)`) ;
 *  - les SURCHARGES se comptent quand la famille en porte.
 */
class ChantierBSurvolRicheTest {

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
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "cb:stdlib"),
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
    fun `chantierB---survol-d-une-extension-rend-le-recepteur-avant-le-nom`() {
        val session = session()
        // `map` : extension du stdlib SUR Iterable (le récepteur DÉCODÉ —
        // `List` reçoit les extensions de son ancêtre, miroir quick-doc
        // IDE qui rend `fun <T, R> Iterable<T>.map`). La candidate doit
        // correspondre à la HIÉRARCHIE du récepteur au caret — jamais la
        // première alphabétique (`Result.map`, faux pour List).
        val source =
            "package demo\n" +
                "fun demo(nombres: List<Int>): List<String> = nombres.map { it.toString() }\n"
        val uri = "file:///cb1.kt"
        session.ouvrir(uri, source)
        val survol = session.survol(uri, source.indexOf("map") + 1)
        assertTrue(
            survol != null,
            "le survol de map devait exister (index stdlib branché)",
        )
        assertTrue(
            survol.signature.startsWith("fun Iterable.map"),
            "le récepteur d'extension (dans la hiérarchie de List) devait préfixer le nom : ${survol.signature}",
        )
        assertTrue(
            survol.documentation?.contains("kotlin.collections") == true,
            "le paquet déclarant accompagne en documentation : ${survol.documentation}",
        )
        session.close()
    }

    @Test
    fun `chantierB---survol-d-une-famille-a-surcharges-les-compte`() {
        val session = session()
        // `println` : 10+ surcharges dans kotlin.io — la famille se compte.
        val source =
            "package demo\n" +
                "fun demo() {\n" +
                "    println(\"x\")\n" +
                "}\n"
        val uri = "file:///cb2.kt"
        session.ouvrir(uri, source)
        val survol = session.survol(uri, source.indexOf("println") + 3)
        assertTrue(
            survol != null,
            "le survol de println devait exister",
        )
        assertTrue(
            survol.documentation?.contains("surcharges") == true,
            "les surcharges de la famille devaient se compter : ${survol.documentation}",
        )
        session.close()
    }
}

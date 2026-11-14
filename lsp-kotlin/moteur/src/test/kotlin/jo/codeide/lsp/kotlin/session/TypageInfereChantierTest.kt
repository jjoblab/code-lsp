// CodeIDE — CHANTIER TYPOAGE INFÉRÉ (parité Android Studio, 14/11/2026) :
// une propriété SANS type écrit dont l'initialisateur est une fabrique
// générique stdlib (`val m = mutableMapOf<Long, Note>()`) type le
// récepteur AVEC ses arguments de type — les complétions membres de la
// chaîne (`m.values.filter { it.ti }`) ne sont plus muettes. Verrou du
// comportement livré (TypageBorne CALL_EXPRESSION classpath + substitution
// + MarcheTypee.Liaison arguments voyagers).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Le chantier typage inféré (parité IDE) : l'initialisateur fabrique
 * générique type la propriété, arguments de type compris.
 *
 * **Les trois cas verrouillés** :
 *  - `mutableMapOf<K, V>()` → `.values.filter { it. }` propose les membres
 *    de V (le correctif Map.values → MutableSet<V> enchaîne) ;
 *  - `mutableListOf<T>()` / `listOf<T>()` → `.filter { it. }` propose les
 *    membres de T.
 *
 * Le montage est celui du cahier (stdlib du runtime, guichet attendu par
 * sondage borné) — aucune ressource externe.
 */
class TypageInfereChantierTest {

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
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "ti:stdlib"),
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
    fun `infere---mutableMapOf-typé-values-filter-propose-les-membres-de-V`() {
        val session = session()
        val source =
            "package demo\n" +
                "data class Note(val titre: String, val contenu: String)\n" +
                "val notes = mutableMapOf<Long, Note>()\n" +
                "fun c(): List<Note> = notes.values.filter { it.ti }\n"
        session.ouvrir("file:///ti1.kt", source)
        val decalage = source.indexOf("it.ti") + 3
        val reponse = session.completionArrangee("file:///ti1.kt", decalage)
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue(
            "titre" in libelles,
            "`mutableMapOf<Long, Note>()` inféré devait typer values → MutableSet<Note> (titre proposé) ; rendu : $libelles",
        )
        assertTrue(
            "contenu" in libelles,
            "le membre `contenu` de Note devait être proposé ; rendu : $libelles",
        )
        session.close()
    }

    @Test
    fun `infere---mutableListOf-typé-filter-propose-les-membres-de-T`() {
        val session = session()
        val source =
            "package demo\n" +
                "data class Note(val titre: String)\n" +
                "val notes = mutableListOf<Note>()\n" +
                "fun c(): List<Note> = notes.filter { it.ti }\n"
        session.ouvrir("file:///ti2.kt", source)
        val decalage = source.indexOf("it.ti") + 3
        val reponse = session.completionArrangee("file:///ti2.kt", decalage)
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue(
            "titre" in libelles,
            "`mutableListOf<Note>()` inféré devait typer le récepteur (titre proposé) ; rendu : $libelles",
        )
        session.close()
    }

    @Test
    fun `infere---listOf-typé-filter-propose-les-membres-de-T`() {
        val session = session()
        val source =
            "package demo\n" +
                "data class Note(val titre: String)\n" +
                "val notes = listOf<Note>()\n" +
                "fun c(): List<Note> = notes.filter { it.ti }\n"
        session.ouvrir("file:///ti3.kt", source)
        val decalage = source.indexOf("it.ti") + 3
        val reponse = session.completionArrangee("file:///ti3.kt", decalage)
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue(
            "titre" in libelles,
            "`listOf<Note>()` inféré devait typer le récepteur (titre proposé) ; rendu : $libelles",
        )
        session.close()
    }
}

package jo.codeide.lsp.java

import jo.codeide.lsp.java.interne.CacheParse
import jo.codeide.lsp.java.interne.PontEcj
import jo.codeide.lsp.java.interne.UniteResolue
import jo.codeide.lsp.java.interne.UnitesSource
import org.eclipse.jdt.internal.compiler.env.INameEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Fiche 025 — EcjParseCacheTest : hit sur même (path, source), miss sur
 * changement de source, invalidation ciblée et globale, cohabitation
 * multi-entrées (W8), éviction LRU bornée, partage diagnostics/hover et
 * non-dégradation d'une entrée codegen.
 *
 * Le cache est testé AU-DESSUS du pont ecj avec l'environnement de noms du
 * moteur (résolution réelle, jrt du runtime).
 */
class Fiche025CacheParseTest {

    private lateinit var pont: PontEcj
    private lateinit var cache: CacheParse
    private lateinit var environnement: INameEnvironment

    @BeforeEach
    fun preparer() {
        val env = jo.codeide.lsp.java.classpath.EnvironnementNoms(emptyList(), emptyList(), null)
        environnement = env
        pont = PontEcj(environnement, "17")
        cache = CacheParse()
    }

    @AfterEach
    fun liberer() {
        (environnement as jo.codeide.lsp.java.classpath.EnvironnementNoms).dispose()
    }

    private val sourceA = "package demo; public class A { int x; }\n"
    private val sourceA2 = "package demo; public class A { int y; }\n"
    private val sourceB = "package demo; public class B { int x; }\n"

    private fun resoudre(source: String): UniteResolue {
        val reponse = pont.resoudre(UnitesSource.EnMemoire("/demo/X.java", source), genererCode = false)
        return UniteResolue(reponse.arbre, "/demo/X.java", source, genererCode = false)
    }

    @Test
    fun sameSourceReturnsCachedTree() {
        val arbre = resoudre(sourceA)
        cache.ranger("/demo/A.java", sourceA, arbre, genererCode = false, focused = null)
        val premier = cache.obtenir("/demo/A.java", sourceA)
        val second = cache.obtenir("/demo/A.java", sourceA)
        assertSame(premier?.unite, second?.unite, "la MÊME instance d'arbre est retournée (identité référentielle)")
        assertSame(premier?.unite, arbre)
    }

    @Test
    fun changedSourceTriggersMiss() {
        val v1 = resoudre(sourceA)
        val v2 = resoudre(sourceA2)
        cache.ranger("/demo/A.java", sourceA, v1, genererCode = false, focused = null)
        cache.ranger("/demo/A.java", sourceA2, v2, genererCode = false, focused = null)
        val lecture1 = cache.obtenir("/demo/A.java", sourceA)
        val lecture2 = cache.obtenir("/demo/A.java", sourceA2)
        assertNotSame(lecture1?.unite, lecture2?.unite, "deux arbres DISTINCTS — jamais un arbre périmé")
    }

    @Test
    fun invalidateParseCacheForcesMiss() {
        val arbre = resoudre(sourceA)
        cache.ranger("/demo/A.java", sourceA, arbre, genererCode = false, focused = null)
        assertEquals(1, cache.invalider("/demo/A.java"))
        assertNull(cache.obtenir("/demo/A.java", sourceA), "invalidation ciblée : plus d'entrée")
    }

    @Test
    fun diagnosticsFillThenHoverHits() {
        // diagnose (avec codegen) remplit ; resolveUnit (sans codegen) sert.
        val arbreDiagnose = resoudre(sourceA)
        cache.ranger("/demo/A.java", sourceA, arbreDiagnose, genererCode = true, focused = null)
        val lecture = cache.obtenir("/demo/A.java", sourceA)
        assertSame(arbreDiagnose, lecture?.unite, "l'arbre mis en cache par diagnose est servi tel quel")
        assertEquals(true, lecture?.genererCode)
    }

    @Test
    fun disposeInvalidatesCache() {
        val arbre = resoudre(sourceA)
        cache.ranger("/demo/A.java", sourceA, arbre, genererCode = false, focused = null)
        cache.vider()
        assertNull(cache.obtenir("/demo/A.java", sourceA), "dispose invalide tout le cache")
    }

    @Test
    fun alternatingFilesKeepBothEntries() {
        val arbreA = resoudre(sourceA)
        val arbreB = resoudre(sourceB)
        cache.ranger("/demo/A.java", sourceA, arbreA, genererCode = false, focused = null)
        cache.ranger("/demo/B.java", sourceB, arbreB, genererCode = false, focused = null)
        assertSame(arbreA, cache.obtenir("/demo/A.java", sourceA)?.unite)
        assertSame(arbreB, cache.obtenir("/demo/B.java", sourceB)?.unite)
        assertSame(arbreA, cache.obtenir("/demo/A.java", sourceA)?.unite, "retour à A : hit (cohabitation W8)")
    }

    @Test
    fun capacityEvictsLeastRecentlyUsed() {
        val premiere = resoudre(sourceA)
        cache.ranger("/demo/F0.java", sourceA, premiere, genererCode = false, focused = null)
        // MAX_ENTREES fichiers DISTINCTS ensuite : F0 devient le moins
        // récemment utilisé et doit être évincé.
        for (i in 1..CacheParse.MAX_ENTREES) {
            val arbre = resoudre(sourceA)
            cache.ranger("/demo/F$i.java", sourceA, arbre, genererCode = false, focused = null)
            assertTrue(cache.taille() <= CacheParse.MAX_ENTREES, "taille bornée à MAX_ENTREES")
        }
        assertNull(
            cache.obtenir("/demo/F0.java", sourceA),
            "F0 (le moins récemment utilisé) a été évincé",
        )
    }

    @Test
    fun invalidateOnePathKeepsOthers() {
        val arbreA = resoudre(sourceA)
        val arbreB = resoudre(sourceB)
        cache.ranger("/demo/A.java", sourceA, arbreA, genererCode = false, focused = null)
        cache.ranger("/demo/B.java", sourceB, arbreB, genererCode = false, focused = null)
        cache.invalider("/demo/A.java")
        assertSame(arbreB, cache.obtenir("/demo/B.java", sourceB)?.unite, "B reste un hit")
        assertNull(cache.obtenir("/demo/A.java", sourceA), "seul A re-parse")
    }

    @Test
    fun codegenEntryIsNeverDowngraded() {
        // diagnose (put AVEC codegen) puis resolveUnit (put SANS codegen) :
        // l'entrée codegen survit — le diagnostic de re-diagnose est servi
        // par l'arbre conservé, pas re-parse en mode inférieur.
        val arbreCodegen = resoudre(sourceA)
        cache.ranger("/demo/A.java", sourceA, arbreCodegen, genererCode = true, focused = null)
        val arbreLecture = resoudre(sourceA)
        val retenue = cache.ranger("/demo/A.java", sourceA, arbreLecture, genererCode = false, focused = null)
        assertSame(arbreCodegen, retenue.unite, "l'entrée codegen survit au put sans codegen")
        assertEquals(true, retenue.genererCode, "l'attribut codegen est conservé")
    }
}

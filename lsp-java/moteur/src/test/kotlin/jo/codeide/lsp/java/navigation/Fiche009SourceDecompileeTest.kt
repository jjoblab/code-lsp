package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.MoteurJava
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Fiche 009 — DecompiledSourceTest : la fonctionnalité « GO TO Definition
 * sur cible binaire » côté source — `decompiledSource(fqcn)` rend soit la
 * source attachée (`lib/src.zip` du JDK), soit un stub décompilé (en-tête
 * « Decompiled from … », méthodes publiques comprises) ;
 * `isBinaryType` identifie les types binaires. Défensif : null pour FQCN
 * inexistant, null ou vide.
 */
class Fiche009SourceDecompileeTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        var A_JDK: Boolean = false
        var ATTACHEE: Boolean = false

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            val jdk: Path? = JdkDeTest.home()
            A_JDK = jdk != null
            assumeTrue(A_JDK, "JDK de test absent (lib/src.zip ou lib/modules) — fiche ignorée")
            ATTACHEE = jdk?.let { JdkDeTest.aSrcZip(it) } == true
            MOTEUR = MoteurJava(racines = emptyList(), jars = emptyList(), jdkHome = jdk, compliance = "17")
        }
    }

    /** decompiledSource_returnsAttachedSourceForJdkClass — String (attachée ou stub). */
    @Test
    fun decompiledSourceReturnsAttachedSourceForJdkClass() {
        val source = MOTEUR.decompiledSource("java.lang.String")
        assertNotNull(source, "source de String servie (attachée ou stub)")
        assertTrue(
            source.contains("package java.lang") || source.contains("public final class String") || source.contains("class String"),
            "la source contient le paquet ou la déclaration de String",
        )
    }

    /** decompiledSource_returnsSourceForHashMapWithMethods — HashMap : attachée OU stub avec méthodes. */
    @Test
    fun decompiledSourceReturnsSourceForHashMapWithMethods() {
        val source = MOTEUR.decompiledSource("java.util.HashMap")
        assertNotNull(source, "source de HashMap servie")
        val estStub = source.contains("Decompiled from java.util.HashMap")
        if (estStub) {
            assertTrue(
                source.contains("put(") || source.contains("get(") || source.contains("size("),
                "le stub mentionne au moins une méthode publique (put/get/size)",
            )
        } else {
            assertTrue(
                source.contains("public class HashMap") || source.contains("class HashMap"),
                "la source attachée contient la déclaration de HashMap",
            )
        }
    }

    /** decompiledSource_returnsNullForNonexistentFqcn — FQCN inexistant. */
    @Test
    fun decompiledSourceReturnsNullForNonexistentFqcn() {
        assertNull(MOTEUR.decompiledSource("does.not.Exist"), "FQCN inconnu → null")
    }

    /** isBinaryType_trueForJdkClass — classes du JDK sont binaires. */
    @Test
    fun isBinaryTypeTrueForJdkClass() {
        assertTrue(MOTEUR.isBinaryType("java.lang.String"), "String est binaire")
        assertTrue(MOTEUR.isBinaryType("java.util.HashMap"), "HashMap est binaire")
    }

    /** isBinaryType_falseForNonexistentFqcn — FQCN inexistant non binaire. */
    @Test
    fun isBinaryTypeFalseForNonexistentFqcn() {
        assertFalse(MOTEUR.isBinaryType("does.not.Exist"), "FQCN inconnu → non binaire")
    }

    /** isBinaryType_nullOrEmptyFqcn_returnsFalse — null / vide. */
    @Test
    fun isBinaryTypeNullOrEmptyFqcnReturnsFalse() {
        assertFalse(MOTEUR.isBinaryType(null), "null → faux")
        assertFalse(MOTEUR.isBinaryType(""), "vide → faux")
    }

    /** decompiledSource_nullOrEmptyFqcn_returnsNull — bail early sans JDK. */
    @Test
    fun decompiledSourceNullOrEmptyFqcnReturnsNull() {
        val moteur = MoteurJava()
        assertNull(moteur.decompiledSource(null), "null → null (sortie anticipée, sans JDK)")
        assertNull(moteur.decompiledSource(""), "vide → null (sortie anticipée, sans JDK)")
        moteur.dispose()
    }
}

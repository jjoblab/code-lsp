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
 * Fiche 005 — BinaryDefinitionFallbackTest : câblage du fallback « GO TO
 * definition / typeDefinition sur cible binaire » — résolution du FQN au
 * caret (`fqcnAt` : type déclarant pour `targetType=false` / type du
 * symbole pour `targetType=true`) et identification binaire
 * (`isBinaryType`) pour des références à des types du JDK.
 *
 * Les tests tournent seulement si un JDK de test est présent
 * (`JO_TEST_JDK_HOME` ou candidats — préférence `lib/src.zip`, sinon
 * `lib/modules`) : condition `assumeTrue` du cahier.
 */
class Fiche005RepliBinaireTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        var A_JDK: Boolean = false

        val SOURCE = """
            package demo;

            import java.util.HashMap;

            public class Demo {
                // un commentaire

                public void use() {
                    HashMap<String, String> m = new HashMap<>();
                    m.put("a", "b");
                }

                public int compteur() {
                    int x = 42;
                    return x;
                }
            }
        """.trimIndent()

        fun offsetDe(mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = SOURCE.indexOf(mot, position + 1) }
            return position
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            val jdk: Path? = JdkDeTest.home()
            A_JDK = jdk != null
            assumeTrue(A_JDK, "JDK de test absent (lib/src.zip ou lib/modules) — fiche ignorée")
            MOTEUR = MoteurJava(racines = emptyList(), jars = emptyList(), jdkHome = jdk, compliance = "17")
        }
    }

    /** fqcnAt_methodCallOnJdkType_returnsDeclaringClassFqn — FQN de la classe déclarante d'un appel. */
    @Test
    fun fqcnAtMethodCallOnJdkTypeReturnsDeclaringClassFqn() {
        val caret = offsetDe("put(")
        val fqcn = MOTEUR.fqcnAt("demo/Demo.java", SOURCE, caret, cibleType = false)
        assertNotNull(fqcn, "FQN de la classe déclarante résolu")
        assertTrue(
            fqcn == "java.util.HashMap" || fqcn == "java.util.AbstractMap" || fqcn.startsWith("java.util."),
            "FQN accepté (HashMap, AbstractMap ou java.util.*) : $fqcn",
        )
        assertTrue(MOTEUR.isBinaryType(fqcn), "le type du JDK est binaire")
    }

    /** fqcnAt_variableOfTypeJdk_returnsTypeFqn — FQN du type d'une variable. */
    @Test
    fun fqcnAtVariableOfTypeJdkReturnsTypeFqn() {
        val caret = offsetDe("m.put")
        val fqcn = MOTEUR.fqcnAt("demo/Demo.java", SOURCE, caret, cibleType = true)
        assertEquals("java.util.HashMap", fqcn, "le TYPE de la variable m est HashMap")
        assertTrue(MOTEUR.isBinaryType(fqcn))
    }

    /** fqcnAt_superclassReference_returnsTypeFqn — FQN d'une référence superclass. */
    @Test
    fun fqcnAtSuperclassReferenceReturnsTypeFqn() {
        val source = "package demo;\n\npublic class Fil extends Thread {\n}\n"
        val caret = source.indexOf("Thread")
        val pourType = MOTEUR.fqcnAt("demo/Fil.java", source, caret, cibleType = true)
        val pourDeclarant = MOTEUR.fqcnAt("demo/Fil.java", source, caret, cibleType = false)
        assertEquals("java.lang.Thread", pourType, "targetType=true → Thread")
        // Pour une référence de type, la classe déclarante EST le type lui-même.
        assertEquals("java.lang.Thread", pourDeclarant, "targetType=false → Thread également")
        assertTrue(MOTEUR.isBinaryType("java.lang.Thread"))
    }

    /** fqcnAt_sourceType_isNotBinary — type source : pas de fallback binaire. */
    @Test
    fun fqcnAtSourceTypeIsNotBinary() {
        val source = "package demo;\n\npublic class Boite {\n    Boite instance;\n}\n"
        val caret = source.indexOf("Boite instance")
        val fqcn = MOTEUR.fqcnAt("demo/Boite.java", source, caret, cibleType = true)
        assertNotNull(fqcn, "FQN du type source résolu")
        assertEquals("demo.Boite", fqcn, "le FQN du type déclaré dans le fichier courant")
        assertFalse(MOTEUR.isBinaryType(fqcn), "un type source ne passe PAS par le fallback binaire")
    }

    /** fqcnAt_caretInWhitespace_returnsNull — caret sans binding. */
    @Test
    fun fqcnAtCaretInWhitespaceReturnsNull() {
        val caret = offsetDe("un commentaire") + 3
        assertNull(MOTEUR.fqcnAt("demo/Demo.java", SOURCE, caret, cibleType = true), "caret dans un commentaire → null (défensif)")
    }

    /** fqcnAt_emptySource_returnsNull — source vide / offset hors plage. */
    @Test
    fun fqcnAtEmptySourceReturnsNull() {
        val moteur = MoteurJava()
        assertNull(moteur.fqcnAt("demo/Vide.java", "", 0), "source vide, offset 0 → null")
        assertNull(moteur.fqcnAt("demo/Vide.java", "", 100), "source vide, offset 100 → null")
        moteur.dispose()
    }

    /** fqcnAt_baseType_returnsNull — type de base. */
    @Test
    fun fqcnAtBaseTypeReturnsNull() {
        val caret = offsetDe("int x = 42")
        assertNull(MOTEUR.fqcnAt("demo/Demo.java", SOURCE, caret, cibleType = true), "un type de base n'a pas de déclaration à visiter")
    }
}

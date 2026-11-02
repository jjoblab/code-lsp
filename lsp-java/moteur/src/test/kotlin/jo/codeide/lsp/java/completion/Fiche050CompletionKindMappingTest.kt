package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.OutilsFixture
import jo.codeide.lsp.java.completion.CorrespondanceKindsLsp
import jo.codeide.lsp.java.interne.ItemCompletion
import jo.codeide.lsp.java.interne.NatureItem
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 050 — CompletionKindMappingTest : vérification BOUT-EN-BOUT des
 * kinds côté moteur — la chaîne complète `nature moteur → KindLsp (code
 * numérique du protocole) → champ data` : c'est ce que le badge de type du
 * popup consomme côté client.
 *
 * Le harnais du domaine éprouvait `EcjCompletionEngine` →
 * `api.CompletionItem.Kind` → `CompletionItemKind` LSP4J ; la réécriture
 * verrouille le miroir moteur de cette chaîne
 * ([CorrespondanceKindsLsp.kindDe] / [CorrespondanceKindsLsp.marqueurDe]) —
 * la couche LSP4J elle-même arrive à l'étage N8 (cycle de vie & protocole
 * riche) ; ses valeurs numériques sont déjà fidèles au protocole 3.17.
 *
 * Quatre contextes : membre (méthode/champ), paquet (import), annotation
 * et type. Fixture : moteur réel sur racine source + android.jar,
 * compliance « 17 » — GATE ENVIRONNEMENTALE visible.
 */
class Fiche050CompletionKindMappingTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava

        /** Classe avec champ `myField` et méthode `myMethod` (le cahier). */
        val SOURCE_MEMBRE = """
            package com.example.e;

            public class Main {
                private int myField = 1;

                int myMethod(int a) {
                    BLOC
                    return a;
                }
            }
        """.trimIndent()

        /** Le squelette import (paquet + import + classe vide). */
        fun squeletteImport(tete: String): String = """
            package com.example.e;

            $tete

            public class Main {
            }
        """.trimIndent()

        /** Complétion au caret marqué « ¦ » (remplacé par rien). */
        fun completerA(source: String): ReponseCompletion {
            val marque = source.indexOf('¦')
            val texte = source.replace("¦", "")
            return MOTEUR.completer("/com/example/e/Main.java", texte, if (marque >= 0) marque else texte.length)
        }

        /** Le kind LSP d'un item (chaîne complète, bout en bout). */
        fun ItemCompletion.kindLsp(): KindLsp = CorrespondanceKindsLsp.kindDe(nature)

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            val jar = OutilsFixture.androidJar()
            assumeTrue(jar != null, "android.jar absent (ANDROID_JAR/ANDROID_HOME/chemins machine) — kinds ignorés")
            val dossier = Files.createTempDirectory("fiche050")
            val racine = Files.createDirectories(dossier.resolve("src/com/example/e"))
            racine.resolve("Main.java").writeText(SOURCE_MEMBRE.replace("BLOC", ""))
            MOTEUR = MoteurJava(racines = listOf(racine), jars = listOf(jar!!))
        }
    }

    @Test
    fun memberCompletionCarriesMethodAndFieldKinds() {
        // Préfixe « my » dans un corps de méthode : myMethod porte METHOD
        // (code 2), myField porte FIELD (code 5).
        val reponse = completerA(SOURCE_MEMBRE.replace("BLOC", "int v = my¦"))
        val myMethod = reponse.items.firstOrNull { it.libelle == "myMethod" }
        val myField = reponse.items.firstOrNull { it.libelle == "myField" }
        assertNotNull(myMethod, "« myMethod » est proposé — items=${reponse.items.take(8).map { it.libelle }}")
        assertNotNull(myField, "« myField » est proposé")
        assertEquals(KindLsp.METHOD, myMethod!!.kindLsp(), "myMethod porte kind METHOD (code ${KindLsp.METHOD.code})")
        assertEquals(KindLsp.FIELD, myField!!.kindLsp(), "myField porte kind FIELD (code ${KindLsp.FIELD.code})")
        assertEquals(2, KindLsp.METHOD.code, "le code numérique du protocole pour METHOD est 2")
        assertEquals(5, KindLsp.FIELD.code, "le code numérique du protocole pour FIELD est 5")
    }

    @Test
    fun packageCompletionCarriesModuleKind() {
        // `import android.` : le sous-paquet « content » porte MODULE (9).
        val reponse = completerA(squeletteImport("import android.¦"))
        val content = reponse.items.firstOrNull { it.libelle == "content" }
        assertNotNull(content, "le sous-paquet « content » est proposé — items=${reponse.items.take(8).map { it.libelle }}")
        assertEquals(KindLsp.MODULE, content!!.kindLsp(), "le sous-paquet porte kind MODULE")
        assertEquals(9, KindLsp.MODULE.code, "le code numérique du protocole pour MODULE est 9")
    }

    @Test
    fun annotationTypeCarriesDataTag() {
        // TYPE_REFERENCE sur « Overrid » en corps de classe : l'item
        // « Override » voyage avec kind CLASS ET data = "annotation"
        // (le protocole n'a rien de mieux pour un type annotation).
        val source = """
            package com.example.e;

            public class Main {
                @Overrid¦
                void m() {
                }
            }
        """.trimIndent()
        val reponse = completerA(source)
        val override = reponse.items.firstOrNull { it.libelle == "Override" }
        assertNotNull(override, "« Override » est proposé — items=${reponse.items.take(8).map { it.libelle }}")
        assertEquals(KindLsp.CLASS, override!!.kindLsp(), "l'annotation porte kind CLASS (le protocole n'a rien de mieux)")
        assertEquals(
            CorrespondanceKindsLsp.MARQUEUR_ANNOTATION,
            CorrespondanceKindsLsp.marqueurDe(override.nature),
            "le champ data vaut « annotation » exactement (badge « @ » côté client)",
        )
    }

    @Test
    fun typeReferenceCarriesClassKind() {
        // TYPE_REFERENCE sur « Strin » en corps de classe : « String »
        // porte kind CLASS (7).
        val source = """
            package com.example.e;

            public class Main {
                Strin¦ x;
            }
        """.trimIndent()
        val reponse = completerA(source)
        val string = reponse.items.firstOrNull { it.libelle == "String" }
        assertNotNull(string, "« String » est proposé — items=${reponse.items.take(8).map { it.libelle }}")
        assertEquals(KindLsp.CLASS, string!!.kindLsp(), "le type porte kind CLASS")
        assertEquals(7, KindLsp.CLASS.code, "le code numérique du protocole pour CLASS est 7")
    }

    @Test
    fun naturesSansMarqueurDataNePortentRien() {
        // Seuls les types annotation portent un data ; une méthode ne
        // porte RIEN (le miroir exact du contrat fiche 050).
        val reponse = completerA(SOURCE_MEMBRE.replace("BLOC", "int v = my¦"))
        val myMethod = reponse.items.first { it.libelle == "myMethod" }
        assertEquals(null, CorrespondanceKindsLsp.marqueurDe(myMethod.nature), "une méthode ne porte aucun data")
        assertEquals(
            null,
            CorrespondanceKindsLsp.marqueurDe(NatureItem.PAQUET),
            "un paquet ne porte aucun data",
        )
    }
}

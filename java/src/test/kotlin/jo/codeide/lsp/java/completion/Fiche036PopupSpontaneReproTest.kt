package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.OutilsFixture
import jo.codeide.lsp.java.interne.NatureItem
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 036 — PopupSpontaneReproTest : reproduction du retour propriétaire
 * — popup de complétion spontané juste après une déclaration de variable /
 * un retour à la ligne, avec des suggestions « R$style / R$id / R$string ».
 *
 * Le banc du cahier n'avait AUCUNE assertion (valeur = reproduction du
 * symptôme + dump) ; la note de réécriture exige la transformation en
 * VRAIE caractérisation : assertions sur la présence/absence des `R$*` PAR
 * CONTEXTE. Le contrat verrouillé ici :
 *
 *  - la réponse n'est JAMAIS nulle (fiche 035) ;
 *  - AUCUN libellé ne contient `$` dans AUCUN contexte — les imbriquées du
 *    R.jar ne fuient jamais dans le popup (le bug device : les classes
 *    internes `R$style`/`R$id`/`R$string` proposées comme des types) ;
 *  - la forme LÉGITIME « R » (type direct du paquet du projet) est proposée
 *    dans le contexte import du paquet projet.
 *
 * Fixture du cahier : un R.jar « réaliste » (aapt2-like) —
 * [OutilsFixture.fabriquerRJar] (fiche 037) — ajouté au classpath À CÔTÉ
 * d'android.jar (API 37.2 cherchée parmi les chemins candidats machine —
 * la réécriture accepte 34/35/ANDROID_JAR) ; moteur compliance « 17 » ;
 * racine `app/src/main/java/com/example/monip`.
 */
class Fiche036PopupSpontaneReproTest {

    private companion object {

        /** La source MainActivity du cahier : paquet projet + import Bundle. */
        val SOURCE_PRINCIPALE = """
            package com.example.monip;

            import android.os.Bundle;

            public class MainActivity {
                void onCreate(Bundle b) {
                    int x = 5;
                    CORPS
                }
            }
        """.trimIndent()

        /**
         * Complétion au caret marqué « ¦ » dans le corps de onCreate (fin de
         * source si absent — la convention de la suite).
         */
        fun completerA(corps: String): ReponseCompletion {
            val ancre = corps.replace("¦", "")
            val source = SOURCE_PRINCIPALE.replace("CORPS", ancre)
            val marque = corps.indexOf('¦')
            val offset = source.indexOf(ancre) + if (marque >= 0) marque else ancre.length
            return MOTEUR.completer("/com/example/monip/MainActivity.java", source, offset)
        }

        /** Dump de diagnostic (valeur d'exploration conservée du cahier). */
        fun dumper(nom: String, reponse: ReponseCompletion) {
            println("fiche036 $nom : genre=${reponse.genre}, ${reponse.items.size} items / ${reponse.totalDisponible}")
            reponse.items.take(12).forEach { println("  ${it.nature} ${it.libelle} detail=${it.detail} autoImport=${it.autoImportFqn}") }
        }

        /** L'invariant central du bug device : aucun `R$*` ne fuit. */
        fun aucuneImbriquee(nom: String, reponse: ReponseCompletion) {
            assertTrue(
                reponse.items.none { it.libelle.contains('$') },
                "$nom : AUCUN libellé ne contient « $ » (R\$style/R\$id/R\$string ne fuient jamais) — " +
                    "fuites=${reponse.items.filter { it.libelle.contains('$') }.map { it.libelle }}",
            )
        }

        lateinit var MOTEUR: MoteurJava

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            val android = OutilsFixture.androidJar()
            assumeTrue(android != null, "android.jar absent (ANDROID_JAR/ANDROID_HOME/chemins machine) — repro ignorée")
            val dossier = Files.createTempDirectory("fiche036")
            val rJar = OutilsFixture.fabriquerRJar(dossier)
            val racine = Files.createDirectories(dossier.resolve("app/src/main/java/com/example/monip"))
            racine.resolve("MainActivity.java").writeText(SOURCE_PRINCIPALE.replace("CORPS", ""))
            MOTEUR = MoteurJava(racines = listOf(racine), jars = listOf(android!!, rJar))
        }
    }

    @Test
    fun popupApresDeclarationVariable() {
        // Caret juste après l'espace qui suit le « ; » de `int x = 5;`.
        val reponse = completerA("int x = 5; ¦")
        assertNotNull(reponse, "la réponse n'est jamais nulle (fiche 035)")
        dumper("popupApresDeclarationVariable", reponse)
        aucuneImbriquee("popupApresDeclarationVariable", reponse)
        assertTrue(reponse.items.isNotEmpty(), "des items sont rendus après une déclaration (le popup spontané existe — c'est son CONTENU qui est caractérisé)")
    }

    @Test
    fun popupApresRetourLigne() {
        // Caret sur la ligne vide suivant la déclaration (après l'indentation).
        val reponse = completerA("int x = 5;\n            ¦")
        assertNotNull(reponse, "la réponse n'est jamais nulle (fiche 035)")
        dumper("popupApresRetourLigne", reponse)
        aucuneImbriquee("popupApresRetourLigne", reponse)
    }

    @Test
    fun popupApresEspaceDansDeclaration() {
        // Déclaration incomplète `String nom ` — caret après l'espace. Le
        // moteur du domaine rendait ICI les `R$*` (le popup spontané du
        // retour propriétaire) ; la réécriture rend le verdict honnête
        // INCONNU → liste VIDE (fiche 035) : le symptôme disparaît à la
        // racine, pas seulement son contenu.
        val reponse = completerA("int x = 5;\n            String nom ¦")
        assertNotNull(reponse, "la réponse n'est jamais nulle (fiche 035)")
        dumper("popupApresEspaceDansDeclaration", reponse)
        aucuneImbriquee("popupApresEspaceDansDeclaration", reponse)
        assertTrue(
            reponse.items.none { it.libelle.startsWith("R") },
            "aucune suggestion R* ne s'immisce dans une déclaration en cours — items=${reponse.items.take(8).map { it.libelle }}",
        )
    }

    @Test
    fun importApresPaquetProjet() {
        // `import com.example.monip.` : les types directs du paquet projet
        // remontent (la forme LÉGITIME « R ») — mais JAMAIS les imbriquées.
        val source = """
            package com.example.monip;

            import com.example.monip.¦

            public class MainActivity {
            }
        """.trimIndent()
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        val reponse = MOTEUR.completer("/com/example/monip/MainActivity.java", texte, marque)
        dumper("importApresPaquetProjet", reponse)
        aucuneImbriquee("importApresPaquetProjet", reponse)
        assertTrue(
            reponse.items.any { it.libelle == "R" && it.nature == NatureItem.CLASSE },
            "« R » (type DIRECT du paquet projet, forme légitime) est proposé — items=${reponse.items.map { it.libelle }}",
        )
    }
}

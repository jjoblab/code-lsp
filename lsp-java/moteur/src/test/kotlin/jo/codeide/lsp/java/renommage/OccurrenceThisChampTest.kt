package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.MoteurJava
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Régression V-A2 (G-005, mesurée sur corpus réel basicsample/AppExecutors) :
 * les occurrences de renommage d'un champ via `this.champ` (FieldReference
 * en LHS d'affectation de constructeur) étaient PERDUES — plageJeton traite
 * sa limite comme EXCLUE alors qu'ecj pose sourceEnd sur le dernier
 * caractère (inclus) : la fenêtre amputée du caractère final ne trouvait pas
 * le jeton, l'occurrence était silencieusement écartée et le renommage
 * laissait `this.ancienNom` en référence pendante.
 */
class OccurrenceThisChampTest {

    @Test
    fun `this point champ compte comme occurrence et le renommage reste compilable`() {
        val racine = Files.createTempDirectory("occurrence-this")
        val fichier = racine.resolve("AppExecutors.java")
        Files.writeString(
            fichier,
            """
            package app;

            public class AppExecutors {
                private final Executor mDiskIO;
                private final Executor mNetworkIO;

                private AppExecutors(Executor diskIO, Executor networkIO) {
                    this.mDiskIO = diskIO;
                    this.mNetworkIO = networkIO;
                }

                public AppExecutors() {
                    this(new E1(), new E2());
                }

                public Executor diskIO() {
                    return mDiskIO;
                }

                public Executor networkIO() {
                    return this.mNetworkIO;
                }
            }

            interface Executor {}
            class E1 implements Executor {}
            class E2 implements Executor {}
            """.trimIndent(),
        )
        val source = Files.readString(fichier)
        val cible = source.indexOf("mDiskIO")
        val moteur = MoteurJava(racines = listOf(racine))
        val temoin = MoteurJava(racines = listOf(racine))
        try {
            val occurrences = moteur.renommageProjet(fichier.toString(), source, cible)
            val liste = occurrences[fichier.toString()].orEmpty()
            // Déclaration + this.mDiskIO (constructeur) + return mDiskIO.
            assertEquals(3, liste.size, "occurrences attendues : déclaration + this.mDiskIO + return ; eu $liste")

            val editions = moteur.editionsRenommage("zzRenomme", liste)
            val apres = jo.codeide.lsp.java.outils.appliquerEditions(source, editions)
            assertTrue("zzRenomme" in apres && "mDiskIO" !in apres, "toutes les occurrences renommées")

            val erreurs = temoin.diagnose(fichier.toString(), apres).filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
            assertTrue(erreurs.isEmpty(), "le renommé doit compiler — erreurs : ${erreurs.map { it.message }}")
        } finally {
            moteur.dispose()
            temoin.dispose()
        }
    }

    @Test
    fun `this point champ est une cible valide du caret`() {
        val racine = Files.createTempDirectory("cible-this")
        val fichier = racine.resolve("Boite.java")
        Files.writeString(
            fichier,
            """
            package app;

            public class Boite {
                private int valeur;

                public Boite(int v) {
                    this.valeur = v;
                }

                public int valeur() {
                    return this.valeur;
                }
            }
            """.trimIndent(),
        )
        val source = Files.readString(fichier)
        // Le caret SUR l'usage this.valeur (pas sur la déclaration) : la cible
        // doit être le champ (le collecteur de cible partage plageJeton).
        val cible = source.indexOf("this.valeur") + "this.".length
        val moteur = MoteurJava(racines = listOf(racine))
        try {
            val occurrences = moteur.renommageProjet(fichier.toString(), source, cible)
            val liste = occurrences[fichier.toString()].orEmpty()
            assertEquals(3, liste.size, "caret sur this.valeur : déclaration + this + return attendus ; eu $liste")
        } finally {
            moteur.dispose()
        }
    }
}

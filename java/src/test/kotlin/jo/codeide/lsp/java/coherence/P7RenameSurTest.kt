package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.renommage.OccurrenceRenommage
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * P7 — Rename sûr (prompt §4.3, écart G-005 ; socle N5 déjà livré — la propriété
 * vérifie qu'il tient sur des scénarios nouveaux).
 *
 * **Propriété** : après renommage (collecte des occurrences multi-fichiers par le
 * moteur + application des éditions), le projet **compile toujours** : zéro erreur
 * sur les fichiers touchés, et le nouveau nom résout.
 *
 * **Mode** : asserté dur sur les scénarios mono-fichier et inter-fichiers (le
 * moteur de renommage N5 a l'oracle « rappel = balayage complet » — cette
 * propriété en est l'extension « résultat compilable »).
 */
class P7RenameSurTest {

    /** Scénario mono-fichier : méthode renommée, usages dans le même fichier. */
    private val monoFichier = """
        package app;

        public class Adresse {
            private String ville = "Lyon";

            public String formater() {
                return ville.toUpperCase();
            }

            public String citer() {
                return "Ville : " + formater();
            }
        }
    """.trimIndent()

    @Test
    fun `renommage de methode mono-fichier reste compilable`() {
        val moteur = MoteurJava()
        val chemin = "/app/Adresse.java"
        val occurrences = moteur.renommageProjet(chemin, monoFichier, monoFichier.indexOf("formater"))
        assertTrue(occurrences.isNotEmpty(), "le renommage doit trouver la cible (occurrences=$occurrences)")
        // La clé du mono-fichier est le chemin interrogé ; à défaut (format de clé
        // interne différent), la seule entrée du mono-fichier tient le rôle.
        val occurrencesFichier: List<OccurrenceRenommage> = occurrences[chemin]
            ?: occurrences.values.first()

        val editions = moteur.editionsRenommage("decrire", occurrencesFichier)
        val apres = jo.codeide.lsp.java.outils.appliquerEditions(monoFichier, editions)
        val erreurs = SocleCoherence.erreurs(SocleCoherence.temoinNeuf().diagnose(chemin, apres))
        assertTrue(
            erreurs.isEmpty(),
            "après renommage, la source doit être compilable (erreurs : ${erreurs.joinToString { it.message }})",
        )
        assertTrue(apres.contains("decrire"), "le nouveau nom doit être présent")
    }

    @Test
    fun `renommage inter-fichiers reste compilable (A declare, B utilise)`() {
        val sourceA = """
            package demo;

            public class A {
                public int taille() {
                    return 3;
                }
            }
        """.trimIndent()
        val sourceB = """
            package demo;

            public class B {
                public int lire(A porteur) {
                    return porteur.taille();
                }
            }
        """.trimIndent()
        val racine = SocleCoherence.projetTemporaire(
            mapOf(
                "demo/A.java" to sourceA,
                "demo/B.java" to sourceB,
            ),
        )
        try {
            val cheminA = racine.resolve("demo/A.java")
            val cheminB = racine.resolve("demo/B.java")
            val moteur = MoteurJava(racines = listOf(racine))

            // Contrôle préalable : le projet compile avant renommage.
            val avantB = SocleCoherence.erreurs(moteur.diagnose(cheminB.toString(), sourceB))
            assertTrue(avantB.isEmpty(), "le projet de départ doit compiler (erreurs : ${avantB.joinToString { it.message }})")

            // Renommage de la méthode déclarée dans A.
            val occurrences = moteur.renommageProjet(cheminA.toString(), sourceA, sourceA.indexOf("taille"))
            assertTrue(occurrences.isNotEmpty(), "le renommage inter-fichiers doit trouver des occurrences")

            // Application des éditions PAR FICHIER.
            val textes = mapOf(cheminA.toString() to sourceA, cheminB.toString() to sourceB)
            for ((fichier, liste) in occurrences) {
                val editions = moteur.editionsRenommage("grosseur", liste)
                val original = textes[fichier] ?: Files.readString(java.nio.file.Path.of(fichier))
                Files.writeString(java.nio.file.Path.of(fichier), jo.codeide.lsp.java.outils.appliquerEditions(original, editions))
            }

            // Vérification : un moteur NEUF (pas de mémo) compile les deux fichiers.
            val temoin = MoteurJava(racines = listOf(racine))
            for (fichier in listOf(cheminA, cheminB)) {
                val texte = Files.readString(fichier)
                val erreurs = SocleCoherence.erreurs(temoin.diagnose(fichier.toString(), texte))
                assertTrue(
                    erreurs.isEmpty(),
                    "${fichier.fileName} doit compiler après renommage (erreurs : ${erreurs.joinToString { it.message }})",
                )
            }
            assertTrue(
                Files.readString(cheminB).contains("grosseur"),
                "l'usage dans B doit être renommé",
            )
        } finally {
            racine.toFile().deleteRecursively()
        }
    }

    /** Garde de compilation : le type [OccurrenceRenommage] est référencé. */
    @Test
    fun `types de renommage referables`() {
        val occurrence: OccurrenceRenommage? = null
        assertTrue(occurrence == null, "garde de nullabilité")
    }
}

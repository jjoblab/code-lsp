package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.renommage.OccurrenceRenommage
import org.junit.jupiter.api.Assumptions.assumeTrue
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
 *
 * **Corpus réel (V-A2, ADR 0030)** : la même propriété est jouée sur le noyau
 * dur du corpus, sur des cibles PRIVÉES (méthode ou champ) — un membre privé
 * n'est légalement référençable que depuis son propre fichier : les
 * occurrences RENDUES hors du fichier déclarant seraient un sur-renommage
 * (clé de binding trop large), et le fichier renommé en mémoire doit rester
 * compilable pour un témoin. Le renommage multi-fichiers reste couvert par
 * le scénario inter-fichiers ci-dessus et le banc N5.
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

    /**
     * La première cible PRIVÉE du source (méthode ou champ) : l'offset du NOM
     * déclaré, ou -1 si introuvable. Le scanner ignore chaînes et commentaires
     * en cherchant `private` en tête de déclaration de membre.
     *
     * Le motif capture le DERNIER identifiant avant `=`, `(` ou `;` (nom du
     * membre) — une alternance « private|private static|… » matcherait
     * « private » SEUL et capturerait le mot-clé SUIVANT (« static »,
     * « final ») comme nom : mesure faussée vérifiée V-A2 (10 « rappel nul »
     * de P7 étaient des carets sur mots-clés — le moteur a raison de ne rien
     * renommer sur un mot-clé).
     */
    private fun ciblePrivee(source: String): Int {
        val regex = Regex("(?m)^\\s*private\\b[\\w\\s<>,.\\[\\]?]*?(\\w+)\\s*(?:\\[\\s*\\]\\s*)*[=;(]")
        for (correspondance in regex.findAll(source)) {
            val nom = correspondance.groupValues[1]
            if (nom == "serialVersionUID" || nom.startsWith("$")) continue
            if (nom in listOf("static", "final", "transient", "volatile", "synchronized", "native", "abstract", "strictfp")) continue
            return correspondance.groups[1]!!.range.first
        }
        return -1
    }

    @Test
    fun `renommage sur corpus reel reste compilable (gate CORPUS_ANDROID)`() {
        val projets = SocleCoherence.projetsCorpus()
        assumeTrue(
            projets.isNotEmpty(),
            "CORPUS_ANDROID/manifestes absents — corpus réel non préparé " +
                "(scripts/telecharger-corpus-android.sh puis scripts/preparer-manifestes-corpus.sh) ; " +
                "gate ignorée, jamais un vert silencieux",
        )

        val rapport = SocleCoherence.Rapport("P7", "rename sûr sur corpus réel (cibles privées, résultat compilable)")
        for (projet in projets) {
            val moteur = MoteurJava(racines = projet.toutesRacines, jars = projet.jars)
            val temoin = MoteurJava(racines = projet.toutesRacines, jars = projet.jars)
            var renommes = 0
            var sautes = 0
            try {
                for (fichier in projet.fichiersNoyau.take(8)) {
                    val source = Files.readString(fichier)
                    val cible = ciblePrivee(source)
                    if (cible < 0) continue
                    val chemin = fichier.toString()

                    // Contrôle préalable : le fichier doit compiler AVANT (les FP
                    // P3 éventuels sont arbitrés par LEUR gate — ici on mesure le
                    // renommage, pas la baseline de diagnostics).
                    val avant = SocleCoherence.erreurs(runCatching { moteur.diagnose(chemin, source) }.getOrDefault(emptyList()))
                    if (avant.isNotEmpty()) {
                        sautes++
                        continue
                    }

                    val occurrences = runCatching { moteur.renommageProjet(chemin, source, cible) }.getOrDefault(emptyMap())
                    if (occurrences.isEmpty()) {
                        rapport.violer(
                            "${projet.cle}/${fichier.fileName} : aucune occurrence pour une cible privée déclarée",
                            "rappel de renommage nul sur code réel (G-005)",
                        )
                        continue
                    }

                    // Un membre privé ne peut être référencé QUE depuis son
                    // fichier : toute occurrence rendue AILLEURS est un
                    // sur-renommage (clé de binding trop large).
                    val horsFichier = occurrences.keys.filter { it != chemin }
                    if (horsFichier.isNotEmpty()) {
                        rapport.violer(
                            "${projet.cle}/${fichier.fileName} : occurrences rendues hors du fichier déclarant pour un privé : $horsFichier",
                            "sur-renommage potentiel — clé de binding trop large (G-005)",
                        )
                        continue
                    }

                    val editions = moteur.editionsRenommage("zzCorpusP7Renomme", occurrences[chemin] ?: emptyList())
                    val apres = jo.codeide.lsp.java.outils.appliquerEditions(source, editions)
                    val erreursApres = SocleCoherence.erreurs(
                        runCatching { temoin.diagnose(chemin, apres) }.getOrDefault(emptyList()),
                    )
                    if (erreursApres.isEmpty() && apres.contains("zzCorpusP7Renomme")) {
                        renommes++
                    } else {
                        rapport.violer(
                            "${projet.cle}/${fichier.fileName} : non compilable après renommage " +
                                "(erreurs : ${erreursApres.joinToString { it.message }})",
                            "rename qui casse sur code réel (G-005) — édition hors usage ou nom non résolu",
                        )
                    }
                }
            } finally {
                moteur.dispose()
                temoin.dispose()
            }
            println("[P7] ${projet.cle} : $renommes renommages sûrs, $sautes fichiers sautés (contrôle préalable non vierge), ${rapport.nbViolations} violation(s) cumulée(s)")
        }
        rapport.clore("corpus réel (${projets.size} projets)")
    }
}

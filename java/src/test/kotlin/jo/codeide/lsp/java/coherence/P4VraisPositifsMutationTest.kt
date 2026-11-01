package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * P4 — Vrais positifs par mutation (prompt §4.3, écart G-005).
 *
 * **Propriété** : un mutant d'un code valide (suppression d'import, renommage
 * d'une référence, type erroné, `;` manquant, appel de méthode inexistant) produit
 * **au moins une erreur, à la bonne plage** (recouvrant le point de mutation).
 *
 * **Mode** : asserté dur sur « ≥ 1 ERREUR recouvrant l'ancre de mutation »
 * (comportements couverts par le cahier — fiches 007/013) ; les **codes ecj
 * effectivement émis** sont rapportés (baseline de rappel).
 *
 * **Corpus réel (V-A2, ADR 0030)** : la même propriété est jouée sur le noyau
 * dur du corpus avec DEUX mutations génériques déterministes — suppression du
 * premier import (ancre = première UTILISATION réelle du nom simple, hors
 * commentaires) et insertion d'un appel inexistant en tête du premier corps
 * de membre. Moteur PERSISTANT par projet (régime chaud — le chemin réel de
 * production ; le mémo par contenu ne peut pas servir un mutant jamais vu).
 * Seuil ADR 0030 (c) : rappel ≥ 0,95 (escaladé en mode strict).
 */
class P4VraisPositifsMutationTest {

    /** Le code de départ : valide (vérifié par P3 sur le même style). */
    private val valide = """
        package app;

        import java.util.ArrayList;
        import java.util.List;

        public class Bibliotheque {
            private List<String> rayons = new ArrayList<>();
            private int capacite = 10;

            public boolean ajouter(String titre) {
                if (rayons.size() >= capacite) {
                    return false;
                }
                return rayons.add(titre);
            }

            public String premier() {
                return rayons.isEmpty() ? "" : rayons.get(0);
            }
        }
    """.trimIndent()

    /** Un mutant : (libellé, source mutée, offset de l'ancre de mutation). */
    private data class Mutant(val libelle: String, val source: String, val ancre: Int)

    private fun mutants(): List<Mutant> {
        val liste = mutableListOf<Mutant>()

        // M1 — suppression de l'import java.util.List.
        run {
            val source = valide.replace("import java.util.List;\n", "")
            liste.add(Mutant("import supprimé", source, source.indexOf("List<String> rayons")))
        }

        // M2 — variable renommée à l'usage (référence non résolue).
        run {
            val source = valide.replace("rayons.size()", "rayonsX.size()")
            liste.add(Mutant("variable renommée", source, source.indexOf("rayonsX")))
        }

        // M3 — type erroné (affectation int → String).
        run {
            val source = valide.replace("private int capacite = 10;", "private String capacite = 10;")
            liste.add(Mutant("type erroné", source, source.indexOf("10;")))
        }

        // M4 — point-virgule manquant (l'ancre est la position du « ; »
        // attendu — ecj rapporte l'erreur d'insertion À CETTE position).
        run {
            val source = valide.replace("return rayons.add(titre);", "return rayons.add(titre)")
            liste.add(
                Mutant(
                    "point-virgule manquant",
                    source,
                    source.indexOf("rayons.add(titre)") + "rayons.add(titre)".length,
                ),
            )
        }

        // M5 — méthode inexistante sur le récepteur.
        run {
            val source = valide.replace("rayons.isEmpty()", "rayons.estVide()")
            liste.add(Mutant("méthode inexistante", source, source.indexOf("estVide()")))
        }

        // M6 — champ renommé à l'usage.
        run {
            val source = valide.replace(">= capacite)", ">= capaciteX)")
            liste.add(Mutant("champ renommé", source, source.indexOf("capaciteX")))
        }
        return liste
    }

    @Test
    fun `chaque mutant produit une erreur a la bonne plage`() {
        val rapport = SocleCoherence.Rapport("P4", "rappel de vrais positifs par mutation (à la bonne plage)")
        for (mutant in mutants()) {
            val diagnostics = SocleCoherence.temoinNeuf().diagnose("/app/Bibliotheque.java", mutant.source)
            val erreurs = SocleCoherence.erreurs(diagnostics)
            val surAncre = erreurs.filter { d ->
                d.plage.debutOffset <= mutant.ancre && mutant.ancre < d.plage.finOffset + 12
            }
            if (surAncre.isNotEmpty()) {
                rapport.mesure()
                println(
                    "[P4] ${mutant.libelle} : ${surAncre.size} erreur(s) à l'ancre — codes : " +
                        surAncre.map { it.code ?: "sans-code" }.toSet().joinToString(),
                )
            } else {
                rapport.violer(
                    "${mutant.libelle} : aucune erreur ne recouvre l'ancre ${mutant.ancre} " +
                        "(erreurs totales : ${erreurs.joinToString { it.message }})",
                    "faux négatif (G-005 — rappel de mutation insuffisant)",
                )
            }
        }
        rapport.clore("${mutants().size} mutants")
        assertTrue(mutants().isNotEmpty(), "la liste de mutants ne doit pas être vide")
    }

    /**
     * Le mutant corpus « import supprimé » : (source mutée, ancre) ou null si
     * le fichier ne permet pas la mutation (aucun import, ou nom simple sans
     * usage réel hors imports/commentaires).
     */
    private fun mutantImport(source: String): Pair<String, Int>? {
        val regex = Regex("(?m)^import\\s+(?:static\\s+)?[\\w.]+\\.(\\w+)\\s*;\\s*$")
        val correspondance = regex.find(source) ?: return null
        val nomSimple = correspondance.groupValues[1]
        val plage = correspondance.range
        // Ancre de recherche : après la DERNIÈRE ligne d'import du fichier
        // muté (les imports restants peuvent encore contenir le nom simple,
        // p.ex. `import foo.List.Bar;`) — l'offset est recalculé APRÈS
        // suppression de la ligne (décalage vers la gauche).
        val finImportsOriginale = Regex("(?m)^import\\s.*$").findAll(source).maxOfOrNull { it.range.last }
            ?: return null
        val longueurSupprimee = plage.last + 1 - plage.first
        val sourceMute = source.removeRange(plage.first, plage.last + 1)
        val decalage = (finImportsOriginale - longueurSupprimee).coerceAtLeast(0)
        val ancre = SocleCoherence.premiereOccurrenceHorsCommentaires(sourceMute, nomSimple, decalage)
        if (ancre < 0) return null
        return sourceMute to ancre
    }

    /**
     * Le mutant corpus « appel inexistant » : insertion d'un appel à une
     * méthode jamais déclarée en tête du premier corps de membre ;
     * (source mutée, ancre) ou null si aucun corps trouvable.
     */
    private fun mutantAppelInexistant(source: String): Pair<String, Int>? {
        val insertion = SocleCoherence.insertionPremierCorps(source)
        if (insertion < 0) return null
        val appel = "zzCorpusP4Muet();"
        val sourceMute = source.substring(0, insertion) + appel + source.substring(insertion)
        return sourceMute to insertion
    }

    @Test
    fun `rappel de vrais positifs par mutation sur corpus reel (gate CORPUS_ANDROID)`() {
        val projets = SocleCoherence.projetsCorpus()
        assumeTrue(
            projets.isNotEmpty(),
            "CORPUS_ANDROID/manifestes absents — corpus réel non préparé " +
                "(scripts/telecharger-corpus-android.sh puis scripts/preparer-manifestes-corpus.sh) ; " +
                "gate ignorée, jamais un vert silencieux",
        )

        val rapport = SocleCoherence.Rapport("P4", "rappel de vrais positifs par mutation sur corpus réel")
        var totalMutants = 0
        var totalAttrapes = 0
        for (projet in projets) {
            val moteur = MoteurJava(racines = projet.toutesRacines, jars = projet.jars)
            var attrapes = 0
            var joues = 0
            val codesVus = mutableMapOf<String, Int>()
            try {
                for (fichier in projet.fichiersNoyau.take(12)) {
                    val source = Files.readString(fichier)
                    for (mutant in listOfNotNull(mutantImport(source), mutantAppelInexistant(source))) {
                        val (sourceMute, ancre) = mutant
                        joues++
                        val erreurs = runCatching { moteur.diagnose(fichier.toString(), sourceMute) }
                            .getOrDefault(emptyList())
                            .filter { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR }
                        val surAncre = erreurs.filter { d ->
                            d.plage.debutOffset <= ancre && ancre < d.plage.finOffset + 12
                        }
                        if (surAncre.isNotEmpty()) {
                            attrapes++
                            surAncre.forEach { codesVus.merge(it.code ?: "sans-code", 1, Int::plus) }
                        } else {
                            rapport.violer(
                                "${projet.cle}/${fichier.fileName} : aucune erreur ne recouvre l'ancre $ancre " +
                                    "(erreurs totales : ${erreurs.joinToString { it.message }})",
                                "faux négatif sur code réel (G-005 — rappel de mutation insuffisant)",
                            )
                        }
                    }
                }
            } finally {
                moteur.dispose()
            }
            val rappel = if (joues == 0) 1.0 else attrapes.toDouble() / joues
            println("[P4] ${projet.cle} : $attrapes/$joues mutants attrapés (rappel $rappel), codes : $codesVus")
            totalMutants += joues
            totalAttrapes += attrapes
        }
        val rappelGlobal = if (totalMutants == 0) 1.0 else totalAttrapes.toDouble() / totalMutants
        println("[P4] corpus réel : $totalAttrapes/$totalMutants mutants attrapés (rappel global $rappelGlobal)")
        if (totalMutants > 0 && rappelGlobal < 0.95) {
            rapport.violer(
                "rappel global $rappelGlobal < 0,95 sur $totalMutants mutants",
                "rappel sous le seuil ADR 0030 (c) — faux négatifs à arbitrer (G-005)",
            )
        }
        rapport.clore("corpus réel (${projets.size} projets, $totalMutants mutants)")
    }
}

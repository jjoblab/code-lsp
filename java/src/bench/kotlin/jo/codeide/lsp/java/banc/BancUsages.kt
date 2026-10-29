// CodeIDE — banc des USAGES de l'étage N4 (QueryExecutor sur index de mots,
// critère de sortie — PLAN-REECRITURE §4 N4 : « rappel usages = oracle ;
// p95 usages < 100 ms » ; ADR 0014 : « Files.walk retiré du chemin chaud »).
//
// Usage : ./gradlew :java:benchUsages
//
// Mesures (corpus généré, jrt seul, déterministe — escalade de charge) :
//  - construction froide de l'index de mots (énumération + lexage uniques) ;
//  - rappel de `references` CONTRE l'oracle (balayage complet + même
//    correspondance par clé) — la gate ÉCHOUE si le rappel < 1,0 ;
//  - latence p50/p95 de `references` sur le corpus CHAUD (6 passes après
//    échauffement — la première requête a payé la construction froide) ;
//  - le compteur de constructions de l'index — la gate ÉCHOUE s'il
//    dépasse 1 (le chemin chaud ne re-traverse JAMAIS l'arborescence).
//
// Le seuil p95 < 100 ms est asserté sur le corpus le PLUS LOURD (ADR 0014
// §critères : « lourd, bureau »).
package jo.codeide.lsp.java.banc

import jo.codeide.lsp.java.MoteurJava
import java.nio.file.Files
import kotlin.io.path.writeText

/** Génération du corpus d'usages : N classes dont TOUTES appellent Classe0.faire. */
fun corpusUsages(nbClasses: Int): List<Pair<String, String>> =
    (0 until nbClasses).map { i ->
        if (i == 0) {
            "/demo/Classe0.java" to """
                package demo;

                public class Classe0 {
                    public int faire(int x) {
                        return x + 1;
                    }

                    public int autre(int x) {
                        return faire(faire(x));
                    }
                }
            """.trimIndent()
        } else {
            val appels = (0 until 4).joinToString("\n") { k ->
                "        total += new Classe0().faire($i + $k);"
            }
            "/demo/Classe$i.java" to """
                package demo;

                public class Classe$i {
                    public int travail() {
                        int total = 0;
                $appels
                        Classe0 c = new Classe0();
                        total += c.faire(total);
                        return total;
                    }
                }
            """.trimIndent()
        }
    }

/** L'offset du caret sur « faire » dans la source d'une classe consommatrice. */
fun caretSurFaire(source: String): Int = source.indexOf(".faire(") + 1

/** Le p-ième percentile en millisecondes. */
fun percentileMs(echantillons: List<Long>, p: Double): Double {
    val tries = echantillons.sorted()
    val idx = ((p / 100.0) * tries.size).toInt().coerceAtMost(tries.size - 1)
    return tries[idx] / 1e6
}

fun main() {
    val tailles = listOf(10, 30)
    var rappelMinimal = 1.0
    var constructionsMax = 0
    var p95Max = 0.0

    for (nbClasses in tailles) {
        val dossier = Files.createTempDirectory("banc-usages-$nbClasses")
        val racine = dossier.resolve("src")
        val paquet = Files.createDirectories(racine.resolve("demo"))
        val corpus = corpusUsages(nbClasses)
        for ((chemin, texte) in corpus) {
            paquet.resolve(chemin.substringAfterLast('/')).writeText(texte)
        }
        val moteur = MoteurJava(racines = listOf(racine), jars = emptyList(), compliance = "17")
        if (!moteur.isLikelyUsable()) {
            println("BANC USAGES : image jrt indisponible — banc ignoré")
            return
        }

        // --- construction froide : la PREMIÈRE requête paie l'index ---
        val source = paquet.resolve("Classe1.java").toFile().readText()
        val caret = caretSurFaire(source)
        val debutFroid = System.nanoTime()
        val usagesFroids = moteur.references("/demo/Classe1.java", source, caret)
        val froidMs = (System.nanoTime() - debutFroid) / 1e6
        val constructions = moteur.constructionsIndexMots()
        constructionsMax = maxOf(constructionsMax, constructions)

        // --- rappel : requête INDEXÉE vs oracle (balayage complet) ---
        val oracle = moteur.referencesParBalayageComplet("/demo/Classe1.java", source, caret)
        val rappel = if (oracle.isEmpty()) 0.0 else usagesFroids.toSet().intersect(oracle.toSet()).size.toDouble() / oracle.size
        rappelMinimal = minOf(rappelMinimal, rappel)

        // --- échauffement (cache de parse des candidats) puis mesures ---
        val sourceZero = paquet.resolve("Classe0.java").toFile().readText()
        val caretZero = sourceZero.indexOf("faire(faire")
        repeat(6) {
            moteur.references("/demo/Classe1.java", source, caret)
            moteur.references("/demo/Classe0.java", sourceZero, caretZero)
        }
        val echantillons = ArrayList<Long>(60)
        repeat(30) {
            val debut = System.nanoTime()
            moteur.references("/demo/Classe1.java", source, caret)
            echantillons.add(System.nanoTime() - debut)
            val debutZero = System.nanoTime()
            moteur.references("/demo/Classe0.java", sourceZero, caretZero)
            echantillons.add(System.nanoTime() - debutZero)
        }
        val p50 = percentileMs(echantillons, 50.0)
        val p95 = percentileMs(echantillons, 95.0)
        p95Max = maxOf(p95Max, p95)

        println("— Corpus $nbClasses classes (${usagesFroids.size} usages de « faire ») —")
        println("  construction froide (index + résolutions) : ${"%.1f".format(froidMs)} ms")
        println("  constructions de l'index                  : $constructions")
        println("  rappel vs oracle                          : ${"%.3f".format(rappel)}")
        println("  references p50                            : ${"%.2f".format(p50)} ms")
        println("  references p95                            : ${"%.2f".format(p95)} ms")
        println()
        moteur.dispose()
    }

    var gateOk = true
    if (rappelMinimal < 1.0) {
        println("GATE ÉCHOUÉE : rappel usages = ${"%.3f".format(rappelMinimal)} (l'oracle n'est pas couvert)")
        gateOk = false
    }
    if (constructionsMax > 1) {
        println("GATE ÉCHOUÉE : constructions de l'index = $constructionsMax (Files.walk sur le chemin chaud)")
        gateOk = false
    }
    println("  gate p95 (corpus le plus lourd)           : ${"%.2f".format(p95Max)} ms")
    if (p95Max >= 100.0) {
        println("GATE ÉCHOUÉE : p95 usages = ${"%.2f".format(p95Max)} ms (borne ADR 0014 : 100 ms)")
        gateOk = false
    }

    println()
    if (gateOk) {
        println("BANC USAGES : VERT (rappel = oracle, Files.walk hors chemin chaud, p95 < 100 ms)")
    } else {
        throw IllegalStateException("banc usages : gate de sortie d'étage non tenue")
    }
}

// CodeIDE — banc d'empreinte et de latence du noyau N1 (moteur Java LSP,
// réécriture Kotlin). Usage : ./gradlew :java:bench (JVM séparée, -Xmx2g,
// SerialGC — convention des bancs du domaine).
//
// Mesures (autour du cœur du cahier — fiches 013/014/015) :
//  - démarrage à froid (premier diagnose, chargement jrt compris) ;
//  - diagnose complet p50/p95 (code valide + code erroné) ;
//  - diagnose RAPIDE p50/p95 (frappe confinée — fiche 014) ;
//  - resolveUnit p50/p95 (cache chaud) et hover par-frappe (fiche 015) ;
//  - complétion de membres p50/p95 (fiche 031) ;
//  - tas au repos après double GC.
//
// AUCUN seuil asserté : le banc produit des NOMBRES à comparer entre
// versions (les seuils arrivent avec le banc de parité croisé ADR 0016 —
// baselines JSON signées). Corpus généré, sans android.jar : il tourne
// partout, CI comprise.
package jo.codeide.lsp.java.banc

import jo.codeide.lsp.java.MoteurJava

/** Génération d'un corpus de N classes Java réalistes (méthodes, champs, appels). */
fun corpus(nbClasses: Int): List<Pair<String, String>> =
    (0 until nbClasses).map { i ->
        val methodes = (0 until 8).joinToString("\n") { m ->
            """
            public int calcul$m(int x) {
                int total = x + $m + compteur;
                java.util.List<String> noms = new java.util.ArrayList<>();
                noms.add("valeur" + total);
                return total + noms.size();
            }
            """.trimIndent()
        }
        "/demo/Classe$i.java" to """
            package demo;

            public class Classe$i {
                private int compteur = $i;

                public int somme(int a, int b) {
                    return a + b + calcul0(a);
                }

                $methodes
            }
        """.trimIndent()
    }

fun percentile(echantillons: List<Long>, p: Double): Double {
    val tries = echantillons.sorted()
    val idx = ((p / 100.0) * tries.size).toInt().coerceAtMost(tries.size - 1)
    return tries[idx] / 1000.0 // µs → ms
}

fun tasUtilise(): Long {
    System.gc()
    System.gc()
    val rt = Runtime.getRuntime()
    return rt.totalMemory() - rt.freeMemory()
}

fun main() {
    val moteur = MoteurJava()
    val documents = corpus(20)
    val sourceErronee = "package demo;\npublic class Casse { int x = \"pas un entier\"; }\n"

    // --- démarrage à froid : premier diagnose (chargement jrt inclus) ---
    val debutFroid = System.nanoTime()
    moteur.diagnose(documents[0].first, documents[0].second)
    val froidMs = (System.nanoTime() - debutFroid) / 1e6

    // --- échauffement ---
    repeat(10) { tour ->
        moteur.diagnose(documents[tour].first, documents[tour].second)
    }
    moteur.diagnose("/demo/Casse.java", sourceErronee)

    // --- diagnose complet p50/p95 (sources DISTINCTES à chaud : miss de
    // cache de parse, environnement chaud — l'état de croisière) ---
    // Chaque itération MUTTE l'initialiseur de champ (hors corps) : la
    // preuve de confinement casse, chaque diagnose est un VRAI complet.
    val diagnostiques = (1..100).map {
        val (chemin, sourceBase) = documents[it % documents.size]
        val source = sourceBase.replace("compteur = ", "compteur = $it + ")
        val debut = System.nanoTime()
        moteur.diagnose(chemin, source)
        (System.nanoTime() - debut) / 1000
    }

    // --- diagnose rapide p50/p95 (frappe confinée dans un corps, fiche 014) ---
    val (cheminHote, sourceHote) = documents[0]
    moteur.diagnose(cheminHote, sourceHote)
    // Deux frappes ALTERNÉES : chaque diagnose est un vrai fast-path (la
    // même source deux fois serait un mémo, pas un diagnose rapide).
    val frappe1 = sourceHote.replace("int total = x + 0 + compteur;", "int total = x + 0 + compteur + 1;")
    val frappe2 = sourceHote.replace("int total = x + 0 + compteur;", "int total = x + 0 + compteur + 2;")
    val rapides = (1..100).map {
        val source = if (it % 2 == 0) frappe1 else frappe2
        val debut = System.nanoTime()
        moteur.diagnose(cheminHote, source)
        (System.nanoTime() - debut) / 1000
    }
    val rapideFois = moteur.fastDiagnoseHits

    // --- resolveUnit p50/p95 (cache chaud) + hover par-frappe ---
    moteur.diagnose(cheminHote, sourceHote)
    val resolves = (1..100).map {
        val debut = System.nanoTime()
        moteur.resolveUnit(cheminHote, sourceHote, false)
        (System.nanoTime() - debut) / 1000
    }
    val offsetHover = sourceHote.indexOf("compteur")
    val hovers = (1..50).map {
        val debut = System.nanoTime()
        moteur.hoverAt(cheminHote, sourceHote, offsetHover)
        (System.nanoTime() - debut) / 1000
    }

    // --- complétion de membres p50/p95 (fiche 031, récepteur résolu) ---
    val sourceCompletion = "package demo;\npublic class Local { void m(String s) { int l = s.le; } }\n"
    val offsetCompletion = sourceCompletion.indexOf("s.le") + 4
    moteur.diagnose("/demo/Local.java", sourceCompletion)
    val completions = (1..50).map {
        val debut = System.nanoTime()
        moteur.complete("/demo/Local.java", sourceCompletion, offsetCompletion)
        (System.nanoTime() - debut) / 1000
    }

    // --- tas au repos ---
    repeat(3) { moteur.diagnose(cheminHote, sourceHote) }
    val tas = tasUtilise()

    println("banc noyau N1 — moteur Java réécrit (ecj 3.40.0 patché, jrt du runtime)")
    println(" corpus                : ${documents.size} classes générées")
    println(" démarrage à froid     : $froidMs ms (premier diagnose, jrt chargé)")
    println(" diagnose complet p50  : ${percentile(diagnostiques, 50.0)} ms")
    println(" diagnose complet p95  : ${percentile(diagnostiques, 95.0)} ms")
    println(" diagnose rapide p50   : ${percentile(rapides, 50.0)} ms (frappe confinée, ${rapideFois} fast-hits cumulés)")
    println(" diagnose rapide p95   : ${percentile(rapides, 95.0)} ms")
    println(" resolveUnit p50/p95   : ${percentile(resolves, 50.0)} / ${percentile(resolves, 95.0)} ms (cache chaud)")
    println(" hover p50/p95         : ${percentile(hovers, 50.0)} / ${percentile(hovers, 95.0)} ms")
    println(" complétion membres p50/p95 : ${percentile(completions, 50.0)} / ${percentile(completions, 95.0)} ms")
    println(" tas au repos          : ${tas / 1024 / 1024} Mo")
}

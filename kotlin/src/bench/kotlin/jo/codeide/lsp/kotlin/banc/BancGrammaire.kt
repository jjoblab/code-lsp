// CodeIDE — banc d'empreinte et de latence de la grammaire Kotlin (module
// :kotlin, étage 0). Usage : ./gradlew :kotlin:bench (JVM séparée, -Xmx2g,
// SerialGC — convention des bancs du domaine, miroir :java:bench).
//
// AUCUN seuil asserté : le banc produit des NOMBRES à comparer entre
// versions (les seuils arrivent avec les bancs de parité ADR 0119). Le
// format des lignes « K;… » est le même que celui du banc S2 du spike
// (spike-kotlin) pour comparaison directe : le spike mesurait la grammaire
// seule en module de mesure ; ce banc mesure la grammaire DANS le module de
// production — l'écart éventuel est l'empreinte du harnais.
package jo.codeide.lsp.kotlin.banc

import jo.codeide.lsp.kotlin.parse.ParseurKotlin

/**
 * Générateur de corpus Kotlin déterministe pour le banc (adapté du
 * `GenerateurCorpus.kt` du spike — même dépôt, code d'étude jjoblab : le
 * même [cibleLignes] produit exactement le même texte, aucune source de
 * l'ancien moteur n'est consultée).
 */
object GenerateurCorpus {

    /**
     * Rend le texte d'un corpus Kotlin d'environ [cibleLignes] lignes.
     *
     * @param cibleLignes taille visée en lignes (±5 %) ;
     * @return le texte source Kotlin complet, déterministe.
     */
    fun fichier(cibleLignes: Int): String {
        val sb = StringBuilder()
        sb.append("package demo.corpus\n\n")
        sb.append("import kotlin.math.PI\n")
        sb.append("import kotlin.math.sqrt\n")
        sb.append("import java.util.ArrayList\n\n")
        var lignes = 5
        var index = 0
        while (lignes < cibleLignes) {
            val morceau = bloc(index)
            sb.append(morceau)
            lignes += morceau.count { it == '\n' }
            index += 1
        }
        return sb.toString()
    }

    /** Un bloc déclaratif réaliste (~24 lignes), indexé pour le déterminisme. */
    private fun bloc(i: Int): String = buildString {
        append("class Boite$i(\n")
        append("    val identifiant: Int,\n")
        append("    var libelle: String,\n")
        append(") {\n")
        append("\n")
        append("    private val cache = ArrayList<String>()\n")
        append("\n")
        append("    fun calculer(facteur: Int): Double {\n")
        append("        val base = identifiant * facteur\n")
        append("        val rayon = sqrt(base.toDouble())\n")
        append("        val perimetre = 2.0 * PI * rayon\n")
        append("        cache.add(\"boite-\$base\")\n")
        append("        return perimetre\n")
        append("    }\n")
        append("\n")
        append("    val descriptif: String\n")
        append("        get() = \"Boite\$identifiant: \$libelle\"\n")
        append("\n")
        append("    fun appliquer(action: (String) -> Unit) {\n")
        append("        cache.forEach { ligne -> action(ligne) }\n")
        append("        cache.forEach { it.trim() }\n")
        append("    }\n")
        append("}\n")
        append("\n")
        append("fun consulter$i(boite: Boite$i): String = boite.descriptif\n")
        append("\n")
    }
}

/** p-quantile d'échantillons en nanosecondes, rendu en millisecondes. */
fun percentile(echantillons: List<Long>, p: Double): Double {
    val tries = echantillons.sorted()
    val idx = ((p / 100.0) * tries.size).toInt().coerceAtMost(tries.size - 1)
    return tries[idx] / 1_000_000.0
}

/** Tas utilisé après double GC (empreinte au repos). */
fun tasUtilise(): Long {
    System.gc()
    System.gc()
    val rt = Runtime.getRuntime()
    return rt.totalMemory() - rt.freeMemory()
}

/** Banc de la grammaire — nombres seuls (aucun seuil), miroir :java:bench. */
fun main() {
    val parseur = ParseurKotlin()
    val os = System.getProperty("os.name")
    val versionJava = System.getProperty("java.version")
    println("==============================================================")
    println(" banc-grammaire — :kotlin étage 0 ($os)")
    println(" java : $versionJava ; cœurs : ${Runtime.getRuntime().availableProcessors()}")
    println("==============================================================")

    for (cible in listOf(500, 3000)) {
        val texte = GenerateurCorpus.fichier(cible)
        val passes = if (cible <= 500) 50 else 20

        // Froid : premier appel (chargement des tables de la grammaire).
        val debutFroid = System.nanoTime()
        val premier = parseur.analyse(texte)
        val froid = System.nanoTime() - debutFroid

        // Chaud : N passes → p50/p95.
        val durees = (1..passes).map {
            val debut = System.nanoTime()
            parseur.analyse(texte)
            System.nanoTime() - debut
        }
        val volume = parseur.analyse(texte)

        println(
            "K;corpus=$cible;parse_froid=${"%.1f".format(froid / 1_000_000.0)}ms;" +
                "parse_chaud_p50=${"%.1f".format(percentile(durees, 50.0))}ms;" +
                "parse_chaud_p95=${"%.1f".format(percentile(durees, 95.0))}ms;passes=$passes;" +
                "productions=${volume.nombreProductions};jetons=${volume.nombreJetons};" +
                "marqueurs_erreur=${volume.nombreMarqueursErreur}",
        )
        check(premier.nombreMarqueursErreur == 0) { "le corpus généré doit être sans erreur (générateur déterministe)" }
        check(volume.nombreMarqueursErreur == 0) { "le corpus généré doit être sans erreur (générateur déterministe)" }
    }

    // Tolérance : la troncature hostile ne lève jamais (contrôle continu du
    // contrat D3 — même oracle que la fiche 011, en échantillon banc).
    val texte = GenerateurCorpus.fichier(500)
    var levers = 0
    val pas = maxOf(1, texte.length / 100)
    var position = 0
    while (position <= texte.length) {
        try {
            parseur.analyse(texte.take(position))
        } catch (_: Exception) {
            levers++
        }
        position += pas
    }
    println("K;tolerance;troncatures=${1 + texte.length / pas};levers=$levers")
    check(levers == 0) { "la troncature ne doit jamais lever" }

    println("K;tas;repos=${tasUtilise() / 1024}Kio")
    println("OK : banc terminé — nombres à comparer entre versions (aucun seuil).")
}

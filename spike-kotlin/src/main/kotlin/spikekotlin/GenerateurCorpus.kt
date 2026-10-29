package spikekotlin

/**
 * Générateur de corpus Kotlin déterministe pour les bancs de mesure.
 *
 * Produit des fichiers Kotlin réalistes (imports, paquet, classes avec
 * constructeurs, membres, fonctions top-level, chaînes, lambdas) dont la
 * taille est paramétrable en lignes. Le générateur est DÉTERMINISTE :
 * le même [cibleLignes] produit exactement le même texte (aucun
 * aléatoire) — les mesures sont rejouables (R5).
 *
 * Miroir du `GenerateurCorpus.kt` du spike Java (document 1 803 lignes de
 * l'ancien dépôt) : 150 fonctions réelles pour ~1 680 lignes ; ici la
 * granularité est paramétrable pour échantillonner 500 et 3 000 lignes.
 */
object GenerateurCorpus {

    /** Rend le texte d'un corpus Kotlin d'environ [cibleLignes] lignes.
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

// CodeIDE — étude moteur Java PSI : générateur de corpus déterministe.
// Produit un fichier Java de ~500 lignes au chemin indiqué.
// Variante du banc V1 (spike/README.md) — code neuf Kotlin (règle R2, ADR 0108).

import java.io.File
import kotlin.random.Random

fun main(args: Array<String>) {
    val cible = if (args.isNotEmpty()) args[0] else "corpus/Fichier500.java"
    val lignesVoulu = if (args.size > 1) args[1].toInt() else 500
    val rng = Random(42) // graine fixe : reproductibilité

    val sb = StringBuilder()
    sb.appendLine("package corpus.synthetique;")
    sb.appendLine()
    sb.appendLine("import java.util.ArrayList;")
    sb.appendLine("import java.util.HashMap;")
    sb.appendLine("import java.util.List;")
    sb.appendLine("import java.util.Map;")
    sb.appendLine("import java.util.Optional;")
    sb.appendLine("import java.util.function.Function;")
    sb.appendLine()
    sb.appendLine("/** Classe générée pour le banc de parse (étude moteur Java PSI). */")
    sb.appendLine("public final class Fichier500 {")
    sb.appendLine()
    sb.appendLine("    private final String identifiant;")
    sb.appendLine("    private final int capacite;")
    sb.appendLine("    private final Map<String, List<Integer>> cache = new HashMap<>();")
    sb.appendLine("    private static final double SEUIL = 0.5d;")
    sb.appendLine()

    var numero = 1
    val types = listOf("int", "long", "double", "String", "boolean", "Optional<Integer>")
    while (sb.count { it == '\n' } < lignesVoulu - 8) {
        val nomMethode = "traiterDonnee$numero"
        val typeRetour = types[rng.nextInt(types.size)]
        sb.appendLine()
        sb.appendLine("    /** Méthode générée n°$numero. */")
        sb.appendLine("    public $typeRetour $nomMethode(int borne, String libelle) {")
        sb.appendLine("        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());")
        sb.appendLine("        int total = 0;")
        sb.appendLine("        for (int i = 0; i < borne; i++) {")
        sb.appendLine("            if (i % 3 == 0) {")
        sb.appendLine("                total += i * ${1 + rng.nextInt(9)};")
        sb.appendLine("            } else if (i % 5 == 0) {")
        sb.appendLine("                valeurs.add(i);")
        sb.appendLine("            }")
        sb.appendLine("        }")
        sb.appendLine("        switch (borne % 4) {")
        sb.appendLine("            case 0 -> { return convertir(total, libelle); }")
        sb.appendLine("            case 1 -> { return null; }")
        sb.appendLine("            default -> { /* rien */ }")
        sb.appendLine("        }")
        when (typeRetour) {
            "int", "long" -> sb.appendLine("        return ($typeRetour) total;")
            "double" -> sb.appendLine("        return total * ${1 + rng.nextInt(9)}.$numero;")
            "boolean" -> sb.appendLine("        return total > borne && libelle.length() > 2;")
            "String" -> sb.appendLine("        return libelle + '_' + total;")
            else -> sb.appendLine("        return Optional.of(total);")
        }
        sb.appendLine("    }")
        numero++
    }
    sb.appendLine()
    sb.appendLine("    private static int convertir(int v, String s) {")
    sb.appendLine("        return v + (s == null ? 0 : s.hashCode());")
    sb.appendLine("    }")
    sb.appendLine("}")

    val fichier = File(cible)
    fichier.parentFile?.mkdirs()
    fichier.writeText(sb.toString())
    val lignes = sb.count { it == '\n' } + 1
    println("généré : $cible ($lignes lignes, ${sb.length} caractères, $numero méthodes)")
}

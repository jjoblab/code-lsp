package demo

/** Classe publique de premier niveau — visible du Java. */
class ModeleLie(
    val nom: String,
) {
    var compteur: Int = 0

    fun suivant(): ModeleLie = ModeleLie(nom + "*")
}

/** Classe privée de premier niveau — invisible du Java. */
private class CachePrive

/** Objet public de premier niveau — visible du Java. */
object ConstantesLiees {
    const val MAX = 42
}

/** Énum publique de premier niveau — visible du Java. */
enum class EtatLie {
    OUVERT,
    FERME,
}

/**
 * Fonctions et propriétés top-level : la facade `OutilsKt`.
 * Les signatures CERTAINES sont exposées, les autres écartées.
 */
const val COMPTEUR_DEFAUT: Int = 0

var compteur: Int = COMPTEUR_DEFAUT

fun saluer(nom: String, fois: Int): String {
    return "Bonjour $nom" + "!".repeat(fois)
}

fun transformerTexte(valeur: String): String = valeur.trim()

fun additionner(a: Int, b: Int): Int = a + b

// Type NUT (import inconnu du scanner) : fonction ÉCARTÉE — règle
// conservatrice (jamais un type inventé).
fun transformeNu(valeur: String): ModeleLie = ModeleLie(valeur)

// Paramètre nu non résolvable : ÉCARTÉE aussi.
fun melange(element: ModeleLie, autre: Nimporte): Int = 1

/** Fonction FQCN certain : exposée. */
fun fabrique(): demo.ModeleLie = ModeleLie("x")

// Imbriquée dans un objet : PAS top-level (indentee) — invisible ici.

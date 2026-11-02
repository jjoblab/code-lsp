package jo.codeide.lsp.java.outils

/**
 * Nature d'un jeton lexical Java (étage N7 — fiches 016/017/011/052) :
 * le formateur et le pliage partagent CE lexeur — jamais les accolades
 * d'une chaîne, d'un commentaire ou d'un bloc-texte ne doivent être vues
 * comme des accolades de code (fiche 011 : « bracesInStringsAndCommentsIgnored »).
 */
public enum class NatureJeton {
    /** Identifiant (y compris les mots contextuels : record, sealed, yield). */
    IDENTIFIANT,

    /** Littéral numérique (décimal, hexa, binaire, exposant, suffixe). */
    NOMBRE,

    /** Chaîne de caractères classique. */
    CHAINE,

    /** Bloc-texte `"""..."""` (contenu verbatim, multi-lignes). */
    BLOC_TEXTE,

    /** Littéral caractère. */
    CARACTERE,

    /** Commentaire de ligne `//...` (sans le saut de ligne final). */
    COMMENTAIRE_LIGNE,

    /** Commentaire de bloc (bornes « slash-étoile » … « étoile-slash » incluses). */
    COMMENTAIRE_BLOC,

    /** Javadoc (commentaire documentaire, bornes incluses). */
    JAVADOC,

    /** Mot-clé réservé du langage. */
    MOT_CLE,

    /** Ponctuation ou opérateur. */
    OPERATEUR,
}

/**
 * Un jeton lexical : texte verbatim + position dans la source originale.
 *
 * @property nature la nature du jeton.
 * @property texte le texte EXACT du jeton (jamais réécrit par le lexeur —
 *   les chaînes, blocs-texte et commentaires sont sacrés).
 * @property debut l'offset de début (inclus) dans la source.
 * @property fin l'offset de fin (exclusif) dans la source.
 * @property blancAvant les blancs (espaces, tabulations, sauts de ligne)
 *   qui précèdent le jeton depuis le jeton précédent — le formateur s'en
 *   sert pour distinguer les lignes de continuation des lignes fraîches,
 *   et pour préserver les lignes vides.
 */
public class Jeton(
    public val nature: NatureJeton,
    public val texte: String,
    public val debut: Int,
    public val fin: Int,
    public val blancAvant: String,
)

/**
 * Lexeur Java clean-room (étage N7) : découpe une source en jetons en
 * respectant les littéraux — chaînes (échappements), blocs-texte,
 * caractères — et les commentaires. Aucune dépendance à ecj : le formateur
 * et le pliage sont des fonctions PURES du texte (fiche 011 : « le folding
 * ne dépend d'aucune résolution de type » — même discipline pour le
 * formateur, exécutable tel quel sur Android/ART).
 *
 * La fusion des opérateurs est ASYMÉTRIQUE sur les chevrons : `>=`, `>>=`,
 * `>>>=` et `<<=` sont fusionnés (un signe `=` ne peut pas vivre dans des
 * arguments de type), mais `>>` et `>>>` de décalage binaire ne le sont
 * JAMAIS — deux `>` adjacents restent deux jetons, ce qui permet au
 * formateur de refermer `Map<K, List<V>>` angle par angle.
 */
public object LexeurJava {

    /** Mots-clés réservés qui portent une règle d'espacement propre. */
    private val MOTS_CLES: Set<String> = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
        "null",
    )

    /** Vrai ssi [texte] est un mot-clé réservé du langage. */
    public fun estMotCle(texte: String): Boolean = texte in MOTS_CLES

    /**
     * Lexe la source complète.
     *
     * @param source le texte Java.
     * @return les jetons (le premier porte un [Jeton.blancAvant] réduit aux
     *   blancs de tête), ou null si un littéral est inachevé (chaîne,
     *   caractère ou commentaire de bloc non refermé) — cahier fiche 017 :
     *   « imparsable → null », jamais un texte mutilé.
     */
    public fun lexe(source: String): List<Jeton>? {
        val jetons = ArrayList<Jeton>()
        var i = 0
        var debutBlanc = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c.isWhitespace() -> i++

                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    val fin = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                    ajouter(jetons, source, NatureJeton.COMMENTAIRE_LIGNE, i, fin, debutBlanc)
                    i = fin
                    debutBlanc = fin
                }

                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    val fin = source.indexOf("*/", i + 2)
                    if (fin < 0) return null
                    val nature = if (source.startsWith("/**", i) && fin > i + 3) NatureJeton.JAVADOC else NatureJeton.COMMENTAIRE_BLOC
                    ajouter(jetons, source, nature, i, fin + 2, debutBlanc)
                    i = fin + 2
                    debutBlanc = fin + 2
                }

                c == '"' -> {
                    val fin = finDeChaine(source, i) ?: return null
                    val nature = if (source.startsWith("\"\"\"", i)) NatureJeton.BLOC_TEXTE else NatureJeton.CHAINE
                    ajouter(jetons, source, nature, i, fin, debutBlanc)
                    i = fin
                    debutBlanc = fin
                }

                c == '\'' -> {
                    val fin = finDeCaractere(source, i) ?: return null
                    ajouter(jetons, source, NatureJeton.CARACTERE, i, fin, debutBlanc)
                    i = fin
                    debutBlanc = fin
                }

                c.isJavaIdentifierStart() -> {
                    var j = i + 1
                    while (j < source.length && source[j].isJavaIdentifierPart()) j++
                    ajouter(jetons, source, NatureJeton.IDENTIFIANT, i, j, debutBlanc)
                    i = j
                    debutBlanc = j
                }

                c.isDigit() -> {
                    var j = finDeNombre(source, i)
                    ajouter(jetons, source, NatureJeton.NOMBRE, i, j, debutBlanc)
                    i = j
                    debutBlanc = j
                }

                else -> {
                    val j = finDOperateur(source, i)
                    ajouter(jetons, source, NatureJeton.OPERATEUR, i, j, debutBlanc)
                    i = j
                    debutBlanc = j
                }
            }
        }
        // Promotion IDENTIFIANT → MOT_CLE (le lexeur ne le fait pas à la
        // volée pour garder une seule passe de classification).
        return jetons.map { jeton ->
            if (jeton.nature == NatureJeton.IDENTIFIANT && estMotCle(jeton.texte)) {
                Jeton(NatureJeton.MOT_CLE, jeton.texte, jeton.debut, jeton.fin, jeton.blancAvant)
            } else {
                jeton
            }
        }
    }

    /** Enregistre un jeton avec les blancs qui le précèdent. */
    private fun ajouter(
        jetons: MutableList<Jeton>,
        source: String,
        nature: NatureJeton,
        debut: Int,
        fin: Int,
        debutBlanc: Int,
    ) {
        jetons.add(Jeton(nature, source.substring(debut, fin), debut, fin, source.substring(debutBlanc, debut)))
    }

    /** Fin (exclue) d'une chaîne ou d'un bloc-texte ouverts en [debut]. */
    private fun finDeChaine(source: String, debut: Int): Int? {
        // Bloc-texte : triple guillemet ouvrant et fermant hors échappement.
        if (source.startsWith("\"\"\"", debut)) {
            var i = debut + 3
            while (i < source.length) {
                if (source[i] == '\\') {
                    i += 2
                    continue
                }
                if (source.startsWith("\"\"\"", i)) return i + 3
                i++
            }
            return null
        }
        var i = debut + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                '\n' -> return null
                else -> i++
            }
        }
        return null
    }

    /** Fin (exclue) d'un littéral caractère ouvert en [debut]. */
    private fun finDeCaractere(source: String, debut: Int): Int? {
        var i = debut + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                '\n' -> return null
                else -> i++
            }
        }
        return null
    }

    /** Fin (exclue) d'un littéral numérique commencé en [debut]. */
    private fun finDeNombre(source: String, debut: Int): Int {
        var i = debut
        val hexa = source.startsWith("0x", debut, ignoreCase = true) || source.startsWith("0b", debut, ignoreCase = true)
        while (i < source.length) {
            val c = source[i]
            if (c.isDigit() || c == '_' || c.isLetterOrDigit() && hexa) {
                i++
                continue
            }
            // Fraction : le point n'est retenu que suivi d'un chiffre.
            if (c == '.' && i + 1 < source.length && source[i + 1].isDigit()) {
                i++
                continue
            }
            // Exposant décimal : le signe qui suit e/E appartient au nombre.
            if (!hexa && (c == 'e' || c == 'E') && i + 1 < source.length &&
                (source[i + 1].isDigit() || source[i + 1] == '+' || source[i + 1] == '-')
            ) {
                i += 2
                continue
            }
            break
        }
        return i
    }

    /**
     * Fin (exclue) de l'opérateur commencé en [debut] — fusion maximale
     * SAUF sur `>>`/`>>>` de décalage (voir l'en-tête de l'objet).
     */
    private fun finDOperateur(source: String, debut: Int): Int {
        val a = source[debut]
        val deux = debut + 1 < source.length
        val trois = debut + 2 < source.length
        return when (a) {
            '>' -> when {
                trois && source.startsWith(">>>=", debut) -> debut + 4
                trois && source.startsWith(">>=", debut) -> debut + 3
                deux && source[debut + 1] == '=' -> debut + 2
                else -> debut + 1
            }

            '<' -> when {
                trois && source.startsWith("<<=", debut) -> debut + 3
                deux && source[debut + 1] == '=' -> debut + 2
                else -> debut + 1
            }

            '.' -> if (trois && source.startsWith("...", debut)) debut + 3 else debut + 1

            else -> {
                val paires = setOf("->", "--", "++", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "==", "!=", "&&", "||", "::")
                if (deux && "${a}${source[debut + 1]}" in paires) debut + 2 else debut + 1
            }
        }
    }
}

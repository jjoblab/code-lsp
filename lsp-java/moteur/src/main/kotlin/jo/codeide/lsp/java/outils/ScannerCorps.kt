package jo.codeide.lsp.java.outils

/**
 * Plage d'un corps de méthode, intérieur exclu des accolades.
 *
 * Les bornes désignent l'INTÉRIEUR du corps : `[debutInterieur, finInterieur)`
 * avec `debutInterieur` juste après l'accolade ouvrante et `finInterieur`
 * l'index de l'accolade fermante. Cette convention sert les deux consommateurs :
 *  - [SourceFocuser] efface l'intérieur et garde les accolades (`{}`) ;
 *  - le diagnose rapide remplace l'intérieur par un synthétique de MÊME
 *    LONGUEUR (isomorphie des offsets).
 *
 * @property debutInterieur index du premier caractère de l'intérieur.
 * @property finInterieur index de l'accolade fermante (borne exclusive).
 */
public data class PlageCorps(
    public val debutInterieur: Int,
    public val finInterieur: Int,
) {
    /** Longueur de l'intérieur du corps. */
    public val longueur: Int get() = finInterieur - debutInterieur

    /** Indique si l'offset donné est strictement à l'intérieur du corps. */
    public fun contient(offset: Int): Boolean = offset >= debutInterieur && offset < finInterieur
}

/**
 * Scanner de corps de méthodes sur source Java, insensible aux accolades
 * parasites des chaînes et commentaires (étage N1, fiches 014/041).
 *
 * Le scanner est un automate lexical minimal : il suit les chaînes (avec
 * échappements), les littérares caractères et les commentaires (ligne et
 * bloc), apparie les accolades, et classe chaque bloc ouvert après une
 * liste de paramètres comme **corps de méthode** (règle : l'accolade suit
 * une parenthèse fermante dont le groupe a été ouvert sur un identificateur
 * qui n'est ni précédé de `new`/`.` ni un mot-clé de contrôle, en tolérant
 * une clause `throws`). Les autres accolades (contrôles `if`/`for`/…,
 * initialyseurs, types imbriqués, classes anonymes, lambdas) ne sont pas des
 * corps de méthode au niveau considéré — leurs blocs internes sont couverts
 * par le corps englobant.
 *
 * Un source dont les accolades sont déséquilibrées (frappe à cheval sur la
 * structure) rend un résultat `null` : c'est le signal de repli du diagnose
 * rapide vers un diagnose complet (fiche 014 — éditions structurelles).
 */
public object ScannerCorps {

    /** Mots-clés introduisant un groupe de parenthèses de CONTRÔLE (jamais un en-tête de méthode). */
    private val CONTROLES = setOf("if", "while", "for", "switch", "catch", "synchronized", "return", "throw", "assert", "case", "do", "else", "try", "new")

    /** Mots-clés qui, devant un identificateur suivi de `(`, désignent une DÉCLARATION DE TYPE ou une allocation. */
    private val CREATEURS_TYPE = setOf("class", "interface", "enum", "record", "new")

    /**
     * Inventorie les corps de méthodes du source, dans l'ordre du fichier.
     *
     * @param source le source Java (peut être syntaxiquement cassé — l'appariement
     *   d'accolades reste la seule exigence).
     * @return la liste ordonnée des corps détectés ; `null` si les accolades
     *   sont déséquilibrées (repli structurel).
     */
    public fun corpsDeMethodes(source: String): List<PlageCorps>? {
        val n = source.length
        val corps = mutableListOf<PlageCorps>()
        val pileAccolades = ArrayDeque<Int>() // positions des `{`
        val pileParentheses = ArrayDeque<InfoParenthese>()

        var i = 0
        var dernierSignificatif: String? = null // dernier jeton (identificateur ou symbole)
        var avantDernier: String? = null // jeton précédent
        var teteDeclMethodique = false // vrai juste après `)` d'un en-tête de méthode (ou identifiants `throws`)

        while (i < n) {
            val c = source[i]
            when {
                c == '/' && i + 1 < n && source[i + 1] == '/' -> {
                    i = sautLigne(source, i)
                }

                c == '/' && i + 1 < n && source[i + 1] == '*' -> {
                    val fin = source.indexOf("*/", i + 2)
                    if (fin < 0) return null // commentaire non fermé : source cassé, repli
                    i = fin + 2
                }

                c == '"' -> {
                    val fin = finChaine(source, i)
                    if (fin < 0) return null
                    i = fin
                }

                c == '\'' -> {
                    val fin = finCaractere(source, i)
                    if (fin < 0) return null
                    i = fin
                }

                c.isWhitespace() -> i++

                c == '(' -> {
                    pileParentheses.addLast(InfoParenthese(dernierSignificatif, avantDernier))
                    i++
                }

                c == ')' -> {
                    val info = pileParentheses.removeLastOrNull() ?: return null // parenthèse orpheline : repli
                    teteDeclMethodique = info.estEnTeteDeMethode()
                    i++
                }

                c == '{' -> {
                    if (teteDeclMethodique) {
                        corps.add(PlageCorps(i + 1, -1)) // fin résolue à la fermeture
                    }
                    pileAccolades.addLast(i)
                    teteDeclMethodique = false
                    i++
                }

                c == '}' -> {
                    val ouverture = pileAccolades.removeLastOrNull() ?: return null // accolade orpheline : repli
                    // Résoudre la fin du corps ouvert juste après cette accolade.
                    val indexCorps = corps.indexOfFirst { it.finInterieur == -1 && it.debutInterieur == ouverture + 1 }
                    if (indexCorps >= 0) {
                        corps[indexCorps] = PlageCorps(ouverture + 1, i)
                    }
                    i++
                }

                c.isJavaIdentifierStart() -> {
                    var j = i + 1
                    while (j < n && source[j].isJavaIdentifierPart()) j++
                    avantDernier = dernierSignificatif
                    dernierSignificatif = source.substring(i, j)
                    // Les identificateurs entre `)` d'en-tête et `{` étendent la
                    // clause throws : la tête de déclaration reste vivante.
                    i = j
                }

                else -> {
                    // Symbole quelconque ; `throws` toléré entre `)` et `{` via dernierSignificatif.
                    if (!(teteDeclMethodique && c == ',')) {
                        teteDeclMethodique = false
                    }
                    avantDernier = dernierSignificatif
                    dernierSignificatif = c.toString()
                    i++
                }
            }
        }
        if (pileAccolades.isNotEmpty()) return null // accolade jamais fermée : repli
        // Corps jamais refermés (fin de fichier cassée) : les éliminer (structure incomplète).
        return corps.filter { it.finInterieur >= 0 }
    }

    /** Fin d'une chaîne Java ( gestion des échappements `\"` ), index juste après le guillemet fermant. */
    private fun finChaine(source: String, debut: Int): Int {
        var i = debut + 1
        val n = source.length
        while (i < n) {
            when (source[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                '\n' -> return -1 // chaîne non fermée sur la ligne : source cassé, repli
                else -> i++
            }
        }
        return -1
    }

    /** Fin d'un littéral caractère, index juste après l'apostrophe fermante. */
    private fun finCaractere(source: String, debut: Int): Int {
        var i = debut + 1
        val n = source.length
        while (i < n) {
            when (source[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                '\n' -> return -1
                else -> i++
            }
        }
        return -1
    }

    /** Fin d'un commentaire de ligne (juste après le retour ligne ou la fin). */
    private fun sautLigne(source: String, debut: Int): Int {
        val fin = source.indexOf('\n', debut)
        return if (fin < 0) source.length else fin + 1
    }

    /** Contexte d'un groupe de parenthèses : jetons entourant l'ouverture. */
    private class InfoParenthese(val precedent: String?, val avantPrecedent: String?) {
        /**
         * Vrai ssi ce groupe peut être la liste de paramètres d'un en-tête de
         * méthode/constructeur : ouvert sur un IDENTIFICATEUR (nom de membre)
         * non précédé de `new`/`.`/mot-clé de contrôle ni de déclaration de type.
         */
        fun estEnTeteDeMethode(): Boolean {
            val nom = precedent ?: return false
            if (nom.length == 1) return false // symbole — pas un nom de méthode
            if (nom in CONTROLES) return false
            if (nom in CREATEURS_TYPE) return false
            return when (avantPrecedent) {
                null, "new", ".", "::", "class", "interface", "enum", "record" -> false
                else -> true
            }
        }
    }
}

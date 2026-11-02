package jo.codeide.lsp.java.actionscode

/**
 * Analyseur textuel du paquet des correcteurs (étage N6) : les primitives
 * de découpage DU TEXTE Java — paires d'accolades, bornes d'instruction,
 * bornes de ligne, indentation — partagées par tous les correcteurs texte
 * (fiche 063) et par le correcteur de création de méthode (fiche 064).
 *
 * L'analyseur est PRUDENT par construction (fiche 063, règles de prudence) :
 * il ignore chaînes, caractères et commentaires pour l'appariement des
 * accolades et la recherche des points-virgules ; tout doute structurel
 * (borne introuvable, construction non identifiée) rend une réponse NULLE —
 * le correcteur appelant s'abstient alors, il ne devine JAMAIS.
 *
 * Limites documentées (honnêteté) : le recul d'en-tête vers la borne
 * précédente est un balayage brut (une chaîne contenant « ; » dans une
 * annotation AVANT l'en-tête peut leurrer la coupe — hors corpus du cahier,
 * l'abstention protège l'utilisateur). Les correcteurs N6 sont des
 * correcteurs de TEXTE mono-fichier : la sémantique fine relève des
 * protocoles dédiés (N5 renommage, N8 hôte).
 */
internal object AnalyseTexte {

    /** Une paire d'accolades appariées : positions de '{' et de '}' qui se correspond. */
    internal data class PaireAccolades(public val ouverture: Int, public val fermeture: Int)

    /**
     * Apparie TOUTES les accolades de la source, en ignorant chaînes,
     * caractères, commentaires de ligne et de bloc.
     *
     * @param source le texte Java complet.
     * @return les paires (imbrication quelconque) ; une accolade non fermée
     *   n'apparaît pas (prudence).
     */
    internal fun pairesAccolades(source: String): List<PaireAccolades> {
        val paires = ArrayList<PaireAccolades>()
        val pile = ArrayDeque<Int>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    i = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                }
                c == '"' -> {
                    // -1 : le « i++ » de boucle doit retomber sur le caractère
                    // JUSTE APRÈS la chaîne — jamais le sauter (sinon une
                    // « ) » ou « } » collée au littéral serait perdue).
                    i = finDeChaine(source, i) - 1
                }
                c == '\'' -> {
                    i = finDeCaractere(source, i) - 1
                }
                c == '{' -> pile.addLast(i)
                c == '}' -> {
                    val ouverture = pile.removeLastOrNull() ?: return emptyList()
                    paires.add(PaireAccolades(ouverture, i))
                }
            }
            i++
        }
        return paires
    }

    /** La fin d'un littéral de chaîne (guillemet d'ouverture en [debut]). */
    private fun finDeChaine(source: String, debut: Int): Int {
        var i = debut + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                '\n' -> return i // chaîne non fermée : prudent, on s'arrête.
                else -> i++
            }
        }
        return i
    }

    /** La fin d'un littéral de caractère (apostrophe d'ouverture en [debut]). */
    private fun finDeCaractere(source: String, debut: Int): Int {
        var i = debut + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                '\n' -> return i
                else -> i++
            }
        }
        return i
    }

    /**
     * Le point-virgule suivant à profondeur 0 (hors chaînes et commentaires)
     * à partir de [debut] — la fin NATURELLE d'une instruction.
     *
     * @param source le texte Java complet.
     * @param debut l'offset d'où partir (dans l'instruction).
     * @return la position du « ; », ou null (source tronquée).
     */
    internal fun pointVirguleSuivant(source: String, debut: Int): Int? {
        var i = debut.coerceIn(0, source.length)
        var profondeur = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    i = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                }
                c == '"' -> i = finDeChaine(source, i) - 1
                c == '\'' -> i = finDeCaractere(source, i) - 1
                c == '(' || c == '[' || c == '{' -> profondeur++
                c == ')' || c == ']' || c == '}' -> profondeur--
                c == ';' && profondeur <= 0 -> return i
            }
            i++
        }
        return null
    }

    /**
     * Le début de l'instruction contenant [offset] : juste après la borne
     * précédente (« ; », « { » ou « } »), blancs et sauts de ligne sautés.
     *
     * @param source le texte Java complet.
     * @param offset une position DANS l'instruction.
     * @return l'offset du premier caractère de l'instruction (ou la fin de
     *   la source si tout est blanc).
     */
    internal fun debutInstruction(source: String, offset: Int): Int {
        var i = offset.coerceIn(0, source.length)
        while (i > 0) {
            val c = source[i - 1]
            if (c == ';' || c == '{' || c == '}') break
            i--
        }
        while (i < source.length && source[i].isWhitespace()) i++
        return i
    }

    /**
     * Vrai ssi la position [depuis] vit DANS une parenthèse ouverte entre
     * elle et la borne [jusqua] — la garde « paramètre » de la fiche 063 :
     * un diagnostic vivant dans une liste de paramètres (ou un en-tête de
     * for / lambda) ne se SUPPRIME jamais.
     *
     * Le recul suit la PROFONDEUR : une « ) » rencontrée en remontant ferme
     * un groupe interne (profondeur +1), une « ( » qui fait passer la
     * profondeur NÉGATIVE est celle du groupe ENGLOBANT — la position est
     * dedans. Une borne d'instruction (« ; », « { », « } ») atteinte en
     * profondeur positive ou nulle clôt le recul : hors parenthèses.
     *
     * @param source le texte Java complet.
     * @param depuis l'offset de départ du recul (exclu).
     * @param jusqua la borne du recul (incluse).
     */
    internal fun dansParenthesesAvant(source: String, depuis: Int, jusqua: Int): Boolean {
        var i = minOf(depuis, source.length) - 1
        var profondeur = 0
        while (i >= jusqua) {
            val c = source[i]
            when (c) {
                ')' -> profondeur++
                '(' -> {
                    profondeur--
                    if (profondeur < 0) return true
                }
                ';', '{', '}' -> return false
            }
            i--
        }
        return false
    }

    /**
     * Comptage des virgules à PROFONDEUR 0 dans [debut, fin) — la garde
     * « multi-fragments » de la fiche 063 : « int a = 1, b = 2; » ne se
     * supprime jamais (risque de supprimer la variable sœur).
     *
     * @param source le texte Java complet.
     * @param debut borne incluse.
     * @param fin borne exclue.
     * @return le nombre de virgules de profondeur 0 (parenthèses, crochets,
     *   chevrons et chaînes exclus).
     */
    internal fun virgulesProfondeurZero(source: String, debut: Int, fin: Int): Int {
        var i = debut.coerceIn(0, source.length)
        val borne = fin.coerceIn(0, source.length)
        var profondeur = 0
        var virgules = 0
        while (i < borne) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    i = source.indexOf('\n', i).let { if (it < 0 || it >= borne) borne else it }
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i = source.indexOf("*/", i + 2).let { if (it < 0 || it + 2 >= borne) borne else it + 2 }
                }
                c == '"' -> i = finDeChaine(source, i) - 1
                c == '\'' -> i = finDeCaractere(source, i) - 1
                c == '(' || c == '[' || c == '<' -> profondeur++
                c == ')' || c == ']' || c == '>' -> profondeur--
                c == ',' && profondeur <= 0 -> virgules++
            }
            i++
        }
        return virgules
    }

    /**
     * Les bornes de la LIGNE contenant [offset] : début de ligne (après le
     * « \n » précédent) et fin de ligne AVANT le « \n » suivant (le saut
     * n'est PAS inclus).
     *
     * @param source le texte Java complet.
     * @param offset une position sur la ligne.
     * @return la paire (début de ligne, fin de ligne exclusive).
     */
    internal fun bornesLigne(source: String, offset: Int): Pair<Int, Int> {
        val position = offset.coerceIn(0, source.length)
        val debut = source.lastIndexOf('\n', position - 1).let { if (it < 0) 0 else it + 1 }
        val fin = source.indexOf('\n', position).let { if (it < 0) source.length else it }
        return debut to fin
    }

    /**
     * L'indentation de la ligne contenant [offset] : les blancs ininterrompus
     * depuis le début de ligne.
     *
     * @param source le texte Java complet.
     * @param offset une position sur la ligne.
     * @return le préfixe d'indentation (espaces et tabulations).
     */
    internal fun indentation(source: String, offset: Int): String {
        val (debut, _) = bornesLigne(source, offset)
        var fin = debut
        while (fin < source.length && (source[fin] == ' ' || source[fin] == '\t')) fin++
        return source.substring(debut, fin)
    }
}

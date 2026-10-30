package jo.codeide.lsp.java.signature

import jo.codeide.lsp.java.interne.AideSignature
import jo.codeide.lsp.java.interne.CandidatSignature
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.LectureAst.VisiteurResilient
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding

/**
 * Aide de signatures étendue (étage N3 — cahier fiche 023) : l'énumération
 * des surcharges d'un appel — binaires comprises —, le comptage du
 * paramètre actif, et le « niveau 4 » : le texte vivant CASSÉ (parenthèse
 * ouverte, virgule traînante, argument à moitié tapé, chaîne non refermée,
 * constructeur ouvert, paramètre booléen) est quand même servi via des
 * variantes réparées ; hors appel, la réponse reste NULL (garde absolue).
 *
 * Extension du service N1 (fiche 015) : le résolu arrive en tête
 * (signature active 0), puis les autres surcharges du sélecteur dans
 * l'ordre de déclaration — labels `retour selecteur(T1 n1, T2 n2)` quand
 * les noms de paramètres sont disponibles (source, ou binaire documentée),
 * `retour selecteur(T1, T2)` sinon.
 *
 * Le niveau 4 est une famille de variantes de RÉPARATION générées depuis
 * l'état lexical au caret (famille A/B/D/F du cahier) :
 *  - A — littéral ouvert : refermé au caret ;
 *  - B — virgule traînante : littéral de secours apposé ;
 *  - D — identifiant à moitié tapé : substitué par un littéral ;
 *  - F — position d'argument vide : littéral candidat inséré (`0`, puis
 *    `true`, `null`, `"x"` — le premier qui résout gagne) ;
 * puis la queue équilibrée referme les délimiteurs ouverts. La garde est
 * structurelle : une variante ne sert QUE si un appel RÉSOLU couvre le
 * caret dans l'arbre réparé — hors appel, rien n'est inventé.
 */
public object AideSignatures {

    /** Littéraux de secours du niveau 4, du plus large au plus typé. */
    private val LITTERAUX_SECOURS = listOf("0", "true", "null", "\"x\"")

    /**
     * Aide de signature au caret sur un arbre RÉSOLU : l'appel (envoi ou
     * allocation) le plus profond dont la plage couvre le caret, ses
     * surcharges, et le paramètre actif.
     *
     * @param arbre l'arbre résolu (bindings vivants).
     * @param source le texte source dans le système de l'arbre.
     * @param offset la position du caret.
     * @return l'aide, ou null si aucun appel ne couvre l'offset.
     */
    public fun aide(arbre: CompilationUnitDeclaration?, source: String, offset: Int): AideSignature? {
        if (arbre == null) return null
        val collecteur = CollecteurAppels()
        LectureAst.traverserResilient(arbre, collecteur)
        val enveloppant = collecteur.appels
            .filter { it.contient(offset) }
            .minByOrNull { it.fin - it.debut } ?: return null
        return enveloppant.aide(source, offset)
    }

    /**
     * Comptage du paramètre actif : le nombre de VIRGULES de séparation
     * avant le caret, à profondeur zéro de la liste, HORS littéraux — la
     * virgule d'une chaîne n'est jamais un séparateur (fiche 023).
     *
     * @param source le texte source.
     * @param debutListe l'offset du premier caractère de la liste
     *   d'arguments (juste après la parenthèse ouvrante).
     * @param caret la position du caret.
     * @return l'index d'argument en cours de saisie.
     */
    public fun compterParametreActif(source: String, debutListe: Int, caret: Int): Int {
        var compte = 0
        var profondeur = 0
        var dansChaine = false
        var dansCaractere = false
        var echappe = false
        val borne = caret.coerceAtMost(source.length)
        var i = debutListe.coerceAtLeast(0)
        while (i < borne) {
            val c = source[i]
            if (echappe) {
                echappe = false
            } else if (c == '\\') {
                echappe = true
            } else if (dansChaine) {
                if (c == '"') dansChaine = false
            } else if (dansCaractere) {
                if (c == '\'') dansCaractere = false
            } else {
                when (c) {
                    '"' -> dansChaine = true
                    '\'' -> dansCaractere = true
                    '(', '[', '{' -> profondeur++
                    ')', ']', '}' -> if (profondeur > 0) profondeur--
                    ',' -> if (profondeur == 0) compte++
                }
            }
            i++
        }
        return compte
    }

    /**
     * Variantes réparées du niveau 4 (fiche 023) : depuis l'état lexical au
     * caret — littéral ouvert, identifiant partiel en position
     * d'argument, ou position d'argument vide après «(» ou «,» —, rend les
     * textes candidats (l'original n'en fait PAS partie : le chemin normal
     * l'a déjà tenté).
     *
     * v1.1.6 (retours appareil) : la fermeture de la LISTE D'ARGUMENTS
     * s'insère désormais AVANT la première accolade fermante d'un bloc
     * ouvert avant le caret — l'ancienne queue concaténée à la FIN du
     * texte plaçait le «)» APRÈS les «}» englobants (`f(a, 0\n})` —
     * syntaxe cassée, appel perdu) : la virgule traînante
     * `repeter("monip", ` ne rendait RIEN.
     *
     * @param source le texte vivant.
     * @param caret la position du caret.
     * @return les variantes réparées, dans l'ordre d'essai.
     */
    public fun variantesReparees(source: String, caret: Int): List<String> {
        if (caret < 0 || caret > source.length) return emptyList()
        val etat = EtatLexical(source, caret)
        if (!etat.parentheseOuverte) return emptyList()
        val variantes = mutableListOf<String>()
        when {
            etat.dansLitteral -> {
                // Famille A : refermer le littéral OUVERT au caret.
                val fermeture = if (etat.dansChaine) "\"" else "'"
                val base = StringBuilder(source).insert(caret, fermeture).toString()
                variantes.add(refermer(base, caret))
            }

            etat.identifiantPartiel != null -> {
                // Famille D : substituer l'identifiant à moitié tapé.
                val debut = etat.identifiantPartiel ?: 0
                for (litteral in LITTERAUX_SECOURS) {
                    val base = source.substring(0, debut) + litteral + source.substring(caret)
                    variantes.add(refermer(base, debut + litteral.length))
                }
            }

            etat.positionArgument -> {
                // Familles B/F : apposer un littéral de secours au caret.
                for (litteral in LITTERAUX_SECOURS) {
                    val base = StringBuilder(source).insert(caret, litteral).toString()
                    variantes.add(refermer(base, caret + litteral.length))
                }
            }
        }
        return variantes.distinct()
    }

    /**
     * Referme un texte réparé autour du point d'insertion (v1.1.6 —
     * restructuration de l'ancienne queue équilibrée concaténée à la fin).
     *
     * Règle : les listes (`(`, `[`) ouvertes AVANT le caret se referment
     * À L'INTÉRIEUR du bloc englobant — l'insertion se fait juste AVANT la
     * première accolade fermante qui referme un bloc ouvert avant le caret
     * (`f(a, 0);` puis le `}` du corps — l'appel devient une instruction
     * COMPLÈTE du bloc). Le `;` termine l'instruction ; les accolades
     * restent ouvertes pour que la suite du texte les referme à leur
     * place. Sans telle accolade dans la suite (caret en fin de fichier,
     * queue classique), la fermeture complète est concaténée à la fin —
     * l'ancien comportement : listes refermées, `;` avant la première
     * accolade fermante, accolades refermées du plus interne au plus
     * externe.
     *
     * @param texte le texte candidat (littéral déjà inséré).
     * @param insertion la position juste APRÈS le littéral inséré (le
     *   « caret » du texte candidat).
     * @return le texte refermé.
     */
    private fun refermer(texte: String, insertion: Int): String {
        // Phase 1 — pile des ouvrants AVANT l'insertion (littéraux franchis).
        // Chaque élément porte son ORIGINE : ouvert avant le caret (true) ou
        // dans la suite (false) — seules les listes d'AVANT le caret exigent
        // la fermeture avant l'accolade du bloc englobant.
        val pileAvant = ArrayDeque<Pair<Char, Boolean>>()
        var dansChaine = false
        var dansCaractere = false
        var echappe = false
        val borneAvant = insertion.coerceIn(0, texte.length)
        var i = 0
        while (i < borneAvant) {
            val c = texte[i]
            if (echappe) {
                echappe = false
            } else if (c == '\\') {
                echappe = true
            } else if (dansChaine) {
                if (c == '"') dansChaine = false
            } else if (dansCaractere) {
                if (c == '\'') dansCaractere = false
            } else {
                when (c) {
                    '"' -> dansChaine = true
                    '\'' -> dansCaractere = true
                    '(', '[', '{' -> pileAvant.addLast(c to true)
                    ')' -> if (pileAvant.isNotEmpty() && pileAvant.last().first == '(') pileAvant.removeLast()
                    ']' -> if (pileAvant.isNotEmpty() && pileAvant.last().first == '[') pileAvant.removeLast()
                    '}' -> if (pileAvant.isNotEmpty() && pileAvant.last().first == '{') pileAvant.removeLast()
                }
            }
            i++
        }
        // Listes ouvertes avant le caret encore non refermées.
        var listesOuvertes = pileAvant.count { it.second && (it.first == '(' || it.first == '[') }

        // Phase 2 — recherche du point de COUPE : la première accolade
        // fermante rencontrée alors que des listes ouvertes avant le caret
        // restent non refermées. C'est l'accolade qui referme le bloc
        // englobant (ouvrante d'avant le caret) : la fermeture de l'appel
        // doit précéder — sinon le «)» atterrirait après le «}» (v1.1.6).
        val pile = ArrayDeque(pileAvant)
        var coupe = -1
        while (i < texte.length) {
            val c = texte[i]
            if (echappe) {
                echappe = false
            } else if (dansChaine) {
                if (c == '"') dansChaine = false
            } else if (dansCaractere) {
                if (c == '\'') dansCaractere = false
            } else {
                when (c) {
                    '"' -> dansChaine = true
                    '\'' -> dansCaractere = true
                    '(', '[', '{' -> pile.addLast(c to false)
                    ')' -> if (pile.isNotEmpty() && pile.last().first == '(') {
                        if (pile.removeLast().second) listesOuvertes--
                    }
                    ']' -> if (pile.isNotEmpty() && pile.last().first == '[') {
                        if (pile.removeLast().second) listesOuvertes--
                    }
                    '}' -> {
                        if (listesOuvertes > 0) {
                            coupe = i
                            break
                        }
                        if (pile.isNotEmpty() && pile.last().first == '{') pile.removeLast()
                    }
                }
            }
            i++
        }

        // Pas de coupe : fermeture complète concaténée à la fin (l'ancienne
        // queue — caret en fin de texte, toutes les listes refermées d'un
        // coup, `;` avant la première accolade fermante).
        if (coupe < 0) {
            val fermetures = buildString {
                var instructionTerminee = pile.isEmpty() || pile.last().first != '{'
                while (pile.isNotEmpty()) {
                    when (val ouvrant = pile.removeLast().first) {
                        '{' -> {
                            if (!instructionTerminee) {
                                append(';')
                                instructionTerminee = true
                            }
                            append('}')
                        }
                        '(' -> append(')')
                        else -> append(']')
                    }
                }
                if (!instructionTerminee) append(';')
            }
            return texte + fermetures
        }

        // Coupe trouvée : refermer les listes restantes JUSTE AVANT
        // l'accolade du bloc englobant, l'instruction terminée par «;» —
        // les accolades ouvertes restent à la charge du texte suivant.
        val fermante = buildString {
            while (pile.isNotEmpty()) {
                when (val ouvrant = pile.removeLast().first) {
                    '{' -> break // le bloc englobant : refermé par le texte
                    '(' -> append(')')
                    else -> append(']')
                }
            }
            append(';')
        }
        return texte.substring(0, coupe) + fermante + texte.substring(coupe)
    }

    /**
     * État lexical au caret : littéral ouvert, parenthèse d'appel ouverte,
     * identifiant partiel, position d'argument vide.
     */
    private class EtatLexical(private val source: String, private val caret: Int) {
        var dansChaine = false
        var dansCaractere = false
        var parentheseOuverte = false
        var positionArgument = false
        var identifiantPartiel: Int? = null

        init {
            var echappe = false
            var profondeur = 0
            var dernierOuvrant = -1
            var i = 0
            while (i < caret) {
                val c = source[i]
                if (echappe) {
                    echappe = false
                } else if (c == '\\') {
                    echappe = true
                } else if (dansChaine) {
                    if (c == '"') dansChaine = false
                } else if (dansCaractere) {
                    if (c == '\'') dansCaractere = false
                } else {
                    when (c) {
                        '"' -> dansChaine = true
                        '\'' -> dansCaractere = true
                        '(', '[', '{' -> {
                            profondeur++
                            dernierOuvrant = i
                        }
                        ')', ']', '}' -> if (profondeur > 0) profondeur--
                    }
                }
                i++
            }
            // Parenthèse d'APPEL encore ouverte : le dernier ouvrant non
            // fermé est une parenthèse (pas un crochet ni une accolade)
            // précédée d'un identifiant, d'une fermeture ou d'un crochet —
            // la position d'un appel, pas d'un bloc de contrôle nu.
            parentheseOuverte = profondeur > 0 && dernierOuvrant >= 0 &&
                source[dernierOuvrant] == '(' && estPositionAppel(dernierOuvrant)
            // Identification de la famille de réparation quand une
            // parenthèse d'appel est ouverte : identifiant partiel en
            // position d'argument, sinon position d'argument vide.
            if (parentheseOuverte && !dansLitteral) {
                var debut = caret
                while (debut > 0 && source[debut - 1].isJavaIdentifierPart()) debut--
                if (debut < caret && ouvreListe(source, debut)) {
                    identifiantPartiel = debut
                } else if (ouvreListe(source, caret)) {
                    positionArgument = true
                }
            }
        }

        /** Vrai si le caractère non blanc précédant la position ouvre une liste. */
        private fun ouvreListe(texte: String, position: Int): Boolean {
            var i = position
            while (i > 0 && texte[i - 1].isWhitespace()) i--
            if (i == 0) return false
            return texte[i - 1] == '(' || texte[i - 1] == ','
        }

        /** Vrai si la parenthèse ouvrante est en position d'appel. */
        private fun estPositionAppel(ouvrant: Int): Boolean {
            var i = ouvrant
            while (i > 0 && source[i - 1].isWhitespace()) i--
            if (i == 0) return false
            val c = source[i - 1]
            return c.isJavaIdentifierPart() || c == ')' || c == ']'
        }

        /** Littéral (chaîne ou caractère) ouvert au caret. */
        val dansLitteral: Boolean
            get() = dansChaine || dansCaractere
    }

    /** Un appel (envoi ou allocation) repéré, avec sa plage et ses arguments. */
    private class AppelEnveloppant(
        val debut: Int,
        val fin: Int,
        val arguments: List<Pair<Int, Int>>,
        val binding: MethodBinding?,
        val allocation: Boolean,
    ) {
        fun contient(offset: Int): Boolean = offset in debut..fin

        /**
         * Construit l'aide au caret : le résolu en tête, puis les
         * surcharges (getMethods pour un envoi, constructeurs pour une
         * allocation) ; le paramètre actif compte les virgules de
         * séparation hors littéraux, borné au nombre de paramètres.
         */
        fun aide(source: String, caret: Int): AideSignature {
            val candidats = mutableListOf<CandidatSignature>()
            val vus = mutableSetOf<String>()
            if (binding != null && binding.isValidBinding) {
                candidat(binding)?.let {
                    candidats.add(it)
                    vus.add(cle(binding))
                }
            }
            val surcharges = surcharges()
            for (surcharge in surcharges) {
                if (!surcharge.isValidBinding) continue
                val cle = cle(surcharge)
                if (cle in vus) continue
                candidat(surcharge)?.let {
                    candidats.add(it)
                    vus.add(cle)
                }
            }
            val debutListe = debutDeListe(source, caret)
            val nbParametres = binding?.parameters?.size ?: candidats.maxOfOrNull { it.parametres.size } ?: 0
            val actif = compterParametreActif(source, debutListe, caret).coerceAtMost(nbParametres)
            return AideSignature(
                signatures = candidats,
                signatureActive = 0,
                parametreActif = actif,
            )
        }

        /** Le candidat d'affichage d'un binding : label et noms de paramètres. */
        private fun candidat(binding: MethodBinding): CandidatSignature? {
            val selecteur = if (allocation) {
                court(binding.declaringClass) ?: LectureAst.lisible(binding.declaringClass) ?: return null
            } else {
                String(binding.selector)
            }
            val parametres = binding.parameters ?: emptyArray()
            val noms = binding.parameterNames
            val libelle = buildString {
                if (!allocation && !binding.isConstructor()) {
                    append(LectureAst.lisible(binding.returnType) ?: "?")
                    append(' ')
                }
                append(selecteur)
                append('(')
                append(
                    parametres.withIndex().joinToString(", ") { (i, type) ->
                        val nom = noms?.getOrNull(i)?.let(::String)
                        LectureAst.lisible(type)?.let { lisible ->
                            if (nom != null) "$lisible $nom" else lisible
                        } ?: nom ?: "?"
                    },
                )
                append(')')
            }
            val listeNoms = parametres.withIndex().map { (i, _) ->
                noms?.getOrNull(i)?.let(::String) ?: LectureAst.lisible(parametres[i]) ?: "?"
            }
            return CandidatSignature(libelle, listeNoms)
        }

        /** Clé de dédoublonnage d'un binding (sélecteur + types de paramètres). */
        private fun cle(binding: MethodBinding): String = buildString {
            append(String(binding.selector))
            append('(')
            append((binding.parameters ?: emptyArray()).joinToString(",") { LectureAst.lisible(it) ?: "?" })
            append(')')
        }

        /** Surcharges du sélecteur sur la classe déclarante (binaires comprises). */
        private fun surcharges(): List<MethodBinding> {
            val b = binding ?: return emptyList()
            val classe = b.declaringClass as? ReferenceBinding ?: return emptyList()
            return try {
                if (allocation) {
                    (classe.availableMethods() ?: emptyArray()).filter { it.isConstructor() }
                } else {
                    classe.getMethods(b.selector).toList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        /** Nom court d'un type pour les libellés de constructeurs. */
        private fun court(type: org.eclipse.jdt.internal.compiler.lookup.TypeBinding?): String? = try {
            type?.shortReadableName()?.let(::String)
        } catch (_: Exception) {
            null
        }

        /**
         * Début de la liste d'arguments : balayage avant depuis le début de
         * l'appel avec une PILE de parenthèses (littéraux franchis) — au
         * caret, le sommet ouvre la liste de l'appel le plus PROFOND, pas
         * la première parenthèse du chaînage. Pile vide (caret avant la
         * liste) : le caret lui-même — zéro séparateur compté.
         */
        private fun debutDeListe(source: String, caret: Int): Int {
            val pile = mutableListOf<Int>()
            var dansChaine = false
            var dansCaractere = false
            var echappe = false
            val borne = caret.coerceAtMost(source.length)
            var i = debut.coerceAtLeast(0)
            while (i < borne) {
                val c = source[i]
                if (echappe) {
                    echappe = false
                } else if (dansChaine) {
                    if (c == '"') dansChaine = false
                } else if (dansCaractere) {
                    if (c == '\'') dansCaractere = false
                } else {
                    when (c) {
                        '"' -> dansChaine = true
                        '\'' -> dansCaractere = true
                        '(' -> pile.add(i)
                        ')' -> if (pile.isNotEmpty()) pile.removeAt(pile.size - 1)
                    }
                }
                i++
            }
            return if (pile.isEmpty()) caret else pile.last() + 1
        }
    }

    /** Collecteur des appels (envois ET allocations — fiche 023). */
    private class CollecteurAppels : VisiteurResilient() {
        val appels = mutableListOf<AppelEnveloppant>()

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val arguments = node.arguments ?: emptyArray()
            appels.add(
                AppelEnveloppant(
                    debut = node.sourceStart,
                    fin = node.sourceEnd,
                    arguments = arguments.map { it.sourceStart to it.sourceEnd },
                    binding = node.binding as? MethodBinding,
                    allocation = false,
                ),
            )
            return true
        }

        override fun visiteAllocation(node: AllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val arguments = node.arguments ?: emptyArray()
            appels.add(
                AppelEnveloppant(
                    debut = node.sourceStart,
                    fin = node.sourceEnd,
                    arguments = arguments.map { it.sourceStart to it.sourceEnd },
                    binding = node.binding as? MethodBinding,
                    allocation = true,
                ),
            )
            return true
        }
    }
}

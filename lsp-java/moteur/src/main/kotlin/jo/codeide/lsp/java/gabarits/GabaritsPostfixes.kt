package jo.codeide.lsp.java.gabarits

import jo.codeide.lsp.java.outils.EditionTexte

/**
 * Un gabarit postfixe PROPOSÉ (étage N7 — cahier fiche 029) : l'item et
 * ses éditions additionnelles, exprimées en offsets de la source
 * ORIGINALE — elles ne chevauchent JAMAIS la plage principale de l'item
 * (la clé remplacée par le snippet).
 *
 * @property cle la clé postfixe (`sout`, `var`, `opt`…).
 * @property extrait le snippet inséré.
 * @property editions les éditions additionnelles : suppression du
 *   récepteur (et de son point), insertion d'import le cas échéant.
 * @property pertinence la pertinence contractuelle (clé exacte).
 */
public data class ItemPostfixe(
    public val cle: String,
    public val extrait: String,
    public val editions: List<EditionTexte>,
    public val pertinence: Int,
)

/**
 * Gabarits postfixes v1.3.0 (étage N7 — cahier fiche 029) : `expr.var`,
 * `.sout`, `.opt`, `.if`, `.cast`… La conscience de type se joue SANS
 * binding d'abord (prédicats : toute valeur, référence, booléen) puis
 * AVEC binding à l'intégration moteur : une template ne sort jamais là où
 * elle ne compilerait pas.
 */
public object GabaritsPostfixes {

    /** Pertinence d'une postfixe (clé exacte). */
    public const val PERTINENCE: Int = 2500

    /** Les types primitifs — une postfixe de référence ne s'y propose pas. */
    private val PRIMITIFS: Set<String> = setOf(
        "boolean", "byte", "short", "int", "long", "char", "float", "double", "void",
    )

    /**
     * Scanne le RÉCEPTEUR d'une postfixe : l'expression entière AVANT le
     * point porteur de la clé (fiche 029 — « list », « foo() », « arr[i] »,
     * « a.b().c » avant le DERNIER point, « "texte" » guillemets compris).
     *
     * Le balayage arrière traverse les groupements appariés (parenthèses,
     * crochets, accolades) et les points de chaînage qualifiés ; il
     * s'arrête au début de la source, sur un blanc, ou sur un délimiteur
     * d'énoncé (`;`, `,`, `=`, `:`) — les accolades ouvrantes et
     * parenthèses ouvrantes NON appariées bornent l'expression.
     *
     * @param source le texte source complet (ou l'extrait de test).
     * @param debutCle l'offset du premier caractère de la clé.
     * @return la plage du récepteur, ou null (pas de point, expression vide).
     */
    public fun scannerRecepteur(source: String, debutCle: Int): IntRange? {
        if (debutCle <= 1) return null
        // Le point porteur de la clé précède immédiatement la clé.
        if (source[debutCle - 1] != '.') return null
        var i = debutCle - 2
        // Littéral de chaîne en récepteur : remonter jusqu'au guillemet ouvrant.
        if (i >= 0 && source[i] == '"') {
            var j = i - 1
            while (j >= 0) {
                if (source[j] == '"' && (j == 0 || source[j - 1] != '\\')) return j..i
                j--
            }
            return null
        }
        var profondeur = 0
        val debut = i
        while (i >= 0) {
            val c = source[i]
            when {
                c == ')' || c == ']' || c == '}' -> profondeur++

                c == '(' || c == '[' || c == '{' -> {
                    profondeur--
                    // Une ouvrante non appariée BORNE l'expression (argument,
                    // corps, indice).
                    if (profondeur < 0) return if (i == debut) null else (i + 1)..debut
                }

                c.isWhitespace() && profondeur == 0 -> return if (i == debut) null else (i + 1)..debut

                c == ';' || c == ',' || c == '=' || c == ':' -> {
                    if (profondeur == 0) return if (i == debut) null else (i + 1)..debut
                }
            }
            i--
        }
        return 0..debut
    }

    /**
     * Gabarits postfixes applicables à `recepteur.cle`.
     *
     * Prédicats de conscience de type (fiche 029) : sans type résolu, seules
     * les postfixes TOUTE VALEUR sortent (`sout`, `var`, `cast`) — `opt`
     * exige une référence résolue, `if` un booléen ; une template ne sort
     * jamais là où elle ne compilerait pas.
     *
     * @param source la source (pour l'ancre d'import des postfixes importeuses).
     * @param debutCle l'offset de la clé dans la source.
     * @param cle la clé postfixe tapée après le point.
     * @param typeResolu le nom du type résolu du récepteur (null = non résolu).
     * @return les items applicables (souvent 0 ou 1).
     */
    public fun itemsPour(source: String, debutCle: Int, cle: String, typeResolu: String?): List<ItemPostfixe> {
        val recepteur = scannerRecepteur(source, debutCle) ?: return emptyList()
        val texteRecepteur = source.substring(recepteur.first, recepteur.last + 1)
        if (texteRecepteur.isEmpty()) return emptyList()
        val suppression = EditionTexte(recepteur.first, debutCle - recepteur.first, "")
        val estBooleen = typeResolu == "boolean"
        val estReference = typeResolu != null && typeResolu !in PRIMITIFS
        val typeAffichable = typeResolu?.let { nomAffichable(it) }
        val nomDerive = nomDerive(typeAffichable ?: texteRecepteur)

        val items = ArrayList<ItemPostfixe>()
        when (cle) {
            "sout" -> items.add(
                ItemPostfixe(cle, "System.out.println($texteRecepteur);\$0", listOf(suppression), PERTINENCE),
            )

            "var" -> {
                val extrait = if (typeAffichable != null) {
                    "$typeAffichable \${1:$nomDerive} = $texteRecepteur;\$0"
                } else {
                    "var \${1:$nomDerive} = $texteRecepteur;\$0"
                }
                items.add(ItemPostfixe(cle, extrait, listOf(suppression), PERTINENCE))
            }

            "cast" -> items.add(
                ItemPostfixe(cle, "(\${1:$nomDerive}) $texteRecepteur\$0", listOf(suppression), PERTINENCE),
            )

            "opt" -> if (estReference) {
                val ancre = AncreImport.ancre(source)
                val import = EditionTexte(ancre, 0, "import java.util.Optional;\n")
                items.add(
                    ItemPostfixe(cle, "Optional.ofNullable($texteRecepteur)\$0", listOf(suppression, import), PERTINENCE),
                )
            }

            "if" -> if (estBooleen) {
                items.add(
                    ItemPostfixe(cle, "if ($texteRecepteur) {\n    \$0\n}", listOf(suppression), PERTINENCE),
                )
            }
        }
        return items
    }

    /** Nom dérivé : première lettre minuscule du type (ou du récepteur). */
    private fun nomDerive(texte: String): String {
        val nom = texte.substringAfterLast('.').trim('\"')
        if (nom.isEmpty()) return "valeur"
        return nom[0].lowercaseChar() + nom.substring(1)
    }

    /** Nom affichable d'un type : segments de paquet retirés (`java.lang.String` → `String`). */
    private fun nomAffichable(type: String): String =
        type.replace(Regex("([a-zA-Z_$][\\w$]*\\.)+([a-zA-Z_$][\\w$]*)"), "$2")
}

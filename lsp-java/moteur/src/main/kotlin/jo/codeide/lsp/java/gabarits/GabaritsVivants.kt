package jo.codeide.lsp.java.gabarits

/**
 * Un gabarit vivant (live template — étage N7, cahier fiche 029) : une
 * expansion d'instruction déclenchée par une clé tapée en position
 * d'INSTRUCTION.
 *
 * @property cle la clé de déclenchement (`sout`, `fori`…).
 * @property extrait le snippet inséré — tab stops `${1:nom}` liés et `$0`
 *   final (contrat snippet LSP).
 */
public data class GabaritVivant(
    public val cle: String,
    public val extrait: String,
)

/**
 * Un gabarit vivant PROPOSÉ : le gabarit et sa pertinence contractuelle.
 *
 * @property gabarit le gabarit proposé.
 * @property pertinence 2500 pour une clé EXACTE (en tête de liste),
 *   100 pour une clé partielle (discrète) — hiérarchie du cahier : les
 *   gabarits ne doivent pas écraser les items ordinaires sauf
 *   correspondance exacte.
 */
public data class ItemVivant(
    public val gabarit: GabaritVivant,
    public val pertinence: Int,
)

/**
 * Gabarits vivants v1.3.0 (étage N7 — cahier fiche 029) : `sout`, `soutv`,
 * `souf`, `fori`, `try`… servis UNIQUEMENT en position d'instruction et
 * JAMAIS en position d'expression.
 */
public object GabaritsVivants {

    /** Pertinence d'une clé exacte — domine la liste de complétion. */
    public const val PERTINENCE_EXACTE: Int = 2500

    /** Pertinence d'une clé partielle — reste discrète. */
    public const val PERTINENCE_PARTIELLE: Int = 100

    /** Le catalogue (clé → snippet). */
    private val CATALOGUE: List<GabaritVivant> = listOf(
        GabaritVivant("sout", "System.out.println(\$0);"),
        GabaritVivant("soutv", "System.out.println(\"${'$'}{1:var} = \" + ${'$'}{1:var});"),
        GabaritVivant("souf", "System.out.printf(\$0);"),
        GabaritVivant("fori", "for (int ${'$'}{1:i} = 0; ${'$'}{1:i} < ${'$'}{2:max}; ${'$'}{1:i}++) {\n    \$0\n}"),
        GabaritVivant("try", "try {\n    \$0\n} catch (${'$'}{1:Exception} ${'$'}{2:e}) {\n}"),
    )

    /**
     * Gabarits correspondant à un préfixe tapé.
     *
     * @param prefixe le préfixe (null ou vide → AUCUN gabarit : pas
     *   d'inondation du popup à préfixe nu — comportement contractuel).
     * @return les gabarits proposés, la clé exacte EN TÊTE.
     */
    public fun itemsPour(prefixe: String?): List<ItemVivant> {
        if (prefixe.isNullOrEmpty()) return emptyList()
        val exactes = ArrayList<ItemVivant>()
        val partielles = ArrayList<ItemVivant>()
        for (gabarit in CATALOGUE) {
            when {
                gabarit.cle == prefixe -> exactes.add(ItemVivant(gabarit, PERTINENCE_EXACTE))
                gabarit.cle.startsWith(prefixe) -> partielles.add(ItemVivant(gabarit, PERTINENCE_PARTIELLE))
            }
        }
        return exactes + partielles.sortedBy { it.gabarit.cle }
    }

    /**
     * Garde textuelle de position d'instruction (fiche 029) : vraie ssi le
     * mot qui précède la clé (blancs remontés, sauts de ligne compris)
     * termine un énoncé — début de fichier, `;`, `{` ou `}`. Après `=`,
     * dans des arguments (`(`), après un point (`.`) : position
     * d'EXPRESSION — les gabarits d'instruction n'y sortent jamais.
     *
     * @param source le texte source complet.
     * @param debutCle l'offset du premier caractère de la clé.
     * @return vrai ssi la clé vit en position d'instruction.
     */
    public fun estPositionInstruction(source: String, debutCle: Int): Boolean {
        var i = debutCle - 1
        while (i >= 0 && source[i].isWhitespace()) i--
        if (i < 0) return true
        return when (source[i]) {
            ';', '{', '}' -> true
            else -> false
        }
    }
}

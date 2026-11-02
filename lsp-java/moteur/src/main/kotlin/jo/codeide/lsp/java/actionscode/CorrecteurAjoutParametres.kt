package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.diagnostics.Diagnostic

/**
 * Le correcteur « Ajouter les paramètres manquants » (complément de
 * catalogue — ADR 0032, reste consigné du PV V-D3 : « ajout/
 * réordonnancement de paramètres ») : le miroir moteur du quickfix
 * « Change Signature » de la référence — un appel qui passe PLUS
 * d'arguments que la signature n'en prend ÉTEND la signature, les
 * AUTRES appelants recevant la valeur PAR DÉFAUT du type ajouté
 * (`0`/`false`/`null` — miroir du préremplissage du dialogue Change
 * Signature de la référence).
 *
 * Découvertes figées (sonde du 01/11 sur le jar ecj 3.40.0, R5 —
 * jamais de mémoire ; structure CONFIRMÉE par instrumentation du
 * correcteur au banc) :
 *  - un appel MÉTHODE fautif (`calcul(3, "x", 4L)` sur `calcul(int)`)
 *    émet **ParameterMismatch (67108979)** avec QUATRE arguments
 *    `[déclarant, sélecteur, typesExistantsJOINTS,
 *    typesArgumentsJOINTS]` — les deux listes de types sont des chaînes
 *    JOINTES par « , » (miroir des jetons du message : « The method
 *    {1}({2}) in the type {0} is not applicable for the arguments
 *    ({3}) ») ; la liste existante est VIDE sans paramètre ;
 *  - un `new X(…)` fautif (`new Main(1, "y")` sur `Main(int)`) émet
 *    **UndefinedConstructor (134217858)** (« The constructor Main(int,
 *    String) is undefined » — il n'existe PAS de variante « not
 *    applicable » pour les constructeurs, mesuré) avec arguments
 *    `[déclarant, typesArgumentsJOINTS]`.
 *
 * La LISTE est TEXTE pur (arithmétique sur les arguments structurés du
 * diagnostic — aucune résolution) ; les gardes multi-fichiers
 * (surcharge, varargs, hiérarchie, référence de méthode, type binaire)
 * et les ÉDITIONS vivent au RESOLVE ([MoteurJava.resoudreActionProjet],
 * ADR 0027 (f) : rien d'eager).
 *
 * **Refus honnêtes à la liste (R1)** :
 *  - l'appel ne passe PAS d'arguments excédentaires (pur mismatch de
 *    types : changer la signature ne relève pas de l'ajout) ;
 *  - un argument excédentaire est le littéral `null` (type inconnu —
 *    le paramètre ajouté ne serait pas typable honnêtement).
 */
internal object CorrecteurAjoutParametres {

    /** La clé du générateur différé (resolve multi-fichiers). */
    internal const val CLE_AJOUTER_PARAMETRES: String = "quickfix.ajouterParametres"

    /** L'ID ecj de ParameterMismatch (mesuré — sonde 01/11). */
    private const val ID_PARAMETER_MISMATCH: Long = 67108979L

    /** L'ID ecj d'UndefinedConstructor (mesuré — sonde 01/11). */
    private const val ID_UNDEFINED_CONSTRUCTOR: Long = 134217858L

    /**
     * L'action « Ajouter les paramètres manquants » d'un diagnostic
     * PARAMETRES_INAPPLICABLES (l'appel fautif).
     *
     * @param diagnostic le diagnostic ParameterMismatch/UndefinedConstructor
     *   RÉEL du moteur (la plage couvre le sélecteur de l'envoi pour une
     *   méthode, l'allocation ENTIÈRE pour un constructeur — mesuré).
     * @return l'action différée (clé + offset du site), ou null (aucun
     *   argument excédentaire, ou type illisible).
     */
    internal fun action(diagnostic: Diagnostic): ActionCode? {
        return when (diagnostic.code?.removePrefix("ecj:")?.toLongOrNull()) {
            ID_PARAMETER_MISMATCH -> actionMethode(diagnostic)
            ID_UNDEFINED_CONSTRUCTOR -> actionConstructeur(diagnostic)
            else -> null
        }
    }

    /** Le cas MÉTHODE : args [déclarant, sélecteur, existantsJOINTS, argumentsJOINTS] (mesuré). */
    private fun actionMethode(diagnostic: Diagnostic): ActionCode? {
        val arguments = diagnostic.arguments
        if (arguments.size < 4) return null
        val selecteur = arguments[1]
        if (selecteur.isEmpty() || !selecteur[0].isJavaIdentifierStart()) return null
        val ariteExistante = decouper(arguments[2]).size
        val typesArguments = decouper(arguments[3])
        val excédent = typesArguments.size - ariteExistante
        if (excédent <= 0) return null // pur mismatch de types : pas un ajout
        if (typesArguments.drop(ariteExistante).any { it.isEmpty() || it == "null" }) return null
        return differee(diagnostic, titreMethode(selecteur, excédent))
    }

    /** Le cas CONSTRUCTEUR : args [déclarant, argumentsJOINTS] (mesuré — arité existante inconnue à la liste). */
    private fun actionConstructeur(diagnostic: Diagnostic): ActionCode? {
        val arguments = diagnostic.arguments
        if (arguments.size < 2) return null
        val nomType = arguments[0].substringAfterLast('.')
        if (nomType.isEmpty()) return null
        if (decouper(arguments[1]).isEmpty()) return null
        return differee(diagnostic, "Étendre le constructeur « $nomType »")
    }

    /** L'action différée commune (clé + offset du site fautif). */
    private fun differee(diagnostic: Diagnostic, titre: String): ActionCode =
        ActionCode(
            kind = "quickfix",
            titre = titre,
            commande = ModCommande.Differee(CLE_AJOUTER_PARAMETRES, listOf(diagnostic.plage.debutOffset.toString())),
            diagnostic = diagnostic,
        )

    /** Le titre pluriel correct (« paramètre manquant » / « N paramètres manquants »). */
    private fun titreMethode(selecteur: String, excédent: Int): String =
        if (excédent == 1) {
            "Ajouter le paramètre manquant à « $selecteur »"
        } else {
            "Ajouter les $excédent paramètres manquants à « $selecteur »"
        }

    /**
     * Le découpage profondeur-aware d'une chaîne JOINTE de types (miroir
     * de [CorrecteurCreationMethode.decouperTypesParametres] — les
     * virgules INTERNES aux génériques ne comptent pas).
     */
    private fun decouper(jointe: String): List<String> {
        val types = ArrayList<String>()
        val tampon = StringBuilder()
        var profondeur = 0
        for (c in jointe) {
            when {
                c == '<' -> {
                    profondeur++
                    tampon.append(c)
                }
                c == '>' -> {
                    profondeur--
                    tampon.append(c)
                }
                c == ',' && profondeur == 0 -> {
                    types.add(tampon.toString().trim())
                    tampon.setLength(0)
                }
                else -> tampon.append(c)
            }
        }
        val dernier = tampon.toString().trim()
        if (dernier.isNotEmpty()) types.add(dernier)
        return types
    }
}

package jo.codeide.lsp.java.actionscode

/**
 * Le corps de méthode englobant une position (étage N6 — cahier fiches 063
 * et 064) : la découverte STRUCTURELLE partagée par « Déclarer throws » et
 * « Créer la méthode ».
 *
 * La méthode de découverte (fiche 063 : « le scan d'accolades traverse le
 * bloc de contrôle ») : on apparie toutes les accolades de la source
 * ([AnalyseTexte.pairesAccolades] — chaînes et commentaires ignorés), puis
 * on remonte la chaîne des paires ENGLOBANTES depuis la position. La
 * première paire dont l'EN-TÊTE est celui d'une MÉTHODE est le corps
 * cherché : un en-tête de méthode se termine par une liste de paramètres
 * « nom(params) » suivie (blancs et clause throws mis à part) de l'accolade
 * ouvrante — le jeton AVANT la parenthèse ouvrante est un IDENTIFIANT qui
 * n'est PAS un mot-clé de contrôle. Les blocs de contrôle (« if (…) { »),
 * les catch, les initialiseurs « static { », les classes anonymes
 * (« new X(…) { ») et les lambdas (« … -> { ») sont ainsi ÉCARTÉS — le
 * throws atterrit sur la MÉTHODE, jamais sur le if (fiche 063).
 *
 * @property ouverture position de « { » du corps.
 * @property fermeture position de « } » du corps.
 * @property finParams position JUSTE APRÈS la « ) » fermant la liste de
 *   paramètres — le point d'insertion d'une clause throws (« la clause
 *   suit directement la liste de paramètres », fiche 063).
 * @property enTete le texte complet de l'en-tête (modificateurs, nom,
 *   paramètres, clause throws existante éventuelle).
 * @property statique vrai ssi l'en-tête porte le modificateur « static »
 *   (mot entier) — le contexte du site d'appel pour le stub (fiche 064).
 * @property typeRetour le type de retour DECLARÉ de la méthode (texte du
 *   jeton de type avant le nom — génériques et tableaux inclus) ; null pour
 *   un CONSTRUCTEUR (aucun type avant le nom). Le contexte typé des
 *   correcteurs (P5/G-014, vague V-A : le stub de création tient compte du
 *   type attendu par le site d'appel).
 */
internal data class CorpsMethode(
    internal val ouverture: Int,
    internal val fermeture: Int,
    internal val finParams: Int,
    internal val enTete: String,
    internal val statique: Boolean,
    internal val typeRetour: String?,
)

/** Mots-clés de contrôle dont le bloc ressemble à un corps de méthode. */
private val MOTS_CLES_CONTROLE = setOf(
    "if", "for", "while", "switch", "catch", "synchronized", "do", "try", "else",
    "new", "return", "throw", "assert", "finally",
)

/** Mots-clés de MODIFICATION jamais un type de retour (extraction [CorpsMethode.typeRetour]). */
private val MOTS_CLES_MODIFICATEURS = setOf(
    "public", "private", "protected", "static", "final", "abstract",
    "synchronized", "native", "strictfp", "default", "transient", "volatile",
)

/**
 * Le chercheur de corps de méthode englobant (étage N6).
 */
internal object ChercheurMethode {

    /**
     * Localise le corps de méthode englobant [offset].
     *
     * @param source le texte Java complet.
     * @param offset une position DANS la méthode (typiquement la plage du
     *   diagnostic corrigé).
     * @return le corps décrit, ou null (position hors méthode, construction
     *   non identifiée — le correcteur appelant s'abstient).
     */
    internal fun corpsEnglobant(source: String, offset: Int): CorpsMethode? {
        val englobantes = AnalyseTexte.pairesAccolades(source)
            .filter { it.ouverture < offset && offset < it.fermeture }
            .sortedByDescending { it.ouverture }
        for (paire in englobantes) {
            decrire(source, paire)?.let { return it }
        }
        return null
    }

    /**
     * Décrit une paire d'accolades comme corps de méthode — null si
     * l'en-tête n'est PAS celui d'une méthode.
     */
    private fun decrire(source: String, paire: AnalyseTexte.PaireAccolades): CorpsMethode? {
        // Coupe de l'en-tête : depuis la borne précédente (« ; », « } » ou
        // l'accolade ouvrante du bloc ENGLOBANT) jusqu'à l'accolade du corps.
        var borne = 0
        var i = paire.ouverture - 1
        while (i >= 0) {
            val c = source[i]
            if (c == ';' || c == '}' || c == '{') {
                borne = i + 1
                break
            }
            i--
        }
        val enTete = source.substring(borne, paire.ouverture)

        // Coupe de la clause throws EXISTANTE : « nom(params) throws A, B » —
        // la partie utile (préfixe de l'en-tête, offsets conservés) doit se
        // terminer par la « ) » fermant la liste de paramètres.
        val utile = couperClauseThrows(enTete) ?: return null
        val finParams = utile.lastIndexOf(')')
        if (finParams < 0) return null

        // Le jeton AVANT la « ( » ouvrante : l'IDENTIFIANT nom de la
        // méthode — un mot-clé de contrôle écarte le bloc (if, catch…),
        // l'absence d'identifiant écarte les lambdas et initialiseurs.
        val positionOuvrante = positionParentheseOuvrante(utile, finParams)
        if (positionOuvrante < 0) return null
        var k = positionOuvrante - 1
        while (k >= 0 && utile[k].isWhitespace()) k--
        if (k < 0 || !estFinIdentifiant(utile[k])) return null
        var debutNom = k
        while (debutNom > 0 && estPartieIdentifiant(utile[debutNom - 1])) debutNom--
        val nom = utile.substring(debutNom, k + 1)
        if (nom in MOTS_CLES_CONTROLE || nom.isEmpty()) return null

        // Mot « static » ENTIERS dans les modificateurs (avant le nom).
        val modificateurs = utile.substring(0, debutNom)
        val statique = modificateurs.split(Regex("\\s+")).any { it == "static" }

        return CorpsMethode(
            ouverture = paire.ouverture,
            fermeture = paire.fermeture,
            finParams = borne + finParams + 1,
            enTete = enTete,
            statique = statique,
            typeRetour = extraireTypeRetour(utile.substring(0, debutNom)),
        )
    }

    /**
     * Le type de retour déclaré d'un en-tête de méthode : le segment AVANT le
     * nom, dépouillé de ses ANNOTATIONS (arguments compris) et de ses mots-clés
     * de modification — le reste est le type (génériques et tableaux voyagent
     * sans espace). Vide/absent → CONSTRUCTEUR (null). Jamais d'exception :
     * une forme non reconnue rend null (le correcteur appelant replie).
     *
     * @param segment le texte des modificateurs (du début d'en-tête au nom).
     * @return le type de retour textuel, ou null si aucun (constructeur).
     */
    private fun extraireTypeRetour(segment: String): String? {
        val sansAnnotations = Regex("@[A-Za-z_$][A-Za-z0-9_$.]*(\\s*\\([^()]*\\))?").replace(segment, " ")
        val type = sansAnnotations.split(Regex("\\s+")).
            filter { it.isNotEmpty() && it !in MOTS_CLES_MODIFICATEURS }
            .joinToString(" ")
        return type.ifEmpty { null }
    }

    /**
     * Coupe la clause throws EXISTANTE de fin d'en-tête (« … throws A, B ») : rend
     * le préfixe utile terminé par la « ) » des paramètres — les offsets du préfixe
     * restent ceux de l'en-tête COMPLET ; null si la coupe ne termine pas en
     * paramètres.
     */
    private fun couperClauseThrows(enTete: String): String? {
        var utile = enTete.trimEnd()
        val clause = Regex("\\bthrows\\b").findAll(utile).lastOrNull()?.range
        if (clause != null) {
            val queue = utile.substring(clause.last + 1)
            // La queue d'une clause throws : noms, points, virgules, blancs.
            if (queue.isNotEmpty() && queue.matches(Regex("[A-Za-z0-9_$.\\s,]+"))) {
                utile = utile.substring(0, clause.first).trimEnd()
            }
        }
        if (!utile.endsWith(")")) return null
        return utile
    }

    /** La position de la « ( » qui correspond à la « ) » en [finFermante] de l'en-tête. */
    private fun positionParentheseOuvrante(enTete: String, finFermante: Int): Int {
        var profondeur = 0
        var j = finFermante
        while (j >= 0) {
            val c = enTete[j]
            if (c == ')') profondeur++
            if (c == '(') {
                profondeur--
                if (profondeur == 0) return j
            }
            j--
        }
        return -1
    }

    /** Vrai ssi [c] peut FINIR un identifiant Java. */
    private fun estFinIdentifiant(c: Char): Boolean =
        c.isJavaIdentifierPart() && !c.isDigit()

    /** Vrai ssi [c] peut faire partie d'un identifiant Java. */
    private fun estPartieIdentifiant(c: Char): Boolean = c.isJavaIdentifierPart()
}

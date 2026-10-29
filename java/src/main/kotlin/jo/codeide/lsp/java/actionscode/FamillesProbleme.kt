package jo.codeide.lsp.java.actionscode

/**
 * Famille sémantique d'un problème ecj (étage N6 — cahier fiche 065) : le
 * mapping ID ecj → famille, socle du dispatch **ID-FIRST** des correcteurs.
 *
 * Le contrat verrouillé par la fiche 065 : chaque ID ecj testé mappe vers la
 * même famille **indépendamment de la Locale de l'utilisateur** — c'est la
 * condition pour que les quick-fixes soient fiables sur une VM
 * française/espagnole/etc. Le dispatch ne PARSE JAMAIS le message localisé :
 * il lit l'ID numérique (via [Diagnostic.code] ou la forme préfixée
 * « ecj:&lt;ID&gt; » de la couche LSP) et les arguments STRUCTURÉS
 * ([Diagnostic.arguments]).
 *
 * `UndefinedMethod` est volontairement DÉTACHÉ de [UNRESOLVED_REFERENCE] : un
 * nom de méthode n'est pas un type à importer — c'est ce détachement qui
 * aiguille vers le correcteur de création de méthode (fiche 064) plutôt que
 * vers l'import (fiche 063).
 *
 * Découverte figée N6 (sonde sur le jar ecj 3.40.0, `/scripts` du poste
 * d'étude) : un point-virgule manquant (« int x = 5 ») émet
 * `ParsingErrorInsertToComplete` (1610612976) — avec le jeton à insérer en
 * argument 0 — et NON la constante `MissingSemiColon` (1610612960) du
 * montage synthétique du cahier. La famille [MISSING_SEMI] couvre les DEUX :
 * la constante historique (montages synthétiques) et l'ID réellement émis
 * (diagnostics vivants). Les autres familles d'insertion syntaxique
 * (`ParsingError`, `ParsingErrorInsertTokenBefore`…) restent [UNKNOWN] —
 * verrouillé par la fiche 065 : un ID non mappé ne déclenche AUCUN
 * quick-fix ID-FIRST.
 */
public enum class FamilleProbleme {

    /** Nom de type/champ/variable/import non résolu (5 IDs couverts, fiche 065). */
    UNRESOLVED_REFERENCE,

    /** Méthode non définie — famille PROPRE, détachée de la précédente. */
    UNDEFINED_METHOD,

    /** Import inutilisé (famille propre — correcteur de suppression de ligne). */
    UNUSED_IMPORT,

    /** Déclaration inutilisée (locale, paramètre, membre privé, type privé). */
    UNUSED_DECLARATION,

    /** Exception vérifiée non gérée (4 IDs couverts, y compris auto-close). */
    UNCAUGHT_EXCEPTION,

    /** Incompatibilité de types (affectation, retour, retour incompatible). */
    TYPE_MISMATCH,

    /** Point-virgule manquant (constante historique + ID réellement émis). */
    MISSING_SEMI,

    /** ID non mappé — ne déclenche AUCUN quick-fix ID-FIRST (fiche 065). */
    UNKNOWN;

    public companion object {

        /**
         * Le préfixe de la forme LSP de [Diagnostic.code] (fiche 065 :
         * `fromCode` accepte « ecj:&lt;ID&gt; » indifféremment de l'ID brut).
         */
        public const val PREFIXE_ECJ: String = "ecj:"

        /**
         * Table ID → famille (étage N6). Les IDs numériques sont les
         * CONSTANTES d'`org.eclipse.jdt.core.compiler.IProblem` du jar ecj
         * 3.40.0, extraites par lecture du format de classe (fichier
         * `scripts/iproblem_ids.py` du poste d'étude) — jamais codées « de
         * mémoire » : UndefinedType=16777218, UndefinedName=570425394,
         * UnresolvedVariable=33554515, UndefinedField=33554502,
         * ImportNotFound=268435846, UndefinedMethod=67108964,
         * UnusedImport=268435844, LocalVariableIsNeverUsed=536870973,
         * ArgumentIsNeverUsed=536870974, UnusedPrivateMethod=603979894,
         * UnusedPrivateField=570425421, UnusedPrivateType=553648135,
         * UnhandledException=16777384, UnhandledExceptionInDefaultConstructor
         * =16777362, UnhandledExceptionInImplicitConstructorCall=16777363,
         * UnhandledExceptionOnAutoClose=16778098, TypeMismatch=16777233,
         * ReturnTypeMismatch=16777235, IncompatibleReturnType=67109268,
         * MissingSemiColon=1610612960, ParsingErrorInsertToComplete=
         * 1610612976.
         */
        private val TABLE: Map<Long, FamilleProbleme> = buildMap {
            // Non-résolus → UNRESOLVED_REFERENCE (fiche 065 : 5 IDs).
            put(16777218L, UNRESOLVED_REFERENCE) // UndefinedType
            put(570425394L, UNRESOLVED_REFERENCE) // UndefinedName
            put(33554515L, UNRESOLVED_REFERENCE) // UnresolvedVariable
            put(33554502L, UNRESOLVED_REFERENCE) // UndefinedField
            put(268435846L, UNRESOLVED_REFERENCE) // ImportNotFound

            // Méthode non définie — famille propre (fiche 065).
            put(67108964L, UNDEFINED_METHOD) // UndefinedMethod

            // Inutilisés (fiche 065 : 6 IDs, 1 + 5 familles).
            put(268435844L, UNUSED_IMPORT) // UnusedImport
            put(536870973L, UNUSED_DECLARATION) // LocalVariableIsNeverUsed
            put(536870974L, UNUSED_DECLARATION) // ArgumentIsNeverUsed
            put(603979894L, UNUSED_DECLARATION) // UnusedPrivateMethod
            put(570425421L, UNUSED_DECLARATION) // UnusedPrivateField
            put(553648135L, UNUSED_DECLARATION) // UnusedPrivateType

            // Exceptions non gérées (fiche 065 : 4 IDs).
            put(16777384L, UNCAUGHT_EXCEPTION) // UnhandledException
            put(16777362L, UNCAUGHT_EXCEPTION) // UnhandledExceptionInDefaultConstructor
            put(16777363L, UNCAUGHT_EXCEPTION) // UnhandledExceptionInImplicitConstructorCall
            put(16778098L, UNCAUGHT_EXCEPTION) // UnhandledExceptionOnAutoClose

            // Mismatches (fiche 065 : 3 IDs).
            put(16777233L, TYPE_MISMATCH) // TypeMismatch
            put(16777235L, TYPE_MISMATCH) // ReturnTypeMismatch
            put(67109268L, TYPE_MISMATCH) // IncompatibleReturnType

            // Point-virgule manquant : la constante historique ET l'ID
            // réellement émis par ecj (découverte figée ci-dessus).
            put(1610612960L, MISSING_SEMI) // MissingSemiColon (montage synthétique)
            put(1610612976L, MISSING_SEMI) // ParsingErrorInsertToComplete (vivants)
        }

        /**
         * Mapping d'un ID ecj numérique vers la famille (fiche 065).
         *
         * @param id l'ID numérique du problème (`IProblem.getID()`).
         * @return la famille — [UNKNOWN] pour tout ID non mappé (jamais
         *   d'exception : un ID inconnu ne déclenche aucun quick-fix).
         */
        public fun fromId(id: Long): FamilleProbleme = TABLE[id] ?: UNKNOWN

        /**
         * Mapping d'un code de diagnostic vers la famille (fiche 065).
         *
         * Accepte indifféremment la forme préfixée « ecj:&lt;ID&gt; » (le
         * format du `Diagnostic.code()` LSP côté hôte) et l'ID numérique
         * brut (le format du [Diagnostic.code] moteur). Toute entrée non
         * parsable — `null`, code non numérique, préfixe parasite
         * (« garbage ») — rend [UNKNOWN] SANS lever d'exception : un
         * diagnostic malformé ne doit jamais faire échouer la publication.
         *
         * @param code le code du diagnostic (nullable).
         * @return la famille — [UNKNOWN] si non parsable ou non mappé.
         */
        public fun fromCode(code: String?): FamilleProbleme {
            if (code.isNullOrEmpty()) return UNKNOWN
            val brut = if (code.startsWith(PREFIXE_ECJ)) {
                code.removePrefix(PREFIXE_ECJ)
            } else {
                code
            }
            val id = brut.toLongOrNull() ?: return UNKNOWN
            return fromId(id)
        }
    }
}

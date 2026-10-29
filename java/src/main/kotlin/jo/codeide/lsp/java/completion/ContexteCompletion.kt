package jo.codeide.lsp.java.completion

import org.eclipse.jdt.internal.compiler.lookup.TypeBinding

/**
 * Filtre grammatical appliqué aux candidats de TYPE (étage N2 — cahier
 * fiche 007 : « la position grammaticale filtre le légal »).
 */
public enum class FiltreGrammatical {
    /** Après `extends` d'une CLASSE : interfaces refusées. */
    CLASSES_SEULES,

    /** Après `implements` : interfaces seules. */
    INTERFACES_SEULES,

    /** Clauses `throws` et `catch` : Throwables seuls. */
    THROWABLES_SEULES,

    /** Après `new` : instanciables seuls (ni interface, ni abstraite). */
    INSTANCIABLES_SEULES,

    /** Position d'annotation : types `@interface` seuls. */
    ANNOTATIONS_SEULES,
}

/**
 * Contexte de complétion au caret (étage N2) : le VERDICT de l'analyseur —
 * genre, préfixe, et les données de collecte propres au genre.
 *
 * @property genre le genre du contexte (cf. [GenreContexte]).
 * @property prefixe le préfixe tapé (identifiant en cours, éventuellement vide).
 * @property filtre le filtre grammatical des candidats de type (fiche 007).
 * @property paquetTape le paquet déjà tapé avant le point (IMPORT :
 *   `android` pour `import android.` ; REFERENCE_PAQUET : `java.util`).
 * @property recepteur le texte du récepteur (MEMBRE/MEMBRE_STATIQUE :
 *   `s`, `binding.titre`, `String`).
 * @property typeRecepteur le type résolu du récepteur d'INSTANCE (MEMBRE).
 * @property recepteurFqcn le FQCN du qualifier DE TYPE (MEMBRE_STATIQUE) —
 *   la collecte des membres statiques résoudra son binding.
 * @property typeAttendu le type attendu (NOM_DECLARATION : le type déclarant
 *   dont on dérive les noms ; CASE_LABEL/VALEUR_ANNOTATION : l'enum).
 * @property typeEnglobant le type englobant au caret (CORPS_CLASSE : les
 *   stubs d'override parcourent sa hiérarchie).
 * @property importsFqcn les FQCN importés par l'unité (types visibles).
 * @property paquetCourant le paquet de l'unité (types visibles sans import).
 */
public data class ContexteCompletion(
    public val genre: GenreContexte,
    public val prefixe: String,
    public val filtre: FiltreGrammatical? = null,
    public val paquetTape: String? = null,
    public val recepteur: String? = null,
    public val typeRecepteur: TypeBinding? = null,
    public val recepteurFqcn: String? = null,
    public val typeAttendu: TypeBinding? = null,
    public val typeEnglobant: org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding? = null,
    public val importsFqcn: List<String> = emptyList(),
    public val paquetCourant: String? = null,
)

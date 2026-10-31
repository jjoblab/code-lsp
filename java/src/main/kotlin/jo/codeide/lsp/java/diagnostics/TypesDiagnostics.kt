package jo.codeide.lsp.java.diagnostics

/**
 * Sévérité d'un diagnostic du moteur (étage N1, cahier fiches 001/013).
 *
 * Le moteur normalise les catégories d'ecj (`isError`/`isWarning`/`isInfo` de
 * `org.eclipse.jdt.core.compiler.IProblem`) en trois niveaux stables pour le
 * reste de la chaîne LSP. L'ordre des valeurs reflète la gravité croissante :
 * il sert au tri stable des listes de diagnostics à offset égal.
 */
public enum class Severite {
    /** Message purement informatif (task tags, remarques ecj de niveau info). */
    INFO,

    /** Avertissement de compilation (le code compile malgré tout). */
    AVERTISSEMENT,

    /** Erreur de compilation — le contrat de parité du cahier porte sur ce niveau. */
    ERREUR,
}

/**
 * Plage d'un diagnostic en offsets absolus dans le fichier source.
 *
 * Le cahier (fiche 001) fixe le contrat : la plage est exprimée en **offsets
 * absolus de caractères** dans le fichier, jamais en ligne/colonne — la
 * conversion vers le protocole LSP (ligne/colonne UTF-16) est le travail de
 * la couche hôte, pas du moteur.
 *
 * Invariants garantis par le moteur (fiche 013) : `debutOffset >= 0` et
 * `finOffset >= debutOffset` ; sur code cassé, la plage peut être dégénérée
 * d'au plus un caractère (`finOffset == debutOffset + 1`), jamais plus.
 *
 * @property debutOffset offset de début (inclusif), `>= 0`.
 * @property finOffset offset de fin (exclusif), `>= debutOffset`.
 */
public data class Plage(
    public val debutOffset: Int,
    public val finOffset: Int,
)

/**
 * Un diagnostic de compilation publié par le moteur.
 *
 * L'identité de valeur ([equals] de la classe de données) est le contrat
 * d'équivalence du diagnose rapide (fiche 014) : deux listes sont « strictement
 * identiques » ssi tailles, offsets début/fin, sévérités, messages, codes et
 * arguments correspondent un à un. C'est pourquoi [code] fait partie de
 * l'identité.
 *
 * @property severite niveau normalisé du problème.
 * @property message message du compilateur (vocabulaire ecj, `Locale.ROOT`
 *   pour la reproductibilité des tests de parité).
 * @property plage localisation en offsets absolus ; jamais nulle.
 * @property code identifiant du problème ecj (`getID()`), transporté pour la
 *   parité stricte et le classement par familles côté hôte ; peut être nul
 *   pour les diagnostics synthétiques du moteur (enrichissement de hiérarchie).
 * @property arguments les arguments STRUCTURÉS du problème ecj
 *   (`IProblem.getArguments()` — étage N6, cahier fiches 063/064/051) : les
 *   pièces du message AVANT interpolation (`{0}`, `{1}`…), stables entre
 *   locales. Le dispatch ID-FIRST des correcteurs et la reconnaissance du
 *   paquet Data Binding se font sur ces arguments, JAMAIS sur le message
 *   localisé (fiche 065 : « le dispatch est Locale-indépendant »). Vide pour
 *   les diagnostics synthétiques du moteur.
 * @property source la PROVENANCE du diagnostic (V-C1 — ADR 0023/0025) :
 *   `null` = le compilateur ecj (ou un enrichissement synthétique du
 *   moteur), `"inspection"` = la couche IDE au-dessus du compilateur
 *   (ADR 0013 : le rouge n'a qu'une source — les inspections n'ajoutent
 *   JAMAIS de sévérité Erreur, uniquement Warning/Info). Transportée pour
 *   le filtrage et la provenance côté hôte (G-023 : relatedInformation en
 *   V-G). L'équivalence des valeurs par défaut préserve l'identité des
 *   diagnostics ecj existants (contrat fiche 014).
 */
public data class Diagnostic(
    public val severite: Severite,
    public val message: String,
    public val plage: Plage,
    public val code: String? = null,
    public val arguments: List<String> = emptyList(),
    public val source: String? = null,
)

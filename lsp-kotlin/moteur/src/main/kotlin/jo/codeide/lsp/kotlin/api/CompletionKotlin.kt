// CodeIDE — SPI du moteur Kotlin LSP : complétion (module :kotlin, étage 3
// vague 3a — le PIPELINE complet d'ADR 0113 : le germe membre de la vague
// 2a (libellé nu, fiche 022) s'étend en modèle RICHE — nature, détail,
// texte d'insertion, FQCN d'import auto — sans régression des contrats
// déjà verts : les libellés restent NUS, `completion()` rend la liste
// arrangée). Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.api

/**
 * La NATURE d'une proposition de complétion (étage 3 — ADR 0113) : la
 * classification de l'ENTITÉ proposée — présentation côté hôte (icône,
 * genre LSP `CompletionItemKind`) ET classement (le peseur de nature en
 * dépend, priorité par contexte).
 */
public enum class NaturePropositionKotlin {

    /** Une LOCALE du document (`val compte = 1`). */
    VARIABLE,

    /** Un PARAMÈTRE en attente (argument nommé `nom =` compris). */
    PARAMETRE,

    /** Une PROPRIÉTÉ membre (`val nom`, champ JVM, dérivée de getter). */
    PROPRIETE,

    /** Une FONCTION (`fun afficher`, méthode JVM, extension comprise). */
    FONCTION,

    /** Une CONSTANTE d'enum (`ROUGE`). */
    ENUM,

    /** Une CLASSE ou un OBJET. */
    CLASSE,

    /** Une INTERFACE. */
    INTERFACE,

    /** Une classe d'ANNOTATION. */
    ANNOTATION,

    /** Un PAQUET (`android.widget`). */
    PAQUET,

    /** Un MOT-CLÉ du langage (`class`, `return`). */
    MOT_CLE,
}

/**
 * Une proposition de complétion Kotlin (modèle neutre — l'hôte la traduit
 * en `CompletionItem` LSP).
 *
 * Le LIBELLÉ est le nom NU de l'entité proposée (`copy`, `somme`,
 * `requestLayout`) — convention du moteur depuis le germe vague 2a ; les
 * propositions à INSERTION ÉDITABLE (arguments nommés, attributs
 * d'annotation — fiche 043) portent le libellé AVEC sélecteur (`nom =`)
 * et le [texteInsertion] correspondant (`nom = `) : ces contextes sont
 * EXCLUSIFS, le mélange n'existe pas.
 *
 * @property libelle le libellé rendu (`copy`, `nom =`) ;
 * @property nature la nature de l'entité proposée ;
 * @property detail le détail de présentation (FQCN d'un type du classpath
 * `android.widget.TextView`, signature `creer(nom: String, age: Int)`,
 * propriétaire d'un membre hérité) — `null` sans détail démontrable ;
 * @property texteInsertion le texte À INSÉRER au caret quand il diffère
 * du libellé (arguments nommés, attributs d'annotation — insertion AVEC
 * séparateur ` = `) ; `null` = le libellé s'insère tel quel ;
 * @property fqcnImport le FQCN à IMPORTER côté hôte quand la proposition
 * exige un import auto-éditable (`android.widget.TextView`) ; `null`
 * sinon.
 */
public class PropositionKotlin(
    public val libelle: String,
    public val nature: NaturePropositionKotlin,
    public val detail: String? = null,
    public val texteInsertion: String? = null,
    public val fqcnImport: String? = null,
)

/**
 * Le GENRE DE CONTEXTE de complétion au caret (étage 3 — ADR 0113) : la
 * classification produite par le moteur AVANT la collecte ; chaque genre
 * ouvre SON régime de collecte (et ses bornes conservatrices D3 — un
 * contexte non prouvable retombe sur [NOM] ou rend une liste vide,
 * jamais une invention).
 */
public enum class GenreContexteKotlin {

    /** Complétion de MEMBRES : `recepteur.prefixe|` (récepteur typé). */
    MEMBRE,

    /** Complétion de NOM : identifiant en position d'expression/corps. */
    NOM,

    /** Contexte d'IMPORT : `import paquet.tape.prefixe|`. */
    IMPORT,

    /**
     * Arguments NOMMÉS d'un appel résolu : `appel(|` — les paramètres non
     * encore fournis, à insertion avec séparateur (fiche 043).
     */
    ARGUMENTS_NOMMES,

    /**
     * Constantes d'ENUM : condition d'une branche d'un `when` à sujet enum
     * typé — contexte EXCLUSIF (fiche 043).
     */
    CONSTANTES_ENUM,

    /**
     * Attributs d'ANNOTATION : arguments d'une entrée d'annotation résolue
     * — contexte EXCLUSIF (fiche 043).
     */
    ATTRIBUTS_ANNOTATION,

    /** Contexte non classé (repli honnête : liste vide). */
    INCONNU,
}

/**
 * La réponse ARRANGÉE de complétion (étage 3 — ADR 0113 : l'arrangement
 * honnête d'IntelliJ transposé) : la liste ordonnée, plafonnée à
 * l'affichage, AVEC le total réel de l'espace de candidats — le plafond
 * ne ment JAMAIS sur la taille de l'espace.
 *
 * @property genre le genre du contexte au caret ;
 * @property propositions les propositions ordonnées (≤ limite
 * d'affichage) ;
 * @property totalDisponible le nombre RÉEL de candidats correspondants
 * AVANT plafond (l'honnêteté du trim).
 */
public class ReponseCompletionKotlin(
    public val genre: GenreContexteKotlin,
    public val propositions: List<PropositionKotlin>,
    public val totalDisponible: Int,
) {

    /** Vrai ssi la liste a été raccourcie par le plafond d'affichage. */
    public val tronquee: Boolean
        get() = totalDisponible > propositions.size
}

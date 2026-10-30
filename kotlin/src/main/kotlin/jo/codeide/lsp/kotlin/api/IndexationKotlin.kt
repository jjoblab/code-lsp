// CodeIDE — SPI du moteur Kotlin LSP : événements d'indexation (module
// :kotlin, étage 1 vague 1d). Code NEUF jjoblab écrit pour code-lsp
// (contrat : fiche 009 du cahier docs/cahier-specifications-kotlin/ —
// pilote de passages d'indexation, demandes D1/D3/D4/D5 de CodeIDE ;
// ADR 0211/0213/0214 anciens dont le CONTRAT observable est repris tel
// quel : séquence d'événements, annulation coopérative, reprise ciblée,
// no-op chaud). Types NEUTRES, aucune dépendance LSP4J ni Android — D6.
package jo.codeide.lsp.kotlin.api

/**
 * La phase GÉNÉRALE d'un passage d'indexation (ADR 0213 ancien — la
 * progression publiée aux observateurs).
 */
public enum class PhaseIndexationKotlin {

    /** Le passage construit l'index (bibliothèques puis sources). */
    CONSTRUCTION,

    /** Le passage est ABOUTI : aucun écart, aucune annulation. */
    PRET,

    /**
     * Le passage s'est arrêté en chemin : au moins une entrée écartée
     * (illisible) ou une annulation — l'ACQUIS reste publié et servi.
     */
    PARTIEL,
}

/** La phase DÉTAILLÉE d'un événement de construction (ADR 0213 ancien). */
public enum class PhaseDetailleeIndexationKotlin {

    /** Segments des bibliothèques (décodage/reprise des jars, entrée par entrée). */
    BIBLIOTHEQUES,

    /** Scan des racines sources (fichier par fichier — marche d'abord). */
    SOURCES,

    /** Hors construction (événement final PRET/PARTIEL). */
    AUCUNE,
}

/**
 * L'état d'une entrée de la liste de travail (ADR 0213 ancien) — l'écran
 * de progression de l'hôte le rend tel quel.
 */
public enum class EtatEntreeTravailKotlin {

    /** Pas encore traitée (pend — notamment après une annulation). */
    EN_ATTENTE,

    /** En cours de traitement (un décodage à froid peut durer des secondes). */
    ACTIF,

    /** Servie SANS reconstruction (cache mémoire ou segment disque). */
    REPRIS,

    /** Décodée à froid et persistée. */
    TERMINE,

    /** Illisible : écartée honnêtement (PARTIEL, jamais d'exception). */
    ECARTE,
}

/**
 * Une ligne de la liste de travail d'un passage (ADR 0213 ancien) —
 * l'entrée et son état courant.
 *
 * @property nom le chemin de l'entrée (clé de la ligne) ;
 * @property etat l'état courant de l'entrée.
 */
public class EntreeTravailKotlin(
    public val nom: String,
    public val etat: EtatEntreeTravailKotlin,
)

/**
 * Le relevé chronométrique d'UN indexeur du passage (breakdown ADR 0213
 * ancien — le détail affichable des coûts).
 *
 * @property identifiant l'identifiant stable de l'indexeur
 * (`kidx-segments`, `scan-warm`) ;
 * @property libelle le libellé affichable ;
 * @property millisecondesCumulees le temps cumulé passé dans l'indexeur ;
 * @property nombreEntrees le nombre d'unités traitées (entrées ou fichiers).
 */
public class BreakdownIndexeurKotlin(
    public val identifiant: String,
    public val libelle: String,
    public val millisecondesCumulees: Long,
    public val nombreEntrees: Int,
)

/**
 * Les durées des phases d'un passage (ADR 0213 ancien).
 *
 * @property bibliothequesMs la phase segments (décodage/reprise des jars) ;
 * @property sourcesMs la phase scan des racines.
 */
public class DureesPhasesKotlin(
    public val bibliothequesMs: Long,
    public val sourcesMs: Long,
)

/**
 * Le bilan du dernier passage (ADR 0213 ancien — « paritaire » : les
 * compteurs se recoupent, les repris et les reconstruits refont le total).
 *
 * @property dureeMs la durée totale du passage ;
 * @property artefactsTotal le total des entrées du classpath ;
 * @property artefactsRepris les entrées servies sans reconstruction ;
 * @property artefactsReconstruits les entrées décodées à froid ;
 * @property fichiersParcourus les fichiers éligibles vus par la marche du
 * scan (au-delà de la borne de collecte — statistique honnête) ;
 * @property fichiersReanalyses les fichiers réellement re-parsés (cache
 * mtime épargne les autres) ;
 * @property indexeurPlusCouteux l'identifiant de l'indexeur le plus coûteux ;
 * @property horodatageMs l'horodatage de la fin du passage.
 */
public class BilanDernierPassageKotlin(
    public val dureeMs: Long,
    public val artefactsTotal: Int,
    public val artefactsRepris: Int,
    public val artefactsReconstruits: Int,
    public val fichiersParcourus: Int,
    public val fichiersReanalyses: Int,
    public val indexeurPlusCouteux: String,
    public val horodatageMs: Long,
)

/**
 * UN événement d'un passage d'indexation (ADR 0213 ancien — la forme
 * publiée aux observateurs de l'hôte).
 *
 * @property phase la phase générale ;
 * @property faits les unités faites (entrées de bibliothèque pendant
 * CONSTRUCTION ; au final, la progression redevient celle des SEGMENTS) ;
 * @property total le total d'unités (entrées du classpath) ;
 * @property entreeCourante le chemin de l'entrée en cours (`null` sinon) ;
 * @property avertissements les signaux honnêtes (entrée illisible,
 * « passage annulé par l'utilisateur ») ;
 * @property erreurs les erreurs fatales (aucune à ce jour — le contrat
 * D3 ne lève jamais) ;
 * @property phaseDetaillee la phase détaillée ;
 * @property listeTravail la liste de travail complète (ordre du classpath) ;
 * @property breakdownIndexeurs les coûts par indexeur (au final) ;
 * @property dureesPhases les durées des phases (au final) ;
 * @property bilanDernierPassage le bilan paritaire (au final) ;
 * @property settled le passage est-il abouti (`false` après annulation) ;
 * @property reprises le nombre d'entrées reprises sans reconstruction.
 */
public class EvenementIndexationKotlin(
    public val phase: PhaseIndexationKotlin,
    public val faits: Int,
    public val total: Int,
    public val entreeCourante: String?,
    public val avertissements: List<String>,
    public val erreurs: List<String>,
    public val phaseDetaillee: PhaseDetailleeIndexationKotlin,
    public val listeTravail: List<EntreeTravailKotlin>,
    public val breakdownIndexeurs: List<BreakdownIndexeurKotlin>,
    public val dureesPhases: DureesPhasesKotlin?,
    public val bilanDernierPassage: BilanDernierPassageKotlin?,
    public val settled: Boolean,
    public val reprises: Int,
)

/**
 * L'observateur des événements d'indexation (ADR 0213 ancien — façade
 * hôte). Un observateur FAUTIF est journalisé et ignoré : le passage
 * continue, jamais d'exception vers le moteur.
 */
public fun interface ObservateurIndexationKotlin {

    /**
     * Un événement du passage en cours.
     *
     * @param evenement l'événement publié (immuable).
     */
    public fun surEvenement(evenement: EvenementIndexationKotlin)
}

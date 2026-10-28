package jo.codeide.lsp.model.protocole

/**
 * Contrat « classpath » du serveur de tooling, tel que consommé par le modèle
 * LSP — réécriture propre pour code-lsp (vague 1b, 28/10/2026).
 *
 * ## Pourquoi ces types vivent ici
 *
 * Dans l'ancien dépôt, ces types provenaient de l'artefact
 * `com.github.jjoblab.codeide-tooling:protocol`, dont le dépôt source ne
 * comporte à ce jour **aucun fichier LICENSE** (action propriétaire P1, ADR
 * 0001/0017 ; registre `docs/PROVENANCE-REPRISE.md` §4). Tant que P1 n'est
 * pas résolu, l'alternative consignée §4 est appliquée : **réécrire localement
 * les types consommés**, pour que code-lsp reste autonome au build (R3) et
 * constructivement propre en licences (R9).
 *
 * Cette réécriture est clean-room au sens du cahier : elle est déduite de la
 * **surface observable** du contrat — les champs construits par les fixtures
 * de test du modèle (`FixturesClasspath`) et les champs lus par la conversion
 * (`ConvertisseurModeleLsp`) — jamais du code source du protocole.
 *
 * ## compatibilité filaire
 *
 * Les noms de propriétés reproduisent exactement la notation du protocole v8
 * (c'est ainsi que le tooling les sérialise) : lorsque P1 sera résolu, le
 * retour à l'artefact officiel — ou l'annotation `@Serializable` de ces types
 * côté hôte — ne changera ni les champs ni leurs noms. Aucune sérialisation
 * n'est posée aujourd'hui : rien dans le modèle ne sérialise le protocole
 * (la persistance `lsp-modele.json` porte les snapshots, pas les réponses).
 *
 * ## périmètre
 *
 * Exactement les quatre types consommés par le modèle : [ClasspathResult],
 * [ClasspathModule], [ClasspathEntry], [ClasspathKind]. L'énumération
 * [ClasspathKind] est **fermée sur quatre valeurs** — la conversion du modèle
 * l'épuise par un `when` sans branche `else`, preuve de compilation que le
 * protocole v8 ne connaît pas d'autre sorte d'entrée.
 */

/** Sorte d'entrée de classpath livrée par le serveur de tooling (v8). */
public enum class ClasspathKind {
    /** Jar de bytecode — y compris jar de classes extrait d'un AAR normalisé. */
    JAR,

    /** AAR brut NON normalisé — périphérique : le moteur ne sait pas le lire. */
    AAR,

    /** Dossier de classes compilées (sortie d'un module du projet). */
    DOSSIER,

    /** Dépendance vers un module frère du même projet (résolution par nom). */
    MODULE,
}

/**
 * Une entrée du classpath d'un module, telle que livrée par le tooling.
 *
 * @property path Chemin absolu de l'entrée : fichier jar, archive aar ou
 * dossier de classes ; pour une entrée [ClasspathKind.MODULE], le **nom** du
 * module frère visé (pas un chemin de disque).
 * @property kind Sorte de l'entrée — détermine la stratégie d'empreinte et
 * de lecture côté modèle puis côté index.
 * @property scope Portée Maven de la dépendance (« compile », « runtime »…),
 * `null` quand le tooling ne la renseigne pas ; le modèle retombe alors sur
 * « compile ».
 * @property sources Jar de sources associé (chemin), `null` si absent —
 * utilisé pour documenter et naviguer dans le code décompilé.
 * @property aar Chemin de l'archive aar **d'origine** lorsque cette entrée
 * est le jar de classes extrait d'un AAR normalisé par Gradle, `null` sinon.
 */
public data class ClasspathEntry(
    public val path: String,
    public val kind: ClasspathKind,
    public val scope: String? = null,
    public val sources: String? = null,
    public val aar: String? = null,
)

/**
 * Un module du projet (v8) : sources, classpath et métadonnées de build.
 *
 * Les compteurs (`nbJars`, `nbAars`, `nbSources`, `nbDependancesProjet`) sont
 * des **statistiques informatives** livrées par le tooling — le modèle ne les
 * consomme pas (il dénombre lui-même), ils restent nullable car facultatifs.
 *
 * @property name Nom du module (notation Gradle « :app », « :core:metier »…).
 * @property sourceDirs Racines de sources du rôle principal, ordre du tooling.
 * @property sourceDirsTest Racines de sources de test, `null` si le tooling
 * ne les sépare pas du rôle principal.
 * @property entries Entrées du classpath PROPRE au module (isolation W5).
 * @property nbJars Nombre de jars annoncés (statistique, nullable).
 * @property nbAars Nombre d'AAR annoncés (statistique, nullable).
 * @property nbSources Nombre de fichiers sources annoncés (statistique, nullable).
 * @property varianteAndroid Variante Android sélectionnée (« debug », «
 * release »…), `null` pour un module JVM pur.
 * @property nbDependancesProjet Nombre de dépendances de projet annoncées
 * (statistique, nullable).
 * @property fichiersGeneres Racines de sources GÉNÉRÉES (build généré), `null`
 * si aucune (rôle distinct W10).
 * @property androidJar Chemin du android.jar de la cible (modules Android
 * uniquement), `null` sinon — sa présence qualifie le module d'Android.
 * @property ignore `true` lorsque le tooling écarte ce module de la
 * synchronisation (non compilable, non intéressant…).
 * @property raisonIgnore Motif humain de l'écart, `null` si non renseigné.
 * @property avertissements Avertissements de résolution du tooling, `null`
 * si aucun.
 * @property sourceCompatibility Niveau Java source annoncé (« 8 », « 1.8 »,
 * « 17 »…), `null` si indécidable — normalisé par le modèle.
 * @property targetCompatibility Niveau Java cible annoncé, `null` sinon.
 */
public data class ClasspathModule(
    public val name: String,
    public val sourceDirs: List<String>,
    public val sourceDirsTest: List<String>? = null,
    public val entries: List<ClasspathEntry>,
    public val nbJars: Int? = null,
    public val nbAars: Int? = null,
    public val nbSources: Int? = null,
    public val varianteAndroid: String? = null,
    public val nbDependancesProjet: Int? = null,
    public val fichiersGeneres: List<String>? = null,
    public val androidJar: String? = null,
    public val ignore: Boolean = false,
    public val raisonIgnore: String? = null,
    public val avertissements: List<String>? = null,
    public val sourceCompatibility: String? = null,
    public val targetCompatibility: String? = null,
)

/**
 * Réponse v8 du serveur de tooling : le classpath complet d'un projet.
 *
 * Chaque synchronisation du tooling publie une réponse NEUVE — le modèle la
 * convertit en [jo.codeide.lsp.model.ProjetLsp] immuable sans conserver de
 * référence vers ces types (conversion pure, testable sans démon).
 *
 * @property id Identifiant de la réponse (déduplication côté hôte).
 * @property protocolVersion Version du protocole — 8 pour la réponse
 * « modules Android par le modèle v2 d'AGP » consommée ici.
 * @property projectDir Répertoire racine du projet synchronisé.
 * @property modules Modules du projet, dans l'ordre du tooling (le modèle
 * trie canoniquement après conversion).
 */
public data class ClasspathResult(
    public val id: String,
    public val protocolVersion: Int,
    public val projectDir: String,
    public val modules: List<ClasspathModule>,
)

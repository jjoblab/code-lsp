package jo.codeide.lsp.java.inspections

import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.interne.DiagnoseRapide
import jo.codeide.lsp.java.interne.LectureAst
import org.eclipse.jdt.internal.compiler.ast.ASTNode
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration

/**
 * CONTRAT d'inspection moteur (vague V-C1 — ADR 0023, écart G-010) : la
 * couche « IDE au-dessus du compilateur ».
 *
 * Règle d'ADR 0013 conservée MOT POUR MOT : **le rouge n'a qu'une source
 * (ecj)** — une inspection ne pose JAMAIS de sévérité Erreur. Elle ajoute
 * des Warning ([SeveriteInspection.AVERTISSEMENT]) ou des Weak
 * Warning/Hint ([SeveriteInspection.FAIBLE]), marqués `source =
 * "inspection"` dans le pipeline ADR 0025. Le publicateur les sert comme
 * les diagnostics ecj (même notification, sévérité LSP Warning /
 * Information).
 *
 * Le contrat (miroir de la référence §A4) : une inspection est un VISITOR
 * SANS ÉTAT (dumb-aware par défaut — elle ne consulte JAMAIS l'index :
 * l'index simulé en cours ne change pas son verdict), pilotée par la
 * traversée de l'arbre résolu ([LectureAst.VisiteurResilient] protège
 * chaque visite). Elle reçoit un [ContexteInspection] (arbre + source +
 * chemin logique + rouge ecj déjà calculé — pour l'anti-duplication) et
 * signale via un [RapporteurInspection] qui applique le PROFIL de
 * sévérités et les SUPPRESSIONS (`@SuppressWarnings` / `//noinspection`).
 */
public interface InspectionMoteur {

    /** L'identifiant stable de l'inspection (token de suppression). */
    public val id: String

    /** Le libellé humain (vocabulaire français du dépôt). */
    public val libelle: String

    /** Le groupe d'affichage (profils côté hôte). */
    public val groupe: String

    /** La sévérité PAR DÉFAUT (le profil peut la modifier ou couper l'inspection). */
    public val severiteParDefaut: SeveriteInspection

    /**
     * Vrai ssi l'inspection est dumb-aware : elle ne dépend d'aucun index —
     * son verdict est identique pendant la construction de l'index (toutes
     * les inspections pilotes V-C1 le sont par construction : AST pur).
     */
    public val dumbAware: Boolean get() = true

    /**
     * La PROVENANCE des signalements (V-C2 — ADR 0024) : "inspection" pour
     * la couche IDE générique, "lint" pour les contrôles Android à valeur
     * Java (filtrage et rendu distincts côté hôte, G-023).
     */
    public val provenance: String get() = SOURCE_INSPECTION

    /**
     * Inspecte l'unité résolue.
     *
     * @param contexte l'unité (arbre vivant, source, chemin, rouge ecj).
     * @param rapporteur le récepteur des signalements (profil + suppressions
     *   déjà appliqués par l'appelant).
     */
    public fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection)
}

/** Sévérité d'inspection (le profil ne peut JAMAIS monter une inspection en Erreur). */
public enum class SeveriteInspection {
    /** Weak Warning / Hint — LSP Information. */
    FAIBLE,

    /** Warning — LSP Warning. */
    AVERTISSEMENT;

    /** La sévérité moteur correspondante (jamais ERREUR — règle ADR 0013). */
    public fun severiteMoteur(): Severite = when (this) {
        FAIBLE -> Severite.INFO
        AVERTISSEMENT -> Severite.AVERTISSEMENT
    }
}

/**
 * Le contexte d'une passe d'inspection : l'arbre RÉSOLU de l'unité (les
 * bindings sont lisibles), la source exacte, le chemin logique (paquet
 * attendu) et le ROUGE ecj déjà calculé (anti-duplication : un signalement
 * à cheval sur une erreur ecj est tu — le compilateur parle déjà).
 *
 * @property arbre l'arbre résolu (peut être partiel sur source fautive —
 *   chaque inspection garde ses liaisons nulles).
 * @property source la source Java complète ayant produit l'arbre.
 * @property chemin le chemin logique du document (paquet attendu).
 * @property erreursEcj les diagnostics ecj de sévérité ERREUR de la passe.
 * @property minSdk le niveau d'API minimal du module Android (V-C2 —
 *   ADR 0024 ; null = module sans contrat Android : les contrôles de
 *   niveau d'API restent muets, repli honnête).
 */
public class ContexteInspection(
    public val arbre: CompilationUnitDeclaration?,
    public val source: String,
    public val chemin: String,
    public val erreursEcj: List<Diagnostic>,
    public val minSdk: Int? = null,
)

/**
 * Le rapporteur d'inspection : applique le PROFIL (sévérités, coupures) et
 * les SUPPRESSIONS, puis convertit en diagnostics moteur standard.
 *
 * Anti-duplication : un signalement dont la plage chevauche une ERREUR ecj
 * est tu (le compilateur a déjà parlé — paquet fautif, méthode fautive…).
 */
public class RapporteurInspection internal constructor(
    private val inspection: InspectionMoteur,
    private val profil: ProfilInspections,
    private val suppressions: SuppresseurInspections,
    private val erreursEcj: List<Diagnostic>,
) {

    private val signalements = mutableListOf<Diagnostic>()

    /**
     * Signale un problème d'inspection.
     *
     * @param plage la plage EXACTE du code en cause (fin EXCLUSIVE —
     *   convention TypesDiagnostics : fin ecj + 1).
     * @param message le message humain (vocabulaire français du dépôt).
     * @return vrai ssi le signalement a été RETENU (non supprimé, profil
     *   actif) — les bancs y vérifient la rétention.
     */
    public fun signaler(plage: Plage, message: String): Boolean {
        val severite = profil.severiteDe(inspection.id, inspection.severiteParDefaut) ?: return false
        if (suppressions.estSupprime(inspection.id, plage)) return false
        if (erreursEcj.any { it.plage.debutOffset < plage.finOffset && plage.debutOffset < it.plage.finOffset }) return false
        signalements.add(
            Diagnostic(
                severite = severite.severiteMoteur(),
                message = message,
                plage = plage,
                code = inspection.id,
                source = inspection.provenance,
            ),
        )
        return true
    }

    /** Les signalements retenus (ordre de signalement). */
    public fun retenus(): List<Diagnostic> = signalements.toList()
}

/**
 * Le PROFIL d'inspections (ADR 0023 (b)) : sévérités par défaut
 * modifiables par identifiant, inspections coupables coupées franchement.
 * Le profil PAR DÉFAUT sert les sévérités déclarées par chaque
 * inspection ; un profil explicite ne peut JAMAIS monter une inspection
 * au-dessus d'AVERTISSEMENT (règle « jamais Error »).
 *
 * @param severites les sévérités posées par identifiant (absent = défaut).
 * @param coupees les identifiants coupés (aucun signalement).
 */
public class ProfilInspections(
    private val severites: Map<String, SeveriteInspection> = emptyMap(),
    private val coupees: Set<String> = emptySet(),
) {
    /** La sévérité effective d'une inspection (null = coupée). */
    internal fun severiteDe(id: String, defaut: SeveriteInspection): SeveriteInspection? {
        if (id in coupees) return null
        return severites[id] ?: defaut
    }

    public companion object {
        /** Le profil par défaut : sévérités déclarées par les inspections. */
        public val DEFAUT: ProfilInspections = ProfilInspections()
    }
}

/**
 * Les SUPPRESSIONS d'inspections (ADR 0023 (b)) : `@SuppressWarnings("id")`
 * posée sur une déclaration englobante, ou `//noinspection id` en commentaire
 * sur la ligne du signalement OU la ligne précédente. Les tokens acceptés :
 * NOS identifiants d'inspection + les tokens javac alignés quand ils
 * existent (`unchecked`, `deprecation`, `unused`, `rawtypes`, `serial`) —
 * une inspection dont un token javac existe répond AUSSI à ce token.
 */
internal class SuppresseurInspections(
    /** Les (plage de déclaration, tokens) de chaque @SuppressWarnings trouvé. */
    private val portees: List<Pair<Plage, Set<String>>>,
    /** Les (ligne, tokens) de chaque commentaire //noinspection trouvé. */
    private val commentaires: List<Pair<Int, Set<String>>>,
    private val source: String,
) {
    /** Vrai ssi le signalement de cette inspection à cette plage est supprimé. */
    fun estSupprime(id: String, plage: Plage): Boolean {
        val tokenAligne = tokenJavacDe(id)
        for ((portee, tokens) in portees) {
            if (portee.debutOffset <= plage.debutOffset && plage.finOffset <= portee.finOffset &&
                (id in tokens || (tokenAligne != null && tokenAligne in tokens))
            ) {
                return true
            }
        }
        val ligne = ligneDe(plage.debutOffset)
        for ((ligneCommentaire, tokens) in commentaires) {
            if ((ligneCommentaire == ligne || ligneCommentaire == ligne - 1) &&
                (id in tokens || (tokenAligne != null && tokenAligne in tokens))
            ) {
                return true
            }
        }
        return false
    }

    /** Le numéro de ligne (0-based) d'un offset. */
    private fun ligneDe(offset: Int): Int = source.substring(0, offset.coerceAtMost(source.length)).count { it == '\n' }

    public companion object {
        /** Le token javac aligné d'une inspection (null si aucun). */
        public fun tokenJavacDe(id: String): String? = when (id) {
            "RedundantCast" -> "cast"
            else -> null
        }

        /**
         * Construit le suppresseur d'une unité : collecte les
         * `@SuppressWarnings` des déclarations et les `//noinspection` du
         * texte. Tolérant par construction (arbre cassé → zéro portée).
         */
        public fun de(arbre: CompilationUnitDeclaration?, source: String): SuppresseurInspections {
            val portees = mutableListOf<Pair<Plage, Set<String>>>()
            val visiter = object : LectureAst.VisiteurResilient() {
                override fun visiteMethode(
                    node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration,
                    scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?,
                ): Boolean {
                    // PORTÉE = la déclaration ENTIÈRE (annotations + corps) :
                    // sourceStart/sourceEnd ne couvrent que l'EN-TÊTE d'une
                    // méthode — la suppression doit protéger le corps aussi.
                    porteeDe(node.annotations, node.declarationSourceStart, node.declarationSourceEnd)?.let { portees.add(it) }
                    return true
                }

                override fun visiteType(
                    node: org.eclipse.jdt.internal.compiler.ast.TypeDeclaration,
                    scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?,
                ): Boolean {
                    porteeDe(node.annotations, node.declarationSourceStart, node.declarationSourceEnd)?.let { portees.add(it) }
                    return true
                }

                override fun visiteChamp(
                    node: org.eclipse.jdt.internal.compiler.ast.FieldDeclaration,
                    scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?,
                ): Boolean {
                    porteeDe(node.annotations, node.declarationSourceStart, node.declarationSourceEnd)?.let { portees.add(it) }
                    return true
                }

                private fun porteeDe(
                    annotations: Array<org.eclipse.jdt.internal.compiler.ast.Annotation>?,
                    debut: Int,
                    fin: Int,
                ): Pair<Plage, Set<String>>? {
                    val tokens = mutableSetOf<String>()
                    for (annotation in annotations ?: emptyArray()) {
                        val nomAnnotation = String(annotation.type.getLastToken())
                        // V-C1 : @SuppressWarnings ; V-C2 (ADR 0024) :
                        // @SuppressLint — mêmes portées, valeurs scalaires
                        // OU tableaux {"NewApi", "SetTextI18n"}.
                        if (nomAnnotation != "SuppressWarnings" && nomAnnotation != "SuppressLint") continue
                        when (annotation) {
                            is org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation -> {
                                extraireTokens(annotation.memberValue, tokens)
                            }
                            is org.eclipse.jdt.internal.compiler.ast.NormalAnnotation -> {
                                for (paire in annotation.memberValuePairs ?: emptyArray()) {
                                    extraireTokens(paire.value, tokens)
                                }
                            }
                            else -> {}
                        }
                    }
                    if (tokens.isEmpty()) return null
                    return Plage(debut.coerceAtLeast(0), fin + 1) to tokens
                }
            }
            LectureAst.traverserResilient(arbre, visiter)
            val commentaires = mutableListOf<Pair<Int, Set<String>>>()
            var curseur = 0
            while (curseur <= source.length) {
                // NOTE : indexOf rend -1 (PAS null) — l'elvis ne s'applique
                // pas, la borne doit être testée explicitement (le piège a
                // produit une boucle infinie — mesuré au banc).
                val index = source.indexOf("//noinspection", curseur)
                if (index < 0) break
                val reste = source.substring(index + "//noinspection".length)
                val tokens = reste.takeWhile { it != '\n' }
                    .split(',', ' ', '\t', '\r').filter { it.isNotBlank() }.toSet()
                if (tokens.isNotEmpty()) {
                    commentaires.add(source.substring(0, index).count { it == '\n' } to tokens)
                }
                curseur = index + 1
            }
            return SuppresseurInspections(portees, commentaires, source)
        }

        /** Extrait les tokens d'une valeur d'annotation (scalaire ou tableau). */
        private fun extraireTokens(valeur: org.eclipse.jdt.internal.compiler.ast.Expression, tokens: MutableSet<String>) {
            when (valeur) {
                is org.eclipse.jdt.internal.compiler.ast.StringLiteral -> tokens.add(String(valeur.source()))
                is org.eclipse.jdt.internal.compiler.ast.ArrayInitializer -> {
                    for (element in valeur.expressions ?: emptyArray()) {
                        (element as? org.eclipse.jdt.internal.compiler.ast.StringLiteral)?.let {
                            tokens.add(String(it.source()))
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

/** La constante de provenance des diagnostics d'inspection (ADR 0025). */
public const val SOURCE_INSPECTION: String = "inspection"

/**
 * Le moteur d'inspections (V-C1) : exécute les inspections ACTIVES sur une
 * unité résolue, dans l'ORDRE du catalogue, derrière le drapeau de repli
 * `jo.codeide.lsp.java.inspections.disabled` (coupe franche documentée —
 * retour au comportement d'avant V-C1 : le rouge ecj seul).
 *
 * Anti-bruit (R2 de l'ADR 0023) : sur une unité portant une erreur de
 * SYNTAXE, la passe est tuïe entière (les liaisons sont peu fiables et la
 * référence ne charge pas non plus ses passes sur du code syntaxiquement
 * cassé) — le signalement revient à la frappe suivante. La garde utilise
 * le masque Syntax mesuré d'ecj 3.40 ([DiagnoseRapide.estErreurSyntaxe]).
 */
public object MoteurInspections {

    /**
     * Le catalogue (ordre stable du plan) : les 8 inspections pilotes V-C1
     * puis les contrôles LINT Android à valeur Java V-C2 (ADR 0024 —
     * provenance "lint", muets sans contrat/minSdk quand ils l'exigent).
     */
    public val catalogue: List<InspectionMoteur> = listOf(
        InspectionEgaliteChaines,
        InspectionResultatIgnore,
        InspectionCastRedondant,
        InspectionEqualsHashCode,
        InspectionTailleVersIsEmpty,
        InspectionChampStatiqueViaSousClasse,
        InspectionPaquetDeclare,
        InspectionChaineFormat,
        ControleNewApi,
        ControleSuperManquant,
        ControleTexteBrut,
    )

    /** Kill-switch du plan de gouvernance (drapeau + repli documentés). */
    private val DESACTIVE: Boolean =
        System.getProperty("jo.codeide.lsp.java.inspections.disabled") == "true"

    /**
     * Inspecte une unité résolue : rend les diagnostics d'inspection
     * (sévérité Warning/Info, source "inspection") — JAMAIS une erreur, et
     * JAMAIS un signalement sur une unité syntaxiquement fautive.
     *
     * @param arbre l'arbre résolu de l'unité (masquée ou complète).
     * @param source la source exacte ayant produit l'arbre.
     * @param chemin le chemin logique du document.
     * @param diagnosticsEcj les diagnostics ecj de la MÊME passe (toutes
     *   sévérités — la garde syntaxe lit les erreurs de syntaxe,
     *   l'anti-duplication lit les erreurs).
     * @param profil le profil de sévérités (défaut : [ProfilInspections.DEFAUT]).
     * @return les signalements retenus (vide en repli ou sur code fautif).
     */
    public fun inspecter(
        arbre: CompilationUnitDeclaration?,
        source: String,
        chemin: String,
        diagnosticsEcj: List<Diagnostic>,
        profil: ProfilInspections = ProfilInspections.DEFAUT,
        minSdk: Int? = null,
    ): List<Diagnostic> {
        if (DESACTIVE || arbre == null) return emptyList()
        if (diagnosticsEcj.any { it.severite == Severite.ERREUR && DiagnoseRapide.estErreurSyntaxe(it) }) {
            return emptyList()
        }
        val contexte = ContexteInspection(
            arbre,
            source,
            chemin,
            diagnosticsEcj.filter { it.severite == Severite.ERREUR },
            minSdk,
        )
        val suppresseur = SuppresseurInspections.de(arbre, source)
        val resultat = mutableListOf<Diagnostic>()
        for (inspection in catalogue) {
            val rapporteur = RapporteurInspection(inspection, profil, suppresseur, contexte.erreursEcj)
            runCatching { inspection.visiter(contexte, rapporteur) }
            resultat.addAll(rapporteur.retenus())
        }
        return resultat.sortedWith(compareBy({ it.plage.debutOffset }, { it.plage.finOffset }))
    }
}

package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.completion.ReponseCompletion
import jo.codeide.lsp.java.completion.ServiceCompletion
import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.formatage.FormateurSource
import jo.codeide.lsp.java.formatage.StyleFormattage
import jo.codeide.lsp.java.hierarchie.AppelantEntrant
import jo.codeide.lsp.java.hierarchie.CibleMethodeHierarchie
import jo.codeide.lsp.java.hierarchie.CibleSortante
import jo.codeide.lsp.java.hierarchie.CibleTypeHierarchie
import jo.codeide.lsp.java.hierarchie.ServiceHierarchies
import jo.codeide.lsp.java.inlay.IndiceInlay
import jo.codeide.lsp.java.inlay.ServiceInlay
import jo.codeide.lsp.java.imports.OrganisateurImports
import jo.codeide.lsp.java.imports.PropositionImports
import jo.codeide.lsp.java.navigation.CibleNavigation
import jo.codeide.lsp.java.navigation.CibleSuper
import jo.codeide.lsp.java.navigation.ChaineSelection
import jo.codeide.lsp.java.navigation.GenreSurbrillance
import jo.codeide.lsp.java.navigation.IndexMotsSources
import jo.codeide.lsp.java.navigation.ServiceDecompilation
import jo.codeide.lsp.java.navigation.ServiceNavigation
import jo.codeide.lsp.java.navigation.ServiceSelection
import jo.codeide.lsp.java.navigation.ServiceUsages
import jo.codeide.lsp.java.navigation.Surbrillance
import jo.codeide.lsp.java.navigation.Usage
import jo.codeide.lsp.java.renommage.CibleRenommage
import jo.codeide.lsp.java.renommage.ConflitRenommage
import jo.codeide.lsp.java.renommage.MoteurRenommage
import jo.codeide.lsp.java.renommage.OccurrenceRenommage
import jo.codeide.lsp.java.interne.AideSignature
import jo.codeide.lsp.java.interne.CacheParse
import jo.codeide.lsp.java.interne.DiagnoseRapide
import jo.codeide.lsp.java.interne.ItemCompletion
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.PontEcj
import jo.codeide.lsp.java.interne.RegimeInstantane
import jo.codeide.lsp.java.interne.UniteResolue
import jo.codeide.lsp.java.interne.UnitesSource
import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.outils.PlageCorps
import jo.codeide.lsp.java.outils.ScannerCorps
import jo.codeide.lsp.java.outils.SourceFocuser
import jo.codeide.lsp.java.pliage.Pli
import jo.codeide.lsp.java.pliage.ServicePliage
import jo.codeide.lsp.java.signature.AideSignatures
import jo.codeide.lsp.java.survol.ServiceSurvolRiche
import jo.codeide.lsp.java.survol.SurvolRiche
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import java.nio.file.Path

/**
 * Moteur Java par module (étage N1) — nom NEUTRE : cette classe ne dérive
 * d'aucune classe ecj/JDT, elle ORCHESTRE le pont ecj, les caches et le
 * routage de classpath (convention de nommage du dépôt, AGENTS.md).
 *
 * Un moteur = SON verrou (le moniteur de l'instance), SES caches, SON
 * environnement de noms — l'isolation lot E du cahier (fiche 034) : un
 * module bloqué n'arrête jamais l'autre. Toutes les entrées publiques sont
 * sérialisées sur le moniteur du moteur : la cohérence sous concurrence
 * (fiche 012 — MAIN_OK rend toujours 0 erreur, MAIN_ERR toujours au moins
 * une, quel que soit l'entrelacement) est garantie par construction ; la
 * granularité fichier arrivera avec les vagues de concurrence (ADR 0010).
 *
 * Sur le plan des régimes : `isLikelyUsable()` distingue le régime plein
 * (plateforme résoluble — jrt OU jar du classpath porteur, v1.1.4) du
 * régime instantané (fiche 028 : NI jrt NI jar porteur) — dans le repli,
 * `diagnose` sert la SYNTAXE seule (jamais de « cannot be resolved »
 * fabriqué) et `complete` sert le fichier courant.
 *
 * @param racines racines sources du module (FQCN par plus long préfixe).
 * @param jars jars du classpath du module — un jar portant `java.lang.Object`
 *   (ex. `android.jar` sur ART) vaut plateforme (régime plein, v1.1.4).
 * @param jdkHome home JDK explicite (null = runtime ; sans `lib/modules`
 *   ni fournisseur jrt instanciable = pas de jrt — le régime reste plein
 *   si un jar du classpath porte la plateforme).
 * @param compliance niveau Java EFFECTIF du moteur (défaut de l'environnement
 *   ou niveau posé par module).
 * @param niveauDemande niveau posé explicitement pour le module (null =
 *   hérité du défaut — observable par [complianceModule]).
 * @param indexNoms index de noms partagé (peut être null — moteur sans index).
 */
public class MoteurJava(
    private val racines: List<Path>,
    private val jars: List<Path>,
    private val jdkHome: Path?,
    private val compliance: String,
    private val niveauDemande: String?,
    private val indexNoms: IndexNoms?,
) {

    /**
     * Constructeur de commodité (moteur autonome, niveau hérité du défaut).
     *
     * @param chaineCompletion chaîne de classement de complétion de
     *   remplacement (BANC de qualité ADR 0016 — la baseline « naïve » ;
     *   null = chaîne standard D8).
     */
    public constructor(
        racines: List<Path> = emptyList(),
        jars: List<Path> = emptyList(),
        jdkHome: Path? = null,
        compliance: String = "17",
        chaineCompletion: jo.codeide.lsp.java.completion.ChaineClassement? = null,
    ) : this(racines, jars, jdkHome, compliance, null, null) {
        this.chaineCompletion = chaineCompletion
    }

    /** Chaîne de classement de complétion de remplacement (banc ADR 0016). */
    private var chaineCompletion: jo.codeide.lsp.java.completion.ChaineClassement? = null

    /** Environnement de noms (paresseux — le premier usage paie jrt/jars). */
    private var environnement: EnvironnementNoms? = null

    /** Pont ecj (paresseux, lié à l'environnement). */
    private var pont: PontEcj? = null

    /** Cache de parse LRU (fiche 025). */
    private val cacheParse = CacheParse()

    /** Service de complétion & classement (étage N2), paresseux. */
    private var serviceCompletion: ServiceCompletion? = null

    /** Service de hiérarchies (étage N7), paresseux — projet multi-fichiers. */
    private var serviceHierarchies: ServiceHierarchies? = null

    /** Index de mots des sources (étage N4 — QueryExecutor), paresseux. */
    private var indexMotsSources: IndexMotsSources? = null

    /** Routeur de navigation (étage N4), paresseux. */
    private var serviceNavigation: ServiceNavigation? = null

    /** Service de décompilation (étage N4), paresseux. */
    private var serviceDecompilation: ServiceDecompilation? = null

    /** Service de sélection & formatage à la frappe (étage N4), paresseux. */
    private var serviceSelection: ServiceSelection? = null

    /** Exécuteur de requêtes d'usages (étage N4 — QueryExecutor), paresseux. */
    private var serviceUsages: ServiceUsages? = null

    /** Moteur de renommage par phases (étage N5 — ADR 0014), paresseux. */
    private var moteurRenommage: MoteurRenommage? = null

    /** Service de code actions v2 (étage N6 — ADR 0013), paresseux. */
    private var serviceActionsCode: jo.codeide.lsp.java.actionscode.ServiceActionsCode? = null

    /** États rapides par chemin (fiche 014/015). */
    private val etatsRapides = HashMap<String, DiagnoseRapide.Etat>()

    /** Compteur des diagnostiques COMPLETS (surface observable, fiche 014). */
    private var compteurFullDiagnose = 0

    /** Compteur des diagnostiques RAPIDES et mémos (fiche 014). */
    private var compteurFastDiagnose = 0

    /** Compteur des services de résolution par arbre FOCALISÉ (fiche 015). */
    private var compteurFastResolve = 0

    /** Compteur des services par l'arbre RÉEL du cache de parse (fiche 015). */
    private var compteurCachedResolve = 0

    public companion object {
        /**
         * Kill-switch du diagnose rapide (contrat de présence verrouillé par
         * la fiche 014) : champ statique final, valeur lisible. La
         * désactivation FONCTIONNELLE passe par la propriété système
         * `jo.codeide.lsp.java.fastdiagnose.disabled` (lue à l'initialisation
         * de la classe — non testable dans la même JVM).
         */
        public const val FAST_DIAGNOSE_DISABLED: Boolean = false

        /** État de la propriété système au chargement de la classe. */
        private val DESACTIVE_PAR_PROPRIETE: Boolean =
            System.getProperty("jo.codeide.lsp.java.fastdiagnose.disabled") == "true"
    }

    // ------------------------------------------------------------------ //
    // Régimes et sondes
    // ------------------------------------------------------------------ //

    /**
     * Sonde de disponibilité de la plateforme (régime plein, v1.1.4) :
     * vrai dès que `java.lang.Object` est résoluble — par l'image jrt
     * (runtime ou externe) OU par un jar du classpath porteur (sur ART,
     * le `android.jar` du modèle : le fournisseur jrt du JDK externe y est
     * ininstanciable, cf. AccesJrt v1.1.2). Faux seulement en régime
     * instantané (NI jrt NI jar porteur, fiche 028).
     *
     * @return vrai ssi la plateforme est résoluble (régime plein).
     */
    public fun isLikelyUsable(): Boolean = synchronized(this) { environnementNoms().plateformeResolvable() }

    /** Niveau Java EFFECTIF du moteur (hérité ou posé par module). */
    public fun complianceDemandee(): String = compliance

    /** Niveau posé explicitement pour le module (null = hérité du défaut). */
    public fun complianceModule(): String? = niveauDemande

    /**
     * Index des types du moteur (sonde fiche 039 : `find(fqcn)`,
     * `hasBinarySource()`).
     */
    public fun typeIndex(): IndexTypesMoteur = IndexTypesMoteur()

    /**
     * Calcule le FQCN d'un fichier source par PLUS LONG préfixe de paquet
     * (fiche 010 : l'ordre des racines ne doit jamais être signifiant — le
     * bug historique matchait la racine projet d'abord et segmentait le
     * FQCN par les slashes).
     *
     * @param fichier le fichier `.java` dans une racine du module.
     * @return le FQCN en points, ou null si aucune racine ne contient le fichier.
     */
    public fun fqcnPour(fichier: Path): String? {
        val racine = racines
            .filter { fichier.startsWith(it.normalize()) }
            .maxByOrNull { it.toString().length } ?: return null
        val relatif = racine.normalize().relativize(fichier.normalize())
        if (relatif.nameCount == 0) return null
        val segments = (0 until relatif.nameCount).map { relatif.getName(it).toString() }
        val nomFichier = segments.last()
        if (!nomFichier.endsWith(".java")) return null
        return (segments.dropLast(1) + nomFichier.removeSuffix(".java")).joinToString(".")
    }

    // ------------------------------------------------------------------ //
    // Diagnostics
    // ------------------------------------------------------------------ //

    /**
     * Diagnostique une source du module.
     *
     * Pipeline : mémo (source identique ET dépendances disque inchangées —
     * fraîcheur inter-fichiers P8, ADR 0025) → preuve de confinement
     * (diagnose RAPIDE, fiche 014) → diagnose complet. En régime instantané,
     * rend les diagnostics de SYNTAXE seuls (garanties ①/② de la fiche 028).
     *
     * **Fraîcheur du mémo (écart G-003/H4, corrigé vague V-A)** : le mémo
     * enregistre les fichiers SOURCES du module lus sur disque pendant la
     * passe (empreinte STAT mtime+taille chacun) ; si l'API d'un fichier voisin
     * change sur disque, le mémo n'est PLUS servi et un diagnose complet
     * re-résout contre l'état frais — les diagnostics d'un document ouvert ne
     * restent jamais périmés au-delà de la demande suivante (la REPUBLICATION
     * sans demande reste du ressort de l'hôte, qui re-sollicite le moteur).
     *
     * @param chemin chemin logique du document (`/demo/Main.java`).
     * @param source le texte Java complet.
     * @return la liste de diagnostics (triée par offset croissant) ; JAMAIS nulle.
     */
    public fun diagnose(chemin: String, source: String): List<Diagnostic> = synchronized(this) {
        // Mémo : republish post-debounce (fiche 014, memeSource) — servi
        // uniquement si AUCUNE dépendance disque n'a changé (P8, ci-dessus).
        val etat = etatsRapides[chemin]
        if (etat?.source == source && etat.dependancesInchangees()) {
            compteurFastDiagnose++
            return etat.diagnostics
        }
        if (!isLikelyUsableInterne()) {
            compteurFullDiagnose++
            val diagnostics = RegimeInstantane.diagnosticsSyntaxe(pontEcj(), unite(chemin, source))
            etatsRapides[chemin] = DiagnoseRapide.Etat(source, diagnostics, emptyList(), null, null)
            return diagnostics
        }
        // Diagnose rapide : frappe confinée dans un seul corps (fiche 014).
        // Kill-switch : la propriété système coupe aussi le fast-path.
        val hote = if (FAST_DIAGNOSE_DISABLED || DESACTIVE_PAR_PROPRIETE) {
            null
        } else {
            DiagnoseRapide.preuveDeConfinement(etat, source)
        }
        if (hote != null) {
            compteurFastDiagnose++
            return diagnostiquerRapide(chemin, source, etat!!, hote)
        }
        // Diagnose complet.
        compteurFullDiagnose++
        return diagnostiquerComplet(chemin, source)
    }

    /**
     * Diagnostics de SYNTAXE seuls, dans tout régime (fiche 028 : le repli
     * est natif, pas une exception) : parse sans résolution, tri par offset.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @return les diagnostics de syntaxe (jamais nuls).
     */
    public fun diagnoseSyntax(chemin: String, source: String): List<Diagnostic> = synchronized(this) {
        RegimeInstantane.diagnosticsSyntaxe(pontEcj(), unite(chemin, source))
    }

    /** Diagnose complet : résolution, enrichissement de hiérarchie, caches. */
    private fun diagnostiquerComplet(chemin: String, source: String): List<Diagnostic> {
        // Table ANALYTIQUE (étage N8, fiche 056) : les corps ENTIERS sont
        // analysés — l'import inutilisé est un vrai signal publié au
        // client (tag Unnecessary côté protocole) ; le diagnose RAPIDE
        // (corps masqués) garde la table stricte (fiche 014).
        val environnement = environnementNoms()
        val piste = environnement.ouvrirPiste()
        val reponse = try {
            pontEcj().resoudre(unite(chemin, source), genererCode = true, analytique = true)
        } finally {
            environnement.fermerPiste()
        }
        var diagnostics = reponse.problemes().map { RegimeInstantane.convertir(it) }
            .sortedWith(compareBy({ it.plage.debutOffset }, { it.plage.finOffset }))
        diagnostics = enrichirHierarchie(diagnostics, reponse.arbre)
        val uniteResolue = UniteResolue(reponse.arbre, chemin, source, genererCode = true)
        cacheParse.ranger(chemin, source, uniteResolue, genererCode = true, focused = null)
        val corps = ScannerCorps.corpsDeMethodes(source) ?: emptyList()
        etatsRapides[chemin] = DiagnoseRapide.Etat(
            source,
            diagnostics,
            corps,
            arbreFocalise = null,
            corpsHote = null,
            dependances = empreintes(piste),
            analytiquesHorsCorps = analytiquesHorsCorps(diagnostics, corps),
        )
        return diagnostics
    }

    /**
     * Diagnose rapide : source masquée + portage des corps masqués (fiche 014)
     * + portage des signaux ANALYTIQUES hors corps (retours appareil du
     * 30/10/2026 : l'import inutilisé et le membre privé inutilisé restent
     * affichés PENDANT la frappe — la passe rapide ne peut pas les recalculer
     * honnêtement, le signal du dernier diagnose complet est PORTÉ).
     *
     * **Re-validation du portage hors-corps (vague V-A, ADR 0025 — P1)** :
     * les signaux portés sont GABARITÉS par les règles du témoin neuf
     * (mesurées ecj 3.40 : import inutilisé exige une unité sans AUCUNE
     * erreur ; membre privé inutilisé exige une unité sans erreur de
     * SYNTAXE) — l'état d'erreur pris en compte couvre le frais (corps hôte
     * + hors-corps) ET les corps masqués portés, soit l'unité entière.
     */
    private fun diagnostiquerRapide(chemin: String, source: String, etat: DiagnoseRapide.Etat, hote: PlageCorps): List<Diagnostic> {
        val corps = ScannerCorps.corpsDeMethodes(source) ?: emptyList()
        val masquee = DiagnoseRapide.sourceMasquee(source, corps, hote)
        val environnement = environnementNoms()
        val piste = environnement.ouvrirPiste()
        // Table RAPIDE (locale inutilisée allumée — corps hôte intact) :
        // les usages d'une locale vivent dans SA méthode, aucun fallacieux.
        val reponse = try {
            pontEcj().resoudre(unite(chemin, masquee), genererCode = true, rapide = true)
        } finally {
            environnement.fermerPiste()
        }
        var frais = reponse.problemes().map { RegimeInstantane.convertir(it) }
            .sortedWith(compareBy({ it.plage.debutOffset }, { it.plage.finOffset }))
        frais = enrichirHierarchie(frais, reponse.arbre)
        val indexHote = corps.indexOf(hote)
        val portables = DiagnoseRapide.portables(etat.diagnostics, etat.corps, corps, indexHote)
        // État d'erreur de l'unité ENTIÈRE (frais = hôte + hors-corps,
        // portables = corps masqués) — l'entrée du gabarit du témoin.
        val erreursUnite = (frais + portables).filter { it.severite == Severite.ERREUR }
        val portage = DiagnoseRapide.portablesHorsCorps(
            etat.analytiquesHorsCorps,
            etat.corps,
            corps,
            indexHote,
            aUneErreur = erreursUnite.isNotEmpty(),
            aUneErreurSyntaxe = erreursUnite.any(DiagnoseRapide::estErreurSyntaxe),
        )
        val resultat = (frais + portables + portage.publies)
            .sortedWith(compareBy({ it.plage.debutOffset }, { it.plage.finOffset }))
        // L'arbre focalisé sert les features DANS le corps hôte (fiche 015) ;
        // le cache de parse n'est PAS rempli par un diagnose rapide.
        etatsRapides[chemin] = DiagnoseRapide.Etat(
            source,
            resultat,
            corps,
            arbreFocalise = UniteResolue(reponse.arbre, chemin, masquee, genererCode = true),
            corpsHote = hote,
            dependances = empreintes(piste),
            analytiquesHorsCorps = portage.portes,
        )
        return resultat
    }

    /**
     * Enrichissement du diagnostic « La hiérarchie du type X est
     * incohérente » (fiche 019) : quand l'ancêtre manquant est le super
     * d'un supertype BINAIRE (classe d'un jar dont la propre superclasse a
     * été retirée), le message est replié en
     * `… — absent du classpath : <FQN> (requis par <exigeant>)`.
     *
     * Garde anti-fuite (fiche 019) : une hiérarchie saine ne produit AUCUN
     * message contenant « classpath » ; le chemin « supertype source
     * inconnu » reste best-effort (erreur ecj publiée, enrichissement absent).
     */
    private fun enrichirHierarchie(diagnostics: List<Diagnostic>, arbre: org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration?): List<Diagnostic> {
        if (arbre == null) return diagnostics
        val amorce = "The hierarchy of the type "
        if (diagnostics.none { it.message.startsWith(amorce) && it.message.endsWith(" is inconsistent") }) {
            return diagnostics
        }
        val env = environnementNoms()
        return diagnostics.map { diagnostic ->
            if (!diagnostic.message.startsWith(amorce) || !diagnostic.message.endsWith(" is inconsistent")) {
                return@map diagnostic
            }
            val nomType = diagnostic.message
                .removePrefix(amorce)
                .removeSuffix(" is inconsistent")
            val supertypes = supertypesBinairesDe(arbre, nomType)
            for ((exigeant, superFqcn) in supertypes) {
                if (env.trouverFqcn(superFqcn) == null) {
                    return@map diagnostic.copy(
                        message = diagnostic.message + " — absent du classpath : $superFqcn (requis par $exigeant)",
                    )
                }
            }
            diagnostic
        }
    }

    /**
     * Fournit (exigeant, super-FQN-déclaré) pour les supertypes BINAIRES
     * d'un type source : lit le `.class` du supertype dans le jar et rend
     * le nom de SA superclasse déclarée.
     */
    private fun supertypesBinairesDe(
        arbre: org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration,
        nomType: String,
    ): List<Pair<String, String>> {
        val resultats = mutableListOf<Pair<String, String>>()
        val declarations = mutableListOf<org.eclipse.jdt.internal.compiler.ast.TypeDeclaration>()
        fun collecter(types: Array<org.eclipse.jdt.internal.compiler.ast.TypeDeclaration>?) {
            types?.forEach { type ->
                declarations.add(type)
                collecter(type.memberTypes)
            }
        }
        collecter(arbre.types)
        for (declaration in declarations) {
            if (String(declaration.name) != nomType) continue
            val superReference = declaration.superclass ?: continue
            val binding = try {
                superReference.resolvedType as? ReferenceBinding
            } catch (_: Exception) {
                null
            } ?: continue
            val fqcnSuper = String(binding.readableName())
            val octets = environnementNoms().cacheLocalisation.octetsPour(fqcnSuper) ?: continue
            val lecteur = try {
                ClassFileReader.read(octets, fqcnSuper.replace('.', '/') + ".class")
            } catch (_: Exception) {
                null
            } ?: continue
            val superDeclare = lecteur.superclassName?.let { String(it).replace('/', '.') } ?: continue
            if (superDeclare != "java.lang.Object") {
                resultats.add(fqcnSuper to superDeclare)
            }
        }
        return resultats
    }

    // ------------------------------------------------------------------ //
    // Résolution et features par-frappe
    // ------------------------------------------------------------------ //

    /**
     * Résout une unité de compilation (bindings vivants), avec le cache de
     * parse (fiche 025) : la clé est le couple (chemin, source) ; un hit
     * rend la MÊME instance d'arbre.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param genererCode attribut d'entrée (une entrée codegen n'est jamais
     *   dégradée par un put sans codegen).
     * @return l'unité résolue, ou null en régime instantané (pas de plateforme).
     */
    public fun resolveUnit(chemin: String, source: String, genererCode: Boolean): UniteResolue? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        val existante = cacheParse.obtenir(chemin, source)
        if (existante != null) {
            return existante.unite
        }
        val reponse = pontEcj().resoudre(unite(chemin, source), genererCode)
        val uniteResolue = UniteResolue(reponse.arbre, chemin, source, genererCode)
        cacheParse.ranger(chemin, source, uniteResolue, genererCode, focused = null)
        return uniteResolue
    }

    /**
     * Hover minimal par-frappe (fiche 015) : servi par la chaîne à trois
     * niveaux — arbre focalisé (garde de contenu + garde d'offset dans le
     * corps hôte), arbre réel du cache de parse, résolution fraîche
     * (réduite par [SourceFocuser] au-delà de 2 000 caractères).
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet (garde de contenu).
     * @param offset la position interrogée.
     * @return le texte de hover, ou null (aucun nœud résolu à l'offset —
     *   réponse honnête, jamais un contenu périmé).
     */
    public fun hoverAt(chemin: String, source: String, offset: Int): String? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        val cible = resoudrePourOffset(chemin, source, offset) ?: return null
        // Clôtures markdown (fiche 021 — contrat historique du hover
        // texte : «```java\n…\n```») ; JAMAIS la documentation.
        LectureAst.hover(cible.arbre, cible.position)?.let { "```java\n$it\n```" }
    }

    /**
     * Survol RICHE au caret (étage N3 — cahier fiche 021) : la signature du
     * symbole PLUS la javadoc de sa déclaration quand elle vit dans la même
     * unité (documentation null sinon — périmètre honnête). Même chaîne de
     * service à trois niveaux que [hoverAt].
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée.
     * @return le survol structuré, ou null (aucun nœud résolu à l'offset).
     */
    public fun survolRiche(chemin: String, source: String, offset: Int): SurvolRiche? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        val cible = resoudrePourOffset(chemin, source, offset) ?: return null
        ServiceSurvolRiche.survol(cible.arbre, cible.sourceArbre, cible.position)
    }

    /**
     * Définition de la référence au caret (étage N4 — cahier fiche 049) :
     * le ROUTEUR complet du GO TO definition — intra-fichier (locale,
     * champ, méthode ou type de l'unité courante), puis PROJET (le type
     * déclarant se localise par l'annuaire, la cible est la plage du JETON
     * nom dans SA source), puis BINAIRE (`jdt://decompiled/…`, plage
     * vide — le client appelle ensuite [decompiledSource]).
     *
     * Le remappage vers la source ORIGINALE est garanti (fiche 062) :
     * quand la résolution passe par une source RÉDUITE (SourceFocuser),
     * les plages rendues sont traduites en coordonnées du document
     * original — jamais en coordonnées réduites.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return les cibles (liste NON NULL, vide honnête — fiche 049 :
     *   document inconnu ou curseur hors symbole).
     */
    public fun definition(chemin: String, source: String, offset: Int): List<CibleNavigation> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        val cible = resoudrePourOffset(chemin, source, offset) ?: return emptyList()
        val cibles = serviceNavigation().definition(chemin, cible.arbre, cible.sourceArbre, cible.position)
        return cibles.map { c -> remapperCourante(c, cible.remap) }
    }

    /**
     * Définition du TYPE du symbole au caret (étage N4 — cahier fiche
     * 062) : la variable vise la déclaration de son TYPE ; le type
     * BINAIRE rend l'URI synthétique `jdt://decompiled/<chemin>.java` à
     * plage VIDE ; le type de base rend une liste VIDE.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return les cibles (0 ou 1 — remappées en coordonnées originales).
     */
    public fun typeDefinition(chemin: String, source: String, offset: Int): List<CibleNavigation> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        val cible = resoudrePourOffset(chemin, source, offset) ?: return emptyList()
        val cibles = serviceNavigation().typeDefinition(chemin, cible.arbre, cible.sourceArbre, cible.position)
        return cibles.map { c -> remapperCourante(c, cible.remap) }
    }

    /**
     * Implémentations du type au caret (étage N4 — cahier fiche 060) :
     * les héritiers et implémenteurs DIRECTS du type en contexte —
     * balayage inter-fichiers des racines (l'énumération de l'index de
     * mots), une cible par héritier sur le NOM de classe.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return les cibles (vide si aucune descendance directe).
     */
    public fun implementation(chemin: String, source: String, offset: Int): List<CibleNavigation> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        val cible = resoudrePourOffset(chemin, source, offset) ?: return emptyList()
        return serviceNavigation().implementation(chemin, cible.arbre, cible.sourceArbre, cible.position)
    }

    /**
     * Super au caret (étage N4 — cahier fiches 049/060) : le membre
     * OUTREPASSÉ dans le supertype (libellé « nom  ·  Classe »), sinon
     * les supertypes DIRECTS du type en contexte (libellé « Nom  ·  paquet »)
     * — un supertype BINAIRE ne rend AUCUNE cible (règle D3).
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return les cibles internes (chemin + plage + libellé picker).
     */
    public fun superDefinition(chemin: String, source: String, offset: Int): List<CibleSuper> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        val cible = resoudrePourOffset(chemin, source, offset) ?: return emptyList()
        return serviceNavigation().superDefinition(chemin, cible.arbre, cible.sourceArbre, cible.position)
    }

    /**
     * FQN au caret (étage N4 — cahier fiche 005 : `fqcnAt`) : la sonde du
     * câble « GO TO sur cible binaire » côté serveur LSP.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée.
     * @param cibleType `targetType` du cahier : faux → la classe
     *   DÉCLARANTE du symbole ; vrai → le TYPE du symbole.
     * @return le FQN, ou null (caret sans binding, type de base, source
     *   vide, offset hors bornes).
     */
    public fun fqcnAt(chemin: String, source: String, offset: Int, cibleType: Boolean = false): String? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        if (source.isEmpty()) return null
        if (offset < 0 || offset > source.length) return null
        val cible = resoudrePourOffset(chemin, source, offset) ?: return null
        return serviceNavigation().fqcnAt(chemin, cible.arbre, cible.sourceArbre, cible.position, cibleType)
    }

    /**
     * Sonde de type binaire (étage N4 — cahier fiche 005 : `isBinaryType`) :
     * vrai ssi le FQCN existe et vit hors des sources du projet.
     *
     * @param fqcn le FQCN testé (null/vide → faux).
     * @return vrai pour un jar, l'image jrt ou un stub intrinsèque.
     */
    public fun isBinaryType(fqcn: String?): Boolean = synchronized(this) {
        if (fqcn.isNullOrEmpty()) return false
        if (!isLikelyUsableInterne()) return false
        return serviceDecompilation().estTypeBinaire(fqcn)
    }

    /**
     * La source d'un type binaire (étage N4 — cahier fiche 009 :
     * `decompiledSource`) : la source ATTACHÉE du JDK (`lib/src.zip`)
     * sinon le STUB décompilé minimal (en-tête « Decompiled from … »,
     * méthodes publiques comprises).
     *
     * @param fqcn le FQCN (null/vide → null, sans exiger de JDK).
     * @return le texte Java, ou null (FQCN inconnu).
     */
    public fun decompiledSource(fqcn: String?): String? = synchronized(this) {
        if (fqcn.isNullOrEmpty()) return null
        return serviceDecompilation().sourceDecompilee(fqcn)
    }

    /**
     * Les chaînes de sélection aux positions demandées (étage N4 — cahier
     * fiche 027) : le miroir moteur de `textDocument/selectionRange` —
     * STRICTEMENT croissantes (chaque niveau contient strictement le
     * précédent — contrat LSP 3.17), escalade complète jeton → expression
     * → instruction → bloc → déclaration → type → unité, sommet = toute
     * la source.
     *
     * Tolérances verrouillées : offsets hors bornes ou source vide →
     * chaîne ABSENTE ; source imparsable → chaîne partielle mais BORNÉE.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offsets les positions interrogées.
     * @return une chaîne par offset (null = position invalide).
     */
    public fun chainesSelection(chemin: String, source: String, offsets: List<Int>): List<ChaineSelection?> = synchronized(this) {
        if (!isLikelyUsableInterne()) return offsets.map { null }
        return serviceSelection().chainesSelection(chemin, source, offsets)
    }

    /**
     * La cible de formatage à la frappe (étage N4 — cahier fiche 027) :
     * le miroir moteur de `textDocument/onTypeFormatting` — la
     * construction qui SE TERMINE au caractère tapé (`}` → la méthode
     * entière, `;` → l'instruction).
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caractère tapé.
     * @param declencheur le déclencheur demandé (`}` ou `;`).
     * @return la plage cible, ou null (déclencheur hors liste, caractère
     *   différent à l'offset).
     */
    public fun plageFormattable(chemin: String, source: String, offset: Int, declencheur: Char): Plage? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        return serviceSelection().plageFormattable(chemin, source, offset, declencheur)
    }

    /**
     * Les usages du symbole au caret (étage N4 — ADR 0014 : le
     * QueryExecutor sur l'index de mots, `Files.walk` retiré du chemin
     * chaud) : le miroir moteur de `textDocument/references` — EXHAUSTIF
     * sur les racines, par clé de binding (homonymes départagés par le
     * propriétaire et l'arité).
     *
     * @param chemin chemin logique du document interrogé.
     * @param source le texte du document (tampon ouvert — prime sur le disque).
     * @param offset la position du caret.
     * @param inclureDeclaration inclure la déclaration (paramètre LSP).
     * @return les usages triés (chemin, offset) — vide honnête.
     */
    public fun references(
        chemin: String,
        source: String,
        offset: Int,
        inclureDeclaration: Boolean = true,
    ): List<Usage> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        return serviceUsages().references(chemin, source, offset, inclureDeclaration)
    }

    /**
     * Les usages par BALAYAGE COMPLET (étage N4) : l'ORACLE du banc et des
     * tests — toutes les sources des racines résolues, même correspondance
     * par clé que [references]. Ce chemin traverse volontiers
     * l'arborescence : il n'est JAMAIS sur le chemin chaud (c'est le
     * vérificateur du critère « rappel usages = oracle »).
     *
     * @param chemin chemin logique du document interrogé.
     * @param source le texte du document (tampon ouvert).
     * @param offset la position du caret.
     * @return les usages exhaustifs (même forme que [references]).
     */
    public fun referencesParBalayageComplet(chemin: String, source: String, offset: Int): List<Usage> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        return serviceUsages().referencesParBalayageComplet(chemin, source, offset)
    }

    /**
     * Les surbrillances du document courant (étage N4) : le miroir moteur
     * de `textDocument/documentHighlight` — toutes les occurrences du
     * symbole au caret dans le document SEUL, avec leur genre (méthode et
     * type → TEXTE ; variable → ECRITURE sur la déclaration et les cibles
     * d'affectation, LECTURE sinon).
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return les surbrillances (vide honnête).
     */
    public fun surbrillances(chemin: String, source: String, offset: Int): List<Surbrillance> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        return serviceUsages().surbrillances(chemin, source, offset)
    }

    /**
     * Le nombre de constructions FROIDES de l'index de mots (étage N4) :
     * l'observable du critère « `Files.walk` retiré du chemin chaud » —
     * 1 après N requêtes si aucune invalidation.
     *
     * @return le nombre de constructions de l'index.
     */
    public fun constructionsIndexMots(): Int = synchronized(this) { indexMotsSources().constructions() }

    // ------------------------------------------------------------------ //
    // Renommage par phases (étage N5 — cahier fiches 026 et 061)
    // ------------------------------------------------------------------ //

    /**
     * La CIBLE de renommage au caret (étage N5 — cahier fiche 026) :
     * le symbole identifié par bindings — nom actuel, genre, clé
     * structurelle (« method:paquet.Classe.nom( » / « field:… » /
     * « type:… ») et périmètre. Le nom d'un CONSTRUCTEUR vise le TYPE ;
     * un segment d'IMPORT est une cible comme une autre ; une LOCALE
     * est file-local.
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte Java complet (tampon ouvert).
     * @param offset la position du caret.
     * @return la cible, ou null (caret hors identifiant renommable).
     */
    public fun cibleRenommageA(chemin: String, source: String, offset: Int): CibleRenommage? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        return moteurRenommage().cibleA(chemin, source, offset)
    }

    /**
     * Les occurrences du renommage par fichier (étage N5 — cahier
     * fiche 026 : `renameProject`) : EXHAUSTIF sur les racines par clé
     * de binding (homonymes exclus), imports et constructeurs compris
     * pour un type ; les fichiers sans occurrence sont ABSENTS de la
     * sortie. Une LOCALE court-circuite le balayage projet (une seule
     * entrée : le fichier courant).
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte du document (tampon ouvert — prime sur le disque).
     * @param offset la position du caret.
     * @param suivreOverrides suivre les déclarations outrépassées dans
     *   les sous-types (opt-in explicite, faux par défaut — ADR 0014).
     * @return la map triée fichier → occurrences (triées par offset).
     */
    public fun renommageProjet(
        chemin: String,
        source: String,
        offset: Int,
        suivreOverrides: Boolean = false,
    ): Map<String, List<OccurrenceRenommage>> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyMap()
        return moteurRenommage().renommageProjet(chemin, source, offset, suivreOverrides)
    }

    /**
     * L'ORACLE du banc de renommage (étage N5) : les occurrences par
     * BALAYAGE COMPLET des racines, même correspondance par clé — le
     * rappel de [renommageProjet] est mesuré CONTRE cette map (« 0
     * occurrence manquée », la moitié occurrences du critère « 0 conflit
     * manqué (oracle) »). Ce chemin traverse exprès l'arborescence :
     * JAMAIS sur le chemin chaud.
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte du document (tampon ouvert).
     * @param offset la position du caret.
     * @return la map exhaustive (même forme que [renommageProjet]).
     */
    public fun renommageProjetParBalayageComplet(
        chemin: String,
        source: String,
        offset: Int,
    ): Map<String, List<OccurrenceRenommage>> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyMap()
        return moteurRenommage().renommageProjetParBalayageComplet(chemin, source, offset)
    }

    /**
     * Les COLLISIONS typées du renommage proposé (étage N5 — ADR 0014 :
     * liste typée de collisions, détectées AVANT écriture) :
     * identifiant invalide, nom pris, masquage, override non suivi,
     * référence brisée par re-résolution de l'aperçu.
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte du document (tampon ouvert).
     * @param offset la position du caret.
     * @param nouveauNom le nom proposé.
     * @param suivreOverrides suivre les overrides (opt-in — supprime la
     *   collision OVERRIDE_NON_SUIVI).
     * @return les collisions typées (vide = renommage sûr).
     */
    public fun conflitsRenommage(
        chemin: String,
        source: String,
        offset: Int,
        nouveauNom: String,
        suivreOverrides: Boolean = false,
    ): List<ConflitRenommage> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        return moteurRenommage().conflits(chemin, source, offset, nouveauNom, suivreOverrides)
    }

    /**
     * Les éditions d'un renommage (étage N5 — phase EXÉCUTION) : le
     * JETON remplacé EXACTEMENT par le nouveau nom, une édition par
     * occurrence, offsets croissants.
     *
     * @param nouveauNom le nouveau nom.
     * @param occurrences les occurrences retouchées (un seul fichier).
     * @return les éditions triées par offset croissant.
     */
    public fun editionsRenommage(nouveauNom: String, occurrences: List<OccurrenceRenommage>): List<EditionTexte> =
        moteurRenommage().editions(nouveauNom, occurrences)

    /**
     * L'APERÇU du renommage (étage N5 — preview = résultat) : la source
     * après application des éditions — le moteur n'écrit JAMAIS sur le
     * disque, l'aperçu est la preuve que la prévisualisation et le
     * résultat coïncident.
     *
     * @param source la source originale du fichier.
     * @param editions les éditions du renommage pour CE fichier.
     * @return la source previewée.
     */
    public fun apercuRenommage(source: String, editions: List<EditionTexte>): String =
        moteurRenommage().apercu(source, editions)

    // ------------------------------------------------------------------ //
    // Étage N6 — code actions v2 (ModCommand) & correcteurs
    // ------------------------------------------------------------------ //

    /**
     * Les actions correctrices d'une liste de diagnostics (étage N6 —
     * cahier fiche 063 ; ADR 0013 : quick-fixes à édits réels, plans
     * déclaratifs sérialisables) : le miroir moteur du
     * `textDocument/codeAction` LSP. Le dispatch est ID-FIRST et
     * Locale-indépendant ; l'exécution est déléguée au client (le moteur
     * n'écrit JAMAIS sur le disque).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet (tampon ouvert).
     * @param diagnostics les diagnostics du contexte de la requête.
     * @return les actions (chacune porte des édits non vides ; vide si
     *   aucune famille corrigible).
     */
    public fun actionsCode(
        chemin: String,
        source: String,
        diagnostics: List<Diagnostic>,
    ): List<jo.codeide.lsp.java.actionscode.ActionCode> = synchronized(this) {
        serviceActionsCode().actions(chemin, source, diagnostics)
    }

    /**
     * Les actions correctrices d'UN diagnostic (étage N6 — montage
     * synthétique du cahier : le banc appelle correcteur par correcteur).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet (tampon ouvert).
     * @param diagnostic le diagnostic à corriger.
     * @return les actions (0 à N — try-catch ET throws se servent ensemble).
     */
    public fun actionsCodePour(
        chemin: String,
        source: String,
        diagnostic: Diagnostic,
    ): List<jo.codeide.lsp.java.actionscode.ActionCode> = synchronized(this) {
        serviceActionsCode().actions(chemin, source, diagnostic)
    }

    /**
     * Le déclassement honnête des imports Data Binding (étage N6 — cahier
     * fiche 051) : la couche de PUBLICATION déclasse en HINT les imports
     * `.databinding` non résolus d'un module SANS classes générées (le
     * remède est un Build) — avec coupure honnête : dès que la première
     * classe générée existe (racines sources ou jars du moteur), plus
     * AUCUN downgrade sur ce paquet. Le diagnose du moteur reste SANS
     * compromis ; les autres diagnostics passent INTACTS.
     *
     * @param diagnostics les diagnostics publiés par le moteur.
     * @return la liste PUBLIÉE (diagnostics déclassés ou intacts).
     */
    public fun declasserDatabinding(diagnostics: List<Diagnostic>): List<Diagnostic> =
        jo.codeide.lsp.java.actionscode.DeclassementDatabinding.declasser(
            diagnostics,
            racines,
            jars,
        )

    /**
     * Indices inlay de l'unité (étage N3 — cahier fiches 002/003/030) :
     * paramètres « nom: » aux sites d'appel de méthodes SOURCE, types « : T »
     * des `var` et des paramètres lambda implicites, frontières de
     * chaînage multi-lignes. Liste DÉLIBÉRÉMENT non triée (paramètres avant
     * types — contrat fiche 003) ; AUCUN filtrage par plage (responsabilité
     * de l'éditeur).
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @return les indices de l'unité (vide en régime instantané).
     */
    public fun inlayHints(chemin: String, source: String): List<IndiceInlay> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        val unite = resolveUnit(chemin, source, genererCode = false) ?: return emptyList()
        ServiceInlay.indices(unite.arbre, source)
    }

    /**
     * Aide de signature au caret (fiche 015, étendue N3 — cahier fiche
     * 023) : même chaîne de service que [hoverAt], puis — si le texte
     * vivant est cassé — le « niveau 4 » : variantes réparées résolues une
     * à une jusqu'à ce qu'un appel couvre le caret (garde absolue : hors
     * appel, null — jamais rien d'inventé hors d'une liste d'arguments). Le
     * résolu arrive en tête des signatures, puis les surcharges du
     * sélecteur (binaires comprises) ; le paramètre actif compte les
     * virgules de séparation hors littéraux.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return l'aide de signature, ou null si aucun appel ne couvre l'offset.
     */
    public fun signatureHelp(chemin: String, source: String, offset: Int): AideSignature? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        val cible = resoudrePourOffset(chemin, source, offset)
        if (cible != null) {
            val aide = AideSignatures.aide(cible.arbre, cible.sourceArbre, cible.position)
            if (aide != null && aide.signatures.isNotEmpty()) return aide
        }
        // Niveau 4 (fiche 023) : texte vivant cassé — variantes réparées,
        // la première qui résout un appel couvrant le caret gagne.
        for (variante in AideSignatures.variantesReparees(source, offset)) {
            val reponse = pontEcj().resoudre(unite(chemin, variante), false)
            val aide = reponse.arbre?.let { AideSignatures.aide(it, variante, offset) }
            if (aide != null && aide.signatures.isNotEmpty()) return aide
        }
        return null
    }

    /**
     * Complétion au caret : MEMBRES du récepteur résolu dans le régime
     * plein (`recepteur.prefixe`), sinon inventaire du fichier courant +
     * mots-clés (complétion instantanée, fiche 028). Ne rend JAMAIS null —
     * une liste, éventuellement vide, est toujours rendue (honnêteté
     * protocolaire).
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java (null ou vide toléré).
     * @param offset la position du caret.
     * @return les items de complétion.
     */
    public fun complete(chemin: String, source: String?, offset: Int): List<ItemCompletion> = synchronized(this) {
        completer(chemin, source, offset).items
    }

    /**
     * Complétion RICHE au caret (étage N2 — fiches 007/008/035/043/044) :
     * le pipeline complet — analyse de contexte (sonde à chaud puis source
     * marquée), collecte par genre, filtrage gradué (le préfixe domine,
     * bonus −200), chaîne de peseurs D8, gel des têtes et plafond honnête
     * de 120 items (total réel communiqué).
     *
     * En régime instantané (fiche 028), le service rend la complétion du
     * fichier courant (champs/méthodes/locales/mots-clés filtrés par
     * préfixe) — le repli est natif, jamais une levée.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java (null ou vide toléré).
     * @param offset la position du caret.
     * @return la réponse (items + total disponible + genre) — jamais nulle.
     */
    public fun completer(chemin: String, source: String?, offset: Int): ReponseCompletion = synchronized(this) {
        if (source.isNullOrEmpty()) return ReponseCompletion(emptyList(), 0, jo.codeide.lsp.java.completion.GenreContexte.INCONNU)
        if (!isLikelyUsableInterne()) {
            val items = RegimeInstantane.completionInstantanee(pontEcj(), unite(chemin, source), source, offset)
            return ReponseCompletion(items, items.size, jo.codeide.lsp.java.completion.GenreContexte.NOM)
        }
        serviceCompletion().completer(chemin, source, offset)
    }

    /**
     * Chaîne de service à trois niveaux (fiche 015) :
     * (1) arbre focalisé — double garde : contenu identique + offset DANS le
     * corps hôte (jamais hors du corps hôte) ;
     * (2) arbre réel du parseCache (offset translaté si réduction) ;
     * (3) résolution fraîche (réduction SourceFocuser au-delà du seuil),
     * qui REMPLIT le cache sans compter comme un hit.
     */
    private fun resoudrePourOffset(chemin: String, source: String, offset: Int): CibleLecture? {
        val etat = etatsRapides[chemin]
        if (etat?.arbreFocalise != null && etat.source == source && etat.corpsHote != null && etat.corpsHote!!.contient(offset)) {
            compteurFastResolve++
            return CibleLecture(etat.arbreFocalise!!.arbre, offset, etat.arbreFocalise!!.source) { p -> p }
        }
        val entree = cacheParse.obtenir(chemin, source)
        if (entree != null) {
            compteurCachedResolve++
            val position = entree.focused?.mapAvant(offset) ?: offset
            val sourceArbre = entree.focused?.source() ?: source
            val remap: (Int) -> Int = entree.focused?.let { f -> { p -> f.mapBack(p) } } ?: { p -> p }
            return CibleLecture(entree.unite.arbre, position, sourceArbre, remap)
        }
        val focused = SourceFocuser.focusAndMap(source, offset)
        val reponse = pontEcj().resoudre(unite(chemin, focused.source()), genererCode = false)
        val uniteResolue = UniteResolue(reponse.arbre, chemin, focused.source(), genererCode = false)
        cacheParse.ranger(
            chemin,
            source,
            uniteResolue,
            genererCode = false,
            focused = if (focused.source().length != source.length) focused else null,
        )
        val remap: (Int) -> Int = { p -> focused.mapBack(p) }
        return CibleLecture(reponse.arbre, focused.offset(), focused.source(), remap)
    }

    /**
     * Cible de lecture : arbre + position d'interrogation + source dans le
     * système de l'arbre + traduction inverse réduite → ORIGINALE
     * (identité quand la résolution n'a pas réduit la source — fiche 062 :
     * les plages rendues au client vivent TOUJOURS en coordonnées
     * originales).
     */
    private class CibleLecture(
        val arbre: org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration?,
        val position: Int,
        val sourceArbre: String,
        val remap: (Int) -> Int,
    )

    // ------------------------------------------------------------------ //
    // Requêtes d'index (fiche 032) — filtre « $ » sur les trois surfaces
    // ------------------------------------------------------------------ //

    /**
     * Types par préfixe de nom simple (fiche 032) : les FQCN binaires bruts
     * avec `$` ne doivent JAMAIS atteindre le client — le filtre s'applique
     * en AVAL de l'index, même contre un index imparfait.
     */
    public fun findTypesByPrefix(prefixe: String, limite: Int): List<String> {
        val index = indexNoms ?: return emptyList()
        return index.nomsParPrefixe(prefixe, limite)
            .flatMap { nom -> index.fqnsForSimpleName(nom) }
            .filter { fqcn -> !fqcn.contains('$') }
            .distinct()
            .take(limite)
    }

    /**
     * FQCN importables pour un nom simple (fiche 032) — filtre `$ » appliqué.
     */
    public fun fqnsForSimpleName(nom: String): List<String> {
        val index = indexNoms ?: return emptyList()
        return index.fqnsForSimpleName(nom).filter { fqcn -> !fqcn.contains('$') }
    }

    /**
     * Types d'un paquet pour la complétion d'import (fiche 032) : le type de
     * premier niveau reste proposé, les imbriquées brutes jamais.
     */
    public fun typesDansPaquet(paquet: String): List<String> {
        val index = indexNoms ?: return emptyList()
        return index.typesDansPaquet(paquet).filter { fqcn -> !fqcn.contains('$') }
    }

    // ------------------------------------------------------------------ //
    // Étage N7 — confort éditeur : formatage, pliage, imports, hiérarchies
    // ------------------------------------------------------------------ //

    /**
     * Formate la source entière (étage N7 — cahier fiches 016/017).
     *
     * Le formateur est une fonction PURE du texte (clean-room Kotlin — le
     * jar ecj patché n'embarque pas le formateur JDT) : aucune résolution,
     * aucune dépendance au classpath — exécutable tel quel sur Android/ART.
     *
     * @param source le texte Java complet.
     * @param style le style de formatage (Eclipse par défaut).
     * @return le texte formaté, ou null si imparsable (jamais mutilé).
     */
    public fun formater(source: String, style: StyleFormattage = StyleFormattage.ECLIPSE): String? =
        FormateurSource(compliance, style).format(source)

    /**
     * Formate la PLAGE demandée (fiche 016) — le résultat égale
     * l'application des éditions de [editionsFormatagePlage].
     *
     * @param source le texte Java complet.
     * @param debut le début de la plage.
     * @param longueur la longueur de la plage (0 → source inchangée).
     * @param style le style de formatage.
     * @return le texte résultat, ou null si imparsable.
     */
    public fun formaterPlage(
        source: String,
        debut: Int,
        longueur: Int,
        style: StyleFormattage = StyleFormattage.ECLIPSE,
    ): String? = FormateurSource(compliance, style).formatRange(source, debut, longueur)

    /**
     * Éditions de formatage de la PLAGE (fiche 016) : confinées à la plage,
     * ordre croissant, disjointes ; liste VIDE pour une plage vide (jamais
     * null) ; null pour une source imparsable.
     *
     * @param source le texte Java complet.
     * @param debut le début de la plage.
     * @param longueur la longueur de la plage.
     * @param style le style de formatage.
     * @return les éditions, une liste vide, ou null.
     */
    public fun editionsFormatagePlage(
        source: String,
        debut: Int,
        longueur: Int,
        style: StyleFormattage = StyleFormattage.ECLIPSE,
    ): List<EditionTexte>? = FormateurSource(compliance, style).editionsRange(source, debut, longueur)

    /**
     * Régions de pliage de l'unité (étage N7 — cahier fiches 011/052) :
     * fonction PURE du texte — le pliage ne dépend d'aucune résolution de
     * type (moteur vide au cahier).
     *
     * @param source le texte Java complet.
     * @return les régions en offsets (ordre du document).
     */
    public fun plis(source: String): List<Pli> = ServicePliage.plis(source)

    /**
     * Organise les imports de l'unité (étage N7 — cahier fiche 022) :
     * identification par BINDINGS, exemptions (java.lang, même paquet,
     * on-demand), bloc trié, plage de remplacement.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @return la proposition, ou null (régime instantané / imparsable).
     */
    public fun organiserImports(chemin: String, source: String): PropositionImports? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        val arbre = resolveUnit(chemin, source, genererCode = false)?.arbre ?: return null
        OrganisateurImports.organiser(arbre, source)
    }

    /**
     * Type au caret dans la hiérarchie (étage N7 — cahier fiche 018) :
     * référence ou déclaration, localisée dans les SOURCES du projet —
     * jamais d'URI factice pour un binaire (règle D3).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée.
     * @return la cible, ou null.
     */
    public fun typeAuCaret(chemin: String, source: String, offset: Int): CibleTypeHierarchie? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        serviceHierarchies().typeAuCaret(chemin, source, offset)
    }

    /**
     * Supertypes DIRECTS du type au caret (fiche 018) : superclasse et
     * interfaces, localisées dans les sources du projet.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée (nom déclaré du type).
     * @return les supertypes directs (binaires écartés).
     */
    public fun supertypesDirects(chemin: String, source: String, offset: Int): List<CibleTypeHierarchie> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        serviceHierarchies().supertypesDirects(chemin, source, offset)
    }

    /**
     * Sous-types du type au caret (fiche 018) : balayage des racines
     * sources du projet (balayage froid — l'index CIDX arrive en N4).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée (nom déclaré du type).
     * @return les sous-types localisés.
     */
    public fun sousTypes(chemin: String, source: String, offset: Int): List<CibleTypeHierarchie> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        serviceHierarchies().sousTypes(chemin, source, offset)
    }

    /**
     * Méthode au caret (fiche 018) : l'appel résolu vers sa déclaration,
     * ou la déclaration elle-même — localisée dans les sources projet.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée.
     * @return la cible, ou null.
     */
    public fun methodeAuCaret(chemin: String, source: String, offset: Int): CibleMethodeHierarchie? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        serviceHierarchies().methodeAuCaret(chemin, source, offset)
    }

    /**
     * Appels ENTRANTS de la méthode au caret (fiche 018) : balayage des
     * racines, groupés par méthode appelante (une entrée, sites multiples).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée (nom déclaré de la méthode).
     * @return les appelants entrants (vide sans méthode au caret).
     */
    public fun appelsEntrants(chemin: String, source: String, offset: Int): List<AppelantEntrant> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        serviceHierarchies().appelsEntrants(chemin, source, offset)
    }

    /**
     * Appels OUTANTS de la méthode au caret (fiche 018) : résolus vers des
     * déclarations SOURCE du projet — cibles binaires écartées (règle D3).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée (nom déclaré de la méthode).
     * @return les cibles sortantes avec leurs sites.
     */
    public fun appelsSortants(chemin: String, source: String, offset: Int): List<CibleSortante> = synchronized(this) {
        if (!isLikelyUsableInterne()) return emptyList()
        serviceHierarchies().appelsSortants(chemin, source, offset)
    }

    // ------------------------------------------------------------------ //
    // Invalidations et cycle de vie
    // ------------------------------------------------------------------ //

    /**
     * Invalide les entrées du cache de parse d'un chemin (didChange : le
     * flux LSP invalide le cache à chaque édition du document).
     *
     * @param chemin le chemin du document édité.
     */
    public fun invalidateParseCache(chemin: String) {
        synchronized(this) {
            cacheParse.invalider(chemin)
        }
    }

    /**
     * Invalide le cache de présence source (événement Created d'un `.java`
     * observé — fiche 006 : la nouvelle classe devient visible immédiatement)
     * ET l'index de mots des sources (étage N4 : les fichiers créés ou
     * disparus deviennent candidats — la reconstruction est froide et
     * paresseuse, à la prochaine requête d'usages).
     */
    public fun invalidateSourcePresence() {
        synchronized(this) {
            environnementNoms().cachePresence.invalider()
            indexMotsSources?.invalider()
        }
    }

    /**
     * Libère le moteur : caches vidés, environnement disposé. Le moteur
     * reste utilisable (l'environnement se reconstruit paresseusement) —
     * les entrées suivantes construisent des arbres distincts.
     */
    public fun dispose() {
        synchronized(this) {
            cacheParse.vider()
            etatsRapides.clear()
            environnement?.dispose()
            environnement = null
            pont = null
        }
    }

    /** Nombre d'entrées du cache de parse (sonde des tests du cahier). */
    public fun parseCacheSize(): Int = synchronized(this) { cacheParse.taille() }

    /** Nombre de diagnostiques COMPLETS depuis la naissance (fiche 014). */
    public val fullDiagnoseCount: Int
        get() = synchronized(this) { compteurFullDiagnose }

    /** Nombre de diagnostiques RAPIDES et mémos depuis la naissance (fiche 014). */
    public val fastDiagnoseHits: Int
        get() = synchronized(this) { compteurFastDiagnose }

    /** Nombre de services par l'arbre FOCALISÉ depuis la naissance (fiche 015). */
    public val fastResolveHits: Int
        get() = synchronized(this) { compteurFastResolve }

    /** Nombre de services par l'arbre RÉEL du cache de parse (fiche 015). */
    public val cachedResolveHits: Int
        get() = synchronized(this) { compteurCachedResolve }

    // ------------------------------------------------------------------ //
    // Internes
    // ------------------------------------------------------------------ //

    /** Sonde interne de plateforme (sous verrou). */
    private fun isLikelyUsableInterne(): Boolean = environnementNoms().plateformeResolvable()

    /** Service de hiérarchies (paresseux — racines + résolution + annuaire). */
    private fun serviceHierarchies(): ServiceHierarchies {
        serviceHierarchies?.let { return it }
        val nouveau = ServiceHierarchies(
            racines = racines,
            resoudre = { chemin, source -> resolveUnit(chemin, source, genererCode = false)?.arbre },
            localiser = { fqcn -> runCatching { environnementNoms().trouverFqcn(fqcn) }.getOrNull() },
            enumererFichiers = { indexMotsSources().fichiers() },
        )
        serviceHierarchies = nouveau
        return nouveau
    }

    /** Index de mots des sources (paresseux — construction froide unique). */
    private fun indexMotsSources(): IndexMotsSources {
        indexMotsSources?.let { return it }
        val index = IndexMotsSources(racines)
        indexMotsSources = index
        return index
    }

    /** Routeur de navigation (paresseux — résolution + annuaire + hiérarchies). */
    private fun serviceNavigation(): ServiceNavigation {
        serviceNavigation?.let { return it }
        val service = ServiceNavigation(
            resoudre = { chemin, source -> resolveUnit(chemin, source, genererCode = false)?.arbre },
            localiser = { fqcn -> runCatching { environnementNoms().trouverFqcn(fqcn) }.getOrNull() },
            sousTypesDeFqn = { fqn -> serviceHierarchies().sousTypesDeFqn(fqn) },
        )
        serviceNavigation = service
        return service
    }

    /** Service de décompilation (paresseux — environnement + home JDK). */
    private fun serviceDecompilation(): ServiceDecompilation {
        serviceDecompilation?.let { return it }
        val service = ServiceDecompilation(
            environnement = { environnementNoms() },
            jdkHomeExplicite = jdkHome,
        )
        serviceDecompilation = service
        return service
    }

    /** Service de sélection (paresseux — résolution de la source complète). */
    private fun serviceSelection(): ServiceSelection {
        serviceSelection?.let { return it }
        val service = ServiceSelection(
            resoudre = { chemin, source -> resolveUnit(chemin, source, genererCode = false)?.arbre },
        )
        serviceSelection = service
        return service
    }

    /** Exécuteur de requêtes d'usages (paresseux — index + résolution). */
    private fun serviceUsages(): ServiceUsages {
        serviceUsages?.let { return it }
        val service = ServiceUsages(
            racines = racines,
            indexMots = indexMotsSources(),
            resoudre = { chemin, source -> resolveUnit(chemin, source, genererCode = false)?.arbre },
        )
        serviceUsages = service
        return service
    }

    /** Moteur de renommage par phases (paresseux — index + résolution + diagnose + annuaire). */
    private fun moteurRenommage(): MoteurRenommage {
        moteurRenommage?.let { return it }
        val moteur = MoteurRenommage(
            racines = racines,
            indexMots = indexMotsSources(),
            resoudre = { chemin, source -> resolveUnit(chemin, source, genererCode = false)?.arbre },
            diagnostiquer = { chemin, source -> diagnose(chemin, source) },
            localiser = { fqcn ->
                (runCatching { environnementNoms().trouverFqcn(fqcn) }.getOrNull()
                    as? jo.codeide.lsp.java.classpath.EnvironnementNoms.Localisation.Source)?.fichier
            },
        )
        moteurRenommage = moteur
        return moteur
    }

    /** Service de code actions v2 (paresseux — correcteurs texte + index des noms). */
    private fun serviceActionsCode(): jo.codeide.lsp.java.actionscode.ServiceActionsCode {
        serviceActionsCode?.let { return it }
        val service = jo.codeide.lsp.java.actionscode.ServiceActionsCode(moteur = this)
        serviceActionsCode = service
        return service
    }

    /**
     * Remappe une cible vers la source ORIGINALE quand elle vit dans le
     * document courant (les plages de l'unité courante sont calculées sur
     * la source de l'arbre — potentiellement réduite, fiche 062) ; les
     * cibles PROJET et BINAIRE vivent dans LEUR source (disque / URI
     * synthétique) : identité.
     */
    private fun remapperCourante(cible: CibleNavigation, remap: (Int) -> Int): CibleNavigation {
        if (cible.genre != jo.codeide.lsp.java.navigation.GenreCible.SOURCE_COURANTE) return cible
        return cible.copy(plage = Plage(remap(cible.plage.debutOffset), remap(cible.plage.finOffset)))
    }

    /** Environnement de noms paresseux (jrt + jars ouverts au premier usage). */
    private fun environnementNoms(): EnvironnementNoms {
        environnement?.let { return it }
        val env = EnvironnementNoms(racines, jars, jdkHome)
        environnement = env
        return env
    }

    /** Pont ecj paresseux. */
    private fun pontEcj(): PontEcj {
        pont?.let { return it }
        val pont = PontEcj(environnementNoms(), compliance)
        this.pont = pont
        return pont
    }

    /** Service de complétion paresseux (étage N2). */
    private fun serviceCompletion(): ServiceCompletion {
        serviceCompletion?.let { return it }
        val service = ServiceCompletion(
            pontFournisseur = { pontEcj() },
            environnementFournisseur = { environnementNoms() },
            arbreOriginal = { chemin, source -> arbreOriginal(chemin, source) },
            chainePersonnalisee = chaineCompletion,
        )
        serviceCompletion = service
        return service
    }

    /**
     * Arbre résolu de la source ORIGINALE (cache de parse puis résolution
     * fraîche) — la sonde à chaud de la complétion y lit les récepteurs.
     */
    private fun arbreOriginal(chemin: String, source: String): org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration? {
        val existante = cacheParse.obtenir(chemin, source)
        if (existante != null) return existante.unite.arbre
        val reponse = pontEcj().resoudre(unite(chemin, source), genererCode = false)
        val uniteResolue = UniteResolue(reponse.arbre, chemin, source, genererCode = false)
        cacheParse.ranger(chemin, source, uniteResolue, genererCode = false, focused = null)
        return reponse.arbre
    }

    /** Unité de compilation en mémoire. */
    private fun unite(chemin: String, source: String): ICompilationUnit =
        UnitesSource.EnMemoire(chemin, source)

    /**
     * Les dépendances disque d'une passe, empreintées (P8 — fraîcheur du
     * mémo) : chaque fichier source servi par l'environnement de noms pendant
     * la fenêtre de pistage est empreinté par STAT (mtime+taille — coût
     * microseconde, cf. [DiagnoseRapide.empreinteFichier]) — le mémo ne sera
     * servi que si TOUTES ces empreintes restent valables (voir
     * [DiagnoseRapide.Etat.dependancesInchangees]).
     *
     * @param piste l'ensemble des fichiers consignés par la passe.
     * @return la carte chemin → empreinte (vide si la passe n'a rien lu).
     */
    private fun empreintes(piste: MutableSet<java.nio.file.Path>): Map<java.nio.file.Path, String> =
        piste.associateWith { DiagnoseRapide.empreinteFichier(it) ?: "" }

    /**
     * Les signaux analytiques HORS CORPS d'un diagnose complet (vague V-A,
     * ADR 0025) : les familles UNUSED (import, membre privé) hors de tout
     * corps de méthode — la SOURCE DE PORTAGE persistante des passes
     * rapides suivantes ([DiagnoseRapide.Etat.analytiquesHorsCorps]). Les
     * signaux DANS un corps ne portent pas (recalculés frais ou portés par
     * [DiagnoseRapide.portables] selon le corps).
     *
     * @param diagnostics le résultat frais du diagnose complet.
     * @param corps les plages de corps de la même source.
     * @return les seuls signaux analytiques hors corps.
     */
    private fun analytiquesHorsCorps(
        diagnostics: List<Diagnostic>,
        corps: List<PlageCorps>,
    ): List<Diagnostic> = diagnostics.filter { diagnostic ->
        val famille = jo.codeide.lsp.java.actionscode.FamilleProbleme.fromCode(diagnostic.code)
        if (famille != jo.codeide.lsp.java.actionscode.FamilleProbleme.UNUSED_IMPORT &&
            famille != jo.codeide.lsp.java.actionscode.FamilleProbleme.UNUSED_DECLARATION
        ) {
            return@filter false
        }
        corps.none { plage ->
            plage.debutInterieur <= diagnostic.plage.debutOffset &&
                diagnostic.plage.finOffset <= plage.finInterieur
        }
    }

    /**
     * Index des types du moteur (sondes fiche 039 : `find`, `hasBinarySource`).
     */
    public inner class IndexTypesMoteur internal constructor() {
        /**
         * Sonde de résolution d'un FQCN : sources → jars → jrt → stubs.
         *
         * @param fqcn nom entièrement qualifié en points.
         * @return la localisation, ou null si introuvable.
         */
        public fun find(fqcn: String): EnvironnementNoms.Localisation? = synchronized(this@MoteurJava) {
            environnementNoms().trouverFqcn(fqcn)
        }

        /**
         * Sonde de source binaire remplie (fiche 004 : `hasBinarySource`).
         */
        public fun hasBinarySource(): Boolean = synchronized(this@MoteurJava) {
            environnementNoms().aSourceBinaire()
        }
    }
}

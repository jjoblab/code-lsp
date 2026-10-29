package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.completion.ReponseCompletion
import jo.codeide.lsp.java.completion.ServiceCompletion
import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.interne.AideSignature
import jo.codeide.lsp.java.interne.CacheParse
import jo.codeide.lsp.java.interne.DiagnoseRapide
import jo.codeide.lsp.java.interne.ItemCompletion
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.PontEcj
import jo.codeide.lsp.java.interne.RegimeInstantane
import jo.codeide.lsp.java.interne.UniteResolue
import jo.codeide.lsp.java.interne.UnitesSource
import jo.codeide.lsp.java.outils.PlageCorps
import jo.codeide.lsp.java.outils.ScannerCorps
import jo.codeide.lsp.java.outils.SourceFocuser
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
 * (image jrt disponible) du régime instantané (fiche 028) — dans le repli,
 * `diagnose` sert la SYNTAXE seule (jamais de « cannot be resolved »
 * fabriqué) et `complete` sert le fichier courant.
 *
 * @param racines racines sources du module (FQCN par plus long préfixe).
 * @param jars jars du classpath du module.
 * @param jdkHome home JDK explicite (null = runtime ; sans `lib/modules` =
 *   régime instantané).
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
     */
    public constructor(
        racines: List<Path> = emptyList(),
        jars: List<Path> = emptyList(),
        jdkHome: Path? = null,
        compliance: String = "17",
    ) : this(racines, jars, jdkHome, compliance, null, null)

    /** Environnement de noms (paresseux — le premier usage paie jrt/jars). */
    private var environnement: EnvironnementNoms? = null

    /** Pont ecj (paresseux, lié à l'environnement). */
    private var pont: PontEcj? = null

    /** Cache de parse LRU (fiche 025). */
    private val cacheParse = CacheParse()

    /** Service de complétion & classement (étage N2), paresseux. */
    private var serviceCompletion: ServiceCompletion? = null

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
     * Sonde de disponibilité de la plateforme : vrai dès qu'une image jrt
     * (runtime ou externe) charge — faux en régime instantané (classpath
     * vide + jdkHome sans `lib/modules`, fiche 028).
     */
    public fun isLikelyUsable(): Boolean = synchronized(this) { environnementNoms().jrtDisponible() }

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
     * Pipeline : mémo (source identique) → preuve de confinement (diagnose
     * RAPIDE, fiche 014) → diagnose complet. En régime instantané, rend les
     * diagnostics de SYNTAXE seuls (garanties ①/② de la fiche 028).
     *
     * @param chemin chemin logique du document (`/demo/Main.java`).
     * @param source le texte Java complet.
     * @return la liste de diagnostics (triée par offset croissant) ; JAMAIS nulle.
     */
    public fun diagnose(chemin: String, source: String): List<Diagnostic> = synchronized(this) {
        // Mémo : republish post-debounce (fiche 014, memeSource).
        val etat = etatsRapides[chemin]
        if (etat?.source == source) {
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
        val reponse = pontEcj().resoudre(unite(chemin, source), genererCode = true)
        var diagnostics = reponse.problemes().map { RegimeInstantane.convertir(it) }
            .sortedWith(compareBy({ it.plage.debutOffset }, { it.plage.finOffset }))
        diagnostics = enrichirHierarchie(diagnostics, reponse.arbre)
        val uniteResolue = UniteResolue(reponse.arbre, chemin, source, genererCode = true)
        cacheParse.ranger(chemin, source, uniteResolue, genererCode = true, focused = null)
        etatsRapides[chemin] = DiagnoseRapide.Etat(
            source,
            diagnostics,
            ScannerCorps.corpsDeMethodes(source) ?: emptyList(),
            arbreFocalise = null,
            corpsHote = null,
        )
        return diagnostics
    }

    /** Diagnose rapide : source masquée + portage des corps masqués (fiche 014). */
    private fun diagnostiquerRapide(chemin: String, source: String, etat: DiagnoseRapide.Etat, hote: PlageCorps): List<Diagnostic> {
        val corps = ScannerCorps.corpsDeMethodes(source) ?: emptyList()
        val masquee = DiagnoseRapide.sourceMasquee(source, corps, hote)
        val reponse = pontEcj().resoudre(unite(chemin, masquee), genererCode = true)
        var frais = reponse.problemes().map { RegimeInstantane.convertir(it) }
            .sortedWith(compareBy({ it.plage.debutOffset }, { it.plage.finOffset }))
        frais = enrichirHierarchie(frais, reponse.arbre)
        val portables = DiagnoseRapide.portables(etat.diagnostics, etat.corps, corps, corps.indexOf(hote))
        val resultat = (frais + portables)
            .sortedWith(compareBy({ it.plage.debutOffset }, { it.plage.finOffset }))
        // L'arbre focalisé sert les features DANS le corps hôte (fiche 015) ;
        // le cache de parse n'est PAS rempli par un diagnose rapide.
        etatsRapides[chemin] = DiagnoseRapide.Etat(
            source,
            resultat,
            corps,
            arbreFocalise = UniteResolue(reponse.arbre, chemin, masquee, genererCode = true),
            corpsHote = hote,
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
        return LectureAst.hover(cible.arbre, cible.position)
    }

    /**
     * Aide de signature au caret (fiche 015) : même chaîne de service que
     * [hoverAt] ; le paramètre actif = nombre d'arguments complets avant le
     * caret.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return l'aide de signature, ou null si aucun appel ne couvre l'offset.
     */
    public fun signatureHelp(chemin: String, source: String, offset: Int): AideSignature? = synchronized(this) {
        if (!isLikelyUsableInterne()) return null
        val cible = resoudrePourOffset(chemin, source, offset) ?: return null
        return LectureAst.aideSignature(cible.arbre, cible.position)
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
            return CibleLecture(etat.arbreFocalise!!.arbre, offset, etat.arbreFocalise!!.source)
        }
        val entree = cacheParse.obtenir(chemin, source)
        if (entree != null) {
            compteurCachedResolve++
            val position = entree.focused?.mapAvant(offset) ?: offset
            val sourceArbre = entree.focused?.source() ?: source
            return CibleLecture(entree.unite.arbre, position, sourceArbre)
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
        return CibleLecture(reponse.arbre, focused.offset(), focused.source())
    }

    /** Cible de lecture : arbre + position d'interrogation + source dans le système de l'arbre. */
    private class CibleLecture(
        val arbre: org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration?,
        val position: Int,
        val sourceArbre: String,
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
     * observé — fiche 006 : la nouvelle classe devient visible immédiatement).
     */
    public fun invalidateSourcePresence() {
        synchronized(this) {
            environnementNoms().cachePresence.invalider()
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
    private fun isLikelyUsableInterne(): Boolean = environnementNoms().jrtDisponible()

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

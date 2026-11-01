package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.outils.EditionTexte
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.QualifiedAllocationExpression
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * AJOUT DE PARAMÈTRES — la résolution multi-fichiers (complément de
 * catalogue, ADR 0032 — reste consigné du PV V-D3) : l'appel FAUTIF
 * (`calcul(3, "x", 4L)` sur `calcul(int)`, `new Main(1, "y")` sur
 * `Main(int)`) ÉTEND la signature de la méthode visée — les paramètres
 * ajoutés sont TYPÉS par les types RÉSOLUS des arguments excédentaires
 * (bindings du site fautif — jamais un type reniflé), et chaque AUTRE
 * appelant de l'ancienne signature reçoit la VALEUR PAR DÉFAUT du type
 * (`0`/`false`/`null` — miroir du préremplissage du dialogue Change
 * Signature de la référence).
 *
 * **MESURÉ au banc (sonde du 01/11, R5)** : le site fautif MÉTHODE porte
 * un `MethodBinding` LISIBLE (`problemId()` = 0, `declaringClass` bon —
 * ecj garde le candidat le plus proche) : il est donc COLLECTÉ par le
 * graphe d'appelants à l'ANCIENNE arité — la règle « chaque site dont le
 * nombre d'arguments DÉPASSE l'ancienne arité ne se retouche PAS »
 * (l'appel fautif porte DÉJÀ les futurs paramètres). Le site fautif
 * CONSTRUCTEUR porte un `ProblemMethodBinding` (NotFound) — le type visé
 * se lit sur le NOM du type de l'allocation.
 *
 * **Refus honnêtes (R1 — jamais de transformation fausse)** :
 *  - « siteIntrouvable » : plus d'appel/allocation au caret (le document
 *    a bougé), ou bornes folles ;
 *  - « mismatch » : l'appel ne passe PAS d'arguments excédentaires (pur
 *    mismatch de types) ;
 *  - « typeIllisible » : un argument excédentaire est le littéral `null`
 *    ou un type non résolu ;
 *  - « binaire » : le type déclarant vit hors des sources (SDK/jar) ;
 *  - « surcharge » : le type déclarant porte PLUSIEURS méthodes du même
 *    sélecteur (l'extension du candidat le plus proche est le choix
 *    d'ecj, pas celui de l'utilisateur) ;
 *  - « sansConstructeur » : aucun constructeur explicite (le constructeur
 *    par défaut est synthétique — rien à étendre) ;
 *  - « varargs » : la signature cible est varargs (le rang d'un argument
 *    étalé ne se sait pas à l'appel) ;
 *  - « hierarchie » : la méthode outrepasse ou est outrépassée ;
 *  - « referenceMethode » : un `X::m` fixe la forme.
 *
 * @param arbre l'arbre RÉSOLU de l'unité courante (site fautif).
 * @param source la source Java complète de l'unité courante.
 * @param cheminCourant le chemin DISQUE de l'unité courante.
 * @param resoudre résout une unité (chemin logique, texte) en arbre bindé.
 * @param localiser localise le fichier source d'un FQN.
 * @param sitesPour le graphe des appelants d'une clé de méthode (socle
 *   ADR 0028 — fourni par le moteur).
 */
internal class ServiceAjoutParametres(
    private val arbre: CompilationUnitDeclaration?,
    private val source: String,
    private val cheminCourant: String,
    private val resoudre: (String, String) -> CompilationUnitDeclaration?,
    private val localiser: (String) -> Path?,
    private val sitesPour: (jo.codeide.lsp.java.navigation.CleUsages.Methode) -> List<ServiceChangeSignature.SiteAppel>,
) {

    /** Le verdict du resolve : refus motivé OU éditions par fichier. */
    internal class VerdictAjout(
        /** Le sélecteur lisible (méthode) ou le nom du type (constructeur). */
        val selecteur: String,
        /** Le motif de refus honnête (null = éditions servies). */
        val refus: String?,
        /** Le nombre de paramètres AJOUTÉS. */
        val ajoutes: Int,
        /** Le nombre d'APPELANTS retouchés (hors site fautif). */
        val appelants: Int,
        /** Les éditions par fichier (déclaration + appelants). */
        val editions: Map<String, List<EditionTexte>>,
    )

    /**
     * Les éditions de l'AJOUT : la signature étendue + la valeur par
     * défaut chez chaque autre appelant. Le calcul est payé ici (resolve)
     * — la garde d'abord, les éditions ensuite.
     *
     * @param offset le caret (le début du site fautif — sélecteur de
     *   l'envoi ou allocation entière).
     * @return le verdict (refus motivé ou éditions par fichier).
     */
    internal fun ajouter(offset: Int): VerdictAjout {
        val site = siteFautifAu(offset) ?: return VerdictAjout("", "siteIntrouvable", 0, 0, emptyMap())

        // Le FQN du type DÉCLARANT (top-niveau — miroir des clés d'usages).
        val fqnDeclarant = site.fqnDeclarant() ?: return VerdictAjout(site.selecteur, "typeIllisible", 0, 0, emptyMap())

        // Le fichier DÉCLARANT : l'unité courante (overlay) ou une source
        // localisée — jamais un binaire.
        val declarante = uniteDeclarante(fqnDeclarant)
            ?: return VerdictAjout(site.selecteur, "binaire", 0, 0, emptyMap())

        // La DÉCLARATION cible (méthode unique du sélecteur, ou l'unique
        // constructeur explicite).
        val cible = declarationCible(declarante.arbre, site, fqnDeclarant)
            ?: return VerdictAjout(site.selecteur, site.motifDeclarationManquante ?: "surcharge", 0, 0, emptyMap())

        val ariteAncienne = runCatching { cible.arguments?.size ?: 0 }.getOrDefault(0)
        val extras = site.arguments.drop(ariteAncienne)
        if (extras.isEmpty()) return VerdictAjout(site.selecteur, "mismatch", 0, 0, emptyMap())

        // Les TYPES des paramètres ajoutés : les types RÉSOLUS des
        // arguments excédentaires (bindings du site — jamais reniflés).
        val typesExtras = ArrayList<String>()
        for (argument in extras) {
            val nom = runCatching { argument.resolvedType?.readableName()?.let { String(it) } }.getOrNull()
            if (nom.isNullOrEmpty()) {
                return VerdictAjout(site.selecteur, "typeIllisible", 0, 0, emptyMap())
            }
            typesExtras.add(nom)
        }

        // Garde VARARGS (mesuré V-D3 : le drapeau vit sur le BINDING).
        if (runCatching { cible.binding?.isVarargs() == true }.getOrDefault(false)) {
            return VerdictAjout(site.selecteur, "varargs", 0, 0, emptyMap())
        }

        // Garde HÉRARCHIE (méthodes seulement — un constructeur ne
        // s'outrepasse pas).
        if (cible is MethodDeclaration &&
            ServiceChangeSignature(declarante.arbre, declarante.texte, declarante.chemin).outrepasseOuEstOutrepassee(cible)
        ) {
            return VerdictAjout(site.selecteur, "hierarchie", 0, 0, emptyMap())
        }

        // Le graphe des APPELANTS (clé à l'ancienne arité — socle ADR 0028).
        val cle = jo.codeide.lsp.java.navigation.CleUsages.Methode(
            fqnDeclarant,
            if (cible is ConstructorDeclaration) fqnDeclarant.substringAfterLast('.') else site.selecteur,
            ariteAncienne,
        )
        val sites = sitesPour(cle)
        if (sites.any { it.estReferenceMethode }) {
            return VerdictAjout(site.selecteur, "referenceMethode", 0, 0, emptyMap())
        }

        // Les NOMS disponibles : paramètres existants + locales du corps +
        // champs de l'unité déclarante (un nom de paramètre qui masquerait
        // un champ lu changerait la sémantique du corps — garde
        // conservatrice) + les types courts eux-mêmes.
        val pris = nomsPris(declarante.arbre, cible)
        val raccourcis = typesExtras.map { CorrecteurCreationMethode.raccourcirType(it) }
        val noms = ArrayList<String>(typesExtras.size)
        for ((indice, type) in typesExtras.withIndex()) {
            noms.add(nomParametreDisponible(type, pris))
            pris.add(noms.last())
            if (indice < raccourcis.size) pris.add(raccourcis[indice].court)
        }

        val editions = LinkedHashMap<String, MutableList<EditionTexte>>()

        // La DÉCLARATION : `, Type nom` après le dernier paramètre (ou
        // `Type nom` dans les parenthèses vides).
        val texteParams = raccourcis.mapIndexed { indice, r -> "${r.court} ${noms[indice]}" }.joinToString(", ")
        val ancreDeclaration = ancreFinParametres(cible, declarante.texte)
            ?: return VerdictAjout(site.selecteur, "siteIntrouvable", 0, 0, emptyMap())
        editions.getOrPut(declarante.chemin) { mutableListOf() }
            .add(EditionTexte(ancreDeclaration, 0, if (ariteAncienne == 0) texteParams else ", $texteParams"))

        // Les IMPORTS des types raccourcis (dans le fichier DÉCLARANT).
        CorrecteurCreationMethode.editionsImports(declarante.texte, raccourcis)?.let {
            editions.getOrPut(declarante.chemin) { mutableListOf() }.add(it)
        }

        // Les AUTRES appelants : la valeur PAR DÉFAUT de chaque type
        // ajouté, après le dernier argument. Les sites dont le nombre
        // d'arguments DÉPASSE l'ancienne arité ne se retouchent PAS —
        // l'appel fautif porte DÉJÀ les futurs paramètres (mesuré).
        val defauts = typesExtras.joinToString(", ") { valeurParDefaut(it) }
        var retouches = 0
        for (appelant in sites) {
            if (appelant.estReferenceMethode) continue
            if (appelant.arguments.size != ariteAncienne) continue
            val ancreAppelant = ancreFinArguments(appelant) ?: continue
            editions.getOrPut(appelant.chemin) { mutableListOf() }.add(
                EditionTexte(ancreAppelant, 0, if (ariteAncienne == 0) defauts else ", $defauts"),
            )
            retouches++
        }

        for (liste in editions.values) {
            liste.sortBy { it.offset }
        }
        return VerdictAjout(site.selecteur, null, typesExtras.size, retouches, editions)
    }

    // ------------------------------------------------------------------ //
    // Le site fautif
    // ------------------------------------------------------------------ //

    /** Le site fautif couvrant l'offset (le PLUS PETIT span gagne — l'envoi le plus profond). */
    private fun siteFautifAu(offset: Int): SiteFautif? {
        val collecteur = CollecteurSitesFautifs(offset)
        LectureAst.traverserResilient(arbre ?: return null, collecteur)
        return collecteur.sites.minByOrNull { it.fin - it.debut }
    }

    /** Un site d'appel fautif (envoi, allocation, appel explicite). */
    private inner class SiteFautif(
        /** Le début du site. */
        val debut: Int,
        /** La fin du site. */
        val fin: Int,
        /** Les arguments du site. */
        val arguments: List<Expression>,
        /** Le sélecteur LISIBLE (méthode ; nom du type pour un constructeur). */
        val selecteur: String,
        /** Le binding du site (méthode — le plus proche candidat pour un site fautif, mesuré). */
        val binding: MethodBinding?,
        /** Le type ALLOUÉ (allocations — le constructeur n'a pas de type propre). */
        val typeAlloue: ReferenceBinding?,
        /** Le motif de refus si la déclaration cible est introuvable/ambiguë. */
        var motifDeclarationManquante: String? = null,
    ) {
        /** Le FQN top-niveau du type DÉCLARANT (bindings). */
        fun fqnDeclarant(): String? = runCatching {
            var courant: ReferenceBinding? = when {
                typeAlloue != null -> typeAlloue
                binding != null -> binding.declaringClass
                else -> return null
            }
            while (courant?.enclosingType() != null) courant = courant.enclosingType()
            courant?.compoundName?.joinToString(".") { String(it) }
        }.getOrNull()
    }

    /** Le collecteur des sites d'appel couvrant l'offset. */
    private inner class CollecteurSitesFautifs(private val offset: Int) : LectureAst.VisiteurResilient() {
        val sites = ArrayList<SiteFautif>()

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            runCatching {
                if (offset in node.sourceStart..node.sourceEnd) {
                    sites.add(
                        SiteFautif(
                            node.sourceStart,
                            node.sourceEnd,
                            node.arguments?.toList() ?: emptyList(),
                            String(node.selector),
                            node.binding as? MethodBinding,
                            null,
                        ),
                    )
                }
            }
            return true
        }

        override fun visiteAllocation(node: AllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(
                node.arguments,
                node.sourceStart,
                node.sourceEnd,
                node.binding as? MethodBinding,
                node.type?.resolvedType as? ReferenceBinding ?: node.resolvedType as? ReferenceBinding,
            )
            return true
        }

        override fun visit(node: QualifiedAllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(
                node.arguments,
                node.sourceStart,
                node.sourceEnd,
                node.binding as? MethodBinding,
                node.type?.resolvedType as? ReferenceBinding ?: node.resolvedType as? ReferenceBinding,
            )
            return true
        }

        override fun visit(node: ExplicitConstructorCall, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            runCatching {
                if (offset in node.sourceStart..node.sourceEnd) {
                    val b = node.binding as? MethodBinding
                    val type = b?.declaringClass
                    if (type != null) {
                        sites.add(
                            SiteFautif(
                                node.sourceStart,
                                node.sourceEnd,
                                node.arguments?.toList() ?: emptyList(),
                                String(type.compoundName.last()),
                                b,
                                type,
                            ),
                        )
                    }
                }
            }
            return true
        }

        private fun considerer(
            arguments: Array<Expression>?,
            debut: Int,
            fin: Int,
            binding: MethodBinding?,
            type: ReferenceBinding?,
        ) {
            runCatching {
                if (type == null) return
                if (offset < debut || offset > fin) return
                sites.add(
                    SiteFautif(
                        debut,
                        fin,
                        arguments?.toList() ?: emptyList(),
                        String(type.compoundName.last()),
                        binding,
                        type,
                    ),
                )
            }
        }
    }

    // ------------------------------------------------------------------ //
    // La déclaration cible
    // ------------------------------------------------------------------ //

    /** L'unité DÉCLARANTE (chemin disque, texte, arbre résolu). */
    private data class UniteDeclarante(
        val chemin: String,
        val texte: String,
        val arbre: CompilationUnitDeclaration,
    )

    /** L'unité DÉCLARANTE : l'unité courante (overlay) si le type y vit, sinon la source localisée. */
    private fun uniteDeclarante(fqn: String): UniteDeclarante? {
        if (arbre != null) {
            val local = TypeDeFqnCollecteur(fqn)
            LectureAst.traverserResilient(arbre, local)
            if (local.trouve != null) return UniteDeclarante(cheminCourant, source, arbre)
        }
        val fichier = localiser(fqn)?.toString() ?: return null
        val texte = try {
            Path.of(fichier).readText()
        } catch (_: Exception) {
            return null
        }
        val arbreFichier = runCatching { resoudre(fichier, texte) }.getOrNull() ?: return null
        return UniteDeclarante(fichier, texte, arbreFichier)
    }

    /**
     * La déclaration CIBLE dans l'arbre déclarant : la méthode du
     * sélecteur (UNIQUE même sélecteur dans le type déclarant — la
     * surcharge est un refus honnête), ou l'UNIQUE constructeur explicite
     * du type alloué. L'appartenance au type se lit par BINDING
     * (declaringClass) — jamais par bornes (un type imbriqué couvre les
     * mêmes plages que son englobant).
     */
    private fun declarationCible(
        arbreCible: CompilationUnitDeclaration,
        site: SiteFautif,
        fqnDeclarant: String,
    ): AbstractMethodDeclaration? {
        val type = TypeDeFqnCollecteur(fqnDeclarant)
        LectureAst.traverserResilient(arbreCible, type)
        val declarationType = type.trouve ?: run {
            site.motifDeclarationManquante = "binaire"
            return null
        }
        val bindingType = runCatching { declarationType.binding }.getOrNull()
        val methodes = MethodeDeSelecteur(site.selecteur)
        LectureAst.traverserResilient(arbreCible, methodes)
        val candidates = methodes.trouvees.filter { methode ->
            runCatching {
                methode.binding != null &&
                    (bindingType == null || methode.binding?.declaringClass === bindingType)
            }.getOrDefault(false)
        }
        if (site.typeAlloue != null) {
            // CONSTRUCTEUR : l'unique explicite (le par défaut synthétique
            // ne compte JAMAIS — mesuré V-D1).
            val explicites = candidates.filter {
                it is ConstructorDeclaration && !runCatching { it.isDefaultConstructor() }.getOrDefault(false)
            }
            return when {
                explicites.isEmpty() -> {
                    site.motifDeclarationManquante = "sansConstructeur"
                    null
                }
                explicites.size > 1 -> {
                    site.motifDeclarationManquante = "surcharge"
                    null
                }
                else -> explicites.first()
            }
        }
        // MÉTHODE : l'unique du sélecteur (garde « surcharge »).
        if (candidates.size > 1) {
            site.motifDeclarationManquante = "surcharge"
            return null
        }
        return candidates.firstOrNull() ?: run {
            site.motifDeclarationManquante = "binaire"
            null
        }
    }

    // ------------------------------------------------------------------ //
    // Ancres d'insertion
    // ------------------------------------------------------------------ //

    /**
     * L'ancre de FIN des paramètres d'une déclaration : après le DERNIER
     * paramètre, ou juste après la parenthèse OUVRANTE (trouvée depuis le
     * JETON du sélecteur — les annotations parenthésées ne trompent pas
     * le scan).
     */
    private fun ancreFinParametres(methode: AbstractMethodDeclaration, texte: String): Int? {
        val arguments = runCatching { methode.arguments }.getOrNull()
        if (!arguments.isNullOrEmpty()) {
            val fin = arguments.last().declarationSourceEnd
            if (fin in 0 until texte.length) return fin + 1
        }
        val jeton = jo.codeide.lsp.java.renommage.plageJeton(
            texte,
            methode.declarationSourceStart,
            methode.declarationSourceEnd + 1,
            String(methode.selector),
        ) ?: return null
        var i = jeton.finOffset
        while (i < texte.length && texte[i].isWhitespace()) i++
        return if (i < texte.length && texte[i] == '(') i + 1 else null
    }

    /** L'ancre de FIN des arguments d'un site d'appel : après le dernier argument, ou après la '(' du jeton visé. */
    private fun ancreFinArguments(appelant: ServiceChangeSignature.SiteAppel): Int? {
        if (appelant.arguments.isNotEmpty()) {
            val fin = runCatching { appelant.arguments.last().sourceEnd }.getOrDefault(-1)
            if (fin in 0 until appelant.texte.length) return fin + 1
        }
        val texte = appelant.texte
        // La parenthèse qui suit le jeton visé (sélecteur de l'envoi, nom
        // du type d'une allocation, mot-clé this/super d'un appel
        // explicite — la plage du SITE la couvre, mesuré V-D2/V-D3).
        var i = appelant.plage.finOffset
        while (i < texte.length && texte[i].isWhitespace()) i++
        return if (i < texte.length && texte[i] == '(') i + 1 else null
    }

    // ------------------------------------------------------------------ //
    // Noms et défauts
    // ------------------------------------------------------------------ //

    /** Les noms PRIS pour un nouveau paramètre : paramètres, locales du corps, champs de l'unité déclarante. */
    private fun nomsPris(arbreCible: CompilationUnitDeclaration, methode: AbstractMethodDeclaration): HashSet<String> {
        val collecteur = NomsDuCorps(methode)
        LectureAst.traverserResilient(arbreCible, collecteur)
        val pris = HashSet<String>(collecteur.noms)
        for (argument in runCatching { methode.arguments ?: emptyArray() }.getOrDefault(emptyArray())) {
            argument.name?.let { pris.add(String(it)) }
        }
        return pris
    }

    /** Le collecteur des noms PRIS : locales du corps + champs de l'unité déclarante. */
    private class NomsDuCorps(private val methode: AbstractMethodDeclaration) : LectureAst.VisiteurResilient() {
        val noms = HashSet<String>()

        override fun visiteLocale(node: org.eclipse.jdt.internal.compiler.ast.LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            runCatching {
                if (couvreMethode(node.sourceStart, node.sourceEnd)) node.name?.let { noms.add(String(it)) }
            }
            return true
        }

        override fun visiteArgument(node: org.eclipse.jdt.internal.compiler.ast.Argument, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            runCatching {
                if (couvreMethode(node.sourceStart, node.sourceEnd)) node.name?.let { noms.add(String(it)) }
            }
            return true
        }

        override fun visiteChamp(node: org.eclipse.jdt.internal.compiler.ast.FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
            runCatching { node.name?.let { noms.add(String(it)) } }
            return true
        }

        private fun couvreMethode(debut: Int, fin: Int): Boolean = runCatching {
            methode.declarationSourceStart <= debut && methode.declarationSourceEnd >= fin
        }.getOrDefault(false)
    }

    /** Un nom de PARAMÈTRE disponible (heuristique par type — miroir du nommage V-D2, suffixe anti-collision). */
    private fun nomParametreDisponible(type: String, pris: Set<String>): String {
        val base = when {
            type == "boolean" -> "drapeau"
            type in setOf("int", "long", "short", "byte") -> "nombre"
            type in setOf("double", "float") -> "valeur"
            type == "char" -> "caractere"
            type.endsWith("String") -> "texte"
            else -> type.substringAfterLast('.').substringBefore('[').ifEmpty { "Objet" }.replaceFirstChar { it.lowercaseChar() }
        }
        var nom = base
        var rang = 2
        while (nom in pris) {
            nom = base + rang
            rang++
        }
        return nom
    }

    /** La valeur PAR DÉFAUT compilable d'un type (miroir V-A/V-D3 — le préremplissage de la référence). */
    private fun valeurParDefaut(type: String): String = when (type) {
        "boolean" -> "false"
        "byte", "short", "int", "long", "char", "float", "double" -> "0"
        else -> "null"
    }

    /** Le type DÉCLARÉ portant un FQN dans l'unité (miroir CorrecteurCreationChamp). */
    private class TypeDeFqnCollecteur(private val fqn: String) : LectureAst.VisiteurResilient() {
        var trouve: TypeDeclaration? = null

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            if (trouve != null) return true
            val b = runCatching { node.binding as? ReferenceBinding }.getOrNull()
            val fqnLocal = runCatching { b?.compoundName?.joinToString(".") { String(it) } }.getOrNull()
            if (fqnLocal == fqn) trouve = node
            return true
        }
    }

    /** Les déclarations du sélecteur (dans l'unité traversée). */
    private class MethodeDeSelecteur(private val selecteur: String) : LectureAst.VisiteurResilient() {
        val trouvees = ArrayList<AbstractMethodDeclaration>()

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            if (String(node.selector) == selecteur) trouvees.add(node)
            return true
        }

        override fun visiteConstructeur(node: ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            if (String(node.selector) == selecteur) trouvees.add(node)
            return true
        }
    }
}

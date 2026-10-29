package jo.codeide.lsp.java.interne

import jo.codeide.lsp.java.completion.GradeCorrespondance
import org.eclipse.jdt.internal.compiler.ASTVisitor
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.Argument
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.internal.compiler.ast.ImportReference
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.lookup.BlockScope
import org.eclipse.jdt.internal.compiler.lookup.ClassScope
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodScope
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding

/**
 * Nature d'un item de complétion (étage N1, cahier fiche 028) — kinds
 * DISTINCTS exigés par le contrat : FIELD / VARIABLE / KEYWORD / CLASS /
 * METHOD / PARAMETER.
 *
 * Étage N2 (complétion & classement) : le répertoire s'étend aux candidats
 * des fiches 007/008/043/044/050 — paquets (kind LSP Module), interfaces,
 * constantes d'enum (kind LSP Enum), snippets (stubs d'override, fiche 007)
 * et types annotation (kind LSP Class + marqueur `data` — fiche 050). Les
 * valeurs s'ajoutent EN FIN : l'ordre de l'énumération ordonne le repli
 * instantané N1 (contrat préservé).
 */
public enum class NatureItem {
    /** Type déclaré dans l'unité courante. */
    CLASSE,

    /** Méthode déclarée dans l'unité courante. */
    METHODE,

    /** Champ de l'unité courante. */
    CHAMP,

    /** Locale de la méthode englobante. */
    VARIABLE,

    /** Paramètre de la méthode englobante. */
    PARAMETRE,

    /** Mot-clé du langage Java. */
    MOT_CLE,

    /** Sous-paquet (segment simple) — kind LSP Module (fiche 050). */
    PAQUET,

    /** Type interface — kind LSP Interface (fiche 050). */
    INTERFACE,

    /** Type annotation (`@interface`) — kind LSP Class + data "annotation" (fiche 050). */
    ANNOTATION,

    /** Constante d'enum (labels de switch, valeurs d'annotation) — kind LSP Enum (fiche 007). */
    CONSTANTE_ENUM,

    /** Extrait de code — stub d'override (fiche 007) — kind LSP Snippet. */
    EXTRAIT,
}

/**
 * Un item de complétion.
 *
 * Étage N2 : l'item porte le CONTRAT CLIENT complet des fiches 007/008/
 * 036/043/044/050 — texte d'insertion distinct du libellé (segment simple
 * pour un paquet, extrait de code pour un stub), détail (FQN du paquet,
 * signature de méthode), proposition d'auto-import, marqueur `data` LSP
 * (p. ex. `annotation`) et grade de correspondance (banc de qualité).
 * Les champs nouveaux ont des VALEURS PAR DÉFAUT : les appels N1
 * (`ItemCompletion(libelle, nature)`) restent valides — compatibilité de
 * source conservée.
 *
 * @property libelle le texte affiché proposé au client (p. ex. `toString(`
 *   pour un stub d'override — fiche 007).
 * @property nature la nature de l'item (kind LSP côté hôte).
 * @property texteInsertion le texte inséré (null = le libellé) — JAMAIS un
 *   FQN en contexte de paquet déjà tapé (règle d'or 5, fiche 044).
 * @property detail le détail affichable (FQN complet d'un paquet — règle
 *   d'or 5 ; signature d'une méthode).
 * @property autoImportFqn l'import proposé avec l'item (null = aucun) —
 *   JAMAIS en contexte d'import déjà tapé (règle d'or 4, fiche 044).
 * @property extrait vrai ssi l'item est un snippet LSP (stubs d'override,
 *   fiche 007 : `$0` final obligatoire).
 * @property marqueur le champ `data` LSP (p. ex. `annotation` pour un type
 *   annotation — fiche 050 : badge « @ » côté client).
 * @property correspondance le grade de matching du préfixe (banc de
 *   qualité — la dominance du préfixe, fiche 008).
 * @property pertinence la pertinence contractuelle des gabarits (étage N7,
 *   fiche 029 : 2500 clé exacte — en tête, 100 clé partielle — discrète ;
 *   null = l'item ordinaire n'en porte pas).
 * @property editionsAdditionnelles les éditions ADDITIONNELLES du snippet
 *   (étage N7, fiche 029) : suppression du récepteur d'une postfixe,
 *   insertion d'import — en offsets de la source ORIGINALE, JAMAIS en
 *   chevauchement de la plage principale (contrainte LSP).
 */
public data class ItemCompletion(
    public val libelle: String,
    public val nature: NatureItem,
    public val texteInsertion: String? = null,
    public val detail: String? = null,
    public val autoImportFqn: String? = null,
    public val extrait: Boolean = false,
    public val marqueur: String? = null,
    public val correspondance: GradeCorrespondance? = null,
    public val pertinence: Int? = null,
    public val editionsAdditionnelles: List<jo.codeide.lsp.java.outils.EditionTexte> = emptyList(),
)

/**
 * Un candidat de signature pour l'aide de signature.
 *
 * @property libelle le libellé affichable (p. ex. `int add(int, int)`).
 * @property parametres les noms (ou types) des paramètres, dans l'ordre.
 */
public data class CandidatSignature(
    public val libelle: String,
    public val parametres: List<String>,
)

/**
 * L'aide de signature rendue au caret (étage N1, fiche 015).
 *
 * @property signatures les candidats (le résolu en premier).
 * @property signatureActive l'index de la signature active.
 * @property parametreActif l'index du paramètre en cours de saisie.
 */
public data class AideSignature(
    public val signatures: List<CandidatSignature>,
    public val signatureActive: Int,
    public val parametreActif: Int,
)

/**
 * Lecture des formes d'arbre consommées par le moteur (étage N1, fiches
 * 015/028/031/040) : hover, aide de signature, complétion de membres,
 * complétion instantanée et dump empirique des formes AST.
 *
 * La traversée est **résiliente** (fiche 040) : chaque visite est protégée —
 * un AST en recovery ne fait jamais échouer la requête qui le lit ; les nœuds
 * fautifs sont simplement ignorés.
 */
public object LectureAst {

    /** Mots-clés du langage proposés par la complétion instantanée (repli). */
    public val MOTS_CLES: List<String> = listOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null", "var", "record", "yield", "sealed", "permits",
    )

    /**
     * Hover minimal par-frappe : rend le texte descriptif du nœud le plus
     * profond couvrant l'offset.
     *
     * Formats (neutres, stables — l'équivalence du cahier se joue moteur
     * contre témoin, même fonction des deux côtés) :
     *  - référence de variable/locale/paramètre : `<type> <nom>` ;
     *  - invocation : `<retour> <sélecteur>(<types paramètres>)` ;
     *  - allocation : `new <type>(<types paramètres>)` ;
     *  - référence de type : le nom lisible du type.
     *
     * @param arbre l'arbre RÉSOLU (bindings vivants).
     * @param offset la position interrogée (dans le système de l'arbre).
     * @return le texte de hover, ou null si aucun nœud résolu ne couvre.
     */
    public fun hover(arbre: CompilationUnitDeclaration?, offset: Int): String? {
        val collecteur = HoverCollecteur(offset)
        traverserResilient(arbre, collecteur)
        return collecteur.meilleur
    }

    /**
     * Aide de signature au caret : cherche l'invocation la plus profonde dont
     * la liste d'arguments couvre l'offset.
     *
     * @param arbre l'arbre résolu.
     * @param offset la position du caret.
     * @return l'aide (candidat résolu + paramètre actif), ou null si aucun
     *   appel ne couvre l'offset.
     */
    public fun aideSignature(arbre: CompilationUnitDeclaration?, offset: Int): AideSignature? {
        val collecteur = AppelsCollecteur()
        traverserResilient(arbre, collecteur)
        val enveloppant = collecteur.appels
            .filter { it.contient(offset) }
            .minByOrNull { it.fin - it.debut } ?: return null
        return enveloppant.aide(offset)
    }

    /**
     * Complétion de MEMBRES minimale : au caret, trouve l'expression
     * qualifiée la plus profonde se terminant à l'offset (`recepteur.prefixe`)
     * et énumère les membres du type résolu du récepteur, filtrés par préfixe.
     *
     * @param arbre l'arbre résolu.
     * @param source le texte source DANS LE SYSTÈME DE L'ARBRE (réduit le cas échéant).
     * @param offset la position du caret (même système).
     * @return les items de membre (méthodes et champs), filtrés par préfixe.
     */
    public fun completionMembres(arbre: CompilationUnitDeclaration?, source: String, offset: Int): List<ItemCompletion> {
        val collecteur = MembresCollecteur(offset)
        traverserResilient(arbre, collecteur)
        val cible = collecteur.candidates.lastOrNull() ?: return emptyList()
        val texte = try {
            source.substring(cible.debut, cible.fin.coerceAtMost(source.length))
        } catch (_: Exception) {
            return emptyList()
        }
        val dernierPoint = texte.lastIndexOf('.')
        if (dernierPoint < 0) return emptyList()
        val prefixe = texte.substring(dernierPoint + 1)
        val type = cible.typeRecepteur ?: return emptyList()
        if (type !is ReferenceBinding) return emptyList()
        val items = mutableListOf<ItemCompletion>()
        for (champ in type.availableFields() ?: emptyArray()) {
            if (String(champ.name).startsWith(prefixe)) {
                items.add(ItemCompletion(String(champ.name), NatureItem.CHAMP))
            }
        }
        for (methode in type.availableMethods() ?: emptyArray()) {
            if (methode.isConstructor()) continue
            if (String(methode.selector).startsWith(prefixe)) {
                items.add(ItemCompletion(String(methode.selector), NatureItem.METHODE))
            }
        }
        return items.distinctBy { it.libelle }.sortedBy { it.libelle }
    }

    /**
     * Inventaire du fichier pour la complétion INSTANTANÉE (fiche 028) :
     * types, méthodes, champs de l'unité + locales et paramètres de la
     * méthode englobante du caret.
     *
     * @param arbre l'arbre PARSÉ (résolution non requise — le repli n'a pas
     *   de plateforme).
     * @param offset la position du caret.
     * @return les items du fichier (non filtrés par préfixe).
     */
    public fun inventaireFichier(arbre: CompilationUnitDeclaration?, offset: Int): List<ItemCompletion> {
        val collecteur = InventaireCollecteur(offset)
        traverserResilient(arbre, collecteur)
        return collecteur.items
    }

    /**
     * Dump empirique des formes AST (fiche 040) : lambdas, envois,
     * allocations — impressions des champs observables, AUCUNE assertion.
     *
     * @param arbre l'arbre résolu.
     * @return les lignes du dump (à imprimer/journaliser).
     */
    public fun dumpFormes(arbre: CompilationUnitDeclaration?): List<String> {
        val lignes = mutableListOf<String>()
        traverserResilient(arbre, object : VisiteurResilient() {
            override fun visiteLambda(node: LambdaExpression, scope: BlockScope?): Boolean {
                val arguments = node.arguments ?: emptyArray()
                lignes.add(
                    "[LAMBDA] elision=${node.argumentsTypeElided()} args=${arguments.size} " +
                        arguments.joinToString(" | ") { arg ->
                            val type = arg.type?.resolvedType
                            "${arg.name} : ${type?.readableName()?.let(::String) ?: "?"} @${arg.sourceStart}..${arg.sourceEnd}"
                        },
                )
                return true
            }

            override fun visiteEnvoi(node: MessageSend, scope: BlockScope?): Boolean {
                val nomPos = node.nameSourcePosition
                val nomDebut = (nomPos ushr 32).toInt()
                val nomFin = nomPos.toInt()
                lignes.add(
                    "[SEND] ${String(node.selector)} recepteur=${node.receiver?.javaClass?.simpleName ?: "?"} " +
                        "typeRecepteur=${lisible(node.actualReceiverType) ?: "?"} " +
                        "nom@$nomDebut..$nomFin finRecepteur=${node.receiver?.sourceEnd ?: -1}",
                )
                return true
            }

            override fun visiteAllocation(node: AllocationExpression, scope: BlockScope?): Boolean {
                val binding = node.binding
                lignes.add(
                    "[ALLOC] classe=${node.javaClass.simpleName} binding=${binding?.readableName()?.let(::String) ?: "?"} " +
                        "constructeur=${binding?.isConstructor() ?: false} declarante=${binding?.declaringClass?.readableName()?.let(::String) ?: "?"} " +
                        "args=${node.arguments?.size ?: 0}",
                )
                return true
            }
        })
        return lignes
    }

    /**
     * Traversée résiliente de l'arbre : les exceptions de visite sont
     * avalées nœud par nœud (fiche 040 — un AST en recovery ne fait jamais
     * échouer la lecture).
     *
     * @param arbre l'arbre à traverser.
     * @param visiteur le visiteur (surcharges protégées par try/catch).
     */
    public fun traverserResilient(arbre: CompilationUnitDeclaration?, visiteur: VisiteurResilient) {
        if (arbre == null) return
        try {
            arbre.traverse(visiteur, arbre.scope)
        } catch (_: Exception) {
            // La traversée racine peut elle-même échouer sur un arbre très cassé :
            // les nœuds déjà visités ont été collectés — propriété conservée.
        }
    }

    /** Base des visiteurs résilients : chaque visite est protégée. */
    public abstract class VisiteurResilient : ASTVisitor() {
        override fun visit(singleNameReference: SingleNameReference, scope: BlockScope?): Boolean = protege { visiteReferenceSimple(singleNameReference, scope) }
        override fun visit(qualifiedNameReference: QualifiedNameReference, scope: BlockScope?): Boolean = protege { visiteReferenceQualifiee(qualifiedNameReference, scope) }
        override fun visit(fieldReference: FieldReference, scope: BlockScope?): Boolean = protege { visiteReferenceChamp(fieldReference, scope) }
        override fun visit(messageSend: MessageSend, scope: BlockScope?): Boolean = protege { visiteEnvoi(messageSend, scope) }
        override fun visit(allocation: AllocationExpression, scope: BlockScope?): Boolean = protege { visiteAllocation(allocation, scope) }
        override fun visit(lambda: LambdaExpression, scope: BlockScope?): Boolean = protege { visiteLambda(lambda, scope) }
        override fun visit(localDeclaration: LocalDeclaration, scope: BlockScope?): Boolean = protege { visiteLocale(localDeclaration, scope) }
        override fun visit(fieldDeclaration: FieldDeclaration, scope: MethodScope?): Boolean = protege { visiteChamp(fieldDeclaration, scope) }
        override fun visit(methodDeclaration: MethodDeclaration, scope: ClassScope?): Boolean = protege { visiteMethode(methodDeclaration, scope) }
        override fun visit(constructorDeclaration: ConstructorDeclaration, scope: ClassScope?): Boolean = protege { visiteConstructeur(constructorDeclaration, scope) }
        override fun visit(typeDeclaration: TypeDeclaration, scope: ClassScope?): Boolean = protege { visiteType(typeDeclaration, scope) }
        override fun visit(typeDeclaration: TypeDeclaration, scope: CompilationUnitScope?): Boolean = protege { visiteType(typeDeclaration, null) }
        override fun visit(argument: Argument, scope: BlockScope?): Boolean = protege { visiteArgument(argument, scope) }
        override fun visit(importReference: ImportReference, scope: CompilationUnitScope?): Boolean = protege { visiteImport(importReference, scope) }
        override fun endVisit(methodDeclaration: MethodDeclaration, scope: ClassScope?): Unit = protegeUnit { finMethode(methodDeclaration, scope) }
        override fun endVisit(constructorDeclaration: ConstructorDeclaration, scope: ClassScope?): Unit = protegeUnit { finConstructeur(constructorDeclaration, scope) }

        private fun protege(corps: () -> Boolean): Boolean = try {
            corps()
        } catch (_: Exception) {
            false // nœud fautif : ne pas descendre
        }

        private fun protegeUnit(corps: () -> Unit) {
            try {
                corps()
            } catch (_: Exception) {
                // idem
            }
        }

        // --- points d'extension (tout retourne « descendre » par défaut) ---
        public open fun visiteReferenceSimple(node: SingleNameReference, scope: BlockScope?): Boolean = true
        public open fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: BlockScope?): Boolean = true
        public open fun visiteReferenceChamp(node: FieldReference, scope: BlockScope?): Boolean = true
        public open fun visiteEnvoi(node: MessageSend, scope: BlockScope?): Boolean = true
        public open fun visiteAllocation(node: AllocationExpression, scope: BlockScope?): Boolean = true
        public open fun visiteLambda(node: LambdaExpression, scope: BlockScope?): Boolean = true
        public open fun visiteLocale(node: LocalDeclaration, scope: BlockScope?): Boolean = true
        public open fun visiteChamp(node: FieldDeclaration, scope: MethodScope?): Boolean = true
        public open fun visiteMethode(node: MethodDeclaration, scope: ClassScope?): Boolean = true
        public open fun visiteConstructeur(node: ConstructorDeclaration, scope: ClassScope?): Boolean = true
        public open fun visiteType(node: TypeDeclaration, scope: ClassScope?): Boolean = true
        public open fun visiteArgument(node: Argument, scope: BlockScope?): Boolean = true
        public open fun visiteImport(node: ImportReference, scope: CompilationUnitScope?): Boolean = true
        public open fun finMethode(node: MethodDeclaration, scope: ClassScope?): Unit {}
        public open fun finConstructeur(node: ConstructorDeclaration, scope: ClassScope?): Unit {}
    }

    /** Traduction lisible et sûre d'un type résolu. */
    internal fun lisible(type: TypeBinding?): String? = try {
        type?.readableName()?.let(::String)
    } catch (_: Exception) {
        null
    }

    /** Texte de hover d'une référence nommée (locale, paramètre, champ ou type). */
    private fun texteReference(nom: String, binding: org.eclipse.jdt.internal.compiler.lookup.Binding?): String? {
        return when (binding) {
            is LocalVariableBinding -> {
                val type = lisible(binding.type) ?: return null
                "$type $nom"
            }

            is FieldBinding -> {
                val type = lisible(binding.type) ?: return null
                "$type $nom"
            }

            is VariableBinding -> {
                val type = lisible(binding.type) ?: return null
                "$type $nom"
            }

            is ReferenceBinding -> lisible(binding)
            is MethodBinding -> null
            else -> null
        }
    }

    /** Collecteur de hover : retient le candidat le plus profond couvrant l'offset. */
    private class HoverCollecteur(private val offset: Int) : VisiteurResilient() {
        var meilleur: String? = null
        private var profondeurMeilleur = -1

        override fun visiteReferenceSimple(node: SingleNameReference, scope: BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, texteReference(String(node.token), node.binding), PROFONDEUR_NOM)
            return true
        }

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, lisible(node.resolvedType), PROFONDEUR_NOM)
            return true
        }

        override fun visiteReferenceChamp(node: FieldReference, scope: BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, texteReference(String(node.token), node.binding), PROFONDEUR_NOM)
            return true
        }

        override fun visiteEnvoi(node: MessageSend, scope: BlockScope?): Boolean {
            val binding = node.binding
            val texte = if (binding is MethodBinding) {
                val retour = lisible(binding.returnType) ?: "?"
                val params = (binding.parameters ?: emptyArray()).joinToString(", ") { lisible(it) ?: "?" }
                "$retour ${String(node.selector)}($params)"
            } else {
                lisible(node.resolvedType)
            }
            considerer(node.sourceStart, node.sourceEnd, texte, PROFONDEUR_APPEL)
            return true
        }

        override fun visiteAllocation(node: AllocationExpression, scope: BlockScope?): Boolean {
            val binding = node.binding
            val texte = if (binding is MethodBinding) {
                val params = (binding.parameters ?: emptyArray()).joinToString(", ") { lisible(it) ?: "?" }
                "new ${lisible(binding.declaringClass) ?: "?"}($params)"
            } else {
                lisible(node.resolvedType)
            }
            considerer(node.sourceStart, node.sourceEnd, texte, PROFONDEUR_APPEL)
            return true
        }

        override fun visiteLocale(node: LocalDeclaration, scope: BlockScope?): Boolean {
            val type = lisible(node.type?.resolvedType)
                ?: lisible((node.binding as? VariableBinding)?.type)
            if (type != null) {
                considerer(node.sourceStart, node.sourceEnd, "$type ${String(node.name)}", PROFONDEUR_NOM)
            }
            return true
        }

        // Déclarations (étage N3 — fiche 020 : le hover sert aussi le NOM
        // d'une méthode, d'un champ ou d'un type DÉCLARÉ, pas seulement les
        // références). Priorité la plus basse : une référence ou un appel
        // plus serré couvrant le caret gagne toujours.
        override fun visiteMethode(node: MethodDeclaration, scope: ClassScope?): Boolean {
            val binding = node.binding
            val texte = if (binding != null) {
                val retour = lisible(binding.returnType) ?: "?"
                val params = (binding.parameters ?: emptyArray()).joinToString(", ") { lisible(it) ?: "?" }
                "$retour ${String(node.selector)}($params)"
            } else {
                null
            }
            considerer(node.sourceStart, node.declarationSourceEnd, texte, PROFONDEUR_DECLARATION)
            return true
        }

        override fun visiteChamp(node: FieldDeclaration, scope: MethodScope?): Boolean {
            val type = lisible(node.binding?.type) ?: lisible(node.type?.resolvedType)
            considerer(node.sourceStart, node.declarationSourceEnd, type?.let { "$it ${String(node.name)}" }, PROFONDEUR_DECLARATION)
            return true
        }

        override fun visiteType(node: TypeDeclaration, scope: ClassScope?): Boolean {
            considerer(node.sourceStart, node.declarationSourceEnd, lisible(node.binding), PROFONDEUR_DECLARATION)
            return true
        }

        private fun considerer(debut: Int, fin: Int, texte: String?, profondeur: Int) {
            if (texte == null) return
            if (offset < debut || offset > fin) return
            if (profondeur < profondeurMeilleur) return
            meilleur = texte
            profondeurMeilleur = profondeur
        }

        companion object {
            const val PROFONDEUR_NOM = 2
            const val PROFONDEUR_APPEL = 1
            const val PROFONDEUR_DECLARATION = 0
        }
    }

    /** Un appel de méthode repéré, avec sa plage d'arguments. */
    private class AppelEnveloppant(
        val debut: Int,
        val fin: Int,
        val arguments: List<Pair<Int, Int>>,
        val binding: MethodBinding?,
        val selector: String,
    ) {
        fun contient(offset: Int): Boolean = offset in debut..fin

        /**
         * Construit l'aide au caret : le paramètre ACTIF est l'index de
         * l'argument en cours de saisie — le nombre d'arguments COMPLETS
         * situés avant le caret (un argument est complet quand il FINIT
         * avant le caret) ; borné au nombre de paramètres de la signature.
         */
        fun aide(caret: Int): AideSignature {
            val candidats = mutableListOf<CandidatSignature>()
            if (binding != null) {
                val libelle = buildString {
                    append(lisible(binding.returnType) ?: "?")
                    append(' ')
                    append(selector)
                    append('(')
                    append((binding.parameters ?: emptyArray()).joinToString(", ") { lisible(it) ?: "?" })
                    append(')')
                }
                val noms = binding.parameterNames.map { String(it) }
                candidats.add(CandidatSignature(libelle, noms))
            }
            val nbParametres = binding?.parameters?.size ?: arguments.size
            val actif = arguments.count { (_, fin) -> fin < caret }.coerceAtMost(nbParametres)
            return AideSignature(
                signatures = candidats,
                signatureActive = 0,
                parametreActif = actif,
            )
        }
    }

    /** Collecteur des appels (plage récepeur→parenthèse fermante + arguments). */
    private class AppelsCollecteur : VisiteurResilient() {
        val appels = mutableListOf<AppelEnveloppant>()

        override fun visiteEnvoi(node: MessageSend, scope: BlockScope?): Boolean {
            val arguments = node.arguments ?: emptyArray()
            val plages = arguments.map { it.sourceStart to it.sourceEnd }
            appels.add(
                AppelEnveloppant(
                    debut = node.sourceStart,
                    fin = node.sourceEnd,
                    arguments = plages,
                    binding = node.binding,
                    selector = String(node.selector),
                ),
            )
            return true
        }
    }

    /** Candidat de complétion de membres : expression qualifiée finissant au caret. */
    private class CibleMembres(val debut: Int, val fin: Int, val typeRecepteur: TypeBinding?)

    /** Collecteur des expressions qualifiées pour la complétion de membres. */
    private class MembresCollecteur(private val caret: Int) : VisiteurResilient() {
        val candidates = mutableListOf<CibleMembres>()

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, typeRecepteurDe(node))
            return true
        }

        override fun visiteReferenceChamp(node: FieldReference, scope: BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.actualReceiverType)
            return true
        }

        override fun visiteEnvoi(node: MessageSend, scope: BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.actualReceiverType)
            return true
        }

        /**
         * Type du RÉCEPTEUR d'une référence qualifiée `recepteur.prefixe` :
         * pour la forme à deux fragments (le cas de la complétion de
         * membres), le binding du PREMIER fragment est le récepteur —
         * `actualReceiverType` reste sur le type ENGLOBANT quand le dernier
         * fragment ne résout pas (frappe en cours), il est donc inutilisable.
         */
        private fun typeRecepteurDe(node: QualifiedNameReference): TypeBinding? {
            val fragments = node.tokens?.size ?: 0
            if (fragments == 2) {
                (node.binding as? VariableBinding)?.let { return it.type }
            }
            return node.actualReceiverType
        }

        private fun considerer(debut: Int, fin: Int, type: TypeBinding?) {
            if (caret < debut || caret > fin + 1) return
            candidates.add(CibleMembres(debut, fin, type))
        }
    }

    /** Inventaire du fichier pour la complétion instantanée (fiche 028). */
    private class InventaireCollecteur(private val caret: Int) : VisiteurResilient() {
        val items = mutableListOf<ItemCompletion>()
        private val pileMethodes = ArrayDeque<PlageMethode>()

        override fun visiteType(node: TypeDeclaration, scope: ClassScope?): Boolean {
            items.add(ItemCompletion(String(node.name), NatureItem.CLASSE))
            return true
        }

        override fun visiteMethode(node: MethodDeclaration, scope: ClassScope?): Boolean {
            items.add(ItemCompletion(String(node.selector), NatureItem.METHODE))
            val corpsDebut = node.bodyStart
            val corpsFin = node.bodyEnd
            if (corpsDebut >= 0 && corpsFin >= 0) {
                pileMethodes.addLast(PlageMethode(corpsDebut, corpsFin))
            }
            // Paramètres : uniquement ceux de la méthode ENGLOBANT le caret
            // (contrat fiche 028 — portée = méthode englobante). La borne de
            // FIN est la fin de DÉCLARATION (le sourceEnd d'ecj s'arrête à
            // l'en-tête, le corps est couvert par declarationSourceEnd).
            if (node.sourceStart >= 0 && node.declarationSourceEnd >= 0 &&
                caret in node.sourceStart..node.declarationSourceEnd
            ) {
                for (argument in node.arguments ?: emptyArray()) {
                    items.add(ItemCompletion(String(argument.name), NatureItem.PARAMETRE))
                }
            }
            return true
        }

        override fun finMethode(node: MethodDeclaration, scope: ClassScope?) {
            if (pileMethodes.isNotEmpty()) pileMethodes.removeLast()
        }

        override fun visiteChamp(node: FieldDeclaration, scope: MethodScope?): Boolean {
            items.add(ItemCompletion(String(node.name), NatureItem.CHAMP))
            return true
        }

        override fun visiteLocale(node: LocalDeclaration, scope: BlockScope?): Boolean {
            // Les locales ne comptent que pour la méthode ENGLOBANTE du caret.
            val englobante = pileMethodes.lastOrNull { it.contient(caret) }
            if (englobante != null) {
                items.add(ItemCompletion(String(node.name), NatureItem.VARIABLE))
            }
            return true
        }
    }

    /** Plage de corps de méthode pour l'attribution des locales. */
    private class PlageMethode(private val debut: Int, private val fin: Int) {
        fun contient(offset: Int): Boolean = offset in debut..fin
    }
}

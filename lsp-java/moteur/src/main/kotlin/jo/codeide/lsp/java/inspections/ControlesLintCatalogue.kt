package jo.codeide.lsp.java.inspections

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import org.eclipse.jdt.internal.compiler.ast.Annotation
import org.eclipse.jdt.internal.compiler.ast.ArrayAllocationExpression
import org.eclipse.jdt.internal.compiler.ast.ArrayInitializer
import org.eclipse.jdt.internal.compiler.ast.Assignment
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.IntLiteral
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.NormalAnnotation
import org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.StringLiteral
import org.eclipse.jdt.internal.compiler.ast.TryStatement
import org.eclipse.jdt.internal.compiler.impl.Constant
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import java.util.IdentityHashMap

/**
 * Les contrôles Android LINT du COMPLÉMENT DE CATALOGUE (suite de la vague
 * V-C2 — ADR 0024, reste consigné du PV 1.7.0) : **Range**
 * (@IntRange/@FloatRange/@Size), **ResourceType** (@…Res contre la famille
 * réelle `R.<famille>.x`), **CommitPrefEdits**, **Recycle**.
 *
 * Règles communes (ADR 0023/0024) : mono-fichier, sévérité LSP Warning
 * (JAMAIS Error — un contrôle lint ne remplace pas ecj), suppression
 * `@SuppressLint` par le suppresseur commun, garde « erreur de résolution
 * = silence » du circuit annotator de la référence, repli honnête muet sur
 * liaison illisible. Les annotations se lisent DEUX fois : sur les
 * déclarations du MÊME fichier (AST résolu — clés par IDENTITÉ de binding)
 * et sur les bindings BINAIRES des .class du SDK/androidx
 * ([MethodBinding.getAnnotations]/[getParameterAnnotations] — muets si le
 * .class n'expose rien : jamais un signal fabriqué).
 */
public object ControleRange : InspectionMoteur {
    override val id: String = "Range"
    override val libelle: String = "Valeur hors bornes @IntRange/@FloatRange/@Size"
    override val groupe: String = "Correctitude Android"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT
    override val provenance: String get() = SOURCE_LINT

    /** La nature d'une annotation de plage. */
    internal enum class Nature { ENTIER, DECIMAL, TAILLE }

    /** Les bornes d'une annotation de plage. */
    internal data class Bornes(val nature: Nature, val minimum: Double?, val maximum: Double?, val exacte: Long?) {
        internal companion object {
            internal fun entiere(minimum: Double?, maximum: Double?): Bornes = Bornes(Nature.ENTIER, minimum, maximum, null)
            internal fun decimale(minimum: Double?, maximum: Double?): Bornes = Bornes(Nature.DECIMAL, minimum, maximum, null)
            internal fun taille(minimum: Double?, maximum: Double?, exacte: Long?): Bornes = Bornes(Nature.TAILLE, minimum, maximum, exacte)
        }
    }

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        if (contexte.erreursEcj.isNotEmpty()) return
        val arbre = contexte.arbre ?: return

        // Pré-passe : annotations de PARAMÈTRES des méthodes du fichier, par
        // IDENTITÉ de binding (les appels intra-fichier retombent dessus).
        val parametres = IdentityHashMap<MethodBinding, Array<List<Annotation>>>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                runCatching {
                    val liaison = node.binding ?: return@runCatching
                    parametres[liaison] = node.arguments?.map { it.annotations?.toList() ?: emptyList() }?.toTypedArray() ?: emptyArray()
                }
                return true
            }

            override fun visiteConstructeur(node: org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                runCatching {
                    val liaison = node.binding ?: return@runCatching
                    parametres[liaison] = node.arguments?.map { it.annotations?.toList() ?: emptyList() }?.toTypedArray() ?: emptyArray()
                }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)

        // Passe de contrôle : appels (arguments constants) + retours (méthode
        // annotée). La pile de méthodes donne l'englobante de chaque retour.
        val pileMethodes = ArrayDeque<org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration>()
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                pileMethodes.addLast(node)
                return true
            }

            override fun finMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?) {
                pileMethodes.removeLastOrNull()
            }

            override fun visiteConstructeur(node: org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                pileMethodes.addLast(node)
                return true
            }

            override fun finConstructeur(node: org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?) {
                pileMethodes.removeLastOrNull()
            }

            override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                val arguments = node.arguments ?: return true
                val liaisons = runCatching { node.binding }.getOrNull()
                for (index in arguments.indices) {
                    val bornes = bornesParametre(liaisons, index, parametres) ?: continue
                    eprouver(arguments[index], bornes, "paramètre ${index + 1} de « ${String(node.selector)} »", rapporteur)
                }
                return true
            }

            override fun visiteRetour(node: org.eclipse.jdt.internal.compiler.ast.ReturnStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                val expression = node.expression ?: return true
                val methode = pileMethodes.lastOrNull() ?: return true
                val bornes = methode.annotations?.mapNotNull(::bornesAst)?.firstOrNull() ?: return true
                eprouver(expression, bornes, "retour de « ${String(methode.selector)} »", rapporteur)
                return true
            }
        }
        LectureAst.traverserResilient(arbre, visiteur)
    }

    /** Éprouve une expression contre des bornes (rien à éprouver : silence). */
    private fun eprouver(expression: Expression, bornes: Bornes, cible: String, rapporteur: RapporteurInspection) {
        if (bornes.nature == Nature.TAILLE) {
            // @Size : la LONGUEUR d'un littéral de chaîne ou le CARDINAL d'un
            // initialisateur de tableau — rien d'autre n'est prouvable.
            (expression as? StringLiteral)?.let { litteral ->
                val taille = String(litteral.source()).length.toLong()
                if (horsTaille(taille, bornes)) {
                    rapporteur.signaler(
                        Plage(litteral.sourceStart, litteral.sourceEnd + 1),
                        "La chaîne de longueur $taille ne respecte pas @Size du $cible (${texteBornes(bornes)})",
                    )
                }
                return
            }
            (expression as? ArrayAllocationExpression)?.initializer?.let { initial ->
                val taille = (initial.expressions?.size ?: 0).toLong()
                if (horsTaille(taille, bornes)) {
                    rapporteur.signaler(
                        Plage(initial.sourceStart, initial.sourceEnd + 1),
                        "Le tableau de $taille élément(s) ne respecte pas @Size du $cible (${texteBornes(bornes)})",
                    )
                }
                return
            }
            (expression as? ArrayInitializer)?.let { initial ->
                val taille = (initial.expressions?.size ?: 0).toLong()
                if (horsTaille(taille, bornes)) {
                    rapporteur.signaler(
                        Plage(initial.sourceStart, initial.sourceEnd + 1),
                        "Le tableau de $taille élément(s) ne respecte pas @Size du $cible (${texteBornes(bornes)})",
                    )
                }
                return
            }
            return
        }
        // @IntRange/@FloatRange : la valeur CONSTANTE de l'expression.
        val constante = runCatching { expression.constant }.getOrNull() ?: return
        val valeur = runCatching { constante.doubleValue() }.getOrNull() ?: return
        if (horsValeur(valeur, bornes)) {
            rapporteur.signaler(
                Plage(expression.sourceStart, expression.sourceEnd + 1),
                "La valeur ${formate(valeur)} est hors bornes du $cible (${texteBornes(bornes)})",
            )
        }
    }

    /** Les bornes du paramètre i d'un appel (AST local par identité, puis binaire). */
    private fun bornesParametre(liaison: MethodBinding?, index: Int, parametres: IdentityHashMap<MethodBinding, Array<List<Annotation>>>): Bornes? {
        if (liaison != null) {
            for (annotation in parametres[liaison]?.getOrNull(index) ?: emptyList()) {
                bornesAst(annotation)?.let { return it }
            }
        }
        val binaires = runCatching {
            liaison?.getParameterAnnotations()?.getOrNull(index)?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
        for (annotation in binaires) {
            bornesBinding(annotation)?.let { return it }
        }
        return null
    }

    /** Les bornes d'une annotation AST (@IntRange/@FloatRange/@Size). */
    internal fun bornesAst(annotation: Annotation): Bornes? {
        val nom = runCatching { String(annotation.type.getLastToken()) }.getOrNull() ?: return null
        return when (nom) {
            "IntRange" -> when (annotation) {
                is NormalAnnotation -> Bornes.entiere(
                    valeurDouble(annotation, "from"),
                    valeurDouble(annotation, "to"),
                )
                else -> null
            }
            "FloatRange" -> when (annotation) {
                is NormalAnnotation -> Bornes.decimale(
                    valeurDouble(annotation, "from"),
                    valeurDouble(annotation, "to"),
                )
                else -> null
            }
            "Size" -> when (annotation) {
                is SingleMemberAnnotation -> Bornes.taille(null, null, valeurLongue(annotation.memberValue))
                is NormalAnnotation -> Bornes.taille(
                    valeurDouble(annotation, "min"),
                    valeurDouble(annotation, "max"),
                    valeurLongueDe(annotation, "value"),
                )
                else -> null
            }
            else -> null
        }
    }

    /** Les bornes d'une annotation BINAIRE (binding du .class). */
    internal fun bornesBinding(annotation: AnnotationBinding): Bornes? {
        val nom = runCatching { String(annotation.getAnnotationType()?.readableName() ?: return null) }.getOrNull() ?: return null
        val simple = nom.substringAfterLast('.')
        val paires = runCatching {
            annotation.getElementValuePairs()?.map { String(it.name) to it.value } ?: emptyList<Pair<String, Any?>>()
        }.getOrDefault(emptyList())
        fun numerique(cle: String): Double? = paires.firstOrNull { it.first == cle }?.second?.let(::nombreDe)?.toDouble()
        fun entiere(cle: String): Long? = paires.firstOrNull { it.first == cle }?.second?.let(::nombreDe)?.toLong()
        return when (simple) {
            "IntRange" -> Bornes.entiere(numerique("from"), numerique("to"))
            "FloatRange" -> Bornes.decimale(numerique("from"), numerique("to"))
            "Size" -> Bornes.taille(numerique("min"), numerique("max"), entiere("value"))
            else -> null
        }
    }

    /** La valeur numérique d'une valeur d'annotation binaire (résolue). */
    private fun nombreDe(valeur: Any?): Number? = when (valeur) {
        is Number -> valeur
        is Constant -> runCatching { valeur.longValue() }.getOrNull()
        is IntLiteral -> runCatching { String(valeur.source()).toLong() }.getOrNull()
        else -> null
    }

    /** La valeur d'une paire nommée d'une annotation AST (constante pliée). */
    private fun valeurDouble(annotation: NormalAnnotation, cle: String): Double? {
        val paire = annotation.memberValuePairs?.firstOrNull { String(it.name) == cle } ?: return null
        runCatching { paire.value.constant?.doubleValue() }.getOrNull()?.let { return it }
        return runCatching { paire.value.constant?.longValue()?.toDouble() }.getOrNull()
    }

    /** La valeur LONGUE d'une paire nommée d'une annotation AST. */
    private fun valeurLongueDe(annotation: NormalAnnotation, cle: String): Long? {
        val paire = annotation.memberValuePairs?.firstOrNull { String(it.name) == cle } ?: return null
        runCatching { paire.value.constant?.longValue() }.getOrNull()?.let { return it }
        (paire.value as? IntLiteral)?.let { return runCatching { String(it.source()).toLong() }.getOrNull() }
        return null
    }

    /** La valeur LONGUE d'une valeur d'annotation AST (constante pliée). */
    private fun valeurLongue(expression: Expression?): Long? {
        if (expression == null) return null
        runCatching { expression.constant?.longValue() }.getOrNull()?.let { return it }
        (expression as? IntLiteral)?.let { return runCatching { String(it.source()).toLong() }.getOrNull() }
        return null
    }

    private fun horsValeur(valeur: Double, bornes: Bornes): Boolean {
        if (bornes.minimum != null && valeur < bornes.minimum!!) return true
        if (bornes.maximum != null && valeur > bornes.maximum!!) return true
        return false
    }

    private fun horsTaille(taille: Long, bornes: Bornes): Boolean {
        bornes.exacte?.let { return taille != it }
        if (bornes.minimum != null && taille < bornes.minimum!!.toLong()) return true
        if (bornes.maximum != null && taille > bornes.maximum!!.toLong()) return true
        return false
    }

    private fun formate(valeur: Double): String =
        if (valeur == valeur.toLong().toDouble()) valeur.toLong().toString() else valeur.toString()

    private fun texteBornes(bornes: Bornes): String = buildString {
        if (bornes.nature == Nature.TAILLE && bornes.exacte != null) append("valeur ${bornes.exacte}")
        if (bornes.minimum != null) {
            if (isNotEmpty()) append(", ")
            append("min ${formate(bornes.minimum!!)}")
        }
        if (bornes.maximum != null) {
            if (isNotEmpty()) append(", ")
            append("max ${formate(bornes.maximum!!)}")
        }
        if (isEmpty()) append("bornes par défaut")
    }
}

/**
 * **ResourceType** : une ressource `R.<famille>.x` (ou un nom annoté
 * `@<famille>Res` du fichier) passée à un paramètre annoté
 * `@<autre>Res`. Seul le MISMATCH PROUVABLE est signalé — un entier
 * ordinaire n'est jamais signalé (règle de silence honnête : mieux vaut
 * un faux négatif qu'un faux positif au fil de l'eau).
 */
public object ControleResourceType : InspectionMoteur {
    override val id: String = "ResourceType"
    override val libelle: String = "Famille de ressource inattendue pour @…Res"
    override val groupe: String = "Correctitude Android"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT
    override val provenance: String get() = SOURCE_LINT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        if (contexte.erreursEcj.isNotEmpty()) return
        val arbre = contexte.arbre ?: return

        // Pré-passe : familles annotées des CHAMPS et des paramètres du
        // fichier + annotations des paramètres par identité de binding.
        val famillesChamps = mutableMapOf<String, String>()
        val famillesParametres = mutableMapOf<String, String>()
        val parametres = IdentityHashMap<MethodBinding, Array<List<Annotation>>>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteChamp(node: org.eclipse.jdt.internal.compiler.ast.FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
                familleResDe(node.annotations)?.let { famillesChamps[String(node.name)] = it }
                return true
            }

            override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                runCatching {
                    val liaison = node.binding
                    if (liaison != null) {
                        parametres[liaison] = node.arguments?.map { it.annotations?.toList() ?: emptyList() }?.toTypedArray() ?: emptyArray()
                    }
                    for (argument in node.arguments ?: emptyArray()) {
                        familleResDe(argument.annotations)?.let { famillesParametres.putIfAbsent(String(argument.name), it) }
                    }
                }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)

        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                val arguments = node.arguments ?: return true
                val liaison = runCatching { node.binding }.getOrNull()
                for (index in arguments.indices) {
                    val attendue = familleAttendue(liaison, index, parametres) ?: continue
                    val reelle = familleReelle(arguments[index], famillesChamps, famillesParametres) ?: continue
                    if (reelle != "any" && reelle != attendue) {
                        rapporteur.signaler(
                            Plage(arguments[index].sourceStart, arguments[index].sourceEnd + 1),
                            "La ressource est de type $reelle mais le paramètre ${index + 1} de « ${String(node.selector)} » attend @${attendue.replaceFirstChar { it.uppercase() }}Res",
                        )
                    }
                }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, visiteur)
    }

    /** La famille attendue d'un paramètre (binding local par identité, puis binaire). */
    private fun familleAttendue(liaison: MethodBinding?, index: Int, parametres: IdentityHashMap<MethodBinding, Array<List<Annotation>>>): String? {
        if (liaison != null) {
            for (annotation in parametres[liaison]?.getOrNull(index) ?: emptyList()) {
                familleResDe(annotation)?.let { return it }
            }
        }
        val binaires = runCatching {
            liaison?.getParameterAnnotations()?.getOrNull(index)?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
        for (annotation in binaires) {
            familleResDe(annotation)?.let { return it }
        }
        return null
    }

    /** La famille PROUVÉE d'un argument (sinon null : silence honnête). */
    private fun familleReelle(expression: Expression, champs: Map<String, String>, parametres: Map<String, String>): String? {
        // R.<famille>.x : le DÉCLARANT du champ est la classe imbriquée
        // « …R.<famille> » — l'avant-dernier segment doit être « R ».
        if (expression is org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference) {
            val champ = runCatching { expression.binding }.getOrNull() as? org.eclipse.jdt.internal.compiler.lookup.FieldBinding ?: return null
            val declarant = runCatching { String(champ.declaringClass.readableName()) }.getOrNull() ?: return null
            val segments = declarant.split('.')
            if (segments.size >= 2 && segments[segments.size - 2] == "R") {
                return segments.last().lowercase()
            }
            return null
        }
        // Un nom simple : champ ou paramètre ANNOTÉ du fichier.
        if (expression is SingleNameReference) {
            val nom = String(expression.token)
            return parametres[nom] ?: champs[nom]
        }
        return null
    }

    /** La famille d'une annotation @…Res (AST) — null si aucune. */
    internal fun familleResDe(annotations: Array<Annotation>?): String? {
        for (annotation in annotations ?: emptyArray()) {
            familleResDe(annotation)?.let { return it }
        }
        return null
    }

    /** La famille d'UNE annotation @…Res (AST) — null si aucune. */
    internal fun familleResDe(annotation: Annotation): String? {
        val nom = runCatching { String(annotation.type.getLastToken()) }.getOrNull() ?: return null
        return familleSimpleDe(nom)
    }

    /** La famille d'une annotation @…Res BINAIRE (binding du .class). */
    internal fun familleResDe(annotation: AnnotationBinding): String? {
        val nom = runCatching { String(annotation.getAnnotationType()?.readableName() ?: return null) }.getOrNull() ?: return null
        return familleSimpleDe(nom.substringAfterLast('.'))
    }

    private fun familleSimpleDe(simple: String): String? = when {
        simple == "AnyRes" -> "any"
        simple.length > 3 && simple.endsWith("Res") -> simple.removeSuffix("Res").lowercase()
        else -> null
    }
}

/**
 * Le socle commun de [ControleCommitPrefEdits] et [ControleRecycle] :
 * pistage des LOCALES par méthode, sur UNE traversée à piles vivantes
 * (méthodes, envois, déclarations) — ecj ne pose pas de lien parent sur
 * ses nœuds, la traversée le fournit.
 *
 * La direction conservatrice est la règle : toute ÉVASION du nom (argument
 * d'appel, retour, opérande, réaffectation) rend le verdict SILENCIEUX ;
 * un geste terminal (`commit`/`apply`/`recycle`) sur le nom, où qu'il vive
 * dans la méthode, acquitte. Les occurrences d'un nom hors de la méthode
 * porteuse ne comptent jamais (pistage par plage).
 */
private class PisteurLocales(
    /** Vrai ssi l'envoi OBTIENT la ressource pistée (edit()/obtain…()). */
    private val estOrigine: (MessageSend) -> Boolean,
    /** Les sélecteurs qui acquittent la piste. */
    private val terminaux: Set<String>,
) {

    /** Une piste : nom, plage de la méthode porteuse, appel d'origine, exemptions. */
    private data class Piste(
        val nom: String,
        val plageMethode: IntRange,
        val origine: MessageSend,
        val declaration: LocalDeclaration?,
        var acquittee: Boolean = false,
        var evasive: Boolean = false,
    )

    /** Les pistes ouvertes. */
    private val pistes = mutableListOf<Piste>()

    /** Les références simples du fichier + les récepteurs d'envois. */
    private val references = mutableListOf<SingleNameReference>()
    private val recepteurs = IdentityHashMap<SingleNameReference, MessageSend>()

    /** Les déclarations ressources des try-with-resources (exemptées au verdict). */
    private val exemptes = mutableListOf<LocalDeclaration>()

    /** Les piles vivantes de la traversée. */
    private val pileMethodes = ArrayDeque<org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration>()
    private val pileEnvois = ArrayDeque<MessageSend>()
    private val pileLocales = ArrayDeque<LocalDeclaration>()

    /** Examine l'unité et signale les pistes jamais acquittées. */
    fun examiner(arbre: org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration?, signaler: (MessageSend) -> Unit) {
        if (arbre == null) return
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                pileMethodes.addLast(node)
                return true
            }

            override fun finMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?) {
                pileMethodes.removeLastOrNull()
            }

            override fun visiteConstructeur(node: org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                pileMethodes.addLast(node)
                return true
            }

            override fun finConstructeur(node: org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?) {
                pileMethodes.removeLastOrNull()
            }

            override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                pileLocales.addLast(node)
                return true
            }

            override fun endVisit(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?) {
                runCatching { pileLocales.removeLastOrNull() }
            }

            override fun visiteAffectation(node: Assignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                // Une affectation `x = …` sur un nom PISTÉ rend le flot trop
                // riche (l'éditeur/la ressource peut être remplacée) : évasion.
                val cible = node.lhs as? SingleNameReference
                if (cible != null) {
                    pistes.filter {
                        it.nom == String(cible.token) && node.sourceStart in it.plageMethode
                    }.forEach { it.evasive = true }
                }
                return true
            }

            override fun visiteRetour(node: org.eclipse.jdt.internal.compiler.ast.ReturnStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                // Un retour sur un nom PISTÉ : la ressource s'évade.
                (node.expression as? SingleNameReference)?.let { cible ->
                    pistes.filter {
                        it.nom == String(cible.token) && node.sourceStart in it.plageMethode
                    }.forEach { it.evasive = true }
                }
                return true
            }

            override fun visit(node: TryStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                runCatching {
                    // Exemption try-with-resources (ADR 0024) : les
                    // déclarations ressources sont EXEMPTÉES au verdict — la
                    // visite du try précède celle des ressources.
                    for (ressource in node.resources ?: emptyArray()) {
                        (ressource as? LocalDeclaration)?.let { exemptes.add(it) }
                    }
                }
                return true
            }

            override fun visit(node: SingleNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                runCatching { references.add(node) }
                return true
            }

            override fun visit(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                runCatching {
                    (node.receiver as? SingleNameReference)?.let { recepteurs[it] = node }
                    pileEnvois.addLast(node)
                    examinerOrigine(node)
                }
                return true
            }

            override fun endVisit(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?) {
                runCatching { pileEnvois.removeLastOrNull() }
            }
        }
        LectureAst.traverserResilient(arbre, visiteur)

        // Verdicts (après la traversée complète : tous les gestes sont connus).
        for (piste in pistes) {
            if (piste.acquittee || piste.evasive) continue
            if (piste.declaration != null && exemptes.any { it === piste.declaration }) continue
            val terminal = recepteurs.values.any { envoi ->
                envoi.sourceStart in piste.plageMethode &&
                    (envoi.receiver as? SingleNameReference)?.let { String(it.token) } == piste.nom &&
                    String(envoi.selector) in terminaux
            }
            if (terminal) continue
            // Évasion : le nom apparaît dans la méthode hors position de
            // récepteur (argument, retour, opérande, affectation).
            val evasion = references.any { reference ->
                reference.sourceStart in piste.plageMethode &&
                    String(reference.token) == piste.nom && reference !in recepteurs
            }
            if (evasion) continue
            signaler(piste.origine)
        }
    }

    /** Ouvre une piste : l'initialisateur d'une locale est un appel d'origine. */
    private fun examinerOrigine(envoi: MessageSend) {
        if (!estOrigine(envoi)) return
        val locale = pileLocales.lastOrNull() ?: return
        if (locale.initialization !== envoi) return
        val methode = pileMethodes.lastOrNull() ?: return
        pistes.add(Piste(String(locale.name), methode.declarationSourceStart..methode.declarationSourceEnd, envoi, locale))
    }
}

/**
 * **CommitPrefEdits** : `SharedPreferences.edit()` dont l'éditeur n'est
 * JAMAIS scellé par `commit()`/`apply()` — les écritures préparées ne
 * seront pas persistées. Pistage des locales (`Editor e = prefs.edit();`)
 * ET des chaînes nues (`prefs.edit().putX(…);` sans terminaison) ;
 * l'éditeur capturé hors locale marquée, passé en argument ou retourné
 * rend le verdict silencieux.
 */
public object ControleCommitPrefEdits : InspectionMoteur {
    override val id: String = "CommitPrefEdits"
    override val libelle: String = "SharedPreferences.edit() sans commit()/apply()"
    override val groupe: String = "Correctitude Android"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT
    override val provenance: String get() = SOURCE_LINT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        if (contexte.erreursEcj.isNotEmpty()) return
        val arbre = contexte.arbre ?: return
        val pisteur = PisteurLocales(
            estOrigine = ::estEdition,
            terminaux = setOf("commit", "apply"),
        )
        pisteur.examiner(arbre) { origine ->
            rapporteur.signaler(
                Plage(origine.sourceStart, origine.sourceEnd + 1),
                "« edit() » sans commit()/apply() — les modifications ne seront jamais écrites",
            )
        }
        // Les chaînes NUES : prefs.edit().putX(…) ; — l'appel externe de la
        // chaîne de récepteurs n'est pas un terminal. Passe dédiée (les
        // piles du pisteur ne traversent pas les deux contrôles à la fois).
        val nus = object : LectureAst.VisiteurResilient() {
            private val pileEnvois = ArrayDeque<MessageSend>()
            private val pileLocales = ArrayDeque<LocalDeclaration>()
            private val pileAffectations = ArrayDeque<Assignment>()
            private val pileRetours = ArrayDeque<org.eclipse.jdt.internal.compiler.ast.ReturnStatement>()

            override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                pileLocales.addLast(node)
                return true
            }

            override fun endVisit(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?) {
                runCatching { pileLocales.removeLastOrNull() }
            }

            override fun visiteAffectation(node: Assignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                pileAffectations.addLast(node)
                return true
            }

            override fun endVisit(node: Assignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?) {
                runCatching { pileAffectations.removeLastOrNull() }
            }

            override fun visiteRetour(node: org.eclipse.jdt.internal.compiler.ast.ReturnStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                pileRetours.addLast(node)
                return true
            }

            override fun endVisit(node: org.eclipse.jdt.internal.compiler.ast.ReturnStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?) {
                runCatching { pileRetours.removeLastOrNull() }
            }

            override fun visit(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                runCatching {
                    if (estEdition(node)) jugerChaineNue(node)
                    pileEnvois.addLast(node)
                }
                return true
            }

            override fun endVisit(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?) {
                runCatching { pileEnvois.removeLastOrNull() }
            }

            /** Juge une édition : chaîne nue non scellée → signalement. */
            private fun jugerChaineNue(edition: MessageSend) {
                // Captée par une locale ? → pisteur.
                val locale = pileLocales.lastOrNull()
                if (locale != null && locale.initialization === edition) return
                // Dans une affectation (membre droit, même imbriqué) ? → le
                // résultat s'évade vers la cible : silence.
                if (pileAffectations.isNotEmpty()) return
                // Retournée (même imbriquée) ? → évasion.
                if (pileRetours.isNotEmpty()) return
                // La chaîne de récepteurs : l'APPEL EXTERNE décide. En pile,
                // les ancêtres sont [externe, …, parent].
                var externe = edition
                for (index in pileEnvois.indices.reversed()) {
                    val ancetre = pileEnvois[index]
                    if (ancetre.receiver === externe) {
                        externe = ancetre
                    } else {
                        break
                    }
                }
                // La chaîne s'évade dans un argument d'appel ? → silence.
                if (pileEnvois.any { it.arguments?.contains(externe) == true }) return
                if (String(externe.selector) == "commit" || String(externe.selector) == "apply") return
                rapporteur.signaler(
                    Plage(edition.sourceStart, edition.sourceEnd + 1),
                    "« edit() » sans commit()/apply() — les modifications ne seront jamais écrites",
                )
            }
        }
        LectureAst.traverserResilient(arbre, nus)
    }

    /** Vrai ssi l'envoi est `X.edit()` sur un SharedPreferences (type du déclarant). */
    private fun estEdition(envoi: MessageSend): Boolean {
        if (String(envoi.selector) != "edit") return false
        val declarant = runCatching { String(envoi.binding.declaringClass.readableName()) }.getOrNull() ?: return false
        val simple = declarant.substringAfterLast('.')
        return simple == "SharedPreferences" || simple == "SharedPreferencesEditor" || declarant.endsWith("SharedPreferences")
    }
}

/**
 * **Recycle** : ressource OBTENUE (`TypedArray` par
 * `obtainStyledAttributes`/`obtainTypedAttributes`, `VelocityTracker` par
 * `obtain`) jamais restituée par `recycle()`. Pistage intra-méthode des
 * locales ; try-with-resources et évasions exemptées (silence conservateur).
 */
public object ControleRecycle : InspectionMoteur {
    override val id: String = "Recycle"
    override val libelle: String = "recycle() manquant sur la ressource obtenue"
    override val groupe: String = "Correctitude Android"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT
    override val provenance: String get() = SOURCE_LINT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        if (contexte.erreursEcj.isNotEmpty()) return
        val arbre = contexte.arbre ?: return
        val pisteur = PisteurLocales(
            estOrigine = ::siObtenue,
            terminaux = setOf("recycle"),
        )
        pisteur.examiner(arbre) { origine ->
            rapporteur.signaler(
                Plage(origine.sourceStart, origine.sourceEnd + 1),
                "Ressource obtenue sans recycle() — restituer le TypedArray/VelocityTracker (recycle())",
            )
        }
    }

    /** Vrai ssi l'appel OBTIENT une ressource à recycler (typage du RETOUR). */
    private fun siObtenue(envoi: MessageSend): Boolean {
        val selecteur = String(envoi.selector)
        if (selecteur != "obtainStyledAttributes" && selecteur != "obtainTypedAttributes" && selecteur != "obtain") return false
        val retour = runCatching { String(envoi.binding.returnType?.readableName() ?: return false) }.getOrNull() ?: return false
        val simple = retour.substringAfterLast('.')
        return simple == "TypedArray" || simple == "VelocityTracker"
    }
}

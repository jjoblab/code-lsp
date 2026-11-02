package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.LectureAst.VisiteurResilient
import jo.codeide.lsp.java.interne.PontEcj
import jo.codeide.lsp.java.interne.UnitesSource
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.Annotation
import org.eclipse.jdt.internal.compiler.ast.Argument
import org.eclipse.jdt.internal.compiler.ast.ArrayInitializer
import org.eclipse.jdt.internal.compiler.ast.ASTNode
import org.eclipse.jdt.internal.compiler.ast.Assignment
import org.eclipse.jdt.internal.compiler.ast.CastExpression
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.ConditionalExpression
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.NormalAnnotation
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement
import org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.SwitchStatement
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.ast.TypeReference
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding

/**
 * Le TYPE ATTENDU du contexte de complétion (vague V-C3 — ADR 0026 (a),
 * fiches C-03/C-04/C-08, écart G-012).
 *
 * Le calcul est DÉLÉGUÉ aux bindings ecj (A7 — « aucune réimplémentation
 * d'inférence ») : l'expression englobante du caret (affectation, argument
 * d'appel, `return`, ternaire, lambda) donne le type attendu par SES PROPRES
 * liaisons — membre gauche d'affectation, paramètre formel de l'appel
 * résolu, type de retour de la méthode englobante. Le filtrage qui en
 * découle est **gradué** : les candidats type-compatibles REMONTENT, les
 * autres DESCENDENT, **rien n'est jamais caché** (fiche C-03 S5 — la liste
 * plafonnée honnête et le filtrage par préfixe restent intacts).
 *
 * Le même calcul porte les annotations Android de la position (ADR 0026
 * (e)/(f), écart G-017) :
 *  - **typedef** `@IntDef({...})`/`@StringDef({...})` sur le paramètre ou le
 *    champ visé → les constantes AUTORISÉES voyagent avec le contexte
 *    ([TypedefAttendu]) et sont proposées EN TÊTE (fiche C-04) ;
 *  - **ressource** `@StringRes` & co. sur le paramètre visé → le type de
 *    ressource attendu (complétion des littéraux de chaîne, B11).
 *
 * Les noms des constantes typedef sont lus dans la SOURCE DÉCLARANTE de
 * l'annotation (les valeurs d'un `@IntDef({A, B})` sont des références de
 * compilation : le fichier `.class` ne porte que leurs VALEURS, pas leurs
 * NOMS — même contrainte que la référence, qui lit l'annotation à sa source).
 * Bornes honnêtes : une annotation déclarée dans un type BINAIRE (androidx
 * d'un AAR) ne livre pas ses noms — silence, jamais d'invention.
 *
 * Cette objet ne lève jamais : tout binding fautif rend `null`/vide.
 */
public object TypeAttendu {

    /**
     * Les constantes autorisées d'un typedef `@IntDef`/`@StringDef` (fiche
     * C-04).
     *
     * @property constantes les noms des constantes listées dans l'annotation
     *   (`MODE_ON`, `MODE_OFF`), dans l'ordre de déclaration.
     * @property typeDeclarent le FQCN du type qui DÉCLARE les constantes —
     *   l'origine de l'import statique à l'insertion quand le nom simple
     *   n'est pas visible (T1 : « proposées même si non importées »).
     */
    public class TypedefAttendu(
        public val constantes: List<ConstanteTypedef>,
        public val typeDeclarent: String,
    )

    /**
     * Le verdict d'attente d'un contexte : l'UNION des types attendus (fiche
     * C-03 S2 : surcharges — l'union des types des paramètres à cet indice)
     * plus les annotations Android portées par la position.
     *
     * @property types l'union des types attendus (vide = pas de filtrage
     *   smart — contexte non calculable, dégradation honnête).
     * @property typedef le typedef du paramètre/champ visé (null = aucun).
     * @property ressource le type de RESSOURCE attendu (`"string"`,
     *   `"drawable"`…) quand la position est annotée `@…Res` (null = aucun).
     */
    public class Attendu(
        public val types: List<TypeBinding>,
        public val typedef: TypedefAttendu?,
        public val ressource: String?,
    ) {
        public companion object {
            /** Le verdict vide : pas d'attente calculable (repli honnête). */
            public val AUCUN: Attendu = Attendu(emptyList(), null, null)
        }
    }

    /** Annotation typedef portant des constantes `int` (fiche C-04 T1). */
    private const val INT_DEF: String = "IntDef"

    /** Annotation typedef portant des constantes `String` (fiche C-04 T2). */
    private const val STRING_DEF: String = "StringDef"

    /** Simple nom d'une annotation de type `@…Res` → type de ressource (B11). */
    private val ANNOTATIONS_RESSOURCES: Map<String, String> = mapOf(
        "StringRes" to "string",
        "DrawableRes" to "drawable",
        "ColorRes" to "color",
        "LayoutRes" to "layout",
        "IdRes" to "id",
        "IntegerRes" to "integer",
        "BoolRes" to "bool",
        "DimenRes" to "dimen",
        "ArrayRes" to "array",
        "StyleRes" to "style",
        "StyleableRes" to "styleable",
        "AttrRes" to "attr",
        "AnimRes" to "anim",
        "AnimatorRes" to "animator",
        "FontRes" to "font",
        "MenuRes" to "menu",
        "PluralsRes" to "plurals",
        "RawRes" to "raw",
        "TransitionRes" to "transition",
        "XmlRes" to "xml",
        "NavigationRes" to "navigation",
    )

    /** Union maximale de types attendus (surcharges — fiche C-03 S2). */
    private const val MAX_UNION: Int = 6

    // ------------------------------------------------------------------ //
    // Compatibilité au type attendu (fiche C-03 S5 — gradué, jamais caché)
    // ------------------------------------------------------------------ //

    /**
     * La compatibilité d'un type candidat à l'union des types attendus.
     *
     * @param candidat le type RÉSOLU de l'expression candidate (null = type
     *   inconnu — l'item reste neutre).
     * @param attendus l'union des types attendus (vide = tout neutre).
     * @return 0 = compatible (remonte), 1 = inconnu (neutre), 2 = incompatible
     *   (descend — JAMAIS caché).
     */
    public fun compatibilite(candidat: TypeBinding?, attendus: List<TypeBinding>): Int {
        if (attendus.isEmpty() || candidat == null) return 1
        if (estVide(candidat)) return 2 // void : illégal en position d'expression
        for (attendu in attendus) {
            if (attendu == null) continue
            if (estAssignable(candidat, attendu)) return 0
        }
        return 2
    }

    /** L'épreuve d'assignabilité JLS par les bindings ecj (A7 — jamais réimplémentée). */
    private fun estAssignable(candidat: TypeBinding, attendu: TypeBinding): Boolean {
        if (candidat === attendu) return true
        return try {
            candidat.isCompatibleWith(attendu)
        } catch (_: Exception) {
            // bindings incomplets (recovery) : neutre serait mensonger vers le
            // haut — l'échec de l'épreuve laisse l'item en bas, jamais caché.
            false
        }
    }

    /** Vrai ssi le binding est `void` (ou problématique assimilé vide). */
    private fun estVide(type: TypeBinding?): Boolean = try {
        type == TypeBinding.VOID || "void" == type?.readableName()?.let { String(it) }
    } catch (_: Exception) {
        false
    }

    // ------------------------------------------------------------------ //
    // Calcul du type attendu — chemin d'ancêtres (source marquée)
    // ------------------------------------------------------------------ //

    /**
     * Le type attendu depuis le CHEMIN d'ancêtres du marqueur (classification
     * marquée — la liste va du plus intérieur au plus extérieur, tous les
     * nœuds couvrent la position).
     *
     * Règles (fiche C-03) :
     *  - S1 affectation : type du membre gauche (`=`) ou du type déclaré
     *    (initialiseur de locale/champ) ;
     *  - S2 argument d'appel : union des types des paramètres à cet indice
     *    sur les surcharges du sélecteur ;
     *  - S3 `return` : type de retour de la méthode englobante ;
     *  - S4 ternaire : TRANSPARENT (l'attente est celle du contexte externe) ;
     *    lambda : type de retour de la méthode fonctionnelle de l'interface
     *    attendue en aval ;
     *  - cast : type du cast (l'expression castée vise ce type).
     *
     * @param chemin les ancêtres couvrant le caret, du plus intérieur au plus
     *   extérieur.
     * @param caret la position interrogée (dans le système de la source marquée).
     * @return le verdict (jamais nul — [Attendu.AUCUN] en repli).
     */
    public fun depuisChemin(chemin: List<ASTNode>, caret: Int): Attendu {
        // Du PLUS INTÉRIEUR au plus extérieur : le porteur le plus PROCHE du
        // caret gagne — une lambda fonctionnalise l'attente de SON contexte
        // externe (S4), un initialiseur de déclaration ne la court-circuite pas.
        for (indice in chemin.indices.reversed()) {
            val node = chemin[indice]
            val verdict = when (node) {
                is ConditionalExpression -> null // S4 : transparent

                is Assignment -> siCouvre(node.expression, caret) {
                    attenduDeExpression(node.lhs)
                }

                is LocalDeclaration -> siCouvre(node.initialization, caret) {
                    Attendu(listOfNotNull(typeDeDeclaration(node)), null, null)
                }

                is FieldDeclaration -> siCouvre(node.initialization, caret) {
                    Attendu(listOfNotNull(typeDeDeclarationChamp(node)), null, null)
                }

                is ReturnStatement -> siCouvre(node.expression, caret) {
                    val methode = chemin.subList(0, indice + 1)
                        .filterIsInstance<MethodDeclaration>()
                        .lastOrNull()
                    val retour = try {
                        methode?.binding?.returnType
                    } catch (_: Exception) {
                        null
                    }
                    Attendu(listOfNotNull(retour), null, null)
                }

                is MessageSend -> {
                    val index = indexArgument(node, caret)
                    if (index >= 0) attenduDAppel(node, index) else null
                }

                is AllocationExpression -> {
                    val index = indexArgumentAllocation(node, caret)
                    if (index >= 0) {
                        val retour = try {
                            node.binding?.parameters?.getOrNull(index)
                        } catch (_: Exception) {
                            null
                        }
                        Attendu(listOfNotNull(retour), null, null)
                    } else {
                        null
                    }
                }

                is CastExpression -> siCouvre(node.expression, caret) {
                    Attendu(listOfNotNull(typeReference(node.type)), null, null)
                }

                is org.eclipse.jdt.internal.compiler.ast.LambdaExpression -> {
                    // S4 lambda : l'attente EXTERNE (affectation/argument)
                    // fonctionnalisée — la méthode abstraite unique de
                    // l'interface attendue rend son type de retour. La liste
                    // va du plus EXTÉRIEUR au plus intérieur : le contexte
                    // externe vit AVANT la lambda (0 jusqu'à indice).
                    val externe = depuisChemin(chemin.subList(0, indice), caret)
                    Attendu(externe.types.mapNotNull(::retourFonctionnel), null, null)
                }

                else -> null
            }
            if (verdict != null) return verdict
        }
        return Attendu.AUCUN
    }

    /** Le verdict si l'expression couvre le caret, sinon null (gardien). */
    private inline fun siCouvre(expression: Expression?, caret: Int, corps: () -> Attendu?): Attendu? {
        if (expression == null) return null
        val debut = try {
            expression.sourceStart
        } catch (_: Exception) {
            -1
        }
        val fin = try {
            expression.sourceEnd
        } catch (_: Exception) {
            -1
        }
        if (debut < 0 || caret < debut || caret > fin + 1) return null
        return corps() ?: Attendu.AUCUN
    }

    /** Index de l'argument couvrant le caret d'un envoi (−1 si aucun). */
    private fun indexArgument(node: MessageSend, caret: Int): Int {
        val arguments = try {
            node.arguments
        } catch (_: Exception) {
            null
        } ?: return -1
        for ((index, argument) in arguments.withIndex()) {
            if (couvre(argument, caret)) return index
        }
        return -1
    }

    /** Index de l'argument couvrant le caret d'une allocation (−1 si aucun). */
    private fun indexArgumentAllocation(node: AllocationExpression, caret: Int): Int {
        val arguments = try {
            node.arguments
        } catch (_: Exception) {
            null
        } ?: return -1
        for ((index, argument) in arguments.withIndex()) {
            if (couvre(argument, caret)) return index
        }
        return -1
    }

    /** Vrai ssi un nœud couvre la position. */
    private fun couvre(node: ASTNode?, caret: Int): Boolean {
        if (node == null) return false
        return try {
            node.sourceStart in 0..caret && node.sourceEnd + 1 >= caret
        } catch (_: Exception) {
            false
        }
    }

    /** Le type attendu d'une affectation depuis son membre GAUCHE résolu. */
    private fun attenduDeExpression(lhs: Expression?): Attendu {
        val type = try {
            lhs?.resolvedType
        } catch (_: Exception) {
            null
        } ?: try {
            (lhs as? SingleNameReference)?.binding as? VariableBinding
        } catch (_: Exception) {
            null
        }?.type
        return Attendu(listOfNotNull(type), null, null)
    }

    /** Le type déclaré d'une locale (binding d'abord, référence ensuite). */
    private fun typeDeDeclaration(node: LocalDeclaration): TypeBinding? = try {
        node.binding?.type ?: node.type?.resolvedType
    } catch (_: Exception) {
        try {
            node.type?.resolvedType
        } catch (_2: Exception) {
            null
        }
    }

    /** Le type déclaré d'un champ (binding d'abord, référence ensuite). */
    private fun typeDeDeclarationChamp(node: FieldDeclaration): TypeBinding? = try {
        node.binding?.type ?: node.type?.resolvedType
    } catch (_: Exception) {
        try {
            node.type?.resolvedType
        } catch (_2: Exception) {
            null
        }
    }

    /** Le type résolu d'une référence de type. */
    private fun typeReference(reference: TypeReference?): TypeBinding? = try {
        reference?.resolvedType
    } catch (_: Exception) {
        null
    }

    /**
     * L'UNION des types attendus d'un argument d'appel (fiche C-03 S2) : le
     * paramètre à cet indice du binding RÉSOLU, puis les surcharges du
     * sélecteur sur la hiérarchie du récepteur (même arité, même indice).
     *
     * @param node l'envoi résolu.
     * @param index l'indice de l'argument en cours de frappe.
     * @return le verdict (union bornée à [MAX_UNION] types).
     */
    private fun attenduDAppel(node: MessageSend, index: Int): Attendu {
        val union = LinkedHashSet<String>()
        val types = mutableListOf<TypeBinding>()
        fun ajouter(type: TypeBinding?) {
            if (type == null || types.size >= MAX_UNION) return
            // Une VARIABLE DE TYPE (E, T…) n'est PAS un type attendu utile : sa
            // borne (Object) rend TOUT compatible et le filtrage devient muet.
            // La substitution générique (List<String> → add(String)) la remplace
            // déjà quand ecj l'a résolue — sinon on l'écarte (honnête).
            if (type is org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding) return
            val cle = try {
                String(type.readableName())
            } catch (_: Exception) {
                return
            }
            if (union.add(cle)) types.add(type)
        }
        try {
            ajouter(node.binding?.parameters?.getOrNull(index))
        } catch (_: Exception) {
            // binding fautif : l'union des surcharges prend le relais
        }
        val recepteur = try {
            node.actualReceiverType as? ReferenceBinding
        } catch (_: Exception) {
            null
        }
        val selecteur = try {
            String(node.selector)
        } catch (_: Exception) {
            ""
        }
        // ARITÉ (fiche C-03 S2) : l'union ne retient que les surcharges dont
        // le nombre de paramètres ACCORDE l'appel en frappe (`l.add(M)` = 1
        // argument : `add(int, E)` — l'INDEX — ne participe PAS). Sans
        // surcharge d'arité concordante, toutes les mêmes sélecteurs
        // participent (repli honnête).
        val arite = try {
            node.arguments?.size
        } catch (_: Exception) {
            null
        }
        if (recepteur != null && selecteur.isNotEmpty()) {
            val candidates = mutableListOf<MethodBinding>()
            for (typeCourant in hierarchieComplete(recepteur)) {
                for (methode in methodesDeclarees(typeCourant)) {
                    if (methode.isConstructor()) continue
                    if (String(methode.selector) != selecteur) continue
                    candidates.add(methode)
                }
            }
            val concordantes = if (arite != null) {
                candidates.filter { (it.parameters?.size ?: -1) == arite }
            } else {
                emptyList()
            }
            for (methode in if (concordantes.isNotEmpty()) concordantes else candidates) {
                ajouter(methode.parameters?.getOrNull(index))
            }
        }
        return Attendu(types, null, null)
    }

    /**
     * Le type de retour de la MÉTHODE FONCTIONNELLE d'un type attendu (fiche
     * C-03 S4 — lambda) : l'unique méthode abstraite non statique de
     * l'interface ; `void` rend null (rien d'attendu — honnête).
     */
    private fun retourFonctionnel(attendu: TypeBinding): TypeBinding? {
        val reference = attendu as? ReferenceBinding ?: return null
        if (!reference.isInterface) return null
        val abstraites = mutableListOf<MethodBinding>()
        for (typeCourant in hierarchieComplete(reference)) {
            for (methode in methodesDeclarees(typeCourant)) {
                if (methode.isStatic()) continue
                if (methode.isConstructor()) continue
                val abstraite = try {
                    methode.isAbstract()
                } catch (_: Exception) {
                    false
                }
                if (!abstraite) continue
                if (abstraites.none { String(it.selector) == String(methode.selector) && it.parameters?.size == methode.parameters?.size }) {
                    abstraites.add(methode)
                }
            }
        }
        if (abstraites.size != 1) return null // pas fonctionnelle (ou ambiguë) : silence
        val retour = abstraites[0].returnType ?: return null
        return if (estVide(retour)) null else retour
    }

    // ------------------------------------------------------------------ //
    // Calcul du type attendu — arbre ORIGINAL (sonde à chaud)
    // ------------------------------------------------------------------ //

    /**
     * Le type attendu depuis l'arbre ORIGINAL du cache (sonde à chaud —
     * même calcul que [depuisChemin], la chaîne d'ancêtres est collectée sur
     * l'arbre vivant par plages couvrantes).
     *
     * @param arbre l'arbre original résolu (offsets du système DE LA SOURCE
     *   le ayant produit — sur un arbre RÉDUIT le calcul peut manquer son
     *   ancêtre : dégradation honnête, pas de filtrage smart).
     * @param offset la position interrogée.
     * @return le verdict (jamais nul).
     */
    public fun depuisArbre(arbre: CompilationUnitDeclaration?, offset: Int): Attendu {
        if (arbre == null) return Attendu.AUCUN
        val collecteur = CheminAttendu(offset)
        LectureAst.traverserResilient(arbre, collecteur)
        return depuisChemin(collecteur.chemin, offset)
    }

    /** Collecteur de la chaîne d'ancêtres PORTEURS D'ATTENTE couvrant la position. */
    private class CheminAttendu(private val caret: Int) : VisiteurResilient() {
        val chemin = mutableListOf<ASTNode>()

        private fun considerer(node: ASTNode): Boolean {
            if (couvre(node, caret)) chemin.add(node)
            return true
        }

        override fun visiteAffectation(node: Assignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = considerer(node)

        override fun visiteAffectationComposee(node: org.eclipse.jdt.internal.compiler.ast.CompoundAssignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = considerer(node)

        override fun visiteRetour(node: org.eclipse.jdt.internal.compiler.ast.ReturnStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = considerer(node)

        override fun visiteConditionnelle(node: org.eclipse.jdt.internal.compiler.ast.ConditionalExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = considerer(node)

        override fun visiteCast(node: org.eclipse.jdt.internal.compiler.ast.CastExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = considerer(node)

        override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = considerer(node)

        override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean = considerer(node)

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = considerer(node)

        override fun visiteAllocation(node: AllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = considerer(node)

        override fun visiteLambda(node: org.eclipse.jdt.internal.compiler.ast.LambdaExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = considerer(node)

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean = considerer(node)
    }

    // NOTE de tolérance : la sonde lit l'arbre du CACHE DE PARSE — si l'entrée
    // vivait d'une résolution RÉDUITE (grands fichiers, SourceFocuser), les
    // plages ne correspondent plus aux offsets originaux et la chaîne
    // n'attrape pas son ancêtre : dégradation honnête (pas de filtrage
    // smart), la CASCADE de la source marquée capture toujours en repli.

    // ------------------------------------------------------------------ //
    // Hiérarchie et méthodes déclarées (partagé avec ServiceCompletion)
    // ------------------------------------------------------------------ //

    /**
     * Hiérarchie COMPLÈTE d'un type : lui-même, ses superclasses (jusqu'à
     * Object) et ses interfaces — du plus DÉRIVÉ au plus ancien, protégé
     * contre les cycles par garde d'identité (le recovery peut fabriquer des
     * hiérarchies bouclées).
     *
     * @param base le type de départ.
     * @return la hiérarchie (jamais vide — le type lui-même au minimum).
     */
    public fun hierarchieComplete(base: ReferenceBinding): List<ReferenceBinding> {
        val resultat = mutableListOf<ReferenceBinding>()
        val vus = java.util.IdentityHashMap<ReferenceBinding, Boolean>()
        fun visiter(type: ReferenceBinding?) {
            if (type == null || vus.containsKey(type)) return
            vus[type] = true
            resultat.add(type)
            try {
                visiter(type.superclass())
                for (interface_ in type.superInterfaces() ?: emptyArray()) {
                    visiter(interface_)
                }
            } catch (_: Exception) {
                // hiérarchie non connectée : le type seul suffit
            }
        }
        visiter(base)
        return resultat
    }

    /** Méthodes DÉCLARÉES d'un type (défensif — liaison incomplète tolérée). */
    public fun methodesDeclarees(type: ReferenceBinding): Array<MethodBinding> = try {
        type.availableMethods() ?: emptyArray()
    } catch (_: Exception) {
        emptyArray()
    }

    // ------------------------------------------------------------------ //
    // Annotations Android de la position (ADR 0026 (e)/(f) — G-017)
    // ------------------------------------------------------------------ //

    /**
     * Le type de RESSOURCE d'une annotation `@…Res` par son simple nom
     * (B11) — `StringRes` → `"string"`.
     *
     * @param nomSimple le simple nom de l'annotation (dernier segment).
     * @return le type de ressource, ou null si l'annotation n'est pas une
     *   annotation de ressource connue.
     */
    public fun typeRessource(nomSimple: String): String? = ANNOTATIONS_RESSOURCES[nomSimple]

    /** Vrai ssi le simple nom est une annotation typedef (fiche C-04). */
    public fun estTypedef(nomSimple: String): Boolean = nomSimple == INT_DEF || nomSimple == STRING_DEF

    /**
     * Les annotations d'un PARAMÈTRE de méthode par le binding (route
     * binaires : les annotations de paramètres voyagent dans le fichier de
     * classe quand le compilateur les y a écrites) — défensive : aucune
     * hypothèse sur la population des annotations de paramètres par ecj.
     *
     * @param methode le binding de la méthode appelée.
     * @param index l'indice du paramètre.
     * @return les simples noms des annotations du paramètre (vide si
     *   indisponibles).
     */
    public fun nomsAnnotationsParametre(methode: MethodBinding?, index: Int): List<String> {
        if (methode == null || index < 0) return emptyList()
        return try {
            val annotations = methode.getParameterAnnotations()?.getOrNull(index) ?: return emptyList()
            annotations.mapNotNull { annotation ->
                try {
                    val type = annotation.getAnnotationType() ?: return@mapNotNull null
                    String(type.readableName())
                } catch (_: Exception) {
                    null
                }?.substringAfterLast('.')
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Le TYPEDEF d'un paramètre : les constantes autorisées lues dans la
     * SOURCE DÉCLARANTE de l'annotation (fiche C-04 T1 — les noms ne vivent
     * pas dans le `.class`).
     *
     * @param pont le pont ecj (résolution de la source déclarante).
     * @param environnement l'environnement de noms (localisation des sources).
     * @param methode le binding de la méthode APPELÉE.
     * @param index l'indice du paramètre annoté.
     * @param arbreCourant l'arbre déjà résolu de l'unité COURANTE (la méthode
     *   déclarante peut y vivre — lecture sans nouvelle résolution).
     * @return le typedef, ou null (annotation absente, ou déclarante binaire —
     *   silence honnête).
     */
    public fun typedefDeParametre(
        pont: PontEcj,
        environnement: EnvironnementNoms,
        methode: MethodBinding?,
        index: Int,
        arbreCourant: CompilationUnitDeclaration?,
    ): TypedefAttendu? {
        val selecteur = try {
            methode?.selector?.let { String(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        val declarante = try {
            methode?.declaringClass
        } catch (_: Exception) {
            null
        } ?: return null
        val declaration = declarationDeMethode(pont, environnement, declarante, selecteur, index, arbreCourant)
            ?: return null
        return typedefDeDeclaration(declaration.first, declaration.second, index)
    }

    /**
     * Le TYPEDEF d'un champ (fiche C-04 T2 : affectation à un champ annoté).
     *
     * @param pont le pont ecj.
     * @param environnement l'environnement de noms.
     * @param champ le binding du champ affecté.
     * @param arbreCourant l'arbre déjà résolu de l'unité courante.
     * @return le typedef, ou null.
     */
    public fun typedefDeChamp(
        pont: PontEcj,
        environnement: EnvironnementNoms,
        champ: FieldBinding?,
        arbreCourant: CompilationUnitDeclaration?,
    ): TypedefAttendu? {
        val nom = try {
            champ?.name?.let { String(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        val declarante = try {
            champ?.declaringClass
        } catch (_: Exception) {
            null
        } ?: return null
        val paire = declarationDeType(pont, environnement, declarante, arbreCourant) ?: return null
        val (fqcnDeclarent, typeDeclare) = paire
        val champDeclare = champsDe(typeDeclare).firstOrNull { String(it.name) == nom } ?: return null
        val annotations = try {
            champDeclare.annotations ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        val annotation = annotations.firstOrNull { estTypedef(nomSimpleDe(it)) } ?: return null
        return TypedefAttendu(constantesDeAnnotation(annotation) ?: emptyList(), fqcnDeclarent)
    }

    /**
     * Le TYPEDEF du sélecteur d'un `switch` (fiche C-04 T3) : l'annotation
     * `@IntDef` de la locale/du champ sélecteur.
     *
     * @param interrupteur le switch résolu.
     * @param arbreCourant l'arbre de l'unité courante.
     * @return le typedef (constantes + type déclarant), ou null.
     */
    public fun typedefDeSwitch(
        interrupteur: SwitchStatement,
        arbreCourant: CompilationUnitDeclaration?,
    ): TypedefAttendu? {
        val expression = try {
            interrupteur.expression
        } catch (_: Exception) {
            null
        } ?: return null
        // Locale : la déclaration porte l'annotation (binding → déclaration).
        val locale = try {
            (expression as? SingleNameReference)?.binding as? org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
        } catch (_: Exception) {
            null
        }
        if (locale != null) {
            val declaration = try {
                locale.declaration
            } catch (_: Exception) {
                null
            }
            val annotations = try {
                declaration?.annotations ?: emptyArray()
            } catch (_: Exception) {
                emptyArray()
            }
            val annotation = annotations.firstOrNull { estTypedef(nomSimpleDe(it)) }
            if (annotation != null) {
                val porteur = typeEnglobantDe(declaration?.sourceStart ?: -1, arbreCourant)
                return TypedefAttendu(constantesDeAnnotation(annotation) ?: emptyList(), porteur ?: "")
            }
        }
        // Champ : la déclaration vit dans le type déclarant (route source).
        val champ = try {
            (expression as? SingleNameReference)?.binding as? FieldBinding
        } catch (_: Exception) {
            null
        }
        if (champ != null) {
            val nom = String(champ.name)
            val declarante = champ.declaringClass ?: return null
            val declaration = declarationDeType(null, null, declarante, arbreCourant) ?: return null
            val champDeclare = champsDe(declaration.second).firstOrNull { String(it.name) == nom } ?: return null
            val annotations = try {
                champDeclare.annotations ?: emptyArray()
            } catch (_: Exception) {
                emptyArray()
            }
            val annotation = annotations.firstOrNull { estTypedef(nomSimpleDe(it)) }
            if (annotation != null) {
                return TypedefAttendu(constantesDeAnnotation(annotation) ?: emptyList(), declaration.first)
            }
        }
        return null
    }

    /**
     * Le TYPE DE RESSOURCE attendu d'un argument annoté `@…Res` (B11) :
     * simple nom de l'annotation → type de ressource. La détection passe par
     * le BINDING (binaires couverts quand ecj expose les annotations de
     * paramètres du fichier de classe), puis par la source déclarante.
     *
     * @param pont le pont ecj.
     * @param environnement l'environnement de noms.
     * @param methode le binding de la méthode appelée.
     * @param index l'indice de l'argument.
     * @param arbreCourant l'arbre de l'unité courante.
     * @return le type de ressource (`"string"`…), ou null.
     */
    public fun ressourceDeParametre(
        pont: PontEcj,
        environnement: EnvironnementNoms,
        methode: MethodBinding?,
        index: Int,
        arbreCourant: CompilationUnitDeclaration?,
    ): String? {
        nomsAnnotationsParametre(methode, index).firstOrNull { typeRessource(it) != null }
            ?.let { return typeRessource(it) }
        val selecteur = try {
            methode?.selector?.let { String(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        val declarante = try {
            methode?.declaringClass
        } catch (_: Exception) {
            null
        } ?: return null
        val declaration = declarationDeMethode(pont, environnement, declarante, selecteur, index, arbreCourant)
            ?: return null
        val annotations = try {
            declaration.first.arguments?.getOrNull(index)?.annotations ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        return annotations.firstNotNullOfOrNull { typeRessource(nomSimpleDe(it)) }
    }

    // ------------------------------------------------------------------ //
    // Lecture des annotations dans les sources déclarantes
    // ------------------------------------------------------------------ //

    /**
     * Retrouve la DÉCLARATION d'une méthode par (classe déclarante, sélecteur,
     * indice de paramètre) : dans l'arbre COURANT si la classe y vit, sinon
     * par résolution de la source déclarante localisée par FQCN.
     *
     * @return la paire (arbre porteur, déclaration de méthode), ou null.
     */
    private fun declarationDeMethode(
        pont: PontEcj?,
        environnement: EnvironnementNoms?,
        declarante: ReferenceBinding,
        selecteur: String,
        index: Int,
        arbreCourant: CompilationUnitDeclaration?,
    ): Pair<MethodDeclaration, CompilationUnitDeclaration>? {
        for (arbre in listOfNotNull(arbreCourant, arbreResolue(pont, environnement, declarante))) {
            val declaration = chercherMethode(arbre, declarante, selecteur, index)
            if (declaration != null) return declaration to arbre
        }
        return null
    }

    /**
     * Retrouve la DÉCLARATION d'un type : dans l'arbre courant si possible,
     * sinon par résolution de la source localisée.
     *
     * @return la paire (FQCN déclarant, déclaration de type), ou null.
     */
    private fun declarationDeType(
        pont: PontEcj?,
        environnement: EnvironnementNoms?,
        declarante: ReferenceBinding,
        arbreCourant: CompilationUnitDeclaration?,
    ): Pair<String, TypeDeclaration>? {
        val fqcn = try {
            String(declarante.readableName())
        } catch (_: Exception) {
            return null
        }
        for (arbre in listOfNotNull(arbreCourant, arbreResolue(pont, environnement, declarante))) {
            val declaration = chercherType(arbre, declarante)
            if (declaration != null) return fqcn to declaration
        }
        return null
    }

    /** L'arbre RÉSOLU de la source déclarante d'un binding (null si binaire/introuvable). */
    private fun arbreResolue(
        pont: PontEcj?,
        environnement: EnvironnementNoms?,
        declarante: ReferenceBinding,
    ): CompilationUnitDeclaration? {
        val fqcn = try {
            String(declarante.readableName())
        } catch (_: Exception) {
            return null
        }
        val fichier = environnement?.trouverFqcn(fqcn) as? EnvironnementNoms.Localisation.Source
            ?: return null
        val chemin = fichier.fichier
        val texte = try {
            chemin.toFile().readText()
        } catch (_: Exception) {
            return null
        }
        val pontEffectif = pont ?: return null
        return try {
            pontEffectif.resoudre(UnitesSource.EnMemoire(chemin.toString(), texte), genererCode = false).arbre
        } catch (_: Exception) {
            null
        }
    }

    /** Cherche une méthode par sélecteur dans l'arbre — les candidates dont
     *  le paramètre PORT UNE ANNOTATION dominent (surcharges : l'annotée
     *  est celle qui porte le typedef/la ressource). */
    private fun chercherMethode(
        arbre: CompilationUnitDeclaration,
        declarante: ReferenceBinding,
        selecteur: String,
        index: Int,
    ): MethodDeclaration? {
        var annot: MethodDeclaration? = null
        var premiere: MethodDeclaration? = null
        LectureAst.traverserResilient(arbre, object : VisiteurResilient() {
            override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                if (String(node.selector) != selecteur) return true
                val arguments = node.arguments ?: return true
                if (index >= arguments.size) return true
                if (premiere == null) premiere = node
                val annotations = try {
                    arguments[index].annotations
                } catch (_: Exception) {
                    null
                }
                if (annotations?.isNotEmpty() == true && annot == null) annot = node
                return true
            }
        })
        return annot ?: premiere
    }

    /** Cherche la déclaration de type correspondant au binding déclarant. */
    private fun chercherType(arbre: CompilationUnitDeclaration, declarante: ReferenceBinding): TypeDeclaration? {
        var trouve: TypeDeclaration? = null
        val nomAttendu = try {
            String(declarante.sourceName())
        } catch (_: Exception) {
            return null
        }
        LectureAst.traverserResilient(arbre, object : VisiteurResilient() {
            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                if (trouve == null && String(node.name) == nomAttendu) trouve = node
                return true
            }
        })
        return trouve
    }

    /** Les champs déclarés d'un TypeDeclaration (défensif). */
    private fun champsDe(type: TypeDeclaration): Array<FieldDeclaration> = try {
        type.fields ?: emptyArray()
    } catch (_: Exception) {
        emptyArray()
    }

    /** Le FQCN du type englobant d'une position dans l'arbre (T3 : porteur des constantes). */
    private fun typeEnglobantDe(offset: Int, arbre: CompilationUnitDeclaration?): String {
        if (arbre == null || offset < 0) return ""
        var nom: String? = null
        LectureAst.traverserResilient(arbre, object : VisiteurResilient() {
            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                val couvre = try {
                    node.sourceStart in 0..offset && node.declarationSourceEnd + 1 >= offset
                } catch (_: Exception) {
                    false
                }
                if (couvre) {
                    try {
                        node.binding?.readableName()?.let { nom = String(it) }
                    } catch (_: Exception) {
                        nom = String(node.name)
                    }
                }
                return true
            }
        })
        return nom ?: ""
    }

    /** Le simple nom d'une annotation AST (dernier segment du type). */
    private fun nomSimpleDe(annotation: Annotation): String = try {
        val type = annotation.type ?: return ""
        when (type) {
            is org.eclipse.jdt.internal.compiler.ast.SingleTypeReference -> String(type.token)
            is org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference ->
                type.tokens.lastOrNull()?.let { String(it) } ?: ""
            else -> ""
        }
    } catch (_: Exception) {
        ""
    }

    /** Une constante du typedef : son NOM (affichage) et sa forme D'INSERTION. */
    public class ConstanteTypedef(
        public val nom: String,
        public val insertion: String,
    )

    /**
     * Les CONSTANTES listées dans une annotation typedef : l'attribut
     * `value` (forme `@IntDef({A, B})` — NormalAnnotation ou
     * SingleMemberAnnotation). Chaque élément porte son NOM (affichage) et
     * sa FORME D'INSERTION : le texte QUALIFIÉ tel qu'écrit dans
     * l'annotation (`Mode.MODE_ON` — compilable tel quel, P6) pour une
     * référence qualifiée, le nom SIMPLE sinon (l'import statique du type
     * déclarant voyage alors avec l'item).
     *
     * @param annotation l'annotation AST résolue.
     * @return les constantes (null si l'attribut n'est pas un tableau de noms).
     */
    private fun constantesDeAnnotation(annotation: Annotation): List<ConstanteTypedef>? {
        return try {
            val valeur: Expression? = when (annotation) {
                is NormalAnnotation -> annotation.memberValuePairs
                    ?.firstOrNull { String(it.name) == "value" }?.value
                is SingleMemberAnnotation -> annotation.memberValue
                else -> null
            }
            val init = valeur as? ArrayInitializer ?: return null
            init.expressions?.mapNotNull { element ->
                when (element) {
                    is SingleNameReference -> ConstanteTypedef(String(element.token), String(element.token))
                    is org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference -> {
                        val jetons = element.tokens ?: return@mapNotNull null
                        if (jetons.isEmpty()) return@mapNotNull null
                        ConstanteTypedef(String(jetons.last()), jetons.joinToString(".") { String(it) })
                    }
                    else -> null
                }
            }?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /** Le verdict typedef d'une DÉCLARATION de méthode trouvée (paramètre index). */
    private fun typedefDeDeclaration(
        declaration: MethodDeclaration,
        arbre: CompilationUnitDeclaration,
        index: Int,
    ): TypedefAttendu? {
        val annotations = try {
            declaration.arguments?.getOrNull(index)?.annotations ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        val annotation = annotations.firstOrNull { estTypedef(nomSimpleDe(it)) } ?: return null
        val constantes = constantesDeAnnotation(annotation) ?: emptyList()
        val porteur = typeEnglobantDe(declaration.sourceStart, arbre)
        return TypedefAttendu(constantes, porteur)
    }
}

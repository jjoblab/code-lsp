package jo.codeide.lsp.java.survol

import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.LectureAst.VisiteurResilient
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding

/**
 * Le survol RICHE servi au caret (étage N3 — cahier fiche 021, lot S2-a) :
 * la signature du symbole PLUS la documentation de sa déclaration, en deux
 * champs STRUCTURÉS (le client rend le markdown comme il l'entend).
 *
 * La documentation NULL (aucune javadoc) est distincte d'une documentation
 * vide — contrat explicite de la fiche : un symbole binaire (classpath, jrt)
 * ou une déclaration sans javadoc sert `documentation = null`, signature
 * seule.
 *
 * @property signature la signature lisible du symbole au caret (p. ex.
 *   `int somme(int, int)`, `int total`, `demo.Main`), jamais vide quand le
 *   survol est servi.
 * @property documentation la javadoc de la déclaration convertie en
 *   markdown ([NettoyeurJavadocMarkdown]), ou null — périmètre « honnête » :
 *   seules les déclarations de la MÊME unité de compilation apportent leur
 *   javadoc.
 */
public data class SurvolRiche(
    public val signature: String,
    public val documentation: String?,
)

/**
 * Service du survol riche (étage N3 — cahier fiches 021 et 020 partie
 * hover) : trouve le nœud le plus profond couvrant le caret, construit sa
 * signature, et retrouve la javadoc de sa DÉCLARATION via le registre des
 * déclarations de l'unité.
 *
 * Le registre rend l'ordre du fichier INDIFFÉRENT (fiche 021) : une
 * référence vue AVANT ou APRÈS sa déclaration la retrouve également — le
 * balayage de l'arbre est complet, pas séquentiel au texte.
 *
 * Le périmètre de la documentation est la MÊME unité : les symboles
 * binaires (jrt, jars) ne portent jamais de javadoc ici — le moteur ne
 * devine pas ce qu'il n'a pas lu (périmètre « honnête », R5).
 */
public object ServiceSurvolRiche {

    /**
     * Sert le survol riche au caret.
     *
     * @param arbre l'arbre RÉSOLU (bindings vivants).
     * @param source le texte source dans le système de l'arbre.
     * @param offset la position interrogée.
     * @return le survol (signature + documentation), ou null si aucun nœud
     *   résolu ne couvre l'offset.
     */
    public fun survol(arbre: CompilationUnitDeclaration?, source: String, offset: Int): SurvolRiche? {
        if (arbre == null) return null
        val registre = RegistreDeclarations()
        LectureAst.traverserResilient(arbre, registre)
        val collecteur = CollecteurSurvol(source, offset, registre)
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.meilleur
    }

    /**
     * Signature lisible d'une méthode : `retour selecteur(T1, T2)` — les
     * types au nom long (lisible), la signature du cahier 020 se contente
     * d'y retrouver le nom et les types.
     */
    internal fun signatureMethode(binding: MethodBinding): String? {
        val retour = LectureAst.lisible(binding.returnType) ?: return null
        val params = (binding.parameters ?: emptyArray()).joinToString(", ") { LectureAst.lisible(it) ?: "?" }
        return "$retour ${String(binding.selector)}($params)"
    }

    /**
     * Javadoc d'une déclaration : le texte brut du commentaire qui précède
     * ou commence la déclaration, nettoyé en markdown.
     *
     * Convention ecj vérifiée : `declarationSourceStart` INCLUT la javadoc
     * quand il y en a une (le marqueur d'ouverture commence exactement à
     * cet offset). Le repli balaye vers l'arrière — un espace blanc puis la
     * fermeture du commentaire signalent une javadoc adjacente.
     *
     * @param source le texte source de l'unité.
     * @param declarationSourceStart le début de plage de la déclaration.
     * @return la documentation markdown, ou null (pas de javadoc).
     */
    internal fun javadocDe(source: String, declarationSourceStart: Int): String? {
        if (declarationSourceStart < 0 || declarationSourceStart >= source.length) return null
        // Convention ecj vérifiée : declarationSourceStart INCLUT la javadoc
        // quand il y en a une — le marqueur d'ouverture (slash + deux
        // étoiles) commence exactement à cet offset.
        if (declarationSourceStart + 3 <= source.length &&
            source[declarationSourceStart] == '/' &&
            source[declarationSourceStart + 1] == '*' &&
            source[declarationSourceStart + 2] == '*'
        ) {
            var i = declarationSourceStart + 3
            while (i < source.length - 1) {
                if (source[i] == '*' && source[i + 1] == '/') {
                    return NettoyeurJavadocMarkdown.nettoyer(source.substring(declarationSourceStart, i + 2))
                }
                i++
            }
            return null
        }
        // Repli : la javadoc précède immédiatement la déclaration (plage
        // écj ne l'incluant pas) — blancs franchis vers l'arrière.
        var i = declarationSourceStart
        while (i > 0 && source[i - 1].isWhitespace()) i--
        if (i >= 2 && source[i - 2] == '*' && source[i - 1] == '/') {
            val ouverture = source.lastIndexOf("/*", i - 2)
            if (ouverture >= 0 && source.regionMatches(ouverture + 1, "**", 0, 2)) {
                return NettoyeurJavadocMarkdown.nettoyer(source.substring(ouverture, i))
            }
        }
        return null
    }

    /** Priorités de profondeur : le nœud le plus « serré » gagne le caret. */
    private const val PRIORITE_TYPE = 1
    private const val PRIORITE_DECLARATION = 2
    private const val PRIORITE_APPEL = 3
    private const val PRIORITE_REFERENCE = 4

    /**
     * Registre des déclarations de l'unité : binding → déclaration — la
     * javadoc d'une RÉFÉRENCE vit sur sa déclaration, où qu'elle soit dans
     * le fichier (fiche 021 : référence avant ou après déclaration).
     */
    private class RegistreDeclarations : VisiteurResilient() {
        val champs = HashMap<org.eclipse.jdt.internal.compiler.lookup.FieldBinding, FieldDeclaration>()
        val methodes = HashMap<org.eclipse.jdt.internal.compiler.lookup.MethodBinding, MethodDeclaration>()
        val types = HashMap<org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding, TypeDeclaration>()

        override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
            node.binding?.let { champs[it] = node }
            return true
        }

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            node.binding?.let { methodes[it] = node }
            return true
        }

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            node.binding?.let { types[it] = node }
            return true
        }
    }

    /** Collecteur du meilleur candidat de survol riche au caret. */
    private class CollecteurSurvol(
        private val source: String,
        private val offset: Int,
        private val registre: RegistreDeclarations,
    ) : VisiteurResilient() {
        var meilleur: SurvolRiche? = null
        private var prioriteMeilleur = -1

        override fun visiteReferenceSimple(node: SingleNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, candidatReference(String(node.token), node.binding), PRIORITE_REFERENCE)
            return true
        }

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val dernierFragment = node.tokens?.lastOrNull()?.let(::String) ?: ""
            considerer(node.sourceStart, node.sourceEnd, candidatReference(dernierFragment, node.binding), PRIORITE_REFERENCE)
            return true
        }

        override fun visiteReferenceChamp(node: FieldReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, candidatReference(String(node.token), node.binding), PRIORITE_REFERENCE)
            return true
        }

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val binding = node.binding as? MethodBinding
            val signature = binding?.let { signatureMethode(it) } ?: LectureAst.lisible(node.resolvedType)
            val documentation = binding?.let { documentationDeMethode(it) }
            considerer(node.sourceStart, node.sourceEnd, signature?.let { SurvolRiche(it, documentation) }, PRIORITE_APPEL)
            return true
        }

        override fun visiteAllocation(node: AllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val binding = node.binding as? MethodBinding
            val signature = if (binding != null && binding.isConstructor()) {
                val params = (binding.parameters ?: emptyArray()).joinToString(", ") { LectureAst.lisible(it) ?: "?" }
                "${LectureAst.lisible(binding.declaringClass) ?: "?"}($params)"
            } else {
                LectureAst.lisible(node.resolvedType)
            }
            val documentation = binding?.let { documentationDeMethode(it) }
            considerer(node.sourceStart, node.sourceEnd, signature?.let { SurvolRiche(it, documentation) }, PRIORITE_APPEL)
            return true
        }

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            val signature = node.binding?.let { signatureMethode(it) } ?: run {
                val params = (node.arguments ?: emptyArray()).joinToString(", ") { LectureAst.lisible(it.type?.resolvedType) ?: "?" }
                "${LectureAst.lisible(node.returnType?.resolvedType) ?: "?"} ${String(node.selector)}($params)"
            }
            considerer(
                node.sourceStart,
                node.declarationSourceEnd,
                SurvolRiche(signature, javadocDe(source, node.declarationSourceStart)),
                PRIORITE_DECLARATION,
            )
            return true
        }

        override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
            val type = LectureAst.lisible(node.binding?.type) ?: LectureAst.lisible(node.type?.resolvedType) ?: "?"
            considerer(
                node.sourceStart,
                node.declarationSourceEnd,
                SurvolRiche("$type ${String(node.name)}", javadocDe(source, node.declarationSourceStart)),
                PRIORITE_DECLARATION,
            )
            return true
        }

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(
                node.sourceStart,
                node.declarationSourceEnd,
                SurvolRiche(LectureAst.lisible(node.binding) ?: String(node.name), javadocDe(source, node.declarationSourceStart)),
                PRIORITE_TYPE,
            )
            return true
        }

        override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val type = LectureAst.lisible(node.binding?.type) ?: LectureAst.lisible(node.type?.resolvedType) ?: return true
            considerer(node.sourceStart, node.declarationSourceEnd, SurvolRiche("$type ${String(node.name)}", null), PRIORITE_DECLARATION)
            return true
        }

        override fun visiteLambda(node: LambdaExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val type = LectureAst.lisible(node.resolvedType)
            if (type != null) {
                considerer(node.sourceStart, node.sourceEnd, SurvolRiche(type, null), PRIORITE_APPEL)
            }
            return true
        }

        /** Signature + documentation d'une référence nommée (locale, champ, méthode). */
        private fun candidatReference(nom: String, binding: org.eclipse.jdt.internal.compiler.lookup.Binding?): SurvolRiche? {
            return when (binding) {
                is LocalVariableBinding -> {
                    val type = LectureAst.lisible(binding.type) ?: return null
                    SurvolRiche("$type $nom", null)
                }

                is FieldBinding -> {
                    val type = LectureAst.lisible(binding.type) ?: return null
                    SurvolRiche("$type $nom", registre.champs[binding]?.let { javadocDe(source, it.declarationSourceStart) })
                }

                is MethodBinding -> SurvolRiche(signatureMethode(binding) ?: return null, documentationDeMethode(binding))

                is VariableBinding -> {
                    val type = LectureAst.lisible(binding.type) ?: return null
                    SurvolRiche("$type $nom", null)
                }

                is ReferenceBinding -> SurvolRiche(LectureAst.lisible(binding) ?: return null, null)

                else -> null
            }
        }

        /** Documentation d'une méthode par son binding (registre, puis original). */
        private fun documentationDeMethode(binding: MethodBinding): String? {
            val declaration = registre.methodes[binding]
                ?: binding.original()?.let { registre.methodes[it] }
                ?: return null
            return javadocDe(source, declaration.declarationSourceStart)
        }

        /** Retient le candidat s'il couvre le caret et bat la priorité courante. */
        private fun considerer(debut: Int, fin: Int, candidat: SurvolRiche?, priorite: Int) {
            if (candidat == null) return
            if (offset < debut || offset > fin) return
            if (priorite < prioriteMeilleur) return
            meilleur = candidat
            prioriteMeilleur = priorite
        }
    }
}

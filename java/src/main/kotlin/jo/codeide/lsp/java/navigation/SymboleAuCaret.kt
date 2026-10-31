package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldReference
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
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding

/**
 * Nature du symbole identifié au caret (étage N4) : la famille de binding
 * retenue par le [CollecteurSymbole] — elle détermine la clé de
 * correspondance des usages et le routage des définitions.
 */
public enum class NatureSymbole {
    /** Un envoi de message (ou une déclaration de méthode) au caret. */
    METHODE,

    /** Un champ (référence qualifiée, référence simple ou déclaration). */
    CHAMP,

    /** Une variable LOCALE ou un paramètre (périmètre de l'unité courante). */
    LOCALE,

    /** Une référence ou une déclaration de TYPE. */
    TYPE,
}

/**
 * Clé de correspondance d'un symbole pour la recherche d'usages croix
 * fichiers (étage N4 — ADR 0014 : identification par clé de BINDING, les
 * homonymes sont départagés par le propriétaire et l'arité).
 *
 * Les LOCALES n'ont pas de clé croix-fichiers (périmètre de leur méthode)
 * — elles vivent dans l'arbre courant, par identité de binding.
 */
public sealed interface CleUsages {
    /** Une méthode : propriétaire top-niveau + sélecteur + arité. */
    public data class Methode(
        public val fqnProprietaire: String,
        public val selecteur: String,
        public val nbParametres: Int,
    ) : CleUsages

    /** Un champ : propriétaire top-niveau + nom. */
    public data class Champ(
        public val fqnProprietaire: String,
        public val nom: String,
    ) : CleUsages

    /** Un type : FQN source complet (formes imbriquées comprises). */
    public data class Type(public val fqn: String) : CleUsages
}

/**
 * Le symbole identifié au caret (étage N4) : la référence la plus
 * PROFONDE couvrant la position, avec la précision de SEGMENT exigée par
 * la fiche 049 — « la position EXACTE du caret est contractuelle : sur le
 * récepteur → le paramètre ; sur le point → le dernier chaînon ; sur le
 * segment → le membre visé ».
 *
 * @property nature la famille du symbole.
 * @property cleUsages la clé de correspondance croix-fichiers (null pour
 *   une locale — périmètre unité courante, et pour tout binding non sain).
 * @property fqnProprietaire le FQN du type DÉCLARANT (classe qui porte la
 *   déclaration — la cible d'un GO TO definition) ; null pour une locale
 *   (aucune classe déclarante) et pour un type (le propriétaire EST le
 *   type — voir [fqnTypeDuSymbole]).
 * @property fqnTypeDuSymbole le FQN du TYPE du symbole (variable → son
 *   type déclaré ; référence de type → elle-même ; méthode → son type de
 *   RETOUR) — la cible d'un GO TO typeDefinition. Null pour un type de
 *   base (`int`) : « un type de base n'a pas de déclaration à visiter ».
 * @property jeton la plage du JETON visé dans la source de l'arbre.
 * @property bindingEcj le binding ecj sous-jacent (méthode, champ,
 *   locale ou type) — usage MOTEUR interne uniquement (les types ecj ne
 *   traversent pas l'API publique du dépôt).
 */
public class SymboleAuCaret(
    public val nature: NatureSymbole,
    public val cleUsages: CleUsages?,
    public val fqnProprietaire: String?,
    public val fqnTypeDuSymbole: String?,
    public val jeton: Plage,
    internal val bindingEcj: org.eclipse.jdt.internal.compiler.lookup.Binding? = null,
)

/**
 * Registre des déclarations d'une unité (étage N4) : les maps
 * binding → nœud de déclaration pour les champs, méthodes et types — le
 * socle des définitions INTRA-fichier (la déclaration vit dans l'unité
 * courante) et du marquage « est-ce la déclaration ? » des usages.
 */
internal class RegistreUnite : LectureAst.VisiteurResilient() {
    val champs = HashMap<FieldBinding, FieldDeclaration>()
    val methodes = HashMap<MethodBinding, MethodDeclaration>()
    val types = HashMap<ReferenceBinding, TypeDeclaration>()

    override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
        try {
            node.binding?.let { champs[it] = node }
        } catch (_: Exception) {
            // Binding malade : déclaration écartée.
        }
        return true
    }

    override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
        try {
            node.binding?.let { methodes[it] = node }
        } catch (_: Exception) {
            // idem
        }
        return true
    }

    override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
        try {
            (node.binding as? ReferenceBinding)?.let { types[it] = node }
        } catch (_: Exception) {
            // idem
        }
        return true
    }
}

/**
 * Collecteur du symbole au caret (étage N4) : la référence la plus
 * profonde couvrant l'offset, AVEC la précision de segment des références
 * qualifiées (fiche 049). Les nœuds sont retenus par TAILLE CROISSANTE :
 * le plus serré gagne — sur le récepteur d'un envoi, la référence simple
 * du récepteur (plus courte) départage l'envoi entier.
 *
 * La couverture des segments d'une référence qualifiée `a.tag` passe par
 * `sourcePositions` (positions empilées start/end de chaque jeton) : le
 * binding du segment 0 vit dans `binding`, celui du segment i vit dans
 * `otherBindings[i - 1]` (convention ecj — le récepteur d'abord, les
 * chaînons ensuite).
 */
internal open class CollecteurSymbole(
    private val source: String,
    private val offset: Int,
) : LectureAst.VisiteurResilient() {

    /** Le meilleur candidat courant : le plus serré couvrant le caret. */
    private var tailleMeilleure = Int.MAX_VALUE
    private var jeton: Plage? = null
    private var nature: NatureSymbole? = null
    private var cle: CleUsages? = null
    private var fqnProprietaire: String? = null
    private var fqnType: String? = null
    private var binding: org.eclipse.jdt.internal.compiler.lookup.Binding? = null

    /**
     * Retient un candidat couvrant le caret s'il est plus serré que le
     * courant — CHAMP PAR CHAMP (un candidat sans clé écrase la clé du
     * candidat précédent : la locale du récepteur ne porte pas la clé du
     * champ du dernier chaînon).
     */
    fun considerer(
        debut: Int,
        fin: Int,
        nature: NatureSymbole,
        cle: CleUsages?,
        proprietaire: String?,
        typeFqn: String?,
        bindingEcj: org.eclipse.jdt.internal.compiler.lookup.Binding?,
    ) {
        if (offset < debut || offset > fin) return
        if (fin - debut >= tailleMeilleure) return
        tailleMeilleure = fin - debut
        jeton = Plage(debut, fin + 1)
        this.nature = nature
        this.cle = cle
        fqnProprietaire = proprietaire
        fqnType = typeFqn
        binding = bindingEcj
    }

    override fun visiteReferenceSimple(node: SingleNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        when (b) {
            is LocalVariableBinding -> considerer(
                node.sourceStart, node.sourceEnd, NatureSymbole.LOCALE,
                cle = null,
                proprietaire = null,
                typeFqn = fqnDeType(b.type),
                bindingEcj = b,
            )
            is FieldBinding -> {
                val proprietaire = fqnTopDe(b.declaringClass)
                if (proprietaire != null) {
                    considerer(
                        node.sourceStart, node.sourceEnd, NatureSymbole.CHAMP,
                        cle = CleUsages.Champ(proprietaire, String(b.name)),
                        proprietaire = proprietaire,
                        typeFqn = fqnDeType(b.type),
                        bindingEcj = b,
                    )
                }
            }
            else -> (b as? ReferenceBinding)?.let { rb ->
                val fqn = fqnCompletDe(rb)
                if (fqn != null) {
                    considerer(
                        node.sourceStart, node.sourceEnd, NatureSymbole.TYPE,
                        cle = CleUsages.Type(fqn),
                        proprietaire = null,
                        typeFqn = fqn,
                        bindingEcj = rb,
                    )
                }
            }
        }
        return true
    }

    override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        val positions = try { node.sourcePositions } catch (_: Exception) { null } ?: return true
        val autres = try { node.otherBindings } catch (_: Exception) { null }
        // Segment 0 : le récepteur (binding du PREMIER jeton).
        runCatching {
            val p0 = positions.getOrNull(0) ?: return@runCatching
            val debut = (p0 ushr 32).toInt()
            val fin = p0.toInt()
            if (offset in debut..fin) {
                when (val b = node.binding) {
                    is LocalVariableBinding -> considerer(debut, fin, NatureSymbole.LOCALE, null, null, fqnDeType(b.type), b)
                    is FieldBinding -> {
                        val proprietaire = fqnTopDe(b.declaringClass)
                        if (proprietaire != null) {
                            considerer(
                                debut, fin, NatureSymbole.CHAMP,
                                CleUsages.Champ(proprietaire, String(b.name)),
                                proprietaire, fqnDeType(b.type), b,
                            )
                        }
                    }
                    else -> (b as? ReferenceBinding)?.let { rb ->
                        val fqn = fqnCompletDe(rb)
                        if (fqn != null) {
                            considerer(debut, fin, NatureSymbole.TYPE, CleUsages.Type(fqn), null, fqn, rb)
                        }
                    }
                }
            }
        }
        // Segments suivants : les chaînons (otherBindings[i - 1]).
        for (i in 1 until positions.size) {
            val b = autres?.getOrNull(i - 1) ?: continue
            if (b !is FieldBinding) continue
            runCatching {
                val p = positions[i]
                val debut = (p ushr 32).toInt()
                val fin = p.toInt()
                if (offset in debut..fin) {
                    val proprietaire = fqnTopDe(b.declaringClass)
                    if (proprietaire != null) {
                        considerer(
                            debut, fin, NatureSymbole.CHAMP,
                            CleUsages.Champ(proprietaire, String(b.name)),
                            proprietaire,
                            fqnDeType(b.type),
                            b,
                        )
                    }
                }
            }
        }
        // Forme « liaison unique » (V-B2) : pour `Type.Nested.champ`, ecj
        // lie le DERNIER segment dans `binding` et laisse `otherBindings`
        // NULL — le caret sur le dernier jeton désigne quand même le champ
        // (latence de navigation comblée pour TOUTE référence qualifiée
        // statique, `R.layout.x` compris).
        if (autres == null && positions.isNotEmpty()) {
            runCatching {
                val b = node.binding
                if (b is FieldBinding) {
                    val p = positions[positions.size - 1]
                    val debut = (p ushr 32).toInt()
                    val fin = p.toInt()
                    if (offset in debut..fin) {
                        val proprietaire = fqnTopDe(b.declaringClass)
                        if (proprietaire != null) {
                            considerer(
                                debut, fin, NatureSymbole.CHAMP,
                                CleUsages.Champ(proprietaire, String(b.name)),
                                proprietaire,
                                fqnDeType(b.type),
                                b,
                            )
                        }
                    }
                }
            }
        }
        return true
    }

    override fun visiteReferenceChamp(node: FieldReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } as? FieldBinding ?: return true
        val proprietaire = fqnTopDe(b.declaringClass) ?: return true
        val jetonNom = try {
            val nom = String(node.token)
            Plage(node.sourceStart, node.sourceStart + nom.length)
        } catch (_: Exception) {
            null
        } ?: return true
        considerer(
            jetonNom.debutOffset, jetonNom.finOffset - 1, NatureSymbole.CHAMP,
            CleUsages.Champ(proprietaire, String(b.name)),
            proprietaire,
            fqnDeType(b.type),
            b,
        )
        return true
    }

    override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } as? MethodBinding ?: return true
        val proprietaire = fqnTopDe(b.declaringClass) ?: return true
        considerer(
            node.sourceStart, node.sourceEnd, NatureSymbole.METHODE,
            CleUsages.Methode(proprietaire, String(b.selector), b.parameters?.size ?: 0),
            proprietaire,
            fqnDeType(b.returnType),
            b,
        )
        return true
    }

    override fun visiteAllocation(node: AllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        val t = try { node.resolvedType } catch (_: Exception) { null } as? ReferenceBinding ?: return true
        val fqn = fqnCompletDe(t) ?: return true
        considerer(
            node.sourceStart, node.sourceEnd, NatureSymbole.TYPE,
            CleUsages.Type(fqn),
            null,
            fqn,
            t,
        )
        return true
    }

    override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        considerer(
            node.sourceStart, node.sourceStart + node.name.size - 1, NatureSymbole.LOCALE,
            null, null, fqnDeType(b.type),
            b,
        )
        return true
    }

    override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        val proprietaire = fqnTopDe(b.declaringClass) ?: return true
        considerer(
            node.sourceStart, node.sourceStart + node.name.size - 1, NatureSymbole.CHAMP,
            CleUsages.Champ(proprietaire, String(b.name)),
            proprietaire,
            fqnDeType(b.type),
            b,
        )
        return true
    }

    override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        val proprietaire = fqnTopDe(b.declaringClass) ?: return true
        // Le nom déclaré est plus serré que la méthode entière — la plage
        // couvre la TÊTE (modificateurs + type de retour compris) et le
        // sélecteur est retrouvé par balayage (convention N3).
        considerer(
            node.sourceStart, node.sourceStart + 120, NatureSymbole.METHODE,
            CleUsages.Methode(proprietaire, String(b.selector), b.parameters?.size ?: 0),
            proprietaire,
            fqnDeType(b.returnType),
            b,
        )
        return true
    }

    override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } as? ReferenceBinding ?: return true
        val fqn = fqnCompletDe(b) ?: return true
        considerer(
            node.sourceStart, node.sourceStart + node.name.size - 1, NatureSymbole.TYPE,
            CleUsages.Type(fqn),
            null,
            fqn,
            b,
        )
        return true
    }

    // --- références de type : toutes les variantes (héritage direct d'ASTVisitor) ---

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    /** Considère une référence de type : le type EST à la fois propriété et type du symbole. */
    private fun considererType(debut: Int, fin: Int, type: TypeBinding?) {
        val reference = type as? ReferenceBinding ?: return
        val fqn = fqnCompletDe(reference) ?: return
        considerer(debut, fin, NatureSymbole.TYPE, CleUsages.Type(fqn), null, fqn, reference)
    }

    /** Le symbole identifié, ou null (caret sans binding — blanc, commentaire). */
    fun symbole(): SymboleAuCaret? {
        val nature = nature ?: return null
        val jeton = jeton ?: return null
        return SymboleAuCaret(nature, cle, fqnProprietaire, fqnType, jeton, binding)
    }

    // --- extraction de FQN (défensif : tout binding ecj peut être malade) ---

    /** FQN complet d'un type (formes imbriquées comprises : `demo.Outer.Inner`). */
    private fun fqnCompletDe(type: ReferenceBinding?): String? = try {
        type?.compoundName?.joinToString(".") { String(it) }
    } catch (_: Exception) {
        null
    }

    /** FQN du type TOP-NIVEAU porteur (les imbriqués retombent sur l'englobant). */
    private fun fqnTopDe(type: ReferenceBinding?): String? = try {
        var courant = type ?: return null
        while (courant.enclosingType() != null) courant = courant.enclosingType()
        courant.compoundName?.joinToString(".") { String(it) }
    } catch (_: Exception) {
        null
    }

    /** FQN d'un type quelconque (null pour un type de base — `int` n'a pas de déclaration). */
    private fun fqnDeType(type: TypeBinding?): String? = try {
        when (type) {
            null -> null
            is ReferenceBinding -> fqnCompletDe(type)
            else -> null // type de base ou tableau : pas de déclaration à visiter
        }
    } catch (_: Exception) {
        null
    }
}

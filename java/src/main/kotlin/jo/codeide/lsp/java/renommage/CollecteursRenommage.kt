package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.navigation.CleUsages
import jo.codeide.lsp.java.navigation.CollecteurSymbole
import jo.codeide.lsp.java.navigation.NatureSymbole
import jo.codeide.lsp.java.navigation.SymboleAuCaret
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.Argument
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.internal.compiler.ast.ImportReference
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.lookup.ClassScope
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.ImportBinding
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.BlockScope

// --------------------------------------------------------------------- //
// Outils FQN et jetons partagés par les collecteurs du renommage (N5)
// --------------------------------------------------------------------- //

/** FQN complet d'un type (imbriqués compris : `demo.Externe.Interne`). */
internal fun fqnCompletDuType(type: ReferenceBinding?): String? = try {
    type?.compoundName?.joinToString(".") { String(it) }
} catch (_: Exception) {
    null
}

/** FQN d'un type QUELCONQUE (null pour un type de base — `int` n'a pas de FQN). */
internal fun fqnDuType(type: org.eclipse.jdt.internal.compiler.lookup.TypeBinding?): String? = try {
    when (type) {
        null -> null
        is ReferenceBinding -> fqnCompletDuType(type)
        else -> null
    }
} catch (_: Exception) {
    null
}

/** FQN du type TOP-NIVEAU porteur (les imbriqués retombent sur l'englobant). */
internal fun fqnTopDuType(type: ReferenceBinding?): String? = try {
    var courant = type ?: return null
    while (courant.enclosingType() != null) courant = courant.enclosingType()
    courant.compoundName?.joinToString(".") { String(it) }
} catch (_: Exception) {
    null
}

/** La superclasse d'un type résolu (null si malade ou racine). */
private fun superclassDe(type: ReferenceBinding?): ReferenceBinding? = try {
    type?.superclass()
} catch (_: Exception) {
    null
}

/** Les interfaces directes d'un type résolu (vide si malades). */
private fun interfacesDe(type: ReferenceBinding?): Array<ReferenceBinding> = try {
    type?.superInterfaces() ?: emptyArray()
} catch (_: Exception) {
    emptyArray()
}

/**
 * Vrai ssi le type candidat est un SOUS-TYPE strict du type portant le
 * FQN de base (chaîne des superclasses et interfaces, garde de
 * profondeur anti-cycle) — le départage des overrides non suivis.
 */
internal fun estSousTypeFqn(candidat: ReferenceBinding?, fqnBase: String, profondeur: Int = 0): Boolean {
    if (candidat == null || profondeur > 32) return false
    var courant: ReferenceBinding? = candidat
    var garde = 0
    while (courant != null && garde <= 32) {
        val fqn = fqnCompletDuType(courant)
        if (fqn == fqnBase) return true
        if (fqn == "java.lang.Object") break
        for (parente in interfacesDe(courant)) {
            if (estSousTypeFqn(parente, fqnBase, profondeur + garde + 1)) return true
        }
        courant = superclassDe(courant)
        garde++
    }
    return false
}

/** Cherche [mot] aux bornes de mots dans `[debut, limite]` du texte. */
internal fun plageJeton(source: String, debut: Int, limite: Int, mot: String): Plage? {
    val borne = limite.coerceAtMost(source.length)
    if (debut >= borne) return null
    val tete = source.substring(debut, borne)
    var i = 0
    while (i < tete.length) {
        if (tete[i].isJavaIdentifierStart() && tete.regionMatches(i, mot, 0, mot.length)) {
            val apres = i + mot.length
            val borneAvant = i == 0 || !tete[i - 1].isJavaIdentifierPart()
            val borneApres = apres >= tete.length || !tete[apres].isJavaIdentifierPart()
            if (borneAvant && borneApres) return Plage(debut + i, debut + apres)
        }
        i++
    }
    return null
}

// --------------------------------------------------------------------- //
// Phase PREPARE — le collecteur de cible (fiche 026)
// --------------------------------------------------------------------- //

/**
 * Collecteur de la cible de renommage (étage N5 — fiche 026) : le
 * [CollecteurSymbole] de l'étage N4 EXTENDU aux deux cas particuliers
 * du renommage :
 *
 *  - le nom d'un CONSTRUCTEUR vise le TYPE (« la cible est le TYPE
 *    (isType = true) ») ;
 *  - un SEGMENT d'import est une cible comme une autre : le nom simple
 *    d'un type importé vise le type, le membre d'un import statique
 *    vise le champ ou la méthode.
 *
 * L'identification reste par BINDINGS (la clé structurelle isole les
 * homonymes) — le texte seul ne décide jamais.
 */
internal class CollecteurCibleRenommage(
    private val texte: String,
    private val position: Int,
    private val liaisons: Map<ImportReference, ImportBinding>,
) : CollecteurSymbole(texte, position) {

    /**
     * Une déclaration de méthode : le JETON EXACT du sélecteur (le
     * collecteur N4 considère la TÊTE entière — le renommage exige la
     * plage du nom, fiche 061).
     */
    override fun visiteMethode(node: MethodDeclaration, scope: ClassScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        val proprietaire = fqnTopDuType(b.declaringClass) ?: return true
        val nom = try { String(node.selector) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(texte, node.sourceStart, node.sourceStart + 120, nom) ?: return true
        considerer(
            plage.debutOffset, plage.finOffset - 1, NatureSymbole.METHODE,
            CleUsages.Methode(proprietaire, nom, b.parameters?.size ?: 0),
            proprietaire, fqnDuType(b.returnType), b,
        )
        return true
    }

    /**
     * Une déclaration de CONSTRUCTEUR : la cible est le TYPE (fiche 026 —
     * « la cible est le TYPE (isType = true) ») — ecj dispatche les
     * constructeurs vers leur propre point de visite.
     */
    override fun visiteConstructeur(node: ConstructorDeclaration, scope: ClassScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        val fqnType = fqnCompletDuType(b.declaringClass) ?: return true
        val nom = try { String(node.selector) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(texte, node.sourceStart, node.sourceStart + 120, nom) ?: return true
        considerer(
            plage.debutOffset, plage.finOffset - 1,
            NatureSymbole.TYPE,
            CleUsages.Type(fqnType),
            null, fqnType, b,
        )
        return true
    }

    /** Une déclaration de champ : le JETON EXACT du nom. */
    override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        val proprietaire = fqnTopDuType(b.declaringClass) ?: return true
        val nom = try { String(node.name) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(texte, node.sourceStart, node.sourceStart + 200, nom) ?: return true
        considerer(
            plage.debutOffset, plage.finOffset - 1, NatureSymbole.CHAMP,
            CleUsages.Champ(proprietaire, nom),
            proprietaire, fqnDuType(b.type), b,
        )
        return true
    }

    /** Une déclaration de type : le JETON EXACT du nom (recherche en tête). */
    override fun visiteType(node: TypeDeclaration, scope: ClassScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } as? ReferenceBinding ?: return true
        val fqn = fqnCompletDuType(b) ?: return true
        val nom = try { String(node.name) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(texte, node.sourceStart, node.sourceStart + 200, nom) ?: return true
        considerer(plage.debutOffset, plage.finOffset - 1, NatureSymbole.TYPE, CleUsages.Type(fqn), null, fqn, b)
        return true
    }

    /** Une déclaration de locale : le JETON EXACT du nom (recherche en tête). */
    override fun visiteLocale(node: LocalDeclaration, scope: BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        val nom = try { String(node.name) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(texte, node.sourceStart, node.sourceStart + 200, nom) ?: return true
        considerer(plage.debutOffset, plage.finOffset - 1, NatureSymbole.LOCALE, null, null, fqnDuType(b.type), b)
        return true
    }

    /** Un paramètre : le JETON EXACT du nom (recherche en tête). */
    override fun visiteArgument(node: Argument, scope: BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        val nom = try { String(node.name) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(texte, node.sourceStart, node.sourceStart + 200, nom) ?: return true
        considerer(plage.debutOffset, plage.finOffset - 1, NatureSymbole.LOCALE, null, null, fqnDuType(b.type), b)
        return true
    }

    /** Un envoi de message : le JETON EXACT du sélecteur. */
    override fun visiteEnvoi(node: MessageSend, scope: BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } as? MethodBinding ?: return true
        val proprietaire = fqnTopDuType(b.declaringClass) ?: return true
        val nom = try { String(b.selector) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(texte, node.sourceStart, node.sourceEnd, nom) ?: return true
        considerer(
            plage.debutOffset, plage.finOffset - 1, NatureSymbole.METHODE,
            CleUsages.Methode(proprietaire, nom, b.parameters?.size ?: 0),
            proprietaire, fqnDuType(b.returnType), b,
        )
        return true
    }

    /** Une référence de champ explicite : le JETON EXACT du nom (recherche). */
    override fun visiteReferenceChamp(node: FieldReference, scope: BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } as? FieldBinding ?: return true
        val proprietaire = fqnTopDuType(b.declaringClass) ?: return true
        val nom = try { String(node.token) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(texte, node.sourceStart, node.sourceEnd, nom) ?: return true
        considerer(
            plage.debutOffset, plage.finOffset - 1, NatureSymbole.CHAMP,
            CleUsages.Champ(proprietaire, String(b.name)),
            proprietaire, fqnDuType(b.type), b,
        )
        return true
    }

    /** Une référence de type QUALIFIÉE : le SEGMENT du nom simple (jeton exact). */
    override fun visit(node: QualifiedTypeReference, scope: BlockScope?): Boolean {
        considererTypeQualifie(node)
        return true
    }

    /** Une référence de type QUALIFIÉE : le SEGMENT du nom simple (jeton exact). */
    override fun visit(node: QualifiedTypeReference, scope: ClassScope?): Boolean {
        considererTypeQualifie(node)
        return true
    }

    /** Une référence de type qualifiée paramétrée : le SEGMENT du nom simple. */
    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference, scope: BlockScope?): Boolean {
        considererTypeQualifie(node)
        return true
    }

    /** Une référence de type qualifiée paramétrée : le SEGMENT du nom simple. */
    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference, scope: ClassScope?): Boolean {
        considererTypeQualifie(node)
        return true
    }

    /** Considère le dernier SEGMENT d'une référence de type qualifiée. */
    private fun considererTypeQualifie(node: QualifiedTypeReference) {
        val reference = try { node.resolvedType } catch (_: Exception) { null } as? ReferenceBinding ?: return
        val fqn = fqnCompletDuType(reference) ?: return
        val positions = try { node.sourcePositions } catch (_: Exception) { null } ?: return
        val dernier = positions.lastOrNull() ?: return
        val debut = (dernier ushr 32).toInt()
        val fin = dernier.toInt()
        if (position in debut..fin) {
            considerer(debut, fin, NatureSymbole.TYPE, CleUsages.Type(fqn), null, fqn, reference)
        }
    }

    /** Les segments d'import : le type importé, le membre statique importé. */
    override fun visiteImport(node: ImportReference, scope: CompilationUnitScope?): Boolean {
        val liaison = liaisons[node]
        val resolu = try { liaison?.resolvedImport } catch (_: Exception) { null }
        val estStatique = try { node.isStatic() } catch (_: Exception) { false }
        val positions = try { node.sourcePositions } catch (_: Exception) { null } ?: return true
        val tokens = try { node.tokens?.map { String(it) } } catch (_: Exception) { null } ?: return true

        // Dernier segment : le type (import simple, à la demande, statique
        // à la demande) ou le membre (import statique nominatif).
        run {
            val i = tokens.lastIndex
            val p = positions.getOrNull(i) ?: return@run
            val debut = (p ushr 32).toInt()
            val fin = p.toInt()
            if (position in debut..fin) {
                when (val b = resolu) {
                    is ReferenceBinding -> {
                        val fqn = fqnCompletDuType(b)
                        if (fqn != null) {
                            considerer(debut, fin, NatureSymbole.TYPE, CleUsages.Type(fqn), null, fqn, b)
                        }
                    }
                    is FieldBinding -> {
                        val proprietaire = fqnTopDuType(b.declaringClass)
                        if (proprietaire != null) {
                            considerer(
                                debut, fin, NatureSymbole.CHAMP,
                                CleUsages.Champ(proprietaire, String(b.name)),
                                proprietaire, fqnDuType(b.type), b,
                            )
                        }
                    }
                    is MethodBinding -> {
                        val proprietaire = fqnTopDuType(b.declaringClass)
                        if (proprietaire != null) {
                            considerer(
                                debut, fin, NatureSymbole.METHODE,
                                CleUsages.Methode(proprietaire, String(b.selector), b.parameters?.size ?: 0),
                                proprietaire, fqnDuType(b.returnType), b,
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        // Avant-dernier segment d'un import statique nominatif : le nom
        // simple du type porteur du membre importé.
        if (estStatique && tokens.size >= 2) {
            val i = tokens.size - 2
            val p = positions.getOrNull(i) ?: return true
            val debut = (p ushr 32).toInt()
            val fin = p.toInt()
            if (position in debut..fin) {
                val declarante = when (val b = resolu) {
                    is FieldBinding -> b.declaringClass
                    is MethodBinding -> b.declaringClass
                    else -> null
                }
                val fqn = fqnCompletDuType(declarante)
                if (fqn != null && tokens[i] == fqn.substringAfterLast('.')) {
                    considerer(debut, fin, NatureSymbole.TYPE, CleUsages.Type(fqn), null, fqn, declarante)
                }
            }
        }
        return true
    }
}

// --------------------------------------------------------------------- //
// Phase USAGES — le collecteur d'occurrences (fiche 026)
// --------------------------------------------------------------------- //

/**
 * Collecteur des occurrences d'un renommage dans UNE unité résolue
 * (étage N5 — fiche 026) : la même correspondance EXACTE par clé de
 * binding que le collecteur d'usages de l'étage N4, ÉTENDUE aux cas
 * du renommage :
 *
 *  - TYPE : les CONSTRUCTEURS (déclaration + le sélecteur compte pour
 *    le type) et les IMPORTS (segment du nom simple) sont des
 *    occurrences comme les autres ;
 *  - CHAMP/MÉTHODE : un IMPORT STATIQUE nominatif portant le membre
 *    est retouché ;
 *  - références de type QUALIFIÉES : l'occurrence couvre le segment du
 *    nom SIMPLE (le jeton exact), jamais la référence entière ;
 *  - OVERRIDES (opt-in) : les déclarations outrépassées des sous-types
 *    suivent le renommage quand l'utilisateur l'arme explicitement.
 */
internal class CollecteurOccurrencesRenommage(
    private val source: String,
    private val cle: CleUsages?,
    private val localeCible: LocalVariableBinding?,
    private val suivreOverrides: Boolean = false,
    private val liaisons: Map<ImportReference, ImportBinding> = emptyMap(),
) : LectureAst.VisiteurResilient() {

    /** Une occurrence : plage du JETON exact + drapeau déclaration. */
    data class Occurrence(val plage: Plage, val estDeclaration: Boolean)

    val occurrences = ArrayList<Occurrence>()

    /** Vrai ssi la méthode binding correspond à la clé méthode (ou à un override suivi). */
    private fun correspond(b: MethodBinding): Boolean {
        val m = cle as? CleUsages.Methode ?: return false
        val proprietaire = fqnTopDuType(b.declaringClass) ?: return false
        if (proprietaire != m.fqnProprietaire) {
            // Opt-in overrides : la déclaration d'un sous-type (même
            // sélecteur, même arité) suit le renommage de la base.
            return suivreOverrides &&
                String(b.selector) == m.selecteur &&
                (b.parameters?.size ?: 0) == m.nbParametres &&
                estSousTypeFqn(b.declaringClass, m.fqnProprietaire)
        }
        return String(b.selector) == m.selecteur && (b.parameters?.size ?: 0) == m.nbParametres
    }

    /** Vrai ssi le champ binding correspond à la clé champ. */
    private fun correspond(b: FieldBinding): Boolean {
        val c = cle as? CleUsages.Champ ?: return false
        return fqnTopDuType(b.declaringClass) == c.fqnProprietaire && String(b.name) == c.nom
    }

    /** Vrai ssi le type binding correspond à la clé type. */
    private fun correspond(b: ReferenceBinding): Boolean {
        val t = cle as? CleUsages.Type ?: return false
        return fqnCompletDuType(b) == t.fqn
    }

    /** Vrai ssi la locale binding EST la locale cible (identité). */
    private fun correspond(b: LocalVariableBinding): Boolean = b === localeCible

    /** Retient une occurrence (plage du jeton exact). */
    private fun retenir(debut: Int, fin: Int, estDeclaration: Boolean) {
        occurrences.add(Occurrence(Plage(debut, fin + 1), estDeclaration))
    }

    override fun visiteEnvoi(node: MessageSend, scope: BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null }
        if (b is MethodBinding && correspond(b)) {
            val nom = try { String(b.selector) } catch (_: Exception) { null }
            val plage = if (nom != null) plageJeton(source, node.sourceStart, node.sourceEnd, nom) else null
            if (plage != null) retenir(plage.debutOffset, plage.finOffset - 1, false)
        }
        return true
    }

    override fun visiteMethode(node: MethodDeclaration, scope: ClassScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null }
        if (b is MethodBinding && correspond(b)) {
            val nom = try { String(node.selector) } catch (_: Exception) { null } ?: return true
            val plage = plageJeton(source, node.sourceStart, node.sourceStart + 120, nom)
            if (plage != null) retenir(plage.debutOffset, plage.finOffset - 1, true)
        }
        return true
    }

    /** Le CONSTRUCTEUR compte pour le type (fiche 026) — jeton exact du sélecteur. */
    override fun visiteConstructeur(node: ConstructorDeclaration, scope: ClassScope?): Boolean {
        val t = cle as? CleUsages.Type ?: return true
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        if (fqnCompletDuType(b.declaringClass) != t.fqn) return true
        val nom = try { String(node.selector) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(source, node.sourceStart, node.sourceStart + 120, nom)
        if (plage != null) retenir(plage.debutOffset, plage.finOffset - 1, true)
        return true
    }

    override fun visiteReferenceSimple(node: SingleNameReference, scope: BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null }
        when {
            b is LocalVariableBinding && correspond(b) -> retenir(node.sourceStart, node.sourceEnd, false)
            b is FieldBinding && correspond(b) -> retenir(node.sourceStart, node.sourceEnd, false)
            b is ReferenceBinding && correspond(b) -> retenir(node.sourceStart, node.sourceEnd, false)
        }
        return true
    }

    override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: BlockScope?): Boolean {
        val positions = try { node.sourcePositions } catch (_: Exception) { null } ?: return true
        val autres = try { node.otherBindings } catch (_: Exception) { null }
        runCatching {
            val b = node.binding
            val p0 = positions.getOrNull(0) ?: return@runCatching
            val debut = (p0 ushr 32).toInt()
            val fin = p0.toInt()
            when {
                b is LocalVariableBinding && correspond(b) -> retenir(debut, fin, false)
                b is FieldBinding && correspond(b) -> retenir(debut, fin, false)
                b is ReferenceBinding && correspond(b) -> retenir(debut, fin, false)
            }
        }
        for (i in 1 until positions.size) {
            val b = autres?.getOrNull(i - 1) ?: continue
            runCatching {
                val p = positions[i]
                val debut = (p ushr 32).toInt()
                val fin = p.toInt()
                if (b is FieldBinding && correspond(b)) retenir(debut, fin, false)
            }
        }
        return true
    }

    override fun visiteReferenceChamp(node: FieldReference, scope: BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } as? FieldBinding ?: return true
        if (correspond(b)) {
            val nom = try { String(node.token) } catch (_: Exception) { null }
            if (nom != null) {
                val plage = plageJeton(source, node.sourceStart, node.sourceEnd, nom)
                if (plage != null) retenir(plage.debutOffset, plage.finOffset - 1, false)
            }
        }
        return true
    }

    override fun visiteLocale(node: LocalDeclaration, scope: BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        if (b is LocalVariableBinding && correspond(b)) {
            val nom = String(node.name)
            val plage = plageJeton(source, node.sourceStart, node.sourceStart + 200, nom) ?: return true
            retenir(plage.debutOffset, plage.finOffset - 1, true)
        }
        return true
    }

    override fun visiteArgument(node: Argument, scope: BlockScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        if (b is LocalVariableBinding && correspond(b)) {
            val nom = String(node.name)
            val plage = plageJeton(source, node.sourceStart, node.sourceStart + 200, nom) ?: return true
            retenir(plage.debutOffset, plage.finOffset - 1, true)
        }
        return true
    }

    override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } ?: return true
        if (b is FieldBinding && correspond(b)) {
            val nom = String(node.name)
            val plage = plageJeton(source, node.sourceStart, node.sourceStart + 200, nom) ?: return true
            retenir(plage.debutOffset, plage.finOffset - 1, true)
        }
        return true
    }

    override fun visiteType(node: TypeDeclaration, scope: ClassScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } as? ReferenceBinding ?: return true
        if (correspond(b)) {
            val nom = String(node.name)
            val plage = plageJeton(source, node.sourceStart, node.sourceStart + 200, nom) ?: return true
            retenir(plage.debutOffset, plage.finOffset - 1, true)
        }
        return true
    }

    // --- références de type : toutes variantes, SEGMENT simple pour les qualifiées ---

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.SingleTypeReference, scope: BlockScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.SingleTypeReference, scope: ClassScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: QualifiedTypeReference, scope: BlockScope?): Boolean {
        considererTypeQualifie(node)
        return true
    }

    override fun visit(node: QualifiedTypeReference, scope: ClassScope?): Boolean {
        considererTypeQualifie(node)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference, scope: BlockScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference, scope: ClassScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference, scope: BlockScope?): Boolean {
        considererTypeQualifie(node)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference, scope: ClassScope?): Boolean {
        considererTypeQualifie(node)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference, scope: BlockScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference, scope: ClassScope?): Boolean {
        considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
        return true
    }

    /** Une référence de type SIMPLE : le jeton exact. */
    private fun considererType(debut: Int, fin: Int, type: org.eclipse.jdt.internal.compiler.lookup.TypeBinding?) {
        val reference = type as? ReferenceBinding ?: return
        if (correspond(reference)) retenir(debut, fin, false)
    }

    /** Une référence de type QUALIFIÉE : le SEGMENT du nom simple (jeton exact). */
    private fun considererTypeQualifie(node: QualifiedTypeReference) {
        val reference = try { node.resolvedType } catch (_: Exception) { null } as? ReferenceBinding ?: return
        if (!correspond(reference)) return
        val positions = try { node.sourcePositions } catch (_: Exception) { null } ?: return
        val dernier = positions.lastOrNull() ?: return
        val debut = (dernier ushr 32).toInt()
        val fin = dernier.toInt()
        if (fin >= debut) retenir(debut, fin, false)
    }

    // --- imports : le segment du nom importé est une occurrence ---

    override fun visiteImport(node: ImportReference, scope: CompilationUnitScope?): Boolean {
        val cle = cle ?: return true
        val positions = try { node.sourcePositions } catch (_: Exception) { null } ?: return true
        val tokens = try { node.tokens?.map { String(it) } } catch (_: Exception) { null } ?: return true
        val liaison = liaisons[node]
        val resolu = try { liaison?.resolvedImport } catch (_: Exception) { null }
        val estStatique = try { node.isStatic() } catch (_: Exception) { false }

        fun retenirSegment(i: Int) {
            val p = positions.getOrNull(i) ?: return
            val debut = (p ushr 32).toInt()
            val fin = p.toInt()
            if (fin >= debut) retenir(debut, fin, false)
        }

        when (cle) {
            is CleUsages.Type -> {
                when {
                    resolu is ReferenceBinding && fqnCompletDuType(resolu) == cle.fqn ->
                        retenirSegment(tokens.lastIndex)
                    (resolu is FieldBinding || resolu is MethodBinding) -> {
                        val declarante = when (val b = resolu) {
                            is FieldBinding -> b.declaringClass
                            is MethodBinding -> b.declaringClass
                            else -> null
                        }
                        if (declarante != null && fqnCompletDuType(declarante) == cle.fqn && tokens.size >= 2) {
                            retenirSegment(tokens.size - 2)
                        }
                    }
                    // Repli textuel (import non résolu) : un import est
                    // PLEINEMENT qualifié — le texte ne peut pas mentir.
                    resolu == null && !estStatique && tokens.joinToString(".") == cle.fqn ->
                        retenirSegment(tokens.lastIndex)
                    else -> {}
                }
            }
            is CleUsages.Champ -> {
                when {
                    resolu is FieldBinding &&
                        fqnCompletDuType(resolu.declaringClass) == cle.fqnProprietaire &&
                        String(resolu.name) == cle.nom -> retenirSegment(tokens.lastIndex)
                    resolu == null && estStatique &&
                        tokens.joinToString(".") == "${cle.fqnProprietaire}.${cle.nom}" ->
                        retenirSegment(tokens.lastIndex)
                    else -> {}
                }
            }
            is CleUsages.Methode -> {
                when {
                    resolu is MethodBinding &&
                        fqnCompletDuType(resolu.declaringClass) == cle.fqnProprietaire &&
                        String(resolu.selector) == cle.selecteur -> retenirSegment(tokens.lastIndex)
                    resolu == null && estStatique &&
                        tokens.joinToString(".") == "${cle.fqnProprietaire}.${cle.selecteur}" ->
                        retenirSegment(tokens.lastIndex)
                    else -> {}
                }
            }
        }
        return true
    }
}

// --------------------------------------------------------------------- //
// Phase CONFLITS — l'analyseur des périmètres et le chasseur d'overrides
// --------------------------------------------------------------------- //

/**
 * Analyseur des périmètres d'une unité déclarante (phase CONFLITS) :
 * les noms des MEMBRES par classe déclarante, les LOCALES par méthode
 * hôte, les TYPES de l'unité — la matière première des collisions
 * NOM_PRIS et MASQUAGE.
 *
 * @param source le texte de l'unité déclarante.
 * @param symbole la cible du renommage (l'identité de binding de la
 *   locale sert à retrouver sa méthode hôte).
 */
internal class AnalyseurPerimetres(
    private val source: String,
    symbole: SymboleAuCaret?,
) : LectureAst.VisiteurResilient() {

    /** Un membre : nom, plage du jeton, FQN de la classe déclarante. */
    private val membres = ArrayList<Triple<String, Plage, String>>()

    /** Une locale : nom, plage du jeton, méthode hôte. */
    private val locales = ArrayList<Triple<String, Plage, AbstractMethodDeclaration>>()

    /** Les types déclarés dans l'unité : nom simple + plage du jeton. */
    private val types = ArrayList<Pair<String, Plage>>()

    /** La méthode hôte de la locale cible (périmètre d'une locale). */
    internal var methodeHote: AbstractMethodDeclaration? = null
        private set

    /** La pile des méthodes et constructeurs en cours de traversée. */
    private val pile = ArrayDeque<AbstractMethodDeclaration>()

    private val localeCible = symbole?.bindingEcj as? LocalVariableBinding

    override fun visiteMethode(node: MethodDeclaration, scope: ClassScope?): Boolean {
        pile.addLast(node)
        val b = try { node.binding } catch (_: Exception) { null }
        if (b is MethodBinding) {
            val fqn = fqnCompletDuType(b.declaringClass)
            val nom = try { String(node.selector) } catch (_: Exception) { null }
            if (fqn != null && nom != null) {
                val plage = plageJeton(source, node.sourceStart, node.sourceStart + 120, nom)
                if (plage != null) membres.add(Triple(nom, plage, fqn))
            }
        }
        return true
    }

    override fun finMethode(node: MethodDeclaration, scope: ClassScope?) {
        if (pile.isNotEmpty() && pile.last() === node) pile.removeLast()
    }

    /**
     * Un constructeur entre dans la pile (les locales de son corps ont
     * une méthode hôte) mais PAS dans les membres — un constructeur
     * n'occupe pas l'espace des noms de champs/méthodes.
     */
    override fun visiteConstructeur(node: ConstructorDeclaration, scope: ClassScope?): Boolean {
        pile.addLast(node)
        return true
    }

    override fun finConstructeur(node: ConstructorDeclaration, scope: ClassScope?) {
        if (pile.isNotEmpty() && pile.last() === node) pile.removeLast()
    }

    override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null }
        if (b is FieldBinding) {
            val fqn = fqnCompletDuType(b.declaringClass)
            val nom = try { String(node.name) } catch (_: Exception) { null }
            if (fqn != null && nom != null) {
                val plage = plageJeton(source, node.sourceStart, node.sourceStart + 200, nom)
                if (plage != null) membres.add(Triple(nom, plage, fqn))
            }
        }
        return true
    }

    override fun visiteType(node: TypeDeclaration, scope: ClassScope?): Boolean {
        val nom = try { String(node.name) } catch (_: Exception) { null } ?: return true
        val plage = plageJeton(source, node.sourceStart, node.sourceStart + 200, nom) ?: return true
        types.add(Pair(nom, plage))
        return true
    }

    override fun visiteLocale(node: LocalDeclaration, scope: BlockScope?): Boolean {
        enregistrerLocale(node.binding, node.sourceStart, node.name)
        return true
    }

    override fun visiteArgument(node: Argument, scope: BlockScope?): Boolean {
        enregistrerLocale(node.binding, node.sourceStart, node.name)
        return true
    }

    /** Enregistre une locale (et sa méthode hôte si c'est la cible). */
    private fun enregistrerLocale(binding: org.eclipse.jdt.internal.compiler.lookup.Binding?, debut: Int, nom: CharArray) {
        val b = try { binding } catch (_: Exception) { null } as? LocalVariableBinding ?: return
        val texte = String(nom)
        val plage = plageJeton(source, debut, debut + 200, texte) ?: return
        val hote = pile.lastOrNull()
        locales.add(Triple(texte, plage, hote ?: return))
        if (localeCible != null && b === localeCible && methodeHote == null) {
            methodeHote = hote
        }
    }

    /** Les membres (nom, plage) d'une classe déclarante (FQN complet). */
    fun membresDeLaClasse(fqn: String): List<Pair<String, Plage>> =
        membres.filter { it.third == fqn }.map { Pair(it.first, it.second) }

    /** Les locales (nom, plage) des méthodes d'une classe (FQN complet). */
    fun localesDesMethodesDe(fqn: String): List<Pair<String, Plage>> {
        val resultats = ArrayList<Pair<String, Plage>>()
        for ((nom, plage, methode) in locales) {
            val b = try { methode.binding } catch (_: Exception) { null } as? MethodBinding ?: continue
            val declarante = fqnCompletDuType(b.declaringClass)
            if (declarante == fqn) resultats.add(Pair(nom, plage))
        }
        return resultats
    }

    /** Les locales (nom, plage) d'UNE méthode hôte. */
    fun localesDeLaMethode(hote: AbstractMethodDeclaration): List<Pair<String, Plage>> =
        locales.filter { it.third === hote }.map { Pair(it.first, it.second) }

    /** Les membres (nom, plage) du type englobant d'une méthode hôte. */
    fun membresDuTypeEnglobant(hote: AbstractMethodDeclaration): List<Pair<String, Plage>> {
        val b = try { hote.binding } catch (_: Exception) { null } as? MethodBinding ?: return emptyList()
        val fqn = fqnCompletDuType(b.declaringClass) ?: return emptyList()
        return membresDeLaClasse(fqn)
    }

    /** Les types (nom, plage) déclarés dans l'unité. */
    fun typesDeLUnite(): List<Pair<String, Plage>> = types
}

/**
 * Le chasseur de déclarations OVERRIDES (phase CONFLITS) : les
 * méthodes d'un sous-type portant le sélecteur et l'arité de la cible —
 * celles qui gardent l'ancien nom quand l'opt-in n'est pas armé.
 *
 * @param selecteur le sélecteur de la méthode renommée.
 * @param nbParametres l'arité de la méthode renommée.
 * @param fqnProprietaire le FQN du type déclarant de la méthode renommée.
 * @param source le texte du fichier chassé.
 */
internal class ChasseurOverrides(
    private val selecteur: String,
    private val nbParametres: Int,
    private val fqnProprietaire: String,
    private val source: String,
) : LectureAst.VisiteurResilient() {

    /** Les plages des sélecteurs des déclarations outrépassées. */
    val trouvailles = ArrayList<Plage>()

    override fun visiteMethode(node: MethodDeclaration, scope: ClassScope?): Boolean {
        val b = try { node.binding } catch (_: Exception) { null } as? MethodBinding ?: return true
        val nom = try { String(b.selector) } catch (_: Exception) { null } ?: return true
        if (nom != selecteur) return true
        if ((b.parameters?.size ?: 0) != nbParametres) return true
        val declarante = b.declaringClass ?: return true
        val fqn = fqnCompletDuType(declarante) ?: return true
        if (fqn == fqnProprietaire) return true
        if (!estSousTypeFqn(declarante, fqnProprietaire)) return true
        val plage = plageJeton(source, node.sourceStart, node.sourceStart + 120, nom)
        if (plage != null) trouvailles.add(plage)
        return true
    }
}

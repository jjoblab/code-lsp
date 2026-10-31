package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.outils.EditionTexte
import org.eclipse.jdt.internal.compiler.ast.ASTNode
import org.eclipse.jdt.internal.compiler.ast.Assignment
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * CRÉATION DE CHAMP SUR UndefinedField (vague V-D TRANCHE 3, ADR 0027 —
 * « création de champ sur UndefinedField ») : un champ non résolu en
 * position de CHAMP (`this.foo = …`, `x.foo` où le type de `x` est
 * SOURCE) reçoit sa déclaration `private Type foo = <défaut>;` en tête
 * des membres du type DÉCLARANT — qui peut vivre dans un AUTRE fichier
 * (miroir de la référence : Create Field).
 *
 * Le TYPE est inféré de l'AFFECTATION quand la référence en est la
 * CIBLE (`this.foo = expr` → le type RÉSOLU de `expr` — bindings, pas
 * littéral reniflé) ; sinon `Object` (repli générique honnête). La
 * VALEUR par défaut est compilable (`false`/`0`/`null` — jamais un champ
 * non initialisé).
 *
 * **Refus honnêtes (R1)** : récepteur de type BINAIRE (la déclaration
 * devrait naître dans android.jar ou un jar — pas dans les sources),
 * récepteur non résolu, type déclarant introuvable. L'action voyage
 * DIFFÉRÉE (ADR 0027 (f)) : la liste ne porte que clé + position, les
 * éditions — possiblement dans un AUTRE fichier — se calculent au
 * resolve (la forme multi-fichiers du WorkspaceEdit appartient au
 * client, ADR 0014).
 *
 * L'ID ecj d'UndefinedField (« The field T.foo is undefined », sonde du
 * jar — fiche 065) : 33554502.
 */
internal object CorrecteurCreationChamp {

    /** L'ID ecj d'UndefinedField. */
    private const val ID_UNDEFINED_FIELD: Long = 33554502L

    /** La clé différée du générateur. */
    internal const val CLE_CREER_CHAMP: String = "quickfix.creerChamp"

    /**
     * L'action « Créer le champ » d'un diagnostic UndefinedField (servie
     * à la LISTE — différée, clé + position ; les refus se paient au
     * resolve, rien d'eager).
     *
     * @param diagnostic le diagnostic UndefinedField RÉEL du moteur.
     * @return l'action, ou null (autre famille).
     */
    internal fun action(diagnostic: Diagnostic): ActionCode? {
        if (diagnostic.code?.removePrefix("ecj:")?.toLongOrNull() != ID_UNDEFINED_FIELD) return null
        val finNom = diagnostic.plage.finOffset
        val debutNom = diagnostic.plage.debutOffset
        if (finNom <= debutNom) return null
        val nom = diagnostic.arguments.firstOrNull() ?: ""
        return ActionCode(
            kind = "quickfix.create.field",
            titre = if (nom.isEmpty()) "Créer le champ" else "Créer le champ « $nom »",
            commande = ModCommande.Differee(CLE_CREER_CHAMP, listOf(debutNom.toString())),
            diagnostic = diagnostic,
        )
    }

    /**
     * Les éditions de la création (servies au RESOLVE) : la déclaration
     * insérée en tête des membres du type DÉCLARANT — dans SON fichier
     * (le récepteur peut mener hors de l'unité courante ; `this.foo` et
     * `x.foo` avec x du type ENGLOBANT restent dans le fichier courant,
     * sur la source DONNÉE — l'overlay tampon).
     *
     * @param arbre l'arbre RÉSOLU de l'unité courante.
     * @param source la source Java complète de l'unité courante.
     * @param cheminCourant le chemin DISQUE de l'unité courante.
     * @param resoudre résout une unité (chemin logique, texte) en arbre bindé.
     * @param localiser localise le fichier source d'un FQN.
     * @param debutNom le début du jeton du champ non résolu.
     * @return les éditions par fichier, ou null (refus honnête).
     */
    internal fun editions(
        arbre: CompilationUnitDeclaration?,
        source: String,
        cheminCourant: String,
        resoudre: (String, String) -> CompilationUnitDeclaration?,
        localiser: (String) -> Path?,
        debutNom: Int,
    ): Map<String, List<EditionTexte>>? {
        val collecteur = ReferenceChamp(debutNom)
        LectureAst.traverserResilient(arbre ?: return null, collecteur)
        val reference = collecteur.reference ?: return null
        val nom = reference.nom ?: return null

        // Le type DÉCLARANT : le type du RÉCEPTEUR (this → le type
        // englobant ; x → le type résolu de x ; X.foo statique → X).
        val fqnDeclarant = reference.fqnRecepteur() ?: return null

        // Le type déclarant vit-il dans l'UNITÉ COURANTE (overlay) ? Le
        // collecteur ne voit que les types DÉCLARÉS de cet arbre.
        val typeCourant = TypeDeFqn(fqnDeclarant)
        LectureAst.traverserResilient(arbre, typeCourant)
        val typeLocal = typeCourant.trouve
        val (fichierCible, texteCible, arbreCible, typeDecl) = if (typeLocal != null) {
            Quadruplet(cheminCourant, source, arbre, typeLocal)
        } else {
            val fichier = localiser(fqnDeclarant)?.toString() ?: return null
            val texte = try {
                Path.of(fichier).readText()
            } catch (_: Exception) {
                return null
            }
            val arbreFichier = runCatching { resoudre(fichier, texte) }.getOrNull() ?: return null
            val collecteurDistant = TypeDeFqn(fqnDeclarant)
            LectureAst.traverserResilient(arbreFichier, collecteurDistant)
            val type = collecteurDistant.trouve ?: return null
            Quadruplet(fichier, texte, arbreFichier, type)
        }

        // Le TYPE inféré : la valeur affectée à la référence, sinon Object.
        val type = reference.typeAffecte() ?: "java.lang.Object"
        val valeur = valeurParDefaut(type)

        // La VISIBILITÉ (mesuré au banc : `private` depuis un AUTRE type ne
        // compile pas — « The field T.f is not visible ») : privé dans le
        // type de l'UNITÉ COURANTE ; sinon package (même paquet) ou public
        // (paquet différent) — miroir du Create Field de la référence.
        val modificateur = if (typeLocal != null) {
            "private"
        } else {
            val paquetCourant = nomPaquet(arbre)
            val paquetCible = nomPaquet(arbreCible)
            if (paquetCourant == paquetCible) "" else "public"
        }

        // L'ANCRE : début de ligne du PREMIER MEMBRE RÉEL (le constructeur
        // par défaut synthétique ne compte jamais — mesuré V-D2) ; corps
        // vide : juste après l'accolade ouvrante, ligne propre.
        val ancre = ancreMembre(arbreCible, typeDecl, texteCible) ?: return null
        val indentation = indentationDe(ancre, texteCible)
        val declaration = StringBuilder(indentation).apply {
            if (modificateur.isNotEmpty()) {
                append(modificateur)
                append(' ')
            }
            append(type)
            append(' ')
            append(nom)
            append(" = ")
            append(valeur)
            append(";\n")
        }.toString()
        return mapOf(fichierCible to listOf(EditionTexte(ancre, 0, declaration)))
    }

    /** Le nom du PAQUET d'une unité (chaîne vide pour le défaut). */
    private fun nomPaquet(arbre: CompilationUnitDeclaration): String = runCatching {
        arbre.currentPackage?.tokens?.joinToString(".") { String(it) } ?: ""
    }.getOrDefault("")

    /** Un quadruplet interne (Kotlin n'en a pas de standard). */
    private data class Quadruplet<A, B, C, D>(val premier: A, val second: B, val troisieme: C, val quatrieme: D)

    // ------------------------------------------------------------------ //
    // La référence au champ non résolu
    // ------------------------------------------------------------------ //

    /**
     * La référence de champ couvrant l'offset : `this.foo` (FieldReference),
     * `x.foo` (FieldReference ou QualifiedNameReference — le DERNIER
     * segment porte le champ, le premier le récepteur, convention ecj).
     */
    private class ReferenceChamp(private val offset: Int) : LectureAst.VisiteurResilient() {
        var reference: Info? = null
        private val affectations = HashMap<IntRange, Expression>()

        override fun visiteAffectation(node: Assignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            runCatching {
                val cible = node.lhs
                val valeur = node.expression
                if (cible != null && valeur != null) {
                    affectations[cible.sourceStart..cible.sourceEnd] = valeur
                }
            }
            return true
        }

        override fun visiteReferenceChamp(node: FieldReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val jetonDebut = runCatching { node.sourceStart }.getOrDefault(-1)
            val jetonFin = runCatching { node.sourceEnd }.getOrDefault(-1)
            if (jetonDebut < 0 || jetonFin < jetonDebut) return true
            if (offset < jetonDebut || offset > jetonFin) return true
            if (reference == null) {
                reference = Info(
                    nom = runCatching { String(node.token) }.getOrNull(),
                    noeudDebut = jetonDebut,
                    noeudFin = jetonFin,
                    recepteurType = { runCatching { node.receiver?.resolvedType }.getOrNull() },
                    bindingRecepteur = null,
                    affectations = affectations,
                )
            }
            return true
        }

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val positions = runCatching { node.sourcePositions }.getOrNull() ?: return true
            if (positions.isEmpty()) return true
            val pDernier = positions.last()
            val jetonDebut = (pDernier ushr 32).toInt()
            val jetonFin = pDernier.toInt()
            if (offset < jetonDebut || offset > jetonFin) return true
            if (reference == null) {
                reference = Info(
                    nom = runCatching { node.tokens?.lastOrNull()?.let { String(it) } }.getOrNull(),
                    noeudDebut = node.sourceStart,
                    noeudFin = node.sourceEnd,
                    recepteurType = { null },
                    bindingRecepteur = runCatching { node.binding }.getOrNull(),
                    affectations = affectations,
                )
            }
            return true
        }
    }

    /** Les informations de la référence retenue. */
    private class Info(
        val nom: String?,
        private val noeudDebut: Int,
        private val noeudFin: Int,
        /** Le type résolu du récepteur (FieldReference — `this.foo`, `x.foo`). */
        private val recepteurType: () -> TypeBinding?,
        /** Le binding du récepteur (QualifiedNameReference — segment 0). */
        private val bindingRecepteur: org.eclipse.jdt.internal.compiler.lookup.Binding?,
        private val affectations: Map<IntRange, Expression>,
    ) {
        /** Le FQN du type du RÉCEPTEUR (le type déclarant du champ à créer). */
        fun fqnRecepteur(): String? = runCatching {
            recepteurType()?.let { fqnDeType(it) } ?: when (val b = bindingRecepteur) {
                is LocalVariableBinding -> fqnDeType(b.type)
                is FieldBinding -> fqnDeType(b.type)
                is ReferenceBinding -> fqnDeType(b)
                else -> null
            }
        }.getOrNull()

        /** Le type RÉSOLU de la valeur affectée à la référence (bindings). */
        fun typeAffecte(): String? {
            val expression = affectations[noeudDebut..noeudFin]
                ?: affectations.entries.firstOrNull { it.key.first <= noeudDebut && it.key.last >= noeudFin }?.value
                ?: return null
            return runCatching {
                expression.resolvedType?.readableName()?.let { String(it) }
            }.getOrNull()
        }
    }

    /** Le FQN source d'un TypeBinding. */
    private fun fqnDeType(type: TypeBinding): String? = runCatching {
        (type as? ReferenceBinding)?.compoundName?.joinToString(".") { String(it) }
    }.getOrNull()

    // ------------------------------------------------------------------ //
    // Outils
    // ------------------------------------------------------------------ //

    /** La valeur par défaut compilable d'un type (miroir V-A). */
    private fun valeurParDefaut(type: String): String = when (type) {
        "boolean" -> "false"
        "byte", "short", "int", "long", "char", "float", "double" -> "0"
        else -> "null"
    }

    /** L'indentation de la ligne portant l'offset. */
    private fun indentationDe(offset: Int, texte: String): String {
        var debut = offset
        while (debut > 0 && texte[debut - 1] != '\n') debut--
        var fin = debut
        while (fin < texte.length && (texte[fin] == ' ' || texte[fin] == '\t')) fin++
        return texte.substring(debut, fin)
    }

    /**
     * L'ancre d'insertion des membres (miroir tranche 1/2) : DÉBUT DE
     * LIGNE du premier membre RÉEL (champ ou méthode — le constructeur
     * PAR DÉFAUT synthétique ne compte JAMAIS, filtre
     * `declarationSourceStart >= bodyStart`).
     */
    private fun ancreMembre(arbre: CompilationUnitDeclaration, type: TypeDeclaration, texte: String): Int? {
        val collecteur = MembresDeType(type)
        LectureAst.traverserResilient(arbre, collecteur)
        val bodyStart = runCatching { type.bodyStart }.getOrDefault(-1)
        val membres = collecteur.membres.filter {
            bodyStart < 0 || debutDeclaration(it) >= bodyStart
        }
        val premier = membres.minByOrNull { debutDeclaration(it) }
        if (premier != null) {
            var debut = debutDeclaration(premier)
            if (debut < 0) return null
            while (debut > 0 && texte[debut - 1] != '\n') debut--
            return debut
        }
        // Corps vide : juste après l'accolade ouvrante, ligne propre.
        val finLigne = texte.indexOf('\n', runCatching { type.bodyStart }.getOrDefault(texte.length))
        return if (finLigne < 0) texte.length else finLigne + 1
    }

    /** Le début de DÉCLARATION d'un nœud (bornes complètes quand elles existent). */
    private fun debutDeclaration(n: ASTNode): Int = runCatching {
        when (n) {
            is FieldDeclaration -> n.declarationSourceStart
            is org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration -> n.declarationSourceStart
            is TypeDeclaration -> n.declarationSourceStart
            else -> n.sourceStart
        }
    }.getOrDefault(-1)

    /** Les membres DÉCLARÉS d'un type (champs et méthodes). */
    private class MembresDeType(private val cible: TypeDeclaration) : LectureAst.VisiteurResilient() {
        val membres = ArrayList<ASTNode>()

        override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
            if (couvreMembre(cible, node)) membres.add(node)
            return true
        }

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            if (couvreMembre(cible, node)) membres.add(node)
            return true
        }

        private fun couvreMembre(grand: ASTNode, petit: ASTNode): Boolean {
            if (grand === petit) return false
            // bornes STRICTES (ASTNode) : le type couvre le membre déclaré.
            val gd = runCatching { grand.sourceStart }.getOrDefault(-1)
            val gf = runCatching { grand.sourceEnd }.getOrDefault(-1)
            val pd = runCatching { petit.sourceStart }.getOrDefault(-1)
            val pf = runCatching { petit.sourceEnd }.getOrDefault(-1)
            return gd >= 0 && gf >= gd && gd <= pd && gf >= pf
        }
    }

    /** Le type DÉCLARÉ portant un FQN dans l'unité. */
    private class TypeDeFqn(private val fqn: String) : LectureAst.VisiteurResilient() {
        var trouve: TypeDeclaration? = null

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            if (trouve != null) return true
            val b = runCatching { node.binding as? ReferenceBinding }.getOrNull()
            val fqnLocal = runCatching { b?.compoundName?.joinToString(".") { String(it) } }.getOrNull()
            if (fqnLocal == fqn) trouve = node
            return true
        }
    }
}

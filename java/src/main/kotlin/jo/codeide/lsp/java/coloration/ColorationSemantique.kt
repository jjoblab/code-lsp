package jo.codeide.lsp.java.coloration

import org.eclipse.jdt.internal.compiler.ast.Argument
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import jo.codeide.lsp.java.interne.LectureAst

/**
 * Le genre d'un jeton sémantique (vague V-G — ADR 0031 (a),
 * `textDocument/semanticTokens`) : les CATÉGORIES de la légende du
 * serveur — types (classe/interface/énumération), annotation, méthode,
 * champ, paramètre, locale. Les noms suivent les types STANDARD de la
 * légende LSP (voir la légende côté protocole : `class`, `interface`,
 * `enum`, `decorator`, `method`, `property`, `parameter`, `variable`).
 */
public enum class GenreJeton {
    /** Déclaration ou référence d'une CLASSE. */
    CLASSE,

    /** Déclaration ou référence d'une INTERFACE. */
    INTERFACE,

    /** Déclaration ou référence d'une ÉNUMÉRATION. */
    ENUM,

    /** Usage d'une ANNOTATION (`@Override`, `@Foo(…)`) — `decorator` LSP. */
    ANNOTATION,

    /** Déclaration ou référence d'une MÉTHODE (invocation, constructeur). */
    METHODE,

    /** Déclaration ou référence d'un CHAMP (`property` LSP). */
    PROPRIETE,

    /** Déclaration ou référence d'un PARAMÈTRE de méthode. */
    PARAMETRE,

    /** Déclaration ou référence d'une VARIABLE LOCALE. */
    VARIABLE,
}

/**
 * Un jeton sémantique rendu par le moteur (V-G — ADR 0031 (a)) : le
 * couple (position, nature) que le protocole encode en deltas relatifs.
 *
 * La position est en OFFSETS ABSOLUS du texte source (convention maison
 * moteur — la conversion ligne/colonne UTF-16 est le travail du repère
 * côté serveur).
 *
 * @property offset le début du jeton (inclusif).
 * @property longueur la longueur du jeton (le NOM seul, jamais le `.` ni
 *   les arguments — la coloration sémantique colore des IDENTIFIANTS).
 * @property genre la catégorie (voir [GenreJeton]).
 * @property declaration vrai ssi ce jeton EST la déclaration (le nom
 *   introduit) — modificateur standard `declaration` de la légende.
 * @property statique le modificateur `static` (membre ou méthode).
 * @property final le modificateur `final` (membre, méthode ou type).
 * @property abstrait le modificateur `abstract` (méthode ou type).
 */
public class JetonSemantique(
    public val offset: Int,
    public val longueur: Int,
    public val genre: GenreJeton,
    public val declaration: Boolean,
    public val statique: Boolean,
    public val final: Boolean,
    public val abstrait: Boolean,
)

/**
 * Le calcul des JETONS SÉMANTIQUES sur l'arbre RÉSOLU (V-G — ADR 0031
 * (a) : « calcul sur bindings ») : la traversée résiliente de l'arbre
 * (`VisiteurResilient` — un AST en recovery ne fait jamais échouer la
 * coloration) classe chaque identifiant PORTANT par son BINDING :
 *
 *  - une référence dont le binding est un [ReferenceBinding] est un
 *    TYPE (classe/interface/énumération — annotation si le binding est
 *    un type d'annotation) ;
 *  - un envoi/allocation dont le binding est un [MethodBinding] est
 *    une MÉTHODE (constructeur compris — le jeton couvre le nom du
 *    type, sélecteur lisible, miroir V-D2) ;
 *  - une référence dont le binding est un [FieldBinding] est un CHAMP ;
 *  - une référence locale est PARAMÈTRE (sa déclaration est un
 *    [Argument]) ou VARIABLE (sinon) — la hiérarchie ecj route les
 *    déclarations vers `visiteArgument`/`visiteLocale` (même discipline
 *    que Assignment/CompoundAssignment, V-E).
 *
 * **Honnêtetés** :
 *  - les IMPORTS ne sont pas jetonisés (la coloration d'import relève
 *    du langage, pas de la sémantique) ;
 *  - les MOTS-CLÉS, littéraux et commentaires ne le sont pas (la
 *    coloration syntaxique du client les couvre — la valeur ajoutée
 *    ici est la SÉMANTIQUE : membre vs type vs variable) ;
 *  - un nœud sans binding exploitable est IGNORÉ (silence honnête —
 *    une tokenisation partielle vaut mieux qu'une couleur fausse) ;
 *  - le genre d'une référence de TYPE vient du BINDING résolu
 *    (`resolvedType`) quand il est lisible, CLASSE sinon (référence en
 *    recovery).
 */
public object ColorationSemantique {

    /**
     * Calcule les jetons sémantiques d'un arbre résolu.
     *
     * @param arbre l'arbre RÉSOLU (bindings vivants — `resolveUnit`).
     * @param source le texte Java complet.
     * @return les jetons TRIÉS par offset croissant, DÉDOU BLONNÉS (un
     *   même identifiant ne produit qu'un jeton — la déclaration, plus
     *   riche, gagne la fusion).
     */
    public fun jetons(arbre: CompilationUnitDeclaration?, source: String): List<JetonSemantique> {
        if (arbre == null) return emptyList()
        val collecteur = Collecteur(source)
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.rassembler()
    }

    /** Le collecteur : accumule, fusionne et trie les jetons. */
    private class Collecteur(private val source: String) : LectureAst.VisiteurResilient() {

        /** Les jetons par offset — fusion des visites concurrentes d'un même identifiant. */
        private val parPlage = HashMap<Int, JetonSemantique>()

        /** Cherche le MOT aux bornes de mots dans [debut, limite] du texte. */
        private fun plageJeton(debut: Int, limite: Int, mot: String): Int? {
            val borne = limite.coerceAtMost(source.length)
            if (debut >= borne || debut < 0) return null
            val tete = source.substring(debut, borne)
            var i = 0
            while (i < tete.length) {
                if (tete[i].isJavaIdentifierStart() && tete.regionMatches(i, mot, 0, mot.length)) {
                    val apres = i + mot.length
                    val borneAvant = i == 0 || !tete[i - 1].isJavaIdentifierPart()
                    val borneApres = apres >= tete.length || !tete[apres].isJavaIdentifierPart()
                    if (borneAvant && borneApres) return debut + i
                }
                i++
            }
            return null
        }

        /** Le début du DERNIER identifiant de [debut, limite) (segment final d'un nom qualifié). */
        private fun dernierIdentifiant(debut: Int, limite: Int): Int? {
            val borne = limite.coerceAtMost(source.length)
            var fin = borne - 1
            while (fin >= debut && !source[fin].isJavaIdentifierPart()) fin--
            if (fin < debut) return null
            var debutMot = fin
            while (debutMot > debut && source[debutMot - 1].isJavaIdentifierPart()) debutMot--
            return debutMot
        }

        /** Retient un jeton (fusion : une DÉCLARATION remplace une référence du même offset). */
        private fun retenir(
            offset: Int,
            longueur: Int,
            genre: GenreJeton,
            declaration: Boolean,
            statique: Boolean = false,
            final: Boolean = false,
            abstrait: Boolean = false,
        ) {
            if (longueur <= 0 || offset < 0) return
            val existant = parPlage[offset]
            if (existant == null || (declaration && !existant.declaration)) {
                parPlage[offset] = JetonSemantique(offset, longueur, genre, declaration, statique, final, abstrait)
            }
        }

        /** Le genre d'un type binding (annotation d'abord — c'est aussi un type). */
        private fun genreDe(type: ReferenceBinding?): GenreJeton? = try {
            when {
                type == null -> null
                type.isAnnotationType() -> GenreJeton.ANNOTATION
                type.isEnum() -> GenreJeton.ENUM
                type.isInterface() -> GenreJeton.INTERFACE
                else -> GenreJeton.CLASSE
            }
        } catch (_: Exception) {
            null
        }

        /** Le genre d'une DÉCLARATION par ses modificateurs (style maison — InspectionsPilotes). */
        private fun genreDeclaration(modificateurs: Int): GenreJeton = when {
            modificateurs and ClassFileConstants.AccAnnotation != 0 -> GenreJeton.ANNOTATION
            modificateurs and ClassFileConstants.AccEnum != 0 -> GenreJeton.ENUM
            modificateurs and ClassFileConstants.AccInterface != 0 -> GenreJeton.INTERFACE
            else -> GenreJeton.CLASSE
        }

        // --- déclarations ------------------------------------------------ //

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            val nom = try { String(node.name) } catch (_: Exception) { null } ?: return true
            val modificateurs = try { node.modifiers } catch (_: Exception) { 0 }
            val debut = plageJeton(node.sourceStart, node.sourceEnd + 1, nom)
            if (debut != null) {
                retenir(
                    debut, nom.length, genreDeclaration(modificateurs), declaration = true,
                    statique = modificateurs and ClassFileConstants.AccStatic != 0,
                    final = modificateurs and ClassFileConstants.AccFinal != 0,
                    abstrait = modificateurs and ClassFileConstants.AccAbstract != 0,
                )
            }
            return true
        }

        override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            val nom = try { String(node.selector) } catch (_: Exception) { null } ?: return true
            val modificateurs = try { node.binding?.modifiers ?: node.modifiers } catch (_: Exception) { node.modifiers }
            val debut = plageJeton(node.sourceStart, node.sourceEnd + 1, nom)
            if (debut != null) {
                retenir(
                    debut, nom.length, GenreJeton.METHODE, declaration = true,
                    statique = modificateurs and ClassFileConstants.AccStatic != 0,
                    final = modificateurs and ClassFileConstants.AccFinal != 0,
                    abstrait = modificateurs and ClassFileConstants.AccAbstract != 0,
                )
            }
            return true
        }

        override fun visiteConstructeur(node: org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            val nom = try { String(node.selector) } catch (_: Exception) { null } ?: return true
            val debut = plageJeton(node.sourceStart, node.sourceEnd + 1, nom)
            if (debut != null) retenir(debut, nom.length, GenreJeton.METHODE, declaration = true)
            return true
        }

        override fun visiteChamp(node: org.eclipse.jdt.internal.compiler.ast.FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
            val nom = try { String(node.name) } catch (_: Exception) { null } ?: return true
            val modificateurs = try { node.binding?.modifiers ?: node.modifiers } catch (_: Exception) { node.modifiers }
            val debut = plageJeton(node.sourceStart, node.sourceEnd + 1, nom)
            if (debut != null) {
                retenir(
                    debut, nom.length, GenreJeton.PROPRIETE, declaration = true,
                    statique = modificateurs and ClassFileConstants.AccStatic != 0,
                    final = modificateurs and ClassFileConstants.AccFinal != 0,
                )
            }
            return true
        }

        override fun visiteArgument(node: Argument, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val nom = try { String(node.name) } catch (_: Exception) { null } ?: return true
            val debut = plageJeton(node.sourceStart, node.sourceEnd + 1, nom)
            if (debut != null) retenir(debut, nom.length, GenreJeton.PARAMETRE, declaration = true)
            return true
        }

        override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val nom = try { String(node.name) } catch (_: Exception) { null } ?: return true
            val debut = plageJeton(node.sourceStart, node.sourceEnd + 1, nom)
            if (debut != null) retenir(debut, nom.length, GenreJeton.VARIABLE, declaration = true)
            return true
        }

        // --- références -------------------------------------------------- //

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null } as? MethodBinding ?: return true
            val nom = try { String(b.selector) } catch (_: Exception) { null } ?: return true
            val debut = plageJeton(node.sourceStart, node.sourceEnd + 1, nom) ?: return true
            retenir(
                debut, nom.length, GenreJeton.METHODE, declaration = false,
                statique = try { b.modifiers and ClassFileConstants.AccStatic != 0 } catch (_: Exception) { false },
            )
            return true
        }

        override fun visiteAllocation(node: org.eclipse.jdt.internal.compiler.ast.AllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null } as? MethodBinding ?: return true
            // Le sélecteur LISIBLE d'un constructeur : le NOM DU TYPE
            // (le sélecteur brut est `<init>` — mesuré V-D2).
            val nom = try {
                if (b.isConstructor()) String(b.declaringClass.compoundName.last()) else String(b.selector)
            } catch (_: Exception) {
                null
            } ?: return true
            val debutType = try { node.type?.sourceStart } catch (_: Exception) { null } ?: node.sourceStart
            val finType = try { node.type?.sourceEnd } catch (_: Exception) { null } ?: node.sourceEnd
            val debut = plageJeton(debutType, finType + 1, nom) ?: return true
            retenir(debut, nom.length, GenreJeton.METHODE, declaration = false)
            return true
        }

        override fun visiteReferenceSimple(node: SingleNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null }
            when (b) {
                is LocalVariableBinding -> retenirLocale(b, node.sourceStart, node.sourceEnd)
                is FieldBinding -> retenir(node.sourceStart, node.sourceEnd - node.sourceStart + 1, GenreJeton.PROPRIETE, declaration = false)
                is ReferenceBinding -> retenir(node.sourceStart, node.sourceEnd - node.sourceStart + 1, genreDe(b) ?: return true, declaration = false)
            }
            return true
        }

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val positions = try { node.sourcePositions } catch (_: Exception) { null } ?: return true
            val autres = try { node.otherBindings } catch (_: Exception) { null }
            runCatching {
                val b = node.binding
                val p0 = positions.getOrNull(0) ?: return@runCatching
                val debut = (p0 ushr 32).toInt()
                val fin = p0.toInt()
                when (b) {
                    is LocalVariableBinding -> retenirLocale(b, debut, fin)
                    is FieldBinding -> retenir(debut, fin - debut + 1, GenreJeton.PROPRIETE, declaration = false)
                    is ReferenceBinding -> genreDe(b)?.let { retenir(debut, fin - debut + 1, it, declaration = false) }
                }
            }
            for (i in 1 until positions.size) {
                val b = autres?.getOrNull(i - 1) as? FieldBinding ?: continue
                runCatching {
                    val p = positions[i]
                    val debut = (p ushr 32).toInt()
                    retenir(debut, p.toInt() - debut + 1, GenreJeton.PROPRIETE, declaration = false)
                }
            }
            return true
        }

        override fun visiteReferenceChamp(node: org.eclipse.jdt.internal.compiler.ast.FieldReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null } as? FieldBinding ?: return true
            val nom = try { String(node.token) } catch (_: Exception) { null } ?: return true
            val debut = plageJeton(node.sourceStart, node.sourceEnd + 1, nom) ?: return true
            retenir(
                debut, nom.length, GenreJeton.PROPRIETE, declaration = false,
                statique = try { b.modifiers and ClassFileConstants.AccStatic != 0 } catch (_: Exception) { false },
            )
            return true
        }

        /** La référence de méthode `X::m` — surcharge DIRECTE (pas de pont résilient dédié). */
        override fun visit(node: ReferenceExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean = try {
            val nom = String(node.selector)
            retenir(node.nameSourceStart, nom.length, GenreJeton.METHODE, declaration = false)
            true
        } catch (_: Exception) {
            true
        }

        // --- références de TYPES : surcharges directes, protégées à la main --- //

        override fun visit(node: SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protegeType { retenirType(node.resolvedType, node.sourceStart, node.sourceEnd) }

        override fun visit(node: SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protegeType { retenirType(node.resolvedType, node.sourceStart, node.sourceEnd) }

        override fun visit(node: QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protegeType { retenirType(node.resolvedType, node.sourceStart, node.sourceEnd) }

        override fun visit(node: QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protegeType { retenirType(node.resolvedType, node.sourceStart, node.sourceEnd) }

        override fun visit(node: ParameterizedSingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protegeType { retenirType(node.resolvedType, node.sourceStart, node.sourceEnd) }

        override fun visit(node: ParameterizedSingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protegeType { retenirType(node.resolvedType, node.sourceStart, node.sourceEnd) }

        override fun visit(node: ParameterizedQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protegeType { retenirType(node.resolvedType, node.sourceStart, node.sourceEnd) }

        override fun visit(node: ParameterizedQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protegeType { retenirType(node.resolvedType, node.sourceStart, node.sourceEnd) }

        /** Protection maison des visites de types (miroir du cadre résilient). */
        private inline fun protegeType(corps: () -> Unit): Boolean = try {
            corps()
            true
        } catch (_: Exception) {
            true // nœud fautif : continuer sans descendre davantage
        }

        /**
         * Retient une référence de TYPE : le jeton couvre le DERNIER
         * segment (le nom simple — `java.util.List` ne colore que `List`)
         * et le genre vient du BINDING résolu quand il est lisible.
         */
        private fun retenirType(type: org.eclipse.jdt.internal.compiler.lookup.TypeBinding?, debut: Int, fin: Int) {
            val debutMot = dernierIdentifiant(debut, fin + 1) ?: return
            var finMot = debutMot
            while (finMot < source.length && source[finMot].isJavaIdentifierPart()) finMot++
            val genre = genreDe(type as? ReferenceBinding) ?: GenreJeton.CLASSE
            retenir(debutMot, finMot - debutMot, genre, declaration = false)
        }

        /** Retient une référence LOCALE ou PARAMÈTRE par son binding. */
        private fun retenirLocale(b: LocalVariableBinding, debut: Int, fin: Int) {
            val estParametre = try { b.declaration is Argument } catch (_: Exception) { false }
            retenir(debut, fin - debut + 1, if (estParametre) GenreJeton.PARAMETRE else GenreJeton.VARIABLE, declaration = false)
        }

        /** Le tri final : offset croissant, dédoublonné par la fusion. */
        fun rassembler(): List<JetonSemantique> =
            parPlage.values.sortedBy { it.offset }
    }
}

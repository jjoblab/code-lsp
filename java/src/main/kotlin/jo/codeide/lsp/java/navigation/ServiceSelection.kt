package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.outils.LexeurJava
import org.eclipse.jdt.internal.compiler.ASTVisitor
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.Assignment
import org.eclipse.jdt.internal.compiler.ast.BinaryExpression
import org.eclipse.jdt.internal.compiler.ast.Block
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.ForStatement
import org.eclipse.jdt.internal.compiler.ast.IfStatement
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement
import org.eclipse.jdt.internal.compiler.ast.SwitchStatement
import org.eclipse.jdt.internal.compiler.ast.TryStatement
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.ast.WhileStatement
import org.eclipse.jdt.internal.compiler.lookup.BlockScope
import org.eclipse.jdt.internal.compiler.lookup.ClassScope
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope
import org.eclipse.jdt.internal.compiler.lookup.MethodScope

/**
 * Service des chaînes de sélection et de la cible de formatage à la
 * frappe (étage N4 — cahier fiche 027, lot S2-b) : le miroir moteur de
 * `textDocument/selectionRange` et `textDocument/onTypeFormatting`.
 *
 * La chaîne de sélection est STRICTEMENT croissante (contrat LSP 3.17 :
 * chaque niveau parent CONTIENT STRICTEMENT le précédent — croissance
 * stricte de la longueur ET inclusion) et couvre l'escalade complète
 * jeton → expression → instruction → bloc → déclaration → type → unité ;
 * le SOMMET démarre à 0 et couvre toute la source.
 *
 * Tolérances verrouillées par la fiche :
 *  - offsets hors bornes ou source vide → chaîne ABSENTE (jamais une
 *    plage bidon, jamais d'exception) ;
 *  - source IMPARSABLE → chaîne PARTIELLE mais BORNÉE : toutes les plages
 *    restent dans `[0, longueur]` avec `début < fin`.
 *
 * La cible de formatage à la frappe tombe sur la construction qui SE
 * TERMINE au caractère tapé : `}` → la déclaration de méthode entière
 * (l'en-tête et le corps — le nom reste visible dans la cible) ; `;` →
 * l'instruction. Le caractère à l'offset DOIT être le déclencheur, et le
 * déclencheur doit appartenir à la liste supportée — sinon null.
 *
 * @param resoudre résout une unité (chemin logique, texte) en arbre —
 *   sur la source COMPLÈTE (jamais focalisée : la chaîne doit escalader
 *   jusqu'à l'unité entière).
 */
public class ServiceSelection(
    private val resoudre: (String, String) -> CompilationUnitDeclaration?,
) {

    // ------------------------------------------------------------------ //
    // Chaîne de sélection
    // ------------------------------------------------------------------ //

    /**
     * Les chaînes de sélection aux positions demandées (fiche 027 : le
     * miroir de `selectionRange` — une chaîne par position, null pour une
     * position invalide).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java COMPLET.
     * @param offsets les positions interrogées.
     * @return une chaîne par offset (null = offset invalide / source vide).
     */
    public fun chainesSelection(chemin: String, source: String, offsets: List<Int>): List<ChaineSelection?> =
        offsets.map { chaineSelection(chemin, source, it) }

    /**
     * La chaîne de sélection à une position (fiche 027).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée.
     * @return la chaîne strictement croissante (sommet = toute la source),
     *   ou null (offset hors bornes, source vide).
     */
    public fun chaineSelection(chemin: String, source: String, offset: Int): ChaineSelection? {
        if (source.isEmpty()) return null
        if (offset < 0 || offset >= source.length) return null

        // 1. Le JETON lexical couvrant l'offset (premier niveau).
        val jetons = LexeurJava.lexe(source)
        val jeton = jetons?.firstOrNull { offset >= it.debut && offset < it.fin }

        // 2. Les plages structurelles saines couvrant l'offset (AST).
        val structurelles = ArrayList<Plage>()
        val arbre = runCatching { resoudre(chemin, source) }.getOrNull()
        if (arbre != null) {
            val collecteur = VisiteurNiveaux(source, offset)
            runCatching { arbre.traverse(collecteur, arbre.scope) }
            structurelles.addAll(collecteur.niveaux)
        }

        // 3. Assemblage STRICTEMENT croissant : le jeton d'abord, puis
        //    chaque plage structurelle qui CONTIENT STRICTEMENT le sommet
        //    courant, l'unité `[0, longueur]` en dernier.
        val maillons = ArrayList<MaillonSelection>()
        var sommet: Plage? = null
        if (jeton != null) {
            maillons.add(MaillonSelection(Plage(jeton.debut, jeton.fin)))
            sommet = Plage(jeton.debut, jeton.fin)
        }
        for (plage in structurelles.sortedBy { it.finOffset - it.debutOffset }) {
            if (sommet == null) {
                maillons.add(MaillonSelection(plage))
                sommet = plage
                continue
            }
            if (contientStrictement(plage, sommet)) {
                maillons.add(MaillonSelection(plage))
                sommet = plage
            }
        }
        val unite = Plage(0, source.length)
        if (sommet == null || contientStrictement(unite, sommet)) {
            maillons.add(MaillonSelection(unite))
        }
        return ChaineSelection(maillons)
    }

    /** Vrai ssi [grande] contient STRICTEMENT [petite] (longueur croissante + inclusion). */
    private fun contientStrictement(grande: Plage, petite: Plage): Boolean =
        grande.debutOffset <= petite.debutOffset &&
            grande.finOffset >= petite.finOffset &&
            (grande.finOffset - grande.debutOffset) > (petite.finOffset - petite.debutOffset)

    // ------------------------------------------------------------------ //
    // Cible de formatage à la frappe
    // ------------------------------------------------------------------ //

    /**
     * La cible de formatage à la frappe (fiche 027 : le miroir de
     * `onTypeFormatting`) : la construction qui SE TERMINE au caractère
     * tapé.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caractère tapé.
     * @param declencheur le déclencheur demandé (`}` ou `;` — tout autre
     *   caractère n'est pas supporté).
     * @return la plage de la construction cible, ou null (déclencheur hors
     *   liste, caractère à l'offset différent du déclencheur, source
     *   hors bornes).
     */
    public fun plageFormattable(chemin: String, source: String, offset: Int, declencheur: Char): Plage? {
        if (declencheur != '}' && declencheur != ';') return null
        if (offset < 0 || offset >= source.length) return null
        if (source[offset] != declencheur) return null

        val arbre = runCatching { resoudre(chemin, source) }.getOrNull()
        if (arbre != null) {
            // Le nœud le PLUS EXTERIEUR qui se TERMINE sur le caractère
            // tapé : pour `}`, la déclaration de méthode (l'en-tête porte
            // le nom, le corps porte les instructions) ; pour `;`,
            // l'instruction.
            val collecteur = VisiteurFins(offset)
            runCatching { arbre.traverse(collecteur, arbre.scope) }
            val exterieure = collecteur.exterieure
            if (exterieure != null) {
                return Plage(exterieure.debutOffset, exterieure.finOffset)
            }
        }

        // Repli TEXTUEL pour `;` (instruction d'expression — le nœud AST
        // porteur n'est pas visitable) : l'instruction s'étend du premier
        // caractère significatif après la frontière précédente jusqu'au
        // point-virgule INCLUS.
        if (declencheur == ';') {
            var frontiere = 0
            for (i in offset - 1 downTo 0) {
                val c = source[i]
                if (c == ';' || c == '{' || c == '}' || c == '\n') {
                    frontiere = i + 1
                    break
                }
            }
            var debut = frontiere
            while (debut < offset && source[debut].isWhitespace()) debut++
            if (debut < offset) return Plage(debut, offset + 1)
        }
        return null
    }

    // ------------------------------------------------------------------ //
    // Visiteurs des niveaux structurels
    // ------------------------------------------------------------------ //

    /**
     * Collecteur des plages structurelles SAINES couvrant l'offset : les
     * expressions, instructions, blocs et déclarations de la fiche 027 —
     * chaque visite est protégée (source imparsable : les nœuds déjà vus
     * restent, les plages dégénerées sont écartées).
     */
    private inner class VisiteurNiveaux(
        private val source: String,
        private val offset: Int,
    ) : ASTVisitor() {

        val niveaux = ArrayList<Plage>()

        private fun retenir(node: org.eclipse.jdt.internal.compiler.ast.ASTNode) {
            val debut = try { node.sourceStart } catch (_: Exception) { return }
            val fin = try { node.sourceEnd } catch (_: Exception) { return }
            retenirPlage(debut, fin)
        }

        /** Retient une DÉCLARATION sur ses bornes COMPLÈTES (modificateurs et « ; »/« } » compris). */
        private fun retenirDeclaration(debut: Int, fin: Int, repli: org.eclipse.jdt.internal.compiler.ast.ASTNode) {
            if (debut >= 0 && fin >= debut) {
                retenirPlage(debut, fin)
            } else {
                retenir(repli)
            }
        }

        private fun retenirPlage(debut: Int, fin: Int) {
            if (debut < 0 || fin < debut) return
            if (fin + 1 > source.length) return
            if (offset < debut || offset > fin) return
            niveaux.add(Plage(debut, fin + 1))
        }

        override fun visit(node: BinaryExpression, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: Assignment, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: MessageSend, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: AllocationExpression, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: ReturnStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: LocalDeclaration, scope: BlockScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd, node) }
        override fun visit(node: Block, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: IfStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: ForStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: WhileStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: TryStatement, scope: BlockScope?):Boolean = protege { retenir(node) }
        override fun visit(node: SwitchStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: FieldDeclaration, scope: MethodScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd, node) }
        override fun visit(node: MethodDeclaration, scope: ClassScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd, node) }
        override fun visit(node: TypeDeclaration, scope: ClassScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd, node) }
        override fun visit(node: TypeDeclaration, scope: CompilationUnitScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd, node) }

        private fun protege(corps: () -> Unit): Boolean = try {
            corps()
            true
        } catch (_: Exception) {
            true // nœud fautif : descendre quand même
        }
    }

    /** Collecteur du nœud le plus EXTERIEUR se terminant sur l'offset. */
    private inner class VisiteurFins(private val offset: Int) : ASTVisitor() {

        var exterieure: Plage? = null
            private set

        private fun retenir(node: org.eclipse.jdt.internal.compiler.ast.ASTNode) {
            val debut = try { node.sourceStart } catch (_: Exception) { return }
            val fin = try { node.sourceEnd } catch (_: Exception) { return }
            retenirPlage(debut, fin)
        }

        /** Retient une DÉCLARATION se terminant sur l'offset (bornes complètes : « ; »/« } » compris). */
        private fun retenirDeclaration(debut: Int, fin: Int) {
            if (debut >= 0 && fin >= debut) {
                retenirPlage(debut, fin)
            }
        }

        private fun retenirPlage(debut: Int, fin: Int) {
            if (fin != offset || debut < 0 || fin < debut) return
            val courante = exterieure
            if (courante == null || debut < courante.debutOffset) {
                exterieure = Plage(debut, fin + 1)
            }
        }

        override fun visit(node: BinaryExpression, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: Assignment, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: MessageSend, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: AllocationExpression, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: ReturnStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: LocalDeclaration, scope: BlockScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd) }
        override fun visit(node: Block, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: IfStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: ForStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: WhileStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: TryStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: SwitchStatement, scope: BlockScope?): Boolean = protege { retenir(node) }
        override fun visit(node: FieldDeclaration, scope: MethodScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd) }
        override fun visit(node: MethodDeclaration, scope: ClassScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd) }
        override fun visit(node: TypeDeclaration, scope: ClassScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd) }
        override fun visit(node: TypeDeclaration, scope: CompilationUnitScope?): Boolean = protege { retenirDeclaration(node.declarationSourceStart, node.declarationSourceEnd) }

        private fun protege(corps: () -> Unit): Boolean = try {
            corps()
            true
        } catch (_: Exception) {
            true
        }
    }
}

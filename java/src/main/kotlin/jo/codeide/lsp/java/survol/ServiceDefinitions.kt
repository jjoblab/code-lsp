package jo.codeide.lsp.java.survol

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.LectureAst.VisiteurResilient
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding

/**
 * Service des définitions (étage N3 — cahier fiche 020, partie définition) :
 * la plage de la DÉCLARATION visée par la référence au caret.
 *
 * Trois familles couvertes par la fiche :
 *  - variable LOCALE : le binding porte sa déclaration (lien direct ecj) ;
 *  - CHAMP : la déclaration vit dans la même unité (registre) — la plage
 *    vise le nom déclaré ;
 *  - MÉTHODE appelée : la déclaration vit dans la même unité (registre) —
 *    la plage vise le sélecteur déclaré.
 *
 * Les symboles binaires (jrt, jars) ne sont pas localisables par ce service
 * — l'étage N4 (navigation) ouvrira les définitions binaires par le
 * routeur de classpath ; ici, périmètre honnête : null.
 *
 * Conventions de plage (empiriques, fiches 020) : pour une déclaration de
 * variable ou de champ, `sourceStart` ecj pointe le NOM déclaré ; pour une
 * méthode, le type de retour — le sélecteur est retrouvé par balayage du
 * texte de tête de déclaration.
 */
public object ServiceDefinitions {

    /**
     * Plage de définition de la référence au caret.
     *
     * @param arbre l'arbre RÉSOLU (bindings vivants).
     * @param source le texte source dans le système de l'arbre.
     * @param offset la position interrogée.
     * @return la plage du NOM déclaré, ou null (référence non résolue, ou
     *   déclaration hors de l'unité — binaire).
     */
    public fun definition(arbre: CompilationUnitDeclaration?, source: String, offset: Int): Plage? {
        if (arbre == null) return null
        val registre = RegistreChampsEtMethodes()
        LectureAst.traverserResilient(arbre, registre)
        val collecteur = CollecteurReferences(offset, source, registre)
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.meilleure
    }

    /**
     * Registre des déclarations de champs et de méthodes de l'unité — les
     * définitions locales/champs/méthodes de la fiche 020 vivent toutes
     * dans l'unité courante.
     */
    private class RegistreChampsEtMethodes : VisiteurResilient() {
        val champs = HashMap<org.eclipse.jdt.internal.compiler.lookup.FieldBinding, org.eclipse.jdt.internal.compiler.ast.FieldDeclaration>()
        val methodes = HashMap<org.eclipse.jdt.internal.compiler.lookup.MethodBinding, org.eclipse.jdt.internal.compiler.ast.MethodDeclaration>()

        override fun visiteChamp(node: org.eclipse.jdt.internal.compiler.ast.FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
            node.binding?.let { champs[it] = node }
            return true
        }

        override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            node.binding?.let { methodes[it] = node }
            return true
        }
    }

    /**
     * Collecteur de la référence la plus profonde couvrant le caret, avec
     * son binding résolu.
     */
    private class CollecteurReferences(
        private val offset: Int,
        private val source: String,
        private val registre: RegistreChampsEtMethodes,
    ) : VisiteurResilient() {
        var meilleure: Plage? = null
        private var tailleMeilleure = Int.MAX_VALUE

        override fun visiteReferenceSimple(node: SingleNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.binding)
            return true
        }

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.binding)
            return true
        }

        override fun visiteReferenceChamp(node: FieldReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.binding)
            return true
        }

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            // Un appel couvre le caret quand il porte sur le sélecteur ou
            // la liste d'arguments — la définition vise la méthode appelée.
            considerer(node.sourceStart, node.sourceEnd, node.binding)
            return true
        }

        /** Retient la référence si elle couvre le caret et est plus serrée. */
        private fun considerer(debut: Int, fin: Int, binding: org.eclipse.jdt.internal.compiler.lookup.Binding?) {
            if (offset < debut || offset > fin) return
            val plage = plageDe(binding) ?: return
            if (fin - debut < tailleMeilleure) {
                meilleure = plage
                tailleMeilleure = fin - debut
            }
        }

        /** Plage du NOM déclaré visé par le binding, ou null. */
        private fun plageDe(binding: org.eclipse.jdt.internal.compiler.lookup.Binding?): Plage? {
            return when (binding) {
                is LocalVariableBinding -> {
                    val declaration = binding.declaration ?: return null
                    Plage(declaration.sourceStart, declaration.sourceStart + declaration.name.size)
                }

                is FieldBinding -> {
                    val declaration = registre.champs[binding] ?: return null
                    Plage(declaration.sourceStart, declaration.sourceStart + declaration.name.size)
                }

                is MethodBinding -> {
                    val declaration = registre.methodes[binding]
                        ?: binding.original()?.let { registre.methodes[it] }
                        ?: return null
                    plageNomMethode(declaration)
                }

                else -> null
            }
        }

        /**
         * Plage du sélecteur d'une déclaration de méthode : balayage du
         * texte de tête (modificateurs et type de retour compris) jusqu'au
         * premier identifiant égal au sélecteur, protégé par des bornes de
         * mots — le sélecteur ne peut pas être confondu avec le type de
         * retour (un type de retour égal au sélecteur serait un mot-clé ou
         * une collision illégale en Java).
         */
        private fun plageNomMethode(declaration: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration): Plage? {
            val selecteur = String(declaration.selector)
            val tete = source.substring(
                declaration.sourceStart,
                (declaration.sourceStart + 120).coerceAtMost(source.length),
            )
            var i = 0
            while (i < tete.length) {
                if (tete[i].isJavaIdentifierStart() && tete.regionMatches(i, selecteur, 0, selecteur.length)) {
                    val apres = i + selecteur.length
                    val borneAvant = i == 0 || !tete[i - 1].isJavaIdentifierPart()
                    val borneApres = apres >= tete.length || !tete[apres].isJavaIdentifierPart()
                    if (borneAvant && borneApres) {
                        return Plage(declaration.sourceStart + i, declaration.sourceStart + apres)
                    }
                }
                i++
            }
            return null
        }
    }
}

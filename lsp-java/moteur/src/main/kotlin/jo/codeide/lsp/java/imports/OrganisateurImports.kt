package jo.codeide.lsp.java.imports

import jo.codeide.lsp.java.gabarits.AncreImport
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.outils.LexeurJava
import jo.codeide.lsp.java.outils.NatureJeton
import org.eclipse.jdt.internal.compiler.ast.ArrayQualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference
import org.eclipse.jdt.internal.compiler.lookup.ArrayBinding
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ProblemReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding

/**
 * Proposition d'organisation des imports (étage N7 — cahier fiche 022) :
 * la liste des FQN à importer, le bloc formaté et la plage de
 * remplacement dans la source originale.
 *
 * @property fqcn les imports retenus, TRIÉS alphabétiquement (l'on-demand
 *   « x.* » n'est jamais décomposé — il couvre son paquet et reste dans
 *   la liste).
 * @property bloc le bloc d'imports formaté — une ligne « import <fqn>; »
 *   par entrée, chacune terminée par un saut de ligne.
 * @property debutRemplacement le début de la plage de remplacement dans
 *   la source originale (point d'insertion zéro-largeur quand il n'y a
 *   pas d'imports existants).
 * @property finRemplacement la fin (exclusive) de la plage de
 *   remplacement — égale au début en insertion pure.
 */
public data class PropositionImports(
    public val fqcn: List<String>,
    public val bloc: String,
    public val debutRemplacement: Int,
    public val finRemplacement: Int,
)

/**
 * Organisateur d'imports (étage N7 — cahier fiche 022) : identification
 * des FQN à importer depuis les BINDINGS (types qualifiés utilisés,
 * superclasse, déclarants des références résolues), règles d'exemption
 * (java.lang, même paquet, paquet couvert par un on-demand existant) et
 * remplacement du bloc d'imports existant par un bloc trié.
 *
 * Tolérance au code cassé : les bindings non résolus (problèmes) sont
 * ignorés — aucun faux import, aucune levée.
 */
public object OrganisateurImports {

    /** Un import existant, scanné textuellement. */
    private data class ImportExistant(
        val texte: String,
        val onDemand: Boolean,
        val statique: Boolean,
        val debut: Int,
        val finSemi: Int,
        val finLigne: Int,
    )

    /**
     * Organise les imports d'une unité RÉSOLUE.
     *
     * @param arbre l'arbre résolu (bindings vivants) — null → null.
     * @param source la source originale (positions du bloc existant).
     * @return la proposition (liste vide si rien à importer — jamais un
     *   faux import), ou null sans arbre.
     */
    public fun organiser(arbre: CompilationUnitDeclaration?, source: String): PropositionImports? {
        if (arbre == null) return null
        val collecteur = CollecteurTypes()
        LectureAst.traverserResilient(arbre, collecteur)

        val paquet = arbre.currentPackage?.tokens?.joinToString(".") { String(it) }
        val importsExistants = scannerImports(source)
        val onDemand = importsExistants.filter { it.onDemand && !it.statique }
        val statiques = importsExistants.filter { it.statique }
        val paquetsCouverts = onDemand.map { it.texte.removeSuffix(".*") }.toSet()

        // Exemptions : java.lang, même paquet, paquet couvert par un on-demand.
        val retenus = collecteur.fqcn.filter { fqn ->
            val paquetFqn = fqn.substringBeforeLast('.')
            !paquetFqn.startsWith("java.lang") &&
                paquetFqn != paquet &&
                paquetFqn !in paquetsCouverts
        }.toSortedSet()

        // L'on-demand et les imports statiques sont préservés tels quels.
        val liste = (retenus + statiques.map { it.texte } + onDemand.map { it.texte }).sorted()

        val bloc = liste.joinToString("") { "import $it;\n" }
        return if (importsExistants.isEmpty()) {
            // Insertion juste après le package (zéro-largeur).
            val ancre = AncreImport.ancre(source)
            PropositionImports(liste, bloc, ancre, ancre)
        } else {
            val premier = importsExistants.first()
            val dernier = importsExistants.last()
            PropositionImports(liste, bloc, premier.debut, dernier.finLigne)
        }
    }

    // ------------------------------------------------------------------ //
    // Balayage textuel du bloc existant
    // ------------------------------------------------------------------ //

    /** Scanne les instructions d'import existantes (positions comprises). */
    private fun scannerImports(source: String): List<ImportExistant> {
        val jetons = LexeurJava.lexe(source) ?: return emptyList()
        val resultat = ArrayList<ImportExistant>()
        var i = 0
        while (i < jetons.size) {
            val jeton = jetons[i]
            if (jeton.texte != "import" || jeton.nature != NatureJeton.MOT_CLE) {
                i++
                continue
            }
            // `import [static] a.b.C[. *] ;`
            var j = i + 1
            var statique = false
            if (j < jetons.size && jetons[j].texte == "static" && jetons[j].nature == NatureJeton.MOT_CLE) {
                statique = true
                j++
            }
            val debutNom = jetons[j].debut
            var finSemi = -1
            while (j < jetons.size && jetons[j].texte != ";") {
                if (jetons[j].nature == NatureJeton.CHAINE || jetons[j].texte == "{") break
                j++
            }
            if (j < jetons.size && jetons[j].texte == ";") finSemi = jetons[j].fin
            if (finSemi < 0) break
            val texte = source.substring(debutNom, finSemi - 1).trim()
            val finLigne = source.indexOf('\n', finSemi - 1).let { if (it < 0) source.length else it + 1 }
            resultat.add(ImportExistant(texte, texte.endsWith(".*"), statique, jeton.debut, finSemi, finLigne))
            i = j + 1
        }
        return resultat
    }

    // ------------------------------------------------------------------ //
    // Collecte des types par bindings
    // ------------------------------------------------------------------ //

    /**
     * Collecteur résilient : les références de TYPE (toutes variantes —
     * simples, qualifiées, paramétrées, tableaux), les déclarants des
     * champs et méthodes référencés — chaque binding résolu contribue son
     * FQN ; les PROBLÈMES sont ignorés (code cassé : aucun faux import).
     */
    private class CollecteurTypes : LectureAst.VisiteurResilient() {

        /** Les FQN collectés (dédupliqués). */
        val fqcn = LinkedHashSet<String>()

        override fun visiteReferenceSimple(
            node: org.eclipse.jdt.internal.compiler.ast.SingleNameReference,
            scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?,
        ): Boolean {
            collecterNom(node.binding)
            return true
        }

        override fun visiteReferenceQualifiee(
            node: org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference,
            scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?,
        ): Boolean {
            collecterNom(node.binding)
            return true
        }

        override fun visiteReferenceChamp(
            node: org.eclipse.jdt.internal.compiler.ast.FieldReference,
            scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?,
        ): Boolean {
            collecterNom(node.binding)
            return true
        }

        override fun visiteEnvoi(
            node: org.eclipse.jdt.internal.compiler.ast.MessageSend,
            scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?,
        ): Boolean {
            collecterNom(node.binding)
            return true
        }

        // ---- Références de type : les douze surcharges ASTVisitor ---- //

        override fun visit(node: SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: ParameterizedSingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: ParameterizedSingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: ParameterizedQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: ParameterizedQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: ArrayTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: ArrayTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: ArrayQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean =
            protege { collecter(node.resolvedType) }

        override fun visit(node: ArrayQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean =
            protege { collecter(node.resolvedType) }

        /** Protege n'est pas visible depuis l'extérieur : repli local. */
        private fun protege(corps: () -> Unit): Boolean = try {
            corps()
            true
        } catch (_: Exception) {
            false
        }

        // ---- Contribution des bindings ---- //

        /** Nom porté par un binding de référence (type, champ, méthode). */
        private fun collecterNom(binding: org.eclipse.jdt.internal.compiler.lookup.Binding?) {
            try {
                when (binding) {
                    is TypeBinding -> collecter(binding)
                    is FieldBinding -> collecter(binding.declaringClass)
                    is MethodBinding -> collecter(binding.declaringClass)
                    else -> {}
                }
            } catch (_: Exception) {
                // Un binding fautif n'contribue rien — résilience.
            }
        }

        /** FQN d'un type résolu — les problèmes et les nus de paquet sont écartés. */
        private fun collecter(binding: TypeBinding?) {
            if (binding == null) return
            var feuille = binding
            while (feuille is ArrayBinding) {
                feuille = feuille.leafComponentType() ?: return
            }
            if (feuille is ProblemReferenceBinding) return
            if (feuille !is ReferenceBinding) return
            val nom = try {
                feuille.compoundName?.joinToString(".") { String(it) }
            } catch (_: Exception) {
                null
            } ?: return
            if ('.' in nom) fqcn.add(nom)
        }
    }
}

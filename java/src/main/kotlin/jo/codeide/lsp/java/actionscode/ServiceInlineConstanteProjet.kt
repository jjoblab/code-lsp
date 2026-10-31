package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.outils.EditionTexte
import org.eclipse.jdt.internal.compiler.ast.BinaryExpression
import org.eclipse.jdt.internal.compiler.ast.CharLiteral
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.DoubleLiteral
import org.eclipse.jdt.internal.compiler.ast.ExtendedStringLiteral
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.internal.compiler.ast.FloatLiteral
import org.eclipse.jdt.internal.compiler.ast.IntLiteral
import org.eclipse.jdt.internal.compiler.ast.LongLiteral
import org.eclipse.jdt.internal.compiler.ast.NullLiteral
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.StringLiteral
import org.eclipse.jdt.internal.compiler.ast.StringLiteralConcatenation
import org.eclipse.jdt.internal.compiler.ast.TrueLiteral
import org.eclipse.jdt.internal.compiler.ast.UnaryExpression
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * INLINE DE CONSTANTE MULTI-FICHIERS (vague V-D TRANCHE 3, ADR 0027 (e)) :
 * la garde mono-fichier de la tranche 2 (« un usage hors du fichier courant
 * ferme la porte honnêtement ») se lève — chaque LECTURE du champ
 * `static final` à initialisateur constant, dans TOUS les fichiers du
 * projet, reçoit la valeur ; la DÉCLARATION quitte son fichier.
 *
 * La correspondance multi-fichiers est par CLÉ DE CHAMP (propriétaire
 * top-niveau + nom — socle ADR 0028, candidats de l'index de mots) ; les
 * références QUALIFIÉES sont remplacées ENTIÈRES (`demo.Main.MAX` devient
 * `42`, miroir du mono-fichier), les références de champ (`this.MAX`)
 * pareil, les simples par leur jeton.
 *
 * Une valeur NÉGATIVE se met entre parenthèses (`X * 2` avec X = -42) ;
 * les lignes repliées de l'initialisateur se recollent (miroir tranche 2).
 *
 * Le calcul vit au RESOLVE (ADR 0027 (f)) : la liste ne paie jamais le
 * balayage.
 *
 * @param arbre l'arbre RÉSOLU de l'unité courante.
 * @param source la source Java complète de l'unité courante.
 * @param cheminCourant le chemin DISQUE de l'unité courante.
 * @param resoudre résout une unité (chemin logique, texte) en arbre bindé.
 * @param fichiersPourMot les candidats de l'index de mots.
 */
internal class ServiceInlineConstanteProjet(
    private val arbre: CompilationUnitDeclaration?,
    private val source: String,
    private val cheminCourant: String,
    private val resoudre: (String, String) -> CompilationUnitDeclaration?,
    private val fichiersPourMot: (String) -> List<Path>,
) {

    /**
     * Les éditions de l'inline PROJET de la constante au caret : les
     * usages remplacés dans chaque fichier + la déclaration supprimée.
     *
     * @param offset le caret (sur le nom du champ).
     * @return les éditions par fichier (vide = rien à faire), ou null si
     *   le caret ne porte pas une constante inlinable (refus honnête).
     */
    internal fun editions(offset: Int): Map<String, List<EditionTexte>>? {
        // Le champ au caret : static final à initialisateur constant.
        val collecteur = ChampsAuCaret(offset)
        LectureAst.traverserResilient(arbre ?: return null, collecteur)
        val champ = collecteur.trouve ?: return null
        if (!estStatiqueFinal(champ)) return null
        val initialisateur = runCatching { champ.initialization }.getOrNull() ?: return null
        if (!estConstante(initialisateur)) return null
        val cle = cleDe(champ) ?: return null

        // La valeur de remplacement : l'initialisateur REPLIÉ (les lignes
        // recollées), parenthésé s'il est unaire (négatif).
        val texteValeur = source.substring(initialisateur.sourceStart, initialisateur.sourceEnd + 1)
            .replace(Regex("\\s*\n\\s*"), " ")
        val remplacement = if (initialisateur is UnaryExpression) "($texteValeur)" else texteValeur

        val editions = LinkedHashMap<String, MutableList<EditionTexte>>()

        // Le FICHIER COURANT d'abord (source donnée — l'overlay tampon).
        run {
            val collecteurUsages = CollecteurUsagesChamp(source, cle)
            LectureAst.traverserResilient(arbre, collecteurUsages)
            for (plage in collecteurUsages.plages) {
                editions.getOrPut(cheminCourant) { mutableListOf() }
                    .add(EditionTexte(plage.debutOffset, plage.finOffset - plage.debutOffset, remplacement))
            }
        }

        // Les AUTRES fichiers : candidats de l'index, texte disque.
        val disqueCourant = Path.of(cheminCourant)
        for (fichier in fichiersPourMot(cle.nom)) {
            if (fichier == disqueCourant) continue // déjà servi (overlay)
            val texte = try {
                fichier.readText()
            } catch (_: Exception) {
                continue
            }
            val arbreFichier = runCatching { resoudre(fichier.toString(), texte) }.getOrNull() ?: continue
            val collecteurUsages = CollecteurUsagesChamp(texte, cle)
            LectureAst.traverserResilient(arbreFichier, collecteurUsages)
            for (plage in collecteurUsages.plages) {
                editions.getOrPut(fichier.toString()) { mutableListOf() }
                    .add(EditionTexte(plage.debutOffset, plage.finOffset - plage.debutOffset, remplacement))
            }
        }

        // La DÉCLARATION quitte son fichier (bornes de ligne avalées).
        val plageDeclaration = plageLigneSupprimable(champ.declarationSourceStart, champ.declarationSourceEnd, source)
        if (plageDeclaration != null) {
            editions.getOrPut(cheminCourant) { mutableListOf() }
                .add(EditionTexte(plageDeclaration.first, plageDeclaration.last + 1 - plageDeclaration.first, ""))
        }
        val resultat = LinkedHashMap<String, List<EditionTexte>>()
        for ((chemin, liste) in editions) resultat[chemin] = liste.sortedBy { it.offset }
        return resultat
    }

    // ------------------------------------------------------------------ //
    // Clef et usages
    // ------------------------------------------------------------------ //

    /** La clé de champ du champ (propriétaire top-niveau + nom). */
    private fun cleDe(champ: FieldDeclaration): jo.codeide.lsp.java.navigation.CleUsages.Champ? {
        val b = runCatching { champ.binding }.getOrNull() ?: return null
        var courant: ReferenceBinding? = runCatching { b.declaringClass }.getOrNull() ?: return null
        runCatching {
            while (courant?.enclosingType() != null) courant = courant.enclosingType()
        }
        val proprietaire = runCatching { courant?.compoundName?.joinToString(".") { String(it) } }.getOrNull()
            ?: return null
        return jo.codeide.lsp.java.navigation.CleUsages.Champ(proprietaire, String(b.name))
    }

    /**
     * Le collecteur des usages d'une clé de champ dans UNE unité résolue :
     * références simples (jeton), QUALIFIÉES (le NŒUD ENTIER —
     * `demo.Main.MAX` devient `42`) et références de champ (`this.MAX`).
     */
    private class CollecteurUsagesChamp(
        private val source: String,
        private val cle: jo.codeide.lsp.java.navigation.CleUsages.Champ,
    ) : LectureAst.VisiteurResilient() {

        val plages = ArrayList<Plage>()

        private fun correspond(b: FieldBinding?): Boolean = runCatching {
            if (b == null) return false
            var courant: ReferenceBinding? = b.declaringClass
            while (courant?.enclosingType() != null) courant = courant.enclosingType()
            val proprietaire = courant?.compoundName?.joinToString(".") { String(it) } ?: return false
            proprietaire == cle.fqnProprietaire && String(b.name) == cle.nom
        }.getOrDefault(false)

        override fun visiteReferenceSimple(node: SingleNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = runCatching { node.binding }.getOrNull()
            if (b is FieldBinding && correspond(b)) {
                plages.add(Plage(node.sourceStart, node.sourceEnd + 1))
            }
            return true
        }

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            // Le NŒUD ENTIER part (le récepteur compris — miroir du
            // mono-fichier : `demo.Main.MAX` devient `42`).
            val segments = ArrayList<org.eclipse.jdt.internal.compiler.lookup.Binding>()
            runCatching { segments.add(node.binding) }
            runCatching {
                for (b in node.otherBindings ?: emptyArray()) segments.add(b)
            }
            if (segments.any { it is FieldBinding && correspond(it) }) {
                plages.add(Plage(node.sourceStart, node.sourceEnd + 1))
            }
            return true
        }

        override fun visiteReferenceChamp(node: FieldReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = runCatching { node.binding }.getOrNull()
            if (b is FieldBinding && correspond(b)) {
                plages.add(Plage(node.sourceStart, node.sourceEnd + 1))
            }
            return true
        }
    }

    /** Le champ (déclaration) au caret — le jeton du nom CHERCHÉ dans les bornes. */
    private inner class ChampsAuCaret(private val offset: Int) : LectureAst.VisiteurResilient() {
        var trouve: FieldDeclaration? = null

        override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
            if (trouve != null) return true
            val nom = String(node.name)
            val plage = jo.codeide.lsp.java.renommage.plageJeton(
                source,
                node.declarationSourceStart,
                node.declarationSourceEnd + 1,
                nom,
            ) ?: return true
            if (offset >= plage.debutOffset && offset < plage.finOffset) trouve = node
            return true
        }
    }

    // ------------------------------------------------------------------ //
    // Miroirs tranche 2
    // ------------------------------------------------------------------ //

    /** Vrai sss le champ est static final (miroir tranche 2). */
    private fun estStatiqueFinal(champ: FieldDeclaration): Boolean = runCatching {
        (champ.modifiers and ClassFileConstants.AccStatic) != 0 &&
            (champ.modifiers and ClassFileConstants.AccFinal) != 0
    }.getOrDefault(false)

    /** Vrai sss l'expression est CONSTANTE (miroir tranche 2). */
    private fun estConstante(expr: org.eclipse.jdt.internal.compiler.ast.Expression): Boolean = when (expr) {
        is IntLiteral, is LongLiteral, is FloatLiteral, is DoubleLiteral, is StringLiteral, is ExtendedStringLiteral,
        is StringLiteralConcatenation, is CharLiteral, is TrueLiteral, is FalseLiteral, is NullLiteral,
        -> true
        is UnaryExpression -> estConstante(expr.expression)
        is BinaryExpression -> estConstante(expr.left) && estConstante(expr.right)
        else -> false
    }

    /** La plage de suppression ÉTENDUE (miroir tranche 2). */
    private fun plageLigneSupprimable(debut: Int, fin: Int, texte: String): IntRange? {
        if (debut < 0 || fin < debut || fin >= texte.length) return null
        var d = debut
        var f = fin + 1
        while (d > 0 && texte[d - 1] != '\n' && texte[d - 1].isWhitespace()) d--
        if (f < texte.length && texte[f] == '\n') {
            f++
        } else if (f < texte.length && texte[f].isWhitespace()) {
            var i = f
            while (i < texte.length && texte[i] != '\n' && texte[i].isWhitespace()) i++
            if (i < texte.length && texte[i] == '\n') f = i + 1
        }
        return d..f - 1
    }
}

package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.outils.EditionTexte
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.Argument
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.QualifiedAllocationExpression
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.compiler.lookup.Binding
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding

/**
 * CHANGE SIGNATURE — suppression de paramètre (vague V-D TRANCHE 3, ADR 0027
 * (e) : « change signature (graphe d'appelants CIDX, dernier) ») : le refus
 * honnête de la tranche 2 (« supprimer un paramètre réécrit les APPELANTS —
 * périmètre change-signature, tranche 3 ») se débloque ici.
 *
 * Le contrat miroir de la référence (§A5) : la signature change ET chaque
 * site d'appel perd son argument au RANG du paramètre — le graphe des
 * appelants est servi par le socle d'usages différenciés (ADR 0028) : la
 * liste des candidats vient de l'index de mots, la correspondance est par
 * CLÉ DE BINDING (propriétaire top-niveau + sélecteur + arité), les envois
 * `m(…)`, les sites `new X(…)` (le binding d'une allocation EST le
 * constructeur) et les appels explicites `this(…)`/`super(…)` comptent.
 *
 * **Refus honnêtes (R1 — jamais de transformation fausse)** :
 *  - le paramètre est LU dans le corps de sa méthode (« utilise ») ;
 *  - le paramètre est VARARGS (les appelants légitimes passent un tableau
 *    ou une liste variable — le rang d'un argument ne se sait pas) ;
 *  - la méthode OUTREPASSE ou EST OUTREPASSÉE (la hiérarchie exigerait le
 *    même changement dans chaque sous-type — famille suppression
 *    hiérarchique, tranche 3 aussi) ;
 *  - une RÉFÉRENCE DE MÉTHODE `X::m` vise la méthode (sa forme doit
 *    correspondre à l'interface fonctionnelle — la changer la casserait).
 *
 * Les éditions voyagent DIFFÉRÉES ([ModCommande.Differee] : clé + position
 * du caret, ADR 0027 (f)) : la garde (usages du paramètre, hiérarchie,
 * références de méthode, collecte des appelants) se paie au RESOLVE — la
 * liste n'a jamais rien d'eager.
 *
 * Le kind `refactor.rewrite.signature` est une forme hiérarchique LSP 3.17
 * (prolongement de `refactor.rewrite`, consommable par préfixe K2).
 *
 * @param arbre l'arbre RÉSOLU de l'unité courante.
 * @param source la source Java complète de l'unité courante.
 * @param cheminCourant le chemin DISQUE de l'unité courante (clé de la
 *   map d'éditions — la déclaration vit toujours dedans).
 */
internal class ServiceChangeSignature(
    private val arbre: CompilationUnitDeclaration?,
    private val source: String,
    private val cheminCourant: String,
) {

    /** Le paramètre au caret : la déclaration visée, son RANG et sa méthode. */
    internal data class ParametreCible(
        /** Le nom lisible du paramètre. */
        val nom: String,
        /** Le RANG (base 0) du paramètre dans la signature. */
        val rang: Int,
        /** La déclaration de la méthode porteuse. */
        val methode: AbstractMethodDeclaration,
        /** La clé d'usages de la méthode (appelants multi-fichiers). */
        val cle: jo.codeide.lsp.java.navigation.CleUsages.Methode,
    )

    /** Le verdict du resolve : refus motivé OU éditions par fichier. */
    internal class ChangementSignature(
        /** Le nom du paramètre visé. */
        val nom: String,
        /** Le motif de refus honnête (null = éditions servies). */
        val refus: String?,
        /** Les appelants multi-fichiers retouchés (chemin → éditions). */
        val editions: Map<String, List<EditionTexte>>,
    )

    /**
     * La SERVABILITÉ LISTE (gardes LOCALES peu coûteuses, arbre courant
     * seul) : le caret porte un paramètre NON utilisé, NON varargs, d'une
     * méthode HORS hiérarchie d'overrides. Les gardes multi-fichiers
     * (référence de méthode `X::m`, appelants exotiques) restent au
     * resolve.
     *
     * @param offset le caret.
     * @return la cible servable, ou null (rien à servir — refus honnête).
     */
    internal fun servable(offset: Int): ParametreCible? {
        val cible = parametreAuCaret(offset) ?: return null
        if (parametreUtilise(cible)) return null
        // VARARGS (mesuré : le drapeau vit sur le BINDING, pas sur
        // Argument.modifiers — le paramètre varargs est toujours le dernier).
        val arguments = runCatching { cible.methode.arguments ?: emptyArray() }.getOrDefault(emptyArray())
        if (runCatching { cible.methode.binding?.isVarargs() == true }.getOrDefault(false) &&
            cible.rang == arguments.size - 1
        ) {
            return null
        }
        if (outrepasseOuEstOutrepassee(cible.methode)) return null
        return cible
    }

    /**
     * Le paramètre au caret (le caret porte le JETON du nom — mesuré :
     * sourceStart d'un Argument EST le début du nom, comme LocalDeclaration).
     *
     * @param offset le caret.
     * @return la cible, ou null (caret hors nom de paramètre).
     */
    internal fun parametreAuCaret(offset: Int): ParametreCible? {
        val collecteur = CollecteurParams(offset)
        LectureAst.traverserResilient(arbre ?: return null, collecteur)
        return collecteur.cible
    }

    /**
     * Les éditions de la SUPPRESSION du paramètre au caret : la signature
     * réécrite + chaque APPELANT multi-fichiers privé de son argument au
     * rang. Le calcul est payé ici (resolve) — la garde d'abord, les
     * éditions ensuite.
     *
     * @param offset le caret (sur le nom du paramètre).
     * @param appelants la collecte multi-fichiers des sites d'appel (clé de
     *   méthode → (chemin, texte, arguments)) — fournie par le moteur.
     * @return le verdict (refus motivé ou éditions par fichier).
     */
    internal fun supprimerParametre(
        offset: Int,
        appelants: List<SiteAppel>,
    ): ChangementSignature {
        val cible = parametreAuCaret(offset)
            ?: return ChangementSignature("", "parametre", emptyMap())
        // Garde 1 — le paramètre est LU dans le corps : sa suppression
        // casserait la compilation (la référence miroir refuse aussi).
        if (parametreUtilise(cible)) {
            return ChangementSignature(cible.nom, "utilise", emptyMap())
        }
        // Garde 2 — VARARGS : le rang d'un argument ne se sait pas à l'appel
        // (un tableau passé nu occupe le dernier rang, une liste variable
        // s'étale) — refus honnête. MESURÉ : le drapeau AccVarargs n'est
        // PAS dans Argument.modifiers — il vit sur le BINDING de la
        // méthode (isVarargs), le paramètre varargs est TOUJOURS le dernier.
        val arguments = runCatching { cible.methode.arguments ?: emptyArray() }.getOrDefault(emptyArray())
        if (runCatching { cible.methode.binding?.isVarargs() == true }.getOrDefault(false) &&
            cible.rang == arguments.size - 1
        ) {
            return ChangementSignature(cible.nom, "varargs", emptyMap())
        }
        // Garde 3 — HÉRARCHIE : la méthode outrepasse ou est outrépassée —
        // chaque surcharge devrait suivre (famille hiérarchique séparée).
        if (outrepasseOuEstOutrepassee(cible.methode)) {
            return ChangementSignature(cible.nom, "hierarchie", emptyMap())
        }
        // Garde 4 — RÉFÉRENCE DE MÉTHODE : `X::m` fixe la forme.
        if (appelants.any { it.estReferenceMethode }) {
            return ChangementSignature(cible.nom, "referenceMethode", emptyMap())
        }
        // Garde 5 — un appelant sans liste d'arguments lisible (syntaxe
        // exotique, arbre incomplet) : refus plutôt qu'une édition fausse.
        if (appelants.any { !it.estReferenceMethode && it.arguments.size <= cible.rang }) {
            return ChangementSignature(cible.nom, "appelIncomplet", emptyMap())
        }

        val editions = LinkedHashMap<String, MutableList<EditionTexte>>()
        // La DÉCLARATION : le paramètre et sa virgule quittent la signature.
        editionDeclaration(cible)?.let { editions.getOrPut(cheminCourant) { mutableListOf() }.add(it) }
        // Les APPELANTS : l'argument au rang et sa virgule quittent l'envoi.
        for (site in appelants) {
            if (site.estReferenceMethode) continue
            editionAppelant(site, cible.rang)?.let {
                editions.getOrPut(site.chemin) { mutableListOf() }.add(it)
            }
        }
        for (liste in editions.values) {
            liste.sortBy { it.offset }
        }
        return ChangementSignature(cible.nom, null, editions)
    }

    // ------------------------------------------------------------------ //
    // Gardes
    // ------------------------------------------------------------------ //

    /** Vrai ssi le corps de la méthode LIT le paramètre (lecture = usage). */
    private fun parametreUtilise(cible: ParametreCible): Boolean {
        val binding = runCatching { cible.methode.arguments?.getOrNull(cible.rang)?.binding }.getOrNull()
            as? LocalVariableBinding ?: return true // binding malade : refus prudent
        val collecteur = LecturesParametre(binding)
        LectureAst.traverserResilient(arbre ?: return true, collecteur)
        return collecteur.utilise
    }

    /**
     * Vrai ssi la méthode OUTREPASSE un supertype ou EST OUTREPASSÉE par un
     * sous-type SOURCE (même sélecteur, même arité — au banc, les
     * implémentations binaires comptent par le binding, les sources par la
     * déclaration). La méthode par défaut synthétique ne compte jamais.
     */
    private fun outrepasseOuEstOutrepassee(methode: AbstractMethodDeclaration): Boolean {
        val b = runCatching { methode.binding }.getOrNull() ?: return true
        val declarant = runCatching { b.declaringClass }.getOrNull() ?: return true
        val arite = runCatching { b.parameters?.size ?: 0 }.getOrDefault(0)
        fun chercheSuper(t: ReferenceBinding?, profondeur: Int): Boolean {
            if (t == null || profondeur > 12) return false
            for (m in runCatching { t.methods() }.getOrDefault(emptyArray())) {
                if (String(m.selector) == String(b.selector) && (runCatching { m.parameters?.size ?: 0 }.getOrDefault(0)) == arite) return true
            }
            return chercheSuper(runCatching { t.superclass() }.getOrNull(), profondeur + 1) ||
                runCatching { t.superInterfaces() }.getOrDefault(emptyArray()).any { chercheSuper(it, profondeur + 1) }
        }
        // EST-elle abstraite/interface ? Les implémentations des sous-types
        // sont des overrides à part entière.
        if (runCatching { b.isAbstract() || b.declaringClass?.isInterface() == true }.getOrDefault(false)) return true
        return chercheSuper(runCatching { declarant.superclass() }.getOrNull(), 0) ||
            runCatching { declarant.superInterfaces() }.getOrDefault(emptyArray()).any { chercheSuper(it, 0) }
    }

    // ------------------------------------------------------------------ //
    // Éditions
    // ------------------------------------------------------------------ //

    /** L'édition de la DÉCLARATION : le paramètre et sa virgule quittent la signature. */
    private fun editionDeclaration(cible: ParametreCible): EditionTexte? {
        val arguments = cible.methode.arguments ?: return null
        if (arguments.getOrNull(cible.rang) == null) return null
        val bornes = arguments.map { it.declarationSourceStart to it.declarationSourceEnd }
        val plage = plageSuppressionArgument(bornes, cible.rang, source) ?: return null
        return EditionTexte(plage.first, plage.last + 1 - plage.first, "")
    }

    /**
     * L'édition d'un site d'APPEL : l'argument au rang et sa virgule
     * quittent la liste d'arguments.
     */
    private fun editionAppelant(site: SiteAppel, rang: Int): EditionTexte? {
        val arguments = site.arguments
        if (arguments.size <= rang) return null
        val texte = site.texte
        val bornes = arguments.map { it.sourceStart to it.sourceEnd }
        val plage = plageSuppressionArgument(bornes, rang, texte) ?: return null
        return EditionTexte(plage.first, plage.last + 1 - plage.first, "")
    }

    /**
     * La plage de suppression d'un argument : l'argument PLUS une virgule.
     *  - NON TERMINAL : l'argument, la virgule SUIVANTE et les blancs
     *    d'après partent ensemble (`(int bruit, int utile)` retire
     *    `int bruit, ` — le prochain argument reste collé à la parenthèse) ;
     *  - TERMINAL : depuis la FIN du PRÉCÉDENT (la virgule et les blancs
     *    intermédiaires compris — `m(a, b)` retire `, b`, jamais un `a,`
     *    orphelin) ;
     *  - SEUL : sans virgule (les parenthèses restent : `m()`).
     *
     * @param bornes les (début, fin) des arguments, dans l'ordre.
     * @param rang le rang de l'argument supprimé.
     * @param texte le texte du fichier porteur de la liste.
     * @return la plage [début, fin] inclusive, ou null (bornes folles).
     */
    private fun plageSuppressionArgument(
        bornes: List<Pair<Int, Int>>,
        rang: Int,
        texte: String,
    ): IntRange? {
        val (debut, fin) = bornes.getOrNull(rang) ?: return null
        if (debut < 0 || fin < debut || fin >= texte.length) return null
        // Virgule SUIVANTE : premier ',' non blanc après l'argument, puis
        // les blancs d'après (le re-partage va jusqu'au prochain argument).
        var i = fin + 1
        while (i < texte.length && texte[i].isWhitespace()) i++
        if (i < texte.length && texte[i] == ',') {
            var j = i + 1
            while (j < texte.length && texte[j].isWhitespace()) j++
            return debut..j - 1
        }
        // Pas de virgule suivante : argument TERMINAL — tout ce qui suit le
        // PRÉCÉDENT part avec lui (virgule et blancs intermédiaires).
        if (rang > 0) {
            val finPrecedent = bornes[rang - 1].second
            if (finPrecedent in 0 until debut) {
                return finPrecedent + 1..fin
            }
        }
        // Argument seul : sans virgule.
        return debut..fin
    }

    // ------------------------------------------------------------------ //
    // Collecte
    // ------------------------------------------------------------------ //

    /** Un site d'appel multi-fichiers (le graphe des appelants). */
    internal data class SiteAppel(
        /** Le fichier du site (chemin disque). */
        val chemin: String,
        /** Le texte du fichier du site. */
        val texte: String,
        /** Les ARGUMENTS du site (ordres de la signature). */
        val arguments: List<Expression>,
        /** Vrai pour une référence de méthode `X::m` (aucun argument). */
        val estReferenceMethode: Boolean,
        /** La plage du JETON visé (sélecteur de l'envoi, nom du type d'une
         * allocation, mot-clé `this`/`super` d'un appel explicite). */
        val plage: jo.codeide.lsp.java.diagnostics.Plage,
    )

    /**
     * Le collecteur des sites d'appel d'une clé de méthode dans UNE unité
     * résolue : envois, allocations (le constructeur), allocations
     * qualifiées et appels explicites `this(…)`/`super(…)`.
     */
    internal class CollecteurSitesAppel(
        private val cle: jo.codeide.lsp.java.navigation.CleUsages.Methode,
        private val chemin: String,
        private val texte: String,
    ) : LectureAst.VisiteurResilient() {

        val sites = ArrayList<SiteAppel>()

        /** Le sélecteur LISIBLE (nom du type pour un constructeur — mesuré V-D2). */
        private fun selecteurDe(b: MethodBinding): String = runCatching {
            if (b.isConstructor()) String(b.declaringClass.compoundName.last()) else String(b.selector)
        }.getOrDefault(String(b.selector))

        /** Vrai ssi le binding correspond à la clé (propriétaire top-niveau + sélecteur + arité). */
        private fun correspond(b: MethodBinding): Boolean = runCatching {
            var courant: ReferenceBinding? = b.declaringClass
            while (courant?.enclosingType() != null) courant = courant.enclosingType()
            val proprietaire = courant?.compoundName?.joinToString(".") { String(it) } ?: return false
            proprietaire == cle.fqnProprietaire &&
                selecteurDe(b) == cle.selecteur &&
                (b.parameters?.size ?: 0) == cle.nbParametres
        }.getOrDefault(false)

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = runCatching { node.binding }.getOrNull()
            if (b is MethodBinding && correspond(b)) {
                val plage = jo.codeide.lsp.java.renommage.plageJeton(
                    texte,
                    node.sourceStart,
                    node.sourceEnd,
                    selecteurDe(b),
                )
                if (plage != null) {
                    sites.add(
                        SiteAppel(
                            chemin, texte,
                            node.arguments?.toList() ?: emptyList(),
                            estReferenceMethode = false,
                            plage,
                        ),
                    )
                }
            }
            return true
        }

        override fun visiteAllocation(node: AllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(
                node.binding,
                node.arguments,
                node.type?.sourceStart ?: node.sourceStart,
                node.type?.sourceEnd ?: node.sourceEnd,
            )
            return true
        }

        override fun visit(node: QualifiedAllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(
                node.binding,
                node.arguments,
                node.type?.sourceStart ?: node.sourceStart,
                node.type?.sourceEnd ?: node.sourceEnd,
            )
            return true
        }

        override fun visit(node: ExplicitConstructorCall, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            // Le jeton d'un appel explicite est le mot-clé `this`/`super` —
            // la plage du SITE (garde d'usages) couvre le mot-clé.
            val b = runCatching { node.binding }.getOrNull()
            if (b is MethodBinding && correspond(b)) {
                sites.add(
                    SiteAppel(
                        chemin, texte,
                        node.arguments?.toList() ?: emptyList(),
                        estReferenceMethode = false,
                        jo.codeide.lsp.java.diagnostics.Plage(node.sourceStart, node.sourceEnd + 1),
                    ),
                )
            }
            return true
        }

        // Les références de méthode `X::m` FIXENT la forme de la méthode :
        // le change signature les refuse (garde 4) mais il faut les VOIR.
        override fun visit(node: org.eclipse.jdt.internal.compiler.ast.ReferenceExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = runCatching { node.getExactMethod() }.getOrNull()
            if (b is MethodBinding && correspond(b)) {
                val nom = runCatching { String(node.selector) }.getOrNull()
                if (nom != null) {
                    val debut = runCatching { node.nameSourceStart }.getOrDefault(node.sourceStart)
                    sites.add(
                        SiteAppel(
                            chemin, texte,
                            emptyList(),
                            estReferenceMethode = true,
                            jo.codeide.lsp.java.diagnostics.Plage(debut, debut + nom.length),
                        ),
                    )
                }
            }
            return true
        }

        private fun considerer(b: Binding?, arguments: Array<Expression>?, debutType: Int, finType: Int) {
            if (b !is MethodBinding || !correspond(b)) return
            // La LIMITE de plageJeton est EXCLUE et sourceEnd d'un type est
            // INCLUSIF (mesuré V-D2) : +1 pour ne pas couper la dernière
            // lettre du nom.
            val plage = jo.codeide.lsp.java.renommage.plageJeton(texte, debutType, finType + 1, selecteurDe(b)) ?: return
            sites.add(
                SiteAppel(
                    chemin, texte,
                    arguments?.toList() ?: emptyList(),
                    estReferenceMethode = false,
                    plage,
                ),
            )
        }
    }

    /** Le collecteur du paramètre au caret. */
    private inner class CollecteurParams(private val offset: Int) : LectureAst.VisiteurResilient() {
        var cible: ParametreCible? = null

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node)
            return true
        }

        override fun visiteConstructeur(node: ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node)
            return true
        }

        private fun considerer(node: AbstractMethodDeclaration) {
            if (node is ConstructorDeclaration && runCatching { node.isDefaultConstructor() }.getOrDefault(false)) return
            val arguments = runCatching { node.arguments }.getOrNull() ?: return
            for ((rang, argument) in arguments.withIndex()) {
                val nom = argument.name ?: continue
                if (offset < argument.sourceStart || offset >= argument.sourceStart + nom.size) continue
                val cle = cleDe(node) ?: return
                cible = ParametreCible(String(nom), rang, node, cle)
                return
            }
        }

        /** La clé d'usages de la méthode (le graphe des appelants). */
        private fun cleDe(node: AbstractMethodDeclaration): jo.codeide.lsp.java.navigation.CleUsages.Methode? {
            val b = runCatching { node.binding }.getOrNull() ?: return null
            var courant: ReferenceBinding? = runCatching { b.declaringClass }.getOrNull() ?: return null
            runCatching {
                while (courant?.enclosingType() != null) courant = courant.enclosingType()
            }
            val proprietaire = runCatching {
                courant?.compoundName?.joinToString(".") { String(it) }
            }.getOrNull() ?: return null
            val selecteur = if (b.isConstructor()) String(b.declaringClass.compoundName.last()) else String(b.selector)
            return jo.codeide.lsp.java.navigation.CleUsages.Methode(
                proprietaire,
                selecteur,
                runCatching { b.parameters?.size ?: 0 }.getOrDefault(0),
            )
        }
    }

    /** Le lecteur d'usages du paramètre dans le corps (garde 1). */
    private class LecturesParametre(private val binding: LocalVariableBinding) : LectureAst.VisiteurResilient() {
        var utilise = false

        override fun visiteReferenceSimple(node: SingleNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = runCatching { node.binding }.getOrNull()
            if (b is LocalVariableBinding && b === binding) utilise = true
            return true
        }

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val positions = runCatching { node.sourcePositions }.getOrNull() ?: return true
            val b = runCatching { node.binding }.getOrNull()
            if (b is LocalVariableBinding && b === binding && positions.isNotEmpty()) utilise = true
            return true
        }

        override fun visiteReferenceChamp(node: FieldReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val recepteur = runCatching { node.receiver }.getOrNull() as? SingleNameReference ?: return true
            val b = runCatching { recepteur.binding }.getOrNull()
            if (b is LocalVariableBinding && b === binding) utilise = true
            return true
        }
    }
}

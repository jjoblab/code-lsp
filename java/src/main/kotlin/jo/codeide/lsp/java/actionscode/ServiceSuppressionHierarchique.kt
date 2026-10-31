package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.outils.EditionTexte
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.QualifiedAllocationExpression
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * SUPPRESSION HIÉRARCHIQUE (vague V-D TRANCHE 3, ADR 0027 (e)) : le safe
 * delete d'une méthode qui participe à une chaîne d'OVERRIDES — la
 * référence (§A5 : safe delete = usages + conflits) demande la suppression
 * de la hiérarchie ENTIÈRE (la méthode, ses outrépassantes et sa
 * outrépassée), jamais un maillon seul (un `@Override` orphelin ne
 * compile pas).
 *
 * La chaîne se collecte dans les DEUX sens :
 *  - vers le HAUT par les BINDINGS (superclasse et interfaces du
 *    déclarant — déjà résolus dans l'arbre courant) ;
 *  - vers le BAS par BALAYAGE des sources (chaque type déclaré dont la
 *    hiérarchie DIRECTE referme un maillon — fermeture transitive). Ce
 *    balayage vit au RESOLVE (ADR 0027 (f) : rien d'eager — la liste ne
 *    montre jamais la chaîne).
 *
 * La garde des USAGES reste celle du safe delete (socle ADR 0028,
 * multi-fichiers par clé de binding — chaque maillon porte SA clé) : un
 * site d'appel BLOQUE s'il vit HORS des maillons supprimés (les appels
 * INTERNES à la chaîne disparaissent avec elle ; un appel depuis une
 * autre méthode du sous-type reste bloquant, MESURÉ au banc).
 *
 * Le constructeur PAR DÉFAUT synthétique ne compte jamais (mesuré V-D2 :
 * positionné sur le nom du type, hors corps).
 *
 * @param arbre l'arbre RÉSOLU de l'unité courante.
 * @param source la source Java complète de l'unité courante.
 * @param cheminCourant le chemin DISQUE de l'unité courante.
 * @param resoudre résout une unité (chemin logique, texte) en arbre bindé.
 * @param fichiersPourMot les candidats de l'index de mots (chemin chaud).
 * @param toutesSources l'énumération des sources (balayage de la chaîne
 *   descendante — chemin RESOLVE, jamais la liste).
 * @param localiser localise le fichier source d'un FQN (annuaire de noms).
 */
internal class ServiceSuppressionHierarchique(
    private val arbre: CompilationUnitDeclaration?,
    private val source: String,
    private val cheminCourant: String,
    private val resoudre: (String, String) -> CompilationUnitDeclaration?,
    private val fichiersPourMot: (String) -> List<Path>,
    private val toutesSources: () -> List<Path>,
    private val localiser: (String) -> Path?,
) {

    /** Un maillon de la chaîne d'overrides : la déclaration localisée. */
    internal data class MembreHierarchie(
        /** Le fichier du maillon (chemin disque). */
        val chemin: String,
        /** Le début de la déclaration (bornes complètes, `;`/`}` avalés). */
        val debut: Int,
        /** La fin de la déclaration (inclusive). */
        val fin: Int,
        /** Le sélecteur lisible (nom du type pour un constructeur). */
        val selecteur: String,
        /** Vrai ssi le maillon EST la cible au caret. */
        val estCible: Boolean,
        /** La clé d'usages du maillon (garde multi-fichiers). */
        val cle: jo.codeide.lsp.java.navigation.CleUsages.Methode,
    )

    /** Un site d'appel bloquant (hors chaîne supprimée). */
    internal data class UsageBloquant(
        val chemin: String,
        val plage: Plage,
    )

    /** Le verdict : la chaîne, les blocages éventuels, les éditions. */
    internal class VerdictHierarchique(
        /** Le sélecteur de la cible. */
        val nom: String,
        /** La chaîne COMPLÈTE (la cible comprise). */
        val membres: List<MembreHierarchie>,
        /** Les sites d'appel bloquants (vide = suppression servie). */
        val bloquants: List<UsageBloquant>,
        /** Les éditions par fichier (vides si blocage). */
        val editions: Map<String, List<EditionTexte>>,
    )

    // ------------------------------------------------------------------ //
    // La chaîne
    // ------------------------------------------------------------------ //

    /**
     * La chaîne d'overrides de la méthode au caret : la cible, sa
     * outrépassée (bindings) et ses outrépassantes (balayage descendant).
     *
     * @param offset le caret (sur le nom de la méthode).
     * @return la chaîne (vide si le caret ne porte pas de méthode).
     */
    internal fun hierarchieDe(offset: Int): List<MembreHierarchie> {
        val methode = methodeAuCaret(offset) ?: return emptyList()
        val membres = LinkedHashMap<String, MembreHierarchie>()

        fun ajouter(chemin: String, texte: String, declaration: AbstractMethodDeclaration, fqnDeclarant: String, estCible: Boolean) {
            val cle = cleDe(declaration, fqnDeclarant) ?: return
            val identite = "$chemin#${declaration.declarationSourceStart}"
            if (membres.containsKey(identite)) return
            membres[identite] = MembreHierarchie(
                chemin,
                declaration.declarationSourceStart,
                declaration.declarationSourceEnd,
                cle.selecteur,
                estCible,
                cle,
            )
        }

        // La CIBLE (fichier courant, source donnée — l'overlay tampon).
        val fqnCible = fqnDeclarantDe(methode) ?: return emptyList()
        ajouter(cheminCourant, source, methode, fqnCible, estCible = true)

        // VERS LE HAUT : bindings du déclarant (superclasse + interfaces).
        val aMonter = ArrayDeque<String>()
        aMonter.add(fqnCible)
        val vusHaut = HashSet<String>()
        while (aMonter.isNotEmpty()) {
            val fqn = aMonter.removeFirst()
            if (!vusHaut.add(fqn)) continue
            for (superFqn in supersDe(fqn)) {
                if (superFqn in vusHaut) continue
                val (fichier, texte) = fichierDe(superFqn) ?: continue
                val declaration = trouverMethode(fichier, texte, superFqn, String(methode.selector), ariteDe(methode)) ?: continue
                ajouter(fichier, texte, declaration, superFqn, estCible = false)
                aMonter.add(superFqn)
            }
        }

        // VERS LE BAS : fermeture transitive des sous-types (balayage des
        // sources — chemin RESOLVE, jamais la liste).
        val fermeture = HashSet<String>()
        fermeture.add(fqnCible)
        var grandit = true
        while (grandit) {
            grandit = false
            for (fichier in toutesSources()) {
                val texte = try {
                    fichier.readText()
                } catch (_: Exception) {
                    continue
                }
                val arbreFichier = runCatching { resoudre(fichier.toString(), texte) }.getOrNull() ?: continue
                val collecteur = SousTypesDirects(fermeture)
                LectureAst.traverserResilient(arbreFichier, collecteur)
                for (type in collecteur.trouves) {
                    val fqn = type.second
                    if (fermeture.add(fqn)) {
                        grandit = true
                        val declaration = trouverMethode(fichier.toString(), texte, fqn, String(methode.selector), ariteDe(methode))
                        if (declaration != null) {
                            ajouter(fichier.toString(), texte, declaration, fqn, estCible = false)
                        }
                    }
                }
            }
        }
        return membres.values.toList()
    }

    /**
     * Le verdict de la suppression hiérarchique : la chaîne, les usages
     * BLOQUANTS (hors chaîne) et — sans blocage — les éditions par fichier.
     *
     * @param offset le caret (sur le nom de la méthode).
     * @return le verdict, ou null (caret sans méthode).
     */
    internal fun suppression(offset: Int): VerdictHierarchique? {
        val membres = hierarchieDe(offset)
        if (membres.isEmpty()) return null
        val cible = membres.first { it.estCible }

        // Les sites d'appel de CHAQUE maillon (clé propre), multi-fichiers
        // — un site BLOQUE s'il vit hors des maillons supprimés.
        val bloquants = ArrayList<UsageBloquant>()
        val fichiersASonder = HashSet<String>()
        fichiersASonder.add(cheminCourant)
        for (mot in membres.map { it.cle.selecteur }.toSet()) {
            for (fichier in fichiersPourMot(mot)) fichiersASonder.add(fichier.toString())
        }
        for (fichier in fichiersASonder) {
            val estCourant = fichier == cheminCourant
            val texte = if (estCourant) source else try {
                runCatching { Path.of(fichier).readText() }.getOrNull()
            } catch (_: Exception) {
                null
            } ?: continue
            val arbreFichier = if (estCourant) arbre else runCatching { resoudre(fichier, texte) }.getOrNull()
            if (arbreFichier == null) continue
            for (membre in membres) {
                val collecteur = ServiceChangeSignature.CollecteurSitesAppel(membre.cle, fichier, texte)
                LectureAst.traverserResilient(arbreFichier, collecteur)
                for (site in collecteur.sites) {
                    // Un site VIT dans un maillon supprimé : il disparaît
                    // avec lui (les appels internes à la chaîne, y compris
                    // les envois à ZÉRO argument — la plage porte le
                    // sélecteur, pas un argument).
                    val dansUnMaillon = membres.any { m ->
                        m.chemin == fichier && site.plage.debutOffset >= m.debut && site.plage.finOffset <= m.fin + 1
                    }
                    if (!dansUnMaillon) {
                        bloquants.add(UsageBloquant(fichier, site.plage))
                    }
                }
            }
        }
        if (bloquants.isNotEmpty()) {
            return VerdictHierarchique(cible.selecteur, membres, bloquants.distinctBy { it.chemin to it.plage.debutOffset }.sortedBy { it.chemin }, emptyMap())
        }

        // Les éditions : chaque maillon supprimé (bornes de ligne avalées,
        // miroir du safe delete simple).
        val editions = LinkedHashMap<String, MutableList<EditionTexte>>()
        for (membre in membres) {
            val texte = if (membre.chemin == cheminCourant) source else try {
                runCatching { Path.of(membre.chemin).readText() }.getOrNull()
            } catch (_: Exception) {
                null
            } ?: continue
            val plage = plageLigneSupprimable(membre.debut, membre.fin, texte) ?: continue
            editions.getOrPut(membre.chemin) { mutableListOf() }
                .add(EditionTexte(plage.first, plage.last + 1 - plage.first, ""))
        }
        for (liste in editions.values) liste.sortBy { it.offset }
        return VerdictHierarchique(cible.selecteur, membres, emptyList(), editions)
    }

    // ------------------------------------------------------------------ //
    // Outils de chaîne
    // ------------------------------------------------------------------ //

    /** La méthode (déclaration) au caret — le constructeur par défaut saute. */
    private fun methodeAuCaret(offset: Int): AbstractMethodDeclaration? {
        val collecteur = MethodesAuCaret(source, offset)
        LectureAst.traverserResilient(arbre ?: return null, collecteur)
        return collecteur.trouve
    }

    /** Le FQN du type déclarant de la méthode (bindings). */
    private fun fqnDeclarantDe(methode: AbstractMethodDeclaration): String? {
        val b = runCatching { (methode as? MethodDeclaration)?.binding ?: (methode as? ConstructorDeclaration)?.binding }.getOrNull()
        val declarant = runCatching { b?.declaringClass }.getOrNull() ?: return null
        return runCatching { declarant.compoundName?.joinToString(".") { String(it) } }.getOrNull()
    }

    /** Les FQN des supertypes DIRECTS d'un type source localisé. */
    private fun supersDe(fqn: String): List<String> {
        val (fichier, texte) = fichierDe(fqn) ?: return emptyList()
        val arbreFichier = runCatching { resoudre(fichier, texte) }.getOrNull() ?: return emptyList()
        val collecteur = SupersDirects(fqn)
        LectureAst.traverserResilient(arbreFichier, collecteur)
        return collecteur.supers
    }

    /** Le fichier source d'un FQN (annuaire — résolu par le moteur). */
    private val cacheLocalisation = HashMap<String, Pair<String, String>?>()

    private fun fichierDe(fqn: String): Pair<String, String>? {
        if (cacheLocalisation.containsKey(fqn)) return cacheLocalisation[fqn]
        val chemin = localiser(fqn)?.toString()
        val resultat = chemin?.let {
            try {
                it to Path.of(it).readText()
            } catch (_: Exception) {
                null
            }
        }
        cacheLocalisation[fqn] = resultat
        return resultat
    }

    /** La méthode de sélecteur/arité donnés dans le type FQN d'un fichier. */
    private fun trouverMethode(
        chemin: String,
        texte: String,
        fqn: String,
        selecteur: String,
        arite: Int,
    ): AbstractMethodDeclaration? {
        val arbreFichier = runCatching { resoudre(chemin, texte) }.getOrNull() ?: return null
        val collecteur = MethodeDeType(fqn, selecteur, arite)
        LectureAst.traverserResilient(arbreFichier, collecteur)
        return collecteur.trouve
    }

    /** L'arité d'une déclaration (paramètres déclarés). */
    private fun ariteDe(methode: AbstractMethodDeclaration): Int =
        runCatching { methode.arguments?.size ?: 0 }.getOrDefault(0)

    /** La clé d'usages d'un maillon (propriétaire top-niveau + sélecteur + arité). */
    private fun cleDe(declaration: AbstractMethodDeclaration, fqnDeclarant: String): jo.codeide.lsp.java.navigation.CleUsages.Methode? {
        val b = runCatching { (declaration as? MethodDeclaration)?.binding ?: (declaration as? ConstructorDeclaration)?.binding }.getOrNull() ?: return null
        val selecteur = if (runCatching { b.isConstructor() }.getOrDefault(false)) {
            runCatching { String(b.declaringClass.compoundName.last()) }.getOrDefault(String(declaration.selector))
        } else {
            String(declaration.selector)
        }
        var courant: ReferenceBinding? = runCatching { b.declaringClass }.getOrNull() ?: return null
        runCatching {
            while (courant?.enclosingType() != null) courant = courant.enclosingType()
        }
        val proprietaire = runCatching { courant?.compoundName?.joinToString(".") { String(it) } }.getOrNull() ?: fqnDeclarant
        return jo.codeide.lsp.java.navigation.CleUsages.Methode(proprietaire, selecteur, ariteDe(declaration))
    }

    /**
     * La plage de suppression ÉTENDUE d'une déclaration : blancs de tête
     * de ligne et retour de ligne avalés (miroir du safe delete simple —
     * une déclaration par ligne).
     */
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

    // ------------------------------------------------------------------ //
    // Collecteurs
    // ------------------------------------------------------------------ //

    /** La méthode au caret (le caret porte le jeton du sélecteur). */
    private class MethodesAuCaret(private val source: String, private val offset: Int) : LectureAst.VisiteurResilient() {
        var trouve: AbstractMethodDeclaration? = null

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node)
            return true
        }

        override fun visiteConstructeur(node: ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node)
            return true
        }

        private fun considerer(node: AbstractMethodDeclaration) {
            if (trouve != null) return
            if (node is ConstructorDeclaration && runCatching { node.isDefaultConstructor() }.getOrDefault(false)) return
            val nom = String(node.selector)
            val plage = jo.codeide.lsp.java.renommage.plageJeton(
                source,
                node.sourceStart,
                node.sourceStart + 120,
                nom,
            ) ?: return
            if (offset >= plage.debutOffset && offset < plage.finOffset) trouve = node
        }
    }

    /** Les supertypes DIRECTS (FQN) d'un type déclaré dans l'unité. */
    private class SupersDirects(private val fqn: String) : LectureAst.VisiteurResilient() {
        val supers = ArrayList<String>()

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            val b = runCatching { node.binding as? ReferenceBinding }.getOrNull()
            val fqnLocal = runCatching { b?.compoundName?.joinToString(".") { String(it) } }.getOrNull()
            if (fqnLocal != fqn) return true
            val superClasse = runCatching { node.superclass?.resolvedType as? ReferenceBinding }.getOrNull()
            if (superClasse != null) {
                runCatching { superClasse.compoundName?.joinToString(".") { String(it) } }.getOrNull()?.let { supers.add(it) }
            }
            for (interface_ in runCatching { node.superInterfaces ?: emptyArray() }.getOrDefault(emptyArray())) {
                val bInterface = runCatching { interface_.resolvedType as? ReferenceBinding }.getOrNull() ?: continue
                runCatching { bInterface.compoundName?.joinToString(".") { String(it) } }.getOrNull()?.let { supers.add(it) }
            }
            return true
        }
    }

    /** Les types DÉCLARÉS dont la hiérarchie directe referme la fermeture. */
    private class SousTypesDirects(private val fermeture: Set<String>) : LectureAst.VisiteurResilient() {
        val trouves = ArrayList<Pair<TypeDeclaration, String>>()

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            val b = runCatching { node.binding as? ReferenceBinding }.getOrNull()
            val fqnLocal = runCatching { b?.compoundName?.joinToString(".") { String(it) } }.getOrNull() ?: return true
            val superClasse = runCatching { node.superclass?.resolvedType as? ReferenceBinding }.getOrNull()
            val superFqn = runCatching { superClasse?.compoundName?.joinToString(".") { String(it) } }.getOrNull()
            if (superFqn != null && superFqn in fermeture) {
                trouves.add(node to fqnLocal)
                return true
            }
            for (interface_ in runCatching { node.superInterfaces ?: emptyArray() }.getOrDefault(emptyArray())) {
                val bInterface = runCatching { interface_.resolvedType as? ReferenceBinding }.getOrNull() ?: continue
                val fqnInterface = runCatching { bInterface.compoundName?.joinToString(".") { String(it) } }.getOrNull()
                if (fqnInterface != null && fqnInterface in fermeture) {
                    trouves.add(node to fqnLocal)
                    return true
                }
            }
            return true
        }
    }

    /** La méthode de sélecteur/arité donnés dans un type FQN de l'unité. */
    private class MethodeDeType(
        private val fqn: String,
        private val selecteur: String,
        private val arite: Int,
    ) : LectureAst.VisiteurResilient() {
        var trouve: AbstractMethodDeclaration? = null

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node)
            return true
        }

        override fun visiteConstructeur(node: ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node)
            return true
        }

        private fun considerer(node: AbstractMethodDeclaration) {
            if (trouve != null) return
            if (node is ConstructorDeclaration && runCatching { node.isDefaultConstructor() }.getOrDefault(false)) return
            if (String(node.selector) != selecteur) return
            if ((runCatching { node.arguments?.size ?: 0 }.getOrDefault(0)) != arite) return
            val b = runCatching { node.binding }.getOrNull() ?: return
            var courant: ReferenceBinding? = runCatching { b.declaringClass }.getOrNull() ?: return
            runCatching {
                while (courant?.enclosingType() != null) courant = courant.enclosingType()
            }
            val proprietaire = runCatching { courant?.compoundName?.joinToString(".") { String(it) } }.getOrNull()
            if (proprietaire == fqn) trouve = node
        }
    }
}

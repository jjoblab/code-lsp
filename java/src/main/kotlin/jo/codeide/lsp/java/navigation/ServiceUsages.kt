package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import org.eclipse.jdt.internal.compiler.ast.Assignment
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Exécuteur de requêtes d'usages (étage N4 — ADR 0014 : le QueryExecutor
 * par-dessus l'index de mots, remplacement du balayage `Files.walk` du
 * chemin chaud).
 *
 * La requête d'usages se joue en DEUX temps (le protocole de
 * `MethodUsagesSearcher` de l'étude D10, traduit clean-room) :
 *
 *  1. **CANDIDATS par l'index de mots** : les fichiers du projet qui
 *     contiennent le sélecteur / le nom du champ / le nom simple du type
 *     ([IndexMotsSources] — construction froide unique) ;
 *  2. **correspondance EXACTE par clé de binding** : chaque candidat est
 *     résolu (l'arbre bindé de l'unité) et seuls les nœuds dont la clé
 *     (propriétaire top-niveau + sélecteur + arité ; propriétaire + nom ;
 *     FQN du type) ÉGALE la clé du symbole interrogé sont retenus — les
 *     HOMONYMES sont départagés par le propriétaire et l'arité, jamais par
 *     le texte seul.
 *
 * L'overlay tampons-ouverts est porté par l'appelant : le document
 * interrogé est analysé sur la source DONNÉE (jamais relue au disque) ;
 * les autres fichiers sur leur texte disque (fraîcheur de l'index).
 *
 * Exhaustivité : [references] est EXHAUSTIF sur les racines — le banc
 * [referencesParBalayageComplet] (l'oracle « toutes les sources résolues,
 * même correspondance ») vérifie le rappel = 100 %.
 *
 * @param racines les racines sources du projet.
 * @param indexMots l'index de mots des sources (construction froide partagée).
 * @param resoudre résout une unité (chemin logique, texte) en arbre bindé.
 */
public class ServiceUsages(
    private val racines: List<Path>,
    private val indexMots: IndexMotsSources,
    private val resoudre: (String, String) -> CompilationUnitDeclaration?,
) {

    // ------------------------------------------------------------------ //
    // textDocument/references
    // ------------------------------------------------------------------ //

    /**
     * Les usages du symbole au caret (miroir moteur de
     * `textDocument/references`) : EXHAUSTIF sur les racines du projet,
     * par clé de binding — l'index de mots sert les candidats, le chemin
     * chaud ne traverse JAMAIS l'arborescence.
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte Java complet du document (tampon ouvert).
     * @param offset la position du caret.
     * @param inclureDeclaration inclure la DÉCLARATION du symbole dans les
     *   usages rendus (paramètre `includeDeclaration` du protocole LSP).
     * @return les usages triés (chemin, offset) — vide honnête.
     */
    public fun references(
        chemin: String,
        source: String,
        offset: Int,
        inclureDeclaration: Boolean = true,
    ): List<Usage> {
        val arbre = runCatching { resoudre(chemin, source) }.getOrNull() ?: return emptyList()
        val symbole = CollecteurSymbole(source, offset)
        LectureAst.traverserResilient(arbre, symbole)
        val cible = symbole.symbole() ?: return emptyList()
        val tous = ArrayList<Usage>()
        val cheminCourant = fichierCourant(chemin)?.toString() ?: chemin

        // LOCALE : périmètre de l'unité courante (identité de binding).
        if (cible.nature == NatureSymbole.LOCALE) {
            val locale = cible.bindingEcj as? LocalVariableBinding ?: return emptyList()
            val collecteur = CollecteurUsages(source, null, locale)
            LectureAst.traverserResilient(arbre, collecteur)
            tous.addAll(collecteur.occurrences.map { Usage(cheminCourant, it.plage, it.estDeclaration) })
            return filtrerDeclaration(tous, inclureDeclaration)
        }

        val cle = cible.cleUsages ?: return emptyList()
        val mot = motDe(cle)

        // Document courant : la source DONNÉE (overlay tampon ouvert).
        val collecteurCourant = CollecteurUsages(source, cle, null)
        LectureAst.traverserResilient(arbre, collecteurCourant)
        tous.addAll(collecteurCourant.occurrences.map { Usage(cheminCourant, it.plage, it.estDeclaration) })

        // Autres fichiers : candidats de l'index de mots, texte disque.
        val disqueCourant = fichierCourant(chemin)
        for (fichier in indexMots.fichiersPour(mot)) {
            if (disqueCourant != null && fichier == disqueCourant) continue // déjà servi (overlay)
            val texte = try {
                fichier.readText()
            } catch (_: Exception) {
                continue
            }
            val arbreFichier = runCatching { resoudre(fichier.toString(), texte) }.getOrNull() ?: continue
            val collecteur = CollecteurUsages(texte, cle, null)
            LectureAst.traverserResilient(arbreFichier, collecteur)
            tous.addAll(collecteur.occurrences.map { Usage(fichier.toString(), it.plage, it.estDeclaration) })
        }
        return filtrerDeclaration(tous, inclureDeclaration)
    }

    /**
     * Les usages par BALAYAGE COMPLET des racines (l'ORACLE du banc et des
     * tests : toutes les sources résolues, même correspondance par clé) —
     * le rappel de [references] est mesuré CONTRE cette liste. Ce chemin
     * traverse exprès l'arborescence (il n'est JAMAIS sur le chemin chaud).
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte du document (tampon ouvert).
     * @param offset la position du caret.
     * @return les usages exhaustifs (même forme que [references]).
     */
    public fun referencesParBalayageComplet(chemin: String, source: String, offset: Int): List<Usage> {
        val arbre = runCatching { resoudre(chemin, source) }.getOrNull() ?: return emptyList()
        val symbole = CollecteurSymbole(source, offset)
        LectureAst.traverserResilient(arbre, symbole)
        val cible = symbole.symbole() ?: return emptyList()
        val disqueCourant = fichierCourant(chemin)
        val cheminCourant = disqueCourant?.toString() ?: chemin
        val tous = ArrayList<Usage>()

        if (cible.nature == NatureSymbole.LOCALE) {
            val locale = cible.bindingEcj as? LocalVariableBinding ?: return emptyList()
            val collecteur = CollecteurUsages(source, null, locale)
            LectureAst.traverserResilient(arbre, collecteur)
            tous.addAll(collecteur.occurrences.map { Usage(cheminCourant, it.plage, it.estDeclaration) })
            return tous
        }
        val cle = cible.cleUsages ?: return emptyList()

        // Le document courant d'abord (source DONNÉE — overlay).
        run {
            val collecteur = CollecteurUsages(source, cle, null)
            LectureAst.traverserResilient(arbre, collecteur)
            tous.addAll(collecteur.occurrences.map { Usage(cheminCourant, it.plage, it.estDeclaration) })
        }
        // Puis TOUTES les sources des racines.
        for (fichier in balayerSources()) {
            if (disqueCourant != null && fichier == disqueCourant) continue
            val texte = try {
                fichier.readText()
            } catch (_: Exception) {
                continue
            }
            val arbreFichier = runCatching { resoudre(fichier.toString(), texte) }.getOrNull() ?: continue
            val collecteur = CollecteurUsages(texte, cle, null)
            LectureAst.traverserResilient(arbreFichier, collecteur)
            tous.addAll(collecteur.occurrences.map { Usage(fichier.toString(), it.plage, it.estDeclaration) })
        }
        return tous
    }

    // ------------------------------------------------------------------ //
    // textDocument/documentHighlight
    // ------------------------------------------------------------------ //

    /**
     * Les surbrillances du document courant (miroir moteur de
     * `textDocument/documentHighlight`) : TOUTES les occurrences du
     * symbole au caret dans le document SEUL — la même correspondance par
     * clé que [references], confinée à l'unité courante.
     *
     * Genres (contrat moteur, traduit en `DocumentHighlightKind` par la
     * couche LSP de N8) : méthode et type → TEXTE ; variable → ECRITURE
     * (déclaration et cible d'affectation) ou LECTURE (le reste).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return les surbrillances (vide honnête).
     */
    public fun surbrillances(chemin: String, source: String, offset: Int): List<Surbrillance> {
        val arbre = runCatching { resoudre(chemin, source) }.getOrNull() ?: return emptyList()
        // identification du symbole d'abord (le collecteur d'usages a
        // besoin de la clé ou du binding local).
        val identificateur = CollecteurSymbole(source, offset)
        LectureAst.traverserResilient(arbre, identificateur)
        val cible = identificateur.symbole() ?: return emptyList()
        val collecteur = if (cible.nature == NatureSymbole.LOCALE) {
            val locale = cible.bindingEcj as? LocalVariableBinding ?: return emptyList()
            CollecteurUsages(source, null, locale)
        } else {
            CollecteurUsages(source, cible.cleUsages ?: return emptyList(), null)
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.occurrences.map { Surbrillance(it.plage, it.genre) }
    }

    // ------------------------------------------------------------------ //
    // Outils internes
    // ------------------------------------------------------------------ //

    /** Le mot-indexable d'une clé (sélecteur, nom de champ, nom simple). */
    private fun motDe(cle: CleUsages): String = when (cle) {
        is CleUsages.Methode -> cle.selecteur
        is CleUsages.Champ -> cle.nom
        // Le nom composé d'un type membre porte un « $ » (forme binaire
        // ecj) : le mot indexé est le nom SIMPLE, après le « $ ».
        is CleUsages.Type -> cle.fqn.substringAfterLast('.').substringAfterLast('$')
    }

    /** Filtre (ou pas) les déclarations selon le paramètre LSP. */
    private fun filtrerDeclaration(usages: List<Usage>, inclure: Boolean): List<Usage> =
        if (inclure) usages.sortedWith(compareBy({ it.chemin }, { it.plage.debutOffset })) else
            usages.filterNot { it.estDeclaration }.sortedWith(compareBy({ it.chemin }, { it.plage.debutOffset }))

    /** Le fichier disque d'un chemin logique (null si purement en mémoire). */
    internal fun fichierCourant(chemin: String): Path? {
        runCatching {
            val direct = Path.of(chemin)
            if (Files.isRegularFile(direct)) return direct
        }
        for (racine in racines) {
            val candidat = racine.resolve(chemin)
            if (Files.isRegularFile(candidat)) return candidat
        }
        return null
    }

    /** L'énumération complète des sources (balayage de l'ORACLE seulement). */
    private fun balayerSources(): List<Path> {
        val fichiers = ArrayList<Path>()
        for (racine in racines) {
            if (!Files.isDirectory(racine)) continue
            try {
                Files.walk(racine).use { flux ->
                    flux.filter { it.fileName?.toString()?.endsWith(".java") == true }.forEach { fichiers.add(it) }
                }
            } catch (_: Exception) {
                // Racine illisible écartée.
            }
        }
        return fichiers.sorted()
    }

    /**
     * Collecteur des occurrences d'une clé (ou d'une locale par identité)
     * dans UNE unité résolue : références ET déclarations, avec le genre
     * de surbrillance (écriture = cible d'affectation ou déclaration
     * d'une variable ; lecture = le reste des variables ; texte sinon).
     */
    private class CollecteurUsages(
        private val source: String,
        private val cle: CleUsages?,
        private val localeCible: LocalVariableBinding?,
    ) : LectureAst.VisiteurResilient() {

        val occurrences = ArrayList<Occurrence>()

        /** Une occurrence : plage du JETON, drapeau déclaration, genre. */
        data class Occurrence(val plage: Plage, val estDeclaration: Boolean, val genre: GenreSurbrillance)

        /** Les plages des CIBLES d'affectation vues jusqu'ici (écritures). */
        private val ciblesAffectation = ArrayList<IntRange>()

        /** Vrai ssi la méthode binding correspond à la clé méthode. */
        private fun correspond(b: MethodBinding): Boolean {
            val m = cle as? CleUsages.Methode ?: return false
            val proprietaire = fqnTopDe(b.declaringClass) ?: return false
            return proprietaire == m.fqnProprietaire &&
                String(b.selector) == m.selecteur &&
                (b.parameters?.size ?: 0) == m.nbParametres
        }

        /** Vrai ssi le champ binding correspond à la clé champ. */
        private fun correspond(b: FieldBinding): Boolean {
            val c = cle as? CleUsages.Champ ?: return false
            return fqnTopDe(b.declaringClass) == c.fqnProprietaire && String(b.name) == c.nom
        }

        /** Vrai ssi le type binding correspond à la clé type. */
        private fun correspond(b: ReferenceBinding): Boolean {
            val t = cle as? CleUsages.Type ?: return false
            return fqnCompletDe(b) == t.fqn
        }

        /** Vrai ssi la locale binding EST la locale cible (identité). */
        private fun correspond(b: LocalVariableBinding): Boolean = b === localeCible

        /** Retient une occurrence de variable (lecture/écriture selon l'affectation). */
        private fun retenirVariable(debut: Int, fin: Int, estDeclaration: Boolean) {
            val jeton = Plage(debut, fin + 1)
            val genre = if (estDeclaration || estCibleAffectation(debut, fin)) {
                GenreSurbrillance.ECRITURE
            } else {
                GenreSurbrillance.LECTURE
            }
            occurrences.add(Occurrence(jeton, estDeclaration, genre))
        }

        /** Retient une occurrence textuelle (méthode ou type). */
        private fun retenirTexte(debut: Int, fin: Int, estDeclaration: Boolean) {
            occurrences.add(Occurrence(Plage(debut, fin + 1), estDeclaration, GenreSurbrillance.TEXTE))
        }

        /** Vrai ssi la plage est la cible (membre gauche) d'une affectation. */
        private fun estCibleAffectation(debut: Int, fin: Int): Boolean =
            ciblesAffectation.any { debut >= it.first && fin <= it.last }

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null }
            if (b is MethodBinding && correspond(b)) {
                val nom = try { String(b.selector) } catch (_: Exception) { null }
                val plage = if (nom != null) plageJeton(node.sourceStart, node.sourceEnd, nom) else null
                if (plage != null) retenirTexte(plage.debutOffset, plage.finOffset - 1, false)
            }
            return true
        }

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null }
            if (b is MethodBinding && correspond(b)) {
                val plage = plageJeton(node.sourceStart, node.sourceStart + 120, String(node.selector))
                if (plage != null) retenirTexte(plage.debutOffset, plage.finOffset - 1, true)
            }
            return true
        }

        override fun visiteReferenceSimple(node: SingleNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null }
            when {
                b is LocalVariableBinding && correspond(b) -> retenirVariable(node.sourceStart, node.sourceEnd, false)
                b is FieldBinding && correspond(b) -> retenirVariable(node.sourceStart, node.sourceEnd, false)
                b is ReferenceBinding && correspond(b) -> retenirTexte(node.sourceStart, node.sourceEnd, false)
            }
            return true
        }

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val positions = try { node.sourcePositions } catch (_: Exception) { null } ?: return true
            val autres = try { node.otherBindings } catch (_: Exception) { null }
            // Segment 0 : le récepteur.
            runCatching {
                val b = node.binding
                val p0 = positions.getOrNull(0) ?: return@runCatching
                val debut = (p0 ushr 32).toInt()
                val fin = p0.toInt()
                when {
                    b is LocalVariableBinding && correspond(b) -> retenirVariable(debut, fin, false)
                    b is FieldBinding && correspond(b) -> retenirVariable(debut, fin, false)
                    b is ReferenceBinding && correspond(b) -> retenirTexte(debut, fin, false)
                }
            }
            // Segments suivants.
            for (i in 1 until positions.size) {
                val b = autres?.getOrNull(i - 1) ?: continue
                runCatching {
                    val p = positions[i]
                    val debut = (p ushr 32).toInt()
                    val fin = p.toInt()
                    if (b is FieldBinding && correspond(b)) retenirVariable(debut, fin, false)
                }
            }
            return true
        }

        override fun visiteReferenceChamp(node: FieldReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null } as? FieldBinding ?: return true
            if (correspond(b)) {
                val nom = try { String(node.token) } catch (_: Exception) { null }
                if (nom != null) {
                    val plage = plageJeton(node.sourceStart, node.sourceEnd, nom)
                    if (plage != null) retenirVariable(plage.debutOffset, plage.finOffset - 1, false)
                }
            }
            return true
        }

        override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null } ?: return true
            if (b is LocalVariableBinding && correspond(b)) {
                retenirVariable(node.sourceStart, node.sourceStart + node.name.size - 1, true)
            }
            return true
        }

        override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null } ?: return true
            if (b is FieldBinding && correspond(b)) {
                retenirVariable(node.sourceStart, node.sourceStart + node.name.size - 1, true)
            }
            return true
        }

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            val b = try { node.binding } catch (_: Exception) { null } as? ReferenceBinding ?: return true
            if (correspond(b)) retenirTexte(node.sourceStart, node.sourceStart + node.name.size - 1, true)
            return true
        }

        // --- références de type : toutes les variantes ---

        override fun visit(node: org.eclipse.jdt.internal.compiler.ast.SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: org.eclipse.jdt.internal.compiler.ast.SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considererType(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
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

        /** Enregistre les cibles d'affectation AVANT de visiter leurs références. */
        override fun visit(node: Assignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            try {
                val cible = node.lhs
                if (cible != null) ciblesAffectation.add(cible.sourceStart..cible.sourceEnd)
            } catch (_: Exception) {
                // Affectation malade : ignorée.
            }
            return true
        }

        /** Considère une référence de type. */
        private fun considererType(debut: Int, fin: Int, type: org.eclipse.jdt.internal.compiler.lookup.TypeBinding?) {
            val reference = type as? ReferenceBinding ?: return
            if (correspond(reference)) retenirTexte(debut, fin, false)
        }

        /** Cherche [mot] aux bornes de mots dans `[debut, fin]` du texte. */
        private fun plageJeton(debut: Int, limite: Int, mot: String): Plage? {
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

        /** FQN complet d'un type (imbriqués compris). */
        private fun fqnCompletDe(type: ReferenceBinding?): String? = try {
            type?.compoundName?.joinToString(".") { String(it) }
        } catch (_: Exception) {
            null
        }

        /** FQN du type TOP-NIVEAU porteur. */
        private fun fqnTopDe(type: ReferenceBinding?): String? = try {
            var courant = type ?: return null
            while (courant.enclosingType() != null) courant = courant.enclosingType()
            courant.compoundName?.joinToString(".") { String(it) }
        } catch (_: Exception) {
            null
        }
    }
}

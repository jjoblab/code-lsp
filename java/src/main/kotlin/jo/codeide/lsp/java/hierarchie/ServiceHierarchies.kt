package jo.codeide.lsp.java.hierarchie

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.ArrayQualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Cible de hiérarchie de TYPES (étage N7 — cahier fiche 018) : un type
 * localisé dans les SOURCES du projet — jamais d'URI factice pour un
 * élément binaire (règle D3 : soit la source projet, soit null).
 *
 * @property nomSimple le nom simple du type.
 * @property chemin le fichier source du type (SA source).
 * @property plageNom la plage du JETON nom dans sa source.
 * @property plageDeclaration la plage de la construction entière.
 */
public data class CibleTypeHierarchie(
    public val nomSimple: String,
    public val chemin: Path,
    public val plageNom: Plage,
    public val plageDeclaration: Plage,
)

/**
 * Cible de hiérarchie de MÉTHODE (fiche 018) : une méthode localisée dans
 * les sources du projet.
 *
 * @property nom le sélecteur de la méthode.
 * @property chemin le fichier source de la DÉCLARATION.
 * @property plageNom la plage du jeton sélecteur dans sa source.
 * @property classeProprietaire le FQN de la classe déclarante.
 */
public data class CibleMethodeHierarchie(
    public val nom: String,
    public val chemin: Path,
    public val plageNom: Plage,
    public val classeProprietaire: String,
)

/**
 * Un appelant ENTRANT (fiche 018) : une méthode appelante et ses sites
 * d'appel — le groupement « par méthode appelante » (une entrée par
 * appelant, sites multiples) est la forme attendue par le client
 * hiérarchie d'appels.
 *
 * @property nomMethode le nom de la méthode appelante.
 * @property chemin le fichier source de l'appelant.
 * @property sites les extraits de source des sites d'appel.
 */
public data class AppelantEntrant(
    public val nomMethode: String,
    public val chemin: Path,
    public val sites: List<String>,
)

/**
 * Une cible OUTANTE (fiche 018) : une méthode du projet appelée depuis la
 * méthode interrogée, avec ses sites — les cibles BINAIRES sont écartées
 * (règle D3).
 *
 * @property nom le sélecteur de la méthode appelée.
 * @property classeProprietaire le FQN de la classe propriétaire.
 * @property sites les extraits de source des sites d'appel.
 */
public data class CibleSortante(
    public val nom: String,
    public val classeProprietaire: String,
    public val sites: List<String>,
)

/**
 * Moteur de navigation hiérarchique (étage N7 — cahier fiche 018) :
 * hiérarchies de TYPES (supertypes directs, sous-types par balayage des
 * racines) et hiérarchies d'APPELS (entrants groupés par appelant,
 * sortants résolus vers des déclarations source).
 *
 * Le service se construit AU-DESSUS du moteur de compilation : la
 * hiérarchie exige le projet multi-fichiers, pas l'unité isolée — les
 * sources se résolvent entre elles par l'environnement de noms (sonde
 * disque sur les racines), et la localisation FQN → fichier passe par ce
 * même annuaire. Le balayage des racines est FROID (requête explicite) —
 * le chemin chaud (QueryExecutor sur CIDX) relève de l'étage N4.
 *
 * Règle D3 : ne JAMAIS fabriquer d'URI pour un élément binaire — soit la
 * source projet, soit null / liste vide.
 *
 * @param racines les racines sources du projet.
 * @param resoudre résout une unité (chemin logique, texte) en arbre bindé.
 * @param localiser localise un FQN (annuaire de noms du moteur).
 */
public class ServiceHierarchies(
    private val racines: List<Path>,
    private val resoudre: (String, String) -> CompilationUnitDeclaration?,
    private val localiser: (String) -> EnvironnementNoms.Localisation?,
) {

    // ------------------------------------------------------------------ //
    // Hiérarchie de types
    // ------------------------------------------------------------------ //

    /**
     * Type au caret (fiche 018) : la référence OU la déclaration visée,
     * localisée dans les sources du projet.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée.
     * @return la cible, ou null (mot non-type, ou type binaire — jamais
     *   d'URI factice).
     */
    public fun typeAuCaret(chemin: String, source: String, offset: Int): CibleTypeHierarchie? {
        val arbre = resoudre(chemin, source) ?: return null
        val collecteur = TypeAuCaret(source, offset)
        LectureAst.traverserResilient(arbre, collecteur)
        val cible = collecteur.cible ?: return null
        return cibleVersType(cible)
    }

    /**
     * Supertypes DIRECTS du type au caret (fiche 018) : superclasse et
     * interfaces, localisées dans les sources du projet.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée (nom déclaré du type).
     * @return les supertypes directs (binaire → écarté, règle D3).
     */
    public fun supertypesDirects(chemin: String, source: String, offset: Int): List<CibleTypeHierarchie> {
        val arbre = resoudre(chemin, source) ?: return emptyList()
        val declaration = declarationTypeAuCaret(arbre, source, offset) ?: return emptyList()
        val supertypes = ArrayList<ReferenceBinding>()
        declaration.superclass?.resolvedType?.let { if (it is ReferenceBinding) supertypes.add(it) }
        for (interface_ in declaration.superInterfaces ?: emptyArray()) {
            (interface_.resolvedType as? ReferenceBinding)?.let { supertypes.add(it) }
        }
        return supertypes.mapNotNull { binding -> cibleVersType(binding) }
    }

    /**
     * Sous-types du type au caret (fiche 018) : balayage des racines
     * sources du projet — chaque type déclaré dont la hiérarchie DIRECTE
     * referme la cible.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée (nom déclaré du type).
     * @return les sous-types localisés.
     */
    public fun sousTypes(chemin: String, source: String, offset: Int): List<CibleTypeHierarchie> {
        val arbre = resoudre(chemin, source) ?: return emptyList()
        val declaration = declarationTypeAuCaret(arbre, source, offset) ?: return emptyList()
        val binding = (declaration.binding as? ReferenceBinding) ?: return emptyList()
        val fqnCible = binding.compoundName?.joinToString(".") { String(it) } ?: return emptyList()
        val resultat = ArrayList<CibleTypeHierarchie>()
        for (fichier in balayerSources()) {
            val texte = try {
                fichier.readText()
            } catch (_: Exception) {
                continue
            }
            val arbreFichier = resoudre(fichier.toString(), texte) ?: continue
            val collecteur = SousTypesPotentiels(fqnCible)
            LectureAst.traverserResilient(arbreFichier, collecteur)
            for (candidate in collecteur.trouves) {
                cibleVersType(candidate)?.let { resultat.add(it) }
            }
        }
        return resultat
    }

    // ------------------------------------------------------------------ //
    // Hiérarchie d'appels
    // ------------------------------------------------------------------ //

    /**
     * Méthode au caret (fiche 018) : l'appel résolu vers sa déclaration,
     * ou la déclaration elle-même quand le caret y vit.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée.
     * @return la cible localisée, ou null.
     */
    public fun methodeAuCaret(chemin: String, source: String, offset: Int): CibleMethodeHierarchie? {
        val arbre = resoudre(chemin, source) ?: return null
        val collecteur = MethodeAuCaret(source, offset)
        LectureAst.traverserResilient(arbre, collecteur)
        val binding = collecteur.envoi ?: collecteur.declaration ?: return null
        return cibleVersMethode(binding)
    }

    /**
     * Appels ENTRANTS de la méthode au caret (fiche 018) : balayage des
     * racines, groupés par méthode appelante (une entrée par appelant,
     * sites multiples).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée (nom déclaré de la méthode).
     * @return les appelants entrants (vide si aucune méthode au caret).
     */
    public fun appelsEntrants(chemin: String, source: String, offset: Int): List<AppelantEntrant> {
        val arbre = resoudre(chemin, source) ?: return emptyList()
        val cible = methodeDeclareeAuCaret(arbre, source, offset) ?: return emptyList()
        val fqnProprietaire = cible.first
        val selecteur = cible.second
        val resultat = ArrayList<AppelantEntrant>()
        for (fichier in balayerSources()) {
            val texte = try {
                fichier.readText()
            } catch (_: Exception) {
                continue
            }
            val arbreFichier = resoudre(fichier.toString(), texte) ?: continue
            val collecteur = AppelsEntrants(fqnProprietaire, selecteur, texte)
            LectureAst.traverserResilient(arbreFichier, collecteur)
            for ((nomMethode, sites) in collecteur.parAppelant) {
                resultat.add(AppelantEntrant(nomMethode, fichier, sites))
            }
        }
        return resultat
    }

    /**
     * Appels OUTANTS de la méthode au caret (fiche 018) : résolus vers des
     * déclarations SOURCE du projet — les cibles binaires sont écartées
     * (règle D3).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position interrogée (nom déclaré de la méthode).
     * @return les cibles sortantes avec leurs sites (vide sans méthode au caret).
     */
    public fun appelsSortants(chemin: String, source: String, offset: Int): List<CibleSortante> {
        val arbre = resoudre(chemin, source) ?: return emptyList()
        val collecteur = MethodDeclarationAuCaret(source, offset)
        LectureAst.traverserResilient(arbre, collecteur)
        val declaration = collecteur.declaration ?: return emptyList()
        val sortants = AppelsSortants(source, corpsSeulement = true, declaration = declaration)
        LectureAst.traverserResilient(arbre, sortants)
        return sortants.parCible.entries
            .map { (cle, sites) -> CibleSortante(cle.second, cle.first, sites) }
            .sortedBy { it.classeProprietaire + it.nom }
    }

    // ------------------------------------------------------------------ //
    // Localisation des cibles (règle D3)
    // ------------------------------------------------------------------ //

    /** Convertit un binding de type en cible localisée — binaire → null. */
    private fun cibleVersType(binding: ReferenceBinding): CibleTypeHierarchie? {
        val fqn = try {
            binding.compoundName?.joinToString(".") { String(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        val localisation = localiser(fqn) as? EnvironnementNoms.Localisation.Source ?: return null
        val texte = try {
            localisation.fichier.readText()
        } catch (_: Exception) {
            return null
        }
        val arbre = resoudre(localisation.fichier.toString(), texte) ?: return null
        val nomSimple = fqn.substringAfterLast('.')
        val declaration = trouverDeclarationType(arbre, texte, nomSimple) ?: return null
        val plageNom = plageNomType(declaration, texte) ?: return null
        // La déclaration s'étend au DÉBUT DE LIGNE de son en-tête : les
        // modificateurs (« public class Base … ») en font partie (la plage
        // « déclaration » COUVRE la construction — contient « class Base »).
        val debutLigne = texte.lastIndexOf('\n', declaration.sourceStart).let { if (it < 0) 0 else it + 1 }
        return CibleTypeHierarchie(
            nomSimple,
            localisation.fichier,
            plageNom,
            Plage(debutLigne, declaration.sourceEnd + 1),
        )
    }

    /** Convertit un binding de méthode en cible localisée — binaire → null. */
    private fun cibleVersMethode(binding: MethodBinding): CibleMethodeHierarchie? {
        val proprietaire = binding.declaringClass ?: return null
        val fqn = try {
            proprietaire.compoundName?.joinToString(".") { String(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        val localisation = localiser(fqn) as? EnvironnementNoms.Localisation.Source ?: return null
        val texte = try {
            localisation.fichier.readText()
        } catch (_: Exception) {
            return null
        }
        val arbre = resoudre(localisation.fichier.toString(), texte) ?: return null
        val selecteur = String(binding.selector)
        val declaration = trouverDeclarationMethode(arbre, texte, selecteur, binding.parameters?.size ?: -1)
            ?: trouverDeclarationMethode(arbre, texte, selecteur, -1)
            ?: return null
        val plageNom = plageNomMethode(declaration, texte) ?: return null
        return CibleMethodeHierarchie(selecteur, localisation.fichier, plageNom, fqn)
    }

    /** Déclaration de type portant [nom] dans l'unité (balayage résilient). */
    private fun trouverDeclarationType(arbre: CompilationUnitDeclaration, texte: String, nom: String): TypeDeclaration? {
        val collecteur = object : LectureAst.VisiteurResilient() {
            var trouvee: TypeDeclaration? = null
            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                if (trouvee == null && String(node.name) == nom) trouvee = node
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.trouvee
    }

    /** Déclaration de méthode portant [selecteur] (et [nbParametres] si ≥ 0). */
    private fun trouverDeclarationMethode(
        arbre: CompilationUnitDeclaration,
        texte: String,
        selecteur: String,
        nbParametres: Int,
    ): MethodDeclaration? {
        val collecteur = object : LectureAst.VisiteurResilient() {
            var trouvee: MethodDeclaration? = null
            override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                if (trouvee == null && String(node.selector) == selecteur &&
                    (nbParametres < 0 || node.arguments?.size == nbParametres)
                ) {
                    trouvee = node
                }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.trouvee
    }

    /** Plage du JETON nom d'une déclaration de type (balayage de tête). */
    private fun plageNomType(declaration: TypeDeclaration, texte: String): Plage? {
        val nom = String(declaration.name)
        return plageJeton(declaration.sourceStart, declaration.sourceStart + 200, texte, nom)
    }

    /** Plage du JETON sélecteur d'une déclaration de méthode (balayage de tête). */
    private fun plageNomMethode(declaration: MethodDeclaration, texte: String): Plage? {
        val selecteur = String(declaration.selector)
        return plageJeton(declaration.sourceStart, declaration.sourceStart + 160, texte, selecteur)
    }

    /** Cherche [mot] aux bornes de mots dans la tête [debut, limite) de la source. */
    private fun plageJeton(debut: Int, limite: Int, texte: String, mot: String): Plage? {
        val borne = limite.coerceAtMost(texte.length)
        val tete = texte.substring(debut, borne)
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

    /** Déclaration de type dont le NOM déclaré couvre le caret. */
    private fun declarationTypeAuCaret(
        arbre: CompilationUnitDeclaration,
        source: String,
        offset: Int,
    ): TypeDeclaration? {
        val collecteur = object : LectureAst.VisiteurResilient() {
            var trouvee: TypeDeclaration? = null
            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                if (offset in node.sourceStart..node.sourceEnd) {
                    // Le caret doit viser le NOM déclaré, pas le corps entier.
                    val plage = plageJeton(node.sourceStart, node.sourceStart + 200, source, String(node.name))
                    if (plage != null && offset >= plage.debutOffset && offset < plage.finOffset) trouvee = node
                }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.trouvee
    }

    /** (FQN propriétaire, sélecteur) de la méthode DÉCLARÉE au caret, ou null. */
    private fun methodeDeclareeAuCaret(
        arbre: CompilationUnitDeclaration,
        source: String,
        offset: Int,
    ): Pair<String, String>? {
        val collecteur = MethodDeclarationAuCaret(source, offset)
        LectureAst.traverserResilient(arbre, collecteur)
        val declaration = collecteur.declaration ?: return null
        val binding = declaration.binding ?: return null
        val proprietaire = binding.declaringClass ?: return null
        val fqn = try {
            proprietaire.compoundName?.joinToString(".") { String(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        return fqn to String(binding.selector)
    }

    /** Les fichiers source des racines (balayage froid — l'index arrive en N4). */
    private fun balayerSources(): List<Path> {
        val fichiers = ArrayList<Path>()
        for (racine in racines) {
            if (!Files.isDirectory(racine)) continue
            try {
                Files.walk(racine).use { flux ->
                    flux.filter { it.fileName?.toString()?.endsWith(".java") == true }.forEach { fichiers.add(it) }
                }
            } catch (_: Exception) {
                // Une racine illisible est écartée — résilience.
            }
        }
        return fichiers.sorted()
    }

    // ------------------------------------------------------------------ //
    // Collecteurs privés
    // ------------------------------------------------------------------ //

    /** Type (binding) au caret — référence de type, déclaration ou référence nommée. */
    private inner class TypeAuCaret(private val source: String, private val offset: Int) :
        LectureAst.VisiteurResilient() {

        var cible: ReferenceBinding? = null
        private var taille = Int.MAX_VALUE

        private fun considerer(debut: Int, fin: Int, binding: TypeBinding?) {
            if (binding == null || offset < debut || offset > fin) return
            if (fin - debut >= taille) return
            val reference = binding as? ReferenceBinding ?: return
            cible = reference
            taille = fin - debut
        }

        override fun visiteReferenceSimple(
            node: org.eclipse.jdt.internal.compiler.ast.SingleNameReference,
            scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?,
        ): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.binding as? TypeBinding)
            return true
        }

        override fun visiteReferenceQualifiee(
            node: org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference,
            scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?,
        ): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.binding as? TypeBinding)
            return true
        }

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.binding?.declaringClass)
            return true
        }

        override fun visiteAllocation(node: AllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.binding as? TypeBinding)
            return true
        }

        override fun visit(node: SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: SingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: QualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: ParameterizedSingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: ParameterizedSingleTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: ParameterizedQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: ParameterizedQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: ArrayTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: ArrayTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: ArrayQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }

        override fun visit(node: ArrayQualifiedTypeReference, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            considerer(node.sourceStart, node.sourceEnd, node.resolvedType)
            return true
        }
    }

    /** Types dont la hiérarchie DIRECTE referme le FQN cible. */
    private class SousTypesPotentiels(private val fqnCible: String) : LectureAst.VisiteurResilient() {

        val trouves = ArrayList<ReferenceBinding>()

        private fun estCible(superType: TypeBinding?): Boolean {
            val reference = superType as? ReferenceBinding ?: return false
            val fqn = try {
                reference.compoundName?.joinToString(".") { String(it) }
            } catch (_: Exception) {
                null
            } ?: return false
            return fqn == fqnCible
        }

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            val superEstCible = estCible(node.superclass?.resolvedType) ||
                (node.superInterfaces ?: emptyArray()).any { estCible(it.resolvedType) }
            if (superEstCible) {
                (node.binding as? ReferenceBinding)?.let { trouves.add(it) }
            }
            return true
        }
    }

    /** Méthode au caret : l'envoi le plus profond, ou la déclaration nommée. */
    private class MethodeAuCaret(private val source: String, private val offset: Int) : LectureAst.VisiteurResilient() {

        /** Le binding de l'ENVOI couvrant le caret (le plus profond). */
        var envoi: MethodBinding? = null
        private var tailleEnvoi = Int.MAX_VALUE

        /** Le binding de la DÉCLARATION dont le nom couvre le caret. */
        var declaration: MethodBinding? = null

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            if (offset in node.sourceStart..node.sourceEnd && node.sourceEnd - node.sourceStart < tailleEnvoi) {
                envoi = node.binding
                tailleEnvoi = node.sourceEnd - node.sourceStart
            }
            return true
        }

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            if (offset in node.sourceStart..node.sourceEnd) {
                val selecteur = String(node.selector)
                var i = node.sourceStart
                val borne = (node.sourceStart + 160).coerceAtMost(source.length)
                while (i < borne) {
                    if (source[i].isJavaIdentifierStart() && source.regionMatches(i, selecteur, 0, selecteur.length)) {
                        val apres = i + selecteur.length
                        val borneAvant = i == 0 || !source[i - 1].isJavaIdentifierPart()
                        val borneApres = apres >= source.length || !source[apres].isJavaIdentifierPart()
                        if (borneAvant && borneApres && offset >= i && offset < apres) {
                            declaration = node.binding
                        }
                        break
                    }
                    i++
                }
            }
            return true
        }
    }

    /** Déclaration de méthode dont le NOM déclaré couvre le caret. */
    private class MethodDeclarationAuCaret(private val source: String, private val offset: Int) : LectureAst.VisiteurResilient() {

        var declaration: MethodDeclaration? = null

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            if (offset in node.sourceStart..node.sourceEnd) {
                val selecteur = String(node.selector)
                var i = node.sourceStart
                val borne = (node.sourceStart + 160).coerceAtMost(source.length)
                while (i < borne) {
                    if (source[i].isJavaIdentifierStart() && source.regionMatches(i, selecteur, 0, selecteur.length)) {
                        val apres = i + selecteur.length
                        val borneAvant = i == 0 || !source[i - 1].isJavaIdentifierPart()
                        val borneApres = apres >= source.length || !source[apres].isJavaIdentifierPart()
                        if (borneAvant && borneApres && offset >= i && offset < apres) {
                            declaration = node
                        }
                        break
                    }
                    i++
                }
            }
            return true
        }
    }

    /** Appels ENTRANTS vers (fqn propriétaire, sélecteur) — groupés par méthode appelante. */
    private class AppelsEntrants(
        private val fqnProprietaire: String,
        private val selecteur: String,
        private val texte: String,
    ) : LectureAst.VisiteurResilient() {

        /** Méthode appelante courante (pile légère : une seule profondeur suffit à la fiche). */
        private var methodeCourante: MethodDeclaration? = null

        val parAppelant = LinkedHashMap<String, MutableList<String>>()

        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            methodeCourante = node
            return true
        }

        override fun finMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?) {
            methodeCourante = null
        }

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val binding = node.binding ?: return true
            val proprietaire = binding.declaringClass ?: return true
            val fqn = try {
                proprietaire.compoundName?.joinToString(".") { String(it) }
            } catch (_: Exception) {
                null
            } ?: return true
            if (fqn == fqnProprietaire && String(binding.selector) == selecteur) {
                val appelante = methodeCourante
                val nomAppelant = appelante?.let { String(it.selector) } ?: "<init>"
                val site = try {
                    texte.substring(node.sourceStart, node.sourceEnd + 1)
                } catch (_: Exception) {
                    ""
                }
                parAppelant.getOrPut(nomAppelant) { ArrayList() }.add(site)
            }
            return true
        }
    }

    /** Appels OUTANTS du corps d'une méthode — vers des sources projet uniquement. */
    private inner class AppelsSortants(
        private val texte: String,
        private val corpsSeulement: Boolean,
        private val declaration: MethodDeclaration,
    ) : LectureAst.VisiteurResilient() {

        val parCible = LinkedHashMap<Pair<String, String>, MutableList<String>>()

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            if (corpsSeulement && !dansCorps(node)) return true
            val binding = node.binding ?: return true
            val proprietaire = binding.declaringClass ?: return true
            val fqn = try {
                proprietaire.compoundName?.joinToString(".") { String(it) }
            } catch (_: Exception) {
                null
            } ?: return true
            // Règle D3 : cibles binaires écartées — sources du projet seules.
            if (localiser(fqn) !is EnvironnementNoms.Localisation.Source) return true
            val cle = fqn to String(binding.selector)
            val site = try {
                texte.substring(node.sourceStart, node.sourceEnd + 1)
            } catch (_: Exception) {
                ""
            }
            parCible.getOrPut(cle) { ArrayList() }.add(site)
            return true
        }

        private fun dansCorps(node: MessageSend): Boolean {
            // Le corps d'une déclaration ecj vit entre bodyStart et bodyEnd
            // (sourceStart..sourceEnd ne couvre que l'EN-TÊTE de la méthode).
            val debutCorps = declaration.bodyStart
            val finCorps = declaration.bodyEnd
            return node.sourceStart >= debutCorps && node.sourceEnd <= finCorps
        }
    }
}

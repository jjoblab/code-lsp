package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.PontEcj
import jo.codeide.lsp.java.interne.UnitesSource
import org.eclipse.jdt.internal.compiler.ASTVisitor
import org.eclipse.jdt.internal.compiler.ast.ASTNode
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.Argument
import org.eclipse.jdt.internal.compiler.ast.CaseStatement
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.internal.compiler.ast.ImportReference
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MarkerAnnotation
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.NormalAnnotation
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference
import org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference
import org.eclipse.jdt.internal.compiler.ast.SwitchStatement
import org.eclipse.jdt.internal.compiler.ast.TryStatement
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.ast.TypeReference
import org.eclipse.jdt.internal.compiler.lookup.BlockScope
import org.eclipse.jdt.internal.compiler.lookup.ClassScope
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope
import org.eclipse.jdt.internal.compiler.lookup.MethodScope
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding

/**
 * Analyseur de contexte de complétion (étage N2 — fiches 007/008/035/044).
 *
 * Le principe (même invention que le pipeline de collecte du domaine —
 * ADR 0012 : « splice/marqueur », miroir de `insertDummyIdentifier`
 * d'IntelliJ) : le PRÉFIXE tapé est REMPLACÉ dans la source par un
 * identifiant-marqueur, la source marquée est RÉSOLUE (bindings vivants),
 * et la position du marqueur dans l'arbre DONNE le contexte grammatical —
 * la clause `extends`, la position de nom de déclaration, l'import en
 * cours, le récepteur d'accès membre… Le filtrage grammatical s'appuie
 * ainsi sur la position dans l'AST de résolution (pas d'heuristique
 * lexicale — note du cahier fiche 007) ; le comportement observable
 * (présence/absence d'items selon la position) est ce que les fiches
 * verrouillent.
 *
 * Deux étages :
 *  - **sonde à chaud** ([sonde]) sur l'arbre ORIGINAL du cache — les
 *    contextes les plus fréquents (membre, référence de paquet) se lisent
 *    sans nouvelle résolution : le récepteur est extrait TEXTUELLEMENT puis
 *    son nœud est retrouvé dans l'arbre par plage exacte (le récepteur est
 *    une expression SAINE de l'arbre — la frappe incomplète est APRÈS le
 *    point) ;
 *  - **classification marquée** ([analyser]) — la source marquée est
 *    résolue et le CHEMIN d'ancêtres du marqueur est classé.
 */
public class AnalyseurContexte(
    private val pont: PontEcj,
    private val environnement: EnvironnementNoms,
) {

    /** Position du marqueur de l'analyse en cours (portée par [analyser]). */
    private var marqueurCourant: Int = -1

    /**
     * Analyse le contexte au caret par la source marquée.
     *
     * L'ÉCHELLE DE VARIANTES (la réponse au phénomène découvert par la sonde
     * de développement : ecj ABANDONNE les membres incomplets — champ sans
     * nom `private M`, paramètre sans nom `m(M`, sélecteur sans corps `M`,
     * initialiseur non terminé `new M` — et le marqueur disparaît de
     * l'arbre avec le membre entier). Le pipeline du domaine payait le
     * même phénomène par ses « 5 variantes de splice » (ADR 0012) ; ici
     * chaque variante n'est construite que dans SA grammaire (analyse
     * textuelle de la tête) et la CASCADE s'arrête à la première capture
     * (genre ≠ INCONNU) — en frappe courante UNE seule résolution suffit.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return le contexte classé (genre INCONNU = liste vide honnête, la
     *   « non capture par conception » de la fiche 035).
     */
    public fun analyser(chemin: String, source: String, offset: Int): ContexteCompletion {
        val prefixe = prefixeIdentifiant(source, offset)
        val debutMarqueur = (offset - prefixe.length).coerceAtLeast(0)
        // Le suffixe identifiant APRÈS le caret est AUSSI consommé :
        // compléter au milieu d'un mot (« activity_m|ain ») collerait le
        // résiduel au marqueur → identifiant fusionné dont aucun segment
        // n'égalerait le marqueur → capture impossible (cas le plus
        // courant en frappe live).
        var finRemplacement = offset.coerceAtMost(source.length)
        while (finRemplacement < source.length && source[finRemplacement].isJavaIdentifierPart()) {
            finRemplacement++
        }
        val reste = source.substring(finRemplacement)
        var repli: ContexteCompletion? = null
        for (suffixe in echelleDeVariantes(source, debutMarqueur)) {
            marqueurCourant = debutMarqueur
            val marquee = source.substring(0, debutMarqueur) + MARQUEUR + suffixe + reste
            val reponse = pont.resoudre(UnitesSource.EnMemoire(chemin, marquee), genererCode = false)
            val arbre = reponse.arbre
            val commun = donneesCommunes(arbre)
            if (arbre == null) {
                // repli : le premier INCONNU garde au moins imports/paquet
                if (repli == null) {
                    repli = ContexteCompletion(
                        GenreContexte.INCONNU,
                        prefixe,
                        importsFqcn = commun.imports,
                        paquetCourant = commun.paquet,
                    )
                }
                continue
            }
            val collecteur = CheminMarqueur(debutMarqueur)
            try {
                arbre.traverse(collecteur, arbre.scope, false)
            } catch (_: Exception) {
                // arbre très cassé : le chemin partiel déjà collecté est conservé
            }
            val classification = classer(collecteur.chemin, prefixe, marquee, debutMarqueur, commun, arbre)
            if (classification.genre != GenreContexte.INCONNU) {
                // La capture est DÉFINITIVE (un genre est un verdict, pas une
                // liste : le classement du domaine « rank supérieur vide ne
                // masque jamais un non-vide inférieur » n'a pas d'équivalent
                // à ce niveau — le gating textuel a déjà écarté les variantes
                // hors de leur grammaire).
                return classification
            }
            if (repli == null) repli = classification
        }
        return repli ?: ContexteCompletion(GenreContexte.INCONNU, prefixe)
    }

    /**
     * Sonde à chaud sur l'arbre ORIGINAL (cache de parse) : détecte les
     * contextes MEMBRE / MEMBRE_STATIQUE / REFERENCE_PAQUET sans résolution
     * nouvelle.
     *
     * @param arbre l'arbre original résolu.
     * @param source la source originale.
     * @param offset la position du caret.
     * @return le contexte sondé, ou null si la sonde est inconclusive (la
     *   classification marquée prend le relais).
     */
    public fun sonde(arbre: CompilationUnitDeclaration?, source: String, offset: Int): ContexteCompletion? {
        if (arbre == null) return null
        val prefixe = prefixeIdentifiant(source, offset)
        val recepteur = recepteurTextuel(source, offset - prefixe.length) ?: return null
        // Référence de paquet : le récepteur EST un paquet (fiche 044 rule 9).
        if (estPrefixePaquet(recepteur)) {
            return ContexteCompletion(
                genre = GenreContexte.REFERENCE_PAQUET,
                prefixe = prefixe,
                paquetTape = recepteur,
            )
        }
        // Qualifier DE TYPE : membres STATIQUES seuls (fiche 020).
        val fqcnType = fqcnDuNomType(recepteur, arbre)
        if (fqcnType != null) {
            return ContexteCompletion(
                genre = GenreContexte.MEMBRE_STATIQUE,
                prefixe = prefixe,
                recepteur = recepteur,
                recepteurFqcn = fqcnType,
            )
        }
        // Récepteur d'instance : le nœud de plage exacte porte le type résolu.
        val typeInstance = typeDuRecepteur(arbre, recepteur, offset - prefixe.length)
        if (typeInstance != null) {
            return ContexteCompletion(
                genre = GenreContexte.MEMBRE,
                prefixe = prefixe,
                recepteur = recepteur,
                typeRecepteur = typeInstance,
            )
        }
        return null
    }

    // ------------------------------------------------------------------ //
    // Classification du chemin du marqueur
    // ------------------------------------------------------------------ //

    /** Données communes extraites de l'unité (imports, paquet courant). */
    private class Communs(val imports: List<String>, val paquet: String?)

    /** Extraction des imports et du paquet de l'unité. */
    private fun donneesCommunes(arbre: CompilationUnitDeclaration?): Communs {
        val imports = mutableListOf<String>()
        var paquet: String? = null
        if (arbre != null) {
            arbre.currentPackage?.let { ref ->
                paquet = ref.tokens.joinToString(".") { String(it) }
            }
            for (import in arbre.imports ?: emptyArray()) {
                if (import.isStatic) continue
                imports.add(import.tokens.joinToString(".") { String(it) })
            }
        }
        return Communs(imports, paquet)
    }

    /** Classification : chemin d'ancêtres (extérieur → intérieur) → contexte. */
    private fun classer(
        chemin: List<ASTNode>,
        prefixe: String,
        source: String,
        debutMarqueur: Int,
        commun: Communs,
        arbre: CompilationUnitDeclaration,
    ): ContexteCompletion {
        val base = ContexteCompletion(
            genre = GenreContexte.INCONNU,
            prefixe = prefixe,
            importsFqcn = commun.imports,
            paquetCourant = commun.paquet,
        )
        if (chemin.isEmpty()) return base
        val plusInterieur = chemin.last()

        // --- import : le marqueur est un token d'ImportReference ---
        if (plusInterieur is ImportReference) {
            val indexMarqueur = plusInterieur.tokens.indexOfFirst { String(it) == MARQUEUR }
            val paquetTape = if (indexMarqueur > 0) {
                (0 until indexMarqueur).joinToString(".") { String(plusInterieur.tokens[it]) }
            } else {
                ""
            }
            return ContexteCompletion(
                genre = GenreContexte.IMPORT,
                prefixe = prefixe,
                paquetTape = paquetTape,
                importsFqcn = commun.imports,
                paquetCourant = commun.paquet,
            )
        }

        // --- référence qualifiée finissant par le marqueur ---
        if (plusInterieur is QualifiedNameReference) {
            val recepteur = recepteurDe(plusInterieur, source, debutMarqueur)
            if (recepteur != null) {
                if (estPrefixePaquet(recepteur)) {
                    return ContexteCompletion(GenreContexte.REFERENCE_PAQUET, prefixe, paquetTape = recepteur)
                }
                val fqcnType = fqcnDuNomType(recepteur, arbre)
                if (fqcnType != null) {
                    return ContexteCompletion(
                        GenreContexte.MEMBRE_STATIQUE,
                        prefixe,
                        recepteur = recepteur,
                        recepteurFqcn = fqcnType,
                        importsFqcn = commun.imports,
                        paquetCourant = commun.paquet,
                    )
                }
                return ContexteCompletion(
                    genre = GenreContexte.MEMBRE,
                    prefixe = prefixe,
                    recepteur = recepteur,
                    typeRecepteur = typeRecepteurDe(plusInterieur),
                    importsFqcn = commun.imports,
                    paquetCourant = commun.paquet,
                )
            }
        }

        // --- accès champ finissant par le marqueur (après un appel : `a.b().|`) ---
        if (plusInterieur is FieldReference && String(plusInterieur.token) == MARQUEUR) {
            val type = try {
                plusInterieur.actualReceiverType
            } catch (_: Exception) {
                null
            }
            return ContexteCompletion(
                genre = GenreContexte.MEMBRE,
                prefixe = prefixe,
                recepteur = "expression",
                typeRecepteur = type,
                importsFqcn = commun.imports,
                paquetCourant = commun.paquet,
            )
        }

        // --- appel de méthode au sélecteur-marqueur (`recepteur.m|(...)`) ---
        // Un récepteur TEXTUEL est EXIGÉ : une complétion de méthode a
        // toujours un récepteur avant le point — les appels sans récepteur
        // ne surgissent que des formes de recovery parasites (variante
        // METHODE en contexte d'instruction) et ne doivent pas capturer.
        if (plusInterieur is MessageSend && String(plusInterieur.selector) == MARQUEUR) {
            val recepteur = recepteurTextuel(source, debutMarqueur)
            if (recepteur != null) {
                // qualifier DE TYPE : membres STATIQUES seuls (fiche 020)
                val fqcnType = fqcnDuNomType(recepteur, arbre)
                if (fqcnType != null) {
                    return ContexteCompletion(
                        genre = GenreContexte.MEMBRE_STATIQUE,
                        prefixe = prefixe,
                        recepteur = recepteur,
                        recepteurFqcn = fqcnType,
                        importsFqcn = commun.imports,
                        paquetCourant = commun.paquet,
                    )
                }
                val type = try {
                    plusInterieur.actualReceiverType
                } catch (_: Exception) {
                    null
                } ?: try {
                    plusInterieur.receiver?.resolvedType
                } catch (_: Exception) {
                    null
                }
                return ContexteCompletion(
                    genre = GenreContexte.MEMBRE,
                    prefixe = prefixe,
                    recepteur = recepteur,
                    typeRecepteur = type,
                    importsFqcn = commun.imports,
                    paquetCourant = commun.paquet,
                )
            }
        }

        // --- le marqueur est une position de NOM de déclaration ---
        classerPositionNom(chemin, prefixe, commun)?.let { return it }

        // --- le marqueur est une position de TYPE (TypeReference) ---
        if (plusInterieur is TypeReference) {
            return classerPositionType(chemin, prefixe, commun, source, debutMarqueur)
        }

        // --- le marqueur est une expression simple ---
        if (plusInterieur is SingleNameReference) {
            return classerExpressionSimple(chemin, prefixe, commun)
        }

        return base
    }

    /**
     * Classification d'une position de TYPE : la clause grammaticale se lit
     * dans les ancêtres.
     *
     * La branche LocalDeclaration porte l'heuristique MULTI-LIGNE (retours
     * appareil du 31/10/2026, banc S11) : un marqueur avalé comme TYPE d'une
     * déclaration recovery (`MARQUEUR titre` né de `ti` seul sur sa ligne
     * suivi d'une autre instruction) n'est une vraie position de TYPE que
     * si le NOM déclaré suit SUR LA MÊME LIGNE — sinon l'utilisateur tape
     * une EXPRESSION sur sa propre ligne et le contexte est NOM (locales +
     * membres + mots-clés + types, exactement le popup Android Studio).
     */
    private fun classerPositionType(
        chemin: List<ASTNode>,
        prefixe: String,
        commun: Communs,
        source: String,
        debutMarqueur: Int,
    ): ContexteCompletion {
        val typeRef = chemin.last()
        for (i in chemin.indices.reversed()) {
            val ancetre = chemin[i]
            if (ancetre === typeRef) continue
            if (ancetre is LocalDeclaration && ancetre.type === typeRef) {
                // La fin du marqueur vers le NOM déclaré : seuls des blancs
                // peuvent séparer les deux (l'ordre Java est TYPE NOM = …) ;
                // un SAUT DE LIGNE avant le premier jeton = le marqueur
                // n'est pas le type d'une vraie déclaration en cours.
                var indice = (debutMarqueur + MARQUEUR.length).coerceAtMost(source.length)
                while (indice < source.length && (source[indice] == ' ' || source[indice] == '\t')) indice++
                val memeLigne = indice >= source.length || source[indice] != '\n'
                return if (memeLigne) {
                    ContexteCompletion(GenreContexte.TYPE, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
                } else {
                    ContexteCompletion(GenreContexte.NOM, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
                }
            }
            if (ancetre is TypeDeclaration) {
                if (ancetre.superclass === typeRef) {
                    return ContexteCompletion(GenreContexte.TYPE, prefixe, FiltreGrammatical.CLASSES_SEULES, importsFqcn = commun.imports, paquetCourant = commun.paquet)
                }
                if (ancetre.superInterfaces?.contains(typeRef) == true) {
                    return ContexteCompletion(GenreContexte.TYPE, prefixe, FiltreGrammatical.INTERFACES_SEULES, importsFqcn = commun.imports, paquetCourant = commun.paquet)
                }
            }
            if (ancetre is MethodDeclaration && ancetre.thrownExceptions?.contains(typeRef) == true) {
                return ContexteCompletion(GenreContexte.TYPE, prefixe, FiltreGrammatical.THROWABLES_SEULES, importsFqcn = commun.imports, paquetCourant = commun.paquet)
            }
            if (ancetre is Argument && ancetre.type === typeRef) {
                val estCatch = chemin.any { it is TryStatement }
                return ContexteCompletion(
                    GenreContexte.TYPE,
                    prefixe,
                    if (estCatch) FiltreGrammatical.THROWABLES_SEULES else null,
                    importsFqcn = commun.imports,
                    paquetCourant = commun.paquet,
                )
            }
            if (ancetre is AllocationExpression && ancetre.type === typeRef) {
                return ContexteCompletion(GenreContexte.TYPE, prefixe, FiltreGrammatical.INSTANCIABLES_SEULES, importsFqcn = commun.imports, paquetCourant = commun.paquet)
            }
            if (ancetre is MarkerAnnotation && ancetre.type === typeRef) {
                return ContexteCompletion(GenreContexte.TYPE, prefixe, FiltreGrammatical.ANNOTATIONS_SEULES, importsFqcn = commun.imports, paquetCourant = commun.paquet)
            }
            if (ancetre is NormalAnnotation && ancetre.type === typeRef) {
                return ContexteCompletion(GenreContexte.TYPE, prefixe, FiltreGrammatical.ANNOTATIONS_SEULES, importsFqcn = commun.imports, paquetCourant = commun.paquet)
            }
            if (ancetre is SingleMemberAnnotation && ancetre.type === typeRef) {
                return ContexteCompletion(GenreContexte.TYPE, prefixe, FiltreGrammatical.ANNOTATIONS_SEULES, importsFqcn = commun.imports, paquetCourant = commun.paquet)
            }
            if (ancetre is MethodDeclaration && ancetre.returnType === typeRef) {
                return ContexteCompletion(GenreContexte.TYPE, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
            }
        }
        // Repli (retours appareil du 31/10/2026, banc S11) : une référence
        // de type NUE au niveau INSTRUCTION (ECJ parse l'identifiant isolé
        // d'une frappe en cours comme SingleTypeReference de recovery) —
        // toute vraie position de type porte une ANCRE de déclaration ci-
        // dessus (superclass, implements, throws, argument, allocation,
        // annotation, type de retour, type de locale/champ). Dans le CORPS
        // d'une méthode CONCRÈTE, le popup est donc NOM : locales +
        // membres + mots-clés + types, exactement Android Studio.
        if (chemin.any { it is MethodDeclaration && it.statements != null }) {
            return ContexteCompletion(GenreContexte.NOM, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
        }
        return ContexteCompletion(GenreContexte.TYPE, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
    }

    /**
     * Classification d'une position de NOM de déclaration : le marqueur est
     * le nom (ou la forme recovery) d'un champ/locale/paramètre/méthode.
     *
     * Règles (les pièges du cahier, fiches 007/008/035) :
     *  - déclaration AVEC type → [GenreContexte.NOM_DECLARATION] (noms
     *    dérivés du type — l'espace final est le discriminant : la source
     *    marquée `Type MARQUEUR` ne se parse ainsi QUE si l'espace était
     *    là) ;
     *  - champ SANS type AVEC modificateurs (`private MARQUEUR`) →
     *    [GenreContexte.TYPE] (l'utilisateur tape le type du champ) ;
     *  - champ SANS type SANS modificateur, ou sélecteur de méthode sans
     *    corps (`toStr|`, `ru|`) → [GenreContexte.CORPS_CLASSE] (stubs
     *    d'override) ;
     *  - argument SANS type → [GenreContexte.TYPE] (le token unique d'un
     *    paramètre incomplet se lit comme type — fiche 035).
     */
    private fun classerPositionNom(chemin: List<ASTNode>, prefixe: String, commun: Communs): ContexteCompletion? {
        val noeud = chemin.last()
        when (noeud) {
            is LocalDeclaration -> {
                if (estLeMarqueur(noeud.name)) {
                    val attendu = try {
                        noeud.type?.resolvedType ?: noeud.binding?.type
                    } catch (_: Exception) {
                        null
                    }
                    return ContexteCompletion(
                        GenreContexte.NOM_DECLARATION,
                        prefixe,
                        typeAttendu = attendu,
                        importsFqcn = commun.imports,
                        paquetCourant = commun.paquet,
                    )
                }
                if (noeud.type != null && couvreMarqueur(noeud.type)) {
                    return ContexteCompletion(GenreContexte.TYPE, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
                }
            }

            is FieldDeclaration -> {
                if (estLeMarqueur(noeud.name)) {
                    if (noeud.type != null) {
                        val attendu = try {
                            noeud.type?.resolvedType ?: noeud.binding?.type
                        } catch (_: Exception) {
                            null
                        }
                        return ContexteCompletion(
                            GenreContexte.NOM_DECLARATION,
                            prefixe,
                            typeAttendu = attendu,
                            importsFqcn = commun.imports,
                            paquetCourant = commun.paquet,
                        )
                    }
                    return if (noeud.modifiers != 0) {
                        ContexteCompletion(GenreContexte.TYPE, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
                    } else {
                        // Identifiant nu dans le corps de classe : méthode en
                        // cours de frappe — stubs d'override (fiche 007).
                        ContexteCompletion(
                            GenreContexte.CORPS_CLASSE,
                            prefixe,
                            typeEnglobant = typeEnglobantDe(chemin),
                            importsFqcn = commun.imports,
                            paquetCourant = commun.paquet,
                        )
                    }
                }
                if (noeud.type != null && couvreMarqueur(noeud.type)) {
                    return ContexteCompletion(GenreContexte.TYPE, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
                }
            }

            is Argument -> {
                if (estLeMarqueur(noeud.name)) {
                    if (noeud.type != null) {
                        val attendu = try {
                            noeud.type?.resolvedType ?: noeud.binding?.type
                        } catch (_: Exception) {
                            null
                        }
                        return ContexteCompletion(
                            GenreContexte.NOM_DECLARATION,
                            prefixe,
                            typeAttendu = attendu,
                            importsFqcn = commun.imports,
                            paquetCourant = commun.paquet,
                        )
                    }
                    return ContexteCompletion(GenreContexte.TYPE, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
                }
                if (noeud.type != null && couvreMarqueur(noeud.type)) {
                    val estCatch = chemin.any { it is TryStatement }
                    return ContexteCompletion(
                        GenreContexte.TYPE,
                        prefixe,
                        if (estCatch) FiltreGrammatical.THROWABLES_SEULES else null,
                        importsFqcn = commun.imports,
                        paquetCourant = commun.paquet,
                    )
                }
            }

            is MethodDeclaration -> {
                if (estLeMarqueur(noeud.selector) && noeud.statements == null) {
                    // Frappe d'un nom de méthode dans le corps de classe.
                    return ContexteCompletion(
                        GenreContexte.CORPS_CLASSE,
                        prefixe,
                        typeEnglobant = typeEnglobantDe(chemin),
                        importsFqcn = commun.imports,
                        paquetCourant = commun.paquet,
                    )
                }
            }

            else -> return null
        }
        return null
    }

    /** Classification d'une expression simple (le marqueur est un nom isolé). */
    private fun classerExpressionSimple(chemin: List<ASTNode>, prefixe: String, commun: Communs): ContexteCompletion {
        // Label de switch sur enum (fiche 007) : le sélecteur donne l'enum.
        for (ancetre in chemin.reversed()) {
            if (ancetre is CaseStatement) {
                val interrupteur = chemin.firstOrNull { it is SwitchStatement } as? SwitchStatement
                val typeEnum = try {
                    interrupteur?.expression?.resolvedType
                } catch (_: Exception) {
                    null
                }
                return ContexteCompletion(
                    GenreContexte.CASE_LABEL,
                    prefixe,
                    typeAttendu = typeEnum,
                    importsFqcn = commun.imports,
                    paquetCourant = commun.paquet,
                )
            }
        }
        // Valeur d'attribut d'annotation (fiche 007) : le type de l'attribut.
        for (ancetre in chemin.reversed()) {
            if (ancetre is MemberValuePair) {
                val typeEnum = try {
                    ancetre.binding?.returnType ?: typeAttribut(chemin, ancetre.name)
                } catch (_: Exception) {
                    null
                }
                return ContexteCompletion(
                    GenreContexte.VALEUR_ANNOTATION,
                    prefixe,
                    typeAttendu = typeEnum,
                    importsFqcn = commun.imports,
                    paquetCourant = commun.paquet,
                )
            }
            if (ancetre is SingleMemberAnnotation) {
                val typeEnum = try {
                    typeAttribut(chemin, null)
                } catch (_: Exception) {
                    null
                }
                return ContexteCompletion(
                    GenreContexte.VALEUR_ANNOTATION,
                    prefixe,
                    typeAttendu = typeEnum,
                    importsFqcn = commun.imports,
                    paquetCourant = commun.paquet,
                )
            }
        }
        return ContexteCompletion(GenreContexte.NOM, prefixe, importsFqcn = commun.imports, paquetCourant = commun.paquet)
    }

    /** Type de l'attribut d'une annotation (par son nom, ou `value` en mono-valeur). */
    private fun typeAttribut(chemin: List<ASTNode>, nomAttribut: CharArray?): TypeBinding? {
        val annotation = chemin.firstOrNull {
            it is NormalAnnotation || it is SingleMemberAnnotation || it is MarkerAnnotation
        } as? org.eclipse.jdt.internal.compiler.ast.Annotation ?: return null
        val typeAnnotation = try {
            annotation.resolvedType as? ReferenceBinding
        } catch (_: Exception) {
            null
        } ?: return null
        val nom = nomAttribut?.let { String(it) } ?: "value"
        val methodes = try {
            typeAnnotation.availableMethods() ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        return methodes.firstOrNull { String(it.selector) == nom }?.returnType
    }

    /** Type englobant du chemin (le TypeDeclaration le plus profond). */
    private fun typeEnglobantDe(chemin: List<ASTNode>): ReferenceBinding? =
        chemin.filterIsInstance<TypeDeclaration>().lastOrNull()?.binding

    /** Récepteur textuel d'une référence qualifiée marquée (texte avant le marqueur). */
    private fun recepteurDe(node: QualifiedNameReference, source: String, debutMarqueur: Int): String? {
        val fragments = node.tokens?.size ?: 0
        if (fragments < 2) return null
        val fin = debutMarqueur - 1
        if (fin < 0) return null
        val texte = try {
            source.substring(node.sourceStart, fin)
        } catch (_: Exception) {
            return null
        }
        return texte.trimEnd('.').ifEmpty { null }
    }

    /**
     * Type d'instance d'un récepteur marqué (QNR) : le DERNIER binding sain
     * de la chaîne, en partant du premier segment. Le premier token (`s`
     * dans `s.marq|`) porte un binding de variable — c'est le récepteur du
     * cas 2 tokens ; les tokens suivants (`binding.titre.marq|`) ajoutent
     * des bindings de champs — le dernier SAIN avant le marqueur est le
     * récepteur réel (l'échec du marqueur lui-même ne doit pas masquer la
     * chaîne résolue en amont).
     */
    private fun typeRecepteurDe(node: QualifiedNameReference): TypeBinding? = try {
        var sain: TypeBinding? = null
        val premier = node.binding
        if (premier is org.eclipse.jdt.internal.compiler.lookup.VariableBinding) {
            sain = premier.type
        }
        for (binding in node.otherBindings ?: emptyArray()) {
            if (binding == null || binding is org.eclipse.jdt.internal.compiler.lookup.ProblemFieldBinding) break
            sain = binding.type
        }
        sain ?: node.actualReceiverType
    } catch (_: Exception) {
        try {
            node.actualReceiverType
        } catch (_2: Exception) {
            null
        }
    }

    /** Vrai si le tableau de nom EST le marqueur. */
    private fun estLeMarqueur(nom: CharArray?): Boolean = nom != null && String(nom) == MARQUEUR

    /** Vrai si un TypeReference couvre la position du marqueur courant. */
    private fun couvreMarqueur(type: TypeReference?): Boolean {
        if (type == null) return false
        return try {
            type.sourceStart in 0..marqueurCourant && type.sourceEnd + 1 >= marqueurCourant
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ //
    // Helpers textuels et résolution de noms
    // ------------------------------------------------------------------ //

    /**
     * Construit l'ÉCHELLE DE VARIANTES de splice : les suffixes insérés
     * après le marqueur, du plus fidèle au plus synthétique. Chaque
     * variante (hors remplacement et queue) n'existe que dans SA
     * grammaire — c'est le gating textuel qui écarte les formes hors de
     * leur contexte, la cascade qui ordonne les tentatives.
     *
     *  1. REMPLACEMENT («») — le marqueur nu : capture toute syntaxe déjà
     *     complète (clauses, expressions terminées, accès membres) ;
     *  2. APPEL_NEW («()») — si `new` précède : `new M()` est une
     *     allocation valide, l'initialiseur incomplet est sinon jeté ;
     *  3. CHAMP (« x;») — si des modificateurs précèdent : `private M x;`
     *     est une déclaration valide, le champ sans nom est sinon jeté ;
     *  4. PARAM (« x») — si la position ouvre une liste (`(` ou `,`) :
     *     `m(M x)` est un paramètre valide, le paramètre sans nom est
     *     sinon jeté (le token unique se lit comme type — fiche 035) ;
     *  5. METHODE («() {}») — si AUCUN modificateur ne précède : `M() {}`
     *     est une méthode valide, l'identifiant nu en corps de classe est
     *     sinon jeté — la position de membre, stubs d'override (fiche 007) ;
     *  6. QUEUE ÉQUILIBRÉE — dernier recours : referme les délimiteurs
     *     ouverts de la tête et termine par «;» — sauve les instructions
     *     non terminées qu'ecj jette autrement (chaîne autour d'une
     *     lambda, accès membre en fin de frappe).
     */
    private fun echelleDeVariantes(source: String, debutMarqueur: Int): List<String> {
        val variantes = mutableListOf("")
        val modificateurs = apresModificateurs(source, debutMarqueur)
        if (motPrecedent(source, debutMarqueur) == "new") {
            variantes.add("()")
        }
        if (modificateurs) {
            variantes.add(" x;")
        }
        if (ouvreListe(source, debutMarqueur)) {
            variantes.add(" x")
        }
        // Jamais après un point : une position d'accès membre n'est jamais
        // une déclaration fraîche (sinon le recovery d'ecj synthétise un
        // MethodDeclaration parasite en contexte d'instruction non terminée).
        if (!modificateurs && !apresPoint(source, debutMarqueur)) {
            variantes.add("() {}")
        }
        variantes.add(queueEquilibree(source.substring(0, debutMarqueur)))
        return variantes
    }

    /**
     * Mot-clé/identifiant qui précède la position (espaces franchis) ;
     * null si aucun (ponctuation, début de source).
     */
    private fun motPrecedent(source: String, position: Int): String? {
        var fin = position
        while (fin > 0 && source[fin - 1].isWhitespace()) fin--
        var debut = fin
        while (debut > 0 && source[debut - 1].isJavaIdentifierPart()) debut--
        if (debut == fin) return null
        return source.substring(debut, fin)
    }

    /**
     * Vrai si des MODIFICATEURS de déclaration précèdent la position
     * (``private``, ``public``, ``static``, ``final``…) : la chaîne
     * complète est remontée (`private final |`). Vrai aussi pour les
     * locales `final` — la variante CHAMP y produit une déclaration locale
     * valide, classification identique.
     */
    private fun apresModificateurs(source: String, position: Int): Boolean {
        var fin = position
        var vuModificateur = false
        while (true) {
            while (fin > 0 && source[fin - 1].isWhitespace()) fin--
            var debut = fin
            while (debut > 0 && source[debut - 1].isJavaIdentifierPart()) debut--
            if (debut == fin) return vuModificateur
            if (source.substring(debut, fin) !in MODIFICATEURS_DECLARATION) return vuModificateur
            vuModificateur = true
            fin = debut
        }
    }

    /** Vrai si la position ouvre une liste d'arguments/paramètres (`(` ou `,`). */
    private fun ouvreListe(source: String, position: Int): Boolean {
        var i = position
        while (i > 0 && source[i - 1].isWhitespace()) i--
        if (i == 0) return false
        val c = source[i - 1]
        return c == '(' || c == ','
    }

    /** Vrai si la position suit immédiatement un point d'accès membre. */
    private fun apresPoint(source: String, position: Int): Boolean {
        var i = position
        while (i > 0 && source[i - 1].isWhitespace()) i--
        return i > 0 && source[i - 1] == '.'
    }

    /**
     * Queue équilibrée (la 5e forme du domaine, portée ici sans le `()`
     * après point : la branche FieldReference du classificateur capture
     * l'accès membre non appelé, là où le domaine devait matérialiser un
     * MessageSend). Referme les parenthèses/accolades/crochets OUVERTS
     * dans la tête (du plus interne au plus externe) et termine par «;».
     */
    private fun queueEquilibree(tete: String): String {
        val pile = ArrayDeque<Char>()
        var dansChaine = false
        var dansCaractere = false
        var echappe = false
        for (c in tete) {
            if (echappe) {
                echappe = false
                continue
            }
            if (c == '\\') {
                echappe = true
                continue
            }
            if (dansChaine) {
                if (c == '"') dansChaine = false
                continue
            }
            if (dansCaractere) {
                if (c == '\'') dansCaractere = false
                continue
            }
            when (c) {
                '"' -> dansChaine = true
                '\'' -> dansCaractere = true
                '(', '[', '{' -> pile.addLast(c)
                ')', ']', '}' -> if (pile.isNotEmpty()) pile.removeLast()
            }
        }
        val fermetures = pile.reversed().joinToString("") { ouvrant ->
            when (ouvrant) {
                '(' -> ")"
                '[' -> "]"
                else -> "}"
            }
        }
        return ";$fermetures"
    }

    /**
     * Préfixe identifiant juste avant le caret.
     *
     * @param source le texte source.
     * @param offset la position du caret.
     * @return le préfixe (éventuellement vide).
     */
    public fun prefixeIdentifiant(source: String, offset: Int): String {
        var debut = offset.coerceAtMost(source.length)
        while (debut > 0 && source[debut - 1].isJavaIdentifierPart()) {
            debut--
        }
        return source.substring(debut, offset.coerceAtMost(source.length))
    }

    /**
     * Récepteur TEXTUEL d'une complétion membre : remonte depuis la fin du
     * préfixe — espace(s), puis `.` attendu, puis l'expression qualifiée
     * (identifiants, points, crochets/parenthèses fermants).
     *
     * @param source le texte source.
     * @param finPrefixe l'offset de FIN du préfixe (début de la zone à sonder).
     * @return le texte du récepteur (`s`, `binding.titre`, `String`), ou null
     *   si aucun point ne précède (complétion de nom, pas de membre).
     */
    public fun recepteurTextuel(source: String, finPrefixe: Int): String? {
        var i = finPrefixe
        while (i > 0 && source[i - 1].isWhitespace()) i--
        if (i == 0 || source[i - 1] != '.') return null
        i-- // le point
        val fin = i
        var profondeur = 0
        while (i > 0) {
            val c = source[i - 1]
            if (c == ')' || c == ']' || c == '}') {
                profondeur++
                i--
            } else if (c == '(' || c == '[' || c == '{') {
                if (profondeur == 0) break
                profondeur--
                i--
            } else if (profondeur == 0 && (c.isJavaIdentifierPart() || c == '.')) {
                i--
            } else if (profondeur > 0) {
                i--
            } else {
                break
            }
        }
        val recepteur = source.substring(i, fin).trim('.')
        return recepteur.ifEmpty { null }
    }

    /** Vrai si le texte est un préfixe de PAQUET du classpath (sources → jars → jrt). */
    private fun estPrefixePaquet(texte: String): Boolean {
        if (texte.isEmpty()) return false
        if (environnement.cachePresence.estPaquet(texte)) return true
        if (environnement.cacheLocalisation.estPaquet(texte)) return true
        if (environnement.accesJrt?.estPaquet(texte) == true) return true
        return false
    }

    /**
     * FQCN d'un NOM DE TYPE résolu comme TYPE VISIBLE (imports, paquet
     * courant, java.lang) — l'épreuve du qualifier DE TYPE (fiche 020) ;
     * résolution par l'environnement (caches négatifs), SANS index complet.
     *
     * Les qualifiers IMBRIQUÉS (`R.layout`, `Build.VERSION`) sont portés par
     * le premier segment (résolu comme nom simple) puis chaînés en
     * imbriqués `$` (`com.example.monip.R$layout`) — la forme attendue par
     * [ServiceCompletion.membresStatiques] et [EnvironnementNoms.trouverFqcn]
     * (retombe sur le fichier de l'englobant en sources, entrée exacte en
     * jars). Sans CE chemin, la complétion statique imbriquée rendait une
     * liste vide — `R.layout.a|` ne proposait rien (retours appareil du
     * 31/10/2026, banc S09).
     *
     * @param nom le nom candidat (simple `TextView` ou imbriqué `R.layout`).
     * @param arbre l'arbre original (imports de l'unité).
     * @return le FQCN du type (forme `$` pour les imbriqués), ou null si le
     *   nom n'est pas un type visible.
     */
    private fun fqcnDuNomType(nom: String, arbre: CompilationUnitDeclaration?): String? {
        if (nom.isEmpty()) return null
        if (!nom.contains('.')) return fqcnDuNomSimple(nom, arbre)
        val segments = nom.split('.')
        var courant = fqcnDuNomSimple(segments.first(), arbre) ?: return null
        for (segment in segments.drop(1)) {
            courant = "$courant\$$segment"
            if (!typeResolvable(courant)) return null
        }
        return courant
    }

    /**
     * Épreuve d'existence d'un type (imbriqué compris) : l'entrée EXACTE
     * d'abord (jars — `R$layout` y vit en entrée propre), puis le FICHIER
     * ENGLOBANT en sources (un imbriqué source vit dans le `.java` de son
     * englobant — miroir du repli interne de [EnvironnementNoms.trouver]).
     *
     * @param fqcn le FQCN candidat (forme `$` pour les imbriqués).
     * @return vrai ssi le type est résolvable dans l'environnement.
     */
    private fun typeResolvable(fqcn: String): Boolean {
        if (environnement.trouverFqcn(fqcn) != null) return true
        val positionDollar = fqcn.lastIndexOf('$')
        if (positionDollar > 0) {
            val englobant = fqcn.substring(0, positionDollar)
            if (englobant.contains('.') && environnement.trouverFqcn(englobant) != null) return true
        }
        return false
    }

    /**
     * FQCN d'un nom SIMPLE résolu comme type visible — le cœur historique
     * de [fqcnDuNomType] (paquet courant, java.lang, imports).
     *
     * @param nom le nom simple candidat (jamais qualifié).
     * @param arbre l'arbre original (imports de l'unité).
     * @return le FQCN du type, ou null si le nom n'est pas un type visible.
     */
    private fun fqcnDuNomSimple(nom: String, arbre: CompilationUnitDeclaration?): String? {
        val candidats = mutableListOf<String>()
        arbre?.currentPackage?.let { ref ->
            val paquet = ref.tokens.joinToString(".") { String(it) }
            if (paquet.isNotEmpty()) candidats.add("$paquet.$nom")
        }
        candidats.add("java.lang.$nom")
        if (arbre != null) {
            for (import in arbre.imports ?: emptyArray()) {
                if (import.isStatic) continue
                val fqn = import.tokens.joinToString(".") { String(it) }
                if (fqn.endsWith(".*")) {
                    candidats.add("${fqn.removeSuffix(".*")}.$nom")
                } else if (fqn.endsWith(".$nom")) {
                    candidats.add(fqn)
                }
            }
        }
        return candidats.firstOrNull { fqcn -> environnement.trouverFqcn(fqcn) != null }
    }

    /**
     * Type RÉSOLU d'un récepteur d'instance : retrouve dans l'arbre original
     * le nœud dont la plage correspond au texte du récepteur et lit son type
     * résolu.
     */
    private fun typeDuRecepteur(arbre: CompilationUnitDeclaration, recepteur: String, finRecepteur: Int): TypeBinding? {
        val debut = finRecepteur - recepteur.length
        if (debut < 0) return null
        val collecteur = object : LectureAst.VisiteurResilient() {
            var type: TypeBinding? = null
            override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: BlockScope?): Boolean {
                if (node.sourceStart == debut && node.sourceEnd + 1 == finRecepteur) {
                    type = try {
                        node.resolvedType
                    } catch (_: Exception) {
                        null
                    }
                }
                return true
            }

            override fun visiteReferenceSimple(node: SingleNameReference, scope: BlockScope?): Boolean {
                if (node.sourceStart == debut && node.sourceEnd + 1 == finRecepteur) {
                    type = try {
                        node.resolvedType
                    } catch (_: Exception) {
                        null
                    }
                }
                return true
            }

            override fun visiteEnvoi(node: MessageSend, scope: BlockScope?): Boolean {
                if (node.sourceStart == debut && node.sourceEnd + 1 == finRecepteur) {
                    type = try {
                        node.resolvedType
                    } catch (_: Exception) {
                        null
                    }
                }
                return true
            }

            override fun visiteReferenceChamp(node: FieldReference, scope: BlockScope?): Boolean {
                if (node.sourceStart == debut && node.sourceEnd + 1 == finRecepteur) {
                    type = try {
                        node.resolvedType
                    } catch (_: Exception) {
                        null
                    }
                }
                return true
            }
        }
        try {
            arbre.traverse(collecteur, arbre.scope)
        } catch (_: Exception) {
            // arbre cassé : pas de type
        }
        return collecteur.type
    }

    // ------------------------------------------------------------------ //
    // Visiteur de chemin
    // ------------------------------------------------------------------ //

    /**
     * Collecteur du CHEMIN d'ancêtres du marqueur : chaque nœud STRUCTUREL
     * couvrant la position du marqueur est empilé (extérieur → intérieur).
     * La descente n'est jamais élaguée : les plages de l'AST en recovery
     * peuvent mentir, le coût est borné par la taille de l'unité.
     */
    private inner class CheminMarqueur(private val marqueur: Int) : ASTVisitor() {

        val chemin = mutableListOf<ASTNode>()

        private fun considerer(node: ASTNode, finDecl: Int = node.sourceEnd): Boolean {
            val ok = try {
                node.sourceStart in 0..marqueur && finDecl + 1 >= marqueur
            } catch (_: Exception) {
                false
            }
            if (ok && node !is CompilationUnitDeclaration) chemin.add(node)
            return true
        }

        override fun visit(importReference: ImportReference, scope: CompilationUnitScope?): Boolean = considerer(importReference)
        override fun visit(typeDeclaration: TypeDeclaration, scope: CompilationUnitScope?): Boolean = considerer(typeDeclaration, typeDeclaration.declarationSourceEnd)
        override fun visit(typeDeclaration: TypeDeclaration, scope: ClassScope?): Boolean = considerer(typeDeclaration, typeDeclaration.declarationSourceEnd)
        override fun visit(methodDeclaration: MethodDeclaration, scope: ClassScope?): Boolean = considerer(methodDeclaration, methodDeclaration.declarationSourceEnd)
        override fun visit(fieldDeclaration: FieldDeclaration, scope: MethodScope?): Boolean = considerer(fieldDeclaration, fieldDeclaration.declarationSourceEnd)
        override fun visit(localDeclaration: LocalDeclaration, scope: BlockScope?): Boolean = considerer(localDeclaration, localDeclaration.declarationSourceEnd)
        override fun visit(argument: Argument, scope: BlockScope?): Boolean = considerer(argument, argument.declarationSourceEnd)
        override fun visit(allocation: AllocationExpression, scope: BlockScope?): Boolean = considerer(allocation)
        override fun visit(qualifiedNameReference: QualifiedNameReference, scope: BlockScope?): Boolean = considerer(qualifiedNameReference)
        override fun visit(singleNameReference: SingleNameReference, scope: BlockScope?): Boolean = considerer(singleNameReference)
        override fun visit(fieldReference: FieldReference, scope: BlockScope?): Boolean = considerer(fieldReference)
        override fun visit(messageSend: MessageSend, scope: BlockScope?): Boolean = considerer(messageSend)
        override fun visit(tryStatement: TryStatement, scope: BlockScope?): Boolean = considerer(tryStatement)
        override fun visit(switchStatement: SwitchStatement, scope: BlockScope?): Boolean = considerer(switchStatement)
        override fun visit(caseStatement: CaseStatement, scope: BlockScope?): Boolean = considerer(caseStatement)
        override fun visit(markerAnnotation: MarkerAnnotation, scope: BlockScope?): Boolean = considerer(markerAnnotation)
        override fun visit(normalAnnotation: NormalAnnotation, scope: BlockScope?): Boolean = considerer(normalAnnotation)
        override fun visit(singleMemberAnnotation: SingleMemberAnnotation, scope: BlockScope?): Boolean = considerer(singleMemberAnnotation)
        override fun visit(memberValuePair: MemberValuePair, scope: BlockScope?): Boolean = considerer(memberValuePair)
        override fun visit(singleTypeReference: SingleTypeReference, scope: BlockScope?): Boolean = considerer(singleTypeReference)
        override fun visit(singleTypeReference: SingleTypeReference, scope: ClassScope?): Boolean = considerer(singleTypeReference)
        override fun visit(qualifiedTypeReference: QualifiedTypeReference, scope: BlockScope?): Boolean = considerer(qualifiedTypeReference)
        override fun visit(qualifiedTypeReference: QualifiedTypeReference, scope: ClassScope?): Boolean = considerer(qualifiedTypeReference)
    }

    public companion object {
        /**
         * Identifiant-marqueur du splice (même invention que le pipeline du
         * domaine — ADR 0012 cite le marqueur `__codeide_completion__`) :
         * remplacé par le préfixe, il occupe la position grammaticale que
         * le token complété occupera.
         */
        public const val MARQUEUR: String = "__codeide_completion__"

        /**
         * Modificateurs de déclaration (gating de la variante CHAMP) :
         * précédés d'au moins un d'eux, le marqueur est la position de
         * TYPE d'une déclaration champ/locale en cours de frappe.
         */
        private val MODIFICATEURS_DECLARATION: Set<String> = setOf(
            "public", "private", "protected", "static", "final", "transient",
            "volatile", "abstract", "default", "native", "synchronized", "strictfp",
        )
    }
}

package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.outils.EditionTexte
import org.eclipse.jdt.internal.compiler.ast.AND_AND_Expression
import org.eclipse.jdt.internal.compiler.ast.ASTNode
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression
import org.eclipse.jdt.internal.compiler.ast.Argument
import org.eclipse.jdt.internal.compiler.ast.ArrayAllocationExpression
import org.eclipse.jdt.internal.compiler.ast.ArrayInitializer
import org.eclipse.jdt.internal.compiler.ast.ArrayReference
import org.eclipse.jdt.internal.compiler.ast.AssertStatement
import org.eclipse.jdt.internal.compiler.ast.Assignment
import org.eclipse.jdt.internal.compiler.ast.BinaryExpression
import org.eclipse.jdt.internal.compiler.ast.Block
import org.eclipse.jdt.internal.compiler.ast.BreakStatement
import org.eclipse.jdt.internal.compiler.ast.CaseStatement
import org.eclipse.jdt.internal.compiler.ast.CastExpression
import org.eclipse.jdt.internal.compiler.ast.CharLiteral
import org.eclipse.jdt.internal.compiler.ast.ClassLiteralAccess
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.CompoundAssignment
import org.eclipse.jdt.internal.compiler.ast.ConditionalExpression
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration
import org.eclipse.jdt.internal.compiler.ast.ContinueStatement
import org.eclipse.jdt.internal.compiler.ast.DoStatement
import org.eclipse.jdt.internal.compiler.ast.DoubleLiteral
import org.eclipse.jdt.internal.compiler.ast.EmptyStatement
import org.eclipse.jdt.internal.compiler.ast.EqualExpression
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.ExtendedStringLiteral
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.internal.compiler.ast.FloatLiteral
import org.eclipse.jdt.internal.compiler.ast.ForStatement
import org.eclipse.jdt.internal.compiler.ast.ForeachStatement
import org.eclipse.jdt.internal.compiler.ast.IfStatement
import org.eclipse.jdt.internal.compiler.ast.InstanceOfExpression
import org.eclipse.jdt.internal.compiler.ast.IntLiteral
import org.eclipse.jdt.internal.compiler.ast.LabeledStatement
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.LongLiteral
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.NullLiteral
import org.eclipse.jdt.internal.compiler.ast.OR_OR_Expression
import org.eclipse.jdt.internal.compiler.ast.PostfixExpression
import org.eclipse.jdt.internal.compiler.ast.PrefixExpression
import org.eclipse.jdt.internal.compiler.ast.QualifiedAllocationExpression
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.QualifiedSuperReference
import org.eclipse.jdt.internal.compiler.ast.QualifiedThisReference
import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.Statement
import org.eclipse.jdt.internal.compiler.ast.StringLiteral
import org.eclipse.jdt.internal.compiler.ast.StringLiteralConcatenation
import org.eclipse.jdt.internal.compiler.ast.SuperReference
import org.eclipse.jdt.internal.compiler.ast.SwitchStatement
import org.eclipse.jdt.internal.compiler.ast.SynchronizedStatement
import org.eclipse.jdt.internal.compiler.ast.ThisReference
import org.eclipse.jdt.internal.compiler.ast.ThrowStatement
import org.eclipse.jdt.internal.compiler.ast.TrueLiteral
import org.eclipse.jdt.internal.compiler.ast.TryStatement
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.ast.UnaryExpression
import org.eclipse.jdt.internal.compiler.ast.WhileStatement
import org.eclipse.jdt.internal.compiler.ast.YieldStatement
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding

/**
 * Les REFACTORINGS LOURDS de la vague V-D tranche 2 (ADR 0027 (e) —
 * « extract variable/constant puis method, inline, safe delete ») :
 * calculés en LECTURE sur l'arbre RÉSOLU comme des plans déclaratifs
 * [ActionCode] (jamais d'écriture disque), voyagent DIFFÉRÉS
 * ([ModCommande.Differee] : clé + positions, éditions au
 * `codeAction/resolve` — recalcul DÉTERMINISTE depuis la source).
 *
 * Heuristiques de la référence (ADR 0027, `02-reference…` §A5) :
 *  - **extract method** : une SORTIE au plus, ENTRÉES bornées (les locales
 *    lues avant toute écriture deviennent des paramètres, ordre de première
 *    lecture), JAMAIS de contrôle de flux non local (`return`,
 *    `break`/`continue`) dans la sélection ;
 *  - **safe delete** : les USAGES ferment la porte (socle ADR 0028) — la
 *    DÉTECTION est multi-fichiers, la SUPPRESSION est mono-fichier (la
 *    déclaration vit toujours dans le fichier courant) ; la garde vit au
 *    niveau moteur ([MoteurJava.suppressionSure]) ;
 *  - **refus honnête** (R1) : plutôt qu'une transformation fausse,
 *    l'action n'est pas servie — extraction d'une expression en position
 *    paresseuse (opérande de `&&`/`||`, branche de ternaire, condition de
 *    boucle, `assert`), inline multi-usage d'un initialisateur à effets,
 *    deux sorties, méthode qui outrepasse…
 *
 * Les kinds sont les formes hiérarchiques LSP 3.17 :
 * `refactor.extract.variable`, `refactor.extract.constant`,
 * `refactor.extract.function`, `refactor.inline.variable`,
 * `refactor.inline.constant`, `refactor.rewrite.delete`.
 *
 * Pièges ecj MESURÉS au banc (sonde V-D2) : `declarationSourceEnd` d'une
 * locale/méthode AVALE le `;`/`}` final ; un instruction-expression
 * (envoi, affectation, incrément) ne porte PAS son `;` dans ses bornes ;
 * le CORPS d'une méthode n'est PAS un nœud Block (les instructions de haut
 * niveau ont la méthode pour conteneur) ; ecj ne pose pas de lien parent —
 * les relations se calculent par plage.
 *
 * @param arbre l'arbre RÉSOLU de l'unité (bindings vivants).
 * @param source la source Java complète.
 */
public class ServiceRefactorisations(
    private val arbre: CompilationUnitDeclaration?,
    private val source: String,
) {

    /** La structure collectée en UNE traversée (lazy — payée au premier usage). */
    private val structure: Structure by lazy {
        val s = Structure()
        LectureAst.traverserResilient(arbre, s)
        s
    }

    // ------------------------------------------------------------------ //
    // Liste des actions (clés + positions — éditions au resolve)
    // ------------------------------------------------------------------ //

    /**
     * Les refactorings applicables au caret/sélection.
     *
     * @param debut le début du caret/sélection.
     * @param fin la fin du caret/sélection (strictement supérieure à debut
     *   pour une SÉLECTION — les extractions ne se servent QUE sur sélection
     *   réelle, miroir de la référence : l'ampoule au caret simple porte les
     *   intentions légères de la tranche 1).
     * @return les actions (différées ; vide si rien ne s'applique).
     */
    public fun refactorings(debut: Int, fin: Int): List<ActionCode> {
        if (arbre == null) return emptyList()
        val resultat = mutableListOf<ActionCode>()
        if (fin > debut) {
            val (d, f) = rogneBlanc(debut, fin)
            // Extract VARIABLE : expression couverte, en code DROIT (la
            // déclaration s'insère AVANT l'instruction de bloc porteuse).
            if (d < f && extraireVariableEditions(d, f) != null) {
                resultat.add(ActionCode("refactor.extract.variable", "Extraire la variable", ModCommande.Differee(CLE_EXTRAIRE_VARIABLE, listOf(d.toString(), f.toString()))))
            }
            if (d < f && extraireConstanteEditions(d, f) != null) {
                resultat.add(ActionCode("refactor.extract.constant", "Extraire la constante", ModCommande.Differee(CLE_EXTRAIRE_CONSTANTE, listOf(d.toString(), f.toString()))))
            }
            // Extract MÉTHODE : expression couverte (l'appel reste SUR PLACE,
            // l'ordre d'évaluation est préservé) ou plage d'instructions.
            if (d < f && extraireMethodeEditions(d, f) != null) {
                resultat.add(ActionCode("refactor.extract.function", "Extraire la méthode", ModCommande.Differee(CLE_EXTRAIRE_METHODE, listOf(d.toString(), f.toString()))))
            }
        } else {
            val position = debut.coerceIn(0, source.length)
            // V-D3 — CHANGE SIGNATURE : le caret porte un PARAMÈTRE non
            // utilisé, non varargs, hors hiérarchie (les gardes locales
            // peu coûteuses ; le graphe des appelants se paie au resolve,
            // ADR 0027 (f)).
            if (ServiceChangeSignature(arbre, source, "").servable(position) != null) {
                val nomParametre = nomParametreAu(position) ?: ""
                resultat.add(
                    ActionCode(
                        "refactor.rewrite.signature",
                        "Supprimer le paramètre « $nomParametre »",
                        ModCommande.Differee(CLE_SUPPRIMER_PARAMETRE, listOf(position.toString())),
                    ),
                )
            }
            // Complément de catalogue (ADR 0032) — RÉORDONNANCEMENT : le
            // caret porte un paramètre dont le VOISIN existe (le paramètre
            // PEUT être utilisé — il se déplace avec ses lectures).
            ServiceChangeSignature(arbre, source, "").deplacementServable(position)?.let { possible ->
                val nomParametre = possible.cible.nom
                if (possible.gauche) {
                    resultat.add(
                        ActionCode(
                            "refactor.rewrite.signature",
                            "Déplacer le paramètre « $nomParametre » vers la gauche",
                            ModCommande.Differee(CLE_DEPLACER_PARAMETRE, listOf(position.toString(), "-1")),
                        ),
                    )
                }
                if (possible.droite) {
                    resultat.add(
                        ActionCode(
                            "refactor.rewrite.signature",
                            "Déplacer le paramètre « $nomParametre » vers la droite",
                            ModCommande.Differee(CLE_DEPLACER_PARAMETRE, listOf(position.toString(), "1")),
                        ),
                    )
                }
            }
            // Inline VARIABLE : le caret porte une locale initialisée
            // (les RÉAFFECTATIONS ferment la porte — une variable écrite
            // ne se met pas en ligne).
            mettreEnLigneEditions(position)?.let { editions ->
                val nom = nomLocaleAu(position) ?: ""
                val titre = if (editions.size == 1) "Supprimer la variable inutilisée « $nom »" else "Mettre « $nom » en ligne"
                resultat.add(ActionCode("refactor.inline.variable", titre, ModCommande.Differee(CLE_METTRE_EN_LIGNE, listOf(position.toString()))))
            }
            // Inline CONSTANTE : le caret porte un champ static final à
            // initialisateur constant — la variante PROJET (multi-fichiers,
            // V-D3) : chaque usage du PROJET reçoit la valeur, la garde
            // mono-fichier de la tranche 2 est LEVÉE.
            if (mettreEnLigneConstanteEditions(position) != null) {
                resultat.add(ActionCode("refactor.inline.constant", "Mettre la constante en ligne", ModCommande.Differee(CLE_METTRE_EN_LIGNE_CONSTANTE_PROJET, listOf(position.toString()))))
            }
            // Safe DELETE : le caret porte une déclaration supprimable —
            // la garde des USAGES (multi-fichiers, exhaustive) vit au RESOLVE
            // (ADR 0027 (f) : rien d'eager).
            cibleSuppression(position)?.let { cible ->
                if (cible.refus == null) {
                    resultat.add(ActionCode("refactor.rewrite.delete", "Supprimer « ${cible.nom} » sans danger", ModCommande.Differee(CLE_SUPPRIMER_SURE, listOf(position.toString()))))
                } else if (cible.refus == "override") {
                    // V-D3 — SUPPRESSION HIÉRARCHIQUE : la méthode
                    // OUTREPASSE (refus tranche 2) — la chaîne ENTIÈRE se
                    // supprime (la cible, sa outrépassée, ses
                    // outrépassantes), la garde des usages au resolve.
                    resultat.add(
                        ActionCode(
                            "refactor.rewrite.delete",
                            "Supprimer « ${cible.nom} » et sa hiérarchie",
                            ModCommande.Differee(CLE_SUPPRIMER_HIERARCHIQUE, listOf(position.toString())),
                        ),
                    )
                }
            }
        }
        return resultat
    }

    /**
     * La RÉSOLUTION d'une commande différée de refactoring : les éditions
     * recalculées DÉTERMINISTEMENT depuis la source. Les clés
     * [CLE_SUPPRIMER_SURE] et [CLE_METTRE_EN_LIGNE_CONSTANTE] exigent la
     * garde des usages MULTI-FICHIERS : elles vivent au niveau moteur
     * ([MoteurJava.resoudreAction]), pas ici.
     *
     * @param cle la clé du générateur.
     * @param arguments les arguments (positions dans la source).
     * @return les éditions, ou null (clé inconnue / cible disparue / refus
     *   honnête de l'heuristique).
     */
    public fun resoudre(cle: String, arguments: List<String>): List<EditionTexte>? {
        if (arbre == null) return null
        val positions = arguments.mapNotNull { it.toIntOrNull() }
        return when (cle) {
            CLE_EXTRAIRE_VARIABLE -> positions.takeIf { it.size >= 2 }?.let { extraireVariableEditions(it[0], it[1]) }
            CLE_EXTRAIRE_CONSTANTE -> positions.takeIf { it.size >= 2 }?.let { extraireConstanteEditions(it[0], it[1]) }
            CLE_EXTRAIRE_METHODE -> positions.takeIf { it.size >= 2 }?.let { extraireMethodeEditions(it[0], it[1]) }
            CLE_METTRE_EN_LIGNE -> positions.firstOrNull()?.let { mettreEnLigneEditions(it) }
            CLE_METTRE_EN_LIGNE_CONSTANTE -> positions.firstOrNull()?.let { mettreEnLigneConstanteEditions(it) }
            else -> null
        }
    }

    /**
     * Les éditions de SUPPRESSION de la déclaration au caret (le bras
     * ÉDITIONS du safe delete ; la garde des usages est AMONT, au niveau
     * moteur [MoteurJava.suppressionSure]).
     *
     * @param offset le caret (sur le NOM de la déclaration).
     * @return l'édition unique de suppression, ou null (caret sans
     *   déclaration supprimable / refus honnête).
     */
    public fun suppressionEditions(offset: Int): List<EditionTexte>? {
        val plage = plageSupprimable(offset) ?: return null
        return listOf(EditionTexte(plage.first, plage.last + 1 - plage.first, ""))
    }

    /**
     * La cible de suppression au caret (l'oracle du banc safe-delete).
     *
     * @param offset le caret.
     * @return la cible, ou null (rien de supprimable au caret).
     */
    public fun cibleSuppression(offset: Int): CibleSuppression? {
        // LOCALE : le caret porte le jeton du nom (mesuré : sourceStart
        // d'une LocalDeclaration EST le début du nom).
        structure.locales.firstOrNull { porteLeNom(it.sourceStart, it.name, offset) }?.let {
            return CibleSuppression(String(it.name), "local", null, it)
        }
        // CHAMP : idem (le jeton du nom se CHERCHE — les modificateurs
        // précèdent, sourceStart n'est pas garanti être le nom).
        structure.champs.firstOrNull { porteNomJeton(it.declarationSourceStart, it.declarationSourceEnd, String(it.name), offset) }?.let {
            return CibleSuppression(String(it.name), "field", null, it)
        }
        // MÉTHODE/CONSTRUCTEUR : refus honnête si la méthode OUTREPASSE un
        // supertype (la clé d'usages ne suit PAS les overrides : un appel
        // via le supertype ne compterait pas — suivre la hiérarchie
        // relève de la tranche 3, avec change signature). Le constructeur
        // PAR DÉFAUT synthétique (positionné sur le nom du type, mesuré)
        // ne compte JAMAIS — c'est le TYPE qui est visé.
        for (methode in structure.methodes) {
            if (methode is ConstructorDeclaration && runCatching { methode.isDefaultConstructor() }.getOrDefault(false)) continue
            if (!porteNomJeton(methode.declarationSourceStart, methode.declarationSourceEnd, String(methode.selector), offset)) continue
            val nom = String(methode.selector)
            if (methode is ConstructorDeclaration) {
                return CibleSuppression(nom, "method", null, methode)
            }
            if (methode is MethodDeclaration && outrepasse(methode)) {
                return CibleSuppression(nom, "method", "override", methode)
            }
            return CibleSuppression(nom, "method", null, methode)
        }
        // TYPE : le caret porte le nom du type.
        structure.types.firstOrNull { porteNomJeton(it.declarationSourceStart, it.declarationSourceEnd, String(it.name), offset) }?.let {
            return CibleSuppression(String(it.name), "type", null, it)
        }
        // PARAMÈTRE : refus honnête — supprimer un paramètre réécrit les
        // APPELANTS (périmètre change-signature, tranche 3).
        structure.arguments.firstOrNull { porteLeNom(it.sourceStart, it.name, offset) }?.let {
            return CibleSuppression(String(it.name), "parameter", "parametre", it)
        }
        return null
    }

    // ------------------------------------------------------------------ //
    // Extract variable / constante
    // ------------------------------------------------------------------ //

    /** Extract VARIABLE : `Type nom = <expr>;` insérée avant l'instruction de bloc porteuse, sélection remplacée par le nom. */
    private fun extraireVariableEditions(debut: Int, fin: Int): List<EditionTexte>? {
        val expr = expressionCouverte(debut, fin) ?: return null
        val type = typeLisible(expr) ?: return null
        if (type == "void") return null
        // Une sélection qui couvre EXACTEMENT des instructions désigne des
        // INSTRUCTIONS (l'expression en serait un fragment trompeur).
        if (plageInstructions(debut, fin) != null) return null
        // Code DROIT uniquement (R1) : la déclaration s'insère AVANT
        // l'instruction — une expression en position paresseuse changerait
        // de moment d'évaluation.
        if (!estEnCodeDroit(expr)) return null
        val ancre = instructionDeBloc(expr) ?: return null
        val methode = methodeEnglobante(expr) ?: return null
        val nom = nomVariableDisponible(type, nomsPrisDans(methode))
        // MESURÉ : l'insertion se fait au DÉBUT DE LIGNE de l'instruction
        // porteuse (convention tranche 1) — jamais au début du nom.
        val offsetAncre = debutDeLigne(debutComplet(ancre))
        val indent = indentationDe(offsetAncre)
        val declaration = "$indent$type $nom = ${texte(expr)};\n"
        return listOf(
            EditionTexte(offsetAncre, 0, declaration),
            EditionTexte(expr.sourceStart, expr.sourceEnd + 1 - expr.sourceStart, nom),
        )
    }

    /** Extract CONSTANTE : `private static final Type NOM = <expr>;` en tête des membres du type englobant. */
    private fun extraireConstanteEditions(debut: Int, fin: Int): List<EditionTexte>? {
        val expr = expressionCouverte(debut, fin) ?: return null
        if (!estConstante(expr)) return null
        if (plageInstructions(debut, fin) != null) return null
        val type = typeLisible(expr) ?: return null
        val typeEnglobant = typeEnglobantDe(expr) ?: return null
        val nom = nomConstanteDisponible(expr, nomsConstantesDans(typeEnglobant))
        val ancre = ancreMembreDe(typeEnglobant) ?: return null
        val indent = indentationDe(ancre)
        val declaration = "${indent}private static final $type $nom = ${texte(expr)};\n"
        return listOf(
            EditionTexte(ancre, 0, declaration),
            EditionTexte(expr.sourceStart, expr.sourceEnd + 1 - expr.sourceStart, nom),
        )
    }

    // ------------------------------------------------------------------ //
    // Extract méthode
    // ------------------------------------------------------------------ //

    /** Extract MÉTHODE : expression couverte (appel sur place) ou plage d'instructions. */
    private fun extraireMethodeEditions(debut: Int, fin: Int): List<EditionTexte>? {
        // Une sélection qui couvre EXACTEMENT des instructions désigne des
        // INSTRUCTIONS — l'expression en serait un fragment trompeur.
        return if (plageInstructions(debut, fin) != null) {
            extraireMethodeInstructions(debut, fin)
        } else {
            extraireMethodeExpression(debut, fin)
        }
    }

    /** CAS EXPRESSION : la méthode rend le type de l'expression ; l'appel REMPLACE l'expression sur place (ordre d'évaluation préservé). */
    private fun extraireMethodeExpression(debut: Int, fin: Int): List<EditionTexte>? {
        val expr = expressionCouverte(debut, fin) ?: return null
        if (dansLambda(expr)) return null // la portée de la lambda ne suit pas
        val type = typeLisible(expr) ?: return null
        if (type == "void") return null
        val methode = methodeEnglobante(expr) ?: return null
        val typeEnglobant = typeEnglobantDe(expr) ?: return null
        // Une expression qui ÉCRIT (`x = (y = 5)`) ne s'extrait pas.
        if (structure.ecritures.any { it.offset >= expr.sourceStart && it.offset <= expr.sourceEnd }) return null
        val parametres = parametresDe(expr, methode)
        val nomMethode = nomMethodeDisponible(typeEnglobant, if (type == "boolean") "verifier" else "calculer")
        val indent = indentationDe(methode.declarationSourceStart)
        val signature = signaturePrivee(nomMethode, parametres, type, estStatique(methode))
        val corps = "${indent}    return ${texte(expr)};\n"
        val nouvelleMethode = "\n\n$indent$signature\n$corps$indent}\n"
        val appel = "$nomMethode(${parametres.joinToString(", ") { it.second }})"
        return listOf(
            EditionTexte(expr.sourceStart, expr.sourceEnd + 1 - expr.sourceStart, appel),
            EditionTexte(methode.declarationSourceEnd + 1, 0, nouvelleMethode),
        )
    }

    /** CAS INSTRUCTIONS : une plage CONTIGUË d'instructions d'un même bloc (heuristiques §A5 de la référence). */
    private fun extraireMethodeInstructions(debut: Int, fin: Int): List<EditionTexte>? {
        val run = plageInstructions(debut, fin) ?: return null
        val methode = methodeEnglobante(run.instructions.first()) ?: return null
        // Contrôle de flux NON LOCAL dans la sélection (return, break,
        // continue, label) : refus honnête (R1) — pas de transformation fausse.
        if (run.instructions.any { contientControleNonLocal(it) }) return null
        val locales = localesDe(methode) // binding → (nom, offset de déclaration)
        val lecturesRun = structure.lectures.filter { it.debut >= debut && it.debut < fin }
        val ecrituresRun = structure.ecritures.filter { it.offset >= debut && it.offset < fin }
        val lecturesApres = structure.lectures.filter { it.debut >= fin && it.debut <= methode.declarationSourceEnd }
        // Premières lectures / écritures par binding.
        val premieresLectures = LinkedHashMap<LocalVariableBinding, Int>()
        for (l in lecturesRun) if (locales.containsKey(l.binding) && l.binding !in premieresLectures) premieresLectures[l.binding] = l.debut
        val premieresEcritures = HashMap<LocalVariableBinding, Int>()
        for (e in ecrituresRun) if (locales.containsKey(e.binding) && e.binding !in premieresEcritures) premieresEcritures[e.binding] = e.offset
        // Les locales déclarées AVANT la sélection et RÉFÉRENCÉES dedans se
        // partagent en deux familles :
        //  - ENTRÉE : lue avant toute écriture (sa valeur d'entrée compte) ;
        //  - À DÉCLARER : écrite avant toute lecture (sa valeur d'entrée n'est
        //    jamais lue — la méthode extraite la déclare LOCALEMENT, miroir du
        //    comportement de la référence).
        val entrees = ArrayList<Pair<LocalVariableBinding, String>>()
        val aDeclarer = ArrayList<Pair<LocalVariableBinding, String>>()
        for ((binding, info) in locales) {
            val (nom, offsetDecl) = info
            if (offsetDecl >= debut) continue
            val premiereLecture = premieresLectures[binding]
            val premiereEcriture = premieresEcritures[binding]
            if (premiereLecture == null && premiereEcriture == null) continue
            if (premiereEcriture == null || (premiereLecture != null && premiereLecture < premiereEcriture)) {
                entrees.add(binding to nom)
            } else {
                aDeclarer.add(binding to nom)
            }
        }
        // SORTIE (au plus UNE) : locale lue APRÈS la sélection et
        // (déclarée dedans OU écrite dedans) — les écritures INTERNES
        // multiples suivent l'extrait sans dommage (la méthode rend la
        // valeur FINALE, miroir de la référence). Le TROISIÈME champ dit
        // si la déclaration A DÉMÉNAGÉ dans la méthode (l'appel déclare)
        // ou RESTE au site (l'appel ASSIGNE).
        var sortie: Triple<LocalVariableBinding, String, Boolean>? = null
        for ((binding, info) in locales) {
            val (nom, offsetDecl) = info
            if (!lecturesApres.any { it.binding === binding }) continue
            val declareDedans = offsetDecl >= debut
            val nbEcritures = ecrituresRun.count { it.binding === binding }
            if (declareDedans || nbEcritures > 0) {
                if (sortie != null) return null
                sortie = Triple(binding, nom, declareDedans)
            }
        }
        val typeSortie = sortie?.let { lisible(it.first) } ?: "void"
        val typeEnglobant = typeEnglobantDe(run.instructions.first()) ?: return null
        val nomMethode = nomMethodeDisponible(
            typeEnglobant,
            if (typeSortie == "void") "executer" else if (typeSortie == "boolean") "verifier" else "calculer",
        )
        val indent = indentationDe(methode.declarationSourceStart)
        val signature = signaturePrivee(nomMethode, entrees, typeSortie, estStatique(methode))
        val declarationsLocales = aDeclarer.joinToString("") { (b, n) -> "${indent}    ${lisible(b) ?: "var"} $n;\n" }
        // RÉ-INDENTATION RELATIVE : l'indent de la ligne de la sélection est
        // la BASE — chaque ligne garde sa profondeur relative, translatée
        // au corps de la nouvelle méthode (la sélection peut démarrer en
        // MILIEU de ligne : sa première ligne n'a pas d'indent).
        val corpsBrut = texteEntre(debut, fin)
        val baseIndent = indentationDe(debut).length
        val cibleIndent = indent.length + 4
        val corps = reindente(corpsBrut, baseIndent, cibleIndent).let { if (it.endsWith("\n")) it else it + "\n" }
        val retour = if (sortie != null) "${indent}    return ${sortie.second};\n" else ""
        val nouvelleMethode = "\n\n$indent$signature\n$declarationsLocales$corps$retour$indent}\n"
        val arguments = entrees.joinToString(", ") { it.second }
        val appel = when {
            sortie == null -> "$nomMethode($arguments);"
            sortie.third -> "$typeSortie ${sortie.second} = $nomMethode($arguments);"
            else -> "${sortie.second} = $nomMethode($arguments);"
        }
        return listOf(
            EditionTexte(debut, fin - debut, appel),
            EditionTexte(methode.declarationSourceEnd + 1, 0, nouvelleMethode),
        )
    }

    // ------------------------------------------------------------------ //
    // Inline variable / constante
    // ------------------------------------------------------------------ //

    /** Inline LOCALE : chaque LECTURE remplacée par l'initialisateur, déclaration supprimée ; les RÉAFFECTATIONS ferment la porte. */
    private fun mettreEnLigneEditions(offset: Int): List<EditionTexte>? {
        val locale = structure.locales.firstOrNull { porteLeNom(it.sourceStart, it.name, offset) } ?: return null
        val init = locale.initialization ?: return null
        // Une locale RÉAFFECTÉE (affectation, composée, incrément) ne se
        // met pas en ligne — sa valeur n'est pas l'initialisateur.
        if (structure.ecritures.any { it.binding === locale.binding }) return null
        // Un initialisateur qui ÉCRIT (`int x = (y = 5);`) n'est jamais inlinable.
        if (structure.ecritures.any { it.offset >= init.sourceStart && it.offset <= init.sourceEnd }) return null
        val usages = structure.lectures.filter { it.binding === locale.binding }.sortedBy { it.debut }
        if (usages.size > 1 && !estPur(init)) return null // ré-évaluer un effet : non
        if (usages.size == 1 && !estPur(init) && dansPlageLambda(usages[0].debut, usages[0].fin)) return null
        val texteInit = texte(init).replace(Regex("\\s*\n\\s*"), " ")
        val parenthese = init is BinaryExpression || init is ConditionalExpression || init is UnaryExpression ||
            init is EqualExpression || init is AND_AND_Expression || init is OR_OR_Expression || init is LambdaExpression
        val remplacement = if (parenthese) "($texteInit)" else texteInit
        val editions = usages.map { EditionTexte(it.debut, it.fin + 1 - it.debut, remplacement) }.toMutableList()
        val plage = plageLigneSupprimable(locale.declarationSourceStart, locale.declarationSourceEnd)
        editions.add(EditionTexte(plage.first, plage.last + 1 - plage.first, ""))
        return editions.sortedBy { it.offset }
    }

    /** Inline CONSTANTE (champ static final à initialisateur constant) — les usages du FICHIER COURANT seulement (garde moteur). */
    private fun mettreEnLigneConstanteEditions(offset: Int): List<EditionTexte>? {
        val champ = structure.champs.firstOrNull { porteNomJeton(it.declarationSourceStart, it.declarationSourceEnd, String(it.name), offset) } ?: return null
        if (!estStatiqueFinal(champ)) return null
        val init = champ.initialization ?: return null
        if (!estConstante(init)) return null
        val texteInit = texte(init)
        // Une valeur NÉGATIVE se met entre parenthèses (`X * 2` avec X = -42).
        val remplacement = if (init is UnaryExpression) "($texteInit)" else texteInit
        val usages = usagesFichierCourantDuChamp(champ)
        val editions = usages.map { EditionTexte(it.sourceStart, it.sourceEnd + 1 - it.sourceStart, remplacement) }.toMutableList()
        val plage = plageLigneSupprimable(champ.declarationSourceStart, champ.declarationSourceEnd)
        editions.add(EditionTexte(plage.first, plage.last + 1 - plage.first, ""))
        return editions.sortedBy { it.offset }
    }

    /** Les références du champ dans l'unité courante (la référence ENTIÈRE est remplacée par la valeur — `demo.Main.MAX` devient `42`). */
    private fun usagesFichierCourantDuChamp(champ: FieldDeclaration): List<ASTNode> {
        val bindingChamp = runCatching { champ.binding }.getOrNull()
        val cibles = mutableListOf<ASTNode>()
        for (ref in structure.referencesSimples) {
            if (runCatching { ref.binding }.getOrNull() === bindingChamp) cibles.add(ref)
        }
        for (ref in structure.referencesQualifiees) {
            val segments = runCatching { segmentsDe(ref) }.getOrNull() ?: continue
            if (segments.any { it === bindingChamp }) cibles.add(ref)
        }
        for (ref in structure.referencesChamp) {
            if (runCatching { ref.binding }.getOrNull() === bindingChamp) cibles.add(ref)
        }
        return cibles.distinctBy { it.sourceStart to it.sourceEnd }.sortedBy { it.sourceStart }
    }

    // ------------------------------------------------------------------ //
    // Suppression (bras éditions — la garde des usages vit au moteur)
    // ------------------------------------------------------------------ //

    /** La plage supprimable de la déclaration au caret (bornes de ligne avalées quand la ligne ne porte qu'elle). */
    private fun plageSupprimable(offset: Int): IntRange? {
        val cible = cibleSuppression(offset) ?: return null
        if (cible.refus != null) return null
        val noeud = cible.noeud
        val debut = debutComplet(noeud)
        val fin = finComplet(noeud)
        if (debut < 0 || fin < debut || fin >= source.length) return null
        return plageLigneSupprimable(debut, fin)
    }

    /** La plage de suppression ÉTENDUE : blancs de tête de ligne et retour de ligne avalés. */
    private fun plageLigneSupprimable(debut: Int, fin: Int): IntRange {
        var d = debut
        var f = fin + 1
        while (d > 0 && source[d - 1] != '\n' && source[d - 1].isWhitespace()) d--
        if (f < source.length && source[f] == '\n') {
            f++
        } else if (f < source.length && source[f].isWhitespace()) {
            var i = f
            while (i < source.length && source[i] != '\n' && source[i].isWhitespace()) i++
            if (i < source.length && source[i] == '\n') f = i + 1
        }
        return d..f - 1
    }

    // ------------------------------------------------------------------ //
    // Structure collectée (UNE traversée résiliente, ordre source)
    // ------------------------------------------------------------------ //

    /** Une lecture de locale : plage du SEGMENT lu, binding, nom. */
    private data class Lecture(val debut: Int, val fin: Int, val binding: LocalVariableBinding, val nom: String)

    /** Une écriture de locale : offset de la cible, binding. */
    private data class Ecriture(val offset: Int, val binding: LocalVariableBinding)

    /** La cible de suppression au caret (l'oracle du banc safe-delete). */
    public data class CibleSuppression(
        /** Le nom lisible du symbole visé. */
        public val nom: String,
        /** Le genre : local / field / method / type / parameter. */
        public val genre: String,
        /** Le motif de refus honnête (null = supprimable) : override, parametre. */
        public val refus: String?,
        /** Le nœud de la déclaration. */
        public val noeud: ASTNode,
    )

    /** Le collecteur structurel : instructions, expressions, lectures, écritures. */
    private inner class Structure : LectureAst.VisiteurResilient() {
        val methodes = ArrayList<AbstractMethodDeclaration>()
        val types = ArrayList<TypeDeclaration>()
        val champs = ArrayList<FieldDeclaration>()
        val locales = ArrayList<LocalDeclaration>()
        val arguments = ArrayList<Argument>()
        val blocs = ArrayList<Block>()
        val lambdas = ArrayList<LambdaExpression>()
        val instructions = ArrayList<Statement>()
        val expressions = ArrayList<Expression>()
        val lectures = ArrayList<Lecture>()
        val ecritures = ArrayList<Ecriture>()
        val referencesSimples = ArrayList<SingleNameReference>()
        val referencesQualifiees = ArrayList<QualifiedNameReference>()
        val referencesChamp = ArrayList<FieldReference>()

        /** Les offsets des CIBLES d'affectation vus jusqu'ici (une cible N'EST PAS une lecture). */
        val ciblesAffectation = ArrayList<Int>()

        private fun retenirInstruction(s: Statement) {
            instructions.add(s)
        }

        private fun retenirExpression(e: Expression) {
            expressions.add(e)
        }

        private fun lectureSimple(node: SingleNameReference) {
            val b = runCatching { node.binding }.getOrNull() as? LocalVariableBinding ?: return
            // La CIBLE d'une affectation (`x = …`, `x += …`, `x++`) n'est pas
            // une LECTURE — son binding arrive APRÈS le visit de l'instruction
            // porteuse (ordre parent-puis-enfants d'ecj, mesuré au banc).
            if (node.sourceStart in ciblesAffectation) return
            lectures.add(Lecture(node.sourceStart, node.sourceEnd, b, String(node.token)))
        }

        private fun ecritureSur(cible: Expression?) {
            val nom = cible as? SingleNameReference ?: return
            ciblesAffectation.add(nom.sourceStart)
            val b = runCatching { nom.binding }.getOrNull() as? LocalVariableBinding ?: return
            ecritures.add(Ecriture(nom.sourceStart, b))
        }

        // --- conteneurs et déclarations ---
        override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            methodes.add(node)
            return true
        }

        override fun visiteConstructeur(node: ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            methodes.add(node)
            return true
        }

        override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
            types.add(node)
            return true
        }

        override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
            champs.add(node)
            return true
        }

        override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            locales.add(node)
            retenirInstruction(node)
            return true
        }

        override fun visiteArgument(node: Argument, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            arguments.add(node)
            return true
        }

        override fun visit(node: Block, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            blocs.add(node)
            retenirInstruction(node)
            return true
        }

        override fun visiteLambda(node: LambdaExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            lambdas.add(node)
            retenirExpression(node)
            return true
        }

        // --- instructions ---
        override fun visiteRetour(node: ReturnStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: IfStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: ForStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: ForeachStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: WhileStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: DoStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: TryStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: SwitchStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: ThrowStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: BreakStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: ContinueStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: AssertStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: LabeledStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: SynchronizedStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: EmptyStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: CaseStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: YieldStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        override fun visit(node: ExplicitConstructorCall, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            return true
        }

        // --- instructions-expressions (un envoi/affectation/incrément nu EST une instruction) ---
        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            retenirExpression(node)
            return true
        }

        override fun visiteAffectation(node: Assignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            retenirExpression(node)
            ecritureSur(node.lhs)
            return true
        }

        override fun visiteAffectationComposee(node: CompoundAssignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            retenirExpression(node)
            ecritureSur(node.lhs)
            return true
        }

        override fun visitePostfixe(node: PostfixExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            retenirExpression(node)
            ecritureSur(node.lhs)
            return true
        }

        override fun visitePrefixe(node: PrefixExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            retenirExpression(node)
            ecritureSur(node.lhs)
            return true
        }

        override fun visiteAllocation(node: AllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            retenirExpression(node)
            return true
        }

        override fun visit(node: QualifiedAllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirInstruction(node)
            retenirExpression(node)
            return true
        }

        // --- expressions ---
        override fun visiteReferenceSimple(node: SingleNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            referencesSimples.add(node)
            retenirExpression(node)
            lectureSimple(node)
            return true
        }

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            referencesQualifiees.add(node)
            retenirExpression(node)
            runCatching {
                val positions = node.sourcePositions
                val b = node.binding
                val p0 = positions.getOrNull(0) ?: return@runCatching
                val debut = (p0 ushr 32).toInt()
                val finSegment = p0.toInt()
                if (b is LocalVariableBinding) {
                    lectures.add(Lecture(debut, finSegment, b, String(node.tokens?.firstOrNull() ?: CharArray(0))))
                }
            }
            return true
        }

        override fun visiteReferenceChamp(node: FieldReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            referencesChamp.add(node)
            retenirExpression(node)
            runCatching {
                val recepteur = node.receiver as? SingleNameReference ?: return@runCatching
                val b = recepteur.binding as? LocalVariableBinding ?: return@runCatching
                lectures.add(Lecture(recepteur.sourceStart, recepteur.sourceEnd, b, String(recepteur.token)))
            }
            return true
        }

        override fun visiteBinaire(node: BinaryExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visiteEgalite(node: EqualExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: AND_AND_Expression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: OR_OR_Expression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: UnaryExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: InstanceOfExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visiteConditionnelle(node: ConditionalExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visiteCast(node: CastExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: ArrayAllocationExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: ArrayInitializer, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: ArrayReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: ClassLiteralAccess, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: ThisReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: QualifiedThisReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: SuperReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: QualifiedSuperReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: ReferenceExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: IntLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: LongLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: FloatLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: DoubleLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: StringLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: ExtendedStringLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: StringLiteralConcatenation, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: CharLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: TrueLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: FalseLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }

        override fun visit(node: NullLiteral, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            retenirExpression(node)
            return true
        }
    }

    // ------------------------------------------------------------------ //
    // Analyse structurelle (relations par plage — ecj n'a pas de parent)
    // ------------------------------------------------------------------ //

    /** Le début COMPLET d'un nœud (bornes de déclaration quand elles existent — le `;`/`}` est avalé). */
    private fun debutComplet(n: ASTNode): Int = when (n) {
        is AbstractMethodDeclaration -> n.declarationSourceStart
        is FieldDeclaration -> n.declarationSourceStart
        is TypeDeclaration -> n.declarationSourceStart
        is LocalDeclaration -> n.declarationSourceStart
        is Argument -> n.declarationSourceStart
        else -> n.sourceStart
    }

    /** La fin COMPLÈTE d'un nœud (bornes de déclaration quand elles existent). */
    private fun finComplet(n: ASTNode): Int = when (n) {
        is AbstractMethodDeclaration -> n.declarationSourceEnd
        is FieldDeclaration -> n.declarationSourceEnd
        is TypeDeclaration -> n.declarationSourceEnd
        is LocalDeclaration -> n.declarationSourceEnd
        is Argument -> n.declarationSourceEnd
        else -> n.sourceEnd
    }

    /** Vrai sss [grand] couvre [petit] (bornes complètes comprises). */
    private fun couvre(grand: ASTNode, petit: ASTNode): Boolean {
        if (grand === petit) return false
        val gd = debutComplet(grand)
        val gf = finComplet(grand)
        return gd <= petit.sourceStart && gf >= petit.sourceEnd && gf >= gd
    }

    /** La sélection rognée des blancs de bord. */
    private fun rogneBlanc(debut: Int, fin: Int): Pair<Int, Int> {
        var d = debut.coerceIn(0, source.length)
        var f = fin.coerceIn(0, source.length)
        while (d < f && source[d].isWhitespace()) d++
        while (f > d && source[f - 1].isWhitespace()) f--
        return d to f
    }

    /** La plus grande EXPRESSION couverte par la sélection (la sélection doit couvrir une expression complète). */
    private fun expressionCouverte(debut: Int, fin: Int): Expression? =
        structure.expressions
            .filter { it.sourceStart >= debut && it.sourceEnd < fin && it.sourceEnd >= it.sourceStart }
            .maxWithOrNull(compareBy({ it.sourceEnd - it.sourceStart }, { it.sourceStart }))

    /** L'instruction DE BLOC porteuse : l'enfant DIRECT du bloc (ou de la méthode) le plus proche qui contient le nœud. */
    private fun instructionDeBloc(noeud: ASTNode): Statement? {
        // Le nœud EST lui-même une instruction enfant directe (un envoi/affectation
        // nu au niveau d'un bloc — mesuré : ces expressions SONT des instructions).
        if (noeud is Statement) {
            val conteneur = conteneurDirect(noeud)
            if (conteneur is Block || conteneur is AbstractMethodDeclaration) return noeud
        }
        // Le conteneur de PLUS BAS NIVEAU (bloc imbriqué le plus profond, ou
        // la méthode — mesuré : le corps d'une méthode n'est PAS un Block).
        val blocConteneur: ASTNode = structure.blocs
            .filter { couvre(it, noeud) }
            .minByOrNull { it.sourceEnd - it.sourceStart }
            ?: structure.methodes.filter { couvre(it, noeud) }.minByOrNull { it.declarationSourceEnd - it.declarationSourceStart }
            ?: return null
        // L'enfant direct de ce conteneur qui contient le nœud (unique —
        // l'imbrication est un arbre) : l'instruction de HAUT NIVEAU.
        return structure.instructions
            .filter { couvre(it, noeud) && conteneurDirect(it) === blocConteneur }
            .maxByOrNull { it.sourceEnd - it.sourceStart }
    }

    /** Le conteneur DIRECT d'une instruction : le plus petit nœud (instruction, bloc ou méthode) la contenant strictement. */
    private fun conteneurDirect(s: Statement): ASTNode? {
        val contendants = ArrayList<ASTNode>()
        for (autre in structure.instructions) if (couvre(autre, s)) contendants.add(autre)
        for (b in structure.blocs) if (couvre(b, s)) contendants.add(b)
        for (m in structure.methodes) if (couvre(m, s)) contendants.add(m)
        return contendants.minByOrNull { finComplet(it) - debutComplet(it) }
    }

    /** La méthode englobante du nœud. */
    private fun methodeEnglobante(noeud: ASTNode): AbstractMethodDeclaration? =
        structure.methodes.filter { couvre(it, noeud) }.minByOrNull { it.declarationSourceEnd - it.declarationSourceStart }

    /** Le type englobant du nœud. */
    private fun typeEnglobantDe(noeud: ASTNode): TypeDeclaration? =
        structure.types.filter { couvre(it, noeud) }.minByOrNull { it.declarationSourceEnd - it.declarationSourceStart }

    /** Vrai ssi le nœud vit dans le corps d'une lambda. */
    private fun dansLambda(noeud: ASTNode): Boolean = dansPlageLambda(noeud.sourceStart, noeud.sourceEnd)

    /** Vrai sss la plage vit dans le corps d'une lambda. */
    private fun dansPlageLambda(debut: Int, fin: Int): Boolean =
        structure.lambdas.any { it.sourceStart <= debut && it.sourceEnd >= fin && !(it.sourceStart == debut && it.sourceEnd == fin) }

    /** Vrai ssi le caret porte le jeton du nom (bornes du nom, texte exact). */
    private fun porteLeNom(debutNom: Int, nom: CharArray, offset: Int): Boolean =
        offset >= debutNom && offset < debutNom + nom.size

    /** Vrai ssi le caret porte le jeton du nom CHERCHÉ dans la déclaration (modificateurs compris dans les bornes). */
    private fun porteNomJeton(declStart: Int, declEnd: Int, nom: String, offset: Int): Boolean {
        val plage = jo.codeide.lsp.java.renommage.plageJeton(source, declStart, declEnd + 1, nom) ?: return false
        return offset >= plage.debutOffset && offset < plage.finOffset
    }

    /** Vrai sss l'expression vit en code DROIT — pas en position paresseuse (R1 : refus honnête, jamais de transformation fausse). */
    private fun estEnCodeDroit(expr: Expression): Boolean {
        val contendants = structure.expressions.filter { couvre(it, expr) } +
            structure.instructions.filter { couvre(it, expr) } +
            structure.lambdas.filter { couvre(it, expr) }
        for (contenant in contendants) {
            if (contenant is AND_AND_Expression || contenant is OR_OR_Expression) return false
            if (contenant is ConditionalExpression) return false
            if (contenant is AssertStatement) return false
            if (contenant is LambdaExpression) return false
            if (contenant is ForeachStatement) {
                val collection = contenant.collection
                if (collection != null && recouvre(collection, expr)) return false
            }
            val condition = when (contenant) {
                is IfStatement -> contenant.condition
                is WhileStatement -> contenant.condition
                is DoStatement -> contenant.condition
                is ForStatement -> contenant.condition
                else -> null
            }
            // La condition COMPLÈTE d'une boucle est ré-évaluée : même
            // l'extraire ENTIÈRE changerait la sémantique (identité comprise).
            if (condition != null && recouvre(condition, expr)) return false
        }
        return true
    }

    /** Vrai sss [petit] vit dans les bornes de [grand] — IDENTITÉ COMPRISE (le cas « l'expression EST la condition »). */
    private fun recouvre(grand: ASTNode, petit: ASTNode): Boolean =
        grand.sourceStart <= petit.sourceStart && grand.sourceEnd >= petit.sourceEnd

    /** Vrai sss l'instruction (ou une instruction IMBRIQUÉE) porte un contrôle de flux non local. */
    private fun contientControleNonLocal(s: Statement): Boolean {
        if (s is ReturnStatement || s is BreakStatement || s is ContinueStatement || s is LabeledStatement) return true
        return structure.instructions.any {
            it !== s && couvre(s, it) && (it is ReturnStatement || it is BreakStatement || it is ContinueStatement || it is LabeledStatement)
        }
    }

    /** Les locales (paramètres ET déclarations) d'une méthode : binding → (nom, offset de déclaration). */
    private fun localesDe(methode: AbstractMethodDeclaration): LinkedHashMap<LocalVariableBinding, Pair<String, Int>> {
        val locales = LinkedHashMap<LocalVariableBinding, Pair<String, Int>>()
        for (argument in structure.arguments) {
            if (!couvre(methode, argument)) continue
            val b = runCatching { argument.binding }.getOrNull() as? LocalVariableBinding ?: continue
            locales[b] = String(argument.name) to argument.declarationSourceStart
        }
        for (locale in structure.locales) {
            if (!couvre(methode, locale)) continue
            val b = runCatching { locale.binding }.getOrNull() as? LocalVariableBinding ?: continue
            locales[b] = String(locale.name) to locale.declarationSourceStart
        }
        return locales
    }

    /** Les ENTRÉES d'une expression : locales lues, ordre de première lecture. */
    private fun parametresDe(expr: Expression, methode: AbstractMethodDeclaration): List<Pair<LocalVariableBinding, String>> {
        val locales = localesDe(methode)
        val premieres = LinkedHashMap<LocalVariableBinding, String>()
        for (lecture in structure.lectures.filter { it.debut >= expr.sourceStart && it.fin <= expr.sourceEnd }.sortedBy { it.debut }) {
            val nom = locales[lecture.binding]?.first ?: continue
            if (lecture.binding !in premieres) premieres[lecture.binding] = nom
        }
        return premieres.map { it.key to it.value }
    }

    /** Une plage d'instructions CONTIGUËS d'un même conteneur couverte EXACTEMENT par la sélection. */
    private data class RunInstructions(val instructions: List<Statement>, val conteneur: ASTNode)

    private fun plageInstructions(debut: Int, fin: Int): RunInstructions? {
        for (methode in structure.methodes) {
            if (debut < methode.declarationSourceStart || fin > methode.declarationSourceEnd + 1) continue
            val candidats = structure.instructions
                .filter { couvre(methode, it) && conteneurDirect(it).let { c -> c is AbstractMethodDeclaration || c is Block } }
                .sortedBy { it.sourceStart }
            val couvertes = candidats.filter { debutComplet(it) >= debut && finAvecPointVirgule(it) <= fin }
            if (couvertes.isEmpty()) continue
            val premier = couvertes.first()
            if (debutComplet(premier) != debut) continue
            // La FAMILLE est celle du conteneur du PREMIER couvert : les
            // instructions IMBRIQUÉES (le corps d'un if couvert, p. ex.) ne
            // comptent JAMAIS dans la plage — seule la rangée CONTIGUË du
            // MÊME conteneur s'extrait (mesuré : sans ce filtre, le return
            // d'un bloc then volait la place de dernier couvert).
            val conteneur = conteneurDirect(premier) ?: continue
            val famille = couvertes.filter { conteneurDirect(it) === conteneur }
            if (famille.isEmpty()) continue
            if (finAvecPointVirgule(famille.last()) != fin) continue
            val voisins = candidats.filter { conteneurDirect(it) === conteneur }
            val entre = voisins.filter { it.sourceStart >= premier.sourceStart && it.sourceStart <= famille.last().sourceStart }
            if (entre.any { it !in famille }) continue
            return RunInstructions(famille, conteneur)
        }
        return null
    }

    /** La fin d'une instruction en avalant le point-virgule (une instruction-expression ne le porte PAS dans ses bornes — mesuré). */
    private fun finAvecPointVirgule(s: Statement): Int {
        val fin = finComplet(s)
        var i = fin + 1
        while (i < source.length && (source[i] == ' ' || source[i] == '\t')) i++
        return if (i < source.length && source[i] == ';') i + 1 else fin + 1
    }

    // ------------------------------------------------------------------ //
    // Noms et textes
    // ------------------------------------------------------------------ //

    /** Le texte source d'une EXPRESSION (bornes strictes — les parenthèses externes n'appartiennent à aucun nœud ecj). */
    private fun texte(noeud: ASTNode): String {
        val debut: Int
        val fin: Int
        if (noeud is Expression) {
            debut = noeud.sourceStart
            fin = noeud.sourceEnd + 1
        } else {
            debut = debutComplet(noeud)
            fin = finComplet(noeud) + 1
        }
        val borneDebut = if (debut < 0) 0 else debut
        val borneFin = if (fin > source.length) source.length else fin
        return source.substring(borneDebut, borneFin)
    }

    /** Le texte entre deux offsets. */
    private fun texteEntre(debut: Int, fin: Int): String =
        source.substring(debut.coerceIn(0, source.length), fin.coerceIn(0, source.length))

    /** Le type lisible résolu d'une expression (null si non résolu). */
    private fun typeLisible(expr: Expression): String? = runCatching {
        expr.resolvedType?.readableName()?.let { String(it) }
    }.getOrNull()

    /** Le type lisible d'un binding de locale. */
    private fun lisible(binding: LocalVariableBinding): String? = runCatching {
        binding.type?.readableName()?.let { String(it) }
    }.getOrNull()

    /** L'indentation de la ligne portant l'offset. */
    private fun indentationDe(offset: Int): String {
        var debut = offset
        while (debut > 0 && source[debut - 1] != '\n') debut--
        var fin = debut
        while (fin < source.length && (source[fin] == ' ' || source[fin] == '\t')) fin++
        return source.substring(debut, fin)
    }

    /** Le DÉBUT DE LIGNE (après \n) d'un offset — la convention d'insertion de la tranche 1. */
    private fun debutDeLigne(offset: Int): Int = source.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)) + 1

    /** L'ancre d'insertion des membres : DÉBUT DE LIGNE du premier membre RÉEL (convention tranche 1) — ecj ajoute un constructeur PAR DÉFAUT à type.methods, positionné sur le NOM du type (hors corps, mesuré) : il ne compte JAMAIS comme ancre. */
    private fun ancreMembreDe(type: TypeDeclaration): Int? {
        val reel = { n: ASTNode -> debutComplet(n) >= type.bodyStart }
        val membres: List<ASTNode> = structure.champs.filter { couvre(type, it) && reel(it) } +
            structure.methodes.filter { couvre(type, it) && reel(it) }
        val premier = membres.minByOrNull { debutComplet(it) }
        if (premier != null) return debutDeLigne(debutComplet(premier))
        // Corps vide : juste après l'accolade ouvrante, ligne propre.
        val finLigne = source.indexOf('\n', runCatching { type.bodyStart }.getOrDefault(source.length))
        return if (finLigne < 0) source.length else finLigne + 1
    }

    /** Vrai sss la méthode est statique. */
    private fun estStatique(methode: AbstractMethodDeclaration): Boolean = runCatching {
        (methode.modifiers and org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants.AccStatic) != 0
    }.getOrDefault(false)

    /** Vrai sss le champ est static final. */
    private fun estStatiqueFinal(champ: FieldDeclaration): Boolean = runCatching {
        (champ.modifiers and org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants.AccStatic) != 0 &&
            (champ.modifiers and org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants.AccFinal) != 0
    }.getOrDefault(false)

    /** Vrai sss la méthode OUTREPASSE un supertype (même sélecteur, même arité) — refus honnête du safe delete. */
    private fun outrepasse(methode: MethodDeclaration): Boolean = runCatching {
        val b = methode.binding ?: return false
        val declarant = b.declaringClass ?: return false
        val arite = b.parameters?.size ?: 0
        fun cherche(t: org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding?, profondeur: Int): Boolean {
            if (t == null || profondeur > 12) return false
            for (m in t.methods()) {
                if (String(m.selector) == String(b.selector) && (m.parameters?.size ?: 0) == arite) return true
            }
            return cherche(t.superclass(), profondeur + 1) || t.superInterfaces().any { cherche(it, profondeur + 1) }
        }
        cherche(declarant.superclass(), 0) || declarant.superInterfaces().any { cherche(it, 0) }
    }.getOrDefault(false)

    /** Vrai sss l'expression est CONSTANTE (littéral, concaténation ou binaire de constantes). */
    private fun estConstante(expr: Expression): Boolean = when (expr) {
        is IntLiteral, is LongLiteral, is FloatLiteral, is DoubleLiteral, is StringLiteral, is ExtendedStringLiteral,
        is StringLiteralConcatenation, is CharLiteral, is TrueLiteral, is FalseLiteral, is NullLiteral,
        -> true
        is UnaryExpression -> estConstante(expr.expression)
        is BinaryExpression -> estConstante(expr.left) && estConstante(expr.right)
        else -> false
    }

    /** Vrai sss l'expression est PURE (ré-évaluable sans changer de sens : littéral, référence, accès de champ, littéral de classe). */
    private fun estPur(expr: Expression): Boolean = when (expr) {
        is IntLiteral, is LongLiteral, is FloatLiteral, is DoubleLiteral, is StringLiteral, is ExtendedStringLiteral,
        is StringLiteralConcatenation, is CharLiteral, is TrueLiteral, is FalseLiteral, is NullLiteral,
        is SingleNameReference, is QualifiedNameReference, is FieldReference, is ClassLiteralAccess,
        is ThisReference, is QualifiedThisReference,
        -> true
        is UnaryExpression -> estPur(expr.expression)
        is CastExpression -> estPur(expr.expression)
        else -> false
    }

    /** Le nom SIMPLE d'un type lisible (java.lang.String → String ; demo.Main[] → Main). */
    private fun nomSimple(type: String): String = type.substringAfterLast('.').substringBefore('[').ifEmpty { "Objet" }

    /** Un nom de VARIABLE disponible (heuristique par type, suffixe anti-collision). */
    private fun nomVariableDisponible(type: String, pris: Set<String>): String {
        val base = when {
            type == "boolean" -> "drapeau"
            type in setOf("int", "long", "short", "byte") -> "nombre"
            type in setOf("double", "float") -> "valeur"
            type == "char" -> "caractere"
            type.endsWith("String") -> "texte"
            else -> nomSimple(type).replaceFirstChar { it.lowercaseChar() }
        }
        var nom = base
        var rang = 2
        while (nom in pris) {
            nom = base + rang
            rang++
        }
        return nom
    }

    /** Un nom de CONSTANTE disponible (miroir de l'intention tranche 1 : CONSTANTE_42 / CHAINE_prefixe). */
    private fun nomConstanteDisponible(expr: Expression, pris: Set<String>): String {
        val texteLitteral = texte(expr)
        val brut = when (expr) {
            is StringLiteral, is StringLiteralConcatenation, is ExtendedStringLiteral ->
                "CHAINE_" + texteLitteral.trim('"').uppercase().map { c -> if (c.isLetterOrDigit()) c else '_' }.joinToString("").take(24).trimEnd('_')
            else ->
                "CONSTANTE_" + texteLitteral.uppercase().map { c -> if (c.isLetterOrDigit()) c else '_' }.joinToString("").take(24).trimEnd('_')
        }
        var nom = if (brut.length < 9 || brut.endsWith('_')) "CONSTANTE" else brut
        var rang = 2
        while (nom in pris) {
            nom = brut + rang
            rang++
        }
        return nom
    }

    /** Un nom de MÉTHODE disponible (suffixe anti-collision sur les sélecteurs du type). */
    private fun nomMethodeDisponible(type: TypeDeclaration, base: String): String {
        val selecteurs = runCatching {
            (type.methods ?: emptyArray()).map { String(it.selector) }.toSet()
        }.getOrDefault(emptySet())
        var nom = base
        var rang = 2
        while (nom in selecteurs) {
            nom = base + rang
            rang++
        }
        return nom
    }

    /** Les noms PRIS pour une nouvelle locale : locales et paramètres de la méthode, champs du type, et tout identifiant LU dans la méthode (le nommé masquerait). */
    private fun nomsPrisDans(methode: AbstractMethodDeclaration): Set<String> {
        val pris = HashSet<String>()
        for ((_, info) in localesDe(methode)) pris.add(info.first)
        val type = typeEnglobantDe(methode)
        if (type != null) for (champ in structure.champs.filter { couvre(type, it) }) pris.add(String(champ.name))
        for (lecture in structure.lectures) {
            if (lecture.debut >= methode.declarationSourceStart && lecture.fin <= methode.declarationSourceEnd) pris.add(lecture.nom)
        }
        return pris
    }

    /** Les noms de membres du type (constantie comprise — anti-collision de la nouvelle constante). */
    private fun nomsConstantesDans(type: TypeDeclaration): Set<String> =
        structure.champs.filter { couvre(type, it) }.map { String(it.name) }.toSet() +
            structure.methodes.filter { couvre(type, it) }.map { String(it.selector) }

    /** Le nom de la locale au caret (titre de l'action inline). */
    private fun nomLocaleAu(position: Int): String? =
        structure.locales.firstOrNull { porteLeNom(it.sourceStart, it.name, position) }?.let { String(it.name) }

    /** La signature privée d'une méthode extraite. */
    private fun signaturePrivee(nom: String, parametres: List<Pair<LocalVariableBinding, String>>, typeRetour: String, statique: Boolean): String {
        val params = parametres.joinToString(", ") { (b, n) -> "${lisible(b) ?: "Object"} $n" }
        val modificateurs = if (statique) "private static" else "private"
        return "$modificateurs $typeRetour $nom($params) {"
    }

    /** RÉ-INDENTE un extrait : la profondeur RELATIVE des lignes (par rapport à la base) est translatée vers la cible. */
    private fun reindente(texte: String, base: Int, cible: Int): String =
        texte.split("\n").joinToString("\n") { ligne ->
            if (ligne.isBlank()) {
                ""
            } else {
                val actuel = ligne.takeWhile { it == ' ' || it == '\t' }.length
                val relatif = (actuel - base).coerceAtLeast(0)
                " ".repeat((cible + relatif).coerceAtMost(64)) + ligne.trimStart(' ', '\t')
            }
        }

    /** Les bindings de SEGMENTS d'une référence qualifiée. */
    private fun segmentsDe(ref: QualifiedNameReference): List<org.eclipse.jdt.internal.compiler.lookup.Binding> {
        val segments = ArrayList<org.eclipse.jdt.internal.compiler.lookup.Binding>()
        runCatching { segments.add(ref.binding) }
        runCatching {
            for (b in ref.otherBindings ?: emptyArray()) segments.add(b)
        }
        return segments
    }

    /** Le nom du paramètre au caret (titre de l'action change signature). */
    private fun nomParametreAu(position: Int): String? {
        for (argument in structure.arguments) {
            if (porteLeNom(argument.sourceStart, argument.name, position)) return String(argument.name)
        }
        return null
    }

    private companion object {
        const val CLE_EXTRAIRE_VARIABLE = "refactor.extraireVariable"
        const val CLE_EXTRAIRE_CONSTANTE = "refactor.extraireConstante"
        const val CLE_EXTRAIRE_METHODE = "refactor.extraireMethode"
        const val CLE_METTRE_EN_LIGNE = "refactor.mettreEnLigne"
        const val CLE_METTRE_EN_LIGNE_CONSTANTE = "refactor.mettreEnLigneConstante"
        const val CLE_METTRE_EN_LIGNE_CONSTANTE_PROJET = "refactor.mettreEnLigneConstanteProjet"
        const val CLE_SUPPRIMER_SURE = "refactor.supprimerSure"
        const val CLE_SUPPRIMER_HIERARCHIQUE = "refactor.supprimerHierarchique"
        const val CLE_SUPPRIMER_PARAMETRE = "refactor.supprimerParametre"
        const val CLE_DEPLACER_PARAMETRE = "refactor.deplacerParametre"
    }
}

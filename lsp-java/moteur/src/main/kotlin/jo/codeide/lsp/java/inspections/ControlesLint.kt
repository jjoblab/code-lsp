package jo.codeide.lsp.java.inspections

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.inspections.BaseApiAndroid.nomInterneDe
import jo.codeide.lsp.java.inspections.BaseApiAndroid.niveauChamp
import jo.codeide.lsp.java.inspections.BaseApiAndroid.sinceClasse
import jo.codeide.lsp.java.inspections.BaseApiAndroid.sinceMethode
import jo.codeide.lsp.java.interne.LectureAst
import org.eclipse.jdt.internal.compiler.ast.ASTNode
import org.eclipse.jdt.internal.compiler.ast.Block
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.IfStatement
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.Statement
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding

/** La provenance des diagnostics LINT (ADR 0024 — distincte des inspections). */
public const val SOURCE_LINT: String = "lint"

/**
 * Les contrôles Android LINT à valeur Java, mono-fichier (vague V-C2 —
 * ADR 0024, écart G-011) : des [InspectionMoteur] spécialisés, sévérité
 * LSP Warning par défaut (règle ADR 0023/0024 : un contrôle lint ne
 * remplace pas ecj — NewApi signale du code VALIDE pour la compilation
 * mais invalide à l'exécution sur minSdk), suppression `@SuppressLint`.
 *
 * Garde commune (miroir de la référence) : sur une unité portant une
 * ERREUR de résolution, la passe lint est muette — les liaisons ne sont
 * pas fiables, jamais de signal fallacieux.
 */

// ------------------------------------------------------------------ //
// NewApi + InlinedApi + ObsoleteSdkInt — le détecteur de niveaux d'API
// ------------------------------------------------------------------ //

/**
 * **NewApi** : appel d'une méthode/champ/classe du SDK dont le niveau
 * `since` dépasse le minSdk du module — avec les ÉCHAPPATOIRES de la
 * référence : `@RequiresApi(n)`/`@TargetApi(n)` sur la méthode ou classe
 * englobante (n ≥ since), et GARDE `Build.VERSION.SDK_INT >= n` encadrant
 * l'accès dans la méthode (n ≥ since, intra-méthode). **InlinedApi** :
 * champ marqué `inlined` au-delà du minSdk (constante inlinée à la
 * construction — signal faible). **ObsoleteSdkInt** : comparaison
 * `SDK_INT < minSdk` (branche morte par construction).
 */
public object ControleNewApi : InspectionMoteur {
    override val id: String = "NewApi"
    override val libelle: String = "API requise plus récente que le minSdk"
    override val groupe: String = "Correctitude Android"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT
    override val provenance: String get() = SOURCE_LINT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val minSdk = contexte.minSdk ?: return
        if (contexte.erreursEcj.isNotEmpty()) return // garde de la référence
        val arbre = contexte.arbre ?: return
        // Pré-passe : les @RequiresApi/@TargetApi posés par déclaration
        // (méthodes et types) — l'échappatoire la plus courante.
        val exigences = mutableMapOf<Plage, Int>()
        val collecteurAnnotations = object : LectureAst.VisiteurResilient() {
            override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                niveauRequisDe(node.annotations)?.let { exigences[Plage(node.declarationSourceStart, node.declarationSourceEnd + 1)] = it }
                return true
            }

            override fun visiteType(node: org.eclipse.jdt.internal.compiler.ast.TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                niveauRequisDe(node.annotations)?.let { exigences[Plage(node.declarationSourceStart, node.declarationSourceEnd + 1)] = it }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteurAnnotations)

        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                if (node.statements != null) {
                    marcher(node.statements!!.toList(), minSdk, exigences, rapporteur)
                }
                return true
            }

            override fun visiteConstructeur(node: org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                if (node.statements != null) {
                    marcher(node.statements!!.toList(), minSdk, exigences, rapporteur)
                }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, visiteur)
    }

    /** Marche les instructions en pistant les gardes SDK_INT englobantes. */
    private fun marcher(instructions: List<Statement>, minSdk: Int, exigences: Map<Plage, Int>, rapporteur: RapporteurInspection) {
        for (instruction in instructions) {
            marcher(instruction, minSdk, exigences, emptyList(), rapporteur)
        }
    }

    private fun marcher(instruction: Statement, minSdk: Int, exigences: Map<Plage, Int>, gardes: List<Pair<Plage, Int>>, rapporteur: RapporteurInspection) {
        val nouvellesGardes = gardes + gardeSdkIntDe(instruction, minSdk, rapporteur)
        when (instruction) {
            is MessageSend -> examiner(instruction, minSdk, exigences, nouvellesGardes, rapporteur)
            is org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference -> examinerChamp(instruction, minSdk, exigences, nouvellesGardes, rapporteur)
            is Block -> instruction.statements?.forEach { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
            is IfStatement -> {
                instruction.thenStatement?.let { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
                instruction.elseStatement?.let { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
            }
            is org.eclipse.jdt.internal.compiler.ast.ForStatement -> instruction.action?.let { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
            is org.eclipse.jdt.internal.compiler.ast.WhileStatement -> instruction.action?.let { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
            is org.eclipse.jdt.internal.compiler.ast.DoStatement -> instruction.action?.let { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
            is org.eclipse.jdt.internal.compiler.ast.TryStatement -> {
                val essai = instruction
                essai.tryBlock?.statements?.forEach { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
                essai.finallyBlock?.statements?.forEach { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
                for (bloc in essai.catchBlocks ?: emptyArray()) {
                    bloc.statements?.forEach { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
                }
            }
            is org.eclipse.jdt.internal.compiler.ast.SynchronizedStatement -> {
                instruction.block?.statements?.forEach { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
            }
            is org.eclipse.jdt.internal.compiler.ast.LabeledStatement -> instruction.statement?.let { marcher(it, minSdk, exigences, nouvellesGardes, rapporteur) }
            is org.eclipse.jdt.internal.compiler.ast.ReturnStatement -> {
                (instruction.expression as? MessageSend)?.let { examiner(it, minSdk, exigences, nouvellesGardes, rapporteur) }
                (instruction.expression as? org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference)?.let {
                    examinerChamp(it, minSdk, exigences, nouvellesGardes, rapporteur)
                }
            }
            is org.eclipse.jdt.internal.compiler.ast.Expression -> {
                // instruction-expression nue : un appel jeté ou une
                // référence de champ (p. ex. une constante lue pour rien).
                (instruction as? MessageSend)?.let { examiner(it, minSdk, exigences, nouvellesGardes, rapporteur) }
                (instruction as? org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference)?.let {
                    examinerChamp(it, minSdk, exigences, nouvellesGardes, rapporteur)
                }
            }
            else -> {}
        }
    }

    /** La garde `SDK_INT >= n` (ou `> n-1`) d'une instruction if — sinon null ; + ObsoleteSdkInt. */
    private fun gardeSdkIntDe(instruction: Statement, minSdk: Int, rapporteur: RapporteurInspection): List<Pair<Plage, Int>> {
        if (instruction !is IfStatement) return emptyList()
        val condition = instruction.condition
        val bitsOperateur = (condition as? org.eclipse.jdt.internal.compiler.ast.BinaryExpression)?.bits ?: return emptyList()
        val operateur = (bitsOperateur and 0x1F00) ushr 8
        if (operateur != org.eclipse.jdt.internal.compiler.ast.OperatorIds.GREATER_EQUAL &&
            operateur != org.eclipse.jdt.internal.compiler.ast.OperatorIds.GREATER &&
            operateur != org.eclipse.jdt.internal.compiler.ast.OperatorIds.LESS &&
            operateur != org.eclipse.jdt.internal.compiler.ast.OperatorIds.LESS_EQUAL
        ) {
            return emptyList()
        }
        val binaire = condition as org.eclipse.jdt.internal.compiler.ast.BinaryExpression
        val coteVersion = listOf(binaire.left, binaire.right).firstOrNull { estChampSdkInt(it) } ?: return emptyList()
        val coteNombre = listOf(binaire.left, binaire.right).firstOrNull { it != coteVersion && it is org.eclipse.jdt.internal.compiler.ast.IntLiteral } ?: return emptyList()
        val nombre = runCatching { String((coteNombre as org.eclipse.jdt.internal.compiler.ast.IntLiteral).source()).toInt() }.getOrNull() ?: return emptyList()
        val plage = Plage(instruction.sourceStart, (instruction.thenStatement?.sourceEnd ?: instruction.sourceEnd) + 1)
        return when (operateur) {
            org.eclipse.jdt.internal.compiler.ast.OperatorIds.GREATER_EQUAL -> listOf(plage to nombre)
            org.eclipse.jdt.internal.compiler.ast.OperatorIds.GREATER -> listOf(plage to (nombre + 1))
            // `< n` / `<= n` : la branche ALORS vit sous le niveau — ce n'est
            // pas une échappatoire ; et sous minSdk, la branche est MORTE.
            else -> {
                val borneMorte = if (operateur == org.eclipse.jdt.internal.compiler.ast.OperatorIds.LESS_EQUAL) nombre else nombre - 1
                if (borneMorte < minSdk) {
                    rapporteur.signaler(
                        Plage(condition.sourceStart, condition.sourceEnd + 1),
                        "Comparaison SDK_INT $borneMorte obsolète : le minSdk du module est déjà $minSdk (branche morte)",
                    )
                }
                emptyList()
            }
        }
    }

    /** Vrai ssi l'expression lit `Build.VERSION.SDK_INT` (référence qualifiée de ce nom). */
    private fun estChampSdkInt(expression: Expression): Boolean = runCatching {
        val texte = expression.toString()
        expression is QualifiedNameReference && texte.endsWith("SDK_INT")
    }.getOrDefault(false)

    /** Examine un appel de méthode contre la base. */
    private fun examiner(envoi: MessageSend, minSdk: Int, exigences: Map<Plage, Int>, gardes: List<Pair<Plage, Int>>, rapporteur: RapporteurInspection) {
        val liaison = runCatching { envoi.binding }.getOrNull() ?: return
        val declarant = runCatching { liaison.declaringClass }.getOrNull() ?: return
        val nomInterne = nomInterneDe(runCatching { String(declarant.readableName()) }.getOrNull() ?: return)
        val parametres = runCatching {
            liaison.parameters?.map { parametre -> String(parametre.readableName()).substringAfterLast('.') } ?: emptyList()
        }.getOrDefault(emptyList())
        val since = sinceMethode(nomInterne, String(liaison.selector), parametres)
        if (since != null && since > minSdk) {
            val plage = Plage(envoi.sourceStart, envoi.sourceEnd + 1)
            if (!estEchappee(plage, since, exigences, gardes)) {
                rapporteur.signaler(
                    plage,
                    "« ${String(liaison.selector)} » requiert le niveau d'API $since (minSdk du module : $minSdk) — entourer d'une garde SDK_INT ou annoter @RequiresApi($since)",
                )
            }
        }
    }

    /** Examine un accès de champ (constante SDK, champ d'une classe du SDK). */
    private fun examinerChamp(reference: QualifiedNameReference, minSdk: Int, exigences: Map<Plage, Int>, gardes: List<Pair<Plage, Int>>, rapporteur: RapporteurInspection) {
        val champ = runCatching { reference.binding }.getOrNull() as? org.eclipse.jdt.internal.compiler.lookup.FieldBinding ?: return
        val declarant = runCatching { champ.declaringClass }.getOrNull() ?: return
        val nomDeclarant = runCatching { String(declarant.readableName()) }.getOrNull() ?: return
        if (!nomDeclarant.startsWith("android.")) return
        val nomInterne = nomInterneDe(nomDeclarant)
        val nomChamp = String(champ.name)
        val niveau = niveauChamp(nomInterne, nomChamp)
        val plage = Plage(reference.sourceStart, reference.sourceEnd + 1)
        if (niveau != null && niveau.since > minSdk && !estEchappee(plage, niveau.since, exigences, gardes)) {
            rapporteur.signaler(
                plage,
                "Le champ « $nomChamp » requiert le niveau d'API ${niveau.since} (minSdk du module : $minSdk)",
            )
        } else if (niveau?.inlined != null && niveau.inlined > minSdk && !estEchappee(plage, niveau.inlined, exigences, gardes)) {
            // InlinedApi : constante inlinée à la construction — signal FAIBLE
            // (le code fonctionne, la valeur est figée au build).
            rapporteur.signaler(
                plage,
                "Constante inlinée depuis le niveau d'API ${niveau.inlined} (minSdk du module : $minSdk) — la valeur est figée au build",
            )
        }
    }

    /** Vrai ssi une annotation @RequiresApi/@TargetApi ou une garde couvre le niveau requis. */
    private fun estEchappee(plage: Plage, since: Int, exigences: Map<Plage, Int>, gardes: List<Pair<Plage, Int>>): Boolean {
        for ((portee, niveau) in exigences) {
            if (portee.debutOffset <= plage.debutOffset && plage.finOffset <= portee.finOffset && niveau >= since) return true
        }
        for ((portee, niveau) in gardes) {
            if (portee.debutOffset <= plage.debutOffset && plage.finOffset <= portee.finOffset && niveau >= since) return true
        }
        return false
    }

    /** Le niveau requis par les annotations @RequiresApi/@TargetApi d'une déclaration. */
    private fun niveauRequisDe(annotations: Array<org.eclipse.jdt.internal.compiler.ast.Annotation>?): Int? {
        var niveau: Int? = null
        for (annotation in annotations ?: emptyArray()) {
            val nom = String(annotation.type.getLastToken())
            if (nom != "RequiresApi" && nom != "TargetApi") continue
            val valeur = when (annotation) {
                is org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation ->
                    valeurNumerique(annotation.memberValue)
                is org.eclipse.jdt.internal.compiler.ast.NormalAnnotation ->
                    annotation.memberValuePairs?.firstOrNull { String(it.name) == "value" }?.let { valeurNumerique(it.value) }
                else -> null
            }
            if (valeur != null) {
                niveau = maxOf(niveau ?: 0, valeur)
            }
        }
        return niveau
    }

    /** La valeur numérique d'une expression d'annotation (littéral ou champ constant). */
    private fun valeurNumerique(expression: Expression?): Int? {
        if (expression == null) return null
        (expression as? org.eclipse.jdt.internal.compiler.ast.IntLiteral)?.let {
            return runCatching { String(it.source()).toInt() }.getOrNull()
        }
        (expression as? org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference)?.let {
            return runCatching { it.constant?.intValue() }.getOrNull()
        }
        (expression as? SingleNameReference)?.let {
            return runCatching { it.constant?.intValue() }.getOrNull()
        }
        return null
    }
}

// ------------------------------------------------------------------ //
// MissingSuperCall — méthode outrepassée annotée @CallSuper sans super.x()
// ------------------------------------------------------------------ //

/** Une méthode qui outrepasse un `@CallSuper` sans appeler `super.x()` (perte d'initialisation). */
public object ControleSuperManquant : InspectionMoteur {
    override val id: String = "MissingSuperCall"
    override val libelle: String = "Appel super.manquant pour @CallSuper"
    override val groupe: String = "Correctitude Android"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT
    override val provenance: String get() = SOURCE_LINT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        if (contexte.erreursEcj.isNotEmpty()) return
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                val liaison = runCatching { node.binding }.getOrNull() ?: return true
                if (!runCatching { !liaison.isStatic() && !liaison.isConstructor() && liaison.isOverriding() }.getOrDefault(false)) return true
                if (appelleSuper(node.statements, String(node.selector))) return true
                // L'annotation @CallSuper vit sur la méthode OUTREPASSÉE du
                // supertype — résolue par la liaison exacte du supertype.
                val superMethode = runCatching {
                    val superClasse = liaison.declaringClass?.superclass() ?: return@runCatching null
                    superClasse.getExactMethod(liaison.selector, liaison.parameters, scope?.compilationUnitScope())
                }.getOrNull() ?: return true
                val annote = runCatching {
                    superMethode.getAnnotations().any {
                        String((it.getAnnotationType()?.readableName()) ?: return@any false).substringAfterLast('.') == "CallSuper"
                    }
                }.getOrDefault(false)
                if (annote) {
                    rapporteur.signaler(
                        Plage(node.sourceStart, node.sourceEnd + 1),
                        "« ${String(node.selector)} » outrepasse une méthode @CallSuper sans l'appeler — ajouter « super.${String(node.selector)}(); »",
                    )
                }
                return true
            }
        }
        LectureAst.traverserResilient(contexte.arbre, visiteur)
    }

    /** Vrai ssi un appel super.selecteur(...) vit dans les instructions (premier niveau des blocs). */
    private fun appelleSuper(instructions: Array<Statement>?, selecteur: String): Boolean {
        for (instruction in instructions ?: emptyArray()) {
            if (chercheSuper(instruction, selecteur)) return true
        }
        return false
    }

    private fun chercheSuper(instruction: Statement?, selecteur: String): Boolean {
        if (instruction == null) return false
        if (instruction is MessageSend && instruction.receiver is org.eclipse.jdt.internal.compiler.ast.SuperReference) {
            if (runCatching { String(instruction.selector) }.getOrDefault("") == selecteur) return true
        }
        if (instruction is Block) return instruction.statements?.any { chercheSuper(it, selecteur) } == true
        if (instruction is IfStatement) {
            return chercheSuper(instruction.thenStatement, selecteur) || chercheSuper(instruction.elseStatement, selecteur)
        }
        if (instruction is org.eclipse.jdt.internal.compiler.ast.TryStatement) {
            val essai = instruction
            if (essai.tryBlock?.statements?.any { chercheSuper(it, selecteur) } == true) return true
            if (essai.finallyBlock?.statements?.any { chercheSuper(it, selecteur) } == true) return true
            for (bloc in essai.catchBlocks ?: emptyArray()) {
                if (bloc.statements?.any { chercheSuper(it, selecteur) } == true) return true
            }
        }
        return false
    }
}

// ------------------------------------------------------------------ //
// SetTextI18n — setText(littéral) hors getString
// ------------------------------------------------------------------ //

/** `textView.setText("littéral")` (ou append) — texte brut sans ressources : signal faible de localisation. */
public object ControleTexteBrut : InspectionMoteur {
    override val id: String = "SetTextI18n"
    override val libelle: String = "Texte brut dur (setText littéral)"
    override val groupe: String = "Internationalisation Android"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.FAIBLE
    override val provenance: String get() = SOURCE_LINT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                val selecteur = String(node.selector)
                if (selecteur != "setText" && selecteur != "append" && selecteur != "setHint") return true
                val premier = node.arguments?.firstOrNull() ?: return true
                if (premier !is org.eclipse.jdt.internal.compiler.ast.StringLiteral &&
                    premier !is org.eclipse.jdt.internal.compiler.ast.BinaryExpression
                ) {
                    return true
                }
                val recepteur = node.receiver?.resolvedType ?: return true
                val nom = runCatching { String(recepteur.readableName()) }.getOrNull() ?: return true
                if (nom != "android.widget.TextView" && !nom.startsWith("android.widget.") && nom != "android.widget.EditText") {
                    return true
                }
                rapporteur.signaler(
                    Plage(premier.sourceStart, premier.sourceEnd + 1),
                    "Texte brut dur — extraire dans les ressources (getString(R.string.…)) pour la localisation",
                )
                return true
            }
        }
        LectureAst.traverserResilient(contexte.arbre, visiteur)
    }
}

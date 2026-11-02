package jo.codeide.lsp.java.inspections

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import org.eclipse.jdt.internal.compiler.ast.ASTNode
import org.eclipse.jdt.internal.compiler.ast.BinaryExpression
import org.eclipse.jdt.internal.compiler.ast.Block
import org.eclipse.jdt.internal.compiler.ast.EqualExpression
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.IntLiteral
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.NullLiteral
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.ast.Statement
import org.eclipse.jdt.internal.compiler.ast.StringLiteral
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding
import org.eclipse.jdt.internal.compiler.parser.ScannerHelper

/**
 * Les HUIT inspections pilotes V-C1 (ADR 0023 — catalogue B/L : « ecj
 * fournit déjà la matière »). Toutes dumb-aware (AST pur, zéro index),
 * sans état, à liaisons gardées nulles : une source fautive ne produit
 * JAMAIS de signalement fallacieux (silence honnête).
 */
// ------------------------------------------------------------------ //
// 1. StringEquality — comparaison de chaînes par référence
// ------------------------------------------------------------------ //

/** `==`/`!=` entre deux opérandes de type String (null exclu). */
public object InspectionEgaliteChaines : InspectionMoteur {
    override val id: String = "StringEquality"
    override val libelle: String = "Comparaison de chaînes par référence"
    override val groupe: String = "Bogues probables"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteEgalite(node: EqualExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                val gauche = node.left.resolvedType
                val droite = node.right.resolvedType
                if (estChaines(gauche, node.left) && estChaines(droite, node.right)) {
                    rapporteur.signaler(
                        Plage(node.sourceStart, node.sourceEnd + 1),
                        "Comparaison de chaînes par référence (« == » compare les identités) — utiliser equals()",
                    )
                }
                return true
            }

            private fun estChaines(type: TypeBinding?, expression: Expression): Boolean {
                if (expression is NullLiteral) return false
                if (type == null || type.isBaseType()) return false
                return runCatching { String(type.readableName()) == "java.lang.String" }.getOrDefault(false)
            }
        }
        LectureAst.traverserResilient(contexte.arbre, visiteur)
    }
}

// ------------------------------------------------------------------ //
// 2. IgnoreResultOfCall — résultat d'un appel annoté @CheckResult ignoré
// ------------------------------------------------------------------ //

/** Appel d'une méthode annotée `@CheckResult` dont le résultat est jeté (instruction nue). */
public object InspectionResultatIgnore : InspectionMoteur {
    override val id: String = "IgnoreResultOfCall"
    override val libelle: String = "Résultat d'appel annoté @CheckResult ignoré"
    override val groupe: String = "Bogues probables"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteMethode(
                node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration,
                scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?,
            ): Boolean {
                for (instruction in node.statements ?: emptyArray()) {
                    visiterInstruction(instruction, rapporteur)
                }
                return true
            }

            override fun visiteConstructeur(
                node: org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration,
                scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?,
            ): Boolean {
                for (instruction in node.statements ?: emptyArray()) {
                    visiterInstruction(instruction, rapporteur)
                }
                return true
            }
        }
        LectureAst.traverserResilient(contexte.arbre, visiteur)
    }

    /** Marche les instructions en profondeur (blocs, ifs, boucles, try…). */
    private fun visiterInstruction(instruction: Statement, rapporteur: RapporteurInspection) {
        when (instruction) {
            is MessageSend -> {
                val binding = runCatching { instruction.binding }.getOrNull() ?: return
                if (porteCheckResult(binding)) {
                    rapporteur.signaler(
                        Plage(instruction.sourceStart, instruction.sourceEnd + 1),
                        "Le résultat de « ${String(binding.selector)}() » (annoté @CheckResult) est ignoré",
                    )
                }
            }
            is Block -> for (s in instruction.statements ?: emptyArray()) visiterInstruction(s, rapporteur)
            is org.eclipse.jdt.internal.compiler.ast.IfStatement -> {
                instruction.thenStatement?.let { visiterInstruction(it, rapporteur) }
                instruction.elseStatement?.let { visiterInstruction(it, rapporteur) }
            }
            is org.eclipse.jdt.internal.compiler.ast.ForStatement -> instruction.action?.let { visiterInstruction(it, rapporteur) }
            is org.eclipse.jdt.internal.compiler.ast.WhileStatement -> statementDuCorps(instruction.action)?.let { visiterInstruction(it, rapporteur) }
            is org.eclipse.jdt.internal.compiler.ast.DoStatement -> instruction.action?.let { visiterInstruction(it, rapporteur) }
            is org.eclipse.jdt.internal.compiler.ast.TryStatement -> {
                val essai = instruction
                for (b in essai.tryBlock?.statements ?: emptyArray()) visiterInstruction(b, rapporteur)
                for (b in essai.finallyBlock?.statements ?: emptyArray()) visiterInstruction(b, rapporteur)
            }
            is org.eclipse.jdt.internal.compiler.ast.SynchronizedStatement -> {
                for (s in instruction.block?.statements ?: emptyArray()) visiterInstruction(s, rapporteur)
            }
            is org.eclipse.jdt.internal.compiler.ast.LabeledStatement -> instruction.statement?.let { visiterInstruction(it, rapporteur) }
            else -> {}
        }
    }

    private fun statementDuCorps(noeud: ASTNode?): Statement? = noeud as? Statement

    private fun finallyBlock(essai: org.eclipse.jdt.internal.compiler.ast.TryStatement): Block? = essai.finallyBlock

    /** Vrai ssi la méthode porte une annotation de nom simple « CheckResult ». */
    private fun porteCheckResult(binding: org.eclipse.jdt.internal.compiler.lookup.MethodBinding): Boolean = runCatching {
        binding.getAnnotations().any { annotation ->
            val type = annotation.getAnnotationType() ?: return@any false
            String(type.readableName()).substringAfterLast('.') == "CheckResult"
        }
    }.getOrDefault(false)
}

// ------------------------------------------------------------------ //
// 3. RedundantCast — cast vers le type déjà statique de l'expression
// ------------------------------------------------------------------ //

/** `(T) x` où le type de `x` est déjà exactement `T` (références uniquement). */
public object InspectionCastRedondant : InspectionMoteur {
    override val id: String = "RedundantCast"
    override val libelle: String = "Conversion (cast) redondante"
    override val groupe: String = "Verbosité du code"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.FAIBLE

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteCast(node: org.eclipse.jdt.internal.compiler.ast.CastExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                val cible = node.resolvedType
                val source = node.expression.resolvedType
                if (cible != null && source != null && !cible.isBaseType() && !source.isBaseType() &&
                    runCatching { String(cible.readableName()) == String(source.readableName()) }.getOrDefault(false)
                ) {
                    rapporteur.signaler(
                        Plage(node.sourceStart, node.sourceEnd + 1),
                        "Conversion redondante : l'expression est déjà du type demandé",
                    )
                }
                return true
            }
        }
        LectureAst.traverserResilient(contexte.arbre, visiteur)
    }
}

// ------------------------------------------------------------------ //
// 4. EqualsHashCode — equals sans hashCode (et réciproquement)
// ------------------------------------------------------------------ //

/** Une classe définit equals sans hashCode (ou l'inverse) — contrat rompu des tables de hachage. */
public object InspectionEqualsHashCode : InspectionMoteur {
    override val id: String = "EqualsHashCode"
    override val libelle: String = "equals() et hashCode() appareillés"
    override val groupe: String = "Bogues probables"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                val aEquals = node.methods?.any { it.isEquals() } == true
                val aHashCode = node.methods?.any { it.isHashCode() } == true
                if (aEquals != aHashCode && !estInterface(node)) {
                    rapporteur.signaler(
                        Plage(node.sourceStart, (node.sourceEnd ?: node.sourceStart) + 1),
                        if (aEquals) {
                            "« equals(Object) » est défini sans « hashCode() » — le contrat des tables de hachage est rompu"
                        } else {
                            "« hashCode() » est défini sans « equals(Object) » — le contrat des tables de hachage est rompu"
                        },
                    )
                }
                return true
            }
        }
        LectureAst.traverserResilient(contexte.arbre, visiteur)
    }

    private fun estInterface(node: TypeDeclaration): Boolean =
        (node.modifiers and org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants.AccInterface) != 0

    private fun org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration.isEquals(): Boolean =
        !isConstructor() && String(selector) == "equals" && arguments?.size == 1

    private fun org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration.isHashCode(): Boolean =
        !isConstructor() && String(selector) == "hashCode" && (arguments?.size ?: 0) == 0
}

// ------------------------------------------------------------------ //
// 5. SizeReplaceableByIsEmpty — taille().zéro → isEmpty()
// ------------------------------------------------------------------ //

/** `c.size() == 0` (ou `< 1`, `<= 0`) sur une collection/cartographie → `c.isEmpty()`. */
public object InspectionTailleVersIsEmpty : InspectionMoteur {
    override val id: String = "SizeReplaceableByIsEmpty"
    override val libelle: String = "size() remplaçable par isEmpty()"
    override val groupe: String = "Verbosité du code"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.FAIBLE

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteEgalite(node: EqualExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                if (estTailleNulle(node.left, node.right) || estTailleNulle(node.right, node.left)) {
                    rapporteur.signaler(
                        Plage(node.sourceStart, node.sourceEnd + 1),
                        "« size() == 0 » se lit mieux « isEmpty() »",
                    )
                }
                return true
            }

            override fun visiteBinaire(node: BinaryExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                // L'opérateur d'un BinaryExpression vit dans « bits » (encodé
                // `operator << 8` par le constructeur ecj — masque 5 bits).
                val operateur = (node.bits and 0x1F00) ushr 8
                if (operateur == org.eclipse.jdt.internal.compiler.ast.OperatorIds.LESS ||
                    operateur == org.eclipse.jdt.internal.compiler.ast.OperatorIds.LESS_EQUAL
                ) {
                    if (estTailleNulle(node.left, node.right)) {
                        rapporteur.signaler(
                            Plage(node.sourceStart, node.sourceEnd + 1),
                            "La comparaison de taille se lit mieux « isEmpty() »",
                        )
                    }
                }
                return true
            }
        }
        LectureAst.traverserResilient(contexte.arbre, visiteur)
    }

    /** Vrai ssi (taille, constante nulle) : `size() == 0`, `size() < 1`, `size() <= 0`. */
    private fun estTailleNulle(gauche: Expression, droite: Expression): Boolean {
        if (gauche !is MessageSend || String(gauche.selector) != "size") return false
        val recepteur = gauche.receiver ?: return false
        val type = recepteur.resolvedType ?: return false
        if (!estCollectionOuCarte(type)) return false
        if (droite !is IntLiteral) return false
        // IntLiteral : la valeur portée par le JETON (« 0 » ou « 1 »).
        val texte = runCatching { String(droite.source()) }.getOrNull() ?: return false
        return texte == "0" || texte == "1"
    }

    /** Vrai ssi le type implémente java.util.Collection ou java.util.Map (hiérarchie complète, en largeur). */
    private fun estCollectionOuCarte(type: TypeBinding): Boolean {
        val racine = type as? ReferenceBinding ?: return false
        val aVoir = ArrayDeque<ReferenceBinding>()
        aVoir.add(racine)
        val vus = HashSet<ReferenceBinding>()
        while (aVoir.isNotEmpty()) {
            val courant = aVoir.removeFirst()
            if (!courant.isValidBinding() || !vus.add(courant)) continue
            // Le nom EFFACÉ : « List<String> » se lit « java.util.List » —
            // la paramétrisation ne masque jamais l'interface implémentée.
            val nom = runCatching { String(courant.readableName()) }.getOrNull()?.substringBefore('<') ?: continue
            if (nom == "java.util.Collection" || nom == "java.util.Map") return true
            runCatching { courant.superclass() }.getOrNull()?.let { aVoir.add(it) }
            runCatching { courant.superInterfaces() }.getOrNull()?.forEach { aVoir.add(it) }
            // La forme GÉNÉRIQUE (paramétrée) peut porter des interfaces vides :
            // l'original (l'effacement) les connaît.
            runCatching { courant.original() as? ReferenceBinding }.getOrNull()?.let { aVoir.add(it) }
        }
        return false
    }
}

// ------------------------------------------------------------------ //
// 6. StaticFieldReferenceOnSubclass — champ statique via une sous-classe
// ------------------------------------------------------------------ //

/** `Sous.CONSTANT` où CONSTANT est déclaré dans une SUPERCLASSE de Sous. */
public object InspectionChampStatiqueViaSousClasse : InspectionMoteur {
    override val id: String = "StaticFieldReferenceOnSubclass"
    override val libelle: String = "Champ statique accédé par la sous-classe"
    override val groupe: String = "Verbosité du code"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.FAIBLE

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                val binding = runCatching { node.binding }.getOrNull()
                val champ = binding as? org.eclipse.jdt.internal.compiler.lookup.FieldBinding ?: return true
                if (!champ.isStatic()) return true
                val recepteur = runCatching { node.actualReceiverType }.getOrNull() as? ReferenceBinding ?: return true
                val declarant = champ.declaringClass ?: return true
                val nomRecepteur = runCatching { String(recepteur.readableName()) }.getOrNull() ?: return true
                val nomDeclarant = runCatching { String(declarant.readableName()) }.getOrNull() ?: return true
                if (nomRecepteur != nomDeclarant) {
                    rapporteur.signaler(
                        Plage(node.sourceStart, node.sourceEnd + 1),
                        "Champ statique déclaré dans « $nomDeclarant » — y accéder par « $nomRecepteur » masque son origine",
                    )
                }
                return true
            }
        }
        LectureAst.traverserResilient(contexte.arbre, visiteur)
    }
}

// ------------------------------------------------------------------ //
// 7. WrongPackageStatement — paquet déclaré ≠ paquet du dossier
// ------------------------------------------------------------------ //

/** Le paquet déclaré ne correspond pas au dossier du chemin logique du document. */
public object InspectionPaquetDeclare : InspectionMoteur {
    override val id: String = "WrongPackageStatement"
    override val libelle: String = "Paquet déclaré incohérent avec le dossier"
    override val groupe: String = "Bogues probables"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.FAIBLE

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val paquet = contexte.arbre?.currentPackage ?: return
        val attendu = paquetDuChemin(contexte.chemin) ?: return
        val declare = runCatching { paquet.tokens.joinToString(".") { String(it) } }.getOrNull() ?: return
        if (declare.isNotEmpty() && declare != attendu) {
            rapporteur.signaler(
                Plage(paquet.sourceStart, paquet.sourceEnd + 1),
                "Le paquet déclaré « $declare » ne correspond pas au dossier « $attendu »",
            )
        }
    }

    /** Le paquet impliqué par les dossiers du chemin logique (null si racine). */
    private fun paquetDuChemin(chemin: String): String? {
        val separe = chemin.replace('\\', '/')
        val dernierSlash = separe.lastIndexOf('/')
        if (dernierSlash < 0) return ""
        val dossiers = separe.substring(0, dernierSlash)
        if (dossiers.isEmpty()) return ""
        return dossiers.split('/').filter { it.isNotEmpty() }.joinToString(".")
    }
}

// ------------------------------------------------------------------ //
// 8. FormatString — spécificateurs vs arguments de String.format/printf
// ------------------------------------------------------------------ //

/** Le nombre de spécificateurs de format ne matche pas le nombre d'arguments. */
public object InspectionChaineFormat : InspectionMoteur {
    override val id: String = "FormatString"
    override val libelle: String = "Chaîne de format incohérente"
    override val groupe: String = "Bogues probables"
    override val severiteParDefaut: SeveriteInspection = SeveriteInspection.AVERTISSEMENT

    override fun visiter(contexte: ContexteInspection, rapporteur: RapporteurInspection) {
        val visiteur = object : LectureAst.VisiteurResilient() {
            override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                val selecteur = String(node.selector)
                if (selecteur != "format" && selecteur != "printf") return true
                val arguments = node.arguments ?: return true
                if (arguments.isEmpty()) return true
                // Le gabarit : PREMIER argument chaîne littérale — la
                // surcharge (Locale, gabarit, …) le place en seconde place.
                var indexGabarit = -1
                for ((indice, argument) in arguments.withIndex()) {
                    if (argument is StringLiteral) {
                        indexGabarit = indice
                        break
                    }
                }
                if (indexGabarit != 0 && indexGabarit != 1) return true
                val gabarit = arguments[indexGabarit] as StringLiteral
                val texte = runCatching { String(gabarit.source()) }.getOrNull() ?: return true
                val specificateurs = compterSpecificateurs(texte)
                if (specificateurs < 0) return true // positionnels : silence honnête
                val consommes = arguments.size - 1 - indexGabarit
                if (consommes != specificateurs) {
                    rapporteur.signaler(
                        Plage(node.sourceStart, node.sourceEnd + 1),
                        "La chaîne de format attend $specificateurs argument(s), l'appel en fournit $consommes",
                    )
                }
                return true
            }
        }
        LectureAst.traverserResilient(contexte.arbre, visiteur)
    }

    /**
     * Le nombre de spécificateurs CONSOMMATEURS de la chaîne ; -1 si la
     * chaîne utilise des index positionnels (`%1$s` — comptage non fiable,
     * silence honnête).
     */
    private fun compterSpecificateurs(texte: String): Int {
        var total = 0
        var i = 0
        while (i < texte.length) {
            if (texte[i] != '%') {
                i++
                continue
            }
            i++
            if (i >= texte.length) break
            if (texte[i] == '%' || texte[i] == 'n') {
                i++ // %% et %n ne consomment pas
                continue
            }
            // index positionnel éventuel
            var j = i
            while (j < texte.length && texte[j].isDigit()) j++
            if (j < texte.length && texte[j] == '$') return -1
            // flags / largeur / précision
            while (j < texte.length && texte[j] in "-#+ 0,(<.123456789") j++
            if (j >= texte.length) break
            total++
            i = j + 1
        }
        return total
    }
}

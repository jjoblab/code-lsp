package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.outils.EditionTexte
import org.eclipse.jdt.internal.compiler.ast.Assignment
import org.eclipse.jdt.internal.compiler.ast.Block
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.IfStatement
import org.eclipse.jdt.internal.compiler.ast.IntLiteral
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference
import org.eclipse.jdt.internal.compiler.ast.Statement
import org.eclipse.jdt.internal.compiler.ast.StringLiteral
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.ast.TryStatement
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding

/**
 * Le service des INTENTIONS et de la GÉNÉRATION DE CODE (vague V-D —
 * ADR 0027, écarts G-014/G-015) : les ampoules SANS diagnostic de la
 * référence (Alt+Enter jaune) — conversions, extractions rapides,
 * génération par capacités — calculées en LECTURE sur l'arbre RÉSOLU
 * comme des plans déclaratifs [ActionCode] (jamais d'écriture disque).
 *
 * Deux régimes dans UNE réponse `codeAction` :
 *  - les INTENTIONS légères (if→ternaire, extraction de constante,
 *    lambda→référence de méthode, var↔explicite) voyagent avec leurs
 *    éditions — une édition chacune, calcul peu coûteux ;
 *  - les GÉNÉRATEURS (getter/setter, constructeur, equals/hashCode,
 *    toString, stubs d'outrepassement) voyagent DIFFÉRÉS
 *    ([ModCommande.Differee] — clé + positions) : les éditions se
 *    calculent au `codeAction/resolve` (ADR 0027 (f) : rien d'eager,
 *    la liste reste légère).
 *
 * Le déclenchement est CIBLÉ (miroir de la référence) : le caret porte
 * l'intention — sur un CHAMP (getter/setter), sur le NOM DU TYPE
 * (constructeur/equals/hashCode/toString), dans un type à méthodes
 * abstraites manquantes (stubs), sur un littéral, une lambda, une
 * déclaration var ou un if/else symétrique.
 *
 * @param arbre l'arbre RÉSOLU de l'unité (bindings vivants).
 * @param source la source Java complète.
 */
public class ServiceIntentions(
    private val arbre: org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration?,
    private val source: String,
) {

    /**
     * Les intentions et générateurs applicables à la position.
     *
     * @param debut le début du caret/sélection.
     * @param fin la fin du caret/sélection.
     * @return les actions (intentions EAGER, générateurs DIFFÉRÉS).
     */
    public fun intentions(debut: Int, fin: Int): List<ActionCode> {
        if (arbre == null) return emptyList()
        val resultat = mutableListOf<ActionCode>()
        val position = (if (fin > debut) debut else debut.coerceAtMost(source.length))
        resultat.addAll(intentionsLegeres(position))
        resultat.addAll(generateursDifferees(position))
        return resultat
    }

    /**
     * La résolution d'une commande différée (ADR 0027 (f)) : les éditions
     * du générateur désigné — recalculées DÉTERMINISTEMENT depuis la
     * source (même source → mêmes éditions, jamais d'état caché).
     *
     * @param cle la clé du générateur.
     * @param arguments les arguments du générateur.
     * @return les éditions, ou null (clé inconnue / cible disparue —
     *   le document a bougé entre la liste et la résolution).
     */
    public fun resoudre(cle: String, arguments: List<String>): List<EditionTexte>? {
        if (arbre == null) return null
        val positions = arguments.mapNotNull { it.toIntOrNull() }
        return when (cle) {
            "generer.getter" -> positions.firstOrNull()?.let { champDe(it) }?.let { genererAccesseur(it, avecSetteur = false) }
            "generer.setter" -> positions.firstOrNull()?.let { champDe(it) }?.let { genererAccesseur(it, avecSetteur = true, uniquementSetteur = true) }
            "generer.getterSetteur" -> positions.firstOrNull()?.let { champDe(it) }?.let { genererAccesseur(it, avecSetteur = true) }
            "generer.constructeur" -> positions.firstOrNull()?.let { typeDe(it) }?.let { genererConstructeur(it) }
            "generer.equalsHashcode" -> positions.firstOrNull()?.let { typeDe(it) }?.let { genererEqualsHashcode(it) }
            "generer.toString" -> positions.firstOrNull()?.let { typeDe(it) }?.let { genererToString(it) }
            "generer.outrepasser" -> {
                val type = positions.getOrNull(0)?.let { typeDe(it) } ?: return null
                val manquante = manquantesDe(type).getOrNull(positions.getOrNull(1) ?: return null) ?: return null
                genererStub(type, manquante.first)
            }
            else -> null
        }
    }

    /**
     * Les STUBS des méthodes abstraites manquantes d'un diagnostic ecj
     * (famille [jo.codeide.lsp.java.actionscode.FamilleProbleme.METHODES_ABSTRAITES],
     * V-D — ADR 0027 (a)) : le type visé et le sélecteur manquant sont
     * portés par les ARGUMENTS STRUCTURÉS du diagnostic (sonde mesurée :
     * args = [run, , java.lang.Runnable, demo.Main]) — la signature
     * complète vient des BINDINGS de la hiérarchie.
     *
     * @param nomType le nom LIISIBLE du type visé (args[3], « demo.Main »).
     * @param selecteur le sélecteur de la méthode manquante (args[0]).
     * @param diagnostic le diagnostic ecj source (l'action le porte — le
     *   cycle P5 vérifie sa disparition).
     * @return l'action EAGER du stub, ou null (type introuvable dans le
     *   fichier / méthode introuvable dans la hiérarchie).
     */
    public fun stubAbstraites(nomType: String, selecteur: String, diagnostic: jo.codeide.lsp.java.diagnostics.Diagnostic): ActionCode? {
        if (arbre == null) return null
        val types = mutableListOf<TypeDeclaration>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                types.add(node)
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        val type = types.firstOrNull { noeud ->
            runCatching { noeud.binding?.readableName()?.let { nom -> String(nom) } }.getOrNull() == nomType
        } ?: types.firstOrNull { noeud -> String(noeud.name) == nomType.substringAfterLast('.') } ?: return null
        val manquante = manquantesDe(type).firstOrNull { paire -> String(paire.first.selector) == selecteur } ?: return null
        val editions = genererStub(type, manquante.first)
        if (editions.isEmpty()) return null
        return ActionCode(
            "quickfix.implement.methods",
            "Implémenter « $selecteur » (stub)",
            ModCommande.EditsTexte(editions),
            diagnostic,
        )
    }

    // ------------------------------------------------------------------ //
    // Intentions légères (EAGER — une édition chacune)
    // ------------------------------------------------------------------ //

    /** Les intentions applicables au caret. */
    private fun intentionsLegeres(position: Int): List<ActionCode> {
        val resultat = mutableListOf<ActionCode>()
        intentionTernaire(position)?.let { resultat.add(it) }
        intentionConstante(position)?.let { resultat.add(it) }
        intentionReferenceMethode(position)?.let { resultat.add(it) }
        intentionVar(position)?.let { resultat.add(it) }
        return resultat
    }

    /** `if/else` symétrique → ternaire (deux return ou deux affectations). */
    private fun intentionTernaire(position: Int): ActionCode? {
        val si = chercherEnclos<IfStatement>(position) ?: return null
        si.elseStatement ?: return null
        // Les branches entre accolades sont des BLOCS mono-instruction —
        // le retour vit dedans (déballage avant l'épreuve).
        val alors = instructionNue(si.thenStatement) as? ReturnStatement ?: return null
        val sinon = instructionNue(si.elseStatement) as? ReturnStatement ?: return null
        val valeurAlors = alors.expression ?: return null
        val valeurSinon = sinon.expression ?: return null
        val remplacement = "return ${texte(si.condition)} ? ${texte(valeurAlors)} : ${texte(valeurSinon)};"
        return action(
            "refactor.rewrite",
            "Remplacer if/else par un opérateur ternaire",
            EditionTexte(si.sourceStart, si.sourceEnd + 1 - si.sourceStart, remplacement),
        )
    }

    /** L'instruction nue d'une branche (déballage du bloc mono-instruction). */
    private fun instructionNue(instruction: Statement?): Statement? {
        if (instruction is Block) {
            val contenu = instruction.statements
            if (contenu != null && contenu.size == 1) return contenu[0]
        }
        return instruction
    }

    /** Littéral au caret → constante de classe. */
    private fun intentionConstante(position: Int): ActionCode? {
        val litteral = litteralEnclos(position) ?: return null
        val texteLitteral = texte(litteral)
        val type = when (litteral) {
            is StringLiteral -> "String"
            else -> "int"
        }
        val nom = if (litteral is StringLiteral) {
            "CHAINE_" + texteLitteral.trim('"').uppercase().map { c -> if (c.isLetterOrDigit()) c else '_' }
                .joinToString("").take(24).trimEnd('_')
        } else {
            "CONSTANTE_" + texteLitteral.removeSuffix("L")
        }
        if (nom.endsWith('_') || nom.length < 9) return null // nom trop pauvre : silence honnête
        val typeEnglobant = typeEnclosant(position) ?: return null
        val ancre = ancreDeMembre(typeEnglobant)
        val declaration = "${indentationDe(ancre)}private static final $type $nom = $texteLitteral;\n"
        return action(
            "refactor.extract",
            "Extraire la constante « $nom »",
            EditionTexte(ancre, 0, declaration),
            EditionTexte(litteral.sourceStart, litteral.sourceEnd + 1 - litteral.sourceStart, nom),
        )
    }

    /** Lambda à un paramètre `x -> x.m()` → référence de méthode `T::m`. */
    private fun intentionReferenceMethode(position: Int): ActionCode? {
        // Chercher la lambda englobante (plage du nœud) puis éprouver son corps.
        val lambdas = mutableListOf<LambdaExpression>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteLambda(node: LambdaExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                lambdas.add(node)
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        val lambda = lambdas.firstOrNull { position >= it.sourceStart && position <= it.sourceEnd } ?: return null
        val corps = (lambda.body as? Expression) ?: return null
        val envoi = corps as? MessageSend ?: return null
        val arguments = lambda.arguments ?: return null
        if (envoi.arguments?.isNotEmpty() == true) {
            // `(a, b) -> a.plus(b)` : les arguments du DOIVENT être les
            // paramètres restants, dans l'ordre.
            if (envoi.arguments.size != arguments.size - 1) return null
            envoi.arguments.forEachIndexed { index, argument ->
                val attendu = arguments[index + 1]
                if (argument !is SingleNameReference || String(argument.token) != String(attendu.name)) return null
            }
        }
        val recepteur = envoi.receiver
        val typeRecepteur: String?
        var prefixe = ""
        when {
            recepteur is SingleNameReference && arguments.size == 1 && String(recepteur.token) == String(arguments[0].name) -> {
                typeRecepteur = runCatching {
                    recepteur.resolvedType?.readableName()?.let { String(it) }
                }.getOrNull()
            }
            recepteur is org.eclipse.jdt.internal.compiler.ast.ThisReference && envoi.arguments?.isEmpty() == true -> {
                typeRecepteur = "this"
                prefixe = "this::"
            }
            else -> return null
        }
        if (typeRecepteur == null) return null
        // java.lang est auto-importé : le nom SIMPLE suffit (l'idiome de la
        // référence) ; hors java.lang, le nom QUALIFIÉ garantit le compile.
        val recepteurSimple = if (typeRecepteur.startsWith("java.lang.") && typeRecepteur.count { it == '.' } == 2) {
            typeRecepteur.substringAfterLast('.')
        } else {
            typeRecepteur
        }
        val cible = if (prefixe.isEmpty()) "$recepteurSimple::" else prefixe
        return action(
            "refactor.rewrite",
            "Remplacer la lambda par « $cible${String(envoi.selector)} »",
            EditionTexte(lambda.sourceStart, lambda.sourceEnd + 1 - lambda.sourceStart, "$cible${String(envoi.selector)}"),
        )
    }

    /** Déclaration locale au caret : `String x = …` ↔ `var x = …`. */
    private fun intentionVar(position: Int): ActionCode? {
        val locales = mutableListOf<LocalDeclaration>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                locales.add(node)
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        val locale = locales.firstOrNull {
            position >= it.declarationSourceStart && position <= it.declarationSourceEnd && it.initialization != null
        } ?: return null
        val texteType = texte(locale.type)
        if (texteType == "var") {
            val type = runCatching { locale.binding?.type?.readableName()?.let { String(it) } }.getOrNull() ?: return null
            return action(
                "refactor.rewrite",
                "Typer explicitement (« $type »)",
                EditionTexte(locale.type.sourceStart, locale.type.sourceEnd + 1 - locale.type.sourceStart, type),
            )
        }
        // `String x = "a";` → var : seulement si le type déclaré EST le type
        // résolu de l'initialisateur (Object x = "a" changerait de sémantique).
        val typeDeclare = runCatching { locale.type.resolvedType?.readableName()?.let { String(it) } }.getOrNull()
        val typeInitial = runCatching { locale.initialization?.resolvedType?.readableName()?.let { String(it) } }.getOrNull()
        if (typeDeclare == null || typeInitial == null || typeDeclare != typeInitial) return null
        if (locale.initialization is org.eclipse.jdt.internal.compiler.ast.ArrayInitializer) return null
        return action(
            "refactor.rewrite",
            "Remplacer le type par « var »",
            EditionTexte(locale.type.sourceStart, locale.type.sourceEnd + 1 - locale.type.sourceStart, "var"),
        )
    }

    // ------------------------------------------------------------------ //
    // Générateurs (DIFFÉRÉS — clé + positions, éditions au resolve)
    // ------------------------------------------------------------------ //

    /** Les générateurs applicables au caret (actions sans édits, avec clé). */
    private fun generateursDifferees(position: Int): List<ActionCode> {
        val resultat = mutableListOf<ActionCode>()
        // Getter/setter : le caret porte un champ d'instance.
        champDe(position)?.let { champ ->
            if (!estStatique(champ)) {
                val nomChamp = String(champ.name)
                resultat.add(
                    ActionCode(
                        "source.generate.getter",
                        "Générer le getter « ${nomAccesseur(champ)} »",
                        ModCommande.Differee("generer.getter", listOf(champ.declarationSourceStart.toString())),
                    ),
                )
                if (!estFinal(champ)) {
                    resultat.add(
                        ActionCode(
                            "source.generate.setter",
                            "Générer le setter « set${nomChamp.replaceFirstChar { it.uppercase() }} »",
                            ModCommande.Differee("generer.setter", listOf(champ.declarationSourceStart.toString())),
                        ),
                    )
                    resultat.add(
                        ActionCode(
                            "source.generate.getterSetteur",
                            "Générer getter et setter de « $nomChamp »",
                            ModCommande.Differee("generer.getterSetteur", listOf(champ.declarationSourceStart.toString())),
                        ),
                    )
                }
            }
        }
        // Constructeur/equals/hashCode/toString : le caret porte le NOM du type.
        typeAuNom(position)?.let { type ->
            // P5 (15/11/2026) — la garde constructeur : un type qui a DÉJÀ
            // un constructeur (quelconque — la forme générée dépend des
            // champs, pas de lui) ne voit plus l'action ; la générer
            // produirait un DOUBLON de constructeur par défaut ou une
            // surcharge accidentelle. IntelliJ fait de même (l'action
            // LISTE les constructeurs existants ; ici on ne génère que
            // la forme « tous les champs », la garde la plus honnête).
            if (champsInstance(type).isNotEmpty() && !aConstructeur(type)) {
                resultat.add(ActionCode("source.generate.constructor", "Générer le constructeur", ModCommande.Differee("generer.constructeur", listOf(type.declarationSourceStart.toString()))))
            }
            if (!declare(type, "equals") || !declare(type, "hashCode")) {
                resultat.add(ActionCode("source.generate.equalsHashcode", "Générer equals() et hashCode()", ModCommande.Differee("generer.equalsHashcode", listOf(type.declarationSourceStart.toString()))))
            }
            if (!declare(type, "toString")) {
                resultat.add(ActionCode("source.generate.toString", "Générer toString()", ModCommande.Differee("generer.toString", listOf(type.declarationSourceStart.toString()))))
            }
        }
        // Stubs : le type englobant a des méthodes abstraites manquantes.
        typeEnclosant(position)?.let { type ->
            val manquantes = manquantesDe(type)
            if (manquantes.isNotEmpty()) {
                manquantes.take(8).forEachIndexed { rang, (methode, _) ->
                    resultat.add(
                        ActionCode(
                            "source.generate.override",
                            "Implémenter « ${String(methode.selector)}${signatureCourte(methode)} »",
                            ModCommande.Differee("generer.outrepasser", listOf(type.declarationSourceStart.toString(), rang.toString())),
                        ),
                    )
                }
            }
        }
        return resultat
    }

    /** Le texte du getter (et setter) d'un champ — insertion après le champ. */
    private fun genererAccesseur(champ: FieldDeclaration, avecSetteur: Boolean, uniquementSetteur: Boolean = false): List<EditionTexte> {
        val nomChamp = String(champ.name)
        val typeChamp = texte(champ.type)
        val indent = indentationDe(champ.declarationSourceStart)
        val ancre = finDeLigne(champ.declarationSourceEnd)
        val texteGenere = buildString {
            if (!uniquementSetteur) {
                append(indent)
                append("public ")
                append(typeChamp)
                append(" ")
                append(nomAccesseur(champ))
                append("() {\n")
                append(indent)
                append("    return ")
                append(nomChamp)
                append(";\n")
                append(indent)
                append("}\n")
            }
            if (avecSetteur && !estFinal(champ)) {
                if (isNotEmpty()) append("\n")
                append(indent)
                append("public void set")
                append(nomChamp.replaceFirstChar { it.uppercase() })
                append("(")
                append(typeChamp)
                append(" ")
                append(nomChamp)
                append(") {\n")
                append(indent)
                append("    this.")
                append(nomChamp)
                append(" = ")
                append(nomChamp)
                append(";\n")
                append(indent)
                append("}\n")
            }
        }
        return listOf(EditionTexte(ancre, 0, texteGenere))
    }

    /** Le constructeur assignant tous les champs d'instance. */
    private fun genererConstructeur(type: TypeDeclaration): List<EditionTexte> {
        val champs = champsInstance(type)
        if (champs.isEmpty()) return emptyList()
        val nomType = String(type.name)
        val indent = indentDeMembre(type)
        val texteGenere = buildString {
            append(indent)
            append("public ")
            append(nomType)
            append("(")
            append(champs.joinToString(", ") { champ -> "${texte(champ.type)} ${String(champ.name)}" })
            append(") {\n")
            for (champ in champs) {
                append(indent)
                append("    this.")
                append(String(champ.name))
                append(" = ")
                append(String(champ.name))
                append(";\n")
            }
            append(indent)
            append("}\n")
        }
        return listOf(EditionTexte(ancreDeMembre(type), 0, texteGenere))
    }

    /** equals() + hashCode() sur les champs d'instance (java.util.Objects). */
    private fun genererEqualsHashcode(type: TypeDeclaration): List<EditionTexte> {
        val champs = champsInstance(type)
        if (champs.isEmpty()) return emptyList()
        val nomType = String(type.name)
        val indent = indentDeMembre(type)
        val texteGenere = buildString {
            append(indent)
            append("@Override\n")
            append(indent)
            append("public boolean equals(Object autre) {\n")
            append(indent)
            append("    if (this == autre) return true;\n")
            append(indent)
            append("    if (autre == null || getClass() != autre.getClass()) return false;\n")
            append(indent)
            append("    ")
            append(nomType)
            append(" cela = (")
            append(nomType)
            append(") autre;\n")
            append(indent)
            append("    return ")
            append(champs.joinToString(" && ") { champ -> comparaisonEgalite(champ, "cela") })
            append(";\n")
            append(indent)
            append("}\n")
            append("\n")
            append(indent)
            append("@Override\n")
            append(indent)
            append("public int hashCode() {\n")
            append(indent)
            append("    return java.util.Objects.hash(")
            append(champs.joinToString(", ") { champ -> String(champ.name) })
            append(");\n")
            append(indent)
            append("}\n")
        }
        return listOf(EditionTexte(ancreDeMembre(type), 0, texteGenere))
    }

    /** toString() « Nom{champ=valeur, …} » sur les champs d'instance. */
    private fun genererToString(type: TypeDeclaration): List<EditionTexte> {
        val champs = champsInstance(type)
        if (champs.isEmpty()) return emptyList()
        val nomType = String(type.name)
        val indent = indentDeMembre(type)
        val texteGenere = buildString {
            append(indent)
            append("@Override\n")
            append(indent)
            append("public String toString() {\n")
            append(indent)
            append("    return \"")
            append(nomType)
            append("{")
            append(champs.joinToString(", ") { champ -> "${String(champ.name)}=\" + ${String(champ.name)} + \"" })
            append("}\";\n")
            append(indent)
            append("}\n")
        }
        return listOf(EditionTexte(ancreDeMembre(type), 0, texteGenere))
    }

    /** Le stub d'une méthode abstraite manquante (valeur par défaut + TODO). */
    private fun genererStub(type: TypeDeclaration, methode: MethodBinding): List<EditionTexte> {
        val indent = indentDeMembre(type)
        val parametres = methode.parameters?.mapIndexed { index, parametre ->
            "${String(parametre.readableName())} arg$index"
        } ?: emptyList()
        val retour = runCatching {
            val typeRetour = methode.returnType
            if (typeRetour == null) "void" else String(typeRetour.readableName())
        }.getOrDefault("void")
        val texteGenere = buildString {
            append(indent)
            append("@Override\n")
            append(indent)
            append("public ")
            append(retour)
            append(" ")
            append(String(methode.selector))
            append("(")
            append(parametres.joinToString(", "))
            append(") {\n")
            append(indent)
            append("    // TODO : implémenter\n")
            when {
                retour == "void" -> {}
                retour == "boolean" -> append(indent).append("    return false;\n")
                retour in setOf("int", "long", "short", "byte", "char", "float", "double") -> append(indent).append("    return 0;\n")
                else -> append(indent).append("    return null;\n")
            }
            append(indent)
            append("}\n")
        }
        return listOf(EditionTexte(ancreDeMembre(type), 0, texteGenere))
    }

    // ------------------------------------------------------------------ //
    // Auxiliaires de localisation et de texte
    // ------------------------------------------------------------------ //

    /** Le nom du getter d'un champ (is pour boolean, get sinon). */
    private fun nomAccesseur(champ: FieldDeclaration): String {
        val nom = String(champ.name).replaceFirstChar { it.uppercase() }
        val type = texte(champ.type)
        return if (type == "boolean") "is$nom" else "get$nom"
    }

    /** La comparaison d'égalité d'un champ (== primitif, Objects sinon). */
    private fun comparaisonEgalite(champ: FieldDeclaration, cible: String): String {
        val nom = String(champ.name)
        val type = texte(champ.type)
        return when {
            type in setOf("int", "long", "short", "byte", "char", "float", "double", "boolean") -> "$nom == $cible.$nom"
            type.endsWith("[]") -> "java.util.Arrays.deepEquals($nom, $cible.$nom)"
            else -> "java.util.Objects.equals($nom, $cible.$nom)"
        }
    }

    /** Les champs d'INSTANCE du type (ni statiques). */
    private fun champsInstance(type: TypeDeclaration): List<FieldDeclaration> =
        type.fields?.filter { !estStatique(it) }.orEmpty()

    /** Vrai ssi le type déclare déjà un membre de ce sélecteur. */
    private fun declare(type: TypeDeclaration, selecteur: String): Boolean =
        type.methods?.any { String(it.selector) == selecteur } == true

    /** Vrai ssi le type a DÉJÀ un constructeur EXPLICITE (garde P5).
     *  MESURÉ : ecj SYNTHÉTISE le constructeur par défaut dans l'AST
     *  (type.methods contient un ConstructorDeclaration même sans
     *  constructeur écrit, sa plage recouvre le nom de la classe) —
     *  le drapeau ASTNode.IsDefaultConstructor (bit 128 de `bits`)
     *  seul le distingue. */
    private fun aConstructeur(type: TypeDeclaration): Boolean =
        type.methods?.any {
            it.isConstructor() && (it.bits and org.eclipse.jdt.internal.compiler.ast.ASTNode.IsDefaultConstructor) == 0
        } == true

    /** Vrai ssi le champ est statique (modificateurs ecj). */
    private fun estStatique(champ: FieldDeclaration): Boolean =
        (champ.modifiers and org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants.AccStatic) != 0

    /** Vrai ssi le champ est final (pas de setter). */
    private fun estFinal(champ: FieldDeclaration): Boolean =
        (champ.modifiers and org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants.AccFinal) != 0

    /**
     * Les méthodes ABSTRAITES manquantes du type : déclarées dans les
     * supertypes, sans implémentation concrète dans la hiérarchie —
     * liste DÉTERMINISTE (ordre de collecte de la hiérarchie, dédoublonnée
     * par (sélecteur, arité)) pour que la clé différée soit stable.
     */
    private fun manquantesDe(type: TypeDeclaration): List<Pair<MethodBinding, String>> {
        val liaison = runCatching { type.binding }.getOrNull() ?: return emptyList()
        val candidates = LinkedHashMap<String, MethodBinding>()
        fun visiter(supertype: ReferenceBinding?, vus: MutableSet<String>) {
            if (supertype == null) return
            val nom = runCatching { String(supertype.readableName()) }.getOrNull() ?: return
            if (nom in vus) return
            vus.add(nom)
            if (nom == "java.lang.Object") return
            for (methode in runCatching { supertype.methods() }.getOrDefault(emptyArray())) {
                runCatching {
                    if (methode.isAbstract && !methode.isStatic && !methode.isConstructor()) {
                        val cle = "${String(methode.selector)}\u0000${methode.parameters?.size ?: 0}"
                        candidates.putIfAbsent(cle, methode)
                    }
                }
            }
            visiter(runCatching { supertype.superclass() }.getOrNull(), vus)
            for (abstraite in runCatching { supertype.superInterfaces() }.getOrDefault(emptyArray())) {
                visiter(abstraite, vus)
            }
        }
        visiter(runCatching { liaison.superclass() }.getOrNull(), mutableSetOf())
        for (abstraite in runCatching { liaison.superInterfaces() }.getOrDefault(emptyArray())) {
            visiter(abstraite, mutableSetOf())
        }
        val resultat = mutableListOf<Pair<MethodBinding, String>>()
        for ((cle, methode) in candidates) {
            val concrete = runCatching {
                liaison.getExactMethod(methode.selector, methode.parameters, arbre?.scope)?.takeIf { !it.isAbstract }
            }.getOrNull()
            if (concrete == null) {
                resultat.add(methode to cle)
            }
        }
        return resultat
    }

    /** La signature courte d'une méthode pour un titre. */
    private fun signatureCourte(methode: MethodBinding): String {
        val noms = methode.parameters?.map { String(it.readableName()).substringAfterLast('.') }.orEmpty()
        return "(" + noms.joinToString(", ") + ")"
    }

    /** Le champ d'instance dont la déclaration porte la position. */
    private fun champDe(position: Int): FieldDeclaration? {
        val champs = mutableListOf<FieldDeclaration>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
                champs.add(node)
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return champs.firstOrNull { position >= it.declarationSourceStart && position <= it.declarationSourceEnd }
    }

    /** Le type dont la déclaration porte la position (innermost). */
    private fun typeDe(position: Int): TypeDeclaration? {
        val types = mutableListOf<TypeDeclaration>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                types.add(node)
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return types.filter { position >= it.declarationSourceStart && position <= it.declarationSourceEnd }
            .minByOrNull { it.declarationSourceEnd - it.declarationSourceStart }
    }

    /** Le type dont le NOM porte la position (déclencheur ciblé des générateurs). */
    private fun typeAuNom(position: Int): TypeDeclaration? {
        val types = mutableListOf<TypeDeclaration>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                types.add(node)
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return types.firstOrNull { type ->
            val nom = String(type.name)
            val plageNom = plageNomDe(type, nom)
            plageNom != null && position >= plageNom.first && position <= plageNom.last + 1
        }
    }

    /** La plage du NOM déclaré d'un type (recherche après l'en-tête). */
    private fun plageNomDe(type: TypeDeclaration, nom: String): IntRange? {
        val fin = type.bodyStart.coerceAtMost(source.length)
        var index = type.sourceStart
        while (index < fin) {
            index = source.indexOf(nom, index)
            if (index < 0 || index >= fin) return null
            val avant = index - 1
            val apres = index + nom.length
            val borneAvant = avant < 0 || !source[avant].isLetterOrDigit() && source[avant] != '_' && source[avant] != '.'
            val borneApres = apres >= source.length || !source[apres].isLetterOrDigit() && source[apres] != '_'
            if (borneAvant && borneApres) return index..(index + nom.length - 1)
            index += nom.length
        }
        return null
    }

    /** Le type englobant la position (innermost). */
    private fun typeEnclosant(position: Int): TypeDeclaration? = typeDe(position)

    /** L'ancre d'insertion des membres : début de ligne du PREMIER membre. */
    private fun ancreDeMembre(type: TypeDeclaration): Int {
        val premier = premierMembreDe(type)
        if (premier == null) {
            // Corps vide : après l'accolade ouvrante, ligne propre.
            return finDeLigne(type.bodyStart - 1)
        }
        return debutDeLigne(premier)
    }

    /** L'indentation d'insertion des membres (celle du premier membre). */
    private fun indentDeMembre(type: TypeDeclaration): String {
        val premier = premierMembreDe(type)
        if (premier == null) return indentationDe(type.sourceStart) + "    "
        return indentationDe(premier)
    }

    /** Le début du premier membre déclaré du type (champ ou méthode). */
    private fun premierMembreDe(type: TypeDeclaration): Int? {
        // Les membres RÉELS vivent dans le corps (≥ bodyStart) — ecj ajoute
        // un constructeur PAR DÉFAUT à type.methods, positionné sur le NOM
        // du type (hors corps) : il ne compte JAMAIS comme ancre.
        val champ = type.fields?.filter { it.declarationSourceStart >= type.bodyStart }?.minOfOrNull { it.declarationSourceStart }
        val methode = type.methods?.filter { it.declarationSourceStart >= type.bodyStart }?.minOfOrNull { it.declarationSourceStart }
        return listOfNotNull(champ, methode).minOrNull()
    }

    /** Le littéral numérique ou chaîne couvrant la position. */
    private fun litteralEnclos(position: Int): Expression? {
        val litteraux = mutableListOf<Expression>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteBinaire(node: org.eclipse.jdt.internal.compiler.ast.BinaryExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                runCatching {
                    if (node.left is IntLiteral || node.left is StringLiteral) litteraux.add(node.left)
                    if (node.right is IntLiteral || node.right is StringLiteral) litteraux.add(node.right)
                }
                return true
            }

            override fun visiteRetour(node: ReturnStatement, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                runCatching { ajouterLitteral(node.expression) }
                return true
            }

            override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                runCatching { ajouterLitteral(node.initialization) }
                return true
            }

            override fun visiteAffectation(node: Assignment, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                runCatching { ajouterLitteral(node.expression) }
                return true
            }

            override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                runCatching { node.arguments?.forEach { ajouterLitteral(it) } }
                return true
            }

            private fun ajouterLitteral(expression: Expression?) {
                when (expression) {
                    is IntLiteral -> litteraux.add(expression)
                    is StringLiteral -> litteraux.add(expression)
                    is org.eclipse.jdt.internal.compiler.ast.LongLiteral -> litteraux.add(expression)
                    is org.eclipse.jdt.internal.compiler.ast.DoubleLiteral -> litteraux.add(expression)
                    else -> {}
                }
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return litteraux.firstOrNull { position >= it.sourceStart && position <= it.sourceEnd + 1 }
    }

    /** Le nœud du type demandé couvrant la position (instructions recursives). */
    private inline fun <reified T : org.eclipse.jdt.internal.compiler.ast.ASTNode> chercherEnclos(position: Int): T? {
        val instructions = mutableListOf<Statement>()
        val collecteur = object : LectureAst.VisiteurResilient() {
            override fun visiteMethode(node: org.eclipse.jdt.internal.compiler.ast.MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                collecterInstructions(node.statements, instructions)
                return true
            }

            override fun visiteConstructeur(node: org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                collecterInstructions(node.statements, instructions)
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return instructions.filterIsInstance<T>().firstOrNull { position >= it.sourceStart && position <= it.sourceEnd + 1 }
    }

    /** La collecte récursive des instructions (blocs, if, try). */
    private fun collecterInstructions(instructions: Array<Statement>?, sortie: MutableList<Statement>) {
        for (instruction in instructions ?: emptyArray()) {
            sortie.add(instruction)
            when (instruction) {
                is Block -> collecterInstructions(instruction.statements, sortie)
                is IfStatement -> {
                    collecterInstructions(arrayOf(instruction.thenStatement).filterNotNull().toTypedArray(), sortie)
                    instruction.elseStatement?.let { collecterInstructions(arrayOf(it), sortie) }
                }
                is TryStatement -> {
                    collecterInstructions(instruction.tryBlock?.statements, sortie)
                    collecterInstructions(instruction.finallyBlock?.statements, sortie)
                    for (bloc in instruction.catchBlocks ?: emptyArray()) {
                        collecterInstructions(bloc.statements, sortie)
                    }
                }
                else -> {}
            }
        }
    }

    /** Le texte source d'un nœud. */
    private fun texte(noeud: org.eclipse.jdt.internal.compiler.ast.ASTNode): String =
        source.substring(noeud.sourceStart.coerceIn(0, source.length), (noeud.sourceEnd + 1).coerceIn(0, source.length))

    /** L'indentation de la ligne d'un offset. */
    private fun indentationDe(offset: Int): String {
        val debut = debutDeLigne(offset)
        return source.substring(debut, offset.coerceAtMost(source.length)).takeWhile { it == ' ' || it == '\t' }
    }

    /** Le début de ligne (après \n) d'un offset. */
    private fun debutDeLigne(offset: Int): Int = source.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)) + 1

    /** La fin de ligne (y compris \n) d'un offset. */
    private fun finDeLigne(offset: Int): Int {
        val fin = source.indexOf('\n', offset)
        return if (fin < 0) source.length else fin + 1
    }

    /** Une action EAGER avec ses éditions. */
    private fun action(kind: String, titre: String, vararg editions: EditionTexte): ActionCode =
        ActionCode(kind, titre, ModCommande.EditsTexte(editions.toList()))
}

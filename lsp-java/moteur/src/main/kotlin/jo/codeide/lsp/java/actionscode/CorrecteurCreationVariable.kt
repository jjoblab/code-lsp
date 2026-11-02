package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.outils.EditionTexte

/**
 * Le correcteur « Créer la variable locale » (vague V-A — écart G-014,
 * complément du banc P5) : un nom non résolu en POSITION DE VARIABLE
 * (`UnresolvedVariable` ecj) reçoit une déclaration locale typée par le
 * CONTEXTE — le banc P5 a mesuré l'absence totale d'action sur
 * `return inconnue` (fixture sans action) : le catalogue 7 familles ne
 * couvrait pas la création de variable.
 *
 * **Ce que le correcteur fait** (borné, honnête) :
 *  - déclare `Type nom = <défaut>;` en TÊTE du corps de la méthode
 *    englobante (couvre tous les usages du corps, jamais seulement
 *    l'occurrence corrigée) ;
 *  - type par le contexte : `return nom` → type de retour de la méthode
 *    englobante ; `Type x = … nom …` → type déclaré à gauche ; sinon
 *    `Object` (repli générique) ;
 *  - valeur par défaut du type : `false` (boolean), `0` (primitifs), `null`
 *    (références) — jamais une locale non initialisée (erreur de flux) ;
 *  - TERMINE l'instruction hôte par `;` si elle est non terminée (frappe
 *    interrompue : `return inconnue` sans point-virgule) — uniquement sur
 *    instruction SIMPLE (jamais dans une construction de contrôle).
 *
 * **Ce qu'il ne fait pas** (limites documentées) : pas de création de champ
 * ni de paramètre (familles futures, vagues V-C/V-D) ; pas de résolution du
 * type attendu en position d'argument (repli `Object`) ; un nom en position
 * de TYPE (`UndefinedType`) relève des candidats d'import (fiche 063), pas
 * d'ici ; la déclaration en tête de corps est volontairement APRÈS
 * l'accolade ouvrante — un usage dans une classe anonyme imbriquée reste
 * hors de portée de la locale (sémantique Java).
 */
internal object CorrecteurCreationVariable {

    /** L'ID ecj d'UnresolvedVariable (« x cannot be resolved to a variable »). */
    private const val ID_UNRESOLVED_VARIABLE: Long = 33554515L

    /** Mots-clés amorce d'une construction de CONTRÔLE (jamais une instruction simple). */
    private val MOTS_CLES_CONTROLE = setOf(
        "if", "for", "while", "switch", "catch", "do", "else", "try", "synchronized", "new", "throw", "assert",
    )

    /**
     * Construit la déclaration locale pour un nom non résolu en position de
     * variable.
     *
     * @param source le texte Java complet.
     * @param diagnostic le diagnostic UnresolvedVariable RÉEL du moteur (la
     *   plage couvre le JETON du nom).
     * @return l'action « quickfix.create.variable », ou null (nom qualifié,
     *   hors méthode, instruction non analysable).
     */
    internal fun creationVariable(source: String, diagnostic: Diagnostic): ActionCode? {
        if (diagnostic.code?.removePrefix("ecj:")?.toLongOrNull() != ID_UNRESOLVED_VARIABLE) return null
        val finNom = diagnostic.plage.finOffset.coerceIn(0, source.length)
        val debutNom = diagnostic.plage.debutOffset.coerceIn(0, finNom)
        val nom = source.substring(debutNom, finNom)
        if (nom.isEmpty() || !nom[0].isJavaIdentifierStart()) return null

        // Garde « nom qualifié » : un point avant le nom (récepteur, paquet)
        // écarte la création — ce n'est pas une locale manquante.
        var k = debutNom - 1
        while (k >= 0 && source[k].isWhitespace()) k--
        if (k >= 0 && source[k] == '.') return null

        // La méthode englobante du site (la locale vit dans son corps).
        val corps = ChercheurMethode.corpsEnglobant(source, debutNom) ?: return null

        // Le contexte typé : instruction hôte depuis son début.
        val debut = AnalyseTexte.debutInstruction(source, debutNom)
        val avantNom = source.substring(debut, debutNom)
        val type = typeContextuel(avantNom, corps)

        val editions = ArrayList<EditionTexte>()
        // (1) Déclaration en tête du corps : couvre tous les usages du corps.
        val indentation = AnalyseTexte.indentation(source, corps.ouverture)
        editions.add(
            EditionTexte(
                offset = corps.ouverture + 1,
                longueur = 0,
                texte = "\n$indentation    $type $nom = ${valeurParDefaut(type)};",
            ),
        )
        // (2) Point-virgule manquant de l'instruction hôte (frappe
        // interrompue) — sur instruction SIMPLE seulement.
        terminaisonManquante(source, debut, finNom, avantNom)?.let { editions.add(it) }

        return CorrecteursTexte.action(
            kind = "quickfix.create.variable",
            titre = "Créer la variable locale $nom",
            editions = editions,
            diagnostic = diagnostic,
        )
    }

    /**
     * Le type contextuel de la variable : `return` → type de retour déclaré
     * de la méthode englobante ; `Type x =` → type déclaré ; sinon `Object`.
     *
     * @param avantNom le texte de l'instruction avant le nom.
     * @param corps la méthode englobante du site.
     * @return le type de la déclaration créée.
     */
    private fun typeContextuel(avantNom: String, corps: CorpsMethode): String {
        val amorce = avantNom.trim()
        if (amorce == "return") {
            val type = corps.typeRetour
            if (!type.isNullOrEmpty() && type != "void") return type
        }
        val affectation = Regex("^([A-Za-z_$][A-Za-z0-9_$.]*(?:<[^=]*>)?(?:\\s*\\[\\s*\\])*)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*$")
            .find(amorce)
        if (affectation != null && affectation.groupValues[1] != "var") return affectation.groupValues[1]
        return "Object"
    }

    /**
     * La valeur par défaut compilable d'un type : `false` pour boolean, `0`
     * pour les autres primitifs, `null` pour les références (jamais une
     * locale nue — l'analyse de flux exigerait une initialisation).
     *
     * @param type le type de la déclaration.
     * @return le littéral par défaut.
     */
    private fun valeurParDefaut(type: String): String = when (type) {
        "boolean" -> "false"
        "byte", "short", "int", "long", "char", "float", "double" -> "0"
        else -> "null"
    }

    /**
     * L'édition de terminaison `;` de l'instruction hôte si elle est NON
     * TERMINÉE — null si elle l'est déjà ou si le contexte ne permet pas une
     * insertion sûre.
     *
     * La borne de l'instruction : premier `;`, `{` ou `}` à profondeur 0
     * après le nom (chaînes et commentaires sautés). `;` → terminée. `{`/`}`
     * → non terminée : le `;` s'insère APRÈS le dernier caractère non blanc
     * avant la borne — SAUF si l'instruction amorce une construction de
     * contrôle (if, for…) ou vit dans une parenthèse encore ouverte :
     * l'insertion y serait une corruption, le correcteur s'abstient.
     *
     * @param source le texte Java complet.
     * @param debut le début de l'instruction hôte.
     * @param finNom la fin du nom non résolu.
     * @param avantNom le texte de l'instruction avant le nom.
     * @return l'édition d'insertion du `;`, ou null.
     */
    private fun terminaisonManquante(source: String, debut: Int, finNom: Int, avantNom: String): EditionTexte? {
        // Instruction de contrôle ou parenthèse ouverte : jamais de `;` forcé.
        if (avantNom.trim().split(Regex("\\s+")).firstOrNull() in MOTS_CLES_CONTROLE) return null
        var profondeur = 0
        var i = finNom
        var borne = -1
        borne@ while (i < source.length) {
            when (val c = source[i]) {
                '/' -> if (i + 1 < source.length && source[i + 1] == '/') {
                    val fin = source.indexOf('\n', i)
                    i = if (fin < 0) source.length else fin
                    continue@borne
                } else if (i + 1 < source.length && source[i + 1] == '*') {
                    val fin = source.indexOf("*/", i + 2)
                    i = if (fin < 0) source.length else fin + 2
                    continue@borne
                }
                '"' -> {
                    val fin = finDeChaine(source, i)
                    i = fin
                    continue@borne
                }
                '\'' -> {
                    val fin = finDeCaractere(source, i)
                    i = fin
                    continue@borne
                }
                '(', '[', '{' -> profondeur++
                ')', ']', '}' -> if (profondeur == 0) {
                    borne = i
                    break
                } else {
                    profondeur--
                }
                ';' -> if (profondeur == 0) {
                    borne = i
                    break
                }
            }
            i++
        }
        if (borne < 0 || source[borne] == ';') return null // déjà terminée (ou EOF sans borne : abstention)
        // Dernier caractère non blanc de l'instruction : le `;` va juste après.
        var dernier = borne - 1
        while (dernier >= debut && source[dernier].isWhitespace()) dernier--
        if (dernier < debut) return null
        return EditionTexte(offset = dernier + 1, longueur = 0, texte = ";")
    }

    /** La fin d'un littéral de chaîne (guillemet ouvrant en [debut], rendue APRÈS le fermant). */
    private fun finDeChaine(source: String, debut: Int): Int {
        var i = debut + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                '\n' -> return i
                else -> i++
            }
        }
        return i
    }

    /** La fin d'un littéral de caractère (apostrophe ouvrante en [debut]). */
    private fun finDeCaractere(source: String, debut: Int): Int {
        var i = debut + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                '\n' -> return i
                else -> i++
            }
        }
        return i
    }
}

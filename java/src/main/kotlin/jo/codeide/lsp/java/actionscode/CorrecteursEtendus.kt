package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.outils.EditionTexte

/**
 * Les correcteurs des FAMILLES ECJ ÉTENDUES (vague V-D — ADR 0027 (a) :
 * « étendre la table ID→famille aux IDs ecj à fix réel ») : corps de
 * méthode manquant, retour de valeur en void, import en conflit, cast
 * inutile (le signal de l'INSPECTION V-C1 « RedundantCast » — code nommé).
 *
 * Les stubs de méthodes abstraites manquantes (famille
 * [FamilleProbleme.METHODES_ABSTRAITES]) exigent l'ARBRE RÉSOLU : ils sont
 * servis par la passe d'appoint de [jo.codeide.lsp.java.MoteurActionsCode]
 * via [ServiceIntentions.stubAbstraites] — jamais par ces correcteurs
 * texte.
 */
public object CorrecteursEtendus {

    // ------------------------------------------------------------------ //
    // Corps de méthode manquant — MethodRequiresBody
    // ------------------------------------------------------------------ //

    /**
     * Insère un corps à la méthode qui se termine par « ; » (diagnostic
     * plage = « nom() ») : le « ; » est remplacé par un corps qui JETTE —
     * compilable pour TOUT type de retour (void compris), l'intention
     * « à implémenter » est explicite.
     *
     * @param source la source Java complète.
     * @param diagnostic le diagnostic (plage du nom et des paramètres).
     * @return l'action, ou null (le « ; » n'est pas trouvé après la plage —
     *   source déjà remaniée : silence honnête).
     */
    internal fun corpsManquant(source: String, diagnostic: Diagnostic): ActionCode? {
        val position = positionPointVirgule(source, diagnostic.plage.finOffset) ?: return null
        val corps = " { throw new UnsupportedOperationException(\"TODO : corps manquant\"); }"
        return action(
            "quickfix.insert.body",
            "Insérer un corps de méthode",
            listOf(EditionTexte(position, 1, corps)),
            diagnostic,
        )
    }

    // ------------------------------------------------------------------ //
    // Retour de valeur en méthode void — VoidMethodReturnsValue
    // ------------------------------------------------------------------ //

    /**
     * Supprime l'INSTRUCTION de retour entière (plage = « return expr; ») :
     * une méthode void n'a rien à rendre — le retrait du seul mot-clé
     * laisserait une expression nue qui ne compile pas toujours.
     *
     * @param source la source Java complète.
     * @param diagnostic le diagnostic (plage de l'instruction).
     * @return l'action (toujours — la plage est celle du diagnostic).
     */
    internal fun retourVoid(source: String, diagnostic: Diagnostic): ActionCode {
        return action(
            "quickfix.remove.return",
            "Supprimer le retour de valeur",
            listOf(
                EditionTexte(
                    diagnostic.plage.debutOffset,
                    diagnostic.plage.finOffset - diagnostic.plage.debutOffset,
                    "",
                ),
            ),
            diagnostic,
        )
    }

    // ------------------------------------------------------------------ //
    // Import en conflit — ConflictingImport / DuplicateImport
    // ------------------------------------------------------------------ //

    /**
     * Supprime la LIGNE d'import en conflit (plage = le FQN de l'import
     * fautif) — la ligne entière part, comme l'import inutilisé (fiche
     * 063 : coupe franche, aucun reste vide).
     *
     * @param source la source Java complète.
     * @param diagnostic le diagnostic (plage dans l'import fautif).
     * @return l'action, ou null (la ligne porteuse n'est pas un import).
     */
    internal fun importEnConflit(source: String, diagnostic: Diagnostic): ActionCode? {
        val (debutLigne, finLigne) = AnalyseTexte.bornesLigne(source, diagnostic.plage.debutOffset)
        val ligne = source.substring(debutLigne, finLigne).trimStart()
        if (!ligne.startsWith("import ") && !ligne.startsWith("import\t")) return null
        val fin = if (finLigne < source.length) finLigne + 1 else finLigne
        val nom = diagnostic.arguments.firstOrNull()?.substringAfterLast('.')
        return action(
            "quickfix.remove.import",
            if (nom != null) "Supprimer l'import en conflit $nom" else "Supprimer l'import en conflit",
            listOf(EditionTexte(debutLigne, fin - debutLigne, "")),
            diagnostic,
        )
    }

    // ------------------------------------------------------------------ //
    // Cast inutile — le signal de l'inspection RedundantCast (V-C1)
    // ------------------------------------------------------------------ //

    /**
     * Retire le PREMIER groupe de parenthèses du cast redondant (plage =
     * l'expression de cast entière) : « (String) x » → « x ». Les casts
     * chaînés perdent leur couche la plus externe — l'intention du
     * diagnostic ne porte QUE sur le cast signalé.
     *
     * @param source la source Java complète.
     * @param diagnostic le diagnostic (plage du cast).
     * @return l'action, ou null (la plage ne commence pas par « ( » —
     *   source remaniée : silence honnête).
     */
    internal fun castInutile(source: String, diagnostic: Diagnostic): ActionCode? {
        val debut = diagnostic.plage.debutOffset
        if (debut >= source.length || source[debut] != '(') return null
        var profondeur = 0
        for (index in debut until source.length) {
            when (source[index]) {
                '(' -> profondeur++
                ')' -> {
                    profondeur--
                    if (profondeur == 0) {
                        return action(
                            "quickfix.remove.cast",
                            "Supprimer le cast redondant",
                            listOf(EditionTexte(debut, index - debut + 1, "")),
                            diagnostic,
                        )
                    }
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------ //
    // Auxiliaires
    // ------------------------------------------------------------------ //

    /** La position du « ; » suivant (espaces et commentaires sautés). */
    private fun positionPointVirgule(source: String, depuis: Int): Int? {
        var index = depuis
        while (index < source.length) {
            when {
                source[index] == ' ' || source[index] == '\t' || source[index] == '\n' || source[index] == '\r' -> index++
                source[index] == '/' && index + 1 < source.length && source[index + 1] == '/' -> {
                    val fin = source.indexOf('\n', index)
                    if (fin < 0) return null
                    index = fin + 1
                }
                source[index] == '/' && index + 1 < source.length && source[index + 1] == '*' -> {
                    val fin = source.indexOf("*/", index)
                    if (fin < 0) return null
                    index = fin + 2
                }
                else -> return if (source[index] == ';') index else null
            }
        }
        return null
    }

    /** Une action correctrice avec ses éditions et son diagnostic source. */
    private fun action(kind: String, titre: String, editions: List<EditionTexte>, diagnostic: Diagnostic): ActionCode =
        ActionCode(kind, titre, ModCommande.EditsTexte(editions), diagnostic)
}

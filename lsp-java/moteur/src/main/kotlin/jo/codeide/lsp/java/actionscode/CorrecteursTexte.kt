package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.gabarits.AncreImport
import jo.codeide.lsp.java.outils.EditionTexte

/**
 * Les correcteurs TEXTuels (étage N6 — cahier fiche 063) : chaque correcteur
 * est une FONCTION PURE du texte et du diagnostic STRUCTURÉ — aucune
 * résolution, aucune dépendance au classpath — exécutable tel quel sur
 * Android/ART (le montage « service TEXTE pur » de la fiche : fixes sans
 * index, sans environnement).
 *
 * Les règles de prudence verrouillées par la fiche 063 (notes de
 * réécriture) :
 *  - JAMAIS de suppression hors ligne d'import (garde « import » en tête
 *    de ligne), sur multi-fragments (garde virgule profondeur 0), ou sur
 *    un paramètre (garde parenthèse ouvrante en remontant) ;
 *  - le try-catch cible le type EXACT de l'argument structuré ;
 *  - le throws traverse les blocs de contrôle (scan d'accolades) ;
 *  - le cast cible le type ATTENDU (deuxième argument) ;
 *  - l'insertion d'import est une insertion PURE (début = fin) en
 *    « import …;\n », juste après le dernier import (ancre N7, fiche 029 —
 *    réutilisé ici par les correcteurs comme prévu).
 *
 * Chaque fonction rend une action COMPLÈTE ou NULL — jamais d'action
 * coquille vide (fiche 063 : « toutes les actions rendues portent des édits
 * non-null et non vides »).
 */
internal object CorrecteursTexte {

    /** L'ID ecj d'UndefinedType — seul ID de la famille non-résolus qui reçoit des candidats d'import. */
    internal const val ID_UNDEFINED_TYPE: Long = 16777218L

    /** L'ID ecj de LocalVariableIsNeverUsed — seul ID « inutilisé » dont la suppression est offerte. */
    internal const val ID_LOCAL_NEVER_USED: Long = 536870973L

    /** L'ID ecj de MissingSemiColon (montage synthétique du cahier). */
    internal const val ID_MISSING_SEMICOLON: Long = 1610612960L

    /** L'ID ecj réellement émis pour un point-virgule manquant (découverte N6). */
    internal const val ID_INSERT_TO_COMPLETE: Long = 1610612976L

    // ------------------------------------------------------------------ //
    // Suppression d'import inutilisé — « quickfix.remove.import »
    // ------------------------------------------------------------------ //

    /**
     * Supprime la LIGNE d'import entière portant le diagnostic (fiche 063 :
     * « la LIGNE d'import entière a disparu et l'autre import survit »).
     *
     * Garde : la ligne doit ÊTRE une ligne d'import (l'import en tête après
     * coupe des blancs) — un diagnostic UnusedImport dont la plage vit sur
     * une déclaration ne produit AUCUNE action (fiche 063 : « pas de
     * suppression hors ligne d'import »).
     *
     * @param source le texte Java complet.
     * @param diagnostic le diagnostic UnusedImport (plage sur l'import).
     * @return l'action, ou null (hors ligne d'import).
     */
    internal fun suppressionImportInutilise(source: String, diagnostic: Diagnostic): ActionCode? {
        val (debutLigne, finLigne) = AnalyseTexte.bornesLigne(source, diagnostic.plage.debutOffset)
        val ligne = source.substring(debutLigne, finLigne).trimStart()
        if (!ligne.startsWith("import ") && !ligne.startsWith("import\t")) return null
        // Le saut de ligne final est CONSOMMÉ avec la ligne (aucune ligne
        // vide résiduelle) ; en fin de fichier, la coupe s'arrête au texte.
        val fin = if (finLigne < source.length) finLigne + 1 else finLigne
        val arguments = diagnostic.arguments
        val nom = arguments.firstOrNull()?.substringAfterLast('.') ?: ""
        val titre = if (nom.isNotEmpty()) {
            "Supprimer l'import inutilisé $nom"
        } else {
            "Supprimer l'import inutilisé"
        }
        return action(
            kind = "quickfix.remove.import",
            titre = titre,
            editions = listOf(EditionTexte(offset = debutLigne, longueur = fin - debutLigne, texte = "")),
            diagnostic = diagnostic,
        )
    }

    // ------------------------------------------------------------------ //
    // Point-virgule manquant — « quickfix.semicolon »
    // ------------------------------------------------------------------ //

    /**
     * Insère le point-virgule en fin d'instruction (fiche 063 : « le « ; »
     * se retrouve en fin d'instruction ») — insertion pure juste APRÈS le
     * jeton porté par la plage du diagnostic.
     *
     * Découverte figée N6 : un point-virgule manquant émet
     * `ParsingErrorInsertToComplete` avec le JETON à insérer en argument 0
     * — l'action ne sort que si ce jeton EST un « ; » (les autres
     * insertions syntaxiques ne sont pas des points-virgules). La constante
     * `MissingSemiColon` (montages synthétiques) insère sans condition.
     *
     * @param source le texte Java complet.
     * @param diagnostic le diagnostic de point-virgule manquant.
     * @return l'action, ou null (jeton à insérer différent de « ; »).
     */
    internal fun pointVirguleManquant(source: String, diagnostic: Diagnostic): ActionCode? {
        val id = diagnostic.code?.toLongOrNull() ?: return null
        if (id == ID_INSERT_TO_COMPLETE && diagnostic.arguments.firstOrNull() != ";") return null
        val position = diagnostic.plage.finOffset.coerceIn(0, source.length)
        return action(
            kind = "quickfix.semicolon",
            titre = "Insérer le point-virgule manquant",
            editions = listOf(EditionTexte(offset = position, longueur = 0, texte = ";")),
            diagnostic = diagnostic,
        )
    }

    // ------------------------------------------------------------------ //
    // Déclaration locale inutilisée — « quickfix.remove.unused »
    // ------------------------------------------------------------------ //

    /**
     * Supprime la déclaration locale inutilisée (fiche 063 : « la
     * déclaration disparaît ; l'instruction suivante survit »).
     *
     * Trois gardes de prudence (fiche 063) :
     *  1. l'ID doit ÊTRE LocalVariableIsNeverUsed — les PARAMÈTRES
     *     (ArgumentIsNeverUsed) ne se suppriment pas, les membres privés ne
     *     relèvent pas du correcteur texte ;
     *  2. une parenthèse ouvrante rencontrée en remontant vers la borne
     *     précédente écarte la position (liste de paramètres, en-tête de
     *     for, lambda) ;
     *  3. une virgule de profondeur 0 dans l'instruction écarte la
     *     déclaration multi-fragments (« int a = 1, b = 2; » — risque de
     *     supprimer la variable sœur).
     *
     * @param source le texte Java complet.
     * @param diagnostic le diagnostic LocalVariableIsNeverUsed.
     * @return l'action, ou null (une garde s'est fermée).
     */
    internal fun suppressionDeclarationInutilise(source: String, diagnostic: Diagnostic): ActionCode? {
        if (diagnostic.code?.toLongOrNull() != ID_LOCAL_NEVER_USED) return null
        val debut = AnalyseTexte.debutInstruction(source, diagnostic.plage.debutOffset)
        if (debut >= source.length) return null
        // Garde paramètre : parenthèse ouvrante entre la borne précédente
        // et la déclaration.
        val bornePrecedente = positionBornePrecedente(source, debut)
        if (AnalyseTexte.dansParenthesesAvant(source, diagnostic.plage.debutOffset, bornePrecedente)) return null
        val pointVirgule = AnalyseTexte.pointVirguleSuivant(source, debut) ?: return null
        // Garde multi-fragments : virgule de profondeur 0 dans l'instruction.
        if (AnalyseTexte.virgulesProfondeurZero(source, debut, pointVirgule) > 0) return null
        // La coupe : l'instruction ET son point-virgule ; le saut de ligne
        // qui suit immédiatement (blancs TAB/ESPACE seuls) est consommé si
        // la déclaration occupait seule sa fin de ligne.
        var fin = pointVirgule + 1
        var i = fin
        while (i < source.length && (source[i] == ' ' || source[i] == '\t')) i++
        if (i < source.length && source[i] == '\n') fin = i + 1
        val nom = diagnostic.arguments.firstOrNull() ?: source.substring(
            diagnostic.plage.debutOffset,
            diagnostic.plage.finOffset.coerceAtMost(source.length),
        )
        return action(
            kind = "quickfix.remove.unused",
            titre = "Supprimer la déclaration inutilisée $nom",
            editions = listOf(EditionTexte(offset = debut, longueur = fin - debut, texte = "")),
            diagnostic = diagnostic,
        )
    }

    /** La position de la borne précédente (« ; », « { » ou « } ») avant [debut]. */
    private fun positionBornePrecedente(source: String, debut: Int): Int {
        var i = debut - 1
        while (i >= 0) {
            val c = source[i]
            if (c == ';' || c == '{' || c == '}') return i
            i--
        }
        return 0
    }

    // ------------------------------------------------------------------ //
    // Entourage try-catch — « quickfix.surround.trycatch »
    // ------------------------------------------------------------------ //

    /**
     * Entoure l'instruction fautive d'un try-catch ciblant le type EXACT de
     * l'exception (fiche 063 : « un bloc try { … } catch
     * (FileNotFoundException e) { »).
     *
     * Le type exact vient de l'ARGUMENT STRUCTURÉ (arguments[0] — FQN de
     * l'exception, stable entre locales) ; l'import du type est AJOUTÉ si
     * le type est hors java.lang et pas déjà importé (insertion pure à
     * l'ancre d'import N7).
     *
     * @param source le texte Java complet.
     * @param diagnostic le diagnostic UnhandledException.
     * @return l'action, ou null (argument absent, instruction introuvable).
     */
    internal fun entourageTryCatch(source: String, diagnostic: Diagnostic): ActionCode? {
        val fqn = diagnostic.arguments.firstOrNull() ?: return null
        val debut = AnalyseTexte.debutInstruction(source, diagnostic.plage.debutOffset)
        if (debut >= source.length) return null
        val pointVirgule = AnalyseTexte.pointVirguleSuivant(source, debut) ?: return null
        val instruction = source.substring(debut, pointVirgule + 1)
        val indentation = AnalyseTexte.indentation(source, debut)
        val simple = simpleDe(fqn)
        val remplacement = buildString {
            append("try {\n")
            append(indentation)
            append("    ")
            append(instruction)
            append('\n')
            append(indentation)
            append("} catch (")
            append(simple)
            append(" e) {\n")
            append(indentation)
            append('}')
        }
        val editions = ArrayList<EditionTexte>()
        editionsImport(source, fqn)?.let { editions.add(it) }
        editions.add(EditionTexte(offset = debut, longueur = pointVirgule + 1 - debut, texte = remplacement))
        return action(
            kind = "quickfix.surround.trycatch",
            titre = "Entourer avec try-catch ($simple)",
            editions = editions,
            diagnostic = diagnostic,
        )
    }

    // ------------------------------------------------------------------ //
    // Déclaration throws — « quickfix.declare.throws »
    // ------------------------------------------------------------------ //

    /**
     * Déclare `throws` sur la méthode englobante (fiche 063 : « void m(int
     * a) throws InterruptedException » — la clause suit DIRECTEMENT la
     * liste de paramètres ; une clause existante est COMPLÉTÉE : « throws
     * java.io.IOException, InterruptedException »).
     *
     * Le scan d'accolades traverse les blocs de contrôle (fiche 063 : « le
     * throws atterrit sur void m() throws …, jamais sur le if ») —
     * [ChercheurMethode.corpsEnglobant]. L'exception déclarée porte son nom
     * SIMPLE ; son import est ajouté si hors java.lang et pas déjà importé.
     *
     * @param source le texte Java complet.
     * @param diagnostic le diagnostic UnhandledException.
     * @return l'action, ou null (méthode introuvable, exception absente ou
     *   déjà déclarée).
     */
    internal fun declarationThrows(source: String, diagnostic: Diagnostic): ActionCode? {
        val fqn = diagnostic.arguments.firstOrNull() ?: return null
        val corps = ChercheurMethode.corpsEnglobant(source, diagnostic.plage.debutOffset) ?: return null
        val entre = source.substring(corps.finParams, corps.ouverture)
        if (contientThrows(entre)) {
            // Clause existante : COMPLÉTER, jamais remplacer (fiche 063).
            if (exceptionsDeClause(entre).any { it == simpleDe(fqn) || it == fqn }) return null
            var finListe = corps.ouverture
            while (finListe > corps.finParams && source[finListe - 1].isWhitespace()) finListe--
            val editions = ArrayList<EditionTexte>()
            editionsImport(source, fqn)?.let { editions.add(it) }
            editions.add(
                EditionTexte(
                    offset = finListe,
                    longueur = 0,
                    texte = ", " + simpleDe(fqn),
                ),
            )
            return action(
                kind = "quickfix.declare.throws",
                titre = "Déclarer throws ${simpleDe(fqn)}",
                editions = editions,
                diagnostic = diagnostic,
            )
        }
        // Pas de clause : la NOUVELLE clause suit directement la liste de
        // paramètres (fiche 063).
        val editions = ArrayList<EditionTexte>()
        editionsImport(source, fqn)?.let { editions.add(it) }
        editions.add(
            EditionTexte(
                offset = corps.finParams,
                longueur = 0,
                texte = " throws " + simpleDe(fqn),
            ),
        )
        return action(
            kind = "quickfix.declare.throws",
            titre = "Déclarer throws ${simpleDe(fqn)}",
            editions = editions,
            diagnostic = diagnostic,
        )
    }

    /** Vrai ssi le texte entre parenthèses et accolade porte une clause « throws ». */
    private fun contientThrows(entre: String): Boolean =
        entre.trim().startsWith("throws")

    /** Les noms d'exceptions (simples ou FQN) d'une clause throws existante. */
    private fun exceptionsDeClause(entre: String): List<String> =
        entre.trim().removePrefix("throws").trim()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    // ------------------------------------------------------------------ //
    // Insertion de cast — « quickfix.cast »
    // ------------------------------------------------------------------ //

    /**
     * Insère le cast vers le type ATTENDU (fiche 063 : « (String) o — le
     * cast cible le type ATTENDU (deuxième argument du diagnostic), pas le
     * type source » — message ecj « cannot convert from {0} to {1} »).
     *
     * @param source le texte Java complet.
     * @param diagnostic le diagnostic TypeMismatch.
     * @return l'action, ou null (arguments absents).
     */
    internal fun insertionCast(source: String, diagnostic: Diagnostic): ActionCode? {
        if (diagnostic.arguments.size < 2) return null
        val attendu = diagnostic.arguments[1]
        val position = diagnostic.plage.debutOffset.coerceIn(0, source.length)
        val editions = ArrayList<EditionTexte>()
        editionsImport(source, attendu)?.let { editions.add(it) }
        editions.add(
            EditionTexte(
                offset = position,
                longueur = 0,
                texte = "(" + simpleDe(attendu) + ") ",
            ),
        )
        return action(
            kind = "quickfix.cast",
            titre = "Insérer un cast vers ${simpleDe(attendu)}",
            editions = editions,
            diagnostic = diagnostic,
        )
    }

    // ------------------------------------------------------------------ //
    // Candidats d'import — « quickfix.import.fqn »
    // ------------------------------------------------------------------ //

    /**
     * Les candidats d'import depuis l'index des noms (fiche 063 : « au
     * moins 1 action quickfix.import.fqn ; au moins un titre contenant le
     * FQN ; CHAQUE action porte EXACTEMENT 1 édition — insertion PURE
     * (début = fin) d'un texte commençant par « import » et finissant par
     * « ;\n » »).
     *
     * Gardes verrouillées (fiche 063) : seuls les noms ressemblant à des
     * TYPES (initiale MAJUSCULE) reçoivent des candidats ; java.lang n'est
     * JAMAIS proposé ; un FQCN déjà importé (ligne d'import exacte) n'est
     * pas reproposé — un HOMONYME du classpath reste légitimement proposable.
     *
     * @param source le texte Java complet.
     * @param diagnostic le diagnostic UndefinedType.
     * @param fqns les FQCN candidats de l'index (déjà filtrés `$`).
     * @return les actions (0 à N), triées par FQCN.
     */
    internal fun candidatsImport(source: String, diagnostic: Diagnostic, fqns: List<String>): List<ActionCode> {
        if (diagnostic.code?.toLongOrNull() != ID_UNDEFINED_TYPE) return emptyList()
        val nom = diagnostic.arguments.firstOrNull() ?: return emptyList()
        if (nom.isEmpty() || !nom[0].isUpperCase()) return emptyList()
        val ancre = AncreImport.ancre(source)
        return fqns
            .filter { fqcn -> paquetDe(fqcn) != "java.lang" }
            .filter { fqcn -> !dejaImporte(source, fqcn) }
            .sorted()
            .map { fqcn ->
                action(
                    kind = "quickfix.import.fqn",
                    titre = "Importer $nom ($fqcn)",
                    editions = listOf(
                        EditionTexte(offset = ancre, longueur = 0, texte = "import $fqcn;\n"),
                    ),
                    diagnostic = diagnostic,
                )
            }
            .filterNotNull()
    }

    // ------------------------------------------------------------------ //
    // Outils partagés
    // ------------------------------------------------------------------ //

    /**
     * La fabrique d'action du paquet : le GARDIEN du contrat « aucune
     * action sans édits » (fiche 063) — une liste d'éditions vide ou nulle
     * rend NULL, l'action coquille vide ne sort jamais.
     */
    internal fun action(
        kind: String,
        titre: String,
        editions: List<EditionTexte>?,
        diagnostic: Diagnostic?,
    ): ActionCode? {
        if (editions.isNullOrEmpty()) return null
        return ActionCode(
            kind = kind,
            titre = titre,
            commande = ModCommande.EditsTexte(editions.sortedBy { it.offset }),
            diagnostic = diagnostic,
        )
    }

    /**
     * L'édition d'import d'un FQN (insertion PURE à l'ancre N7) — null si
     * le type est java.lang (implicitement importé) ou déjà importé.
     */
    internal fun editionsImport(source: String, fqn: String): EditionTexte? {
        if (paquetDe(fqn) == "java.lang") return null
        if (dejaImporte(source, fqn)) return null
        return EditionTexte(
            offset = AncreImport.ancre(source),
            longueur = 0,
            texte = "import $fqn;\n",
        )
    }

    /** Vrai ssi la source porte DÉJÀ la ligne d'import exacte du FQN. */
    internal fun dejaImporte(source: String, fqn: String): Boolean {
        for (ligne in source.lineSequence()) {
            val pliee = ligne.trim()
            if (pliee == "import $fqn;") return true
        }
        return false
    }

    /** Le nom simple d'un FQN (le segment après le dernier point). */
    internal fun simpleDe(fqn: String): String = fqn.substringAfterLast('.')

    /** Le paquet d'un FQN (vide si pas de point). */
    internal fun paquetDe(fqn: String): String =
        if (fqn.contains('.')) fqn.substringBeforeLast('.') else ""
}

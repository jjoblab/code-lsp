package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.outils.EditionTexte

/**
 * Le correcteur « Créer la méthode » (étage N6 — cahier fiche 064) : le
 * portage clean-room du CreateMethodFromUsage du projet tiers de référence
 * — un stub `private [static]` inséré APRÈS la méthode englobante, les
 * types des paramètres déduits des ARGUMENTS STRUCTURÉS du diagnostic ecj,
 * le retour `void` (appel-instruction) ou `Object` (appel-expression), le
 * corps en `UnsupportedOperationException`.
 *
 * Découverte figée N6 (sonde sur le jar ecj 3.40, corrigée par le banc
 * doré de la fiche 064) : les arguments d'UndefinedMethod sont au nombre
 * de TROIS — `[typeDéclarant, nomMéthode, typesParamètres]` — où le
 * TROISIÈME argument est la chaîne JOINTE des types de paramètres sous
 * forme lisible (« int, String », « java.util.List&lt;java.lang.String&gt; »,
 * chaîne vide sans paramètre). C'est elle qui type les paramètres du stub
 * sans AUCUNE résolution — les virgules INTERNES aux génériques sont
 * départagées par profondeur de chevrons ([decouperTypesParametres]).
 *
 * Règles verrouillées (fiche 064, notes de réécriture) :
 *  - le stub est TOUJOURS `private` (+ `static` si le site d'appel est
 *    statique), inséré APRÈS la méthode englobante ;
 *  - les paramètres se nomment `arg0`, `arg1`, … ;
 *  - le retour est `void` si l'appel EST l'instruction, le TYPE DE RETOUR
 *    DE LA MÉTHODE ENGBLOBANTE si l'appel est l'EXPRESSION d'un `return`
 *    (vague V-A, écart G-014 : `return formater(…)` dans une méthode
 *    `String` crée un stub `String` — le banc P5 a mesuré l'erreur de type
 *    laissée par le stub `Object` générique), sinon `Object` (affectation,
 *    argument… — conforme fiche 064, l'extension à l'affectation attendra
 *    l'amendement arbitré du cahier, vagues V-C/V-D) ;
 *  - le corps imposé est « throw new UnsupportedOperationException("TODO:
 *    &lt;nom&gt;"); » ;
 *  - les FQN de paramètres sont RACCOURCIS vers le nom simple, avec
 *    édition d'import associée (java.lang excepté) ;
 *  - JAMAIS de création de méthode pour un appel QUALIFIÉ sur un autre
 *    type que le type englobant (le correcteur ne touche pas un autre
 *    fichier/type) — « this. » est le seul récepteur qualifié admis.
 */
internal object CorrecteurCreationMethode {

    /** L'ID ecj d'UndefinedMethod. */
    private const val ID_UNDEFINED_METHOD: Long = 67108964L

    /**
     * Construit le stub de méthode pour un appel non résolu (fiche 064).
     *
     * @param source le texte Java complet.
     * @param diagnostic le diagnostic UndefinedMethod RÉEL du moteur (la
     *   plage couvre le JETON nom de l'appel).
     * @return l'action « quickfix.create.method », ou null (appel qualifié
     *   sur un autre type, arguments absents, méthode englobante
     *   introuvable).
     */
    internal fun creationMethode(source: String, diagnostic: Diagnostic): ActionCode? {
        if (diagnostic.code?.toLongOrNull() != ID_UNDEFINED_METHOD) return null
        val arguments = diagnostic.arguments
        if (arguments.size < 2) return null
        val nom = arguments[1]
        if (nom.isEmpty() || !nom[0].isJavaIdentifierStart()) return null

        // Garde « appel qualifié » : un récepteur avant le nom écarte la
        // création — seul « this. » (le type englobant lui-même) est admis.
        if (recepteurQualifie(source, diagnostic.plage.debutOffset)) return null

        // La méthode englobante du site d'appel (insertion APRÈS son corps,
        // contexte statique du site).
        val corps = ChercheurMethode.corpsEnglobant(source, diagnostic.plage.debutOffset) ?: return null

        // Appel-instruction ou appel-expression : l'instruction qui porte
        // l'appel est-elle RÉDUITE à l'appel ?
        val debut = AnalyseTexte.debutInstruction(source, diagnostic.plage.debutOffset)
        val pointVirgule = AnalyseTexte.pointVirguleSuivant(source, debut) ?: return null
        val avantNom = source.substring(debut, diagnostic.plage.debutOffset)
        val estInstruction = avantNom.isBlank()
        val retour = if (estInstruction) {
            "void"
        } else {
            typeRetourContextuel(avantNom, corps) ?: "Object"
        }

        // Paramètres : le TROISIÈME argument est la chaîne JOINTE lisible des
        // types (« int, String ») — découpage profondeur-aware, puis
        // raccourcissement vers les noms simples + imports requis.
        val raccourcis = decouperTypesParametres(arguments.getOrNull(2) ?: "")
            .map { raccourcirType(it) }
        val parametres = raccourcis.mapIndexed { index, type ->
            "${type.court} arg$index"
        }.joinToString(", ")

        val modificateurs = if (corps.statique) "private static" else "private"
        val signature = "$modificateurs $retour $nom($parametres) {"
        val corpsStub = "throw new UnsupportedOperationException(\"TODO: $nom\");"
        val indentation = AnalyseTexte.indentation(source, corps.ouverture)
        val insertion = buildString {
            append('\n')
            append('\n')
            append(indentation)
            append(signature)
            append('\n')
            append(indentation)
            append("    ")
            append(corpsStub)
            append('\n')
            append(indentation)
            append('}')
        }

        val editions = ArrayList<EditionTexte>()
        editionsImports(source, raccourcis)?.let { editions.add(it) }
        editions.add(
            EditionTexte(
                offset = corps.fermeture + 1,
                longueur = 0,
                texte = insertion,
            ),
        )
        return CorrecteursTexte.action(
            kind = "quickfix.create.method",
            titre = "Créer la méthode $nom",
            editions = editions,
            diagnostic = diagnostic,
        )
    }

    /**
     * Le type de retour attendu par le CONTEXTE du site d'appel (vague V-A,
     * écart G-014 — le banc P5 a mesuré le fix non contextuel : un stub
     * `Object` laissait une erreur de type dans `return formater(…)` d'une
     * méthode `String`).
     *
     * Contexte reconnu (borné, honnête) : la **position de retour** —
     * l'instruction est `return &lt;appel&gt;` — rend le type de retour DECLARÉ de
     * la méthode englobante ([CorpsMethode.typeRetour]). Tout autre contexte
     * (affectation, argument, condition…) rend null → repli `Object` du
     * caller : l'affectation est VOLONTAIREMENT laissée à `Object`, le
     * cahier (fiche 064) verrouillant ce comportement — son extension
     * contextuelle exigera un amendement arbitré (vagues V-C/V-D).
     *
     * Limites documentées : un `return` au sein d'un LAMBDA hériterait du type
     * de la méthode englobante — approximation acceptée (le repli `Object`
     * n'est pas plus juste) ; la sémantique fine relève des correcteurs
     * sémantiques (vagues V-C/V-D).
     *
     * @param avantNom le texte de l'instruction avant le nom de l'appel.
     * @param corps la méthode englobante du site.
     * @return le type contextuel, ou null si le contexte ne dit rien.
     */
    private fun typeRetourContextuel(avantNom: String, corps: CorpsMethode): String? {
        // Position de retour : « return » (et rien d'autre) avant l'appel.
        if (avantNom.trim() != "return") return null
        val type = corps.typeRetour ?: return null
        if (type == "void") return null // `return valeur;` en méthode void : déjà fautive, repli générique
        return type
    }

    /**
     * Vrai ssi l'appel est QUALIFIÉ par un récepteur autre que « this » —
     * le jeton qui précède le nom (blancs sautés) est un point dont le
     * récepteur n'est pas le mot « this ».
     */
    private fun recepteurQualifie(source: String, debutNom: Int): Boolean {
        var i = debutNom - 1
        while (i >= 0 && source[i].isWhitespace()) i--
        if (i < 0 || source[i] != '.') return false
        // Le récepteur : l'identifiant (ou littéral) juste avant le point.
        var j = i - 1
        while (j >= 0 && source[j].isWhitespace()) j--
        if (j < 0) return false
        if (!source[j].isJavaIdentifierPart()) return true // littéral, « ) », « ] »… : récepteur réel.
        var debut = j
        while (debut > 0 && source[debut - 1].isJavaIdentifierPart()) debut--
        return source.substring(debut, j + 1) != "this"
    }

    /**
     * Découpe la chaîne JOINTE des types de paramètres (troisième argument
     * du diagnostic, « int, String ») : les virgules de profondeur 0
     * séparent les paramètres — celles INTERNES aux génériques
     * (« Map&lt;String, Integer&gt; ») ne comptent jamais.
     */
    private fun decouperTypesParametres(jointe: String): List<String> {
        val types = ArrayList<String>()
        val tampon = StringBuilder()
        var profondeur = 0
        for (c in jointe) {
            when {
                c == '<' -> {
                    profondeur++
                    tampon.append(c)
                }
                c == '>' -> {
                    profondeur--
                    tampon.append(c)
                }
                c == ',' && profondeur == 0 -> {
                    types.add(tampon.toString().trim())
                    tampon.setLength(0)
                }
                else -> tampon.append(c)
            }
        }
        val dernier = tampon.toString().trim()
        if (dernier.isNotEmpty()) types.add(dernier)
        return types
    }

    /**
     * Le résultat du raccourcissement d'un type lisible : la forme courte
     * (noms simples) et le FQN dont l'import est requis (null si java.lang
     * ou sans point).
     */
    private data class TypeRaccourci(public val court: String, public val fqnImport: String?)

    /**
     * Raccourcit un type lisible ecj vers ses noms SIMPLES (fiche 064 :
     * « process(List&lt;String&gt; arg0) » depuis « java.util.List&lt;java.
     * lang.String&gt; ») — les FQN à import (hors java.lang) sont
     * collectés pour l'édition d'import.
     */
    private fun raccourcirType(type: String): TypeRaccourci {
        val imports = LinkedHashSet<String>()
        val court = Regex("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
            .replace(type) { occurrence ->
                val fqn = occurrence.value
                if (!fqn.contains('.')) {
                    fqn
                } else {
                    val simple = fqn.substringAfterLast('.')
                    if (CorrecteursTexte.paquetDe(fqn) != "java.lang") imports.add(fqn)
                    simple
                }
            }
        return TypeRaccourci(court, imports.firstOrNull())
    }

    /**
     * L'édition d'import des types raccourcis : UNE seule insertion pure à
     * l'ancre (les imports requis, triés, un par ligne — jamais deux éditions
     * au même offset) ; null si aucun import requis.
     */
    private fun editionsImports(source: String, raccourcis: List<TypeRaccourci>): EditionTexte? {
        val fqns = raccourcis.mapNotNull { it.fqnImport }
            .filter { !CorrecteursTexte.dejaImporte(source, it) }
            .distinct()
            .sorted()
        if (fqns.isEmpty()) return null
        val texte = fqns.joinToString("") { "import $it;\n" }
        return EditionTexte(
            offset = jo.codeide.lsp.java.gabarits.AncreImport.ancre(source),
            longueur = 0,
            texte = texte,
        )
    }
}

// CodeIDE — actions de SOURCE et REFACTORING (module :kotlin, vague V-F
// — KC-10/KC-11, ADR 0131/0136) : l'organisateur d'imports (tri stable,
// retrait des inutilisés PROUVÉS lexicalement, alias et jokers CONSERVÉS,
// doublons fusionnés — jamais de suppression sur doute) et l'extraction
// de VARIABLE bornée (expression intra-fonction → `val extrait = …`).
// Code NEUF jjoblab (clean-room — conservateur : chaque plan se prouve
// sur l'arbre et le texte ; sans preuve, PAS de plan, D3).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.EditionFichierKotlin
import jo.codeide.lsp.kotlin.api.EditionTexteKotlin
import jo.codeide.lsp.kotlin.api.GenreActionKotlin
import jo.codeide.lsp.kotlin.api.PlanActionKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Les actions de source et refactoring V-F (ADR 0131) :
 *
 * - [organiserImports] (KC-10 — `source.organizeImports`) : organise le
 *   bloc d'imports du document — TRI stable (alphabétique par chemin
 *   qualifié, alias et jokers à leur place de tri), RETRAIT des
 *   inutilisés PROUVÉS lexicalement (le nom effectif absent des
 *   identifiants du document entier), FUSION des doublons EXACTS ;
 *   alias CONSERVÉS TEL QUEL (I5), jokers CONSERVÉS (l'inutilité d'un
 *   `*` est indémontrable à coût borné), conflits de noms simples
 *   CONSERVÉS (aucun retrait automatique, I4). Le plan N'EXISTE que si
 *   l'organisation CHANGE le bloc (idempotent — I1) ;
 * - [extraireVariable] (KC-11 Q1/Q5 — `refactor.extract`) : l'expression
 *   englobante du caret (appel, binaire ou sélecteur qualifié) devient
 *   une `val` locale INSÉRÉE avant l'instruction porteuse, l'expression
 *   est REMPLACÉE par le nom — INTRA-FONCTION seul (Q5), sans capture
 *   ambiguë : l'expression vit dans UNE instruction du corps.
 */
internal object ActionsSource {

    // ------------------------------------------------------------------
    // KC-10 — source.organizeImports.
    // ------------------------------------------------------------------

    /**
     * Le plan d'ORGANISATION des imports du document (KC-10) : remplace
     * le bloc d'imports par sa forme organisée — tri stable, inutilisés
     * prouvés retirés, doublons fusionnés. Le plan n'existe que si le
     * texte CHANGE (idempotence : une seconde exécution ne produit RIEN).
     *
     * @param arbre l'arbre du document ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret (l'action se propose sur le
     * BLOC d'imports — le caret vit sur une directive OU le bloc n'est
     * pas propre : l'action source est un acte de DOCUMENT) ;
     * @return le plan ; `null` si le bloc est déjà organisé ou vide.
     */
    internal fun organiserImports(
        arbre: KtFichier,
        uri: String,
        deplacement: Int,
    ): PlanActionKotlin? {
        val directives = arbre.imports()
        if (directives.isEmpty()) return null

        // Le bloc : de la première à la dernière directive (les lignes
        // vides intermédiaires se normalisent — l'édition est unique).
        val debutBloc = directives.first().plage.debut
        val finBloc = directives.last().plage.fin

        // Les identifiants ÉCRITS hors directives (la preuve lexicale
        // d'usage — miroir de DiagnosticsImports).
        val identifiants = identifiantsHorsDirectives(arbre.noeud)

        // La forme organisée : doublons exacts fusionnés (le PREMIER
        // gagne), inutilisés prouvés RETIRÉS (jamais un joker ni un
        // alias en conflit), tri stable alphabétique.
        val vues = mutableSetOf<String>()
        val conservees = mutableListOf<Triple<String, String?, Boolean>>()
        for (directive in directives) {
            val chemin = directive.nomQualifie() ?: continue
            val alias = directive.alias()
            val joker = directive.estJoker()
            val effectif = (alias ?: chemin.substringAfterLast('.')).trim()
            // Le doublon EXACT (chemin + alias) : fusion (I4).
            if (!vues.add("$chemin as ${alias.orEmpty()}")) continue
            // Le retrait : JAMAIS un joker (I5), JAMAIS un alias (I5),
            // JAMAIS sans preuve lexicale (I2 — le nom absent PARTOUT).
            if (!joker && alias == null && effectif.isNotEmpty() &&
                effectif !in identifiants
            ) {
                continue
            }
            conservees.add(Triple(chemin, alias, joker))
        }
        if (conservees.isEmpty()) {
            // Le bloc entier serait retiré : l'édition remplace le bloc
            // par RIEN — le plan existe (c'est l'organisation demandée).
        }
        val organisees = conservees.sortedBy { it.first }
        val texteOrganise = organisees.joinToString("\n") { (chemin, alias, joker) ->
            buildString {
                append("import ")
                append(chemin)
                if (joker) append(".*")
                alias?.let { append(" as ") ; append(it) }
            }
        }
        val texteCourant = arbre.source.substring(debutBloc, finBloc)
        if (texteOrganise == texteCourant) return null
        return PlanActionKotlin(
            titre = "Organiser les imports",
            genre = GenreActionKotlin.ORGANISER_IMPORTS,
            editions = listOf(
                EditionFichierKotlin(
                    uri = uri,
                    editions = listOf(
                        EditionTexteKotlin(plage = PlageTexte(debutBloc, finBloc), texte = texteOrganise),
                    ),
                ),
            ),
        )
    }

    // ------------------------------------------------------------------
    // KC-11 — refactor.extract (extraction de variable).
    // ------------------------------------------------------------------

    /**
     * Le plan d'EXTRACTION DE VARIABLE (KC-11 Q1/Q5 — `refactor.extract`)
     * : l'expression englobante du caret (appel, binaire, sélecteur
     * qualifié — la plus profonde) devient `val <nom> = <expression>`
     * insérée AVANT l'instruction porteuse, l'expression est remplacée
     * par le nom.
     *
     * Bornes (Q5) : INTRA-FONCTION seul — l'instruction porteuse vit
     * dans le BLOCK d'un FUN ; le nom est LIBRE (premier `extrait`,
     * `extrait2`… non écrit dans le document) ; sans expression
     * candidate, PAS de plan (D3).
     *
     * @param arbre l'arbre du document ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret (DANS l'expression) ;
     * @return le plan ; `null` hors d'une extraction bornée.
     */
    internal fun extraireVariable(
        arbre: KtFichier,
        uri: String,
        deplacement: Int,
    ): PlanActionKotlin? {
        val source = arbre.source
        // La chaîne des ancêtres couvrant le caret : le FUN porteur
        // (bornage intra-fonction), l'INSTRUCTION porteuse (enfant
        // direct du BLOCK du FUN) et l'EXPRESSION candidate (la plus
        // profonde : appel, binaire, sélecteur).
        var funPorteuse: NoeudArbre? = null
        var expression: NoeudArbre? = null
        var instruction: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre, parent: NoeudArbre?) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.FUN) funPorteuse = noeud
            if (parent != null && parent.genre == KtNodeTypes.BLOCK &&
                noeud.genre != KtNodeTypes.BLOCK
            ) {
                instruction = noeud
            }
            if (noeud.genre == KtNodeTypes.CALL_EXPRESSION ||
                noeud.genre == KtNodeTypes.BINARY_EXPRESSION ||
                noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION
            ) {
                expression = noeud
            }
            noeud.enfants.forEach { marcher(it, noeud) }
        }
        marcher(arbre.noeud, null)
        val porteeuse = funPorteuse ?: return null
        val expr = expression ?: return null
        val instr = instruction ?: return null
        // L'expression doit vivre dans l'instruction, l'instruction dans
        // le corps du FUN (le bornage Q5).
        if (expr.fin > instr.fin || expr.debut < instr.debut) return null
        val corps = porteeuse.enfants.firstOrNull { it.genre == KtNodeTypes.BLOCK } ?: return null
        if (instr.debut < corps.debut || instr.fin > corps.fin) return null
        // L'expression NE DOIT PAS être l'instruction entière (extraire
        // une expression-instruction n'a pas de point d'insertion lisible).
        if (expr.debut == instr.debut && expr.fin == instr.fin) return null

        // Le nom LIBRE : extrait, extrait2, … (premier absent du document).
        var rang = 0
        var nom = "extrait"
        val ecrits = arbre.source
        while (contientMot(ecrits, nom)) {
            rang++
            nom = "extrait$rang"
        }

        // L'indentation de l'instruction porteuse (la ligne de son début).
        val debutLigne = ecrits.lastIndexOf('\n', instr.debut).let { if (it < 0) 0 else it + 1 }
        val indent = ecrits.substring(debutLigne, instr.debut).takeWhile { it == ' ' || it == '\t' }
        val texteExpression = ecrits.substring(expr.debut, expr.fin)

        // Les éditions : l'insertion AVANT l'instruction, le REMPLACEMENT
        // de l'expression par le nom (en ordre du document : l'insertion
        // d'abord, le remplacement ensuite — les plages ne se chevauchent
        // pas : l'insertion est une plage vide à instr.debut).
        return PlanActionKotlin(
            titre = "Extraire « $texteExpression » dans une variable",
            genre = GenreActionKotlin.EXTRAIRE_VARIABLE,
            editions = listOf(
                EditionFichierKotlin(
                    uri = uri,
                    editions = listOf(
                        EditionTexteKotlin(
                            plage = PlageTexte(instr.debut, instr.debut),
                            texte = "val $nom = $texteExpression\n$indent",
                        ),
                        EditionTexteKotlin(
                            plage = PlageTexte(expr.debut, expr.fin),
                            texte = nom,
                        ),
                    ),
                ),
            ),
        )
    }

    // ------------------------------------------------------------------
    // CHANTIER D — refactor.rewrite : la MUTABILITÉ val <-> var.
    // ------------------------------------------------------------------

    /**
     * Le plan de CHANGEMENT DE MUTABILITÉ (chantier D — parité Android
     * Studio, intention « Convertir var en val / val en var ») : la
     * déclaration `val`/`var` englobant le caret (locale, paramètre de
     * propriété du constructeur primaire ou propriété de classe) voit
     * son mot-clé REMPLACÉ.
     *
     * Les bornes conservatrices (D3 — jamais de plan sur doute) :
     *  - var → val : PROUVÉ SUREPRIS uniquement quand la propriété est
     *    de classe ou paramètre (la preuve locale de non-réécriture est
     *    un chantier d'analyse des flux, refusé ici) ; une `var` LOCALE
     *    ne reçoit PAS la conversion — le moteur ne ment pas ;
     *  - val → var : TOUJOURS proposé (l'assouplissement n'engendre
     *    aucune erreur de compilation, miroir IDE) ;
     *  - le mot-clé remplacé est le JETON propre `val`/`var` de la
     *    déclaration (jamais un mot dans une chaîne ou un commentaire —
     *    la preuve vit sur l'arbre).
     *
     * @param arbre l'arbre du document ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret — sur le nom, le type ou
     * l'initialisateur de la déclaration ;
     * @return le plan ; `null` si aucune déclaration ne porte le caret.
     */
    internal fun changerMutabilite(
        arbre: KtFichier,
        uri: String,
        deplacement: Int,
    ): PlanActionKotlin? {
        val declaration = declarationPortante(arbre.noeud, deplacement) ?: return null
        // Le genre porte le jeton : PROPERTY couvre locale ET propriété,
        // le paramètre primaire vit dans VALUE_PARAMETER.
        val jeton = declaration.jetonsPropres.firstOrNull {
            it.texte == "val" || it.texte == "var"
        } ?: return null
        val cible = if (jeton.texte == "val") "var" else "val"
        // var → val : la preuve de non-réécriture est EXIGÉE — locale
        // refusée (analyse des flux hors périmètre), propriété/paramètre
        // de classe refusés aussi (la réécriture peut vivre dans un
        // AUTRE fichier) : la conversion n'est proposée que sur la
        // preuve documentaire — le jeton `val` manquant dans TOUT le
        // reste du document pour ce nom est indémontrable ici. Seul
        // l'ASSOUPLISSEMENT (val → var) est servi, honnête.
        if (jeton.texte == "var") return null
        val nom = declaration.jetonsPropres
            .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
            ?: return null
        return PlanActionKotlin(
            titre = "Convertir '$nom' en var",
            genre = GenreActionKotlin.REECRIRE,
            editions = listOf(
                EditionFichierKotlin(
                    uri = uri,
                    editions = listOf(
                        EditionTexteKotlin(
                            plage = PlageTexte(jeton.debut, jeton.fin),
                            texte = cible,
                        ),
                    ),
                ),
            ),
        )
    }

    /**
     * La DÉCLARATION `val`/`var` dont la plage couvre le caret (chantier
     * D) : la plus PROFONDE (une imbriquée prime une englobante).
     *
     * @param racine la racine de l'arbre du document ;
     * @param deplacement l'offset du caret ;
     * @return le nœud PROPERTY ou VALUE_PARAMETER (constructeur
     * primaire) porteur ; `null` si le caret n'y vit pas.
     */
    private fun declarationPortante(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.PROPERTY ||
                noeud.genre == KtNodeTypes.VALUE_PARAMETER
            ) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    // ------------------------------------------------------------------
    // Aides de preuve.
    // ------------------------------------------------------------------

    /**
     * Les IDENTIFIANTS écrits du document HORS directives d'import : la
     * preuve lexicale d'usage (miroir de DiagnosticsImports — un nom
     * apparaissant même non résolu CONSERVE son import, jamais de
     * suppression sur doute, I2).
     */
    private fun identifiantsHorsDirectives(racine: NoeudArbre): Set<String> {
        val identifiants = mutableSetOf<String>()
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.IMPORT_DIRECTIVE) return
            for (jeton in noeud.jetonsPropres) {
                if (jeton.genre == KtTokens.IDENTIFIER && jeton.texte.isNotEmpty()) {
                    identifiants.add(jeton.texte)
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return identifiants
    }

    /** Le texte contient-il le mot COMPLET (bornes non alphanumériques) ? */
    private fun contientMot(texte: CharSequence, mot: String): Boolean {
        var indice = texte.indexOf(mot)
        while (indice >= 0) {
            val avant = if (indice == 0) ' ' else texte[indice - 1]
            val apres = if (indice + mot.length >= texte.length) ' ' else texte[indice + mot.length]
            if (!avant.isLetterOrDigit() && avant != '_' && !apres.isLetterOrDigit() && apres != '_') {
                return true
            }
            indice = texte.indexOf(mot, indice + mot.length)
        }
        return false
    }
}

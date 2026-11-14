// CodeIDE — actions de SOURCE et REFACTORING (module :kotlin, vague V-F
// — KC-10/KC-11, ADR 0131/0136) : l'organisateur d'imports (tri stable,
// retrait des inutilisés PROUVÉS lexicalement, alias et jokers CONSERVÉS,
// doublons fusionnés — jamais de suppression sur doute) et l'extraction
// de VARIABLE bornée (expression intra-fonction → `val extrait = …`).
// Code NEUF jjoblab (clean-room — conservateur : chaque plan se prouve
// sur l'arbre et le texte ; sans preuve, PAS de plan, D3).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.DiagnosticKotlin
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
    // CHANTIER E — refactor.inline : l'INLINE de variable.
    // ------------------------------------------------------------------

    /**
     * Le plan d'INLINE de la variable englobant le caret (chantier E —
     * parité Android Studio, « Inline Variable ») : chaque USAGE du nom
     * dans la fonction englobante est REMPLACÉ par l'initialisateur, la
     * DÉCLARATION est RETIRÉE.
     *
     * Les bornes conservatrices (D3 — jamais de plan sur doute) :
     *  - `val` LOCALE SEULE (jamais `var` — l'inline changerait la
     *    sémantique de réécriture ; jamais une propriété de classe —
     *    l'usage peut vivre dans un autre fichier) ;
     *  - l'initialisateur est SANS EFFET DE BORD DÉMONTRABLE : un
     *    LITTÉRAL (nombre, chaîne, booléen, null) ou une RÉFÉRENCE
     *    SIMPLE (identifiant nu) — un appel ou une binaire pourrait
     *    lancer ou répéter un calcul : refusé ;
     *  - l'AMBIGUÏTÉ refuse : une SECONDE déclaration du même nom dans
     *    la fonction (l'ombre n'est pas arbitrée), aucun usage (une
     *    variable morte n'est pas un inline).
     *
     * @param arbre l'arbre du document ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret — sur la déclaration ;
     * @return le plan ; `null` si une borne refuse.
     */
    internal fun inlineVariable(
        arbre: KtFichier,
        uri: String,
        deplacement: Int,
    ): PlanActionKotlin? {
        val declaration = declarationPortante(arbre.noeud, deplacement) ?: return null
        if (declaration.genre != KtNodeTypes.PROPERTY) return null
        // `val` seul — le jeton prouve la non-mutabilité.
        val jetonVal = declaration.jetonsPropres.firstOrNull { it.texte == "val" }
            ?: return null
        val jetonNom = declaration.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
            ?: return null
        val nom = jetonNom.texte

        // LOCALE seule : la déclaration vit dans un CORPS de fonction
        // (un PROPERTY dont un parent est un BLOCK sous FUN).
        val parents = parentDe(arbre.noeud, declaration.debut)
        if (parents.none { it.genre == KtNodeTypes.BLOCK }) return null
        val fonction = parents.firstOrNull { it.genre == KtNodeTypes.FUN } ?: return null

        // L'INITIALISATEUR : le dernier enfant EXPRESSION de la
        // déclaration — littéral ou référence simple seule.
        val initialiseur = declaration.enfants.lastOrNull {
            it.genre != KtNodeTypes.TYPE_REFERENCE && it.genre != KtNodeTypes.MODIFIER_LIST
        } ?: return null
        val texteInitialisateur = arbre.source
            .substring(initialiseur.debut, initialiseur.fin).trim()
        val estLitteral = texteInitialisateur.startsWith("\"") ||
            texteInitialisateur.matches(Regex("-?[0-9][0-9_]*((\\.[0-9]+)?[fFLdD]?)?|true|false|null"))
        val estReferenceSimple = initialiseur.genre == KtNodeTypes.REFERENCE_EXPRESSION
        if (!estLitteral && !estReferenceSimple) return null

        // Les USAGES : les identifiants du nom dans la fonction, HORS
        // déclaration — et l'AMBIGUÏTÉ : une seconde DÉCLARATION du même
        // nom refuse le plan.
        val usages = ArrayList<PlageTexte>()
        var secondeDeclaration = false
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.PROPERTY) {
                val jeton = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
                if (jeton != null && jeton.texte == nom && noeud !== declaration) {
                    secondeDeclaration = true
                }
            }
            if (noeud !== declaration &&
                noeud.genre == KtNodeTypes.REFERENCE_EXPRESSION &&
                noeud.texteCanonique == nom
            ) {
                usages.add(PlageTexte(noeud.debut, noeud.fin))
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(fonction)
        if (secondeDeclaration) return null
        if (usages.isEmpty()) return null

        // Les éditions : le RETRAIT de la LIGNE de déclaration (du
        // début de ligne à la fin de ligne incluse — l'indent ne
        // survit pas), puis chaque usage remplacé — en ordre du
        // document, plages disjointes.
        val source = arbre.source
        val debutLigne = source.lastIndexOf('\n', declaration.debut).let { if (it < 0) 0 else it + 1 }
        var finLigne = source.indexOf('\n', declaration.fin)
        if (finLigne < 0) finLigne = source.length else finLigne += 1
        val editions = ArrayList<EditionTexteKotlin>()
        editions.add(
            EditionTexteKotlin(plage = PlageTexte(debutLigne, finLigne), texte = ""),
        )
        for (usage in usages) {
            editions.add(EditionTexteKotlin(plage = usage, texte = texteInitialisateur))
        }
        return PlanActionKotlin(
            titre = "Inline '$nom' (${usages.size} usage${if (usages.size > 1) "s" else ""})",
            genre = GenreActionKotlin.INLINER_VARIABLE,
            editions = listOf(EditionFichierKotlin(uri = uri, editions = editions)),
        )
    }

    /**
     * La chaîne des PARENTS d'un nœud à une position (chantier E) — du
     * plus proche à la racine.
     */
    private fun parentDe(racine: NoeudArbre, deplacement: Int): List<NoeudArbre> {
        val parents = ArrayList<NoeudArbre>()
        fun marcher(noeud: NoeudArbre): Boolean {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return false
            // Le nœud CONTIENT la position : il reste dans la chaîne —
            // la feuille contenante est le nœud le plus profond (elle
            // retourne vrai, la chaîne ne se déroule pas).
            parents.add(noeud)
            for (enfant in noeud.enfants) {
                if (marcher(enfant)) return true
            }
            return true
        }
        marcher(racine)
        return parents.reversed()
    }

    // ------------------------------------------------------------------
    // CHANTIER G — quickfix @Suppress : la suppression du diagnostic.
    // ------------------------------------------------------------------

    /**
     * Le plan de SUPPRESSION d'un diagnostic (chantier G — parité
     * IntelliJ, quickfix « Suppress… ») : l'annotation
     * `@Suppress("code")` est INSÉRÉE sur la DÉCLARATION englobante du
     * diagnostic (la fonction, la classe, la propriété — la plus
     * profonde), au-dessus de ses modificateurs.
     *
     * Les bornes conservatrices (D3 — jamais de plan sur doute) :
     *  - le diagnostic vit dans CE document (sa plage y est) et un CODE
     *    stable l'identifie (le moteur n'insère pas un Suppress sans
     *    savoir ce qu'il supprime) ;
     *  - la déclaration englobante existe (FUN, CLASS, PROPERTY,
     *    OBJECT) — un diagnostic hors déclaration (import, haut de
     *    fichier) ne reçoit PAS de plan : la cible de l'annotation
     *    serait le FICHIER, un acte plus large que le quickfix annoncé ;
     *  - une annotation @Suppress du MÊME code existe déjà sur la
     *    déclaration : le plan REFUSE (redondant — le respect de la
     *    suppression le filtrerait de toute façon) ;
     *  - le texte inséré porte le MOT-CLÉ seul — le diagnostic couvre
     *    le caret OU vit à la LIGNE du caret (l'ampoule apparaît sur
     *    la ligne soulignée, miroir IDE).
     *
     * @param arbre l'arbre du document ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret ;
     * @param diagnostics les diagnostics COURANTS du document (le
     * moteur les re-prouve à la requête — l'alignement est par
     * construction) ;
     * @return le plan ; `null` si aucune borne ne s'applique.
     */
    internal fun supprimerDiagnostic(
        arbre: KtFichier,
        uri: String,
        deplacement: Int,
        diagnostics: List<DiagnosticKotlin>,
    ): PlanActionKotlin? {
        if (diagnostics.isEmpty()) return null
        // Le diagnostic COUVRANT le caret (le plus profond = le plus
        // petit plage qui contient le caret).
        val cible = diagnostics
            .filter { diagnostic ->
                deplacement >= diagnostic.plage.debut && deplacement <= diagnostic.plage.fin
            }
            .minByOrNull { it.plage.fin - it.plage.debut }
            ?: return null
        // La DÉCLARATION englobante du diagnostic (la plus profonde —
        // l'annotation y est INSÉRÉE ; IntelliJ supprime au plus près).
        val declaration = declarationSupprimable(arbre.noeud, cible.plage.debut) ?: return null
        // Le code du diagnostic (l'identifiant STABLE du catalogue).
        val code = cible.code.code
        // Une annotation @Suppress du MÊME code existe déjà sur la
        // déclaration OU UNE ENGLOBANTE (la région est déjà couverte —
        // le plan serait redondant) : refus.
        if (suppressionDejaPresente(arbre.noeud, declaration, code)) return null

        // L'INSERTION : au-dessus de la déclaration, sur SA ligne —
        // l'indentation de la déclaration est reprise (miroir du
        // formateur), la ligne est coupée avant le premier jeton.
        val source = arbre.source
        val debutLigne = source.lastIndexOf('\n', declaration.debut).let { if (it < 0) 0 else it + 1 }
        val indent = source.substring(debutLigne, declaration.debut).takeWhile { it == ' ' || it == '\t' }
        return PlanActionKotlin(
            titre = "Supprimer '${cible.code.code}' pour cette déclaration",
            genre = GenreActionKotlin.RAPIDE,
            editions = listOf(
                EditionFichierKotlin(
                    uri = uri,
                    editions = listOf(
                        EditionTexteKotlin(
                            plage = PlageTexte(debutLigne, debutLigne),
                            texte = "$indent@Suppress(\"$code\")\n",
                        ),
                    ),
                ),
            ),
        )
    }

    /**
     * La DÉCLARATION porteuse d'une SUPPRESSION (chantier G) : le
     * FUN/CLASS/PROPERTY/OBJECT englobant le plus profond à une
     * position — la cible légale de `@Suppress`.
     */
    private fun declarationSupprimable(racine: NoeudArbre, position: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (position < noeud.debut || position > noeud.fin) return
            if (noeud.genre == KtNodeTypes.FUN ||
                noeud.genre == KtNodeTypes.CLASS ||
                noeud.genre == KtNodeTypes.PROPERTY ||
                noeud.genre == KtNodeTypes.OBJECT_DECLARATION
            ) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /**
     * La chaîne des DÉCLARATIONS englobantes porte-t-elle DÉJÀ un
     * `@Suppress` de ce code (chantier G) ? La suppression d'un PARENT
     * couvre toute sa région — une annotation fille serait redondante.
     */
    private fun suppressionDejaPresente(
        racine: NoeudArbre,
        declaration: NoeudArbre,
        code: String,
    ): Boolean {
        // La chaîne racine → déclaration (les englobants de la cible).
        val chaine = ArrayList<NoeudArbre>()
        fun marcher(noeud: NoeudArbre): Boolean {
            if (declaration.debut < noeud.debut || declaration.debut > noeud.fin) return false
            // Le nœud CONTIENT la position : il reste dans la chaîne (le
            // premier enfant contenante prime, la descente s'arrête).
            chaine.add(noeud)
            for (enfant in noeud.enfants) {
                if (marcher(enfant)) return true
            }
            return true
        }
        marcher(racine)
        // Chaque déclaration de la chaîne porte-t-elle l'annotation ?
        return chaine.any { noeud ->
            val modificateurs = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.MODIFIER_LIST }
                ?: return@any false
            modificateurs.enfants.any { annotation ->
                annotation.genre == KtNodeTypes.ANNOTATION_ENTRY &&
                    annotation.texteCanonique.contains("\"$code\"")
            }
        }
    }

    // ------------------------------------------------------------------
    // CHANTIER H — refactor.extract : l'extraction de FONCTION.
    // ------------------------------------------------------------------

    /**
     * Le plan d'EXTRACTION DE FONCTION (chantier H — parité Android
     * Studio « Extract Function », complément de l'extraction de
     * variable KC-11) : l'expression englobante du caret devient le
     * CORPS d'une nouvelle fonction privée INSÉRÉE après la fonction
     * porteuse, l'expression est REMPLACÉE par l'APPEL.
     *
     * Les bornes conservatrices (D3 — jamais de plan sur doute) :
     *  - l'expression vit dans UNE instruction du corps d'une FUN
     *    (intra-fonction, miroir KC-11 Q5) ;
     *  - l'expression est SANS CAPTURE : aucune REFERENCE_EXPRESSION
     *    ne vise un nom LOCAL de la fonction porteuse (paramètres,
     *    locales) — une capture exigerait des PARAMÈTRES, un chantier
     *    de typage complet. Les références à des noms de PREMIER
     *    NIVEAU (déclarations du fichier) sont légales : elles restent
     *    résolues depuis la nouvelle fonction ;
     *  - le nom LIBRE : extraitFonction, extraitFonction2, … ;
     *  - l'expression n'est pas L'INSTRUCTION entière (le corps de la
     *    nouvelle fonction serait l'instruction — sans point
     *    d'insertion lisible).
     *
     * @param arbre l'arbre du document ;
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret (dans l'expression) ;
     * @return le plan ; `null` si une borne refuse.
     */
    internal fun extraireFonction(
        arbre: KtFichier,
        uri: String,
        deplacement: Int,
    ): PlanActionKotlin? {
        // La FUN porteuse et l'instruction du caret (intra-fonction).
        val parents = parentDe(arbre.noeud, deplacement)
        val fonction = parents.firstOrNull { it.genre == KtNodeTypes.FUN } ?: return null
        val bloc = parents.firstOrNull { it.genre == KtNodeTypes.BLOCK } ?: return null
        // L'instruction porteuse : l'ENFANT DIRECT du bloc qui contient
        // la position (la liste va du plus profond à la racine — on
        // cherche le parent IMMÉDIATEMENT sous le bloc, pas une
        // sous-expression).
        val instr = parents.lastOrNull { parent ->
            parent !== bloc && bloc.enfants.any { enfant -> enfant === parent }
        } ?: return null

        // L'expression : le parent EXPR le plus PROFOND qui contient le
        // caret (la liste va du plus profond à la racine — le premier
        // CALL/BINARY/DOT trouvé est l'expression SOUS le caret, miroir
        // IntelliJ : l'ampoule extrait ce que le caret désigne).
        val expr = parents.firstOrNull {
            (it.genre == KtNodeTypes.CALL_EXPRESSION ||
                it.genre == KtNodeTypes.BINARY_EXPRESSION ||
                it.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION) &&
                it.debut >= instr.debut && it.fin <= instr.fin
        } ?: return null
        if (expr.debut == instr.debut && expr.fin == instr.fin) return null

        // La preuve de NON-CAPTURE : aucune référence de l'expression
        // ne vise un LOCAL de la porteuse (paramètre ou locale).
        val locaux = nomsLocauxDe(fonction)
        if (expr.contientReference(locaux)) return null

        // Le nom LIBRE (premier absent du document).
        var rang = 0
        var nom = "extraitFonction"
        while (contientMot(arbre.source, nom)) {
            rang++
            nom = "extraitFonction$rang"
        }

        // L'indentation de la fonction porteuse (la nouvelle vit au
        // MÊME niveau — après sa fermeture).
        val source = arbre.source
        val debutLigne = source.lastIndexOf('\n', fonction.debut).let { if (it < 0) 0 else it + 1 }
        val indent = source.substring(debutLigne, fonction.debut).takeWhile { it == ' ' || it == '\t' }
        val texteExpression = source.substring(expr.debut, expr.fin)

        // Les éditions : le REMPLACEMENT de l'expression par l'appel
        // (l'instruction vit AVANT l'insertion de la fonction), puis
        // l'INSERTION de la fonction après la fermeture de la porteuse
        // — en ordre du document, plages disjointes.
        return PlanActionKotlin(
            titre = "Extraire « $texteExpression » dans une fonction",
            genre = GenreActionKotlin.EXTRAIRE_FONCTION,
            editions = listOf(
                EditionFichierKotlin(
                    uri = uri,
                    editions = listOf(
                        EditionTexteKotlin(
                            plage = PlageTexte(expr.debut, expr.fin),
                            texte = "$nom()",
                        ),
                        EditionTexteKotlin(
                            plage = PlageTexte(fonction.fin, fonction.fin),
                            texte = "\n\n$indent/** Le corps extrait de l'expression sélectionnée. */\n" +
                                "${indent}private fun $nom() = $texteExpression",
                        ),
                    ),
                ),
            ),
        )
    }

    /**
     * Les NOMS LOCAUX d'une fonction (chantier H) : les paramètres et
     * les locales — les noms qu'une fonction extraite ne peut PAS
     * capturer sans paramètre.
     */
    private fun nomsLocauxDe(fonction: NoeudArbre): Set<String> {
        val noms = mutableSetOf<String>()
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.VALUE_PARAMETER ||
                noeud.genre == KtNodeTypes.PROPERTY
            ) {
                noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
                    ?.let { noms.add(it.texte) }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(fonction)
        return noms
    }

    /**
     * Le sous-arbre CONTIENT-IL une référence à l'un de ces noms
     * (chantier H — la preuve de non-capture) ?
     */
    private fun NoeudArbre.contientReference(noms: Set<String>): Boolean {
        if (noms.isEmpty()) return false
        if (genre == KtNodeTypes.REFERENCE_EXPRESSION && texteCanonique in noms) return true
        return enfants.any { it.contientReference(noms) }
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

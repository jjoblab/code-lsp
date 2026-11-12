// CodeIDE — capacités ARBRE d'un document (module :kotlin, vague V-I1 —
// ADR 0136 étape B.2 : linkedEditingRange, documentColor, onTypeFormatting
// — les trois familles décidables sur l'arbre/le texte SEUL, avant le
// palier 2). Code NEUF jjoblab (clean-room — bornologie : la paire de
// délimiteurs vit dans les JETONS de l'arbre ; la couleur est un LITTÉRAL
// hexadécimal ; la ré-indentation est LOCALE à la ligne — miroir borné du
// formateur ADR 0115, jamais une re-formatisation complète).
package jo.codeide.lsp.kotlin.session

import com.intellij.platform.syntax.SyntaxElementType
import jo.codeide.lsp.kotlin.api.CouleurDocumentKotlin
import jo.codeide.lsp.kotlin.api.EditionTexteKotlin
import jo.codeide.lsp.kotlin.api.PlagesLieesKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import org.jetbrains.kotlin.kmp.lexer.KtTokens

/**
 * Les capacités ARBRE (V-I1) : trois familles HONNÊTES décidables sans la
 * résolution —
 *
 * - [plagesLiees] : la paire de DÉLIMITEURS appariés couvrant le point
 *   (`()`/`{}`/`[]` par empilement lexical, `<>` UNIQUEMENT dans les
 *   listes d'arguments de TYPE — jamais les comparaisons) ;
 * - [couleurs] : les LITTÉRAUX hexadécimaux Android du document
 *   (`0xAARRGGBB`, `0xRRGGBB`) avec canaux normalisés ;
 * - [formatageFrappé] : la RÉ-INDENTATION d'une ligne après frappe
 *   (retour à la ligne : l'indent de la ligne précédente + l'ouverture
 *   nette de ses délimiteurs ; accolade fermante : l'indent de la ligne
 *   de l'OUVRANT apparié) — miroir borné du formateur, jamais une
 *   re-formatisation du document.
 */
internal object CapacitesArbre {

    // ── linkedEditingRange ──────────────────────────────────────────────

    /**
     * Les plages LIÉES au point : la paire de délimiteurs appariés dont
     * l'un couvre le caret (offset sur le délimiteur).
     *
     * Les paires `()`/`{}`/`[]` s'apparient par EMPILEMENT lexical (un
     * délimiteur ouvrant attend son fermant de MÊME genre — un fermant
     * orphelin est ignoré, jamais une paire inventée). Les paires `<>` ne
     * vivent QUE dans les listes d'arguments de TYPE (`List<Int>`) : les
     * chevrons de COMPARAISON ne s'apparient jamais.
     *
     * @param arbre le document porteur ;
     * @param deplacement l'offset du caret ;
     * @return la paire couvrante ; `null` si le caret n'est pas sur un
     * délimiteur apparié (jamais d'exception).
     */
    internal fun plagesLiees(arbre: KtFichier, deplacement: Int): PlagesLieesKotlin? {
        val paires = pairesDeDelimiteurs(arbre)
        for ((ouvrante, fermante) in paires) {
            if (deplacement >= ouvrante.debut && deplacement <= ouvrante.fin) {
                return PlagesLieesKotlin(plageOuvrante = ouvrante, plageFermante = fermante)
            }
            if (deplacement >= fermante.debut && deplacement <= fermante.fin) {
                return PlagesLieesKotlin(plageOuvrante = ouvrante, plageFermante = fermante)
            }
        }
        return null
    }

    /**
     * Les PAIRES de délimiteurs du document, en ordre d'ouverture :
     * l'empilement lexical pour `()`/`{}`/`[]`, les bornes `<`…`>` des
     * listes d'arguments de TYPE pour les chevrons.
     */
    private fun pairesDeDelimiteurs(arbre: KtFichier): List<Pair<PlageTexte, PlageTexte>> {
        val jetons = jetonsTries(arbre)
        val paires = mutableListOf<Pair<PlageTexte, PlageTexte>>()

        // 1. L'empilement lexical des délimiteurs `()`, `{}`, `[]` (les
        // genres de jetons sont en IDENTITÉ d'objet avec KtTokens).
        val ouvrants = ArrayDeque<Pair<SyntaxElementType, PlageTexte>>()
        for (jeton in jetons) {
            when (jeton.genre) {
                KtTokens.LPAR -> ouvrants.addLast(Pair(KtTokens.RPAR, jeton.plage))
                KtTokens.LBRACE -> ouvrants.addLast(Pair(KtTokens.RBRACE, jeton.plage))
                KtTokens.LBRACKET -> ouvrants.addLast(Pair(KtTokens.RBRACKET, jeton.plage))
                KtTokens.RPAR, KtTokens.RBRACE, KtTokens.RBRACKET -> {
                    // Le DERNIER ouvrant du même genre ferme (LIFO) — les
                    // ouvrants d'un AUTRE genre attendent leur tour ; un
                    // fermant orphelin est ignoré (document hostile :
                    // jamais une paire inventée).
                    val attendu = jeton.genre
                    var index = -1
                    for (i in ouvrants.indices.reversed()) {
                        if (ouvrants[i].first === attendu) {
                            index = i
                            break
                        }
                    }
                    if (index >= 0) {
                        val ouvrante = ouvrants.removeAt(index)
                        paires.add(Pair(ouvrante.second, jeton.plage))
                    }
                }
                else -> Unit
            }
        }

        // 2. Les chevrons des listes d'arguments de TYPE (`List<Int>`) :
        // les bornes `<` et `>` de la liste — jamais les comparaisons.
        marcher(arbre.noeud) { noeud ->
            if (noeud.genre == KtNodeTypes.TYPE_ARGUMENT_LIST) {
                val chevrons = noeud.jetonsPropres.filter {
                    it.genre === KtTokens.LT || it.genre === KtTokens.GT
                }
                if (chevrons.isNotEmpty()) {
                    val premier = chevrons.first()
                    val dernier = chevrons.last()
                    if (premier.genre === KtTokens.LT && dernier.genre === KtTokens.GT) {
                        paires.add(
                            Pair(
                                PlageTexte(premier.debut, premier.fin),
                                PlageTexte(dernier.debut, dernier.fin),
                            ),
                        )
                    }
                }
            }
        }
        return paires.sortedBy { it.first.debut }
    }

    // ── documentColor ───────────────────────────────────────────────────

    /**
     * Les COULEURS littérales du document : les entiers hexadécimaux
     * Android à 6 chiffres (`0x1A73E8` — opaque) ou 8 chiffres
     * (`0xFF1A73E8` — alpha en TÊTE, convention Android) ; les autres
     * littéraux ne sont pas des couleurs (jamais une couleur inventée).
     *
     * @param arbre le document porteur ;
     * @return les couleurs en ordre du document ; liste vide sans littéral
     * hexadécimal de la bonne largeur.
     */
    internal fun couleurs(arbre: KtFichier): List<CouleurDocumentKotlin> {
        val couleurs = mutableListOf<CouleurDocumentKotlin>()
        for (jeton in jetonsTries(arbre)) {
            if (jeton.genre !== KtTokens.INTEGER_LITERAL) continue
            val hexa = jeton.texte
                .removeSuffix("u").removeSuffix("U")
                .removePrefix("0x").removePrefix("0X")
            if (hexa.length != 6 && hexa.length != 8) continue
            if (hexa.any { Character.digit(it, 16) < 0 }) continue
            val canaux = if (hexa.length == 8) {
                listOf(
                    hexa.substring(0, 2), hexa.substring(2, 4),
                    hexa.substring(4, 6), hexa.substring(6, 8),
                )
            } else {
                listOf("ff", hexa.substring(0, 2), hexa.substring(2, 4), hexa.substring(4, 6))
            }
            couleurs.add(
                CouleurDocumentKotlin(
                    plage = jeton.plage,
                    alpha = canaux[0].toInt(16) / 255.0,
                    rouge = canaux[1].toInt(16) / 255.0,
                    vert = canaux[2].toInt(16) / 255.0,
                    bleu = canaux[3].toInt(16) / 255.0,
                ),
            )
        }
        return couleurs
    }

    // ── onTypeFormatting ────────────────────────────────────────────────

    /**
     * La RÉ-INDENTATION d'une ligne après frappe (miroir borné du
     * formateur ADR 0115 — l'édition ne touche QUE le blanc de tête de la
     * ligne concernée, jamais une re-formatisation du document) :
     *
     * - frappe `\\n` : la NOUVELLE ligne reçoit l'indent de la ligne
     *   précédente + une indent par OUVERTURE NETTE de cette ligne
     *   (ouvrants − fermants) ;
     * - frappe `}` : la ligne de l'accolade reçoit l'indent de la ligne
     *   de son OUVRANT apparié (recherche en arrière par empilement).
     *
     * @param texte le texte du document ;
     * @param deplacement l'offset du caret APRÈS la frappe ;
     * @param caractere le caractère frappé (`\\n` ou `}`) ;
     * @param tailleIndentation la taille d'un niveau d'indentation ;
     * @return ZÉRO ou UNE édition (le blanc de tête de la ligne) — liste
     * vide quand la forme ne se décide pas (jamais une exception).
     */
    internal fun formatageFrappé(
        texte: CharSequence,
        deplacement: Int,
        caractere: Char,
        tailleIndentation: Int,
    ): List<EditionTexteKotlin> {
        if (tailleIndentation <= 0) return emptyList()
        return when (caractere) {
            '\n' -> indentApresRetourLigne(texte, deplacement, tailleIndentation)
            '}' -> indentAccoladeFermante(texte, deplacement, tailleIndentation)
            else -> emptyList()
        }
    }

    /** L'indent de la NOUVELLE ligne (la frappe `\\n` vient d'avoir lieu). */
    private fun indentApresRetourLigne(
        texte: CharSequence,
        deplacement: Int,
        tailleIndentation: Int,
    ): List<EditionTexteKotlin> {
        val lignes = bornesDeLigne(texte, deplacement) ?: return emptyList()
        val precedente = lignes.precedente ?: return emptyList()
        val contenuPrecedente = texte.substring(precedente.debut, precedente.fin)
        val base = longueurBlancDeTete(contenuPrecedente)
        val ouvertureNette = contenuPrecedente.count { it == '{' || it == '(' || it == '[' } -
            contenuPrecedente.count { it == '}' || it == ')' || it == ']' }
        if (ouvertureNette <= 0) return emptyList()
        val cible = base + ouvertureNette * tailleIndentation
        return editionDeBlancDeTete(texte, lignes.courante, cible)
    }

    /** L'indent de la ligne de l'accolade FERMANTE frappée. */
    private fun indentAccoladeFermante(
        texte: CharSequence,
        deplacement: Int,
        tailleIndentation: Int,
    ): List<EditionTexteKotlin> {
        val lignes = bornesDeLigne(texte, deplacement) ?: return emptyList()
        val courante = texte.substring(lignes.courante.debut, lignes.courante.fin)
        // La ligne doit COMMENCER (après blanc) par l'accolade fermante
        // frappée — une accolade en fin d'expression reste à sa place.
        val blanc = longueurBlancDeTete(courante)
        if (blanc >= courante.length || courante[blanc] != '}') return emptyList()

        // L'OUVRANT apparié : empilement en ARRIÈRE depuis l'accolade.
        var profondeur = 0
        var ouvrant: Int = -1
        var i = lignes.courante.debut + blanc - 1
        while (i >= 0) {
            when (texte[i]) {
                '}' -> profondeur++
                '{' -> {
                    if (profondeur == 0) {
                        ouvrant = i
                        break
                    }
                    profondeur--
                }
            }
            i--
        }
        if (ouvrant < 0) return emptyList()
        val ligneOuvrante = bornesDeLigne(texte, ouvrant) ?: return emptyList()
        val cible = longueurBlancDeTete(
            texte.substring(ligneOuvrante.courante.debut, ligneOuvrante.courante.fin),
        )
        return editionDeBlancDeTete(texte, lignes.courante, cible)
    }

    /** L'édition du BLANC DE TÊTE de la ligne vers la longueur cible. */
    private fun editionDeBlancDeTete(
        texte: CharSequence,
        ligne: PlageTexte,
        cible: Int,
    ): List<EditionTexteKotlin> {
        val contenu = texte.substring(ligne.debut, ligne.fin)
        val actuel = longueurBlancDeTete(contenu)
        if (actuel == cible) return emptyList()
        return listOf(
            EditionTexteKotlin(
                plage = PlageTexte(ligne.debut, ligne.debut + actuel),
                texte = " ".repeat(cible),
            ),
        )
    }

    /** La longueur du BLANC DE TÊTE d'une ligne (espaces et tabulations). */
    private fun longueurBlancDeTete(ligne: String): Int {
        var i = 0
        while (i < ligne.length && (ligne[i] == ' ' || ligne[i] == '\t')) i++
        return i
    }

    /** Les bornes (précédente, courante) de la ligne couvrant l'offset. */
    private fun bornesDeLigne(texte: CharSequence, deplacement: Int): BornesLignes? {
        if (deplacement < 0 || texte.isEmpty()) return null
        if (deplacement > texte.length) return null
        var debut = texte.lastIndexOf('\n', (deplacement - 1).coerceAtLeast(0))
        debut = if (debut < 0) 0 else debut + 1
        var fin = texte.indexOf('\n', debut)
        if (fin < 0) fin = texte.length
        // Après une frappe \n, le caret est sur la NOUVELLE ligne (souvent
        // vide) : la ligne courante est celle qui SUIT le dernier \n.
        if (deplacement > fin && fin < texte.length) {
            debut = fin + 1
            fin = texte.indexOf('\n', debut)
            if (fin < 0) fin = texte.length
        }
        val precedenteFin = debut - 1
        val precedente = if (precedenteFin >= 0) {
            val precedenteDebut = texte.lastIndexOf('\n', precedenteFin - 1).let {
                if (it < 0) 0 else it + 1
            }
            if (precedenteFin >= precedenteDebut) PlageTexte(precedenteDebut, precedenteFin) else null
        } else {
            null
        }
        return BornesLignes(precedente, PlageTexte(debut, fin))
    }

    /** Les bornes (précédente, courante) d'une ligne du document. */
    private class BornesLignes(internal val precedente: PlageTexte?, internal val courante: PlageTexte)

    // ── Jetons ──────────────────────────────────────────────────────────

    /** Tous les jetons SIGNIFICATIFS du document, TRIÉS par position. */
    private fun jetonsTries(arbre: KtFichier): List<JetonPositionne> {
        val jetons = mutableListOf<JetonPositionne>()
        marcher(arbre.noeud) { noeud ->
            for (jeton in noeud.jetonsPropres) {
                jetons.add(JetonPositionne(jeton.genre, PlageTexte(jeton.debut, jeton.fin), jeton.texte))
            }
        }
        return jetons.sortedBy { it.plage.debut }
    }

    /** La marche de tout l'arbre (préfixe — l'ordre du document suit le tri). */
    private fun marcher(noeud: NoeudArbre, visiter: (NoeudArbre) -> Unit) {
        visiter(noeud)
        noeud.enfants.forEach { marcher(it, visiter) }
    }

    /** Un jeton positionné (genre + plage + texte). */
    private class JetonPositionne(
        internal val genre: SyntaxElementType,
        internal val plage: PlageTexte,
        internal val texte: String,
    )
}

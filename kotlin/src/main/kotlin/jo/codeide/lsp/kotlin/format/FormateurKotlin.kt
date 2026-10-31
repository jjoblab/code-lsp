// CodeIDE — formateur Kotlin borné (module :kotlin, zone format —
// REPRISE VOIE C, manifeste docs/PROVENANCE-REPRISE-KOTLIN.md §3 R6).
// Origine : codeide-lsp
// lsp/kotlin/src/main/kotlin/jo/lspkotlin/format/FormateurKotlin.kt
// @ bc11253 (auteur jjoblab, dépôt propriétaire) — contrôle R9.4 PASSÉ
// (en-têtes sans mention tierce ; re-contrôle au moment de la copie,
// vague 5a). Adaptations de câblage SEULEMENT, listées ici : paquet
// jo.codeide.lsp.kotlin.format ; l'arbre paramètre est la façade neuve
// `parse.kt.KtFichier` (le texte s'y lit sur [KtFichier.source]) ; le
// REFUS sur arbre en erreur compte les marqueurs par la marche du
// modèle neuf (`messageErreur` par nœud) ; la suite de jetons BLANCS
// COMPRIS se lexifie depuis le texte (le modèle neuf ne porte que les
// jetons significatifs — le curseur d'analyse exige les blancs fusionnés
// « \n    » du lexeur, ils se re-lexifient ici à l'identique) ; l'édition
// rendue est le SPI neuf `api.EditionTexteKotlin` (plage + texte). Le
// cœur (profondeur structurelle, intouchables, continuation de liste,
// idempotence) est repris TEL QUEL.
package jo.codeide.lsp.kotlin.format

import com.intellij.platform.syntax.lexer.performLexing
import jo.codeide.lsp.kotlin.api.EditionTexteKotlin
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import org.jetbrains.kotlin.kmp.lexer.KotlinLexer
import org.jetbrains.kotlin.kmp.lexer.KtTokens

/**
 * FORMATEUR Kotlin borné (L2 — plan 1.3.0, ADR 0202) : RÉ-INDENTATION
 * canonique seule, miroir du formateur XML S3.
 *
 * ## Le choix borné
 * Le contenu des lignes n'est JAMAIS réécrit : seule la PLENTE de tête
 * d'une ligne est remplacée, par la profondeur STRUCTURELLE calculée
 * depuis les jetons de l'arbre (le lexer a déjà résolu ce que le texte
 * brut ne prouve pas : une accolade dans une chaîne ou un commentaire
 * n'est PAS un jeton LBRACE, une chaîne brute multi-lignes reste entre
 * ses jetons délimiteurs).
 *
 * La profondeur d'une ligne :
 *  - base = nombre d'accolades OUVERTES terminées avant la ligne (une
 *    `{` en fin de ligne précédente indente les lignes qui suivent) ;
 *  - MOINS les accolades fermantes en TÊTE de ligne (`}`, `} else {`,
 *    `} }` ferment AVANT d'occuper leur colonne — le fermant vit à la
 *    profondeur de son bloc) ;
 *  - PLUS UN si la ligne est une CONTINUATION de liste non fermée
 *    (paramètres/arguments/indices multi-lignes : la `(`/`[` ouvrante
 *    est restée ouverte — convention canonique, les éléments vivent un
 *    niveau sous l'ouvrante) ; une ligne qui FERME la liste (`)` ou
 *    `]` en tête) revient à la base.
 *
 * ## Intouchables (honnêteté D3)
 *  - document portant des MARQUEURS D'ERREUR du parseur (édition en
 *    cours, fragment non reconnu) ⇒ liste VIDE — la profondeur d'un
 *    arbre incomplet serait une invention ;
 *  - ligne DANS un jeton multi-ligne (chaîne brute entre ses jetons de
 *    contenu, bloc de commentaire, KDoc) ⇒ jamais d'édition — le blanc
 *    de tête de ces lignes est du CONTENU ;
 *  - ligne de continuation D'EXPRESSION en tête (`.` chaîné, `?:`,
 *    opérateur binaire, `->`) hors liste ouverte ⇒ jamais d'édition —
 *    l'indentation de continuation libre est un choix de style, pas
 *    une structure prouvable ;
 *  - ligne VIDE ⇒ jamais d'édition ;
 *  - ligne déjà juste ⇒ aucune édition (idempotence : reformater un
 *    document formaté ne change rien).
 */
internal object FormateurKotlin {
    /**
     * Éditions de ré-indentation de [arbre] (texte [KtFichier.source],
     * [tailleIndentation] espaces par niveau), restreintes aux lignes
     * qui INTERSECTENT la plage [debutPlage, finPlage) — ordonnées,
     * jamais superposées ; liste VIDE si refus ou rien à changer.
     */
    internal fun editer(
        arbre: KtFichier,
        tailleIndentation: Int,
        debutPlage: Int = 0,
        finPlage: Int = arbre.source.length,
    ): List<EditionTexteKotlin> {
        val texte = arbre.source
        // Refus honnête : marqueur d'erreur dans l'arbre (la structure
        // n'est pas prouvée, la profondeur serait une invention) ou
        // plage vide — rien à servir.
        if (nombreErreurs(arbre.noeud) > 0 || debutPlage >= finPlage) return emptyList()

        val jetons = jetonsLexes(texte)
        val editions = mutableListOf<EditionTexteKotlin>()
        val analyse = Analyse(jetons)

        var debutLigne = 0
        val longueur = texte.length
        while (debutLigne < longueur) {
            val finLigne =
                texte.indexOf('\n', debutLigne).let { if (it < 0) longueur else it }
            // La ligne intersecte la plage demandée ? (formatage de
            // plage : le client sélectionne par lignes, une ligne à
            // cheval sur une borne est candidate.)
            if (debutLigne < finPlage && finLigne > debutPlage) {
                val cible = analyse.profondeurDe(debutLigne, finLigne)
                if (cible != null) {
                    ajouterEditionSiDecalee(texte, debutLigne, cible, tailleIndentation, editions)
                }
            }
            analyse.avancerJusqua(finLigne + 1)
            debutLigne = finLigne + 1
        }
        return editions
    }

    /**
     * Le nombre de MARQUEURS D'ERREUR de l'arbre (adaptation de câblage :
     * le modèle neuf porte le message d'erreur par nœud — la marche les
     * compte, miroir du `nombreErreurs` de l'ancien arbre).
     */
    private fun nombreErreurs(noeud: NoeudArbre): Int {
        val propre = if (noeud.messageErreur != null) 1 else 0
        return propre + noeud.enfants.sumOf(::nombreErreurs)
    }

    /**
     * La suite de jetons du document BLANCS COMPRIS (adaptation de
     * câblage : le modèle neuf ne retient que les jetons significatifs
     * — le curseur d'analyse vit des blancs fusionnés « \n    » du
     * lexeur, on les re-lexifie ici, positions identiques).
     */
    private fun jetonsLexes(texte: CharSequence): List<JetonBrut> {
        val lexe = performLexing(texte, KotlinLexer(), cancellationProvider = null, logger = null)
        return (0 until lexe.tokenCount).map { indice ->
            JetonBrut(
                genre = checkNotNull(lexe.getTokenType(indice)),
                texte = checkNotNull(lexe.getTokenText(indice)).toString(),
                debut = lexe.getTokenStart(indice),
                fin = lexe.getTokenEnd(indice),
            )
        }
    }

    /** Édition du SEUL blanc de tête, si la ligne est décalée. */
    private fun ajouterEditionSiDecalee(
        texte: CharSequence,
        debutLigne: Int,
        niveau: Int,
        tailleIndentation: Int,
        editions: MutableList<EditionTexteKotlin>,
    ) {
        var premier = debutLigne
        while (premier < texte.length && texte[premier].isWhitespace() && texte[premier] != '\n') {
            premier += 1
        }
        val cible = " ".repeat(niveau.coerceAtLeast(0) * tailleIndentation)
        // Déjà juste : aucune édition (une édition à l'identique serait
        // du bruit pour l'éditeur — un TextEdit sans effet).
        if (texte.substring(debutLigne, premier) == cible) return
        editions.add(EditionTexteKotlin(PlageTexte(debutLigne, premier), cible))
    }

    /**
     * Balayage des jetons structuraux — un CURSEUR unique avance au fil
     * des lignes : INVARIANT à l'entrée de chaque ligne, les jetons
     * d'index &lt; [index] sont TERMINÉS avant le début de la ligne (leur
     * effet est déjà appliqué), le jeton [index] le cas échéant la
     * TRAVERSE (jeton multi-ligne — chaîne brute, bloc de commentaire,
     * KDoc).
     */
    private class Analyse(
        jetons: List<JetonBrut>,
    ) {
        /** Index du prochain jeton à consommer. */
        private var index = 0

        /** Tous les jetons, en ordre du document. */
        private val jetons: List<JetonBrut> = jetons

        /** Accolades ouvertes par les jetons consommés. */
        private var accoladesOuvertes = 0

        /** Parenthèses ouvertes par les jetons consommés (continuation). */
        private var parenthesesOuvertes = 0

        /** Crochets ouverts par les jetons consommés (continuation). */
        private var crochetsOuverts = 0

        /** Jetons blancs (fin de ligne comprise). */
        private val blancs =
            setOf(KtTokens.WHITE_SPACE, KtTokens.EOL_OR_SEMICOLON, KtTokens.SEMICOLON)

        /** Un jeton est-il un BLANC (fusion « \n    » comprise) ? */
        private fun estBlanc(jeton: JetonBrut): Boolean = jeton.genre in blancs

        /**
         * Jetons portant du CONTENU de chaîne BRUTE : le lexer découpe
         * la chaîne ligne par ligne (partie de chaîne par ligne,
         * délimiteurs ouvrant/fermant, entrées de template) — le blanc
         * de tête d'une ligne qui les porte en PREMIER jeton est DANS
         * la chaîne (du contenu), jamais du code.
         */
        private val jetonsChaine =
            setOf(
                KtTokens.OPEN_QUOTE, KtTokens.CLOSING_QUOTE,
                KtTokens.REGULAR_STRING_PART, KtTokens.ESCAPE_SEQUENCE,
            )

        /** Jetons de tête d'une continuation d'EXPRESSION libre. */
        private val continuationsLibres =
            setOf(
                KtTokens.DOT, KtTokens.SAFE_ACCESS, KtTokens.ARROW, KtTokens.ANDAND,
                KtTokens.OROR, KtTokens.AND, KtTokens.ELVIS, KtTokens.PLUS, KtTokens.MINUS,
                KtTokens.MUL, KtTokens.DIV, KtTokens.PERC, KtTokens.LT, KtTokens.GT,
                KtTokens.LTEQ, KtTokens.GTEQ, KtTokens.EQ, KtTokens.EQEQ, KtTokens.EXCLEQ,
                KtTokens.EXCLEXCL, KtTokens.EXCL, KtTokens.RANGE, KtTokens.RANGE_UNTIL,
                KtTokens.COLON, KtTokens.COLONCOLON, KtTokens.COMMA,
            )

        /**
         * Consomme les jetons terminés STRICTEMENT avant [position] —
         * l'état d'une ligne qui commence à [position].
         */
        fun avancerJusqua(position: Int) {
            while (index < jetons.size && jetons[index].fin <= position) {
                when (jetons[index].genre) {
                    KtTokens.LBRACE -> accoladesOuvertes += 1
                    KtTokens.RBRACE -> accoladesOuvertes -= 1
                    KtTokens.LPAR -> parenthesesOuvertes += 1
                    KtTokens.RPAR -> parenthesesOuvertes -= 1
                    KtTokens.LBRACKET -> crochetsOuverts += 1
                    KtTokens.RBRACKET -> crochetsOuverts -= 1
                    else -> {}
                }
                index += 1
            }
        }

        /**
         * Tête de ligne analysée : premiers jetons SIGNIFICATIFS —
         * fermantes d'accolades, fermeture de liste, premier jeton.
         */
        private data class TeteLigne(
            val fermantes: Int,
            val fermeListe: Boolean,
            val premier: JetonBrut?,
        )

        /**
         * Tête significative de la ligne [debutLigne, finLigne) : les
         * `}` ouvrant la ligne et la fermeture de liste `)`/`]` en
         * tête, avec le premier jeton significatif.
         */
        private fun teteSignificative(
            finLigne: Int,
        ): TeteLigne {
            var fermantes = 0
            var fermeListe = false
            var enTete = true
            var premier: JetonBrut? = null
            var position = index
            // Les significatifs de la ligne commencent au plus tôt au
            // PREMIER caractère de la ligne (un jeton qui la précède
            // et la chevauche est un BLANC fusionné, sans contenu).
            while (position < jetons.size && jetons[position].debut < finLigne) {
                val jeton = jetons[position]
                if (estBlanc(jeton)) {
                    position += 1
                    continue
                }
                if (premier == null) premier = jeton
                if (enTete) {
                    if (jeton.genre == KtTokens.RBRACE) {
                        fermantes += 1
                    } else {
                        enTete = false
                        fermeListe = jeton.genre == KtTokens.RPAR || jeton.genre == KtTokens.RBRACKET
                    }
                }
                position += 1
            }
            return TeteLigne(fermantes, fermeListe, premier)
        }

        /**
         * Profondeur cible de la ligne [debutLigne, finLigne) — `null`
         * pour une ligne intouchable (vide, dans un jeton multi-ligne,
         * continuation d'expression libre).
         *
         * Exemption detekt ciblée (règle 16) : ReturnCount — chaque
         * INTOUCHABLE est une ISSUE distincte (le contrat même du
         * formateur borné : ce qui n'est pas prouvé ne bouge pas).
         */
        @Suppress("ReturnCount")
        fun profondeurDe(
            debutLigne: Int,
            finLigne: Int,
        ): Int? {
            // Ligne vide : le vide ne bouge pas.
            if (debutLigne >= finLigne) return null
            // Ligne DANS un jeton multi-ligne NON BLANC (chaîne brute,
            // bloc de commentaire, KDoc) : par l'INVARIANT du curseur,
            // le premier jeton non consommé commence AVANT la ligne et
            // ne s'y termine pas — le blanc de tête est du CONTENU.
            // Un BLANC qui chevauche le début de ligne est la FUSION
            // « \n    » du lexer (le retour à la ligne et l'indentation
            // forment UN jeton) : l'indentation de tête n'est PAS du
            // contenu — la ligne reste candidate.
            if (index < jetons.size && jetons[index].debut < debutLigne && !estBlanc(jetons[index])) {
                return null
            }
            // La ligne COMMENCE par du contenu de chaîne brute (le
            // blanc de tête vit DANS la chaîne — le lexer la découpe
            // ligne par ligne) : le blanc est du CONTENU.
            if (index < jetons.size &&
                jetons[index].debut == debutLigne &&
                jetons[index].genre in jetonsChaine
            ) {
                return null
            }

            val tete = teteSignificative(finLigne)

            // Continuation LIBRE (hors liste ouverte) : opérateur, appel
            // chaîné ou flèche en tête — un choix de style, pas une
            // structure prouvable (le formateur borné s'abstient).
            val listeOuverte = parenthesesOuvertes > 0 || crochetsOuverts > 0
            if (!listeOuverte && tete.premier != null && tete.premier.genre in continuationsLibres) {
                return null
            }

            val base = accoladesOuvertes - tete.fermantes
            return if (listeOuverte) {
                // Continuation de liste : les éléments vivent UN niveau
                // sous l'ouvrante ; la ligne qui FERME la liste revient
                // à la base de l'ouvrante.
                if (tete.fermeListe) base else base + 1
            } else {
                base
            }
        }
    }
}

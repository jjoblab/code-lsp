// CodeIDE — diagnostics d'imports et de syntaxe d'un document (module
// :kotlin, étage 1 vague 1b). Code NEUF jjoblab (clean-room — contrat :
// fiche 049 du cahier + LSP_KOTLIN.md §5 K2 : `kt.syntax` marqueurs du
// parseur message amont repris, `kt.unusedImport` nom effectif absent des
// identifiants du fichier — joker et directives en édition jamais signalés,
// `kt.conflictingImport` même nom effectif chemins distincts — signalé sur
// le plus tardif ; tags/informations liées KP15).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.DiagnosticKotlin
import jo.codeide.lsp.kotlin.api.InformationLieeKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.api.TagDiagnosticKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtDirectiveImport
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Calcul des diagnostics d'un document ouvert (pur — aucun état, appelé sous
 * verrou de lecture) : erreurs de syntaxe, imports inutilisés et imports
 * conflictuels, en ordre du document.
 *
 * ## Règles (fiche 049)
 *
 * - **`kt.syntax`** : chaque nœud `ERROR_ELEMENT` de l'arbre (les DEUX formes
 *   de la grammaire : appariée et mono-entrée) rend un diagnostic portant la
 *   plage et le MESSAGE AMONT du parseur — une plage vide reste fidèle au
 *   marqueur (l'élargissement est un soin d'hôte).
 * - **`kt.unusedImport`** : le NOM EFFECTIF de la directive (l'alias s'il est
 *   écrit, sinon le dernier segment du chemin) est cherché parmi les
 *   IDENTIFIANTS du fichier HORS directives — un nom présent en COMMENTAIRE
 *   ou en CHAÎNE ne compte pas (les identifiants sont des jetons de code).
 *   Le JOKER n'est jamais signalé ; une directive EN ÉDITION (chemin absent,
 *   alias tronqué, segment effectif vide) n'est jamais signalée.
 * - **`kt.conflictingImport`** : deux directives de même nom effectif mais de
 *   chemins qualifiés DISTINCTS se signalent sur la PLUS TARDIVE — le message
 *   porte les DEUX chemins (KP15 : « avec les deux noms »), l'information
 *   liée pointe la PREMIÈRE directive (KP15).
 */
internal object DiagnosticsImports {

    /**
     * Calcule tous les diagnostics du document.
     *
     * @param document le document ouvert (texte + arbre) ;
     * @return les diagnostics en ordre du document (syntaxe, puis imports
     * inutilisés, puis conflits) ; liste vide pour un document sain sans
     * import superflu.
     */
    internal fun calculer(document: DocumentOuvert): List<DiagnosticKotlin> {
        val resultat = mutableListOf<DiagnosticKotlin>()
        resultat.addAll(erreursSyntaxe(document.arbre.noeud))
        val directives = directivesSaines(document.arbre)
        val identifiants = identifiantsHorsDirectives(document.arbre.noeud)
        resultat.addAll(importsInutilises(directives, identifiants))
        resultat.addAll(importsConflictuels(directives))
        return resultat
    }

    /**
     * Collecte les erreurs de syntaxe : tout nœud porteur d'un message
     * d'erreur, en ordre du document (parcours préfixe de l'arbre
     * reconstruit).
     *
     * @param racine la racine de l'arbre du document ;
     * @return un diagnostic `kt.syntax` par nœud d'erreur (plage et message
     * amont repris tels quels).
     */
    private fun erreursSyntaxe(racine: NoeudArbre): List<DiagnosticKotlin> {
        val erreurs = mutableListOf<DiagnosticKotlin>()
        fun walk(n: NoeudArbre) {
            val message = n.messageErreur
            if (message != null) {
                erreurs.add(
                    DiagnosticKotlin(
                        code = CodeDiagnosticKotlin.SYNTAXE,
                        severite = SeveriteKotlin.ERREUR,
                        plage = PlageTexte(debut = n.debut, fin = n.fin),
                        message = message,
                        tags = emptyList(),
                        informationsLiees = emptyList(),
                    ),
                )
            }
            n.enfants.forEach { walk(it) }
        }
        walk(racine)
        return erreurs
    }

    /**
     * Rend les directives d'import SIGNIFIABLES du document : chemins
     * présents, non joker, alias ni absent ni tronqué, segment effectif non
     * vide (les directives EN ÉDITION sont exclues — jamais signalées).
     *
     * @param fichier la racine de la façade du document ;
     * @return les directives retenues, en ordre d'écriture.
     */
    private fun directivesSaines(fichier: KtFichier): List<KtDirectiveImport> {
        return fichier.imports().filter { directive ->
            if (directive.estJoker()) return@filter false
            val chemin = directive.nomQualifie() ?: return@filter false
            val porteurAlias = directive.premierEnfantDeGenre(KtNodeTypes.IMPORT_ALIAS) != null
            val alias = directive.alias()
            if (porteurAlias && alias == null) return@filter false
            nomEffectif(chemin, alias).isNotEmpty()
        }
    }

    /**
     * Rend le NOM EFFECTIF d'une directive : l'alias s'il est écrit, sinon le
     * dernier segment du chemin qualifié.
     *
     * @param chemin le chemin qualifié importé (jamais nul) ;
     * @param alias l'alias écrit (`null` si aucun) ;
     * @return le nom effectif (vide si le chemin se termine par un point —
     * directive tronquée).
     */
    private fun nomEffectif(chemin: String, alias: String?): String {
        return (alias ?: chemin.substringAfterLast('.')).trim()
    }

    /**
     * Collecte les textes des jetons IDENTIFIER du document HORS directives
     * package/import : les références de code seules comptent pour l'usage
     * d'un import (un nom en commentaire ou en chaîne ne compte pas — ce
     * sont des trivia ou des littéraux, jamais des identifiants).
     *
     * @param racine la racine de l'arbre du document ;
     * @return l'ensemble des noms écrits en code (hors préambule).
     */
    private fun identifiantsHorsDirectives(racine: NoeudArbre): Set<String> {
        val noms = mutableSetOf<String>()
        fun walk(n: NoeudArbre) {
            if (n.genre == KtNodeTypes.PACKAGE_DIRECTIVE || n.genre == KtNodeTypes.IMPORT_LIST) return
            n.jetonsPropres
                .filter { it.genre == KtTokens.IDENTIFIER }
                .forEach { noms.add(it.texte) }
            n.enfants.forEach { walk(it) }
        }
        walk(racine)
        return noms
    }

    /**
     * Rend la plage SIGNIFICATIVE d'une directive : la plage de l'écriture
     * SANS trivia de marge — la politique de liaison de la grammaire attache
     * les trivia (commentaires précédents, blancs et fins de ligne
     * traînantes — sonde : le `\n` d'une directive d'import se lie à son
     * dernier enfant) aux plages des productions, un diagnostic doit
     * désigner le CODE.
     *
     * Le début est le premier jeton propre ; la fin rogne les blancs
     * traînants du texte de la plage (le contenu des commentaires de fin de
     * ligne reste — périmètre exact des directives, sonde).
     *
     * @param directive la directive visée ;
     * @return la plage rognée (premier jeton propre → fin significative).
     */
    private fun plageSignificative(directive: KtDirectiveImport): PlageTexte {
        val noeud = directive.noeud
        val debut = noeud.jetonsPropres.firstOrNull()?.debut ?: noeud.debut
        val fin = if (noeud.fin > debut) {
            debut + directive.source.subSequence(debut, noeud.fin).toString().trimEnd().length
        } else {
            noeud.fin
        }
        return PlageTexte(debut = debut, fin = maxOf(fin, debut))
    }

    /**
     * Signale les imports INUTILISÉS : nom effectif absent des identifiants
     * du fichier.
     *
     * @param directives les directives signifiables (en ordre d'écriture) ;
     * @param identifiants les noms écrits en code (hors préambule) ;
     * @return un diagnostic `kt.unusedImport` par directive inutilisée —
     * portant le tag `INUTILE` (KP15), SANS information liée (KP15).
     */
    private fun importsInutilises(
        directives: List<KtDirectiveImport>,
        identifiants: Set<String>,
    ): List<DiagnosticKotlin> {
        val resultat = mutableListOf<DiagnosticKotlin>()
        for (directive in directives) {
            val chemin = directive.nomQualifie() ?: continue
            val alias = directive.alias()
            val effectif = nomEffectif(chemin, alias)
            if (effectif.isEmpty() || identifiants.contains(effectif)) continue
            val mention = if (alias == null) {
                "L'import « $chemin » n'est pas utilisé"
            } else {
                "L'import « $chemin » (alias « $alias ») n'est pas utilisé"
            }
            resultat.add(
                DiagnosticKotlin(
                    code = CodeDiagnosticKotlin.IMPORT_INUTILISE,
                    severite = SeveriteKotlin.AVERTISSEMENT,
                    plage = plageSignificative(directive),
                    message = mention,
                    tags = listOf(TagDiagnosticKotlin.INUTILE),
                    informationsLiees = emptyList(),
                ),
            )
        }
        return resultat
    }

    /**
     * Signale les imports CONFLICTUELS : même nom effectif, chemins qualifiés
     * distincts — signalé sur la plus tardive, message portant les DEUX
     * chemins, information liée sur la PREMIÈRE directive du même nom.
     *
     * @param directives les directives signifiables (en ordre d'écriture) ;
     * @return un diagnostic `kt.conflictingImport` par directive tardive en
     * conflit avec une directive antérieure.
     */
    private fun importsConflictuels(directives: List<KtDirectiveImport>): List<DiagnosticKotlin> {
        val resultat = mutableListOf<DiagnosticKotlin>()
        // Première directive vue par nom effectif (l'information liée la désigne).
        val premiereParNom = mutableMapOf<String, KtDirectiveImport>()
        for (directive in directives) {
            val chemin = directive.nomQualifie() ?: continue
            val effectif = nomEffectif(chemin, directive.alias())
            if (effectif.isEmpty()) continue
            val premiere = premiereParNom[effectif]
            if (premiere != null) {
                val cheminPremier = premiere.nomQualifie() ?: continue
                if (cheminPremier == chemin) continue // même import répété : pas un conflit
                resultat.add(
                    DiagnosticKotlin(
                        code = CodeDiagnosticKotlin.IMPORT_CONFLICTUEL,
                        severite = SeveriteKotlin.AVERTISSEMENT,
                        plage = plageSignificative(directive),
                        message = "L'import « $chemin » entre en conflit avec « $cheminPremier » " +
                            "(même nom effectif « $effectif »)",
                        tags = emptyList(),
                        informationsLiees = listOf(
                            InformationLieeKotlin(
                                plage = plageSignificative(premiere),
                                message = "Premier import portant le nom « $effectif »",
                            ),
                        ),
                    ),
                )
            } else {
                premiereParNom[effectif] = directive
            }
        }
        return resultat
    }
}

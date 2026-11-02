// CodeIDE — passerelle moteur → protocole LSP du serveur Kotlin (module
// :kotlin, étage 6 vague 6e — miroir :java N8). Code NEUF jjoblab : toutes
// les conversions de types du moteur ([jo.codeide.lsp.kotlin.api]) vers les
// liaisons lsp4j. Les paquets moteur restent LIBRES de lsp4j — la
// convention de l'étage 1 (« la conversion vers le protocole LSP est le
// travail de la couche hôte, pas du moteur ») est tenue : CE fichier est la
// couche hôte.
package jo.codeide.lsp.kotlin.protocole

import jo.codeide.lsp.kotlin.api.DiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EditionTexteKotlin
import jo.codeide.lsp.kotlin.api.EmplacementKotlin
import jo.codeide.lsp.kotlin.api.GenreSymboleKotlin
import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.api.TagDiagnosticKotlin
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticRelatedInformation
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DiagnosticTag
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextEdit

/**
 * La passerelle moteur → protocole LSP : les conversions de types et le
 * REPÈRE de document (offsets absolus ↔ positions UTF-16).
 *
 * **Positions** : le moteur raisonne en OFFSETS ABSOLUS de caractères ; le
 * protocole LSP raisonne en (ligne, caractère) d'UNITÉS UTF-16 —
 * l'encodage annoncé par la capability `positionEncoding` (UTF-16). Un
 * `String` Kotlin/JVM EST déjà une suite d'unités UTF-16 : l'offset du
 * `String` et le « caractère » LSP coïncident NUMÉRIQUEMENT, y compris
 * au-delà du BMP (un caractère supplémentaire compte pour DEUX — 3.17
 * §textDocument/positionEncoding). La conversion est donc une INDEXATION
 * de lignes : les débuts de ligne sont précalculés une fois par
 * instantané, la recherche est dichotomique.
 */
public object ConvertisseurLsp {

    // ----------------------------------------------------------------- //
    // Repère de document : offsets absolus ↔ positions LSP
    // ----------------------------------------------------------------- //

    /**
     * Le repère d'un document : les débuts de ligne d'un texte figé.
     *
     * Construit UNE fois par instantané (le copy-on-write rend le repère
     * cohérent avec le texte qu'il indexe pour toute la vie de
     * l'instantané). Recherche de ligne par dichotomie.
     *
     * @property texte le texte indexé (l'unité UTF-16 de comptage).
     * @property debutsLignes l'offset de début de CHAQUE ligne (la ligne 0
     *   commence à 0 ; chaque « \n » ouvre la ligne suivante).
     */
    public class Repere(public val texte: String, public val debutsLignes: IntArray) {

        /**
         * La position LSP d'un offset absolu.
         *
         * @param offset l'offset (borné au texte par sécurité).
         * @return la position (ligne, caractère UTF-16).
         */
        public fun position(offset: Int): Position {
            val borne = offset.coerceIn(0, texte.length)
            val ligne = rechercheLigne(borne)
            return Position(ligne, borne - debutsLignes[ligne])
        }

        /**
         * La plage LSP d'une plage moteur.
         *
         * @param plage la plage moteur (offsets absolus).
         * @return la plage LSP correspondante.
         */
        public fun plage(plage: PlageTexte): Range =
            Range(position(plage.debut), position(plage.fin))

        /**
         * L'offset absolu d'une position LSP (le chemin inverse — les
         * positions client y sont BORNÉES au texte, jamais d'exception).
         *
         * @param position la position LSP (ligne, caractère).
         * @return l'offset absolu borné.
         */
        public fun offset(position: Position): Int {
            val ligne = position.line.coerceIn(0, debutsLignes.size - 1)
            val debutLigne = debutsLignes[ligne]
            val borne = position.character.coerceAtLeast(0)
            val finLigne = if (ligne + 1 < debutsLignes.size) {
                debutsLignes[ligne + 1] - 1
            } else {
                texte.length
            }
            return (debutLigne + borne).coerceAtMost(finLigne)
        }

        /** La ligne d'un offset (dichotomie sur les débuts). */
        private fun rechercheLigne(offset: Int): Int {
            var bas = 0
            var haut = debutsLignes.size - 1
            while (bas < haut) {
                val milieu = (bas + haut + 1) ushr 1
                if (debutsLignes[milieu] <= offset) bas = milieu else haut = milieu - 1
            }
            return bas
        }
    }

    /**
     * Le repère d'un texte (les débuts de ligne précalculés).
     *
     * @param texte le texte à indexer.
     * @return le repère du texte.
     */
    public fun repere(texte: String): Repere {
        val debuts = mutableListOf(0)
        for (indice in texte.indices) {
            if (texte[indice] == '\n') debuts.add(indice + 1)
        }
        return Repere(texte, debuts.toIntArray())
    }

    // ----------------------------------------------------------------- //
    // Les conversions de types
    // ----------------------------------------------------------------- //

    /**
     * Le chemin de fichier d'une URI `file://` (null si ce n'en est pas
     * une — la forme est préservée telle quelle pour les URI de fixture).
     */
    public fun cheminDeUri(uri: String): String? {
        if (!uri.startsWith("file://")) return null
        return try {
            java.net.URI(uri).path
        } catch (_: Exception) {
            null
        }
    }

    /** La sévérité LSP d'une sévérité moteur. */
    public fun severite(severite: SeveriteKotlin): DiagnosticSeverity = when (severite) {
        SeveriteKotlin.ERREUR -> DiagnosticSeverity.Error
        SeveriteKotlin.AVERTISSEMENT -> DiagnosticSeverity.Warning
        SeveriteKotlin.INFORMATION -> DiagnosticSeverity.Information
    }

    /** Le tag LSP d'un tag moteur (`null` : aucun tag inventé hors famille). */
    public fun tag(tag: TagDiagnosticKotlin): DiagnosticTag = when (tag) {
        TagDiagnosticKotlin.INUTILE -> DiagnosticTag.Unnecessary
    }

    /**
     * Le diagnostic LSP d'un diagnostic moteur : le code `kt.*` voyage TEL
     * QUEL (le catalogue conservateur du cahier), les informations liées
     * comprises (KP15).
     */
    public fun diagnostic(source: DiagnosticKotlin, uri: String, repere: Repere): Diagnostic {
        val rendu = Diagnostic()
        rendu.severity = severite(source.severite)
        rendu.setCode(org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(source.code.code))
        rendu.message = source.message
        rendu.range = repere.plage(source.plage)
        if (source.tags.isNotEmpty()) {
            rendu.tags = source.tags.map(::tag)
        }
        if (source.informationsLiees.isNotEmpty()) {
            rendu.relatedInformation = source.informationsLiees.map { liee ->
                DiagnosticRelatedInformation(
                    Location(uri, repere.plage(liee.plage)),
                    liee.message,
                )
            }
        }
        return rendu
    }

    /** La localisation LSP d'un emplacement moteur. */
    public fun emplacement(emplacement: EmplacementKotlin, repere: Repere): Location =
        Location(emplacement.uri, repere.plage(emplacement.plage))

    /** Le kind de complétion LSP d'une nature de proposition moteur. */
    public fun kindCompletion(nature: NaturePropositionKotlin): CompletionItemKind =
        when (nature) {
            NaturePropositionKotlin.VARIABLE -> CompletionItemKind.Variable
            NaturePropositionKotlin.PARAMETRE -> CompletionItemKind.Variable
            NaturePropositionKotlin.PROPRIETE -> CompletionItemKind.Property
            NaturePropositionKotlin.FONCTION -> CompletionItemKind.Function
            NaturePropositionKotlin.ENUM -> CompletionItemKind.EnumMember
            NaturePropositionKotlin.CLASSE -> CompletionItemKind.Class
            NaturePropositionKotlin.INTERFACE -> CompletionItemKind.Interface
            NaturePropositionKotlin.ANNOTATION -> CompletionItemKind.Class
            NaturePropositionKotlin.PAQUET -> CompletionItemKind.Module
            NaturePropositionKotlin.MOT_CLE -> CompletionItemKind.Keyword
        }

    /** Le kind de symbole LSP d'un genre de symbole moteur. */
    public fun kindSymbole(genre: GenreSymboleKotlin): SymbolKind = when (genre) {
        GenreSymboleKotlin.CLASSE -> SymbolKind.Class
        GenreSymboleKotlin.INTERFACE -> SymbolKind.Interface
        GenreSymboleKotlin.ENUM -> SymbolKind.Enum
        GenreSymboleKotlin.OBJET -> SymbolKind.Object
        GenreSymboleKotlin.FONCTION -> SymbolKind.Function
        GenreSymboleKotlin.PROPRIETE -> SymbolKind.Field
        GenreSymboleKotlin.TYPEALIAS -> SymbolKind.TypeParameter
    }

    /** L'édition de texte LSP d'une édition moteur (plage + texte). */
    public fun edition(edition: EditionTexteKotlin, repere: Repere): TextEdit =
        TextEdit(repere.plage(edition.plage), edition.texte)

    /** Les éditions de texte LSP d'une liste moteur. */
    public fun editions(editions: List<EditionTexteKotlin>, repere: Repere): List<TextEdit> =
        editions.map { edition(it, repere) }
}

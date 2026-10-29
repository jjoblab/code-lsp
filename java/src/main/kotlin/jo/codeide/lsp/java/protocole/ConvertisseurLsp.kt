package jo.codeide.lsp.java.protocole

import jo.codeide.lsp.java.actionscode.ActionCode
import jo.codeide.lsp.java.actionscode.FamilleProbleme
import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.navigation.CibleNavigation
import jo.codeide.lsp.java.navigation.GenreCible
import jo.codeide.lsp.java.navigation.GenreSurbrillance
import jo.codeide.lsp.java.navigation.Surbrillance
import jo.codeide.lsp.java.navigation.Usage
import jo.codeide.lsp.java.outils.EditionTexte
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DiagnosticTag
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit

/**
 * La passerelle moteur → protocole LSP (étage N8) : toutes les
 * conversions de types du moteur ([jo.codeide.lsp.java]) vers les
 * liaisons lsp4j. Les paquets moteur restent LIBRES de lsp4j — la
 * convention de l'étage N1 (« la conversion vers le protocole LSP est
 * le travail de la couche hôte, pas du moteur », TypesDiagnostics) est
 * tenue : CE fichier est la couche hôte.
 *
 * **Positions** : le moteur raisonne en OFFSETS ABSOLUS de caractères ;
 * le protocole LSP raisonne en (ligne, caractère) d'UNITÉS UTF-16 —
 * l'encodage annoncé par la capability `positionEncoding` (UTF-16,
 * fiche 055). Un `String` Kotlin/JVM EST déjà une suite d'unités UTF-16 :
 * l'offset du `String` et le « caractère » LSP coïncident NUMÉRIQUEMENT,
 * y compris au-delà du BMP (un caractère supplémentaire compte pour
 * DEUX — 3.17 §textDocument/positionEncoding). La conversion est donc
 * une INDEXATION de lignes : les débuts de ligne sont précalculés une
 * fois par instantané de document, la recherche est dichotomique.
 *
 * **Codes de diagnostic** : le cahier (fiche 056) verrouille la forme
 * préfixée `ecj:<IProblem.ID>` — le moteur transporte l'ID nu
 * (`getID().toString()`), la couche protocole préfixe. Les TAGS ne sont
 * posés QUE sur les familles connues (fiche 056 : « pas de tag inventé
 * hors famille » — Unnecessary sur les déclarables inutilisés).
 *
 * **URI** : le moteur rend des `file://` et des `jdt://decompiled/…`
 * (cibles binaires, fiches 049/060) ; le protocole les transporte telles
 * quelles — la résolution `file://` ↔ chemin disque est symétrique et
 * sans perte.
 */
public object ConvertisseurLsp {

    /** Le préfixe des codes de diagnostic côté protocole (fiche 056). */
    public const val PREFIXE_CODE_ECJ: String = "ecj:"

    // ----------------------------------------------------------------- //
    // Repère de document : offsets absolus ↔ positions LSP
    // ----------------------------------------------------------------- //

    /**
     * Le repère d'un document : les débuts de ligne d'un texte figé.
     *
     * Construit UNE fois par instantané (le copy-on-write rend le repère
     * cohérent avec le texte qu'il indexe pour toute la vie de
     * l'instantané). Recherche de ligne par dichotomie — le repère d'un
     * fichier de 10^5 lignes se sonde en ~17 comparaisons.
     *
     * @property texte le texte indexé (l'unité UTF-16 de comptage).
     * @property debutsLignes l'offset de début de CHAQUE ligne (la
     *   ligne 0 commence à 0 ; chaque « \n » ouvre la ligne suivante).
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
        public fun plage(plage: Plage): Range = Range(position(plage.debutOffset), position(plage.finOffset))

        /**
         * L'offset absolu d'une position LSP (le chemin inverse —
         * didChange n'en a pas besoin en sync pleine, mais le gardien de
         * cohérence des requêtes l'utilise pour borner les positions
         * client).
         *
         * @param position la position LSP (ligne, caractère).
         * @return l'offset absuel, borné au texte (jamais de hors-bornes).
         */
        public fun offset(position: Position): Int {
            val ligne = position.line.coerceIn(0, debutsLignes.size - 1)
            val debutLigne = debutsLignes[ligne]
            val finLigne = if (ligne + 1 < debutsLignes.size) debutsLignes[ligne + 1] else texte.length
            return (debutLigne + position.character).coerceIn(debutLigne, finLigne)
        }

        /** Recherche dichotomique de la ligne portant l'offset. */
        private fun rechercheLigne(offset: Int): Int {
            var bas = 0
            var haut = debutsLignes.size - 1
            while (bas < haut) {
                val milieu = (bas + haut + 1) / 2
                if (debutsLignes[milieu] <= offset) bas = milieu else haut = milieu - 1
            }
            return bas
        }
    }

    /**
     * Construit le repère d'un texte.
     *
     * @param texte le texte Java complet.
     * @return le repère indexé (débuts de ligne précalculés).
     */
    public fun repere(texte: String): Repere {
        val debuts = ArrayList<Int>()
        debuts.add(0)
        var indice = 0
        while (true) {
            val suivant = texte.indexOf('\n', indice)
            if (suivant < 0) break
            debuts.add(suivant + 1)
            indice = suivant + 1
        }
        return Repere(texte, debuts.toIntArray())
    }

    // ----------------------------------------------------------------- //
    // Diagnostics : moteur → LSP (+ tags, + code préfixé)
    // ----------------------------------------------------------------- //

    /**
     * Un diagnostic moteur converti, avec son repère : la publication a
     * besoin du [Diagnostic] LSP ET de la version du document — cette
     * forme évite un second passage.
     *
     * @property lsp le diagnostic LSP prêt à publier.
     * @property moteur le diagnostic moteur source (traçabilité).
     */
    public data class DiagnosticLsp(
        public val lsp: org.eclipse.lsp4j.Diagnostic,
        public val moteur: Diagnostic,
    )

    /**
     * Convertit un diagnostic moteur (fiche 056 : tags, code préfixé).
     *
     * Les TAGS suivent les familles du cahier : `Unnecessary` sur les
     * déclarables inutilisés (import inutilisé, déclaration inutilisée —
     * le rendu estompé côté éditeur) ; TOUTE autre famille reste SANS
     * tag (liste vide — jamais un tag inventé).
     *
     * @param diagnostic le diagnostic moteur.
     * @param repere le repère du document porteur.
     * @return le diagnostic LSP + traçabilité moteur.
     */
    public fun diagnostic(diagnostic: Diagnostic, repere: Repere): DiagnosticLsp {
        val lsp = org.eclipse.lsp4j.Diagnostic()
        lsp.range = repere.plage(diagnostic.plage)
        lsp.severity = severite(diagnostic.severite)
        lsp.message = diagnostic.message
        lsp.code = diagnostic.code?.let { Either.forLeft(PREFIXE_CODE_ECJ + it) }
        lsp.tags = tagsDe(diagnostic)
        return DiagnosticLsp(lsp, diagnostic)
    }

    /**
     * Convertit une liste de diagnostics moteur.
     *
     * @param diagnostics les diagnostics moteur.
     * @param repere le repère du document porteur.
     * @return les diagnostics LSP (même ordre — l'ordre moteur est déjà
     *   le contrat de tri stable de la publication).
     */
    public fun diagnostics(diagnostics: List<Diagnostic>, repere: Repere): List<org.eclipse.lsp4j.Diagnostic> =
        diagnostics.map { diagnostic(it, repere).lsp }

    /** La sévérité LSP d'une sévérité moteur. */
    private fun severite(severite: Severite): DiagnosticSeverity = when (severite) {
        Severite.INFO -> DiagnosticSeverity.Information
        Severite.AVERTISSEMENT -> DiagnosticSeverity.Warning
        Severite.ERREUR -> DiagnosticSeverity.Error
    }

    /** Les tags d'un diagnostic moteur — famille connue uniquement. */
    private fun tagsDe(diagnostic: Diagnostic): List<DiagnosticTag> =
        when (FamilleProbleme.fromCode(diagnostic.code)) {
            FamilleProbleme.UNUSED_IMPORT, FamilleProbleme.UNUSED_DECLARATION ->
                listOf(DiagnosticTag.Unnecessary)
            else -> emptyList()
        }

    // ----------------------------------------------------------------- //
    // Navigation : cibles, usages, surbrillances
    // ----------------------------------------------------------------- //

    /**
     * La `Location` LSP d'une cible moteur (fiches 049/060/062) : URI
     * transportée telle quelle (`file://` ou `jdt://decompiled/`), plage
     * convertie par LE REPÈRE DU FICHIER PORTeur — le repère du document
     * interrogé ne convient QUE pour [GenreCible.SOURCE_COURANTE].
     *
     * @param cible la cible moteur.
     * @param reperePorteur le repère du fichier PORTANT la cible.
     * @return la localisation LSP.
     */
    public fun localisation(cible: CibleNavigation, reperePorteur: Repere): Location =
        Location(cible.uri, reperePorteur.plage(cible.plage))

    /**
     * Les `Location` LSP des usages d'un symbole (fiche 054 :
     * `references` — chaque usage est un fichier du projet, le repère de
     * CHAQUE fichier sert la conversion).
     *
     * @param usages les usages moteur (chemin logique + plage).
     * @param reperes les repères par chemin logique (le serveur tient le
     *   cache des textes de projet — fichiers sur disque + overlay).
     * @return les localisations LSP, URI `file://` du fichier porteur.
     */
    public fun localisations(usages: List<Usage>, reperes: (String) -> Repere?): List<Location> =
        usages.mapNotNull { usage ->
            reperes(usage.chemin)?.let { Location(uriDeFichier(usage.chemin), it.plage(usage.plage)) }
        }

    /**
     * L'URI `file://` d'un chemin logique moteur (la forme exacte que le
     * moteur rend dans [CibleNavigation.uri] pour les sources).
     *
     * @param chemin le chemin (logique ou absolu — moteur).
     * @return l'URI `file://` correspondante.
     */
    public fun uriDeFichier(chemin: String): String = "file://$chemin"

    /**
     * Le chemin moteur d'une URI `file://` (le miroir de [uriDeFichier] ;
     * les URI non `file` rendent null — le serveur ignore le document).
     *
     * @param uri l'URI du document.
     * @return le chemin moteur, ou null.
     */
    public fun cheminDeUri(uri: String): String? =
        if (uri.startsWith("file://")) uri.removePrefix("file://") else null

    /**
     * Le `DocumentHighlight` LSP d'une surbrillance moteur (fiche 054 :
     * kind numérique — TEXTE 1 / LECTURE 2 / ECRITURE 3, valeurs posées
     * par [GenreSurbrillance.codeLsp]).
     *
     * @param surbrillance la surbrillance moteur.
     * @param repere le repère du document courant.
     * @return le surlignage LSP.
     */
    public fun surlignage(surbrillance: Surbrillance, repere: Repere): DocumentHighlight =
        DocumentHighlight(repere.plage(surbrillance.plage), DocumentHighlightKind.forValue(surbrillance.genre.codeLsp))

    // ----------------------------------------------------------------- //
    // Éditions : moteur → TextEdit LSP
    // ----------------------------------------------------------------- //

    /**
     * Le `TextEdit` LSP d'une édition moteur (offset + longueur + texte).
     *
     * @param edition l'édition moteur.
     * @param repere le repère du document édité.
     * @return l'édition LSP.
     */
    public fun edition(edition: EditionTexte, repere: Repere): TextEdit {
        val plage = Plage(edition.offset, edition.offset + edition.longueur)
        return TextEdit(repere.plage(plage), edition.texte)
    }

    /**
     * Les `TextEdit` LSP d'un plan d'éditions moteur (fiche 016 : le
     * client applique en ordre DÉCROISSANT — les plages LSP étant
     * disjointes, l'ordre du plan est conservé tel quel).
     *
     * @param editions les éditions moteur.
     * @param repere le repère du document édité.
     * @return les éditions LSP.
     */
    public fun editions(editions: List<EditionTexte>, repere: Repere): List<TextEdit> =
        editions.map { edition(it, repere) }

    /**
     * Le titre de commande d'une action (fiche 058 : les titres
     * exacts en français — singulier/pluriel).
     *
     * @param nombre le nombre de références comptées.
     * @return le titre contractuel (« 0 référence » / « 1 référence » /
     *   « N références »).
     */
    public fun titreCompteurReferences(nombre: Int): String =
        if (nombre > 1) "$nombre références" else "$nombre référence"

    // ----------------------------------------------------------------- //
    // Actions de code : moteur → CodeAction LSP
    // ----------------------------------------------------------------- //

    /**
     * Le `CodeAction` LSP d'une action moteur (étage N6 → protocole N8,
     * fiche 063) : titre humain, kind hiérarchique (le kind moteur
     * `quickfix.<famille>` EST déjà une forme hiérarchique LSP 3.17 —
     * « quickfix » annoncé dans les capabilities, fiche 055), diagnostic
     * source converti, et l'édition de l'espace de travail en forme
     * HÉRITÉE `changes` (URI → éditions — le client cel-lsp n'annonce
     * pas `documentChanges`, convention N5).
     *
     * @param action l'action moteur (editions JAMAIS vides — fiche 063).
     * @param uri l'URI du document interrogé (les correcteurs N6 sont
     *   mono-fichier : les éditions vivent dans CE document).
     * @param repere le repère du document interrogé.
     * @return l'action LSP prête à la réponse `codeAction`.
     */
    public fun actionCode(action: ActionCode, uri: String, repere: Repere): org.eclipse.lsp4j.CodeAction {
        val lsp = org.eclipse.lsp4j.CodeAction()
        lsp.title = action.titre
        lsp.kind = action.kind
        action.diagnostic?.let { source -> lsp.diagnostics = listOf(diagnostic(source, repere).lsp) }
        if (action.editions.isNotEmpty()) {
            val espace = org.eclipse.lsp4j.WorkspaceEdit()
            espace.changes = java.util.Collections.singletonMap(uri, editions(action.editions, repere))
            lsp.edit = espace
        }
        return lsp
    }
}

package jo.codeide.lsp.java.confort

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.formatage.StyleFormattage
import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.survol.SurvolRiche

/**
 * Genre de marquage d'un contenu rendu au client (fiche 057) : les kinds
 * du protocole LSP.
 *
 * @property libelleLsp la valeur `kind` du protocole.
 */
public enum class GenreMarquage(public val libelleLsp: String) {
    /** Texte brut — signature seule, jamais de clôtures de code. */
    TEXTE_BRUT("plaintext"),

    /** Markdown — signature en bloc de code ```java puis la javadoc. */
    MARKDOWN("markdown"),
}

/**
 * Un contenu marqué rendu au client (fiche 057) : le genre et la valeur —
 * JAMAIS la forme MarkedString-string dépréciée.
 *
 * @property genre le genre de marquage.
 * @property valeur la valeur rendue.
 */
public data class ContenuMarquage(
    public val genre: GenreMarquage,
    public val valeur: String,
)

/**
 * Capacités de confort annoncées par le serveur (fiche 057) : le miroir
 * moteur du bloc `capabilities` — la couche LSP4J (étage N8) la recopiera
 * telle quelle.
 *
 * @property formatagePlageDocument `documentRangeFormattingProvider`,
 *   annoncé en forme BOOLÉENNE (pas en options détaillées).
 */
public data class CapacitesConfort(
    public val formatagePlageDocument: Boolean = true,
)

/**
 * Requête refusée par la garde de cycle de vie I2 (fiche 057) : tout
 * service recevait une requête AVANT `initialized` — le miroir moteur de
 * la `ResponseErrorException` du domaine ; l'étage N8 la traduit en code
 * d'erreur protocole.
 */
public class RequeteRefusee(message: String) : RuntimeException(message)

/**
 * Service du lot S2-a « confort structurel » (étage N7 — cahier fiche
 * 057) : formatage de SÉLECTION borné à la plage et survol riche
 * marqué — le MIROIR MOTEUR de la façade `JavaTextDocumentService` (la
 * couche LSP4J et son cycle de vie complet arrivent à l'étage N8 ; les
 * capacités et la garde I2 vivent ICI, prêtes à être branchées).
 *
 * Garde lifecycle : le service refuse toute requête avant
 * [initialiser] — exactement la garde I2 du cahier.
 *
 * @param moteur le moteur hôte (formatage de plage, survol riche).
 */
public class ServiceConfort(private val moteur: MoteurJava) {

    /** Vrai ssi [initialiser] a été appelé (le cycle de vie est ouvert). */
    private var initialise: Boolean = false

    /**
     * Ouvre le cycle de vie du service (équivalent moteur du
     * `initialized` LSP).
     */
    public fun initialiser() {
        initialise = true
    }

    /**
     * Les capacités de confort annoncées (fiche 057 :
     * `documentRangeFormattingProvider` en forme booléenne TRUE).
     */
    public fun capacites(): CapacitesConfort = CapacitesConfort(formatagePlageDocument = true)

    /**
     * Formatage de SÉLECTION (fiche 057) : les éditions sont bornées à la
     * plage demandée — le code hors plage n'est JAMAIS touché (vérifié
     * par reconstruction du texte complet côté client).
     *
     * @param chemin le chemin logique du document.
     * @param source la source originale.
     * @param debut le début de la sélection.
     * @param longueur la longueur de la sélection.
     * @param style le style de formatage (défaut Eclipse).
     * @return les éditions ordonnées, ou null (imparsable).
     * @throws RequeteRefusee si le service n'est pas initialisé (garde I2).
     */
    public fun formatagePlage(
        chemin: String,
        source: String,
        debut: Int,
        longueur: Int,
        style: StyleFormattage = StyleFormattage.ECLIPSE,
    ): List<EditionTexte>? {
        exigerInitialisation("rangeFormatting")
        return moteur.editionsFormatagePlage(source, debut, longueur, style)
    }

    /**
     * Contenu de survol MARQUÉ (fiche 057) : signature seule → PLAINTEXT
     * (aucune clôture de code) ; signature + javadoc → MARKDOWN, la valeur
     * COMMENÇANT par « ```java\n » (bloc de code de la signature) puis la
     * javadoc. Jamais la forme MarkedString-string dépréciée.
     *
     * @param chemin le chemin logique du document.
     * @param source la source originale.
     * @param offset la position du caret.
     * @return le contenu marqué, ou null (aucun survol à cet offset).
     * @throws RequeteRefusee si le service n'est pas initialisé (garde I2).
     */
    public fun contenuHover(chemin: String, source: String, offset: Int): ContenuMarquage? {
        exigerInitialisation("hover")
        val survol: SurvolRiche = moteur.survolRiche(chemin, source, offset) ?: return null
        val documentation = survol.documentation?.trim().orEmpty()
        return if (documentation.isEmpty()) {
            ContenuMarquage(GenreMarquage.TEXTE_BRUT, survol.signature)
        } else {
            ContenuMarquage(GenreMarquage.MARKDOWN, "```java\n${survol.signature}\n```\n$documentation")
        }
    }

    /** Garde lifecycle I2 : toute requête avant initialisation est rejetée. */
    private fun exigerInitialisation(service: String) {
        if (!initialise) {
            throw RequeteRefusee("requête $service refusée : service non initialisé (garde I2)")
        }
    }
}

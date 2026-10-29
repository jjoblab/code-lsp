package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.outils.EditionTexte

/**
 * Le service de renommage HONNÊTE (étage N5 — cahier fiche 061) : le
 * miroir moteur de `textDocument/prepareRename` et `textDocument/rename`.
 * La couche LSP4J et son cycle de vie complet arrivent à l'étage N8 —
 * les capacités, la négociation de forme et l'erreur typée vivent ICI,
 * prêtes à être branchées.
 *
 * Comportements clés (fiche 061, lot S0-b « rename honnête », I8) :
 *
 *  - `prepareRename` rend le placeholder EXACT et la plage EXACTE du
 *    jeton — le caret AU MILIEU du nom rend la plage du jeton ENTIER ;
 *  - `rename` sans cible REJETTE par [RenommageSansCible] (code
 *    RequestFailed -32803, message FR, `data` structuré avec le code
 *    machine « renommage.sansCible » et la position) — JAMAIS une
 *    édition vide silencieuse ;
 *  - le `WorkspaceEdit` rendu suit la forme NÉGOCIÉE : `changes`
 *    (héritée) quand le client n'annonce pas
 *    `workspace.workspaceEdit.documentChanges`, `documentChanges`
 *    versionnée quand il l'annonce — jamais les deux ;
 *  - l'édition versionnée porte la version COURANTE du document, celle
 *    posée par le didOpen ([ouvrirDocument]) — les fichiers balayés
 *    non ouverts portent une version null.
 *
 * Les COLLISIONS typées sont servies par [conflits] : le moteur calcule
 * et signale, le CLIENT décide (projection ModShowConflicts — dialogue
 * de décision utilisateur locale) ; [renommage] calcule toujours les
 * éditions (modèle serveur-calcule / client-applique, ADR 0014).
 *
 * @param moteur le moteur hôte (phases du renommage).
 * @param capacites les capacités d'édition d'espace de travail
 *   annoncées par le client à initialize (négociation de forme).
 */
public class ServiceRenommage(
    private val moteur: MoteurJava,
    private val capacites: CapsEspaceDeTravailClient = CapsEspaceDeTravailClient(),
) {

    /** Les versions courantes des documents ouverts (miroir didOpen). */
    private val versions = HashMap<String, Long>()

    /**
     * Ouvre un document (miroir moteur du `didOpen` LSP) : pose la
     * version courante portée par les éditions versionnées.
     *
     * @param uri l'URI du document ouvert.
     * @param version la version posée par le didOpen.
     */
    public fun ouvrirDocument(uri: String, version: Long) {
        versions[uri] = version
    }

    /**
     * Ferme un document (miroir moteur du `didClose` LSP) : la version
     * n'est plus connue.
     *
     * @param uri l'URI du document fermé.
     */
    public fun fermerDocument(uri: String) {
        versions.remove(uri)
    }

    /**
     * `textDocument/prepareRename` (fiche 061) : le placeholder EXACT et
     * la plage EXACTE du jeton — le caret AU MILIEU du nom rend la plage
     * du jeton ENTIER (jamais `[caret, caret + longueur)` comme
     * l'ancien code).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet (tampon ouvert).
     * @param offset la position du caret.
     * @return la préparation { plage, placeholder }, ou null (mot-clé ou
     *   hors jeton renommable — le client n'ouvre pas la boîte de
     *   dialogue).
     */
    public fun preparerRenommage(chemin: String, source: String, offset: Int): PreparationRenommage? {
        val cible = moteur.cibleRenommageA(chemin, source, offset) ?: return null
        val placeholder = try {
            if (cible.jeton.debutOffset in 0..source.length && cible.jeton.finOffset <= source.length) {
                source.substring(cible.jeton.debutOffset, cible.jeton.finOffset)
            } else {
                cible.nomActuel
            }
        } catch (_: Exception) {
            cible.nomActuel
        }
        return PreparationRenommage(cible.jeton, placeholder)
    }

    /**
     * `textDocument/rename` (fiche 061) : les éditions du renommage
     * sous la forme NÉGOCIÉE — `changes` (héritée) ou `documentChanges`
     * versionnée, JAMAIS les deux.
     *
     * @param uri l'URI du document ouvert côté client (l'identité du
     *   tampon — les éditions du document courant voyagent sous CETTE
     *   URI ; les autres fichiers balayés sous `file://`).
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte Java complet (tampon ouvert).
     * @param offset la position du caret.
     * @param nouveauNom le nouveau nom.
     * @return l'édition d'espace de travail (forme négociée).
     * @throws RenommageSansCible si aucun symbole renommable ne vit au
     *   caret (erreur typée — jamais une édition vide silencieuse).
     */
    public fun renommage(
        uri: String,
        chemin: String,
        source: String,
        offset: Int,
        nouveauNom: String,
    ): EditionEspaceDeTravail {
        val cible = moteur.cibleRenommageA(chemin, source, offset)
            ?: throw RenommageSansCible(positionSource(source, offset))

        val parFichier = moteur.renommageProjet(chemin, source, offset)
        val cheminCourant = cheminDeUri(uri)

        if (!capacites.documentChanges) {
            // Forme héritée : URI → éditions (le client cel-lsp).
            val changes = LinkedHashMap<String, List<EditionTexte>>()
            for ((fichier, occurrences) in parFichier) {
                val uriFichier = if (fichier == cheminCourant) uri else uriDeFichier(fichier)
                changes[uriFichier] = moteur.editionsRenommage(nouveauNom, occurrences)
            }
            return EditionEspaceDeTravail.formeChanges(changes)
        }

        // Forme versionnée : TextDocumentEdit par document, version
        // COURANTE du document ouvert (celle du didOpen), null sinon.
        val documentChanges = ArrayList<EditionDocumentVersionnee>()
        for ((fichier, occurrences) in parFichier) {
            val uriFichier = if (fichier == cheminCourant) uri else uriDeFichier(fichier)
            documentChanges.add(
                EditionDocumentVersionnee(
                    uri = uriFichier,
                    version = versions[uriFichier],
                    editions = moteur.editionsRenommage(nouveauNom, occurrences),
                ),
            )
        }
        return EditionEspaceDeTravail.formeDocumentChanges(documentChanges)
    }

    /**
     * Les COLLISIONS typées du renommage proposé (phase CONFLITS,
     * ADR 0014) : servies au client pour le dialogue de décision — le
     * moteur signale, le client décide.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet (tampon ouvert).
     * @param offset la position du caret.
     * @param nouveauNom le nouveau nom proposé.
     * @param suivreOverrides suivre les overrides (opt-in explicite).
     * @return les collisions typées (vide = renommage sûr).
     */
    public fun conflits(
        chemin: String,
        source: String,
        offset: Int,
        nouveauNom: String,
        suivreOverrides: Boolean = false,
    ): List<ConflitRenommage> =
        moteur.conflitsRenommage(chemin, source, offset, nouveauNom, suivreOverrides)

    /**
     * L'APERÇU du renommage (preview = résultat, ADR 0014) : la source
     * d'UN fichier après application de ses éditions.
     *
     * @param source la source originale du fichier.
     * @param editions les éditions du renommage pour CE fichier.
     * @return la source previewée.
     */
    public fun apercu(source: String, editions: List<EditionTexte>): String =
        moteur.apercuRenommage(source, editions)

    /**
     * La cible courante du renommage (diagnostic client : nom, genre,
     * clé structurelle, périmètre).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return la cible, ou null.
     */
    public fun cible(chemin: String, source: String, offset: Int): CibleRenommage? =
        moteur.cibleRenommageA(chemin, source, offset)

    // ------------------------------------------------------------------ //
    // Outils internes
    // ------------------------------------------------------------------ //

    /** Le chemin de fichier d'une URI `file://` (l'URI sinon). */
    private fun cheminDeUri(uri: String): String =
        if (uri.startsWith("file://")) uri.removePrefix("file://") else uri

    /** L'URI d'un fichier balayé (convention `file://<chemin>`). */
    private fun uriDeFichier(fichier: String): String = "file://$fichier"

    /** La position (ligne, caractère UTF-16, offset) d'un offset dans la source. */
    private fun positionSource(source: String, offset: Int): PositionSource {
        var ligne = 0
        var debutLigne = 0
        val borne = offset.coerceIn(0, source.length)
        for (i in 0 until borne) {
            if (source[i] == '\n') {
                ligne++
                debutLigne = i + 1
            }
        }
        return PositionSource(ligne, borne - debutLigne, borne)
    }
}

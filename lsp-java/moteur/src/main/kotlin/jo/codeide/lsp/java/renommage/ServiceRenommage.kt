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
 * **Régimes du moteur** (INTEGRATION-CODEIDE §3.3, option B) : le service
 * consomme un FOURNISSEUR `(chemin) -> MoteurJava?` — la forme FIGÉE
 * (constructeur de compatibilité) sert le moteur unique capturé à
 * initialize ; la forme VIVANTE résout le moteur du document RENOMMÉ par
 * le ROUTAGE fichier → module (fiche 047, relu à chaque requête : une
 * reconfiguration à chaud sert le nouveau moteur sans reconstruction du
 * service — le miroir des versions DID PAR le service survit aux sync).
 *
 * @param moteur le résolveur du moteur par chemin de document.
 * @param capacites les capacités d'édition d'espace de travail
 *   annoncées par le client à initialize (négociation de forme).
 */
public class ServiceRenommage(
    private val moteur: (chemin: String) -> MoteurJava?,
    private val capacites: CapsEspaceDeTravailClient = CapsEspaceDeTravailClient(),
) {

    /**
     * La forme FIGÉE (compatibilité des tests du cahier) : le moteur
     * unique sert toutes les requêtes.
     *
     * @param moteur le moteur hôte (phases du renommage).
     * @param capacites les capacités d'édition d'espace de travail
     *   annoncées par le client à initialize (négociation de forme).
     */
    public constructor(
        moteur: MoteurJava,
        capacites: CapsEspaceDeTravailClient = CapsEspaceDeTravailClient(),
    ) : this({ _ -> moteur }, capacites)

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
        val moteur = moteur(chemin) ?: return null
        // V-D3 (G-028) : une RESSOURCE Android au caret (`R.string.foo`)
        // se prépare pareil — le placeholder EXACT, la plage EXACTE du
        // jeton (celle du segment `<nom>`) ; un refus honnête (fichier,
        // styleable) ferme la boîte de dialogue.
        moteur.cibleRessourceA(chemin, source, offset)?.let { ressource ->
            if (ressource.refus != null) return null
            return PreparationRenommage(ressource.jeton, ressource.nom)
        }
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
        val moteur = moteur(chemin) ?: throw RenommageSansCible(positionSource(source, offset))

        // V-D3 (G-028) : le RENOMMAGE DE RESSOURCE — propagation code Java
        // (R.<type>.<nom>) ET XML (déclarations name=, références @type/,
        // @+type/, ?type/, data binding). Un refus honnête (fichier,
        // styleable) REJETE — jamais une édition vide silencieuse.
        val cibleRessource = moteur.cibleRessourceA(chemin, source, offset)
        if (cibleRessource != null) {
            if (cibleRessource.refus != null) {
                throw RenommageSansCible(positionSource(source, offset))
            }
            val parFichier = moteur.renommageRessource(chemin, source, offset, nouveauNom)
            if (parFichier.isEmpty()) {
                throw RenommageSansCible(positionSource(source, offset))
            }
            return formesNegociees(uri, chemin, parFichier.mapValues { (_, editions) -> editions })
        }

        val cible = moteur.cibleRenommageA(chemin, source, offset)
            ?: throw RenommageSansCible(positionSource(source, offset))

        val parFichier = moteur.renommageProjet(chemin, source, offset)
        return formesNegociees(uri, chemin, parFichier.mapValues { (_, occurrences) -> moteur.editionsRenommage(nouveauNom, occurrences) })
    }

    /**
     * La construction des FORMES NÉGOCIÉES (changes héritée ou
     * documentChanges versionnée) d'une map fichier → éditions : le
     * document courant voyage sous SON URI, les autres sous
     * `file://<chemin>` (convention du balayage).
     */
    private fun formesNegociees(
        uri: String,
        chemin: String,
        editionsParFichier: Map<String, List<EditionTexte>>,
    ): EditionEspaceDeTravail {
        val cheminCourant = cheminDeUri(uri)
        if (!capacites.documentChanges) {
            // Forme héritée : URI → éditions (le client cel-lsp).
            val changes = LinkedHashMap<String, List<EditionTexte>>()
            for ((fichier, editions) in editionsParFichier) {
                val uriFichier = if (fichier == cheminCourant) uri else uriDeFichier(fichier)
                changes[uriFichier] = editions
            }
            return EditionEspaceDeTravail.formeChanges(changes)
        }

        // Forme versionnée : TextDocumentEdit par document, version
        // COURANTE du document ouvert (celle du didOpen), null sinon.
        val documentChanges = ArrayList<EditionDocumentVersionnee>()
        for ((fichier, editions) in editionsParFichier) {
            val uriFichier = if (fichier == cheminCourant) uri else uriDeFichier(fichier)
            documentChanges.add(
                EditionDocumentVersionnee(
                    uri = uriFichier,
                    version = versions[uriFichier],
                    editions = editions,
                ),
            )
        }
        return EditionEspaceDeTravail.formeDocumentChanges(documentChanges)
    }

    /**
     * `textDocument/prepareRename` D'UN DOCUMENT XML (complément de
     * catalogue — ADR 0032, « rename depuis un XML ») : la ressource au
     * caret (déclaration `name="…"` des values ou référence
     * `@&lt;type&gt;/&lt;nom&gt;`, `@+&lt;type&gt;/&lt;nom&gt;`,
     * `?&lt;type&gt;/&lt;nom&gt;`, `R.&lt;type&gt;.&lt;nom&gt;` du data binding) se
     * prépare comme depuis le code Java — placeholder EXACT, plage EXACTE
     * du segment `&lt;nom&gt;` ; un refus honnête (fichier, styleable)
     * ferme la boîte de dialogue.
     *
     * @param chemin le chemin DISQUE du document XML du module.
     * @param texte le texte XML complet (tampon ouvert).
     * @param offset la position du caret.
     * @return la préparation { plage, placeholder }, ou null.
     */
    public fun preparerRenommageXml(chemin: String, texte: String, offset: Int): PreparationRenommage? {
        val moteur = moteur(chemin) ?: return null
        moteur.cibleRessourceXmlA(chemin, texte, offset)?.let { ressource ->
            if (ressource.refus != null) return null
            return PreparationRenommage(ressource.jeton, ressource.nom)
        }
        return null
    }

    /**
     * `textDocument/rename` D'UN DOCUMENT XML (ADR 0032) : la MÊME
     * propagation multi-fichiers que depuis le code Java (code Java par
     * bindings + XML du module), le document XML courant servi depuis son
     * TAMPON OUVERT — sous la forme NÉGOCIÉE (changes ou documentChanges
     * versionnée, jamais les deux).
     *
     * @param uri l'URI du document XML ouvert côté client.
     * @param chemin le chemin DISQUE du document XML du module.
     * @param texte le texte XML complet (tampon ouvert).
     * @param offset la position du caret.
     * @param nouveauNom le nouveau nom.
     * @return l'édition d'espace de travail (forme négociée).
     * @throws RenommageSansCible si aucune ressource renommable ne vit au
     *   caret (erreur typée — jamais une édition vide silencieuse).
     */
    public fun renommageXml(
        uri: String,
        chemin: String,
        texte: String,
        offset: Int,
        nouveauNom: String,
    ): EditionEspaceDeTravail {
        val moteur = moteur(chemin) ?: throw RenommageSansCible(positionSource(texte, offset))
        val cible = moteur.cibleRessourceXmlA(chemin, texte, offset)
        if (cible == null || cible.refus != null) {
            throw RenommageSansCible(positionSource(texte, offset))
        }
        val parFichier = moteur.renommageRessourceXml(chemin, texte, offset, nouveauNom)
        if (parFichier.isEmpty()) {
            throw RenommageSansCible(positionSource(texte, offset))
        }
        return formesNegociees(uri, chemin, parFichier.mapValues { (_, editions) -> editions })
    }

    /**
     * Les COLLISIONS typées du renommage DEPUIS UN DOCUMENT XML (le
     * référentiel reste la source de vérité).
     *
     * @param chemin le chemin DISQUE du document XML du module.
     * @param texte le texte XML complet (tampon ouvert).
     * @param offset la position du caret.
     * @param nouveauNom le nouveau nom proposé.
     * @return les collisions typées (vide = renommage sûr).
     */
    public fun conflitsXml(
        chemin: String,
        texte: String,
        offset: Int,
        nouveauNom: String,
    ): List<ConflitRenommage> {
        val moteur = moteur(chemin) ?: return emptyList()
        return moteur.conflitsRessourceXml(chemin, texte, offset, nouveauNom)
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
    ): List<ConflitRenommage> {
        val moteur = moteur(chemin) ?: return emptyList()
        // V-D3 (G-028) : les collisions typées d'une RESSOURCE (identifiant
        // invalide, nom déjà pris du même type — le référentiel tranche).
        val cibleRessource = moteur.cibleRessourceA(chemin, source, offset)
        if (cibleRessource != null) {
            return moteur.conflitsRessource(chemin, source, offset, nouveauNom)
        }
        return moteur.conflitsRenommage(chemin, source, offset, nouveauNom, suivreOverrides)
    }

    /**
     * L'APERÇU du renommage (preview = résultat, ADR 0014) : la source
     * d'UN fichier après application de ses éditions — la fonction est
     * PURE (application d'édits), le moteur n'y est que porteur : le
     * régime vivant résout le moteur DÉFAUT (toujours construisible),
     * l'aperçu est RENDU TEL QUEL si aucun moteur n'existe (honnête,
     * jamais une exception sur une fonction pure).
     *
     * @param source la source originale du fichier.
     * @param editions les éditions du renommage pour CE fichier.
     * @return la source previewée.
     */
    public fun apercu(source: String, editions: List<EditionTexte>): String =
        moteur("")?.apercuRenommage(source, editions) ?: source

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
        moteur(chemin)?.cibleRenommageA(chemin, source, offset)

    /**
     * La RESSOURCE au caret (V-D3 — G-028) : type R, nom, jeton et motif
     * de refus honnête — le diagnostic client du rename de ressource.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @param offset la position du caret.
     * @return la cible ressource, ou null (caret hors R.<type>.<nom>).
     */
    public fun cibleRessource(chemin: String, source: String, offset: Int): jo.codeide.lsp.java.renommage.RenommageRessources.CibleRessource? =
        moteur(chemin)?.cibleRessourceA(chemin, source, offset)

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

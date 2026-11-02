package jo.codeide.lsp.java.outils

/**
 * Édition de texte exprimée en offsets de la source ORIGINALE (étage N7 —
 * cahier fiches 016 et 029) : le substrat commun du formatage de plage et
 * des éditions additionnelles de complétion.
 *
 * Contrat transverse (fiche 016, note de réécriture) : une liste d'éditions
 * voyage en ordre CROISSANT d'offset et ses plages sont DISJOINTES — c'est
 * ce qui permet au client LSP de les appliquer en ordre DÉCROISSANT sans
 * invalider les offsets amont. La plage principale d'un item de complétion
 * et ses éditions additionnelles obéissent à la même interdiction de
 * chevauchement (fiche 029, contrainte LSP).
 *
 * @property offset le début de la plage remplacée, dans la source originale.
 * @property longueur la longueur de la plage remplacée (0 = insertion
 *   pure — p. ex. l'insertion d'un import à l'ancre, fiche 029).
 * @property texte le texte de remplacement ("" = suppression pure — p. ex.
 *   la suppression du récepteur d'une template postfixe).
 */
public data class EditionTexte(
    public val offset: Int,
    public val longueur: Int,
    public val texte: String,
)

/**
 * Application d'une liste d'éditions à une source (fiche 016 : le client
 * LSP applique en ordre DÉCROISSANT d'offset — les offsets amont restent
 * valides parce que les éditions sont disjointes).
 *
 * @param source la source originale.
 * @param editions les éditions (n'importe quel ordre — triées ici).
 * @return le texte résultat de l'application de toutes les éditions.
 */
public fun appliquerEditions(source: String, editions: List<EditionTexte>): String {
    val sb = StringBuilder(source)
    // Ordre DÉCROISSANT : chaque remplacement laisse intacts les offsets
    // des éditions situées en amont.
    for (edition in editions.sortedByDescending { it.offset }) {
        val fin = (edition.offset + edition.longueur).coerceAtMost(sb.length)
        sb.replace(edition.offset.coerceAtMost(sb.length), fin, edition.texte)
    }
    return sb.toString()
}

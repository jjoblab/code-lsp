package jo.codeide.lsp.java.outils

/**
 * Source focalisé — résultat de la réduction [SourceFocuser].
 *
 * Contrat de surface (fiche 041) : [source] (réduite), [offset] (position du
 * caret dans la source réduite) et [mapBack] (traduction inverse réduite →
 * originale). L'aller-retour du caret est garanti :
 * `mapBack(offset())` rend EXACTEMENT l'offset original.
 */
public class Focused internal constructor(
    private val sourceOriginale: String,
    private val offsetOriginal: Int,
    private val sourceReduite: String,
    private val offsetReduit: Int,
    private val coupes: List<Coupe>,
) {

    /** Source réduite (identique à l'originale si aucune réduction n'a eu lieu). */
    public fun source(): String = sourceReduite

    /** Position du caret dans la source réduite. */
    public fun offset(): Int = offsetReduit

    /**
     * Traduit une position de la source réduite vers la source originale.
     *
     * Règles (contrat fiche 041) :
     *  - position AVANT une coupe : identique (aucun décalage amont) ;
     *  - position DANS le jeton inséré `{}` (y compris sur son `}`) : position
     *    de REPRISE — juste après l'accolade fermante du corps original ;
     *  - position APRÈS les coupes : restituée à sa position originale ;
     *  - fin de la source réduite → fin de la source originale ;
     *  - source non réduite : identité partout.
     *
     * @param position position dans la source réduite.
     * @return la position correspondante dans la source originale.
     */
    public fun mapBack(position: Int): Int {
        var resultat = position
        for (coupe in coupes) {
            val debutJeton = coupe.positionReduite // position du `{` du jeton `{}`
            if (position <= debutJeton) break // avant cette coupe : aucun décalage amont
            if (position <= debutJeton + 1) {
                // Dans le jeton inséré : reprise juste après le `}` du corps original.
                return coupe.finOriginale + 1
            }
            resultat += coupe.longueurOriginale
        }
        return resultat.coerceAtMost(sourceOriginale.length)
    }

    /**
     * Traduit une position de la source originale vers la source réduite
     * (sens direct, utilisé par le moteur pour interroger l'arbre résolu sur
     * la source réduite avec un offset d'origine).
     *
     * @param position position dans la source originale.
     * @return la position correspondante dans la source réduite.
     */
    public fun mapAvant(position: Int): Int {
        var resultat = position
        for (coupe in coupes) {
            if (position <= coupe.debutOriginale) break // avant la coupe : identique
            if (position < coupe.finOriginale) {
                // À l'intérieur d'un corps effacé : clamp au jeton inséré.
                return coupe.positionReduite + 1
            }
            resultat -= coupe.longueurOriginale
        }
        return resultat.coerceAtMost(sourceReduite.length)
    }

    /** Une coupe : intérieur d'un corps de méthode remplacé par le jeton `{}`. */
    internal class Coupe(
        val debutOriginale: Int, // premier caractère de l'intérieur effacé (après `{`)
        val finOriginale: Int, // accolade fermante du corps original (exclue de l'effacement)
        val positionReduite: Int, // position du `{` du jeton `{}` dans la source réduite
    ) {
        val longueurOriginale: Int get() = finOriginale - debutOriginale
    }
}

/**
 * Focalisation de source (étage N1, cahier fiche 041) — technique de
 * réduction à la java-language-server.
 *
 * Réduire un gros source en effaçant les CORPS des méthodes hors du caret
 * (remplacés par le bloc vide `{}`), pour accélérer les opérations de
 * binding, tout en préservant :
 *  - intégralement la méthode contenant l'offset (le « corps hôte ») ;
 *  - les SIGNATURES de toutes les méthodes (résolution de surcharges et
 *    d'héritage conservée) ;
 *  - tout ce qui n'est pas un corps de méthode (champs, initialiseurs,
 *    types imbriqués, imports).
 *
 * L'appariement d'accolades est insensible aux accolades parasites des
 * chaînes et commentaires (délégué à [ScannerCorps]). En dessous du seuil
 * de 2 000 caractères, ou lorsque l'offset est hors de toute méthode, la
 * source est rendue INCHANGÉE (réduction = identité).
 */
public object SourceFocuser {

    /** Seuil d'activation de la réduction (contrat observable de la fiche 041). */
    public const val SEUIL_REDUCTION: Int = 2_000

    /**
     * Focalise le source autour de l'offset, sans translation.
     *
     * @param source le source Java complet.
     * @param offset offset du caret (position d'intérêt) dans le source.
     * @return le [Focused] (source réduite ou identité).
     */
    @JvmStatic
    public fun focus(source: String, offset: Int): Focused = focusAndMap(source, offset)

    /**
     * Focalise le source autour de l'offset et rend la traduction inverse.
     *
     * @param source le source Java complet.
     * @param offset offset du caret dans le source original.
     * @return le [Focused] portant la source réduite, l'offset réduit et
     *   [Focused.mapBack].
     */
    @JvmStatic
    public fun focusAndMap(source: String, offset: Int): Focused {
        if (source.length < SEUIL_REDUCTION) {
            return Focused(source, offset, source, offset, emptyList())
        }
        val corps = ScannerCorps.corpsDeMethodes(source) ?: return Focused(source, offset, source, offset, emptyList())
        // Corps hôte = le PLUS EXTERIEUR contenant l'offset (une méthode locale
        // imbriquée vit dans le texte du corps englobant : c'est lui qu'il faut
        // préserver intégralement).
        val hote = corps.firstOrNull { it.contient(offset) } ?: return Focused(source, offset, source, offset, emptyList())

        // Effacer l'intérieur de chaque corps SAUF le corps hôte.
        val coupes = mutableListOf<Focused.Coupe>()
        val constructeur = StringBuilder(source.length)
        var curseur = 0
        var frontiereCoupePrecedente = -1 // fin (exclusive) de la dernière coupe posée
        for (plage in corps) {
            if (plage === hote || plage.debutInterieur >= plage.finInterieur) continue
            if (plage.debutInterieur < frontiereCoupePrecedente) continue // corps imbriqué déjà couvert
            constructeur.append(source, curseur, plage.debutInterieur)
            val positionReduite = constructeur.length - 1 // position du `{` qui précède
            // Jeton inséré : le bloc vide `{}` — l'intérieur est simplement omis.
            coupes.add(Focused.Coupe(plage.debutInterieur, plage.finInterieur, positionReduite))
            frontiereCoupePrecedente = plage.finInterieur
            curseur = plage.finInterieur
        }
        constructeur.append(source, curseur, source.length)
        val reduite = constructeur.toString()
        // Traduction du caret : le corps hôte est intact, le décalage est purement amont.
        var offsetReduit = offset
        for (coupe in coupes) {
            if (coupe.debutOriginale < offset) {
                offsetReduit -= coupe.longueurOriginale
            }
        }
        return Focused(source, offset, reduite, offsetReduit, coupes)
    }
}

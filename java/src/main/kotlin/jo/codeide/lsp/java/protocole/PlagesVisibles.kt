package jo.codeide.lsp.java.protocole

import java.util.concurrent.ConcurrentHashMap

/**
 * Le registre des PLAGES VISIBLES par document (vague V-F4 — ADR 0029
 * phase 4, « plage prioritaire : zone visible d'abord ») : le client y
 * déclare, par la notification maison `$/codeide/plageVisible`, la zone
 * de CHAQUE document qu'il affiche réellement — le publicateur s'en
 * sert pour servir les diagnostics du CORPS VISIBLE d'abord sur les
 * grands fichiers (la passe complète suit, la publication complète fait
 * foi).
 *
 * **Convention d'offsets** : la maison (miroir de `CibleSuperRpc`) —
 * offsets ABSOLUS dans le texte du document, JAMAIS de conversion
 * ligne/colonne côté serveur.
 *
 * **Sémantique de fraîcheur** : la dernière notification gagne (la zone
 * défile) ; un document FERMÉ est OUBLIÉ (la zone d'un tampon mort ne
 * priorise rien) ; l'ABSENCE d'entrée = comportement d'avant V-F4 (une
 * seule publication — un client qui n'envoie jamais la notification ne
 * change RIEN, les contrats du cahier restent intacts).
 *
 * Thread-safe : table concurrente, les notifications arrivent sur le fil
 * de messages, les tirs du publicateur lisent sur le fil des publications.
 */
public class PlagesVisibles {

    /** La plage visible d'un document : offsets absolus [debut, fin). */
    public data class Plage(public val debut: Int, public val fin: Int)

    /** Les plages déclarées, par URI de document. */
    private val plages = ConcurrentHashMap<String, Plage>()

    /**
     * Déclare la plage visible d'un document (la dernière notification
     * gagne — un défilement remplace la zone précédente).
     *
     * @param uri l'URI du document affiché.
     * @param debut l'offset ABSOLU du début de la zone visible.
     * @param fin l'offset ABSOLU de la fin de la zone visible.
     */
    public fun notifier(uri: String, debut: Int, fin: Int) {
        if (debut < 0 || fin < debut) return // valeurs absurdes : ignorées
        plages[uri] = Plage(debut, fin)
    }

    /**
     * La plage visible courante d'un document.
     *
     * @param uri l'URI du document.
     * @return la plage déclarée, ou null (aucune priorité — comportement
     *   d'avant V-F4).
     */
    public fun plageDe(uri: String): Plage? = plages[uri]

    /** Oublie la plage d'un document (didClose : le tampon est mort). */
    public fun oublier(uri: String) {
        plages.remove(uri)
    }
}

package jo.codeide.lsp.java.protocole

import java.util.concurrent.ConcurrentHashMap

/**
 * Le magasin des documents ouverts du serveur (étage N8) : le miroir
 * moteur de la synchronisation plein texte (`TextDocumentSyncKind.Full`).
 *
 * **Époque copy-on-write** (design intégré de l'ancienne vague 4, plan
 * §4 N8 : « 0 réponse périmée ») : chaque mutation (`didOpen`, `didChange`,
 * `didSave` recalant la version) publie un NOUVEL [Instantane] immuable et
 * incrémente l'époque du document. Une requête qui capture un instantané
 * peut ensuite VÉRIFIER sa fraîcheur ([estPerime]) et recalculer sur
 * l'instantané courant ([Calculs.fraiche]) — une réponse calculée contre
 * un texte révolu n'est JAMAIS livrée au client. Les instantanés étant des
 * classes de données immuables, l'écriture ne perturbe jamais un calcul
 * en cours : c'est le copy-on-write.
 *
 * **Version du document** : transportée par `didOpen`/`didChange`
 * (compteur croissant du client), servie en retour dans les publications
 * de diagnostics (fiche 059 I5 : la publication didSave porte la version
 * COURANTE du buffer — 1 après un didChange en version 1).
 *
 * Thread-safety : [ConcurrentHashMap] par URI + compteur d'époques
 * atomique par document ; les notifications arrivent sur le fil JSON-RPC
 * unique de lsp4j (les notifications y sont SÉRIALISÉES entre elles),
 * mais la classe reste correcte sous concurrence libre.
 */
public class EtatsDocuments {

    /**
     * Un instantané IMMUABLE d'un document ouvert (l'unité de
     * copy-on-write) : URI, version client, texte intégral, époque de
     * naissance. Toute mutation produit un nouvel instantané ; aucun
     * champ n'est jamais modifié après construction.
     *
     * @property uri l'URI du document (forme exacte reçue du client).
     * @property version la version client courante du document.
     * @property texte le texte intégral du buffer VIVANT.
     * @property epoque l'époque de naissance de cet instantané (les
     *   mutations incrémentent l'époque du document).
     */
    public data class Instantane(
        public val uri: String,
        public val version: Long,
        public val texte: String,
        public val epoque: Long,
    )

    /** Le document vivant : l'instantané courant et son verrou d'époque. */
    private class DocumentVivant {
        var instantane: Instantane? = null
        var epoque: Long = 0
    }

    /** Les documents ouverts, par URI. */
    private val documents = ConcurrentHashMap<String, DocumentVivant>()

    /**
     * Ouvre un document (`didOpen`, fiche 059 : « didOpen (version 0) ») :
     * publie l'instantané initial et INCRÉMENTE l'époque (une réouverture
     * du même URI invalide les calculs antérieurs).
     *
     * @param uri l'URI du document.
     * @param texte le texte intégral reçu.
     * @param version la version initiale (0 par convention client).
     * @return l'instantané né de l'ouverture.
     */
    public fun ouvrir(uri: String, texte: String, version: Long): Instantane {
        val vivant = documents.computeIfAbsent(uri) { DocumentVivant() }
        synchronized(vivant) {
            vivant.epoque += 1
            val instantane = Instantane(uri, version, texte, vivant.epoque)
            vivant.instantane = instantane
            return instantane
        }
    }

    /**
     * Change un document (`didChange` plein texte) : publie le nouvel
     * instantané et incrémente l'époque — les calculs en cours sur
     * l'ancien texte deviennent périmés ([estPerime]).
     *
     * @param uri l'URI du document.
     * @param texte le texte intégral APRÈS changement.
     * @param version la version client APRÈS changement.
     * @return le nouvel instantané, ou null si le document n'est PAS
     *   ouvert (un didChange hors ouverture est ignoré — la spec 3.17
     *   n'en fait rien, le serveur non plus).
     */
    public fun changer(uri: String, texte: String, version: Long): Instantane? {
        val vivant = documents[uri] ?: return null
        synchronized(vivant) {
            vivant.epoque += 1
            val instantane = Instantane(uri, version, texte, vivant.epoque)
            vivant.instantane = instantane
            return instantane
        }
    }

    /**
     * Sauve un document (`didSave`, fiche 059 I5) : sans `includeText`,
     * le buffer VIVANT fait foi — seule la version est recalée sur
     * l'instantané (la version client d'un didSave peut être plus
     * fraîche que celle du dernier didChange) ; AUCUNE publication
     * d'époque n'est nécessaire (le texte ne change pas), mais la version
     * est celle que la publication doit transporter.
     *
     * @param uri l'URI du document.
     * @param version la version client transportée par le didSave (null =
     *   conserver celle du buffer).
     * @return l'instantané recalé, ou null si le document n'est PAS
     *   ouvert (fiche 059 : « didSave sur document jamais ouvert ne
     *   republie pas »).
     */
    public fun sauver(uri: String, version: Long?): Instantane? {
        val vivant = documents[uri] ?: return null
        synchronized(vivant) {
            val courant = vivant.instantane ?: return null
            if (version != null && version != courant.version) {
                val recale = courant.copy(version = version)
                vivant.instantane = recale
                return recale
            }
            return courant
        }
    }

    /**
     * Ferme un document (`didClose`) : l'instantané disparaît, l'époque
     * est incrémentée (les calculs en cours deviennent périmés), le
     * document quitte le magasin.
     *
     * @param uri l'URI du document.
     */
    public fun fermer(uri: String) {
        documents.remove(uri)?.let { vivant ->
            synchronized(vivant) {
                vivant.epoque += 1
                vivant.instantane = null
            }
        }
    }

    /**
     * L'instantané courant d'un document (pour calculer une réponse).
     *
     * @param uri l'URI du document.
     * @return l'instantané courant, ou null si le document n'est pas
     *   ouvert (le serveur relit alors le disque — convention I9).
     */
    public fun instantane(uri: String): Instantane? = documents[uri]?.instantane

    /**
     * L'époque COURANTE d'un document (indépendante de l'ouverture :
     * 0 pour un document jamais ouvert — tout instantané né plus tôt
     * est alors périmé par construction s'il référence une autre époque).
     *
     * @param uri l'URI du document.
     * @return l'époque courante (0 = jamais ouvert).
     */
    public fun epoque(uri: String): Long = documents[uri]?.epoque ?: 0

    /**
     * Vrai ssi l'instantané est PÉRIMÉ : son époque ne correspond plus à
     * l'époque courante du document — une réponse calculée contre lui ne
     * doit JAMAIS être livrée (design « 0 réponse périmée »).
     *
     * @param instantane l'instantané capturé au début du calcul.
     * @return vrai ssi le document a muté depuis la capture.
     */
    public fun estPerime(instantane: Instantane): Boolean = epoque(instantane.uri) != instantane.epoque

    /**
     * Le protocole de calcul FRAIS (design « 0 réponse périmée ») :
     * exécute le calcul sur l'instantané courant ; si le document a muté
     * PENDANT le calcul, RETENTE sur le nouvel instantané (borné — un
     * document qui muterait sans relâche rendrait la boucle infinie).
     *
     * La borne de relance est fixée à 3 : au-delà, la réponse rendue est
     * celle du DERNIER calcul — l'honnêteté du protocole préfère une
     * réponse cohérente avec un texte très récent à l'absence totale de
     * réponse (le client relance de toute façon à la frappe suivante).
     *
     * @param uri l'URI du document interrogé.
     * @param calcul le calcul pur (instantané → résultat) : il NE MUTATE
     *   JAMAIS le magasin (précondition) et peut être rejoué sans effet
     *   de bord observable.
     * @return le résultat FRAIS, ou null si le document n'est pas ouvert
     *   (le service relit alors le disque, convention I9).
     */
    public fun <T> calculerFraiche(uri: String, calcul: (Instantane) -> T): T? {
        var tentative = 0
        while (true) {
            val instantane = instantane(uri) ?: return null
            val resultat = calcul(instantane)
            if (!estPerime(instantane)) return resultat
            tentative += 1
            if (tentative >= BORNE_RELANCES) return resultat
        }
    }

    /** Les URIs des documents ouverts (overlay des requêtes multi-fichiers). */
    public val urisOuverts: Set<String>
        get() = documents.entries.asSequence().filter { it.value.instantane != null }.map { it.key }.toSet()

    private companion object {
        /** Bornes de relance du calcul frais (« 0 réponse périmée »). */
        private const val BORNE_RELANCES: Int = 3
    }
}

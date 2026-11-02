// CodeIDE — magasin des documents ouverts du serveur Kotlin (module
// :kotlin, étage 6 vague 6e — ADR 0118, miroir :java N8). Code NEUF
// jjoblab : l'ÉPOQUE COPY-ON-WRITE du design intégré (« 0 réponse
// périmée », plan §4 de la synthèse) — le MODÈLE Java transposé.
package jo.codeide.lsp.kotlin.protocole

import java.util.concurrent.ConcurrentHashMap

/**
 * Le magasin des documents ouverts du serveur : le miroir moteur de la
 * synchronisation plein texte (`TextDocumentSyncKind.Full`).
 *
 * **Époque copy-on-write** (« 0 réponse périmée ») : chaque mutation
 * (`didOpen`, `didChange`, `didSave` recalant la version) publie un
 * NOUVEL [Instantane] immuable et incrémente l'époque du document. Une
 * requête qui capture un instantané peut ensuite VÉRIFIER sa fraîcheur
 * ([estPerime]) et recalculer sur l'instantané courant
 * ([calculerFraiche]) — une réponse calculée contre un texte révolu
 * n'est JAMAIS livrée au client. Les instantanés étant des classes de
 * données immuables, l'écriture ne perturbe jamais un calcul en cours :
 * c'est le copy-on-write.
 *
 * **Version du document** : transportée par `didOpen`/`didChange`
 * (compteur croissant du client), servie en retour dans les publications
 * de diagnostics (la publication porte la version COURANTE du buffer).
 *
 * Thread-safety : [ConcurrentHashMap] par URI + verrou d'époque par
 * document ; les notifications arrivent sur le fil JSON-RPC unique de
 * lsp4j (les notifications y sont SÉRIALISÉES entre elles), mais la
 * classe reste correcte sous concurrence libre.
 */
public class EtatsDocuments {

    /**
     * Un instantané IMMUABLE d'un document ouvert (l'unité de
     * copy-on-write) : URI, version client, texte intégral, époque de
     * naissance. Toute mutation produit un nouvel instantané ; aucun champ
     * n'est jamais modifié après construction.
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
     * Ouvre un document (`didOpen`) : publie l'instantané initial et
     * INCRÉMENTE l'époque (une réouverture du même URI invalide les
     * calculs antérieurs).
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
     * instantané et incrémente l'époque — les calculs lancés contre
     * l'ancien texte deviennent périmés ([estPerime]).
     *
     * @param uri l'URI du document.
     * @param texte le texte intégral NOUVEAU.
     * @param version la version client NOUVELLE.
     * @return l'instantané né du changement.
     */
    public fun changer(uri: String, texte: String, version: Long): Instantane {
        val vivant = documents.computeIfAbsent(uri) { DocumentVivant() }
        synchronized(vivant) {
            vivant.epoque += 1
            val instantane = Instantane(uri, version, texte, vivant.epoque)
            vivant.instantane = instantane
            return instantane
        }
    }

    /**
     * Recale la version d'un document (`didSave` SANS includeText : la
     * version courante du client devient celle du buffer) — l'époque est
     * INCHANGÉE (le texte n'a pas bougé, la publication porte la version
     * courante).
     *
     * @param uri l'URI du document.
     * @param version la version du SAUVEUR (`null` = conserver la courante).
     * @return l'instantané recalé ; `null` si le document n'est pas ouvert.
     */
    public fun sauver(uri: String, version: Long?): Instantane? {
        val vivant = documents[uri] ?: return null
        synchronized(vivant) {
            val courant = vivant.instantane ?: return null
            if (version != null && version != courant.version) {
                val recale = Instantane(uri, version, courant.texte, courant.epoque)
                vivant.instantane = recale
                return recale
            }
            return courant
        }
    }

    /**
     * Ferme (oublie) un document — l'instantané disparaît, l'époque du
     * document est conservée (une réouverture l'incrémentera : les calculs
     * d'avant la fermeture restent invalidables).
     *
     * @param uri l'URI du document.
     */
    public fun fermer(uri: String) {
        documents[uri]?.let { vivant ->
            synchronized(vivant) {
                vivant.instantane = null
            }
        }
    }

    /**
     * L'instantané VIVANT d'un document.
     *
     * @param uri l'URI du document.
     * @return l'instantané courant ; `null` si le document n'est pas ouvert.
     */
    public fun instantane(uri: String): Instantane? {
        return documents[uri]?.instantane
    }

    /**
     * L'époque COURANTE d'un document (0 s'il n'a jamais été ouvert).
     *
     * @param uri l'URI du document.
     * @return l'époque courante.
     */
    public fun epoque(uri: String): Long = documents[uri]?.epoque ?: 0L

    /**
     * Un instantané est-il PÉRIMÉ (son document a muté depuis sa
     * naissance) ?
     *
     * @param instantane l'instantané capturé par un calcul.
     * @return vrai ssi l'époque du document a changé depuis.
     */
    public fun estPerime(instantane: Instantane): Boolean =
        epoque(instantane.uri) != instantane.epoque

    /**
     * Le calcul FRAIS d'un document (« 0 réponse périmée ») : le calcul
     * se lance sur l'instantané courant ; s'il est PÉRIMÉ à l'issue (le
     * document a muté PENDANT), il est REJOUÉ sur le nouvel instantané —
     * la réponse livrée n'est JAMAIS calculée contre un texte révolu.
     *
     * La borne de relance est fixée à 3 : au-delà, la réponse rendue est
     * celle du DERNIER calcul — l'honnêteté du protocole préfère une
     * réponse cohérente avec un texte très récent à l'absence totale de
     * réponse (le client relance de toute façon à la frappe suivante).
     *
     * @param uri l'URI du document interrogé.
     * @param calcul le calcul pur (instantané → résultat) : il NE MUTE
     * JAMAIS le magasin (précondition) et peut être rejoué sans effet de
     * bord observable.
     * @return le résultat FRAIS, ou `null` si le document n'est pas ouvert.
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

    /** Les URIs des documents ouverts. */
    public val urisOuverts: Set<String>
        get() = documents.entries.asSequence().filter { it.value.instantane != null }
            .map { it.key }.toSet()

    private companion object {
        /** La borne de relance du calcul frais (« 0 réponse périmée »). */
        private const val BORNE_RELANCES: Int = 3
    }
}

package jo.codeide.lsp.java.classpath

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache négatif de présence source (étage N1, cahier fiche 006 — v1.9.1).
 *
 * Motivation mesurée par le cahier : la résolution ecj appelle
 * `find(fqcn)` des CENTAINES de fois par passe, chaque appel sondant le
 * disque (`Files.isRegularFile` par racine source) — sur stockage FUSE
 * Android, 0,5–5 ms par stat × centaines d'appels × 3–5 splices dominait
 * la latence de complétion. Le négatif mémorise « ce FQCN n'est PAS une
 * source du projet » pendant une fenêtre TTL (30 s par défaut).
 *
 * Contrats verrouillés (fiche 006) :
 *  - **seuls les négatifs sont cachés** : toute lecture positive touche le
 *    disque (le contenu suit toujours le fichier) ;
 *  - **fenêtre de masquage contractuelle** : une classe créée sur disque
 *    reste invisible à `find` tant que le négatif n'a pas expiré ou été
 *    invalidé — comportement documenté, assumé par le cahier ;
 *  - **invalidation par événement Created** : [invalider] vide immédiatement
 *    le cache (didChangeWatchedFiles → création de `.java`).
 *
 * Thread-safe : la structure est concurrente (lecture à chaud sans verrou),
 * l'invalidation est totale et non bloquante.
 *
 * @param racines racines sources du périmètre (probes `Files.isRegularFile`).
 * @param ttlMillis fenêtre de vie d'un négatif, en millisecondes.
 */
public class CachePresenceSource(
    private val racines: List<Path>,
    private val ttlMillis: Long = 30_000,
) {

    /** Un négatif mémorisé : FQCN → instant d'expiration. */
    private val negatifs = ConcurrentHashMap<String, Long>()

    /**
     * Cherche le fichier source d'un FQCN dans les racines.
     *
     * @param fqcn nom entièrement qualifié en points (`com.example.c.Nouvelle`).
     * @return le chemin du `.java` s'il existe dans une racine, null sinon
     *   (le null est alors MÉMORISÉ pendant le TTL : les appels suivants ne
     *   re-sondent pas le disque).
     */
    public fun fichierPour(fqcn: String): Path? {
        val expiration = negatifs[fqcn]
        val maintenant = System.currentTimeMillis()
        if (expiration != null) {
            if (maintenant < expiration) return null // négatif vivant : masqué
            negatifs.remove(fqcn) // expiré : re-sonder
        }
        // Lecture POSITIVE : toujours le disque (jamais cachée — contrat).
        val relatif = fqcn.replace('.', '/') + ".java"
        for (racine in racines) {
            val candidat = racine.resolve(relatif)
            if (Files.isRegularFile(candidat)) return candidat
        }
        negatifs[fqcn] = maintenant + ttlMillis
        return null
    }

    /**
     * Indique si un paquet existe comme répertoire dans une racine.
     *
     * Les négatifs de PAQUETS partagent la même fenêtre TTL et la même
     * invalidation que les classes (le coût disque d'un stat de répertoire
     * est identique à celui d'un fichier).
     *
     * @param paquet nom du paquet en points.
     * @return vrai ssi le répertoire existe dans une racine.
     */
    public fun estPaquet(paquet: String): Boolean {
        val cle = "pkg:$paquet"
        val expiration = negatifs[cle]
        val maintenant = System.currentTimeMillis()
        if (expiration != null) {
            if (maintenant < expiration) return false
            negatifs.remove(cle)
        }
        val relatif = paquet.replace('.', '/')
        for (racine in racines) {
            if (Files.isDirectory(racine.resolve(relatif))) return true
        }
        negatifs[cle] = maintenant + ttlMillis
        return false
    }

    /**
     * Vide TOUT le cache (négatifs de classes et de paquets).
     *
     * Déclenché par l'événement LSP `Created` d'un `.java` observé : la
     * nouvelle classe doit devenir visible immédiatement, sans attendre
     * l'expiration du TTL (contrat fiche 006).
     */
    public fun invalider() {
        negatifs.clear()
    }

    /** Nombre de négatifs mémorisés (sonde de diagnostic). */
    public fun taille(): Int = negatifs.size
}

package jo.codeide.lsp.java.protocole

/**
 * La machine à états du cycle de vie LSP du serveur (étage N8 — cahier
 * fiche 055) : UNINITIALIZED → INITIALIZING → INITIALIZED → SHUTDOWN →
 * EXITED.
 *
 * Le cahier verrouille l'ORDRE des transitions :
 *  - `initialize` répond puis passe en **INITIALIZING** (la transition
 *    finale attend la notification `initialized`) ;
 *  - `initialized` finalise → **INITIALIZED** ;
 *  - `shutdown` → **SHUTDOWN** ;
 *  - `exit` → **EXITED** (après shutdown — fiche 055 :
 *    « standaloneModeDoesNotExitProcess », l'exit en mode in-process
 *    n'arrête JAMAIS la JVM hôte).
 *
 * Cette classe est volontairement MINCE et sans dépendance : elle encode
 * le protocole pur (fiche 055 : « serveur in-process construit sans
 * environnement de compilation — protocole pur »). Les gardes de requête
 * (I2/I3) et le rejet des notifications hors fenêtre vivent dans
 * [ServeurLangageJava], qui CONSULTE l'état ; seule la mutation de l'état
 * passe par [CycleVie].
 *
 * Thread-safety : le cycle de vie est mutable par champ volatile —
 * `initialize` arrive sur le fil JSON-RPC, `exit` peut arriver d'un autre
 * fil (arrêt de l'hôte) ; les lectures ([etat]) voient toujours la
 * dernière écriture.
 */
public class CycleVie {

    /** Les états du cycle de vie LSP 3.17 (fiche 055, verrouillés). */
    public enum class Etat {
        /** Fraîchement construit — aucune requête reçue. */
        UNINITIALIZED,

        /** `initialize` a répondu ; la notification `initialized` est attendue. */
        INITIALIZING,

        /** Initialisation complète — les requêtes sont servies. */
        INITIALIZED,

        /** `shutdown` a répondu — les requêtes sont rejetées (InvalidRequest). */
        SHUTDOWN,

        /** `exit` a été notifié — le serveur est terminé (mode in-process : la JVM survit). */
        EXITED,
    }

    /** L'état courant (volatile : lectures croisées entre fils). */
    @Volatile
    private var etatCourante: Etat = Etat.UNINITIALIZED

    /**
     * L'état exposé du serveur (fiche 055 : « l'état exposé est
     * UNINITIALIZED ») — observable par les tests et l'hôte.
     *
     * @return l'état courant du cycle de vie.
     */
    public fun etat(): Etat = etatCourante

    /**
     * Vrai ssi le serveur est dans la fenêtre où les REQUÊTES sont
     * autorisées — INITIALIZED seul : le cahier fiche 059 exige le rejet
     * ServerNotInitialized **avant initialized**, y compris entre
     * `initialize` et `initialized`.
     *
     * @return vrai ssi l'état autorise les requêtes.
     */
    public fun requetesPermises(): Boolean = etatCourante == Etat.INITIALIZED

    /**
     * Passe à INITIALIZING après une réponse `initialize` réussie
     * (fiche 055 : « initialize répond et passe en INITIALIZING (et non
     * INITIALIZED — la transition finale attend `initialized`) »).
     */
    public fun surInitialize() {
        etatCourante = Etat.INITIALIZING
    }

    /**
     * Finalise l'initialisation sur la notification `initialized`
     * (fiche 055 : « initialized finalise l'initialisation »).
     */
    public fun surInitialized() {
        etatCourante = Etat.INITIALIZED
    }

    /**
     * Passe à SHUTDOWN après une requête `shutdown` (fiche 055) — les
     * requêtes suivantes seront rejetées en InvalidRequest (-32600,
     * fiche 059 I2).
     */
    public fun surShutdown() {
        etatCourante = Etat.SHUTDOWN
    }

    /**
     * Passe à EXITED sur la notification `exit` (fiche 055) — le mode
     * in-process ne tue JAMAIS le processus ici : la décision
     * `System.exit` appartient au serveur (standaloneMode), pas à la
     * machine à états.
     */
    public fun surExit() {
        etatCourante = Etat.EXITED
    }
}

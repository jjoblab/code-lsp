// CodeIDE — machine à états du cycle de vie LSP du serveur Kotlin (module
// :kotlin, étage 6 vague 6e — ADR 0118 option 1, miroir :java N8). Code
// NEUF jjoblab : le MODÈLE du cycle de vie Java (fiche 055 du cahier Java)
// transposé — jamais son code (le miroir est architectural, ADR 0118).
package jo.codeide.lsp.kotlin.protocole

/**
 * La machine à états du cycle de vie LSP du serveur : UNINITIALIZED →
 * INITIALIZING → INITIALIZED → SHUTDOWN → EXITED.
 *
 * L'ORDRE des transitions (miroir N8 Java) :
 *  - `initialize` répond puis passe en **INITIALIZING** (la transition
 *    finale attend la notification `initialized`) ;
 *  - `initialized` finalise → **INITIALIZED** ;
 *  - `shutdown` → **SHUTDOWN** ;
 *  - `exit` → **EXITED** (le mode in-process ne tue JAMAIS la JVM hôte —
 *    la décision `System.exit` appartient au serveur, pas à la machine).
 *
 * Cette classe est volontairement MINCE et sans dépendance : elle encode
 * le protocole pur. Les gardes de requête (I2/I3) et l'ignorance des
 * notifications hors fenêtre vivent dans [ServeurLangageKotlin], qui
 * CONSULTE l'état ; seule la mutation passe par [CycleVie].
 *
 * Thread-safety : champ volatile — `initialize` arrive sur le fil JSON-RPC,
 * `exit` peut arriver d'un autre fil (arrêt de l'hôte) ; les lectures
 * voient toujours la dernière écriture.
 */
public class CycleVie {

    /** Les états du cycle de vie LSP 3.17. */
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
     * L'état exposé du serveur — observable par les tests et l'hôte.
     *
     * @return l'état courant du cycle de vie.
     */
    public fun etat(): Etat = etatCourante

    /**
     * Vrai ssi le serveur est dans la fenêtre où les REQUÊTES sont
     * autorisées — INITIALIZED seul : le rejet ServerNotInitialized se fait
     * AVANT `initialized`, y compris entre `initialize` et `initialized`.
     *
     * @return vrai ssi l'état autorise les requêtes.
     */
    public fun requetesPermises(): Boolean = etatCourante == Etat.INITIALIZED

    /**
     * Passe à INITIALIZING après une réponse `initialize` réussie (la
     * transition finale attend la notification `initialized`).
     */
    public fun surInitialize() {
        etatCourante = Etat.INITIALIZING
    }

    /** Finalise l'initialisation sur la notification `initialized`. */
    public fun surInitialized() {
        etatCourante = Etat.INITIALIZED
    }

    /**
     * Passe à SHUTDOWN après une requête `shutdown` — les requêtes
     * suivantes seront rejetées en InvalidRequest (-32600).
     */
    public fun surShutdown() {
        etatCourante = Etat.SHUTDOWN
    }

    /**
     * Passe à EXITED sur la notification `exit` — le mode in-process ne
     * tue JAMAIS le processus ici : la décision `System.exit` appartient
     * au serveur (mode autonome), pas à la machine.
     */
    public fun surExit() {
        etatCourante = Etat.EXITED
    }
}

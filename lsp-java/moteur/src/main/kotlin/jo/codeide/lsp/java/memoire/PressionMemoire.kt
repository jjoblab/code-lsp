package jo.codeide.lsp.java.memoire

/**
 * Les niveaux de PRESSION MÉMOIRE du shim `onTrimMemory` (vague V-F3 —
 * ADR 0029 phase 3, exécution de l'ADR 0011 point 5) : le MIROIR des
 * constantes `ComponentCallbacks2` d'Android.
 *
 * Le moteur n'a AUCUNE dépendance Android (il tourne sur JVM de bureau
 * ET sur ART) : l'hôte Android traduit son appel
 * `ComponentCallbacks2.onTrimMemory(level)` en
 * [jo.codeide.lsp.java.MoteurJava.pressionMemoire] avec le MÊME ENTIER —
 * les valeurs ci-dessous sont celles de la plateforme (documentation
 * Android « ComponentCallbacks2 », consultée pour elles-mêmes), la
 * correspondance est donc IDENTITÉ, pas traduction.
 *
 * Politique engine-side (documentée, cumulative — voir
 * [jo.codeide.lsp.java.MoteurJava.pressionMemoire]) :
 *
 *  - **RUNNING_\*** (5/10/15) et **UI_HIDDEN** (20) : l'application est
 *    au premier plan (l'éditeur vit, l'utilisateur tape) — seuls les
 *    ARBRES RÉSOLUS du cache de parse partent (le morceau le plus lourd,
 *    reconstruit paresseusement à la prochaine résolution) ; le MÉMO de
 *    diagnose (frappe) et les verdicts de localisation RESTENT (la
 *    frappe reste servie au chemin chaud) ;
 *  - **BACKGROUND** (40) / **MODERATE** (60) : le processus est parti en
 *    arrière-plan — en plus du cache de parse, les MÉMOS de diagnose et
 *    les NÉGATIFS de localisation partent (les positifs gardent leurs
 *    handles ouverts : les jars restent lisibles sans re-scan) ;
 *  - **COMPLETE** (80) : le système est sur le point de tuer le
 *    processus — libération INTÉGRALE ([dispose] : environnement, image
 *    jrt, handles ; le moteur reste utilisable, tout se reconstruit
 *    paresseusement).
 *
 * Un niveau INCONNU reste servi sans exception : < 20 → traitement le
 * plus doux (cache de parse seul), ≥ 80 → libération intégrale.
 */
public object NiveauxPressionMemoire {

    /** Processus au premier plan, mémoire modérément pressée. */
    public const val RUNNING_MODERATE: Int = 5

    /** Processus au premier plan, mémoire faiblement pressée. */
    public const val RUNNING_LOW: Int = 10

    /** Processus au premier plan, mémoire critique (le système va tuer). */
    public const val RUNNING_CRITICAL: Int = 15

    /** L'interface n'est plus visible (activity stopped) — session LSP vivante. */
    public const val UI_HIDDEN: Int = 20

    /** Processus en arrière-plan (liste LRU des process). */
    public const val BACKGROUND: Int = 40

    /** Processus en fond de la liste LRU — libération attendue. */
    public const val MODERATE: Int = 60

    /** Le système va tuer le process — libérer tout ce qui est libérable. */
    public const val COMPLETE: Int = 80
}

/**
 * Le rapport d'une pression mémoire relâchée au moteur (vague V-F3) :
 * ce qui a été LIBÉRÉ, exactement — l'hôte journalise ces compteurs
 * (l'observable des logs appareil « caches d'arbre libérés », écarts E4
 * de INTEGRATION-CODEIDE, devient une donnée CHIFFRÉE côté moteur).
 *
 * @property niveau le niveau reçu (miroir [NiveauxPressionMemoire]).
 * @property entreesParseLiberees le nombre d'entrées (arbres résolus)
 *   évacuées du cache de parse.
 * @property memosDiagnoseLiberes le nombre de MÉMOS de diagnose (états
 *   rapides par document) évacués.
 * @property negatifsLocalisationLiberes le nombre de verdicts NÉGATIFS
 *   de localisation (fqcn absents des jars) évacués.
 * @property environnementLibere vrai ssi la libération INTÉGRALE a eu
 *   lieu (niveau COMPLETE : environnement de noms, image jrt, handles).
 */
public data class RapportPressionMemoire(
    public val niveau: Int,
    public val entreesParseLiberees: Int,
    public val memosDiagnoseLiberes: Int,
    public val negatifsLocalisationLiberes: Int,
    public val environnementLibere: Boolean,
) {
    /** Le rapport « rien libéré » (niveau inconnu doux, ou kill-switch). */
    public companion object {
        public fun rien(niveau: Int): RapportPressionMemoire =
            RapportPressionMemoire(niveau, 0, 0, 0, false)
    }
}

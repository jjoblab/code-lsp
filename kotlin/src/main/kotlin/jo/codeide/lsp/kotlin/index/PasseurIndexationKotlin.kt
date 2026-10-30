// CodeIDE — pilote des passages d'indexation Kotlin (module :kotlin, étage
// 1 vague 1d). Code NEUF jjoblab écrit pour code-lsp (contrat : fiche 009
// du cahier docs/cahier-specifications-kotlin/ ; ADR 0211/0213/0214
// anciens dont le CONTRAT observable est repris : une passe par sync sur
// fil démon dédié, séquence d'événements BIBLIOTHEQUES puis SOURCES puis
// PRET/PARTIEL, annulation coopérative, reprise ciblée, no-op chaud).
// INTERNE au module (D6).
package jo.codeide.lsp.kotlin.index

import java.io.Closeable
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger
import jo.codeide.lsp.kotlin.api.BilanDernierPassageKotlin
import jo.codeide.lsp.kotlin.api.BreakdownIndexeurKotlin
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.DureesPhasesKotlin
import jo.codeide.lsp.kotlin.api.EntreeTravailKotlin
import jo.codeide.lsp.kotlin.api.EtatEntreeTravailKotlin
import jo.codeide.lsp.kotlin.api.EvenementIndexationKotlin
import jo.codeide.lsp.kotlin.api.ObservateurIndexationKotlin
import jo.codeide.lsp.kotlin.api.PhaseDetailleeIndexationKotlin
import jo.codeide.lsp.kotlin.api.PhaseIndexationKotlin
import jo.codeide.lsp.kotlin.session.ScanProjetParesseux

/**
 * Le PILOTE des passages d'indexation : UNE fois par sync, sur un fil
 * démon DÉDIÉ, il nourrit l'index des appelables (segments par
 * empreinte), publie les ÉVÉNEMENTS de progression aux observateurs
 * enregistrés, honore l'ANNULATION coopérative (drapeau testé entre les
 * unités — l'acquis est publié) et la REPRISE ciblée des échecs (le cache
 * rend les entrées saines QUASI GRATUITES — seuls les écarts et les
 * pendances sont re-payés).
 *
 * ## Séquence d'événements (ADR 0213 ancien, reprise)
 *
 * CONSTRUCTION/BIBLIOTHEQUES au fil des segments
 * (EN_ATTENTE → ACTIF → REPRIS/TERMINE/ECARTE), puis CONSTRUCTION/SOURCES
 * au fil des fichiers du scan (faits/total — la marche précède le parse,
 * le total est connu), puis le final PRET/PARTIEL (PARTIEL si écarts ou
 * annulation ; `settled=false` après annulation ; breakdown des DEUX
 * indexeurs, durées, bilan paritaire).
 *
 * ## Cohérence concurrente
 *
 * Le passage tourne sur UN fil ; les événements du scan arrivent du fil
 * du scan PENDANT que le pilote l'attend, donc JAMAIS deux émetteurs en
 * même temps sur un même passage. Un observateur FAUTIF est journalisé
 * et ignoré — le passage continue (jamais d'exception vers le moteur).
 *
 * ## Publication
 *
 * Le [publication] callback est invoqué à l'issue de la phase SEGMENTS
 * d'un passage NON PÉRIMÉ : le propriétaire de l'index (le moteur) y
 * publie son snapshot — les sessions branchées voient l'index dès que
 * les segments sont posés, sans attendre le scan des sources.
 *
 * @param index l'index des appelables à nourrir ;
 * @param scan le scanneur paresseux des racines sources ;
 * @param publication le callback de publication du snapshot (défaut :
 *        silence — usages de test).
 */
@Suppress("TooManyFunctions")
internal class PasseurIndexationKotlin(
    private val index: IndexAppelablesKotlin,
    private val scan: ScanProjetParesseux,
    private val publication: (ClasspathKotlin) -> Unit = {},
) {

    /** Le fil UNIQUE des passages (démon — la JVM ne l'attend pas). */
    private val executeur: ExecutorService =
        Executors.newSingleThreadExecutor { tache ->
            Thread(tache, "indexation-kotlin").apply { isDaemon = true }
        }

    /** Les observateurs enregistrés (façades hôte — ADR 0213 ancien). */
    private val observateurs = CopyOnWriteArrayList<ObservateurIndexationKotlin>()

    /** Le dernier classpath poussé par la sync (l'identité du passage). */
    @Volatile
    private var classpathCourant: ClasspathKotlin? = null

    /** La génération courante — un passage PÉRIMÉ s'arrête à la prochaine unité. */
    @Volatile
    private var generation: Long = 0

    /** Un passage est-il en vol (annulation possible) ? */
    @Volatile
    private var enVol: Boolean = false

    /** Annulation demandée pour le passage en vol. */
    @Volatile
    private var annulationDemandee: Boolean = false

    /** Les chemins ÉCARTÉS du dernier passage (reprise ciblée). */
    @Volatile
    private var ecartsDernierPassage: List<String> = emptyList()

    /** Les entrées NON TRAITÉES du dernier passage (pendances d'annulation). */
    @Volatile
    private var pendancesDernierPassage: Int = 0

    /** Le scan du dernier passage a-t-il été ANNULÉ en route ? */
    @Volatile
    private var scanAnnuleDernierPassage: Boolean = false

    /**
     * Enregistre un observateur des événements du passage.
     *
     * @param observateur l'observateur (façade hôte) ;
     * @return la poignée de désinscription (fermeture silencieuse).
     */
    internal fun enregistrer(observateur: ObservateurIndexationKotlin): Closeable {
        observateurs.add(observateur)
        return Closeable { observateurs.remove(observateur) }
    }

    /**
     * Demande un passage pour [classpath] (une fois par sync) — retour
     * IMMÉDIAT : le travail part sur le fil dédié ; un passage PÉRIMÉ
     * (nouvelle sync en vol) s'arrête à la prochaine unité et laisse la
     * place.
     *
     * @param classpath le classpath de la synchronisation ;
     * @return toujours `true` — la demande est prise en charge.
     */
    internal fun definirClasspath(classpath: ClasspathKotlin): Boolean {
        classpathCourant = classpath
        generation++
        executeur.execute { passer() }
        return true
    }

    /**
     * Annule le passage en vol (l'acquis est publié, les suivantes
     * pendent — la reprise les complète).
     *
     * @return `true` si un passage était en vol ; `false` honnête sinon.
     */
    internal fun annulerPassage(): Boolean {
        if (!enVol) return false
        annulationDemandee = true
        return true
    }

    /**
     * Retente les échecs du dernier passage : le cache mémoire rend les
     * entrées saines REPRIS sans re-lecture, seuls les écarts (et les
     * pendances d'une annulation, segments OU scan) sont re-payés.
     *
     * @return `true` si un nouveau passage est parti ; `false` quand il
     * n'y a rien à retenter.
     */
    internal fun reesayerEchecs(): Boolean {
        if (ecartsDernierPassage.isEmpty() && pendancesDernierPassage == 0 && !scanAnnuleDernierPassage) {
            return false
        }
        generation++
        executeur.execute { passer() }
        return true
    }

    // ------------------------------------------------------------------
    // Le passage (le seul fil qui décode).
    // ------------------------------------------------------------------

    /**
     * UN passage complet : segments puis publication puis scan puis
     * final — le récit linéaire de la séquence ADR 0213 (le découper
     * cacherait l'ORDONNANCEMENT qui fait le contrat : BIBLIOTHEQUES
     * avant SOURCES avant final, publication avant scan).
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount", "LoopWithTooManyJumpStatements")
    private fun passer() {
        val classpath = classpathCourant ?: return
        val maGeneration = generation
        val debutPassage = System.currentTimeMillis()
        enVol = true
        annulationDemandee = false
        val etats = LinkedHashMap<String, EtatEntreeTravailKotlin>()
        val avertissements = mutableListOf<String>()
        var faits = 0
        var reprises = 0
        val ecarts = mutableListOf<String>()
        var pendances = 0
        var annule = false
        try {
            val entrees = classpath.entrees
            entrees.forEach { etats[it.chemin] = EtatEntreeTravailKotlin.EN_ATTENTE }
            publier(
                evenement(
                    phase = PhaseIndexationKotlin.CONSTRUCTION,
                    faits = 0,
                    total = entrees.size,
                    entreeCourante = null,
                    avertissements = avertissements,
                    phaseDetaillee = PhaseDetailleeIndexationKotlin.BIBLIOTHEQUES,
                    etats = etats,
                    reprises = 0,
                    durees = null,
                    breakdown = emptyList(),
                    bilan = null,
                    settled = false,
                ),
            )
            // ── Phase SEGMENTS : les bibliothèques, entrée par entrée ──
            val debutSegments = System.currentTimeMillis()
            val observation =
                IndexAppelablesKotlin.ObservationPassage { entree, issue ->
                    if (issue == null) {
                        // Entrée ACTIVE : l'écran montre l'entrée en cours
                        // (un décodage à froid peut durer des secondes).
                        etats[entree.chemin] = EtatEntreeTravailKotlin.ACTIF
                        publier(
                            evenement(
                                phase = PhaseIndexationKotlin.CONSTRUCTION,
                                faits = faits,
                                total = entrees.size,
                                entreeCourante = entree.chemin,
                                avertissements = avertissements,
                                phaseDetaillee = PhaseDetailleeIndexationKotlin.BIBLIOTHEQUES,
                                etats = etats,
                                reprises = reprises,
                                durees = null,
                                breakdown = emptyList(),
                                bilan = null,
                                settled = false,
                            ),
                        )
                        return@ObservationPassage true
                    }
                    when (issue) {
                        IndexAppelablesKotlin.IssueSegment.REPRIS -> {
                            etats[entree.chemin] = EtatEntreeTravailKotlin.REPRIS
                            reprises++
                        }

                        IndexAppelablesKotlin.IssueSegment.TERMINE ->
                            etats[entree.chemin] = EtatEntreeTravailKotlin.TERMINE

                        IndexAppelablesKotlin.IssueSegment.ECARTE -> {
                            etats[entree.chemin] = EtatEntreeTravailKotlin.ECARTE
                            ecarts.add(entree.chemin)
                            avertissements.add("${entree.chemin} : entrée illisible (écartée)")
                        }
                    }
                    faits++
                    publier(
                        evenement(
                            phase = PhaseIndexationKotlin.CONSTRUCTION,
                            faits = faits,
                            total = entrees.size,
                            entreeCourante = null,
                            avertissements = avertissements,
                            phaseDetaillee = PhaseDetailleeIndexationKotlin.BIBLIOTHEQUES,
                            etats = etats,
                            reprises = reprises,
                            durees = null,
                            breakdown = emptyList(),
                            bilan = null,
                            settled = false,
                        ),
                    )
                    // Continuation : annulation demandée ou passage
                    // PÉRIMÉ (une sync plus fraîche a remplacé).
                    !annulationDemandee && maGeneration == generation
                }
            val resultat = index.definir(entrees, classpath.dossierIndex?.let(::File), observation)
            if (resultat == IndexAppelablesKotlin.ResultatDefin.DEJA_COUVERT) {
                // No-op chaud (sync redondante) : tout est REPRIS sans
                // re-lecture — l'écran voit une reprise immédiate.
                for (entree in entrees) {
                    etats[entree.chemin] = EtatEntreeTravailKotlin.REPRIS
                }
                faits = entrees.size
                reprises = entrees.size
            }
            if (resultat == IndexAppelablesKotlin.ResultatDefin.PARTIEL_ANNULE) {
                annule = true
                pendances = entrees.count { etats[it.chemin] == EtatEntreeTravailKotlin.EN_ATTENTE }
                avertissements.add("passage annulé par l'utilisateur")
            }
            if (maGeneration != generation) {
                // PÉRIMÉ : le passage neuf prend la main — AUCUN final ici
                // (les événements du neuf réparent l'écran).
                return
            }
            val finSegments = System.currentTimeMillis()
            val dureeSegments = finSegments - debutSegments
            // ── PUBLICATION : le snapshot de l'index est posé — les
            //    sessions branchées voient les appelables dès maintenant,
            //    sans attendre le scan des sources. ──────────────────────
            publication(classpath)
            // ── Phase SOURCES : le scan des racines, fichier par fichier ──
            publier(
                evenement(
                    phase = PhaseIndexationKotlin.CONSTRUCTION,
                    faits = 0,
                    total = 0,
                    entreeCourante = null,
                    avertissements = avertissements,
                    phaseDetaillee = PhaseDetailleeIndexationKotlin.SOURCES,
                    etats = etats,
                    reprises = reprises,
                    durees = null,
                    breakdown = emptyList(),
                    bilan = null,
                    settled = false,
                ),
            )
            var scanAnnule = false
            scan.definirObservateur { faitsFichier, totalFichier, nom ->
                publier(
                    evenement(
                        phase = PhaseIndexationKotlin.CONSTRUCTION,
                        faits = faitsFichier,
                        total = totalFichier,
                        entreeCourante = nom,
                        avertissements = avertissements,
                        phaseDetaillee = PhaseDetailleeIndexationKotlin.SOURCES,
                        etats = etats,
                        reprises = reprises,
                        durees = null,
                        breakdown = emptyList(),
                        bilan = null,
                        settled = false,
                    ),
                )
                if (annulationDemandee || maGeneration != generation) {
                    scanAnnule = true
                    return@definirObservateur false
                }
                true
            }
            val debutScan = System.currentTimeMillis()
            if (scanAnnuleDernierPassage) {
                // Le scan du passage précédent a été ANNULÉ : la reprise
                // repart d'un RAFRAÎCHISSEMENT (l'acquis est en cache
                // mtime, seul le reste est re-parsé).
                scan.definirRacines(classpath.racinesSources)
                scan.rafraichir()
            } else {
                scan.definirRacines(classpath.racinesSources)
            }
            scan.attendrePassageCourant()
            val finScan = System.currentTimeMillis()
            scan.definirObservateur(null)
            if (scanAnnule) {
                annule = true
                avertissements.add("passage annulé par l'utilisateur")
            }
            val statsScan = scan.statsDernierPassage()
            scanAnnuleDernierPassage = scanAnnule
            if (maGeneration != generation) return
            // ── Final : PRET ou PARTIEL, breakdown et bilan paritaires ──
            val phase =
                if (ecarts.isEmpty() && !annule) PhaseIndexationKotlin.PRET else PhaseIndexationKotlin.PARTIEL
            val dureeScan = finScan - debutScan
            val durees = DureesPhasesKotlin(bibliothequesMs = dureeSegments, sourcesMs = dureeScan)
            val plusCouteux = if (dureeScan >= dureeSegments) IDENTIFIANT_SCAN else IDENTIFIANT_SEGMENTS
            publier(
                evenement(
                    phase = phase,
                    // Au final, la progression globale redevient celle des
                    // SEGMENTS (les fichiers du scan vivent dans le bilan).
                    faits = faits,
                    total = entrees.size,
                    entreeCourante = null,
                    avertissements = avertissements,
                    phaseDetaillee = PhaseDetailleeIndexationKotlin.AUCUNE,
                    etats = etats,
                    reprises = reprises,
                    durees = durees,
                    breakdown =
                        listOf(
                            BreakdownIndexeurKotlin(
                                identifiant = IDENTIFIANT_SEGMENTS,
                                libelle = "Segments Kotlin (kidx)",
                                millisecondesCumulees = dureeSegments,
                                nombreEntrees = entrees.size,
                            ),
                            BreakdownIndexeurKotlin(
                                identifiant = IDENTIFIANT_SCAN,
                                libelle = "Scan des sources Kotlin",
                                millisecondesCumulees = dureeScan,
                                nombreEntrees = statsScan.total,
                            ),
                        ),
                    bilan =
                        BilanDernierPassageKotlin(
                            dureeMs = System.currentTimeMillis() - debutPassage,
                            artefactsTotal = entrees.size,
                            artefactsRepris = reprises,
                            artefactsReconstruits = faits - reprises,
                            fichiersParcourus = statsScan.total,
                            fichiersReanalyses = statsScan.reanalyses,
                            indexeurPlusCouteux = plusCouteux,
                            horodatageMs = System.currentTimeMillis(),
                        ),
                    settled = !annule,
                ),
            )
        } finally {
            if (maGeneration == generation) {
                ecartsDernierPassage = ecarts
                pendancesDernierPassage = pendances
            }
            enVol = false
        }
    }

    /** Publie un événement à CHAQUE observateur (un fautif n'arrête rien). */
    private fun publier(evenement: EvenementIndexationKotlin) {
        for (observateur in observateurs) {
            try {
                observateur.surEvenement(evenement)
            } catch (t: Throwable) {
                LOGGER.log(Level.WARNING, "observateur d'indexation fautif (ignoré)", t)
            }
        }
    }

    /**
     * Assemble un événement — la liste de travail suit l'ordre du
     * classpath ; le breakdown n'est rempli qu'au final (miroir de la
     * progression hôte).
     */
    @Suppress("LongParameterList")
    private fun evenement(
        phase: PhaseIndexationKotlin,
        faits: Int,
        total: Int,
        entreeCourante: String?,
        avertissements: List<String>,
        phaseDetaillee: PhaseDetailleeIndexationKotlin,
        etats: Map<String, EtatEntreeTravailKotlin>,
        reprises: Int,
        durees: DureesPhasesKotlin?,
        breakdown: List<BreakdownIndexeurKotlin>,
        bilan: BilanDernierPassageKotlin?,
        settled: Boolean,
    ): EvenementIndexationKotlin =
        EvenementIndexationKotlin(
            phase = phase,
            faits = faits,
            total = total,
            entreeCourante = entreeCourante,
            avertissements = avertissements.toList(),
            erreurs = emptyList(),
            phaseDetaillee = phaseDetaillee,
            listeTravail = etats.map { EntreeTravailKotlin(it.key, it.value) },
            breakdownIndexeurs = breakdown,
            dureesPhases = durees,
            bilanDernierPassage = bilan,
            settled = settled,
            reprises = reprises,
        )

    private companion object {
        private val LOGGER: Logger = Logger.getLogger(PasseurIndexationKotlin::class.java.name)

        /** Identifiants des indexeurs (breakdown — libellés affichables). */
        internal const val IDENTIFIANT_SEGMENTS: String = "kidx-segments"

        internal const val IDENTIFIANT_SCAN: String = "scan-warm"
    }
}

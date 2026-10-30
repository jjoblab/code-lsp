// CodeIDE — scan projet PARESSEUX des racines sources (module :kotlin, étage 1
// vague 1c — fiche 041 du cahier). Code NEUF jjoblab — contrat : la fiche 041
// (15 comportements : scan paresseux, exclusion du document courant,
// invalidation au changement de racines, amorce warm, incrémental mtime,
// rafraîchissement externe L1, accès concurrents KP6, progression à total connu,
// annulation coopérative, reprise, projet sans sources). ÉCART DE PROCÉDURE
// consigné (ALERTES.md §4, E-K5) : le main ancien `session/ScanProjet.kt` de
// codeide-lsp a été lu par accident pendant le cadrage de cette vague (zone en
// lecture interdite R9.1) — la conception et le code suivent la fiche et les
// conventions du module, l'incident est déclaré au propriétaire.
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import jo.codeide.lsp.kotlin.parse.ParseurKotlin
import jo.codeide.lsp.kotlin.resolve.ContributionFichier
import jo.codeide.lsp.kotlin.resolve.LecteurDeclarationsJava
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.session.ScanSources.DeclarationExterne

/**
 * Le scan projet PARESSEUX, BORNÉ et ANNULABLE des racines sources (L1 —
 * fiche 041) : il remplace le scan synchrone de la vague 1b sans changer le
 * contrat de publication (snapshot [ScanSources] immuable, publié hors du
 * verrou de session).
 *
 * ## Le contrat (fiche 041)
 *
 * - [definirRacines] pose les racines : un appel aux MÊMES racines est un
 *   NO-OP chaud ; un CHANGEMENT invalide le snapshot servi puis AMORCE la
 *   passe sur le fil dédié (l'amorce WARM : le coût part à la définition,
 *   pas à la première frappe) ;
 * - [declarations] ne scanne JAMAIS sur le fil appelant : il REJOINT la passe
 *   en vol, sinon rend le snapshot publié pour les racines courantes (un
 *   passage ABOUTI, même VIDE ou PARTIEL annulé, est un état légitime — sans
 *   quoi chaque lecture ré-amorcerait une passe) ;
 * - [rafraichir] re-passe les MÊMES racines (événement de fichier externe,
 *   lot L1) : la passe est DELTA-ONLY grâce au cache par fichier (mtime) ;
 * - la MARCHE précède le PARSE : le total des fichiers est CONNU dès le
 *   premier tick de progression (vraie fraction faits/total) ;
 * - l'observateur ([definirObservateur]) est consulté entre chaque fichier :
 *   son retour `false` ANNULE la passe — l'ACQUIS est publié, le passage
 *   partiel est ABOUTI (pas de ré-amorce en boucle), la REPRISE le complète ;
 * - un projet SANS sources est un état légitime : passage abouti, vide.
 *
 * ## Concurrence (KP6 — miroir du verrou de la session, ADR 0117/0212)
 *
 * La paire (racines, snapshot servi) est mutée sous le [verrouScan] ; la
 * visibilité mémoire des lectures sans verrou est garantie par les champs
 * volatils. La publication et la LIBÉRATION du vol vivent DANS le corps de la
 * tâche (avant son achèvement) : un lecteur qui rejoint le vol est garanti de
 * voir le résultat publié ET le vol libéré (précédence-avant du
 * [FutureTask]) — une libération côté exécuteur ne garantirait rien (elle
 * court APRÈS l'achèvement : `get()` peut rendre avant la libération, et
 * l'amorce suivante serait bloquée par un vol fantôme).
 *
 * L'API NIO employée est la couche Java 7 consommable par ART dès l'API 26
 * ([Paths.get], [Files.readAllBytes], [Files.getLastModifiedTime] — PAS
 * `Path.of` ni `Files.readString`, arrivées avec Java 11 : le dexage du pack
 * cible min-api 26) ; l'UTF-8 est explicite à la lecture.
 *
 * @property bornFichiers la borne de la COLLECTE de la marche (couture de
 * test : la production garde le défaut — la troncation observable est le
 * sujet de la fiche 036, étape ultérieure) ; le COMPTE des fichiers
 * éligibles ne l'est PAS (statistiques honnêtes).
 */
internal class ScanProjetParesseux(
    private val bornFichiers: Int = BORN_FICHIERS,
) {

    /** Les racines courantes (identité d'invalidation). Volatile : KP6. */
    @Volatile
    private var racines: List<String> = emptyList()

    /** Le dernier snapshot publié (vide avant la première passe). Volatile : KP6. */
    @Volatile
    private var publie: ScanSources? = null

    /**
     * Les racines que [publie] couvre (`null` tant qu'aucune passe n'a publié) :
     * un snapshot n'est SERVI que s'il couvre les racines COURANTES — sinon la
     * lecture rend l'état vide honnête (« moins riche, jamais faux »).
     * Volatile : KP6.
     */
    @Volatile
    private var racinesPubliees: List<String>? = null

    /**
     * Un passage a-t-il ABOUTI pour les racines courantes ? Posé à la
     * publication d'une passe dont les racines sont les courantes (même VIDE
     * ou PARTIEL annulé), abaissé à chaque invalidation (changement de
     * racines, rafraîchissement) : c'est lui qui ferme la boucle d'amorcage
     * (« pas de ré-amorce perpétuelle »). Volatile : KP6.
     */
    @Volatile
    private var pret: Boolean = false

    /** Statistiques du dernier passage exécuté. Volatile : KP6. */
    @Volatile
    private var statsPassage: StatsPassage = StatsPassage(0, 0)

    /**
     * L'observateur du prochain passage (progression/annulation — posé par le
     * propriétaire du scanneur avant d'amorcer). `null` = silence. Volatile :
     * posé hors verrou, lu sur le fil du scan.
     */
    @Volatile
    private var observateur: ObservateurScan? = null

    /** Le drapeau de fermeture : après fermer(), tout est no-op honnête. */
    @Volatile
    private var ferme: Boolean = false

    /**
     * La passe en vol (vol unique : jamais deux passes du même travail — les
     * lecteurs la REJOIGNENT). Protégé par [verrouScan].
     */
    private var enVol: FutureTask<ScanSources>? = null

    /** Le verrou de la paire (racines, snapshot servi, vol) — KP6. */
    private val verrouScan = Any()

    /**
     * Le cache par FICHIER : chemin → (mtime, déclarations du fichier). Un
     * fichier inchangé n'est JAMAIS re-parsé (passe delta-only) ; les
     * fichiers disparus sont purgés à chaque passe (l'index ne ment jamais).
     */
    private val cacheFichiers = ConcurrentHashMap<String, EntreeCache>()

    /** Le fil UNIQUE du scan (démon : la JVM ne l'attend pas). */
    private val executeur: ExecutorService =
        Executors.newSingleThreadExecutor { tache ->
            Thread(tache, "scan-projet-kotlin").apply { isDaemon = true }
        }

    /** Le parseur du scan (sans état — sert chaque passe). */
    private val parseur = ParseurKotlin()

    // ------------------------------------------------------------------
    // Racines et rafraîchissement (fiche 041).
    // ------------------------------------------------------------------

    /**
     * (Re)pose les racines sources.
     *
     * - MÊMES racines : NO-OP chaud (synchronisation redondante — l'état
     *   publié reste servi tel quel) ;
     * - CHANGEMENT : le snapshot servi est INVALIDÉ (racines publiées
     *   détachées — les lectures rendent l'état vide honnête jusqu'à la
     *   nouvelle publication) puis la passe est AMORCÉE sur le fil dédié :
     *   le coût part à la définition, une lecture immédiate REJOINT la passe
     *   en vol et voit le résultat complet (l'amorce WARM).
     *
     * Un scanneur fermé ignore l'appel (no-op honnête).
     *
     * @param nouvelles les racines à scanner (chemins, en ordre de
     * préséance) ; liste vide = plus aucune racine.
     */
    internal fun definirRacines(nouvelles: List<String>) {
        synchronized(verrouScan) {
            if (ferme || nouvelles == racines) return
            racines = nouvelles
            publie = null
            racinesPubliees = null
            pret = false
        }
        amorcer()
    }

    /**
     * RAFRAÎCHIT les racines COURANTES (lot L1 : un événement de fichier
     * externe a signalé une création/modification hors éditeur) : la passe
     * est ré-amorcée sur le fil dédié, DELTA-ONLY grâce au cache mtime (un
     * fichier inchangé n'est jamais re-parsé, le coût est un listage). Le
     * snapshot publié RESTE servi pendant la passe (mêmes racines : jamais
     * moins riche), la nouvelle publication le remplace d'un bloc.
     *
     * Sans racines ou fermé : no-op honnête (fiche 041).
     */
    internal fun rafraichir() {
        if (ferme || racines.isEmpty()) return
        synchronized(verrouScan) { pret = false }
        amorcer()
    }

    // ------------------------------------------------------------------
    // Lectures (fiche 041 — jamais de scan sur le fil appelant).
    // ------------------------------------------------------------------

    /**
     * Rend les déclarations du snapshot courant pour les racines courantes :
     * REJOINT la passe en vol s'il y en a une, sinon rend l'état publié (un
     * passage abouti — même vide ou partiel — est la vérité), sinon AMORCE
     * la passe et rend l'état courant (sous-lecture honnête d'UNE lecture :
     * la passe publie pour les suivantes).
     *
     * @param uriExclue l'URI du document COURANT à exclure (son doublon du
     * scan serait périmé : le document vit son arbre frais côté session) ;
     * `null` pour tout rendre ;
     * @return les déclarations externes en ordre de marche (déterministe) ;
     * liste vide si rien n'est publié pour les racines courantes — jamais
     * d'exception.
     */
    internal fun declarations(uriExclue: String? = null): List<DeclarationExterne> {
        assurerSnapshot()
        val servable = snapshotCourant() ?: return emptyList()
        return servable.declarations(uriExclue)
    }

    /**
     * Le SNAPSHOT au consult (étage 2 — l'univers des preuves, fiches
     * 022/006) : REJOINT la passe en vol (jamais de scan sur le fil
     * appelant — miroir de [declarations]) et rend l'état publié pour les
     * racines courantes, contributions du modèle de symboles et ÉTAT de la
     * passe compris.
     *
     * @return le snapshot immuable (contributions + complétude) ; `null`
     * si aucune passe n'a abouti pour les racines courantes (l'univers
     * projet est alors ABSENT — projet sans racines, connaissance vide).
     */
    internal fun snapshotAuConsult(): ScanSources? {
        assurerSnapshot()
        return snapshotCourant()
    }

    /**
     * Garantit qu'un état existe SANS JAMAIS scanner sur le fil appelant :
     * rejoint le vol en cours, sinon si un passage a ABOUTI pour les racines
     * courantes rend tel quel, sinon amorce (la passe publie pour les
     * lectures suivantes).
     */
    private fun assurerSnapshot() {
        if (racines.isEmpty()) return
        val enCours = synchronized(verrouScan) { enVol }
        if (enCours != null) {
            try {
                enCours.get()
            } catch (_: Throwable) {
                // Passe périmée ou tombée : l'état est relu ci-dessous, la
                // lecture suivante ré-amorce (la tolérance est le contrat).
            }
        }
        if (pret) return
        amorcer()
    }

    /**
     * Attend l'aboutissement du passage COURANT des racines courantes — usage
     * du possesseur du scanneur (la session attend la fin de
     * [SessionLangageKotlin.rafraichirSources] avant de servir le résultat).
     * Ne scanne jamais sur le fil appelant : rejoint le vol en cours,
     * ré-amorce si un passage n'a pas abouti ; retour immédiat quand tout est
     * abouti (le drapeau `pret` ferme la boucle — un projet sans sources ne
     * ré-amorce pas perpétuellement).
     */
    internal fun attendrePassageCourant() {
        while (true) {
            if (ferme || racines.isEmpty()) return
            val enCours = synchronized(verrouScan) { enVol }
            if (enCours != null) {
                try {
                    enCours.get()
                } catch (_: Throwable) {
                    // Passe périmée ou tombée : la boucle relit l'état.
                }
                continue
            }
            if (pret) return
            amorcer()
        }
    }

    /**
     * Le snapshot publié couvrant les racines COURANTES (lecture PURE : ni
     * amorce ni attente — la session s'en sert à la publication de
     * [SessionLangageKotlin.rafraichirSources]).
     *
     * @return le snapshot immuable ; `null` si aucune passe n'a publié pour
     * les racines courantes.
     */
    internal fun snapshotCourant(): ScanSources? {
        return synchronized(verrouScan) {
            val couvertes = racinesPubliees
            if (couvertes != null && couvertes == racines) publie else null
        }
    }

    // ------------------------------------------------------------------
    // Progression et annulation (fiche 041).
    // ------------------------------------------------------------------

    /**
     * Pose l'observateur du prochain passage : consulté APRÈS chaque fichier
     * traité (vraie fraction faits/total — la marche précède le parse), son
     * retour `false` ANNULE la passe (l'acquis est publié, le passage partiel
     * est abouti ; la reprise par [rafraichir] le complète — le cache mtime
     * épargne l'acquis).
     *
     * @param observateur l'observateur à consulter ; `null` pour le silence.
     */
    internal fun definirObservateur(observateur: ObservateurScan?) {
        this.observateur = observateur
    }

    /**
     * Les statistiques du dernier passage EXÉCUTÉ (bilan honnête : le total
     * compte les fichiers éligibles VUS par la marche, au-delà de la borne de
     * collecte ; `faits < total` signale une annulation ou une troncation,
     * jamais un petit projet sain déguisé).
     *
     * @return les statist du dernier passage (faits, total).
     */
    internal fun statsDernierPassage(): StatsPassage = statsPassage

    // ------------------------------------------------------------------
    // Fermeture.
    // ------------------------------------------------------------------

    /**
     * Abandonne le scanneur : le fil dédié est interrompu, les amorces
     * postérieures et les publications d'une passe résiduelle sont des no-ops
     * (l'état publié reste lisible par acquit de conscience). Idempotente,
     * ne lève jamais.
     */
    internal fun fermer() {
        synchronized(verrouScan) { ferme = true }
        executeur.shutdownNow()
    }

    // ------------------------------------------------------------------
    // Passe (le seul fil qui parse).
    // ------------------------------------------------------------------

    /**
     * Amorce la passe des racines courantes sur le fil dédié : vol UNIQUE
     * (jamais deux passes du même travail — les lectures rejoignent) ; les
     * racines vides et le scanneur fermé n'amorcent pas. La publication et la
     * libération du vol vivent DANS le corps de la tâche (précédence-avant du
     * [FutureTask] : un lecteur qui rejoint voit le publié ET le vol libéré).
     */
    private fun amorcer() {
        synchronized(verrouScan) {
            if (ferme || racines.isEmpty() || enVol != null) return
            val racinesDuScan = racines
            val tache = FutureTask<ScanSources> {
                try {
                    val resultat = passer(racinesDuScan)
                    synchronized(verrouScan) {
                        if (!ferme && racines == racinesDuScan) {
                            publie = resultat
                            racinesPubliees = racinesDuScan
                            pret = true
                        }
                    }
                    resultat
                } finally {
                    synchronized(verrouScan) { enVol = null }
                }
            }
            enVol = tache
            try {
                executeur.execute { tache.run() }
            } catch (_: Throwable) {
                // Exécuteur mort (fermeture concurrente) : le vol fantôme
                // part, l'état reste lisible.
                enVol = null
            }
        }
    }

    /**
     * Mène la passe complète des racines (sur le fil dédié) : MARCHE d'abord
     * (listage borné, compte honnête — le total est connu avant le parse),
     * PARSE ensuite (incrémental mtime, observateur entre fichiers,
     * annulation coopérative), PURGE enfin (les fichiers disparus partent du
     * cache — l'index ne ment jamais ; seuls les VIVANTS de la marche sont
     * conservés : une annulation ne purge jamais un fichier sain non parcouru).
     *
     * Tolérance D3 partout : une racine ou un fichier illisible est IGNORÉ
     * (absent du scan, partiel honnête) — jamais d'exception.
     *
     * @param racinesDuScan les racines couvertes par cette passe ;
     * @return le snapshot immuable de la passe (partiel si annulée).
     */
    private fun passer(racinesDuScan: List<String>): ScanSources {
        // 1. MARCHE — le total est connu AVANT le premier parse.
        val marche = Marche(bornFichiers)
        for (racine in racinesDuScan) {
            marcher(racine, marche)
        }
        // 2. PARSE — incrémental mtime, premier nom gagne (les surcharges
        //    relèvent de la résolution, étage 2).
        val carte = LinkedHashMap<String, DeclarationExterne>()
        val contributions = mutableListOf<ContributionFichier>()
        var faits = 0
        var reanalyses = 0
        for (fichier in marche.fichiers) {
            val cle = fichier.toString()
            val modification = mtimeDe(fichier)
            val enCache = cacheFichiers[cle]
            val duFichier =
                if (enCache != null && enCache.modification == modification) {
                    enCache
                } else {
                    val analysee = analyseDuFichier(fichier)
                    cacheFichiers[cle] = analysee
                    reanalyses++
                    analysee
                }
            for (declaration in duFichier.declarations) {
                if (declaration.nom !in carte) carte[declaration.nom] = declaration
            }
            duFichier.contribution?.let { contributions.add(it) }
            faits++
            val obs = observateur
            if (obs != null && !obs.surFichier(faits, marche.vus, fichier.fileName.toString())) {
                // Annulation coopérative : l'acquis est publié, le passage
                // PARTIEL est un état abouti légitime.
                break
            }
        }
        // 3. PURGE — le cache ne ment jamais (sur les vivants de la marche).
        cacheFichiers.keys.retainAll(marche.fichiers.map { it.toString() }.toSet())
        statsPassage = StatsPassage(faits = faits, total = marche.vus, reanalyses = reanalyses)
        // La COMPLÉTUDE de la passe (étage 2 — l'univers des preuves) : une
        // passe annulée ou tronquée rend un snapshot PARTIEL (les preuves
        // d'absence s'y taisent — fiches 022/036).
        val complet = faits >= marche.vus
        return ScanSources(carte, contributions, complet)
    }

    /**
     * Marche UNE racine : dossiers et fichiers `.kt`/`.kts`/`.java` en ordre
     * de tri (déterminisme : la même cible sort toujours au même rang —
     * étage 2 : les sources JAVA nourrissent le modèle de symboles, fiche
     * 022), fichiers éligibles COMPTÉS au-delà de la borne de collecte
     * (statistiques honnêtes). Les dossiers de SORTIE sont EXCLUS de la
     * marche (vague 2e — fiche 035, exclusions ADR 0212 : le scan est le
     * SOURCE, `build` est la sortie — les sources GÉNÉRÉES qui y vivent
     * sont invisibles par conception, leurs conventions de nom sont muettes
     * côté résolution). Racine illisible ou inexistante : ignorée
     * (D3).
     *
     * @param racine le chemin de la racine ;
     * @param marche la marche cumulée de la passe.
     */
    private fun marcher(racine: String, marche: Marche) {
        val dossier =
            try {
                Paths.get(racine)
            } catch (_: InvalidPathException) {
                return
            }
        if (!Files.isDirectory(dossier)) return
        val fichiers =
            try {
                Files.walk(dossier).use { flux ->
                    flux.filter { fichier ->
                        estEligible(fichier)
                    }.sorted().toList()
                }
            } catch (_: Exception) {
                return
            }
        for (fichier in fichiers) {
            marche.compter(fichier)
        }
    }

    /**
     * Un chemin est-il ÉLIGIBLE au scan : fichier RÉGULIER `.kt`/`.kts`/
     * `.java` hors d'un dossier de sortie (vague 2e — fiche 035 : `build`
     * et ses sous-arbres sont les SORTIES du projet, pas ses sources).
     */
    private fun estEligible(fichier: Path): Boolean {
        if (!Files.isRegularFile(fichier)) return false
        if (fichier.fileName.toString().let { nom ->
                !nom.endsWith(".kt") && !nom.endsWith(".kts") && !nom.endsWith(".java")
            }
        ) {
            return false
        }
        var courant: Path? = fichier.parent
        while (courant != null) {
            if (courant.fileName?.toString() == DOSSIER_EXCLU) return false
            courant = courant.parent
        }
        return true
    }

    /**
     * Analyse UN fichier du scan (tolérant : un fichier illisible est
     * ABSENT du scan, partiel honnête D3) : les déclarations nommées du
     * SURVOL (fichiers Kotlin seulement) et la CONTRIBUTION au modèle de
     * symboles du projet (Kotlin ET Java — fiche 022).
     *
     * @param fichier le fichier à analyser ;
     * @return l'entrée de cache du fichier (déclarations + contribution,
     * listes vides si illisible ou sans déclaration) — jamais d'exception.
     */
    private fun analyseDuFichier(fichier: Path): EntreeCache {
        val texte =
            try {
                String(Files.readAllBytes(fichier), Charsets.UTF_8)
            } catch (_: Exception) {
                return EntreeCache(0L, emptyList(), null)
            }
        val uri = fichier.toUri().toString()
        if (fichier.fileName.toString().endsWith(".java")) {
            // Source JAVA : contribution au modèle de symboles SEULEMENT
            // (la matière du survol reste Kotlin — frontière documentée).
            return EntreeCache(mtimeDe(fichier), emptyList(), LecteurDeclarationsJava.lire(texte, uri))
        }
        val script = fichier.fileName.toString().endsWith(".kts")
        val arbre = parseur.arbre(texte, script)
        val commentaires = arbre.noeud.commentaires
        val resultat = mutableListOf<DeclarationExterne>()
        for (declaration in SurvolDocuments.declarationsRecursives(arbre)) {
            val nom = declaration.nom() ?: continue
            val signature = SurvolDocuments.renduSignature(declaration) ?: continue
            resultat.add(
                DeclarationExterne(
                    nom = nom,
                    uri = uri,
                    signature = signature,
                    documentation = SurvolDocuments.kdocPrecedente(declaration, commentaires),
                    plageNom = SurvolDocuments.plageNomDe(declaration),
                ),
            )
        }
        return EntreeCache(mtimeDe(fichier), resultat, ModeleProjet.contributionKotlin(arbre, uri))
    }

    /**
     * La date de modification d'un fichier (clé du cache incrémental).
     *
     * @param fichier le fichier à sonder ;
     * @return le mtime en millisecondes ; `0` si illisible (la clé ne lève
     * jamais).
     */
    private fun mtimeDe(fichier: Path): Long =
        try {
            Files.getLastModifiedTime(fichier).toMillis()
        } catch (_: Exception) {
            0L
        }

    /**
     * L'observateur d'un passage de scan : appelé APRÈS chaque fichier traité
     * (la fraction `faits/total` est vraie — la marche précède le parse) ; son
     * retour `false` ANNULE le passage entre deux fichiers (l'acquis est
     * publié, le passage partiel est abouti, la reprise le complète).
     */
    internal fun interface ObservateurScan {

        /**
         * Tick de progression d'un fichier traité.
         *
         * @param faits le nombre de fichiers traités (ce fichier compris) ;
         * @param total le total des fichiers éligibles VUS par la marche ;
         * @param nom le nom du fichier qui vient d'être traité ;
         * @return `true` pour continuer le passage ; `false` pour L'ANNULER
         * après ce fichier (coopératif — jamais d'interruption brutale).
         */
        fun surFichier(
            faits: Int,
            total: Int,
            nom: String,
        ): Boolean
    }

    /**
     * Les statistiques d'un passage de scan : le bilan honnête de la marche
     * et du parse.
     *
     * @property faits les fichiers traités par la passe (publiés dans
     * l'acquis) ;
     * @property total les fichiers éligibles VUS par la marche (au-delà de la
     * borne de collecte — `faits < total` signale annulation ou troncation) ;
     * @property reanalyses les fichiers RÉ-ANALYSÉS par la passe (le cache
     * mtime épargne les autres — alimente le bilan d'indexation, vague 1d).
     */
    internal class StatsPassage(
        internal val faits: Int,
        internal val total: Int,
        internal val reanalyses: Int = 0,
    )

    /**
     * L'entrée du cache par fichier : les déclarations d'un fichier, sa
     * contribution au modèle de symboles (étage 2) et le mtime qui les
     * légitime (un fichier inchangé n'est jamais re-parsé).
     *
     * @property modification le mtime du fichier à l'analyse ;
     * @property declarations les déclarations extraites alors ;
     * @property contribution la contribution au modèle de symboles (les
     * sources Java n'ont QUE celle-là).
     */
    private class EntreeCache(
        internal val modification: Long,
        internal val declarations: List<DeclarationExterne>,
        internal val contribution: ContributionFichier?,
    )

    /**
     * La marche d'une passe : la COLLECTE des fichiers à traiter est bornée
     * ([fichiers]), le COMPTE des fichiers éligibles ne l'est pas ([vus] —
     * statistiques honnêtes, total réel).
     *
     * @property borne la borne de collecte (couture de test).
     */
    private class Marche(private val borne: Int) {

        /** Les fichiers à traiter (parse, cache — collecte bornée). */
        internal val fichiers = mutableListOf<Path>()

        /** Le total des fichiers éligibles vus (compte non borné — honnête). */
        internal var vus: Int = 0
            private set

        /**
         * Compte UN fichier éligible ; le collecte si la borne le permet.
         *
         * @param fichier le fichier éligible rencontré.
         */
        internal fun compter(fichier: Path) {
            vus++
            if (fichiers.size < borne) fichiers.add(fichier)
        }
    }

    internal companion object {

        /**
         * La borne de COLLECTE de la marche (2 000 fichiers : la mémoire
         * appareil est finie — la troncation observable et sa preuve
         * d'absence sont le sujet des fiches 036/022, étapes ultérieures).
         * INTERNE : la couture de constructeur sert la fiche 036 (test de
         * la troncation sans écrire 2 001 fichiers).
         */
        internal const val BORN_FICHIERS: Int = 2_000

        /**
         * Le dossier de SORTIE exclu de la marche (vague 2e — fiche 035,
         * ADR 0212 : sorties et caches ; les sources générées y vivent).
         */
        private const val DOSSIER_EXCLU: String = "build"
    }
}

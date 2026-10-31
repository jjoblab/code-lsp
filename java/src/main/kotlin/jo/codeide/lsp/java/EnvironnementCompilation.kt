package jo.codeide.lsp.java

import jo.codeide.lsp.java.concurrence.EpoqueRetiree
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Environnement de compilation (étage N1, fiches 034/047) — nom neutre :
 * orchestration pure, aucune classe ecj/JDT dérivée.
 *
 * Rôle : détenir les **contextes par module** (racines sources, classpath,
 * niveau Java) et dériver paresseusement un [MoteurJava] par module — SON
 * verrou, SES caches (isolation lot E). Trois surfaces verrouillées par le
 * cahier :
 *
 *  - **routage fichier → module** (fiche 047) : le routeur est une fonction
 *    chemin → module (null = contexte défaut), posée UNE fois et RELUE à
 *    chaque requête — aucun cache de décision ne fige un modèle devenu
 *    obsolète ; un routeur qui jette ne casse JAMAIS une requête ;
 *  - **sémantiques replaceAll par module** (fiche 034) : remplacement
 *    redondant = no-op système (même moteur, zéro notification) ;
 *    remplacement effectif (listes OU niveau) = moteur reconstruit et
 *    auditeurs notifiés EXACTEMENT une fois ; l'invalidation est de
 *    périmètre MODULE (une sync de `:a` ne vide que le moteur de `:a`) ;
 *  - **champ partagé index** (fiche 034) : [setIndexNoms] invalide le
 *    moteur défaut ET tous les moteurs de module (idempotent pour la même
 *    instance).
 *
 * Les clés de module vides ou nulles visent le contexte DÉFAUT (comportement
 * documenté, fiche 034) ; les racines posées doivent EXISTER sur disque
 * (garde `isDirectory`).
 *
 * ## Double-tampon et époques (vague V-F1 — ADR 0029 phase 1)
 *
 * Le déficit corrigé ici (G-030 / H2) : avant V-F1, un remplacement effectif
 * disposait l'ancien moteur SUR LE CHAMP, et la requête suivante payait la
 * reconstruction froide de l'environnement de noms (jrt + jars — mesuré
 * 11–12 s côté appareil) DANS son propre fil : l'appareil « se figeait » aux
 * premières frappes post-sync. Depuis V-F1 :
 *
 *  - **double-tampon** : à un remplacement effectif AVEC moteur vivant,
 *    l'ancien moteur CONTINUE DE SERVIR pendant qu'un fil démon construit le
 *    neuf (moteur + réchauffement de l'environnement — voir [rechauffer]) ;
 *    le SWAP est atomique (sous le verrou d'orchestration) ; l'ancien est
 *    alors RETIRÉ : son jeton d'époque est annulé (les points de contrôle
 *    d'annulation fine coupent les collectes et balayages en cours), puis
 *    disposé (le moniteur du moteur attend naturellement la fin des calculs
 *    en vol) ;
 *  - **époques** : chaque remplacement effectif incrémente la GÉNÉRATION du
 *    module (l'époque copy-on-write des documents N8, étendue à
 *    l'environnement). Le protocole [calculerFraiche] capture le couple
 *    (moteur, génération) ATOMIQUEMENT, calcule, puis ne LIVRE que si
 *    l'époque n'a pas tourné — sinon il REJOUE le calcul sur l'époque
 *    courante (« 0 réponse périmée », rejeu des requêtes coupées). Servi par
 *    l'ancien moteur PENDANT la construction : légitime par construction
 *    (c'est l'époque vivante jusqu'au swap) ;
 *  - **repli documenté** : la propriété système
 *    `jo.codeide.lsp.java.doubletampon.disabled=true` rétablit le
 *    comportement d'avant V-F1 (dispose immédiat et synchrone à la sync) ;
 *    le protocole d'époque reste actif dans les DEUX régimes (la génération
 *    avance dans tous les cas).
 *
 * **Contrat Android (V-B — ADR 0020, décision D2)** : chaque contexte peut
 * porter un [jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur] (posé
 * par la sync hôte — il PRIME) ; sinon le moteur dérive le sien DU DISQUE
 * ([jo.codeide.lsp.java.projetandroid.ModeleAndroidDerive] — parseur Gradle
 * tolérant) au premier usage, derrière le drapeau de repli
 * `jo.codeide.lsp.java.projetandroid.derivation.disabled`.
 *
 * @param jdkHome home JDK explicite (null = runtime courant ; sans
 * `lib/modules` ni fournisseur jrt instanciable = pas de jrt — le régime
 * reste PLEIN dès qu'un jar de contexte porte la plateforme, v1.1.4).
 * @param complianceDefaut niveau Java par défaut (défaut constructeur « 17 »,
 * transmis par héritage aux modules sans niveau posé).
 */
public class EnvironnementCompilation(
    private val jdkHome: Path? = null,
    private val complianceDefaut: String = "17",
) {

    /** Contexte d'un module (listes + niveau demandé + contrat Android). */
    private data class ContexteModule(
        val racines: List<Path>,
        val jars: List<Path>,
        val niveauDemande: String?,
        val projetAndroid: jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur?,
    )

    /**
     * Prise d'époque (V-F1) : le couple (moteur vivant, génération) capturé
     * ATOMIQUEMENT sous le verrou — l'unité du protocole [calculerFraiche].
     *
     * @property cle la clé de module normalisée ("" = défaut).
     * @property moteur le moteur VIVANT de l'époque capturée (jamais null —
     *   construit paresseusement à la prise si le module n'en avait pas).
     * @property generation la génération du module au moment de la capture.
     */
    private class Prise(val cle: String, val moteur: MoteurJava, val generation: Long)

    /** Contextes par module (clé normalisée ; "" = défaut). */
    private val contextes = HashMap<String, ContexteModule>()

    /** Moteurs vivants par module (clé normalisée ; "" = défaut). */
    private val moteurs = LinkedHashMap<String, MoteurJava>()

    /**
     * Génération (époque) par module (V-F1) : incrémentée à CHAQUE
     * remplacement effectif, attachement d'index et libération — une
     * réponse calculée contre une génération révolue n'est jamais livrée
     * ([calculerFraiche] la rejoue sur l'époque courante).
     */
    private val generations = HashMap<String, Long>()

    /** Ordre d'insertion des modules (pour [moteursVivants] après le défaut). */
    private val ordreModules = mutableListOf<String>()

    /**
     * Inventaire ordonné des racines sources des CONTEXTES posés (défaut
     * en tête, puis modules dans l'ordre de leur première pose) — la
     * surface de balayage des symboles d'espace de l'étage N8 quand le
     * serveur vit sur un environnement (INTEGRATION-CODEIDE §3.3,
     * option B) : les moteurs étant paresseux, cet inventaire ne dépend
     * PAS de leur construction — un module nourri par la sync mais
     * jamais sollicité expose déjà SES racines.
     *
     * @return les racines vivantes (photographie ordonnée, dédoublonnée).
     */
    public fun racinesSources(): List<Path> = synchronized(verrou) {
        val resultat = LinkedHashSet<Path>()
        contextes[""]?.let { resultat.addAll(it.racines) }
        for (cle in ordreModules) {
            if (cle != "") {
                contextes[cle]?.let { resultat.addAll(it.racines) }
            }
        }
        return resultat.toList()
    }

    /**
     * Photographie des RACINES du contexte DÉFAUT (sonde d'état —
     * l'orchestrateur hôte et ses tests y vérifient le nourrissage du
     * contexte défaut avant/après les sync).
     *
     * @return les racines du contexte défaut (photographie).
     */
    public fun racinesDefaut(): List<Path> = synchronized(verrou) {
        contextes[""]?.racines ?: emptyList()
    }

    /**
     * Photographie des JARS du contexte DÉFAUT (sonde d'état — les tests
     * d'orchestration de l'hôte y vérifient la plateforme du toolchain
     * avant toute sync, motif h4 écart 1).
     *
     * @return les jars du contexte défaut (photographie).
     */
    public fun jarsDefaut(): List<Path> = synchronized(verrou) {
        contextes[""]?.jars ?: emptyList()
    }

    /** Index de noms partagé (champ de l'environnement). */
    private var indexNoms: IndexNoms? = null

    /** Auditeurs de contexte notifiés aux remplacements effectifs. */
    private val auditeurs = mutableListOf<(String) -> Unit>()

    /**
     * Auditeurs d'ÉPOQUE (V-F1) : notifiés APRÈS chaque swap de
     * double-tampon effectif — le moteur neuf est déjà vivant et sert ;
     * l'auditeur type (le serveur) y redemande la publication des
     * documents ouverts pour que les diagnostics des tampons suivent
     * l'époque. NON notifié quand la reconstruction a été dépassée par
     * une sync plus récente (aucun swap n'a eu lieu).
     */
    private val auditeursEpoque = mutableListOf<(String) -> Unit>()

    /** Verrou de l'orchestration (contextes + moteurs + observateurs). */
    private val verrou = Any()

    /** Routeur fichier → module, relu à CHAQUE requête (fiche 047). */
    public var routeurFichierModule: ((String) -> String?)? = null

    /**
     * Le fil unique des reconstructions de double-tampon (V-F1) : DÉMON,
     * paresseux — il n'existe que si une sync a déjà remplacé un moteur
     * vivant. Un seul fil (miroir du plafond de charge raisonnable de
     * l'ADR 0010 sur ART) : les syncs rapides successives s'y SÉRIALISENT,
     * chacune vérifiant sa génération avant de swapper.
     */
    private val filReconstruction by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "lspjava-double-tampon").apply { isDaemon = true }
        }
    }

    /**
     * Réchauffeur du moteur neuf (V-F1 — injectable pour les bancs) :
     * joué sur le fil de reconstruction AVANT le swap, il paie le coût
     * froid de l'environnement de noms PENDANT QUE L'ANCIEN SERT. Défaut :
     * ouvrir l'environnement + sonder la plateforme (jrt/jars — le reste
     * de l'annuaire reste paresseux, premier usage complétion).
     */
    internal var rechauffeur: (MoteurJava) -> Unit = { moteur ->
        runCatching { moteur.isLikelyUsable() }
    }

    /**
     * Surcharge de REPLI par instance (V-F1 — réservée aux bancs : la
     * production règle le repli par la propriété système
     * `jo.codeide.lsp.java.doubletampon.disabled`, lue une fois) : vrai
     * rétablit le comportement d'avant V-F1 pour CET environnement
     * (dispose immédiat et synchrone à chaque remplacement effectif),
     * null laisse la propriété décider. Le protocole d'époque reste actif
     * dans les deux régimes.
     */
    internal var doubleTamponForceDesactive: Boolean? = null

    /**
     * Reconstructions de double-tampon en cours (V-F1 — sonde d'état) :
     * incrémenté à la soumission, décrémenté en fin de tâche ; les bancs
     * et l'hôte y observent la fenêtre de construction.
     */
    private val reconstructionsEnCours = AtomicInteger(0)

    init {
        contextes[""] = ContexteModule(emptyList(), emptyList(), null, null)
    }

    /**
     * Rend le moteur d'un module (construction paresseuse, instance
     * canonique par module tant qu'aucun remplacement effectif n'a eu lieu).
     *
     * **Sous double-tampon (V-F1)** : pendant la fenêtre de reconstruction,
     * cette méthode rend l'ANCIEN moteur — il sert légitimement jusqu'au
     * swap (l'époque vivante). Le moteur neuf ne devient observable ICI
     * qu'après le swap ; [attendreReconstructions] borne l'attente.
     *
     * @param module la clé du module (`:app`) ; null ou vide = contexte défaut.
     * @return le moteur du module.
     */
    public fun moteur(module: String? = null): MoteurJava = synchronized(verrou) {
        val cle = normaliser(module)
        moteurs[cle]?.let { return it }
        val contexte = contextes[cle] ?: ContexteModule(emptyList(), emptyList(), null, null)
        val moteur = creerMoteur(contexte)
        moteurs[cle] = moteur
        if (cle != "" && cle !in ordreModules) {
            ordreModules.add(cle)
        }
        return moteur
    }

    /**
     * Rend le moteur du fichier par le ROUTAGE (fiche 047) : le routeur est
     * relu à CHAQUE requête (instantané courant du modèle), un routeur
     * fautif replie sur le contexte défaut sans propager l'exception.
     *
     * @param chemin le chemin du document.
     * @return le moteur routé (ou le moteur du contexte défaut).
     */
    public fun moteurPourFichier(chemin: String): MoteurJava {
        val module = try {
            routeurFichierModule?.invoke(chemin)
        } catch (_: Exception) {
            null
        }
        return moteur(module)
    }

    /**
     * Le protocole de calcul FRAIS d'époque (V-F1 — « 0 réponse périmée ») :
     * l'extension aux ENVIRONNEMENTS du design copy-on-write des documents
     * (N8). Capture le couple (moteur, génération) ATOMIQUEMENT, exécute le
     * calcul sur ce moteur, puis ne livre le résultat que si l'époque du
     * module n'a pas tourné pendant le calcul — sinon REJEU sur l'époque
     * courante.
     *
     * Trois honnêtetés bornent le rejeu :
     *  - un calcul COUPÉ par l'annulation fine ([EpoqueRetiree] — le moteur
     *    a été retiré par un swap) est RELANCÉ jusqu'à
     *    [BORNE_COUPURES] fois : la coupure ne produit aucun résultat
     *    livrable ; au-delà (syncs en rafale soutenue), l'exception
     *    traverse — le client voit une erreur franche plutôt qu'un
     *    résultat périmé, et la frappe suivante rejoue ;
     *  - un calcul TERMINÉ mais périmé est relancé jusqu'à
     *    [BORNE_RELANCES] fois, puis le DERNIER résultat est livré (même
     *    clause que les documents : une réponse cohérente avec un état très
     *    récent vaut mieux qu'aucune réponse — le client relance de toute
     *    façon) ;
     *  - pendant la FENÊTRE de reconstruction, l'ancien moteur sert :
     *    c'est l'époque VIVANTE (par conception, « 0 figage ») — les
     *    réponses de cette fenêtre ne sont pas périmées.
     *
     * @param chemin le chemin du document interrogé (routé par
     * [routeurFichierModule], relu à chaque tentative).
     * @param calcul le calcul pur (moteur → résultat) : rejouable sans
     * effet de bord observable côté client.
     * @return le résultat FRAIS de l'époque courante.
     */
    public fun <T> calculerFraiche(chemin: String, calcul: (MoteurJava) -> T): T {
        var relances = 0
        var coupures = 0
        while (true) {
            val prise = prisePour(chemin)
            val resultat = try {
                calcul(prise.moteur)
            } catch (e: EpoqueRetiree) {
                coupures += 1
                if (coupures >= BORNE_COUPURES) throw e
                continue
            }
            val courante = synchronized(verrou) { generations[prise.cle] ?: 0L }
            if (courante == prise.generation) return resultat
            relances += 1
            if (relances >= BORNE_RELANCES) return resultat
        }
    }

    /**
     * La prise d'époque (V-F1) : route le document, puis capture le couple
     * (moteur vivant, génération) SOUS LE VERROU — l'atomicité de la
     * capture est la garantie que le moteur servi et la génération lue
     * appartiennent à la MÊME époque.
     */
    private fun prisePour(chemin: String): Prise {
        val module = try {
            routeurFichierModule?.invoke(chemin)
        } catch (_: Exception) {
            null
        }
        val cle = normaliser(module)
        return synchronized(verrou) {
            val moteur = moteurs[cle] ?: run {
                val contexte = contextes[cle] ?: ContexteModule(emptyList(), emptyList(), null, null)
                val nouveau = creerMoteur(contexte)
                moteurs[cle] = nouveau
                if (cle != "" && cle !in ordreModules) ordreModules.add(cle)
                nouveau
            }
            Prise(cle, moteur, generations[cle] ?: 0L)
        }
    }

    /**
     * Remplace le contexte d'un module — surcharge IGNORANT le niveau : le
     * niveau demandé du module est CONSERVÉ (fiche 034 : « la surcharge 3
     * arguments conserve le niveau »).
     *
     * @param module la clé du module ; null ou vide = contexte défaut.
     * @param racines les racines sources (doivent exister : garde isDirectory).
     * @param jars les jars du classpath du module.
     */
    public fun remplacerContexteModule(module: String?, racines: List<Path>, jars: List<Path> = emptyList()) {
        synchronized(verrou) {
            val cle = normaliser(module)
            val ancien = contextes[cle]
            val niveau = ancien?.niveauDemande
            appliquerRemplacement(cle, racines, jars, niveau, ancien?.projetAndroid, ancien)
        }
    }

    /**
     * Remplace le contexte d'un module AVEC niveau explicite (fiche 034) :
     * `null` ramène le module au niveau PAR DÉFAUT de l'environnement
     * (héritage) ; un changement de niveau SEUL est un remplacement effectif.
     *
     * @param module la clé du module ; null ou vide = contexte défaut.
     * @param racines les racines sources (doivent exister).
     * @param jars les jars du classpath du module.
     * @param niveau le niveau Java demandé (null = retour au défaut).
     */
    public fun remplacerContexteModule(module: String?, racines: List<Path>, jars: List<Path> = emptyList(), niveau: String?) {
        synchronized(verrou) {
            val cle = normaliser(module)
            val ancien = contextes[cle]
            appliquerRemplacement(cle, racines, jars, niveau, ancien?.projetAndroid, ancien)
        }
    }

    /**
     * Remplace le contexte d'un module AVEC son contrat Android (V-B —
     * ADR 0020, décision D2) : la VARIANTE SYNC — l'hôte vient de lire
     * `ModuleLsp.android` du modèle et le pose ICI ; ce contrat PRIME sur
     * toute dérivation disque future pour ce module (R3 de l'ADR).
     *
     * Un changement de contrat SEUL est un remplacement effectif (le moteur
     * doit re-fabriquer ses classes light).
     *
     * @param module la clé du module ; null ou vide = contexte défaut.
     * @param racines les racines sources (doivent exister).
     * @param jars les jars du classpath du module.
     * @param niveau le niveau Java demandé (null = conserver l'ancien).
     * @param projetAndroid le contrat Android sync (null = dérivation disque).
     */
    public fun remplacerContexteModule(
        module: String?,
        racines: List<Path>,
        jars: List<Path> = emptyList(),
        niveau: String?,
        projetAndroid: jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur?,
    ) {
        synchronized(verrou) {
            val cle = normaliser(module)
            val ancien = contextes[cle]
            appliquerRemplacement(cle, racines, jars, niveau ?: ancien?.niveauDemande, projetAndroid, ancien)
        }
    }

    /**
     * Pose l'index de noms partagé (champ de l'environnement, fiche 034) :
     * l'attachement invalide le moteur défaut ET tous les moteurs de module ;
     * ré-attacher la MÊME instance ne re-invalide pas (idempotence).
     *
     * @param index l'index (null détache).
     */
    public fun setIndexNoms(index: IndexNoms?) {
        synchronized(verrou) {
            if (index === indexNoms) return
            indexNoms = index
            invaliderToutesEpoques()
            for ((_, moteur) in moteurs) {
                moteur.dispose()
            }
            moteurs.clear()
        }
    }

    /**
     * Enregistre un auditeur de contexte — notifié EXACTEMENT une fois par
     * remplacement EFFECTIF de contexte de module (clé du module en argument).
     *
     * @param auditeur le callback (clé de module normalisée).
     */
    public fun ajouterAuditeurContexte(auditeur: (String) -> Unit) {
        synchronized(verrou) {
            auditeurs.add(auditeur)
        }
    }

    /**
     * Enregistre un auditeur d'ÉPOQUE (V-F1) : notifié après chaque swap de
     * double-tampon EFFECTIF — le moteur neuf est déjà vivant et sert.
     * L'auditeur type (le serveur) y redemande la publication des documents
     * ouverts : les diagnostics suivent l'époque au lieu d'attendre la
     * frappe suivante.
     *
     * @param auditeur le callback (clé de module normalisée).
     */
    public fun ajouterAuditeurEpoque(auditeur: (String) -> Unit) {
        synchronized(verrou) {
            auditeursEpoque.add(auditeur)
        }
    }

    /**
     * Attend la DRAINAGE des reconstructions de double-tampon en cours
     * (V-F1 — sonde d'attente pour l'hôte et les bancs : après une sync,
     * borne le moment où le moteur neuf est vivant).
     *
     * @param dureeMaxMs la borne d'attente en millisecondes.
     * @return vrai ssi toutes les reconstructions sont drainées.
     */
    public fun attendreReconstructions(dureeMaxMs: Long = 60_000): Boolean {
        val echeance = System.nanoTime() + dureeMaxMs * 1_000_000
        while (reconstructionsEnCours.get() > 0) {
            if (System.nanoTime() >= echeance) return false
            Thread.sleep(5)
        }
        return true
    }

    /**
     * La GÉNÉRATION courante d'un module (V-F1 — sonde d'époque pour les
     * bancs et l'hôte) : croît à chaque remplacement effectif ; deux prises
     * de même génération servent la même époque.
     *
     * @param module la clé du module ; null ou vide = défaut.
     */
    public fun generationDe(module: String?): Long = synchronized(verrou) {
        generations[normaliser(module)] ?: 0L
    }

    /**
     * Nombre de reconstructions de double-tampon EN COURS (V-F1 — sonde).
     */
    public fun reconstructionsEnCours(): Int = reconstructionsEnCours.get()

    /**
     * Garde de construction paresseuse par module (fiche 047) : faux avant
     * toute requête, vrai dès que le moteur du module existe.
     *
     * @param module la clé du module ; null ou vide = défaut.
     */
    public fun moteurInitialise(module: String?): Boolean = synchronized(verrou) {
        normaliser(module) in moteurs
    }

    /**
     * Garde paresseuse par CHEMIN : route le fichier et rend la garde du
     * module routé (le défaut n'est construit que s'il est sollicité).
     *
     * @param chemin le chemin du document.
     */
    public fun moteurInitialisePour(chemin: String): Boolean {
        val module = try {
            routeurFichierModule?.invoke(chemin)
        } catch (_: Exception) {
            null
        }
        return moteurInitialise(module)
    }

    /**
     * Inventaire ordonné des moteurs vivants (fiche 047) : le DÉFAUT en
     * première position, puis les modules dans l'ordre de leur première
     * construction.
     *
     * @return les moteurs vivants (photographie ordonnée).
     */
    public fun moteursVivants(): List<MoteurJava> = synchronized(verrou) {
        val resultat = mutableListOf<MoteurJava>()
        moteurs[""]?.let { resultat.add(it) }
        for (cle in ordreModules) {
            if (cle != "") {
                moteurs[cle]?.let { resultat.add(it) }
            }
        }
        return resultat
    }

    /** Niveau Java par défaut de l'environnement (défaut constructeur « 17 »). */
    public fun complianceDefaut(): String = complianceDefaut

    /**
     * Libère l'environnement : tous les moteurs vivants sont disposés et
     * oubliés (les prochains accès construisent des moteurs distincts),
     * le routeur et les contextes sont conservés. Les reconstructions de
     * double-tampon en cours sont invalidées (leurs générations tournent —
     * aucune ne swappera après la libération).
     */
    public fun dispose() {
        synchronized(verrou) {
            invaliderToutesEpoques()
            for ((_, moteur) in moteurs) {
                moteur.dispose()
            }
            moteurs.clear()
        }
    }

    /** Applique les sémantiques replaceAll (no-op ou invalidation + notification unique). */
    private fun appliquerRemplacement(
        cle: String,
        racines: List<Path>,
        jars: List<Path>,
        niveau: String?,
        projetAndroid: jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur?,
        ancien: ContexteModule?,
    ) {
        // Garde : les racines doivent exister (fiche 047 — fixtures réelles).
        val racinesValidees = racines.filter { Files.isDirectory(it) }
        val nouveau = ContexteModule(racinesValidees, jars, niveau, projetAndroid)
        if (ancien != null && ancien == nouveau) {
            return // no-op système : même moteur, zéro notification
        }
        contextes[cle] = nouveau
        // L'ordre d'insertion vit à la PREMIÈRE POSE DE CONTEXTE (pas à la
        // première construction du moteur) : [racinesSources] expose les
        // racines d'un module nourri mais jamais encore sollicité, tandis
        // que [moteursVivants] n'itère que les moteurs EXISTANTS — même
        // sortie qu'avant pour elle.
        if (cle != "" && cle !in ordreModules) {
            ordreModules.add(cle)
        }
        // V-F1 : l'époque du module TOURNE (toute réponse en vol sur
        // l'ancienne génération sera rejouée).
        generations.merge(cle, 1L, Long::plus)
        val sortant = moteurs[cle]
        if (doubleTamponForceDesactive ?: DOUBLE_TAMPON_DESACTIVE || sortant == null) {
            // Repli documenté (propriété système) ou premier moteur du
            // module : comportement d'avant V-F1 — dispose immédiat et
            // synchrone (la requête suivante paie la reconstruction).
            moteurs.remove(cle)?.dispose()
        } else {
            // V-F1 — double-tampon : l'ancien continue de SERVIR pendant
            // que le fil de reconstruction construit et réchauffe le neuf ;
            // le swap est atomique, l'ancien est retiré APRÈS.
            soumettreReconstruction(cle, sortant, generations[cle] ?: 0L)
        }
        for (auditeur in auditeurs.toList()) {
            auditeur(cle)
        }
    }

    /**
     * Soumet la reconstruction en tâche de fond (V-F1 — appelé SOUS verrou,
     * la génération capturée est celle du remplacement en cours).
     *
     * La tâche vérifie sa génération AVANT de construire et AVANT de
     * swapper : une sync plus récente (ou une libération) la dépasse sans
     * effet — le moteur construit pour rien est disposé, JAMAIS swappé.
     */
    private fun soumettreReconstruction(cle: String, sortant: MoteurJava, generation: Long) {
        reconstructionsEnCours.incrementAndGet()
        filReconstruction.execute {
            try {
                val contexte = synchronized(verrou) { contextes[cle] } ?: return@execute
                if (synchronized(verrou) { generations[cle] ?: 0L } != generation) return@execute
                val moteur = try {
                    creerMoteur(contexte)
                } catch (_: Exception) {
                    return@execute // construction fautive : l'ancien continue de servir
                }
                try {
                    rechauffeur(moteur)
                } catch (_: Exception) {
                    // réchauffement fautif : le moteur reste utilisable
                    // (l'environnement se reconstruit paresseusement au
                    // premier usage) — on swap quand même : l'époque neuve
                    // est plus fraîche que l'ancien moteur.
                }
                var aRetirer: MoteurJava? = null
                synchronized(verrou) {
                    if (generations[cle] == generation && moteurs[cle] === sortant) {
                        // SWAP ATOMIQUE : le neuf devient l'époque vivante.
                        moteurs[cle] = moteur
                        aRetirer = sortant
                    }
                }
                val retire = aRetirer
                if (retire != null) {
                    // HORS verrou : le retrait pose le jeton d'époque annulé
                    // (coupure fine des calculs en vol) puis dispose — le
                    // moniteur du moteur attend les calculs non ponctués.
                    retire.retirer()
                    for (auditeur in synchronized(verrou) { auditeursEpoque.toList() }) {
                        runCatching { auditeur(cle) }
                    }
                } else {
                    // Reconstruction dépassée (sync plus récente ou
                    // libération) : le neuf construit pour rien est jeté.
                    moteur.dispose()
                }
            } finally {
                reconstructionsEnCours.decrementAndGet()
            }
        }
    }

    /** Fait tourner TOUTES les époques (invalidation globale — V-F1). */
    private fun invaliderToutesEpoques() {
        for (cle in contextes.keys) {
            generations.merge(cle, 1L, Long::plus)
        }
    }

    /** Construit un moteur depuis un contexte (niveau hérité du défaut si null). */
    private fun creerMoteur(contexte: ContexteModule): MoteurJava = MoteurJava(
        racines = contexte.racines,
        jars = contexte.jars,
        jdkHome = jdkHome,
        compliance = contexte.niveauDemande ?: complianceDefaut,
        niveauDemande = contexte.niveauDemande,
        indexNoms = indexNoms,
        projetAndroid = contexte.projetAndroid ?: deriverAndroid(contexte.racines),
    )

    /**
     * Dérivation disque du contrat Android (V-B — ADR 0020 option 1) :
     * jouée une fois PAR MOTEUR construit sans contrat sync, derrière le
     * drapeau de repli `jo.codeide.lsp.java.projetandroid.derivation.disabled`
     * ; rend null pour un module non-Android (coût : quelques stats de
     * fichiers). Le référentiel de ressources, lui, ne scanne qu'au premier
     * usage des classes light (paresseux dans le moteur).
     */
    private fun deriverAndroid(racines: List<Path>): jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur? {
        if (DERIVATION_ANDROID_DESACTIVEE) return null
        if (racines.isEmpty()) return null
        return try {
            jo.codeide.lsp.java.projetandroid.ModeleAndroidDerive.deriver(racines)
        } catch (_: Exception) {
            null // dérivation fautive : moteur sans contrat (jamais un crash)
        }
    }

    private companion object {
        /** Kill-switch de la dérivation disque (repli documenté ADR 0020). */
        private val DERIVATION_ANDROID_DESACTIVEE: Boolean =
            System.getProperty("jo.codeide.lsp.java.projetandroid.derivation.disabled") == "true"

        /**
         * Kill-switch du double-tampon (V-F1 — ADR 0029 phase 1, repli
         * documenté) : `jo.codeide.lsp.java.doubletampon.disabled` = vrai
         * rétablit le comportement d'avant V-F1 — dispose immédiat et
         * synchrone à chaque remplacement effectif (la requête suivante
         * paie la reconstruction froide). Le protocole d'époque
         * ([calculerFraiche]) reste actif dans les DEUX régimes.
         */
        private val DOUBLE_TAMPON_DESACTIVE: Boolean =
            System.getProperty("jo.codeide.lsp.java.doubletampon.disabled") == "true"

        /** Bornes de rejeu du calcul frais d'époque (V-F1 — « 0 réponse périmée »). */
        private const val BORNE_RELANCES: Int = 3

        /** Bornes de rejeu après coupure fine (V-F1 — annulation fine). */
        private const val BORNE_COUPURES: Int = 8
    }

    /** Normalise une clé de module (null/vide → défaut). */
    private fun normaliser(module: String?): String = module ?: ""
}

package jo.codeide.lsp.java

import java.nio.file.Files
import java.nio.file.Path

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
 * **Contrat Android (V-B — ADR 0020, décision D2)** : chaque contexte peut
 * porter un [jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur] (posé
 * par la sync hôte — il PRIME) ; sinon le moteur dérive le sien DU DISQUE
 * ([jo.codeide.lsp.java.projetandroid.ModeleAndroidDerive] — parseur Gradle
 * tolérant) au premier usage, derrière le drapeau de repli
 * `jo.codeide.lsp.java.projetandroid.derivation.disabled`.
 *
 * @param jdkHome home JDK explicite (null = runtime courant ; sans
 *   `lib/modules` ni fournisseur jrt instanciable = pas de jrt — le régime
 *   reste PLEIN dès qu'un jar de contexte porte la plateforme, v1.1.4).
 * @param complianceDefaut niveau Java par défaut (défaut constructeur « 17 »,
 *   transmis par héritage aux modules sans niveau posé).
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

    /** Contextes par module (clé normalisée ; "" = défaut). */
    private val contextes = HashMap<String, ContexteModule>()

    /** Moteurs vivants par module (clé normalisée ; "" = défaut). */
    private val moteurs = LinkedHashMap<String, MoteurJava>()

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

    /** Verrou de l'orchestration (contextes + moteurs + observateurs). */
    private val verrou = Any()

    /** Routeur fichier → module, relu à CHAQUE requête (fiche 047). */
    public var routeurFichierModule: ((String) -> String?)? = null

    init {
        contextes[""] = ContexteModule(emptyList(), emptyList(), null, null)
    }

    /**
     * Rend le moteur d'un module (construction paresseuse, instance
     * canonique par module tant qu'aucun remplacement effectif n'a eu lieu).
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
     * le routeur et les contextes sont conservés.
     */
    public fun dispose() {
        synchronized(verrou) {
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
        moteurs.remove(cle)?.dispose()
        for (auditeur in auditeurs.toList()) {
            auditeur(cle)
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
    }

    /** Normalise une clé de module (null/vide → défaut). */
    private fun normaliser(module: String?): String = module ?: ""
}

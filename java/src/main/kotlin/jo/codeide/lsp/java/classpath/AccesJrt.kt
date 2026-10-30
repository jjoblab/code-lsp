package jo.codeide.lsp.java.classpath

import java.net.URI
import java.net.URLClassLoader
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.ServiceLoader
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Accès à l'image jrt d'un JDK (étage N1, cahier fiche 031).
 *
 * Trois configurations de plateforme à distinguer (contrat du cahier) :
 *  - **jdkHome explicite** : image jrt EXTERNE (le JDK Termux est hors de
 *    l'app sur Android/ART) — le fournisseur `jrt` est chargé depuis le
 *    `lib/jrt-fs.jar` du JDK cible par `URLClassLoader` + `ServiceLoader`,
 *    puis ouvert sur ce home ;
 *  - **jdkHome null** : image jrt du runtime courant (fournisseur système) ;
 *  - **jdkHome sans `lib/modules`** : aucune image — le moteur déclare la
 *    plateforme absente (régime instantané, fiche 028).
 *
 * [ouvrir] rend `null` dans le dernier cas et sur échec de chargement
 * silencieux : la sonde observable de disponibilité reste
 * `MoteurJava.isLikelyUsable`.
 *
 * **ART (incident appareil du 30/10 au soir, corrigé v1.1.2)** : « échec
 * de chargement silencieux » capture `Throwable`, PAS seulement
 * `Exception` — sur appareil, le fournisseur `jrt` du `lib/jrt-fs.jar`
 * du JDK externe ne peut PAS s'instancier (il référence des internes
 * JDK absents d'ART) et `ServiceLoader` le signale par
 * `ServiceConfigurationError`, un **Error**. L'ancien `catch (Exception)`
 * laissait l'Error s'échapper : la construction paresseuse
 * d'`EnvironnementNoms` explosait à la PREMIÈRE requête, et le serveur
 * répondait « Internal error. » à TOUT (complétion, surlignage, inlay,
 * diagnostics muets) — le moteur dégrade désormais vers le régime
 * instantané documenté (`null`), la plateforme restant couverte par les
 * entrées réelles du classpath (android.jar du modèle).
 *
 * L'accès établit une carte **paquet → modules** en parcourant
 * `/modules/<module>/<paquet>` une seule fois à l'ouverture : les sondes
 * suivantes ([octets], [estPaquet]) sont des lectures directes, sans
 * re-parcours de l'image.
 */
public class AccesJrt private constructor(
    private val fs: FileSystem,
    private val paquetsVersModules: Map<String, MutableList<String>>,
    private val chargeurExterne: URLClassLoader?,
) : AutoCloseable {

    /** Racine `/modules` de l'image. */
    private val racineModules: Path = fs.getPath("/modules")

    /**
     * Indique si un paquet existe dans l'image (au moins un module le déclare).
     *
     * @param paquet nom du paquet en points (`java.lang`).
     * @return vrai ssi le paquet existe.
     */
    public fun estPaquet(paquet: String): Boolean = paquetsVersModules.containsKey(paquet)

    /**
     * Ensemble des paquets déclarés par l'image (étage N2 — segments de
     * l'annuaire de classpath, sans nouvelle traversée du système de
     * fichiers : la carte a été construite à l'ouverture).
     *
     * @return les noms de paquets connus (en points).
     */
    public fun paquetsConnus(): Set<String> = paquetsVersModules.keys.toSet()

    /**
     * Modules déclarant un paquet (sonde de diagnostic).
     *
     * @param paquet nom du paquet en points.
     * @return la liste (possiblement vide) des noms de modules.
     */
    public fun modulesDe(paquet: String): List<String> = paquetsVersModules[paquet]?.toList() ?: emptyList()

    /**
     * Noms simples des classes POSÉES dans un paquet (étage N2 — annuaire de
     * classpath) : liste le répertoire du paquet dans chaque module qui le
     * déclare. Paresseux et sans cache interne : l'appelant (annuaire) mémorise.
     *
     * @param paquet nom du paquet en points.
     * @return les noms simples des `.class` du paquet (imbriqués `$` compris —
     *   le filtrage est en aval, fiche 032).
     */
    public fun nomsClassesDuPaquet(paquet: String): List<String> {
        val modules = paquetsVersModules[paquet] ?: return emptyList()
        val noms = mutableListOf<String>()
        for (module in modules) {
            val dossier = racineModules.resolve(module).resolve(paquet.replace('.', '/'))
            if (!Files.isDirectory(dossier)) continue
            try {
                Files.list(dossier).use { entrees ->
                    entetes@ for (entree in entrees) {
                        val nom = entree.fileName.toString()
                        if (nom.endsWith(".class")) noms.add(nom.removeSuffix(".class"))
                    }
                }
            } catch (_: Exception) {
                // module illisible : écarté
            }
        }
        return noms
    }

    /**
     * Traversée intégrale de l'image (étage N2 — index des noms) : rend à
     * [consommateur] le chemin de CHAQUE fichier de classe sous
     * `/modules/<module>/<paquet>/…` (forme `paquet/Nom.class`), une seule
     * passe. Réservé à la construction de l'index par préfixe de l'annuaire.
     *
     * @param consommateur receveur de chaque chemin de classe (barres `/`).
     */
    public fun parcourirClasses(consommateur: (String) -> Unit) {
        val racine = fs.getPath("/modules")
        if (!Files.isDirectory(racine)) return
        try {
            Files.walk(racine).use { flux ->
                flux.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") }
                    .forEach { chemin ->
                        val relatif = racine.relativize(chemin).toString()
                        // Le premier segment est le module : l'entrée de classe
                        // commence après (forme paquet/Nom.class).
                        val premiereBarre = relatif.indexOf('/')
                        if (premiereBarre > 0) consommateur(relatif.substring(premiereBarre + 1))
                    }
            }
        } catch (_: Exception) {
            // image illisible : traversée avortée, l'appelant garde le partiel
        }
    }


    /**
     * Lit les octets du fichier de classe d'un FQCN dans l'image.
     *
     * @param fqcn nom entièrement qualifié en points (`java.lang.String`) ;
     *   un chemin `$` imbriqué est accepté tel quel.
     * @return les octets de la classe, ou null si le paquet ou la classe est
     *   absent de l'image.
     */
    public fun octets(fqcn: String): ByteArray? {
        val dernierPoint = fqcn.lastIndexOf('.')
        if (dernierPoint < 0) return null
        val paquet = fqcn.substring(0, dernierPoint)
        val cheminClasse = fqcn.replace('.', '/') + ".class"
        val modules = paquetsVersModules[paquet] ?: return null
        for (module in modules) {
            val chemin = racineModules.resolve(module).resolve(cheminClasse)
            if (Files.isRegularFile(chemin)) {
                return Files.readAllBytes(chemin)
            }
        }
        return null
    }

    /**
     * Libère les ressources : le chargeur externe éventuel (le fournisseur
     * jrt du runtime, lui, est partagé par la JVM — jamais fermé ici).
     */
    override fun close() {
        chargeurExterne?.close()
    }

    public companion object {

        /**
         * Ouvre l'accès jrt pour un home JDK.
         *
         * @param jdkHome home du JDK (`<jdk>` contenant `lib/modules`) ; null
         *   pour le runtime courant ; un répertoire SANS `lib/modules` rend
         *   null (régime instantané).
         * @return l'accès ouvert, ou null si l'image est absente/inutilisable.
         */
        @JvmStatic
        public fun ouvrir(jdkHome: Path?): AccesJrt? {
            val home = jdkHome ?: Path.of(System.getProperty("java.home"))
            if (!Files.isRegularFile(home.resolve("lib/modules"))) return null
            if (jdkHome == null) {
                // Runtime courant : fournisseur système déjà installé par la JVM.
                try {
                    val fs = FileSystems.getFileSystem(URI("jrt:/"))
                    return AccesJrt(fs, construireCartePaquets(fs), null)
                } catch (_: FileSystemNotFoundException) {
                    // Fournisseur absent (plateforme exotique) : repli jar externe.
                }
            }
            return ouvrirDepuisJarExterne(home)
        }

        /** Journal des échecs de fournisseur (traçage harnais — v1.1.2). */
        private val LOGGER: Logger = Logger.getLogger(AccesJrt::class.java.name)

        /**
         * Voie générique ( externe ou ART ) : le fournisseur jrt est chargé
         * depuis le `lib/jrt-fs.jar` du home cible — `URLClassLoader` +
         * `ServiceLoader`, image pointée sur CE home (fiche 031 : « point
         * dur à reproduire sur Android/ART où le JDK Termux est externe à
         * l'app »).
         *
         * **ART (v1.1.2)** : l'échec d'instanciation du fournisseur est un
         * `ServiceConfigurationError` (ou un `LinkageError`) — un **Error**,
         * jamais une `Exception` : la garde capture `Throwable` pour
         * honorer le contrat « null sur échec de chargement silencieux ».
         * Sans elle, TOUTE requête moteur explose en « Internal error. »
         * (la construction d'`EnvironnementNoms` est paresseuse, déclenchée
         * par la première requête — défaut constaté sur appareil :
         * complétion/surlignage/inlay en erreur, plus AUCUN diagnostic).
         */
        private fun ouvrirDepuisJarExterne(home: Path): AccesJrt? {
            val jrtFsJar = home.resolve("lib/jrt-fs.jar")
            if (!Files.isRegularFile(jrtFsJar)) return null
            return try {
                val chargeur = URLClassLoader(arrayOf(jrtFsJar.toUri().toURL()))
                val fournisseur = fournisseurJrtDe(chargeur)
                if (fournisseur == null) {
                    chargeur.close()
                    return null
                }
                val fs = fournisseur.newFileSystem(URI("jrt:/"), mapOf("java.home" to home.toString()))
                AccesJrt(fs, construireCartePaquets(fs), chargeur)
            } catch (t: Throwable) {
                // ART : ServiceConfigurationError / LinkageError ne sont pas
                // des Exception — la cause est journalisée (traçable en
                // harnais JVM), puis repli silencieux documenté.
                LOGGER.log(
                    Level.WARNING,
                    "fournisseur jrt de $home indisponible — repli régime instantané",
                    t,
                )
                null
            }
        }

        /**
         * Le fournisseur `jrt` d'un chargeur — la SONDE isolable du défaut
         * appareil du 30/10 (v1.1.2) : l'itération `ServiceLoader` INSTANCIE
         * chaque fournisseur déclaré ; sur ART le `jrt-fs.jar` du JDK externe
         * référence des internes JDK absents du runtime (`jdk.internal.jimage`
         * notamment) — l'instanciation lève `ServiceConfigurationError` ou
         * `NoClassDefFoundError`, des **Error** qu'un `catch (Exception)`
         * laissait fuir : la construction paresseuse d'`EnvironnementNoms`
         * explosait à la PREMIÈRE requête moteur (« Internal error. » sur
         * toute requête, diagnostics muets — CodeIDE 0.204.1, moto g06).
         *
         * Délègue à [premierFournisseur] — générique UNIQUEMENT pour que la
         * régression puisse simuler ART fidèlement : sur JVM, le service
         * `FileSystemProvider` est TOUJOURS fourni par le module `java.base`
         * (couche boot, itéré AVANT les fournisseurs du classpath du
         * chargeur) — le fournisseur système masque donc tout défaut du jar
         * (vérifié expérimentalement : c'est pourquoi la CI JVM est aveugle
         * à CE défaut). La condition ART — le fournisseur cassé est le
         * SEUL itéré — n'est reproductible sur JVM qu'avec un service sans
         * AUCUN module fournisseur.
         *
         * @param chargeur le chargeur du `lib/jrt-fs.jar` cible.
         * @return le fournisseur jrt instancié, ou null (échec contenu).
         */
        internal fun fournisseurJrtDe(chargeur: ClassLoader): FileSystemProvider? =
            premierFournisseur(FileSystemProvider::class.java, chargeur) { it.scheme == "jrt" }

        /**
         * Le premier fournisseur d'un service satisfaisant [predicat] — le
         * cœur du correctif v1.1.2 : l'itération (qui INSTANCIE chaque
         * fournisseur déclaré) est contenue face à **`Throwable`**, pas
         * seulement `Exception` — un fournisseur cassé lève
         * `ServiceConfigurationError` / `LinkageError` (des **Error**), et
         * le contrat « null sur échec de chargement silencieux » (fiche
         * 031) exige qu'ils soient capturés eux aussi. L'échec est
         * journalisé (JUL) pour rester traçable en harnais.
         *
         * @param service le type de service demandé au `ServiceLoader`.
         * @param chargeur le chargeur dont le classpath porte les fournisseurs.
         * @param predicat filtre de sélection (schéma, par exemple).
         * @return le premier fournisseur instancié satisfaisant le prédicat,
         *   ou null (échec contenu).
         */
        internal fun <S : Any> premierFournisseur(
            service: Class<S>,
            chargeur: ClassLoader,
            predicat: (S) -> Boolean,
        ): S? =
            try {
                ServiceLoader.load(service, chargeur).firstOrNull(predicat)
            } catch (t: Throwable) {
                LOGGER.log(
                    Level.WARNING,
                    "fournisseur de ${service.name} ininstanciable — repli régime instantané",
                    t,
                )
                null
            }

        /**
         * Parcourt `/modules/<module>` jusqu'au niveau paquet et construit la
         * carte paquet → modules. Un seul passage à l'ouverture.
         */
        private fun construireCartePaquets(fs: FileSystem): Map<String, MutableList<String>> {
            val carte = HashMap<String, MutableList<String>>()
            val racine = fs.getPath("/modules")
            if (!Files.isDirectory(racine)) return carte
            Files.list(racine).use { modules ->
                for (module in modules) {
                    val nomModule = module.fileName.toString()
                    visiterPaquets(module, "", nomModule, carte, 0)
                }
            }
            return carte
        }

        /**
         * Descend les répertoires d'un module ; rend vrai si le sous-arbre
         * contient des classes — un répertoire est un PAQUET dès que son
         * sous-arbre porte des `.class` (les paquets intermédiaires comptent :
         * `java` doit exister pour que `java.lang` soit atteignable).
         */
        private fun visiterPaquets(
            dossier: Path,
            prefixePaquet: String,
            nomModule: String,
            carte: HashMap<String, MutableList<String>>,
            profondeur: Int,
        ): Boolean {
            if (profondeur > 12) return false // garde contre les images exotiques
            var sousArbreContientClasses = false
            val entetes = try {
                Files.list(dossier).use { it.toList() }
            } catch (_: Exception) {
                return false
            }
            for (entete in entetes) {
                if (Files.isDirectory(entete)) {
                    val nom = entete.fileName.toString()
                    val paquetEnfant = if (prefixePaquet.isEmpty()) nom else "$prefixePaquet.$nom"
                    if (visiterPaquets(entete, paquetEnfant, nomModule, carte, profondeur + 1)) {
                        sousArbreContientClasses = true
                    }
                } else if (entete.fileName.toString().endsWith(".class")) {
                    sousArbreContientClasses = true
                }
            }
            if (sousArbreContientClasses && prefixePaquet.isNotEmpty()) {
                carte.getOrPut(prefixePaquet) { mutableListOf() }.let {
                    if (nomModule !in it) it.add(nomModule)
                }
            }
            return sousArbreContientClasses
        }
    }
}

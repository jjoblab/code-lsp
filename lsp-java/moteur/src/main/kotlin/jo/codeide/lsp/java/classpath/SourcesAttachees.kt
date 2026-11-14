package jo.codeide.lsp.java.classpath

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Sources ATTACHÉES du classpath (chantier A — javadoc du classpath au
 * survol, TOP 10 de l'audit java du 14/11/2026) : la javadoc ne vit NI
 * dans les `.class` NI dans l'image jrt — il faut des SOURCES. Le service
 * découvre deux canaux standards et y lit la javadoc d'un symbole :
 *
 *  1. le dossier `sources/android-<api>` du SDK Android — DÉDUIT du jar
 *     plateforme du classpath (`…/platforms/android-37.2/android.jar` →
 *     `…/sources/android-37.2/`), le chemin que `sdkmanager "sources;…"`
 *     installe (miroir du dossier « Sources for Android XX » d'Android
 *     Studio) ;
 *  2. le `src.zip` du JDK (`$JAVA_HOME/lib/src.zip`) — sources des
 *     modules java.* / javax.* (miroir IntelliJ « Choose Sources »).
 *
 * Contrats (miroir du périmètre « honnête » R5 du moteur) :
 *  - une source ABSENTE ne fabrique JAMAIS de documentation (null, pas
 *    d'exception, pas de signal inventé) ;
 *  - la lecture est un scan TEXTE BORNÉ (le fichier du FQCN une seule
 *    fois, cache LRU par FQCN) — jamais un parse ecj complet des sources
 *    du SDK (latence) ;
 *  - la déclaration visée est retrouvée par son NOM (classe, ou méthode
 *    par sélecteur) avec la javadoc ADJACENTE qui la précède ;
 *  - thread-safe : cache concurrent, ouvertures paresseuses.
 *
 * Le service ne CONNAÎT pas la sémantique : il rend le texte javadoc
 * BRUT (le nettoyage markdown reste à l'appelant — [NettoyeurJavadocMarkdown]).
 */
public class SourcesAttachees(
    jarsClasspath: List<Path> = emptyList(),
    jdkHome: Path? = null,
) {

    /** Un canal de sources résolu (dossier plat ou zip). */
    private sealed interface Canal {
        /** Dossier racine des sources (`sources/android-37.2`). */
        data class Dossier(val racine: Path) : Canal

        /** Archive zip de sources (`lib/src.zip` du JDK). */
        data class Zip(val chemin: Path, val fs: FileSystem) : Canal
    }

    /** Les canaux découverts (ordre : android d'abord, jdk ensuite). */
    private val canaux: List<Canal> = construireCanaux(jarsClasspath, jdkHome)

    /**
     * Cache des sources lues par FQCN — les NÉGATIFS sont mémorisés par
     * SENTINELLE (ConcurrentHashMap interdit les valeurs null) : un FQCN
     * absent des sources n'est pas re-sondé à chaque survol.
     */
    private object Absente

    private val cacheSources = ConcurrentHashMap<String, Any>()

    /** Plafond du cache texte (FQCN) — le SDK android-37 dépasse 30 Mo. */
    private companion object {
        const val PLAFOND_CACHE = 512
    }

    init {
        // Le fs des zips ouverts vit avec le service — fermeture au
        // disposal du moteur via [close].
    }

    /**
     * La javadoc BRUTE du TYPE (FQCN) dans les sources attachées.
     *
     * @param fqcn le nom entièrement qualifié (`android.app.Activity`).
     * @return le texte du commentaire javadoc brut, ou null (sources
     *   absentes, type introuvable, pas de javadoc).
     */
    public fun javadocType(fqcn: String): String? {
        val texte = texteType(fqcn) ?: return null
        return javadocAvantDeclaration(texte) { zone ->
            chercherDeclarationType(texte, zone, nomSimpleDe(fqcn))
        }
    }

    /**
     * La javadoc BRUTE d'une MÉTHODE du type dans les sources attachées.
     *
     * La recherche est par SÉLECTEUR (surcharge : la première déclaration
     * de ce nom gagne — les sources Android documentent chacune des
     * surcharges, la première est le meilleur repli honnête).
     *
     * @param fqcn le nom entièrement qualifié du type déclarant.
     * @param selecteur le nom de la méthode (`onCreate`).
     * @return le texte javadoc brut, ou null.
     */
    public fun javadocMethode(fqcn: String, selecteur: String): String? {
        val texte = texteType(fqcn) ?: return null
        return javadocAvantDeclaration(texte) { zone ->
            chercherDeclarationMethode(texte, zone, selecteur)
        }
    }

    /**
     * La javadoc BRUTE d'un CHAMP du type dans les sources attachées.
     *
     * @param fqcn le nom entièrement qualifié du type déclarant.
     * @param nom le nom du champ.
     * @return le texte javadoc brut, ou null.
     */
    public fun javadocChamp(fqcn: String, nom: String): String? {
        val texte = texteType(fqcn) ?: return null
        return javadocAvantDeclaration(texte) { zone ->
            chercherDeclarationChamp(texte, zone, nom)
        }
    }

    /** Vrai si au moins un canal de sources a été découvert. */
    public val actif: Boolean get() = canaux.isNotEmpty()

    /**
     * Le NOM DE FICHIER lisible du type dans les sources attachées
     * (chantier D — origine « Défini dans … » du hover) : `Activity.java`
     * pour `android.app.Activity`.
     *
     * @param fqcn le nom entièrement qualifié.
     * @return le nom du fichier source, ou null (absent des canaux).
     */
    public fun fichierType(fqcn: String): String? {
        val relatif = fqcn.replace('.', '/') + ".java"
        for (canal in canaux) {
            when (canal) {
                is Canal.Dossier -> if (Files.isRegularFile(canal.racine.resolve(relatif))) {
                    return relatif.substringAfterLast('/')
                }
                is Canal.Zip -> if (Files.isRegularFile(canal.fs.getPath("/$relatif"))) {
                    return relatif.substringAfterLast('/')
                }
            }
        }
        return null
    }

    /** Ferme les systèmes de fichiers zip ouverts. */
    public fun close() {
        for (canal in canaux) {
            if (canal is Canal.Zip) runCatching { canal.fs.close() }
        }
    }

    // --------------------------------------------------------------- //
    // Découverte des canaux
    // --------------------------------------------------------------- //

    /** Construit les canaux : sources android du SDK (déduites du jar), puis src.zip du JDK. */
    private fun construireCanaux(jars: List<Path>, jdkHome: Path?): List<Canal> {
        val resultats = ArrayList<Canal>()
        // 1. SDK android : le premier jar plateforme (`platforms/android-X/…`)
        //    donne la version API → `sources/android-X` à côté.
        for (jar in jars) {
            val parent = jar.parent ?: continue
            if (parent.fileName?.toString() == "platforms" || parent.parent?.fileName?.toString() == "platforms") continue
            val sdk = parent.parent ?: continue
            val dossier = sdk.resolve("sources").resolve(parent.fileName ?: continue)
            if (Files.isDirectory(dossier)) {
                resultats.add(Canal.Dossier(dossier))
                break
            }
        }
        // Forme repli : jar posé directement SOUS `platforms/android-37.2/`.
        if (resultats.isEmpty()) {
            for (jar in jars) {
                val parent = jar.parent ?: continue
                if (parent.fileName?.toString()?.startsWith("android-") == true &&
                    parent.parent?.fileName?.toString() == "platforms"
                ) {
                    val dossier = parent.parent.parent?.resolve("sources")?.resolve(parent.fileName) ?: continue
                    if (Files.isDirectory(dossier)) {
                        resultats.add(Canal.Dossier(dossier))
                        break
                    }
                }
            }
        }
        // 2. JDK : `lib/src.zip` (JAVA_HOME explicite, puis déduction
        //    depuis le java courant — miroir des sondes jdkHome moteur).
        val home = jdkHome ?: homeDuJavaCourant()
        val srcZip = home?.resolve("lib")?.resolve("src.zip")
        if (srcZip != null && Files.isRegularFile(srcZip)) {
            runCatching {
                val fs = FileSystems.newFileSystem(srcZip, mapOf("java.util.zip.ZipFile" to "true"))
                resultats.add(Canal.Zip(srcZip, fs))
            }
        }
        return resultats
    }

    /** Le JAVA_HOME déduit du java qui exécute (`…/lib/…` du chemin binaire). */
    private fun homeDuJavaCourant(): Path? {
        val cle = "java.home"
        val home = System.getProperty(cle) ?: return null
        return runCatching { Path.of(home) }.getOrNull()
    }

    // --------------------------------------------------------------- //
    // Lecture des sources d'un FQCN
    // --------------------------------------------------------------- //

    /** Le texte source du type (cache par FQCN, canaux dans l'ordre). */
    private fun texteType(fqcn: String): String? {
        val enCache = cacheSources[fqcn]
        if (enCache != null) return enCache as? String
        val relatif = fqcn.replace('.', '/') + ".java"
        var texte: String? = null
        for (canal in canaux) {
            texte = when (canal) {
                is Canal.Dossier -> lireFichier(canal.racine.resolve(relatif))
                is Canal.Zip -> {
                    // Le src.zip du JDK range les sources sous <module>/
                    // (java.base/java/lang/String.java) — le chemin nu ET
                    // le chemin préfixé par le module déduit du paquet
                    // (java.lang → java.base, javax.crypto → java.base)
                    // sont sondés.
                    var lu: String? = null
                    val direct = canal.fs.getPath("/$relatif")
                    if (Files.isRegularFile(direct)) lu = lireFichier(direct)
                    if (lu == null) {
                        val module = moduleDuPaquet(fqcn.substringBeforeLast('.', ""))
                        if (module != null) {
                            val prefixe = canal.fs.getPath("/$module/$relatif")
                            if (Files.isRegularFile(prefixe)) lu = lireFichier(prefixe)
                        }
                    }
                    // Repli : balayer les entrées du zip par SUFFIXE (un
                    // src.zip à plat, ou un module inattendu).
                    if (lu == null) {
                        val cible = relatif.substringAfterLast('/')
                        val paquet = relatif.substringBeforeLast('/')
                        val prefixeModule = moduleDuPaquet(fqcn.substringBeforeLast('.', ""))
                        for (canalChemin in canal.fs.rootDirectories) {
                            val candidats = ArrayList<String>()
                            if (prefixeModule != null) candidats.add("$prefixeModule/$relatif")
                            candidats.add(relatif)
                            for (candidat in candidats) {
                                val chemin = canalChemin.resolve(candidat)
                                if (Files.isRegularFile(chemin)) {
                                    lu = lireFichier(chemin)
                                    break
                                }
                            }
                            if (lu != null) break
                        }
                    }
                    lu
                }
            }
            if (texte != null) break
        }
        if (cacheSources.size >= PLAFOND_CACHE) cacheSources.clear()
        cacheSources[fqcn] = texte ?: Absente
        return texte
    }

    /** Lecture d'un fichier texte UTF-8 (null silencieux — contrat honnête). */
    private fun lireFichier(chemin: Path): String? = runCatching {
        Files.readString(chemin, Charsets.UTF_8)
    }.getOrNull()

    // --------------------------------------------------------------- //
    // Scan borné : javadoc adjacente à une déclaration
    // --------------------------------------------------------------- //

    /**
     * Cherche la déclaration dans la zone de tête du fichier (le type est
     * presque toujours dans les premiers ~300 Ko) et rend la javadoc qui
     * la précède immédiatement.
     *
     * La position de déclaration rendue par les chercheurs pointe le MOT-CLÉ
     * (`class`, sélecteur, nom de champ) — mais la javadoc précède les
     * MODIFICATEURS (`public final class …`) : le recul arrière remonte au
     * DÉBUT DE LIGNE de la déclaration, puis franchit les lignes de
     * modificateurs/annotations (une déclaration éclatée sur plusieurs
     * lignes est couverte : on remonte tant que la ligne précédente
     * ressemble à un prologue de déclaration).
     */
    private fun javadocAvantDeclaration(
        texte: String,
        trouverDeclaration: (Int) -> Int,
    ): String? {
        val borne = minOf(texte.length, BORNE_SCAN)
        val positionDeclaration = trouverDeclaration(borne)
        if (positionDeclaration < 0) return null
        // 1. Début de la LIGNE portant le mot-clé de la déclaration.
        var debutLigne = texte.lastIndexOf('\n', positionDeclaration) + 1
        // 2. Remonter les lignes de PROLOGUE : modificateurs, annotations,
        //    types génériques éclatés — une ligne est un prologue si elle
        //    n'est ni vide, ni un commentaire, ni une accolade fermante.
        while (debutLigne > 0) {
            val finLignePrecedente = debutLigne - 1
            val debutLignePrecedente = texte.lastIndexOf('\n', finLignePrecedente - 1) + 1
            val lignePrecedente = texte.substring(debutLignePrecedente, finLignePrecedente).trim()
            if (lignePrecedente.isEmpty()) break
            if (lignePrecedente.endsWith("*/")) {
                // La javadoc : extraire du "/**" le plus proche en amont.
                val ouverture = texte.lastIndexOf("/**", debutLignePrecedente)
                if (ouverture >= 0) return texte.substring(ouverture, finLignePrecedente)
                break
            }
            if (lignePrecedente.startsWith("//")) {
                // Commentaire de ligne du PROLOGUE (cas réel du SDK :
                // « // Strictly speaking this should be marked as @Nullable… »
                // entre la javadoc et la déclaration findViewById) : le
                // franchir et continuer à remonter.
                debutLigne = debutLignePrecedente
                continue
            }
            if (lignePrecedente.startsWith("*") || lignePrecedente.startsWith("/*") ||
                lignePrecedente.startsWith("}") || lignePrecedente.startsWith("{")
            ) {
                break
            }
            // Ligne de prologue (modificateur/annotation/générique) :
            // continuer à remonter.
            debutLigne = debutLignePrecedente
        }
        return null
    }

    /** La position de la déclaration du TYPE public/package (mot-clé + nom). */
    private fun chercherDeclarationType(texte: String, borne: Int, nomSimple: String): Int {
        for (motCle in listOf("class ", "interface ", "enum ", "record ", "@interface ")) {
            var depuis = 0
            while (true) {
                val i = texte.indexOf(motCle, depuis)
                if (i < 0 || i >= borne) break
                val apres = i + motCle.length
                if (texte.regionMatches(apres, nomSimple, 0, nomSimple.length) &&
                    !apresEstIdent(texte, apres + nomSimple.length) &&
                    !dansUnCommentaire(texte, i)
                ) {
                    return i
                }
                depuis = i + 1
            }
        }
        return -1
    }

    /** La position de la déclaration d'une MÉTHODE (sélecteur + `(`). */
    private fun chercherDeclarationMethode(texte: String, borne: Int, selecteur: String): Int {
        var depuis = 0
        while (true) {
            val i = texte.indexOf(selecteur, depuis)
            if (i < 0 || i >= borne) break
            val apres = i + selecteur.length
            if (!avantEstIdent(texte, i) && !apresEstIdent(texte, apres) && texte.startsWith("(", apres) &&
                !dansUnCommentaire(texte, i)
            ) {
                return i
            }
            depuis = i + 1
        }
        return -1
    }

    /** La position de la déclaration d'un CHAMP (nom + ` =` / `;`). */
    private fun chercherDeclarationChamp(texte: String, borne: Int, nom: String): Int {
        var depuis = 0
        while (true) {
            val i = texte.indexOf(nom, depuis)
            if (i < 0 || i >= borne) break
            val apres = i + nom.length
            if (!avantEstIdent(texte, i) && !apresEstIdent(texte, apres) && !dansUnCommentaire(texte, i)) {
                val c = texte.getOrNull(apres)
                if (c == '=' || c == ';' || (c != null && c.isWhitespace())) return i
            }
            depuis = i + 1
        }
        return -1
    }

    /**
     * Vrai si la position vit dans un COMMENTAIRE du texte — les javadoc
     * du SDK réel contiennent des EXEMPLES de code (`<pre> public class
     * Activity …`) qui font échouer un scan naïf : l'occurrence dans le
     * commentaire est écartée, la vraie déclaration (après la fermeture)
     * gagne. Balayage des marqueurs en amont de la position : le dernier
     * ouvrant non fermé décide.
     */
    internal fun dansUnCommentaire(texte: String, position: Int): Boolean {
        var ouverture = -1
        var chercheDepuis = 0
        while (true) {
            val bloc = texte.indexOf("/*", chercheDepuis)
            if (bloc < 0 || bloc >= position) break
            val fermeture = texte.indexOf("*/", bloc + 2)
            if (fermeture < 0 || fermeture >= position) {
                ouverture = bloc
                break
            }
            chercheDepuis = fermeture + 2
        }
        if (ouverture >= 0) return true
        // Commentaire de ligne : le « // » le plus proche sur la MÊME ligne.
        var ligneDebut = texte.lastIndexOf('\n', position - 1) + 1
        while (true) {
            val l = texte.indexOf("//", ligneDebut)
            if (l < 0 || l >= position) return false
            return true
        }
    }

    /** Vrai si le caractère AVANT la position étend un identifiant. */
    private fun avantEstIdent(texte: String, position: Int): Boolean =
        position > 0 && (texte[position - 1].isJavaIdentifierPart())

    /** Vrai si le caractère À la position étend un identifiant. */
    private fun apresEstIdent(texte: String, position: Int): Boolean =
        position < texte.length && texte[position].isJavaIdentifierPart()

    /** Le nom simple d'un FQCN (dernier segment). */
    private fun nomSimpleDe(fqcn: String): String = fqcn.substringAfterLast('.')

    /**
     * Le module JPMS le plus probable d'un paquet (src.zip du JDK range
     * les sources par module) : table des modules courants de java.* —
     * null si le paquet n'est pas un paquet JDK connu (le repli par
     * chemin direct a déjà été tenté).
     */
    internal fun moduleDuPaquet(paquet: String): String? = when {
        paquet.startsWith("java.lang") && paquet != "java.lang.annotation" && paquet != "java.lang.instrument" -> "java.base"
        paquet.startsWith("java.util") && !paquet.startsWith("java.util.jar") && !paquet.startsWith("java.util.zip") -> "java.base"
        paquet.startsWith("java.io") || paquet.startsWith("java.nio") || paquet.startsWith("java.net") ||
            paquet.startsWith("java.security") || paquet.startsWith("java.text") || paquet.startsWith("java.time") ||
            paquet.startsWith("java.math") || paquet.startsWith("java.rmi") == false && paquet == "java.lang.ref" -> "java.base"
        paquet.startsWith("javax.crypto") || paquet.startsWith("javax.net") || paquet.startsWith("javax.security") -> "java.base"
        paquet.startsWith("javax.swing") || paquet.startsWith("javax.accessibility") -> "java.desktop"
        paquet.startsWith("javax.xml") -> "java.xml"
        paquet.startsWith("java.sql") || paquet.startsWith("javax.sql") -> "java.sql"
        paquet.startsWith("java.rmi") || paquet.startsWith("java.rmi.") -> "java.rmi"
        paquet.startsWith("jdk.") -> "jdk.unsupported"
        paquet.startsWith("com.sun.") -> null
        else -> "java.base"
    }

    /** La zone de tête scannée pour chercher une déclaration. */
    private val BORNE_SCAN = 300_000
}

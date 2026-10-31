// CodeIDE — socle du banc de cohérence P1–P9 du module :kotlin (mission
// « niveau Android Studio », vague 0 — miroir de jo.codeide.lsp.java
// .coherence.SocleCoherence du domaine Java, et du harnais du banc de
// fidélité K2 vague 2g). Code NEUF jjoblab (banc de MESURE, jamais de
// production).
package jo.codeide.lsp.kotlin.coherence

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.session.MoteurLangageKotlinNoyau
import jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments

/**
 * Socle du banc de cohérence Kotlin P1–P9 (vague 0 — miroir du socle Java).
 *
 * **Rôle** : outiller la mesure des PROPRIÉTÉS du moteur Kotlin sur des
 * sources réelles — l'auto-corpus (le module `:kotlin` lui-même, du vrai
 * Kotlin compilable, TOUJOURS disponible en CI) et le corpus externe Android
 * (`bancs/corpus-android-kotlin`, jamais committé — voir son LISEZMOI).
 *
 * **Mode de fonctionnement (convention d'honnêteté R5)** : les chiffres sont
 * IMPRIMÉS, jamais inventés — un écart est un RÉSULTAT à rapporter ; le mode
 * strict (propriété `banc.coherence.strict=true` ou variable d'environnement
 * `BANC_COHERENCE_STRICT`) escalade les écarts en échecs. NOTE de montage :
 * la JVM de test ne reçoit PAS les `-D` de la JVM de build (le module ne
 * transporte que ses propriétés propres) — les propriétés sont lues ICI mais
 * seules les VARIABLES D'ENVIRONNEMENT traversent Gradle sans toucher au
 * build : c'est le canal documenté pour piloter le banc.
 *
 * **L'oracle K2** (ADR 0119) : le compilateur véritable en SOUS-PROCESSUS
 * (`org.jetbrains.kotlin.cli.jvm.K2JVMCompiler` du classpath de test),
 * multi-fichiers en UNE invocation — miroir du banc de fidélité vague 2g.
 * Le CWD du sous-processus est un répertoire TEMPORAIRE dédié (sans `-d`, K2
 * écrit ses sorties `META-INF/` et paquets dans son répertoire de travail —
 * le banc de fidélité laisse les siennes dans `kotlin/mondes/` et
 * `kotlin/META-INF/`, gitignoreés ; ici le bac à sable reste PROPRE, best-
 * effort de suppression inclus).
 */
internal object SocleCoherence {

    /**
     * La racine du dépôt (remontée depuis le répertoire de travail du test
     * jusqu'à `settings.gradle.kts` — miroir de la fiche 010 de parité).
     */
    internal val racineDepot: Path = run {
        var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null && !Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
            dir = dir.parent
        }
        checkNotNull(dir) { "settings.gradle.kts introuvable en remontant depuis ${Path.of(System.getProperty("user.dir")).toAbsolutePath()}" }
    }

    /** Le mode strict (escalade des écarts mesurés en échecs). */
    internal val STRICT: Boolean =
        System.getProperty("banc.coherence.strict")?.toBoolean()
            ?: System.getenv("BANC_COHERENCE_STRICT")?.toBoolean()
            ?: false

    /** La demande de mesure P1(c) sans classpath (univers vide, D3). */
    internal val SANS_CLASSPATH: Boolean =
        System.getProperty("corpus.kotlin.sansClasspath")?.toBoolean()
            ?: System.getenv("CORPUS_KOTLIN_SANS_CLASSPATH")?.toBoolean()
            ?: false

    /**
     * Le répertoire du corpus externe (null = absent). Propriété
     * `corpus.kotlin` ou variable d'environnement `CORPUS_KOTLIN` ; un chemin
     * RELATIF se résout contre la racine du dépôt (le LISEZMOI documente
     * `CORPUS_KOTLIN=bancs/corpus-android-kotlin`, or la JVM de test travaille
     * dans le répertoire du MODULE — la remontée rend l'écriture honnête).
     */
    internal val corpusKotlin: Path? = run {
        val brut = System.getProperty("corpus.kotlin") ?: System.getenv("CORPUS_KOTLIN") ?: return@run null
        val direct = Path.of(brut)
        when {
            Files.isDirectory(direct) -> direct
            else -> racineDepot.resolve(brut).takeIf { Files.isDirectory(it) }
        }
    }

    /** Un projet du corpus externe : racines de sources + jars du manifeste. */
    internal data class ProjetCorpus(
        val nom: String,
        val racines: List<Path>,
        val jars: List<Path>,
    )

    /**
     * Les projets du corpus externe, lus des manifestes `*.manifeste` du
     * répertoire `prepares` (une ligne `racine:<chemin absolu>` par racine
     * de sources, `classpath:<chemin absolu>` par jar — voir
     * `bancs/corpus-android-kotlin/LISEZMOI.md`). Liste VIDE si le corpus est
     * absent ou sans manifeste : les gates corpus s'ignorent alors AVEC LA
     * RAISON AFFICHÉE (jamais un vert silencieux).
     */
    internal fun projetsCorpus(): List<ProjetCorpus> {
        val prepares = corpusKotlin?.resolve("prepares") ?: return emptyList()
        if (!Files.isDirectory(prepares)) return emptyList()
        return Files.list(prepares).use { flux ->
            flux.filter { it.fileName.toString().endsWith(".manifeste") }
                .sorted()
                .toList()
                .mapNotNull(::lireManifeste)
        }
    }

    /** Lit un manifeste (racines et jars EXISTANTS seuls — montage honnête). */
    private fun lireManifeste(manifeste: Path): ProjetCorpus? {
        val racines = mutableListOf<Path>()
        val jars = mutableListOf<Path>()
        for (ligne in Files.readAllLines(manifeste)) {
            when {
                ligne.startsWith("racine:") ->
                    Path.of(ligne.removePrefix("racine:").trim()).let { if (Files.isDirectory(it)) racines.add(it) }
                ligne.startsWith("classpath:") ->
                    Path.of(ligne.removePrefix("classpath:").trim()).let { if (Files.isRegularFile(it)) jars.add(it) }
            }
        }
        if (racines.isEmpty()) return null
        return ProjetCorpus(
            nom = manifeste.fileName.toString().removeSuffix(".manifeste"),
            racines = racines,
            jars = jars,
        )
    }

    /**
     * Les racines de l'AUTO-CORPUS : les sources du module `:kotlin` lui-même
     * (`kotlin/src/main/kotlin` + la grammaire vendorée
     * `kotlin/src/main/vendor`, relativement à la racine du dépôt) — du vrai
     * Kotlin compilable, TOUJOURS présent en CI (P3–P9 tournent sans corpus
     * téléchargé, miroir du LISEZMOI du corpus).
     */
    internal fun racinesAutoCorpus(): List<Path> =
        listOf("kotlin/src/main/kotlin", "kotlin/src/main/vendor")
            .map { racineDepot.resolve(it) }
            .filter { Files.isDirectory(it) }

    /**
     * Les jars de l'AUTO-CORPUS : les entrées FICHIERS du classpath de la JVM
     * de test (`java.class.path`, séparateur de plateforme), filtrées aux
     * fichiers existants — stdlib, syntax-api, lsp4j, kotlin-metadata-jvm,
     * junit, android 4.1.1.4… Le filtre écarte les RÉPERTOIRES de classes du
     * classpath (les sources vivent dans les racines, pas dans les classes).
     */
    internal fun jarsAutoCorpus(): List<Path> =
        (System.getProperty("java.class.path") ?: "")
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { Path.of(it).toAbsolutePath().normalize() }
            .filter { Files.isRegularFile(it) }
            .distinct()

    /**
     * Ajoute le jar kotlin-stdlib VÉRITABLE (ancre de classe) s'il manque —
     * miroir de la voie 6 du banc de fidélité : aucun projet Kotlin ne
     * compile sans stdlib et l'oracle doit voir le MÊME univers que le
     * moteur (le LISEZMOI du corpus l'exige des manifestes ; le plancher
     * rend le montage robuste si un manifeste l'omet). Ne touche PAS à une
     * liste vide (l'univers vide de la mesure P1(c) reste vide).
     */
    internal fun assurerPlancherStdlib(jars: List<Path>): List<Path> {
        if (jars.isEmpty()) return jars
        if (jars.any { it.fileName.toString().startsWith("kotlin-stdlib") }) return jars
        val ancre = Path.of(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain.codeSource.location.toURI(),
        )
        return jars + ancre.toAbsolutePath().normalize()
    }

    /**
     * Monte une session branchée sur le moteur SINGLETON SPI (miroir EXACT
     * du montage de la fiche 045 : le moteur nourricier reçoit le classpath,
     * la session se crée PUIS se branche, le sondage borné attend la
     * couverture de l'index — jamais de mesure en régime dégradé).
     *
     * ## Remontage dans la MÊME JVM de test
     *
     * `MoteurLangageKotlinNoyau.definirClasspath` accepte les RÉDÉFINITIONS
     * (le pilote de passages incrémente sa génération : la passe en vol
     * s'arrête à la prochaine unité, la nouvelle passe repart) — monter une
     * SECONDE session avec un autre classpath se fait donc par un simple
     * NOUVEL appel à cette fonction ; les sessions du montage précédent
     * voient leur guichet se TAIRE (la garde compare la liste ordonnée des
     * entrées) : l'appelant les FERME (`close()`) avant de remonter, par
     * propreté.
     */
    internal fun monterSession(
        racines: List<Path>,
        jars: List<Path>,
        dossierIndex: Path? = null,
        attenteSecondes: Long = 120,
    ): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.toString(), "coherence:$i:${jar.fileName}")
            },
            racinesSources = racines.map { it.toAbsolutePath().toString() },
            dossierIndex = dossierIndex?.toString(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session, attenteSecondes)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    internal fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 120) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /** Une erreur de l'oracle K2 : fichier, ligne, message. */
    internal data class ErreurOracle(
        val fichier: Path,
        val ligne: Int,
        val message: String,
    )

    /**
     * L'ORACLE : K2 VÉRITABLE en sous-processus sur les fichiers donnés, en
     * UNE invocation multi-fichiers (le module de compilation est l'UNIVERS
     * de la mesure : découper un module en passes séparées fabriquerait des
     * « unresolved reference » CROISÉS artificiels — mesuré en bac à sable :
     * 462 erreurs fantômes sur 40 fichiers de l'auto-corpus, 0 sur les 99 en
     * une seule invocation). La sortie est lue ligne à ligne, les erreurs
     * (`^(.*?):(\d+):\d+: error: (.*)$`) regroupées par FICHIER (chemin
     * normalisé ; un chemin relatif imprimé se résout contre le répertoire
     * de travail du sous-processus).
     *
     * **Codes de sortie** : 1 AVEC sortie non vide est NORMAL (le compilateur
     * sort 1 quand il y a des erreurs) ; un timeout (AssertionError avec les
     * 50 premières lignes) ou un crash du compilateur (code ≥ 2 ou trace
     * « exception: », miroir du codegen vu en bac à sable) ÉCHOUE
     * explicitement — l'oracle ne mesure pas en silence.
     */
    internal fun oracleK2(
        fichiers: List<Path>,
        jars: List<Path>,
        timeoutSecondes: Long = 600,
    ): Map<Path, List<ErreurOracle>> {
        if (fichiers.isEmpty()) return emptyMap()
        val dossierTravail = Files.createTempDirectory("p1-oracle-k2")
        val commande = mutableListOf(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-cp", System.getProperty("java.class.path"),
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib",
            "-nowarn",
        )
        if (jars.isNotEmpty()) {
            commande += "-cp"
            commande += jars.joinToString(File.pathSeparator)
        }
        fichiers.forEach { commande += it.toString() }
        val processus = ProcessBuilder(commande)
            .redirectErrorStream(true)
            .directory(dossierTravail.toFile())
            .start()
        var sortie = ""
        val lecteur = Thread { sortie = processus.inputStream.bufferedReader().readText() }
            .apply { isDaemon = true; start() }
        if (!processus.waitFor(timeoutSecondes, TimeUnit.SECONDS)) {
            processus.destroyForcibly()
            lecteur.join(5_000)
            throw AssertionError(
                "oracle K2 : timeout de ${timeoutSecondes}s — 50 premières lignes de sortie :\n" +
                    sortie.lineSequence().take(50).joinToString("\n"),
            )
        }
        lecteur.join(10_000)
        supprimerRecursif(dossierTravail)
        val code = processus.exitValue()
        if (code >= 2 || sortie.contains("exception: ")) {
            throw AssertionError(
                "oracle K2 : le compilateur a levé (code=$code) — 50 premières lignes de sortie :\n" +
                    sortie.lineSequence().take(50).joinToString("\n"),
            )
        }
        if (code != 0 && sortie.isBlank()) {
            throw AssertionError("oracle K2 : code=$code sans aucune sortie — invocation incohérente")
        }
        val erreurs = LinkedHashMap<Path, MutableList<ErreurOracle>>()
        val formeErreur = Regex("^(.*?):(\\d+):\\d+: error: (.*)$")
        for (ligne in sortie.lineSequence()) {
            val correspondance = formeErreur.find(ligne) ?: continue
            var chemin = Path.of(correspondance.groupValues[1]).normalize()
            if (!chemin.isAbsolute) chemin = dossierTravail.resolve(chemin).normalize()
            val numero = correspondance.groupValues[2].toIntOrNull() ?: continue
            erreurs.getOrPut(chemin) { mutableListOf() }
                .add(ErreurOracle(chemin, numero, correspondance.groupValues[3]))
        }
        return erreurs
    }

    /** Suppression best-effort d'un répertoire (les sorties .class de K2). */
    private fun supprimerRecursif(racine: Path) {
        runCatching {
            Files.walk(racine).use { flux ->
                flux.sorted(Comparator.reverseOrder()).forEach { fichier ->
                    runCatching { Files.deleteIfExists(fichier) }
                }
            }
        }
    }
}

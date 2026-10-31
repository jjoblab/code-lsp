package jo.codeide.lsp.java.projetandroid

import java.nio.file.Files
import java.nio.file.Path

/**
 * Dérivateur DISQUE du contrat Android (vague V-B — ADR 0020 option 1,
 * décision D2) : avant toute sync Gradle, un parseur TEXTE TOLÉRANT des
 * fichiers `build.gradle(.kts)` + `AndroidManifest.xml` + conventions de
 * sourceSets produit le même contrat que la sync hôte — le moteur est
 * intelligent sans Gradle (axe de dépassement §0.1-2 : « il ne connaît pas
 * le code » sur un projet jamais buildé).
 *
 * **Règles de tolérance** (R1 de l'ADR — « parseur texte cassé par des DSL
 * exotiques ») :
 *  - chaque champ est extrait INDÉPENDAMMENT par motif, pas par grammaire :
 *    un bloc incompréhensible n'empêche jamais les autres champs ;
 *  - les commentaires (ligne et bloc) et les chaînes brutes sont écartés
 *    avant l'extraction ;
 *  - les notations KTS ET Groovy sont reconnues (`viewBinding = true`,
 *    `isViewBinding = true`, `minSdk 24`, `minSdkVersion = 24`…) ;
 *  - un champ introuvable garde sa valeur par défaut AGP 8 — le contrat
 *    dérivé est un REPLI, jamais une vérité fragile : dès que la sync
 *    livre `ModuleLsp.android`, elle PRIME (R3).
 *
 * La détection du module : depuis chaque racine source, remonter jusqu'au
 * dossier portant `build.gradle(.kts)` (borné — un `settings.gradle` arrête
 * la remontée : racine projet, pas module).
 */
public object ModeleAndroidDerive {

    /** Bornage de la remontée dossiers (racine → module). */
    private const val PROFONDEUR_MAX: Int = 8

    /**
     * Dérive le contrat Android d'un module depuis SES racines sources.
     *
     * @param racines les racines sources du module (p. ex.
     *   `<module>/src/main/java`) ; la première racine ANDROID décide.
     * @return le contrat dérivé, ou `null` si le module n'est pas Android
     *   (aucun build Gradle Android, aucun manifest, aucun `res/`).
     */
    public fun deriver(racines: List<Path>): ContratAndroidMoteur? {
        for (racine in racines) {
            val contrat = deriverDuneRacine(racine) ?: continue
            return contrat
        }
        return null
    }

    /** Dérivation depuis UNE racine (retourne null si non-Android). */
    private fun deriverDuneRacine(racine: Path): ContratAndroidMoteur? {
        val dossierModule = chercherDossierModule(racine) ?: return null
        val texteBuild = lireBuild(dossierModule)
        val manifest = chercherManifest(dossierModule)
        val resDefaut = dossierModule.resolve("src/main/res")
        val dossiersRes = extraireSrcDirsRes(texteBuild, dossierModule)
        val estAndroid =
            texteBuild?.let { contientBlocAndroid(it) } == true ||
                manifest != null ||
                Files.isDirectory(resDefaut)
        if (!estAndroid) return null

        val namespace = extraireValeur(texteBuild, setOf("namespace"))
            ?: manifest?.let { lirePackageManifest(it) }
            ?: ""
        if (namespace.isEmpty()) return null

        val applicationId = extraireValeur(texteBuild, setOf("applicationId"))
        val minSdk = extraireEntier(texteBuild, setOf("minSdk", "minSdkVersion"))
        val targetSdk = extraireEntier(texteBuild, setOf("targetSdk", "targetSdkVersion"))
        val compileSdk =
            extraireEntier(texteBuild, setOf("compileSdk", "compileSdkVersion"))
                ?: compilerDepuisNom(dossierModule)
        val dossiersResFinaux =
            (if (Files.isDirectory(resDefaut)) listOf(resDefaut) else emptyList()) +
                dossiersRes.filter { it != resDefaut }
        return ContratAndroidMoteur(
            namespace = namespace,
            applicationId = applicationId,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            dossiersRes = dossiersResFinaux.distinct(),
            manifest = manifest,
            viewBinding = extraireBooleen(texteBuild, setOf("viewBinding", "isViewBinding")),
            dataBinding = extraireBooleen(texteBuild, setOf("dataBinding", "isDataBinding")),
            buildConfig = extraireBooleen(texteBuild, setOf("buildConfig", "isBuildConfig")),
        )
    }

    // ------------------------------------------------------------------ //
    // Localisation des fichiers
    // ------------------------------------------------------------------ //

    /** Remonte depuis une racine jusqu'au dossier du module Gradle. */
    private fun chercherDossierModule(racine: Path): Path? {
        var courant: Path? = racine.toAbsolutePath().normalize()
        if (!Files.isDirectory(courant)) courant = courant?.parent
        var profondeur = 0
        while (courant != null && profondeur < PROFONDEUR_MAX) {
            if (Files.isRegularFile(courant.resolve("build.gradle.kts")) ||
                Files.isRegularFile(courant.resolve("build.gradle"))
            ) {
                return courant
            }
            // settings.gradle(.kts) = racine PROJET : au-dessus, ce ne sont
            // plus les modules — arrêt honnête.
            if (Files.isRegularFile(courant.resolve("settings.gradle.kts")) ||
                Files.isRegularFile(courant.resolve("settings.gradle"))
            ) {
                return null
            }
            courant = courant.parent
            profondeur++
        }
        return null
    }

    /** Le contenu du build.gradle(.kts) du module (null si illisible). */
    private fun lireBuild(dossierModule: Path): String? =
        sequenceOf("build.gradle.kts", "build.gradle")
            .map { dossierModule.resolve(it) }
            .firstOrNull { Files.isRegularFile(it) }
            ?.let {
                try {
                    Files.readString(it, Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
            }

    /** Le manifest principal du module (src/main/AndroidManifest.xml). */
    private fun chercherManifest(dossierModule: Path): Path? =
        dossierModule.resolve("src/main/AndroidManifest.xml").takeIf { Files.isRegularFile(it) }

    /** Le `package="…"` du manifest (AGP < 8 — repli namespace). */
    private fun lirePackageManifest(manifest: Path): String? =
        try {
            val texte = Files.readString(manifest, Charsets.UTF_8)
            LECTEUR_PACKAGE.find(texte)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }

    /** Vrai si le build Gradle déclare le plugin Android. */
    private fun contientBlocAndroid(texte: String): Boolean =
        "com.android.application" in texte ||
            "com.android.library" in texte ||
            MOTIF_BLOC_ANDROID.containsMatchIn(sansCommentaires(texte))

    /** compileSdk déduit du nom de dossier res/android ? Honnête : non — null. */
    private fun compilerDepuisNom(@Suppress("UNUSED_PARAMETER") dossierModule: Path): Int? = null

    // ------------------------------------------------------------------ //
    // Extraction tolérante
    // ------------------------------------------------------------------ //

    /** Une valeur quotée d'un des noms de propriété (`namespace = "…"`, `namespace "…"`). */
    private fun extraireValeur(
        texte: String?,
        noms: Set<String>,
    ): String? {
        if (texte == null) return null
        for (nom in noms) {
            val motif = Regex("(?:^|[^\\w.])$nom(?:Version)?\\s*(?:=\\s*)?[\"']([^\"']+)[\"']")
            motif.find(sansCommentaires(texte))?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    /** Un entier d'un des noms de propriété (`minSdk = 24`, `minSdkVersion 24`). */
    private fun extraireEntier(
        texte: String?,
        noms: Set<String>,
    ): Int? {
        if (texte == null) return null
        for (nom in noms) {
            val motif = Regex("(?:^|[^\\w.])$nom(?:Version)?\\s*(?:=\\s*)?(\\d+)\\b")
            motif.find(sansCommentaires(texte))?.groupValues?.get(1)?.let { return it.toIntOrNull() }
        }
        return null
    }

    /**
     * Un booléen d'un des noms de propriété — cherché DANS le bloc
     * `buildFeatures { … }` (borné par accolades équilibrées) :
     * `viewBinding = true`, `isDataBinding = true`.
     */
    private fun extraireBooleen(
        texte: String?,
        noms: Set<String>,
    ): Boolean {
        if (texte == null) return false
        val bloc = blocBalise(sansCommentaires(texte), "buildFeatures") ?: return false
        for (nom in noms) {
            val motif = Regex("(?:^|[^\\w.])$nom\\s*=\\s*true\\b")
            if (motif.containsMatchIn(bloc)) return true
        }
        return false
    }

    /**
     * Les dossiers `res` additionnels des sourceSets :
     * `res.srcDirs = […]`, `res.srcDirs += '…'`, `res.srcDirs("…")`,
     * `res.srcDirs("a", "b")` — résolus RELATIFS au dossier module.
     */
    private fun extraireSrcDirsRes(
        texte: String?,
        dossierModule: Path,
    ): List<Path> {
        if (texte == null) return emptyList()
        val nettoye = sansCommentaires(texte)
        val resultat = mutableListOf<Path>()
        for (occurrence in Regex("res\\.srcDirs").findAll(nettoye)) {
            // La déclaration vit sur la même « ligne logique » : jusqu'au
            // crochet/parenthèse fermant après l'occurrence.
            val fin = minOf(nettoye.length, occurrence.range.last + 1 + 240)
            val segment = nettoye.substring(occurrence.range.last + 1, fin)
            for (chemin in Regex("[\"']([^\"']+)[\"']").findAll(segment)) {
                val brut = chemin.groupValues[1]
                val resolu = dossierModule.resolve(brut).normalize()
                if (Files.isDirectory(resolu)) resultat.add(resolu)
            }
        }
        return resultat
    }

    /** Le bloc `nom { … }` (accolades équilibrées), ou null. */
    private fun blocBalise(
        texte: String,
        nom: String,
    ): String? {
        val occurrence = Regex("(?:^|[^\\w.])$nom\\s*\\{").find(texte) ?: return null
        var profondeur = 1
        var i = occurrence.range.last + 1
        val debut = i
        while (i < texte.length && profondeur > 0) {
            when (texte[i]) {
                '{' -> profondeur++
                '}' -> profondeur--
            }
            i++
        }
        return if (profondeur == 0) texte.substring(debut, i - 1) else null
    }

    /** Écarte les commentaires ligne et bloc (les chaînes sont conservées). */
    private fun sansCommentaires(texte: String): String =
        texte
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
            .replace(Regex("//[^\n]*"), " ")

    private val LECTEUR_PACKAGE = Regex("package\\s*=\\s*\"([^\"]+)\"")

    private val MOTIF_BLOC_ANDROID = Regex("""\bandroid\s*\{""")
}

package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.outils.appliquerEditions
import java.nio.file.Files
import java.nio.file.Path

/**
 * Socle du banc d'incohérences P1–P8 (mission « niveau Android Studio », vague 0 —
 * prompt §4.3, ADR 0030).
 *
 * **Rôle** : outiller la mesure des PROPRIÉTÉS du moteur (pas seulement des cas
 * dorés) : chaque propriété P1–P8 est jouée sur des sources réelles et historiques
 * de frappe, et ses violations sont **collectées et classées par cause racine** —
 * la matière première des ADR (0025 notamment).
 *
 * **Mode de fonctionnement (convention d'honnêteté)** :
 *  - les invariants DÉJÀ garantis par le cahier (fiches 013/014…) sont **assertés
 *    durs** (CI rouge si rompus) ;
 *  - les propriétés NON encore garanties (écarts G-002/G-003/G-004…) sont
 *    **mesurées et rapportées** : les violations s'impriment dans les logs de test
 *    avec leur cause racine et servent de **baseline d'écart** ;
 *  - la propriété système `banc.coherence.strict` (ou la variable d'environnement
 *    `BANC_COHERENCE_STRICT`) **escalade toutes les violations en échecs** — c'est
 *    le mode qui sera activé par la vague V-A (plan 04) au fil des correctifs.
 *
 * **Corpus réel** : la variable d'environnement `CORPUS_ANDROID` (ou la propriété
 * `corpus.android`) désigne le répertoire du corpus des 5 projets (téléchargé par
 * `scripts/telecharger-corpus-android.sh`) ; les propriétés P3/P4/P7 sur corpus
 * ne se jouent que si des manifestes de préparation existent
 * (`<repertoire>/prepares/<projet>.manifeste` — voir
 * `bancs/corpus-android/LISEZMOI.md`). Sans corpus, les tests de corpus sont
 * **ignorés avec la raison affichée** (jamais un vert silencieux).
 */
internal object SocleCoherence {

    /** Le mode strict (escalade des violations en échecs). */
    internal val STRICT: Boolean =
        System.getProperty("banc.coherence.strict")?.toBoolean()
            ?: System.getenv("BANC_COHERENCE_STRICT")?.toBoolean()
            ?: false

    /** Le répertoire du corpus réel (null = absent). */
    internal val corpusAndroid: Path? =
        System.getProperty("corpus.android")?.let(Path::of)
            ?: System.getenv("CORPUS_ANDROID")?.let(Path::of)

    /**
     * Une violation de propriété : la description factuelle + la cause racine
     * pressentie (renvoi aux hypothèses H# de l'audit et aux écarts G-###).
     */
    internal data class Violation(
        val propriete: String,
        val description: String,
        val causeRacine: String,
    )

    /** Le rapport d'une propriété : violations + compteurs. */
    internal class Rapport(
        val propriete: String,
        val resume: String,
    ) {
        private val violations = mutableListOf<Violation>()
        private var mesures = 0

        /** Le nombre de violations enregistrées (pour les métriques FP/1 000 lignes). */
        val nbViolations: Int
            get() = violations.size

        /** Enregistre une mesure réussie (sans violation). */
        fun mesure() {
            mesures++
        }

        /** Enregistre une violation avec sa cause racine. */
        fun violer(description: String, causeRacine: String) {
            violations.add(Violation(propriete, description, causeRacine))
        }

        /** Clôture le rapport : imprime le bilan ; en mode strict, échoue. */
        fun clore(contexte: String) {
            if (violations.isEmpty()) {
                println("[$propriete] $contexte — $resume : 0 violation sur $mesures mesures (baseline tenue)")
                return
            }
            println(
                "[$propriete] $contexte — $resume : ${violations.size} violation(s) sur $mesures mesures " +
                    "(BASELINE D'ÉCART — à corriger, cf. cause racine) :",
            )
            violations.forEachIndexed { index, violation ->
                println("[$propriete]   ${index + 1}. ${violation.description} — cause racine : ${violation.causeRacine}")
            }
            if (STRICT) {
                val premiere = violations.first()
                throw AssertionError(
                    "[$propriete] mode STRICT : ${violations.size} violation(s). Première : " +
                        "${premiere.description} (cause racine : ${premiere.causeRacine})",
                )
            }
        }
    }

    /** Les erreurs d'une liste de diagnostics. */
    internal fun erreurs(diagnostics: List<Diagnostic>): List<Diagnostic> =
        diagnostics.filter { it.severite == Severite.ERREUR }

    /** L'empreinte comparable d'un diagnostic (sévérité, code, message, plage). */
    internal fun empreinte(d: Diagnostic): String =
        "${d.severite}|${d.code}|${d.message}|${d.plage.debutOffset}-${d.plage.finOffset}"

    /** Compare deux listes (équivalence stricte fiche 014) et rend les différences. */
    internal fun differences(sousTest: List<Diagnostic>, temoin: List<Diagnostic>): List<String> {
        val differences = mutableListOf<String>()
        if (sousTest.size != temoin.size) {
            differences.add(
                "tailles différentes : sous-test ${sousTest.size} contre témoin ${temoin.size} " +
                    "(sous-test : ${sousTest.joinToString { empreinte(it) }} ; témoin : ${temoin.joinToString { empreinte(it) }})",
            )
            return differences
        }
        val a = sousTest.map(::empreinte)
        val b = temoin.map(::empreinte)
        for (i in a.indices) {
            if (a[i] != b[i]) {
                differences.add("diagnostic #$i divergent : « ${a[i]} » contre « ${b[i]} »")
            }
        }
        return differences
    }

    /** Un diagnostic « de résolution » (cannot be resolved…) — le vrai faux positif. */
    internal fun estErreurDeResolution(d: Diagnostic): Boolean {
        val message = d.message.lowercase()
        return message.contains("cannot be resolved") ||
            message.contains("does not exist") ||
            message.contains("cannot find")
    }

    /**
     * Insère une suggestion de complétion comme le ferait un client LSP standard :
     * remplace le préfixe tapé par le texte d'insertion, applique les éditions
     * additionnelles (imports — fiche 029) en une seule passe d'éditions
     * disjointes.
     *
     * @param source la source originale.
     * @param caret l'offset du caret (fin du préfixe).
     * @param longueurPrefixe la longueur du mot tapé (0 = insertion pure).
     * @param texteInsertion le texte inséré (label ou snippet).
     * @param editionsAdditionnelles les éditions additionnelles de l'item.
     * @return la source résultat.
     */
    internal fun insererSuggestion(
        source: String,
        caret: Int,
        longueurPrefixe: Int,
        texteInsertion: String,
        editionsAdditionnelles: List<EditionTexte> = emptyList(),
    ): String {
        val editions = buildList {
            add(EditionTexte(caret - longueurPrefixe, longueurPrefixe, texteInsertion))
            addAll(editionsAdditionnelles)
        }
        return appliquerEditions(source, editions)
    }

    /** La longueur du préfixe tapé avant un offset (identifiants Java). */
    internal fun longueurPrefixeAuCaret(source: String, caret: Int): Int {
        var debut = caret
        while (debut > 0 && source[debut - 1].isJavaIdentifierPart()) debut--
        return caret - debut
    }

    /** L'offset d'une occurrence de mot (0 = première). */
    internal fun offsetDe(source: String, mot: String, occurrence: Int = 0): Int {
        var position = -1
        repeat(occurrence + 1) { position = source.indexOf(mot, position + 1) }
        return position
    }

    /** Un moteur témoin NEUF (diagnose complet, aucun mémo). */
    internal fun temoinNeuf(): MoteurJava = MoteurJava()

    /**
     * Un répertoire temporaire de projet (racines sources) — utilisé par P7/P8 pour
     * les scénarios inter-fichiers. Supprimé à la fin du test par l'appelant.
     */
    internal fun projetTemporaire(fichiers: Map<String, String>): Path {
        val racine = Files.createTempDirectory("banc-coherence")
        fichiers.forEach { (cheminRelatif, contenu) ->
            val cible = racine.resolve(cheminRelatif)
            Files.createDirectories(cible.parent)
            Files.writeString(cible, contenu)
        }
        return racine
    }

    // =====================================================================
    // V-A2 — Corpus réel : montage des manifestes de préparation (ADR 0030,
    // bancs/corpus-android/LISEZMOI.md §2). Format du manifeste (une entrée
    // par ligne, chemins ABSOLUS, `#` = commentaire ignoré) :
    //   racine:<répertoire de sources écrites à la main>       (noyau dur)
    //   racine-generee:<répertoire de sources générées>        (R, binding…)
    //   classpath:<jar de la plateforme ou artefact résolu>
    // =====================================================================

    /** Un projet du corpus réel monté depuis son manifeste. */
    internal data class ProjetCorpus(
        val cle: String,
        val racines: List<Path>,
        val racinesGenerees: List<Path>,
        val jars: List<Path>,
    ) {
        /** Les fichiers du NOYAU DUR (sources écrites à la main), tri déterministe. */
        val fichiersNoyau: List<Path> by lazy { racines.flatMap { SocleCoherence.listerJava(it) } }

        /** Les fichiers GÉNÉRÉS par le build (R, BuildConfig, binding…), tri déterministe. */
        val fichiersGenerees: List<Path> by lazy { racinesGenerees.flatMap { SocleCoherence.listerJava(it) } }

        /** Toutes les racines sources (noyau dur + générées). */
        val toutesRacines: List<Path> get() = racines + racinesGenerees
    }

    /**
     * Les projets du corpus réel montés (manifestes du répertoire `prepares`,
     * produits par `scripts/preparer-manifestes-corpus.sh`). Vide (et la gate
     * s'ignore avec raison) si le corpus ou les manifestes sont absents.
     */
    internal fun projetsCorpus(): List<ProjetCorpus> {
        val corpus = corpusAndroid ?: return emptyList()
        val prepares = corpus.resolve("prepares")
        if (!Files.isDirectory(prepares)) return emptyList()
        return Files.list(prepares).use { flux ->
            flux.filter { it.fileName.toString().endsWith(".manifeste") }
                .sorted()
                .map(::monterManifeste)
                .filter { it.racines.isNotEmpty() }
                .toList()
        }
    }

    /** Monte UN manifeste (lignes racine:/racine-generee:/classpath:, le reste ignoré). */
    private fun monterManifeste(manifeste: Path): ProjetCorpus {
        val racines = mutableListOf<Path>()
        val generees = mutableListOf<Path>()
        val jars = mutableListOf<Path>()
        Files.readAllLines(manifeste).forEach { ligne ->
            when {
                ligne.startsWith("racine-generee:") -> Path.of(ligne.removePrefix("racine-generee:").trim())
                    .let { if (Files.isDirectory(it)) generees.add(it) }

                ligne.startsWith("racine:") -> Path.of(ligne.removePrefix("racine:").trim())
                    .let { if (Files.isDirectory(it)) racines.add(it) }

                ligne.startsWith("classpath:") -> Path.of(ligne.removePrefix("classpath:").trim())
                    .let { if (Files.isRegularFile(it)) jars.add(it) }
            }
        }
        return ProjetCorpus(
            cle = manifeste.fileName.toString().removeSuffix(".manifeste"),
            racines = racines,
            racinesGenerees = generees,
            jars = jars,
        )
    }

    /** Les fichiers Java d'une racine (tri lexical déterministe). */
    private fun listerJava(racine: Path): List<Path> =
        if (Files.isDirectory(racine)) {
            Files.walk(racine).use { flux ->
                flux.filter { it.fileName.toString().endsWith(".java") && Files.isRegularFile(it) }
                    .sorted()
                    .toList()
            }
        } else {
            emptyList()
        }

    /**
     * L'offset d'insertion dans le PREMIER CORPS DE MEMBRE de la source :
     * juste après l'accolade ouvrante qui fait passer la profondeur 1 → 2 ET
     * qui est précédée (en ignorant les blancs) d'une parenthèse fermante
     * (corps de méthode/constructeur — écarte les initialiseurs de tableaux
     * `= {` et les blocs statiques `static {`). Chaînes, caractères et
     * commentaires sont ignorés ; -1 si introuvable.
     */
    internal fun insertionPremierCorps(source: String): Int {
        var profondeur = 0
        var precedentNonBlanc = ' '
        var i = 0
        val n = source.length
        while (i < n) {
            val c = source[i]
            when {
                c == '/' && i + 1 < n && source[i + 1] == '/' -> {
                    while (i < n && source[i] != '\n') i++
                }
                c == '/' && i + 1 < n && source[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(source[i] == '*' && source[i + 1] == '/')) i++
                    i++
                }
                c == '"' -> {
                    i++
                    while (i < n && source[i] != '"' && source[i] != '\n') {
                        if (source[i] == '\\') i++
                        i++
                    }
                }
                c == '\'' -> {
                    i++
                    while (i < n && source[i] != '\'' && source[i] != '\n') {
                        if (source[i] == '\\') i++
                        i++
                    }
                }
                c == '{' -> {
                    profondeur++
                    if (profondeur == 2 && precedentNonBlanc == ')') return i + 1
                    precedentNonBlanc = '{'
                    i++
                }
                c == '}' -> {
                    profondeur--
                    precedentNonBlanc = '}'
                    i++
                }
                c.isWhitespace() -> i++ // les blancs ne changent pas le « précédent »
                else -> {
                    precedentNonBlanc = c
                    i++
                }
            }
        }
        return -1
    }

    /**
     * La première occurrence de MOT (bords de mots) à partir de [depuis], en
     * ignorant chaînes, caractères et commentaires ; -1 si introuvable. Sert
     * à ancrer le mutant « import supprimé » sur la PREMIÈRE UTILISATION
     * réelle du nom simple (pas une mention javadoc `{@link}`).
     */
    internal fun premiereOccurrenceHorsCommentaires(source: String, mot: String, depuis: Int): Int {
        var i = depuis.coerceIn(0, source.length)
        val n = source.length
        while (i < n) {
            val c = source[i]
            when {
                c == '/' && i + 1 < n && source[i + 1] == '/' -> {
                    while (i < n && source[i] != '\n') i++
                }
                c == '/' && i + 1 < n && source[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(source[i] == '*' && source[i + 1] == '/')) i++
                    i++
                }
                c == '"' || c == '\'' -> {
                    val fin = c
                    i++
                    while (i < n && source[i] != fin && source[i] != '\n') {
                        if (source[i] == '\\') i++
                        i++
                    }
                }
                source.startsWith(mot, i) -> {
                    val avant = i == 0 || !source[i - 1].isJavaIdentifierPart()
                    val apres = i + mot.length >= n || !source[i + mot.length].isJavaIdentifierPart()
                    if (avant && apres) return i
                    i++
                }
                else -> i++
            }
        }
        return -1
    }
}

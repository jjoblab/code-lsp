// CodeIDE — catalogue de versions Gradle (module :kotlin, vague V-G —
// ADR 0130 : le DISQUE est la source de vérité, jamais la sync) : un
// parseur TOML TOLÉRANT du fichier `libs.versions.toml` (sections
// [versions]/[libraries]/[bundles]/[plugins], alias EXACTS, imbrication
// par groupe avec normalisation tirets/points) et les accès `libs.…`
// qu'il sert en complétion/navigation/diagnostics bornés. Code NEUF
// jjoblab (clean-room — tolérant par construction : une erreur de
// syntaxe se replie en SILENCE, jamais une cascade ; catalogue absent
// ou invalide → `libs.` non complété, G4 bornes).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Une ENTRÉE du catalogue de versions : l'alias EXACT (tel qu'écrit dans
 * le TOML), la section qui le porte, sa LIGNE (navigation G5) et le
 * chemin d'accès normalisé (`libs.` + groupes + alias, tirets et points
 * équivalents).
 *
 * @property alias l'alias exact (`androidx-core-ktx`) ;
 * @property section la section TOML (`libraries`, `versions`, `bundles`,
 * `plugins`) ;
 * @property ligne la ligne (0-based) de l'entrée dans le TOML ;
 * @property chemin le chemin d'accès normalisé complet
 * (`libs.androidx.core.ktx` — les tirets deviennent des points de
 * groupe, le dernier segment porte l'alias ORIGINAL) ;
 * @property versionRef la référence `version.ref` (`null` sinon — la
 * navigation G5 saute à l'entrée `[versions]` pointée).
 */
internal class EntreeCatalogue internal constructor(
    internal val alias: String,
    internal val section: String,
    internal val ligne: Int,
    internal val chemin: String,
    internal val versionRef: String?,
    /** Les SEGMENTS DE CLÉ (groupe imbriqué par points, alias EXACT en dernier) : la segmentation de la complétion G4. */
    internal val segmentsCle: List<String> = emptyList(),
)

/**
 * Le catalogue de versions Gradle lu du disque (ADR 0130 — Option 1 :
 * lecture disque SANS sync, parseur TOML tolérant dédié, PAS les
 * accesseurs générés par Gradle).
 *
 * Le parseur lit les sections `[versions]`, `[libraries]`, `[bundles]`,
 * `[plugins]` : chaque entrée `alias = "…"`, `alias = { … }` ou
 * `alias.ref = "…"` porte son ALIAS EXACT et sa LIGNE. Tout ce qui ne
 * se parse pas est IGNORÉ silencieusement (une entrée hostile ne casse
 * jamais le catalogue — D3).
 */
internal object CatalogueGradle {

    /** Le nom canonique du catalogue (convention Gradle). */
    private const val NOM_CATALOGUE: String = "libs.versions.toml"

    /** Les sections servies par le catalogue. */
    private val SECTIONS = setOf("versions", "libraries", "bundles", "plugins")

    /**
     * Lit le catalogue du répertoire donné : `gradle/libs.versions.toml`
     * (convention Gradle), sinon `libs.versions.toml` à la racine.
     *
     * @param repertoire le répertoire du document `.kts` (le module ou
     * la racine) ;
     * @return les entrées ; VIDE si le catalogue est absent (le silence
     * est la borne honnête, G4).
     */
    internal fun lire(repertoire: Path): List<EntreeCatalogue> {
        val gradle = repertoire.resolve("gradle").resolve(NOM_CATALOGUE)
        val racine = repertoire.resolve(NOM_CATALOGUE)
        val fichier = when {
            Files.isRegularFile(gradle) -> gradle
            Files.isRegularFile(racine) -> racine
            else -> return emptyList()
        }
        return lireFichier(fichier)
    }

    /**
     * Parse les entrées d'un fichier TOML (tolérant : les lignes
     * hostiles sont ignorées).
     *
     * @param fichier le fichier catalogue ;
     * @return les entrées des sections connues, en ordre du fichier.
     */
    private fun lireFichier(fichier: Path): List<EntreeCatalogue> {
        val lignes = try {
            Files.readAllLines(fichier)
        } catch (_: Exception) {
            return emptyList()
        }
        val entrees = mutableListOf<EntreeCatalogue>()
        var section = ""
        for (indice in lignes.indices) {
            val ligne = lignes[indice].trim()
            if (ligne.isEmpty() || ligne.startsWith("#")) continue
            // La section : `[libraries]` (les tables imbriquées
            // `[libraries.androidx]` portent le préfixe de groupe) ; une
            // section inconnue devient la section courante (les entrées
            // suivantes n'y comptent pas — le parseur reprend à la
            // section connue suivante).
            if (ligne.startsWith("[")) {
                section = ligne.trim('[', ']').trim().lowercase()
                continue
            }
            if (section !in SECTIONS && section.substringBefore('.') !in SECTIONS) continue
            // L'entrée : `alias = …` — l'alias est le premier identifiant
            // avant `=` (un point dans la clé est un groupe imbriqué).
            val egale = ligne.indexOf('=')
            if (egale <= 0) continue
            val cle = ligne.substring(0, egale).trim()
            if (cle.isEmpty()) continue
            val groupe = if (cle.contains('.')) cle.substringBeforeLast('.') else ""
            val alias = cle.substringAfterLast('.')
            if (alias.isEmpty()) continue
            val valeur = ligne.substring(egale + 1).trim()
            // La référence de version : `version.ref = "…"` dans une
            // entrée de bibliothèque (navigation G5 vers [versions]).
            val versionRef = extraireVersionRef(valeur)
            val chemin = construireChemin(groupe, alias, section)
            val segmentsCle = mutableListOf<String>()
            if (groupe.isNotEmpty()) segmentsCle.addAll(groupe.split('.'))
            if (section == "plugins") segmentsCle.add("plugins")
            segmentsCle.add(alias)
            entrees.add(EntreeCatalogue(alias, section, indice, chemin, versionRef, segmentsCle))
        }
        return entrees
    }

    /**
     * La référence `version.ref` d'une valeur d'entrée TOML
     * (`{ module = "…", version.ref = "core" }` → `core`).
     */
    private fun extraireVersionRef(valeur: String): String? {
        val ref = "version.ref"
        val indice = valeur.indexOf(ref)
        if (indice < 0) return null
        val reste = valeur.substring(indice + ref.length)
        val egale = reste.indexOf('=')
        if (egale < 0) return null
        val apres = reste.substring(egale + 1).trim()
        if (!apres.startsWith("\"")) return null
        val fin = apres.indexOf('"', 1)
        if (fin <= 1) return null
        return apres.substring(1, fin)
    }

    /**
     * Le chemin d'accès NORMALISÉ d'une entrée (la convention des
     * accesseurs Gradle) : `libs.` + TOUS les tirets de la clé deviennent
     * des points (`androidx-core-ktx` → `libs.androidx.core.ktx`), la
     * clé imbriquée par points (`androidx.core-ktx` sous
     * `[libraries.androidx]` → `libs.androidx.core.ktx`), et la section
     * `plugins` porte son préfixe (`android-application` →
     * `libs.plugins.android.application`).
     */
    private fun construireChemin(groupe: String, alias: String, section: String): String {
        val segments = mutableListOf<String>()
        if (groupe.isNotEmpty()) segments.addAll(groupe.split('.'))
        if (section == "plugins") segments.add("plugins")
        segments.add(alias)
        var chemin = "libs"
        for (segment in segments) {
            chemin += "." + segment.replace('-', '.')
        }
        return chemin
    }

    /**
     * Les entrées CANDIDATES d'un accès `libs.…` au préfixe donné : les
     * chemins normalisés qui CONTINUENT le préfixe frappé (chaque
     * segment s'offre : `libs.androidx.|` propose `core`, `appcompat`…
     * ET les groupes plus profonds) — la complétion propose les SEGMENTS
     * suivants, pas les chemins entiers (miroir de la complétion par
     * groupes de la référence).
     *
     * @param entrees les entrées du catalogue ;
     * @param prefixe le préfixe frappé après `libs.` (`androidx.`) ;
     * @return les segments suivants distincts, triés.
     */
    internal fun segmentsSuivants(entrees: List<EntreeCatalogue>, prefixe: String): List<String> {
        if (prefixe.contains(' ')) return emptyList()
        val prefixeNorme = prefixe.replace('-', '.')
        val complets = prefixeNorme.removeSuffix(".").split('.').filter { it.isNotEmpty() }
        val attendu = complets.joinToString(".")
        val segments = mutableSetOf<String>()
        for (entree in entrees) {
            // L'appariement se fait sur le CHEMIN NORMALISÉ (les tirets
            // y valent des points — la convention des accesseurs Gradle :
            // l'alias `androidx-core-ktx` se frappe `libs.androidx.core.ktx`).
            val chemin = entree.chemin.removePrefix("libs.")
            if (attendu.isNotEmpty()) {
                if (!chemin.startsWith("$attendu.")) continue
            }
            val reste = if (attendu.isEmpty()) chemin else chemin.removePrefix("$attendu.")
            // Le SEGMENT SUIVANT du chemin normalisé (`libs.|` propose
            // `androidx`, `libs.androidx.|` propose `core` — miroir des
            // accesseurs Gradle, un segment par point).
            reste.substringBefore('.').takeIf { it.isNotEmpty() }?.let { segments.add(it) }
        }
        return segments.sorted()
    }

    /**
     * L'entrée EXACTE d'un accès complet `libs.…` : le chemin normalisé
     * égal (les tirets équivalents aux points — normalisation des deux
     * bords).
     *
     * @param entrees les entrées du catalogue ;
     * @param acces l'accès écrit (`libs.androidx.core.ktx`) ;
     * @return l'entrée exacte ; `null` si l'accès ne désigne AUCUNE
     * entrée (la preuve d'absence sert le diagnostic G6).
     */
    internal fun entreeExacte(entrees: List<EntreeCatalogue>, acces: String): EntreeCatalogue? {
        val normalise = acces.removePrefix("libs.")
        return entrees.firstOrNull { it.chemin.removePrefix("libs.") == normalise }
    }

    /**
     * La ligne (0-based) de l'entrée `[versions]` référencée par une
     * entrée (`version.ref`) — la navigation G5 vers la version.
     *
     * @param entrees les entrées du catalogue ;
     * @param reference le nom référencé (`core`) ;
     * @return la ligne de l'entrée `versions` ; `null` si absente.
     */
    internal fun ligneVersion(entrees: List<EntreeCatalogue>, reference: String): Int? =
        entrees.firstOrNull { it.section == "versions" && it.alias == reference }?.ligne
}

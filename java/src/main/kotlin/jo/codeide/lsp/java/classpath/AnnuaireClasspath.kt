package jo.codeide.lsp.java.classpath

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Genre binaire d'un type tel que lu dans un fichier de classe (étage N2 —
 * le filtrage grammatical de la complétion, cahier fiche 007).
 *
 * La distinction [ANNOTATION] (un `@interface`) d'avec [INTERFACE] est
 * portée par l'indicateur `AccAnnotation` du format de classe ; un enum
 * reste aussi une classe — le genre rendu est le plus SPÉCIFIQUE.
 */
public enum class GenreType {
    /** Classe concrète instanciable. */
    CLASSE,

    /** Classe abstraite (non instanciable après `new`). */
    CLASSE_ABSTRAITE,

    /** Interface (ni `new`, ni `extends` d'une classe). */
    INTERFACE,

    /** Type annotation (`@interface`). */
    ANNOTATION,

    /** Enum (labels de `switch`, valeurs d'attribut d'annotation). */
    ENUM,
}

/** Une entrée de type énumérée depuis le classpath. */
public class EntreeType(
    /** Nom simple (sans paquet, sans `$`). */
    public val nomSimple: String,
    /** FQCN en points. */
    public val fqcn: String,
    /** Origine de l'entrée (diagnostic/trace). */
    public val origine: Origine,
)

/** Origine d'une entrée de type de l'annuaire. */
public enum class Origine {
    /** Fichier source d'une racine du module. */
    SOURCE,

    /** Entrée d'un jar du classpath. */
    JAR,

    /** Entrée de l'image jrt. */
    JRT,
}

/**
 * Annuaire du classpath (étage N2 — complétion & classement) : l'énumération
 * des PAQUETS et des TYPES que la complétion parcourt — enfants directs d'un
 * paquet (règles d'or « ni plus ni moins », fiche 044), types directs d'un
 * paquet, et recherche par préfixe de nom simple à travers tout le classpath
 * (la complétion de type, fiches 007/008/035).
 *
 * Construction en trois phases, toutes paresseuses et jouées UNE fois :
 *
 * 1. **segments** — sources balayées + tables centrales des jars + SEGMENTS
 *    jrt dérivés de la carte paquet→modules déjà construite par [AccesJrt]
 *    à l'ouverture (aucune traversée nouvelle) ;
 * 2. **types d'un paquet** — listing à la demande du répertoire jrt du
 *    paquet, fusionné aux types sources/jars de la phase 1 ;
 * 3. **index par préfixe** — traversée intégrale de l'image jrt (une seule
 *    passe), uniquement à la première recherche de type par préfixe.
 *
 * Les genres binaires ([genreDe]) et l'épreuve Throwable ([estThrowable])
 * ne lisent les fichiers de classe qu'À LA DEMANDE, sur les courtes listes
 * candidates — jamais en bloc.
 *
 * Les types IMBRIQUÉS (`X$Y`) ne sont JAMAIS énumérés comme entrées propres
 * (fiche 032 : le filtre `$` s'applique en aval de l'index ; fiche 036 : le
 * popup spontané ne doit plus proposer les `R$*`).
 *
 * @param racines racines sources du module (paquets = chemins relatifs).
 * @param jars jars du classpath (ordre préservé, doublons écartés).
 * @param accesJrt accès jrt (null en régime instantané — l'annuaire ne voit
 *   alors que sources et jars).
 */
public class AnnuaireClasspath(
    private val racines: List<Path>,
    jars: List<Path>,
    private val accesJrt: AccesJrt?,
) {

    /** Jars balayés (dédupliqués, ordre préservé). */
    private val jars: List<Path> = jars.distinct()

    /** Structure d'un paquet : types directs par nom simple. */
    private class Paquet {
        val types = mutableMapOf<String, Origine>()
    }

    /** Carte paquet → types directs (sources + jars, puis jrt à la demande). */
    private val paquets = ConcurrentHashMap<String, Paquet>()

    /** Segments jrt déjà fusionnés dans la carte des segments. */
    @Volatile
    private var segmentsJrtFusionnes: Boolean = false

    /** Vrai quand l'index par préfixe est construit (phase 3). */
    @Volatile
    private var indexConstruit: Boolean = false

    /** Index nom simple (minuscules) → entrées (phase 3). */
    @Volatile
    private var indexParNom: Map<String, List<EntreeType>> = emptyMap()

    /** Genres mémorisés par FQCN (lecture à la demande). */
    private val genres = ConcurrentHashMap<String, GenreType?>()

    /** Verdicts Throwable mémorisés par FQCN. */
    private val throwables = ConcurrentHashMap<String, Boolean>()

    /**
     * Segments ENFANTS DIRECTS d'un paquet (règle d'or 1 — fiche 044) : un
     * SEGMENT simple, jamais un paquet profond ; le dédoublonnage par
     * segment (règle 2) est inhérent à la structure (ensemble).
     *
     * @param paquet nom du paquet en points (p. ex. `android`) ; la chaîne
     *   vide désigne les paquets de PREMIER NIVEAU.
     * @return l'ensemble des segments directement sous le paquet.
     */
    public fun segmentsEnfants(paquet: String): Set<String> {
        garantirPhasesUnEtDeux()
        val sousPaquet = if (paquet.isEmpty()) "" else "$paquet."
        val segments = mutableSetOf<String>()
        for (paquetConnu in paquets.keys) {
            if (paquetConnu.isEmpty()) continue
            if (sousPaquet.isNotEmpty() && !paquetConnu.startsWith(sousPaquet)) continue
            val reste = paquetConnu.removePrefix(sousPaquet)
            val premierPoint = reste.indexOf('.')
            val segment = if (premierPoint < 0) reste else reste.substring(0, premierPoint)
            if (segment.isNotEmpty()) segments.add(segment)
        }
        return segments
    }

    /**
     * Types DIRECTEMENT posés dans un paquet (règle d'or 4 — fiche 044 :
     * « ni plus ni moins ») : les types du paquet lui-même, jamais ceux des
     * sous-paquets ; les imbriqués `$` ne remontent jamais.
     *
     * @param paquet nom du paquet en points.
     * @return les entrées de types directs (nom simple + FQCN + origine).
     */
    public fun typesDirects(paquet: String): List<EntreeType> {
        garantirPhasesUnEtDeux()
        val structure = paquets[paquet] ?: paquets.computeIfAbsent(paquet) { Paquet() }
        if (!structure.types.isEmpty()) return rendu(structure, paquet)
        // Paquet jrt non encore listé : listing à la demande (phase 2).
        val jrt = accesJrt
        if (jrt != null && jrt.estPaquet(paquet)) {
            synchronized(this) {
                if (structure.types.isEmpty()) {
                    for (nom in jrt.nomsClassesDuPaquet(paquet)) {
                        if (nom.contains('$')) continue
                        structure.types.putIfAbsent(nom, Origine.JRT)
                    }
                }
            }
        }
        return rendu(structure, paquet)
    }

    /**
     * Recherche de types par PRÉFIXE de nom simple à travers tout le
     * classpath (sources + jars + jrt), insensible à la casse.
     *
     * La première appelée paie la construction de l'index complet (une seule
     * traversée de l'image jrt et des tables centrales) ; les suivantes sont
     * des lectures directes.
     *
     * @param prefixe préfixe du nom simple (≥ 1 caractère).
     * @param limite borne du nombre d'entrées rendues.
     * @return les entrées dont le nom simple démarre par le préfixe.
     */
    public fun typesParPrefixe(prefixe: String, limite: Int): List<EntreeType> {
        if (prefixe.isEmpty()) return emptyList()
        garantirIndex()
        val cle = prefixe.lowercase()
        val resultats = mutableListOf<EntreeType>()
        for ((nom, entrees) in indexParNom) {
            if (nom.startsWith(cle)) resultats.addAll(entrees)
        }
        return resultats.sortedWith(compareBy({ it.nomSimple.length }, { it.nomSimple })).take(limite)
    }

    /**
     * FQCN portant EXACTEMENT un nom simple (proposition d'auto-import —
     * fiche 008 : la complétion de type traverse le classpath).
     *
     * @param nomSimple nom simple exact.
     * @return les FQCN candidats (jamais imbriqués `$`).
     */
    public fun fqcnPourNomSimple(nomSimple: String): List<String> {
        garantirIndex()
        return indexParNom[nomSimple.lowercase()]?.map { it.fqcn } ?: emptyList()
    }

    /**
     * Genre binaire d'un type (fiche 007 : le filtrage grammatical) — lu
     * À LA DEMANDE dans le fichier de classe et mémorisé ; null si le type
     * est introuvable ou illisible (le filtrage le traite alors comme une
     * classe ordinaire, verdict conservateur).
     *
     * @param fqcn FQCN en points.
     * @return le genre, ou null si indéterminable.
     */
    public fun genreDe(fqcn: String): GenreType? {
        if (genres.containsKey(fqcn)) return genres[fqcn]
        val octets = lireOctets(fqcn)
        if (octets == null) {
            // Type introuvable en binaire (source locale, classe illisible) :
            // verdict conservateur de CLASSE, mémorisé — le filtrage
            // grammatical ne bloque jamais sur un type de statut inconnu.
            genres[fqcn] = GenreType.CLASSE
            return GenreType.CLASSE
        }
        val genre = try {
            genreDepuisOctets(octets)
        } catch (_: Exception) {
            null
        }
        genres[fqcn] = genre
        return genre
    }

    /**
     * Épreuve Throwable (fiche 007 : clauses `throws`/`catch`) : remonte la
     * chaîne des superclasses lues dans les fichiers de classe, bornée à
     * [PROFONDEUR_MAX] maillons pour garantir la terminaison sur un
     * classpath fautif.
     *
     * @param fqcn FQCN candidat.
     * @return vrai si le type descend de `java.lang.Throwable`.
     */
    public fun estThrowable(fqcn: String): Boolean {
        throwables[fqcn]?.let { return it }
        var courant = fqcn
        var verdict = false
        for (i in 0 until PROFONDEUR_MAX) {
            if (courant == "java.lang.Throwable") {
                verdict = true
                break
            }
            if (courant == "java.lang.Object" || courant.isEmpty()) break
            throwables[courant]?.let {
                verdict = it
                break
            }
            val octets = lireOctets(courant) ?: break
            val superNom = try {
                ClassFileReader.read(octets, "x.class").superclassName?.let { String(it) }
            } catch (_: Exception) {
                null
            } ?: break
            courant = superNom.replace('/', '.')
        }
        throwables[fqcn] = verdict
        return verdict
    }

    /**
     * Constantes d'un enum lu dans son fichier de classe (fiche 007 : labels
     * de `switch`, valeurs d'annotation) — les champs statiques portant
     * l'indicateur `AccEnum` et déclarés par l'enum lui-même.
     *
     * @param fqcn FQCN de l'enum attendu.
     * @return les noms des constantes dans l'ordre du fichier de classe.
     */
    public fun constantesEnum(fqcn: String): List<String> {
        val octets = lireOctets(fqcn) ?: return emptyList()
        return try {
            val lecteur = ClassFileReader.read(octets, "x.class")
            (lecteur.fields ?: emptyArray())
                .filter { champ ->
                    (champ.modifiers and ClassFileConstants.AccStatic) != 0 &&
                        (champ.modifiers and ClassFileConstants.AccEnum) != 0
                }
                .map { champ -> String(champ.name) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------ //
    // Construction des phases
    // ------------------------------------------------------------------ //

    /** Rendu trié des entrées d'un paquet. */
    private fun rendu(structure: Paquet, paquet: String): List<EntreeType> =
        structure.types.map { (nom, origine) -> EntreeType(nom, "$paquet.$nom", origine) }
            .sortedBy { it.nomSimple }

    /** Phase 1 : sources + jars + segments jrt (une fois). */
    private fun garantirPhasesUnEtDeux() {
        if (paquets.isEmpty() || !segmentsJrtFusionnes) {
            synchronized(this) {
                if (paquets.isEmpty()) {
                    construireSources()
                    construireJars()
                }
                if (!segmentsJrtFusionnes) {
                    fusionnerSegmentsJrt()
                }
            }
        }
    }

    /** Phase 3 : index par préfixe (une fois, traverse l'image jrt). */
    private fun garantirIndex() {
        if (indexConstruit) return
        synchronized(this) {
            if (indexConstruit) return
            garantirPhasesUnEtDeux()
            // La traversée jrt intégrale remplace le listing par paquet.
            val jrt = accesJrt
            if (jrt != null) {
                jrt.parcourirClasses { cheminClasse -> enregistrer(cheminClasse, Origine.JRT) }
            }
            construireIndexParNom()
            indexConstruit = true
        }
    }

    /** Enregistre une entrée de classe dans la carte (paquet en points). */
    private fun enregistrer(cheminClasse: String, origine: Origine) {
        val derniereBarre = cheminClasse.lastIndexOf('/')
        if (derniereBarre <= 0) return
        val nomFichier = cheminClasse.substring(derniereBarre + 1)
        if (!nomFichier.endsWith(".class")) return
        val nomSimple = nomFichier.removeSuffix(".class")
        if (nomSimple.contains('$')) return // imbriqués jamais proposés (fiche 032/036)
        val paquet = cheminClasse.substring(0, derniereBarre).replace('/', '.')
        val structure = paquets.computeIfAbsent(paquet) { Paquet() }
        structure.types.putIfAbsent(nomSimple, origine)
        // Les ancêtres du paquet existent aussi (règle isPackage, fiche 008).
        var ancetre = paquet
        while (ancetre.contains('.')) {
            ancetre = ancetre.substringBeforeLast('.')
            paquets.computeIfAbsent(ancetre) { Paquet() }
        }
    }

    /** Balaye les racines sources : chaque `.java` est un type de premier niveau. */
    private fun construireSources() {
        for (racine in racines) {
            if (!Files.isDirectory(racine)) continue
            try {
                Files.walk(racine).use { flux ->
                    flux.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                        .forEach { fichier ->
                            val relatif = racine.relativize(fichier)
                            if (relatif.nameCount == 0) return@forEach
                            val segments = (0 until relatif.nameCount).map { relatif.getName(it).toString() }
                            val nomFichier = segments.last()
                            val paquet = segments.dropLast(1).joinToString(".")
                            if (paquet.isEmpty()) return@forEach
                            enregistrer("$paquet/${nomFichier.removeSuffix(".java")}.class", Origine.SOURCE)
                        }
                }
            } catch (_: Exception) {
                // racine illisible : écartée silencieusement (parité N1)
            }
        }
    }

    /** Balaye la table centrale de chaque jar (un seul passage par jar). */
    private fun construireJars() {
        for (jar in jars) {
            if (!Files.isRegularFile(jar)) continue
            try {
                for (nom in LecteurZipBrut.scanNames(jar)) {
                    if (!nom.endsWith(".class")) continue
                    enregistrer(nom, Origine.JAR)
                }
            } catch (_: Exception) {
                // jar illisible : écarté
            }
        }
    }

    /** Fusionne les SEGMENTS jrt depuis la carte paquet→modules (sans FS). */
    private fun fusionnerSegmentsJrt() {
        val jrt = accesJrt ?: return
        for (paquet in jrt.paquetsConnus()) {
            paquets.computeIfAbsent(paquet) { Paquet() }
        }
        segmentsJrtFusionnes = true
    }

    /** Construit l'index nom simple (minuscules) → entrées. */
    private fun construireIndexParNom() {
        val index = HashMap<String, MutableList<EntreeType>>()
        for ((paquet, structure) in paquets) {
            for ((nomSimple, origine) in structure.types) {
                index.getOrPut(nomSimple.lowercase()) { mutableListOf() }
                    .add(EntreeType(nomSimple, "$paquet.$nomSimple", origine))
            }
        }
        indexParNom = index
    }

    /** Octets du fichier de classe d'un FQCN (jars puis jrt). */
    private fun lireOctets(fqcn: String): ByteArray? {
        val chemin = fqcn.replace('.', '/') + ".class"
        for (jar in jars) {
            if (!Files.isRegularFile(jar)) continue
            try {
                return LecteurZipBrut.readSingle(jar, chemin)
            } catch (_: Exception) {
                // absent de CE jar : essayer le suivant
            }
        }
        return try {
            accesJrt?.octets(fqcn)
        } catch (_: Exception) {
            null
        }
    }

    /** Genre lu depuis les indicateurs d'accès d'un fichier de classe. */
    private fun genreDepuisOctets(octets: ByteArray): GenreType? {
        val lecteur = ClassFileReader.read(octets, "x.class")
        val drapeaux = lecteur.modifiers
        return when {
            (drapeaux and ClassFileConstants.AccAnnotation) != 0 -> GenreType.ANNOTATION
            (drapeaux and ClassFileConstants.AccEnum) != 0 -> GenreType.ENUM
            (drapeaux and ClassFileConstants.AccInterface) != 0 -> GenreType.INTERFACE
            (drapeaux and ClassFileConstants.AccAbstract) != 0 -> GenreType.CLASSE_ABSTRAITE
            else -> GenreType.CLASSE
        }
    }

    private companion object {
        /** Borne de la remontée de chaîne de superclasses (garde anti-boucle). */
        private const val PROFONDEUR_MAX: Int = 24
    }
}

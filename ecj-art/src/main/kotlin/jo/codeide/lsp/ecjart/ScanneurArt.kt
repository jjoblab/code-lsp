package jo.codeide.lsp.ecjart

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Références de sensibilité ART (android.jar API 34) — listes noires du
 * scanner, portées du spike de l'étude (`spike/scripts/ScanArt.kt`,
 * résultats consignés dans `spike/resultats/scan-art-v3-ecj.txt`).
 *
 * Écrites à partir de la JVMS et de la documentation Android (niveaux d'API) ;
 * aucun code tiers consulté pour l'écriture (R6.4). Ces listes constituent
 * la référence des motifs que la CI confronte à la liste de shims versionnée
 * (ADR 0018) : tout motif constaté dans le jar entrant non couvert par une
 * directive = échec de build.
 */
public object ReferencesArt {

    /**
     * Classes sensibles : préfixe de nom interne JVM (slashs) → verdict.
     * La correspondance est exacte ou par préfixe de paquet/membre
     * (`motif`, `motif/…`, `motif$…`).
     */
    public val classesNoires: Map<String, String> = linkedMapOf(
        "sun/misc/Unsafe" to "sun.misc.Unsafe — restreint/hidden sur ART",
        "sun/nio/ch/DirectBuffer" to "sun.nio.ch.DirectBuffer (cleaner) — ABSENT d'ART",
        "java/lang/foreign" to "java.lang.foreign (MemorySegment/Arena) — JDK 19+, ABSENT",
        "javax/management" to "javax.management — ABSENT d'ART",
        "java/lang/management" to "java.lang.management — ABSENT d'ART",
        "jdk/internal" to "jdk.internal.* — ABSENT d'ART",
        "java/awt" to "java.awt — ABSENT d'ART (shim requis)",
        "javax/swing" to "javax.swing — ABSENT d'ART (shim requis)",
        "com/sun" to "com.sun.* — partiellement absent",
        "java/lang/invoke/MethodHandles" to "MethodHandles — présent sur ART (API 26+), usages Lookup à vérifier",
        "java/lang/invoke/VarHandle" to "VarHandle — API 33+ sur ART (minSdk 26 ⇒ incompatible)",
        "java/lang/Runtime\$Version" to "java.lang.Runtime.Version — ABSENT d'ART (shim du patcheur ecj-art)",
        "java/lang/StackWalker" to "java.lang.StackWalker — ABSENT d'ART (shim du patcheur ecj-art)",
        "java/lang/Module" to "java.lang.Module (JPMS) — ABSENT d'ART",
        "java/util/ServiceLoader" to "ServiceLoader — présent sur ART, packaging META-INF/services à vérifier",
        "java/nio/MappedByteBuffer" to "MappedByteBuffer — présent mais map() limité sur ART",
    )

    /**
     * Méthodes sensibles : (propriétaire, nom simple) → verdict avec niveau
     * d'API Android minimal. La correspondance se fait sur le couple
     * (propriétaire, nom), descripteur non discriminant.
     */
    public val methodesNoires: Map<Pair<String, String>, String> = linkedMapOf(
        ("java/io/InputStream" to "readAllBytes") to "InputStream.readAllBytes — API 33+ (minSdk 26 ⇒ shim)",
        ("java/io/InputStream" to "readNBytes") to "InputStream.readNBytes — API 33+ (minSdk 26 ⇒ shim)",
        ("java/io/InputStream" to "transferTo") to "InputStream.transferTo — API 33+ (minSdk 26 ⇒ shim)",
        ("java/lang/Runtime" to "version") to "Runtime.version() → Runtime.Version — ABSENT d'ART",
    )
}

/** Verdict de sensibilité pour une référence constatée dans un constant pool. */
internal fun verdictClasse(reference: String): String? {
    if (reference.startsWith("[")) return null // tableaux : pas une référence de classe
    for ((motif, verdict) in ReferencesArt.classesNoires) {
        if (reference == motif || reference.startsWith("$motif/") || reference.startsWith("$motif\$")) {
            return verdict
        }
    }
    return null
}

/**
 * Une invocation référencée dans un constant pool (CONSTANT_Methodref ou
 * CONSTANT_InterfaceMethodref).
 *
 * @param proprietaire nom interne JVM de la classe déclarante.
 * @param methode nom simple de la méthode.
 * @param descripteur descripteur JVM de la méthode.
 */
public data class Invocation(
    public val proprietaire: String,
    public val methode: String,
    public val descripteur: String,
)

/**
 * Constant pool d'une classe, analysé en une passe (JVMS §4).
 *
 * Contient tout ce dont le patcheur a besoin pour cibler ses passes et tout
 * ce dont la vérification a besoin pour compter les motifs sensibles.
 *
 * @param chemin nom de l'entrée dans le jar.
 * @param nomInterne nom interne JVM de la classe analysée (`this_class`).
 * @param classesReferencees occurrences des entrées CONSTANT_Class,
 *   nom interne → nombre d'occurrences dans le pool.
 * @param invocations occurrences des entrées Methodref/InterfaceMethodref.
 * @param chaines chaînes CONSTANT_Utf8 (descripteurs, noms, signatures…) —
 *   conservées pour la détection de mentions de type dans les descripteurs.
 */
public class AnalyseClasse(
    public val chemin: String,
    public val nomInterne: String,
    public val classesReferencees: Map<String, Int>,
    public val invocations: Map<Invocation, Int>,
    public val chaines: List<String>,
) {
    /**
     * Indique si le pool mentionne le type [classe] — soit par une entrée
     * CONSTANT_Class, soit par toute chaîne Utf8 contenant sa forme de
     * descripteur (`L<classe>;`), qui couvre signatures de méthodes et de
     * champs, attributs Signature et types génériques.
     */
    public fun mentionne(classe: String): Boolean {
        if (classesReferencees.containsKey(classe)) return true
        val forme = "L$classe;"
        return chaines.any { it.contains(forme) }
    }
}

/**
 * Motif sensible agrégé sur l'ensemble d'un jar.
 *
 * @param cle identification lisible (nom de classe ou `propriétaire.méthode`).
 * @param verdict justification (API Android minimale ou absence).
 * @param occurrences nombre total d'occurrences constatées dans les pools.
 */
public data class MotifSensible(
    public val cle: String,
    public val verdict: String,
    public val occurrences: Int,
)

/**
 * Rapport de scan d'un jar : analyse de chaque classe + motifs sensibles
 * agrégés (même convention de comptage que le scanner du spike — les chiffres
 * sont directement comparables à `spike/resultats/scan-art-v3-ecj.txt`).
 */
public class RapportScan(
    public val jar: Path,
    public val analyses: List<AnalyseClasse>,
) {
    /** Motifs sensibles agrégés (classes puis méthodes, ordre des listes noires). */
    public fun motifs(): List<MotifSensible> {
        val resultat = mutableListOf<MotifSensible>()
        for ((motif, verdict) in ReferencesArt.classesNoires) {
            var total = 0
            for (a in analyses) for ((ref, n) in a.classesReferencees) {
                if (ref == motif || ref.startsWith("$motif/") || ref.startsWith("$motif\$")) total += n
            }
            if (total > 0) resultat.add(MotifSensible(motif, verdict, total))
        }
        for ((cle, verdict) in ReferencesArt.methodesNoires) {
            var total = 0
            for (a in analyses) for ((inv, n) in a.invocations) {
                if (inv.proprietaire == cle.first && inv.methode == cle.second) total += n
            }
            if (total > 0) resultat.add(MotifSensible("${cle.first}.${cle.second}", verdict, total))
        }
        return resultat
    }

    /** Classes dont le pool mentionne le type donné (ciblage du remap). */
    public fun classesMentionnant(classe: String): List<String> =
        analyses.filter { it.mentionne(classe) }.map { it.chemin }

    /** Classes invoquant exactement (propriétaire, méthode, descripteur). */
    public fun classesInvoquant(proprietaire: String, methode: String, descripteur: String): List<String> =
        analyses.filter { it.invocations.containsKey(Invocation(proprietaire, methode, descripteur)) }
            .map { it.chemin }
}

/**
 * Scanner de compatibilité ART par analyse des constant pools (JVMS §4) —
 * portage bibliothèque du scanner du spike, écrit à partir de la JVMS.
 *
 * Limites documentées (héritées du spike) : le bytecode n'est pas parcouru —
 * les usages dynamiques (réflexion, ServiceLoader via `META-INF/services`)
 * échappent à l'analyse ; le comptage d'occurrences porte sur les entrées de
 * pool, pas sur les sites d'appel (une entrée peut servir plusieurs sites).
 */
public class ScanneurArt {

    /**
     * Analyse toutes les entrées `.class` d'un jar.
     *
     * @param jar chemin du jar à scanner.
     * @return le rapport (une [AnalyseClasse] par entrée de classe valide ;
     *   les entrées illisibles sont ignorées silencieusement — le format jar
     *   amont est réputé sain, le patcheur refuse de toute façon un jar dont
     *   le re-scan ne serait pas vierge).
     */
    public fun scanner(jar: Path): RapportScan {
        val analyses = mutableListOf<AnalyseClasse>()
        ZipFile(jar.toFile()).use { z ->
            for (entree in z.entries()) {
                if (!entree.name.endsWith(".class")) continue
                val brut = z.getInputStream(entree).readBytes()
                analyserClasse(entree.name, brut)?.let(analyses::add)
            }
        }
        return RapportScan(jar, analyses)
    }

    /**
     * Analyse le constant pool d'un fichier `.class` (une passe).
     *
     * @return `null` si l'entrée n'est pas un fichier de classe valide.
     */
    private fun analyserClasse(chemin: String, brut: ByteArray): AnalyseClasse? {
        try {
            val flux = DataInputStream(ByteArrayInputStream(brut))
            if (flux.readInt() != 0xCAFEBABE.toInt()) return null
            flux.readUnsignedShort() // minor_version
            flux.readUnsignedShort() // major_version
            val n = flux.readUnsignedShort()

            val utf8 = HashMap<Int, String>(n)
            val classesIdx = mutableListOf<Int>()          // index d'Utf8 pointés par des entrées Class
            val classEntries = HashMap<Int, Int>()         // idx entrée Class → idx Utf8 du nom
            val nameAndType = HashMap<Int, Pair<Int, Int>>()
            val methodrefs = mutableListOf<Pair<Int, Int>>()
            val chaines = mutableListOf<String>()

            var i = 1
            while (i < n) {
                when (val tag = flux.readUnsignedByte()) {
                    1 -> { // Utf8
                        val ln = flux.readUnsignedShort()
                        val octets = ByteArray(ln)
                        flux.readFully(octets)
                        val s = String(octets, Charsets.UTF_8)
                        utf8[i] = s
                        chaines.add(s)
                    }
                    7 -> { // Class
                        val nameIdx = flux.readUnsignedShort()
                        classEntries[i] = nameIdx
                        classesIdx.add(nameIdx)
                    }
                    8, 16, 19, 20 -> flux.readUnsignedShort() // String, MethodType, Module, Package
                    3, 4 -> flux.readInt()                     // Integer, Float
                    5, 6 -> { flux.readLong(); i++ }           // Long, Double (2 slots)
                    9, 10, 11 -> {                             // Fieldref, Methodref, InterfaceMethodref
                        val c = flux.readUnsignedShort()
                        val nta = flux.readUnsignedShort()
                        if (tag == 10 || tag == 11) methodrefs.add(c to nta)
                    }
                    12 -> { // NameAndType
                        val nomIdx = flux.readUnsignedShort()
                        val descIdx = flux.readUnsignedShort()
                        nameAndType[i] = nomIdx to descIdx
                    }
                    17, 18 -> { flux.readUnsignedShort(); flux.readUnsignedShort() } // Dynamic, InvokeDynamic
                    15 -> { flux.readUnsignedByte(); flux.readUnsignedShort() }      // MethodHandle
                    else -> return null // tag inconnu : entrée rejetée
                }
                i++
            }

            // this_class (après le pool) pour le nom interne de la classe.
            flux.readUnsignedShort() // access_flags
            val thisIdx = flux.readUnsignedShort()
            val nomInterne = utf8[classEntries[thisIdx] ?: thisIdx] ?: chemin.removeSuffix(".class")

            // Agrégations par occurrences.
            val classesRef = LinkedHashMap<String, Int>()
            for (idx in classesIdx) {
                val ref = utf8[idx] ?: continue
                classesRef[ref] = (classesRef[ref] ?: 0) + 1
            }
            val invocations = LinkedHashMap<Invocation, Int>()
            for ((cIdx, nta) in methodrefs) {
                val proprietaire = utf8[classEntries[cIdx] ?: continue] ?: continue
                val paire = nameAndType[nta] ?: continue
                val nom = utf8[paire.first] ?: continue
                val desc = utf8[paire.second] ?: continue
                val inv = Invocation(proprietaire, nom, desc)
                invocations[inv] = (invocations[inv] ?: 0) + 1
            }
            return AnalyseClasse(chemin, nomInterne, classesRef, invocations, chaines)
        } catch (_: Exception) {
            return null // entrée tronquée ou corrompue : ignorée (documenté)
        }
    }
}

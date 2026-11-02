// CodeIDE — étude moteur Java PSI : analyse statique de compatibilité ART
// d'une pile de jarres (§6.a du prompt de mission : « scan des références
// JDK absentes d'android.jar, liste des shims nécessaires »).
//
// Méthode : analyse du constant pool de chaque .class (JVMS §4) —
//   - CONSTANT_Class          → références de classes (APIs absentes d'ART) ;
//   - CONSTANT_Methodref      → références de méthodes ciblées (readAllBytes…) ;
//   - method_info             → comptage des méthodes déclarées (proxy dex) ;
//   - version de format       → exigence JVM minimale.
// Écrit à partir de la JVMS et de la documentation Android (android.jar
// API 34) — aucun code tiers consulté pour l'écriture (R6.4).
//
// Limites documentées : le bytecode n'est pas parcouru (les usages dynamiques
// — réflexion, ServiceLoader via META-INF/services — échappent à l'analyse) ;
// le comptage de méthodes compte les déclarées, pas les référencées (le dex
// compte les deux : borne basse).
//
// Usage : java -cp build-scan:<kotlin-stdlib> ScanArtKt <jar> [<jar>...]
// Sortie : rapport texte sur stdout.

import java.io.DataInputStream
import java.util.zip.ZipFile
import kotlin.system.exitProcess

// --- APIs absentes ou sensibles sur ART (android.jar API 34) ---
// classes : motif du nom interne JVM (slashs) → verdict
val NOIR_CLASSES: Map<String, String> = linkedMapOf(
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

// méthodes : (classe, méthode) → verdict (API Android minimale)
val NOIR_METHODES: Map<Pair<String, String>, String> = linkedMapOf(
    ("java/io/InputStream" to "readAllBytes") to "InputStream.readAllBytes — API 33+ (minSdk 26 ⇒ shim)",
    ("java/io/InputStream" to "readNBytes") to "InputStream.readNBytes — API 33+ (minSdk 26 ⇒ shim)",
    ("java/io/InputStream" to "transferTo") to "InputStream.transferTo — API 33+ (minSdk 26 ⇒ shim)",
    ("java/lang/Runtime" to "version") to "Runtime.version() → Runtime.Version — ABSENT d'ART",
)

fun regarderClasse(ref: String): String? {
    if (ref.startsWith("[")) return null // tableaux
    for ((motif, verdict) in NOIR_CLASSES) {
        if (ref == motif || ref.startsWith("$motif/") || ref.startsWith("$motif\$")) return verdict
    }
    return null
}

class Stats {
    var classes = 0
    var methodes = 0L
    val versions = LinkedHashMap<Int, Int>()
    val classesNoires = LinkedHashMap<String, Pair<Int, String>>() // ref → (nb, verdict)
    val methodesNoires = LinkedHashMap<String, Pair<Int, String>>() // "cls.meth" → (nb, verdict)
}

fun analyser(jar: java.io.File): Pair<Stats, List<String>> {
    val stats = Stats()
    val erreurs = mutableListOf<String>()
    ZipFile(jar).use { z ->
        val entrees = z.entries().asSequence().filter { it.name.endsWith(".class") }
        for (entree in entrees) {
            try {
                // Une seule passe : tout est parsé depuis le flux mémoisé
                val brut = z.getInputStream(entree).readBytes()
                val flux = DataInputStream(java.io.ByteArrayInputStream(brut))
                val magic = flux.readInt()
                if (magic != 0xCAFEBABE.toInt()) throw IllegalArgumentException("pas un .class")
                flux.readUnsignedShort() // minor
                val major = flux.readUnsignedShort()
                val n = flux.readUnsignedShort()
                val utf8 = HashMap<Int, String>(n)
                val classesIdx = mutableListOf<Int>()
                val classEntries = HashMap<Int, Int>() // idx de l'entrée Class → name_idx
                val nameAndType = HashMap<Int, Pair<Int, Int>>() // idx → (nameIdx, descIdx)
                val methodrefs = mutableListOf<Pair<Int, Int>>() // classIdx → nameAndTypeIdx
                var i = 1
                while (i < n) {
                    when (val tag = flux.readUnsignedByte()) {
                        1 -> { // Utf8
                            val ln = flux.readUnsignedShort()
                            val octets = ByteArray(ln)
                            flux.readFully(octets)
                            utf8[i] = String(octets, Charsets.UTF_8)
                        }
                        7, 8, 16, 19, 20 -> { // Class, String, MethodType, Module, Package
                            if (tag == 7) {
                                val nameIdx = flux.readUnsignedShort()
                                classEntries[i] = nameIdx
                                classesIdx.add(nameIdx)
                            } else {
                                flux.readUnsignedShort()
                            }
                        }
                        3, 4 -> flux.readInt() // Integer, Float
                        5, 6 -> { flux.readLong(); i++ } // Long, Double (2 slots)
                        9, 10, 11 -> { // Fieldref, Methodref, InterfaceMethodref (u2+u2)
                            val c = flux.readUnsignedShort()
                            val nta = flux.readUnsignedShort()
                            if (tag == 10 || tag == 11) methodrefs.add(c to nta)
                        }
                        12 -> { // NameAndType : name_idx + descriptor_idx
                            val nomIdx = flux.readUnsignedShort()
                            val descIdx = flux.readUnsignedShort()
                            nameAndType[i] = nomIdx to descIdx
                        }
                        17, 18 -> { flux.readUnsignedShort(); flux.readUnsignedShort() } // Dynamic, InvokeDynamic
                        15 -> { flux.readUnsignedByte(); flux.readUnsignedShort() } // MethodHandle
                        else -> throw IllegalArgumentException("tag constant pool inconnu $tag")
                    }
                    i++
                }

                // --- refs de classes ---
                for (idx in classesIdx) {
                    val ref = utf8[idx] ?: continue
                    val verdict = regarderClasse(ref) ?: continue
                    val precedent = stats.classesNoires[ref]
                    stats.classesNoires[ref] = ((precedent?.first ?: 0) + 1) to verdict
                }
                // --- refs de méthodes ciblées (résolution Class + NameAndType) ---
                for ((cIdx, nta) in methodrefs) {
                    val cls = utf8[classEntries[cIdx] ?: continue] ?: continue
                    val paire = nameAndType[nta] ?: continue
                    val nom = utf8[paire.first] ?: continue
                    val verdict = NOIR_METHODES[cls to nom] ?: continue
                    val cle = "$cls.$nom"
                    val precedent = stats.methodesNoires[cle]
                    stats.methodesNoires[cle] = ((precedent?.first ?: 0) + 1) to verdict
                }

                // --- comptage des méthodes déclarées ---
                flux.readUnsignedShort() // access
                flux.readUnsignedShort() // this
                flux.readUnsignedShort() // super
                repeat(flux.readUnsignedShort()) { flux.readUnsignedShort() }
                repeat(flux.readUnsignedShort()) { // fields
                    flux.readUnsignedShort(); flux.readUnsignedShort(); flux.readUnsignedShort()
                    val nbAttrs = flux.readUnsignedShort()
                    repeat(nbAttrs) {
                        flux.readUnsignedShort()
                        val ln = flux.readInt()
                        flux.skipBytes(ln)
                    }
                }
                var nbMethodes = 0
                repeat(flux.readUnsignedShort()) { // methods
                    flux.readUnsignedShort(); flux.readUnsignedShort(); flux.readUnsignedShort()
                    nbMethodes++
                    val nbAttrs = flux.readUnsignedShort()
                    repeat(nbAttrs) {
                        flux.readUnsignedShort()
                        val ln = flux.readInt()
                        flux.skipBytes(ln)
                    }
                }
                stats.classes++
                stats.methodes += nbMethodes
                stats.versions[major] = (stats.versions[major] ?: 0) + 1
            } catch (e: Exception) {
                erreurs.add("${entree.name}: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
    return stats to erreurs
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage : ScanArt <jar> [<jar>...]")
        exitProcess(2)
    }
    println("=== Analyse statique de compatibilité ART (scanner Kotlin, constant pools .class)")
    println("Environnement : JVM ${System.getProperty("java.version")}, android.jar de référence API 34")
    println()
    val totClasses = arrayOf(0)
    var totMethodes = 0L
    val totNoires = LinkedHashMap<String, Pair<Int, String>>()
    val totMethodesNoires = LinkedHashMap<String, Pair<Int, String>>()
    for (chemin in args) {
        val jar = java.io.File(chemin)
        val (stats, erreurs) = analyser(jar)
        println("--- ${jar.name} (${jar.length() / 1024} Ko)")
        println("    classes .class    : ${stats.classes}")
        println("    méthodes déclarées: ${stats.methodes}")
        val versions = stats.versions.entries.joinToString(", ") { (m, c) ->
            (if (m > 44) "JVM ${m - 44}" else "major $m") + " ×$c"
        }
        println("    versions de format: $versions")
        if (stats.classesNoires.isEmpty() && stats.methodesNoires.isEmpty()) {
            println("    références sensibles ART : AUCUNE")
        } else {
            println("    RÉFÉRENCES SENSIBLES ART :")
            for (entree in stats.classesNoires) {
                println("      - ${entree.key} (${entree.value.first}x) → ${entree.value.second}")
            }
            for (entree in stats.methodesNoires) {
                println("      - ${entree.key} (${entree.value.first}x) → ${entree.value.second}")
            }
        }
        if (erreurs.isNotEmpty()) println("    ERREURS de parsing : ${erreurs.size} (ex : ${erreurs.first()})")
        println()
        totClasses[0] += stats.classes
        totMethodes += stats.methodes
        for ((ref, paire) in stats.classesNoires) totNoires[ref] = paire
        for ((cle, paire) in stats.methodesNoires) totMethodesNoires[cle] = paire
    }
    println("=== TOTAL pile : ${totClasses[0]} classes, $totMethodes méthodes déclarées")
    println("=== TOTAL motifs sensibles distincts : ${totNoires.size + totMethodesNoires.size}" +
        " (classes ${totNoires.size}, méthodes ${totMethodesNoires.size})")
}

package jo.codeide.lsp.kotlin

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Garde de compatibilité JDK 17 du bytecode du module — née de l'incident
 * appareil du 30/10 (banc-appareil, moto g06 / Termux openjdk 17.0.19) :
 * `java.lang.NoSuchMethodError: java.util.List.removeLast()` levé au
 * PREMIER parse (KotlinFlexLexer.popState, ligne 774 de la grammaire
 * vendorée) alors que bac à sable et CI (JDK 21) exécutaient le MÊME jar
 * sans erreur.
 *
 * Mécanisme constaté (expérience menée au compilateur 2.2.10 embarqué) :
 * `MutableList.removeLast()` résout vers le MEMBRE `java.util.List.removeLast`
 * (JDK 21 SEUL) dès que le JDK de compilation le porte — QUEL QUE SOIT
 * jvmTarget (17 et 21 émettent l'un et l'autre l'InterfaceMethodref dans du
 * bytecode 61). `jvmTarget` ne borne PAS le pool de constantes : seul le JDK
 * de compilation compte — d'où la toolchain 17 du module (build.gradle.kts),
 * qui rend le membre invisible à la résolution.
 *
 * La présente garde lit MÉCANIQUEMENT le pool de constantes du code compilé
 * (main + grammaire vendorée) et échoue au moindre receveur
 * `java/util/List` (ou `java/util/SequencedCollection`) appelé avec un nom
 * de la famille SequencedCollection (JDK 21 — absente de JDK 17 ET
 * d'android.jar). Elle est le miroir Java du scan-art.py (MOTIFS_MEMBRES,
 * même corrélation déclarant↔membre) appliqué aux classes AVANT assemblage
 * du jar — les trois couches de défense : compiler SOUS 17 (résolution),
 * cette garde (statique), scan-art.py en CI (jar assemblé).
 *
 * Receveurs NON sensibles (vérifiés avant adoption du motif) :
 * `java/util/Deque`, `java/util/ArrayDeque`, `java/util/LinkedList` portent
 * ces méthodes depuis Java 6 ; `kotlin.collections.ArrayDeque` est une
 * implémentation Kotlin pure (membre propre, aucun JDK requis) — c'est
 * pourquoi le code neuf peut utiliser `ArrayDeque.removeLast()` sans risque.
 */
class CompatibiliteJdk17Test {

    /** Noms de la famille SequencedCollection (introduite par Java 21). */
    private val nomsSensibles = setOf(
        "addFirst", "addLast", "getFirst", "getLast",
        "removeFirst", "removeLast", "reversed",
    )

    /** Receveurs dont la famille SequencedCollection n'existe qu'en JDK 21. */
    private val receveursSensibles = setOf(
        "java/util/List",
        "java/util/SequencedCollection",
    )

    /**
     * Rend les références sensibles (receveur.méthode (fichier)) du pool de
     * constantes d'un class-file. Parseur minimal miroir de scan-art.py :
     * seuls les tags utiles à la corrélation déclarant↔membre sont gardés ;
     * un tag inconnu ABANDONNE honnêtement la classe (aucune levée).
     */
    private fun referencesSensibles(fichier: File): List<String> {
        val donnees = fichier.readBytes()
        val lecture = DataInputStream(ByteArrayInputStream(donnees))
        if (lecture.readInt() != 0xCAFEBABE.toInt()) return emptyList()
        lecture.skip(2) // version mineure
        lecture.skip(2) // version majeure
        val nbConst = lecture.readUnsignedShort()
        val utf8s = arrayOfNulls<String>(nbConst)
        val classes = HashMap<Int, Int>() // tag 7 (Class) → indice Utf8 du nom
        val nats = HashMap<Int, Pair<Int, Int>>() // tag 12 → (nom, descripteur)
        val refs = ArrayList<Pair<Int, Int>>() // tags 10/11 → (classe, nat)

        fun sauter(n: Int) = lecture.readFully(ByteArray(n))

        var idx = 1
        while (idx < nbConst) {
            when (lecture.readUnsignedByte()) {
                1 -> { // Utf8 : u2 longueur + octets (décode avec remplacement)
                    val lng = lecture.readUnsignedShort()
                    utf8s[idx] = String(ByteArray(lng).also(lecture::readFully), Charsets.UTF_8)
                }
                7 -> classes[idx] = lecture.readUnsignedShort() // Class
                12 -> { // NameAndType : u2 nom + u2 descripteur
                    val nom = lecture.readUnsignedShort()
                    val desc = lecture.readUnsignedShort()
                    nats[idx] = nom to desc
                }
                10, 11 -> { // Methodref/InterfaceMethodref : u2 classe + u2 nat
                    val classe = lecture.readUnsignedShort()
                    val nat = lecture.readUnsignedShort()
                    refs += classe to nat
                }
                8, 16, 19, 20 -> sauter(2) // String, MethodType, Module, Package
                15 -> sauter(3) // MethodHandle : u1 kind + u2 index
                9, 3, 4, 17, 18 -> sauter(4) // Fieldref, Int, Float, Dynamic…
                5, 6 -> { // Long, Double : u8, DEUX entrées du pool
                    sauter(8)
                    idx++
                }
                else -> return emptyList() // tag inconnu : abandon honnête
            }
            idx++
        }

        val trouves = mutableListOf<String>()
        for ((indiceClasse, indiceNat) in refs) {
            val nomClasse = utf8s[classes[indiceClasse] ?: continue] ?: continue
            val nat = nats[indiceNat] ?: continue
            val nom = utf8s[nat.first] ?: continue
            if (nomClasse in receveursSensibles && nom in nomsSensibles) {
                trouves += "$nomClasse.$nom (${fichier.path})"
            }
        }
        return trouves
    }

    @Test
    fun `bytecode---aucune-methode-jdk21-sequencedcollection-sur-java-util-list`() {
        // Les sorties de compilation du module : code neuf (main) ET
        // grammaire vendorée (vendor) — les deux vivent dans le jar moteur.
        val racines = listOf(
            File("build/classes/kotlin/main"),
            File("build/classes/kotlin/vendor"),
        )
        val introuvables = racines.filterNot { it.isDirectory }
        assertTrue(
            introuvables.isEmpty(),
            "répertoires de classes introuvables : $introuvables — " +
                "ce garde doit s'exécuter APRÈS compilation (tâche :kotlin:test standard)",
        )
        val trouves = racines.flatMap { racine ->
            racine.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .flatMap(::referencesSensibles)
        }
        assertTrue(
            trouves.isEmpty(),
            "le bytecode du module référence des méthodes JDK 21 (famille " +
                "SequencedCollection sur java.util.List — absentes de JDK 17 et " +
                "d'android.jar, NoSuchMethodError appareil garanti) :\n" +
                trouves.joinToString("\n") +
                "\n→ compiler sous JDK 17 (toolchain du module) ou remplacer " +
                "l'appel par un chemin JDK 17 sûr (removeAt(size - 1), " +
                "kotlin.collections.ArrayDeque…)",
        )
    }
}

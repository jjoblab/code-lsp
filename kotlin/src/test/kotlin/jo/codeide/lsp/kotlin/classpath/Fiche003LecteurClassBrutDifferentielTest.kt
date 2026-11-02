package jo.codeide.lsp.kotlin.classpath

import java.io.File
import java.util.SortedSet
import java.util.zip.ZipFile
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fiche 003 — LecteurClassBrutDifferentielTest : TEST DIFFÉRENTIEL du
 * lecteur de classes contre la RÉFLEXION JDK sur DEUX corpus réels :
 * android.jar (AOSP API 16, Maven Central — le SDK Android est absent des
 * environnements de test JVM) et le jar kotlin-stdlib du classpath de
 * test. La réflexion sur classes CHARGÉES (jamais initialisées —
 * `Class.forName(…, initialize=false)`) sert de référence : aucune
 * dépendance ASM n'entre dans le module (entité indépendante D6).
 *
 * Normalisation du contrat (pas une tolérance de comparaison) :
 *  - le fichier de classe d'une INTERFACE porte super_class =
 *    java.lang.Object alors que la réflexion rend `getSuperclass()` null
 *    — l'attendu est normalisé côté référence ;
 *  - les classes INTERNES (`$`) et `module-info` sont hors périmètre (le
 *    lecteur les lit par ailleurs — fiche 004).
 *
 * Second volet : le DÉCODEUR — les appelables de premier niveau du
 * stdlib sont confrontés aux méthodes STATIQUES PUBLIQUES des façades
 * `*Kt` (réflexion). La comparaison par NOMS est structurellement bruitée
 * (extensions côté récepteur, propriétés en champs statiques, typealias
 * sans miroir bytecode, synthétiques `$annotations` privés) : le test
 * ARRÊTE les invariants forts (volume, appelables usuels présents,
 * extensions réputées) et IMPRIME les statistiques d'écart.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/).
 */
@Suppress("LoopWithTooManyJumpStatements", "CyclomaticComplexMethod")
class Fiche003LecteurClassBrutDifferentielTest {

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): File =
        File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** android.jar réel (AOSP API 16, Maven Central) — ancre android.util.Log. */
    private fun jarAndroid(): File =
        File(
            android.util.Log::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /**
     * Les classes de premier niveau (`$` exclu) d'un préfixe, dans un jar.
     */
    private fun classesDe(
        jar: File,
        prefixe: String,
    ): List<Pair<String, ByteArray>> {
        val octets = mutableListOf<Pair<String, ByteArray>>()
        ZipFile(jar).use { zip ->
            for (entree in zip.entries().asSequence()) {
                if (!entree.name.endsWith(".class") || entree.name == "module-info.class") continue
                val fqcn = entree.name.removeSuffix(".class").replace('/', '.')
                if ('$' in fqcn || !fqcn.startsWith(prefixe)) continue
                octets += fqcn to zip.getInputStream(entree).use { it.readBytes() }
            }
        }
        return octets
    }

    /** Le bilan d'un différentiel de structure. */
    private class Bilan(
        val total: Int,
        val illisibles: List<String>,
        val ecarts: List<String>,
    )

    /** Différentiel structure (superclasse + interfaces) lecteur contre réflexion. */
    private fun differentielStructure(
        jar: File,
        prefixe: String,
    ): Bilan {
        val illisibles = mutableListOf<String>()
        val ecarts = mutableListOf<String>()
        var total = 0
        for ((fqcn, octets) in classesDe(jar, prefixe)) {
            val lue = LecteurClassBrut.lire(octets)
            total++
            if (lue == null) {
                illisibles += fqcn
                continue
            }
            val reflechie = Class.forName(fqcn, false, javaClass.classLoader)
            // Normalisation : une INTERFACE porte super_class = Object dans
            // le fichier, null en réflexion.
            val attenduSuper = reflechie.superclass?.name ?: "java.lang.Object"
            if (lue.superFqcn != attenduSuper) {
                ecarts += "$fqcn: super lu=${lue.superFqcn} attendu=$attenduSuper"
            }
            val attenduesInterfaces = reflechie.interfaces.map { it.name }.sorted()
            if (lue.interfaces.sorted() != attenduesInterfaces) {
                ecarts += "$fqcn: interfaces lu=${lue.interfaces.sorted()} attendu=$attenduesInterfaces"
            }
        }
        return Bilan(total, illisibles, ecarts)
    }

    @Test
    fun `android-jar---structure-identique-a-la-reflexion,-zero-classe-illisible`() {
        val bilan = differentielStructure(jarAndroid(), "android.")
        println("=== DIFFERENTIEL STRUCTURE android.jar: ${bilan.total} classes")
        assertTrue(bilan.total >= 500, "corpus chargé (au moins 500 classes) : ${bilan.total}")
        assertTrue(bilan.illisibles.isEmpty(), "aucune classe illisible : ${bilan.illisibles.take(5)}")
        assertTrue(bilan.ecarts.isEmpty(), "aucun écart super/interfaces : ${bilan.ecarts.take(5)}")
    }

    @Test
    fun `kotlin-stdlib---structure-identique-a-la-reflexion,-zero-classe-illisible`() {
        val bilan = differentielStructure(jarStdlib(), "kotlin.")
        println("=== DIFFERENTIEL STRUCTURE kotlin-stdlib: ${bilan.total} classes")
        assertTrue(bilan.total >= 500, "corpus chargé (au moins 500 classes) : ${bilan.total}")
        assertTrue(bilan.illisibles.isEmpty(), "aucune classe illisible : ${bilan.illisibles.take(5)}")
        assertTrue(bilan.ecarts.isEmpty(), "aucun écart super/interfaces : ${bilan.ecarts.take(5)}")
    }

    /**
     * Les méthodes STATIQUES PUBLIQUES des façades `*Kt` du stdlib — la
     * RÉFLEXION du volet métadonnées (initialisation jamais exécutée).
     */
    private fun statiquesDesFacades(jar: File): SortedSet<String> {
        val facades =
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.endsWith("Kt.class") }
                    .map { it.name.removeSuffix(".class").replace('/', '.') }
                    .filter { '$' !in it }
                    .toList()
            }
        val statiques = sortedSetOf<String>()
        for (fqcn in facades) {
            Class.forName(fqcn, false, javaClass.classLoader).declaredMethods
                .filter { methode ->
                    java.lang.reflect.Modifier.isStatic(methode.modifiers) &&
                        java.lang.reflect.Modifier.isPublic(methode.modifiers)
                }
                .forEach { methode -> statiques += methode.name }
        }
        return statiques
    }

    @Test
    fun `metadonnees-stdlib---appelables-usuels-presents,-extensions-reputees,-volume-rendu`() {
        val jar = jarStdlib()
        val appelables = DecodeurMetadonnees.decoder(jar, KindEntreeKotlin.JAR)
        val noms = appelables.map { it.nom }.toSet()
        val recepteurs = appelables.filter { it.recepteur != null }.map { it.nom }.toSet()
        // Volume : le stdlib porte des milliers d'appelables déclarés.
        assertTrue(appelables.size >= 3000, "volume d'appelables lu (>= 3000) : ${appelables.size}")
        // Fonctions de premier niveau USUELLES (aucune ne doit manquer).
        val usuelles =
            listOf(
                "println", "print", "error", "require", "check", "TODO", "repeat", "with",
                "runCatching", "lazy", "listOf", "mapOf", "setOf", "emptyList",
                "maxOf", "minOf", "abs", "buildList", "sortedSetOf",
            )
        val manquantes = usuelles.filterNot { it in noms }
        assertTrue(manquantes.isEmpty(), "fonctions top-level usuelles lues (manquantes: $manquantes)")
        // Extensions RÉPUTÉES à récepteur CLASSE (récepteur non null côté métadonnées).
        val reputees = listOf("filter", "map", "forEach")
        val extensionsManquantes = reputees.filterNot { it in recepteurs }
        assertTrue(extensionsManquantes.isEmpty(), "extensions réputées lues (manquantes: $extensionsManquantes)")
        // Extensions à récepteur PARAMÈTRE DE TYPE (`fun <T> T.let(…)`) :
        // indexées comme appelables TOP-LEVEL du paquet `kotlin`
        // (récepteur non-classe — sous-rapport assumé K4 ; les diagnostics
        // les résolvent par la voie 6 + imports par défaut).
        val reputeesT = listOf("let", "also", "apply", "run", "takeIf", "takeUnless")
        val topManquantes = reputeesT.filterNot { it in noms }
        assertTrue(
            topManquantes.isEmpty(),
            "extensions à récepteur T lues comme top-level (manquantes: $topManquantes)",
        )
        // Statistiques d'écart contre la référence (réflexion sur les
        // façades *Kt statiques publiques) — IMPRIMÉES pour le rapport,
        // la divergence par noms étant structurelle (propriétés → champs
        // statiques, typealias → aucun miroir, extensions → récepteur).
        val statiques = statiquesDesFacades(jar)
        val tops = appelables.filter { it.recepteur == null }.map { it.nom }.toSet()
        println(
            "=== DIFFERENTIEL METADONNEES stdlib: appelables=${appelables.size} " +
                "topDistincts=${tops.size} extensionsDistinctes=${recepteurs.size} " +
                "statiquesFacade=${statiques.size} horsLecteur=${statiques - tops} " +
                "horsFacade=${tops - statiques}",
        )
    }
}

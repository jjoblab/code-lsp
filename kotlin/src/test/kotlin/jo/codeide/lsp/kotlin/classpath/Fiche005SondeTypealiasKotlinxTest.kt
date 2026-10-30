package jo.codeide.lsp.kotlin.classpath

import java.io.File
import java.util.zip.ZipFile
import kotlin.metadata.KmClassifier
import kotlin.metadata.Visibility
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.visibility
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fiche 005 — SondeTypealiasKotlinxTest : SONDE du rejeu §4 (le rapport
 * faux positifs laissait le `typealias` de bibliothèques NON CONCLUANT —
 * « aucun typealias public JVM du stdlib n'est isolable par la sonde. À
 * rejouer sur un corpus kotlinx (p. ex. kotlinx.serialization) »).
 *
 * Cette sonde ISOLE les typealiases publics de DEUX corpus kotlinx RÉELS
 * (Maven Central, testImplementation) en lisant leurs métadonnées par la
 * bibliothèque kotlin-metadata (la même que le moteur) — SANS réflexion :
 * un typealias n'a PAS de miroir bytecode (c'est précisément l'angle mort
 * structurel du différentiel de la fiche 003).
 *
 * Corpus (ancres de localisation, jamais résolues en code) :
 *  - kotlinx-datetime-jvm 0.6.1 : TROIS alias publics — `DayOfWeek` =
 *    java.time.DayOfWeek, `Month` = java.time.Month, `ZoneOffset` =
 *    kotlinx.datetime.FixedOffsetTimeZone (cible DANS le corpus) ;
 *  - kotlinx-serialization-core-jvm 1.9.0 : le corpus CITÉ par le
 *    rapport — la sonde a établi qu'il porte ZÉRO alias public
 *    (l'hypothèse n'y est PAS éprouvable).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/).
 */
@Suppress("LoopWithTooManyJumpStatements", "CyclomaticComplexMethod", "NestedBlockDepth")
class Fiche005SondeTypealiasKotlinxTest {

    /** Le jar kotlinx-datetime du classpath de test. */
    private fun jarDatetime(): File =
        File(
            kotlinx.datetime.DateTimePeriod::class.java.protectionDomain
                .codeSource
                .location
                .toURI(),
        )

    /** Le jar kotlinx-serialization-core du classpath de test. */
    private fun jarSerialization(): File =
        File(
            kotlinx.serialization.KSerializer::class.java.protectionDomain
                .codeSource
                .location
                .toURI(),
        )

    /** Un typealias public isolé du corpus. */
    private data class TypeAliasPublic(
        val fqcn: String,
        val cible: String,
    )

    /**
     * Les typealiases PUBLICS d'un jar : les déclarations `typeAliases`
     * des métadonnées des FACADES de fichiers (`*Kt`) — un typealias
     * n'existe NULLE PART ailleurs (aucun miroir bytecode, aucune classe
     * dédiée : c'est l'angle mort structurel).
     */
    private fun typealiasesPublics(jar: File): List<TypeAliasPublic> {
        val resultat = mutableListOf<TypeAliasPublic>()
        ZipFile(jar).use { zip ->
            val entrees =
                zip
                    .entries()
                    .asSequence()
                    .filter { it.name.endsWith(".class") && it.name != "module-info.class" }
            for (entree in entrees) {
                val octets = zip.getInputStream(entree).use { it.readBytes() }
                val infos = LecteurClassBrut.lire(octets) ?: continue
                val metadonnees = infos.metadonnees ?: continue
                val kmPackage =
                    when (val lu = KotlinClassMetadata.readLenient(metadonnees)) {
                        is KotlinClassMetadata.FileFacade -> lu.kmPackage
                        is KotlinClassMetadata.MultiFileClassPart -> lu.kmPackage
                        else -> continue
                    }
                for (alias in kmPackage.typeAliases) {
                    if (alias.visibility != Visibility.PUBLIC) continue
                    val cible =
                        (alias.underlyingType.classifier as? KmClassifier.Class)
                            ?.name
                            ?.replace('/', '.')
                            ?: "?"
                    resultat += TypeAliasPublic("${infos.fqcn.substringBeforeLast('.')}.${alias.name}", cible)
                }
            }
        }
        return resultat.sortedBy { it.fqcn }
    }

    @Test
    fun `kotlinx-datetime---les-typealiases-publics-du-corpus-sont-isoles`() {
        val aliases = typealiasesPublics(jarDatetime())
        println("=== SONDE TYPEALIAS kotlinx-datetime: ${aliases.size} publics")
        aliases.forEach { println("  ${it.fqcn} = ${it.cible}") }
        assertTrue(aliases.isNotEmpty(), "corpus chargé (au moins 1 typealias public) : $aliases")
        // Le FAIT DU CORPUS, verrouillé (les métadonnées font foi) : ces
        // trois alias publics servent la reproduction de l'étape B.
        assertTrue(
            aliases.any { it.fqcn == "kotlinx.datetime.DayOfWeek" && it.cible == "java.time.DayOfWeek" } &&
                aliases.any { it.fqcn == "kotlinx.datetime.Month" && it.cible == "java.time.Month" } &&
                aliases.any {
                    it.fqcn == "kotlinx.datetime.ZoneOffset" &&
                        it.cible == "kotlinx.datetime.FixedOffsetTimeZone"
                },
            "les 3 alias publics attendus sont portés " +
                "(DayOfWeek/Month vers java.time, ZoneOffset vers le corpus) : $aliases",
        )
    }

    @Test
    fun `kotlinx-serialization-core---la-sonde-dit-ce-que-le-corpus-porte`() {
        val aliases = typealiasesPublics(jarSerialization())
        println("=== SONDE TYPEALIAS kotlinx-serialization-core: ${aliases.size} publics")
        aliases.forEach { println("  ${it.fqcn} = ${it.cible}") }
        // VERDICT du rejeu §4 sur le corpus CITÉ par le rapport : ZÉRO
        // alias public dans kotlinx-serialization-core 1.9.0 — l'hypothèse
        // n'y est PAS éprouvable (impression pour la traçabilité si une
        // future version du corpus en porte).
        assertTrue(
            aliases.isEmpty() && jarSerialization().isFile,
            "kotlinx-serialization-core ne porte AUCUN typealias public (fait du corpus, le jar répond) : $aliases",
        )
    }
}

package jo.codeide.lsp.java.inspections

import java.nio.file.Files
import java.nio.file.Path

/**
 * L'OUTIL CLI de compilation du segment compact de la base API (suite V-C2
 * — ADR 0024 : « compilée en segment CIDX binaire compact, ~1,7 Mo cible,
 * re-compilable bit-à-bit, empreinte versionnée »).
 *
 * Usage :
 * ```
 * java -cp <jar-du-moteur> jo.codeide.lsp.java.inspections.OutilSegmentBaseApi \
 *     <api-versions.xml[.gz]> <sortie.seg>
 * ```
 *
 * Sortie : la taille du segment écrit, l'empreinte SHA-256 du segment et
 * celle de la source (les DEUX signatures se consignent au déploiement —
 * le couple prouve la re-compilation bit-à-bit). Le script
 * `scripts/preparer-base-api.sh` enveloppe cet outil quand le jar du
 * moteur est disponible (`java/build/libs/` ou `JAR_BASE_API`).
 */
public object OutilSegmentBaseApi {

    /**
     * Le point d'entrée : compile la base et imprime les empreintes.
     *
     * @param arguments `[sourceXml, segmentSortie]`.
     */
    @JvmStatic
    public fun main(arguments: Array<String>) {
        if (arguments.size != 2) {
            System.err.println("Usage : OutilSegmentBaseApi <api-versions.xml[.gz]> <sortie.seg>")
            kotlin.system.exitProcess(2)
        }
        val source = Path.of(arguments[0])
        val cible = Path.of(arguments[1])
        if (!Files.isRegularFile(source)) {
            System.err.println("ERREUR : source introuvable : $source")
            kotlin.system.exitProcess(1)
        }
        val octets = BaseApiAndroid.compilerSegment(source, cible)
        val empreinteSegment = BaseApiAndroid.empreinte(cible)
        val empreinteSource = BaseApiAndroid.empreinte(source)
        println("Segment compact écrit : $cible ($octets octets)")
        println("Empreinte SHA-256 segment : $empreinteSegment")
        println("Empreinte SHA-256 source  : $empreinteSource")
    }
}

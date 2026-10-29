// PILOTE ORACLE du banc de parité croisé — étage T (ADR 0016).
//
// CE PROCESSUS exécute le moteur VENDORED de `codeide-lsp` (jar réel
// construit au commit épinglé par scripts/preparer-oracle.sh) comme
// ORACLE EN BOÎTE NOIRE : il ne touche QUE les points d'entrée publics
// documentés (`EcjCompilerEngine`/`EcjCompletionEngine` — la surface
// même que sollicitent les tests du vendored, source du cahier), reçoit
// le corpus JSON produit par l'orchestrateur, rend ses résultats JSON.
//
// ISOLEMENT : il vit dans son PROPRE sous-processus JVM — le moteur neuf
// et le vendored embarquent chacun leur ecj patché, jamais dans le même
// classpath. Ce pilote est compilé contre les STUBS (`bancStubs`) et ne
// porte AUCUNE dépendance du dépôt : classpath = jar vendored + lsp4j +
// gson (celles du vendored) + kotlin-stdlib.
//
// Usage (invoqué par BancPariteCroise, jamais à la main) :
//   java -cp <jar-vendored:lsp4j:gson:classes-pilote:kotlin-stdlib> \
//       jo.codeide.lsp.java.banc.DriverOracleKt \
//       <corpus.json> <resultats.json> <racine-sources>
package jo.codeide.lsp.java.banc

import jo.lspjava.ecj.EcjCompletionEngine
import jo.lspjava.ecj.EcjCompilerEngine
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Marqueur de caret du corpus (convention du cahier). */
private const val MARQUEUR = '¦'

fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("Usage : DriverOracleKt <corpus.json> <resultats.json> <racine-sources>")
        kotlin.system.exitProcess(2)
    }
    val cheminCorpus = Path.of(args[0])
    val cheminResultats = Path.of(args[1])
    val racineSources = Path.of(args[2])

    try {
        verifierOracleReel()

        // Bootstrap documenté du vendored : racines sources, classpath vide
        // (jrt du runtime seul — même plateforme que le corpus N2),
        // conformité 17 (défaut du moteur neuf).
        val moteur = EcjCompilerEngine(
            listOf(racineSources.toFile()),
            emptyList(),
            "17",
        )
        val completion = EcjCompletionEngine(moteur)

        val corpus = FormatEchange.lireCorpus(cheminCorpus.readText(StandardCharsets.UTF_8))

        fun rejouer(position: PositionBrute): ResultatPosition {
            val marque = position.source.indexOf(MARQUEUR)
            if (marque < 0) {
                error("position « ${position.nom} » sans marqueur de caret")
            }
            val texte = position.source.replace(MARQUEUR.toString(), "")
            // Contrat du vendored : null quand le contexte n'est pas
            // résolible — sérialisé comme liste VIDE (l'orchestrateur
            // compare les libellés, l'absence de réponse EST l'information).
            val items = completion.complete("/demo/Main.java", texte, marque) ?: emptyList()
            return ResultatPosition(position.nom, items.map { it.label() })
        }

        val resultats = corpus.positions.map(::rejouer) + corpus.succession.map(::rejouer)
        cheminResultats.writeText(FormatEchange.ecrireResultats(resultats), StandardCharsets.UTF_8)
        println(
            "pilote oracle : ${corpus.positions.size} positions + ${corpus.succession.size} frappes " +
                "rejouées contre codeide-lsp (jar ${nommerJarCharge()})",
        )
    } catch (t: Throwable) {
        System.err.println("PILOTE ORACLE — échec : ${t.javaClass.simpleName} : ${t.message}")
        t.printStackTrace(System.err)
        kotlin.system.exitProcess(2)
    }
}

/**
 * Garde anti-stub : la classe du moteur doit venir d'un JAR (le vendored),
 * jamais d'un répertoire de classes (les stubs de compilation — cf.
 * bancStubs). Un échec ici signifie un classpath mal assemblé, jamais une
 * régression du moteur.
 */
private fun verifierOracleReel() {
    val source = EcjCompilerEngine::class.java.protectionDomain.codeSource
        ?: error("codeSource illisible pour EcjCompilerEngine (classe système ?)")
    val emplacement = source.location.toString()
    check(emplacement.endsWith(".jar")) {
        "EcjCompilerEngine chargé depuis « $emplacement » (répertoire de classes — STUB, pas le jar vendored) : " +
            "le classpath du pilote doit porter .oracle/java-1.16.0.jar en tête (scripts/preparer-oracle.sh)"
    }
}

/** Le nom simple du jar vendored réellement chargé (journal de traçabilité). */
private fun nommerJarCharge(): String =
    EcjCompilerEngine::class.java.protectionDomain.codeSource!!.location.toString()
        .substringAfterLast('/')

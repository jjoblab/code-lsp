package jo.codeide.lsp.java.actionscode

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Severite

/**
 * Le déclassement honnête des imports Data Binding (étage N6 — cahier
 * fiche 051) : repro du constat production v0.107.0 — « The import
 * com.example.monip.databinding cannot be resolved » en ERROR sec sur un
 * projet Data Binding sans build préalable. Les classes `XxxBinding` ne
 * sont produites par AGP qu'AU BUILD (la sync ne les génère pas) : avant
 * tout build, l'import est STRUCTURELLEMENT non résoluble — l'ERROR est
 * exacte mais incurable côté utilisateur.
 *
 * Le correctif verrouillé (fiche 051) : au package Data Binding, le MÊME
 * downgrade honnête que la classe R — ERROR → HINT avec remède (« lance
 * un Build ») — avec COUPURE honnête : dès que la PREMIÈRE classe générée
 * existe (sources générées ou binaires du module), une faute de frappe
 * redevient une ERROR intégrale — le downgrade ne masque JAMAIS une vraie
 * erreur.
 *
 * La reconnaissance se fait par les ARGUMENTS STRUCTURÉS du problème
 * (arguments[0] = nom importé, stable entre locales — jamais par le
 * message localisé) et par la garde de SEGMENT : seul un VRAI segment
 * `.databinding` (des points des deux côtés) déclenche le downgrade — un
 * paquet homonyme (« mydatabinding ») reste ERROR intégrale.
 *
 * Miroir moteur de la couche de PUBLICATION : le downgrade vit dans la
 * publication (fiche 051 : « le downgrade vit dans la PUBLICATION, pas
 * dans le moteur ») — le moteur diagnose sans compromis, la publication
 * déclasse ; le cycle de vie LSP complet arrive à l'étage N8.
 */
public object DeclassementDatabinding {

    /** Le segment de paquet des classes générées par AGP. */
    private const val SEGMENT_DATABINDING = "databinding"

    /**
     * Déclasse les diagnostics d'import Data Binding non résolus (fiche 051).
     *
     * La coupure honnête sonde l'existence de classes générées : pour le
     * paquet `.databinding` visé, TOUTE classe présente (fichier `.java`
     * d'une racine source, entrée `.class` d'un jar du classpath) verrouille
     * le paquet en régime POST-BUILD — plus AUCUN downgrade sur ce paquet,
     * une faute de frappe redevient ERROR intégrale.
     *
     * @param diagnostics les diagnostics publiés par le moteur (sans
     *   compromis — le diagnose reste fidèle).
     * @param racines les racines sources du module (sources générées
     *   comprises).
     * @param jars les jars du classpath du module (binaires générés compris).
     * @return la liste publiée : chaque diagnostic d'import non résolu dont
     *   le paquet est un VRAI segment `.databinding` non peuplé est déclassé
     *   en HINT, son message EXPLIQUE le remède (contient « Data Binding »
     *   et « Build ») ; tous les autres diagnostics passent INTACTS.
     */
    public fun declasser(diagnostics: List<Diagnostic>, racines: List<Path>, jars: List<Path>): List<Diagnostic> {
        val paquetsVerrouilles = HashSet<String>()
        return diagnostics.map { diagnostic ->
            val paquetImporte = paquetDataBindingDe(diagnostic) ?: return@map diagnostic
            if (paquetsVerrouilles.contains(paquetImporte)) return@map diagnostic
            if (paquetPeuple(paquetImporte, racines, jars)) {
                // Coupure honnête : la première classe générée verrouille
                // le paquet — plus AUCUN downgrade dessus (fiche 051).
                paquetsVerrouilles.add(paquetImporte)
                return@map diagnostic
            }
            declasserDiagnostic(diagnostic, paquetImporte)
        }
    }

    /**
     * Le paquet Data Binding visé par un diagnostic — null si le diagnostic
     * n'est PAS un import non résolu vers un VRAI segment `.databinding`.
     *
     * Garde de segment (fiche 051) : le découpage en points ne reconnaît
     * que les VRAIS segments — « mydatabinding » est un identifiant unique,
     * jamais un segment `.databinding` ; un segment databinding SANS parent
     * (« databinding.Machin », paquet racine improbable) est écarté.
     *
     * Découverte figée N6 (sonde) : l'argument 0 d'ImportNotFound est le
     * PRÉFIXE non résoluble (« com.example.e.databinding » quand
     * com.example.e existe au source path — la forme SANS build comme la
     * forme post-build) — c'est lui qui porte le segment, stable entre
     * locales.
     */
    private fun paquetDataBindingDe(diagnostic: Diagnostic): String? {
        if (diagnostic.severite != Severite.ERREUR) return null
        if (FamilleProbleme.fromCode(diagnostic.code) != FamilleProbleme.UNRESOLVED_REFERENCE) return null
        val nom = diagnostic.arguments.firstOrNull() ?: return null
        val segments = nom.split('.')
        val index = segments.indexOf(SEGMENT_DATABINDING)
        if (index <= 0) return null
        return segments.subList(0, index + 1).joinToString(".")
    }

    /**
     * Le downgrade d'UN diagnostic : sévérité HINT (l'échelle moteur s'arrête
     * à INFO, son niveau le plus bas — projeté Hint LSP par la couche hôte),
     * message qui EXPLIQUE le remède (contient « Data Binding » — les
     * classes XxxBinding sont générées par AGP — et nomme l'action :
     * « Build »). Le code et les arguments STRUCTURÉS passent INTACTS : la
     * coupure honnête et le dispatch correcteur restent possibles sur le
     * diagnostic déclassé.
     */
    private fun declasserDiagnostic(diagnostic: Diagnostic, paquet: String): Diagnostic {
        val message = "L'import $paquet n'est pas encore résolu — les classes Data Binding " +
            "(XxxBinding) sont générées par AGP lors du Build : lancez un Build du module " +
            "puis rouvrez le fichier. Si le nom est une faute de frappe, il redeviendra " +
            "une erreur une fois les classes générées."
        return diagnostic.copy(severite = Severite.INFO, message = message)
    }

    /**
     * La sonde de coupure : le paquet `.databinding` est-il PEUPLÉ ?
     *
     * Sont sondés les sources générées (une racine source contenant le
     * dossier du paquet avec au moins un `.java`) et les binaires du
     * classpath (une entrée `<paquet>.<Classe>.class` dans un jar).
     */
    private fun paquetPeuple(paquet: String, racines: List<Path>, jars: List<Path>): Boolean {
        val dossier = paquet.replace('.', '/')
        for (racine in racines) {
            val chemin = racine.resolve(dossier)
            if (Files.isDirectory(chemin) &&
                Files.list(chemin).use { flux -> flux.anyMatch { it.fileName.toString().endsWith(".java") } }
            ) {
                return true
            }
        }
        for (jar in jars) {
            if (!Files.isRegularFile(jar)) continue
            val prefix = "$dossier/"
            try {
                ZipFile(jar.toFile()).use { zip ->
                    if (zip.entries().asSequence().any { entree ->
                            entree.name.startsWith(prefix) && entree.name.endsWith(".class")
                        }
                    ) return true
                }
            } catch (_: Exception) {
                // Jar illisible : la sonde ne bloque pas la publication.
            }
        }
        return false
    }
}

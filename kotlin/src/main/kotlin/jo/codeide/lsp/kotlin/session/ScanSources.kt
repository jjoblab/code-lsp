// CodeIDE — scan des racines sources d'une session (L1, module :kotlin,
// étage 1 vague 1b). Code NEUF jjoblab (clean-room — contrat : fiche 049 du
// cahier : « rafraichirSources : une déclaration externe devient
// resolvable »). Scan minimal SYNCHRONE de la vague 1b — le scan paresseux
// borné annulable (fiche 041) est la vague 1c.
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import jo.codeide.lsp.kotlin.parse.ParseurKotlin

/**
 * Le résultat IMMUABLE d'un scan des racines sources (L1) : les déclarations
 * nommées des fichiers Kotlin des racines, indexées par nom — la matière du
 * survol externe.
 *
 * Le scan est conservateur : seuls les fichiers `.kt`/`.kts` LISIBLES
 * contributeurs sont retenus (un fichier illisible est ignoré, jamais une
 * exception — D3) ; pour chaque nom, la PREMIÈRE déclaration en ordre de
 * marche gagne (racines dans l'ordre posé, fichiers triés par chemin — les
 * surcharges relèvent de la résolution, étage 2).
 *
 * @property declarations les déclarations externes indexées par nom.
 */
internal class ScanSources private constructor(
    private val declarations: Map<String, DeclarationExterne>,
) {

    /**
     * Une déclaration externe rendue par le scan.
     *
     * @property uri l'identifiant (chemin) du fichier la portant ;
     * @property signature la signature écrite conservatrice ;
     * @property documentation la documentation KDoc qui la précède (`null`
     * si aucune).
     */
    internal class DeclarationExterne(
        internal val uri: String,
        internal val signature: String,
        internal val documentation: String?,
    )

    /**
     * Rend la déclaration externe portant ce nom.
     *
     * @param nom le nom cherché ;
     * @return la première déclaration du scan portant ce nom ; `null` si le
     * scan n'en connaît aucune.
     */
    internal fun declaration(nom: String): DeclarationExterne? {
        return declarations[nom]
    }

    internal companion object {

        /** Le parseur du scan (sans état — partagé par les passes). */
        private val parseur = ParseurKotlin()

        /**
         * Scan les racines sources : marche des dossiers, lecture et parse
         * des fichiers Kotlin, collecte des déclarations nommées.
         *
         * La marche est HORS verrou de session (ADR 0212 : jamais de scan
         * sous verrou) ; l'ordre est DÉTERMINISTE : racines dans l'ordre
         * posé, fichiers triés par chemin.
         *
         * L'API NIO employée est la couche Java 7 CONSOMMABLE par ART dès
         * l'API 26 ([Paths.get], [Files.readAllBytes] — PAS `Path.of` ni
         * `Files.readString`, arrivées avec Java 11 : le dexage du pack
         * cible min-api 26) ; l'UTF-8 est explicite à la lecture.
         *
         * @param racines les dossiers racines à scanner (chemins) ;
         * @return le scan publié (vide si aucune racine ou aucun fichier —
         * jamais d'exception).
         */
        internal fun scanner(racines: List<String>): ScanSources {
            val collectees = mutableMapOf<String, DeclarationExterne>()
            for (racine in racines) {
                val dossier = try {
                    Paths.get(racine)
                } catch (_: InvalidPathException) {
                    // Chemin illisible : racine ignorée honnêtement (D3).
                    continue
                }
                if (!Files.isDirectory(dossier)) continue
                val fichiers = try {
                    Files.walk(dossier).use { flux ->
                        flux.filter { fichier ->
                            Files.isRegularFile(fichier) && (
                                fichier.fileName.toString().endsWith(".kt") ||
                                    fichier.fileName.toString().endsWith(".kts")
                                )
                        }.sorted().toList()
                    }
                } catch (_: Exception) {
                    // Racine illisible : ignorée honnêtement (D3).
                    continue
                }
                for (fichier in fichiers) {
                    val texte = try {
                        String(Files.readAllBytes(fichier), Charsets.UTF_8)
                    } catch (_: Exception) {
                        continue
                    }
                    val uri = fichier.toUri().toString()
                    val script = fichier.fileName.toString().endsWith(".kts")
                    val arbre = parseur.arbre(texte, script)
                    val commentaires = arbre.noeud.commentaires
                    for (declaration in SurvolDocuments.declarationsRecursives(arbre)) {
                        val nom = declaration.nom() ?: continue
                        if (collectees.containsKey(nom)) continue
                        val signature = SurvolDocuments.renduSignature(declaration) ?: continue
                        collectees[nom] = DeclarationExterne(
                            uri = uri,
                            signature = signature,
                            documentation = SurvolDocuments.kdocPrecedente(declaration, commentaires),
                        )
                    }
                }
            }
            return ScanSources(collectees)
        }
    }
}

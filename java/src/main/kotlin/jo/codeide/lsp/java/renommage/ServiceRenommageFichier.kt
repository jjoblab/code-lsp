package jo.codeide.lsp.java.renommage

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.java.outils.EditionTexte

/**
 * Les éditions d'un RENOMMAGE DE FICHIER (vague V-G — ADR 0031 (d),
 * `workspace/willRenameFiles`) : le geste de l'arbre éditeur « renommer /
 * déplacer un `.java` » arrive AVANT le renommage effectif (le client
 * demande les éditions à appliquer PENDANT le geste) — le moteur y calcule
 * les ajustements qui gardent le projet COHÉRENT :
 *
 *  1. **la déclaration de paquet** du fichier renommé : déplacer
 *     `demo/A.java` vers `autre/B.java` change le FQCN `demo.A` →
 *     `autre.B` — la ligne `package demo;` devient `package autre;` ;
 *  2. **les imports des fichiers porteurs** : tout `import demo.A;`
 *     (et `import demo.A.Imbrique;`, `import static demo.A.CHAMP;`)
 *     devient l'import du NOUVEAU FQCN.
 *
 * **Périmètre honnête (documenté, ADR 0031 (d))** : les usages QUALIFIÉS
 * complets SANS import (`demo.A a = …`) ne sont PAS réécrits (le cas doré
 * de la décision est « package + imports ») — un projet sain utilise les
 * imports ; le renommage à la volée des qualifications reste à la vague
 * d'après si la demande apparaît.
 *
 * Les éditions sont exprimées en OFFSETS ABSOLUS dans le texte COURANT de
 * chaque fichier porteur (disque, ou tampon ouvert — le serveur choisit
 * la source fraîche) ; par fichier, elles sont DISJOINTES et triées par
 * offset croissant (contrat [EditionTexte]).
 *
 * @property editionsParFichier les éditions par chemin de fichier porteur
 *   (le fichier renommé y figure avec son édition de paquet ; les autres
 *   avec leurs éditions d'imports) — VIDE si rien à ajuster.
 */
public class EditionsRenommageFichier(
    public val editionsParFichier: Map<String, List<EditionTexte>>,
)

/**
 * Le calcul des éditions de renommage de fichier (V-G — ADR 0031 (d)) :
 * service SANS ÉTAT, appelé par le moteur sur le geste `willRenameFiles`.
 *
 * Le FQCN ancien/nouveau suit le PLUS LONG préfixe de racine du moteur
 * (fiche 010 — pure arithmétique de chemins : le nouveau fichier
 * n'existe PAS encore, `willRename` précède le renommage).
 */
public object ServiceRenommageFichier {

    /**
     * Calcule les éditions d'un renommage / déplacement de fichier Java.
     *
     * @param racines les racines sources du module (plus long préfixe).
     * @param ancien le chemin du fichier AVANT le geste (existant).
     * @param nouveau le chemin APRÈS le geste (pas encore créé).
     * @param fichiersSources l'énumération des sources du module (l'index
     *   de mots — les candidats à importer l'ancien FQCN).
     * @param texteDe la source FRAÎCHE d'un fichier (le lecteur du
     *   serveur : tampon ouvert si le document l'est, disque sinon) ;
     *   null = fichier illisible, écarté honnêtement.
     * @return les éditions par fichier porteur, ou null si le fichier
     *   n'appartient à aucune racine (rien à ajuster — honnête).
     */
    public fun editions(
        racines: List<Path>,
        ancien: Path,
        nouveau: Path,
        fichiersSources: List<Path>,
        texteDe: (Path) -> String?,
    ): EditionsRenommageFichier? {
        val ancienFqcn = fqcnDe(racines, ancien) ?: return null
        val nouveauFqcn = fqcnDe(racines, nouveau) ?: return null
        if (ancienFqcn == nouveauFqcn) {
            // Renommage qui conserve le FQCN (casse du fichier, déplacement
            // interne au même paquet) : RIEN à ajuster — honnête, jamais
            // une réécriture inutile.
            return EditionsRenommageFichier(emptyMap())
        }
        val editions = LinkedHashMap<String, List<EditionTexte>>()

        // 1. La déclaration de paquet du fichier renommé (si elle change).
        val ancienPaquet = ancienFqcn.substringBeforeLast('.', "")
        val nouveauPaquet = nouveauFqcn.substringBeforeLast('.', "")
        if (ancienPaquet != nouveauPaquet) {
            val texte = texteDe(ancien)
            if (texte != null) {
                editionPaquet(texte, ancienPaquet, nouveauPaquet)?.let { edition ->
                    editions[ancien.toString()] = listOf(edition)
                }
            }
        }

        // 2. Les imports des AUTRES fichiers (le renommé est couvert par
        //    son édition de paquet — ses propres imports ne bougent pas).
        for (fichier in fichiersSources) {
            if (fichier == ancien) continue
            val texte = texteDe(fichier) ?: continue
            val editionsImports = editionsImports(texte, ancienFqcn, nouveauFqcn)
            if (editionsImports.isNotEmpty()) {
                editions[fichier.toString()] = editionsImports
            }
        }
        return EditionsRenommageFichier(editions)
    }

    /**
     * L'édition de la déclaration de paquet : remplace la ligne
     * `package <ancien>;` par `package <nouveau>;` — la plage couvre le
     * NOM du paquet uniquement (le mot-clé et le point-virgule restent).
     *
     * @param texte le source du fichier renommé.
     * @param ancienPaquet le paquet déclaré attendu (contrôle de cohérence).
     * @param nouveauPaquet le paquet cible (dérivé du nouveau chemin).
     * @return l'édition, ou null si aucune déclaration exploitable.
     */
    private fun editionPaquet(texte: String, ancienPaquet: String, nouveauPaquet: String): EditionTexte? {
        val regex = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;")
        val occurrence = regex.find(texte) ?: return null
        val nomDeclare = occurrence.groupValues[1]
        // Garde de cohérence : la déclaration doit porter l'ANCIEN paquet
        // (dérivé du chemin) — une déclaration divergente (fichier déjà
        // incohérent) est réécrite AUSSI (le geste de l'utilisateur EST de
        // ranger le fichier au bon endroit), mais l'édition reste exacte :
        // la plage couvre le nom déclaré, quel qu'il soit.
        val debutNom = occurrence.range.first + occurrence.value.indexOf(nomDeclare)
        return EditionTexte(
            offset = debutNom,
            longueur = nomDeclare.length,
            texte = nouveauPaquet,
        )
    }

    /**
     * Les éditions d'imports d'un fichier : chaque `import <ancienFqcn>`
     * (suivi de `;` ou `.` — types imbriqués) et `import static
     * <ancienFqcn>…` voit son segment FQCN réécrit vers le nouveau.
     *
     * @param texte le source du fichier porteur.
     * @param ancienFqcn l'ancien FQCN (points).
     * @param nouveauFqcn le nouveau FQCN (points).
     * @return les éditions (disjointes, triées par offset croissant).
     */
    private fun editionsImports(texte: String, ancienFqcn: String, nouveauFqcn: String): List<EditionTexte> {
        val editions = mutableListOf<EditionTexte>()
        val regex = Regex("""import\s+static\s+\Q$ancienFqcn\E(?=[.;\s])|import\s+\Q$ancienFqcn\E(?=[.;\s])""")
        for (occurrence in regex.findAll(texte)) {
            // La plage de l'édition : le SEGMENT FQCN seul (le mot-clé
            // import/static reste, le suffixe `;`/`.Imbrique` reste).
            val debutFqcn = occurrence.range.first + occurrence.value.indexOf(ancienFqcn)
            editions.add(
                EditionTexte(
                    offset = debutFqcn,
                    longueur = ancienFqcn.length,
                    texte = nouveauFqcn,
                ),
            )
        }
        return editions.sortedBy { it.offset }
    }

    /** Le FQCN d'un fichier par plus long préfixe de racine (fiche 010 — sans disque). */
    private fun fqcnDe(racines: List<Path>, fichier: Path): String? {
        val racine = racines
            .filter { fichier.startsWith(it.normalize()) }
            .maxByOrNull { it.toString().length } ?: return null
        val relatif = racine.normalize().relativize(fichier.normalize())
        if (relatif.nameCount == 0) return null
        val segments = (0 until relatif.nameCount).map { relatif.getName(it).toString() }
        val nomFichier = segments.last()
        if (!nomFichier.endsWith(".java")) return null
        return (segments.dropLast(1) + nomFichier.removeSuffix(".java")).joinToString(".")
    }
}

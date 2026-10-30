// CodeIDE — couture interne de nourrissage de l'index partagé (module
// :kotlin, étage 1 vague 1b). Code NEUF jjoblab (clean-room — contrat :
// fiche 042 du cahier + ADR 0211/0212 anciens : branchement/sous-lecture du
// moteur nourricier). INTERNE au module : la frontière publique reste le
// SPI `api/` (D6).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin

/**
 * La couture de nourrissage vue par une session (interne) : ce que le MOTEUR
 * expose à ses sessions quand il possède le nourrissage de l'index partagé
 * (ADR 0211 : « le moteur expose definierClasspath, la passe publie un
 * snapshot IMMUABLE, et la session se branche »).
 *
 * À la vague 1b le snapshot porte l'IDENTITÉ du classpath couvert (entrées
 * ordonnées + racines) ; le CONTENU réel de l'index (segments d'appelables,
 * lecture des jars) arrive à la vague 1d SUR cette même couture — les
 * requêtes qui prouvent par l'absence (diagnostics `kt.unresolved`, étage 2)
 * s'y brancheront alors pour garantir le silence quand l'index n'est pas
 * couvert (aucun faux négatif).
 */
internal interface NourrissageMoteur {

    /**
     * Le moteur possède-t-il le NOURRISSAGE (passe moteur unique par sync) ?
     *
     * `true` : les sessions créées se BRANCHENT (leur `definirClasspath` ne
     * re-lect rien) ; `false` : chaque session nourrit elle-même,
     * synchrone (contrat historique des usages directs — tests, packs
     * anciens).
     */
    val nourrissageParMoteur: Boolean

    /**
     * Rend le snapshot publié par la dernière passe du moteur S'IL couvre
     * [entrees] — la garde compare la LISTE ORDONNÉE (l'ordre du classpath
     * fait la préséance des candidats, jamais un ensemble).
     *
     * Consulté À CHAQUE REQUÊTE côté session (jamais d'état périmé : la
     * publication d'un nouveau snapshot guérit la requête suivante sans
     * intervention).
     *
     * @param entrees les entrées posées par la session (ordre de préséance) ;
     * @return le snapshot immuable couvrant exactement ces entrées ; `null`
     * si aucune passe n'a abouti pour elles (sous-lecture honnête).
     */
    fun indexSiPret(entrees: List<String>): SnapshotClasspath?
}

/**
 * Le snapshot IMMUABLE de l'index partagé publié par une passe du moteur
 * (interne — vague 1b : identité du classpath couvert ; vague 1d : vue des
 * appelables).
 *
 * @property classpath le classpath couvert par la passe (entrées ordonnées +
 * racines sources).
 */
internal class SnapshotClasspath internal constructor(
    internal val classpath: ClasspathKotlin,
)

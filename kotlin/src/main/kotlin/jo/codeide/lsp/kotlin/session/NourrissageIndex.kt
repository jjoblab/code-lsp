// CodeIDE — couture interne de nourrissage de l'index partagé (module
// :kotlin, étage 1 vague 1b ; vague 1d — la vue des APPELABLES rejoint
// le snapshot). Code NEUF jjoblab (contrat : fiches 042 puis 009/034 du
// cahier + ADR 0211/0212/0214 anciens : branchement/sous-lecture du
// moteur nourricier, reprise ciblée). INTERNE au module : la frontière
// publique reste le SPI `api/` (D6).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.index.VueAppelables

/**
 * La couture de nourrissage vue par une session (interne) : ce que le MOTEUR
 * expose à ses sessions quand il possède le nourrissage de l'index partagé
 * (ADR 0211 : « le moteur expose definirClasspath, la passe publie un
 * snapshot IMMUABLE, et la session se branche »).
 *
 * Depuis la vague 1d le snapshot porte la VUE des appelables du classpath
 * (segments décodés par la passe du moteur) : les requêtes qui prouvent
 * par l'absence (diagnostics `kt.unresolved`, étage 2) s'y branchent pour
 * garantir le silence quand l'index n'est pas couvert (aucun faux négatif).
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
     * [entrees] — la garde compare la liste ORDONNÉE des empreintes (l'ordre
     * du classpath fait la préséance des candidats, jamais un ensemble).
     *
     * Consulté À CHAQUE REQUÊTE côté session (jamais d'état périmé : la
     * publication d'un nouveau snapshot guérit la requête suivante sans
     * intervention).
     *
     * @param entrees les entrées posées par la session (ordre de préséance) ;
     * @return le snapshot immuable couvrant exactement ces entrées ; `null`
     * si aucune passe n'a abouti pour elles (sous-lecture honnête).
     */
    fun indexSiPret(entrees: List<EntreeClasspathKotlin>): SnapshotClasspath?
}

/**
 * Le snapshot IMMUABLE de l'index partagé publié par une passe du moteur
 * (interne).
 *
 * La [vue] délègue à l'état COURANT de l'index du moteur (vivante) : la
 * garde de couverture du guichet la légitime à chaque requête — une vue
 * n'est servie que tant que l'index couvre le classpath posé. Depuis la
 * vague 2b, le snapshot porte AUSSI la [vueJvm] des MEMBRES du classpath
 * (fiche 046 / ADR 0223) : paresseuse (rien n'est lu à la construction),
 * pilotée par les MÊMES entrées — les preuves sur récepteurs de
 * bibliothèque vivent sous la même garde de couverture.
 *
 * @property classpath le classpath couvert par la passe (entrées ordonnées
 * + racines sources) ;
 * @property vue la vue des appelables du snapshot publié ;
 * @property vueJvm la vue JVM des membres du classpath couvert.
 */
internal class SnapshotClasspath internal constructor(
    internal val classpath: ClasspathKotlin,
    internal val vue: VueAppelables,
    internal val vueJvm: jo.codeide.lsp.kotlin.resolve.VueJvmMembres = jo.codeide.lsp.kotlin.resolve.VueJvmMembres(classpath.entrees),
)

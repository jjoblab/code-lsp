// CodeIDE — moteur de langage Kotlin noyau (module :kotlin, étage 1 vague
// 1b — session documents). Code NEUF jjoblab (clean-room — contrat : fiches
// 049/042 du cahier ; nourrissage moteur ADR 0211 : une passe par sync,
// snapshot immuable publié, sessions branchées sans re-lecture).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.api.SessionLangageKotlin

/**
 * Le moteur de langage Kotlin du NOYAU — fournisseur du SPI
 * [ServiceMoteurLangageKotlin] (chargé par [ServiceMoteurLangageKotlin.unique]
 * via ServiceLoader ou repli).
 *
 * ## Nourrissage (ADR 0211 — fiche 042)
 *
 * Le moteur est NOURRICIER : [definirClasspath] mène LA PASSE (à la vague 1b :
 * construction immédiate du snapshot immuable — la lecture des segments et
 * le décodage des jars arrivent à la vague 1d sur cette même passe, alors
 * sur fil dédié) puis publie le snapshot ; les sessions créées se branchent
 * dessus sans rien relire, et consultent l'état publié À CHAQUE requête
 * (sous-lecture honnête tant que rien n'est couvert, guérison à la requête
 * suivante dès publication).
 *
 * ## Sessions
 *
 * Chaque [creerSession] rend une session NEUVE et étanche (états
 * indépendants — fiche 049) : documents, classpath et scan propres à la
 * session ; seul l'état NOURRI (snapshot d'index) est partagé entre les
 * sessions d'un même moteur.
 */
public class MoteurLangageKotlinNoyau : ServiceMoteurLangageKotlin {

    /** Le verrou de la passe (publication du snapshot — pas sur le chemin des requêtes). */
    private val verrouMoteur = Any()

    /** Le dernier snapshot publié (volatil : lectures sans verrou des requêtes). */
    @Volatile
    private var publie: SnapshotClasspath? = null

    /**
     * Crée une session NEUVE, câblée sur le nourrissage de CE moteur.
     *
     * @return la session indépendante (documents/classpath/scan propres) ;
     * jamais nulle.
     */
    override fun creerSession(): SessionLangageKotlin {
        return SessionLangageKotlinDocuments(NourrissageImp())
    }

    /**
     * Mène la passe de nourrissage pour [classpath] : construit et PUBLIE le
     * snapshot immuable de l'index partagé.
     *
     * À la vague 1b la passe est immédiate (le snapshot porte l'identité du
     * classpath couvert) ; à la vague 1d elle portera la lecture réelle des
     * segments d'appelables — le CONTRAT (retour `true`, publication,
     * branchement des sessions) est ce que cette vague verrouille.
     *
     * @param classpath le classpath neutre (entrées ordonnées + racines) ;
     * @return toujours `true` — le moteur est nourricier (une session branchée
     * ne re-lect rien).
     */
    override fun definirClasspath(classpath: ClasspathKotlin): Boolean {
        val frais = SnapshotClasspath(classpath)
        synchronized(verrouMoteur) { publie = frais }
        return true
    }

    /**
     * Le snapshot courant publié par la dernière passe (interne — inspection
     * de la fiche 042 : « l'index reste tel quel »).
     */
    internal val snapshotCourant: SnapshotClasspath?
        get() = publie

    /**
     * L'implémentation de la couture de nourrissage remise aux sessions de
     * ce moteur (interne).
     */
    private inner class NourrissageImp : NourrissageMoteur {

        /** Le moteur noyau est TOUJOURS nourricier. */
        override val nourrissageParMoteur: Boolean
            get() = true

        /**
         * Rend le snapshot publié s'il couvre exactement [entrees] (liste
         * ORDONNÉE — la préséance du classpath fait la garde).
         */
        override fun indexSiPret(entrees: List<String>): SnapshotClasspath? {
            val courant = publie ?: return null
            return if (courant.classpath.entrees == entrees) courant else null
        }
    }
}

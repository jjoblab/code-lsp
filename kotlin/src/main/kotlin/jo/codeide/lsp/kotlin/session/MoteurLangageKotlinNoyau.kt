// CodeIDE — moteur de langage Kotlin noyau (module :kotlin, étage 1 vague
// 1b — session documents ; vague 1d — la passe nourrit réellement l'index
// des appelables). Code NEUF jjoblab (contrat : fiches 049/042 puis
// 008/009/034 du cahier ; nourrissage moteur ADR 0211 : une passe par sync
// sur fil dédié, snapshot immuable publié, sessions branchées sans
// re-lecture).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.api.SessionLangageKotlin
import jo.codeide.lsp.kotlin.index.IndexAppelablesKotlin
import jo.codeide.lsp.kotlin.index.PasseurIndexationKotlin

/**
 * Le moteur de langage Kotlin du NOYAU — fournisseur du SPI
 * [ServiceMoteurLangageKotlin] (chargé par [ServiceMoteurLangageKotlin.unique]
 * via ServiceLoader ou repli).
 *
 * ## Nourrissage (ADR 0211 — fiches 042/009)
 *
 * Le moteur est NOURRICIER : [definirClasspath] confie le classpath au
 * PILOTE de passages ([PasseurIndexationKotlin]) — la passe part sur le fil
 * démon dédié (segments des bibliothèques, puis scan des sources), PUBLIE
 * le snapshot immuable à l'issue des segments et rend IMMÉDIATEMENT ; les
 * sessions créées se branchent dessus sans rien relire, et consultent
 * l'état publié À CHAQUE requête (sous-lecture honnête tant que rien n'est
 * couvert, guérison à la requête suivante dès publication).
 *
 * ## Sessions
 *
 * Chaque [creerSession] rend une session NEUVE et étanche (états
 * indépendants — fiche 049) : documents, classpath et scan propres à la
 * session ; seuls l'index des appelables et la passe du moteur sont
 * PARTAGÉS entre les sessions d'un même moteur.
 */
public class MoteurLangageKotlinNoyau : ServiceMoteurLangageKotlin {

    /** Le verrou de la publication (pas sur le chemin des requêtes). */
    private val verrouMoteur = Any()

    /** Le dernier snapshot publié (volatil : lectures sans verrou des requêtes). */
    @Volatile
    private var publie: SnapshotClasspath? = null

    /**
     * L'index des appelables partagé par les passes du moteur (interne —
     * inspection des fiches 009/034 : couverture, nom de segment).
     */
    internal val index = IndexAppelablesKotlin()

    /** Le scanneur des racines mené par la passe du moteur. */
    private val scanMoteur = ScanProjetParesseux()

    /**
     * Le pilote des passages : nourrit l'index, publie les événements et
     * invoque la publication du snapshot à l'issue des SEGMENTS (les
     * sessions voient les appelables sans attendre le scan des sources).
     */
    private val passeur =
        PasseurIndexationKotlin(index, scanMoteur) { classpath ->
            synchronized(verrouMoteur) {
                publie = SnapshotClasspath(classpath, index)
            }
        }

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
     * Confie le classpath à la passe du moteur (UNE fois par
     * synchronisation côté hôte, ADR 0211) : le travail part sur le fil
     * dédié, le snapshot est publié à l'issue des segments.
     *
     * @param classpath le classpath (entrées ordonnées + racines + dossier
     * d'index) ;
     * @return toujours `true` — le moteur est nourricier (une session
     * branchée ne re-lect rien).
     */
    override fun definirClasspath(classpath: ClasspathKotlin): Boolean {
        passeur.definirClasspath(classpath)
        return true
    }

    /**
     * Le snapshot courant publié par la dernière passe (interne —
     * inspection de la fiche 042 : « l'index reste tel quel »).
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
         * ORDONNÉE d'empreintes — la préséance du classpath fait la garde,
         * et la couverture de l'index fait foi : une passe PARTIELLE ou
         * annulée ne couvre pas, le guichet se tait).
         */
        override fun indexSiPret(entrees: List<EntreeClasspathKotlin>): SnapshotClasspath? {
            val courant = publie ?: return null
            if (courant.classpath.entrees != entrees) return null
            return if (index.couvre(entrees)) courant else null
        }
    }
}

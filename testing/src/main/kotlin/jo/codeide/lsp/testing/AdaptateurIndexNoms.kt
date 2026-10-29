package jo.codeide.lsp.testing

import jo.codeide.lsp.index.ServiceIndexDisque
import jo.codeide.lsp.index.VueIndex
import jo.codeide.lsp.java.IndexNoms
import jo.codeide.lsp.model.ClasspathInstantane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Adaptateur de l'index disque CIDX sur le port [IndexNoms] du moteur
 * (étage N1 — suite d'intégration `:testing`).
 *
 * Miroir Kotlin de l'`AdaptateurIndexNoms` de l'ancienne suite (codeide-lsp
 * `lsp/testing`, Java) : il surveille la transition CONSTRUCTION → PRÊT de
 * la vue (collecte du flux d'état en arrière-plan), exécute les
 * observateurs posés AVANT la transition dès qu'elle advient (et
 * immédiatement pour un index déjà prêt — contrat des index factices du
 * cahier), et traduit les requêtes du port en requêtes de fusion LSM de la
 * vue (`localiser`, `nomsParPrefixe`, `estPaquet`, `typesDansPaquet`,
 * `sousPaquetsDirects`, `membresStatiques`).
 *
 * L'adaptateur vit AU-DESSUS des deux modules qu'il relie — même schéma
 * que le futur hôte `:lsp:host` (ADR 0111 lot C : le moteur ne dépend
 * d'AUCUN module interne, extractibilité bibliothèque).
 *
 * @param service le service d'index disque (stockage partagé entre projets).
 * @param classpath le classpath instantané du module à indexer.
 * @param scope la portée de la surveillance d'état (annulée par [fermer]).
 */
public class AdaptateurIndexNoms(
    private val service: ServiceIndexDisque,
    classpath: ClasspathInstantane,
    scope: CoroutineScope,
) : IndexNoms {

    /** La vue d'index du classpath (fusion LSM à la requête). */
    private val vue: VueIndex = service.vuePour(classpath)

    /** Observateurs de la transition PRÊT (exécutés une fois au plus). */
    private val observateurs = mutableListOf<() -> Unit>()

    /** La transition a-t-elle eu lieu ? */
    @Volatile
    private var pret: Boolean = false

    /** Job de la surveillance d'état. */
    private val surveillance: Job = scope.launch {
        vue.etat.collect { etat ->
            if (!pret && (etat.phase == jo.codeide.lsp.index.PhaseIndex.PRET || etat.phase == jo.codeide.lsp.index.PhaseIndex.PARTIEL)) {
                pret = true
                for (observateur in observateurs.toList()) {
                    observateur()
                }
            }
        }
    }

    /** L'index a-t-il fini sa construction (PRÊT ou PARTIEL honnête) ? */
    override fun estPret(): Boolean = pret

    /**
     * La vue ADAPTÉE : c'est ELLE que [ServiceIndexDisque.construire] doit
     * recevoir — chaque appel de `vuePour` rend une vue NEUVE (aucune
     * mise en cache côté service), construire une autre vue ne ferait
     * jamais transitionner celle observée ici.
     */
    public fun vue(): VueIndex = vue

    /**
     * Enregistre un observateur de la transition PRÊT : un index DÉJÀ prêt
     * l'exécute immédiatement (contrat du cahier, fiches 032/034).
     */
    override fun ajouterObservateurPret(observateur: () -> Unit) {
        if (pret) {
            observateur()
            return
        }
        observateurs.add(observateur)
    }

    /** Jar porteur d'un FQCN (le chemin du CONTENEUR — l'entrée d'index est la ressource classe interne). */
    override fun jarDe(fqcn: String): String? = vue.localiser(fqcn)?.conteneur?.chemin

    /** Noms simples commençant par un préfixe (bornés). */
    override fun nomsParPrefixe(prefixe: String, limite: Int): List<String> =
        vue.nomsParPrefixe(prefixe, limite.coerceAtLeast(1)).map { it.nomSimple }

    /** FQCN portant EXACTEMENT un nom simple (rendus bruts, filtre moteur). */
    override fun fqnsForSimpleName(nom: String): List<String> =
        vue.nomsParPrefixe(nom, LIMITE_RECHERCHE_EXACTE)
            .filter { it.nomSimple == nom }
            .flatMap { it.fqcns }

    /** Le paquet existe dans un segment prêt (tolérance aux préfixes intermédiaires). */
    override fun estPaquet(paquet: String): Boolean = vue.estPaquet(paquet)

    /** Sous-paquets directs d'un paquet. */
    override fun sousPaquetsDirects(paquet: String): List<String> = vue.sousPaquetsDirects(paquet)

    /** Types d'un paquet (FQCN bruts — le moteur filtre `$` en aval). */
    override fun typesDansPaquet(paquet: String): List<String> = vue.typesDansPaquet(paquet)

    /** Membres statiques d'un type (noms simples). */
    override fun membresStatiques(fqcn: String): List<String> =
        vue.membresStatiques(fqcn).map { it.nom }

    /** Libère la surveillance d'état (la vue reste interrogeable). */
    public fun fermer() {
        surveillance.cancel()
    }

    private companion object {
        /** Garde des recherches de nom exact (préfixe = nom, exact filtré en aval). */
        const val LIMITE_RECHERCHE_EXACTE: Int = 1_000
    }
}

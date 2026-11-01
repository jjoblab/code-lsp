package jo.codeide.lsp.java.interne

import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import jo.codeide.lsp.java.outils.Focused

/**
 * Entrée du cache de parse (fiche 025) : l'arbre résolu RETENU + ses
 * attributs d'entrée.
 *
 * @property unite l'arbre résolu (identité référentielle = le contrat
 *   observable du cache : un hit rend la MÊME instance).
 * @property genererCode l'attribut d'entrée de la mise en cache — une entrée
 *   codegen n'est JAMAIS dégradée par un put sans codegen.
 * @property focused la réduction éventuelle (grands fichiers) : les offsets
 *   d'interrogation sont traduits par `mapAvant` avant lecture de l'arbre.
 */
internal class EntreeParse(
    val unite: UniteResolue,
    val genererCode: Boolean,
    val focused: Focused?,
)

/**
 * L'unité résolue publiée par le moteur : enveloppe l'arbre ecj vivant.
 *
 * @property arbre l'arbre `CompilationUnitDeclaration` (scope et bindings
 *   lisibles) ; null si la passe a avorté prématurément (source
 *   catastrophique — le moteur rend alors un diagnostic d'échec propre).
 * @property chemin le chemin logique du document.
 * @property source le source ayant produit l'arbre.
 * @property genererCode si la codegen mémoire a été jouée.
 */
public class UniteResolue internal constructor(
    public val arbre: CompilationUnitDeclaration?,
    public val chemin: String,
    public val source: String,
    public val genererCode: Boolean,
)

/**
 * Cache de parse LRU borné (étage N1, cahier fiche 025).
 *
 * Clé = couple **(chemin, source)** — la garde de contenu qui interdit de
 * servir un arbre périmé entre deux debounces. Le contrat observable est
 * l'IDENTITÉ RÉFÉRENTIELLE de l'arbre : deux `resolveUnit` sur la même clé
 * rendent la même instance ; un changement de source, une invalidation
 * ciblée ou un `dispose` rendent un arbre DISTINCT.
 *
 * W8 (v1.5.x) : l'entrée unique évinçait à chaque put — le cache est
 * MULTI-ENTRÉES avec éviction du moins récemment utilisé au-delà de
 * [MAX_ENTREES] (aller-retour entre fichiers sans re-payer le parse).
 *
 * **Budget OCTETS (vague V-F3 — ADR 0029 phase 3, exécution de l'ADR 0011
 * point 2)** : la borne en ENTRÉES (8 × ~100 Ko mesurés par l'audit
 * ADR 0011 ≈ 800 Ko en régime typique) laisse passer le trou inverse —
 * UN SEUL FICHIER GÉANT peut dépasser largement ce total. Chaque entrée
 * porte un COÛT ESTIMÉ en octets (formule [coutEstime] : le source retenu
 * × un facteur d'arbre ESTIMÉ — ce n'est PAS une mesure, c'est une borne
 * de gestion : le tas réel reste l'affaire du banc mémoire, R5) et
 * l'éviction LRU s'applique AUSSI au-delà du budget en octets. Garde
 * anti-cyclage : l'entrée la plus RÉCENTE est TOUJOURS retenue (un
 * fichier unique plus gros que le budget vit seul jusqu'à la suivante —
 * jamais de put/éviction en boucle). Kill-switch :
 * `jo.codeide.lsp.java.memoire.budgets.disabled=true` rétablit la borne
 * d'entrées seule (budget infini — comportement d'avant V-F3).
 *
 * Partage diagnostics/hover : `diagnose` (codegen) et `resolveUnit`
 * (lecture) reposent sur la MÊME table — la codegen ne fractionne pas le
 * cache, et un put sans codegen ne dégrade JAMAIS une entrée codegen.
 *
 * @param budgetOctets le budget d'octets ESTIMÉS (défaut
 *   [BUDGET_OCTETS_DEFAUT] ; le kill-switch global ne peut que le
 *   RELÂCHER — le minimum des deux gouverne).
 */
internal class CacheParse(
    budgetOctets: Long = BUDGET_OCTETS_DEFAUT,
) {

    /** Capacité et budget du cache (constantes documentées — le cahier ne fixe pas les valeurs). */
    public companion object {

        /** Nombre maximal d'entrées retenues (éviction LRU au-delà). */
        public const val MAX_ENTREES: Int = 8

        /**
         * Le budget d'octets ESTIMÉS par défaut (8 Mio) : généreux devant
         * le régime typique (8 entrées ≈ 800 Ko — audit ADR 0011), il ne
         * mord que sur les fichiers GÉANTS — plusieurs géants partagent le
         * budget et s'évincent au LRU jusqu'à rentrer sous la borne.
         */
        public const val BUDGET_OCTETS_DEFAUT: Long = 8L * 1024 * 1024

        /**
         * Le facteur d'arbre ESTIMÉ : combien d'octets d'arbre résolu
         * (nœuds + scopes + liaisons) par caractère de source (UTF-16 →
         * 2 octets) retenu. ESTIMATION de gestion, pas une mesure (R5) —
         * recalibrable par le banc mémoire ADR 0011/0016 qui mesure le
         * tas RÉEL.
         */
        internal const val FACTEUR_ARBRE_ESTIME: Long = 6

        /** Coût fixe estimé d'une entrée (enveloppe + référence de valeur). */
        internal const val COUT_FIXE_ENTREE: Long = 64

        /**
         * Le budget global après kill-switch : `Long.MAX_VALUE` = borne
         * d'entrées seule (repli documenté — retour au comportement
         * d'avant V-F3 sans toucher au code).
         */
        private val BUDGET_GLOBAL: Long =
            if (System.getProperty("jo.codeide.lsp.java.memoire.budgets.disabled") == "true") {
                Long.MAX_VALUE
            } else {
                BUDGET_OCTETS_DEFAUT
            }

        /**
         * Le coût ESTIMÉ d'une entrée pour un source donné.
         *
         * @param source le source retenu par l'entrée (la clé le garantit
         *   identique à celui du ranger).
         * @return le coût en octets estimés (fixe + facteur × source).
         */
        internal fun coutEstime(source: String): Long =
            COUT_FIXE_ENTREE + FACTEUR_ARBRE_ESTIME * 2L * source.length
    }

    /** Le budget effectif : le paramètre ne peut pas être plus permissif que le kill-switch global. */
    private val budgetEffectif: Long = minOf(budgetOctets, BUDGET_GLOBAL)

    /** Table d'accès : clé (chemin, source) → entrée ; ordre d'accès = LRU. */
    private val entrees = LinkedHashMap<Cle, EntreeParse>(16, 0.75f, true)

    /** Total des coûts estimés des entrées courantes (sous le verrou du cache). */
    private var octetsCourants: Long = 0

    /**
     * Rend l'entrée pour la clé (chemin, source) — un ACCÈS la rafraîchit
     * (LRU d'accès, pas d'insertion).
     *
     * @param chemin chemin logique du document.
     * @param source source exacte (garde de contenu).
     * @return l'entrée, ou null si absente.
     */
    @Synchronized
    public fun obtenir(chemin: String, source: String): EntreeParse? = entrees[Cle(chemin, source)]

    /**
     * Range une entrée pour la clé (chemin, source).
     *
     * Si une entrée existe déjà AVEC codegen et que ce put est SANS codegen,
     * l'entrée existante est CONSERVÉE (jamais dégradée — contrat fiche 025) ;
     * dans tous les autres cas l'entrée est posée (le passage sans codegen →
     * codegen est une montée en grade légitime) et l'éviction LRU s'applique
     * (bornes ENTRÉES et OCTETS — V-F3). Le remplacement d'une clé vivante
     * conserve le source (la clé le porte) : le coût estimé est inchangé,
     * seul un NOUVEAU clé coûte.
     *
     * @param chemin chemin logique du document.
     * @param source source exacte.
     * @param unite l'arbre résolu à retenir.
     * @param genererCode l'attribut codegen de ce put.
     * @param focused la réduction éventuelle (grands fichiers).
     * @return l'entrée effectivement retenue (l'existante si non dégradée).
     */
    @Synchronized
    public fun ranger(
        chemin: String,
        source: String,
        unite: UniteResolue,
        genererCode: Boolean,
        focused: Focused?,
    ): EntreeParse {
        val cle = Cle(chemin, source)
        val existante = entrees[cle]
        if (existante != null && existante.genererCode && !genererCode) {
            return existante // codegen survit : pas de dégradation
        }
        if (existante == null) {
            octetsCourants += coutEstime(source) // nouvelle clé : son coût entre au total
        }
        val entree = EntreeParse(unite, genererCode, focused)
        entrees[cle] = entree
        evictSiNecessaire()
        return entree
    }

    /**
     * Évince le moins récemment utilisé au-delà des DEUX bornes (entrées
     * ET octets estimés — V-F3). La dernière entrée posée est TOUJOURS
     * retenue (garde anti-cyclage : un fichier seul plus gros que le
     * budget vit seul, il ne s'évince pas lui-même à chaque put).
     *
     * Le total est RECALCULÉ depuis la table en fin de passe (cohérence
     * défensive : aucune dérive par un chemin d'exception oublié).
     */
    private fun evictSiNecessaire() {
        val it = entrees.entries.iterator()
        while (entrees.size > 1 &&
            (entrees.size > MAX_ENTREES || octetsCourants > budgetEffectif) &&
            it.hasNext()
        ) {
            val evictée = it.next().value
            octetsCourants -= coutEstime(evictée.unite.source)
            it.remove()
        }
        octetsCourants = entrees.values.sumOf { coutEstime(it.unite.source) }
    }

    /**
     * Invalide TOUTES les entrées d'un chemin (didChange : le flux LSP
     * invalide le cache à chaque édition du document).
     *
     * @param chemin le chemin du document édité.
     * @return le nombre d'entrées évincées.
     */
    @Synchronized
    public fun invalider(chemin: String): Int {
        val avant = entrees.size
        val it = entrees.entries.iterator()
        while (it.hasNext()) {
            val (cle, entree) = it.next()
            if (cle.chemin == chemin) {
                octetsCourants -= coutEstime(entree.unite.source)
                it.remove()
            }
        }
        return avant - entrees.size
    }

    /**
     * Vide le cache ENTIER (dispose, pression mémoire V-F3).
     *
     * @return le nombre d'entrées évacuées (le rapport de pression mémoire
     *   rend ce compte à l'hôte — donnée chiffrée du journal appareil).
     */
    @Synchronized
    public fun vider(): Int {
        val liberees = entrees.size
        entrees.clear()
        octetsCourants = 0
        return liberees
    }

    /** Nombre d'entrées retenues (sonde observable des tests du cahier). */
    @Synchronized
    public fun taille(): Int = entrees.size

    /**
     * Le total des coûts ESTIMÉS des entrées courantes (sonde V-F3 — le
     * budget d'octets est observable par les tests et le banc mémoire).
     */
    @Synchronized
    public fun octetsEstimes(): Long = octetsCourants

    /** Le budget effectif appliqué (sonde V-F3, après kill-switch). */
    @Synchronized
    public fun budgetOctets(): Long = budgetEffectif

    /** Clé du cache : (chemin, source) — la source EST la garde de contenu. */
    private data class Cle(val chemin: String, val source: String)
}

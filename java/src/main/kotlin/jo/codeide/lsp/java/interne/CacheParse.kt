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
 * Partage diagnostics/hover : `diagnose` (codegen) et `resolveUnit`
 * (lecture) reposent sur la MÊME table — la codegen ne fractionne pas le
 * cache, et un put sans codegen ne dégrade JAMAIS une entrée codegen.
 */
internal class CacheParse {

    /** Capacité du cache (constante documentée — la valeur n'est pas fixée par le cahier). */
    public companion object {
        /** Nombre maximal d'entrées retenues (éviction LRU au-delà). */
        public const val MAX_ENTREES: Int = 8
    }

    /** Table d'accès : clé (chemin, source) → entrée ; ordre d'accès = LRU. */
    private val entrees = LinkedHashMap<Cle, EntreeParse>(16, 0.75f, true)

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
     * codegen est une montée en grade légitime) et l'éviction LRU s'applique.
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
        val entree = EntreeParse(unite, genererCode, focused)
        entrees[cle] = entree
        evictSiNecessaire()
        return entree
    }

    /** Évince le moins récemment utilisé au-delà de la capacité. */
    private fun evictSiNecessaire() {
        val it = entrees.entries.iterator()
        while (entrees.size > MAX_ENTREES && it.hasNext()) {
            it.next()
            it.remove()
        }
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
        entrees.keys.removeIf { it.chemin == chemin }
        return avant - entrees.size
    }

    /** Vide le cache ENTIER (dispose). */
    @Synchronized
    public fun vider() {
        entrees.clear()
    }

    /** Nombre d'entrées retenues (sonde observable des tests du cahier). */
    @Synchronized
    public fun taille(): Int = entrees.size

    /** Clé du cache : (chemin, source) — la source EST la garde de contenu. */
    private data class Cle(val chemin: String, val source: String)
}

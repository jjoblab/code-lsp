package spikekotlin

import jo.lspkotlin.api.MembresTypeKotlin
import jo.lspkotlin.api.NomTypeKotlin
import jo.lspkotlin.api.VueClasspathKotlin

/**
 * Vue de classpath VIDE pour le pilote oracle S0.
 *
 * Implémentation clean-room de l'interface publique `VueClasspathKotlin`
 * de l'artefact publié : chaque membre rend la réponse minimale honnête
 * (vide/null). Elle permet de conduire l'oracle SANS vue réelle — les
 * mesures S0 bornent ainsi le plan « édition » (parse + diagnostics du
 * document), le plan classpath restant hors périmètre du banc de bureau.
 *
 * Aucune source de l'ancien dépôt n'a été lue : l'interface est la surface
 * publique de l'artefact (consommateur standard), l'implémentation est
 * triviale par construction.
 */
class VueVide : VueClasspathKotlin {
    override fun nomsParPrefixe(prefixe: String, limite: Int): List<NomTypeKotlin> = emptyList()

    override fun typesDansPaquet(paquet: String): List<String> = emptyList()

    override fun estPaquet(nom: String): Boolean = false

    override fun sousPaquetsDirects(nom: String): List<String> = emptyList()

    override fun membres(fqcn: String): MembresTypeKotlin? = null

    override fun octetsClasse(fqcn: String): ByteArray? = null
}

package jo.codeide.lsp.java.classpath

import java.nio.file.Path

/**
 * Contrat d'un FOURNISSEUR de types synthétiques (vague V-B — ADR 0021/0022)
 * : des classes light fabriquées EN MÉMOIRE (API-seule), servies par
 * l'environnement de noms ENTRE les sources et les jars.
 *
 * Deux implémentations sont prévues par les ADR :
 *  - [jo.codeide.lsp.java.projetandroid.ClassesLegereAndroid] (ADR 0021) :
 *    `R`, `BuildConfig`, `*Binding` d'un module Android ;
 *  - les déclarations Kotlin publiques (ADR 0022, V-B3) : classes et
 *    facades `*Kt` lues lexicalement des `.kt` du projet.
 *
 * **Ordre de repli VERROUILLÉ** (miroir du PsiElementFinder de la
 * référence, §B9) : sources → TYPES SYNTHÉTIQUES → jars → jrt → stubs
 * intrinsèques. La source générée réelle PRIME donc toujours sur le
 * synthétique (anti-doublon R3 de l'ADR 0021) ; le synthétique prime sur
 * les jars pour qu'un R de build PÉRIMÉ ne masque jamais un référentiel
 * frais.
 */
public interface TypesSynthetiques {
    /**
     * Les octets du fichier de classe d'un type synthétique.
     *
     * @param fqcn nom entièrement qualifié (points ; imbriquées par `$`).
     * @return les octets du format de classe, ou null si le FQCN n'est pas
     *   synthétique pour ce fournisseur.
     */
    public fun octetsPour(fqcn: String): ByteArray?

    /**
     * Les types synthétiques proposables en COMPLÉTION, par paquet —
     * l'annuaire les enregistre APRÈS les sources (anti-doublon) et AVANT
     * les jars/jrt.
     *
     * @return paquet (points) → noms simples de premier niveau.
     */
    public fun typesParPaquet(): Map<String, List<String>>

    /**
     * Le paquet porte-t-il des types synthétiques (isPackage).
     *
     * @param nomPaquet le paquet en points.
     */
    public fun estPaquet(nomPaquet: String): Boolean

    /**
     * La cible de NAVIGATION d'un CHAMP synthétique : `R.layout.x` → le
     * fichier XML déclarant + l'offset exact du nom.
     *
     * @param fqcnDeclarant le FQCN du type déclarant (`ns.R$layout`).
     * @param champ le nom du champ (`activity_main`).
     * @return (fichier, offset du jeton cible) ou null si non applicable.
     */
    public fun cibleChamp(
        fqcnDeclarant: String,
        champ: String,
    ): Pair<Path, Int>?
}

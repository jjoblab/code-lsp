// CodeIDE — SPI du moteur Kotlin LSP : entrées de classpath riches (module
// :kotlin, étage 1 vague 1d — classpath + index appelables). Code NEUF
// jjoblab écrit pour code-lsp (contrat : fiches 002–005/008/009/034 du
// cahier docs/cahier-specifications-kotlin/ ; ADR 0108 — index dédié,
// hypothèse de travail tant que l'arbitrage Q-K1 est ouvert). Formes
// NEUTRES : chemins et empreintes en chaînes, aucune dépendance LSP4J ni
// Android — frontière D6.
package jo.codeide.lsp.kotlin.api

/**
 * La nature d'une entrée de classpath (K3) — le décodeur de métadonnées
 * parcourt le conteneur correspondant.
 */
public enum class KindEntreeKotlin {

    /** Archive Java (`.jar`) — entrées `.class` parcourues directement. */
    JAR,

    /**
     * Bibliothèque Android (`.aar`) — les jars INTERNES (`classes.jar`
     * et ceux du répertoire `libs`) sont parcourus EN MÉMOIRE, aucune
     * extraction sur disque.
     */
    AAR,
}

/**
 * Une entrée de classpath d'une session Kotlin (jars/AAR des
 * bibliothèques) — la moitié « bibliothèques » du
 * [ClasspathKotlin][jo.codeide.lsp.kotlin.api.ClasspathKotlin].
 *
 * L'[empreinte] est la clé d'identité du CONTENU de l'entrée : elle vient
 * de l'HÔTE (conventions d'empreintes du dépôt — jamais recalculée par le
 * moteur, qui ne re-hache que le nom de segment qu'il en dérive). Deux
 * fichiers différents sous la même empreinte relèvent de la cohérence de
 * l'hôte ; le moteur fait foi de l'empreinte déclarée.
 *
 * @property kind la nature du conteneur (jar ou AAR) ;
 * @property chemin le chemin du fichier sur disque (forme neutre) ;
 * @property empreinte l'identité de contenu posée par l'hôte — clé du
 * segment d'index et de la garde de couverture.
 */
public class EntreeClasspathKotlin(
    public val kind: KindEntreeKotlin,
    public val chemin: String,
    public val empreinte: String,
)

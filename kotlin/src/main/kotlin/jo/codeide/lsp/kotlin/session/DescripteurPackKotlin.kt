// CodeIDE — descripteur de pack langue Kotlin (module :kotlin, étage 6
// vague 6a — chargeur). Code NEUF jjoblab (contrat : fiche 001 du cahier
// docs/cahier-specifications-kotlin/ — « descripteur de pack complet :
// valeurs réelles pas des replis » ; le descripteur `version.properties`
// est GÉNÉRÉ par :kotlin:genererDescripteurPack depuis la version du
// projet — contrat D6, ADR 0101 ; les CLÉS `id`/`version`/`minApp`/
// `grammaire` sont verrouillées par le test du SPI).
package jo.codeide.lsp.kotlin.session

import java.util.Properties
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin

/**
 * La lecture du descripteur de pack langue (`version.properties` généré —
 * contrat D6) : l'identité du pack posée par le BUILD, servie par le SPI
 * ([ServiceMoteurLangageKotlin.identifiant] et ses voisins).
 *
 * ## Le repli « inconnue » existe mais reste DÉNONCÉ
 *
 * Un chargement de moteur ne lève JAMAIS (miroir de
 * [ServiceMoteurLangageKotlin.unique]) : une ressource absente sert donc
 * le repli [REPLI]. MAIS le cahier (fiche 001) exige des VALEURS RÉELLES —
 * un descripteur absent du classpath est un CONTRAT DE PACK CASSÉ : c'est
 * le TEST du SPI qui le dit (il échoue sur le repli), pas le moteur qui
 * l'absorbe silencieusement. Le moteur reste honnête en régime dégradé ;
 * la CI refuse le pack cassé.
 *
 * Thread-safety : lecture UNE fois (lazy — la valeur est stable pour la
 * vie de la JVM, le descripteur est généré avant l'exécution).
 */
internal object DescripteurPackKotlin {

    /** Le chemin de la ressource générée (contrat D6 — générée par le build). */
    private const val RESSOURCE: String = "/jo/codeide/lsp/kotlin/pack/version.properties"

    /** Le repli d'une clé absente (DÉNONCÉ par la fiche 001, jamais silencieux côté test). */
    private const val REPLI: String = "inconnue"

    /** Le descripteur lu UNE fois (vide si la ressource manque — repli servi). */
    private val lu: Properties by lazy {
        val proprietes = Properties()
        try {
            ServiceMoteurLangageKotlin::class.java.getResourceAsStream(RESSOURCE)?.use { flux ->
                proprietes.load(flux)
            }
        } catch (_: Exception) {
            // Illisible (jamais observé) : le repli est servi — le test du
            // SPI dénonce le contrat cassé (D3 côté moteur, preuve côté CI).
        }
        proprietes
    }

    /**
     * La valeur d'une clé du descripteur.
     *
     * @param cle la clé lue (`id`, `version`, `minApp`, `grammaire`) ;
     * @return la valeur RÉELLE du descripteur, ou [REPLI] si la clé est
     * absente ou vide — jamais d'exception.
     */
    internal fun valeur(cle: String): String =
        lu.getProperty(cle)?.takeIf { it.isNotBlank() } ?: REPLI
}

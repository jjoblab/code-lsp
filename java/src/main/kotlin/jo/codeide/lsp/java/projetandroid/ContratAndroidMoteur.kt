package jo.codeide.lsp.java.projetandroid

import java.nio.file.Path

/**
 * Contrat Android D'UN MODULE, côté moteur (vague V-B — ADR 0020, décision
 * D2). Miroir moteur de `ModuleAndroidLsp` du module `:model` (v11) : le
 * moteur ne dépend PAS de `:model` (artefact autonome, budget ART — la
 * correspondance des champs est documentée dans `USAGE.md` §7.8).
 *
 * DEUX producteurs, UN contrat :
 *
 *  1. **La sync hôte** (prioritaire — « la sync prime par contrat », ADR
 *     0020 R3) : l'hôte lit `ModuleLsp.android` et appelle
 *     [jo.codeide.lsp.java.EnvironnementCompilation.remplacerContexteModule]
 *     avec ce contrat ;
 *  2. **Le dérivateur disque** ([ModeleAndroidDerive]) : avant toute sync,
 *     un parseur Gradle TOLÉRANT produit le même contrat depuis
 *     `build.gradle(.kts)`, le manifest et les dossiers `res/` — le moteur
 *     est intelligent sans Gradle (axe de dépassement §0.1-2).
 *
 * Immuable ; l'identité (equals/hashCode structurels) alimente
 * l'invalidation du moteur au remplacement de contexte.
 *
 * @property namespace espace de noms des ressources (`com.example.monip`) —
 *   la CLÉ des classes light (`<ns>.R`, `<ns>.BuildConfig`,
 *   `<ns>.databinding.*`) ; vide = pas de classes light R.
 * @property applicationId identifiant d'application (`null` = namespace).
 * @property minSdk niveau d'API minimal (`null` = inconnu — consommé par le
 *   lint NewApi de la vague V-C).
 * @property targetSdk niveau d'API cible (`null` = inconnu).
 * @property compileSdk niveau d'API de compilation (`null` = inconnu).
 * @property dossiersRes dossiers `res/` du sourceSet principal (premier =
 *   principal) — la source de vérité du [ReferentielRessources].
 * @property manifest chemin du manifest principal (`null` = introuvable).
 * @property viewBinding `buildFeatures.viewBinding` (AGP 8 : faux).
 * @property dataBinding `buildFeatures.dataBinding` (AGP 8 : faux).
 * @property buildConfig `buildFeatures.buildConfig` (AGP 8 : faux).
 * @property nonTransitiveRClass R non transitif (AGP 8 : vrai) — les champs
 *   ne couvrent que les ressources DU module.
 * @property nonFinalResIds champs de R non finaux (défaut : faux — finaux).
 * @property resValues valeurs `resValue` additionnelles (type:nom → valeur).
 * @property buildConfigFields champs `buildConfigField` additionnels
 *   (nom → « type valeur ») — consommés par la fabrique BuildConfig (V-B3).
 */
public data class ContratAndroidMoteur(
    public val namespace: String,
    public val applicationId: String? = null,
    public val minSdk: Int? = null,
    public val targetSdk: Int? = null,
    public val compileSdk: Int? = null,
    public val dossiersRes: List<Path> = emptyList(),
    public val manifest: Path? = null,
    public val viewBinding: Boolean = false,
    public val dataBinding: Boolean = false,
    public val buildConfig: Boolean = false,
    public val nonTransitiveRClass: Boolean = true,
    public val nonFinalResIds: Boolean = false,
    public val resValues: Map<String, String> = emptyMap(),
    public val buildConfigFields: Map<String, String> = emptyMap(),
) {
    /** Vrai ssi ce contrat active AU MOINS une classe light (R au minimum). */
    public val actif: Boolean get() = namespace.isNotEmpty() && dossiersRes.isNotEmpty()
}

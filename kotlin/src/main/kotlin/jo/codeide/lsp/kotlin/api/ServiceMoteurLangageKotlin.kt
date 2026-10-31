// CodeIDE — SPI du moteur Kotlin LSP (module :kotlin, étage 1 vague 1b —
// session documents). Code NEUF jjoblab (clean-room — contrat : fiche 049 du
// cahier : « par le SPI ([ServiceMoteurLangageKotlin.unique]), PAS par le
// type concret : c'est le chemin de production de l'hôte (D6) » ; le
// ServiceLoader et le repli reprennent le mécanisme du pack langue D6).
package jo.codeide.lsp.kotlin.api

import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import jo.codeide.lsp.kotlin.session.MoteurLangageKotlinNoyau

/**
 * Le SPI du moteur de langage Kotlin — le point d'entrée UNIQUE de l'hôte
 * (D6 : le pack langue futur se branche ici, et uniquement ici, par
 * [unique] ; aucune classe du moteur n'est référencée nommément hors du
 * module).
 *
 * ## Nourrissage moteur (fiche 042 — ADR 0211)
 *
 * [definirClasspath] est le guichet du NOURRISSAGE MOTEUR : l'hôte l'appelle
 * UNE fois par synchronisation ; il rend `true` quand le moteur nourrit
 * lui-même l'index partagé (les sessions se BRANCHENT alors sans rien
 * relire) et `false` par défaut — un moteur qui ne le surcharge pas laisse
 * la façade retomber sur le monde d'avant (poussée synchrone aux sessions,
 * tolérance de version du pack dans les DEUX sens).
 *
 * ## Chargement (D6)
 *
 * [unique] charge l'implémentation par [ServiceLoader] (descripteur
 * `META-INF/services` embarqué dans le jar du pack) et retombe sur le moteur
 * noyau du module si aucun fournisseur n'est enregistré — le chargement ne
 * lève JAMAIS (la fiche 001 verrouille le chargement et les clés du
 * descripteur).
 */
public interface ServiceMoteurLangageKotlin {

    /**
     * L'IDENTIFIANT du pack langue (contrat D6 — clé `id` du descripteur
     * `version.properties` généré par le build) : il discrimine CE pack
     * côté hôte, la valeur attendue est `jo-codeide-lsp-kotlin`.
     */
    public val identifiant: String

    /**
     * La VERSION du pack (clé `version` du descripteur) : celle du projet,
     * générée à la construction — JAMAIS un repli (fiche 001 : « valeurs
     * réelles pas des replis » ; un descripteur absent du classpath est un
     * contrat de pack cassé, le test le dénonce).
     */
    public val version: String

    /**
     * La VERSION DE LA GRAMMAIRE vendorée (clé `grammaire` du descripteur —
     * épinglage unique ADR 0121, miroir de `VERSION_GRAMMAIRE` du build).
     */
    public val versionGrammaire: String

    /**
     * La VERSION MINIMALE de l'app consommatrice (clé `minApp` du
     * descripteur — motif `x.y.z`) : la borne de compatibilité posée par
     * le premier consommateur potentiel du pack.
     */
    public val versionMinimaleApp: String

    /**
     * Crée une session de langage NEUVE et indépendante (états étanches —
     * fiche 049 : « sessions distinctes : états indépendants »).
     *
     * La session créée par un moteur nourricier est câblée sur son nourrissage
     * : son [SessionLangageKotlin.definirClasspath] est un branchement.
     *
     * @return la session prête à recevoir des documents ; jamais nulle.
     */
    public fun creerSession(): SessionLangageKotlin

    /**
     * Nourrit l'index partagé du moteur pour ce classpath — UNE fois par
     * synchronisation côté hôte (ADR 0211).
     *
     * @param classpath le classpath neutre (entrées ordonnées + racines) ;
     * @return `true` quand le moteur possède le nourrissage (la passe a été
     * menée, un snapshot immuable est publié) ; `false` par défaut — le
     * moteur ne surcharge pas le nourrissage et l'hôte retombe sur la
     * poussée synchrone aux sessions.
     */
    public fun definirClasspath(classpath: ClasspathKotlin): Boolean = false

    public companion object {

        /**
         * L'instance UNIQUE du moteur (chargée une fois, thread-safe).
         *
         * Le chargement consulte le [ServiceLoader] du classpath (descripteur
         * embarqué du pack) et retombe sur le moteur noyau du module en
         * l'absence de fournisseur — jamais d'exception au chargement.
         */
        public val unique: ServiceMoteurLangageKotlin by lazy { charger() }

        /**
         * Charge l'implémentation du SPI : le premier fournisseur du
         * [ServiceLoader], sinon le moteur noyau du module.
         *
         * @return le moteur chargé ; jamais nul.
         */
        private fun charger(): ServiceMoteurLangageKotlin {
            val fournisseur = try {
                ServiceLoader.load(ServiceMoteurLangageKotlin::class.java).firstOrNull()
            } catch (_: ServiceConfigurationError) {
                // Descripteur cassé (jamais observé) : repli honnête plutôt
                // qu'une panne de chargement — le moteur noyau sert tout.
                null
            }
            return fournisseur ?: MoteurLangageKotlinNoyau()
        }
    }
}

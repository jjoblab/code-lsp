// CodeIDE — journal « explain » des `kt.unresolved` émis (module :kotlin,
// étage 2 vague 2a — fiche 006 du cahier : instrumentation de la mission de
// fiabilisation, étape A.1). Code NEUF jjoblab (clean-room — contrat : la
// fiche 006 « chaque émission est consignée en niveau FINE — forme
// syntaxique de la référence, issue de la sélection par types complets
// (ADR 0223), récepteur TYPÉ et état de l'univers des preuves (scan projet,
// index portant, régime script) » ; ADR 0112 : FINE, JAMAIS actif en
// production par défaut).
package jo.codeide.lsp.kotlin.resolve

import java.util.logging.Level
import java.util.logging.Logger

/**
 * La forme syntaxique de la référence non résolue (première clé du journal).
 */
internal enum class FormeReference {
    /** Un nom NU (`grincher`, `introuvable`). */
    NOM_NU,

    /** Un sélecteur qualifié à absence PROUVÉE (`b.champInconnu`). */
    SELECTEUR_QUALIFIE_ABSENCE_PROUVEE,
}

/**
 * L'issue de la sélection par types complets (ADR 0223 — la « voie de
 * preuve » qui a conclu).
 */
internal enum class IssueSelection {
    /** La voie NORMALE : pas de récepteur typé, le nom est introuvable partout. */
    INCONNU,

    /** L'absence PROUVÉE : le récepteur typé porte un jeu de membres complet. */
    ABSENCE_PROUVEE,
}

/**
 * L'état du scan projet au moment de l'émission (l'univers des preuves).
 */
internal enum class EtatScan {
    /** Aucune racine source posée (projet vide — connaissance complète). */
    ABSENT,

    /** La dernière passe a couvert tous les fichiers éligibles vus. */
    COMPLET,

    /** La dernière passe a été ANNULÉE ou TRONQUÉE : l'univers n'est pas fiable. */
    PARTIEL,
}

/**
 * Le journal « explain » des `kt.unresolved` : chaque ÉMISSION est consignée
 * en niveau FINE sur un [Logger] JUL dédié, sous forme greppable
 * `clé=valeur` — ce fichier EST la « commande qui rend le journal » (fiche
 * 006 : le test branche un handler et vérifie le rendu ligne par ligne).
 *
 * ## Contrat (fiche 006 + ADR 0112)
 *
 * - **Éteint par défaut** : la production ne branche AUCUN handler et le
 *   niveau INFO filtre FINE — l'enregistrement paresseux ne construit RIEN
 *   tant que [journal] n'est pas loggable FINE (le garde `isLoggable`
 *   précède TOUTE construction) ;
 * - les références RÉSOLUES ne journalisent RIEN (le journal ne parle que
 *   des émissions) ;
 * - la ligne rend une entrée PAR émission : la référence entre guillemets
 *   (« grincher »), sa forme, l'issue de la sélection, le récepteur TYPÉ
 *   (`aucun` pour un nom nu), l'état des preuves (scan, index, script) et
 *   la position `ligne:colonne` (1-based).
 */
internal object ExplicationsNonResolus {

    /** Le logger JUL dédié (le test le connaît par son nom canonique). */
    internal val journal: Logger = Logger.getLogger(ExplicationsNonResolus::class.java.name)

    /**
     * Consigne UNE émission de `kt.unresolved` (niveau FINE).
     *
     * @param reference le nom non résolu (`grincher`) ;
     * @param forme la forme syntaxique de la référence ;
     * @param issue l'issue de la sélection par types complets ;
     * @param recepteurTypé le FQCN du récepteur TYPÉ (`com.ex.app.Boite`),
     * `null` pour un nom nu ;
     * @param etatScan l'état du scan projet ;
     * @param indexPortant l'index des appelables couvre-t-il le classpath
     * posé (le guichet de la fiche 042) ;
     * @param script le document est-il un script `.kts` ;
     * @param ligne la ligne (1-based) de la référence ;
     * @param colonne la colonne (1-based) de la référence.
     */
    internal fun consigner(
        reference: String,
        forme: FormeReference,
        issue: IssueSelection,
        recepteurTypé: String?,
        etatScan: EtatScan,
        indexPortant: Boolean,
        script: Boolean,
        ligne: Int,
        colonne: Int,
    ) {
        // Garde paresseux : rien n'est construit sous le niveau FINE
        // (production = INFO — le journal ne rend RIEN, fiche 006).
        if (!journal.isLoggable(Level.FINE)) return
        val rendu = StringBuilder("kt.unresolved")
            .append(" « ").append(reference).append(" »")
            .append(" forme=").append(
                when (forme) {
                    FormeReference.NOM_NU -> "nom-nu"
                    FormeReference.SELECTEUR_QUALIFIE_ABSENCE_PROUVEE -> "selecteur-qualifie-absence-prouvee"
                },
            )
            .append(" selection=").append(
                when (issue) {
                    IssueSelection.INCONNU -> "INCONNU"
                    IssueSelection.ABSENCE_PROUVEE -> "ABSENCE_PROUVEE"
                },
            )
            .append(" recepteur=").append(recepteurTypé ?: "aucun")
            .append(" scan=").append(
                when (etatScan) {
                    EtatScan.ABSENT -> "absent"
                    EtatScan.COMPLET -> "complet"
                    EtatScan.PARTIEL -> "partiel"
                },
            )
            .append(" index=").append(if (indexPortant) "portant" else "absent")
            .append(" script=").append(if (script) "oui" else "non")
            .append(" position=").append(ligne).append(':').append(colonne)
            .toString()
        journal.fine(rendu)
    }
}

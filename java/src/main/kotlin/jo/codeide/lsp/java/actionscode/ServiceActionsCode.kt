package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.diagnostics.Diagnostic

/**
 * Le service de code actions v2 (étage N6 — cahier fiche 063 ; ADR 0013
 * option 1 : `CodeActionService` réécrit sur le modèle ModCommand) : les
 * quick-fixes à ÉDITS RÉELS, calculés en LECTURE comme des plans
 * déclaratifs sérialisables ([ModCommande]) — l'exécution est déléguée au
 * client LSP, le moteur n'écrit JAMAIS sur le disque.
 *
 * Le dispatch est **ID-FIRST** ([FamilleProbleme.fromCode] du code du
 * diagnostic) et **Locale-indépendant** : jamais de parsing du message
 * localisé pour choisir un correcteur — les pièces nécessaires (type exact
 * de l'exception, types source/attendu du mismatch, nom non résolu) viennent
 * des [arguments STRUCTURÉS][Diagnostic.arguments] du diagnostic.
 *
 * Deux montages (fiche 063) :
 *  - **service TEXTE pur** — construit SANS moteur : les correcteurs texte
 *    (imports inutilisés, point-virgule, déclarations inutilisées,
 *    try-catch, throws, cast) ne dépendent d'aucun environnement — le banc
 *    est exécutable partout (note de réécriture de la fiche : les fixes
 *    texte sont DÉTACHÉS de la dépendance SDK) ;
 *  - **service AVEC INDEX** — construit sur un [MoteurJava] : les fixes
 *    d'import proposent les candidats de l'index des noms (jrt du JDK,
 *    jars du classpath, racines sources).
 *
 * Interdépendance documentée : les familles « unused » sont ÉTEINTES dans
 * le diagnose du moteur (décision fiche 014, `PontEcj` : « la valeur du
 * moteur est l'erreur de compilation fidèle, pas l'analytique IDE ») — les
 * diagnostics UnusedImport/LocalVariableIsNeverUsed servis à ce service
 * proviennent du montage synthétique du cahier, de l'hôte ou des futures
 * inspections lint (ADR 0013 option 1b) ; les correcteurs les traitent
 * identiquement, quelle que soit leur provenance.
 *
 * Le drapeau « codeactions.v2 » de l'ADR 0013 (cohabitation avec l'ancien
 * service pour retour arrière) ne s'applique pas à la réécriture
 * clean-room : il n'y a PAS d'ancien service dans ce dépôt — la migration
 * « un fix à la fois, validée par test doré » se traduit par les
 * correcteurs individuels et leurs fiches du cahier.
 *
 * @param moteur le moteur hôte (index des noms pour les fixes d'import) ;
 *   null = service TEXTE pur.
 */
public class ServiceActionsCode(
    private val moteur: MoteurJava? = null,
) {

    /**
     * Les actions correctrices d'une liste de diagnostics (le
     * `textDocument/codeAction` LSP : uri + plage + context.diagnostics →
     * actions).
     *
     * @param chemin le chemin logique du document (transporté pour la
     *   couche hôte ; les correcteurs N6 sont mono-fichier).
     * @param source le texte Java complet (tampon ouvert).
     * @param diagnostics les diagnostics du contexte (seuls ceux dont la
     *   famille est corrigible produisent des actions).
     * @return les actions, chacune porte des édits NON VIDES (fiche 063 :
     *   « aucune action sans édits ») — liste jamais nulle.
     */
    public fun actions(chemin: String, source: String, diagnostics: List<Diagnostic>): List<ActionCode> =
        diagnostics.flatMap { actions(chemin, source, it) }

    /**
     * Les actions correctrices d'UN diagnostic (fiche 063 : le montage
     * synthétique du cahier appelle le correcteur par diagnostic).
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet (tampon ouvert).
     * @param diagnostic le diagnostic à corriger.
     * @return les actions (0 à N — try-catch ET throws se servent
     *   ensemble sur une exception non gérée).
     */
    public fun actions(chemin: String, source: String, diagnostic: Diagnostic): List<ActionCode> {
        val resultat = ArrayList<ActionCode>()
        when (FamilleProbleme.fromCode(diagnostic.code)) {
            FamilleProbleme.UNUSED_IMPORT -> {
                CorrecteursTexte.suppressionImportInutilise(source, diagnostic)?.let { resultat.add(it) }
            }
            FamilleProbleme.MISSING_SEMI -> {
                CorrecteursTexte.pointVirguleManquant(source, diagnostic)?.let { resultat.add(it) }
            }
            FamilleProbleme.UNUSED_DECLARATION -> {
                CorrecteursTexte.suppressionDeclarationInutilise(source, diagnostic)?.let { resultat.add(it) }
            }
            FamilleProbleme.UNCAUGHT_EXCEPTION -> {
                CorrecteursTexte.entourageTryCatch(source, diagnostic)?.let { resultat.add(it) }
                CorrecteursTexte.declarationThrows(source, diagnostic)?.let { resultat.add(it) }
            }
            FamilleProbleme.TYPE_MISMATCH -> {
                CorrecteursTexte.insertionCast(source, diagnostic)?.let { resultat.add(it) }
            }
            FamilleProbleme.UNRESOLVED_REFERENCE -> {
                // Seul UndefinedType porte des candidats d'import (fiche
                // 063 : « seuls les noms ressemblant à des TYPES reçoivent
                // des candidats ») — et seulement sur le montage AVEC INDEX.
                val fqns = moteur?.fqnsForSimpleName(diagnostic.arguments.firstOrNull() ?: "") ?: emptyList()
                resultat.addAll(CorrecteursTexte.candidatsImport(source, diagnostic, fqns))
                // Nom non résolu en POSITION DE VARIABLE (vague V-A, G-014) :
                // la création de locale typée par le contexte — le banc P5 a
                // mesuré l'absence d'action sur ce profil.
                CorrecteurCreationVariable.creationVariable(source, diagnostic)?.let { resultat.add(it) }
            }
            FamilleProbleme.UNDEFINED_METHOD -> {
                CorrecteurCreationMethode.creationMethode(source, diagnostic)?.let { resultat.add(it) }
            }
            FamilleProbleme.UNKNOWN -> Unit
        }
        return resultat
    }
}

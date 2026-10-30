// CodeIDE — SPI du moteur Kotlin LSP : contrat de session (module :kotlin,
// étage 1 vague 1b — session documents). Code NEUF jjoblab (clean-room —
// contrat : fiche 049 du cahier + cadrage LSP_KOTLIN.md §5 K2 « session LSP
// minimale » : ouvrir/modifier/fermer + diagnostics neutres, le moteur ne
// voit pas LSP4J — D6 : le pack futur se branche ICI, et uniquement ici).
package jo.codeide.lsp.kotlin.api

/**
 * Le contrat de session de langage Kotlin — le SEUL chemin de production de
 * l'hôte (D6) : le pack langue futur se branche sur ce contrat et sur le SPI
 * [ServiceMoteurLangageKotlin], jamais sur un type concret du moteur.
 *
 * ## Cycle de vie des documents (fiche 049)
 *
 * - [ouvrir] / [modifier] / [fermer] suivent le protocole de synchronisation
 *   INTÉGRALE (didOpen/didChange full/didClose) : chaque mutation transporte
 *   le texte COMPLET du document ; la synchronisation incrémentale n'est pas
 *   de cet étage ;
 * - [modifier] sans [ouvrir] préalable vaut une OUVERTURE (tolérant) ;
 * - [diagnostics], [plagesPliables] et [survol] sur un document INCONNU,
 *   FERMÉ ou vide rendent une réponse VIDE honnête — JAMAIS d'exception
 *   (borne conservatrice « preuve ou silence », D3) ;
 * - [close] abandonne TOUT : les mutations postérieures sont des no-ops
 *   silencieux (aucune exception, aucun effet).
 *
 * Le mode SCRIPT (`.kts`) se déduit de l'URI : un document dont l'URI se
 * termine par `.kts` est analysé en mode script de la grammaire (les
 * instructions de premier niveau y sont légales).
 *
 * ## Classpath (fiche 042 — ADR 0211/0212)
 *
 * [definirClasspath] pose classpath et racines : en usage DIRECT (session
 * sans moteur nourricier) la définition nourrit ICI, synchronement ; quand
 * le moteur possède le nourrissage (`nourrissageParMoteur`), la définition
 * est un BRANCHEMENT — elle ne re-lect rien, chaque requête consulte l'état
 * publié par le moteur (sous-lecture honnête tant que rien n'est couvert).
 * [rafraichirSources] (L1) re-scanne les racines : une déclaration externe
 * devient alors résolvable pour le survol.
 */
public interface SessionLangageKotlin : AutoCloseable {

    /**
     * Ouvre un document (didOpen) : le texte devient l'état courant du
     * document, analysé immédiatement.
     *
     * @param uri l'identifiant du document (le mode script se déduit du
     * suffixe `.kts`) ;
     * @param texte le contenu COMPLET du document (jamais nul — peut être
     * vide, tronqué ou hostile : la grammaire ne lève jamais) ;
     * @throws kotlin.Exception JAMAIS — une session fermée ignore
     * silencieusement l'ouverture (no-op honnête).
     */
    public fun ouvrir(uri: String, texte: CharSequence)

    /**
     * Modifie un document (didChange, synchronisation intégrale) : le texte
     * remplace INTÉGRALEMENT l'état courant, ré-analysé immédiatement.
     *
     * Sans [ouvrir] préalable, l'appel vaut une ouverture (tolérant, fiche
     * 049 — comportement `didChange-sans-didOpen---tolerant,-vaut-une-ouverture`).
     *
     * @param uri l'identifiant du document ;
     * @param texte le contenu COMPLET et NOUVEAU du document ;
     * @throws kotlin.Exception JAMAIS — une session fermée ignore
     * silencieusement la modification.
     */
    public fun modifier(uri: String, texte: CharSequence)

    /**
     * Ferme un document (didClose) : le document est OUBLIÉ — les requêtes
     * postérieures le voient inconnu (réponses vides).
     *
     * @param uri l'identifiant du document à oublier ; une URI inconnue est
     * ignorée sans exception.
     */
    public fun fermer(uri: String)

    /**
     * Rend les diagnostics COURANTS d'un document : erreurs de syntaxe
     * (`kt.syntax` — plage et message du parseur), imports inutilisés
     * (`kt.unusedImport` — joker et directives en édition JAMAIS signalés)
     * et imports conflictuels (`kt.conflictingImport` — signalés sur le plus
     * tardif, information liée sur la première directive, KP15).
     *
     * @param uri l'identifiant du document ;
     * @return les diagnostics, en ordre du document ; liste VIDE pour un
     * document inconnu, fermé ou sain — jamais `null`, jamais d'exception.
     */
    public fun diagnostics(uri: String): List<DiagnosticKotlin>

    /**
     * Rend les plages pliables d'un document (fiche 049 : classe, fonctions,
     * imports et KDoc).
     *
     * @param uri l'identifiant du document ;
     * @return les plages en ordre du document ; liste VIDE pour un document
     * inconnu, fermé ou vide — jamais d'exception.
     */
    public fun plagesPliables(uri: String): List<PlagePliableKotlin>

    /**
     * Rend le survol (S2) au point demandé : la signature de la déclaration
     * visée, accompagnée de sa documentation KDoc lorsqu'il y en a une.
     *
     * La chaîne conservatrice de l'étage : déclaration du document courant
     * (site de déclaration ou référence par nom), puis déclarations des
     * AUTRES documents ouverts, puis déclarations externes du scan des
     * racines (après [rafraichirSources]).
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret dans le texte du document ;
     * @return le survol ; `null` (conservateur, D3) si le point ne désigne
     * aucun identifiant résolvable — jamais d'exception.
     */
    public fun survol(uri: String, deplacement: Int): SurvolKotlin?

    /**
     * Re-scanne les racines sources du classpath (L1) : les déclarations
     * externes deviennent résolvables pour le survol à partir de la requête
     * suivante.
     *
     * Le scan ne court JAMAIS sous le verrou de session (ADR 0212) : la
     * marche des racines précède la publication du résultat. Une session
     * fermée l'ignore silencieusement (no-op honnête, fiche 049).
     */
    public fun rafraichirSources()

    /**
     * Pose le classpath de la session : entrées des bibliothèques et racines
     * des sources (fiche 042 — ADR 0211).
     *
     * En usage direct (sans moteur nourricier), la définition nourrit ICI,
     * synchronement. Quand le moteur possède le nourrissage, la définition
     * est un BRANCHEMENT quasi gratuit : elle pose classpath et racines et
     * ne re-lect RIEN — l'état de l'index se consulte à chaque requête.
     *
     * @param classpath le classpath neutre (entrées ordonnées + racines) ;
     * @throws kotlin.Exception JAMAIS — une session fermée ignore
     * silencieusement la définition.
     */
    public fun definirClasspath(classpath: ClasspathKotlin)

    /**
     * Abandonne TOUT (documents, classpath, scan) : les mutations et requêtes
     * postérieures sont des no-ops silencieux (fiche 049 — comportement
     * `fermeture---tout-abandonne,-mutations-posterieures-no-ops`).
     *
     * La fermeture est IDEMPOTENTE : fermer une session déjà fermée ne fait
     * rien, ne lève jamais.
     */
    override fun close()
}

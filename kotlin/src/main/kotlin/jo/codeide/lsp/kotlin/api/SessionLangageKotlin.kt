// CodeIDE — SPI du moteur Kotlin LSP : contrat de session (module :kotlin,
// étage 1 vague 1b — session documents ; étage 2 vague 2b — quick-fix
// d'import `importsCandidats`, fiche 046). Code NEUF jjoblab (clean-room —
// contrat : fiches 049/046 du cahier + cadrage LSP_KOTLIN.md §5 K2 « session
// LSP minimale » : ouvrir/modifier/fermer + diagnostics neutres, le moteur
// ne voit pas LSP4J — D6 : le pack futur se branche ICI, et uniquement ici).
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
 * La pose des racines AMORCE le scan en tâche de fond (fiche 041 : le coût
 * du scan n'est pas à la première frappe) — mais la session ne SERT rien du
 * scan avant [rafraichirSources]. [rafraichirSources] (L1) rafraîchit la
 * passe (incrémentale par fichier) : une déclaration externe devient alors
 * résolvable pour le survol.
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
     * Rend les propositions de COMPLÉTION au point demandé (étage 3 —
     * ADR 0113 : le pipeline complet — classification du caret, régimes
     * de collecte membre/nom/import/contextuelle, arrangement honnête ;
     * le germe membre de la vague 2a, fiche 022 : « copy proposé après
     * `p.` », reste couvert par le régime MEMBRE).
     *
     * Le moteur est conservateur : un point sans contexte prouvable rend
     * une liste VIDE honnête (récepteur non typé, valeur en cours de
     * frappe — D3), jamais une invention.
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret dans le texte du document ;
     * @return les propositions ARRANGÉES (ordonnées puis plafonnées à
     * l'affichage) ; liste VIDE pour un document inconnu, fermé ou une
     * position sans contexte — jamais `null`, jamais d'exception.
     */
    public fun completion(uri: String, deplacement: Int): List<PropositionKotlin>

    /**
     * Rend la réponse ARRANGÉE de complétion au point demandé (étage 3 —
     * ADR 0113 : l'arrangement honnête — le plafond d'affichage ne ment
     * jamais sur la taille de l'espace de candidats).
     *
     * Le genre du contexte (membre, nom, import, arguments nommés,
     * constantes d'enum, attributs d'annotation) voyage avec la réponse :
     * l'hôte sait POURQUOI la liste a cette forme (contexte exclusif ou
     * régime de repli), et le total réel lui dit si la liste est
     * tronquée.
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret dans le texte du document ;
     * @return la réponse arrangée (genre + propositions ≤ plafond +
     * total réel) ; propositions VIDE et genre [GenreContexteKotlin.INCONNU]
     * pour un document inconnu, fermé ou une position sans contexte —
     * jamais `null`, jamais d'exception.
     */
    public fun completionArrangee(uri: String, deplacement: Int): ReponseCompletionKotlin

    /**
     * Rend les FQCN candidats à l'IMPORT pour le nom sous le point demandé
     * (étage 2 vague 2b, fiche 046 — le carburant du quick-fix « Importer »
     * : les classes du CLASSPATH portant EXACTEMENT ce nom simple).
     *
     * Le contrat est conservateur (D3) : un nom déjà résolu par un import
     * EXACT (même nom effectif) ou couvert par un import JOKER rend une
     * liste VIDE (rien à proposer — jamais de quick-fix inutile) ; le régime
     * dégradé (classpath non posé, passe non publiée) rend une liste vide
     * honnête ; l'ordre des candidats est DÉTERMINISTE (alphabétique).
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret DANS l'identifiant non résolu ;
     * @return les FQCN candidats (`android.widget.TextView`), triés ; liste
     * VIDE pour un document inconnu/fermé, une position sans identifiant,
     * un nom déjà importé ou couvert, ou sans classpath — jamais `null`,
     * jamais d'exception.
     */
    public fun importsCandidats(uri: String, deplacement: Int): List<String>

    /**
     * Rend les CORRECTIFS DE TEXTE du document (étage 2 vague 2f — fiche
     * 016 : accès non sûr `?.`/`!!`/entourage, conversions de littéraux).
     *
     * Le moteur RE-PROUVE le document courant (jamais un diagnostic
     * périmé) : un correctif existe SI ET SEULEMENT SI le diagnostic
     * correspondant existe — la matrice de conversion est FERMÉE par
     * famille, les cibles non primitives ne reçoivent que le changement
     * de type, les accès sûrs et gardes de nullité ne reçoivent RIEN
     * (D3).
     *
     * @param uri l'identifiant du document ;
     * @return les correctifs en ordre du document ; liste VIDE pour un
     * document inconnu/fermé ou sans correctif — jamais `null`, jamais
     * d'exception.
     */
    public fun correctifsTexte(uri: String): List<CorrectifTexteKotlin>

    /**
     * Rend le correctif « IMPLÉMENTER LES MEMBRES MANQUANTS » de la
     * déclaration de type sous le point (étage 2 vague 2f — fiche 020,
     * chantier P1-C ADR 0229) : les membres ABSTRAITS des supertypes
     * (projet par la clôture, classpath par la vue JVM — getters/setters
     * appariés en propriétés, paramètres de type substitués par la clause
     * écrite) insérés après le dernier membre, dans le corps créé en fin
     * d'en-tête s'il est absent, ou remplaçant un corps vide.
     *
     * Conservateur (D3) : une cible interface ou abstraite ELLE-MÊME, une
     * clause NUE sur un supertype générique, ou une clôture non fermée ne
     * produisent RIEN.
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret DANS le nom de la déclaration ;
     * @return le correctif d'insertion ; liste VIDE sans déclaration
     * visée, sans supertype ou sous garde D3 — jamais `null`, jamais
     * d'exception.
     */
    public fun membresManquants(uri: String, deplacement: Int): List<CorrectifTexteKotlin>

    /**
     * Re-scanne les racines sources du classpath (L1) : les déclarations
     * externes deviennent résolvables pour le survol à partir de la requête
     * suivante.
     *
     * La passe court sur le FIL DÉDIÉ du scanneur paresseux (fiche 041 :
     * jamais de parse sur le fil appelant, jamais de scan sous le verrou de
     * session — ADR 0212) et est INCRÉMENTALE par fichier (cache mtime : un
     * fichier inchangé n'est jamais re-parsé) ; l'appel attend
     * l'aboutissement de la passe puis publie le snapshot immuable. Une
     * session fermée l'ignore silencieusement (no-op honnête, fiche 049).
     */
    public fun rafraichirSources()

    /**
     * Rend les emplacements de DÉFINITION du nom sous le point (étage 2
     * vague 2e — fiche 031 : go-to-definition de l'usage commonMain vers
     * l'`expect` KMP).
     *
     * La chaîne conservatrice (miroir du survol) : déclarations du document
     * courant (site de déclaration ou même nom), puis déclarations des
     * AUTRES documents ouverts, puis déclarations externes du scan des
     * racines (leur URI de fichier + la plage du nom).
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret DANS l'identifiant usage ;
     * @return les emplacements de déclaration (le premier est le meilleur
     * candidat) ; liste VIDE pour un document inconnu/fermé, une position
     * sans identifiant ou un nom sans déclaration trouvable — jamais
     * `null`, jamais d'exception.
     */
    public fun definition(uri: String, deplacement: Int): List<EmplacementKotlin>

    /**
     * Rend les SYMBOLES du document (le plan du fichier — étage 2 vague
     * 2e, fiche 025 : « symboles de document d'un fichier Compose-like »)
     * : chaque déclaration nommée avec son genre neutre et ses plages.
     *
     * La liste est PLATE en ordre du document (la hiérarchie et le
     * classement relèvent de l'étage 3) ; les déclarations sans nom
     * (compagnon anonyme) sont ÉCARTÉES (conservateur).
     *
     * @param uri l'identifiant du document ;
     * @return les symboles en ordre du document ; liste VIDE pour un
     * document inconnu, fermé ou sans déclaration — jamais `null`, jamais
     * d'exception.
     */
    public fun symboles(uri: String): List<SymboleDocumentKotlin>

    /**
     * Rend les RÉFÉRENCES du nom sous le point (étage 2 vague 2e — fiche
     * 025 : « références d'une fonction projet ») : le site de déclaration
     * (s'il vit dans le document) puis chaque RÉFÉRENCE du même nom dans le
     * document, en ordre du document.
     *
     * Le périmètre est le DOCUMENT courant (honnête : les usages des
     * sources externes relèvent de l'indexation de l'étage 3) ; les
     * homonymes non liés (une locale de même nom dans une autre portée)
     * peuvent entrer dans la liste — la résolution fine relève de
     * l'étage 3, jamais de faux retraits ici.
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret DANS le nom (usage ou
     * déclaration) ;
     * @return les emplacements (déclaration puis usages) ; liste VIDE pour
     * un document inconnu/fermé, une position sans identifiant ou un nom
     * sans occurrence — jamais `null`, jamais d'exception.
     */
    public fun occurrences(uri: String, deplacement: Int): List<EmplacementKotlin>

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

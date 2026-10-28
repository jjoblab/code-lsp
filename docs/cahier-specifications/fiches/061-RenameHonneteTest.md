# Fiche 061 — RenameHonneteTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/RenameHonneteTest.java`.
> **Domaine** : rename
> **Résumé** : Lot S0-b « rename honnête » (I8, comportement clé documenté dans `docs/LSP_JAVA.md` §18.1) : `prepareRename` rend le placeholder EXACT et la plage EXACTE du jeton (caret au milieu du nom compris) ; `rename` sans cible → erreur TYPOÉE RequestFailed avec message FR et `data` structuré (jamais d'édition vide silencieuse) ; renommage nominal sous les DEUX formes négociées du WorkspaceEdit — `changes` (héritée) quand le client n'annonce pas `workspace.workspaceEdit.documentChanges`, `documentChanges` versionnée quand il l'annonce. Fixture unique : classe `Panier` (champ `total` affecté/retourné, méthodes `ajouter`/`somme`). Hypothèse visible dans `@BeforeAll` : `android.jar` présent, sinon classe ignorée ; le classpath est branché après coup via le remplacement d'environnement (même live-refresh que la production).

## Comportements

### prepareRenamePlaceholderExactCaretMilieuDuNom — placeholder et plage exacts du jeton
- **Préconditions / entrée** : `didOpen` de `Panier.java` (version 0) ; `prepareRename` au caret placé AU MILIEU de « ajouter » (offset nom + 3).
- **Comportement attendu** : résultat non-null en forme { range, placeholder } ; placeholder EXACT « ajouter » ; la plage couvre TOUT le jeton — début de plage = DÉBUT du nom, fin de plage = FIN du nom (et non [caret, caret+longueur) comme le faisait l'ancien code).
- **Bornes** : réponse en 30 s max ; placeholder exact ; plage complète du jeton.
- **Statut** : actif

### prepareRenameHorsNomRendNull — hors jeton renommable → null
- **Préconditions / entrée** : `prepareRename` au caret sur le mot-clé « package ».
- **Comportement attendu** : null (un mot-clé n'est pas un jeton renommable — le client n'ouvre pas la boîte de dialogue de renommage).
- **Bornes** : réponse en 30 s max.
- **Statut** : actif

### renameSansCibleErreurTypeeRequestFailed — sans cible : erreur typée (I8)
- **Préconditions / entrée** : `rename` au caret sur le mot-clé « public » avec nouveau nom « autre ».
- **Comportement attendu** : rejet `ResponseErrorException` avec code `RequestFailed` (-32803) ; message FR contenant « Aucun symbole renommable » ; `data` structuré non-null contenant le code machine « renommage.sansCible » (et la position).
- **Bornes** : code d'erreur exact -32803 ; data.code = « renommage.sansCible ».
- **Statut** : actif

### renommageNominalFormeChangesQuandClientNeDeclarePas — forme changes (caps legacy)
- **Préconditions / entrée** : serveur initialisé avec les caps LEGACY (aucune `WorkspaceEditCapabilities`, miroir du client cel-lsp actuel) ; `rename` de « total » en « solde ».
- **Comportement attendu** : `WorkspaceEdit` avec `changes` non-null (l'URI ouvert y figure) et `documentChanges` NULL (les deux formes ne coexistent jamais) ; exactement 4 éditions pour l'URI — déclaration + 2 usages dans l'affectation + return ; chaque édition remplace EXACTEMENT le texte « total » par « solde ».
- **Bornes** : réponse en 60 s max ; 4 éditions exactement ; plage = exactement « total » à chaque fois.
- **Statut** : actif

### renommageNominalFormeDocumentChangesVersionneeQuandNegociee — forme documentChanges versionnée (caps négociées)
- **Préconditions / entrée** : second serveur initialisé avec caps `workspace.workspaceEdit.documentChanges = true` ; `rename` de « somme » en « cumul ».
- **Comportement attendu** : `WorkspaceEdit` avec `documentChanges` non-null contenant exactement 1 `TextDocumentEdit` pour l'URI ouvert, portant la version COURANTE du document (0 — posée par le didOpen) ; `changes` null OU vide (jamais les deux formes) ; exactement 1 édition (la déclaration seule de « somme », aucun autre usage dans la source).
- **Bornes** : réponse en 60 s max ; 1 entrée documentChanges ; version 0 ; 1 édition exactement.
- **Statut** : actif

## Notes pour la réécriture
- Négociation de forme : le serveur doit mémoriser les caps client (annoncées à initialize) et rendre `changes` OU `documentChanges`, jamais les deux — le client réel (cel-lsp) n'annonce pas documentChanges et reçoit donc la forme héritée.
- L'édition versionnée porte la version posée par le didOpen (0 ici) — le document ouvert garde sa version courante.
- Erreur typée rename : RequestFailed -32803, message FR « Aucun symbole renommable », data structuré avec code machine « renommage.sansCible » + position (à reproduire tel quel).
- prepareRename : placeholder = jeton exact ; plage = jeton entier même si le caret est au milieu.
- Les positions sont en unités UTF-16 (comme le moteur) ; hypothèse d'environnement android.jar — sinon classe ignorée (assumption visible dans `@BeforeAll`).

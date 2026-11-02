# Fiche 059 — LifecycleHygieneTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/LifecycleHygieneTest.java`.
> **Domaine** : cycle-vie-lsp
> **Résumé** : Lot S0-a « hygiène protocolaire » (audit §6.3) : garde lifecycle I2 (requête avant `initialized` rejetée ServerNotInitialized, après `shutdown` rejetée InvalidRequest ; notifications ignorées hors fenêtre), initialize réentrant I3 (InvalidRequest), didSave sans `includeText` I5 (republication depuis le buffer vivant), serverInfo.version I7 (version du dépôt lue de la ressource `jo/lspjava/pack/version.properties`), convention null/vide I9 et erreur typée rename I8. Tous les tests tournent SANS classpath Android (protocole verrouillé, pas l'analyse) ; client espion pour les publications de diagnostics.

## Comportements

### initializeDeuxFoisRejeteInvalidRequest — initialize réentrant rejeté (I3)
- **Préconditions / entrée** : serveur déjà initialisé une fois ; second appel à `initialize`.
- **Comportement attendu** : rejet (ExecutionException dont la cause est `ResponseErrorException`) avec code `InvalidRequest` (-32600) ; le message contient « initialize ».
- **Bornes** : code d'erreur exact -32600.
- **Statut** : actif

### requeteAvantInitializedRejeteeServerNotInitialized — requête précoce rejetée (I2)
- **Préconditions / entrée** : serveur NON initialisé (aucun `initialized`) ; requête `hover` sur un document fictif.
- **Comportement attendu** : rejet SYNCHRONE avec code `ServerNotInitialized` (-32002).
- **Bornes** : code d'erreur exact -32002 ; rejet immédiat (pas de future en erreur différée).
- **Statut** : actif

### requeteApresShutdownRejeteeInvalidRequest — requête post-shutdown rejetée (I2)
- **Préconditions / entrée** : serveur initialisé puis `shutdown` terminé ; requête `completion`.
- **Comportement attendu** : rejet avec code `InvalidRequest` (-32600) — l'ancien code servait la requête sur des moteurs disposés.
- **Bornes** : code d'erreur exact -32600.
- **Statut** : actif

### notificationAvantInitializedIgnoree — notification précoce ignorée, pas rejetée
- **Préconditions / entrée** : client espion connecté ; `didOpen` AVANT initialize ; attente ; puis initialisation complète et MÊME `didOpen`.
- **Comportement attendu** : le didOpen précoce ne lève AUCUNE exception et ne produit AUCUNE publication (contrôle après 200 ms) ; une fois INITIALIZED, le même didOpen publie des diagnostics (sous 3 s) — contre-épreuve.
- **Bornes** : 0 publication après 200 ms ; publication ≤ 3 s après initialized.
- **Statut** : actif

### notificationApresShutdownIgnoree — notification post-shutdown ignorée
- **Préconditions / entrée** : serveur initialisé puis shutdown ; notification `didClose`.
- **Comportement attendu** : ignorée sans exception (une notification n'a pas de réponse d'erreur — la spec demande d'ignorer, pas de rejeter).
- **Bornes** : —
- **Statut** : actif

### didSaveSansIncludeTextRepublieDepuisLeBuffer — didSave sans includeText republie (I5)
- **Préconditions / entrée** : `didOpen` (version 0, « class Save {} ») ; `didChange` full-sync (version 1, texte avec erreur de syntaxe longue) ; attente du debounce ; `didSave` SANS texte (includeText absent).
- **Comportement attendu** : après didChange, au moins 2 publications cumulées ; le didSave sans texte déclenche une NOUVELLE publication (≤ 3 s) qui porte l'URI du document et la version COURANTE du buffer (1) — la publication didSave transportant la version est elle-même verrouillée.
- **Bornes** : debounce de 50 ms (drain attendu 400 ms) ; ≥ 2 publications après didChange ; nouvelle publication ≤ 3 s ; version publiée = 1.
- **Statut** : actif

### didSaveSurDocumentJamaisOuvertNeRepubliePas — didSave sans buffer : rien
- **Préconditions / entrée** : `didSave` sur un URI JAMAIS ouvert.
- **Comportement attendu** : AUCUNE publication (contrôle après 200 ms) — republier sur un buffer vide serait faux ; l'invalidation de cache suffit.
- **Bornes** : 0 publication après 200 ms.
- **Statut** : actif

### serverInfoVersionEstCelleDuDepot — serverInfo.version = version du dépôt (I7)
- **Préconditions / entrée** : `initialize` d'un serveur neuf ; lecture de la clé `version` de la ressource `jo/lspjava/pack/version.properties` embarquée.
- **Comportement attendu** : serverInfo.name = « lspjava » ; version non-null, différente de la valeur fantôme « 1.2.0 », et EXACTEMENT égale à la version de la ressource du dépôt.
- **Bornes** : version fantôme proscrite « 1.2.0 » ; égalité stricte avec la ressource.
- **Statut** : actif

### conventionNullVideSelonRepliClient — convention null/vide (I9)
- **Préconditions / entrée** : didOpen d'un document trivial (« class Conv {} ») ; `hover` au caret (0, 12) sans symbole ; `foldingRange` ; `inlayHint` (plage 0:0–1:0) ; `definition`, `documentSymbol`, `semanticTokensFull` sur un document JAMAIS ouvert.
- **Comportement attendu** : hover → null (le client a un repli) ; foldingRange → liste non-null VIDE (moteur utilisable + aucun pli — « indisponible » est une autre information) ; inlayHint → liste non-null VIDE ; definition → réponse non-null avec liste vide ; documentSymbol → liste non-null vide ; semanticTokens → data null ou vide.
- **Bornes** : foldingRange et inlayHint en 30 s max ; autres requêtes en 5 s max ; null interdit partout sauf hover.
- **Statut** : actif

### renameSansSymboleErreurTypeeSansClasspath — rename sans cible : erreur typée (I8)
- **Préconditions / entrée** : didOpen de `R.java` (« class R {} ») ; requête `rename` au caret (0, 3) — sur le mot-clé « class », sans classpath Android.
- **Comportement attendu** : rejet `ResponseErrorException` avec code `RequestFailed` (-32803) ; message FR contenant « Aucun symbole renommable » — jamais un WorkspaceEdit vide silencieux.
- **Bornes** : code d'erreur exact -32803.
- **Statut** : actif

## Notes pour la réécriture
- Codes d'erreur exacts à reproduire : ServerNotInitialized -32002 (avant initialized), InvalidRequest -32600 (après shutdown ET initialize réentrant), RequestFailed -32803 (rename sans cible) ; messages en français explicites.
- Notifications hors fenêtre : TOUJOURS ignorées silencieusement (jamais rejetées) — avant initialize comme après shutdown.
- didSave republie SYSTÉMATIQUEMENT depuis le buffer vivant (version courante transportée dans la publication) ; document jamais ouvert → aucune publication.
- Convention I9 : null quand le client a un repli (hover), structure VIDE sinon (foldingRange, inlayHint, definition, documentSymbol, semanticTokens) ; « moteur indisponible → null » n'est pas reproductible sur JDK de bureau (image jrt) — seule la branche est documentée.
- La version du serveur provient d'une ressource embarquée `jo/lspjava/pack/version.properties` (clé `version`) — à synchroniser avec la version du dépôt en réécriture.

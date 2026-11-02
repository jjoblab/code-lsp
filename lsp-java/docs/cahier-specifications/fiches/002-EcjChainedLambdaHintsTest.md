# Fiche 002 — EcjChainedLambdaHintsTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/EcjChainedLambdaHintsTest.java`.
> **Domaine** : hover-inlay
> **Résumé** : Verrouille deux familles d'inlay hints (parité `JdtInlayHintService` du projet tiers de référence) : les **chaining hints** — sur un chaînage fluent multi-ligne, le type du récepteur (l'appel de la ligne précédente) est affiché à la fin de SA ligne, libellé = nom court sans deux-points, padding gauche ; aucun hint pour le premier maillon (récepteur = constructeur) ni sur une seule ligne — et les **hints de type des paramètres lambda implicites** — « : Type » collé à la fin du nom, sans padding ; rien pour un paramètre typé explicitement. Le test passe par le cycle LSP complet : `initialize`/`initialized` (garde lifecycle I2, v0.134.11) puis `didOpen`, et interroge `inlayHint` sur `file:///Demo.java`.

## Comportements

### multiLineChain_emitsReceiverTypeAtEachBoundary — chaining hints aux frontières multi-lignes
- **Préconditions / entrée** : document ouvert ; source `String r = new StringBuilder("a")` ligne 2 puis `.append("b")` / `.append("c")` / `.toString()` chacun sur sa propre ligne ; requête `inlayHint` sur la range (0,0)–(50,MAX).
- **Comportement attendu** : exactement 2 hints de libellé « StringBuilder » (frontières `.append("c")`←`.append("b")` et `.toString()`←`.append("c")`) ; kind `Type` ; `paddingLeft=TRUE` et `paddingRight≠TRUE` ; ancres en fin de ligne du récepteur : (3,24) et (4,24). Le maillon `.append("b")` (récepteur = `ClassInstanceCreation`) ne reçoit PAS de hint. L'ordre de la liste n'est pas asserté (parcours pré-ordre : la frontière la plus externe arrive en premier) — on asserte l'ensemble des ancres.
- **Bornes** : 2 hints ; ancres exactes (3,24) et (4,24).
- **Statut** : actif

### sameLineChain_emitsNoChainingHint — chaînage monoligne silencieux
- **Préconditions / entrée** : même chaînage `new StringBuilder("a").append("b").append("c").toString()` écrit sur UNE seule ligne.
- **Comportement attendu** : aucun hint de libellé « StringBuilder » (un hint monoligne ne serait que du bruit visuel).
- **Bornes** : 0 hint de chaînage.
- **Statut** : actif

### implicitLambdaParam_emitsTypeHint — paramètre lambda implicite
- **Préconditions / entrée** : `list.forEach(x -> System.out.println(x));` sur une `List<String>` ; requête sur toute la plage du document.
- **Comportement attendu** : exactement 1 hint de libellé « : String », kind `Type`, sans padding gauche NI droit (collé au nom), ancré ligne 4 colonne 22 (fin du paramètre `x`).
- **Bornes** : 1 hint ; position (4,22).
- **Statut** : actif

### typedLambdaParam_emitsNoHint — paramètre lambda typé explicitement
- **Préconditions / entrée** : `list.forEach((String x) -> ...)`.
- **Comportement attendu** : aucun hint « : String » (le type est déjà visible dans la source).
- **Bornes** : 0 hint.
- **Statut** : actif

### twoImplicitLambdaParams_bothHinted — deux paramètres implicites hintés
- **Préconditions / entrée** : `BiConsumer<String, Integer> c = (a, b) -> System.out.println(a + b);`.
- **Comportement attendu** : un hint « : String » (paramètre `a`) ET un hint « : Integer » (paramètre `b`).
- **Bornes** : 2 hints (un par paramètre implicite).
- **Statut** : actif

## Notes pour la réécriture
- Les requêtes sont rejetées hors fenêtre INITIALIZED : le serveur doit passer par `initialize`/`initialized` avant tout `inlayHint` (garde lifecycle I2) — timeouts de 10 s sur `initialize` et sur la requête.
- Libellés contractuels : chaining = nom COURT du type, SANS deux-points, AVEC padding gauche ; lambda-param = « : Type » collé, SANS padding.
- Le premier maillon d'un chaînage (récepteur constructeur) ne reçoit jamais de hint — le récepteur doit être un `MethodInvocation` (parité explicite du projet tiers de référence).
- L'ordre des hints dans la liste n'est PAS contractuel (parcours pré-ordre) ; les tests assertent des ensembles de positions.
- Anomalie de nommage : la classe interne s'appelle `JdtChainedLambdaHintsTest` alors que le fichier est `EcjChainedLambdaHintsTest.java` (reliquat du portage jdt→ecj).

# Fiche 003 — InlayHintTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/InlayHintTest.java`.
> **Domaine** : hover-inlay
> **Résumé** : Couverture du fournisseur d'inlay hints via `JavaTextDocumentService` (cycle `initialize`/`initialized` + `didOpen` sur `file:///Demo.java`). Verrouille : hints de type « : Type » pour les déclarations `var` dont l'initialiseur est inférable (littéraux, création d'objet, retour de méthode), rien pour les déclarations non-`var` ; hints de noms de paramètres « name: » aux sites d'appel de méthodes déclarées dans la CU (kind `Parameter`, libellé terminé par « : ») ; rien pour les appels sans argument ou externes à la CU ; pas de filtrage par range côté serveur ; liste volontairement NON triée (contrat documenté).

## Comportements

### inlayHint_stringLiteral_var_emitsStringTypeHint — var initialisé par littéral chaîne
- **Préconditions / entrée** : `var s = "hello";` en ligne 2 (0-based) ; requête sur la range couvrant tout le fichier.
- **Comportement attendu** : exactement 1 hint, kind `Type`, libellé « : String », position sur la ligne 2 ; la colonne n'est contractuelle que comme « dans la ligne et non négative » (la conversion d'offset peut tomber d'un côté ou de l'autre de l'identifiant).
- **Bornes** : 1 hint.
- **Statut** : actif

### inlayHint_integerLiteral_emitsIntTypeHint — var initialisé par littéral entier
- **Préconditions / entrée** : `var x = 42;` ; range (0,0)–(5,MAX).
- **Comportement attendu** : exactement 1 hint, libellé « : int ».
- **Bornes** : 1 hint.
- **Statut** : actif

### inlayHint_booleanLiteral_emitsBooleanTypeHint — var booléen
- **Préconditions / entrée** : `var b = true;`.
- **Comportement attendu** : exactement 1 hint, libellé « : boolean ».
- **Bornes** : 1 hint.
- **Statut** : actif

### inlayHint_longLiteral_emitsLongTypeHint — var long
- **Préconditions / entrée** : `var l = 42L;`.
- **Comportement attendu** : exactement 1 hint, libellé « : long ».
- **Bornes** : 1 hint.
- **Statut** : actif

### inlayHint_objectCreation_emitsTypeNameHint — var initialisé par création d'objet
- **Préconditions / entrée** : `var list = new ArrayList<String>();` (avec import).
- **Comportement attendu** : exactement 1 hint dont le libellé contient « ArrayList » (le libellé porte le type complet avec les generics).
- **Bornes** : 1 hint.
- **Statut** : actif

### inlayHint_nonVarDeclaration_emitsNoHint — déclarations fortement typées silencieuses
- **Préconditions / entrée** : `String s = "hello"; int x = 42;` (déclarations non-`var`).
- **Comportement attendu** : 0 hint (le type est déjà visible dans la source — un hint serait redondant).
- **Bornes** : 0 hint.
- **Statut** : actif

### inlayHint_methodCallInitializer_emitsBindingTypeHint — var initialisé par appel de méthode
- **Préconditions / entrée** : `var s = n();` où `String n() { return ""; }` est déclaré dans la même CU.
- **Comportement attendu** : exactement 1 hint « : String » (type de retour réel de l'appel, résolu par bindings).
- **Bornes** : 1 hint.
- **Statut** : actif

### inlayHint_methodCall_emitsParameterNameHints — noms de paramètres aux sites d'appel
- **Préconditions / entrée** : appel `setName("John", "Doe")` sur une méthode `setName(String first, String last)` déclarée dans la CU.
- **Comportement attendu** : exactement 2 hints, tous deux de kind `Parameter` ; les libellés finissent par « : » (style « first: » / « last: ») — résolution de la surcharge exacte.
- **Bornes** : 2 hints.
- **Statut** : actif

### inlayHint_noArgMethodCall_emitsNoHint — appel sans argument
- **Préconditions / entrée** : `doStuff()` où `public void doStuff() {}` est déclaré dans la CU.
- **Comportement attendu** : 0 hint.
- **Bornes** : 0 hint.
- **Statut** : actif

### inlayHint_externalMethodCall_emitsNoHint — appel externe à la CU
- **Préconditions / entrée** : `System.out.println("hello")` (méthode déclarée hors de la CU, dans `PrintStream`).
- **Comportement attendu** : 0 hint (les méthodes externes ne sont pas résolues par l'index intra-CU).
- **Bornes** : 0 hint.
- **Statut** : actif

### inlayHint_emptyRange_returnsEmptyList — range ne couvrant pas la déclaration
- **Préconditions / entrée** : source avec `var s = "hello";` en ligne 2 ; requête sur la range (0,0)–(0,MAX) qui n'inclut PAS la ligne 2.
- **Comportement attendu** : réponse non nulle, sans crash. Le serveur parcourt toute la CU et ne filtre PAS par range : il renvoie tous les hints de la CU — le filtrage par range est la responsabilité de l'éditeur.
- **Bornes** : —
- **Statut** : actif

### inlayHint_varAndCallOnSameLine_emitsBothHintsUnsorted — liste délibérément non triée
- **Préconditions / entrée** : ligne 3 = `        var s = "hi"; greet(s);` (méthode `greet(String name)` déclarée dans la CU).
- **Comportement attendu** : 2 hints dans cet ordre exact : index 0 = hint `Parameter` « name: » ancré (3,28) (sur l'argument `s`) ; index 1 = hint `Type` « : String » ancré (3,13) (fin du nom `s` du var). Le caractère du premier est STRICTEMENT supérieur à celui du second : la liste n'est PAS triée par position — le client doit trier par colonne avant le tissage.
- **Bornes** : 2 hints ; colonnes exactes 28 et 13 ; désordre asserté.
- **Statut** : actif

## Notes pour la réécriture
- Deux passes de production : les hints de paramètres (passe AST) sont calculés AVANT les hints var, d'où le désordre contractuel — le tri côté client (`EditorView.layoutForLine`, car `buildColumnMaps` suppose une liste triée) est une dépendance assumée.
- Aucun filtrage par range côté serveur : la range de la requête est ignorée (comportement documenté par le test).
- Hints de paramètres limités aux méthodes déclarées DANS la CU courante (index intra-CU) ; surcharge exacte résolue par bindings.
- Timeout de la requête `inlayHint` : 5 s ; garde lifecycle I2 (serveur rejetant hors INITIALIZED).
- La colonne exacte du hint var n'est pas contractuelle pour le cas littéral (seule la ligne l'est) — marge de liberté pour la réécriture.

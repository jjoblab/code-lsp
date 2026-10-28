# Fiche 040 — ScratchAstShapesTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/ScratchAstShapesTest.java`.
> **Domaine** : parse
> **Résumé** : Banc d'étude (dump empirique, **aucune assertion**) des formes AST consommées par le moteur : lambdas (implicites et typées), chaînage d'appels, allocations. Le source de démonstration est résolu par `resolveUnit`, puis traversé par une traversée RÉSILIENTE avec un visiteur qui imprime, pour chaque nœud observé, ses champs observables (positions sources, bindings, arités). Utile comme grammaire d'inspection pour la réécriture : ce que l'ancien moteur lisait sur ces formes.

## Comportements

### dump — impression des formes lambda/envoi/allocation
- **Préconditions / entrée** : moteur sans racines sources ni jars, compliance « 17 » ; source `/demo/Main.java` contenant : lambda à deux arguments implicites `(x, y) -> {…}` passée à un `BiConsumer`, lambda un-argument `s -> …` passée à `forEach`, lambda TYPÉE `(String a, Integer b) -> { }` sur une variable `BiConsumer`, chaînage `make().append("a").append("b")`, allocations `new Main().run(items, fn)` et `new Widget(1, "x")` (constructeur à 2 arguments).
- **Comportement attendu** : `resolveUnit` rend une unité compilée (avec scope) ; la traversée imprime : `[LAMBDA]` — drapeau d'élision des types, nombre d'arguments, et par argument nom/type source/`sourceStart`/`sourceEnd`/binding (avec type lisible) ; `[SEND]` — sélecteur, classe du récepteur, type résolu du récepteur, `nameSourcePosition` (démélée en début/fin 32 bits), fin du récepteur et lignes correspondantes ; `[ALLOC]` — classe du nœud, binding lisible, drapeau constructeur, classe déclarante, nombre d'arguments, classe du premier argument ; plus les positions de séparateurs de lignes et le calcul inverse offset→ligne.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Test d'exploration stdout sans oracle : sa valeur documentaire est la liste des champs effectivement consommés sur chaque forme (positions de NOM dans les envois, élision d'arguments des lambdas, bindings d'allocation) — à transformer en assertions ciblées si ces formes redeviennent critiques.
- La traversée (`EcjAst.traverseResilient`) est supposée ne pas planter sur un AST incomplet/en recovery — propriété à conserver côté lecture d'arbre.
- Le décodage de `nameSourcePosition` (start dans les 32 bits de poids fort, end dans les poids faibles) est un détail de format à ne pas perdre lors d'une réécriture des positions de noms.
- Aucune classe de production n'est chargée au-delà du moteur : le banc est autoportant (aucune dépendance android.jar).

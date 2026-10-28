# Fiche 052 — FoldingRangeFacadeTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/FoldingRangeFacadeTest.java`.
> **Domaine** : folding
> **Résumé** : Tests BOUT-EN-BOUT des handlers à travers la façade LSP réelle `JavaTextDocumentService` : `foldingRange` (imports repliés par défaut avec texte replié, javadoc/commentaires, corps des accolades — avec précision CARACTÈRE du dépliage) et `inlayHint` (hint de type `var` inféré par bindings). Fixture : android.jar + compliance « 17 », documents posés sur disque et ouverts par `didOpen`.

## Comportements

### foldingRange_importsBlockCommentAndBodies — folds imports/commentaires/corps
- **Préconditions / entrée** : source avec 2 imports consécutifs, une javadoc de classe, une classe et une méthode ; requête `foldingRange` sur le document ouvert.
- **Comportement attendu** : au moins 4 folds rendues ; fold des imports : kind « imports », de la ligne 2 à la ligne 3, texte replié « import ... » ; fold de la javadoc : kind « comment », ligne de départ 5 ; fold du corps de `run()` : kind « block », de la ligne 9 à la ligne 11, DÉMARRANT juste après le « { » (colonne 16) et se terminant SUR la « } » (colonne 4).
- **Bornes** : `>= 4` folds ; imports lignes 2→3 ; commentaire ligne 5 ; méthode lignes 9→11, startCharacter 16, endCharacter 4.
- **Statut** : actif (hypothèse `@BeforeAll` : android.jar absent → classe ignorée)

### inlayHint_varTypeFromBindings — hint de type var par bindings
- **Préconditions / entrée** : source avec `var s = String.valueOf(1);` dans un corps de méthode ; requête `inlayHint` couvrant tout le document (plage (0,0)–(20,0)).
- **Comportement attendu** : un hint « : String » est rendu, de kind Type, positionné ligne 4 caractère 13 — en fin du nom `s`, juste avant le « = » (l'inférence du type d'un appel de méthode passe par les bindings).
- **Bornes** : position ligne 4, caractère 13 ; kind Type ; libellé « : String ».
- **Statut** : actif (hypothèse android.jar présent)

## Notes pour la réécriture
- La précision CARACTÈRE des folds est contractuelle : le dépliage démarre juste APRÈS l'accolade ouvrante et se termine SUR la fermante — pas des débuts/fins de ligne approximatifs.
- Le texte replié du bloc d'imports est « import ... » (literal exact observé côté client).
- Le hint `var` s'ancre entre le nom de variable et le « = » : la colonne est fin du nom + 1.
- Les deux handlers sont éprouvés via la façade complète (didOpen puis requête asynchrone `.get()`) : aucune moquerie — même gabarit que les autres tests de façade du lot.

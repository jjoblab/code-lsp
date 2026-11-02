# Fiche 018 — EcjHierarchiesTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjHierarchiesTest.java`.
> **Domaine** : hierarchies
> **Résumé** : Tests du lot S2-b (moteur) : hiérarchies de TYPES et d'APPELS sur un projet multi-fichiers réel écrit dans un répertoire temporaire. Fixture : `demo.Base` (méthodes `utilise(int)`, `log(int)`, `fournis()`), `demo.Contrat` (interface `fournis()`), `demo.Enfant extends Base implements Contrat`, `demo.Appelant` (appelle `b.utilise(3)` et `b.utilise(4)` depuis `declenche()`). Invariants verrouillés : les éléments sont localisés dans les SOURCES du projet (jamais d'URI factice pour un type binaire — règle D3), les appels entrants sont groupés par méthode appelante, les appels sortants sont résolus vers des déclarations source (cibles binaires écartées).

## Comportements

### elementTypeAuCaretSurUneReference — type au caret sur une référence
- **Préconditions / entrée** : moteur de navigation sur le projet (racines sources = répertoire temporaire, conformité « 17 ») ; caret sur « Base » dans « extends Base » de `demo/Enfant.java`.
- **Comportement attendu** : une cible de hiérarchie est retournée ; nom simple « Base » ; chemin se terminant par `demo/Base.java` (SA source, pas d'URI factice) ; la plage « nom » vise le jeton « Base » dans la source de Base ; la plage « déclaration » couvre la classe (contient « class Base »).
- **Bornes** : —
- **Statut** : actif

### supertypesDirectsClasseEtInterface — superclasse + interface directs
- **Préconditions / entrée** : caret sur le nom « Enfant » de sa déclaration.
- **Comportement attendu** : exactement 2 supertypes directs : « Base » (superclasse) et « Contrat » (interface) ; tous deux localisés dans les sources du projet (chemin contenant « demo »).
- **Bornes** : 2 supertypes exactement.
- **Statut** : actif

### sousTypesViaBalayageProjet — sous-types par balayage des racines
- **Préconditions / entrée** : caret sur le nom « Base » de sa déclaration.
- **Comportement attendu** : exactement 1 sous-type : « Enfant », dont le chemin se termine par `Enfant.java` (balayage des racines sources du projet).
- **Bornes** : 1 sous-type exactement.
- **Statut** : actif

### typeBinaireAuCaretNullPasDUriFactice — pas de type projet au caret → null
- **Préconditions / entrée** : caret sur « @Override » (mot non-type) dans `Enfant.java`.
- **Comportement attendu** : la cible de hiérarchie de types est NULL — jamais une URI factice pour un élément binaire ou non-type.
- **Bornes** : —
- **Statut** : actif

### elementMethodeAuCaretSurUnAppel — méthode au caret sur un appel
- **Préconditions / entrée** : caret sur « utilise » de « b.utilise(3) » dans `Appelant.java`.
- **Comportement attendu** : cible résolue et localisée ; nom « utilise » ; la déclaration vit dans `Base.java` ; la plage nom vise le jeton « utilise » dans la source de Base.
- **Bornes** : —
- **Statut** : actif

### appelsEntrantsGroupesParAppelant — appelants entrants groupés avec leurs sites
- **Préconditions / entrée** : caret sur le nom de la déclaration de `Base.utilise`.
- **Comportement attendu** : exactement 1 appelant : méthode « declenche » dans `Appelant.java`, portant 2 sites d'appel ; chaque site extrait de la source commence par « b.utilise ».
- **Bornes** : 1 appelant, 2 sites d'appel.
- **Statut** : actif

### appelsSortantsResolusVersSourcesDuProjet — sortants résolus en source (D3)
- **Préconditions / entrée** : caret sur le nom « declenche » de sa déclaration.
- **Comportement attendu** : exactement 1 cible sortante : « utilise », classe propriétaire « demo.Base », 2 sites d'appel ; les cibles binaires (p. ex. `Object.<init>`) sont écartées.
- **Bornes** : 1 cible, 2 sites ; 0 cible binaire.
- **Statut** : actif

### pasDeMethodeAuCaretListesVides — pas de méthode au caret → liste vide
- **Préconditions / entrée** : caret sur un littéral (« return 1; », sur « tu ») dans `Base.java`.
- **Comportement attendu** : la liste des appels entrants est VIDE (pas de méthode au caret).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Le moteur de navigation se construit AU-DESSUS du moteur de compilation (racines sources réelles sur disque) — la hiérarchie exige le projet multi-fichiers, pas l'unité isolée.
- Règle D3 (implicite aux tests, issue de la conception) : ne JAMAIS fabriquer d'URI pour un élément binaire — soit la source projet, soit null/liste vide.
- Le groupement entrants « par méthode appelante » (une entrée par appelant, sites multiples) est la forme de sortie attendue par le client hiérarchie d'appels.
- Les plages retournées (nom vs déclaration) sont deux informations distinctes de la cible : le nom vise le jeton, la déclaration couvre la construction entière.

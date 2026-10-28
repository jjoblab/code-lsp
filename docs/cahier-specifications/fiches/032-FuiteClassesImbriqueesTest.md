# Fiche 032 — FuiteClassesImbriqueesTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/FuiteClassesImbriqueesTest.java`.
> **Domaine** : index-classpath (fiche mixte : filtrage de l'index disque observé côté complétion/quick-fix)
> **Résumé** : Régression v1.9.1 : les classes internes binaires (« R$style », « R$id », « R$string »…) ne doivent PLUS fuiter dans les complétions servies par l'INDEX DISQUE PRÊT (retour propriétaire : le popup proposait `import com.example.monip.R$style;` et des FQN bruts impossibles à écrire en Java). Un index de test (`IndexNoms` PRÊT) mime l'index de production AVANT correctif : il indexe les imbriquées par segment après dollar (clé « style » → FQCN `com.example.monip.R$style`) et rend les entrées brutes. Les TROIS chemins consommateurs sont éprouvés : complétion d'import, complétion de types par préfixe, quick-fix d'import. Moteur sur racine source réelle + android.jar + R.jar (construit par le helper partagé `PopupSpontaneReproTestHelper`) ; le lot se skippe si android.jar est absent.

## Comportements

### importApresPaquetProjetNeProposePasLesImbriquees — complétion d'import sans imbriquées
- **Préconditions / entrée** : source « package com.example.monip; import com.example.monip.| » (caret en fin de qualified) ; index PRÊT qui rend `typesDansPaquet("com.example.monip")` = [R, R$style, R$id, R$string] (comportement AVANT correctif).
- **Comportement attendu** : la complétion propose le type de premier niveau « R » ; AUCUN label ne contient « $ ».
- **Bornes** : 0 label contenant « $ » ; « R » présent.
- **Statut** : actif — ignoré si android.jar absent (hypothèse visible `Assumptions.assumeTrue` dans le @BeforeAll)

### completionTypeParPrefixeNeProposePasLesImbriquees — findTypesByPrefix sans imbriquées
- **Préconditions / entrée** : requête `findTypesByPrefix("style", 25)` sur l'index de types du moteur (la clé « style » porte R$style dans l'index brut), puis `findTypesByPrefix("R", 25)`.
- **Comportement attendu** : aucun FQN retourné ne contient « $ » ; le rappel normal reste servi : « R » donne le FQN exact « com.example.monip.R ».
- **Bornes** : limite de requête = 25 ; 0 FQN contenant « $ ».
- **Statut** : actif — ignoré si android.jar absent (hypothèse visible)

### quickFixImportNeProposePasLesImbriquees — quick-fix d'import sans imbriquées
- **Préconditions / entrée** : requêtes `fqnsForSimpleName("style")` puis `fqnsForSimpleName("R")` sur l'index.
- **Comportement attendu** : aucun FQN pour « style » ne contient « $ » (le quick-fix « Importer style » n'offre pas le FQN binaire brut) ; « R » reste importable (« com.example.monip.R » présent).
- **Bornes** : 0 FQN contenant « $ ».
- **Statut** : actif — ignoré si android.jar absent (hypothèse visible)

### completionAnnotationApresArobase — `@` déclencheur : réponse honnête
- **Préconditions / entrée** : source avec « @ » seul au-dessus d'une méthode ; caret juste après l'arobase (déclencheur annoncé v1.9.1).
- **Comportement attendu** : la complétion n'est JAMAIS null — le contexte annotation doit au moins se résoudre (TYPE_REFERENCE ou NAME_REFERENCE) ; la liste peut être vide à préfixe vide (types filtrés), le serveur répond honnêtement au client.
- **Bornes** : —
- **Statut** : actif — ignoré si android.jar absent (hypothèse visible)

## Notes pour la réécriture
- Le filtre « $ » doit s'appliquer aux TROIS surfaces de l'index : complétion d'import, `findTypesByPrefix`, `fqnsForSimpleName` — les imbriquées brutes (FQN binaire avec `$`) ne doivent jamais atteindre le client ; le type de premier niveau (`R`) doit rester proposé.
- L'index d'essai rend EXPRÈS les entrées brutes (comportement pré-correctif) : c'est le MOTEUR qui doit filtrer en aval — invariant à conserver même avec un index imparfait.
- Le contrat `IndexNoms` simulé comprend : estPret (true), ajouterObservateurPret (exécution immédiate de l'observateur), nomsParPrefixe, estPaquet, sousPaquetsDirects, typesDansPaquet, membresStatiques, jarDe — interface à reproduire pour les tests de la réécriture.
- Dépendance environnementale : android.jar recherché à chemins fixes (`/home/z/android-sdk/platforms/android-37.2/android.jar` ou `/home/z/tools/…`) ; absent → tout le lot est ignoré (skip visible).
- La complétion d'annotation « @ » est best-effort : jamais null, éventuellement vide — l'honnêteté protocolaire prime.

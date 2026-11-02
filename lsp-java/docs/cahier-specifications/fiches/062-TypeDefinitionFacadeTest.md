# Fiche 062 — TypeDefinitionFacadeTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/TypeDefinitionFacadeTest.java`.
> **Domaine** : navigation
> **Résumé** : Tests E2E à travers la façade LSP réelle : `textDocument/typeDefinition` (le type du symbole au caret — entrée « GO TO » du menu contextuel unifié) et les offsets de `textDocument/definition` sur les gros fichiers réduits par SourceFocuser (> 2000 caractères) — les plages rendues doivent être en coordonnées de la source ORIGINALE, pas de la source réduite. Fixtures : `Holder` (classe interne `Item` et variable locale), `Box` (référence de type sur soi), `UsesString` (type binaire java.lang.String), `Counts` (type de base int), `BigFile` (générée : 120 lignes de remplissage dans `big()`, au-delà du seuil de réduction). Hypothèse visible dans `@BeforeAll` : `android.jar` présent, sinon classe ignorée ; environnement racine src/demo + android.jar, Java 17, façade construite directement.

## Comportements

### typeDefinition_localVariableResolvesItsDeclaredType — variable locale → déclaration de son type
- **Préconditions / entrée** : `Holder.java` ouvert (contient une classe interne `Item` et la locale `Item it = new Item();`) ; caret sur « it » (la variable locale, dans `it.name`).
- **Comportement attendu** : exactement 1 cible : la déclaration de `Item` — ligne et colonne exactes du nom « Item » de la classe interne.
- **Bornes** : 1 cible exactement ; position du nom vérifiée ligne + colonne.
- **Statut** : actif

### typeDeclaration_onTypeReferenceResolvesItself — référence de type → sa propre déclaration
- **Préconditions / entrée** : `Box.java` ouvert ; caret sur « Box » dans « Box b ».
- **Comportement attendu** : exactement 1 cible : la déclaration de `Box`, à la position exacte du nom.
- **Bornes** : 1 cible exactement.
- **Statut** : actif

### typeDeclaration_binaryTypeYieldsNoTarget — type binaire → URI synthétique décompilée
- **Préconditions / entrée** : `UsesString.java` ouvert ; caret sur « s » (variable de type `String`, BINAIRE du classpath JDK).
- **Comportement attendu** : exactement 1 Location dont l'URI est l'URI synthétique « jdt://decompiled/java/lang/String.java » et dont la plage est VIDE (0:0–0:0) — câble « GO TO sur cible binaire » : le client appelle ensuite `textDocument/decompiledSource(fqcn)` pour ouvrir le buffer read-only, caret en haut.
- **Bornes** : 1 cible exactement ; URI exacte « jdt://decompiled/java/lang/String.java » ; plage 0:0–0:0 exactement.
- **Statut** : actif

### typeDeclaration_intLiteralYieldsNoTarget — type de base → aucune cible
- **Préconditions / entrée** : `Counts.java` ouvert ; caret sur « n » (variable de type `int`).
- **Comportement attendu** : liste VIDE (un type de base n'a pas de déclaration).
- **Bornes** : —
- **Statut** : actif

### definition_afterFocusedMethod_landsOnExactOriginalOffset — offsets sur source réduite
- **Préconditions / entrée** : `BigFile.java` — fixture générée avec `caller()` qui appelle `big()`, et `big()` remplie de 120 lignes « int filler<i> = <i>; » ; la source fait plus de 2000 caractères (assertion du test) ; caret sur « big() » dans `caller()` ; requête `definition`.
- **Comportement attendu** : au moins 1 cible ; la première désigne le NOM « big » de la déclaration à la position EXACTE de la source ORIGINALE (ligne et colonne) — pas en coordonnées de la source réduite (décalées).
- **Bornes** : fixture > 2000 caractères (seuil de réduction) ; 120 lignes de remplissage ; position exacte verrouillée.
- **Statut** : actif

## Notes pour la réécriture
- Le câble binaire est complet : type binaire → `Location` à URI `jdt://decompiled/<chemin-fqcn>.java` avec plage vide, puis le client récupère la source via `decompiledSource(fqcn)` (attachée ou stub décompilé) et ouvre un buffer read-only.
- Le seuil de réduction de source (SourceFocuser) est > 2000 caractères : TOUTES les plages rendues (definition, etc.) doivent être remappées vers les coordonnées de la source originale.
- Types de base (int, etc.) → liste vide, jamais d'URI synthétique.
- La cible est toujours le NOM de la déclaration (pas la clause entière), position vérifiée ligne + colonne.
- Hypothèse d'environnement android.jar (ANDROID_HOME/ANDROID_SDK_ROOT/`/home/z/tools/android-sdk`) — sinon classe ignorée à l'exécution.

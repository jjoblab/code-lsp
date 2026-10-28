# Fiche 005 — BinaryDefinitionFallbackTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/BinaryDefinitionFallbackTest.java`.
> **Domaine** : navigation
> **Résumé** : Câblage LSP du fallback « GO TO definition / type definition sur cible binaire » : résolution du FQN au caret (`fqcnAt` : type déclarant pour `targetType=false` / definition, type du symbole pour `targetType=true` / typeDefinition) et identification binaire (`isBinaryType`) pour des références à des types du JDK (`java.util.HashMap`, `java.lang.String`, `java.lang.Thread`) — le serveur renvoie alors une Location `jdt://decompiled/...`. La plupart des tests ne tournent que si le JDK home contient `lib/src.zip` ou `lib/modules` (Temurin 17 en CI) — condition `@EnabledIf("hasTestJdk")`.

## Comportements

### fqcnAt_methodCallOnJdkType_returnsDeclaringClassFqn — FQN de la classe déclarante d'un appel
- **Préconditions / entrée** : source avec `HashMap<String, String> m = new HashMap<>(); m.put("a", "b");` ; caret sur `put` (offset de `indexOf("put(")`) ; `targetType=false`.
- **Comportement attendu** : FQN non null ; accepté : `java.util.HashMap` OU `java.util.AbstractMap` OU tout FQN commençant par `java.util.` (le binding résolu pointe sur HashMap, override de AbstractMap mis à part) ; `isBinaryType(fqcn)` = true.
- **Bornes** : —
- **Statut** : actif (exécuté seulement si JDK de test présent — `@EnabledIf` ; sinon ignoré)

### fqcnAt_variableOfTypeJdk_returnsTypeFqn — FQN du type d'une variable
- **Préconditions / entrée** : même source ; caret sur la variable `m` (offset de `indexOf("m.put")`) ; `targetType=true`.
- **Comportement attendu** : FQN exactement `java.util.HashMap` ; `isBinaryType` = true.
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

### fqcnAt_superclassReference_returnsTypeFqn — FQN d'une référence superclass
- **Préconditions / entrée** : `class Demo extends Thread` ; caret sur `Thread`.
- **Comportement attendu** : `targetType=true` → `java.lang.Thread` ; `targetType=false` → `java.lang.Thread` également (pour une référence de type, la classe déclarante EST le type lui-même) ; `isBinaryType("java.lang.Thread")` = true.
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

### fqcnAt_sourceType_isNotBinary — type source : pas de fallback binaire
- **Préconditions / entrée** : caret sur `Demo` de `Demo instance;` (type déclaré dans le fichier courant).
- **Comportement attendu** : FQN `demo.Demo` non null ; le fallback binaire ne se déclenche PAS pour un type source (`findDefinition` JDT-first l'a déjà trouvé — c'est le point du test).
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

### fqcnAt_caretInWhitespace_returnsNull — caret sans binding
- **Préconditions / entrée** : caret au milieu d'un commentaire (`// un commentaire`).
- **Comportement attendu** : `fqcnAt` retourne null (défensif).
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

### fqcnAt_emptySource_returnsNull — source vide / offset hors plage
- **Préconditions / entrée** : source vide, offsets 0 et 100 ; moteur construit sans JDK explicite.
- **Comportement attendu** : null dans les deux cas.
- **Bornes** : —
- **Statut** : actif

### fqcnAt_baseType_returnsNull — type de base
- **Préconditions / entrée** : caret sur `int` de `int x = 42;` ; `targetType=true`.
- **Comportement attendu** : null (un type de base n'a pas de déclaration à visiter — filtré).
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

## Notes pour la réécriture
- `fqcnAt` est résilient : n'importe quel offset entre `sourceStart` et `sourceEnd` du nœud fonctionne (assertion souple sur `put`).
- Tolérance explicite sur la classe déclarante d'un override (HashMap vs AbstractMap vs `java.util.*`) — la réécriture devra documenter et figer ce choix.
- JDK de test : variable d'env `JO_TEST_JDK_HOME`, sinon candidats `/home/z/tools/jdk-17.0.20.1+1`, `/home/z/my-project/tools/jdk-17.0.20.1+1`, `/usr/lib/jvm/java-17-openjdk-amd64`, `java-21-openjdk-amd64`, puis `java.home` (en remontant d'un niveau si sous-dossier `jre/`) — préférence à un JDK avec `lib/src.zip`, sinon `lib/modules`.
- Les types source (déclarés dans le fichier) ne passent jamais par le fallback binaire — invariant de routage definition-first.
- L'identification binaire conditionne l'URI `jdt://decompiled/...` côté serveur LSP (non testée ici directement).

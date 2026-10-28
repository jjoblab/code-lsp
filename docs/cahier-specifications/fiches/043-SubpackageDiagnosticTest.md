# Fiche 043 — SubpackageDiagnosticTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/SubpackageDiagnosticTest.java`.
> **Domaine** : completion
> **Résumé** : Diagnostic (caractérisation) du filtre subpackage « ni plus ni moins » : dump EXACT de ce que propose le moteur pour des contextes import typés — `import android.`, `import android.c`, `import j`, `import java.util.f`, `import java.util.` — afin de comparer aux règles d'or de `packageChildren` (projet tiers de référence) : enfants directs uniquement, dédoublonnés par segment, préfixe vide liste quand même, types directs sans auto-import, insertion du segment simple. **Aucune assertion** : chaque test imprime les items (kind, label, insertText, detail, relevance, autoImportFqn). Fixture : android.jar (API 34/35 selon disponibilité), racine `src/com/example/e`, compliance « 17 ».

## Comportements

### dumpImportAndroid — dump du contexte `import android.`
- **Préconditions / entrée** : source avec `import android.` et caret en fin du préfixe tapé (préfixe VIDE après le point).
- **Comportement attendu** : la complétion rend des items imprimés (kind/label/insert/detail/relevance/autoImport) — observation brute, sans oracle.
- **Bornes** : affichage plafonné à 120 items par dump.
- **Statut** : actif (hypothèse `@BeforeAll` : android.jar absent → classe ignorée)

### dumpImportAndroidC — dump du contexte `import android.c`
- **Préconditions / entrée** : caret après `import android.c` (préfixe « c »).
- **Comportement attendu** : dump des items — sous-paquets filtrés par préfixe et types directs filtrés.
- **Bornes** : 120 items affichés max.
- **Statut** : actif (hypothèse android.jar présent)

### dumpImportJ — dump du contexte `import j`
- **Préconditions / entrée** : caret après `import j`.
- **Comportement attendu** : dump des items (racines du classpath commençant par « j » — java/javax…).
- **Bornes** : 120 items affichés max.
- **Statut** : actif (hypothèse android.jar présent)

### dumpImportJavaUtilF — dump du contexte `import java.util.f`
- **Préconditions / entrée** : caret après `import java.util.f`.
- **Comportement attendu** : dump des items (enfants de java.util filtrés par « f » — p. ex. function).
- **Bornes** : 120 items affichés max.
- **Statut** : actif (hypothèse android.jar présent)

### dumpImportJavaUtilDot — dump du contexte `import java.util.`
- **Préconditions / entrée** : caret après `import java.util.` (préfixe vide sur un paquet profond).
- **Comportement attendu** : dump des items (avalanche des enfants directs de java.util).
- **Bornes** : 120 items affichés max.
- **Statut** : actif (hypothèse android.jar présent)

## Notes pour la réécriture
- Banc d'exploration SANS assertion : la spécification exécutable des mêmes comportements vit dans `SubpackageGoldenRulesTest` (fiche 044) — c'est là qu'est la valeur contractuelle ; conserver celui-ci comme harnais d'inspection manuelle.
- Le caret est toujours en fin de la tête d'import déjà tapée ; la source est un squelette minimal (paquet + import + classe vide).
- La trace imprimée inclut `relevance` et `autoImportFqn`, champs absents des règles d'or mais utiles au diagnostic.

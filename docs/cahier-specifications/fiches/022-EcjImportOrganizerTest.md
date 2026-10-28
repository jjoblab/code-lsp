# Fiche 022 — EcjImportOrganizerTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjImportOrganizerTest.java`.
> **Domaine** : imports
> **Résumé** : Tests de l'organisateur d'imports (`EcjImportOrganizer`) : identification des FQN à importer depuis les bindings (types qualifiés utilisés, superclasse), règles d'exemption (java.lang, même paquet, on-demand existant), remplacement du bloc d'imports existant par un bloc trié, insertion après le `package` quand il n'y a pas d'imports, et robustesse au code cassé (aucun faux import). L'organisateur est construit sur un moteur à racines/classpath vides (« 17 ») — les types java.* résolvent via le jrt de test.

## Comportements

### javaLangAndSamePackageNeedNoImport — java.lang et même paquet : aucun import
- **Préconditions / entrée** : classe `demo.Main` n'utilisant que `String` (java.lang) et des types du même paquet.
- **Comportement attendu** : résultat non null ; liste d'imports VIDE.
- **Bornes** : 0 import.
- **Statut** : actif

### javaUtilTypeNeedsImport — type java.util identifié
- **Préconditions / entrée** : méthode retournant `java.util.List<String>` (usage QUALIFIÉ).
- **Comportement attendu** : « java.util.List » figure dans les imports identifiés (l'import n'est pas strictement nécessaire pour un usage qualifié, mais l'organisateur l'identifie pour permettre le dé-qualification).
- **Bornes** : —
- **Statut** : actif

### onDemandImportCoversPackage — l'on-demand couvre le paquet et est préservé
- **Préconditions / entrée** : source avec « import java.util.*; » et usages de `List`/`ArrayList` non qualifiés.
- **Comportement attendu** : la liste d'imports est EXACTEMENT « java.util.* » (l'on-demand est préservé, aucun import explicite ajouté) ; le bloc d'imports est « import java.util.*;\n ».
- **Bornes** : 1 élément dans la liste.
- **Statut** : actif

### unqualifiedTypeFromOtherPackageIsImported — type d'un autre paquet importé
- **Préconditions / entrée** : usages de `java.math.BigDecimal` (qualifiés, paquet ≠ demo).
- **Comportement attendu** : « java.math.BigDecimal » présent dans les imports.
- **Bornes** : —
- **Statut** : actif

### replacesExistingBlockSorted — remplacement du bloc existant, trié
- **Préconditions / entrée** : source avec imports existants (« java.math.BigDecimal », « java.util.List ») et usages des deux FQN.
- **Comportement attendu** : 2 imports identifiés ; le bloc proposé commence par « import java.math.BigDecimal;\nimport java.util.List; » (ordre alphabétique) ; la plage de remplacement [replaceStart, replaceEnd] couvre les imports existants.
- **Bornes** : 2 imports.
- **Statut** : actif

### insertionAfterPackageWhenNoImports — insertion juste après le package
- **Préconditions / entrée** : source sans bloc d'imports, utilisant `java.math.BigDecimal`.
- **Comportement attendu** : le point d'insertion (replaceStart) est situé après « package demo; » et avant « public class ».
- **Bornes** : —
- **Statut** : actif

### brokenCodeYieldsNoFalseImports — code cassé : aucun faux import
- **Préconditions / entrée** : source avec champ de type `UndefinedThing` non résolu et initialiseur non résolu.
- **Comportement attendu** : aucune exception ; si un résultat est retourné, aucun FQN contenant « Undefined » n'apparaît (les bindings non résolus sont ignorés).
- **Bornes** : —
- **Statut** : actif

### superclassFromOtherPackageIsImported — la superclasse est importée
- **Préconditions / entrée** : « public class Main extends java.util.TimerTask ».
- **Comportement attendu** : « java.util.TimerTask » présent dans les imports.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Sortie structurée attendue : liste d'imports (FQN), bloc d'imports formaté (une ligne « import <fqn>;\n » par entrée, tri alphabétique), plage de remplacement dans la source originale.
- L'on-demand (« x.* ») n'est jamais décomposé : il couvre le paquet et reste le seul élément du bloc.
- Exemptions : java.lang et même paquet — jamais d'import explicite pour eux.
- La superclasse (et donc a priori les positions structurelles : extends/implements) doit être balayée comme usage de type.
- Tolérance au code cassé : ne pas lever, ne pas inventer d'import sur un binding non résolu.

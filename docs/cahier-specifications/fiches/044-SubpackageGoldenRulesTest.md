# Fiche 044 — SubpackageGoldenRulesTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/SubpackageGoldenRulesTest.java`.
> **Domaine** : completion
> **Résumé** : Les SIX RÈGLES D'OR du filtre subpackage (portage de `CandidateCollector.packageChildren` du projet tiers de référence), vérifiées sur le contexte exact du bug device (`import android.`) : (1) enfants DIRECTS uniquement, (2) dédoublonnage par segment, (3) préfixe vide liste quand même les sous-paquets, (4) types DIRECTEMENT dans le paquet — ni plus ni moins — sans auto-import, (5) insertion du segment simple ; (6) exclusions module JVM (N/A : CodeIDE est exclusivement Android). Éprouve aussi le matching gradué (préfixe insensible à la casse) et l'application des mêmes règles au contexte PACKAGE_REFERENCE hors import. Fixture : android.jar (API 34/35), racine `src/com/example/e`, compliance « 17 ».

## Comportements

### rule1_directChildrenOnly_neverDeepPackages — enfants directs uniquement
- **Préconditions / entrée** : complétion sur `import android.` (préfixe vide).
- **Comportement attendu** : chaque label de PACKAGE est un SEGMENT simple (aucun ne contient de point) ; les petits-fils connus (`content.pm`, `os.storage`) sont ABSENTS ; les enfants directs connus (`content`, `os`, `view`, `widget`) sont PRÉSENTS.
- **Bornes** : —
- **Statut** : actif (hypothèse `@BeforeAll` : android.jar absent → classe ignorée)

### rule4_typesDirectlyInPackage_manifestAndR_deterministic — types directs, déterministes
- **Préconditions / entrée** : 20 appels consécutifs de complétion sur `import android.`.
- **Comportement attendu** : à CHAQUE round, les items de kind CLASS sont exactement `Manifest` et `R` (les 2 types directement posés dans `android` dans android.jar) ; les types des sous-paquets (`Bundle`, `Activity`) ne remontent jamais.
- **Bornes** : 20 rounds ; ensemble de types exactement `{Manifest, R}`.
- **Statut** : actif (hypothèse android.jar présent)

### rule4_typesDirect_neverAutoImport — jamais d'auto-import
- **Préconditions / entrée** : complétion sur `import android.`.
- **Comportement attendu** : AUCUN item ne porte d'`autoImportFqn` (le chemin de paquet est déjà tapé avant le caret — proposer un import serait redondant).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

### rule2_dedupBySegment — dédoublonnage par segment
- **Préconditions / entrée** : complétion sur `import android.`.
- **Comportement attendu** : chaque segment de sous-paquet apparaît UNE seule fois (des dizaines de classes vivent dans `android.content.*` → une seule entrée `content`).
- **Bornes** : « content » exactement 1 fois.
- **Statut** : actif (hypothèse android.jar présent)

### rule3_emptyPrefixStillListsSubPackages — préfixe vide = avalanche
- **Préconditions / entrée** : complétion sur `import android.` (préfixe vide après le point).
- **Comportement attendu** : les sous-paquets sont listés quand même (le préfixe vide ne coupe pas l'avalanche).
- **Bornes** : `>= 40` items de kind PACKAGE (47 sous-paquets réels sous `android` dans android.jar).
- **Statut** : actif (hypothèse android.jar présent)

### rule3_prefixFiltersSegments — filtrage par préfixe
- **Préconditions / entrée** : complétion sur `import android.c` puis `import android.M`.
- **Comportement attendu** : avec « c » : les segments `companion`, `content`, `credentials` sont proposés et TOUS les labels de paquet démarrent par « c »/« C » ; avec « M » : le type direct `Manifest` est proposé et `R` ne l'est pas.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

### rule5_insertSimpleSegment — insertion du segment simple
- **Préconditions / entrée** : complétion sur `import android.`.
- **Comportement attendu** : AUCUN `insertText` ne contient de point (le texte avant le caret contient déjà `android.`) ; le DETAIL d'un item PACKAGE porte le FQN complet (démarre par `android.`).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

### graduatedMatching_onSegments — matching gradué insensible à la casse
- **Préconditions / entrée** : complétion sur `import android.Con`.
- **Comportement attendu** : le paquet `content` est proposé (préfixe insensible à la casse : « Con » matche « content »).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

### packageReferenceOutsideImport_sameGoldenRules — hors import, mêmes règles
- **Préconditions / entrée** : source avec `Object o = android.widget.` dans un corps de méthode ; caret après le point final (contexte PACKAGE_REFERENCE).
- **Comportement attendu** : les types DIRECTS d'android.widget (`TextView`, `Button`) sont proposés ; pas de paquet profond (`content.pm`) ; les insertions restent des segments simples (sans point).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

## Notes pour la réécriture
- Le cœur du contrat : « ni plus ni moins » — les types proposés depuis un chemin de paquet déjà tapé sont EXACTEMENT les types directs du paquet (ensemble fini vérifié), et les paquets sont des SEGMENTS simples dédoublonnés.
- La déterministe sur 20 rounds protège contre un index non stabilisé (cache/asynchronisme) : chaque round doit rendre le même ensemble.
- Insérer le segment simple mais détailler le FQN complet : la réécriture doit distinguer label/insert (segment) et detail (FQN) pour les paquets.
- Le filtrage par préfixe s'applique aussi bien aux segments de paquets qu'aux types directs ; le préfixe vide ne supprime PAS la liste.
- La règle 6 (exclusions de modules JVM) est documentée N/A pour CodeIDE (exclusivement Android) — à réévaluer si la réécriture vise d'autres cibles.

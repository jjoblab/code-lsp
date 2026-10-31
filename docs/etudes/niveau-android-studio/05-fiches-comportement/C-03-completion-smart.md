# Fiche C-03 — Complétion smart : filtrage par type attendu

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §A2
> (mécanisme documenté + code public de la référence). Comportement décrit pour
> lui-même.
> **Domaine** : complétion
> **Résumé** : aux positions où un type attendu est calculable (affectation,
> argument d'appel, `return`, condition de ternaire, paramètre de lambda), les
> candidats **type-compatibles remontent** dans la liste ; les autres restent
> visibles mais descendent — **jamais cachés** (dégradation honnête, plafond intact).

## Comportements

### S1 — affectation
- **Préconditions / entrée** : `String s = |` avec des locaux `String nom`,
  `int age`, `Object o`.
- **Comportement attendu** : `nom` (et toute expression `String`) apparaît **avant**
  `age` et `o` ; `age` reste dans la liste (en bas).
- **Bornes** : `String s = o.|` → membres de `Object` retournant `String` en tête.

### S2 — argument d'appel
- **Entrée** : `java.util.List<String> l; l.add(|)` avec locaux `String a`,
  `Integer b`.
- **Attendu** : `a` en tête ; `b` présent mais derrière. Surcharge : l'union des
  types attendus des paramètres à cet indice.

### S3 — return
- **Entrée** : méthode `View build()` ; corps `return |` avec locaux `TextView tv`,
  `int x`.
- **Attendu** : `tv` en tête (sous-type accepté), `x` en bas.

### S4 — ternaire et lambda
- **Entrée** : ` CharSequence c = flag ? | ` ; `Runnable r = () -> |`.
- **Attendu** : expressions de type compatible en tête dans les deux cas.

### S5 — jamais de cachage
- **Entrée** : toute position S1–S4.
- **Attendu** : la liste contient toujours les candidats du genre courant filtrés
  par préfixe (le filtrage par type n'**ordonne que**), plafond honnête inchangé.

## Notes pour la réécriture
- Le type attendu est demandé **aux bindings ecj** (type du membre gauche /
  paramètre formel / type de retour) — aucune réimplémentation d'inférence
  (ADR 0026, A7).
- Complétion « chain » associée : sur `a.|` sans candidat direct type-compatible,
  proposer en seconde passe les méthodes de `a` retournant le type attendu
  (profondeur 2, budget d'items, asynchrone possible).

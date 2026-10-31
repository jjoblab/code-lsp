# Fiche C-04 — Complétion des typedefs `@IntDef`/`@StringDef`

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §B12
> (documentation officielle des annotations + comportement du contributeur de la
> référence vérifié dans son code public). Comportement décrit pour lui-même.
> **Domaine** : complétion / intégration Android
> **Résumé** : quand la position de complétion est un argument/une valeur dont le
> paramètre ou le champ est annoté `@IntDef({...})`/`@StringDef({...})` (ou
> référence des constantes), les **constantes autorisées** sont proposées **en tête**
> et les autres valeurs descendantes.

## Comportements

### T1 — argument annoté
- **Préconditions / entrée** :
  `void setMode(@IntDef({MODE_ON, MODE_OFF}) int mode);` ; appel `setMode(|)` ;
  constantes `MODE_ON`, `MODE_OFF`, `OTHER` visibles.
- **Comportement attendu** : `MODE_ON`/`MODE_OFF` apparaissent **avant** `OTHER` et
  avant toute autre variable `int` ; les constantes typedef sont proposées même si
  non importées (auto-import à l'insertion).
- **Bornes** : `flag=true` : les combinaisons `A | B` sont autorisées (pas de
  diagnostic de combinaison en v1).

### T2 — affectation à un champ annoté
- **Entrée** : champ `@StringDef({KEY_A, KEY_B}) String key;` ; `key = |`.
- **Attendu** : `KEY_A`/`KEY_B` en tête.

### T3 — cas de switch sur un typedef
- **Entrée** : `switch (mode) { case |` où `mode` est typedef.
- **Attendu** : constantes du typedef (le CASE_LABEL existant se nourrit du typedef
  en plus de l'enum).

### T4 — diagnostic associé (v2, après complétion)
- **Entrée** : `setMode(OTHER)` où `OTHER` hors typedef.
- **Attendu** : avertissement d'inspection (sévérité Warning, provenance
  inspection/lint) — **jamais** une erreur ecj.

## Notes pour la réécriture
- Tout est local : lire l'annotation sur le paramètre/champ **résolu** (bindings
  ecj) — aucune donnée globale.
- Priorité d'implémentation haute : effet « wow » maximal pour un coût minimal
  (G-017, ADR 0026).

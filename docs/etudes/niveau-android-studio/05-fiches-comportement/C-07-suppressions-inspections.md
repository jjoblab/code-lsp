# Fiche C-07 — Suppressions d'inspections (`@SuppressWarnings` / `//noinspection`)

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §A4
> (mécanisme vérifié dans le code public de la référence). Comportement décrit pour
> lui-même.
> **Domaine** : inspections
> **Résumé** : toute inspection (et tout contrôle lint maison) est supprimable
> localement par `@SuppressWarnings("<id>")` (classe/méthode/champ/paramètre) ou
> `//noinspection <id>` (instruction), avec des IDs **stables et documentés** ;
  les suppressions devenues inutiles sont signalées.

## Comportements

### W1 — suppression par annotation
- **Préconditions / entrée** : inspection `egalite-de-chaines` signalant
  `a == b` sur `String` ; méthode annotée `@SuppressWarnings("egalite-de-chaines")`.
- **Comportement attendu** : le diagnostic disparaît **dans le périmètre de
  l'annotation** (méthode), pas au-delà.
- **Bornes** : l'ID est celui de **notre** inspection ; les tokens javac
  (`unchecked`, `deprecation`, `rawtypes`, `serial`, `unused`) sont **aussi**
  acceptés (alignement documenté de la référence).

### W2 — suppression par commentaire
- **Entrée** : `//noinspection egalite-de-chaines` sur la ligne précédant
  l'instruction.
- **Attendu** : seule cette instruction est soulagée.

### W3 — @SuppressLint (lint)
- **Entrée** : `@SuppressLint("NewApi")` sur la méthode (contrôle C-05).
- **Attendu** : NewApi supprimé dans le périmètre ; `@SuppressLint("all")` tout
  supprime (contrôles lint seulement).

### W4 — suppression redondante
- **Entrée** : `@SuppressWarnings("x")` sans diagnostic `x` actif dans le périmètre.
- **Attendu** : hint « suppression inutile » avec fix de retrait (Weak Warning).

### W5 — jamais sur le rouge ecj
- **Entrée** : tentative de suppression d'une **erreur de compilation**.
- **Attendu** : **aucun effet** — le rouge n'a qu'une source (ADR 0013/0023) ;
  éventuellement un hint « cette annotation ne supprime pas les erreurs du
  compilateur ».

## Notes pour la réécriture
- Le registre des IDs vit avec le profil (ADR 0023) ; documentation générée
  (liste id → inspection → sévérité).

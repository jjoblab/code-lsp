# Fiche C-09 — Analyse de nullité intra-méthode (annotations + contrats)

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §A4
> (inspection de flux de nullité, annotations reconnues, discipline anti-FP de la
> référence). Comportement décrit pour lui-même.
> **Domaine** : inspections
> **Résumé** : une inspection de nullité **bornée à la méthode** (v1) lit les
> annotations `@Nullable`/`@NonNull` (androidx, org.jetbrains, javax) et les
> contrats simples, et signale : déréférencement possiblement null, valeur
> @Nullable retournée où @NonNull attendu, vérification redondante. Les
> avertissements « unsound » (possibles, non certains) sont **off par défaut**.

## Comportements

### X1 — déréférencement après test
- **Préconditions / entrée** : `@Nullable String n = f(); if (n == null) return;
  n.length();`.
- **Comportement attendu** : aucun diagnostic (le test domine le flux).
- **Bornes** : réaffectation de `n` après le test → l'analyse retombe
  (peut être null).

### X2 — retour @Nullable vers @NonNull
- **Entrée** : méthode annotée `@NonNull` dont un `return f();` rend un
  `@Nullable`.
- **Attendu** : avertissement à la ligne du `return` (pas à la signature).

### X3 — paramètre @NonNull appelé avec @Nullable
- **Entrée** : `g(@Nullable p)` ; appel `autre(p)` où `autre` exige `@NonNull`.
- **Attendu** : avertissement à l'argument.

### X4 — discipline anti-FP
- **Attendu** : par défaut, seuls les cas **certains** (démontrés par le flux) sont
  signalés ; l'option « unsound » (possibles) est désactivée par défaut et
  marquée expérimentale.
- **Bornes** : périmètre v1 = intra-méthode (pas d'interprocédural).

### X5 — annotations reconnues
- **Attendu** : `androidx.annotation.Nullable/NonNull` (priorité),
  `org.jetbrains.annotations`, `javax.annotation` (et `@Nullable` du SDK inféré si
  lisible) — mêmes règles de précédence que la référence (androidx d'abord).

## Notes pour la réécriture
- Le mini-DFA est **borné** (une méthode, pas d'interprocédural) — toute extension
  passe par amendement (ADR 0023 R3).
- P3 garde-fou : FP = 0 sur corpus réel, sinon l'inspection est sévitisée
  (Weak Warning) ou désactivée par profil.

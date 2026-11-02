# ADR 0116 — Compose : previews et analyses spécifiques

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K11 : « Que faut-il garder tel quel, que faut-il redessiner ? » La
capacité previews `@Preview @Composable` par SPI (C1 livré par
l'ancien moteur, 522 l.) est consommée par la carte « goutte d'eau » de
CodeIDE : modèle NEUTRE (nom, plage, paramètres tranchés dans la
source, corps réduit en arbre d'appels), preuve FQCN par les imports,
inlining borné des composables du projet (cycles typés, budgets
500 nœuds/profondeur 6), échecs typés.

## Sources consultées

- Cahier : fiche 051 (`SessionLangageKotlinPreviewsTest` — 16
  comportements, tous les gardes).
- `codeide-lsp` : `docs/LSP_KOTLIN.md` §5-C1, PREVIEW.md §4.4 de
  CodeIDE.
- Étude : `01-architectures-analyse-kotlin.md` §K11 (aucun équivalent
  amont embarquable).

## Options

**Option 1 — Conserver la conception à l'identique** (reprise voie C
ou réécriture cahier en main). **Option 2 — Analyses Compose riches**
(typage des modifier, diagnostics de composabilité) : exigent le typage
d'expression complet (ADR 0107) — horizon, pas première vague.
**Option 3 — Abandonner la capacité** : régresse une fonctionnalité en
service — écarté. **Option 4 — Ne rien changer.**

## Critères et mesures

- Fiche 051 : 16 comportements verrouillés (paramètres tranchés,
  joker/qualifié, ordre du document, lambda final, inlining disque,
  cycle CYCLE, budget BUDGET, paramétrés PARAMETRES, lignes
  d'annotation, fermeture).

## Décision proposée

**Option 1** : la conception C1 est gardée telle quelle — modèle neutre
par SPI, preuve FQCN, budgets, frontière rendu. Les analyses Compose
spécifiques (typage de modifiers, diagnostics de recomposition)
restent HORS périmètre jusqu'à ce que le typage borné (ADR 0107)
s'étende par décision écrite.

## Conséquences

- La capacité continue de fonctionner sans type Compose (frontière
  D6-like maintenue) ; aucun couplage aux artefacts Compose.

## Plan de migration progressive

Reprise/réécriture à l'étage confort (après navigation) ; fiche 051
comme gate.

## Tests de non-régression et critères de sortie

- Fiche 051 verte (16/16).

## Risques

- R1 — Évolution de l'annotation `@Preview` amont : mitigation =
  paramètres tranchés dans la source (pas de schéma figé) + veille.

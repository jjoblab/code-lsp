# ADR 0109 — Synthétiques Android et code généré

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K6 du prompt : « Quelle règle de preuve avant tout build, et quand
s'effacent-ils devant le généré réel ? » Les synthétiques (R,
BuildConfig, ViewBinding, DataBinding — 667 l. chez l'ancien, D5) sont
une **invention maison** sans équivalent amont embarquable : l'IDE de
référence délègue à Gradle. Les sources générées KSP/Hilt/Room sous
`build/` restent l'angle mort documenté (H2 du rapport faux positifs).

## Sources consultées

- Cahier : fiches 047 (`SessionLangageKotlinK5Test` — R, ViewBinding,
  DataBinding, BuildConfig, coupure, sans manifeste), 041
  (`ReproductionViewBindingTest`), 034 (`ReproductionSourcesGenereesTest`
  — H2 documenté).
- `codeide-lsp` : `docs/LSP_KOTLIN.md` D5, ADR 0219 V5 (convention
  sous-paquet `databinding`).
- Étude : `01-architectures-analyse-kotlin.md` §K6.

## Options

**Option 1 — Reprendre le design à l'identique** (voie C : module
`synthetics/` candidat de reprise propre ; voie A : réécriture cahier
en main). **Option 2 — Modeler le généré par conventions de noms**
(Hilt_, _Impl) : liste blanche fragile, faux positifs garantis —
écartée (option 2 de l'ADR 0221-like). **Option 3 — Ne rien
entreprendre avant le build réel** : régresse la capacité
différenciante — écarté. **Option 4 — Ne rien changer.**

## Critères et mesures

- Règle de preuve éprouvée par les fiches : synthétiques résolvent
  AVANT tout build (DOM borné sur `res/` + manifeste ; manifeste sans
  `package` = aucun synthétique, zéro FQCN inventé) et **s'effacent
  dès qu'une classe générée réelle homonyme existe** (coupure du
  groupe entier — fiche 047).

## Décision proposée

**Option 1** : le design D5 est conservé tel quel (preuve avant build,
coupure devant le réel, convention AGP du sous-paquet `databinding`,
BuildConfig type seul — champs jamais inventés). Extension H2
(documentée, à décider) : racines `build/generated` en lecture seule
POUR LA RÉSOLUTION (jamais la complétion parasite) — la fiche 034
caractérise le comportement actuel (parent muet) ; la correction
deviendrait une décision écrite post-parité.

## Conséquences

- Capacité différenciante conservée à coût minimal (reprise voie C) ;
- le généré réel reste la seule source de vérité (coupure).

## Plan de migration progressive

Reprise/réécriture à l'étage classpath+projet (miroir K5 ancien) ;
la coupure éprouvée par la fiche 047 reste la gate.

## Tests de non-régression et critères de sortie

- Fiches 047/041/034 vertes (y compris « sans manifeste, rien de
  faux » et « coupure par classe réelle »).

## Risques

- R1 — Évolution des conventions AGP : mitigation = tests sur
  convention documentée + veille de version (ADR 0121).

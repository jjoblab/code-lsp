# ADR 0113 — Complétion et classement (banc de qualité commun avec Java)

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K10 du prompt : « Quels mécanismes de classement sont transposables ? »
L'étude Java a établi que le classement est « le plus gros levier de
qualité perçue » (ADR 0012 — chaîne weighers/arrangement/split RPC,
designs IntelliJ) ; l'ancien moteur Kotlin offre une complétion correcte
(contextuelle vague 1, ADR 0228) SANS classement structuré — écart
qualifié de « retard majeur » par le rapport d'analyse du 28/10 (cité
par ADR 0228 [non vérifié — document interne non relu]).

## Sources consultées

- `code-lsp` : ADR 0012 Java (chaîne de classement — designs
  IntelliJ étudiés : weighers paresseux, gel, liftShorter, trim honnête,
  `RpcPrefixMatcher` = re-filtrage local client), ADR 0016 (banc
  qualité — recall/top-1/MRR).
- Cahier : fiches 045 (`SessionLangageKotlinCompletionContextuelleTest`),
  023 (K3 complétion), 040 (vague 5 — scopes), 042 (ViewBinding),
  047 (R/multi-langue).
- `codeide-lsp` : ADR 0228 (contextuelle v1 — trois contextes).

## Options

**Option 1 — Chaîne de classement intégrée en naissance** (voie A) ou
greffée tôt (voie B/C) : weighers + arrangement + frontière RPC,
language-agnostique (transposition directe de l'ADR 0012).
**Option 2 — Classement scalaire unique** (statu quo ancien — tri
pertinence/longueur/alphabétique) : abandonne le levier n° 1 de
qualité perçue — écarté. **Option 3 — Ne rien changer.**

## Critères et mesures

- Critère §4 du prompt : recall/top-1/MRR sur corpus fixe, **banc
  commun avec Java** (ADR 0016 — les corpus/métriques sont partagés,
  les implémentations par langue).
- Fiches complétion (44 comportements) = le plancher de parité ; le
  banc mesure l'au-delà.

## Décision proposée

**Option 1** : la chaîne de classement de l'ADR 0012 est la
spécification du pipeline de complétion Kotlin neuf (intégrée en
naissance en voie A ; greffée à l'étage complétion en voie C) ; le banc
de qualité est **commun** avec Java (corpus versionnés partagés,
métriques identiques, baselines par langue — Q-K6). La complétion
contextuelle v1 (arguments nommés, constantes d'enum, attributs
d'annotation) est reprise en parité ; la vague 2 (pesée par type
attendu) reste après le typage (ADR 0107).

## Conséquences

- Le classement Kotlin naît bancé (baseline signée avant toute
  extension) — pas de régression invisible.
- Le split RPC (re-filtrage local par préfixe côté client) est le
  patron d'intégration LSP (capability riche, ADR 0118).

## Plan de migration progressive

Étage complétion : parité (fiches) → chaîne de classement → banc
baseline → vagues contextuelles ultérieures.

## Tests de non-régression et critères de sortie

- Fiches complétion vertes ; banc commun : recall ≥ baseline, top-1/MRR
  en hausse mesurée, p95 ≤ baseline, zéro « saut » de liste.

## Risques

- R1 — Sur-investissement classement avant parité : mitigation =
  séquence parité d'abord (fiches), classement ensuite.

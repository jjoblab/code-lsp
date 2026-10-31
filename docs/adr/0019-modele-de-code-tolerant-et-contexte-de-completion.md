# ADR 0019 — Modèle de code tolérant aux erreurs et contexte de complétion

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Écarts résolus** : G-004, G-024, G-025, G-031 (renvois `01-audit-moteur.md` §1.9, §1.10 ; `03-matrice-ecarts.md`)

## Contexte

La classification du contexte de complétion repose sur une **échelle de six variantes
de splice** textuelles (`completion/AnalyseurContexte.kt:747–780`) autour d'un marqueur :
ecj abandonne les membres incomplets en fin d'unité, les variantes compensent
(remplacement, `()`, ` x;`, ` x`, `() {}`, queue équilibrée). C'est la même invention
que la référence (copie du fichier + identifiant factice au caret, `02-reference…`
§A2), mais **appliquée par variantes textuelles** au lieu d'une copie unique : fragile
par construction (écart M6 : lambda dans la queue — `ECARTS-SERVEUR.md` §2 ; M1 :
`extends` en tête de déclaration verrouillé par la fiche 007). L'audit n'a **pas**
mesuré le taux d'échec de classification sur frappe réelle (`[NON VÉRIFIÉ]` —
propriété P6 du banc livré). Par ailleurs l'ADR 0006 (vague « java-syntax » :
validation d'accolades pré-ecj, plage commune) est restée **Proposé** : la dépendance
n'est pas dans le build du module `:java`, et les conditions d'usage des artefacts
Binaires JetBrains restent à confirmer (ADR 0017, action propriétaire).

## Sources consultées

- `02-reference-android-studio.md` §A2 (mécanisme de la copie + identifiant factice,
  contributeurs position-driven) — code public de la plateforme et documentation SDK.
- `01-audit-moteur.md` §1.9, §3.1 (état exact de l'échelle, ADR 0006 non livré).
- ADR 0006 (parsing et incrémentalité), ADR 0012 (complétion — le splice conservé),
  ADR 0017 (conditions artefacts JetBrains — contrainte explicite du prompt §7.1).
- Banc livré : propriétés P6/P2 (`java/src/test/…/coherence/`).

## Options

**Option 1 — Convergence « copie + identifiant factice » (miroir de la référence).**
Toujours classifier sur **une copie du texte avec identifiant factice inséré au caret**
(une seule construction, plus de cascade) ; l'arbre ecj de la copie fournit le contexte ;
l'échelle de splice est conservée comme **repli** derrière drapeau.

**Option 2 — Arbre tolérant aux erreurs « java-syntax » (cadre ADR 0006).** Ajouter la
dépendance `java-syntax` et classifier sur un arbre tolérant (pas de réparation du
texte du tout), ecj restant souverain pour la collecte. **Conditionnée** à la
confirmation des conditions d'artefacts (ADR 0017) — +0,8 Mo APK, 0 motif ART constaté
au spike V1.

**Option 3 — Statu quo (échelle seule).** Zéro coût ; conserve M6/M1 ; le taux d'échec
reste inconnu jusqu'à P6.

## Critères et mesures

- **P6 (livré, à jouer)** : taux de suggestions « résolubles » (insertion → zéro
  nouvelle erreur) par position, sur corpus réel + historiques de frappe.
- **P2** : stabilité de la classification (même position → même genre) froid/chaud.
- Latence p95 de classification ≤ baseline du banc qualité (32,46 ms hérités — borne
  basse JVM).
- [À mesurer] : taux d'échec de classification par variante (télémétrie de debug
  locale, opt-in).

## Décision proposée

**Option 1 immédiatement** (peu coûteuse, supprime la cascade du chemin chaud),
**option 2 comme cadre cible** si (a) P6 montre un taux d'échec > 5 % après l'option 1
et (b) les conditions d'artefacts JetBrains sont confirmées (ADR 0017) — la vague
« incrémentalité parsing » de l'ADR 0006 reste le véhicule. Le classement D8, la
collecte par genre et la frontière RPC (ADR 0012) sont **inchangés**. M1 (`extends`)
est traité côté ADR 0026 (fusion mots-clés + stubs en tête de déclaration, arbitrage
cahier à demander).

## Conséquences

- Le chemin de classification devient déterministe (une copie, un factice) — testable
  unitairement ; l'échelle de splice survit en repli une version.
- Aucune dépendance nouvelle tant que l'option 2 n'est pas déclenchée.

## Plan de migration progressive

1. Drapeau `completion.contexte-copie` : classification par copie+factice, repli
   échelle ; banc qualité + P6 avant/après.
2. Si déclenchement option 2 : vague ADR 0006 (validation accolades → plage commune →
   arbre léger), chaque étape sous drapeau.

## Tests de non-régression et critères de sortie

- P6 ≥ 99 % de suggestions résolubles sur corpus réel ; P2 : 0 violation.
- Banc qualité : recall ≥ baseline, top-1/MRR ≥ baseline, p95 ≤ baseline.
- Critère de sortie : 2 versions sans recours au repli.

## Risques

- **R1 — La copie factice échoue où une variante réussissait** : repli conservé une
  version + P6 comparatif.
- **R2 — Conditions artefacts JetBrains non confirmées** : l'option 2 reste bloquée ;
  l'option 1 est autonome (aucune dépendance).
- **R3 — Latence de la copie** : bornée par la taille du fichier ; mesure P2/p95.

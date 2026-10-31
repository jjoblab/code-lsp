# ADR 0030 — Qualité mesurée « niveau Android Studio » (corpus, propriétés, métriques, baselines)

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Écart résolu** : G-034, G-005 (`01-audit-moteur.md` §1.11, §1.12 — la vérité terrain manquante)

## Contexte

Les bancs actuels mesurent la **parité** (oracle = ancien moteur, 165 deltas) et un
corpus de **18 positions** saturées (recall/top-1/MRR = 1,000 — sur-apprentissage
avéré par construction, risque n°5 de la synthèse PSI) : ils ne disent rien de la
qualité « niveau Android Studio » (H11). Il manque : un corpus **réel**, des
métriques de **faux positifs/faux négatifs**, des positions de complétion étiquetées,
et des **propriétés d'incohérence** (P1–P8) exécutables en CI. La vague 0 de cette
mission livre déjà : le banc P1–P8 (`java/src/test/…/coherence/`), le corpus 5 projets
(commit épinglés, script de téléchargement), le protocole de captures Android Studio
(06), le gabarit de mesure appareil (07). Cet ADR en fixe le **cadre permanent**.

## Sources consultées

- `01-audit-moteur.md` §1.11, §4, §5 (état des bancs, corpus, mesures exécutables).
- ADR 0016 (baselines versionnées signées, CI bloquante — cadre réutilisé tel quel).
- `02-reference-android-studio.md` §A2 (métriques de la référence : +10 % sélection
  ML — le genre de chiffre que nos bancs doivent pouvoir trancher), §A7 (divergences
  documentées de la référence — notre P3 mesure les nôtres).
- `docs/RECALAGE-1/2.md` (méthode de mesure honnête, variabilité p95 — ±×1,5,
  passes communes exigées).
- ADR 0025 (P1/P2/P8 deviennent gates), ADR 0026 (banc qualité étendu).

## Options

**Option 1 — Cadre permanent à trois étages (recommandée).**
(a) **Corpus réel versionné par script** (5 projets, commits épinglés au registre de
    provenance ; téléchargement CI, jamais de binaire commité) + **split train/éval**
    déterministe (le corpus de développement des cas dorés ≠ le corpus d'évaluation —
    la réponse formelle au sur-apprentissage).
(b) **Propriétés P1–P8 en gates CI** : P1 équivalence rapide/complet sur rejeu
    d'historiques de frappe ; P2 idempotence/stabilité ; P3 zéro faux positif sur
    code compilant (FP/1 000 lignes, par famille d'ID ecj) — inclut la mesure
    **ecj↔javac** (le corpus compile avec javac/AGP : tout rouge sur ces fichiers est
    un FP potentiel, arbitré) ; P4 rappel de vrais positifs par mutants (à la bonne
    plage) ; P5 cycle quick-fix ; P6 complétion résoluble ; P7 rename sûr ; P8
    cohérence inter-fichiers.
(c) **Métriques et seuils** : FP/1 000 lignes (cible < 0,5 en régime nominal, 0 sur
    le noyau dur), rappel mutants (≥ 0,95), top-1/top-5/MRR sur **positions réelles
    étiquetées** (cible top-1 ≥ 0,80 — à recalibrer après la première mesure),
    taux de suggestions compilables (≥ 0,99), latences p50/p95/p99 par capacité ;
    **fiches de comportement dérivées de la référence** (05) comme vérité terrain
    qualitative ; protocole de captures Android Studio (06) pour les cas que nous ne
    pouvons pas exécuter.
(d) **Politique de baseline** : chaque métrique a une baseline JSON signée (format
    ADR 0016) ; régression = CI rouge ; amélioration = re-baseline par PR dédiée.

**Option 2 — Étendre le corpus 18 positions.** Insuffisant : reste artificiel,
non représentatif du code réel (AndroidX, binding, générics).

**Option 3 — Bancs ponctuels manuels.** Rejeté (c'est le constat fondateur, ADR 0016).

## Critères et mesures

- Reproductibilité : script unique, empreinte du corpus (SHA par projet), résultats
  JSON bruts + seuils, environnement consigné (CI = référence, JVM locale = borne
  basse).
- Variabilité : 3 passes min, médiane, p95 documentés avec incertitude (RECALAGE-2).
- Coût CI : P3/P4/P7 (lourds) en nightly ; P1/P2/P5/P6/P8 (légers) à chaque PR.

## Décision proposée

**Option 1.** La vague 0 (livrée) pose l'outillage ; cet ADR en fait des **gates** :
P1/P2/P5/P6 bloquantes à chaque PR dès la vague V-A ; P3/P4/P7/P8 bloquantes en
nightly dès V-A ; le banc qualité complétion est **étendu** aux positions réelles
étiquetées (50+ positions issues du corpus, split train/éval) à la vague V-C. La
mesure ecj↔javac (P3) produit un **rapport de divergences arbitré** — base de la
couche « quirks » documentée (02 §A7-implications).

## Conséquences

- Chaque vague d'intelligence (V-B/V-C/V-D/V-E) arrive avec ses chiffres avant/après
  sur le **même** corpus — les décisions ADR 0021–0028 s'arbitrent sur données.
- Coût CI mesurable (nightly) ; le corpus ne bouge que par PR (commits re-épinglés).
- Le sur-apprentissage devient structurellement impossible (split + corpus réel +
  positions nouvelles à chaque vague).

## Plan de migration progressive

1. (Livré) banc P1–P8 + corpus + protocoles ; première exécution CI = **baseline
   d'écart** (l'état actuel du moteur mesuré — matière première des vagues).
2. V-A : passages en gates (légers PR, lourds nightly).
3. V-C : banc complétion étendu (positions réelles étiquetées, split).
4. Chaque vague : enrichissement par PR (discipline ADR 0016).

## Tests de non-régression et critères de sortie

- Seuils ci-dessus tenus sur 3 passes ; zéro baseline cassée durablement ; rapport
  de divergences ecj↔javac entretenu (toute divergence arbitrée : FP corrigé, FN
  documenté, ou « quirk » consigné).

## Risques

- **R1 — Corpus indisponible en CI (réseau)** : miroir d'artefacts ou cache GitHub
  Actions ; le banc saute **annoncé** (jamais silencieusement vert).
- **R2 — Bruit des projets amont** (code ne compilant pas à un commit donné) :
  commits épinglés + vérification de compilation à l'épinglage.
- **R3 — Coût nightly** : corpus borné (5 projets), filtrage des fichiers, budget
  temps documenté.

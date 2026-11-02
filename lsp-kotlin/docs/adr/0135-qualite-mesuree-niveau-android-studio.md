# ADR 0135 — Qualité mesurée « niveau Android Studio » : corpus, propriétés P1–P9, seuils et baselines

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Amende** : ADR 0119 (banc de qualité — extension à l'échelle réelle)
- **Écarts** : GK-002, GK-034 (KH3, KH13)
- **Aligne** : ADR Java 0030 (cadre P1–P8, décisions D5 approuvées)

## Contexte

Le dispositif de qualité actuel est **auto-référentiel** : banc de fidélité
sur 86 lignes (4 mondes + témoin), banc de complétion saturé
(1,000/1,000/1,000 sur 16 positions — il ne peut plus distinguer
amélioration et régression), baselines signées sur les productions du
moteur lui-même (KH3/KH13). Le banc Java P1–P8 (ADR 0030) a corrigé ce
trou côté Java (26 violations initiales corrigées en V-A1). La vague 0 de
cette mission livre l'équivalent Kotlin : **banc P1–P9 + corpus réel
(6 projets Apache-2.0 épinglés) + auto-corpus**, et la mesure inaugurale
est déjà tombée : **980 FP / 34 934 l. = 28,05 FP/1 000 lignes, FN = 0**
(audit §4.3).

## Sources consultées

- Audit §4.3 (mesure P1), §1.3 (corpus minuscule), §5 (corpus livré).
- ADR Java 0030 (option 1 : corpus épinglé + split train/éval + P1–P8 en
  gates + baselines signées ; seuils D5 : FP/1 000 < 0,5, top-1 ≥ 0,80,
  P6 ≥ 99 % — approuvés 31/10, arbitré par la passe 1).
- `bancs/corpus-android-kotlin/LISEZMOI.md` (protocole des manifestes).

## Options

| Option | Description | Critères |
|---|---|---|
| **1 — Cadre permanent à trois étages (recommandée, miroir 0030)** | (a) corpus réel versionné par script + **auto-corpus toujours actif en CI** + split train/éval déterministe ; (b) propriétés P1–P9 en gates ; (c) métriques et seuils ; (d) baselines signées (régression = rouge, amélioration = re-baseline par PR dédiée) | la mesure inaugurale prouve l'utilité (980 FP invisibles du banc actuel) ; l'auto-corps ne coûte aucun téléchargement en CI (~36 s mesurées) |
| **2 — Statu quo (bancs actuels)** | 86 lignes + 16 positions saturées | aveugle par construction — écarté |
| **3 — Corpus seul, sans gates** | mesurer sans bloquer | un banc qui ne bloque pas régresse en silence — écarté (leçon Java) |

## Critères et mesures

- **Seuils proposés (miroir D5 Java, à valider par le propriétaire)** :
  FP/1 000 lignes < 0,5 en régime nominal (0 sur le noyau dur) ;
  rappel mutants ≥ 0,95 (P3) ; top-1 ≥ 0,80 / top-5 / MRR sur **50+
  positions réelles étiquetées** avec split train/éval (le banc actuel à
  16 positions sert d'apprentissage, plus d'évaluation) ; suggestions
  compilables ≥ 0,99 (P6) ; latences p50/p95/p99 par capacité ;
- **gating** : P1 auto-corpus, P4, P5, P9 **à chaque push** (mode rapport
  par défaut pendant V-A — l'escalade stricte suit la correction de la
  baseline : `BANC_COHERENCE_STRICT=true`) ; P1(b)/P2/P3/P7 corpus en
  **nuit**/à la demande (manifestes requis, `assumeTrue` avec raison —
  jamais un vert silencieux) ;
- **politique de baseline** : la baseline FP du 31/10 (28,05/1 000 l.)
  est CONSIGNÉE et jamais réécrite — les vagues la font descendre, les
  re-signatures passent par des PR dédiées (R5).

## Décision proposée

**Option 1**. Le banc P1–P9 livré en V0 devient le cadre permanent ;
l'étiquetage de 50+ positions réelles (train/éval) est le premier chantier
de la V-A' qualité (miroir banc Java).

## Conséquences

- La CI reste verte pendant V-A (mode rapport), la baseline FP descend
  sous garde stricte ensuite ;
- le corpus externe reste optionnel en CI (téléchargement + SDK requis).

## Plan de migration progressive

V0 (livré) → V-A (correction baseline FP, escalade stricte) → V-A'
(étiquetage positions réelles, split train/éval) → vagues suivantes
sous gates.

## Tests de non-régression et critères de sortie

- P1 auto-corpus vert (rapport puis strict) ; suite 523+ tests verte ;
  CI 10/10 inchangée ; toute mesure consignée avec sa commande (R5).

## Risques

- R1 — L'auto-corpus est JVM, pas Android : la confirmation vient des
  manifestes (P1(b)) — les deux voies sont livrées ;
- R2 — Sur-apprentissage du nouveau banc : split train/éval dès V-A'
  (leçon Java AL1).

PV DE LECTURE — SÉRIE APPAREIL DES 3 PASSES DU GABARIT 07 ENRICHI (V-H)
(moto g06, 12/11/2026, passes jouées 17:07 / 17:11 / 17:25 heure appareil)
====================================================================

R5 : AUCUNE valeur fabriquée. Tous les chiffres viennent des trois
transcripts bruts joints (jamais retouchés) :
- mesures/2026-11-12-vh-passe1-1707-transcript.txt (17:07:37) ;
- mesures/2026-11-12-vh-passe2-1711-transcript.txt (17:11:14) ;
- mesures/2026-11-12-vh-passe3-1725-transcript.txt (17:25:17).
La passe de 16:59 (consignée plus tôt le même jour) devient la
**PASSE TÉMOIN** : elle est conservée telle quelle
(mesures/2026-11-12-vh-passe-temoin-1659-transcript.txt + lecture
associée) mais HORS SÉRIE — les passes espacées de ≥ 30 s exigées par
le gabarit 07 §2 sont les trois ci-dessus (espacements : 3 min 37 s,
puis 14 min 03 s — protocole tenu). Médianes et facteurs calculés par
code (scripts/lecture-serie.py), jamais de tête.

--- Le contexte de la série -----------------------------------------

Même appareil (moto g06, Android 15, aarch64, bac `com.acside`,
OpenJDK 17.0.19, 8 cœurs, 3,8 Go total / 594-734 Mo libres selon la
passe), même kit (moteur b0bb9ce), même corpus (géant 1 803 l. de la
fiche 021, texte muté à chaque itération). Charge (mode avion, autres
applications) : TOUJOURS non consignée dans les transcripts — la
nuance vaut pour la série entière (gabarit §2 : à consigner par
l'opérateur ; aucune inversion possible des lectures, voir §4).

--- 1. Les médianes : le chemin de frappe reste VERT, les seuils H/F5
       restent manqués -------------------------------------------------

| Mesure | P1 17:07 | P2 17:11 | P3 17:25 | Médiane | Seuil | Verdict |
|---|---|---|---|---|---|---|
| S1 chaud (ms) | 379 | 249 | 197 | **249** | < 3 000 | TENU ×12 |
| S2 froid (ms) | 4 567 | 3 373 | 2 493 | **3 373** | — | — |
| F1 p50/p95 | 4/10 | 2/6 | 2/5 | **2/6** | < 100 | TENU ×17 |
| F2 p50/p95 | 7/13 | 4/9 | 4/7 | **4/9** | < 100 | TENU ×11 |
| F3 p50/p95 | 1/2 | 0/2 | 0/2 | **0/2** | < 100 | TENU ×50 |
| F4 p50/p95 | 0/2 | 0/1 | 0/2 | **0/2** | < 100 | TENU ×50 |
| F5 géant p50/p95 | 1 383/1 669 | 1 220/1 411 | 1 151/1 242 | **1 220/1 411** | < 300 | **MANQUÉ ×4,7** |
| F6 p50/p95/p99 | 22/29/40 | 19/24/28 | 19/23/25 | **19/24/28** | — | aucune fige |
| C1 pendant p95 + facteur | 11 + ×2,00 | 5 + ×0,67 | 7 + ×1,00 | **7 + ×1,00** | — | contention nulle |
| Q1 édition A→rouge B (ms) | 108 | 72 | 40 | **72** | < 300 | TENU ×4,2 |
| Q2 périmés servis | 0 | 0 | 0 | **0** | 0 | TENU |
| M1/M2 (Mo) | 8 / 12+4 | 8 / 12+4 | 8 / 12+4 | **8 / 12+4** | — | stable |
| H1 1er profond (ms) | 27 308 | 18 985 | 18 647 | **18 985** | < 10 000 | **MANQUÉ ×1,9** |
| H2 profond chaud (ms) | 24 809 | 18 956 | 18 736 | **18 956** | < 2 000 | **MANQUÉ ×9,5** |

Les trois passes confirment la passe témoin sur tous les seuils :
mêmes verdicts, facteurs légèrement plus favorables (H1 ×2,7 → ×1,9 ;
F5 ×5,1 → ×4,7). La baseline appareil du chemin de frappe est
ACQUISE sur médiane : F1-F4 ≤ 9 ms p95 (marges ×11 à ×50), S1 249 ms
(×12), F6 p99 28 ms, C1 ×1,00, Q1 72 ms, zéro périmé, mémoire 8→12 Mo.

--- 2. H2 ≈ H1 : la cause structurelle confirmée 3 fois sur 3 ------

| Passe | H1 (ms) | H2 (ms) | H2/H1 | Delta |
|---|---|---|---|---|
| P1 17:07 | 27 308 | 24 809 | 0,908 | −2 499 ms |
| P2 17:11 | 18 985 | 18 956 | 0,998 | −29 ms |
| P3 17:25 | 18 647 | 18 736 | 1,005 | +89 ms |
| (Témoin 16:59 | 27 163 | 27 129 | 0,999 | −34 ms) |

Sur les 3 passes de la série, H2 == H1 à ±2,5 % près (et à ±0,5 % sur
P2/P3) : l'option 2 RE-PAYE l'amorçage du sous-processus K2 à CHAQUE
requête — le diagnostic « chaud » n'existe pas sans démon. La lecture
de la passe témoin est CONFIRMÉE par la série : c'est bien une cause
structurelle (relance du sous-process), pas un artefact de passe.

--- 3. Tendance thermique H : 27,3 s → 18,6 s — à lire avec prudence

H1 passe de 27 308 ms (P1) à 18 647 ms (P3), soit −32 % ; S2 suit la
même pente (4 567 → 2 493 ms, −45 %) tandis que les mesures STABLES
restent stables (F1 p95 10 → 5 ms, F5 p95 1 669 → 1 242 ms — tendance
identique, amplitude ×1,3-1,5 entre passes pour H1 comme pour F5).
Lecture honnête : la MEILLEURE passe de la série (H1 18 647 ms,
toutes conditions confondues) reste ×1,86 la cible de 10 s, et H2
18 736 ms reste ×9,4 la cible de 2 s. La tendance ne renverse AUCUN
verdict — elle dit seulement que le premier « sursaut thermique » de
l'appareil (P1, après une période d'inactivité) est le cas PIRE et
que l'appareil se stabilise vers ~18,6-19 s à chaud d'appareil (pas
« chaud » de sous-process : H2 ≈ H1 à chaque passe). Pour l'arbitrage
option 3, c'est la plage 18,6-27,3 s qu'un démon devrait amortir sur
une session.

--- 4. Marges d'inversion (pourquoi la médiane ne peut rien renverser)

- seuils MANQUÉS par ×1,9 (H1), ×9,5 (H2), ×4,7 (F5) en médiane ; la
  meilleure passe de la série reste à ×1,86 / ×9,37 / ×4,1 des seuils :
  aucun écart inter-passes observé (×1,3-1,5 sur les mesures longues)
  ne comble un facteur ×1,9 ;
- seuils TENUS par ×4,2 (Q1) à ×50 (F3/F4) en médiane ; la pire passe
  de la série reste à ×2,8 (Q1 108 ms) — aucune dégradation ne les
  renverse ;
- la cause H2 ≈ H1 est structurelle : elle est reproduite sur les
  4 exécutions indépendantes (3 série + témoin).

--- 5. Verdict proposé à l'arbitrage du propriétaire (sur la série) --

La mesure (a) de l'ADR 0124 ÉCHOUE sur la série protocolaire :

- **option 2 (sans démon) : REJET MOTIVÉ, CONFIRMÉ PAR LA SÉRIE** —
  H1 médiane ×1,9, H2 médiane ×9,5, cause structurelle reproduite
  3/3 (amorçage re-payé à chaque requête). Le palier 2 reste le
  plafond de fidélité ;
- **l'option 3 (démon résident) reste la seule voie crédible vers les
  seuils** : potentiel chiffré par la série — l'amorçage de ~19-27 s
  (selon thermique appareil) serait payé UNE fois par session, le
  diagnostic chaud deviendrait le temps de compilation seul (l'ordre
  de grandeur reste à isoler par une passe dédiée au banc : candidate
  à la re-passe gabarit 07 §7 après décision du propriétaire) ;
- **Q-K7 : ACQUIS** (inchangé — 4 exécutions sur le même environnement
  déporté `com.acside`, OpenJDK 17.0.19 aarch64) ;
- **la baseline appareil du chemin de frappe : ACQUISE SUR MÉDIANE**
  (gabarit §6 (i) : figer la série comme baseline) ; F5 géant reste
  hors seuil (×4,7) — recalibrage proposé au gabarit §6 : budget
  ~1 500 ms ou recentrage du corpus cible sur les documents de
  travail (Q1 tenu à 72 ms médiane).

La décision finale appartient au propriétaire (ADR 0124 §10).

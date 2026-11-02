# ADR 0119 — Banc de qualité et non-régression Kotlin

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

Critère §4 du prompt : fidélité des diagnostics (faux positifs/négatifs
face à K2 sur corpus fixe), qualité de complétion (recall/top-1/MRR,
**banc commun avec Java** — ADR 0016), démarrage/mémoire sur ART. Les
cibles « à créer » sont proposées ici avec justification. Le corpus
minimal du prompt : projets Android Views, Compose, Kotlin/JVM, scripts
`.gradle.kts`, code généré, **gros fichiers ≥ 3 000 lignes** (le rapport
faux positifs recommande de mesurer à cette échelle — notre S0 le
confirme : diagnostics chauds 364 ms à 3 000 l. [mesuré]).

## Sources consultées

- `code-lsp` : ADR 0016 Java (bancs versionnés, baselines JSON signées,
  CI bloquante) — le gabarit à transposer.
- Spike : `02-spike-mesures.md` (méthode, corpus déterministe, mesures).
- Cahier : 469 comportements = le corpus de parité contractuel.
- `codeide-lsp` : corpus E2E quatre mondes (classpaths réels Compose
  17 jars / Views 27 artefacts / KMP / Gradle) — à reprendre comme
  données.

## Options

**Option 1 — Quatre bancs versionnés + CI bloquante (miroir ADR 0016
Java), corpus communs aux deux langues.** **Option 2 — Latences seules**
(statu quo ancien) : ne mesure ni la fidélité ni le classement — ne
satisfait pas §4 — écarté. **Option 3 — Banc ad hoc non versionné** :
non rejouable — écarté. **Option 4 — Ne rien changer.**

## Critères et mesures (cibles proposées, à valider par le propriétaire)

| Banc | Métrique | Cible proposée | Justification |
|---|---|---|---|
| Fidélité diagnostics | faux positifs sur corpus quatre mondes + corpus 3 000 l. | **0** (invariant §4) | contrat « preuve ou silence » |
| Fidélité diagnostics | faux négatifs vs oracle K2 (sous-processus) | baseline signée, variation = régression | la couverture se mesure, ne se déclare pas |
| Complétion | recall/top-1/MRR (banc commun Java) | recall ≥ baseline ; top-1/MRR +5 pts après classement | miroir ADR 0012/0016 Java |
| Complétion | p95 à chaud (≤ 2 000 l., index chaud) | < 100 ms sur appareil ; JVM = limite basse | cible §4 |
| Latence | premier diagnostic après parse | < 250 ms | cible §4 |
| Empreinte | jar moteur + dépendances T1 | ≤ 4 Mo APK (mesure dex en CI) | cible §4 — marge mesurée ×4 [spike S0/S2 : 0,86 Mo moteur] |
| Gros fichiers | reparse/diagnostics à 3 000 l. | reparse < 50 ms JVM ; diagnostics < 500 ms JVM | S0 mesuré 37,7/364 ms |
| Parité cahier | 469 comportements vs oracle 1.22.1 | 469/469 verts | critère de bascule |

**Corpus minimal versionné** (dans le dépôt, comme données) :
templates quatre mondes (repris de l'ancien dépôt — classpaths réels
attachés au banc, jamais dans l'APK), corpus JVM/Kotlin pur, scripts
`.gradle.kts` canoniques, code généré (fixtures KSP/Hilt-like),
gros fichiers 3 000/10 000 lignes (générateur déterministe — le
`GenerateurCorpus` du spike est le germoir).

## Décision proposée

**Option 1** : quatre bancs (fidélité, complétion, comportements des
greffes, empreinte) versionnés dans `:testing` (Q-K6), baselines JSON
signées, CI bloquante (extension des workflows du domaine Java au
module `:kotlin` en vague 0) ; échec = régression ; amélioration =
re-baseline par PR dédiée. L'oracle de fidélité K2 = le compilateur
embarqué en sous-processus (léger à conduire : `spike-kotlin` le
démontre [mesuré]).

## Conséquences

- La qualité Kotlin devient une donnée chiffrée comparable à Java (le
  « retard majeur » de complétion devient mesurable et pilotable).
- Les corpus réels (jars Compose/Views) sont des données de banc —
  téléchargées par script (R6.4), jamais committées.

## Plan de migration progressive

Banc fidélité dès l'étage diagnostics ; banc complétion avec le
classement (ADR 0113) ; empreinte en CI dès la vague 0.

## Tests de non-régression et critères de sortie

- CI verte sur les quatre bancs ; baselines signées ; toute montée de
  version (grammaire, kotlin-metadata) re-passe les bancs (ADR 0121).

## Risques

- R1 — Sur-apprentissage du corpus : mitigation = split train/éval +
  corpus réels renouvelés (miroir risque 5 Java).
- R2 — Coût CI du banc K2 (sous-processus 3,5 s) : acceptable (une
  passe par build) ; parallélisable.

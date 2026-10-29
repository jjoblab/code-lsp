# GABARIT DE PASSE BASCULE SUR APPAREIL — étage T (§8.5, à remplir par le propriétaire)

> **Rôle** : protocole de retour des mesures **sur appareil** qui accompagnent la
> décision de **bascule** du moteur vendored vers le moteur neuf
> (`PLAN-REECRITURE.md` §8.5 : « bascule uniquement à parité : 0 comportement
> perdu contre l'oracle — banc croisé ADR 0016 — et latences §4 tenues sur
> appareil »). Le propriétaire exécute le kit (`scripts/kit-bascule.sh` →
> `banc-bascule.sh` sur l'appareil), reporte les valeurs ci-dessous et joint le
> transcript brut. **Aucune valeur de ce document n'a été pré-remplie avant
> exécution** (R5 : jamais prétendre une mesure appareil sans résultats réels —
> même règle que `spike/appareil/GABARIT-RESULTATS.md`).
>
> La PARTIE DÉTERMINISTE de la bascule (banc de parité croisé : corpus rejoué
> contre l'oracle vendored épinglé, baseline signée `bancs/baselines/
> parite-completion.json`, CI bloquante `banc-parite`) est close par la CI —
> elle ne requiert PAS d'appareil. Ce gabarit ne porte que la partie que seul
> l'appareil peut prouver : **la parité de comportement constatée sur appareil
> et les latences réelles**.

## 1. Environnement déclaré (à remplir)

> **Passe reportée** : 2026-10-29 13:56:04 (heure appareil) — sortie brute
> transmise par le propriétaire, reportée ci-dessous **telle quelle** (R5) ;
> transcript intégral joint : [`resultats-bascule.txt`](resultats-bascule.txt).
> Kit testé : `kit-bascule.zip` sha256 `064715f7…76d8e`, assemblé le 29/10
> 17:23 UTC depuis le commit `97cf181`.

| Champ | Valeur |
|---|---|
| Appareil (modèle) | moto g06 |
| SoC / RAM totale | *(à remplir — non rapporté par le transcript)* |
| Version d'Android (API) | 15 (niveau API non rapporté par le transcript) |
| Runtime d'exécution (Termux openjdk-__ / AndroidIDE…) | Termux — openjdk version « 17.0.19 » 2026-04-21 |
| Mode (chargeur branché ? économie d'énergie ? autres apps) | *(à remplir)* |
| Date et heure de la passe (heure appareil) | 2026-10-29 13:56:04 |
| `resultats-bascule.txt` brut joint ? | **oui** — `bancs/resultats-bascule.txt` (transcript exact de la passe) |
| Commit du moteur neuf testé (`git rev-parse HEAD`) | `97cf1810934ea6d53c49e082288dba495b632ee6` |

## 2. Préparation du kit (poste de bureau, une fois par passe)

```bash
bash scripts/kit-bascule.sh          # compile + assemble kit-bascule.zip
# transférer kit-bascule.zip vers l'appareil (adb push / scp / téléchargement)
```

Sur l'appareil (Termux, OpenJDK 17 — même protocole que le gabarit du spike) :

```bash
unzip -o kit-bascule.zip -d bascule && cd bascule
bash banc-bascule.sh > resultats-bascule.txt
```

## 3. Couverture du cahier sur appareil (copier la section « couverture » du transcript)

| Mesure | Valeur appareil | (référence CI JVM, pour mémoire) |
|---|---|---|
| Couverture du cahier (18 positions du corpus de parité) | **18/18** | 18/18 (recall 1.000 — `completion.json`) |
| Positions en échec (noms + libellés manquants) | **aucune** (18 ✓ — transcript) | aucune |
| Sauts de liste (contrat gel, succession de frappe) | **0** (0 exigé) | 0 |

## 4. Latences de complétion sur appareil (copier la section « latences » du transcript)

| Genre de contexte | p50 appareil (ms) | p95 appareil (ms) | (p95 JVM bureau, pour mémoire) |
|---|---|---|---|
| MEMBRE | 59,69 | 75,73 | ≈ 30 ms (corpus N2, `completion.json`) |
| MEMBRE_STATIQUE | 41,50 | 111,93 (max = 111,93) | — |
| TYPE | 39,36 | 54,87 | — |
| NOM | 33,37 | 60,39 | — |
| IMPORT / REFERENCE_PAQUET | 8,63 | 24,96 | — |
| CORPS_CLASSE / CASE_LABEL | 49,63 / 58,50 | 50,25 / 66,60 | — |
| **GLOBAL** | **42,93** | **73,01** (max = 111,93, n = 90) | 32,46 ms |

Lecture honnête (aucun seuil n'est asserté par le banc de passe — R5) : le
facteur appareil/JVM observé au spike était de **×8–14** ; cette passe
constate **×2,2 sur le p95 GLOBAL** (73,01 vs 32,46 baseline signée) et,
en référence auxiliaire, ×6,9 sur le p50 GLOBAL (42,93 vs 6,23 — fumée
bureau du même kit le 29/10, non signée) — soit un appareil **plus
favorable que la bande du spike**. La fiche 033 et les gates de latence CI
(`Fiche033BancLatenceTest`) restent les bornes de non-régression côté JVM,
la passe appareil documente la réalité de bout en bout pour la décision.

## 5. Checklist de décision de bascule (§8.5 — à cocher par le propriétaire)

| # | Critère | État | Preuve |
|---|---|---|---|
| 1 | Banc de parité croisé VERT en CI (oracle épinglé cb809e14) | ☑ | job CI `Banc parité croisée T (oracle vendored épinglé)` — **success** sur `97cf181` (29/10, vérifié via API GitHub Actions) |
| 2 | Couverture du cahier sur appareil = 18/18 (§3 ci-dessus) | ☑ | transcript de passe — 18 ✓, aucun manquant |
| 3 | Latences §4 constatées sur appareil et consignées (§4 ci-dessus) | ☑ | transcript de passe — p50/p95 par genre + GLOBAL consignés |
| 4 | Deltas du banc croisé (`pertes_vs_oracle` — 165 libellés à la signature du 29/10) arbitrés classe par classe : bruit hors cahier / comportement à rattraper / divergence D8 assumée | ☑ | **arbitrage délégué ADOPTÉ (30/10)** : 103 bruit hors cahier / 62 divergences assumées / **0 à rattraper** — décision et lecture alternative écartée consignées au §6 de [`ANALYSE-DELTAS-BASCULE.md`](ANALYSE-DELTAS-BASCULE.md), annexe libellé par libellé (chaque classe vérifiée `jrt` + code des deux moteurs) |
| 5 | Gates `model.json` dormantes (Fiche010/Fiche039 — dette documentée RECALAGE-2 §5.1) : artefact fourni OU décision explicite de baser la bascule sur les autres couvertures | ☑ | **décision explicite (30/10) : baser la bascule sur les autres couvertures** — banc croisé CI bloquant (oracle épinglé, 18/18) + banc qualité signé + passe appareil §3/§4 ; les 2 gates restent dormantes (hypothèses `assumeTrue` visibles, jamais simulées — R5), à ouvrir post-bascule sur export réel du propriétaire ou équivalent régénérable (piste notée fiche 039) |
| 6 | Plan de retour arrière actif (substitution composite inversée, ADR 0015 — 1 ligne) | ☑ | « Inverser la substitution composite de `CodeIDE/settings.gradle.kts` (l. 226–251) — `:lsp:host` re-pointe sur le moteur vendored, l'état d'avant est reconstruit **sans re-déploiement du client** (ADR 0015) ; drapeaux serveur off si vagues actives » |

Critères 1–3 cochés sur preuves objectives (CI du 29/10 + transcript
appareil) ; critères 4–6 arbitrés le **30/10** par décision déléguée du
propriétaire (« je te laisse décider de la marche à suivre pour le critère 4,
continue pour 5 et 6 ») et consignés ci-dessus. Décision finale **signée
le 30/10** (§6 ci-après).

## 6. Décision (signée)

> **Décision SIGNÉE le 30/10/2026** par le propriétaire (message « bascule ») —
> checklist §5 6/6 sur preuves ; la présente consigne la décision et le suivi
> post-bascule borné. Mise en œuvre côté client (CodeIDE) : procédure
> préparée au [`docs/BASCULE-CODEIDE.md`](../docs/BASCULE-CODEIDE.md).

- Décision de bascule : **BASCULE — signée le 30/10/2026**. Motif : 0
  comportement du cahier ou de la politique du moteur neuf perdu contre
  l'oracle (banc croisé VERT sur CI `97cf181`, deltas arbitrés 103/62/0) ;
  parité constatée sur appareil (18/18, 0 saut — §3) ; latences réelles
  consignées (p50/p95 GLOBAL 42,93/73,01 ms — §4, appareil plus favorable
  que la bande du spike ×8–14) ; retour arrière en 1 ligne sans
  re-déploiement (critère 6).
- **Suivi post-bascule borné** (non bloquant) :
  1. filtrer les membres package-private lus au bytecode (`valueOfCodePoint`,
     `coder`, `value`, `hash`, `hashIsZero`, `isLatin1`) — aligner les MEMBRES
     sur le choix « surface publique » déjà porté par les TYPES (ANALYSE §5) ;
  2. *(optionnel)* étendre la collecte TYPE aux grades hump/sous-chaîne, en
     bas de liste (seul cas défendable : `CopyOnWriteArrayList` — ANALYSE §6) ;
  3. ouvrir les gates `model.json` (Fiche010/Fiche039) sur export réel ou
     équivalent régénérable (critère 5).
- Date, signature : **2026-10-30 — propriétaire (jjoblab)**, décision
  transmise en séance (« bascule ») et consignée par l'agent le jour même.
- Transcript brut joint : `bancs/resultats-bascule.txt` (§1)

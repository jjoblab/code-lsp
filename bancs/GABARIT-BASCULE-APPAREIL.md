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

| Champ | Valeur |
|---|---|
| Appareil (modèle) | *(à remplir)* |
| SoC / RAM totale | *(à remplir)* |
| Version d'Android (API) | *(à remplir)* |
| Runtime d'exécution (Termux openjdk-__ / AndroidIDE…) | *(à remplir)* |
| Mode (chargeur branché ? économie d'énergie ? autres apps) | *(à remplir)* |
| Date et heure de la passe (heure appareil) | *(à remplir)* |
| `resultats-bascule.txt` brut joint ? | *(oui/non — chemin)* |
| Commit du moteur neuf testé (`git rev-parse HEAD`) | *(à remplir)* |

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
| Couverture du cahier (18 positions du corpus de parité) | *(x/18)* | 18/18 (recall 1.000 — `completion.json`) |
| Positions en échec (noms + libellés manquants) | *(aucune / liste)* | aucune |
| Sauts de liste (contrat gel, succession de frappe) | *(n)* | 0 |

## 4. Latences de complétion sur appareil (copier la section « latences » du transcript)

| Genre de contexte | p50 appareil (ms) | p95 appareil (ms) | (p95 JVM bureau, pour mémoire) |
|---|---|---|---|
| MEMBRE | *(à remplir)* | *(à remplir)* | ≈ 30 ms (corpus N2, `completion.json`) |
| MEMBRE_STATIQUE | *(à remplir)* | *(à remplir)* | — |
| TYPE | *(à remplir)* | *(à remplir)* | — |
| NOM | *(à remplir)* | *(à remplir)* | — |
| IMPORT / REFERENCE_PAQUET | *(à remplir)* | *(à remplir)* | — |
| CORPS_CLASSE / CASE_LABEL | *(à remplir)* | *(à remplir)* | — |
| **GLOBAL** | *(à remplir)* | *(à remplir)* | 32,46 ms |

Lecture honnête (aucun seuil n'est asserté par le banc de passe — R5) : le
facteur appareil/JVM observé au spike était de **×8–14** ; la fiche 033 et les
gates de latence CI (`Fiche033BancLatenceTest`) restent les bornes de
non-régression côté JVM, la passe appareil documente la réalité de bout en
bout pour la décision.

## 5. Checklist de décision de bascule (§8.5 — à cocher par le propriétaire)

| # | Critère | État | Preuve |
|---|---|---|---|
| 1 | Banc de parité croisé VERT en CI (oracle épinglé cb809e14) | ☐ | job CI `banc-parite` |
| 2 | Couverture du cahier sur appareil = 18/18 (§3 ci-dessus) | ☐ | transcript de passe |
| 3 | Latences §4 constatées sur appareil et consignées (§4 ci-dessus) | ☐ | transcript de passe |
| 4 | Deltas du banc croisé (`pertes_vs_oracle` — 165 libellés à la signature du 29/10) arbitrés classe par classe : bruit hors cahier / comportement à rattraper / divergence D8 assumée | ☐ | `bancs/baselines/parite-completion.json` + analyse jointe à la décision |
| 5 | Gates `model.json` dormantes (Fiche010/Fiche039 — dette documentée RECALAGE-2 §5.1) : artefact fourni OU décision explicite de baser la bascule sur les autres couvertures | ☐ | décision consignée |
| 6 | Plan de retour arrière actif (substitution composite inversée, ADR 0015 — 1 ligne) | ☐ | procédure CodeIDE |

## 6. Décision (à remplir par le propriétaire)

- Décision de bascule : *(bascule / ajournement — motif)*
- Date, signature : *(à remplir)*
- Transcript brut joint : *(chemin)*

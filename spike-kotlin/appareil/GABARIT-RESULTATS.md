# GABARIT DE RÉSULTATS — moteur Kotlin étage 0, benchmark sur appareil

> Moteur Kotlin (`:kotlin`, voie C — ADR 0100 acceptée). Ce gabarit est la
> procédure de retour des mesures **sur appareil** : le propriétaire exécute
> `banc-appareil.sh` (Termux), reporte les valeurs ci-dessous et joint le
> fichier au ticket de l'étage 1. **Aucune valeur de ce document n'a été
> pré-remplie** (R5 : jamais prétendre une mesure appareil sans résultats
> réels) — les chiffres entre parenthèses sont les LIMITES BASSES de bac à
> sable (JVM de bureau conteneurisée, 2 cœurs, rapport d'étage 0 §3).
>
> **Statut : PAS ENCORE EXÉCUTÉ** — la mesure appareil est la PORTE
> D'ENTRÉE de l'étage 1 (décision Java §10.2 transposée, synthèse §6.3) :
> l'étage 1 ne s'ouvre qu'avec ce gabarit rempli par une passe réelle.

## 1. Environnement déclaré (à remplir)

| Champ | Valeur |
|---|---|
| Appareil (modèle) | |
| SoC / RAM totale | |
| Version d'Android (API) | |
| Runtime d'exécution (Termux openjdk-__ / AndroidIDE / dalvikvm…) | |
| Mode (chargeur branché ? économie d'énergie ? autres apps) | |
| Date et heure de la passe | |
| `resultats-appareil.txt` brut joint ? | |

## 2. Mesures grammaire — pile chaude voie (i) (copier depuis la sortie)

| Mesure | Valeur appareil | (limite basse bac à sable) |
|---|---|---|
| Démarrage à froid (1er parse, 500 l.) | | 274,5 ms |
| Parse p50 / p95 (500 l.) | | 6,4 / 37,0 ms |
| Parse p50 / p95 (3 000 l.) | | 18,6 / 51,9 ms |
| Productions / jetons (3 000 l.) — déterminisme | | 31 368 / 24 274 |
| Tolérance : troncatures / levers | | 101 / 0 |
| Tas au repos | | 3 698 Kio |

Notes brutes (min/médiane/max sur passes répétées, thermique — 3 passes
minimum recommandées, miroir §5.4 de la synthèse) :

## 3. Scan ART (échantillon jar moteur, si python3 présent sur l'appareil)

| Mesure | Valeur appareil | (bac à sable) |
|---|---|---|
| Classes / méthodes déclarées (jar moteur) | | 69 / 150 |
| Références sensibles ART | | AUCUNE |

## 4. Compléments demandés (si possible sur l'appareil)

- Le facteur ART 3–5× annoncé (E-K4) se vérifie ici : rapport
  p50 appareil / p50 bac à sable — à reporter tel quel.
- Dexage réel : déjà couvert par la CI (`kotlin-art`, build-tools 34) —
  15 964 références de méthodes / 2,74 Mo [mesuré bac à sable, d8 r34] ;
  l'appareil n'a PAS à le rejouer.
- GC : comportement observé pendant les 20+ passes (pauses visibles ?) —
  note libre (ADR 0117 : les soft-références et ReferenceQueue se
  testeront à l'étage 1 avec la session).

## 5. Décision que ce gabarit alimente

- **Ouverture de l'étage 1** (noyau parse/session/classpath) : la synthèse
  §4 fixe « gabarit appareil rempli (porte d'entrée) » comme critère de
  sortie de l'étage 1 — la présente passe EST ce gabarit.
- Les latences cibles §4 du prompt (complétion < 100 ms, premier
  diagnostic < 250 ms, APK ≤ 4 Mo) se mesureront aux étages 2/3 avec le
  moteur réel — l'étage 0 ne mesure que la couche grammaticale.

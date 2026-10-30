# GABARIT DE RÉSULTATS — moteur Kotlin étage 0, benchmark sur appareil

> Moteur Kotlin (`:kotlin`, voie C — ADR 0100 acceptée). Ce gabarit est la
> procédure de retour des mesures **sur appareil** : le propriétaire exécute
> `banc-appareil.sh` (Termux), reporte les valeurs ci-dessous et joint le
> fichier au ticket de l'étage 1. **Aucune valeur de ce document n'a été
> pré-remplie** (R5 : jamais prétendre une mesure appareil sans résultats
> réels) — les chiffres entre parenthèses sont les LIMITES BASSES de bac
> à sable (JVM de bureau conteneurisée, 2 cœurs — **vague 1c**, journal §12 ;
> bruit de mesure entre exécutions documenté au même endroit).
>
> **Statut : EN ATTENTE DE LA PASSE AVEC KIT** — le propriétaire a lancé le
> script le 30/10 sur l'appareil (échec attendu « kit incomplet » : le kit
> s'assemble sur la machine de build, en-tête du script) ; `kit-kotlin.zip`
> (vague 1c, piles sha1 publiés vérifiées) a été assemblé et livré — la
> présente passe attend son exécution avec le kit en place.

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
| Démarrage à froid (1er parse, 500 l.) | | 188,1 ms |
| Parse p50 / p95 (500 l.) | | 5,1 / 38,8 ms |
| Parse p50 / p95 (3 000 l.) | | 20,6 / 53,7 ms |
| Productions / jetons (3 000 l.) — déterminisme | | 31 368 / 24 274 |
| Tolérance : troncatures / levers | | 101 / 0 |
| Tas au repos | | 3 700 Kio |

Notes brutes (min/médiane/max sur passes répétées, thermique — 3 passes
minimum recommandées, miroir §5.4 de la synthèse) :

## 3. Scan ART (échantillon jar moteur, si python3 présent sur l'appareil)

| Mesure | Valeur appareil | (bac à sable) |
|---|---|---|
| Classes / méthodes déclarées (jar moteur) | | 122 / 205 |
| Références sensibles ART | | AUCUNE |

## 4. Compléments demandés (si possible sur l'appareil)

- Le facteur ART 3–5× annoncé (E-K4) se vérifie ici : rapport
  p50 appareil / p50 bac à sable — à reporter tel quel.
- Dexage réel : déjà couvert par la CI (`kotlin-art`, build-tools 34) —
  **16 307 références de méthodes / 2,69 Mo (2 821 056 o)** [mesurés par la
  CI à la vague 1c, run 41004282232] ; l'appareil n'a PAS à le rejouer.
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

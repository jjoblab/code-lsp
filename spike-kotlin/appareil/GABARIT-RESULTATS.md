# GABARIT DE RÉSULTATS — moteur Kotlin étage 0, benchmark sur appareil

> Moteur Kotlin (`:kotlin`, voie C — ADR 0100 acceptée). Ce gabarit est la
> procédure de retour des mesures **sur appareil** : le propriétaire exécute
> `banc-appareil.sh` (Termux), reporte les valeurs ci-dessous et joint le
> fichier au ticket de l'étage 1. **Aucune valeur de ce document n'a été
> pré-remplie** (R5 : jamais prétendre une mesure appareil sans résultats
> réels) — les chiffres entre parenthèses sont les LIMITES BASSES de bac
> à sable, désormais mesurées **sous JDK 17** (toolchain du module depuis
> le correctif du 30/10 — MÊME JVM major que l'appareil ; journal §14).
>
> **Statut : PREMIÈRE PASSE ÉCHOUÉE (bug réel corrigé) — PASSE AVEC KIT v2
> EN ATTENTE**. La passe du 30/10 01:33 a levé `NoSuchMethodError :
> java.util.List.removeLast()` au premier parse : une référence **JDK 21
> seule** vivait dans le bytecode 17 de la grammaire vendorée (invisible
> en bac à sable/CI JDK 21, où la méthode existe). Corrigé sur la branche
> (toolchain JDK 17 + gardes — journal §14, alerte E-K7) ; le kit v2
> (sha256 `1352072e…`) est livré et validé sous openjdk 17 en machine de
> build : la présente passe attend son exécution avec le kit v2 en place.

## 1. Environnement déclaré (à remplir)

| Champ | Valeur |
|---|---|
| Appareil (modèle) | moto g06 [déclaré propriétaire, passe du 30/10 01:33] |
| SoC / RAM totale | |
| Version d'Android (API) | Android 15 [déclaré propriétaire] |
| Runtime d'exécution (Termux openjdk-__ / AndroidIDE / dalvikvm…) | Termux openjdk **17.0.19** (2026-04-21) [déclaré propriétaire] |
| Cœurs visibles de la JVM | 8 [mesuré par l'en-tête du banc sur l'appareil] |
| Mode (chargeur branché ? économie d'énergie ? autres apps) | |
| Date et heure de la passe | |
| `resultats-appareil.txt` brut joint ? | |

NB : ces seuls champs environnementaux sont pré-remplis des FAITS déclarés
par la passe du 30/10 — aucune MESURE appareil n'est pré-sumée (R5) ; les
cellules de mesure restent vides jusqu'au retour du propriétaire.

## 2. Mesures grammaire — pile chaude voie (i) (copier depuis la sortie)

| Mesure | Valeur appareil | (limite basse bac à sable — JDK 17) |
|---|---|---|
| Démarrage à froid (1er parse, 500 l.) | | 273,8 ms |
| Parse p50 / p95 (500 l.) | | 6,1 / 32,1 ms |
| Parse p50 / p95 (3 000 l.) | | 25,0 / 58,1 ms |
| Productions / jetons (3 000 l.) — déterminisme | | 31 368 / 24 274 |
| Tolérance : troncatures / levers | | 101 / 0 |
| Tas au repos | | 3 781 Kio |

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
  CI à la vague 1c, run 41004282232 — jar antérieur au correctif JDK 17 :
  écart attendu ≤ quelques unités, re-mesuré par la CI du commit
  correctif] ; l'appareil n'a PAS à le rejouer.
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

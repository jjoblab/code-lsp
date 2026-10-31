# 07 — Gabarit de mesure sur appareil (moteur « niveau Android Studio »)

> Style `bancs/GABARIT-BASCULE-APPAREIL.md` (R5 : **valeurs réelles, jamais
> simulées** — le gabarit est rempli par le propriétaire sur l'appareil de
> référence). Appareil de référence : **moto g06, Android 15, OpenJDK 17 aarch64 /
> ART** (même appareil que la bascule §8.5).
>
> Objectif : établir les **budgets p50/p95/p99 appareil** de la mission (§0.1 :
> complétion/hover/signature perçus < 100 ms p95 ; diagnostics de frappe < 300 ms ;
> aucune fonctionnalité de base indisponible pendant l'indexation ; démarrage à
> chaud < 3 s) — à **valider ou corriger** par ces mesures, puis à tenir vague après
> vague (ADR 0029/0030).

## 1. Conditions de passe

> **Périmètre de la première passe (avant toute vague)** — les mesures qui
> n'attendent aucune implémentation : **S1, S2, S3, F1, F3, F4, F5, F6,
> R1 (colonne « Avant V-F1 »), M1, M2, M3, M4, Q1, Q2, Q3** et C3 (déjà
> mesuré côté dépôt, voir §5). Restent vides pour l'instant : S4 (après
> V-B), F2 (après V-C), S5 (après V-G), R2/R3 (après V-F1), C1/C2 (après
> V-F). Une case non mesurable reste **vide avec sa raison** (R5).
>
> **Méthode de chronométrage conseillée** : enregistrement d'écran à 60 i/s,
> lecture image par image (≈ 16,7 ms/image) entre l'appui touche et
> l'affichage complet du popup/soulignement ; 3 passes minimum espacées de
> ≥ 30 s, mode avion (§11), aucune autre application active.

- [ ] Appareil : ____________ (modèle, Android, RAM libre au repos : ______ Mo)
- [ ] Version du moteur : `lspjava` v________ (tag/commit : __________)
- [ ] Version hôte CodeIDE : ______ / client : ______
- [ ] Corpus : projet Android réel du banc (`bancs/projet-android/`) + 1 projet du
      corpus 5 (nom : __________, commit : __________)
- [ ] Charge : appareil au repos (aucune autre app), mode avion (hors ligne — §11),
      écran allumé, 3 passes minimum par mesure (min/médiane/max — RECALAGE-2 §méthode)
- [ ] Thermique : passes espacées de ≥ 30 s ; température de départ consignée : ____

## 2. Démarrage et indexation (axe §0.1-1/3)

| # | Mesure | Passe 1 | Passe 2 | Passe 3 | p95 |
|---|---|---|---|---|---|
| S1 | Démarrage à **chaud** (caches présents) → première complétion utile (ms) | | | | |
| S2 | Démarrage à **froid** (caches vidés) → première complétion utile (ms) | | | | |
| S3 | Pendant l'indexation : `import a|` rend (nb d'items — dégradation honnête) | | | | |
| S4 | Pendant l'indexation : `R.string.|` (après V-B) rend | | | | |
| S5 | Progression visible (workDoneProgress après V-G) : oui/non | | | | |

## 3. Latence de frappe (le cœur — §0.1)

| # | Mesure | Passe 1 | Passe 2 | Passe 3 | p50 | p95 | p99 |
|---|---|---|---|---|---|---|---|
| F1 | Complétion de membres (`truc.|`) perçue (ms) | | | | | | |
| F2 | Complétion smart (après V-C — `String s = |`) | | | | | | |
| F3 | Hover | | | | | | |
| F4 | Aide de signature | | | | | | |
| F5 | Diagnostics de frappe visibles (squiggle) | | | | | | |
| F6 | Frappe dans un fichier de 1 000+ lignes (régularité : figes constatées) | | | | | | |

## 4. Reconstruction post-sync (double-tampon — V-F1)

| # | Mesure | Avant V-F1 | Après V-F1 |
|---|---|---|---|
| R1 | Sync Gradle (invalidation) → premières frappes : délai de figage (s) | (référence : 11–12 s hérités) | |
| R2 | Latence p95 de complétion PENDANT la reconstruction (× nominale) | | |
| R3 | Réponses périmées servies (0 attendu — époques) | | |

## 5. Concurrence (V-F)

| # | Mesure | Valeur |
|---|---|---|
| C1 | Complétion fichier A pendant diagnose fichier B : p95 de A (ms) | |
| C2 | Interblocage constaté (jamais — dump si survient) | |
| C3 | Comptage `synchronized` résiduel (CI : `rg -c` — cible < 6 après V-F2) | **82 occurrences** au 31/10/2026 sur `3dfcc4a` (dont 46 dans `MoteurJava.kt`, 11 dans `EnvironnementCompilation.kt`) — mesure dépôt, hors appareil |

## 6. Mémoire et pression (§11)

| # | Mesure | Valeur |
|---|---|---|
| M1 | Tas moteur au repos (Mo) après indexation du corpus | |
| M2 | Tas après 10 min de frappe (fuite ?) | |
| M3 | `onTrimMemory` respecté : caches libérés sous pression (oui/non + niveau) | |
| M4 | Taille d'APK ajoutée par le moteur (cible budget ~5 Mo ; +2 Mo prévus V-B) | |

## 7. Qualité sur appareil (échantillon du banc P1–P8)

| # | Mesure | Valeur |
|---|---|---|
| Q1 | P3 (zéro faux positif) sur 20 fichiers du corpus réel : FP constatés | |
| Q2 | P6 : suggestions insérées compilables (sur 50 insertions) | |
| Q3 | P8 : édition d'API dans A → diagnostics de B (ouvert) en < 300 ms | |

## 8. Verdict et décisions

- [ ] Les cibles §0.1 sont : atteintes / partiellement / non atteintes — commentaires :
- [ ] Budgets à recalibrer (nouveaux chiffres) :
- [ ] Décision de suite demandée au propriétaire :

> **Rappel d'honnêteté (R5)** : toute case laissée vide parce que la mesure n'a pas
> pu être faite reste vide avec la raison écrite — aucune valeur déduite du bureau
> n'est reportée ici (facteur ART 3–5× = estimation, pas une mesure).

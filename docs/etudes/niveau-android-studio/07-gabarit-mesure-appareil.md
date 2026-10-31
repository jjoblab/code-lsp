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
> mesuré côté dépôt, voir §5). **Passe 1 jouée le 30/10/2026 22 h 48
> (heure appareil), consignée le 31/10** : S2/S3, F1, F3-F6, C1/C2, Q3 et
> M1/M2 sont remplis ci-dessous ; C1/C2 — prévus « après V-F » — ont été
> mesurés par le banc vague 0 comme point **avant V-F** (contention du verrou
> global actuel). Restent vides : S1 (caches disque — hôte), S4 (après V-B),
> F2 (après V-C), S5 (après V-G), R2/R3 (après V-F1), M3/M4 (hôte), Q1/Q2
> (CI corpus). Une case non mesurable reste **vide avec sa raison** (R5).
>
> **Méthode de chronométrage conseillée** : enregistrement d'écran à 60 i/s,
> lecture image par image (≈ 16,7 ms/image) entre l'appui touche et
> l'affichage complet du popup/soulignement ; 3 passes minimum espacées de
> ≥ 30 s, mode avion (§11), aucune autre application active.
>
> **Kit d'exécution (vague 0, livré)** : sur le poste de bureau,
> `bash scripts/kit-mesure.sh` produit `build/kit-mesure.zip` (jamais
> committé). Le transférer sur l'appareil (Termux, OpenJDK 17) puis :
>
> ```bash
> unzip -o kit-mesure.zip -d mesure && cd mesure
> bash banc-mesure.sh > resultats-mesure.txt
> ```
>
> Le banc joue **3 passes espacées de 30 s** (`PASSES=1` pour une passe
> rapide) et couvre **S2/S3, F1, F3, F4, F5, F6, C1, Q3, M1/M2** ; les
> autres lignes du gabarit sont listées dans le transcript avec leur raison
> (hôte CodeIDE, vague future ou CI corpus). Poser un `android.jar` à côté
> du script fait passer la plateforme du jrt au jar (optionnel). Les
> latences rendues sont des **mesures moteur in-process** : des bornes
> basses du perçu (celui-ci ajoute le debounce hôte 50 ms et la
> publication) — le protocole écran 60 i/s reste la mesure du perçu.

- [x] Appareil : **moto g06, Android 15, aarch64** — RAM libre au repos :
      non mesurée (le banc ne sonde pas la mémoire hôte)
- [x] Version du moteur : kit `scripts/kit-mesure.sh` — commit de référence
      **`2baa9be`** (moteur + ecj 3.40.0-art patché ; le transcript correspond
      au banc livré à ce commit)
- [x] Version hôte CodeIDE : **sans objet** — banc moteur in-process, aucun hôte
- [x] Corpus : corpus de sonde du banc (`Main`/`Widget`/`Grand.java` générés en
      temporaire, textes du dépôt) — pas `bancs/projet-android/` ni corpus 5
      (banc autonome sans hôte ; Q1/Q2 relèvent de la CI corpus)
- [x] Charge : 3 passes espacées de 30 s (imposées par le script) ; repos
      exclusif et mode avion **non vérifiables** par le banc — non contrôlés
- [x] Thermique : pauses de 30 s entre passes (script) ; température de
      départ : non mesurée (pas de sonde)
- [x] **Transcript brut** : `mesures/2026-10-30-passe1-moto-g06-jrt.txt` —
      plateforme **jrt du runtime (android.jar absent)** : une re-passe avec
      `android.jar` API 34 est souhaitable avant de figer la baseline (S2
      dépend du classpath de plateforme)

## 2. Démarrage et indexation (axe §0.1-1/3)

| # | Mesure | Passe 1 | Passe 2 | Passe 3 | p95 |
|---|---|---|---|---|---|
| S1 | Démarrage à **chaud** (caches présents) → première complétion utile (ms) | | | | |
| S2 | Démarrage à **froid** (caches vidés) → première complétion utile (ms) | 7646.43 | 9363.86 | 10709.61 | 9363.86 (médiane) |
| S3 | Pendant l'indexation : `import a\|` rend (nb d'items — dégradation honnête) | 0 pdt constr. · 1 après (2.93–4.98 ms) | 0 pdt constr. · 1 après (2.97–4.57 ms) | 0 pdt constr. · 1 après (7.02–14.42 ms) | — |
| S4 | Pendant l'indexation : `R.string.|` (après V-B) rend | | | | |
| S5 | Progression visible (workDoneProgress après V-G) : oui/non | | | | |

> S2 : mesure mono-coup par passe (construction d'environnement incluse,
> 1 item rendu) ; la colonne p95 porte la **médiane des 3 passes** (min
> 7646.43 / max 10709.61 — RECALAGE-2 §méthode). `isLikelyUsable()` après
> échauffement : 0.06–0.08 ms → vrai (3/3 passes). S3 : la 1re requête
> **bloque pendant toute la construction** (0 item servi), puis la sonde
> `import java.u¦` rend 1 item en 2.93–14.42 ms — dégradation honnête :
> **rien n'est servi pendant l'indexation** à l'état vague 0 (cible V-B) ; à
> re-mesurer après V-B avec S4.

## 3. Latence de frappe (le cœur — §0.1)

| # | Mesure | Passe 1 | Passe 2 | Passe 3 | p50 | p95 | p99 |
|---|---|---|---|---|---|---|---|
| F1 | Complétion de membres (`truc.\|`) perçue (ms) | 219.81 / 461.50 | 174.22 / 404.87 | 154.68 / 331.99 | 174.22 | 404.87 | 404.87 |
| F2 | Complétion smart (après V-C — `String s = \|`) | | | | | | |
| F3 | Hover | 0.26 / 1.17 | 0.33 / 1.16 | 1.35 / 2.63 | 0.33 | 1.17 | 1.17 |
| F4 | Aide de signature | 39.18 / 88.58 | 30.74 / 81.39 | 49.48 / 83.78 | 39.18 | 83.78 | 83.78 |
| F5 | Diagnostics de frappe visibles (squiggle) | 50.03 / 168.85 | 27.65 / 178.63 | 23.24 / 119.24 | 27.65 | 168.85 | — |
| F6 | Frappe dans un fichier de 1 000+ lignes (régularité : figes constatées) — diagnose | 485.27 / 1154.13 | 553.45 / 1102.36 | 423.97 / 1181.32 | 485.27 | 1154.13 | 1154.13 |
| F6 bis | idem — complétion au milieu du fichier | 107.45 / 252.96 | 112.14 / 497.92 | 182.57 / 419.35 | 112.14 | 419.35 | 419.35 |

> Cellules par passe : **p50 / p95 intra-passe** (20 itérations mesurées ;
> F5 : 18 frappes en 3 tours de succession). Colonnes finales : **médiane
> des 3 passes** (RECALAGE-2 §méthode) ; F5 : p99 non rendu par le banc.
> Valeurs **moteur in-process = bornes basses du perçu** (le perçu ajoute le
> debounce hôte 50 ms et la publication — §1) : le perçu se mesure au
> protocole écran 60 i/s, pas encore joué. F6 : `Grand.java` (1356 lignes,
> 150 méthodes), texte muté à chaque itération (sinon diagnose mémoïsé =
> no-op) ; « figes constatées » = le diagnose est l'opération la plus longue.

## 4. Reconstruction post-sync (double-tampon — V-F1)

| # | Mesure | Avant V-F1 | Après V-F1 |
|---|---|---|---|
| R1 | Sync Gradle (invalidation) → premières frappes : délai de figage (s) | (référence : 11–12 s hérités) | |
| R2 | Latence p95 de complétion PENDANT la reconstruction (× nominale) | | |
| R3 | Réponses périmées servies (0 attendu — époques) | | |

## 5. Concurrence (V-F)

| # | Mesure | Valeur |
|---|---|---|
| C1 | Complétion fichier A pendant diagnose fichier B : p95 de A (ms) | p95 concurrent **459.04 / 725.05 / 943.44** (passes 1-3, médiane **725.05**) vs nominal p95 331.99–461.50 : **×1.0 / ×1.8 / ×2.8** (médiane ×1.8) — 16/17/20 diagnose(B) servis pendant la fenêtre |
| C2 | Interblocage constaté (jamais — dump si survient) | **aucun** pendant C1 — les 3 passes sont menées à terme (worker joint < 10 s, aucun dump) |
| C3 | Comptage `synchronized` résiduel (CI : `rg -c` — cible < 6 après V-F2) | **82 occurrences** au 31/10/2026 sur `3dfcc4a` (dont 46 dans `MoteurJava.kt`, 11 dans `EnvironnementCompilation.kt`) — mesure dépôt, hors appareil |

## 6. Mémoire et pression (§11)

| # | Mesure | Valeur |
|---|---|---|
| M1 | Tas moteur au repos (Mo) après indexation du corpus | **15.4** (15.4 / 15.4 / 15.3 — double `System.gc()` ; JVM du banc −Xmx2g : composant moteur, pas le produit intégré ; corpus = sonde du banc) |
| M2 | Tas après 10 min de frappe (fuite ?) | **15.0** après **500 éditions diagnostiquées** (delta **−0.3 Mo** : pas de fuite) — 500 éditions simulées, pas 10 min de frappe réelle (limite du banc autonome, consignée) |
| M3 | `onTrimMemory` respecté : caches libérés sous pression (oui/non + niveau) | |
| M4 | Taille d'APK ajoutée par le moteur (cible budget ~5 Mo ; +2 Mo prévus V-B) | |

## 7. Qualité sur appareil (échantillon du banc P1–P8)

| # | Mesure | Valeur |
|---|---|---|
| Q1 | P3 (zéro faux positif) sur 20 fichiers du corpus réel : FP constatés | |
| Q2 | P6 : suggestions insérées compilables (sur 50 insertions) | |
| Q3 | P8 : édition d'API dans A → diagnostics de B (ouvert) en < 300 ms | **44.60 ms** (médiane ; 27.82 / 52.09 / 44.60) — 1 diagnostic rendu, ciblant `zAdd`, **0 périmé inter-fichiers** → conforme < 300 ms (mesure moteur) |

## 8. Verdict et décisions

- [x] Les cibles §0.1 sont : **partiellement atteintes** (passe 1 — mesures
      moteur, bornes basses du perçu, plateforme jrt) :
      - hover < 100 ms p95 : **atteint avec marge** — F3 p95 médiane 1.17 ms ;
      - signature < 100 ms p95 : **sous la cible en borne basse** — F4 p95
        médiane 83.78 ms, marge mince (le perçu ajoute le debounce 50 ms,
        non mesuré ici : à confirmer au protocole écran 60 i/s) ;
      - complétion < 100 ms p95 : **non atteint** — F1 p95 médiane 404.87 ms,
        soit ≈ 4× le budget déjà en borne basse ; dans le fichier de 1356
        lignes, complétion p95 jusqu'à 497.92 ms (passe 2) ;
      - diagnostics de frappe < 300 ms : **sous la cible en borne basse** —
        F5 p95 médiane 168.85 ms (max 178.63) ; perçu à confirmer ;
      - « aucune fonctionnalité de base indisponible pendant l'indexation » :
        **non atteint à l'état vague 0** — S3 : la 1re requête bloque pendant
        toute la construction (cible V-B) ;
      - démarrage à chaud < 3 s : **non mesurable sans hôte** (S1, caches
        disque) ; à froid, S2 médiane 9363.86 ms construction incluse.
- [ ] Budgets à recalibrer (nouveaux chiffres) : recommandation = **ne pas
      recalibrer** — maintenir les cibles §0.1 (contrat mission, décision D5)
      et consigner la présente passe comme **baseline appareil vague 0**
      (re-passe android.jar + perçu écran avant figement). F1 (complétion de
      membres) devient l'écart perf n°1 quantifié sur appareil → à prendre en
      compte dans V-B/V-F (alerte AL9 du registre ALERTES).
- [ ] Décision de suite demandée au propriétaire : (i) re-passe avec
      `android.jar` API 34 (représentativité plateforme, S2 en tête) ;
      (ii) figer la passe 1 comme baseline vague 0 (non-régression des vagues
      suivantes) ; (iii) lancer V-A (recommandation inchangée — les écarts
      constatés relèvent des vagues déjà planifiées, aucune nouvelle vague
      proposée).

> **Rappel d'honnêteté (R5)** : toute case laissée vide parce que la mesure n'a pas
> pu être faite reste vide avec la raison écrite — aucune valeur déduite du bureau
> n'est reportée ici (facteur ART 3–5× = estimation, pas une mesure).

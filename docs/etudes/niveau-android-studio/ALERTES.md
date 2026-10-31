# ALERTES — mission « niveau Android Studio » (études/niveau-android-studio)

> Document vivant (style `docs/etudes/moteur-java-psi/ALERTES.md`) : anomalies,
> découvertes et écarts avec le prompt de mission, signalés factuellement (R6.2 :
> sans corriger ni minimiser). Ajoutés au fil de la mission ; à trier par le
> propriétaire.

## 1. Écarts entre les affirmations du prompt (§3 « hypothèses ») et le code vérifié

| # | Affirmation du prompt | Vérification faite | Statut |
|---|---|---|---|
| A1 | « ~7 options ecj surchargées » (H5) | **10 clés** posées (`PontEcj.kt:216–236` : 3 compliance + 3 « unused » + 4 « ignore ») | Écart affiné (sans gravité) |
| A2 | « 20 IDs ecj mappés sur 7 familles » (H7) | **21 IDs** → 7 familles (`FamillesProbleme.kt:83–135`, comptage ligne par ligne) | Écart affiné |
| A3 | « 82 occurrences de `synchronized` » (H13) | **82 exactement** (`rg -o --no-filename | wc -l`), dont 46 dans `MoteurJava.kt` seul | Conforme |
| A4 | « `references` confinée au document courant » (H8, écart M4) | Vrai **au niveau protocole** (`ServeurLangageJava.kt:666–685` filtre explicite) — mais le **moteur** est déjà multi-fichiers exhaustif (`ServiceUsages.kt:60–100`, rappel = oracle au banc) | **Écart majeur de lecture** : la cause racine est un filtre de parité, pas une capacité manquante — corrige la priorisation (G-013 devient quasi gratuit) |
| A5 | « ~22 600 lignes Kotlin » | 22 649 (comptage) | Conforme |
| A6 | H6 : « le diagnose rapide porte les problèmes précédents des corps masqués » | Confirmé, **mais** garde de repli documentée (repli complet si hors-corps change) et table rapide n'allumant que `unusedLocal` — le risque est le report **périmé** (re-validation absente), pas les faux « unused » (déjà couvert) | Nuancé |

## 2. Découvertes à caractère d'alerte

- **AL1 — La baseline qualité est saturée** : `bancs/baselines/completion.json`
  (18 positions, d8 1,000/1,000/1,000) ne peut plus **distinguer** une amélioration
  d'une régression sur le recall/top-1 — tout plafonne. Conséquence : la moindre
  métrique « niveau AS » exige le corpus réel (livré) avant toute décision de
  complétion. (Relayé G-034/ADR 0030.)
- **AL2 — `referencesParBalayageComplet` sert aussi aux lentilles** :
  `ServeurLangageJava.kt` (codeLens resolve, l. 969+) consomme le comptage
  multi-fichiers — la levée du confinement M4 (ADR 0028) doit vérifier l'impact
  sur `codeLens` (déjà multi-fichiers, cohérent a priori — à tester).
- **AL3 — Événementielle divergence build/IDE dans le corpus** : le corpus
  `architecture-components-samples` (BasicSample) utilise Gradle 7.5 — la vérification
  « compile avec Gradle récent » de P3 doit tolérer ce cas (JDK 17 OK documenté) ou
  l'épingler sur une branche plus récente ; à trancher à la première exécution CI.
- **AL4 — Rate-limit Maven Central rencontré pendant l'étude** : la compilation
  locale de `:java:compileTestKotlin` a échoué trois fois sur des 429 (Too Many
  Requests) au téléchargement des dépendances — l'environnement d'étude (2 cœurs /
  4 Go) n'a **pas** permis d'exécuter le banc P1–P8 localement avant push ;
  la **CI GitHub** (plus de RAM, réseau sain) reste le lieu d'exécution prévu (§1.5).
  Les tests sont livrés **compilables par construction** (API du dépôt uniquement)
  mais leur exécution n'est pas attestée ici — `[NON VÉRIFIÉ]`, traçable au run CI
  du push.
- **AL5 — `Thunderbird-android` éliminé du corpus** : le projet (ex-K-9 Mail) est
  passé à 82,8 % Kotlin au HEAD épinglé — l'hypothèse du prompt (K-9 = bon corpus
  Java) est périmée ; remplacé par gh4a/AnySoftKeyboard. Les candidats GPL éliminés
  sont listés dans l'audit §5 (AntennaPod, gpslogger, AmazeFileManager,
  LibreTorrent…).

## 3. Limites de l'étude (transparence)

- **AL6 — Android Studio non exécutable dans l'environnement d'étude** : toute
  affirmation « comment fait la référence » vient des sources listées dans
  `02-reference-android-studio.md` (doc officielle + code public, marques
  [doc]/[code]/[inférence]/NV) ; les points NV attendent les captures du
  propriétaire (protocole 06).
- **AL7 — Aucune mesure appareil** : le gabarit 07 est livré vide (R5) ; les cibles
  de vitesse §0.1 restent **indicatives** jusqu'à son retour.
- **AL8 — Efforts en j-h agent [estimés]** : les fourchettes du plan 04 suivent la
  méthode du PLAN-REECRITURE (recoupement volumétrie + vélocité N1–N8) ; aucune
  vélocité post-bascule n'a encore été mesurée sur ces périmètres (premier recalage
  prévu fin V-A).

## 4. Actions propriétaire ouvertes (hors décisions numérotées du compte-rendu)

- Captures Android Studio (protocole 06 — lots A à D).
- Passe appareil sur le gabarit 07 (conditions §1).
- Confirmation éventuelle des conditions d'artefacts JetBrains (ADR 0017 — ne
  conditionne que la vague incrémentalité, ADR 0019 option 2).

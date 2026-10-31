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

- **AL4bis — Découvertes du banc P1–P8 (mesures du 31/10, JVM bureau)** : outre les
  confirmations H4/H6, le banc a mis au jour un **bug inconnu jusque-là** : la
  complétion de types propose des classes **internes non publiques du JDK**
  (`java.util.concurrent.atomic.Striped64`, `java.util.stream.StreamShape`,
  `StreamOpFlag`, `Streams`) avec auto-import insoluble — le filtre des internes
  livré en N2 (`com.sun.*`/`jdk.*`) ne couvre pas les non-public de `java.util.*`.
  Écart à ajouter au registre : G-004 (complétion) — correction candidate vague
  V-A. De même : le quick-fix « création de méthode » en contexte typé (`return
  f()` dans une méthode `String`) insère un stub de retour `Object` et laisse une
  erreur de type — G-014, le fix n'est pas contextuel.
- **AL4 — Rate-limit Maven Central rencontré pendant l'étude** : la compilation
  locale de `:java:compileTestKotlin` a échoué trois fois sur des 429 (Too Many
  Requests) au téléchargement des dépendances (rafales parallèles de Gradle). Le
  build a **finalement réussi** en sérialisant les requêtes (`--max-workers=1`) :
  le banc P1–P8 a donc bien été **exécuté en local** (23 tests / 0 échec / 1 gate
  corpus ignorée) — les mesures sont consignées dans l'audit §1.12. La gate corpus
  réel (P3/P4/P7) reste à jouer en CI après préparation des classpaths.
- **AL5 — `Thunderbird-android` éliminé du corpus** : le projet (ex-K-9 Mail) est
  passé à 82,8 % Kotlin au HEAD épinglé — l'hypothèse du prompt (K-9 = bon corpus
  Java) est périmée ; remplacé par gh4a/AnySoftKeyboard. Les candidats GPL éliminés
  sont listés dans l'audit §5 (AntennaPod, gpslogger, AmazeFileManager,
  LibreTorrent…).
- **AL9 — Complétion de membres ≈ 4× au-dessus du budget §0.1 (mesure appareil,
  passe 1)** : F1 (`s.le¦`) p95 **médiane 404.87 ms** sur moto g06 (3 passes,
  moteur in-process, plateforme jrt) pour < 100 ms p95 perçu visés — le budget
  est dépassé **déjà en borne basse** (le perçu ne peut qu'ajouter le debounce).
  Même passe : diagnose p95 **médiane 1154.13 ms** sur `Grand.java` (1356
  lignes), S3 = **rien servi pendant la construction** (1re requête bloquée),
  C1 = dégradation **×1.0–×2.8** sous diagnose concurrent, S2 froid médian
  **9363.86 ms** (construction incluse). Points conformes : hover 1.17 ms,
  signature 83.78 ms et frappe 168.85 ms (bornes basses), Q3 44.60 ms avec
  **0 périmé inter-fichiers**, tas stable (15.4 → 15.0 Mo, pas de fuite).
  Baseline vague 0 (transcript
  `mesures/2026-10-30-passe1-moto-g06-jrt.txt`) : arbitre la décision D5 et
  oriente V-B/V-F ; à re-mesurer avec `android.jar` avant figement.

## 3. Limites de l'étude (transparence)

- **AL6 — Android Studio non exécutable dans l'environnement d'étude** : toute
  affirmation « comment fait la référence » vient des sources listées dans
  `02-reference-android-studio.md` (doc officielle + code public, marques
  [doc]/[code]/[inférence]/NV) ; les points NV attendent les captures du
  propriétaire (protocole 06).
- **AL7 — Mesure appareil partielle (passe 1 du 30/10, consignée le 31/10)** :
  le gabarit 07 est rempli sur S2/S3, F1/F3-F6, C1/C2, Q3, M1/M2 (moto g06,
  mesures moteur in-process, plateforme **jrt — android.jar absent**). Les cibles
  §0.1 sont désormais confrontées à des mesures (gabarit 07 §8 :
  **partiellement atteintes** ; F1 ≈ 4× le budget — AL9). Restent non mesurés :
  le **perçu** (protocole écran 60 i/s, debounce et publication inclus), la
  passe `android.jar`, S1/M3/M4 (hôte) et Q1/Q2 (CI corpus).
- **AL8 — Efforts en j-h agent [estimés]** : les fourchettes du plan 04 suivent la
  méthode du PLAN-REECRITURE (recoupement volumétrie + vélocité N1–N8) ; aucune
  vélocité post-bascule n'a encore été mesurée sur ces périmètres (premier recalage
  prévu fin V-A).

## 4. Actions propriétaire ouvertes (hors décisions numérotées du compte-rendu)

- Captures Android Studio (protocole 06 — lots A à D).
- Re-passe appareil du gabarit 07 **avec `android.jar`** (API 34) — la passe 1
  (jrt) est consignée (gabarit 07 §2/§3/§5-§8 + transcript `mesures/`) ; perçu
  écran 60 i/s quand un hôte CodeIDE sera disponible.
- ~~Décisions D1–D9~~ **APPROUVÉES le 31/10/2026 (GO)** — vagues lancées :
  V-A1 et V-E livrées le 31/10, **V-B livrée le 31/10 au soir (v1.4.0** —
  PV `04-plan-vagues.md` §V-B : 717 tests / 0 échec, budgets ADR tenus ;
  restes consignés : segment CIDX côté index, R.txt AAR, gate P3 corpus
  manifestes, producteur hôte du modèle v11 = prompt §4 du plan).
- Confirmation éventuelle des conditions d'artefacts JetBrains (ADR 0017 — ne
  conditionne que la vague incrémentalité, ADR 0019 option 2).

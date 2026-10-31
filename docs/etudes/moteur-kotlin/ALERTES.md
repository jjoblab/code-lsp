# ALERTES — anomalies, risques de provenance, écarts avec le prompt de mission

> Étude « moteur Kotlin » (dépôt `jjoblab/code-lsp`, branche
> `etude/moteur-kotlin`). Document vivant : toute nouvelle alerte s'ajoute
> ici (R6.5 : signaler factuellement, sans corriger ni minimiser).

## 1. Écarts entre les affirmations du prompt (§2 « État de l'existant, à VÉRIFIER ») et la réalité vérifiée

| # | Affirmation du prompt | Vérification (faite) | Statut |
|---|---|---|---|
| A-K1 | « ≈ 30 800 lignes Kotlin (80 fichiers) **+** grammaire officielle vendorée (≈ 9 900 lignes) » | Mesuré @ `bc11253` : `src/main` **total 30 782 l. / 80 f. incluant** le vendor (9 585 l. / 23 f.) ; production propre **21 197 l. / 57 f.**. Le « 30 800/80 » est exact mais **contient déjà** le vendor — le prompt invite à les additionner (≈ 40 700), ce qui serait un double comptage | **Écart de lecture** — chiffres réaffirmés : propre 21 197, vendor 9 585, total 30 782 |
| A-K2 | « ≈ 469 tests (comptage @Test, à vérifier) » | **469 méthodes `@Test` dans 53 fichiers / 15 853 l.** [mesuré, `rg --count-matches`] | **Conforme** |
| A-K3 | « grammaire vendorée ≈ 9 900 lignes, JetBrains, Apache-2.0 » | 9 585 l. / 23 f. `.kt` [mesuré] (écart −3 % — fichiers non-`.kt` de la notice exclus du comptage) ; Apache-2.0 confirmé par `LICENSE.txt`/`COPYRIGHT.txt` du répertoire vendor + notice tierce | **Conforme (précision)** |
| A-K4 | « plus gros fichiers : DiagnosticsSemantiques ≈ 1 780, SessionLangageKotlinParse ≈ 1 240, ResolutionKotlin ≈ 990 » | Mesuré : 1 784 / 1 241 / 990 — suivi de `OccurrencesKotlin` 875, `CompletionKotlin` 817, `SymbolesProjet` 759, `MembresManquantsKotlin` 731 | **Conforme** |
| A-K5 | « la résolution sémantique est heuristique » | Confirmé par les ADR elles-mêmes : surcharges par nom + arité, typage borné, smart casts positionnels, inférence par attente — la couverture est un sous-ensemble arbitraire (voir `00b-inventaire-existant.md` §2.3) | **Conforme** |
| A-K6 | « branche `etude/moteur-kotlin` ; rebase-la souvent sur `main` » | **Aucune branche `main` n'existe dans `code-lsp`** [mesuré, `git ls-remote --heads`] : seule `origin/etude/moteur-java` existe (défaut). Le commit 970cacf mentionne un push sur `origin/main` (8450bb7) mais la branche n'est plus sur le distant | **Écart** — la branche d'étude a été créée depuis `etude/moteur-java` (HEAD) ; rebaser sur `etude/moteur-java` en attendant une éventuelle recréation de `main` — décision propriétaire |
| A-K7 | « l'agent Java travaille en parallèle » | L'étude Java est **terminée et publiée** : moteur `:java` v1.0.0 (tag v1.0.0, GitHub Packages), ADR 0001–0018 pour la plupart acceptés/transformés, Java archivé côté `codeide-lsp` (ADR 0233 ancien). Pas de concurrence d'écriture constatée | **Conforme** (coordination sans conflit — périmètres disjoints) |
| A-K8 | Matrice des capacités (colonne Kotlin) : « Super-définition (expérimental) ❌ » | L'ADR 0210 de l'ancien dépôt documente un `superDefinition` Kotlin **livré** (moteur `SuperKotlinTest` 10 tests, hôte L2, harnais E9) — la matrice `LSP_MATRICE_CAPACITES.md` affiche ❌ sur la ligne Super | **Écart interne de l'ancien dépôt** — signalé factuellement ; le cahier retient les TESTS (verrouillés) comme vérité |

## 2. Risques de provenance (R6.5 — signalés factuellement, non corrigés)

- **P-K1 — Filiation documentaire GPL-3.0** : les décisions D1/D2/D5/D8 et
  le §2 « Enseignements » de `LSP_KOTLIN.md` (ancien dépôt) déclarent
  s'appuyer sur l'étude d'un **projet tiers de référence sous GPL-3.0**
  (« aucune ligne de code réutilisée » — non vérifiable sans lire du code
  GPL). Ce projet **n'a pas été consulté** pendant la présente mission
  (R6.2/R9.4 respectés). La vérification objective de similarité
  d'expression est **recommandée au propriétaire comme audit séparé**,
  confié à un rôle distinct des implémenteurs. Toute reprise (voie B/C)
  des zones dont la conception est issue de ces enseignements devra en
  tenir compte dans le registre.
  **Statut (30/10, vague 1e — factuel)** : les copies R1/R2 (zone
  synthetics, conception D5) ont été EFFECTUÉES sur ordre de poursuite de
  l'étage 1 reçu du propriétaire (« Go étage 1 ») sans audit préalable —
  la réserve est consignée au registre (manifeste §3/§6), les adaptations
  de câblage sont documentées dans les diffs du commit de livraison ;
  la recommandation d'audit DEMEURE OUVERTE et s'exerce désormais a
  posteriori.
- **P-K2 — Conditions des artefacts binaires intellij-dependencies** :
  miroir de l'alerte AL1 Java — les jars `org.jetbrains:syntax-api`
  n'embarquent pas de `META-INF/LICENSE` ; licence des sources
  Apache-2.0 ; l'hypothèse Apache-2.0 pour les **binaires** reste à
  confirmer par le propriétaire avant tout embarquement définitif.
- **P-K3 — Conformité R6.2 de l'étude** : aucune source GPL/AGPL ni de
  licence non vérifiée consultée (terrains : `code-lsp` et `codeide-lsp`
  [proprio jjoblab], `JetBrains/kotlin` [Apache-2.0], artefacts publics
  Maven Central / intellij-dependencies, spécification Kotlin et doc
  publique JetBrains/Android, spec LSP). `Kotlin/kotlin-lsp` (incubateur
  JetBrains annoncé pré-alpha) : **licence vérifiée avant lecture** —
  voir `01-architectures-analyse-kotlin.md` §0 (Apache-2.0 constaté au
  dépôt ; consultation limitée aux documents de conception publics).

## 3. Anomalies et découvertes à caractère d'alerte (issues de l'étude)

- **AL-K1 — H1/H2 du rapport faux positifs (26/10)** : la troncation du
  scan ne se reflétait pas dans `estUniversConna()` (H1) et les sources
  générées KSP/Hilt/Room sous `build/` sont invisibles (H2) — confirmés
  par reproduction. Le rapport recommande les correctifs (étape B) ;
  **l'état exact à HEAD (bc11253) n'a pas été re-vérifié test par test**
  (hors périmètre de lecture) : le cahier verrouille ce que les tests
  couvrent à ce commit.
- **AL-K2 — Grammaire figée v2.3.0** : ADR 0119 (renonciation
  `languageVersion`) — le `kt.syntax` sur du Kotlin 2.4+ est signalé
  (honnête) ; la fréquence augmentera avec l'écosystème. Le re-vendoring
  est scripté mais manuel (2×/an) et lie la montée de syntax-api à la
  chaîne d'outils (bootstrap 2.2.x).
- **AL-K3 — L'historique de correction vit dans les tests** : 24 classes
  `Reproduction*` [mesuré] — signal structurel : la résolution a été
  construite correctif par correctif ; un moteur refondé devra re-découvrir
  ces 24 familles **via le cahier** (elles y sont toutes fichées).

## 4. Écarts d'exécution de la mission (transparence)

- **E-K1 — Démarrage différé** : la mission a été envoyée le 29/10 au
  soir ; l'environnement de travail était **vierge** au 30/10 (aucun
  clone, aucun livrable) — le travail n'a commencé que le 30/10, après
  relance du propriétaire. Aucune heure de travail antérieure n'est à
  porter au crédit de l'étude ; le compte rendu final chiffre ce qui a
  été réellement produit.
- **E-K2 — Aucun appareil Android disponible** : conformément au §7 du
  prompt, seules (a) l'analyse statique ART, (b) les mesures JVM
  (limites basses) et (c) le point d'entrée appareil sont produits.
  Aucune mesure appareil n'est prétendue.
- **E-K3 — Jeton GitHub** : fourni par le propriétaire en clair dans le
  canal ; utilisé exclusivement en variable d'environnement et en URL de
  clone distant — **jamais écrit dans un fichier suivi ni commité**
  (vérifié : recherche du préfixe de jeton dans l'historique complet —
  vide). Recommandation usuelle :
  le révoquer après la mission.
- **E-K4 — Mesures JVM de bureau** : environnement bac à sable
  conteneurisé (2 cœurs effectifs constatés sur les mesures Java
  antérieures) — toute mesure est une **limite basse**, incertitude
  documentée dans `02-spike-mesures.md` (variabilité p95 ±×1,5 entre
  passes constatée par l'étude Java — AL6 Java).
- **E-K5 — Écart de procédure R9.1 pendant la vague 1c (30/10/2026)** :
  en cadrant la vague 1c (scan projet paresseux), l’implémenteur a lu par
  erreur `codeide-lsp:lsp/kotlin/src/main/kotlin/jo/lspkotlin/session/
  ScanProjet.kt` (implémentation source) — zone NOYAU en **lecture
  interdite** (R9.1 maintenu par l’ADR 0100 : le noyau se réécrit du
  cahier, en boîte noire). Le test source `ScanProjetTest.kt` était, lui,
  en consultation LÉGITIME (base du cahier, R9.5). Faits : (a) la lecture a
  porté sur l’implémentation complète (~550 l.) avant que l’interdit ne
  soit relevé ; (b) aucune ligne n’est copiée — le code livré
  (`kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/session/
  ScanProjetParesseux.kt`) est écrit neuf, dans les conventions du module,
  et l’écart est tracé dans son en-tête ; (c) la CONVERGENCE inévitable de
  certains mécanismes (vol unique, cache mtime, marche avant parse) tient
  au CONTRAT de la fiche 041 et de son test (les nommer dans les deux
  implémentations n’est pas une preuve de copie). La condition de
  clean-room STRICT étant rompue pour cette vague, un **audit de
  similarité d’expression mené par un rôle distinct des implémenteurs**
  (même principe que P-K1) est RECOMMANDÉ au propriétaire avant toute
  reprise du motif pour les vagues suivantes. Aucune autre zone du main
  ancien n’a été lue depuis la découverte de l’écart.
- **E-K6 — Test flottant du chantier Java découvert par la CI de la vague
  1c (30/10/2026)** : `testing/…/IntegrationMoteurIndexTest.
  reouvertureChaudeSansReconstruction` (ligne 230) échoue de façon NON
  DÉTERMINISTE sur une assertion de TEMPO sans tolérance
  (`chaudMs <= froidMs` — réouverture chaude pas plus lente que la
  construction à froid). Constats : échec sur la tentative 1 du run CI
  `41004282232` (commit `6df996c`, dont le diff ne touche QUE `kotlin/` et
  `docs/`) ; reproduit localement sur code inchangé (1 échec sur 4 passes
  `:testing:test --rerun-tasks`) ; le fichier est intact depuis l'étage N1
  Java (commit `ce291dc`). La relance du job a rendu la tentative 2 VERTE
  (9/9) mais le flottement demeure : chaque future livraison du dépôt
  prend le risque d'une CI rouge sans lien avec son propre périmètre.
  RECOMMANDATION au propriétaire (périmètre Java) : assouplir l'assertion
  avec une marge de tolérance (p. ex. `chaud <= froid × 1,5` ou une borne
  absolue), ou comparer des durées MÉDIANÉES sur plusieurs passes.
  **Deuxième occurrence CI (31/10/2026, clôture étage 3)** : échec sur la
  tentative 1 du run `41143338177` (commit `a9a9b50`, dont le diff ne
  touche QUE `docs/` — journal de clôture) ; relance du job → tentative 2
  VERTE (10/10). Le motif et la résorption sont IDENTIQUES à la première
  occurrence : le flottement demeure, la recommandation aussi.

- **E-K7 — Classe de risque « référence API JDK 21 dans du bytecode 17 »
  (découverte par la Passe appareil du 30/10/2026, corrigée pour :kotlin)** :
  `MutableList.removeLast()` (grammaire vendorée) compilée sous JDK 21
  émet l'InterfaceMethodref `java.util.List.removeLast` — méthode **JDK 21
  seule** — DANS du bytecode 61, QUEL QUE SOIT jvmTarget (constaté par
  expérience au compilateur 2.2.10 : les cibles 17 et 21 émettent l'une et
  l'autre la référence ; `jvmTarget` ne borne pas le pool de constantes).
  L'erreur n'est visible QUE sur un runtime sans la méthode : l'openjdk 17
  de Termux a levé `NoSuchMethodError` au premier parse, alors que bac à
  sable et CI (JDK 21) exécutaient le même jar sans erreur — **une CI JDK
  21 ne peut pas attraper cette famille**. CORRECTIF posé pour :kotlin :
  toolchain 17 (compilation/tests/bancs sous le JDK de l'appareil), garde
  `CompatibiliteJdk17Test` (pool de constantes), motif corrélé
  `List.<SequencedCollection>` dans scan-art.py (CI), garde d'expédition
  dans `assembler-kit.sh`. Vérification factuelle du périmètre voisin :
  `java-1.0.0.jar` (PUBLIÉ, tag v1.0.0) scanné **PROPRE** [mesuré, scan du
  pool de constantes — famille SequencedCollection sur java.util.List :
  aucune]. RECOMMANDATION au propriétaire (périmètre Java, chantier clos) :
  la même classe de garde avant toute future montée du moteur Java
  (l'exécution CodeIDE de :java sous un runtime < 21 partagerait le
  mécanisme — sa toolchain est 21 avec jvmTarget 17, config exacte de
  l'incident ; le scan actuel ne trouve rien, le risque est celui des
  écritures FUTURES).
- **E-K8 — Budget dex de la pile chaude (ARBITRÉ le 31/10/2026 : budget
  taille ÉLEVÉ à 6 Mo par le propriétaire — voir l'entrée d'arbitrage
  ci-dessous ; historique vague 2a, 30/10/2026 — actualisation)** : l'adoption de
  `org.jetbrains.kotlin:kotlin-metadata-jvm:2.2.10` (décodeur `@Metadata`,
  ADR 0108 — artefact épinglé à la chaîne d'outils) avait fait passer le dex
  d8 de la pile chaude (moteur + syntax-api + lang-syntax + annotations +
  kotlin-stdlib + kotlin-metadata-jvm, min-api 26) de 16 306 méthodes /
  2,69 Mo à 24 673 / 3 790 192 o (3,62 Mo) [mesuré, bac à sable, d8
  build-tools 34] ; la vague 1e l'avait porté à 24 772 / 3 815 728 o ; la
  vague 2a (socle de résolution : modèle de symboles, lecteur Java, univers
  des preuves, passe sémantique, journal explain, germe de complétion) le
  porte à **24 996 références de méthodes / 3 882 064 o (3,70 Mo)** [mesuré,
  mêmes commandes] : les seuils étage 0 (≤ 32 768 méthodes, ≤ 4 Mo) restent
  tenus (marges 7 772 méthodes / 312 Kio) mais **93 % du budget taille est
  consommé**. Les étages 2b–6 n'ajoutent que du code MOTEUR (les piles sont
  fixes) : à surveiller à chaque vague — si la marge fond, les leviers sont
  l'arbitrage Q-K2 (décodeur @Metadata maison, sans la bibliothèque), une
  demande d'interface au propriétaire sur le budget T1, ou la voie JVM
  maison pour la vue des membres (vague 2b — la consommation de
  l'analyseur `:index` public exigerait la mesure préalable de SON coût
  dex, Q-K1). Le seuil CI mesure à chaque push (aucune action requise tant
  qu'il est vert).
- **E-K8 — ARBITRAGE DU PROPRIÉTAIRE (31/10/2026) : le budget T1 est ÉLEVÉ
  4 Mo → 6 Mo** — levier « demande d'interface au propriétaire sur le
  budget T1 » actionné à la demande expresse du propriétaire avant
  l'engagement des vagues 2d-2g (« Élève le budget (E-k8) », 31/10/2026) ;
  le seuil CI taille passe à ≤ 6 Mo (le seuil méthodes RESTE ≤ 32 768 —
  moitié du mur 64K, marge structurelle du format dex, jamais négociable).
  À la clôture 2c la pile chaude mesurait 25 273 méthodes /
  3 980 148 o, soit 95 % de l'ANCIEN budget taille et 77 % du seuil
  méthodes : le nouveau budget rend **63 %** consommés côté taille — la
  marge couvre le reste de l'étage 2 (vagues 2d-2f ≈ +0,1-0,3 Mo de code
  moteur attendu, miroir des vagues 2a-2c : +0,06/+0,05/+0,04 Mo) et
  laisse de la place pour les étages 3-6. L'alerte reste OUVERTE en mode
  surveillance : chaque clôture de vague actualise le pourcentage (le
  seuil CI échoue toujours au-delà de 6 Mo) ; les leviers alternatifs
  (Q-K2 décodeur maison, Q-K1) restent consignés si le budget devait
  fondre à nouveau.
  - *Actualisation vague 2d (31/10, clôture `df77e91`)* : 25 287 méthodes /
    3 985 408 o — +14 méthodes / +5 260 o, **63 %** du budget 6 Mo et
    77 % du seuil méthodes ; la marge tient, surveillance maintenue.
  - *Actualisation vagues 2e/2f (31/10, livraisons `53da2ef` et à
    suivre)* : 2e 25 343 / 4 000 812 o (64 %) ; 2f 25 420 méthodes /
    4 030 652 o (3,84 Mo) — méthodes 78 % du plafond, taille **64 %** ;
    la marge tient pour la vague de clôture 2g, surveillance maintenue.
  - *Actualisation vague 4a (31/10, étage 4 — occurrences prouvées)* : la
    clôture 3e avait laissé le dex INCHANGÉ au octet près (25 636 /
    4 090 064 o — aucun code main touché, §9.3 du journal étage 3) ; la
    vague 4a (moteur d'occurrences + SPI navigation) le porte à
    **25 751 méthodes / 4 124 124 o (3,93 Mo)** — méthodes **79 %** du
    plafond 32 768, taille **66 %** du budget 6 Mo (+115 méthodes /
    +34 060 o) ; la marge tient pour les vagues 4b-4d (navigation de
    types et super : code moteur marginal attendu, miroir 4a),
    surveillance maintenue.

## 5. Points restés « à vérifier » (non clos par l'étude)

- Comportement GC ART (SoftReference/ReferenceQueue) — appareil requis.
- Conditions exactes des artefacts binaires intellij-dependencies
  (P-K2) — action propriétaire.
- État réel (à HEAD) des correctifs recommandés par le rapport faux
  positifs (AL-K1) — le cahier fixe le périmètre testé.
- Fréquence réelle du `kt.syntax` sur code Kotlin 2.4+ en production —
  donnée d'usage appareil, hors portée.

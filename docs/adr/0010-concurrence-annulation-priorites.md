# ADR 0010 — Concurrence, annulation, priorités

- **Statut** : **Accepté — exécuté par vagues (V-F2 le 31/10/2026,
  V-H le 02/11/2026)** — schéma B livré (verrous par périmètre,
  comptage `synchronized(` 99 → 0, cible < 6 tenue) ; le chaînon
  manquant du branchement PROTOCOLE livré en V-H : la sémantique
  3 voies du scheduler (« conservé » ci-dessous) est TRANSPOSÉE à la
  couche protocole (`soumettrePrioritaire` — 29 méthodes de document
  câblées), après qu'un retour appareil a mesuré la complétion servie
  29–87 s derrière la file du fil de lecture lsp4j (PV
  `mesures/2026-11-02-vh-repartition.txt`). L'époque C (swap
  d'environnement) est livrée par V-F1 (double-tampon).
- **Date** : 2026-10-28

## Contexte

§5-D11 : « comment remplacer le verrou global actuel ? ». Faits mesurés et constatés :

- **Statu quo** : `EcjCompilerEngine` ( 2 417 l. ) = **24 méthodes synchronized + 2 blocs** ( 17 public — le prompt annonçait ≈ 20, écart affiné ) ; pivot `resolveUnit` protégeant `EcjParseCache` ( non thread-safe ) et l'environnement de jars partagé. `EcjEngineScheduler` ( 487 l. ) : 3 lanes mono-thread ( interactive 3 s, analysis 15 s, diagnostics 15 s ) + supersession par fichier + watchdogs + `CancelToken` ( deadline + interrupt ). W8 : une complétion du module A bloque le module B ; le moteur par module v0.97.0 ( `ConcurrentHashMap`, ordre this → contexte ) en est l'étage 1.
- **IntelliJ** : 3 couches — API `ThreadingSupport` ( détachable ), impl IDE `NestedLocksThreadingSupport` ( 2 113 l. + artefact `rwmutex-idea`, EDT, coroutines — non détachable ), headless `MockThreadingSupport` ( mono-thread, **aucun verrou utile** : embarquer la pile headless n'achète aucune primitive ). Annulation = Job coroutine → `Cancellation.checkCancelled` → PCE ; `NonBlockingReadActionImpl` = annuler-c'est-rejouer ( NBRA ) : lecture coupée par un écrivain → PCE → re-soumission après lui.
- **ART** : awt/swing/VarHandle/MethodHandles denses dans platform-impl ( D11 ) — le verrouillage complet d'IntelliJ est indissociable de son modèle UI.

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule) : `platform/core-api` `ThreadingSupport.kt` ( 412 l. ), `actions.kt` ; `platform-impl`/`locking-impl` [hors sparse, git cat-file] `NestedLocksThreadingSupport` ( 2 113 l. ), `NonBlockingReadActionImpl` ( 1 141 l. ), `ApplicationImpl` ; `platform/util` `Cancellation.java`, `CoreProgressManager`.
- `jjoblab/codeide-lsp` @ `cb809e14` : `EcjCompilerEngine`, `EcjEngineScheduler`, `CancelToken`, `EcjParseCache` — comptages synchronized ( 24+2 ) mesurés sur le code.
- `jjoblab/codeide-tooling` @ `b60f7f0` : `HeapMonitor.kt` ( non pertinent ici, lu pour D12 ).

## Options ( les trois schémas de remplacement proposés par l'étude D11 )

**Schéma A — RW multiple-lecteurs + file unique écrivain ( calque IntelliJ ).** Exige que tout état partagé devienne thread-safe en lecture ( l'AST ecj ne l'est pas → verrous par arbre ou snapshots immuables ) ; n'apporte de gain que si le multi-fichiers concurrent devient dominant. **À mesurer avant** ( risqué, gros chantier ).

**Schéma B — verrous par périmètre ( fichier/module ), écrivain rare ( RECOMMANDÉ ).** Remplacer le moniteur `this` par des verrous par fichier ( ou *striped* ~64 seaux ) autour de `resolveUnit`/`EcjParseCache` ; `EcjCompilerEnvironment` sous verrou séparé ( amorcé : `indexLock` :1484 ) ou immuable-échangé ; ordre global **fichier → module → index** ; workers plafonnés à 2–3 ( ART 3–5× ). Gain direct : la diagnose du fichier A ne bloque plus la complétion du fichier B. Coût : quelques centaines de lignes.

**Schéma C — époque/génération ( copy-on-write de l'environnement ).** Compteur `volatile long epoch` bumpé à chaque changement de classpath ; lecteurs : capture au départ, re-validation à la publication, rejet par `OperationCanceledException` si périmé ( l'équivalent ciblé du `PsiModificationTracker` sans sa grossièreté ) ; écrivain : construction en tâche de fond puis **swap atomique** du champ `volatile index` ( déjà volatile : `EcjCompilerEngine.java:232` ). **Cumulable avec B.**

**Option « ne rien changer »** — verrou global : conserve W8 ; repli permanent derrière drapeau.

## Critères et mesures

- Verrou actuel : 24 synchronized + 2 blocs ( comptage sur le code, D11 ) ; latences de frappe bureau p50/p95 30/64 ms ( 20 jars ) → 128/150 ms ( 250 jars ) — `LSP_JAVA.md` §4 ; ART ×3–5 ⇒ p95 appareil estimé ~200–750 ms ( estimation ).
- Le scheduler maison couvre déjà ~60 % de la sémantique NBRA ( supersession, timeout, lanes ) — manquent : granularité de verrou, priorité écrivain + rejeu, époque d'invalidation.

## Décision proposée

**Schéma B en premier** ( verrous par périmètre, ordre global, workers 2–3 ), **puis C** ( époque + swap atomique ) — les deux cumulables ; A seulement si les mesures post-B montrent un besoin multi-fichiers dominant non couvert. Le scheduler 3 lanes est conservé ( il est déjà l'ordonnanceur NBRA ) ; l'annulation reste `CancelToken` ( deadline + interrupt ), complétée d'un point d'entrée « rejeu après écrivain » inspiré NBRA ( resubmission ).

## Conséquences

- Une diagnose/une complétion par fichier cessent de se bloquer mutuellement ( W8 résolu au niveau fichier ) ; l'époque rend l'invalidation de classpath non bloquante ( plus de vidage sous verrou global ).
- Discipline à respecter partout : **ordre de verrou fichier → module → index** ( risque d'interblocage sinon ) ; plafond de workers ( ART ).
- Le comptage synchronized doit décroître à chaque vague ( métrique de suivi CI possible : `rg -c synchronized` ).

## Plan de migration progressive

1. Vague « verrous B » : extraction du périmètre fichier ( drapeau `concurrence.verrous-b` ) — d'abord diagnostics ( lane 3 ) et complétion ( lane 1 ) en parallèle sur fichiers distincts.
2. Vague « époque C » : swap d'environnement au changement de classpath ( drapeau `concurrence.epoque` ) ; rejeu des lectures coupées.
3. Retour arrière : chaque drapeau retombe sur le moniteur global actuel ( inchangé pendant la migration ).

## Tests de non-régression et critères de sortie

- Test de charge déterministe : complétion fichier A + diagnostics fichier B concurrents — p95 de chaque ≤ p95 séquentiel + 10 % ; zéro interblocage ( timeout + dump verrous en échec ).
- Époque : changement de classpath pendant rafale de requêtes → 0 réponse périmée servie ( vérification par marqueur de génération dans les réponses de test ).
- Critère de sortie : comptage synchronized < 6 méthodes résiduelles ( pivots d'initialisation ).

## Risques

- **R1 — Interblocage par violation d'ordre** : ordre global documenté + test de chaos multi-threads en CI.
- **R2 — `EcjParseCache` non thread-safe** : verrous par fichier sur le cache, ou migration en `ConcurrentHashMap` de paires ( arbre + verrou ) — mesurer les deux.
- **R3 — ART monocœur bas de gamme** : workers plafonnés, bénéfice réel à re-mesurer sur appareil ( gabarit spike ).

## Exécution V-H — le branchement du protocole (02/11/2026)

La décision ci-dessus disait « le scheduler 3 lanes est conservé » —
mais la réécriture avait laissé le chaînon au sol : au protocole,
CHAQUE requête était calculée SYNCHRONEMENT sur le fil de lecture
lsp4j. Le retour appareil du 01/11/2026 (CodeIDE 0.205.2, moto g06,
`codeide-logs-2026-11-01-221628.zip`) l'a mesuré : le client émet
complétion + inlayHint + surbrillances (+ codeAction sur idle) à
CHAQUE frappe ; la complétion rendait ses 120 items 29 s et 87 s plus
tard, timeout client 5 s — RIEN ne s'affichait (l'utilisateur voyait
« liaison. » et « greeter. » muets alors que la résolution était
JUSTE).

Le correctif transpose les voies du scheduler à la couche protocole :

- `OrdonnanceurMoteur.soumettrePrioritaire(fichier, priorite,
  calcul, remplacement)` — 3 voies (0 FRAPPE : complétion, aide de
  signature, fin d'instruction ; 1 NAVIGATION ; 2 CONFORT) ;
- **supersession à VALEUR** : la tâche remplacée se solde par la
  réponse VIDE honnête du type — jamais une annulation (le client
  attend une réponse pour son identifiant) ; une FRAPPE rend caduques
  les voies inférieures EN ATTENTE du même document ;
- **exclusion par fichier toutes voies** (contrat V-F2 inchangé) ;
  clé null = durable ;
- **capture du document à la soumission + vérification d'époque à la
  livraison** : l'invariant « 0 réponse périmée » (critère de sortie
  ci-dessus) tient sous le nouveau parallélisme ;
- `exit()` solde les requêtes en attente par leur réponse vide.

Verrous : `RepartitionPrioriteeVhTest` (5 — voies, supersession à
valeur, frappe-caducité, exclusion par fichier, arrêt) +
`TempeteAppareilVhTest` (3 — au PROTOCOLE, boucle locale LSP4J : la
tempête du journal rejouée ne perd ni n'échoue aucune future, la
complétion rend les membres d'une classe du projet PENDANT la
tempête, péremption honnête, arrêt propre). Reste : la mesure APPAREIL
du service pendant la tempête réelle (gabarit 07 — propriétaire).

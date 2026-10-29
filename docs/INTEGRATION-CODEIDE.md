# INTÉGRATION CODEIDE — audit des logs appareil et plan du branchement complet

> **Rôle** : mise en œuvre de l'objectif propriétaire du 30/10/2026 — «
> l'intégration complète du nouveau lsp-java, que toutes les
> fonctionnalités soient bien connectées et fonctionnent, cohérentes ».
> Ce document suit [`BASCULE-CODEIDE.md`](BASCULE-CODEIDE.md) (publication
> v1.0.0 EFFECTUÉE le 30/10 au soir ; rails CodeIDE poussés `8450bb7`) :
> il audite l'état réel de l'intégration **à partir des logs appareil
> fournis par le propriétaire le 30/10/2026**, rappelle le **chaînon
> restant** (l'adaptation de `:lsp:host`, bascule §3.2.2) et fixe les
> critères d'acceptation « tout connecté, cohérent ». Contexte déposé le
> même jour : le moteur Java vendored de `codeide-lsp` est **ARCHIVÉ**
> (ADR 0233 de ce dépôt, commit `bc11253` poussé) — `code-lsp` est
> l'habitat unique du LSP Java de CodeIDE ; Kotlin et XML y resteront
> jusqu'à leur migration ou réécriture (décision propriétaire).

## 1. Ce que disent les logs — l'état de l'intégration AUJOURD'HUI

Appareil : **CodeIDE 0.202.0** (build 20200, debug) — motorola moto g06
(Android 15, API 35), arm64-v8a, fr-HT. Deux sessions : `f29776b5`
(ouverture du projet `1679d68e` à 16:13 le 29/10, heure appareil) puis
`c8737d73` (relance à 16:31, ~19 min plus tard). **Moteur en service : le
vendored** — CodeIDE 0.202.0 est la montée codeide-lsp 1.16.0 (commit
`54e99c6` de CodeIDE) et le chaînon restant n'est pas encore branché : la
coordonnée `jo.codeide.lsp.java` n'est consommée par personne. Ces logs
décrivent donc la **ligne de base** de l'intégration : les écarts qu'ils
montrent traverseront la bascule et doivent être traités pour atteindre
l'objectif de cohérence (bascule §3.1.3 : ce ne sont pas des régressions
de la bascule, ce sont des écarts d'intégration).

### 1.1 Constats positifs — le socle tient

| Constat | Preuve (log) |
|---|---|
| Pipeline sync Gradle complet et fonctionnel | orchestrateur lancé (pid 31331) → `gradle-server 0.50.0` → handshake accepté `app 0.202.0` (build, sync, tasks, dependencies, model, cancel, heap) → connexion Tooling API ouverte |
| État sync restitué depuis le cache | « empreinte identique — état sync restitué » + « session LSP nourrie depuis le cache (2 module(s)) » |
| L'hôte orchestre les trois langues | « session LSP ouverte » / « session LSP Kotlin ouverte » / « session LSP XML ouverte » |
| Découverte progressive des langues | « LspProject configuré (Java) » → « (Java + Kotlin) » → « (Java + Kotlin + XML) » |
| Connexion LSP Java opérationnelle | « LSP Java connecté » — 13 occurrences sur les deux sessions (thread `cel-lsp-1`) |
| Alerte SLF4J du gradle-server | cosmétique (NOP logger du démon), aucune incidence fonctionnelle |

### 1.2 Écarts (E1–E4) — la matière de l'intégration

| # | Écart (log exact) | Responsable | Lecture |
|---|---|---|---|
| **E1** | WARN Editor : « cible de go-to introuvable dans l'arbre : `app/build/generated/data_binding_base_class_source_out/debug/out/com/example/monip/databinding/ActivityMainBinding.java` » (16:15, ~2 min après l'ouverture) | **client** (arbre d'indexation) | le moteur RÉSOUT la référence databinding vers sa source GÉNÉRÉE sous `build/` — la résolution est correcte ; c'est l'arbre de l'éditeur qui n'indexe pas `build/generated` |
| **E2** | WARN LspEditeur : « source décompilée impossible : pas de projet LSP » (16:22) | **client** (cycle de vie) | demande de décompilation ~47 s AVANT la première configuration « LspProject configuré » — le service dépend d'une session LSP qui n'existe pas encore |
| **E3** | WARN LspEditeur : « source décompilée indisponible : java.lang.String » (6 ms après E2) | **client** (même tentative qu'E2) | sans projet, pas de classpath : le FQCN ne résout pas et `decompiledSource` répond honnêtement null (contrat §9.2 du `USAGE.md`) |
| **E4** | session `c8737d73` : 10 × « LspProject configuré » et 10 × « LSP Java connecté » en ~1,4 s pour un projet de 2 modules (dont 6 reconfigurations au jeu de langues final) ; 3 × « caches d'arbre libérés (pression mémoire) » sur l'ensemble des logs | **hôte** (cycle de vie des sessions) | churn de reconfiguration : chaque re-configuration semble rouvrir une session LSP Java ; la pression mémoire consécutive sur un moto g06 en est le symptôme visible |

### 1.3 Lectures transversales

- **Aucun témoin `serverInfo` dans ces logs.** Le témoin de branchement de
  la bascule (§4 : `serverInfo.version` = 1.0.0) n'est aujourd'hui
  observable nulle part côté client — la connexion devrait le consigner.
- **E4 quantifié contre le moteur neuf** : le noyau pèse ~16 Mo de tas au
  repos (banc `:java:bench`, §12 du `USAGE.md`) — dix instances vivantes,
  c'est ~160 Mo pour un appareil d'entrée de gamme. L'adaptation de l'hôte
  ne peut pas transposer le churn tel quel : l'invariant ADR 0211 de
  `codeide-lsp` (« nourrissage moteur unique par sync ») doit être
  préservé quelle que soit l'option d'architecture retenue (§3.3 ci-après).
- **E2/E3 ne sont pas des échecs du moteur** : le flux « source
  décompilée » existe côté client (tag `LspEditeur`) et côté moteur neuf
  (§9.2 : `definition` → `jdt://decompiled/<fqcn>` → `decompiledSource`
  → tampon lecture-seule, `source` null honnête si introuvable) — il
  manque la garde d'attente de session côté client.
- **E1 traversera la bascule inchangé** (et doit le faire) : le moteur
  neuf résout de la même manière vers le généré ; c'est le traitement
  client de la cible qui fait la différence entre un WARN muet et une
  navigation qui aboutit.

## 2. Le chaînon restant — l'adaptation de `:lsp:host` (rappel mesuré)

Rappel de la bascule §3.2.2 (surface mesurée sur `codeide-lsp` @
`35234b9`, v1.22.1 — l'archivage `bc11253` ne change rien à cette
surface, il fige l'état) :

- **4 fichiers main** de `:lsp:host` : `SessionLsp.kt` (858 l. —
  construit `JavaLanguageServer(environnement) {…}` par connexion LSP,
  possède le `CompilationEnvironment`, `dispose()` à la fermeture),
  `OrchestrateurLsp.kt` (453 l. — `remplacerContexteModule` sur l'env
  partagé), `AdaptateurIndexNoms.kt` (170 l. — pont vers
  `jo.lspjava.api.IndexNoms`), `KotlinLanguageServer.kt` (1594 l. —
  import `NavigationTarget` seul) + **5 fichiers de test** (141 tests
  d'hôte) ;
- **correspondances de types** : `IndexNoms` →
  `jo.codeide.lsp.java.IndexNoms` (constructeur de `MoteurJava`),
  `NavigationTarget` → `CibleNavigation` (paquet `navigation`),
  `LanguageServer` lsp4j inchangé (`ServeurLangageJava` l'implémente —
  la surface publique `SessionLsp.serveur()` tient) ;
- **dépendances** : `api(project(":lsp:java"))` →
  `api("jo.codeide.lsp.java:jo-codeide-lsp-java:1.0.0")` + compagnon
  `runtimeOnly` ; lsp4j `api` inchangé (0.22.0 imposée par cel-lsp,
  moteur binaire sous 0.22.0 — même recette que le vendored,
  `AlignementLsp4jTest`) ;
- **écarts architecturaux** : (1) le vendored partage un environnement
  VIVANT (un `CompilationEnvironment` muté par l'orchestrateur) là où le
  neuf expose `ServeurLangageJava(fabriqueMoteur, rappelArret)` — moteur
  FIGÉ à `initialize` ; (2) le pendant exact existe —
  `jo.codeide.lsp.java.EnvironnementCompilation` (contextes par module,
  sémantiques replaceAll fiche 034, routeur fichier→module fiche 047,
  index partagé, auditeurs) — mais il n'est PAS câblé dans
  `ServeurLangageJava` ;
- fourchette honnête : **3–6 j-h**, l'arbitrage d'architecture (§3.3)
  appartenant au propriétaire — qui a posé la direction le 30/10 :
  « tout connecté, cohérent ».

## 3. Traitement des écarts dans l'intégration

### 3.1 E1 — go-to vers les sources générées (databinding)

La cible vit sous la racine du projet (`app/build/generated/…`), donc
accessible via SAF ; elle est simplement hors de l'arbre d'indexation de
l'éditeur. Options, toutes côté CodeIDE (le moteur neuf ne change rien —
sa résolution est correcte et doit le rester) :

1. (a) **indexer `build/generated/**` pour la navigation seule** (cibles
   de go-to ouvrables, sans diagnostics ni complétion sur le généré) —
   recommandé : c'est le comportement d'Android Studio (les sources
   générées sont navigables, en lecture seule) ;
2. (b) repli « ouvrir hors arbre » : si la cible du go-to existe sur le
   disque, l'ouvrir en lecture seule sans exiger l'indexation préalable ;
3. (c) extension serveur servant le contenu du fichier généré (sur le
   modèle de `decompiledSource`) — écarté en première intention : le
   fichier existe déjà côté client, le servir deux fois compliquerait
   pour rien.

En attendant (a) ou (b), le WARN reste une **limite connue documentée**,
pas une régression.

### 3.2 E2/E3 — « source décompilée » avant session prête

Le contrat moteur est déjà honnête (`USAGE.md` §9.2 : `source` null si
le FQCN est introuvable ; §11 : le routeur N1 résout sources ouvertes →
jars du classpath → jrt → stubs, FQCN longest-match). Avec un classpath
synchronisé, `java.lang.String` résout (android.jar API 34 le contient).
Le traitement est client : **file d'attente / retry jusqu'à session LSP
prête** (ou déclenchement de la session à la demande), avec retour
utilisateur explicite (« session LSP en préparation ») plutôt qu'un WARN
muet. Après bascule, la même passe doit être rejouée : `definition` sur
un type binaire → `jdt://decompiled/<fqcn>` → `decompiledSource` →
tampon lecture-seule.

### 3.3 E4 — churn de sessions et arbitration d'architecture (au propriétaire)

Les données des logs plaident pour traiter le cycle de vie AVANT de
brancher le moteur neuf : 10 ouvertures de session en 1,4 s (2 modules),
6 reconfigurations au jeu de langues final, 3 libérations de caches sous
pression mémoire. Deux options héritées de la bascule §3.2.2 :

1. **(A) fabrique figée** : `SessionLsp` passe une fabrique qui consulte
   l'état sync courant à `initialize` ; chaque (re)connexion fige un
   moteur neuf. Simple, fidèle au design « moteur figé » — mais sous le
   régime de reconfiguration observé, elle reconstruit moteur + index à
   chaque re-connexion (≈16 Mo l'unité) sauf à debouncer/réutiliser côté
   hôte ;
2. **(B) routeur multi-modules au protocole** : câbler
   `EnvironnementCompilation` dans `ServeurLangageJava` — UNE instance
   par projet, les reconfigurations remplacent les contextes (replaceAll,
   fiche 034) via le routeur fichier→module (fiche 047), index partagé.
   Plus fidèle à l'architecture de l'hôte vendored (env vivant) et à
   l'invariant ADR 0211 de `codeide-lsp` (« nourrissage moteur unique
   par sync ») ; demande une évolution de la façade neuve.

L'arbitrage appartient au propriétaire. Quoi qu'il en soit : l'invariant
« une session LSP Java vivante par projet, pas de reconstruction moteur à
chaque reconfiguration » est un **critère d'acceptation** (§5, critère 6)
— le moteur neuf est assez rapide (p50 diagnose rapide 1,5 ms) pour que
la cohérence se joue entièrement dans le cycle de vie hôte.

### 3.4 Écarts de capacités assumés à documenter côté client

Deux deltas délibérés du moteur neuf (§13 du `USAGE.md`) doivent être
vérifiés côté consommation cel-lsp pour éviter une action morte ou une
attente silencieuse : **semanticTokens non annoncé** (requête tolérante
vide — le client doit conserver sa coloration syntaxique de repli) et
**organisation des imports** (API moteur §7.17 uniquement — pas encore
d'action `source.organizeImports` au fil LSP). Les capacités servies mais
non consommées restent suivies dans `docs/LSP_ECARTS_CLIENT.md` du dépôt
`codeide-lsp` — la liste de contrôle s'inverse après bascule : vérifier
que tout ce que le moteur neuf ANNONCE est bien consommé.

## 4. Plan d'intégration (I0–I6)

| Étape | Contenu | Sortie |
|---|---|---|
| **I0** Préparation | re-vérifier les rails au HEAD courant de CodeIDE (`8450bb7` : dépôt Packages `jo.codeide.lsp.java` + filtres + mavenLocal filtré + composite opt-in) ; checkout `codeide-lsp` archivé (`bc11253`) ; oracle du banc croisé disponible (`scripts/preparer-oracle.sh`, épinglage `cb809e14`) | préambule vérifié, exigence §2 de la bascule |
| **I1** Adaptation de `:lsp:host` (le chaînon) | arbitrage §3.3 (A/B) PUIS les 4 fichiers main + 5 fichiers de tests, dépendance `api(project(":lsp:java"))` → coordonnée + compagnon `runtimeOnly` | `:lsp:host` compilé contre la façade `jo.codeide.lsp.java.*` |
| **I2** DEV sans jeton | `publishToMavenLocal` de code-lsp (rails : mavenLocal filtré `jo.codeide.lsp.java`) + `publishToMavenLocal` de `:lsp:host` adapté → construire CodeIDE, vérifier `serverInfo = { "name": "lspjava", "version": "1.0.0" }` au premier lancement | témoin de branchement observé côté client (à CONSIGNER dans les logs — aujourd'hui absent, §1.3) |
| **I3** Non-régression hôte | 141 tests d'hôte rejoués contre la façade neuve (ADR 0015 §Tests) ; `AlignementLsp4jTest` (0.22.0 consommée, binaire 0.21.2) ; CI `codeide-lsp` verte ; publication de l'`:host` adapté (version `codeide-lsp`) | `:host` publié sur Packages |
| **I4** Bascule de consommation | bump du catalogue CodeIDE vers l'`:host` adapté ; incrément de version CodeIDE selon sa convention de release | CodeIDE servi par le moteur neuf |
| **I5** Passe appareil complète | fiches 004/010/039 sur projet Android réel (scénario de la bascule §3.1.2) ; re-vérification E1–E4 (go-to généré, décompilée, cycle de vie/pression mémoire) ; témoin `serverInfo` dans les logs | procès-verbal d'appareil, écarts restants classés |
| **I6** Retrait du vendored | après « 2 versions sans régression » (règle ADR 0012–0014) : désactivation de `:lsp:java` + `:lsp:ecj-art` du build `codeide-lsp` (l'archivage ADR 0233 est déjà en place) | habitat unique effectif |

## 5. Critères d'acceptation « tout connecté, cohérent »

1. **Témoin** : `serverInfo = { "name": "lspjava", "version": "1.0.0" }`
   consigné côté client à CHAQUE connexion LSP Java (le log existe —
   c'est la ligne « LSP Java connecté » qui gagnera le témoin).
2. **Capacités** : chaque capability annoncée à `initialize` correspond à
   un usage cel-lsp réel ; les deux deltas assumés (semanticTokens non
   annoncé, organizeImports hors fil LSP) vérifiés côté client — aucune
   action morte, aucune attente silencieuse.
3. **Extensions expérimentales** : `superDefinition` (§9.1) et
   `decompiledSource` (§9.2) répondent sur appareil ; `java.lang.String`
   résout avec classpath synchronisé (routeur N1, §11).
4. **E1** : go-to vers source générée databinding — cible ouvrable
   (indexation ou repli client) OU WARN reclassé limite connue documentée.
5. **E2/E3** : plus aucune demande de décompilation ne tombe sur « pas de
   projet LSP » sans file d'attente/retry ni message explicite.
6. **E4** : une session LSP Java vivante par projet ; pas de
   reconstruction moteur à chaque reconfiguration ; plus de « caches
   d'arbre libérés (pression mémoire) » en régime normal ; l'invariant
   ADR 0211 (nourrissage moteur unique par sync) tient côté hôte adapté.
7. **Filets** : CI code-lsp 6/6 verte sur les commits suivants (banc
   croisé bloquant, oracle `cb809e14`) ; 141 tests d'hôte verts contre
   la façade neuve ; `AlignementLsp4jTest` vert.
8. **Appareil** : passe de non-régression (fiches 004/010/039) sur
   projet Android réel, procès-verbal consigné.

## 6. Risques et retour arrière

- **lsp4j** : le client impose 0.22.0, le moteur est binaire sous 0.22.0
  — même recette que le vendored, filet `AlignementLsp4jTest` (I3).
- **Empreinte mémoire** : l'option d'architecture §3.3 conditionne
  l'empreinte (fabrique figée multipliée vs routeur unique) — le
  critère 6 tranche en pratique.
- **Régression** : substitution inverse en une ligne
  (`CodeIDE/settings.gradle.kts`, bascule §3.3) — le vendored archivé
  reste au build et publiable jusqu'au retrait (ADR 0233 de
  `codeide-lsp`) ; l'oracle du banc croisé reste joignable (re-pin par
  PR dédiée uniquement, §12 du `USAGE.md`).
- **Périmètre** : les LSP Kotlin et XML ne bougent pas dans ce plan —
  ils restent servis par `codeide-lsp` (habitat unique Java seulement,
  conformément à l'instruction du propriétaire du 30/10/2026).

## 7. Exécution (30/10 au soir) — arbitrage tranché, I0–I3 exécutés

**Arbitrage §3.3 : OPTION B** (routeur multi-modules AU PROTOCOLE) —
motif de la décision : le critère 6 d'acceptation tranche en pratique
(« une session LSP Java vivante par projet ; pas de reconstruction
moteur à chaque reconfiguration ; l'invariant ADR 0211 tient côté hôte
adapté ») ; sous le régime E4 mesuré sur appareil (10 ouvertures de
session en 1,4 s pour 2 modules), l'option A (fabrique figée)
reconstruirait moteur + index à chaque re-connexion (≈16 Mo l'unité) et
violerait le critère — la direction propriétaire « tout connecté,
cohérent » du 30/10 impose B.

| Étape | État | Preuve |
|---|---|---|
| **I0** Préparation | **FAIT** | rails vérifiés au HEAD (`code-lsp` 62dbbee, `codeide-lsp` bc11253, `CodeIDE` 8450bb7) ; oracle du banc croisé présent (`.oracle/`, empreintes vérifiées) |
| **I1** Adaptation de `:lsp:host` | **FAIT** | 4 fichiers main + 5 fichiers de test adaptés (surface réelle mesurée en cours de chantier : `SessionLsp`, `OrchestrateurLsp`, `AdaptateurIndexNoms`, `KotlinLanguageServer` + `NavigationCible` NOUVEAU ; tests : `OrchestrateurLspTest`, `CapacitesLspPariteTest`, `harnais/OutilsHarnais`, `harnais/ServeurStdioJava`, `utilisateurs/ExperienceU2ViewsE2ETest`) + `settings.gradle.kts` (rails mavenLocal filtré / Packages / composite opt-in) + `lsp/host/build.gradle.kts` (coordonnée 1.1.0 + compagnon runtimeOnly) |
| **I2** DEV sans jeton | **FAIT** | `publishToMavenLocal` de code-lsp v1.1.0 (`version.properties=1.1.0` dans le jar) ; l'hôte résout la coordonnée depuis mavenLocal et COMPILE contre la façade `jo.codeide.lsp.java.*` ; le montage complet (BoucleLocale équivalent) est couvert par la fiche 066 côté moteur |
| **I3** Non-régression hôte | **FAIT** | **257 tests d'hôte VERTS** contre la façade neuve — dont les scénarios E2E stdio du harnais (D1 live-republish après sync, D1bis ViewBinding généré, D2 définition inter-fichiers, D4 racine GENERATED, D5 bindings ecj) et `AlignementLsp4jTest` ; côté moteur : 631 tests verts + **banc de parité croisée VERT** (oracle épinglé, baseline signée inchangée) |

**Corrections apportées AU MOTEUR pour l'intégration** (v1.1.0 —
chacune mesurée par un test) :

1. **Régime vivant** — `ServeurLangageJava(environnement) { rappel }` :
   résolution du moteur PAR REQUÊTE (routeur fiche 047 relu à chaque
   requête), services dérivés à FOURNISSEURS (publicateur résout au TIR,
   renommage par document, symboles d'espace aux racines des contextes),
   `decompiledSource` sonde les moteurs VIVANTS (motif ADR 0215),
   sonde publique `environnement()` — garde : **fiche 066** (7 tests :
   routage par module, repli défaut, diagnostics routés, reconfiguration
   à chaud SANS re-initialize, invariant ADR 0211 à travers 5 syncs,
   sonde vivants, live-republish) ;
2. **Contrat de sérialisation `superDefinition`** — `CibleSuperRpc`
   aligné sur le vendord `{path, start, end, label}` (miroir exact du
   client cel-lsp — la v1.0.0 n'aurait transporté que `{path, libelle}`
   et le picker client aurait perdu offsets et libellé) — verrouillé
   côté fil par l'enrichissement de la fiche 054 ;
3. **LIVE-REPUBLISH** — auditeur de contexte : chaque remplacement
   EFFECTIF republie les documents OUVERTS du module remplacé (motif
   `ContextListener` du vendord — le scénario E2E D1 de l'hôte
   l'exigeait) ;
4. **`cheminDeUri` double forme** — `file:/path` (`File.toUri()` du
   client/harnais) admis en plus de `file:///path` : sans elle, le
   ROUTEUR recevait l'URI BRUTE, rien ne routait (définitions vides,
   moteur défaut au lieu du module — MESURÉ par les échecs D1–D5 du
   harnais avant correction) ;
5. **Annonce `experimental`** — `superDefinitionProvider` /
   `decompiledSourceProvider` annoncées, miroir du vendord (le client
   conditionne ses entrées UI à cette annonce — sans elle les boutons
   disparaîtraient silencieusement) ; TABLE_JAVA du test de parité L0
   mise à jour en miroir des capabilities réelles du moteur réécrit.

**Reste** : **I4** (bump du catalogue CodeIDE vers l'`:host` 1.23.0
publié + release CodeIDE), **I5** (passe appareil complète — fiches
004/010/039, re-vérification E1–E4, témoin `serverInfo = 1.1.0` dans les
logs), **I6** (retrait physique du vendored après « 2 versions sans
régression »). Le retour arrière reste 1 ligne (substitution inverse) ;
`:lsp:java` vendord reste au build (gelé, plus consommé par AUCUN
module).

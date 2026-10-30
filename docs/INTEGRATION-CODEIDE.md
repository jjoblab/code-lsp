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

**Publication EFFECTUÉE** : tag **`v1.1.0`** (annoté, `fcb636b`) →
workflow Publication **vert** (run `40972384002`) ; les artefacts
`jo-codeide-lsp-java:1.1.0` + compagnon `ecj-art:1.1.0` résolvent sur
`maven.pkg.github.com/jjoblab/code-lsp` (POM vérifié, HTTP 200). CI de la
branche verte sur les deux commits du chantier (runs `40970906201`,
`40972359111`). Premier passage du run `40970928331` : échec CI sur
l'enrichissement 054 — le test n'avait tourné qu'en CI (gate android.jar
ouverte là, ignorée localement) et la fixture `@TempDir` de la fiche
était piégée (champ RÉINJECTÉ par JUnit après le `@BeforeAll`, sur un
AUTRE répertoire que le paramètre : les relectures disque post-@BeforeAll
lisaient un répertoire VIDE) — corrigé par fixture racine MANUELLE
(`fcb636b`, motif Fiche026/066) ; re-tag et publication au second
passage. Côté hôte : `codeide-lsp` poussé (`ac7a953`, v1.23.0).

**Reste** : **I4** (bump du catalogue CodeIDE vers l'`:host` 1.23.0
publié + release CodeIDE), **I5** (passe appareil complète — fiches
004/010/039, re-vérification E1–E4, témoin `serverInfo = 1.1.0` dans les
logs), **I6** (retrait physique du vendored après « 2 versions sans
régression »). Le retour arrière reste 1 ligne (substitution inverse) ;
`:lsp:java` vendord reste au build (gelé, plus consommé par AUCUN
module).

## 8. Exécution (31/10 au matin) — I4 EFFECTUÉ : le moteur neuf SERT CodeIDE

**codeide-lsp v1.23.0 publiée** (le chemin fut heurté — cinq runs CI
rouges avant le vert, chacun diagnostiqué et corrigé) :

| Run | Échec | Correction |
|---|---|---|
| `40971065006` (ac7a953) | résolution moteur 1.1.0 introuvable en CI (mavenLocal/Google/Central sondés, PAS de Packages) | `a8ddaaf` : secret `CODEIDE_PACKAGES_PAT` exposé à la vérification Gradle |
| `40977019903` (a8ddaaf) | spotless (imports, chaînes) | `f21e382` : corrections exactes du diff |
| `40978171846` (f21e382) | lint `UseTomlInstead` : coordonnées moteur inline dans `lsp/host/build.gradle.kts:64-65` | `0ca7e7b` : épinglage catalogue (`lsp-java-moteur`/`lsp-java-ecj-art` vers `codeide-lsp-java = 1.1.0`, miroir du toml CodeIDE) |
| `40979686795` (0ca7e7b) | `PariteCorpusDepotTest` : baseline du digeste (1 échec/469) — le corpus compte TOUT `.kt` du dépôt, l'adaptation hôte en avait ajouté 1 (`NavigationCible.kt`) et modifié 9 | `7bd0e0b` : régénération `-Dparite.majBase=true --rerun-tasks` RÉELLE (307 → 308 fichiers) ; grammaire SANS changement (le sweep « sans marqueur d'erreur » reste vert au même passage) |
| `40982522303` + `40982522451` (8ba8b2e, release) | — | **VERTS** (tag + main) |

Publication : release commit `8ba8b2e` (CHANGELOG « Non publié » →
`## 1.23.0`, guide de passage `MISE-A-JOUR-CODEIDE.md` — bump, témoin,
deltas, retour arrière) + tag `v1.23.0` → workflow **vert** (run
`40982522258`) ; registre vérifié : `maven-metadata.xml` de `host`
porte `1.23.0` en latest, **POM de l'hôte contrôlé portant la dépendance
`jo.codeide.lsp.java:jo-codeide-lsp-java:1.1.0` (compile)** + lsp4j
0.21.2 + model/kotlin/xml 1.23.0.

**CodeIDE v0.203.0 poussé** (`f70ff78`, main) : catalogue
`codeide-lsp = "1.23.0"` + `codeide-lsp-java = "1.1.0"` (pin miroir) ;
`version.properties` 0.203.0 / 20300 ; entrée CHANGELOG complète ;
protocole d'appareil **B1–B8** dans `docs/TESTS_MANUELS.md` (témoin
serverInfo, capacités sans action morte, extensions expérimentales,
E1 go-to généré, E4 cycle de vie, live-republish, fiches 004/010/039,
garde Kotlin/XML).

**Vérification locale end-to-end CONTRE LE REGISTRE** (le moteur
mavenLocal fut écarté — `~/.m2/.../java` renommé — pour forcer la
résolution GitHub Packages) : `:app:compileDebugKotlin` **VERT** (344
tâches) ; `dependencyInsight` : `host:1.23.0 -> jo-codeide-lsp-java:1.1.0
+ jo-codeide-lsp-java-ecj-art:1.1.0`. Incident d'environnement local
consigné : throttling 429 du CDN Maven Central **par user-agent JVM**
(curl passe, Gradle non — Cloudflare HKG) ; contourné par un redirect
local mitmproxy Central → miroir public (jetable, hors dépôts). La CI
GitHub, sur un autre réseau, n'a JAMAIS vu ce problème.

**Reste I5** (passe appareil — mains du propriétaire : APK 0.203.0 à
installer, B1–B8 de `TESTS_MANUELS.md`, fiches 004/010/039, PV à
consigner) **et I6** (retrait physique du vendored après « 2 versions
sans régression » — l'habitat unique Java est EFFECTIF depuis cette
poussée).

## 9. Exécution (31/10 au matin, suite) — I5 : la panne « rien ne s'affiche » reproduite, isolée et CORRIGÉE (v1.1.1)

Le retour appareil du 30/10 au soir (CodeIDE **0.203.0**, session
`b5e5cc70` — « le serveur semble ne pas fonctionner, l'éditeur n'affiche
rien quand je tape, aucun signe des fonctionnalités du serveur » ; 39
entrées exportées) a été **reproduit sur JVM** par un harnais miroir du
branchement complet (cel-lsp **4.14.1** + transport in-process +
`ServeurLangageJava(EnvironnementCompilation)` + frappe réelle dans le
document) : didOpen/didChange partent, `publishDiagnostics` ARRIVE — mais
**toujours avec 0 diagnostic**, y compris pour une ligne volontairement
cassée (`Systeme.out.printl`).

### 9.1 Cause racine (mesurée, pas déduite)

`DiagnoseRapide.sourceMasquee` comparait le corps hôte par **IDENTITÉ**
(`===`). Or l'hôte est rendu par `preuveDeConfinement` — une instance de
SON scan interne — tandis que `diagnostiquerRapide` re-scanne la source
pour construire la liste des corps : **deux scans de la même source
produisent des instances DISTINCTES**, l'hôte n'était JAMAIS reconnu et
était masqué par le synthétique `throw new RuntimeException();` comme les
autres corps. La frappe de l'utilisateur était littéralement EFFACÉE du
texte soumis à ecj : le diagnose rapide rendait les diagnostics PORTÉS de
l'état précédent (0 après une ouverture saine) — plus AUCUN diagnostic à
la frappe, sur appareil comme sur JVM. L'arbre focalisé (complétion dans
le corps hôte, fiche 015) étant construit sur la même source masquée, la
complétion dans le corps était atteinte du même défaut.

**Pourquoi la CI de la v1.1.0 n'a rien vu** : les corps de la fixture de
la fiche 014 sont tous PLUS COURTS que le synthétique (30 caractères) —
laissés VERBATIM par `sourceMasquee`, la voie masquable n'était jamais
exercée sur le corps HÔTE. Tout fichier Java réel (corps ≥ 30 caractères)
déclenchait le défaut à la première frappe confinée.

### 9.2 Correctif + garde

1. **`sourceMasquee`** : l'hôte est désormais comparé par égalité
   STRUCTURELLE (`==` — `PlageCorps` est une data class ; les plages
   d'une même liste étant disjointes, deux corps distincts ne peuvent
   être égaux). Audit des autres comparaisons d'identité :
   `SourceFocuser` dérive l'hôte de LA MÊME liste qu'il itère (un seul
   scan — sain) ; `portables` passe par `indexOf` (structurel — sain) ;
   `corpsHote` n'est consommé que par plage (`contient` — sain).
2. **Régression verrouillée** (fiche 014, `corpsHoteLong…`) : corps hôte
   VOLONTAIREMENT ≥ 30 caractères, diagnose initial sain puis frappe
   confinée cassée — équivalence stricte au témoin neuf exigée (le test
   ÉCHOUE sur la v1.1.0 — vérifié sur le jar publié — et passe avec le
   correctif).

### 9.3 Validation locale (bac à sable — même incident 429 Central, même
contournement miroir jetable que §8)

`:java:test --rerun-tasks` : **478 tests verts** (dont la régression
nouvelle) ; `test --rerun-tasks` sur tout le dépôt : 640 tests, **1 flake
de minutage** (`IntegrationMoteurIndexTest` « réouverture chaude 3 ms /
froid 2 ms » — passe isolément, sans rapport avec le correctif, même
classe d'incident que les flakes de bench documentés par les dépôts
frères). Publication **v1.1.1** : version du dépôt 1.1.0 → 1.1.1,
`USAGE.md` aligné (serverInfo 1.1.1 — 3 occurrences + historique).

### 9.4 Chaîne de consommation (au propriétaire)

La correction ne sert CodeIDE qu'au terme de la chaîne : tag **v1.1.1**
(ci-dessous) → CI de publication → **montée du pin hôte**
`codeide-lsp-java = "1.1.1"` dans le catalogue de codeide-lsp + release
de l'hôte (1.23.1) → bump du catalogue CodeIDE. D'ici là, le diagnostic
de la v1.1.0 reste observable côté CodeIDE 0.204.0 : le sink cel-lsp
câblé en v0.204.0 montre précisément `didChange` envoyé puis
`publishDiagnostics: 0 diagnostics received` — la signature exacte de CE
défaut.

## 10. Exécution (31/10, suite) — I5 (suite) : la panne résiduelle « Internal error. » sur TOUTE requête, isolée et CORRIGÉE (v1.1.2)

Après la v1.1.1 (§9), le retour appareil suivant (CodeIDE **0.204.1**,
moto g06, logs `codeide-logs-2026-10-30-001905.zip`) **change de
signature** : le client cel-lsp 4.14.1 se connecte (`connect: SUCCESS`,
capabilities YES) mais CHAQUE requête échoue côté serveur —
`documentHighlight: ERROR … ResponseErrorException: Internal error.`
(dès l'ouverture, offset=0), `inlayHint: ERROR … Internal error.`,
`completion: ERROR … Internal error.` après chaque frappe — et AUCUNE
ligne `publishDiagnostics: N diagnostics received` : la publication
n'arrive jamais. Ce n'est PLUS « 0 diagnostics » (§9) : c'est le serveur
qui refuse tout.

### 10.1 Cause racine (mesurée sur le fil exact de la session)

Un harnais de reproduction (`ReproductionFrappeAppareilTest`, dépôt
codeide-lsp — didOpen → documentHighlight(0) → inlayHint → didChange →
completion, stdio, chaîne de production complète) rejoue le fil défaillant
et capture le stderr du sous-processus : la stack trace réelle du
« Internal error. » est un **`ServiceConfigurationError`** levé par le
`ServiceLoader` d'`AccesJrt.ouvrirDepuisJarExterne` — sur **ART**, le
fournisseur `jrt` du `lib/jrt-fs.jar` du JDK Termux externe **ne peut pas
s'instancier** (il référence des internes JDK absents du runtime Android,
`jdk.internal.jimage` notamment). Or la garde historique capturait
`Exception` — et `ServiceConfigurationError` est un **Error** : il
s'échappait de la voie « échec de chargement silencieux », remontait la
construction **paresseuse** d'`EnvironnementNoms` (déclenchée par la
PREMIÈRE requête moteur) et faisait échouer TOUTES les requêtes suivantes
en « Internal error. » — complétion, surlignage, inlay — pendant que les
diagnostics, servis par une voie qui interroge aussi l'environnement,
restaient muets.

**Pourquoi ni la CI ni la JVM locale ne l'avaient vu** : sur JVM, le
fournisseur `jrt` SYSTÈME précède les fournisseurs déclarés du jar dans
l'itération du `ServiceLoader` (ordre parent-d'abord) — le défaut du jar
externe est masqué accidentellement. Il ne se manifeste QUE sur ART, où
aucun fournisseur jrt système n'existe : condition que la régression
nouvelle simule au plus près (chargeur SANS parent + fournisseur déclaré
cassé).

### 10.2 Correctif + gardes

1. **`AccesJrt`** : `ouvrirDepuisJarExterne` et la sonde isolable
   `fournisseurJrtDe` capturent désormais **`Throwable`** — le contrat
   « null sur échec de chargement silencieux » (fiche 031) est honoré
   même face aux `Error` ; l'échec est journalisé (JUL, WARNING, avec
   cause) pour rester traçable en harnais. Conséquence moteur :
   dégradation vers le **régime instantané documenté** (`AccesJrt = null`)
   — la plateforme `java.*` n'est plus résolue par l'image jrt du JDK
   externe, mais les entrées RÉELLES du classpath (android.jar du modèle,
   bibliothèques du projet) continuent de servir complétion, diagnostics
   et navigation sur le code utilisateur.
2. **Trois régressions verrouillées** (fiche 031) : (a) sonde
   `fournisseurJrtDe` — un chargeur sans parent portant un jrt-fs.jar dont
   le fournisseur déclaré n'existe pas rend **null**, l'Error CONTENUE ;
   (b) contrat de surface — `ouvrir` ne LÈVE jamais, quel que soit l'état
   du home JDK factice ; (c) signature EXACTE du défaut appareil — le
   moteur monté sur un JDK cassé répond quand même à la complétion (champ
   du fichier courant servi, fiche 028).

### 10.3 Chaîne de consommation (au propriétaire)

Même chaîne que §9.4 : tag **v1.1.2** → CI de publication → montée du pin
hôte `codeide-lsp-java = "1.1.2"` dans le catalogue de codeide-lsp +
release de l'hôte (1.23.2) → bump du catalogue CodeIDE (0.204.2). Le
harnais de reproduction est commité avec la release hôte : la régression
du fil appareil est désormais exécutée par la CI de codeide-lsp sur la
chaîne stdio complète.

### 10.4 Exécution (31/10, fin) — la chaîne est FRANCHIE

- **v1.1.2 publiée** (commit `0bc56ee`, tag poussé) : CI de publication
  **verte** (run `41008294645`) ; registre vérifié — jar moteur 786 963 o
  avec `version.properties=1.1.2`, compagnon ecj-art 3 288 188 o,
  `maven-metadata.xml` latest=1.1.2 ; CI branche verte aussi
  (`41008294696`).
- **Régression prouvée verrouillante** : la sonde `fournisseurJrtDe`
  ré-écrite avec `catch (Exception)` (état v1.1.1) fait ÉCHOUER la
  régression (a) — le verrou tient. Quatre tests nouveaux (simulation
  ART fidèle par service factice sans module fournisseur + masquage JVM
  documenté + contrat de non-jet + moteur répondant sans plateforme) :
  `:java:test` 482 verts, dépôt entier 644 verts.
- **Pourquoi la simulation utilise un service factice** : vérifié
  expérimentalement — sur JVM, le fournisseur `jrt` de `java.base`
  (couche boot) est TOUJOURS itéré AVANT les fournisseurs du classpath
  du chargeur, même sans parent : le défaut du jar externe est
  structurellement masqué sur JVM. La condition ART (fournisseur cassé
  SEUL itéré) exige un service sans AUCUN module fournisseur — d'où la
  sonde générique `premierFournisseur` (le cœur du correctif, testable
  fidèlement) et le test de masquage qui documente l'aveuglement de la
  CI JVM.
- **codeide-lsp 1.23.2 publiée** (release `16405f4`, pin
  `codeide-lsp-java = "1.1.2"`, CHANGELOG + guide `MISE-A-JOUR-CODEIDE.md`
  §1.23.2) : publication des 8 modules **verte** — POM de l'hôte
  contrôlé portant `jo-codeide-lsp-java:1.1.2` (compile) +
  `ecj-art:1.1.2` (runtime), metadata latest=1.23.2. Deux correctifs de
  conformité CI absorbés ensuite sur main (motif 1.23.0) : spotless
  (`a228bc3`, imports + ligne vide du test de reproduction) puis detekt
  (`4468925`, exemption `LongMethod` justifiée — le fil appareil se lit
  d'un bloc) puis baseline du digeste du corpus régénérée APRÈS les
  changements de contenu (`10120f6`, 309 fichiers, sweep vert) ; le tag
  v1.23.2 déplacé sur l'état final, CI verte main + tag
  (`41015186438`/`41015186392`), garde 409 du workflow de publication
  (re-tag inoffensif, documenté) — les artefacts étant identiques (les
  sources de test ne sont pas embarquées).
- **La régression du fil appareil validée contre le REGISTRE** :
  `ReproductionFrappeAppareilTest` ré-exécutée avec le moteur mavenLocal
  ÉCARTÉ (résolution GitHub Packages 1.1.2, `--rerun-tasks`) : VERTE —
  le fil exact de la session défaillante (didOpen → documentHighlight(0)
  → inlayHint → didChange → completion → pousserSync → re-frappe) ne
  produit plus AUCUN « Internal error. ».
- **CodeIDE 0.204.2 poussé** (`a670132`, main) : catalogue
  `codeide-lsp = "1.23.2"` + `codeide-lsp-java = "1.1.2"` (pin miroir),
  `version.properties` 0.204.2 / 20402, entrée CHANGELOG complète. CI
  verte (`41015970688`). Validation locale CONTRE LE REGISTRE :
  `:app:compileDebugKotlin` VERT (344 tâches) ; chaîne de dépendance
  confirmée `host:1.23.2 → jo-codeide-lsp-java:1.1.2 + ecj-art:1.1.2`.
- **Incident d'environnement local consigné** (bac à sable recyclé entre
  les sessions, hors dépôts) : JDK système redevenu JRE (démon Gradle
  basculé sur le JDK adoptium foojay persistant), SDK Android absent
  (re-provisionné par `scripts/setup-env.sh` de CodeIDE — idempotent),
  miroir 429 Central re-écrit (huaweicloud en tête de chaque liste, le
  portal de plugins redirigeant vers Central étant aborté par le 429) ;
  la CI GitHub, sur un autre réseau, n'a jamais vu ces problèmes.

Reste au propriétaire : installer l'APK 0.204.2 sur le moto g06 et
rejouer la passe appareil — attendu : les requêtes RÉPONDENT (plus de
`ResponseErrorException: Internal error.`), la frappe d'une erreur
republie des diagnostics N > 0 ; écart résiduel possible sur la
complétion `java.*` pure (régime instantané).

## 11. Exécution (30/10, deuxième vague) — v1.1.3 : les familles muettes parlent (superDefinition, « unused », déclencheur @)

> Retour appareil sur CodeIDE 0.204.2 (logs `codeide-logs-2026-10-30-025629.zip`,
> moto g06, session 4e363797) : « il y a grand progrès » — la chaîne de
> consommation est FRANCHIE (connect SUCCESS, capabilities YES, complétion
> `import com.` → **58 items reçus**, `publishDiagnostics` reçus avec
> sévérité correcte `[3]` = erreur côté échelle interne du client). Deux
> familles restent muettes côté utilisateur ; diagnostic MESURÉ sur les
> logs + reproduction verrouillée (`Repro20261030AppareilTest`, 11 tests).

### 11.1 Ce que les logs disaient (et ce que la repro a prouvé)

- **« j'ai tapé "import ", il n'y a pas de suggestions de package ; j'ai
  même essayé "import com.", j'aurais dû avoir "exemple" »** : la requête
  `com.` A ÉTÉ envoyée (trigger `.`) et le moteur a rendu 58 items — la
  repro confirme le contenu au fil : « `import ` » (préfixe vide) rend
  com/java/javax/org + paquets projet, « `import com.` » rend les
  sous-paquets DONT « exemple » (paquet projet), labels = SEGMENTS
  (compatibles avec le re-filtrage local cel-lsp), filterText = label
  (I12). L'absence d'affichage après « `import ` » + espace suit la
  POLITIQUE cel-lsp (préfixe vide = caractère déclencheur requis,
  comportement identique au vendord) : les suggestions arrivent à la
  première LETTRE (debounce 80 ms), après « . », ou via Cmplt/Ctrl+Espace.
- **« le fichier des imports non utilisés, variables non utilisées et
  autres, il ne les affiche pas »** : reproduit — la table analytique ne
  portait que `unusedImport`, la table rapide RIEN, et les diagnostics
  HORS CORPS n'étaient pas portés : à la première frappe dans une
  méthode, l'import inutilisé DISPARAISSAIT (le diagnose rapide ne peut
  pas le recalculer honnêtement — le masquage des autres corps casserait
  son analyse d'usages).
- **`superDefinition: ERROR … Unsupported request method:
  textDocument/superDefinition`** (journal 09:34) : le `@JsonRequest` NU
  sur `ServiceExperimental` (interface HORS hiérarchie
  `TextDocumentService`) enregistrait la méthode sous son nom NU — le
  client cel-lsp appelle le nom COMPLET (préfixe `textDocument/` du
  `@JsonSegment` du vendord). Le harnais du dépôt appelait le même nom
  nu des deux côtés : les tests passaient, la production échouait.
- **Écarts mineurs relevés au passage** : `codeAction` avec « context: 0
  diagnostics » (le client n'a pas encore de diagnostics dans la plage —
  honnête), `documentHighlight`/`inlayHint` « no results » sur un fichier
  SYNTAXIQUEMENT CASSÉ par la frappe de l'utilisateur (l'import testé était
  dans le CORPS de la classe — erreurs de syntaxe) : comportement moteur
  attendu sur AST cassé, vérifié non-nul sur code sain par la repro.

### 11.2 Corrections (v1.1.3)

1. **Noms de méthodes EXPLICITES** : `@JsonRequest("textDocument/
   superDefinition")` / `@JsonRequest("textDocument/decompiledSource")`
   sur `ServiceExperimental` — le fil est aligné sur le contrat du
   client cel-lsp SANS toucher à la hiérarchie des interfaces.
2. **Trois tables ecj** (PontEcj) : analytique (import + locale + champ/
   méthode/type privé inutilisés — diagnose complet), rapide (locale
   seule — corps hôte INTACT : les usages d'une locale vivent dans SA
   méthode, aucun fallacieux fiche 014), stricte (passes masquées).
   **Portage hors corps** (`DiagnoseRapide.portablesHorsCorps`) : les
   signaux unitaires analytiques (import/membre privé) sont PORTÉS du
   dernier diagnose complet pendant la frappe, translatés du delta de fin
   du corps hôte après lui, inchangés avant lui, abandonnés à cheval.
   Fraîcheur bornée (même doctrine que le portage des corps masqués) :
   apparition/révocation au prochain diagnose complet ; un fichier
   syntaxiquement cassé ne produit pas ces signaux (ecj les saute sur
   unité non parsable — honnête).
3. **Déclencheurs `[".", "@"]`** (CapacitesServeur) — miroir EXACT du
   vendord (`ServerCapabilitiesProvider`, `List.of(".", "@")`) : la
   complétion d'annotation s'arme à la frappe. TABLE_JAVA de parité de
   l'hôte alignée (`déclencheurs .@`).

### 11.3 Validation

- **Reproduction verrouillée** : `Repro20261030AppareilTest` — serveur
  réel sur racine `com/exemple/monip` + boucle LSP4J + client MIRROR
  CEL-LSP (interface portant les noms COMPLETS) : les 11 scénarios
  (complétion d'import aux trois profondeurs + contexte cassé,
  diagnostics unused × 3, survie à la frappe dans un corps,
  superDefinition par son nom complet, documentHighlight, inlayHint).
- **494 tests moteur verts** (fiche 014 : 4 tests adaptés à la part
  STABLE de l'oracle — erreurs + avertissements de corps en équivalence
  stricte, signaux unitaires en politique sticky documentée dans la
  KDoc du test).
- **Chaîne** : code-lsp tag v1.1.3 (CI 41027683205 + Publication
  41027683332 vertes, artefacts vérifiés) → codeide-lsp 1.23.3 (pin +
  TABLE_JAVA ; 258 tests d'hôte verts contre le moteur 1.1.3 ;
  Publication 41028341920 verte ; le premier passage CI du tag a
  échoué sur le flaky documenté `OrchestrateurLspTest.controles de
  passage` — re-run vert, connu v1.8.0/v1.9.0) → CodeIDE 0.204.3
  (`f6ebdddf`, catalogue miroir 1.23.3/1.1.3, validation locale contre
  le registre : `:app:compileDebugKotlin` VERT, chaîne
  `host:1.23.3 → jo-codeide-lsp-java:1.1.3 + ecj-art:1.1.3`).

Reste au propriétaire : installer l'APK 0.204.3 sur le moto g06 et
rejouer la passe appareil — attendu : les TROIS signaux « unused » à
l'ouverture, leur PERSISTANCE pendant la frappe dans un corps, le GO TO
« Super » qui répond, la complétion d'annotation armée par « @ », et les
suggestions de paquets dès la première lettre après « import ».

## 12. Exécution (30/10, troisième vague) — la passe appareil 0.204.3 : audit fonctionnalité par fonctionnalité, cause racine ART, correctif v1.1.4

> Retour appareil sur CodeIDE 0.204.3 (logs `codeide-logs-2026-10-30-105910.zip`,
> moto g06, session 7e7665d8, 463 entrées dont 443 `LspEditeur`) : « à part le
> document highlight qui me semble fonctionner bien, les autres fonctionnalités
> non. Les diagnostics, go to, complétion, etc. rien du tout, seulement du
> basic. » Le propriétaire demande en outre (mandat consigné dans
> `docs/etudes/editeur-lsp/01-responsabilites-editeur-lsp.md`) : consulter
> Android Studio sur la répartition des responsabilités éditeur ↔ LSP AVANT
> toute décision de refonte, puis travailler UNE FONCTIONNALITÉ À LA FOIS
> depuis le LSP jusqu'à l'éditeur.

### 12.1 Ce que les logs disent — l'audit fonctionnalité par fonctionnalité

La chaîne est franchie (connect SUCCESS, capabilities YES ×16, didChange ×57,
publishDiagnostics reçus ×57). Témoins extraits des 443 entrées `LspEditeur` :

| Fonctionnalité | Requêtes observées | Réponses serveur | Ce que voit l'utilisateur | Verdict |
|---|---|---|---|---|
| Diagnostics (syntaxe) | — (push) | reçus ×57, sévérité correcte | squigglés de frappe | **passe** |
| Diagnostics analytiques (unused import/variable/champ) | — (push) | **count=0 sur fichier sain** | rien | **MUET** |
| Complétion | 17 requêtes | 6/58/66/3/5/1/0 items — **mots-clés + fichier courant** (58 = mots-clés, cf. instantané) | « pas de suggestions de package » | **PAUVRE** |
| documentHighlight | 50 requêtes | **« no results » ×50** (même fichier SAIN en fin de session) | surlignage = couche 1 de l'éditeur (mot sous le caret) — cru « fonctionnel » | **MUET (masqué)** |
| inlayHint | 28 requêtes | « no results » ×28 | rien | **MUET** |
| definition / typeDefinition / superDefinition / implementation | 1 chacune (fin de session, fichier sain) | « no results » | rien | **MUET** |
| codeAction | 1 (contexte 0 diagnostics) | « no results » | rien | **MUET** |
| hover / signatureHelp / rename / formatting | 0 requête | — | — | non sollicités dans la session (hover = appui-long côté cel-lsp) |

Fait décisif n°1 : les « no results » persistent SUR FICHIER SAIN (fin de
session : didChange v52–v56 → publishDiagnostics count=0, puis
documentHighlight/inlayHint/definition/… → vides). Fait décisif n°2 : la
complétion rend des mots-clés en masse (58 items après « . ») — la signature
du RÉGIME INSTANTANÉ (`RegimeInstantane.completionInstantanee` : fichier
courant + mots-clés filtrés par préfixe), pas celle du régime plein.

### 12.2 La cause racine — la sonde de plateforme ne voit que le jrt

Sur ART, le fournisseur `jrt` du `lib/jrt-fs.jar` du JDK Termux externe est
ININSTANCIABLE ( internes JDK absents — incident contenu en v1.1.2,
`AccesJrt.ouvrirDepuisJarExterne` → null ). La sonde du régime plein était
`MoteurJava.isLikelyUsable()` = `jrtDisponible()` = **jrt seul** :

- `java/src/main/kotlin/jo/codeide/lsp/java/MoteurJava.kt:1297` (avant
  correctif) : `isLikelyUsableInterne() = environnementNoms().jrtDisponible()` ;
- ~30 points de garde `if (!isLikelyUsableInterne()) return emptyList()` —
  definition, typeDefinition, implementation, superDefinition, survol,
  surbrillances, inlay, usages, renommage, code actions, sélection, pliage… ;
- `diagnose` → repli syntaxe seule (`MoteurJava.diagnose:247`) — les tables
  analytiques (unused) ne tournent JAMAIS ;
- `completer` → `RegimeInstantane.completionInstantanee` (mots-clés).

OR l'hôte branche `android.jar` dans le classpath (boot classpath Android :
`java.*` ET `android.*` — KDoc `SessionLsp`, lot h4 écart 1) : la plateforme
EST résoluble par l'ordre de repli de `EnvironnementNoms` (sources → **jars**
→ jrt → stubs, fiche 042) — la sonde la déclarait absente. Toutes les
familles muettes ont UNE cause, côté serveur, pas côté éditeur : l'étude
`docs/etudes/editeur-lsp/01-responsabilites-editeur-lsp.md` écarte la piste
« politique cel-lsp » (l'éditeur est générique, l'adaptateur est pur, les
politiques d'affichage restent légitimes) — le document highlight « vu
fonctionnel » par l'utilisateur est la couche 1 de l'éditeur (surlignage
lexical du mot), pas le documentHighlight LSP.

Pourquoi la repro §11 ne l'a pas vu : `Repro20261030AppareilTest` tourne sur
JVM avec `JdkDeTest.home()` (jrt RÉEL) — le régime y est PLEIN par jrt ; la
condition ART (jrt ininstanciable + plateforme du classpath) n'y était pas
jouée. C'est le masquage documenté en §11.2/v1.1.2 : sur JVM, le fournisseur
système précède toujours le cassé.

### 12.3 Le correctif (v1.1.4) — la plateforme suit le classpath, comme android.jar

Miroir de la règle Android Studio « la plateforme suit le module » (étude
citée, R4) : la sonde du régime plein devient **« `java.lang.Object`
résoluble »** — par le jrt OU par un jar du classpath porteur.

1. `EnvironnementNoms.plateformeResolvable()` (nouveau) :
   `accesJrt != null || cacheLocalisation.jarPour("java.lang.Object") != null`
   — la classe racine que TOUTE résolution ecj réclame ; verdict mémoïsé
   (positif ET négatif) par `CacheLocalisation`. La sonde n'est PAS
   `aSourceBinaire()` : un jar applicatif seul ne doit PAS ouvrir le régime
   plein (les fiches 042 gardent leur contrat).
2. `MoteurJava.isLikelyUsable()` / `isLikelyUsableInterne()` passent de
   `jrtDisponible()` à `plateformeResolvable()` — les ~30 gardes, `diagnose`
   et `completer` suivent sans autre changement.
3. KDocs mis à jour (MoteurJava, EnvironnementNoms, EnvironnementCompilation,
   ServiceDiagnostics, RegimeInstantane) : « régime instantané » = NI jrt NI
   jar porteur.

### 12.4 Validation — la régression ART simulée, fonctionnalité par fonctionnalité

`java/src/test/kotlin/jo/codeide/lsp/java/protocole/PlateformeClasspathARTTest.kt`
(8 tests verts) : home JDK factice SANS `lib/modules` + JAR DE PLATEFORME
construit depuis l'image jrt du runtime ( `java.lang.Object`, `String`,
`Iterable`, `Integer`, `java.util.ArrayList` — le rôle d'`android.jar` ) +
racine `com/exemple/monip`. Témoins extraits de l'exécution :

```
[ART] publishDiagnostics sur le fil → [The import java.util.ArrayList is never used,
       The value of the field Main.jamaisUtilise is not used, The value of the local variable x is not used]
[ART] textDocument/definition sur le fil → 1 cible(s)
[ART] documentHighlight(total) → 2 occurrence(s)
[ART] complétion import (vide) → 2 items : [com, java]
```

- sonde : régime PLEIN sans jrt dès qu'un jar porte la plateforme ; repli
  instantané INTACT sans jar porteur (fiche 028 inchangée — vérifiée) ;
- diagnostics analytiques : les TROIS signaux « unused » (import, champ
  privé, locale) rendus ET publiés sur le fil ;
- résolution : `java.lang.Object` depuis le jar — zéro « cannot be
  resolved » fabriqué ;
- complétion d'import : segments `com` (racine source) + `java` (jar de
  plateforme) — avec le vrai `android.jar` : `com`/`java`/`javax`/`org` ;
- navigation : definition (1 cible) + documentHighlight (2 occurrences) ;
- protocole (aller-retour LSP4J authentique par `BoucleLocale`) :
  definition servie, diagnostics publiés, complétion servie.

Non-régressions exécutées : `Fiche028RepliSyntaxeTest` (12), 
`Fiche042StubsIntrinsequesTest` (5 — leurs jars ne portent PAS
`java.lang.Object`, repli inchangé), `Fiche031JrtExterneTest` (7), et la
suite complète `:java:test` : **501 tests, 0 échec, 0 erreur** (39 ignorés
par hypothèses visibles — android.jar absent du poste, etc.), version
1.1.4 posée.

### 12.5 Chaîne exécutée et reste

- code-lsp : correctif v1.1.4 + étude `docs/etudes/editeur-lsp/` + ce §12,
  suite `:java:test` complète verte (501/0/0, cf. §12.4). Poussé
  `fc906f0` (CI 41086126289 verte) ; tag **v1.1.4** → Publication
  41086202563 VERTE, artefacts `jo-codeide-lsp-java:1.1.4` +
  `jo-codeide-lsp-java-ecj-art:1.1.4` vérifiés HTTP 200 sur
  `maven.pkg.github.com/jjoblab/code-lsp`.
- codeide-lsp **1.23.4** (`fe89b7d`, tag v1.23.4) : pin moteur 1.1.4 seul —
  AUCUN changement d'hôte requis ; CI 41087160192 + Publication
  41087160073 VERTES ; POM `host:1.23.4` vérifié portant
  `jo-codeide-lsp-java:1.1.4` + `ecj-art:1.1.4`.
- CodeIDE **0.204.4** (`d664fd8`, catalogue miroir 1.23.4/1.1.4,
  CHANGELOG 0.204.4) : poussé sur main, CI 41088855726 **VERTE**
  (compilation de l'app contre la chaîne du registre).
- Passe appareil attendue sur moto g06 (0.204.4) : les TROIS signaux
  « unused » à l'ouverture et leur persistance à la frappe ; les paquets
  (`com`, `java`, `javax`, `org`, `exemple`…) dès la première lettre après
  « import » et au « . » ; go-to définition/super NON vide ; documentHighlight
  LSP visible (le distinguer de la couche 1 : tester sur un champ lié à son
  usage, pas sur un mot isolé) ; inlay hints présents ; hover à l'appui-long.
- Deux écarts documentés, décision propriétaire (étude §5.5/e1-e2) :
  capabilities annoncées en régime dégradé (fenêtre réduite par le présent
  correctif) et distinction couche 1 ↔ LSP dans la méthode de passe terrain.

## 13. Exécution (31/10) — le banc PROJET ANDROID RÉEL : la précision Android Studio, éprouvée sur vraies bibliothèques

### 13.1 Ce que les retours demandaient

Le propriétaire (31/10, logs `codeide-logs-2026-10-30-124450.zip`, CodeIDE
0.204.4) : « il y a encore beaucoup d'incohérences, de retard dans les
suggestions de complétion, les suggestions doivent être précises
dépendamment d'où je suis (import, classe, membre, etc.), exactement comme
Android Studio. Pour résoudre ces problèmes, je propose que tu crées un
test projet android complet (avec de vrais jar, aar) et testé les
fonctionnalités. »

L'audit des logs a d'abord établi les FAITS :

- la session démarre NOURRIE DU CACHE (t+8,5 s, 2 modules) — le classpath
  réel n'arrive qu'à t+123,9 s (« session LSP nourrie (1 module(s)
  actif(s), 47 entrées d'index) ») : pendant DEUX MINUTES, `import a|`
  rend 0 item et « AppCompatActivity cannot be resolved » squiggle —
  honnête, mais l'utilisateur voit « rien ne marche » ;
- les complétions 11–12 s (t+339 s, t+625 s) sont le PREMIER appel après
  invalidation du moteur par la sync — la reconstruction froide de
  l'environnement de noms sur classpath réel ;
- `import androidx.appcompat.app.ActionBar` finit par résoudre (t+560 s :
  « The import … is never used ») : la chaîne sync → classpath →
  résolution EST saine une fois nourrie.

### 13.2 Le banc (v1.1.5) — `bancs/projet-android/` + vraies bibliothèques

Le montage demandé — un PROJET ANDROID COMPLET aux artefacts réels :

- `bancs/projet-android/` : la réplique du projet « monip »
  (com.example.monip, AppCompatActivity, ActivityMainBinding data binding,
  Greeter, layout `<layout>`) ;
- `bancs/projet-android-genere/` : les sources GÉNÉRÉES simulées (R.java,
  ActivityMainBinding) à leur emplacement AGP ;
- `scripts/preparer-projet-android.sh` : télécharge la plateforme
  **android.jar 34-ext12** + **16 AARs/jars réels** (appcompat 1.7.0,
  material 1.12.0, constraintlayout, core, fragment, activity 1.8.2,
  lifecycle, savedstate, viewbinding, databinding-runtime…) — la FERMETURE
  transitive vérifiée CLASSE PAR CLASSE (AppCompatActivity →
  FragmentActivity → ComponentActivity → … → Object) : sans activity ni
  viewbinding ni lifecycle-common, ecj rend « The hierarchy of the type
  MainActivity is inconsistent » — le montage est celui d'une VRAIE sync
  Gradle ;
- `BancProjetAndroidReelTest` (20 tests ordonnés) : la timeline appareil
  REJOUÉE au protocole (régime VIVANT, routeur multi-racines, jdkHome
  inexistant — la plateforme vit ENTIÈREMENT dans android.jar comme sur
  ART) : didOpen avant sync → diagnostics du cache → sync → LIVE-REPUBLISH
  → PUIS chaque famille de complétion.

### 13.3 Les cinq correctifs moteur (les écarts du premier run)

Première exécution : 12/18 — les écarts étaient les bugs demandés :

1. **qualifier IMBRIQUÉ** (`R.layout.a|` → 0 item) : `fqcnDuNomType`
   rejetait tout nom à point. Les segments se chaînent maintenant en
   imbriqués `$` (`com.example.monip.R$layout`, repli fichier englobant en
   sources) et la sonde binaire du membre statique reçoit la forme pointée ;
2. **auto-import muet** (`MaterialAlertDialogB|` → le type, SANS édition) :
   le FQCN voyagait en détail mais AUCUNE édition n'était produite. L'item
   porte désormais `additionalTextEdits` — l'insertion
   `import com.google.android.material.dialog.MaterialAlertDialogBuilder;`
   au point d'insertion des imports, jamais quand l'import est inutile
   (déjà présent, paquet courant, java.lang) ;
3. **marqueur de splice en fuite** : l'identifiant interne
   `__codeide_completion__()` paraissait dans l'inventaire à préfixe vide
   (il parse comme déclaration du type englobant) — écarté au dernier
   rempart du filtrage ET à la source de collecte ;
4. **identifiant isolé en corps de méthode** (`ti|` → des TYPES seuls :
   Tile, Time, Timer…) : ECJ parse l'identifiant nu d'une frappe comme
   SingleTypeReference de recovery ; toute vraie position de type porte une
   ancre (superclass, argument, allocation, returnType…). Sans ancre dans
   une méthode concrète, le contexte est NOM — la locale `titre` paraît
   EN TÊTE, les types suivent : le popup Android Studio ;
5. **classpath de banc incomplet** (hiérarchie inconsistante) — corrigé par
   la fermeture transitive réelle (§13.2), miroir du montage hôte.

### 13.4 Validation — 20/20, puis TOUT le dépôt

- banc projet Android réel : **20/20** (6,6 s) — la timeline complète :
  S01 diagnostics honnêtes du cache (11 « cannot be resolved », Bundle
  résout), S02 LIVE-REPUBLISH propre après sync, S02b froid complet
  237 ms, S03–S05 imports précis par niveau (android/androidx → appcompat
  → AppCompatActivity), S06 type + auto-import + nom dérivé `builder`,
  S07–S10 membres (binding.textTitre, liaison.saluer, R.layout.
  activity_main, inflate), S11 locale en tête + filtrage de préfixe
  strict, S12 `new Bund|` → Bundle, S13/S13b stubs d'override (toString
  en tête, 120 items à préfixe vide, zéro fuite de marqueur), S14 hover,
  S15 definition → jdt://decompiled, S16 cohérence de la frappe continue,
  S17 chaud p95=119 ms / frappe pire=82 ms, S18 repli utile en frappe ;
- suite complète `:java:test` (gates Android ouvertes) : **521 tests,
  0 échec, 0 erreur** ;
- `:java:benchQualite` : **recall 1,000 / top-1 1,000 / MRR 1,000,
  p95 36,7 ms, 0 saut** — la baseline SIGNÉE tient (les correctifs ne
  dégradent RIEN du contrat de classement) ;
- `:java:benchUsages` : rappel 1,000, p95 43 ms ;
- `:java:bench` : diagnose complet p50 12,8 ms, hover 0,09 ms,
  complétion membres p50 5,7 ms, tas 17 Mo.

Écart documenté (décision future) : la frappe du MOT-CLÉ lui-même
(`i`, `im`, `import` avant l'espace) rend 0 item — Android Studio y
propose `import`/`interface` ; la complétion de mot-clé en position de
top-level n'est pas câblée (aucun impact sur les paquets après « import »,
le sujet des retours).

### 13.5 Chaîne exécutée et reste

- code-lsp : cinq correctifs + banc + script (§13.2–13.3), suite verte
  (§13.4). Reste au propriétaire : publication v1.1.5 (tag) + pin hôte
  codeide-lsp + version CodeIDE — même geste que §12.5 ;
- la passe appareil attendue sur moto g06 : le banc garantit désormais le
  comportement MOTEUR sur classpath réel — les écarts restants appareil se
  lisent donc côté HÔTE (nourrissage du cache à l'ouverture, délai de la
  première sync) : le banc S01/S02 en est la sonde de parité exacte.

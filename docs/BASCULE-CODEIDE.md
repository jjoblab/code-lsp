# BASCULE CÔTÉ CODEIDE — procédure de mise en œuvre (§8.5, décision signée le 30/10/2026)

> **Rôle** : mise en œuvre **côté client** de la décision de bascule signée le
> 30/10/2026 ([`bancs/GABARIT-BASCULE-APPAREIL.md`](../bancs/GABARIT-BASCULE-APPAREIL.md)
> §6 — checklist 6/6 : banc croisé CI VERT, passe appareil 18/18 / 0 saut,
> latences consignées, deltas arbitrés 103/62/0, retour arrière 1 ligne).
> Ce document est la **procédure préparée** dans le dépôt `code-lsp` ; les
> modifications proprement dites ont lieu dans le dépôt tiers
> `jjoblab/CodeIDE` et restent à la main du propriétaire (ou de son agent,
> sur instruction explicite). Chaque fait est marqué **vérifié** (constaté
> dans ce dépôt au 30/10) ou **à faire** (action CodeIDE).

## 1. Faits vérifiés côté code-lsp (le fournisseur est prêt)

| Fait | Preuve |
|---|---|
| Version du dépôt = **1.0.0** (décision §8.5 signée) | `build.gradle.kts` — `serverInfo.version` générée de cette valeur (fiche 059 I7) |
| Bytecode **17**, consommable par la chaîne Android/ART (d8) | `java/build.gradle.kts` (`jvmTarget` 17, `sourceCompatibility` 17) |
| Façade publique LSP : `jo.codeide.lsp.java.protocole.ServeurLangageJava` (implémente `LanguageServer` + `TextDocumentService` + `WorkspaceService` + expérimentales) | `java/src/main/kotlin/.../protocole/ServeurLangageJava.kt` |
| Façade publique in-process : `jo.codeide.lsp.java.MoteurJava` (tests/bancs, §10 du `USAGE.md`) | `java/src/main/kotlin/.../MoteurJava.kt` |
| Mode autonome `setStandaloneMode(true)` + lanceur stdio lsp4j documentés (exemple complet) | `USAGE.md` §4.1 |
| CI **VERTE** sur le commit de la signature (6/6 jobs : banc parité croisée, banc qualité, empreinte/latence, gates Android, provenance, build/tests) | API GitHub Actions, commit `5b0db74` |
| **Fait (30/10, PR publication)** : bloc `maven-publish` dans `:java` **et** `:ecj-art` — moteur `jo.codeide.lsp.java:jo-codeide-lsp-java:1.0.0` + compagnon runtime `jo.codeide.lsp.java:jo-codeide-lsp-java-ecj-art:1.0.0` (jar ecj 3.40.0 patché, EPL-2.0 — le moteur est ecj-less par construction, compileOnly : « l'hôte fournit le jar ») ; workflow `publish.yml` (tag `v*`, build de vérification puis publication, `packages: write`) ; `publishToMavenLocal` vérifié : `version.properties=1.0.0` dans le jar, POM sans ecj, lsp4j runtime seule, bytecode 17 | `java/build.gradle.kts`, `ecj-art/build.gradle.kts`, `.github/workflows/publish.yml` (constat du 30/10 au soir — le « non fait » du matin est résolu) |
| **Fait (30/10 au soir, publication EFFECTUÉE)** : tag **`v1.0.0`** (annoté, `a178dff`) poussé → workflow `Publication` **vert** (run `40942576172` : build + tests PUIS publication) ; les 4 artefacts résolvent sur `maven.pkg.github.com/jjoblab/code-lsp` (HTTP 302) et ont été re-téléchargés et vérifiés : POM moteur sans ecj (kotlin-stdlib 2.2.10 compile + lsp4j 0.21.2 runtime seules dépendances), **`version.properties=1.0.0`** embarqué dans le jar moteur (témoin serverInfo), compagnon 942 entrées dont 929 `org/eclipse/jdt` + shims `jo/codeide/lsp/ecjart/compat` + `about.html` + registre `META-INF/ECJ-ART-MODIFICATIONS.txt` (EPL-2.0) | tag `v1.0.0`, run Actions `40942576172` |

## 2. Point d'intégration (inchangé par construction)

CodeIDE consomme `:lsp:host`, qui orchestre les moteurs ; l'hôte consomme la
façade publique ci-dessus **par substitution composite** (ADR 0015 : lignes
226–251 de `CodeIDE/settings.gradle.kts`, citées à l'état `54e99c6` —
**à re-vérifier au HEAD courant** avant édition). Le client LSP in-process
(`cel-lsp`, code-editor, Apache-2.0) est consommé tel quel : aucune
modification du client n'est requise — la bascule ne touche que la source
de l'artefact moteur.

## 3. Étapes

### 3.1 DEV — substitution composite (sans publication)

1. Dans `CodeIDE/settings.gradle.kts` (bloc de substitution, l. 226–251 à
   l'état cité par l'ADR 0015) : substituer le module moteur consommé par
   `:lsp:host` vers le dépôt `code-lsp` local
   (`includeBuild("<chemin>/code-lsp")` + `dependencySubstitution` du module
   `jo.codeide.lsp.java` vers `:java` du build inclus).
2. Construire CodeIDE ; vérifier au premier lancement :
   - `initialize` rend `serverInfo = { "name": "lspjava", "version": "1.0.0" }`
     (le témoin direct que le moteur neuf est branché) ;
   - complétion, diagnostics, survol, renommage sur un projet Android réel
     (le scénario des fiches 004/010/039).
3. Toute anomalie ici n'est **pas** une régression de la bascule mais un
   écart d'intégration : la consigner et arbitrer (le moteur reste sous
   bancs bloquants dans `code-lsp`).

### 3.2 PRODUCTION — GitHub Packages (publication EFFECTUÉE le 30/10/2026 — v1.0.0)

1. **FAIT (30/10 au soir)** : publication exécutée par le chemin documenté — tag
   `v1.0.0` (annoté, cible `a178dff`) poussé sur `etude/moteur-java` → workflow
   `publish.yml` (run `40942576172`, **conclusion success** : `./gradlew build`
   — compilation + 625 tests — PUIS `publish`). Vérification post-publication
   (téléchargement réel des artefacts) : moteur
   `jo.codeide.lsp.java:jo-codeide-lsp-java:1.0.0` (POM sans ecj,
   kotlin-stdlib 2.2.10 compile + lsp4j 0.21.2 runtime, jar portant
   `jo/codeide/lsp/java/protocole/version.properties` = **1.0.0**) et compagnon
   `jo-codeide-lsp-java-ecj-art:1.0.0` (929 entrées `org/eclipse/jdt`, shims
   `ecjart/compat`, `about.html` et registre des modifications EPL-2.0). La CI
   publie sur **tag `v*`** — aucun secret personnel dans le dépôt (jetons
   `GITHUB_ACTOR`/`GITHUB_TOKEN` automatiques d'Actions, permissions
   `packages: write`).
2. Dans `CodeIDE` : déclarer le dépôt GitHub Packages (auth token `jjoblab`)
   pour le groupe `jo.codeide.lsp.java` et consommer la coordonnée publiée —
   **rails POUSSÉS le 30/10 au soir sur `origin/main`** (commit `8450bb7` de
   `jjoblab/CodeIDE`, au-dessus de `54e99c6` : dépôt Packages + filtre
   `includeGroup` + mavenLocal filtré + catalogue + substitution composite sur
   opt-in, même recette que les frères LayoutLib / codeide-tooling /
   codeide-lsp). **Re-vérification au HEAD `54e99c6`** (exigée en §2) : la
   dépendance moteur vit dans le **POM de l'hôte**
   (`com.github.jjoblab.codeide-lsp:host`, dépôt vendored) — l'hôte consomme la
   façade `jo.lspjava.*` (4 fichiers, surface étroite : `CompilationEnvironment`,
   `JavaLanguageServer`, `IndexNoms`, `NavigationTarget`). Le branchement de
   l'hôte sur la façade neuve (`jo.codeide.lsp.java.*`) est le **chaînon
   restant** ; le POM publié ci-dessus est la coordonnée exacte que cette
   adaptation déclarera.

   **Chaînon restant — surface MESURÉE le 30/10 au soir** (lecture du code
   `codeide-lsp` @ `35234b9`, v1.22.1) :
   - 4 fichiers main de `:lsp:host` : `SessionLsp.kt` (858 l. — construit
     `JavaLanguageServer(environnement) {…}` par connexion LSP, possède le
     `CompilationEnvironment`, `dispose()` à la fermeture),
     `OrchestrateurLsp.kt` (453 l. — `remplacerContexteModule` sur l'env
     partagé), `AdaptateurIndexNoms.kt` (170 l. — pont vers
     `jo.lspjava.api.IndexNoms`), `KotlinLanguageServer.kt` (1594 l. — import
     `NavigationTarget` seul) + **5 fichiers de test** ;
   - écart architectural 1 : le vendored partage un environnement VIVANT (un
     `CompilationEnvironment` muté par l'orchestrateur, `ContextListener`) ; le
     neuf expose `ServeurLangageJava(fabriqueMoteur: (racine) -> MoteurJava?,
     rappelArret)` — moteur FIGÉ à `initialize` ;
   - écart architectural 2 : le pendant exact existe —
     `jo.codeide.lsp.java.EnvironnementCompilation` (contextes par module,
     sémantiques replaceAll fiche 034, routeur fichier→module fiche 047, index
     partagé, auditeurs) — mais il n'est PAS câblé dans `ServeurLangageJava`
     (moteur unique par connexion) : l'arbitrage « fabrique consultant l'état
     courant à initialize » vs « exposition du routeur multi-modules au
     protocole » appartient au propriétaire ;
   - correspondances de types : `IndexNoms` → `jo.codeide.lsp.java.IndexNoms`
     (constructeur de `MoteurJava`), `NavigationTarget` → `CibleNavigation`
     (paquet `navigation`), `LanguageServer` lsp4j inchangé
     (`ServeurLangageJava` l'implémente — la surface publique
     `SessionLsp.serveur()` tient) ;
   - dépendances de l'hôte : `api(project(":lsp:java"))` →
     `api("jo.codeide.lsp.java:jo-codeide-lsp-java:1.0.0")` + compagnon
     `runtimeOnly` ; lsp4j `api` inchangé (0.22.0 imposée par cel-lsp, moteur
     binaire sous 0.22.0 — même recette que le vendored, `AlignementLsp4jTest`) ;
   - non-régression : les **141 tests d'hôte** rejoués contre la façade neuve
     (ADR 0015 §Tests) ; publication d'un `:host` adapté (version `codeide-lsp`)
     puis bump catalogue CodeIDE ;
   - fourchette honnête : **3–6 j-h**, décision de suite au propriétaire
     (c'est le dépôt ORACLE — `codeide-lsp` en lecture seule pour `code-lsp`,
     ADR 0012–0014 : la modification se fait sur instruction explicite).
3. Incrément de version CodeIDE selon sa convention de release.

### 3.3 Retour arrière (critère 6 du gabarit — 1 ligne)

Inverser la substitution composite (ou retirer la dépendance GitHub
Packages) dans `CodeIDE/settings.gradle.kts` : `:lsp:host` re-pointe sur le
moteur vendored, l'état d'avant la bascule est reconstruit **sans
re-déploiement du client** (ADR 0015). Drapeaux serveur `off` si des vagues
sont actives.

## 4. Vérifications post-bascule (témoin et filet)

- `serverInfo.version` = **1.0.0** côté client (témoin du branchement) ;
- les 6 jobs CI de `code-lsp` restent verts sur les commits suivants
  (banc croisé bloquant — l'oracle épinglé `cb809e14` demeure la référence
  jusqu'au retrait du vendored, qui suit la règle « 2 versions sans
  régression » des ADR 0012–0014) ;
- suivi post-bascule borné (gabarit §6) : filtrage des membres
  package-private (`valueOfCodePoint`, `coder`, `value`, `hash`,
  `hashIsZero`, `isLatin1`), extension *optionnelle* de la collecte TYPE
  (hump/sous-chaîne), ouverture des gates `model.json` (Fiche010/Fiche039)
  sur export réel ou équivalent régénérable.

# REORGANISATION-AVANT — Photographie mesurée « avant » (fusion et réorganisation)

> Mission du 02/11/2026 : **fusion sans perte** de `etude/moteur-java` et
> `etude/moteur-kotlin`, puis réorganisation par déplacements purs.
> Ce document consigne les **invariants mesurés avant toute action** (fusion
> comprise). Chaque valeur porte sa source (R5 — aucune valeur fabriquée) ;
> une valeur non mesurable est marquée `[À COMPLÉTER PAR LE PROPRIÉTAIRE]`.
> Snapshots exacts pour retour arrière : tags `pre-fusion/etude-moteur-java`
> (tête Java) et `pre-fusion/etude-moteur-kotlin` (tête Kotlin), poussés le
> 02/11/2026.

## 1. Têtes mesurées et CI

| Branche | Tête | Dernier commit | Dernier run CI | Conclusion |
|---|---|---|---|---|
| `etude/moteur-java` | `4724fe99e76a657d3535cefd64750ff1421bd97d` | 02/11/2026 05:20 (PV vague V-H, version 1.13.0) | run #122, id 41393518111 (02/11 05:21) | **succès — 6/6 jobs verts** |
| `etude/moteur-kotlin` | `8856b95e697f26f25a5de198c4ed2c2427641069` | 31/10/2026 11:14 (attestation CI, diff document seul) | run #109, id 41196064353 (31/10 11:15) | **succès — 10/10 jobs verts** |

- Base commune : `62dbbee18656a1a24b3032eda75c6bcbccae7f59` (29/10/2026).
- Commits depuis la base : **89 côté Java / 104 côté Kotlin** (mesure
  `git rev-list --count`, 02/11/2026 — le prompt de mission annonçait 58/71
  au 31/10 : les deux agents ont poursuivi depuis).
- Fichiers touchés **des deux côtés** depuis la base : `.gitignore`,
  `.github/workflows/ci.yml`, `AGENTS.md` (mesure `comm -12` des
  `git diff --name-only`). Périmètres disjoints par ailleurs : Kotlin n'a
  rien touché dans `java/`, `spike/`, `index/`, `model/`, `testing/`,
  `ecj-art/` ; Java rien dans `kotlin/`, `spike-kotlin/`.
- Publication : dernière version taguée **v1.12.2** (run #12, id 41372554541,
  02/11/2026 01:27, succès). La tête Java porte la version **1.13.0**
  (`build.gradle.kts` ligne 410, `allprojects { version = "1.13.0" }`).

## 2. Comptes de tests

### 2.1 Tests exécutés (sources documentées)

| Périmètre | Valeur | Source |
|---|---|---|
| Moteur Java (build strict P1–P8) | **959 tests / 0 échec** (45 gates ignorées avec raison ; banc projet réel exécuté) | PV vague V-H, commit `4724fe9` + `docs/etudes/niveau-android-studio/00-synthese.md` §V-H (02/11/2026) |
| Moteur Kotlin (suite du module) | **cahier COMPLET 469/469** | Nom du job CI du run 41196064353 + `docs/etudes/moteur-kotlin/00-synthese.md` (31/10/2026) |

Gradle n'imprime pas de compte par module dans les logs CI ; l'invariant
comparable commit-à-commit est donc le **compte d'annotations de tests**
(§2.2, commande reproductible) en complément de la CI verte.

### 2.2 Annotations de tests (mesure locale reproductible)

Commande : `git grep -h -E '@(Test|ParameterizedTest|RepeatedTest|TestFactory)\b' <ref> -- <module>/src/test | wc -l`.

| Module | Tête Java | Tête Kotlin | Après fusion (attendu) |
|---|---:|---:|---:|
| `:java` (`java/`) | **731** (113 fichiers) | 419 (62 fichiers, version base) | **731** (version Java) |
| `:index` (`index/`) | **84** (14 fichiers) | 84 (14) | **84** |
| `:model` (`model/`) | **42** (4 fichiers) | 38 (4, version base) | **42** (version Java) |
| `:testing` (`testing/`) | **4** (1 fichier) | 4 (1) | **4** |
| `:ecj-art` (`ecj-art/`) | **36** (5 fichiers) | 36 (5) | **36** |
| `:kotlin` (`kotlin/`) | — | **534** (70 fichiers) | **534** |
| `:spike-kotlin` (`spike-kotlin/`) | — | **8** (3 fichiers) | **8** |

Nota : le module `model` a évolué côté Java depuis la base (contrats
`ProjetLsp`, `ProtocoleClasspath`, `ConvertisseurModeleLsp` — 4 annotations
de plus) ; le moteur Kotlin compile après fusion contre cette version
évoluée → contrôle sémantique de la fusion (CI sur `integration/fusion`).
`spike/` n'a pas de tests.

## 3. Cahiers de spécifications (comportements)

| Cahier | Fiches | Comportements | Source |
|---|---:|---:|---|
| Java (`docs/cahier-specifications/`) | 66 fichiers `.md` dans `fiches/` (mesure) | **449 méthodes de test** (65 fichiers sources, 16 229 lignes) | `INVENTAIRE.md` (extraction codeide-lsp @ cb809e14, lecture seule) |
| Kotlin (`docs/cahier-specifications-kotlin/`) | 53 fichiers `.md` (mesure) | **469 méthodes `@Test`** (53 fichiers, 15 853 lignes) | `INVENTAIRE.md` (extraction codeide-lsp @ bc11253, lecture seule) |

## 4. Baselines signées et digestes (SHA-256, mesurés par `git show <ref>:<fichier> | sha256sum`)

### Tête Java (`etude/moteur-java`)

| Fichier | SHA-256 |
|---|---|
| `bancs/baselines/completion.json` | `59f28913e2220ad561d5819edc37bdf4ec6ebf885b2a334fe089676b79effae8` |
| `bancs/baselines/parite-completion.json` | `a06d4cd2ae95fcf29f0073910441eac4ba2e91ae4420ce5d62f072ca31f56396` |

### Tête Kotlin (`etude/moteur-kotlin`)

| Fichier | SHA-256 |
|---|---|
| `bancs/baselines/completion-kotlin.json` | `6b9f9937dabc5cce3d0df72508a6415aaf0fd618878c232c9f64be1b0e1505b2` |
| `bancs/baselines/completion.json` (version base) | `5499fdf9880dfff37aedb15b8754d7b2c63fadd848de0cf2e08d76c8fe38c4c7` |
| `bancs/baselines/parite-completion.json` (version base) | `2d5e789cbbaabadf92dab326d4caa300097c044e4322a57c3d272a65c6804683` |
| `kotlin/src/test/resources/parite/digeste-corpus.txt` | `b4b61ad509e7ed22a5122543ab2910851d536b2b216668c5b7a6a82ee8d07ffa` — **432 lignes** |
| `kotlin/src/test/resources/fidelite/baseline-fidelite.txt` | `694d15ba3d2fbaa6900d525e7781c0f5ece20444ba73ae165c877a5568e9ba47` — 8 lignes |

Après fusion, les baselines Java attendues sont celles de la **tête Java**
(évoluées côté Java depuis la base), plus `completion-kotlin.json` (ajout
Kotlin). Le digeste `digeste-corpus.txt` liste des empreintes
`SHA-256  chemin/relatif/au/dépôt` couvrant des fichiers **Java et Kotlin**
(piège P-1 : tout déplacement casse la parité — procédure dédiée en Phase 2).

## 5. Chiffres dex Kotlin (mesure CI, run 41196064353, job « Moteur Kotlin - scan ART + dexage d8 (seuils étage 0) », 31/10/2026)

| Artefact | Méthodes déclarées (borne basse dex) |
|---|---:|
| `kotlin-1.0.0.jar` (moteur) | 337 |
| `syntax-api-jvm-0.3.340.jar` | 514 |
| `lang-syntax-jvm-0.3.340.jar` | 71 |
| `annotations-26.0.2.jar` | 33 |
| `kotlin-stdlib-2.2.10.jar` | 6 612 |
| `kotlin-metadata-jvm-2.2.10.jar` | 1 141 |

- Références de méthodes (method_ids) pile chaude : **26 590** (seuil ≤ 32 768).
- `classes.dex` pile chaude : **4 294 352 octets (4,10 Mo)** (seuil ≤ 6 Mo —
  budget T1 élevé par le propriétaire le 31/10/2026, E-K8).
- Après fusion, le jar moteur Kotlin devient `kotlin-1.13.0.jar` (la version
  vient du projet racine, unifié à 1.13.0 par la fusion) — attendu, sans
  effet sur les seuils (aucun `1.0.0` codé en dur dans les tests Kotlin,
  `minApp=1.0.0` du descripteur de pack étant indépendant).

## 6. Tailles de jars

| Artefact | Taille | Source |
|---|---:|---|
| `ecj-3.40.0-art.jar` (compagnon `:ecj-art`) | **3 211 Ko** | Log du run de publication v1.12.2 (id 41372554541, 02/11/2026) |
| Jar moteur Java (`jo-codeide-lsp-java`) | `[À COMPLÉTER PAR LE PROPRIÉTAIRE]` | Non imprimé par la CI ; build local impossible dans le bac à sable de mesure (rate-limit Maven Central, même classe de panne que l'alerte du 31/10) ; packages GitHub non visibles par le jeton de mission |
| Jar moteur Kotlin | `[À COMPLÉTER PAR LE PROPRIÉTAIRE]` | Idem (seule la borne basse de méthodes est mesurée, §5) |

## 7. CI — noms exacts des jobs (à conserver à l'identique)

- `ci.yml`, branche Java (6 jobs) : `Build et tests (JDK 21)` ·
  `Banc qualité complétion (baseline signée)` · `Banc empreinte/latence du
  noyau N1` · `Banc parité croisée T (oracle vendored épinglé)` ·
  `Gates Android (SDK 34 en CI)` · `Manifeste de provenance
  (anti-fichier-hors-registre)`.
- `ci.yml`, branche Kotlin (10 jobs) : les 6 ci-dessus (identiques pour
  `banc-noyau`, `banc-parite`, `banc-qualite`, `provenance` ; versions
  divergentes pour `build` et `gates-android` — la tête Java est la plus
  récente : env `BANC_COHERENCE_STRICT` + étape « Bibliothèques réelles du
  banc projet Android ») + 4 jobs propres : `Moteur Kotlin - suite du
  module (cahier COMPLET 469/469 …)` · `Moteur Kotlin - banc
  empreinte/latence (nombres)` · `Moteur Kotlin - banc qualité complétion
  (baseline signée, étage 3)` · `Moteur Kotlin - scan ART + dexage d8
  (seuils étage 0)`.
- `publish.yml` (1 job `Vérification puis publication (JDK 21)`,
  déclenché par tags `v*` uniquement).
- Déclencheurs `ci.yml` : `push` branches `['**']`, `pull_request`,
  `workflow_dispatch` → la CI s'exécute sur toute nouvelle branche,
  dont `integration/fusion`.
- Modules Gradle : base `:java` `:index` `:model` `:testing` `:ecj-art` ;
  la branche Kotlin ajoute `:kotlin` et `:spike-kotlin` (tâches désactivées
  sans `-PspikeKotlin`).

## 8. Arborescence des fichiers suivis (mesure `git ls-tree -r --name-only <ref>`)

### Tête Java — 545 fichiers

`java/` 256 · `docs/` 156 · `index/` 34 · `spike/` 24 · `bancs/` 17 ·
`model/` 13 · `scripts/` 12 · `ecj-art/` 12 · `testing/` 4 · `gradle/` 2 ·
`.github/` 2 · racine 9 (`README.md`, `AGENTS.md`, `USAGE.md`, `NOTICE`,
`LICENSE`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
`.gitignore`) + `.githooks/` (1) + `gradlew`/`gradlew.bat`.

### Tête Kotlin — 744 fichiers

`docs/` 230 · `kotlin/` 191 · `java/` 158 (version base) · `spike-kotlin/`
48 · `index/` 34 · `spike/` 24 · `model/` 13 · `ecj-art/` 12 · `bancs/` 7 ·
`scripts/` 6 · `testing/` 4 · `gradle/` 2 · `.github/` 2 · racine 9 (idem)
+ `.githooks/` (1) + `gradlew`/`gradlew.bat`.

Reproduction exacte : `git ls-tree -r --name-only pre-fusion/etude-moteur-java`
et `git ls-tree -r --name-only pre-fusion/etude-moteur-kotlin`.

## 9. Environnement de mesure

Bac à sable JDK 21, Gradle 8.13 (wrapper du dépôt). Les builds complets
locaux n'ont pas été exécutables (échec de résolution
`kotlin-serialization-compiler-plugin-embeddable:2.2.10` — rate-limit Maven
Central, même classe de panne transitoire que celle consignée le 31/10 par
l'agent Kotlin) : conformément au prompt de mission, **la CI GitHub est
l'arbitre** ; les mesures locales ci-dessus sont toutes reproductibles par
les commandes indiquées.

## 10. Invariants à retrouver à l'identique en fin de réorganisation

1. Comptes d'annotations de tests par module (§2.2) **identiques** — et CI
   verte sur l'ensemble des jobs.
2. Cahiers : 66 fiches Java / 449 comportements ; 53 fiches Kotlin /
   469 comportements (§3).
3. Baselines **identiques octet pour octet** (§4) — hors re-signature du
   digeste par la procédure dédiée du piège P-1 (preuve d'équivalence des
   empreintes de contenu, commit dédié, accord du propriétaire).
4. Chiffres dex Kotlin (§5) et taille du compagnon ecj (§6) identiques.
5. Jobs CI : **mêmes noms**, tous verts (§7).
6. Zéro lien mort (vérificateur de liens, Phase 2).
7. `git log --follow` retrouve l'historique des fichiers déplacés
   (déplacements par `git mv`, similarité 100 %).
8. Build, bancs et scripts exécutables **depuis la racine** comme avant ;
   chemins de projets Gradle (`:java`, `:kotlin`, `:index`, `:model`,
   `:testing`, `:ecj-art`), coordonnées publiées et capacités LSP
   **inchangés**.

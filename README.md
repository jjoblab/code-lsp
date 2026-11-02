# code-lsp — dépôt officiel des serveurs de langage (LSP) de CodeIDE

> Vision du propriétaire (28/10/2026, ADR 0002) : **code-lsp sera le dépôt
> officiel des LSP pour CodeIDE une fois terminé** — des serveurs de langage
> « frais, plus solides, plus intelligents, plus robustes, plus propres et
> professionnels », grâce à l'architecture IntelliJ PSI (étudiée en
> profondeur, embarquée nulle part, greffée partout où elle prouve sa
> valeur — GO PARTIEL, ADR 0004).
>
> Réorganisation du 03/11/2026 : chaque LSP vit dans **sa propre section**
> (`lsp-java/`, `lsp-kotlin/`, `lsp-xml/` réservé) autour d'un **socle
> partagé** (`socle/`) — plan [`docs/REORGANISATION.md`](docs/REORGANISATION.md),
> photographie avant [`docs/REORGANISATION-AVANT.md`](docs/REORGANISATION-AVANT.md),
> PV d'exécution [`docs/REORGANISATION-BILAN-PHASE2.md`](docs/REORGANISATION-BILAN-PHASE2.md).

## 1. Carte des LSP

| LSP | Emplacement | État (mesuré dans les documents) | Référence |
|---|---|---|---|
| **Java** (`lspjava`) | [`lsp-java/`](lsp-java/README.md) | **EN SERVICE** — bascule CodeIDE §8.5 signée le 30/10/2026 (v1.0.0) ; **11 versions publiées** sur GitHub Packages (`jo.codeide.lsp.java:jo-codeide-lsp-java`, dernière **1.12.2** le 02/11/2026) ; étages N1–N8+T livrés, mission « niveau Android Studio » V-A→V-H livrée ; tête **1.13.0** (959 tests / 0 échec) | [`lsp-java/README.md`](lsp-java/README.md) |
| **Kotlin** (`lspkotlin`) | [`lsp-kotlin/`](lsp-kotlin/README.md) | **LIVRÉ, NON BRANCHÉ** — programme étages 0–6+T clos sur preuve mécanique le 31/10/2026 (cahier **469/469** comportements + 12 cas dorés, CI 10/10) ; serveur LSP4J complet ; **pas encore en service** dans CodeIDE (ADR 0136 : la bascule I0–I6 passe AVANT l'extension des capacités) ; pack **publié à partir de 1.13.0** (Q-K4 tranché le 03/11/2026 — miroir `:java`, dépôt GitHub Packages partagé) | [`lsp-kotlin/README.md`](lsp-kotlin/README.md) |
| **XML** | [`lsp-xml/`](lsp-xml/README.md) | **À MIGRER** — emplacement réservé, **aucun code** ; le LSP XML actuel vit dans l'ancien dépôt `codeide-lsp` | [`lsp-xml/README.md`](lsp-xml/README.md) · [`lsp-xml/docs/PLAN-MIGRATION.md`](lsp-xml/docs/PLAN-MIGRATION.md) |

## 2. Le socle partagé (`socle/`)

| Module Gradle | Rôle | Origine (manifeste de provenance) |
|---|---|---|
| `:index` | index disque **CIDX** (noms, appelables) partagé par les moteurs | repris contrôlé vague 1b (33 fichiers main + banc, `codeide-lsp` @ cb809e14) |
| `:model` | modèle de projet LSP + types du protocole tooling (contrat `ProjetLsp` v11) | repris contrôlé vague 1b + code neuf (`jo.codeide.lsp.model.protocole`) |
| `:testing` | suite d'intégration moteur ↔ index CIDX | réécrite en Kotlin neuf à l'étage N1 (adaptateur `IndexNoms`) |
| `:ecj-art` | patcheur **build-time** d'ecj 3.40.0 pour ART (bridge `Compiler.resolve`) | réécriture Kotlin clean-room vague 1c — publie le compagnon `ecj-3.40.0-art.jar` (3 211 Ko) |

Les chemins de projets Gradle (`:java`, `:kotlin`, `:index`, `:model`,
`:testing`, `:ecj-art`, `:spike-kotlin`) sont **inchangés** depuis la
réorganisation : seuls les répertoires sont remappés dans
[`settings.gradle.kts`](settings.gradle.kts) — les coordonnées publiées, les
tâches et les artefacts restent identiques pour l'hôte CodeIDE.

## 3. Démarrage rapide (depuis la racine)

Prérequis : JDK 21 (le plugin foojay d'`settings.gradle.kts` le
télécharge sinon). Le wrapper Gradle 8.13 est inclus.

```bash
./gradlew build                                    # build + tests Java et socle (exclut :kotlin)
./gradlew :kotlin:test :spike-kotlin:test          # suite du module Kotlin (cahier complet)
./gradlew :java:bench                              # banc empreinte/latence du noyau N1
./gradlew :java:benchQualite                       # banc qualité complétion — ÉCHOUE sur régression vs baseline signée
./gradlew :java:benchUsages                        # banc usages N4 (rappel = oracle, CI-bloquant)
./gradlew :java:benchParite                        # banc de parité croisée T (oracle vendored épinglé)
./gradlew :kotlin:bench :kotlin:benchQualite       # bancs Kotlin (empreinte/latence, qualité baseline signée)
ANDROID_JAR=… ./gradlew :java:test                 # ouvre les gates Android (fiches 033/036/043/044/050/053…)
```

Chaque LSP détaille ses bancs et ses baselines signées dans son propre
`README.md` (liens §1). Les tâches du module de mesure `:spike-kotlin` sont
désactivées sans `-PspikeKotlin`.

## 4. État de la CI

La CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) exécute
**10 jobs** à chaque push : build et tests JDK 21, bancs Java
(empreinte/latence N1, qualité complétion baseline signée, parité croisée
oracle vendored, usages), gates Android (SDK 34), manifeste de provenance
(anti-fichier-hors-registre), et côté Kotlin la suite du module (cahier
469/469), les bancs empreinte/latence et qualité complétion (baselines
signées) et le scan ART + dexage d8 sous seuils. Dernier état mesuré :
**run #129 sur `main` (`d7e58f3`, 03/11/2026) — succès 10/10**. La
publication ([`publish.yml`](.github/workflows/publish.yml)) est déclenchée
par les tags `v*` uniquement (build de vérification puis publication vers
GitHub Packages).

`main` est la branche par défaut, **protégée** : checks requis (les 10
jobs, stricts), force push et suppression interdits. Les branches d'étude
`etude/moteur-java` et `etude/moteur-kotlin` sont **conservées** et
tagguées `etude-java-final` / `etude-kotlin-final` (décision propriétaire
du 03/11/2026).

## 5. Intégration avec l'hôte CodeIDE

- **Java** : audit des logs appareil et plan de branchement —
  [`lsp-java/docs/INTEGRATION-CODEIDE.md`](lsp-java/docs/INTEGRATION-CODEIDE.md) ;
  procédure de bascule (substitution composite ADR 0015, rails
  consommateurs poussés dans `CodeIDE` @ `8450bb7`) —
  [`lsp-java/docs/BASCULE-CODEIDE.md`](lsp-java/docs/BASCULE-CODEIDE.md) ;
  gabarit de passe bascule sur appareil —
  [`docs/GABARIT-BASCULE-APPAREIL.md`](docs/GABARIT-BASCULE-APPAREIL.md).
- **Kotlin** : séquencement de bascule I0–I6 **avant** l'extension des
  capacités (ADR 0136, écart GK-027/GK-028) —
  [`lsp-kotlin/docs/adr/0136-surface-protocole-et-bascule-codeide.md`](lsp-kotlin/docs/adr/0136-surface-protocole-et-bascule-codeide.md).
- Surface LSP exacte de chaque serveur : [`lsp-java/USAGE.md`](lsp-java/USAGE.md)
  et [`lsp-kotlin/USAGE.md`](lsp-kotlin/USAGE.md).

## 6. Gouvernance (clean-room, provenance, licences)

- **Licence du dépôt : Apache-2.0** ([`LICENSE`](LICENSE), [`NOTICE`](NOTICE),
  ADR 0001). Dépôt propriétaire `jjoblab` (Olson).
- **Clean-room strict** pour les moteurs réécrits : l'ancien code moteur de
  `codeide-lsp` n'est jamais consulté (sauf oracle en boîte noire) ; la
  base de spécification est le **cahier** extrait des tests. Détails :
  [`docs/GOUVERNANCE.md`](docs/GOUVERNANCE.md).
- **Provenance** : toute reprise depuis un ancien dépôt est inscrite **au
  préalable** dans un manifeste ([`lsp-java/docs/PROVENANCE-REPRISE.md`](lsp-java/docs/PROVENANCE-REPRISE.md),
  [`lsp-kotlin/docs/PROVENANCE-REPRISE-KOTLIN.md`](lsp-kotlin/docs/PROVENANCE-REPRISE-KOTLIN.md)) ;
  la CI refuse tout fichier hors registre
  ([`scripts/verifier-provenance.sh`](scripts/verifier-provenance.sh)).
- **Références externes** : liste blanche de projets tiers nommables
  (IntelliJ Platform/Android Studio comme références de comportement,
  ecj/JDT, lsp4j, compilateur Kotlin K2, grammaire KMP vendorée…) —
  aucun autre nom, aucune copie de code.

## 7. Travailler sur le dépôt

Les règles complètes (langue, KDoc, conventions de commits, procédure de
push, travail multi-agents) sont dans [`AGENTS.md`](AGENTS.md). En bref :

- **français partout** (documentation, KDoc, commits), **Kotlin 100 %** ;
- avant chaque push : `git pull --rebase` puis
  `GITHUB_TOKEN=<jeton> scripts/verifier-push.sh` (identité `jjoblab`,
  distant `github.com/jjoblab/code-lsp`) ;
- jamais de `push --force` sur une branche partagée, jamais de squash/rebase
  des branches publiées — fusions par **commit de fusion** ;
- un agent = **un périmètre de dossiers déclaré** ; la **CI est l'arbitre**.

## 8. Index des documents

- Transverse : [`docs/INDEX.md`](docs/INDEX.md) (gouvernance, réorganisation,
  gabarits communs) et [`docs/GOUVERNANCE.md`](docs/GOUVERNANCE.md).
- Par LSP : [`lsp-java/docs/INDEX.md`](lsp-java/docs/INDEX.md) (ADR 0001–0032,
  cahier, études) et [`lsp-kotlin/docs/INDEX.md`](lsp-kotlin/docs/INDEX.md)
  (ADR 0100–0136, cahier, études, journaux d'étages).
- Historique de la mission Java (ancien README racine, conservé tel quel) :
  [`lsp-java/docs/HISTORIQUE-README-RACINE.md`](lsp-java/docs/HISTORIQUE-README-RACINE.md).

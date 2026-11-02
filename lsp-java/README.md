# lsp-java — serveur de langage Java pour CodeIDE (`lspjava`)

> Moteur Java LSP de CodeIDE, réécrit **depuis zéro** (ADR 0001, option 2 —
> réécriture intégrale, clean-room) : aucun code du moteur vendored de
> `codeide-lsp` n'est repris. La base contractuelle est le
> [cahier de spécifications](docs/cahier-specifications/) (66 fiches, 449
> comportements extraits des tests de l'ancien moteur).

## État et version (mesurés)

| Grandeur | Valeur | Source |
|---|---|---|
| Statut | **EN SERVICE** dans CodeIDE — bascule §8.5 signée le 30/10/2026 (checklist 6/6, passe appareil moto g06 18/18 / 0 saut) | `lsp-java/docs/HISTORIQUE-README-RACINE.md` · [gabarit](../docs/GABARIT-BASCULE-APPAREIL.md) |
| Version de tête | **1.13.0** (vague V-H — répartiteur au protocole) | `build.gradle.kts` (racine) l. 410 |
| Dernière version publiée | **1.12.2** (02/11/2026) — `jo.codeide.lsp.java:jo-codeide-lsp-java` sur GitHub Packages | registre de publication, run id 41372554541 |
| Tests | **959 / 0 échec** (45 gates ignorées avec raison ; banc projet réel exécuté) | PV vague V-H (commit `4724fe9`) — build strict P1–P8 |
| Jar du moteur | tête : `java-1.13.0.jar` **1 532 056 o** ; publié 1.12.2 : **1 523 043 o** | build local du 03/11/2026 (tâches `:java:jar`, méthode vérifiée au tag v1.12.2) |
| Compagnon ecj | `ecj-3.40.0-art.jar` **3 288 188 o** (3 211 Ko, constant sur toutes les versions publiées) | paquets GitHub, 11 versions mesurées |

## Périmètre (servi / absent)

**Servi** (surface LSP exacte : [`USAGE.md`](USAGE.md)) : complétion
membre/type/smart/typedef avec classement et gel, survol riche, inlay
hints, aide à la signature, diagnostics (analytiques + rapides, inspections,
familles Android Lint), navigation exhaustive (definition, typeDefinition,
implementation, usages multi-fichiers, hiérarchies), renommage sûr par
phases, actions de code et refactorings (génération, extract/inline, change
signature, safe delete), formatage, pliage, organisateur d'imports,
symboles document/espace, semanticTokens, codeLens à resolve paresseux,
willRenameFiles, workDoneProgress d'indexation — plus les extensions
maison `x-codeide.completionRanking` (v2) et `x-codeide.usages` (v1), les
méthodes expérimentales `superDefinition` / `decompiledSource` et l'API
in-process `MoteurJava`.

**Absents / assumés** : fidélité §10.4 du vendored levée sur
`references` (ADR 0028 D1, capability versionnée) ; les limites documentées
sont listées dans [`USAGE.md`](USAGE.md) et
[`lsp-java/docs/ECARTS-SERVEUR.md`](docs/ECARTS-SERVEUR.md).

## Architecture en 10 lignes

1. **ecj 3.40.0 patché** par [`:ecj-art`](../socle/ecj-art/) (patcheur
   build-time — bridge `Compiler.resolve`, arbres vivants jamais nettoyés).
2. **Routeur de classpath** sources → jars → jrt → stubs intrinsèques,
   FQCN longest-match ; jrt runtime ou externe replié honnêtement sur ART.
3. **Moteur par module** + routeur fichier→module relu à chaque requête.
4. **Ordonnanceur 3 voies** (FRAPPE / NAVIGATION / CONFORT, supersession à
   valeur, exclusion par fichier — vague V-H, ADR 0010).
5. **Double-tampon + époques** : 0 figage, 0 réponse périmée (ADR 0029).
6. **Diagnose rapide** par corps de méthode + repli syntaxe instantané sans
   plateforme (fiches 014/028).
7. **Chaîne de complétion** : échelle de variantes du splice, collecte par
   genre, peseurs, gel des têtes + plafond honnête 120 (ADR 0012).
8. **Modèle Android** : référentiel de ressources XML, classes light
   (R, BuildConfig, ViewBinding), interop Kotlin conservatrice (ADR
   0020/0021/0022).
9. **Bancs à baselines signées** : qualité, usages, parité croisée (ADR
   0016) — la CI échoue sur régression.
10. **Protocole lsp4j** complet + capabilities maison versionnées (ADR
    0031) ; le détail : [`ARCHITECTURE.md`](ARCHITECTURE.md).

## Démarrage, tests et bancs

```bash
./gradlew build                     # build + tests (Java et socle)
./gradlew :java:bench               # empreinte/latence noyau N1
./gradlew :java:benchQualite        # qualité complétion — ÉCHOUE sur régression vs baseline
./gradlew :java:benchUsages         # usages (rappel = oracle, CI-bloquant)
./gradlew :java:benchParite         # parité croisée vs oracle vendored épinglé
ANDROID_JAR=… ./gradlew :java:test  # ouvre les gates Android
```

Chiffres de référence mesurés (v1.12.0, en-tête de version du
`build.gradle.kts` racine) : démarrage à froid 893 ms, diagnose complet
p50 13,1 ms / rapide p50 4,9 ms, hover p50 0,11 ms, tas au repos 35 Mo ;
banc qualité d8 1,000/1,000/1,000 (corpus 24 positions, split
train/éval déterministe ADR 0030) ; banc usages rappel 1,000 / p95 ≤ 51 ms.

## Documentation du LSP Java

| Document | Rôle |
|---|---|
| [`USAGE.md`](USAGE.md) | guide d'utilisation du serveur `lspjava` — surface LSP exacte, cycle de vie, 17 fonctionnalités avec exemples JSON-RPC, API in-process |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | composants, flux de données, décisions structurantes, contraintes Android/ART, budgets |
| [`CHANGELOG.md`](CHANGELOG.md) | versions du moteur (1.0.0 → 1.13.0), format Ajouté / Modifié / Corrigé |
| [`AGENTS.md`](AGENTS.md) | règles propres au LSP Java (clean-room, nommage `Ecj*`/`Jdt*`, pièges) — renvoie au [`AGENTS.md`](../AGENTS.md) racine |
| [`docs/INDEX.md`](docs/INDEX.md) | index des ADR (0001–0032), du cahier, des études, des journaux et alertes |
| [`lsp-java/docs/BASCULE-CODEIDE.md`](docs/BASCULE-CODEIDE.md) | procédure de bascule côté client CodeIDE (publication, rails, retour arrière) |
| [`lsp-java/docs/INTEGRATION-CODEIDE.md`](docs/INTEGRATION-CODEIDE.md) | audit des logs appareil et plan de branchement complet I0–I6 |
| [`lsp-java/docs/ECARTS-SERVEUR.md`](docs/ECARTS-SERVEUR.md) | inventaire des écarts reportés / non étudiés |
| [`lsp-java/docs/PROVENANCE-REPRISE.md`](docs/PROVENANCE-REPRISE.md) | manifeste de provenance (reprises contrôlées, registre) |

Bancs et baselines : [`lsp-java/bancs/`](bancs/) (corpus, projet Android réel,
baselines signées `lsp-java/bancs/baselines/completion.json` et
`parite-completion.json` — SHA-256 consignés dans
[`docs/REORGANISATION-AVANT.md`](../docs/REORGANISATION-AVANT.md) §4).

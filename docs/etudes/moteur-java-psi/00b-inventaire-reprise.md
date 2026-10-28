# 00b — Inventaire de reprise (Phase 0)

> Mission « étude moteur Java PSI » — inventaire des modules des anciens dépôts, statut de provenance (règle R9 du prompt) et verdict **reprendre / réécrire / abandonner**.
> Anciens dépôts en lecture seule : `jjoblab/codeide-lsp` @ `cb809e14` (v1.16.0, extraction du 2026-10-17), `jjoblab/codeide-tooling` @ `b60f7f0`, `jjoblab/CodeIDE` @ `54e99c6` (v0.202.0), `jjoblab/code-editor` @ `066b8c0`.
> Entrées de mission appliquées : ADR 0108, 0109, 0110 (clôture 19/10/2026), 0111, 0200 de `codeide-lsp` ; `docs/THIRD_PARTY_NOTICES.md` ; `AGENTS.md` + `docs/STRATEGIE_VERIFICATION.md` de CodeIDE. Aucun fichier n'est copié à ce stade (R9.1 : rien avant la décision de licence du nouveau dépôt, ADR 0001).

## 0. Rappel du cadre R9 (ce qui gouverne tout l'inventaire)

- **Exclus par défaut** (R9.3) : le moteur vendored `lsp/java` dans son ensemble (≈25 800 lignes Java main), le patcheur `lsp/ecj-art`, tout fichier classé niveau A ou B par l'ADR 0110, et tout fichier portant un nom identique à celui du projet tiers de référence. Leur reprise exige une **décision écrite du propriétaire** (ADR 0001) fondée sur l'une des voies de l'ADR 0110 : réécriture cloisonnée depuis spécifications, autorisation écrite de l'auteur, ou licence compatible assumée.
- **Fait d'entrée important** : l'ADR 0110 est **clôturé** dans l'ancien dépôt (niveau A réécrit depuis la spec JDK en v0.94.0 ; niveau B attesté travail original par le propriétaire les 16 et 19/10/2026 ; renommage intégral `Jdt*→Ecj*` v0.95.0 ; purge des désignations tiers — ADR 0200 ; publication Packages autorisée depuis v1.0.0). Ce fait **n'annule pas** l'exclusion par défaut du prompt de mission (qui la pose expressément), mais il constitue une **pièce à conviction disponible** pour la décision ADR 0001 : la voie « réécriture cloisonnée » a déjà été exécutée une fois pour le niveau A, et l'attestation de paternité du niveau B existe au propre.
- Les modules « écrits à la main » selon le README (model, index, host, parseurs Kotlin/XML) restent des **candidats** soumis au même contrôle (R9.4), avec leurs attributions (grammaire Kotlin vendorée, etc.).

## 1. codeide-lsp — module par module

Comptages réels (commandes `rg --files | wc -l`, `xargs wc -l` au commit indiqué ; lignes main / fichiers de test).

| Module | Rôle (README + code) | Taille | Dépendances clés | Tests | Statut provenance (R9) | Verdict |
|---|---|---|---|---|---|---|
| `:lsp:model` | Modèle de projet LSP — snapshots immuables depuis `ClasspathResult` du protocole tooling | 11 f / 2 160 l. main ; 5 f. de test | `com.github.jjoblab.codeide-tooling:protocol` 1.0.0 ; kotlinx-coroutines/serialization | inclus dans les 498 tests Java module+host (plan stabilisation) | **Candidat** — couche neuve écrite à la main d'après ADR 0110 §« Décisions d'ingénierie » (« les nouvelles couches sont écrites à partir du présent ADR et de docs/LSP_JAVA.md ») ; MAIS dépend du protocole tooling dont **le dépôt source n'a pas de fichier LICENSE à ce jour** (notice tierce, ligne du 2026-10-20) | **Reprendre** (après décision ADR 0001), à condition d'assainir la chaîne de licences du protocole en entrée (voir §3) |
| `:lsp:index` | Index disque en segments par jar — clés de contenu, fusion LSM, budget octets (ADR 0111) | 33 f / 6 472 l. main ; 15 f. de test (+ banc `scripts/lsp-index`) | Kotlin JVM pur ; kotlinx | idem | **Candidat** — même fondement que model ; l'étude D7 confirme la valeur : l'index CIDX bat le design IntelliJ sur les axes mobiles mesurables (préfixe binaire p50 0,16–0,76 ms ; +1 dépendance : seul le jar nouveau ; budget 4,2–29,7 Mo ; corruption par segment) | **Reprendre** — c'est le socle que l'étude recommande de conserver (ADR 0008 à venir) |
| `:lsp:java` | Moteur Java vendored — ecj 3.40.0 patché pour ART, lsp4j 0.21.2 ; ≈25 839 l. Java main (53 fichiers), 65 f. / 16 229 l. de tests, **449 méthodes @Test/@TestFactory** | 118 f / 42 068 l. main total | ecj (EPL-2.0, patché et graphé) ; lsp4j (EPL-2.0) ; gson (Apache-2.0) | 449 tests, 0 échec, 76 ignorés (plan stabilisation) ; **dérive vs 400 annoncés** (README 21/10) | **Exclu par défaut** (R9.3) malgré la clôture ADR 0110/0200 — reprise conditionnée à décision écrite ADR 0001 | **Décision propriétaire requise** — recommandation de l'étude : hybride (statu quo ecj consolidé + designs IntelliJ greffés), donc « reprendre sous condition » est la voie de moindre risque ; alternative « réécrire » = repartir de zéro sur ecj avec specs (coût maximal) |
| `:lsp:ecj-art` | Patcher ASM build-time de l'ecj (Runtime$Version/StackWalker/readAllBytes → shims) — jamais dans l'APK | 2 f / 468 l. | org.ow2.asm 9.7 (BSD-3) | tests dans `:lsp:java` (CompatShimsTest) | **Exclu par défaut** (R9.3 : « l'approche est reprise d'un projet tiers », ADR 0110 §mesures ; passes d'origine `RelocateTypesInJar.kt`/`EclipseStreamArtPass.kt` dans le build-logic du tiers) | **Réécrire** si la voie ecj est retenue — petite surface (468 l.), dicée par l'API ASM, réécrivable depuis la javadoc JDK + JEP 322 + doc ASM (le niveau A a déjà été réécrit une fois selon ce procédé) |
| `:lsp:testing` | Suite d'intégration moteur ↔ index (au-dessus des deux) | 4 f / 1 464 l. | scope test uniquement, **aucune dépendance externe publiée** (notice §4) | — | **Candidat** | **Reprendre** avec le moteur et l'index (scénarios de parité) |
| `:lsp:host` | L'HÔTE — SessionLsp, orchestrateur sync → modèle → index → moteurs (ADR 0109) | 51 f / 21 071 l. main ; 37 f. de test | `:lsp:java`, `:lsp:model` (api) ; `:lsp:index` (impl) ; `:lsp:kotlin`, `:lsp:xml` (api) ; **cel-lsp / cel-lsp-api 3.72.0 (code-editor, Apache-2.0)** ; lsp4j | 141 tests (README) | **Candidat** — écrit à la main ; mais orchestre **trois langues** (Java, Kotlin, XML) | **Ne pas reprendre tel quel dans le nouveau dépôt** : il reste dans codeide-lsp tant que kotlin/xml y vivent (R8) ; le nouveau dépôt expose une **façade moteur Java** (API publique `jo.lspjava.api.*` existante) que l'hôte consommera — découpage à trancher en ADR 0002 |
| `:lsp:kotlin` | Moteur Kotlin — grammaire officielle du compilator VENDORÉE (ADR 0115) | 124 f / 42 518 l. (vendor inclus) ; 218 tests | grammaire JetBrains Apache-2.0 (LICENSE/COPYRIGHT copiés dans `vendor/`) ; syntax-api 0.3.340 ; kotlin-metadata-jvm | 218 tests verts | Candidat mais **hors périmètre** (R8 : moteur Java uniquement) | **Abandonner pour ce dépôt** — reste dans codeide-lsp |
| `:lsp:xml` | Parseur XML Android tolérant écrit main, zéro dépendance | 70 f / 14 739 l. ; 245 tests + 134 host | aucune | 245 tests | Candidat mais **hors périmètre** (R8) | **Abandonner pour ce dépôt** — reste dans codeide-lsp |
| `build-logic` | Plugins de convention (qualité, frontières, publication) | ~7 plugins Kotlin | — | — | Candidat | **S'inspirer** (règles spotless/detekt/frontières) sans copie obligatoire — le squelette Gradle minimal du spike est le seul besoin immédiat (R3) |
| `docs/` | 120 ADR hérités gelés (0001–0120) + 27 propres (0200+) + rapports | ~230 fichiers .md | — | — | **Entrées de mission** (R6.6 : « à lire et appliquer, pas à réécrire ») | **Abandonner la copie** — le nouveau dépôt a sa propre numérotation (ADR 0001–0018 de la mission) ; renvois par citation |

## 2. codeide-tooling — le protocole

- `tooling/protocol` : **5 fichiers / 1 316 lignes** main (messages, framing doré, golden JSON) — c'est la seule pièce du dépôt frère que le périmètre moteur Java consomme (via `:lsp:model` → `ClasspathResult`).
- Total `tooling/` (server, api, protocol) : 50 fichiers / 6 594 lignes main — le serveur Gradle est hors périmètre moteur Java (il appartient à la chaîne d'installation/sync de CodeIDE).
- **Point de vigilance licence** : `docs/THIRD_PARTY_NOTICES.md` de codeide-lsp note que le dépôt `jjoblab/codeide-tooling` « **pas de fichier LICENSE sur le dépôt à ce jour** ». C'est un projet du propriétaire (même organisation), donc le risque est interne, mais la chaîne de provenance du nouveau dépôt exige que le protocole soit éclairé : soit licence explicite ajoutée au dépôt source, soit réécriture des ~10 types consommés (modeste).
- **Verdict** : **reprendre comme dépendance d'artefact** (déjà le mécanisme : `com.github.jjoblab.codeide-tooling:protocol`) + demander l'ajout d'un fichier LICENSE au dépôt frère (action propriétaire, à consigner dans l'ADR 0017).

## 3. CodeIDE et code-editor — l'intégration pertinente uniquement (R8/R9)

- **CodeIDE** (v0.202.0) : l'intégration LSP tient dans `settings.gradle.kts` (lignes 226–251) — substitution composite en DEV des 8 artefacts `com.github.jjoblab.codeide-lsp:*` vers les projets frères, consommation par GitHub Packages en production ; « `feature:editor` ne voit que `:lsp:host` » (ligne 349). Règles de travail qui nous concernent : `AGENTS.md` (**Kotlin 100 %, aucun Java écrit à la main** — exemption vendored documentée par ADR 0108 ; **français** pour KDoc/commits/docs ; minSdk 26 ; targetSdk 28 délibéré W^X) et `docs/STRATEGIE_VERIFICATION.md` (**le local attrape le bon marché et déterministe, la CI porte le lourd** — applicable au nouveau dépôt : tests du module `:spike` en local uniquement, R4). **Rien à copier** — intégration par consommation d'artefacts.
- **code-editor** : `cel-lsp` (306 fichiers / 80 993 lignes main, Apache-2.0 avec `LICENSE` + `NOTICE` — embarque une dépendance LSP sous EPL-2.0 notée en amont) est le client LSP in-process consommé par `:lsp:host`. **Rien à copier** — dépendance d'artefact déjà en place ; le NOTICE du nouveau dépôt devra reflécter la chaîne si l'hôte migre un jour.

## 4. La valeur des 449 tests de `lsp/java` (§4 bis — comment la conserver sans reprendre le code)

Les 65 fichiers de tests (16 229 lignes, 449 méthodes, 0 échec, 76 ignorés) encodent des comportements éprouvés (fidélité ART, complétion, rename honnête, hiérarchies, formateur…). Si le propriétaire tranche « réécrire » (ou pendant la période d'exclusion), la valeur se conserve ainsi :

1. **Extraire les comportements, pas le code** : pour chaque test, une fiche de spécification (préconditions, entrée, comportement attendu, borne) rédigée **sans lire le code de production repris** — le corpus devient un cahier de recette réutilisable contre n'importe quel moteur (voie réécriture comme voie embarquée PSI).
2. **Jeux de données** : fixtures, golden files et scénarios (p.ex. `RenameHonneteTest`, `SubpackageGoldenRulesTest`, `CompletionRegressionTest`) sont ré-exploitables tels quels s'ils sont des **données** (entrées/sorties attendues) et non du code de test dépendant du moteur.
3. **Banc de non-régression croisé** : le même corpus rejoué contre le statu quo ecj et contre la voie retenue donne la mesure de parité exigée par l'ADR 0016 (baselines versionnées).
4. La dérive de comptage (449 réels vs 400 annoncés au prompt ; README « 400 » au 21/10 ; plan stabilisation « 498 tests Java module+host ») est consignée dans `ALERTES.md`.

## 5. Périmètre proposé pour le nouveau dépôt (conclusion de la Phase 0)

Proposition (à trancher par le propriétaire en ADR 0002) :

- **Cœur** : `model` + `index` + `testing` (reprise après ADR 0001) + le moteur Java selon la voie tranchée (reprise conditionnelle du vendored, ou réécriture) — soit le **domaine moteur Java**, autonome vis-à-vis de l'UI.
- **Non inclus** : `host` (reste dans codeide-lsp pour orchestrer les trois langues ; le nouveau dépôt expose la façade), `kotlin`, `xml` (R8), tout code CodeIDE/code-editor (intégration par artefacts).
- **Dépendances d'artefacts** : protocole tooling (licence à clarifier), ecj + lsp4j (EPL-2.0) si la voie statu quo/hybride est retenue, java-syntax JetBrains (Apache-2.0) si la voie V1/V2 du spike est retenue.
- Le nouveau dépôt ne dépend d'aucun ancien dépôt **au build ni à l'exécution** (R3) : les reprises sont des copies contrôlées, listées dans `docs/PROVENANCE-REPRISE.md`.

## 6. Actions demandées au propriétaire (verrouillent la suite)

1. **ADR 0001** — licence du nouveau dépôt et règles de reprise (décision bloquante : aucune copie avant).
2. Ajout d'un fichier `LICENSE` au dépôt `jjoblab/codeide-tooling` (éclaire la chaîne model→protocol).
3. Confirmation du périmètre (§5) dans l'ADR 0002.

# AGENTS.md — règles de travail du dépôt code-lsp

Destiné aux agents et contributeurs intervenant sur ce dépôt. Ces règles
complètent les ADR (`docs/adr/`) — en cas de doute, l'ADR prime.

## Identité et vision

- **Vision du propriétaire (28/10/2026)** : code-lsp sera **le dépôt officiel
  des LSP pour CodeIDE une fois terminé** — un LSP Java « frais, plus solide,
  plus intelligent, plus robuste, plus propre et professionnel », grâce à
  l'architecture IntelliJ PSI (étudiée en profondeur dans
  `docs/etudes/moteur-java-psi/`, embarquée nulle part, greffée partout où
  elle prouve sa valeur — GO PARTIEL, ADR 0004).
- Le moteur Java est une **réécriture intégrale en Kotlin** (ADR 0001,
  option 2) : aucun code du vendored de `codeide-lsp` n'est repris.

## Langue et documentation

- **Français obligatoire** : documentation, commentaires, KDoc/Javadoc et
  messages de commit (décision propriétaire du 28/10/2026).
- **KDoc/Javadoc détaillé obligatoire** : chaque classe publique et chaque
  membre public documentent rôle, préconditions, paramètres et retours. Un
  code non documenté n'est pas fini.

## Code

- **Kotlin 100 %** — aucun Java écrit à la main (R2 ; ADR 0001 : le moteur
  réécrit naît en Kotlin, l'exemption « vendored » de l'ancien dépôt ne
  s'applique pas ici).
- **Nommage des classes (décision propriétaire du 28/10/2026)** : les
  préfixes `Ecj*`/`Jdt*` sont **réservés aux classes qui dérivent réellement
  d'ECJ/JDT** (adaptateurs du compilateur ecj, ponts vers l'AST JDT,
  encapsulations directes de ces bibliothèques). Toute autre classe —
  réécriture Kotlin pure, service, façade, cache, utilitaire — porte un **nom
  neutre** décrivant son rôle (ex. `MoteurDiagnostics`, `CacheAnalyse`,
  `OrganiseurImports`). L'ancien dépôt préfixait massivement `Ecj*` des
  classes sans lien réel avec ecj ou jdt : pratique **abandonnée**. Les noms
  `Ecj*Test` des fiches du cahier sont les identifiants historiques des tests
  vendored — ils ne préjugent pas du nom des classes neuves.
- Clean-room strict pour le moteur : lecture **interdite** de
  `lsp/java/src/main` de `codeide-lsp` (production vendored). Le vendored
  s'utilise uniquement comme **oracle en boîte noire** (l'exécuter et
  comparer ses sorties). La base de spécification est le **cahier**
  (`docs/cahier-specifications/`), extrait des tests uniquement.

## Provenance (R9)

- Toute reprise depuis un ancien dépôt doit être inscrite **au préalable**
  dans `docs/PROVENANCE-REPRISE.md` (dépôt, chemin, commit, auteur, licence,
  statut, décision, justification — jamais « en bloc »).
- La CI refuse tout fichier hors registre (`scripts/verifier-provenance.sh`).

## Git et push — TRÈS IMPORTANT

- **Avant CHAQUE push** : exécuter `GITHUB_TOKEN=<jeton> scripts/verifier-push.sh`.
  Le push n'est autorisé que si le script confirme :
  1. le distant est bien `github.com/jjoblab/code-lsp` ;
  2. l'utilisateur GitHub authentifié par le jeton est **`jjoblab`** ;
  3. l'auteur git (`user.name`) est **`jjoblab`** et son courriel
     (`user.email`) est **`olson12jb@gmail.com`**.
  (Décision propriétaire du 28/10/2026 — un push effectué sans cette
  vérification est une faute de procédure.)
- **Crochet `pre-push` automatique (vague 1c)** : le dépôt fournit
  `.githooks/pre-push`, qui REFUSE le push si l'identité git n'est pas
  `jjoblab <olson12jb@gmail.com>` — même si la vérification manuelle a été
  oubliée. Avec `GITHUB_TOKEN` dans l'environnement, il exécute en plus la
  vérification complète (`scripts/verifier-push.sh`). Activation après tout
  clonage (`.git/hooks` n'étant pas versionné) :
  `git config core.hooksPath .githooks`
- Auteur git du dépôt : `jjoblab <olson12jb@gmail.com>` (courriel communiqué
  par le propriétaire le 28/10/2026). **Historique réattribué** : le 28/10/2026,
  l'intégralité des commits du dépôt a été réécrite sous cette identité — les
  SHA cités dans les échanges antérieurs à cette date ne correspondent plus à
  rien dans le dépôt ; les références internes ont été corrigées.
- Les jetons ne sont **jamais** committés ni écrits dans le dépôt (R6.4).
- Commits : conventions conventional commits préfixées `lsp-java(scope):`,
  en français, une décision ou une capacité par commit autant que possible.

## Lectures préalables avant de coder

1. `docs/etudes/moteur-java-psi/00-synthese.md` — décision GO PARTIEL et
   impact de l'ADR 0001 (réécriture intégrale).
2. `docs/adr/0001` (réécriture, plan de migration), `0004` (stratégie),
   `0002` (périmètre et vision), `0005`–`0018` au fil des domaines.
3. `docs/cahier-specifications/` — cahier des comportements attendus
   (extraction des 449 tests de l'ancien moteur) : c'est le contrat.
4. `spike/README.md` — bancs de mesure et chiffres de référence.
5. `USAGE.md` — guide d'utilisation du serveur (`lspjava`) : surface LSP
   exacte (capabilities, extensions expérimentales), API in-process du
   moteur — la référence avant toute modification de comportement visible
   du client.

## Références externes — liste blanche (décision propriétaire, 31/10/2026)

- **Nommables** (code, KDoc, ADR, commits, rapports, documentation) :
  **IntelliJ Platform**, **Android Studio**, le **plugin Kotlin de l'IDE**
  (mode K2, Analysis API), le **plugin Android**, **Android Lint** — comme
  *références de comportement* en lecture seule —, le **compilateur Kotlin
  K2** (oracle), **kotlin-metadata-jvm**, la **grammaire KMP vendorée**,
  **lsp4j**, et les artefacts déjà inscrits au registre de provenance.
- **Aucun autre projet tiers ne doit être mentionné, nulle part.** Si un
  comportement s'inspire d'un projet hors liste, on décrit le comportement
  et la décision **pour eux-mêmes**, sans le nommer.
- **Corpus de mesure** (exception) : les dépôts inscrits au registre de
  provenance en qualité de **données de corpus** (cibles de test, jamais
  committées) sont nommables avec leur rôle — voir
  `docs/PROVENANCE-REPRISE-KOTLIN.md` §Corpus et
  `bancs/corpus-android-kotlin/LISEZMOI.md`.
- **Aucune copie de code** d'IntelliJ/Android Studio/plugin Kotlin :
  comportements et designs seulement (lecture seule, réécriture
  clean-room) ; toute reprise littérale, même de `testData`, passe par le
  registre + `NOTICE` (Apache-2.0 : attribution + modifications).
- Commits : le préfixe du domaine Kotlin est `lsp-kotlin(scope):` (miroir
  de `lsp-java(scope):` ci-dessus), en français, une décision ou capacité
  par commit autant que possible.

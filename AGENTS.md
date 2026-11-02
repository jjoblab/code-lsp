# AGENTS.md — règles de travail du dépôt code-lsp

Destiné aux agents et contributeurs intervenant sur ce dépôt. Ces règles
complètent les ADR (par LSP : [`lsp-java/docs/adr/`](lsp-java/docs/adr/),
[`lsp-kotlin/docs/adr/`](lsp-kotlin/docs/adr/)) — en cas de doute, l'ADR
prime. Chaque LSP ajoute ses règles propres dans son `AGENTS.md`
([`lsp-java/AGENTS.md`](lsp-java/AGENTS.md),
[`lsp-kotlin/AGENTS.md`](lsp-kotlin/AGENTS.md)).

## Identité et vision

- **Vision du propriétaire (28/10/2026)** : code-lsp sera **le dépôt officiel
  des LSP pour CodeIDE une fois terminé** — un LSP Java « frais, plus solide,
  plus intelligent, plus robuste, plus propre et professionnel », grâce à
  l'architecture IntelliJ PSI (étudiée en profondeur dans
  `lsp-java/docs/etudes/moteur-java-psi/`, embarquée nulle part, greffée partout où
  elle prouve sa valeur — GO PARTIEL, ADR 0004).
- Le moteur Java est une **réécriture intégrale en Kotlin** (ADR 0001,
  option 2) : aucun code du vendored de `codeide-lsp` n'est repris.
- Le moteur Kotlin suit la **voie C hybride** (ADR 0100) : noyau réécrit
  clean-room depuis le cahier, modules différenciants repris après contrôle,
  grammaire ré-obtenue de l'amont.

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
- Clean-room strict pour les moteurs : lecture **interdite** des zones
  « noyau » de production de l'ancien dépôt `codeide-lsp` (pour le Java :
  `lsp/lsp-java/moteur/src/main` ; pour le Kotlin : `parse/`, `resolve/`,
  `diagnostics/`, `completion/`, `session/`, `api/` de production — R9.1).
  Le vendored s'utilise uniquement comme **oracle en boîte noire**
  (l'exécuter et comparer ses sorties). Les bases de spécification sont les
  **cahiers** (`lsp-java/docs/cahier-specifications/`,
  `lsp-kotlin/docs/cahier-specifications-kotlin/`), extraits des tests
  uniquement.

## Provenance (R9)

- Toute reprise depuis un ancien dépôt doit être inscrite **au préalable**
  dans le manifeste du périmètre concerné
  ([`lsp-java/docs/PROVENANCE-REPRISE.md`](lsp-java/docs/PROVENANCE-REPRISE.md)
  ou [`lsp-kotlin/docs/PROVENANCE-REPRISE-KOTLIN.md`](lsp-kotlin/docs/PROVENANCE-REPRISE-KOTLIN.md) :
  dépôt, chemin, commit, auteur, licence, statut, décision, justification —
  jamais « en bloc »).
- La CI refuse tout fichier hors registre (`scripts/verifier-provenance.sh`).
- La grammaire ne se « reprend » jamais de l'ancien dépôt : elle se
  **ré-obtient de l'amont** (R9.2 — vendoring, notices obligatoires).

## Références externes (liste blanche — décision propriétaire du 31/10/2026)

- **Nommables** (code, KDoc, ADR, commits, rapports, documentation) :
  **IntelliJ Platform**, **Android Studio**, le **plugin Kotlin de l'IDE**
  (mode K2, Analysis API), le **plugin Android**, **Android Lint** — comme
  *références de comportement* en lecture seule —, **ecj/JDT**, **lsp4j**,
  **java-syntax**, le **compilateur Kotlin K2** (oracle),
  **kotlin-metadata-jvm**, la **grammaire KMP vendorée**, et les artefacts
  déjà inscrits aux registres de provenance.
- **Aucun autre projet tiers ne doit être mentionné, nulle part** (code,
  KDoc, ADR, CHANGELOG, commits, rapports, noms de tests). Si un design
  s'inspire d'un projet hors liste, son nom ne s'écrit pas : le comportement
  et la décision de conception se décrivent pour eux-mêmes.
- **Aucune copie de code** d'IntelliJ/Android Studio/plugin Kotlin : les
  comportements et designs seulement (lecture seule, réécriture
  clean-room) ; toute reprise littérale — même d'un fichier de test ou de
  donnée de test — passe par le registre + `NOTICE` (Apache-2.0 :
  attribution et indication des modifications). Par défaut : **extraire des
  comportements** (fiches du cahier), pas copier des fichiers.
- **Corpus de mesure** (exception) : les dépôts inscrits aux registres de
  provenance en qualité de **données de corpus** (cibles de test, jamais
  committées) sont nommables avec leur rôle — voir
  `lsp-kotlin/docs/PROVENANCE-REPRISE-KOTLIN.md` §Corpus et
  `lsp-kotlin/bancs/corpus-android-kotlin/LISEZMOI.md`.

## Git et push — TRÈS IMPORTANT

- **Avant CHAQUE push** : exécuter `GITHUB_TOKEN=<jeton> scripts/verifier-push.sh`.
  Le push n'est autorisé que si le script confirme :
  1. le distant est bien `github.com/jjoblab/code-lsp` ;
  2. l'utilisateur GitHub authentifié par le jeton est **`jjoblab`** ;
  3. l'auteur git (`user.name`) est **`jjoblab`** et son courriel
     (`user.email`) est **`olson12jb@gmail.com`**.
  (Décision propriétaire du 28/10/2026 — un push effectué sans cette
  vérification est une faute de procédure.)
- **Crochet `pre-push` automatique** : le dépôt fournit `.githooks/pre-push`,
  qui REFUSE le push si l'identité git n'est pas
  `jjoblab <olson12jb@gmail.com>` — même si la vérification manuelle a été
  oubliée. Avec `GITHUB_TOKEN` dans l'environnement, il exécute en plus la
  vérification complète. Activation après tout clonage :
  `git config core.hooksPath .githooks`.
- Auteur git du dépôt : `jjoblab <olson12jb@gmail.com>` (courriel communiqué
  par le propriétaire le 28/10/2026). **Historique réattribué** : le
  28/10/2026, l'intégralité des commits a été réécrite sous cette identité —
  les SHA cités dans les échanges antérieurs ne correspondent plus à rien
  dans le dépôt.
- Les jetons ne sont **jamais** committés ni écrits dans le dépôt (R6.4).
- **Commits** : conventions conventional commits en français, préfixées par
  le domaine (`lsp-java(scope):`, `lsp-kotlin(scope):`, `refonte(scope):`,
  `fusion(scope):`), une décision ou une capacité par commit autant que
  possible.
- **Fusions** : par **commit de fusion** — jamais `squash`, jamais `rebase`
  des branches publiées, jamais de `push --force` sur une branche partagée.

## Travail multi-agents (décision consignée le 03/11/2026 — réorganisation)

- **Un agent = un périmètre de dossiers déclaré** (branche dédiée) ; un
  agent n'intervient jamais sur la branche de travail d'un autre agent.
- Modifier un module du `socle/` ou un fichier racine (CI, `settings.gradle.kts`,
  `AGENTS.md`, `NOTICE`…) exige une **demande d'interface** consignée au
  format `Q-…` (voir
  [`lsp-kotlin/docs/etudes/moteur-kotlin/DEMANDES-INTERFACE.md`](lsp-kotlin/docs/etudes/moteur-kotlin/DEMANDES-INTERFACE.md))
  — le propriétaire arbitre.
- **`git pull --rebase` avant chaque push** ; commits courts et fréquents.
- En cas de conflit sur un fichier partagé : **l'agent qui arrive en second
  résout** et le consigne dans son journal.
- **Un fichier de journal par agent et par chantier** (miroir des journaux
  d'étages `lsp-kotlin/docs/etudes/moteur-kotlin/`).
- **La CI est l'arbitre** : une tête n'est fusionnée que CI verte ; les
  casses sémantiques découvertes en fusion sont corrigées dans un commit
  `fix(fusion): …` minimal et expliqué.
- `main` est protégée (checks requis = les 10 jobs CI) ; les branches
  d'étude `etude/*` ne sont jamais supprimées ni rebasées.

## Lectures préalables avant de coder

1. [`README.md`](README.md) — carte des LSP, socle, démarrage, gouvernance.
2. L'`AGENTS.md` du LSP concerné ([`lsp-java/AGENTS.md`](lsp-java/AGENTS.md),
   [`lsp-kotlin/AGENTS.md`](lsp-kotlin/AGENTS.md)) puis l'index de sa
   documentation (`lsp-java/docs/INDEX.md`, `lsp-kotlin/docs/INDEX.md`).
3. Les ADR fondateurs : Java `0001` (réécriture), `0002` (périmètre et
   vision), `0004` (stratégie) ; Kotlin `0100` (voie C), `0103` (stratégie
   sémantique).
4. Le cahier de spécifications du LSP concerné — c'est le contrat.
5. `lsp-java/spike/README.md` — bancs de mesure et chiffres de référence.
6. [`lsp-java/USAGE.md`](lsp-java/USAGE.md) / [`lsp-kotlin/USAGE.md`](lsp-kotlin/USAGE.md) —
   surface LSP exacte (capabilities, extensions), la référence avant toute
   modification de comportement visible du client.

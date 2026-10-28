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

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
  3. l'auteur git (`user.name`) est **`jjoblab`**.
  (Décision propriétaire du 28/10/2026 — un push effectué sans cette
  vérification est une faute de procédure.)
- Auteur git du dépôt : `jjoblab <150948254+jjoblab@users.noreply.github.com>`.
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

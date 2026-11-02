# INDEX — documentation transverse du dépôt code-lsp

> Index des documents **transverses** (ni Java, ni Kotlin, ni XML). Chaque
> LSP a son propre index : [`lsp-java/docs/INDEX.md`](../lsp-java/docs/INDEX.md) ·
> [`lsp-kotlin/docs/INDEX.md`](../lsp-kotlin/docs/INDEX.md). Les documents
> racine : [`README.md`](../README.md) (vue d'ensemble) et
> [`AGENTS.md`](../AGENTS.md) (règles de travail).

## Gouvernance et procédures

| Document | Rôle |
|---|---|
| [`GOUVERNANCE.md`](GOUVERNANCE.md) | clean-room, provenance, licences, publication, CI — les règles de fond |
| [`GABARIT-BASCULE-APPAREIL.md`](GABARIT-BASCULE-APPAREIL.md) | gabarit de passe bascule sur appareil (commun aux LSP — rempli et signé pour le Java le 30/10/2026) |

## Réorganisation du dépôt (03/11/2026)

| Document | Rôle |
|---|---|
| [`REORGANISATION.md`](REORGANISATION.md) | plan de réorganisation validé (Phase 1 — classification, arborescence cible, ordre des commits) |
| [`MIGRATION-CHEMINS.md`](MIGRATION-CHEMINS.md) | table ancien chemin → nouveau chemin (générée par script, relue) |
| [`REORGANISATION-AVANT.md`](REORGANISATION-AVANT.md) | photographie mesurée « avant » (invariants : tests, baselines, dex, jobs CI) |
| [`REORGANISATION-BILAN-PHASE2.md`](REORGANISATION-BILAN-PHASE2.md) | PV d'exécution de la Phase 2 (invariants comparés, pièges P-1…P-8 traités) |

## Études transverses

| Document | Rôle |
|---|---|
| [`etudes/editeur-lsp/01-responsabilites-editeur-lsp.md`](etudes/editeur-lsp/01-responsabilites-editeur-lsp.md) | étude éditeur ↔ LSP (mandat propriétaire du 30/10/2026 — partage des responsabilités) |

## Où trouver le reste

- **ADR Java (0001–0032)** : [`lsp-java/docs/adr/`](../lsp-java/docs/adr/) — indexé dans [`lsp-java/docs/INDEX.md`](../lsp-java/docs/INDEX.md).
- **ADR Kotlin (0100–0136)** : [`lsp-kotlin/docs/adr/`](../lsp-kotlin/docs/adr/) — indexé dans [`lsp-kotlin/docs/INDEX.md`](../lsp-kotlin/docs/INDEX.md).
- **Cahiers de spécifications** : [`lsp-java/docs/cahier-specifications/`](../lsp-java/docs/cahier-specifications/) (66 fiches, 449 comportements) · [`lsp-kotlin/docs/cahier-specifications-kotlin/`](../lsp-kotlin/docs/cahier-specifications-kotlin/) (53 fiches, 469 comportements).
- **Journaux d'exécution Kotlin** (étages 0–6, transverse T, décisions post-clôture) : [`lsp-kotlin/docs/etudes/moteur-kotlin/`](../lsp-kotlin/docs/etudes/moteur-kotlin/).
- **Historique de la mission Java** (ancien README racine conservé tel quel) : [`lsp-java/docs/HISTORIQUE-README-RACINE.md`](../lsp-java/docs/HISTORIQUE-README-RACINE.md).

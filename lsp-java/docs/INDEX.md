# INDEX — documentation du LSP Java

> Index des documents du périmètre `lsp-java/` — sans réécrire les
> documents. Vue d'ensemble : [`lsp-java/README.md`](../README.md) ; règles
> propres : [`lsp-java/AGENTS.md`](../AGENTS.md). Index transverse :
> [`docs/INDEX.md`](../../docs/INDEX.md) du dépôt.

## ADR (32 — numéros, titres, statuts)

| ADR | Titre | Statut |
|---|---|---|
| [0001](adr/0001-licence-et-regles-de-reprise.md) | Licence du nouveau dépôt et règles de reprise | Accepté (option 2 — réécriture intégrale) |
| [0002](adr/0002-perimetre-du-depot-et-modules.md) | Périmètre du dépôt et modules | Accepté |
| [0003](adr/0003-index-de-l-etude.md) | Index de l'étude : ordre de lecture | Proposé |
| [0004](adr/0004-strategie-globale.md) | Stratégie globale : embarquer / s'inspirer / hybride | Accepté (GO PARTIEL) |
| [0005](adr/0005-packaging-et-compatibilite-art.md) | Packaging et compatibilité ART | Proposé |
| [0006](adr/0006-parsing-et-incrementalite.md) | Parsing et incrémentalité | Proposé |
| [0007](adr/0007-resolution-et-inference.md) | Résolution et inférence | Proposé |
| [0008](adr/0008-index-stubs-disque.md) | Index (stubs / disque) | Proposé |
| [0009](adr/0009-lecture-du-bytecode.md) | Lecture du bytecode, AAR, android.jar | Proposé |
| [0010](adr/0010-concurrence-annulation-priorites.md) | Concurrence, annulation, priorités | Accepté (exécuté par vagues — V-H) |
| [0011](adr/0011-memoire-et-cycle-de-vie.md) | Mémoire et cycle de vie | Proposé |
| [0012](adr/0012-completion-et-classement.md) | Complétion et classement | Proposé |
| [0013](adr/0013-diagnostics-inspections-fidelite.md) | Diagnostics, inspections, fidélité au compilateur | Proposé |
| [0014](adr/0014-navigation-refactorings-usages.md) | Navigation, refactorings, usages | Proposé |
| [0015](adr/0015-integration-lsp-et-remplacement-progressif.md) | Intégration LSP et remplacement progressif | Proposé |
| [0016](adr/0016-banc-de-qualite-et-non-regression.md) | Banc de qualité et non-régression | Accepté (exécuté par étapes) |
| [0017](adr/0017-licences-et-provenance-de-la-solution.md) | Licences et provenance de la solution | Proposé |
| [0018](adr/0018-version-jdt-et-shims.md) | Version de JDT ≥ 3.43 et API JDK à shimmer | Proposé |
| [0019](adr/0019-modele-de-code-tolerant-et-contexte-de-completion.md) | Modèle de code tolérant et contexte de complétion | Proposé |
| [0020](adr/0020-modele-de-projet-android-et-referentiel-de-ressources.md) | Modèle de projet Android et référentiel de ressources | Accepté (exécuté — V-B1/V-B2) |
| [0021](adr/0021-classes-light-android.md) | Classes « light » Android (R, BuildConfig, ViewBinding…) | Accepté (exécuté — V-B1/V-B3) |
| [0022](adr/0022-interoperabilite-kotlin-vers-java.md) | Interopérabilité Kotlin → Java | Accepté (exécuté — amendé V-A2, complément 1.12.2) |
| [0023](adr/0023-moteur-d-inspections-et-profil.md) | Moteur d'inspections et profil de sévérités | Accepté (livré — V-C1) |
| [0024](adr/0024-controles-android-lint-a-valeur-java.md) | Contrôles Android Lint à valeur Java | Accepté (livré — V-C2) |
| [0025](adr/0025-coherence-des-diagnostics.md) | Cohérence des diagnostics (pipeline unifié) | Accepté (GO — vague V-A) |
| [0026](adr/0026-completion-niveau-android-studio.md) | Complétion niveau Android Studio | Accepté (livrée — V-C3) |
| [0027](adr/0027-actions-de-code-etendues.md) | Actions de code étendues (catalogue, source.\*, refactor.\*) | Accepté (livrée en trois tranches) |
| [0028](adr/0028-recherche-et-navigation-exhaustives.md) | Recherche et navigation exhaustives | Accepté (GO D1–D9) |
| [0029](adr/0029-performance-concurrence-memoire-art.md) | Performance, concurrence, mémoire sur ART | Accepté (phases 1–4 — V-F) |
| [0030](adr/0030-qualite-mesuree-niveau-android-studio.md) | Qualité mesurée « niveau Android Studio » | Accepté (GO — D5 arbitrée) |
| [0031](adr/0031-surface-protocole.md) | Surface protocole (semanticTokens, kinds, progression…) | Accepté (livrée — v1.11.0) |
| [0032](adr/0032-complement-de-catalogue-familles-unitaires.md) | Complément de catalogue : familles unitaires | Accepté (livrée — v1.12.0) |

## Cahier de spécifications (le contrat)

[`cahier-specifications/`](cahier-specifications/) — **66 fiches, 449
comportements** extraits des 449 tests de l'ancien moteur (extraction
clean-room `codeide-lsp` @ `cb809e14`, lecture seule) ; fiches dans
[`cahier-specifications/fiches/`](cahier-specifications/fiches/).

## Études

| Étude | Contenu |
|---|---|
| [`etudes/moteur-java-psi/`](etudes/moteur-java-psi/) | architecture IntelliJ PSI (D1–D12) : synthèse, inventaire de reprise, mesures du spike, ALERTES, annexes |
| [`etudes/niveau-android-studio/`](etudes/niveau-android-studio/) | mission « niveau Android Studio » : synthèse, audit du moteur (KH1–KH16), référence Android Studio, matrice d'écarts G-029, plan par vagues V-A→V-H, fiches de comportement C-01…C-10, protocole de captures, gabarit de mesure appareil, rapport de divergences ecj/javac, ALERTES, captures (lots A–D) |

## Documents de pilotage et d'exploitation

| Document | Rôle |
|---|---|
| [`../USAGE.md`](../USAGE.md) | guide d'utilisation du serveur `lspjava` (surface LSP exacte, exemples JSON-RPC) |
| [`../ARCHITECTURE.md`](../ARCHITECTURE.md) | composants, flux, décisions structurantes, budgets |
| [`../CHANGELOG.md`](../CHANGELOG.md) | versions 1.0.0 → 1.13.0 (Ajouté / Modifié / Corrigé) |
| [`../AGENTS.md`](../AGENTS.md) | règles propres au LSP Java |
| [`PLAN-REECRITURE.md`](PLAN-REECRITURE.md) | programme de réécriture chiffré (étages N1–N8+T, 160–270 j-h, approuvé 28/10/2026) |
| [`RECALAGE-1.md`](RECALAGE-1.md) · [`RECALAGE-2.md`](RECALAGE-2.md) | recalages de fin N1 / fin N2 (vélocité, re-projection) |
| [`BASCULE-CODEIDE.md`](BASCULE-CODEIDE.md) | procédure de bascule côté client CodeIDE |
| [`INTEGRATION-CODEIDE.md`](INTEGRATION-CODEIDE.md) | audit des logs appareil, plan de branchement I0–I6 |
| [`ECARTS-SERVEUR.md`](ECARTS-SERVEUR.md) | inventaire des écarts reportés / non étudiés |
| [`REGISTRE-CLIENT-K2.md`](REGISTRE-CLIENT-K2.md) | registre client K2 (v1.11.0) |
| [`PROVENANCE-REPRISE.md`](PROVENANCE-REPRISE.md) | manifeste de provenance (reprises contrôlées socle, corpus) |
| [`HISTORIQUE-README-RACINE.md`](HISTORIQUE-README-RACINE.md) | ancien README racine, conservé tel quel (état au 02/11/2026) |

## Bancs et baselines

[`../bancs/`](../bancs/) — corpus de bancs, projet Android réel (réplique
monip, 16 AARs/jars), baselines signées
[`lsp-java/bancs/baselines/completion.json`](../bancs/baselines/completion.json) et
[`lsp-java/bancs/baselines/parite-completion.json`](../bancs/baselines/parite-completion.json)
(SHA-256 consignés dans
[`REORGANISATION-AVANT.md`](../../docs/REORGANISATION-AVANT.md) §4) ;
scripts de montage dans [`../scripts/`](../scripts/) ; bancs de mesure du
spike dans [`../spike/`](../spike/).

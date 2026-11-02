# INDEX — documentation du LSP Kotlin

> Index des documents du périmètre `lsp-kotlin/` — sans réécrire les
> documents. Vue d'ensemble : [`lsp-kotlin/README.md`](../README.md) ;
> règles propres : [`lsp-kotlin/AGENTS.md`](../AGENTS.md). Index
> transverse : [`docs/INDEX.md`](../../docs/INDEX.md) du dépôt.

## ADR (37 — numéros, titres, statuts)

| ADR | Titre | Statut |
|---|---|---|
| [0100](adr/0100-voie-gouvernance-et-regles-de-reprise-kotlin.md) | Décision de fond : voie de gouvernance A/B/C/D et règles de reprise | Accepté (voie C — hybride) |
| [0101](adr/0101-perimetre-et-frontieres-kotlin.md) | Périmètre du moteur Kotlin et frontières | Proposé |
| [0102](adr/0102-index-de-l-etude-kotlin.md) | Index de l'étude « moteur Kotlin » | Proposé |
| [0103](adr/0103-strategie-semantique-kotlin.md) | Stratégie sémantique (modèle maison / Analysis API K2 / hybride / externe) | Accepté (voie (i) + (iii) différée) |
| [0104](adr/0104-syntaxe-grammaire-officielle-et-incrementalite.md) | Syntaxe : grammaire officielle, incrémentalité, scripts | Proposé |
| [0105](adr/0105-modele-de-symboles-kotlin.md) | Modèle de symboles et déclarations | Proposé |
| [0106](adr/0106-resolution-portees-imports.md) | Résolution de noms, portées, récepteurs implicites, imports | Proposé |
| [0107](adr/0107-typage-et-inference-bornes.md) | Typage et inférence : périmètre assumé, « preuve ou silence » | Proposé |
| [0108](adr/0108-classpath-metadonnees-et-index.md) | Classpath, bytecode et métadonnées Kotlin ; partage CIDX | Proposé |
| [0109](adr/0109-synthetiques-android-et-genere.md) | Synthétiques Android et code généré | Proposé |
| [0110](adr/0110-interop-java-kotlin.md) | Interop Java ↔ Kotlin avec le moteur Java | Proposé |
| [0111](adr/0111-gradle-kotlin-dsl.md) | Gradle Kotlin DSL (`.gradle.kts`) | Proposé |
| [0112](adr/0112-diagnostics-fidelite-et-mode-profond.md) | Diagnostics : fidélité, faux positifs, mode profond | Proposé |
| [0113](adr/0113-completion-et-classement.md) | Complétion et classement (banc commun avec Java) | Proposé |
| [0114](adr/0114-navigation-renommage-usages.md) | Navigation, renommage, usages, hiérarchies | Proposé |
| [0115](adr/0115-formatage-imports-pliage-actions.md) | Formatage, imports, pliage, actions de code | Proposé |
| [0116](adr/0116-compose-previews.md) | Compose : previews et analyses spécifiques | Proposé |
| [0117](adr/0117-concurrence-annulation-sessions-memoire.md) | Concurrence, annulation, sessions, mémoire et GC sur ART | Proposé |
| [0118](adr/0118-integration-lsp-et-remplacement-progressif.md) | Intégration LSP/SPI et remplacement progressif dans CodeIDE | Proposé |
| [0119](adr/0119-banc-de-qualite-et-non-regression.md) | Banc de qualité et non-régression Kotlin | Proposé |
| [0120](adr/0120-licences-et-provenance-kotlin.md) | Licences et provenance de la solution retenue | Proposé |
| [0121](adr/0121-suivi-des-versions-kotlin-et-grammaire.md) | Suivi des versions de Kotlin et de la grammaire | Proposé |
| [0122](adr/0122-architecture-semantique-a-paliers.md) | Architecture sémantique à paliers (1/2/3) | Proposé |
| [0123](adr/0123-systeme-de-types-et-inference-palier-2.md) | Système de types et inférence étendus (palier 2) | Proposé |
| [0124](adr/0124-mode-profond-k2-palier-3.md) | Mode profond K2 (palier 3) | Proposé |
| [0125](adr/0125-catalogue-diagnostics-par-decidabilite.md) | Catalogue des diagnostics par décidabilité | Proposé |
| [0126](adr/0126-completion-niveau-android-studio.md) | Complétion « niveau Android Studio » | Proposé |
| [0127](adr/0127-modele-projet-android-et-code-genere-mutualise.md) | Modèle de projet Android et code généré (mutualisé Java) | Proposé |
| [0128](adr/0128-compose-et-coroutines.md) | Compose et coroutines : décidabilité par paliers | Proposé |
| [0129](adr/0129-interop-java-kotlin-bidirectionnelle.md) | Interopérabilité Java↔Kotlin bidirectionnelle | Proposé |
| [0130](adr/0130-gradle-kotlin-dsl-et-catalogues.md) | Gradle Kotlin DSL et catalogue de versions | Proposé |
| [0131](adr/0131-actions-et-refactorings-kotlin.md) | Actions et refactorings Kotlin | Proposé |
| [0132](adr/0132-usages-et-navigation-a-l-echelle.md) | Usages et navigation à l'échelle | Proposé |
| [0133](adr/0133-budget-dex-taille-et-packaging.md) | Budget dex, taille et packaging (gel du socle + packs) | Proposé |
| [0134](adr/0134-performance-concurrence-memoire-art.md) | Performance, concurrence et mémoire sur ART | Proposé |
| [0135](adr/0135-qualite-mesuree-niveau-android-studio.md) | Qualité mesurée « niveau Android Studio » (P1–P9, seuils, baselines) | Proposé |
| [0136](adr/0136-surface-protocole-et-bascule-codeide.md) | Surface de protocole, capacités absentes et bascule AVANT extension | Proposé |

Les 8 ADR impactées par l'étude post-clôture portent des **amendements
datés** (« Amendement 2026-10-31 ») activés uniquement si le propriétaire
accepte les ADR de renvoi — l'histoire n'est pas réécrite.

## Cahier de spécifications (le contrat)

[`cahier-specifications-kotlin/`](cahier-specifications-kotlin/) — **53
fiches, 469 comportements** (extraction clean-room `codeide-lsp` @
`bc11253`, lecture seule) ; fiches dans
[`cahier-specifications-kotlin/fiches/`](cahier-specifications-kotlin/fiches/).

## Études

| Étude | Contenu |
|---|---|
| [`etudes/moteur-kotlin/`](etudes/moteur-kotlin/) | synthèse et décision (00) · inventaire de l'existant (00b) · architectures K1–K12 (01) · spike S0–S3 (02) · **journaux d'exécution des étages 0–6 et transverse T** (03–10) · décisions propriétaire post-clôture (11) · ALERTES · DEMANDES-INTERFACE |
| [`etudes/niveau-android-studio-kotlin/`](etudes/niveau-android-studio-kotlin/) | audit du moteur (matrice capacité × moteur, KH1–KH16) · référence Android Studio · matrice d'écarts GK-001…GK-038 · plan par vagues V-A…V-I · fiches de comportement · protocole de captures · gabarit de mesure appareil · résultats de captures (lot E) |

## Documents d'exploitation

| Document | Rôle |
|---|---|
| [`../USAGE.md`](../USAGE.md) | surface LSP exacte (matrice), cycle de vie, intégration hôte, bascule, problèmes courants |
| [`../ARCHITECTURE.md`](../ARCHITECTURE.md) | composants, flux, contraintes ART, budgets mesurés |
| [`../CHANGELOG.md`](../CHANGELOG.md) | étages 0–6+T → 1.0.0 → 1.13.0 unifiée |
| [`../AGENTS.md`](../AGENTS.md) | règles propres au LSP Kotlin |
| [`PROVENANCE-REPRISE-KOTLIN.md`](PROVENANCE-REPRISE-KOTLIN.md) | manifeste de provenance (zones de reprise R1–R8, vendoring, corpus) |

## Bancs et baselines

[`../bancs/`](../bancs/) — baselines signées (qualité complétion, digeste
de parité), corpus de mesure
([`lsp-kotlin/bancs/corpus-android-kotlin/LISEZMOI.md`](../bancs/corpus-android-kotlin/LISEZMOI.md)) ;
scripts dans [`../scripts/`](../scripts/) ; module de mesure
[`../spike/`](../spike/) (tâches sans `-PspikeKotlin` désactivées).

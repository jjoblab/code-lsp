# ARCHITECTURE — moteur Kotlin LSP (`lspkotlin`)

> Composants, flux de données et décisions structurantes du moteur Kotlin
> (voie C — ADR 0100). Chaque décision renvoie à son ADR
> ([index complet](docs/INDEX.md)) ; les chiffres portent leur source
> (journaux d'étages, CI). La surface LSP exacte est dans
> [`USAGE.md`](USAGE.md).

## 1. Composants

| Composant | Rôle | Décision / preuve |
|---|---|---|
| Grammaire vendorée | grammaire officielle KMP **ré-obtenue de l'amont** (tag v2.3.0, notices Apache-2.0) — jamais depuis l'ancien dépôt ; dérive gardée par la suite de parité (digeste structurel, fiches 010/012) | R9.2 · VENDOR.md |
| Façade `Kt*` | API de parsing du module (fiche 013 — 13/13 ; domaine parse 27/27) | étage 1, vague 1a |
| Session documents | états de documents **à époque copy-on-write**, SPI en zone `api/` (`ServiceMoteurLangageKotlin`, fiche 042) | étage 1, vague 1b |
| Scan projet paresseux | passe **bornée et annulable** sur fil dédié, amorce chaude, incrémental mtime (fiche 041 — 15/15) | étage 1, vague 1c |
| Classpath & métadonnées | lecture `@Metadata` (`kotlin-metadata-jvm` 2.2.10, Apache-2.0, épinglée), reprises zones `classpath/` ; partage de l'index CIDX + index dédié `kotlin-appelables/*.kidx` | ADR 0108 · reprises R4/R5 |
| Modèle de symboles conservateur | **signatures certaines seulement** — politique « preuve ou silence » (zéro faux positif) | ADR 0103/0105/0107 |
| Moteurs L2 | survol riche (fiche classpath, fiche 018), inlay hints, aide à la signature (fiche 051) | étage 6, vagues 6b–6d |
| Complétion & classement | chaîne de complétion avec banc de qualité à **baseline signée** (étage 3) | ADR 0113/0119 |
| Descripteur de pack | drapeau pack, version réelle du moteur, chargement `ChargeurMoteurKotlin` | fiche 001 · ADR 0133 |
| Serveur LSP4J | `ServeurLangageKotlin` + `CycleVie`, `EtatsDocuments`, `CapacitesServeur`, `ConvertisseurLsp`, `PublicateurDiagnostics` (code NEUF, miroir N8 Java — le modèle, jamais le code) | ADR 0118 · journal étage 6 |

Les reprises contrôlées (zones `synthetics/`, `preview/`, `classpath/`,
`format/`, `plis/`, `documentation/`, `symboles/` — reprises R1–R8,
~2 600 lignes) sont inscrites au manifeste
[`docs/PROVENANCE-REPRISE-KOTLIN.md`](docs/PROVENANCE-REPRISE-KOTLIN.md) ;
le noyau (parse/résolution/session/diagnostics/complétion, serveur) est du
code NEUF clean-room.

## 2. Flux de données (une requête de survol)

1. `didChange` → la session documents publie un instantané immuable,
   l'époque s'incrémente (copy-on-write).
2. La requête passe par le recalcul frais (`calculerFraiche`, rejeu borné
   à 3 relances) — 0 réponse périmée par construction.
3. Le scan paresseux (fil dédié, incrémental mtime) garantit l'image du
   projet ; la façade `Kt*` sert l'arbre de la grammaire vendorée.
4. Le modèle conservateur ne répond que ce qui est **certain** (preuve ou
   silence) ; le moteur L2 compose la fiche classpath / le KDoc.
5. Le convertisseur UTF-16 traduit les offsets en positions LSP ; la
   réponse part sur le câble lsp4j de l'hôte.

## 3. Contraintes Android / ART (budgets sous garde CI)

| Contrainte | Valeur mesurée / règle | Source |
|---|---|---|
| method_ids pile chaude | **26 590** (plafond 32 768 — 81 %) ; câble lsp4j NON compté (porté par l'hôte ; avec le câble entier : 32 643) | CI scan ART · journal étage 6 §9.3 |
| classes.dex pile chaude | **4 294 352 o (4,10 Mo)** (budget 6 Mo — 68 % ; T1 élevé par le propriétaire le 31/10, E-K8) | idem |
| Gel du socle | ≤ 26 590 méthodes / 4,10 Mo + packs optionnels à chargement à la demande ; levée du plafond soumise à mesure appareil | ADR 0133 |
| Scan ART | **6 × AUCUNE** référence sensible (moteur 349 classes, syntax-api 309, lang-syntax 3, annotations 61) | journal étage 6 §9.3 |
| Mémoire | caches **bornés en octets** ; `onTrimMemory` par SPI hôte (Q-K8) ; GC ART au gabarit 07 | ADR 0134 |
| Tas au repos | **3 506 Kio** (banc empreinte, corpus 500) | journal étage 6 §9.3 |
| Facteur ART | ≈ 4× observé côté Java — **aucune extrapolation** JVM sans appareil | ADR 0134/0136 |

## 4. Mesures de référence (avec sources)

| Grandeur | Valeur | Source |
|---|---|---|
| Parse / re-parse | p50 1,8 ms (corpus 500 l.) / 8,6 ms (3 000 l.) | spike S0 |
| Productions | 5 448 (corpus 500) / 31 368 (corpus 3 000), 0 marqueur d'erreur | journal étage 6 §9.3 |
| Banc qualité complétion | p95 **6,28 ms** (sous la baseline signée 7,53 ms), 0 saut, 16/16 positions gagnantes au rang 0 | journal étage 6 §9.3 |
| Qualité « niveau AS » (baseline consignée, jamais réécrite) | FP P1 **28,05 / 1 000 lignes** mesuré sur corpus réel ; seuils arbitrés ADR 0135 (FP/1 000 < 0,5 visé, top-1 ≥ 0,80 sur 50+ positions split, P6 ≥ 0,99, rappel mutants ≥ 0,95) | ADR 0135 · étude niveau-AS |
| Jar du moteur | 991 023 o (1.13.0) / 990 859 o (clôture étage 6) / 892 147 o (étage 5) | build 03/11/2026 · journal étage 6 |
| Suite du module | 522 tests à la clôture étage 6 ; cahier complet 469/469 + 12 cas dorés | journaux 09/10 |

## 5. Décisions structurantes (lecture rapide)

ADR [0100](docs/adr/0100-voie-gouvernance-et-regles-de-reprise-kotlin.md)
voie C ·
[0103](docs/adr/0103-strategie-semantique-kotlin.md) voie (i) parseur
officiel + modèle conservateur ·
[0104](docs/adr/0104-syntaxe-grammaire-officielle-et-incrementalite.md)
grammaire officielle et incrémentalité ·
[0107](docs/adr/0107-typage-et-inference-bornes.md) preuve ou silence ·
[0108](docs/adr/0108-classpath-metadonnees-et-index.md) classpath et index ·
[0118](docs/adr/0118-integration-lsp-et-remplacement-progressif.md)
intégration LSP/SPI ·
[0122](docs/adr/0122-architecture-semantique-a-paliers.md) architecture à
paliers 1/2/3 ·
[0133](docs/adr/0133-budget-dex-taille-et-packaging.md) budget dex et
packs · [0134](docs/adr/0134-performance-concurrence-memoire-art.md)
performance/mémoire ART ·
[0135](docs/adr/0135-qualite-mesuree-niveau-android-studio.md) qualité
mesurée ·
[0136](docs/adr/0136-surface-protocole-et-bascule-codeide.md) bascule
AVANT extension — index complet : [`docs/INDEX.md`](docs/INDEX.md).

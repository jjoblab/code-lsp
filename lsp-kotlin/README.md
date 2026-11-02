# lsp-kotlin — serveur de langage Kotlin pour CodeIDE (`lspkotlin`)

> Moteur Kotlin LSP de CodeIDE, construit par la **voie C hybride**
> (ADR 0100, acceptée le 30/10/2026) : noyau réécrit **clean-room depuis le
> cahier de spécifications**, modules différenciants repris après contrôle
> (manifeste), grammaire officielle **ré-obtenue de l'amont** (vendoring).
> Stratégie sémantique : **voie (i)** — parseur officiel + modèle de
> symboles conservateur (ADR 0103), la profondeur K2 restant une extension
> différée.

## État et version (mesurés)

| Grandeur | Valeur | Source |
|---|---|---|
| Statut | **LIVRÉ, NON BRANCHÉ** — programme étages 0–6+T clos sur preuve mécanique le 31/10/2026 ; aucune bascule CodeIDE (ADR 0136 : la bascule I0–I6 passe AVANT l'extension des capacités — KH16 : sans bascule, toute capacité est invisible) | journaux [`lsp-kotlin/docs/etudes/moteur-kotlin/`](docs/etudes/moteur-kotlin/) · ADR 0136 |
| Cahier | **469/469 comportements** + 12 cas dorés (53 fiches) | journal 10 §8.4 (CI verte 10/10, run 41165869078) |
| Tests du module | **522 tests** à la clôture de l'étage 6 (477 + fiches 001/015/018/051 + 7 dorés protocole) | journal étage 6 §9.3 |
| Version | tête **1.13.0** (unifiée par la fusion du 02/11/2026 — `version.properties`) ; le pack langue n'est **pas publié** (Q-K4 ; bloqueurs P-K1/P-K2 levés le 31/10) | `mesures` · [`lsp-kotlin/docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md`](docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md) |
| Jar du moteur | `kotlin-1.13.0.jar` **991 023 o** (tête ; 990 859 o à la clôture étage 6 ; 892 147 o étage 5) | build local 03/11/2026 · journal étage 6 §9.3 |
| Budget dex (pile chaude) | **26 590 method_ids / classes.dex 4 294 352 o (4,10 Mo)** — 81 % du plafond 32 768, 68 % du budget 6 Mo ; câble lsp4j NON compté (porté par l'hôte) | CI run 41196064353 · journal étage 6 §9.3 |
| Qualité complétion | p95 **6,28 ms** (sous la baseline signée 7,53 ms), 0 saut de liste | journal étage 6 §9.3 |

## Périmètre (annoncé / absent)

La **matrice paritaire** est verrouillée par un cas doré (les annoncées une
à une ET les absentes une à une — règle : capacité testée = annoncée,
sinon absente). Détail complet : [`USAGE.md`](USAGE.md).

**Annoncées et servies (19)** : sync plein texte, UTF-16, survol,
complétion `.`/`(`, aide à la signature `(`/`,`, definition, typeDefinition,
implementation, references, documentHighlight, documentSymbol,
workspaceSymbol, selectionRange, foldingRange, formatting, rangeFormatting,
codeAction quickfix, inlayHint, rename + prepareRename.

**Non annoncées (honnêtes, 7)** : semanticTokens (dette D3 documentée),
codeLens, callHierarchy, typeHierarchy, documentColor, onTypeFormatting,
linkedEditingRange — leur ajout relève d'une extension dédiée, **après la
bascule** (ADR 0136). L'extension maison `x-codeide.completionRanking`
n'est **pas** annoncée (pas de frontière RPC de classement côté Kotlin —
écart consigné, journal étage 6 §7.1).

## Architecture en 10 lignes

1. **Grammaire officielle vendorée** (ré-obtention amont, tag v2.3.0 —
   jamais depuis l'ancien dépôt) avec parité gardée par digeste structurel.
2. **Façade `Kt*`** (fiche 013) : l'API de parsing du module.
3. **Session documents** à époque copy-on-write (« 0 réponse périmée » —
   SPI `api/`).
4. **Scan projet paresseux** : passe bornée, annulable, fil dédié, amorce
   chaude, incrémental mtime (fiche 041).
5. **Classpath et métadonnées** : lecture `@Metadata` (kotlin-metadata-jvm,
   Apache-2.0), partage de l'index CIDX + index dédié
   `kotlin-appelables/*.kidx` (ADR 0108).
6. **Modèle conservateur** : signatures certaines seulement — la politique
   « preuve ou silence » (ADR 0107 : zéro faux positif).
7. **Moteurs L2** : survol riche, inlay hints, aide à la signature (fiches
   015/018/051).
8. **Complétion et classement** avec banc de qualité à baseline signée.
9. **Descripteur de pack** (fiche 001) : drapeau pack, version réelle du
   moteur, budget dex sous seuils CI.
10. **Serveur LSP4J** (miroir N8 Java) : gardes aux codes exacts, matrice
    paritaire, publications débouncées — détail :
    [`ARCHITECTURE.md`](ARCHITECTURE.md).

## Démarrage, tests et bancs

```bash
./gradlew :kotlin:test :spike-kotlin:test   # suite du module (cahier complet)
./gradlew :kotlin:bench                     # banc empreinte/latence (nombres)
./gradlew :kotlin:benchQualite              # banc qualité complétion (baseline signée)
./gradlew :kotlin:jar                       # jar du moteur (scan ART + dexage d8 en CI)
```

Chiffres de référence mesurés (journal étage 6 §9.3) : parse p50 1,8 ms /
500 lignes, re-parse p50 8,6 ms / 3 000 lignes, productions 5 448 (corpus
500) / 31 368 (corpus 3 000), tas au repos **3 506 Kio** ; banc qualité
p95 6,28 ms (baseline signée 7,53 ms).

## Documentation du LSP Kotlin

| Document | Rôle |
|---|---|
| [`USAGE.md`](USAGE.md) | surface LSP exacte (matrice annoncées/absentes), cycle de vie et codes d'erreur, intégration hôte, bascule, problèmes courants |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | composants, flux de données, décisions structurantes, contraintes ART, budgets |
| [`CHANGELOG.md`](CHANGELOG.md) | versions du moteur (étages 0–6+T → 1.0.0 → 1.13.0 unifiée) |
| [`AGENTS.md`](AGENTS.md) | règles propres au LSP Kotlin (voie C, zones interdites R9.1, pièges) — renvoie au [`AGENTS.md`](../AGENTS.md) racine |
| [`docs/INDEX.md`](docs/INDEX.md) | index des ADR (0100–0136), du cahier, des études et journaux d'étages |
| [`lsp-kotlin/docs/PROVENANCE-REPRISE-KOTLIN.md`](docs/PROVENANCE-REPRISE-KOTLIN.md) | manifeste de provenance (zones de reprise R1–R8, vendoring, corpus) |
| [`lsp-kotlin/docs/etudes/moteur-kotlin/DEMANDES-INTERFACE.md`](docs/etudes/moteur-kotlin/DEMANDES-INTERFACE.md) | demandes d'interface Q-K1…Q-K9 (le propriétaire arbitre) |

Bancs et baselines : [`lsp-kotlin/bancs/`](bancs/) (baselines signées, corpus —
`corpus-android-kotlin/LISEZMOI.md`) ; module de mesure : [`lsp-kotlin/spike/`](spike/)
(tâches désactivées sans `-PspikeKotlin`).

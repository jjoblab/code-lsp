# FEUILLE DE ROUTE — Vagues du chantier « niveau Android Studio Kotlin »

> Document VIVANT de pilotage des vagues V-A…V-I du plan
> [`04-plan-vagues.md`](04-plan-vagues.md) — mis à jour à CHAQUE étape
> terminée (engagement de la mission du 12/11/2026 : « toujours la feuille
> de route pour chaque étape terminée »). Chaque vague livrée pointe vers
> son [journal d'exécution](execution/) (miroir des journaux d'étages) ;
> la CI reste l'arbitre (une tête n'est poussée que suite verte).
>
> **Point d'entrée du 12/11/2026** : la bascule (ADR 0136 étape A) et la
> publication du pack (Q-K4) sont FAITES (`v1.13.0`, 03/11/2026) — les
> vagues ci-dessous sont l'ÉTAPE B de l'ADR 0136 (capacités et fiabilité).

## État des vagues

| Vague | Intitulé | État | Journal | Preuves |
|---|---|---|---|---|
| V0 | Mesure (banc P1–P9) | **LIVRÉE** (mission du 31/10) | — | baseline FP 28,05/1000 l. consignée |
| V-A | Fiabilité & bascule | **LIVRÉE** (12/11/2026) | [execution/V-A-fiabilite.md](execution/V-A-fiabilite.md) | P1 strict **FP=0** (0,000/1000 l.), 542/542 tests, bancs verts |
| V-I1 | Capacités arbre (ADR 0136 B.2) | **LIVRÉE** (12/11/2026) | [execution/V-I1-capacites-arbre.md](execution/V-I1-capacites-arbre.md) | matrice **22 annoncées / 4 absentes**, 550/550 tests |
| V-B | Typage palier 2 | **LIVRÉE** (12/11/2026) | [execution/V-B-typage-palier2.md](execution/V-B-typage-palier2.md) | substitution des types (it: élément), P1 strict FP=0, 556/556, SPECIFICATION-TYPES v1 |
| V-C | Diagnostics élargis (gated) | **LIVRÉE** (12/11/2026) | [execution/V-C-diagnostics-elargis.md](execution/V-C-diagnostics-elargis.md) | whenNonExhaustif + suspendHorsContexte + @Suppress servi, mutants 4/4+3/3, P1 strict FP=0, 564/564 |
| V-D | Complétion smart/chain/DSL | **LIVRÉE** (12/11/2026) | [execution/V-D-completion-smart.md](execution/V-D-completion-smart.md) | peseur typeAttendu (KC-01) + chaînes extensions ≤2 (KC-02) + citées déclassées (KC-04 W1) + contexte OVERRIDE (KC-05), 12 tests, 577/577 — banc 50+ positions (E-K11) REPORTÉ au journal |
| V-I2 | Vague palier 2 protocole (ADR 0136 B.3) | **LIVRÉE** (12/11/2026) | [execution/V-I2-palier2-protocole.md](execution/V-I2-palier2-protocole.md) | semanticTokens borné KH1 + typeHierarchy + callHierarchy + codeLens, matrice 26/0, 586/586 |
| V-E | Android réel | **LIVRÉE** (12/11/2026 — dépendance levée : socle Java déjà livré + autorisation propriétaire) | [execution/V-E-android-reel.md](execution/V-E-android-reel.md) | V-E2 scan dédié build/generated + coupure + estNomGenere borné par preuve ; V-E3 @Composable/suspend au survol + Parcelize injecté + annotationsFqcn ; V-E1 namespace du build PRIME le manifest — 7 tests, 608/608 — corpus nowinandroid REPORTÉ au journal |
| V-F | Actions & refactorings | **LIVRÉE** (12/11/2026) | [execution/V-F-actions-refactorings.md](execution/V-F-actions-refactorings.md) | source.organizeImports (KC-10) + refactor.extract variable (KC-11 Q5) + kinds exacts + codeAction/resolve, cycle P5 ×2, 593/593 — autres refactorings REPORTÉS au journal |
| V-G | Gradle KTS & catalogue | **LIVRÉE** (12/11/2026) | [execution/V-G-gradle-kts-catalogue.md](execution/V-G-gradle-kts-catalogue.md) | TOML tolérant + accesseurs libs.… par lecture disque (G4) + navigation kts→toml (G5) + kt.cleCatalogueInconnue (G6), 8 tests — G1/G2/G3 REPORTÉS au journal |
| V-H | Palier profond | **GERME LIVRÉ + SÉRIE APPAREIL 3 PASSES PRODUITE (médiane) — verdict final SOUS ARBITRAGE propriétaire** (12/11/2026) | [execution/V-H-palier-profond.md](execution/V-H-palier-profond.md) | ModeProfondK2 derrière drapeau (613/613) ; **série moto g06 (17:07/17:11/17:25) : H1 médiane 19,0 s (×1,9), H2 médiane 19,0 s (×9,5), H2 ≈ H1 sur 4 exécutions, Q-K7 ACQUIS (com.acside)** — rejet motivé de l'option 2 sans démon CONFIRMÉ PAR LA SÉRIE (amorçage re-payé à chaque requête) ; l'option 3 (démon) reste la seule voie crédible — instruction au propriétaire ; au passage : baseline appareil du chemin de frappe ACQUISE SUR MÉDIANE (F1-F4 p95 ≤ 9 ms, S1 249 ms) + F5 géant médiane ×4,7 (recalibrage proposé) |
| V-I | Protocole, sémantique & usages à l'échelle | **LIVRÉE par étapes** (V-I1 + V-I2, 12/11/2026) | [execution/V-I1-capacites-arbre.md](execution/V-I1-capacites-arbre.md) + [execution/V-I2-palier2-protocole.md](execution/V-I2-palier2-protocole.md) | arbre + palier 2 : matrice 26/0, 586/586 ; l'index d'usages à l'échelle (0132) reste CONDITIONNÉ aux mesures |

## Ordre d'exécution suivi

```
V0 mesure (LIVRÉE, 31/10)
 └─ V-A fiabilité (LIVRÉE, 12/11) ── V-I1 capacités arbre ── V-B typage palier 2
        │                                                        │
        │                                                        ├─ V-C diagnostics élargis (gated)
        │                                                        └─ V-D complétion smart/chain
        │
        └─ V-E Android réel ── V-G Gradle KTS
                 │
                 └─ V-F actions & refactorings ── V-I2 protocole palier 2
                                              └─ V-H palier profond (SI arbitré)
```

L'ordre suit le plan §1 (V-A rend le moteur vrai et visible, V-B débloque
V-C/V-D, V-E suit le modèle partagé) AMENDÉ par l'ADR 0136 acceptée : les
CAPACITÉS D'ARBRE (linkedEditingRange, documentColor, onTypeFormatting)
précèdent le palier 2 (séquencement « bascule d'abord, capacités ensuite »
— étape B.2 avant B.3).

## Règles de la mission (consignées)

- **Commit + push après CHAQUE vague terminée** (décision propriétaire du
  12/11/2026) ; le tag `v*` ne se pose QUE lorsque TOUTES les vagues sont
  closes (il déclenche la publication Maven du pack).
- La vérification `scripts/verifier-push.sh` (distant + identité +
  utilisateur GitHub) précède CHAQUE push.
- Les re-signatures de baselines (digeste de parité) se font par étape
  dédiée avec la preuve d'équivalence consignée au journal de la vague
  (piège P-1, écart E-K9).
- La CI (10 jobs) reste l'arbitre : toute casse corrigée par commit
  `fix(...)` minimal expliqué.
- Les critères de sortie MESURABLES du plan §2 s'imposent à chaque vague ;
- ce document est la **feuille de route** tenue à jour à chaque étape.

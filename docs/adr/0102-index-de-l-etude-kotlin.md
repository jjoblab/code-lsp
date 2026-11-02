# ADR 0102 — Index de l'étude « moteur Kotlin » : ordre de lecture et dépendances

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

La mission Kotlin produit quatre rapports d'étude, un cahier, un
manifeste et 22 ADR (0100–0121) : le présent ADR fixe l'ordre de lecture
et le graphe de dépendances (miroir de l'ADR 0003 Java, adapté à la
plage Kotlin).

## Sources consultées

- Prompt de mission §7/§8 (numérotation, format) ; ADR 0003 Java
  (convention d'ordonnancement par dépendance).

## Ordre de lecture et graphe

```
0100 Voie de gouvernance A/B/C/D + règles de reprise ──── VERROU : décision PROPRIÉTAIRE avant toute copie
  └─> 0101 Périmètre & frontières (SPI, pack langue, :java/:index/:model)
        └─> 0102 (présent ADR, index)
              └─> 0103 Stratégie sémantique (i)–(iv) ── spike §5 + étude K1–K12
                    ├─> 0104 Syntaxe (grammaire, incrémentalité, scripts) ── K1 + spike S2
                    ├─> 0105 Modèle de symboles ── K2
                    ├─> 0106 Résolution de noms/portées/imports ── K3
                    ├─> 0107 Typage & inférence bornés (« preuve ou silence ») ── K4 + spike S1
                    ├─> 0108 Classpath/métadonnées, partage CIDX ── K5
                    ├─> 0109 Synthétiques Android & généré ── K6
                    ├─> 0110 Interop Java ↔ Kotlin ── K7
                    ├─> 0111 Gradle Kotlin DSL ── K8
                    ├─> 0112 Diagnostics (fidélité, mode profond différé) ── K9 + spike S1
                    ├─> 0113 Complétion & classement (banc commun Java) ── K10 + ADR 0012 Java
                    ├─> 0114 Navigation/rename/usages/hiérarchies ── K10
                    ├─> 0115 Formatage/imports/pliage/actions de code ── K9/K10
                    ├─> 0116 Compose (previews) ── K11
                    ├─> 0117 Concurrence/annulation/sessions/mémoire ART ── K12
                    ├─> 0118 Intégration LSP/SPI & remplacement progressif ── K10 + ADR 0015 Java
                    └─> 0119 Banc de qualité & non-régression ── ADR 0016 Java
  └─> 0120 Licences & provenance ── manifeste Kotlin + étude §0 + ALERTES P-K1/P-K2
  └─> 0121 Suivi des versions Kotlin & grammaire ── K1 + spike §5.1
```

Documents de base : `docs/etudes/moteur-kotlin/00b-inventaire-existant.md`
(Phase 0) · `01-architectures-analyse-kotlin.md` (Phase 1, K1–K12) ·
`02-spike-mesures.md` (Phase 2, S0–S3) · `00-synthese.md` (Phase 4) ·
`ALERTES.md` · `DEMANDES-INTERFACE.md` · manifeste
`docs/PROVENANCE-REPRISE-KOTLIN.md` · cahier
`docs/cahier-specifications-kotlin/` (53 fiches, 469 comportements).

## Conséquences

- Toute ADR cite ses sources vérifiées (R6.3) et renvoie aux mesures
  (R5) ; les ADR restent « Proposées » jusqu'à décision du propriétaire
  (R7).
- Les dépendances d'arbitrage inter-modules vivent dans
  `DEMANDES-INTERFACE.md` (Q-K1…Q-K6), jamais dans les ADR de domaine.

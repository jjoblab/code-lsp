# AGENTS.md — règles propres au LSP Kotlin

> Règles **propres** au moteur Kotlin (`lsp-kotlin/`). Les règles communes
> (langue, KDoc, provenance, push, multi-agents) sont dans le
> [`AGENTS.md`](../AGENTS.md) racine — elles s'appliquent intégralement.

## La voie C — le régime du moteur Kotlin (ADR 0100)

- **Noyau clean-room** (R9.1) : la lecture des zones NOYAU anciennes de
  production de `codeide-lsp` (`parse/`, `resolve/`, `diagnostics/`,
  `completion/`, `session/`, `api/`) est **interdite**. Les chantiers neufs
  s'écrivent cahier en main. Le code du module `:java` est en **lecture
  autorisée** pour le miroir architectural (le modèle, jamais le code
  copié — ADR 0118).
- **Zones de reprise** (voie C, inscription PRÉALABLE fichier par fichier) :
  `synthetics/`, `preview/`, `classpath/`, `format/`, `plis/`,
  `documentation/`, `symboles/` — reprises R1–R8, toutes livrées ;
  manifeste [`docs/PROVENANCE-REPRISE-KOTLIN.md`](docs/PROVENANCE-REPRISE-KOTLIN.md).
- **Vendoring** (R9.2) : la grammaire se **ré-obtient de l'amont**
  (jamais depuis l'ancien dépôt) — notices obligatoires, dérive gardée par
  le digeste structurel. Ne JAMAIS modifier le contenu de
  `src/main/vendor/**`.
- Les tests sources anciens restent consultables (R9.1/R9.5) pour les
  FIXTURES, jamais copiés.

## Lectures préalables (LSP Kotlin)

1. [`README.md`](README.md) — état (livré, non branché), matrice, budgets.
2. ADR [`0100`](docs/adr/0100-voie-gouvernance-et-regles-de-reprise-kotlin.md)
   (voie C) et [`0103`](docs/adr/0103-strategie-semantique-kotlin.md)
   (voie (i) — parseur officiel + modèle conservateur).
3. Le cahier de spécifications Kotlin
   ([`docs/cahier-specifications-kotlin/`](docs/cahier-specifications-kotlin/) —
   53 fiches, 469 comportements) : c'est le contrat.
4. Les journaux d'étages
   ([`docs/etudes/moteur-kotlin/`](docs/etudes/moteur-kotlin/) — 03 à 10 :
   étages 0 à 6 et transverse T) — la méthode et les preuves de clôture.
5. [`USAGE.md`](USAGE.md) — la surface LSP exacte (matrice paritaire),
   référence avant toute modification de comportement visible du client.

## Procédure de vérification (avant tout push du domaine Kotlin)

```bash
./gradlew :kotlin:test                # suite du module (cahier complet)
./gradlew :kotlin:bench :kotlin:benchQualite   # bancs (baseline signée)
scripts/verifier-provenance.sh        # vendoring + zones de reprise : aucun fichier hors registre
```

- La CI exécute en plus le **scan ART + dexage d8 sous seuils** (étage 0) :
  method_ids ≤ 32 768, classes.dex ≤ 6 Mo — le gel du socle (ADR 0133) est
  une **garde**, toute levée passe par mesure appareil.
- Les **baselines signées** (banc qualité, digeste de parité) ne se
  re-signent que par étape dédiée avec preuve d'équivalence et accord du
  propriétaire (piège P-1 ; écart de procédure E-K9 consigné).
- Commits : préfixe **`lsp-kotlin(scope):`**, en français.

## Pièges et alertes connus (consignés — les lire avant d'y retomber)

- [`docs/etudes/moteur-kotlin/ALERTES.md`](docs/etudes/moteur-kotlin/ALERTES.md) :
  E-K9 (re-signature digeste — procédure), E-K10 (zéro faux positif infirmé
  à l'échelle : 980/34 934 l.), E-K11 (banc complétion saturé), E-K12
  (exclusion fiche 010), E-K13 (kit `.kt.txt`) — et la classe de panne
  **rate-limit Maven Central 429** (déjà rencontrée côté Java et Kotlin :
  relancer, jamais de reprise de code).
- [`docs/etudes/moteur-kotlin/DEMANDES-INTERFACE.md`](docs/etudes/moteur-kotlin/DEMANDES-INTERFACE.md) :
  demandes d'interface Q-K1…Q-K9 — le propriétaire arbitre (R3) ; les
  arbitrages rendus sont consignés.
- « Preuve ou silence » (ADR 0107) : un comportement non prouvé n'est pas
  rendu — jamais de faux positif pour « aider » le client.
- Le jar du moteur **n'embarque pas** lsp4j (porté par l'hôte) : toute
  dépendance nouvelle du serveur doit être arbitrée en budget dex (ADR
  0133).

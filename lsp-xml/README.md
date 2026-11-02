# lsp-xml — emplacement réservé du futur LSP XML

> **Statut : RÉSERVÉ — NON MIGRÉ, aucun code, aucun module Gradle déclaré.**
> Créé par la réorganisation Phase 2 (docs/REORGANISATION.md §2, décision
> validée par le propriétaire le 03/11/2026) pour fixer la place du futur
> LSP XML dans l'arborescence cible : chaque LSP vit dans sa propre section.
>
> **Source actuelle** : le LSP XML en service vit dans l'ancien dépôt
> `codeide-lsp` (aucun code ni lecture de code de l'ancien dépôt dans la
> mission de réorganisation — cf. interdits §9 du prompt).
> **Périmètre attendu** : `[À COMPLÉTER PAR LE PROPRIÉTAIRE]`.

Ce répertoire accueillera, le moment venu, la même structure que ses aînés :

```
lsp-xml/
├─ README.md · USAGE.md · ARCHITECTURE.md · CHANGELOG.md · AGENTS.md
├─ moteur/     # module Gradle :xml (réservation commentée dans
│              # settings.gradle.kts — décommenter à l'arrivée du code)
├─ docs/       # adr/ · cahier-specifications-xml/ · études/
├─ bancs/      # baselines, corpus
├─ scripts/
└─ spike/
```

Le plan d'accueil détaillé (pré-requis, standards d'entrée, étapes) vit dans
[docs/PLAN-MIGRATION.md](docs/PLAN-MIGRATION.md).

Les **standards d'entrée** s'appliqueront au LSP XML comme à ses aînés
(détail dans [docs/PLAN-MIGRATION.md](docs/PLAN-MIGRATION.md) §2) :
cahier de spécifications extrait avant toute réécriture · ADR fondatrices ·
banc de qualité à **baseline signée** · **scan ART / dexage d8 sous seuils** ·
registre de **provenance** dédié · serveur **LSP4J** + **matrice de
capacités** verrouillée par cas dorés · la même documentation standard
(`README/USAGE/ARCHITECTURE/CHANGELOG/AGENTS` + `docs/INDEX.md`) · gabarit
de passe bascule appareil commun (`docs/GABARIT-BASCULE-APPAREIL.md`).

# lsp-xml — emplacement réservé du futur LSP XML

> **Statut : RÉSERVÉ — aucun code, aucun module Gradle déclaré.**
> Créé par la réorganisation Phase 2 (docs/REORGANISATION.md §2, décision
> validée par le propriétaire le 03/11/2026) pour fixer la place du futur
> LSP XML dans l'arborescence cible : chaque LSP vit dans sa propre section.

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

Les standards d'entrée (§7.3 du prompt de mission) s'appliqueront au LSP XML
comme aux autres : gabarit de passe bascule appareil commun
(`docs/GABARIT-BASCULE-APPAREIL.md`), registre de provenance dédié,
cahier de spécifications extrait avant toute réécriture.

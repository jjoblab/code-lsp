# ADR 0117 — Concurrence, annulation, sessions, mémoire et GC sur ART

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K12 : « Comment éviter un verrou global et garder le tas plat ? »
L'ancien moteur : sessions étanches (états indépendants — fiche cahier),
verrou lecture/écriture de l'index (fiche 022, ADR 0211), observateurs
neutres sans coroutines (ADR 0213), index disque par empreinte (tas
plat), scan paresseux borné annulable. Le défaut de structure
documenté côté Java vendored (verrou global synchronized) n'a PAS été
mesuré côté Kotlin — [non vérifié] : le comptage synchronized de
l'artefact 1.22.1 reste à produire par outil.

## Sources consultées

- Cahier : fiches 022 (A211 — verrou lecture/écriture sans
  interblocage), 043 (`ScanProjetTest` — accès concurrents, annulation,
  reprise), 009 (pilote d'indexation).
- `code-lsp` : ADR 0010 Java (verrous par périmètre — schéma B + ordre
  global documenté), ADR 0011 Java (mémoire : soft-réfs, budgets
  octets, invalidation par dépendances, shim `onTrimMemory`).
- syntax-api : `CancellationProvider` + `ThreadLocalRegistryHolderJvm`
  [mesuré, listing du jar] — annulation au lexing disponible.

## Options

**Option 1 — Schéma de concurrence par périmètre transposé**
(schéma B de l'ADR 0010 Java : verrou par document/session/index,
ordre global documenté, époque copy-on-write en option pour le protocole
riche). **Option 2 — Verrou moteur global** (simplicité apparente,
risque d'interblocage et de jank UI — le défaut constaté côté Java
vendored) — écarté. **Option 3 — Coroutines dans le moteur** :
contredit la condition pack téléchargeable (ADR 0213 ancienne — le
SPI reste en types neutres) — écarté. **Option 4 — Ne rien changer.**

## Critères et mesures

- Fiches 022/043/009 : les comportements de concurrence éprouvés
  (états indépendants par session, requêtes parallèles sans
  interblocage, annulation coopérative publiant l'acquis, reprise).
- Cible « tas plat » : aucune structure de classes résidente (index
  disque + cache LRU borné — fiche 008).

## Décision proposée

**Option 1** : transposition directe du schéma Java — verrous par
périmètre (document → session → index, ordre global documenté, tests de
chaos en CI), annulation par jeton (le `CancellationProvider` de
syntax-api sert le lexing), budgets octets explicites partout (cache
segments), `onTrimMemory` branché au niveau pack. Observateurs neutres
(type ADR 0213) pour l'état d'indexation — le module reste sans
coroutines. Le protocole riche (réponses non périmées) adoptera
l'époque copy-on-write du design Java à l'étage intégration LSP
(ADR 0118).

## Conséquences

- Comportement GC ART : soft-références à mesurer sur appareil
  (miroir AL Java) ; repli bornes dures en octets.
- Le comptage synchronized du moteur neuf devient une métrique CI
  (seuil documenté — miroir « < 6 » Java).

## Plan de migration progressive

Le schéma entre en naissance (voie A) ou à l'étage consolidation
(voie B/C) ; les tests de chaos avec l'étage concurrence.

## Tests de non-régression et critères de sortie

- Fiches 022/043/009 vertes ; zéro interblocage aux tests de chaos ;
  comptage synchronized sous seuil ; tas par palier de jars sous
  budget.

## Risques

- R1 — GC ART incompatibilité soft-réfs : mitigation = bornes dures +
  mesure appareil (gabarit).
- R2 — Ordre global mal respecté dans un greffon futur : mitigation =
  revue de concurrence obligatoire sur tout ajout de verrou (règle
  ADR 0010 Java).

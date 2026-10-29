# ADR 0106 — Résolution de noms, portées, récepteurs implicites, imports

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K3 du prompt : « Quelles règles de la spécification faut-il couvrir
exactement ? » La résolution est le cœur mesuré du moteur
(19 f / 7 966 l. chez l'ancien) et le siège de son historique de
corrections : 24 classes `Reproduction*` [mesuré] — la couverture actuelle
a été construite correctif par correctif, pas dérivée de la
spécification.

## Sources consultées

- Spécification Kotlin (chapitres imports, scopes, receiver
  disambiguation — canons publics).
- `JetBrains/kotlin` @ `e3806c8f` : `compiler/fir/resolve` (151 f),
  design doc `content-and-resolution-scope-architecture.md` (scopes de
  contenu/résolution — complexité amont documentée).
- Cahier : fiches de résolution (K3/K4/K5, héritage, vagues 4/5,
  scopes, `with`, destructuration) — l'inventaire exact des règles
  éprouvées.
- `codeide-lsp` : ADR 0203/0205/0216/0221/0222/0223 (règles de preuve).

## Options

**Option 1 — Dérivation règle par règle depuis la spécification**
(périmètre EXPLICITE, chaque règle citée avec sa référence de spec,
chaque lacune déclarée sous-rapport). **Option 2 — Reprise du
machin ancien et extension** (voie B) : la structure correctif par
correctif migre. **Option 3 — Résolution K2** : disqualifiée sur le
chemin d'édition (ADR 0103). **Option 4 — Ne rien changer.**

## Critères et mesures

- Le cahier fixe le périmètre éprouvé : hiérarchie de portées
  (locals → membres → englobantes → paquet → imports exact/joker/alias
  → défauts `kotlin.*`/`java.lang.*`), récepteurs implicites
  (`with`/`apply` emboîtés, scopes de lambda Compose `RowScope`,
  pile des this dans objets anonymes), membres hérités (fermeture
  mixte projet/classpath par clauses écrites + port de fichiers),
  `expect`/`actual` (expect d'abord), compagnons (anonymes/nommés,
  classpath), typealias (carte dédiée), alias JVM↔Kotlin, scripts
  (nus muets). Bornes de preuve : profondeur 16/budget 96 sondages,
  garde anti-cycle, segment unique faillible des types qualifiés.

## Décision proposée

**Option 1** : la résolution neuve est **spécifiée avant implémentée**
— un tableau règles × références de spécification × statut
(couvert/sous-rapport/hors périmètre) vit dans le module (document de
spécification interne, versionné) ; l'implémentation le remplit
fiche par fiche ; le banc de fidélité (ADR 0119) mesure l'écart à K2
en oracle. La sélection d'appelé reste **par preuve** (nom + arité,
types complets par récepteur typé — ADR 0223-like) : jamais de
résolution devinée.

## Conséquences

- La « dette de spécification » devient visible et mesurable (le
  tableau des règles) au lieu d'implicite.
- En voie C, la reprise de `resolve/` est limitée aux fonctions
  pures réutilisables (alias, chaînes de supertypes) — le reste est
  réécrit depuis le tableau.

## Plan de migration progressive

Étage résolution après parse/session (miroir N1 Java) : d'abord les
portées/import (le socle), puis membres hérités, puis récepteurs de
portée, chaque tranche validée par les fiches correspondantes.

## Tests de non-régression et critères de sortie

- 100 % des fiches de résolution du cahier vertes ; zéro faux positif
  corpus quatre mondes ; vrais négatifs toujours signalés (typos
  qualifiées).

## Risques

- R1 — Le tableau de règles devient une deuxième spécification
  divergente : mitigation = chaque ligne cite le canon (spec Kotlin) ;
  revue au banc.
- R2 — Règles de preuve coûteuses (fermetures mixtes) : mitigation =
  bornes mesurées de l'ancien moteur (profondeur/budget) reprises
  comme contrats.

# Fiche C-08 — Second appel de complétion : membres inaccessibles et statiques importables

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §A2
> (invocations successives documentées). Comportement décrit pour lui-même.
> **Domaine** : complétion
> **Résumé** : la **deuxième invocation** de complétion à la même position élargit
> la liste : membres **inaccessibles** (privés/protegés du hors-périmètre) et
> membres **statiques importables** ; la **troisième** élargit aux types du projet
  entier avec import automatique.

## Comportements

### D1 — second appel, membres inaccessibles
- **Préconditions / entrée** : `autre.|` où `autre` est un `Autre` d'un autre
  paquetage ; premier appel : membres publics ; **second appel** à la même position.
- **Comportement attendu** : les membres `private`/`protected` (hors périmètre)
  apparaissent, **marqués** (dégravés visuellement — détail de présentation client),
  derrière les accessibles.
- **Bornes** : l'insertion d'un membre inaccessible reste possible (code qui ne
  compile pas) mais est signalée par sa marque — jamais silencieuse.

### D2 — second appel, import statique
- **Entrée** : `asse|` en expression ; `Math.*` contient `asin` ; second appel.
- **Attendu** : propositions d'**imports statiques** (`import static java.lang.Math.asin`)
  avec `additionalTextEdits` portant l'import — insertion compilable (P6).

### D3 — troisième appel, projet entier
- **Entrée** : troisième invocation ; types non visibles du paquetage courant.
- **Attendu** : tous les types du **projet** (CIDX) proposés avec import
  automatique.

### D4 — compteur d'invocation
- **Entrée** : frappe entre deux invocations (préfixe changé).
- **Attendu** : le compteur retombe à 1 (changement d'ancre = nouveau contexte) ;
  le compteur est par (document, position d'ancre).

## Notes pour la réécriture
- Le compteur vit côté serveur (capability v2 pour que le client gère le geste
  double-appel — ADR 0026/0031).

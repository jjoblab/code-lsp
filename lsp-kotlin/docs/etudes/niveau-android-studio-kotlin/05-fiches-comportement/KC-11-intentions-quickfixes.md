# Fiche KC-11 — Intentions et quick-fixes Kotlin prioritaires

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) : intentions (Alt+Entrée) et quick-fixes. Étude en lecture
> seule, clean-room — catalogue **prioritaire borné**, décrit pour
> lui-même.
> **Domaine** : actions / quick-fixes / refactorings
> **Résumé** : catalogue prioritaire d'intentions sûres : disponibilité
> **contextuelle** (motif prouvé requis), **prévisualisation texte**, kinds
> LSP (`quickfix`/`source`/`refactor`). Aucune action ne repose sur une
> information non démontrée — motif non prouvable → action **absente**.

## Comportements

### Q1 — catalogue prioritaire

| Intention | Motif déclencheur (prouvé) | Kind LSP |
|---|---|---|
| expression-body ↔ block-body | fonction à corps de bloc mono-`return` / corps expression | `refactor.rewrite` |
| type de retour explicite | corps expression dont le type est prouvé palier 2 | `refactor.rewrite` |
| `if` ↔ `when` | chaîne `if/else` ↔ `when` sans garde | `refactor.rewrite` |
| `x?.let { }` | garde de nullité + appel membre | `refactor` |
| gabarit de chaîne | concaténation `"... " + expr` | `quickfix` ou `refactor` |
| supprimer `!!` | `!!` après smart cast prouvé non-null | `quickfix` |
| nommer l'argument | littéral en position, nom lu sur la signature | `quickfix` |
| extraire variable | expression intra-fonction sans capture ambiguë | `refactor.extract` |
| extraire fonction | bloc borné, paramètres = identifiants libres | `refactor.extract` |
| entourer `run`/`with` | expression/instruction sélectionnée | `refactor` |

### Q2 — disponibilité contextuelle
- **Préconditions / entrée** : curseur sur `fun f() = 1` (type `Int` prouvé
  palier 2).
- **Comportement attendu** : « ajouter le type de retour explicite »
  proposée ; sur `fun g() = calculer()` sans type prouvé → **absente**.

### Q3 — prévisualisation texte
- **Attendu** : chaque action porte un **titre** décrivant la
  transformation, avec un diff avant/après en **prévisualisation texte**
  côté client ; jamais d'aperçu exécuté.

### Q4 — kinds LSP
- **Attendu** : actions rattachées à un diagnostic → `quickfix` ;
  réécritures stylistiques → `source` (groupables via `source.fixAll`
  quand applicable) ; extractions et entourages → `refactor.*`. Le client
  filtre par kind demandé ; le moteur ne ment jamais sur le kind.

### Q5 — bornes des extractions
- **Attendu** : extraire **variable** : locale `val` intra-fonction ;
  extraire **fonction** : même fichier `.kt`/`.kts`, paramètres =
  identifiants libres du bloc, pas de gestion des retours multiples — hors
  bornes : action absente.
- **Statut** : `[À CAPTURER]` — libellés exacts et ordre du menu
  Alt+Entrée dans la référence.

## Notes pour la réécriture
- ADR 0131 (actions/refactorings — porteur), ADR 0136 (protocole — kinds
  codeAction), ADR 0125 (quickfixes liés aux diagnostics), ADR 0123 (preuves
  de typage).
- Renvoi cahier : fiche 016 (correctifs texte) à amender ; écarts
  GK-xxx (matrice, à venir).

# Fiche KC-09 — Nullabilité et smart casts (Kotlin)

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) : garde de nullabilité et smart casts (`if (x is T)`,
> garde à sortie précoce, `?:`, `!!`, `requireNotNull`). Étude en lecture
> seule, clean-room.
> **Domaine** : diagnostics / typage / complétion
> **Résumé** : l'accès membre sur `T?` sans garde est **rouge** ; après une
> garde prouvée, le type est smart-casté (membres du type cible en tête de
> complétion). État moteur : gardes à sortie précoce et `&&` couverts, casts
> positionnels, `||` annule ; **cibles d'extension** : `when` à sujet et
> elvis `?:`.

## Comportements

### N1 — garde de nullabilité
- **Préconditions / entrée** : `val nom: String? = lireNom()` ; accès
  membre `nom.length` **sans garde**.
- **Comportement attendu** : rouge `kt.unsafeNullable` (code existant du
  catalogue moteur), aligné sur la référence.
- **Bornes** : nullabilité **lue sur les déclarations** (marqueur `?`) — pas
  d'inférence de nullabilité au-delà ; récepteur non typé → silence.

### N2 — smart casts couverts (état moteur)
- **Entrée** : `if (x is Client) { x.| }` (branche THEN) ; garde
  `if (x !is Client) return` puis `x.|` ; opérande droit d'un `&&` ; entrée
  d'un `when` sans sujet.
- **Attendu** : membres du type casté en tête ; locale **ombrée** après la
  condition → le cast ne fuit pas.

### N3 — `!!` et `requireNotNull`
- **Entrée** : `nom!!.length` ; `val y = requireNotNull(nom)` puis `y.|`.
- **Attendu** : après `!!`, accès non-null sans rouge ; `requireNotNull`
  **cible** (repli : silence tant que non prouvé).

### N4 — cibles d'extension
- **Entrée** : `when (x) { is Client -> x.| }` (`when` **à sujet**) ;
  `x ?: return` puis `x.|` (elvis à sortie précoce).
- **Attendu** : smart cast appliqué — **cible**, pas jour 1.
- **Statut** : `[À CAPTURER]` — frontière exacte des casts acceptés par la
  référence (contrats, `checkNotNull`, opérande droit de `||`).

### N5 — bornes actuelles du moteur
- **Attendu** : casts **positionnels** (analyse linéaire du bloc) ;
  l'opérande droit d'un `||` **annule** le cast ; pas de flux
  inter-procédural.
- **Bornes** : pas de génériques/variance → silence sur les types
  paramétrés (typage borné palier 2).

### N6 — complétion après garde
- **Entrée** : après une garde prouvée, `x.|`.
- **Attendu** : membres du type casté (non-null) **en tête** ; sinon
  retombée sur KC-01/KC-03.

## Notes pour la réécriture
- ADR 0123 (typage palier 2 — nullabilité déclarée), ADR 0125 (catalogue —
  `kt.unsafeNullable` à conserver, cibles d'extension à arbitrer), ADR 0126
  (complétion post-garde).
- Renvoi cahier : fiche 046 (smart casts, `kt.unsafeNullable` déjà testés) ;
  écarts GK-xxx (matrice, à venir).

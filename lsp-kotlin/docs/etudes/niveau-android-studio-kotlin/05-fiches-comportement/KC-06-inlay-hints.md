# Fiche KC-06 — Inlay hints Kotlin

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) : indices en ligne (types implicites, noms de paramètres,
> récepteurs implicites, longueur des chaînes). Étude en lecture seule,
> clean-room.
> **Domaine** : inlay hints / typage
> **Résumé** : le moteur fournit des **hints factuels — jamais faux** : type
> implicite après `val x = …`, noms de paramètres aux appels et dans les
> lambdas, récepteur implicite (`this:`/`it:`) en tête de lambda, longueur
> des littéraux de chaîne. **Sans type prouvé, pas de hint** (règle
> d'effacement).

## Comportements

### H1 — types implicites
- **Préconditions / entrée** : `val compteur = 0` → hint `: Int` ;
  `val u = trouverUtilisateur()` → hint `: Utilisateur` si le type de retour
  est prouvé (palier 2 : type du projet ou FQCN de bibliothèque).
- **Comportement attendu** : le hint suit l'identifiant ; **effacé** dès que
  le type est écrit explicitement (`val x: Int = …` → rien).
- **Bornes** : type non démontrable (inférence générique, variance) → **pas
  de hint** — jamais de type deviné.

### H2 — noms de paramètres aux appels
- **Entrée** : `repeter(3, "x")` avec
  `fun repeter(fois: Int, etiquette: String)`.
- **Attendu** : hints `fois =` devant `3`, `etiquette =` devant `"x"` ;
  argument unique non ambigu → hint masqué (règle client).
- **Bornes** : noms lus sur les signatures projet/classpath ; signature non
  lue → pas de hint.

### H3 — noms dans les lambdas
- **Entrée** : `paires.map { (nom, age) -> nom }` (destructuration) ; lambda
  à paramètres explicites.
- **Attendu** : hints de noms sur les composants de la destructuration et
  les paramètres, selon les déclarations lues.

### H4 — récepteurs implicites en tête de lambda
- **Entrée** : `buildString { … }` → hint `this: StringBuilder` à l'ouverture
  du lambda ; `clients.map { … }` → hint `it: Client`.
- **Attendu** : récepteur/paramètre implicite annoté **une fois**, en tête ;
  effacé si le code écrit explicitement `this`/le paramètre.
- **Statut** : `[À CAPTURER]` — rendu exact (préfixe, position) des hints de
  récepteur dans la référence.

### H5 — longueur des chaînes
- **Entrée** : accès `.length` sur un littéral de chaîne.
- **Attendu** : hint numérique (longueur constante) sur l'accès.
- **Bornes** : littéral simple uniquement (pas de concaténation, pas de
  gabarit) ; sinon pas de hint.

### H6 — règles d'effacement
- **Attendu** : un hint n'est rendu que sur **preuve** (type, nom, longueur,
  récepteur) ; information déjà écrite dans le code → jamais dupliquée ;
  familles activables côté client via les paramètres LSP
  (`textDocument/inlayHint`).
- **Bornes** : la politique « preuve ou silence » s'applique aux hints —
  aucun hint décoratif sans preuve.

## Notes pour la réécriture
- ADR 0123 (typage palier 2 — source des hints de type), ADR 0126 (les hints
  partagent la lecture des signatures avec la complétion), ADR 0136
  (protocole — inlayHint, familles, résolution).
- Écarts GK-xxx (matrice, à venir) ; budget ART : calcul différé et annulable.

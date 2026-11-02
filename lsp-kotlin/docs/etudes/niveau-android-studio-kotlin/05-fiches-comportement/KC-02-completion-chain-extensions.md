# Fiche KC-02 — Complétion chain et extensions applicables (Kotlin)

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) : seconde complétion intelligente (chaînes d'expressions,
> fonctions d'extension applicables). Étude en lecture seule, clean-room.
> **Domaine** : complétion
> **Résumé** : après une expression de type connu `T`, l'invocation
> intelligente (Ctrl+Shift+Espace) propose les **fonctions d'extension de
> `T`** (projet + classpath) permettant d'**atteindre le type attendu**, y
> compris des chaînes courtes (profondeur 2 bornée). Plafond de propositions
> avec **total annoncé** — jamais de troncature silencieuse.

## Comportements

### E1 — extensions applicables
- **Préconditions / entrée** : `val fiche: Fiche` ; extension
  `fun Fiche.auteur(): Auteur` (projet ou classpath) ; position
  `val a: Auteur = fiche.|` avec type attendu `Auteur`.
- **Comportement attendu** : `auteur` proposé en tête (extension de `Fiche`
  retournant `Auteur`) ; les extensions retournant un autre type restent
  disponibles en complétion membre ordinaire, pas dans cette passe.
- **Bornes** : récepteur `T` et type cible prouvés palier 2 (type du projet
  ou FQCN) ; sans preuve → passe désactivée.

### E2 — chaînage profondeur 2
- **Entrée** : `fiche.|` avec type attendu `String` ; il existe
  `Fiche.auteur(): Auteur` et `Auteur.nomComplet(): String`.
- **Attendu** : la chaîne `fiche.auteur().nomComplet()` proposée **comme un
  seul item** (expression complète insérable d'un coup).
- **Bornes** : profondeur **bornée à 2** (jamais 3) ; chaque maillon doit être
  démontrable ; un maillon manquant → aucune chaîne proposée.

### E3 — plafond et total annoncé
- **Entrée** : récepteur très connecté (bibliothèque riche en extensions).
- **Attendu** : plafond de propositions appliqué ; le dépassement est
  **annoncé** (métadonnée de total ou item de reliquat « … et N autres ») —
  jamais de coupe muette.
- **Bornes** : plafond calibré pour ART (budget dex serré) ; valeur fixée au
  banc, documentée en ADR.

### E4 — honnêteté du repli
- **Entrée** : `inconnu.|` sans type démontrable, ou position sans type
  attendu.
- **Attendu** : **aucune** proposition de chaîne (pas de spéculation) ;
  comportement = complétion membre/récepteur ordinaire (KC-01, KC-03).
- **Statut** : `[À CAPTURER]` — rendu exact des chaînes dans la référence
  (nommage des variables intermédiaires, présentation de l'expression).

### E5 — tri final
- **Entrée** : plusieurs extensions candidates applicables.
- **Attendu** : candidats du **projet avant le classpath**, puis ordre
  alphabétique stable ; l'ordre doit être **reproductible** (gate de test).

## Notes pour la réécriture
- ADR 0126 (complétion — passe chain, exécution asynchrone possible), ADR
  0123 (typage palier 2 — les deux maillons de la chaîne), ADR 0122
  (architecture à paliers, budget ART, plafonds).
- Homologue du dépôt Java : fiche C-03 (même mécanisme, écarts distincts) —
  GK-xxx (matrice, à venir).
- Amendement cahier : étendre la fiche 043 au-delà du genre NOM pour la
  passe chain.

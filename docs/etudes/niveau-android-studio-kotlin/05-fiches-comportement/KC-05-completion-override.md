# Fiche KC-05 — Complétion `override` et génération de stubs (Kotlin)

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) : complétion des membres ouvrables et génération de
> signatures `override`. Étude en lecture seule, clean-room.
> **Domaine** : complétion / génération de code
> **Résumé** : après `override fun |` (ou `override val/var |`) dans le corps
> d'une classe, la complétion liste les **membres ouvrables** des supertypes
> (projet + classpath) ; la sélection insère la **signature complète** du
> membre, corps stub, avec **appel du super optionnel**. Membre non lisible →
> absent de la liste (jamais de signature devinée).

## Comportements

### O1 — membres ouvrables listés
- **Préconditions / entrée** : `class Panneau : Vue { override fun | }` où
  `Vue` déclare `fun mesurer()` (abstraite) et `open fun dessiner()`.
- **Comportement attendu** : `mesurer` et `dessiner` (ouvrables) proposés ;
  membres `final` et membres **déjà surchargés** absents ; les abstraits
  devant les `open`.
- **Bornes** : supertypes du projet ou lus au classpath (FQCN) ; interfaces
  Java lues en déclarations bornées (interop).

### O2 — signature complète générée
- **Entrée** : sélection de `mesurer(largeur: Int): Boolean`.
- **Attendu** : insertion de la signature complète `override fun
  mesurer(largeur: Int): Boolean { … }` avec corps stub ; curseur placé dans
  le corps ; imports requis ajoutés le cas échéant.
- **Statut** : `[À CAPTURER]` — corps par défaut (stub vide, `TODO()`, appel
  super) et réglage correspondant dans la référence.

### O3 — propriétés
- **Entrée** : `override val |` / `override var |`.
- **Attendu** : propriétés ouvrables listées (val/var, type) ; sélection =
  signature complète (`override val titre: String`), initialisateur selon le
  réglage du corps stub.

### O4 — appel du super optionnel
- **Entrée** : membre **non abstrait** du supertype (ex. `dessiner`).
- **Attendu** : le corps généré peut contenir l'appel `super.dessiner()`
  (option, réglage client) ; membre abstrait → **jamais** d'appel super.

### O5 — honnêteté des sources
- **Entrée** : membre du supertype **non lisible** (classpath non indexé,
  binaire muet, déclaration interop tronquée).
- **Attendu** : membre **absent** de la liste ; aucun stub généré ; aucun
  diagnostic corrélé (preuve ou silence).

## Notes pour la réécriture
- ADR 0126 (complétion — genre MEMBRE filtré aux ouvrables), ADR 0131
  (actions/refactorings — génération de stub), ADR 0129 (interop Java —
  déclarations bornées, arité non contrôlée), ADR 0123 (typage palier 2 des
  supertypes).
- Renvoi cahier : fiches 044 (héritage) et 052 (super) à amender ; écarts
  GK-xxx (matrice, à venir).

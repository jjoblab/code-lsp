# Fiche KC-01 — Complétion smart par type attendu (Kotlin)

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) : réordonnancement des propositions de complétion par type
> attendu. Étude en lecture seule, clean-room. Comportement décrit pour
> lui-même.
> **Domaine** : complétion
> **Résumé** : aux positions où un **type attendu est démontrable** (argument
> d'appel typé, `return` dans une fonction typée, initialisation/affectation
> typée, branche d'`if`/`when` expression, argument de lambda typé), les
> expressions du bon type — ou convertibles — **dominent le classement** ; les
> autres restent visibles mais descendent. Sans type attendu démontrable :
> **aucun réordonnancement** (jamais de tri mensonger).

## Comportements

### T1 — argument d'appel typé
- **Préconditions / entrée** : `fun saluer(qui: Client)` ; locaux
  `val ami: Client`, `val age: Int` ; curseur sur `saluer(|)`.
- **Comportement attendu** : `ami` (et toute expression de type `Client`)
  proposé **en tête** ; `age` reste présent, en bas de liste.
- **Bornes** : type attendu = type du projet ou FQCN de bibliothèque (typage
  borné : pas de génériques, nullabilité inférée, variance).

### T2 — `return` dans une fonction typée
- **Entrée** : `fun construire(): Ecran` ; corps `return |` avec locaux
  `val accueil: Ecran`, `val titre: String`.
- **Attendu** : `accueil` en tête ; `titre` en bas. Les expressions
  convertibles (littéral, appel retournant un sous-type démontrable)
  s'intercalent derrière les candidats exacts.
- **Statut** : `[À CAPTURER]` — place exacte des convertibles par rapport aux
  candidats exacts dans le classement de la référence.

### T3 — initialisation et affectation typées
- **Entrée** : `val principale: Ecran = |` puis `var active: Ecran = |` avec
  des locaux de types variés.
- **Attendu** : expressions de type `Ecran` (ou convertibles) dominent ;
  comportement identique pour `val` et `var`.

### T4 — branche d'`if`/`when` expression
- **Entrée** : `val message = if (erreur) | else "aucune"` — branche incomplète
  typée par les branches complètes ; idem pour une branche de `when`
  expression.
- **Attendu** : expressions compatibles en tête dans la branche incomplète.
- **Bornes** : type inféré **des branches complètes lisibles** ; si aucune
  branche ne fournit de type démontrable → pas de tri.

### T5 — argument de lambda typé
- **Entrée** : `fun convertir(action: (Client) -> Unit)` ; appel
  `convertir { | }` — corps du lambda typé par l'attente de la fonction.
- **Attendu** : membres du paramètre implicite `it` et expressions
  compatibles en tête (renvoi KC-03 pour le récepteur/`it`).

### T6 — règle d'honnêteté et marqueur de provenance
- **Entrée** : toute position T1–T5 dont le type attendu n'est **pas
  démontrable** (signature non lue, type générique, appelable non résolu).
- **Attendu** : liste **inchangée** (ordre du genre courant, filtrage par
  préfixe) — le filtrage par type n'**ordonne que**, il ne cache jamais. Quand
  le tri s'applique, chaque item trié porte un **marqueur de provenance
  palier 2** (type du projet vs FQCN de bibliothèque), rendu par le client.
- **Bornes** : le marqueur est factuel ; il ne masque aucun candidat.

## Notes pour la réécriture
- Type attendu demandé au **typage palier 2** (ADR 0123) ; classement et
  genres dans ADR 0126 ; marqueur de provenance spécifié en ADR 0122
  (architecture à paliers).
- Amendement cahier : la fiche 043 (complétion contextuelle) hérite du
  réordonnancement — décision propriétaire requise.
- Écarts relevés : GK-xxx (matrice, à venir) ; l'oracle K2 (compilateur en
  sous-processus) tranche le classement exact au banc.

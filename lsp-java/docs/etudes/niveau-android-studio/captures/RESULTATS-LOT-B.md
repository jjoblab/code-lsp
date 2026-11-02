# Résultats — Lot B (diagnostics et inspections)

> À remplir par le propriétaire. Images dans `images/Bx-*.png`. Support :
> [`kit/KitCapturesAs.java`](kit/KitCapturesAs.java) sauf B6/B7 (niveau projet).

## B1 — `a == b` sur String + Alt+Enter

- Message exact du quick-fix « equals » :
- Option « unsafe (…) » présente ? oui / non — libellé exact : ________
- Liste complète des actions proposées :
  1.
  2.
  3.
- Remarques :

## B2 — `@Nonnull` retournant un champ `@Nullable`

- Sévérité affichée : Warning / Weak Warning / autre : ________
- Message exact au survol :
- Quick-fix proposé (libellé) :
- Même résultat avec `androidx.annotation` au lieu de `org.jetbrains` ? oui / non / non testé

## B3 — `@SuppressWarnings("StringEquality")`

- L'annotation est-elle acceptée sans signal ? oui / non
- Si non : token alternatif proposé par AS : ________
- Remarques :

## B4 — NewApi (appel API 26+ sous minSdk 24)

- Sévérité : ________
- Message exact :
- Fixes proposés (liste complète, libellés exacts) :
  1.
  2.
  3.
- Variante sous garde `SDK_INT` : signalisation entièrement disparue ? oui / non — reste : ________
- Remarques :

## B5 — `@CallSuper` non appelé

- Message exact :
- Fix proposé (libellé) :
- Remarques :

## B6 — projet SANS build (copie fraîche, aucune compilation)

> Préparation : copie fraîche de `bancs/projet-android/` (avec le kit +
> strings.xml posés), ouverte dans AS, **ne PAS lancer de build** ; attendre
> la fin de l'indexation uniquement.

- `ActivityMainBinding` (ligne 10 de MainActivity.java) : zéro rouge ? oui / non
  — si rouge : message exact : ________
- Survol (hover) de `ActivityMainBinding` : contenu exact : ________
- `binding.` complétion dans onCreate : que propose-t-elle ? ________
- `R.string.app_name` : zéro rouge ? oui / non — hover : ________
- Après l'édition d'une valeur dans strings.xml : la complétion/hover
  reflète-t-elle la nouvelle valeur sans build ? oui / non / non testé
- Remarques :

## B7 — invalidation inter-fichiers (B6 ouverts tous les deux)

> Geste : ouvrir MainActivity.java et Greeter.java, supprimer la méthode
> `saluer` de Greeter.java, chronométrer le re-highlight de MainActivity.

- Délai de re-highlight de MainActivity (ms, estimation vidéo/frames) : ________
- Le rouge apparaît-il sur `liaison.saluer(...)` (ligne 20) ? oui / non
- Diagnostics de MainActivity périmés après re-highlight ? oui / non
- Remarques :

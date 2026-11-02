# Résultats — Lot D (points NV de l'étude)

> À remplir par le propriétaire. Images dans `images/Dx-*.png`.

## D1 — hover d'un champ `R.string.x`

- Contenu exact du hover :
  - valeur résolue affichée ? oui / non — texte : ________
  - fichier/ressource d'origine mentionné ? oui / non — sous quelle forme : ________
  - autres éléments (icône, déclaration, actions) : ________
- Remarques :

## D2 — champs standards de `BuildConfig`

> Après un build debug (puis release si possible) du projet de test, ouvrir
> le `BuildConfig.java` généré et copier la liste des champs.

- Champs générés (debug) :
  1.
  2.
  3.
  4.
  5.
- Différences en variante release (si testée) : ________
- Le fichier s'ouvre-t-il en lecture seule avec bannière « generated » ? ___

## D3 — distinction lecture/écriture dans find usages

- Symbole testé (champ avec lectures et écritures) : ________
- Le dialogue/usages distingue-t-il lecture/écriture ? oui / non — comment : ________
- Options d'affichage relevées (filtres, onglets, regroupements) : ________

## D4 — `@Dimension` / `@Px`

> Support : bloc `d4Appel` du kit (`poserMarge(`) et appel `v.setPadding(0,0,0,8)`.

- Diagnostic sur un littéral nu passé à un paramètre `@Dimension` ? oui / non — message : ________
- Complétion proposée à `poserMarge(` : conversion (8dp / 8sp…) ? oui / non — détail : ________
- Remarques :

## D5 — profil d'inspections par défaut

> Settings > Editor > Inspections > export (même partiel).

- Fichier exporté joint ? oui (chemin : ________) / non — raison : ________
- Nombre total de contrôles activés (si affiché) : ________
- Remarques (profils Android Studio spécifiques visibles ?) :

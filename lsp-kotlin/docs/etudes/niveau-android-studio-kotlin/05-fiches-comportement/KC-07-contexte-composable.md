# Fiche KC-07 — Contexte `@Composable` (Compose)

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Android / plugin Kotlin mode K2) autour de Jetpack Compose : détection du
> contexte composable, complétion, `Modifier`, `remember`. Étude en lecture
> seule, clean-room.
> **Domaine** : Compose / diagnostics / complétion
> **Résumé** : appeler un composable hors contexte composable est **rouge**
> (comportement référence) ; dans un corps composable, la complétion propose
> composables et membres ; `Modifier` et `remember` suivent les mêmes règles.
> Décidabilité **cadencée par paliers** : palier 1 = présence de l'import
> `androidx.compose.runtime.Composable` prouvée ; palier 2+ = statut
> composable des appelés.

## Comportements

### C1 — composable hors contexte = rouge (référence)
- **Préconditions / entrée** : `@Composable fun Titre()` ; appel `Titre()`
  depuis une fonction **non** composable — les deux déclarations lisibles.
- **Comportement attendu** : erreur dans la référence ; dans le moteur :
  **cible**, émise seulement si (a) l'annotation `@Composable` de l'appelé
  est prouvée (déclaration projet ou classpath lu), (b) l'absence de
  contexte composable à l'appel est prouvée.
- **Bornes** : appelant non analysable ou appelé non prouvé → silence
  (preuve ou silence) ; code `kt.*` à arbitrer au catalogue.

### C2 — complétion dans un corps composable
- **Entrée** : `@Composable fun Ecran() { | }`.
- **Attendu** : composables du projet et du classpath (FQCN compose) proposés
  et **marqués**, en tête des candidats ; le reste de la portée sans
  changement (genres NOM/MEMBRE inchangés).
- **Bornes** : liste bornée aux déclarations lues ; symboles non prouvés
  composables non marqués (pas de marquage mensonger).

### C3 — `Modifier` en paramètre
- **Entrée** : `fun Carte(modifieur: Modifier = Modifier)` ; position
  `Carte(Modifier.|)`.
- **Attendu** : extensions de `Modifier` (remplissage, taille, …) issues du
  classpath lu, chaînables (renvoi KC-02).

### C4 — `remember`
- **Entrée** : `val x = remember { | }` dans un corps composable.
- **Attendu** : corps du lambda complété comme un lambda typé (renvoi
  KC-03) ; `mutableStateOf` proposé dans un corps composable.

### C5 — décidabilité par palier
- **Attendu** : **palier 1** (syntaxe + disque) : présence de l'import
  `androidx.compose.runtime.Composable` prouvée → annotation reconnue,
  contexte composable des fonctions annotées détecté, complétion C2
  partielle ; **palier 2+** : statut composable des appelés (déclarations
  projet/classpath), rouges C1, marquage complet.
- **Statut** : `[À CAPTURER]` — message exact du rouge et traitement des
  valeurs par défaut de paramètres composables dans la référence.

## Notes pour la réécriture
- ADR 0128 (Compose/coroutines — porteur), ADR 0127 (modèle projet Android /
  code généré), ADR 0125 (catalogue diagnostics), ADR 0123 (typage palier 2).
- Renvoi cahier : fiche 025 (ReproductionComposeTest) à aligner ; écarts
  GK-xxx (matrice, à venir).

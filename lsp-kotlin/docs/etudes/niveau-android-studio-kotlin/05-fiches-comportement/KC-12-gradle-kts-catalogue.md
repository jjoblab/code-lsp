# Fiche KC-12 — Gradle KTS et catalogue de versions

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Android / support Gradle) dans `build.gradle.kts` et
> `gradle/libs.versions.toml`. Étude en lecture seule, clean-room.
> **Domaine** : Gradle KTS / complétion / navigation / diagnostics
> **Résumé** : dans `build.gradle.kts`, la complétion des **plugins**,
> **dépendances** et clés de configuration AGP fonctionne **par lecture
> disque, sans sync** ; les accesseurs `libs.…` proviennent de la **lecture
> TOML réelle** (pas des accesseurs générés par Gradle) ; navigation vers
> l'entrée du catalogue ; diagnostics bornés à la clé inconnue. **Source de
> vérité = le disque** ; ce que la sync ajoute (variantes…) est hors
> périmètre.

## Comportements

### G1 — plugins
- **Préconditions / entrée** : `build.gradle.kts` du projet ;
  `plugins { | }`.
- **Comportement attendu** : ids de plugins **lisibles sur le disque**
  (déjà déclarés dans le projet, catalogue, `pluginManagement`) proposés ;
  aucun appel réseau, aucune résolution.
- **Bornes** : plugin absent du disque → absent de la liste (pas de
  suggestion inventée).

### G2 — dépendances
- **Entrée** : `dependencies { | }` puis `implementation(|)`.
- **Attendu** : configurations du bloc (`implementation`, `api`, …) par
  lecture du DSL (renvoi KC-03) ; coordonnées du catalogue + artefacts déjà
  connus du disque (dépendances du projet, cache Gradle lisible).
- **Bornes** : pas de résolution réseau ; genre NOM borné.

### G3 — configuration AGP
- **Entrée** : `android { | }`.
- **Attendu** : clés lisibles dans les **déclarations AGP trouvées sur
  disque** (cache Gradle, jar indexé en déclarations bornées) ; sinon repli
  honnête (liste vide ou partielle, jamais inventée).

### G4 — accesseurs `libs.versions.toml`
- **Entrée** : catalogue `gradle/libs.versions.toml` (sections
  `[versions]`, `[libraries]`, `[bundles]`, `[plugins]`) ; position `libs.|`
  dans le kts.
- **Attendu** : accès **par lecture TOML directe** (parseur dédié), pas via
  les accesseurs générés par Gradle ; imbrication par groupe
  (`libs.androidx.…`) avec normalisation tirets/points ; **alias exacts** du
  fichier.
- **Bornes** : catalogue absent ou invalide → `libs.` non complété
  (silence).

### G5 — navigation vers l'entrée du catalogue
- **Entrée** : `definition` demandée sur `libs.androidx.core` dans le kts.
- **Attendu** : saut à la **ligne de l'entrée** correspondante dans
  `libs.versions.toml` ; pour un `version.ref`, saut à l'entrée
  `[versions]`.
- **Bornes** : navigation inter-fichiers (kts → toml) — extension à
  documenter au protocole.

### G6 — diagnostics bornés
- **Entrée** : `libs.pasDansLeCatalogue` — catalogue parsé, clé absente
  (preuve d'absence).
- **Attendu** : diagnostic borné « clé inconnue du catalogue » ; **aucun**
  diagnostic de version ou de conflit (indémontrables sans sync).
- **Statut** : `[À CAPTURER]` — complétion exacte offerte par la référence
  **avant** toute sync (sous-ensemble réel).

### G7 — périmètre
- **Attendu** : la **sync** ajoute les variantes, les types générés et le
  contrôle de configuration → **hors périmètre**, documenté comme écart
  explicite.

## Notes pour la réécriture
- ADR 0130 (Gradle KTS — porteur), ADR 0132 (usages/navigation — saut au
  TOML), ADR 0127 (modèle projet Android / code généré), ADR 0125
  (diagnostics bornés).
- Renvoi cahier : fiche 027 (ReproductionGradleKtsTest) à aligner ; écarts
  GK-xxx (matrice, à venir).

# Fiche 047 — CompilationEnvironmentRouteurTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/engine/CompilationEnvironmentRouteurTest.java`.
> **Domaine** : caches-perf
> **Résumé** : Tests du ROUTAGE fichier → module (ADR 0215, découverte D6 du rapport d'audit §14) : le serveur LSP Java doit analyser un fichier avec le moteur de SON module, pas avec le contexte défaut — sans quoi un projet Android de deux fichiers du même paquet montrait « Greeter cannot be resolved to a type » (l'orchestrateur nourrissait bien les moteurs par module, mais TOUS les services consultaient le contexte défaut). Éprouve : le comportement historique sans routeur, l'association par routeur, sa relecture à CHAQUE requête (instantané courant du modèle, sans re-pose), le repli sur le défaut quand le routeur jette, l'isolation des moteurs par module, la garde paresseuse de construction, l'ordre des moteurs vivants (défaut d'abord) et l'invalidation de contexte limitée au seul module remplacé.

## Comportements

### sansRouteurComportementHistorique — tout au défaut
- **Préconditions / entrée** : environnement sans routeur posé ; `jdtPourFichier("/quelconque/App.java")`.
- **Comportement attendu** : le moteur rendu est celui du contexte DÉFAUT (même instance que `jdt()`).
- **Bornes** : —
- **Statut** : actif

### routeurAssocieLeMoteurDuModule — routage nominal
- **Préconditions / entrée** : routeur associant les chemins finissant par `App.java` au module `:app`.
- **Comportement attendu** : `jdtPourFichier("/projet/src/App.java")` rend LE moteur de `:app` (même instance que `jdt(":app")`, distincte du défaut) ; un chemin non associé (routeur rendant null) retombe sur le contexte défaut.
- **Bornes** : —
- **Statut** : actif

### routeurVuAChaqueRequeteSansRePose — instantané vivant
- **Préconditions / entrée** : routeur lisant un état MUABLE (module courant, initialement null) ; l'état passe à `:app` entre deux requêtes, SANS re-pose du routeur.
- **Comportement attendu** : la première requête route vers le défaut ; après le changement d'état, la MÊME requête route vers `:app` — le routage suit l'instantané COURANT du modèle (miroir du dépôt de modèle : sync suivante = nouveau module routé).
- **Bornes** : —
- **Statut** : actif

### routeurQuiJetteRepliSurDefaut — routeur fautif
- **Préconditions / entrée** : routeur jetant `IllegalStateException("dépôt illisible")`.
- **Comportement attendu** : `jdtPourFichier` ne propage JAMAIS l'exception — repli sur le contexte défaut.
- **Bornes** : —
- **Statut** : actif

### fichiersDeModulesDistinctsMoteursDistincts — isolation par module
- **Préconditions / entrée** : routeur `chemin.contains("/lib/") ? :lib : :app` ; deux fichiers, l'un de :app, l'autre de :lib.
- **Comportement attendu** : les deux fichiers obtiennent des moteurs DISTINCTS (isolation lot E), chacun étant l'instance canonique de son module.
- **Bornes** : —
- **Statut** : actif

### gardeParesseuseParModuleSansConstruction — construction paresseuse
- **Préconditions / entrée** : routeur tout-`:app` ; garde `isEcjInitialisePour(chemin)`.
- **Comportement attendu** : avant toute requête, le moteur du module n'existe pas (garde fausse) ; après UN `jdtPourFichier`, la garde devient vraie pour le chemin ET pour `isEcjInitialized(":app")` (même garde que la variante qualifiée) ; le contexte DÉFAUT reste NON construit (jamais sollicité).
- **Bornes** : —
- **Statut** : actif

### moteursVivantsDefautPuisModules — inventaire ordonné
- **Préconditions / entrée** : routeur `Lib.java → :lib` ; construction du défaut PUIS d'un fichier :lib.
- **Comportement attendu** : avant tout, `moteursVivants()` est vide ; après : exactement 2 moteurs vivants, le DÉFAUT en PREMIER position, puis les modules.
- **Bornes** : 2 moteurs vivants ; défaut en index 0.
- **Statut** : actif

### remplacementDeContexteInvalideLeMoteurDuSeulModule — invalidation ciblée via le routage
- **Préconditions / entrée** : contextes `:app` et `:lib` posés (racines existantes) ; moteurs obtenus par routage. Entrée : remplacement EFFECTIF du contexte de `:app` (autre racine).
- **Comportement attendu** : le moteur de `:app` est reconstruit (instance différente au prochain routage) ; le moteur de `:lib` VIT (même instance — jamais invalidé par la sync de `:app`).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Le routeur est une fonction chemin → module (ou null → défaut), posée UNE fois et relue à CHAQUE requête : aucun cache de décision ne doit figer un modèle devenu obsolète.
- Robustesse impérative : un routeur qui jette ne casse JAMAIS une requête (repli défaut) — même exigence que pour un dépôt de modèle illisible.
- La garde paresseuse existe en deux variantes équivalentes : par chemin (`isEcjInitialisePour`) et par module (`isEcjInitialized(module)`) — le défaut n'est construit que s'il est sollicité.
- Les racines sources des contextes doivent EXISTER sur disque (garde isDirectory côté remplacerContexteModule) : les fixtures créent des répertoires réels.
- `moteursVivants()` est un inventaire ordonné (défaut d'abord) utilisable pour des shutdown/invalidations globales.

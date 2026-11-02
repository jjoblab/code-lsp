# Fiche C-06 — Navigation `R.string.x` ↔ `strings.xml`

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §B11
> (goto declaration sur champs R, find usages XML→code, rename de ressource).
> Comportement décrit pour lui-même.
> **Domaine** : navigation / intégration Android
> **Résumé** : la navigation bidirectionnelle entre le code Java et les déclarations
> de ressources : `R.string.x` → élément `<string name="x">` ; usages XML
> (`@string/x`) ↔ usages Java ; manifest ↔ classes déclarées.

## Comportements

### G1 — definition code → XML
- **Préconditions / entrée** : `res/values/strings.xml` :
  `<string name="app_name">CodeIDE</string>` ; caret sur `app_name` dans
  `R.string.app_name` ; requête definition.
- **Comportement attendu** : **une** cible : `strings.xml`, plage de l'élément
  `<string>` (ou de son attribut `name` — cohérent, à fixer en cas doré).
- **Bornes** : ressource multi-qualifiers (`values-fr/strings.xml`) → toutes les
  déclarations sont des cibles (une par fichier).

### G2 — usages XML → code et code → XML
- **Entrée** : `@string/app_name` dans un layout ; `getString(R.string.app_name)`
  dans du Java ; requête references sur la déclaration XML ou sur le champ R.
- **Attendu** : les **deux** familles d'occurrences sont rendues (index de mots
  étendu aux XML) — groupées par fichier.

### G3 — rename de ressource
- **Entrée** : rename de `app_name` → `nom_app`.
- **Attendu** : occurrences réécrites dans **toutes** les déclarations
  (multi-qualifiers) et tous les usages (Java + XML) ; collision détectée avant
  écriture si `nom_app` existe déjà ; aperçu = résultat.

### G4 — manifest ↔ classe
- **Entrée** : `android:name=".MainActivity"` dans `AndroidManifest.xml`.
- **Attendu** : navigation vers `MainActivity.java` (et inversement : symboles du
  manifest dans l'espace de travail).

### G5 — hover de ressource
- **Entrée** : survol de `R.string.app_name`.
- **Attendu** : signature (`public static final int`) + **valeur résolue** du
  référentiel (ex. `« CodeIDE »`), + fichier de déclaration. [Captures AS demandées
  pour la mise en forme exacte — protocole 06.]

## Notes pour la réécriture
- Dépend de l'index de mots étendu aux XML (ADR 0020) et du référentiel ; les
  collisions du rename réutilisent le moteur N5.

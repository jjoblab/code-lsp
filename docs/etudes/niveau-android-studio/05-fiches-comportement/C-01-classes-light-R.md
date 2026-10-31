# Fiche C-01 — Classe `R` light : résolution et complétion avant build

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §B8/§B9
> (document du plugin Android sur les classes light ; règles AGP 8 vérifiées dans la
> documentation officielle). Comportement décrit pour lui-même (clean-room).
> **Domaine** : intégration Android / résolution / complétion
> **Résumé** : sur un module Android sans build préalable, les références à
> `<namespace>.R.<type>.<nom>` résolvent, se complètent et se survolent à partir du
> **référentiel de ressources** dérivé du disque — pas d'erreur « cannot be
> resolved », navigation vers la déclaration XML. La classe `R` synthétique n'expose
> que l'API (champs `public static final int`, non-final si `android.nonFinalResIds`),
> jamais de corps.

## Comportements

### R1 — résolution avant build
- **Préconditions / entrée** : module Android (namespace `com.ex`) avec
  `res/values/strings.xml` contenant `<string name="app_name">…</string>` ; source
  `int n = com.ex.R.string.app_name;` ; **aucun build exécuté**.
- **Comportement attendu** : zéro diagnostic d'erreur sur la référence ; le survol
  du champ affiche `public static final int app_name` + provenance ressource.
- **Bornes** : le type de l'expression est `int`.
- **Statut** : candidat (P3 corpus doit le vérifier : aujourd'hui FP garanti).

### R2 — R non transitif (AGP 8)
- **Entrée** : deux modules `app` (namespace `com.app`) et `lib` (namespace
  `com.lib`) ; `lib` définit `R.string.lib_label` ; app dépend de lib ;
  `android.nonTransitiveRClass=true` (défaut AGP 8).
- **Attendu** : `com.app.R.string.lib_label` est une **erreur** (ressource du
  module autre) ; `com.lib.R.string.lib_label` résout (via le `R.txt`/référentiel
  de la dépendance).
- **Bornes** : avec `nonTransitiveRClass=false`, `com.app.R.string.lib_label`
  résout.

### R3 — complétion `R.string.<caret>` et `R.<caret>`
- **Entrée** : `R.string.a|` → toutes les chaînes du module (+ dépendances si R
  transitif), triées par le classement D8 ; `R.|` → les types internes
  (`string`, `drawable`, `id`, `layout`, `color`, …) qui ont au moins un élément.
- **Attendu** : aucune suggestion de type vide ; les ressources **privées** des
  dépendances (préfixées `androidx.` internes) ne sont pas proposées pour le module
  courant.

### R4 — anti-doublon avec le build
- **Entrée** : build exécuté → `build/generated/…/R.java` existe sur un srcDir
  généré du modèle.
- **Attendu** : la source générée **prime** ; le synthétique est masqué (aucun
  doublon en complétion, aucune divergence de valeur).

### R5 — ids et styleables
- **Entrée** : layout `res/layout/main.xml` avec `android:id="@+id/titre"` ;
  `res/values/attrs.xml` avec `<declare-styleable name="Bar"><attr name="b1"
  format="color"/></declare-styleable>`.
- **Attendu** : `R.id.titre` résout ; `R.styleable.Bar` (tableau) et
  `R.styleable.Bar_b1` (index) résolvent ; `R.attr.b1` résout.

## Notes pour la réécriture
- Amendement cahier : la fiche 036 (popup spontané `R$*`) et la fiche 051
  (déclassement databinding) sont **remplacées** par la résolution réelle — décision
  D6 demandée.
- `R.txt` des AAR : format texte trivial `int|string styleable X_y N` — à lire si
  présent (priorité bytecode > référentiel, cf. ADR 0022 même règle).

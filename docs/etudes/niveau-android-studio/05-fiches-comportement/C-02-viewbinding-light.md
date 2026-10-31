# Fiche C-02 — ViewBinding light : nommage, champs, nullabilité par configuration

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §B9
> (règles vérifiées dans le code public du plugin Android + documentation officielle
> view-binding). Comportement décrit pour lui-même.
> **Domaine** : intégration Android / résolution / diagnostics
> **Résumé** : avec `buildFeatures { viewBinding true }`, les classes
> `<Module>.databinding.<NomLayout>Binding` existent pour le moteur **avant build**,
> avec un champ par `android:id` (nullabilité selon les configurations), `getRoot()`,
  `inflate(...)` et `bind(...)`.

## Comportements

### V1 — nommage et résolution
- **Préconditions / entrée** : `res/layout/result_profile.xml` (racine non `<layout>`),
  binding activé ; source `import com.ex.databinding.ResultProfileBinding;`.
- **Comportement attendu** : l'import résout ; zéro « cannot be resolved ».
- **Bornes** : `<layout class="AutreNom">` redéfinit le nom (DataBinding).

### V2 — champs par id, nullabilité par configuration
- **Entrée** : `main.xml` (port) et `main.xml` (layout-land) ; `@+id/titre` présent
  dans les deux ; `@+id/badge` présent **seulement en port** ; `@+id/zone` de types
  incohérents (TextView vs ImageView) entre configurations.
- **Attendu** : champ `titre` de type la vue commune, **non null** ;
  champ `badge` **`@Nullable`** (absent d'une configuration) ; champ `zone` de type
  `android.view.View` (incohérence) ; `tools:viewBindingType` force le type déclaré.
- **Bornes** : layout exclu par `tools:viewBindingIgnore="true"` → pas de classe.

### V3 — méthodes générées (API seule)
- **Attendu** : `inflate(LayoutInflater)`, `inflate(LayoutInflater, ViewGroup,
  boolean)`, racine `<merge>` : `inflate(LayoutInflater, ViewGroup)` (parent non
  null) ; `bind(View)` ; `getRoot()` du type de la racine ; tout `@NonNull`/`@Nullable`
  conformes à la référence.

### V4 — activation conditionnelle
- **Entrée** : module **sans** `viewBinding`/`dataBinding` activé (buildFeatures
  par défaut faux).
- **Attendu** : les classes `*Binding` **n'existent pas** pour ce module (une
  référence produit l'erreur honnête — pas de fausse résolution).

## Notes pour la réécriture
- L'activation se dérive de la lecture texte des build.gradle (ADR 0020) — la
  référence la tient du modèle sync ; comportement identique, source différente.
- Le banc `bancs/projet-android-genere/` devient l'oracle d'équivalence light ↔
  généré (cas dorés V1–V3).

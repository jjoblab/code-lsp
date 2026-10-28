# Fiche 036 — PopupSpontaneReproTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/PopupSpontaneReproTest.java`.
> **Domaine** : completion
> **Résumé** : Reproduction du retour propriétaire : popup de complétion spontané juste après une déclaration de variable / un retour à la ligne, avec des suggestions « R$style / R$id / R$string ». Banc de caractérisation : chaque test déclenche une complétion et IMPRIME les items (label, kind, detail, insertText, autoImportFqn) ainsi que la trace interne du moteur — **aucune assertion**. Fixture : un R.jar « réaliste » (aapt2-like) compilé au vol — `com.example.monip.R` + classes internes `R$style`, `R$id`, `R$string` — ajouté au classpath à côté d'android.jar (API 37.2 cherchée parmi des chemins candidats machine) ; moteur compliance « 17 ».

## Comportements

### popupApresDeclarationVariable — caret après une déclaration
- **Préconditions / entrée** : source `MainActivity.java` (paquet `com.example.monip`, import `android.os.Bundle`) avec `int x = 5;` dans un corps de méthode ; caret juste après l'espace qui suit le « ; » de la déclaration.
- **Comportement attendu** : la complétion rend des items ; le banc les dump (avec trace) sur stdout — reproduction/diagnostic du popup spontané, sans oracle.
- **Bornes** : —
- **Statut** : actif (hypothèse `@BeforeAll` : android.jar absent → classe ignorée)

### popupApresRetourLigne — caret sur ligne vide
- **Préconditions / entrée** : même source ; caret sur la ligne vide suivant la déclaration (après l'indentation).
- **Comportement attendu** : complétion rendue et dumpée (trace + items) — observation de la présence/absence des `R$*`.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

### popupApresEspaceDansDeclaration — caret dans une déclaration en cours
- **Préconditions / entrée** : source avec `String nom ` (déclaration incomplète) ; caret après « String nom » + espace.
- **Comportement attendu** : complétion rendue et dumpée (trace + items).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

### importApresPaquetProjet — contexte import sur le paquet du projet
- **Préconditions / entrée** : source avec `import com.example.monip.` ; caret juste après le point final.
- **Comportement attendu** : complétion rendue et dumpée — observation de la fuite éventuelle des classes `R$*` dans le contexte import du paquet projet.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

## Notes pour la réécriture
- Banc d'exploration SANS assertion : sa valeur est la reproduction du symptôme et le dump (label/kind/detail/insert/autoImport + trace interne) — à transformer en vraie caractérisation lors de la réécriture (assertions sur présence/absence des `R$*` par contexte).
- La variante à trace (`complete(uri, source, caret, trace)`) accepte un réceptacle de trace : surface utile pour le diagnostic côté réécriture.
- Le R.jar de fixture est construit par une méthode privée locale, dupliquée à l'identique dans `PopupSpontaneReproTestHelper` (fiche 037) — la version partagée est celle du helper.
- Classpath du moteur : `[android.jar, r.jar]` avec racine source `app/src/main/java/com/example/monip` — la position du caret (après « ; »+espace, sur ligne vide, dans déclaration incomplète) est l'entrée contractuelle.

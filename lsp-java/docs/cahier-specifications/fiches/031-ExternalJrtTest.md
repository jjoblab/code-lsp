# Fiche 031 — ExternalJrtTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/ExternalJrtTest.java`.
> **Domaine** : index-classpath
> **Résumé** : Tests du jrt EXTERNE : résolution des classes java.* depuis un JDK autre que le runtime courant (typiquement Java 21 système pendant que le build tourne sous Java 17), via son `lib/jrt-fs.jar` — le chemin réel sur Android/ART où le JDK Termux est externe à l'app. Le chargement passe par URLClassLoader + ServiceLoader du fournisseur jrt. Deux tests sont conditionnés par la présence du JDK externe à chemin fixe (`/usr/lib/jvm/java-21-openjdk-amd64`, détecté par `lib/modules` présent) ; le troisième vérifie le repli vers le jrt du runtime courant.

## Comportements

### resolvesJavaLangFromExternalJdkViaJrtFsJar — java.lang résout depuis le jrt externe
- **Préconditions / entrée** : JDK externe Java 21 présent sur la machine (`/usr/lib/jvm/java-21-openjdk-amd64` avec `lib/modules`) ; moteur construit avec racines vides, classpath vide, conformité « 17 », jdkHome = le JDK externe.
- **Comportement attendu** : `isLikelyUsable()` vrai (le jrt externe rend le moteur utilisable) ; le diagnose d'une classe utilisant `java.lang.String` s'exécute sans erreur et AUCUN message « cannot be resolved » n'est publié.
- **Bornes** : 0 message « cannot be resolved ».
- **Statut** : actif — ignoré si le JDK 21 externe est absent (motif visible : `@EnabledIf("hasExternalJdk")`)

### externalJdkCompletesStringMembers — complétion des membres depuis le jrt externe
- **Préconditions / entrée** : même moteur sur JDK externe ; source « return s.le| ».
- **Comportement attendu** : complétion non null ; « length » proposé — les membres de `String` résolvent depuis l'image modules externe.
- **Bornes** : —
- **Statut** : actif — ignoré si le JDK 21 externe est absent (motif visible : `@EnabledIf("hasExternalJdk")`)

### nullJdkHomeFallsBackToRuntime — jdkHome null → jrt du runtime
- **Préconditions / entrée** : moteur construit SANS jdkHome explicite (racines et classpath vides, « 17 »).
- **Comportement attendu** : `isLikelyUsable()` vrai — le jrt du runtime courant est utilisé par défaut.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Trois configurations de plateforme à distinguer : jdkHome explicite (jrt externe via `lib/jrt-fs.jar`), jdkHome null (jrt du runtime), jdkHome sans `lib/modules` (régime instantané — voir fiche 028).
- Le chargement du jrt externe passe par URLClassLoader + ServiceLoader (fournisseur jrt) — point dur à reproduire sur Android/ART où le JDK Termux est hors de l'app.
- `isLikelyUsable()` est la sonde observable de la disponibilité de la plateforme : vrai dès qu'un jrt (externe ou runtime) charge.
- Dépendance environnementale : les deux premiers tests exigent un JDK 21 système à chemin fixe — sur machine sans ce JDK ils sont ignorés (skip visible, pas un échec).

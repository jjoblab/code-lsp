# Fiche 037 — PopupSpontaneReproTestHelper

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/PopupSpontaneReproTestHelper.java`.
> **Domaine** : autre
> **Résumé** : AIDE de test (aucune méthode `@Test`) : assistant partagé qui construit un R.jar « réaliste » (aapt2-like) pour les bancs — notamment celui de `PopupSpontaneReproTest` (fiche 036). Écrit la source `R.java` (paquet `com.example.monip`, classe finale `R` avec classes internes statiques vides `style`, `id`, `string`), la compile via le compilateur système et package les 4 `.class` dans `r-bench.jar`. Version partagée de la méthode privée `buildRJar` dupliquée dans le test 036.

## Comportements

### buildRJar — fabrique le R.jar réaliste (service rendu, pas un test)
- **Préconditions / entrée** : JDK avec compilateur système (`ToolProvider.getSystemJavaCompiler()`) ; un répertoire temporaire de travail passé en argument.
- **Comportement attendu** : rend le chemin d'un jar `r-bench.jar` contenant exactement 4 entrées — `com/example/monip/R.class`, `com/example/monip/R$style.class`, `com/example/monip/R$id.class`, `com/example/monip/R$string.class` — compilées depuis la source `R.java` embarquée ; un échec de `javac` lève `IllegalStateException("javac R.java a échoué")`.
- **Bornes** : 4 entrées exactement ; compilation `-source 17 -target 17`.
- **Statut** : — (aide de test, jamais exécutée seule)

## Notes pour la réécriture
- Le helper est la source canonique du fixture R.jar : la même fabrique (source, commandes javac, 4 entrées) est dupliquée en privé dans le test 036 — ne reproduire qu'une seule fabrique côté réécriture.
- La forme visée imite la sortie d'aapt2 : une classe `R` finale du paquet de l'application avec classes internes `R$style`/`R$id`/`R$string` — c'est ce qui rendait les suggestions « R$style / R$id / R$string » observables.
- Tout échec de compilation est fatal (exception explicite) : la fixture est supposée toujours compiler.

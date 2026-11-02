# Fiche 053 — ImportCompletionIntegrationTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/ImportCompletionIntegrationTest.java`.
> **Domaine** : completion
> **Résumé** : Test d'intégration in-process de la complétion dans un contexte d'import (repro utilisateur : après « import android. », le serveur proposait des mots-clés au lieu de la continuité du package). Vérifie la composition didChange → completion : le serveur doit détecter le contexte d'import sur le texte à jour et rendre les sous-paquets directs + les types directs, jamais des mots-clés. Montage : serveur réel initialisé avec `android.jar` seul (Java 17) sur une racine `app/src/main/java/com/example/e` (rootUri + workspaceFolders), didOpen/didChange full-sync puis requête completion. Hypothèse visible dans `@BeforeAll` : `android.jar` du SDK local présent (candidats android-34/35, `ANDROID_HOME`), sinon toute la classe est ignorée à l'exécution.

## Comportements

### didChange_then_completion_returns_subpackages_not_keywords — didChange puis completion : sous-paquets, pas mots-clés
- **Préconditions / entrée** : serveur initialisé (rootUri et workspaceFolders = racine source, `android.jar` seul au classpath) ; `didOpen` de `MainActivity.java` (classe vide, version 1) ; `didChange` full-sync (range null, version 2) insérant « import android. » en ligne 2 ; requête `completion` à la position (2, 15) — juste après le point — avec `CompletionTriggerKind.Invoked`.
- **Comportement attendu** : la réponse (liste d'items ou `CompletionList`) est non-null et non vide ; les labels contiennent les sous-paquets directs « os », « content », « widget » et les types directs « Manifest » et « R » ; aucun des 20 mots-clés Java suivants ne figure dans les labels : package, public, class, interface, void, static, import, final, private, protected, extends, implements, new, return, if, else, for, while, try, catch ; chaque item typé porte un kind Module, Class, Interface, Enum ou Struct — jamais Keyword.
- **Bornes** : réponse attendue en 30 s max ; position (2, 15) ; 20 mots-clés proscrits ; 5 kinds autorisés (Module/Class/Interface/Enum/Struct).
- **Statut** : actif

### didOpen_with_import_returns_subpackages — didOpen seul (texte déjà à jour) suffit
- **Préconditions / entrée** : `didOpen` de `Main2.java` dont le texte contient déjà « import android. » (version 1) ; requête `completion` à la position (2, 15).
- **Comportement attendu** : liste non-null ; « os » et « content » sont proposés ; les mots-clés « package » et « class » sont absents des labels.
- **Bornes** : réponse en 30 s max ; position (2, 15).
- **Statut** : actif

### completion_import_prefix_returns_top_level_packages — préfixe sans point : paquets de premier niveau
- **Préconditions / entrée** : `didOpen` de `Main3.java` avec « import and » (préfixe non vide, sans point) ; requête `completion` à la position (2, 10) — juste après « and ».
- **Comportement attendu** : les paquets de premier niveau « android » et « androidx » sont proposés (filtrage par préfixe) ; le mot-clé « package » est absent des labels.
- **Bornes** : réponse en 30 s max ; position (2, 10).
- **Statut** : actif

## Notes pour la réécriture
- Le contrat clé est la composition didChange → completion : la complétion doit refléter le texte du didChange reçu juste avant (le client réel flush le didChange en attente AVANT la requête, même file FIFO côté LSP4J) ; un texte périmé fait rater le contexte import et retomber sur les mots-clés — c'est la signature du bug verrouillée ici.
- En contexte import : sous-paquets rendus en kind Module (Package mappé Module en LSP4J 0.22+), types directs en Class — jamais Keyword (14).
- Les types directs « Manifest » et « R » doivent apparaître (règle « golden rules » citée par le test).
- Dépendance d'environnement : `android.jar` (plateformes 34/35, `ANDROID_HOME`) — aucun cache Gradle requis ; sans le jar, la classe entière est ignorée (hypothèse visible).
- Documents ouverts par URI résolue depuis la racine source (MainActivity.java, Main2.java, Main3.java) avec version 1, didChange en version 2.

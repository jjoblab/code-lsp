# Fiche 064 — CreateMethodFixTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/services/CreateMethodFixTest.java`.
> **Domaine** : diagnostics
> **Résumé** : Quick-fix « Créer la méthode » (famille UNDEFINED_METHOD) — portage du CreateMethodFromUsage du projet tiers de référence : stub `private [static]` inséré APRÈS la méthode englobante, types des paramètres déduits des arguments STRUCTURÉS du diagnostic ecj, retour `void` (appel-instruction) ou `Object` (appel-expression), corps en `UnsupportedOperationException`. Particularité forte : les diagnostics viennent du VRAI moteur ecj (diagnose) — plage et arguments réels, pas synthétiques ; le test 1 re-diagnostique le texte corrigé et exige la disparition de l'erreur. Hypothèse visible dans `@BeforeAll` : `android.jar` présent, sinon classe ignorée.

## Comportements

### statementCallCreatesVoidPrivateMethod — appel-instruction → stub void privé
- **Préconditions / entrée** : `Main.java` avec l'appel-instruction `compute(1, "x")` dans `run()` ; diagnostic UndefinedMethod RÉEL produit par le moteur.
- **Comportement attendu** : une action « quickfix.create.method » ; le stub inséré est « private void compute(int arg0, String arg1) { » ; son corps est « throw new UnsupportedOperationException("TODO: compute"); » ; le stub est inséré APRÈS la méthode englobante `run()` ; le fichier corrigé re-diagnostiqué ne contient PLUS AUCUN UndefinedMethod.
- **Bornes** : 0 UndefinedMethod après correction (compte exact).
- **Statut** : actif

### assignmentCallCreatesObjectReturningMethod — appel-expression → retour Object
- **Préconditions / entrée** : `Assign.java` avec « int r = compute(1); » (l'appel est une expression).
- **Comportement attendu** : le stub créé est « private Object compute(int arg0) { » — retour `Object` quand la valeur de l'appel est utilisée.
- **Bornes** : —
- **Statut** : actif

### staticCallSiteCreatesStaticMethod — site statique → stub statique
- **Préconditions / entrée** : `Stat.java` où l'appel `compute(1)` est fait depuis une méthode `static`.
- **Comportement attendu** : le stub est « private static void compute(int arg0) { » — le `static` suit le contexte du site d'appel.
- **Bornes** : —
- **Statut** : actif

### qualifiedCallOnOtherTypeNotFixed — appel qualifié sur un autre type : pas de fix
- **Préconditions / entrée** : `Other.java` avec « "abc".noSuchMethod(1); » (appel qualifié dont le propriétaire est un AUTRE type que le type englobant).
- **Comportement attendu** : si le diagnostic UndefinedMethod existe, AUCUNE action « quickfix.create.method » (jamais de création de méthode dans un autre fichier/type) ; le test ne vérifie le fix que si le diagnostic est présent.
- **Bornes** : —
- **Statut** : actif (vérification conditionnelle explicite dans le test)

### fqnParamsShortenedWithImportEdit — paramètres FQN raccourcis + import
- **Préconditions / entrée** : `Fqn.java` avec « void run(java.util.List<String> items) { process(items); } ».
- **Comportement attendu** : le stub utilise la forme RACCOURCIE « process(List<String> arg0) » et une édition séparée ajoute « import java.util.List; ».
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Le stub est TOUJOURS `private` (+ `static` si le site d'appel est statique), inséré après la méthode englobante, avec les paramètres nommés `arg0`, `arg1`, … typés depuis les arguments structurés du diagnostic.
- Retour : `void` si l'appel EST l'instruction ; `Object` si l'appel est une expression (affectation).
- Corps imposé : « throw new UnsupportedOperationException("TODO: <nom>"); ».
- Les FQN de paramètres sont raccourcis vers le nom simple, avec édition d'import associée.
- Le test boucle la boucle : le texte corrigé est re-diagnostiqué par le moteur réel et doit être sans erreur UndefinedMethod — bon modèle de « quick-fix vérifié » pour le banc de parité.
- Hypothèse d'environnement android.jar (la méthode findAndroidJar est partagée avec CodeActionServiceTest) — sinon classe ignorée.

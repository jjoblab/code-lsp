# Fiche 001 — DiagnosticServiceTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/DiagnosticServiceTest.java`.
> **Domaine** : diagnostics
> **Résumé** : Couche la plus ancienne du service de diagnostics (API interne `Diagnostic`/`Severity`/`Range` exprimée en offsets absolus). Verrouille les invariants structurels de la liste retournée par `diagnose(nomFichier, source)` : liste non nulle, sévérité/message/range renseignés, offsets cohérents, tolérance aux erreurs de syntaxe graves. Fixtures minimales : classe `Test` avec variable non définie, syntaxe cassée, `return` en méthode `void`.

## Comportements

### testParseableCode — diagnostics sur code valide
- **Préconditions / entrée** : source Java valide (classe `Test`, méthode avec `int x = 42; System.out.println(x);`), fichier `Test.java`.
- **Comportement attendu** : `diagnose` retourne une liste non nulle (aucune autre exigence sur le contenu).
- **Bornes** : —
- **Statut** : actif

### testDiagnosticSeverity — sévérité renseignée sur chaque diagnostic
- **Préconditions / entrée** : source avec variable non définie (`System.out.println(undefined)`).
- **Comportement attendu** : chaque diagnostic porte une sévérité non nulle et un message non nul.
- **Bornes** : —
- **Statut** : actif

### testDiagnosticRange — plage d'offsets valide
- **Préconditions / entrée** : même source avec `undefined`.
- **Comportement attendu** : chaque diagnostic a une `range` non nulle ; `startOffset >= 0` ; `endOffset >= startOffset`.
- **Bornes** : —
- **Statut** : actif

### testDiagnosticMessage — message non vide
- **Préconditions / entrée** : même source avec `undefined`.
- **Comportement attendu** : chaque diagnostic a un message non vide.
- **Bornes** : —
- **Statut** : actif

### testSyntaxError — tolérance aux erreurs de syntaxe graves
- **Préconditions / entrée** : source avec erreur de syntaxe (`public void method( {`).
- **Comportement attendu** : soit `diagnose` retourne une liste non nulle, soit lève une `RuntimeException` — les deux issues sont acceptées (comportement tolérant du parseur sur erreurs graves).
- **Bornes** : —
- **Statut** : actif

### testReturnType — return en méthode void
- **Préconditions / entrée** : `return 42;` dans une méthode `void`.
- **Comportement attendu** : `diagnose` retourne une liste non nulle (aucune exigence sur le contenu).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Le contrat vérifié est faible (non-null / cohérence de champs) : les bornes fortes sur le contenu des diagnostics viennent d'`EcjCompilerEngineTest` (fiche 013).
- La `Range` est en offsets absolus dans le fichier (`startOffset`/`endOffset`), pas en ligne/colonne.
- L'exception sur erreur de syntaxe grave est un comportement *acceptable*, pas exigé : la réécriture Kotlin peut choisir de toujours retourner des diagnostics sans violer ce test.
- Le service est instancié autour d'un `CompilationEnvironment` vierge — aucun classpath n'est requis.

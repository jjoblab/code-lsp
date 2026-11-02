# Fiche 030 — EcjVarTypeHintsTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjVarTypeHintsTest.java` (la classe interne se nomme `JdtVarTypeHintsTest`).
> **Domaine** : hover-inlay
> **Résumé** : Tests des inlay hints de TYPE des déclarations `var` (`typeHintsFor`) : le type RÉEL de l'initialiseur (appel de méthode, référence générique, enhanced-for) est affiché et ancré juste après le NOM de la déclaration, avant le « = ». Les déclarations typées explicitement ne sont pas annotées, et un « var » dans une chaîne littérale n'est pas une déclaration. Corpus : classe `demo.Main` dont le corps est injecté (avec `import java.util.List`).

## Comportements

### complexInitializerGetsRealType — type réel d'un initialiseur complexe
- **Préconditions / entrée** : « var s = String.valueOf(42); ».
- **Comportement attendu** : un hint « : String » ancré à la fin du nom « s » — le type vient du binding de l'APPEL, pas du texte.
- **Bornes** : —
- **Statut** : actif

### genericsPreserved — généricité préservée
- **Préconditions / entrée** : « void run(List<String> items) » avec « var list = items; ».
- **Comportement attendu** : hint « : List<String> » (le type générique complet, pas d'effacement).
- **Bornes** : —
- **Statut** : actif

### enhancedForVarHinted — `var` du enhanced-for annoté
- **Préconditions / entrée** : « for (var it : items) » où `items` est `List<String>`.
- **Comportement attendu** : hint « : String » — le var de boucle est annoté du type d'élément.
- **Bornes** : —
- **Statut** : actif

### typedDeclarationNotHinted — déclarations typées : aucun hint
- **Préconditions / entrée** : « int plain = 1; » et « String s = "x"; » (types explicites).
- **Comportement attendu** : AUCUN hint émis — seules les déclarations `var` sont annotées.
- **Bornes** : 0 hint.
- **Statut** : actif

### varInStringLiteralNotHinted — « var » dans une chaîne : pas de hint
- **Préconditions / entrée** : « String s = "var x = 1;"; » (le mot var vit dans un littéral).
- **Comportement attendu** : aucun hint — l'analyse est structurelle (AST), pas textuelle.
- **Bornes** : 0 hint.
- **Statut** : actif

### anchorIsAtNameEnd — ancre en fin de nom, avant le « = »
- **Préconditions / entrée** : « var greeting = "hi"; ».
- **Comportement attendu** : l'offset du hint est JUSTE après « greeting », AVANT le « = » (les 2 caractères à l'offset sont «  = ») ; label « : String ».
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Label canonique « : <Type> » (deux-points + espace + type lié) ; ancre = fin exacte du nom de la déclaration.
- Le hint ne concerne QUE `var` (locale ou enhanced-for) ; le type affiché est le type RÉEL lié, généricité comprise.
- Le test localise la déclaration par le motif « var <nom> » (pas la première occurrence du nom) — l'implémentation Kotlin doit distinguer déclaration et usages pour l'ancre.
- Anti-piège verrouillé : un « var » textuel dans une chaîne littérale ne doit jamais produire de hint.
- Anomalie de nommage : fichier `EcjVarTypeHintsTest.java`, classe `JdtVarTypeHintsTest`.

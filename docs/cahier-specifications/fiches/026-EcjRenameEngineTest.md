# Fiche 026 — EcjRenameEngineTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjRenameEngineTest.java`.
> **Domaine** : rename
> **Résumé** : Tests du renommage sûr structurel (`EcjRenameEngine`) : identification de cible par bindings (méthode, champ, locale, type — avec clé structurelle `kind:paquet.Classe.membre`), collecte des références (déclaration + usages simples et qualifiés + imports + constructeurs pour un type), non-renommage des homonymes d'une autre classe, balayage multi-fichiers des racines sources, et court-circuit file-local pour les locales. Fixtures : classes `demo.Main`, `demo.Other`, `demo.Config`, et pour le multi-fichiers un projet `demo` avec `User` (champ `name`), `Printer` (usage qualifié `user.name`), `Order` (champ homonyme `name`) et `Unrelated`.

## Comportements

### targetAtOnMethodDeclaration — cible méthode à la déclaration
- **Préconditions / entrée** : caret sur « greet » (déclaration de méthode).
- **Comportement attendu** : cible non null ; oldName « greet » ; kind « method » ; clé structurelle commençant par « method:demo.Main.greet( ».
- **Bornes** : —
- **Statut** : actif

### targetAtOnField — cible champ
- **Préconditions / entrée** : caret sur « counter » (déclaration de champ).
- **Comportement attendu** : kind « field » ; clé exacte « field:demo.Main.counter ».
- **Bornes** : —
- **Statut** : actif

### targetAtOnLocalIsFileLocal — une locale est file-local
- **Préconditions / entrée** : caret sur « total » (locale de méthode).
- **Comportement attendu** : cible servie avec fileLocal = true (le renommage ne sortira pas du fichier).
- **Bornes** : —
- **Statut** : actif

### targetAtOnType — cible type
- **Préconditions / entrée** : caret sur « Main » (nom de classe).
- **Comportement attendu** : isType = true ; clé exacte « type:demo.Main ».
- **Bornes** : —
- **Statut** : actif

### targetAtOutsideNameIsNull — hors nom : null
- **Préconditions / entrée** : offset 0 (début de fichier, hors identifiant).
- **Comportement attendu** : cible null.
- **Bornes** : —
- **Statut** : actif

### referencesIncludeDeclarationAndUses — références = déclaration + usages
- **Préconditions / entrée** : champ `counter` déclaré puis lu/écrit 3 fois dans `next()`.
- **Comportement attendu** : 4 occurrences (1 déclaration + 3 usages) ; CHAQUE occurrence couvre exactement le texte « counter ».
- **Bornes** : 4 occurrences.
- **Statut** : actif

### homonymMethodOfDifferentClassIsNotRenamed — homonyme d'une autre classe exclu
- **Préconditions / entrée** : `Main.get()` et `Other.get()` homonymes ; cible = la DÉCLARATION de `Main.get` ; source contient l'appel nu `get()` et l'appel qualifié `other.get()`.
- **Comportement attendu** : exactement 2 occurrences (déclaration + appel nu) ; l'appel `other.get()` n'est PAS retouché.
- **Bornes** : 2 occurrences ; 0 sur l'homonyme.
- **Statut** : actif

### qualifiedFieldReferenceIsCaught — usage qualifié de champ capté
- **Préconditions / entrée** : champ `Config.timeout` ; usage « config.timeout » dans une autre classe du fichier.
- **Comportement attendu** : clé « field:demo.Config.timeout » ; 2 occurrences (déclaration + usage qualifié).
- **Bornes** : 2 occurrences.
- **Statut** : actif

### typeRenameIncludesDeclarationConstructorAndImports — renommage de type : import + extends
- **Préconditions / entrée** : projet avec `demo/Helper.java` (source réelle) ; `Main.java` contient « import demo.Helper; » et « extends Helper » ; cible = Helper (via l'import ou l'extends).
- **Comportement attendu** : ≥ 2 occurrences ; l'IMPORT est retouché (au moins une occurrence vit sur une ligne commençant par « import »).
- **Bornes** : ≥ 2 occurrences.
- **Statut** : actif

### constructorDeclarationCountsForTypeRename — le constructeur compte pour le type
- **Préconditions / entrée** : cible prise sur le nom du CONSTRUCTEUR « Main() ».
- **Comportement attendu** : la cible est le TYPE (isType = true) ; 2 occurrences : « class Main » + « Main() ».
- **Bornes** : 2 occurrences.
- **Statut** : actif

### projectRenameSweepsSourceRoots — balayage projet multi-fichiers
- **Préconditions / entrée** : projet 4 fichiers : `User` (champ `name` + getter), `Printer` (usage qualifié `user.name`), `Unrelated` (méthode `name()` sans le champ), `Order` (champ homonyme `name`) ; cible = `field:demo.User.name` ; `renameProject`.
- **Comportement attendu** : sortie par fichier : `User.java` → 2 occurrences (déclaration + `return name`) ; `Printer.java` → 1 occurrence (`user.name`) ; `Order.java` → ABSENT de la map (homonyme non retouché) ; `Unrelated` non résolu/retouché.
- **Bornes** : User = 2 ; Printer = 1 ; Order = absent.
- **Statut** : actif

### fileLocalRenameSkipsProjectSweep — file-local : pas de balayage projet
- **Préconditions / entrée** : locale `total` (déclaration + usage) ; `renameProject`.
- **Comportement attendu** : la map contient UNE seule entrée (le fichier courant) avec 2 occurrences.
- **Bornes** : 1 fichier ; 2 occurrences.
- **Statut** : actif

## Notes pour la réécriture
- Clé structurelle de cible : « method:<paquet>.<classe>.<nom>( », « field:<paquet>.<classe>.<nom> », « type:<paquet>.<nom> » — c'est elle qui isole les homonymes.
- La sortie projet est une map fichier → liste d'occurrences (plages exactes du jeton) ; les fichiers sans occurrence n'y figurent pas.
- Cas particuliers du type : le constructeur renomme la classe ; l'import est une occurrence comme les autres.
- fileLocal (locales) court-circuite le balayage des racines sources — gain essentiel sur projets larges.
- Le test wrappe un cas dans try/catch re-lancé en AssertionError : toute exception du moteur est un échec, jamais un skip silencieux.

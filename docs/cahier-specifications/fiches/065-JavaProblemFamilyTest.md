# Fiche 065 — JavaProblemFamilyTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/services/JavaProblemFamilyTest.java`.
> **Domaine** : diagnostics
> **Résumé** : Mapping ID ecj → famille sémantique (JavaProblemFamily) : verrouille le dispatch ID-FIRST — chaque ID ecj testé mappe vers la même famille, indépendamment de la Locale de l'utilisateur (condition pour que les quick-fixes soient fiables sur une VM française/espagnole/etc.). Test unitaire pur (aucun moteur, aucun android.jar, toujours exécutable) : couvre `fromId` et `fromCode` (format « ecj:<ID> » utilisé par Diagnostic.code(), ID numérique brut, entrées invalides).

## Comportements

### undefinedTypesMapToUnresolvedReference — non-résolus → UNRESOLVED_REFERENCE
- **Préconditions / entrée** : `fromId` avec les IDs ecj UndefinedType, UndefinedName, UnresolvedVariable, UndefinedField, ImportNotFound.
- **Comportement attendu** : les 5 IDs mappent tous vers la famille UNRESOLVED_REFERENCE.
- **Bornes** : 5 IDs couverts.
- **Statut** : actif

### undefinedMethodMapsToItsOwnFamily — UndefinedMethod détaché
- **Préconditions / entrée** : `fromId` avec l'ID UndefinedMethod.
- **Comportement attendu** : famille propre UNDEFINED_METHOD (détachée de UNRESOLVED_REFERENCE) — le quick-fix « Importer X » ne s'applique pas à un nom de méthode ; le portage CreateMethodFromUsage prend le relais (stub private/private static dans le type englobant).
- **Bornes** : —
- **Statut** : actif

### unusedWarningMapsToUnusedFamily — inutilisés → UNUSED_IMPORT / UNUSED_DECLARATION
- **Préconditions / entrée** : `fromId` avec UnusedImport, LocalVariableIsNeverUsed, ArgumentIsNeverUsed, UnusedPrivateMethod, UnusedPrivateField, UnusedPrivateType.
- **Comportement attendu** : UnusedImport → UNUSED_IMPORT (famille propre) ; les 5 autres → UNUSED_DECLARATION.
- **Bornes** : 6 IDs couverts ; 1 + 5 familles.
- **Statut** : actif

### unhandledExceptionMapsToUncaughtExceptionFamily — exceptions non gérées → UNCAUGHT_EXCEPTION
- **Préconditions / entrée** : `fromId` avec UnhandledException, UnhandledExceptionInDefaultConstructor, UnhandledExceptionInImplicitConstructorCall, UnhandledExceptionOnAutoClose.
- **Comportement attendu** : les 4 IDs mappent vers UNCAUGHT_EXCEPTION.
- **Bornes** : 4 IDs couverts.
- **Statut** : actif

### typeMismatchMapsToTypeMismatchFamily — mismatches → TYPE_MISMATCH
- **Préconditions / entrée** : `fromId` avec TypeMismatch, ReturnTypeMismatch, IncompatibleReturnType.
- **Comportement attendu** : les 3 IDs mappent vers TYPE_MISMATCH.
- **Bornes** : 3 IDs couverts.
- **Statut** : actif

### missingSemicolonMapsToMissingSemi — point-virgule manquant → MISSING_SEMI
- **Préconditions / entrée** : `fromId` avec MissingSemiColon.
- **Comportement attendu** : mapping vers MISSING_SEMI.
- **Bornes** : —
- **Statut** : actif

### unmappedIdsReturnUnknown — IDs non mappés → UNKNOWN
- **Préconditions / entrée** : `fromId` avec ParsingError et ParsingErrorInsertTokenBefore.
- **Comportement attendu** : les 2 IDs mappent vers UNKNOWN — un ID non mappé ne déclenche AUCUN quick-fix ID-FIRST.
- **Bornes** : 2 IDs couverts.
- **Statut** : actif

### fromCodeParsesPrefixedCode — fromCode : préfixe ecj, brut, invalide
- **Préconditions / entrée** : `fromCode` avec : « ecj:<TypeMismatch> » (format du Diagnostic.code() LSP), « <TypeMismatch> » (ID numérique brut), `null`, « ecj:PasUnNombre », « garbage ».
- **Comportement attendu** : forme préfixée → TYPE_MISMATCH ; ID brut → TYPE_MISMATCH ; `null`, code non numérique et code parasite → UNKNOWN (jamais d'exception).
- **Bornes** : 5 entrées couvertes.
- **Statut** : actif

## Notes pour la réécriture
- `fromCode` doit accepter indifféremment « ecj:<ID> » (format du Diagnostic.code() LSP) et l'ID numérique brut ; toute entrée non parsable (null, non numérique, préfixe inconnu) rend UNKNOWN sans lever d'exception.
- Familles minimales à reproduire : UNRESOLVED_REFERENCE, UNDEFINED_METHOD, UNUSED_IMPORT, UNUSED_DECLARATION, UNCAUGHT_EXCEPTION, TYPE_MISMATCH, MISSING_SEMI, UNKNOWN.
- Le dispatch est ID-FIRST et Locale-indépendant : jamais de parsing du message humain pour choisir un quick-fix.
- UndefinedMethod est volontairement séparé de UNRESOLVED_REFERENCE (un nom de méthode n'est pas un type à importer) — c'est ce qui aiguille vers le quick-fix CreateMethodFromUsage.
- Test 100 % unitaire : aucune dépendance moteur/SDK — à conserver comme garde-fou de pureté du mapping en réécriture Kotlin.

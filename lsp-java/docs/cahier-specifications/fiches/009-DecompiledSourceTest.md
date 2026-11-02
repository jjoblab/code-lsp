# Fiche 009 — DecompiledSourceTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/DecompiledSourceTest.java`.
> **Domaine** : navigation
> **Résumé** : Fonctionnalité « GO TO Definition sur cible binaire » côté source : `decompiledSource(fqcn)` renvoie soit la source attachée (src.zip du JDK), soit un stub décompilé (en-tête « Decompiled from … ») pour les types du JDK (`java.lang.String`, `java.util.HashMap`) ; `isBinaryType` identifie les types binaires. Défensif : null pour FQCN inexistant, null ou vide. La plupart des tests exigent un JDK avec `lib/src.zip` ou `lib/modules` (Temurin 17 en CI) — condition `@EnabledIf("hasTestJdk")`.

## Comportements

### decompiledSource_returnsAttachedSourceForJdkClass — source attachée pour String
- **Préconditions / entrée** : `decompiledSource("java.lang.String")` (String est dans src.zip).
- **Comportement attendu** : source non null contenant « package java.lang » OU « public final class String » OU « class String ».
- **Bornes** : —
- **Statut** : actif (exécuté seulement si JDK de test présent — `@EnabledIf` ; sinon ignoré)

### decompiledSource_returnsSourceForHashMapWithMethods — HashMap : attachée OU stub avec méthodes
- **Préconditions / entrée** : `decompiledSource("java.util.HashMap")`.
- **Comportement attendu** : soit la source attachée (contient « public class HashMap » ou « class HashMap »), soit le stub (contient « Decompiled from java.util.HashMap ») ; si stub, il mentionne au moins une méthode publique parmi put/get/size.
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

### decompiledSource_returnsNullForNonexistentFqcn — FQCN inexistant
- **Préconditions / entrée** : `decompiledSource("does.not.Exist")`.
- **Comportement attendu** : null.
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

### isBinaryType_trueForJdkClass — classes du JDK sont binaires
- **Préconditions / entrée** : `isBinaryType("java.lang.String")` et `isBinaryType("java.util.HashMap")`.
- **Comportement attendu** : true dans les deux cas.
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

### isBinaryType_falseForNonexistentFqcn — FQCN inexistant non binaire
- **Préconditions / entrée** : `isBinaryType("does.not.Exist")`.
- **Comportement attendu** : false.
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

### isBinaryType_nullOrEmptyFqcn_returnsFalse — null / vide
- **Préconditions / entrée** : `isBinaryType(null)` et `isBinaryType("")`.
- **Comportement attendu** : false dans les deux cas.
- **Bornes** : —
- **Statut** : actif (`@EnabledIf` hasTestJdk)

### decompiledSource_nullOrEmptyFqcn_returnsNull — bail early sans JDK
- **Préconditions / entrée** : moteur construit SANS JDK explicite (aucune racine, aucun jar) ; `decompiledSource(null)` et `decompiledSource("")`.
- **Comportement attendu** : null dans les deux cas (sortie anticipée, sans nécessiter de JDK).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Deux représentations acceptées pour un même FQCN (source attachée vs stub décompilé) — le serveur LSP renvoie une Location `jdt://decompiled/...` dans les deux cas.
- Le stub minimal doit au moins mentionner les méthodes publiques (put/get/size pour HashMap) — critère de qualité du décompileur minimal.
- JDK de test : `JO_TEST_JDK_HOME`, sinon candidats outils (préférence `lib/src.zip` > `lib/modules`), sinon `java.home` — pattern partagé avec `BinaryDefinitionFallbackTest` (fiche 005).
- Les extraits de diagnostic du test sont tronqués à 200 caractères avec `\n`/`\r` remplacés — purement cosmétique, sans valeur contractuelle.
- Le runtime de build JUnit peut être un JRE minimal : les tests source-attachée ne tournent alors pas — d'où le double guard `@EnabledIf` + `assumeTrue` dans `newEngine()`.

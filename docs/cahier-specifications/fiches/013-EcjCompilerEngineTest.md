# Fiche 013 — EcjCompilerEngineTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjCompilerEngineTest.java`.
> **Domaine** : diagnostics
> **Résumé** : Tests du moteur ecj in-process sur JVM desktop : ces tests consomment le jar ecj PATCHÉ (tâche `patchEcjForArt`) et valident donc indirectement que les shims (`RuntimeVersion`, `InputStreamCompat`) satisfont les références réécrites — le Parser ecj passe par `InputStream.readAllBytes` (réécrit vers le shim) dès le premier resolve ; la plateforme (`java.lang.Object`, `String`…) vient de l'image jrt du JDK courant. Verrouille : utilisabilité desktop, 0 erreur sur code valide, erreurs typiques (variable non définie, mismatch de type, return en void), diagnostics localisés sur code cassé, résolution inter-fichiers depuis une racine source SANS build, résolution binaire depuis un jar du classpath.

## Comportements

### engineIsUsableOnDesktopViaJrt — utilisabilité via l'image jrt
- **Préconditions / entrée** : moteur construit vide (aucune racine source, aucun jar, niveau « 17 ») sur JVM desktop.
- **Comportement attendu** : `isLikelyUsable()` = true (l'image jrt du JDK courant rend le moteur utilisable).
- **Bornes** : —
- **Statut** : actif

### validCodeProducesNoErrors — code valide : zéro erreur
- **Préconditions / entrée** : MAIN_OK (main avec `String message = "Hello, " + args.length;`).
- **Comportement attendu** : 0 diagnostic de sévérité ERROR.
- **Bornes** : 0 erreur.
- **Statut** : actif

### undefinedVariableIsReported — variable non définie signalée
- **Préconditions / entrée** : `int x = undefinedVariable;`.
- **Comportement attendu** : ≥ 1 diagnostic ERROR dont le message contient « undefinedVariable ».
- **Bornes** : —
- **Statut** : actif

### typeMismatchIsReported — mismatch de type signalé
- **Préconditions / entrée** : `String s = 42;`.
- **Comportement attendu** : ≥ 1 diagnostic ERROR dont le message contient « mismatch » (insensible à la casse).
- **Bornes** : —
- **Statut** : actif

### voidMethodReturnIsReported — return en méthode void signalé
- **Préconditions / entrée** : `return 42;` dans une méthode `void`.
- **Comportement attendu** : ≥ 1 diagnostic ERROR dont le message contient « void » (attendu : « Void methods cannot return a value »).
- **Bornes** : —
- **Statut** : actif

### brokenCodeStillYieldsLocatedDiagnostics — code cassé : diagnostics localisés
- **Préconditions / entrée** : `void m() { if (x.` (code à moitié tapé, source non fermée).
- **Comportement attendu** : diagnostics NON vides ; chaque `range` non nulle avec `endOffset > startOffset` OU `endOffset == startOffset + 1` (range localisée, jamais plus dégénérée que cela).
- **Bornes** : —
- **Statut** : actif

### crossFileResolutionFromSourceRoot — résolution inter-fichiers sans build
- **Préconditions / entrée** : racine source temporaire contenant `demo/Helper.java` (`public static final int ANSWER = 42`) ; `Main.java` référence `Helper.ANSWER` ; AUCUN build préalable.
- **Comportement attendu** : 0 erreur (Helper résout depuis la racine source — pattern ModuleCompilationContext du projet tiers de référence).
- **Bornes** : 0 erreur.
- **Statut** : actif

### jarClasspathResolvesTypes — résolution binaire depuis un jar
- **Préconditions / entrée** : classpath = jar JUnit du classpath de test (localisé via `org.junit.jupiter.api.Test` code source) ; `Main.java` avec `@org.junit.jupiter.api.Test`.
- **Comportement attendu** : 0 erreur (résolution binaire depuis le jar).
- **Bornes** : 0 erreur.
- **Statut** : actif

## Notes pour la réécriture
- Les messages d'erreur sont assertés par SOUS-CHAÎNE (« undefinedVariable », « mismatch », « void ») — la réécriture Kotlin devra produire un vocabulaire équivalent, ou ces assertions devront être adaptées au banc de parité (ADR 0016).
- Résolution inter-fichiers SANS build préalable : contrat fondamental pour Android (pas de sortie Gradle disponible) — la racine source suffit.
- Le test valide indirectement le pipeline desktop (jar ecj patché + shims ART + image jrt) — la réécriture n'aura plus la contrainte de shim mais devra reproduire les mêmes diagnostics.
- La tolérance « range dégénérée d'au plus 1 caractère » sur code cassé est un garde-fou anti-crash utile pour l'éditeur mobile.
- Le jar de classpath est localisé dynamiquement (jar JUnit du runtime de test) — pas de dépendance à un SDK Android ici.

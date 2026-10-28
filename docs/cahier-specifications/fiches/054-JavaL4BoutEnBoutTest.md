# Fiche 054 — JavaL4BoutEnBoutTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/JavaL4BoutEnBoutTest.java`.
> **Domaine** : autre
> **Résumé** : Suite E2E multi-capacités du « lot L4 » (colonne « preuve E2E » de la matrice de capacités) : aller-retours LSP4J réels sur des capacités non couvertes par les suites existantes — formatage du document ENTIER, références, symboles de document, prepareRename+rename, surlignage d'occurrences, superDefinition et decompiledSource (méthodes expérimentales). Domaine « autre » car volontairement transversal (formatage, navigation, rename, sources binaires) — chaque comportement porte son propre périmètre. Fixtures : classe `T` mal indentée (`int x=1; total+=x;`), `Base`/`Derivee` (redéfinition), `T`+`U` (appel inter-fichiers), `java.lang.String` (binaire JDK). Montage : serveur réel sur un dossier `@TempDir`, classpath remplacé après initialize par `android.jar` réel — hypothèse visible : sans `android.jar`, tests ignorés.

## Comportements

### formattingDocumentEntierReindenteLeCorpsMalIndenté — formatage plein document réindente le corps
- **Préconditions / entrée** : `didOpen` de `demo/T.java` (corps de `a()` non indenté) ; requête `formatting` sur le document entier avec `FormattingOptions(4, false)` — tabulation 4, pas d'espaces d'insertion.
- **Comportement attendu** : des éditions non vides sont produites ; le texte reconstruit en appliquant les éditions contient « int x = 1; » précédé de 8 espaces — le corps de `a()` est réindenté, avec espaces autour de `=`.
- **Bornes** : réponse en 60 s max ; options (tabSize 4, insertSpaces false) ; indentation attendue à 8 espaces.
- **Statut** : actif

### referencesDUneMéthodeTrouventLesAppels — références d'une méthode : déclaration seule
- **Préconditions / entrée** : deux fichiers sur disque AVANT création du serveur et replaceAll : `demo/T.java` (déclare `a()`) et `demo/U.java` (`appelante(T t)` contenant `t.a()`) ; les deux ouverts ; requête `references` sur la déclaration « void a() » avec `ReferenceContext(includeDeclaration=true)`.
- **Comportement attendu** : exactement 1 référence rendue — la déclaration, dans `T.java`. L'appel inter-fichiers (`U.java`, `t.a()`) N'EST PAS rendu : écart moteur assumé, verrouillé tel quel par ce test (le commentaire du test le qualifie d'écart connu à instruire).
- **Bornes** : réponse en 60 s max ; taille attendue = 1 exactement.
- **Statut** : actif

### documentSymbolRendLaClasseEtSesMembres — documentSymbol : classe + membres
- **Préconditions / entrée** : `didOpen` de `demo/T.java` ; requête `documentSymbol`.
- **Comportement attendu** : 1 symbole au premier niveau (la classe `T`) ; au moins 2 enfants ; un enfant nommé « a ».
- **Bornes** : réponse en 60 s max ; 1 symbole racine ; ≥ 2 enfants.
- **Statut** : actif

### prepareRenamePuisRenameSurUnChamp — prepareRename puis rename sur un champ
- **Préconditions / entrée** : `didOpen` de `demo/T.java` ; `prepareRename` sur le champ « total » ; puis `rename` de « total » en « cumul » à la même position.
- **Comportement attendu** : `prepareRename` rend un résultat non-null (le champ est renommable, forme plage) ; `rename` rend un `WorkspaceEdit` avec des éditions pour l'URI du document courant ; au moins 2 éditions (déclaration + usages, dont `total+=x`).
- **Bornes** : chaque requête en 60 s max ; ≥ 2 éditions.
- **Statut** : actif

### documentHighlightDUnChampDéclarationEtUsages — surlignage : déclaration + usage
- **Préconditions / entrée** : `didOpen` de `demo/T.java` ; requête `documentHighlight` sur « total ».
- **Comportement attendu** : exactement 2 occurrences surlignées — la déclaration (`int total = 0`, écriture) et l'usage (`total+=x`, lecture).
- **Bornes** : réponse en 60 s max ; 2 occurrences exactement.
- **Statut** : actif

### superDefinitionDuneRedéfinitionTrouveLaMéthodeDeBase — super sur une redéfinition
- **Préconditions / entrée** : `demo/Base.java` (déclare `signaler()`) et `demo/Derivee.java` (`@Override signaler()`) sur disque, `Derivee` ouverte ; requête `superDefinition` au caret placé au milieu du nom « signaler() » de la redéfinition.
- **Comportement attendu** : exactement 1 cible ; son chemin se termine par `Base.java` — la méthode de BASE est la cible (GO TO « Super », méthode expérimentale).
- **Bornes** : réponse en 60 s max ; 1 cible exactement.
- **Statut** : actif

### decompiledSourceSurUneClasseDuJdkRendLaSource — decompiledSource sur un FQCN du JDK
- **Préconditions / entrée** : serveur initialisé (JDK du démon via `JO_TEST_JDK_HOME` branché sur l'environnement) ; `didOpen` de `demo/T.java` ; requête `decompiledSource` avec le FQCN « java.lang.String » (scénario de production : la définition a répondu `jdt://decompiled/<fqcn>`).
- **Comportement attendu** : réponse non-null ; champ `source` non-null, non vide (blank), contenant « String » — la source du JDK (attachée ou décompilée) est servie.
- **Bornes** : réponse en 60 s max.
- **Statut** : actif

## Notes pour la réécriture
- Hypothèse d'environnement visible (`assumeTrue(androidJar != null)`) dans le montage ET dans les tests à fichiers multiples : `ANDROID_HOME`/`ANDROID_SDK_ROOT`/`/tmp/my-project/android-sdk`/`/home/z/tools/android-sdk`, plateforme la plus haute d'abord — sans jar, tests ignorés.
- L'écart « références inter-fichiers non rendues » (U.java) est verrouillé à 1 référence : **décision tranchée le 28/10/2026 — parité stricte** (`PLAN-REECRITURE.md` §10.4). Le moteur neuf reproduit l'écart pendant le programme (gate « taille attendue = 1 exactement » inchangée) ; la correction (rendre l'appel inter-fichiers, borne « 2 exactement ») est une amélioration candidate post-bascule, avec re-qualification de la fiche à ce moment.
- Les fichiers doivent exister sur disque AVANT le `replaceAll` du classpath pour que la résolution du projet entier se fasse ; `JO_TEST_JDK_HOME` nourrit l'attachement de source des classes binaires.
- `superDefinition` et `decompiledSource` sont des méthodes EXPÉRIMENTALES (extensions hors protocole LSP standard) ; `superDefinition` rend une liste de cibles à champ `path`, `decompiledSource` prend un FQCN binaire.
- Les timeout E2E sont généreux (60 s par requête) : garder ces bornes comme garde-fous, pas comme objectifs de latence.

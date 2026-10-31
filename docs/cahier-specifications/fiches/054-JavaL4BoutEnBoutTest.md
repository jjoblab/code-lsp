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

### referencesDUneMéthodeTrouventLesAppels — références d'une méthode : déclaration + appel inter-fichiers
- **Préconditions / entrée** : deux fichiers sur disque AVANT création du serveur et replaceAll : `demo/T.java` (déclare `a()`) et `demo/U.java` (`appelante(T t)` contenant `t.a()`) ; les deux ouverts ; requête `references` sur la déclaration « void a() » avec `ReferenceContext(includeDeclaration=true)`.
- **Comportement attendu** : exactement **2** références rendues — la DÉCLARATION dans `T.java` ET l'APPEL inter-fichiers `t.a()` dans `U.java` (sa plage est convertie avec le repère du fichier PORTEUR et pointe le jeton « a »).
- **Bornes** : réponse en 60 s max ; taille attendue = **2** exactement.
- **Statut** : actif (**amendé D1**)
- **AMENDEMENT (décision D1 du propriétaire, 31/10/2026 — ADR 0028)** : le comportement d'origine (vagué 0 : « exactement 1 référence — la déclaration ; l'appel inter-fichiers N'EST PAS rendu : écart moteur assumé, verrouillé tel quel ») reprenait la parité stricte §10.4 avec le vendored. La bascule étant signée (v1.0.0, 30/10) et le propriétaire ayant approuvé D1, la parité stricte est FORMELLEMENT ABANDONNÉE pour `references` : le confinement protocolaire est retiré, la borne passe de 1 à 2, la correction prévue « post-bascule » est exécutée (vague V-E) avec re-qualification de la fiche — l'objet même de cette note.

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

### documentHighlightDUnChampDéclarationEtUsages — surlignage : déclaration + usage, genres verrouillés
- **Préconditions / entrée** : `didOpen` de `demo/T.java` ; requête `documentHighlight` sur « total ».
- **Comportement attendu** : exactement 2 occurrences surlignées — la déclaration (`int total = 0`) et l'usage (`total+=x`), TOUTES DEUX typées **Write** (`DocumentHighlightKind` 3).
- **Bornes** : réponse en 60 s max ; 2 occurrences exactement ; kinds 3/3.
- **Statut** : actif (**amendé V-E**)
- **AMENDEMENT (vague V-E, G-026 — ADR 0028 (b), 31/10/2026)** : la fiche d'origine décrivait l'usage `total+=x` comme une LECTURE (comportement observé du vendored). Une affectation COMPOSÉE **écrit** la variable : au niveau de la référence (distinction lecture/écriture des dialogues courants), l'écriture domine — le genre LSP est unique par plage, la lecture implicite d'une composée relève du data flow, pas du surlignage. La réécriture type donc déclaration, cibles d'affectation (simples ET composées) et incréments (`x++`/`++x`) en Write, le reste en Read, méthodes/types en Text.

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
- ~~L'écart « références inter-fichiers non rendues » (U.java) est verrouillé à 1 référence : décision tranchée le 28/10/2026 — parité stricte (`PLAN-REECRITURE.md` §10.4).~~ **AMENDÉ le 31/10/2026 (décision D1 — ADR 0028)** : la correction prévue « post-bascule » est exécutée par la vague V-E — la gate « taille attendue = 1 » est INVERSÉE (borne « 2 exactement »), la fiche re-qualifiée (voir l'amendement du comportement ci-dessus).
- Les fichiers doivent exister sur disque AVANT le `replaceAll` du classpath pour que la résolution du projet entier se fasse ; `JO_TEST_JDK_HOME` nourrit l'attachement de source des classes binaires.
- `superDefinition` et `decompiledSource` sont des méthodes EXPÉRIMENTALES (extensions hors protocole LSP standard) ; `superDefinition` rend une liste de cibles à champ `path`, `decompiledSource` prend un FQCN binaire.
- **Extension post-bascule (vague V-E, 31/10/2026)** : le RPC maison `textDocument/usagesGroupes` (ADR 0028 (e)) est couvert par le test E2E `usagesGroupesParFichier` de cette suite — groupes par fichier porteur, déclaration incluse et signalée, offsets absolus, plafond honnête annoncé (`total`/`rendus`/`plafond`/`tronque` — ADR 0028 R1). Contrat complet : `USAGE.md` §9.3.
- Les timeout E2E sont généreux (60 s par requête) : garder ces bornes comme garde-fous, pas comme objectifs de latence.

# ADR 0014 — Navigation, refactorings, usages

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

§5-D10. L'étude établit que le calcul ( usage search indexé, conflits typés, mutations ) est **détachable et 0-API-sensible-ART**, et que l'interaction ( dialogues, preview toolwindow, inplace, undo groupé ) est plateforme — 71/577 fichiers swing-awt ( 12,3 % ), 94/577 référencent Editor — et se rejoue côté client Android :

- **BaseRefactoringProcessor** ( 878 l. ) est auto-diagnostiqué par JetBrains : « heavy machinery from the early days of IntelliJ […] mixing UI with business logic » ( :95-96 ), « not compatible with the ModCommand API » ( :164-167 ) — ne pas embarquer.
- **Le rename est officiellement headless-able** : `RenamePsiElementProcessorCore` ( « holds the part of a rename that asks the user nothing » :22-29 ), `HeadlessRenameJavaMethodProcessor` ( « renames a Java method for a caller that has no user » :474-486 ). La spécialisation Java méthode montre le cahier des charges : `findReferences` par `MethodReferencesSearch` ( index, :191-195 ), **renommage des overrides** avec suggestion ( :292-328 ), **4 familles de collisions typées** ( `SubmemberHidesMemberUsageInfo` via `ClassInheritorsSearch`, member-hides-outer, collision de nom, prototype re-résolu :209-252 ), **réparations post-rename** ( qualification des références masquées, imports statiques, suppression de `@Override` devenu faux :143-156 ).
- **Usage search** : `ExtensibleQueryFactory` + `QueryExecutorBase` ( composition par EP — la javadoc mentionne « the language server root area », `ExtensibleQueryFactory.java:18-21` ) ; exécuteur type `MethodUsagesSearcher` ( `searchWord` sur l'index de mots + résolution des candidats, `DumbService.runReadActionInSmartMode`, :24-104 ) ; 22 QueryExecutors dans java-indexing-impl.
- **Statu quo** : `EcjRenameEngine` ( 973 l. ) — identification par **clé de binding** ( correcte, homonymes exclus ) ; mais **0 conflit détecté** ( `rg "conflict"` : 0 ), overrides volontairement non renommés ( :57-59 ), usages = `Files.walk` + pré-filtre textuel ( :226-280 ), aucune réparation de références masquées, pas de preview ; navigation/usages = balayage borné ( cap fichiers + budget wall-clock, résultat partiel ) ; `references` = mêmes occurrences que le rename ( `JavaTextDocumentService.java:1596-1634` ).

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule) : `platform/refactoring` `BaseRefactoringProcessor.java`, `java/java-impl-refactorings` ( 577 f / 99 850 l. ; rename 53, move 53, extractMethod 58 ) — `RenamePsiElementProcessorCore`, `RenameJavaMethodProcessor` ( lu en profondeur ), `java/java-indexing-impl` `MethodUsagesSearcher.java`, `ExtensibleQueryFactory.java`, `platform/usageView` ( 166 f / 15 581 l., 22 fichiers swing sur 77 côté impl ), `WriteCommandAction`/`CoreCommandProcessor`.
- `jjoblab/codeide-lsp` @ `cb809e14` : `EcjRenameEngine` ( 973 l. ), `NavigationService`, `JavaTextDocumentService` ( references/documentHighlight ), `RenameHonneteTest` — écarts mesurés ci-dessus.

## Options

**Option 1 — Greffer les protocoles IntelliJ sur ecj/CIDX ( recommandé ).** (a) **QueryExecutor par-dessus CIDX** en remplacement du balayage `Files.walk` : recherche d'usages indexée ( index de mots ou clés de binding dans CIDX — la seule brique « réutilisation » vraiment payante, D10 ) ; (b) **protocole de phases du rename** ( prepare/usage/conflicts/exec ) comme cahier des charges d'`EcjRenameEngine` v2 : liste **typée** de collisions + réparations post-rename + preview ; (c) projection LSP des `ModStartRename`/`ModShowConflicts` ( D9/ADR 0013 ) ; (d) le scheduler 3 lanes du statu quo remplace EDT/progression modale ( D11 ).

**Option 2 — Porter les refactorings IntelliJ.** `BaseRefactoringProcessor` auto-déprécié, usageView = présentation, EDT — rejeté ( D10 ).

**Option 3 — Améliorer le balayage `Files.walk` uniquement** ( caches, pré-filtres ) : gain limité ( borné par l'I/O ), ne donne ni conflits ni réparations — sous-ensemble de l'option 1.

**Option « ne rien changer »** : rename honnête actuel ( sans conflits, sans overrides, sans réparations ) — repli.

## Critères et mesures

- Écarts mesurés du rename actuel ( ci-dessus, D10 ) : 0 conflit, overrides exclus, `Files.walk`, pas de réparations, pas de preview.
- Valeur indexée : CIDX préfixe binaire p50/p95 0,16–0,76/1,1–7,1 ms ( petit→lourd ) — la base d'un usage search < 100 ms.
- Matrice de capacités : rename/references/documentHighlight/preparedRename — suivie en ADR 0015.

## Décision proposée

**Option 1** : (a) d'abord l'**usage search indexé** ( `references`/`documentHighlight` sur CIDX — remplace le balayage borné, même contrat LSP ) ; (b) puis `EcjRenameEngine` v2 par phases avec collisions typées + réparations + preview ( projection `ModShowConflicts` côté client : dialogue = décision utilisateur locale ) ; (c) le renommage des overrides reste **opt-in explicite** ( comportement actuel documenté comme volontaire, :57-59 — l'option par défaut reste « ne pas suivre les overrides » avec choix utilisateur ).

## Conséquences

- `references` devient exhaustif et rapide ( fini le résultat partiel borné ) ; le rename devient « sûr » ( collisions détectées avant écriture ) au prix d'une complexité maîtrisée ( phases typées ).
- Le modèle serveur-calcule/client-applique du statu quo est conservé ( `WriteCommandAction`/undo groupé IntelliJ non portés — le client LSP applique et possède l'undo ).
- L'index de mots ( si retenu ) ajoute des segments CIDX ( budget octets à étendre, ADR 0008/0011 ).

## Plan de migration progressive

1. Vague « usages indexés » : QueryExecutor sur CIDX + drapeau `usages.index` ; double moteur ( balayage borné conservé ) ; baselines de latence et d'exhaustivité.
2. Vague « rename v2 » : phases + collisions typées + réparations ; drapeau `rename.v2` ; corpus de renommages ( ADR 0016 ) ; preview LSP.
3. Retour arrière : chaque drapeau retombe sur le moteur actuel ( contrats LSP inchangés ).

## Tests de non-régression et critères de sortie

- Corpus rename ( scénarios dorés : renommage champ/méthode/classe, homonymes, overrides, imports statiques, `@Override` cassé ) : 0 conflit manqué ( comparaison à oracle ), réparations exactes, preview = résultat.
- Usages : rappel = 100 % des références résolues ( vs oracle bindings ) ; latence p95 < 100 ms ( lourd, bureau ).
- Critère de sortie vague 1 : `Files.walk` retiré du chemin chaud.

## Risques

- **R1 — Faux positifs de collisions** ( sur-détection ) : oracle humain sur corpus doré ; seuils conservateurs.
- **R2 — Index de mots trop volumineux** : budget octets + mesure ; alternative = clés de binding uniquement.
- **R3 — Réparations post-rename complexes à spécifier** : commencer par les 3 réparations documentées ( qualification, imports statiques, `@Override` ), étendre ensuite.

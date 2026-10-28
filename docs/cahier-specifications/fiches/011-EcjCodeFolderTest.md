# Fiche 011 — EcjCodeFolderTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjCodeFolderTest.java`.
> **Domaine** : folding
> **Résumé** : Calcul des régions de folding (`EcjCodeFolder.foldsFor(chemin, source)`) — parité `EcjCodeFolder` du projet tiers de référence : groupe d'imports replié par défaut, régions couvrant l'INTÉRIEUR des accolades (les `{}`/`()` restent visibles), commentaires de bloc et javadoc multi-lignes, RIEN pour les paires sur une seule ligne ni pour les accolades dans chaînes/commentaires/text-blocks ; blocs de contrôle et corps de lambda pliables. Moteur construit vide (aucune racine, aucun classpath) — le folding ne dépend d'aucune résolution de type.

## Comportements

### importGroupFoldedCollapsedByDefault — groupe d'imports replié par défaut
- **Préconditions / entrée** : deux imports (`java.util.List`, `java.util.Map`) suivis d'une classe.
- **Comportement attendu** : une fold kind « imports », placeholder « import ... », `collapsedByDefault` = true ; `start` au « import » du PREMIER ; `end` JUSTE APRÈS le « ; » du DERNIER import.
- **Bornes** : —
- **Statut** : actif

### noImportFoldWithoutImports — pas d'imports, pas de fold
- **Préconditions / entrée** : classe sans aucun import.
- **Comportement attendu** : aucune fold « imports ».
- **Bornes** : —
- **Statut** : actif

### bracePairsFoldInteriorOnly — accolades : intérieur seulement
- **Préconditions / entrée** : classe contenant une méthode `run()`.
- **Comportement attendu** : fold « block » du type : `start` = index de la `{` ouvrante + 1, `end` = index de la DERNIÈRE `}`, `collapsedByDefault` = false ; fold « block » de la méthode : `start` = accolade ouvrante + 1, `end` = accolade fermante (EXCLUE) — les accolades restent visibles après repli.
- **Bornes** : —
- **Statut** : actif

### singleLineBracesNotFolded — paires monolignes ignorées
- **Préconditions / entrée** : `public class Main { void run() { int x = 1; } }` écrit sur une seule ligne.
- **Comportement attendu** : liste de folds VIDE.
- **Bornes** : 0 fold.
- **Statut** : actif

### bracesInStringsAndCommentsIgnored — accolades dans chaînes / commentaires / text-blocks
- **Préconditions / entrée** : source contenant une chaîne `"{ not a fold"`, un commentaire de bloc multi-lignes et un text-block contenant `text { block } content`.
- **Comportement attendu** : la fold « comment » placeholder « /*…*/ » existe ; AUCUNE fold ne démarre juste après l'accolade de la chaîne ; aucune fold « block » ne se FERME sur l'accolade interne du text-block.
- **Bornes** : —
- **Statut** : actif

### controlFlowAndLambdaBlocksFold — blocs if et lambda pliables
- **Préconditions / entrée** : un bloc `if` multi-lignes et un corps de lambda multi-lignes dans une méthode.
- **Comportement attendu** : une fold « block » contient la position juste après l'accolade ouvrante du `if` ; une fold « block » contient la position juste après l'accolade ouvrante du lambda.
- **Bornes** : —
- **Statut** : actif

### blockAndJavadocCommentsFold — javadoc et commentaires de bloc
- **Préconditions / entrée** : une javadoc multi-lignes (`/** … */`) au-dessus de la classe et un commentaire de bloc multi-lignes dans la classe.
- **Comportement attendu** : fold « comment » placeholder « /**…*/ » : `start` au `/**`, `end` = index du `*/` + 2 ; fold « comment » placeholder « /*…*/ » : `start` au `/*` du commentaire de bloc.
- **Bornes** : —
- **Statut** : actif

### lineCommentsNeverFold — commentaires de ligne jamais pliés
- **Préconditions / entrée** : deux commentaires `//` consécutifs.
- **Comportement attendu** : aucune fold « comment ».
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Représentation observable : `Fold { kind, placeholder, start, end, collapsedByDefault }` en offsets caractères ; kinds contractuels : « imports », « block », « comment » ; placeholders : « import ... », « /*…*/ », « /**…*/ ».
- Contrat de placement : les régions d'accolades couvrent l'INTÉRIEUR (`{` incluse → `}` exclue) — seuls les commentaires s'étendent jusqu'AU-DELÀ de leur marqueur de fin (`*/` incluse).
- Seule la fold « imports » est repliée par défaut ; toutes les autres sont dépliées.
- Aucune fold ne doit naître d'une accolade située dans une chaîne, un commentaire ou un text-block — le test le vérifie par les positions de début/fin des folds.
- Le text-block de test est construit à l'exécution (concaténation) pour éviter l'imbrication de `"""` dans le source du test lui-même — astuce à conserver.

# Annexe — Rapport brut exp-D9D10 (D9 inspections/quick fixes, D10 refactorings/usages)

> Task ID: exp-D9D10 · Agent: explore-D9D10
> Reproduit tel que rendu par l'agent d'étude ; le document maître est `../01-architecture-psi.md`.
> **Note de provenance (R5)** : le bac à sable de la mission a été réinitialisé le 28/10/2026 (~10h13) ; ce rapport a été reconstruit le même jour depuis le contexte de mission (les citations `fichier:ligne` proviennent des lectures réelles effectuées sur le clone @ `865067b0` avant la réinitialisation ; le clone de référence a été recréé au même commit et des vérifications ponctuelles y ont été rejouées — journal : `worklog` de la mission). Traiter toute citation non re-vérifiée comme « à vérifier » au besoin.
- **Terrain** : JetBrains/intellij-community @ `865067b0a7f1a73ee0fa8bce4f042013c8ac334f` (clone sparse blob:none, LECTURE SEULE) ; ancien moteur jjoblab/codeide-lsp @ `cb809e14` (v1.16.0, lecture seule)
- **Licence sources consultées** : intellij-community = « JetBrains Open-Source Build Terms v1.3 » (15/06/2026), sources Apache-2.0 (LICENSE.txt) ; codeide-lsp = dépôt propriétaire (mission R9) ; fichiers `com.siyeh.ig.*` d'inspection portent des en-têtes Apache-2.0 « Copyright 2003-2015 Dave Griffith, Bas Leijdekkers » (ex. UnusedImportInspection.java:1-15, MisspelledEqualsInspection.java:1-15) — provenance InspectionGadgets à consigner dans PROVENANCE-REPRISE si reprise.
- **Conventions** : citation `fichier:ligne` ; « [hors sparse] » = fichier lu via `git cat-file -p HEAD:<chemin>` (absent du working tree sparse mais présent dans l'arbre git complet) ; aucune écriture dans les dépôts.
- Commandes de comptage indiquées en tête de chaque tableau ; aucun chiffre inventé.

# D9 — Inspections et quick fixes

## D9.1 Rôle et flux complet

Une **inspection locale** IntelliJ est un visitor PSI qui dépose des problèmes dans un holder. Chaîne d'appel vérifiée (points clés en `fichier:ligne`) :

1. **Déclaration** : `LocalInspectionTool extends InspectionProfileEntry implements PossiblyDumbAware` — `platform/analysis-api/src/com/intellij/codeInspection/LocalInspectionTool.java:37`. Le point d'entrée unique est `buildVisitor(ProblemsHolder, isOnTheFly, LocalInspectionToolSession)` → `PsiElementVisitor` (:138-140, :165-167) ; variante héritée `checkFile` (:120-122). Le contrat est **pur PSI** : aucun Editor, aucun index dans l'API.
2. **Moteur d'exécution (batch/headless)** : `InspectionEngine.inspectEx(toolWrappers, psiFile, restrictRange, priorityRange, isOnTheFly, …)` — `platform/analysis-impl/src/com/intellij/codeInsight/InspectionEngine.java:139-198` :
   - `Divider.divideInsideAndOutsideAllRoots` découpe le fichier selon la plage restreinte (:153) ;
   - `inspectElements` (:328-432) crée un `ProblemsHolder` par outil (:349), `createVisitor` (:75-100), puis `InspectionVisitorOptimizer.acceptElements(elements, visitor)` nourrit chaque élément (:388) ;
   - garde `assertUnderProgressIndicator()` (:435-438) : **exécution obligatoirement sous `ProgressManager.runProcess()`** ;
   - concurrence par `JobLauncher.invokeConcurrentlyUnderContextProgress` (:174, :428) ; injection de langages via `InjectedLanguageManager` (:161-173) ; suppressions via `SuppressionUtil.inspectionResultSuppressed` (:420).
3. **Exécution on-the-fly (éditeur)** : `LocalInspectionsPass extends ProgressableTextEditorHighlightingPass implements PossiblyDumbAware` [hors sparse] — `platform/lang-impl/src/com/intellij/codeInsight/daemon/impl/LocalInspectionsPass.java:86` (587 lignes). `collectInformationWithProgress` (:124) appelle `inspectEx` incrémentalement, convertit chaque `ProblemDescriptor` en `HighlightInfo` (modèle de surlignage de l'éditeur) via `ProblemDescriptorUtil.renderDescriptor`/`highlightTypeFromDescriptor` (:331, :357) et y attache les quick fixes (`registerQuickFixes` :450). Le filtre dumb-mode est explicite : `DumbToolWrapperCondition.value` → si dumb et non `DumbAware`, l'inspection est inactive (:560-576).
4. **Descripteur de problème** : `ProblemDescriptor extends CommonProblemDescriptor` — `platform/analysis-api/src/com/intellij/codeInsight/ProblemDescriptor.java:24-60` : `getPsiElement()`, `getTextRangeInElement()`, `getHighlightType()`, `setTextAttributes(TextAttributesKey)` (attributs textuels de présentation). Le `ProblemsHolder` est une simple liste + fabriques `registerProblem(...)` (`ProblemsHolder.java:81-178`) avec un builder moderne `problem(...).highlight(...).range(...).fix(ModCommandAction).register()` (:243-346).
5. **Quick fixes** : `LocalQuickFix extends QuickFix<ProblemDescriptor>, FileModifier` — `platform/analysis-api/src/com/intellij/codeInspection/LocalQuickFix.java:43`. Le fix classique s'exécute dans `applyFix(Project, CommonProblemDescriptor)` sous write action ; `availableInBatchMode()` (:51-53) distingue les fixes exigeant l'éditeur/l'UI ; `generatePreview` (:78-87) applique le fix sur une **copie non physique** du fichier (preview hors write action). `IntentionAction` (intentionnalités non liées à un problème) vit dans `platform/analysis-api/src/com/intellij/codeInsight/intention/IntentionAction.java` (22 fichiers dans `intention/`). L'adaptateur historique vers le daemon est `QuickFixAction` — `platform/analysis-impl/src/com/intellij/codeInsight/daemon/impl/quickfix/QuickFixAction.java`.
6. **Le style « mod-commands » (nouveau, 2024-2026)** : `ModCommand` est une **interface scellée de commandes déclaratives** — `platform/analysis-api/src/com/intellij/modcommand/ModCommand.java:59-62` (permits : ModChooseAction, ModCompositeCommand, ModCopyToClipboard, ModCreateFile, ModDeleteFile, ModDisplayMessage, ModEditOptions, ModHighlight, ModLaunchEditorAction, ModMoveFile, ModNavigate, ModNothing, ModOpenUrl, ModRegisterTabOut, ModShowConflicts, ModStartRename, ModStartTemplate, ModUpdateFileText, ModUpdateReferences, ModUpdateSystemOptions). Toutes les implémentations sont des **records** ; la javadoc l'assume : *« Thanks to this property, every ModCommand can be easily serialized and sent e.g., over the network, or to a different process »* (**ModCommand.java:42-43**). `ModCommandAction` est le remplacement déclaré de `IntentionAction` : *« An IntentionAction replacement that, once performed, produces a declarative ModCommand instead of performing the action right away »* — `ModCommandAction.java:31-33`. `ModCommandAction.perform(ActionContext)` calcule en read action un plan (texte à mettre à jour, fichiers créés/déplacés, navigation, choix utilisateur, conflits) ; l'exécution est déléguée à `ModCommandExecutor`/`ModCommandService` (impl : `platform/analysis-impl/src/com/intellij/lang/impl/modcommand/ModCommandServiceImpl.java`, 193 lignes, **lié à l'Editor** : import :25, `hostEditor.getCaretModel().getOffset()` :158). Le pont quick fix : `LocalQuickFix.from(ModCommandAction)` (`LocalQuickFix.java:123-125`) et `ProblemsHolder.ProblemBuilder.fix(ModCommandAction)` (:299-302).

   **Sémantique** : ce style **déplace la mutation hors du calcul** — le fix est calculé en read action (annulable, sérialisable, prévisualisable), et l'écriture devient un `ModUpdateFileText` (ancien + nouveau texte par fichier) appliqué plus tard par la plateforme. C'est exactement le modèle d'un CodeAction LSP (`WorkspaceEdit`), et la réponse de JetBrains au même problème que CodeIDE.
7. **Base Java** : `AbstractBaseJavaLocalInspectionTool` — `java/java-analysis-api/src/com/intellij/codeInsight/AbstractBaseJavaLocalInspectionTool.java:21` ajoute `checkMethod/checkClass/checkField` (:67-93), un visitor `JavaElementVisitor` (:96-130) et le filtrage par `JavaFeature` (niveau de langage) via `requiredFeatures()`/`isAvailableForFile` (:27-37).
8. **Diagnostics « compilateur » (rouge d'éditeur)** — à ne pas confondre avec les inspections : `HighlightVisitorImpl` — `java/java-analysis-impl/src/com/intellij/codeInsight/daemon/impl/analysis/HighlightVisitorImpl.java:62-68` (*« Java highlighting: reports compilation errors in Java code »*) délègue TOUT le calcul à `JavaErrorCollector` du module **java/codeserver** [hors sparse] : `java/codeserver/highlighting/src/com/intellij/java/codeserver/highlighting/JavaErrorCollector.java:20-43` (visitor PSI → `JavaCompilationError` typés, `JavaErrorKinds` = 1 912 lignes d'erreurs typées). Le module codeserver (49 fichiers Java, 18 130 lignes, comptés via boucle `git cat-file -p HEAD:$f | wc -l`) contient les checkers : ExpressionChecker 1 759 l., JavaErrorVisitor 1 498 l., GenericsChecker 1 105 l., ClassChecker 975 l., AnnotationChecker 768 l., SwitchChecker 683 l., MethodChecker 622 l. Aucun ecj : **les erreurs rouges d'IntelliJ sont une réimplémentation maison sur PSI/résolution**, pas un compilateur.

## D9.2 Classes/interfaces clés (chemins exacts)

| Rôle | Classe | Chemin |
|---|---|---|
| API inspection locale | `LocalInspectionTool` | platform/analysis-api/src/com/intellij/codeInspection/LocalInspectionTool.java |
| Session | `LocalInspectionToolSession` | platform/analysis-api/src/com/intellij/codeInspection/LocalInspectionToolSession.java |
| Marker cleanup | `CleanupLocalInspectionTool` (interface vide, marker XML `cleanupTool="true"`) | platform/analysis-api/src/com/intellij/codeInspection/CleanupLocalInspectionTool.java:17 |
| Holder | `ProblemsHolder` | platform/analysis-api/src/com/intellij/codeInspection/ProblemsHolder.java |
| Descripteur | `ProblemDescriptor`, `CommonProblemDescriptor` | platform/analysis-api/src/com/intellij/codeInspection/ProblemDescriptor.java |
| Sévérité | `ProblemHighlightType` (enum) | platform/analysis-api/src/com/intellij/codeInspection/ProblemHighlightType.java |
| Niveaux | `HighlightDisplayLevel` | platform/analysis-api/src/com/intellij/codeHighlighting/HighlightDisplayLevel.kt |
| Fabrique | `InspectionManager` (abstract, `getInstance(project)`) | platform/analysis-api/src/com/intellij/codeInspection/InspectionManager.java |
| Global | `GlobalInspectionTool`, `GlobalSimpleInspectionTool` | platform/analysis-api/src/com/intellij/codeInspection/GlobalInspectionTool.java, GlobalSimpleInspectionTool.java |
| Moteur d'exécution | `InspectionEngine` | platform/analysis-impl/src/com/intellij/codeInsight/InspectionEngine.java |
| Pass éditeur | `LocalInspectionsPass` [hors sparse] | platform/lang-impl/src/com/intellij/codeInsight/daemon/impl/LocalInspectionsPass.java |
| Rendu → éditeur | `ProblemDescriptorUtil` | platform/analysis-impl/src/com/intellij/codeInsight/ProblemDescriptorUtil.java |
| Quick fix | `LocalQuickFix`, `LocalQuickFixOnPsiElement`, `LocalQuickFixAsIntentionAdapter` | platform/analysis-api/src/com/intellij/codeInspection/LocalQuickFix.java etc. |
| Intention | `IntentionAction`, `IntentionManager` | platform/analysis-api/src/com/intellij/codeInsight/intention/IntentionAction.java |
| ModCommand | `ModCommand`, `ModCommandAction`, `ModCommandService`, `ModCommandExecutor`, `Presentation`, `PsiUpdateModCommandQuickFix` | platform/analysis-api/src/com/intellij/modcommand/ (37 fichiers) |
| Base Java | `AbstractBaseJavaLocalInspectionTool`, `BaseJavaLocalInspectionTool` | java/java-analysis-api/src/com/intellij/codeInspection/ |
| Erreurs Java | `JavaErrorCollector` [hors sparse] | java/codeserver/highlighting/src/com/intellij/java/codeserver/highlighting/JavaErrorCollector.java |
| Fixes d'erreurs | `DefaultJavaErrorFixProvider` (1 607 l.), `AbstractJavaErrorFixProvider` | java/java-analysis-impl/src/com/intellij/codeInsight/daemon/impl/analysis/ |

## D9.3 Dépendances plateforme et détachabilité

**Ce qui tient à la plateforme (à remplacer sous ART/LSP)** :
- `InspectionManager.getInstance(project)` + `ProblemsHolder(manager, psiFile, onTheFly)` : le holder fabrique les descripteurs via le manager → dépendance **Project** (légère : fabrique pure, remplaçable par une fabrique de module).
- `assertUnderProgressIndicator()` (InspectionEngine.java:435-438) et `JobLauncher` : thread model de la plateforme (annulation + parallélisme), substituable par un pool + CancelToken.
- `LocalInspectionsPass` : **toute la couche HighlightInfo/éditeur/EDT** (587 l. [hors sparse]) — pur rendu, sans calcul.
- Profils d'inspection (`InspectionProfile`, `InspectionEP`, paquet `options/` + `ui/` de analysis-api) : configuration persistante + UI de réglages — hors périmètre mobile utile.
- Dumb mode : `DumbService.isDumbAware(tool)` filtre les outils pendant l'indexation (LocalInspectionsPass.java:560-576 [hors sparse]) — concept utile (dumb 2 niveaux), implémentation plateforme.
- `ModCommandServiceImpl` : exécuteur **Editor-centrique** (:129-159) — à réécrire, mais les commandes elles-mêmes sont détachées.

**Ce qui reste si on retire Project/UI/Editor/Undo** : le triptyque `LocalInspectionTool.buildVisitor → PsiElementVisitor → ProblemsHolder.registerProblem(ProblemDescriptor + LocalQuickFix)` est **intégralement réutilisable en bibliothèque** : 0 awt/swing dans les 3 fichiers (vérifié). Les inspections elles-mêmes (les 332 fichiers Java) consomment PSI + résolution + index (selon l'inspection) — cf. §D9.5.

## D9.4 APIs sensibles ART (comptages `rg -l <pattern> <périmètre> | wc -l`)

| Périmètre | fichiers | javax.swing | java.awt | Unsafe | MethodHandles | VarHandle | ServiceLoader | mmap/MappedByteBuffer | ThreadLocal | management |
|---|---|---|---|---|---|---|---|---|---|---|
| analysis-api/codeInspection | 115 | 6 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| analysis-api/modcommand | 37 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| analysis-impl/codeInspection | 138 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| analysis-impl/codeInsight/daemon | 82 | 7 | 5 | 0 | 1 | 1 | 0 | 0 | 0 | 0 |
| java-analysis-api/codeInspection | 25 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| java-analysis-impl/codeInspection | 328 | 3 | 0 | 0 | 1 | 0 | 0 | 0 | 2 | 0 |
| java-analysis-impl/…/daemon | 100 | 1 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| java-impl-inspections/src | 332 | 1 | 0 | 0 | 1 | 2 | 0 | 0 | 1 | 0 |
| java-impl/src/…/codeInspection | 109 | 9 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

Lecture : **le cœur D9 est quasi propre pour ART**. Les imports swing dans l'API sont presque tous `javax.swing.Icon` (icônes de présentation — fichiers listés : Presentation.java, SuppressIntentionAction.java, ScopeToolState.java, LocalQuickFixBackedByIntentionAction.java, InspectionProfileEntry.java, OptionPaneRenderer.java, RefEntity.java) : interface shim-able. Le seul fichier swing des 332 inspections est `com/siyeh/ig/javadoc/MissingPackageInfoInspection.java`. VarHandle/MethodHandles touchent 1-2 fichiers par périmètre (identifiés par D3/D11 : LazyParseableElement etc.).

## D9.5 Ordres de grandeur et part calcul vs UI

Commandes : `rg --files <dir> | wc -l` ; lignes : `rg --files <dir> | xargs wc -l | tail -1`.

| Bloc | Fichiers | Lignes | Nature |
|---|---|---|---|
| API inspections (analysis-api/codeInspection) | 115 | 8 789 | API + options/ + ui/ |
| API modcommand | 37 | 2 975 | protocole déclaratif |
| Moteur (analysis-impl/codeInspection) | 138 | 21 846 | exécution batch + profiles + ex |
| analysis-impl/codeInsight/daemon | 82 | 13 439 | rendu éditeur (HighlightInfo etc.) |
| java-analysis-api/codeInspection | 25 | 1 613 | base Java |
| java-analysis-impl/codeInspection | 328 | 68 011 | inspections siyeh + deadCode + unusedImport… |
| java-analysis-impl/…/daemon | 100 | 14 369 | erreurs rouges + quickfixes d'erreur |
| **java-impl-inspections/src** | **332** | **78 317** | inspections IG récentes (stream, records, lambda…) |
| java-impl/src/…/codeInspection | 109 | 21 183 | deadCode UI + intentions ipp |
| java/codeserver [hors sparse] | 49 | 18 130 | erreurs « compilateur » |

Part UI : **1 fichier sur 332** importe swing dans java-impl-inspections ; 3/328 dans java-analysis-impl/codeInspection ; la présentation est concentrée dans le daemon (analysis-impl : 7 swing + 5 awt sur 82) et l'exécuteur ModCommand. Échantillonnage de 6 inspections représentatives :

| Inspection | Chemin | Consomme |
|---|---|---|
| `UnusedImportInspection` (GlobalSimple) | java/java-analysis-impl/src/com/intellij/codeInspection/unusedImport/UnusedImportInspection.java:39-69 | PSI pur (visitor `ImportsAreUsedVisitor`) + `multiResolve(false)` ; fix `DeleteImportFix` (ModCommand, java-analysis-impl/src/com/siyeh/ig/fixes/DeleteImportFix.java:25) |
| `MisspelledEqualsInspection` | java/java-impl-inspections/src/com/siyeh/ig/bugs/MisspelledEqualsInspection.java:29-62 | PSI pur (nom + arité) ; fix `RenameFix` |
| `UnusedSymbolLocalInspection` (locale, on-the-fly) | java/java-analysis-impl/src/com/intellij/codeInspection/unusedSymbol/UnusedSymbolLocalInspection.java:103, :142-143 | **requêtes search** (`SuperMethodsSearch`, `OverridingMethodsSearch`) + `PsiSearchHelper.isCheapEnoughToSearch` (java-analysis-impl/src/com/intellij/codeInsight/daemon/impl/UnusedSymbolUtil.java:251-271) → **index** |
| `UnusedDeclarationInspectionBase` (globale) | java/java-analysis-impl/src/com/intellij/codeInspection/deadCode/UnusedDeclarationInspectionBase.java (690 l.) | graphe Ref global (RefManager/RefClass/RefMethod), findClass sur `GlobalSearchScope.allScope` (:311-318) → plateforme lourde |
| `DefaultJavaErrorFixProvider` | java/java-analysis-impl/src/com/intellij/codeInsight/daemon/impl/analysis/DefaultJavaErrorFixProvider.java:454-… | erreurs typées du codeserver → `QuickFixFactory` (fixes d'import/cast/create method) |
| Familles java-impl-inspections | 28 sous-paquets `com.intellij.codeInspection.*` (accessStaticViaInstance…wrongPackageStatement) + 37 sous-paquets `com.siyeh.ig.*` | mixte PSI/résolution ; 147 fichiers sur 332 utilisent déjà ModCommand |

Inventaire par familles (comptage `rg --files <dir> | wc -l` dans java-impl-inspections) : style 17, redundancy 12, classlayout 12, bugs 10, controlflow 9, maturity 5, threading 5, migration 4, assignment 4, inheritance 3, imports 1 (+ 28 paquets `com.intellij.codeInspection.*` ciblés API récente : streamMigration, lambdaToExplicit, classCanBeRecord, enhancedSwitch, java18api/java19api…). JUnit et UnusedDeclaration sont hors module (java-impl / java-analysis-impl). 124 fichiers étendent `BaseJavaLocalInspectionTool`/`BaseInspection`.

## D9.6 Comparaison à armes égales avec le statu quo ecj

| Axe | IntelliJ (inspections + codeserver) | codeide-lsp v1.16.0 (ecj) |
|---|---|---|
| Source des diagnostics | 2 voies : codeserver (erreurs « compilateur » réimplémentées, 18 k l. hors sparse) + ~660 inspections lint | `DiagnosticService.diagnose` → `jdt.diagnose` (bindings ecj, fidélité compilateur native) — DiagnosticService.java:67-102 |
| Granularité/incrément | pass par plages (restrict/priority range), `runForWholeFile()` opt-in (LocalInspectionTool.java:96-109) | `EcjFastDiagnose` : re-diagnose du corps édité seul, corps frères remplacés par `throw null;` de même longueur, mémo clé-contenu, repli complet au doute (EcjFastDiagnose.java:18-83) |
| Fidélité compilateur | codeserver = réimplémentation (écarts à mesurer ; cf. D5 : inférence calée javac) ; inspections = lint | native ecj (constats W1-W10 + correctifs §17.1-17.2 LSP_JAVA.md : StringConcatFactory, databinding = bugs de fidélité résolus par connaissances Android) |
| Quick fixes | déclaratifs sérialisables (ModCommand), preview sur copie non physique, disponibles en batch (`availableInBatchMode`) | `CodeActionService` : 6 familles mappées ID→famille (JavaProblemFamily.java:34-67) ; édits texte concrets (record `Edit(start,end,text)` CodeActionService.java:35) : importFixes (depuis l'index de noms :158), createMethodFixes (stub :290), removeDeclarationFixes (:213), exceptionFixes try/catch+throws (:564), castFix (:619), point-virgule (:131) |
| Tags/props LSP | sévérités + attributs textuels | tags Unnecessary/Deprecated dérivés des IDs ecj (LSP_JAVA.md §19.1) |
| Supersession/annulation | ProgressIndicator + PCE (Job coroutine, D11) | CancelToken par fichier, annulation coopérative aux checkpoints INameEnvironment (DiagnosticService.java:23-33, :77-91) |

**Coûts des deux côtés** : embarquer les inspections IntelliJ exige le PSI + la résolution + l'index + un environnement headless (D1-D7), soit tout le socle déjà étudié — et n'apporte pas la fidélité compilateur (codeserver ≠ ecj). Le statu quo ecj a la fidélité et un fast-path maison, mais : couverture quick fix ~6 familles vs ~660 inspections ; aucun profil de sévérité ; diagnostics et quick fixes non découplés du message ID ecj (familles stables certes, JavaProblemFamily.java:26-30).

## D9.7 Verdict D9

**S'INSPIRER** — trois apports précis, un refus d'embarquement :
1. **Le protocole ModCommand** (ModCommand.java:42-43 : sérialisable réseau/processus) est LE modèle à adopter côté LSP : calculer les quick fixes en read action comme des plans déclaratifs (texte ancien/nouveau par fichier = `ModUpdateFileText`, équivalent direct de `WorkspaceEdit`) au lieu d'édits impératifs ad hoc. La frontière calcul/exécution de CodeActionService (919 l. d'édits maison) s'en trouverait réduite et preview-able par construction.
2. **L'architecture visitor/holder/descriptor** (3 classes, 0 UI) comme contrat d'inspection portable par-dessus le moteur existant : permettrait de greffer des inspections « lint » sans ecj, et plus tard d'en importer (sous contrôle R6/R9 : en-têtes Apache-2.0 siyeh).
3. **La séparation dumb-aware / dumb-ignorant** (LocalInspectionsPass.java:560-576) pour la montée en régime après indexation.

**NE PAS embarquer** : LocalInspectionsPass/HighlightInfo (éditeur), profils/options UI, ModCommandServiceImpl (Editor), UnusedDeclarationInspectionBase (graphe Ref global), et le codeserver comme substitut d'ecj pour les diagnostics (perdrait la fidélité compilateur, cœur du statu quo — cf. critère §4 « fidélité des diagnostics vs compilateur »).

## D9.8 Réponse à la question à trancher

**Quelle part dépend de la plateforme IDE vs du moteur ?** — Mesurée, la part plateforme est **majoritaire en code, minoritaire en valeur par inspection** : le contrat (`LocalInspectionTool`/`ProblemsHolder`/`ProblemDescriptor`/`LocalQuickFix`, ~1 500 lignes utiles) est pur PSI et détachable ; l'exécution on-the-fly (LocalInspectionsPass + HighlightInfo + daemon + EDT, ~14 k lignes) est du pur rendu éditeur ; le moteur batch (InspectionEngine + JobLauncher + ProgressIndicator, ~600 lignes) est un orchestrateur remplaçable. La valeur sémantique des ~660 inspections Java (147 k lignes au total avec java-analysis-impl) dépend des services de calcul (résolution D5, index D7) — pas de l'UI : 1 fichier swing sur 332 dans java-impl-inspections, 3 sur 328 dans java-analysis-impl. Le quick fix moderne (ModCommand) est quant à lui un **protocole sérialisable nativement adapté à LSP** — c'est la découverte la plus directement transposable de D9.

---

# D10 — Refactorings et recherche d'usages

## D10.1 Rôle et flux complet

### Refactoring « à l'ancienne » (BaseRefactoringProcessor)

Chaîne vérifiée dans `platform/refactoring/src/com/intellij/refactoring/BaseRefactoringProcessor.java` (878 l.) :

1. `run()` → `doRun()` (**@RequiresEdt**, hors write action) — :288-289 : `PsiDocumentManager.commitAllDocumentsUnderProgress()` (commit Document→PSI), `DumbService.completeJustSubmittedTasks()` (:299).
2. `findUsages()` sous `ReadAction.computeBlocking` (:303) **et** `ProgressManager.runProcessWithProgressSynchronously` (progression **modale**) (:323-324) ; garde dumb explicite : `IndexNotReadyException` ou `!DumbService.isUsableInCurrentContext(this)` → notification dumb-mode et arrêt (:335-338).
3. `preprocessUsages(refUsages)` (:354) → conflits → `processConflicts` : en mode interactif **dialogue UI** (`ConflictsDialog`), en tests exception `ConflictsInTestsException` (:535-539).
4. Preview : `previewRefactoring` (:387-418) ouvre une **UsageView** (toolwindow) ; `createPresentation` fabrique les libellés/compteurs (:471-524) — présentation pure.
5. `execute(usages)` (:451-461) : `CommandProcessor.executeCommand(project, …, getCommandName(), …)` (undo groupé, `markCurrentCommandAsGlobal`) → `doRefactoring` → `performRefactoring(usages)` (**@RequiresEdt @RequiresWriteLock** :274-276) : les mutations PSI.

La javadoc de la classe est un auto-diagnostic JetBrains : *« It's heavy machinery from the early days of IntelliJ. Implementations often end up complex, mixing UI with business logic. If possible, please do not create new classes that inherit from this class »* (:95-96), et : *« BaseRefactoringProcessor is not compatible with the ModCommand API […] because ModCommandQuickFix.perform() is executed in a background read action, but run() acquires a Write Intent Lock »* (:164-167).

### Le rename, échantillonné en profondeur

Architecture en couches récente (2026) — `platform/refactoring/src/com/intellij/refactoring/rename/RenamePsiElementProcessorCore.java:22-29` : *« It holds the part of a rename that asks the user nothing. RenamePsiElementProcessorBase adds the questions of an element […] HeadlessRenamePsiElementProcessor adds the answer of every question instead, so the engine of a rename with no user reads that one alone »*. Le noyau sans utilisateur :

- `findReferences(element, scope, …)` → `ReferencesSearch.search(element, scope).findAll()` (:51-55) ;
- `findCollisions(element, newName, allRenames, result)` : collecte des usages que le nouveau nom casse (:135-139) ;
- `renameElement` → `RenameUtil.doRenameGenericNamedElement` sous `@RequiresWriteLock` (:41-47) ;
- `createUsageInfo` (:149-153), `getTextOccurrenceSearchStrings` (:74), `getQualifiedNameAfterRename` (:84).

Spécialisation Java méthode — `java/java-impl-refactorings/src/com/intellij/refactoring/rename/RenameJavaMethodProcessor.java` (487 l.) :
- `findReferences` → `MethodReferencesSearch.search(method, scope, true).findAll()` (:191-195) — QueryExecutor sur index ;
- `prepareRenaming` (:292-328) : `SuperMethodWarningUtil.getSiblings` + `OverridingMethodsSearch` → **les redéfinitions sont renommées aussi** (nouveau nom suggéré par `RefactoringUtil.suggestNewOverriderName` :321) ; branchement EDT explicite : `EDT.isCurrentThreadEdt() ? runProcessWithProgressSynchronously : compute` (:306-309) ;
- `findCollisions` (:198-207) : 4 familles — `SubmemberHidesMemberUsageInfo` (via `ClassInheritorsSearch` :416-436), member-hides-outer, collision contre le nouveau nom, et `findHidingMethodWithOtherSignature` (**prototype renommé + re-résolution** pour détecter le re-liage d'appel :209-252) ;
- `findExistingNameConflicts` → `ConflictsUtil.checkMethodConflicts` (:269-282) ;
- `renameElement` (:85-157) : `method.setName`, `ref.handleElementRename(newName)` (:163), puis **réparations** : qualification des références masquées (`qualifyOuterMemberReferences`/`qualifyStaticImportReferences` :147-148), suppression de `@Override` devenu faux (:150-156) ;
- variante **headless** officielle : `HeadlessRenameJavaMethodProcessor` (:474-486) — *« Renames a Java method for a caller that has no user »*.

Le paquet rename Java (53 fichiers, 5 057 lignes) contient les collisions typées (`FieldHidesLocalUsageInfo`, `MemberHidesStaticImportUsageInfo`, `ClassHidesImportedClassUsageInfo`, `UnresolvableLocalCollisionUsageInfo`…), les validateurs (`JavaTypeRenameValidator`, `PsiPackageRenameValidator`), et le sous-paquet `inplace/` (3 fichiers Java seulement : la machinerie in-place est plateforme [hors sparse]).

### Recherche d'usages (le moteur)
- **API contrat** : `FindUsagesProvider` — `platform/indexing-api/src/com/intellij/lang/findUsages/FindUsagesProvider.java:16` (words scanner, type/descripteur de l'élément — **présentation + déclaration du scanner de mots**).
- **Mécanique Query** : `ExtensibleQueryFactory<Result, Parameters>` — `platform/core-api/src/com/intellij/psi/search/searches/ExtensibleQueryFactory.java:18-21` (résolution d'EP à chaque appel ; javadoc : *« one JVM may host several applications (tests), and the language server root area depends on the current analyzer context »* — trace explicite d'un usage language-server de la plateforme). `QueryExecutorBase` — `platform/core-api/src/com/intellij/openapi/application/QueryExecutorBase.java`. Les requêtes (`ReferencesSearch`, `MethodReferencesSearch`, `OverridingMethodsSearch`, `ClassInheritorsSearch`…) composent des **executors** enregistrés en EP (`java/java-indexing-impl/resources/META-INF/JavaIndexingPlugin.xml:87-92` : 6 `referencesSearch`, plus `methodReferencesSearch` :76).
- **Exécuteur type** : `MethodUsagesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>` — `java/java-indexing-impl/src/com/intellij/psi/impl/search/MethodUsagesSearcher.java:24-26` : tout le calcul se fait dans `DumbService.runReadActionInSmartMode` (:35, :55, :79) puis `SearchRequestCollector.searchWord(methodName, scope, searchContext, true, method, getTextOccurrenceProcessor(...))` (:86) — **recherche par mot dans l'index d'usages + résolution des candidats** (`MethodTextOccurrenceProcessor`), + accessors propriétés (`SimpleAccessorReferenceSearcher` :96). 22 fichiers QueryExecutor dans java-indexing-impl (`rg -l "extends QueryExecutorBase|implements QueryExecutor" | wc -l`).
- **Présentation des usages** : platform/usageView (89 fichiers/5 895 l., API : Usage, UsageGroup, UsagePresentation, UsageViewPresentation, TextChunk…) + platform/usageView-impl (77 fichiers/9 686 l.). L'adaptateur calc→vue est `UsageInfo2UsageAdapter`/`UsageInfoSearcherAdapter` (usageView/src/com/intellij/usages/). Le calcul utile est `UsageInfo` + `UsageSearcher` ; l'arbre/regroupement/onglets est présentation (22 fichiers swing sur 77 côté impl).

### Écriture / transaction / undo
- `WriteCommandAction` — `platform/core-api/src/com/intellij/openapi/command/WriteCommandAction.java` (224 l.) : compose `Application.runWriteAction` (:151) + `CommandProcessor.executeCommand` ; `runWriteCommandAction(project, …)` (:201-221).
- `CoreCommandProcessor` — `platform/core-impl/src/com/intellij/openapi/command/impl/CoreCommandProcessor.java` (442 l.) : `executeCommand` (:100+) fabrique un `CommandDescriptor` (:132), `fireCommandStarted/Finished`, marque undo global — **undo/redo = CommandProcessor + UndoManager (API dans analysis-api/src/com/intellij/openapi/command/undo/UndoManager.java, impl platform-impl [hors sparse])**.
- `WriteIntentReadAction` existe (`platform/core-api/src/com/intellij/openapi/application/WriteIntentReadAction.java`) ; **« AtomicActionNonBlocking » n'existe pas à ce commit** (introuvable dans l'arbre ; les NBRA = `NonBlockingReadAction` de platform-impl [hors sparse], cf. D11).

## D10.2 Classes/interfaces clés (chemins exacts)

| Rôle | Classe | Chemin |
|---|---|---|
| Orchestrateur historique | `BaseRefactoringProcessor` | platform/refactoring/src/com/intellij/refactoring/BaseRefactoringProcessor.java |
| Contrat action | `RefactoringActionHandler`, `RefactoringActionHandlerOnPsiElement` | platform/refactoring/src/com/intellij/refactoring/ |
| Preview | `PreviewableRefactoringActionHandler` | platform/refactoring/src/com/intellij/refactoring/PreviewableRefactoringActionHandler.java |
| Post-passes | `RefactoringHelper` (EP `com.intellij.refactoring.helper`, ex. optimize imports) | platform/refactoring/src/com/intellij/refactoring/RefactoringHelper.java |
| UI conflits | `ConflictsDialogBase` (interface : `setCommandName`, `showAndGet`, `isShowConflicts`) | platform/refactoring/src/com/intellij/refactoring/ConflictsDialogBase.java |
| Rename noyau | `RenamePsiElementProcessorCore` (+Base, Headless, DelegatingHeadless) | platform/refactoring/src/com/intellij/refactoring/rename/ |
| Rename handlers | `PsiElementRenameHandler`, `RenameRefactoringDialog`, `Renamer`, `RenamerFactory` | platform/refactoring/src/com/intellij/refactoring/rename/ |
| Rename Java | `RenameJavaMethodProcessor`/`Class`/`Variable`/`Member`/`Module`, `JavaNameSuggestionProvider`, `*UsageInfo` de collision | java/java-impl-refactorings/src/com/intellij/refactoring/rename/ (53 fichiers) |
| Find usages | `FindUsagesProvider`, `LanguageFindUsages` | platform/indexing-api/src/com/intellij/lang/findUsages/ |
| Query | `ExtensibleQueryFactory`, `QueryFactory`, `QueryExecutorBase`, `SearchSession` | platform/core-api/src/com/intellij/psi/search/searches/, core-api/src/com/intellij/openapi/application/, indexing-api/src/com/intellij/psi/search/SearchSession.java |
| Searchers Java | `MethodUsagesSearcher` (+21 autres executors) | java/java-indexing-impl/src/com/intellij/psi/impl/search/ |
| Vue usages | `Usage`, `UsageInfo`, `UsageView`, `UsageViewPresentation` | platform/usageView/src/com/intellij/usages/ |
| Écriture | `WriteCommandAction`, `CommandProcessor`, `CoreCommandProcessor` | platform/core-api/src/com/intellij/openapi/command/, core-impl/src/com/intellij/openapi/command/impl/ |
| Undo | `UndoManager` (API) | platform/analysis-api/src/com/intellij/openapi/command/undo/UndoManager.java |

## D10.3 Dépendances plateforme et détachabilité

Retirer Project/UI/Editor/Undo de `BaseRefactoringProcessor` : il reste un **ordonnanceur séquentiel** (findUsages → preprocess → execute) dont la valeur est le **protocole de phases**, pas le code. Les dépendances identifiées : EDT (`@RequiresEdt` :274/:288), progression modale (:323), dumb-mode (:335), `ConflictsDialog`/UsageView (UI), `CommandProcessor`/Undo (:453-456), `PsiDocumentManager.commitAllDocuments` (:290), `SuggestedRefactoringProvider` (:457). **Le calcul utile est dans les trois hooks abstraits** : `findUsages` (index+résolution), `preprocessUsages` (conflits — `MultiMap<PsiElement, String>` : données pures), `performRefactoring` (mutations PSI).

Le trio récent **Core/Base/Headless** (RenamePsiElementProcessorCore.java:22-29) + `HeadlessRenameJavaMethodProcessor` (RenameJavaMethodProcessor.java:474-486) montre que **JetBrains lui-même extrait la voie sans utilisateur** — exactement la voie LSP. `findReferences`/`findCollisions`/`renameElement` sont annotés `@RequiresReadLock`/`@RequiresWriteLock` : sémantique de verrous substituable (D11 : verrous par périmètre).

## D10.4 APIs sensibles ART (comptages `rg -l <pattern> <dir> | wc -l`)

| Périmètre | fichiers | swing | awt | Unsafe | MethodHandles | VarHandle | ServiceLoader | mmap | ThreadLocal | management |
|---|---|---|---|---|---|---|---|---|---|---|
| platform/refactoring/src | 81 | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| java/java-impl-refactorings/src | 577 | 66 | 54 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| platform/usageView/src | 89 | 13 | 4 | 0 | 0 | 0 | 0 | 0 | 1 | 0 |
| platform/usageView-impl/src | 77 | 22 | 12 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| platform/indexing-api/…/findUsages | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| platform/indexing-impl/…/psi/impl/search | 16 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| java/java-indexing-impl/src | (22 executors) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

**Les couches de calcul (searchers, query, findUsages API, WriteCommandAction, CoreCommandProcessor) sont à 0 API sensible** ; les swing/awt se concentrent dans usageView (présentation) et les dialogues de java-impl-refactorings.

## D10.5 Ordres de grandeur et part calcul vs UI

| Bloc | Fichiers | Lignes |
|---|---|---|
| platform/refactoring/src | 81 | 7 090 |
| **java-impl-refactorings/src** | **577** | **99 850** |
| platform/usageView/src | 89 | 5 895 |
| platform/usageView-impl/src | 77 | 9 686 |

Thèmes java-impl-refactorings (`rg --files src/com/intellij/refactoring/<theme> | wc -l`) : extractMethod 58, **rename 53**, **move 53**, typeMigration 27, inline 25, introduceParameter 24, extractclass 23, safeDelete 19, changeSignature 17, introduceField 18, introduceVariable 15, makeStatic 15, migration 14, memberPullUp 6, encapsulateFields 6, chainCall 5, memberPushDown 4, extractSuperclass 8, extractInterface 4, anonymousToInner 4, copy 2, invertBoolean 1 (+ actions/listeners/openapi/psi/util).

Part UI/editor (java-impl-refactorings, 577 fichiers) : **71 fichiers (12,3 %)** importent javax.swing ou java.awt ; **94 fichiers (16,3 %)** référencent `openapi.editor.Editor` ; 21 fichiers utilisent `WriteCommandAction` ; 16 EDT ; 15 DumbService ; 35 ReadAction. Dans platform/refactoring : 2 fichiers swing seulement (mais `RenameRefactoringDialog`, `ConflictsDialogBase` vivent du GUI hors du périmètre listé). usageView : 27 % de swing côté impl — c'est la couche présentation.

## D10.6 Comparaison à armes égales avec le statu quo ecj

Fonctionnement actuel (vérifié) :
- **Rename** : `EcjRenameEngine` (973 l.) — identification par **clé de binding** (classe déclarante + nom + signature, :43-49) : homonymes exclus, usages qualifiés/imports/constructeurs attrapés ; cibles type/méthode/champ/locale/paramètre ; locales/paramètres file-local (balayage esquivé :60-63) ; **les redéfinitions ne sont PAS poursuivies** (:57-59) ; multi-fichiers = `Files.walk` sur les racines sources + pré-filtre textuel + résolution par fichier (renameProject :226-280), overlay des tampons ouverts, CancelToken 15 s + résultat partiel. **Aucune détection de conflit** (`rg "conflict" EcjRenameEngine.java` : 0) ; aucune réparation de références masquées ; pas de preview (occurrences → WorkspaceEdit). `RenameHonneteTest` (298 l.) verrouille : placeholder exact, plage exacte, erreur typée `RequestFailed` sans cible, formes changes/documentChanges (LSP_JAVA.md §18.1 I8).
- **Navigation/usages** : `NavigationService` (201 l.) → `EcjNavigationEngine` (1 533 l.) : définition intra-fichier par binding ; multi-fichiers = **balayage borné (cap fichiers + budget wall-clock, résultat partiel)** + overlay (EcjNavigationEngine.java:64-76, :98-123) ; `textDocument/references` = **mêmes occurrences que le rename** (`renameProject`) — JavaTextDocumentService.java:1596-1634 ; `documentHighlight` = occurrences du fichier courant (:1560-1592).
- Écriture : le serveur ne modifie jamais les fichiers — il rend des `WorkspaceEdit` versionnés au client (I8).

| Axe | IntelliJ | ecj (statu quo) |
|---|---|---|
| Usages | index inversé (searchWord → candidats → résolution), QueryExecutor composables | balayage Files.walk + pré-filtre textuel + re-résolution complète des fichiers candidats |
| Conflits rename | 4 familles typées + prototype re-résolu (RenameJavaMethodProcessor.java:198-207, :209-252) | aucun |
| Overrides | renommés avec suggestion de nom (:292-328) | volontairement non renommés (:57-59) |
| Réparations | qualification de références masquées, imports statiques, @Override (:143-156) | aucune |
| Transaction/undo | CommandProcessor + UndoManager global (CTRL+Z unique) | LSP WorkspaceEdit versionné (undo client, à vérifier côté éditeur cel) |
| Threading | EDT + read/write locks + progression modale | lane scheduler + CancelToken coopératif (adapté mobile) |

## D10.7 Verdict D10

**S'INSPIRER** (fortement) + **réutiliser une brique** :
1. **Phases du rename** : adopter le protocole `substituteElementToRename → prepareRenaming (multi-cibles) → findReferences → findCollisions → findExistingNameConflicts → exécution (renameElement + réparations)` comme cahier des charges du `EcjRenameEngine` v2 : la valeur est la **liste typée de collisions** (MemberHides…/ClassHides…/Unresolvable…) et les réparations post-rename — manquantes du statu quo.
2. **QueryExecutor/SearchSession** : la composition d'executors par EP (ExtensibleQueryFactory.java:18-21) est le modèle pour un « usage search » par-dessus l'index CIDX existant (D7) : remplacer le balayage `Files.walk` par des requêtes indexées, garder le pré-filtre textuel comme fast-path.
3. **HeadlessRenamePsiElementProcessor** : la preuve JetBrains que la voie « sans utilisateur » est extractible — même frontière que LSP (RenamePsiElementProcessorCore.java:22-29).
4. **ModCommand pour les refactorings-light** : `ModStartRename`, `ModShowConflicts`, `ModUpdateReferences` sont des commandes sérialisables directement projetables sur LSP (`textDocument/rename` + `workspace/applyEdit` + fenêtre de conflits côté client).
5. **Transaction** : conserver le modèle LSP « le serveur calcule, le client applique » (déjà le statu quo) ; l'undo groupé CommandProcessor n'a pas d'équivalent nécessaire côté serveur.

**NE PAS embarquer** : `BaseRefactoringProcessor` (auto-déprécié, EDT+UI+undo :95-96, :164-167), usageView/usageView-impl (présentation), `ConflictsDialogBase`, `RenameRefactoringDialog`, inplace rename (éditeur). Embarquer tout ou partie des 577 fichiers java-impl-refactorings n'a de sens qu'avec le socle PSI complet (D1-D7) — coûts du côté IntelliJ : ~107 k lignes de refactoring + l'infrastructure usageQuery ; du côté statu quo : ~2 700 lignes (EcjRenameEngine + EcjNavigationEngine + services) mais des trous fonctionnels mesurés ci-dessus.

## D10.8 Réponse à la question à trancher

**Quelle part dépend de la plateforme IDE ?** — Trois parts bien distinctes :
1. **Le calcul** (recherche d'usages par index, résolution, détection de conflits, mutations PSI) est moteur — détachable, 0 API sensible ART, et désormais officiellement « headless-able » (HeadlessRenamePsiElementProcessor).
2. **L'interaction** (dialogues de renommage, preview UsageView en toolwindow, conflits modaux, inplace rename lié à l'éditeur, undo groupé CTRL+Z) est plateforme — 71/577 et 94/577 fichiers des refactorings Java, 27 % de swing dans usageView-impl ; sous LSP/Android, tout cela se rejoue côté client (dialogues Android, applyEdit, undo éditeur).
3. **L'ordonnancement** (EDT, progression modale, commit Document→PSI, dumb-mode) est plateforme mais **remplaçable** — le statu quo ecj a déjà son équivalent mobile (scheduler 3 lanes + CancelToken + overlay tampons).

Conclusion : pour D10 comme pour D9, la plateforme n'apporte pas du calcul irremplaçable au moteur ecj — elle apporte un **protocole de phases** (usage search → conflits typés → exécution + réparations) et une **qualité d'usage search** (index vs balayage) qui manquent au statu quo.

---

# Conclusion commune D9/D10 (pour le planificateur)

1. **Le point commun des deux domaines** : les couches de calcul sont PSI/résolution/index et quasi exemptes d'API sensibles ART (0 Unsafe/ServiceLoader/mmap dans tous les périmètres comptés) ; les couches UI/transaction sont concentrées et clairement séparables (daemon/highlighting pour D9, dialogues/usageView/EDT pour D10).
2. **Deux découvertes structurantes pour CodeIDE** :
   - **ModCommand** (D9) : protocole JetBrains 2024-2026 de quick fixes **déclaratifs sérialisables** (records, « sent over the network or to a different process », ModCommand.java:42-43) — la grille de conception idéale pour un `CodeActionService` v2 (plan calculé en read action, sérialisable, preview-able), y compris pour le rename (ModStartRename/ModShowConflicts).
   - **HeadlessRenamePsiElementProcessor / RenamePsiElementProcessorCore** (D10) : JetBrains extrait lui-même le noyau « sans utilisateur » du rename — valider la même frontière côté LSP.
3. **Fidélité compilateur** : les diagnostics rouges d'IntelliJ ne viennent PAS d'un compilateur mais de java/codeserver (49 f./18 k l., hors sparse) — réimplémentation maison. Sur le critère §4 « fidélité des diagnostics vs compilateur », le moteur ecj reste l'atout du statu quo ; les inspections IntelliJ sont un complément lint, pas un substitut.
4. **Verdicts** : D9 = S'INSPIRER (protocole ModCommand + contrat visitor/holder/descriptor + dumb-aware ; ne pas embarquer pass/profils/codeserver) ; D10 = S'INSPIRER (phases + conflits typés + réparations + QueryExecutor sur CIDX ; réutiliser la brique « recherche d'usages indexée » en remplacement du balayage ; ne pas embarquer BaseRefactoringProcessor/usageView).
5. **Écarts signalés** : `LocalInspectionsPass` et `java/codeserver` [hors sparse] (lus via `git cat-file`) ; `AtomicActionNonBlocking` introuvable à ce commit (remplacé par NonBlockingReadAction/WriteIntentReadAction, cf. D11) ; java-impl-inspections = 332 fichiers conformes au prompt mais les grandes familles « unused/JUnit/naming » vivent dans java-analysis-impl/java-impl (JUnit : `com.siyeh.ig.junit` dans java-analysis-impl, intentions `ipp/junit` dans java-impl) ; provenance InspectionGadgets (en-têtes Dave Griffith/Bas Leijdekkers) à consigner si reprise éventuelle (R6/R9).

## Mesuré / Estimé / Non vérifié

**Mesuré** : tous les comptages fichiers/lignes/API-sensibles ci-dessus (commandes `rg --files | wc -l`, `xargs wc -l`, `rg -l <pattern> | wc -l` sur le clone @ 865067b0) ; structure des chaînes d'appel par lecture directe (citations fichier:ligne) ; taille du module java/codeserver par boucle `git cat-file -p | wc -l`.

**Estimé** : aucune estimation chiffrée nécessaire au présent rapport (pas de mesure de latence/memory dans le périmètre D9/D10).

**Non vérifié / à faire sur appareil ou en atelier** : fidélité diagnostics codeserver vs ecj sur corpus (banc à créer, §4) ; coût réel d'exécution d'un jeu d'inspections PSI par-dessus la pile headless (dépend de la décision D1-D7) ; comportement du renommage multi-fichiers sur de gros projets Android avec index CIDX (banc rename/references) ; consommation réelle du client cel-lsp pour undo d'un WorkspaceEdit multi-fichiers (matrice : consommation client partielle).

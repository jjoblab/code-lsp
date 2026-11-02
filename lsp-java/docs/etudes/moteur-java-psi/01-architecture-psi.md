# 01 — Architecture PSI : rapport d'étude (domaines D1–D12)

> Étude du moteur Java de niveau IDE pour CodeIDE sur Android/ART, fondée sur l'architecture IntelliJ (PSI).
> Branche `etude/moteur-java` du dépôt `jjoblab/code-lsp`. Document d'étude ; aucune implémentation (§0 du prompt de mission).

## Cadre et méthode

- **Terrain principal** : `JetBrains/intellij-community` @ commit `865067b0a7f1a73ee0fa8bce4f042013c8ac334f` (clone sparse `--filter=blob:none` ; modules extraits : `platform/{core-api, core-impl, indexing-api, indexing-impl, lang-api, lang-impl/backend, analysis-api, analysis-impl, completion, refactoring, usageView, usageView-impl, util, progress, syntax}` et `java/{java-syntax, java-psi-api, java-psi-impl, java-indexing-serializers, java-analysis-api, java-analysis-impl, java-indexing-impl, java-impl, java-impl-inspections, java-impl-refactorings}`). Fichiers absents du working tree mais présents dans l'arbre git : lus via `git cat-file -p HEAD:<chemin>`, signalés « [hors sparse] ».
- **Licence du terrain** : `LICENSE.txt` du dépôt = « JetBrains Open-Source Build Terms v1.3 » (15/06/2026), sources Apache-2.0. Aucun code copié dans ce dépôt (règle R6.4 du prompt de mission).
- **Terrain de comparaison (statu quo)** : `jjoblab/codeide-lsp` @ `cb809e1412fec429c9672da087244b3bd3176545` (v1.16.0), `jjoblab/codeide-tooling` @ `b60f7f0`, `jjoblab/CodeIDE` @ `54e99c6` (v0.202.0), `jjoblab/code-editor` @ `066b8c0` — tous en lecture seule.
- **Méthode** : lecture directe du code, citations `fichier:ligne` vérifiables au commit indiqué ; comptages réels (`rg --files | wc -l`, `xargs wc -l`, `rg -l <pattern> | wc -l`) avec la commande indiquée ; ce qui n'a pas pu être vérifié est étiqueté « à vérifier ». Aucune mesure sur appareil n'est prétendue.
- **Détail des études D7–D12** : les rapports bruts des agents d'étude (avec l'intégralité des citations) sont annexés dans `annexes/exp-D7D8.md`, `annexes/exp-D9D10.md`, `annexes/exp-D11D12.md`. Le présent document condense et synthétise D1–D12.

## Synthèse des verdicts

| # | Domaine | Question (§5 du prompt) | Verdict |
|---|---|---|---|
| D1 | Environnement | Minimum pour parser/résoudre, et son coût | **Réutiliser (partiellement)** : pile Core headless ; shims awt/swing nécessaires |
| D2 | Fichiers virtuels | Peut-on s'en passer / l'émuler | **Émuler** : LightVirtualFile/MockVirtualFileSystem suffisent au noyau |
| D3 | Parsing | Niveau d'incrémentalité atteignable et utile | **Réutiliser** java-syntax + **s'inspirer** du reparse incrémental |
| D4 | Arbre PSI et stubs | Représentation mémoire, empreinte | **S'inspirer** (stubs plat ushort, interning) |
| D5 | Résolution, types, inférence | Source de vérité des types, fidélité | **S'inspirer** (invalidation trop grossière à copier ; inférence réutilisable en voie embarquée) |
| D6 | Bytecode (Cls, stubs .class) | Comparaison avec AnalyseurClassFile/:lsp:index | **Réutiliser le design du pipeline ASM→stub**, miroir décompilé superflu |
| D7 | Index (stubs, file-based, préfixe) | Comparaison avec l'index par segments (ADR 0111) | **S'inspirer** — conserver CIDX ; reprendre 4 designs (détail D7) |
| D8 | Complétion, classement | Mécanismes de classement transposables | **S'inspirer fortement** — le plus transposable : weighers + frontière RPC |
| D9 | Inspections, quick fixes | Part dépendant de la plateforme IDE | **S'inspirer** — ModCommand = modèle direct du CodeAction LSP |
| D10 | Refactorings, usages | Idem | **S'inspirer fortement** — phases du rename + usages indexés sur CIDX |
| D11 | Concurrence, annulation | Remplacer le verrou global actuel | **S'inspirer** — 3 schémas proposés (B recommandé en premier) |
| D12 | Mémoire, caches, GC | Empreinte et GC sur ART | **S'inspirer** — soft-références + budgets octets + shim onTrimMemory |

Aucun domaine ne débouche sur un « embarquer tel quel » intégral ; aucun ne débouche sur un « ignorer » complet. La stratégie qui se dégage des faits est une **hybride** : statu quo ecj/CIDX consolidé, greffé des protocoles et designs IntelliJ, avec une voie embarquée (V2) évaluée au spike (voir `02-spike-mesures.md`).

---

## D1 — Environnement (application, projet, extensions, services)

**Rôle**. L'amorçage d'un environnement Java headless complet tient en 4 classes : `CoreApplicationEnvironment` → `CoreProjectEnvironment` → `JavaCoreApplicationEnvironment` → `JavaCoreProjectEnvironment` (`java/java-indexing-serializers/src/com/intellij/core/JavaCoreApplicationEnvironment.java`). Il enregistre ~71 services, ~28 points d'extension et ~32 extensions **programmatiquement** — aucun `plugin.xml` n'est requis. `CoreJavaFileManager` résout les classes par parcours VFS (sans index), le classpath se monte par `addJarToClasspath`.

**Mécanique**. Conteneur Pico forké (`com.intellij.util.pico`) ; `MockApplication` auto-instancie les `@Service` finaux ; **pas de `java.util.ServiceLoader`** dans cette voie. Les singletons statiques (`ApplicationManager.ourApplication`, `Extensions.staticRootArea`, holder d'`AppScheduledExecutorService`) imposent **une seule instance d'environnement par JVM**, remplaçable séquentiellement (`setApplication` + restore) — contrainte forte pour un serveur LSP multi-projets dans un même processus Android.

**Sensibilité ART**. Le socle parse/resolve est à 0 mmap, 0 Unsafe, 0 ServiceLoader. Points critiques : EDT/`ThreadingAssertions` (`java.awt.EventQueue`), `SwingUtilities` dans `MockApplication.invokeLater`, `VarHandle` dans `TreeElement`/`CompositeElement` (AST — nécessite API 33+ sinon crash, fallback Unsafe non catché), `MethodHandles` dans `MethodHandleCache` (MessageBus).

**Taille**. Socle ≈ 4 500 fichiers sources (core-api 884, core-impl 694, util 1606, extensions 46, service-container 16, psi-api 394, psi-impl 645, java-syntax 39, frontback-psi-impl 8, syntax KMP 148) → ~5 500–7 000 `.class` + kotlin-stdlib/coroutines/fastutil.

**Verdict** : la pile Core est réutilisable en principe (voie embarquée V2) avec shims awt/swing et minSdk ≥ 33 (ou patch VarHandle) — c'est le principal coût d'intégration à mesurer au spike.

## D2 — Fichiers virtuels / modèle de fichier

**Faits**. Le VFS est émulable sans l'infrastructure desktop : `LightVirtualFile` (core-api/testFramework) + `MockVirtualFileSystem` (platform/testFramework) + `PsiFileFactoryImpl.createFileFromText` parsent du texte **en mémoire sans EP**. `CoreLocalFileSystem` (68 l.) fonctionne sans `ManagingFS` ; `CoreStubTreeLoader` ne persiste rien ; `DocumentImpl` (core-impl, 487 l., 0 awt) existe, mais `DocumentCommitProcessor` y est un Mock no-op → le **commit incrémental Document→PSI est à réimplémenter** (c'est le mécanisme par lequel l'éditeur ne re-parse que la plage modifiée).

**Verdict** : on peut s'en passer et l'émuler simplement pour le noyau ; le commit Document→PSI incrémental est le seul trou fonctionnel, déjà couvert côté statu quo par le re-diagnostic de corps (`EcjFastDiagnose`).

## D3 — Parsing : lexer, PsiBuilder, arbres, parse paresseux, reparse incrémental

**Autonomie du parseur Java**. `java-syntax` est **autonome** : module KMP bâti sur `platform/syntax`, API `SyntaxTreeBuilder`, aucun `PsiBuilder`/`Project` ; `BUILD.bazel` ne déclare que syntax-api/i18n/util/extensions. Il est **publié** sur intellij-dependencies (cache-redirector) : `org.jetbrains:java-syntax 0.3.381`, variante JVM 274 856 octets / 95 classes ; pile syntax complète ≈ 800 Ko (java-syntax-jvm + syntax-api-jvm + lang-syntax-jvm + ij-multiplatform-util-jvm). C'est la voie V1 du spike.

**Parse paresseux**. Les corps de méthodes sont parsés paresseusement : `parseBlockLazy` → `marker.collapse(CODE_BLOCK)` (`java/java-syntax/src/com/intellij/java/syntax/parser/StatementParser.kt:44`, `SyntaxBuilderUtil.kt:92-123`) + chameleon `LazyParseableElement.ensureParsed` (:274-323) avec **réutilisation des tokens** (`reuseCollapsedTokens=true`, `JavaElementType.java:337`). Les stubs sont construits depuis l'arbre léger sans parser les corps (`JavaLightStubBuilder`).

**Reparse incrémental**. `BlockSupportImpl` : calcul de la plage commune préfixe/suffixe (`ChangedPsiRangeUtil`) → remontée au plus profond nœud `IReparseableElementType` (validation par équilibre d'accolades `getErrorsCount`) → reparse du chameleon → diff/graft via `DiffLog` ; repli `makeFullParse` en cas d'échec.

**Sensibilité ART (fichiers touchés)**. `VarHandle` (`LazyParseableElement:39-40`, `CompositeElement:45-47`, `TreeElement:41-44`), `ThreadLocal` (`StubTreeSerializerBase:26`, `ThreadLocalRegistryHolderJvm.kt:8`), `Class.forName` (`ExtensionRegistryHolderJvm.kt:17`, `JvmResourceBundle.kt:25`), mmap (`SerializationManagerImpl:116`), `ReentrantLock`+`checkCanceled` (`LazyParseableElement:45,411-423`).

**Verdict** : **réutiliser `java-syntax`** (parseur) — c'est l'artefact publié et autonome ; **s'inspirer** du reparse incrémental (plage commune + chameleon + validation accolades) et du design stubs ; **ne pas porter** `PsiBuilderImpl`/`BlockSupportImpl`/`SerializationManager` tels quels (couplage Project/PsiFile/Document/DummyHolder/événements PSI).

## D4 — Arbre PSI et stubs

**Faits**. Stubs = arbre plat d'`ushort` (`StubList`, 3 shorts/stub) + sérialisation DFS avec **interning global** (`SerializationManager`/`PersistentStringEnumerator`, rep.names) et `LazyStubList`, indépendants du PSI (`StubBase.getPsi` lazy :84-107). Les stubs des fichiers Java sont construits depuis l'arbre léger sans parse des corps (`JavaLightStubBuilder`). L'empreinte mémoire du PSI complet d'un fichier n'a pas été trouvée dans le sparse (pas de banc) — **à mesurer au spike**.

**Verdict** : **s'inspirer** du design (arbre plat compact, interning, séparation stub/PSI) pour le futur format d'index ; le format CIDX actuel (ADR 0111) partage déjà ces principes (sections triées, décodage paresseux).

## D5 — Résolution, types, inférence, caches, invalidation

**Faits clés**.
- `ResolveCache` (core-impl, 343 l.) : 8 maps (physique×incomplet×poly), clé = objet `PsiReference` **faible**, valeurs molles ; **vidage total** sur `ANY_PSI_CHANGE_TOPIC.beforePsiChanged` + `LowMemoryWatcher` (`java.lang.management` — absent d'ART).
- Invalidation par **compteur global** `PsiModificationTrackerImpl` (sans UI mais `runWriteAction`/`TransactionGuard` obligatoires) ; l'entrée/sortie du dumb mode bump les compteurs → re-résolution après index prêt.
- `graphInference` = 22 fichiers / 5 908 l. (`InferenceSession` 2 114 l.), réimplémentation JLS 18 calée sur javac (clés Registry `javac.*` ; défaut = émuler javac même hors spec).
- `findClass` : `JavaPsiFacadeImpl` → EP `PsiElementFinder` → `JavaFileManagerImpl` (`JavaFullClassNameIndex`) ; variante headless `CoreJavaFileManager` (classpath brut).

**Verdict** : **s'inspirer** — l'invalidation « vidage total au changement global » coûte trop cher sur mobile (le moteur ecj re-résout déjà finement par frappe) ; l'inférence est réutilisable **si** la pile PSI est embarquée (voie V2). La source de vérité des types côté statu quo reste ecj (fidélité compilateur native, cf. D9).

## D6 — Lecture du bytecode (Cls, stubs .class, décompilation), AAR, android.jar

**Faits**. `.class` → stubs via **ASM** (`org.jetbrains.intellij.deps:asm-all:9.6.1`, package ombré `org.jetbrains.org.objectweb.asm`) : `StubBuildingVisitor` 898 l. + `FirstPassData` + `SignatureParsing` ; `SKIP_CODE`/`SKIP_FRAMES`. Les classes internes sont lues dans les fichiers frères `Outer$Inner.class` (hack documenté `ClassFileStubBuilder:58-69`). Paquet `compiled/` = 56 fichiers / 9 694 l. La décompilation est un rendu texte approximatif (bannière « Implementation of methods is not available ») + miroir PSI re-parsé — usage navigation/quick-doc, pas fidélité ; pas de `DecompilerResult` ni paquet `decompiler/` à ce commit. **Pas de support AAR/android.jar** dans ces modules (plugin Android hors périmètre ; seul `ConcurrencyAnnotationsManager` connaît androidx.annotation). 6 fichiers du paquet `compiled/` importent `javax.swing` (Icon).

**Verdict** : **réutiliser le design du pipeline ASM→stub** (le plus portable — le statu quo a déjà `ClassFileStubBuilder.java` et `RawZipReader` côté lsp/java, et l'index CIDX par segments) ; le miroir décompilé-source est superflu sur mobile (la quick-doc par signature suffit, cf. matrice de capacités).

---

## D7 — Index (stub index, file-based indexes, persistance, requêtes par préfixe)

> Rapport brut complet : `annexes/exp-D7D8.md` (§ D7).

**Architecture**. Index inversé multi-indices : chaque `FileBasedIndexExtension<K,V>` (`platform/indexing-api/src/com/intellij/util/indexing/FileBasedIndexExtension.java`) déclare ID + `DataIndexer` + `KeyDescriptor` + `DataExternalizer`. Les **stub indexes** sont enrobés en `FileBasedIndexExtension<K,Void>` (`platform/indexing-impl/src/com/intellij/psi/stubs/StubIndexEx.java:473-525`) car les données réelles (stub trees sérialisés) vivent dans un index séparé, le `StubUpdatingIndex` (clé = fileId, valeur = `SerializedStubTree`), lu seulement à la matérialisation PSI.

**Deux piles de stockage**, toutes deux problématiques pour ART :
1. Pile par défaut : `VfsAwareMapIndexStorage` → `MapIndexStorage` → **`PersistentHashMap`** → `PagedFileStorage` (`DefaultIndexStorageLayoutProvider.kt:197-209`) + WAL actif par défaut. Elle « émule » le mmap avec des `ByteBuffer.allocateDirect` re-lus par `FileChannel.read` (`DirectBufferWrapper.java:16-17,262-276`), pool ≤ 100 Mo, page cache total **600 Mo par défaut** (`PageCacheUtils.java:44-49`) — dimensionné desktop ; et la libération des buffers directs passe par `Class.forName("sun.nio.ch.DirectBuffer")` + `cleaner().clean()` via **MethodHandles** (`ByteBufferUtil.java:23-56`) — API JDK absente d'ART.
2. Pile « durable » opt-in : `DurableMapIndexStorage`/`DurableDatabaseIndexLayoutProvider` — repose sur `java.lang.foreign` (Arena/MemorySegment, **38 fichiers**, `MMappedFileStorage.java:18-19,108,504`) et `VarHandle` (21 fichiers) — JDK 19+, incompatible ART en l'état.

**Requêtes**. Chaîne de lecture : `FileBasedIndex.getValues` → `FileBasedIndexEx.getValues` (:191-232) → `ensureUpToDate` (`FileBasedIndexImpl.java:951-1058` : read action obligatoire, dumb mode → `IndexNotReadyException` :1070-1073, forceUpdate VFS, indexation des documents non sauvés). StubIndex : `StubIndexEx.processElements` (:158-277) → `getContainingIds` → désérialisation des stub trees ; auto-réparation `tryFixIndexesForProblemFiles` (:285-326) via `invokeLater`/`WriteAction` — **dépendance EDT**. Dumb mode à deux niveaux : `DumbModeAccessType` (`RELIABLE_DATA_ONLY` / `RAW_INDEX_DATA_ACCEPTABLE`, `platform/indexing-api/src/com/intellij/util/indexing/DumbModeAccessType.java:18-61`) — mais interdit pour StubIndex (`StubIndexEx.java:186-189`).

**Fait critique — il n'existe PAS de requête par préfixe au niveau du stockage**. La complétion de noms de classes énumère **toutes les clés** de l'index des noms courts puis filtre en mémoire : `AllClassesGetter.processJavaClasses` (`java/java-impl/src/com/intellij/codeInsight/completion/AllClassesGetter.java:193-204`) → `PsiShortNamesCache.processAllClassNames` (`PsiShortNamesCacheImpl.java:117-123`) → `StubIndex.processAllKeys(CLASS_SHORT_NAMES)` + filtrage `prefixMatcher.prefixMatches`. `JavaShortClassNameIndex` est à clé exacte (`StringStubIndexExtension`), `JavaFullClassNameIndex` à hash de FQN (requête exacte). L'énumération est le prix du camel-hump universel sans structure de préfixe ; elle est amortie sur desktop par les caches SLRU — coûteuse a priori sur ART (le jeu lourd équivalent fait ~23 300 noms simples, `docs/LSP_JAVA.md` §12).

**Écriture**. Événements VFS → `IndexUpdateRunner` → `FileBasedIndexImpl.indexFileContent` → `MapReduceIndex.mapInputAndPrepareUpdate` (`MapReduceIndex.java:289-303`) : le **forward index** (fileId → données indexées sérialisées, `PersistentMapBasedForwardIndex`) fournit l'ancienne carte de clés → diff ADDED/UPDATED/REMOVED → updates différentielles. Versions : `IndexVersion` (BASE 15) par index ; corruption d'E/S → `requestRebuild` = **rebuild de l'index entier** (`MapReduceIndex.java:505-521`) ; `CorruptionMarker` global → tout rebuild.

**Tailles (comptées)**. Périmètre D7 ≈ **823 fichiers / ~118 600 lignes** (indexing-api 104/7 632 ; indexing-impl 172/20 704 ; lang-impl/backend-indexing 118/20 414 ; util/io 168/29 927 ; util/indexing 64/6 317 ; util/storages 118/24 036 ; java-indexing-impl 73/8 008 ; + stubs). Cœur minimal « index inversé persistant + requêtes » ≈ 12 fichiers / ≈ 9 000 lignes, mais grevé des dépendances plateforme (Project, VFS-fileId, EDT/MessageBus, LowMemoryWatcher via `java.lang.management`).

**Comparaison factuelle avec CIDX (ADR 0111)** — tableau complet en annexe D7.6 :
- Préfixe : **CIDX gagne** (recherche binaire par segment trié, `VueIndex.kt:137-176`, p50/p95 = 0,16/1,1 ms petit → 0,76/7,1 ms lourd) vs énumération complète des clés côté IntelliJ.
- +1 dépendance : CIDX indexe **seul le jar nouveau** (85/89/87 ms pour 14/83/250 entrées) ; IntelliJ indexe les nouveaux fichiers (index global par fileId) mais n'a pas de mesure publiée ici.
- Mémoire : CIDX budget **octets** mesuré (4,2/2,9/29,7 Mo ; cible < 30 Mo/100 jars tenue) vs page cache 600 Mo + SLRU 1 024 entrées/index.
- Corruption : CIDX refuse le **seul segment** et le reconstruit paresseusement ; IntelliJ rebuild l'index entier.
- Ce que l'index IntelliJ a en plus : invalidation **par fichier source** (IndexingStamp), **forward index** (updates différentielles — réponse au point faible W4 du statu quo), **généricité multi-index** (19 index Java sur un même moteur), remontée PSI complète.

**Verdict D7 : S'INSPIRER — ne pas embarquer.** Conserver l'architecture CIDX comme socle ; reprendre quatre designs : (a) ValueContainer clé→(fileId→valeur) comme forme canonique multi-usage ; (b) forward index / snapshot de clés par unité pour des updates différentielles des **sources** ; (c) l'idiome IndexingStamp (unité × index × version) ; (d) dumb mode à deux niveaux. Aucune des deux piles de persistance n'est compatible ART sans réécriture profonde (cleaner `sun.nio.ch.DirectBuffer` d'un côté, `java.lang.foreign` de l'autre) ; le modèle fileId-VFS verrouille sur le VFS persistant complet.

## D8 — Complétion : contributors, contexte, classement

> Rapport brut complet : `annexes/exp-D7D8.md` (§ D8).

**Flux**. (1) Initialisation : `CompletionInitializationUtil.insertDummyIdentifier` (`platform/analysis-impl/src/com/intellij/codeInsight/completion/CompletionInitializationUtil.java:123-149`) insère un identifiant factice **dans une copie du fichier** — même invention que le marqueur `__codeide_completion__` du moteur ecj. (2) Collecte : `CompletionService.performCompletion` → boucle sur les contributors EP (`CompletionService.java:60-84`, `IndexNotReadyException` **attrapée et ignorée** :78-79 — repli dumb natif) ; contributeur Java principal `JavaCompletionContributor` (`java/java-impl/src/com/intellij/codeInsight/completion/JavaCompletionContributor.java:486`, 1 380 l.). (3) Filtrage par préfixe à chaque ajout (`BaseCompletionService.java:114-128`). (4) **Classement** : `defaultSorter` (:204-238) compose une chaîne de classifiers — `PreferStartMatching`, weighers EP (`RealPrefixMatchingWeigher`, `StatisticsWeigher`, `PriorityWeigher`, `GroupingWeigher`), `LiftShorterItemsClassifier`, cache des poids (`CompletionSorterImpl.java:112-124`, `CachingComparingClassifier`). (5) **Arrangement** : `BaseCompletionLookupArranger` (~734 l.) — `arrangeItems` sans Lookup réel (:337-380), **gel des items affichés** (`myFrozenItems` :71, freezeTopItems :522-531 — pas de « saut » de liste), `trimToHalfLimit` avec publicité « not all variants are shown » (:264-302), batchUpdate.

**Mécanique de pesée**. `Weigher<T,Location>` + `WeighingComparable` (`platform/analysis-api/src/com/intellij/psi/Weigher.java:17-29`, `WeighingComparable.java:12-103`) : comparaison **paresseuse** poids par poids, premiers poids décisifs, cache — extensible par EP sans toucher au tri. `RealPrefixMatchingWeigher` = max du matchingDegree camel-hump gradué — équivalent exact des grades EXACT/PREFIX/PREFIX_CI/HUMP/SUBSTRING de `EcjPrefixMatcher` (`lsp/java/.../EcjPrefixMatcher.java:34-36`).

**Découverte clé — le split RPC frontend/backend** (`platform/completion/{common,frontend,backend}`) : 57 fichiers / 2 340 lignes de **protocole sérialisable** (kotlinx.serialization) qui spécifie la frontière calcul/présentation : `RpcCompletionRequest`, `RpcCompletionItem` (présentation calculée **backend**), `RpcCompletionItemWeight` (:12-19 — « the lower the weight, the higher the priority » : le classement est calculé backend et transporté comme **un entier**), `RpcPrefixMatcher` **sérialisé Frontend** (re-filtrage local par frappe, sans RPC) ou référencé Backend (trop complexe), `RpcLookupElementEvent`, modCommands d'insertion. `FrontendCompletionContributor` est déprécié (« put your completion contributors in backend modules », `FrontendCompletionContributor.kt:14-19`) ; l'API `com.intellij.modcompletion` fournit un modèle d'item sans LookupElement. **C'est exactement la frontière qu'impose un LSP sur Android** — et c'est la réponse structurelle au facteur « ART 3–5× plus lent » : re-filtrer côté client à chaque frappe sans relancer le calcul serveur.

**Sensibilité ART**. Le **chemin de classement pur** (sorter + arranger + 4 weighers plateforme + `WeighingComparable` + `CamelHumpMatcher`) contient **0 référence swing/awt** (vérifié `rg -l "javax\.swing\."` sur ces fichiers : 0). Le swing (51 fichiers du périmètre D8) est concentré dans `Icon`/présentations et la Lookup UI. `StatisticsWeigher` dépend de la pile `platform/statistics` **absente du sparse** (signalé) — à réécrire sur un simple compteur d'usage local.

**Tailles (comptées)**. Périmètre D8 ≈ 316 fichiers / ~33 550 lignes (analysis-api completion+lookup 70/5 726 ; analysis-impl 64/6 082 ; completion/common 57/2 340 ; java-impl/completion 125/19 403). **Noyau classement ≈ 10 fichiers / ~1 500 lignes**, sans dépendance UI.

**Statu quo (ecj)**. `EcjCompletionEngine` (3 491 l.) : 5 variantes de splice autour du marqueur, `kindRank`, break au premier résultat non vide de rank 2, dernier recours déclaration synthétique puis repli lexical. Classement actuel = **un seul score scalaire** `rankScore` (:3228-3258) : base 1000 − bonus de grade − `BONUS_ASSIGNABLE` + proximité (LOCAL 0 … OBJECT_MEMBER 180 … KEYWORD 350) − pénalités ; tri pertinence → nom le plus court → alphabétique. Écarts structurels vs IntelliJ : pas de statistiques d'usage, pas de gel, pas de liftShorter, pas de groupes, pas de trim honnête, **recalcul complet à chaque frappe**, un seul axe de tri.

**Verdict D8 : S'INSPIRER fortement — c'est le domaine le plus transposable de toute la pile.** Réécrire en Kotlin (R2 : code neuf en Kotlin) la chaîne « classifiers/weighers ordonnés + WeighingComparable paresseux » par-dessus l'API `CompletionItem` actuelle ; y brancher grades + proximité existants + liftShorter + frozen-items + trimToHalfLimit ; adopter la frontière RPC (matcher + poids + présentation envoyés au client pour re-filtrage local) ; conserver le pipeline ecj (splice/marqueur = même invention que DUMMY_IDENTIFIER, testé).

## D9 — Inspections et quick fixes

> Rapport brut complet : `annexes/exp-D9D10.md` (§ D9).

**Flux**. Une inspection locale est un visitor PSI : `LocalInspectionTool.buildVisitor(ProblemsHolder, isOnTheFly, session)` → `PsiElementVisitor` (`platform/analysis-api/src/com/intellij/codeInsight/LocalInspectionTool.java:138-140`) — contrat **pur PSI, 0 UI**. Exécution batch/headless : `InspectionEngine.inspectElements` (`platform/analysis-impl/src/com/intellij/codeInsight/InspectionEngine.java:328-432` ; garde « obligatoirement sous ProgressManager.runProcess » :435-438 ; parallélisme `JobLauncher`). Le résultat est un `ProblemDescriptor` (+ `LocalQuickFix`) déposé dans `ProblemsHolder`. L'exécution on-the-fly (`LocalInspectionsPass` [hors sparse], 587 l.) convertit les descripteurs en `HighlightInfo` (rendu éditeur) et filtre le dumb mode (`DumbToolWrapperCondition` :560-576) — pure couche de présentation.

**Découverte clé — ModCommand** (`platform/analysis-api/src/com/intellij/modcommand/`, 37 fichiers / 2 975 l.) : interface **scellée de records** (`ModUpdateFileText`, `ModNavigate`, `ModStartRename`, `ModShowConflicts`, `ModChooseAction`, `ModCreateFile`, `ModMoveFile`…) dont la javadoc assume : *« every ModCommand can be easily serialized and sent e.g., over the network, or to a different process »* (`ModCommand.java:42-43`). `ModCommandAction` est le remplacement déclaré de `IntentionAction` : le fix est **calculé en read action** comme un plan déclaratif (texte ancien/nouveau par fichier), prévisualisable sur copie non physique, sérialisable — l'exécution est déléguée à un service Editor-centrique (`ModCommandServiceImpl.java:129-159`, à réécrire). **C'est exactement le modèle d'un CodeAction LSP** (`WorkspaceEdit`) : la réponse de JetBrains au même problème que CodeIDE, et la grille de conception directe pour un `CodeActionService` v2 (919 lignes d'édits impératifs ad hoc aujourd'hui côté statu quo).

**Diagnostics rouges ≠ compilateur**. `HighlightVisitorImpl` (`java/java-analysis-impl/.../analysis/HighlightVisitorImpl.java:62-68`) délègue tout le calcul à `JavaErrorCollector` du module **java/codeserver** [hors sparse] : 49 fichiers / 18 130 lignes de checkers **réimplémentés sur PSI/résolution** (`JavaErrorKinds` 1 912 l., `ExpressionChecker` 1 759 l., `GenericsChecker` 1 105 l.) — aucun ecj. Les erreurs « compilateur » d'IntelliJ sont une réimplémentation maison : sur le critère §4 « fidélité des diagnostics vs compilateur », **ecj reste l'atout du statu quo** (DiagnosticService → bindings ecj natifs, correctifs de fidélité §17 de `LSP_JAVA.md`).

**Inventaire**. ~660 inspections Java au total : `java-impl-inspections` 332 f / 78 317 l. (28+37 sous-paquets, 124 `BaseInspection`, 147 utilisent déjà ModCommand, **1 seul fichier swing**) + `java-analysis-impl/codeInspection` 328 f / 68 011 l. Échantillonnage : la plupart consomment PSI + résolution ; celles qui touchent l'index (`UnusedSymbolLocalInspection`) passent par `PsiSearchHelper.isCheapEnoughToSearch` ; `UnusedDeclarationInspectionBase` (globale, 690 l.) exige le graphe Ref complet — plateforme lourde. Comptages ART : 0 Unsafe/ServiceLoader/mmap ; swing concentré dans `Icon` de présentation.

**Réponse à la question à trancher** : la part plateforme est **majoritaire en code, minoritaire en valeur** — le contrat d'inspection (~1 500 lignes utiles : visitor/holder/descriptor/quickfix) est détachable et 0-UI ; l'exécution on-the-fly (~14 k lignes : pass + HighlightInfo + daemon + EDT) est du pur rendu ; la valeur sémantique des ~660 inspections dépend des services de calcul (résolution D5, index D7), pas de l'UI.

**Verdict D9 : S'INSPIRER** — adopter (a) le protocole ModCommand comme modèle du CodeActionService v2, (b) le contrat visitor/holder/descriptor pour greffer des inspections lint par-dessus le moteur existant, (c) la séparation dumb-aware. Ne pas embarquer : LocalInspectionsPass/HighlightInfo, profils/options UI, ModCommandServiceImpl, UnusedDeclaration (graphe global), et le codeserver comme substitut d'ecj (perdrait la fidélité compilateur).

## D10 — Refactorings et recherche d'usages

> Rapport brut complet : `annexes/exp-D9D10.md` (§ D10).

**Le refactoring « à l'ancienne » est auto-diagnostiqué par JetBrains** : `BaseRefactoringProcessor` (878 l., `platform/refactoring/src/com/intellij/refactoring/BaseRefactoringProcessor.java`) — *« heavy machinery from the early days of IntelliJ […] mixing UI with business logic »* (:95-96), *« not compatible with the ModCommand API »* (:164-167). Flux : `doRun` [@RequiresEdt] → commit Documents → `findUsages` sous read action bloquante + progression **modale** (:323-324) → garde dumb (:335-338) → conflits → **dialogue UI** (:535) → preview UsageView (:387-418) → `execute` = `CommandProcessor.executeCommand` (undo groupé) → `performRefactoring` [@RequiresEdt @RequiresWriteLock]. La valeur est le **protocole de phases**, pas le code.

**Le rename est officiellement « headless-able »** : architecture en couches récente — `RenamePsiElementProcessorCore` (*« holds the part of a rename that asks the user nothing »*, `RenamePsiElementProcessorCore.java:22-29`), complétée par `HeadlessRenamePsiElementProcessor` et `HeadlessRenameJavaMethodProcessor` (*« renames a Java method for a caller that has no user »*, `RenameJavaMethodProcessor.java:474-486`). La spécialisation Java méthode montre ce qui manque au statu quo : `findReferences` par `MethodReferencesSearch` (QueryExecutor sur index, :191-195) ; **renommage des overrides** avec suggestion de nom (:292-328) ; **4 familles de collisions typées** (`SubmemberHidesMemberUsageInfo` via `ClassInheritorsSearch`, member-hides-outer, collision nouveau nom, prototype renommé **re-résolu** :209-252) ; **réparations post-rename** (qualification des références masquées, imports statiques, suppression de `@Override` devenu faux :143-156).

**Usage search** : `ExtensibleQueryFactory` + `QueryExecutorBase` (composition d'executors par EP — la javadoc mentionne textuellement *« the language server root area »*, `ExtensibleQueryFactory.java:18-21`) ; exécuteur type `MethodUsagesSearcher` (`java/java-indexing-impl/src/com/intellij/psi/impl/search/MethodUsagesSearcher.java:24-104`) : `SearchRequestCollector.searchWord` (index de mots) + résolution des candidats, le tout sous `DumbService.runReadActionInSmartMode` ; 22 QueryExecutors dans java-indexing-impl. `usageView` (166 f / 15 581 l.) = présentation (22 fichiers swing sur 77 côté impl) ; le calcul utile tient dans `UsageInfo` + QueryExecutor. Écriture/undo : `WriteCommandAction` (224 l.) + `CoreCommandProcessor` (442 l.) — sous LSP, le modèle « serveur calcule / client applique » du statu quo reste le bon.

**Comparaison à armes égales**. Statu quo : `EcjRenameEngine` (973 l.) — identification par **clé de binding** (correcte, homonymes exclus) ; mais **0 conflit détecté** (`rg "conflict"` : 0), **overrides volontairement non renommés** (:57-59), usages = `Files.walk` + pré-filtre textuel (:226-280), aucune réparation de références masquées, pas de preview. Navigation/usages : balayage borné (cap fichiers + budget wall-clock, résultat partiel) ; `references` = mêmes occurrences que le rename (`JavaTextDocumentService.java:1596-1634`). Part UI mesurée côté IntelliJ : 71/577 fichiers swing-awt (12,3 %), 94/577 référencent Editor — mais **0 API sensible ART dans toutes les couches de calcul** (searchers, query, WriteCommandAction, CoreCommandProcessor).

**Réponse à la question à trancher** : le calcul (usage search indexé, conflits typés, mutations) est moteur et détachable ; l'interaction (dialogues, preview toolwindow, inplace, undo groupé) est plateforme et se rejoue côté client Android ; l'ordonnancement (EDT, progression modale, dumb) est remplaçable — le scheduler 3 lanes + CancelToken du statu quo est déjà l'équivalent mobile.

**Verdict D10 : S'INSPIRER fortement** — (a) adopter le protocole de phases du rename comme cahier des charges d'`EcjRenameEngine` v2 (liste typée de collisions + réparations) ; (b) architecture QueryExecutor par-dessus **CIDX** en remplacement du balayage `Files.walk` (c'est la seule brique « réutilisation » vraiment payante) ; (c) valider la frontière headless comme JetBrains lui-même ; (d) ModStartRename/ModShowConflicts projetables directement sur LSP. Ne pas embarquer : BaseRefactoringProcessor (auto-déprécié), usageView, ConflictsDialog, inplace rename.

---

## D11 — Concurrence : read/write actions, verrous, annulation

> Rapport brut complet : `annexes/exp-D11D12.md` (§ D11).

**Le modèle IntelliJ a trois couches**, et seule la première nous concerne directement :
1. **API (core-api — détachable)** : le contrat `ThreadingSupport` (412 l., `platform/core-api/src/com/intellij/openapi/application/ThreadingSupport.kt:15`) expose trois niveaux de permit — **lecture**, **write-intent** (intention d'écrire, exclusif mais annulable), **écriture** — plus `runWhenWriteActionIsCompleted`. Façades `ReadAction`/`WriteAction`/`WriteIntentReadAction`, fonctions Kotlin dépréciées vers le bloquant (`actions.kt:26-27`), `TransactionGuard`/`ModalityState` (no-op en headless), `NonBlockingReadAction` (API).
2. **Implémentation IDE (platform-impl + locking-impl [hors sparse] — NON détachable)** : `ApplicationImpl` (1 533 l.) ne contient plus aucun verrou et délègue à `NestedLocksThreadingSupport` (2 113 l., verrou RW **par niveau de parallélisation**, 8 champs ThreadLocal, spin de 100 `Thread.yield()` avec `checkCanceled` :864-877, priorité écrivain `smartAcquireReadPermit` :826-878, upgrade write-intent→write :407-420) ; la mutex vient d'un artefact externe publié (`org.jetbrains.intellij.deps:rwmutex-idea:0.0.10`). **`ReadMostlyRWLock` et `ClassCancellation` n'existent plus à ce commit** (écart vs documentation attendue). `NonBlockingReadActionImpl` (1 141 l. [hors sparse]) est **le mécanisme à imiter** : une lecture coupée par un écrivain est annulée (PCE) puis **re-soumise après lui** (`rescheduleLater` :822-826), coalescence par clé, expiration par `Disposable`.
3. **Couche headless (celle qui tournerait sur ART)** : `MockApplication`/`MockThreadingSupport` — read/write actions = **exécution directe sans verrou**, mono-thread par construction ; `invokeLater` → `SwingUtilities.invokeLater` (dépendance javax.swing). **Embarquer la pile headless (V2) n'apporte aucune primitive de concurrence utile** : tout le verrouillage intelligent vit dans platform-impl/locking-impl.

**Annulation**. Token moderne = Job coroutine : `Cancellation.checkCancelled` (`platform/util/src/com/intellij/openapi/progress/Cancellation.java:63-78`) transforme `java.util.concurrent.CancellationException` en PCE ; contexte ThreadLocal maison interfacé avec le **fork JetBrains de kotlinx.coroutines**. `CoreProgressManager` (1 374 l.) : maps statiques d'indicateurs, ping 10 ms. Dans le parse/résolution, l'annulation remonte par exception PCE aux points `checkCanceled` (D3).

**Statu quo mesuré**. `EcjCompilerEngine` (2 417 l.) : **24 méthodes synchronized + 2 blocs** (17 public — le prompt annonçait ≈20 ; écart affiné) ; pivot `resolveUnit` (:660, :672) protégeant `EcjParseCache` (non thread-safe) et l'environnement de jars partagé. `EcjEngineScheduler` (487 l.) : 3 lanes mono-thread (interactive 3 s, analysis 15 s, diagnostics 15 s) + **supersession par fichier** + watchdogs + `CancelToken` (deadline + interrupt). Latence de frappe mesurée bureau : p50/p95 = 30/64 ms (20 jars) → 128/150 ms (250 jars) ; ART 3–5× plus lent ⇒ p95 appareil estimé ~200–750 ms (estimation, facteur LSP_JAVA.md §4). W8 confirmé : une complétion du module A bloque le module B — atténué par le moteur par module v0.97.0 (carte `ConcurrentHashMap` par module, ordre de verrou this → contexte).

**Trois schémas de remplacement du verrou global** (réponse à la question à trancher) :
- **Schéma A — RW multiple-lecteurs + file unique écrivain** (calque IntelliJ) : complétion/hover/diagnostics = lecteurs simultanés ; invalidation classpath/dispose = écrivains. Exige que tout état partagé devienne thread-safe en lecture (AST ecj ne l'est pas → verrou par arbre ou snapshots immuables). N'apporte de gain que si le multi-fichiers concurrent devient dominant (rename/find-refs) — **à mesurer avant**.
- **Schéma B — verrous par périmètre (fichier/module), écrivain rare (RECOMMANDÉ en premier)** : remplacer le moniteur `this` par des verrous par fichier (ou *striped* ~64 seaux) autour de `resolveUnit`/`EcjParseCache` ; le moteur par module v0.97.0 est l'étage 1 ; conditions : `EcjCompilerEnvironment` sous verrou séparé (déjà amorcé `indexLock` :1484) ou immuable-échangé ; ordre global imposé **fichier → module → index** ; plafonner les workers à 2–3 (ART 3–5×). Gain direct : une diagnose du fichier A ne bloque plus la complétion du fichier B. Coût : ~quelques centaines de lignes.
- **Schéma C — époque/génération (copy-on-write de l'environnement)** : compteur `volatile long epoch` bumpé à chaque changement de classpath ; les lecteurs capturent l'époque au départ, la re-valident à la publication, rejettent par `OperationCanceledException` si périmé (l'équivalent ciblé du `PsiModificationTracker`, sans sa grossièreté) ; l'écrivain construit le nouvel environnement en tâche de fond puis **swap atomique** du champ `volatile index` (`EcjCompilerEngine.java:232` — déjà volatile). B et C sont **cumulables**.

**Sensibilité ART (comptages sur tout le sparse)**. java.awt 1 338 occurrences/447 fichiers, javax.swing 1 567/667 (EDT, `LaterInvocator.java:40-43` : JFrame/Dialog/Window), VarHandle 309/48, MethodHandles 102/34 (`EDT.java:18-19,109-121`), sun.misc.Unsafe 2/2 (conteneurs concurrents), java.lang.management/javax.management 8/8 (`LowMemoryWatcherManager`, `CoreProgressManager`), ThreadLocal dense (util 98). Le cœur Java-PSI est **coroutine-free** (java-psi-api 0/394, java-psi-impl 1/645, java-syntax 0/36) ; la glue plateforme en est tissée.

**Verdict D11 : S'INSPIRER** — le verrouillage complet d'IntelliJ est indissociable de son modèle UI et de ses coroutines ; transférer coûterait plus qu'il ne rapporte. Trois invariants transposables : *lecteurs majoritaires, écrivain rare et prioritaire* ; *annuler-c'est-rejouer* (NBRA) ; *le compteur d'époque remplace le verrou d'invalidation*. Le scheduler maison couvre déjà ~60 % de la sémantique NBRA (supersession, timeout, lanes) ; ce qui manque : granularité de verrou, priorité écrivain + rejeu, époque d'invalidation.

## D12 — Mémoire : références faibles/molles, caches bornés

> Rapport brut complet : `annexes/exp-D11D12.md` (§ D12).

**Le modèle IntelliJ : « la valeur vit sur l'élément, meurt avec lui ou avec la mémoire »**.
- `CachedValuesManagerImpl` (159 l.) : la valeur cachée est posée **dans le UserDataHolder lui-même** (:87, :104-105) ; `PsiCachedValueImpl` variantes Soft/SoftTracked (recommandées) ; `CachedValueBase` : invalidation **par dépendances + horodatages** (:108-133), verrou par valeur, protection anti-récursion.
- `ResolveCache` : 8 maps `ConcurrentWeakKeySoftValueHashMap` (clés faibles, valeurs molles, **2 ReferenceQueue**) — mais **vidage total** sur ANY_PSI_CHANGE : l'exemple **à ne pas** copier (D5).
- `SmartPointerTracker` (561 l.) : la « file morte » — `ReferenceQueue` statique, retrait O(1) des pointeurs morts, purge aussi **sur signal LowMemoryWatcher** ; pointeurs utilisateurs faibles, marqueurs de plage mis à jour aux DocumentEvents et re-validés après reparse.
- `FileTrees` (412 l.) : trois états par fichier (stub seul / AST seul / les deux) ; StubTree en `Reference` soft, AST en `ConcreteAstPointer` (soft pour fichiers physiques) ; **robinet mémoire global** : `PsiManagerImpl.runInBatchFilesMode` bascule toutes les rétentions soft→weak pendant l'indexation batch (:590-603) — ~30 lignes **transposables tel quel** au moteur ecj pour l'indexation des jars.
- Caches d'index : SLRU 1 024 **entrées** par index (`FileBasedIndexExtension.java:56`) + provider Caffeine opt-in — bornes en entrées, à re-qualifier en octets pour ART.
- **Signal mémoire : `LowMemoryWatcher` repose sur javax.management + java.lang.management** (`LowMemoryWatcherManager.java:11-18,156-168`) — **absents d'ART** ; la pile headless ne le démarre pas (non-bloquant pour V2) ; la substitution naturelle est `android.content.ComponentCallbacks2.onTrimMemory` (~100 lignes).

**Densité de l'idiome** : ≈31 usages SoftReference + ≈86 weak dans les 4 modules principaux — structurel chez IntelliJ ; le moteur ecj actuel en a **zéro**.

**Statu quo**. `EcjParseCache` : LRU **8 entrées** en **références fortes** (~800 ko estimés ; borné en entrées, pas en octets ; non thread-safe par conception) ; `EcjLocateCache` : LRU 8 192 entrées (ReentrantLock) ; `CacheSegments` (index CIDX) : **budget octets** explicite (cible < 30 Mo/100 jars tenue : 4,2/2,9/29,7 Mo mesurés) ; `HeapMonitor.kt` (tooling) : `Runtime` seul — compatible ART. Budgets mesurés (bureau) : tas après index/stable = 9/25 Mo (20 jars) → 108/131 Mo (250 jars) ; le budget snapshot 2,5 Mo/jar (W2) est le principal responsable du dépassement.

**Verdict D12 : S'INSPIRER fortement** — (1) passer `EcjParseCache` en valeurs **molles** avec ReferenceQueue + purge paresseuse ; (2) généraliser les **budgets octets** (CacheSegments le fait déjà — l'étendre au parse-cache) ; (3) invalidation **par dépendances** type `CachedValueBase` au lieu du vidage total ; (4) mode batch soft→weak pour l'indexation des jars ; (5) shim `onTrimMemory` en remplacement de LowMemoryWatcher. Comportement précis du GC ART (fréquence d'éviction des SoftReference, coût des ReferenceQueue) : **à vérifier sur appareil**.

---

## Conclusion obligatoire (§5 du prompt) — que reste-t-il si l'on n'embarque que PSI ?

**Question posée** : ce qui fait la qualité perçue dans un IDE complet relève-t-il de PSI lui-même ou de la plateforme autour (index, inspections, UI) ? Quelle partie de « la puissance de PSI » n'est PAS portable en n'embarquant que PSI ?

**Réponse en trois temps, fondée sur les faits D1–D12.**

**1. PSI lui-même est la partie la plus portable — et ce n'est pas là que réside la puissance perçue.** Le parseur Java (`java-syntax`) est autonome et publié (≈800 Ko la pile, 0 awt, D3) ; l'amorçage headless tient en 4 classes (D1) ; le VFS s'émule (D2) ; les stubs sont un arbre plat compact sérialisable (D4). Mais « parser juste » n'a jamais été le problème de CodeIDE : ecj le fait déjà avec la fidélité compilateur en plus (D9). L'embarquement de PSI seul n'apporterait donc que ce que le statu quo a déjà — en perdant la fidélité ecj (les diagnostics rouges d'IntelliJ eux-mêmes ne viennent pas d'un compilateur mais de 18 130 lignes de checkers réimplémentés, D9).

**2. La qualité perçue d'un IDE complet réside dans la plateforme autour, et précisément dans cinq mécanismes qui ne sont PAS dans PSI :**
- **l'index inversé multi-usage + forward index** (D7) : l'invalidation fine par fichier, les updates différentielles, les 19 index Java (méthodes, champs, annotations, super-classes) qui alimentent complétion, navigation, recherche d'usages et inspections — tout cela vit dans indexing-api/indexing-impl/lang-impl/backend, pas dans le PSI ;
- **la recherche d'usages indexée** (D10) : `MethodUsagesSearcher` & co. — le renommage sûr, la recherche de références et l'inversion de contrôle du « qui appelle quoi » sont des QueryExecutors sur l'index, pas du PSI ;
- **les ~660 inspections et leurs quick fixes** (D9) : 147 k lignes de valeur sémantique greffées sur le contrat visitor/holder — c'est l'écosystème, pas le noyau ;
- **le classement de complétion** (D8) : la chaîne de weighers + l'arrangement incrémental (gel, trim honnête) + les statistiques d'usage — la « complétion intelligente » perçue par l'utilisateur est à ~90 % du côté de l'arrangement, pas de la collecte ;
- **le modèle de concurrence et de mémoire** (D11/D12) : read/write actions avec priorité écrivain, annuler-c'est-rejouer, soft-références généralisées, robinet batch — ce qui permet à tout ce qui précède de tourner sans saturer l'UI ni le tas.

**3. La partie non portable en n'embarquant que PSI, dit précisément, est donc : tout ce qui donne du contexte au-delà du fichier courant** — l'index (et avec lui la recherche d'usages, les inspections index-dépendantes, la complétion de types hors fichier), les statistiques d'usage, l'exécution non bloquante multi-fichiers. Et réciproquement : embarquer ces mécanismes-là sous forme de **code** est précisément ce que l'étude déconseille (deux piles de persistance incompatibles ART, EDT, 600 Mo de page cache, rebuild global sur corruption — D7 ; platform-impl non détachable — D11). La conclusion d'architecture est que ces mécanismes doivent être **repris comme designs et protocoles** (ValueContainer/forward-index/IndexingStamp, QueryExecutor sur CIDX, ModCommand, chaîne de weighers, NBRA, époque) et greffés sur le moteur et l'index existants — c'est la voie « s'inspirer » de l'ADR 0004 à venir.

Cette conclusion borne aussi la voie embarquée (V2 du spike) : elle achète le **parseur** et le **PSI/résolution** (utiles pour la navigation riche et les refactorings sûrs à terme), pas l'index ni les inspections ni le classement — qui restent, dans toutes les voies, du travail maison par-dessus CIDX et ecj.

## Sources consultées (R6)

- `JetBrains/intellij-community` @ `865067b0a7f1a73ee0fa8bce4f042013c8ac334f` — clone sparse lecture seule (modules listés en tête) ; fichiers cités `chemin:ligne` dans le corps et dans les annexes ; quelques fichiers lus via `git cat-file` ([hors sparse]). Licence : Apache-2.0, « JetBrains Open-Source Build Terms v1.3 » du 15/06/2026 (`LICENSE.txt`).
- `jjoblab/codeide-lsp` @ `cb809e1412fec429c9672da087244b3bd3176545` (v1.16.0) — lecture seule : `lsp/java` (EcjCompilerEngine, EcjCompletionEngine, EcjRenameEngine, EcjNavigationEngine, EcjParseCache, EcjLocateCache, EcjEngineScheduler, CancelToken, services/*), `lsp/index` (CIDX), `docs/` (LSP_JAVA.md, LSP_MATRICE_CAPACITES.md, ADR 0108/0109/0110/0111/0200, THIRD_PARTY_NOTICES.md). Dépôt propriétaire jjoblab.
- `jjoblab/codeide-tooling` @ `b60f7f0` — lecture seule : `tooling/server/.../HeapMonitor.kt`.
- `jjoblab/CodeIDE` @ `54e99c6` (v0.202.0), `jjoblab/code-editor` @ `066b8c0` — clonés lecture seule pour la phase 0 (inventaire).
- Artefacts binaires consultés : `org.jetbrains:java-syntax 0.3.381` (+ syntax-api, lang-syntax, ij-multiplatform-util `-jvm`) via cache-redirector JetBrains (intellij-dependencies) — conditions d'utilisation à consigner dans l'ADR licences (0001/0017).
- Aucune source GPL/AGPL ni licence non vérifiée consultée (R6.2). Aucun code tiers copié (R6.4).

## Mesuré / Estimé / Non vérifié

**Mesuré** (comptages et lectures directes, commandes dans les annexes) : tous les comptages fichiers/lignes par module ; les comptages d'API sensibles ART par périmètre ; la présence/absence des classes et mécanismes cités (avec `fichier:ligne`) ; les chiffres CIDX/ecj repris de `docs/LSP_JAVA.md` §4/§14 (mesures bureau antérieures du projet, non re-mesurées ce jour — voir `02-spike-mesures.md`).

**Estimé** : le facteur ART 3–5× (source `LSP_JAVA.md`, appliqué aux latences) ; le noyau classement ≈ 10 fichiers/1 500 lignes ; ~800 ko pour la pile java-syntax (tailles d'artefacts publiés, sommées).

**Non vérifié / à faire sur appareil** : comportement GC ART (éviction SoftReference, coût ReferenceQueue — D12) ; latences réelles des voies V1/V2 sur appareil (spike) ; empreinte mémoire du PSI complet d'un fichier (pas de banc dans le sparse) ; publication/conditions des artefacts `platform/util/storages` ; enregistrements XML des weighers Java (hors sparse) ; `CompletionProgressIndicator` et implémentation `platform/backend` du RPC complétion (hors sparse).


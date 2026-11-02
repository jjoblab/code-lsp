# Annexe — Rapport brut exp-D7D8 (D7 index, D8 complétion)

> Task ID: exp-D7D8 · Agent: explore-D7D8 · Lecture seule absolue · Aucun build lancé.
> Reproduit tel que rendu par l'agent d'étude ; le document maître est `../01-architecture-psi.md`.
> **Note de provenance (R5)** : le bac à sable de la mission a été réinitialisé le 28/10/2026 (~10h13) ; ce rapport a été reconstruit le même jour depuis le contexte de mission (les citations `fichier:ligne` proviennent des lectures réelles effectuées sur le clone @ `865067b0` avant la réinitialisation ; le clone de référence a été recréé au même commit et des vérifications ponctuelles y ont été rejouées — journal : `worklog` de la mission). Traiter toute citation non re-vérifiée comme « à vérifier » au besoin.
>
> Sources : `JetBrains/intellij-community` sparse clone @ `865067b0a7f1a73ee0fa8bce4f042013c8ac334f` (LICENSE « JetBrains Open-Source Build Terms v1.3 » du 15/06/2026, sources Apache-2.0) ; `jjoblab/codeide-lsp` @ `cb809e1412fec429c9672da087244b3bd3176545` (v1.16.0). Préalables lus : worklog (D1–D6), prompt mission (§4, §5, R5, R6), ADR 0111, `docs/LSP_JAVA.md` (§3, §4, §9, §14), `docs/LSP_MATRICE_CAPACITES.md`.
>
> Conventions : les chemins sont relatifs à la racine du dépôt concerné (préfixés `IC:` pour intellij-community, `CL:` pour codeide-lsp) ; `fichier:ligne` pour les points précis. Chiffres = comptages réels, commande indiquée. Ce qui n'a pas pu être vérifié est étiqueté « à vérifier ».

# D7 — Index (stub index, file-based indexes, persistance, requêtes par préfixe)

**Question à trancher** : comparaison avec l'index par segments par jar (ADR 0111 du projet actuel).

## D7.1 Rôle et flux complets

### Architecture générale

Le système d'index IntelliJ est un **index inversé multi-indices** : chaque `FileBasedIndexExtension<K, V>` (IC: `platform/indexing-api/src/com/intellij/util/indexing/FileBasedIndexExtension.java`) déclare un ID, un `DataIndexer<K, V, FileContent>` (extraction clé→valeur par fichier), un `KeyDescriptor<K>` (sérialisation/comparaison des clés) et un `DataExternalizer<V>`. Les **stub indexes** sont un cas particulier : chaque `StubIndexExtension` est enrobée en `FileBasedIndexExtension<K, Void>` (valeur vide, IC: `platform/indexing-impl/src/com/intellij/psi/stubs/StubIndexEx.java:473-525` — `wrapStubIndexExtension`, indexer qui jette `AssertionError` :499-501, `VoidDataExternalizer` :511-513) car **les données réelles (stub trees sérialisés) vivent dans un index à part**, le `StubUpdatingIndex` (clé = fileId, valeur = `SerializedStubTree`), lu seulement au moment de matérialiser les PSI.

Deux piles de stockage coexistent à ce commit :

1. **Pile par défaut** (celle réellement active sans propriété système) : `VfsAwareMapIndexStorage` = `MapIndexStorage` sur **`PersistentHashMap`** sur **`PagedFileStorage`** — IC: `platform/indexing-impl/src/com/intellij/util/indexing/impl/storage/DefaultIndexStorageLayoutProvider.kt:197-209` (`createDefaultFactories` instancie `VfsAwareMapIndexStorage`), le forward index est `PersistentMapBasedForwardIndex` (:211-215). Un WAL est actif par défaut : `System.getProperty("indexes.use-write-ahead-log", "persistent")` (même fichier, ~ligne 257).
2. **Pile « durable » expérimentale** (opt-in) : `DurableMapIndexStorage` / `DurableDatabaseIndexLayoutProvider`, sélectionnée par la propriété système `DurableDatabaseIndexLayoutProvider.APPLICABLE_INDEX_IDS` (liste d'IDs, `*` = tous) — IC: `platform/indexing-impl/src/com/intellij/util/indexing/impl/storage/durablemap/database/DurableDatabaseIndexLayoutProvider.kt:40-48` (commentaire de classe). Elle repose sur `platform/util/storages` (DurableMap, DurableDatabase, AppendOnlyLog, extendible hash maps).

### Chaîne de requête (lecture) — points clés fichier:ligne

```
FileBasedIndex.getValues(ID, key, scope)                      IC: platform/indexing-api/src/com/intellij/util/indexing/FileBasedIndex.java:157
 └─ FileBasedIndexEx.getValues                                IC: platform/indexing-impl/src/com/intellij/util/indexing/FileBasedIndexEx.java:191-232
     ├─ extractIdFilter / getAccessibleFileIdFilter           (:206-207)   — filtre Bloom-ish de fileIds par projet
     ├─ processValueIterator → readIndexAndProcessExceptions  (:412-449)
     │    ├─ waitUntilIndicesAreInitialized                   (:417)
     │    ├─ ensureUpToDate                                   IC: platform/lang-impl/backend/src/com/intellij/util/indexing/FileBasedIndexImpl.java:951-1058
     │    │    ├─ assertReadAccessAllowed / NoAccessDuringPsiEvents (:971-972)
     │    │    ├─ isDumbMode → handleDumbMode                 (:991-993) → IC:1070-1073 : throw IndexNotReadyException.create(DumbService…getDumbModeStartTrace())
     │    │    ├─ forceUpdate (updates VFS en attente)        (:1034-1038)
     │    │    └─ indexUnsavedDocuments (documents non sauvés) (:1041)
     │    └─ computable.convert(index) → index.withData(key…)  (:429) → IC: platform/util/src/com/intellij/util/indexing/impl/MapReduceIndex.java:283-286 (myStorage.read)
     └─ filtre scope.contains(file) par inputId               (FileBasedIndexEx.java:210-227)
```

Requête **StubIndex** (celle qu'utilisent les index Java) :

```
StubIndex.getElements(key…)                                  IC: platform/indexing-api/src/com/intellij/psi/stubs/StubIndex.java:93-111
 └─ StubIndexEx.processElements                               IC: platform/indexing-impl/src/com/intellij/psi/stubs/StubIndexEx.java:158-277
     ├─ DumbService.isDumb(project) + DumbModeAccessType      (:181-190) — RAW_INDEX_DATA_ACCEPTABLE INTERDIT pour StubIndex (AssertionError :186-189)
     ├─ getContainingIds(indexKey, key…)                       (:391-457)
     │    ├─ ensureUpToDate(StubUpdatingIndex.INDEX_ID …)      (:401)
     │    └─ index.withData(dataKey, container.forEach(…))     (:430-438)  → IntSet de fileIds
     ├─ pour chaque fileId : myCachedStubIds[key][fileId]      (:242-244) — cache ConcurrentHashMap (KeyAndFileId→StubIdList) déclaré :78-82, invalidé par modificationStamp du StubUpdatingIndex
     └─ StubProcessingHelper.processStubsInFile                (:197-201) — désérialisation du SerializedStubTree (StubUpdatingIndex) → remontée des éléments PSI
```

Auto-réparation en fin de requête : `tryFixIndexesForProblemFiles` (StubIndexEx.java:285-326) — re-index des fichiers en erreur + `psiManager.dropPsiCaches()` via `invokeLater`/`WriteAction` (:320-324) — **dépendance EDT/WriteAction**.

### Chaîne d'indexation (écriture)

```
Événements VFS → ChangedFilesCollector / DirtyFiles / FilesToUpdateCollector   IC: FileBasedIndexImpl.java:209-238
Scan initial (dumb mode) → UnindexedFilesFinder/UnindexedFilesIndexer          IC: platform/lang-impl/backend/src/com/intellij/util/indexing/
Content queue → IndexUpdateRunner.kt (630 l.)                                  IC: platform/lang-impl/backend/src/com/intellij/util/indexing/contentQueue/IndexUpdateRunner.kt
 └─ FileBasedIndexImpl.indexFileContent                                        IC: FileBasedIndexImpl.java:1633-…
     └─ index.mapInputAndPrepareUpdate(fileId, content)                        IC: MapReduceIndex.java:289-303
         ├─ mapInput → indexer.map(content)                                    (:364-374)
         └─ prepareUpdate → diffBuilder.differentiate(newData…)                (:306-326)  — LE forward index fournit l'ANCIENNE carte de clés du fichier → diff ADDED/UPDATED/REMOVED
 └─ IndexWriter.suitableWriter(…).writeSync                                    IC: FileBasedIndexImpl.java:1589-1593
     └─ StorageUpdate.update() → MapReduceIndex.updateWith                    IC: MapReduceIndex.java:444-483
         └─ changedEntriesProcessor → storage.addValue/updateValue/removeAllValues  (:422-438) + updateForwardIndex (:465)
 └─ IndexingStamp.setFileIndexedStateCurrent(fileId, indexId)                  IC: platform/lang-impl/backend/src/com/intellij/util/indexing/impl/storage/VfsAwareMapReduceIndex.java:184-194
```

Le **forward index** (map fileId → données indexées sérialisées, `PersistentMapBasedForwardIndex`) est la pièce qui rend la mise à jour **incrémentale par fichier** : sans lui, retirer les anciennes clés d'un fichier nécessiterait de le ré-indexer (MapReduceIndex.java:348-362 `getKeysDiffBuilder`). C'est l'équivalent fonctionnel du « contenu du segment » CIDX côté codeide-lsp.

**Versions et rebuild** : `IndexVersion` (IC: `platform/lang-impl/backend/src/com/intellij/util/indexing/IndexVersion.java`, BASE_VERSION=15 :25) — version (index, common, vfsCreationStamp) par index ; si la version diffère → `storageLayout.clearIndexData()` + suppression du répertoire de l'index + ré-écriture (IC: `platform/lang-impl/backend/src/com/intellij/psi/stubs/StubIndexImpl.java:161-182`). `CorruptionMarker.kt` (même répertoire) : marqueur global `corruption.marker` dans le root des index → rebuild complet. Un échec d'E/S pendant une update → `requestRebuild(indexId, e)` → **rebuild de l'index ENTIER** (tous fichiers), pas du seul fichier (FileBasedIndexImpl.java:477-496, MapReduceIndex.java:505-521).

**Dumb mode** : `DumbService` (IC: `platform/core-api/src/com/intellij/openapi/project/DumbService.kt`) ; accès index en dumb = `IndexNotReadyException` (FileBasedIndexImpl.java:1070-1073) sauf via `DumbModeAccessType.ignoreDumbMode(...)` (IC: `platform/indexing-api/src/com/intellij/util/indexing/DumbModeAccessType.java:18-61`, `RELIABLE_DATA_ONLY` / `RAW_INDEX_DATA_ACCEPTABLE`). Les requêtes Java typiques passent par `DumbService.runReadActionInSmartMode` (IC: `java/java-indexing-impl/src/com/intellij/psi/impl/search/AllClassesSearchExecutor.java:91,101`).

### La complétion par préfixe côté IntelliJ — fait critique

**Il n'existe PAS de requête par préfixe au niveau du stockage.** La complétion de noms de classes énumère TOUTES les clés de l'index des noms courts puis filtre en mémoire :

```
AllClassesGetter.processJavaClasses(matcher, project, scope, processor)       IC: java/java-impl/src/com/intellij/codeInsight/completion/AllClassesGetter.java:193-204
 ├─ AllClassesSearchExecutor.processClassNames                                IC: java/java-indexing-impl/src/com/intellij/psi/impl/search/AllClassesSearchExecutor.java:100-109
 │   └─ PsiShortNamesCache.processAllClassNames                               IC: java/java-indexing-impl/src/com/intellij/psi/impl/PsiShortNamesCacheImpl.java:117-123
 │       └─ StubIndex.processAllKeys(CLASS_SHORT_NAMES)  ← ÉNUMÉRATION COMPLÈTE des clés
 ├─ filtrage en mémoire : if (prefixMatcher.prefixMatches(s)) names.add(s)    (AllClassesGetter.java:197-201)
 ├─ LinkedHashSet sorted = prefixMatcher.sortMatching(names)                  (AllClassesGetter.java:202)
 └─ processClassesByNames → PsiShortNamesCache.getClassesByName (clé EXACTE)  (AllClassesSearchExecutor.java:74-98) → StubIndex.getElements → désérialisation stubs → PSI
```

`JavaShortClassNameIndex` (IC: `java/java-indexing-impl/src/com/intellij/psi/impl/java/stubs/index/JavaShortClassNameIndex.java:16-49`) est un `StringStubIndexExtension` (clé = nom simple exact, :29-31) avec `traceKeyHashToVirtualFileMapping()=true` (:47-49) — ce drapeau active le `KeyHashLog` (IC: `platform/indexing-impl/src/com/intellij/util/indexing/impl/storage/KeyHashLog.java`) qui persiste (keyHash→fileId) pour **filtrer les clés par projet pendant l'énumération** (VfsAwareMapIndexStorage.processKeys, IC: `platform/indexing-impl/src/com/intellij/util/indexing/impl/storage/VfsAwareMapIndexStorage.java:153-178`). `JavaFullClassNameIndex` (IC: même répertoire, :16-44) est un `CharSequenceHashStubIndexExtension` (clé = hash du FQN) — requête exacte uniquement, utilisée par `findClass` (cf. D5).

L'énumération complète est le prix payé pour le camel-hump universel (`CamelHumpMatcher`/`MinusculeMatcher`) sans structure de préfixe au sol ; sur le jeu lourd équivalent (~23 300 noms simples, CL: `docs/LSP_JAVA.md` §12) chaque complétion de type repasse donc par la désérialisation/itération de toutes les clés de l'index — amortie sur desktop par les caches (SLRU de clés, `MapIndexStorageCache`), coûteuse a priori sur ART.

## D7.2 Classes/interfaces clés (chemins exacts)

| Rôle | Classe | Chemin (IC:) |
|---|---|---|
| API requêtes | `FileBasedIndex` (abstrait) | platform/indexing-api/src/com/intellij/util/indexing/FileBasedIndex.java |
| Impl requêtes | `FileBasedIndexEx` → `FileBasedIndexImpl` | platform/indexing-impl/src/…/FileBasedIndexEx.java ; platform/lang-impl/backend/src/…/FileBasedIndexImpl.java (2 606 l.) |
| Moteur index générique | `MapReduceIndex` (abstract) / `MapReduceIndexBase` | platform/util/src/com/intellij/util/indexing/impl/MapReduceIndex.java (551 l.) ; platform/indexing-impl/src/…/storage/MapReduceIndexBase.java |
| Spécialisation VFS | `VfsAwareMapReduceIndex` | platform/lang-impl/backend/src/…/impl/storage/VfsAwareMapReduceIndex.java (334 l.) |
| Stockage inverted (défaut) | `VfsAwareMapIndexStorage` → `MapIndexStorage` | platform/indexing-impl/src/…/impl/storage/VfsAwareMapIndexStorage.java ; platform/util/src/…/impl/MapIndexStorage.java |
| Map persistante | `PersistentHashMap` (délégue `PersistentMapBase`/`PersistentMapImpl`) | platform/util/src/com/intellij/util/io/PersistentHashMap.java (269 l.) ; PersistentHashMapValueStorage.java (1 203 l.) |
| Pages/caches | `PagedFileStorage`, `FilePageCache`, `DirectBufferWrapper`, `DirectByteBufferAllocator` | platform/util/src/com/intellij/util/io/ |
| Cache mémoire par index | `MapIndexStorageCache` (SLRU, provider) | platform/util/src/com/intellij/util/indexing/impl/MapIndexStorageCache.kt |
| Forward index (diff) | `PersistentMapBasedForwardIndex` + accessors | platform/indexing-impl/src/…/impl/forward/ |
| Stub index | `StubIndex` (API) / `StubIndexEx` / `StubIndexImpl` | platform/indexing-api/src/com/intellij/psi/stubs/StubIndex.java ; platform/indexing-impl/src/…/StubIndexEx.java (557 l.) ; platform/lang-impl/backend/src/…/psi/stubs/StubIndexImpl.java (487 l.) |
| Stub par fichier | `StubUpdatingIndex`, `StubUpdatingIndexStorage`, `StubTreeLoaderImpl` | platform/indexing-impl/src/…/StubUpdatingIndex.java ; platform/lang-impl/backend/src/…/psi/stubs/StubUpdatingIndexStorage.java (240 l.) |
| Index Java | `JavaFullClassNameIndex`, `JavaShortClassNameIndex`, `JavaMethodNameIndex`, `JavaFieldNameIndex`, `JavaAnnotationIndex`, `JavaSuperClassNameOccurenceIndex`, … | java/java-indexing-impl/src/com/intellij/psi/impl/java/stubs/index/ (19 index listés) |
| Versions/rebuild | `IndexVersion`, `CorruptionMarker`, `RebuildStatus` | platform/lang-impl/backend/src/com/intellij/util/indexing/ |
| Pile durable (opt-in) | `DurableMapIndexStorage`, `DurableMap`, `DurableDatabase`, `MMappedFileStorage`, `AppendOnlyLogOverMMappedFile`, `DurableEnumerator` | platform/indexing-impl/src/…/durablemap/ ; platform/util/storages/src/com/intellij/platform/util/io/storages/ |

## D7.3 Dépendances plateforme et détachabilité

Ce qui **tombe** si l'on retire Project/UI/Editor (et donc ce qu'il faudrait réécrire) :

- **Project** : les requêtes prennent `GlobalSearchScope` (porte le projet) ; `ensureUpToDate` exige un contexte projet pour les updates (FileBasedIndexImpl.java:981-987) ; le filtre par projet (`ProjectIndexableFilesFilterHolder`, IC: `platform/lang-impl/backend/src/com/intellij/util/indexing/projectFilter/ProjectIndexableFilesFilterHolder.kt`) suit les fileIds par projet. Sans projet : utilisable en tout-scope, mais l'écosystème est pensé projet.
- **VFS/ManagingFS** : les clés de valeur sont des **fileIds VFS** (`VirtualFileWithId`) — `FileBasedIndex.getFileId` lève si le fichier n'a pas d'ID (IC: FileBasedIndex.java:139-145). Une réutilisation exige soit le VFS persistant d'IntelliJ, soit une émulation complète d'IDs de fichiers.
- **Read/Write actions, EDT, MessageBus** : `ensureUpToDate` appelle `assertReadAccessAllowed` (FileBasedIndexImpl.java:971) ; le rebuild passe par `DumbServiceImpl.queueTask`/`invokeLater` (VfsAwareMapReduceIndex.java:274-290) ; `StubIndexImpl` utilise `AppUIExecutor.onWriteThread` (StubIndexImpl.java:416) ; écoutes MessageBus (FileDocumentManagerListener, FileBasedIndexPluginListener…, FileBasedIndexImpl.java:288-333).
- **Documents/PSI non sauvés** : `indexUnsavedDocuments` + `PsiDocumentTransactionListener` (FileBasedIndexImpl.java:288-308) — couplage PSI/Document pour l'index à chaud des documents ouverts.
- **LowMemoryWatcher** : chaque `MapReduceIndex` enregistre un flusher (MapReduceIndex.java:113) → `LowMemoryWatcherManager` (IC: platform/util/src/com/intellij/openapi/util/LowMemoryWatcherManager.java:11-16) qui utilise **javax.management + java.lang.management** (GC MXBeans) — absent d'ART.
- **Détachable en pratique** : la *paire* (DataIndexer, KeyDescriptor/DataExternalizer) d'une extension est du Java pur ; le cœur algorithmique `MapReduceIndex` + `MapIndexStorage` + `PersistentHashMap` est *presque* autonome (Logger, ProcessCanceledException, LowMemoryWatcher, ThrowableComputable) — c'est la couche dessous (PagedFileStorage/FilePageCache/StorageLockContext/WAL) et la couche au-dessus (FileBasedIndexImpl, VFS, dumb mode) qui portent la plateforme.

## D7.4 APIs runtime sensibles pour ART (D7)

Comptages réels — commande : `rg -l "<pattern>" <modules D7>` sur `platform/indexing-api/src platform/indexing-impl/src platform/lang-impl/backend/src/com/intellij/util/indexing platform/lang-impl/backend/src/com/intellij/psi/stubs platform/util/src/com/intellij/util/io platform/util/src/com/intellij/util/indexing platform/util/storages/src java/java-indexing-impl/src` :

| API | Fichiers touchés | Détail |
|---|---|---|
| `sun.misc.Unsafe` | **0** | — |
| `java.lang.foreign` (Arena/MemorySegment) | **38** | quasi tous dans `platform/util/storages` (pile durable non-défaut) ; critique : `MMappedFileStorage.java:18-19,108,504` (`channel.map(READ_WRITE, offset, pageSize, pagesArena)`) |
| `VarHandle` | **21** | pile durable (layouts de records : `AppendOnlyLogOverMMappedFile.java:189-198`, `KeyRecordLayout`, `BlockHeaderLayout`, …) + `platform/util/src/com/intellij/util/io/pagecache/Page.java` |
| `MethodHandles` | **2** | `ByteBufferUtil.java:23-56` : `Class.forName("sun.nio.ch.DirectBuffer")` + `cleaner().clean()` pour libérer les buffers directs — **API JDK absente d'ART** |
| `MappedByteBuffer` | **2** | `writeaheadlog/FileChannelWithWAL.kt` ; `ResilientFileChannel.java:132-135` (`ch.map(mapMode, …)`) |
| réflexion JDK interne | (fichier unique, hors comptage ci-dessus) | `PageCacheUtils.maxDirectMemory` (PageCacheUtils.java:110-130) : `jdk.internal.misc.VM` / `sun.misc.VM` / `java.nio.Bits` — absent d'ART |
| `javax.swing` | **2** | `SwitchFileBasedIndexStorageAction.kt:16-17` (action UI) ; `SourceCodeCompressor.java:28` (chaîne de test seulement) |
| `java.awt` | **0** | — |
| `ServiceLoader` | **2** | dont `MapIndexStorageCache.kt:20` (provider de cache de stockage) — ServiceLoader existe sur ART, mais packaging `META-INF/services` en APK à vérifier |
| `System.getProperty` | **12** | réglages (tailles de pages, WAL, durable opt-in, provider de cache) |
| `java.lang.management`/`javax.management` | 0 dans le périmètre ci-dessus, **1 hors périmètre immédiat** | `LowMemoryWatcherManager.java:11-16` (utilisé par MapReduceIndex.java:113) |
| `ThreadLocal` | multiples | `FileBasedIndexImpl.java:175-176` (ourIndexedFile, ourWritingIndexFile), `:947` (myReentrancyGuard) ; `PagedFileStorage.java:36,47` ; `MapIndexStorageCache.kt` |

Point notable : **la pile par défaut n'utilise PAS `MappedByteBuffer`** — `DirectBufferWrapper` « émule » le mmap avec des `ByteBuffer.allocateDirect` re-lus par `FileChannel.read` (IC: `platform/util/src/com/intellij/util/io/DirectBufferWrapper.java:16-17,262-276`), poolés par `DirectByteBufferAllocator` (IC: `DirectByteBufferAllocator.java:105-107,186-188`, pool ≤ 100 Mo — `PageCacheUtils.java:55`). Le page cache total vise **600 Mo par défaut** (200 Mo en 32 bits, IC: `PageCacheUtils.java:44-49`), pages de 10 Mo (:35) — dimensionnés desktop, inadaptés tels quels à un budget mobile de 30 Mo.

## D7.5 Ordres de grandeur (comptages réels)

Commande type : `find <module> -type f \( -name '*.java' -o -name '*.kt' \) | wc -l` puis `-exec cat {} + | wc -l`.

| Module (IC:) | Fichiers | Lignes |
|---|---:|---:|
| platform/indexing-api | 104 | 7 632 |
| platform/indexing-impl | 172 | 20 704 |
| platform/lang-impl/backend/src/…/util/indexing (+contentQueue) | 118 | 20 414 |
| platform/lang-impl/backend/src/…/psi/stubs | 6 | 1 576 |
| platform/util/src/com/intellij/util/io | 168 | 29 927 |
| platform/util/src/com/intellij/util/indexing | 64 | 6 317 |
| platform/util/storages (pile durable) | 118 | 24 036 |
| java/java-indexing-impl | 73 | 8 008 |
| **Total périmètre D7** | **~823** | **~118 600** |

Cœur minimal de la fonctionnalité « index inversé persistant + requêtes » (FileBasedIndexEx + MapReduceIndex + MapIndexStorage + PersistentHashMap(+ValueStorage) + PagedFileStorage + FilePageCache + DirectBufferWrapper/Allocator + StubIndexEx/Impl + StubUpdatingIndexStorage) ≈ 12 fichiers / ≈ 9 000 lignes — mais avec les dépendances plateforme listées en D7.3. Artefacts publiés : les modules `com.jetbrains.intellij.platform:indexing-impl` sont figés à 241.194 sur intellij-dependencies (constaté D1/D2, cf. worklog) — version obsolète vs ce commit ; la pile `util/storages` n'est pas publiée comme artefact autonome vérifiable (à vérifier).

## D7.6 Comparaison factuelle avec l'index par segments par jar (ADR 0111)

Faits côté codeide-lsp (CL: `lsp/index/` @ cb809e14 — 15 fichiers main Kotlin, 3 700 lignes + tests 2 511 + banc 261 ; module `:lsp:index` ; ADR 0111 ; `docs/LSP_JAVA.md` §14.1-14.2) :

| Dimension | IntelliJ FileBasedIndex/StubIndex @ 865067b0 | codeide-lsp `:lsp:index` (ADR 0111, v0.93.0+) |
|---|---|---|
| Granularité d'invalidation | **fichier** (fileId VFS × indexId, via IndexingStamp) | **jar/entrée de classpath** (segment = empreinte de contenu W6, `maven:<sha1>` du chemin Gradle, CL: `lsp/index/src/main/kotlin/jo/codeide/lsp/index/ClesSegments.kt:28-44`) |
| Identité de contenu | fileId VFS (identité de *chemin* dans le VFS persistant) ; pas de hash de contenu au niveau index | hash sha256 de l'empreinte → nom de segment opaque `sha256(empreinte).idx` |
| Structure | N index inversés (clé → ValueContainer(fileId→valeur)) + 1 forward index (fileId→données) par index | 1 segment par jar, sections numérotées (ENTRÉES/PAQUETS/NOMS SIMPLES triés/MEMBRES paresseux/CHAÎNES/ORIGINES), fusion LSM à la requête (union ordonnée, premier porteur gagnant, CL: `VueIndex.kt:82-265`) |
| Persistance | `PersistentHashMap` propriétaire sur `PagedFileStorage` (pages 10 Mo, direct ByteBuffers, WAL activé par défaut) | format « CIDX » propre, gros-boutien, versionné, crc32, écriture atomique `.tmp`+renommage+fsync (ADR 0111 §Mise en œuvre) |
| **Requête par préfixe** | **N'EXISTE PAS** : énumération de TOUTES les clés + filtre CamelHumpMatcher en mémoire (AllClassesGetter.java:193-204 ; PsiShortNamesCacheImpl.java:117-123) | **recherche binaire par segment trié** : `nomsParPrefixe` (VueIndex.kt:137-176) — banc : préfixe p50/p95 = 0,16/1,1 ms (petit) à 0,76/7,1 ms (lourd) (LSP_JAVA.md §14.2) |
| +1 dépendance | seuls les nouveaux fichiers (nouveaux fileIds) sont indexés ; jar déjà indexé dans un autre projet = réutilisé (index GLOBAL par fileId) — mais un jar jamais vu = scan + indexation complète ; pas de mesure publiée ici | **seul le jar nouveau analysé** : 85/89/87 ms pour 14/83/250 entrées (vs 257/396/1 178 ms avant ADR 0111) (§14.2) |
| Reprise à chaud | validation par IndexVersion + IndexingStamp par fichier (pas de mesure ici) | validation d'en-tête seuls : 7/10/25 ms pour 250 segments (§14.2, cible < 1,5 s tenue 60×) |
| Corruption | exception → `requestRebuild` = rebuild de l'INDEX ENTIER (tous fichiers) (MapReduceIndex.java:505-521) ; CorruptionMarker global → tout rebuild | refus du SEUL segment (crc/magie/version/clé/stats) → reconstruction paresseuse de ce segment, jamais de crash (ADR 0111 §4) |
| Empreinte mémoire | FilePageCache 600 Mo direct (défaut) + SLRU 1 024 entrées par index (FileBasedIndexExtension.java:56 `DEFAULT_CACHE_SIZE = 1024`) + cache StubIdList non borné par (clé,fileId) | cache LRU borné par construction, octets comptés : 4,2/2,9/29,7 Mo mesurés (§14.2) — budget cible < 30 Mo/100 jars tenu |
| Remontée éléments | StubIndex → désérialisation SerializedStubTree → **arbre PSI complet** (lourd mais riche : navigation, quick-doc, resolve) | noms simples, FQCN, membres (décodage paresseux), paquets — **pas d'arbre PSI** |
| APIs sensibles | direct ByteBuffer + cleaner MethodHandles (`sun.nio.ch.DirectBuffer`) + réflexion `sun.misc.VM` + WAL mmap + (pile durable : Arena/MemorySegment/VarHandle — JDK 19+) | Kotlin JVM pur : `RandomAccessFile` + `Inflater` (LecteurZipBrut), zéro mmap, zéro réflexion (§14.1) |
| Connaissance « classpath » | aucune : l'indexation suit les racines indexables du projet ; l'ordre du classpath est géré par les scopes | ordre du classpath ordonné dans la fusion LSM (préséance des segments) |

**Ce que l'index IntelliJ a et que CIDX n'a pas** : (1) l'invalidation **par fichier source** (IndexingStamp) — indispensable pour les sources du module éditées en continu, hors du périmètre ADR 0111 qui délègue ça au modèle/au moteur ; (2) le **forward index** qui rend les updates différentielles (retrait propre des anciennes clés) ; (3) la **généricité multi-index** (un même moteur sert 19 index Java : methods, fields, annotations, super-classes, usages…) ; (4) la remontée PSI complète. — Ces apports sont des *designs*, réutilisables indépendamment du code.

## D7.7 Verdict D7

**S'INSPIRER — ne pas embarquer. À armes égales avec le statu quo :**

- **Embarquer** coûterait : ~110 k lignes de périmètre direct (D7.5) + les dépendances Project/VFS-fileId/EDT/MessageBus/LowMemoryWatcher (D7.3) + des APIs JDK absentes d'ART sur les DEUX piles de stockage (cleaner MethodHandles d'un côté, java.lang.foreign de l'autre) + un page cache dimensionné 600 Mo + des formats disque non documentés et non stabilisés (WAL, durablemap opt-in) + un rebuild « tout l'index » sur corruption. En échange : rien que CIDX ne fournisse déjà pour les cas d'usage LSP mobiles actuels (les cibles §4 mesurées par l'ADR 0111 sont tenues).
- **Le statu quo amélioré** garde l'avantage mesuré sur : requête par préfixe (binaire vs énumération totale — le point le plus important : la complétion de types IntelliJ repasse par TOUTES les clés à chaque invocation), budget mémoire borné, corruption par segment, +1 dépendance.
- **À reprendre d'IntelliJ (designs)** : (a) ValueContainer clé→(fileId→valeur) comme forme canonique d'index inversé multi-usage (aujourd'hui CIDX est mono-usage par extension) ; (b) forward index / snapshot des clés indexées par unité pour des **updates différentielles** des sources du module (le point faible restant du statu quo : invalidation des sources par événements, W4) ; (c) l'idiome IndexingStamp (unité × index × version) pour une invalidation fine et multi-index cohérente ; (d) dumb mode avec états (RELIABLE_DATA_ONLY) plutôt qu'un booléen prêt/en construction — converge avec l'ADR 0111 §8.
- Licence : Apache-2.0 (OK pour inspiration/réutilisation avec NOTICE) ; les chiffres de ce rapport proviennent des deux dépôts autorisés.

## D7.8 Réponse à la question à trancher (§5)

**L'index IntelliJ n'est PAS supérieur à l'index par segments par jar pour le cas CodeIDE ; il est supérieur sur des axes différents.** Verdict : **conserver l'architecture ADR 0111 (statu quo) comme socle, et s'inspirer de trois mécanismes IntelliJ** : la multi-indexation générique sur une même base (ValueContainer), l'invalidation fine par (unité × index) avec snapshot/forward-index pour les diffs, et la sémantique à deux niveaux du dumb mode (données fiables vs données brutes). Ne pas embarquer les piles de persistance (aucune des deux n'est compatible ART sans réécriture profonde : cleaner `sun.nio.ch.DirectBuffer` d'un côté, `java.lang.foreign` de l'autre), ni le modèle fileId-VFS (verrouillage VFS persistant complet). Le point factuel décisif : **IntelliJ n'a pas de requête par préfixe dans son index** — sa complétion de types énumère toutes les clés et filtre en mémoire (AllClassesGetter.java:193-204) ; CIDX fait mieux sur cet axe précis (recherche binaire, p50 ≤ 1,7 ms mesuré au jeu moyen).

# D8 — Complétion (contributors, contexte, classement)

**Question à trancher** : quels mécanismes de classement sont transposables sur ART dans une architecture LSP ?

## D8.1 Rôle et flux complet

### Chaîne d'appel (points clés fichier:ligne)

```
1. INITIALISATION — l'astuce de l'identifiant factice (miroir du MARQUEUR ecj)
   CompletionInitializationUtil.insertDummyIdentifier          IC: platform/analysis-impl/src/com/intellij/codeInsight/completion/CompletionInitializationUtil.java:123-149
   → COPIE du fichier (CopyFileUpdateTask.replaceInCopy :149) avec DUMMY_IDENTIFIER inséré au caret
     (CompletionInitializationContext.java:23-24,31) — rend la position parsable et résoluble hors EDT.

2. COLLECTE — boucle sur les contributors
   CompletionService.performCompletion                        IC: platform/analysis-api/src/com/intellij/codeInsight/completion/CompletionService.java:159+
   └─ getVariantsFromContributors                              IC: CompletionService.java:60-84
       ├─ CompletionContributor.forParameters(parameters)      (EP `com.intellij.completion.contributor`)
       ├─ pour chaque contributor : fillCompletionVariants     (:135 via getVariantsFromContributors)
       │    IndexNotReadyException ATTRAPÉE et ignorée         (:78-79) ← repli naturel en dumb mode
       └─ result.isStopped() → arrêt                            (:80-82)
   Contributor Java principal : JavaCompletionContributor      IC: java/java-impl/src/com/intellij/codeInsight/completion/JavaCompletionContributor.java:486 (fillCompletionVariants), :1159 (beforeCompletion)
       — getReferenceFilter par ElementPattern (IN_EXTENDS_OR_IMPLEMENTS, IN_PERMITS_LIST, INSIDE_CONSTRUCTOR… :192-…, :227+), référence getVariants() + JavaCompletionProcessor (scope), mots-clés, expected-tokens.
       JavaClassNameCompletionContributor → AllClassesGetter (cf. D7.1) ; JavaMemberNameCompletionContributor (DumbAware, :83).

3. FILTRAGE PAR PRÉFIXE — à l'ajout de chaque élément
   BaseCompletionService.BaseCompletionResultSet.addElement    IC: platform/analysis-impl/src/com/intellij/codeInsight/completion/BaseCompletionService.java:114-128
   ├─ sorter = defaultSorter(parameters, matcher) si absent     (:122)
   └─ CompletionResult.wrap(element, matcher, sorter)           (:124) — prefixMatches sur les lookupStrings

4. CLASSEMENT — composition du sorter (LA pièce centrale)
   BaseCompletionService.defaultSorter                         IC: BaseCompletionService.java:204-238
   ├─ PreferStartMatching (classifier)                          (:210)
   ├─ pour chaque Weigher de l'EP `com.intellij.weigher` key="completion" (WeighingService.getWeighers(RELEVANCE_KEY), :213)
   │    "prefix"  → RealPrefixMatchingWeigher                    (:215-217)
   │    "stats"   → StatisticsWeigher (plateforme statistics)    (:218-220)
   │    autre     → adaptateur LookupElementWeigher              (:221-230)
   └─ "liftShorter" (LiftShorterItemsClassifier) avant "priority" (:232-237)
   Enregistrements plateforme : IC: platform/analysis-impl/resources/intellij.platform.analysis.impl.xml:184-188 (priority first / prefix / grouping last) et :129-130 (stats, after prefix).
   Chaque classifier est une Couche : CompletionSorterImpl.buildClassifier chaîne les ClassifierFactory (IC: platform/analysis-impl/src/…/completion/impl/CompletionSorterImpl.java:112-124) ; CachingComparingClassifier met les poids en cache (:28-41).

5. ARRANGEMENT — ordre final + sélection initiale
   CompletionLookupArranger (interface)                         IC: platform/analysis-impl/src/…/completion/CompletionLookupArranger.java:16-46
   BaseCompletionLookupArranger                                IC: platform/analysis-impl/src/…/completion/BaseCompletionLookupArranger.java
   ├─ addElement : classifier.addElement + batch/trim           (:173-217 ; batchUpdate :223-249 ; trimToHalfLimit :264-302)
   ├─ arrangeItems : getMatchingItems → groupItemsBySorter      (:337-380 — dummy presenter SANS Lookup réel :338-378)
   │    → sortByRelevance → applyFinalSorter                    (:389-420 ; :467-472)
   │    → fillModelByRelevance : topPriority + prefix + FROZEN  (:474-497) → freezeTopItems (:522-531)
   └─ trimToHalfLimit : si > limite → éviction en gardant topPriority/prefix/frozen + publicité
        « not all variants are shown » + watch prefix           (:264-302)
```

Le **gel (frozen items)** : une fois le lookup affiché, les premiers items visibles sont figés (`myFrozenItems`, :71) et ne sont plus re-triés — évite le « saut » de liste sous les yeux de l'utilisateur pendant que des contributors lents ajoutent des résultats. Documenté dans la FAQ de `CompletionContributor.java` (:104-111, IC: platform/analysis-api/src/…/completion/CompletionContributor.java).

### La mécanique de pesée extensible (Weigher/WeighingComparable)

- `Weigher<T, Location>` (IC: `platform/analysis-api/src/com/intellij/psi/Weigher.java:17-29`) : renvoie un `Comparable` — s'enregistre sous `com.intellij.weigher` avec une `key` (« completion », « proximity ») et un ordre (`LoadingOrder`).
- `WeighingComparable` (IC: `platform/analysis-api/src/com/intellij/psi/WeighingComparable.java:12-103`) : compare **paresseusement** deux éléments poids par poids (:49-68) — le premier poids qui diffère décide ; les poids calculés sont mis en cache (:70-79) ; les poids nuls trient en premier (:55-57).
- Weighers concrets :
  - `RealPrefixMatchingWeigher` (IC: platform/analysis-impl/src/…/completion/impl/RealPrefixMatchingWeigher.java:21-27) : `getBestMatchingDegree` = **max du matchingDegree du matcher sur tous les lookupStrings** — c'est le grade camel-hump gradué, exact équivalent des `Grade` EXACT/PREFIX/PREFIX_CI/HUMP/SUBSTRING de `EcjPrefixMatcher` (CL: `lsp/java/src/main/java/jo/lspjava/ecj/EcjPrefixMatcher.java:34-36`).
  - `PriorityWeigher` (IC: platform/analysis-impl/src/…/completion/PriorityWeigher.java:14-27) : Double (priorité explicite portée par l'élément).
  - `GroupingWeigher` (IC: …/GroupingWeigher.java:13-22) : Integer (groupes exclusifs en fin de liste).
  - `StatisticsWeigher` (IC: …/StatisticsWeigher.java:31+) : s'appuie sur `CompletionStatistician` (EP) + la **pile statistics (FUS) — module `platform/statistics` ABSENT du sparse, signalé**.
  - Weighers Java (IC: java/java-impl/src/com/intellij/codeInsight/completion/) : `PreferByKindWeigher.java` (proximité/kind/expected-type — gros fichier ~900 l.), `PreferMostUsedWeigher.java`, `RecursionWeigher.java`, `JavaCompletionSorting.java` (compose le sorter Java avec ces weighers), `JavaProximityStatistician.java`, `JavaCompletionStatistician.java`. Leurs enregistrements XML ne sont pas dans le sparse (seuls les 4 weighers plateforme le sont, cf. D8.1.4) — **à vérifier** côté `java/java-impl/resources`.
- `PrefixMatcher` de référence : `CamelHumpMatcher` (IC: platform/analysis-impl/src/…/completion/impl/CamelHumpMatcher.java:26-33) délègue à `MinusculeMatcher` (camel-hump, tolérance de typo) et expose `matchingDegree` — dépend de `CodeInsightSettings` (:81-83).

### Découverte clé — le split RPC frontend/backend (platform/completion)

À ce commit, `platform/completion/` contient trois modules :

- **common** : 57 fichiers / 2 340 lignes de **protocole sérialisable** (kotlinx.serialization) — c'est le contrat frontend/backend :
  - `RpcCompletionRequest.kt` : `id, sessionId, editorId, projectId, startingEditorVersion, startingDocumentVersion, completionType, invocationCount, isWriteActionRestart` (+ commentaire :14 référençant `BackendCompletionSession.restartAfterWriteAction` — classe hors sparse).
  - `RpcCompletionItem.kt` : `lookupString, allLookupStrings, presentation (RpcCompletionItemPresentation), insertHandler (Backend|Frontend), prefixMatcher, autoCompletionPolicy, flags (isCaseSensitive, isDirectInsertion, isWorthShowingInAutoPopup, isTopPriorityItem, completionGroup, hasModCommand…)` + conversion `CompletionResult.toRpc()` (:77-103) qui **rend la présentation au backend** (`element.renderElement`).
  - `RpcCompletionItemWeight.kt` :12-19 : `(id, weight: Int)` — « the lower the weight, the higher the priority » : **le classement est calculé backend et transporté comme un entier**.
  - `RpcPrefixMatcher.kt` :18-45 : matcher soit **sérialisé Frontend** (descriptor — filtrage local par frappe, sans RPC), soit **Backend** (référencé par id — trop complexe pour le frontend).
  - `RpcLookupElementEvent.kt`, `RpcCompletionArrangement.kt`, `RpcCompletionResponseEvent.kt`, `RpcCompletionCommand.kt`, `RpcCompletionFinishReason.kt`, et `modCommand/*` (RpcModCommand : updateFileText, navigate, startRename, displayMessage… — les actions post-insertion sérialisables).
- **frontend** et **backend** : `src/.keep` — **modules vides** à ce commit ; l'implémentation vit dans `platform/backend` (runtime_dep du BUILD.bazel de backend), **hors sparse — à vérifier**. Le découpage est donc EN COURS côté JetBrains : le protocole existe, la migration du code pas encore consommée dans ce clone.
- Témoignage d'orientation : `FrontendCompletionContributor` est déprécié « not supported YET… put your completion contributors in backend modules » (IC: platform/analysis-api/src/…/completion/FrontendCompletionContributor.kt:14-19) ; l'API moderne `com.intellij.modcompletion` (IC: platform/analysis-api/src/com/intellij/modcompletion/, `ModCompletionItem`, `ModCompletionItemProvider`, adaptée par `CompletionItemContributor.java:19-45`) fournit un modèle d'item **sans LookupElement**, aligné avec `RpcCompletionItem`.

**Ce que JetBrains a séparé (documenté par le protocole)** : le backend fait TOUT le calcul (PSI, index, rendu de présentation, poids, matchers complexes, insert handlers) ; le frontend reçoit des items autosuffisants (texte, présentation, poids entier, matcher éventuellement sérialisé) et peut **re-filtrer localement à chaque frappe** quand le matcher est Frontend, sans relancer de calcul ; les événements (frappe, sélection, annulation) remontent par RPC (events/command/finishReason) ; l'insertion reste exécutable backend (`RpcInsertHandler.Backend` par défaut). **C'est exactement la frontière calcul/présentation qu'impose un LSP sur Android** — et le protocole commun (2 340 lignes) en est une spécification directement exploitable comme modèle.

### Dépendances à mesurer (D8) — Editor/EDT/Lookup/statistics

- **Editor** : `CompletionParameters` porte un `Editor` (IC: platform/analysis-api/src/…/completion/CompletionParameters.java:22,120) ; `InsertionContext` (Document+Editor), `CompletionInitializationContext` (Caret+Editor) — l'API completion est Editor-centrique ; l'Editor IntelliJ est une abstraction émulable (DocumentImpl existe côté core-impl, cf. D2) mais ici il n'y a pas de variante « sans Editor ».
- **Lookup UI** : `BaseCompletionLookupArranger.arrangeItems()` (:337-380) instancie un **dummy LookupElementListPresenter anonyme** — preuve que l'arrangement est exécutable sans Lookup réel ; en revanche `LookupElementPresentation` utilise `javax.swing.Icon` (19 références IC: platform/analysis-api/src/com/intellij/codeInsight/lookup/LookupElementPresentation.java).
- **EDT/WriteAction** : `StubIndexEx.tryFixIndexesForProblemFiles` → `invokeLater`+`WriteAction`+`dropPsiCaches` (StubIndexEx.java:320-324) ; `AppUIExecutor.onWriteThread` (StubIndexImpl.java:416) ; `CompletionProcessEx` importe javax.swing. `CompletionProgressIndicator` (la supervision EDT de la complétion) est **hors sparse — à vérifier** (vit dans platform/lang-impl frontend).
- **Index/dumb** : contributors dumb-aware (`JavaMemberNameCompletionContributor.java:83 implements DumbAware`) ; `IndexNotReadyException` attrapée et ignorée dans la boucle contributors (CompletionService.java:78-79) — le comportement « complétion dégradée en dumb mode » est natif.
- **statistics** : `platform/statistics` **absent du sparse** — `StatisticsWeigher`, `CompletionStatistician`, `JavaCompletionStatistician`, `PreferMostUsedWeigher` en dépendent : SIGNALÉ conformément au prompt ; toute réutilisation de la branche « stats » exige de réécrire le stockage d'usage.
- **Annulation** : `ProgressManager.checkCanceled()` omniprésent (CompletionService.java:67 ; BaseCompletionService.java:115).

## D8.2 Classes/interfaces clés (chemins exacts)

API (IC: platform/analysis-api/src/com/intellij/codeInsight/completion/) : `CompletionContributor.java` (311 l., FAQ canonique), `CompletionService.java` (220 l.), `CompletionParameters.java`, `CompletionResultSet.java`, `CompletionSorter.java`, `CompletionInitializationContext.java`, `InsertHandler.java`, `InsertionContext.java`, `PrefixMatcher` + `CompletionWeigher` ; IC: platform/analysis-api/src/com/intellij/psi/ : `Weigher.java`, `WeighingComparable.java`.

Impl (IC: platform/analysis-impl/src/com/intellij/codeInsight/completion/) : `BaseCompletionService.java` (244 l.), `BaseCompletionLookupArranger.java` (~734 l.), `CompletionLookupArranger.java` (interface), `impl/CompletionSorterImpl.java` (126 l.), `impl/RealPrefixMatchingWeigher.java`, `impl/CamelHumpMatcher.java`, `impl/LiftShorterItemsClassifier.java`, `impl/PreferStartMatching`, `PrefixMatchingWeigher.java` (marqueur), `PriorityWeigher.java`, `GroupingWeigher.java`, `StatisticsWeigher.java`, `CompletionFinalSorter.java` (point d'entrée ML-ranking plugin), `CompletionContext.java`, `CompletionInitializationUtil.java`, `CompletionUtil.java`.

Protocole RPC (IC: platform/completion/common/src/protocol/) : `RpcCompletionRequest.kt`, `RpcCompletionItem.kt`, `RpcCompletionItemWeight.kt`, `RpcPrefixMatcher.kt`, `RpcLookupElementEvent.kt`, `RpcCompletionArrangement.kt`, `RpcCompletionSession*.kt`, `modCommand/*`.

Java (IC: java/java-impl/src/com/intellij/codeInsight/completion/) : `JavaCompletionContributor.java` (1 380 l.), `JavaClassNameCompletionContributor.java`, `JavaMemberNameCompletionContributor.java` (DumbAware), `JavaSmartCompletionContributor.java`, `AllClassesGetter.java`, `MemberLookupHelper.java`, `StaticMemberProcessor.java`, `JavaCompletionSorting.java`, `PreferByKindWeigher.java`, `PreferMostUsedWeigher.java`, `RecursionWeigher.java`, `JavaLookupElementBuilder.java`, `scope/JavaCompletionProcessor.java` ; modCommand : `PostfixTemplateModCompletionItemProvider.java` etc.

## D8.3 APIs runtime sensibles pour ART (D8)

Comptages réels — commande : `rg -l "<pattern>" platform/analysis-api/src platform/analysis-impl/src platform/completion/common/src java/java-impl/src/com/intellij/codeInsight/completion` :

| API | Fichiers | Détail |
|---|---:|---|
| `sun.misc.Unsafe` | **0** | — |
| `javax.swing` | **51** | surtout `Icon` dans les LookupElement/presentations/insert handlers (ex. `JavaPsiClassReferenceElement.java:33`) |
| `java.awt` | **16** | icons/colors des présentations |
| `VarHandle` / `MethodHandles` / `System.getProperty` | 1 / 1 / 1 | marginaux |
| `ServiceLoader`, `MappedByteBuffer`, management | 0 | — |
| DispatchThread/nonBlockingEdt/EventQueue | 10 | EDT (cf. D8.1) |

**Fait décisif** : le **chemin de classement pur** — `CompletionSorterImpl`, `BaseCompletionService.defaultSorter`, `BaseCompletionLookupArranger`, les 4 weighers plateforme, `WeighingComparable`, `CamelHumpMatcher` — contient **0 référence swing/awt** (vérifié par `rg -l "javax\.swing\."` sur ces fichiers : 0 résultat). Le classement est du pur calcul Java détachable ; le swing est dans la présentation (Icon) et la UI Lookup.

## D8.4 Ordres de grandeur (comptages réels)

| Module | Fichiers | Lignes |
|---|---:|---:|
| platform/analysis-api completion+lookup | 70 | 5 726 |
| platform/analysis-impl completion+lookup | 64 | 6 082 |
| platform/completion/common (protocole RPC) | 57 | 2 340 |
| java/java-impl/completion | 125 | 19 403 |
| **Total périmètre D8** | **~316** | **~33 550** |

Le **noyau classement** (sorter + arranger + weighers + matcher + WeighingComparable) ≈ 10 fichiers / ≈ 1 500 lignes, sans dépendance UI.

## D8.5 Le statu quo (moteur ecj actuel) — classement et mesures

Faits côté codeide-lsp @ cb809e14 (lecture directe) :

- **Pipeline** (CL: `lsp/java/src/main/java/jo/lspjava/ecj/EcjCompletionEngine.java`, 3 491 l.) : `complete()` (:117-132) → `SourceFocuser.focusAndMap` (réduction du texte hors méthode du caret, :155-157) → préfixe `identifierPrefix` (:159) → **5 variantes de splice** avec le marqueur `__codeide_completion__` (:61, :181-191) → pour chaque variante : `engine.resolveUnit` → `analyzeContext` → `collectCandidates` par `Kind` (MEMBER_ACCESS/NAME_REFERENCE/TYPE_REFERENCE/PACKAGE_REFERENCE/IMPORT_REFERENCE) ; `kindRank` (2 = contexte résolu, 1 = NAME, 0 = NONE, :539-546) ; **break au premier résultat non vide de rank 2** (:282-284) ; dernier recours : déclaration synthétique `private __m__ x;` (:294-308) puis repli lexical `textualMemberCompletions` sans bindings (:310-328).
- **Classement actuel = UN SEUL score scalaire** : `rankScore` (:3228-3258) = base 1000 − bonus de grade (EXACT 500 / PREFIX 200 / PREFIX_CI 120 / HUMP 60 / SUBSTRING 30, :3206-3212) − `BONUS_ASSIGNABLE` 300 (affinité au type attendu à l'erasure près, :3215, :3271-3281) + **proximité** (LOCAL 0 / PARAMETER 8 / OWN_MEMBER 25 / NESTED_TYPE 35 / PACKAGE 40 / INHERITED 55 / OVERRIDE 15 / OBJECT_MEMBER 180 / UNIMPORTED_TYPE 300 / KEYWORD 350, :3183-3196) + pénalités (discouraged 200 / deprecated 400, :3218-3219) ; `relevance = 2000 − score` (:3261-3263) ; tri final `RELEVANCE_ORDER` = pertinence desc → nom le plus court → alphabétique (:3294-3297). Grades du matcher : EXACT/PREFIX/PREFIX_CI/HUMP/SUBSTRING (CL: `EcjPrefixMatcher.java:34-36`) — structurellement le « matchingDegree » d'IntelliJ.
- **Kinds actuels** (CL: `lsp/java/src/main/java/jo/lspjava/api/CompletionItem.java:31-36`) : KEYWORD, CLASS, INTERFACE, ENUM, ANNOTATION, METHOD, CONSTRUCTOR, FIELD, VARIABLE, PARAMETER, TYPE_PARAMETER, PACKAGE, MODULE, SNIPPET, TEXT → mappés `CompletionItemKind` LSP4J, verrouillés par `CompletionKindMappingTest` (CL: `lsp/java/src/test/java/jo/lspjava/lsp/CompletionKindMappingTest.java`).
- **Repli « mode sourd »** : `EcjSyntaxCompletion.java` (278 l.) — complétion par les symboles du fichier seul (parse sans bindings) quand la plateforme n'est pas prête.
- **Latence mesurée aujourd'hui** : `LatenceCompletionBenchmark.java` (CL: `lsp/java/src/test/java/jo/lspjava/ecj/LatenceCompletionBenchmark.java:79-174`) — 10 mesures à chaud `System.nanoTime`, 3 scénarios (membre `binding.titre.`, type `TextVi`, préfixe vide), android.jar requis sinon skip (:35-36, :176-186) ; pas de p50/p95 agrégés dans le test (brute sortie console). Les chiffres de référence moteur restent ceux de LSP_JAVA.md §4.2 (membres p95 ≤ 6 ms, types p50 18→109 ms selon 20→250 jars — violant la cible < 100 ms au jeu lourd **avant** l'index disque, cf. §4.3 ; la complétion par préfixe côté index est passée à 0,16-1,7 ms p50 après ADR 0111, §14.2).
- **Écarts structurels vs IntelliJ (faits)** : pas de statistiques d'usage ; pas de gel des items affichés ; pas de liftShorter ; pas de groupes ; pas de trim avec publicité honnête ; pas de batch ; **recalcul complet à chaque frappe** (pas de re-filtrage client — l'infrastructure RPC d'IntelliJ répond précisément à ça) ; un seul axe de tri au lieu d'une chaîne ordonnée de poids.

## D8.6 Verdict D8

**S'INSPIRER fortement — c'est le domaine le plus transposable de toute la pile.** À armes égales :

- **Ce qui plaide pour l'embarquement partiel** : le noyau classement (≈1 500 lignes, 0 swing) est portable ; l'architecture RPC common (2 340 lignes de spécification sérialisable) est un modèle DIRECT pour un LSP (calcul serveur / filtrage client / poids entier transporté / insert handler backend) ; la FAQ de CompletionContributor et la composition du sorter sont la meilleure documentation disponible du domaine.
- **Ce qui plaide contre l'embarquement** : Contributors et LookupElements réclament Project/Editor/PSI complet + Icon swing + statistics FUS ; CompletionProgressIndicator et le handler de déclenchement sont hors sparse ; le recadrage vers LSP exige de toute façon une réécriture des entrées/sorties.
- **Coût statu quo** : le classement ecj à score unique est correct sur les grades et la proximité (d'ailleurs calqué sur le même tiers de référence que le portage CompletionRanker, cf. commentaires :3224-3226) mais **monodimensionnel** : pas de chaîne de poids extensibles, pas de gel, pas de liftShorter, pas de re-filtrage par frappe côté client (le facteur ART 3-5× rend ce dernier point décisif sur la latence perçue).
- **Plan de reprise recommandé (inspiration, pas copie)** : (1) réécrire en Kotlin le modèle « chaîne de classifiers/weighers ordonnés + WeighingComparable paresseux » par-dessus l'API CompletionItem actuelle ; (2) y brancher prefix-degree (déjà présent via Grade), proximity (déjà présent), + liftShorter + frozen-items + trimToHalfLimit ; (3) adopter la frontière RPC : envoyer au client matcher + poids + présentation pour re-filtrage local par frappe ; (4) garder le pipeline ecj (splice/marqueur = même invention que DUMMY_IDENTIFIER, CompletionInitializationUtil.java:123) qui fonctionne et est testé.

Licence : Apache-2.0 (intellij-community) — l'inspiration/réécriture est propre ; toute reprise de code exigerait NOTICE + mention des modifications (R6.4).

## D8.7 Réponse à la question à trancher (§5)

**Transposables sur ART dans une architecture LSP tels quels (logique pure, zéro UI, vérifié par scan)** :
1. la chaîne de pesée extensible : `Weigher` + `WeighingComparable` + `CompletionSorterImpl` (classifiers chaînés, cache des poids) ;
2. `RealPrefixMatchingWeigher` (matchingDegree gradué camel-hump — déjà à moitié présent côté ecj via les Grade) ;
3. `PriorityWeigher` / `GroupingWeigher` (priorité explicite, groupes) ;
4. `LiftShorterItemsClassifier` + `PreferStartMatching` (heuristiques de présentation d'ordre) ;
5. le gel des items affichés + `trimToHalfLimit` + publicité « toutes les variantes ne sont pas montrées » + batchUpdate ;
6. le repli dumb-aware natif (`IndexNotReadyException` ignorée → contributors suivants) ;
7. le protocole de frontière calcul/présentation (modèle `RpcCompletionItem`/`RpcCompletionItemWeight`/`RpcPrefixMatcher` Frontend-vs-Backend) : le re-filtrage de préfixe côté client par frappe sans re-calcul serveur est LA réponse structurelle à « ART 3-5× plus lent » pour la latence perçue.

**Non transposables sans réécriture** : `StatisticsWeigher`/`PreferMostUsedWeigher` (pile statistics absente du sparse — à réécrire sur un simple compteur d'usage local persisté) ; `CompletionFinalSorter` ML-ranking (plugin tiers) ; Lookup UI/EDT/`CompletionProgressIndicator` (hors sparse) ; l'API Editor-centrique (paramètres/insertion) — à remplacer par les équivalents LSP (position, TextEdit).

# Conclusion transverse D7/D8

- La « puissance IDE » en index et complétion d'IntelliJ réside pour l'essentiel dans des **modèles algorithmiques** (index inversé multi-usage + forward index pour diff ; chaîne de weighers + arrangement incrémental + frontière RPC calcul/présentation) et non dans du code portable : les deux piles de persistance de l'index sont incompatibles ART (cleaner `sun.nio.ch.DirectBuffer` via MethodHandles pour la pile par défaut ; `java.lang.foreign` Arena/MemorySegment pour la pile durable), et la complétion est couplée Editor/PSI/statistics.
- L'index par segments (ADR 0111) **reste la bonne base** pour CodeIDE — il bat le design IntelliJ sur les axes mesurables mobiles (préfixe par recherche binaire vs énumération complète des clés ; budget 30 Mo vs page cache 600 Mo ; corruption par segment vs rebuild d'index entier). Les apports à hybridater : invalidation fine multi-index par unité (IndexingStamp), forward index pour updates différentielles, sémantique dumb-mode à deux niveaux.
- La complétion est le domaine où le rapport bénéfice/coût de l'inspiration est le plus élevé : réécrire la chaîne de classement (~1 500 lignes de logique pure) + adopter la frontière RPC (spécification de 2 340 lignes comme modèle) par-dessus le moteur ecj existant et l'index CIDX, sans embarquer un gramme de code IntelliJ.


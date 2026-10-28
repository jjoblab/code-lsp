# Annexe — Rapport brut exp-D11D12 (D11 concurrence, D12 mémoire)

> Task ID: exp-D11D12 · Agent: explore-D11D12 · Mode : lecture seule, aucun build.
> Reproduit tel que rendu par l'agent d'étude ; le document maître est `../01-architecture-psi.md`.
> **Note de provenance (R5)** : le bac à sable de la mission a été réinitialisé le 28/10/2026 (~10h13) ; ce rapport a été reconstruit le même jour depuis le contexte de mission (les citations `fichier:ligne` proviennent des lectures réelles effectuées sur le clone @ `865067b0` avant la réinitialisation ; le clone de référence a été recréé au même commit et des vérifications ponctuelles y ont été rejouées — journal : `worklog` de la mission). Traiter toute citation non re-vérifiée comme « à vérifier » au besoin.
- **Terrain** : `JetBrains/intellij-community` @ `865067b0a7f1a73ee0fa8bce4f042013c8ac334f` (clone sparse) ; ancien moteur `jjoblab/codeide-lsp` @ `cb809e1` (v1.16.0) + `jjoblab/codeide-tooling` @ `b60f7f0`.
- **Conventions** : `chemin:ligne` = position vérifiée dans le sparse au commit ci-dessus. Les fichiers marqués **[hors sparse]** n'étaient PAS dans le sparse-checkout (le prompt de mission annonçait ApplicationImpl « vérifié présent dans le sparse » : **écart constaté**) ; ils ont été lus **en lecture seule** via `git cat-file -p HEAD:<chemin>` (le clone est `--filter=blob:none`, l'arbre git est complet, aucun fichier du working tree n'a été modifié). Licences : sources IntelliJ Apache-2.0 (« JetBrains Open-Source Build Terms v1.3 », cf. worklog D1–D6).

# D11 — Concurrence : read/write actions, verrous, annulation

## 1. Question à trancher (§5 du prompt mission)

> « D11 — Concurrence : read/write actions, verrous, annulation. Comment remplacer le verrou global actuel ? »

Réponse courte : le verrou global `synchronized` d'`EcjCompilerEngine` doit être remplacé par une **combinaison** (i) verrouillage par périmètre (fichier ou module) — voie déjà entamée côté codeide-lsp (« moteur par module », commit a v0.97.0, `docs/LSP_JAVA.md:1030-1084`), (ii) **annulation coopérative avec priorité écrivain** à la `NonBlockingReadAction` d'IntelliJ, (iii) pour l'invalidation classpath, un **swap d'époque volatile** (le champ `volatile EcjCompilerEnvironment index` existe déjà, `EcjCompilerEngine.java:232`). Trois schémas détaillés en §7.

## 2. Le modèle de concurrence d'IntelliJ : trois couches

### 2.1 Couche API (core-api / util — dans le sparse, détachable)

| Élément | Chemin | Rôle |
|---|---|---|
| `Application` (interface) | `platform/core-api/src/com/intellij/openapi/application/Application.java` | contrat runReadAction/runWriteAction/invokeLater/executeOnPooledThread |
| `ApplicationManager` | `platform/core-api/src/com/intellij/openapi/application/ApplicationManager.java` | singleton statique `ourApplication` (1 env/JVM, cf. worklog D1–D2) |
| `ThreadingSupport` (interface, 412 l.) | `platform/core-api/src/com/intellij/openapi/application/ThreadingSupport.kt:15` | **l'abstraction du verrou RW** : runReadAction, runWriteActionBlocking, runWriteIntentReadAction, parallelizeLock, runWhenWriteActionIsCompleted… |
| `ReadWriteActionSupport` | `platform/core-api/src/com/intellij/openapi/application/ReadWriteActionSupport.kt:9-34` | variante suspend (coroutines) + `computeCancellableUnsafe` |
| `ReadAction` / `WriteAction` / `WriteIntentReadAction` | `platform/core-api/src/com/intellij/openapi/application/ReadAction.java` (221 l.), `WriteAction.java` (101 l.), `WriteIntentReadAction.java` (57 l.) | façades statiques ; `ReadAction.computeCancellableUnsafe` délègue au service `ReadWriteActionSupport` (`ReadAction.java:184-187`) |
| fonctions Kotlin | `platform/core-api/src/com/intellij/openapi/application/actions.kt:10-50` | `runWriteAction`, `runReadActionBlocking` ; l'ancien `runReadAction` est **déprécié** vers le bloquant (`actions.kt:26-27`) |
| `AccessToken` | `platform/util/src/com/intellij/openapi/application/AccessToken.java:8` | try-with-resources des acquisitions |
| `TransactionGuard(+Impl)` | `platform/core-api/src/com/intellij/openapi/application/TransactionGuard.java` ; `platform/core-impl/src/com/intellij/openapi/application/TransactionGuardImpl.java:22-119` | transactions d'écriture sur EDT + « write-safe modalities » ; notion **purement UI** (modalités Swing) |
| `ModalityState` | `platform/core-api/src/com/intellij/openapi/application/ModalityState.kt` + `modality.kt` | ordonnancement des invokeLater par modalité de dialogue ; **no-op en headless** |
| `NonBlockingReadAction` (API) | `platform/core-api/src/com/intellij/openapi/application/NonBlockingReadAction.java` | lecture en tâche de fond annulée par un écrivain en attente, puis re-exécutée |
| `AppUIExecutor` | `platform/core-api/src/com/intellij/openapi/application/AppUIExecutor.java` | exécution contrainte (EDT/WT, modalité, expiration) |

Points clés : la plateforme distingue **trois niveaux de permit** — lecture, *write-intent* (intention d'écrire, exclusif mais annulable), écriture — et un quatrième mode historique `runWriteIntentReadAction` (l'éditeur tape sous write-intent). Le verrou n'est jamais pris directement par le code applicatif : tout passe par `Application`/`ThreadingSupport`.

### 2.2 Couche implémentation IDE (platform-impl / locking-impl — **hors sparse**, lue via git cat-file)

- `platform/platform-impl/src/com/intellij/openapi/application/impl/ApplicationImpl.java` [hors sparse] (1 533 l.) : ne contient **plus aucun verrou** — il délègue tout à `NestedLocksThreadingSupport` via `ThreadingSupportHolder.getThreadingSupport()` (`ApplicationImpl.java:195`). `runReadAction` → `getThreadingSupport().runReadAction(...)` (`:1112-1124`), `runWriteAction` → `runWriteActionBlocking` + compteur de write-actions en fond (`:1172-1217`). `executeOnPooledThread` → `AppExecutorUtil.getAppExecutorService()` en avalant les `ProcessCanceledException` (`:380-400`).
- **`ReadMostlyRWLock` et `ClassCancellation` n'existent plus à ce commit** : `git ls-tree -r HEAD | rg "ReadMostlyRWLock|ClassCancellation"` → 0 résultat. Ils ont été remplacés par :
  - le module **`platform/locking-impl`** (`platform/locking-impl/src/NestedLocksThreadingSupport.kt` [hors sparse], 2 113 l.) — technique dite « nested locks » documentée en tête de fichier (`:117-179`) : un verrou RW **par niveau de parallélisation** (niveau 0 = verrou global, niveaux supérieurs créés quand on parallélise *dans* un permit déjà pris, p.ex. progression modale) ;
  - la mutex elle-même vient d'un **artefact externe publié** : `org.jetbrains.intellij.deps:rwmutex-idea:0.0.10` (déclaration `.idea/libraries/jetbrains_intellij_deps_rwmutex_idea.xml` [hors sparse], classes `com.intellij.core.rwmutex.RWMutexIdea/ReadPermit/WriteIntentPermit/WritePermit`).
- Mécanique de lecture (`NestedLocksThreadingSupport.kt`) :
  - `runReadAction` (`:905-950`) : rapide = `tryAcquireReadPermit` ; si un écrivain est **en attente**, le lecteur **relâche et attend** pour lui donner la priorité (`smartAcquireReadPermit`, `:826-878`, piloté par `stallReadActionsIfThereIsPendingWrite`) ; échec rapide → écouteurs `fastPathAcquisitionFailed` (mode « impatient reader » d'`ApplicationImpl.java:176-181` [hors sparse]) ; sinon **spin de 100 `Thread.yield()` avec `indicator.checkCanceled()`** puis acquisition bloquante (`:864-877`).
  - `tryRunReadAction` (`:952-997`) : non-bloquant, rend `false` si échec.
  - `runWriteActionBlocking` (`:1082-1110`) : d'abord permit *write-intent*, puis upgrade en *write* sur toute la pile de niveaux inférieurs (`upgradeWritePermit`, `:407-420`) — pour annuler les lectures de bas niveau ; compteur « pending write » par niveau (`myWriteActionPending`, `:274`) ; `CancellationException` convertie en `ProcessCanceledException` (`:1093-1102`).
  - **8 champs ThreadLocal** dans cette seule classe : `thisLevelPermit` (`:200`), `statesOfWIThread` (`:224`, EDT seulement), `myNoWriteActionCounter` (`:275`), `myReadActionsInThread` (`:277`), `myLockingProhibited` (`:278`), `myTopmostReadAction` (`:281`), `myLockInterceptor` (`:286`), `myWriteIntentAcquired` (`:293`).
  - le contexte de verrou voyage aussi **dans le contexte coroutine** (`ComputationStateContextElement`, `:562-579`) et se replie sur les ThreadLocal quand le contexte est réinitialisé (`getComputationState`, `:711-718`).
- `NonBlockingReadActionImpl` [hors sparse] (`platform/platform-impl/src/com/intellij/openapi/application/impl/NonBlockingReadActionImpl.java`, 1 141 l.) : c'est **le mécanisme à imiter**. Soumission → `attemptComputation` (`:778-820`) → `runInReadActionWithWriteActionPriority` ; si un écrivain arrive, l'indicateur est annulé → PCE → `rescheduleLater()` (`:822-826`) via `runWhenWriteActionIsCompleted` ; ré-exécution après l'écrivain. Coalescence par clé d'égalité (`coalesceBy`), expiration par `Disposable`/`expireWhen`. Variante synchrone `executeSynchronously` (`:661-728`) avec compensation anti-famine (`:665`). **VarHandle** sur `myCleaned` (`:345-354`).
- `ProgressManagerImpl` [hors sparse] (`platform/platform-impl/src/com/intellij/openapi/progress/impl/ProgressManagerImpl.java`, 372 l.) : sous-classe IDE de `CoreProgressManager` ; `runInReadActionWithWriteActionPriority` → `ProgressIndicatorUtils.runInReadActionWithWriteActionPriority` (`:251-253`).
- `ProgressIndicatorUtils` [hors sparse] (`platform/ide-core-impl/src/com/intellij/openapi/progress/util/ProgressIndicatorUtils.java`, 404 l.) : mécanisme historique (déprécié) : écoute `beforeWriteActionStart` → annule l'indicateur (`:203-211`), retry via invokeLater (`:187-240`) ; les nouvelles API renvoient à `ReadAction.computeCancellableUnsafe` (`:95-110`, `:126`).
- Files d'événements : `LaterInvocator` (sparse, `platform/core-impl/src/com/intellij/openapi/application/impl/LaterInvocator.java:51-119`) gère les piles de modalité et délègue à `NonBlockingFlushQueue` (sparse, `platform/core-impl/src/com/intellij/openapi/application/impl/NonBlockingFlushQueue.kt:24-90`) — la file qui **retarde les runnables write-intent tant que le verrou est pris** (automate `ALL`↔`UI_ONLY`, `:42-51`). L'ancien « flushPosts » d'ApplicationImpl a disparu au profit de ce couple ; les deux sont **couplés AWT/Swing** (`LaterInvocator.java:40-43` : `JFrame`, `Dialog`, `Window`).

### 2.3 Couche headless (dans le sparse — celle qui tournerait sur ART)

- `platform/core-impl/src/com/intellij/mock/MockApplication.kt` (311 l.) : `runReadAction`/`runWriteAction` = **exécution directe, aucun verrou** (`:148-170`) ; `assertReadAccessAllowed` etc. = no-op (`:102-115`) ; `isReadAccessAllowed()=true` (`:120-122`) ; `invokeLater` → `SwingUtilities.invokeLater` (`:193-211`) — **dépendance javax.swing** ; `executeOnPooledThread` → `AppExecutorUtil` (`:130-132`).
- `platform/core-impl/src/com/intellij/mock/MockThreadingSupport.kt` (139 l.) : `ThreadingSupport` « lock-free » — tout est trivial (`runWriteActionBlocking = computation()` `:39`). **Le socle headless est mono-thread par construction** : la protection est assurée par la discipline d'appel (un seul thread), pas par des verrous.
- `CoreApplicationEnvironment` (377 l.) monte ce Mock via `ApplicationManager.setApplication` (cf. D1–D2).

**Conséquence majeure pour l'étude** : embarquer la pile headless (voie D1) n'apporte **aucune primitive de concurrence utile** — tout le verrouillage intelligent (RW, priorité écrivain, NBRA) vit dans `platform-impl`/`locking-impl`, modules gros et couplés EDT/coroutines. La valeur pour CodeIDE est **le design**, pas le binaire.

## 3. Annulation : comment elle se propage

- **Token moderne = Job coroutine** : `platform/util/src/com/intellij/openapi/progress/Cancellation.java` (250 l.) — `currentJob() = ThreadContext.currentThreadContext().get(Job.Key)` (`:26-28`) ; `checkCancelled()` → `ensureActive(job)` qui transforme `java.util.concurrent.CancellationException` en `CeProcessCanceledException` (`:63-78`) ; sections non-annulables = contexte + `NonCancellable.INSTANCE` installé par `ThreadContext.installThreadContext` (`:159-190`).
- Le thread-contexte lui-même est un **ThreadLocal maison** : `platform/util/src/com/intellij/concurrency/threadContext.kt:95-97` (`ThreadLocal<InstalledThreadContext>`), interfacé avec **`kotlinx.coroutines.internal.intellij.IntellijCoroutines`** (`:38-48`, `:99-109`) — c'est-à-dire le **fork JetBrains de kotlinx.coroutines** (champ d'API non standard).
- `CoreProgressManager` (sparse, `platform/core-impl/src/com/intellij/openapi/progress/impl/CoreProgressManager.java`, 1 374 l.) : `checkCanceled()` → `doCheckCanceled()` → `Cancellation.currentJob()/ensureActive` (`:205-246`) ; machinerie d'indicateurs historiques : maps **statiques** `threadsUnderIndicator` (HashMap synchronisée) et `currentIndicators` (ConcurrentLongObjectMap thread-id→indicateur) (`:96-102`) ; ping des indicateurs non standard toutes les **10 ms** sur le scheduler global (`:89`, `:159-174`) ; `checkCanceled` ne consulte l'indicateur que si un hook ou un indicateur annulé existe (`CheckCanceledBehavior`, `:116-142`).
- Dans le parse/résolution : `BlockSupportImpl` appelle `checkCanceled()` (vérifié D3) — l'annulation remonte donc **par exception PCE** aux points de contrôle, pas par interruption.
- **Héritage headless** : `CoreProgressManager.runInReadActionWithWriteActionPriority` = simple `runReadAction` + `true` (`:783-786`) — la variante headless n'annule pas sur écrivain (il n'y a pas d'écrivain concurrent).
- `AppScheduledExecutorService` (sparse, `platform/util/src/com/intellij/util/concurrency/AppScheduledExecutorService.java`, 340 l.) : **singleton JVM** via Holder (`:49-59`), pool non borné (SynchronousQueue, keepAlive 1 min, priorité `NORM_PRIORITY-1` `:117`) ; thread « periodic tasks » dédié (`:263-265`) ; shutdown interdit (`:145-147`) ; **possède le `LowMemoryWatcherManager`** sauf en headless (`:66-68`, `:86-88`). `JobScheduler.getScheduler()` (core-api/src/com/intellij/concurrency/JobScheduler.java:23-25) renvoie au même service.
- Coroutines : `EdtCoroutineDispatcher.kt` (sparse, 198 l.) définit 4 dispatchers (`EDT`, `LAX_UI`, `UI`, `MAIN`) avec politiques de verrou et modalité par défaut (`:141-192`), dispatch via `ApplicationManagerEx.dispatchCoroutineOnEDT` (`:57`). Densité coroutine (imports `kotlinx.coroutines`, fichiers concernés / total) : core-api 36/884, core-impl 28/694, util 16/768, **lang-impl/backend 23/143 (16 %)**, indexing-impl 4/171, java-psi-api **0/394**, java-psi-impl **1/645**, java-syntax **0/36** — le cœur Java-PSI est coroutine-free, la glue plateforme en est tissée.

## 4. Le statu quo : moteur ecj actuel (codeide-lsp v1.16.0)

- `lsp/java/src/main/java/jo/lspjava/ecj/EcjCompilerEngine.java` (2 417 l.) — comptages réels :
  - `rg -c "public synchronized"` → **17 méthodes** ;
  - `rg -n "^\s+synchronized [A-Z]"` + la ligne 1552 (`synchronized org.eclipse.jdt...classFileReader`) → **7 méthodes package-private** ;
  - `rg -c "^\s+synchronized \("` → **2 blocs** (`synchronized (this)` `:1396`, `synchronized (indexLock)` `:1484`) ;
  - soit **24 méthodes synchronized + 2 blocs** (le worklog D1–D6 disait « 19 public + 2 blocs » — écart : 17 public réels, 24 au total).
  - Le pivot : `resolveUnit` (`:660`, `:672`) « point de passage unique » (javadoc `:638-649`) protégeant `EcjParseCache` (non thread-safe) et `EcjCompilerEnvironment` (jars partagés). Le verrou est **réentrant**, donc pas de surcoût d'imbrication.
- `lsp/java/src/main/java/jo/lspjava/engine/EcjEngineScheduler.java` (487 l.) : 3 lanes mono-thread (`interactive` timeout 3 s, `analysis` 15 s, `diagnostics` 15 s, `:72-75`, `:158-160`) ; **supersession par fichier** (`ConcurrentMap.put` + `cancel(false)` avant exécution, `:186-191`, `:245-250`) ; watchdogs sur un `ScheduledExecutorService` mono-thread partagé (`:116-121`) ; `Future` maison à `AtomicReference<State>` + CAS + `notifyAll` (`:410-484`) ; reset `Thread.interrupted()` après chaque tâche (`:393`). Utilisé depuis `JavaTextDocumentService` (`:407`, `:954`, `:1102`, `:1350`, `:1997`).
- Annulation actuelle : `engine/CancelToken.java` (130 l.) — deadline nanos + drapeau `volatile` + `Thread.interrupt()` (`:92-110`) ; branché dans le compilateur via `resolveCancelHook` (`EcjCompilerEngine.java:246`, posé `:727`, contrôle post-phase `:734-738`) ; `OperationCanceledException` (RuntimeException, `engine/OperationCanceledException.java:37`) attrapée par les services (p.ex. `services/DiagnosticService.java:92`).
- Ordonnancement LSP : debounce diagnostics **50 ms** (`lsp/JavaTextDocumentService.java:129`) + debounce client non garanti (`:114`).
- Latence subie (mesures bureau `docs/LSP_JAVA.md:168-177`) : frappe (complétion+diagnose) p50/p95 = 30/64 ms (20 jars) → 69/160 (73) → 128/150 ms (250 jars) ; ART **3–5× plus lent** (`LSP_JAVA.md:188-189`) ⇒ sur appareil, p95 de frappe entre ~200 et ~750 ms. W8 confirmé : « UNE complétion du module A bloque le module B » (`LSP_JAVA.md:1032-1035`) — atténué depuis par le moteur par module livré en commit a v0.97.0 (carte `ConcurrentHashMap` de contextes par module, ordre de verrou `this` → contexte, `LSP_JAVA.md:1065-1084`).

## 5. APIs sensibles ART dans le périmètre D11 (comptages réels, sparse)

Commande type : `rg -c "<pattern>" --glob "*.java" --glob "*.kt" platform java` (occurrences/fichiers, tout le sparse) :

| API | Occurrences | Fichiers | Points chauds pertinents D11 |
|---|---:|---:|---|
| `java.awt.` | 1 338 | 447 | `EDT.java:14-17` (EventQueue+Toolkit), `LaterInvocator.java:40-43`, `ThreadingAssertions.java:19` |
| `javax.swing.` | 1 567 | 667 | `MockApplication.kt:35` (SwingUtilities), `TransactionGuardImpl` (via EDT), `EdtCoroutineDispatcher` |
| `VarHandle` | 309 | 48 | `NonBlockingReadActionImpl.java:345-354` [hors sparse], AST (D3) |
| `MethodHandles` | 102 | 34 | `EDT.java:18-19,109-121` (dispatchEvent), `MethodHandleCache` (D1) |
| `sun.misc.Unsafe` | 2 | 2 | `platform/util/src/com/intellij/util/containers/Unsafe.java` (conteneurs concurrents) |
| `Class.forName` | 43 | 37 | `MapIndexStorageCache.kt:75` (provider), `ExtensionRegistryHolderJvm` (D1) |
| `java.util.ServiceLoader` | 6 | 6 | `MapIndexStorageCache.kt:83` (fallback provider index) |
| `jdk.internal` | 15 | 14 | conteneurs concurrents (`ConcurrentLongObjectHashMap`, etc.), `ThreadDumper` |
| `java.lang.management`/`javax.management` | 8 fich. | 8 | `CoreProgressManager.java:58-59` (ThreadInfo, diagnostics), `LowMemoryWatcherManager` (D12) |
| `ThreadLocal` (occurrences) | — | — | core-api 22, core-impl 41, **util 98**, java-psi-api 19, indexing-impl 16, java-psi-impl 13, java-syntax 3 |

Détachabilité : la **couche API** (core-api) est détachable si l'on fournit (a) un `ThreadingSupport` maison (le contrat est pur, 412 l., zéro AWT direct — l'AWT n'arrive que dans les implémentations), (b) un shim `java.awt.EventQueue.isDispatchThread()` (le seam `EDT.ourIsCurrentThreadEDT`, `EDT.java:36-37`, n'a **pas** de setter public — il faudra patcher ou shimmer la classe), (c) éventuellement kotlinx-coroutines (ou n'en pas dépendre : java-psi-* s'en passe). La **couche IDE** (platform-impl + locking-impl) n'est pas détachable (EDT fort, coroutines denses, VarHandle, Telemetry/OTel, MessageBus).

## 6. Ordres de grandeur D11

- Tailles de fichiers clés : `NestedLocksThreadingSupport.kt` 2 113 l. ; `NonBlockingReadActionImpl.java` 1 141 l. ; `CoreProgressManager.java` 1 374 l. ; `ThreadingSupport.kt` 412 l. ; `ApplicationImpl.java` 1 533 l. ; `AppScheduledExecutorService.java` 340 l. ; `threadContext.kt` 518 l. ; `propagation.kt` 621 l.
- Côté codeide-lsp : `EcjCompilerEngine.java` 2 417 l. ; `EcjEngineScheduler.java` 487 l. ; `CancelToken.java` 130 l. — l'ordonnanceur maison (~620 l. avec CancelToken) couvre déjà ~60 % de la sémantique utile de `NonBlockingReadActionImpl` (supersession, timeout, lanes).

## 7. Verdict D11 + trois schémas de remplacement du verrou global

**Verdict : S'INSPIRER (pas porter, pas embarquer).** Le verrouillage RW complet d'IntelliJ (nested locks, niveaux, write-intent, modalités, EDT) est indissociable de son modèle UI et de ses coroutines ; son transfert sur ART coûterait plus qu'il ne rapporte. En revanche, trois de ses **invariants** sont transposables et directement opposables au verrou global ecj :
1. *les lecteurs sont la majorité, l'écrivain est rare et prioritaire* (read cheap, write exclusive) — `smartAcquireReadPermit` `NestedLocksThreadingSupport.kt:826-878` ;
2. *annuler-c'est-rejouer* : une lecture coupée par un écrivain est re-soumise après lui (`NonBlockingReadActionImpl.java:822-826`) ;
3. *le compteur d'époque remplace le verrou d'invalidation* (`PsiModificationTracker`, D5 ; `volatile` index côté ecj).

### Schéma A — RW multiple-lecteurs + file unique écrivain (calque IntelliJ)
- Un `RWMutex` global (artifact `rwmutex-idea` 0.0.10 publié, ou `ReentrantReadWriteLock` JDK, suffisant) : complétion/hover/diagnostics = lecteurs simultanés ; invalidation classpath, `dispose`, remplacement de contexte = écrivains.
- Annulation des lecteurs pendant qu'un écrivain attend : réutiliser `CancelToken` + supersession de l'`EcjEngineScheduler` ; re-soumission post-écrivain via une file `runWhenWriteActionIsCompleted` équivalente (une `ConcurrentLinkedQueue` + signal suffit).
- **Coût** : tout état partagé doit devenir thread-safe en lecture (l'AST ecj d'un fichier ne l'est pas → il faut soit verrou par arbre, soit snapshots immuables) ; le pool de 24 poignées `RawZipReader` et `EcjCompilerEnvironment` doivent être audités. Gain : parallélisme inter-fichiers des requêtes interactives.
- Applicable surtout si on veut plusieurs fichiers **au sein d'un même module** en parallèle — cas rare en frappe (un seul fichier édité), fréquent en rename/find-refs (W4 : walks multi-fichiers).

### Schéma B — verrous par périmètre (fichier ou module), écrivain rare (recommandé en premier)
- Remplacer le moniteur `this` par une `ConcurrentMap<String, ReentrantLock>` (ou verrous *striped* à ~64 seaux sur le hash du chemin — `FixedSizeLocks` n'existe plus dans le repo, à écrire en 30 lignes). Le « moteur par module » déjà livré (commit a v0.97.0, `LSP_JAVA.md:1065-1084`) est le premier étage ; le second étage est le verrou **par fichier** autour de `resolveUnit`/`EcjParseCache` (qui reste non thread-safe, protégé par son verrou fichier).
- Conditions : (i) `EcjCompilerEnvironment`/`typeIndex`/`binaryNames` passent sous un verrou séparé (déjà amorcé : `indexLock` `EcjCompilerEngine.java:1484`) ou deviennent immuables-échangés (voir C) ; (ii) `EcjLocateCache` est déjà `ReentrantLock` (`EcjLocateCache.java:46`) — OK ; (iii) compte tenu d'ART 3–5×, plafonner le nombre de requêtes réellement parallèles à 2–3 workers (le pool L4J est un cachedThreadPool, à borner).
- Gain direct : une diagnose du fichier A ne bloque plus la complétion du fichier B ; latence de frappe inchangée sur le fichier courant (déjà sérialisée, à juste titre).
- Risque : éclosion de verrous imbriqués (fichier → index) → **imposer un ordre global** (fichier → module → index), comme le fait déjà le commit a (« jamais l'inverse », `LSP_JAVA.md:1073`).

### Schéma C — époque/génération (copy-on-write de l'environnement)
- Un compteur `volatile long epoch` bumpé à chaque changement de classpath/modèle ; les lecteurs capturent l'époque au départ, vérifient à la publication, et rejettent par `OperationCanceledException` si périmé (l'équivalent exact de `PsiModificationTracker` d'IntelliJ, mais au périmètre classpath — D5 a montré que le compteur global PSI d'IntelliJ est trop grossier pour mobile ; ici il serait bien ciblé).
- L'écrivain construit le nouvel `EcjCompilerEnvironment` en tâche de fond puis **swap atomique** du champ `volatile index` (`EcjCompilerEngine.java:232` — le champ est déjà volatile, il ne manque que l'époque et la re-validation). Les caches (`EcjParseCache.invalidate()`, `EcjLocateCache.clear()`) sont vidés au moment du swap.
- Gain : plus aucun verrou de lecture sur l'environnement ; l'invalidation W6/W7 (empreintes par contenu) se greffe naturellement sur l'époque.
- Coût : pendant la reconstruction, double mémoire de l'index (transitoire) ; discipline « re-valide avant de publier » à injecter dans chaque service.

**Recommandation** : B puis C sont cumulables et à armes égales avec le statu quo (coût : ~quelques centaines de lignes ; bénéfice mesurable : suppression du blocage inter-fichiers/inter-modules et de l'attente derrière les reconstructs W1). A n'apporte de gain supplémentaire que si le multi-fichiers concurrent devient un cas d'usage dominant (rename/find-refs à 250 jars) — à mesurer avant.

---

# D12 — Mémoire : références faibles/molles, caches bornés, GC sur ART

## 1. Question à trancher (§5 du prompt mission)

> « D12 — Mémoire : références faibles/molles, caches bornés. Empreinte et comportement du GC sur ART. »

Réponse courte : le modèle IntelliJ est « **la valeur vit sur l'élément, meurt avec lui ou avec la mémoire** » (SoftReference généralisé + file de références mortes + caches bornés SLRU), complété par des caches d'index à budget en *entrées* (1024/index par défaut) et un signal mémoire central (`LowMemoryWatcher`) **indisponible sur ART**. Le moteur actuel codeide-lsp n'utilise **aucune référence faible/molle** (caches forts LRU) mais a déjà un budget *octets* explicite côté index disque. Verdict : **s'inspirer du premier (soft-références + files mortes), garder le second (budgets octets), remplacer LowMemoryWatcher par un shim Android (`ComponentCallbacks2.onTrimMemory`)**.

## 2. Le modèle mémoire d'IntelliJ

### 2.1 CachedValues — la valeur stockée sur l'élément
- `platform/core-impl/src/com/intellij/util/CachedValuesManagerImpl.java` (159 l.) : la valeur cachée est posée **dans le UserDataHolder lui-même** (`dataHolder.getUserData(key)` `:87`, `putUserDataIfAbsent` `:104-105`) ; le manager ne garde qu'une `ConcurrentWeakIdentityMap` de holders (pour le déchargement de plugins, `:32`, `:120-125`, `:133-135` — PsiElement/ASTNode/FileViewProvider exclus `:133-135`).
- `platform/core-impl/src/com/intellij/psi/impl/PsiCachedValueImpl.kt` (153 l.) : quatre variantes — `Soft`/`SoftTracked` (données en `SoftReference`, `:62-104`, la variante recommandée, l'ancienne classe est `@Deprecated` `:27-37`) et `Direct`/`DirectTracked` (référence forte, `:106-152`).
- `platform/core-impl/src/com/intellij/util/CachedValueBase.java` (346 l.) : invalidation **par dépendances + horodatages** (`:108-133`, `getTimeStamp` gère VirtualFile/ModificationTracker/`Reference` (dépendance faible !)/Document `:155-183`) ; cas optimisé dépendance-unique au `PsiModificationTracker` (`:60-62`, `PsiDependentData` `:248-268`) ; verrou **par valeur** (`synchronized` sur l'instance pour `cacheOrGetData`/`setRawData`, `:67-79`) + `RecursionManager` contre les récursions (`:302-309`).
- **Où vit la valeur, quand meurt-elle ?** : dans le `CachedValue` posé en user-data de l'élément → elle meurt (a) quand l'élément meurt (le PSI n'est plus référencé), (b) quand le GC vide la SoftReference sous pression, (c) quand une dépendance change (re-calcul, pas de fuite).

### 2.2 ResolveCache — 8 maps weak-key/soft-value
- `platform/core-impl/src/com/intellij/psi/impl/source/resolve/ResolveCache.java` (344 l.) : `AtomicReferenceArray<Map>(8)` (`:38`, physique×incomplet×poly) ; chaque map = `ConcurrentWeakKeySoftValueHashMap` (`:96-124`) — **clés faibles** (la PsiReference), **valeurs molles**, `NULL_RESULT` en référence forte ; vidage **total** sur `ANY_PSI_CHANGE_TOPIC.beforePsiChanged` (`:50-57`) et sur `LowMemoryWatcher` (`:47`, `:59-61`) ; vidage amorti par progression (drop toutes les 500 ms pendant les scans batch, `PsiManagerImpl.java:619-629`).
- La classe utilitaire : `platform/util/base/src/com/intellij/util/containers/ConcurrentWeakKeySoftValueHashMap.java:29-47` — `ConcurrentHashMap` de `WeakKey`/`SoftValue` **+ 2 ReferenceQueues** (clé `:31`, valeur `:32`) ; dépréciée au profit de `ContainerUtil.createConcurrentWeakKeySoftValueMap()`.

### 2.3 SmartPointers — pointeurs stables à travers reparse + file morte
- `platform/core-impl/src/com/intellij/psi/impl/smartPointers/SmartPointerManagerImpl.java` (289 l.) : trackers **par fichier** dans une `ConcurrentWeakValueMap` (VirtualFile→tracker, `:54` — le tracker meurt avec le fichier) ; set de light-trackers sur `WeakHashMap` (`:56`) ; pointeur mis en cache en SoftReference dans le user-data de l'élément (`:124`) avec compteur de références (`:113-117`, `:186-200`).
- `platform/core-impl/src/com/intellij/psi/impl/smartPointers/SmartPointerTracker.kt` (561 l.) : **la « file morte »** = `ReferenceQueue` statique (`ourQueue`, `:539`) ; chaque pointeur est enveloppé dans un `PointerReference extends WeakReference(pointer, ourQueue)` (`:416-432`) ; `processQueue()` retire les entrées mortes (`:551-559`), appelé aux points de création (`SmartPointerManagerImpl.java:111,170`) et **sur signal LowMemoryWatcher** (`:541-546`) ; `WeakPointerReferenceList` à retrait O(1) par index stocké (`:451-536`) ; marqueurs de plage mis à jour aux DocumentEvents (`updateMarkers` `:109-114`) et re-validés après reparse (`updatePointerTargetsAfterReparse` `:150-157`).
- Sémantique : les pointeurs utilisateurs sont **faibles** ; les `SelfElementInfo` restent dures dans le `MarkerCache` et sont purgées à la collecte du pointeur (doc `:25-33`).

### 2.4 Le fichier PSI lui-même : FileTrees
- `platform/core-impl/src/com/intellij/psi/impl/source/FileTrees.java` (412 l.) : trois états — stub seul (le moins cher), AST seul, les deux (« green stub ») (doc `:43-53`) ; **le StubTree est en `Reference<StubTree>` (soft/weak)** (`:77-78`) ; **l'AST est en `ConcreteAstPointer`** = `SoftReference` pour fichiers physiques, `PatchedWeakReference` en mode batch, forte sinon (`PsiFileImpl.java:981-988`) ; identité PSI préservée par `myRefToPsi` = tableau de `WeakReference` vers les PSI déjà distribués (`FileTrees.java:94-101`).
- Clé ART importante : `PsiManagerImpl.runInBatchFilesMode` fait basculer **toutes** les rétentions soft→weak pendant l'indexation batch (`PsiManagerImpl.java:590-603` ; usages `PsiFileImpl.java:985`, `SmartPsiElementPointerImpl.java:117-121`) — un « robinet mémoire » global de 30 lignes, transposable tel quel au moteur ecj (le « mode batch » = indexation des jars).

### 2.5 Caches d'index résidents
- `platform/lang-impl/backend/src/com/intellij/psi/stubs/StubIndexImpl.java` : simple `Map<StubIndexKey, UpdatableIndex>` (`:82`) — **aucun StubTree résident ici** ; tout passe par `FileBasedIndex`/`MapIndexStorage`.
- Taille par défaut : `platform/indexing-api/src/com/intellij/util/indexing/FileBasedIndexExtension.java:56` — `DEFAULT_CACHE_SIZE = 1024` **entrées par index**.
- Cache de stockage : `platform/util/src/com/intellij/util/indexing/impl/MapIndexStorageCache.kt` — fournisseur par défaut **SLRU** (`SlruIndexStorageCacheProvider` `:96-130`) : file protégée = cacheSize, file probatoire = cacheSize/4 (`:147`, `:190-191`) ; fournisseur alternatif **Caffeine** (`platform/indexing-impl/src/com/intellij/util/indexing/impl/storageCacheProviders.kt:81-86`) `maximumSize(cacheSize)` avec **rétro-persistance à l'éviction** (`evictedValuesPersister`, `:65-71`) ; sélection par propriété système `Class.forName` ou `ServiceLoader` (`MapIndexStorageCache.kt:68-91`).
- `KeyHashLog` (indexing-impl) garde son état en `SoftReference<InternalState>` (`:391-394`).
- Stubs disque et `LazyStubList` : déjà couverts en D4 — pas de reprise ici.

### 2.6 Signal mémoire : LowMemoryWatcher — absent d'ART
- `platform/util/src/com/intellij/openapi/util/LowMemoryWatcherManager.java` : imports `javax.management.Notification/NotificationEmitter/NotificationListener` **et** `java.lang.management.*` (`:11-18`) ; écoute `ManagementFactory.getMemoryMXBean()` (`:156-168`, retrait `:298`) ; callback GC-observant (`:95`), purge sur surcharge GC (`:145`).
- **Confirmé : java.lang.management/javax.management sont absents d'ART** ; or le mécanisme d'IntelliJ **dépend** de ces API (`LowMemoryWatcherManager.java:156-168`).
- **Le headless ne l'active pas** : `AppScheduledExecutorService.configureForHeadlessProcess()` (`:66-68`) + init conditionnel (`:86-88`) → la pile headless sur JVM ne démarre pas le watcher. Sur ART il faudra un **shim** : `LowMemoryWatcher.register` peut être réimplémenté sur `android.content.ComponentCallbacks2.onTrimMemory` (API Android standard) — substitution de ~100 lignes, à la discrétion du planificateur.
- Usages du signal dans le sparse : `ResolveCache.java:47`, `SmartPointerTracker.kt:544` (+ autres). C'est le **seul** mécanisme réactif à la mémoire de la plateforme — pas d'`onLowMemory` Android, pas de commentaire spécifique ART/GC dans le code lu.

### 2.7 Boîte à outils références (plateforme/util)
- `com.intellij.reference.SoftReference` (`platform/util/base/src/com/intellij/reference/SoftReference.java:15-38`) + `dereference` : wrapper de debug (bascule dur/mou, javadoc `:9-13`) — **toutes** les SoftReference d'IntelliJ passent par là.
- Conteneurs faibles : `ConcurrentWeakKeySoftValueHashMap`, `ConcurrentIntKeySoftValueHashMap`, `ConcurrentIntKeyWeakValueHashMap`, `IntKeyWeakValueHashMap`, `ConcurrentWeakValueMap`… (dossier `platform/util/src/com/intellij/util/containers/`) — tous avec ReferenceQueue intégrée.
- Caches LRU : `IntObjectCache` (`platform/util/src/com/intellij/util/containers/IntObjectCache.java`, 390 l., défaut **8192** entrées `:13`, retrait O(1) par indices stockés) ; famille SLRU `platform/util/src/com/intellij/util/containers/intcaches/` (`SLRUIntObjectCache.java:21-31` files protégée/probatoire) ; `ThreadLocalCachedValue` (`platform/util/src/com/intellij/openapi/util/ThreadLocalCachedValue.java:11` — ThreadLocal<SoftReference>) ; `TimeoutCachedValue` (TTL synchronisé, `TimeoutCachedValue.java:21-32`).
- L'ancienne `ObjectCache` générique n'existe plus (remplacée par les familles ci-dessus).

## 3. Comptages D12 (commandes réelles, sparse)

| Module | SoftReference | Weak(Ref+WeakHashMap) | ThreadLocal |
|---|---:|---:|---:|
| platform/core-api | 5 | 5 | 22 |
| platform/core-impl | 13 | 45 | 41 |
| platform/util | 12 | 29 | **98** |
| platform/lang-impl/backend | 0 | 4 | 4 |
| platform/indexing-impl | 1 | 0 | 16 |
| platform/indexing-api | 0 | 0 | 0 |
| java/java-psi-api | 0 | 0 | 19 |
| java/java-psi-impl | 12 | 3 | 13 |
| java/java-syntax | 0 | 0 | 3 |

`java.lang.management|javax.management` (sparse entier) : **8 fichiers** — `LowMemoryWatcherManager.java` (8 occ.), `CoreProgressManager.java` (2, ThreadInfo diagnostics), `ThreadDumper.java` (3), `ThreadDump.java` (1), `MemoryDumpHelper.java` (3), `DebugAttachDetectorArgs.java` (1), + 2 fichiers de test. **Densité du pattern Soft/Weak : c'est un idiome structurel de la plateforme** (≈ 31 usages SoftReference + ≈ 86 weak dans les 4 modules principaux) ; à l'inverse le moteur ecj actuel en a **zéro** (voir §4).

## 4. Le statu quo mémoire : moteur ecj + index codeide-lsp

- `lsp/java/src/main/java/jo/lspjava/ecj/EcjParseCache.java` (189 l.) : LRU **8 entrées**, `LinkedHashMap(accessOrder=true)` + `removeEldestEntry` (`:106-112`), clé `(chemin, longueur, FNV-1a 64)` (`:63-85`, `:176-188`) ; **références fortes** (l'arbre `CompilationUnitDeclaration` est gardé dur) ; borné **en entrées, pas en octets** (javadoc `:39-44` : ~100 ko/entrée estimés, 8 ≈ 800 ko) ; non thread-safe par conception (`:44-48`) — protégé par le verrou global.
- `lsp/java/src/main/java/jo/lspjava/ecj/EcjLocateCache.java` (164 l.) : LRU **8 192 entrées** (`:38`), sentinelle négative `NOT_FOUND` (`:44`), `ReentrantLock` avec sonde hors verrou (`:46`, `:69-89`) ; références fortes.
- Index disque `lsp/index/src/main/kotlin/jo/codeide/lsp/index/CacheSegments.kt` (147 l.) : LRU **budget OCTETS explicite** (`budgetOctets`, éviction jusqu'au budget `:125-138`), synchronized, refus de corruption mémorisé (`:61-78`), toucher disque amorti 10 min (`:90-97`, `:145`) — cible ADR 0111 §6 **< 30 Mo pour 100 jars**.
- `HeapMonitor.java` dans `jo/lspjava` : **ABSENT** (Glob sans résultat) ; le vrai moniteur est `codeide-tooling/tooling/server/src/main/kotlin/jo/codeide/tooling/server/HeapMonitor.kt` (64 l.) : `Runtime.getRuntime()` seul (`:48-55`) — **compatible ART** (pas de java.lang.management) ; tick coroutine périodique borné par `--heap-intervalle-ms`.
- Budgets mesurés (bureau, `docs/LSP_JAVA.md:168-182`) : tas après index/stable = **9/25 Mo** (20 jars), **19/36** (73), **108/131** (250) — le budget snapshot **2,5 Mo/jar** (W2, `LSP_JAVA.md:131`) est le principal responsable du dépassement ; `dispose()` ramène à ~13 Mo et 28 poignées (`:182`) ; ART GC plus coûteux (`LSP_JAVA.md:189`).

## 5. APIs sensibles ART dans le périmètre D12

- `java.lang.management`/`javax.management` : voir §2.6 — **le seul point dur réel** ; shim `onTrimMemory` requis si on reprend le pattern LowMemoryWatcher.
- ThreadLocal dense : 98 occurrences dans platform/util (dont `threadContext.kt:95`, `Cancellation.java:89`, `NestedLocksThreadingSupport.kt` 8 champs) — ThreadLocal **existe sur ART** ; le coût est la pression mémoire par thread et le piège des fuites avec pools de threads ; pas un bloqueur.
- Références Soft/Weak/ReferenceQueue : API `java.lang.ref` **présente sur ART** ; le comportement d'éviction des SoftReference sous la politique GC d'ART est **différent de la JVM HotSpot** — **« à vérifier sur appareil »** (aucune donnée dans les dépôts lus ; aucun commentaire GC-related spécifique trouvé dans le code IntelliJ lu).
- Aucun `sun.misc.Unsafe` dans le chemin D12 (2 occurrences au total dans le sparse : `util/containers/Unsafe.java` pour les maps concurrentes, et une inspection java-analysis-impl).

## 6. Verdict D12

**S'INSPIRER — fortement — sans embarquer.**
1. **Pattern « valeur sur l'élément + SoftReference »** : le moteur actuel garde les arbres ecj en dur dans un LRU 8 entrées ; passer `EcjParseCache` à des valeurs molles (avec `ReferenceQueue` + purge paresseuse comme `ConcurrentWeakKeySoftValueHashMap`) alignerait le comportement sur le GC : sous pression ART, l'arbre d'un fichier non focalisé s'évanouit au lieu de provoquer un dépassement du LRU borné en entrées (hétérogènes en octets). Complément IntelliJ à reprendre tel quel : le **mode batch soft→weak** (`PsiManagerImpl.java:590-603`) appliqué à l'indexation des jars.
2. **Budgets octets plutôt qu'entrées** : conserver l'approche `CacheSegments` (budget explicite, mesuré) et l'étendre au parse-cache (budget octets avec estimation par taille de source — l'estimation ~100 ko/500 lignes du javadoc `EcjParseCache.java:41-43` est à re-mesurer sur ART).
3. **Invalidation par dépendances** : le modèle `CachedValueBase` (dépendances + horodatages, cas mono-dépendance optimisé) est la réponse propre à l'invalidation « grossière » W8 : chaque cache porterait ses dépendances (classpath-époque, contenu fichier, index-ready) au lieu du vidage total actuel. La version « 8 maps vidées sur ANY_PSI_CHANGE » d'IntelliJ est au contraire l'exemple **à ne pas** copier (D5 l'a montré coûteux).
4. **LowMemoryWatcher** : à réimplémenter sur `ComponentCallbacks2.onTrimMemory` (~100 lignes) — la pile headless n'en dépend pas (elle ne le démarre pas, `AppScheduledExecutorService.java:86-88`), donc ce n'est **pas** un bloqueur pour la voie embarquée V2 ; c'est un ajout pour la voie inspiration.
5. Comportement GC ART : **à vérifier sur appareil** (fréquence d'éviction des SoftReference, coût des ReferenceQueue, effet du LRU d'entrées vs octets sous mémoire contrainte). Le banc `HeapMonitor` actuel (Runtime-only) est compatible ART et sert de base de mesure.

## 7. Réponses explicites aux questions du §5

- **D11 « comment remplacer le verrou global actuel ? »** — voir §7 de D11 : trois schémas (A RW multiple-lecteurs + file écrivain ; B verrous par périmètre fichier/module — recommandé ; C époque volatile + swap copy-on-write). Aucun n'exige d'embarquer platform-impl : le contrat `ThreadingSupport` (412 l., pur) peut servir de gabarit d'interface. Le scheduler actuel (supersession, lanes, watchdogs) couvre déjà la sémantique d'annulation utile ; ce qui manque est (i) la granularité de verrou, (ii) la priorité écrivain + rejeu, (iii) l'époque d'invalidation.
- **D12 « empreinte et comportement du GC sur ART »** — l'empreinte IntelliJ hors index est pilotée par des SoftReference (valeur sur l'élément) : elle s'adapte théoriquement à la mémoire disponible ; l'empreinte index est bornée en **entrées** (1024/index, SLRU) — borne à re-qualifier en octets pour ART. Le signal mémoire est à reshimmer (`onTrimMemory`). Le comportement GC précis sur ART est non mesuré : **à vérifier sur appareil** (spike Phase 2).

## 8. Écarts constatés vs le prompt de mission (à consigner)

1. `ApplicationImpl`, `ProgressManagerImpl`, `NonBlockingReadActionImpl`, `ProgressIndicatorUtils` et tout `platform/platform-impl` **ne sont pas dans le sparse-checkout** (le prompt les disait « vérifiés présents ») ; lus via `git cat-file` (lecture seule, aucun impact sur le working tree).
2. `ReadMostlyRWLock` et `ClassCancellation` **n'existent plus** à ce commit (remplacés par `platform/locking-impl` + artefact `rwmutex-idea:0.0.10`).
3. `ObjectCache.java` (générique) n'existe plus ; restent `IntObjectCache` (8192 par défaut) et la famille SLRU.
4. `HeapMonitor.java` dans `jo/lspjava` : introuvable ; le moniteur réel est `HeapMonitor.kt` côté codeide-tooling.
5. Comptage synchronized réel : **24 méthodes + 2 blocs** (17 public), vs « 19 + 2 » annoncé dans le worklog.
6. `MapIndexStorageCache` dépend de `ServiceLoader`/`Class.forName` pour choisir son fournisseur (6 fichiers ServiceLoader dans le sparse) — point à shim/patcher sur ART (« à vérifier »).

## 9. Sources consultées (R6)

- `JetBrains/intellij-community` @ `865067b0a7f1a73ee0fa8bce4f042013c8ac334f` — fichiers cités chemin:ligne dans le corps ; lecture seule (sparse + `git cat-file` pour platform-impl/locking-impl/ide-core-impl). Licence : Apache-2.0 (« JetBrains Open-Source Build Terms v1.3 », `LICENSE.txt`, cf. worklog).
- `jjoblab/codeide-lsp` @ `cb809e1` (v1.16.0) : `EcjCompilerEngine.java`, `EcjEngineScheduler.java`, `CancelToken.java`, `OperationCanceledException.java`, `EcjParseCache.java`, `EcjLocateCache.java`, `JavaTextDocumentService.java`, `CacheSegments.kt`, `docs/LSP_JAVA.md`. Lecture seule.
- `jjoblab/codeide-tooling` @ `b60f7f0` : `tooling/server/src/main/kotlin/jo/codeide/tooling/server/HeapMonitor.kt`. Lecture seule.
- `/home/z/my-project/worklog.md` (travaux D1–D6, conventions de citation).

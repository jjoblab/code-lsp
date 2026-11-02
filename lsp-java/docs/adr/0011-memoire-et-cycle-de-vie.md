# ADR 0011 — Mémoire et cycle de vie

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

§5-D12 : « empreinte et comportement du GC sur ART ». Faits :

- **Le modèle IntelliJ** : « la valeur vit sur l'élément, meurt avec lui ou avec la mémoire » — `CachedValuesManagerImpl` ( valeur en user-data du holder ), invalidation **par dépendances + horodatages** ( `CachedValueBase:108-133` ) ; `ResolveCache` : 8 maps weak/soft mais **vidage total** sur ANY_PSI_CHANGE ( contre-exemple, D5 ) ; `SmartPointerTracker` ( file morte `ReferenceQueue` statique ) ; `FileTrees` ( 3 états par fichier ) avec **robinet mémoire global** `runInBatchFilesMode` soft→weak ( `PsiManagerImpl.java:590-603`, ~30 lignes ) ; caches d'index SLRU 1 024 **entrées** ( bornes en entrées, à re-qualifier en octets ) ; `LowMemoryWatcher` = `java.lang.management` ( **absent d'ART** ; non démarré en headless — non bloquant V2, mais à substituer ).
- **Densité de l'idiome** : ≈ 31 usages SoftReference + ≈ 86 weak dans les 4 modules principaux IntelliJ ; **le moteur ecj actuel en a zéro**.
- **Statu quo** : `EcjParseCache` LRU **8 entrées en références fortes** ( ~800 Ko, borné en entrées, pas en octets, non thread-safe ) ; `EcjLocateCache` LRU 8 192 entrées ( ReentrantLock ) ; `CacheSegments` CIDX = **budget octets** explicite ( 4,2/2,9/29,7 Mo — cible tenue ) ; `HeapMonitor.kt` ( tooling ) Runtime seul = compatible ART. Budgets bureau : tas stable 9/25 Mo ( 20 jars ) → 108/131 Mo ( 250 jars ) ; le snapshot 2,5 Mo/jar ( W2 ) est le premier responsable.

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule) : `platform/core-impl` `CachedValuesManagerImpl` ( 159 l. ), `CachedValueBase`, `ResolveCache`, `SmartPointerTracker` ( 561 l. ), `FileTrees` ( 412 l. ), `PsiManagerImpl` ( :590-603 ) ; `platform/indexing-api` `FileBasedIndexExtension.java:56` ; `platform/core-impl` `LowMemoryWatcherManager.java:11-18,156-168`.
- `jjoblab/codeide-lsp` @ `cb809e14` : `EcjParseCache`, `EcjLocateCache`, `CacheSegments` ; `docs/LSP_JAVA.md` ( W2, budgets ).
- `jjoblab/codeide-tooling` @ `b60f7f0` : `tooling/server/.../HeapMonitor.kt`.
- Documentation Android : `ComponentCallbacks2.onTrimMemory` ( substitution naturelle de LowMemoryWatcher, ~100 lignes ).

## Options

**Option 1 — Programme « mémoire IntelliJ-esprit » sur le moteur actuel ( recommandé ).** Cinq chantiers : (1) `EcjParseCache` en **valeurs molles** + `ReferenceQueue` + purge paresseuse ; (2) généralisation des **budgets octets** ( CacheSegments le fait — l'étendre au parse-cache et aux caches de résolution ) ; (3) invalidation **par dépendances** type `CachedValueBase` au lieu des vidages globaux ; (4) **mode batch soft→weak** pendant l'indexation des jars ( transposition directe du robinet `runInBatchFilesMode` ) ; (5) shim `onTrimMemory` ↔ signal mémoire ( remplace LowMemoryWatcher ).

**Option 2 — Porter la pile mémoire IntelliJ.** Exige `java.lang.management` ( absent d'ART ) et la pile holder — rejeté ( D12 ) ; l'idiome, pas le code.

**Option 3 — Statu quo strict ( bornes en entrées, références fortes ).** Accepte W2 et les pics de tas à 250 jars ( 108–131 Mo bureau, ×?? sur ART ) — repli derrière drapeaux.

**Option « ne rien changer »** : identique à l'option 3 sans suivi ; les budgets par entrées dérivent avec la taille des objets.

## Critères et mesures

- Budgets mesurés ( bureau ) : 9/25 Mo → 108/131 Mo ( 20 → 250 jars ) ; cible < 30 Mo/100 jars tenue par CIDX.
- LRU 8 entrées fortes ≈ 800 Ko : borne en entrées — un seul gros fichier peut dépasser.
- GC ART : **non vérifié** ( comportement SoftReference, coût ReferenceQueue — à mesurer sur appareil, gabarit spike §5 ).

## Décision proposée

**Option 1**, dans l'ordre : (2) budgets octets partout ( le plus sûr, déjà prouvé par CacheSegments ) → (1) parse-cache mou → (5) shim `onTrimMemory` ( petit, gros gain UX sous pression mémoire ) → (4) robinet batch pour l'indexation des jars → (3) invalidation par dépendances ( le plus délicat, à caler sur l'époque ADR 0010 ).

## Conséquences

- Le tas du moteur devient borné **en octets** à tous les étages ( index, parse, résolution ) — la pression mémoire sur ART ( foreground/background, `onTrimMemory` ) devient un citoyen de première classe.
- Les soft-références introduisent de la non-déterminabilité ( éviction dépendante du GC ) : les bancs doivent mesurer **avec et sans pression** ( ADR 0016 ).
- Compatibilité test : `WeakReference`/`SoftReference`/`ReferenceQueue` existent sur ART — comportement à vérifier sur appareil.

## Plan de migration progressive

Chaque chantier derrière drapeau ( `memoire.budgets-octets`, `memoire.parse-mou`, `memoire.ontrim`, `memoire.batch-weak`, `memoire.invalidation-dependances` ) ; retour arrière = bornes en entrées ( code actuel conservé une version ).

## Tests de non-régression et critères de sortie

- Banc mémoire : tas après indexation + à l'état stable ( corpus 20/73/250 jars ) — pas de dépassement du budget octets ; éviction effective sous pression simulée ( `Runtime.gc()` + allocation massive ) ; zero fuite après dispose module ( test de cycle de vie ).
- Critère de sortie : tas stable ≤ cible par palier de jars ( à re-mesurer sur appareil ).

## Risques

- **R1 — GC ART évict trop tôt/trop tard les SoftReference** : à mesurer sur appareil ; repli = bornes dures en octets ( LRU pur ).
- **R2 — Coût des ReferenceQueue en purge paresseuse** : mesurer au banc ; purge bornée par tranche.
- **R3 — Invalidation par dépendances mal branchée** : cohérence à revoir avec l'époque ( ADR 0010 ) — même compteur, mêmes points de bump.

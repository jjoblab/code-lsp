# Fiche 025 — EcjParseCacheTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjParseCacheTest.java`.
> **Domaine** : caches-perf
> **Résumé** : Tests du cache de parse de l'unité résolue (`resolveUnit(path, source, generateCode)`) : hit sur même (path, source), miss sur changement de source, invalidation ciblée et globale (dispose), cohabitation multi-entrées (W8 : l'entrée unique évinçait à chaque put), éviction LRU bornée par `MAX_ENTRIES`, partage entre diagnostics (generateCode=true) et hover (generateCode=false), et non-dégradation d'une entrée codegen. Sources témoins : petites classes `demo.A`/`demo.B`/`demo.Main` d'une ligne.

## Comportements

### sameSourceReturnsCachedTree — hit sur même (path, source)
- **Préconditions / entrée** : deux `resolveUnit("/demo/Main.java", source, false)` successifs avec la MÊME source.
- **Comportement attendu** : la MÊME instance d'arbre résolu est retournée (identité référentielle).
- **Bornes** : —
- **Statut** : actif

### changedSourceTriggersMiss — changement de source → nouvel arbre
- **Préconditions / entrée** : `resolveUnit` sur v1 puis v2 (même path, sources différentes : `int x;` vs `int y;`).
- **Comportement attendu** : deux arbres DISTINCTS (cache miss — jamais un arbre périmé).
- **Bornes** : —
- **Statut** : actif

### invalidateParseCacheForcesMiss — invalidation ciblée force le miss
- **Préconditions / entrée** : `resolveUnit` ; `invalidateParseCache("/demo/A.java")` ; re-`resolveUnit` (même source).
- **Comportement attendu** : nouvel arbre (instance différente).
- **Bornes** : —
- **Statut** : actif

### diagnosticsFillThenHoverHits — diagnose remplit, hover sert le cache
- **Préconditions / entrée** : `diagnose` (generateCode=true) puis `resolveUnit(…, false)` sur le même (path, source).
- **Comportement attendu** : l'arbre mis en cache par diagnose est servi au resolveUnit sans codegen (les bindings sont identiques — la codegen n'affecte que des octets de classe jetés) ; le resolveUnit retourne un arbre non null sans lever.
- **Bornes** : —
- **Statut** : actif

### disposeInvalidatesCache — dispose invalide tout le cache
- **Préconditions / entrée** : `resolveUnit` ; `dispose()` ; re-`resolveUnit` (même path/source).
- **Comportement attendu** : nouvel arbre (instance différente).
- **Bornes** : —
- **Statut** : actif

### alternatingFilesKeepBothEntries — cohabitation multi-entrées (W8)
- **Préconditions / entrée** : resolveUnit sur A, puis B, puis à nouveau A, puis B.
- **Comportement attendu** : A et B cohabitent : le retour à A est un hit (même instance), B aussi — l'aller-retour entre fichiers ne repaie plus le parse.
- **Bornes** : —
- **Statut** : actif

### capacityEvictsLeastRecentlyUsed — éviction LRU, capacité bornée
- **Préconditions / entrée** : resolveUnit sur F0, puis sur MAX_ENTRIES+1 fichiers distincts F1..F(MAX_ENTRIES), puis re-resolveUnit sur F0.
- **Comportement attendu** : F0 (le moins récemment utilisé) a été évincé : re-parse (instance différente) ; la taille du cache reste ≤ MAX_ENTRIES à tout moment.
- **Bornes** : capacité = `EcjParseCache.MAX_ENTRIES` (constante référencée symboliquement — valeur numérique non visible dans le test) ; taille ≤ MAX_ENTRIES.
- **Statut** : actif

### invalidateOnePathKeepsOthers — l'invalidation ciblée épargne les autres
- **Préconditions / entrée** : resolveUnit sur A et B ; `invalidateParseCache("/demo/A.java")` ; re-resolveUnit sur B puis sur A.
- **Comportement attendu** : B reste un hit (même instance) ; seul A re-parse (instance différente).
- **Bornes** : —
- **Statut** : actif

### codegenEntryIsNeverDowngraded — une entrée codegen n'est jamais dégradée
- **Préconditions / entrée** : `diagnose` (met en cache une entrée AVEC codegen) ; `resolveUnit(…, false)` (put sans codegen) ; re-`diagnose`.
- **Comportement attendu** : l'entrée codegen survit au put sans codegen — le second diagnose produit le même nombre de diagnostics que le premier (l'arbre codegen est réutilisé, pas re-parse en mode inférieur).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Clé de cache = couple (path, source) — l'identité d'instance d'arbre est le contrat observable (assertSame/assertNotSame).
- Le cache est un LRU multi-entrées borné par une constante MAX_ENTRIES ; la valeur numérique n'est pas fixée par le test — à choisir et documenter en Kotlin.
- `generateCode` est un attribut d'entrée qui ne doit JAMAIS dégrader une entrée existante plus riche (codegen survit aux puts sans codegen).
- `diagnoseSyntax`/`diagnose` et les lectures hover partagent le même cache : la codegen ne doit pas fractionner le cache.
- Anomalie mineure : « diagnosticsFillThenHoverHits » n'affirme pas l'identité (pas de assertSame) — le hit est le comportement visé, seul le non-null est vérifié.

# Fiche 012 — EcjCompilerEngineConcurrencyTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjCompilerEngineConcurrencyTest.java`.
> **Domaine** : concurrence-annulation
> **Résumé** : Concurrence du moteur ecj in-process : `resolveUnit()` lit/écrit le parse cache non thread-safe et ouvre les jars partagés de l'environnement ; de nombreux points d'entrée LSP (complétion, signature help, inlay hints, rename, import organizer, document symbol) y passent sur le pool LSP4J (`newCachedThreadPool`) — exposition à une course réelle. Deux tests : un mix concurrent diagnostics + resolveUnit + hover sur un moteur PARTAGÉ (cohérence des résultats, aucune exception) et un stress de `resolveUnit` sur deux sources à clés de cache distinctes.

## Comportements

### concurrentMixOfOperationsDoesNotCorruptCache — mix concurrent sans corruption de cache
- **Préconditions / entrée** : un moteur UNIQUE partagé (aucune racine, aucun jar) ; 8 threads × 12 itérations ; à chaque itération : `diagnose("/demo/Main.java", src)` avec `src` = MAIN_OK (code valide) ou MAIN_ERR (`String s = 42`) en alternance `(idx+it)%2`, puis `resolveUnit("/demo/Main.java", src, false)`, puis `hoverAt("/demo/Main.java", src, 60)`.
- **Comportement attendu** : 0 exception levée ; pour MAIN_OK : le nombre de diagnostics de sévérité ERROR est TOUJOURS 0 (sinon le cache a rendu le résultat d'une autre frappe) ; pour MAIN_ERR : toujours ≥ 1 erreur ; `resolveUnit` jamais null ; le pool se termine proprement.
- **Bornes** : 8 threads ; 12 itérations/thread ; `future.get(30, SECONDS)` ; `awaitTermination(5, SECONDS)`.
- **Statut** : actif

### twoThreadsResolveDifferentSourcesConcurrentlyWithoutCrash — stress resolveUnit sur deux clés distinctes
- **Préconditions / entrée** : un moteur unique ; thread A = 20 × `resolveUnit("/demo/A.java", MAIN_OK, false)` ; thread B = 20 × `resolveUnit("/demo/B.java", MAIN_ERR, false)` (clés de cache différentes → invalidation mutuelle).
- **Comportement attendu** : aucun crash ; chaque thread obtient au moins une résolution non nulle (`nonNull > 0`) ; le pool se termine proprement.
- **Bornes** : 2 threads × 20 itérations ; `future.get(30, SECONDS)` ; `awaitTermination(5, SECONDS)`.
- **Statut** : actif

## Notes pour la réécriture
- Le scénario reproduit la charge réelle du serveur LSP multi-thread (pool LSP4J) — la réécriture Kotlin devra garantir la même cohérence sous 8 threads sans verrou global si possible.
- Invariant central : le cache ne doit JAMAIS rendre le résultat d'une autre frappe — MAIN_OK → 0 erreur et MAIN_ERR → ≥ 1 erreur à CHAQUE itération, quel que soit l'entrelacement.
- Seule la première défaillance est rapportée (`AtomicReference<Throwable> firstFailure` + compteur atomique d'erreurs) — pattern de diagnostic à conserver.
- `hoverAt` à l'offset fixe 60 n'est pas asserté sur le contenu : seul « ne doit pas crasher » compte ici.
- Le départ simultané est synchronisé par `CountDownLatch` — les 8 threads démarrent leurs 12 itérations au même instant.

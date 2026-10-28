# Fiche 048 — EcjEngineSchedulerTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/engine/EcjEngineSchedulerTest.java`.
> **Domaine** : concurrence-annulation
> **Résumé** : Tests unitaires du scheduler du moteur : un worker unique sérialise RÉELLEMENT les tâches interactives (aucun chevauchement) ; la supersession annule les tâches interactives obsolètes d'un même fichier (les analyse, elles, ne sont jamais superseded) ; shutdown idempotent. Éprouve les propriétés via compteurs d'exécution simultanée max, de complétions et de supersessions.

## Comportements

### interactiveTasksAreSerializedOnSingleWorker — sérialisation stricte
- **Préconditions / entrée** : scheduler nommé « test1 » ; 10 tâches interactives soumises sur 3 fichiers distincts (F0/F1/F2), chacune dormant 30 ms.
- **Comportement attendu** : au moins 1 tâche complète ; AUCUNE paire de tâches ne s'exécute simultanément (le maximum d'exécutions en vol constaté vaut exactement 1) ; toutes les tâches soumises sont comptabilisées (complétées ou superseded).
- **Bornes** : 10 tâches / 3 fichiers ; sommeil 30 ms par tâche ; `maxInFlight == 1` ; `completed + superseded == 10` ; récupération des `Future` en 5 s max.
- **Statut** : actif

### supersessionCancelsPendingInteractiveForSameFile — supersession par fichier
- **Préconditions / entrée** : scheduler « test2 » ; une tâche ANALYSE bloquante (sommeil 800 ms) sature le worker ; 5 tâches interactives soumises pendant l'occupation sur le MÊME fichier `/demo/F1.java`.
- **Comportement attendu** : les interactives en attente sont supersedées par la plus récente (annulation via `CancellationException`) ; au moins une supersession est comptée ; au plus 2 tâches interactives ont réellement démarré sur F1.java ; même la dernière peut être superseded si les soumissions sont trop rapides pour que le worker n'en démarre qu'une (toléré par le harnais).
- **Bornes** : analyse bloquante 800 ms ; 5 interactives ; `supersededCount() >= 1` ; `completed <= 2` ; récupérations en 5 s.
- **Statut** : actif

### analysisTasksAreNotSuperseded — analyses jamais superseded
- **Préconditions / entrée** : scheduler « test3 » ; 5 tâches d'ANALYSE soumises (sommeil 30 ms chacune).
- **Comportement attendu** : les 5 complètent TOUTES ; aucune n'est superseded (compteur à zéro).
- **Bornes** : 5 tâches ; `completed == 5` ; `supersededCount() == 0` ; récupérations en 5 s.
- **Statut** : actif

### shutdownIsIdempotentAndReleasesWorker — shutdown idempotent
- **Préconditions / entrée** : scheduler « test4 » ; `shutdown()` appelé DEUX fois.
- **Comportement attendu** : aucun état d'erreur — le second appel ne lève pas (idempotence, libération du worker).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- La supersession est à clé de FICHIER et ne concerne QUE les tâches interactives (les tâches analyse sont durables : jamais annulées par une plus récente).
- La garantie de sérialisation est testée par le maximum observé d'exécutions simultanées (doit rester à 1) — un worker unique, pas un pool.
- Le compteur `supersededCount()` est une surface observable de diagnostic : à conserver pour le banc de parité.
- Une tâche superseded se manifeste par une `CancellationException` sur son `Future` (l'appelant ne doit PAS propager cette exception comme une erreur serveur).
- Les tâches interactives sont soumises avec (fichier, fournisseur) ; les analyses avec un fournisseur seul — deux files/modes distincts derrière un même worker.

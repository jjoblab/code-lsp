# Fiche 046 — CancelTokenTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/engine/CancelTokenTest.java`.
> **Domaine** : concurrence-annulation
> **Résumé** : Tests du token d'annulation coopératif `CancelToken` : singleton `NEVER` jamais annulable, token manuel (annulation idempotente), token à échéance (timeout), interruption du thread attaché lors du `cancel`, et `close()` qui libère sans annuler. Toutes les variantes sont auto-fermables.

## Comportements

### neverTokenIsNeverCancelled — singleton NEVER
- **Préconditions / entrée** : le token partagé `CancelToken.NEVER`.
- **Comportement attendu** : `isCancelled()` est toujours faux.
- **Bornes** : —
- **Statut** : actif

### manualTokenStartsNotCancelled — token manuel
- **Préconditions / entrée** : un token créé par `CancelToken.manual()`.
- **Comportement attendu** : frais, il n'est pas annulé ; après `cancel()`, `isCancelled()` est vrai ; un second contrôle reste vrai (idempotent).
- **Bornes** : —
- **Statut** : actif

### timeoutTokenFiresAfterDeadline — annulation à échéance
- **Préconditions / entrée** : token créé par `CancelToken.timeout(50, TimeUnit.MILLISECONDS)`.
- **Comportement attendu** : frais, pas annulé ; après un sommeil supérieur au délai, il est annulé.
- **Bornes** : timeout 50 ms ; vérifié après un sommeil de 120 ms.
- **Statut** : actif

### cancelInterruptsAttachedThread — interruption du thread attaché
- **Préconditions / entrée** : token manuel attaché au thread courant ET à un thread worker (endormi 5 s) ; `cancel()` déclenché après 100 ms.
- **Comportement attendu** : le sommeil du worker est INTERROMPU (il reçoit l'interruption) et le worker s'arrête ; le thread principal, également attaché, a aussi reçu l'interruption (le harnais nettoie son flag).
- **Bornes** : cancel à 100 ms ; sommeil du worker 5 000 ms ; `join` réussi en 500 ms (worker non vivant).
- **Statut** : actif

### closeClearsWorkerReference — close sans annulation
- **Préconditions / entrée** : token manuel attaché au thread courant, puis `close()`.
- **Comportement attendu** : `close()` ne plante pas (libération de la référence au worker) et NE déclenche PAS l'annulation — `isCancelled()` reste faux.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Surface observable : `NEVER`, `manual()`, `timeout(durée, unité)`, `isCancelled()`, `cancel()`, `attachToCurrentThread()`, `close()` (auto-fermable) — contrat minimal à reproduire en Kotlin.
- `cancel()` interrompt TOUS les threads attachés (le test attache le principal et le worker : l'interruption est diffuse) — l'appelant doit s'attendre à recevoir aussi l'interrupt.
- `close()` ≠ `cancel()` : libérer les ressources ne marque jamais le token annulé.
- L'annulation est COOPÉRATIVE : c'est la consultation `isCancelled()` et l'interruption attachée qui propagent l'arrêt, pas un kill.

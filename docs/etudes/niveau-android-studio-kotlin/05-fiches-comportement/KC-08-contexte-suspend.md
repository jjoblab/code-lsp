# Fiche KC-08 — Contexte `suspend` et coroutines

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) autour des fonctions `suspend` et des coroutines
> (`launch`/`async`/`runBlocking`, `Flow`). Étude en lecture seule, clean-room.
> **Domaine** : coroutines / diagnostics / complétion
> **Résumé** : appeler une fonction `suspend` hors contexte suspendable est
> **rouge** (comportement référence) ; dans un corps `suspend` ou un lambda
> de coroutine builder, la complétion propose les suspendues + les membres du
> récepteur (`CoroutineScope`) ; les opérateurs de `Flow` sont des extensions
> du classpath. Décidabilité cadencée par paliers.

## Comportements

### S1 — suspendue hors contexte = rouge
- **Préconditions / entrée** : `suspend fun charger(): Donnees` ; appel
  `charger()` depuis une fonction non `suspend` et hors lambda suspendable —
  les deux déclarations lisibles (projet ou classpath).
- **Comportement attendu** : erreur dans la référence ; dans le moteur :
  **cible**, émise seulement si (a) le modificateur `suspend` de l'appelée
  est prouvé, (b) le contexte d'appel est **prouvé non suspendable**.
- **Bornes** : code `kt.*` au catalogue ; contexte incertain (lambda non
  typé, inline inconnu) → silence.

### S2 — complétion dans `launch`/`async`/`runBlocking`
- **Entrée** : `scope.launch { | }` — lambda à récepteur `CoroutineScope`
  (classpath kotlinx).
- **Attendu** : membres du récepteur implicite (`launch`, `async`, …) en
  tête (renvoi KC-03), puis fonctions suspendues du projet/classpath.
- **Bornes** : builder reconnu par import prouvé (palier 1) ; sans preuve →
  repli NOM.

### S3 — `Flow` : opérateurs en extension
- **Entrée** : `val flux: kotlinx.coroutines.flow.Flow` (FQCN démontrable,
  sans argument de type) ; position `flux.|`.
- **Attendu** : opérateurs d'extension de `Flow` (`map`, `filter`, `onEach`,
  …) issus du classpath, chaînables (renvoi KC-02).
- **Bornes** : typage borné — le récepteur est démontré comme FQCN **sans
  génériques** ; le paramètre de type `T` n'est ni lu ni vérifié.

### S4 — décidabilité par palier
- **Attendu** : **palier 1** : mot-clé `suspend` (syntaxe) + imports kotlinx
  prouvés → contexte suspend des corps détecté, builders reconnus ;
  **palier 2+** : statut `suspend` des appelées (déclarations), rouges S1,
  types `Flow` en FQCN.
- **Statut** : `[À CAPTURER]` — ensemble exact des contextes acceptés par la
  référence (inline, `withContext`, builders personnalisés).

### S5 — garde anti-faux-positif
- **Entrée** : appelé dont le statut `suspend` n'est pas lisible ; appelant
  partiellement analysé ; lambda d'un inline inconnu.
- **Attendu** : **aucun** rouge ; la complétion reste disponible (repli
  honnête), seuls les diagnostics sont muets.

## Notes pour la réécriture
- ADR 0128 (Compose/coroutines — porteur), ADR 0125 (catalogue diagnostics),
  ADR 0123 (typage palier 2 — FQCN sans génériques), ADR 0122 (paliers).
- Écarts GK-xxx (matrice, à venir) ; oracle K2 (sous-processus) pour les
  contextes limites au banc.

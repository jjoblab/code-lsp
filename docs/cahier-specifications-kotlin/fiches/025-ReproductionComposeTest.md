# Fiche 025 — ReproductionComposeTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionComposeTest.kt` (12 tests, 300 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION des faux positifs `kt.unresolved` signalés par le propriétaire sur un projet Android Compose (25/10/2026) — chaque test porte UN motif Compose canonique et constate le verdict du moteur aujourd'hui. Classpath : kotlin-stdlib RÉEL (voie 6 d'ADR 0203) — les motifs ne dépendent d'aucune AAR Compose, ils isolent les FORMES.
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `repro---lambda-finale-a-parametre-nomme-(Scaffold-innerPadding)`

- **Attendu** : repro : lambda finale a parametre nomme (Scaffold innerPadding).
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `repro---lambda-imbriquee-parametres-nommes`

- **Attendu** : repro : lambda imbriquee parametres nommes.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `repro---appel-complet-qualifie-premier-segment-(paquet)`

- **Attendu** : repro : appel complet qualifie premier segment (paquet).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `repro---annotation-qualifiee`

- **Attendu** : repro : annotation qualifiee.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `repro---delegation-by-lazy`

- **Attendu** : repro : delegation by lazy.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `repro---import-alias-puis-usage`

- **Attendu** : repro : import alias puis usage.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `repro---import-joker-puis-usage`

- **Attendu** : repro : import joker puis usage.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `repro---appel-de-fonction-importee-prefixee-par-paquet`

- **Attendu** : repro : appel de fonction importee prefixee par paquet.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `sondage---survol-d'un-appelable-top-level-du-classpath-(println)`

- **Attendu** : sondage : survol d'un appelable top level du classpath (println).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `sondage---symboles-de-document-d'un-fichier-Compose-like`

- **Attendu** : sondage : symboles de document d'un fichier Compose like.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `sondage---references-d'une-fonction-projet`

- **Attendu** : sondage : references d'une fonction projet.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `sondage---completion-de-nom-dans-un-corps`

- **Attendu** : sondage : completion de nom dans un corps.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

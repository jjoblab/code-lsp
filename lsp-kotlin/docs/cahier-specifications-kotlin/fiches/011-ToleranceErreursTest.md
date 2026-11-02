# Fiche 011 — ToleranceErreursTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/parite/ToleranceErreursTest.kt` (3 tests, 87 l.).
> **Domaine** : parse
> **Résumé** : Tolérance aux fautes (LSP_KOTLIN D3) : un texte en cours d'édition doit TOUJOURS produire un arbre — l'éditeur appelle le parseur à chaque frappe, y compris entre deux lettres, au milieu d'une chaîne, d'un commentaire ou d'un KDoc. Le oracle est simple : aucune exception, un arbre racine complet.
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `troncature-a-chaque-position---aucun-lever`

- **Attendu** : troncature a chaque position : aucun lever — aucune exception levée (tolérance d'édition)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `insertions-en-positions-hostiles---aucun-lever`

- **Attendu** : insertions en positions hostiles : aucun lever — aucune exception levée (tolérance d'édition)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `deletions-aleatoires-bornees---aucun-lever`

- **Attendu** : deletions aleatoires bornees : aucun lever — aucune exception levée (tolérance d'édition)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

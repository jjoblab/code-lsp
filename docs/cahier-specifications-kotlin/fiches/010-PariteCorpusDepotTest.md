# Fiche 010 — PariteCorpusDepotTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/parite/PariteCorpusDepotTest.kt` (2 tests, 149 l.).
> **Domaine** : parse
> **Résumé** : Suite de parité « dépôt » (LSP_KOTLIN.md D2) : TOUT fichier `.kt` du dépôt CodeIDE est parsé par la grammaire vendorée du compilateur — chaque fichier doit produire un arbre SANS marqueur d'erreur (le dépôt compile en CI, la grammaire du compilateur doit l'accepter telle quelle), et la FORME globale des arbres est épingée par un digeste de base (toute dérive de grammaire — re-sync amont ou régress
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `tout-le-depôt-se-parse-sans-marqueur-d'erreur`

- **Attendu** : tout le depôt se parse sans marqueur d'erreur.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `le-digeste-structurel-du-corpus-correspond-a-la-baseline`

- **Attendu** : le digeste structurel du corpus correspond a la baseline.
- **Classement** : conforme (propriété de l'amont, éprouvée par la suite)

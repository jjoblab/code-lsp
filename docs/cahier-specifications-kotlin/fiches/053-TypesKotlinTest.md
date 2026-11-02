# Fiche 053 — TypesKotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/TypesKotlinTest.kt` (7 tests, 278 l.).
> **Domaine** : navigation
> **Résumé** : Tests de la NAVIGATION DE TYPES Kotlin (L2 — plan 1.3.0, `textDocument/typeDefinition` et `textDocument/implementation`) — par le SPI (chemin de production D6), moteur RÉEL, projet multi-fichiers sur disque (racines sources du scan, même montage que les occurrences S2). Le cœur éprouvé : la PREUVE PAR FQCN. Une cible est servie seulement si son nom résout par les IMPORTS du fichier du curseur (ou
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `typeDefinition-référence-de-type---la-déclaration-du-type-visé`

- **Attendu** : typeDefinition référence de type : la déclaration du type visé.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `typeDefinition-symbole-typé---propriété-et-paramètre-de-type-écrit`

- **Attendu** : typeDefinition symbole typé : propriété et paramètre de type écrit.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `typeDefinition-constructeur---la-classe-construite`

- **Attendu** : typeDefinition constructeur : la classe construite.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `typeDefinition-incertain---vide,-jamais-deviné`

- **Attendu** : typeDefinition incertain : vide, jamais deviné — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `implementations---héritiers-prouvés,-document-courant-en-tête,-fermés-du-scan-ensuite`

- **Attendu** : implementations : héritiers prouvés, document courant en tête, fermés du scan ensuite.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `implementations-homonymie---le-supertype-résout-par-les-imports-du-fichier,-pas-par-nom-simple`

- **Attendu** : implementations homonymie : le supertype résout par les imports du fichier, pas par nom simple.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `implementations-incertain---vide-sans-type-prouvé`

- **Attendu** : implementations incertain : vide sans type prouvé.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

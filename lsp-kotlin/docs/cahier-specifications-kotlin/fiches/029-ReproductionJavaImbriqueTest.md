# Fiche 029 — ReproductionJavaImbriqueTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionJavaImbriqueTest.kt` (5 tests, 256 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION H3 (mission fiabilisation, étape A.2 — hypothèse « Java mixte : classes imbriquées » du prompt, MESURÉE) : la frontière réelle du modèle Java de [SourcesJava], telle que la mesure la révèle (le sondeur a confronté chaque niveau au moteur) : - NIVEAU 2 (`Aquarium.Bac`) : RÉSOUT — l'imbriquée est extraite comme DÉCLARATION MEMBRE du conteneur (`PROPRIETE:Bac`, conteneur `com.ex.app.Aqua
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `imbriquee-de-niveau-2---type,-expression-et-import-resoluent-(hypothese-infirmee)`

- **Attendu** : imbriquee de niveau 2 : type, expression et import resoluent (hypothese infirmee).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `imbriquee-de-niveau-3-en-type---extraction-recursive,-dernier-segment-muet`

- **Attendu** : imbriquee de niveau 3 en type : extraction recursive, dernier segment muet — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `imbriquee-de-niveau-3-en-expression---la-garde-selecteur-couvre-(muet)`

- **Attendu** : imbriquee de niveau 3 en expression : la garde selecteur couvre (muet) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `membre-de-niveau-1---non-regression-de-la-vague-2`

- **Attendu** : membre de niveau 1 : non regression de la vague 2.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `entree-d-enum-java---silence-d3,-non-regression-vague-3`

- **Attendu** : entree d enum java : silence d3, non regression vague 3 — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

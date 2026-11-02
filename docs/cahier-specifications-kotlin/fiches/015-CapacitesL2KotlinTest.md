# Fiche 015 — CapacitesL2KotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/CapacitesL2KotlinTest.kt` (12 tests, 380 l.).
> **Domaine** : hover-inlay
> **Résumé** : Tests des trois capacités L2 restantes — INDICES EN LIGNE (`textDocument/inlayHint`), SYMBOLES D'ESPACE (`workspace/symbol`) et CHAÎNES DE SÉLECTION (`textDocument/selectionRange`) — par le SPI (chemin de production D6), moteur RÉEL. Le cœur éprouvé : l'honnêteté D3. Un appel sans candidate prouvée ne rend AUCUN indice de paramètre ; une initialisation dont le type n'est pas certain (expression co
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `indices---noms-de-paramètres-aux-appels-positionnels-prouvés`

- **Attendu** : indices : noms de paramètres aux appels positionnels prouvés.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `indices---argument-nommé-sans-indice,-positionnels-suivis`

- **Attendu** : indices : argument nommé sans indice, positionnels suivis.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `indices---types-certains-des-déclarations-sans-type-écrit`

- **Attendu** : indices : types certains des déclarations sans type écrit.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `indices---incertain-sans-indice,-plage-bornée,-document-inconnu-vide`

- **Attendu** : indices : incertain sans indice, plage bornée, document inconnu vide — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `indices---it-implicite-du-littéral,-preuve-par-la-fonction-projet`

- **Attendu** : indices : it implicite du littéral, preuve par la fonction projet.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `indices---paramètres-explicites-sans-type-écrit,-un-TYPE-par-nom`

- **Attendu** : indices : paramètres explicites sans type écrit, un TYPE par nom.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `indices---littéral-sans-preuve,-arité-divergente,-paramètre-typé`

- **Attendu** : indices : littéral sans preuve, arité divergente, paramètre typé.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `indices---it-implicite-par-propriété-typée-et-SAM-du-projet`

- **Attendu** : indices : it implicite par propriété typée et SAM du projet.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `symbolesEspace---par-requête-contient,-locales-exclues,-ouverts-et-fermés`

- **Attendu** : symbolesEspace : par requête contient, locales exclues, ouverts et fermés.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `symbolesEspace---requête-sans-réponse-vide`

- **Attendu** : symbolesEspace : requête sans réponse vide — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `selections---chaîne-strictement-croissante-du-profond-au-fichier`

- **Attendu** : selections : chaîne strictement croissante du profond au fichier.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `selections---plusieurs-positions-dans-l'ordre,-hors-arbre-fichier-seul`

- **Attendu** : selections : plusieurs positions dans l'ordre, hors arbre fichier seul.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

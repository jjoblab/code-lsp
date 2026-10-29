# Fiche 014 — PlisDocumentKotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/plis/PlisDocumentKotlinTest.kt` (7 tests, 153 l.).
> **Domaine** : folding
> **Résumé** : Tests du moteur de pliage Kotlin (S2 — plan de stabilisation LSP §3.2) : chaque corps à accolades est UNE plage (de la première accolade ouvrante à la dernière fermante — JAMAIS du début du nœud), le bloc d'imports entier en est un, les commentaires multi-lignes aussi. Déduplication BODY/BLOCK ; tolérance D3 (jamais d'exception).
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `corps-de-classe-et-de-fonctions---une-plage-par-paire-d-accolades`

- **Attendu** : corps de classe et de fonctions : une plage par paire d accolades.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `liste-d-imports---du-premier-au-dernier`

- **Attendu** : liste d imports : du premier au dernier.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `commentaire-multi-ligne---KDoc-et-bloc-pliables`

- **Attendu** : commentaire multi ligne : KDoc et bloc pliables.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `lambda-et-when---pliés-par-accolades,-pas-de-doublon`

- **Attendu** : lambda et when : pliés par accolades, pas de doublon.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `structures-de-controle---if-for-while-sans-doublon-BODY-BLOCK`

- **Attendu** : structures de controle : if for while sans doublon BODY BLOCK.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `edition-en-cours---accolade-fermante-absente,-sous-rapport-sans-exception`

- **Attendu** : edition en cours : accolade fermante absente, sous rapport sans exception — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `document-vide-ou-mono-ligne---rendu-honnete,-filtre-hote-ensuite`

- **Attendu** : document vide ou mono ligne : rendu honnete, filtre hote ensuite.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

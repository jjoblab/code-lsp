# Fiche 012 — ParseurKotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/parse/ParseurKotlinTest.kt` (9 tests, 128 l.).
> **Domaine** : parse
> **Résumé** : Tests du parseur sur des sources représentatives (l'autre moitié de la suite de parité K1 : le corpus balaye le dépôt entier, ces fixtures épinglent des formes précises, jeton par jeton).
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `fichier-vide---arbre-racine-KT_FILE-sans-erreur`

- **Attendu** : fichier vide : arbre racine KT_FILE sans erreur.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `accesseurs-non-whitespace---texte-du-jeton-exact`

- **Attendu** : accesseurs non whitespace : texte du jeton exact.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `fichier-valide-complet---aucun-marqueur-d'erreur`

- **Attendu** : fichier valide complet : aucun marqueur d'erreur.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `accolade-manquante---arbre-construit-et-erreur-marquee`

- **Attendu** : accolade manquante : arbre construit et erreur marquee.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `texte-tronque-en-plein-identifiant---parse-sans-lever`

- **Attendu** : texte tronque en plein identifiant : parse sans lever — aucune exception levée (tolérance d'édition)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `chaine-non-fermee---parse-sans-lever`

- **Attendu** : chaine non fermee : parse sans lever — aucune exception levée (tolérance d'édition)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `script-kts---directives-autorisees-en-racine`

- **Attendu** : script kts : directives autorisees en racine.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `determinisme---deux-analyses-du-meme-texte-donnent-le-meme-digeste`

- **Attendu** : determinisme : deux analyses du meme texte donnent le meme digeste.
- **Classement** : conforme (propriété de l'amont, éprouvée par la suite)

### `positions---offsets-de-jetons-dans-le-texte-source`

- **Attendu** : positions : offsets de jetons dans le texte source.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

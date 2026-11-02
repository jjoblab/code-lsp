# Fiche 013 — FacadeKtTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/parse/kt/FacadeKtTest.kt` (13 tests, 240 l.).
> **Domaine** : parse
> **Résumé** : Tests de la façade `Kt*` : les accesseurs portent le vocabulaire du compilateur et RESTENT conservateurs (D3) — introuvable = null/vide.
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `directive-package---nom-qualifie`

- **Attendu** : directive package : nom qualifie.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `fichier-sans-package---nom-qualifie-null-(directive-vide-presente)`

- **Attendu** : fichier sans package : nom qualifie null (directive vide presente).
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `imports-simple-alias-et-wildcard`

- **Attendu** : imports simple alias et wildcard.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `alias-sans-identifiant-derriere-as---null-conservateur`

- **Attendu** : alias sans identifiant derriere as : null conservateur.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `declarations-de-premier-niveau---noms-et-genres`

- **Attendu** : declarations de premier niveau : noms et genres.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `classe---membres,-interface,-enum`

- **Attendu** : classe : membres, interface, enum.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `fonction---parametres-nommes`

- **Attendu** : fonction : parametres nommes.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `propriete-avec-objet-anonyme---nom-de-propriete-conservateur`

- **Attendu** : propriete avec objet anonyme : nom de propriete conservateur.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `declaration-tronquee-sans-identifiant---nom-null-sans-lever`

- **Attendu** : declaration tronquee sans identifiant : nom null sans lever — aucune exception levée (tolérance d'édition)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `navigation-generique---enfants-par-type`

- **Attendu** : navigation generique : enfants par type.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `annotations---noms-ecrits-et-ordre-du-document`

- **Attendu** : annotations : noms ecrits et ordre du document.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `annotations---arguments-nommes-avec-plages-de-valeurs`

- **Attendu** : annotations : arguments nommes avec plages de valeurs.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `annotations---fonction-sans-annotations-et-fonction-avec-corps`

- **Attendu** : annotations : fonction sans annotations et fonction avec corps.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

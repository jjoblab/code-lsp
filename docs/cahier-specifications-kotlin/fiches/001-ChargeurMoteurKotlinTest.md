# Fiche 001 — ChargeurMoteurKotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/api/ChargeurMoteurKotlinTest.kt` (3 tests, 44 l.).
> **Domaine** : cycle-vie-lsp
> **Résumé** : Tests du SPI : résolution ServiceLoader du moteur (le contrat de pack LSP_KOTLIN §6 doit être vérifié MÉCANIQUEMENT, pas seulement revu) et intégrité du descripteur de pack.
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `serviceLoader-resolve-le-moteur-unique`

- **Attendu** : serviceLoader resolve le moteur unique.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `descripteur-de-pack-complet---valeurs-reelles-pas-des-replis`

- **Attendu** : descripteur de pack complet : valeurs reelles pas des replis.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `le-moteur-analyse-via-le-spi---arbre-non-vide`

- **Attendu** : le moteur analyse via le spi : arbre non vide.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

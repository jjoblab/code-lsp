# Fiche 038 — ReproductionVague5Test

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionVague5Test.kt` (8 tests, 511 l.).
> **Domaine** : completion
> **Résumé** : REPRODUCTION des familles de la VAGUE 5 (retour utilisateurs 25/10/2026 — backlog RETOURS §4, items 1 et 2) : 1. RÉCEPTEURS DE PORTÉE des lambdas de scope — le paramètre `content: RowScope.() -> Unit` de Compose `Row { … }` prête RowScope comme this IMPLICITE (U1 « weight »/« align ») : membre NU et MEMBRE-EXTENSION (`Modifier.weight(1f)` — weight vit DANS RowScope, le récepteur d'extension est le
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `portee---membre-nu-et-member-extension-du-scope-projet`

- **Attendu** : portee : membre nu et member extension du scope projet.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `portee---membre-inconnu-dans-le-scope-reste-signale`

- **Attendu** : portee : membre inconnu dans le scope reste signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `portee---hors-du-lambda-le-membre-du-scope-est-signale`

- **Attendu** : portee : hors du lambda le membre du scope est signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `portee---pile-des-scopes-emboites`

- **Attendu** : portee : pile des scopes emboites.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `portee---buildList-du-classpath-prete-MutableList`

- **Attendu** : portee : buildList du classpath prete MutableList.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `destructuring---composants-nommes-en-portee`

- **Attendu** : destructuring : composants nommes en portee.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `destructuring---nom-inconnu-dans-le-corps-reste-signale`

- **Attendu** : destructuring : nom inconnu dans le corps reste signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `builtins-map---entries-keys-values-resolus`

- **Attendu** : builtins map : entries keys values resolus.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

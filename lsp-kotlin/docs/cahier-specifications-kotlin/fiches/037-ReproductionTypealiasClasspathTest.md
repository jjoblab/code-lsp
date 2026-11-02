# Fiche 037 — ReproductionTypealiasClasspathTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionTypealiasClasspathTest.kt` (5 tests, 251 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION (mission fiabilisation, étape B — rejeu du non-concluant §4 du rapport faux positifs) : un TYPEALIAS PUBLIC d'une bibliothèque Kotlin (aucun miroir bytecode — la sonde [jo.lspkotlin.classpath.SondeTypealiasKotlinxTest] a établi que le corpus kotlinx-datetime 0.6.1 en porte trois : `DayOfWeek`, `Month`, `ZoneOffset`) n'est exposé NULLE PART par le pipeline moteur : - [DecodeurMetadonne
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `typealias-importe-explicitement---type-nu-muet`

- **Attendu** : typealias importe explicitement : type nu muet — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `typealias-qualifie-complet---dernier-segment-muet`

- **Attendu** : typealias qualifie complet : dernier segment muet — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `typealias-derriere-import-joker---type-nu-muet`

- **Attendu** : typealias derriere import joker : type nu muet — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `controles---classe-reelle-du-corpus-muette-en-import-et-en-qualifie`

- **Attendu** : controles : classe reelle du corpus muette en import et en qualifie — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `controle-d3---nom-inconnu-en-qualifie-reste-signale`

- **Attendu** : controle d3 : nom inconnu en qualifie reste signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

# Fiche 026 — ReproductionExtensionsRecepteurNulTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionExtensionsRecepteurNulTest.kt` (2 tests, 150 l.).
> **Domaine** : diagnostics
> **Résumé** : EXTENSIONS À RÉCEPTEUR PARAMÈTRE DE TYPE (mission fiabilisation, étape A.3 — mesure née du différentiel [jo.lspkotlin.classpath.LecteurClassBrutDifferentielTest]) : `let`/`also`/`apply`/`run`/`takeIf`/`takeUnless` sont déclarées `fun <T> T.…(…)` — leur récepteur n'est PAS une classe, et [DecodeurMetadonnees] les indexe donc comme appelables TOP-LEVEL du paquet `kotlin` (récepteur null, sous-rappor
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `let-also-apply-run-takeIf-takeUnless---muets-malgre-un-recepteur-classpath-lisible`

- **Attendu** : let also apply run takeIf takeUnless : muets malgre un recepteur classpath lisible — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `controle---un-membre-reellement-absent-du-recepteur-lisible-reste-signale`

- **Attendu** : controle : un membre reellement absent du recepteur lisible reste signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

# Fiche 023 — ReproductionAppelablesBuiltinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionAppelablesBuiltinTest.kt` (2 tests, 141 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION (mission fiabilisation, étape A.3 — découverte du test différentiel [jo.lspkotlin.classpath.LecteurClassBrutDifferentielTest]) : les FONCTIONS BUILTIN `arrayOf` et `intArrayOf` du stdlib ne sont PAS émises par [DecodeurMetadonnees] (le nom manque de l'index des appelables — seul écart constaté sur la liste des fonctions usuelles) → la voie 6 de la résolution (appelables de premier niv
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `builtins-arrayOf-et-intArrayOf---intrinseques-réputées,-muets`

- **Attendu** : builtins arrayOf et intArrayOf : intrinseques réputées, muets — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `controles---listOf,-emptyArray-et-println-restent-muets`

- **Attendu** : controles : listOf, emptyArray et println restent muets — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

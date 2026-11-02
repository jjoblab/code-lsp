# Fiche 031 — ReproductionKmpTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionKmpTest.kt` (5 tests, 226 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION du template Kotlin Multiplatform (KMP) d'Android Studio au niveau MOTEUR — le module `shared` a DEUX racines sources (le paquet y vit DEUX fois : expect dans commonMain, actual dans androidMain) : shared/src/commonMain/kotlin/com/example/kmp/Platform.kt expect fun platformName(): String ; expect class Platform shared/src/commonMain/kotlin/com/example/kmp/Greeting.kt class Greeting { f
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `kmp---commonMain,-expect-et-usages,-zero-faux-positif`

- **Attendu** : kmp : commonMain, expect et usages, zero faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kmp---expect-declasse-et-fonction,-fichier-meme,-zero-faux-positif`

- **Attendu** : kmp : expect declasse et fonction, fichier meme, zero faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kmp---androidMain,-actual-et-usages,-zero-faux-positif`

- **Attendu** : kmp : androidMain, actual et usages, zero faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kmp---survol-de-l-expect-platformName-depuis-commonMain`

- **Attendu** : kmp : survol de l expect platformName depuis commonMain.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kmp---definition-go-to-depuis-l-usage-common-vers-l-expect`

- **Attendu** : kmp : definition go to depuis l usage common vers l expect.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

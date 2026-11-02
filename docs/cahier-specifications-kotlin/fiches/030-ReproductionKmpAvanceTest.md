# Fiche 030 — ReproductionKmpAvanceTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionKmpAvanceTest.kt` (5 tests, 236 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION KMP AVANCÉE — les formes qui dépassent la déclaration simple : `expect val`, `expect object` (le patron HttpClient/Database de toute lib KMP), `actual typealias`, membres d'expect object, génériques sur un type expect, usage depuis une EXTENSION.
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `kmp-avance---usages-commonMain-de-expect-val,-object,-generique`

- **Attendu** : kmp avance : usages commonMain de expect val, object, generique.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kmp-avance---androidMain,-actual-val,-object,-typealias`

- **Attendu** : kmp avance : androidMain, actual val, object, typealias.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kmp-avance---typage-du-retour-de-Boite-ouvrir`

- **Attendu** : kmp avance : typage du retour de Boite ouvrir.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `kmp-avance---survol-du-membre-d-un-expect-object`

- **Attendu** : kmp avance : survol du membre d un expect object.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kmp-avance---completion-membre-apres-Fabrique-point`

- **Attendu** : kmp avance : completion membre apres Fabrique point.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

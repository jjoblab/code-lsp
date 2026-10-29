# Fiche 002 — DecodeurMetadonneesTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/classpath/DecodeurMetadonneesTest.kt` (3 tests, 49 l.).
> **Domaine** : index-classpath
> **Résumé** : Tests du décodeur de métadonnées (K3) sur des jars RÉELS du classpath de test : - kotlin-stdlib (Kotlin → appelables de premier niveau + extensions) ; - junit4 (Java → PORTE ABSENTE, zéro classe décodée — le saut de §4.2 est le contrat même du décodeur).
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `stdlib---des-appelables-de-premier-niveau-et-des-extensions`

- **Attendu** : stdlib : des appelables de premier niveau et des extensions.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `jar-Java-(junit)---porte-absente,-ZERO-appelable`

- **Attendu** : jar Java (junit) : porte absente, ZERO appelable.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `fichier-absent---liste-vide,-jamais-d'exception-(D3)`

- **Attendu** : fichier absent : liste vide, jamais d'exception (D3) — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

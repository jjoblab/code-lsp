# Fiche 005 — SondeTypealiasKotlinxTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/classpath/SondeTypealiasKotlinxTest.kt` (2 tests, 145 l.).
> **Domaine** : index-classpath
> **Résumé** : SONDE DU REJEU §4 (mission fiabilisation, étape B) — le rapport faux positifs 2026-10-26 laissait le `typealias` de bibliothèques NON CONCLUANT : « aucun typealias public JVM du stdlib n'est isolable par la sonde (l'écart de noms métadonnées les mélange aux propriétés). À rejouer en étape B sur un corpus kotlinx (p. ex. `kotlinx.serialization`) ». Cette sonde ISOLE les typealiases publics de DEUX
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `kotlinx-datetime---les-typealiases-publics-du-corpus-sont-isoles`

- **Attendu** : kotlinx datetime : les typealiases publics du corpus sont isoles.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `kotlinx-serialization-core---la-sonde-dit-ce-que-le-corpus-porte`

- **Attendu** : kotlinx serialization core : la sonde dit ce que le corpus porte.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

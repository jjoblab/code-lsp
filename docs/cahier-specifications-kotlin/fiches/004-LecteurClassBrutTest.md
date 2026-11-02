# Fiche 004 — LecteurClassBrutTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/classpath/LecteurClassBrutTest.kt` (5 tests, 135 l.).
> **Domaine** : index-classpath
> **Résumé** : Tests du lecteur de class-files BRUT (K3) — deux vérités : - une classe SYNTHÉTIQUE fabriquée octet par octet (contrôle total du format : super, interfaces, absence de @Metadata) ; - une classe RÉELLE du kotlin-stdlib du classpath de test (`kotlin.jvm.internal.Intrinsics` — facade Kotlin avec @Metadata, kind FileFacade).
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `classe-synthetique---identite,-super-et-interfaces-lues`

- **Attendu** : classe synthetique : identite, super et interfaces lues.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `classe-synthetique-sans-super---super-null-(java-lang-Object)`

- **Attendu** : classe synthetique sans super : super null (java lang Object).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `octets-invalides---null,-jamais-d'exception-(D3)`

- **Attendu** : octets invalides : null, jamais d'exception (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `classe-Kotlin-reelle-du-stdlib---identite-et-metadata-reconstruite`

- **Attendu** : classe Kotlin reelle du stdlib : identite et metadata reconstruite.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `classe-JAVA-reelle-du-stdlib---pas-de-metadata-(négatif-honnête)`

- **Attendu** : classe JAVA reelle du stdlib : pas de metadata (négatif honnête).
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

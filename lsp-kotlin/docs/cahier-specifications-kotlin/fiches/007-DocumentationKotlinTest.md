# Fiche 007 — DocumentationKotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/documentation/DocumentationKotlinTest.kt` (9 tests, 129 l.).
> **Domaine** : hover-inlay
> **Résumé** : Tests du moteur de KDoc (S2 — survol riche, KDocParser vendoré) : extraction du DOC_COMMENT porté par la déclaration, rendu en texte neutre (sections libres + tags). Tolérance D3 : rien ne lève.
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `kdoc-simple---texte-libre-rendu,-asteriskes-et-blancs-detaches`

- **Attendu** : kdoc simple : texte libre rendu, asteriskes et blancs detaches.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kdoc-avec-tags---param-et-return-rendus-en-lignes`

- **Attendu** : kdoc avec tags : param et return rendus en lignes.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kdoc-d-un-membre---fonction-documentée`

- **Attendu** : kdoc d un membre : fonction documentée.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kdoc-avant-annotations---porte-quand-meme-par-la-declaration`

- **Attendu** : kdoc avant annotations : porte quand meme par la declaration.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `sans-kdoc---null`

- **Attendu** : sans kdoc : null.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `block-comment-n-est-pas-un-kdoc---null`

- **Attendu** : block comment n est pas un kdoc : null.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kdoc-vide---etoiles-seules,-null`

- **Attendu** : kdoc vide : etoiles seules, null.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `kdoc-une-ligne---rendu-sans-entourage`

- **Attendu** : kdoc une ligne : rendu sans entourage.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `tag-sans-contenu---rendu-degage-sans-exception`

- **Attendu** : tag sans contenu : rendu degage sans exception.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

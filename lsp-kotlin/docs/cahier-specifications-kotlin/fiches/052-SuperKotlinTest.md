# Fiche 052 — SuperKotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SuperKotlinTest.kt` (10 tests, 357 l.).
> **Domaine** : navigation
> **Résumé** : Tests du GO TO « Super » Kotlin (ADR 0210 — `textDocument superDefinition`, miroir des scénarios `NavigationFacadeTest` du LSP Java) — par le SPI (chemin de production D6), moteur RÉEL, projet multi-fichiers sur disque (racines sources du scan, même montage que [TypesKotlinTest]). Le cœur éprouvé : les DEUX chemins du picker Super — (1) MEMBRE au caret (déclaration outrepassante, ou usage RÉSOLU p
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `super-sur-déclaration-outrepassante---le-même-nommé-du-supertype-direct`

- **Attendu** : super sur déclaration outrepassante : le même nommé du supertype direct.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `super-sur-déclaration-outrepassante---clôture-TRANSITIVE-(deux-niveaux)`

- **Attendu** : super sur déclaration outrepassante : clôture TRANSITIVE (deux niveaux).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `super-sur-déclaration-d'une-IMPLÉMENTATION-d'interface`

- **Attendu** : super sur déclaration d'une IMPLÉMENTATION d'interface.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `super-sur-usage-AMBIGU-d'un-override---vide-honnête-(borne-O3)`

- **Attendu** : super sur usage AMBIGU d'un override : vide honnête (borne O3).
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `super-sur-usage-UNIQUE-d'un-membre---les-supertypes-du-déclarant`

- **Attendu** : super sur usage UNIQUE d'un membre : les supertypes du déclarant.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `super-sur-propriété-NON-outrepassante---retombée-classe-englobante`

- **Attendu** : super sur propriété NON outrepassante : retombée classe englobante.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `super-sur-référence-de-type---les-supertypes-du-type-visé`

- **Attendu** : super sur référence de type : les supertypes du type visé.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `super-classe-racine-sans-clause---aucune-cible-(garde-D3)`

- **Attendu** : super classe racine sans clause : aucune cible (garde D3).
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `supertype-classpath-SANS-source-projet---aucune-cible-(garde-D3)`

- **Attendu** : supertype classpath SANS source projet : aucune cible (garde D3).
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `document-inconnu-ou-fermé---liste-vide,-jamais-d'exception`

- **Attendu** : document inconnu ou fermé : liste vide, jamais d'exception — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

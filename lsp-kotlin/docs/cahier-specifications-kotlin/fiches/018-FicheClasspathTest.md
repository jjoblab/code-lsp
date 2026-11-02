# Fiche 018 — FicheClasspathTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/FicheClasspathTest.kt` (11 tests, 459 l.).
> **Domaine** : hover-inlay
> **Résumé** : Tests du chantier P1-D « documentation du classpath au survol et à la signature » (ADR 0230, feuille de route du rapport d'analyse du 28/10/2026) — par le SPI (chemin de production D6) : - la FICHE STRUCTURELLE du survol d'un type du classpath (genre, héritage direct, interfaces, constantes d'enum, champs publics) prouvée par la lecture brute du `.class` — les OCTETS sont synthétiques (super et in
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `survol-type-classpath---classe,-heritage-direct-et-champs-publics`

- **Attendu** : survol type classpath : classe, heritage direct et champs publics.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-type-classpath---interface,-section-Interfaces,-jamais-d-extends`

- **Attendu** : survol type classpath : interface, section Interfaces, jamais d extends.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-type-classpath---enum,-constantes-servies,-super-Enum-masque`

- **Attendu** : survol type classpath : enum, constantes servies, super Enum masque.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-type-classpath---objet-Kotlin-par-champ-INSTANCE,-marqueur-exclu-des-champs`

- **Attendu** : survol type classpath : objet Kotlin par champ INSTANCE, marqueur exclu des champs.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-type-classpath---plafonds-d-affichage,-champs-8-et-constantes-20`

- **Attendu** : survol type classpath : plafonds d affichage, champs 8 et constantes 20.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-type-classpath---binaire-illisible,-genre-et-champs-sans-heritage-ni-interfaces`

- **Attendu** : survol type classpath : binaire illisible, genre et champs sans heritage ni interfaces.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---KDoc-du-document-courant-accompagne-la-candidate`

- **Attendu** : signature : KDoc du document courant accompagne la candidate.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---KDoc-cross-fichier-par-le-port,-fichier-tiers-FERME`

- **Attendu** : signature : KDoc cross fichier par le port, fichier tiers FERME.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---KDoc-cross-fichier,-fichier-tiers-OUVERT,-arbre-vif-et-non-disque-perime`

- **Attendu** : signature : KDoc cross fichier, fichier tiers OUVERT, arbre vif et non disque perime.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---candidate-du-classpath-sans-documentation,-le-bytecode-est-muet`

- **Attendu** : signature : candidate du classpath sans documentation, le bytecode est muet — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `signature---membre-vif-de-la-classe-englobante,-KDoc-vive`

- **Attendu** : signature : membre vif de la classe englobante, KDoc vive.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
